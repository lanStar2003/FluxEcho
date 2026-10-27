# 开发手册 · 形与皮（0.17.0 起）：新材质族、`ShapeGeo`、`ShapeRender`、盒子表 `SkinTable`、旧楼灯窗、全城本色、遮挡门控、天幕层、探针、样板墙、机位

> 加新形、给楼配材质、碰灯窗和本色、画世界内的光（要被墙挡住或挂在天上）、加验收探针之前读这一篇。总览见 `CLAUDE.md`；设计依据是 `D:/Code/FluxEcho-campus/redesign/style/FINAL.md`（§3 材质家族、§3.3 形、§3.6 本色、§3.9 盒子表、§7 调色、§8.4–§8.6 光影和样板墙）和 `refactor/FINAL.md`（§5.6 旧楼灯窗、§7.1 探针）；实施记录在 `refactor/v0.17/reports/F03–F07.md`、`review.md`。
> 路径相对 `src/main/java/com/fluxecho/`。ID 一律以 [ids.md](ids.md) 为准（§1.1、§1.2、§1.4 部件码，§3.1 方块，§4.1 消息，§5.2 `Skn`，§7 配置，§18 命令）。

## 0. 定位

0.17.0「白舟」只铺底座、不改玩法。这一篇管的是重新设计里「看得见」的那一层公共件：

| 层 | 是什么 | 0.17.0 里谁用 |
|---|---|---|
| **形** | `logic/shape`：21 种形的纯几何（每格的 quad、法线、碰撞盒、挡视线的盒），按 26 个邻格自己变形 | 新材质族的方块、行走测试（`KitClimbTest`）、遮挡门控 |
| **皮** | `skin/**`、`client/shape/**`：四个材质族 8 个方块 ID（每族一个整块 ID、一个形块 ID），一个渲染器 `ShapeRender`，两个贴图生成器；舷号砖（deck 13）和花床（fitting 14）；活物料码 | 只有样板墙和探针会摆它们；**没有配方**，中枢也不建（0.19.0 起的新楼才用） |
| **盒子表** | `logic/skin/**`、`campus/SkinFeed`、`client/skin/**`：服务端说了算的「每座楼一条」表（状态、供电、本色、灯窗盒），同步给客户端，区块渲染器读它的不可变快照 | 0.16.0 的全部旧楼（`LEGACY` 档案）：黄昏的灯窗 |
| **本色** | `logic/skin/SkinPalette`：全城 23 行本色、三种光色、CIEDE2000 | 墙裙光线、盒子表、样板墙、界面令牌 `Tok`（`TokLumMatchesPaletteTest`） |
| **遮挡门控** | `logic/occlusion`、`client/occlusion`：光影下深度靠不住时，世界画完后的光由它逐件判断被不被挡 | 深度探针的两件光（各楼的「深度失效时」拆件从它那一版起登记） |
| **天幕层 / 日轨** | `logic/sky`、`client/sky`：天上的光（极光）两种放法；倾斜太阳轨道下的太阳方向 | 天幕探针、日轨探针（0.20 光井、0.21 极光、0.25 帆起正式用） |
| **探针、样板墙、机位** | `logic/probe`、`client/probe`、`command/Probe*`、`Swatch*`、`Cam*`、`mana/BedFlowers` | 验收卡第 1–5、9、10、12 项（`refactor/v0.17/ingame.md`），只限 OP |

不在这一篇的：七行调色本身（贴图在 `NexusTextures`、`CampusTextures`，见 [platform.md](platform.md) §10 和 [campus.md](campus.md) §3）、中枢白肋（[nexus.md](nexus.md) §3、§8）、旧楼灯窗怎么画进旧部件（[city.md](city.md) §4.10，这里只讲规则和数据）。

## 1. 文件

| 文件 | 作用 |
|---|---|
| `logic/shape/ShapeGeo` | 入口：`quads(Form, Nbhd)`、`item(Form)`、`boxes(Form, Nbhd)`、`occluders(Form, Nbhd)`、`section(Form, Nbhd, face)`、`MAX_QUADS = 128`；全部纯函数，可在任何线程调 |
| `logic/shape/Form`、`Family`、`FormCodes`、`SkinBlocks` | 形的词汇（21 个值）、四个族（族内形号 0–15 → 形）、部件码 ↔（族, 形号）、整块 / 形块 ID 的分法和每种形的光、透光、形名、材质 |
| `logic/shape/Nbhd`、`Quad`、`Overlay`、`Dir`、`Poly`、`Mesh` | 26 格邻域、一个 quad 的纯数据、叠层种类、几何辅助 |
| `logic/shape/CubeGeo`、`RoundGeo`、`SlopeGeo`、`HalfGeo`、`RibGeo`、`ColumnGeo`、`LouvreGeo`、`SoffitGeo`、`Geo` | 各种形的几何 |
| `skin/SkinModule` | 注册 8 个方块（`FluxEcho.preInit` 里紧跟 `CampusModule.preInit()`，任何模块开关下都注册）；`create()` 只造不注册（测试用）、`all()`、`block(Family, whole)` |
| `skin/BlockSkin`、`ItemBlockSkin` | 新族的方块和物品（meta = 形号；物品名 `tile.fluxecho.<族>.<形名>`，提示 `fluxecho.skin.<材质>.tip`） |
| `skin/ShapeAccess` | 从 `IBlockAccess` 读邻域交给 `ShapeGeo`：`nbhd`、`quads`（带一个 2048 项的缓存，满了清空）、`boxes`、`code`、`hidden`（`Formed.HIDE` 的格当空） |
| `client/shape/ShapeClient` | 客户端登记（`ClientProxy.init` 里 `CampusClient.register()` 之后） |
| `client/shape/ShapeRender` | 新族唯一的 ISBRH（区块网格 + 物品）；`skin(ShapeSkin)` 插盒子表的槽 |
| `client/shape/ShapeSkin` | 渲染器问盒子表「这一格看起来怎样」的接口：`Look(accent, awake, lamps, keel)`、`Look.DEFAULT`、`NONE` |
| `client/shape/SkinIcons` | 每族的 sprite（跨格图案按世界坐标挑 sprite）、发光层、舷号砖和花床的图标、`tint(Family)`（舟壳按 `style.hullBrightness` 压暗） |
| `client/shape/BedDraw` | 花床（fitting 14）的画法，由 `campus/client/FittingRender` 的两个 `case Parts.F_BED` 交过来 |
| `logic/LiveParts`、`logic/Parts`（`D_HULL_NUMBER`、`F_BED`、`BIRCH`…`FLOWER`、`isLive`） | 活物料码和舷号砖、花床的常量 |
| `logic/skin/SkinEntry`、`SkinState`、`SkinKeys`、`SkinTable`、`SkinCodec` | 盒子表的条目、状态码、楼键 ↔ 行号、表、编码 |
| `logic/skin/SkinTrack`、`PowerDebounce`、`LampRule`、`RebuildQueue`、`SkinView`、`SkinViewers`、`SkinDevelop` | 状态机、供电去抖、灯规则、重建节流、客户端快照、谁拿着哪个园区的表、显影（只有接口） |
| `logic/skin/SkinProfiles`、`Profile` | 外观档案：`LEGACY`（35 个旧形状）、`NEW_SHAPE`（以后的新形状）、旧楼的灯窗遮罩 `legacyMask` |
| `logic/skin/SkinPalette` | 全城本色（唯一来源）和色差 |
| `campus/SkinFeed` | 服务端：每个园区一个 feed，存读 `Skn`，发 `SKIN_TABLE` / `SKIN_DELTA` |
| `client/skin/ClientSkin`、`SkinPackets` | 客户端：解包、持有快照、黄昏 / 黎明按节流翻灯、`glow(...)` 给旧部件的渲染器 |
| `logic/occlusion/SightCells`、`SightRay`、`GatePiece`、`GateCore` | 遮挡门控的纯逻辑 |
| `client/occlusion/OcclusionGate`、`WorldSight`、`DepthProbeRender` | 门控的客户端（client tick 里算、渲染只读答案）、世界怎么挡视线、深度探针的五件光 |
| `logic/sky/Aurora`、`SkySheet`、`SunPath` | 极光的 quad、两种放法、太阳方向和帆法线 |
| `client/sky/SkyLayer`、`SunProbeRender` | 天幕层的渲染器、日轨探针的线 |
| `logic/probe/ProbeFrame`、`ProbeMarker`、`ProbeRigs`、`ProbeArgs`、`SwatchLayout`、`CamPoints`、`SwatchPreview`（测试侧） | 探针的框架、告示牌格式、各探针的几何、命令词和补全、样板墙格位表、机位 |
| `client/probe/ProbeMarkers`、`SwatchLooks`、`ProbeClient` | 客户端认出探针、样板墙的光、登记 |
| `command/ProbeCommands`、`ProbeWorld`、`SwatchCommand`、`CamCommand`；`command/CommandFluxEcho` 的路由 | 命令 |
| `mana/BedFlowers` | 花床探针要种的花（植物魔法的类只在 `mana` 包） |
| `tools/HullTextures.java`、`tools/SkinTextures.java` | 新族的贴图生成器（`textures/blocks/skin/<材质>/…`，159 张；第一个参数可给输出根目录） |

