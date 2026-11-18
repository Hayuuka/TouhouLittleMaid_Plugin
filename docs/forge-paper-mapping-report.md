# TLM → CatMaid 移植能力映射报告（Forge 1.20.1 → Paper 26.2 纯插件）

> 2026-11-17 调研产出。**纯调研报告，未改动任何项目文件。**
> 对象：把"车万女仆"（Touhou Little Maid，TLM，MC 1.20.1 Forge mod）用到的一类 Forge 能力，
> 映射到 CatMaid 所在的 Paper 26.2 纯插件世界（Citizens2 + BetterModel + 可选 CraftEngine）。
>
> **证据标记**：
> - **[JAR]** = 本轮对本地 jar `javap`/jar 目录清单逐项核对（paper-api 26.2.build.135-stable、Citizens 2.0.44、BetterModel 3.6.0 dev 541）；
> - **[实测]** = 引用 docs/api-facts.md 或 docs/pending-bug-verification.md 的既有实测结论（注明章节号）；
> - **[文档]** = 来自公开文档/上游源码（Forge 1.20.1 文档、Bukkit `JavaPlugin` 源码、TLM 1.20 分支源码结构，引用处注明）；
> - **[推断]** = 无法本地确认，写明验证途径，不臆造。
>
> 项目铁律提醒（agent.md 1）：纯插件、禁 NMS/`net.minecraft.*`、禁新插件依赖、禁客户端 mod。

---

## 0. TLM 侧技术盘点（映射的"左边"）

TLM（1.20 分支，`TartaricAcid/TouhouLittleMaid`）的架构轮廓（来源：pending-bug-verification §7/§7.1 直读源码 + api-facts 25.2 + Modrinth jar 结构 [实测/文档]）：

| 层 | TLM 用到的 Forge 能力 |
|---|---|
| 实体 | `EntityMaid extends TamableAnimal`（自有 EntityType 注册、GoalSelector AI、原版 PathNavigation、Forge Capability `MaidNumCapability`、实体 NBT 自动序列化） |
| 事件 | `SlabClickEvent`（自定义事件）、`LivingDeathEvent`、玩家交互事件、`TickEvent` 等 Forge 总线事件 |
| 物品 | 自定义 Item 注册（`ItemSmartSlab`/`ItemServantBell`/`ItemFilm`）、`useOn`/`use`、`canFitInsideContainerItems`、`isFoil`、物品冷却 `getCooldowns().addCooldown` |
| 方块 | `BlockShrine`（神社复活仪式）、`EntityTombstone`（墓碑实体） |
| 渲染/动画 | GeckoLib（bone 级关键帧动画 + molang 表达式 + item 挂骨）、基岩几何 JSON 模型（292 个，`tlm_custom_pack` 开放模型包） |
| GUI | `AbstractContainerMenu`（服务端槽位逻辑 + 客户端自绘 Screen） |
| 持久化 | 实体 NBT（Owner 写入）+ `SavedData`（`MaidWorldData` 全局台账）+ Forge Capability |
| 资源 | Forge 资源包/datapack 结构、语音包（自定义 .ogg + sounds.json） |
| 客户端 | 客户端 Screen、自定义粒子渲染、HUD、GeckoLib 客户端动画管线、molang |

CatMaid 的"右边"：Citizens 玩家型 NPC（`ServerPlayer` 假人）+ 自建 BehaviorMode 调度器 + BetterModel display 实体逐帧动画 + 原版物品 + PDC + SQLite。

---

## 1. 实体系统

### 1.1 能力映射表

