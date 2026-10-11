# 开发手册 · 回响之城：城市模块框架、新部件、生态穹（0.11.0 起）

> 改 `city/`、`city/client/`、`logic/` 里的城市类（`SiteFamily`、`ShapeKit`、`UnitLayout`、`CityBuild`、`EcoDomeShape`、`ModuleCores`、`ModuleLook`、`ModuleMap`、`UnitBrowse`、`PreviewSites`），或者要给城里加一座新建筑之前读这一篇。园区、建造引擎、投影、地形判定、回响档案馆在 [campus.md](campus.md)；中枢、研究、显化、多方块框架在 [nexus.md](nexus.md)；画在世界里的东西先守 [gates.md](gates.md) §5；总览见 `CLAUDE.md`。
> **方案在仓库外**：建筑方案 `D:\Code\FluxEcho-campus\city\CITY.md`（下文「CITY §x」）和它的生成器 `city/gen/`（`kit.py` 图例和自检、`b_hall.py` 生态穹和二向箔仓、`siteplan.py` 场地检查、`report.py` 计数和用料、`plans.txt` 每个形状的每一层）；每个版本的合同在 `D:\Code\FluxEcho-campus\spec\M<n>.md`（0.11.0 是 `M8.md`）。**文字和生成器对不上时以生成器为准**，Java 逐行移植生成器（像 `ArchiveShape` 移植 `spec/gen/archive.py`），改了生成器就把 CITY.md 的文字改过来。
> 路径相对 `src/main/java/com/fluxecho/`。`EchoText.t("x")` 对应 lang 键 `fluxecho.x`。文中数字和键都按 0.11.0 的代码核对过。

## 0. 定位

用户在 0.10.1 之后的指示：「根据计划 持续推进任务 先把计划中的结构与建筑全部做完」——**先把 BLUEPRINT 3.3、3.7–3.9 里的每座结构和建筑都立起来**（由中枢自己建、能走进去、能成型），各模块真正干什么、数值，等结构做完再做（0.19.0 起，§11）。CITY.md 是为此写的建筑方案：园区变成一座**井字形的城**——中间一格是广场和中枢；四条轴上是大门和三座大厅；四个对角是四座亭子；相位 III 开出四条笔直的井字街，通到八角形的外环步道，外面立十二个外环位；源点时代再围一圈十六个第三环位。中枢上方 `Y0+40` 悬着吊舱（先立在六条腿上，相位 IV 浮起来），周围八个浮空平台，顶上是轨道锚；下方一口井一直深到 `Y0−57`；一条光梯贯穿整条轴：根 ↔ 中枢台 ↔ 天顶 ↔ 轨道（CITY §0）。参照《考工记》的「匠人营国，方九里……九经九纬」和井田（九格围着一口公用的井，这里根位真的是中间那格下面的井）。

**0.11.0「百工」是第一批**（CITY §5.1）：通用的模块框架（场地族、形状工具、单元、通用建造计划、模块核心家族、通用窗口、通用渲染钩子、新部件 SOCKET/BASIN/ANCHOR/WATER）加第一座新建筑**生态穹**。基础设施先做完，后面的批次大多只是数据：一个形状移植、一个 `ModuleSpec`、一个渲染、窗口的小调整（§7、§11）。

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
- **入口**：朝前的楼（大厅、大门、外环侧位、第三环、空中小屋）用**套件门**（`ShapeKit.portal`）：两扇 3×4 的门（`x = MID−3…MID−1`、`MID+1…MID+3`，y1–4，两格深必须是空气），门间柱里自下而上是控制台座（y1）、控制器（y2，眼睛高度）、两节立柱（y3–4），y5 光檐门楣，门外两格的悬浮环段雨棚（y5，decor）——和档案馆的门一模一样。中心对称的楼（亭子、外环对角位）开四个切角门（离对角线 2 格内的切角格，4 高，两格深空气，y5 门楣，亮门框），控制器在中心的控制台上：从中枢那边沿对角线走进去、在楼的中心操作。竖向的（吊舱、轨道锚、根）坐光梯进，控制器在梯台旁的控制台上、眼睛高度。
- **名牌**：门口雨棚上方（中心对称的楼在中心控制台上方）一块朝相机的名牌，模块色，48 格内（§4.10）；铺砖的箭头指着每扇门。

**不规则但统一**（CITY §1.2，效果从大到小）：研究顺序（每种模块有场地偏好，先研究哪个就先填哪个场地，两座园区不会一样）；四个朝向（每座楼都朝中枢，同一座楼在不同场地上被看到正面、侧面或背面）；高低不同的天际线（地面上 8–33，天顶 `+40…+49`，根到 `−57`，同轴同对角的邻居不同高）；**种子装饰**（DECOR 阶段按 `Mix.seed(控制器)` 选：雨棚 2/3/4 格深，一侧 apron 外排一个「附属」——种植墙、灯排、长椅或全息牌柱；都是蓝图外的 SOFT，检查的形状照样镜像，装饰立不立都能成型，§4.3）；铺砖（Voronoi 缝、深色团、侧叶、种子光井）；楼之间留草地（园区里的公园）。永远不变的：调色板、套件门、墙裙和檐口的词汇、光只走线、主题色只在光里。

**预算**（CITY §1.3，括号里是硬上限）：

| 项 | 预算 |
|---|---|
| 每个形状的检查格 | ≤ 7,000（12,000）；现在最大是档案馆 5,055 |
| 每项任务的步骤（检查 + 软 + 空气 + decor） | ≤ 8,000（12,000）；档案馆 6,237、生态穹 3,614 |
| 每项任务的放置时间（`buildBlocksPerTick` 2） | ≤ 4 分钟（另加阶段节奏） |
| 每项任务的清理体积 | ≤ 40,000 格（8 格/tick 约 4 分钟）；超了分两遍清 |
| 每项任务的 EU（每块 256） | ≤ 2 M |
| 客户端每座楼 | **近**（相机在里面或离盒子 ≤ 24 格）≤ 2,000 quads；**中**（≤ `effectRange` 128）≤ 300；**远** ≤ 64 |
| 画到 512 格的地标 | 每个 ≤ 400 quads（中枢光柱、穹顶壳、大门尖塔、天空光柱、智子天幕、卫星、源点之心） |
| 样本座、画布里的物品 | 最近 256 个、24 格内（档案馆书的规矩） |
| 广场上整帧 | ≤ 12,000 quads |
| 区块网格 | 只有 ISBRH；溶解的格子不画（`Formed` 的 HIDE） |
| NEI | 模块预览 ≤ 7,000 格；中枢城市预览（6–10 级）≤ 60,000 格，不铺地面 |
| 服务端 | 成型复查每 100 tick；单元闲着不花东西；单元内容走增量包，压缩后 < 28 kB（`BookPacket` 的做法） |
| 竖向 | 天顶和轨道要 `Y0+57 ≤ 255`；井 `Y0 ≥ 32`、深根 `Y0 ≥ 45`、地脉核 `Y0 ≥ 62`，提供任务时检查，不够就 `no_room` 带原因 |

**形状规则**（CITY §1.5）：
- 本地坐标同 `ArchiveShape`：`x` 横向 `0…W−1`（`MID` 中间）、`y` 向上（y0 是地基层，人站在它上面）、`z` 从正面（z0 朝中枢；中心对称的楼是蓝图的正面）。一张图例全城唯一（§4.1）；档案馆的字符意思不变，它的 `S` 等不给城里的形状用。
- **四种格子**：检查格（结构检查看的，HARD 步骤）；**软格**（`.` 地砖、`,` 草、`;` 泥土、`=` 水、`+` 光带地砖、`_` 地缝钢、`{` 坡道块、`}` 坡道踏板、`|` 坡道栏杆、内境门位的 `[` 深色墙裙、`!` 光缝墙板、`/` 窗格：SOFT 步骤，不检查，可以不对称）；**必须是空气**（`-`，AIR 步骤，按空气检查）；**decor**（盒子外：雨棚、种子装饰，SOFT）。
- **内境门位**（CITY §4.15）：有内境的建筑在蓝图轴上留一个 3×4 的门位。门位的格子还是原来那堵墙的方块，只是写成软格的「孪生」字符（`w L G W` → `[ ! / {`；生成器 `Shape.gate_niche`、Java `ShapeKit.Shape.gateNiche`，`ShapeKit.checkedTwin` 反查是哪种墙）：照样建（SOFT 步骤，阶段跟着那堵墙），结构检查不看。0.19.0 起在这里开光门，不用改检查的形状、不用提 `BuildJob.V`。生态穹的门位是鼓座后墙正中 `x 12–14, y 1–4, z 34`（12 格，§6）；回响档案馆照 CITY §4.0 到 0.19.0 才放开，它的后墙正中（讲台后面）现在还是检查格——0.10.x 已经建好的档案馆要照旧成型，放开时那一版自己提 `BuildJob.V`。
- **镜像**：检查格和空气格沿 `x` 镜像（`symmetric()` 跳过软格和登记的不对称格）；两座楼梯互为镜像（右边 `mirror = −1`）。
- **整组单元**：一个单元 = 种类、格子、槽数、可选的朝向；点它任何一格就是这个单元；单元格永远是检查格；**单元顺序就是槽号，发布后永不改**（`LibraryUnits` 的规矩）。
- 控制器在 y2、下面 y1 是控制台座（血契坛、潮汐井在台上高一格）；门 3 宽 4 高、两格深的空气；站的地方上方留 2 格净空（人高 1.8）；旋转楼梯用档案馆的 3×3 井（每个环格一级、每级半格、12 级升 6，楼梯上方转一圈后净空 3.5）。
- **自检**（每个生成器里有，Java 里是单元测试）：`kit.summary` 的计数、`symmetric`，和从正门外走进去的**行走测试**（以半格为单位的广度优先，一步最多升 0.5、最多落 3，光梯连接上下梯台）：要走到眼睛高度的控制器和每个单元。CITY 的二十个形状都通过。

