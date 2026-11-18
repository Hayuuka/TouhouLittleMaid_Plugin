package dev.touhoulittlemaid.platform.impl;

import dev.touhoulittlemaid.platform.ContentPlatform;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * CraftEngine 内容平台适配器 —— 全项目唯一与 CE 交互的地方（环3）。
 *
 * <p>CE 同时带 plugin.yml 与 paper-plugin.yml，运行时 classloader 隔离，必须反射
 * （api-facts 24.3-5/6）。调用姿势照抄 v1 {@code util/MaidItems}：
 * {@code CraftEngineItems.byId(id)} → {@code BukkitItemDefinition#buildBukkitItem()}。
 */
public final class CraftEngineContent implements ContentPlatform {

    private static final String CE_ITEMS_API = "net.momirealms.craftengine.bukkit.api.CraftEngineItems";
    private static final String CE_BLOCKS_API = "net.momirealms.craftengine.bukkit.api.CraftEngineBlocks";
    private static final String CE_ITEM_DEFINITION =
            "net.momirealms.craftengine.bukkit.item.BukkitItemDefinition";
    private static final String CE_HOLDER = "net.momirealms.craftengine.core.registry.Holder";
    private static final String CE_BLOCK_DEFINITION =
            "net.momirealms.craftengine.core.block.BlockDefinition";

    private final Plugin plugin;

    private boolean resolved;
    private boolean disabled;
    private boolean warned;
    private boolean missingWarned;

    private Method itemById;
    private Method buildBukkitItem;
    private Method getCustomItemId;
    private Method isCustomBlock;
    private Method getCustomBlockState;
    private Method holderValue;
    private Method definitionId;

    public CraftEngineContent(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack customItem(String ceId) {
        if (ceId == null || !resolve()) {
            return null;
        }
        try {
            Object definition = itemById.invoke(null, ceId);
            if (definition == null) {
                warnMissing("CraftEngine 里没有物品 " + ceId + "，按缺失处理");
                return null;
            }
            Object built = buildBukkitItem.invoke(definition);
            return built instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnMissing("CraftEngine 构造物品 " + ceId + " 失败：" + e);
            return null;
        }
    }

    @Override
    public String customItemId(ItemStack stack) {
        if (stack == null || !resolve()) {
            return null;
        }
        try {
            Object key = getCustomItemId.invoke(null, stack);
            return key == null ? null : key.toString();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnOnce("读取 CraftEngine 物品 id 失败：" + e);
            return null;
        }
    }

    @Override
    public void playSoundAt(Entity anchor, String key, float volume, float pitch) {
        if (anchor == null || key == null || key.isBlank()) {
            return;
        }
        // 以实体为锚点广播给附近玩家；key 直接走资源包 sounds.json 的自定义声音键 [待验 V0-1]
        anchor.getWorld().playSound(anchor, key, SoundCategory.NEUTRAL, volume, pitch);
    }

    @Override
    public boolean isCustomBlock(Block block, String ceId) {
        if (block == null || ceId == null || !resolve()) {
            return false;
        }
        try {
            if (!(boolean) isCustomBlock.invoke(null, block)) {
                return false;
            }
            Object state = getCustomBlockState.invoke(null, block);
            if (state == null) {
                return false;
            }
            Object holder = state.getClass().getMethod("owner").invoke(state);
            if (holder == null) {
                return false;
            }
            Object definition = holderValue.invoke(holder);
            if (definition == null) {
                return false;
            }
            Object id = definitionId.invoke(definition);
            return id != null && ceId.equals(id.toString());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnOnce("判定 CraftEngine 自定义方块失败：" + e);
            return false;
        }
    }

    private boolean resolve() {
        if (resolved) {
            return true;
        }
        if (disabled) {
            return false;
        }
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) {
            disabled = true;
            warnOnce("CraftEngine 未加载，自定义物品/方块不可用");
            return false;
        }
        try {
            ClassLoader loader = craftEngine.getClass().getClassLoader();
            Class<?> items = Class.forName(CE_ITEMS_API, true, loader);
            Class<?> blocks = Class.forName(CE_BLOCKS_API, true, loader);
            Class<?> itemDefinition = Class.forName(CE_ITEM_DEFINITION, true, loader);
            Class<?> holder = Class.forName(CE_HOLDER, true, loader);
            Class<?> blockDefinition = Class.forName(CE_BLOCK_DEFINITION, true, loader);

            itemById = items.getMethod("byId", String.class);
            getCustomItemId = items.getMethod("getCustomItemId", ItemStack.class);
            buildBukkitItem = itemDefinition.getMethod("buildBukkitItem");

            isCustomBlock = blocks.getMethod("isCustomBlock", Block.class);
            getCustomBlockState = blocks.getMethod("getCustomBlockState", Block.class);
            holderValue = holder.getMethod("value");
            definitionId = blockDefinition.getMethod("id");

            resolved = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            disabled = true;
            warnOnce("CraftEngine API 与预期不符，已放弃自定义内容：" + e);
            return false;
        }
    }

    private void warnOnce(String message) {
        if (!warned) {
            warned = true;
            plugin.getLogger().log(Level.WARNING, "[TLM] " + message);
        }
    }

    private void warnMissing(String message) {
        if (!missingWarned) {
            missingWarned = true;
            plugin.getLogger().warning("[TLM] " + message + "（后续同类问题不再重复打印）");
        }
    }
}
