# 开发手册 · 营造：园区、自动建造、投影、回响档案馆（0.10.0 起）

> 改 `campus/`、`campus/client/`、档案馆（`library/` 里 0.10.0 的部分）、`nexus/ItemBlockNexusCore`，或者要给中枢加一个由它自己建造的模块之前读这一篇。中枢本体、研究、显化、多方块框架、0.9.2 书库大厅见 [nexus.md](nexus.md)；画在世界里的东西先守 [gates.md](gates.md) §5；总览见 `CLAUDE.md`。
> 路径相对 `src/main/java/com/fluxecho/`。`EchoText.t("x")` 对应 lang 键 `fluxecho.x`。文中数字和键都按 0.10.0 的代码核对过。

## 0. 定位

0.10.0「营造」让通量中枢自己盖房子：玩家把中枢核心放在地面上 → 核心抬到眼睛高度，中枢在原地**勘测**、出**投影**（要清掉的地形、要立起来的本体和广场）→ 成员在中枢界面的「建造」页按**开始**（之前什么都不动）→ 中枢清场、平整整片园区、按相位 I 立起自己、铺广场/环道/大门、立塔柱和补给口。研究了回响书库、环上还有空位时，中枢在模块位上投影出**回响档案馆**（27×35×16 的两层长厅书库）→ 成员把材料交给中枢（「补给」、投入槽、补给口）→ 方块一块块从晶核飞出、打印、落位。园区只有一层地面（Y0），深色科技（中枢的深蓝钢 + 细青光线），布局按种子带一点不规整（侧叶、深色团、Voronoi 缝）。档案馆能走进去：78 个书架、702 本书，书架整个一起用。

用户定的（BLUEPRINT 第六节 17）：书库选「长厅」（两层、回廊、旋转楼梯）；书架整组为单位；建立时平整整片园区（只花 EU）；深色科技；流程是 放核心 → 自建 → 研究 → 投影 → 供料 → 自动放置 + 特效。由此定下的规矩：
- **一切先勘测、出投影，成员按「开始」才动工**（取代"放下就算同意"）；旧中枢（0.9.2 存档、潜行放的核心）什么都不动，直到成员按「铺设广场」（例外：拆下后带着园区放回原处的核心怎么放都接着用原来的园区，§8）。
- **建造投影默认开**（`buildProjection=full`），和状态全息是两回事；全息照旧默认关。
- 中枢的建造从不拆玩家的东西：拿不准就算 BLOCKED（标红、暂停或跳过），见 §5。

## 1. 文件

| 文件 | 作用 |
|---|---|
| `campus/CampusModule` | 注册 `fluxecho:deck`、`fluxecho:fitting`、`fluxecho:supply_port`（TE 同名）；postInit 注册地板、配件、补给口的配方（`defaultRecipes && nexusEnabled`，每条单独 try/catch）；`init()` 空着 |
| `campus/BlockDeck`、`ItemBlockDeck` | 地板/墙板，12 个 meta（§3） |
| `campus/BlockFitting`、`ItemBlockFitting` | 配件，7 个 meta；`onUse(Use)` 钩子（档案馆书架用）、`railArm`、`box`、`full` |
| `campus/BlockSupplyPort`（物品 `BlockSupplyPort.ItemPort`）、`TileSupplyPort` | 补给口（§6） |
| `campus/PartBlocks` | 部件码 ↔ 方块/meta/物品/配方原料：**唯一**把部件码变成 `Block` 的地方；`code(Block, meta)` 反查；`register(Recipe)` 注册配方 |
| `campus/Campus` | 一座中枢的园区：legacy/active、当前任务和队列、账本、投入槽、收获、模块位记录、GUI/核心/补给口调用的入口（都返回 `fluxecho.build.*` 键） |
| `campus/BuildJob` | 一项任务：任务键和计划键、状态、阶段、done/skipped/flying 位集、飞行中的格子、计数、keep-out；NBT |
| `campus/Builder` | 引擎：勘测、清场与平整、发射、落地、等结构成型；`QUIET` |
| `campus/Terrain` | 从世界填 `TerrainRule.Probe`（自然地形、玩家建的、GT 矿和矿辞的矿、我们自己的方块和补给口、它们是不是散落的） |
| `campus/ModuleSpec`、`ModuleSpecs` | 中枢能自己建的模块（书库的在 `LibraryModule.init` 登记） |
| `campus/CampusRegistry` | `WorldSavedData` `fluxecho_campus`：每座园区的维度、中心、半径；送过免费补给口的位置 |
| `campus/FxBatch`、`CampusNet` | 四 tick 一批（满一页就当 tick 发）的发射/清除特效，按页发包；`BUILD_FX`、`BUILD_STATE` 发包 |
| `campus/client/CampusClient` | 客户端注册入口（`ClientProxy.init` 调，`FrameClient` 之后） |
| `campus/client/DeckRender`、`FittingRender`、`PartDraw`、`Neighbours` | 区块网格 ISBRH 和共用画法 |
| `campus/client/BuildClient` | 客户端的建造数据：进度、飞行、清除、计划缓存（普通代码可以引用，只有内部 `Hooks` 是 SideOnly） |
| `campus/client/BuildScan`、`BuildRender` | 投影：客户端 tick 里扫描、选格；`RenderWorldLast` 里只画 |
| `campus/client/BuildFx` | 发射弧线、货物方块、打印、落地闪光；清除的粒子和声音 |
| `campus/client/Masterplan` | 园区总图、放核心的预览 |
| `logic/Mix`、`Parts`、`PartRecipes`、`GtPattern` | 种子哈希；部件码；配方表（合成 = 成本）；GT 工具字母 |
| `logic/CampusPlan`、`BuildPlan`、`BuildLedger`、`BuildState`、`BuildPace`、`TerrainRule`、`FxCodec`、`LiftRule` | 园区布局与铺砖；建造计划；账本；状态机；节奏和飞行时间；地形判定；格子打包；核心抬升 |
| `logic/GhostCells`、`LaunchPhase` | 投影的选格/面/棱/最低层；发射时间线和弧线 |
| `logic/ArchiveShape`、`LibraryUnits`、`UnitLook`、`SpineColour`、`Blueprint`（`cell`/`point`） | 档案馆形状；书架单元；书架外观规则；书脊颜色；蓝图反算 |
| `nexus/ItemBlockNexusCore`、`BlockNexusCore` | 核心抬升放置和拒绝；拆核心带走余额、显化和园区，放回原处园区接着用（§8） |
| `nexus/TileNexus`、`TileMultiblock`、`NexusGui`、`client/NexusScreen`、`NexusNet`、`NexusWaila`、`TerminalView`、`core/Directory` | 园区接进中枢的地方：tick 顺序、按场地停靠、多形状、「建造」页、包、Waila、终端、状态（[nexus.md](nexus.md)） |
| `library/TileLibrary`、`Units`、`ArchiveGui`、`ArchiveMap`、`BookPacket`、`BookSlot`、`LibraryVault`、`LibraryGui`、`BlockLibraryCore` | 档案馆的服务端和界面（§12） |
| `library/client/ArchiveRender`、`ArchiveLook`、`ArchiveFrame`、`ArchiveScreen`、`LibraryBooks`、`LibraryRender` | 档案馆的画面（`LibraryRender` 按形状分派） |
| `frame/client/ShelfUnits`、`FrameClient` | 书格画成书架单元的一格（区块网格）；登记 `shelf_board` 图标 |
| `tools/CampusTextures.java` | 地板、配件、书架单元、补给口的贴图（`java tools/CampusTextures.java`） |

## 2. 园区坐标与场地（`CampusPlan`）

- **坐标**：中心 `C = TileNexus.centre()`（底座中心），`Y0 = C.y` 是底座那层，也是铺砖那层；人站在 Y0+1，控制器在 Y0+2。本地坐标 `A` 沿中枢正面向前、`R` 向右（俯视顺时针）：`dx = A·fx − R·fz`，`dz = A·fz + R·fx`（和 `RingSlots.offset` 一样），`toWorld`/`toLocal` 互逆；`key(a, r)` 打包成 long。
- **八角** `oct(a, r, Rmax, K)`：`|A| ≤ Rmax && |R| ≤ Rmax && |A|+|R| ≤ K`。底座 `oct(5,7)`、格栅带 `oct(6,8)`、广场 `oct(16,22)`（`FORUM_R/K`）、环道 `oct(19,26)`（`RING_R/K`）、平整圆盘 `oct(64,90)`（`GRADE_R/K`；`gradeArea()` = 圆盘去掉底座，13580 格，和种子无关）。
- **场地**：场地 k 在正前方顺时针 45°·k。0 = 大门；`HALL_SITES = {4, 2, 6}` 是模块位（偏好顺序：正后、右、左）；1/3/5/7 是对角预留（`diagonalCentre` = (±34, ±34)，`DIAGONAL_SIZE` 23）。`moduleCentre(site, depth)` = 场地轴 × (`HALL_FRONT` 24 + (depth−1)/2)：大厅前排在径向 24，档案馆（depth 35）的中心在 41。`hallSiteAt` 两轴容差各 ±3；`inHall(site, w, d, a, r)` 是占地判定。
- **大门**：A 17–36、|R| ≤ 3，节点 A 25–29 和远端门槛 A 34–36 放宽到 |R| ≤ 5。四根塔柱 (27, ±5)、(35, ±5)；补给口 (9, 5)，站在 Y0+1 的地砖上。
- **铺砖**（移植 `spec/gen/synth_campus.py` 的 `tile()`，按优先级）：光井 → 广场肋线（轴线和对角线，Chebyshev ≤ 10 实线、外面隔格）LIT → 格栅带 GRATE → 大门中线偶数格 LIT → 大厅门槛（径向 23、|s| ≤ 3）LIT、大厅脊线（径向 20–23 偶数格）LIT、门前箭头（顶点径向 22、臂长 4）CHEVRON → 大门两边 TRIM → apron 内圈（阴影缝）和外缘勾边 TRIM → Voronoi 缝 TRIM（种子点在每 6 格一圈的极坐标环上）→ 侧叶里的深色团（0.55）→ 深色团（3×3，概率 `clamp((r−12)/140, 0, 0.35)`）→ 其余 DECK。
  - 光井 3×3：广场 (±9, ±9) 四个候选各 75% 保留，再加两个侧叶各一个；`wells()` 的格子在 Y0 是空气、Y0−1 是 WELL。
  - 侧叶：四条对角线里按种子挑两条（`lobeSites()`），那一段环道多出深色团和一个光井——打破对称。
  - 广场开放边按 0.45 成 2×2 掉格（离大门/环道 3 格内不掉），只对 establish 集合（广场 ∪ 大门 ∪ 环道）算。
  - `siteFloor(site, w, d)`：前庭（径向 20–23，半宽 8, 8, 7, 6）+ apron（占地外扩 2 格，后角超出 3 的切掉，内圈 TRIM）+ 脊线 + 门槛 + 箭头。**只增不改**：从不碰 establish 地面、底座、环道以内和大门的格子，铺好的砖不会被重新评估。
