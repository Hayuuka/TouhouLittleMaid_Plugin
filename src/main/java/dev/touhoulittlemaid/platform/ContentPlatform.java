package dev.touhoulittlemaid.platform;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * 自定义内容平台门面（blueprint 0.4 环2 → 环3）。
 *
 * <p>CraftEngine 是 paper-plugin（classloader 隔离），实现必须反射调用
 * （v1 util/MaidItems 姿势，api-facts 24.3-5/6）。
 */
public interface ContentPlatform {

    /** CE 物品 id → ItemStack；缺失返回 null 并告警一次。 */
    ItemStack customItem(String ceId);

    /** "ns:id"；非 CE 物品返回 null。 */
    String customItemId(ItemStack stack);

    /** 以实体为锚点向附近玩家播放声音（[待验 V0-1] 自定义 sounds.json 是否可行）。 */
    void playSoundAt(Entity anchor, String key, float volume, float pitch);

    boolean isCustomBlock(Block block, String ceId);
}