## 2. 场地（CITY §2.1–2.2；`logic/SiteFamily`、`logic/CampusPlan`）

**场地族** `SiteFamily`（枚举；名字可能存进 spec，永不改；ordinal 不存）：

| 族 | 场地 | `opens`（相位） | 外形 | 控制器 / 朝向 | 0.11.0 的状态 |
|---|---|---|---|---|---|
| `HALL` | 4、2、6 | I | 横 27、深 ≤ 35，前排径向 24（`HALL_FRONT`，到径向 58）；apron 2；前庭径向 20–23 | 轴上的套件门，`Y0+2`，朝中枢 | 建：档案馆、生态穹 |
| `PAVILION` | 1、3、5、7 | II | `oct(12,16)`（25×25）中心 (±36, ±36)（A、R 24–48）；apron `oct(14,18)` | 中心控制台 `(±36, Y0+2, ±36)`；蓝图正面沿 A 朝中枢（1、7 朝 −A，3、5 朝 +A） | 只有外形（总图画、`SitePlanTest` 查），0.13.0 建 |
| `GATE` | 0 | II | A 23–39、R −15…15（31×17），R −6…6 的通道永不建 | 路中间的亭 `(A 30, R 0, Y0+2)`，朝 −A | 只有外形常量，0.14.0 |
| `ZENITH` | 轴上 | II | 吊舱 `oct(10,14)` 楼面 `Y0+40`，六条腿落在广场半径 14 | 吊舱控制台 `(A 4, R 0, Y0+42)`，朝 +A | 只有名字，0.12.0 |
| `ROOT` | 轴下 | II | `oct(11,15)` `Y0−1…−26`；`oct(12,16)` 到 −39；球到 −57 | 岛上控制台 `(A 2, R 0, Y0−23)`，朝 +A | 只有名字，0.12.0 |
| `OUTER_DIAG` | O1、O3、O5、O7 | III | `oct(15,20)` 中心 (±62, ±62) | 中心或正面（按楼） | 0.15.0 |
| `OUTER_FLANK` | O0±…O6± | IV | 27 × 31，前排径向 64，横向中心 ±19（正对井字街） | 套件门 | 预留（逆熵闸门、建筑回响、回响中继） |
| `SKY` | S0–S7 | IV | `oct(10,14)` 平台 `Y0+40` | 朝吊舱 | 0.16.0 |
| `ORBIT` | 轴上 | V | `oct(7,9)` `Y0+52` | `(A 3, R 0, Y0+54)` | 0.17.0 |
| `THIRD` | T00–T15 | V | 41×41，前排径向 105，横向中心 ±25、±71 | 套件门 | 0.18.0 |

`SiteFamily.of(site)`：0 → GATE，奇数 → PAVILION，其余 → HALL；`sites()`（内城 0–7 里的场地，别处的族为空）、`hasSite`、`opens`。**0.11.0 只真正建大厅族**：`ModuleSpec.footprint` 对 HALL 和 PAVILION 有答案，别的族抛 `UnsupportedOperationException`；`BuildPlan.module`/`hallController` 只认大厅位（`IllegalArgumentException`）。

**`CampusPlan` 的外形常量**（0.11.0）：`DIAGONAL_CENTRE` 36、`DIAGONAL_SIZE` 25、`DIAGONAL_K` 16（0.10.0 预留的是 (±34, ±34) 的 23×23；那里从没建过东西，只有总图的轮廓挪了）；`PAVILION_SITES {1,3,5,7}`、`PAVILION_APRON` 2；对角林荫道 `ALLEE_FROM` 27 到 `ALLEE_TO` 55（`|a|+|r|`，3 宽：`|a|−|r| ∈ {−1,0,1}`，从环道的对角面到亭子的内门）；大门楼 `GATEHOUSE_FROM/TO` 23/39、`GATEHOUSE_HALF` 15、`GATE_PASSAGE` 6；井字街 `STREET_FROM/TO` 18/20、`STREET_REACH` 60。方法：`family`、`inEnvelope`（按族分派）、`inPavilion`、`pavilionCells`（481 格）、`pavilionApron`（140 格）、`pavilionAllee`（44 格）、`inGatehouse`、`gatehouseCells`（527 格）、`gatehouseApron`、`hallCells`、`hallApron`（含前庭，铺砖前的整块）、`streetCells`（相位 III，还没建）。

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

模块的偏好列表：library {4, 2, 6}；**eco_dome {2, 6, 4}**；dual_foil_vault {6, 2, 4}；mana_fountain {1, 7, 3, 5}；arcane_court {7, 1, 5, 3}；hunting_ground {3, 5, 1, 7}；blood_covenant {5, 3, 7, 1}；resonance_gate {0}。内城容量：相位 I 2、II 4、III 8（`RingSlots.open`）；园区上限 `TileNexus.MAX_CAMPUS_MODULES` 现在还是 7，0.15.0（相位 III，大门也算一个）改成 8。八个内城模块正好是 BLUEPRINT 3.7 的八个内环模块。

**场地地面**：大厅照旧 `siteFloor(site, 27, 35)`（campus.md §2）。亭子（0.13.0）：apron `oct(14,18)` 去掉占地（内圈 TRIM）+ 对角林荫道（中线在偶数 `|a|+|r|` 上是 LIT 虚线，50–53 箭头，54–55 亮门槛）= 140 + 44 格。大门楼（0.14.0）：通道两边 `|R|` 4–6（路不铺）+ 两侧翼两格 apron。都只增，不碰 establish 的地面和别的场地。

**`SitePlanTest`**（`siteplan.py` 断言里现在存在的内城部分）：族和场地；生成器的数字；内城 `problems: 0`——建筑两两不相交（广场 + 环道 + 大门、大门楼、三座大厅、四座亭子），大厅 apron 和前庭不碰亭子和它的 apron、不碰大门楼，亭子 apron 不碰大门楼和它的 apron，林荫道不碰大厅，塔柱在大门楼通道里，每个内城外形在 `oct(64,90)` 里，井字街不碰任何建筑和 apron；间隙：大厅 apron ↔ 亭子 apron 6 格草地、大门楼 apron ↔ 亭子 apron 4 格；外形属于自己的族。外环、第三环、天上、地下的断言等对应的批次再加。

## 3. 文件

| 文件 | 作用 |
|---|---|
| `city/CityModule` | 注册（`FluxEcho` 三个阶段在 `LibraryModule` 之后调）：preInit `fluxecho:module_core`（物品 `ItemBlockModuleCore`）、TE `fluxecho:eco_dome`；init 生态穹的 NEI 预览（`infoContainer("eco_dome.structure")`）、中枢预览（`NexusModule.previewModule(core, ModuleCores.ECO_DOME, TileEcoDome::new)`）、`BlockFrame.onUse`/`BlockFitting.onUse` 登记 `UnitUse::use`、`ModuleSpecs.register(ecoDome())`（在书库之后，所以中枢先提供档案馆）；postInit 显化 `eco_dome_core` |
| `city/BlockModuleCore`、`ItemBlockModuleCore` | 模块核心家族：一个方块，meta = 模块（§4.5） |
| `city/TileCityModule` | 城市模块基类：结构定义、溶解、场地上的朝向、本地坐标、单元和样本、维持电、样本同步（§4.6–4.7） |
| `city/TileEcoDome` | 生态穹：`COLOR`、`EcoDomeShape` 的形状和单元、核心 meta 0、溶解 `CZ`、能穿过的 `Z`（§6） |
| `city/UnitUse` | 单元的右键：放、取、开窗口（§4.8） |
| `city/CityGui`、`UnitSlot` | 通用模块窗口（MUI1，三页）；单元格子的槽（热键塞整堆只留一件）（§4.9） |
| `city/client/CityScreen` | 窗口的自绘部分 |
| `city/client/CityRender`、`CityFrame` | 每座城市模块共有的画面（墙裙光线、扫光、名牌、样本座里的物品）；模块本地坐标到相机坐标、线条和光带（§4.10） |
| `city/client/EcoDomeRender` | 生态穹自己的画面（§6） |
| `logic/SiteFamily` | 场地族（§2） |
| `logic/ShapeKit` | `city/gen/kit.py` 的移植：图例、几何、`Shape`、镜像、行走、内境门位、`summary`（§4.1） |
| `logic/UnitLayout` | 一个形状的单元、槽号、样本座（§4.2） |
| `logic/CityBuild` | 城市模块的形状交给通用建造计划（`BuildPlan.ModuleBuild`）（§4.3） |
| `logic/EcoDomeShape` | 生态穹的形状、单元、蓝图、阶段规则（`b_hall.py` 的 `eco_dome()`）（§6） |
| `logic/ModuleCores` | `fluxecho:module_core` 的 meta 表（§4.5） |
| `logic/ModuleLook` | 渲染用的纯规则：墙裙光线的面和顺序、名牌位置、档位、样本座凹槽、池沿、涟漪的余量、认蜜蜂（§4.10） |
| `logic/ModuleMap`、`UnitBrowse` | 窗口的平面图（`ArchiveMap` 的通用版）；单元列表的翻页、循环、选择检查（§4.9） |
| `logic/PreviewSites` | 中枢 NEI 城市预览给每种模块挑位置：有 spec 的只上自己的场地、都占了就不立；没 spec 的用内环槽，和场地分开记（§4.11） |
| `logic/BuildPlan` | 0.11.0 加的 `ModuleBuild` 接口、`module`、`hallController`、`ornaments`/`canopyDepth`/`annex`、`ARCHIVE`、`MODULE_MARGIN`/`MODULE_HEADROOM`（§4.3） |
| `logic/CampusPlan`、`Parts`、`PartRecipes`、`TerrainRule.forRepairGround` | 场地族的外形（§2）；新部件码（§5）；新配方；修复只往空格里铺地面（§6） |
| 改过的老类 | `nexus/TileMultiblock.coreMeta`、`BlockNexus.quiet`、`NexusModule.previewModule(core, meta, tile)`、`TileNexus.previewModules`、`NexusNet.UNIT_DELTA`、`NexusWaila`、`NexusGui.stageKey`、`nexus/client/ModuleRender`、`frame/BlockFrame`（ANCHOR）、`frame/client/FrameClient`（离开世界时 `TileCityModule.clearClient`）、`campus/BlockFitting`（SOCKET、BASIN）、`campus/client/FittingRender`、`PartBlocks`、`ModuleSpec`（`family`）、`Campus`（`RING_FULL`、`offerStatus`）、`Builder`（`Parts.euOnly`、修复的地面）、`Terrain.isTarget`（水）、`campus/client/BuildFx`、`BuildScan`（图标缓存按 128 个码）、`campus/client/Masterplan`（亭子外形）、`Config.moduleUpkeep` |
| `tools/CampusTextures.java`、`tools/NexusTextures.java` | 样本座和池沿；覆层锚点和模块核心的贴图（§5） |