- **比例**（`PavingTest`）：establish 地面 DECK ≥ 45%、TRIM 12–35%、LIT ≤ 9%；一座大厅的地面 296 格、约一半 TRIM（apron 内圈 115 格按规则就是 TRIM）；三座大厅都建好后全园区 TRIM ≤ 45%，DECK、LIT 仍在界内。
- **确定性**：布局只看种子 `Mix.seed(控制器 x, y, z)` 和 `StrictMath`，**不读配置**；服务端和客户端各自算出同一份。

## 3. 部件与方块

**部件码**（`logic/Parts`，计划、成本、包里只出现这个数，不出现 `Block`）：

| 码 | 是什么 |
|---|---|
| 0–15 `FRAME + meta` | 构架 `fluxecho:frame`（`FR_BASE`…`FR_CONSOLE`，0–8 在用） |
| 16–31 `DECK + meta` | 地板/墙板 `fluxecho:deck`（`D_*`，0–11 在用） |
| 32–47 `FITTING + meta` | 配件 `fluxecho:fitting`（`F_*`，0–6 在用） |
| 48 `LIBRARY_CORE`、49 `SUPPLY_PORT` | 书库核心（任意 meta）、补给口 |
| 50 `GRASS`、51 `DIRT`、63 `AIR` | 平整填坑用的草和泥土；必须变成空气的格子 |

**地板/墙板 `fluxecho:deck`**（石质、硬度 3、抗性 15、镐 1，不刷怪；`DeckRender`：先 `renderStandardBlock`，再关 AO 用 `0xF000F0` 画 `_glow` 层；LIT 的侧面用一条水平光线的 `lit_side`、底面不发光）：

| meta | 名（`tile.fluxecho.deck.<n>.name`） | 光 | 配方（产量） | 用在 |
|---|---|---|---|---|
| 0 `deck` | 地砖 | 0 | ×16 `SSS/SPS/SSS`（`ore:stone`、钢板） | 园区主地面、档案馆地面和回廊楼板 |
| 1 `trim` | 地缝钢 | 0 | ×8 `DDD/DcD/DDD`（地砖、煤粉） | 勾边、缝、apron 内圈 |
| 2 `lit` | 光带地砖 | 9 | ×8 `DgD/DDD/DgD`（地砖、荧石粉） | 肋线、门槛、脊线、回廊边 |
| 3 `grate` | 格栅地砖 | 0 | ×8 `DDD/DbD/DDD`（地砖、铁栏杆） | 底座外一圈 |
| 4 `dark` | 深色地砖 | 0 | ×8 `DTD/TDT/DTD`（地砖、地缝钢） | 深色团 |
| 5 `skirt` | 踢脚光带 | 6 | ×8 `TTT/TgT/TTT`（地缝钢、荧石粉） | 目前没有计划用它，只能手放 |
| 6 `chevron` | 箭头地砖 | 7 | ×8 `LTL/TLT/LTL`（光带地砖、地缝钢） | 门前箭头 |
| 7 `well` | 光井 | 12 | ×4 `L L/ G /L L`（光带地砖、荧石块） | 光井底（Y0−1） |
| 8 `panel` | 墙板 | 0 | ×16 `BBB/BPB/BBB`（任意石砖、钢板） | 档案馆墙、屋顶、楼梯整级 |
| 9 `panel_lit` | 光缝墙板 | 8 | ×8 `PPP/gPg/PPP`（墙板、荧石粉） | 壁柱 |
| 10 `panel_dark` | 深色墙裙 | 4 | ×8 `PPP/PcP/PPP`（墙板、煤粉） | 墙脚一圈 |
| 11 `cornice` | 光檐 | 10 | ×8 `PPP/gSg/PPP`（墙板、不锈钢板、荧石粉） | y12 檐口、门楣 |

**配件 `fluxecho:fitting`**（铁质、硬度 3、抗性 15、镐 1、透光 0、不刷怪；`FittingRender`，栏杆臂由 `BlockFitting.railArm` 决定，画法和碰撞用同一条规则）：

| meta | 名 | 光 | 形状 | 配方（产量） |
|---|---|---|---|---|
| 0 `post` | 书架立柱 | 6 | 2–14/16 立柱，整高 | ×8 `PrP/PgP/PrP`（墙板、钢杆、荧石粉） |
| 1 `plinth` | 书架底座 | 9 | 整块 | ×8 `KKK/KgK/KKK`（深色墙裙、荧石粉） |
| 2 `crown` | 书架顶檐 | 10 | 整块 | ×8 `LLL/LPL/LLL`（光缝墙板、钢板） |
| 3 `rail` | 栏杆 | 6 | 中柱 6–10/16 + 连向栏杆/立柱/不透明方块的臂，碰撞 1.5 高 | ×8 `r r/rGr/r r`（钢杆、`ore:paneGlass`） |
| 4 `tread` | 踏板 | 7 | 下半砖 | ×8 `PgP/PPP`（墙板、荧石粉） |
| 5 `glaze` | 窗格 | 0 | 整块，pass 0 镂空玻璃（不是半透明 pass） | ×8 `GGG/GPG/GGG`（`ore:blockGlass`、钢板） |
| 6 `pedestal` | 全息底座 | 8 | 3–13/16 宽、0–12/16 高 | ×4 ` L /LBL/ L `（光缝墙板、导能基座） |

**补给口 `fluxecho:supply_port`**（铁质、硬度 5、抗性 10、镐 1；朝向存在 TE 里，方块 meta 保持 0）：×1 `PCP/PHP/PPP`（钢板、通量结晶、漏斗）。

**`PartRecipes`：一张表同时管合成和建造成本**。每条：部件、产量、1–3 行图案、键 → 原料 spec（`ore:<名>`、`item:<mod>:<名>[@meta]`（32767 = 任意）、`part:<码>`；解析同 `ResearchTree.Cost.parse`，不带数量）。`cost(part)` 把 `part:` 递归展开成原料，单位 `UNIT = 65536` = 1/65536 件；每层都必须整除（类加载时检查，错了直接抛）。例：地砖 = `{ore:stone: 32768, ore:plateSteel: 4096}`（半块石头 + 1/16 钢板）。`partOnly`：晶核座（要 GT HV 外壳，表里写不出来，`FrameModule` 手写配方）、书库核心、补给口——建造时只收成品。构架部件也从这张表注册（0.10.0 起**回响书格 ×32**，石砖接受任意变种）。
- **GT 工具字母的坑**（`GtPattern`）：`GTModHandler.addCraftingRecipe` 把图案里的小写 `bcdfhijkmprswx` 当成合成工具（c 撬棍、r 软锤……），把工具追加到键后面，Forge 的有序配方保留同一个键的最后一个值——原料被工具顶掉，GT 还报成功。`GtPattern.safe` 把**所有**小写键换成配方里没用过的大写字母（行和键一起改、键序不变），`PartBlocks.register` 在交给 `EchoRecipes.shaped` 之前调用。`GtPatternTest` 守着。
- 三个方块和构架的 `onBlockAdded/breakBlock` 只在 `!Builder.QUIET` 时调 `FrameEvents.changed`（构架从 `onBlockPlacedBy` 挪到了 `onBlockAdded`，投影仪和建造放的也算）；建造期间由引擎每阶段结束统一通知一次。
- 贴图：`tools/CampusTextures.java` 生成 `blocks/deck/<n>`（有光的加 `_glow`，8 帧 frametime 3）、`lit_side`、`blocks/fitting/<n>` + `_top` + `_glow`、`blocks/frame/shelf_niche|shelf_board|book_spine`、`blocks/supply_port/{top,side,front}`。调色板锁定：地砖 `#3E4C58`、地缝钢 `#101C28`、光 `#C8F8FF` 在 `#1C3446` 上、深色 `#2C3842`、墙板 `#45525E`、钢 `#2C5470`、青 `#4FE3FF`、书库紫 `#8A5CFF`。

## 4. 建造计划（`BuildPlan`）

计划是纯数据：一项任务按顺序放的每一格（世界坐标）+ 开工前要清的柱子，只由任务的输入算出（`CampusPlan`、中心、正面、模块的场地、establish/forum 的 keep-out 盒），**不读配置、不读世界**。任务只存计划键和 keep-out，计划每次重算（`BuildJob.planFor`），客户端用同样的输入算出同一份投影。

- **Step**：`x, y, z, part, stage, group, kind`；`HARD`（结构检查会看的格子）、`SOFT`（地砖、光井、塔柱、补给口、雨棚）、`AIR`（门洞、光井顶，必须变空气）。`group` = 书架单元的序号（15 格：3 底座 + 9 书格 + 3 顶檐，连续排列，一起发射一起落地），其余 `NO_GROUP`。**Column**：`x, z, yFrom, yTo, grade`，从上往下清自然方块，`grade` 的柱子清完再填坑。一个阶段内：先按层从下往上，再从中枢往外（`RIPPLE`），沿任务轴的镜像格相邻。不会有重复格（测试）。
- **任务键**：`establish`、`forum`、`module:<模块>@<场地>`（`moduleKey`，如 `module:library@4`）；修复是 `repair:establish`、`repair:<场地>`，计划键是被修的那个。
- **establish**（`E_CLEAR, E_CORE, E_FORM, E_FLOOR, E_FIXTURE`）：CLEAR = 底座上方的柱子（从 Y0+1，控制器那一柱只清它上面）+ `gradeArea()` 的平整柱（Y0+1 到 Y0+`CLEAR_UP` 24，`grade`；引擎再按 `buildClearHeight` 截短）；CORE = 相位 I 除控制器外的每一格（控制台座先放，再导能辐线由内向外、底座按八角距离、肋柱/导能柱/晶核座/环段从下往上，每层从正前方顺时针）；FORM 没有步骤（等中枢成型）；FLOOR = `establishFloor()` 由内向外，光井是 Y0 空气 + Y0−1 WELL；FIXTURE = 四根塔柱（两节构架支柱 + 一节导能柱，Y0+1 到 Y0+3）再补给口（Y0+1）。
- **forum**：同上但没有中枢本体（给 0.9.2 的中枢）。
- **keep-out**：`moduleKeepOut(centre)` = 模块地基中心 ±`MODULE_KEEP_OUT`(7)（13×13 地基外扩一格）。盒子里的平整柱、地砖（含光井）、塔柱、补给口都去掉；底座柱和中枢本体从不去掉（所以放核心要拒绝压在模块上，§8）。任务把盒子存成 `Ko` 并同步，客户端必须用同一份，否则投影和服务端不一致。
- **archive**（`M_CLEAR … M_COMMISSION` 共 9 段）：控制器在场地轴上、前排径向 24、Y0+`CTRL_Y`(2)，朝向中枢（场地轴取反，`archiveController` 返回 `{x, y, z, facing}`），每格用 `Blueprint.world` 摆，所以 HARD 格正好是结构检查看的格子（测试 4 个朝向 × 3 个场地）。CLEAR = 占地外扩 `ARCHIVE_MARGIN`(1) 的盒子，Y0+1 到 Y0+`HEIGHT`+`ARCHIVE_HEADROOM`(=Y0+18)，**不按配置截短**（山不能留在房子里）；FLOOR = `siteFloor` + 室内软地面（地砖，轴线上 z1–z28 偶数格 LIT 脊线）；SHELL = y0 的模块地基、y1–y5 外墙（墙板、壁柱、窗格、墙裙、门间立柱、门楣）和所有门洞空气格；DECK = y6 一层（回廊楼板、光边、平台、墙的 y6 带）、栏杆、两座旋转楼梯；UPPER = y7–y11 外墙；ROOF = y12–y15；FIT = 书架立柱，再按 `LibraryUnits.UNITS` 顺序每个书架一组 15 格，再全息底座、讲台、阅读台；DECOR = 门口雨棚（`ArchiveShape.decor()`，14 格悬浮环段）；COMMISSION = 控制器（`LIBRARY_CORE`），最后一步。
- **规模**（示例种子；地面砖数随种子差几块）：establish 1464 步（CORE 134、FLOOR 1317、FIXTURE 13）、13677 根柱子；forum 1330 步；档案馆 6237 步（FLOOR 1120、SHELL 744、DECK 808、UPPER 600、ROOF 1119、FIT 1831、DECOR 14、COMMISSION 1）、1073 根柱子。
- **版本**：`BuildJob.V = 1`。**改了 `BuildPlan`、`CampusPlan` 或模块形状里任何会改变任务放什么的东西，就把 V 加一**：旧任务读进来时进度位集作废、飞行中的格子按记下的部件退款、已过清场阶段的不再清场（`afterLoad`）。

