# FluxEcho 项目总览（新会话先读这里）

> 这是给接手的 Claude 会话看的主文件：项目是什么、必须遵守的规范、流程，以及"要改什么 → 去读哪份详细手册"。详细内容在 `docs/dev/*.md`，**只读和当前任务相关的那一份**，不要整仓重读代码。改了某个子系统，**同一个提交里更新对应的手册**（以及本文件里过时的地方）。
>
> **0.17.0 起**这个模组按一套已定稿的全面重新设计逐版重构：12 个版本，0.17.0「白舟」到 0.28.0「跃升」。设计在仓库外 `D:/Code/FluxEcho-campus/redesign/`（**只读**；总览 `REDESIGN.md`，实施路线 `refactor/FINAL.md`），全部 ID 的唯一的账是仓库里的 [docs/dev/ids.md](docs/dev/ids.md)。设计稿和代码事实冲突时以代码为准，并在那一版的报告或 `plan.md` 的「勘误」里写明。

## 1. 项目是什么

GT New Horizons 2.8.4（MC 1.7.10、GT5U 5.09.51.482、Forge 10.13.4.1614）的私货模组，作者 lanStar2003（GitHub 仓库 `lanStar2003/FluxEcho`）。一个 jar、三个 `@Mod`：

| modid | 包 | 内容 | 详细手册 |
|---|---|---|---|
| `fluxecho` | `com.fluxecho` | 回响机器（养蜂、神秘、血魔法、猎物、作物、植物魔法）、回响图鉴、回响 ME 供应器、光门与内室、通量中枢（0.9.0 起）和它自己建的园区（0.10.0 营造）、园区长成的回响之城（0.11.0–0.16.0 的 15 座城市模块：生态穹、回响穹顶发生器、潮汐井和深根、四座亭子、谐振塔、二向箔仓、智子阵列、全息沙盘、天环四座，city.md §0）；0.17.0 起重构成「一叶归舟」的城（`REDESIGN.md`） | 见下表 |
| `fluxlite` | `com.fluxlite` | 通量网络：连接器、通量监控台、通量终端、团队无线电网/蒸汽网络、无线充电 | [docs/dev/fluxlite.md](docs/dev/fluxlite.md) |
| `fluxdepths` | `com.fluxdepths` | 通量碎片采集器、流体回响泵、印记拓印器 | [docs/dev/fluxdepths.md](docs/dev/fluxdepths.md) |

FluxLite 和 FluxDepths 在 0.8.0 原样并进来，**保留各自的 modid、注册名、配置文件、网络频道、GT ID**，所以旧存档零迁移。原来的 `D:\Code\FluxLite`、`D:\Code\FluxDepths` 仓库不再开发（也不要在 GitHub 上归档它们），新工作都在这里。

**世界观**：FluxLite 把电送进「通量层」，FluxDepths 从深层捞矿脉碎片；通量层记得流过它的一切。**亲手做过一次的事，之后交给机器**——机器只认真东西（真蜂、真要素、亲手做的宝珠、亲手杀的怪），"做过一次"记进团队账本。用户玩过一遍这些模组，只想跳过重复，不想跳过进度。**重新设计的故事**：整座城是一条搁在岸上、一舱一舱修回来的船——你从「原乡」漂来，手里只剩中枢的一颗晶核（搁浅舱）；中枢的心智「长庚」照唯一一张船图，按固定位置、固定顺序把 20 座楼一座座造回来，每座楼替你干一件 GTNH 里重复的活；船尾的跃迁环开局就断着，每造回一座楼补一段，UHV 末拱心合拢、跃升（REDESIGN ①、③）。

## 2. 要改什么 → 读哪份

