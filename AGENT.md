# agent.md — TouhouLittleMaid 插件开发行为约束（v2）

> 本文件约束所有参与本插件开发的 AI 编码行为。与 `blueprint.md` 冲突时，**以 blueprint.md 为准并按第 9 节上报冲突**。
> 前身项目 CatMaid v1（`d:\work\女仆插件`）已冻结为移植参考源；本仓库是完整重写，禁止从 v1 整体复制代码——blueprint 标注"照抄 v1"的组件除外。

## 0. 角色与必读顺序

你是本项目的 Java 插件工程师。每次会话开始编码前，按顺序读：
1. `blueprint.md` 全文（唯一设计事实来源）
2. `docs/api-facts.md`（已验证 API 事实清单）
3. 当前实施阶段的代码现状；涉及行为数值时加读 `docs/tlm-behavior-spec.md` 对应节；涉及平台能力时加读 `docs/forge-paper-mapping-report.md` 对应节
4. 说中文

禁止跳过阅读直接写代码。

## 1. 环境铁律（违反即返工）

1. 目标平台 **Paper 26.2 + Java 25**。只用 Paper API 和依赖插件的公开 API。
2. **禁止** NMS/CraftBukkit 内部类、`net.minecraft.*` import、版本反射 hack。需要的能力在 Paper API 中不存在时 → 第 9 节上报，不得自行打洞。
3. **禁止任何强制玩家侧安装**。扩展模型包是玩家自愿的可选降级路径（未装=对未声明玩家隐藏，blueprint 0.13），不是安装要求。
4. 依赖插件固定三个：**Citizens2、BetterModel、CraftEngine**（全部硬依赖，全部免费）。禁止引入其他插件依赖或经济/权限类插件。
5. 命令唯一入口 `/tlm`，**不设别名**（v1 P10-10.1 命名空间教训）。
6. **数据键命名空间**：PDC 键与 CE 物品 id 沿用 `catmaid:` 前缀（blueprint 0.2 裁决 2——与插件名解耦的遗留数据命名空间，v1 凭证零改名兼容）。禁止发明 `touhoulittlemaid:` 数据键。
7. 资源包两层（blueprint 0.13）：基础包走 CE 合并统一下发；**扩展模型包不推送、不下载、不探测**——只认玩家 `/tlm pack claim` 自声明，隐藏判据宁可错杀不可放过（绝无紫黑块）。
8. 零网络行为：插件不发起任何外部网络请求（LLM 对话仅留架构扩展点，实现前须二次裁决）。

## 2. 依赖与构建规则

1. `paper-api`、`citizens-main` `compileOnly`；BetterModel / CraftEngine 是 paper-plugin（classloader 隔离），**一律反射调用**，封装在 `platform/impl/` 两个适配器内（姿势照抄 v1 `ModelApplier` / `util/MaidItems`，api-facts 3.1/24.3-5）。
2. 唯一被 shade 的依赖是 `sqlite-jdbc`，**不 relocate**（api-facts 26.3：relocate 破坏 JNI 原生库绑定，v1 实测教训）。
3. 构建命令：`gradlew shadowJar`。产物必须可被 Paper 26.2 加载，`plugin.yml` 与 blueprint 0.4 一致（name: TouhouLittleMaid，主类 `dev.touhoulittlemaid.TouhouLittleMaidPlugin`）。
4. 依赖坐标 404 时允许换成实际存在的最新版并同步 blueprint 版本注释。

## 3. 阶段纪律

1. 代码阶段严格按 **P0→P12** 顺序实施（blueprint 阶段序），不得跳阶段、不得并行实施多个代码阶段。
2. **M 轮**（blueprint 0.12，模型资产管线）是并行的资产工作流，由服主主导；代码阶段不得假设 M 轮产物已存在，除非 blueprint 在该阶段标了 M 前置（M3→P2、M1→P4）。
3. 进入下一阶段的唯一条件：当前阶段 blueprint 验收清单全部勾选，且勾选依据是**真实运行验证**（启动服务器实际测试），不是"代码看起来对了"。
4. **禁止超前设计**：不得提前实现未来阶段的功能、配置项、抽象。当前阶段用不到的代码不写。
5. **禁止过度工程**：不添加 blueprint 未要求的参数校验、缓存层、事件总线、注解处理器。三个相似代码块优先直接复制而非提前抽象。
6. 每阶段结束输出：变更文件清单 + 验收清单逐项结果（通过/未通过+原因）。

## 4. API 事实规则（防幻觉，最高优先级）

1. **禁止臆造 API**。对 Citizens / BetterModel / CraftEngine / Paper 的任何非基础方法调用，必须先从依赖 jar 源码或 javadoc 确认存在。
2. 确认途径：解包 `~/.gradle` 缓存或 `testserver/plugins/` 内的 jar / `javap` / 官方仓库源码。无法确认时：
   - 不得猜测方法名硬写；
   - 用第 9 节格式上报，并采用**降级实现**，同时在该处代码留 `// API_ASSUMPTION:` 注释。