## 5. 服务端引擎

**入口和 tick**（`TileNexus.serverTick` 第一件事，成型与否都跑）：`if (nexusEnabled && campusEnabled) campus.tick()`。`Campus.tick`：出过异常就不再跑（记一次日志、清 `QUIET`，直到中枢重新加载）；首 tick `afterLoad`（登记 `CampusRegistry`、`dropStale(2·GRADE_R)`、旧版本任务重开、半途的任务重新勘测）；legacy 只发提示；active：收投入槽 → 放回原处的园区到了 `Rc` 那一 tick 就 `afterResume`（§8）→ 每 20 tick `watchStructures`（记中枢/模块散架的时间，模块没了就忘掉场地）→ 每 100 tick `autoJobs` → 没有当前任务就取队首 → `builder.tick(job)` → 结束、没有飞行中的格子、过了 `DONE_LINGER`(100 tick，让客户端看到完成和完成光环) 就换下一个 → `flush`。

**`Builder.tick`**：先落地到期的飞行；然后按状态：SURVEY 勘测；WAITING 直接转 BUILDING；BUILDING 补预算、跑一遍、判断卡住；PAUSED 补预算、`tryResume`。有耗电就 `ServerEvents.addTeamTick`。
- **勘测**：每 tick `SURVEY_UNITS` = 4096 单位，完整检查一格 8，已完成/已跳过/飞行中的步骤和清场柱里的空格 1（约 512 次完整检查）；只用 `blockExists`，**不加载不生成区块**；已经对的格子直接记 done，统计要清的方块（`natural`：判 CLEAR 的步骤格 + 清场柱里 CLEAR/REPLACE 的格子，开始前在「建造」页显示为「将清理」）、受阻格子、未加载格子和平整要填的格子；完成后 `BuildState.surveyed` → PROJECTING（记 `projectedAt`）或 WAITING/PAUSED。
- **清场**（阶段 0）：按顺序逐柱从上往下，每 tick `CLEAR_UNITS` 4096（同上计价）；每拆一格花一份 `buildClearPerTick` 预算和 `buildEuPerClear` EU；平整柱清完再填坑（下面）。判定用 `forClear`（`Builder.clearVerdict`）：BLOCKED 的格子只标红、留在原地，**清场不因此暂停**；模块任务的 REPLACE（散落的我们的方块）拆掉后按部件记进账本，不进收获。
- **放置**：在当前阶段第一个未完成的步骤之后 `WINDOW`=256 步里找；已经对的格子免费记 done（每 tick ≤ 64）；BLOCKED 的 SOFT 格或开着「跳过受阻」就跳过，否则标红并让任务卡住；其余在飞行数 < `MAX_FLIGHTS`(256) 且预算够时发射。书架单元整组：未完成的格子一起付钱一起发射，飞行数 + 组大小 ≤ 256 且 `BuildPace.canGroup` 才发。
- **发射**（`launch`，全有或全无）：账本逐格扣（先成品后原料，§6），EU 逐格算（`Builder.stepEu`）：每格 `buildEuPerBlock`，这一格要先拆东西时再加 `buildEuPerClear`（书架整组发射时只有要拆的那几格加收），AIR/草/泥土的格子只算 `buildEuPerClear`；从队伍无线电网一次扣（`GTWirelessBackend.INSTANCE.add(team, −eu)`），扣不到就全部退款（缺电）。飞行时间 `BuildPace.flightTicks` = `clamp(12 + 距离/6, 14, 30)` tick（距离从晶核 Y0+4.5 算）。`Flight` 记下步骤、落地 tick、是否用了成品、是否扣过账、部件和成本倍率，退款按自己记的来。establish/forum 的补给口：账本里没有补给口时**免费送一次**（同一位置只送一次，`CampusRegistry.portGifted`；修复从不送）。
- **落地**（`landOne`）：再探一次。已经对了 → 退款记 done；BLOCKED → 退款（SOFT/跳过受阻时跳过）；格子里有生物（放的是实心方块）→ 等 10 tick × 3 次，之后地砖让人（跳过），结构格每 40 tick 再等、最多再 30 次（约 1 分钟）后跳过（成型检查会再试）；要拆的是矿（含矿辞的矿）或原木而收获放不下 → 每 20 tick 再试并通知成员一次（`spoils_full`）。然后设 `QUIET`：先拆（假玩家发 `BlockEvent.BreakEvent`，被取消 = 受保护；拆的是我们自己的错部件 → 账本 `addPart` 1:1；矿和原木的掉落在拆之前算好放进收获），再放（先 `setBlock`，再用 `BlockSnapshot` + `ForgeEventFactory.onPlayerBlockPlace` 发放置事件，被取消就 `restoringBlockSnapshots` 还原）。受保护：结构格让任务暂停 PROTECTED，SOFT 格跳过。放下的是模块核心 → `commission`（`place(owner, name, facing)`、`FrameEvents.changed`、`recordSite`）；是补给口 → `TileSupplyPort.linkAt`。
- **阶段结束**：在锚点（中枢控制器或模块控制器）`FrameEvents.changed` 一次 + `nexus.frameChanged()`。
- **等成型**（`waitForm`：establish 的 E_FORM，模块任务最后一段之后）：第一次进来先 `recommission`——**已经站在控制器格上的模块核心**（手放的、中枢没加载时放的）没被 commission 过，可能朝向不对，转成朝向中枢（有玩家主人就保留，没有就给中枢主人），再 `frameChanged`；`FORM_WAIT` 200 tick 没成型就 `recheck`（done 的 HARD 格现在不对的重做，跳过的格子现在能放的重试）并回到那一段；`FORM_TRIES` 3 轮后暂停 INCOMPLETE。成型 → 下一段或完成（通知成员 `done`）。
- **平整填坑**：只填露天的坑——Y0+1 到清场高度之间没有挡路的方块（自己的步骤不算；勘测时将被清掉的不算），且高度图 ≤ 清场高度 + 1；坑 = 从 Y0 往下连续的空格（空气或可替换、不是岩浆，水算），最深 `buildFillDepth`；Y0 本身是实地就不填（地下室、洞穴、屋顶下的房间都不动）。Y0 放草（上面是实心就放泥土），下面放泥土；别的园区里、受保护盒子里不填；格子里有生物等 30 tick 后留着不填。只花 EU（`buildEuPerClear`），计 `toFill`/`filled`。

**地形判定**（`Terrain` 填 `Probe`，`TerrainRule` 判）：
- `forBuild(p, module)`（步骤自己的格子）：已是目标 → ALREADY；空气/可替换/流体（不是岩浆）→ PLACE；岩浆、不可破坏、别的园区、我们的方块但在别的结构里 → BLOCKED；GT 矿 → CLEAR；**模块任务**遇到散落的补给口 → REPLACE；其他有 TE 的 → BLOCKED；我们自己的错部件 → REPLACE；玩家建的 → BLOCKED；自然 → CLEAR；其余 BLOCKED。`forClear(p, module)`（清场柱，格子最后必须是空的）：空气或非流体的可替换 → ALREADY；不可破坏、别的园区、别的结构里的我们的方块 → BLOCKED；GT 矿 → CLEAR；**模块任务**遇到散落的我们的方块（构架、地板、配件、补给口）→ REPLACE（拆掉、按部件记账）；TE、我们的方块、玩家建的、岩浆 → BLOCKED；自然（含水）→ CLEAR；其余 BLOCKED。`module` = 任务建或修的是模块（`BuildJob.module() != null`）；中枢自己的 establish/forum 和它们的修复从不拆我们的方块（单参数版 = `false`）。AIR 步骤（`Builder.verdict`）：空气 ALREADY，可替换 CLEAR，其余照 `forBuild`、PLACE 改 CLEAR。
- **自然**（`TerrainRule.natural`）：没 TE、不在 `buildBlocked` 名单上，且（`buildClearable` 名单强制算；或 Forge `isWood`/`isLeaves`/`isReplaceable`、`IPlantable`/`IShearable`、`isReplaceableOreGen(…, stone)`、材质 ground/grass/sand/clay/snow/craftedSnow/ice/packedIce/leaves/plants/vine/gourd/cactus/coral）。**其余岩石**（材质 rock）只在是整块不透明立方体（`isOpaqueCube && renderAsNormalBlock`）、不算玩家建的、**且确知是世界生成的**时才算：原版 `stone`/`netherrack`/`end_stone`/meta 0 的 `sandstone`（`naturalRock`）、平顶山生物群系里的 `hardened_clay`/`stained_hardened_clay`（`mesaClay`：`BiomeGenMesa` 或 BiomeDictionary 的 MESA）、GT 的原始花岗岩/大理石/玄武岩（`rawGtStone`）、矿辞里有 `ore` 开头名字的方块（`Terrain.dictOre`，按方块和 meta 缓存）。石英块、雕纹/平滑砂岩、黑曜石、GT 混凝土、平顶山外的陶瓦、别的模组的石头都可能是玩家的，**一律 BLOCKED**：投影画红框；在步骤格上就照常受阻/跳过，在清场柱里只是留着、不暂停。名单写 `modid:name` 或 `modid:*`。
- **玩家建的**：原版圆石、苔石、石砖、砖块、木板、耕地、干草块、南瓜灯；注册名含 `brick`/`smooth`/`cobble`/`planks`（GT 原始石头除外）；GT 的 `BlockStonesAbstract` 系方块（花岗岩、大理石、玄武岩、混凝土）除原始石头外都算；`buildBlocked`；再加 `builtByHand`：手放的原版树叶（meta 第 4 位）、耕地上的作物、一组相连原木（含斜角，最多 256 根）有一根挨着加工过的东西（木板、玻璃、门、火把、箱子、铁……；结果记 200 tick，静态表弱引用着世界，`Terrain.serverStopped()` 清空）。手放的泥土、沙子、原版石头分不出来，照样当地形。
- **GT 矿**按类名反射找一次：`gregtech.common.blocks.BlockOresAbstract`、`bartworks.system.material.BWTileEntityMetaGeneratedOre`（TE）、`gtPlusPlus.core.block.base.BlockBaseOre`；GT 石头 `gregtech.common.blocks.BlockStonesAbstract` 只用来标出加工过的变种。**原始石头按类名精确判**：类正好是 `TerrainRule.GT_GRANITES`（`gregtech.common.blocks.BlockGranites`）或 `GT_STONES`（`…BlockStones`）且 meta%8==0（混凝土 `BlockConcretes` 同一个父类，永远算建的）。
- **我们的方块**：`ours` = 构架、地板、配件；`port` = 补给口（有 TE，`ours` 不含它）。`loose` = 是我们的（`ours` 或 `port`）、不是这一格的目标、而且不在任何已加载的**已成型**中枢或模块的 `covers()` 里（`TileMultiblock.covers`：控制器周围 ±(最大形状的最长边 + 1) 的立方体，档案馆 ±36、中枢 ±12）——比如拆了核心的 0.9.2 大厅留下的构架和书格。
- **保护**：`oursInOtherStructure` = 我们的方块（含补给口）落在保护盒里：别的已加载多方块的 `bounds()`（自己的中枢和当前任务自己的模块除外，20 tick 刷新）+ 当前任务的 keep-out 盒（全高）。`otherNexusArea` = `CampusRegistry.ownerAt`（别的园区中心 Chebyshev ≤ `GRADE_R`）。
- **区块**：动一个格子前 `checkChunksExist(±1)`，否则这一格算未加载（任务卡住就暂停 UNLOADED）；清场柱要整柱 ±1 都在。**带 TE 的方块只拆两种**：GT 矿（含 Bartworks 的 TE 矿），以及模块任务拆散落的补给口。
- **假玩家**：`FakePlayerFactory.get(ws, new GameProfile(主人 UUID（没有主人用 "[FluxEcho]" 的名字 UUID）, "[FluxEcho]"))`，每次都设 `worldObj`、`dimension`（领地模组按维度查）、位置，手里清空；破坏/放置抛异常 → 当受保护处理，记一次日志。
- **收获**：27 格（`Campus.SPOILS`），`buildKeepSpoils` 时只收矿（GT/GT++ 的矿、Bartworks 的 TE 矿、矿辞里 `ore*` 的岩石方块，`Terrain.spoils`）和原木的掉落，其余作废；放不下就等，不丢。模块任务拆掉的散落方块不进收获，按部件记账。
- **天空、高度、间距**（放核心时查，§8）：Y0+30 ≤ 255（`HEADROOM`）、核心处 `canBlockSeeTheSky`（`buildRequireSky`）、底座不压模块、离别的园区中心 Chebyshev ≥ `buildMinSpacing`(144 = 2·64+16)。
- **`CampusRegistry`**（主世界 mapStorage，`data/fluxecho_campus.dat`：`Campuses[{E:[dim,x,y,z,radius]}]`、`Ports` 平铺的 dim,x,y,z）：establish/铺设广场/加载时登记，拆（放置过的）核心时删；`tooClose`/`dropStale` 在核心可能站的四个格子都已加载、都没有这座中枢时删掉陈旧条目；`portGifted`/`markPortGifted`。

