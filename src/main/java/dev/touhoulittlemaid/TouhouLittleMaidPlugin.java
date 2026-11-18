package dev.touhoulittlemaid;

import dev.touhoulittlemaid.command.TlmCommand;
import dev.touhoulittlemaid.platform.ContentPlatform;
import dev.touhoulittlemaid.platform.MaidEntityPlatform;
import dev.touhoulittlemaid.platform.MaidModelPlatform;
import dev.touhoulittlemaid.platform.impl.BetterModelPlatform;
import dev.touhoulittlemaid.platform.impl.CitizensEntityPlatform;
import dev.touhoulittlemaid.platform.impl.CraftEngineContent;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * 插件入口（blueprint 0.5 / Phase 0）。
 *
 * <p>P0 只做三件事：依赖自检（缺一自禁用）→ 装配三个平台适配器 → 起一条空转的 tick 管线。
 * 数据库、领域对象与行为管线分别在 P1/P3 接入。
 */
public final class TouhouLittleMaidPlugin extends JavaPlugin {

    /** 三个硬依赖（blueprint 0.1）；缺任何一个都以中文原因自禁用。 */
    private static final List<String> REQUIRED_PLUGINS = List.of("Citizens", "BetterModel", "CraftEngine");

    private MaidEntityPlatform entityPlatform;
    private MaidModelPlatform modelPlatform;
    private ContentPlatform contentPlatform;

    private BukkitTask tickTask;
    private long ticks;

    @Override
    public void onEnable() {
        if (!checkDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 平台装配：三环纪律的唯一落点（blueprint 0.4）
        this.entityPlatform = new CitizensEntityPlatform(this);
        this.modelPlatform = new BetterModelPlatform(this);
        this.contentPlatform = new CraftEngineContent(this);

        // 注册表空装载：P0 尚无业务注册表（任务/饰品/行为由 P5-P8 接入）

        PluginCommand command = getCommand("tlm");
        if (command != null) {
            TlmCommand executor = new TlmCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // tick 管线先空转（P3 BehaviorPipeline 接管；Trait#run 不会被调用，api-facts 17.2）
        this.tickTask = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("[TLM] 已启用：P0 平台骨架就绪，行为管线空转");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private boolean checkDependencies() {
        boolean ok = true;
        for (String name : REQUIRED_PLUGINS) {
            if (getServer().getPluginManager().getPlugin(name) == null) {
                getLogger().severe("[TLM] 缺少硬依赖插件 " + name + "，本插件将停用");
                ok = false;
            }
        }
        return ok;
    }

    private void tick() {
        ticks++;
    }

    public MaidEntityPlatform entityPlatform() {
        return entityPlatform;
    }

    public MaidModelPlatform modelPlatform() {
        return modelPlatform;
    }

    public ContentPlatform contentPlatform() {
        return contentPlatform;
    }

    /** 已空转的 tick 数（P0 仅用于验证调度器活着）。 */
    public long ticks() {
        return ticks;
    }
}