## 4. 共用框架

### 4.1 `ShapeKit`（`kit.py` 和 `b_hall.py` 用到的辅助函数的移植）

- **图例** `partOf(ch)`（CITY 附录 A，全城唯一）：构架 `F` 地基、`B` 基座、`g` 导能基座、`P` 支柱、`C` 导能柱、`R` 环段、`Z` 晶核座、`K` 控制台座、`A` 覆层锚点；地板 `d` 地砖（检查）、`#` 地缝钢、`e` 光带地砖、`:` 格栅、`%` 深色、`k` 踢脚光带、`*` 光井、`W` 墙板、`L` 光缝墙板、`w` 深色墙裙、`n` 光檐；配件 `q` 立柱、`p` 底座、`c` 顶檐、`r` 栏杆、`t` 踏板、`G` 窗格、`h` 全息底座、`o` 样本座、`u` 池沿；软格 `.` 地砖、`+` 光带、`_` 地缝钢、`,` 草（`Parts.GRASS`）、`;` 泥土、`=` 水（`Parts.WATER`）、`{` 墙板、`}` 踏板、`|` 栏杆、内境门位的 `[` 深色墙裙、`!` 光缝墙板、`/` 窗格；`-` → `Parts.AIR`；`~` 控制器和空格 → −1（控制器的部件是模块核心）。附录 A 里的 `f` 浮空地基、`Q` 画布、`^` 光梯是以后批次的部件，`partOf` 现在不认（抛 `IllegalArgumentException`），哪个批次加部件就在这里加一行。
- 字符集：`SOFT` = `.,=;+_{}|[!/`、`soft`、`checked`（不是空格、`-` 和软格）、`checkedTwin`（软格放的是哪个检查字符的部件：`.`→`d`、`+`→`e`、`_`→`#`、`{`→`W`、`}`→`t`、`|`→`r`、`[`→`w`、`!`→`L`、`/`→`G`；草、泥土、水是自己；画面和平面图按它认墙）；行走用的 `PASSABLE`（空格、`-`、水）、`HALF`（`t}` 站在 +0.5）、`NOSTAND`（`r|` 栏杆 1.5 高）、`LOW`（`uh` 池沿和全息底座挡路、不能站）。
- 几何：`octo(du, dv, half, k)`、`hypot`（整数平方和的 `Math.sqrt`，同 Python 的 `math.hypot`）、`angle`（`StrictMath.atan2`）、`offOctant`、`disc`、`ring`、`key/keyX/keyZ`、`boundary`、`thickBoundary`、`inBall`、`ballShell`、`spiral(cx, cz, yFrom, steps, start, mirror)`（档案馆的井：每个环格一级、半格一级、整格是墙板）、`portal(s, z, yLintel, canopy, cx)`（套件门）。
- `Shape`：W×H×D 的字符盒（`set/get/clear`、`counts()`）、`unit(name, kind, cells, slots, face)`、`controller`、`decor`（`{x, y, z, 字符}`）、光梯、`asym`（不参加镜像检查的格子，比如生态穹树干的螺旋）、`gateNiche(x0, x1, y0, y1, z)` 和 `niche`（内境门位：把墙格换成软的孪生字符，不是墙就抛 `IllegalArgumentException`；§1）、`symmetric()`、`walk`（状态 `state(x, z, h)`，h 以半格计）、`unitReach`、`controllerReach`；`summary(shape, starts)` → `Summary`（计数、对称、走到的状态数、控制器和每个单元能不能到）。
- 和生成器逐格一致：上面的 `hypot`/`atan2` 换法给出和 Python 一样的格子；每个形状测试比对整层的 CRC 校验和（`EcoDomeShapeTest`）。

### 4.2 `UnitLayout`

- 从 `Shape.units()` 按生成器声明的顺序建：`Unit{index, name, kind, cells, sockets, slots, firstSlot, face, floor}`；槽号一个单元接一个单元连续排（单元 i 的槽紧跟单元 i−1 的）；`sockets` 是单元格里的样本座 `o`（按生成器列格子的顺序），槽 `firstSlot + k` 显示在第 k 个样本座里；样本座少于槽数时多出来的槽不在哪个格子上显示（画布的一个槽画满整面），多于槽数、一格属于两个单元 → 构造时抛 `IllegalStateException`。`floor` = 单元最低一格的 y。
- 查询：`unitOfCell`、`slotOfSocket`、`unitOfSlot`、`socketOf`、`slots()`、`size()`、`storeys()`（单元站的不同楼层，从低到高——窗口平面图的层）、`storey(unit)`。
- **顺序就是槽号**（`TileCityModule` 存档 `Samples` 按槽号）：发布后不改顺序、不在中间插单元、不改单元的槽数；真要加只能加在末尾（会改形状，要提 `BuildJob.V`）。

### 4.3 通用建造计划（`BuildPlan.ModuleBuild`、`BuildPlan.module`、`CityBuild`）

- **`ModuleBuild`**（一座模块的形状怎么交给建造计划）：`key`、`width/depth/height`、`blueprint`、`cellOf`（本地格 → 蓝图格）、`controller`、`cell(x,y,z)`（检查字符、软字符、`-`、`~` 或空格）、`part`、`stage`（不属于单元的格子在哪个阶段）、`fitRank`（FIT 阶段里的次序：负的在单元之前、正的在之后，不能是 0）、`units`（每个单元的格子，一个单元一组）、`decor`、`ornaments`。档案馆（`BuildPlan.ARCHIVE`）和城市模块（`CityBuild`）都这样描述自己，一个建造器铺所有大厅。
- **`BuildPlan.module(plan, site, cx, y0, cz, fx, fz, m)`**（9 段，`M_CLEAR … M_COMMISSION`）：
  - CLEAR：占地外扩 `MODULE_MARGIN`(1) 的柱子，从 Y0+1 到形状里最高的非空层 + 1 + `MODULE_HEADROOM`(2)，由中枢向外，`grade`（同档案馆：没铺砖的 Y0 格换成草、下面填坑，campus.md §5）；
  - FLOOR：`siteFloor` + 模块放在 FLOOR 阶段的软格，由中枢向外（`RIPPLE`）；
  - SHELL、DECK、UPPER、ROOF：模块的 `stage` 分到这几段的格子，每段自下而上；
  - FIT：rank < 0 的格子 → 每个单元一组（组号 = 单元序号，连续、组内自下而上，一起付钱一起发射）→ rank > 0 的格子，每个 rank 内自下而上；
  - DECOR：形状的 decor，`ornaments()` 为真时再加种子装饰；
  - COMMISSION：控制器（模块核心），最后一步。
  软字符 → SOFT、`-` → AIR、其余 → HARD；每格用 `Blueprint.world` 按 `hallController(m, …)` 摆，所以 HARD 格正好是结构检查格。抛 `IllegalStateException`：格子没有阶段、FIT 的 rank 是 0、单元格不是部件、单元格数和形状里的单元格数不等。
