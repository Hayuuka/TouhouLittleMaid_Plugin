package dev.touhoulittlemaid.platform.impl;

import dev.touhoulittlemaid.platform.MaidModelPlatform;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * BetterModel 模型/动画平台适配器 —— 全项目唯一与 BetterModel 交互的地方（环3）。
 *
 * <p>为什么全程反射：BetterModel 是 paper-plugin，与 Bukkit 插件 classloader 隔离，
 * 且 Maven Central 的 API 包与运行期 jar 不一致（api-facts 3.1/24.3）。调用姿势照抄 v1
 * {@code ModelApplier} / {@code ModelAnimator}，句柄只解析一次，失败整体禁用并告警一次。
 *
 * <p>补播纪律（blueprint 0.9 / api-facts 17.9）：单次 animate 播完停在最后一帧不自续，
 * LOOP 类动画（idle/walk/run/sit/sleep）由本适配器按 per-npc 计时表周期性重播。
 */
public final class BetterModelPlatform implements MaidModelPlatform {

    private static final String MODEL_TRAIT =
            "kr.toxicity.model.bukkit.compatibility.citizens.trait.ModelTrait";
    private static final String BETTER_MODEL_API = "kr.toxicity.model.api.BetterModel";
    private static final String MODIFIER = "kr.toxicity.model.api.animation.AnimationModifier";
    private static final String ITERATOR_TYPE = "kr.toxicity.model.api.animation.AnimationIterator$Type";
    private static final String TRACKER = "kr.toxicity.model.api.tracker.Tracker";
    private static final String ENTITY_TRACKER = "kr.toxicity.model.api.tracker.EntityTracker";
    private static final String REGISTRY = "kr.toxicity.model.api.tracker.EntityTrackerRegistry";
    private static final String RENDERER = "kr.toxicity.model.api.data.renderer.ModelRenderer";
    private static final String RENDERED_BONE = "kr.toxicity.model.api.bone.RenderedBone";
    private static final String MODEL_DISPLAY = "kr.toxicity.model.api.nms.ModelDisplay";
    private static final String TRANSFORMED_ITEM_STACK = "kr.toxicity.model.api.util.TransformedItemStack";
    private static final String BUKKIT_ITEM_STACK = "kr.toxicity.model.api.bukkit.platform.BukkitItemStack";

    /** LOOP 类动画的补播间隔；数值取自 v1 实测手感（walk 1 秒一循环、首尾同帧）。 */
    private static final long LOOP_REPLAY_MS = 1000L;

    private static final Set<String> LOOP_ANIMATIONS =
            Set.of("idle", "walk", "run", "sit", "sleep");

    /** 姿态跟着 tracker 走：tracker 换了（实体重建/换模型）旧状态自动失效。 */
    private record LoopState(Object tracker, String animation, long playedAt) {
    }

    private final Plugin plugin;
    private final Map<Integer, LoopState> loopStates = new HashMap<>();
    private final Set<String> missingAnimWarned = new HashSet<>();

    private boolean resolved;
    private boolean disabled;
    private final Set<String> warned = new HashSet<>();

    private Method modelOrNull;
    private Method registryOrNull;
    private Method registryFirst;
    private Method trackerAnimate;
    private Method trackerStopAnimation;
    private Method trackerRenderer;
    private Method trackerBones;
    private Method trackerBone;
    private Method rendererAnimation;
    private Method boneGetDisplay;
    private Method boneItemStack;
    private Method displayUuid;
    private Method damageTint;
    private Method transformedOf;
    private Method transformedEmpty;
    private Constructor<?> bukkitItemStackCtor;
    private Object loopModifier;
    private Object playOnceModifier;

