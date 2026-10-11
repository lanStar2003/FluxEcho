# 开发手册 · 平台层（生命周期、配置、机器框架、视觉、图鉴、NEI、任务书、AE、构建发布、测试）

> 加配置、加机器、加网络消息、碰客户端渲染、改任务书、改构建之前读这一篇。总览见 `CLAUDE.md`。
> 路径相对 `src/main/java/com/fluxecho/`。

## 1. 根包与生命周期

一个 jar 三个 `@Mod`：`fluxecho`（`com.fluxecho`）、`fluxlite`（`com.fluxlite`）、`fluxdepths`（`com.fluxdepths`）。后两个见 [fluxlite.md](fluxlite.md)、[fluxdepths.md](fluxdepths.md)。

| 文件 | 作用 |
|---|---|
| `FluxEcho` | `@Mod(modid="fluxecho")`：`LOG`、`instance`、`proxy`、创造栏 `TAB`，生命周期调度 |
| `Config` | `config/fluxecho.cfg`，`public static` 字段，`VERSION` + `upgrade()` 迁移 |
| `Mods` | preInit 读一次可选模组是否在场：`forestry, thaumcraft, bloodMagic(AWWayofTime), fluxDepths, betterQuesting, mobsInfo, ic2, botania, ae2, waila` |
| `CommonProxy` / `ClientProxy` | 客户端注册所有渲染和事件 |
| `NEIFluxEchoConfig` | NEI 插件（按类名被发现） |
| `Tags` | 构建时生成（`gradle.properties: generateGradleTokenClass`），`Tags.VERSION` |

依赖串：`required-after:gregtech;after:Forestry;after:Thaumcraft;after:AWWayofTime;after:betterquesting;after:fluxdepths;after:appliedenergistics2;after:ae2fc;after:mobsinfo;after:IC2;after:berriespp;after:Botania;after:Waila`。

- **preInit**：`Config.load` → `Mods.detect` → `QuestInstaller.preInit` → `CodexModule.preInit`（物品 codex、`EchoNet.init`）→ `MatterModule`（0.12.0 起还有 `phase_alloy`、`resonant_glass` 和矿辞 `ingotPhaseAlloy`、`glassResonant`）→ `FrameModule` → `CampusModule`（地板、配件、补给口、回收箱）→ `NexusModule` → `LibraryModule` → `CityModule`（0.11.0：模块核心 `fluxecho:module_core`、TE `fluxecho:eco_dome`；0.12.0 加 TE `fluxecho:dome_generator`、`fluxecho:root`，见 [city.md](city.md)）→ `NexusNet.init` → `GateModule` → 按在场：Bee/TC/Mob/Crop/AE `preInit`。
- **init**：按在场调各模块 `.machines()`（GT ID 在这里 `claim`；"registered even when a module is off, so placed machines keep their blocks"）→ `GateModule.init` → `CampusModule.init`（注册 `DropCapture`：营造拆方块时接住洒出来的物品，0.10.1；0.12.0 起再 `Lift.register()`：光梯的服务端处理——FML `PlayerTickEvent`（END）、Forge `LivingFallEvent`、`PlayerLoggedOutEvent`、`PlayerChangedDimensionEvent`，见 [nexus.md](nexus.md) §4.2）→ `NexusModule.init` → `LibraryModule.init`（还登记营造的 `ModuleSpec`）→ `CityModule.init`（0.11.0：生态穹的 NEI 预览和中枢预览、`BlockFrame`/`BlockFitting.onUse` 登记单元的右键 `UnitUse`、生态穹的 `ModuleSpec`——在书库之后，所以中枢先提供档案馆；0.12.0 再登记回响穹顶发生器和潮汐井的 NEI 预览、中枢预览和 `ModuleSpec`）→ `proxy.init()`。
- **postInit**：Codex、Matter（0.12.0 起还有相位合金的 GT 电力高炉配方，`defaultRecipes` 时）、Frame、Campus（0.12.0 起配件配方里有光梯）、Nexus（`Manifests.init`，0.12.0 起含显化 `resonant_glass`）、Library、City（0.11.0：显化 `eco_dome_core`；0.12.0 加 `dome_generator_core`、`root_core`）、Gate，再按在场 Bee/TC/Blood/Mob/Crop/Mana/AE（注册图鉴类别和配方）。
- **loadComplete**：Bee/Crop/TC/Mob 补 NEI 示例 → `RecipeCheck.run()`。
- **serverStarting**（0.10.1）：`e.registerServerCommand(new CommandFluxEcho())`——管理员命令 `/fluxecho`（权限等级 2）：`research all|reset [玩家]`、`build free [on|off]`、`build finish`、`build status`，参数由纯逻辑 `logic/EchoCommand` 读，回复都是 `fluxecho.cmd.*`/`fluxecho.build.*` 的 lang 键（见 [campus.md](campus.md) §7）。
- **serverStarted**：`QuestInstaller.serverStarted()`。
- **serverStopped**：`EchoLedger.reset`、`ResearchData.reset`、`Formed.clearServer`、`FrameEvents.clear`、`NexusModule.serverStopped`、`Terrain.serverStopped`（营造记住的原木判定和它弱引用的世界）、`GateModule.serverStopped`、（神秘在场）`TCModule.serverStopped`。`Directory.clear()` 存在但没人调用。
- **`ClientProxy.init()`**：`CodexTooltips`（Forge + FML 总线）→ `MachineFx` → `FrameClient` → `CampusClient`（地板/配件的 ISBRH、建造投影、发射特效、总图，0.12.0 起还有光梯的 `LiftClient`（FML `PlayerTickEvent` START、`FOVUpdateEvent`、`WorldEvent.Unload`）和 `LiftRender`（`RenderWorldLastEvent` + `FarDraw`，它的 `LiftScan` 挂 client tick 和世界卸载），见 [campus.md](campus.md) §11、[nexus.md](nexus.md) §4.2）→ `TileMultiblock.clientWorld = WorldClient.class` → `NexusRender` → `EchoCrystalRender` → `MachineHolo` → `FlowFx` → `GateClient` → （神秘）`TCClient.init`、（植物魔法）`ManaHolo` → NEI 在场时注册客户端命令 `NeiCheck`。

