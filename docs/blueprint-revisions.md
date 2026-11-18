# 蓝图修订记录（blueprint-revisions.md）

> 本文件是 [blueprint.md](../blueprint.md) 的**修订台账**：每次对蓝图的实质性修改在此留痕，
> 与 `docs/api-facts.md`（API 事实）互补——api-facts 记"代码与依赖的事实"，本文件记"蓝图文本改了什么、为什么改"。
> 规则：
> 1. 每次修订一条记录（R1、R2、…），倒序不重排，老记录永不改写（勘误以追加方式补记）。
> 2. 每条记录必须写明：冲突来源 → 蓝图修订位置（章节/原文 → 新文）→ 理由 → 代码对齐状态。
> 3. 修订原因三类：实施窗口上报的冲突（引用 api-facts 对应节）、事实核验推翻、服主裁决。
> 4. 只改与冲突直接相关的文字，不顺手"改进"无关章节（最小改动纪律）。
> 5. 修订中发现的**新问题但不属本轮裁决范围**的，记入该记录的"附带发现（未裁决）"，留待后续修订或阶段开工前裁决。

---

## R2 — AGENTS.md 合规审计修正（2026-11-18）

**来源**：服主发现 `AGENTS.md` 未被加载，要求按 AGENTS.md 全文复核 P0 落码。
**结论**：三环扫描 11 文件通过、无 NMS/CraftBukkit 引用、无玩家可见硬编码文案、仅三个依赖、命令唯一 `/tlm`。
发现并修正 1 处接口越界，另记录 3 处 AGENTS 与蓝图/测试服的冲突处理。

### R2-1 收回 `setBoneItemTransform` 接口方法（回归蓝图签名）

- **冲突**：V0-2 调试期在 `MaidModelPlatform` 新增了 `setBoneItemTransform(...)`，蓝图 Phase 0 接口块
  只有 `setBoneItem/clearBoneItem`；违反 AGENTS 5.2「方法签名以 blueprint 为准」与 3.4「不写未来阶段代码」。
- **修正**：删除接口方法与 `bonetf` 探针；`setBoneItem` 内部在物品挂上后**自动**套用
  `THIRDPERSON_RIGHTHAND`（`RenderedBone#applyAtDisplay` → `ModelDisplay#display`）。接口回归蓝图原文。
- **代码对齐**：`MaidModelPlatform` 只保留蓝图方法；`BetterModelPlatform#setBoneItem` 自动套用；
  `TlmCommand` 移除 `bonetf`。V0-2 客户端复验：剑身朝正前方、手在剑柄。

### R2-2 `/tlm spike` 探针集扩展（V0 验收所需）

- R1-4 只登记了 6 个子命令；P0 验收 V0-1..V0-4 实际需要
  `pos`（走动位移）、`attack`（冷却曲线）、`sound`（CE 音效）、`seat/sit/stand/stopanim`（座椅与动画状态）。
- 这些均为**控制台日志探针**、无玩家可见文案，P1 正式命令树落地时整体删除；已在 api-facts 29.3 登记复跑入口。

### R2-3 `softdepend` 与 AGENTS 1.4「硬依赖」的冲突（保留 softdepend）

- **AGENTS 1.4**：三插件「全部硬依赖」；**蓝图 P0**：onEnable 依赖自检，缺一自禁用。
  用 `depend` 时缺依赖 Paper 直接拒绝加载，无法执行蓝图要求的中文自禁用路径。
- **裁决**：以蓝图 P0 为准保留 `softdepend`（加载顺序仍由 softdepend 保证），自检逻辑保留；
  此差异在此留痕，AGENTS 1.4 的字面口径待服主后续裁决。

### R2-4 CE 测试包位置与 AGENTS 6.1 红线（测试服专用）