**任务与队列**（`Campus`）：一次一个当前任务。
- `autoJobs`（每 100 tick；中枢已成型且 `dockKnown()`）：容量 `min(RingSlots.open(PHASE), 7)` 减去已停靠的和在建的模块任务；对每个 `ModuleSpec`：开着（`archiveEnabled`）、中枢的队伍并集有研究、还没有这种模块（停靠的、记在场地上的、在建的）、没在冷却（取消后 `DECLINE_TICKS` 24000）、有空场地（按偏好顺序，没记录模块、没任务、占地外扩 1 不碰 keep-out、没有加载的模块中心在里面）→ 入队，通知 `unlocked`。
- `admit`：修复排在所有排队任务前面；当前任务没人按过开始、什么都没动、没有飞行中的格子时，修复直接顶替它（它回到队首，轮到时重新勘测）；其他任务排队尾。
- **keep-out（活的）**：中枢 `GRADE_R+24`(88) 内、不在记录场地上的模块 → `moduleKeepOut`；记录场地上的模块 → `spec.footprint(site, …, 1)`；100 tick 刷新。新 establish/forum 任务创建时把当时的盒子存进任务。
- **`ModuleSpec`**：`key, research, width, depth, height, sites, corePart, colour` + 计划工厂、控制器工厂、`enabled`。书库：`library`、研究 `library`、27×35×16、场地 {4, 2, 6}、`LIBRARY_CORE`、`0x8A5CFF`、`BuildPlan::archive`、`BuildPlan::archiveController`、`() -> Config.archiveEnabled`。
- **停靠**：`TileNexus.refreshDock` 先认（1）记录在场地上的控制器，（2）active 园区里站在模块位上的已成型模块（有 spec、中心在该深度的 `moduleCentre` ±3 内、离底座层上下 ≤ 1、正面朝中枢）→ 收编并 `recordSite`，（3）0.9.2 内环槽。场地上的模块用场地号停靠；`openSlots()` = 有内环研究时 `RingSlots.open(PHASE)`，active 园区再封顶 `MAX_CAMPUS_MODULES` 7。

**加一个中枢自建的模块**（照书库）：形状（`logic/XShape`，字符和已有形状不冲突）→ `BuildPlan` 加计划工厂和控制器工厂（阶段、组、HARD 格 = 结构检查格的测试）→ 新方块要新部件码（`Parts`，只加不改）和 `PartRecipes` 条目 → `ModuleSpecs.register(new ModuleSpec(...))` 放在模块的 `init` → 结构定义里每个字符用 `PartBlocks.block/meta` 映射（同 `TileLibrary.parts()`）→ lang `fluxecho.module.<key>`、`build.stage.m<n>` 如阶段不同要另起键 → 提 `BuildJob.V`（如果改了已有计划）。其余照 nexus.md §3「加一个新模块」。

## 6. 材料与 EU

**`BuildLedger`**（每座中枢一本）：原料余额（spec → 单位，1 件 = 65536）和成品部件（码 → 个数）。
- `charge(part, scale)`：有成品先用成品，否则 `cost(part)` 每行 × 倍率向上取整；`partOnly` 只能用成品（倍率 0 时什么都免费）；付不起什么都不改。`usedPart()` 告诉调用方该怎么 `refund`，退款精确。
- `need(未建部件数, scale)`：原料行 + 部件行；可合成部件的 `part:` 行只在它自己的原料还短缺时出现，封顶到补上最大缺口所需的成品数；`partOnly` 的行就是还缺的个数。`need` 为空 ⇔ `funded`（全额到位）。`wants` 做反压：已经够的东西不再收。`bill(need)` 是玩家要补的行（界面账单、Waila、聊天）。
- `withdrawable`/`take`：整件取回；`KEPT`（`ore:gemEchoCrystal`，回响晶带 NBT，账本存不下）不能取。`MAX_SCALE` 10，`clamp` 把 NaN 当 1。
- 持久化：`Campus` NBT `Ledger{Raw{spec: long}, Parts{"码": int}}`。

**交材料**（`Campus.credit`，只给 active 园区里没结束的任务、只收还缺的）：
- 我们自己方块的物品 → 成品部件（0.9.2 的回响书格物品 1:1 抵档案馆书格），收到那一行补足为止；否则按 `BuildLedger.share` 分给原料行：按 `need` 的顺序，每条 `Costs.matches` 这个物品、还缺的原料行收它缺的整件数（单位向上取整），剩下的交给下一条接受它的行（荧石粉同时喂一条 `item:` 行和一条 `ore:` 行）；部件行和已经够的行跳过；每行记 n 件 × `UNIT`。
- 「补给」：从成员的主背包拿；投入槽（GUI 里 1 格，不对自动化开放，每 tick 记账）：这两条**在按开始之前也收**，所以可以先备料。
- 补给口（自动化）：只喂成员按过开始的任务；开始之前只收这项模块任务的核心（开始要它）。
- 拆核心带走的余额（§8）。
- 「取出」：原料余额的整件（`Campus.stackOf`：部件的方块、`item:` 的物品、`ore:` 的首选矿辞条目，GregTech 的优先）+ 全部收获，给成员，放不下掉脚下；成品部件留在账上。
- 模块任务按「开始」前账本里要有它的核心（倍率 0 或核心已经站着时不要求），否则 `need_core`。

**补给口**（`TileSupplyPort`）：1 格 `ISidedInventory`，所有面都开；`isItemValidForSlot` 问 `campus.offer(simulate)`，同一种物品一 tick 内缓存答案；放进来就记账、之后每 tick 再记，所以槽几乎总是空的，剩的能被抽走，管道不堵。establish/forum 放的补给口链接自己的中枢、正面朝园区主轴（`linkAt`）；手放的链接 `RANGE` 128 内最近的、已加载的同队中枢，中枢没了会自己再找。队伍一旦定下不变：只有本队（或链接中枢的成员）能手动链接、取回槽里的东西（`mayUse`）；谁都能往里交材料。右键：拿着东西 → 交（走 `offer`，只喂已开始的任务）；潜行空手 → 把主背包里任务要的都交；空手 → 聊天显示还缺的前三行，并退还槽里卡着的东西（本队）。NBT `Slot/Nexus/Team/Facing`，描述包 `F`。

**EU**：每放一格 `buildEuPerBlock`(256)，这一格要先拆东西时再加 `buildEuPerClear`(64)（逐格算，`Builder.stepEu`）；每清一格、填一格、每个 AIR 步骤 `buildEuPerClear`；都从主人队伍的无线电网扣，`ServerEvents.addTeamTick` 记进统计。界面的 EU/t 是 10 tick 的平均。

**用量示例**（`buildCostScale`=1，示例种子）：establish ≈ 石头 628、钢板 79、不锈钢板 70、石砖 49、不锈钢杆 48、煤粉 43、荧石粉 63、通量结晶 27、碎屑 21、玻璃板 14、荧石块 14、铁栏杆 3、**晶核座 1（成品）**，放置约 36 万 EU + 清场每格 64；档案馆 ≈ 石砖 1619、石头 848、钢板 437、荧石粉 331、钢杆 212、不锈钢板 140、玻璃 129、书架 88、煤粉 61、回响晶 38、通量结晶 12、碎屑 11、玻璃板 9、不锈钢杆 8、**书库核心 1**，放置约 158 万 EU。

## 7. 状态与暂停（`BuildState`）

| 转移 | 条件 |
|---|---|
| （新任务）→ SURVEY | `initial()` |
| SURVEY → PROJECTING / WAITING / PAUSED | 勘测完：没人按过开始 / 按过 / 按过且勘测中被成员暂停 |
| PROJECTING、WAITING → BUILDING | 成员按开始（WAITING 也由引擎下一 tick 接上） |
| BUILDING、WAITING → PAUSED | 有原因卡住 |
| PAUSED → BUILDING | 原因消失（PLAYER 除外，只有开始能解） |
| BUILDING → DONE | 最后一段完成（模块要成型） |
| 未结束 → SURVEY | 需要重新勘测（加载、换场地、新计划版本） |
| 未结束 → CANCELLED | 成员按两次取消 |