## 2. 配置约定（`Config.java`）

- 类别名是 `private static final String` 常量；字段 `public static` 带默认值；`load()` 里 `c.getInt/getBoolean(key, CAT, 当前值, min, max, "英文说明")`；double 用 `c.get(...).getDouble()`；long 用字符串 + `parseLong`（带回退）。
- 每个类别要 `setCategoryComment`；只在客户端生效的项说明以 "Client:" 开头。
- **删除某项或改默认值**：把 `VERSION` 提到新版本，在 `upgrade()` 里用 `older(was, "x.y.z")` 删掉旧 key（加载时按新默认值重建）。现有迁移：0.5.0 删 `enchanting` 类别和若干 thaum key；0.6.0 删 `manaPerTick`、`manaPerPetal`；0.9.2 删 `nexus.librarySamples`；0.10.1 把还是旧默认 `"2"` 的 `nexus.buildFillDepth` 删掉、按新默认 4 重建（`bumpsFillDepth`，玩家改过的数保留）。`ConfigTest` 测 `older()`、0.10.1 的默认值和 `bumpsFillDepth`。
- README「配置」一节的中文表要同步。
- 类别：`general`（`firstMachineId` 24530、`enableDefaultRecipes`）、`quests`（`install`）、`bees`、`thaumcraft`、`bloodmagic`、`mobs`、`crops`、`botania`、`codex`（`tooltipHints`）、`nexus`（中枢见 nexus.md §9，0.10.0 的营造键 `campusEnabled`、`archiveEnabled`、`build*` 和 0.10.1 的 `buildClearAll` 见 campus.md §14，0.11.0 城市模块的维持电 `moduleUpkeepEuPerTick` 见 nexus.md §9）、`effects`（`machineEffects`、`flowTrails`、`effectRange` 32、`hologramRange` 16）、`ae2`（`enabled`）、`gates`。各模块的键和默认值写在对应的手册里。现在 `VERSION="0.10.1"`（0.10.1 加 `buildClearAll`、改 `buildFillDepth` 的默认值，迁移见上；0.11.0 只加了 `nexus.moduleUpkeepEuPerTick`，只加键不用提版本；0.12.0 没有加也没有改任何键）。

## 3. 机器框架（`core/`）