| 任务 | 读 |
|---|---|
| **任何 ID**（部件码、注册名、TE id、meta、配置键、NBT 键、`WorldSavedData` 名、消息种类号、场地号、计划键、形状名、单元顺序、研究 id、任务 key、记分项……） | [docs/dev/ids.md](docs/dev/ids.md)（唯一的账；机器可读的导出 `docs/dev/ids.csv`） |
| 重新设计本身：故事、总平面、20 座楼、风格、界面、数值、跃升、旧存档会经历什么 | `D:/Code/FluxEcho-campus/redesign/REDESIGN.md`（总览，先读）；楼稿 `buildings/<组>/<楼>.md`；风格 `style/FINAL.md`；界面 `ui/FINAL.md`；总平面 `masterplan/FINAL.md`；叙事与数值 `narrative/FINAL.md`；裁定 `DECISIONS.md`；未决 `OPEN-QUESTIONS.md`（用户没回答，A1–A22 一律按「推荐默认」） |
| 实施路线：版本表、每版的卡、任务分解和文件归属、接口先行清单、旧存档迁移、测试策略、多代理手册 | `redesign/refactor/FINAL.md`（§2.1 版本表、§2.6 每版的卡、§3.2 任务表、§④ 接口、§⑤ 迁移、§⑥ 测试、§⑧ 多代理）；本版的计划、波次、勘误 `redesign/refactor/v0.XX/plan.md`，各卡的报告 `refactor/v0.XX/reports/` |
| 生命周期、配置约定（分节）、机器框架、视觉/光影规则、图鉴账本、NEI、任务书（分片）、lang（分片）、AE、网络频道、构建发布、工具、测试清单和快慢分组 | [docs/dev/platform.md](docs/dev/platform.md) |
| 养蜂/神秘/血魔法/猎物/作物/植物魔法的机器和物品、机器 ID 表、加一台新回响机器 | [docs/dev/echo-machines.md](docs/dev/echo-machines.md) |
| 光门、内室、折叠区、mixin、AT；**加任何世界内渲染效果（FarDraw/Sight 接口）** | [docs/dev/gates.md](docs/dev/gates.md) |
| 通量中枢、构架、通量物质、研究星图、显化台、模块与内环、多方块框架、停靠、回响档案馆（0.16.0 以前叫回响书库）、终端的中枢页、相位生长、光梯 | [docs/dev/nexus.md](docs/dev/nexus.md) |
| 营造：园区、自动建造、投影、档案馆长厅、地板/配件/补给口/回收箱、建造账本和成本、地形判定、拆除和回收、放核心和拆核心、整修园区、`/fluxecho` 命令、生长/释放/外环建立/退役等任务、`BuildJob.Form`、和相邻园区分地 | [docs/dev/campus.md](docs/dev/campus.md) |
| 0.16.0 的回响之城：场地族和总平面、城市模块框架（`ShapeKit`、单元和样本、通用建造计划、模块核心家族、通用窗口、通用渲染）、15 座楼各自的形状和画面、随地势调整、翻新、0.17.0 旧楼的过渡态和灯窗；**给中枢加一座 0.16 式的新建筑**（一步步照生态穹） | [docs/dev/city.md](docs/dev/city.md) |
| 0.17.0 起的形与皮：新材质族（舟壳、玄基、苔铜 / 汉白玉、墨 / 宣）和它们的方块、`ShapeGeo` 的形、`ShapeRender`、贴图生成器；盒子表 `SkinTable`（状态、本色、灯窗、显影）和旧楼的 `LEGACY` 灯窗；全城本色 `SkinPalette`；遮挡门控 `OcclusionGate`；天幕层 `SkyLayer` 和日轨；探针 `probe`、样板墙 `swatch`、机位 `cam`；`render.*` / `style.*` 配置 | [docs/dev/skin.md](docs/dev/skin.md) |
| 通量网络（连接器、监控台、终端、结算、蒸汽、充电、统计、告警） | [docs/dev/fluxlite.md](docs/dev/fluxlite.md) |
| 碎片采集器、钻头、流体泵、印记 | [docs/dev/fluxdepths.md](docs/dev/fluxdepths.md) |
| 0.16.0 以前的长期路线、世界观、已回答/未定的问题 | [docs/BLUEPRINT.md](docs/BLUEPRINT.md)（§3.14 的旧版本路线——CITY.md 批次里的 0.17.0「归一」、0.18.0「源点」——**作废**，由 `refactor/FINAL.md` §2.1 取代）；旧建筑方案和生成器在 `D:\Code\FluxEcho-campus\city\`（CITY.md、`gen/`，0.17.0 起每个形状一个 `gen/plans/<形状>.txt`），0.16.0 以前每个版本的合同 `D:\Code\FluxEcho-campus\spec\M<n>.md`（0.17.0 起 M14 … 0.28.0 M25） |
| 玩家向说明（中文） | `README.md`、`docs/FluxLite.md`、`docs/FluxDepths.md`；更新记录 `CHANGELOG.md` |

## 3. 必须遵守的规范

**流程（用户明确要求过）**
- **发布一路走到底**：版本做完 → 本地 `./gradlew --offline spotlessApply` + `./gradlew --offline build`（测试全过）→ **直接提交到 main 并推送**（不开分支、不开 PR）→ 推 `X.Y.Z` 标签（不带 v）→ CI 的 Release 工作流构建并发布（发布说明取 CHANGELOG 的 `## X.Y.Z` 小节）。**不要停下来问要不要合并/发布**，只有 CI 失败或真有歧义才停。多代理时只有整合者推送、打标签（见下面「多代理流程」）；需要用户下结论的画面项一律带保守默认发布，不等用户。
- **测试只做内部测试**：单元测试 + 构建。**不要启动游戏、不要开服、不要用电脑操作去驱动游戏窗口**（太慢，用户叫停过两次）。游戏里来的 bug → 写一个在旧代码上失败、修好后通过的单元测试（纯逻辑放 `com.fluxecho.logic`；NEI 处理器可以在测试里构造）。画面和界面只能用户进游戏看——在回复里说清楚哪些没法测；每版发一张 ≤ 12 项的验收卡（refactor §2.6）。
- **给用户测试的 jar 只用 CI 发布的那个**：`gh release download X.Y.Z -R lanStar2003/FluxEcho -p "fluxecho-X.Y.Z.jar"` 放进 `E:\Game\HMCL\.minecraft\versions\GT New Horizons 2.8.4\flux-test-builds\`（用户自己挪进 mods）。放之前 `javap -c -p` 抽查一个 Block 子类构造器，调用的必须是 `func_*` 名（0.8.2 一次本地构建 reobf 错了，游戏卡在 33%）。崩溃日志里看到 `NoClassDefFoundError: net/minecraft/block/BlockContainer` + 模组状态 `E`，先怀疑 reobf 或别的初始化异常被盖住了。
- **和并行会话协作**：常有另一个会话或别的代理同时在改。推送前 `git fetch` / `git pull --ff-only`；版本号顺延；CHANGELOG 新条目放在最上面；不碰对方声明负责的文件。别用裸 `git stash`（stash 栈是共享的）。
- 提交信息、PR、代码里不写模型名。提交信息结尾按系统提示加 `Co-Authored-By` 行。

**多代理流程（0.17.0 起，refactor §⑧ 8.1–8.5）**
- **角色**：**整合者**（主控会话，一个）写每版的计划和任务卡、做接口提交、按顺序合并、写文档、发布，只有它跑全量构建、推送、打标签；每版 3–6 个**代理**（底座、楼、界面、叙事、QA），每个一张卡、一块绿区，只经任务卡、报告和 RFC 和整合者沟通，不互相商量。全部工作流和子代理用 Opus 5.5、effort xhigh。
- **一版的生命周期**：D0 整合者写 `refactor/v0.XX/plan.md` 和卡 → D0.5 **接口提交**（直接上 main、全量构建绿：本版的消息常量和处理器空壳、新配置节、lang 和任务书的空分片、`Buildings` 行、`ids.md` 本版行「新增 → 本版」、`batches.csv`）→ D1–D4 各代理在自己的工作树里**先提交测试**（期望值抄设计稿里的数）再实现 → D3–D5 整合者按「底座件 → 公共线 → 楼按顺序号 → 叙事 → QA」合并，每合一支跑全量，红了退回那一支 → D5 跑 `LangMerge`、`build_quests.py`、贴图生成器、预览出图，更新 CHANGELOG（固定小节 refactor §8.9）/ README / 本文件 §5 / `docs/dev/*`，发布，发验收卡和预览 PNG。用户的验收结论只落到下一版或补丁版 `0.XX.1`（只翻默认值、改常量、修 bug）。
- **红、黄、绿三区**（refactor §8.3）：**红区**只有整合者改（别人写 RFC `refactor/v0.XX/rfc/<n>.md`）：`FluxEcho.java`、`CommonProxy`/`ClientProxy`、`Config.java` 的登记表 `SECTIONS`、`city/kit/Buildings.java`、`logic/Parts.java`、`logic/ModuleCores.java`、`skin/fixture/FixtureSpec.java`、`nexus/NexusNet.java` 的常量、`campus/Campus.java` 的 `ModuleStatus`、`research.json`、`NEIFluxEchoConfig`、`EchoRecipes` 登记表、生成出来的两份 lang、`lang/src/areas.txt` 和 `order.txt`、`quests/build_quests.py` 和 `questkit.py`、`docs/**`、README、CHANGELOG、本文件、`build.gradle.kts`/`dependencies.gradle`、`ids.csv`/`batches.csv`、`src/test/resources/api/*.sig`。**黄区**（被改造的旧核心类：`TileCityModule`、`UnitUse`、`CityModule`、`CityRender`、`ModuleSpec`、`CityOffer`、`TileNexus`、`BuildJob`、`Builder`、`TileMultiblock`、`ShapeKit`、`kit.py`、`LiftShaft`、`DeckRender`、`FittingRender`、`NexusGui`、`ResearchTree`、`CampusRegistry`、`TerrainRule`）每版只由卡上写明的一个任务改。**绿区**是卡上独占的路径（楼的 `logic/<楼>/**`、`city/<楼>/**`、`lang/src/<楼键>.*.lang`、`quests/lines/city_<楼>.py`、`city/gen/b_<形状>.py`、`plans/<形状>.txt`、`tools/<楼>Textures.java`；界面 `client/ui/**`；叙事 `lang/src/narrative.*`……）。代理确实要在红区加一行时只增、改动最小，并在报告里逐行列出。
- **工作树和 gradle**（refactor §8.5）：每个角色一棵常驻工作树 `D:/Code/FluxEcho-wt/<角色>`，分支 `wip/0.XX/<卡号>`（从本地 main 拉）；不在主仓库目录里开发（整合者和接口提交除外）。gradle 一律 `--offline`，并发时加 `-Dorg.gradle.workers.max=4`（或卡上写的数）；同一棵树上不同时跑两次构建；代理跑 `spotlessApply` + 自己包的快组和慢组，整合者每次合并后跑全量 `build`。用编译期依赖（MUI2、NEI、Angelica……）的新 API 之前先 `javap` 客户端 jar（`E:/Game/HMCL/.minecraft/versions/GT New Horizons 2.8.4/mods/`）核实运行时有。
- **草稿**：并行代理共用一个 scratchpad，草稿和脚本只放自己的子目录 `scratchpad/<卡号>/`，不写顶层。设计稿只读；实施中发现设计稿的数错了，写进报告和本版 `plan.md` 的「勘误」，整合者版末统一改。代理发现自己是在续作（工作树或分支已有提交），保留已有工作接着做，不重建、不 reset。

**设计（新设计的规则摘要；细节和数字以 `REDESIGN.md` 和各定稿为准）**
- **每座楼自己的风格与玩法**：20 座楼加一只搁浅舱，形体两两不同（涂黑后 120 格外对天叫得出名字）、动词两两不同；主玩法一律在世界里的方块和空间上完成（泉阵种真花、箔仓真的压缩、猎场往场里扔印记……），全城不再有「建筑 + 样本座」那种玩法；控制台默认开「运转」页，样本座 / 单元只在次要的「部件」页；相邻两座按高低、虚实、冷暖、明暗、快慢五项对比至少 3 项不同（REDESIGN ②、⑤、⑥.4）。**推翻**了 0.10.1 之后的「先结构、后功能」和「同一种材料、主题色只在渲染的光里」：每座楼按 BLUEPRINT 做自己的功能，按自己的题材用自己的材质。
- **位置固定、按序解锁**（总平面「一叶归舟」）：每座楼一个专属场地号（72–90 = 70 + 舷号）、唯一朝向、唯一顺序号 1–24（主线 16 步 `requiresBuilt` 单链，支线 8 座同时代内任意），没有候选位置；阙 → 桅（中枢）→ 跃迁环三点一线，主轴笔直，「不死板」来自弯的叶脉光渠、水系、两舷错开和高低交替；新园区固定朝东；所有结构离中枢 Chebyshev ≤ 127（`FxCodec`）。楼严格按顺序号交付、每版最多 3 座新形状，所以任何一版开新档看到的城都是总平面 §7.1 某个阶段的子集（refactor §2.5 `BatchPlanTest`）。
- **白舟玄基**（风格）：三层材质 + 一线——脚下暖黑玄武岩「玄基」、身上题材本地的「百皮」（每座楼一对皮材）、骨和冠是珍珠白「舟壳」，每座楼一条指向中枢的中枢青龙骨；全城语法 G1–G13；12 种材质 × 20 种本色；形体是会投影、会被光影照亮的方块（`ShapeGeo` 16 种邻居感知的形 + `ShapeRender`，每个顶点带法线），客户端再叠一张服务端同步的盒子表 `SkinTable`（变体纹样、本色、灯窗、显影），世界方块不为画面改动；成型即显影，只溶解门心晶体、冠顶晶体、悬浮撑（≤ 5% 格子）。0.17.0 的七行调色让旧楼立刻变成「白骨 + 浅暖灰墙 + 玄基墙裙」的过渡态，不给开关（DECISIONS B10）；颜色由 `/fluxecho swatch` 样板墙一轮定。
- **晴空星盘**（界面）：瓷白纸面、墨蓝字、一页一光（强调色 ≤ 8%）、右上切角、深色墨卡浮层、自绘数显字；研究星图是半圆的航图（中枢在地平线、长庚在天顶、九个星座、83 颗星按 `since` 显示）；目标分辨率 854×480（REDESIGN ⑦）。
- **跃迁环与跃升终局**：船尾 80 格外的跃迁环开局就断着（托环 + 23 段，开局四段白角），每座楼首次成型补上它那一段、跃升清单那一行备齐时那段转亮；UHV 末 20 行全部「电换不来」的清单备齐、拱心合拢，满月午夜大潮起航；中枢光柱平时青，跃升后转长庚金（DECISIONS A1、A6；REDESIGN ⑨）。
- **视觉先按「开着光影包」设计**：用户用 Angelica + Iris 跑 Complementary。世界内效果只在 `RenderWorldLastEvent` 画或用 ISBRH 方块网格（不用 TESR）、跳过阴影 pass、每个顶点给法线、只用 quads、popAttrib 后手动 `glEnable(GL_TEXTURE_2D)`、登记 `FarDraw.add`、相对 `RenderManager.renderPos*` 画；`onRenderLast` 每帧可能被调很多次（光门里每扇门的画面各一次），必须幂等：动画只按时间算，不在里面生成粒子、发包、累加计数；服务端按距离挑接收者的包用 `Sight.near`（TE 描述包不用管）（详见 platform.md §4、gates.md §5）。绝不能「光影下退回简单版本然后算作限制」——③（世界画完后的光）每座楼 ≤ 30%，深度在光影下靠不住时由全城公共件 `OcclusionGate` 逐件遮挡接管、**画面一件不删**；保守默认 `render.occlusion = gate`、`render.skyLayer = world`，探针 `probe depth / sky` 过了才在补丁版翻。用户报画面问题，先看 `logs/latest.log` 有没有 "Using shaderpack"。
- **全息投影默认关闭**，可切换（GUI 按钮或螺丝刀）；用户希望不右键也能看到状态（全息、Waila 显示 GT 式基础信息）。**建造投影不是全息**：中枢要建的东西的投影**默认开**，和状态全息分开（配置 `nexus.buildProjection` full/outline/off）。
- **中枢自己建园区和楼（0.10.0 用户定的流程，仍然有效）**：放核心 → 中枢勘测、出投影 → 成员按「开始」→ 供料（补给、投入槽、补给口）→ 自动放置，每块从晶核飞出、带特效落位。**先投影、按开始才动工**；要清理的地方整片清空，机器、箱子连同里面的东西都收进回收箱，按开始前用黄框和「将拆除 N 个……」让玩家看清（重铸时玩家的外来机器默认保留、二次确认才拆）；创造模式不用交材料。楼是**能走进去、在里面真实操作**的房子，控制器站着就能点到，主方块要有完整的 NEI 三维预览（campus.md §0、nexus.md §0）。
- **单方块回响机器的「房屋风格」**：一台机器 + 核心电路分阶 + 自己的贴图/GUI/NEI 页 + 全息默认关 + 通量世界观；每台都要「活着」。改单方块机器的玩法（加核心分阶、吃谐振塔加速……）先问用户（潮汐影响速度、日均 100%、`tides.echoMachines` 可关，是 DECISIONS B6 定过的）。
- 消耗品（钻头等）留在槽里按概率磨损，不在第一次使用时整个消失。成本 = EU + 少量原版材料，全部可配置。通量碎屑**只**从碎片采集器出。不做敌人/威胁事件（猎场「亲猎」默认关，等用户签字）；要覆盖 GTNH 全程（蒸汽 → UHV+）。
- **老存档不停**（refactor §⑤）：四道保险每版都跑——ID 快照（`IdFreezeTest`）、0.16.0 金样存档（`LegacySaveTest`）、旧图冻结（老园区 `Mp` = 0）、旧形状 CRC（`OldShapesCrcTest`）；每座楼新形状上线的同一版，老园区里它的旧形状进「旧形状模式」照常成型、旧单元照旧保管；整图重铸 0.22 起按存档开门；不支持降级。`BuildJob.V`（3）和 `GRADE_V`（2）整程不提。

**代码约定**
- **ID 和名字只增不改、不复用**：[docs/dev/ids.md](docs/dev/ids.md) 是唯一的账（0.17.0 入库，设计稿那份冻结；`ids.csv` 由 `java tools/IdsCsv.java` 从它的 §19 导出，`IdsCsvTest` 守着）。范围：GT 机器 ID（FluxDepths 24520–24529，FluxEcho 24530–24569；offset 3 和 12 已退役）、注册名、TE id、meta、配置键（改默认值要走 `Config.upgrade()`）、NBT 键、`WorldSavedData` 名、网络频道和消息种类号、部件码、单元顺序与槽数、场地号、计划键、步骤种类、形状名、任务 key（ID 由 key 算出，改了玩家进度就丢）、研究 id、`ModuleStatus` 的 ordinal、`RoomPlan` 模板 id、`FoldedZone` 常量、记分项……**0.16.0 已发布的任何含义都不准改**；已发布的行一个字都不改；新 ID 先查账、在表末追加，由整合者在接口提交里定成「本版」、打标签时改「已发布」（ids.md §22）。版本还没到的消息号只有常量、没有处理器。
- **可选模组隔离**：Forestry/Thaumcraft/BloodMagic/MobsInfo/IC2/Botania/AE2/BetterQuesting 的类只能在各自的包里出现（`IsolationTest` 扫编译产物）。平台代码只看 `Mods.xxx` 标志。模块关掉时方块和物品照样注册（存档不丢方块），只是不注册配方和 NEI、机器停机。
- **lang 分片**：`zh_CN` 和 `en_US` 两份同时改，键和占位符一致；fluxecho 的键只写在 `lang/src/<区>.<语言>.lang`（分区表 `lang/src/areas.txt`，platform.md §10），改完跑 `java tools/LangMerge.java` 生成两份 lang 一起提交，**不要手改** `assets/fluxecho/lang/*.lang`（`LangShardTest`：提交的 = 合并结果、两种语言键相同、一个键只在一个分片、键在它前缀的区）。`LangTest` 查不出运行时拼出来的键，要自己核对。玩家可见的中文措辞和 README 一致。FluxLite、FluxDepths 的 lang 不分片，照旧两份直接改。
- **任务书分片**：每条线是 `quests/lines/<线>.py` 的一个模块（`line()`），`quests/build_quests.py` 只装配（`LINES`），共用的辅助在 `quests/questkit.py`；由 `python -B quests/build_quests.py` 生成，**不手改 json**。任务 key 和线的目录永不改（`QuestPackTest` 对着冻结的 `quest/0.16.0.json` 逐任务比：只有名字和描述可以改）。
- **配置分节**：每一节一个 `com.fluxecho.config.XConfig`（字段、`CATEGORY`、`load`），`Config.java` 只有登记表 `SECTIONS` 和 `upgrade()`；旧键和默认值一个字都不改（`ConfigTest` 对着 0.16.0 写出的 cfg 比），新键先登进 ids.md §7。
- 配方一律走 `EchoRecipes`（GT 优先，失败退 Forge，`RecipeCheck` 兜底），包在 try/catch 里只写日志。
- GT 机器的 `getDescription()` 返回空数组（GT 会把第一次见到的描述写进自己的 lang），提示走 `addAdditionalTooltipInformation`。贴图图标必须在机器构造时创建。
- Java：Jabel 编到 J8 字节码；可用 `instanceof X x`、switch 箭头/表达式；不用 record、文本块、`var`。注释是英文完整句子，类级 Javadoc 写「是什么、为什么」。
- 纯逻辑放 `com.fluxecho.logic` 并**先写测试**（期望值抄设计稿里的数）；纹理只由 `tools/*.java` 生成（不要手画）；测试按快慢分组（假世界、全串、出图、跑一秒以上的标 `@Tag("slow")`，platform.md §11）。
- 文档（README、CHANGELOG、BLUEPRINT、手册）用中文；代码、注释、提交信息用英文。README 的模块表、配方表、任务表、配置表要跟着代码更新。

## 4. 常用命令和路径

| 用途 | 命令 / 路径 |
|---|---|
| 格式化、构建 + 测试 | `./gradlew --offline spotlessApply`；`./gradlew --offline build`（全量，含快慢两组）；并发时加 `-Dorg.gradle.workers.max=4` |
| 只跑快组 / 慢组 / 自己的包 | `./gradlew --offline test -PfastOnly --tests 'com.fluxecho.<包>*'`；`./gradlew --offline test -PslowOnly --tests 'com.fluxecho.<包>*'`（`golden-gen` 永远不在构建里跑） |
| 合并 lang 分片 | `java tools/LangMerge.java`（`--check` 只比较） |
| 导出 ID 账 | `java tools/IdsCsv.java`（`docs/dev/ids.md` §19 → `docs/dev/ids.csv`；`--check` 只比较） |
| 重新生成任务书 | `python -B quests/build_quests.py` |
| 重新生成贴图 | `java tools/<X>.java`（Textures、FluxTextures、SpringTextures、GateTextures、NexusTextures、CampusTextures、DepthsTextures、DepthsGuiTextures） |
| 旧建筑生成器 | `D:\Code\FluxEcho-campus\city\gen\`：`python -B report.py` 写 `plans/<形状>.txt` 和 `plans/INDEX.txt` |
| 计划数据 | `src/test/resources/plan/batches.csv`（逐版的楼、环段、星、规模，`BatchPlanTest`） |
| 工作树 | `D:/Code/FluxEcho-wt/<角色>`，分支 `wip/0.XX/<卡号>` |
| 发布（只有整合者） | `git push origin main` → `git tag X.Y.Z && git push origin X.Y.Z` → `gh run watch` → `gh release download ...` |
| 客户端（HMCL） | `E:\Game\HMCL\.minecraft\versions\GT New Horizons 2.8.4\`（`mods/`、`config/`、`logs/`、`flux-test-builds/`） |
| 服务端（旧 GT 476） | `D:\MC-Server` |
| JDK | 25，在 PATH 上；`javap` 可用来查 GTNH jar 的 API；gradle 的工具链是 21 |
| Windows 上写文件 | Python 写文件用 `open(p,'w',encoding='utf-8',newline='\n')`（否则是 CRLF）；带引号或反斜杠的大段内容用 Write 工具，别用 bash heredoc |

## 5. 当前状态（随版本更新这一节）

- **最新发布**：0.17.0「白舟」（2026-10-27；计划 `D:/Code/FluxEcho-campus/redesign/refactor/v0.17/plan.md`，卡 `refactor/FINAL.md` §2.6 的 0.17 卡，各卡和三次合并、规则审查的报告在 `refactor/v0.17/reports/`，验收卡 `refactor/v0.17/ingame.md`，预览 `refactor/v0.17/previews/`）——重新设计的第一版，**只铺底座、不改玩法**（研究、花费、数值、单元、计划、任务进度一项不变）：
  - **F01** 开版与分片：ID 账入库 `docs/dev/ids.md` + `ids.csv`、计划数据 `plan/batches.csv`；lang 分片 + `tools/LangMerge.java`；任务书分片 `quests/lines/*.py`；配置分节 `config/*Config`；快慢测试分组；接口提交（`SKIN_TABLE=7`、`SKIN_DELTA=8`，`render.occlusion=gate`、`render.skyLayer=world`、`style.hullBrightness=1.0`）。
  - **F02** 存档护栏：`IdFreezeTest`（0.16.0 导出的 4,402 项）、0.16.0 金样存档 + `LegacySaveTest`、`OldShapesCrcTest`、`UnitFreezeTest`。
  - **F03** `ShapeGeo`（21 种形的纯几何，每个顶点法线）；**F04** 四个材质族 8 个方块、`ShapeRender`、舷号砖（deck 13）、花床（fitting 14）、活物料码、每楼图例、`LiftShaft.OURS`（落在 `Lift.cell`）、`HullTextures` / `SkinTextures`；**F05** 盒子表 `SkinTable` + 旧楼 `LEGACY` 灯窗（35 个旧形状白天 ≤ 2.79%、夜里 ≤ 11.90%）、园区 NBT `Skn`；**F06** `OcclusionGate`、`SkyLayer`、`probe depth / sky / sun / bed`、`swatch`、`cam A–E`；**F07** 七行调色 + `SkinPalette` + 中枢白肋（`dissolves` 只剩 C、S）+ 墙裙光线取本色；**F08** 界面底座 U0（`Tok`、`Fmt`、`Lay`、`Anim`、`TextModel`、`FluxCanvas`、`UiAtlas`，0.17 没有调用者）；**T01** 叙事 N1（典籍卷零–卷三、17 条舱志、29 颗星注、改名「回响档案馆」「龙骨 / 外舷 / 立桅」、任务线新标题、`echo_general` 1 → 6、`ScoreBridge` v1 登记 31 个 `fe*`、写 22 个）。两刀（`ScoreBridge` v1、中枢白肋）都没砍。
  - 规则审查修了 3 个 bug：盒子表的收件人按玩家 UUID（重生后旧玩家对象不再留在表里）、客户端世界卸载只清本维度的表（换维度不丢表）、探针和样板墙不覆盖园区部件。
  - 全量构建 252 个测试类、1,729 个测试（0.16.0 是 201 / 1,417）。新手册 [docs/dev/skin.md](docs/dev/skin.md)；ids.md 里首发 0.17 的行是「本版」，打标签的提交改「已发布」。
- **之前**：0.16.0「天环」——回响之城的第六批（规格 `D:\Code\FluxEcho-campus\spec\M13.md`，city.md §0）：相位 IV（`phase_4`、`grow:4`）；释放任务 `release`（吊舱的腿拆下、吊舱浮空，`BuildJob.Form` 的 `Pf`/`Pd`/`Sd`/`Rs`，`BuildJob.V` 没提）；天环族 SKY（场地 30–37）和天穹气象站、天光帆阵、观星台、水滴机库；根的第二个形状深根（按地方大小长）；显化物质、反重力晶格、构架的浮空地基；研究和显化的电路付 GT 自己的矿辞（`CircuitTiers`）。更早的版本（0.9.0「地基」到 0.15.0「外环」）见 `CHANGELOG.md` 和各手册的 §0。
- **下一步**：0.18.0「航图」（`refactor/FINAL.md` §2.1、§2.6 的 0.18 卡）：U1 组件库；研究表 `research.json`（83 节点一次登记、按 `since` 显示）+ 九星座半圆航图 P01；舰桥控制台 P02–P11 + 问题抽屉 P68 + 定位光柱；典籍 P57（接手 0.17 的卷页和舱志，`CodexPages` 照用）；FluxLite 终端换装 U3b；读本舱 + `fluxecho_voyage` + 回声定位 + 老存档补授（`XiangBackfill`）；新广场样地 `/fluxecho proto plaza`。整合者先写 `refactor/v0.18/plan.md` 和卡、做接口提交。0.17 留下的：`ScoreBridgeServer` 接团队并集、卷二改按 `recall_echo` 解锁、`Day0`；ids.md 的账务（F04 新族物品名 `tile.fluxecho.<族>.<形名>` 按代码改写 §8 那行；F01 勘误 1——`nexus` 节实为 33 个键——和 F02 的 §16 两条——谐振塔机架叫 `rack-7…10`、`well*` 8 个 `tap` 单元但 handler 16 槽——碰的是「已发布」行，只能追加到 §20 差异表）；`ShapeRender` ↔ 盒子表的接线（0.19 档案馆那一版，走 `SwatchLooks.install`，skin.md §4.8）；可选：`build.gradle.kts` 的 `-PgoldenGen` 名副其实、`fluxecho.cmd.usage` 的值列上新命令。用户的验收结论只落到 0.17.1（翻默认值、调色、修 bug）或下一版。
- **用户在游戏里试过的**：0.9.2 正常（2026-10-09）；0.10.0（2026-10-10，雪地针叶林）报的问题 0.10.1 修了；0.14.1（2026-10-13）报的谐振塔和二向箔仓下雪不成型 0.14.2 修了。**0.10.1、0.11.0–0.16.0 都还没进游戏试**：每个版本只能进游戏看的清单在 city.md §9、campus.md §16、nexus.md §10（开着和关着光影都要看）。**0.17.0 也还没进游戏试**：验收卡 `refactor/v0.17/ingame.md` 的 12 项（样板墙一轮定色、`probe depth / sky / sun / bed`、LuV 老档的过渡态、黄昏灯窗、中枢白肋、样板墙上的明暗和区块重建、20 种本色的夜里白芯、任务书和典籍和记分板、机位组图），skin.md §14 有全部「只能进游戏看」的项。
- **还没定的**：重新设计的未决问题在 `redesign/OPEN-QUESTIONS.md`（A 类要用户定；用户没回答，A1–A22 一律按「推荐默认」实施，refactor 卷末「未决问题」同）；0.16.0 以前替用户做的选择在 BLUEPRINT 第七节 6–12；中枢、营造、城市模块的数值（维持电、算力、研究花费、每块 EU）按 `narrative/FINAL.md` 的账逐版落地。
- **已知未修的小问题**：见各手册的「已知问题」（fluxdepths.md §9 的 lang 第 11 行、campus.md §16、city.md §9、nexus.md §10 光梯的几处、skin.md §14）；0.17.0 的几处（旧楼的光梯光圈白天不亮、潮汐井 / 全息沙盘 / 气象站 / 秘法庭的墙裙光线和名牌颜色不一样、探针在半空摆时告示牌会掉、遮挡门控一次异常后整局全显、`/fluxecho cam` 在生存模式下打开的飞行不会自己关）随验收结论或各楼那一版处理；0.16.0 的几处（释放以后天顶的 keep-out 还按系泊的腿算、留下受保护的腿格会再提供一次释放、找不到中枢时浮空吊舱的基准高度按 40 算、天环四座的墙裙不发光、观星台没有内境门位、显化物质的 30,720 EU/t 没有配置键、电路的矿辞名只用 javap 查过）在重构里随各楼的那一版处理。
