# 开发手册 · 通量深层（`com.fluxdepths`）

> 改通量碎片采集器、流体回响泵、印记拓印器、钻头之前读这一篇。玩家向说明在 [docs/FluxDepths.md](../FluxDepths.md)。总览见仓库根目录 `CLAUDE.md`。

## 0. 定位

- jar 里的第三个 `@Mod`：`modid="fluxdepths"`，`dependencies="required-after:gregtech;required-after:visualprospecting"`（VP 1.4.8，GTNH 自带），版本 = `com.fluxecho.Tags.VERSION`。
- 注册名 `fluxdepths:*`、配置 `config/fluxdepths.cfg`、网络频道 `fluxdepths`、GT 机器 ID 24520–24529。**都不能改名**（0.8.0 合并约定）。
- `IsolationTest` 只扫 `com/fluxecho/`，明确豁免 `com/fluxdepths/`。
- 生命周期（`FluxDepths.java`）：preInit 读配置、注册物品 `imprinter`、`imprint`；init `Collectors.register()`、`Pumps.register()`、`HoloNet.init()`，客户端 `HoloClient.register()`；postInit 配方和泵的 NEI 页；loadComplete `RecipeGuard.check()`。创造栏 `fluxdepths`。

## 1. 文件

| 文件 | 作用 |
|---|---|
| `FluxDepths`、`Config`、`Tags` | 入口、配置、版本 |
| `RecipeGuard` | 配方先走 `GTModHandler.addCraftingRecipe`（`NOT_REMOVABLE`），GT 不收就退成 `ShapedOreRecipe`；工具字母 d/f/h/k/r/s/w/x = 螺丝刀/锉/锤/刀/软锤/锯/扳手/剪线钳；`check()` 在加载末尾补回丢失的配方，报告引用空矿辞的配方（"GT drops a recipe without a word when it does not like it"） |
| `NEIFluxDepthsConfig` | NEI 插件：`VeinHandler` 标签页和催化剂；隐藏旧采集器；替换 NEI 的 `@` 模组搜索，让 GT 机器按 metaName 前缀归到对应模组 |
| `shard/MTEFluxCollector` | 采集器本体（`MTEBasicMachine`）：供能、槽位、迁移旧机器、贴图、GUI、NBT、Waila、全息数据 |
| `shard/ShardTier` | 等级表（第 3 节） |
| `shard/ShardWork` | 每个工作周期的逻辑（代替 GT 配方查找）；通量碎屑也在这里出 |
| `shard/CoreCircuits` | 核心电路矿辞 `circuitLV..circuitLuV` → 等级 1–6；`example(t)` |
| `shard/Collectors` | 注册 1 台采集器 + 6 个旧 ID；`main()`、`legacy()` |
| `shard/DrillHead`、`DrillHeads` | 6 种固定钻头、磨损概率；GT `toolHeadDrill` → `DrillHead`，`uses(stack)` |
| `shard/Mix` | 矿脉出矿权重（主/次各 1，伴生/零散各 1/8） |
| `shard/Veins` | 懒加载所有矿脉（GT `OreMixes` + Bartworks `BWOreLayer`），键是 VP 的矿脉名 |
| `shard/ShardState` | `Status`（9 种）、`next`、`produced`、`lastVein` |
| `shard/ShardText`、`ShardTextures` | 文本（提示/GUI/全息/Waila）；方块贴图（外壳、核心透镜、顶部栅格、按等级着色的灯条、泵覆盖层） |
| `shard/CollectorGui`、`client/CollectorScreen` | ModularUI 界面 176×220；GL 画的部分 |
| `shard/ShardCrafting` | 拓印器和采集器配方；旧采集器无序合成成新采集器 |
| `client/FluxDraw`、`HoloClient`、`Keys` | 通量风格绘图工具（与 `com.fluxecho.client.FluxDraw` 是**各自独立的副本**）；全息；Shift 检测 |
| `holo/HoloNet` | 频道 `fluxdepths`，消息号 0，S→C 全息数据 |
| `fluid/MTEFluidPump`、`PumpTier`、`PumpRates`、`Pumps` | 流体回响泵；LV/MV/HV 参数；每周期升数；注册、NEI 伪配方表 `fluxdepths.recipe.fluid_pump` |
| `item/ItemImprinter`、`ItemImprint` | 印记拓印器；印记 |
| `nei/VeinHandler` | NEI「通量碎片采集」页（id `fluxdepths.veins`） |