| 能力 | Forge/TLM 侧 | Paper 侧等价/替代 | 可行性 | 关键 API | 证据 |
|---|---|---|---|---|---|
| 自定义 EntityType 注册 | `Registry.register(ForgeRegistries.ENTITY_TYPES, id, EntityType.Builder.of(EntityMaid::new, MobCategory.CREATURE))` | **不可行**。Paper 的 `Registry` 只读（`Registry$SimpleRegistry` 存在但无插件注册口 [JAR]）；插件不能新增 `EntityType`。既有替代：Citizens NPC（PLAYER 型）+ BetterModel 模型，CatMaid 全链路已走通 | 可直接做（沿既有路线） | `net.citizensnpcs.api.npc.NPCRegistry#createNPC(EntityType, String)` [JAR api-facts 3.2] | [JAR][实测] |
| 实体构造与类型语义 | `EntityMaid` 是真 Mob：有 AI/导航/掉落/属性全套 | 玩家型 NPC 是假 `ServerPlayer`：**不在 level 实体索引、原版命令选不中、无 mob AI 语义**（api-facts 18.3-1 / 21.3-4） | 已接受（架构既定） | `NPC#getEntity(): LivingEntity` [JAR 3.2] | [实测] |
| 实体 NBT 序列化（caps/SaveDoc） | Forge 实体自动 `save/load` NBT + `Capability` 随实体存档 | Bukkit `Entity` 接口**没有任何 NBT 读写方法**（本轮 javap 过滤确认无 NBT 入口 [JAR]）；只有 PDC + Citizens `Trait#load(DataKey)/save(DataKey)` [JAR 3.2] + 自有 SQLite | 可直接做（CatMaid 已是全自持） | `Entity#getPersistentDataContainer()`（`Entity extends PersistentDataHolder` [JAR]）；`ItemStack#serializeAsBytes()/deserializeBytes(byte[])` [JAR 3.3] | [JAR][实测] |
| GoalSelector / Brain（任务优先级 + 记忆黑板） | GoalSelector 按优先级插队抢占；Brain 用 MemoryModule/传感器/活动组合 | CatMaid 自建 BehaviorMode：单模式切换 + 坐下硬拦截（api-facts 17.4）。**评估：优先级抢占用"单模式 + 事件触发切换"已覆盖**（主人被打转战斗 = OwnerProtectionListener 事件驱动）；**MemoryBoard 类黑板不值得搬**——女仆的记忆需求只有"导航卡死拉黑目标 30s"，一个 `Map<targetId, untilTick>` 足够（api-facts 17.7-2/19.x 已落地） | 需降级（够用，别搬） | 自建 `BehaviorMode#tick()`；`BehaviorManager.tickAll` 每 tick 推（Trait#run 永不被调，见 §10） | [实测] |
| 原版 PathNavigation（malus flag） | `Mob#setPathfindingMalus(BlockPathTypes, float)`（避水/避火/避危险）、开门是 Goal 的一部分 | Citizens `NavigatorParameters` 可设：`baseSpeed(float)`/`speedModifier(float)`/`range(float)`/`avoidWater(boolean)`/`stationaryTicks(int)`/`updatePathRate(int)`/`fallDistance(int)`/`distanceMargin(double)`/`pathDistanceMargin(double)`/`destinationTeleportMargin(double)`/`straightLineTargetingDistance(float)`/`stuckAction(StuckAction)`/`lookAtFunction(Function)`/`entityTargetLocationMapper(Function)`/`examiner(BlockExaminer)`/`pathfinderType`/`useNewPathfinder(boolean)`（全部 [JAR] 本轮核对）。**缺：逐类型 malus（水/火/岩浆分开调）没有公开口**，只有 avoidWater 布尔；**开门**无显式 API 旗标 [推断——Citizens 自带寻路对门的处理在 BlockExaminer 内部，可用自定义 `BlockExaminer` 挂 `examiner()` 精确控制，验证途径：给 NavigatorParameters 加自定义 Examiner 实测女仆过门] | 需降级 | `net.citizensnpcs.api.ai.NavigatorParameters`（上列方法）[JAR]；`Navigator#setTarget(Location/Entity,boolean/Iterable<Vector>/Function)`、`setStraightLineTarget`、`setPaused(boolean)`、`cancelNavigation(CancelReason)` [JAR] | [JAR] |
| 实体属性 | 注册时定 `EntityAttributes`（血/攻/速/跟随距离） | Paper 属性齐全：`Attribute` 接口含 `MAX_HEALTH/FOLLOW_RANGE/MOVEMENT_SPEED/FLYING_SPEED/ATTACK_DAMAGE/ATTACK_SPEED/SCALE/STEP_HEIGHT` 等 static 字段 [JAR]；`AttributeInstance#setBaseValue/addModifier/addTransientModifier` [JAR]。女仆 40 血走 `MAX_HEALTH`（api-facts 21.3 实测） | 可直接做 | `org.bukkit.attribute.Attribute` / `AttributeInstance` [JAR] | [JAR][实测] |
| 碰撞箱 | 注册时 `EntityDimensions`（TLM 女仆 0.4×1.3 类） | **不可改每实体碰撞箱尺寸**（Bukkit 无此 API）。绕法三层：① `LivingEntity#setCollidable(boolean)` [JAR] 关实体间碰撞；② BM 自有 `Tracker#createHitBox`/`EntityTracker#createHitBox(HitBoxListener, Predicate<RenderedBone>)` [JAR]（按骨骼建交互箱）；③ `Interaction` 实体 `setInteractionWidth/Height(float)` + `setResponsive(boolean)` [JAR] 自定义点击热区 | 需降级（能像，不能完全一样） | 上列 [JAR] | [JAR] |
| 年龄/模型缩放 | 幼体 `baby` 分支 + `refreshDimensions` 缩放 | `Attribute.SCALE` [JAR] 可缩实体本身；BetterModel 侧 `Tracker#scaler()`/`RenderedBone#scale(FloatSupplier)` [JAR] 可缩模型（互相独立，模型缩放推荐走 BM） | 可直接做 | `Attribute.SCALE`；`kr.toxicity.model.api.bone.RenderedBone#scale` [JAR] | [JAR] |
| 生物 AI 开关 | `setNoAi` / GoalSelector 直改 | `Mob#setAware(boolean)`（关掉原版目标选择，仅留移动）[JAR]；`LivingEntity#setAI(boolean)` [JAR]。女仆是玩家型 NPC 本来就没 mob AI，不适用，仅备查 | 可直接做（备查） | `org.bukkit.entity.Mob#setAware` [JAR] | [JAR] |

### 1.2 结论（1 节）
- **实体壳**：TLM 的"自有实体"在纯插件侧唯一等价物就是 Citizens NPC，已走通且不可换（api-facts 3.1/10.x）。玩家型 NPC 的全部怪癖见 §10。
- **AI 体系**：不搬 GoalSelector/Brain。CatMaid 的单模式调度器覆盖了 TLM 女仆全部行为复杂度；黑板机制对"女仆"这种目标简单的实体是过度设计（与 agent.md 3.4 反过度工程一致）。
- **导航**：Navigator 能力面比原版 PathNavigation 窄（缺 malus 粒度），但 `avoidWater`/`stuckAction`/`entityTargetLocationMapper`/自定义 `BlockExaminer` 可补关键场景；需要逐生物 malus 时上报冲突（agent.md 9）而不是打洞。

---

## 2. 事件映射表（重点交付物）

Forge 事件 → Bukkit/Paper 等价物。**语义差异栏写"触发时机 / 可取消性 / 能否改结果"。**

