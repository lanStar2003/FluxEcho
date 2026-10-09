# 开发手册 · 回响机器（养蜂 / 神秘 / 血魔法 / 猎物 / 作物 / 植物魔法）

> 改 `bees/ thaumcraft/ blood/ mobs/ crops/ mana/` 之前读这一篇。机器基类、ID、账本、NEI、配方工具的细节在 [platform.md](platform.md)。总览见 `CLAUDE.md`。
> 路径相对 `src/main/java/com/fluxecho/`。数字都是代码默认值。

## 0. 公共规则（每个模块都一样）

- **核心原则**：亲手做过一次的事，之后交给机器。机器只认真东西：样本要来自真蜂、真要素、亲手做的宝珠、亲手杀的怪、扫满的种子袋。"做过一次"的证明写进团队账本 `EchoLedger`（`remember(category, key)`）。
- **机器基类** `core/MTEEchoMachine`（GT `MTEBasicMachine` 子类）：`checkRecipe()` final → 模块关了 `idle("disabled")`，否则调子类 `work()`；机器自己算结果，不查 recipe map。辅助：`idle(why)`、`blocked()`、`start(eut, ticks)`、`circuit()`、`input(n)`、`sample()`（special slot；**放书库索引卡时解析成书库借出的样本**，见 [nexus.md](nexus.md)）、`remember(cat, key)`、`team()`。
- **状态键**：`idle("xxx")` → lang `fluxecho.status.xxx`。新状态必须加 lang（`LangTest.everyKeyTheCodeNamesExists` 会查）。
- **GUI** `core/FluxMachineGui` 176×220：金色样本槽 (9,25)、电池 (9,65)、输入输出由 `logic/SlotLayout.grid` 排、中间 `client/MachineScreen.panel` 画 `Motif` 动画（点击跳 NEI `fluxecho.machine.<key>`）、全息开关 (149,25)、自动输出 (149,43)、电路 (150,64)、底部 readout = `info()`（`encode(langKey, args…)` 用 `\u0001` 分隔）。
- **全息**：默认关；GUI 或螺丝刀（非正面、非输出面）切换；开着时每 10 tick `EchoNet.holo(..., HOLO_MACHINE=2, holoData())`，范围 `effects.hologramRange`（16）；客户端 `client/MachineHolo`。
- **世界动效**：`client/MachineFx.seen(kind,…)` 按 `Motif` 画，受 `effects.machineEffects`、`effects.effectRange` 控制。
- **Directory**：每 100 tick `Directory.report(...)`，给终端"机器"页。
- **配方**：`core/EchoRecipes.machine/shaped/shapeless` → `GTModHandler`（`NOT_REMOVABLE`），GT 拒收退回 Forge 配方，`RecipeCheck` 末尾补回。固定模式：每台机器带一件**它所重复的原版设备**（做过一次的证明）+ 一颗末影珍珠 `E`（通往通量层的那一环）。都要求"模块 enabled 且 `general.enableDefaultRecipes`"（例外：蜂的 `GeneSpliceRecipe` 只看 `bees.enabled`）。
- **NEI**：每台机器一个 GT recipe map `fluxecho.recipe.<key>`，**只给 NEI 当示例**（`disableRegisterNEI`），由 `nei/FluxRecipeHandler` 用通量风格显示；`EchoRecipeMaps.page()` 加 fake 示例，`later()` 延迟生成（猎物）；页底说明 `fluxecho.nei.note.<key>`。
- **隔离**：可选模组的类只能在自己的包里出现（`IsolationTest` 扫编译产物常量池）：`forestry/`→`bees/`、`thaumcraft/`→`thaumcraft/`、`WayofTime/`→`blood/`、`com/kuba6000/mobsinfo/`→`mobs/`、`ic2/`→`crops/`、`vazkii/botania/`→`mana/`。`FluxEcho.java` 只在 `Mods.xxx` 为真时调 `XModule.preInit/machines/postInit/loadComplete/serverStopped`；Waila 用 IMC 按**字符串**注册（类不会被提前加载）。
- **模块关掉时**：机器和物品照样注册（"item and machines are registered even when the module is switched off"），放下的方块保留，只是停机。
- **任务**：`quests/build_quests.py` 里 `echo_machine(offset)` = `gregtech:gt.blockmachines` damage 24530+offset；每条线开头拼 `ECHO_LORE`；`write_line(..., requires=[modid])` 只在对应 mod 加载时安装。