资源 `assets/fluxdepths/`：lang；`textures/blocks/collector/*`（front/top/pump_front 各有普通、active、active_glow；casing_side/top/bottom；strip；core；core_active(+glow) 8 帧 frametime 2）；`textures/gui/*`；`textures/items/{imprint,imprinter}`。贴图生成：`java tools/DepthsTextures.java`、`java tools/DepthsGuiTextures.java`（坐标和 `CollectorGui` 对应）。

## 2. 通量碎片采集器 / Flux Shard Collector

- **ID**：`shard_collectors.firstMachineId`（默认 24520），metaName `fluxdepths.shard.collector`，类 `MTEFluxCollector`。24521–24526 是旧的分级采集器（`fluxdepths.shard.{hp_steam,lv,mv,hv,ev,iv}`，同一个类，`LEGACY_TIER={0..5}`，NEI 隐藏，可无序合成成主机器）。ID 被占用时抛 `IllegalStateException`。
- **一台机器，核心槽的 GT 电路决定等级**（蒸汽 → LuV），这是 FluxEcho 的"房屋风格"。
- **槽位**：`CORE=3`（GT special slot，核心电路，只能手放）；钻头槽 = `getInputSlot()`；印记槽 `getInputSlot()+1+i`（i=0..3）；4 个输出槽。主面不接受任何物品；钻头槽只收 `DrillHeads.uses>0`；印记槽只在空着时收矿脉印记。钻井液罐 16 000 L，只收钻井液。
- **供能**：核心空 = 蒸汽模式（`maxSteamStore=16_000`，注释说 GT 蒸汽单位是半升）；有电路 = 电（`maxEUInput=电压`、2A、`maxEUStore=max(32,V)*64`）；开工最低储量蒸汽 1000、电 `V*16`；没有电池槽；`checkRecipe` 直接设 `mEUt`/`mMaxProgresstime`，**GT 不超频**。
- **工作周期 `ShardWork.check`**（顺序）：
  1. 模块关闭 → DISABLED；
  2. 按槽位顺序收集有效矿脉印记，最多 `tier.imprints` 条；`crossDimension=false` 时跳过别的维度的印记；
  3. 一条都没有 → WRONG_WORLD 或 NO_IMPRINT；没钻头 → NO_HEAD；MV 起要钻井液（`fluidPerOre`）→ NO_FLUID；
  4. `turn = floorMod(next, n)`，`ore = vein.mix.pick(rand)` 一块；放不下 → OUTPUT_FULL；
  5. 开工：`next` 前进；按概率磨损钻头；扣钻井液；`mOutputItems[0]=ore`；**掷通量碎屑**；设 EU 和时长；`lastVein`。钻头和钻井液在周期开始时扣；`endProcess` 时 `produced++`；多张印记轮流出矿（分摊速度，不叠加）。
- **通量碎屑钩子（FluxEcho 0.9.0）**，在 `mOutputItems[0]=ore` 之后：
  ```java
  ItemStack grit = Grit.roll(world.rand, m.getBaseMetaTileEntity().getOwnerUuid());
  m.mOutputItems[1] = grit != null && m.fits(grit) ? grit : null;
  ```
  碎屑放不下就丢掉，**不会**让机器 OUTPUT_FULL（"it never holds the ore up"）。几率 `nexus.gritChance`（0.15），团队有研究 `grit_yield` 时 ×1.5。这是碎屑的**唯一来源**（用户定的规则）。