| 文件 | 作用 |
|---|---|
| `MTEEchoMachine` | 回响机器基类（`MTEBasicMachine`），细节见 [echo-machines.md](echo-machines.md) §0 |
| `MachineId` | 机器枚举（offset、key、电压、英文名、主题色、`Motif`、样本槽），`RESERVED=40`；ID 表见 echo-machines.md §1 |
| `Machines` | `claim()`（ID 被占用就抛 `IllegalStateException`："a silent overwrite would turn that mod's placed machines into ours"）；`put/get` 机器 ItemStack |
| `Motif` | 11 种动画母题：`SCAN, COMB, HELIX, VORTEX, GLYPHS, CAULDRON, RUNES, BLOOD, PREY, SPROUT, FOUNTAIN`（新母题要在 `client/Motifs.draw` 和 `client/MachineFx.effect` 各加一个 case） |
| `EchoTextures` | 机器方块面：通量外壳、各机器正面 `machines/<key>/front[_active[_glow]]`、顶面回响环；**图标必须在机器构造时创建**（"only those that exist before the block textures are stitched get an image"） |
| `FluxMachineGui` | ModularUI 176×220（背包 y=138），`neiId(kind)="fluxecho.machine.<key>"` |
| `EchoText` | 翻译工具，前缀固定 `fluxecho.`：`t`、`lines`（字面 `\n` 换行）、`machineType`、`status`、`statusColor`、`decode`、`tier`、`seconds`、`wailaBody` |
| `EchoRecipes` | 配方先走 `GTModHandler`（`NOT_REMOVABLE`），GT 拒收退成 Forge 矿辞配方；`forForge()`；全部登记到 `RecipeCheck.expect`；缺材料只写日志。⚠ **GT 工具字母**：GT 5.09.51 把有序配方图案里的小写 `b c d f h i j k m p r s w x` 当成合成工具（c 撬棍、r 软锤……），把工具追加在调用方的键后面，Forge 的有序配方对同一个键取最后一个值——原料被工具顶掉，GT 还报成功（所以也不会退成 Forge 配方）。图案只用大写键；表驱动的配方先过 `logic/GtPattern.safe`（把所有小写键换成没用过的大写字母，行和键一起改，`GtPatternTest` 守着），`campus/PartBlocks.register` 就是这样做的 |
| `RecipeCheck` | loadComplete 时补回丢失的配方，报告空矿辞名 |
| `EchoRecipeMaps` | 每台机器一个 GT `RecipeMap` `fluxecho.recipe.<key>`（`disableRegisterNEI`，只给 NEI 当示例）；`map/page/later/examples/get`；全部 `synchronized`（"NEI asks from its own threads"） |
| `EchoPattern` | 给 AE 的"现在能做什么" |
| `Directory` | 内存里的机器登记表：键 `dim:x:y:z`，`Entry(team, dim, xyz, name 键, status 键, level, seen)`；`of(team)` 丢掉 1 小时没上报的，按 PROBLEM(2) > IDLE(1) > WORKING(0)、再按名字排序。上报方：`MTEEchoMachine`、`TileNexus`（有营造任务时状态换成 `building`/`build_paused`，`nexusStatus`）、`TileLibrary`、FluxDepths 的采集器和泵（每 100 tick）。读取方：`nexus.TerminalView` |
| `Owners` | `team(uuid)` = GT `SpaceProjectManager.getLeader`（异常退回本人）；`online`、`loginName`（"Thaumcraft and Blood Magic key their player data by login name"） |
| `CoreCircuits` | 矿辞 `circuitLV..circuitLuV` → 等级 1–6，`example/label/color`（FluxDepths 有自己的副本） |

状态颜色（`EchoText.statusColor`）：`working` 0x6CFF8A；`idle/ready` 0x86A6B8；`no_power/disabled` 0xFF6050；其余 0xFFB040。机器"房屋风格"（用户认可）：一台机器靠核心电路分阶、自己的贴图/GUI/NEI 页、全息默认关可开、通量世界观。其他回响机器保持原玩法（不加核心分阶，要加先问）。

## 4. 客户端视觉框架与光影安全

| 文件 | 作用 |
|---|---|
| `client/FluxDraw` | 通量风格平面 GL 绘制（不用纹理）：`pane/bar/column/scan/frame/corners/gradient/text/small...`、调色板（DEEP 0x0A1622、SEAM 0x1F3C4E、CYAN 0x4FE3FF、VIOLET 0x8A5CFF…）、`worldBegin/worldEnd` |
| `client/ShaderCompat` | 反射 Iris API：`packInUse()`、`shadowPass()` |
| `client/FarDraw` | "画完世界后"部件的登记表，光门从另一侧再画一遍（见 [gates.md](gates.md) §5）。登记者：`MachineHolo`、`MachineFx`、`FlowFx`、`NexusRender`、`BuildRender`、`BuildFx`、`Masterplan`（后三个是 0.10.0 营造的，`campus/client/`；0.10.1 起 `BuildRender` 也画光井的光，数据来自 `WellScan` 的 client tick；0.12.0 起还画地下计划的透视和悬空计划的影子）、`LiftRender`（0.12.0，光梯的光束，数据来自 `LiftScan` 的 client tick）、`ManaHolo`、`PedestalHolo`、`fluxdepths/client/HoloClient`；书库由 `NexusRender` 经 `ModuleRender` → `LibraryRender` 画（档案馆的 `ArchiveRender` 由 `LibraryRender` 调用），0.11.0 的城市模块经 `ModuleRender` → `city/client/CityRender`（生态穹再调 `EcoDomeRender`，0.12.0 的 `DomeGeneratorRender`、`RootRender` 也一样；超出范围的穹顶发生器的地标经 `ModuleRender.landmark` → `CityRender.landmark`），都不单独登记 |
| `client/Motes` | 朝向相机的发光点（加法混合，`textures/effects/mote.png`）；`begin` 在打开批次前失败、`end` 画的时候出错，都会把 GL 状态出栈（0.10.0） |
| `client/MachineFx`、`MachineHolo`、`HoloStore` | 机器头顶动效（每帧最多 48 台）；机器全息（150×104 px，`PX=1/90`）；全息数据（2500 ms 过期） |
| `client/FlowFx`、`FlowStore` | 机器到目标的弧形轨迹（寿命 1600 ms） |
| `client/MachineScreen`、`Motifs`、`Keys` | GUI 自绘部分；母题动画（GUI、NEI、全息共用，`hash/mix/dot/ring/hex`）；Shift |
| `render/Shapes` | 大型多方块几何库（见 [nexus.md](nexus.md) §8） |

