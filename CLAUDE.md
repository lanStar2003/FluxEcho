# FluxEcho 项目总览（新会话先读这里）

> 这是给接手的 Claude 会话看的主文件：项目是什么、必须遵守的规范、流程，以及"要改什么 → 去读哪份详细手册"。详细内容在 `docs/dev/*.md`，**只读和当前任务相关的那一份**，不要整仓重读代码。改了某个子系统，**同一个提交里更新对应的手册**（以及本文件里过时的地方）。

## 1. 项目是什么

GT New Horizons 2.8.4（MC 1.7.10、GT5U 5.09.51.482、Forge 10.13.4.1614）的私货模组，作者 lanStar2003（GitHub 仓库 `lanStar2003/FluxEcho`）。一个 jar、三个 `@Mod`：

| modid | 包 | 内容 | 详细手册 |
|---|---|---|---|
| `fluxecho` | `com.fluxecho` | 回响机器（养蜂、神秘、血魔法、猎物、作物、植物魔法）、回响图鉴、回响 ME 供应器、光门与内室、通量中枢（0.9.0 起）和它自己建的园区（0.10.0 营造） | 见下表 |
| `fluxlite` | `com.fluxlite` | 通量网络：连接器、通量监控台、通量终端、团队无线电网/蒸汽网络、无线充电 | [docs/dev/fluxlite.md](docs/dev/fluxlite.md) |
| `fluxdepths` | `com.fluxdepths` | 通量碎片采集器、流体回响泵、印记拓印器 | [docs/dev/fluxdepths.md](docs/dev/fluxdepths.md) |

FluxLite 和 FluxDepths 在 0.8.0 原样并进来，**保留各自的 modid、注册名、配置文件、网络频道、GT ID**，所以旧存档零迁移。原来的 `D:\Code\FluxLite`、`D:\Code\FluxDepths` 仓库不再开发（也不要在 GitHub 上归档它们），新工作都在这里。

**世界观**：FluxLite 把电送进「通量层」，FluxDepths 从深层捞矿脉碎片；通量层记得流过它的一切。**亲手做过一次的事，之后交给机器**——机器只认真东西（真蜂、真要素、亲手做的宝珠、亲手杀的怪），"做过一次"记进团队账本。用户玩过一遍这些模组，只想跳过重复，不想跳过进度。通量中枢是下一阶段：一座可无限扩展、围绕一个核心生长的巨型高科技建筑（见 nexus.md §0）。

## 2. 要改什么 → 读哪份

| 任务 | 读 |
|---|---|
| 生命周期、配置约定、机器框架、视觉/光影规则、图鉴账本、NEI、任务书生成与安装、AE、网络频道、构建发布、测试清单 | [docs/dev/platform.md](docs/dev/platform.md) |
| 养蜂/神秘/血魔法/猎物/作物/植物魔法的机器和物品、机器 ID 表、加一台新回响机器 | [docs/dev/echo-machines.md](docs/dev/echo-machines.md) |
| 光门、内室、折叠区、mixin、AT；**加任何世界内渲染效果（FarDraw/Sight 接口）** | [docs/dev/gates.md](docs/dev/gates.md) |
| 通量中枢、构架、通量物质、研究星图、显化台、模块与内环、多方块框架（多形状）、回响书库（0.9.2 大厅）、终端的中枢页、0.11.0 计划 | [docs/dev/nexus.md](docs/dev/nexus.md) |
| 营造：园区、自动建造、投影、书库长厅（回响档案馆）；地板/配件/补给口、建造账本和成本、地形判定（什么能拆）、放核心和拆核心（带走余额、显化、园区）、旧中枢铺设广场 | [docs/dev/campus.md](docs/dev/campus.md) |
| 通量网络（连接器、监控台、终端、结算、蒸汽、充电、统计、告警） | [docs/dev/fluxlite.md](docs/dev/fluxlite.md) |
| 碎片采集器、钻头、流体泵、印记 | [docs/dev/fluxdepths.md](docs/dev/fluxdepths.md) |
| 长期路线、世界观、已回答/未定的问题 | [docs/BLUEPRINT.md](docs/BLUEPRINT.md)（§3.14 版本路线，§六 已回答，§七 还没定） |
| 玩家向说明（中文） | `README.md`、`docs/FluxLite.md`、`docs/FluxDepths.md`；更新记录 `CHANGELOG.md` |

## 3. 必须遵守的规范