- **暂停原因** `Pause`：`MATERIALS`、`POWER`、`UNLOADED`、`BLOCKED`、`PROTECTED`、`PLAYER`、`INCOMPLETE`；`autoResumes` = 除 PLAYER 外都会自己恢复。缺料/缺电要 40 tick 没有进展才暂停（飞行中的格子算在等），暂停后至少 100 tick（`RESUME_AFTER`）才恢复，免得一点点进账就让任务来回翻；恢复尝试每 10 tick（PROTECTED 每 100 tick）跑一遍，有进展就恢复；INCOMPLETE 等结构被人手补好成型。
- `canStart(state, pause)` 只有两参数版本（光看状态分不出玩家暂停，单参数版被测试禁止）。
- **按钮**（`Campus` 入口，都要 `member(p)`；GUI 还要求 8 格内、`nexusEnabled && campusEnabled`）：开始（勘测中按 = 记下同意，勘测完自动继续；`started` 标志让重新勘测后回到 WAITING）；暂停（没人开始过的任务返回 `not_now`；勘测中按会保留到勘测结束）；取消（GUI 第一下只在客户端预备 3 秒，第二下才发；建好的留着、飞行中的照样落地、余额留着，模块 24000 tick 内不再提供）；◀ ▶ 换场地（只在还没动过、没有飞行中的格子时；换了要重新按开始）；跳过受阻（园区级开关 `Skip`）；修复（停在 INCOMPLETE 的任务原计划重来、不再清场 `keepClear`；否则中枢或场地上的模块散架超过 `REPAIR_AFTER` 200 tick 时排一个修复任务：不清场不平整，只有中枢修复会清底座上方，不送补给口）；铺设广场（§9）；取出、补给（§6）。

## 8. 放核心（`LiftRule`、`ItemBlockNexusCore`）

- **抬升规则**：点的是方块顶面、没潜行、点的不是我们的构架/地板/配件、上方两格可放（可替换且没有生物）、y+2 ≤ 255 → 核心放在点击方块上方 **2 格**（点的那层成为底座层，上一格是控制台座，核心在眼睛高度）；否则照常放（0.9.2 的手动方式，campus 保持 legacy；带着园区放回原处的核心除外，见下面「放回原处」）。点的是草、藤、薄雪这类可替换方块时按它下面的地面算（`ground`）。两端用同一规则；**只有服务端决定抬升放置**，客户端返回 true 等方块，不预测（服务端拒绝时不会留下幽灵方块）。
- **拒绝**（只在 `nexusEnabled && campusEnabled` 时；顺序）：`too_high`（Y0+`HEADROOM`30 > 255）、`no_sky`（`buildRequireSky` 且核心处看不到天）、`on_module`（相位 I 的盒子压到已加载模块的 `bounds()`）、`too_close`（`CampusRegistry.tooClose` 或已加载但还没园区的中枢，Chebyshev < `buildMinSpacing`，带对方中心坐标）。园区关掉时照样抬升但不建任务。放下时 `placeBlockAt` 里先 `restore`（带来的园区、显化、余额），园区没有因此变 active（不是放回原处）、也不是因为别的园区太近而没能接着用（`Campus.resumeRefused()`）时，才 `campus.establish(p)` → 聊天 `placed_lifted`。
- **拆核心**（`BlockNexusCore`）：只有放置过的 tile（有主人或有园区）才 `CampusRegistry.remove`；不在 `restoringBlockSnapshots` 时掉落投入槽，并在核心**带着余额或显化**时（余额、部件、收获、扣过账的飞行、正在显化的产物）从 `breakBlock` 掉一个核心物品：先 `refundFlights`，NBT `BuildCredit{Raw, Parts, Spoils}`、`Pending`（产物）、`Manifest{Id, Ticks, Max}`，active 园区再加 `Campus`（下一条），账本和收获随之清空；这种情况 `getDrops` 什么都不给，免得掉两次。不带余额和显化的核心走普通 `getDrops`（只有采集、爆炸、破坏机才掉；传送器换掉核心什么都拿不到）；active 园区的 `Campus` 跟着这个普通掉落走：`getDrops` 先被问（爆炸、破坏机，tile 还在）就从 tile 取（`ItemBlockNexusCore.campusTag`），后被问（采集）就取 `breakBlock` 同一 tick 记下的（`noteCampus`/`takeCampus`，维度、坐标、tick 都对上才给，取一次就忘）。掉的东西不比以前多，所以带园区不是刷物品的路。放这个核心（抬升与否）时 `restore` 加回账本和收获（收获放不下掉在核心旁），显化只恢复到没在显化的中枢。物品闪光、提示 `nexus.carries_credit`/`carries_pending`/`carries_campus`（带核心原来的坐标）。——0.9.x「中枢被破坏时显化中的产物丢失」由此修好。
- **核心带着的园区**（`Campus.carried`，legacy 园区不带；`Campus.Carried` 读写）：物品 NBT `Campus{Dim, Pos（核心 int[3]）, F（正面）, Sites[{S,P,K}]（记录的场地和模块种类，同园区存档）, Skip, Declined{模块: 到期 tick}, Job}`，`Job` 是没完成的园区任务的计划键（establish 优先，其次 forum，没有为 ""）。不带任务和账本（余额走 `BuildCredit`），模块任务丢掉（`autoJobs` 之后照常再提供）。
- **放回原处**（`Campus.resume`，`restore` 里第一个做，要 `nexusEnabled`；`campusEnabled` 关着或园区已经 active 时不做）：只在**同一维度、同一方块**才接着用，**怎么放都行**——潜行放、点它的控制台座或底座（这些从不抬升）也一样；潜行只在放到新地方时选 0.9.2 方式，接着用的园区在成员按「开始」之前什么都不动。先把核心转回原来的正面（`nexus.place(owner, name, F)`，底座才对得上），再查 `CampusRegistry.tooClose`（`buildMinSpacing`）：太近 → `build.resume_too_close`（园区留 legacy，余额照样还回）；否则园区 active、恢复场地和种类、跳过受阻、冷却，登记 `CampusRegistry`，聊天 `build.resumed`。`RESUME_CHECK` 40 tick 后（结构已经检查过）`afterResume`：没有未结束的中枢任务、而中枢没成型或原来的 establish 没完成 → 排一个中枢修复 `repair:establish`（同 establish 计划，只清底座上方、不平整，立着的格子勘测时直接记 done）；否则原来的 forum 没完成又没有 forum 任务 → 重排 forum 并 `restart(true)`（`keepClear`，清场已经做过）。两者都等成员按「开始」。等着检查的这一步存在园区 NBT `Rc`（到期 tick）/`Rj`（计划键）里，重新加载也不丢。放到别的地方：是一座新中枢（抬升就勘测，潜行就手搭），只带余额和显化。

## 9. 旧中枢与铺设广场

- NBT 里没有 `Campus` 的中枢（0.9.2 存档）和潜行放下的核心（放回原处接着用的除外，§8）是 **legacy**：没有投影，世界里什么都不动；队伍有书库研究时每 200 tick 试着给在线成员发一次 `legacy_hint`（有人听到就记 `Hint`，不再发）。提示说：按「铺设广场」平整地面、铺广场；没有书库停靠时中枢在大厅位上建档案馆，停靠着的 0.9.2 大厅就算有书库；拆下大厅核心，书进队伍书库仓库，档案馆建成后取回。
- 「建造」页显示「铺设广场」→ `layForum`（成员；离别的园区太近 `forum_too_close`）→ 园区变 active、登记 `CampusRegistry`、建 forum 任务（平整 + 地面 + 塔柱 + 补给口，没有中枢本体），带上当时附近模块的 keep-out：停在内环上的 0.9.2 书库大厅（槽 0 正好压在大门上，每个槽都在平整圆盘里）原样不动，照旧用环槽停靠。
- 之后研究过的模块照常在空的模块位上出投影（占地碰到 keep-out 的场地跳过）。**停靠着的 0.9.2 书库大厅就算这座园区有书库**（`Campus.exists` 先看停靠的模块），中枢不提供档案馆，大厅照旧工作（167 本，§12）。
- **大厅 → 档案馆**：拆下大厅核心（书进队伍书库仓库，§12）→ 大厅不再停靠 → 下一次 `autoJobs` 在第一个空的大厅位（按 4、2、6）上投影档案馆。档案馆是模块任务，所以它清场盒里大厅留下的构架、书格（不在任何已成型结构的 `covers()` 里，`loose`）被拆掉、按部件记进账本（`forClear` 的 REPLACE），落在步骤格上的照常当错部件替换（`forBuild`）；档案馆第一次成型时自动从仓库取书（`refillOnce`）。只清档案馆自己的清场盒和步骤格：档案馆选了别的大厅位时，旧大厅的残留留在原地。

## 10. 同步、界面、Waila

- **描述包** `Cp`（`Campus.writeSync`，只在状态变化时发；红框的受阻格变化时最多每秒一次）：`Ac` active、`Sk` 跳过受阻（园区的开关，有没有任务都发）；有任务时再加 `K/Pk` 任务键和计划键、`S` 场地、`V` 版本、`St/Ps/Sg/Sn` 状态/暂停（ordinal）/阶段/阶段数、`Pl/To` 已放/总数、`Pa/Ft` 投影/完成 tick、`Mi[{物品, N}]` 缺的前三样、`Bl` 受阻格（`FxCodec` 相对中心，≤ 64）、`Ko` keep-out。客户端读成 `TileNexus.clientCampus`（`Campus.View`），`readSync` 时调 `BuildClient.viewChanged` 丢掉矛盾的进度数字。
- **`NexusNet` S→C**（都带 `Dim/X/Y/Z` 控制器和 `Ce` 中心，发给 `Sight.near(中心, nexusEffectRange + 64)`）：
  - `BUILD_FX = 2`：`T` 基准 tick；发射 `P` int[] 格子、`M` byte[] 部件码、`L` byte[] 飞行 tick、`O` byte[] 发射 tick − T；清除 `C` int[] 格子、`Cb` int[] `blockId<<4|meta`。`FxBatch` 按页存：每页（`Page`）最多 64 个发射和 64 个清除（`MAX`），满了或离这一页第一条超过 127 tick 就开新页，一页一个包（`T` 是这一页的基准）；每 4 tick 发一次，有一页满了（`full()`）就在这一 tick 结尾的 `Campus.flush` 里发；一批最多 `MAX_PAGES` 32 页，远多于建造在一次发送之间能填的（飞行最多 256、清场有预算），只有失控的批次才会丢特效。
  - `BUILD_STATE = 3`：`W` tick、`K/Pk`（没任务时 `K` 为空）、`St/Ps/Sg`、`Pl/To`、`Cl/Bk/Sk/Un` 清除/受阻/跳过/未加载、`Tf/Fd` 要填/已填、`Eu` 上一 tick EU、`Et` 预计剩余 tick、`Fl` 飞行数（short）。每 10 tick 数字变了才发，有任务时每 `STATE_HEARTBEAT` 100 tick 也发一次（后来走近的人也能收到）。
  - 客户端：Netty 线程入队，client tick START 在主线程交给 `CampusNet.receive` → `BuildClient`。