- **状态修正 `onPostTick`**：空闲时电不够开工就显示 NO_POWER（"GT does not even look for work without the steam or EU to start"）；工作中 `mStuttering` 也是 NO_POWER。
- **产出**：普通 GT 矿石方块；权重和 GTNH 虚空采矿机一致；同一种矿占两层算两份。
- **迁移旧机器**：NBT 没有 `fdV3` 时第一次服务端 tick 运行 `migrate()`：核心槽按旧等级放电路，原有物品分拣进各槽，放不下的掉出来。
- **外观**：正面外壳 + 核心透镜（工作时 active + 发光）；输出面 GT 管道口；顶面栅格；侧面灯条按等级着色并发光。`ShardTextures.front()` 和 front*.png 没被代码使用。GT 更新字节：bit 0–2 朝向、3–5 等级、0x40 全息。
- **GUI** `CollectorGui` 176×220：左列核心(9,25)/钻头(9,45)/钻井液(9,65)；印记 2×2（未用的槽盖锁）；深井 (71,23) 32×58，点击开 NEI `fluxdepths.veins`；输出 2×2 从 (107,25)；全息开关 (149,25)；自动输出 (149,43)。
- **全息**：默认关闭；GUI 按钮或螺丝刀切换。服务端每 10 tick 向 `com.fluxecho.gate.Sight.near(…, hologramRange+8)` 的玩家发；客户端 `HoloClient` 同时注册 `RenderWorldLastEvent` 和 `com.fluxecho.client.FarDraw.add`（光门里也看得见），`STALE_MS=2500`。
- **Waila** NBT：`fdTier/fdStatus/fdHeadUses/fdImprints/fdProduced/fdVein/fdHolo`。
- **保存的 NBT**：GT 自身 + `fdNext`、`fdProduced`、`fdVein`、`fdHolo`、`fdV3`。
- **配方**：`PEP/GHG/TDT`（青铜板、末影珍珠、青铜齿轮、青铜外壳、中型青铜管、青铜钻头）。
- **Directory**：每 100 tick 向 `com.fluxecho.core.Directory.report` 报状态（终端"机器"页）。

## 3. 等级表 `ShardTier`

| 核心 | 电压 | ticks/块 | energy/t | 每小时 | 印记 | 钻井液 L/块 |
|---|---|---|---|---|---|---|
| STEAM（无电路） | — | 200 | 8 蒸汽单位（16 L/t） | 360 | 1 | 0 |
| LV | 32 | 50 | 24 | 1440 | 2 | 0 |
| MV | 128 | 33 | 96 | 2181.8 | 2 | 20 |
| HV | 512 | 25 | 384 | 2880 | 3 | 20 |
| EV | 2048 | 18 | 1536 | 4000 | 3 | 20 |
| IV | 8192 | 13 | 6144 | 5538 | 4 | 20 |
| LuV | 32768 | 11 | 24576 | 6545 | 4 | 20 |

- `VOID_MINER_PER_SECOND=2`：**每一级都必须慢于 GTNH 虚空采矿机 I**（"a pinhole into the depths never matches a tear"），`ShardTierTest` 守着。`MAX_IMPRINTS=4`。
- 电压超过核心等级会像 GT 机器一样爆炸（"unplug high voltage before fitting a lower circuit"）。

## 4. 钻头（GT `toolHeadDrill`，本模组没有自己的钻头物品）

- 每块矿石磨损概率 1/uses：`wears(uses, roll) = uses <= 1 || roll*uses < 1`。
- 固定 6 种（平均块数）：青铜 128、钢 256、铝 384、不锈钢 512、钛 768、钨钢 1024；其他材料 `derivedUses = clamp(mDurability/2, 32, 16384)`。
- 用户规则：钻头**留在槽里按概率磨损**，绝不在第一次使用时整个消失。

## 5. 流体回响泵（LV–HV）

- ID `fluid_pumps.firstMachineId + ordinal`（默认 24527/24528/24529），metaName `fluxdepths.pump.{lv,mv,hv}`，类 `MTEFluidPump`（GT 外壳 + 覆盖层）。
- special slot 放流体印记，输入槽放钻头；输出罐 64 000 L。
- `checkRecipe`：disabled → no_imprint → wrong_world → `litres = PumpRates.perCycle(amount, share)`（0 → dry）→ 钻头必须是固定 6 种之一且不低于 `tier.takes`（**按耐久推算的钻头不收**）→ output_full → 磨损 `wears(ores*15)` → 开工，周期 20 tick。**区块储量永远不减少**。
- 每秒产量 `floor(pristine*share)`，至少 1。

| 泵 | GT 等级 | EU/t | 最低钻头 | 默认份额 | 钻头平均寿命 |
|---|---|---|---|---|---|
| LV | 1 | 24 | 钢 | 0.1 | 64 min |
| MV | 2 | 96 | 铝 | 0.25 | 96 min |
| HV | 3 | 384 | 不锈钢 | 0.5 | 128 min |

- NEI：GT 配方表 `fluxdepths.recipe.fluid_pump`（只给 NEI），按 `GTMod.proxy.mUndergroundOil` 每种流体一条 `.fake()` 配方。
- 配方 `CPC/UHU/TXT`：X 是上一级机器（LV 用采集器，MV 用 LV 泵，HV 用 MV 泵）；板和管 LV 青铜、MV 钢、HV 不锈钢。
- Waila NBT `fdPump`、`fdHeadSeconds`；不存自定义 NBT。