| Forge 事件（1.20.1） | Bukkit/Paper 等价 | 语义差异 | 可行性 | 关键 API | 证据 |
|---|---|---|---|---|---|
| `TickEvent.ServerTickEvent`（START/END 两相） | `com.destroystokyo.paper.event.server.ServerTickStartEvent` / `ServerTickEndEvent`（注意包名是 `com.destroystokyo.paper`，`org.bukkit.event.server` 下**不存在** [JAR]） | Paper 只有 START/END，无 Forge 的 `haveTime` 等上下文；无每世界 tick 事件（Forge `TickEvent.LevelTickEvent` 无等价，需在 ServerTick 内自行按世界分桶） | 可直接做 | 两事件类 [JAR] | [JAR] |
| `TickEvent.PlayerTickEvent` | **无每玩家 tick 事件**。绕法：全局 ServerTickStartEvent 遍历 `Bukkit.getOnlinePlayers()`（须错峰节流，agent.md 7.1）；女仆场景实际不需要玩家 tick | — | 变通 | `ServerTickStartEvent` [JAR] | [JAR] |
| `PlayerInteractEntityEvent` / `EntityInteract`（MobInteract） | 玩家右键：`org.bukkit.event.player.PlayerInteractEntityEvent(Player, Entity, EquipmentSlot)`、`PlayerInteractAtEntityEvent(Player, Entity, Vector, EquipmentSlot)` [JAR]；**NPC 右键会被 Citizens 先拦成 `NPCRightClickEvent`**（有 `setDelayedCancellation`，api-facts 3.2）[实测] | Bukkit 版只在**玩家**手上触发；Forge `MobInteractEvent` 对任意实体——纯插件侧"NPC 右键别的实体"没有事件，但 CatMaid 女仆的交互全部由插件自驱，不需要 | 可直接做（玩家侧） | 上列 + `net.citizensnpcs.api.event.NPCRightClickEvent#getClicker()` [JAR 3.2] | [JAR][实测] |
| `LivingAttackEvent`（伤害前） | 无独立前段事件 | Forge 三段链 Attack(可取消)→Hurt(可改量)→Damage(结算后)；Bukkit 只有一个 `EntityDamageEvent`（结算前、可取消、可改量）——**一次顶三段**。需要"伤害前拦截"就取消 EntityDamageEvent | 可直接做（单点化） | `EntityDamageEvent implements Cancellable` [JAR] | [JAR] |
| `LivingHurtEvent`（改伤害量） | `EntityDamageEvent#setDamage(double)`、`setDamage(DamageModifier, double)` + `getOriginalDamage(DamageModifier)`/`isApplicable(DamageModifier)`（ATTACK_COOLDOWN/ARMOR/CRIT 等 modifier 全在）[JAR] | Bukkit 能精确改每个 modifier 的量，甚至比 Forge 的 LivingHurtEvent 更细；`getFinalDamage()` 终值只读 [JAR] | 可直接做 | 上列 [JAR] | [JAR] |
| `LivingDamageEvent.Post`（结算后） | **无等价**（Bukkit 事件全部发生在最终结算写入血量之前）。需要"结算后"监听血量变化：无 `EntityHealthChangeEvent`（本轮 jar 清单确认不存在 [JAR]）；绕法：`EntityDamageEvent` 里按 `health - getFinalDamage()` 预判（CatMaid 死亡兜底正是这么做的，api-facts 18.3-2）或下一 tick 比对血量 | 时序差：CatMaid 的死亡判定发生在原版死亡结算之前 [实测 18.3-2]，不要删 | 需降级（已有兜底模式） | `Damageable#getHealth()` [JAR] | [JAR][实测] |
| `LivingHurtEvent` 改攻击者/来源 | **`EntityDamageByEntityEvent#getDamager()` 只读，无 setDamager** [JAR]——不能换 attacker。绕法：`EntityDamageEvent#setCancelled(true)` + 重新打 `Damageable#damage(double, DamageSource)`，`DamageSource` 用 `org.bukkit.damage.DamageSource.builder(DamageType)` [JAR] 造（`getCausingEntity()/getDirectEntity()/isIndirect()` 可追溯 [JAR]）——"嫁祸/自定义归因"在 Paper API 内是合法可行的 | — | 可直接做（取消+重打） | `DamageSource.builder(org.bukkit.damage.DamageType)`；`Damageable#damage(double, DamageSource)` [JAR] | [JAR] |
| `LivingDeathEvent` | `org.bukkit.event.entity.EntityDeathEvent`（**且是 Cancellable**：取消即不死；Paper 还给了 `setReviveHealth(double)` [JAR]，比 Forge 的 LivingDeathEvent 能力更强）+ `PlayerDeathEvent` [JAR 清单] | **玩家型 NPC 死亡到底派发哪个未实测**（pending §3 登记项）；CatMaid 用 `EntityDamageEvent` 预判兜底 + 双事件幂等 [实测 18.3-2] | 可直接做（保留兜底） | `EntityDeathEvent#getDrops()/setDroppedExp(int)/setReviveHealth` [JAR] | [JAR][实测] |
| `EntityJoinLevelEvent` | `com.destroystokyo.paper.event.entity.EntityAddToWorldEvent` [JAR]（逐实体）+ `org.bukkit.event.world.EntitiesLoadEvent`（区块实体批量载入 [JAR]） | Forge 的 join 对载入中每实体触发；Bukkit 侧区块批量载入走 EntitiesLoadEvent 一次给 `List<Entity>`，逐实体补 EntityAddToWorldEvent | 可直接做 | 上列 [JAR] | [JAR] |
| `EntityLeaveLevelEvent` | `EntityRemoveFromWorldEvent` [JAR] + `org.bukkit.event.entity.EntityRemoveEvent`（带 `getCause()`：DEATH/UNLOAD/PLUGIN 等）[JAR] | Bukkit 的 EntityRemoveEvent 多一个"原因"枚举，能区分 despawn 与卸载 | 可直接做 | 上列 [JAR] | [JAR] |
| `EntityMountEvent` / `EntityDismountEvent` | **同名存在且可取消**：`org.bukkit.event.entity.EntityMountEvent(Entity, Entity)#getMount()` [JAR]；`EntityDismountEvent`（非骑乘者的下鞍不可取消：`isCancellable()`）[JAR] | 语义一致 | 可直接做 | 上列 + `Entity#addPassenger/getPassengers` [JAR] | [JAR] |
| `UseBlockEvent` / `UseItemOnBlockEvent` / `ItemUseFirst` | 玩家侧：`PlayerInteractEvent`（含 Action.LEFT/RIGHT、`getHand()`、可取消、可改用物）[JAR 清单]；**非玩家实体**：`org.bukkit.event.entity.EntityInteractEvent(Entity, Block)` [JAR] 但只覆盖压力板/绊线类"实体踩上"交互，没有"实体用物品点方块" | CatMaid 女仆的"用物品"全是插件代码直接调，不需要事件 | 可直接做（玩家侧） | 上列 [JAR] | [JAR] |
| `BlockEvent.BreakEvent/PlaceEvent` | `BlockBreakEvent` / `BlockPlaceEvent`（语义一致，可取消） | — | 可直接做 | Bukkit 标准 | [文档] |
| `EntityTravelToDimensionEvent` | 玩家：**`PlayerChangedWorldEvent(Player, World from)`** [JAR]（`PlayerChangedDimensionEvent` 不存在 [JAR]）；非玩家实体：`EntityPortalEvent`（可改 `setTo`、`setSearchRadius`）[JAR] | Forge 按维度、Bukkit 按世界——等价；**女仆跨世界跟随无事件可用**（NPC 非玩家），CatMaid 用 7 秒延迟传送已实测通过 [api-facts 17.6] | 可直接做（玩家侧）/已绕（女仆侧） | 上列 [JAR] | [JAR][实测] |
| `ProjectileHitEvent` | 同名：`getHitEntity()/getHitBlock()/getHitBlockFace()`、可取消 [JAR] | 语义一致；CatMaid 战斗模式的箭走 `World#spawnArrow` + 此事件判命中 | 可直接做 | [JAR] | [JAR][实测 21.3] |
| `LivingSetAttackTargetEvent` | `org.bukkit.event.entity.EntityTargetLivingEntityEvent` / `EntityTargetEvent`（**`setTarget(Entity)` 可改目标** [JAR]，比 Forge 强——Forge 该事件目标只读） | — | 可直接做 | [JAR] | [JAR] |
| `MobSpawnEvent` / `FinalizeSpawnEvent` | `CreatureSpawnEvent`（含 SpawnReason）/ `SpawnerSpawnEvent` | — | 可直接做 | Bukkit 标准 | [文档] |
| `EntityItemPickupEvent` | `org.bukkit.event.entity.EntityPickupItemEvent`（可取消）[JAR 清单] | — | 可直接做 | [JAR] | [JAR] |
| `LivingEquipmentChangeEvent` | 玩家：`com.destroystokyo.paper.event.player.PlayerArmorChangeEvent` [JAR 清单]（仅盔甲槽）；**NPC/实体装备变更无事件**。CatMaid 用 2 tick 错峰轮询比对装备（api-facts 24.4-2） | — | 需降级（轮询替代） | [JAR] | [JAR][实测] |
| `PlayerLoggedInEvent/LoggedOutEvent/RespawnEvent/Clone` | `PlayerJoinEvent`/`PlayerQuitEvent`/`PlayerRespawnEvent`（Clone 用于死亡保 NBT——Bukkit 背包原版保留，不需要） | — | 可直接做 | Bukkit 标准 | [文档] |
| TLM 自定义事件 `SlabClickEvent`（右键女仆收放魂符） | CatMaid 侧在 `NPCRightClickEvent`/`PlayerInteractEntityEvent` 里判 PDC 实现同语义；若要对外广播，自定义 Bukkit 事件类是公开做法（CatMaid 已有 MaidSummonEvent 等） | — | 可直接做 | blueprint 0.5 event 包 [文档] | [实测] |
| `EntityEvent` 携带 capability 数据 | Bukkit 事件无 capability；靠 PDC/自持数据按实体 UUID 查 | — | 已架构 | PDC [JAR] | [JAR] |