- **`BuildClient`**：每座中枢（维度 + 控制器）一份 `Site{key, centre, state, launches, clears}`，列表是整体替换的不可变快照，渲染线程随便读；`LAND_LINGER` 10、`CLEAR_LINGER` 20、每座 `MAX_PER_NEXUS` 512、待放粒子 `MAX_PENDING` 512、超前 `MAX_AHEAD` 400 tick 的丢掉、进度 `STATE_TTL` 300 tick 没刷新就丢；`plan(TileNexus)` 用 `clientCampus` 按服务端同样的 `planFor` 算并缓存；第一次收包时自己注册 client tick 清理和世界卸载清空。
- **「建造」页**（`NexusGui`/`NexusScreen`，248×248，标签 [中枢] (164,4)、[建造] (204,4) 各 38×15，页码只在客户端）：左边任务面板（任务名和场地、状态和暂停原因、阶段 n/N、进度条、EU/t 和预计时间、已清理/受阻/跳过/未加载；没按开始之前「已清理」那格换成勘测数出的「将清理 n」，`Na` = `BuildJob.natural()`，键 `build.gui.to_clear`）、三排按钮（开始/暂停、补给、取消；◀ 场地 ▶、跳过受阻；取出、铺设广场或修复）；右边账单（8 行一页、最多 16 行，`有 / 要` 带图标和提示）、投入槽 (144,137)、余额和收获。数据是一个 `FakeSyncWidget<NBTTagCompound>`，每 10 tick 重建（按钮后立刻），变了才发。中枢未成型且有任务时窗口直接开在「建造」页。按钮回调在服务端跑：`refuse` → `disabled`/`not_member`/`too_far`（8 格），结果作为聊天行回给玩家。
- **Waila**（核心）：`营造：<任务> <百分比>% · <阶段/暂停原因/状态>`，再一行 `缺 <物品> ×n`（`feBuild*` 字段，只在 active 园区有未结束任务时）。**终端**：`TerminalView` 的中枢页带 `build{ac, k, pk, st, ps, sg, done, total}`（没任务时只有 `ac`）；FluxLite 的 `TerminalScreen.build` 在有 `k` 时于模块列表上方画一行：「建造 + 任务名」（`BuildJob.name`），右边暂停原因（暂停时）或状态 + `done / total`，圆点建造中/完成绿、暂停/投影中橙、其余灰。**Directory**：中枢的状态在有任务时报 `building`/`build_paused`（研究、显化优先；成员自己的暂停按空闲级别报）。

## 11. 客户端：投影、特效、总图

全部 `@SideOnly(CLIENT)`，只经 `ClientProxy` → `CampusClient.register()` 进来（先注册 `DeckRender`/`FittingRender` 的 render id，再 `BuildScan`、`BuildRender`、`BuildFx`、`Masterplan`，各自登记 Forge/FML 事件和 `FarDraw.add`）。三个世界渲染器都守 [gates.md](gates.md) §5：只在 `RenderWorldLastEvent` 画、阴影 pass 跳过、相对 `RenderManager.renderPos*`、只用 quads、每个顶点有法线、popAttrib 后手动开 `GL_TEXTURE_2D`、不写深度；`onRenderLast` 幂等——动画只按世界时间算，粒子和声音在 client tick 里发；每个部分单独失败（记一次日志、本次运行关掉这部分），GL 状态在 finally 里恢复。

- **`BuildScan`**（client tick，渲染只读它发布的不可变 `Ghost`）：每座有任务、显示投影（PROJECTING/WAITING/BUILDING/PAUSED）的中枢，在 `full` 模式、观察者在 `buildGhostRange`+64 内时扫描客户端世界判断哪些格子已建好——规则和服务端一样（`Terrain.isTarget`，AIR 步骤看空气）：第一遍每 tick 1024 格由近到远，之后每 tick 103 格（约 10 tick 1024 格）；没扫过的格子不显示（中途到场不会看到盖在成品上的幽灵）；客户端区块没加载的格子（`chunkExists` 在客户端恒真，要看 `provideChunk(...).isEmpty()`）先当没建，区块到了每 tick 最多重查 1024 格；刚落地的格子每 tick 查。选格用 `GhostCells`：最近的 `buildGhostCells` 个未建格子、只留外表面（两个幽灵之间、贴着不透明方块的面剔掉）、表面棱线（≤ 16384）、当前阶段最低一层（≤ 512）；每面四角和贴图坐标预先算好；最多每 10 tick 重建一次（阶段变化、有清除、100 tick 刷新、观察者移动 8 格），其间落地的格子只标 `gone`。
- **`BuildRender`**：幽灵 = 每格用自己方块的图标（`Block.getIcon(side, meta)`）画外表面，先铺一层平涂（青 0.09 / 琥珀 0.08），再叠贴图（0.75 / 0.9），加法混合、深度测试开不写深度、放大 0.002 + `glPolygonOffset(-3,-3)`（之后手动归零）；呼吸只在 57%–100% 亮度间摆（3 秒；缺料/缺电时琥珀色、60%–100%、5 秒）；表面棱线至少 1 像素宽。新投影从 `projectedAt` 起一个扫描面 4 秒（`SCAN_TICKS` 80）升过计划的盒子；建造中当前阶段最低一层更亮、格子描边；受阻格画红框（透过一切，≤ 64）；平整圆盘边缘在 Y0+1.05 画琥珀色边线直到清场完（修复任务不画）；完成后 2 秒（`RING_TICKS` 40）一圈硬光扫过新地面（establish/forum 以中枢为心、半径 19.5，模块以盒子中心）。超出 `buildGhostRange`、`outline` 模式、还在勘测时只描各阶段的包围盒（`OUTLINE_RANGE` 512 内）；`off` 什么都不画。
- **`BuildFx`** + `LaunchPhase`：每个发射是一个带自己贴图的小方块，从晶核（已成型：Y0+5.5 加晃动；未成型：控制器顶）沿二次贝塞尔弧飞向格子（顶点高出较高一端 `APEX_RISE` 3 + 0.35 × 水平距离），前面跑一个亮脉冲；格子最后 `min(PRINT 8, 飞行/2)` tick 从下往上打印；落地后描边闪 `FLASH` 6 tick。客户端晚几 tick 才知道发射，所以每个发射从"发射和第一次看到取较晚者"画到真实落地（`shownStart/shownLand`）：货物飞得快一点但总在方块出现那一 tick 到；落地后超过 `MAX_LATE` 8 tick 才看到的不重播。清除：client tick 里每 tick 最多 32 个清除各放 4–6 个原版破坏粒子（64 格内），破坏声和放置声各最多每 5 tick 一次，最近 128 个清除的格子描边停留一会儿。上限：`buildArcs` 管弧线和货物，打印和闪光取离相机最近的 256 个。`buildEffects` 关掉全不画。
- **`Masterplan`**：拿着中枢核心、模块核心或终端时（`NexusRender.holdsHint`），256 格内每座 active 园区在地面上（Y0+1.05）描出将来的样子：平整圆盘边、广场、环道、大门、每个模块位的档案馆占地（朝中枢那边标门）、对角预留位（虚线，`|du|+|dv|>15` 切角），96 格内每个场地号立一块朝相机的牌子；有模块的场地用模块色、当前任务的场地青色。**放置预览**：拿着中枢核心对着方块、没潜行时，按物品自己的规则（`ground`/`lift`/`facingFor`）画出抬升放置会立起的底座、广场、环道、大门、圆盘边，加朝向箭头和核心处一道光柱；客户端已经能看出服务端会拒绝时变红并在核心上方写原因（`too_high`、`no_sky`、`on_module`、`too_close`——后两个只看客户端加载了的中枢和模块）。`nexusEnabled` 或 `campusEnabled` 关掉时没有预览。线都画两遍（0.35 透过地形、再深度测试），是朝相机的条带、任何距离至少约 1 像素、每段 ≤ 8 格。
- **`NexusRender` 的园区部分**：场地上的模块的光桥沿场地轴从半径 5.6 到模块朝中枢的那一面（模块没加载时到 23.5），门口一道横光；内环模块照旧画环桥（`siteDoorSafe` 出错就记一次、退回环桥）；active 园区不再画 0.9.2 的槽位提示（总图代替）；每个多方块单独 try/catch。
- **区块网格**：`DeckRender`、`FittingRender`、`ShelfUnits` 都走 `PartDraw`：`renderStandardBlock`（原版光照）+ 显露面上关 AO、`0xF000F0` 画发光层；`Neighbours`/`UnitLook` 只读方块和 meta（区块可能在别的线程建），不读 `Formed`、不读 TE。

## 12. 回响档案馆

**形状 `ArchiveShape`**（移植 `spec/gen/archive.py`，字符改名到所有书库形状里唯一；0.9.2 大厅用 `F P H B K ~ -`，共用的意思相同）：27×35×16（`WIDTH/DEPTH/HEIGHT`，`MID` 13），本地 `x` 横向 0–26、`y` 向上 0–15（y0 是地基层，人站在它上面）、`z` 从正面 0–34（正面朝中枢）；这个坐标系相对世界是镜像的（蓝图的横轴朝门外看的人的右边），说"左右"要算进去。

| 字符 | 部件 | 字符 | 部件 |
|---|---|---|---|
| `F` | 模块地基 | `W` | 墙板（墙、屋顶、楼梯整级） |
| `-` | 必须是空气（两扇门 x 10–12、14–16，y 1–4，z 0–1，共 48 格） | `L` | 光缝墙板（壁柱） |
| `~` | 控制器（13, 2, 0），书库核心 | `w` | 深色墙裙 |
| `K` | 控制台座（核心下的门间座、阅读台 `DESK` (13,1,29)、8 个讲台） | `G` | 窗格 |
| `S` | 回响书格（书架单元的书格） | `d` | 地砖（回廊楼板） |
| `q` | 书架立柱（含楼梯芯、门间立柱） | `e` | 光带地砖（回廊光边） |
| `p` | 书架底座 | `r` | 栏杆 |
| `c` | 书架顶檐 | `h` | 全息底座（22 个） |
| `n` | 光檐（y12 一圈、门楣） | `t` | 踏板 |

`cell(x,y,z)` 还会返回 `.`（室内软地面，824 格，蓝图里是空格、不检查、按地砖铺）。两层：一楼书架 y2–4，回廊楼板 y6（`DECK_Y`），二楼书架 y8–10，平屋顶和檐口 y12（`ROOF_Y`），高窗 y13、退台 y14、屋脊 y15（天窗缝在屋脊上）；中殿上方 x 9–17、z 5–29 两层通高；前角两座镜像旋转楼梯（各 12 级，偶数级是踏板、奇数级整块，一级半格）；检查格 5055、门 48、软地面 824（生成器是 5057：两座楼梯第六级上方各去掉一块回廊楼板，否则人走不上第七级）。`inside(x,y,z)` 判断室内空间；`decor()` 门口雨棚 14 格（y5、z −1/−2、x 10–16 的悬浮环段）；`lecterns()`、`pedestals()`；`partOf(ch)`；蓝图 `symmetric()`。

