# TouhouLittleMaid 插件分阶段实施蓝图 —— 车万女仆（TLM）精细还原

> **阅读对象**：负责编码的 AI（DeepSeek v4.1 / GLM 5.3 flash 等执行型模型）。
> **本文档是唯一事实来源（SSOT）**。所有设计决策已经裁决完毕——你的工作是**照写，不要自行设计**：
> 类名、包结构、方法签名、数据结构、数值、配置键、验收标准全部以下文为准；没有写的就不要做。
> 行为数值的唯一基准是 `docs/tlm-behavior-spec.md`（下称**规格书**，写明章节处实现前先读对应节）；
> API 能力边界以 `docs/forge-paper-mapping-report.md`（下称**映射报告**）为准；
> 已验证 API 事实以 `docs/api-facts.md`（下称 **api-facts**）为准。
> **证据标记**：[规格书 x.y] = 行为数值出处；[api-facts x.y] = 已实测/已核 API 事实；[映射报告 x.y] = 能力映射出处；[待验] = Phase 0/实施时必须先验证的项（禁止臆造后直接写死）。
> **伪代码总则**：所有代码块为流程示意，方法名签名落地前按 agent.md 第 4 节对 jar 核对（`javap`）。

## 0. 项目总览

### 0.1 目标与环境

| 项 | 值 |
|---|---|
| 项目 | **TouhouLittleMaid**（Paper 插件；仓库 `Hayuuka/TouhouLittleMaid_Plugin`，本地 `D:\work\TouhouLittleMaid_Plugin`；版本 `2.0.0`）。前身是 CatMaid v1（本地 `d:\work\女仆插件`，冻结为移植参考源，下称 **v1 仓库**——"照抄 v1"的组件全部从那里逐文件移植） |
| 命令 | 唯一命令树 `/tlm`（v1 的 `/catmaid` 退役，切换时公告） |
| 目标 | 按 TLM 1.5.3 行为规格书**精细还原**女仆本体体验：好感档位、22 任务、日程三班制、背包体系、三餐、手持可见、饰品、死亡三段链、语音气泡；祭坛/P 点在主线后段接入；**东方原生系模型（旧命名空间 265 个）纳入可玩模型池**（M 轮，0.12） |
| 服务器 | Paper **26.2**，Java **25**（随 MC 版本迁移策略见 0.4） |
| 实现方式 | 纯插件。禁止 mod、禁止要求玩家客户端安装任何东西（**可选模型包除外**——玩家自愿下载，未下载者以"隐藏"降级，绝无紫黑块，见 0.13） |
| 依赖 | **Citizens2**（2.0.44，硬依赖，实体平台）、**BetterModel**（3.7.x，硬依赖，模型/动画平台）、**CraftEngine**（26.9.x，**硬依赖**，自定义物品/方块/基础资源包统一下发） |
| 资源包 | **两层**（0.13）：基础包（CE 物品 + 基础模型产物，CE 合并统一下发，强制）；扩展模型包（纯模型内容，玩家**手动下载**自愿安装，与 CE 物品完全分离）；`server.properties` 的 `resource-pack/sha1/require-resource-pack` 三键保持清空 |
| 服务器性质 | 非商用免费服（TLM 美术 CC BY-NC-SA 4.0 须署名 TartaricAcid 与 TLM 项目，改编物同协议共享——公告/关于页与 LICENSE-MODELS.md 已有，保持） |

### 0.2 裁决记录（已定，执行时不再讨论）

1. **另起新项目（服主 2026-11-17 终裁）**：不基于 CatMaid v1 仓库升级，而是新建本仓库（TouhouLittleMaid）完整重写；v1 仓库（`d:\work\女仆插件`）整体冻结为移植参考源，"照抄 v1"组件从那里逐文件搬运，不整体复制 src。理由：架构解耦要求 Citizens/BM/CE 调用被压进唯一平台适配层，而 v1 全部行为与 Manager 直接引用这些 API，改造成本等于重写；v1 的 Mode 五模式、铃铛死亡链、右键喂食在 v2 中本来就要被 TLM 原型推翻。
2. **命名与数据键（服主 2026-11-17；后半被 R2 修订 2026-11-18 服主终裁推翻）**：插件名/命令/包名/DB 目录取新名（TouhouLittleMaid、`/tlm`、`dev.touhoulittlemaid`、`plugins/TouhouLittleMaid/tlm.db`）；**PDC 键与 CE 物品 id 一律用 `touhoulittlemaid:` 前缀**（R2 裁决：全新项目零 v1 兼容——原"沿用 `catmaid:` 以零改名兼容 v1 凭证"方案随 v1 兼容整体废止而作废，见 0.10；禁止使用 `catmaid:` 遗留键）。
3. **三项产品裁决（服主 2026-11-17）**：
   - **台词系统完全 TLM 化**：删除 v1 `dialogue.yml` 全部文字台词与 `DialogueManager`；对白表现 = 事件语音（ogg，经 CE 资源包 `sounds.json` 下发）+ 颜文字聊天气泡（TextDisplay）。
   - **删除右键喂食**：TLM 无此交互。好感来源改为三餐/偷吃/睡觉/娱乐等 TLM 渠道（见 P7 数值表）。
   - **魂符先行、祭坛进主线后段**：前期用三态魂符召唤（机制照抄 v1 实现姿势，P1 落地，PDC 键用 `touhoulittlemaid:` 新前缀），祭坛多方块 + P 点作为 P11 独立阶段，完成后成为正式获取/复活仪式（魂符保留为**永久并行获取口**，`config.acquisition.allow-talisman: true`——R2：不再是"v1 老玩家兼容入口"，v1 兼容已整体废止）。
4. **资源包与模型可见性（服主 2026-11-17）**：模型包两层分发——基础包强制（CE 合并统一下发）；**扩展模型包纯手动下载**（玩家自己下载 zip 安装，服务端不推送——模型会越来越多，服务器流量扛不住每次全员下发），插件用 `/tlm pack claim` 自声明（**可 `unclaim` 取消**）；未声明者对该层模型的女仆**整体隐藏**（绝不出现紫黑块）；同时保留 v1 的玩家级"一键隐藏全部女仆"开关（`/tlm hide`，给不喜欢猫娘的玩家）。细则见 0.13。
5. **东方系模型纳入（服主 2026-11-17，本项目核心目标）**：v1 只接入了 27 个酒狐系模型，因为东方原生系模型（旧命名空间 265 个）动画硬编码在 TLM 客户端 Java 里、无 json 可注（2026-11-17 调查已定位：`client/animation/inner/MaidBaseAnimation.java` 等，骨骼为小写 `head/armRight/armLeft/legLeft/legRight`）。本项目以 **M 轮资产管线**（0.12）解决：几何转换修复 + 硬编码动画烘焙 + 挂点骨注入，把东方原生系模型加入可玩池。
6. **依赖策略**：Citizens/BetterModel 保留（自研替代成本远超收益，无免费现成替代品）；三者的调用**只允许出现在 `platform/impl/` 三个适配器内**（0.4 三环纪律）。全部免费可得。
7. **约束变更报告**（对 agent.md 的修订，其余红线全部不变）：
   - 依赖放宽为：Citizens、BetterModel、CraftEngine 三个硬依赖；
   - 禁 NMS、零网络请求（LLM 对话只留架构扩展点、不实现）、sqlite 不 relocate（api-facts 26.3 教训）、文案外置 `messages.yml`——全部沿用；"禁玩家侧安装"的准确口径：**禁止任何强制安装**，扩展模型包是玩家自愿的可选降级路径（未装=隐藏而非报错）；
   - 新增三环纪律（0.4）与平台适配器独占规则；
   - v1 的 `dialogue.yml`/`DialogueManager`/右键喂食/`owner_title` 字段随重构删除。

### 0.3 与 v1 的领域模型变更总表（先读，防止照 v1 直觉写错）

| v1 概念 | v2 替代 | 说明 |
|---|---|---|
| `Mode` 五模式枚举（IDLE/FOLLOW/COMBAT/FARM/RANCH） | **任务（MaidTask，22 个）+ 核心行为（跟随/坐下/拾物/乞食/三餐）+ 日程活动门** | TLM 结构：一次只挂一个任务；跟随是常驻行为不是模式；坐下是硬拦截 [规格书 §3] |
| 好感度 0-100、5 档、百分比加成 | **好感度 0-384、4 档、档位直接重设血量/攻击/触及/横扫/饰品行**（0.7） | [规格书 §2.1] |
| 右键喂食 +1（60s 冷却） | 删除。三餐任务自动进食 + 偷吃 + 睡觉/娱乐/棋局加分 | [规格书 §1.5/§2.2] |
| 死亡 → 铃铛（BELL）+ 钻石复活 | **死亡三段链：墓碑实体（收全部物品）→ 胶片（存女仆数据）→ 神社复活（扣血）/祭坛复活配方** | [规格书 §12]（R2：v1 铃铛不做转换，见 0.10） |
| 台词 `dialogue.yml` 聊天栏文字 | 删除。语音 ogg 事件表 + 颜文字气泡 | [规格书 §9/§11.1 EnableEmoji] |
| 固定 36 格背包（好感扩容） | **8 种背包类型**（empty/small/middle/big/crafting/ender/furnace/tank），换装制，恒 36 物理格 + 类型决定 GUI 可用格数 | [规格书 §7.1] |
| 无日程 | 三班制 DAY/NIGHT/ALL + 家模式三锚点（work/idle/sleep） | [规格书 §1.4] |
| `owner_title`（称呼）字段 | 删除（台词没了，无消费者） | — |
| `max-per-player` 上限 | 保留 config 口径（默认 2，生产连续性），但内部实现改为 **quota 只增不减**（TLM 口径） | [规格书 §12.5] |

### 0.4 三环架构与版本迁移策略

```
环1（纯净域）：core/domain
  —— 只准 import JDK 与本包。禁止任何 Bukkit/Paper/Citizens/BM/CE import。
  —— 事实上的纯数据 + 规则层（Maid/FavorTier/Schedule/…），随 MC 版本零改动。
环2（Paper 层）：core/behavior、core/anim、task、gui、listener、command、data、voice、
                bauble、death、economy、pack、util、event
  —— 准用 Paper/Bukkit 公开 API（事件、World、Inventory、Display 族、ItemStack 等）。
  —— **禁止 import Citizens/BetterModel/CraftEngine 的任何类**；一切平台操作经 platform 三接口。
环3（适配层）：platform（三接口 + impl 三个适配器）
  —— Citizens/BM/CE 的调用全项目只落在这里。BM/CE 因 classloader 隔离走反射（v1 ModelApplier 姿势，api-facts 3.1/24.3-5）。
```

- **审查规则**：环2 出现 `net.citizensnpcs` / `kr.toxicity` / `kr.toxicity188` / CE 任何 FQCN 即为返工；环1 出现 `org.bukkit` 即为返工。以包名前缀静态判别，Phase 0 在 `tools/check_rings.py` 写一个 200 行内的扫描脚本挂进验收。
- **MC 版本迁移 runbook**（"便于随版本更新迁移"的落点，写入 `docs/migration-runbook.md` 于 P12）：
  1. 升 `paper-api` 坐标 → 编译 → 环2 编译错误按映射报告 §2 事件表逐项替换；
  2. `javap` 重核三平台 jar（Citizens/BM/CE）→ 只改 `platform/impl`；
  3. 测试服启动过 Phase 验收清单；
  4. api-facts.md 追加"版本 N 迁移记录"。
  预期改动面 = platform/impl + 环2 事件引用，环1 零改动。

### 0.5 包结构（固定，不得擅自增删顶层包）

```
dev.touhoulittlemaid
 ├─ TouhouLittleMaidPlugin                      // 生命周期入口；装配调度器/平台/注册表
 ├─ core
 │   ├─ domain
 │   │   ├─ Maid                       // 领域对象 v2（字段见 0.7）
 │   │   ├─ MaidManager                // 召唤/移除/查询/恢复/quota
 │   │   ├─ MaidInventory              // 82 槽语义（0.8）
 │   │   ├─ MaidStats                  // 统计（沿用 v1 字段 + 见计数）
 │   │   ├─ FavorabilityManager        // 0-384、档位、来源表（0.7）
 │   │   ├─ FavorTier                  // enum LV0..LV3（0.7 数值）
 │   │   ├─ state/MaidState            // enum ALIVE/FILM/STORED + 合法转移
 │   │   └─ schedule/
 │   │       ├─ ScheduleShift           // enum DAY/NIGHT/ALL
 │   │       ├─ ScheduleActivity       // enum WORK/IDLE/REST/SLEEP
 │   │       ├─ ScheduleData           // 班次 + 三锚点 + 半径
 │   │       └─ AnchorPos              // (world,x,y,z) 值对象
 │   ├─ behavior
 │   │   ├─ BehaviorPipeline            // 每 tick 编排：日程门→任务→常驻行为→动画解析（P3）
 │   │   ├─ TaskRegistry                // 任务注册表（id→MaidTask 实例）
 │   │   ├─ MaidTask                   // 接口（签名见 P5）
 │   │   ├─ TaskContext                 // 平台门面接口（导航/传送/攻击；签名见 P5）
 │   │   └─ common/
 │   │       ├─ CommonBehaviorRegistry  // 常驻行为注册（拾物/乞食/三餐/偷吃/随机看走）
 │   │       ├─ PickupBehavior / BegBehavior / MealBehavior / StealEdibleBehavior
 │   │       └─ RandomLookBehavior
 │   └─ anim
 │       ├─ AnimationSystem             // 主状态机（优先级 0-4，P4/P5 接线）
 │       ├─ HandAnimationSystem         // 手部状态机（hold/use/swing × 三级匹配）
 │       ├─ AnimState / HandAnimState / AnimTrigger   // 枚举与触发
 │       └─ AnimFallback                // 模型缺动画时的降级表（写死，0.9）
 ├─ platform
 │   ├─ MaidEntityPlatform / MaidModelPlatform / ContentPlatform   // 三接口（签名 P0）
 │   └─ impl/
 │       ├─ CitizensEntityPlatform      // 唯一 Citizens import 点
 │       ├─ BetterModelPlatform         // 唯一 BM import 点（全反射）
 │       └─ CraftEngineContent          // 唯一 CE import 点（全反射）
 ├─ task                                // 22 任务（P5/P6 全量；每个类见分阶段表）
 ├─ bauble
 │   ├─ BaubleRegistry / Bauble / effects/*   // 饰品系统（P8）
 ├─ death
 │   ├─ TombstoneManager / FilmManager / ShrineManager   // 死亡三段链（P9）
 ├─ voice
 │   ├─ VoiceManager / VoicePackRegistry / BubbleManager // 语音与气泡（P10）
 ├─ economy
 │   ├─ PowerPointManager / AltarManager / AltarRecipe   // P 点与祭坛（P11）
 ├─ pack
 │   ├─ MaidPackLoader / MaidPackValidator              // 女仆包开放格式（P12）
 ├─ gui                                 // GuiManager / GuiLayout / 各 Holder（P2）
 ├─ data
 │   ├─ Database / MaidRepository / TombstoneRepository / ItemStackCodec
 │   └─ MigrationFromV1                 // v1→v2 一次性迁移器（P1）
 ├─ event                              // 领域事件 Bukkit 化（沿用 v1 模式）
 ├─ listener                           // 只做事件转发（agent.md 5.3）
 ├─ command                            // TlmCommand v2
 └─ util                               // Locations / Text / MaidItems(物品工厂) / JsonUtil
```