## 1. 机器 ID 表（`core/MachineId`，id = `general.firstMachineId` 24530 + offset）

| offset | id | key（lang/MTE 名 `fluxecho.<key>`）/ 类 | 名字 | 电压 | 样本槽 | 模块 |
|---|---|---|---|---|---|---|
| 0 | 24530 | `bee_imprinter` / `bees/MTEBeeImprinter` | 蜂种拓印机 | LV | ✓ | 蜂 |
| 1 | 24531 | `larva_incubator` / `bees/MTEBeeIncubator` | 幼虫培育箱 | LV | ✓ | 蜂 |
| 2 | 24532 | `essentia_echo` / `thaumcraft/MTEEssentiaEcho` | 要素回响仪 | MV | ✗ | 神秘 |
| 3 | — | （退役：Vis Charger） | **永不复用** | | | |
| 4 | 24534 | `insight_echo` / `thaumcraft/MTEInsightEcho` | 灵感回响仪 | LV | ✓ | 神秘 |
| 5 | 24535 | `crucible_echo` / `thaumcraft/MTECrucibleEcho` | 炼金回响釜 | MV | ✗ | 神秘 |
| 6 | 24536 | `blood_echo` / `blood/MTEBloodEcho` | 鲜血回响器 | MV | ✓ | 血 |
| 7 | 24537 | `mob_echo` / `mobs/MTEMobEcho` | 猎物回响器 | LV | ✓ | 猎物 |
| 8 | 24538 | `infusion_echo` / `thaumcraft/MTEInfusionEcho` | 注魔回响台 | MV | ✓ | 神秘 |
| 9 | 24539 | `seed_imprinter` / `crops/MTESeedImprinter` | 种子拓印机 | LV | ✓ | 作物 |
| 10 | 24540 | `seed_echo` / `crops/MTESeedEcho` | 种子回响箱 | LV | ✓ | 作物 |
| 11 | 24541 | `mana_echo` / `mana/MTEManaSpring` | 魔力回响泉 | 核心分阶 | ✗ | 植物魔法 |
| 12 | — | （退役：Enchant Echo） | **永不复用** | | | |
| 13 | 24543 | `gene_assembler` / `bees/MTEGeneAssembler` | 基因组合机 | LV | ✗ | 蜂 |

范围 24530–24569（40 个），下一个可用 offset 是 14。四台神秘机器都继承 `thaumcraft/MTEThaumMachine`（加了碎片额度和要素学习）。`MachineIdTest` 守唯一性和不碰 FluxDepths（24520–24529）。

## 2. 解锁规则速查

| 模块 | "亲手做过一次"的证明 | 写到哪里 |
|---|---|---|
| 蜂 | special slot 放**真实**蜂/树苗/蝴蝶做拓印 | `remember(bee/tree/butterfly, SPECIES uid)`（样本是印记或基因样本时不记） |
| 作物 | 种子袋扫描到 4 级才接受；用种子袋做样本拓印 | `remember(crop, owner:name)` |
| 猎物 | 真实玩家（非 FakePlayer）击杀，或统计里杀过 | `MobEvents.write` 中 `record(mob, entityName)` |
| 血 | 已绑定的宝珠（宝珠在祭坛里亲手做出） | 每周期 `remember(orb, modid:name)`（幂等） |
| 神秘 | 学会要素（真要素容器或碎片）+ 主人完成研究 | `AspectMemory`（作为账本 source）；注魔每次 `remember(infusion, modid:name:damage)` |
| 魔力 | 只有配方门槛（魔力池 + 发射器） | 无 |

账本类别 `codex/Categories`：`bee, tree, butterfly, aspect, mob, crop, infusion, orb`。

---

## 3. 养蜂（Forestry）`bees/`

入口 `BeeModule`：preInit 注册 `bee_imprint`、`gene_sample`；machines 注册 3 台；postInit 注册账本类别 bee/tree/butterfly、`fluxecho:gene_splice` 合成（`RecipeSorter` SHAPELESS）、`BeeCrafting`；loadComplete `BeeNei.addPages()`。

