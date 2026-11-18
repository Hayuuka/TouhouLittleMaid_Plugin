package dev.touhoulittlemaid.core.domain;

/**
 * 女仆背包 82 槽语义（blueprint 0.8，全项目唯一口径）。
 *
 * <p>P0 只落槽位常量：平台接口 {@code applyAttributes/applyEquipment} 需要该类型参与签名，
 * 真正的物品存储与读写由 P1 在本类内补齐。环1 纯净域，不得出现 Bukkit 的 ItemStack。
 */
public final class MaidInventory {

    public static final int SIZE = 82;

    /** 主手。 */
    public static final int MAIN_HAND = 0;
    /** 副手。 */
    public static final int OFF_HAND = 1;
    /** 头 / 胸 / 腿 / 靴。 */
    public static final int HELMET = 2;
    public static final int CHESTPLATE = 3;
    public static final int LEGGINGS = 4;
    public static final int BOOTS = 5;

    /** 背包 36 格（物理恒 36，backpack_type 决定 GUI 暴露几格）。 */
    public static final int BACKPACK_START = 6;
    public static final int BACKPACK_END = 41;
    /** 饰品 30 格（FavorTier 解锁 10/10/20/30）。 */
    public static final int BAUBLE_START = 42;
    public static final int BAUBLE_END = 71;
    /** 隐藏槽 1 格（暂存"换手进食"的原手物品）。 */
    public static final int HIDDEN = 72;
    /** 任务槽 9 格（fishing 鱼获、extinguishing 灭火器等按任务 id 占用）。 */
    public static final int TASK_START = 73;
    public static final int TASK_END = 81;

    private MaidInventory() {
    }
}