## 2. 形：`ShapeGeo`

- **词汇**（style §3.2）：`Form` 21 个值。舟壳的 16 种形（`ordinal()` = 形号 0–15）：WHOLE 整、SEAM 缝、ROUND 圆、SLOPE 坡、HALF 半、STEP 阶、RIB 肋、COLUMN 柱、WINDOW 窗、LOUVRE 格、LIGHT_SEAM 光缝、KEEL 龙骨、LAMP 灯窗、SOFFIT 拱腹、FLOOR 地、RELIEF 饰；玄基和皮材换掉的 5 种：COPING 压顶、POOL_LIP 池沿、SPOUT 水口、PLANTER 种植沿、GRATE 格栅。`Family`：`HULL("hull")`、`FOOTING("footing")`、`SKIN_GARDEN("skin_garden")`、`SKIN_SCRIPT("skin_script")`，`form(形号)` 把族内形号映到 `Form`（还没有几何的返回 null：汉白玉 15 宝瓶栏，0.20）。皮材特殊形：苔铜 7 = 铜框窗（WINDOW）、墨 7 = 格栅、宣 13 灯笼板 = LAMP、宣 15 画面 = WHOLE。
- **邻域 `Nbhd`**：周围 26 格，每格 OPEN / SOLID / WATER 或「我们的形（`Form` + 是否同族）」。规则只读风格稿的 14 格（`ofMask` 是它的位图写法）；另外 12 格只用来算邻居在共享面上露出什么（两块我们的形之间精确剔除）和肋的向下斜撑。「实」= SOLID 或 `Form.closed()`。
- **输出**：`quads` 先给格内的面（`face == -1`），再按 `ForgeDirection` 0–5 给格面；同一 `(Form, Nbhd)` 输出逐字节相同，可以缓存。`Quad`：4 个顶点、每个顶点一条单位法线（指向实体外；曲面是解析值）、UV（单位是格；`uAxis / vAxis` 让图案跨格连续，曲面的 u 是弧长、可能超过 1）、`face`、`overlay`（NONE、LINE、KEEL、KEEL_TIP、LAMP、NOSE、LIP、SPOUT）、`conn`（面内哪几边让线 / 框接下去）、`surface`。从外面看逆时针；三角形写成第 4 点重复第 3 点（Angelica 只收 quad）。坐标在格内 0..1，唯一例外是肋的 45° 斜撑最多伸出 0.09。
- **剔除**：朝 SOLID 不画；两个整块形之间不画；看得穿的形之间照画（同形同族的窗之间去掉玻璃）；两块我们的实体形之间各画「自己的截面减去对方的截面」，算不出对方截面时两边都画整截面（只多出看不见的面，不会开缝）。
- **碰撞**：整块；半、阶是下半块；坡 S2 是两盒台阶（间隙 √0.125 ≈ 0.354）；`occluders` 给遮挡门控（窗、格栅为空，肋、柱、半、阶、百叶按自己的盒）。行走测试 `KitClimbTest` 读的就是 `boxes()`。
- **每格 quad 数**：孤立的圆 40，挤满我们的形时最多 72，全部 ≤ `MAX_QUADS = 128`（`ShapeGeoTest.budget` 实测）。

## 3. 皮：四个材质族

### 3.1 方块 ID

| 族 | 整块 ID | 形块 ID | 材质 |
|---|---|---|---|
| 舟壳 | `fluxecho:hull` | `hull_form` | 珍珠白（底色 `#EDF1F3`，是全图最亮的像素，只有 `style.hullBrightness` 能把它整体压暗） |
| 玄基 | `footing` | `footing_form` | 暖黑玄武岩（`#36312D`） |
| 园皮 | `skin_garden` | `skin_garden_form` | 苔铜（形号 0–7）/ 汉白玉（8–15） |
| 文皮 | `skin_script` | `skin_script_form` | 墨（0–7）/ 宣（8–15） |

- **meta = 形号**。形是整格的（`Form.wholeBlock()`：整、缝、光缝、龙骨、灯窗、地、饰，玄基的池沿、水口、种植沿，宣的画面）住整块 ID：不透明整格，剔面、光照、AO 跟石头一样，成本也一样；其余（圆、坡、半、阶、肋、柱、窗、格、拱腹、压顶、格栅）住形块 ID。meta 放错 ID（或还没有几何）画成整格、不是部件。形没有朝向：按邻居定几何，所以一张蓝图四个朝向都成型。
- **列出的只有有码的 36 个形**（创造栏、NEI，`SkinBlocks.listedMetas`）；其余 28 个形方块和贴图照样注册、有名字，但没有码、没有配方，建造器不放，清场时按外来方块拆。
- **方块行为**：`Material.rock`，硬度 3（玄基 4），镐 1 级，不刷怪，`damageDropped = meta`。方块光写死在形上：光缝 7、龙骨 10、灯窗 / 灯笼板 9、阶 6（鼻线）、池沿 4（唇边），其余 0；亮不亮的「画面」由盒子表定。不透光：整块 255；形块里肋、柱、窗、格、格栅 0，其余 255。`isSideSolid`：整块全实；圆、坡、压顶、半、阶只有底面实；拱腹只有顶面实（圆冠、坡顶不积雪）。形块 `useNeighborBrightness`。
- **和城的接口**：`Formed.PASS` 的格没有碰撞，`Formed.HIDE` 的格什么都不画、邻居把朝它的面画回来（和构架一样；重新设计只溶解门心晶体、冠顶晶体、悬浮撑，正常玩法里新族一块都不溶解）；光梯把任何 `BlockSkin` 当 `LiftShaft.Cell.OURS`（`campus/Lift.cell`，骑行和宽限期里穿过、梯垫上头顶两格可上，`ShaftClearTest`）；`Builder.QUIET` 时放、拆不通知周围的多方块。
- **注册名写成字面量**：`SkinModule.preInit` 里 8 个名字是字面量（`IdFreezeTest` 的源码扫描要读出来），`SkinPartsTest` 钉住它们 = ids.md §3.1。