**物品**
- 蜂种印记 `fluxecho:bee_imprint`（`ItemBeeImprint`）：NBT `Genes{染色体名→allele UID}`（含 SPECIES），`Root`（**仅非蜂时写**，保证旧蜂印记不变）。显示为蜂种/树种/蝶种印记。
- 基因样本 `fluxecho:gene_sample`（`ItemGeneSample`）：`Genes`（不含 SPECIES）、`Root`。

**桥接**：`BeeImprints`（`rootOf`、`genes(member)` 取 active allele、`bee()/saplings()/butterflies()` 造原始、已分析、纯合的个体）；`GeneSamples.splice(stacks)` 返回结果或问题键 `splice_two_imprints/blank/foreign/kinds/need_samples/conflict`。

**逻辑**（`logic/`）：`Karyotype`（root 常量；电路 N = 第 N 个性状，`CIRCUIT_ENVIRONMENT=13` 只有蜂（性状 4–8），`CIRCUIT_ALL=14`；蜂 12 性状、树 12、蝴蝶 13）、`Chromosomes`（`transfer` 从不改 SPECIES）、`GeneSplice`（`extract/merge/apply/shape`）。测试 `ChromosomesTest`、`KaryotypeTest`、`GeneSpliceTest`。

**蜂种拓印机**（24530，`SCAN`，0xFFC233）：配方 `PEP/CHC/RBR`（钢板、LV 电路、LV 外壳、LV 机械臂 ×2、Forestry beealyzer）。special slot 放真蜂/树苗/花粉/蝴蝶/印记/样本（不消耗）。输入纸：电路 0 → 完整印记；电路 N → 该性状的基因样本。输入同种印记 + 电路 → 改写印记。`start(16, 100)`。
**幼虫培育箱**（24531，`COMB`）：配方 `PEP/CHC/UAU`（LV 泵 ×2、蜂箱）。special slot 放印记或真个体。蜂：电路≠2 消耗 12 蜂蜜滴 → 1 公主 + 2 雄蜂；电路 2 消耗 16 → 16 雄蜂。树：4 林业肥料 → 4 树苗。蝴蝶：8 蜂蜜滴 → 1 只。`start(24, 200)`。输出里的公主会挡住下一只。有 `echoPatterns()`（AE）。
**基因组合机**（24543，`HELIX`，无样本槽，9 进 1 出）：配方 `PEP/RHR/CBC`。9 个输入一起 `splice`，每槽扣 1。`start(16, 100)`。工作台手工版 `GeneSpliceRecipe`（动态结果）。

配置 `bees.*`：`enabled`、`imprintTicks/EuPerTick`(100/16)、`incubateTicks/EuPerTick`(200/24)、`assembleTicks/EuPerTick`(100/16)、`honeyPerPrincess`(12)、`dronesWithPrincess`(2)、`honeyPerDrones`(16)、`dronesPerBatch`(16)、`fertilizerPerSaplings`(4)、`saplingsPerBatch`(4)、`honeyPerButterfly`(8)、`butterfliesPerBatch`(1)。
任务 `echo_bees()`（requires Forestry）：lore → imprinter → first_imprint → (edit → splice → assembler) / incubator → trees。

## 4. 神秘（Thaumcraft）`thaumcraft/`

入口 `TCModule`：preInit 方块 `essentia_outlet`、`vis_pedestal`、物品 `vis_module`、Waila IMC `PedestalWaila`；machines 4 台 MTE；postInit `EchoLedger.source("aspect", AspectMemory::tags)`（要素不复制进账本，直接读）、类别 aspect/infusion、`TCCrafting`；loadComplete `TCNei.pages()`；serverStopped `AspectMemory.reset()`；客户端 `TCClient.init()`。

**要素记忆** `AspectMemory`（WorldSavedData `fluxecho_aspects`：`Teams[{Team, Aspects}]`、`Research[{Player, Keys}]`）。`AspectSamples.taught(stack)`：要素容器教其中所有要素；魔力碎片 meta 0–5 教六种初始要素；平衡碎片 meta 6 不教但能付额度。学习方式：放进神秘机器输入槽、拿着右键机器、右键要素回响口。研究检查 `MTEThaumMachine.researched`：先查缓存，没缓存时只在主人**在线**时查（"Thaumcraft only has a player's research while they are online"）。

