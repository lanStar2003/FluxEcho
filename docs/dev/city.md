# 开发手册 · 回响之城：城市模块框架、新部件、生态穹（0.11.0 起）、天顶和根（0.12.0）、四亭（0.13.0）、门与仓（0.14.0）

> 改 `city/`、`city/client/`、`logic/` 里的城市类（`SiteFamily`、`ShapeKit`、`UnitLayout`、`CityBuild`、`EcoDomeShape`、`DomeGeneratorShape`、`RootShape`、`ArcaneCourtShape`、`BloodCovenantShape`、`HuntingGroundShape`、`ManaFountainShape`、`ResonanceGateShape`、`DualFoilVaultShape`、`ModuleCores`、`ModuleLook`、`DomeLook`、`RootLook`、`ArcaneLook`、`BloodLook`、`HuntingLook`、`ManaLook`、`CanvasLook`、`VaultLook`、`GateLook`、`Tide`、`XRay`、`ModuleMap`、`UnitBrowse`、`PreviewSites`），或者要给城里加一座新建筑之前读这一篇。园区、建造引擎、投影、地形判定、回响档案馆在 [campus.md](campus.md)；中枢、研究、显化、多方块框架、**相位生长和光梯**在 [nexus.md](nexus.md)（§4.1、§4.2）；画在世界里的东西先守 [gates.md](gates.md) §5；总览见 `CLAUDE.md`。
> **方案在仓库外**：建筑方案 `D:\Code\FluxEcho-campus\city\CITY.md`（下文「CITY §x」）和它的生成器 `city/gen/`（`kit.py` 图例和自检、`b_hall.py` 生态穹和二向箔仓、`b_pav.py` 四座亭子、`b_gate.py` 谐振塔、`b_sky.py` 穹顶发生器的吊舱和腿、`b_root.py` 潮汐井、深根、地脉核、`siteplan.py` 场地检查、`report.py` 计数和用料、`plans.txt` 每个形状的每一层）；每个版本的合同在 `D:\Code\FluxEcho-campus\spec\M<n>.md`（0.11.0 是 `M8.md`，0.12.0 是 `M9.md`，0.13.0 是 `M10.md`，0.14.0 是 `M11.md`）。**文字和生成器对不上时以生成器为准**，Java 逐行移植生成器（像 `ArchiveShape` 移植 `spec/gen/archive.py`），改了生成器就把 CITY.md 的文字改过来。
> 路径相对 `src/main/java/com/fluxecho/`。`EchoText.t("x")` 对应 lang 键 `fluxecho.x`。文中数字和键都按 0.14.0 的代码核对过。

## 0. 定位

用户在 0.10.1 之后的指示：「根据计划 持续推进任务 先把计划中的结构与建筑全部做完」——**先把 BLUEPRINT 3.3、3.7–3.9 里的每座结构和建筑都立起来**（由中枢自己建、能走进去、能成型），各模块真正干什么、数值，等结构做完再做（0.19.0 起，§11）。CITY.md 是为此写的建筑方案：园区变成一座**井字形的城**——中间一格是广场和中枢；四条轴上是大门和三座大厅；四个对角是四座亭子；相位 III 开出四条笔直的井字街，通到八角形的外环步道，外面立十二个外环位；源点时代再围一圈十六个第三环位。中枢上方 `Y0+40` 悬着吊舱（先立在六条腿上，相位 IV 浮起来），周围八个浮空平台，顶上是轨道锚；下方一口井一直深到 `Y0−57`；一条光梯贯穿整条轴：根 ↔ 中枢台 ↔ 天顶 ↔ 轨道（CITY §0）。参照《考工记》的「匠人营国，方九里……九经九纬」和井田（九格围着一口公用的井，这里根位真的是中间那格下面的井）。

**0.11.0「百工」是第一批**（CITY §5.1）：通用的模块框架（场地族、形状工具、单元、通用建造计划、模块核心家族、通用窗口、通用渲染钩子、新部件 SOCKET/BASIN/ANCHOR/WATER）加第一座新建筑**生态穹**。基础设施先做完，后面的批次大多只是数据：一个形状移植、一个 `ModuleSpec`、一个渲染、窗口的小调整（§7、§11）。

**0.12.0「相位」是第二批**（合同 `spec/M9.md`），也是最大的一批：中枢的**相位生长**（`grow:<p>` 任务、按长到的相位检查中枢、`PHASE_NOW` 2、`phase_2` 有真实花费、相位 II 的底座中心是光梯台，nexus.md §4.1）；**光梯**（LIFT 部件、乘梯、光束，nexus.md §4.2，§5）；**轴上的两个族**（§4.12）——**天顶族**（吊舱悬在 Y0+40、腿在 LEGS 阶段、在空中建、不检查的门格、带高度的 keep-out）和**根族**（沉箱 SINK + DRAIN 阶段、地下投影在鬼影之外加画透视的棱、深度检查）；最简的**相位合金**、**共鸣玻璃**物品（研究花费和核心显化要用，nexus.md §2）；两座建筑**回响穹顶发生器**（系泊在六条腿上，§6.2）和**潮汐井**（§6.3）。框架这边多了：一个城市模块可以有几种形状、蓝图可以伸出形状的盒子（`CityBuild` 的偏移）、`covers()` 只罩形状的盒子、轴上的模块不占内环、名牌挂在控制器上方、平面图认光梯台、画到 512 格的地标（§4）。

**0.13.0「四亭」是第三批**（合同 `spec/M10.md`）：**亭子族 PAVILION**（§4.13）——`ShapeKit` 移植 `b_pav.py` 的八角平面、切角门、按节奏排壁柱的外壳、中心控制器、后墙门位；通用模块计划 `BuildPlan.module` 认对角位（`pavilionController`：蓝图正面沿 A 朝中枢、控制器在八角中心的控制台上；清理占地八角外扩 1；场地地面是 apron 加对角林荫道 `pavilionFloor`；附属从第 8 排起）；keep-out 和保护是按行切的整列盒子（apron 和林荫道，每行相连的一段一个）；中枢按八角中心收编；地面上的模块也按形状高度查 `noRoom`；内城上限 8 的准备（「建造」页的模块列表翻页、总图对角位的牌子挪到林荫道上、NEI 预览让四座亭子各占一个对角）；建造计划多了一个按格排序的钩子 `ModuleBuild.order`（猎场的看台一圈一圈往外）；窗口的平面图可以加只看建筑的层（`ModuleMap.overview`，血契坛的上两层台）。四座建筑：**秘法庭**（§6.4）、**血契坛**（§6.5）、**回响猎场**（§6.6）、**魔力泉阵**（§6.7），都在相位 II 研究、占内环（相位 II 只有 4 个位置，档案馆和生态穹建好后只剩两个，CITY §6.2「到相位 III 之前要选」）。`BuildJob.V` 没提（还是 2），没有新配置键、新包。

**0.14.0「门与仓」是第四批**（合同 `spec/M11.md`）：**大门族 GATE**（§4.14）——门楼跨在大门路上：通道（`|R|` ≤ 6）永不建，只在路中间的亭里放控制台座和控制器（`gateController`：盒子中间那一列在路的轴上、前排在 A 23、朝中枢）、只铺路没铺到的通道地面（`gatePassageFloor`）；清理绕开通道、只清桥那 91 根柱子 Y0+7 往上（`GATE_HEADROOM`），大门路、路口和门槛的加宽、四根塔柱原样留着；keep-out 和保护按计划真正动的每一列、从它动的最低高度起（`BuildPlan.gateKeepOut`：桥和路中间的亭那几列是带高度的盒子，园区任务照样在下面铺路、立塔柱）；中枢按控制器格和朝向精确收编；光桥到通道口（`GATE_DOOR` A 22.5）；0 号位在窗口、牌子、聊天、终端里都叫「大门」（`build.gui.site_gate`）。**画布部件 CANVAS**（deck 12 / 码 28，§5）和**画布上画物品**（`CanvasLook`、`Paintings`、`CanvasRender`：单元的物品色调分离后大大地画在它那面画布上，§4.10）；内境门位多了画布的软孪生 `$`（§1）；框架多了挂在墙上、从下面的楼面用的单元（`UnitLayout` 的 `usedFrom`，§4.2）、点没有样本座的单元任何一格潜行取回（`TileCityModule.takeSlot`，§4.8）、墙裙光线认通到外面的路（`ModuleLook.outside`，§4.10）。两座建筑：**谐振塔**（大门位，§6.8）和**二向箔仓**（第三座大厅，§6.9），都在相位 II 研究、占内环。`BuildJob.V` 没提（还是 2），没有新配置键、新包。

**一座建筑「做完」**（CITY §1.6，八条都要有，括号里是测试）：
1. 形状：`logic/XShape` 移植自生成器；计数、对称、控制器、门洞空气、单元、行走测试（`XShapeTest`、`XWalkTest`）。
2. 中枢自建：场地族上的 `ModuleSpec`；分阶段的建造计划，HARD = 结构检查格（4 个朝向 × 每个允许的场地）；投影、发射和落地特效、场地地面、keep-out、按 `PartRecipes` 算的用料（`ModulePlanTest` 的做法）。
3. 成型：`TileCityModule` 子类、结构定义（部件经 `PartBlocks`）、成型扫光、溶解的格子、在场地上停靠。
4. 能走进去、家具是整组单元：点单元的任何一格就是这个单元，单元的槽里放东西，东西画在原位（样本座、画布、种植床）；动线按方案。
5. 控制器在眼睛高度，站在自己的控制台座上，朝向按方案。
6. NEI：自己的三维预览（`InfoContainer`）和中枢城市预览里的位置。
7. 界面：通用模块窗口（状态 / 单元 / 平面，§4.9）。
8. 招牌画面：成型、待机（停靠）、运行（有电）各有样子，光影安全（gates.md §5：只在 `RenderWorldLast`、跳过阴影 pass、quads + 法线、`FarDraw`、相对 `renderPos*`、每帧幂等、每部分单独失败）。

结构阶段**不做**的（CITY §5.3）：单元真正干什么（并行回响、存储、发电、护盾、算力）、数值、覆层、相位核、潮汐、各建筑门位后面的折叠区内境。

## 1. 原则（CITY §1，每座建筑都守）

**一套语言**（CITY §1.1）：
- **体量套件**（每座楼全用）：地基环（frame FOUNDATION，占地边上的 y0；里面是 FLOOR 阶段铺的软地面，同档案馆的 824 格）；**墙裙** PANEL_DARK 在 y1 一圈（亭子、空中小屋也有）；墙是 PANEL 加**按楼不同节奏**的 PANEL_LIT 壁柱（生态穹门厅 4、二向箔仓长边 4 短边 5、秘法庭 3、血契坛 4、猎场 3、魔力回廊 2、大门 4、智子 3、沙盘 3、源点之心 4——邻居之间最先不同的就是节奏）；**光檐带**（deck CORNICE）在每座楼自己的高度：y5 空中小屋，y6 魔力/智子/吊舱，y7 生态穹鼓座/源点之心，y8 猎场，y10 沙盘，y12 档案馆/秘法庭/生态穹门厅，y16 大门，y24 二向箔仓——同一条轴上的邻居不同高；每座楼有**自己的顶**（生态穹的玻璃穹顶、二向箔仓的锯齿碑、秘法庭的八角尖塔……）。
- **光只走线**：壁柱、檐口、门槛、0/45/90° 线上的肋、亮的回廊边、LIT 地面脊线；不打泛光、没有发光的墙；室内靠亮线加渲染的吊灯和光晕。特效也守这条。
- **平面干净**：只用 0° 和 45°，八角 `oct(h, round(4h/3))`（12/16、15/20、20/27…），圆只用于穹顶、环和球（半径 ≥ 6），1:2 只用于路；蓝图沿跨向镜像（StructureLib 的规矩），镜不了的（螺旋坡道、种子装饰）做 SOFT（不检查，§1 形状规则）。
- **一种材料、多种光色**：只用 0.10.0 的部件和锁定的调色板（campus.md §3：地砖 `#3E4C58`、地缝钢 `#101C28`、墙板 `#45525E`、深色 `#2C3842`、钢 `#2C5470`、光 `#C8F8FF` 在 `#1C3446` 上、青 `#4FE3FF`；玻璃是钢框镂空的 GLAZE）。**模块的主题色只出现在渲染的光里**（墙裙光线、脉络、全息、室内特效、名牌、投影），永远不做方块染色——用户要的「每座建筑风格一致」（深蓝钢 + 细青光线），也让部件数保持很少。主题色：档案馆 `0x8A5CFF` · 生态穹 `0x7BE08A` · 二向箔仓 `0xF2EBDD` · 秘法庭 `0xC77DFF` · 血契坛 `0xE0344A` · 回响猎场 `0xFF9A3C` · 魔力泉阵 `0x6AA8FF` · 谐振塔 `0x5CF2C2` · 穹顶发生器 `0x7DB8FF` · 潮汐井 `0x3FC8E8` → 深根 `0x5FD3A8` → 地脉核 `0xFF7A3C` · 智子阵列 `0xFF8CE6` · 全息沙盘 `0x66F0FF` · 天光帆阵 `0xFFD27A` · 观星台 `0xB9C8FF` · 水滴机库 `0xD8E4F0` · 天穹气象站 `0x9AD0FF` · 轨道锚 `0xFFFFFF` · 源点之心 `0xF6E7FF`。
- **入口**：朝前的楼（大厅、大门、外环侧位、第三环、空中小屋）用**套件门**（`ShapeKit.portal`）：两扇 3×4 的门（`x = MID−3…MID−1`、`MID+1…MID+3`，y1–4，两格深必须是空气），门间柱里自下而上是控制台座（y1）、控制器（y2，眼睛高度）、两节立柱（y3–4），y5 光檐门楣，门外两格的悬浮环段雨棚（y5，decor）——和档案馆的门一模一样（大门照生成器不用套件门：两座翼楼各一道 3 宽、两格深的正门和 y5 门楣，控制器在桥下路中间的亭里，§6.8）。中心对称的楼（亭子、外环对角位）开四个切角门（离对角线 2 格内的切角格，4 高，两格深空气，y5 门楣，亮门框），控制器在中心的控制台上：从中枢那边沿对角线走进去、在楼的中心操作。竖向的（吊舱、轨道锚、根）坐光梯进，控制器在梯台旁的控制台上、眼睛高度。
- **名牌**：门口雨棚上方（中心对称的楼在中心控制台上方）一块朝相机的名牌，模块色，48 格内（§4.10）；铺砖的箭头指着每扇门。

**不规则但统一**（CITY §1.2，效果从大到小）：研究顺序（每种模块有场地偏好，先研究哪个就先填哪个场地，两座园区不会一样）；四个朝向（每座楼都朝中枢，同一座楼在不同场地上被看到正面、侧面或背面）；高低不同的天际线（地面上 8–33，天顶 `+40…+49`，根到 `−57`，同轴同对角的邻居不同高）；**种子装饰**（DECOR 阶段按 `Mix.seed(控制器)` 选：雨棚 2/3/4 格深，一侧 apron 外排一个「附属」——种植墙、灯排、长椅或全息牌柱；都是蓝图外的 SOFT，检查的形状照样镜像，装饰立不立都能成型，§4.3）；铺砖（Voronoi 缝、深色团、侧叶、种子光井）；楼之间留草地（园区里的公园）。永远不变的：调色板、套件门、墙裙和檐口的词汇、光只走线、主题色只在光里。

**预算**（CITY §1.3，括号里是硬上限）：

| 项 | 预算 |
|---|---|
| 每个形状的检查格 | ≤ 7,000（12,000）；现在最大是档案馆 5,055 |
| 每项任务的步骤（检查 + 软 + 空气 + decor） | ≤ 8,000（12,000）；档案馆 6,237、生态穹 3,614、穹顶发生器 1,637（含 234 格腿）、潮汐井 3,507（另有沉箱的挖和排水各 7,896 步，按清场算，不算在这里）；0.13.0 的亭子（形状本身）秘法庭 2,615、血契坛 2,251、回响猎场 1,971、魔力泉阵 1,672；0.14.0 谐振塔 2,774、二向箔仓 5,543 |
| 每项任务的放置时间（`buildBlocksPerTick` 2） | ≤ 4 分钟（另加阶段节奏） |
| 每项任务的清理体积 | ≤ 40,000 格（8 格/tick 约 4 分钟）；超了分两遍清；穹顶发生器 569 根柱子 7,111 格、潮汐井挖 7,896 格；亭子都是 585 根柱子，秘法庭到 Y0+35 共 20,475 格、血契坛 11,115、猎场 7,020、泉阵 5,850；谐振塔 471 根柱子 8,874 格（通道上只有桥的 91 根、从 Y0+7 起）；二向箔仓 1,073 根柱子到 Y0+29 共 31,117 格 |
| 每项任务的 EU（每块 256） | ≤ 2 M |
| 客户端每座楼 | **近**（相机在里面或离盒子 ≤ 24 格）≤ 2,000 quads；**中**（≤ `effectRange` 128）≤ 300；**远** ≤ 64 |
| 画到 512 格的地标 | 每个 ≤ 400 quads（中枢光柱、穹顶壳、大门尖塔、天空光柱、智子天幕、卫星、源点之心） |
| 样本座、画布里的物品 | 最近 256 个、24 格内（档案馆书的规矩） |
| 广场上整帧 | ≤ 12,000 quads |
| 区块网格 | 只有 ISBRH；溶解的格子不画（`Formed` 的 HIDE） |
| NEI | 模块预览 ≤ 7,000 格；中枢城市预览（6–10 级）≤ 60,000 格，不铺地面 |
| 服务端 | 成型复查每 100 tick；单元闲着不花东西；单元内容走增量包，压缩后 < 28 kB（`BookPacket` 的做法） |
| 竖向 | 天顶和轨道要 `Y0+57 ≤ 255`；井 `Y0 ≥ 32`、深根 `Y0 ≥ 45`、地脉核 `Y0 ≥ 62`，提供任务时检查，不够就 `no_room` 带原因。0.12.0 实际查的：吊舱清理盒的顶 `Y0+52 ≤ 255`（`DomeGeneratorShape.noRoom`，`build.module.why.height`；轨道锚 0.17.0 再收紧到 +57），井底 `Y0−26 ≥ 6`（`RootShape.noRoom`，`build.module.why.depth`）。0.13.0 起地面上的模块（大厅、亭子，没有 `Envelope`）也查高度：形状的顶层 `Y0 + height − 1 ≤ 255`，不够就 `build.module.why.height`（`ModuleSpec.noRoom`）；放核心允许 `Y0 ≤ 225`（`ItemBlockNexusCore.HEADROOM` 30），所以实际只卡秘法庭（高 33，要 `Y0 ≤ 223`），别的大厅和亭子在任何能放核心的园区都放得下（0.14.0 的二向箔仓 27 高、谐振塔 18 高，大门族也照这条查）；形状上面的清理盒不算，超出世界的格子建造器本来就不碰 |

**形状规则**（CITY §1.5）：
- 本地坐标同 `ArchiveShape`：`x` 横向 `0…W−1`（`MID` 中间）、`y` 向上（y0 是地基层，人站在它上面）、`z` 从正面（z0 朝中枢；中心对称的楼是蓝图的正面）。一张图例全城唯一（§4.1）；档案馆的字符意思不变，它的 `S` 等不给城里的形状用。
- **四种格子**：检查格（结构检查看的，HARD 步骤）；**软格**（`.` 地砖、`,` 草、`;` 泥土、`=` 水、`+` 光带地砖、`_` 地缝钢、`{` 坡道块、`}` 坡道踏板、`|` 坡道栏杆、内境门位的 `[` 深色墙裙、`!` 光缝墙板、`/` 窗格、`$` 画布（0.14.0）：SOFT 步骤，不检查，可以不对称）；**必须是空气**（`-`，AIR 步骤，按空气检查）；**decor**（盒子外：雨棚、种子装饰，SOFT）。
- **内境门位**（CITY §4.15）：有内境的建筑在蓝图轴上留一个 3×4 的门位。门位的格子还是原来那堵墙的方块，只是写成软格的「孪生」字符（`w L G W` → `[ ! / {`，0.14.0 加画布 `Q` → `$`；生成器 `Shape.gate_niche`、Java `ShapeKit.Shape.gateNiche`，`ShapeKit.checkedTwin` 反查是哪种墙）：照样建（SOFT 步骤，阶段跟着那堵墙），结构检查不看。0.19.0 起在这里开光门，不用改检查的形状、不用提 `BuildJob.V`。生态穹的门位是鼓座后墙正中 `x 12–14, y 1–4, z 34`（12 格，§6.1）；四座亭子（0.13.0）都在后面那个平面的正中 `x 11–13, y 1–4, z 24`（各 12 格，生成器 `b_pav.back_niche`、Java `ShapeKit.backNiche`，常量 `PAV_NICHE_*`、`pavilionNiche`；y1 是深色墙裙 `[`，x12 是壁柱 `!`，两边按各自的墙是墙板 `{` 或窗格 `/`，§6.4–6.7）；二向箔仓（0.14.0）的门位在后墙二向箔的下半中间 `x 12–14, y 3–6, z 34`（12 格画布 `$`，常量 `NICHE_*`、`inNiche`：走进画里），二向箔是一个单元、单元格永远是检查格，所以这 12 格**从二向箔这个单元里拿出来**，二向箔留下另外 131 格（替用户做的选择，§6.9）；谐振塔没有门位（它自己就是门）；回响档案馆照 CITY §4.0 到 0.19.0 才放开，它的后墙正中（讲台后面）现在还是检查格——0.10.x 已经建好的档案馆要照旧成型，放开时那一版自己提 `BuildJob.V`。
- **不检查的门格**（0.12.0，CITY §4.8）：生成器留空、以后的批次要打开的格子，现在建成软格、结构检查不看——穹顶发生器吊舱的八个门洞（3 宽、y1–4，共 96 格）先用窗格 `/` 封着，天环的栈桥来了再开；吊舱屋顶上光梯井的井口（y6–9）先用和四周一样的方块盖着（`{` 墙板，y9 是 `/` 窗格），轨道锚来了再开。生成器自己的格子留在 `DomeGeneratorShape.RAW`（校验和钉的就是它），建的是加了这 100 格的 `SHAPE`。
- **镜像**：检查格和空气格沿 `x` 镜像（`symmetric()` 跳过软格和登记的不对称格）；两座楼梯互为镜像（右边 `mirror = −1`）。0.12.0 的腿是生成器在 R ≥ 0 一侧用 `StrictMath` 算、再镜像过去的（四舍五入两边会不一样），根的螺旋坡道是软格（螺旋没法镜像，CITY §6.1 第 9 条）。
- **整组单元**：一个单元 = 种类、格子、槽数、可选的朝向；点它任何一格就是这个单元；单元格永远是检查格；**单元顺序就是槽号，发布后永不改**（`LibraryUnits` 的规矩）。
- 控制器在 y2、下面 y1 是控制台座（血契坛、潮汐井在台上高一格）；门 3 宽 4 高、两格深的空气；站的地方上方留 2 格净空（人高 1.8）；旋转楼梯用档案馆的 3×3 井（每个环格一级、每级半格、12 级升 6，楼梯上方转一圈后净空 3.5）。
- **自检**（每个生成器里有，Java 里是单元测试）：`kit.summary` 的计数、`symmetric`，和从正门外走进去的**行走测试**（以半格为单位的广度优先，一步最多升 0.5、最多落 3，光梯连接上下梯台）：要走到眼睛高度的控制器和每个单元。CITY 的二十个形状都通过。

## 2. 场地（CITY §2.1–2.2；`logic/SiteFamily`、`logic/CampusPlan`）

**场地族** `SiteFamily`（枚举；名字可能存进 spec，永不改；ordinal 不存）：

| 族 | 场地 | `opens`（相位） | 外形 | 控制器 / 朝向 | 现在（0.14.0）的状态 |
|---|---|---|---|---|---|
| `HALL` | 4、2、6 | I | 横 27、深 ≤ 35，前排径向 24（`HALL_FRONT`，到径向 58）；apron 2；前庭径向 20–23 | 轴上的套件门，`Y0+2`，朝中枢 | 建：档案馆、生态穹、二向箔仓（0.14.0，§6.9） |
| `PAVILION` | 1、3、5、7 | II | `oct(12,16)`（25×25）中心 (±36, ±36)（A、R 24–48）；apron `oct(14,18)`；对角林荫道 | 中心控制台 `(±36, Y0+2, ±36)`（血契坛 `Y0+3`）；蓝图正面沿 A 朝中枢（1、7 朝 −A，3、5 朝 +A） | 建（0.13.0，§4.13）：秘法庭、血契坛、回响猎场、魔力泉阵 |
| `GATE` | 0 | II | A 23–39、R −15…15（31×17），R −6…6 的通道永不建 | 路中间的亭 `(A 30, R 0, Y0+2)`，朝 −A | 建（0.14.0，§4.14）：谐振塔 |
| `ZENITH` | 8（`ZENITH_SITE`，轴上） | II | 吊舱 `oct(10,14)` 楼面 `Y0+40`，六条腿落在广场半径 14；俯视的盒子 A −12…12、R −14…14 | 吊舱控制台 `(A 4, R 0, Y0+42)`，朝 +A | 建：回响穹顶发生器（系泊，§6.2；浮空的形状已经有了，释放任务 0.16.0） |
| `ROOT` | 9（`ROOT_SITE`，轴下） | II | `oct(11,15)` `Y0−1…−26`；`oct(12,16)` 到 −39；球到 −57 | 岛上控制台 `(A 2, R 0, Y0−23)`，朝 +A | 建：潮汐井（§6.3；深根、地脉核的形状只移植成数据） |
| `OUTER_DIAG` | O1、O3、O5、O7 | III | `oct(15,20)` 中心 (±62, ±62) | 中心或正面（按楼） | 0.15.0 |
| `OUTER_FLANK` | O0±…O6± | IV | 27 × 31，前排径向 64，横向中心 ±19（正对井字街） | 套件门 | 预留（逆熵闸门、建筑回响、回响中继） |
| `SKY` | S0–S7 | IV | `oct(10,14)` 平台 `Y0+40` | 朝吊舱 | 0.16.0 |
| `ORBIT` | 轴上 | V | `oct(7,9)` `Y0+52` | `(A 3, R 0, Y0+54)` | 0.17.0 |
| `THIRD` | T00–T15 | V | 41×41，前排径向 105，横向中心 ±25、±71 | 套件门 | 0.18.0 |

`SiteFamily.of(site)`：0 → GATE，奇数 → PAVILION，其余 → HALL；0.12.0 起 8 → ZENITH、9 → ROOT。`sites()`（内城 0–7 里的场地，天顶和根各一个轴上的场地，别处的族为空）、`hasSite`、`opens`。**场地号存档**（记录的场地、任务键 `module:dome_generator@8`、`module:root@9`），永不改。0.12.0 加的：`axis()`（ZENITH、ROOT、ORBIT 站在中枢的轴上：不占内环、坐光梯进、在空中或地下建）、`AXIS_FROM` 8、`axisSite(site)`（≥ 8）、`ringSite(site)`（0–7）、`ringCount(sites...)`（停靠的模块里占内环的个数——中枢窗口、Waila、通量终端都按它对着开放的槽数显示）、`labelKey(site)`（窗口和牌子怎么叫一个场地：内环位 `build.gui.site`「%s 号位」，8 是 `build.gui.site_zenith`「天顶」，9 是 `build.gui.site_root`「根位」，0.14.0 起 0 是 `build.gui.site_gate`「大门」——门楼和停在 0.9.2 内环 0 号槽上的老模块都这么叫）。

**哪些族真的建**：大厅族（0.11.0）、天顶族和根族（0.12.0）、亭子族（0.13.0）、大门族（0.14.0）。`ModuleSpec.footprint` 对 HALL、PAVILION 和 GATE 自己有答案（亭子是八角的外接方 25×25，大门是门楼外形 A 23–39、R −15…15），轴上的族交给它的 `ModuleSpec.Envelope`（§4.12），别的族抛 `UnsupportedOperationException`；`BuildPlan.module` 认大厅位、亭子位（0.13.0）和大门位（0.14.0；`moduleController` 按族分给 `hallController`/`pavilionController`/`gateController`，别的场地 `IllegalArgumentException`），天顶和根各有自己的计划 `BuildPlan.zenith`/`BuildPlan.root`（campus.md §4）。

**`CampusPlan` 的外形常量**（0.11.0）：`DIAGONAL_CENTRE` 36、`DIAGONAL_SIZE` 25、`DIAGONAL_K` 16（0.10.0 预留的是 (±34, ±34) 的 23×23；那里从没建过东西，只有总图的轮廓挪了）；`PAVILION_SITES {1,3,5,7}`、`PAVILION_APRON` 2；对角林荫道 `ALLEE_FROM` 27 到 `ALLEE_TO` 55（`|a|+|r|`，3 宽：`|a|−|r| ∈ {−1,0,1}`，从环道的对角面到亭子的内门）；大门楼 `GATEHOUSE_FROM/TO` 23/39、`GATEHOUSE_HALF` 15、`GATE_PASSAGE` 6；井字街 `STREET_FROM/TO` 18/20、`STREET_REACH` 60；0.12.0 的轴上外形（俯视）`ZENITH_R/K` 10/14（吊舱 `oct(10,14)`）、`ROOT_R/K` 11/15（井 `oct(11,15)`）。方法：`family`、`inEnvelope`（按族分派，0.12.0 起认天顶和根的八角）、`inPavilion`、`pavilionCells`（481 格）、`pavilionApron`（140 格）、`pavilionAllee`（44 格）、`inGatehouse`、`gatehouseCells`（527 格）、`gatehouseApron`、`hallCells`、`hallApron`（含前庭，铺砖前的整块）、`streetCells`（相位 III，还没建）、`grateBand(a, r)`（0.12.0：底座外一圈 `oct(6,8)` 里铺格栅地砖、肋线不穿过的格子，潮汐井的光从这里透上来，`RootLook.grate` 用它）。0.13.0 的亭子（§4.13）：`pavilionFront(site)`（蓝图正面 `{da, dr}`：1、7 是 `{−1, 0}`，3、5 是 `{1, 0}`）、`pavilionSiteAt(a, r)`（亭子中心两轴 ±3 内的对角位或 −1，收编用）、`pavilionSite(site)`（亭子任务占的全部格子：apron `oct(14,18)`——里面是亭子、清理外扩和附属——加林荫道，662 格）、`rowBoxes(cells)`（一组格子按 A 每行、R 上相连的一段一个盒子 `{a0, r0, a1, r1}`；一个亭子的场地是 42 个盒子）、`pavilionFloor(site)`（亭子任务铺的地面，按场地缓存）、`PAVILION_SIGN` = `ALLEE_TO − 3` = 52 和 `pavilionSign(site)`（总图牌子在林荫道中线上的位置 (±26, ±26)）、`PAVILION_DOOR` = (2·36 − 16 − 0.5)/√2 ≈ 39.24（沿对角线从中枢中心到亭子内侧切角门的距离）和 `siteDoor(site, built)`（中枢的光桥在模块没加载时伸到哪里，§4.13；0.13.0 参数叫 `pavilion`）。0.14.0 的大门楼（§4.14）：`GATE_HEADROOM` 7（通道从 Y0 往上 7 格永远敞着，任务在通道上只清这以上的桥）、`GATE_DOOR` = `GATEHOUSE_FROM − 0.5` = 22.5（光桥伸到通道口）、`GATE_SIGN` = `GATEHOUSE_FROM − 2.5` = 20.5（总图牌子在路上、门楼前面）；`inGatePassage(a, r)`（在外形里且 `|R|` ≤ 6）、`gatePassageFloor(a, r)`（门楼任务铺的通道格：`|R|` 4–6、路没铺到的——路是 `|R|` ≤ 3，路口 A 25–29 和门槛 A ≥ 34 加宽到 `|R|` ≤ 5；58 格）、`gatehouseSite()`（门楼任务占的地面：两翼 306 格 + 通道地面 58 + apron 去掉建立园区的大门路 194，共 558 格；桥和路中间的亭在通道上占的只从它们的高度起算，§4.14）、`gatehouseFloor()`（门楼任务铺的 apron，每个种子都是 194 格，缓存在键 `1L << 61` 下），`siteDoor` 的第二个参数对大门位也认（建好了门楼才到 `GATE_DOOR`）。

**内城场地和偏好**（CITY §2.2，`ModuleSpec.sites` 的顺序）：

| 场地 | 偏好（能来的模块，先到先得） | 外形 (A, R) |
|---|---|---|
| 0 大门 | 谐振塔 | A 23–39、R −15…15 |
| 2 右 | **生态穹** · 二向箔仓 · 档案馆 | R 24–58、A −13…13 |
| 4 后 | **档案馆** · 生态穹 · 二向箔仓 | A −24…−58、R −13…13 |
| 6 左 | 二向箔仓 · 生态穹 · 档案馆 | R −24…−58 |
| 1 右前 | 魔力泉阵 · 秘法庭 · 猎场 · 血契坛 | A、R 24–48 |
| 7 左前 | 秘法庭 · 魔力 · 血契坛 · 猎场 | A 24–48、R −48…−24 |
| 3 右后 | 回响猎场 · 血契坛 · 魔力 · 秘法庭 | A −48…−24、R 24–48 |
| 5 左后 | 血契坛 · 猎场 · 秘法庭 · 魔力 | A、R −48…−24 |

模块的偏好列表：library {4, 2, 6}；**eco_dome {2, 6, 4}**；dual_foil_vault {6, 2, 4}；mana_fountain {1, 7, 3, 5}；arcane_court {7, 1, 5, 3}；hunting_ground {3, 5, 1, 7}；blood_covenant {5, 3, 7, 1}；resonance_gate {0}。内城容量：相位 I 2、II 4、III 8（`RingSlots.open(中枢的相位)`，0.12.0 起按 `TileNexus.phase()` 算，不再是常量 `PHASE`）；园区上限 `TileNexus.MAX_CAMPUS_MODULES` 现在还是 7，0.15.0（相位 III，大门也算一个）改成 8。八个内城模块正好是 BLUEPRINT 3.7 的八个内环模块。**轴上的模块不占内环**（0.12.0）：天顶和根的模块任务不算进 `busyModules`、停靠时不占 `openSlots`（`refreshDock` 只给内环位数名额）、NEI 城市预览里不占名额（`PreviewSites`），所以相位 II 是内环 4 座 + 天顶 + 根。它们照样按研究提供，但要中枢长到它们的族开放的相位（`family.opens`，都是 II），否则模块列表显示 `NO_ROOM` 和原因 `build.module.why.phase`「中枢要先长到更高的相位」；高度或深度不够时原因是 `why.height`/`why.depth`（§1 竖向）。

**场地地面**：大厅照旧 `siteFloor(site, 27, 35)`（campus.md §2）。亭子（0.13.0，`pavilionFloor`）：apron `oct(14,18)` 去掉占地 = 140 格（`pavilionApron`）加对角林荫道 44 格（`pavilionAllee`），两者共有 3 格，所以每个对角位 181 格；按优先级：林荫道 `|a|+|r|` ≥ 54 亮门槛（LIT）、50–53 箭头（CHEVRON）、中线（`|a| = |r|`）LIT（林荫道 `|a|+|r|` 从 27 起，中线每隔一格一块，所以是虚线）；apron 贴着亭子的一圈 TRIM（同大厅 apron 的阴影缝），林荫道的两边像路沿一样 TRIM；其余照铺砖的 Voronoi 缝和深色团，剩下 DECK。大门楼（0.14.0，`gatehouseFloor`）：外形外面一圈两格的 apron（A 21–41、R −17…17 去掉外形，208 格，含通道的两头）去掉建立园区铺的大门路（A 21–22、`|R|` ≤ 3 的 14 格）= 194 格；贴着翼楼的一圈 TRIM，其余照铺砖的缝和深色团，剩下 DECK；通道里路没铺到的 58 格（`gatePassageFloor`）是门楼形状自己 y0 的软地面。都只增，不碰 establish 的地面、底座、环道以内、大门和别的场地（`PavilionFloorTest.theFloorsNeverMeet`、`GatehouseSiteTest.theApronsFloorOnlyGrows`）。

**`SitePlanTest`**（`siteplan.py` 断言里现在存在的内城部分）：族和场地；生成器的数字；内城 `problems: 0`——建筑两两不相交（广场 + 环道 + 大门、大门楼、三座大厅、四座亭子），大厅 apron 和前庭不碰亭子和它的 apron、不碰大门楼，亭子 apron 不碰大门楼和它的 apron，林荫道不碰大厅，塔柱在大门楼通道里，每个内城外形在 `oct(64,90)` 里，井字街不碰任何建筑和 apron；间隙：大厅 apron ↔ 亭子 apron 6 格草地、大门楼 apron ↔ 亭子 apron 4 格；外形属于自己的族。0.12.0：天顶和根各有一个轴上的场地（8、9），`axis`/`axisSite`/`ringSite` 的真值、两族在相位 II 开放；轴上的模块不占内环（`theAxisModulesTakeNoRingRoom`：场地 {2,4,8,9} 数出 2、{1,2,4,6,8,9} 数出 4），场地的名字键。腿不碰光井、补给口、回收箱和塔柱的断言在 `DomeGeneratorShapeTest`（§8）。外环、第三环、天上的断言等对应的批次再加。

## 3. 文件