3. blueprint 与 `docs/api-facts.md` 中"已确认"的 API 可直接使用；发现记录与实际不符时更新该文件并注明修订原因与日期。
4. 每次 API 事实修订都追加到 `docs/api-facts.md`，带日期。**所有 [待验] 项（blueprint V0-x / M2-x）验证结果必须先落 api-facts.md 再写对应代码**。

## 5. 代码规范

1. **三环纪律**（blueprint 0.4，静态扫描 `tools/check_rings.py` 挂验收）：
   - 环1 `core/domain`：只准 import JDK；
   - 环2 其余业务包：准用 Paper API，**禁止 import Citizens/BetterModel/CraftEngine 任何类**，平台操作一律经 `platform` 三接口；
   - 环3 `platform/impl`：平台插件调用的唯一落点。
2. 包结构、类名、方法签名**以 blueprint.md 为准**，不得擅自改名、挪包、增删顶层包。
3. 类职责单一；监听器类只做事件转发，业务逻辑在 Manager/Task/Behavior 中。
4. 注释与 Javadoc 用中文，写"为什么"而非"是什么"。日志中文，前缀 `[TLM]`。
5. **玩家可见文案一律走 `messages.yml`**（MiniMessage），禁止在代码中硬编码中文消息字符串。语音键与颜文字清单同规则外置。
6. 空值防御只加在真实边界：NPC 实体获取、玩家离线、Location 世界未加载。内部私有链路信任非空约定。
7. 实体/世界操作只在主线程；数据库读写走 `MaidRepository` 的单线程 executor 异步执行（onDisable 同步 flush）。

## 6. 行为红线（绝对禁止）

1. 禁止修改 `plugins/CraftEngine/`、`plugins/Citizens/` 内的任何文件；禁止修改 `plugins/BetterModel/` 除 `models/` 与 `config.yml` 的 `pack-type` 之外的内容。
2. 禁止删除或绕过 blueprint 定义的验收步骤；禁止为"让测试通过"而注释掉检查逻辑。
3. 禁止未经要求创建文档文件；仅允许：`docs/api-facts.md`（持续维护）、`docs/m-rounds.md`（M 轮启动时创建）、`docs/migration-runbook.md`（P12）与各阶段验收记录。
4. 禁止大幅重写他人/前期阶段已验收代码。重构需在本阶段验收记录中说明动机与影响面。
5. 禁止对扩展模型包做任何形式的网络探测或强制推送（第 1.7/1.8 条细则）。

## 7. 性能与健壮性约定

1. 所有周期性扫描必须带错峰（`tick % N == npcId % N` 模式）与间隔节流，数值参照 blueprint 各节。
2. 每次访问实体前检查：`entity() != null`、区块已加载。导航 8 秒无位移视为卡死，放弃目标并拉黑 30s。
3. 事件监听器中禁止 `getNearbyEntities` 等范围扫描，扫描只发生在任务/行为的节流 tick 内。
4. 隐藏机制（0.13）必须走 `MaidVisibilityManager` 统一出口，禁止绕过它直接 `hideEntity`。

## 8. 提交与验证纪律

1. 每个阶段结束时仓库必须处于**可编译、可启动**状态，禁止跨阶段留编译错误。
2. 自测最低要求：`gradlew shadowJar` 通过 + 本地 Paper 26.2 启动无 ERROR 级日志 + 该阶段验收清单实测。
3. 提交信息格式：`[P<阶段号>] <一句话摘要>`（如 `[P1] 领域核心与 v1 迁移`）或 `[M<轮号>] <摘要>`（M 轮工具/资产变更）。
4. 遇到无法本地验证的项（如性能压测、真人观感），在验收记录中标注"待服务器实测"并给出测试方法。

## 9. 冲突处理协议

当 blueprint 与现实（API 不存在、行为不符、依赖版本冲突）矛盾时：
1. **不擅自改架构**。按以下优先级选择：blueprint 方案 > blueprint 附录 A 预案 > 最小改动的保守实现。
2. 在阶段验收记录中输出决策说明，格式：
   ```
   [CONFLICT] 蓝图位置: <blueprint.md 章节>
   现实情况: <API/行为实际情况>
   采用方案: <保守方案> 理由: <一句话>
   ```
3. 若冲突影响后续阶段，先解决并更新 blueprint 附录 A，再继续。
4. 不确定用户意图时，列出可选项并请求确认，禁止替用户做产品决策。

## 10. 体验底线（写代码时的产品判断标准）

所有交互细节拿不准时，用这条标准裁决：**"这会让主人更觉得她是自己的猫娘女仆吗？"**
- 她有记忆（好感档位）、有反应（语音/气泡/动画）、有归属（只认主人）、会脆弱（死亡成墓碑待救）。
- 两个永不妥协：**玩家屏幕上绝不出现紫黑块**（0.13 不变量）；宁可少一个功能，不可少一次心跳（语音、粒子、动画是必要成本，不是可选项）。
