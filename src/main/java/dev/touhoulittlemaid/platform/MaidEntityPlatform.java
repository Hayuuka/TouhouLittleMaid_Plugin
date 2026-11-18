package dev.touhoulittlemaid.platform;

import dev.touhoulittlemaid.core.domain.FavorTier;
import dev.touhoulittlemaid.core.domain.MaidInventory;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 实体平台门面（blueprint 0.4 环2 → 环3 的唯一出口）。
 *
 * <p>环2 的 task/behavior/gui 等包只认这个接口，禁止直接 import Citizens。
 * 全部方法只在主线程调用。
 */
public interface MaidEntityPlatform {

    /** 创建并生成 NPC，返回 npcId；内部完成 setProtected(false) 与 MaidTraitV2 挂载。 */
    int spawn(String name, Location loc);

    /** = Citizens despawn（保留 NPC 注册）。 */
    void despawn(int npcId);

    /** = Citizens destroy（连注册一起删）。 */
    void destroy(int npcId);

    boolean isSpawned(int npcId);

    /** null 安全：NPC 未生成/区块未加载时返回 null。 */
    LivingEntity entity(int npcId);

    UUID entityUuid(int npcId);

    boolean isNpc(Entity e);

    void navigate(int npcId, Location loc, float speed);

    void navigateEntity(int npcId, Entity target, float speed);

    void cancelNavigation(int npcId);

    boolean isNavigating(int npcId);

    void teleport(int npcId, Location loc);

    /**
     * 重设生命上限/攻击基值并同步当前血量。
     *
     * <p>{@code npcId} 不在 blueprint 0.7 的接口签名里，但没有它无法定位实体——
     * 属 blueprint 缺陷，按最小改动补齐（见 docs/api-facts.md 第 29 节冲突记录）。
     */
    void applyAttributes(int npcId, FavorTier tier, boolean struckByLightning);

    /** 0-5 槽写实体 equipment；{@code npcId} 同上属签名补齐。 */
    void applyEquipment(int npcId, MaidInventory inv);

    void hideFrom(Player p, UUID entityUuid);

    void showTo(Player p, UUID entityUuid);

    void attack(int npcId, Entity target);

    /** 返回 0..1 的攻击蓄力进度（V0-4 实测项）。 */
    double attackCooldownProgress(int npcId);
}