**光影安全规则**（用户开着 Complementary 光影玩，所有视觉先按"光影开着"设计）：
1. 只在 `RenderWorldLastEvent` 里画，**不用 TESR**（TESR 画的面板在光影下变白，0.6.0 修过）；每个处理器开头 `if (ShaderCompat.shadowPass()) return;`（"a hologram casts no shadow"）。
2. `FluxDraw.worldBegin()`：pushAttrib；关 lighting/cull/fog；开 blend；`depthMask(false)`；lightmap `(240, packInUse()?0:240)`。
3. **每个顶点都要有法线**（"shader packs light geometry by its normal, and a pane without one comes out washed white"）；`Motes`/`Shapes` 用指向观察者的法线。
4. `worldEnd()`：popAttrib 后**手动** `glEnable(GL_TEXTURE_2D)` 和 `glColor4f(1,1,1,1)`（"Angelica only tells the shader pipeline about glEnable / glDisable, and whatever is drawn next would come out white"）。
5. **只用 quads**（Angelica 的 tessellator 到处都按 quads；三角形用"重复一个角"的 quad），不用 QUAD_STRIP/TRIANGLE_FAN。⚠ `MachineHolo.beam()` 还在用 `GL_TRIANGLES`，未验证。
6. 文字 alpha 下限 5（字体渲染器把接近 0 当不透明）。
7. 登记 `FarDraw.add`，相对 `RenderManager.renderPos*` 画，每帧可能被调用多次；服务端按距离挑接收者的包用 `gate.Sight.near`（TE 描述包不用；营造的 `CampusNet`、书库的 `LIB_DELTA` 和 0.11.0 城市模块的 `UNIT_DELTA` 都用它；只给一个玩家的回答——0.12.0 光梯的 `LIFT_RIDE`——用 `NexusNet.tell` 直接发给他）。细节见 [gates.md](gates.md) §5。
8. 世界里的东西相对相机画、每处理器 try/finally 恢复状态；全息默认关、可切换。营造的**建造投影默认开**（`nexus.buildProjection`），和全息是两回事。
9. 一个处理器里有好几部分时，每部分单独失败：出错记一次日志、本次运行关掉这一部分、关掉没结束的 tessellator 批次（否则后面所有绘制都会被拖下去）；营造和档案馆的渲染器都这样做（[campus.md](campus.md) §11），0.11.0 的 `CityRender`、`EcoDomeRender` 也是（[city.md](city.md) §4.10），0.12.0 的 `DomeGeneratorRender`、`RootRender`、`LiftRender` 也是。
10. 大的东西按距离分档（0.11.0 起城市模块：`ModuleLook.tier` 的 NEAR / MID / FAR，每座楼近 ≤ 2,000 quads、中 ≤ 300、远 ≤ 64，CITY §1.3），预算写在渲染器的类注释里；一帧再画一遍（光门后面）也不许有跨帧计数器，所以上限按每座楼算、不按整帧算。0.12.0：画到 512 格的地标（穹顶发生器的穹顶）另有上限 400（`DomeLook.BUDGET`，测试按每个距离和半径钉住），光梯一道光束 8 quads 加光点（[nexus.md](nexus.md) §4.2）。

## 5. 图鉴与账本（`codex/`）

- `EchoLedger`：主世界 mapStorage `fluxecho_ledger`（`data/fluxecho_ledger.dat`）：`Teams[{Team, Done:{类别:[key]}}]`。`record(team,cat,key)` 只在新增时 `markDirty` + `EchoNet.ledgerChanged(team)`；`source(cat, fn)` 让模块自己保管的类别（目前只有 `aspect` ← `AspectMemory`）在快照时读取；没有世界时 `get()` 返回不保存的空账本。
- `Categories`：`bee, tree, butterfly, aspect, mob, crop, infusion, orb`；`register()` 同 id 替换，顺序即图鉴顺序；`Category{icon, name, keyOf(ItemStack)}`（只在客户端调用）。
- `EchoNet`（频道 `fluxecho`）：0 `MsgLedger`、1 `MsgHolo`、2 `MsgFlow`，都 S→C。登录时 `send(p)`；tick END 向 `CHANGED` 团队的在线玩家发；快照附带 `"@research"`（团队研究）。客户端 Netty 线程入 `INBOX`，tick START `ClientLedger.set`；断线清空。`flow` 发给 `Sight.near(…, 64)`；`holo` 发给 `range+8`（`HOLO_MANA=1`、`HOLO_MACHINE=2`）。
- `CodexTooltips`：`codex.tooltipHints` 开着时每个类别 `keyOf`，做过 `§b fluxecho.codex.done`，没做过 `§8 fluxecho.codex.todo.<cat>`。
- `GuiCodex`（300×210）、`ItemCodex`（`fluxecho:codex`，配方：书 + 末影珍珠 + 玻璃板，无序）。