**单位与额度**：`logic/AspectUnits`（初始=1，复合=组分之和，环或深度≥16 取 65536）。额度 `credit(units, pay)`：每块碎片 `essentiaUnitsPerShard`=64 单位，剩余存 NBT `feCredit`。

- **要素回响仪**（24532，MV，`VORTEX`，2 进 1 出，`maxEUStore=V×2048`、4A："Room for a whole infusion"）：配方 `JEJ/CHC/PFP`（源质罐 ×2、MV 电路、MV 外壳、MV 泵 ×2、炼金炉）。`work()` 只学要素并把容器移到输出；**真正的产出走 `pay(aspect, n)`**，由回响口调用，直接从机器电池扣 `units×n×essentiaEuPerUnit(128)` EU 和额度。
- **要素回响口** `fluxecho:essentia_outlet`（`TileEssentiaOutlet`，`IAspectSource`+`IEssentiaTransport`，不 tick）：配方 `PTP/TJT/PTP`。自己不存要素，每次出要素都调旁边回响仪的 `pay()`（"every essentia is made and paid for the moment it leaves"）；注魔矩阵能直接抽；管道优先给 suction 要的那种；拿 phial/罐子/碎片右键选定要素。NBT `Selected`。
- **灵感回响仪**（24534，LV，`GLYPHS`）：配方 `PEP/CHC/IMT`。主人必须在线；样本必须被主人扫描过；每张纸把 `getScanAspects` 点数加进 `feOwed`，结束时发放。`start(16, 200)`。
- **炼金回响釜**（24535，MV，`CAULDRON`，2 进 1 出 + 电路）：配方 `PEP/CHC/UXU`（坩埚）。催化剂 = 第一个非碎片非要素容器的输入；筛主人研究过且团队学会全部要素的 `CrucibleRecipe`；多个候选用电路选（`logic/Choice`）；EU = 单位×128，ticks = max(20, ceil(EU/128))。有 `echoPatterns()`。
- **注魔回响台**（24538，MV，`RUNES`，9 进 1 出）：配方 `PEP/CHC/AMA`（奥术基座 ×2、符文矩阵）。样本 = 亲手注魔/奥术合成的成品。配方索引 `ThaumRecipes`（Infusion + Shaped/ShapelessArcane，只收产物是普通物品的）；`plan()` 贪心匹配材料；**先扣材料再扣额度**（碎片本身是材料时不能算额度）；每次 `remember(infusion, key)`。不摆基座、没有不稳定、不加扭曲。
- **通量灵气汲取台** `fluxecho:vis_pedestal`（`BlockVisPedestal`+`TileVisPedestal`，**不是 GT MTE**，实现 `IEnergyConnected`）：配方 `TET/CWC/AHA`（法杖充能台）。放法杖/权杖/灵气护符充能；GT 线缆任意电压不爆（对 FluxLite 报 8192V 16A）；电池 4 000 000 EU；速率 `VisFlow.rate(25, 50, 汲取模块数)` centivis/要素/tick，每 centivis 10 EU（每点灵气 1000 EU）。模块 `fluxecho:vis_module`：0 汲取（≤4，加速）、1 无线充能（≤1，32 格内团队玩家，发 trail）、2 通量链接（≤1，电低于一半时从主人 GT 无线电网取，≤32768 EU/t）。状态 IDLE/CHARGING/FULL/NO_POWER。`RenderVisPedestal` TESR（法杖旋转）、`PedestalHolo`（范围 `thaumcraft.hologramRange` 12）。

配置 `thaumcraft.*`：`enabled`、`essentiaEuPerUnit`(128)、`essentiaUnitsPerShard`(64)、`pedestalEuPerCentivis`(10)、`pedestalCentivisPerTick`(25)、`extractionModuleCentivisPerTick`(50)、`pedestalBufferEU`(4000000)、`wirelessModuleRange`(32)、`linkModuleEuPerTick`(32768)、`hologramRange`(12)、`insightTicks/EuPerTick`(200/16)。
测试 `AspectUnitsTest`、`VisFlowTest`、`ChoiceTest`。
任务 `echo_thaum()`（requires Thaumcraft）：lore → memory → essentia_echo → outlet / vis_charger → vis_modules / insight_echo / crucible_echo → infusion_echo。
残留 lang 键 `status.no_wand/no_primal_known/no_book/no_lapis` 属于已删除的机器，没在用。

