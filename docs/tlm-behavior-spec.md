# Touhou Little Maid（车万女仆）1.5.3 行为规格书

> 调研对象：`TartaricAcid/TouhouLittleMaid` 1.5.3（Forge + mc1.20.1，jar 自 Modrinth CDN 下载后 Vineflower 反编译，共 1817 个源文件）。
> 调研目的：作为 CatMaid（Paper 纯插件、玩家零客户端安装）重写蓝图的设计输入。
> 标注约定：**[源码]** = 反编译代码直接验证（FQCN#方法）；**[jar资源]** = jar 内资源文件；**[文档]** = wiki/手册/游戏内文案；**[推断]** = 推断（附依据）。反编译为 SRG 中间名，关键歧义符号已与上游 1.20 分支源码交叉核对。
> 本报告只读不改任何项目代码。已有的本地调研（api-facts §14/§25、pending-bug-verification §7）引用处均已复核。

---

## 目录

1. [女仆实体的数据模型](#1-女仆实体的数据模型)
2. [好感度全表（心情系统不存在）](#2-好感度全表心情系统不存在)
3. [AI 任务体系全貌](#3-ai-任务体系全貌)
4. [战斗细节](#4-战斗细节)
5. [物品全表](#5-物品全表)
6. [召唤与获取（P 点 / 祭坛）](#6-召唤与获取p-点--祭坛)
7. [背包与装备（含手持渲染原理）](#7-背包与装备含手持渲染原理)
8. [模型与动画](#8-模型与动画)
9. [声音系统](#9-声音系统)
10. [世界交互细节](#10-世界交互细节)
11. [配置与开放结构](#11-配置与开放结构)
12. [死亡/复活/防滥用全链路](#12-死亡复活防滥用全链路)
- [附录 A：TLM 与 CatMaid 功能差距清单](#附录-atlm-与-catmaid-功能差距清单)
- [附录 B：服务端可行性备注](#附录-b服务端可行性备注)

---

## 1. 女仆实体的数据模型

### 1.1 存储架构总览

`EntityMaid extends TamableAnimal implements CrossbowAttackMob, IMaid` [源码] `entity/passive/EntityMaid`。数据分四层：

| 层 | 内容 |
|---|---|
| SynchedEntityData（同步字段） | 模型 ID、语音包 ID、任务 UID、背包类型、好感度、经验、日程班次、乞食/举盾/拉弩动作位、家模式中心/半径、聊天气泡、任务数据同步包等 [源码] `EntityMaid#defineSynchedData` |
| NBT（addAdditionalSaveData，约 180 行） | 模型、任务、背包 36 格、饰品 30 格、隐藏槽 1 格、任务槽 9 格、好感度、经验、日程三锚点（SchedulePos）、每女仆配置（MaidSubConfig）、任务数据（MaidTaskDataMaps）、杀敌记录、五子棋段位、AI 对话历史 [源码] `EntityMaid#m_7380_/#m_7378_` |
| Forge Capability | 物品栏对外暴露（手/甲/背包/饰品）；玩家侧 `MaidNumCapability`（女仆数量额度）与 `PowerCapability`（P 点）[源码] `EntityMaid#getCapability`、`capability/*` |
| 全局台账 | `MaidWorldData`（SavedData，存主世界）：按主人 UUID 记录名下每只**在世**女仆/墓碑的 {维度, 区块坐标, 实体 UUID, 名字, 时间戳}；**女仆进入世界时清除记录、区块卸载时登记**——只用于定位，不做归属校验 [源码] `world/data/MaidWorldData`、`EntityMaid#onAddedToWorld/#onRemovedFromWorld` |

### 1.2 养成数值字段一览

| 字段 | 范围/默认 | 现状 |
|---|---|---|
| 好感度 Favorability | 0–384，初始 0 | **唯一情感数值**，见 §2 |
| 饥饿 Hunger | DATA_HUNGER / NBT / `maid_hunger` 属性（默认 20） | **废弃字段**：只有存读，全库无任何增减或行为影响代码 [源码] 全库检索；仅 YSM 动画 Molang 变量在用 |
| 经验 Experience | 无上限；GUI 每 120 经验显示 1 级 | 纯展示+提取：拾取经验球/P 点/熔炉产出累积；玻璃瓶右键女仆 12 经验/瓶抽出；死亡按原版掉落；**不影响任何属性** [源码] `EntityMaid#pickupXPOrb`、`event/maid/GetExpBottleEvent` |
| 心情 Mood | — | **不存在**（全库含语言文件零命中），见 §2.4 |
| 雷劈标记 StruckByLightning | bool | 被雷劈过（祭坛引雷配方）生命上限永久 +20 [源码] `EntityMaid#m_8038_` |
| 杀敌记录 | TotalCount / Slime / Wither / EnderDragon | 仅驱动进度（杀 100/史莱姆 300/凋灵/龙）[源码] `entity/passive/MaidKillRecordManager` |
| 五子棋段位 | Gomoku 胜场 int | 决定下棋 AI 难度 [源码] `entity/ai/brain/MaidGomokuAI#getRank` |

### 1.3 基础属性与回复

- 基础：`FOLLOW_RANGE=64`、`ENTITY_REACH=2.0`、`MAX_HEALTH/ATTACK_DAMAGE` 由好感档位决定（§2.1）[源码] `EntityMaid#createAttributes`、`init/InitAttribute`。
- 自定义属性（均注册为真实 Attribute，可被第三方修改）：`maid_use_item_speed=1.0`、`maid_crossbow_attack_speed=1.0`、`maid_gun_attack_speed=1.0`、`maid_shoot_cooldown=2.0`、`maid_trident_cooldown=20.0`、`maid_pickup_range=0.5`、`maid_passive_use_shield_tick=100.0` [源码] `InitAttribute`。
- 自然回血：血不满时每 tick 0.25% 概率回 1 HP [源码] `EntityMaid#randomRestoreHealth`。
- 睡觉回满血 [源码] `EntityMaid#m_5802_`（入睡时 `setHealth(getMaxHealth())`）。

### 1.4 日程系统（三班制 + 家模式三锚点）

[源码] `entity/ai/brain/MaidSchedule`、`entity/passive/SchedulePos`、`init/InitEntities#ScheduleBuilder`：

| 班次 | tick 0（06:00） | tick 8000（14:00） | tick 12000（18:00） | tick 16000（20:00） | 含义 |
|---|---|---|---|---|---|
| **DAY（默认）** | WORK | — | IDLE | REST | 白天工作、18–20 点休闲、20 点–次日 6 点睡觉 |
| **NIGHT** | REST | IDLE | WORK | WORK | 白天睡觉、18 点–次日 6 点工作 |
| **ALL** | WORK（恒定） | — | — | — | 全天工作 |

- 家模式锚点：`workPos / idlePos / sleepPos` 三个 BlockPos + 维度（用"河童的罗盘"记录，写入女仆 NBT `MaidSchedulePos`）。活动切换时限制中心设到对应锚点，半径 WORK=12 / IDLE=6 / SLEEP=6（可配，min 3）[源码] `SchedulePos#tick`、`MaidConfig.MAID_WORK_RANGE/IDLE_RANGE/SLEEP_RANGE`。
- 越界回收：超出锚点半径 +4 时优先 0.7 速度走回，走不回就传送（最多试 10 次）[源码] `SchedulePos#tick/#teleport`。
- 关闭家模式 → 改为跟随主人模式（跟随行为见 §3.5）。
- 开启家模式校验：锚点须同维度、距女仆 ≤1024 格 [源码] `network/message/MaidConfigMessage#handleHome`。

### 1.5 女仆自己的进食（三类餐饮 + 偷吃）

**重要结论：1.5.3 没有饥饿消耗机制**，吃饭的驱动力是"生命缺口 + 好感度冷却"：

| 类型 | 触发 | 条件 | 效果 | 好感度 |
|---|---|---|---|---|
| 工作餐 WORK_MEAL | 工作时段常驻行为（检查间隔 50+rand(50) tick） | 任务 `enableEating`（默认 true）+ 冷却 3600 tick | 吃手/背包里的食物 | 0 或 +1（公式见 §2.3，即使 0 点也进冷却） |
| 自愈餐 HEAL_MEAL | `MaidHealSelfTask`（CORE 常驻，任意任务生效） | 缺血 ≥2.0、间隔 50+rand(50) tick | 回 `max(total/5, 1)` HP（total=营养公式） | 无 |
| 野餐 HOME_MEAL | IDLE 时找野餐垫（4 座位方块）坐下进食 | 冷却 1200 tick | 吃垫上食物 | `total/15` 点（食物越好越多）+ 落座 +1（每游戏日一次） |
| 偷吃方块 | IDLE/WORK 常驻 `MaidStealEdible*Task` | 范围内有"可食用方块"（蛋糕必须放在零食柜上） | 咬一口 | +1/口，冷却 3600 tick |

[源码] `entity/task/meal/*`、`entity/ai/edible/MaidEdibleBlockManager`、`entity/favorability/Type`。
进食流程：双手找食物 → 从背包抽一叠放到空手（原手物品暂存 1 格隐藏栏）→ 原版进食动画（速度受 `maid_use_item_speed`）→ 吃完把碗等容器按配置返还回背包 [源码] `EntityMaid#memoryHandItemStack`、`MaidEatenReturnContainerList`。

---

## 2. 好感度全表（心情系统不存在）

### 2.1 存储与档位（TLM 的核心养成数值）

[源码] `entity/favorability/FavorabilityManager`（存储 `DATA_FAVORABILITY` + NBT `MaidFavorability`，`Mth.clamp` 到 [0, 384]）：

| 档位 | 点数区间 | 生命上限 | 攻击力 | 攻击距离加成 | 横扫范围 | 饰品栏解锁 |
|---|---|---|---|---|---|---|
| Lv0 | 0–63 | 20 | 2 | +0 | 1.0 | 2 行（10 格） |
| Lv1 | 64–191 | 30 | 3 | +1 | 2.0 | 2 行（10 格） |
| Lv2 | 192–383 | 40 | 4 | +3 | 3.0 | 4 行（20 格） |
| Lv3 | 384（满） | 80 | 6 | +5 | 4.0 | 6 行（30 格） |

应用机制：
- 升/降档时直接重设 `MAX_HEALTH` 与 `ATTACK_DAMAGE` 的 **baseValue**（不是加成 modifier）[源码] `FavorabilityManager#add/#reduceWithoutLevel`。
- 近战触及距离 = `(ENTITY_REACH 2.0 + 好感加成)²` 用于可达判定 [源码] `EntityMaid#m_142593_`。
- 剑横扫：目标 AABB 向四周膨胀档位范围、纵向 `max(range/4, 0.25)`，对范围内非目标实体造成 `1 + 等级×基础攻击` 伤害 [源码] `EntityMaid#doSweepHurt`。
- **降档惩罚**：档位下降时未解锁行的饰品自动掉落地上 [源码] `MaidDropBaubleEvent`。
- 每次加分向附近玩家广播心形粒子（带食物图标）[源码] `FavorabilityManager#add`。

### 2.2 增减来源全表

[源码] `entity/favorability/Type.java`（点数, 冷却 tick；冷却表存 NBT `FavorabilityManagerCounter`）：

| 类型 | 数值 | 冷却 | 触发 |
|---|---|---|---|
| 娱乐方块（书架/电脑/电子琴/五子棋桌） | +2 | 24000（1 游戏日/项） | 坐上对应方块消遣 |
| 睡觉 | +2 | 24000 | 入睡女仆床（每次入睡触发一次） |
| 野餐落座 | +1 | 24000 | 坐上野餐垫座位 |
| 野餐进食 | +1~N（total/15） | 1200 | 吃野餐垫食物 |
| 工作餐 | 0 或 +1 | 3600 | 工作时段进食 |
| 偷吃蛋糕等 | +1/口 | 3600 | 偷吃可食用方块 |
| 五子棋主人赢 | +8 | 12000 | 主人赢女仆（`MaidGomokuOwnerLimit=true` 时仅主人可下） |
| 中/国际象棋主人赢 | +4 | 18000 | 主人将死女仆（两类棋共用同一冷却槽） |
| **死亡** | **−2** | 12000 | 女仆死亡；`reduce()` **以当前档位下限为地板，不会降档** |
| 好感度工具（创造向） | 创造 +1 / 生存 +64；reduce 可降档；full 直接满 | 无 | `UseFavorabilityToolEvent`（已复核：`point = isCreative ? 1 : 64`） |

- **玩家攻击女仆无好感惩罚**，但主人攻击伤害压到 `min(amount/5, 2.0)` [源码] `EntityMaid#m_6469_`。
- 正常游玩约 +10~20 好感/游戏日 [推断：各来源冷却叠加估算]。

### 2.3 食物系统

- **没有"喜欢的食物"正面列表**。"讨厌"= 黑名单：物品标签 `touhou_little_maid:maid_eat_blocklist_item` + 三组 config 黑名单（工作餐/野餐/自愈，默认禁 河豚/毒马铃薯/腐肉/蜘蛛眼/紫颂果）+ 三组正则黑名单 [源码] `MaidConfig`。
- 食物品质只影响好感**概率**：`total = nutrition + nutrition × 饱和系数 × 2`。
  - 工作餐：`rand(100) < total ? 0 : 1` ——注意此公式**食物越好得点概率越低**（上游源码原样如此，疑似笔误级设计，照录）；
  - 野餐：`total/15` 向下取整 + 余数概率进位（正向）；
  - 自愈：`rand(5) < total` 时回 `max(total/5,1)` HP [源码] `DefaultMaidWorkMeal/#DefaultMaidHomeMeal/#DefaultMaidHealSelfMeal`。

### 2.4 心情系统：**不存在**

全代码库（含语言文件）无 mood/心情概念；无衰减、无效率公式、无心情-好感互相作用。与"情绪"沾边的只有娱乐方块（Joy，§2.2 好感来源）与随机表情气泡。**若 CatMaid 蓝图想要心情，属于全新设计，不能"照抄 TLM"。**

### 2.5 喂食交互（与 CatMaid 的"右键喂食"不同）

- **没有右键喂食**。食物放法：GUI 把食物塞进女仆主手/副手槽或背包，由三类餐饮任务自动吃 [文档] Patchouli 手册原文 + [源码] `EntityMaid#m_6071_`。
- 右键女仆的其他交互链：主人 GUI 打开 → 物品自身 `interactLivingEntity`（背包换装/铃绑定/转让工具/好感工具等拦截）→ 未驯服时用驯服物品（默认蛋糕）驯服。
- 手持诱惑物品（默认也是蛋糕）时 6 格内女仆会走近贴脸"乞求"（beg 动画），但**不给物品也不加好感**——纯演出 [源码] `MaidBegTask`（BEG_DISTANCE=6）。

---

## 3. AI 任务体系全貌

### 3.1 架构

- 每个任务 = 实现 `api/task/IMaidTask` 的类，UID 形如 `touhou_little_maid:xxx`，存同步字段 DATA_TASK；`createBrainTasks()` 返回 (优先级, Behavior) 列表注入 Brain 的 WORK 活动 [源码] `entity/task/TaskManager`、`entity/ai/brain/MaidBrain#registerWorkGoals`。
- Brain 按原版 Activity 分组：CORE / IDLE / WORK / REST / PANIC + 三个骑乘活动 RIDE_IDLE/RIDE_WORK/RIDE_REST。**任务行为只在 WORK 活动挂载**——日程到了非工作时段，任务行为天然停摆（GUI 换任务不受时段限制）[源码] `MaidBrain`。
- WORK 组常驻附加行为：乞食（优先级 5/6）、工作餐、偷吃方块（优先级 8）、随机看/随机走 5 格/发呆 30–60 tick [源码] `MaidBrain#getLookAndRandomWalk`。
- 目标搜索骨架：方块类共用 `MaidMoveToBlockTask`（检查率 ≤120+rand(120) tick，BFS 同心方形扫描，纵向 ±1~2，候选须可达 + 非 home 模式须在主人 8 格内）[源码] `MaidMoveToBlockTask#searchForDestination`。

### 3.2 任务全列表（注册顺序 = GUI 顺序，共 22 内置 + 1 兼容）

[源码] `TaskManager#init` + 各 Task 类；名称取 zh_cn.json [jar资源]：

| # | UID | 名称 | 工作内容 / 关键数值 |
|---|---|---|---|
| 1 | idle | 空闲 | 无工作；彩蛋：雪天可与主人/同主女仆互掷雪球（间隔 50+rand(50) tick，每轮 3.125% 停止）|
| 2 | attack | 攻击 | 近战，见 §4 |
| 3 | ranged_attack | 弓兵 | 见 §4 |
| 4 | crossbow_attack | 弩兵 | 见 §4 |
| 5 | danmaku_attack | 弹幕攻击 | 持御币发射弹幕，见 §4 |
| 6 | trident_attack | 三叉戟攻击 | 见 §4 |
| 7 | gun_attack | 枪械 | 仅 TACZ/SWarfare 在场时注册；索敌 64/48/32 三档 |
| 8 | farm | 农场 | 收 `CropBlock` 成熟作物+补种；主手持锄=破坏模式，否则掉落直入女仆背包、方块年龄归 0；第三方作物经 `ISpecialCropHandler`（内置下界疣）|
| 9 | sugar_cane | 甘蔗 | 收 2+ 格甘蔗、种在含水基座 |
| 10 | melon | 瓜类 | 只收不种；南瓜/西瓜+相邻瓜藤；精准采集收整瓜 |
| 11 | cocoa | 可可 | 收 age≥2 可可、种丛林木侧面（一次最多 4 个）|
| 12 | honey | 采蜜 | POI 蜂巢 honey_level≥5：有剪刀→3 蜜脾，否则 1 玻璃瓶→1 蜂蜜瓶；重置 honey_level=0 |
| 13 | grass | 花草 | 只清理高草/花/双层植物 |
| 14 | snow | 清雪 | 清雪片层；有铲掉落、无铲不掉落 |
| 15 | feed | 喂食（喂主人） | 按优先级喂主人：牛奶=有可治愈负面效果；蜂蜜=中毒；金苹果=缺血半时 HIGH；普通食物=主人饿时 HIGH/满时 LOWEST（濒死才喂）|
| 16 | shears | 剪刀 | 剪 `IForgeShearable` 实体（羊/雪傀儡/第三方），掉落物撒地上 |
| 17 | milk | 牛奶 | 挤最近成年 `Cow`（蘑菇牛不算），背包空桶→牛奶桶 |
| 18 | torch | 火把 | 放置点上方亮度 <9 处放火把（纵向 ±2）|
| 19 | feed_animal | 繁殖动物 | 喂可繁殖动物；可见动物数 ≥50（可配）停止并气泡提示，**≥48 时改为攻击动物控数量** |
| 20 | fishing | 钓鱼 | 先找椅子/船坐下（找不到气泡提示），坐定后在 6 格内找水面抛竿（自定义钓鱼钩，钓竿附魔生效）|
| 21 | extinguishing | 灭火 | 持灭火器：灭着火的主人/自己/宠物，喷灭火剂实体（5×3×5 清火，对火焰免疫怪 2.0 伤/5tick）|
| 22 | board_games | 游戏 | 找未占用棋盘（五子棋/中象/国象）坐下与主人对弈 |
| 23 | kill_death_note 等 | — | 未见其余独立任务 [源码] 注册表到此为止 |

每任务自带：`getAmbientSound`（工作语音，§9）、`isEnable()/getEnableConditionDesc()`（GUI 灰置+条件文案，如弓兵"背包需要有箭"）、攻击类有专属配置 GUI（§4.1）。

### 3.3 任务切换方式

- **GUI**（唯一常规途径）：女仆背包 GUI 左侧任务抽屉，线性翻页（每页 12 个），点击→客户端预测→服务端校验归属/可启用后 `setTask`（重建 Brain）[源码] `network/message/MaidTaskMessage`。
- **AI 对话切换**（1.5.3 LLM agent，§11.4）：`switch_work_task` 工具，**切换时会自动从背包装备所需物品**（`onFunctionCallSwitch`）——注意 GUI 路径不会自动装备 [源码] `SwitchWorkTaskTool#onCall` vs `MaidTaskMessage#handle`。
- **没有**右键物品切换、**没有**命令切换任务 [源码] command 包检索。

### 3.4 日常行为

| 行为 | 机制 | 数值 |
|---|---|---|
| 跟随主人 | CORE 优先级 3，速度 0.5、停距 2；距主人 > restrictRadius−2 起步、> restrictRadius+2 直接传送（主人周围 ±3/±1/±3 随机可站立足点，试 10 次，避开水下）；home 模式下禁用跟随 [源码] `MaidFollowOwnerTask`、`EntityMaid#teleportToOwner` | 非家模式半径 8（可配）|
| 坐下 | 主人潜行右键切换坐姿；`sit` 动画 | — |
| 睡觉 | 只睡**女仆床**（专用方块 POI，不睡原版床），REST 时段在半径内找最近未占用床头，走到 2 格入睡；入睡回满血+好感+2 [源码] `MaidBedTask` | REST 时段见 §1.4 |
| 乞食 | 主人手持诱惑物（默认蛋糕）6 格内 | 贴身 2 格凝视 |
| 娱乐 Joy | IDLE 找 JOY_BLOCK POI（书架/电脑/电子琴/棋桌）坐下消遣 [源码] `MaidJoyTask` | — |
| 拾物 | CORE 优先级 10 + `MaidPickupEntitiesSensor`（30 tick）；PickType 配置 拾物品/拾经验/全拾（每女仆可配）[源码] `entity/passive/PickType` | 半径 restrict+1 |
| 游泳/水下 | 两栖导航 `MaidUnderWaterPathNavigation` + 换气行为 `MaidBreathAirTask`（CORE 优先级 0）：缺氧时先喝水下呼吸药/食物，否则 BFS 找 16 格内换气点游上去；换气期间跟随/闲逛让位 [源码] `MaidBreathAirTask` | — |
| 骑乘 | 主人驾驶载具时 3 格内上同乘（船/矿车/马通用，SW 载具 9 格）；主人下车冷却期跟着下车 [源码] `MaidFollowOwnerVehicleTask` | — |

### 3.5 多女协作/防抢活：**基本没有**

- 未找到任务分配、区块占用、全局目标锁 [推断：全库检索无相关结构]。多女仆可同时收同一作物、抢同一掉落物。
- 仅有的三类局部去重：娱乐方块/棋盘/野餐垫座位按"坐骑实体 UUID 存在"判占用 [源码] `MaidJoyTask#isOccupied` 等；女仆床只取最近不查占用；攻击目标无去重（各自 ATTACK_TARGET 记忆）。

---

## 4. 战斗细节

### 4.1 索敌与排斥

- 共用合法性链 [源码] `api/task/IAttackTask#canAttack`：排除盔甲架/TLM 物品实体/**玩家（永不攻击主人）**/有主宠物/自定义名 `MaidNoAttack` 开头实体/config 忽略名单（默认 `mekanism:robit`）。
- 三分类 [源码] `entity/misc/DefaultMonsterType`：实现原版 `Enemy` → HOSTILE（必打）；TamableAnimal/村民类 NPC → FRIENDLY（永不打）；其余 → NEUTRAL（**仅当**是主人最近被打的仇敌、主人正在打的目标、或女仆自己被打的仇敌时才打）。
- **AttackListData**（每女仆数据，不是 datapack）：GUI 里手动输入实体 ID 并在 友好/中立/敌对 间切换，存 NBT `MaidTaskDataMaps`，格式 `{attack_groups: {"<entity_id>": "friendly|neutral|hostile"}}` [源码] `entity/data/inner/AttackListData`、`AttackTaskConfigGui`。
- 索敌半径：近战=工作半径（12/8）；远程=各自射程（弓 48/弩 64/弹幕 64/三叉戟 48）。远程任务**优先反击**（`getLastHurtByMob` 在视线内直接选）[源码] `IRangedAttackTask#findFirstValidAttackTarget`。
- 恐慌：非攻击任务 `enablePanic=true`——被打或 8 格内出现苦力怕（`MaidHostilesSensor` 唯一感知对象）时逃离，攻击任务不恐慌 [源码] `MaidPanicTask`。

### 4.2 武器与伤害

| 武器 | 弹药/消耗 | 蓄力/冷却 | 伤害 |
|---|---|---|---|
| 近战剑 | 无消耗 | 攻速属性 | 攻击力（档位 2/3/4/6）+ 武器加成；好感加成触及距离与横扫（§2.1）|
| 弓 | **背包真实箭**（无限附魔免消耗），箭可捡回 | 蓄力 `20−力量×5` tick，冷却 2 tick 属性 | 默认箭伤 ×（攻击力/2），箭速 clamp(距离/10, 1.6, 3.2)，散布随距离降低 |
| 弩 | 背包箭或副手烟花 | 装填速度属性 | 同上倍率 |
| 弹幕 | 御币扣 1 耐久，无弹药 | 射速属性 2.0 | 攻击力 ×(距离系数 + 1.0/1.2/1.5)；单敌单发→敌多 8 弹 60° 扇形→更多 32 弹 120°；多重射击附魔 3 连扇；`impeding` 附魔附缓慢；永不命中玩家/同主宠物/忽略名单 |
| 三叉戟 | 扣 1 耐久，投出实体不可拾回（去激流） | 蓄力 ≥30 tick，冷却属性 20 | 原版三叉戟伤害 |
| 雪球 | idle 彩蛋 | — | 原版雪球 |

[源码] `TaskBowAttack/#TaskDanmakuAttack/#TaskTridentAttack`、`entity/projectile/EntityDanmaku`、`DanmakuShoot`。

### 4.3 护主/防御/自疗/脱战

- 护主完全靠 NEUTRAL 仇恨链（主人被打→`getLastHurtByMob` 立即合法目标）+ 远程反击优先，无独立仇恨表 [推断：无 setTarget 事件驱动代码]。
- 被投射物打时自动举盾 100 tick（若副手有盾）[源码] `EntityMaid#m_6469_`、属性 `maid_passive_use_shield_tick`。
- 举盾进攻：attack 任务挂 `MaidUseShieldTask`——目标 <8 格且盾不在冷却时持续举盾 [源码] `MaidUseShieldTask`。
- 自疗：§1.5 自愈餐（任意非黑名单食物），另有 0.25%/tick 概率回复。
- 脱战：目标死亡/武器消失/超射程（远程）/超工作半径（近战）；丢视线 −60 tick 放弃拉弓 [源码] `StopAttackingIfTargetInvalid`、`MaidShootTargetTask`。
- 女仆死亡处理见 §12。

---

## 5. 物品全表

共注册 **73 个物品**（含 7 背包、3 魂符、9 进度图标）[源码] `init/InitItems`。已核实确认**不存在**：伞、玩家用弹幕物品、小号以外乐器、独立女仆手册物品（手册=Patchouli guide_book+book NBT）。

### 5.1 凭证类（生命周期链，防滥用核心）

| 物品 | 关键机制 |
|---|---|
| 魂符（三态 INIT/EMPTY/HAS_MAID，共用名"魂符"） | 已在 api-facts §25 完整核对，本轮复核一致：三条路径均为**原地替换手中物品**（不是消耗）；INIT 校验 `InitMaidOwner` 绑定+额度 `canAdd`；EMPTY 收纳=全量 NBT 搬运（严格 1:1）；HAS_MAID 校验内嵌 Owner；三态共用 `stacksTo(1)`，HAS_MAID `canFitInsideContainerItems=false`；20 tick 冷却 [源码] `item/ItemSmartSlab`、`event/maid/SlabClickEvent` |
| 仆人铃 ItemServantBell | 对自己女仆右击绑定 UUID+备注（GUI 选单）；**长按 100 tick** 使用→按 UUID 找到女仆即传送到玩家身边（给 200 tick 缓降，关家模式、下车）；**找不到时**改写台账坐标进铃 NBT 显示"她在 x,y,z"指引寻找；冷却 20 tick；不消耗、永不生成新女仆 [源码] `ItemServantBell#releaseUsing/#showMaidInfo` |
| 小号 ItemTrumpet | 长按召回**同维度全部**自己的女仆，报告"卸载区块还有 N 只"；冷却 200 tick [源码] `ItemTrumpet` |
| 胶片 ItemFilm | 死亡记录介质（§12）；`fireResistant=true`（岩浆不烧）；堆叠 1 |
| 照片 ItemPhoto | 相机对女仆使用生成**完整快照**（含背包全部物品）；右键顶面还原女仆；`canFitInsideContainerItems=false` [源码] `ItemPhoto` |
| 相机 ItemCamera | 8 格射线找自己女仆→discard 并生成照片；耐久 50、冷却 20 tick [源码] `ItemCamera` |
| 主人转让工具 owner_conversion_tool | 对女仆右击=**无视驯服状态直接改主人**（NTR）；也可开别人的墓碑；消耗 1 且占用新主人的女仆额度 [源码] `EntityMaid#tameMaid` isNtr 分支 |

### 5.2 功能类

| 物品 | 机制 |
|---|---|
| 背包 ×7 | 见 §7.1 |
| 御币（博丽/风祝） | 祭坛构建器（§6.2）+ 弹幕任务武器；耐久 1200、攻击+4 攻速−2；手持显示 P 点 HUD [源码] `ItemHakureiGohei` |
| 女仆床 | 女仆睡觉 POI（不睡原版床）；NBT 床颜色 |
| 灭火器 | 玩家不可用（use 只提示），女仆灭火任务专用；耐久 128 [源码] `ItemExtinguisher` |
| 河童罗盘 | 记录 工作/休息/睡觉 三锚点（同维度间距 ≤64），右击女仆写入 SchedulePos [源码] `ItemKappaCompass` |
| 红狐卷轴/白狐卷轴 | 只读查台账：红=名下女仆坐标，白=墓碑坐标 [源码] `ItemFoxScroll` |
| 隙间 wireless_io | 绑定容器→女仆装备后每 100 tick 双向搬运（9 格过滤黑白名单）；饰品化 [源码] `ItemWirelessIO`、`bauble/WirelessIOBauble` |
| 神社庭灯 maid_beacon | 吸收 6 格内 P 点实体（存上限 100）；给 8 格内女仆信标效果（速度/防火/力量/抗性/回血），0.9 P/小时 [源码] `TileEntityMaidBeacon` |
| 模型切换器 | 方块，绑定一只女仆后用于改其模型 [源码] `ItemModelSwitcher` |
| 扫帚 | 双座飞行实体（玩家+同主女仆），WASD+跳升/潜行降 [源码] `EntityBroom`、`PlayerBroomControl` |
| 麻石狐像 scarecrow | 48 格内阻止妖精生成 [源码] `MiscConfig.SCARECROW_RANGE` |
| 替身地藏 | 对自己女仆使用→女仆永久无敌，消耗 1 [源码] `ItemSubstituteJizo` |
| 凿子/手办/雕像 | 副手照片+主手凿子把空间复制成雕像方块（观感用）[源码] `ItemChisel` |
| P 点物品 power_point | 投掷散成 30~88 个 P 点实体 [源码] `EntityThrowPowerPoint` |
| 实体占位符/实体ID复制器/棋残局 ×3 | 编辑器类工具（JEI/进度）|
| 好感度工具 ×3 | §2.2 |

### 5.3 饰品（Bauble）系统

- 女仆 30 格饰品栏（好感档位解锁 10/20/30），与 Curios 可联动；接口 `IMaidBauble`：onTick / onInjured（受伤拦截）/ onDeath（死亡拦截）/ onFavorabilityLevelChange / onBackpackChange [源码] `api/bauble/IMaidBauble`、`item/bauble/BaubleManager`。
- 内置：**原版不死图腾可作女仆饰品**（死亡时消耗+给 buff）、绀珠之药（6 耐久，死时回满血=女仆不死图腾）、爆炸/火焰/弹射物/药水/摔落/溺水保护（各 32~128 耐久）、闪避布（概率闪避）、物品磁铁（扩大拾取）、禁言饰品、隙间。耐久饰品不可修补 [源码] `item/bauble/*`。

---

## 6. 召唤与获取（P 点 / 祭坛）

### 6.1 P 点（Power Point）

- 存储：玩家 Capability，float，**上限 5.0、初始 0**，死亡（非 keepInventory）扣 1.0 [源码] `capability/PowerCapability`、`MiscConfig.PLAYER_DEATH_LOSS_POWER_POINT`。
- 获得：①杀女仆妖精 EntityFairy 掉 P 点实体（成年 0.16×100=16 原始值、婴儿×2；`power += value/100`）；②战利品箱 1/3 概率 1–2 个；③钓鱼 10% 概率；④命令 [源码] `EntityFairy#getPowerPoint`、`EntityPowerPoint#playerTouch`。
- 溢出：超过 5.0 的部分按 1/4 折成经验 [源码] `EntityPowerPoint#transPowerValueToXpValue`。
- **女仆也能捡 P 点**（用于修背包里的经验修补装备）[源码] `EntityMaid#aiStep`。
- 消耗：祭坛配方 0.1–0.5；庭灯 0.9/小时。

### 6.2 祭坛（多方块）与召唤

- 结构 8×6×8：核心=御币右击的目标；6 根**原木柱**（tag `#minecraft:logs`）+ 红色羊毛梁/顶 + 栅栏 tag `#touhou_little_maid:altar_torii`（默认深色橡木/绯红木栅栏）；模板按 4 朝向逐块校验，成功全部替换为 altar 方块（原方块记 TE 便于拆毁恢复）[源码] `item/ItemHakureiGohei#useOn`、`block/multiblock/MultiBlockAltar`、[jar资源] `altar_south.nbt` 实测解析。
- 交互：**可放物品的祭位=6 个原木柱顶**；手持物右击放入→立即尝试匹配配方（`touhou_little_maid:altar_crafting` RecipeType，顺序无关）→P 点足够则扣 P+清空全部祭位+在中心上方生成产物；P 不足提示"没有足够多的P点"且**材料保留**可重试；空手 Shift 取回 [源码] `block/BlockAltar#use/#altarCraft`。
- 关键配方 [jar资源] `data/recipes/altar/*.json`（43 个）：
  - `reborn_maid`：胶片+青金石+金锭+红石+铁锭+煤炭，**0.5 P** → 复活女仆（§12.4）；
  - `spawn_box`：钻石系同款材料 0.5 P → 包装盒实体（内含**未驯服**女仆，打碎后可蛋糕驯服）；
  - `spawn_lightning_bolt`：火药×3+烈焰粉×3，0.2 P → 引雷（女仆被劈+20 血上限）；
  - 背包 0.1–0.3、御币 0.15、其余物品 0.1–0.2。
- **召唤模型决定**：新女仆 `finalizeSpawn` 从服务端已加载模型集合**均匀随机**选一个（默认回退 `hakurei_reimu`）；**没有玩家选择界面**（PickType 是拾取模式不是性别）；事后可 GUI 换模型（`MaidChangeModel=true` 默认允许）[源码] `EntityMaid#finalizeSpawn`。
- **初始好感 0**；驯服=蛋糕右击（消耗 1+占用额度）；改名=GUI 内持名牌改名（≤32 字符，消耗名牌）；转让=§5.1 工具 [源码] `SendNameTagMessage`。

### 6.3 女仆妖精 EntityFairy（P 点生态）

- 怪物类飞行生物：HP 35、攻 1.5、发射弹幕（90% 单发/10% 3 连扇形）[源码] `EntityFairy`。
- 生成：仅当生物群系怪物表含僵尸时追加（权重 70，min2 max4）；维度黑名单（下界/末地/暮色）；48 格内有狐像则不生成 [源码] `MobSpawnInfoRegistry`、`MiscConfig`。
- 彩蛋：1/20 概率命名 "rick" 且婴儿、1/10 婴儿 [源码] `EntityFairy#finalizeSpawn`。

---

## 7. 背包与装备（含手持渲染原理）

### 7.1 背包类型（8 种）

[源码] `entity/backpack/BackpackManager#init`（已复核末影箱=6）：

| 类型 | 可访问格数（主背包物理恒 36 格） | 附加功能 |
|---|---|---|
| empty（默认） | 6 | slot5=背部展示物品槽 |
| small / middle / big | 12 / 24 / 36 | — |
| crafting_table | 18 | GUI 内置 3×3 合成 |
| ender_chest | 6 | GUI 显示女仆 6 格+玩家末影箱 |
| furnace | 18 | 内置熔炉 3 格（BurnTime/CookTime 持久化，女仆拿着时持续烧炼 [推断：FurnaceBackpackData tick 驱动]）|
| tank | 18 | 流体储罐，卸下时物品化 |

- **换装方式**：手持背包物品右击女仆=直接换装（旧背包回收给玩家、超容格子掉落、消耗 1、20 tick 冷却）；剪刀右击=卸下 [源码] `event/maid/HandleBackpackEvent`。不是升级链。
- 主背包物理 36 格 + 双手 2 + 盔甲 4 + 饰品 30 + 隐藏 1 + 任务 9 [源码] `EntityMaid` 字段区。手部入包校验：物品非 config 黑名单且可 `canFitInsideContainerItems`（防潜影盒嵌套）[源码] `MaidHandsInvWrapper`。

### 7.2 手持物品可见的实现原理（对 CatMaid 最重要）

**双渲染管线并存**（1.5.3）：旧 bedrock 管线（`client/renderer/entity/layer/LayerMaidHeldItem` + `simplebedrockmodel`）与新 gecko 管线（`client/renderer/entity/geckolayer/GeckoLayerMaidHeld` + 内置魔改 geckolib3）。

**新管线（推荐对齐的约定）** [源码] `geckolib3/geo/animated/AnimatedGeoModel` 构造器 + `GeckoLayerMaidHeld#render`：
1. **骨骼命名约定**：模型 json 中提供名为 `RightHandLocator` / `LeftHandLocator` 的骨骼（locator）；可选扩展 `extraRightHandBones/extraLeftHandBones` 多挂点、`LeftWaistLocator/RightWaistLocator`（腰挂）、`BackpackLocator`（背饰/背物品/旗指物）、`PistolLocator/RifleLocator`（枪挂）、`Head/Hat/LeftArm/RightArm`。缺哪个骨骼对应渲染层自动跳过——**约定即协议，无注册表**。
2. 渲染时把 PoseStack **定位到该骨骼当前动画位姿**（`RenderUtils.prepMatrixForLocator`），再以 `ItemDisplayContext.THIRD_PERSON_RIGHT_HAND` 渲染 ItemStack——物品的原版 third-person 变换全部生效。
3. **失败回退**：locator 找不到时退到固定偏移 `(0, −0.0625, −0.1)` + 绕 X −90°，走原版 `ItemInHandRenderer`。
4. 背部展示物品：maidInv slot5 的物品在 `BackpackLocator` 以 FIXED context 渲染；背包本体（小/中/大箱形模型）也在 BackpackLocator 渲染独立 bedrock 模型（`bedrock/entity/backpack/*.json`）；旗帜有专门旗模型。
5. 头部：HEAD 槽放头颅方块→原版 SkullBlockRenderer 渲染在 headBones 上。

**旧管线**：模型部件名 `armRight/armLeft` 决定手持渲染，可选 `armRightPositioningBone` 定位骨骼，否则固定偏移 `(±1/16, 0.125, −0.525)` [源码] `client/model/bedrock/BedrockModel`。

**盔甲可见**：不渲染原版盔甲层。旧管线靠模型自带盔甲骨骼（`helmet/chestPlate(+Left/Middle/Right)/leggings(+…)/bootsLeft/bootsRight`）+ 内置 JS 按"穿没穿"显隐（`animation/maid/default/armor/default.js`）；新 gecko 管线给每件盔甲槽注册独立 AnimationController，动画命名约定 `<slot>$<item_id>` / `<slot>#<item_tag>` / `<slot>:default`，模型没做对应动画就不显示 [源码] `GeckoMaidEntity#registerControllers`、`AnimationManager#predicateArmor`。[推断] 未定义盔甲骨骼的模型穿甲不可见。

---

## 8. 模型与动画

### 8.1 通用动画清单（maid.animation.json，97 个）

[jar资源] `assets/touhou_little_maid/animation/maid.animation.json`；触发逻辑 [源码] `client/animation/gecko/AnimationRegister`、`AnimationManager`。

**主状态机（main 控制器，优先级 0→4 取第一个满足者）**：

| 优先级 | 动画 | 触发 |
|---|---|---|
| 0 | death / sleep / swim / ladder_up / ladder_stillness / ladder_down | 死亡 / 睡姿 / 在水 / 攀爬三分支 |
| 1 | gomoku / bookshelf / computer / keyboard / picnic / boat / chair / sit | 坐在对应实体/方块上 |
| 2 | swim_stand / attacked / jump | 水中立 / hurtTime>0 / 离地 |
| 3 | run / walk | 疾跑 / 移动（limbSwing>0.05）|
| 4 | idle | 兜底 |

**misc 控制器（事件类）**：`game_win / game_lost`（棋局）、`pick_up_snowball`、`beg`（乞食）。
**手部状态机（hold_mainhand / hold_offhand / swing / use 四控制器）**：按 `物品ID $ > 物品标签 # > 内部类别 :` 三级匹配——内置 sword/axe/pickaxe/shovel/hoe/shield/bow/crossbow/spear/gohei/eat/drink/spyglass/fishing/throwable_potion/charged_crossbow 的 hold/use/swing 全套（如 `hold_mainhand:sword`、`use_mainhand:eat`、`swing$minecraft:snowball`）+ 空手 `empty`。**这就是"女仆拿剑有拿剑姿势、吃东西有吃东西动作"的机制**。
**盔甲控制器**：每槽一个，`<slot>$<item_id>` 命名约定（§7.2）。
**并行槽 parallel0–7 + pre_parallel0–7**（16 个独立控制器）：眨眼、眼球跟随（Molang `ysm.head_yaw`）、物理骨骼等叠加层。
**条件动画**：`vehicle$<entity_id>`、`chair$<chair_model_id>` 前缀按乘客类型切换。
**本版本 gecko 侧没有 farm/fishing 等"任务专属"独立动画**——任务差异通过手持动画（如 `hold_mainhand:fishing`）与旧 JS 体系实现 [源码] 检索确认。

### 8.2 状态机与工作系统耦合

- 每帧各控制器 predicate 查询实体状态（isBegging/hurtTime/在坐什么/手持什么）→ 查表得动画名 → AnimationController 切换（main 2t 过渡）。**动画与任务系统零耦合**：任务不主动播动画，动画只读实体状态（含 DATA_TASK 间接影响 ambient 音而非动画）[源码] `AnimationManager#predicate*`。
- 动画文件由模型包声明（`maid_model.json` 的 `animation` 列表按序合并，缺的自动补默认表）[源码] `GeckoModelLoader#registerMaidAnimations`。
- Molang：自研引擎 + geckolib3 解析；绑定变量 `is_begging/is_sitting/has_backpack`、YSM 系列、控制器变量；自定义查询函数 `BonePosition/BoneRotation/BoneScale/EffectLevel/EquippedEnchantmentLevel/RelativeBlockName` + 二阶动力学物理函数（头发裙摆物理，`PhysicsManager`）[源码] `molang/*`、`client/animation/gecko/molang/*`。

---

## 9. 声音系统

### 9.1 双层结构

1. **原版 SoundEvent 占位层**：43 个事件全部指向 `maid/empty.ogg` 占位音——真实声音不走原版 sound 系统 [源码] `init/InitSounds` + [jar资源] `sounds.json`。
2. **语音包层（客户端播放）** [源码] `client/sound/CustomSoundLoader`：
   - 包 = `assets/<ns>/maid_sound.json`（pack_name/author/version/icon/url 等）+ `assets/<ns>/sounds/maid/{mode,ai,environment,other}/` 下 ogg；
   - 文件名约定 `<事件短名><序号>.ogg`（如 `death1.ogg`…`death25.ogg`），播放时随机取一个；
   - **复用规则**：缺文件自动继承（range_attack/danmaku_attack←attack；farm/feed/shears/milk/torch/feed_animal/extinguishing/snow/break/furnace/brewing←idle；hurt_fire←hurt）；
   - lang 文件（json/lang 双格式）进 TLM 自有翻译池，用于包名/描述翻译。

### 9.2 事件→语音分类

[源码] `util/SoundUtil`、各 `Task#getAmbientSound`、`EntityMaid#m_7975_/m_5592_`：

| 目录 | 短名 | 触发 |
|---|---|---|
| mode/ | idle, attack, range_attack, danmaku_attack, farm, feed, shears, milk, torch, feed_animal, extinguishing, snow, break, furnace, brewing | 各任务环境音（按 Mob 环境音计时，概率 0.3–0.5）；idle 环境音内部再按时间/天气细分 morning/night/rain/snow/cold/hot |
| ai/ | find_target, hurt, hurt_fire, hurt_player, tamed, item_get（每拾 5 次）, death, game_win, game_lost | 战斗与生命事件 |
| environment/ | （由 idle 细分触发） | 环境 |
| other/ | credit | 制作名单 |

- 服务端→客户端：`PlayMaidSoundMessage`（音事件+语音包 ID+实体 ID，16 格范围），客户端 `MaidSoundInstance` 跟随实体播放 [源码] `network/message/PlayMaidSoundMessage`。
- 每只女仆初始语音包：**75% littlemaid_peco / 25% touhou_little_maid** 随机 [源码] `DefaultMaidSoundPack#getInitSoundPackId`；可 GUI 换。
- 频率：全局百分比 100% × 每女仆 SoundFreq 1.0 [源码] `MaidSoundFreqEvent`。
- **与台词文字无直接联动**：聊天气泡是独立系统（§11.4/表情包）。

---

## 10. 世界交互细节

[源码] `entity/ai/navigation/*`、`entity/ai/path/*`：

| 能力 | 结论 | 依据 |
|---|---|---|
| 开门/关门 | **会开且会自动关**：`canPassDoors(true)+canOpenDoors(true)`；`MaidInteractWithDoor`（core 优先级 2）开前后门并记住位置，走过后关门（8 格、3t 冷却、有其他生物要过则不关）；每女仆可关掉（OpenDoor/OpenFenceGate 默认 true，关掉后门视为铁门不可走） | `MaidNodeEvaluator` + `MaidInteractWithDoor` |
| 走楼梯/爬梯 | ActiveClimbing（默认 true）时梯子/攀爬块视为可行走，额外生成上下攀爬节点（代价 0） | `MaidNodeEvaluator#createClimbNode` |
| 避险 | `MAID_AVOID_BLOCK` 方块标签→绕行（岩浆/危险方块通用做法）；悬空格二次判定脚下火/蜂蜜/细雪改判危险 | `MaidNodeEvaluator` L20–168 |
| 水 | 两栖导航：水下有专用 NodeEvaluator（水面换气节点缓存剪枝）；换气行为 §3.4 | `MaidUnderWaterPathNavigation` |
| 睡觉 | 只睡女仆床（§3.4） | `MaidBedTask` |
| 工作台/熔炉 | 女仆**不主动**用；功能背包给玩家提供便携 GUI（§7.1）；熔炉背包女仆携带时持续烧炼 [推断] | `FurnaceBackpackData` |
| 箱子 | 女仆本体不自动开箱；远程存取靠隙间饰品（ChestManager 注册 Vanilla/Barrel/零食柜+IronChests+第三方） | `inventory/chest/ChestManager` |
| 雨 | 无淋雨机制（不受雨伤、无伞）；雨只触发 rain 语音与盔甲温度 JS | [推断：全库无 umbrella/rain damage] |
| 载具 | 船/矿车/马通用：主人驾驶时 3 格内上同乘；骑乘中有独立 RIDE_* 活动组（骑乘钓鱼/讨食） | `MaidFollowOwnerVehicleTask`、`MaidRideFindWaterTask` |
| 传送 | 跟随超距传送（避开水下落点）、日程越界传送回锚点、铃传送 | §1.4/§3.4/§5.1 |

---

## 11. 配置与开放结构

### 11.1 配置全项（影响行为的）

[源码] `config/GeneralConfig`、`ServerConfig`、`subconfig/*`：

**MaidConfig**：GlobalMaidSoundFrequency=100、GlobalMaidShowChatBubble=true、MaidTamedItem/MaidTemptationItem=minecraft:cake、EnableMaidCurios=true、MaidWorkRange=12 / IdleRange=6 / SleepRange=6 / NonHomeRange=8、BowRange=48 / CrossbowRange=64 / DanmakuRange=64 / TridentRange=48、FeedAnimalMaxNumber=50、MaidChangeModel=true、MaidGomokuOwnerLimit=true、**OwnerMaxMaidNum=Integer.MAX_VALUE（默认不限）**、MaidBackpackBlackList=[]、MaidAttackIgnore=[mekanism:robit]、MaidRangedAttackIgnore=[]、三餐黑名单 ×3、MaidEatenReturnContainerList=[]、EnableEmoji=true（1200t 检查、图/颜文字权重 10/10）、ReplaceAllayPercent=0.2（前哨站悦灵 20% 概率替换成女仆）、枪械距离 64/48/32。

**MiscConfig**：MaidFairyPowerPoint=0.16、MaidFairySpawnProbability=70、妖精维度黑名单=[下界,末地,twilight]、PlayerDeathLossPowerPoint=1.0、GiveSoulSpell（首发魂符）=true、GivePatchouliBook=true、ShrineLampEffectCost=0.9/h、ShrineLampMaxStorage=100、ShrineLampMaxRange=6、ScarecrowRange=48、InvulnerableParticleEffect=true。

**AIConfig**：LLMEnabled=true、AutoGenSettingEnabled=true、MaidHistoryCompressTokenLimit=48K token、TTSEnabled=true（FishAudio/GPT-SoVITS/MiniMax/SiliconFlow/Player2/系统本地）、STTEnabled=true（Player2/阿里云/腾讯/SiliconFlow）、MaidCanChatDistance=12。

**ServerConfig**：ClientPackDownloadUrls=[]、MaidBackupIntervalSeconds=180（最多 3 份女仆数据自动备份 [源码] `world/backups/MaidBackupsManager`）、MaidAITimeDebug=false。

**VanillaConfig/RenderConfig/ChairConfig**：材质替换（史莱姆→柚柚、经验球/图腾贴图）、9 个 tooltip 开关、椅子可破坏=true。

### 11.2 开放格式：第三方自定义包（tlm_custom_pack）

[源码] `client/resource/CustomPackLoader`（客户端）+ `entity/info/ServerCustomPackLoader`（服务端）；根目录 `.minecraft/tlm_custom_pack/` 或服务端工作目录，支持文件夹/zip。

**注册一个新女仆角色的最小文件集**：

```
<pack>/assets/<namespace>/
  maid_model.json          # 包信息 + model_list[]: model_id(必填)/model/texture/animation[]/
                           #   use_sound_pack_id/render_entity_scale/show_backpack/show_custom_head/is_gecko
  models/entity/<id>.json  # bedrock 或 gecko 几何（骨骼名遵守 §7.2/§8 约定）
  textures/entity/<id>.png
  animation/*.animation.json  # 可选，不填用默认 97 动画表
  lang/*.json              # {model.<ns>.<id>.name} 等键
  settings/<角色>.yml      # 可选：LLM 人格设定（meta.model_id + setting 提示词）
```

语音包：另加 `maid_sound.json` + `sounds/maid/{ai,environment,mode,other}/*.ogg`。椅子包：`chair_model.json`。服务端只读两个 model json + settings yml 并算 CRC32（供客户端比对下载）——**模型/语音/语言全部是资源包级开放格式，不需要写代码**。

### 11.3 API 扩展点（代码级）

[源码] `api/ILittleMaid`（`TouhouLittleMaid.EXTENSIONS` 收集，官方示例仓 TLMAdditionExample）：`addMaidTask`（含 IAttackTask/IFarmTask/IFeedTask 等子接口自带行为）、`addMaidBackpack`、`bindMaidBauble`、`addChestType`、`addMaidMeal`、`registerSpecialCropHandler`、`registerTaskData`（Codec 每女仆数据）、`addExtraMaidBrain`（Brain 行为/记忆/传感器注入）、`addHardcodeAnimation`（旧 JS）、KubeJS 注册任务。事件总线 20+（MaidHurt/MaidDeath/MaidPickup/MaidEquip/InteractMaid/…）。

### 11.4 LLM 智能体（1.5.3 新增）

[源码] `ai/*`：女仆可对话（文字 GUI 或按住 X 键语音，12 格内）：STT（4 家供应商）→ LLM（OpenAI 兼容，内置 DeepSeek 引导）→ 回复进气泡+TTS 语音（6 家，默认 en_us）。人格设定=模型包 `settings/<角色>.yml`（空则 LLM 自动生成）。**Function Calling 工具**：切任务/切跟随/切坐姿/切排班/查游戏上下文/查 MC Wiki/UseSkillTool——LLM 能实际操作女仆行为。历史超 48K token 自动摘要压缩；token 按玩家限额（默认无限）。

---

## 12. 死亡/复活/防滥用全链路

### 12.1 死亡拦截链

[源码] `EntityMaid#die`：饰品 `onDeath` 先行——绀珠之药（损 1 耐久回满血取消死亡）、不死图腾（消耗+1 血+buff）→ 都没拦住发 `MaidDeathEvent`（可取消）→ 正常死亡 + 给主人发死亡坐标消息。

### 12.2 死亡掉落：墓碑

[源码] `EntityMaid#m_5907_`（仅驯服女仆）：坐标高度钳制在世界底+5~世界顶 → 生成 `EntityTombstone`（0.8×1.2 实体，**不可破坏/不可推动**，256 槽）→ 收纳盔甲/手/背包/饰品/隐藏/任务槽全部 + 背包本体 + 胶片；消失诅咒按 gamerule 处理 → 台账登记。主人（或持转让工具者）右击取回（先校验背包容量，潜行可强制）；取完自动消失。

### 12.3 胶片（死亡证明）

[源码] `ItemFilm#maidToFilm`：女仆全量 NBT **剥离 19 项**（背包/饰品/经验/装备/血量/坐标/效果等）后存 `MaidInfo`——保留模型/名字/好感/主人/任务/日程配置。唯一获取途径=死亡；火岩浆不烧。

### 12.4 复活：两途径

1. **神龛 BlockShrine**：单槽只收胶片；空手使用→校验玩家**血量 > 一半**→`setHealth(0.25)`（=1/8 心，与游戏文案"降到 1"不一致，以代码为准）→ `filmToMaid` 在神龛上方重生女仆，胶片消耗 [源码] `block/BlockShrine#use`、`TileEntityShrine`。
2. **祭坛配方 reborn_maid**：胶片+5 材料+0.5 P [源码] §6.2。

复活保留：模型/名字/好感/主人/任务/日程；失去：背包（回 6 格空包）/物品/饰品/经验/装备。

### 12.5 防滥用：额度守恒

- **每玩家额度 MaidNumCapability**：int 计数，上限 `OwnerMaxMaidNum`（**默认 Integer.MAX_VALUE 不限**）；`canAdd` 只在两个口校验：驯服（蛋糕）与魂符 INIT 生成；创造豁免 [源码] `capability/MaidNumCapability`。
- **额度只增不减**：死亡/魂符收纳/拍照均不释放——因此"复活免费"（不占新名额），而烧掉胶片/照片会让名额永久占用，只能 `/tlm maid_num min` 命令清零 [源码] 全库检索 min 调用点。
- 凭证防复制：stacksTo(1)+唯一 UUID+归属锁+HAS_MAID/照片禁入容器+掉落物发光悬停无敌防虚空 [源码] `AbstractStoreMaidItem`。
- 与 pending-bug-verification §7 已有结论一致（本轮复核无冲突）。

---

## 附录 A：TLM 与 CatMaid 功能差距清单

按"对玩家体验的重要度"排序（★越多越值得进蓝图）：

| # | 差距 | TLM 行为 | CatMaid 现状 | 重要度 |
|---|---|---|---|---|
| 1 | **手持/背部/盔甲在模型上可见** | RightHandLocator 等骨骼约定 + 骨骼位姿渲染 ItemStack（§7.2/§8.1 手部状态机） | 已证明 BetterModel 无手持骨时外挂 ItemDisplay 观感不合格；模型无约定骨骼 | ★★★★★ |
| 2 | **好感度档位驱动属性** | 生命 20/30/40/80、攻击 2/3/4/6、触及+横扫、饰品栏解锁（§2.1），档位即数值 | 0–100 五档只影响容量/攻击 %/死亡减半，无血量成长 | ★★★★★ |
| 3 | **日程与家模式** | 三班制 + work/idle/sleep 三锚点半径管理（§1.4） | 无日程、无家模式，跟随为主 | ★★★★★ |
| 4 | **背包类型体系** | 8 种背包换装（合成/熔炉/末影箱/储罐）+ 背部展示物（§7.1） | 固定 27→36 格 | ★★★★ |
| 5 | **P 点经济 + 祭坛仪式** | 杀妖精攒 P、多方块祭坛召唤/复活/引雷（§6） | 召唤无成本，死亡转铃铛/钻石复活 | ★★★★ |
| 6 | **三餐 + 睡觉回血** | 工作餐/自愈餐/野餐/偷吃蛋糕（§1.5），喂食=塞手槽 | 右键喂食 60s 冷却（更简单但少"生活感"） | ★★★★ |
| 7 | **任务广度** | 22+ 任务（弹幕/弩/三叉戟/采蜜/甘蔗/瓜/可可/清雪/花草/灭火/喂主人/坐钓/棋类/ Gun 兼容）（§3.2） | 农/牧/战三模式 6 作物 | ★★★★ |
| 8 | **饰品系统** | 30 格 bauble + 图腾/保护/磁铁/隙间（§5.3） | 无 | ★★★ |
| 9 | **死亡三段链** | 墓碑还物 / 胶片存数据 / 神社扣血复活（§12） | 合并进铃铛（已参考 TLM 设计） | ★★★ |
| 10 | **开放资源包格式** | maid_model.json/maid_sound.json/chair_model.json + lang + settings（§11.2），玩家可自制角色 | 模型包是私有 CE 资产，无第三方包概念 | ★★★ |
| 11 | **寻路细节** | 开门自动关门/攀爬/两栖/换气/避险标签（§10） | Citizens 导航默认能力 | ★★★ |
| 12 | **聊天气泡/表情包** | 5 种气泡（文本/表情/图片/进度/等待）+ 随机颜文字（§9.2/§11.4） | dialogue.yml 聊天栏台词 | ★★★ |
| 13 | **语音包** | 开放格式 ogg 包、按事件随机+继承复用（§9） | 无语音（资源包可承载） | ★★ |
| 14 | **女仆妖精生态** | 妖精怪物=P 点来源+敌对弹幕怪+狐像克制（§6.3） | 无 | ★★ |
| 15 | **LLM 对话 agent** | STT→LLM→TTS+Function Calling 操控行为（§11.4） | 无 | ★★（可选） |
| 16 | **多女仆任务分配** | TLM 也没有（§3.5）——**双方都缺**，CatMaid 可做出差异化 | 无 | ★★（机会点） |
| 17 | **台账 MaidWorldData** | 区块卸载登记/进入清除，铃/卷轴定位（§1.1） | SQLite 自有，等价已有 | ★ |
| 18 | 杂项演出 | 雪球互掷、五子棋/象棋、相机/照片/手办/雕像/扫帚/庭灯/乞食动画 | 无 | ★ |

注：TLM 的心情系统、饥饿机制、右键喂食在 1.5.3 **均不存在**——CatMaid 现有的"心情/喂食"设计不是"抄少了"，而是本来就无原型可抄。

## 附录 B：服务端可行性备注（纯 Paper 插件、玩家零安装）

| 功能 | 一句话结论 |
|---|---|
| 手持/背部可见 | **有戏**：BetterModel 支持 bbmodel 骨骼定位，做 "RightHandLocator" 约定骨 + 插件在骨骼位生成/同步 ItemDisplay（BetterModel 有 bone 挂载 API 需二期验证）；手部状态机动画（拿剑/吃相）可手写动画帧驱动 |
| 好感档位属性 | 直接可行：改 Citizens 实体 attribute baseValue（Bukkit Attribute API） |
| 日程/家模式/锚点 | 直接可行：插件调度器 + Bukkit getNearbyBlocks 扫描 |
| 功能背包 GUI | 直接可行：Bukkit Inventory 自绘（熔炉烧炼逻辑手写 tick） |
| 饰品栏 | 直接可行：普通 GUI 槽 + PDC 标签判效果 |
| P 点经济 | 直接可行：PDC/scoreboard 计数 + 自定义物品 |
| 祭坛多方块 | 直接可行：结构扫描 + 6 祭位 inventory；CE 物品可做御币 |
| 弹幕/投掷物 | 直接可行：Paper 自定义实体/DisplayEntity 弹幕 |
| 三餐/睡觉回血 | 直接可行：行为树加节点；女仆床=CE 方块+POI 模拟 |
| 聊天气泡 | 直接可行：TextDisplay（已有名牌基建） |
| 语音 | **有条件可行**：原版资源包（CE 下发）可带自定义 sounds.json+ogg，用 playSound 播放事件；体积与包管理是约束 |
| 开门自动关门 | 可行：导航到门块时 setBlockState 开/关（Citizens PathAlone 事件钩子需验证） |
| 两栖/攀爬寻路 | **受限**：Citizens 导航器无两栖/攀爬 flag，水下跟游需自写简易寻路——建议降级为"游泳跟随传送" |
| 盔甲可见 | 可行但费：按 TLM 约定做盔甲骨骼组 + 显隐（BetterModel bone visible API 待验证），或降级为"只显示手持不显示盔甲" |
| Molang/物理骨骼（裙摆头发） | **死路**：BetterModel 不解析 Molang——若要裙摆物理需自写骨骼逐帧驱动（成本高，建议放弃） |
| YSM 玩家模型替换 | **死路**：纯客户端 mod 功能 |
| Curios 联动 | 无关（服务端背包即可） |
| LLM 对话 | 可行但重：插件可调 OpenAI 兼容 API + Function Calling；作为可选高级功能 |
| 多方语音包随机+继承 | 可行：资源包+播放端逻辑全在服务端 |
| 妖精生态 | 直接可行：CustomEntity 或 Citizens 怪物 |
| tlm_custom_pack 开放格式 | 有条件可行：可定义 CatMaid 自己的"女仆包"目录格式（bbmodel+贴图+配置 json），用 CE/BetterModel 动态加载——是差异化机会 |

---

## 附：本轮调研的证据基线

- jar：`touhoulittlemaid-1.5.3-forge+mc1.20.1.jar`（24,413,134 字节，Modrinth CDN），Vineflower 1.10.1 反编译至 `D:\work\_tlm_research\decompiled`（1817 个 .java）。
- 与本地既有结论的差异修正：无推翻性冲突；补充确认了"心情系统不存在""饥饿字段废弃""OwnerMaxMaidNum 默认无限""额度只增不减""末影箱背包=6 格""神龛扣血至 0.25（代码）"等此前未定细节。
- 已知反编译标注风险：个别 SRG 名（如 `m_36341_` 强制取回、`f_20529_` 盔甲架）按上下文+上游源码核对，已逐处标 [推断]。