### 3.2 部件码

| 码 | 是什么 | 认法 |
|---|---|---|
| 96–107 | 舟壳的形 0、1、2、3、4、5、6、7、10、11、13、15 | `FormCodes` / `SkinBlocks.code`：`PartBlocks.block(code)` = 那一族整块或形块 ID，`meta(code)` = 形号 |
| 108–117 | 玄基的形 0–6、9、11、12 | 同上 |
| 118–125 | 墨 0、2、4、7，宣 8、9、12、13 | 同上 |
| 126、127、59–62 | 苔铜 0、2、3、4、5、7 | 同上 |
| 29 | deck 13 舷号砖 | `BlockDeck.known(meta)`（`DECK_TYPES` 仍 13） |
| 46 | fitting 14 花床 | `BlockFitting.known(meta)`（`FITTING_TYPES` 仍 10） |
| 54–58 | 活物料：白桦原木（meta 2，竖着）、橡树叶（meta 4，不腐烂）、藤（0）、睡莲（0）、花（`red_flower` 0） | `LiveParts`；`PartBlocks.code` 永远不把它们认成我们的，只有计划要它们的那一格 `Terrain.isTarget` 才认（`LiveParts.accepts`：白桦任何朝向、橡叶任何腐烂位、蒲公英也算花） |

- 新码**没有配方和成本**（`PartRecipes.cost` 为空，建造器按纯 EU 收）；配方走 `EchoRecipes`，等 narrative 的账落地时补。
- 活物料在计划里一律是 SOFT 步，统一挪到「投运前最后一段」的末尾、自下而上（`BuildPlan.module` 的 `takeLive`）；没有活物料的楼计划一步不变（`OldShapesCrcTest`）。藤 meta 0 只挂在实心块下面。
- `Terrain.probe` 把有码的新族形当「我们的」（`p.ours`）。

### 3.3 每楼图例（`ShapeKit`、生成器 `kit.py`）

基础图例冻结（`ShapeKit.partOf(char)` 原样留给旧形状）。新形状用 `Shape.legend(ch, code[, soft])` 声明自己的字符（不许用基础图例的字符和 `~ - 空格`、不许重复）；`ShapeKit.partOf(Shape, ch)` 先查本楼图例再查基础图例；`crc(Shape)` 把图例文本也算进去。`CityBuild` 已经走 `partOf(shape, ch)`；**`TileCityModule.define`（结构定义）还没改**，第一座带图例的新形状（0.19 档案馆 `lantern`）上线时改。生成器那边：`kit.py` 的 `LEGEND_BASE`（`LEGEND` 是别名）、`Shape(..., legend={字符: (族, 号[, 'soft'])})`，`report.py` 打印每个形状的 CRC，Java 测试比 `ShapeKit.crc`（`LegendTest`）。

### 3.4 `ShapeRender`

- **整块 ID**：`renderStandardBlock`（原版 AO、剔面），每个面取它在材质图案里那一格的 sprite（`SkinIcons.face`），水口朝空气 / 水那面换出水口；再在光缝、龙骨、灯窗、池沿的露出面上叠发光层。
- **形块 ID**：`ShapeAccess.quads` 的每个 quad：取那一侧邻格的亮度（格内的面取本格）、**每个顶点 `setNormal`**、按法线算原版的平面明暗（上 1、下 0.5、南北 0.8、东西 0.6，按法线分量平方混合）——**开着光影包时不乘**（`ShaderCompat.packInUse()` 每秒最多问一次），舟壳乘 `style.hullBrightness` 的灰；拱腹凹面的 u（弧长 π/2 格）按 2/π 压进一格（`fit`）。
- **发光两层**（白芯彩边，style §3.6）：每个叠层同一个 quad 画两次，亮度 `0xF000F0`：`*_glow_core` 白、不染色；`*_glow_edge` 灰度渐变，按顶点色染——龙骨染 `look.keel`（默认中枢青）、光缝和灯窗染楼的本色（没有本色时暖白）、阶的鼻线和池沿唇边染暖白。灯窗只在 `look.lamps` 时画，其余发光在 `look.awake` 时画。
- **只有一个皮肤槽**：`ShapeRender.skin(ShapeSkin)`。现在只有样板墙用它（`client/probe/SwatchLooks.install(next)` 插在 `ShapeSkin.NONE` 前面）；其余格子都是 `Look.DEFAULT`（没有本色、醒着、灯窗暗、龙骨青）。**以后把盒子表接进来时要调 `SwatchLooks.install(盒子表的 ShapeSkin)`，不要直接 `ShapeRender.skin(...)`**，否则样板墙的光被覆盖（见 §4.8）。
- **线程**：只读方块、meta 和不可变表，`ShapeAccess` 的缓存是并发的；**没有** Angelica 的 `ThreadSafeISBRH` 标记（Angelica 不是编译依赖），所以 Angelica 在主线程上建这些网格，和本模组别的 ISBRH 一样。原型 P3 过了以后先 `javap` 核实注解再加。
- **物品**：`ShapeGeo.item(形)`（孤立的封闭网格，拱腹放在它的洞口里），灯窗点亮。

### 3.5 贴图

`java tools/HullTextures.java`（舟壳、玄基、舷号砖、花床）、`java tools/SkinTextures.java`（苔铜、汉白玉、墨、宣），输出 `textures/blocks/skin/<材质>/…`（每格 32 px，二值 alpha；跨格图案——缝 2×2、玄基砌法 2×2、饰 4×4、汉白玉 4×4——整幅画好再切成每格一张 `<名>_<u>_<v>`）。发光：芯是中间两行（第 15–16 行，通到格边，所以一排光缝连成线），边两侧各两像素 `#FFFFFF` → `#999999`；鼻线在阶前脸最上两行；龙骨芯在第 15–16 列，芯色 `SkinPalette.KEEL_CORE`；唇边在上沿两行，渲染时转到每条朝水的边。改了生成器必须重跑，`GlowTextureTest` 的两个慢方法逐像素比。

### 3.6 舷号砖和花床