- **`hallController(site, cx, y0, cz, fx, fz, ctrlY, ctrlZ)`** 和 `hallController(m, …)`：前排径向 24、控制器在场地轴上往里 `ctrlZ` 行、Y0+`ctrlY`，朝中枢（场地轴取反）；`archiveController` 现在是它的特例。
- **档案馆也走这里**：`BuildPlan.ARCHIVE` 照 0.10 的阶段规则原样（立柱在单元前，全息底座、讲台、阅读台在后；`ornaments()` 假），`archive(...)` = `module(..., ARCHIVE)`；`ModulePlanTest.theArchivesPlanIsCellForCellTheSame` 钉住 4 个朝向 × 3 个场地的 12 个校验和，所以 **`BuildJob.V` 还是 2**。
- **种子装饰**（`ornaments`，种子 `Mix.seed(控制器 x, y, z)`）：`canopyDepth(seed)` 2/3/4（形状自己的雨棚两排，最外一排往外重复）；`annexKind(seed)` 和 `annex(width, depth, seed)`：一侧（`Mix.unit(seed, 43) < 0.5` 时左边 x = −2，否则 x = width+1，即 apron 外排，`ANNEX_OFFSET` 2）、Y0+1、z 从 `ANNEX_FROM` 6 到 depth−7：种植墙（每 4 格一个池沿，其余草）、灯排（每 3 格两节立柱加一个顶檐）、长椅（踏板，每 5 格空一格）、全息牌柱（中间一格：全息底座加两节立柱）。全是 SOFT、在蓝图外。同一座中枢的不同场地种子不同。
- **`CityBuild(key, shape, height, blueprint, layout, corePart, stages)`**：城市模块的 `ModuleBuild`。`cell` 读形状（≥ `height` 的层是空格：生成器可以多分配层）；`part` 按图例、控制器是 `corePart`；`stage`/`fitRank` 交给模块自己的 `Stages`；`units()` 来自 `UnitLayout`；`decor()` 是形状的 decor（部件码）；`ornaments()` 恒真；`cellOf(x,y,z) = {x, height−1−y, z}`。
- **阶段的 lang 键**：模块任务都是 9 段；档案馆用 `fluxecho.build.stage.m<n>`，其他城市模块用 `build.stage.c<n>`：`c0` 清理场地、`c1` 场地地面、`c2` 外墙、`c3` 楼面、`c4` 上层外墙、`c5` 屋顶、`c6` 配件和单元、`c7` 装饰、`c8` 落成（`NexusGui.stageKey` 按 `ModuleSpecs.moduleOf(计划键)` 选 `e`/`m`/`c`）。阶段号和键一一对应。

### 4.4 `ModuleSpec` 和营造引擎

- `ModuleSpec(family, key, research, width, depth, height, sites, corePart, colour, plans, controllers, enabled)`：每个场地都要属于这个族（否则 `IllegalArgumentException`）；老的 11 参数构造器 = `HALL`。`footprint` 按族（大厅：横 × 深从前排；亭子：八角外接方）。
- 生态穹的：`CityModule.ecoDome()` = `HALL`、键 `eco_dome`、研究 `eco_dome`、27×35×21、场地 {2, 6, 4}、`Parts.moduleCore(0)` = 64、`0x7BE08A`、`EcoDomeShape::plan`、`EcoDomeShape::controller`、`() -> true`（没有单独的开关，`campusEnabled` 关掉的是整个营造）。
- 之后的一切照营造引擎（campus.md §5–§10）：`autoJobs` 按研究和偏好提供（容量 `min(RingSlots.open(PHASE), 7)`，停靠的 0.9.2 大厅也占一个）；开始前账本里要有核心（`need_core`）；keep-out 用 `siteBox`；停靠时按场地收编（`TileNexus.refreshDock` 要有 spec）；修复、取消冷却、模块列表、总图牌子、Waila、终端都是通用的。研究了但只因为内环满了不能提供 → `RING_FULL`（§4.11）。

### 4.5 模块核心家族 `fluxecho:module_core`

- **一个方块，meta = 模块**（`logic/ModuleCores`，CITY 附录 C）：0 `eco_dome` · 1 `dual_foil_vault` · 2 `arcane_court` · 3 `blood_covenant` · 4 `hunting_ground` · 5 `mana_fountain` · 6 `resonance_gate` · 7 `dome_generator` · 8 `root` · 9 `sophon_array` · 10 `holo_table` · 11 `sky_sails` · 12 `observatory` · 13 `droplet_hangar` · 14 `weather_station` · 15 `orbital_anchor`；`BUILT` = 1（0.11.0 只有 meta 0 有模块）；第十七个模块（源点之心）起用 `fluxecho:module_core2`（码 80–95，只预留）。`meta(key)`、`key(meta)`、`KEYS`。meta 一旦给了某个模块就永远是它的（是世界里的方块，也是账本里的部件）。
- 部件码 `Parts.MODULE_CORE + meta`（64–79；`moduleCore(m)`、`isModuleCore`、`MODULE_CORES` 16）；`PartBlocks.block` → `CityModule.core`，`code(block, meta)` 按 meta 反查；`PartRecipes.partOnly` 包含全部 16 个——核心显化得来，建造只收成品，模块任务开始前账本里要有它（同书库核心）。
- **`BlockModuleCore extends BlockNexus`**（`super("module_core", "module_core")`，铁、亮度 0.5）：`createNewTileEntity` 按 meta（0 → `TileEcoDome`，其余 null）；`damageDropped` = meta；创造栏只列前 `BUILT` 个；`quiet()` = `Builder.QUIET`（`BlockNexus.breakBlock` 新加的钩子：引擎改世界时拆核心不通知附近的多方块，阶段结束统一通知；书库和中枢的核心照旧每次都通知）；`dropsMore` → `TileCityModule.takeDown`；图标 `fluxecho:module_core/<key>/front|front_on|side|top`（`front_on` 是成型时的正面；没模块的 meta 用生态穹的）。
- **`ItemBlockModuleCore`**：名字 `tile.fluxecho.module_core.<key>.name`；提示 `fluxecho.<key>.tip`，按住 Shift 是 `fluxecho.<key>.structure`（否则 `gui.shift_build`）；放下用物品的 meta。
- 框架里配套的两处：`TileMultiblock.coreMeta()`（默认 0）——NEI 预览世界里 `placeOwnBlock` 按它放核心；`NexusModule.previewModule(core, meta, tile)`——中枢城市预览放核心时带 meta。

### 4.6 `TileCityModule`

- **子类给**：`build()`（`CityBuild`）、`layout()`（`UnitLayout`）、`coreMetaOf()`、`dissolves()`、可选 `passes()`（默认空），加 `TileModule` 的 `moduleKey()`、`moduleColor()`。形状和单元是静态数据，构造器里就能问。
- **结构**：只有一种形状 `MAIN`；`definition()`/`buildDefinition()` 按模块键缓存，每个字符 `ShapeKit.partOf` → `PartBlocks.block/meta`（`-` 是 StructureLib 的必须为空气，控制器跳过；某个字符没有方块就直接抛）——正是建造放的方块，所以引擎建完就成型。`centreCell` = 地基中心 `cellOf((W−1)/2, 0, (D−1)/2)`（停靠按它找场地）；`coreBlock` = `CityModule.core`、`coreMeta` = `coreMetaOf()`；`descriptionKey` = `<key>.structure`。
- **溶解**：`flags(ch)`：`dissolves()` 里的字符成型后 `Formed.HIDE`（区块网格不画，交给渲染）；其中也在 `passes()` 里的才 `HIDE|PASS`（去掉碰撞）。人站在旁边、绕着走的溶解格（生态穹的树干穿过两层岛面、又是螺旋梯的中柱）只能 HIDE，否则留下一口能掉下去的井（0.11.0 开发中的 bug，`TileEcoDomeTest.theTrunkHidesButStaysSolid`、`EcoDomeWalkTest.theCellsPlayersPassThroughAreOutOfReach` 守着）。
- **朝向**：`place(player, front)`——手放在某座已加载中枢、这种模块某个场地的控制器格上时朝向中枢（引擎 commission 的朝向，同档案馆）；`consoleAt` = 形状里 `K` 的格子（`Consoles` 右键开窗口）。
- **本地坐标**：`local(x,y,z)`（方块 → 形状格）、`localPoint`（世界里的点 → 形状里的点）、`worldOf(lx,ly,lz)`、`worldDir(lx,lz)`。
- **单元和样本**：`unitCount`、`unitAt`、`slotAt`（样本座 → 槽）、`slotCell`、`unitCells`、`unitSlots` → `{first, count}`、`sample(slot)`（服务端读 handler，客户端读 `clientSamples`）、`samples()`、`unitFill`、`firstFree`、`sampleCount`、`unitName`（如 `G-RB`）、`unitKind`（lang `fluxecho.unit.<kind>`）、`selectUnit` 和字段 `selectedUnit`（服务端；存档 `Unit`，不进描述包，窗口自己同步）、`openOnUnit`（只在服务端、不存档：`UnitUse` 开窗前设上，窗口开在「单元」页后清掉）、`contents()`。
- **样本 handler**：`layout().slots()` 格（生态穹 36），每格上限 1，**任何物品都收**（结构阶段的选择，CITY §6.1 第 15 条；不像书库那样查重复），tile 失效后不收。
- **供电**：成型、`nexus() != null`（停靠着）、`nexusEnabled`、有队伍 → 每 tick 从团队无线电网扣 `upkeep()` = `Config.moduleUpkeep`（`nexus.moduleUpkeepEuPerTick`，默认 128），扣到了就 `powered`（`ServerEvents.addTeamTick` 记统计），变化时重发描述包。没电时单元里的东西照样在。每 100 tick `Directory.report`：名字 `tile.fluxecho.module_core.<key>.name`、状态 `fluxecho.dock.<停靠状态>`（停靠没电报 `no_power`），停靠且有电 IDLE、否则 PROBLEM。
- **`standing()`**：tile 没失效、区块在、格子里还是它——窗口的校验器。**拆核心**：`takeDown` 把样本全部掉在核心处、清空 handler（不像档案馆的书进队伍仓库）。
- **NBT**：`Samples`（handler）、`Unit`，加多方块的键；读档先读进存档大小的临时 handler 再逐格拷（槽数变了也不清空）。**描述包**：`P` 有电、`Dk` 停靠状态、`Rv`/`Ep` 增量修订号和这个 tile 的寿命号（每次加载随机）、全部样本 `B`/`Bi`（`BookPacket`）。