### 0.6 数据库 Schema v2（SQLite，`plugins/TouhouLittleMaid/tlm.db`；建表一次成型，后续只许 `ALTER TABLE` 幂等迁移）

```sql
CREATE TABLE IF NOT EXISTS maids (
  id            TEXT PRIMARY KEY,       -- 女仆 UUID
  owner_uuid    TEXT NOT NULL,
  name          TEXT NOT NULL,
  model_id      TEXT NOT NULL,
  voice_pack    TEXT NOT NULL DEFAULT 'peco',   -- 语音包 id（P10）
  task_id       TEXT NOT NULL DEFAULT 'idle',    -- 当前任务（22 之一）
  favorability  INTEGER NOT NULL DEFAULT 0,      -- 0..384（clamp）
  exp           INTEGER NOT NULL DEFAULT 0,      -- 经验（纯展示+玻璃瓶抽取）
  state         TEXT NOT NULL DEFAULT 'ALIVE',  -- ALIVE / FILM / STORED
  home_mode     INTEGER NOT NULL DEFAULT 0,      -- 家模式开关（0=跟随主人）
  work_world TEXT, work_x REAL, work_y REAL, work_z REAL,
  idle_world TEXT, idle_x REAL, idle_y REAL, idle_z REAL,
  sleep_world TEXT, sleep_x REAL, sleep_y REAL, sleep_z REAL,
  sitting       INTEGER NOT NULL DEFAULT 0,
  pickup_type   TEXT NOT NULL DEFAULT 'ALL',    -- ITEMS / XP / ALL
  backpack_type TEXT NOT NULL DEFAULT 'empty',   -- 8 种之一（P6 表）
  npc_id        INTEGER,
  extra         TEXT NOT NULL DEFAULT '{}',      -- JSON：attack_groups、任务私有数据、被雷劈标记
  death_at      INTEGER,
  created_at    INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS maid_items (
  maid_id TEXT NOT NULL,
  slot    INTEGER NOT NULL,               -- 0.8 槽位语义（0..81）
  data    BLOB NOT NULL,                 -- ItemStack#serializeAsBytes()
  PRIMARY KEY (maid_id, slot)
);
CREATE TABLE IF NOT EXISTS maid_stats (
  maid_id  TEXT PRIMARY KEY,
  kills    INTEGER NOT NULL DEFAULT 0,
  harvests INTEGER NOT NULL DEFAULT 0,
  breeds   INTEGER NOT NULL DEFAULT 0,
  shears   INTEGER NOT NULL DEFAULT 0,
  milks    INTEGER NOT NULL DEFAULT 0,
  picks    INTEGER NOT NULL DEFAULT 0     -- 拾物计数（驱动 item_get 语音，每 5 次）
);
CREATE TABLE IF NOT EXISTS tombstones (           -- P9
  uuid       TEXT PRIMARY KEY,                    -- 墓碑 Interaction 实体 UUID
  owner_uuid TEXT NOT NULL,
  maid_id    TEXT NOT NULL,                       -- 关联女仆（FILM 态）
  world TEXT, x REAL, y REAL, z REAL,
  items      BLOB NOT NULL,                       -- 全部槽位物品打包
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS player_prefs (
  uuid        TEXT PRIMARY KEY,
  hide_models INTEGER NOT NULL DEFAULT 0,         -- 玩家级一键隐藏（/tlm hide，沿用 v1）
  quota       INTEGER NOT NULL DEFAULT 0,         -- 女仆额度（只增不减，P1）
  power       REAL NOT NULL DEFAULT 0.0,          -- P 点（P11 启用，列先建好）
  claimed_packs TEXT NOT NULL DEFAULT '[]'        -- 已声明安装的模型包 JSON 数组（0.13，/tlm pack claim）
);
```

### 0.7 核心领域对象（环1，签名照抄）

```java
public final class Maid {
    private final UUID id;
    private UUID ownerUuid;
    private String name;
    private String modelId;
    private String voicePack;
    private String taskId;          // 22 任务 id
    private int favorability;       // 0..384
    private int exp;
    private MaidState state;        // ALIVE / FILM / STORED
    private boolean homeMode;       // false=跟随主人模式
    private final ScheduleData schedule;   // 班次 + 三锚点（homeMode=true 时生效）
    private boolean sitting;
    private PickupType pickupType;  // ITEMS / XP / ALL
    private String backpackType;    // 8 种之一
    private boolean struckByLightning;     // 被雷劈标记：血量上限 +20（P11 引雷配方）
    private final Map<String,String> attackGroups; // AttackListData：entity_id→friendly/neutral/hostile（extra JSON）
    private final MaidInventory inventory; // 82 槽
    private final MaidStats stats;
    private int npcId;             // -1 = 未生成

    public int npcId(); public void npcId(int id);
    // entity()/npc() 不在环1 —— 由 platform 提供（TaskContext.entity()）。
    // 严禁在环1 出现 org.bukkit 类型。
}

public enum FavorTier {   // [规格书 §2.1] 数值写死，可配项只允许整体替换单档数值（config.favorability.tiers）
    LV0(0,   20, 2, 0.0, 1.0, 10),
    LV1(64,  30, 3, 1.0, 2.0, 10),
    LV2(192, 40, 4, 3.0, 3.0, 20),
    LV3(384, 80, 6, 5.0, 4.0, 30);
    public final int threshold;        // 档位下限
    public final int maxHealth;         // 生命上限（含雷劈 +20 另算）
    public final int attackDamage;     // 攻击基值
    public final double reachBonus;     // 触及距离加成
    public final double sweepRange;     // 横扫范围
    public final int baubleSlots;       // 饰品解锁格数
    public static FavorTier of(int favorability);   // >=384→LV3, >=192→LV2, >=64→LV1, else LV0
}

public final class FavorabilityManager {   // [规格书 §2.1/§2.2/§2.3]
    // 档位应用：升/降档 → 重设 MAX_HEALTH 与 ATTACK_DAMAGE 的 baseValue（不是 modifier），
    // 并 setHealth(min(当前血, 新上限))；降档时未解锁饰品行自动掉落（P8 接线）
    // 死亡扣分：-2，以当前档位下限为地板（永不降档）
    // 加分时向附近玩家广播心形粒子（带食物图标粒子只在用餐来源时）
    public void add(Maid maid, int amount, FavorReason reason);   // 冷却表 extra JSON 存
    public void reduce(Maid maid, int amount);                    // clamp(tier 下限, 384)
    public FavorTier tier(Maid maid);
    public int foodTotal(ItemStack food);   // nutrition + nutrition × saturation × 2（FoodComponent 读取走环2 工具，注入）
}

public enum FavorReason {  // (数值, 冷却 tick) —— [规格书 §2.2] 全表
    JOY(2, 24000), SLEEP(2, 24000), PICNIC_SEAT(1, 24000), PICNIC_MEAL(0, 1200),
    WORK_MEAL(1, 3600), STEAL(1, 3600), GOMOKU_WIN(8, 12000), CHESS_WIN(4, 18000),
    DEATH(-2, 12000), FAVORABILITY_TOOL(0, 0);
}
```

```java
public final class ScheduleData {   // [规格书 §1.4]
    private ScheduleShift shift;     // DAY / NIGHT / ALL（默认 DAY）
    private AnchorPos workPos, idlePos, sleepPos;   // 可为 null（null 时该活动不换锚点）
    public ScheduleActivity activityAt(long dayTime);  // 表见下
}
// activityAt 活动表（dayTime = world.getTime()）：
//   DAY:   [0,12000)→WORK  [12000,16000)→IDLE  [16000,24000)→REST→SLEEP   // 18-20 休闲，20 点睡
//   NIGHT: [0,8000)→REST(SLEEP)  [8000,12000)→IDLE  [12000,24000)→WORK
//   ALL:   恒 WORK
// 半径：WORK=12、IDLE=6、SLEEP=6（config 可调，下限 3）
// 锚点切换：活动变化 → 限制中心移到对应锚点；越界（半径+4）→ 0.7 速度走回，走不回传送（最多 10 次）

public enum MaidState { ALIVE, FILM, STORED; }   // 合法转移：ALIVE→FILM(死亡)、ALIVE→STORED(收纳)、
                                                // FILM→ALIVE(复活)、STORED→ALIVE(释放)；其余拒绝
```

### 0.8 MaidInventory 槽位语义 v2（固定，全项目唯一口径）

```
slot 0  = 主手        slot 1 = 副手
slot 2..5 = 头/胸/腿/靴
slot 6..41  = 背包 36 格（物理恒 36；backpack_type 决定 GUI 暴露几格，见 P6 表）
slot 42..71 = 饰品 30 格（FavorTier 解锁 10/10/20/30）
slot 72 = 隐藏槽 1 格（暂存"换手进食"的原手物品，[规格书 §1.5]）
slot 73..81 = 任务槽 9 格（预留：fishing 鱼获、extinguishing 灭火器等；各任务按 id 占用，不用可空）
```

### 0.9 动画资产约定与降级表（AnimFallback，写死）

TLM 通用骨架 97 动作 [规格书 §8.1]，完整键名清单以 `D:\work\_tlm_research\decompiled\assets\touhou_little_maid\animation\maid.animation.json` 为准（2026-11-17 实测抽取）。`inject_tlm_anims.py` 注入清单（25 段，**名称照抄 JSON 原键，含冒号**；名称即 BetterModel `Tracker#animate` 的动画名）：
`idle, walk, run, sit, jump, attacked, death, sleep, swim, swim_stand, ladder_up, ladder_stillness, ladder_down, beg, chair, boat, picnic, game_win, game_lost, use_mainhand:eat, use_mainhand:drink, hold_mainhand:sword, hold_mainhand:bow, hold_mainhand:shield, hold_mainhand:fishing`
（手部状态机的其余键——hold/use/swing 各类别、swing:sword、spyglass、crossbow、spear、axe/pickaxe/shovel/hoe、throwable_potion 等——模型容量允许时由 M1 全量注入，代码侧按 HandAnimationSystem 解析名播放、缺了走降级表。）
**手部动画命名规则（真实约定，2026-11-17 从 json 实测）**：`hold_mainhand:<类别>` / `hold_offhand:<类别>` / `use_mainhand:<类别>` / `use_offhand:<类别>` / `swing:<类别>`（**主手挥击无 hand 段**）/ `swing_offhand:<类别>`；物品精确匹配用 `$` 前缀（如 `hold_mainhand$touhou_little_maid:picnic_basket`）。
**降级表**（模型缺该动画时 `animate` 静默返回 false，api-facts 17.9——按此表回落，禁止报错）：

| 缺失动画 | 降级 |
|---|---|
| sleep / swim / ladder_* / chair / boat / picnic / beg / game_* / hold_* / use_* / swing_* | 回 idle（主状态机）/ 不播（事件动画），只打 debug 日志一次/模型 |
| run | 回 walk |
| sit | 坐下仍生效（导航硬拦截不变），模型保持 idle |
| attacked | 播 `EntityTracker#damageTint()` 红闪代替（映射报告 §3） |
| walk / idle | **资产错误**，启动校验失败并告警（这两个不许缺，v1 教训 api-facts 17.9） |

补播纪律（v1 实测 api-facts 17.9，适配器内部实现）：单次 `animate` 播完停最后一帧不自续 → LOOP 类（idle/walk/run/sit/sleep）由 BetterModelPlatform 内部计时补播；起立/姿态切换必须"归位动画顶掉"而非只 `stopAnimation`。

### 0.10 v1 兼容（R2 整体废止，2026-11-18 服主终裁）

**不做对 v1（CatMaid 旧插件）的任何兼容**：原迁移映射表、`/tlm admin migrate-v1` 命令、`MigrationFromV1`、v1 铃铛→胶片转换、v1 凭证 PDC 互认（api-facts 24.3-6"与 v1 互认"口径作废）全部删除，P1 不再包含迁移器。v1 生产数据**不迁移**——老玩家从零开始（服主知情决策：全新项目零历史包袱，也是数据键改用 `touhoulittlemaid:` 前缀的前提）。v1 仓库仅保留"移植参考源"角色（"照抄 v1"组件的姿势出处，见 agent.md 头部）。魂符三态召唤仍为 v2 自身获取机制（P1 落地，PDC 键用新前缀）。

### 0.11 编码执行规则（对执行模型的硬性要求）

1. 开工顺序：本蓝图全文 → `docs/api-facts.md` 对应节 → 当前阶段代码现状。禁止跳读直接写。
2. **不做的就是不做的**：没有出现在本文档的类、配置键、抽象、校验，一律不写（反过度工程）。三个相似代码块直接复制，不提前抽象。
3. 环纪律（0.4）违反即返工。
4. API 事实规则沿用 agent.md 第 4 节：非基础 API（尤其 BM 反射签名、CE 反射签名、Paper 新事件）调用前先 `javap`/jar 核对，核对不了就用 [待验] 标注 + 降级实现 + `// API_ASSUMPTION:` 注释，并追加到 `docs/api-facts.md`。
5. 阶段纪律沿用 agent.md 第 3 节：按序、每阶段真实启动验收、每阶段结束输出变更清单+验收结果。
6. 玩家可见文案一律 `messages.yml`（MiniMessage）；日志中文前缀 `[TLM]`；实体操作只在主线程；DB 单线程 executor 异步（沿用 v1 `MaidRepository` 模式）。
7. 周期任务错峰纪律：`tick % N == npcId % N`；事件监听器内禁止范围扫描。
8. 体验底线（agent.md 第 10 节继续生效）：宁可少一个功能，不可少一次心跳——语音、粒子、音效是必要成本。