**本节关键裁决**：
1. Bukkit 伤害事件是"单点大事件"：**可取消 + 可逐 modifier 改量 + 可造任意 `DamageSource`**，覆盖 Forge 三段链的大部分需求；缺的是"结算后"钩子——CatMaid 的预判兜底（api-facts 18.3-2）就是正确绕法，**不要动**。
2. 改 attacker 唯一路径 = 取消 + `damage(amount, DamageSource.builder(...))` 重打 [JAR]。
3. `PlayerChangedDimensionEvent`、`org.bukkit.event.server.ServerTickStartEvent`、`LivingEntity#getCooledAttackStrength`、`EntityHealthChangeEvent` 这四个"想当然"的名字**均不存在** [JAR]——写代码前按本表查。

---

## 3. 渲染 / 动画

### 3.1 能力映射表

| 能力 | GeckoLib（TLM 侧） | BetterModel / Paper 原生 | 可行性 | 关键 API | 证据 |
|---|---|---|---|---|---|
| bone 级关键帧动画 | GeckoLib `AnimationController`，`maid.animation.json` 97 个动作（api-facts 14.4-3） | BM `Tracker#animate(String)`/`animate(String, AnimationModifier)`/`animate(BlueprintAnimation, AnimationModifier)`/`animate(TrackerAnimation<?>, Runnable)` 共 4 类重载；`stopAnimation(String)`；`replace(String, String, AnimationModifier)` 动画替换 [JAR]；`AnimationModifier`（record：`builder()/type(LOOP|PLAY_ONCE|HOLD_ON_LAST)/speed/priority/override/predicate`）[JAR] | 可直接做（已落地：api-facts 17.5/17.9） | `kr.toxicity.model.api.tracker.Tracker` 上列 [JAR] | [JAR][实测] |
| molang 表达式 | `"0.4*ysm.head_yaw"` 类运行时表达式 | **BM 无 molang 运行时**；转换器直接跳过 molang 关键帧（api-facts 14.4-3）。补偿： bone 级旋转修饰器 `RenderedBone#addLocalRotModifier(Predicate, Function<Quaternionf,Quaternionf>)`/`addGlobalRotModifier`/`addPositionModifier(Predicate, Function<Vector3f,Vector3f>)` [JAR] 可以**代码驱动**实现同类动态（如视线跟随），每 tick 供给 | 需降级（代码驱动替代表达式） | 上列 [JAR] | [JAR][实测] |
| item 挂骨 / itemStack 渲染 | GeckoLib 骨骼挂 `ItemStack` | `RenderedBone#itemStack(Predicate<RenderedBone>, TransformedItemStack)`、`TransformedItemStack.of(new BukkitItemStack(...))`/`empty()` [JAR]——**前提是骨有渲染载体**：定位骨 `getDisplay()` == null，挂了也白挂 [实测 24.3-1]；`worldPosition()/worldRotation()` 是模型局部值不是世界坐标 [实测 24.3-2] | 可直接做（模型需加专用手持骨） | 上列 + `Tracker#bones()`/`bone(String)` [JAR] | [JAR][实测] |
| 独立展示实体族 | （GeckoLib 不需要） | Paper 四件套全齐 [JAR]：`Display`（`setTransformation(Transformation)`、`setTransformationMatrix(Matrix4f)`、`setInterpolationDuration/Delay(int)`、`setTeleportDuration(int)`、`setBillboard(FIXED/CENTER/VERTICAL/HORIZONTAL)`、`setBrightness(Display$Brightness)`、`setViewRange(float)`、`setShadowRadius/Strength(float)`、`setDisplayWidth/Height(float)`、`setGlowColorOverride(Color)`）；`TextDisplay`（`text(Component)`、`setTextOpacity(byte)`、`setSeeThrough`、`setBackgroundColor`、`setAlignment`）；`ItemDisplay`（`setItemStack`、`setItemDisplayTransform(THIRDPERSON_RIGHTHAND 等)`）；`BlockDisplay`。`Transformation(Vector3f translation, Quaternionf leftRot, Vector3f scale, Quaternionf rightRot)` [JAR] | 可直接做（名牌/手持已用） | 上列 [JAR]；既有落地：NameTagManager（api-facts 16.9）、HandItemDisplays（api-facts 24.3-4） | [JAR][实测] |
| 骑乘姿态 | TLM 女仆可被骑乘（动画骑姿） | 服务端真骑乘：`Entity#addPassenger(Entity)` + `EntityMountEvent` [JAR]；模型姿态配合 `animate("sit")`。BM 还有 `Tracker#rotation()/rotator(ModelRotator)` 控制整体转向 [JAR]。已知风险：玩家型 NPC 骑玩家/被骑的观感由客户端插值决定，`setTeleportDuration` 只对 display 实体生效 | 需降级（能做像） | 上列 [JAR] | [JAR] |
| 手持物品可见 | 骨骼挂物品 | 当前模型无专用手持骨 → 独立 `ItemDisplay` + 轮询跟随（已实现，api-facts 24.3-4/24.4-1） | 已落地 | `ItemDisplay#setItemDisplayTransform` [JAR] | [实测] |
| 座椅交互（右键就座） | 实体上有骑乘位 | `Interaction` 实体：`setInteractionWidth/Height(float)`（热区大小）、`setResponsive(boolean)`（响应右键 → 触发 `PlayerInteractEntityEvent`）、`getLastAttack()/getLastInteraction(): PreviousInteraction` [JAR]；叠坐 = 对 Interaction 反复 `addPassenger` [JAR] | 可直接做（叠坐需小心下坐位置） | 上列 [JAR] | [JAR] |
| 视锥/可见性 | 客户端渲染距离 | `Display#setViewRange(float)`（默认 1.0 = 64 格倍率）[JAR]；亮度 `setBrightness` [JAR]；玩家级整体隐藏走 `Player#hideEntity(Plugin, Entity)` [JAR]（api-facts 28 全套实测）或 BM `Tracker#hide/show(PlatformPlayer)`/`perPlayerTick` [JAR] | 可直接做 | 上列 [JAR] | [JAR][实测] |
| 受击反馈 | GeckoLib hurt 动画 + 红闪 | `EntityTracker#damageTint()`/`damageTintValue(int)`/`cancelDamageTint()` [JAR]（BM 原生受击红闪）；`LivingEntity#playHurtAnimation(float)` [JAR]；受击动画 `animate("attacked")` [实测 17.9 动画名表] | 可直接做 | 上列 [JAR] | [JAR][实测] |
| hitbox / 命名 | 原版碰撞箱 + CustomNameTag | BM：`Tracker#createHitBox`/`listenHitBox(Class<T extends HitBoxEvent>, Consumer)` [JAR]（骨骼级命中事件）、`Tracker#createNametag(BonePredicate, BiConsumer<RenderedBone, ModelNametag>)` [JAR]；CatMaid 实际用独立 TextDisplay 名牌（api-facts 16.9，模型接管后原版名牌必消失） | 已落地 | 上列 [JAR] | [JAR][实测] |

