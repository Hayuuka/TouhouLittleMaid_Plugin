# Phase 0 验收记录（P0 平台骨架）

> 日期：2026-11-18 · 验收人：P0 实施窗口（服务器实测 + 服主客户端确认）
> 依据：`blueprint.md` Phase 0「验收清单」，API/行为细节见 `docs/api-facts.md` 第 29 节。

## 环境

| 项 | 值 |
|---|---|
| 服务端 | Paper 26.2-135（API `26.2.build.135-stable`） |
| Java | 运行时 JDK 26；插件字节码 `--release 25` |
| 依赖 | Citizens 2.0.44-b4269、BetterModel 3.7.0-SNAPSHOT-541、CraftEngine 26.9.2 |
| 构建 | Gradle 9.9.0 + shadow 9.6.1，产物 `build/libs/TouhouLittleMaid-2.0.0.jar`（34,030 B） |
| 测试模型 | `winefox`（自带 `RightHandLocator`，已注入 0.01 格物品锚点 cube） |

## 验收清单

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | `gradlew shadowJar` 通过；Paper 26.2 启动 0 ERROR | **通过** | `gradlew clean shadowJar` EXIT=0（仅 unchecked 反射告警）；22:11 启动日志 `Done (15.274s)`，`latest.log` 无 ERROR，TLM `[TLM] 已启用：P0 平台骨架就绪` |
| 2 | `tools/check_rings.py` 扫描通过 | **通过** | `三环扫描通过：11 个 Java 文件，无越界引用` |
| 3 | `/tlm spike summon → 模型/idle → nav 3 → destroy` 全链无异常 | **通过** | 22:43 验收链：NPC 99 `spawned=true`、`idle=true walk=true`、`boneDisplays=122`；`nav` 到 z=13，`pos` z=12.31、`navigating=false`；`destroy` 后 `spawned=false`。全程无异常/ERROR |
| 4 | V0-1..V0-6 全部有结论并写入 api-facts 第 29 节 | **通过** | 见下表与 `api-facts.md` 29.3/29.4 |

## V0 结论汇总

| 编号 | 结论 | 状态 |
|---|---|---|
| V0-1 | CE 自定义音效 `touhoulittlemaid:maid.hurt` 可播；`ce reload all` 才重建包；`sounds.yml name` 必须全限定 | 通过（客户端听感确认） |
| V0-2 | 骨 `setBoneItem` 可渲染；需带 display 的骨（注入 0.01 格 cube）；`setBoneItem` 自动套 `THIRDPERSON_RIGHTHAND` 修正握点；模型组旋转 `[-90,0,180]` 修正朝前 | 通过（客户端视觉确认） |
| V0-3 | `Interaction#setResponsive(true)` 右键触发事件、`addPassenger` 可叠坐；女仆上座后 `boneDisplays=122` 稳定 | 通过（服务端 + 客户端操作） |
| V0-4 | `HumanEntity#getAttackCooldown()` 返回 0..1 蓄力进度（实测 `0.1→0.7→1.0`） | 通过 |
| V0-5 | 玩家型 NPC 死亡会派发 `PlayerDeathEvent`；CE 的 `EntityDeathLootListener` 对其 NPE → **必须拦截原版死亡** | 通过（附风险，记入 P1/P9 设计约束） |
| V0-6 | Citizens 导航把关闭的门当可通行、不触发开门 → P3 需自实现"临近开门→通过→关门" | 通过（结论为需自实现） |

> V0-7（BM 单模型构建 hash 稳定性）不在 Phase 0 验收清单内，属 M3 分包前置，本轮未验。

## 已知风险与后续约束

1. **V0-5**：女仆必须避免走原版死亡（拦截/自管血量与复活），否则 CE 每次死亡刷一条 NPE ERROR。
2. **V0-6**：P3 必须实现门开关逻辑，否则女仆穿门是穿模（不是卡住）。
3. **狐狸坐骑**：`FOX` 是同一 bbmodel 的第二骨架；原版 `parallel1` 用血量 `<20%` 控制显隐，`sit/gomoku/picnic` 才转为坐垫。P4 必须补 `FOX scale` 规则，不能默认常显。
4. **CE 测试包**：仅为 V0-1 验收资产，位于测试服 `plugins/CraftEngine/resources/tlmtest/`，不入仓库（R2-4）。

## P0 交付物

- 代码：`core/domain/{FavorTier,MaidInventory}`、`platform/{MaidEntityPlatform,MaidModelPlatform,ContentPlatform}`、`platform/impl/{CitizensEntityPlatform,BetterModelPlatform,CraftEngineContent,MaidTraitV2}`、`command/TlmCommand`、`TouhouLittleMaidPlugin`（共 11 个 Java 文件）。
- 构建/资源：`build.gradle.kts`、`settings.gradle.kts`、`gradle/wrapper`、`src/main/resources/plugin.yml`。
- 工具：`tools/check_rings.py`、`tools/rcon.py`、`tools/bbmodel_preview.py`。
- 文档：`docs/api-facts.md` 第 29 节（API 事实与 V0 结论）、`docs/blueprint-revisions.md` R1/R2。

## 结论

Phase 0 验收清单 **4/4 通过**；V0-1..V0-6 均有书面结论。可进入 Phase 1（领域核心与数据层）。