### 0.12 模型资产管线（M 轮，与代码阶段并行的资产工作流）

> M 轮是**资产/工具工作流**，不占 P0–P12 的代码阶段序号；由服主 + 工具协作完成（几何以**人工 Blockbench 转换为金标准**——v1 教训：`tlm_to_bbmodel.py` 全自动几何转换有残余 UV 问题，脚本产物不可直接信）。M 轮每个子轮有自己的验收，其产物是代码阶段的硬前置：**M1 → P4 手持动画全线、M2 → 模型池扩展、M3 → P2 模型 GUI 分层与 0.13 可见性**。

**两条模型路线的事实基线（2026-11-17 调查结论，执行前先读 `model_src/注入报告-2026-11-13.md`）**：

| | 酒狐系（geckolib 命名空间，27 个） | 东方原生系（旧命名空间，约 265 个） |
|---|---|---|
| 几何格式 | 基岩新格式（`minecraft:geometry`，box UV） | 旧格式（`geometry.model`，texturewidth/height，**含 per-face UV cube**——`simplebedrockmodel/BedrockCubePerFace`） |
| 骨骼命名 | 标准骨架 `Root/LeftArm/RightArm/...`（camelCase） | 小写 `head/armRight/armLeft/legLeft/legRight/body/sittingRotationSkirt` |
| 动画来源 | `maid.animation.json`（97 段）+ 每模型 main/condition/tac/iss 分层，**json 可注** | **无 json**——硬编码在 TLM 客户端 `client/animation/inner/MaidBaseAnimation.java / MaidExtraAnimation.java / PlayerMaidAnimation.java / SpecialAnimation.java`（反编译源：`D:\work\_tlm_research\decompiled`） |
| 挂点骨 | **模型自带全套**：`RightHandLocator/LeftHandLocator/BackpackLocator/LeftWaistLocator/RightWaistLocator/SheathLocator/BladeLocator/RifleLocator/PistolLocator`（2026-11-17 从 winefox.json 几何实测确认） | 无，需转换时注入 |

**M1 — 酒狐系管线强化（最先做，P4 的前置）**
1. `inject_tlm_anims.py` 注入动作清单从 7 段扩到 0.9 的 25 段（`--anims` 全量名单；动画名照抄 maid.animation.json 原名，含冒号）。
2. **轴向翻转验证**（注入报告 §7-A 遗留的最高风险）：客户端对 winefox 跑 `walk` 观察四肢；顺拐/反关节 → 注入器对 rotation 的 x/y 取反、position 的 x 取反（Blockbench `compileBedrockKeyframe` 规则）后全量重跑。
3. 新增 `tools/anim_validator.py`（静态校验自动化，注入报告 §5 的人工清单脚本化）：animators 键 ∈ groups uuid、必备动作存在（idle/walk 红线）、molang 跳帧计数报告。
4. **验收**：27 模型全部注入 25 段 + 校验通过 + walk 四肢方向正常 + P4 所需 hold/use/swing 段抽查 3 个模型客户端正常。

**M2 — 东方原生系接入（本项目核心目标，M1 后立刻做）**
1. **几何**：优先路径 = 服主 Blockbench 人工转换（金标准）+ `bbmodel_preview.py` 六视图核对贴图对位；辅助路径 = `tlm_to_bbmodel.py` 修复旧格式与 **per-face UV cube**（`BedrockCubePerFace` 的逐面 UV → bbmodel 逐面 UV）后由脚本产出、人工抽查。产出模型必须带 `RightHandLocator/LeftHandLocator/BackpackLocator` 三骨（各含 0.01 隐形 cube，贴图全透明 1×1）。
2. **动画烘焙**：新工具 `tools/bake_legacy_anims.py`——读反编译的 `MaidBaseAnimation/MaidExtraAnimation` 的过程式动画数学（walk 摆臂=sin(limbSwing) 类），**按固定步长采样烘焙为关键帧 JSON**（骨骼名保持旧小写名），再用 `inject_tlm_anims.py` 按名注入（注入器按名反查 uuid，天然兼容任意骨骼名）。[待验 M2-1：先读 MaidBaseAnimation 确定数学结构与采样策略，禁止臆造公式]
3. **手部状态段**（旧模型在 TLM 里本就没有）：用骨骼映射表（`RightArm→armRight、LeftArm→armLeft、Head→head、UpBody→body、RightLeg→legRight、LeftLeg→legLeft`）把 maid.animation.json 的 hold/use/swing 段映射注入，缺副骨（前臂）自动丢弃（注入器既有行为），观感不可接受则 fallback = 无手部动画（TLM 旧管线原状）。[待验 M2-2：映射后观感]
4. **验收**：抽 5 个角色（hakurei_reimu / kirisame_marisa / konpaku_youmu / izayoi_sakuya / flandre_scarlet）全链通过——几何贴图无错位（preview+客户端）、walk/idle/sit 烘焙动画播放正常、挂点骨可挂物品；通过后批量接入其余模型，进度入 `docs/m-rounds.md`（M 轮专用验收记录）。

**M3 — 模型包分包工具链（P2 的前置）**
1. 新工具 `tools/pack_builder.py`：对 BetterModel 做**逐模型单构建**（models/ 目录每次只放一个模型 → 启动 → 收集 build.zip 文件清单），得到 `model → 资源文件集` manifest。[待验 V0-7：BM 产物 hash 跨构建稳定，同模型两次构建产物逐字节一致]
2. 按 manifest + `config.model-packs` 分层清单拼两个包：**基础包**（基础模型的 textures + 骨骼 item model，并入 CE 合并下发）与**扩展模型包**（其余模型，纯模型内容 zip，发布为手动下载物——GitHub Release / 对象存储，运维口径）。
3. **验收**：manifest 双跑一致；两个包体积报告（基础包目标 ≤3MB）；客户端 A（只装基础包）+ B（全装）对照无紫黑块。

### 0.13 资源包分层与模型可见性（0.2 裁决 4 的落地设计）

**分层**（`config.model-packs`）：
```yaml
model-packs:
  base:                                 # 强制层：并入 CE 包统一下发（贴图+骨骼 item model）
    models: [ foxmaid, winefox, zhiban ]   # 精选 3-8 个，包体积约束下服主可调
  extended:                             # 可选层：手动下载 zip，玩家自愿安装
    models: [ 其余全部 ]                  # 从 config.models 自动推导（models - base 即 extended）
    download-url: "https://…/tlm-model-pack.zip"   # 公告/提示文案引用，服务端不下载不推送
```

**可见性不变量（最高优先级，任何代码不得绕过）**：
> **渲染实体对"未声明拥有该层模型包"的玩家永远不下发**——宁可让玩家看不到别人（或自己的）扩展模型女仆，绝不出现紫黑块。未知状态一律按"未声明"处理（错杀不放过）。

**机制**：
1. `ModelPackManager`（环2，gui 同级）：模型 → 层（base/extended）查询；玩家 claim 集合（`player_prefs.claimed_packs` JSON 数组）读写。
2. claim 通道（纯手动下载模式的唯一检测手段）：
   - `/tlm pack list`：列出层、各层模型数、自己的声明状态、下载地址。
   - `/tlm pack claim`：自声明"我已手动安装扩展包"（诚实系统，服务端无法验证——错杀安全方向：claim 错了顶多看不见，不可能紫黑块）。
   - `/tlm pack unclaim`：**取消声明**（服主 2026-11-17 明确要求可撤销），立即重新对扩展包女仆施加隐藏。
3. 隐藏实施：复用 v1 P11 基建（api-facts 第 28 节全套：骨 display 逐个 hideEntity + 名牌 + 底层实体 + 四挂点 + 低频 sweep 对账）；新增两个挂点：**claim/unclaim 变更时全量 applyAll/restoreAll**、**换模型时按新模型层重判**。
4. 与 `/tlm hide` 的关系：hide（玩家级一键隐藏全部女仆）优先级最高、独立开关；两者同走 MaidVisibilityManager 的施加点，效果叠加（任一触发即隐藏）。
5. 模型选择 GUI：图标按层染色/lore 标注（基础/扩展），选扩展模型时 lore 警示"未安装扩展包的玩家将看不到她"；`/tlm pack claim` 前允许选（她自己的观感取决于自己 claim 了吗）。
6. ~~迁移映射：v1 玩家存量模型归层~~（R2 删除：v1 兼容整体废止，见 0.10——无存量 v1 模型需要归层；新插件选模型即写新档，无此场景）。


---

## Phase 0 — 架构骨架与平台适配 Spike（不写业务代码）

**目标**：新包骨架编译通过；三个平台接口 + 最小适配器在测试服跑通"创建 NPC → 挂模型 → 播动画 → 导航一步 → 销毁"全链路（触发入口 = `/tlm spike` 探针命令，仅控制台日志，R1 修订）；**把 0.11 列出的 [待验] 项全部验证并写入 api-facts.md 第 29 节**。本阶段产物是后续一切阶段的地基。

### 新增文件

| 文件 | 职责 |
|---|---|
| `TouhouLittleMaidPlugin.java` | onEnable：依赖自检（三插件缺一禁用自身）→ Database → 平台装配 → 注册表空装载。tick 管线先空转 |
| `core/domain/FavorTier.java` | R1：好感档位 enum，**0.7 数值完整实现随 P0 落地**——平台接口 `applyAttributes` 签名依赖该类型，无法等到 P1 |
| `core/domain/MaidInventory.java` | R1：`applyEquipment` 签名依赖类型。P0 **仅 82 槽常量**（0.8 口径：SIZE=82、槽区段常量）；槽位物品存储与实体装备写回留 P1 |
| `platform/MaidEntityPlatform.java` | 接口（签名见下） |
| `platform/MaidModelPlatform.java` | 接口 |
| `platform/ContentPlatform.java` | 接口 |
| `platform/impl/CitizensEntityPlatform.java` | 最小实现：spawn/despawn/navigate/cancel/teleport/applyAttributes（applyEquipment 为 P0 空实现占位，P1 实装） |
| `platform/impl/BetterModelPlatform.java` | 最小实现：applyModel/animate（含补播计时）——反射姿势**照抄 v1 `ModelApplier`**（api-facts 3.1/17.5），包名按方法名查找 |
| `platform/impl/CraftEngineContent.java` | 最小实现：customItem/customItemId/playSoundAt——反射姿势照抄 v1 `util/MaidItems`（api-facts 24.3-5/6） |
| `platform/impl/MaidTraitV2.java` | R1：纯数据 Trait（trait 名 `tlm`，存 maidId）。**必须 `extends net.citizensnpcs.api.trait.Trait`，0.4 环纪律禁止环2 import Citizens → 只能落 platform/impl**；P0 的 spawn 链（getOrAddTrait）需要它参与签名 |
| `command/TlmCommand.java` | R1/R2：P0 验证探针 `/tlm spike`，子命令 13 个：`summon/anim/stopanim/sit/stand/nav/pos/attack/sound/seat/bone/destroy/check`（R2 扩至 V0 验收所需全集：sound→V0-1、bone→V0-2、seat→V0-3、attack→V0-4、sit/stand/stopanim→动画观察、pos→导航位移观察）——把平台接口串成验收链，输出**只走控制台日志**（不硬编码玩家可见文案，messages.yml P1 才接入）；P1 正式命令树落地时整体替换 |
| `tools/check_rings.py` | 三环纪律扫描（0.4 规则，200 行内，违规包名列表） |

### 平台接口签名（固定）

```java
public interface MaidEntityPlatform {
    int spawn(String name, Location loc);                 // 返回 npcId；setProtected(false) + getOrAddTrait(MaidTraitV2) 在内部完成
    void despawn(int npcId);                             // = Citizens despawn（保留注册）
    void destroy(int npcId);                             // = Citizens destroy
    boolean isSpawned(int npcId);
    LivingEntity entity(int npcId);                      // null 安全
    UUID entityUuid(int npcId);
    boolean isNpc(Entity e);                             // CitizensAPI.getNPCRegistry().isNPC
    void navigate(int npcId, Location loc, float speed);
    void navigateEntity(int npcId, Entity target, float speed);   // straight-line 选项由适配器按 BM 渲染实测调优
    void cancelNavigation(int npcId);
    boolean isNavigating(int npcId);
    void teleport(int npcId, Location loc);
    void applyAttributes(int npcId, FavorTier tier, boolean struckByLightning); // MAX_HEALTH/ATTACK_DAMAGE baseValue 重设 + setHealth 同步（v1 教训 api-facts 21.3-4：每条生成路径都要调）。R1：首参补 npcId——无它无法定位实体（0.7 原签名笔误，api-facts 29.2-1）
    void applyEquipment(int npcId, MaidInventory inv);  // 0-5 槽写实体 equipment。R1：同上补 npcId；P0 仅签名 + MaidInventory 槽位常量，实体装备写回 P1 实装
    void hideFrom(Player p, UUID entityUuid);            // Player#hideEntity
    void showTo(Player p, UUID entityUuid);
    void attack(int npcId, Entity target);               // LivingEntity#attack
    double attackCooldownProgress(int npcId);            // 见 [待验 V0-4]
}

public interface MaidModelPlatform {
    void applyModel(int npcId, String modelId);          // ModelTrait#setRenderer(BetterModel.modelOrNull(id))
    void animate(int npcId, String animName);            // 内部含 LOOP 补播（0.9），适配器持有 per-npc 补播计时表
    void stopAnimation(int npcId, String animName);
    boolean hasAnimation(int npcId, String animName);    // 启动校验用：模型缺 idle/walk 直接告警
    void damageTint(int npcId);                          // EntityTracker#damageTint()
    List<UUID> boneDisplayEntities(int npcId);           // Tracker#bones()→RenderedBone#getDisplay()!=null（hideEntity 用，api-facts 24.3-3）
    void setBoneItem(int npcId, String boneName, ItemStack item);      // RenderedBone#itemStack，P4 用 [待验 V0-2]
    void clearBoneItem(int npcId, String boneName);      // TransformedItemStack.empty()
}

public interface ContentPlatform {
    ItemStack customItem(String ceId);                   // CraftEngineItems.byId → buildBukkitItem；缺失→null 并告警一次
    String customItemId(ItemStack stack);                // getCustomItemId → "ns:id"；非 CE 物品返回 null
    void playSoundAt(Entity anchor, String key, float volume, float pitch);   // 附近玩家逐个 playSound(key) [待验 V0-1]
    boolean isCustomBlock(Block block, String ceId);
}
```