- **AGENTS 6.1**：禁止修改 `plugins/CraftEngine/` 内任何文件；**蓝图 V0-1**：要求在 CE 资源包放 ogg + sounds.json 并实测。
- **处理**：仅在测试服新增独立测试包 `plugins/CraftEngine/resources/tlmtest/`（不碰 CE 自身配置/数据），
  `pack.yml` namespace 按蓝图 460 用 `catmaid`；**不属于仓库交付物，P1 前移除或外置备份**。

### R2-5 V0-1 音效键命名空间修正

- 初版测试用 `tlmtest:maid.hurt`，蓝图 460 明确写 `playSound("catmaid:maid.hurt")`；违反 AGENTS 1.6 数据键命名空间。
  已将测试包 namespace/事件名改为 `catmaid:maid.hurt`，并确认 `sounds.yml` 的 `name` 必须写全限定名，
  否则客户端解析成 `minecraft:maid_hurt` 而静音。

### R2-6 模型朝向修正入仓

- `model_src/酒狐家族/winefox/winefox.bbmodel` 的 `RightHandLocator` 组旋转已写为 `[-90, 0, 180]`
  （配合 `setBoneItem` 自动 `THIRDPERSON_RIGHTHAND`），V0-2 视觉验收通过。

### R2-7 `warnOnce` 去重粒度修正

- 原实现用单个 `boolean warned`，第一条告警后吞掉后续所有告警（审计发现的健壮性缺陷）；
  改为 `Set<String>` 按消息去重，仍保持「同类只告警一次」的降噪意图。

---

## R1 — P0 冲突处理（2026-11-18）

**冲突来源**：v2 P0 实施窗口上报的 4 项蓝图冲突，全文记入 `docs/api-facts.md` 第 29.2 节（2026-11-18）。
**裁决性质**：均为实施必要性冲突（不改无法继续），按最小改动原则处理，无服主裁决项。
**代码对齐状态**：4 项修订所对应的代码**均已随 P0 落地**（见下文对齐表），本次修订是"代码事实 → 蓝图文本"的反向同步，不产生新代码。

### R1-1 `applyAttributes/applyEquipment` 首参补 `int npcId`

- **冲突**：P0 平台接口签名（蓝图 Phase 0 节"平台接口签名（固定）"代码块）原写
  `void applyAttributes(FavorTier tier, boolean struckByLightning)` / `void applyEquipment(MaidInventory inv)`——
  两方法均无 npcId，调用方无法定位实体，属蓝图 0.7 起草时的笔误（api-facts 29.2-1 定性：blueprint 缺陷）。
- **修订**：
  | 位置 | 原文 → 新文 |
  |---|---|
  | Phase 0 接口签名块 | `applyAttributes(FavorTier, boolean)` → `applyAttributes(int npcId, FavorTier tier, boolean struckByLightning)`；`applyEquipment(MaidInventory)` → `applyEquipment(int npcId, MaidInventory inv)`。行尾注释标注 R1 与 api-facts 29.2-1 出处 |
  | P1 关键伪代码（MaidManager.summon 第 4 步） | `entityPlatform.applyAttributes(FavorTier.LV0, false)` → `entityPlatform.applyAttributes(npcId, FavorTier.LV0, false)` |
  | P0 新增文件表 CitizensEntityPlatform 行 | 补注 `applyEquipment` 为 P0 空实现占位、P1 实装 |
- **未动**：P11 祭坛节的"applyAttributes 重算"为文字描述不涉签名；0.7 FavorabilityManager 段只提 TierApplier 注入，接口未变。
- **代码对齐**：`platform/MaidEntityPlatform.java` 已按新签名声明；`CitizensEntityPlatform#applyAttributes` 已实现（属性 baseValue 重设 + 血量同步），`applyEquipment` 为带注释的空实现（P1 实装）。

### R1-2 `MaidTraitV2` 落点：`maid/` → `platform/impl/`