### 3.2 结论（3 节）
- BetterModel 的动画面（逐帧服务端驱动、bone 修饰器、per-player tick）与 GeckoLib 的能力**等价度高**，唯二缺口是 molang 与"定位骨无载体"——前者代码驱动补，后者模型侧加骨补（api-facts 24.3-1 已给出路径）。
- Paper 原生 display 族把"GUI 之外的悬浮表现"（名牌、手持、特效、座椅热区）全部补齐，能力面不弱于 Forge 客户端自绘的*世界内*部分；**世界外**的部分（HUD/屏幕）见 §9，纯插件永远做不到。

---

## 4. 物品

| 能力 | Forge/TLM 侧 | Paper 侧等价/替代 | 可行性 | 关键 API | 证据 |
|---|---|---|---|---|---|
| 自定义物品注册 | `Registry.register(ForgeRegistries.ITEMS, ...)`，`InitItems` | 插件不能注册 Material。两条既有路线：vanilla = 原版材质 + PDC 状态（魂符三态，api-facts 25.3）；craftengine = `CraftEngineItems.byId(String) → BukkitItemDefinition#buildBukkitItem()`，`getCustomItemId(ItemStack)` 比对 Key——CE 与 Bukkit 插件 classloader 隔离，**必须反射** [JAR 24.3-5] | 可直接做（已落地） | 上列 [JAR] | [JAR][实测] |
| `Item#use/useOn`（右键逻辑） | `ItemSmartSlab#useOn`（只认 `Direction.UP` 顶面 + 两格碰撞校验） | `PlayerInteractEvent`（判 `Action.RIGHT_CLICK_BLOCK/AIR`、`getHand()`、可取消）+ `PlayerInteractEntityEvent`；CatMaid 有意差异：额外允许右键空气放脚下（api-facts 25.3） | 可直接做（已落地） | Bukkit 标准事件 | [实测] |
| 物品冷却 | `player.getCooldowns().addCooldown(item, 20)`（按物品实例） | `HumanEntity#setCooldown(Material, int)` / **`setCooldown(ItemStack, int)`** / **`setCooldown(Key, int)`** 三重载 [JAR]——Key 重载意味着**可给 CE 自定义物品挂独立冷却**（与魂符"按材质共享冷却"的差异可在 CE 路线消除，api-facts 25.3-2）；客户端有灰条 | 可直接做 | `org.bukkit.entity.HumanEntity#setCooldown` [JAR] | [JAR][实测] |
| `isFoil`（附魔光） | `isFoil()` 按 Type | `ItemMeta#setEnchantmentGlintOverride(Boolean)` [JAR 16.8]（比藏附魔干净，CatMaid 已用） | 可直接做 | [JAR] | [JAR][实测] |
| `canFitInsideContainerItems`（禁入容器） | TLM `HAS_MAID` 魂符返回 false | **Bukkit 无对应钩子**（api-facts 25.2-7/25.3-3 已记录未实现）。绕法：`InventoryClickEvent`/`InventoryMoveItemEvent` 里按 PDC 拦截 + `PrepareInventoryResultEvent`（存在 [JAR 清单]）拦合成——成本高收益低，维持现状即可 | 不可行（可拦截部分场景） | `InventoryClickEvent`/`PrepareInventoryResultEvent` [JAR] | [JAR][实测] |
| tooltip / 名称 | `getDescriptionId` 客户端翻译 | 服务端拿不到中文材质名；`Material#translationKey()` + `Component.translatable(...)` 让客户端按语言渲染（api-facts 16.8）[JAR]；自定义名/lore 直接 `Component` | 可直接做 | [JAR] | [JAR][实测] |
| 防复制 | `stacksTo(1)` + 不可入容器 + 掉落无敌发光（§7 pending） | `Item#setUnlimitedLifetime(boolean)`/`setInvulnerable`/`setGlowing`/`setCanPlayerPickup`/`setOwner(UUID)`/`setWillAge`/`setHealth(int)` 全在 [JAR]（铃铛已用，api-facts 18.3-3）；不可堆叠由"单件发放 + PDC 绑定"约定保证；**任何能复制 NBT 的第三方插件仍可克隆凭证**（pending §6 已登记） | 需降级（已知边界） | `org.bukkit.entity.Item` 上列 [JAR] | [JAR][实测] |
| 食物属性 / 喂食 | Forge FoodProperties | CatMaid 喂食走自判（config 白名单），不需要物品 Food 组件；Paper 有 datacomponent API（`Entity` 实现了 `DataComponentView` [JAR]，ItemStack 编辑组件在 Paper datacomponent 包）[推断——未逐一核对，需要时 javap `io.papermc.paper.datacomponent.item.FoodProperties`] | 可直接做（既有实现） | PDC + 自判 | [实测] |

