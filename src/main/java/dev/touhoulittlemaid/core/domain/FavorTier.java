package dev.touhoulittlemaid.core.domain;

/**
 * 好感档位（blueprint 0.7 / 规格书 §2.1）。数值写死，可配项只允许整体替换单档数值。
 *
 * <p>环1 纯净域：本类不得出现任何 Bukkit/平台类型。
 */
public enum FavorTier {
    LV0(0, 20, 2, 0.0, 1.0, 10),
    LV1(64, 30, 3, 1.0, 2.0, 10),
    LV2(192, 40, 4, 3.0, 3.0, 20),
    LV3(384, 80, 6, 5.0, 4.0, 30);

    /** 档位下限。 */
    public final int threshold;
    /** 生命上限（雷劈 +20 另算）。 */
    public final int maxHealth;
    /** 攻击基值。 */
    public final int attackDamage;
    /** 触及距离加成。 */
    public final double reachBonus;
    /** 横扫范围。 */
    public final double sweepRange;
    /** 饰品解锁格数。 */
    public final int baubleSlots;

    FavorTier(int threshold, int maxHealth, int attackDamage,
              double reachBonus, double sweepRange, int baubleSlots) {
        this.threshold = threshold;
        this.maxHealth = maxHealth;
        this.attackDamage = attackDamage;
        this.reachBonus = reachBonus;
        this.sweepRange = sweepRange;
        this.baubleSlots = baubleSlots;
    }

    /** {@code >=384→LV3, >=192→LV2, >=64→LV1, else LV0}。 */
    public static FavorTier of(int favorability) {
        if (favorability >= LV3.threshold) {
            return LV3;
        }
        if (favorability >= LV2.threshold) {
            return LV2;
        }
        if (favorability >= LV1.threshold) {
            return LV1;
        }
        return LV0;
    }
}