| 文件 | 作用 |
|---|---|
| `city/CityModule` | 注册（`FluxEcho` 三个阶段在 `LibraryModule` 之后调）：preInit `fluxecho:module_core`（物品 `ItemBlockModuleCore`）、TE `fluxecho:eco_dome`，0.12.0 起还有 `fluxecho:dome_generator`、`fluxecho:root`，0.13.0 起还有 `fluxecho:arcane_court`、`fluxecho:blood_covenant`、`fluxecho:hunting_ground`、`fluxecho:mana_fountain`，0.14.0 起还有 `fluxecho:resonance_gate`、`fluxecho:dual_foil_vault`；init 每座模块的 NEI 预览（`infoContainer("<key>.structure")`）和中枢预览（`NexusModule.previewModule(core, meta, TileX::new)`）、`BlockFrame.onUse`/`BlockFitting.onUse`（0.14.0 起还有 `BlockDeck.onUse`：单元可以由画布组成）登记 `UnitUse::use`、`ModuleSpecs.register(ecoDome())`、`domeGenerator()`、`root()`、`arcaneCourt()`、`bloodCovenant()`、`huntingGround()`、`manaFountain()`，0.14.0 的 `resonanceGate()`、`dualFoilVault()`（在书库之后，所以中枢先提供档案馆；轴上的两个带 `ModuleSpec.Envelope`，§4.12；四座亭子都经包内的 `pavilion(key, research, height, sites, meta, colour, plans, controllers)`，§4.4；谐振塔是 `GATE`、二向箔仓是 `HALL`，都用 12 个参数的构造器）；postInit 显化 `eco_dome_core`、`dome_generator_core`、`root_core`，0.13.0 加 `arcane_court_core`、`blood_covenant_core`、`hunting_ground_core`、`mana_fountain_core`，0.14.0 加 `resonance_gate_core`、`dual_foil_vault_core` |
| `city/BlockModuleCore`、`ItemBlockModuleCore` | 模块核心家族：一个方块，meta = 模块（§4.5） |
| `city/TileCityModule` | 城市模块基类：结构定义（0.12.0 起可以有几种形状）、溶解、场地上的朝向、本地坐标、单元和样本、维持电、样本同步、只罩形状盒子的 `covers()`（§4.6–4.7） |
| `city/TileEcoDome` | 生态穹：`COLOR`、`EcoDomeShape` 的形状和单元、核心 meta 0、溶解 `CZ`、能穿过的 `Z`（§6.1） |
| `city/TileDomeGenerator` | 回响穹顶发生器（0.12.0）：`COLOR` `0x7DB8FF`、两种形状 `moored`/`floating`（浮空的只在中枢长到相位 IV 后才试，`tries`）、核心 meta 7、溶解并穿过 `PC`、`under()` 找它下面的中枢、NEI 预览按相位选形状（`previewPhase`）（§6.2） |
| `city/TileRoot` | 根位的潮汐井（0.12.0）：`COLOR` `0x3FC8E8`、一种形状 `well`、核心 meta 8、溶解并穿过 `R`、`over()` 找它上面的中枢（§6.3） |
| `city/TileArcaneCourt`、`TileBloodCovenant`、`TileHuntingGround`、`TileManaFountain` | 0.13.0 的四座亭子：`COLOR` `0xC77DFF`、`0xE0344A`、`0xFF9A3C`、`0x6AA8FF`，核心 meta 2、3、4、5，一种形状 `main`；溶解并穿过 `CZ`（塔尖）、不溶解、`R`（天篷环）、`CZ`（方尖碑）；血契坛覆写 `planLevels()` 给平面图加上两层台（§4.9）（§6.4–6.7） |
| `city/TileResonanceGate`、`TileDualFoilVault` | 0.14.0：谐振塔（`COLOR` `0x5CF2C2`、核心 meta 6、溶解并穿过 `CZ`（尖塔的核心柱），§6.8）和二向箔仓（`COLOR` `0xF2EBDD`、核心 meta 1、不溶解，两个控制台座都开窗口，§6.9），一种形状 `main` |
| `city/UnitUse` | 单元的右键：放、取、开窗口（§4.8） |
| `city/CityGui`、`UnitSlot` | 通用模块窗口（MUI1，三页）；单元格子的槽（热键塞整堆只留一件）（§4.9） |
| `city/client/CityScreen` | 窗口的自绘部分 |
| `city/client/CityRender`、`CityFrame` | 每座城市模块共有的画面（墙裙光线、扫光、名牌、样本座里的物品）；模块本地坐标到相机坐标、线条和光带（§4.10） |
| `city/client/EcoDomeRender` | 生态穹自己的画面（§6.1） |
| `city/client/DomeGeneratorRender`、`RootRender` | 0.12.0：穹顶发生器的腿、发射晶体、电弧、穹顶壳、成型扫光和画到 512 格的地标（§6.2）；潮汐井的潮汐环、上升的肋光、通量层带、池面焦散、从广场透上来的格栅光（§6.3） |
| `city/client/ArcaneCourtRender`、`BloodCovenantRender`、`HuntingGroundRender`、`ManaFountainRender` | 0.13.0：秘法庭的紫晶、墨环、符文环、要素光点、坩埚气泡、下沉的墨字（§6.4）；血契坛的血珠、光弦和滴落、檐口的红光、仪式圈、宝珠座的小珠、心跳脉冲和水滴（§6.5）；猎场的六边形光篷、扫描线、印记台上的剪影（§6.6）；泉阵的魔力泉柱、喷泉射流、魔法阵、火花（§6.7）。`CityFrame.taper`（0.13.0）是秘法庭刷毛笔画用的渐细条带 |
| `city/client/ResonanceGateRender`、`VaultRender` | 0.14.0：谐振塔浮在桥上的水晶尖塔、绕着它的六片碎晶、谐振室升起的光柱、谐振脉冲和画到 512 格的地标（§6.8）；二向箔仓正面画布上的拼贴、天窗的光柱和光斑、正面亮框的呼吸、二向箔的事件视界和成型时洗过正面的羊皮纸白（§6.9）。`CityFrame.vertexUV`（0.14.0）给画布画贴图用 |
| `city/client/CanvasRender`、`Paintings` | 0.14.0：每个由画布组成的单元把它的物品大大地画在画布上（近处整张、远一点只是一块底色，空的画一层模块色的细网格）；`Paintings` 把物品图标画成色调分离的画、放进一池小贴图（缓存，不是计数，§4.10） |
| `logic/SiteFamily` | 场地族（§2） |
| `logic/ShapeKit` | `city/gen/kit.py` 的移植：图例、几何、`Shape`、镜像、行走、内境门位、`summary`（§4.1） |
| `logic/UnitLayout` | 一个形状的单元、槽号、样本座；0.14.0 起可以有挂在墙上、从下面的楼面用的单元（`usedFrom`，§4.2） |
| `logic/CityBuild` | 城市模块的形状交给通用建造计划（`BuildPlan.ModuleBuild`）（§4.3） |
| `logic/EcoDomeShape` | 生态穹的形状、单元、蓝图、阶段规则（`b_hall.py` 的 `eco_dome()`）（§6.1） |
| `logic/DomeGeneratorShape` | 0.12.0：吊舱（`b_sky.py` 的 `dome_pod()`）、六条腿（`dome_legs()`）、两张蓝图、单元、阶段规则、在园区里占的（俯视的盒子、带高度的 keep-out、保护盒、高度检查）（§6.2） |
| `logic/RootShape` | 0.12.0：潮汐井（`b_root.py` 的 `tidal_well()`）、井壳和井里（沉箱用）、单元、阶段规则、保护盒、深度检查；深根 `deepRoot()` 和地脉核 `leyCore()` 只是数据（§6.3） |
| `logic/DomeLook`、`RootLook`、`Tide` | 0.12.0 渲染用的纯规则：腿的肋、电弧、穹顶半径和 LOD、地标的预算（§6.2）；潮汐环的升降和转动、肋光、通量层带、格栅（§6.3）；回响潮汐的曲线（BLUEPRINT 3.11） |
| `logic/ArcaneCourtShape`、`BloodCovenantShape`、`HuntingGroundShape`、`ManaFountainShape` | 0.13.0：四座亭子（`b_pav.py` 的 `arcane_court()`、`blood_covenant()`、`hunting_ground()`、`mana_fountain()`）的形状、单元、蓝图、阶段规则（猎场还有 DECK 里的 `order`）（§6.4–6.7） |
| `logic/ResonanceGateShape`、`DualFoilVaultShape` | 0.14.0：谐振塔（`b_gate.py` 的 `resonance_gate()`，§6.8）和二向箔仓（`b_hall.py` 的 `vault()`，§6.9）的形状、单元、蓝图、阶段规则 |
| `logic/ArcaneLook`、`BloodLook`、`HuntingLook`、`ManaLook` | 0.13.0 四座亭子渲染用的纯规则：位置、动画（只按世界时间、各自有周期）和每档的 quads 数（`quads(tier, docked, on)`）（§6.4–6.7） |
| `logic/CanvasLook`、`VaultLook`、`GateLook` | 0.14.0 渲染用的纯规则：画布的画面（每个画布单元的一块画面 `Field`、画的大小和位置、色调分离的画法 `paint`、每档的 quads）；二向箔仓的拼贴、天窗光柱、亮框和二向箔的边；谐振塔的尖塔、碎晶、脉冲、光柱和地标（§4.10、§6.8、§6.9） |
| `logic/XRay` | 0.12.0：地下计划的透视棱（`edges`）、悬在空中的计划的影子和铅垂线（`hangs`/`shadow`/`plumbs`）（§4.12、campus.md §11） |
| `logic/LiftShaft`、`LiftInput`、`LiftLook`，`campus/Lift`、`campus/client/LiftClient`、`LiftRender`、`LiftScan` | 0.12.0 光梯：规则、按键变成乘梯请求、光束的位置和乘梯的视角；两端的乘梯和碰撞钩子；客户端的输入和手感、光束、找光梯井（nexus.md §4.2） |
| `logic/ModuleCores` | `fluxecho:module_core` 的 meta 表（§4.5） |
| `logic/ModuleLook` | 渲染用的纯规则：墙裙光线的面和顺序、名牌位置（0.13.0 起中心对称的楼浮在中心控制台上方，`central`/`overCentre`）、档位、样本座凹槽、池沿、涟漪的余量、认蜜蜂；0.13.0 的共用画面预算 `baseQuads`、`sweepQuads`、`PLATE_QUADS`（§4.10） |
| `logic/ModuleMap`、`UnitBrowse` | 窗口的平面图（`ArchiveMap` 的通用版；0.13.0 起可以在单元的楼层之后加只看建筑的层 `overview`，控制台座或控制器站在平面图正中的空格上方时画出来）；单元列表的翻页、循环、选择检查（§4.9） |
| `logic/PreviewSites` | 中枢 NEI 城市预览给每种模块挑位置：有 spec 的只上自己的场地、都占了就不立；没 spec 的用内环槽，和场地分开记（§4.11） |
| `logic/BuildPlan` | 0.11.0 加的 `ModuleBuild` 接口、`module`、`hallController`、`ornaments`/`canopyDepth`/`annex`、`ARCHIVE`、`MODULE_MARGIN`/`MODULE_HEADROOM`；0.13.0 加的 `pavilionController`、`moduleController`、`PAVILION_ANNEX_FROM`、`ModuleBuild.order`（§4.3、§4.13）；0.14.0 加的 `gateController`、`gateKeepOut`、`gateProtect` 和大门位的清理（§4.14） |
| `logic/CampusPlan`、`Parts`、`PartRecipes`、`TerrainRule.forRepairGround` | 场地族的外形（§2；0.13.0 起亭子的地面、正面、收编、场地盒子、总图牌子和光桥的门；0.14.0 起大门楼的通道、地面、场地、`GATE_DOOR`、`GATE_SIGN`）；新部件码（§5；0.14.0 的画布 28）；新配方；修复只往空格里铺地面（§6.1） |
| 改过的老类 | `nexus/TileMultiblock.coreMeta`、`BlockNexus.quiet`、`NexusModule.previewModule(core, meta, tile)`、`TileNexus.previewModules`、`NexusNet.UNIT_DELTA`、`NexusWaila`、`NexusGui.stageKey`、`nexus/client/ModuleRender`、`frame/BlockFrame`（ANCHOR）、`frame/client/FrameClient`（离开世界时 `TileCityModule.clearClient`）、`campus/BlockFitting`（SOCKET、BASIN）、`campus/client/FittingRender`、`PartBlocks`、`ModuleSpec`（`family`）、`Campus`（`RING_FULL`、`offerStatus`）、`Builder`（`Parts.euOnly`、修复的地面）、`Terrain.isTarget`（水）、`campus/client/BuildFx`、`BuildScan`（图标缓存按 128 个码）、`campus/client/Masterplan`（亭子外形）、`Config.moduleUpkeep`。0.12.0：`TileMultiblock`（`reach()`、`previewShape`）、`TileModule.previewPhase`、`TileNexus`（五个相位形状、`phase()`/`grown()`、`ringDocked`、按轴上场地停靠）、`NexusRender`（每相位一节、模块的地标）、`ModuleRender.landmark`、`NexusGui`/`NexusScreen`/`NexusWaila`/`TerminalView`（内环只数 `ringCount`、场地名、`g`/`z`/`r` 阶段键、没地方的原因）、`fluxlite/gui/TerminalScreen`（内环计数、天顶/根位）、`ModuleSpec`（`Envelope`、`keepOut`、`protect`、`noRoom`、`axis`）、`Campus`（生长、轴上的模块、`siteKeepOut`/`siteProtect`）、`BuildJob`（`grow`、`establish:<p>`、`Kr`）、`Builder`（沉箱的挖和排水、空中建造照常）、`Terrain`、`TerrainRule`（`forDig`/`forDrain`）、`BuildPlan`（`grow`、`zenith`、`root`、DIG/DRAIN、分带、带高度的 keep-out）、`CityBuild`（偏移）、`Blueprint`（`reach`、`inBox`）、`ModuleLook`（`plates`、`liftPad`）、`ModuleMap`（光梯台、齐平的单元层）、`PreviewSites`（轴上的不占名额）、`GhostCells`（挖的格子不画）、`campus/client/BuildRender`/`BuildScan`（透视棱、空中的影子）、`Masterplan`（天顶和根位）、`city/client/CityRender`/`CityFrame`/`CityScreen`、`CityGui`（相位）、`BlockFrame`/`BlockFitting`（光梯的碰撞）、`MatterModule`、`Manifests`、`ResearchTree`。0.13.0：`ShapeKit`（亭子的辅助函数，§4.1）、`BuildPlan`（亭子的计划、`order`）、`CityBuild`（`Stages.order`）、`CampusPlan`（亭子的地面和场地）、`ModuleSpec`（亭子的 keep-out 和保护盒、地面模块的 `noRoom`）、`Campus`（`ModuleState.pavilionBuilt`、`View.pavilionBuilt`）、`TileNexus`（`pavilionSite` 收编）、`NexusGui`/`NexusScreen`（模块列表翻页）、`NexusRender`（光桥到亭子的门）、`Masterplan`（对角位写计划的模块、牌子在林荫道上）、`ModuleCores`（`BUILT`）、`BlockModuleCore`、`TileCityModule`（`planLevels`）、`CityGui`（平面图带 `planLevels`）、`CityScreen`（只看建筑的层的标签）、`CityRender`（四个分支）、`CityFrame`（`taper`）、`ModuleLook`、`ModuleMap`、`ResearchTree`/`Research`（四个节点，三个老节点挪了位置）。0.14.0：`ShapeKit`（画布 `Q` 和它的软孪生 `$`）、`UnitLayout`（`usedFrom`、`base`）、`ModuleMap`（挂在墙上的单元画整个占地）、`BuildPlan`（大门的控制器、清理、地面、keep-out 和保护）、`CampusPlan`（大门楼）、`SiteFamily.labelKey`（0 是「大门」）、`Parts`/`PartRecipes`（画布）、`campus/BlockDeck`（画布 meta 12、`Use`/`onUse`/`onBlockActivated`）、`ModuleSpec`（大门族的占地、keep-out、保护）、`Campus`（`ModuleState.gateBuilt`、`View.gateBuilt`、`boxedSite`/`onBoxedSite`、解锁消息用场地名）、`TileNexus`（大门按控制器格精确收编）、`NexusRender`（光桥到门楼的通道口）、`Masterplan`（门楼的两翼、0 号位只认门楼、牌子挪到门楼前的路上）、`fluxlite/gui/TerminalScreen`（门楼写「大门」）、`ModuleCores`（`BUILT`）、`BlockModuleCore`、`TileCityModule`（`takeSlot`）、`UnitUse`（潜行取回）、`CityRender`（画布一部分、两个分支、谐振塔的地标）、`CityFrame`（`vertexUV`）、`ModuleLook`（`outside`）、`frame/client/FrameClient`（离开世界和重新拼贴图集时清 `Paintings`）、`ResearchTree`/`Research`（两个节点，猎场挪了位置） |
| `tools/CampusTextures.java`、`tools/NexusTextures.java` | 样本座和池沿；覆层锚点和模块核心的贴图（§5）；0.12.0 的光梯台 `fitting/lift`、`lift_top`、`lift_glow`，穹顶发生器和潮汐井的核心 `module_core/dome_generator/*`、`module_core/root/*`，物品 `items/phase_alloy`、`items/resonant_glass`；0.13.0 四座亭子的核心 `module_core/arcane_court/*`（`spireEmblem` 尖塔）、`blood_covenant/*`（`altarEmblem` 祭坛）、`hunting_ground/*`（`arenaEmblem` 竞技场）、`mana_fountain/*`（`fountainEmblem` 喷泉），各按主题色（`ARCANE_VIOLET`、`BLOOD_RED`、`HUNT_AMBER`、`MANA_BLUE`）；0.14.0 的画布 `deck/canvas`（`CampusTextures.canvas`：深蓝织纹、两像素的经纬、每半格一条淡青网格线，不发光）和两个核心 `module_core/resonance_gate/*`（`gateEmblem` 两座翼楼、桥和浮着的水晶，`GATE_TEAL`）、`dual_foil_vault/*`（`vaultEmblem` 锯齿顶下的方碑和正面的画布，`VAULT_PARCHMENT`） |

## 4. 共用框架

### 4.1 `ShapeKit`（`kit.py` 和 `b_hall.py` 用到的辅助函数的移植）

- **图例** `partOf(ch)`（CITY 附录 A，全城唯一）：构架 `F` 地基、`B` 基座、`g` 导能基座、`P` 支柱、`C` 导能柱、`R` 环段、`Z` 晶核座、`K` 控制台座、`A` 覆层锚点；地板 `d` 地砖（检查）、`#` 地缝钢、`e` 光带地砖、`:` 格栅、`%` 深色、`k` 踢脚光带、`*` 光井、`W` 墙板、`L` 光缝墙板、`w` 深色墙裙、`n` 光檐、`Q` 画布（0.14.0，deck 12 / 码 28）；配件 `q` 立柱、`p` 底座、`c` 顶檐、`r` 栏杆、`t` 踏板、`G` 窗格、`h` 全息底座、`o` 样本座、`u` 池沿；软格 `.` 地砖、`+` 光带、`_` 地缝钢、`,` 草（`Parts.GRASS`）、`;` 泥土、`=` 水（`Parts.WATER`）、`{` 墙板、`}` 踏板、`|` 栏杆、内境门位的 `[` 深色墙裙、`!` 光缝墙板、`/` 窗格、`$` 画布（0.14.0）；`-` → `Parts.AIR`；`~` 控制器和空格 → −1（控制器的部件是模块核心）；`^` 光梯台（0.12.0，`Parts.fitting(F_LIFT)` = 码 41，形状里用 `Shape.lift(x, y, z)` 放，记进 `lifts`，行走测试按它连上下两个梯台）。附录 A 里的 `f` 浮空地基是以后批次（0.16.0）的部件，`partOf` 现在不认（抛 `IllegalArgumentException`），哪个批次加部件就在这里加一行（`Q` 画布是 0.14.0 加的，`Q` 和 `$` 都是画布）。
- 字符集：`SOFT` = `.,=;+_{}|[!/$`、`soft`、`checked`（不是空格、`-` 和软格）、`checkedTwin`（软格放的是哪个检查字符的部件：`.`→`d`、`+`→`e`、`_`→`#`、`{`→`W`、`}`→`t`、`|`→`r`、`[`→`w`、`!`→`L`、`/`→`G`、0.14.0 的 `$`→`Q`；草、泥土、水是自己；画面和平面图按它认墙）；行走用的 `PASSABLE`（空格、`-`、水）、`HALF`（`t}` 站在 +0.5）、`NOSTAND`（`r|` 栏杆 1.5 高）、`LOW`（`uh` 池沿和全息底座挡路、不能站）。
- 几何：`octo(du, dv, half, k)`、`hypot`（整数平方和的 `Math.sqrt`，同 Python 的 `math.hypot`）、`angle`（`StrictMath.atan2`）、`offOctant`、`disc`、`ring`、`key/keyX/keyZ`、`boundary`、`thickBoundary`、`inBall`、`ballShell`、`spiral(cx, cz, yFrom, steps, start, mirror)`（档案馆的井：每个环格一级、半格一级、整格是墙板）、`portal(s, z, yLintel, canopy, cx)`（套件门）。
- `Shape`：W×H×D 的字符盒（`set/get/clear`、`counts()`）、`unit(name, kind, cells, slots, face)`、`controller`、`decor`（`{x, y, z, 字符}`）、光梯、`asym`（不参加镜像检查的格子，比如生态穹树干的螺旋）、`gateNiche(x0, x1, y0, y1, z)` 和 `niche`（内境门位：把墙格换成软的孪生字符——`NICHE_WALL` `wLGWQ` 对 `NICHE_TWIN` `[!/{$`——不是这几种墙就抛 `IllegalArgumentException`；§1）、`symmetric()`、`walk`（状态 `state(x, z, h)`，h 以半格计）、`unitReach`、`controllerReach`；`summary(shape, starts)` → `Summary`（计数、对称、走到的状态数、控制器和每个单元能不能到）。
- **亭子的辅助函数**（0.13.0，`b_pav.py` 的移植，`PavilionKitTest` 按生成器的校验和钉住）：`PAV_SIZE` 25、`PAV_C` 12（中心）、`PAV_HALF`/`PAV_K` 12/16（平面 `oct(12,16)`）；`octK(h)`（生成器的 `round(4h/3)`：12 → 16、11 → 15、10 → 13）；`onorm(du, dv)`（八角范数：`max(|du|, |dv|, ceil(3(|du|+|dv|)/4))`，亭子里一圈圈的地面和家具都按它画）；`planCells(half, k)`/`planCells()`（平面的柱子，481 根）；`thickEdge`（同 `thickBoundary`，96 根，墙）；`portalCells(edge)`（四个切角门：切角边上离对角线 2 格内、离中心至少 5 的格子，再往里一格，所以门两格深、沿对角线能走，40 根）；`Shell` 和 `shell(s, wallTop, rhythm, glazeRows[, lintel, plinth])`（y0 的地基环 `F` 和里面的软地面 `.`；墙 y1 到 `wallTop`：y1 深色墙裙、四个平面上每 `rhythm` 格一根亮壁柱 `L`、切角门两边的亮门框、平面的 `glazeRows` 层是窗格、其余墙板；门洞 y1–4 必须是空气，y5（`lintel`）光檐门楣）；`range(from, to)`（Python 的 `range`）；`centreController(s, y)`（中心 (12, y, 12) 的控制器，下面 y−1 是控制台座）；`backNiche`/`pavilionNiche`（后墙门位，§1）；`pavilionStarts()`（行走测试的起点：四个门外，正门外再多一个）；`blueprint(shape, height)`（软格变成空格的检查蓝图）、`layers(shape)`（给校验和用的每一层）。生成器的 `spiral` 就是 `ShapeKit.spiral`。
- 和生成器逐格一致：上面的 `hypot`/`atan2` 换法给出和 Python 一样的格子；每个形状测试比对整层的 CRC 校验和（`EcoDomeShapeTest`）。

### 4.2 `UnitLayout`

- 从 `Shape.units()` 按生成器声明的顺序建：`Unit{index, name, kind, cells, sockets, slots, firstSlot, face, floor}`；槽号一个单元接一个单元连续排（单元 i 的槽紧跟单元 i−1 的）；`sockets` 是单元格里的样本座 `o`（按生成器列格子的顺序），槽 `firstSlot + k` 显示在第 k 个样本座里；样本座少于槽数时多出来的槽不在哪个格子上显示（画布的一个槽画满整面），多于槽数、一格属于两个单元 → 构造时抛 `IllegalStateException`。`floor` = 单元最低一格的 y。
- **挂在墙上的单元**（0.14.0）：第二个构造器 `UnitLayout(shape, Map<String, Integer> usedFrom)` 给某些单元指定「从哪一层楼面用」——二向箔挂在后墙上、最低一格在 y3（门位从它下半中间拿走了 12 格，原来 y3 的那排断成两段），人站在长厅地面上用它，所以 `usedFrom` 是 `{"foil": 1}`（`DualFoilVaultShape.FOIL_FLOOR`）。`Unit.base` 是最低一格的 y，`floor` 是指定的楼面（没指定就是 `base`）；`storeys()` 按 `floor` 数，所以二向箔在一楼的平面图上、不自己占一层。指定的楼面高过 `base`、或名字不是这个形状的单元 → `IllegalArgumentException`（`UnitLayoutTest.aUnitHungOnAWallStandsOnTheFloorItIsUsedFrom`）。老的一个参数的构造器 = 空表，别的模块逐格不变。
- 查询：`unitOfCell`、`slotOfSocket`、`unitOfSlot`、`socketOf`、`slots()`、`size()`、`storeys()`（单元站的不同楼层，从低到高——窗口平面图的层）、`storey(unit)`。
- **顺序就是槽号**（`TileCityModule` 存档 `Samples` 按槽号）：发布后不改顺序、不在中间插单元、不改单元的槽数；真要加只能加在末尾（会改形状，要提 `BuildJob.V`）。

### 4.3 通用建造计划（`BuildPlan.ModuleBuild`、`BuildPlan.module`、`CityBuild`）

- **`ModuleBuild`**（一座模块的形状怎么交给建造计划）：`key`、`width/depth/height`、`blueprint`、`cellOf`（本地格 → 蓝图格）、`controller`、`cell(x,y,z)`（检查字符、软字符、`-`、`~` 或空格）、`part`、`stage`（不属于单元的格子在哪个阶段）、`fitRank`（FIT 阶段里的次序：负的在单元之前、正的在之后，不能是 0）、`order`（0.13.0，默认方法返回 0：自下而上的那几段里先按它排，见下面）、`units`（每个单元的格子，一个单元一组）、`decor`、`ornaments`。档案馆（`BuildPlan.ARCHIVE`）和城市模块（`CityBuild`）都这样描述自己，一个建造器铺所有大厅、亭子和门楼。
- **`BuildPlan.module(plan, site, cx, y0, cz, fx, fz, m)`**（9 段，`M_CLEAR … M_COMMISSION`；场地是大厅位、0.13.0 起的亭子位或 0.14.0 起的大门位）：
  - CLEAR：大厅是整个盒子外扩 `MODULE_MARGIN`(1) 的柱子，亭子是占地（形状最底层有东西的柱子，八角 `oct(12,16)`）外扩 1 格（含斜角，585 根，在 apron 里面）；从 Y0+1 到形状里最高的非空层 + 1 + `MODULE_HEADROOM`(2)，由中枢向外，`grade`（同档案馆：没铺砖的 Y0 格换成草、下面填坑，campus.md §5）；大门楼（0.14.0）是盒子外扩 1，但通道（`|R|` ≤ `GATE_PASSAGE` 6）上只清桥站的那些柱子、从 Y0 + `GATE_HEADROOM`(7) 起、不平整（`overPassage`，§4.14）；
  - FLOOR：场地地面（大厅 `siteFloor`，亭子 `pavilionFloor`，大门楼 `gatehouseFloor`，§2）+ 模块放在 FLOOR 阶段的格子，由中枢向外（`RIPPLE`）；
  - SHELL、DECK、UPPER、ROOF：模块的 `stage` 分到这几段的格子，每段先按 `order` 从小到大、同一个 `order` 里自下而上（0.13.0；所有格子都是 0 时比较和以前一模一样，所以已有的计划逐格不变——`ModulePlanTest` 的校验和照过；现在只有猎场给了别的值，§6.6）；
  - FIT：rank < 0 的格子 → 每个单元一组（组号 = 单元序号，连续、组内自下而上，一起付钱一起发射）→ rank > 0 的格子，每个 rank 内自下而上；
  - DECOR：形状的 decor，`ornaments()` 为真时再加种子装饰（亭子没有雨棚，只有附属，从第 `PAVILION_ANNEX_FROM` 8 排到 16 排，在 apron 外排、平的侧面旁边；谐振塔也没有雨棚，附属照大厅从第 `ANNEX_FROM` 6 排到 10 排，在一侧翼楼外两格 R ±17）；
  - COMMISSION：控制器（模块核心），最后一步。
  软字符 → SOFT、`-` → AIR、其余 → HARD；每格用 `Blueprint.world` 按 `moduleController(m, …)` 摆（大厅 `hallController`，亭子 `pavilionController`，§4.13，大门 `gateController`，§4.14），所以 HARD 格正好是结构检查格。抛 `IllegalStateException`：格子没有阶段、FIT 的 rank 是 0、单元格不是部件、单元格数和形状里的单元格数不等。
- **`hallController(site, cx, y0, cz, fx, fz, ctrlY, ctrlZ)`** 和 `hallController(m, …)`：前排径向 24、控制器在场地轴上往里 `ctrlZ` 行、Y0+`ctrlY`，朝中枢（场地轴取反）；`archiveController` 现在是它的特例。
- **档案馆也走这里**：`BuildPlan.ARCHIVE` 照 0.10 的阶段规则原样（立柱在单元前，全息底座、讲台、阅读台在后；`ornaments()` 假），`archive(...)` = `module(..., ARCHIVE)`；`ModulePlanTest.theArchivesPlanIsCellForCellTheSame` 钉住 4 个朝向 × 3 个场地的 12 个校验和，所以 **`BuildJob.V` 还是 2**。
- **种子装饰**（`ornaments`，种子 `Mix.seed(控制器 x, y, z)`）：`canopyDepth(seed)` 2/3/4（形状自己的雨棚两排，最外一排往外重复）；`annexKind(seed)` 和 `annex(width, depth, seed)`：一侧（`Mix.unit(seed, 43) < 0.5` 时左边 x = −2，否则 x = width+1，即 apron 外排，`ANNEX_OFFSET` 2）、Y0+1、z 从 `ANNEX_FROM` 6 到 depth−7：种植墙（每 4 格一个池沿，其余草）、灯排（每 3 格两节立柱加一个顶檐）、长椅（踏板，每 5 格空一格）、全息牌柱（中间一格：全息底座加两节立柱）。全是 SOFT、在蓝图外。同一座中枢的不同场地种子不同。
- **`CityBuild(key, shape, height, blueprint, layout, corePart, stages)`**：城市模块的 `ModuleBuild`。`cell` 读形状（≥ `height` 的层是空格：生成器可以多分配层）；`part` 按图例、控制器是 `corePart`；`stage`/`fitRank`/`order` 交给模块自己的 `Stages`（`Stages.order` 是 0.13.0 的默认方法，返回 0）；`units()` 来自 `UnitLayout`；`decor()` 是形状的 decor（部件码）；`ornaments()` 恒真；`cellOf(x,y,z) = {x + ox, 蓝图高−1−(y + oy), z + oz}`。
- **蓝图伸出形状的盒子**（0.12.0，第八个参数 `offset`）：穹顶发生器的腿从吊舱往下伸 39 格到广场，蓝图（腿和吊舱合起来 29×49×25）比形状（吊舱 21×10×21）大。`offset = {横向, 从蓝图最低层往上, 从蓝图前排往后}` 是形状的格 (0, 0, 0) 在蓝图里的位置（吊舱是 `{4, 39, 2}`）；形状的坐标系照旧是模块自己的（y0 是吊舱舱底，每个渲染器和窗口都这么读），蓝图装着它周围的东西。`localOf(a, b, c)`（蓝图格 → 形状点）、`offset()`；`TileCityModule.local/localPoint`、`CityFrame` 都按它换算。不给 `offset` 就是 `{0, 0, 0}`，和 0.11.0 一样（生态穹逐格不变）。
- **阶段的 lang 键**：大厅族、亭子族和大门族的模块任务都是 9 段；档案馆用 `fluxecho.build.stage.m<n>`，其他大厅族、亭子族和大门族的城市模块用 `build.stage.c<n>`：`c0` 清理场地、`c1` 场地地面、`c2` 外墙、`c3` 楼面、`c4` 上层外墙、`c5` 屋顶、`c6` 配件和单元、`c7` 装饰、`c8` 落成。0.12.0 起天顶族用 `z<n>`（7 段：`z0` 清理支腿和吊舱的位置、`z1` 六条支腿、`z2` 吊舱舱底、`z3` 外壳、`z4` 屋顶、`z5` 控制台和守护台、`z6` 落成），根族用 `r<n>`（5 段：`r0` 沉井、`r1` 排水、`r2` 井底、`r3` 潮汐环、坡道和汲口、`r4` 落成），中枢的生长任务用 `g<n>`（`g0` 清理中枢上方、`g1` 新的一段、`g2` 成型；生长用 establish 的阶段号，地面和塔柱两段是空的）。`NexusGui.stageKey` 按计划键选：establish（含 `establish:<p>`）和 forum → `e`，`grow:<p>` → `g`，spec 是天顶族 → `z`、根族 → `r`，档案馆 → `m`，其余 → `c`。阶段号和键一一对应。

### 4.4 `ModuleSpec` 和营造引擎

- `ModuleSpec(family, key, research, width, depth, height, sites, corePart, colour, plans, controllers, enabled)`：每个场地都要属于这个族（否则 `IllegalArgumentException`）；老的 11 参数构造器 = `HALL`。`footprint` 按族（大厅：横 × 深从前排；亭子：八角外接方）。
- 生态穹的：`CityModule.ecoDome()` = `HALL`、键 `eco_dome`、研究 `eco_dome`、27×35×21、场地 {2, 6, 4}、`Parts.moduleCore(0)` = 64、`0x7BE08A`、`EcoDomeShape::plan`、`EcoDomeShape::controller`、`() -> true`（没有单独的开关，`campusEnabled` 关掉的是整个营造）。
- 之后的一切照营造引擎（campus.md §5–§10）：`autoJobs` 按研究和偏好提供（容量 `min(RingSlots.open(中枢的相位), 7)`，停靠的 0.9.2 大厅也占一个）；开始前账本里要有核心（`need_core`）；keep-out 用 `siteBox`；停靠时按场地收编（`TileNexus.refreshDock` 要有 spec）；修复、取消冷却、模块列表、总图牌子、Waila、终端都是通用的。研究了但只因为内环满了不能提供 → `RING_FULL`（§4.11）。
- **轴上的族**（0.12.0）：`ModuleSpec` 的第 13 个参数 `Envelope`（族是 `axis()` 的必须给，否则 `IllegalArgumentException`）回答大厅用 `footprint`/`siteBox` 回答的问题：`footprint`（俯视的盒子）、`keepOut`（园区任务让开的盒子，可以带高度）、`protect`（除了它自己的任务谁都不拆的空间盒子 `{x0, y0, z0, x1, y1, z1}`）、`noRoom(y0)`（放不下的原因键或 null）。`ModuleSpec.axis()`、`noRoom`（0.13.0 起大厅和亭子按形状高度查，§1 竖向）、`keepOut`（大厅是整列的 `siteBox`）、`protect`（大厅是从基岩到天空的 `siteBox`）。`Campus` 里轴上的 spec 不占内环（`busy`/`cap` 跳过它）、要 `phaseFits`（`nexus.phase() ≥ family.opens`）和 `roomFor`（`noRoom(Y0)` 为 null）才提供；空场地的判定只看控制器格上有没有已加载的模块（keep-out 和模块中心是给地面上的大厅用的）。停靠：`TileNexus.campusSite` 对轴上的 spec 要控制器格和正面**正好**是场地给的（不是大厅那样 ±3）。细节见 §4.12、campus.md §5。
- 穹顶发生器：`CityModule.domeGenerator()` = `ZENITH`、键 `dome_generator`、研究 `dome`、21×21×10（吊舱）、场地 {8}、`Parts.moduleCore(7)` = 71、`0x7DB8FF`、`DomeGeneratorShape::plan`、`::controller`、`() -> true`、`Envelope` 交给 `DomeGeneratorShape.footprint/keepOut/protect/noRoom`。潮汐井：`CityModule.root()` = `ROOT`、键 `root`、研究 `tidal_well`、25×25×26、场地 {9}、`Parts.moduleCore(8)` = 72、`0x3FC8E8`、`RootShape::plan`、`::controller`、`() -> true`、`Envelope` 交给 `RootShape`（`keepOut` 是空的）。
- **亭子**（0.13.0）：`CityModule.pavilion(key, research, height, sites, meta, colour, plans, controllers)` = `new ModuleSpec(PAVILION, key, research, 25, 25, height, sites, Parts.moduleCore(meta), colour, plans, controllers, () -> true)`，没有 `Envelope`（12 个参数的构造器）。模块键就是研究 id：秘法庭 `arcaneCourt()` 33 高、场地 {7, 1, 5, 3}、meta 2（码 66）、`0xC77DFF`；血契坛 `bloodCovenant()` 17 高、{5, 3, 7, 1}、meta 3（67）、`0xE0344A`；回响猎场 `huntingGround()` 10 高、{3, 5, 1, 7}、meta 4（68）、`0xFF9A3C`；魔力泉阵 `manaFountain()` 8 高、{1, 7, 3, 5}、meta 5（69）、`0x6AA8FF`；计划和控制器是各自 `XShape::plan`、`XShape::controller`（`plan` 对不是亭子位的场地抛 `IllegalArgumentException`）。`keepOut` 是亭子的场地按行切的整列盒子，`protect` 是同样的盒子从 0 到 255，`noRoom` 按形状高度查（§1 竖向；只有秘法庭会碰到），细节 §4.13。
- **0.14.0**：谐振塔 `CityModule.resonanceGate()` = `new ModuleSpec(GATE, "resonance_gate", "resonance_gate", 31, 17, 18, {0}, Parts.moduleCore(6)（码 70）, 0x5CF2C2, ResonanceGateShape::plan, ::controller, () -> true)`，没有 `Envelope`；`footprint` 是门楼外形 A 23–39、R ±15（外扩 `margin`），`keepOut` 是 `BuildPlan.gateKeepOut(它自己的计划)`、`protect` 是 `gateProtect`（§4.14），`noRoom` 照地面模块查高度（18 高）。二向箔仓 `CityModule.dualFoilVault()` = `HALL`、键和研究 `dual_foil_vault`、27×35×27、场地 {6, 2, 4}、`Parts.moduleCore(1)`（码 65）、`0xF2EBDD`、`DualFoilVaultShape::plan`、`::controller`（`hallController`）、`() -> true`——和生态穹一样的大厅，`keepOut`/`protect` 是整列的 `siteBox`。登记顺序（`ModuleSpecs`，在书库之后；`autoJobs` 按它提供、中枢 NEI 预览的种类也按它排）：生态穹、穹顶发生器、潮汐井、四座亭子、谐振塔、二向箔仓。

### 4.5 模块核心家族 `fluxecho:module_core`

- **一个方块，meta = 模块**（`logic/ModuleCores`，CITY 附录 C）：0 `eco_dome` · 1 `dual_foil_vault` · 2 `arcane_court` · 3 `blood_covenant` · 4 `hunting_ground` · 5 `mana_fountain` · 6 `resonance_gate` · 7 `dome_generator` · 8 `root` · 9 `sophon_array` · 10 `holo_table` · 11 `sky_sails` · 12 `observatory` · 13 `droplet_hangar` · 14 `weather_station` · 15 `orbital_anchor`；有模块的 meta 是 `ModuleCores.built()` = {0, 1, 2, 3, 4, 5, 6, 7, 8}（0.11.0 只有 0；0.12.0 的 `BUILT` 从个数改成 meta 列表，`built(meta)` 问一个 meta；0.13.0 加 2–5 四座亭子；0.14.0 加 1 二向箔仓、6 谐振塔）；第十七个模块（源点之心）起用 `fluxecho:module_core2`（码 80–95，只预留）。`meta(key)`、`key(meta)`、`KEYS`。meta 一旦给了某个模块就永远是它的（是世界里的方块，也是账本里的部件）。
- 部件码 `Parts.MODULE_CORE + meta`（64–79；`moduleCore(m)`、`isModuleCore`、`MODULE_CORES` 16）；`PartBlocks.block` → `CityModule.core`，`code(block, meta)` 按 meta 反查；`PartRecipes.partOnly` 包含全部 16 个——核心显化得来，建造只收成品，模块任务开始前账本里要有它（同书库核心）。
- **`BlockModuleCore extends BlockNexus`**（`super("module_core", "module_core")`，铁、亮度 0.5）：`createNewTileEntity` 按 meta（0 → `TileEcoDome`，1 → `TileDualFoilVault`，2 → `TileArcaneCourt`，3 → `TileBloodCovenant`，4 → `TileHuntingGround`，5 → `TileManaFountain`，6 → `TileResonanceGate`，7 → `TileDomeGenerator`，8 → `TileRoot`，其余 null）；`damageDropped` = meta；创造栏只列 `built()` 的 meta；图标只给 `built()` 的 meta 注册；`quiet()` = `Builder.QUIET`（`BlockNexus.breakBlock` 新加的钩子：引擎改世界时拆核心不通知附近的多方块，阶段结束统一通知；书库和中枢的核心照旧每次都通知）；`dropsMore` → `TileCityModule.takeDown`；图标 `fluxecho:module_core/<key>/front|front_on|side|top`（`front_on` 是成型时的正面；没模块的 meta 用生态穹的）。
- **`ItemBlockModuleCore`**：名字 `tile.fluxecho.module_core.<key>.name`；提示 `fluxecho.<key>.tip`，按住 Shift 是 `fluxecho.<key>.structure`（否则 `gui.shift_build`）；放下用物品的 meta。
- 框架里配套的两处：`TileMultiblock.coreMeta()`（默认 0）——NEI 预览世界里 `placeOwnBlock` 按它放核心；`NexusModule.previewModule(core, meta, tile)`——中枢城市预览放核心时带 meta。