### Phase 0 必验清单（全部 [待验]，写入 api-facts.md 第 29 节；验证不通过 → 按预案降级并上报，禁止硬写）

| # | 项 | 验证方法 | 失败预案 |
|---|---|---|---|
| V0-1 | CE 资源包内自定义 `sounds.json` + ogg，服务端 `playSound("touhoulittlemaid:maid.hurt")` 能否播放（映射报告 §7 [推断]） | 测试服 CE 资源包放 1 个 ogg + sounds.json，重发包后 RCON 派测试命令 | 不可行则语音改用原版 Sound 近似映射表（P10 兜底方案 B） |
| V0-2 | BM `RenderedBone#itemStack(Predicate, TransformedItemStack)` 在**带 display 的骨骼**上渲染坐标是否随骨骼动画位姿（规格书 §7.2 的核心） | 直接用 winefox（自带 `RightHandLocator` 挂点骨，2026-11-17 实测确认）挂钻石剑 → 走动观察；骨无 display 则给该骨加 0.01 隐形 cube 再试 | 坐标不动/语义不符 → 手持可见降级为 P4 方案 B（独立 ItemDisplay + 骨骼 worldPosition 轮询，v1 HandItemDisplays 改造版） |
| V0-3 | `Interaction` 实体 `setResponsive(true)` 右键触发 `PlayerInteractEntityEvent`，可 `addPassenger` 叠坐（映射报告 §3.1） | 测试命令生成座椅，真人右键 | 不行则座椅用 BlockDisplay + 隐形 ArmorStand 骑乘位 |
| V0-4 | 玩家型 NPC 的攻击强度读取：`HumanEntity#getAttackCooldown()`（映射报告 §10 修正了 v1 伪代码的 `getCooledAttackStrength`）返回值语义 | 测试 NPC 连续 attack，打印冷却值曲线 | 语义不符 → 沿用 v1 的 0.9 闸门策略但记录差异 |
| V0-5 | `EntityDeathEvent`（含 `setReviveHealth`）对玩家型 NPC 是否派发（api-facts 18.3-2 只验了 DamageEvent 兜底） | 杀 NPC 同时挂两事件打日志 | 不派发 → 沿用 v1 双事件 + DamageEvent 兜底幂等方案 |
| V0-6 | Citizens `NavigatorParameters#examiner(BlockExaminer)` 自定义实现可否做到"过门自动开/关门"（映射报告 §1.1 [推断]） | 实现最简 examiner + 门场景实测 | 不可行 → 女仆过门能力砍掉（降级表记录），不做打洞 |
| V0-7 | BetterModel 单模型构建产物 hash 跨构建稳定（M3 分包的可行性前提） | 同一模型两次独立启动构建 build.zip，比对条目逐字节一致 | 不稳定 → 分包改"双全量构建 diff"（全量包 − 基础包，同内容哈希剔除），仍可行但工具更重 |

### 验收清单（Phase 0）

- [ ] `gradlew shadowJar` 通过；Paper 26.2 启动 0 ERROR
- [ ] `tools/check_rings.py` 扫描通过（环1/环2 无平台类 import）
- [ ] `/tlm spike summon <model>` 生成 NPC → 模型渲染 → idle 动画补播可见 → `/tlm spike nav <npcId> 3` 导航 3 格 → `/tlm spike destroy <npcId>` 销毁，全链无异常（探针仅控制台日志；复跑入口见 api-facts 29.3）
- [ ] V0-1..V0-6 全部有结论并写入 api-facts 第 29 节

---

## Phase 1 — 领域核心、数据层与魂符召唤（R2：v1 迁移已删除）

**目标**：0.6/0.7/0.8 的全部对象落库可用；魂符三态召唤落地（机制照抄 v1 姿势，PDC 键用 `touhoulittlemaid:` 新前缀——R2：v1 迁移链已删除）。本阶段结束时 v2 可召唤/持久化，但行为只有待机。

### 新增文件

| 文件 | 职责 |
|---|---|
| `core/domain/**` | 0.7 全部类（Maid/MaidManager/MaidStats/FavorabilityManager/state/schedule）。R1：`FavorTier`、`MaidInventory` 已随 P0 落地（接口签名依赖）——`MaidInventory` 在本阶段补齐槽位物品存储与 `applyEquipment` 实体装备写回 |
| `data/Database.java` | 0.6 建表；连接生命周期同 v1 |
| `data/MaidRepository.java` | CRUD（maids 全字段 + maid_items 全量重写 + maid_stats + player_prefs 读写）；单线程 executor，照抄 v1 模式 |
| `util/MaidItems.java` v2 | 物品工厂/识别器：`soulTalisman()`（空）/ `initTalisman(player)` / `storedTalisman(maid)` / `film(maid)`（P9 前只做数据壳）/ `isXxx(stack)` 三件套。**内部一律先走 ContentPlatform CE 物品（`touhoulittlemaid:talisman` 等，CE 物品定义由服主在 CE 侧维护，文档 `docs/craftengine物品定义.yml` 已有格式），CE 缺失回落原版材质+PDC 并告警**；识别只认 PDC 键 `touhoulittlemaid:soul_talisman`/`touhoulittlemaid:soul_init`/`touhoulittlemaid:maid_id`（R2：不再与 v1 互认，v1 兼容整体废止） |
| `listener/TalismanListener.java` | 魂符三态——**逻辑照抄 v1（api-facts 25.3/25.4）**，仅把物品构造改走 MaidItems v2。三态表与落点校验不变（只认顶面+两格碰撞+右键空气放脚下这条 v1 有意差异保留） |
| `command/tlmCommand.java` 骨架 | `/tlm` 帮助 + `admin summon/remove/list/reload`（收参/权限/Tab 补全框架沿用 v1 P8/P10 成品；R2：`migrate-v1` 子命令删除）。R1：落地时**整体替换 P0 的 `/tlm spike` 探针**（探针使命结束即删，不做别名保留） |
| `platform/impl/MaidTraitV2.java` | R1 修正落点（原 P1 表格误写 `maid/`）：必须 `extends net.citizensnpcs.api.trait.Trait`，0.4 环纪律禁止环2 import Citizens → **只能落 platform/impl，已随 P0 spawn 链落地**。纯数据标记（maidId，trait 名 `tlm`），照抄 v1 结论：`run()` 永不被调、装备写回由 Manager.attach 补做（api-facts 17.2/17.11）。**禁止新建 `maid/` 顶层包** |

### 关键伪代码

```java
// MaidManager.summon（魂符召唤路径调用）
public Maid summon(Player owner, String modelId, Location loc) {
    // 1. quota 校验：player_prefs.quota < config.general.max-per-player（默认 2）
    //    quota 语义：召唤成功 +1；死亡/收纳/送走 **不减**（0.3 口径）
    //    quota 用满了但该玩家存在 FILM 态女仆 → 允许走复活链（P9），召唤口拒绝
    // 2. new Maid（favorability=0（TLM 初始值）、task_id=idle、state=ALIVE、schedule 默认 DAY 班）
    // 3. npcId = entityPlatform.spawn(...)；modelPlatform.applyModel(...)
    // 4. entityPlatform.applyAttributes(npcId, FavorTier.LV0, false)   // 20 血/2 攻（R1：首参 npcId）
    // 5. repository.insert；特效：粒子 HEART + 音效 ENTITY_CAT_AMBIENT + 心形粒子广播（规格书 §2.1）
}
// 随机模型：ModelRegistry 从 ContentPlatform/BetterModel 模型清单随机（服务端已加载模型均匀随机，
// 回退 config.models 第一个 —— [规格书 §6.2]，无玩家选择界面）

// FavorabilityManager.add 的档位应用（通过 platform 接口注入的 EntityPlatform.applyAttributes 完成，
// core 不 import Bukkit：FavorabilityManager 构造注入 `TierApplier` 函数接口，由 app 层绑定适配器）
```

### 配置（config.yml v2 初版，全量默认值即设计值）

```yaml
general: { max-per-player: 2, model-fallback: hakurei_reimu }
favorability: { tiers: { lv1: 64, lv2: 192, lv3: 384 } }   # 只许整体替换单档数值
schedule: { work-radius: 12, idle-radius: 6, sleep-radius: 6, min-radius: 3 }
acquisition: { allow-talisman: true }
models: [ hakurei_reimu, kirisame_marisa, konpaku_youmu, izayoi_sakuya, flandre_scarlet, foxmaid ]
```

### 验收清单

- [ ] `/tlm soul` 领魂符 → 右键顶面召唤 → 手中变空魂符（三态不变）；重启后女仆/好感/任务/背包全恢复
- [ ] 魂符收纳→重启→释放全链保留（v1 行为回归）
- [ ] quota：召唤满 2 只后第三次拒绝；送走（`/tlm release`，0.3 口径）不释放 quota 但 release 允许腾"非占用"说明——**注意**：release 删除 DB 记录时 quota **不减**（TLM 口径 [规格书 §12.5]），`/tlm admin quota <玩家> <数值>` 提供管理员修正

---

## Phase 2 — 实体接线、基础交互与 GUI 骨架

**目标**：右键女仆的完整交互链（TLM §2.5）；主菜单与模型/语音选择 GUI；名牌与可见性开关移植。

### 交互链（MaidInteractListener，严格按 TLM 顺序 [规格书 §2.5]）

```
NPCRightClickEvent（api-facts 3.2：getClicker()）：
  1. clicker 潜行（isSneaking）且是主人 → 切换 sitting（TLM §3.4 坐下方式）；return
  2. 非 owner → 拒绝消息；return
  3. 主手物品判定（按序短路）：
     a. 背包物品（CE id 或 config 判定）→ P6 换装逻辑（本阶段留 TODO 钩子，占位方法）
     b. 剪刀 → 卸下背包（P6）
     c. 名牌 → 改名（消耗 1，≤32 字符，TLM §6.2；v1 的 /tlm rename 保留为等价入口）
     d. 魂符三态（TalismanListener 已在 P1 接管，此处不重复）
  4. 空手/其余 → 打开主菜单 GUI
```

### GUI 布局（GuiLayout 常量，chest 固定网格）

**主菜单**（27 格，`openMain`）：
```
slot 4  = 信息书（名字/好感档位/经验/任务/状态；展示用）
slot 11 = 背包/任务（打开女仆背包 GUI）      slot 13 = 饰品（P8 前灰置提示）
slot 15 = 设置（日程班次/家模式/拾取类型/语音包选择，二级 27 格 GUI，slot 22 返回）
```
**女仆背包 GUI**（54 格，`openMaidBackpack`——TLM §3.3 任务抽屉与背包合一）：
```
row0  slot 0-8   = 任务抽屉：当前页 9 个任务图标（TaskRegistry 注册序），点击切换；
                  slot 0 固定"翻页"改为 slot 45/53 翻页、49 返回主菜单
row1  slot 9-17  = 装备行：9=主手 10=副手 11=头 12=胸 13=腿 14=靴；15-17 灰玻璃
row2-5 slot 18-53 = 背包 36 格（backpack_type 决定可用格数，其余灰玻璃锁——P6 接线，本阶段全开）
```
**模型选择/语音选择**：沿用 v1 54 格分页布局（api-facts 16.x 成品照抄）；模型图标按 0.13 分层染色/lore 标注（基础=绿色✔、扩展=金色+提示"未装扩展包的玩家看不到她"），扩展模型**可选中不禁止**。

### 新增文件

| 文件 | 职责 |
|---|---|
| `gui/GuiManager.java` | openMain / openMaidBackpack / openModelSelect / openVoiceSelect / openSettings；点击/拖拽规则照抄 v1（api-facts 16.5-16.8 的锁定槽/shift/拖拽语义） |
| `gui/MaidMenuHolder.java` | MenuType 枚举扩展（MAIN, BACKPACK, MODEL, VOICE, SETTINGS） |
| `maid/NameTagManager.java` | TextDisplay 悬浮名牌——照抄 v1（api-facts 16.9）；本阶段去掉"名字颜色随 v1 好感档变化"，改为**TLM 风格无档位色**（名字恒定） |
| `maid/MaidVisibilityManager.java` | 玩家级隐身开关——v1 P11 整体移植（api-facts 第 28 节全套：四挂点+对账集+sweep 错峰）；**并扩展为 0.13 的统一隐藏出口**：输入三个判据（hide_models 开关 / 模型层未声明 / 女仆个体），任一命中即隐藏 |
| `maid/ModelPackManager.java` | 0.13 全部机制：分层查询、claim 集合（claimed_packs 读写）、`/tlm pack list\|claim\|unclaim` 命令分支、claim 变更全量重施加、换模型时按层重判 |
| `listener/MaidInteractListener.java` | 上文交互链 |
| `listener/PlayerConnectionListener.java` | 上线补挂/下线保存——v1 移植；onJoin 尾部追加：未 claim 且有 extended 女仆 → 一次性提示（含 download-url，冷却 24h） |

### 验收清单

- [ ] 潜行右键坐下/起立；非主人右键拒绝；空手右键开主菜单
- [ ] 名牌右键改名（≤32 字符、消耗名牌、撞名拒绝——v1 P10.2 查重规则保留）
- [ ] 背包 GUI：任务抽屉翻页显示 22 任务图标（本阶段点击只有 idle/占位提示，P5 接线）、装备行写入实时反映到实体
- [ ] `/tlm hide` 移植回归（v1 P11 验收项原样跑一遍）
- [ ] **模型包可见性（0.13 不变量实测）**：双客户端 A（未 claim）/B（claim）——A 看不见 B 的扩展模型女仆（无紫黑块、无幽灵名牌），B 正常；A `/tlm pack claim` 后立即可见；A `unclaim` 后重新隐藏
- [ ] 模型选择 GUI 分层标注正确；选中扩展模型后 lore 警告出现过
- [ ] 重启后 GUI/名牌/可见性偏好/claim 状态全恢复

---

## Phase 3 — 日程三班制、家模式与日常核心行为

**目标**：BehaviorPipeline 上线：日程活动门 + 跟随/家模式 + 女仆床睡觉 + 拾物 + 乞食 + 随机看走发呆 + 载具同乘。**本阶段起 v1 的 IdleMode/FollowMode 逻辑被完全取代。**

### BehaviorPipeline（每 tick 推行；TouhouLittleMaidPlugin `runTaskTimer(1L,1L)` 唯一驱动——Trait 驱动作废教训 api-facts 17.2）