- **冲突**：P1 新增文件表原写 `maid/MaidTraitV2.java`。但该类必须 `extends net.citizensnpcs.api.trait.Trait`，
  而 0.4 三环纪律禁止环2 import Citizens → `maid/`（若为环2 包）不可能是合法落点；同时 P0 的
  spawn 链（`getOrAddTrait(MaidTraitV2)` 参与 spawn 签名注释）已经需要它存在（api-facts 29.2-2）。
- **修订**：
  | 位置 | 原文 → 新文 |
  |---|---|
  | P1 新增文件表 | `maid/MaidTraitV2.java` 行 → `platform/impl/MaidTraitV2.java` 行，职责补写继承约束、trait 名 `tlm`、"已随 P0 spawn 链落地"、**禁止新建 `maid/` 顶层包** |
  | P0 新增文件表 | 新增 `platform/impl/MaidTraitV2.java` 行（P0 落地项，含继承约束说明） |
- **代码对齐**：`platform/impl/MaidTraitV2.java` 已落地——`extends Trait`、trait 名 `"tlm"`、`load/save` 持久化 maidId（DataKey 键 `maid`）、不驱动行为（`run()` 永不被调，api-facts 17.2 结论写进 Javadoc）。
- **纪律口径**：此后任何"名字带 maid 的类"落包判断标准 = 是否 import 平台类；不 import 的照常走环2 既有包（见附带发现）。

### R1-3 `FavorTier` / `MaidInventory` 随 P0 落地（MaidInventory 仅 82 槽常量）

- **冲突**：P0 平台接口签名依赖 `core/domain/FavorTier` 与 `core/domain/MaidInventory`，但蓝图把全部
  `core/domain/**` 排在 P1——P0 编译期就拿不到这两个类型（api-facts 29.2-3）。
- **修订**：
  | 位置 | 原文 → 新文 |
  |---|---|
  | P0 新增文件表 | 新增 `core/domain/FavorTier.java` 行（**0.7 数值完整实现随 P0 落地**）与 `core/domain/MaidInventory.java` 行（**P0 仅 82 槽常量**，0.8 口径；槽位物品存储与实体装备写回留 P1） |
  | P1 新增文件表 core/domain 行 | 类清单中移除已落地两者，改注"MaidInventory 在本阶段补齐槽位物品存储与 applyEquipment 实体装备写回" |
- **边界钉死**（防止 P0 过度实装）：`MaidInventory` 在 P0 **只有** `SIZE=82` 与槽区段常量
  （MAIN_HAND=0 / OFF_HAND=1 / HELMET..BOOTS=2..5 / BACKPACK=6..41 / BAUBLE=42..71 / HIDDEN=72 / TASK=73..81），
  不做物品数组、不做读写 API——那是 P1 的事。`FavorTier` 是 0.7 完整实现（四档数值 + `of(favorability)`）。
- **代码对齐**：两文件已落地且与上述边界逐条一致（`FavorTier` 为 enum + 公开 final 字段 + `of()`；
  `MaidInventory` 为私有构造的常量类，无任何 Bukkit 类型——环1 纯净域守住）。

### R1-4 新增 `/tlm spike` 探针（P0 链路触发入口）

- **冲突**：P0 验收链"创建 NPC → 挂模型 → 播动画 → 导航一步 → 销毁"需要触发入口，但正式命令树
  `/tlm` 是 P1 产物（api-facts 29.2-4）。
- **修订**：
  | 位置 | 原文 → 新文 |
  |---|---|
  | P0 目标段 | 补"触发入口 = `/tlm spike` 探针命令，仅控制台日志" |
  | P0 新增文件表 | 新增 `command/TlmCommand.java` 行：探针子命令清单、只走控制台日志（不硬编码玩家可见文案——messages.yml P1 才接入）、P1 正式命令树落地时整体替换 |
  | P0 验收清单 | "命令生成 NPC → …" 一条改为 `/tlm spike summon <model>` → `/tlm spike nav <npcId> 3` → `/tlm spike destroy <npcId>` 的可复跑链（复跑入口见 api-facts 29.3） |
  | P1 新增文件表 command 行 | 补注"落地时整体替换 P0 的 `/tlm spike` 探针（使命结束即删，不做别名保留）" |