## 5. 血魔法（Blood Magic）`blood/`

**鲜血回响器**（24536，MV，`BLOOD`，0xE0303A）：配方 `PEP/CHC/UAK`（铝板、MV 电路 ×2、MV 外壳、MV 泵、血之祭坛、献祭小刀）。
- special slot 放**已绑定**的 `IBloodOrb`（否则 `no_orb`/`orb_unbound`）。
- 速率 `BloodRates.lpPerTick(lpPerTick, orbLevel)`，默认 `{4,8,16,32,48,64}`；周期 20 tick 产 `rate×20` LP，`start(rate×euPerLp(2), 20)`（最高 64×2=128 = MV 电压）。
- 电路 0/1：送进 `altarRange=5`/`altarHeight=10` 内最近的祭坛主罐（`sacrificialDaggerCall`，"like the Well of Suffering does"）；电路 2：直接加进主人的灵魂网络（到宝珠上限）。
- 肉：每块 `lpPerMeat`=2000 LP（腐肉、生牛猪鸡鱼、`listAllmeatraw`），用信用结算。
- 缓冲最多 3 个周期；`altarShare` 按符文倍率留空间不溢出；每 20 tick flush；GT 只每 30 秒重查空闲机器，所以 full/no_altar 时 `markInventoryBeenModified()`。
- 每秒向祭坛发 `EchoNet.flow` 轨迹。NBT `feBuffer/feCredit/fePending`。
配置 `bloodmagic.*`：`enabled`、`lpPerTick`（字符串列表，"keep rate x euPerLp at 128 or below"）、`euPerLp`(2)、`lpPerMeat`(2000)、`altarRange`(5)、`altarHeight`(10)。测试 `BloodRatesTest`。
任务 `echo_blood()`（requires AWWayofTime）：lore → first_orb → blood_echo → network。

## 6. 猎物（MobsInfo）`mobs/`

- **猎物印记** `fluxecho:mob_imprint`（NBT `Mob` = EntityList 名）：工作台 4 纸 + 1 腐肉 → 4 张空白。写入（`MobEvents`，"each time by a real player and never by a machine"）：① `LivingDeathEvent` 击杀者是真玩家（非 FakePlayer）、MobsInfo 有掉落表、背包有空白印记、还没带这只怪的印记；② 拿空白印记右键活怪且统计 `stat.killEntity.<mob>`>0。写入时 `EchoLedger.record(team, "mob", mob)`。显示名处理带占位符的 lang（`logic/LangText`）。
- **猎物回响器**（24537，LV，`PREY`，1 进（武器）4 出）：配方 `PEP/CHC/ASA`（铁剑）。掉落 `MobRecipe.generateRandomOutputs(world, rand, looting, true, false)`（含"玩家击杀才掉"，looting 取武器抢夺等级，武器每次扣 1 耐久）；过滤 `dropBlacklist`（`logic/ItemFilter`）；首领需 `allowBosses`。费用 `KillCost.of(maxHealth, 128, boss?50:1, 32, 200)`：EU = ceil(hp×128)×倍率，ticks = max(200, ceil(EU/32))。**机器本身不写账本**。NEI 每种怪一页（`EchoRecipeMaps.later`，MobsInfo 进世界才建表）。
- `MobWaila`：看着怪显示团队有没有它的印记。
配置 `mobs.*`：`enabled`、`euPerHealth`(128)、`minTicks`(200)、`allowBosses`(true)、`bossMultiplier`(50)、`dropBlacklist`(`minecraft:skull:1`)。测试 `KillCostTest`、`ItemFilterTest`、`LangTextTest`。
任务 `echo_prey()`（requires mobsinfo）：lore → imprint → mob_echo → bosses。

## 7. 作物（IC2）`crops/`