## 6. NEI（`nei/`、`NEIFluxEchoConfig`）

- `loadConfig()`：替换 `@` 搜索（GT 机器按 metaName 前缀归模组；"NEI keeps only the last provider registered for a prefix, so FluxDepths ships the same filter"）→ 植物魔法在场 `SpringNei.register()` → `FluxRecipeHandler.registerAll(除 MANA_ECHO 外)` → `NexusModule.core != null` 时 `ManifestHandler.register()`。
- `FluxRecipeHandler`：id `fluxecho.machine.<key>`，166×100，每页 2 条；`HandlerInfo` 同时写进 `GuiRecipeTab.handlerMap` 和 `handlerAdderFromIMC`（"whichever comes first, the tab keeps its look"）；**transfer rect 在构造器里加，`loadTransferRects()` 留空**（0.7.0/0.7.1 的 null id bug，`CatalystLookupTest` 守着）；**必须有 `newInstance()`**（`HandlerCopyTest`）；输出 3 行以上靠右避开 NEI 按钮（`PageLayoutTest`）；带 NBT 的物品匹配要求 NBT 相同。
- `/fluxecho_nei`（`NeiCheck`，客户端命令）：对每个登记的页面检查数量、实际绘制前 4 条、按 U/R 能否反查、catalyst，聊天栏每页一行 + 汇总，详情写日志。

## 7. 任务书

- **生成**：`python quests/build_quests.py`（先删 `DefaultQuests` 再全部重写 + `index.json`）。ID = `uuid5(NS="6f1c2d6a-3a0b-4b51-9a57-7f1d2e9c4b10", key)` 拆两个 long + urlsafe base64；**key 不变 ID 就不变，玩家进度就不丢**（老线 key 沿用旧名就是为此）。
- 辅助：`quest(key, name, desc, icon, tasks, rewards=(), pre=(), main=True)`、`checkbox()`、`retrieval(*items, ignore_nbt)`、`give(...)`、`item(id, dmg, count, ore)`、`echo_machine(offset)`；`write_line(key, slug, name, desc, icon, placed=[(quest,(x,y))], requires=[modid])`。文本中文，`[note]`/`[warn]` 标记，`\n` 换行。
- **新增一条线**：写 `def echo_xxx():` 返回 `write_line("line/xxx", "FluxEchoXxx", ...)`，加进 `__main__` 的 `lines` 列表，重新运行。现有 10 条：LazyAE、FluxDepthsShards、Bees、Thaum、Blood、Prey、Crops、Mana、General、Nexus。
- **安装**（`quest/QuestInstaller`、`QuestPack`、`quest/bq/QuestInjector`）：要 `quests.install` 且 BetterQuesting 在场。
  - preInit：`config/betterquesting/DefaultQuests/QuestLinesOrder.txt` 存在时，把 `requires` 都在场的线写进 DefaultQuests（内容不同才写，先写临时文件再 move）、删本线目录里多出来的旧 json（`QuestPlan.stale`）、合并顺序文件（`QuestOrder.merge`：去 BOM 和空行、保留换行符、按 id 原位替换或追加；改前备份到 `config/fluxecho/backup/`）。
  - serverStarted：`QuestInjector.inject` 直接写进当前世界的任务数据库（新增 / 内容变了就地更新并保留进度 / 从线里掉出来且不属于别的线的任务删除），有变化时玩家首次登录收到 `fluxecho.quests.synced`。只动自己的线。
  - `QuestInjector` 是唯一允许引用 BetterQuesting 的类（`quest/bq/`）。

## 8. AE 集成（回响 ME 供应器，`ae/`）

- 方块 `fluxecho:echo_provider`（TE 同名），配方 `PEP/IHA/PCP`（铝板、末影珍珠、ME 接口、MV 外壳、分子装配室、MV 电路）。
- `TileEchoProvider`：只用 AE2 公开 API；`REQUIRE_CHANNEL`、空闲 1 AE/t；每 20 tick 或 dirty 时收集六面相邻 `MTEEchoMachine` 的 `echoPatterns()`（有变化发 `MENetworkCraftingPatternChange`），并把相邻机器输出槽的东西注入网络；`pushPattern` 用 `acceptInputs` 全放或全不放；`isBusy()` 恒 false。
- `EchoPatternDetails`：处理样板（不可合成、不可替换）；相等性来自 `EchoPattern`，多台机器的相同样板 AE 会分摊。
- 实现 `echoPatterns()` 的：`MTEBeeIncubator`、`MTESeedEcho`、`MTECrucibleEcho`、`MTEInfusionEcho`（猎物回响器没有样板，但输出照样收回）。

## 9. 网络频道一览