---

## 5. GUI

| 能力 | Forge `AbstractContainerMenu` | Bukkit 侧 | 可行性 | 关键 API | 证据 |
|---|---|---|---|---|---|
| 服务端槽位逻辑 + 同步 | Menu 自动双向同步槽位 | `Bukkit.createInventory(InventoryHolder, int, Component)` + holder 身份识别（CatMaid `MaidMenuHolder`）；点击 `InventoryClickEvent`（判 `getClickedInventory().getHolder()`）+ `InventoryDragEvent#getRawSlots()` 判拖拽越界（api-facts 16.8）| 可直接做（已落地 5 GUI） | 上列 [JAR][实测] | [JAR][实测] |
| 槽位权限 / shift 语义 | Menu 的 `mayPlace/mayPickup/quickMoveStack` | 全部手写：`setCancelled` + `ClickType`/`InventoryAction` 分支（`COLLECT_TO_CURSOR` 在 `InventoryAction` 不在 `ClickType`，api-facts 16.8 [JAR]）；护甲 shift 用 `Material#getEquipmentSlot()` 路由 [JAR 16.8] | 可直接做（已落地） | 上列 [JAR] | [JAR][实测] |
| 客户端自绘 Screen / 动态槽位占用 / 马箱背包 | Forge 可注册任意 Screen、任意槽位布局、马箱式扩展背包 | **做不到**（chest GUI 固定 6 行 9 列网格、标题为 Component、无像素级自绘）。惯用绕法：固定布局 + 图标占位（CatMaid `GuiLayout` 静态常量已如此）；"动态容量"用固定行数 + 灰玻璃占位表达 | 不可行 → 伪装 | `GuiLayout`（blueprint 0.5）[文档] | [实测] |
| 非玩家实体打开容器 | NPC 也能 open menu | 不需要（女仆背包是虚拟 36 槽 + GUI 展示） | — | `MaidInventory`（blueprint 0.7）[文档] | [实测] |

---

## 6. 持久化

| 能力 | Forge/TLM 侧 | Paper 侧 | 可行性 | 关键 API | 证据 |
|---|---|---|---|---|---|
| 实体随档 NBT | `EntityMaid` NBT 含 Owner/头像/背包 | 无实体 NBT API → CatMaid 全自持：SQLite `maids/maid_items/maid_stats/player_prefs`（blueprint 0.6）+ `ItemStack#serializeAsBytes`（gzip NBT，api-facts 21.3-5 已解析字节格式）[JAR]；NPC 模型名由 Citizens `Trait#save(DataKey)` 保存、重启自恢复 [实测 3.1] | 可直接做（已落地） | 上列 [JAR][实测] | [JAR][实测] |
| 全局台账（`SavedData`） | `MaidWorldData`：主人→女仆/墓碑 id+坐标，供定位 | SQLite 即台账（maids 表含 world/x/y/z）；**注意数据归属**：主人离线时 `Bukkit.getOfflinePlayer(uuid)` 仍可查名（api-facts 16.9 名牌已在用） | 可直接做（已落地） | `MaidRepository`（blueprint 0.5）[文档] | [实测] |
| 玩家离线时数据 | Forge capability 随 PlayerProvider 保存 | `player_prefs` 表 + `OfflinePlayer`；SQLite 走单线程 executor 异步（api-facts 15.x，agent.md 5.7） | 可直接做（已落地） | 上列 | [实测] |
| 每玩家额度（`MaidNumCapability#canAdd`） | Forge Capability 挂玩家 | 自有计数（召唤/补发口校验，#7 修复已按此口径） | 可直接做（已落地） | — | [实测] |

---

## 7. 声音 / 特效

| 能力 | Forge/TLM 侧 | Paper 侧 | 可行性 | 关键 API | 证据 |
|---|---|---|---|---|---|
| `SoundEvent` 注册 | 自定义声音事件（语音包） | **不能注册新 `Sound`**：`org.bukkit.Sound` 是 `OldEnum<Keyed>` 接口 + `Registry.SOUNDS` 只读 [JAR]。但 `Player#playSound(Location, String, ...)` / Adventure `Sound.sound(Key)` 可播**任意命名空间 key** [JAR]——资源包 `sounds.json` 定义自定义 .ogg 后，`playSound("catmaid:maid_voice_xxx")` 即可播放自定义语音 [推断：客户端按资源包 sounds.json 解析 key 属原版机制，验证途径=测试服资源包加一条 ogg 实测] | 需降级 → 可伪装成功 | `Sound` 接口 / `Registry.SOUNDS` [JAR]；`Player#playSound(String)` 重载 [JAR] | [JAR][推断] |
| 定向/衰减播放 | `level.playSound(null, pos, event, ...)` | `Player#playSound(Entity, Sound, SoundCategory, float, float)`（跟随实体位置的衰减，含 seed 重载）[JAR]；`World#playSound(Entity, ...)` [JAR]；`Entity` 实现了 Adventure `Sound$Emitter` [JAR]，可用 Adventure emitter 语法 | 可直接做 | 上列 [JAR]（World 的 playSound/spawnParticle 重载合计 29 个 [JAR]） | [JAR] |
| 自定义粒子 | Forge 可注册 ParticleType + 客户端渲染 | **不可行**：`Particle` 是 enum、`Registry.PARTICLE_TYPE` 只读 [JAR]。伪装：① 原版粒子组合拼形状（dust/sweep_attack/heart 等 + `DustOptions` 颜色）；② 把"特效"做成 BM 模型动画或 `BlockDisplay` 变形（transform 插值可做飞行动画） | 不可行 → 伪装 | `World#spawnParticle(...)` 29 重载 [JAR]；`Display#setTransformation` + 插值 [JAR] | [JAR] |
| 每实体音量/随机音高 | Forge SoundProperties | `playSound(..., float volume, float pitch)` + `seed` 重载 [JAR]；死亡音效还可在 `EntityDeathEvent#setDeathSound/setDeathSoundVolume/setDeathSoundPitch` [JAR] 直接改 | 可直接做 | 上列 [JAR] | [JAR] |