```java
public final class BehaviorPipeline {   // core/behavior，环2
    // 对每只 ALIVE 女仆（错峰：tick % N == npcId % N 的节流由各行为内部实现，管线不空转：
    //   先做零成本守卫：entity 为 null / 区块未加载 / sitting → 只推动画，跳过行为）
    public void tick(Maid maid, int tick) {
        var activity = homeMode ? schedule.activityAt(dayTime) : Activity.FOLLOW_OWNER;
        // 1. sitting 硬拦截：清导航目标（任何活动/任务都不移动）[v1 P3 口径保留]
        // 2. 常驻行为（CommonBehaviorRegistry，全部先于任务，按注册序）：
        //    Pickup(30t) / Beg(10t) / Meal(P7 接线，本阶段空实现占位) / 随机看(40+rand20 t) / 随机走5格(40+rand20 t，仅 IDLE/REST) / 发呆(30-60t)
        // 3. 活动 → 行为路由：
        //    FOLLOW_OWNER（非家模式）→ FollowOwner 行为（下表数值）
        //    WORK  → TaskRegistry.get(taskId).tick(ctx)     // 任务本体 P5/P6 才有，本阶段只挂 idle
        //    IDLE  → 随机走/看 + StealEdible(P7)
        //    REST/SLEEP → MaidBedTask（下）+ 随机走（半径减半）
        //    家模式越界回收（任何活动内）：距锚点 > 半径+4 → 0.7 速 navigateTo(锚点)；8s 位移<0.5 → teleport(锚点)，累计 10 次放弃本周期
        // 4. AnimationSystem.resolve(maid, activity, tick)   // P4 接线，本阶段占位
    }
}
```

**FollowOwner（非家模式）数值** [规格书 §3.4]：速度 0.5（v1 1.15 **废除**，改用 TLM 值，config `follow.speed: 0.5`）；停距 2；起步距离 = 8−2=6，传送阈值 = 8+2=10（主人周围 ±3/±1/±3 随机立足点，试 10 次，避开水下，`teleportToOwner` 语义 [规格书 §3.4]）；跨世界 → 传送（v1 7 秒延迟保留，api-facts 17.6）。

**MaidBedTask（REST/SLEEP 活动）** [规格书 §3.4/§1.5]：
```
进入 SLEEP 活动且 homeMode：在 sleepPos 半径内找最近 touhoulittlemaid:maid_bed（ContentPlatform.isCustomBlock）
  → navigateTo(床旁 ≤2 格)；到达 → animate("sleep") + setHealth(max)（睡觉回满血）
  + FavorabilityManager.add(SLEEP +2, 冷却 24000)；活动切换/被打 → 起床（归位动画顶掉）
找无床（锚点没配）→ 站在 sleepPos 附近 idle
好感口径照 TLM：每次"真正入睡"即 +2，冷却 24000（一日一次）天然防刷；
起床后再睡不重复触发（同一次 REST 活动内只入一次床）
```

**PickupBehavior** [规格书 §3.4/§1.1]：传感器周期 30t；半径 = 活动半径+1；`pickup_type` 三态（ITEMS/XP/ALL，设置 GUI 改，存 maids.pickup_type）；物品条件照抄 v1 P6 拾物规则（api-facts 20.4：pickupDelay≤0、ticksLived>10、玩家丢的宽限 100t）；经验球：`ALL/XP` 时吸收（exp 累积，stats.picks++ 每拾 5 次留语音钩子）。入包失败 → 跳过。

**BegBehavior** [规格书 §2.5]：主人手持诱惑物（`config.temptation-item: CAKE`）且 6 格内 → navigateTo 主人 2 格内 → 凝视（lookAt）；**不给物品不加分**，纯演出 + `animate("beg")`。

**载具同乘** [规格书 §3.4]：主人上船/矿车/马 → 3 格内女仆 `vehicle.addPassenger(maidEntity)`；主人下车 → 女仆下车（`EntityDismountEvent` 监听）。骑乘中动画 `vehicle$<type>` 降级 sit。

**门与水（降级表）**：
- V0-6 通过 → `CitizensEntityPlatform` 内挂 BlockExaminer（开门/关门/记位置），本阶段实测女仆穿门；
- 水下：Citizens 无两栖寻路（映射报告 §1.1）→ **降级**：女仆落水且导航卡死（8s<0.5 格）→ 直接 straightLineTarget/teleport 到主人身边（沿用 v1 卡死处理），不做换气行为；`swim` 动画照播（淹不死：玩家型 NPC 无氧气条，[实测] 已知）。

### 新增文件

| 文件 | 职责 |
|---|---|
| `core/behavior/BehaviorPipeline.java` | 上文编排 |
| `core/behavior/common/*.java` | Pickup/Beg/RandomLook/StealEdible(占位) 四行为 + 注册表 |
| `core/domain/schedule/ScheduleData.java` 等 | 0.7 签名落地 |
| `listener/VehicleListener.java` | 载具同乘 + EntityDismountEvent |
| `gui` Settings 二级 GUI 接线 | 班次切换（DAY/NIGHT/ALL）、家模式开关、拾取类型、锚点记录入口（配合 P3 物品"河童罗盘"：CE 物品 `touhoulittlemaid:kappa_compass`，右键女仆写入当前脚下三锚点，同维度 ≤1024 校验 [规格书 §1.4/§5.2]；CE 缺失回落原版 `RECOVERY_COMPASS`+PDC） |

### 配置新增

```yaml
follow: { speed: 0.5, stop-dist: 2.0, radius: 8, teleport-tries: 10, cross-world-delay-seconds: 7 }
temptation-item: CAKE
pickup: { sensor-ticks: 30 }
```

### 验收清单

- [ ] DAY 班：白天工作段/18-20 点女仆在 idlePos 附近闲逛/20 点后上床睡觉（回满血 +2 好感，一日一次）
- [ ] 家模式三锚点：越界走回、走不回传送；换班次（设置 GUI）即时生效
- [ ] 非家模式：跟随 0.5 速、停距 2、>10 格传送；骑马/坐船同乘与下车
- [ ] 拾物三种模式；乞食动画（持蛋糕靠近）
- [ ] 女仆过门自动开/关（V0-6 通过时）或已按降级表记录
- [ ] 10 只女仆 TPS 无明显下降（错峰照旧）

---

## Phase 4 — 手持/背部外观可见（骨骼协议 + 手部状态机）

**目标**：主副手物品、背包本体在模型上可见并随手部动画变化。这是规格书差距清单第 1 号（★★★★★）。**依赖 Phase 0 V0-2 结论**。

### 资产前置（M 轮产出，先于 Java 代码）

1. **骨骼协议（名称即协议，缺哪根哪路显示就跳过，[规格书 §7.2]）**：`RightHandLocator` / `LeftHandLocator` / `BackpackLocator`（腰后）。**酒狐系模型已自带全套挂点骨**（0.12 事实基线），无需任何处理；东方原生系模型由 M2 转换时注入三骨（各含 **0.01 尺寸的隐形 cube**，贴图全透明 1×1——纯定位骨无渲染载体，api-facts 24.3-1 教训）。骨无 display 时给骨补隐形 cube 是唯一修复手段。
2. 动画由 M1 全量注入（0.9 清单）；`Tracker#animate(String)` 接受含冒号的动画名 [待验：BM 对冒号动画名的解析，随 V0-2 一起验]。

### Java 侧（环2 + 适配器）

```java
// core/anim/HandAnimationSystem.java（core/anim，环2，只做分类→动画名解析，不触碰平台）
public enum HandSlot { MAIN, OFF }
public enum HandAct { HOLD, USE, SWING }
// 分类解析（三级匹配，[规格书 §8.1]）：物品ID($) > 物品标签(Tag) > 内部类别(:)
//   内部类别表（写死 + config 可扩展）：
//   sword/axe/pickaxe/shovel/hoe → 各类工具；shield；bow；crossbow；gohei(CE id touhoulittlemaid:gohei)；
//   eat(有 FoodComponent)；drink(POTION)；fishing(FISHING_ROD)；empty(空手)
// 产出动画名规则（0.9 真实约定，2026-11-17 实测）：
//   MAIN+HOLD → "hold_mainhand:<category>"    OFF+HOLD → "hold_offhand:<category>"
//   MAIN+USE  → "use_mainhand:<category>"     OFF+USE  → "use_offhand:<category>"
//   MAIN+SWING → "swing:<category>"（无 hand 段！）  OFF+SWING → "swing_offhand:<category>"
//   空手类别 = "empty"（如 hold_mainhand:empty）
public String resolveAnim(HandSlot slot, HandAct act, ItemStack item);

// platform/impl/BetterModelPlatform.java 扩展
// setBoneItem(npcId, "RightHandLocator", item) —— V0-2 通过：直接 RenderedBone#itemStack
// 同步时机（HandItemSyncService，环2）：
//   1. GUI 关闭/换装/任务自动装备处调用 maid.inventory 后 → 立即比对同步（事件驱动，不轮询）
//   2. 兜底低频对账：每 40t 错峰比对实体 equipment 与 MaidInventory 0-1 槽（v1 24.4-2 模式）
//   3. USE 动画：进食(七、三餐)/挥击(战斗 swing)由行为层发 AnimTrigger → AnimationSystem
```

**方案 A（V0-2 通过）**：骨骼直接挂物品。**方案 B（V0-2 失败降级）**：v1 `HandItemDisplays` 改造版——独立 ItemDisplay（THIRD_PERSON 手部 transform）+ 骨骼 `worldPosition()` 每 2t 轮询跟随（接受"不跟手臂动画"的观感损失，v1 已证可跑通）。**验收以方案 A/B 之一达成为准，A 优先。**

背包显示：`backpack_type != 'empty'` → `setBoneItem("BackpackLocator", 背包物品)`；换装/卸下时同步。背部展示物（slot5 旗帜/头颅）**不做**（TLM slot5 是 v1 没有的槽，模型无对应骨，差异声明）。

### 手部状态机接线点（行为层已存在后才完整生效，本阶段先接 HOLD）

| 触发源 | HandAct | 接线位置 |
|---|---|---|
| 主副手物品变化 | HOLD | HandItemSyncService |
| 进食中 | USE:eat | P7 MealBehavior |
| 近战挥击 | SWING(当前类别) | P5 CombatSupport |
| 引雷/受击 | — | 主状态机 attacked（P5） |

### 验收清单

- [ ] 女仆主手拿剑 → 模型手部可见剑且持剑姿势（hold_mainhand:sword）；换面包 → 持面包姿势
- [ ] 行走时手持物随手臂摆动（M1）；或 M2 降级路径观感可接受并已记录
- [ ] 背包换装（small→big）→ 背部背包模型大小变化；卸下消失
- [ ] 缺手部动画的模型自动降级无报错（AnimFallback 生效）

---

## Phase 5 — 任务框架 + 攻击任务族 + 战斗底座

**目标**：TaskRegistry/任务抽屉 GUI 全接线；attack / ranged_attack / crossbow_attack / danmaku_attack / trident_attack 五个攻击任务 + 索敌三分类/AttackListData/护主/恐慌/举盾战斗底座。**v1 CombatMode 被 `attack` 任务完全取代。**

### 框架签名（固定）

```java
public interface MaidTask {   // core/behavior，环2
    String id();                                    // 22 任务 id（TLM 短名，见 P6 总表）
    default boolean isEnabled(Maid maid) { return true; }   // GUI 灰置条件
    String enableConditionKey();                   // messages.yml 键（灰置原因文案）
    default void onEnable(Maid maid) {} default void onDisable(Maid maid) {}
    void tick(Maid maid, TaskContext ctx, int tick);   // 内部自行节流错峰
    default String ambientVoiceKey() { return "mode/" + id(); }
}
public interface TaskContext {   // core/behavior，环2；任务操作平台的唯一门面
    LivingEntity entity();       boolean entityReady();
    void navigateTo(Location loc, float speed);  void navigateToEntity(Entity t, float speed);
    void cancelNavigation();     boolean isNavigating();
    void teleport(Location loc);
    Player owner();               boolean ownerOnline();
    Maid maid();                  MaidInventory inventory();
    void swing(HandSlot slot);   void attack(Entity target);
    double attackCooldownProgress();      // V0-4 结论
    World world();                                        // 世界/方块查询任务直接用 World API
    long now();                                    // 当前 tick
}
// TaskRegistry：LinkedHashMap 保序（注册序 = GUI 抽屉序）；TaskContext 实现类 TaskContextImpl 在环2（桥 platform 单例）。
```

任务基类 `task/BaseTask.java`（环2）提供：`scanBlocks(center, radius, predicate, 错峰)`（v1 P5/P6 扫描模式：同心方形、纵向 ±1~2、每女仆 ≤1 次/秒）、`arriveThen(maid, ctx, loc, range, Runnable)`（导航+8s 卡死拉黑 30s）、`findNearestEntity(...)`。**禁止在事件监听器里扫描**（agent.md 7.4）。

### 战斗底座（task/combat/）

```java
// AttackClassification（三分类 [规格书 §4.1]）：
//   HOSTILE  = e instanceof org.bukkit.entity.Enemy（Monster 系）
//   FRIENDLY = 已驯服 Tameable(isTamed) || Villager || 任意 Citizens NPC（isNpc → 一律 FRIENDLY）
//   NEUTRAL  = 其余：仅当 ①主人最近被打的仇敌 ②主人正在攻击的目标 ③女仆自己被打的仇敌（各 100t 内）
// 排斥（先于三分类，永不攻击）：盔甲架、本项目凭证物品实体（魂符/胶片/墓碑）、
//   自定义名以 "MaidNoAttack" 开头的实体、config.combat.ignore-list（默认空，TLM 默认 robit 我们没有）
// AttackListData：per-maid map（extra JSON `attack_groups`），条目覆盖三分类；命令入口（本阶段）：
//   /tlm attacklist <女仆名> <add|remove|list> [实体类型] [friendly|neutral|hostile]
//   实体类型用 Bukkit EntityType 名；GUI 化入口进未来打磨清单（差异声明）

// 护主/反击（CombatSupport，挂在任何攻击任务上）：
//   OwnerProtectionListener(v2)：主人被 LivingEntity 打 → 该玩家所有攻击任务女仆锁定 attacker（100t）
//   MaidDamageListener(v2)：女仆被 LivingEntity 打 → 攻击任务女仆锁定 attacker；
//     非攻击任务女仆 → 恐慌逃离（PanicBehavior：远离来源 8 格，速度 1.2，持续 5s；8 格内苦力怕恒触发 [规格书 §4.1]）
//   主人打女仆 → 伤害压至 min(amount/5, 2.0)（[规格书 §2.2]，EntityDamageByEntityEvent setDamage）
//   举盾：被投射物打 → 副手有盾则举 100t（视觉=HOLD:shield 动画）；attack 任务目标 <8 格持续举盾
//   [规格书 §4.3]（举盾减伤交给原版玩家实体机制，不额外写伤害修正）

// 仇恨记忆：全局 OwnerThreatIndex / MaidThreatIndex（core/domain 纯数据，环1：
//   owner_uuid → Map<attackerUuid, untilTick>，两表同构，只存 UUID 与 tick）。
```