| 频道 | 所属 | 消息 |
|---|---|---|
| `fluxecho` | `codex/EchoNet` | 0 ledger、1 holo、2 flow（S→C） |
| `fluxecho_nx` | `nexus/NexusNet` | 0 ToServer（`ASK_MAP=0`/`START=1`/`CANCEL=2`）、1 ToClient（`MAP=0`、`RESULT=1`、`BUILD_FX=2`、`BUILD_STATE=3`（营造，`campus/CampusNet`）、`LIB_DELTA=4`（书库书位的增量，`TileLibrary`）、`UNIT_DELTA=5`（0.11.0：城市模块单元里样本的增量，`city/TileCityModule`，格式同 `LIB_DELTA`，city.md §4.7）、`LIFT_RIDE=6`（0.12.0：光梯的回答，只发给乘梯的玩家））；0.12.0 的 ToServer 还有 `LIFT=3`（请求乘梯；两个方向各自编号，和 ToClient 的 `BUILD_STATE=3` 不冲突），见 [nexus.md](nexus.md) §4.2、§7 |
| `fluxecho_gate` | `gate/GateNet` | 0 Transit（S→C） |
| `fluxlite` | FluxLite | 见 fluxlite.md §4（终端的 TERM_REQUEST/TERM_DATA 也在这里） |
| `fluxdepths` | FluxDepths `holo/HoloNet` | 0 全息（S→C） |

新增消息用新的 discriminator；handler 只入队，在主线程 tick 里处理。

TE 描述包和 GUI 的同步不占频道：0.10.1 没有新频道、新消息种类；中枢描述包的园区部分 `Cp` 加了 `Fb`、`Md`、`Fe`、`Te`，「建造」页的同步加了 `Rk/Ri/Mc/Fe/Fb/Md`、去掉了 0.10.0 的 `Sv`，回收箱界面用 MUI 的 `FakeSyncWidget` 按页发（`RecyclePage`，压缩后 < 24000 字节），见 [campus.md](campus.md) §6、§10。0.11.0 加了一种消息 `UNIT_DELTA=5`（上表），没有新频道；城市模块的描述包是 `P/Dk/Rv/Ep` + 全部样本（`BookPacket` 的 `B/Bi`，压缩后 < 28000 字节），通用模块窗口只用 `FakeSyncWidget` 同步状态和选中的单元（客户端经 `ASK_UNIT` 请求），见 [city.md](city.md) §4.6–4.9。0.12.0 加了两种消息 `LIFT=3`（C→S）和 `LIFT_RIDE=6`（S→C），没有新频道；中枢描述包加 `Gr`（相位），园区的 `Cp` 加 `Kr`（带高度的 keep-out）、`Md` 里加 `W`（没地方的原因），终端的中枢页加 `ring`（[nexus.md](nexus.md) §4、§7，[campus.md](campus.md) §10）。

## 10. 构建、发布、工具

- **依赖**（`dependencies.gradle`）：`api` GT5-Unofficial `5.09.51.482:dev`（带进 GTNHLib、StructureLib、ModularUI 1、NEI、IC2、AE2）；`implementation` VisualProspecting `1.4.8:dev`；`compileOnly`（`transitive=false`）Waila 1.8.14、Forestry 4.10.17、Thaumcraft 4.2.3.5、BloodMagic 1.7.52、Mobs-Info 0.5.6-GTNH、Botania 1.12.28-GTNH、BetterQuesting 3.7.15-GTNH；`testRuntimeOnly` lwjgl 2.9.4（NEI 测试要构造处理器）。
- **构建**：`build.gradle.kts` 只用 `com.gtnewhorizons.gtnhconvention` + JUnit 5；`gradle.properties`：Forge 10.13.4.1614、`enableModernJavaSyntax=jabel`（编到 J8 字节码；可用 `instanceof X x` 和 switch 箭头/表达式，**不用 record、文本块、var**）、`usesMixins=true`、`coreModClass=asm.FluxEchoCore`、`accessTransformersFile=fluxecho_at.cfg`、`autoUpdateBuildScript=false`。`.java-version` 25（CI temurin 25）。
- **格式**：spotless（eclipse 4.19，import 顺序 `java, javax, net, org, com`，去未用 import）；**提交前 `./gradlew --offline spotlessApply`**，`build` 会查格式。spotless 会把链式调用拆成多行（`.bus()\n.register(...)` 那种写法就是它造成的）。`.editorconfig`：UTF-8、LF、4 空格；`.lang/.md` 不去行尾空格。
- **命令**：`./gradlew --offline build`（约 25 秒，含测试）；`./gradlew --offline spotlessApply`。
- **CI**：`.github/workflows/build.yml`（push/PR 到 main 跑 `build`，上传 jar）；`release.yml`（push tag `X.Y.Z` 触发：`build` → 用 awk 取 CHANGELOG 里 `## X.Y.Z` 小节作发布说明 → `gh release create` 附 `fluxecho-X.Y.Z.jar`、`-dev`、`-sources`，`--verify-tag`）。版本号来自 git tag（不带 v）。
- **纹理生成器**（`tools/*.java`，单文件程序，仓库根目录 `java tools/X.java`，Java2D 画 PNG，动画同时写 `.mcmeta`，发光部分单独 `*_glow` 层）：