### 4.6 `TileCityModule`

- **子类给**：`build()`（`CityBuild`）、`layout()`（`UnitLayout`）、`coreMetaOf()`、`dissolves()`、可选 `passes()`（默认空）、可选 `planLevels()`（0.13.0，默认空数组：窗口平面图在单元的楼层之后加的只看建筑的层，血契坛给 {8, 13}，§4.9），加 `TileModule` 的 `moduleKey()`、`moduleColor()`。形状和单元是静态数据，构造器里就能问。
- **结构**：默认一种形状 `MAIN`；0.12.0 起子类可以覆写 `shapes()` 给几种（穹顶发生器的 `moored` 和 `floating`，同一个盒子，控制器格相同），`tries(s)` 决定现在试哪几种。`definition()`/`buildDefinition()` 按模块键缓存，带上所有形状，每个字符 `ShapeKit.partOf` → `PartBlocks.block/meta`（`-` 是 StructureLib 的必须为空气，控制器跳过；某个字符没有方块就直接抛）——正是建造放的方块，所以引擎建完就成型。`centreCell` = 地基中心 `cellOf((W−1)/2, 0, (D−1)/2)`（停靠按它找场地）；`coreBlock` = `CityModule.core`、`coreMeta` = `coreMetaOf()`；`descriptionKey` = `<key>.structure`。
- **`covers()`**（0.12.0）：不再是 ±`reach()` 的立方体，只罩它每个形状绕控制器摆好的盒子（`Blueprint.inBox`）——城市模块的盒子就是它真实的范围；天顶 49 高的盒子做成立方体会以吊舱里的控制器为心往四周伸 50 格、罩住每个大厅位，那里残留的我们的方块就不算散落了（campus.md §5）。帧变化的监听和 `UnitUse` 也问它，每个形状的每一格都在它的盒子里，所以都不会漏。
- **控制台座**：`consoleAt` = 形状里 `K` 的格子、**而且不属于任何单元**（0.12.0：穹顶发生器的守护台是控制台座 + 样本座组成的单元，点它是用单元，不是开窗口；0.13.0 秘法庭的 8 张研究台也是，只有中心祭坛的控制台座开窗口）。不属于单元的控制台座可以有几个：二向箔仓（0.14.0）门间柱里控制器下面的和后墙二向箔下面桌子上的都开窗口（`TileDualFoilVaultTest.theControllerAndBothConsoles`）。
- **预览的形状**（0.12.0）：NEI 中枢预览立模块之前调 `previewPhase(相位)`（`TileModule` 的钩子，默认什么都不做）；穹顶发生器在相位 IV 起选 `floating`（`TileMultiblock.previewShape`，只在预览世界里有用）。
- **溶解**：`flags(ch)`：`dissolves()` 里的字符成型后 `Formed.HIDE`（区块网格不画，交给渲染）；其中也在 `passes()` 里的才 `HIDE|PASS`（去掉碰撞）。人站在旁边、绕着走的溶解格（生态穹的树干穿过两层岛面、又是螺旋梯的中柱）只能 HIDE，否则留下一口能掉下去的井（0.11.0 开发中的 bug，`TileEcoDomeTest.theTrunkHidesButStaysSolid`、`EcoDomeWalkTest.theCellsPlayersPassThroughAreOutOfReach` 守着）。**例外**（0.12.0，CITY §4、§4.8）：穹顶发生器的腿（`P`）和发射节点（`C`）是 `HIDE|PASS`，和中枢的 `P`/`C` 一样——腿立在广场上、人碰得到，成型后它们是渲染出来的弧肋，穿过去就是设计；它们不托着任何人站的地面，穿过去也掉不进什么。潮汐井的潮汐环（`R`）照规则：悬在水池上方、没有人站得到的地方（`RootShapeTest.theRingsAreOutOfReach`）。0.13.0 的三座亭子也照规则、不是例外（M10 允许把人碰得到的写成例外，但行走测试证明都碰不到）：秘法庭的塔尖（`CZ`，y30–32，在尖塔的顶盖上、离地面 30 格）、回响猎场的天篷环（`R`，y9，最高的看台和桥上人站在 4，环比脚高 5 格）、魔力泉阵的方尖碑（`CZ`，y3–7，立在控制器上、在旁边任何人的头顶上，泉阵里没有人站在地面以上）——`XWalkTest.theCellsPlayersPassThroughAreOutOfReach`；血契坛什么都不溶解（血珠是画出来的）。**第二个例外**（0.14.0，CITY §4.7「溶解」）：谐振塔桥上的核心柱（导能柱 y9–11 和晶核座 y12，`CZ`）是 `HIDE|PASS`，和中枢的导能柱、晶核座一样——谐振室的楼面 y8 从它下面穿过，溶解之后人能走进核心那一列，正好多出一个站的格子（(15, 8) 站高 9，头顶是格子 y13 的玻璃）、别的什么都不变（`ResonanceGateWalkTest.theDissolvedCoreOpensItsColumnAlone`、`TileResonanceGateTest.theSpireCoreDissolvesAndLetsPlayersThrough`）；尖塔是渲染画在桥顶上面的，人走进去碰不到它。二向箔仓什么都不溶解（画布上的画是画在方块上面的）。
- **朝向**：`place(player, front)`——手放在某座已加载中枢、这种模块某个场地的控制器格上时朝向中枢（引擎 commission 的朝向，同档案馆）；`consoleAt` 的格子由 `Consoles` 右键开窗口。
- **本地坐标**：`local(x,y,z)`（方块 → 形状格，0.12.0 起经 `CityBuild.localOf`，蓝图伸出形状盒子的部分是负的 y）、`localPoint`（世界里的点 → 形状里的点，减去偏移）、`worldOf(lx,ly,lz)`、`worldDir(lx,lz)`。
- **单元和样本**：`unitCount`、`unitAt`、`slotAt`（样本座 → 槽）、`slotCell`、`unitCells`、`unitSlots` → `{first, count}`、`sample(slot)`（服务端读 handler，客户端读 `clientSamples`）、`samples()`、`unitFill`、`firstFree`、`takeSlot(unit, x, y, z)`（0.14.0：潜行空手取回的槽——点的是样本座就是它的槽；单元没有样本座（画布、二向箔，一个槽画满整面）时点它任何一格都取最后一个有东西的槽；都没有是 −1）、`sampleCount`、`unitName`（如 `G-RB`）、`unitKind`（lang `fluxecho.unit.<kind>`）、`selectUnit` 和字段 `selectedUnit`（服务端；存档 `Unit`，不进描述包，窗口自己同步）、`openOnUnit`（只在服务端、不存档：`UnitUse` 开窗前设上，窗口开在「单元」页后清掉）、`contents()`。
- **样本 handler**：`layout().slots()` 格（生态穹 36），每格上限 1，**任何物品都收**（结构阶段的选择，CITY §6.1 第 15 条；不像书库那样查重复），tile 失效后不收。
- **供电**：成型、`nexus() != null`（停靠着）、`nexusEnabled`、有队伍 → 每 tick 从团队无线电网扣 `upkeep()` = `Config.moduleUpkeep`（`nexus.moduleUpkeepEuPerTick`，默认 128），扣到了就 `powered`（`ServerEvents.addTeamTick` 记统计），变化时重发描述包。没电时单元里的东西照样在。每 100 tick `Directory.report`：名字 `tile.fluxecho.module_core.<key>.name`、状态 `fluxecho.dock.<停靠状态>`（停靠没电报 `no_power`），停靠且有电 IDLE、否则 PROBLEM。
- **`standing()`**：tile 没失效、区块在、格子里还是它——窗口的校验器。**拆核心**：`takeDown` 把样本全部掉在核心处、清空 handler（不像档案馆的书进队伍仓库）。
- **NBT**：`Samples`（handler）、`Unit`，加多方块的键；读档先读进存档大小的临时 handler 再逐格拷（槽数变了也不清空）。**描述包**：`P` 有电、`Dk` 停靠状态、`Rv`/`Ep` 增量修订号和这个 tile 的寿命号（每次加载随机）、全部样本 `B`/`Bi`（`BookPacket`）。

### 4.7 样本的同步（`NexusNet.UNIT_DELTA = 5`）

- 照档案馆的书（campus.md §12）：描述包带全部样本（`BookPacket.write`，压缩后 < `LIMIT` 28000 字节，超了的只发 id/meta）；之后只发变了的槽：`UNIT_DELTA`（`Dim/X/Y/Z` 控制器、`Rv`、`Ep`、`B`/`Bi`/`E`），每 tick 最多 `BookPacket.DELTA_SLOTS` 64 槽（剩下的下一 tick），发给 `Sight.near(中心, effectRange + 64)` 并上正在看控制器区块的人（`audience`）。
- 客户端：`NexusNet` 的客户端泵直接交给 `TileCityModule.receiveDelta`（不经星图的 sink，出错只记日志）。每个槽记修订号，比现有的新才收；描述包还没到（或 `Ep` 不是这个 tile 的）增量就停着（最多 `PARKED_MAX` 128 条、30 秒），描述包到了按 `Rv` 顺序重放；描述包的 `Ep` 变了（服务端换了一个 tile）之前听到的全作废。有变化 `clientSampleVersion` +1（渲染和窗口靠它）。离开世界时 `FrameClient` 调 `clearClient()` 清掉停着的包。
- 窗口不另发样本：单元列表、平面图、计数都读 `clientSamples`（M8 说的「分页同步」实际是客户端分页，§9）。

### 4.8 单元的右键（`UnitUse`，档案馆 `Units.use` 的通用版）

- 登记在 `BlockFrame.onUse` 和 `BlockFitting.onUse`，0.14.0 起还有 `BlockDeck.onUse`（单元由构架、配件和画布组成：画布是地板方块 `fluxecho:deck` 的 meta 12，`BlockDeck` 加了和构架、配件一样的 `Use` 接口、`onUse` 和 `onBlockActivated`；档案馆的 `Units.use` 先登记、先问）。两端同样回答：在 `NexusRegistry.loaded(w)` 里找已成型、`covers` 这格、这格属于某个单元的城市模块 → 返回 true（客户端挥手、不放手里的方块），只有服务端动作：
  - 不是本队（`BlockNexus.mayUse`）→ `city.unit.not_yours`（主人名）；
  - 空手 + 潜行 + 点的是有样本的样本座 → 取下那一个放到手上（`city.unit.took`：物品名、单元名）；0.14.0 起没有样本座的单元（画布、二向箔）点任何一格都取它最后一个有东西的槽（`TileCityModule.takeSlot`，`TileDualFoilVaultTest.aSneakingHandTakesACanvassItemAnywhereOnIt`）；
  - 其余空手 → `selectUnit` + `openOnUnit` + `BlockNexus.open`：窗口开在「单元」页、显示这个单元；
  - 拿着东西 → 一个放进第一个空槽（创造模式不扣），`city.unit.put`（物品名、单元名、n、槽数）；满了 → `city.unit.full`（单元名、n、槽数）。
- 和档案馆一样，**拿着任何东西右键单元都会当样本放进去**，所以不能贴着种植床放方块（设计如此）。
- 聊天消息的参数个数由 `LangTest.everyChatMessageGetsItsArguments` 守着：1.7.10 里占位符比参数多会在客户端格式化时抛异常、客户端崩溃（0.11.0 开发中 `full` 就少传了一个）。它查代码里字面写出的 `new ChatComponentTranslation("fluxecho.…", …)` 和 `UnitUse` 的 `tell(p, "x", …)`。

### 4.9 通用模块窗口（`CityGui`、`CityScreen`、`UnitSlot`；CITY §1.6 第 7 条）

- MUI1，260×256，背包在 (49,174)，flux 风格加模块色。顶行：标题 (6,5) 120×14；三个标签 状态 / 单元 / 平面 在 x 130/172/214、y 4，各 40×15（`TAB_X`，`city.gui.tab_status/units/plan`），页只在客户端。页面区 y 22–140；下面一条（分隔线 y 142，内容 (9,145) 242×26）：样本数（`city.gui.foot.samples`：保管的样本 n/m，共 k 个单元）、停靠状态、当前页的提示（`foot.status/units/plan`）——三个标签之后标题太窄，停靠状态挪到了这条里。
- **状态页**：左边 (11,26) 154×110，每行 12 像素：结构、停靠、供电、维持（`city.gui.status.upkeep_value` n EU/t）、相位、场地、单元（`status.units_value`：n 个单元 · a/b 个样本）、主人，最后一行提示（`status.hint.unformed/undocked/no_power/running`）；悬停 `status.tip`（各行的键都在 `city.gui.status.` 下）。右边 (176,25) 76×112 一个随状态动的模块色徽记。
- **单元页**：左边列表 (9,37) 8 行 × 12 像素、宽 106，列表头 (9,25)（`city.gui.units`），翻页箭头 (89,24)、(103,24)（多于 8 个单元才出现，滚轮也翻，循环，`UnitBrowse.page`）；每行名字、种类、装满程度（`city.gui.fill`），点行 → 请服务端显示它。右边单元标题 (126,25) 124×21（名字、种类、`where`「第 n 层 · 高 y」）；格子从 y 52 起每行 6 个、最多 18 个（`GRID_COLS`/`GRID_MAX`；格子数 = 最大单元的槽数，`gridSlots`），在 x 122–254 里居中（生态穹 3 格，从 x 161 起）；提示 (126,107)（`city.gui.hint`/`hint_full`）；◀ ▶ 在 (126,122)、(233,122) 按单元循环（`UnitBrowse.step`），中间 (145,122)「第 n 个 / 共 N 个」（`city.gui.nav`）。
- **平面页**：`ModuleMap` 的平面图在 x 6–134 里居中（生态穹 83×107 在 (28,27)：一格 3 像素，门在下方，横轴朝走进门的人的右手边）；层标签 (140,26) 每个 26×13、每行 4 个、最多 8 个（`storey_short`，悬停 `storey.tip`）；选中单元的信息 (140,57) 112×60；「打开单元」(140,122) 112×14 → 换到单元页。每层画的是这一层高度上立着的东西（`ModuleMap.Mark`：地面、光带、草、水、墙、实心、玻璃、壁柱、门、栏杆、立柱、楼梯、家具、控制台座、控制器、溶解的树干，0.12.0 加光梯台 `LIFT`——地面上一个呼吸的青色光圈，`^` 本身和脚下是 `^` 的空格都是它）。**嵌在地面里的单元**（0.12.0，`ModuleMap.flush`）：某层有单元格的旁边同一高度是地面类部件（`FLOORS` = `dBFg:e%#*`）——潮汐井的汲口嵌在池沿里、和步道一样高，人站在上面一层——这一层的图就画上面一层（和生态穹的种植床立在楼面上一样）；空格下面是池沿、样本座、底座（`u`/`o`/`h`）时仍画成家具。0.13.0：某层的单元格旁边同一高度有人站的空格（下面一格是实心的）时这一层**不算**嵌在地面里——猎场的印记台立在竞技场地面上、背后贴着看台的方块，人站在它旁边的竞技场里，所以平面图停在竞技场那一层，不往上挪一层变成一个洞（`HuntingGroundShapeTest.theWindowsPlanStaysOnTheArenasFloor`；生态穹、穹顶发生器、秘法庭照旧不是，潮汐井照旧是）。单元按装满程度上色（空的暗、装着青、满了模块色），选中的描边；点图选最近的单元（`ModuleMap.nearest`，`REACH` 3 像素内）。
- **只看建筑的层**（0.13.0，`ModuleMap` 的第六个参数 `levels`、`TileCityModule.planLevels()`，`CityGui.map` 传进去；老的五参数构造器没有）：单元的楼层之后再加几张平面图，每张画那个高度上立着的东西，不放单元（`overview(storey)` 为真、`nearest` 永远 −1）；层必须一个比一个高、高过所有单元楼层、在盒子里，否则 `IllegalArgumentException`。标签写高度 `city.gui.level_short`「高%s」（英文 `Y%s`），悬停 `city.gui.level.tip`「高 %s · 从上面看这座建筑，这一层没有单元」（`CityScreen.storeyTab`/`storeyTip`）。现在只有血契坛用：第 1 层（单元站的 y1）、高 8（二层台墙脚、y7 平台上）、高 13（三层台、y12 平台上），看得出一层层往里收的漏斗（`TileBloodCovenantTest.theWindowsPlanShowsTheTiers`）。`storeyAt(y)` 取不高于 y + 0.5 的楼层里最高的那个（和楼层的先后无关）。
- **中心的控制台**（0.13.0，`ModuleMap.centre`）：平面图上控制器那一列是空格、正上方一格是控制台座（`K`）或控制器（`~`）时，这一格画成 `CONSOLE`/`CORE`——血契坛的控制台座在 y2 的祭台上，平面图在 y1、祭台正中那格是空的（生成器这么画），于是平面图正中画出开窗口的地方；那格本来有东西的（控制台座、墙）不变。其余模块的平面图逐格不变。
- 25×25 的亭子：平面图一格 3 像素，77×77（`MAP_MAX_W/H` 124×112 里）。
- 0.14.0：谐振塔的平面图一格 3 像素、95×53，两层（高 1：两座翼楼的门厅和 4 个调谐架，路中间亭子的控制台座画成 `CONSOLE`；高 9：楼上和桥上谐振室的 6 根谐振柱，`TileResonanceGateTest.theWindowsPlanShowsBothStoreys`）；二向箔仓一格 3 像素、83×107，三层（高 1、9、17：地面、第一层走道、第二层走道，各一层画布；门间柱和桌子上的两个控制台座都画成 `CONSOLE`）。**挂在墙上的单元**（`ModuleMap`，`Unit.base > floor`）画成它整个的占地、每列一块：二向箔在一楼的平面图上是后墙一条 13 格宽的整条，点它任何一列都选中它（门位把它最低一排断成两段，否则会画成两段、点中间选不中，`TileDualFoilVaultTest.theWindowsPlanShowsTheTiersAndTheFoil`）；别的单元照旧画最低一格（`base`）。
- **选择**：每个窗口自己的（服务端 `Sel`，起点 `selectedUnit`，变了写回 `selectUnit`，下次开窗从这里起）；客户端经 `Link`（`FakeSyncWidget<Integer>`，id 1 `ASK_UNIT`）请求，服务端 `UnitBrowse.valid` 检查后作为同步值发回（`View.unit`），客户端不预测；◀ ▶ 的回调直接在服务端跑。
- **格子**（`Grid`）：服务端每次访问都按当前选择算出 `first + k`、直接读写模块的 handler（换单元不会留下过期的映射，容器只看到内容变了的格子）；超过这个单元槽数的格子什么都不做；客户端是一份副本，它的检查只按布局预测。客户端每 tick 按页和单元开关格子（`enableGrid`，`SlotWidget.setEnabled` 自己通知服务端）。**动过格子的点击之后服务端下一 tick 整窗重发**（`Link.resend`：原版相信客户端已经预测了点击结果，而选择刚变时客户端的格子可能还是上一个单元，物品也可能被服务端拒了）。`UnitSlot`：热键把整堆塞进空格时只留一件，其余还给玩家（装不下掉脚下）并整窗重发（同档案馆的 `BookSlot`）。Shift 点背包里的物品 = 一格一个填进显示中的单元的空格；Shift 点格子里的样本 = 取回背包。
- 服务端同步给窗口的（`FakeSyncWidget`）：`formed`、`dockStatus`、`powered`、`upkeep`、场地（停靠时是 `slot()`，否则 −1；状态页按 `SiteFamily.labelKey` 写：环上的场地是 `build.gui.site`「n 号位」，0.14.0 起 0 是 `build.gui.site_gate`「大门」，8 是 `build.gui.site_zenith`「天顶」、9 是 `build.gui.site_root`「根位」）、相位（停靠时中枢的 `phase()`，0.12.0 起不再是常量 `TileNexus.PHASE`；否则 0）、主人名，和打开时的页（`openOnUnit` → 单元页；玩家自己点过标签之后不再管它）。
- 校验器 `standing()`：核心被拆或区块卸载就关窗。`CityGui.map(c)` 按模块键缓存平面图（两端同一份纯数据）。

### 4.10 通用渲染钩子（`CityRender`、`CityFrame`、`ModuleLook`；CITY §1.6 第 8 条）

- **入口**：`NexusRender` → `ModuleRender.draw` → `CityRender.draw(tile, t, fade)`——没有自己的 `FarDraw` 登记，跟着中枢的渲染器一起画、一起画进光门（gates.md §5）。`NexusRender` 对客户端每个多方块（`ClientTiles.all()`）各算一次距离：模块的**控制器**离相机超过 `effectRange + 48` 就整座不画（中枢在这个距离外只画光柱），最后 16 格按 `fade` 淡出；停没停靠都画（没成型的只画墙裙光线）。**地标**（0.12.0，CITY §1.3「地标画到 512」）：超出这个距离、但控制器在 `LANDMARK_RANGE`（512 + 160）以内的模块走 `ModuleRender.landmark` → `CityRender.landmark(m, t)`，只画它的地标——现在穹顶发生器（`DomeGeneratorRender.landmark`：停靠时的穹顶、系泊的远处支腿，§6.2）和 0.14.0 的谐振塔（`ResonanceGateRender.landmark`：浮着的尖塔、碎晶的光点和脉冲，§6.8）有，别的模块什么都不画。
- **档位** `ModuleLook.tier(在里面, 离盒子的距离, NEAR 24, effectRange)`：`NEAR`（相机在里面或离盒子 ≤ 24 格）、`MID`（≤ `effectRange`）、`FAR`（更远）。
- **每座城市模块都有**：
  1. **墙裙光线**（`ModuleLook.course`：y1 的 `w` 格（和门位的软墙裙 `[`，同一种方块）朝外的面——0.14.0 起「朝外」也包括朝着一条通到外面的路的面（`ModuleLook.outside`：从盒子外面在墙裙那一层穿过空格往里灌，不穿过门洞的必须是空气、不穿过立着的东西；谐振塔桥下的通道算外面，虽然门楼的地面从下面铺过去；大厅里面只有门通着，不算），谐振塔因此 92 个面（两翼一圈 64，加通道两边的墙 28，朝通道的门那几格没有），别的模块逐面不变（`GateLookTest.thePlinthLineRunsAlongThePassageToo`）——按绕楼的角度排序，从正面中间开始、往走进门的人的右手边绕；门洞没有墙裙，线在门口断开；门位开成光门之前线照样连着）：停靠有电 = 模块色，一道脉冲绕一圈（每 tick 走 0.4 个面，生态穹约 16 秒一圈）；停靠没电 = 琥珀色呼吸；没停靠 = 暗灰蓝；未成型 = 更暗，只亮墙裙方块真的立着的面（逐格看世界，独立的核心和盖到一半的楼不会有悬空的线）。每面一条芯，NEAR 再加光晕；FAR 不画（细于一个像素）。
  2. **成型扫光**：模块色平面加白边升过 `bounds()`（`SWEEP_MS` 2 秒内；迟到的客户端不重播）。穹顶发生器的扫光换成它自己的（`DomeGeneratorRender.sweep`：沿六条腿往上、再穿过吊舱）；二向箔仓在通用的之上再加一层（`VaultRender.sweep`：正面的画布跟着一道亮线被洗成羊皮纸白，§6.9）。
  3. **名牌**（`ModuleLook.plates(build)`，0.12.0 起一座模块可以有几块，按模块键缓存在 `CityRender.PLATES`）：有门的模块一块（`ModuleLook.plate`：控制器轴上、雨棚中间的上方 1.75；没有雨棚就在控制器上方 3.6、门外 1.5）；**中心对称的楼**（0.13.0，`ModuleLook.central`：控制器在盒子正中那一列，就是亭子）没有雨棚，名牌浮在中心控制台上方（`overCentre`）：控制器上方 3.6，控制器上面立着一根柱子时（魔力泉阵的方尖碑）在柱顶上方 `OVER_COLUMN` 1.75——秘法庭和猎场在 y5.6、血契坛 y6.6、泉阵 y9.75（模块坐标，y0 是地基层）；**坐光梯进的模块**（形状里有光梯台 `^`，`ModuleLook.liftPad` 找最低的一个：天顶的吊舱、根）没有门，名牌浮在控制台座上的控制器上方（控制器方块脚下往上 `OVER_CONSOLE` 1.4，在天花板下面）；**悬在空中的**（`CityBuild.offset()[1] > 0`，蓝图往下伸出自己的地面，像吊舱的腿）再在舱底下面、光梯井正上方挂第二块（光梯台往下 `BELLY` 2.2），给广场上的人看。相机离某块名牌水平 `PLATE_CLEAR` 1.5、竖直 `PLATE_CLEAR_Y` 4 以内就不画这一块（乘梯穿过舱底的人会从名牌里穿过去）。48 格内朝相机，最后 16 格淡出；第一行模块名（`module.<key>`，模块色，1.5 倍），第二行运行时是 `city.plate.on`（运行中 · n/m 个样本，白色），否则是停靠状态（`dock.*`，琥珀色）。
  4. **样本座里的物品**（只在 NEAR）：离相机 24 格内最近的 256 个有样本的样本座（每座模块各算各的），每个不贴着整块方块或别的样本座的侧面一份：凹槽里一个模块色光点（`Motes`）和一张物品图（先方块图集再物品图集，每个渲染 pass 一层颜色），轻轻浮动；取不到图的物品跳过。凹槽和图的位置是 `ModuleLook` 的常量（`RECESS_LOW/HIGH` 11/16–15/16、`RECESS_IN` 2.7/16、`PICTURE_MID` 13/16、`PICTURE_HALF` 0.1），`FittingRender` 和 `CityRender` 共用：站在种植床的地面上，从池沿外 0.3 到 24 格都能越过池沿和唇边看到样本的中间和上沿，8 格内整张（`ModuleLookTest.aSocketsSampleShowsOverTheRim`）。
  5. **模块自己的画面**：`m instanceof TileEcoDome` → `EcoDomeRender`（§6.1）；`TileDomeGenerator` → `DomeGeneratorRender`（§6.2，多传一个 `appear`：支腿和穹顶自己量距离、自己淡出）；`TileRoot` → `RootRender`（§6.3）；0.13.0 的 `TileArcaneCourt` → `ArcaneCourtRender`、`TileBloodCovenant` → `BloodCovenantRender`（多传相机位置）、`TileHuntingGround` → `HuntingGroundRender`、`TileManaFountain` → `ManaFountainRender`（§6.4–6.7）；0.14.0 的 `TileDualFoilVault` → `VaultRender`（§6.9）、`TileResonanceGate` → `ResonanceGateRender`（多传相机位置和 `appear`，§6.8）。新模块在这里加一个分支（有地标的再在 `CityRender.landmark` 里加）。
  6. **画布上的画**（0.14.0，`CityRender` 新的一部分 `CANVAS`，`CanvasRender.draw`，只在 NEAR；位置和画法是 `CanvasLook` 的）：每个由画布 `Q` 组成的单元有一块画面（`CanvasLook.fields`：单元的画布格在它朝外那一面上盖住的长方形，离方块面 `OFF` 0.02；二向箔仓的 42 幅画布是长墙上 3 宽 4 高，二向箔是后墙上 13×11——门位的 `$` 是同样的方块、但不是单元格，二向箔的画面照样是框里的整块）。单元里有东西就把它大大地平画在画面上（`picture`：画面短边的 `PICTURE` 0.86、最大 `PICTURE_MAX` 4.5 格，居中）：底色是物品自己的平均色，上面是**画过的**物品图标（`CanvasLook.paint`：先缩到最多 `ICON_MAX` 32 像素，再放大 `SCALE` 2 倍；色调分离成 `VALUE_LEVELS` 5 级明度、`SATURATION_LEVELS` 4 级饱和度、色相按 `HUE_STEPS` 24 步，加短的斜笔触 `DAB` 0.08，深一档的描边 `OUTLINE` 0.3，透明度 < `SOLID_ALPHA` 128 的像素算画布）；画面不完整的（二向箔）底色只在画周围伸 `GROUND_MARGIN` 0.25。空的单元沿画布格画一层模块色的淡网格（`grid`）。同样守档案馆书的规矩：每座模块离相机 24 格内最近的 256 幅整张画，远一点的只画底色那一块；背对相机的画面不画。动画图标取第一帧。画好的图标放在 `city/client/Paintings`：每个图标从它贴图的文件读一次（和物品图集读的一样），画进一池小贴图里的一张（`SIZE` 64 像素见方，画在左上角，所以光影包下也像任何贴图一样绑定），连平均色一起留着；每次最多新画 `BUILDS` 4 个（其余这一帧先从图集画），最多留 `MOST` 256 个，最久没画的让出它的贴图——池里的贴图做一次、反复用（这个版本的贴图管理器从不释放贴图名），最多 257 张，约 4 MB；读不出来的图标（模组自己加载的、没有那个文件的）记下来，直接从它的图集画（每个渲染 pass 一层颜色）。这是**缓存不是计数**：一个图标画出来是什么样和画了几次无关，光门后面再画一遍一样；离开世界和重新拼贴图集（换了资源包，`TextureStitchEvent.Pre`）时由 `FrameClient` 清空（`Paintings.clear`）。
- **预算**（一座模块每帧）：墙裙每面一个芯 quad（生态穹 125 个面，亭子 76 个），NEAR 加光晕（共约 375）；扫光是一个平面加四条边线、每条按 `DomeLook.PIECE` 8 格切段（`ModuleLook.sweepQuads`，按 `bounds()` 用的蓝图盒子：生态穹 1 + 2·4 + 2·5 = 19，25×25 的亭子 17，只在成型的 2 秒里画；0.12.0 之前写的「约 5」没数切段）；名牌约 15 加每个字一个（悬空的模块两块）；样本座物品每个敞开的面一个光点加 1–2 层图（生态穹 96 个敞开的面，最多 288）。生态穹合计近约 1,690（上限 2,000）、中约 290（300）、远约 52（64）——这几个数是按扫光 5 估的，成型那 2 秒里还要加 14。穹顶发生器和潮汐井照同样的上限（近 2,000、中 300、远 64），各自的数在 §6.2、§6.3；穹顶发生器的地标另有上限 `DomeLook.BUDGET` 400。
- **共用画面的上限**（0.13.0，`ModuleLook.baseQuads(build, layout, tier)`，亭子的预算测试拿它加上自己的 `XLook.quads`）：FAR 只有扫光；MID 再加名牌（每块 `PLATE_QUADS` 80：底板和两条线 15、两行字最多 65 个字，是估计）和墙裙每面一个芯；NEAR 墙裙每面三个（芯和两层光晕），再加每个样本座四个侧面各一个光点和最多两层图（`sockets × 4 × 3`）。四座亭子：秘法庭近 853、中 173、远 17，血契坛和猎场近 469、中 173、远 17，泉阵近 517、中 173、远 17（`ModuleLookTest.theSharedDrawingsQuadsAreCountedByTier`）。0.14.0：谐振塔近 659、中 187、远 15（墙裙 92 个面、24 个样本座），二向箔仓近 252、中 150、远 19（没有样本座）；画布的画另算（`CanvasLook.quads(fields, tier)`：NEAR 每块画面取整张画 `paintingQuads` 4——每个渲染 pass 一张、最多 `PASSES` 3，加底色 1——和空网格里大的那个，二向箔仓 43 块共 430；MID、FAR 不画），两座各自的预算测试把这些加上自己的 `VaultLook.quads`/`GateLook.quads`（§6.8、§6.9）。
- **`CityFrame`**（0.12.0）：控制器在形状里的格子取 `CityBuild.controller()`（形状自己的坐标，蓝图可能伸出形状的盒子），不再从蓝图倒推；`local(worldY)` 把世界高度换成形状里的高度（穹顶发生器拿它把扫光的高度换到腿上）。
- **光影规则**：只在中枢的 `RenderWorldLast` 里画（阴影 pass 已经跳过）、相对 `renderPos*`、只用 quads、每个顶点有朝观察者的法线、不写深度、popAttrib 后手动开贴图；动画只按世界时间算，什么都不计数、不生成、不发包，光门后面再画一遍也一样。每部分（墙裙、扫光、名牌、样本座、模块画面、整体）单独失败：记一次日志、本次运行关掉这一部分，GL 状态在 finally 里恢复（`recover`/`close`）。`CityFrame`：模块本地坐标 → 相机相对坐标（`Blueprint.point` 的逆），朝相机的线条和光带至少约 1 像素宽（每格距离 `PIXEL` 0.0022，每段 ≤ 8 格）。
- **`ModuleLook` 的其他规则**：`water(build, y, cap)`（这一层每格水的中心和涟漪最多能扩多大还在水上，最多 `cap`）；`beeName(注册名)`（按大写、数字、下划线、点切词，`bee` 单独或后面跟 drone/princess/queen/larva/larvae 才算蜜蜂：Forestry 的 `beeDroneGE` 算，beef、熟牛肉、beeswax、beeCombs、beehouse 不算——不引用蜜蜂模组的类，守可选模组隔离）；`EYE` 1.62、`WATER_TOP` 8/9（静止水面在方块顶下 1/9）、`BASIN_TOP` 10/16、`LIP_TOP` 12/16、`LIP_MIN/MAX` 6/16–10/16。

### 4.11 Waila、NEI、「建造」页、总图

- **Waila**（`NexusWaila`；提供者登记在 `BlockNexus` 上，所以覆盖模块核心）：`feDock/fePowered/feSamples/feCap/feUnits/feOwner`；正文是停靠状态（停靠没电显示 `no_power`）、`waila.city`「样本 n / m · k 个单元」、主人。
- **NEI**：模块自己的三维预览（`IMultiblockInfoContainer.registerTileClass(TileEcoDome.class, NexusModule.infoContainer("eco_dome.structure"))`，显示 `MAIN` 和用料）。中枢预览 6–10 级（`TileNexus.previewModules`，CITY §3）：相位允许的每种模块（研究的相位到了）各一座，立在它的偏好里第一个还空着的场地上（偏好都是自己族的场地），控制器在 spec 给的位置、朝中枢，最多 `RingSlots.open(相位)` 座——相位 I 是后面的档案馆和右边的生态穹；有 spec 的模块偏好的场地都被占了就**不立**（debug 日志一行，不占名额），绝不退到别处去和别的模块重叠；只有没 spec 的模块种类（现在没有：书库和生态穹都有 spec）才用空着的内环槽（0.9.2 做法），内环槽和场地号分开记（`logic/PreviewSites`，`PreviewSitesTest`）；不铺园区地面。**轴上的种类**（0.12.0：第一个偏好场地是 `SiteFamily.axisSite` 的，天顶 8、根 9）不占名额：场地空着就立，内环名额用完之后、排在它前面的环上种类不立，它照样立（`PreviewSitesTest.axisKindsTakeNoRoom`）。所以相位 II（7 级）是后面的档案馆（场地 4）、右边的生态穹（2）、天顶上的回响穹顶发生器（8）和地下的潮汐井（9）。0.13.0 起亭子也是相位 II 的种类、占内环名额，按登记顺序（书库、生态穹、穹顶发生器、潮汐井、秘法庭、血契坛、猎场、泉阵）相位 II 的 4 个名额是档案馆、生态穹、秘法庭（7）和血契坛（5）；相位 III（8 级）起名额 8，猎场（3）和泉阵（1）也立（`PreviewSitesTest.thePavilionsTakeTheDiagonals`：十种模块各占一个场地）。按检查格算，每个相位最多 19,623 格（相位 III：中枢、档案馆、生态穹、潮汐井、系泊的穹顶发生器和四座亭子；`thePreviewStaysWithinItsBudget`，上限 60,000）。0.14.0 起谐振塔和二向箔仓也是相位 II 的种类、占内环名额，排在登记顺序的最后（…泉阵、谐振塔、二向箔仓），所以相位 II 的 4 个名额还是档案馆、生态穹、秘法庭和血契坛，相位 III 起才立大门上的谐振塔（0）和左边的二向箔仓（6，它的第一个偏好），8 个内环位正好满（`PreviewSitesTest.thePavilionsTakeTheDiagonals`）；预览的格子（蓝图里的检查格和必须是空气的格）每个相位最多 26,821（相位 III：再加谐振塔 2,370 + 136、二向箔仓 4,644 + 48；`thePreviewStaysWithinItsBudget` 从相位 II 起就把它们都算上，比相位 II 真立得下的多）。立每座模块之前调 `m.previewPhase(phase)`（§4.6：穹顶发生器相位 II、III 立在腿上，相位 IV 起浮空）。
- **「建造」页**：城市模块的阶段名是 `build.stage.c<n>`（§4.3）。没有任务时的模块列表多了 `RING_FULL`：研究了、有空场地、只是内环位置满了（停靠的 0.9.2 大厅也算一个）→「内环已满（停靠的 0.9.2 大厅也占一个）」（`build.module.ring_full`），总图牌子写 `build.masterplan.status.ring_full`「内环已满」（`Campus.offerStatus`，campus.md §10）。0.13.0 起模块有 8 种，任务面板放不下，列表**翻页**：每页 `NexusGui.MODULE_ROWS` 3 个（每个两行），列表标题那一行右边一个 16×10 的页码按钮（(116, 36)，写「n/N」，0.13.0 是 3 页，0.14.0 有 10 种模块、4 页；`modulePages`，只有一页时不出现），点它翻到下一页、循环，提示 `build.gui.module_page.tip`「翻到模块列表的另一页」；页码只在客户端（`View.modulePage`）。
- **总图**：对角位的线是亭子的 `oct(12,16)`（中心 ±36，`Masterplan.DIAGONAL_CHAMFER = CampusPlan.DIAGONAL_K`；空着时是虚线）；大厅位照旧画 27×35 的占地（档案馆和生态穹同样大）。0.13.0 起对角位和大厅位一样写计划在那里的模块（`planned`：「模块名（状态）」），立着模块的用模块色写它的名字；对角位的牌子立在林荫道中线上、亭子内侧切角门前面（`CampusPlan.pavilionSign`：(±26, ±26)，`|a|+|r|` 52，在林荫道的箭头上，离地 3.2，同大厅门前的牌子），在任何亭子和 apron 外面，所以亭子建起来不会把牌子关在墙里（`PavilionPlanTest.theMasterplanSignStandsOnTheAlleeBeforeThePortal`：四座亭子 × 4 个朝向都不在那一列放方块）；只有对角位上站着的是不是亭子的模块（0.9.2 停在老内环槽上的模块，停靠着或被拒，`ringStanding`）时牌子才回到八角中心、离地 3.0（0.12.0 的位置），那个模块正好盖着林荫道那一点；空着、计划中、在建、立着亭子的对角位都用林荫道上的位置，任务开始时牌子不跳。0.12.0 起总图画 `SITES` = 10 个场地：内环 0–7 加轴上的两个——**天顶**是吊舱的八角形（`oct(POD_R, POD_K)` = `oct(10,14)`）虚线画在广场上、在舱底高度（Y0 + `FLOOR_UP` 40）再画一圈，加六条腿的脚（`DomeGeneratorShape.feet()` 各一个小方块），牌子挂在吊舱下面（A 3、离地 `FLOOR_UP − 7.5`，避开光梯井、在自己的名牌下面）；**根**是井的八角形（`oct(11,15)`）虚线画在它上方的广场上，牌子在中枢后面（A −8.5、离地 2.2）。牌子写「天顶」「根位」（`SiteFamily.labelKey`）和那里立着的、在建的或计划的模块，同大厅位。轴上的模块按控制器是不是正好在这座中枢的天顶/根控制器格上认（`axisSiteOf`），颜色用模块自己的；中枢的停靠掩码只记内环（nexus.md §4），所以轴上的「停靠」不看它。0.14.0 起大门位（0 号）也画门楼：大门路的线照旧，两边加两翼的外形（`WING_LEFT/RIGHT`：A 23–39、`|R|` 7–15，没有门楼时是虚线，有任务时随呼吸）；0 号位**只认门楼**（大门族的模块）——停在 0.9.2 内环 0 号槽上的老模块站在大门路上，不算占了大门（`occupied` = 立着门楼，或停靠着且客户端的园区记着门楼建好了 `gateBuilt`）；状态照别的场地：有任务写「建造中」、占着写模块色和名字、有计划写计划的模块，什么都没计划才写「预留」（`build.masterplan.reserved`；到 0.13.0 为止一直写 `build.masterplan.gate`「大门」，这个键现在不用了、还留着）；牌子从 (A 31, R 0, 离地 4.2) 挪到门楼前面的路上 (A `GATE_SIGN` 20.5, R 0, 离地 3.2，同大厅门前的牌子)，门楼计划、在建、立着都不会把它关在桥下（`GatePlanTest.theSignAndTheBridgeStandBeforeTheGatehouse`）。
- **Waila**：中枢那一行的 `feDocked` 0.12.0 起是 `ringDocked()`（只数内环上的模块，对着 `feOpen` = 开放的槽数），轴上的模块不算。
- **通量终端**（`fluxlite/gui/TerminalScreen` 的模块行）：轴上的模块写场地名（天顶、根位），0.14.0 起门楼（spec 是大门族的模块）也写「大门」，其余照旧写内环槽号。**解锁的聊天消息**（`Campus.autoJobs`，`build.unlocked`）0.14.0 起第二个参数是场地名（`SiteFamily.labelKey`）：「…的投影已出现在4 号位」「…出现在大门」（中文 lang 去掉了「在」后面的空格，英文是 "is up on Gate"）。