- **子命令集**（与代码一致，与 api-facts 29.3 复跑入口一致）：
  `summon [model] [name]` / `anim <npcId> <animName>` / `nav <npcId> [距离]` / `destroy <npcId>` /
  `bone <npcId> <boneName> <MATERIAL|clear>` / `check <npcId>`（check 打印 spawned/uuid/idle/walk/boneDisplays/attackCooldown）。
- **代码对齐**：`command/TlmCommand.java` 已落地，`spike` 子命令前缀、Tab 补全、控制台日志输出（`[TLM]` 前缀）、零聊天栏玩家文案，全部符合上述口径。

### R1 附带发现（未裁决，留待后续）

1. **P2 新增文件表存在 `maid/` 顶层包条目**（`maid/NameTagManager.java`、`maid/MaidVisibilityManager.java`、
   `maid/ModelPackManager.java`）：这三个类**不继承 Citizens Trait**，不违反 0.4 环纪律；但 0.5 包结构声明
   "固定，不得擅自增删顶层包"且清单里没有 `maid/`——蓝图自身前后不一致。另 0.13 又写 `ModelPackManager`
   是"环2，gui 同级"。本轮 4 项冲突不含它，**未改**；P2 开工前必须裁决（建议：并入 `gui` 或新建 `visibility/`
   并同步修订 0.5 包结构，二选一，届时记 R2）。
2. `MaidInventory` 常量类当前为 `final class` + 私有构造的纯常量形态；P1 补齐存储时需要改实例类
  （`Maid` 字段持有实例）。这是预期演进不是冲突，P1 照 0.7/0.8 实现即可，本记录仅提示"别被 P0 形态误导"。
3. api-facts 29.1 的 8 条静态确认（如 `Player#hideEntity` 无 UUID 重载、`Tracker#hasAnimation` 不存在）
  属于"实现细节级"事实，不足以构成蓝图冲突，已在适配器实现内消化，蓝图签名块**未**逐条反写
  （签名是门面语义，不是 javap 镜像）；后续窗口如认为需要把"UUID 先解析再 hide"这类约束写进蓝图注释，另起修订。

### R1 修订统计

| 项 | 数 |
|---|---|
| 修订条目 | 4（冲突 1..4）+ 1 页脚标记 |
| 触碰章节 | Phase 0（目标/新增文件表/接口签名/验收清单）、Phase 1（新增文件表 3 行/伪代码 1 行）、文末页脚 |
| 未触碰 | 0..0.13 总览章、P2..P12、附录 A/B/C（P2 的 `maid/` 行按附带发现 1 保留原样） |
| 代码同步 | 11 个 Java 文件已存在且与修订后蓝图一致（platform 三接口 + impl 四类 + core/domain 两类 + command/TlmCommand + 主类） |

---

## R2 — 命名空间推翻、v1 兼容废止与 P0 上报冲突裁决（2026-11-18）

**背景**：服主发现 P0 落码期间 AGENTS.md 被误命名为 AGENT.md 未加载，补载后全量复核；本轮裁决共 5 项——
4 项为 P0 上报冲突的裁决（其中 3 项由服主在 AskUserQuestion 终裁确认），1 项为 R1 修订的自查补漏。
**裁决来源**：服主 2026-11-18 三问终裁（命名空间 / softdepend / CE 目录）+ P0 报告的 R2-2 探针集事实。

### R2-1 数据键命名空间：`catmaid:` → `touhoulittlemaid:`（服主终裁，推翻 0.2 裁决 2 后半）

- **冲突**：服主质疑新项目为何沿用旧插件命名空间。原方案是 0.2 裁决 2（2026-11-17）有意设计（v1 凭证零改名兼容）；
  按 AGENTS 9.4 上摆选项后服主终裁**推翻**，连带裁决 v1 兼容整体废止（R2-5）——两案互为前提。