| 脚本 | 内容 |
|---|---|
| `Textures.java` | 顶面回响环、各机器正面（8 帧，frametime 2）、印记/图鉴/供应器/基因样本/汲取台/模块等 |
| `FluxTextures.java` | 通量外壳、GUI `gui/flux/*`、`effects/mote.png` |
| `SpringTextures.java` | 魔力回响泉方块面和 `gui/spring/*` |
| `GateTextures.java` | `blocks/gate/*` |
| `NexusTextures.java` | 构架、中枢、书库、通量物质、索引卡；0.11.0 的覆层锚点 `frame/anchor_top`、`anchor_side`（静态六角插板）和 `anchor_*_glow`（动画），模块核心 `blocks/module_core/<模块>/{front,front_on,side,top}`（`front_on` 动画；0.11.0 的 `eco_dome`，0.12.0 加 `dome_generator`（守护蓝、吊舱徽记）和 `root`（潮汐蓝、井徽记）；每做一座新模块就在这里加一组，`BlockModuleCore` 按 `ModuleCores.built()` 注册）；0.12.0 的物品 `items/phase_alloy`、`items/resonant_glass` |
| `CampusTextures.java` | 0.10.0 营造：`blocks/deck/*`、`blocks/fitting/*`、书架单元（`frame/shelf_niche`、`shelf_board`、`book_spine`）、`blocks/supply_port/*`；0.10.1 的回收箱 `blocks/recycle_bin/{top,side,front}`（补给口那种深蓝钢箱体，紫到青的进料口）；0.11.0 的样本座 `fitting/socket`、`socket_top`、`socket_glow`（凹槽在 11–15/16 高，和 `ModuleLook` 的常量一致）和池沿 `fitting/basin`、`basin_top`、`basin_glow`；0.12.0 的光梯 `fitting/lift`、`lift_top`（光圈）、`lift_glow`（动画）；深色科技调色板锁定 |
| `DepthsTextures.java` / `DepthsGuiTextures.java` | FluxDepths 的方块、物品、GUI |

- **资源**：`mcmod.info`（三个条目，后两个 `parent` 指向 fluxecho）；`assets/fluxecho/lang/{en_US,zh_CN}.lang`（行一一对应，按版本 `#` 分节）；`textures/{blocks,items,gui,effects}`；`quests/{index.json, DefaultQuests/...}`。
- **lang 键约定**：机器 `gt.blockmachines.fluxecho.<key>.name`；方块 `tile.fluxecho.<name>.name`；物品 `item.fluxecho.<name>.name`；创造栏 `itemGroup.fluxecho`；其余一律 `fluxecho.<area>.<…>`（`status.*`、`recipe.<key>`、`<key>.tip/.type/.gui_sample`、`nei.*`、`gui.*`、`holo.*`、`codex.*`、`waila.*`、`research.*`、`nexus.*`、`spring.*`、`gate.*`…）；文本里 `\n` 写字面量。

## 11. 测试（`src/test/java/com/fluxecho/`，JUnit 5，不需要 MC 运行时）