- **舷号砖**（deck 13，码 29）：玄基色地砖 + 不亮的八角徽，本身不发光；号码和亮不亮是盒子表的事（0.19 起的归途之路）。
- **花床**（fitting 14，码 46）：12/16 高（`BlockFitting.BED_TOP`），光 4（`BED_LIGHT`），底面实；`canSustainPlant` 只在顶面、只对 `EnumPlantType.Plains` 和 `Crop` 为真（原版花、树苗、植物魔法的花都是 Plains），`isFertile` 为真；白槽、土顶、唇边两层光（`BedDraw`）。床上的花离土面悬 4/16（原版把植物画在下一格底面），进游戏看了再定（验收卡第 5 项）。唇边的光不跟灯规则走。

## 4. 盒子表 `SkinTable`

服务端说了算：楼长什么样、灯亮不亮，客户端自己的结构检查只决定什么溶解。**盒子表从不改世界里的方块，也不改园区的行为**；出错只记日志、停掉 feed，不停园区。

### 4.1 条目和状态

- **条目 `SkinEntry`**（不可变，`Builder` / `toBuilder()`）：`campus`（园区 id = 中枢中心打包）、`ctrl`（控制器格，楼 id）、`box`（楼的外盒）、`lamp`（灯窗盒，可空）、`row`（ids.md §2 的 `Buildings` 行号，`SkinKeys`）、`shape`、`facing`、`state`、`powered`、`formedAt`、`accent`（本色 RGB）、`variant`、`extra`（≤ 64 字节，给 0.19 的 `VOYAGE` 段和环）。坐标打包：x 26 位 << 38、z 26 位 << 12、y 12 位。
- **状态 `SkinState`**（码永不改，`theStateCodesNeverMove`）：`BUILDING 0`、`FORMED 1`、`HOLD 2`、`DORMANT 3`、`ASCENDING 4`、`MEMORIAL 5`；不认识的码读成 `DORMANT`。醒着 = FORMED、HOLD、MEMORIAL。
- **状态机 `SkinTrack`**：建造中 → 成型（`formedAt` = 那一 tick）；成型的楼散架 → HOLD，60 秒（`DORMANT_AFTER = 1200`）内回来 → FORMED，不回来 → DORMANT；有整修在排队就一直 HOLD；散架期间供电位冻结；第一次见到就已成型的旧楼 `formedAt = 0`（不重放显影）；`ascend()` / `memorialize()` 给 0.28。
- **供电去抖 `PowerDebounce`**：原始供电位稳 200 tick（`STEADY`）才认，两次翻转至少隔 200 tick（`MIN_GAP`）。

### 4.2 灯规则（G6）和节流

- `LampRule.lit ⇔ awake ∧ powered ∧ 世界时间 mod 24000 ∈ [12500, 23500)`（`DUSK`、`DAWN`；半开区间，23,500 那一 tick 已灭）；波从中枢往外 `WAVE = 32` 格 / 秒：楼的延迟 `floor(20·d/32)` tick，d = 控制器到园区中心的水平距离。`isAwake v0 = formed ∧ docked ∧ powered`（v1 的条件随楼的版本改 `LampRule.isAwake` 和 `SkinFeed` 一处）。
- `glow(entry, part, lit)` 给渲染器三种答复：`UNTOUCHED`（−2，照 0.16 画）、`DARK`（−1，不画辉光层）、辉光层的颜色（本色；不着色的部件给白）。**不在任何条目里的格子一律 `UNTOUCHED`**：客户端没收到表（或在别的维度）时旧城和 0.16 一模一样，不会全城灭灯。
- **重建节流 `RebuildQueue`**：同一座楼 10 秒（`PER_BUILDING = 200` tick）最多一次，全城每秒最多 4 个盒子（`PER_SECOND`）；等着的楼不挡别的楼。一次翻灯只重建这座楼的灯窗盒（`SkinEntry.rebuildBox()`），一座楼不会半亮半暗。

### 4.3 外观档案和旧楼的灯窗（`SkinProfiles`，refactor §5.6）

- 外观按 **(楼键, 形状名)** 取。`LEGACY` = 0.16.0 的 35 个旧形状（15 个模块 + 中枢 + 书库，`OLD_SHAPES`）：没有变体、不显影、灯窗来自遮罩；`NEW_SHAPE` 给以后的新形状（各楼在自己那一版 `SkinProfiles.register` 自己的档案）。
- **LEGACY 的灯部件**：deck 9（光缝墙板 `panel_lit`，壁柱）、deck 11（光檐 `cornice`），0.16 `FittingRender` 画辉光层的全部配件——`FITTING_LIGHT > 0` 的 0、1、2、3、4、6、7、8、9（书架立柱、底座、顶檐、栏杆、踏板、全息底座、样本座、池沿、光梯；不含 5 窗格），着本色。
- **并进遮罩的常亮光**（§5.6「写不出来怎么办」第一条）：中枢 → 构架 `BASE_LIT`（亮辐条）+ `RING`；书库 → 构架 `FOUNDATION`（长厅的地基）；水滴机库、观星台、天光帆阵、天穹气象站 → 构架 `FLOAT`（浮空地基）。这几种只开关、不着色，画法在 `frame/client/FrameRender`（灯暗时跳过发光面那一圈）。**所以这些光白天是暗的**。
- **闸**：`LegacyGlowShareTest`——35 个旧形状白天发光面积 / 外表面积 ≤ 3%（最高 2.79% `library:hall`），夜里 ≤ 12%（最高 11.90% `mana_fountain`，离线很近：以后改它的贴图或形状先跑这个测试）；量法（外露面 × 贴图亮像素比例，藏起来的格和世界画完后的光不算）写在测试的类 Javadoc 里；35 个遮罩的格数和 CRC 钉死（运行时从蓝图算 `legacyMask`，`ConcurrentHashMap` 缓存，必须 = 测试里的生成器 `LegacyMaskGen`）。
- 渲染：`DeckRender`、`FittingRender`、`FrameRender` 画灯部件时问 `ClientSkin.glow(w, x, y, z, part)`：`DARK` 只画底层，亮时画底层 + 着本色的辉光层；非灯部件 `mayBeLamp` 为假，零开销。旧贴图是单层辉光，着色后白芯也被染色（G6 的白芯彩边要等各楼的新贴图）。

### 4.4 服务端 `campus/SkinFeed`