---

## 8. 内容分发（资源包 / 模型包 / 语音包）

> 本项目已有定案链路（api-facts §4），**不另起炉灶**，此处只给 TLM 侧对照。

| 能力 | Forge/TLM 侧 | CatMaid 既有链路 | 结论 | 证据 |
|---|---|---|---|---|
| 资源包强制下发 | Forge 走 mod jar 自带 assets，无"弹包"概念 | 双 provider 二选一（4.0b）：vanilla = BM 包独占原版 `server.properties` 三键强制下发；craftengine = BM 包并入 CE `merge-external-zip-files`、CE send_pack 统一下发（4.3 修复后 5 项验收全过） | 已闭环 | api-facts 4.0/4.0b/4.3 | [实测] |
| 更新姿势 | mod 更新 = 换 jar | 无 sha1 时客户端不重下 → 换文件名 + 改 `resource-pack`+`resource-pack-sha1`+重启（4.2）；CE 路线 `/ce reload pack` 即新包（4.3 验收 5） | 已闭环 | api-facts 4.2/4.3 | [实测] |
| 开放式模型包（`tlm_custom_pack` 目录扫描 292 模型） | mod 运行时扫描目录 | CatMaid 对应物 = `plugins/BetterModel/models/*.bbmodel` + `/bm reload`；注意 BM 3.6.0 的 build.zip **只在启动时生成**，改模型要重启（14.4-4） | 等价（运维姿势不同） | api-facts 14.4-4/27 | [实测] |
| 语音包（自定义 .ogg） | 资源包 sounds.json + 语音事件 | 同 §7 第一行：包内放 ogg + sounds.json，代码 `playSound("catmaid:...")` [推断] | 可伪装 | [推断] |
| 格式桥接 | 基岩几何 JSON + GeckoLib 动画 JSON | `tools/tlm_to_bbmodel.py` 纯格式搬运 + 7 动作导出（14.2/14.4），已批量转换 27 个模型（27 节） | 已闭环 | api-facts 14 | [实测] |
| 下发验证手段 | — | 客户端 `downloads/log.json` + 托管包字节数比对；CE 静默跳过三症状（4.3） | 已闭环 | api-facts 4.3 | [实测] |

---

## 9. 客户端能力鸿沟（Forge 能做、Paper 纯插件**根本做不到**）

列清单供蓝图提前"死心或找伪装"，不展开方案：

| # | 能力 | 伪装/降级建议（一句话） |
|---|---|---|
| 1 | 客户端渲染钩子（RenderType/Layer、模型像素级自绘） | 一切视觉走服务端实体（BM 骨骼 display + display 族），本就够用 |
| 2 | 自定义 HUD（血条/好感度面板常驻屏幕） | bossbar / actionbar / `Player#sendActionBar` [JAR] / 头顶名牌 / `sendBlockChange` 伪方块面板 |
| 3 | 自定义 Screen（真 GUI 自绘、动画） | chest GUI + 图标 + 标题 Component（已这样做） |
| 4 | 客户端预测/真实体物理同步（无延迟跟随） | `setTeleportDuration` 插值 + BM 逐帧包已把观感做到可用（api-facts 27.5） |
| 5 | 自定义粒子渲染器 | 原版粒子拼形 + BM 动画 |
| 6 | molang 客户端表达式动画 | bone 修饰器代码驱动（§3） |
| 7 | 与 TLM 客户端/服务端互通 | 永不可能，也不该可能 |
| 8 | 实体 NBT 原生同步（拴绳牵走等原版行为） | 自持数据 + PDC，行为全自建 |
| 9 | 新 SoundEvent/ParticleType/Material/EntityType 注册（Registry 全部只读 [JAR]） | key 播放（声音）/PDC 物品/原版实体 + BM（实体） |

---

## 10. 玩家型 NPC 特有坑位核对（成因解释 + 是否有更优雅替代）

| 坑位 | 成因（API 层面） | 更优雅的公开 API 替代？ | 证据 |
|---|---|---|---|
| `Trait#run()` 永不被调用 | Citizens `PlayerUpdateTask` 对带 `PacketNPC` trait 的 NPC 才走 `AbstractNPC#update()`（trait run + navigator 都在其中）；玩家型 NPC 走原版 `playerTicker`，整条 Citizens tick 链被跳过（api-facts 17.2 字节码证据） | 无。官方替代是 PacketNPC 模式（协议假实体），会与 BM 实体渲染打架（api-facts 17.2 采用方案）。**维持插件自驱调度器** | [实测][JAR] |
| 无玩家在线时 NPC 被丢弃 / 异步载入 | Citizens 自动 despawn 策略 + `NPCRegistry` 异步加载（api-facts 10.1、18.3-4） | 无 API 关闭；绕法 = 启动恢复轮询常驻兜底（Issue #1 修复 fc1d0bd）+ 测试时先 `/npc spawn` | [实测] |
| 死亡后 Citizens 状态 | NPC 被自动 despawn（spawned=false）但注册与 saves.yml 保留；存档带 spawned 状态时重启会"复活幽灵身体"且仍被 tick（api-facts 17.8/18.3-6） | 无更优雅 API；已有三处修复（铃铛态不 tick、despawnHiddenNpcs、死亡后 2 tick 再 despawn） | [实测] |
| attack 强度冷却 | `LivingEntity` 上**没有** `getCooledAttackStrength` [JAR 本轮确认]；玩家型 NPC 走 `Player#getAttackCooldown()`（`HumanEntity` 上 [JAR]）| 已是公开 API 正解（api-facts 21.3-1 的 0.9 闸门） | [JAR][实测] |
| `hideEntity` 不跨 relog | Paper 的 hide 状态随玩家连接销毁，重进后全失效（api-facts 28.4） | 无状态化替代不存在；BM `Tracker#hide/show(PlatformPlayer)` [JAR] 只管骨骼 display，名牌与底层实体仍需 `hideEntity`。**维持四挂点 + quit 清对账集**（教训 28.4：对账集生命周期 ≤ 连接态） | [实测][JAR] |
| Paper `reloadConfig()` 换对象 | `JavaPlugin#reloadConfig()` 内部重新 `loadConfig()` 生成**新 `YamlConfiguration`**，旧引用全部失效 [文档：Bukkit `JavaPlugin` 源码] | 无 API 改变此行为；更优雅的公开做法是 reload 后统一重新分发 config 给各 Manager（CatMaid `/catmaid admin reload` 已是此模式）或自管 `loadConfiguration(File)` [文档] | [文档] |
| 原版命令选不中 NPC / 不在实体索引 | Citizens 玩家型 NPC 不进 level 实体索引，`/damage`/`/data`/scoreboard 全失效（api-facts 18.3-1/21.3-4） | 无公开 API；操作 NPC 只能走 Citizens 命令或插件内 `NPCRegistry#getById` [实测] | [实测] |
| 控制台/RCON 看不见 NPC | 同上 + Citizens remove-from-list（项目记忆 2026-11-17） | 同上 | [实测] |