### 4.7 样本的同步（`NexusNet.UNIT_DELTA = 5`）

- 照档案馆的书（campus.md §12）：描述包带全部样本（`BookPacket.write`，压缩后 < `LIMIT` 28000 字节，超了的只发 id/meta）；之后只发变了的槽：`UNIT_DELTA`（`Dim/X/Y/Z` 控制器、`Rv`、`Ep`、`B`/`Bi`/`E`），每 tick 最多 `BookPacket.DELTA_SLOTS` 64 槽（剩下的下一 tick），发给 `Sight.near(中心, effectRange + 64)` 并上正在看控制器区块的人（`audience`）。
- 客户端：`NexusNet` 的客户端泵直接交给 `TileCityModule.receiveDelta`（不经星图的 sink，出错只记日志）。每个槽记修订号，比现有的新才收；描述包还没到（或 `Ep` 不是这个 tile 的）增量就停着（最多 `PARKED_MAX` 128 条、30 秒），描述包到了按 `Rv` 顺序重放；描述包的 `Ep` 变了（服务端换了一个 tile）之前听到的全作废。有变化 `clientSampleVersion` +1（渲染和窗口靠它）。离开世界时 `FrameClient` 调 `clearClient()` 清掉停着的包。
- 窗口不另发样本：单元列表、平面图、计数都读 `clientSamples`（M8 说的「分页同步」实际是客户端分页，§9）。

### 4.8 单元的右键（`UnitUse`，档案馆 `Units.use` 的通用版）

- 登记在 `BlockFrame.onUse` 和 `BlockFitting.onUse`（单元只能由构架和配件组成；档案馆的 `Units.use` 先登记、先问）。两端同样回答：在 `NexusRegistry.loaded(w)` 里找已成型、`covers` 这格、这格属于某个单元的城市模块 → 返回 true（客户端挥手、不放手里的方块），只有服务端动作：
  - 不是本队（`BlockNexus.mayUse`）→ `city.unit.not_yours`（主人名）；
  - 空手 + 潜行 + 点的是有样本的样本座 → 取下那一个放到手上（`city.unit.took`：物品名、单元名）；
  - 其余空手 → `selectUnit` + `openOnUnit` + `BlockNexus.open`：窗口开在「单元」页、显示这个单元；
  - 拿着东西 → 一个放进第一个空槽（创造模式不扣），`city.unit.put`（物品名、单元名、n、槽数）；满了 → `city.unit.full`（单元名、n、槽数）。
- 和档案馆一样，**拿着任何东西右键单元都会当样本放进去**，所以不能贴着种植床放方块（设计如此）。
- 聊天消息的参数个数由 `LangTest.everyChatMessageGetsItsArguments` 守着：1.7.10 里占位符比参数多会在客户端格式化时抛异常、客户端崩溃（0.11.0 开发中 `full` 就少传了一个）。它查代码里字面写出的 `new ChatComponentTranslation("fluxecho.…", …)` 和 `UnitUse` 的 `tell(p, "x", …)`。

### 4.9 通用模块窗口（`CityGui`、`CityScreen`、`UnitSlot`；CITY §1.6 第 7 条）

- MUI1，260×256，背包在 (49,174)，flux 风格加模块色。顶行：标题 (6,5) 120×14；三个标签 状态 / 单元 / 平面 在 x 130/172/214、y 4，各 40×15（`TAB_X`，`city.gui.tab_status/units/plan`），页只在客户端。页面区 y 22–140；下面一条（分隔线 y 142，内容 (9,145) 242×26）：样本数（`city.gui.foot.samples`：保管的样本 n/m，共 k 个单元）、停靠状态、当前页的提示（`foot.status/units/plan`）——三个标签之后标题太窄，停靠状态挪到了这条里。
- **状态页**：左边 (11,26) 154×110，每行 12 像素：结构、停靠、供电、维持（`city.gui.status.upkeep_value` n EU/t）、相位、场地、单元（`status.units_value`：n 个单元 · a/b 个样本）、主人，最后一行提示（`status.hint.unformed/undocked/no_power/running`）；悬停 `status.tip`（各行的键都在 `city.gui.status.` 下）。右边 (176,25) 76×112 一个随状态动的模块色徽记。
- **单元页**：左边列表 (9,37) 8 行 × 12 像素、宽 106，列表头 (9,25)（`city.gui.units`），翻页箭头 (89,24)、(103,24)（多于 8 个单元才出现，滚轮也翻，循环，`UnitBrowse.page`）；每行名字、种类、装满程度（`city.gui.fill`），点行 → 请服务端显示它。右边单元标题 (126,25) 124×21（名字、种类、`where`「第 n 层 · 高 y」）；格子从 y 52 起每行 6 个、最多 18 个（`GRID_COLS`/`GRID_MAX`；格子数 = 最大单元的槽数，`gridSlots`），在 x 122–254 里居中（生态穹 3 格，从 x 161 起）；提示 (126,107)（`city.gui.hint`/`hint_full`）；◀ ▶ 在 (126,122)、(233,122) 按单元循环（`UnitBrowse.step`），中间 (145,122)「第 n 个 / 共 N 个」（`city.gui.nav`）。
- **平面页**：`ModuleMap` 的平面图在 x 6–134 里居中（生态穹 83×107 在 (28,27)：一格 3 像素，门在下方，横轴朝走进门的人的右手边）；层标签 (140,26) 每个 26×13、每行 4 个、最多 8 个（`storey_short`，悬停 `storey.tip`）；选中单元的信息 (140,57) 112×60；「打开单元」(140,122) 112×14 → 换到单元页。每层画的是这一层高度上立着的东西（`ModuleMap.Mark`：地面、光带、草、水、墙、实心、玻璃、壁柱、门、栏杆、立柱、楼梯、家具、控制台座、控制器、溶解的树干），单元按装满程度上色（空的暗、装着青、满了模块色），选中的描边；点图选最近的单元（`ModuleMap.nearest`，`REACH` 3 像素内）。
- **选择**：每个窗口自己的（服务端 `Sel`，起点 `selectedUnit`，变了写回 `selectUnit`，下次开窗从这里起）；客户端经 `Link`（`FakeSyncWidget<Integer>`，id 1 `ASK_UNIT`）请求，服务端 `UnitBrowse.valid` 检查后作为同步值发回（`View.unit`），客户端不预测；◀ ▶ 的回调直接在服务端跑。
- **格子**（`Grid`）：服务端每次访问都按当前选择算出 `first + k`、直接读写模块的 handler（换单元不会留下过期的映射，容器只看到内容变了的格子）；超过这个单元槽数的格子什么都不做；客户端是一份副本，它的检查只按布局预测。客户端每 tick 按页和单元开关格子（`enableGrid`，`SlotWidget.setEnabled` 自己通知服务端）。**动过格子的点击之后服务端下一 tick 整窗重发**（`Link.resend`：原版相信客户端已经预测了点击结果，而选择刚变时客户端的格子可能还是上一个单元，物品也可能被服务端拒了）。`UnitSlot`：热键把整堆塞进空格时只留一件，其余还给玩家（装不下掉脚下）并整窗重发（同档案馆的 `BookSlot`）。Shift 点背包里的物品 = 一格一个填进显示中的单元的空格；Shift 点格子里的样本 = 取回背包。
- 服务端同步给窗口的（`FakeSyncWidget`）：`formed`、`dockStatus`、`powered`、`upkeep`、场地（停靠时是 `slot()`，否则 −1）、相位（停靠时 `TileNexus.PHASE`，否则 0）、主人名，和打开时的页（`openOnUnit` → 单元页；玩家自己点过标签之后不再管它）。
- 校验器 `standing()`：核心被拆或区块卸载就关窗。`CityGui.map(c)` 按模块键缓存平面图（两端同一份纯数据）。

### 4.10 通用渲染钩子（`CityRender`、`CityFrame`、`ModuleLook`；CITY §1.6 第 8 条）