### 攻击任务规格（tick 间隔全部 10t 错峰 `tick%10==npcId%10`）

**attack（近战）**：
```
索敌半径 = 工作半径 12（可配）；目标死亡/超 20 格脱战 → 缓步回主人（站距 4，v1 P7 结论沿用）
dist > 触及距离（(2.0+tier.reachBonus)²）→ navigateToEntity(speed 1.2)
到达 → 面向；attackCooldownProgress() ≥ 0.9 才 entity.attack(target)（v1 21.3-1 闸门沿用）
横扫（[规格书 §2.1 doSweepHurt]）：主目标被击中后，目标 AABB 膨胀 tier.sweepRange、
  纵向 max(range/4, 0.25)，对范围内非目标活体 damage(1 + 档位序×tier.attackDamage,
  DamageSource 归因女仆)（Damageable#damage(double, DamageSource)，映射报告 §2）
目标死亡 → stats.kills++（杀敌分类计数进 maid_stats 扩展列 total/slime/wither/dragon? 
  ——本期只做 kills 总数，分类计数进未来目标）
```

**ranged_attack（弓）**：背包**必须有箭**（isEnabled 灰置文案 `task.need-arrow`；Infinity 附魔免消耗）；射程 48；`dist ≤ 48 && hasLineOfSight` → 停下面向 → 冷却 2t → `world.spawnArrow`：伤害 = 箭默认伤 ×(tier.attackDamage/2)，速度 `clamp(dist/10, 1.6, 3.2)`，散布随距离降低；`setShooter(女仆实体)`；**箭处理**：`setPickupStatus(DISALLOWED)` + `ProjectileHitEvent` 落地后 20t → 生成 1 支箭 ItemStack 掉落原地（供女仆拾物回收，[规格书 §4.2] "箭可捡回"）；每发消耗背包 1 箭。

**crossbow_attack（弩）**：射程 64；装填期（30t）持弩姿势；射出速度 3.5 固定；其余同弓（烟花分支不做，差异声明）。

**danmaku_attack（弹幕）**：主手必须持御币（CE 物品 `touhoulittlemaid:gohei`，回落原版 `NETHER_STAR`+PDC）；无弹药；每次发射扣御币 1 耐久（耐久 1200）；冷却 `config.combat.danmaku-interval-ticks: 40`（TLM 属性 maid_shoot_cooldown=2.0 秒 [待验单位]）；伤害 = `tier.attackDamage × factor`，factor = 敌数 1 → 1.0 / ≤8 → 1.2 / >8 → 1.5 [规格书 §4.2 近似，系数单位待读反编译核对]；弹形：敌 1 → 单发；≤8 → 8 弹 60° 扇面；>8 → 32 弹 120° 扇面；多重射击附魔 → 3 连发。**弹幕实体实现**：`spawnArrow`（速度 1.5）+ `END_ROD`/`SPELL_WITCH` 粒子尾迹任务（每 2t 沿弹道 spawnParticle，2t 后移除箭）+ `setPickupStatus(DISALLOWED)`；永不命中玩家/同主宠物/忽略名单（`ProjectileHitEvent` 里命中实体在这些类里取消伤害）。弹幕专属音效走 P10 语音表。

**trident_attack（三叉戟）**：主手三叉戟；蓄力 ≥30t（持掷姿势动画）；投出 `setPickupStatus(DISALLOWED)` 且命中/落地后**不返还**（扣 1 耐久 [规格书 §4.2]）；冷却 = `config.combat.trident-interval-ticks: 400` [待验：TLM 属性 maid_trident_cooldown=20.0 的单位，读反编译定]；射程 48；伤害走原版三叉戟（spawnArrow 不适用 → `world.spawn(Trident)`? 不可 → 用 Arrow 仿制 + 原版三叉戟伤害值 8×tier 系数；**[待验 V5-1：Paper 能否 spawn 三叉戟投射物（EntityType.TRIDENT）]**，不行则降级为箭）。

**idle 任务**：无行为；雪天彩蛋（雪球互掷）进未来目标清单。

### 配置新增

```yaml
combat: { radius: 12, lose-dist: 20, sweep-enabled: true, danmaku-interval-ticks: 40,
          trident-interval-ticks: 400, ranged-range: { bow: 48, crossbow: 64, danmaku: 64, trident: 48 },
          ignore-list: [], panic-duration-ticks: 100 }
```

### 验收清单

- [ ] 任务抽屉点击切换 22 任务 id 生效（本阶段 5 个攻击 + idle 有真实行为，其余灰置"未实装"——**注册全部 22 个壳**，未实装的 tick 空转 + GUI 显示禁用文案，防 P6 反复改 GUI）
- [ ] 好感 384 档（80 血/6 攻）与 0 档（20 血/2 攻）血条与伤害实测差异（migrate-v1 造一只 384 的测试女仆）
- [ ] 横扫：3 只僵尸贴脸 → 一刀全部掉血；主人被打群起反击；主人打女仆伤害被压
- [ ] 弓：箭消耗/捡回循环（射 10 发 → 女仆捡回若干）；弹幕单发/扇面/耐久消耗
- [ ] 三分类：村民/已驯服狼永不打；中立怪只有仇恨链内才打
- [ ] 非攻击任务女仆被打 → 逃离；苦力怕逼近 → 逃离

---

## Phase 6 — 背包类型体系 + 农牧/环境任务批

**目标**：8 种背包类型（tank 除外）；farm/feed_animal/shears/milk/sugar_cane/melon/cocoa/honey/grass/snow/torch/feed 十一个任务上线。**v1 FarmMode/RanchMode 被任务完全取代。**

### 背包类型（BackpackType，[规格书 §7.1]；换装=手持背包物品右键女仆，旧背包返还玩家、超容格掉落、消耗 1、20t 冷却；剪刀右键=卸下）

| 类型 | GUI 可访问格（36 物理格中的前 N） | 附加功能 |
|---|---|---|
| empty（默认） | 6 | — |
| small / middle / big | 12 / 24 / 36 | — |
| crafting_table | 18 | GUI 内 3×3 合成区（CraftInventory 桥接：任务槽 73-81 作合成格） |
| ender_chest | 6 | GUI 下半区显示主人末影箱（只读+存取，27 格） |
| furnace | 18 | GUI 内熔炉 3 格（燃料/输入/输出）；**BurnTime/CookTime 持久化进 extra JSON，女仆在线时由管线每 20t 推进烧炼**（[规格书 §7.1] [推断 FurnaceBackpackData 同构]） |
| tank | — | **不做**（Bukkit 流体建模成本高，未来目标清单，差异声明） |

背包物品：CE 物品 `touhoulittlemaid:backpack_small/middle/big/crafting/ender/furnace`（回落原版 `BUNDLE`? 不——回落 `CHEST/MINECART` 材质系+PDC，样式丑但功能一致，告警提示服主补 CE 定义）。

### 任务规格（BaseTask 辅助 + 数值表；未注明处读 [规格书 §3.2] 对应行）

| 任务 id | 行为规格 | 关键数值/备注 |
|---|---|---|
| farm | 扫描半径 12（=工作半径）内成熟作物（Ageable maxAge）；**空手模式（TLM 默认）**：`getDrops(手)` 直接入包 → `setBlockData(同作物 age 0)`（免补种）；**持锄模式**：收成入包 + 从背包扣种子补种（v1 P5 逻辑，种子映射表沿用 api-facts）；6 种作物 + 下界疣 | 检查 ≤120+rand(120) t；收成入包失败 → 掉原地 |
| feed_animal | 半径内可繁殖动物按类分组；成年对 ≥2 且背包有对应饲料（v1 P6 饲料表沿用）→ `setLoveModeTicks(600)`（**必须加 `!isLoveMode()` 防重复喂**，api-facts 20.3/20.6 血泪教训） | 可见动物数 ≥50 → 停 + 气泡提示；≥48 → 转为**攻击动物**控数量 [规格书 §3.2-19]（config 可关 `feed-animal-cull: true`） |
| shears | 主手剪刀（isEnabled 灰置）；最近有毛羊 → 剪；**羊毛掉地上**（撒地，由拾物行为回收——TLM 原样 [规格书 §3.2-16]，与 v1"直接入包"不同） | `sheep.setSheared(true)` + 掉落对应色羊毛；stats.shears++ |
| milk | 空桶 → 最近成年牛（非蘑菇牛）→ 牛奶桶 | stats.milks++ |
| sugar_cane | 收 2+ 格甘蔗（列高 ≥3 时收割至剩 1）；种植逻辑 **[待验：读 `D:\work\_tlm_research\decompiled` 的 SugarCane 任务确认]**，fallback = 只收不种 | 收成入包 |
| melon | 收南瓜/西瓜 + 相邻成熟瓜藤（只收不种）；精准采集镐 → 掉整瓜 | — |
| cocoa | 收 age≥2 可可；补种：背包有可可豆 → 丛林木侧面 `setBlockData(cocoa age0)`，一次决策最多 4 个 | — |
| honey | 半径内蜂巢 `honey_level ≥ 5`：主手剪刀 → 3 蜜脾；否则空玻璃瓶 → 1 蜂蜜瓶；`honey_level = 0` | 被蜂蜇不管（TLM 无蜇处理记录） |
| grass | 清高草/花/双层植物（TallGrass/Flower/双高植物 tag `#minecraft:replaceable`? 用固定 Material 清单写死 config） | breakNaturally → 撒地 |
| snow | 清雪片层：有铲 → 掉落物撒地；无铲 → 直接消失 | — |
| torch | 背包有火把 → 扫描放置点上方光照 <9（纵向 ±2）的位置放火把 | 检查 120+rand(120) t；放置记位置进 extra（防重复放） |
| feed（喂主人） | 按优先级喂主人 [规格书 §3.2-15]：①主人有效负面效果且背包有牛奶 → 喂奶；②中毒且有蜂蜜瓶 → 喂蜜；③血 <50% 且有金苹果 → 喂（HIGH）；④饥饿（foodLevel<18）且食物有富余 → 喂 | 喂 = 递到主人背包 + 挥手动画 + 音效；"濒死才喂 LOWEST"档：foodLevel<6 |

任务槽（73-81）分配：crafting 用 73-81 合成格；furnace 用 73 燃料/74 输入/75 输出。

### 验收清单

- [ ] 8 种背包换装/卸下/超容掉落/冷却；crafting 合成出成品；furnace 女仆拿着时持续烧出铁锭（离线不烧）
- [ ] farm 空手/持锄两模式对照实测；feed_animal 繁殖 + 50 只上限停手（造 50 只鸡验证）
- [ ] shears 羊毛撒地 → 拾物自动回收闭环；honey 剪刀/玻璃瓶两分支
- [ ] torch：夜间在草原点亮区域，不重复放；feed：给中毒主人喂蜂蜜瓶
- [ ] 全部任务在任务抽屉可切换，灰置条件文案正确（弓兵无箭、shears 无剪刀）

---

## Phase 7 — 三餐与生活行为（好感的主要来源）

**目标**：工作餐/自愈餐/野餐/偷吃四条进食链 + 乞食已在 P3。**右键喂食自此永久移除**（0.2 裁决）。

### MealBehavior（common，[规格书 §1.5] 全表）

```
进食能力前提（吃饭总流程，[规格书 §1.5 底部]）：
  双手找食物 → 没有则从背包抽一叠 → 暂存原手物品到 slot72（隐藏槽）
  → animate("use_mainhand:eat")（原版进食时长≈32t，按 Item FoodComponent 无值默认 32t）
  → 吃完：容器返还（碗/瓶 config `meal.return-containers: [BOWL, GLASS_BOTTLE]`）→ slot72 物品回手
工作餐：WORK 活动 && 当前任务 enableEating（任务 config，默认 true）&& 冷却 3600
  → 找食物吃 → 好感 WORK_MEAL：`rand(100) < total ? 0 : 1`（**TLM 原公式，食物越好概率越低——
     已知疑似笔误级设计，照抄**，[规格书 §2.3] 照录标注）；即使 0 分也进冷却
自愈餐：任意活动；缺血 ≥2.0；检查 50+rand(50) t → 吃一份非黑名单食物
  → `rand(5) < total` 时回 `max(total/5, 1)` HP（TLM 原公式照抄）
偷吃（StealEdibleBehavior，IDLE/WORK）：范围内 CAKE 方块（TLM 要求零食柜上——**我们放宽为任意
  放置的蛋糕**，差异声明）→ 走到跟前咬一口：Cake blockdata `bites+1`，咬满 7 → AIR
  → 好感 STEAL +1/口，冷却 3600
野餐（IDLE 活动）：找 CE 方块 `touhoulittlemaid:picnic_mat`（4 座 Interaction，V0-3 实现；占座=骑乘实体存在）
  → 空位坐下 → 吃垫上的食物（座位 1 格内的食物 Item 实体 / mat 方块上表面物品）
  → 好感 PICNIC_MEAL `total/15` 向下取整 + 余数概率进位（冷却 1200）+ PICNIC_SEAT +1（冷却 24000）
黑名单三组（config）：work/heal/picnic 默认 [PUFFERFISH, POISONOUS_POTATO, ROTTEN_FLESH,
  SPIDER_EYE, CHORUS_FRUIT]（[规格书 §2.3]）
```

### 好感全链复核点（本阶段把 [规格书 §2.2] 表全部接线完毕）

- 娱乐方块（IDLE 活动）：范围内 `BOOKSHELF`（原版即可）→ 走去坐下消遣 10s → JOY +2/项/日。电脑/电子琴/棋桌是 TLM 自定义方块，我们只映射书架（差异声明：其余进未来目标，等 CE 自定义方块资产）。
- 睡觉 +2（P3 已接）、棋局 +8/+4（无棋类系统，不接，未来目标）。
- 好感度工具（管理员/创造用）：`/tlm admin favor <女仆名> <add|reduce|full>`（对齐 TLM 创造工具语义：add +64 / reduce 可降档 / full 直满）。