---

## 11. 按移植性价比排序的结论

**差距小但致命（必须守住，别动）**
1. **事件单点化语义**：Bukkit 伤害事件一次顶 Forge 三段，但发生在结算前——CatMaid 死亡兜底（api-facts 18.3-2）正是基于此时序，删了整条死亡链不触发。
2. **玩家型 NPC 的 tick 链**：Trait#run 不被调用是 Citizens 分流设计而非 bug，任何"回到蓝图 MaidTrait.run() 委托"的重构都会翻车（api-facts 17.2）。
3. **实体无 NBT API**：一切状态必须自持（PDC/SQLite）——架构已经是这样，新功能照此办理即可，不要试图找 NBT 入口。

**差距大但影响小（放心砍/伪装）**
4. 自定义 HUD/Screen/粒子/注册表类（§9 全表）：全部有成熟伪装，蓝图设计时直接按伪装形态设计，不留"等 mod 化"的口子。
5. molang：代码驱动 bone 修饰器覆盖。

**值得搬（Paper API 其实比想象强，现成可用）**
6. `DamageSource.builder(DamageType)` + `Damageable#damage(double, DamageSource)`：自定义伤害归因/嫁祸 [JAR]。
7. `EntityDeathEvent#setReviveHealth` + Cancellable：比 Forge 更直接的"免死"钩子，可替换部分死亡兜底场景（需实测玩家型 NPC 是否派发，pending §3）[JAR]。
8. `HumanEntity#setCooldown(Key, int)`：CE 自定义物品独立冷却，消除魂符"按材质共享冷却"的体验差异 [JAR]。
9. `EntityTargetLivingEntityEvent#setTarget`：改仇恨比 Forge 还方便 [JAR]。
10. BM `RenderedBone#addLocalRotModifier/addPositionModifier`：视线跟随/头随目标这类"动态姿势"的正规入口 [JAR]。
11. `Interaction` 实体 + `addPassenger`：座椅交互/叠坐的正规做法 [JAR]。
12. Citizens `NavigatorParameters#attackRange/attackDelayTicks/attackStrategy`：战斗导航的现成参数（当前 CombatMode 是手搓，可评估迁移）[JAR]。
13. `Tracker#createHitBox` + `listenHitBox`：骨骼级命中判定，战斗手感升级的现成入口 [JAR]。

**不值得搬（明确不搬，防过度工程）**
14. GoalSelector/Brain/MemoryBoard 黑板、Forge Capability 体系、SavedData 机制（SQLite 已覆盖）、Forge Tag 系统（Bukkit `Tag` 可用原版 tag）。

---

### 附：本轮 [JAR] 核对清单（供 api-facts 补录时引用）

paper-api 26.2.build.135-stable：
- `EntityDamageEvent`（setDamage/getOriginalDamage/isApplicable/getFinalDamage/getDamageSource）、`EntityDamageByEntityEvent`（getDamager 只读、isCritical）
- `EntityDeathEvent`（Cancellable、setReviveHealth、setDeathSound 系列）、`EntityMountEvent`/`EntityDismountEvent`、`EntityRemoveEvent`、`EntitiesLoadEvent/EntitiesUnloadEvent`、`EntityTargetLivingEntityEvent`、`EntityBreedEvent`、`ProjectileHitEvent`、`EntityPickupItemEvent`、`EntityInteractEvent`、`EntityPortalEvent`
- `com.destroystokyo.paper.event.server.ServerTickStartEvent/EndEvent` 存在；`org.bukkit.event.server.ServerTickStartEvent` 不存在
- `PlayerChangedWorldEvent` 存在；`PlayerChangedDimensionEvent` 不存在
- `EntityAddToWorldEvent/EntityRemoveFromWorldEvent`（com.destroystokyo.paper.event.entity）、`PlayerArmorChangeEvent`、`PrepareInventoryResultEvent`
- Display 族（Display/TextDisplay/ItemDisplay/BlockDisplay/Interaction/Transformation 全方法）
- `Attribute`（MAX_HEALTH/FOLLOW_RANGE/MOVEMENT_SPEED/FLYING_SPEED/ATTACK_DAMAGE/ATTACK_SPEED/SCALE/STEP_HEIGHT 等 static 字段）、`AttributeInstance`
- `Damageable#damage(double, DamageSource)`、`DamageSource.builder/getCausingEntity/isIndirect`
- `HumanEntity#setCooldown(Material/ItemStack/Key, int)`、`getAttackCooldown()`；`LivingEntity` 无 `getCooledAttackStrength`
- `LivingEntity#attack/playHurtAnimation/swingMainHand/knockback/getKiller/setLastDamage/hasLineOfSight/rayTraceEntities/getEquipment/setCollidable/setAI`
- `Entity` 接口无 NBT 读写方法；`Item#setUnlimitedLifetime/setOwner/setCanPlayerPickup/setWillAge/setHealth`
- `Sound`（interface, OldEnum<Keyed>）、`Particle`（enum）、`Registry.SOUNDS/PARTICLE_TYPE` 只读；`World#playSound(Entity,...)`、playSound/spawnParticle 合计 29 重载
- `Player#hideEntity/showEntity/playSound(...)/sendActionBar`

Citizens 2.0.44：`NavigatorParameters` 全方法面（含 attackRange/attackStrategy/avoidWater/stuckAction/examiner/entityTargetLocationMapper 等）、`Navigator`（setTarget 4 重载/setStraightLineTarget/setPaused）。

BetterModel 3.6.0 dev 541：`Tracker`（animate 4 类重载/stopAnimation/replace/perPlayerTick/hide/show/createHitBox/createNametag/bones）、`EntityTracker`（damageTint/bodyRotator/hideOption/spawnCondition）、`RenderedBone`（itemStack/addLocalRotModifier/addGlobalRotModifier/addPositionModifier/scale/tint/enchant/worldPosition/getDisplay/flatten）、`AnimationModifier`（record，builder/type/speed/priority/override）。