- 每个 `Campus` 一个 feed（字段 + `skin()`），`Campus.run()` 里每秒（`EVERY = 20`）看一遍：中枢、记录在案的每个场地的控制器（没加载的保持最后所见或 `Skn` 里的样子）、正在建的模块（计划外盒，BUILDING）、没了的楼（发 remove）。外盒 = 蓝图四角经 `bp.world` 转出的世界坐标；灯窗盒 = 遮罩外盒；本色 `SkinPalette.accentOr`。中枢自己成型前和立园区期间是 BUILDING，整修键 `repair:establish` / `repair:<场地>` 在队列里时 HOLD。
- **NBT `Skn`**（园区子表里的 byte 数组，不能叫 `Sk`）：`[版本 1][short n]{条目编码, long apartSince}`；0.16 的园区没有这个键 → 空表起步；读坏了只记日志、重建。
- **编码 `SkinCodec`**（版本 1）：`Table`（`dim`、`reset`、园区 id 列表、条目）、`Delta.put` / `Delta.remove`；条目的两个盒子写成相对控制器的 short（楼离中枢 ≤ 127），一条约 80 字节。载荷放在 NBT 键 `B` 的 byte 数组里，走 `NexusNet` S→C 7 `SKIN_TABLE` / 8 `SKIN_DELTA`（客户端泵交给 `client/skin/SkinPackets`）。
- **收件人**（`SkinViewers`，`RANGE = 512`、`DROP = 640`）：登录、换维度发那个维度 512 格内园区的整表（`reset`）；进 512 格发那一个园区的表，出 640 格才放下；变化发 `SKIN_DELTA` 给拿着这个园区的玩家。**收件人按玩家 UUID 做键**（`SkinFeed.viewer`，规则审查修过：按玩家对象做键时，重生和从末地回来新建的 `EntityPlayerMP` 会让旧对象留在表里，重复发包、退出后包堆在死连接上），发送时按 UUID 找在线玩家；同一维度重生不重发整表。服务端 tick 每 100 tick 查一次距离、清掉中枢没了的 feed；维度 0 卸载时全清。按 512 格挑人，不用 `Sight.near`（光门的 keepRange 48 远小于它）。
- 中枢整体搬走：旧条目跟着改园区 id；搬走 / 没了的园区对拿着它的玩家发一张只列这个园区、条目为空的表，客户端就不再按规则画它的灯窗。
- `SkinFeed.Sender` / `NETWORK` / `sender` 是测试替身的口子（`SkinSyncTest`）。

### 4.5 客户端 `client/skin/ClientSkin`

- 持有 `volatile SkinView`（不可变快照）。新来的条目按规则立刻画（重建它的盒子一次）；客户端 tick 末问 `due(t)` → 过节流 → `flip`，只 `markBlockRangeForRenderUpdate` 灯窗盒。
- `glow(IBlockAccess, x, y, z, part)` 对任何线程安全：非灯部件直接 `UNTOUCHED`；预览世界查 `SkinTable.resolve`（正午）；真实世界查快照，且只在快照维度 = 当前维度时。
- **世界卸载只清它自己维度的表**（`unloadForgets`，规则审查修过：换维度时新维度的整表在客户端处理重生包、发旧世界 `WorldEvent.Unload` 之前就到了，整表清空会把新表也清掉，直到下次登录灯窗都不跟规则）。
- 什么都不在世界画完后画、不按帧计数，开着光影照常。

### 4.6 NEI 预览

`nexus/InfoContainer` 在预览世界里建好一座楼后调 `SkinFeed.preview(tile)` → `SkinTable.putPreview(world, box, key, shape, facing, ctrl)`（`WeakHashMap`，预览世界被回收就没了）；预览按正午画（`PREVIEW_TIME = 6000`），所以旧楼的灯窗（和并进遮罩的常亮光）在预览里是暗的。中枢预览里的模块没有条目。

### 4.7 显影（只有接口）

`SkinDevelop`：新楼成型那一刻从下往上 2 秒（`SWEEP_TICKS = 40`）显完，显影前是「图纸色」（本色的 HSV 饱和度减半）。`LEGACY.develops = false`，0.17 没有楼显影。**0.19 接显影时，客户端要拿 `getTotalWorldTime` 和 `formedAt` 比**（`formedAt` 是服务端的 `getTotalWorldTime`；灯规则用的是 `getWorldTime`），否则 `/time set` 会让楼重新显影或永远显不完。

### 4.8 还没接的线：`ShapeRender` ↔ 盒子表

0.17 里没有一座楼用新族方块，盒子表里只有中枢和旧形状，接不接看不出区别，所以**没接**（合并 2 §4、合并 3 §5 定由 0.19 档案馆那一版做，连同它的 `SkinProfiles.register`）。接法约 15 行：在 `ClientSkin.register()`（或 `ShapeClient`）里 `SwatchLooks.install((w, x, y, z) -> …)`——预览世界用 `SkinTable.resolve` + `LampRule.lit(e, PREVIEW_TIME)`，真实世界用快照的 `find` + `SkinView.lit(e)`，返回 `new ShapeSkin.Look(e.accent, e.state.awake(), lit, e.state == MEMORIAL ? WARM_WHITE : NEXUS_CYAN)`，没有条目返回 null（= `Look.DEFAULT`）。同时：`SkinView.flip` 对 `NEW_SHAPE` 的楼要重建整座外盒（或让灯窗盒盖住新族的灯窗格），再写一个用假 `IBlockAccess` 断言 `Look` 四个字段的测试。

## 5. 全城本色 `SkinPalette`

- **唯一来源**：23 行（搁浅舱、20 座楼、根位三形态各一行，按 `Buildings` 行序），值按 style §3.6 的 2026-10-15 修订 + B12 霞 + D8；光色：中枢青 `NEXUS_CYAN = #4FE3FF`、龙骨芯 `KEEL_CORE = #E8FCFF`、暖白 `WARM_WHITE = #FFE6B8`、长庚金 `EVENING_GOLD = #FFC56B`；白芯彩边常量 `GLOW_*`（新材质 32 px 贴图芯 2 px、边 2 px；旧 16 px 部件贴图芯 2 px、边 1 px）。
- `accent(key)`、`accent(key, shape)`（根位按形状名：`core*` 熔橙、`root*` 根绿、其余潮青）、`accentOr(key, shape, fallback)`；`lab`、`lightness`（L\*/100）、`deltaE00`（Sharma 2005 校验过）。键是 ids.md §2 的楼键。
- 用它的地方：`CityRender.course` 的墙裙光线（停靠且有电时）、`SkinFeed` 给条目的本色、样板墙、界面的 `Tok.Building.lum`（两边各写一份值，`TokLumMatchesPaletteTest` 守着相等——改色时两边一起改）。
- **定色流程**（DECISIONS B11）：样板墙一轮定色后改 `SkinPalette` 和 `Tok`，重跑 `HullTextures` / `SkinTextures`，把 `PaletteTest.AWAITING_SWATCH` 里过了 12 的对子删掉，同步 style §3.6 / §8.6；0.19 起 `PaletteTest` 改读 `SitePlan` 的机位对子表。

## 6. 遮挡门控 `OcclusionGate`（style §8.4.1）

光影包下世界画完后的光（③）用深度缓冲判断「被不被墙挡住」靠不住时（P0），由它按方块逐件判断。**两种结论下画面都一样、一件不删**。