### 验收清单

- [ ] 工作时段女仆自动吃背包面包；好感按 TLM 概率公式增长（100 次抽样统计核对方向）
- [ ] 打到残血 → 自愈餐回血；河豚永不入嘴
- [ ] 放置蛋糕 → 空闲女仆走过去咬一口（bites+1 可见），满 7 口消失
- [ ] 野餐垫 4 座，3 只女仆入座 + 1 只等待；坐姿动画 + 进食动画 + 每日座位好感
- [ ] 书架消遣 +2/日；好感升 LV1 档（64）瞬间：血条 20→30、攻击 2→3 实测可见

---

## Phase 8 — 饰品系统（Bauble）

**目标**：30 格饰品栏、档位解锁、死亡/受击拦截饰品。**死亡拦截的完整死亡链在 P9，本阶段只做 `onDeath` 返回 true 的取消能力**（P9 接墓碑）。

### 规格（[规格书 §5.3]）

```java
public interface Bauble {   // bauble 包，环2
    String id();            // CE 物品 id 或 vanilla:material
    default void onTick(Maid maid) {}
    default boolean onInjured(Maid maid, EntityDamageEvent e) { return false; }  // true=拦截（cancel+耐久-1）
    default boolean onDeath(Maid maid) { return false; }                          // true=免死
    default void onFavorabilityLevelChange(Maid maid, FavorTier old, FavorTier now) {}
}
// BaubleRegistry：id→Bauble；饰品 GUI（主菜单 slot 13）：54 格 = 30 饰品位（tier.baubleSlots 之前的
// 可用，其余灰玻璃"提升好感解锁"）+ 说明书；拖拽/点击规则照抄 v1 装备 GUI 语义
// 识别：ContentPlatform.customItemId(stack) 命中 CE id，或原版 material+PDC `touhoulittlemaid:bauble`
```

内置饰品（全部先走 CE 物品定义 `touhoulittlemaid:<id>`，缺失回落原版材质+PDC 并告警；耐久用 PDC 计数，**不可修补**）：

| id | 效果 | 耐久 |
|---|---|---|
| vanilla_totem | 原版不死图腾可直接放饰品位：onDeath → 消耗+1 血+原版图腾 buff 组 | 消耗型（1 次性） |
| elixir | 绀珠之药：onDeath → 回满血（免死） | 6 |
| protect_explosion / protect_fire / protect_projectile / protect_magic / protect_fall / protect_drown | 对应 DamageCause 拦截：cancel + 粒子 | 64（TLM 各 32~128 [待验：读 decompiled `item/bauble/*` 耐久常量]，先统一 64 config 可调） |
| dodge_cloth | 受击 30% 概率闪避（cancel，无粒子穿模问题——播 `use_mainhand:empty` 摆动） | 128 |
| item_magnet | 拾物半径 +2 | 无耐久 |
| mute | 禁言（P10 语音静默该女仆） | 无耐久 |

- 受击拦截挂点：`MaidDamageListener` 里按饰品序先于一切（饰品 → 战斗逻辑）。
- **降档掉落**：`FavorabilityManager` 档位下降 → 未解锁行饰品全部掉落女仆脚下 [规格书 §2.1]。
- 饰品 tick 挂 BehaviorPipeline 常驻行为区（每 20t 错峰）。

### 验收清单

- [ ] LV2 解锁 20 格、降档后第 3 行饰品原地掉落
- [ ] 绀珠之药：致命伤回满血并扣 1 耐久；6 次后失效
- [ ] 火焰保护挡岩浆；闪避布概率闪避；磁铁拾物半径实测变大
- [ ] 饰品耐久用 PDC 扣减且铁砧修不掉（不可修补）

---

## Phase 9 — 死亡三段链（墓碑 / 胶片 / 神社复活）

**目标**：女仆死亡 → 墓碑实体收全部物品 → 胶片承载女仆数据 → 神社扣血复活 / 祭坛复活配方（P11 接）。**v1 铃铛死亡链退役**（0.3 变更表）。[规格书 §12] 全链为准。

### 死亡拦截链（严格顺序 [规格书 §12.1]）

```
MaidDamageListener（沿用 v1 双保险：EntityDamageEvent 预判兜底 health-finalDamage<=0
  [api-facts 18.3-2，禁止删兜底] + EntityDeathEvent/PlayerDeathEvent 幂等）：
  handleDeath(maid):
    1. 饰品 onDeath（按饰品栏序）：vanilla_totem → 消耗+setHealth(1)+图腾 buff 组，return
       elixir → 耐久-1+setHealth(max)，return
    2. fire MaidDeathEvent（可取消）
    3. 正常死亡：FavorabilityManager.reduce(DEATH -2，档位下限地板，不降档)
    4. 掉落收集：e.getDrops().clear()（无掉落）
    5. 生成墓碑（下）+ 生成胶片（下）+ state=FILM + repository.save
    6. 给主人发死亡坐标消息（MiniMessage）
    7. 死亡语音 ai/death（P10 接入；本阶段先播原版 ENTITY_CAT_DEATH）
```

### 墓碑（TombstoneManager，[规格书 §12.2]）

```
生成位置：死亡坐标（y 钳制世界底+5 ~ 世界顶）
结构 = 1 个 BlockDisplay（CE 方块 touhoulittlemaid:tombstone 的方块模型；缺失回落
  BlockDisplay(Material.SOUL_SAND) + 说明——观感降级但不阻塞）+ 1 个 Interaction
  （setInteractionWidth(0.8) / Height(1.2) / setResponsive(true)，V0-3），
  两个实体均 setInvulnerable(true)，Interaction 挂 PDC touhoulittlemaid:tombstone
收纳：maid_items 全槽（0..81 非空）+ 背包本体物品 + 胶片 → 全部序列化进 tombstones.items BLOB
台账：tombstones 表（0.6）；onEnable 恢复：表存在而实体丢失 → 原地重建（v1 幽灵身体教训 18.3-6 的反向兜底）
交互：主人右击 Interaction → 逐格还物（塞主人背包，满则掉落原地）；还完自动消失（两实体+表行）
  潜行右击 = 强制取出（TLM 语义）；非主人右击 → 无反应消息
铁砧/爆炸不可破坏：Interaction 不可破坏属性 + EntityDamageEvent 里命中墓碑实体一律 cancel
```

### 胶片（FilmManager，[规格书 §12.3]）

```
film(maid) = CE 物品 touhoulittlemaid:film（缺失回落 Material.HEART_OF_THE_SEA + PDC）
  PDC touhoulittlemaid:maid_id = maid.id；displayName "<name>的胶片"；不堆叠
生成后优先塞主人背包，满 → 掉墓碑顶（Item 实体 setInvulnerable+setGlowing，v1 18.3-3 教训）
胶片承载 = maids 行本身（state=FILM）——**胶片只是钥匙，数据在库**（v1 P10.4 绑定原则沿用：
DB 是事实来源、凭证可补发；补发口 = /tlm film [女仆名]，铃铛态同款）
```

### 神社复活（ShrineManager，[规格书 §12.4-1]）

```
神社方块 = CE 方块 touhoulittlemaid:shrine（缺失回落 config.death.shrine-fallback-block: ENCHANTING_TABLE，
  以 Material 判定——回落模式下任何该方块都算神社）
手持胶片右击神社方块（PlayerInteractEvent RIGHT_CLICK_BLOCK）：
  1. 使用者 == owner 校验
  2. 玩家血量 > 一半 → p.setHealth(0.25)（TLM 原值照抄 [规格书 §12.4]，=1/8 颗心）
  3. state FILM→ALIVE；npc 重新 spawn 在神社上方（v1 复活实测：despawn 状态 npc.spawn 直接可用，api-facts 18.3）
  4. 消耗胶片；复活特效（粒子+音效+on_revive 语音键 ai/tamed）
复活保留：模型/名字/好感/主人/任务/日程；失去（本来就在墓碑）：背包物品/装备/饰品/经验
```

### 验收清单

- [ ] 绀珠之药拦死 → 回满血扣耐久；图腾拦死 → buff 组生效；两件都无 → 死亡链
- [ ] 死亡瞬间：无掉落、墓碑立在原地（不可破坏/推动）、主人背包出现胶片、好感 -2 不降档、收到坐标消息
- [ ] 墓碑右击逐格还物（含大背包物品），还完消失；非主人无效；重启后墓碑仍在
- [ ] 胶片右击神社：血量 >50% 时被扣到 0.25（半心图标可见）、女仆在神社上方复活、装备从墓碑取回后完整
- [ ] 满血玩家直接右击神社 → 拒绝提示（血量不足）
（R2 删除：v1 迁移复验项——v1 兼容整体废止，见 0.10）

---

## Phase 10 — 语音包与表情气泡

**目标**：事件语音（ogg 经 CE 资源包）+ 颜文字气泡。**若 V0-1 失败则启用兜底方案 B**（原版 Sound 近似映射表，`voice.fallback-sounds.yml` 写死每个事件键→原版 Sound）。

### 语音事件表（VoiceManager；[规格书 §9.2] 全表照抄）

| 目录 | 键（短名） | 触发点（已实装的接线，未实装键留表不接线） |
|---|---|---|
| mode/ | idle, attack, range_attack, danmaku_attack, farm, feed, shears, milk, torch, feed_animal, extinguishing, snow | 任务环境音：各任务 tick 内掷 ambient 计时（80~160t 随机重置，触发概率 `config.voice.ambient-chance: 0.006`） |
| ai/ | find_target, hurt, hurt_fire, tamed, item_get, death | find_target=战斗锁定时；hurt=MaidDamageListener（30s 冷却）；hurt_fire=Fire 原因伤害；tamed=召唤/复活；item_get=stats.picks 每 5 次；death=死亡链 |
| environment/ | morning / night / rain / snow（由 idle 细分） | idle 环境音触发时按 world.getTime/isThundering 细分替换 |
| other/ | credit | 不接线（占位） |

```
播放：ContentPlatform.playSoundAt(女仆实体, "touhoulittlemaid:maid.<pack>.<短名><rand>",
      volume 1.0, pitch 1.0) —— 16 格内玩家 [规格书 §9.2]；
资源键规则：每个语音包构建时由 tools/voice_pack_builder.py 从 TLM jar 语音资产生成
  sounds.json：key = touhoulittlemaid:maid.<pack_id>.<短名><n>（n=1..实际数量），播放端随机取 n
继承复用（[规格书 §9.1]，缺键回落）：range_attack/danmaku_attack → attack；
  farm/feed/shears/milk/torch/feed_animal/extinguishing/snow → idle；hurt_fire → hurt
初始语音包：召唤时 75% "peco" / 25% "touhou" 随机 [规格书 §9.2]；GUI（P2 的语音选择）可换
频率：全局 100% × 每女仆 1.0（config.voice.frequency）；mute 饰品静默该女仆
素材授权：TLM jar 语音 ogg（CC BY-NC-SA 4.0，非商用署名口径同模型资产，不入仓库、进部署资产）
```

### 表情气泡（BubbleManager，[规格书 §11.1 EnableEmoji]）

```
载体：TextDisplay，悬于名牌上方（NameTagManager 同款生成模式，api-facts 16.9），
  显示 3s 后删除；文本 = messages.yml `emoji.list` 随机一条颜文字（TLM 图/颜文字权重 10/10，
  我们只做颜文字——图片气泡需要客户端渲染，差异声明）
触发：IDLE/REST 活动每 1200t 检查，概率 50%（TLM EnableEmoji 默认开）
事件气泡（非随机）：feed_animal 达上限 → "waiting" 气泡文案；fishing 找不到椅子类推（未实装任务不接）
API：BubbleManager.show(maid, Component) 供行为层调用（未来 LLM 对话的预留接口）
```

### 配置新增

```yaml
voice: { ambient-chance: 0.006, frequency: 1.0, default-packs: { peco: 0.75, touhou: 0.25 } }
emoji: { enable: true, idle-chance: 0.5, check-ticks: 1200 }
meal: { return-containers: [BOWL, GLASS_BOTTLE], blacklist: { work: [...], heal: [...], picnic: [...] } }
```

### 验收清单

- [ ] 部署 peco/touhou 两包后：召唤 → tamed 语音；被打 → hurt 语音（30s 冷却）；farm 工作 → farm 环境音
- [ ] 继承复用：只有 attack 语音的包 → danmaku 任务播 attack 键
- [ ] 首包 75/25 抽样 20 次符合比例；GUI 换包即时生效
- [ ] 颜文字气泡低频出现、3s 消失、不与名牌重叠
- [ ] （若 V0-1 失败）兜底方案 B 的原版音效全链可听并已记录差异

---

## Phase 11 — 祭坛与 P 点经济

**目标**：祭坛多方块结构 + 御币激活 + 祭位放物 + 配方合成 + P 点货币。**这是获取链的 TLM 化收口**（0.2 裁决：魂符保留为兼容入口，`config.acquisition.allow-talisman: true`）。[规格书 §6] 为准。

### P 点（PowerPointManager）

```
存储：player_prefs.power REAL；上限 5.0、初始 0 [规格书 §6.1]
获取（本期实装渠道）：①钓鱼 10%（PlayerFishEvent State.CAUGHT_FISH）→ 掉落 1 个 P 点实体
  （发光的 Item 实体：CE 物品 touhoulittlemaid:power_point，缺失回落 AMETHYST_SHARD+PDC；setCanPlayerPickup(false)
  + 16 格内玩家右击拾取 +0.16 [规格书 §6.3 妖精值]——实体右击拾取而非原版吸附，防误拾+可做特效）
  ②命令 /tlm admin power <玩家> <数值>
  ③战利品箱/妖精 → 未来目标（差异声明）
玩家死亡（非 keepInventory）扣 1.0 [规格书 §6.1]
溢出：超 5.0 部分按 1/4 折经验直接 give（EntityPowerPoint#transPowerValueToXpValue 同构）
P 点 HUD → 不可能（客户端）；御币右击空气 = actionbar 显示余额（差异声明）
```

### 祭坛（AltarManager，结构以本蓝图写死的简化版为准——TLM 是 8×6×8 大鸟居，我们做成 7×6×3 小鸟居，差异声明）