    public BetterModelPlatform(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void applyModel(int npcId, String modelId) {
        if (!resolve()) {
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) {
            return;
        }
        try {
            ClassLoader loader = loader();
            Class<?> traitClass = Class.forName(MODEL_TRAIT, true, loader);
            Method getOrAddTrait = NPC.class.getMethod("getOrAddTrait", Class.class);
            Object trait = getOrAddTrait.invoke(npc, traitClass);
            if (trait == null) {
                warnOnce("BetterModel 未能为 NPC " + npcId + " 创建 model trait");
                return;
            }
            Object renderer = null;
            if (modelId != null && !modelId.isBlank()) {
                renderer = modelOrNull.invoke(null, modelId);
                if (renderer == null) {
                    plugin.getLogger().warning("[TLM] BetterModel 找不到模型 " + modelId
                            + "，请确认 plugins/BetterModel/models/ 内有该模型");
                }
            }
            Method setRenderer = findSetRenderer(traitClass);
            if (setRenderer == null) {
                warnOnce("ModelTrait 上找不到 setRenderer 方法，BetterModel 版本可能已变");
                return;
            }
            // setRenderer 在"新渲染器非空"分支不关闭旧 tracker（api-facts 27.4），A→B 直接切换会残留；
            // 先传 null 走关闭分支，再挂新模型。
            setRenderer.invoke(trait, new Object[]{null});
            setRenderer.invoke(trait, renderer);
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("挂载 BetterModel 模型失败（API 可能已随版本变化）：" + e);
        }
    }

    @Override
    public void animate(int npcId, String animName) {
        if (animName == null || animName.isBlank()) {
            return;
        }
        Object tracker = tracker(npcId);
        if (tracker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        LoopState state = loopStates.get(npcId);
        boolean loop = LOOP_ANIMATIONS.contains(animName);
        if (loop && state != null && state.tracker() == tracker && animName.equals(state.animation())
                && now - state.playedAt() < LOOP_REPLAY_MS) {
            return;
        }
        try {
            boolean played = (boolean) trackerAnimate.invoke(
                    tracker, animName, loop ? loopModifier : playOnceModifier);
            if (!played) {
                // 模型缺这段动画时 BM 静默返回 false（api-facts 17.9）：只记一次，不报错。
                if (missingAnimWarned.add(animName)) {
                    plugin.getLogger().fine("[TLM] 模型缺少动画「" + animName + "」，按降级表处理");
                }
            }
            loopStates.put(npcId, new LoopState(tracker, animName, now));
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("播放动画 " + animName + " 失败：" + e);
        }
    }

    @Override
    public void stopAnimation(int npcId, String animName) {
        Object tracker = tracker(npcId);
        if (tracker == null) {
            return;
        }
        try {
            trackerStopAnimation.invoke(tracker, animName);
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("停止动画 " + animName + " 失败：" + e);
        }
        loopStates.remove(npcId);
    }

    @Override
    public boolean hasAnimation(int npcId, String animName) {
        Object tracker = tracker(npcId);
        if (tracker == null) {
            return false;
        }
        try {
            Object renderer = trackerRenderer.invoke(tracker);
            if (renderer == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            java.util.Optional<Object> found = (java.util.Optional<Object>) rendererAnimation.invoke(renderer, animName);
            return found != null && found.isPresent();
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("查询动画是否存在失败：" + e);
            return false;
        }
    }

    @Override
    public void damageTint(int npcId) {
        Object tracker = tracker(npcId);
        if (tracker == null) {
            return;
        }
        try {
            damageTint.invoke(tracker);
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("播放受击红闪失败：" + e);
        }
    }

    @Override
    public List<UUID> boneDisplayEntities(int npcId) {
        Object tracker = tracker(npcId);
        if (tracker == null) {
            return List.of();
        }
        try {
            Collection<?> bones = (Collection<?>) trackerBones.invoke(tracker);
            List<UUID> result = new ArrayList<>();
            for (Object bone : bones) {
                Object display = boneGetDisplay.invoke(bone);
                if (display != null) {
                    result.add((UUID) displayUuid.invoke(display));
                }
            }
            return result;
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("枚举骨骼 display 实体失败：" + e);
            return List.of();
        }
    }

    @Override
    public void setBoneItem(int npcId, String boneName, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            clearBoneItem(npcId, boneName);
            return;
        }
        Object tracker = tracker(npcId);
        if (tracker == null) {
            return;
        }
        try {
            Object bone = trackerBone.invoke(tracker, boneName);
            if (bone == null) {
                return;
            }
            Object platformItem = bukkitItemStackCtor.newInstance(item);
            Object transformed = transformedOf.invoke(null, platformItem);
            boolean applied = (boolean) boneItemStack.invoke(
                    bone, (java.util.function.Predicate<Object>) b -> true, transformed);
            if (!applied) {
                warnOnce("骨骼 " + boneName + " 拒绝了物品（骨骼名不存在或模型未就绪）");
                return;
            }
            // 原版手持物品的握点/朝向由物品模型 display.thirdperson_righthand 决定；
            // BM 默认 FIXED 会把物品按中心对齐骨骼，导致手握在剑身上。这里统一套用右手上下文。
            applyItemTransform(bone, "THIRDPERSON_RIGHTHAND");
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("骨骼 " + boneName + " 挂物品失败：" + e);
        }
    }

    @Override
    public void clearBoneItem(int npcId, String boneName) {
        Object tracker = tracker(npcId);
        if (tracker == null) {
            return;
        }
        try {
            Object bone = trackerBone.invoke(tracker, boneName);
            if (bone == null) {
                return;
            }
            boneItemStack.invoke(bone, (java.util.function.Predicate<Object>) b -> true, transformedEmpty.invoke(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("清空骨骼 " + boneName + " 物品失败：" + e);
        }
    }

    /** 给骨上物品套用原版 display 上下文；失败只告警，不影响物品已挂上的事实。 */
    private void applyItemTransform(Object bone, String transformName) {
        try {
            ClassLoader loader = loader();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum> transformClass = (Class<? extends Enum>)
                    Class.forName("kr.toxicity.model.api.platform.PlatformItemTransform", true, loader);
            Object transform = Enum.valueOf(transformClass, transformName.toUpperCase(java.util.Locale.ROOT));
            Class<?> displayClass = Class.forName(MODEL_DISPLAY, true, loader);
            Method setDisplay = displayClass.getMethod("display", transformClass);
            Method applyAtDisplay = bone.getClass().getMethod(
                    "applyAtDisplay", java.util.function.Predicate.class, java.util.function.Consumer.class);
            boolean applied = (boolean) applyAtDisplay.invoke(bone,
                    (java.util.function.Predicate<Object>) b -> true,
                    (java.util.function.Consumer<Object>) display -> {
                        try {
                            setDisplay.invoke(display, transform);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    });
            if (!applied) {
                warnOnce("骨骼 display 未就绪，未能套用物品上下文 " + transformName);
            }
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
            warnOnce("套用骨物品上下文失败：" + e);
        }
    }

    // ---- 内部 ----

    private Object tracker(int npcId) {
        if (!resolve()) {
            return null;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) {
            return null;
        }
        Entity entity = npc.getEntity();
        if (entity == null) {
            // NPC 未生成时没有 tracker；等它回来后调用方会重新驱动
            return null;
        }
        try {
            Object registry = registryOrNull.invoke(null, entity.getUniqueId());
            return registry == null ? null : registryFirst.invoke(registry);
        } catch (ReflectiveOperationException | LinkageError e) {
            warnOnce("获取女仆模型 tracker 失败：" + e);
            return null;
        }
    }

    private boolean resolve() {
        if (resolved) {
            return true;
        }
        if (disabled) {
            return false;
        }
        Plugin betterModel = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (betterModel == null) {
            disabled = true;
            warnOnce("BetterModel 未加载，女仆不会显示自定义模型/动画");
            return false;
        }
        try {
            ClassLoader loader = betterModel.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(BETTER_MODEL_API, true, loader);
            Class<?> modifierClass = Class.forName(MODIFIER, true, loader);
            Class<?> typeClass = Class.forName(ITERATOR_TYPE, true, loader);
            Class<?> registryClass = Class.forName(REGISTRY, true, loader);
            Class<?> trackerClass = Class.forName(TRACKER, true, loader);
            Class<?> rendererClass = Class.forName(RENDERER, true, loader);
            Class<?> boneClass = Class.forName(RENDERED_BONE, true, loader);
            Class<?> displayClass = Class.forName(MODEL_DISPLAY, true, loader);
            Class<?> transformedClass = Class.forName(TRANSFORMED_ITEM_STACK, true, loader);
            Class<?> bukkitItemClass = Class.forName(BUKKIT_ITEM_STACK, true, loader);
            Class<?> entityTrackerClass = Class.forName(ENTITY_TRACKER, true, loader);

            modelOrNull = apiClass.getMethod("modelOrNull", String.class);
            registryOrNull = apiClass.getMethod("registryOrNull", UUID.class);

            registryFirst = registryClass.getMethod("first");

            trackerAnimate = trackerClass.getMethod("animate", String.class, modifierClass);
            trackerStopAnimation = trackerClass.getMethod("stopAnimation", String.class);
            trackerRenderer = trackerClass.getMethod("renderer");
            trackerBones = trackerClass.getMethod("bones");
            trackerBone = trackerClass.getMethod("bone", String.class);

            rendererAnimation = rendererClass.getMethod("animation", String.class);

            boneGetDisplay = boneClass.getMethod("getDisplay");
            boneItemStack = boneClass.getMethod("itemStack", java.util.function.Predicate.class, transformedClass);

            displayUuid = displayClass.getMethod("uuid");

            damageTint = entityTrackerClass.getMethod("damageTint");

            transformedOf = transformedClass.getMethod("of",
                    Class.forName("kr.toxicity.model.api.platform.PlatformItemStack", true, loader));
            transformedEmpty = transformedClass.getMethod("empty");
            bukkitItemStackCtor = bukkitItemClass.getConstructor(ItemStack.class);

            Object builder = modifierClass.getMethod("builder").invoke(null);
            Object loop = typeClass.getField("LOOP").get(null);
            builder.getClass().getMethod("type", typeClass).invoke(builder, loop);
            loopModifier = builder.getClass().getMethod("build").invoke(builder);

            Object onceBuilder = modifierClass.getMethod("builder").invoke(null);
            Object playOnce = typeClass.getField("PLAY_ONCE").get(null);
            onceBuilder.getClass().getMethod("type", typeClass).invoke(onceBuilder, playOnce);
            playOnceModifier = onceBuilder.getClass().getMethod("build").invoke(onceBuilder);

            resolved = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            disabled = true;
            warnOnce("BetterModel API 与预期不符，已放弃模型/动画驱动：" + e);
            return false;
        }
    }

    private static Method findSetRenderer(Class<?> traitClass) {
        for (Method method : traitClass.getMethods()) {
            if (method.getName().equals("setRenderer") && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    private ClassLoader loader() {
        return Bukkit.getPluginManager().getPlugin("BetterModel").getClass().getClassLoader();
    }

    private void warnOnce(String message) {
        if (warned.add(message)) {
            plugin.getLogger().log(Level.WARNING, "[TLM] " + message);
        }
    }
}