**书架单元 `LibraryUnits`**：78 个（一楼 48、二楼回廊 30），每个 = 一个底座行 + 3×3 书格 + 一个顶檐行，两边是立柱，9 本书，共 `SLOTS` 702。顺序：一楼先（前墙；从门往里每对书排左先右、朝门那面先、靠中殿的先；侧墙；后墙），再二楼同样顺序。索书号 `G|U-<区><排><面>-<序号>`（区 F 前墙、L/R 两侧书排、WL/WR 侧墙、B 后墙；排 1–5 和面 a 朝门/b 背门只对书排），唯一。槽号 = `unit·9 + row·3 + col`（row 0 在上，col 0 在面对书的人的左手）；`unitOfCell`、`unitOfPost(x,y,z,side)`、`slot`、`cellOfSlot`。

**外观 `UnitLook`**（区块网格不能读 `Formed`）：书格下面是书格或底座、上面是书格或顶檐 → 画成书架的一格（`ShelfUnits`：背板缩进 3/16、上下隔板、端头侧板、两个敞开面相交处的角柱）；书面 = 邻居不是不透明方块、书格、立柱的水平面；对着立柱的那面是素板。书格里有书时，在下隔板上按书脊颜色画几本书（`LibraryBooks`）。不成单元的书格（0.9.2 大厅）保持原样。

**多形状**：书库是 `TileMultiblock` 的两种形状（框架见 nexus.md §3）：`main` = 档案馆、`hall` = 0.9.2 大厅。新核心是 `main`；没有 `Shape` 的存档按 `legacyShape()` 读成 `hall`；`tries` 在 `archiveEnabled` 关掉时跳过 `main`（已成型的档案馆会散架，书留着）。结构定义里档案馆每个字符用 `ArchiveShape.partOf` → `PartBlocks.block/meta` 映射——正是建造放的方块；两个形状共用的字符必须是同一个方块（`parts()` 第一次建结构定义时检查，不一致直接抛）。`flags` 全 0（不溶解）。`centreCell` 是地基中心：档案馆形状坐标 (13, 0, 17)（`cellOf` 后 {13, 15, 17}），大厅 (6, 0, 6)。手放核心落在某座已加载中枢的模块位控制器格上时，朝向自动对着中枢（`TileLibrary.place` 覆写）。

**`TileLibrary`**：样本 handler 702 格（`SLOTS`），`capacity()` 档案馆 702、大厅 167（`HALL_BOOKS`）；`isItemValid` 拒绝 ≥ 容量的槽、写好的卡、别的格里已有的、tile 已失效的；每格上限 1。读 NBT 先读进存档大小的临时 handler 再逐格拷（0.9.2 的 167 格落在前 167 槽）。NBT `Samples/Desk/Selected/Unit/Refilled` + 基类的 `Shape`。
- **形状变化和仓库**（`formedChanged(true)`）：大厅成型时先把 ≥167 槽的书收进仓库（`stowOverflow`，每本一件，多出来的数量掉在阅读台上）；然后**两种形状**第一次成型时都从队伍书库仓库按槽号顺序取书填这个形状的空位（`refillOnce` → `takeFromVault(capacity())`，只一次，记 `Refilled`）——拆了核心再放回去的大厅是新的 tile，所以也会把自己的书取回来（`HallRefillTest`）。拆核心（`BlockLibraryCore.dropsMore` → `takeDown`）：书收进队伍仓库（每本一件，多的掉落），没有队伍或 `restoringBlockSnapshots` 时全部掉落；写卡台的卡总是掉落；两个 handler 都清空。GUI 的「取回」（`refill`）随时按书架顺序补空位，已有的和放不下的留在仓库。
- **`LibraryVault`**（`WorldSavedData` `fluxecho_vault`，共享 mapStorage，`data/fluxecho_vault.dat`：`Teams[{Team, Items[物品 NBT]}]`）：书按 NBT 原样存（模组没了也不丢）；存的堆叠多于 1 件时一次给一件、计数减一（`useOne`）。
- **交互 `Units.use`**（登记在 `BlockFrame.onUse` 和 `BlockFitting.onUse`，两端同样回答，只有服务端动作）：点已成型档案馆的书格、底座、顶檐，或立柱（按点中立柱面的哪一半选两边的书架；从上下点用玩家位置）→ 这个书架。空手：打开 GUI 停在这个书架（`selectUnit` + `BlockNexus.open`）；拿样本：放进第一个空位（`library.unit.put`，带索书号和 n/9；满了 `full`；重复 `shelf.duplicate`）；拿空白卡：给点中的那本书（没有就书架第一本）写卡；潜行空手点有书的书格：取下那本。不是本队 → `unit.not_yours`。**拿着任何东西右键书架都会当书放**，所以不能贴着书架放方块（设计如此）。大厅的书格仍走 `Shelves`。
- **GUI**（`LibraryGui.window` 按形状分派；两种窗口都设了校验器：tile 不在了就关窗，`standing()`）：档案馆 `ArchiveGui`/`ArchiveScreen`，260×256，背包 (49,174)。标签 书架 (168,4)/目录 (212,4)。书架页：索书号、n/9、位置（一楼/二楼回廊 · 区）；3×3 代理格（`Shelf`：服务端每次访问都按当前选择映射到 `unit·9+k`）；◀◀ ◀ ▶ ▶▶（±5、±1，循环）；还书口（`Drop`：一次放一本到第一个空位，Shift 点背包里的样本走这里，书格本身不收 Shift）；右边两张平面图（`ArchiveMap`，每格 3 像素，门在下，按装满程度上色，点选最近的书架）。目录页：每本书一行（图标、名字、索书号 · 第 r 层第 c 格），点行跳到书架，点行尾的卡片标记让写卡台对准这本。下面一条：写卡台、`n/702`、借阅/未停靠行、「仓库里还有 N 本」和「取回」。选择是每个窗口自己的（服务端 `Sel`，起点 `selectedUnit`，变化写回），客户端经 `Link`（`FakeSyncWidget<Integer>`，id 1 要书架、id 2 点写卡台）请求，不预测。`BookSlot`：热键把整堆塞进空位时只留一件、其余还给玩家并整窗重发；点了书格或还书口之后服务端下一 tick 整窗重发（客户端猜不到书落在哪格）。大厅仍是 0.9.2 的窗口（5 页），加了两样：写卡台右边的「取回」（仓库有书时才可点，服务端 `refill`，同档案馆）和状态栏右边仓库里的本数（`library.gui.vault`，`vaultCount` 经 `guiVault` 同步）。
- **书的同步**：描述包 `P/S/L/Dk/Rv/Ep` + 书（`BookPacket`：`B` 整堆带 `At` 槽号、`Bi` 不带 NBT 的 `槽号, id<<16|meta` 对）；整个描述包只在成型、换形状、供电变化、开始/停止借阅时重发；书变了走 `LIB_DELTA = 4`（`Dim/X/Y/Z`、`Rv`、`Ep`、`B`、`Bi`、`E` 清空的槽），每 tick 最多 `DELTA_SLOTS` 64 槽，发给 `Sight.near(中心, nexusEffectRange+64)` 和正在看控制器区块的人。`BookPacket.write` 让书的 NBT 压缩后 < `LIMIT` 28000 字节：预算从 256 KB 起对半减，超了的书只发 id/meta。客户端按槽记修订号 + tile 的 `Ep`（每次加载随机），乱序到达也能合；描述包还没到的增量最多停 30 秒（最多 128 条）。
- **书脊和网格**：`SpineColour.of(id, meta, 名字哈希)`——旧书皮的暗色调色板、明暗 ±12%、向书库紫 `0x8A5CFF` 靠 20%，同一本书到哪都同色。`LibraryBooks`：位置 → 颜色的不可变表整体替换（同 `Formed` 的客户端表），每座书库按控制器键 `put/forget`，只重画书变了的格子；世界卸载清空。
- **渲染 `ArchiveRender`**（`NexusRender` → `ModuleRender` → `LibraryRender` 按形状分派，所以没有自己的 `FarDraw` 登记）：外面沿墙裙一圈 y1.5 的光线——停靠且有电青色（借阅时一道脉冲约 12 秒绕一圈）、停靠没电琥珀色呼吸、未停靠暗灰蓝；未成型时只在已经立起的墙段画暗线（独立的核心什么都不画）；成型时紫色扫光面升过 `bounds()`。相机在里面或 `NEAR` 24 格内才画室内：中殿上方 y10–12、z17 三圈慢转的硬光吊灯（中心晶体、光点、地面光池）；书架顶檐每 240 tick（12 秒）从门往里扫过一道紫光（二楼晚 3 格）；屋脊天窗到阅读台一道天光柱（按 `getSunBrightness` 日光/月光，没有天空的维度不画，带飘落的灰尘）；阅读台上 0.55 倍的图鉴和上升的字符，回廊高度上方悬着书井；22 个全息底座各显示旁边书排的一本书，每 120 tick（6 秒）换一本、彼此错开；准星下的书架（`ArchiveLook`，8 格内）画轮廓、九个位置、看着的那格，标签 `书架 <索书号> · n/9` + 3×3 小图 + 书名或「空位」（`library.look.*`）。未停靠/没电时室内亮度 0.35（天光不变）。每部分单独失败。

## 13. NEI

- 书库的三维预览显示 `main`（档案馆）和用料；`library.structure` 的说明同时介绍档案馆和照样成型的 0.9.2 大厅。
- 中枢预览 6–10 级：在模块位 4、2、6 上立档案馆（`min(RingSlots.open(相位), 3)` 座：相位 I 两座、II 起三座），控制器在 `archiveController` 的位置、朝向中枢；预览里不铺园区地面（太重）。没有 `ModuleSpec` 的模块种类退回同号内环槽（0.9.2 做法）。
- 地板、配件、补给口、×32 的回响书格都是普通合成表。

## 14. 配置（`nexus` 类别，0.10.0 只加键，`VERSION="0.10.0"`，`upgrade()` 没有新步骤）

| 键 | 默认（范围） | 说明 |
|---|---|---|
| `campusEnabled` | true | 关掉：什么都不建、不动，不做放置检查，也不做园区——点在地面上的核心照样抬高两格放下，但它是一座要手搭的 0.9.2 式中枢（园区保持 legacy、没有任务；配置说明也是这么写的）；带着园区的核心放回原处也不接着用。要 `nexusEnabled` 也开着才跑 |
| `archiveEnabled` | true | 关掉：不再提供/建档案馆，书库只按 0.9.2 大厅成型 |
| `buildBlocksPerTick` | 2.0（0.25–16） | 每 tick 平均发射数；乘阶段节奏（establish 本体 ×0.25、地面 ×1.5，模块地面 ×1.5，其余 ×1），预算上限 `BuildPace.CAP` 24 |
| `buildClearPerTick` | 8（1–256） | 每 tick 清/填的格子 |
| `buildEuPerBlock` | 256（0–最大） | 每放一格 EU |
| `buildEuPerClear` | 64（0–最大） | 每清一格、填一格、要先拆的格子加收 |
| `buildCostScale` | 1.0（0–10） | 原料倍率，0 = 全免费（晶核座、书库核心也免） |
| `buildClearHeight` | 24（0–64） | establish/forum 平整时清到地面以上多高；模块自己的盒子总是清满 |
| `buildFillDepth` | 2（0–16） | 平整时从地面层往下填露天坑和水的深度（地面层放草，上面有实心方块的放泥土；下面泥土）；岩浆和更深的留着 |
| `buildMinSpacing` | 144（0–100000） | 抬升放置离别的园区中心的 Chebyshev 距离，0 关闭 |
| `buildRequireSky` | true | 抬升放置要露天 |
| `buildKeepSpoils` | true | 矿和原木的掉落进收获（27 格），其余作废 |
| `buildClearable` / `buildBlocked` | 空 | `modid:name` 或 `modid:*`：强制可清（别的模组生成的石头要清就写在这里）/ 永不拆 |
| `buildProjection` | full（full/outline/off） | 客户端：投影画法；别的值当 full |
| `buildGhostRange` | 96（0–256） | 客户端：画幽灵块的距离，外面只描边 |
| `buildGhostCells` | 6000（0–100000） | 客户端：最多画多少幽灵块（最近的先） |
| `buildEffects` | true | 客户端：发射弧线、打印、清除粒子 |
| `buildArcs` | 48（0–512） | 客户端：同时画的弧线（和货物）上限 |