### 4.12 轴上的模块：天顶族和根族（0.12.0，CITY §2.5–2.6、§4.8–4.9）

- **轴上**：`SiteFamily.axis()`（ZENITH、ROOT，以后的 ORBIT）的模块站在中枢的竖轴上，每个园区各一个场地：天顶 8（吊舱舱底 Y0+40，先立在六条腿上）、根 9（井盖 Y0−1 到井底 Y0−26）。它们**不占内环**：`Campus.autoJobs` 不拿它们和 `moduleCap` 比、也不让它们占名额，`busyModules` 只数 `ringDocked()` 和内环场地上的模块任务；NEI 预览同样不算（§4.11）；中枢窗口、Waila、通量终端的停靠数都是 `ringDocked()`（nexus.md §4）。它们**没有门**，坐光梯进：中枢台中心的光梯台（相位 II 起，nexus.md §4.2）往上直达吊舱的光梯台、往下直达井底小岛的光梯台。
- **`ModuleSpec.Envelope`**（13 个参数的构造器；轴上的族不给就抛）替代大厅的占地和场地盒，四个问题：

| | 天顶：回响穹顶发生器 | 根：潮汐井 |
|---|---|---|
| `footprint`（从上面看的盒子） | A ±12、R ±14（腿脚和吊舱，`DomeGeneratorShape.footprint`） | 井的八角形的盒子 ±11（`RootShape.footprint`） |
| `keepOut`（园区任务不碰的） | 40 根腿柱，每根一个**带高度的盒子** `{x0, z0, x1, z1, yFrom, yTo}`，只管腿在那一柱占的高度：平整柱碰到这段高度就去掉，腿下 Y0 的地砖和 Y0+1 的塔柱、补给口、回收箱留在园区计划里，修复能补回来（`theForumUnderTheLegsStaysInTheCampusPlans`）；腿避开了广场的光井、补给口和回收箱（`theLegsMissTheForumsWellsAndFixtures`） | 没有：它整个在广场下面 |
| `protect`（只有它自己的任务能拆的空间） | 40 根腿柱的那段高度，加吊舱的清理盒（23×23，Y0+40..Y0+52）；腿下的广场和吊舱下面的中枢还是中枢自己的 | 井的盒子，Y0−26..Y0−1；上面的广场是中枢的，园区的平整和修复停在井盖 |
| `noRoom(y0)`（放不下的原因） | `y0 + CLEAR_TOP(52) > 255` → `build.module.why.height`「放不下：会超出世界顶部」，即 Y0 ≤ 203 才行 | `y0 − 26 < LOWEST_SLAB(6)` → `build.module.why.depth`「放不下：井会挖进基岩」，即 Y0 ≥ 32（`LOWEST_Y0`） |

  `ModuleSpec.keepOut/protect` 对大厅族还是整列的 `siteBox`（`protect` 是从 0 到 255 的盒子），对轴上的族交给 `Envelope`；`Campus.siteKeepOut/siteProtect` 按模块、场地、中心、正面缓存。campus.md §5 讲这些盒子怎么用。
- **什么时候提供**：研究了、中枢的 `phase()` ≥ 族的 `opens`（天顶和根都是 2，`Campus.phaseFits`）、`noRoom` 为 null（`roomFor`）、场地空着。不够的时候模块列表是 `NO_ROOM`，带一个原因 `reason`（`Md` 里的 `W`，lang 键在 `build.module.why.` 下：`phase`「中枢要先长到更高的相位」、`height`、`depth`），「建造」页照着写（campus.md §10）。**场地空不空**（`Campus.siteFree`）对轴上的场地只看有没有已加载的模块正好站在它的控制器格上：keep-out 和模块中心是给中枢周围的地面用的，天顶只用腿碰地面。**停靠**：`TileNexus.refreshDock` 认轴上的模块要控制器正好在这座中枢的天顶/根控制器格上、而且朝向对（`campusSite`），不按中心找；停靠不占内环槽（nexus.md §4）。
- **空中建造**（天顶）：不需要支撑，引擎在空中放方块和在地上一样；先立腿（LEGS 阶段），再在 Y0+40 搭舱底。清理只清腿柱占的那段高度和吊舱的盒子（外扩 `POD_MARGIN` 1，从 Y0+40 到 Y0+`CLEAR_TOP` 52），都不平整（§6.2）。投影：吊舱在头顶 40 格，广场上的人看不清它在哪，所以悬在空中的计划多画一个**影子**（`logic/XRay.hangs/shadow/plumbs`）：一列计划里最低的格子比底座层高 `XRay.AIR` 16 以上、旁边一列也没有更低的格子，就算悬空（大厅的地面在底座层，浮岛和穹顶有地面托着；腿从它出发的那一列托着；舱底下面只有广场，不是它的计划）；悬空部分的轮廓虚线画在广场上（Y0+1 上方一点）、在它的底面再画一遍，八个方向最外的角各垂一条往下慢慢流的虚线（`BuildRender`，`DASH` 2、`DASH_GAP` 1.2；地形后面淡、没挡着的地方亮）。`XRayAirTest`：吊舱悬空、腿不算，影子是吊舱的八角形。
- **沉箱**（根）：`BuildPlan` 加两种步骤，只把格子掏空，不飞、不放、不检查——`DIG`（3：掏掉井壳里面的格子）和 `DRAIN`（4：之后再把渗进来的液体抽掉）；`digs(kind)` = 这两种，`empties(kind)` = 加上 `AIR`。SINK 阶段从井盖往下一层一层：每层先是它的壳（井盖——中心留空给光梯——、一圈 88 格的井壁、或井底板），再掏这一层的里面；每一段是一个**带**（`Plan` 的 `bands`，`bandEnd(i)`、`bandCount()`），引擎在前一个带做完之前不碰下一个带的任何一步（`Builder` 的窗口截到 `bandEnd(cursor)`），所以掏某一层之前它上面那层的井壁一定立着，外面的水碰到的是墙、不是敞着的井。DRAIN 阶段从上往下把里面每一格再过一遍。一格被掏过之后又会被后面的步骤填上（井底、配件、控制器），所以同一格有几步：`repeat(i)`（前面已经有一步在这格）和 `placedAfter(i)`（后面那一步在这格放的部件，或 −1）。
  - **判定**（`TerrainRule`）：`forDig(p, module, all, repair)`——这格已经是后面要放的部件 → ALREADY（再建一次或修复的井留着它的井底、配件和控制器）；修复只抽液体（`forDrain`，修复从不清模块的盒子）；否则同清场柱的一格（`forClear`）。`forDrain(p, module, all)`——是后面要放的部件或不是液体 → ALREADY；是液体 → CLEAR（或 `forClear` 留着它时 BLOCKED：谨慎规则下的岩浆、受保护的格子）。`Builder` 的探测对这两种步骤拿 `placedAfter` 当目标。
  - **引擎**（`Builder.dig`）：立刻做，和清场柱的一格一样走清场的预算和 EU（`buildEuPerClear`），拆下来的东西进回收箱；空着的（或已经是后面要放的部件）在本 tick 的免费额度内免费记 done，额度用完下一 tick 再看；规则或保护模组留着的画红框、跳过（同清场柱）；没有清场预算、没电、回收箱满了就停在这一步（`DIG_STOP`，回收箱满了等成员取东西，不丢）。返回 `DIG_STOP/FREE/LATER/DONE`。
  - **勘测**：开始前的勘测不把 `DIG`/`DRAIN` 步骤记 done（`BuildPlan.surveySettles` 对它们是 false）——勘测时空着的格子等这一层沉到时可能又有了东西（洞穴里岩浆遇水成的石头、末影人放下的方块），之后不会再有人看它；到它那个带时再判（空的照样免费）。重复的格子勘测只看第一步，后面的在自己的阶段判。`CampusEngineTest.theSurveyLeavesTheCaissonToItsBand`、`theCaissonsStepsDigAndDrain`。
  - **数字**：剩余时间（ETA）不算 `DIG`/`DRAIN` 步骤（`Campus` 按阶段数剩下的步骤时跳过它们，它们走清场的预算）；进度条的总数算（§9）。
- **地下的投影**（根）：投影的鬼影面被周围的地形挡着，所以在地下建的计划（`XRay.underground`：有可画的格子低于底座层）多画一层**透视**：还没建（或还没检查过）的格子的棱（`XRay.edges`：一组格子表面的折线——一条网格棱周围四格的填法不平（一格、三格或对角两格）就是折线，同一条线上连着的并成一段，井筒是几百段而不是几万条方块棱），整个计划一份、当前阶段一份；地形后面淡（`XRAY_THROUGH` 0.14）、没挡着的地方亮（`XRAY_SEEN` 0.35），当前阶段更亮（`XRAY_STAGE_THROUGH` 0.3、`XRAY_STAGE_SEEN` 0.7），随鬼影呼吸；最低一层的细线和各阶段的盒子也透过地面画一遍。`BuildScan` 换阶段时、所有格子第一次检查完时、建造中最多每 `XRAY_TICKS` 100 tick 重算一次；悬空的影子只算一次。`GhostCells.drawable` 不画 `DIG`/`DRAIN`（`empties`），`GhostCells.Index` 优先找能画的那一步（`GhostCellsTest.theIndexFindsTheStepThatPlacesABlock`）。`XRayTest`：立方体是 12 条棱、一排是一个盒子且重复的格子只算一次、L 形有凹角、沿棱相碰的格子在那里折、井的轮廓。

### 4.13 亭子族 PAVILION（0.13.0，CITY §1.1 入口、§2.1–2.2）

- **对角位**：1、3、5、7（`CampusPlan.PAVILION_SITES`），相位 II 开放（`opens` 2）；外形 `oct(12,16)`，中心 (±36, ±36)。亭子是**中心对称的楼**：四个切角门（`ShapeKit.portalCells`，两格深、4 高、y5 光檐门楣、两边亮门框），控制器在中心的控制台上；人从中枢那边沿对角的林荫道走进内侧的门，在楼的中心操作。形状都用 `ShapeKit` 的亭子辅助函数画（§4.1），后墙门位一样（§1）。
- **控制器和朝向**（`BuildPlan.pavilionController(m, site, cx, y0, cz, fx, fz)`，`moduleController` 对亭子位调它）：盒子中间那一列（`(W−1)/2, (D−1)/2`）站在八角中心 `diagonalCentre(site)` 上、地基在 Y0；蓝图正面朝 `pavilionFront(site)`，沿 A 朝中枢——中枢前面的 1、7 号位朝 −A，后面的 3、5 朝 +A（沿 A 而不是沿对角线：蓝图只有四个水平朝向）；控制器在形状里相对中间那一列的偏移按 `Blueprint.world` 的映射（横向 `(fz, −fx)`、往后 `(−fx, −fz)`）转到世界里，所以中心对称的楼控制器就在 `(±36, Y0 + ctrlY, ±36)`（血契坛 ctrlY 是 3，其余 2），地基中心正好是 `moduleCentre`。返回 `{x, y, z, facing}`；不是亭子位或正面不是水平单位向量就抛 `IllegalArgumentException`。
- **计划**（`BuildPlan.module`，§4.3）：清理是占地八角外扩 1（`oct(13,18)` 那样的 585 根柱子，在 apron 里面），到形状顶层 + 3，平整；FLOOR 是 `pavilionFloor`（§2，181 格）加亭子 y0 里面的地面（八角 481 格去掉 96 个地基 = 385 格，猎场竞技场的格栅是 HARD，其余软）；没有雨棚，种子装饰只有一侧的附属（`PAVILION_ANNEX_FROM` 8：第 8–16 排，在 apron 外排、平的侧面旁边，这一段在 `oct(14,18)` 里、避开切角门），所以 DECOR 是 3、8 或 9 步（全息牌柱、长椅、灯排或种植墙）。HARD = 检查格，4 个朝向 × 4 个对角位（`PavilionPlanTest`、`PavilionModulesPlanTest`）；任务放和清的每一格都在 `pavilionSite` 里（`theJobStaysInItsSite`）。
- **keep-out 和保护**（`ModuleSpec.keepOut/protect` 的亭子分支 `pavilionBoxes`）：`pavilionSite(site)`（apron `oct(14,18)`——里面是亭子、清理外扩和附属——加林荫道，662 格）按行切成整列盒子（`rowBoxes`，42 个，正好装下这些格子、不多一格；一个大盒子罩住林荫道会把大厅和亭子之间的一大片草地和环道都罩进去），换成世界坐标；保护是同样的盒子从 0 到 255。所以建好的亭子挡住园区的平整和铺砖，整修园区不碰它的场地（四个 tile 测试的 `theRefreshKeepsOffAStanding…`、`theSpecKeepsTheCampusOffItsApronAndAllee`）；大厅的场地盒和亭子的盒子互不相碰：立着的大厅（生态穹、档案馆）和别的亭子的 keep-out 都不挡对角位（外接方外扩 1），亭子也不挡大厅位（`TileArcaneCourtTest.noOtherModuleBlocksADiagonal`）。
- **高度**（`ModuleSpec.noRoom`，§1 竖向）：没有 `Envelope` 的地面模块查 `Y0 + height − 1 ≤ 255`；秘法庭 33 高要 Y0 ≤ 223（`TileArcaneCourtTest.theCourtNeedsRoomUnderTheWorldsTop`：223 放得下、224 和 225 不行），其余三座和所有大厅在任何能放核心的 Y0（≤ 225）都放得下。
- **提供**：照营造引擎（campus.md §5）。亭子占内环（`SiteFamily.ringSite`），和大厅一起数 `moduleCap`：相位 II `RingSlots.open(2)` = 4，档案馆和生态穹建好后只剩两座亭子的位置，四座都建要等相位 III（CITY §6.2）。偏好：泉阵 {1, 7, 3, 5}、秘法庭 {7, 1, 5, 3}、猎场 {3, 5, 1, 7}、血契坛 {5, 3, 7, 1}，被别的模块占了的场地跳过。开始前「建造」页的 ◀ 场地 ▶ 照通用的 `Campus.moveSite`/`freeSite` 在对角位之间换：拿 `ModuleSpec.footprint`（八角的外接方 25×25，外扩 1）和立着的模块的 keep-out 比。
- **停靠**（`TileNexus.pavilionSite`，`campusSite` 对亭子族调它）：模块种类有 spec、spec 的场地里有这个对角位、模块中心（地基中心）在八角中心两轴 ±3 内（`CampusPlan.pavilionSiteAt`）、离中枢底座层上下 ≤ 1、正面等于 `pavilionFront(site)` → 收编、`recordSite`，用场地号停靠（和同号的内环槽方向一致），占内环。
- **内城上限 8 的准备**（上限本身没改；玩家现在看得到的只有翻页、总图的牌子和 NEI 预览）：`RingSlots.open(3)` 是 8（早就是），停靠掩码 `D` 是一个字节、客户端读成 `& 0xFF`，8 位都在；「建造」页的模块列表翻页（§4.11），总图的 10 个场地都写计划的模块，NEI 预览里 8 个内环种类加 2 个轴上的各占一个场地（`PreviewSitesTest.thePavilionsTakeTheDiagonals`）；`TileNexus.MAX_CAMPUS_MODULES` 还是 7，0.15.0 再改。
- **中枢的光桥**（`NexusRender.siteDoor`）：停在对角位上的亭子的门是它内侧的切角门（`CampusPlan.PAVILION_DOOR` ≈ 39.24，沿对角线从中枢中心量），不是它盒子离中枢最近的角；客户端没加载这个模块时用 `CampusPlan.siteDoor(k, v.pavilionBuilt(k) || v.gateBuilt(k))`（`gateBuilt` 是 0.14.0 加的）：大厅位到 `HALL_FRONT − 0.5`，对角位只有客户端的园区记着那里建好了亭子（`Campus.ModuleState.pavilionBuilt`：`BUILT`、场地是它、spec 是亭子族）才到亭子的门，否则停在那里的是老内环槽上的 0.9.2 模块，照旧画环桥（−1；0.14.0 起大门位同理，建好了门楼才到通道口，§4.14）（`PavilionPlanTest.anUnloadedModulesBridgeRunsToItsDoor`、`TileArcaneCourtTest.theCampusTellsAPavilionFromARingModule`）。
- **名牌、平面图、总图**：名牌浮在中心控制台上方（§4.10）；平面图一层（单元都站在 y1），血契坛另有两层台（§4.9）；总图对角位写计划的模块，牌子在林荫道上（§4.11）。
- **再加一座亭子**：形状用 `ShapeKit.shell`、`centreController`、`backNiche` 画，`CityModule.pavilion(...)` 给 spec，`XShape.controller` = `BuildPlan.pavilionController(BUILD, …)`，其余照 §7；测试照 `PavilionModulesPlanTest` 和 `PavilionTileChecks`（`city/` 里给四个 tile 测试共用的检查）。

### 4.14 大门族 GATE（0.14.0，CITY §2.1–2.2、§4.7）

- **大门位**：0 号（`SiteFamily.of(0)` = GATE，`ringSite`，占内环），相位 II 开放；外形 A 23–39、R −15…15（31×17，`GATEHOUSE_FROM/TO/HALF`）。门楼**跨在大门路上**：中间 `|R|` ≤ `GATE_PASSAGE` 6 的通道（13 宽、7 高）永不建，两翼 `|R|` 7–15 才是房子；大门路、路口（A 25–29）和远端门槛（A ≥ 34）加宽到 `|R|` ≤ 5 的那几段、建立园区立的四根塔柱（(A 27, R ±5)、(A 35, R ±5)）都在通道里，原样留给中枢（`GatehouseSiteTest.theEnvelopeAndThePassage`）。通道里门楼只建两样：路中间的亭（控制台座和控制器）和路没铺到的地面（`gatePassageFloor`，58 格，§2）。
- **控制器和朝向**（`BuildPlan.gateController(m, site, cx, y0, cz, fx, fz)`，`moduleController` 对大门位调它）：盒子中间那一列（`x = MID + R`）在路的轴上、前排在 A 23（`z = A − 23`），蓝图正面朝中枢（−A）；控制器落在形状的格子映射到的地方——谐振塔的亭 `(15, 2, 7)` = `(A 30, R 0, Y0+2)`，站在路上就是眼睛高度。返回 `{x, y, z, facing}`；不是大门位或正面不是水平单位向量就抛 `IllegalArgumentException`。
- **计划**（`BuildPlan.module`，§4.3）：CLEAR 是盒子外扩 1，**但通道里只清桥**：通道外的 380 根柱子从 Y0+1 到 Y0+20（顶层 17 + 1 + `MODULE_HEADROOM` 2）、平整；通道上只有桥站的 13×7 = 91 根（`overPassage`：那一列在 `GATE_HEADROOM` 7 以上有格子），从 Y0+7 起、不平整；共 471 根、8,874 格。所以大门路的地砖、加宽和塔柱从不被清（`GatePlanTest.thePassageIsNeverClearedBelowTheBridge`、`theRoadAndThePylonsStayTheEstablishPlans`）。FLOOR 是 `gatehouseFloor`（apron 194 格）加形状 y0 的软地面（两座翼楼的门厅 210 格、通道 58 格）；没有雨棚，种子装饰只有附属（`ANNEX_FROM` 6：第 6–10 排，在一侧翼楼外两格 R ±17），DECOR 3–6 步（`theAnnexStandsOnTheApron`）。HARD = 检查格，4 个朝向（`GatePlanTest.theHardStepsAreTheCheckedCells`）。
- **keep-out**（`BuildPlan.gateKeepOut(plan, cx, y0, cz, fx, fz)`，`ModuleSpec.keepOut` 的大门分支）：按门楼计划**真正动的每一列**、从它在那一列动的最低高度起算：在 Y0 或以下动的列（两翼、它们的清理外扩、铺的地面——apron 和通道里路没铺到的格子）是整列盒子 `{x0, z0, x1, z1}`；通道里只在 Y0 以上动的列（路中间的亭从 Y0+1、桥从 Y0+7）是**带高度的盒子** `{x0, z0, x1, z1, yFrom, 255}`；按 R 一行行切，每段相同最低高度的相连格子一个盒子（`rowBoxes`）。每个朝向都是 49 个盒子：40 个整列、9 个带高度（从 Y0+1 或 Y0+7）。所以建立园区、铺设广场、整修园区照样在桥下和亭的两边铺大门路、立塔柱，而门楼计划的每一格都在盒子里（`GatePlanTest.theKeepOutIsWhatTheJobWorks`、`theForumStillRestoresTheRoadAndThePylons`、`TileResonanceGateTest.theRefreshKeepsOffTheGatehouseButNotOffTheRoad`）。
- **保护**（`BuildPlan.gateProtect`，`ModuleSpec.protect`）：整列盒子从 0 到 255，带高度的盒子保留自己的高度。门楼整个 31×17×18 的外框（`bounds()`）罩着大门路和塔柱，所以 `Campus.protectedBoxes` 对它和对轴上的模块一样跳过外框、只用场地自己的盒子（`Campus.boxedSite`：轴上的场地或大门；`onBoxedSite` 换掉了 `onAxisSite`；`CampusEngineTest.theGatehouseLeavesItsRoadAndPylonsToTheNexussJobs`）。
- **高度**：地面模块照样查（§1 竖向），18 高，任何能放核心的园区都放得下。
- **提供**：照营造引擎（campus.md §5）。大门只有一个场地 {0}，「建造」页的 ◀ ▶ 没有别处可换；门楼占内环、和大厅、亭子一起数 `moduleCap`：相位 II 的 4 个位置里档案馆和生态穹占两个，谐振塔和亭子、二向箔仓按登记顺序争剩下的两个（§9）。
- **停靠**（`TileNexus.campusSite`）：门楼和轴上的模块一样，控制器格和正面**正好**是场地给的才收编、`recordSite`、用 0 号停靠，占内环——路中间的亭在 0.9.2 内环 0 号槽的范围里，只有精确的控制器格才分得清门楼和停在那个槽上的老模块。
- **中枢的光桥**（`NexusRender.siteDoor`）：已加载的门楼按族认（它的亭在老内环 0 号槽里，按位置认会当成环上的模块），光桥伸到通道口 `GATE_DOOR` = A 22.5；客户端没加载时用 `CampusPlan.siteDoor(k, v.pavilionBuilt(k) || v.gateBuilt(k))`：只有客户端的园区记着 0 号位上建好了大门族的模块（`Campus.ModuleState.gateBuilt`：`BUILT`、场地 0、spec 是大门族）才到通道口，否则停在那里的是老内环槽上的 0.9.2 模块，照旧画环桥（−1）（`GatehouseSiteTest.theDoorAndTheSign`、`TileResonanceGateTest.theCampusTellsTheGatehouseFromARingModule`）。
- **名牌、墙裙、平面图、总图**：没有雨棚，名牌在控制器上方 3.6、门外 1.5（`ModuleLook.plate`）：模块坐标 (15.5, 5.6, −1.5)，约在通道口 A 21.5、Y0+5.6 的上空；墙裙光线也沿通道两边的墙走（`ModuleLook.outside`，§4.10）；平面图两层（§4.9）；总图的两翼、牌子在门楼前面的路上（§4.11）；窗口、牌子、聊天、终端都把 0 号位叫「大门」（`build.gui.site_gate`）。
- **以后再加门楼**：大门只有一个场地，CITY 里也只有谐振塔一座；换形状照 §7，控制器用 `BuildPlan.gateController(BUILD, …)`，keep-out 和保护自动按新计划算。

## 5. 新部件（CITY §1.4、附录 C）

| 部件 | 方块 / meta / 码 | 形状 | 光 | 配方（产量），经 `PartRecipes` | 贴图 |
|---|---|---|---|---|---|
| 样本座 Socket | `fluxecho:fitting` 7 / 码 39 | 2–14/16 的立柱，整高；四个侧面各一个凹槽（宽 5–11/16、高 11–15/16、深 2/16），凹槽边一圈青光，样本浮在里面（`CityRender` 画）；碰撞 2–14/16 整高 | 8 | ×8 `PLP/LCL/PLP`（墙板、光缝墙板、通量结晶） | `fitting/socket`、`socket_top`、`socket_glow`（动画） |
| 池沿 Basin rim | `fluxecho:fitting` 8 / 码 40 | 10/16 的半高板；中间一条 2/16 高、4/16 宽（6–10/16）的发光唇边，伸向四周同一高度的池沿（像栏杆的臂），单独一块只有中间一个结；并排的两块池沿之间的面不画；碰撞 10/16（唇边到 12/16，高出碰撞 2/16），只有底面算实心 | 4 | ×8 `T T/TST`（地缝钢、钢板） | `fitting/basin`、`basin_top`、`basin_glow`（动画） |
| 覆层锚点 Cladding anchor | `fluxecho:frame` 9 / 码 9 | 整块，每面一块六角插板（只在贴图里，走构架普通的方块画法）；以后的覆层从这里长 | 6 | ×4 `SRS/RCR/SRS`（不锈钢板、悬浮环段、通量结晶） | `frame/anchor_top`、`anchor_side`、`anchor_top_glow`、`anchor_side_glow`（动画） |
| 光梯 Light lift（0.12.0） | `fluxecho:fitting` 9 / 码 41（`Parts.F_LIFT`，形状字符 `^`；中枢形状里是 `V`，nexus.md §3） | 整块钢立方，顶面是光梯井的光圈（八片暗叶围着亮芯），侧面素钢；上面没盖着东西时顶面全亮；物品栏里整块、顶面一层光。同一竖列叠放的光梯组成光梯井，乘梯见 nexus.md §4.2；碰撞照常，乘梯的人在自己那一列穿过光梯和配件，光梯上方两格里配件（不是光梯）让任何人穿过（`Lift.passes`、`Lift.boardable`） | 12 | ×1 `LCL/CEC/LCL`（光缝墙板、通量结晶、末影之眼） | `fitting/lift`、`lift_top`、`lift_glow`（动画） |
| 画布 Canvas（0.14.0） | `fluxecho:deck` 12 / 码 28（`Parts.D_CANVAS`，形状字符 `Q`，门位里的软孪生 `$`） | 整块石质立方，同别的地板/墙板（`DECK_TYPES` 13，名字 `canvas`）；单元由画布组成时它就是画面，物品画在上面（`CanvasRender`，§4.10）；右键交给 `BlockDeck.onUse`（单元的右键，§4.8） | 0 | ×8 `PPP/PaP/PPP`（墙板、纸 `item:minecraft:paper`） | `deck/canvas`（深蓝织纹、两像素的经纬交替、每半格一条淡青网格线；不发光、没有发光层） |
| 水 Water | 码 53 | 原版水源，像草和泥土一样放（SOFT） | — | 没有配方，只花 EU：`Parts.euOnly`（草、泥土、水）每格 `buildEuPerClear`，从不记账；`Terrain.isTarget` 认静止或流动的水源（meta 0） | — |
| 模块核心 | `fluxecho:module_core` meta m / 码 64 + m | `BlockNexus` 控制器（§4.5） | 7（`BlockNexus` 的 `setLightLevel(0.5)`，同中枢和书库核心；CITY 附录写的 8 没照做） | 只收成品（`partOnly`），在中枢显化 | `module_core/<key>/front`、`front_on`（动画）、`side`、`top` |

- 生成：`java tools/CampusTextures.java`（样本座、池沿，0.12.0 加光梯 `liftSide/liftTop/liftGlow`，0.14.0 加画布 `canvas`）、`java tools/NexusTextures.java`（覆层锚点、模块核心，0.12.0 加 `module_core/dome_generator/` 和 `module_core/root/` 的四张——守护蓝的吊舱徽记、潮汐蓝的井徽记——和物品 `items/phase_alloy`、`items/resonant_glass`；0.14.0 加 `module_core/resonance_gate/` 和 `module_core/dual_foil_vault/` 各四张——青绿的门楼徽记 `gateEmblem`、羊皮纸色的方碑徽记 `vaultEmblem`），从仓库根目录跑；不要手画。
- 码只往后加（`Parts`：frame 0–15、deck 16–31（0.14.0 用到 28 画布）、fitting 32–47、48–52 老的、53 水、63 `AIR`、64–79 模块核心、80–95 `module_core2` 预留）；`BUILD_FX` 一个码一个字节（≤ 127）；`BuildFx`/`BuildScan` 的图标缓存 0.11.0 起按 128 个码开（0.10 是 64）。
- 构架 `LIGHT` 现在是 `{0,7,0,12,8,15,4,8,10,6}`、`BlockFrame.TYPES` 10、`full` 含 ANCHOR；配件 `FITTING_LIGHT` `{6,9,10,6,7,0,8,8,4,12}`、`FITTING_TYPES` 10、`FITTING_NAMES` 加 `socket`、`basin`、`lift`（0.11.0 是 9 种，光梯 0.12.0 加）；`BlockFitting.full` 含光梯（整块立方）；`isSideSolid`：踏板和池沿只有底面实心。
- 精确成本（`PartRecipesTest.theCitysPartsCost`，账本里再展开成原料）：样本座 = 半块墙板 + 半块光缝墙板 + 1/8 通量结晶（4 P + 4 L + 1 C 出 8 个）；池沿 = 半块地缝钢 + 1/8 钢板（4 T + 1 S 出 8 个）；覆层锚点 = 1 不锈钢板 + 1 环段 + 1/4 结晶（4 S + 4 R + 1 C 出 4 个）；光梯 = 4 光缝墙板 + 4 通量结晶 + 1 末影之眼（出 1 个，`PartRecipesTest.theLiftPadCosts`）；画布 = 1 墙板 + 1/8 张纸（8 P + 1 纸出 8 个；展开成原料是半块石砖、1/16 钢板、1/8 张纸，`PartRecipesTest.theCanvasCosts`）。
- 区块网格（`FittingRender`，同 campus.md §11：只读方块和 meta）：样本座的各块每个面都画（凹槽的上下面在方块里面，不能用整柱「顶面、底面贴着不透明方块就剔掉」的规则），发光层只在侧面；池沿用 `Neighbours.fitting` 找同高的池沿伸臂，臂的端头贴着下一块的臂处不发光；物品栏里样本座画整柱、池沿画成东西向的一段。
- 新方块守 `Builder.QUIET`：构架和配件本来就守；模块核心经 `BlockNexus.quiet()`。
- **还没做**（以后的批次）：浮空地基 FLOAT（frame 10 / 码 10，0.16.0）。画布 0.14.0 做了（上表）：地板方块的 `DECK_LIGHT` 加一个 0，`BlockDeck` 多了单元用的 `Use` 接口。附录 C 写好的光照数组到时候再长（构架的 `LIGHT` 0.12.0 没变）。
- **相位的材料**（0.12.0，BLUEPRINT 3.6，`MatterModule`；不是营造部件，研究花费和两座模块的核心要用）：**相位合金** `fluxecho:phase_alloy`（矿辞 `ingotPhaseAlloy`；GT 电力高炉：2 通量结晶 + 1 钛粉 + 1 回响晶（任意）→ 1，600 tick、1920 EU/t、炉温 2700 K，`COIL_HEAT`；只在 `defaultRecipes` 开着时注册，包在 try/catch 里）和**共鸣玻璃** `fluxecho:resonant_glass`（矿辞 `glassResonant`；在中枢显化，研究 `phase_2`：相位合金 ×1 + `ore:ingotBorosilicateGlass` ×4 → 4，400 tick，不压记录）。两种都是 `ItemMatter`，提示在 lang（`fluxecho.phase_alloy.tip`、`fluxecho.resonant_glass.tip`），贴图由 `NexusTextures` 生成（nexus.md §2）。

## 6. 建筑

每座建筑一小节：概念、场地、体量、单元、溶解、计数和校验和、阶段、任务的规模和用料、研究和显化、画面和预算。数字都是拿编译好的类算的（`BuildPlan` 的真实计划按 `PartRecipes.cost` 展开，`buildCostScale` 1），README 的表用同一份。

### 6.1 生态穹（`logic/EcoDomeShape`、`city/TileEcoDome`、`city/client/EcoDomeRender`；CITY §4.1，生成器 `b_hall.py` 的 `eco_dome()`）

- **概念**：BLUEPRINT 的《寂静奔跑》森林穹顶。像万神殿那样一座门廊块立在圆厅前，玻璃像邱园的棕榈温室和伊甸园项目，里面是滨海湾花园云雾林那种可以往上爬的种植山（这里是可以爬上去的浮岛）。
- **场地**：大厅位，偏好 2（中枢右边）→ 6 → 4（`EcoDomeShape.SITES`）；27×35×21（Y0…Y0+20，`WIDTH/DEPTH/HEIGHT`，`MID` 13）；控制器 (13, 2, 0)（`CTRL_X/Y/Z`）在门间柱里、控制台座上方、朝中枢。生成器分配 22 层但只画 y0–y20，所以蓝图 21 高（`GEN_HEIGHT` 22，类加载时检查顶层是空的）。
- **体量**：前面一座**门厅块**（x 2–24、z 0–11：墙 y1–11，壁柱每 4 格，首层窗 `|x−13|` 7–9 的 y2–4，高窗 y8–10，y12 檐口，平屋顶）；后面一个**鼓座**（半径 13.4 绕 (13, 21)：y1 深色墙裙、y2–5 玻璃，45° 线上八条亮肋，y7 檐口）托着**半球玻璃穹顶**（球心 (13, 7, 21)、半径 13.4：GLAZE 加八条亮经肋，y12 和 y16 两道亮纬环，y19–20 玻璃顶灯）。剪影：一座矮块后面一个 21 高的玻璃泡。
- **室内**：套件门 → 9 宽、两层通高的门厅（x 9–17、z 1–8），两个全息底座 (9,1,6)、(17,1,6)；两座镜像旋转楼梯（中柱 (5,3)、(21,3)，12 级，站高 1 → 7）上到门厅二楼（楼面 y6，`DECK_Y`），接**穹顶回廊**（y6 上 3 宽的环，半径 9.5–12.5，内侧一圈光带和栏杆）；两座**桥**（z 20–22）通到**中央浮岛**（`oct(4,5)` 绕树干，y6：基座边、栏杆、上面草）——浮在花园上方 5 格；**世界树**树干（导能柱 y1–16 在 (13,21)，`TRUNK_X/Z`；y17 晶核座）穿过浮岛；**绕树干的螺旋**（10 级，站高 7 → 12，只往一个方向转，是形状里唯一不镜像的格子）上到**树冠岛**（`oct(3,4)`，y11 `CROWN_Y`：草、栏杆，楼梯口留净空）。**地面花园**：树干周围的池（水在 d 1.5–3.6，池沿到 4.6）、种植床、半径 5–10 避开两条轴的草地（给玩家放自己的蜂箱和作物）；回廊和浮岛下面 5 格净空。
- **单元**：12 个**种植床**（种类 `planter`，lang `fluxecho.unit.planter`），每个 = 一排 3 个样本座 + 外面一圈 3×5 的池沿，整张床是一个单元，3 槽（蜜蜂、树苗、种子、蝴蝶；结构阶段任何物品）；回廊上两个（U-R、U-L）的池沿碰到鼓座檐口的那格留给墙（檐口带在玻璃下面没有缺口），所以是 13 格，其余 15 格。共 36 槽。顺序（= 槽号，永不改）：

| 单元 | 名 | 位置 | 站高（层） | 槽 |
|---|---|---|---|---|
| 0 | G-RB | 地面，右后对角（离树干 6、6） | 1 | 0–2 |
| 1 | G-RF | 地面，右前对角 | 1 | 3–5 |
| 2 | G-R | 地面，右侧（离树干 9） | 1 | 6–8 |
| 3 | G-LB | 地面，左后对角 | 1 | 9–11 |
| 4 | G-LF | 地面，左前对角 | 1 | 12–14 |
| 5 | G-L | 地面，左侧 | 1 | 15–17 |
| 6 | G-B | 地面，后面（离树干 9） | 1 | 18–20 |
| 7 | I-F | 浮岛前 | 7 | 21–23 |
| 8 | I-B | 浮岛后 | 7 | 24–26 |
| 9 | U-R | 回廊右（13 格） | 7 | 27–29 |
| 10 | U-L | 回廊左（13 格） | 7 | 30–32 |
| 11 | C | 树冠岛 | 12 | 33–35 |

  「右」是走进门的人的右手边（形状的 +x），「后」是离门远的那边（+z）。窗口平面图三层：高 1、7、12。