## 6. 物品

- **印记拓印器** `fluxdepths:imprinter`（堆叠 1）：配方 `PGP/SCS/dRh`。右键拓矿脉（`ServerCache.instance.prospectOreBlockRadius(dim,x,z,0)`，消耗 1 张纸，坐标是矿脉中心）；潜行右键拓地下流体（`UndergroundOil.getPristineAmount`，坐标是区块）。
- **印记** `fluxdepths:imprint`（堆叠 1）：NBT `vein/fluid/amount/dim/dimName/x/z`；`ANY_DIM=Integer.MIN_VALUE`（NEI 印记；没有 `dim` 键也返回它，所以 `crossDimension=false` 时作弊拿的印记是 WRONG_WORLD）。

## 7. 配置 `config/fluxdepths.cfg`

| 键 | 默认 | 说明 |
|---|---|---|
| `shard_collectors.enabled` | true | 关掉不注册配方和 NEI，机器停（方块保留："registered even when the module is off, so placed collectors keep their blocks"） |
| `shard_collectors.firstMachineId` | 24520 | 占 7 个 ID |
| `shard_collectors.crossDimension` | false | 对矿脉和流体印记都生效 |
| `shard_collectors.hologramRange` | 16 | 0 = 关闭 |
| `shard_collectors.enableDefaultRecipes` | true | |
| `fluid_pumps.enabled` / `firstMachineId` / `enableDefaultRecipes` | true / 24527 / true | 占 3 个 ID |
| `fluid_pumps.sharePerSecond` | "0.1","0.25","0.5" | 坏值回退默认 |

## 8. 任务书（`quests/build_quests.py`）

- `shards()`：key `line/shards`，slug `FluxDepthsShards`，`requires=["fluxdepths"]`，13 个任务；key 沿用旧名（如 `shards/lv` 现在是「核心电路」）以保住进度。泵任务写死 24527–24529。
- `lazy_ae()`：key `line/lazyae`，`requires=["appliedenergistics2","ae2fc"]`，与本模组无关，只是历史上来自 FluxDepths 0.1.x。
- `QuestInstaller.checkFluxDepthsIds()` 反射读 `com.fluxdepths.Config.shardsFirstId`，不是 24520 时只警告（不查泵 ID）。

## 9. 坑和已知问题

- `getDescription()` 返回空数组："GT would store the first description it sees in its own lang file, in whatever language"；提示改走 `addAdditionalTooltipInformation`。（FluxEcho 的机器同理。）
- `ShardTextures.load()` 必须在注册机器时调用："only those that exist before the block textures are stitched get an image"。
- NEI 的 `HandlerInfo` 要同时写进 `handlerMap` 和 `handlerAdderFromIMC`："whichever comes first, the tab keeps its size and icon"。
- 已知问题（读代码发现，未修）：
  - GUI 只是给超出等级的印记槽画锁，`ShardWork` 仍按槽位顺序读它们；
  - `CollectorScreen.readout` 蒸汽上限写死 16 000，和 `MTEFluxCollector.STEAM` 重复；
  - 两个 lang 文件第 11 行（潜行右键拓流体说明）是真换行而不是字面 `\n`，没有 key，被忽略；
  - en_US `imprint.blank` 写 "Vein Imprinter"，物品名是 "Depth Imprinter"；
  - `shards()` 任务线描述说"到 IV"，代码支持到 LuV。

## 10. 测试（`src/test/java/com/fluxdepths/`）

- `shard/ShardTierTest`：每级每秒 < 2；ticks 严格递减；印记数不减且 ≤ 4；每小时产量钉死（STEAM 360、LV 1440、HV 2880、IV 5538、LuV 6545）；`energy <= voltage` 且 `> voltage/4`；只有 MV 起要钻井液。
- `shard/DrillHeadTest`：磨损边界、200 万次抽样平均寿命 ±5%、`derivedUses`、6 种钻头寿命严格递增。
- `shard/MixTest`：权重 1:1:1/8:1/8、重复矿合并、抽样误差。
- `fluid/PumpRatesTest`：向下取整、至少 1 L、不溢出、`parse` 回退、泵等级参数递增。