- **入口**：`NexusRender` → `ModuleRender.draw` → `CityRender.draw(tile, t, fade)`——没有自己的 `FarDraw` 登记，跟着中枢的渲染器一起画、一起画进光门（gates.md §5）。`NexusRender` 对客户端每个多方块（`ClientTiles.all()`）各算一次距离：模块的**控制器**离相机超过 `effectRange + 48` 就整座不画（中枢在这个距离外只画光柱），最后 16 格按 `fade` 淡出；停没停靠都画（没成型的只画墙裙光线）。
- **档位** `ModuleLook.tier(在里面, 离盒子的距离, NEAR 24, effectRange)`：`NEAR`（相机在里面或离盒子 ≤ 24 格）、`MID`（≤ `effectRange`）、`FAR`（更远）。
- **每座城市模块都有**：
  1. **墙裙光线**（`ModuleLook.course`：y1 的 `w` 格（和门位的软墙裙 `[`，同一种方块）朝外的面，按绕楼的角度排序，从正面中间开始、往走进门的人的右手边绕；门洞没有墙裙，线在门口断开；门位开成光门之前线照样连着）：停靠有电 = 模块色，一道脉冲绕一圈（每 tick 走 0.4 个面，生态穹约 16 秒一圈）；停靠没电 = 琥珀色呼吸；没停靠 = 暗灰蓝；未成型 = 更暗，只亮墙裙方块真的立着的面（逐格看世界，独立的核心和盖到一半的楼不会有悬空的线）。每面一条芯，NEAR 再加光晕；FAR 不画（细于一个像素）。
  2. **成型扫光**：模块色平面加白边升过 `bounds()`（`SWEEP_MS` 2 秒内；迟到的客户端不重播）。
  3. **名牌**（`ModuleLook.plate`：控制器轴上、雨棚中间的上方 1.75；没有雨棚就在控制器上方 3.6、门外 1.5）：48 格内朝相机，最后 16 格淡出；第一行模块名（`module.<key>`，模块色，1.5 倍），第二行运行时是 `city.plate.on`（运行中 · n/m 个样本，白色），否则是停靠状态（`dock.*`，琥珀色）。
  4. **样本座里的物品**（只在 NEAR）：离相机 24 格内最近的 256 个有样本的样本座（每座模块各算各的），每个不贴着整块方块或别的样本座的侧面一份：凹槽里一个模块色光点（`Motes`）和一张物品图（先方块图集再物品图集，每个渲染 pass 一层颜色），轻轻浮动；取不到图的物品跳过。凹槽和图的位置是 `ModuleLook` 的常量（`RECESS_LOW/HIGH` 11/16–15/16、`RECESS_IN` 2.7/16、`PICTURE_MID` 13/16、`PICTURE_HALF` 0.1），`FittingRender` 和 `CityRender` 共用：站在种植床的地面上，从池沿外 0.3 到 24 格都能越过池沿和唇边看到样本的中间和上沿，8 格内整张（`ModuleLookTest.aSocketsSampleShowsOverTheRim`）。
  5. **模块自己的画面**：`m instanceof TileEcoDome` → `EcoDomeRender`（§6）；新模块在这里加一个分支。
- **预算**（一座模块每帧）：墙裙每面一个芯 quad（生态穹 125 个面），NEAR 加光晕（共约 375）；扫光约 5；名牌约 15 加每个字一个；样本座物品每个敞开的面一个光点加 1–2 层图（生态穹 96 个敞开的面，最多 288）。生态穹合计近约 1,690（上限 2,000）、中约 290（300）、远约 52（64）。
- **光影规则**：只在中枢的 `RenderWorldLast` 里画（阴影 pass 已经跳过）、相对 `renderPos*`、只用 quads、每个顶点有朝观察者的法线、不写深度、popAttrib 后手动开贴图；动画只按世界时间算，什么都不计数、不生成、不发包，光门后面再画一遍也一样。每部分（墙裙、扫光、名牌、样本座、模块画面、整体）单独失败：记一次日志、本次运行关掉这一部分，GL 状态在 finally 里恢复（`recover`/`close`）。`CityFrame`：模块本地坐标 → 相机相对坐标（`Blueprint.point` 的逆），朝相机的线条和光带至少约 1 像素宽（每格距离 `PIXEL` 0.0022，每段 ≤ 8 格）。
- **`ModuleLook` 的其他规则**：`water(build, y, cap)`（这一层每格水的中心和涟漪最多能扩多大还在水上，最多 `cap`）；`beeName(注册名)`（按大写、数字、下划线、点切词，`bee` 单独或后面跟 drone/princess/queen/larva/larvae 才算蜜蜂：Forestry 的 `beeDroneGE` 算，beef、熟牛肉、beeswax、beeCombs、beehouse 不算——不引用蜜蜂模组的类，守可选模组隔离）；`EYE` 1.62、`WATER_TOP` 8/9（静止水面在方块顶下 1/9）、`BASIN_TOP` 10/16、`LIP_TOP` 12/16、`LIP_MIN/MAX` 6/16–10/16。

### 4.11 Waila、NEI、「建造」页、总图

- **Waila**（`NexusWaila`；提供者登记在 `BlockNexus` 上，所以覆盖模块核心）：`feDock/fePowered/feSamples/feCap/feUnits/feOwner`；正文是停靠状态（停靠没电显示 `no_power`）、`waila.city`「样本 n / m · k 个单元」、主人。
- **NEI**：模块自己的三维预览（`IMultiblockInfoContainer.registerTileClass(TileEcoDome.class, NexusModule.infoContainer("eco_dome.structure"))`，显示 `MAIN` 和用料）。中枢预览 6–10 级（`TileNexus.previewModules`，CITY §3）：相位允许的每种模块（研究的相位到了）各一座，立在它的偏好里第一个还空着的场地上（偏好都是自己族的场地），控制器在 spec 给的位置、朝中枢，最多 `RingSlots.open(相位)` 座——相位 I 是后面的档案馆和右边的生态穹；有 spec 的模块偏好的场地都被占了就**不立**（debug 日志一行，不占名额），绝不退到别处去和别的模块重叠；只有没 spec 的模块种类（现在没有：书库和生态穹都有 spec）才用空着的内环槽（0.9.2 做法），内环槽和场地号分开记（`logic/PreviewSites`，`PreviewSitesTest`）；不铺园区地面。
- **「建造」页**：城市模块的阶段名是 `build.stage.c<n>`（§4.3）。没有任务时的模块列表多了 `RING_FULL`：研究了、有空场地、只是内环位置满了（停靠的 0.9.2 大厅也算一个）→「内环已满（停靠的 0.9.2 大厅也占一个）」（`build.module.ring_full`），总图牌子写 `build.masterplan.status.ring_full`「内环已满」（`Campus.offerStatus`，campus.md §10）。
- **总图**：对角位的虚线是亭子的 `oct(12,16)`（中心 ±36，`Masterplan.DIAGONAL_CHAMFER = CampusPlan.DIAGONAL_K`）；大厅位照旧画 27×35 的占地（档案馆和生态穹同样大）。

## 5. 新部件（CITY §1.4、附录 C）

| 部件 | 方块 / meta / 码 | 形状 | 光 | 配方（产量），经 `PartRecipes` | 贴图 |
|---|---|---|---|---|---|
| 样本座 Socket | `fluxecho:fitting` 7 / 码 39 | 2–14/16 的立柱，整高；四个侧面各一个凹槽（宽 5–11/16、高 11–15/16、深 2/16），凹槽边一圈青光，样本浮在里面（`CityRender` 画）；碰撞 2–14/16 整高 | 8 | ×8 `PLP/LCL/PLP`（墙板、光缝墙板、通量结晶） | `fitting/socket`、`socket_top`、`socket_glow`（动画） |
| 池沿 Basin rim | `fluxecho:fitting` 8 / 码 40 | 10/16 的半高板；中间一条 2/16 高、4/16 宽（6–10/16）的发光唇边，伸向四周同一高度的池沿（像栏杆的臂），单独一块只有中间一个结；并排的两块池沿之间的面不画；碰撞 10/16（唇边到 12/16，高出碰撞 2/16），只有底面算实心 | 4 | ×8 `T T/TST`（地缝钢、钢板） | `fitting/basin`、`basin_top`、`basin_glow`（动画） |
| 覆层锚点 Cladding anchor | `fluxecho:frame` 9 / 码 9 | 整块，每面一块六角插板（只在贴图里，走构架普通的方块画法）；以后的覆层从这里长 | 6 | ×4 `SRS/RCR/SRS`（不锈钢板、悬浮环段、通量结晶） | `frame/anchor_top`、`anchor_side`、`anchor_top_glow`、`anchor_side_glow`（动画） |
| 水 Water | 码 53 | 原版水源，像草和泥土一样放（SOFT） | — | 没有配方，只花 EU：`Parts.euOnly`（草、泥土、水）每格 `buildEuPerClear`，从不记账；`Terrain.isTarget` 认静止或流动的水源（meta 0） | — |
| 模块核心 | `fluxecho:module_core` meta m / 码 64 + m | `BlockNexus` 控制器（§4.5） | 7（`BlockNexus` 的 `setLightLevel(0.5)`，同中枢和书库核心；CITY 附录写的 8 没照做） | 只收成品（`partOnly`），在中枢显化 | `module_core/<key>/front`、`front_on`（动画）、`side`、`top` |

- 生成：`java tools/CampusTextures.java`（样本座、池沿）、`java tools/NexusTextures.java`（覆层锚点、模块核心），从仓库根目录跑；不要手画。
- 码只往后加（`Parts`：frame 0–15、deck 16–31、fitting 32–47、48–52 老的、53 水、63 `AIR`、64–79 模块核心、80–95 `module_core2` 预留）；`BUILD_FX` 一个码一个字节（≤ 127）；`BuildFx`/`BuildScan` 的图标缓存 0.11.0 起按 128 个码开（0.10 是 64）。
- 构架 `LIGHT` 现在是 `{0,7,0,12,8,15,4,8,10,6}`、`BlockFrame.TYPES` 10、`full` 含 ANCHOR；配件 `FITTING_LIGHT` `{6,9,10,6,7,0,8,8,4}`、`FITTING_TYPES` 9、`FITTING_NAMES` 加 `socket`、`basin`；`isSideSolid`：踏板和池沿只有底面实心。
- 精确成本（`PartRecipesTest.theCitysPartsCost`，账本里再展开成原料）：样本座 = 半块墙板 + 半块光缝墙板 + 1/8 通量结晶（4 P + 4 L + 1 C 出 8 个）；池沿 = 半块地缝钢 + 1/8 钢板（4 T + 1 S 出 8 个）；覆层锚点 = 1 不锈钢板 + 1 环段 + 1/4 结晶（4 S + 4 R + 1 C 出 4 个）。
- 区块网格（`FittingRender`，同 campus.md §11：只读方块和 meta）：样本座的各块每个面都画（凹槽的上下面在方块里面，不能用整柱「顶面、底面贴着不透明方块就剔掉」的规则），发光层只在侧面；池沿用 `Neighbours.fitting` 找同高的池沿伸臂，臂的端头贴着下一块的臂处不发光；物品栏里样本座画整柱、池沿画成东西向的一段。
- 新方块守 `Builder.QUIET`：构架和配件本来就守；模块核心经 `BlockNexus.quiet()`。
- **还没做**（以后的批次）：光梯 LIFT（fitting 9 / 码 41，0.12.0）、画布 CANVAS（deck 12 / 码 28，0.14.0）、浮空地基 FLOAT（frame 10 / 码 10，0.16.0）。附录 C 写好的光照数组到时候再长。