- **内境门位**（CITY §4.15，§1）：鼓座后墙正中 `x 12–14, y 1–4, z 34`（`NICHE_X0/X1/Y0/Y1/Z`、`inNiche`），正对着门：y1 是深色墙裙 `[`，y2–4 是两边的窗格 `/` 夹着中间 x13 的后亮肋 `!`——和旁边一样的方块（建造在 SHELL 阶段，SOFT 步骤），结构检查不看，以后在这里开内境的光门。y5 的覆层锚点和两边的玻璃、x11 和 x15 的墙都还是检查格。画面照样把它当墙：墙裙光线在下面连着，后面那条经肋贴着它走（`EcoDomeRender.shell` 用 `checkedTwin`），平面图上是墙、玻璃和壁柱。
- **门洞空气** 48；**溶解**：`DISSOLVES = "CZ"`、`PASSES = "Z"`——成型后树干（16 格导能柱）和晶核座都不画，交给渲染的光之树；树干保留碰撞（它穿过浮岛 y6 和树冠岛 y11 的楼面，又是螺旋梯的中柱，能穿过的话会留下一口掉进池子的井），只有在 y17、够不着的晶核座能穿过。
- **计数**：检查 2,777 · 软 775（其中门位 12）· 空气 48 · decor 14（雨棚）= **3,614 步**；对称，只有树干螺旋的 10 级例外（`asym`；结构检查按 `Blueprint.world` 精确映射，和 StructureLib 无旋转、无翻转的映射一致，`TileEcoDomeTest.theBlueprintMapsCellsAsStructureLibDoes`）。字符：`G` 720、`W` 550、`L` 443、`d` 282、`u` 172、`n` 111、`r` 104、`F` 102、`w` 92、`e` 70、`B` 36、`o` 36、`t` 17、`C` 16、`q` 14、`A` 7（鼓座亮肋的 y5）、`h` 2、`K`/`Z`/`~` 各 1；软 `.` 573、`,` 162、`=` 28、门位 `/` 6、`[` 3、`!` 3。整层校验和 `2037949574`（`EcoDomeShapeTest`；门位改成软格之前是 798826595，回廊种植床让出檐口之前是 3124826963）；行走测试走到 1,179 个状态，到得了眼睛高度的控制器和 12 个种植床，两座岛都有人站，能穿过的格子都够不着（`EcoDomeWalkTest`）。
- **阶段**（`EcoDomeShape.stage`/`fitRank`）：CLEAR（占地外扩 1 的 1,073 根柱子，Y0+1 到 Y0+23，平整）· FLOOR（场地地面、y0 的软地面和花园的草）· SHELL（地基、y1–7 的墙到鼓座檐口、门洞空气、门间柱和门楣）· DECK（y6 楼面：门厅二楼、回廊、桥、浮岛和它的草、栏杆、门厅楼梯）· UPPER（门厅块 y8–12）· ROOF（y8 以上的穹顶）· FIT（树干和晶核座 → 树冠岛 → 树干螺旋 → 12 个种植床各一组 → 池的池沿 → 池水 → 全息底座 → 覆层锚点）· DECOR（雨棚 + 种子装饰）· COMMISSION（生态穹核心）。池水在它的池沿之后放（M8 的偏差），浮岛的草在 DECK、树冠岛的草在 FIT。
- **任务的规模和用料**（真实的计划：`EcoDomeShape.plan` 的每一步按 `PartRecipes.cost` 展开，`buildCostScale` 1；拿编译好的类对 3 个园区种子 × 场地 2、4 算过，README 的数字就是这个）：
  - 步数 3,920–3,936：FLOOR 981（场地地面约 296 + y0 的软地面 573 + 花园的草 112；另 50 格草在两座岛上，水 28 格在 FIT）· SHELL 832 · DECK 537 · UPPER 432 · ROOF 800 · FIT 313 · DECOR 24–40（雨棚 14 加种子装饰，按控制器位置变）· COMMISSION 1；清理 1,073 根柱子。按每 tick 2 块放约 1.6 分钟（另加阶段节奏）。
  - 原料：玻璃 726、石头 ≈650、石砖 ≈635、钢板 ≈267、荧石粉 ≈170、不锈钢板 152、钢杆 ≈82、通量结晶 ≈46、玻璃板 45、煤粉 ≈43、不锈钢杆 25、碎屑 15、回响晶 13；另要两件成品：**晶核座 1**（树梢，要 GT HV 外壳，`partOnly`）和**生态穹核心 1**。
  - EU：放置约 0.96 M（每块 256；草、水和门洞空气 238 步各 64），清理和平整另算。
  - CITY §4.1 和 `report.py` 的账单（石砖 674、石头 517……）只算形状、不算场地地面，比真实的少；以建造页的材料单为准。
- **研究** `eco_dome` 生态穹（ECHO，星图 (226, 38)，相位 1；前置 `inner_ring`、`echo_crystal`；算力 16,000；回响晶 ×4、`ore:treeSapling` ×16、`ore:blockGlass` ×32）——`/fluxecho research all` 在 0.11.0 给 9 项（0.12.0 起 12 项，加上 `phase_2`、`dome`、`tidal_well`）。**显化** `eco_dome_core`（同一研究；回响晶 ×4、`ore:treeSapling` ×8、小麦种子 ×16、`ore:circuitHV` ×2、`ore:blockGlass` ×16；1200 tick；不压记录）→ 生态穹核心（`fluxecho:module_core` meta 0）。**维持** `moduleUpkeepEuPerTick` 128 EU/t。
- **画面**（`EcoDomeRender`，在 `CityRender` 之上；CITY §4.1 Visuals）：
  - **成型**（不论停靠）：树干和晶核座变成**光之树**——3 股光（每股 24 段）绕着柔和的芯从池面盘到晶核座，一道脉冲往上爬；树冠下 6 根枝；顶灯下 140 片发光树叶慢慢摇；16 片光叶飘落，落在树冠岛、浮岛或花园上；顶灯和树冠的光晕。没停靠时亮度 0.3、停靠没电 0.6。NEAR 外只画一根光芯和树冠的一团光晕（15 quads）。
  - **停靠**：穹顶的亮肋（八条经肋从鼓座到顶灯，y12、y16 两道纬环）以模块的绿色**呼吸**，一道光波往上升；肋贴着真实的玻璃台阶走，相机在外面画在玻璃外侧，在鼓座、穹顶或门厅里画在内侧。MID 每隔一段画一段、没有光晕、环 16 段；FAR 只有经肋，每段四层长，没有环（≤ 32）。
  - **停靠、NEAR**：种植床的**全息**——每个有样本的样本座上方一个光锥、一团光和旋转的物品图；种子（`IPlantable`）显示它的作物按生长阶段循环，蜜蜂（`ModuleLook.beeName`）多一个绕样本座转的光点；每个样本座最多 6 quads。
  - **有电、NEAR**：40 颗花粉从种植床和花园升起；8 只蝴蝶（每只 3 quads）在地面、浮岛和回廊之间飘；池面 6 圈涟漪（每圈 16 段），画在静止水面（y1 + 8/9）上方 0.035（光影包带深度和波浪画的水盖不住它），每圈从一格水的中心开始、最多扩到这格的余量（约 0.5，`ModuleLook.water`），不会扩到池沿或树干周围。
  - 每部分（光之树、呼吸肋、全息、花粉、蝴蝶、涟漪）单独失败；表（贴着玻璃的肋、种植床、花园）从 `EcoDomeShape` 一次算好。
- **修复不动花园**：花园是玩家的（开阔的草地就是给他们放蜂箱和作物的）。修复任务里草、泥土、水这些 SOFT 地面步骤只在格子空着时放（`TerrainRule.forRepairGround`：`forBuild` 是 PLACE 就 PLACE，否则 ALREADY；`Builder.verdict` 的第五个参数 `repair`）；耕地、蜂箱下的泥土、玩家放的方块都留着。第一次建造照旧（整片清空规则下照样换掉），结构格和地砖照旧。

### 6.2 回响穹顶发生器（`logic/DomeGeneratorShape`、`logic/DomeLook`、`city/TileDomeGenerator`、`city/client/DomeGeneratorRender`；CITY §4.8，生成器 `b_sky.py` 的 `dome_pod()`、`dome_legs()`）

- **概念**：天顶上将撑起全队穹顶的吊舱。一只碟形吊舱悬在中枢上空 40 格，先**系泊**在六条从广场升起的弧形腿上（相位 II、III），相位 IV 的释放任务（0.16.0）拆掉腿让它浮空。没有门，中枢上的光梯直达舱内。
- **场地**：天顶 8（`SITES`），每个园区一个，不占内环（§4.12）。吊舱 21×21×10（`SIZE`、`HEIGHT`，`MID` 10），八角形 `oct(10,14)`（`POD_R/POD_K` = `CampusPlan.ZENITH_R/K`），舱底在 Y0+`FLOOR_UP`(40)。**两个坐标系**：模块的坐标是吊舱自己的（x 横向 = 10 − R，y 从舱底往上，z 从吊舱正面 = 10 − A），渲染和窗口都按它读；蓝图是腿和吊舱合起来的盒子，29 宽（R 14..−14）、49 高（Y0+1..Y0+49）、25 深（A 12..−12，`BP_WIDTH/HEIGHT/DEPTH`），吊舱的 (0,0,0) 在蓝图的 `OFFSET` {4, 39, 2}（`CityBuild` 的偏移，§4.3）。控制器 (10, 2, 6)（`CTRL_X/Y/Z`）= 园区 (A 4, R 0, Y0+42)，在光梯台前 4 格的控制台座上，朝 +A（大门）；光梯台在吊舱中心 (10, 0, 10) = (A 0, R 0, Y0+40)。
- **体量**：舱底（厚边是模块地基 `F`、里面是软的地砖 `.`、中心光梯台 `^`）；y1–y5 的环形墙（y1 深色墙裙 `w`，y2–4 玻璃 `G`、八条线上亮壁柱 `L`，y5 墙板）；y6 檐口 `n` 和天花板；阶梯碟形屋顶（y7 `oct(7,9)` 的环、y8 `oct(5,6)`、y9 玻璃顶 `oct(2,3)`）；屋顶上六个**发射节点**（导能柱 `C`，y7，离轴 (±7, ±4)、(0, ±8)，`NODE_Y`、`nodes()`）。室内是一圈环厅，围着中间的光梯井。剪影：六条腿托着一只碟。
- **腿**（`legs()`，`LEG_CELLS` 234）：构架支柱 `P`，从广场 Y0+1 到舱沿下面 Y0+39（`LEG_TOP` 39），角度 30 + 60k 度，半径 `9 + 5 (1 − h/38)^1.6`（`LEG_HEAD` 9、`LEG_FOOT` 14、`LEG_CURVE` 1.6，h = 0..38）；R ≥ 0 一侧的三条用 `StrictMath` 算、按生成器的 `round` 半数取偶（`Math.rint`），另一侧镜像（生成器把顶上那格两侧取整得不一样，§1.5）。脚在 (A, R) = (12, ±7)、(0, ±14)、(−12, ±7)（`feet()`）。腿避开广场的光井、补给口和回收箱。腿占 40 根柱子（`legColumns()`：每柱最低和最高的腿格），清理、keep-out 和保护都按柱、按高度（§4.12）。
- **两个形状**（`TileDomeGenerator.shapes()`）：`moored`（`MOORED_BP`，吊舱加腿，检查 1,261 格）在先，`floating`（`FLOATING_BP`，同一个盒子、只有吊舱，检查 1,027 格）只在它下面那座已成型中枢（`under()`：控制器正好是这座中枢的天顶控制器格，停没停靠都认）长到相位 IV（`FLOAT_PHASE`）起才试——所以相位 II、III 腿被拆了吊舱就散架，不会浮起来。NEI 预览相位 IV 起画浮空的（`previewPhase`）。
- **单元**：7 个**守护台**（种类 `ward`，lang `fluxecho.unit.ward`「守护台」），每个 = y1 的控制台座 `K` + 上面 y2 的样本座 `o`，1 槽（0.19.0 起每个放一种穹顶功能的核心；结构阶段任何物品），共 7 槽。顺序（= 槽号，永不改），位置是离光梯台的 (dx, dz)（同 §6.1：+x 是右、+z 是后）：

| 单元 | 名 | 位置 | 槽 |
|---|---|---|---|
| 0 | ward-1 | (6, 0) 右 | 0 |
| 1 | ward-2 | (−6, 0) 左 | 1 |
| 2 | ward-3 | (0, 6) 后 | 2 |
| 3 | ward-4 | (4, 4) 右后 | 3 |
| 4 | ward-5 | (−4, 4) 左后 | 4 |
| 5 | ward-6 | (4, −4) 右前 | 5 |
| 6 | ward-7 | (−4, −4) 左前 | 6 |

  守护台的控制台座属于单元，点它是用单元（`consoleAt` 不算它，§4.6）；控制器下面那个才开窗口。窗口平面图一层（站高 1）。
- **不检查的格子**（CITY §4.8，§1.5）：八个门洞（四个平面和四个斜面各 3 宽，y1–y4，`door()`）先用玻璃 `/` 封着（96 格，等天环的栈桥来接），光梯井穿过屋顶的那一段（y6–y9，`shaftCap()`）先盖着（y6–8 是墙板 `{`、y9 是玻璃 `/`，等轨道锚的任务打开）——都是软格，和旁边一样的方块，结构检查不看。所以吊舱现在只能坐光梯进。`RAW` 是生成器原样的格子（门洞和井口空着），`SHAPE` 是建的（加上这 100 格）。
- **溶解**：`DISSOLVES = PASSES = "PC"`——成型后腿和发射节点都不画（交给渲染的弧肋和电弧），而且能穿过（§4.6 的例外：和中枢的 `P`/`C` 一样，腿立在广场上人碰得到，穿过去就是设计；它们不托着任何人站的地面）。`TileDomeGeneratorTest.theLegsAndNodesDissolveAndLetPlayersThrough`。
- **计数**：吊舱（生成器原样）`W` 540、`.` 276、`G` 172、`F` 80、`n` 80、`L` 76、`w` 56、`K` 8、`o` 7、`C` 6、`^` 1、`~` 1 = 检查 1,027、软 276；建的再加门洞玻璃 96 和井口 4 = 软 376，吊舱 1,403 步，加腿 234 = **1,637 步**。校验和：`dome_pod()` 整层 `4099585315`、前三条腿（30、90、150 度，`a,r,dy;`）`2025965872`（`DomeGeneratorShapeTest`）。行走从光梯台出发：生成器原样 293 个状态（环厅和门洞），建的 269 个（玻璃封住的门洞成了墙）；到得了控制器和 7 个守护台。
- **阶段**（`Z_CLEAR … Z_COMMISSION` 共 7 段，lang `build.stage.z0–z6`：清理支腿和吊舱的位置、六条支腿、吊舱舱底、外壳、屋顶、控制台和守护台、落成；`BuildPlan.zenith`）：CLEAR = 40 根腿柱、每根只清腿占的那段高度（广场的地砖和腿下的补给口、回收箱都留着），加吊舱的盒子外扩 `POD_MARGIN` 1（23×23 = 529 根）从 Y0+40 到 Y0+`CLEAR_TOP` 52，不平整，从中枢往外；LEGS = 234 格腿，六条一起、按层从下往上；FLOOR = 舱底，从轴往外；SHELL = y1–y5 的墙和门洞的玻璃，从下往上；ROOF = 檐口、天花板、碟顶、井口和发射节点，从下往上；FIT = 控制器的控制台座，再按单元顺序每个守护台一组（控制台座 + 样本座）；COMMISSION = 控制器（回响穹顶发生器核心）。每个检查格按 `Blueprint.world` 摆在 `moored` 蓝图里，所以 HARD 步骤就是这个形状的结构检查看的格子（`ZenithPlanTest`：4 个朝向，HARD 1,261）。
- **任务的规模和用料**：
  - 步数 1,637：LEGS 234 · FLOOR 357 · SHELL 400 · ROOF 630 · FIT 15 · COMMISSION 1；清理 569 根柱子（腿 40 + 吊舱 529）、最多 7,111 格。没有种子装饰，四个朝向一样。
  - 原料：石砖 ≈390、不锈钢杆 366、玻璃 269、石头 138、钢板 ≈97、不锈钢板 80、碎屑 ≈67、荧石粉 ≈45、通量结晶 ≈22、玻璃板 12、回响晶 10、煤粉 7、末影之眼 1（光梯）；另要**回响穹顶发生器核心 1**（码 71，`partOnly`）。
  - EU：放置 1,637 × 256 = 419,072（约 0.42 M）；清理每格 `buildEuPerClear`，最多 7,111 格（空气不花）。
  - 节奏的巧合：`BuildPace.modulePace` 按阶段号给 `M_FLOOR`（1）× 1.5，天顶的阶段 1 是 LEGS，所以腿按 1.5 倍的节奏放（§9）。
- **研究** `dome` 回响穹顶（GUARD，星图 (180, 168)，相位 2；前置 `phase_2`；算力 30,000；`ore:plateTitanium` ×16、`ore:circuitEV` ×4、`ore:ingotPhaseAlloy` ×8、`ore:glassResonant` ×16、`ore:gemEchoCrystal` ×4、末影之眼 ×8）。**显化** `dome_generator_core`（同一研究；回响晶 ×8、末影之眼 ×8、相位合金 ×8、`ore:circuitEV` ×2、共鸣玻璃 ×8；1600 tick；不压记录）→ 回响穹顶发生器核心（`fluxecho:module_core` meta 7）。TE id `fluxecho:dome_generator`；主题色守护蓝 `0x7DB8FF`。**维持** `moduleUpkeepEuPerTick` 128 EU/t；穹顶的功能 0.19.0 起才有，现在停靠有电只是画面全亮。
- **画面**（`DomeGeneratorRender`，在 `CityRender` 之上；位置都是 `DomeLook` 的纯算术；CITY §4.8 Visuals）：
  - **成型扫光**：每条腿随支柱消失升起一圈守护蓝的光环，一片光穿过吊舱（`sweep`）。
  - **成型、系泊**：每条腿是一根渐细的钢弧肋，沿着腿格取整前的曲线（`DomeLook.legPoint`，`DomeLookTest.theRibsRunThroughTheLegCells`：每格离肋 ≤ √0.5），切成 `LEG_SEGMENTS` 13 段，关节一圈光、一道光波沿六条腿爬进吊舱，内侧一条光线；脚宽 `FOOT_HALF` 0.55、头宽 `HEAD_HALF` 0.3。腿自己量到腿盒子的距离定档位（NEAR 13 段、MID 4 段没有关节环、FAR 每条 `FAR_LEG_LINES` 2 条线），FAR 按地标的淡出算、和超出范围后的地标接上。发射节点是小晶体，晶体浮在屋顶上方（`EMITTER_Y` 12.5，Y0+52.5），六个节点各一缕光连上去。
  - **停靠**（闲置的样子）：六道**电弧**从节点射向穹顶（`arcEnd`、`arcPoint`，往上弓）；**穹顶**半径 `DomeLook.shellRadius(中枢相位)`（64、104、128、160 对应 II–V），几乎看不见：只有它碰到地面的一圈柔光带和顶上几格闪烁的六边形（`apexCells`、`shimmer`）。没电时穹顶和电弧暗淡的琥珀色、慢慢闪；有电时电弧、晶体和关节全亮。电弧 FAR 不画。
  - **地标**（`landmark`，超出模块的绘制范围后由 `NexusRender` 调，和 `draw` 二选一）：系泊的腿的远处线条（任何停靠状态都画——腿的支柱在任何距离都是隐藏的，否则相位 IV 之前吊舱看着像浮着），停靠时再画穹顶和晶体的一团光；都在最后 `LANDMARK_FADE` 64 格淡出，画到 `LANDMARK` 512。
  - **预算**（每帧，在 `CityRender` 之上）：腿 NEAR 约 700、MID 约 120、FAR 36（`farLegQuads`）；节点和晶体 64 加 8 个光点；电弧 6 × 14 段，NEAR 带光晕 168、MID 一半、FAR 没有；穹顶 `DomeLook.shellQuads`：半径 64 最多 280、104 和 128 最多 290、160 最多 352（地面分段 192 格内 64、320 格内 56、更远 32；六边形 320 格内且半径 64 时两圈、否则一圈；线按每段 ≤ `PIECE` 8 格切，`PIECE` 必须等于 `CityFrame` 的）。加上墙裙、名牌和守护台的样本，近 < 2,000、中 < 300；地标（穹顶、远处的腿、晶体的光）任何距离任何半径 ≤ `DomeLook.BUDGET` 400（`landmarkQuads`：半径 64 最多 317、104 和 128 最多 327、160 最多 389；`DomeLookTest.theDomeKeepsItsBudget`、`theCountIsTheDrawing`）。
  - 每部分（腿肋、发射器、电弧、穹顶、成型扫光）单独失败；朝相机的面才画，不写深度。

### 6.3 潮汐井（`logic/RootShape`、`logic/RootLook`、`logic/Tide`、`city/TileRoot`、`city/client/RootRender`；CITY §4.9，生成器 `b_root.py` 的 `tidal_well()`）

- **概念**：中枢下面的井，通量层的潮汐从这里涌上来。根是一个往下长的模块（`root`），三个形状：这一版建第一个**潮汐井**（`WELL` "well"）；**深根**（`DEEP` "root"，相位 IV）和**地脉核**（`CORE` "core"，UV）只移植成数据（`RootShape.deepRoot()` 25×39×25、检查 5,028、校验和 `2817673059`；`leyCore()` 25×57×25、检查 5,826、校验和 `21006675`），以后的批次建；那时它们的单元要把井的 8 个汲口排在最前（槽号是井的），虽然生成器先列出深根的槽位。控制器一直在小岛上。
- **场地**：根 9（`SITES`），每个园区一个，不占内环，地面上什么都不占（§4.12）。25×25×26（`SIZE`、`HEIGHT` = `DEPTH` 26，`MID` 12）：井筒 `oct(11,15)`（`WELL_R/WELL_K` = `CampusPlan.ROOT_R/K`），井盖 y25 = Y0−1（`LID_Y`，在广场的地砖和中枢台下面，中心留空给光梯），井底板 y0 = Y0−26（`SLAB_Y`）。坐标就是模块的蓝图：x 横向 = 12 − R，y 从井底板往上（Y0 − 26 + y），z 从正面 = 12 − A。控制器 (12, 3, 10)（`CTRL_X/Y/Z`）= 园区 (A 2, R 0, Y0−23)，在光梯台前 2 格的控制台座上、眼睛高度，朝 +A；光梯台在小岛中心 (12, 1, 12)（`PAD_X/Y/Z`）= Y0−25，人站在 Y0−24。**深度检查**：井底板不能低于 y 6（`LOWEST_SLAB`，基岩到 y 4），所以中枢的底座层要 Y0 ≥ 32（`LOWEST_Y0`），否则 `build.module.why.depth`（§4.12）。
- **体量**：井底板和井盖（边是模块地基 `F`、里面 `B`），y1–y24 的井壁（一圈 88 格）：八条线上亮肋 `L`，三道**通量层带**（y6/12/18 深色墙裙 `w`、上面 y7/13/19 檐口 `n`，`BANDS`：表层、记忆层、深层），其余墙板。地面层 y1（`FLOOR_Y`，步道）：中心**小岛** `oct(2,3)`（`B`，光梯台、控制器），四座 3 宽的**桥**沿两条轴，**潮汐池**（水 `=`，八角距离 ≤ 7）、池沿 `u`（距离 8）、靠墙的步道 `d`。池上方三道**潮汐环**（构架悬浮环段 `R`，y6、y11、y16，内半径 2.5、3.5、4.5，各一格宽，`RING_Y/RING_R`）。一条**螺旋坡道**（软：螺旋不能镜像）在八角距离 9–10 的带上，从 200°（`RAMP_START`）顺时针爬 0.97 圈（`RAMP_TURN`），半格一级，从步道上到井盖下 4 格（站高 y21，Y0−5），坡道内侧距离 8 上有栏杆（站高 4 起）。
- **单元**：8 个**潮汐汲口**（种类 `tap`，lang `fluxecho.unit.tap`「潮汐汲口」），每个 = 嵌在池沿里的一个样本座，1 槽（0.19.0 起放潮汐动力的燃料；结构阶段任何物品），共 8 槽。顺序（= 槽号，永不改）`tap-1 … tap-8`：第 t 个在方位 22.5 + 45(t−1) 度（两座桥之间）上离它最近的池沿格。汲口和步道一样高，人站在上面一层，所以窗口平面图画上面一层（`ModuleMap.flush`，§4.9）。
- **溶解**：`DISSOLVES = PASSES = "R"`——成型后三道环交给渲染的潮汐环，能穿过；照规则，它们悬在池上、没有人站得到（`RootShapeTest.theRingsAreOutOfReach`）。
- **计数**：`W` 1,440、`B` 677、`F` 176、`L` 144、`n` 264、`w` 264、`d` 196、`=` 84、`R` 76、`{` 64、`}` 54、`|` 36、`u` 20、`o` 8、`K`/`^`/`~`/`-` 各 1 = 检查 3,268 · 软 238（坡道和栏杆 154、水 84）· 空气 1（井盖中心）= **3,507 步**；校验和 `996641253`（`RootShapeTest`）。行走从小岛的光梯台出发 414 个状态，到得了控制器和 8 个汲口，坡道顶在 y21。
- **阶段**（`R_SINK … R_COMMISSION` 共 5 段，lang `build.stage.r0–r4`：沉井、排水、井底、潮汐环坡道和汲口、落成；`BuildPlan.root`，沉箱见 §4.12）：SINK = 从井盖往下每层两个带——先是这层的壳（井盖带它空着的中心、一圈井壁、或井底板），再掏这层里面（`DIG`）；DRAIN = 里面每一格从上往下再过一遍（`DRAIN`）；FLOOR = 地面层的小岛和光梯台、桥、池沿、步道，从轴往外；FIT = 控制器的控制台座、三道潮汐环、螺旋坡道和栏杆（软）、8 个汲口各一组（按单元顺序），池水最后（池沿完整之后）；COMMISSION = 控制器（潮汐井核心）。**没有清场柱**：沉箱正好掏它的里面，计划里没有一格碰到底座层 Y0。HARD 步骤就是结构检查看的格子（`RootPlanTest`，4 个朝向）。
- **任务的规模和用料**：
  - 步数 19,299：SINK 10,842（井盖 417、24 层井壁各 88、井底板 417、掏 7,896）· DRAIN 7,896 · FLOOR 237 · FIT 323（控制台座 1、环 76、坡道和栏杆 154、汲口 8、水 84）· COMMISSION 1。井里面 24 层 × 329 格 = 7,896 格，掏一遍、抽一遍，所以计划比形状多 15,792 步（`DIG`/`DRAIN`，不算进剩余时间）；SINK 有 50 个带（49 个带的起点）。
  - 原料：石砖 ≈1,451、不锈钢板 ≈600、钢板 ≈149、荧石粉 ≈111、石头 103、碎屑 ≈96、煤粉 ≈34、钢杆 27、通量结晶 ≈25、回响晶 22、玻璃板 ≈5、不锈钢杆 ≈1、末影之眼 1（光梯）；另要**潮汐井核心 1**（码 72，`partOnly`）；水 84 格只花 EU。
  - EU：放置 881,472（约 0.88 M：3,422 步各 256，水 84 和井盖中心的空气各 64）；掏每格 `buildEuPerClear`，最多 7,896 格（约 0.51 M，空着的格子不花）；排水只有真有液体的格子花。
- **研究** `tidal_well` 潮汐井（ENERGY，星图 (92, 160)，相位 2；前置 `phase_2`、`upkeep_1`；算力 30,000；`ore:plateTitanium` ×16、`ore:circuitEV` ×4、`ore:ingotPhaseAlloy` ×8、`ore:glassResonant` ×8、`ore:gemFluxCrystal` ×16）。**显化** `root_core`（同一研究；回响晶 ×8、相位合金 ×8、共鸣玻璃 ×4、`ore:plateTitanium` ×16、`ore:circuitEV` ×2；1600 tick；不压记录）→ 潮汐井核心（`fluxecho:module_core` meta 8）。TE id `fluxecho:root`；主题色潮汐蓝 `0x3FC8E8`。**维持** 128 EU/t；潮汐和汲口的作用 0.19.0 起才有。
- **潮汐**（`logic/Tide`，BLUEPRINT 3.11）：只按世界时间（`World.getWorldTime()`，`/time set` 会动它、关了昼夜循环就停）算的曲线，每个客户端每帧一样：一天涨落一次，午夜（`FULL` 18000）最高、正午最低；幅度随月相（`MOON` 8 天一轮：满月大潮 ±`SPRING` 20%、新月小潮 ±`NEAP` 5%，之间按比例），一天平均正好是平均值；换月相在日出（曲线过平均值的地方），不会跳。潮汐井吃两倍（`WELL` 2：大潮 ±40%）。`level`（0.8–1.2）、`norm`（−1..1，按大潮归一）。`TideTest`。
- **画面**（`RootRender`，位置是 `RootLook` 的；CITY §4.9 Visuals）：
  - **成型**：三道环变成画出来的钢环，随潮汐起落（大潮顶 `RISE` 1.25 格，上面的环晚 `LAG` 400 tick 跟上），一边转（`SPIN` 0.004 弧度/tick，潮汐再让它摆 `SWAY` 0.8，中间那道反着转），每道环一条光缝，光点沿着走；永远不低到碰到小岛或桥上站着的人的头（`HEAD` 1.8 + `CLEAR` 0.5）、不进坡道（`RootLookTest.theRingsStayClearOfThePeople`）。
  - **停靠**：八条亮肋的光往上涌（`ribs`、`ribRuns`：被通量层带截断）；三道通量层带各自的颜色（表层 = 井的颜色、记忆层偏紫、深层偏深根的绿 `0x5FD3A8`）；池面发光，潮越高越亮。从地面看：中枢台周围、井上方广场的格栅带（`RootLook.grate`）随潮汐发光，潮高过 `CREST_FROM` 0.75 时环的波峰像光刃一样从格栅里透出来（最高 `CREST_HIGH` 0.9）。
  - **有电**：池面焦散（两组交叉的摇曳光线），画在静止水面上方 0.035（光影包的水盖不住）。
  - **预算**（每帧，在 `CityRender` 之上）：NEAR 环最多 4 面 × 8 弧 × 3 段（只画朝相机的面，约一半）加光缝和光点（三道约 290）、肋每格一条线一层光晕（约 300）、带每面一条线（约 230）、池每格水一团光两条线（约 330）、地面每格格栅一团光和 16 道光刃（`BLADES`，各一条芯一层光晕，32 quads）；MID 每弧一段、没有光缝，肋每段一条线，没有带，池只有光（约 200）；FAR 环画成线（48）。加上名牌和汲口的样本，近 < 2,000、中 < 300、远 < 64。
  - 每部分（潮汐环、上涌的肋、通量层带、水池、格栅光）单独失败。

### 6.4 秘法庭（`logic/ArcaneCourtShape`、`logic/ArcaneLook`、`city/TileArcaneCourt`、`city/client/ArcaneCourtRender`；CITY §4.3，生成器 `b_pav.py` 的 `arcane_court()`）

- **概念**：BLUEPRINT 的《降临》墨迹文字环绕着一座塔；圣礼拜堂的竖向感；佛罗伦萨洗礼堂那样的八角平面，上面一座陡峭的尖塔。
- **场地**：对角位，偏好 7（大门左前）→ 1 → 5 → 3（`SITES`）；25×25×33（Y0…Y0+32，`WIDTH/DEPTH/HEIGHT`，`MID` 12），外形 `oct(12,16)`；控制器 (12, 2, 12)（`CTRL_X/Y/Z`）在中心祭坛的控制台座上、眼睛高度 = 园区 (±36, Y0+2, ±36)（§4.13）。最高的亭子：Y0 ≤ 223（`noRoom`，§4.13）。
- **体量**：八角大厅的墙 y1–12（`ShapeKit.shell`：平面上每 3 格一根亮壁柱，壁柱之间 y3–10 是细长的窗，门两边亮门框，y5 光檐门楣），y12 檐口 `n`（`WALL_TOP`）。**尖塔**（`SPIRE_FROM` 13）：八角环从半径 12 每两层收一步到 8（y13–22），再每层收一步到 3（y23–27），y28 玻璃灯笼环（`LANTERN_Y`）、y29 灯笼顶 `oct(1,1)` 的墙板加亮边（`CAP_Y`），y30–32 塔尖（导能柱 y30–31、晶核座 y32，`FINIAL_FROM/TO`）；0/45 度八条线上是亮肋，y16 和 y20 两道玻璃带。尖塔是空心的：从祭坛往上看 30 格。覆层锚点 8 个（尖塔肋上的 y13）。剪影：矮八角厅上一根 33 高的尖塔。
- **室内**：四个切角门沿对角线走到中心的**祭坛**（控制台座 + 控制器），祭坛在**注魔圈**里（离祭坛约 4 格的 8 个样本座 (±4, 0)、(0, ±4)、(±3, ±3)，y1；斜的四个按 `onorm` 是 5）；四个平面上各一个**坩埚龛**（3×3 池沿围着一个样本座，中心在半径 8）；八根**要素柱**靠着平面的墙（半径 11、每面中线两边 ±4：y1 底座、y2–4 三个样本座、y5 顶檐，面朝中心）；八张**研究台**（控制台座上一个样本座）在 (±7, ±4)、(±4, ±7)。地面：八角半径 6 的软亮符文环和它的辐条（`+`）。对角的路都空着，龛之间留着地方给玩家放自己的研究台和祭坛。
- **单元**：21 个、44 槽。顺序（= 槽号，永不改；位置是离祭坛的 (dx, dz)，+x 右、+z 后，同 §6.1）：

| 单元 | 名 | 种类（lang） | 位置 | 槽 |
|---|---|---|---|---|
| 0 | infusion | `infusion` 注魔圈 | 祭坛周围 8 个样本座（半径 4） | 0–7 |
| 1–4 | cauldron-1…4 | `cauldron` 坩埚 | 前 (0, −8)、后 (0, 8)、左 (−8, 0)、右 (8, 0)；各 3×3 池沿 + 样本座 | 8–11 |
| 5–12 | aspect-1…8 | `pillar` 要素柱 | 前墙 (−4, −11)、(4, −11)，后墙 (−4, 11)、(4, 11)，左墙 (−11, −4)、(−11, 4)，右墙 (11, −4)、(11, 4)；底座 + 3 个样本座 + 顶檐，面朝中心 | 12–35（每根 3） |
| 13–20 | desk-1…8 | `desk` 研究台 | (7, 4)、(−7, 4)、(7, −4)、(−7, −4)、(4, 7)、(−4, 7)、(4, −7)、(−4, −7)；控制台座 + 样本座 | 36–43 |

  研究台的控制台座属于单元，点它是用单元（`consoleAt` 不算，§4.6），只有祭坛那个开窗口。窗口平面图一层（站高 1）。
- **内境门位**：后面那个平面的正中 `x 11–13, y 1–4, z 24`（`ShapeKit.backNiche`，四座亭子都一样，§1）：y1 深色墙裙 `[`、中间 x12 的亮壁柱 `!`、两边 y2 墙板 `{`、y3–4 窗格 `/`。
- **门洞空气** 160（四个切角门）；**溶解**：`DISSOLVES = PASSES = "CZ"`——塔尖（两格导能柱和晶核座）成型后交给渲染的紫色晶体；照规则，它们在 30 格高的灯笼顶上，没有人站得到（`ArcaneCourtWalkTest.theCellsPlayersPassThroughAreOutOfReach`、`TileArcaneCourtTest.theFinialDissolvesAndLetsPlayersThrough`）。
- **计数**：`W` 931、`L` 389、`G` 328、`n` 136、`F` 96、`w` 65、`o` 44、`u` 32、`K` 9、`A` 8、`c` 8、`p` 8、`C` 2、`Z` 1、`~` 1 = 检查 2,058 · 软 397（`.` 304、`+` 81、门位 `[` 3、`!` 3、`{` 2、`/` 4）· 空气 160 = **2,615 步**；镜像对称（门位是软格）；整层校验和 `1506882503`（`ArcaneCourtShapeTest`）。行走从四个门外出发（`ShapeKit.pavilionStarts`）712 个状态，到得了眼睛高度的控制器和 21 个单元，每个门都走得过（`ArcaneCourtWalkTest`）。
- **阶段**（`ArcaneCourtShape.stage`/`fitRank`；DECK、UPPER 空着）：CLEAR（585 根柱子到 Y0+35）· FLOOR（场地地面 181 + y0 的软地面和符文环 385）· SHELL（地基、y1–12 的墙和檐口、门洞空气和门楣、门位）· ROOF（y13 起：尖塔一圈圈往上、灯笼、灯笼顶）· FIT（21 个单元各一组 → 祭坛的控制台座 → 塔尖）· DECOR（种子装饰）· COMMISSION（秘法庭核心）。
- **任务的规模和用料**（同 §6.1 的算法，3 个园区种子 × 4 个对角位 × 4 个朝向算过）：
  - 步数 2,799–2,805：FLOOR 566 · SHELL 1,308 · ROOF 817 · FIT 104 · DECOR 3/8/9（附属，§4.13）· COMMISSION 1；清理 585 根柱子、20,475 格。
  - 原料（不算附属）：石砖 ≈777、玻璃 332、石头 ≈286、钢板 ≈176、荧石粉 ≈170、不锈钢板 ≈118、煤粉 ≈29、通量结晶 ≈19、回响晶 12、不锈钢杆 ≈10、碎屑 ≈8、玻璃板 4；另要两件成品：**晶核座 1**（塔尖，`partOnly`）和**秘法庭核心 1**。
  - EU：放置 685,056（约 0.69 M：2,636 步各 256，门洞空气 160 步各 64），附属每步另加；清理和平整另算。CITY §4.3 和 `report.py` 的账单（约 0.64 M EU、石砖 821……）只算形状、不算场地地面；以建造页的材料单为准。
- **研究** `arcane_court` 秘法庭（ECHO，星图 (290, 110)，相位 2；前置 `phase_2`、`echo_crystal`；算力 30,000；`ore:plateTitanium` ×16、`ore:circuitEV` ×4、`ore:ingotPhaseAlloy` ×8、附魔台 ×2）。**显化** `arcane_court_core`（同一研究；回响晶 ×8、相位合金 ×8、共鸣玻璃 ×4、`ore:circuitEV` ×2、附魔台 ×1；1600 tick；不压记录）→ 秘法庭核心（`fluxecho:module_core` meta 2，码 66）。TE id `fluxecho:arcane_court`；主题色紫 `0xC77DFF`。**维持** 128 EU/t；功能 0.19.0 起。
- **画面**（`ArcaneCourtRender`，位置和动作是 `ArcaneLook` 的纯算术；CITY §4.3 Visuals）：
  - **成型**（不论停靠）：塔尖变成灯笼顶上方转着的紫色晶体（`CRYSTAL_Y` 31.5，上下浮 0.1、160 tick 一次，400 tick 转一圈），里面一颗小晶体反着转。没停靠时亮度 0.3、停靠没电 0.6（四座亭子都这样）。
  - **停靠**：三道**黑墨环**绕着尖塔，各自的高度、倾角和速度（`RING_Y` 22/25.5/29、半径 11.5/9/6、倾 10/12/22 度、自转 1600/−1100/800 tick、摆 6400/−4400/3200 tick），每道三个提笔的缺口，笔画在缺口前收细（`CityFrame.taper`），墨的半宽 0.24/0.2/0.16（`INK_HALF`）；墨是 alpha 混合的（黑色加不上去），近处后面衬一层紫色光晕；三道环离尖塔都有余量（`ArcaneLookTest.theInkRingsOrbitClearOfTheSpire`）。**停靠、NEAR** 的室内：符文环（半径 5.8、16 个符文、2400 tick 转一圈）在地面的亮环上转；每根要素柱冒 5 颗它那种要素颜色的光点（风、地、火、水、秩序、混沌、魔力、能量：`ASPECTS`，只借 Thaumcraft 的色值，不引用它的类），160 tick 一轮；四个坩埚各冒 6 个气泡（50 tick）。
  - **有电、NEAR**：墨迹文字在空心尖塔里往下沉，像墨滴进水里，边沉边散开（5 个，600 tick 一轮、每 6 秒一个，y26 → y13.5，半径 0.8 → 2.5）。
  - **预算**（`ArcaneLook.quads`，在 `CityRender` 之上）：NEAR 晶体和三道环带光晕 259、符文环 144、光点 40、坩埚 28、有电时墨迹 198 = 669；MID 晶体和每道环 24 段 = 80；FAR 晶体和每道环 12 段 = 45。加上墙裙光线、名牌和 44 个样本（`ModuleLook.baseQuads` 853/173/17）共 1,522/253/62，守住 2,000/300/64（`ArcaneLookTest.theCourtKeepsItsBudget`；FAR 只剩 2 quads 的余量）。名牌在控制器上方 3.6 格（y5.6，§4.10）。
  - 每部分（晶体、墨环、符文环、光点、坩埚、墨迹）单独失败。