**流程（用户明确要求过）**
- **发布一路走到底**：版本做完 → 本地 `./gradlew --offline spotlessApply` + `./gradlew --offline build`（测试全过）→ **直接提交到 main 并推送**（不开分支、不开 PR）→ 推 `X.Y.Z` 标签（不带 v）→ CI 的 Release 工作流构建并发布（发布说明取 CHANGELOG 的 `## X.Y.Z` 小节）。**不要停下来问要不要合并/发布**，只有 CI 失败或真有歧义才停。
- **测试只做内部测试**：单元测试 + 构建。**不要启动游戏、不要开服、不要用电脑操作去驱动游戏窗口**（太慢，用户叫停过两次）。游戏里来的 bug → 写一个在旧代码上失败、修好后通过的单元测试（纯逻辑放 `com.fluxecho.logic`；NEI 处理器可以在测试里构造）。画面和界面只能用户进游戏看——在回复里说清楚哪些没法测。
- **给用户测试的 jar 只用 CI 发布的那个**：`gh release download X.Y.Z -R lanStar2003/FluxEcho -p "fluxecho-X.Y.Z.jar"` 放进 `E:\Game\HMCL\.minecraft\versions\GT New Horizons 2.8.4\flux-test-builds\`（用户自己挪进 mods）。放之前 `javap -c -p` 抽查一个 Block 子类构造器，调用的必须是 `func_*` 名（0.8.2 一次本地构建 reobf 错了，游戏卡在 33%）。崩溃日志里看到 `NoClassDefFoundError: net/minecraft/block/BlockContainer` + 模组状态 `E`，先怀疑 reobf 或别的初始化异常被盖住了。
- **和并行会话协作**：常有另一个会话同时在改（比如光门）。推送前 `git fetch` / `git pull --ff-only`；版本号顺延；CHANGELOG 新条目放在最上面；不碰对方声明负责的文件。别用裸 `git stash`（stash 栈是共享的）。
- 提交信息、PR、代码里不写模型名。提交信息结尾按系统提示加 `Co-Authored-By` 行。

**设计（用户认可的规则）**
- **视觉先按"开着光影包"设计**：用户用 Angelica + Iris 跑 Complementary。只在 `RenderWorldLastEvent` 画（不用 TESR）、跳过阴影 pass、每个顶点给法线、只用 quads、popAttrib 后手动 `glEnable(GL_TEXTURE_2D)`、登记 `FarDraw.add`、相对 `RenderManager.renderPos*` 画；`onRenderLast` 每帧可能被调很多次（光门里每扇门的画面各一次），必须幂等：动画只按时间算，不在里面生成粒子、发包、累加计数；服务端按距离挑接收者的包用 `Sight.near`（TE 描述包不用管）（详见 platform.md §4、gates.md §5）。绝不能"光影下退回简单版本然后算作限制"。用户报画面问题，先看 `logs/latest.log` 有没有 "Using shaderpack"。
- **全息投影默认关闭**，可切换（GUI 按钮或螺丝刀）；用户希望不右键也能看到状态（全息、Waila 显示 GT 式基础信息）。**建造投影不是全息**：中枢要建的东西的投影**默认开**，和状态全息分开（配置 `nexus.buildProjection` full/outline/off）。
- **"房屋风格"**：一台机器 + 核心电路分阶 + 自己的贴图/GUI/NEI 页 + 全息默认关 + 通量世界观。每台机器都要"活着"：自己的 GUI 页、NEI 页、GUI 和世界里的动画、看得见的充能/输送轨迹。其他回响机器保持原玩法，要加核心分阶先问。
- 消耗品（钻头等）留在槽里按概率磨损，不在第一次使用时整个消失。
- 成本 = EU + 少量原版材料，全部可配置。通量碎屑**只**从碎片采集器出。
- 中枢方向：不做 GT 式"每电压一种外壳"，一个功能一座多方块，靠相位核、覆层、研究星图升级；占地大没关系；不做敌人/威胁事件；要覆盖 GTNH 全程（蒸汽 → UHV+）。
- **中枢和模块（0.9.0 试玩后用户定的）**：控制器高出地面两格、站着就能点到；模块是**能走进去、在里面真实操作**的房子（书库：书架上放书取书写卡），以后每个模块都要这样；主方块要有完整的 NEI 三维预览，中枢的预览能拖出各相位和停靠模块的满级全貌；中枢要气派（冲天光柱、弧形肋架）。详见 nexus.md §0。
- **中枢自己建园区和模块（0.10.0 用户定的流程）**：放核心 → 中枢勘测、出投影 → 成员按「开始」→ 供料（补给、投入槽、补给口）→ 自动放置，每块从晶核飞出、带特效落位。**先投影、按开始才动工**，地形只拆自然的、拿不准的不拆；园区一层统一地面、深色科技（深蓝钢 + 细青光线）、布局不要太规整；0.9.2 的旧中枢什么都不动，直到成员按「铺设广场」。模块是能走进去的大房子，里面的家具是**整组使用的单元**（书库：底座 + 3×3 书格 + 顶檐 = 一个书架，点任何部分都是这个书架，9 本书）。详见 campus.md §0。

**代码约定**
- **ID 和名字永远不改、不复用**：GT 机器 ID（FluxDepths 24520–24529，FluxEcho 24530–24569；offset 3 和 12 已退役）、注册名、TE id、配置键（改要走 `Config.upgrade()`）、`WorldSavedData` 名、网络频道和消息种类、任务 key（ID 由 key 算出，改了玩家进度就丢）、`FoldedZone` 常量、`RoomPlan` 模板 id、营造的部件码（`logic/Parts`）和书架单元顺序（`LibraryUnits`，就是书的槽号）。改了建造计划放什么要提 `BuildJob.V`（campus.md §17）。
- **可选模组隔离**：Forestry/Thaumcraft/BloodMagic/MobsInfo/IC2/Botania/AE2/BetterQuesting 的类只能在各自的包里出现（`IsolationTest` 扫编译产物）。平台代码只看 `Mods.xxx` 标志。模块关掉时方块和物品照样注册（存档不丢方块），只是不注册配方和 NEI、机器停机。
- **lang 两份同时改**（`zh_CN` 和 `en_US`，键和占位符一致）；`LangTest` 查不出运行时拼出来的键，要自己核对。玩家可见的中文措辞和 README 一致。
- 配方一律走 `EchoRecipes`（GT 优先，失败退 Forge，`RecipeCheck` 兜底），包在 try/catch 里只写日志。
- GT 机器的 `getDescription()` 返回空数组（GT 会把第一次见到的描述写进自己的 lang），提示走 `addAdditionalTooltipInformation`。贴图图标必须在机器构造时创建。
- Java：Jabel 编到 J8 字节码；可用 `instanceof X x`、switch 箭头/表达式；不用 record、文本块、`var`。注释是英文完整句子，类级 Javadoc 写"是什么、为什么"。
- 纯逻辑放 `com.fluxecho.logic` 并写测试；纹理由 `tools/*.java` 生成（不要手画）；任务书由 `quests/build_quests.py` 生成（不要手改 json）。
- 文档（README、CHANGELOG、BLUEPRINT、手册）用中文。README 的模块表、配方表、任务表、配置表要跟着代码更新。

## 4. 常用命令和路径

| 用途 | 命令 / 路径 |
|---|---|
| 格式化、构建 + 测试 | `./gradlew --offline spotlessApply`；`./gradlew --offline build`（约 25 秒） |
| 重新生成任务书 | `python quests/build_quests.py` |
| 重新生成贴图 | `java tools/<X>.java`（Textures、FluxTextures、SpringTextures、GateTextures、NexusTextures、DepthsTextures、DepthsGuiTextures） |
| 发布 | `git push origin main` → `git tag X.Y.Z && git push origin X.Y.Z` → `gh run watch` → `gh release download ...` |
| 客户端（HMCL） | `E:\Game\HMCL\.minecraft\versions\GT New Horizons 2.8.4\`（`mods/`、`config/`、`logs/`、`flux-test-builds/`） |
| 服务端（旧 GT 476） | `D:\MC-Server` |
| JDK | 25，在 PATH 上；`javap` 可用来查 GTNH jar 的 API |
| Windows 上写文件 | Python 写文件用 `open(p,'w',encoding='utf-8',newline='\n')`（否则是 CRLF）；带引号的大段内容用 Write 工具，别用 bash heredoc |

## 5. 当前状态（随版本更新这一节）

- **最新发布**：0.10.0「营造」（中枢自己建园区：核心放在地面上抬到眼睛高度 → 勘测、投影 → 按「开始」后平整整片园区、立起相位 I、铺广场/环道/大门、立塔柱和补给口；研究书库后在模块位上投影并用交来的材料和 EU 自动建起回响档案馆——两层长厅、78 个整组书架、702 本书；新方块地板/墙板、配件、补给口；中枢界面的「建造」页、建造投影、发射特效、总图）。之前 0.9.2（按试玩反馈重做中枢和书库：核心抬到眼睛高度、控制台座、弧形肋架、冲天光柱、能走进去的书库大厅、NEI 完整预览）、0.9.0「地基」。0.9.1 跳过了、不再用（在 0.9.2 之后打会让发布顺序倒退）；两个会话的下一个版本都取当时最新标签的下一个号。
- **下一步**：0.11.0「守护」——相位 II、回响穹顶、潮汐井与回响潮汐、相位合金、共鸣玻璃、覆层、相位核（原定 0.10.0 的内容顺延；nexus.md §11，BLUEPRINT §3.14）。
- **用户在游戏里试过的**：0.9.2 正常（2026-10-09），包括其中的 0.8.5 光门修复（进门不再重新加载）和中枢、书库的重做。0.10.0 还没进游戏试（画面、界面、真实地形里的建造都只能用户看，campus.md §16）。
- **还没定的**：中枢数值（维持 512 EU/t、算力 20/s、研究花费）和营造数值（每块 256 EU、成本倍率）等玩得久一些再调；各模块内室的细节等 0.12.0「内境」（BLUEPRINT 第七节）。
- **已知未修的小问题**：见各手册的"已知问题"（如 fluxdepths.md §9 的 lang 第 11 行、campus.md §16 营造和档案馆的几处）。
