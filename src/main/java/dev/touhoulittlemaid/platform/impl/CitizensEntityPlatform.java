package dev.touhoulittlemaid.platform.impl;

import dev.touhoulittlemaid.core.domain.FavorTier;
import dev.touhoulittlemaid.core.domain.MaidInventory;
import dev.touhoulittlemaid.platform.MaidEntityPlatform;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Citizens 实体平台适配器 —— 全项目唯一允许 import {@code net.citizensnpcs} 的地方
 * （blueprint 0.4 环3）。
 *
 * <p>P0 只落最小实现：spawn / despawn / destroy / navigate / cancel / teleport /
 * applyAttributes。装备写回等业务接线留给 P1 起的阶段。
 */
public final class CitizensEntityPlatform implements MaidEntityPlatform {

    private static final String TRAIT_NAME = "tlm";

    private final Plugin plugin;

    public CitizensEntityPlatform(Plugin plugin) {
        this.plugin = plugin;
        registerTrait();
    }

    /** Citizens 要求 Trait 先注册才能 getOrAddTrait；重复注册会抛异常，故先查后注册。 */
    private void registerTrait() {
        try {
            if (CitizensAPI.getTraitFactory().getTraitClass(TRAIT_NAME) == null) {
                CitizensAPI.getTraitFactory().registerTrait(
                        TraitInfo.create(MaidTraitV2.class).withName(TRAIT_NAME));
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[TLM] 注册 MaidTraitV2 失败（可能已由 Citizens 恢复）：" + e);
        }
    }

    @Override
    public int spawn(String name, Location loc) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.setProtected(false);
        npc.getOrAddTrait(MaidTraitV2.class);
        if (!npc.spawn(loc)) {
            plugin.getLogger().warning("[TLM] NPC " + npc.getId() + " 生成失败（位置所在区块未加载？）");
        }
        return npc.getId();
    }

    @Override
    public void despawn(int npcId) {
        NPC npc = npc(npcId);
        if (npc != null && npc.isSpawned()) {
            npc.despawn();
        }
    }

    @Override
    public void destroy(int npcId) {
        NPC npc = npc(npcId);
        if (npc != null) {
            npc.destroy();
        }
    }

    @Override
    public boolean isSpawned(int npcId) {
        NPC npc = npc(npcId);
        return npc != null && npc.isSpawned();
    }

    @Override
    public LivingEntity entity(int npcId) {
        NPC npc = npc(npcId);
        if (npc == null) {
            return null;
        }
        return npc.getEntity() instanceof LivingEntity living ? living : null;
    }

    @Override
    public UUID entityUuid(int npcId) {
        NPC npc = npc(npcId);
        if (npc == null) {
            return null;
        }
        Entity entity = npc.getEntity();
        return entity != null ? entity.getUniqueId() : npc.getMinecraftUniqueId();
    }

    @Override
    public boolean isNpc(Entity e) {
        return e != null && CitizensAPI.getNPCRegistry().isNPC(e);
    }

    @Override
    public void navigate(int npcId, Location loc, float speed) {
        Navigator navigator = navigator(npcId);
        if (navigator == null || loc.getWorld() == null) {
            return;
        }
        // baseSpeed 是原版移速倍率（1.0≈玩家步行；蓝图里写的 speedModifier 不存在，api-facts 3.2）
        navigator.getLocalParameters().baseSpeed(speed);
        navigator.setTarget(loc);
    }

    @Override
    public void navigateEntity(int npcId, Entity target, float speed) {
        Navigator navigator = navigator(npcId);
        if (navigator == null || target == null) {
            return;
        }
        navigator.getLocalParameters().baseSpeed(speed);
        navigator.setTarget(target, false);
    }

    @Override
    public void cancelNavigation(int npcId) {
        Navigator navigator = navigator(npcId);
        if (navigator != null && navigator.isNavigating()) {
            navigator.cancelNavigation();
        }
    }

    @Override
    public boolean isNavigating(int npcId) {
        Navigator navigator = navigator(npcId);
        return navigator != null && navigator.isNavigating();
    }

    @Override
    public void teleport(int npcId, Location loc) {
        NPC npc = npc(npcId);
        if (npc == null || loc.getWorld() == null) {
            return;
        }
        cancelNavigation(npcId);
        npc.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @Override
    public void applyAttributes(int npcId, FavorTier tier, boolean struckByLightning) {
        LivingEntity entity = entity(npcId);
        if (entity == null) {
            return;
        }
        // 雷劈 +20 生命上限（blueprint 0.7）
        double maxHealth = tier.maxHealth + (struckByLightning ? 20 : 0);
        setBase(entity, Attribute.MAX_HEALTH, maxHealth);
        setBase(entity, Attribute.ATTACK_DAMAGE, tier.attackDamage);
        // 升档后要补满；降档后不能超过新上限。每条生成路径都必须调本方法（v1 教训 api-facts 21.3-4）
        entity.setHealth(Math.min(entity.getHealth(), maxHealth));
    }

    @Override
    public void applyEquipment(int npcId, MaidInventory inv) {
        // P0 只搭平台骨架：MaidInventory 的槽位存储由 P1 补齐，届时在此写 0-5 槽 equipment。
    }

    @Override
    public void hideFrom(Player p, UUID entityUuid) {
        Entity entity = entityUuid == null ? null : Bukkit.getEntity(entityUuid);
        if (entity != null) {
            p.hideEntity(plugin, entity);
        }
    }

    @Override
    public void showTo(Player p, UUID entityUuid) {
        Entity entity = entityUuid == null ? null : Bukkit.getEntity(entityUuid);
        if (entity != null) {
            p.showEntity(plugin, entity);
        }
    }

    @Override
    public void attack(int npcId, Entity target) {
        LivingEntity entity = entity(npcId);
        if (entity != null && target != null) {
            entity.attack(target);
        }
    }

    @Override
    public double attackCooldownProgress(int npcId) {
        LivingEntity entity = entity(npcId);
        return entity instanceof HumanEntity human ? human.getAttackCooldown() : 0.0;
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** NPC 未生成时导航不可用——这是真实边界，统一在这里兜住（agent.md 5.6）。 */
    private Navigator navigator(int npcId) {
        NPC npc = npc(npcId);
        if (npc == null || !npc.isSpawned()) {
            return null;
        }
        return npc.getNavigator();
    }

    private static NPC npc(int npcId) {
        return CitizensAPI.getNPCRegistry().getById(npcId);
    }
}