- **修订**（blueprint.md）：
  - 0.2 裁决 2：改为"PDC 键与 CE 物品 id 一律用 `touhoulittlemaid:` 前缀（R2 裁决：全新项目零 v1 兼容）；禁止使用 `catmaid:` 遗留键"，标注原案推翻日期与去向（0.10）。
  - 全文键名替换（18 处）：V0-1 音效键 `touhoulittlemaid:maid.hurt`、P1 魂符 PDC `touhoulittlemaid:soul_talisman/soul_init/maid_id` 与 CE `touhoulittlemaid:talisman`、P3 `maid_bed/kappa_compass`、P4/P5/P11 `gohei`、P6 `backpack_*`、P7 `picnic_mat`、P8 `bauble/<id>`、P9 `tombstone/film/shrine`、P10 语音键 `touhoulittlemaid:maid.<pack>.<短名>`、P11 `power_point`。
  - 0.2 裁决 3 魂符项：PDC 键改新前缀，"v1 老玩家兼容入口"表述改为"永久并行获取口"。
  - 保留 2 处字面 `catmaid`：0.1 表"v1 的 /catmaid 退役"（命令史实）、0.2 裁决 2 修订注（历史方案引用）。
- **AGENTS.md 同步**：1.6 条镜像修订（用 `touhoulittlemaid:`、禁 `catmaid:` 遗留键，注明 R2 出处）。
- **代码影响**：P0 已落码无 PDC 键写入（探针只串平台链），唯一受影响物 = **CE 测试包需按新前缀重建**（见返回报告）。

### R2-2 探针集扩展（P0 上报，事实同步）

- **冲突**：R1-4 只列了 6 个子命令，P0 为覆盖 V0-1..V0-4 验收实际扩到 13 个（pos/attack/sound/seat/sit/stand/stopanim）。
- **修订**：P0 新增文件表探针行更新为 13 子命令全集，并注明与 V0 项的对应关系（sound→V0-1、bone→V0-2、seat→V0-3、attack→V0-4、sit/stand/stopanim/pos→动画与位移观察）。
- **代码对齐**：已核对 `TlmCommand.java` switch 分支（13 个 case），与修订后清单一致。
- **定性**：不属"擅自扩接口"——探针是验收工具不是平台接口（P0 同轮已删越界的 `setBoneItemTransform`，接口回归蓝图原文，处理正确）。

### R2-3 plugin.yml 依赖声明：`softdepend` + 运行时自检（服主终裁）

- **冲突**：AGENTS 1.4"硬依赖"若按 plugin.yml `depend` 理解，缺依赖时 Paper 直接拒绝加载，蓝图 P0"依赖自检（三插件缺一禁用自身）"路径无法执行。
- **裁决**：服主确认 softdepend 方案。**语义澄清入档**："硬依赖"指产品层（三插件缺一则插件不可用），声明层用 `softdepend` + onEnable 自检（缺一 → 中文告警 → 自禁用）是蓝图该行为的唯一可行实现，非违反。
- **修订**：无需改 blueprint 正文（P0 表 TouhouLittleMaidPlugin 行已含"依赖自检（三插件缺一禁用自身）"）；本记录与 api-facts 29.4 固化口径，防后续窗口再犯。

### R2-4 CE 目录解禁：修改须说明（服主终裁，AGENTS.md 6.1 修订）

- **冲突**：AGENTS 6.1 原文绝对禁止修改 `plugins/CraftEngine/`，而 V0-1 验收本身要求在 CE 资源包放 ogg+sounds.json（AGENTS 6.2 又禁止绕过蓝图验收步骤——两条规则撞车）。
- **裁决**：服主终裁解除 CraftEngine 禁令，改为**说明义务**：修改前必须说明改了什么及目的（阶段验收记录或本台账留痕）。
- **修订**：AGENTS.md 6.1 已改（Citizens 禁令与 BetterModel 限制条款不动）；blueprint 无需改（无相关条款）；P0 的 CE 测试包操作自此**合法**，但仍须按说明义务留痕。