- **纯逻辑 `logic/occlusion`**：`SightCells`（每格 `CLEAR` / `SOLID` / `BOXES` + 地面高度）；`SightRay`（3D DDA，最长 160 格；眼睛所在格和目标格永远不挡；`BOXES` 格做射线 – 盒子相交）；`GatePiece`（稳定 id + 1–5 个采样点 + 宿主盒 `host` + `indoor` 盒）；`GateCore`：每 `EVERY = 4` tick 一轮，一轮最多 `MAX_RAYS = 400` 条射线，件按离眼由近到远，射线用完的件这轮保留上一轮结论；`FAR = 64` 格外的件每 2 轮判一次；滞后 `HIDE_AFTER = 2`（连续 2 轮全挡才藏，1 轮可见就显）；室内件只看相机在不在盒子里；粗筛：眼到件的线段不穿过任何楼盒、两端都不在地形下 → 直接可见；`Mode.DEPTH`（`render.occlusion = depth`）时 64 格内的件交给深度缓冲、只判远件；每轮发布一张不可变答案表。
- **客户端 `client/occlusion/OcclusionGate`**：客户端 tick 末从 `renderViewEntity` 脚底 + 1.62 打射线；件来自各渲染器 `add(Pieces)` 登记的源；粗筛的楼盒取盒子表每个条目的 `box`；渲染时 `draws(id, 距离)`（按配置模式）或 `visible(id)`（门控自己的答案）；世界卸载清空。**一轮里抛异常就整局关掉门控、此后全部显示**（宁可透墙不丢画面），记一次日志，要重启才恢复。
- **`WorldSight`**：我们的 `BlockSkin` 按形挡（`seeThrough()` 不挡、`closed()` 整格挡、其余按 `ShapeGeo.occluders`）；`Formed.HIDE` 的格不挡；构架整格挡；原版只认 `isOpaqueCube`（玻璃、水、树叶、花草都不挡）；地面高度用高度图。
- **登记**：各楼按 style §4「深度失效时」拆件，从它那一版起 `OcclusionGate.add(...)`：每轮列出自己的件（`GatePiece.panel(id, 心, u, v).host(楼盒)` 或 `.indoor(盒)`），画的时候问 `draws`。0.17 里唯一的件是深度探针的两件门控光。

## 7. 天幕层 `SkyLayer` 和日轨 `SunPath`

- **`Aurora`**：2 道帘 × 24 段 × 2 条带 = **96 quads**（`QUADS`），只按世界时间算（同一时刻同一组 quad），脚绿 `#6CFFB0` → 亮带 `#9BFFC8` → 顶紫 `#C9B8FF`，每个顶点朝相机的单位法线。
- **`SkySheet`** 两种放法：`sky` = 跟相机走的天幕层（半径 = 0.45 × 远裁面，仰角 18°–50°、左右 ±50°，第二道帘错开 12°、高 4°）；`world` = 钉在世界里的一幅（锚点外 128 格、宽 192、高 70–150）。
- **`client/sky/SkyLayer`**：`add(Source)`，源每帧列出要画的 `Sheet`；楼用 `Sheet.configured(...)`（按 `render.skyLayer`），探针直接用 `Sheet.sky` / `Sheet.world`。`RenderWorldLastEvent` 里画、跳过阴影 pass、加法混合不写深度、只用 quads、每顶点法线、相对 `renderPos*`、登记 `FarDraw`；一次异常后关掉并记日志。
- **`SunPath`**：太阳方向 = (−sinθ, cosθ·cosφ, −cosθ·sinφ)，θ 按原版 `celestialAngle`，φ = 光影包的 sunPathRotation（Complementary 默认 −40°：正午太阳在南边仰角 50°，仍从正东升、正西落）；帆的法线 = 太阳方向在东西竖直面上的投影（帆只绕南北轴转）。

## 8. 探针 `/fluxecho probe depth | sky | sun | bed`

- **认牌**（`ProbeMarker`）：每个探针和样板墙在自己框架的固定格立一块原版**立式告示牌**，字朝着操作者；四行：`[FluxEcho]`、种类词（`swatch`、`probe depth`、`probe sky`、`probe sun`、`probe bed`）、截图用的标签（「样板墙 swatch」「深度 P0」「天幕 N0」「日轨 -40°」「花床 S-P4」）、数据（花床 `n=<床数>`，样板墙 `v0.17`）。牌在框架里的格（前, 右）：样板墙 (3, −17)、depth (2, −5)、sky (2, −2)、sun (2, −3)、bed (1, −10)；朝向 meta 按字朝的方向：南 0、西 4、北 8、东 12。客户端 `ProbeMarkers` 每 20 tick 扫已加载的方块实体，由牌的位置和 meta 反推 `ProbeFrame`（操作者脚下格 + 朝向，`at(前, 右, 上)`）。**不要消息号、NBT 键、存档数据、注册名**；牌的格式登在 ids.md §18，发布后只读不改。
- **共同规矩**（`command/ProbeWorld`）：框架 = 操作者脚下格 + 朝向（yaw 吸附到四正向）；**体积里有方块实体或园区的部件**（`PartBlocks.code ≥ 0`；探针自己的新族方块和花床除外）就整条命令拒绝、一格不动，提示里写坐标（规则审查修过：以前只拒方块实体，会挖掉城里的墙、楼板、白肋）；超出 y 1–255 拒绝；放方块时开 `Builder.QUIET`；同一种探针 32 格内已有一座 → 这次命令把它拆掉（开关）。框里别的天然方块会被清掉，拆的时候只拆自己放的，清掉的不回来。告示牌要立在实地上：半空摆的牌会掉，之后同一命令找不回这座探针。
- **`probe depth`（P0）**：清空前 2–11、右 −5…5、上 0–5；前 8 格一堵 9 × 5 的舟壳墙（`hull` 0），右 2–3、上 1–2 是 2 × 2 舟壳窗（`hull` 8）。`DepthProbeRender` 画五件 1.5 格见方的光：墙前左（青，开深度测试）、墙前中（琥珀，关深度测试）——都该看见；墙后左（青，开深度测试、不过门控）——**开着光影还能透墙看见它 = P0 不过**；墙后中、窗后（金，关深度测试、过门控）——只该看见窗后那件。门控件用门控自己的答案（不随 `render.occlusion` 变）。
- **`probe sky`（N0）**：只立牌；`ProbeClient` 的天幕源在牌北边画天幕层（`Sheet.sky(0, −1)`）、南边挂一幅世界帘（`Sheet.world(牌, 0, 1)`），同一个极光并排比较。
- **`probe sun`**：清空前 2–9、右 −11…11、上 0–12；前 6 格一根 12 高的舟壳柱（`hull` 7，桅）；前 6 右 8 一圈 3 × 3 去心、5 高的玄基环（光井）。`SunProbeRender` 从桅顶（48 格）、井底（24 格）各画一条金线指向倾斜后的太阳，旁边淡灰线指向原版不倾斜的太阳；前 6 右 −8 上 7 一面只绕南北轴转的 3 × 5 帆，白线是法线（8 格），金线是它到太阳（20 格）。两种太阳都在地平线 5° 以下时不画。
- **`probe bed`（S-P4）**：没装植物魔法直接拒绝（`fluxecho.cmd.probe.bed.no_botania`，先判 `Mods.botania` 再碰 `mana/BedFlowers`）。`BedFlowers.kinds()` 列出每一种能种的花（16 种神秘花、16 种微光花、16 种高花、`BotaniaAPI.subtilesForCreativeMenu` 里的全部功能花和产能花）；每 16 张花床一排（前 3 起、隔 2，右 −8…7），每张床上用这种花的物品自己的放置（`onItemUse`，等于玩家右键床顶）种一棵，没种上的在聊天里列名（服务端的显示名：专用服务端上是英文）。

## 9. 样板墙 `/fluxecho swatch` / `swatch clear`（style §8.6，DECISIONS B11）