| 测试 | 守住什么 |
|---|---|
| `LangTest` | en/zh 键集合一致、无重复、占位符一致；代码里**字面写出**的键都存在（`EchoText.t/lines("x")` → `fluxecho.x`，`ChatComponentTranslation`/`translateToLocal("fluxecho.…")`）。**运行时拼出来的键查不出**，要自己核对。0.11.0 加 `everyChatMessageGetsItsArguments`：代码里字面写出的 `new ChatComponentTranslation("fluxecho.…", …)` 和 `UnitUse` 的 `tell(p, "x", …)`（= `fluxecho.city.unit.x`）给的参数不少于两份 lang 里的占位符（`%s`/`%d`，`%n$s` 按最大的 n）——1.7.10 在客户端格式化聊天时占位符比参数多会抛异常、客户端崩溃（0.11.0 开发中 `city.unit.full` 就少传了一个）；拼出来的键、经别的辅助方法发的消息它看不到 |
| `IsolationTest` | 读 `build/classes/java/main` 常量池：`forestry/`→`bees`、`thaumcraft/`→`thaumcraft`、`WayofTime/`→`blood`、`betterquesting/`→`quest/bq`、`com/kuba6000/mobsinfo/`→`mobs`、`ic2/`→`crops`、`vazkii/botania/`→`mana`、`appeng/`→`ae`。只看 `com/fluxecho/`（"The dev classpath always has Forestry (GT pulls it in), so a leak would never show in a dev run"） |
| `ConfigTest` | `older()` 版本比较；0.10.1 的默认值（`buildClearAll` 开、`buildFillDepth` 4）和 `bumpsFillDepth` |
| `core/MachineIdTest` | offset 唯一且 < 40；不碰 FluxDepths |
| `core/EchoRecipesTest` | `forForge` |
| `nei/CatalystLookupTest`、`HandlerCopyTest`、`PageLayoutTest` | transfer rect、`newInstance()`、输出不压 NEI 按钮 |
| `quest/QuestPackTest` | 每条线文件齐全、一个 QuestLine.json、任务和摆放一致 |
| `logic/QuestOrderTest`、`QuestPlanTest` | 顺序文件合并、stale 文件 |
| `logic/GtPatternTest` | 表驱动的配方交给 GT 时没有小写（工具）字母，改名后原料和数量不变 |
| `logic/*Test` | 各纯逻辑类（见各子系统手册；营造和档案馆的 `MixTest`、`PartRecipesTest`、`CampusPlanTest`、`PavingTest`、`ArchiveShapeTest`、`StairWalkTest`、`LibraryUnitsTest`、`UnitLookTest`、`TerrainRuleTest`、`BuildPlanTest`、`BuildLedgerTest`、`BuildStateTest`、`BuildPaceTest`、`FxCodecTest`、`LiftRuleTest`、`GhostCellsTest`、`LaunchPhaseTest`、`SpineColourTest`、`BlueprintTest`，0.10.1 的 `RemovalTest`、`RecycleSlotsTest`、`EchoCommandTest` 见 [campus.md](campus.md) §15；0.11.0 回响之城的 `ShapeKitTest`、`SitePlanTest`、`UnitLayoutTest`、`EcoDomeShapeTest`、`EcoDomeWalkTest`、`ModulePlanTest`、`PartsTest`、`ModuleLookTest`、`ModuleMapTest`、`UnitBrowseTest` 见 [city.md](city.md) §8；0.12.0 的 `DomeGeneratorShapeTest`、`ZenithPlanTest`、`DomeLookTest`、`RootShapeTest`、`RootPlanTest`、`RootLookTest`、`TideTest`、`XRayTest`、`XRayAirTest`、`AxisModulesLookTest` 见 [city.md](city.md) §8，`GrowPlanTest`、`LiftShaftTest`、`LiftInputTest`、`LiftLookTest` 见 [nexus.md](nexus.md) §4.1、§4.2、§10） |
| `campus/CampusEngineTest` | 建造任务的存读、计划重算、飞行退款、换场地、修复、受阻格计数、特效批次和分页、核心带着的园区、逐格的拆除 EU、跳过受阻的同步；0.10.1 的整修园区（让开建好模块的整块场地、平整完才记下）、回收箱的步骤、免费建造、立即建造的结束、开始时重取 keep-out、最后补一遍、模块列表（要用非逻辑类，所以不在 `logic`） |
| `campus/RecycleStoreTest`、`RecyclePageTest`、`DropCaptureTest` | 0.10.1：回收箱存储（合并、取出、排序、按种类和字节的软上限、存读）、拆核心时的暂存（`RecycleOverflow`）和放回、回收箱界面的分页（< 24000 字节）和点击找回、拆方块时的物品捕获（正在加载的区块冒出来的不接） |
| `campus/CampusTilesTest` | 0.10.1：回收箱和补给口在中枢区块卸载、重新加载后只用世界里现在的中枢 tile（`TileSupplyPort.loadedNexus`）；漏斗取走最后一件又放回不丢。用测试里的 `campus/FakeWorld`：不跑 `World` 构造器分配出来的服务端世界，只答区块加载、格子里的 tile 和时间（要更多就往里加，别在测试里起 MC） |
| `library/ArchiveMapTest`、`LibraryPacketTest`、`LibraryVaultTest`、`TileLibraryUnitsTest`、`HallRefillTest` | 档案馆平面图、书的包不超过 28000 字节、队伍书库仓库、书库 tile 的坐标和形状、两种形状第一次成型都从仓库取一次书 |
| `city/CityGuiTest`、`TileEcoDomeTest`、`TileDomeGeneratorTest`、`TileRootTest` | 0.12.0 的两个：穹顶发生器和潮汐井的结构映射、控制器和单元、腿和环能穿过、先系泊相位 IV 起才试浮空、引擎建好的框架就是计划的、潮汐井的运行时 lang 键。0.11.0：通用模块窗口的格子每次按显示的单元找槽、选择检查、超过槽数的格子什么都不做、三页的布局；生态穹 tile 的结构映射同 StructureLib、每个样本座是自己的槽、树干隐藏但实心、`local` 是 `worldOf` 的逆（[city.md](city.md) §8） |

FluxLite、FluxDepths 另有 19 个测试类；共 117 个测试类、732 个测试（0.12.0；0.11.0 是 101 个类、623 个；0.10.1 是 88 个类、536 个；0.10.0 是 81 个类、484 个）。