- **作物印记** `fluxecho:crop_imprint`（NBT `Owner/Name/Growth/Gain/Resistance`）。桥接 `CropImprints`（`FULL_SCAN=4`）。
- **种子拓印机**（24539，LV，`SCAN`）：配方 `PEP/CHC/RZR`（作物分析仪）。special slot：扫满 4 级的种子袋或印记（保留）。输入纸 → 印记（忽略电路）；输入印记 + 电路 1–4 → 改属性（1 生长、2 增产、3 抗性、4 全部，`logic/CropStats`，0–31）。`start(16, 100)`。只有样本是种子袋时 `remember(crop, owner:name)`。
- **种子回响箱**（24540，LV，`SPROUT`）：配方 `PEP/CHC/SUS`（作物架 ×2、LV 泵）。special slot 放印记；1 根作物架 → 1 袋扫满、同属性的种子。`start(24, 200)`。有 `echoPatterns()`。
配置 `crops.*`：`enabled`、`imprintTicks/EuPerTick`(100/16)、`seedTicks/EuPerTick`(200/24)、`sticksPerSeed`(1)。测试 `CropStatsTest`。
任务 `echo_crops()`（requires IC2）：lore → imprinter → first_imprint → seed_echo。

## 8. 植物魔法（Botania）`mana/`

**魔力回响泉**（24541，`FOUNTAIN`）——**直接继承 `MTEBasicMachine`，不是 `MTEEchoMachine`**，有自己的 GUI（`ManaGui`/`ManaScreen`）、全息（`ManaHolo`，`HOLO_MANA=1`）、Waila、NEI（`SpringNei`，id `fluxecho.spring`）。这是采集器"房屋风格"的复刻：一台机器，核心电路分阶。
- 配方 `PEP/CHC/SOS`（魔力发射器 ×2、魔力池），LV 配方，出厂没有核心。
- 槽：`CORE=3` 核心电路；输入 0 花瓣（任意色）；输入 1 `IManaItem` 充能。无输出槽。
- 分阶（`core/CoreCircuits.tier()`，LV–LuV = 1–6；`logic/ManaSpring`）：魔力/t = 16 × 4^(t−1)；EU/t = 魔力/t × 2 = 该阶电压；每片花瓣价值 1250 × 4^(t−1)（各阶都约 15.36 片/分）；水平范围 4+t−1，上下 2+(t−1)/2。
- `checkRecipe`：DISABLED → NO_CORE → NO_POOL（范围内没池子且充能槽也满）→ POOL_FULL（缓冲 ≥ 3 周期）→ NO_PETAL；周期 20 tick。flush 先充物品再按距离喂池子。每秒发 trail（最多 8 个池子）。
- 迁移 0.6.0 前的旧机器：有 `feBuffer` 没 `feSpring` → 核心槽放 MV 电路。
- NBT `feBuffer/feCredit/fePending/feDelivered/feHolo/feSpring`；GT 更新字节 bit 0–2 朝向、3–5 阶、6 全息。
- `ManaTextures.load()` 必须在注册机器时调用（GT 只给贴图拼接之前存在的图标上图）。
- 没有账本类别。
配置 `botania.*`：`enabled`、`lvManaPerTick`(16)、`euPerMana`(2)、`lvManaPerPetal`(1250)、`poolRange`(4)、`poolHeight`(2)、`hologramRange`(16)。测试 `ManaSpringTest`。
任务 `echo_mana()`（requires Botania）：lore → mana_echo → tiers。

## 9. 加一台新的回响机器（步骤）

1. `core/MachineId` 加一项（下一个 offset 14，别用 3 和 12），带 key、电压、accent 色、`Motif`、是否有样本槽。
2. 在模块包里写 `MTEXxx extends MTEEchoMachine`，实现 `work()`（用 `idle/start/blocked/remember`），必要时 `info()`、`holoData()`、`echoPatterns()`。
3. 模块的 `machines()` 里注册，建 `fluxecho.recipe.<key>` 示例 map（`EchoRecipeMaps.page`）。
4. 配方走 `EchoRecipes.machine(...)`：带上它重复的原版设备 + 末影珍珠。
5. lang（zh_CN 和 en_US 都要）：`gt.blockmachines.fluxecho.<key>.name`、`fluxecho.<key>.tip`、`.type`、`.gui_sample`、`fluxecho.nei.note.<key>`、所有 `fluxecho.status.*`。
6. 配置节加开关和数值（`Config.java`，见 [platform.md](platform.md)）。
7. 纯逻辑（速率、费用）放 `logic/` 并写单元测试。
8. 任务：在 `build_quests.py` 对应线里加任务，`python quests/build_quests.py` 重新生成。
9. README 的模块表、配方表、配置表；CHANGELOG。