### 6.5 血契坛（`logic/BloodCovenantShape`、`logic/BloodLook`、`city/TileBloodCovenant`、`city/client/BloodCovenantRender`；CITY §4.4，生成器 `b_pav.py` 的 `blood_covenant()`）

- **概念**：BLUEPRINT 的「红色多层圆台，中间悬着血宝珠」：阶梯神庙（大神庙、库库尔坎金字塔）加万神殿的天眼，平面像一只圣杯。
- **场地**：对角位，偏好 5 → 3 → 7 → 1（`SITES`）；25×25×17（Y0…Y0+16，`MID` 12）；控制器 (12, 3, 12)（`CTRL_Y` 3）在祭台的控制台座上方，站在祭台上的人眼睛高度 = 园区 (±36, Y0+3, ±36)。
- **体量**：三层台阶。第一层墙在 `oct(12,16)` 上 y1–6（`TIER1_TOP`；每 4 格一根亮壁柱，没有窗），y7 平台（`SLAB1_Y`：`oct(12,16)` 减 `oct(8,11)`，外圈檐口 `n`），平台边上 y8 一圈栏杆 `r`（`RAIL_Y`，`oct(12,16)` 减 `oct(11,15)`，64 格）；第二层墙在 `oct(9,12)` 上 y8–11（y8 深色墙裙、亮肋），y12 平台（`SLAB2_Y`：减 `oct(5,7)`）；第三层墙在 `oct(6,8)` 上 y13–15，y16 屋顶（`ROOF_Y`：减天眼 `oct(2,3)`），天眼一圈亮边（`oct(3,4)` 减 `oct(2,3)`，16 格）。覆层锚点 4 个（四个平面中间的 y5）。剪影：一座阶梯形的圆庙。
- **室内**：往天眼收的阶梯漏斗：外圈 6 高、中圈到 y11、内圈到 y15，祭坛上方是天。**祭台**：一圈方块 `oct(2,2)`（`B`，12 格，人站在 y2）在一圈踏板 `oct(3,4)`（`t`，24 格）里面，都在 y1；祭台中间、控制台座下面那格空着（照生成器，周围的方块把它围住）；控制台座 y2、控制器 y3。八个**宝珠座**（底座 y1 + 样本座 y2）在八角半径 6；四个**祭坛位**在四个平面的轴上、半径 9，地面用软的饰边标出 5×5（`_`，64 格），给玩家放自己的血之祭坛（二级祭坛放得下），每个中间一块**仪式石**（样本座）；地面是半径 8 的软亮仪式圈和祭台伸出的四条对角辐条。
- **单元**：12 个、12 槽。顺序（= 槽号，永不改）：

| 单元 | 名 | 种类（lang） | 位置 | 槽 |
|---|---|---|---|---|
| 0–7 | cradle-1…8 | `cradle` 宝珠座 | 右 (6, 0)、左 (−6, 0)、后 (0, 6)、前 (0, −6)、右后 (4, 4)、左后 (−4, 4)、右前 (4, −4)、左前 (−4, −4)；底座 + 样本座 | 0–7 |
| 8–11 | bay-1…4 | `ritual` 仪式石 | 前 (0, −9)、后 (0, 9)、左 (−9, 0)、右 (9, 0)；祭坛位中间的样本座 | 8–11 |

  窗口平面图：单元都在 y1，另有两张只看建筑的层「高8」「高13」（`TileBloodCovenant.planLevels()` = {8, 13}：第一层平台上的栏杆和第二层墙、第二层平台上的第三层墙，§4.9），没有单元、点不了。
- **内境门位**：`x 11–13, y 1–4, z 24`（`[` 3、`!` 3、`{` 6：第一层没有窗）。
- **门洞空气** 160；**溶解**：没有（`DISSOLVES = PASSES = ""`，`TileBloodCovenantTest.nothingDissolves`、`BloodCovenantWalkTest.nothingDissolves`）；宝珠是画在天眼下面的，不占格子。
- **计数**：`W` 906、`L` 205、`n` 184、`w` 177、`F` 96、`r` 64、`t` 24、`B` 12、`o` 12、`p` 8、`A` 4、`K` 1、`~` 1 = 检查 1,694 · 软 397（`.` 281、`+` 40、`_` 64、门位 `[` 3、`!` 3、`{` 6）· 空气 160 = **2,251 步**；校验和 `1871731853`（`BloodCovenantShapeTest`）。行走 752 个状态（地面 716、踏板 24、祭台 12），从祭台上看控制器在眼睛高度，到得了 12 个单元（`BloodCovenantWalkTest`）。
- **阶段**（`BloodCovenantShape.stage`/`fitRank`；UPPER 空着）：CLEAR（到 Y0+19）· FLOOR（场地地面、y0 的软地面、仪式圈和祭坛位的饰边）· SHELL（地基、第一层墙、门洞空气和门楣、门位）· DECK（y7 平台、第二层墙、y12 平台）· ROOF（第三层墙、屋顶、天眼亮边、y8 的栏杆）· FIT（祭台 → 控制台座 → 12 个单元各一组）· DECOR · COMMISSION（血契坛核心）。
- **任务的规模和用料**：
  - 步数 2,435–2,441：FLOOR 566 · SHELL 772 · DECK 720 · ROOF 316 · FIT 57 · DECOR 3/8/9 · COMMISSION 1；清理 585 根柱子、11,115 格。
  - 原料（不算附属）：石砖 ≈753、石头 ≈280、钢板 ≈125、荧石粉 ≈118、不锈钢板 ≈111、煤粉 ≈49、钢杆 48（栏杆）、回响晶 12、玻璃板 8、碎屑 ≈8、通量结晶 ≈4、不锈钢杆 ≈1；另要**血契坛核心 1**。
  - EU：放置 591,872（约 0.59 M）。CITY §4.4 的约 0.55 M、石砖 789 只算形状。
- **研究** `blood_covenant` 血契坛（ECHO，星图 (282, 16)，相位 2；前置 `phase_2`、`echo_crystal`；算力 30,000；钛板 ×16、EV 电路 ×4、相位合金 ×8、地狱疣 ×16）。**显化** `blood_covenant_core`（回响晶 ×8、相位合金 ×8、共鸣玻璃 ×4、EV 电路 ×2、地狱疣 ×8；1600 tick；不压记录）→ 血契坛核心（meta 3，码 67）。TE id `fluxecho:blood_covenant`；主题色血红 `0xE0344A`。**维持** 128 EU/t。
- **画面**（`BloodCovenantRender`、`BloodLook`；CITY §4.4 Visuals）：
  - **成型**：**血宝珠**浮在祭台上方、天眼下面（`ORB_Y` 14，半径 1.7），红色的球慢慢呼吸（80 tick）；8 条光线从天眼边垂到宝珠，光点顺着往下流（`DRIP_T` 60），宝珠下面挂着 4 条短的滴痕。球身是混合的，光晕、光线和渗光是加上去的。
  - **停靠**：红光沿三层的檐口（y7、y12、y16 的外面）渗开，三团光慢慢绕着走（`SEEP_T` 240）；**停靠、NEAR**：仪式圈的亮砖发红光，每个宝珠座上一颗小宝珠在样本座上方转（半径 0.22、高 0.45、200 tick 一圈），外面一圈环，放着样本的更亮。
  - **有电**：宝珠像心脏一样跳——一拍 24 tick（1.2 秒）两下（`LUB`、`DUB`）；NEAR 每一下从祭台沿四条辐条往外跑一圈脉冲（半径 2.5 → 9.5），两滴血珠从宝珠落到祭台上（`DROP_T` 48，落在 y4 的控制器顶上）；血珠（连同它的尾迹和头上的光，`DROP_GLOW` 0.1）碰到名牌那一带（`PLATE_Y` 6.6 ± `PLATE_CLEAR` 0.4）时整个不画，两边各 `PLATE_FADE` 0.3 里淡出、淡入，像从名牌后面落下（`BloodLookTest.theDropletsPassBehindTheNamePlate`；`PLATE_Y` 跟着 `ModuleLook.plates` 走）。脉冲永远不画进方块里（`thePulseRunsOutWithEachBeatAndNeverShowsInABlock`）。
  - **预算**（`BloodLook.quads`）：NEAR 1,114（球 8×12、光线和滴痕、三道檐口每个外面一条线一层光晕、仪式圈的亮砖、宝珠座、有电时脉冲和血珠）、MID 86（球 5×8、光线、檐口的八角形）、FAR 38（檐口的八角形和宝珠的光）；加上 `baseQuads` 469/173/17 共 1,583/259/55（`theAltarKeepsItsBudget`）。名牌在控制器上方 3.6 格（y6.6）。
  - 每部分（宝珠、檐口渗光、仪式圈、宝珠座、心跳）单独失败；球背对相机的面不画。

### 6.6 回响猎场（`logic/HuntingGroundShape`、`logic/HuntingLook`、`city/TileHuntingGround`、`city/client/HuntingGroundRender`；CITY §4.5，生成器 `b_pav.py` 的 `hunting_ground()`）

- **概念**：BLUEPRINT 的《星际迷航》全息甲板做成半露天的竞技场；埃皮达鲁斯剧场的碗形和罗马斗兽场的天篷。
- **场地**：对角位，偏好 3 → 5 → 1 → 7（`SITES`）；25×25×10（Y0…Y0+9，露天的碗；CITY §4.5 说它是最矮的亭子、和秘法庭的尖塔对着看，实际最矮的是 8 高的魔力泉阵），控制器 (12, 2, 12) 在竞技场中心的**全息发射器**上。
- **体量**：八角外墙 y1–8（每 3 格一根亮壁柱，y6–7 拱廊窗，`WALL_TOP` 8 檐口），上面敞着；**天篷**：y9 一圈悬浮环段 `R`（`VELARIUM_Y`，108 格），在最上面几排看台上方（八角半径 10–11）。覆层锚点 4 个（y7）。
- **室内**：**竞技场**是八角半径 ≤ 5（`ARENA_R`，按 `onorm` 算，即 `oct(5,6)`，81 格）的地面：软的全息网格，格栅地砖 `:`（45 格，HARD）和亮砖 `+` 相间；发射器（控制台座 + 控制器）周围半径 2 上四个全息底座 `h`。**看台**：半格一级，从八角半径 6（站高 1.5）一圈圈升到 11（站高 4，`TERRACE_FROM/TO`），不用跳就能走遍；**四条通道**（每个门到半径 6 的对角带 `||du|−|dv|| ≤ 1`）在地面穿过看台，半径 9 往外（`BRIDGE_FROM`）上面架着 y3 的桥（站高 4，`BRIDGE_Y`）。十二个**印记台**（底座 y1 + 样本座 y2）在竞技场边上（八角半径 6，`CONSOLE_R`）每 30 度一个。
- **单元**：12 个 `imprint-1…12`（种类 `imprint` 印记台），每个 1 槽（一个猎物印记），槽 0–11；顺序（= 槽号，永不改）从正前方 (0, −6) 起每 30 度一个，经右 (6, 0)、后 (0, 6)、左 (−6, 0) 回到前面。窗口平面图一层，画竞技场的地面（`HuntingGroundShapeTest.theWindowsPlanStaysOnTheArenasFloor`、`TileHuntingGroundTest.theWindowsPlanShowsTheArenaAndTheTerraces`）。
- **内境门位**：`x 11–13, y 1–4, z 24`（`[` 3、`!` 3、`{` 6）。
- **门洞空气** 88（门洞里面那一格让给了看台和通道，比别的亭子少）；**溶解**：`DISSOLVES = PASSES = "R"`——天篷环成型后交给渲染的光篷；照规则，最高的站高 4、天篷在 y9，高出 5 格，没有人碰得到（`HuntingGroundWalkTest.theCellsPlayersPassThroughAreOutOfReach`、`TileHuntingGroundTest.theVelariumDissolvesAndLetsPlayersThrough`）。
- **计数**：`W` 482、`d` 244、`L` 161、`n` 136、`R` 108、`t` 104、`F` 96、`w` 73、`G` 48、`:` 45、`o` 12、`p` 12、`A` 4、`h` 4、`K` 1、`~` 1 = 检查 1,531 · 软 352（`.` 304、`+` 36、门位 `[` 3、`!` 3、`{` 6）· 空气 88 = **1,971 步**；校验和 `4232271297`（`HuntingGroundShapeTest`）。行走 740 个状态，每一圈看台都有人站，到得了控制器和 12 个印记台（`HuntingGroundWalkTest`）。
- **阶段**（`HuntingGroundShape.stage`/`order`/`fitRank`；UPPER 空着）：CLEAR（到 Y0+12）· FLOOR（场地地面、y0 的软地面和竞技场的格栅）· SHELL（地基、外墙和檐口、门洞空气和门楣、门位）· DECK（看台从竞技场往外一圈一圈：`order` = 看台的八角半径 6…11，最后是通道上的桥 `BRIDGE_ORDER` 12；§4.3 的 `ModuleBuild.order`，`PavilionModulesPlanTest.theHuntingDeckRisesRingByRingFromTheArenaOut`）· ROOF（天篷环）· FIT（控制台座 → 四个全息底座 → 12 个印记台各一组）· DECOR · COMMISSION（猎场核心）。
- **任务的规模和用料**：
  - 步数 2,155–2,161：FLOOR 566 · SHELL 892 · DECK 556 · ROOF 108 · FIT 29 · DECOR 3/8/9 · COMMISSION 1；清理 585 根柱子、7,020 格。
  - 原料（不算附属）：石砖 ≈475、石头 ≈402、不锈钢板 ≈235、钢板 ≈113、荧石粉 ≈107、玻璃 48、通量结晶 ≈31、煤粉 ≈28、回响晶 12、碎屑 ≈6、铁栏杆 ≈6、不锈钢杆 ≈1；另要**回响猎场核心 1**。
  - EU：放置 534,016（约 0.53 M）。CITY §4.5 的约 0.49 M、石砖 497 只算形状。
- **研究** `hunting_ground` 回响猎场（ECHO，星图 (322, 152)（0.14.0 从 (316, 146) 挪过来给谐振塔让位），相位 2；前置 `phase_2`、`echo_crystal`；算力 30,000；钛板 ×16、EV 电路 ×4、相位合金 ×8、弓 ×4）。**显化** `hunting_ground_core`（回响晶 ×8、相位合金 ×8、共鸣玻璃 ×4、EV 电路 ×2、弓 ×2；1600 tick；不压记录）→ 回响猎场核心（meta 4，码 68）。TE id `fluxecho:hunting_ground`；主题色琥珀 `0xFF9A3C`。**维持** 128 EU/t。
- **画面**（`HuntingGroundRender`、`HuntingLook`；CITY §4.5 Visuals）：
  - **成型**：天篷环变成合拢在碗上方的半透明**六边形光篷**，边上琥珀色、竞技场上方青色，从 y9.5 的边（`EDGE_Y`）往中间拱起 1.5 格（`RISE`），一道光涌过去（`CANOPY_T` 240）；环本身留成几条光线。六边形 NEAR 边长 1.25、MID 2.0（`HEX`）；光篷离外墙有余量（`theCanopyClosesOverTheBowlClearOfTheWall`）。
  - **停靠**：每 6 秒（`SCAN_T` 120）一道光线扫过竞技场的全息网格（扫 1.8 秒，`SWEEP_T` 36），每次比上次转 90 度，亮砖在它后面 2.5 格（`TRAIL`）里发光；**停靠、NEAR**：放着样本的印记台在样本座上方显示一个小全息——一张朝着相机的平面**剪影**，颜色是样本那种怪的刷怪蛋的两种颜色（猎物印记按注册名 `fluxecho:mob_imprint` 和 NBT `Mob` 读出怪，刷怪蛋按 meta；不引用 `com.fluxecho.mobs` 的类），认不出怪就用中性的钢灰和青色（`NEUTRAL`）。剪影按注册名里的词分 7 种（人形、兽、爬虫、高个、团块、小个、苦力怕，`HuntingLook.kindOf`：先认 zombie/skeleton…，僵尸猪人先算人形、末影螨先算爬虫），不画实体模型（CITY §4.5：光影下在 `RenderWorldLast` 画实体模型要另做原型）。刷怪蛋的颜色每种怪查一次、存进一张表（`EGGS`），不是计数。
  - **有电**：更亮；让印记的怪在竞技场里演出要等功能（0.19.0 起）。
  - **预算**（`HuntingLook.quads`）：NEAR 654（小六边形和边、环的两个八角形和连线、扫光带光晕和尾迹、全息）、MID 82（大六边形、边、扫光）、FAR 20（光篷 8 个 quads 和边）；加上 `baseQuads` 469/173/17 共 1,123/255/37（`theGroundKeepsItsBudget`）。名牌在控制器上方 3.6 格（y5.6）。
  - 每部分（光篷、扫光、全息）单独失败；光篷、扫光和光晕是加上去的，剪影是混合的（深色的蛋加不上去）。

### 6.7 魔力泉阵（`logic/ManaFountainShape`、`logic/ManaLook`、`city/TileManaFountain`、`city/client/ManaFountainRender`；CITY §4.6，生成器 `b_pav.py` 的 `mana_fountain()`）

- **概念**：BLUEPRINT 的「一圈喷泉围着魔法阵」：阿尔罕布拉宫的狮子庭院（拱廊庭院里一座喷泉），回廊尺度的喷泉广场，植物魔法的符文祭坛圈。
- **场地**：对角位，偏好 1 → 7 → 3 → 5（`SITES`）；25×25×8（Y0…Y0+7：低矮敞开，大门边迎客的花园；生成器分配 9 层但只画 y0–y7，`GEN_HEIGHT` 9，类加载时检查顶层是空的）；控制器 (12, 2, 12) 在中心方尖碑上。
- **体量**：外墙 y1–6（每 2 格一根亮壁柱，几乎是拱廊，y3–4 窗），y7 回廊上的**玻璃屋顶**（`ROOF_Y`：`oct(12,16)` 减 `oct(9,12)` 的玻璃 `G`，八条线上是墙板 `W` 的肋——生成器就是这样画的，CITY 的文字写的是亮肋；外圈檐口 `n`）；`oct(9,12)` 线上的**内柱廊**（隔一格一根立柱 `q`，y1–5，160 格；上面 y6 檐口做的额枋 `n`，48 格，`ARCHITRAVE_Y`；四个对角留开口）；中间是露天的**庭院** `oct(8,11)`。覆层锚点 4 个（y5）。
- **室内**：**方尖碑**：控制台座 y1、控制器 y2、导能柱 y3–6、晶核座 y7（`OBELISK_FROM/TO`）。八座**喷泉**在八角半径 6（四正 (±6, 0)、(0, ±6)，四斜 (±4, ±4)）：3×3 池沿围着中间的水，样本座浮在水上方 y2。八个**花瓮**（底座 + 样本座）在柱廊里面 (±8, ±2)、(±2, ±8)。玻璃屋顶下的回廊一圈是**花园的土**（草 `,`，80 格，八角半径 10–11，避开两条轴和对角线），给玩家种自己的花、放魔力池；庭院里是软的亮魔法阵（半径 2 和 8 两个圈加两条轴）。
- **单元**：16 个、16 槽。顺序（= 槽号，永不改）：

| 单元 | 名 | 种类（lang） | 位置 | 槽 |
|---|---|---|---|---|
| 0–7 | fountain-1…8 | `fountain` 喷泉 | 右 (6, 0)、左 (−6, 0)、后 (0, 6)、前 (0, −6)、右后 (4, 4)、左后 (−4, 4)、右前 (4, −4)、左前 (−4, −4)；8 格池沿 + 水上的样本座 | 0–7 |
| 8–15 | urn-1…8 | `urn` 花瓮 | (8, 2)、(8, −2)、(−8, 2)、(−8, −2)、(2, 8)、(−2, 8)、(2, −8)、(−2, −8)；底座 + 样本座 | 8–15 |

  窗口平面图一层（`TileManaFountainTest.theWindowsPlanShowsTheCourt`）。
- **内境门位**：`x 11–13, y 1–4, z 24`（`[` 3、`!` 3、`{` 2、`/` 4）。
- **门洞空气** 160；**溶解**：`DISSOLVES = PASSES = "CZ"`——方尖碑的导能柱和晶核座成型后交给渲染的魔力泉柱；它们在控制器正上方，没有人站得到（`ManaFountainWalkTest.theCellsPlayersPassThroughAreOutOfReach`、`TileManaFountainTest.theObeliskDissolvesAndLetsPlayersThrough`）。
- **计数**：`W` 198、`L` 173、`q` 160、`G` 156、`n` 152、`F` 96、`w` 73、`u` 64、`o` 16、`p` 8、`A` 4、`C` 4、`K` 1、`Z` 1、`~` 1 = 检查 1,107 · 软 405（`.` 232、`+` 73、`,` 80、`=` 8、门位 `[` 3、`!` 3、`{` 2、`/` 4）· 空气 160 = **1,672 步**；校验和 `4155736375`（`ManaFountainShapeTest`）。行走 652 个状态（都在地面），到得了控制器和 16 个单元（`ManaFountainWalkTest`）。
- **阶段**（`ManaFountainShape.stage`/`fitRank`；UPPER 空着）：CLEAR（到 Y0+10）· FLOOR（场地地面、y0 的软地面、魔法阵和花园的草）· SHELL（地基、外墙、门洞空气和门楣、门位）· DECK（柱廊的立柱和额枋，208）· ROOF（y7 的玻璃屋顶，204）· FIT（方尖碑 → 16 个单元各一组 → 池水最后，池沿完整之后）· DECOR · COMMISSION（泉阵核心）。
- **任务的规模和用料**：
  - 步数 1,856–1,862：FLOOR 566 · SHELL 772 · DECK 208 · ROOF 204 · FIT 102 · DECOR 3/8/9 · COMMISSION 1；清理 585 根柱子、5,850 格。
  - 原料（不算附属）：石砖 ≈368、石头 ≈254、玻璃 160、荧石粉 ≈128、钢板 ≈103、不锈钢板 ≈101、钢杆 40（立柱）、煤粉 ≈32、通量结晶 ≈13、回响晶 12、玻璃板 8、碎屑 ≈7、不锈钢杆 ≈7；另要两件成品：**晶核座 1**（方尖碑顶）和**魔力泉阵核心 1**；水 8 格、草 80 格只花 EU。
  - EU：放置 426,752（约 0.43 M：1,605 步各 256，水、草和门洞空气 248 步各 64）。CITY §4.6 的约 0.40 M、石砖 399 只算形状。
- **研究** `mana_fountain` 魔力泉阵（ECHO，星图 (272, 182)，相位 2；前置 `phase_2`、`echo_crystal`；算力 30,000；钛板 ×16、EV 电路 ×4、相位合金 ×8、虞美人 ×16）。**显化** `mana_fountain_core`（回响晶 ×8、相位合金 ×8、共鸣玻璃 ×4、EV 电路 ×2、虞美人 ×8；1600 tick；不压记录）→ 魔力泉阵核心（meta 5，码 69）。TE id `fluxecho:mana_fountain`；主题色淡蓝 `0x6AA8FF`。**维持** 128 EU/t。
- **画面**（`ManaFountainRender`、`ManaLook`；CITY §4.6 Visuals）：
  - **成型**：方尖碑的顶变成**魔力泉柱**——从控制器顶上（y3.15）升到 y8.6 的一根螺旋光点柱，两股互相缠绕（绕 2.5 圈，`SPOUT_TURNS`；一颗光点 100 tick 升到顶，`SPOUT_T`），带光晕；光点 NEAR 32、MID 12。
  - **停靠**：八座喷泉的样本座各射出一道淡蓝的光流（从 y3.05 弧形落到泉柱上 y5.4、离轴 0.3，拱起 1.6；NEAR 20 段带光晕和 3 个光点、MID 6 段、FAR 2 段；`JET_T` 50）；庭院地面的**魔法阵**在转：外圈（半径 7.7，90 秒一圈，8 道亮划、12 个符文）和内圈（半径 1.9，60 秒一圈反着转，8 道刻度），从池沿和花瓮下面穿过去（被挡住的那几段不画）。
  - **有电、NEAR**：16 颗魔力火花（每颗 220 tick）从泉柱高处出发，穿过柱廊的开口，沉到回廊的草地上淡掉。
  - **预算**（`ManaLook.quads`）：NEAR 525、MID 105、FAR 17；加上 `baseQuads` 517/173/17 共 1,042/278/34（`theArrayKeepsItsBudget`）。名牌在方尖碑顶上方 1.75 格（y9.75，`ModuleLook.OVER_COLUMN`，§4.10）。光全是加上去的。
  - 每部分（泉柱、光流、魔法阵、火花）单独失败。

### 6.8 谐振塔（`logic/ResonanceGateShape`、`logic/GateLook`、`city/TileResonanceGate`、`city/client/ResonanceGateRender`；CITY §4.7，生成器 `b_gate.py` 的 `resonance_gate()`）

- **概念**：BLUEPRINT 的浮空水晶尖塔（《星际争霸》神族的水晶塔）做成城的门楼：雅典卫城山门、勃兰登堡门那样两翼夹着一道门，鸟居的门槛，雷门的灯笼换成一颗浮着的水晶。
- **场地**：大门 0（`SITES` {0}，大门族，§4.14）；31×17×18（Y0…Y0+17，`WIDTH/DEPTH/HEIGHT`，`MID` 15），本地坐标 `x = 15 + R`、`z = A − 23`，正面 z0 朝中枢；两翼 x 0–8、22–30（`LEFT_TO`/`RIGHT_FROM`，R ±7…±15），中间 13 宽的通道 x 9–21（R −6…6）7 格高敞着。控制器 (15, 2, 7)（`CTRL_X/Y/Z`）在桥下**路中间的亭**里、控制台座 (15, 1, 7) 上方 = 园区 (A 30, R 0, Y0+2)，朝中枢，站在路上就是眼睛高度。
- **体量**：两座**翼楼**（各 9×17：墙 y1–15，y1 深色墙裙 `w`；亮壁柱 `L` 在角上、正面和背面 `|x−15| % 4 == 3`、侧面 `z % 4 == 0`；外侧面 `z % 4 == 2` 的 y3–6 和 y10–13 是高窗，正面 y10–13 `|x−15| % 4 == 1` 是上层窗；y16 檐口 `n` 一圈加屋顶，y17 每座 6 根亮冠柱（四角和两侧中间），y15 每座正面的两个角各一个覆层锚点，共 4 个）；通道上的**桥**（x 9–21、z 5–11 = A 28–34，`BRIDGE_FROM/TO`）：y8 桥面（`DECK_Y`）、桥边下面 y7 一道檐口，y9–12 两侧的墙（`BRIDGE_WALL`：y10–11 奇数 x 是窗格（两头的 x 9、21 也是），x 9 和 21 的 y9、y12 是亮壁柱，其余墙板），y13 桥顶（`BRIDGE_ROOF`，边上是檐口）；桥正中的**核心柱**：导能柱 (15, 9–11, 8)、晶核座 (15, 12, 8)（`CORE_Z/CORE_FROM/SEAT_Y`），上面 y13 一格玻璃。剪影：两座方塔夹着一座桥，桥上浮着一根水晶。
- **室内**：每座翼楼一层是**门厅**（正面门 x 3–5 / 25–27，z 0–1 两格深、y5 光檐门楣；朝通道的门 x 7–8 / 22–23，z 7–9，两格深），外墙上两个**调谐架**（z 4、8），一个全息底座 (4, 1, 5) / (26, 1, 5)，一座**塔楼楼梯**（中柱 (4, 13)、(26, 13)，`WELLS`，16 级，站高 1 → 9，两座转向相反、互为镜像）上到**楼上**（楼面 y8）；两边楼上都有开口（x 8 / 22，z 6–10，y9–12，必须是空气）通进桥上的**谐振室**：六根**谐振柱**靠着桥两侧的墙，围着中间的核心柱。通道里只有路中间的亭（控制台座和控制器）和路没铺到的软地面 `.`（`gatePassageFloor`，§4.14）；大门路、路口和门槛的加宽、四根塔柱原样留着。
- **单元**：10 个、24 槽。顺序（= 槽号，永不改；位置同 §6.1：+x 是走进通道的人的右手边，+z 是离中枢远的那边）：

| 单元 | 名 | 种类（lang） | 位置 | 槽 |
|---|---|---|---|---|
| 0–2 | resonator-1…3 | `resonator` 谐振柱 | 谐振室前墙边（z6，面朝 +z）x 11、15、19；底座 y9 + 2 个样本座 y10–11 + 顶冠 y12 | 0–5（每根 2） |
| 3–5 | resonator-4…6 | `resonator` 谐振柱 | 谐振室后墙边（z10，面朝 −z）x 11、15、19 | 6–11 |
| 6–7 | rack-7、rack-8 | `rack` 调谐架 | 左翼门厅外墙 x1（面朝 +x）z 4、8；底座 y1 + 3 个样本座 y2–4 + 顶冠 y5 | 12–17（每个 3） |
| 8–9 | rack-9、rack-10 | `rack` 调谐架 | 右翼门厅外墙 x29（面朝 −x）z 4、8 | 18–23 |

  调谐架的名字接着谐振柱往下数（生成器的计数器没有重置）。窗口平面图两层：高 1（门厅、调谐架、路中间亭的控制台座）、高 9（楼上和谐振室的谐振柱）（§4.9）。
- **没有内境门位**：它自己就是门（`ResonanceGateShapeTest.noGateNiche`）。
- **门洞空气** 136（两座翼楼的正面门 48、朝通道的门 48、桥两头的开口 40）；**溶解**：`DISSOLVES = PASSES = "CZ"`——核心柱成型后交给渲染的水晶尖塔，能穿过（§4.6 的第二个例外：谐振室的楼面从它下面穿过，人能走进那一列，只多出这一个站的格子，`ResonanceGateWalkTest.theDissolvedCoreOpensItsColumnAlone`）。
- **计数**：`W` 1,222、`L` 328、`d` 283、`n` 160、`G` 109、`F` 96、`w` 84、`o` 24、`q` 16、`t` 16、`c` 10、`p` 10、`A` 4、`C` 3、`h` 2、`K` 1、`Z` 1、`~` 1 = 检查 2,370 · 软 268（`.`：两座门厅 210、通道 58）· 空气 136 = **2,774 步**；除两座楼梯的 32 格（`asym`）外沿 x 对称；整层校验和 `1079565838`（`ResonanceGateShapeTest`）。行走从路上通道口两边和左翼正门外出发（`walkStarts`）932 个状态：两座门厅、两座楼梯的每半格、两边楼上、谐振室 58 个站的格子，最高站高 9（没有人站在屋顶上），站在路上看控制器是眼睛高度，到得了 10 个单元（`ResonanceGateWalkTest`）。
- **阶段**（`ResonanceGateShape.stage`/`fitRank`）：CLEAR（§4.14：通道外 380 根柱子到 Y0+20、平整，通道上只有桥的 91 根、从 Y0+7 起、不平整）· FLOOR（场地地面 194 + y0 的软地面：门厅 210、通道 58）· SHELL（两翼的地基和 y1–7 的墙、门洞和桥开口的空气、门楣）· DECK（两翼 y8 的楼面和墙带、两座楼梯和中柱、桥面 y8 和下面 y7 的檐口）· UPPER（两翼 y9–17：墙、锚点、檐口和屋顶、冠柱，冠柱在它站的檐口之后；桥两侧 y9–12 的墙）· ROOF（只有桥顶 y13，91 格）· FIT（亭的控制台座 → 6 根谐振柱、4 个调谐架各一组 → 两个全息底座 → 核心柱，自下而上）· DECOR（附属）· COMMISSION（谐振塔核心）（`ResonanceGateShapeTest.everyCellHasItsStage`：翼楼 y9–17 全在 UPPER，ROOF 只有桥顶）。
- **任务的规模和用料**（同 §6.1 的算法，5 个园区种子 × 4 个朝向算过）：
  - 步数 2,971–2,974：FLOOR 462 · SHELL 856 · DECK 453 · UPPER 1,054 · ROOF 91 · FIT 51 · DECOR 3–6（附属：全息牌柱 3、长椅 4、种植墙 5、灯排 6）· COMMISSION 1；清理 471 根柱子、8,874 格（380 根平整）。
  - 原料（不算附属）：石砖 ≈914、石头 ≈373、钢板 ≈173、荧石粉 ≈134、玻璃 109、不锈钢板 ≈103、煤粉 ≈25、回响晶 12、通量结晶 ≈12、碎屑 7、玻璃板 6、不锈钢杆 ≈5、钢杆 4；另要两件成品：**晶核座 1**（核心柱顶，`partOnly`）和**谐振塔核心 1**（码 70）。
  - EU：放置 733,696（约 0.73 M：2,832 步各 256，门洞空气 136 步各 64），附属每步另加；清理和平整另算。CITY §4.7 的约 0.68 M、石砖 955、石头 276、钢板 166、荧石粉 135、玻璃 109 只算形状、不算场地地面；以建造页的材料单为准。
- **研究** `resonance_gate` 谐振塔（ECHO，星图 (340, 120)，在回响分支那一侧的右边、秘法庭和回响猎场之间，猎场为它从 (316, 146) 挪到 (322, 152)；相位 2；前置只有 `phase_2`；算力 30,000；`ore:plateTitanium` ×16、`ore:circuitEV` ×4、`ore:ingotPhaseAlloy` ×8、音符盒 ×16）。**显化** `resonance_gate_core`（同一研究；回响晶 ×8、相位合金 ×8、共鸣玻璃 ×4、`ore:circuitEV` ×2、音符盒 ×4；1600 tick；不压记录）→ 谐振塔核心（`fluxecho:module_core` meta 6，码 70）。TE id `fluxecho:resonance_gate`；主题色青绿 `0x5CF2C2`。**维持** 128 EU/t；谐振柱里的调谐水晶给穹顶下每台机器加速或并行，0.19.0 起。
- **画面**（`ResonanceGateRender`，位置和动作是 `GateLook` 的纯算术，绕尖塔的轴 (15.5, 8.5)；CITY §4.7 Visuals）：
  - **成型**（不论停靠）：核心柱交给浮在桥顶上的**水晶尖塔**——拉长的八面体，3 宽、16 高（`SPIRE_FROM` 14 到 `SPIRE_TO` 30，即 Y0+14…+30，腰在 22），浮起 `HOVER` 0.3、上下浮 `BOB` 0.15（200 tick 一次），60 秒转一圈（`TURN_T` 1200），里面一颗淡一点的晶体发光；六片**碎晶**在腰部半径 `SHARD_R` 3.4 上、高低错开地绕着它转（`ORBIT_T` 480 tick 一圈，各自 90 tick 自转、上下浮 0.2）；一根**光柱**从谐振室的楼面（y9，`BEAM_FROM`）穿过溶解的核心柱和上面的玻璃升到尖塔的脚，NEAR 有 6 颗光点往上升（`BEAM_MOTE_T` 70）。没停靠亮度 0.3、停靠没电 0.6、有电全亮。
  - **停靠**：每 8 秒（`PULSE_T` 160）一道**谐振脉冲**——一圈光从尖塔的腰（半径 1.8）跑到 15 格外（`RING_T` 64 tick 跑完，先快后慢、边跑边淡），扫过两座翼楼；圈按档位切成 48/24/16 段（`RING_PIECES`，NEAR 带光晕）。
  - **有电**：脉冲快一倍（`PULSE_ON_T` 80）；碎晶排成一圈，在尖塔腰的高度上水平地转、快一倍（`ORBIT_ON_T` 240）。
  - **地标**（`landmark`，超出模块的绘制范围后由 `NexusRender` 调）：尖塔、碎晶的光点和脉冲画到 `LANDMARK` 512 格，最后 `LANDMARK_FADE` 64 格淡出；范围以内尖塔、碎晶和脉冲也按到尖塔腰的这个距离淡出（不按特效的 fade），所以 FAR 和地标画的一样、接得上。相位 IV 起天环 S0 的光柱接在尖塔顶上（CITY §4.7，0.16.0）。
  - **墙裙光线**沿通道两边的墙也走（`ModuleLook.outside`，92 个面，`GateLookTest.thePlinthLineRunsAlongThePassageToo`）。名牌没有雨棚，在控制器上方 3.6、门外 1.5：模块坐标 (15.5, 5.6, −1.5)，通道口上空。
  - **预算**（`GateLook.quads`，在 `CityRender` 之上）：NEAR 178（尖塔和里面的晶体加光晕 17、6 片碎晶各带光晕 54、光柱和光点 11、脉冲 96）、MID 93（17、碎晶 48、光柱 4、脉冲 24）、FAR 31（尖塔 9、碎晶 6 个光点、脉冲 16）；加上 `baseQuads` 659/187/15 共 837/280/46，守住 2,000/300/64（`GateLookTest.theGateKeepsItsBudget`；MID 只剩 20 的余量）。地标没停靠 15、停靠 31，上限 `BUDGET` 400。
  - 每部分（尖塔、碎晶、光柱、脉冲、地标）单独失败；朝相机的面才画，不写深度。

### 6.9 二向箔仓（`logic/DualFoilVaultShape`、`logic/VaultLook`、`logic/CanvasLook`、`city/TileDualFoilVault`、`city/client/VaultRender`、`CanvasRender`、`Paintings`；CITY §4.2，生成器 `b_hall.py` 的 `vault()`）