- **格位表 `SwatchLayout`**（33 列 × 12 行，§8.6 只给了内容，逐格表是 F06 定的，金表在 `SwatchLayoutTest` 里）：墙面在前 8 格（`BACK`），中列（第 16 列）在操作者正前方；前 7 格是浮雕层（`FRONT`）；前 4–6 是镜池（水，池底上 −2 玄基整块），前 3 和两侧是玄基池唇（`footing` 9），前 7–8 的上 −1 是一道玄基地脚。
  - 第 0–2 行（从下往上）光：22 种本色（20 座楼 + 根位三形，根绿出现两次凑对子）和一列暖白，第 0 行舟壳光缝、第 1 行亮灯、第 2 行同色暗灯；§8.6 必看对子并排（魔力蓝 – 守护蓝 – 星蓝 – 极光紫、羊皮纸 – 白 – 银 – 冰蓝 – 中枢青 – 潮青、根绿 – 叶绿 – 薄荷 – 根绿、琥珀 – 熔橙、纸灯暖 – 金 – 长庚金），组间隔一列舟壳；右端三格高的长庚金光缝、暖白龙骨（纪念态）和中枢青龙骨。对子的 ΔE00 由 `SkinPalette.deltaE00` 算，和 §8.6 对得上（± 0.06）。
  - 第 3–5 行：舟壳、玄基、苔铜、汉白玉、墨、宣（M1–M6）各一块 5 列的板，墙面上平的形、浮雕层上凸出的形（23 种凸形逐个核过「看得见它的形」）；第 30–32 列原版石头、石英、白桦对照。
  - 第 6–8 行：M7–M12 预留（空气，0.20 有方块再填）；第 9–11 行：装置件预留（「样板墙 2」，0.20）。**以后只填这几行，别的格不挪**。
  - 一共 2,829 个放置（含空气）。
- **建**（`SwatchCommand.build`）：64 格内已有一堵 → 原地重建（同一框架），否则在操作者面前新建；体积前 3–8、右 −17…17、上 −2…11，规矩同 §8；最后在池唇左角立牌。聊天报墙在哪、各行是什么（`swatch.rows`）。**拆**（`clear`）：64 格内最近的一堵，自上而下清空，上 −1 放回草、上 −2 放回泥土（原地面没存，不加存档数据），拆牌。
- **光**（`client/probe/SwatchLooks`）：插在 `ShapeRender` 的皮肤槽前面，样板墙的格按 `SwatchLayout.glows(frame)` 给 `Look`（强调色、醒着、灯亮灭、龙骨色），别的格交给下一个；墙出现或消失时 `ProbeMarkers` 让它的区块重建。**亮灯行全天亮、暗灯行全天熄**（为了正午、黄昏、夜三张都能比，和灯规则不同）。预览世界（NEI）不给样板墙的光。
- **预览**：`logic/probe/SwatchPreview`（慢组，Java2D）按同一张表画 `build/previews/0.17.0/swatch.png`（1670 × 1107），不断言画面。

## 10. 机位 `/fluxecho cam <A|B|C|D|E>`（masterplan §7.3）

- **`CamPoints`**：以中枢底座中心为原点、按中枢的 `front()` 算（新园区朝东；老园区按它自己的朝向）；高度是**脚**的高度（眼睛再高 1.6），落在格中心。`toWorld(a, r, fx, fz) = (a·fx − r·fz, a·fz + r·fx)`。

| 机位 | (a, r) | 高（底座上） | 朝向、俯仰 |
|---|---|---|---|
| A 城门 | (31, 0) | Y0 + 15 | 朝船尾（−a），平视 |
| B 舰桥 | (0, 0) | Y0 + F + 2（F = `DomeGeneratorShape.datum(Y0)`，默认 40） | 朝船尾，俯 15° |
| C 环墩 | (−79, 28) | Y0 + 8 | 朝船头（+a），平视 |
| D 系索台 | (0, 0) | Y0 + 52 | 朝船尾，俯 60°（楼 0.27 才有） |
| E 回望丘 | (−110, 0) | Y0 + 9 | 朝船头（楼 0.27 才有） |

- **命令**（`CamCommand`）：在操作者维度里已加载的**本队**中枢中挑最近的一座；先下坐骑，`setPlayerLocation`、设朝向和俯仰、开飞行（`allowFlying`、`isFlying`、`sendPlayerAbilities`），清摔落距离；脚或头所在格是实心方块时生存模式拒绝、创造模式照去。**`allowFlying` 存进玩家 NBT，生存模式下不会自己关**（只限 OP 的调试命令，可接受；以后要改就在离开机位时还原）。

## 11. 配置

| 键 | 默认 | 说明 |
|---|---|---|
| `render.occlusion` | `gate` | `RenderConfig.occlusion`（`OCCLUSION_GATE` / `OCCLUSION_DEPTH`）；可选 `depth`，其余值读成 `gate`。P0 过了才在补丁版翻 |
| `render.skyLayer` | `world` | `RenderConfig.skyLayer`（`SKY_WORLD` / `SKY`）；可选 `sky`。N0 过了才在补丁版翻 |
| `style.hullBrightness` | 1.0（0.85–1.0） | `StyleConfig.hullBrightness`（`HULL_MIN` / `HULL_MAX`，读到的值再夹一次）；只压新族的舟壳（`SkinIcons.tint`），旧构架是贴图、不受它管 |

三个都是客户端项（注释以 "Client:" 开头），只加键、`Config.VERSION` 不提。键登在 ids.md §7。

## 12. 不变量

1. **ID 只增**：8 个注册名、部件码 29 / 46 / 54–62 / 96–127、deck 13、fitting 14、`SKIN_TABLE = 7` / `SKIN_DELTA = 8`、`Skn`、`SkinState` 的码 0–5、`SkinCodec` 版本 1、告示牌格式、三个配置键——发布后一个字不改；`DECK_TYPES = 13`、`FITTING_TYPES = 10` 和 0.16 的 `*_NAMES` / `*_LIGHT` 不动（新 meta 用 `known`）。
2. **形没有朝向**，几何只看邻居；`ShapeGeo` 纯函数、输出逐字节确定；每个 quad 每个顶点都有单位法线；只出 quad。
3. **新族不为画面改方块**：亮不亮、本色、显影都在盒子表；世界里的方块只有形号。
4. **盒子表从不改世界、不改园区行为**；不在任何条目里的格子照 0.16 画；一座楼的灯整座一起翻；节流每楼 10 秒、全城每秒 4 盒。
5. **旧楼白天发光 ≤ 3%、夜里 ≤ 12%**（`LegacyGlowShareTest`）；遮罩 = 生成器（CRC 钉死）。0.17 发布说明里「黄昏时老城的灯窗一盏盏亮」靠的就是这条。
6. **世界内的光只在 `RenderWorldLastEvent` 画或进区块网格**，不用 TESR；`SkyLayer`、`DepthProbeRender`、`SunProbeRender` 跳过阴影 pass、只用 quads、每顶点法线、相对 `renderPos*`、登记 `FarDraw`、只按时间算（幂等）；门控的答案在 client tick 里算，渲染只读。
7. **探针和样板墙不碰园区**：框里有方块实体或园区部件就拒绝。
8. **`ShapeRender` 只有一个皮肤槽**，插东西走 `SwatchLooks.install(next)`。
9. 可选模组：植物魔法的类只在 `mana/BedFlowers`（`IsolationTest`）。