`effectRange`（`nexus` 类别，默认 128）同时决定建造包和书增量包的接收范围（+64）。中枢的其余键见 nexus.md §9。

## 15. 测试（纯逻辑在 `src/test/java/com/fluxecho/logic`，需要非逻辑类的在 `campus/`、`library/`）

| 测试 | 守住什么 |
|---|---|
| `MixTest` | 确定、[0,1)、不同位置不同种子 |
| `PartRecipesTest` | 每个键能解析、图案成形、表里有每个部件、构架图案同 0.9.2、地砖/地缝钢成本、展开精确、`partOnly`、坏表抛异常 |
| `GtPatternTest` | 没有配方把小写字母（GT 工具字母）交给 GT；改名后原料和数量不变 |
| `CampusPlanTest` | 坐标双射且同 `RingSlots.offset`、场地和中心、0.9.2 槽落在同号模块位里、三座档案馆不碰广场/环道/大门和彼此、平整格在圆盘内、establish 铺满广场大门和整圈环道、模块地面只增、确定、种子随位置变 |
| `PavingTest` | 各比例、全园区比例、光只在允许的线上、光井在广场且离底座 ≥2、侧叶、大厅地面通向门、和生成器一致 |
| `ArchiveShapeTest`、`StairWalkTest` | 尺寸、对称、控制器和座、精确计数、门和门厅、室内封闭、`inside`、阅读台讲台底座、软地面、雨棚；楼梯每级升 ≤0.5、净空 ≥2、到回廊 |
| `LibraryUnitsTest`、`UnitLookTest` | 78/702、一楼 48、黄金顺序、索书号唯一、每格只属一个书架、槽号往返、书面前有可走空气、镜像、左右是看书人的左右、立柱按点击侧选；书架外观真值表 |
| `TerrainRuleTest` | 判定顺序、目标总放行、岩浆/不可破坏/别的结构/别的园区受阻、清场体、自然来自钩子和材质、玩家建的岩石、名单；只有确知生成的岩石算自然（原版石头/地狱岩/末地石/普通砂岩、平顶山陶瓦、GT 原始石头、矿辞的矿；混凝土、石英、黑曜石、平滑砂岩、别的模组的石头不算）、`naturalRock`/`mesaClay`/`rawGtStone`；模块任务拆散落的我们的方块和补给口，中枢自己的任务、已成型结构范围里的不拆 |
| `BuildPlanTest` | 朝向、establish = 相位 I 去控制器且控制台座先、由内向外、塔柱再补给口、平整和底座柱、forum = 去掉中枢、keep-out 让开停靠的大厅、档案馆朝中枢、HARD 格 = 蓝图检查格（4 朝向）、无重复、阶段、书架是 15 格连续组、镜像相邻、清场盒、确定 |
| `BuildLedgerTest`、`BuildStateTest`、`BuildPaceTest` | 扣退往返、成品优先、倍率 0 免费、向上取整、回响晶不可取、need/wants 反压、need 空 ⇔ 全额、一组物品依次喂每条接受它的原料行（`share`）；状态表、开始要看暂停、自动恢复；节奏、组、清场预算、飞行时间、ETA |
| `FxCodecTest`、`LiftRuleTest`、`GhostCellsTest`、`LaunchPhaseTest` | 打包布局；抬升真值表；选格/面/棱/最低层/包围盒/轮廓（三万格够快）；发射时间线和弧线 |
| `SpineColourTest`、`BlueprintTest` | 书脊不透明、暗色、偏紫；`cell` 是 `world` 的逆、`point` |
| `campus/CampusEngineTest` | 位置打包、模块键、任务存读、keep-out 随任务、计划重算一致、占地、旧存档的飞行、退款、换场地收回开始、勘测中暂停、修复不平整、修复排前、受阻格封顶计数、特效批次时间、忙的一 tick 特效分页不丢、核心带着的园区（`Carried`）存读、只有要拆的格子付拆的 EU（`stepEu`）、跳过受阻没任务也同步 |
| `library/ArchiveMapTest`、`LibraryPacketTest`、`LibraryVaultTest`、`TileLibraryUnitsTest`、`HallRefillTest` | 平面图格子在图内不重叠、点哪选哪、方向；满库的书包 < 28000、增量包；仓库存读、坏条目跳过、一次一件；坐标、新核心是档案馆、旧存档是大厅、书面朝屋里、底座顶檐立柱指向书架、讲台是控制台、按中心停靠；大厅和档案馆第一次成型都按自己的容量从仓库取一次、存读后不再取 |

## 16. 已知问题、只能进游戏看的

- **已知问题**：
  - active 园区提供模块的上限按 `min(RingSlots.open(PHASE), 7)` 算、不看内环研究，而停靠（`openSlots`）要内环——书库研究本来就要内环，现在没区别。
  - 修复后原计划重来（`keepClear`）的任务，客户端仍画平整边线（`Kc` 没同步）。
  - 放置预览的 `too_close`/`on_module` 只看客户端加载了的中枢和模块（`CampusRegistry` 只在服务端）。
  - 手放的泥土、沙子、原版石头分不出来，按地形清掉；投影不按种类列出要清的格子（「建造」页只给总数「将清理」）。
  - 别的模组生成的石头（比如 Et Futurum 的安山岩、闪长岩、花岗岩）不在确知生成的名单里，标红留着；要清得写进 `buildClearable`。
  - `covers()` 是宽松的立方体（成型档案馆 ±36）：以后在成型的档案馆旁边建别的模块时，落在它范围里的我们的残留方块不算散落，照样受阻。
  - 罕见：抬升放回原处时，放置前的 `too_close` 检查（`refusal`）按玩家朝向算中枢中心，`resume` 按带着的正面算；两者不一致时可能检查通过而 `resume` 报 `resume_too_close`，这时核心只是放下了、园区留 legacy（`resumeRefused()` 挡住了 `establish`）；反过来检查不过时，潜行放回去照样接着用。
  - 缺料/缺电这类暂时的暂停也走描述包（`RESUME_AFTER` 限住了频率）。
  - `BuildClient` 的计划缓存只在离开世界时清（每座中枢一条）。
  - 档案馆：控制器区块卸载而书架区块还在时书架上的书不画；一本书先不带 NBT、后带 NBT 发来时书脊颜色可能变；客户端还没收到描述包就开 GUI，两边窗口可能不一致；GUI 里选择变化后一个往返内点格子，作用在新书架上（看到的是旧内容，不丢东西）。
- **只能进游戏看**（开着和关着 Complementary 各看一遍）：投影（幽灵块白天和琥珀色是否看得清、多层叠加是否过亮、棱线、扫描面、最低层、红框、平整边线、完成光环）；弧线、货物方块、打印、落地闪光和声音时机；总图和预览线站着在 20–70 格外看不看得见；地板/配件/书架单元的网格和发光；「建造」页和档案馆 GUI 的点击、拖放、Shift 点、热键、点后整窗重发；大厅窗口的「取回」和仓库本数、终端中枢页的营造行；拆下核心再放回原处（抬升、潜行、点控制台座）园区接着用、修复排上；档案馆室内特效和看着的书架标签；约 60 格宽的 NEI 预览；手放核心朝向中枢并成型、区块边缘不散架、成型时帧时间；未成型档案馆的墙裙光线；真实世界里的清场、平整、领地模组的拒绝和假玩家。

## 17. 安全修改须知

- **计划确定性**：`CampusPlan`、`BuildPlan`、`ArchiveShape`、`LibraryUnits` 不读配置、不用随机数和 `Math` 的三角函数（`Mix` + `StrictMath`）；任务只存计划键和 keep-out，计划每次重算，客户端也照样算。改了它们放什么 → 提 `BuildJob.V`。keep-out 必须随任务存、随描述包同步。
- **部件码**（`Parts`）、deck/fitting 的 meta 永不改、不复用：码存在账本 `Parts`、飞行 `P`、`BUILD_FX` 的 `M` 里，meta 是世界里的方块。`PartRecipes` 改配方会改成本（账本按单位存，已有余额照旧有效）。
- **场地常量**（`HALL_FRONT`、`HALL_SITES`、`GRADE_R/K`、广场、环道、大门、塔柱、补给口位置）改了会让已有园区的记录场地、keep-out、已铺地面对不上。
- **档案馆**：`ArchiveShape` 改了已建的档案馆就不成型（还要提 V）；`LibraryUnits` 的顺序就是书的槽号（`unit·9+k`），改顺序 = 把书挪到别的书架；`TileLibrary.SHAPES` 顺序（`main` 再 `hall`）和 `legacyShape()=hall` 决定旧存档怎么读；`SLOTS` 702 不能变小。
- **NBT 键**：中枢 `Campus{St, Hint, Skip, Sites[{S,P,K}], Job, Queue, Ledger{Raw,Parts}, BuildIn, Spoils, Declined, Rc, Rj}`（`Rc/Rj` 只在放回原处等检查时有）；任务 `K/Pk/S/V/St/Ps/Sg/Go/ByM/ByL/To/Kc/Pa/Ft/Ko/D/Sk/Cc/Cy/Ct/Cl/Na/Un/Tf/Fd/Bc/Bl/Fl[{I,L,U,C,P,Sc,D}]/Fs/Fr`（状态和暂停按枚举名存）；核心物品 `BuildCredit/Pending/Manifest/Campus{Dim, Pos, F, Sites[{S,P,K}], Skip, Declined, Job}`；补给口 `Slot/Nexus/Team/Facing`；书库 `Samples/Desk/Selected/Unit/Refilled/Shape`；`WorldSavedData` 名 `fluxecho_campus`、`fluxecho_vault`。
- **包**：`NexusNet` 种类 `BUILD_FX=2`、`BUILD_STATE=3`、`LIB_DELTA=4` 不改不复用；描述包和 `BUILD_STATE` 发 `BuildState` 的 ordinal——枚举只能在末尾加值；`FxCodec` 布局两端一致。
- **阶段**编号和 lang 键 `fluxecho.build.stage.e<n>`/`m<n>` 一一对应。
- **`Builder.QUIET`**：引擎改世界时设上、结束清掉；我们的方块在 `onBlockAdded/breakBlock` 里守着它，新方块也要守，否则每放一块附近的多方块都复查一次。
- 改 `Terrain`/`TerrainRule` 先想"会不会拆掉别人的东西"：拿不准就 BLOCKED。