## 6. 生态穹（`logic/EcoDomeShape`、`city/TileEcoDome`、`city/client/EcoDomeRender`；CITY §4.1，生成器 `b_hall.py` 的 `eco_dome()`）

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
- **研究** `eco_dome` 生态穹（ECHO，星图 (226, 38)，相位 1；前置 `inner_ring`、`echo_crystal`；算力 16,000；回响晶 ×4、`ore:treeSapling` ×16、`ore:blockGlass` ×32）——`/fluxecho research all` 现在给 9 项。**显化** `eco_dome_core`（同一研究；回响晶 ×4、`ore:treeSapling` ×8、小麦种子 ×16、`ore:circuitHV` ×2、`ore:blockGlass` ×16；1200 tick；不压记录）→ 生态穹核心（`fluxecho:module_core` meta 0）。**维持** `moduleUpkeepEuPerTick` 128 EU/t。
- **画面**（`EcoDomeRender`，在 `CityRender` 之上；CITY §4.1 Visuals）：
  - **成型**（不论停靠）：树干和晶核座变成**光之树**——3 股光（每股 24 段）绕着柔和的芯从池面盘到晶核座，一道脉冲往上爬；树冠下 6 根枝；顶灯下 140 片发光树叶慢慢摇；16 片光叶飘落，落在树冠岛、浮岛或花园上；顶灯和树冠的光晕。没停靠时亮度 0.3、停靠没电 0.6。NEAR 外只画一根光芯和树冠的一团光晕（15 quads）。
  - **停靠**：穹顶的亮肋（八条经肋从鼓座到顶灯，y12、y16 两道纬环）以模块的绿色**呼吸**，一道光波往上升；肋贴着真实的玻璃台阶走，相机在外面画在玻璃外侧，在鼓座、穹顶或门厅里画在内侧。MID 每隔一段画一段、没有光晕、环 16 段；FAR 只有经肋，每段四层长，没有环（≤ 32）。
  - **停靠、NEAR**：种植床的**全息**——每个有样本的样本座上方一个光锥、一团光和旋转的物品图；种子（`IPlantable`）显示它的作物按生长阶段循环，蜜蜂（`ModuleLook.beeName`）多一个绕样本座转的光点；每个样本座最多 6 quads。
  - **有电、NEAR**：40 颗花粉从种植床和花园升起；8 只蝴蝶（每只 3 quads）在地面、浮岛和回廊之间飘；池面 6 圈涟漪（每圈 16 段），画在静止水面（y1 + 8/9）上方 0.035（光影包带深度和波浪画的水盖不住它），每圈从一格水的中心开始、最多扩到这格的余量（约 0.5，`ModuleLook.water`），不会扩到池沿或树干周围。
  - 每部分（光之树、呼吸肋、全息、花粉、蝴蝶、涟漪）单独失败；表（贴着玻璃的肋、种植床、花园）从 `EcoDomeShape` 一次算好。
- **修复不动花园**：花园是玩家的（开阔的草地就是给他们放蜂箱和作物的）。修复任务里草、泥土、水这些 SOFT 地面步骤只在格子空着时放（`TerrainRule.forRepairGround`：`forBuild` 是 PLACE 就 PLACE，否则 ALREADY；`Builder.verdict` 的第五个参数 `repair`）；耕地、蜂箱下的泥土、玩家放的方块都留着。第一次建造照旧（整片清空规则下照样换掉），结构格和地砖照旧。

## 7. 加一座建筑（照生态穹）

1. **形状**：把生成器（`city/gen/b_*.py`）逐行移植成 `logic/XShape`（用 `ShapeKit`，图例不改名）；`XShapeTest`：整层校验和、计数（检查/软/空气/decor）、对称（不对称的格子登记进 `asym`，并确认 `Blueprint.world` 的精确映射对它们成立）、控制器和控制台座、门洞空气、每个字符都是图例部件、每格有阶段；`XWalkTest`：走到控制器和每个单元、溶解后能穿过的格子够不着。生成器自己改了形状，先改生成器、重跑 `report.py`（更新 `plans.txt`），再移植。有内境的建筑，CITY §4.15 的 3×4 门位在生成器里就用 `s.gate_niche(xs, ys, z)` 标成软格（同样的方块，§1），移植成 `s.gateNiche(x0, x1, y0, y1, z)`；`XShapeTest` 照 `EcoDomeShapeTest.theGateNicheIsSoftAndStillBuilt` 写明哪几格（软、蓝图里是空格、`checkedTwin` 是原来的墙、阶段跟着墙），计划照 `ModulePlanTest.theGateNicheIsBuiltSoft` 查它们是 SOFT 步骤、部件不变。门位里不能有单元格（单元格永远是检查格，`gateNiche` 只认墙）：二向箔仓的门位在二向箔（一个单元）里面，移植它的时候（0.14.0）要先定怎么办。
2. **单元**：生成器里的单元 → `new UnitLayout(SHAPE)`；新的单元种类加 lang `fluxecho.unit.<kind>`（两份）。单元顺序从此是槽号。
3. **建造**：`new CityBuild(KEY, SHAPE, HEIGHT, BLUEPRINT, UNITS, Parts.moduleCore(meta), stages)`，`Stages.stage` 照 CITY §4.x 的阶段表，`fitRank` 不能是 0；`plan(...)` = `BuildPlan.module(..., BUILD)`、`controller(...)` = `BuildPlan.hallController(BUILD, ...)`。`ModulePlanTest` 那样测：HARD = 检查格（4 朝向 × 每个场地）、阶段内容、单元组连续且按顺序、清理盒、种子装饰。
4. **核心**：在 `ModuleCores` 用表里的 meta（附录 C 已经给每个模块分好了），`BUILT` 加一；`BlockModuleCore.createNewTileEntity` 加分支；`tools/NexusTextures.java` 生成 `module_core/<key>/{front,front_on,side,top}`（`registerBlockIcons` 按 `BUILT` 注册，缺贴图就是紫黑块）。
5. **tile**：`TileX extends TileCityModule`：`moduleKey`、`moduleColor`（CITY §1.1 的主题色）、`build`、`layout`、`coreMetaOf`、`dissolves`、`passes`（只放人碰不到的格子）。
6. **登记**（`CityModule`）：preInit `registerTileEntity(TileX.class, "fluxecho:<key>")`；init `registerTileClass(TileX.class, NexusModule.infoContainer("<key>.structure"))`、`previewModule(core, meta, TileX::new)`、`ModuleSpecs.register(new ModuleSpec(family, key, research, W, D, H, sites, Parts.moduleCore(meta), colour, XShape::plan, XShape::controller, enabled))`（场地偏好照 CITY §2.2）；postInit 显化 `<key>_core`。
7. **研究**：`ResearchTree` 加节点（CITY 附录 B 的分支、相位、前置；`ResearchTreeTest`：坐标离边 ≥ 16、间距 ≥ 24；相位 ≤ `PHASE_NOW` 的要有算力和材料，占位成本用该相位的外壳板和电路：EV 钛/`circuitEV`、IV 钨钢、LuV 铬、ZPM 铱、UV 锇）、`Research` 常量、lang `research.node.<id>` 和 `.desc`；`research all` 只给本版本能做的。
8. **渲染**：`XRender`（照 `EcoDomeRender`：本地坐标的表一次算好、按档位画、每部分单独失败、守预算）在 `CityRender.draw` 的模块画面那一段加分支；新的纯规则放 `ModuleLook` 或自己的 logic 类并写测试。
9. **lang 两份**：`tile.fluxecho.module_core.<key>.name`、`fluxecho.<key>.tip`、`fluxecho.<key>.structure`、`fluxecho.module.<key>`、单元种类、研究；阶段用现成的 `build.stage.c<n>`（还是 9 段的话不用新键）。
10. **不是大厅族**：先在那个批次里把族做出来（CITY §2.1 每个族要给：外形 → `footprint`/`siteBox`、控制器位置和朝向、场地地面、清理盒（往上、往下或空中）、keep-out、按相位开放的个数）：`BuildPlan` 的计划和控制器工厂、`ModuleSpec.footprint`、`TileNexus.refreshDock` 的收编（现在按 `CampusPlan.moduleCentre(site, depth)` 找）、`CampusPlanTest`/`SitePlanTest` 的断言、总图。
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
| 改过的 | `LangTest.everyChatMessageGetsItsArguments`（§4.8）；`ResearchTreeTest.theEcoDome`（`PHASE_NOW` 还是 1）；`PartRecipesTest.theCitysPartsCost` 和部件表；`CampusPlanTest`（对角中心 ±36）；`TerrainRuleTest.aRepairLaysGroundOnlyWhereNothingIs`；`CampusEngineTest.aRepairKeepsTheGarden`、`aFullRingIsNamed` |

