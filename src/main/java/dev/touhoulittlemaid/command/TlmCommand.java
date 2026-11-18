package dev.touhoulittlemaid.command;

import dev.touhoulittlemaid.TouhouLittleMaidPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * P0 验证探针命令 `/tlm spike ...`。
 *
 * <p>Phase 0 的验收链（创建 NPC → 挂模型 → 播动画 → 导航一步 → 销毁）需要一个触发入口，
 * 因此本类只做"把平台接口串起来"这一件事，输出全部走控制台日志（玩家可见文案在 P1 接入
 * messages.yml 后由正式命令负责，此处不硬编码聊天栏消息）。
 *
 * <p>P1 的 {@code command/TlmCommand} 骨架会把本类替换成正式命令树。
 */
public final class TlmCommand implements CommandExecutor, TabCompleter {

    private static final String NAME = "TLM-P0";

    private final TouhouLittleMaidPlugin plugin;

    public TlmCommand(TouhouLittleMaidPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("spike")) {
            plugin.getLogger().info("[TLM] 用法: /tlm spike summon|anim|nav|destroy|bone|check ...（P0 验证探针）");
            return true;
        }
        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "summon" -> summon(sender, args);
                case "anim" -> animate(args);
                case "nav" -> navigate(sender, args);
                case "destroy" -> destroy(args);
                case "bone" -> bone(args);
                case "check" -> check(args);
                default -> plugin.getLogger().info("[TLM] 未知 spike 子命令: " + args[1]);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[TLM] spike 参数错误: " + e.getMessage());
        }
        return true;
    }

    private void summon(CommandSender sender, String[] args) {
        String modelId = args.length > 2 ? args[2] : null;
        String name = args.length > 3 ? args[3] : NAME;
        Location loc = sender instanceof Player player
                ? player.getLocation()
                : Bukkit.getWorlds().getFirst().getSpawnLocation();
        int npcId = plugin.entityPlatform().spawn(name, loc);
        if (modelId != null) {
            plugin.modelPlatform().applyModel(npcId, modelId);
        }
        plugin.modelPlatform().animate(npcId, "idle");
        plugin.getLogger().info("[TLM] spike: NPC " + npcId + " 已生成（model=" + modelId + ", loc="
                + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + "), idle=" + plugin.modelPlatform().hasAnimation(npcId, "idle")
                + ", walk=" + plugin.modelPlatform().hasAnimation(npcId, "walk"));
    }

    private void animate(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        String anim = requireArg(args, 3, "animName");
        plugin.modelPlatform().animate(npcId, anim);
        plugin.getLogger().info("[TLM] spike: NPC " + npcId + " 播放动画 " + anim);
    }

    private void navigate(CommandSender sender, String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        double distance = args.length > 3 ? Double.parseDouble(args[3]) : 3.0;
        Location from = plugin.entityPlatform().entity(npcId) != null
                ? plugin.entityPlatform().entity(npcId).getLocation()
                : (sender instanceof Player player ? player.getLocation() : null);
        if (from == null) {
            plugin.getLogger().warning("[TLM] spike: 找不到可用的起点");
            return;
        }
        double yaw = Math.toRadians(sender instanceof Player player ? player.getLocation().getYaw() : 0);
        Location target = from.clone().add(-Math.sin(yaw) * distance, 0, Math.cos(yaw) * distance);
        plugin.entityPlatform().navigate(npcId, target, 1.0f);
        plugin.getLogger().info("[TLM] spike: NPC " + npcId + " 导航至 " + target.getBlockX()
                + " " + target.getBlockY() + " " + target.getBlockZ());
    }

    private void destroy(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        plugin.entityPlatform().destroy(npcId);
        plugin.getLogger().info("[TLM] spike: NPC " + npcId + " 已销毁");
    }

    private void bone(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        String boneName = requireArg(args, 3, "boneName");
        String raw = args.length > 4 ? args[4] : "clear";
        if (raw.equalsIgnoreCase("clear")) {
            plugin.modelPlatform().clearBoneItem(npcId, boneName);
            plugin.getLogger().info("[TLM] spike: 清空骨骼 " + boneName + " 物品");
            return;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("[TLM] spike: 无效材质 " + raw);
            return;
        }
        plugin.modelPlatform().setBoneItem(npcId, boneName, new ItemStack(material));
        plugin.getLogger().info("[TLM] spike: 骨骼 " + boneName + " 挂上 " + material);
    }

    private void check(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        plugin.getLogger().info("[TLM] spike: NPC " + npcId
                + " spawned=" + plugin.entityPlatform().isSpawned(npcId)
                + " uuid=" + plugin.entityPlatform().entityUuid(npcId)
                + " idle=" + plugin.modelPlatform().hasAnimation(npcId, "idle")
                + " walk=" + plugin.modelPlatform().hasAnimation(npcId, "walk")
                + " boneDisplays=" + plugin.modelPlatform().boneDisplayEntities(npcId).size()
                + " attackCooldown=" + plugin.entityPlatform().attackCooldownProgress(npcId));
    }

    private static int requireInt(String[] args, int index, String what) {
        String raw = requireArg(args, index, what);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " 必须是整数: " + raw);
        }
    }

    private static String requireArg(String[] args, int index, String what) {
        if (args.length <= index) {
            throw new IllegalArgumentException("缺少参数 " + what);
        }
        return args[index];
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spike");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spike")) {
            return List.of("summon", "anim", "nav", "destroy", "bone", "check");
        }
        return List.of();
    }
}