## 13. 测试

| 测试 | 组 | 守什么 |
|---|---|---|
| `logic/shape/ShapeGeoTest`（19）、`MeshCheck` | 快（`everyCellIsClosedInEveryNeighbourhood` 慢） | §3.3.4 的面数表、水密（每条棱正反各走一次）、无破面、法线、转 90° 一致、碰撞盒、纯函数、剔除、跨格叠层、quad 上限、物品网格、族和码对 ids.md |
| `logic/KitClimbTest`（+1） | 快 | 新族的格从 `ShapeGeo.boxes()` 取碰撞盒，36 个码在全部 16,384 种邻域里盒子都从地面无缝叠到顶 |
| `logic/PartsTest`（扩）、`skin/SkinPartsTest`、`logic/LegendTest`、`logic/PlantOrderTest`、`campus/PlantBedTest`、`campus/ShaftClearTest`、`client/shape/ShapeRenderFormedTest` | 快 | 码双向、0.16 计数不变；8 个注册名 = ids.md；每楼图例和 CRC = `kit.py`；活物料的施工顺序；花床的 `canSustainPlant`；光梯穿过新族；`Formed.HIDE` 后邻居的面回来、方块标志 |
| `client/shape/GlowTextureTest`（5 快 + 2 慢）、`FormPreview`（慢，出图） | 快 / 慢 | 每张 sprite 32×32 二值 alpha、发光芯和边的位置、舟壳不亮过 `#EDF1F3`；慢：生成器逐像素 = 提交的贴图、光色 = `SkinPalette`；`forms.png` |
| `logic/skin/LampRuleTest`（9）、`SkinTableTest`（21） | 快 | 夜窗半开区间、醒着的状态、光波、`isAwake v0`、LEGACY 的灯部件和并进的构架；状态机、去抖、节流、编码、坐标打包、快照经节流翻灯、显影 |
| `logic/skin/LegacyGlowShareTest`（5） | 慢 | 35 个旧形状 = LEGACY；运行时遮罩 = 生成器；并进的部件最少；白天 ≤ 3%、夜里 ≤ 12%；不并就超标 |
| `campus/SkinSyncTest`（8）、`PreviewSkinTest`（3） | 慢 | 假世界里中枢和楼各一条；登录、进 512 格、换维度各只发一次；`Skn` 写读；楼没了发 remove；搬走的中枢；预览条目 |
| `campus/SkinViewerKeyTest`（2）、`client/skin/ClientSkinUnloadTest`（2） | 快 | 收件人按 UUID、重生不留旧对象；卸载只清本维度（规则审查加的） |
| `logic/skin/PaletteTest`（12）、`PaletteSyncTest`（3）、`GeneratedTexturesTest`（2，慢）、`logic/ui/TokLumMatchesPaletteTest`（4） | 快 / 慢 | 23 行本色 = style §3.6、CIEDE2000、对中枢青 ≥ 10、同框对子 ≥ 12、待样板墙的例外表；七行旧贴图的明度区间和白芯；生成器逐像素；`Tok` 的 `lum` = 本色 |
| `logic/occlusion/OcclusionGateTest`（13） | 快 | 墙后藏、窗后显、百叶只在叶片处挡、滞后不闪、同轮同答、射线预算由近到远、远件隔轮、深度模式只判远件、室内件、粗筛、端点格不挡、件消失 |
| `logic/sky/SkyLayerBudgetTest`（7） | 快 | ≤ 96 quads、同时刻同 quad、法线、天幕层在远裁面内不贴脸、世界帘不跟相机、−40° 日轨 |
| `logic/probe/SwatchLayoutTest`（10）、`CamPointsTest`（6）、`ProbeRigsTest`（6）、`command/ProbeGuardTest`（2）、`SwatchPreview`（慢，出图） | 快 / 慢 | 样板墙逐格金表、对子 ΔE00、镜池和地脚的格数；五个机位 = masterplan §7.3；探针几何、告示牌反推框架、命令词；框里有园区部件时拒绝；`swatch.png` |

预览 PNG 都由慢组写到 `build/previews/0.17.0/`（`forms.png`、`swatch.png`，加上界面的 `ui-tokens.png`、`ui-atlas.png`），不入库，随验收卡发。

## 14. 已知问题、只能进游戏看的

- **只能进游戏看**（验收卡 `refactor/v0.17/ingame.md`）：样板墙定色（必看对子，魔力蓝 – 守护蓝最要紧）；P0、N0、日轨、花床四支探针；舟壳在 Complementary 下是否过曝、`hullBrightness` 0.85 压不压得住；跨格图案的接缝；白芯彩边读不读成光；圆、坡、拱腹逐顶点法线的明暗和区块重建耗时；黄昏灯窗的光波、一座楼不半亮、断电 10 秒后灭；旧楼白天暗下来的光（**光梯的光圈白天找不找得到**，唯一可能碍事的一处）；配件染本色；NEI 预览是白天；机位的落点和朝向；门控在边缘会不会闪。
- 旧贴图是单层辉光，旧楼的灯着本色后白芯也被染色（等各楼新贴图）。
- 潮汐井、全息沙盘、气象站、秘法庭的墙裙光线（读 `SkinPalette`）和名牌、扫光、核心贴图（还读 `TileX.COLOR`）颜色不一样；`TileX.COLOR` 属各楼绿区，随各楼那一版改。
- 探针在半空摆时告示牌会掉，之后找不回；`swatch clear` 只能把地面放回草和泥土；`ProbeMarkers` 每秒扫一次（≤ 1 秒延迟）；门控一轮出异常就整局全显、要重启。
- `cam` 在生存模式下打开的飞行不会自己关。
- `fluxecho.cmd.usage`（`/help` 里那条）还只列旧命令；新命令词错时报 `fluxecho.cmd.probe.usage`。要列全只改值、不改键。
- 新码没有配方和成本；窗的连续框（按 `Quad.conn` 选框）没做，贴图每格都是整框；`ShapeRender` 没有 `ThreadSafeISBRH`。
- 花床的花悬空 4/16。

## 15. 给以后各版

- **新楼（0.19 起）**：`b_<形状>.py` 和 `XShape.build()` 用每楼图例放新族的码；`TileCityModule.define` 改成 `partOf(shape, ch)` / `soft(shape, ch)`；`SkinProfiles.register` 自己的档案（`NEW_SHAPE` 起步：显影、变体、灯部件）；接上 §4.8 的 `ShapeRender` ↔ 盒子表；世界画完后的光按「深度失效时」拆件登记到 `OcclusionGate.add`，天上的光用 `SkyLayer.Sheet.configured`；新码补配方和成本（`EchoRecipes`、`PartRecipes`）。
- **0.20**：后六种材质的族（`skin_rite` / `skin_flux` / `skin_void` 等，ids.md §3.1）和样板墙第 6–11 行（`swatch 2`）；光井用 `SunPath`。
- **0.28**：`SkinTrack.ascend()` / `memorialize()`、`MEMORIAL` 的暖白龙骨和长庚金描边（`CityPalette`）。
- 原型 P3 过了以后给 `ShapeRender` 加 `ThreadSafeISBRH`（先 `javap` 核实 Angelica 的注解）。