- **概念**：《死神永生》里把世界压成一幅画的二向箔；里面是一座画廊（卢浮宫大画廊、冬宫挂满画的墙），尺度是泰特现代美术馆的涡轮大厅；外面是一座没有窗的方碑，整个正面是一幅巨大的画布。
- **场地**：大厅位，偏好 6（中枢左边）→ 2 → 4（`SITES`）；27×35×27（Y0…Y0+26，`WIDTH/DEPTH/HEIGHT`，`MID` 13）；控制器 (13, 2, 0)（`CTRL_X/Y/Z`）在套件门的门间柱里、控制台座上方，前排径向 24、Y0+2、朝中枢（`hallController`）。
- **体量**：没有窗的方碑：墙 y1–23（y1 深色墙裙；亮壁柱在长墙 `z % 4 == 0`、前后墙 `|x−13| % 5 == 0` 和四角），y24 檐口带 `n`（`CORNICE_Y`），y25 屋顶（`ROOF_Y`）上六道玻璃天窗（`z ≡ 3 mod 6`、x 4–22，6 × 19 = 114 格），每道天窗上方 y26 一道凸起的屋脊（`RIDGE_Y`）——锯齿形的顶。**正面画布**：门上方 x 4–22、y 7–20 一圈亮框围着 17×12 格画布（`FACADE_*`），框的四角各一个覆层锚点；下面是套件门（`ShapeKit.portal`，y5 门楣、两排雨棚）。
- **室内**：一座 25×33 的长厅、通到屋顶。两面长墙各挂**三层、每层七幅画布**（底座排 y1/9/17、画布 3 宽 4 高、上面一排顶冠，单元之间是立柱；`TIERS` {1, 9, 17}，从 z 5 起每 4 格一幅，`CANVAS_FROM/STEP/PER_SIDE`）。两角各一座**塔楼楼梯**（中柱 (2, 2)、(24, 2)，`STAIRS`，一条 32 级的螺旋，站高 1 → 9 → 17，两座转向相反、互为镜像）上到两层 3 宽的**走道**（x 1–3、23–25，楼面 y8 和 y16，`DECKS`；靠长厅一边亮边和栏杆）和前面楼梯周围的**平台**（x 1–6、20–25，z 1–6）；y16 一条**横廊**（z 31–33，`CROSS_FROM/TO`）从二向箔上方穿过、两头通两边的走道。后墙是**二向箔**（亮框 x 6–20、y 2–14 里 13×11 的黑画布 x 7–19、y 3–13，`FOIL_*`），下面一张**桌子**：控制台座 (13, 1, 30)（`DESK_X/Y/Z`）夹在两个全息底座 (10, 1, 30)、(16, 1, 30) 之间。
- **单元**：43 个、43 槽，每个 1 槽（画布存一种物品；数量 0.19.0 起）。顺序（= 槽号，永不改；+x 是走进门的人的右手边）：

| 单元 | 名 | 种类（lang） | 位置 | 槽 |
|---|---|---|---|---|
| 0–6 | GL-1…7 | `canvas` 画布 | 一层（底座 y1、画布 y2–5、顶冠 y6）左墙 x0，从前往后 z 5–7、9–11 … 29–31；每幅 3 个底座 + 12 格画布 + 3 个顶冠，面朝 +x | 0–6 |
| 7–13 | GR-1…7 | `canvas` 画布 | 一层右墙 x26，面朝 −x | 7–13 |
| 14–27 | ML-1…7、MR-1…7 | `canvas` 画布 | 二层（y9–14），左墙再右墙 | 14–27 |
| 28–41 | UL-1…7、UR-1…7 | `canvas` 画布 | 三层（y17–22），左墙再右墙 | 28–41 |
| 42 | foil | `foil` 二向箔 | 后墙 13×11 的黑画布去掉门位 12 格 = 131 格，面朝 −z；最低一格 y3，从长厅地面用（`usedFrom` 1，`FOIL_FLOOR`） | 42 |

  画布单元没有样本座：一个槽画满整面画布（§4.10），潜行空手点它任何一格取回（`takeSlot`，§4.8）。两个控制台座（门间柱里控制器下面的、桌子上的）都开窗口（§4.6）。窗口平面图三层：高 1（地面一层画布、二向箔——后墙一条 13 格宽的整条——和两个控制台座）、高 9、高 17（§4.9）。
- **内境门位**：二向箔的下半中间 `x 12–14, y 3–6, z 34`（`NICHE_X0/X1/Y0/Y1/Z`、`inNiche`）：12 格画布换成软孪生 `$`（同样的方块，跟后墙一起在 SHELL 建，结构检查不看），以后从这里「走进画里」（画墙仓的内境，CITY §4.2）。生成器把门位放在二向箔里，而单元格永远是检查格（`gateNiche` 也只认墙），所以 0.14.0 **把这 12 格从二向箔这个单元里拿出来**，二向箔留下另外 131 格（§7 第 1 步说过移植时要先定；替用户做的选择，BLUEPRINT 第七节 9）。画面照样把门位当画布：二向箔的画面是框里的整块 13×11，事件视界绕着整块走（`DualFoilVaultShapeTest.theGateNicheIsSoftCanvasInNoUnit`、`VaultPlanTest.theGateNicheIsBuiltSoft`）。
- **门洞空气** 48（套件门）；**溶解**：没有（`DISSOLVES = PASSES = ""`，`TileDualFoilVaultTest.nothingDissolves`）——画是画在方块上面的。
- **计数**：`W` 1,717、`Q` 839、`L` 417、`d` 330、`q` 322、`e` 159、`r` 153、`n` 129、`p` 126、`c` 126、`F` 120、`G` 114、`w` 51、`t` 32、`A` 4、`K` 2、`h` 2、`~` 1 = 检查 4,644 · 软 837（`.` 825、门位 `$` 12）· 空气 48 · decor 14（雨棚）= **5,543 步**；镜像对称（两座楼梯互为镜像，生成器照样把它们的 64 格记进 `asym`）；整层校验和 `4211763960`（`DualFoilVaultShapeTest`）。行走从两扇门外出发 1,484 个状态：两座楼梯的每半格、两层走道从平台走到后墙、横廊 19 × 2 格，最高站高 17（没有人站在屋顶上）；每层画布从它自己那层楼面、正对着它用（一层站高 1、二层 9、三层 17），二向箔整个 13 格宽站在长厅地面上够得着（`DualFoilVaultWalkTest`）。
- **阶段**（`DualFoilVaultShape.stage`/`fitRank`）：CLEAR（1,073 根柱子 Y0+1..Y0+29，平整）· FLOOR（场地地面 296 + y0 的软地面 825）· SHELL（地基、外墙 y1–7 连门位、门洞空气、门间柱和门楣）· DECK（两座塔楼楼梯和中柱、y8–9 和 y16–17 的走道、亮边、栏杆、平台、横廊）· UPPER（外墙 y8–23，正面的画布在这里）· ROOF（y24 檐口带、y25 屋顶和天窗、y26 屋脊）· FIT（42 幅画布各一组 → 二向箔一组 → 桌子的控制台座 → 两个全息底座 → 四个覆层锚点）· DECOR（雨棚 + 种子装饰）· COMMISSION（二向箔仓核心）。长墙上画布那几列是单元格，在 FIT 里一组一组地立起来。
- **任务的规模和用料**（同 §6.1 的算法，5 个园区种子 × 场地 6、2、4 × 4 个朝向算过）：
  - 步数 5,842–5,877：FLOOR 1,121 · SHELL 676 · DECK 738 · UPPER 1,336 · ROOF 1,059 · FIT 894（画布 42 × 18、二向箔 131、另 7 格）· DECOR 17–52（雨棚 14 加种子多出的 0/7/14 格，附属 3/19/23/24）· COMMISSION 1；清理 1,073 根柱子、31,117 格。
  - 原料（不算 DECOR 的雨棚和附属）：石砖 ≈1,821、石头 ≈796、钢板 ≈353、荧石粉 ≈273、钢杆 ≈195、不锈钢板 ≈118、玻璃 114、纸 ≈106（画布）、煤粉 ≈41–44、玻璃板 ≈19、回响晶 15、碎屑 ≈8、通量结晶 ≈3、不锈钢杆 ≈2；另要**二向箔仓核心 1**（码 65，`partOnly`）。
  - EU：放置 1,481,984（约 1.48 M：5,777 步各 256，门洞空气 48 步各 64），DECOR 每步另加；清理和平整另算。CITY §4.2 的约 1.41 M、石砖 1,869、石头 657、钢板 342、荧石粉 270、钢杆 196、纸 107 只算形状、不算场地地面；以建造页的材料单为准。
- **研究** `dual_foil_vault` 二向箔仓（DEPTHS，星图 (84, 104)，在深层分支碎屑富集的左下、能量分支上方；相位 2；前置只有 `phase_2`；算力 30,000；`ore:plateTitanium` ×16、`ore:circuitEV` ×4、`ore:ingotPhaseAlloy` ×8、纸 ×32）。**显化** `dual_foil_vault_core`（同一研究；回响晶 ×8、相位合金 ×8、共鸣玻璃 ×4、`ore:circuitEV` ×2、画 ×4；1600 tick；不压记录）→ 二向箔仓核心（`fluxecho:module_core` meta 1，码 65）。TE id `fluxecho:dual_foil_vault`；主题色羊皮纸 `0xF2EBDD`。**维持** 128 EU/t；画进画布的大量存储（接 AE）、二向箔的销毁模式 0.19.0 起。
- **画面**（`VaultRender` 和画布的 `CanvasRender`，位置是 `VaultLook`、`CanvasLook` 的纯算术；CITY §4.2 Visuals）：
  - **成型扫光**：通用的扫光之外，正面画布跟着一道亮线被洗成羊皮纸白（`VaultRender.sweep`，亮线下面 `SWEEP_TRAIL` 3 格）。
  - **成型**（不论停靠）：42 幅画布和二向箔画各自的物品（§4.10 第 6 条：色调分离的大图、底色、空的画网格）；**正面的拼贴**：门上方的正面画布（17×12 的画面，在正面墙外侧）拼着仓里最多的几种物品——9 块（边长 7、4、4、3、3、2、2、2、2；最多的那种在中间最大的一块，其余按轴成对镜像，`VaultLook.tile`），按存它的单元数排（物品 id + 损伤值，不看 NBT；一样多的按先出现的槽，`ranking`），FAR 每块只是物品颜色的一块；**天窗光柱**：六道天窗投下静止的光柱进长厅，每落一格往前倾 `SLANT` 0.35，停在碰到的第一块楼面、走道或墙上，整个落在楼面或走道上的留一块光斑（`VaultLook.shafts`：每道天窗按落得一样远的格子切段，共 12 段），亮度跟一天的时间走（`daylight`：太阳高时全亮，夜里 `NIGHT` 0.12，月光），不看天气；光柱只在 NEAR。
  - **停靠**：正面的亮框以羊皮纸色呼吸（`BREATH_T` 120）；二向箔的边一条淡线。
  - **有电**：亮框的光稳住、一道闪光绕框走（`GLINT_T` 400）；二向箔的边像**事件视界**：黑画布四周一条白热的线、一段亮弧每 `RIM_T` 160 tick 绕一圈、往框外一层柔光（`HALO` 0.75）、画布从边往里变暗（`DARK` 1.4）。东西被压扁进二向箔要等功能。
  - **预算**（`VaultLook.quads`，在 `CityRender` 和画布之上）：NEAR 201（拼贴 36、成型时扫光 4、光柱 41、亮框 20、事件视界 100；停靠没电时边只是 48 的淡线）、MID 66（拼贴 36、扫光 4、亮框 10、视界 16）、FAR 13（拼贴的 9 块底色、扫光 4）；加上 `baseQuads` 252/150/19 和画布 430/0/0 共 883/216/32（`VaultLookTest.theVaultKeepsItsBudget`）。名牌在雨棚上方 1.75（模块坐标 (13.5, 6.75, −1.0)）。
  - 每部分单独失败（拼贴、光柱、亮框、视界在 `VaultRender` 里各自失败，扫光跟着 `CityRender` 的扫光，画布是 `CityRender` 的 `CANVAS` 那一部分）。

## 7. 加一座建筑（照生态穹）

1. **形状**：把生成器（`city/gen/b_*.py`）逐行移植成 `logic/XShape`（用 `ShapeKit`，图例不改名）；`XShapeTest`：整层校验和、计数（检查/软/空气/decor）、对称（不对称的格子登记进 `asym`，并确认 `Blueprint.world` 的精确映射对它们成立）、控制器和控制台座、门洞空气、每个字符都是图例部件、每格有阶段；`XWalkTest`：走到控制器和每个单元、溶解后能穿过的格子够不着。生成器自己改了形状，先改生成器、重跑 `report.py`（更新 `plans.txt`），再移植。有内境的建筑，CITY §4.15 的 3×4 门位在生成器里就用 `s.gate_niche(xs, ys, z)` 标成软格（同样的方块，§1），移植成 `s.gateNiche(x0, x1, y0, y1, z)`；`XShapeTest` 照 `EcoDomeShapeTest.theGateNicheIsSoftAndStillBuilt` 写明哪几格（软、蓝图里是空格、`checkedTwin` 是原来的墙、阶段跟着墙），计划照 `ModulePlanTest.theGateNicheIsBuiltSoft` 查它们是 SOFT 步骤、部件不变。门位里不能有单元格（单元格永远是检查格，`gateNiche` 只认墙）：生成器把门位放在单元里的（二向箔仓的门位在二向箔里面），0.14.0 定的做法是把门位的格子从单元里拿出来、换成软孪生（画布 `Q` → `$`），单元留下其余的格子（二向箔 131 格，§6.9）；以后再碰到同样的情况照这样做，`XShapeTest` 写明门位不属于任何单元（`DualFoilVaultShapeTest.theGateNicheIsSoftCanvasInNoUnit`）。
2. **单元**：生成器里的单元 → `new UnitLayout(SHAPE)`（挂在墙上、从下面的楼面用的单元给 `new UnitLayout(SHAPE, usedFrom)`，照二向箔，§4.2）；新的单元种类加 lang `fluxecho.unit.<kind>`（两份）。单元顺序从此是槽号。
3. **建造**：`new CityBuild(KEY, SHAPE, HEIGHT, BLUEPRINT, UNITS, Parts.moduleCore(meta), stages)`，`Stages.stage` 照 CITY §4.x 的阶段表，`fitRank` 不能是 0；`plan(...)` = `BuildPlan.module(..., BUILD)`、`controller(...)` = `BuildPlan.hallController(BUILD, ...)`（亭子是 `BuildPlan.pavilionController(BUILD, ...)`，§4.13；门楼是 `BuildPlan.gateController(BUILD, ...)`，§4.14）；一个阶段里要按别的顺序放的（猎场的看台一圈圈往外）给 `Stages.order`，默认 0 就是老的从下往上。`ModulePlanTest` 那样测：HARD = 检查格（4 朝向 × 每个场地）、阶段内容、单元组连续且按顺序、清理盒、种子装饰。
4. **核心**：在 `ModuleCores` 用表里的 meta（附录 C 已经给每个模块分好了），加进 `BUILT`（0.12.0 起是 meta 的列表，不再是个数：0.12.0 `{0, 7, 8}`，0.13.0 `{0, 2, 3, 4, 5, 7, 8}`，0.14.0 `{0, 1, 2, 3, 4, 5, 6, 7, 8}`）；`BlockModuleCore.createNewTileEntity` 加分支；`tools/NexusTextures.java` 生成 `module_core/<key>/{front,front_on,side,top}`（`registerBlockIcons` 按 `built()` 注册，缺贴图就是紫黑块）。
5. **tile**：`TileX extends TileCityModule`：`moduleKey`、`moduleColor`（CITY §1.1 的主题色）、`build`、`layout`、`coreMetaOf`、`dissolves`、`passes`（只放人碰不到的格子，或像穹顶发生器的腿那样不托任何人站的地面、穿过去就是设计的——写明是例外并在 tile 的测试里钉住，§4.6）；几个形状的覆写 `shapes()`/`tries()`/`previewPhase()`（照 `TileDomeGenerator`）。
6. **登记**（`CityModule`）：preInit `registerTileEntity(TileX.class, "fluxecho:<key>")`；init `registerTileClass(TileX.class, NexusModule.infoContainer("<key>.structure"))`、`previewModule(core, meta, TileX::new)`、`ModuleSpecs.register(new ModuleSpec(family, key, research, W, D, H, sites, Parts.moduleCore(meta), colour, XShape::plan, XShape::controller, enabled))`（场地偏好照 CITY §2.2；轴上的族用 13 个参数的构造器给 `Envelope`，照 `CityModule.domeGenerator()`/`root()`；亭子用 `CityModule.pavilion(key, research, height, sites, meta, colour, plans, controllers)`，照 `arcaneCourt()`）；postInit 显化 `<key>_core`。
7. **研究**：`ResearchTree` 加节点（CITY 附录 B 的分支、相位、前置；`ResearchTreeTest`：坐标离边 ≥ 16、间距 ≥ 24；相位 ≤ `PHASE_NOW` 的要有算力和材料，占位成本用该相位的外壳板和电路：EV 钛/`circuitEV`、IV 钨钢、LuV 铬、ZPM 铱、UV 锇）、`Research` 常量、lang `research.node.<id>` 和 `.desc`；`research all` 只给本版本能做的。0.13.0 起星图还要过三条（`ResearchTreeTest`）：每个节点的名字（按两份 lang 的真实宽度，`textWidthsAreMinecrafts` 钉住 Minecraft 字体的宽度）在 10 种界面尺寸（320×240 到 1920×1080）下都不碰别的星、别的名字和分支名、不出页眉页脚（间隔 0.5 像素，`starsAndNamesKeepOffEachOther`；英文名比中文宽得多，挤的是英文）；两颗星之间的连线不穿过第三颗星（`noLinkRunsThroughAThirdStar`，只有 `phase_2` → `dome` 那条主干例外）。0.13.0 为了放下四座亭子的星挪了 `echo_crystal`、`library`、`library_reach`（nexus.md §5）。0.14.0 再加一条：名字在中轴左边或右边的星座，它的每颗星都在名字那一侧（`everyStarStandsOnItsConstellationsSide`：星云画在星的中间，不能跨过中轴）；为了放下谐振塔挪了 `hunting_ground`（(316, 146) → (322, 152)）。
8. **渲染**：`XRender`（照 `EcoDomeRender`：本地坐标的表一次算好、按档位画、每部分单独失败、守预算）在 `CityRender.draw` 的模块画面那一段加分支；新的纯规则放 `ModuleLook` 或自己的 logic 类（照 `DomeLook`、`RootLook`）并写测试。要画到 512 格的地标照 `DomeGeneratorRender.landmark`（或 0.14.0 的 `ResonanceGateRender.landmark`）在 `CityRender.landmark` 里加分支，并像 `DomeLook.landmarkQuads`、`GateLook.landmarkQuads` 那样在测试里钉住预算。单元由画布 `Q` 组成的，画布上的画是通用的（§4.10 第 6 条，`CanvasRender`），不用自己画，预算测试把 `CanvasLook.quads` 加上（照 `VaultLookTest`）。
9. **lang 两份**：`tile.fluxecho.module_core.<key>.name`、`fluxecho.<key>.tip`、`fluxecho.<key>.structure`、`fluxecho.module.<key>`、单元种类、研究；阶段用现成的 `build.stage.c<n>`（还是 9 段的话不用新键；阶段不一样的给自己的前缀，照天顶的 `z<n>`、根的 `r<n>`，在 `NexusGui.stageKey` 里按 spec 的族选，§4.3）。运行时拼出来的键（阶段、`why` 原因、场地名）`LangTest` 查不到，照 `TileRootTest.itsRunTimeKeysAreInBothLangFiles` 自己写一个。
10. **不是大厅族**：先在那个批次里把族做出来（CITY §2.1 每个族要给：外形 → `footprint`/`siteBox`、控制器位置和朝向、场地地面、清理盒（往上、往下或空中）、keep-out、按相位开放的个数）：`BuildPlan` 的计划和控制器工厂、`ModuleSpec.footprint`、`TileNexus.refreshDock` 的收编（大厅位按 `CampusPlan.moduleCentre(site, depth)` 找，轴上的场地按控制器格和朝向精确认，`campusSite`）、`CampusPlanTest`/`SitePlanTest` 的断言、总图。0.13.0 的亭子族是地面上的例子（§4.13：控制器在八角中心、正面沿 A、清理外扩一圈、按行切的 keep-out 盒子、对角位按中心 ±3 停靠、名牌在中心）；0.14.0 的大门族是跨在路上的例子（§4.14：控制器在路中间的亭里、通道不清、keep-out 按计划真正动的列和最低高度切、带高度的盒子、`Campus.boxedSite` 跳过它的外框、按控制器格精确停靠、光桥和牌子挪到门楼前面）；0.12.0 的天顶族和根族是轴上的例子（§4.12）：`ModuleSpec.Envelope`（占地、带高度的 keep-out、保护盒、`noRoom`）、空中建造和影子、沉箱的 `DIG`/`DRAIN` 和带、地下投影的透视。
11. **随批次一起**（CITY §5.2）：改了已有的计划就提 `BuildJob.V`；任务书（`build_quests.py` 的 `echo_nexus()`，每座建筑一个任务，key 按模块键）；README 的表、CHANGELOG、两份 lang、这篇手册和 campus.md/nexus.md、BLUEPRINT §3.14、`CLAUDE.md` §5；回复里说清楚哪些只能进游戏看。

## 8. 测试

| 测试 | 守住什么 |
|---|---|
| `logic/ShapeKitTest` | 图例唯一且完整（和 `kit.py` 一致）、软格的孪生字符放同一个部件、内境门位（墙换成软格、不用镜像、不是墙就抛）、螺旋就是档案馆的（一格一级、半格、整格墙板）、套件门、角度和八分、边界和球壳、行走走半格台阶和光梯（不走整格台阶）、单元要有格子 |
| `logic/SitePlanTest` | 场地族和内城场地（10 个族；HALL 相位 I、PAVILION 相位 II 开放）、`siteplan.py` 的数字、内城 `problems: 0` 和间隙、外形属于自己的族 |
| `logic/UnitLayoutTest` | 槽号一个单元接一个单元、一格只属于一个单元、单元的样本座、生态穹的种植床 |
| `logic/EcoDomeShapeTest` | 和生成器逐格一致（整层 CRC `2037949574`）、计数、内境门位的 12 格是软格、不在蓝图里、方块还是原来的墙、跟墙一起建（`theGateNicheIsSoftAndStillBuilt`）、除树干螺旋外对称、尺寸、控制器和套件门、树干和晶核座、12 个种植床、每个字符是图例部件、每格有阶段、FIT 里树先、池水在池沿后、鼓座的檐口带完整（55 格，`theDrumsCorniceIsWhole`） |
| `logic/EcoDomeWalkTest` | 1,179 个状态（和生成器一样）、眼睛高度的控制器、每个种植床、两座岛有人站、能穿过的格子够不着 |
| `logic/ModulePlanTest` | 档案馆的计划和 0.10.1 逐格相同（12 个校验和）、HARD = 检查格（2,777、AIR 48、4 朝向 × 3 场地，控制器朝中枢、地基中心是停靠找的中心）、步骤不重复且阶段往前、种植床是连续的组（15 格，回廊的两个 13 格）按单元顺序、清理盒 1,073 根到 Y0+23、FLOOR 是场地地面加花园、种子装饰（三种雨棚深度、四种附属、都在蓝图外、确定）、阶段内容同 CITY、门位每个场地和朝向都是 12 个 SHELL 阶段的 SOFT 步骤、部件就是鼓座的墙（`theGateNicheIsBuiltSoft`） |
| `logic/PartsTest` | 0.10 的码不变、0.11.0 的码（9、39、40、53、64+）、族和 meta、水/草/泥土只花 EU、模块核心的键 |
| `logic/ModuleLookTest` | 墙裙光线在墙裙外侧、门洞没有、门位的软墙裙下照样有、从正面中间往右绕；名牌在雨棚上方；档位；样本座的样本越过池沿看得见（0.3–24 格）；涟漪留在水上（池的 28 格，余量 0.45–0.8）；认蜜蜂 |
| `logic/ModuleMapTest` | 生态穹一格 3 像素放得下、三层和各自的种植床、每格在图内且单元不重叠、点任何一格选中它的单元、图的方向（门在下、右手在右）、每格显示站在那里的东西、站着的人在脚下那层 |
| `logic/UnitBrowseTest` | 选择检查和夹紧、每页每个单元出现一次、箭头循环 |
| `logic/PreviewSitesTest` | 中枢城市预览：相位 I 是后面的档案馆和右边的生态穹、场地都被占了的模块不立（不占名额，后面的照样立）、两座模块不会在同一个场地、内环槽和场地分开记（没 spec 的种类用槽，内环满了只影响它们）、名额用完就停 |
| `city/CityGuiTest` | 格子每次访问都按显示的单元找槽、选择检查和循环、超过槽数的格子什么都不做、客户端的格子只预测、三页的布局放得下 |
| `city/TileEcoDomeTest` | `Blueprint.world` 是 StructureLib 的映射、控制器和控制台座、每个样本座是自己的槽、每格属于自己的种植床、树干隐藏但实心、`local` 是 `worldOf` 的逆、引擎建好的穹顶的框架就是计划的 |
| `logic/DomeGeneratorShapeTest` | 吊舱和 `dome_pod()` 逐格一致（整层 CRC `4099585315`）和计数（检查 1,027、软 276）；门洞的玻璃（96）和井口（4）是软格、不在蓝图里；控制器、光梯台和 7 个守护台；从光梯台走到控制器和每个守护台（生成器原样 293 个状态，建的 269）；腿是生成器的前三条镜像（CRC `2025965872`，234 格）；两个蓝图（系泊 1,261、浮空 1,027）；吊舱每格有阶段；`Envelope`（占地、腿柱的带高度盒子、保护盒、Y0 ≤ 203）；腿避开广场的光井、补给口和回收箱；腿下的广场留在园区计划里 |
| `logic/ZenithPlanTest` | HARD = 系泊形状的检查格（1,261，4 个朝向）、1,637 步；阶段内容和顺序（腿、舱底、外壳、屋顶、守护台成组、控制器最后）；清理（腿柱只到腿的高度、吊舱的盒子到 Y0+52、不平整）；只认天顶场地 |
| `logic/DomeLookTest` | 弧肋穿过每个腿格、脚在广场、头碰舱底、渐细；电弧落在穹顶上、往外往上弓；穹顶半径 64/104/128/160；顶上的六边形在球面上、会闪；穹顶和地标在每个距离、每个半径都在预算内（≤ 400）；quads 的计数就是画法（`PIECE` 8）；地标在 512 前 64 格淡出 |
| `logic/RootShapeTest` | 井和 `tidal_well()` 逐格一致（CRC `996641253`）、计数（检查 3,268、软 238、空气 1、3,507 步）；深根和地脉核是生成器的（CRC `2817673059`、`21006675`，检查 5,028、5,826）；井盖（中心是空气）、井壁、井底板；小岛、光梯台、控制器和 8 个汲口；从光梯台走到控制器和每个汲口（414 个状态，坡道顶在 y21）；环够不着；每格有阶段；`Envelope`（没有 keep-out、保护盒 Y0−26..Y0−1、Y0 ≥ 32） |
| `logic/RootPlanTest` | HARD = 井的检查格（3,268，4 个朝向），步数 = 检查 + 软 + 空气 + 2 × 7,896；阶段；沉箱从井盖一层层往下、每层先壳后掏、掏之前上面那层的井壁已经立着；掏过的格子知道后面放什么（`placedAfter`）；带从阶段里面开始；只认根的场地 |
| `logic/TideTest` | 午夜最高、正午最低；月相定幅度（大潮 ±20%、小潮 ±5%）；一天平均正好是平均值；曲线不跳 |
| `logic/RootLookTest` | 环不碰人（小岛和桥上的头、坡道）；环随潮汐（上面的晚、中间的反转）；肋的线在墙上；通量层带朝井里；格栅带就是广场的格栅 |
| `logic/XRayTest`、`logic/XRayAirTest` | 透视的棱：立方体 12 条、一排一个盒子且重复只算一次、L 形的凹角、沿棱相碰的格子在那里折、井的轮廓；悬空：吊舱悬空、腿不算，影子是吊舱的八角形 |
| `logic/AxisModulesLookTest` | 吊舱两块名牌（控制器上方、舱底下）、井一块（控制器上方）、大厅还是雨棚上一块；吊舱的平面图有光梯台和守护台；井的平面图画步道那一层（汲口嵌在池沿里）；轴上场地的名字（天顶、根位） |
| `city/TileDomeGeneratorTest` | 两个蓝图按 StructureLib 的映射摆格；控制器、控制台座和守护台（守护台的控制台座属于单元）；`local` 是 `worldOf` 的逆、一直到腿上；腿和节点溶解且能穿过；先系泊、相位 IV 起才试浮空；引擎建好的发生器的框架就是计划的 |
| `city/TileRootTest` | 蓝图映射；控制器、控制台座和汲口；`local` 是 `worldOf` 的逆；井是唯一的形状、环能穿过；沉好的井的框架就是计划的；spec 在地面上什么都不占；运行时拼的键（阶段 `r0–r4`、`why.depth`、模块和汲口的名字、相位合金和共鸣玻璃）两份 lang 都有 |
| `logic/PavilionKitTest`（0.13.0） | 亭子的辅助函数和 `b_pav.py` 一致：八角范数（−15..15 的方块上逐格的 CRC `33328030`、和 11824）、平面 481 根和厚边 96 根、四个切角门 40 根、`shell`（各座亭子的墙高、壁柱节奏、窗的层，按生成器每层的校验和）、中心控制器和后墙门位、螺旋、行走起点在门外 |
| `logic/PavilionFloorTest` | 亭子的场地地面：apron 140 + 林荫道 44（共 181）、林荫道的亮虚线中线、人字纹和门槛、和建立园区的地面、大厅和别的亭子的地面都不重叠；正面朝 A、停靠的 ±3 判定；场地按行切成 42 个盒子正好装下 662 格 |
| `logic/PavilionPlanTest` | 秘法庭的计划：HARD = 检查格（2,058，4 朝向 × 4 个对角位）、控制器在中心朝 A、步骤不重复且阶段往前、清理是八角外扩一圈到 Y0+35、FLOOR 是 apron、林荫道和符文环、单元成组按槽号、门位是 SOFT、附属在 apron 上、任务不出场地、阶段内容同 CITY、总图的牌子在林荫道上门前、没加载的模块的光桥到它的门（`siteDoor`） |
| `logic/PavilionModulesPlanTest` | 另外三座的计划，同上（HARD、控制器、阶段、清理、FLOOR 是 apron + 林荫道 + 里面的地面、单元成组夹在各自的配件之间、门位、不出场地），加猎场的看台一圈圈往外建、桥最后（`order`） |
| `logic/ArcaneCourtShapeTest`、`BloodCovenantShapeTest`、`HuntingGroundShapeTest`、`ManaFountainShapeTest` | 和生成器逐格一致（CRC `1506882503`、`1871731853`、`4232271297`、`4155736375`）、计数、25×25 和各自的高度、沿 x 对称、控制器（祭坛、祭台、发射器、方尖碑）、四个切角门、各自的体量（尖塔和塔尖、三层和天眼、竞技场看台和天篷、回廊）、门位是软格且照样建、每个字符是图例部件、单元、每格有阶段（猎场：平面图停在竞技场的地面） |
| `logic/ArcaneCourtWalkTest`、`BloodCovenantWalkTest`、`HuntingGroundWalkTest`、`ManaFountainWalkTest` | 712、752、740、652 个状态（和生成器一样）；眼睛高度的控制器（血契坛从祭台上看）、每个单元、每个门走得过；能穿过的格子够不着（血契坛：没有）；猎场每圈看台都有人站 |
| `city/TileArcaneCourtTest`、`TileBloodCovenantTest`、`TileHuntingGroundTest`、`TileManaFountainTest`（共用 `PavilionTileChecks`） | 蓝图按 StructureLib 的映射摆格；控制器和控制台座；每个样本座是自己的槽、每格属于自己的单元；溶解的格子能穿过（血契坛没有）；`local` 是 `worldOf` 的逆；引擎建好的亭子的框架就是计划的；spec 让园区离开 apron 和林荫道，整修园区不碰立着的亭子；运行时拼的键两份 lang 都有。秘法庭另有：Y0 ≤ 223 才放得下、没有别的模块挡对角位、园区分得清亭子和老内环槽上的模块；血契坛、猎场、泉阵另有平面图（祭台和两张只看建筑的层、竞技场和看台、庭院） |
| `logic/ArcaneLookTest`、`BloodLookTest`、`HuntingLookTest`、`ManaLookTest`（共用 `LookCells`） | 画面的纯规则：墨环离尖塔有余量、高度倾角速度各不同、三个缺口、晶体在塔尖溶解的地方、符文在地面亮环上、光点留在大厅的空气里、气泡在池沿上、墨迹在空心尖塔里沉；宝珠在天眼下、光线从天眼边到宝珠、血珠落在祭台上并从名牌后面过、檐口的光在檐口外面、仪式圈、脉冲不进方块、宝珠座的小宝珠、一拍两下；光篷合拢在碗上离墙、环画在天篷的位置、六边形铺满、每 6 秒一扫、尾迹、剪影在印记台上、每种怪的种类；泉柱、光流从样本座到泉柱、魔法阵在空地上转、火花落进花园；动作都会循环；四座都守预算（共用画面加自己的，近 ≤ 2,000、中 ≤ 300、远 ≤ 64） |
| `logic/ResonanceGateShapeTest`（0.14.0） | 和 `resonance_gate()` 逐格一致（整层 CRC `1079565838`）、计数（检查 2,370、软 268、空气 136）、31×17×18、除两座楼梯的 32 格外沿 x 对称、路中间亭上的控制器、通道敞着（只有亭和路没铺到的地面）、桥和核心柱、门洞空气、10 个单元按槽号、没有门位、每格有阶段（`everyCellHasItsStage`：翼楼 y9–17 全在 UPPER，ROOF 只有桥顶 91 格）、场地和建造 |
| `logic/ResonanceGateWalkTest` | 932 个状态（和生成器一样）；站在路上看控制器在眼睛高度、每个单元；两座门厅、楼梯的每半格、两边楼上、谐振室 58 个站的格子，最高站高 9；溶解的核心柱只多开出它自己那一格（§4.6 的第二个例外） |
| `logic/GatehouseSiteTest` | 外形 527 格、通道 13×17、大门路和四根塔柱在通道里；通道地面 58 格、不碰路；apron 的地面只增（208 格去掉路的 14 格、贴着翼楼是 TRIM、同种子同计划，不碰建立园区的地面、环道、底座）；任务占的地面（两翼 306 格加通道地面和 apron）；`GATE_DOOR`、`GATE_SIGN`、`GATE_HEADROOM`、`siteDoor(0, …)` |
| `logic/GatePlanTest` | 形状在门楼外形里；HARD = 检查格（4 个朝向）；控制器在亭上、朝中枢；步骤不重复且阶段往前；桥下的通道从不清（只清桥的 91 根、从 Y0+7 起）；大门路和塔柱还是建立园区的；FLOOR 是 apron、门厅和通道地面；keep-out 正好是任务动的列和高度（整列和带高度的盒子）；铺设广场照样修路、立塔柱；单元按槽号成组；附属在 apron 上；牌子和光桥在门楼前面 |
| `city/TileResonanceGateTest` | 蓝图按 StructureLib 的映射摆格；亭上的控制器和控制台座；每个样本座是自己的槽、单元的种类；核心柱溶解且能穿过；`local` 是 `worldOf` 的逆；引擎建好的门楼的框架就是计划的；spec 是大门族的（占地、keep-out、保护）；整修园区不碰门楼动的地面、照样在通道里铺路、立四根塔柱；平面图两层；园区分得清门楼和老内环槽上的模块（`theCampusTellsTheGatehouseFromARingModule`：客户端没加载时，只有 0 号位上 `BUILT` 的大门族模块才算门楼——光桥到 `GATE_DOOR`；还在建的、别的模块、记在别处的、亭子的、没有列表的都照旧画环桥）；运行时拼的键两份 lang 都有 |
| `logic/DualFoilVaultShapeTest` | 和 `vault()` 逐格一致（整层 CRC `4211763960`）、计数（检查 4,644、软 837、空气 48、decor 14）、27×35×27、沿长厅对称（两座楼梯也是）、控制器和套件门、方碑、正面画布和锯齿屋顶、走道横廊和楼梯、门位是软画布 `$` 且不在任何单元里（`theGateNicheIsSoftCanvasInNoUnit`）、42 幅画布和二向箔（131 格）、每个字符是图例部件、每格有阶段、FIT 里桌子、全息底座、锚点的先后 |
| `logic/DualFoilVaultWalkTest` | 1,484 个状态（和生成器一样）；眼睛高度的控制器、每幅画布和二向箔；每层画布从它自己那层楼面用；两座楼梯的每半格、两层走道、横廊 19 × 2，最高站高 17 |
| `logic/VaultPlanTest` | HARD = 检查格（4 个朝向 × 3 个场地）；门位是 12 个 SHELL 阶段的 SOFT 步骤、部件是画布；步骤不重复且阶段往前；单元按槽号成组、在桌子之前；FLOOR 是场地地面和长厅的地面；种子装饰；阶段内容同 CITY |
| `city/TileDualFoilVaultTest` | 蓝图映射；控制器和两个控制台座（都开窗口）；每幅画布和二向箔是自己的单元；潜行空手点画布任何一格都取回它的物品（`takeSlot`）；什么都不溶解；`local` 是 `worldOf` 的逆；引擎建好的仓的框架就是计划的；spec 是大厅族、场地 6、2、4；平面图三层（1/9/17）和二向箔（`theWindowsPlanShowsTheTiersAndTheFoil`：挂在墙上的二向箔在平面图上是 13 格宽的一整条，点中间也选得中）；运行时拼的键 |
| `logic/CanvasLookTest` | 每个画布单元有一块画面、画面贴在画布上朝着长厅、二向箔的画面是框里的整块、画在底色正中、网格沿画布格走、画色调分离到它的级数、明度和饱和度是台阶、描边围着形状、图标取第一帧且最多 32 像素、平均色只算不透明的像素、拼贴的一块是不属于单元的整块画面 |
| `logic/VaultLookTest`、`GateLookTest` | 画面的纯规则：拼贴在正面画布上、最多的排最前；光柱只穿过长厅的空气、停在楼面或墙上，亮度跟一天的时间；事件视界绕着二向箔、在框里面；扫光和亮框在正面上；尖塔浮在桥顶上、碎晶绕着它的腰转、碰不到它也碰不到门楼；每 8 秒一道脉冲、有电快一倍；光柱穿过溶解的核心柱；动作都会循环；墙裙光线也沿通道走（92 个面）；两座都守预算（仓 883/216/32，门楼 837/280/46，地标 ≤ 400） |
| 光梯、生长 | `LiftShaftTest`、`LiftInputTest`、`LiftLookTest`、`GrowPlanTest`：见 nexus.md §10 |
| 改过的 | `LangTest.everyChatMessageGetsItsArguments`（§4.8）；`ResearchTreeTest.theEcoDome`（0.11.0 时 `PHASE_NOW` 1，0.12.0 起 2）；`PartRecipesTest.theCitysPartsCost` 和部件表；`CampusPlanTest`（对角中心 ±36）；`TerrainRuleTest.aRepairLaysGroundOnlyWhereNothingIs`；`CampusEngineTest.aRepairKeepsTheGarden`、`aFullRingIsNamed`；0.12.0：`PartsTest.theCodesOf012`（码 41、光 12）和模块核心的 meta 7、8（`built()` 按 meta 排序，二向箔仓还没有）；`PartRecipesTest.theLiftPadCosts`；`ShapeKitTest`（`^` 是光梯台，`Q` 照样抛）；`SitePlanTest`（天顶和根各一个场地、`theAxisModulesTakeNoRingRoom`）；`PreviewSitesTest.axisKindsTakeNoRoom`；`BlueprintTest.theBoxHoldsEveryCellAndNothingPastIt`（`inBox`）；`GhostCellsTest.theIndexFindsTheStepThatPlacesABlock`；`TerrainRuleTest.theCaissonDigsAndDrains`；`CampusEngineTest` 的沉箱、勘测、散落和生长几条（campus.md §15）；0.13.0：`PartsTest`（meta 2–5、码 66–69、`built()` 是 `{0, 2, 3, 4, 5, 7, 8}`）；`ModuleLookTest.aCentralPlansPlateFloatsOverItsCentre`、`thePavilionsPlatesFloatOverTheirCentres`、`theSharedDrawingsQuadsAreCountedByTier`（扫光 19/17、`baseQuads`）；`PreviewSitesTest.thePavilionsTakeTheDiagonals`（偏好列表、相位 III 的 8 + 2 个场地、相位 II 只立两座亭子）和 `thePreviewStaysWithinItsBudget`（预览的检查格每个相位 ≤ 60,000）；`ResearchTreeTest.theArcaneCourt`、`theOtherPavilions`、`textWidthsAreMinecrafts`、`starsAndNamesKeepOffEachOther`、`noLinkRunsThroughAThirdStar`（§7 第 7 步）；0.14.0：`ShapeKitTest`（图例里有 `Q` 和 `$`，画布墙的门位变成 `$`）、`UnitLayoutTest.aUnitHungOnAWallStandsOnTheFloorItIsUsedFrom`（`usedFrom`、`base` 和楼层，楼面高过最低一格或名字不对就抛）、`PartsTest.theCodesOf014`（画布 deck 12 / 码 28、光 0，meta 1、6 和码 65、70 有模块）、`PartRecipesTest.theCanvasCosts`、`ResearchTreeTest.theResonanceGate`、`theDualFoilVault`、`everyStarStandsOnItsConstellationsSide`（左右两边星座的星都在自己名字那一侧，谐振塔在回响这边）、`PreviewSitesTest`（谐振塔和二向箔仓进预览的预算、它们的格子数、二向箔仓的偏好 {6, 2, 4}、十种模块各占一个场地）、`AxisModulesLookTest`（0 号位的名字键是 `build.gui.site_gate`）、`PavilionPlanTest`（`siteDoor(0, true)` 是 `GATE_DOOR` 22.5）、`CampusEngineTest.theGatehouseLeavesItsRoadAndPylonsToTheNexussJobs`（大门和轴上的场地一样是 `Campus.boxedSite`：建造器不保护门楼整个 31×17×18 的外框，只保护它场地自己的盒子——整修园区在门楼下铺的大门路地砖和四根塔柱都在外框里、但不在任何保护盒里，门楼自己计划的每一块都在） |