全仓 101 个测试类、623 个测试（0.11.0；0.10.1 是 88 个类、536 个）。

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
  - 内环容量：相位 I 只有 2 个位置（`RingSlots.open(1)`），档案馆和生态穹正好占满；停靠着的 0.9.2 大厅也算一个（campus.md 的规则），另一个模块就显示「内环已满」，拆下大厅核心或到了相位 II 才有位置。
  - 单元只能由构架和配件组成（`UnitUse` 只登记在这两种方块上）。
  - 树干在光之树里是一根看不见但实心的柱子（设计如此：它撑着两层岛面）。
  - 只有 meta 0 有模块：别的 meta 的核心（只能用命令拿到）放下没有 tile，图标是生态穹的。
  - `covers()` 是宽松的立方体（生态穹 ±36），同 campus.md §16。
  - 窗口里选择变化后一个往返内点格子，作用在新单元上（之后整窗重发，显示对齐，不丢东西）。
- **只能进游戏看**（开着和关着 Complementary 各看一遍）：光之树、呼吸的肋（外面和里面）、全息（作物生长阶段、绕圈的蜜蜂）、花粉、蝴蝶、池面涟漪、成型扫光、墙裙光线（三种停靠状态和未成型）、名牌、样本座凹槽里的物品（越过池沿）、远处的样子（FAR）和帧率是否在预算内；样本座和池沿的区块网格和物品栏图标、池沿连起来的唇边、覆层锚点的六角插板、模块核心的贴图；窗口的样子和手感、点单元打开在「单元」页、Shift 点、热键、平面图和点选；树干在光之树里是实心的；Waila 那一行；「建造」页和总图的「内环已满」；整个建造（投影、阶段、单元整组飞、雨棚和附属、池水）、NEI 的生态穹预览和中枢城市预览。

## 10. 安全修改须知

- **内境门位**：门位的格子是软格（CITY §4.15）；别改回检查格（已建的穹顶也照样成型，但 0.19.0 开门就得改检查的形状、提 `BuildJob.V`），也别在门位里放单元。开门的那一版把这些格子换成光门。
- **形状**：`EcoDomeShape` 就是生成器；要改先改 `b_hall.py`、重跑 `report.py`，再改移植、重钉校验和；改了检查格，已建的生态穹就不成型，改了计划放什么要提 `BuildJob.V`。`Blueprint.world` 的映射要和 StructureLib 逐格一致（生态穹有不镜像的格子）；`ShapeKit` 的 `hypot`/`angle` 换法不能动（和 Python 逐格一致）。
- **永不改、不复用**：单元顺序和每个单元的槽数（`Samples` 按槽号存）；`ModuleCores` 的 meta 和 `KEYS`；部件码（9、39、40、53、64–79，只往后加）；TE id `fluxecho:eco_dome`、注册名 `fluxecho:module_core`；研究 id `eco_dome`、显化 id `eco_dome_core`；NBT `Samples`、`Unit`；描述包 `P/Dk/Rv/Ep/B/Bi`；`NexusNet.UNIT_DELTA = 5`；`Campus.ModuleStatus` 的 ordinal（`RING_FULL` = 5，只能在末尾加）；`SiteFamily` 的名字；配置键 `nexus.moduleUpkeepEuPerTick`；阶段号和 `build.stage.c<n>` 的对应。
- **场地常量**：`DIAGONAL_*`、`GATEHOUSE_*`、`ALLEE_*`、`STREET_*` 是后面批次建亭子、大门楼、井字街的依据，改了要同时改 `siteplan.py` 和 `SitePlanTest`；大厅的常量照 campus.md §17。
- **溶解**：`passes()` 只放人碰不到的格子；能穿过的格子要在行走测试里证明够不着。
- **确定性**：`ShapeKit`、`CityBuild`、`BuildPlan.module` 和种子装饰不读配置、不用随机数和 `Math` 的三角函数（`Mix` + `StrictMath`）；客户端用同样的输入算出同一份投影。
- **渲染**：守 §4.10 的光影规则和预算；凹槽、池沿、水面的常量只在 `ModuleLook` 里改（区块网格和样本画在一起）；`onRenderLast` 里什么都不累加。
- **同步**：样本只走描述包和 `UNIT_DELTA`；`BookPacket` 的预算保证 < 28 kB；增量要带 `Ep`/`Rv`，客户端按槽合并。
- **聊天消息**：参数个数不少于两份 lang 里的占位符（`LangTest` 只查字面写出的键）。
- **`Builder.QUIET`**：新方块的 `onBlockAdded/breakBlock` 要守；新的核心方块经 `BlockNexus.quiet()`。

## 11. 批次（CITY §5）

每批最多是 0.10.0 档案馆那样大（一块引擎 + 一到四座建筑），单独发布（CI、CHANGELOG、README 的表、两份 lang、手册和 BLUEPRINT §3.14 同一个提交），发布后城是完整一致的。

| 版本 | 名字 | 基础设施 | 建筑 |
|---|---|---|---|
| **0.11.0**（本篇） | 百工 | 通用模块框架：场地族（大厅搬上去）；`ShapeKit`；通用单元（`UnitLayout`、`UnitUse`、样本存储和增量包、样本座里画物品）；通用模块窗口；通用渲染钩子；形状的通用建造计划（按层和部件类分阶段、单元成组、SOFT、AIR、decor、种子装饰）；新部件 SOCKET、BASIN、ANCHOR、WATER、`module_core` 家族；NEI 通用预览；总图画亭子外形（对角位挪到 ±36 的 25×25） | **生态穹**（相位 I） |
| 0.12.0 | 相位 | **相位生长**（`grow:<p>` 任务、按当前相位检查中枢、`PHASE_NOW` 2、`phase_2` 有真实花费）；**光梯**（LIFT 部件、乘梯、光束）；**ZENITH 族**（系泊/浮空两种形状、腿是 LEGS 阶段、空中建造、不检查的门格）；**ROOT 族**（沉箱 SINK + DRAIN 阶段、地下投影只画透视的边、深度检查）；最简的**相位合金**、**共鸣玻璃**物品（研究花费要用） | **回响穹顶发生器**（系泊）、**潮汐井** |
| 0.13.0 | 四亭 | **PAVILION 族**：切角门、中心控制器、apron 和林荫道地面、偏好列表、内城上限 8 的准备 | **秘法庭**、**血契坛**、**回响猎场**、**魔力泉阵** |
| 0.14.0 | 门与仓 | **GATE 族**（跨在路上：通道不建、塔柱保留、路中间的亭里放控制器）；**CANVAS** 部件；画布上画物品 | **谐振塔**、**二向箔仓** |
| 0.15.0 | 外环 | **相位 III**（`grow:3`、内城 8 含大门、`MAX_CAMPUS_MODULES` 8）；**外环建立**（环带平整、井字街、外环步道、大道、外门、外林荫道、门前标记）；**OUTER_DIAG** 和预留的 **OUTER_FLANK** 族；园区半径和间距（CITY §2.9：`buildMinSpacing` 默认 312）；最简的**记忆丝** | **智子阵列**、**全息沙盘** |
| 0.16.0 | 天环 | **相位 IV**（`grow:4`）；**释放**任务（腿拆掉、吊舱浮起来）；**SKY 族**（FLOAT 部件、栈桥、打开吊舱的门）；根的第二个形状；最简的**显化物质**和**反重力晶格** | **天穹气象站**、**天光帆阵**、**观星台**、**水滴机库**、**深根** |
| 0.17.0 | 归一 | **相位 V**（`grow:5`）；**ORBIT 族**；对角的天环泊位；光环和极光（渲染）；卫星（物品 + 渲染） | **轨道锚**、**天环泊位 ×4**、**地脉核** |
| 0.18.0 | 源点 | **THIRD 族**；**第三环建立**（平整、第三环步道、大道、第三道门）；源点之窗（渲染） | **源点之心**、**源点之冠**（轨道锚的第二个形状） |

0.18.0 之后 BLUEPRINT 3.3、3.7–3.9 的每座结构都立着、能走进去、能成型；NEI 的城市预览随每批长大（6–10 级）。

**每批都要带上**（CITY §5.2）：改了已有的计划就 `BuildJob.V` + 1（0.12.0：`NexusShape` 相位 ≥ II 和 establish 认识光梯台）；该相位开放的研究要有真实花费（`ResearchTreeTest`：相位 ≤ `PHASE_NOW` ⇒ 算力 > 0 且有材料；占位用该相位的外壳板和电路，数值以后定）；每座建筑一个任务（`build_quests.py` 的 `echo_nexus()`，key 按模块键）；测试：形状、行走、单元、计划（HARD = 检查格 × 4 朝向 × 允许的场地）、场地（`siteplan.py` 的断言进 `SitePlanTest`/`CampusPlanTest`）、光梯路径、沉箱顺序（根）、释放（天顶）；回复里说清楚只能进游戏看的（每个画面、乘梯的手感、穹顶壳、水下的沉箱、NEI 城市预览的加载时间）。

**结构之后**（CITY §5.3，0.19.0 起，按用户想先玩到什么排）：潮汐（BLUEPRINT 3.11）和潮汐井发电；穹顶的功能（3.8）；各模块的功能（并行回响、存储、算力、魔力、血、猎物、天气、水滴、卫星）；覆层和相位核（3.4）；各建筑门位后面的折叠区内境和城中城大厅（3.10）；帆阵、地脉核、源点之心的发电（3.9）；所有数值。CITY §6.1 替用户做的选择列在 BLUEPRINT 第七节，等用户确认。
