package dev.touhoulittlemaid.platform;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * 模型/动画平台门面（blueprint 0.4 环2 → 环3）。
 *
 * <p>BetterModel 是 paper-plugin（classloader 隔离），实现必须反射调用（v1 ModelApplier 姿势）。
 * 全部方法只在主线程调用。
 */
public interface MaidModelPlatform {

    void applyModel(int npcId, String modelId);

    /** 播放动画；LOOP 类动画由实现内部计时补播（blueprint 0.9）。 */
    void animate(int npcId, String animName);

    void stopAnimation(int npcId, String animName);

    /** 启动校验用：模型缺 idle/walk 直接告警。 */
    boolean hasAnimation(int npcId, String animName);

    /** attacked 动画缺失时的红闪替代（映射报告 §3）。 */
    void damageTint(int npcId);

    /** 骨 display 实体 UUID 列表（hideEntity 用，api-facts 24.3-3）。 */
    List<UUID> boneDisplayEntities(int npcId);

    /** 骨上挂物品（P4 手持可见，V0-2）。 */
    void setBoneItem(int npcId, String boneName, ItemStack item);

    void clearBoneItem(int npcId, String boneName);
}
