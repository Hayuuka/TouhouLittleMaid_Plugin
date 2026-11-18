package dev.touhoulittlemaid.command;

import dev.touhoulittlemaid.TouhouLittleMaidPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * P0 验证探针命令 `/tlm spike ...`。
 *
 * <p>Phase 0 的验收链（创建 NPC → 挂模型 → 播动画 → 导航一步 → 销毁）需要一个触发入口，
 * 因此本类只做"把平台接口串起来"这一件事，输出全部走控制台日志（玩家可见文案在 P1 接入
 * messages.yml 后由正式命令负责，此处不硬编码聊天栏消息）。
 *
 * <p>P1 的 {@code command/TlmCommand} 骨架会把本类替换成正式命令树。
 */
public final class TlmCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String NAME = "TLM-P0";

    private final TouhouLittleMaidPlugin plugin;

    /** V0-3 探针：当前测试座椅实体，右键后让点击者骑上去。 */
    private UUID seatId;

    public TlmCommand(TouhouLittleMaidPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("spike")) {
            plugin.getLogger().info("[TLM] 用法: /tlm spike summon|anim|nav|destroy|bone|check|pos|attack|sound|seat ...（P0 验证探针）");
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
                case "pos" -> position(args);
                case "attack" -> attack(args);
                case "sound" -> sound(args);
                case "seat" -> seat(args);
                case "bonetf" -> boneTransform(args);
                case "sit" -> sit(args);
                case "stand" -> stand(args);
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

    private void position(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        if (plugin.entityPlatform().entity(npcId) == null) {
            plugin.getLogger().warning("[TLM] spike: NPC " + npcId + " 没有实体");
            return;
        }
        Location loc = plugin.entityPlatform().entity(npcId).getLocation();
        plugin.getLogger().info("[TLM] spike: NPC " + npcId + " pos=" + loc.getX() + "," + loc.getY()
                + "," + loc.getZ() + " navigating=" + plugin.entityPlatform().isNavigating(npcId));
    }

    /** V0-4：发起一次攻击后逐 tick 打印 getAttackCooldown，确认冷却曲线可用。 */
    private void attack(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        var maid = plugin.entityPlatform().entity(npcId);
        if (maid == null) {
            plugin.getLogger().warning("[TLM] spike: NPC " + npcId + " 没有实体");
            return;
        }
        var zombie = maid.getWorld().spawn(maid.getLocation().add(2, 0, 0), Zombie.class);
        zombie.setAI(false);
        zombie.setInvulnerable(false);
        plugin.entityPlatform().attack(npcId, zombie);
        plugin.getLogger().info("[TLM] spike: attack 已发起，开始采样 cooldown");
        var counter = new int[] {0};
        var task = new BukkitTask[] {null};
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double value = plugin.entityPlatform().attackCooldownProgress(npcId);
            plugin.getLogger().info("[TLM] spike: cooldown sample=" + counter[0] + " value=" + value);
            counter[0]++;
            if (counter[0] >= 21) {
                task[0].cancel();
                zombie.remove();
            }
        }, 0L, 2L);
    }

    /** V0-1：以女仆实体为锚点播放自定义声音键。 */
    private void sound(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        String key = requireArg(args, 3, "soundKey");
        var anchor = plugin.entityPlatform().entity(npcId);
        if (anchor == null) {
            plugin.getLogger().warning("[TLM] spike: NPC " + npcId + " 没有实体");
            return;
        }
        plugin.contentPlatform().playSoundAt(anchor, key, 1.0f, 1.0f);
        plugin.getLogger().info("[TLM] spike: 已向附近玩家播放 " + key);
    }

    /** V0-3：在女仆旁生成一个 Interaction 座椅，真人右键后骑上去。 */
    private void seat(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        var maid = plugin.entityPlatform().entity(npcId);
        if (maid == null) {
            plugin.getLogger().warning("[TLM] spike: NPC " + npcId + " 没有实体");
            return;
        }
        Location at = maid.getLocation().add(1.5, 0.6, 0);
        Interaction seat = maid.getWorld().spawn(at, Interaction.class, interaction -> {
            interaction.setInteractionWidth(0.8f);
            interaction.setInteractionHeight(1.2f);
            interaction.setResponsive(true);
        });
        this.seatId = seat.getUniqueId();
        plugin.getLogger().info("[TLM] spike: 座椅已生成 uuid=" + seatId + " at "
                + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "，请右键");
    }

    @EventHandler
    public void onSeatClick(PlayerInteractEntityEvent event) {
        if (seatId == null || !seatId.equals(event.getRightClicked().getUniqueId())) {
            return;
        }
        Player clicker = event.getPlayer();
        var seat = event.getRightClicked();
        plugin.getLogger().info("[TLM] spike: 座椅被 " + clicker.getName() + " 右键");
        if (clicker.isInsideVehicle()) {
            plugin.getLogger().info("[TLM] spike: 玩家已在骑乘状态，跳过");
            return;
        }
        clicker.leaveVehicle();
        seat.addPassenger(clicker);
        plugin.getLogger().info("[TLM] spike: 玩家已上座，passengers=" + seat.getPassengers().size()
                + "，女仆 boneDisplays 不受影响");
    }

    /** 设置骨上物品的原版 display 上下文（THIRDPERSON_RIGHTHAND 等，决定抓握点）。 */
    private void boneTransform(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        String bone = requireArg(args, 3, "boneName");
        String transform = requireArg(args, 4, "transform");
        boolean ok = plugin.modelPlatform().setBoneItemTransform(npcId, bone, transform);
        plugin.getLogger().info("[TLM] spike: 骨骼 " + bone + " 物品上下文 → " + transform
                + (ok ? " 成功" : " 失败"));
    }

    /** V0-3 女仆侧：让女仆坐上最近的 Interaction 座椅，观察 BM display 是否跟随。 */
    private void sit(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        var maid = plugin.entityPlatform().entity(npcId);
        if (maid == null) {
            plugin.getLogger().warning("[TLM] spike: NPC " + npcId + " 没有实体");
            return;
        }
        Interaction seat = null;
        double best = Double.MAX_VALUE;
        for (var e : maid.getWorld().getNearbyEntities(maid.getLocation(), 4, 4, 4)) {
            if (e instanceof Interaction interaction) {
                double distance = interaction.getLocation().distanceSquared(maid.getLocation());
                if (distance < best) {
                    best = distance;
                    seat = interaction;
                }
            }
        }
        if (seat == null) {
            plugin.getLogger().warning("[TLM] spike: 4 格内没有 Interaction 座椅，先 /tlm spike seat " + npcId);
            return;
        }
        plugin.entityPlatform().cancelNavigation(npcId);
        maid.leaveVehicle();
        seat.addPassenger(maid);
        plugin.getLogger().info("[TLM] spike: 女仆已坐上座椅，passengers=" + seat.getPassengers().size()
                + "，boneDisplays=" + plugin.modelPlatform().boneDisplayEntities(npcId).size());
    }

    /** 让女仆离开座椅。 */
    private void stand(String[] args) {
        int npcId = requireInt(args, 2, "npcId");
        var maid = plugin.entityPlatform().entity(npcId);
        if (maid == null) {
            plugin.getLogger().warning("[TLM] spike: NPC " + npcId + " 没有实体");
            return;
        }
        boolean wasRiding = maid.isInsideVehicle();
        maid.leaveVehicle();
        plugin.getLogger().info("[TLM] spike: 女仆已离开座椅（wasRiding=" + wasRiding + "）");
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
            return List.of("summon", "anim", "nav", "destroy", "bone", "check", "pos", "attack", "sound", "seat",
                    "bonetf", "sit", "stand");
        }
        return List.of();
    }
}