### R2-5 v1 兼容整体废止（服主终裁，含影响面报告）

- **裁决**：**不做对 v1（CatMaid 旧插件）的任何兼容**。原 P1 的 `MigrationFromV1`、`/tlm admin migrate-v1`、v1 铃铛→胶片转换、v1 凭证 PDC 互认全部删除；v1 生产数据不迁移，老玩家从零开始。
- **影响面报告**（服主要求的评估结论）：

| 模块 | 影响 | 处置 |
|---|---|---|
| P0 已落码 | 无 v1 兼容代码；CE 测试包前缀受 R2-1 连带 | 测试包重建（返报告第 1 项） |
| P1 | 删 `MigrationFromV1` 文件行、`migrate-v1` 子命令、迁移验收项 | 工作量**减** |
| P9 | 删"v1 迁移复验"验收项 | 验收量减 |
| P12 | "v1 下线与收口"改为"v1 归档与收口"，删迁移流程与"迁移玩家旅程"验收项 | 仅剩归档动作 |
| 0.13 | 第 6 条（v1 存量模型归层）删除——新插件选模型即写新档，无存量场景 | 无功能损失 |
| api-facts | 24.3-6"与 v1 互认"口径作废；25.x 魂符三态、3.x/16.x/17.x/28 节"照抄 v1"姿势事实**不受影响**（v1 仓库仍冻结为移植参考源——agent.md 头部角色保留） | 29.4 通告 |
| 生产后果 | **v1 服老玩家的存量女仆/好感/物品无法带入 v2，从零开始** | 服主知情决策（终裁原文："不做对旧插件任何的兼容"） |
| 服主侧资产 | `docs/craftengine物品定义.yml` 与 CE 侧物品定义前缀需换 `touhoulittlemaid:` | P1 开工时服主同步 |

- **结论**：删除 v1 兼容**不影响任何 v2 自身功能模块**（魂符三态、GUI、死亡链等"照抄 v1"均指实现姿势出处，非兼容层），只减少工作量；唯一运行时后果即上表"生产后果"行。
- **修订**（blueprint.md）：0.10 节整体替换为废止说明；0.3 表"v1 铃铛迁移转胶片"尾巴改注；0.13 第 6 条划线删除；P1 标题/目标/表行/验收、P9 验收、P12 目标/收口节/最终验收同步删改。

### R2-6 R1 自查补漏（AGENTS.md 加载后复核）

- R1 修订本身合规（blueprint-revisions.md 有服主明示授权；未动命名空间；签名修订走第 9 节协议）。
- 补漏：AGENTS 6.3 文档白名单追加 `docs/blueprint-revisions.md`（本次同步修订）。
- R1 附带发现 1（P2 表 `maid/` 包与 0.5 包结构不一致）**仍然待裁决**，P2 开工前必须收口。

### R2 修订统计

| 项 | 数 |
|---|---|
| 修订条目 | 5 裁决 + 1 自查补漏 |
| blueprint.md 触碰 | 0.2/0.3/0.10/0.13、P0（表/清单）、P1（标题/目标/表/验收）、P9 验收、P12（目标/收口/验收）、18 处键名、页脚 |
| AGENTS.md 触碰 | 1.6、6.1、6.3 |
| api-facts.md 触碰 | 头部更新行 + 新增 29.4 |
| 需 P0 返工 | CE 测试包按 `touhoulittlemaid:` 前缀重建（含 pack.yml namespace / sounds.yml 键名 / assets 目录） |

---

*维护口径：本文件由蓝图修订者维护；每轮阶段实施上报冲突 → 先记 api-facts（事实）→ 再改 blueprint（文本）→ 最后在此留痕（台账）。*