```
结构（相对核心方块的偏移，核心=御币右击的任意一根柱基；校验 4 朝向旋转）：
  柱：6 根原木（tag #minecraft:logs），两排三列 (x,z)∈{-2,0,2}×{-1,1}，每根 2 高（y=0,1）
  顶梁：红羊毛两条（y=3，z=±1，x∈-3..3）；其上栅栏两条（y=4，同位置）
  栅栏材料：config altar.fence-blocks: [DARK_OAK_FENCE, CRIMSON_FENCE]（TLM tag 的简化）
祭位 = 6 个柱顶面（y=2 的 6 个坐标）：
  手持物右击柱顶面 → 物品以 Item 实体立在柱顶（setCanPlayerPickup(false)+setInvulnerable+
    setWillAge(false)，已放物品记入 altar 实例内存 Map）
  空手潜行右击 → 取回
  放入后自动尝试配方（顺序无关）→ 命中且 P 点够 → 扣 P + 清空全部祭位物品 + 在核心上方生成产物
  P 不足 → "P 点不够"提示，材料保留可重试 [规格书 §6.2]
配方（config `recipes/altar.yml`，本期 7 条 [规格书 §6.2 精选]）：
  spawn_box:        [DIAMOND, IRON_INGOT, GOLD_INGOT, LAPIS_LAZULI, REDSTONE, COAL] 0.5P
  reborn_maid:      [胶片, LAPIS_LAZULI, GOLD_INGOT, REDSTONE, IRON_INGOT, COAL]   0.5P
  spawn_lightning:  [GUNPOWDER×3, BLAZE_POWDER×3]                                    0.2P
  backpack_small/middle/big: [ ...材料同 TLM 数值，写进 yml ]                        0.1/0.2/0.3P
  gohei:            [ ... ]                                                          0.15P
产物语义：
  spawn_box → 生成包装盒（Item 实体+Interaction，右击拆封 → 召唤女仆绑定拆封者，
    quota 校验；TLM 是未驯服+蛋糕驯服，我们直接绑定——差异声明）
  reborn_maid → 胶片转女仆（同神社流程但不扣血、消耗胶片；等于"有钱免痛"路线）
  spawn_lightning → 祭坛上空召唤 LightningBolt → 5 格内自己的女仆 struckByLightning=true
    （血量上限 +20 永久，applyAttributes 重算 [规格书 §1.2]）+ 引雷特效
  背包/御币 → 直接给出 CE 物品
御币 = 弹幕武器兼祭坛激活器（CE touhoulittlemaid:gohei；P5 已实装战斗侧）
```

### 验收清单

- [ ] 钓鱼出 P 点实体 → 右击拾取 +0.16；余额 actionbar；死亡扣 1；超 5 转经验
- [ ] 按蓝图摆出结构 → 御币右击识别（4 朝向）；错摆（缺一根柱/换白羊毛）→ 不识别
- [ ] 6 祭位放钻石系材料 → 0.5P 扣除 → 拆包装盒出女仆；P 不足材料保留
- [ ] 胶片祭坛复活：扣 0.5P 免痛复活；引雷：女仆血上限 20→LV0 40（20+20）
- [ ] 祭位物品取回；拆掉结构后祭位物品不掉虚空（拆结构 → 剩余祭位物品掉落返还）

---

## Phase 12 — 女仆包开放格式与切换收尾

**目标**：第三方"女仆包"目录格式（TLM tlm_custom_pack 的对应物，差异化机会）；v1 参考源归档与全链路收口（R2：v1 迁移链已删除，v1 仅剩归档动作）。

### 女仆包格式（PackLoader/PackValidator；[规格书 §11.2] 的 bbmodel 化）

```
plugins/TouhouLittleMaid/packs/<pack_id>/
  pack.json           # { name, author, version, tier: "base"|"extended", models: [{ id, file, display-name }],
  #                     voice_pack: "<id>"(可选，映射到 P10 语音包) }
  models/<id>.bbmodel # 骨骼名遵守 P4 协议（RightHandLocator 等）；自带 idle/walk 动画（AnimFallback 红线）
  lang/zh_cn.json     # 模型显示名等键
安装：/tlm admin pack validate <pack_id> → 校验（pack.json schema + bbmodel 可被 BM 解析
  + 动画红线检查）→ /tlm admin pack install <pack_id> → bbmodel 复制进
  plugins/BetterModel/models/、lang 键合并进 messages.yml、模型追加进 config.models
  ——**需要重启**（BM build.zip 只在启动时生成，api-facts 14.4-4；文档写明运维口径）
  ——**tier 注册**：pack.json 的 tier 决定模型归层（0.13）；extended 包安装后重跑 M3 的
  pack_builder.py 构建新的扩展模型包 zip 并换发下载物（GitHub Release/对象存储）
卸载：反向删除 + models 列表移除（正被引用的模型拒绝卸载）
```

### v1 归档与收口（本阶段完成，此后不再允许带未验项收口；R2：v1 迁移链已删除）

1. 生产服：停 v1 jar → 部署 v2 → 全量回归（下条清单）→ v1 仓库（`d:\work\女仆插件`）打 tag 归档（R2：无迁移步骤——v1 数据不迁移，老玩家从零开始，见 0.10）。
2. `docs/migration-runbook.md`：0.4 的迁移 runbook 落文档（paper-api 版本步骤 + 平台 jar 重核 + 验收抽取）。
3. `agent.md` 修订提交：按 0.2 约束变更报告同步（CE 硬依赖、三环纪律、平台独占、台词/喂食删除；R2 追加：数据键 `touhoulittlemaid:` 前缀、CE 目录修改须说明义务——AGENTS.md 已同步修订）。
4. `blueprint.md`（v1）头部加"已被 blueprint-v2.md 取代"指向注记。

### 最终验收（全链路，服主双人实机）

- [ ] 新玩家旅程：魂符召唤 → 三餐涨好感 → 升 LV1 血条跳变 → 抽屉切任务干活 → 手持可见 → 死亡 → 墓碑还物 → 神社复活 → 祭坛召唤第二只
（R2 删除：迁移玩家旅程验收项——v1 兼容整体废止，见 0.10）
- [ ] 60 只女仆同服 TPS ≥ 19.5（错峰 + 行为扫描节流全开）
- [ ] 女仆包：第三方 bbmodel 按格式放入 → validate → install → 重启 → 渲染 + 动画 + 骨骼手持正常
- [ ] 三环扫描脚本 CI 化（挂进 `gradlew check`，可选）
- [ ] `gradlew shadowJar` + 启动 0 ERROR + 全部前序阶段验收清单无回归

---

## 附录 A — 风险与预案

| 风险 | 预案 |
|---|---|
| V0-2 失败（BM 骨骼挂物品语义不符） | P4 走 M2 降级（独立 ItemDisplay 轮询），手持可见是唯一受影响项；记录到 api-facts |
| V0-1 失败（CE sounds.json key 不可播） | P10 兜底方案 B（原版 Sound 映射表）；语音是体验大项，验收按兜底版口径收 |
| BM/CE/Citizens 版本升级破坏反射签名 | 平台调用全部集中在三个适配器 + "按方法名查找"姿势（v1 ModelApplier 已验证该姿势的抗版本能力，api-facts 3.1 修订记录）；迁移按 runbook 走 |
| 60 女仆性能（每只 110 骨 display + 粒子/语音） | 错峰纪律全部保留；display 实体数量与 v1 相同量级（P11 hideEntity 实测 hide 后不发包）；压测不过再砍：弹幕粒子每 4t、ambient 音全局节流（配好 config，不写死） |
| 语音包体积撑大 CE 资源包 | 首发只带 peco+touhou 两包；包是部署资产不进仓库；`voice.fallback-sounds.yml` 保证零资源包也能听 |
| TLM 反编译 SRG 名歧义 | 蓝图中全部 [待验] 数值以 `D:\work\_tlm_research\decompiled` 直读复核为准，不得凭记忆定值 |
| 箭→物品转换刷箭（P5 弓任务） | 每箭落地只转 1 个物品且 20t 延迟；女仆拾物回收是唯一出口（玩家捡不到 pickup=false——Item 实体 `setCanPlayerPickup` 控制 [映射报告 附清单]） |
| danmaku 粒子尾迹开销 | 每弹幕独立 task 不做，统一在 `VoiceManager` 同级的 `DanmakuFxService` 批量：每 2t 扫一次在场弹幕列表 spawnParticle |
| 墓碑实体被第三方清理 | onEnable 恢复轮询（表存在实体丢失即重建），v1 Issue #1 同款兜底 |
| CE 缺物品定义导致观感降级 | 全部物品工厂（MaidItems）走"CE 优先 + 原版回落 + 告警一次"统一模式（v1 24.3-6 已验证）；服主补定义后 reload 即恢复 |
| 玩家型 NPC 全部既有坑 | Trait#run 不调/无玩家 despawn/死亡双事件/attack 冷却/hideEntity 不跨 relog/reloadConfig 换对象——**全部沿用 v1 已验证对策，逐条见 api-facts 对应节，新阶段接线处蓝图已标注**，禁止"优化"掉这些兜底 |
| 东方原生系几何转换残余错位（v1 的老问题：脚本转的都有问题） | 金标准 = 服主 Blockbench 人转 + `bbmodel_preview.py` 六视图核对；脚本（tlm_to_bbmodel.py 修 per-face UV 后）只做辅助产出且必须人工抽查；双闸不过不入池（0.12 M2） |
| 烘焙动画失真（M2 从 Java 硬编码采样关键帧） | 采样步长可调（默认 2t/帧）；对照 TLM 客户端录屏核对 walk/sit；失真不可接受的模型退回"手部映射段 + idle/walk 最低线" |
| claim 诚实性（服务端无法验证玩家是否真装了扩展包） | 错杀安全方向设计（0.13 不变量）：claim 错了顶多看不见，不可能紫黑块；unclaim 可撤销；不做任何网络探测（零网络红线） |
| BM 单模型 manifest 不稳定（M3 前提） | V0-7 预案：改双全量构建 diff；工具更重但产物等价 |
| 扩展模型包越做越大（未来几百模型） | 分包粒度可扩（M3 的 pack_builder 支持多 extended 包 id）；download-url 每包一个；0.13 机制天然支持多包 claim |

## 附录 B — 阶段依赖图

```
P0 ─→ P1 ─→ P2 ─→ P3 ─→ P4 ─→ P5 ─→ P6 ─→ P7 ─→ P8 ─→ P9 ─→ P10 ─→ P11 ─→ P12
严格按编号顺序实施（agent.md 3.1 不并行）。依赖备注：
  M 轮（0.12）是并行资产工作流，不占阶段序号；硬前置关系：
    M1 → P4（手部/事件动画全线）；M3 → P2（模型 GUI 分层与 0.13 可见性）；M2 → P12 全量模型池（越早越好，服主核心目标）
  P4 依赖 P0-V0-2 与 P2 的同步服务，与 P3 无耦合——若 V0-2 结论未出，可先跳 P5 再回补 P4，
  但必须同轮次内补齐，不得跨阶段欠账；
  P3 依赖 P2 交互链；P5 战斗底座依赖 P3 管线与仇恨记忆；
  P9 依赖 P8（饰品 onDeath 拦截）；P10 依赖 P5/P6/P7 的事件接线点；
  P11 依赖 P9（胶片/复活）与 P5（御币武器侧）；P12 依赖全部
```

严格按序实施；每阶段验收清单全勾（真实运行验证）才进下一阶段；不允许带未验项收口（v1 P8 教训已入 agent.md）。

## 附录 C — 未来目标清单（架构已留位，本期一律不实现）

| 目标 | TLM 出处 | 扩展点（实现时只动这里） |
|---|---|---|
| 女仆妖精生态（自然生成/弹幕怪/P 点主力来源/狐像克制） | §6.3 | 新 `world/` 模块：CreatureSpawnEvent 注入 + 实体走 Citizens NPC（怪物型）+ 掉 P 点实体（P11 已有） |
| 女仆备份（180s 自动 3 份） | §11.1 ServerConfig | MaidRepository 快照表 |
| 神社庭灯（吸 P 点/范围 buff） | §5.2 | ContentPlatform 方块 + 行为管线常驻 buff 区 |
| 扫帚（双座飞行） | §5.2 | 载具模块（VehicleListener 扩展）+ BM 模型 + 每t 位移 |
| 相机/照片（完整快照还原） | §5.1 | FilmManager 已是 80% 基建（物品=快照钥匙、DB=数据） |
| 雕像/手办 | §5.2 | 照片系统的衍生 |
| 隙间 wireless_io | §5.2 | Bauble + 每 100t 双向搬运行为 |
| 棋类（五子棋/象棋 + 好感） | §3.2-22 | BoardGamesTask 壳 + GUI 画布（chest 棋盘）——工作量最大，最后做 |
| 雪天雪球彩蛋 / 换载具动画细分 | §3.2-1 | IdleTask / AnimationSystem 触发表 |
| 坦克背包（流体） | §7.1 | BackpackType 补一格枚举 + 流体 GUI 降级（桶计数显示） |
| 盔甲可见 | §7.2 | 需要 CE 侧给模型加盔甲骨骼组 + AnimationSystem 盔甲控制器——资产成本高，单独立项 |
| fishing 任务 | §3.2-20 | 座椅系统（P7 已有）+ 自定义浮漂（Display 位移）+ 钓获入任务槽 |
| extinguishing 简化版以外的完整灭火器实体 | §3.2-21 | 弹幕实体同款基建 |
| LLM 对话 agent | §11.4 | `voice/BubbleManager.show` 是预留出口；实现前须修订 agent.md 零网络红线并二次裁决 |
| 多女仆任务协作/防抢活（**TLM 也没有，差异化机会**） | §3.5 | TaskRegistry 加全局目标锁钩子（任务 tick 前查 `ClaimIndex`：目标被其他女仆认领即跳过） |
| 第三方代码级扩展（TLM api/ILittleMaid 等价物） | §11.3 | 不做（单服插件无第三方开发者）；女仆包（P12）已覆盖资源级开放 |

---

*blueprint-v2.md · 2026-11-17 · R1 修订 2026-11-18（P0 冲突处理：接口签名补 npcId、MaidTraitV2 落点、FavorTier/MaidInventory 随 P0 落地、`/tlm spike` 探针）· R2 修订 2026-11-18（数据键命名空间改 `touhoulittlemaid:`、v1 兼容整体废止、探针集扩至 13 子命令、softdepend+运行时自检裁决、CE 目录修改须说明——均服主终裁；逐项记录见 `docs/blueprint-revisions.md`）· 基于 docs/tlm-behavior-spec.md 与 docs/forge-paper-mapping-report.md 设计；实施期间所有 API 事实修订继续追加到 docs/api-facts.md。*