全仓 149 个测试类、1,031 个测试（0.14.0：新的 12 个类 105 个，改过的 `ResearchTreeTest`（+3）、`CampusEngineTest`、`PartRecipesTest`、`PartsTest`、`UnitLayoutTest`（各 +1）多了 7 个；0.13.0 是 137 个类、919 个——新的 20 个类 177 个，改过的 `ResearchTreeTest`、`ModuleLookTest`、`PreviewSitesTest` 多了 10 个；0.12.0 是 117 个类、732 个，0.11.0 是 101 个类、623 个，0.10.1 是 88 个类、536 个）。

## 9. 已知问题、只能进游戏看的

- **已知问题和与方案的出入**：
  - 样本座物品的上限按模块算：每座模块各自最近 256 个、24 格内，不是整帧共用一个预算（整帧计数会破坏「光门后面同一帧再画一遍」的幂等规则）。
  - M8 的「分页同步」实际是客户端分页：样本整份在描述包和增量包里（`BookPacket` 的预算，< 28 kB），窗口只同步选择和状态。
  - Shift 点一格一个填进空格；没有档案馆那样的还书口；超过单元槽数的格子关着，服务端也不收。
  - 覆层锚点只有贴图上的六角插板，没有额外的几何。
  - 池沿的唇边 10/16–12/16 高出碰撞箱 2/16（同栏杆的臂）。
  - 样本座各块每个面都画，上下贴着不透明方块时凹槽的上下面也不剔。
  - 停靠状态在下面那条里，不在标题里。
  - 拆模块核心时样本掉在核心处，不进队伍仓库（档案馆的书进仓库）。
  - 生态穹没有单独的配置开关（`ModuleSpec.enabled` 恒真）。
  - 内环容量：相位 I 只有 2 个位置（`RingSlots.open(1)`），档案馆和生态穹正好占满；停靠着的 0.9.2 大厅也算一个（campus.md 的规则），另一个模块就显示「内环已满」，拆下大厅核心或长到相位 II（4 个位置，`RingSlots.open(2)`）才有位置。天顶和根不占内环（§4.12）。亭子占内环（0.13.0）：相位 II 的 4 个位置里档案馆和生态穹占两个，只剩两座亭子的位置，四座都建要等相位 III（0.15.0，8 个位置）。谐振塔和二向箔仓（0.14.0）也占内环、也争这两个位置（下面 0.14.0 那条）。
  - 单元只能由构架、配件和（0.14.0 起）画布组成（`UnitUse` 登记在构架、配件和地板方块上，地板里只有画布当单元格用）。
  - 树干在光之树里是一根看不见但实心的柱子（设计如此：它撑着两层岛面）。
  - 只有 meta 0–8 有模块（`ModuleCores.built()`，0.14.0 起；0.13.0 是 0、2–5、7、8，0.12.0 是 0、7、8）：别的 meta 的核心（只能用命令拿到，如 9 智子阵列）放下没有 tile，图标是生态穹的（`BlockModuleCore.built(meta)`）。
  - `covers()`：0.11.0 是宽松的立方体（生态穹 ±36）；0.12.0 起城市模块只罩它每个形状绕控制器摆好的盒子（`TileCityModule.covers`、`Blueprint.inBox`：生态穹是它自己的盒子，天顶是腿和吊舱合起来的 29×49×25，根是 25×26×25），所以旁边的残留算散落（campus.md §5）；档案馆照旧是立方体（campus.md §16）。
  - 窗口里选择变化后一个往返内点格子，作用在新单元上（之后整窗重发，显示对齐，不丢东西）。
  - **0.12.0**：
    - 回响穹顶发生器和潮汐井现在什么都不做：守护台和汲口只保管样本，穹顶、潮汐和功能核心 0.19.0 起；停靠有电时照样每 tick 扣 128 EU（`moduleUpkeepEuPerTick`，和生态穹一样）。潮汐曲线已经在画（环、池、格栅），但不影响任何数值。
    - 吊舱只能坐光梯进：八个门洞用玻璃封着（等 0.16.0 的栈桥）、光梯井的井口盖着（等轨道锚），都是软格。`floating` 形状只在中枢长到相位 IV 才试，这一版中枢只能长到相位 II，所以吊舱一直系泊；释放任务（拆腿、浮起来）0.16.0 才有。
    - 根的深根和地脉核只是数据（`RootShape.deepRoot()`、`leyCore()`），`TileRoot` 只有 `well` 一个形状。
    - 根的任务步数是 19,299：进度条的总数和「已放/总数」包括沉箱的 15,792 步 `DIG`/`DRAIN`（掏空的格子和空着的格子走得很快），剩余时间不算它们。
    - 天顶的腿按 1.5 倍节奏放：`BuildPace.modulePace` 按阶段号给 `M_FLOOR`（1）加速，天顶的阶段 1 正好是 LEGS（巧合，不是设计）。
    - 天顶的 `covers()` 是 29×49×25 的盒子（腿和吊舱合起来），根是 25×26×25（上面 `covers()` 那条）；城市模块不再用 `reach()` 的立方体。
    - 轴上的模块只按控制器格和朝向精确停靠（`TileNexus.campusSite`）：手放的核心偏一格就不停靠（引擎放的总在那一格上）。
  - **0.13.0**：
    - 四座亭子现在什么都不做：单元只保管样本（印记、宝珠、花……结构阶段任何物品都收），功能 0.19.0 起；停靠有电时照样每 tick 扣 128 EU。猎场「有电」只是更亮，怪在竞技场里演出要等功能。
    - 内城上限 8 只做了准备：`TileNexus.MAX_CAMPUS_MODULES` 还是 7，中枢这一版只长到相位 II（内环 4 个位置），档案馆和生态穹都在时同时最多两座亭子（上面「内环容量」那条）；哪两座看研究的先后：`autoJobs` 按登记顺序（秘法庭、血契坛、回响猎场、魔力泉阵）提供已研究的，提供出来的任务还没按「开始」就占着位置，取消一座才轮到下一座；场地按各自的偏好列表定，NEI 的中枢预览在相位 II 按登记顺序立秘法庭和血契坛。
    - 秘法庭 33 高：中枢的底座层 Y0 在 224–225（放核心允许到 225）时建不了，「建造」页写 `build.module.why.height`（§4.13）；其余三座和所有大厅没有这个限制。
    - 亭子只在八角中心两轴 ±3、离底座层上下 ≤ 1、正面正好朝 `pavilionFront` 时停靠：手放的核心朝向不对（亭子的蓝图只认沿 A 的朝向）就不停靠；引擎放的总是对的。
    - 账单：CITY §4.3–4.6 和 `report.py` 的 EU 只算形状，比建造页少场地地面那 181 格；它们的用料是 `report.py` 自己的估算，和建造页的材料单对不上（石砖反而更多，如秘法庭 821 对 ≈777），都以建造页为准（§6.4–6.7）。
    - 秘法庭 FAR 的预算是 62/64（`ArcaneLook.quads` 45 + `baseQuads` 17），再加东西要先减；名牌按 `ModuleLook.PLATE_QUADS` 80 算（面板和两条线 15，两行最多 65 个字、一字一个），名字更长就比算的多。
    - 猎场的全息是平面剪影、不是实体模型（CITY §4.5）：颜色取刷怪蛋，没有蛋的怪（或样本认不出怪）是中性的钢灰和青色；种类按注册名里的词猜，猜不出就画人形。
    - 没停靠的亭子只画成型的那一样（晶体、宝珠、光篷、泉柱），亮度 0.3；停靠后的画面（墨环、渗光、扫光、光流……）要停靠才有。
    - 照生成器、和 CITY 的文字不一样的地方：泉阵玻璃屋顶的肋是墙板 `W`（CITY 写亮肋；`ManaFountainShape` 那段注释也写亮肋）；血契坛祭台中间、控制台座下面那格空着；泉阵 8 高（生成器分配 9 层）；CITY §4.5 说猎场是最矮的亭子，实际猎场 10 高、泉阵 8 高。
    - 总图的牌子：对角位上立着一座不是亭子的模块（0.9.2 的老内环槽）时，牌子退回八角中心、高 3.0，轮廓照旧（§4.11）。
  - **0.14.0**：
    - 谐振塔和二向箔仓现在什么都不做：单元只保管样本（结构阶段任何物品，画布和二向箔各一件），谐振柱的加速和并行、画进画布的大量存储、二向箔的销毁 0.19.0 起；停靠有电时照样每 tick 扣 128 EU。
    - 内环：相位 II 的 4 个位置里档案馆和生态穹占两个，剩下两个由四座亭子、谐振塔和二向箔仓按 `autoJobs` 的登记顺序（秘法庭、血契坛、回响猎场、魔力泉阵、谐振塔、二向箔仓）争，提供出来还没按「开始」的任务也占着位置，所以四座亭子都研究了的话，谐振塔和二向箔仓排在最后，要取消前面的任务才轮到它们；八座都建（含大门）要等 0.15.0（相位 III、`MAX_CAMPUS_MODULES` 8）。NEI 的中枢预览同样到相位 III 才立它们。
    - 照生成器留下的怪处：通道里 A 37–39、`|R|` ≤ 5 的格子两边都不铺——大门路在 A 36 结束，生成器（和照它写的 `gatePassageFloor`）把 A ≥ 34 都算成路的加宽，所以路尽头外面这几格既不是建立园区的路、也不是门楼的地面，保持建立园区留下的样子（`GatehouseSiteTest.thePassageFloor` 钉着 (38, 4) 不铺）。
    - 门楼立着时整修园区或铺设广场：碰到门楼带高度的 keep-out 盒子的平整柱整根去掉，所以桥下和路中间的亭那几列 Y0+1..6 不清理（玩家在桥下通道里放的东西留着）；大门路的地砖和四根塔柱照样铺、立（塔柱在 (A 27, R ±5)、(A 35, R ±5)，在桥的 A 28–34 外面）。
    - `Campus.protectedBoxes` 里跳过门楼外框的那段循环只有代码审查：`CampusEngineTest.theGatehouseLeavesItsRoadAndPylonsToTheNexussJobs` 测的是 `boxedSite` 和保护盒，不是那段循环本身。
    - 门楼只按控制器格和朝向精确停靠（`TileNexus.campusSite`，同轴上的模块）：手放的核心偏一格就不停靠（引擎放的总在那一格上）。
    - 溶解的第二个例外：谐振塔的核心柱溶解后人能走进那一列、站在谐振室楼面上（§4.6），光柱就从那里穿过。
    - 二向箔的门位 12 格不属于任何单元：点它们不是在用二向箔（`UnitUse` 找不到单元，就是普通的画布方块），点二向箔的其余 131 格才是。
    - 画布的画：只在 NEAR 画整张（每座模块 24 格内最近 256 幅），远一点只是一块底色，MID、FAR 不画画布（正面的拼贴除外）；有的模组图标（模组自己加载贴图的、按名字找不到文件的）读不出来，就从图集直接画（每个渲染 pass 一层颜色，没有色调分离）；动画图标只画第一帧；画是全亮的，不受方块光照；每个画好的图标是一张动态贴图（`DynamicTexture`），池最多 257 张、约 4 MB，换资源包时清空重画。
    - 「存得最多」按存这种物品的单元数（一个单元一件），物品按 id + 损伤值认、不看 NBT（附魔书、药水这类靠 NBT 区分的算同一种）；一样多的按先出现的槽。
    - 天窗光柱是静止的面片，亮度只跟一天的时间走（`World.getWorldTime()`），不看天气，下雨也一样亮。
    - 东西被压扁吸进二向箔、天环 S0 的光柱接到谐振塔尖塔顶上，分别等功能（0.19.0）和天环（0.16.0）。
    - 显化二向箔仓核心要的 `item:minecraft:painting`（画 ×4）这个物品 id 没有测试；解锁消息给 `build.gui.site_gate`「大门」多传了一个它不用的场地号，1.7.10 的格式化忽略多出来的参数——这是照原版的行为假设的，没有测试（`LangTest` 只查参数够不够）。
    - 聊天的措辞：中文「…的投影已出现在%s」去掉了「在」后面的空格（「出现在4 号位」「出现在大门」）；英文有名字的场地读作 "is up on Gate"。
    - 照 CITY、和文字不一样的地方：CITY §4.7 写有电时碎晶「对齐穹顶的边」，实现是在尖塔腰的高度排成一圈水平的环、转得快一倍；CITY §4.2 说二向箔仓（27 高）是内环最高的楼，内环上的秘法庭有 33 高；CITY §4.7 的阶段写 FIT 是「亭、调谐架、谐振柱、核心」，实现照单元顺序先谐振柱后调谐架。
    - 总图 0 号位的老键 `build.masterplan.gate` 不用了（只在没计划时写「预留」），键留着。
- **只能进游戏看**（开着和关着 Complementary 各看一遍）：光之树、呼吸的肋（外面和里面）、全息（作物生长阶段、绕圈的蜜蜂）、花粉、蝴蝶、池面涟漪、成型扫光、墙裙光线（三种停靠状态和未成型）、名牌、样本座凹槽里的物品（越过池沿）、远处的样子（FAR）和帧率是否在预算内；样本座和池沿的区块网格和物品栏图标、池沿连起来的唇边、覆层锚点的六角插板、模块核心的贴图；窗口的样子和手感、点单元打开在「单元」页、Shift 点、热键、平面图和点选；树干在光之树里是实心的；Waila 那一行；「建造」页和总图的「内环已满」；整个建造（投影、阶段、单元整组飞、雨棚和附属、池水）、NEI 的生态穹预览和中枢城市预览。
- **0.12.0 只能进游戏看的**（同样开着和关着光影各一遍）：光梯的手感（跳起、潜行、起步和刹车、视野变宽、声音、穿过中枢和吊舱的地面、撞到东西弹回再落下、没有摔落伤害；见 nexus.md §4.2）和光束；吊舱的穹顶壳（地面的光带、顶上闪烁的六边形，在广场上和 512 格外看，不同相位的半径）；电弧（有电和没电的琥珀色）、晶体和光线；远处的腿（中枢范围外是线、地标里接得上，任何停靠状态都不能看着像浮着）；成型扫光沿腿上升；潮汐环随潮汐起落（午夜和正午、满月和新月）、肋和通量层带的光、池面焦散、广场格栅带的光和高潮时的光刃；沉箱在地下一层层沉下去（先壳后掏、地下水和岩浆、排水、回收箱）、透视投影（地形后面淡、没挡着的亮、当前阶段）、吊舱的影子和垂线；两块名牌（舱内和舱底）；窗口的「天顶」「根位」和平面图（光梯台、守护台、井的步道层）；NEI 的中枢城市预览加了两座模块后的加载时间（7–10 级）、浮空的吊舱（相位 IV 起的预览）；新贴图（光梯、两个核心、相位合金、共鸣玻璃）。
- **0.13.0 只能进游戏看的**（同样开着和关着光影各一遍）：四座亭子的建造（对角位上的投影、清理外扩一圈、apron 和林荫道的地面——人字纹、亮虚线中线、门槛——、按阶段立起、猎场的看台一圈圈往外、附属、池水最后）和成型、停靠（手放核心偏一点、朝向不对）；秘法庭的紫晶体、三道黑墨环（墨是 alpha 混合的，光影包下黑墨和紫色光晕的样子、提笔缺口的收细）、地面的符文环、要素颜色的光点、坩埚的气泡、尖塔里下沉的墨迹，从塔底往上看 30 格的空心尖塔；血契坛的宝珠（呼吸、心跳）、垂下的光线和滴痕、檐口的渗光、仪式圈和脉冲、宝珠座的小宝珠、血珠从名牌后面落到祭台；猎场的六边形光篷（琥珀到青、涌过去的光）、竞技场的扫光和尾迹、印记台上的剪影（各种怪的刷怪蛋颜色、7 种轮廓、没有蛋的中性色）；泉阵的螺旋泉柱、八道光流和光点、转动的魔法阵（从池沿和花瓮下面穿过）、飘进花园的火花、池水和草；四块名牌的位置（中心控制台上方、泉阵在方尖碑顶上）；窗口的平面图（77×77、中心的控制台和核心、血契坛的「高8」「高13」两张只看建筑的层）、「建造」页的模块列表翻页（n/N）、总图对角位的名字和林荫道上的牌子、中枢到亭子内侧门的光桥；新贴图（四个模块核心）；Waila；远处的样子和帧率（秘法庭 FAR 只有 2 quads 余量）；NEI 的四座亭子预览和中枢城市预览（相位 II 两座、相位 III 起四座）的加载时间。
- **0.14.0 只能进游戏看的**（同样开着和关着光影各一遍）：谐振塔的建造（大门位上的投影、只清两翼和桥、桥下的通道连大门路和四根塔柱原样留着、apron 和通道的地面、附属）、成型和停靠（手放的核心偏一格）；浮在桥上的水晶尖塔（慢慢转、上下浮、里面的晶体）、六片碎晶（待机高低错开、有电排成一圈水平的环，`effectRange` 外换成光点）、谐振室升起的光柱和光点、扫过两翼的谐振脉冲（8 秒一道、有电 4 秒）、512 格内的地标和它跟 FAR 接不接得上；走进溶解的核心柱；墙裙光线沿通道两边走；二向箔仓的建造（Y0+29 的清理、长墙上的画布在 FIT 里一组组立起来、门位跟后墙一起建）和成型；画布的贴图（深蓝织纹和淡青网格，区块网格和物品栏图标）；画布上的画（色调分离和笔触的样子、底色、空画布的网格、24 格内整张和远一点的色块、背对的不画、从图集画的模组图标、动画图标、全亮），光影包下的动态贴图；正面的拼贴（最多的在中间、FAR 的色块）、羊皮纸白的成型扫光、天窗光柱和光斑（白天和夜里、往正面倾）、亮框的呼吸和闪光、二向箔的事件视界；潜行空手点画布任何一格取回、点门位那 12 格不算；两个控制台座都开窗口；窗口的平面图（谐振塔两层、路中间亭的控制台座，二向箔仓三层、二向箔的整条）；「建造」页 4 页、总图大门位的两翼、「预留」、门楼前面路上的牌子、聊天的「出现在大门」、中枢到通道口的光桥、终端的「大门」；星图上的两颗新星和挪了的猎场；新贴图（画布、两个模块核心）；Waila；远处的样子和帧率（谐振塔 MID 只有 20 quads 余量）；NEI 的两座预览和中枢城市预览（相位 III 起立它们）的加载时间。

## 10. 安全修改须知

- **内境门位**：门位的格子是软格（CITY §4.15）；别改回检查格（已建的穹顶也照样成型，但 0.19.0 开门就得改检查的形状、提 `BuildJob.V`），也别在门位里放单元。开门的那一版把这些格子换成光门。二向箔仓的门位（0.14.0）在二向箔里，是从这个单元拿出来的 12 格画布 `$`：别把它们放回二向箔（单元格必须是检查格），二向箔的格子数（131）变了已建的仓就不成型。
- **形状**：`EcoDomeShape`、`DomeGeneratorShape`、`RootShape`、四座亭子（`ArcaneCourtShape`、`BloodCovenantShape`、`HuntingGroundShape`、`ManaFountainShape`）、`ShapeKit` 的亭子辅助函数、0.14.0 的 `ResonanceGateShape` 和 `DualFoilVaultShape` 就是生成器（`b_hall.py`、`b_sky.py`、`b_root.py`、`b_pav.py`、`b_gate.py`）；要改先改生成器、重跑 `report.py`，再改移植、重钉校验和；改了检查格，已建的模块就不成型，改了计划放什么要提 `BuildJob.V`（0.12.0 的天顶和根是新计划，没改任何旧计划，所以 V 没提，还是 2；campus.md §4）。穹顶发生器的腿是半边算再镜像的（`StrictMath`、`Math.rint`），别换成两边各算（生成器两边取整不一样，会多出不对称的格子）；门洞和井口的软格（`door()`、`shaftCap()`）等以后的栈桥和轨道锚开，别改回检查格。`Blueprint.world` 的映射要和 StructureLib 逐格一致（生态穹有不镜像的格子）；`ShapeKit` 的 `hypot`/`angle` 换法不能动（和 Python 逐格一致）。
- **永不改、不复用**：单元顺序和每个单元的槽数（`Samples` 按槽号存；0.12.0 的 `ward-1…7` 和 `tap-1…8`，以后根的深根和地脉核要把这 8 个汲口排在最前；0.13.0 的秘法庭 `infusion`、`cauldron-1…4`、`aspect-1…8`、`desk-1…8`（44 槽），血契坛 `cradle-1…8`、`bay-1…4`，猎场 `imprint-1…12`，泉阵 `fountain-1…8`、`urn-1…8`；0.14.0 的谐振塔 `resonator-1…6`（每个 2 槽）、`rack-7…10`（每个 3 槽），二向箔仓 `GL-1…7`、`GR-1…7`、`ML-…`、`MR-…`、`UL-…`、`UR-1…7`、`foil`（各 1 槽，二向箔是槽 42））；`ModuleCores` 的 meta 和 `KEYS`（0.12.0：7 `dome_generator`、8 `root`；0.13.0：2 `arcane_court`、3 `blood_covenant`、4 `hunting_ground`、5 `mana_fountain`，码 66–69；0.14.0：1 `dual_foil_vault`、6 `resonance_gate`，码 65、70）；部件码（9、28、39、40、41、53、64–79，只往后加；光梯是 fitting meta 9，画布是 deck meta 12、码 28）；TE id `fluxecho:eco_dome`、`fluxecho:dome_generator`、`fluxecho:root`、`fluxecho:arcane_court`、`fluxecho:blood_covenant`、`fluxecho:hunting_ground`、`fluxecho:mana_fountain`、`fluxecho:resonance_gate`、`fluxecho:dual_foil_vault`，注册名 `fluxecho:module_core`；研究 id `eco_dome`、`phase_2`、`dome`、`tidal_well`、`arcane_court`、`blood_covenant`、`hunting_ground`、`mana_fountain`、`resonance_gate`、`dual_foil_vault`，显化 id `eco_dome_core`、`dome_generator_core`、`root_core`、`resonant_glass` 和六个 `<key>_core`；单元种类的 lang 键 `fluxecho.unit.<kind>`（`infusion`、`cauldron`、`pillar`、`desk`、`cradle`、`ritual`、`imprint`、`fountain`、`urn`、`resonator`、`rack`、`canvas`、`foil`）；画布的名字 `tile.fluxecho.deck.canvas.name`（`DECK_NAMES` 的 `canvas`）；物品注册名 `fluxecho:phase_alloy`、`fluxecho:resonant_glass` 和矿辞 `ingotPhaseAlloy`、`glassResonant`（研究花费按矿辞写）；场地号（天顶 8、根 9，对角位 1、3、5、7，大门 0：记录的场地和任务键 `module:dome_generator@8`、`module:root@9`、`module:<亭子>@<1|3|5|7>`、`module:resonance_gate@0`、`module:dual_foil_vault@<6|2|4>` 存档）；形状名（`moored`、`floating`、`well`，以后的 `root`、`core`；存在 `Sh` 里）；NBT `Samples`、`Unit`，任务的 `Kr`（带高度的 keep-out）；描述包 `P/Dk/Rv/Ep/B/Bi`；`NexusNet.UNIT_DELTA = 5`、`LIFT = 3`（C→S）、`LIFT_RIDE = 6`（S→C）；`Campus.ModuleStatus` 的 ordinal（`RING_FULL` = 5，只能在末尾加）；`SiteFamily` 的名字；`BuildPlan` 的步骤种类（`HARD/SOFT/AIR` 0–2、`DIG` 3、`DRAIN` 4）；配置键 `nexus.moduleUpkeepEuPerTick`；阶段号和 `build.stage.c<n>`、`z<n>`、`r<n>`、`g<n>` 的对应；lang 键 `build.module.why.<原因>`（存在 `Md` 的 `W` 里）；0.14.0 的 `build.gui.site_gate`（`SiteFamily.labelKey` 运行时拼出来，`LangTest` 查不到，`TileResonanceGateTest.itsRunTimeKeysAreInBothLangFiles` 查）。
- **场地常量**：`DIAGONAL_*`、`GATEHOUSE_*`、`ALLEE_*`、`STREET_*` 是亭子（0.13.0 起已建，改了已建的亭子就不在自己的场地上）、大门楼、井字街的依据，改了要同时改 `siteplan.py` 和 `SitePlanTest`；大厅的常量照 campus.md §17。`ZENITH_R/K`、`ROOT_R/K`、`FLOOR_UP`、`DEPTH` 和两个控制器的位置决定已建的吊舱和井在哪、怎么停靠（按控制器格精确认），改了已建的就对不上；`LOWEST_Y0` 32 和 `CLEAR_TOP` 52 是 `noRoom` 的依据。亭子（0.13.0）：`diagonalCentre`、`pavilionFront`、`pavilionSiteAt` 的 ±3、`PAVILION_DOOR`、`PAVILION_SIGN` 和 `pavilionSite` 决定已建的亭子在哪、怎么停靠、keep-out 罩哪里、光桥和牌子到哪；`pavilionFloor` 的地砖规则、`PAVILION_ANNEX_FROM` 8、清理外扩一圈到顶上 + 3、`Stages.order` 都是计划的一部分——0.13.0 的亭子计划是新的，没改任何旧计划（`order` 默认 0，老计划逐格不变），所以 `BuildJob.V` 没提；以后改了它们就要提。大门楼（0.14.0）：`GATEHOUSE_*`、`GATE_PASSAGE`、`GATE_HEADROOM` 7、`gateController`（盒子中间那一列在路的轴上、前排在 A 23）决定已建的门楼在哪、怎么停靠（按控制器格精确认）、清理绕开哪里；`gatePassageFloor`（照生成器把 A ≥ 34 算成路的加宽，§9）、`gatehouseFloor` 的地砖规则、桥上清理从 Y0+7 起、附属的 `ANNEX_FROM` 6 是计划的一部分；`gateKeepOut` 从计划算出来，改计划 keep-out 跟着变，但它必须一直把通道里只在 Y0 以上动的列写成带高度的盒子，否则园区任务再也铺不了桥下的大门路、立不了塔柱；`GATE_DOOR`、`GATE_SIGN` 是光桥和总图牌子的位置。0.14.0 的门楼和二向箔仓是新计划，旧计划逐格没变（`ModulePlanTest` 的校验和照过，`ModuleLook.outside` 只改画面），所以 `BuildJob.V` 还是 2。
- **溶解**：`passes()` 只放人碰不到的格子，能穿过的格子要在行走测试里证明够不着（潮汐井的环，`RootShapeTest.theRingsAreOutOfReach`；0.13.0 秘法庭的塔尖、猎场的天篷、泉阵的方尖碑，各自的 `…WalkTest.theCellsPlayersPassThroughAreOutOfReach`；血契坛什么都不溶解）。**例外只有两个**：穹顶发生器的腿和发射节点（`P`、`C`，和中枢的 `P`/`C` 一样）：人碰得到，但它们不托任何人站的地面，穿过去就是设计（CITY §4、§4.8），由 `TileDomeGeneratorTest.theLegsAndNodesDissolveAndLetPlayersThrough` 钉住；0.14.0 谐振塔的核心柱（`C`、`Z`，CITY §4.7）：谐振室的楼面从它下面穿过，溶解后人能走进那一列，只多出这一个站的格子（`ResonanceGateWalkTest.theDissolvedCoreOpensItsColumnAlone` 钉着正好多一格，`TileResonanceGateTest.theSpireCoreDissolvesAndLetsPlayersThrough`）。再加这样的例外要写明、钉测试；托着人的格子（楼面、楼梯、像生态穹树干那样的中柱）永远不能 PASS。
- **确定性**：`ShapeKit`、`CityBuild`、`BuildPlan.module` 和种子装饰不读配置、不用随机数和 `Math` 的三角函数（`Mix` + `StrictMath`）；客户端用同样的输入算出同一份投影。
- **渲染**：守 §4.10 的光影规则和预算；凹槽、池沿、水面的常量只在 `ModuleLook` 里改（区块网格和样本画在一起）；`onRenderLast` 里什么都不累加。`DomeLook.PIECE` 必须等于 `CityFrame` 切线的长度（8），否则 `landmarkQuads` 数的就不是画的；地标的预算 `DomeLook.BUDGET` 400 由测试按每个距离、每个半径钉住，加东西先改计数再改画法。潮汐只按 `World.getWorldTime()` 算（`Tide`），不要读服务器的状态或计数。0.13.0：秘法庭的墨是 alpha 混合的（黑色加不上去），别改成加法；`ArcaneLook` 的 FAR 预算 62/64 由测试钉住，加东西先减；`BloodLook.PLATE_Y` 从 `ModuleLook.plates` 取，名牌挪了血珠跟着淡出；猎场只画刷怪蛋颜色的平面剪影，不画实体模型（光影下要另做原型，CITY §4.5），刷怪蛋颜色的缓存是一种怪一项，不是每帧的计数；要素颜色只抄 Thaumcraft 的色值、猎物印记按注册名 `fluxecho:mob_imprint` 和 NBT `Mob` 读，都不引用可选模组的类（`IsolationTest`），也不引用只该和 MobsInfo 一起加载的 `com.fluxecho.mobs`。0.14.0：`Paintings` 是缓存不是计数（一个图标画出来什么样和画了几次无关），池里的贴图做一次、反复用（这个版本的贴图管理器从不释放贴图名），`MOST` 256、每次最多新画 `BUILDS` 4，别改成每帧新建贴图；离开世界和 `TextureStitchEvent.Pre` 时要清（`FrameClient`）；画布的画只在 NEAR，加到 MID 要先改 `CanvasLook.quads` 和两座的预算测试；`CanvasLook.PIECE` 必须等于 `DomeLook.PIECE`；谐振塔 MID 是 280/300（`GateLookTest.theGateKeepsItsBudget`），地标 ≤ `GateLook.BUDGET` 400，尖塔、碎晶和脉冲在范围以内也按地标的距离淡出，改了一边另一边要跟着改，否则 FAR 和地标接不上；`ModuleLook.outside` 只给有通到外面的路的楼（现在只有谐振塔）加墙裙面，改它要保证别的模块的面数不变（`GateLookTest.thePlinthLineRunsAlongThePassageToo` 钉着每座的面数：谐振塔 92 = 两翼一圈 64 + 通道两边 28，生态穹 125、穹顶发生器 64、潮汐井 0、亭子各 76、二向箔仓 51）；天窗光柱只按世界时间算亮度，不读天气或服务器状态。
- **同步**：样本只走描述包和 `UNIT_DELTA`；`BookPacket` 的预算保证 < 28 kB；增量要带 `Ep`/`Rv`，客户端按槽合并。
- **聊天消息**：参数个数不少于两份 lang 里的占位符（`LangTest` 只查字面写出的键）。
- **`Builder.QUIET`**：新方块的 `onBlockAdded/breakBlock` 要守；新的核心方块经 `BlockNexus.quiet()`。

## 11. 批次（CITY §5）

每批最多是 0.10.0 档案馆那样大（一块引擎 + 一到四座建筑），单独发布（CI、CHANGELOG、README 的表、两份 lang、手册和 BLUEPRINT §3.14 同一个提交），发布后城是完整一致的。

| 版本 | 名字 | 基础设施 | 建筑 |
|---|---|---|---|
| 0.11.0（已发布） | 百工 | 通用模块框架：场地族（大厅搬上去）；`ShapeKit`；通用单元（`UnitLayout`、`UnitUse`、样本存储和增量包、样本座里画物品）；通用模块窗口；通用渲染钩子；形状的通用建造计划（按层和部件类分阶段、单元成组、SOFT、AIR、decor、种子装饰）；新部件 SOCKET、BASIN、ANCHOR、WATER、`module_core` 家族；NEI 通用预览；总图画亭子外形（对角位挪到 ±36 的 25×25） | **生态穹**（相位 I） |
| 0.12.0（已发布） | 相位 | **相位生长**（`grow:<p>` 任务、中枢按当前相位检查（五个形状）、`PHASE_NOW` 2、`phase_2` 有真实花费，nexus.md §4.1）；**光梯**（LIFT 部件、乘梯、光束，nexus.md §4.2）；**ZENITH 族**（系泊/浮空两种形状、腿是 LEGS 阶段、空中建造和影子、不检查的门格、带高度的 keep-out）；**ROOT 族**（沉箱 SINK + DRAIN 阶段和带、地下投影的透视、深度检查）；`Envelope`、轴上的模块不占内环；最简的**相位合金**、**共鸣玻璃**物品（研究花费要用） | **回响穹顶发生器**（系泊，§6.2）、**潮汐井**（§6.3） |
| 0.13.0（已发布） | 四亭 | **PAVILION 族**（§4.13）：切角门、中心控制器（`pavilionController`，正面沿 A）、清理外扩一圈、apron 和林荫道地面（`pavilionFloor`）、按行切的 keep-out 和保护盒、对角位的停靠、偏好列表和 ◀ ▶、`noRoom` 的高度检查、内城上限 8 的准备（「建造」页模块列表翻页、总图和 NEI 预览的 8 + 2 个场地）、光桥到亭子内侧的门；通用框架加了 `Stages.order`、中心对称楼的名牌、平面图只看建筑的层和中心的控制台 | **秘法庭**（§6.4）、**血契坛**（§6.5）、**回响猎场**（§6.6）、**魔力泉阵**（§6.7） |
| **0.14.0**（本篇，做完了，还没进游戏试） | 门与仓 | **GATE 族**（§4.14）：跨在路上（通道不建、大门路和塔柱保留、路中间的亭里放控制器 `gateController`）、清理绕开通道只清桥、apron 地面 `gatehouseFloor`、按计划动的列和高度切的 keep-out 和保护（带高度的盒子）、`Campus.boxedSite`、按控制器格精确停靠、光桥到通道口、0 号位叫「大门」、总图的两翼和牌子；**CANVAS** 部件（deck 12 / 码 28）和内境门位的软孪生 `$`；画布上画物品（`CanvasLook`、`Paintings`、`CanvasRender`）；框架加了挂在墙上的单元（`usedFrom`）、潜行取回没有样本座的单元、墙裙光线认通到外面的路 | **谐振塔**（§6.8）、**二向箔仓**（§6.9） |
| 0.15.0 | 外环 | **相位 III**（`grow:3`、内城 8 含大门、`MAX_CAMPUS_MODULES` 8）；**外环建立**（环带平整、井字街、外环步道、大道、外门、外林荫道、门前标记）；**OUTER_DIAG** 和预留的 **OUTER_FLANK** 族；园区半径和间距（CITY §2.9：`buildMinSpacing` 默认 312）；最简的**记忆丝** | **智子阵列**、**全息沙盘** |
| 0.16.0 | 天环 | **相位 IV**（`grow:4`）；**释放**任务（腿拆掉、吊舱浮起来）；**SKY 族**（FLOAT 部件、栈桥、打开吊舱的门）；根的第二个形状；最简的**显化物质**和**反重力晶格** | **天穹气象站**、**天光帆阵**、**观星台**、**水滴机库**、**深根** |
| 0.17.0 | 归一 | **相位 V**（`grow:5`）；**ORBIT 族**；对角的天环泊位；光环和极光（渲染）；卫星（物品 + 渲染） | **轨道锚**、**天环泊位 ×4**、**地脉核** |
| 0.18.0 | 源点 | **THIRD 族**；**第三环建立**（平整、第三环步道、大道、第三道门）；源点之窗（渲染） | **源点之心**、**源点之冠**（轨道锚的第二个形状） |

0.18.0 之后 BLUEPRINT 3.3、3.7–3.9 的每座结构都立着、能走进去、能成型；NEI 的城市预览随每批长大（6–10 级）。

**每批都要带上**（CITY §5.2）：改了已有的计划就 `BuildJob.V` + 1（0.14.0 也没提：门楼和二向箔仓是新计划，旧计划逐格不变；0.13.0 也没提：亭子是新计划，`Stages.order` 默认 0，老的计划逐格不变；0.12.0 没提：相位 I 的 establish 计划逐格没变，光梯台只在相位 ≥ II 的形状和新的 `grow:<p>`、`establish:<p>` 计划里，天顶和根是新计划，带高度的 keep-out 盒子单独存在 `Kr`、整列盒子的任务存档不变；`GrowPlanTest.phaseOneIsUnchanged`，`BuildPlanTest` 一行没改，campus.md §4）；该相位开放的研究要有真实花费（`ResearchTreeTest`：相位 ≤ `PHASE_NOW` ⇒ 算力 > 0 且有材料；占位用该相位的外壳板和电路，数值以后定）；每座建筑一个任务（`build_quests.py` 的 `echo_nexus()`，key 按模块键）；测试：形状、行走、单元、计划（HARD = 检查格 × 4 朝向 × 允许的场地）、场地（`siteplan.py` 的断言进 `SitePlanTest`/`CampusPlanTest`）、光梯路径、沉箱顺序（根）、释放（天顶）；回复里说清楚只能进游戏看的（每个画面、乘梯的手感、穹顶壳、水下的沉箱、NEI 城市预览的加载时间）。

**结构之后**（CITY §5.3，0.19.0 起，按用户想先玩到什么排）：潮汐（BLUEPRINT 3.11）和潮汐井发电；穹顶的功能（3.8）；各模块的功能（并行回响、存储、算力、魔力、血、猎物、天气、水滴、卫星）；覆层和相位核（3.4）；各建筑门位后面的折叠区内境和城中城大厅（3.10）；帆阵、地脉核、源点之心的发电（3.9）；所有数值。CITY §6.1 替用户做的选择列在 BLUEPRINT 第七节，等用户确认。
