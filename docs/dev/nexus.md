# 开发手册 · 通量中枢、研究、模块、回响书库（0.9.0 起，0.9.2 重做布局，0.10.0 营造）

> 改 `matter/ frame/ nexus/ library/ research/ render/Shapes`、做 0.11.0 及以后的版本之前读这一篇。**园区、自动建造、投影、回响档案馆**（`campus/`、`campus/client/`、书库的档案馆部分、`ItemBlockNexusCore`）在 [campus.md](campus.md)，这里只写它们怎么接进中枢。路线和设计在 [BLUEPRINT](../BLUEPRINT.md) 3.2–3.11、3.14。总览见 `CLAUDE.md`。
> 路径相对 `src/main/java/com/fluxecho/`。`EchoText.t("x")` 对应 lang 键 `fluxecho.x`。

## 0. 定位与已定的设计

- 用户定的方向：一个**可无限扩展的模块化巨型建筑**，围绕一个核心（通量中枢），像 GTNH 的水处理厂：模块停靠到中枢的环上，中枢自己按相位进化，建满了是一座漂亮的高科技建筑；不规则形状、大量动画。占地 200 格以上没关系（"做的就是大建筑"）。
- **不做 GT 式"每个电压一种外壳"**：一个功能一座多方块，靠相位核、覆层（长在结构上看得见）、自己的通量算力 + 研究星图升级。新的非原版材料卡进度，做每种材料要用前面的设备。成型后变成非标准的动画形状。
- 能量统一走团队无线电网；界面、全息、NEI、动画、美术风格统一。
- 布局：同心环（内环 8 槽 r=32、外环 12 槽 r=72、后期第三环 16 槽 r=120）+ 空中（天顶、天环、轨道）+ 根位。0.10.0 起 active 园区的模块不再停在内环槽上，而是建在园区的**模块位**上（场地 4/2/6，径向 24 起，campus.md §2），方向和同号槽一致。
- **用户在 0.9.0 试玩后定的规矩（0.9.2 起，以后每个模块都照做）**：
  - **控制器在眼睛的高度**：高出地面两格（中枢脚下垫控制台座，模块在前墙里离地基两格），站着就能点到，绝不放在地上；
  - **模块是能走进去的房子，在里面真实操作**：书库不是只在中心点开界面，而是走进大厅、在书架上放书取书写卡；以后的模块也要有"进去干活"的那一层（实体交互 + 界面并存），界面只是同一份数据的另一种看法；
  - **NEI 预览要完整**：主方块在 NEI 里有三维预览、能看用料；中枢的等级滑块能看各相位和停靠模块的满级全貌；
  - **中枢要气派**：光柱冲天、远处可见；支撑件是弧形分节肋架，不是直柱；
  - 渲染不许"从角上看进去"：画在方块里面的东西只在相机进到里面时画。
- **0.10.0「营造」定的**（详见 campus.md §0）：中枢**自己建园区和模块**——放核心 → 勘测、投影 → 成员按「开始」→ 供料 → 自动放置 + 特效；**建造投影默认开**、和状态全息是两回事（`buildProjection`）；模块是能走进去的大房子，书库里的书架**整组为单位**（一个书架 9 本，点书架的任何部分）；旧中枢不动，直到成员按「铺设广场」。
- **0.9.0 实际做法（和 BLUEPRINT 3.13 原计划不同）**：中枢和模块是**自己的 TileEntity**（`nexus.TileMultiblock`），不是 GT MTE，不占 GT 机器 ID；电直接从团队无线电网取，不需要能源仓；StructureLib 照样用（`ISurvivalConstructable` 投影和自动搭建，`IMultiblockInfoContainer` 给 NEI 三维预览）。

## 1. 文件

| 文件 | 作用 |
|---|---|
| `matter/MatterModule` | 物品 `flux_grit`、`flux_crystal`、`echo_crystal`，矿辞 `dustFluxGrit`/`gemFluxCrystal`/`gemEchoCrystal`，高压釜配方 |
| `matter/ItemMatter`、`ItemEchoCrystal`、`Grit` | 简单物品；回响晶（NBT `Cat`/`Key`，`of(cat,key,n)`）；`Grit.roll(rand, ownerUuid)` |
| `matter/client/EchoCrystalRender` | `IItemRenderer`（INVENTORY）：晶体上画记录图标 |
| `frame/BlockFrame`、`ItemBlockFrame`、`FrameModule` | 9 个 meta 的构架方块、物品、注册；配方从 `logic/PartRecipes` 注册（晶核座手写）；`BlockFrame.onUse(Use)` 让成型的结构接管右键（书架、控制台座） |
| `frame/Formed` | 已成型表：坐标 → `HIDE=1`/`PASS=2`；客户端、服务端各一份 |
| `frame/FrameEvents` | 构架方块放下/破坏（`onBlockAdded`/`breakBlock`，`Builder.QUIET` 时不发）→ 通知覆盖这里的控制器马上复查 |
| `frame/client/FrameRender`、`FrameClient`、`ShelfUnits` | ISBRH（HIDE 的格子不画）；注册、世界卸载时清客户端表、登记 `shelf_board` 图标；档案馆书架单元的一格（campus.md §12） |
| `nexus/TileMultiblock` | 多方块控制器基类，可以有多个形状（第 3 节） |
| `nexus/TileModule` | 模块基类：停靠状态、`dockedUntil`、拒绝原因 |
| `nexus/TileNexus` | 中枢本体（第 4 节）；持有园区 `Campus` |
| `nexus/BlockNexus`、`BlockNexusCore`、`ItemBlockNexus`、`ItemBlockNexusCore` | 控制器方块基类（朝向放置者、右键开 MUI、权限、掉落）；中枢核心（拆下时带走园区余额和显化，campus.md §8）；模块核心物品（`<key>.tip`，Shift 显示 `<key>.structure`）；中枢核心物品（右键地面抬高两格放、触发营造） |
| `nexus/NexusModule` | 注册 `fluxecho:nexus`（物品 `ItemBlockNexusCore`）和 TE、InfoContainer、Waila IMC、核心配方、`Manifests.init()`；`infoContainer(key)`、`previewModule(core, tile)` |
| `nexus/Manifests`、`Costs`、`Records`、`Recorded` | 显化配方表；花费 ↔ 物品（`matches/options/plan/takeFrom`）；团队图鉴记录（`count/next/resting/entry`）；StructureLib 元素包装（记下实际检查过的格子） |
| `nexus/NexusRegistry`、`ClientTiles` | 服务端已加载的中枢和模块（按维度）；客户端已加载的多方块；`NexusRegistry.loaded(world)` 两边通用 |
| `nexus/Consoles` | 控制台座的右键：属于哪个成型结构（`consoleAt`）就开它的界面 |
| `nexus/NexusBinding` | 终端 ↔ 中枢绑定（终端 NBT `feNexus{Dim,X,Y,Z}`） |
| `nexus/InfoContainer` | StructureLib `IMultiblockInfoContainer`（NEI 预览、投影仪任意朝向） |
| `nexus/NexusNet` | 频道 `fluxecho_nx`：星图请求/应答；营造的 `BUILD_FX`/`BUILD_STATE`、书库的 `LIB_DELTA`（第 7 节） |
| `nexus/NexusGui`、`client/NexusScreen` | 中枢 MUI 窗口，[中枢][建造] 两页；自绘部分、打开星图 |
| `nexus/TerminalView` | 服务端给终端的机器/研究/中枢页组装 NBT |
| `nexus/NexusWaila`、`NexusNei` | Waila；NEI id 常量 `fluxecho.manifest` |
| `nexus/client/NexusRender`、`ModuleRender` | 画成型的中枢和模块（`RenderWorldLast` + `FarDraw`）；按模块类型分派 |
| `library/TileLibrary`、`BlockLibraryCore`、`LibraryModule`、`LibraryGui`、`Lending`、`ItemLibraryCard`、`Shelves` | 回响书库（第 6 节）：两种形状；`LibraryModule` 还登记营造的 `ModuleSpec`；`LibraryGui` 按形状分派窗口；`Shelves` 是 0.9.2 大厅书格的右键 |
| `library/Units`、`ArchiveGui`、`ArchiveMap`、`BookPacket`、`BookSlot`、`LibraryVault` | 档案馆的书架交互、界面、平面图、书的包、书格槽、队伍书库仓库（campus.md §12） |
| `library/client/LibraryRender`、`LibraryScreen` | 大厅的屋顶图鉴、书和书井（档案馆转给 `ArchiveRender`）；大厅 GUI 自绘 |
| `library/client/ArchiveRender`、`ArchiveLook`、`ArchiveFrame`、`ArchiveScreen`、`LibraryBooks` | 档案馆的画面和界面、书架网格里的书（campus.md §12） |
| `research/Research`、`ResearchData`、`client/StarMapScreen` | 服务端门面和 id 常量；存档 `fluxecho_research`；星图界面 |
| `render/Shapes` | 世界里的光影安全几何库（第 8 节） |
| `nei/ManifestHandler` | 显化台 NEI 页 |
| `logic/Blueprint`、`NexusShape`、`LibraryShape`、`RingSlots`、`ResearchTree`、`CostPlan` | 纯逻辑 + 测试；档案馆和园区的逻辑类（`ArchiveShape`、`LibraryUnits`、`CampusPlan`、`BuildPlan`……）见 campus.md §1 |
| `campus/`、`campus/client/` | 营造：园区、建造引擎、补给口、投影和特效（campus.md） |
| `tools/NexusTextures.java`、`tools/CampusTextures.java` | 0.9.0 的构架/中枢/书库/通量物质/索引卡贴图；0.10.0 的地板、配件、书架单元、补给口贴图 |
| FluxLite 侧 | `com/fluxlite/gui/TerminalScreen`、`net/Kinds.TERM_REQUEST=4/TERM_DATA=3`、`item/ItemFluxTerminal`（见 [fluxlite.md](fluxlite.md)） |
| FluxDepths 侧 | `com/fluxdepths/shard/ShardWork` 掷碎屑（见 [fluxdepths.md](fluxdepths.md)） |
| 其他 | `codex/EchoNet`（研究随账本下发 `@research`）、`codex/ClientLedger`、`core/Directory`（中枢的 `building`/`build_paused`）、`core/MTEEchoMachine.sample()`（解析索引卡） |

## 2. 物品和方块

| 中 / 英 | 注册名 | 矿辞 | 来源 |
|---|---|---|---|
| 通量碎屑 Flux Grit | `fluxecho:flux_grit` | `dustFluxGrit` | **只**从碎片采集器出：每凝结一次矿石按 `gritChance`（0.15）掷，主人团队**自己**研究了 `grit_yield` ×1.5；输出槽 1 放不下就丢掉，不挡矿 |
| 通量结晶 Flux Crystal | `fluxecho:flux_crystal` | `gemFluxCrystal` | GT 高压釜：`crystalGrit`(3) 碎屑 + 末影珍珠粉 + 250 L 蒸馏水，600 tick，120 EU/t（只看 `defaultRecipes`） |
| 回响晶 Echo Crystal | `fluxecho:echo_crystal` | `gemEchoCrystal`（meta 0 登记，"the ore dictionary does not look at NBT"） | 只能显化；NBT `{Cat, Key}` = 一条图鉴记录 |
| 书库索引卡 Library Index Card | `fluxecho:library_card` | — | 显化；空白卡堆 64，写好的堆 1（NBT `{Sample, Team}`） |

**构架 `fluxecho:frame`**（硬度 5、抗性 30、镐 2，`LIGHT={0,7,0,12,8,15,4,8,10}`；配方要 `defaultRecipes && nexusEnabled`）。0.10.0 起配方从 `PartRecipes`（合成和营造成本同一张表，campus.md §3）经 `PartBlocks.register` 注册，小写键先过 `GtPattern`；石砖接受任意变种；晶核座要 GT HV 外壳、表里写不出来，仍由 `FrameModule` 手写，营造只收成品。

| meta | 常量 | 名字 | 碰撞 | 配方 |
|---|---|---|---|---|
| 0 | `BASE` | 构架基座 | 整块 | ×8 `BSB/SGS/BSB`（石砖、不锈钢板、碎屑） |
| 1 | `BASE_LIT` | 导能基座 | 整块，顶面发光 | ×4 `GBG/BCB/GBG`（萤石粉、基座、结晶） |
| 2 | `PILLAR` | 构架支柱 | 5–11/16 | ×4 `R R/RGR/R R`（不锈钢杆、碎屑） |
| 3 | `CONDUIT` | 导能柱 | 4–12/16，内芯发光 | ×1 ` C /GPG/ C `（结晶、玻璃板、支柱） |
| 4 | `RING` | 悬浮环段 | y 5–11/16，侧面发光 | ×4 `SCS/SSS`（不锈钢板、结晶） |
| 5 | `SEAT` | 晶核座 | 下半块，晶体发光 | ×1 `CEC/CHC/SSS`（结晶、末影之眼、HV 外壳、不锈钢板） |
| 6 | `FOUNDATION` | 模块地基 | 整块，顶面发光 | ×8 `BSB/SES/BSB`（基座、不锈钢板、回响晶） |
| 7 | `SHELF` | 回响书格 | 整块，侧面发光；在档案馆书架单元里画成书架的一格 | **×32**（0.10.0 起）`SBS/BEB/SBS`（钢板、书架、回响晶） |
| 8 | `CONSOLE` | 控制台座 | 1–15/16 宽、整高（脚、柱、台面），柱侧和台面发光 | ×2 `SCS/ P / L `（不锈钢板、结晶、支柱、导能基座） |

贴图 `textures/blocks/frame/<name>_top|_side`；`*_glow`、`conduit_core`、`seat_crystal` 是 8 帧动画（frametime 3）；书架单元另有 `shelf_niche`、`shelf_board`、`book_spine`（`CampusTextures`）。

**控制器方块**（`BlockNexus` 子类，铁、硬度 6、抗性 60、亮度 0.5）：放下正面朝向放置者（`{2,5,3,4}[look]`）并 `FrameEvents.changed`；右键 `BlockNexus.open(m,p)` → 静态 `mayUse`（没主人人人可用；OP 创造可用；中枢要 `member(p)`；模块要同队）→ MUI，否则 `fluxecho.nexus.not_yours`；破坏掉出库存 + `dropsMore`；图标 `front`/`front_on`（成型）/`side`/`top`。
- 通量中枢 `fluxecho:nexus`（TE `fluxecho:nexus`）：配方 `CXC/RHR/CMC`（结晶 ×4、回响图鉴、HV 电路 ×2、HV 外壳、FluxLite 通量监控台）。物品是 `ItemBlockNexusCore`：右键地面顶面（不潜行）放在高出点击方块两格处、园区变 active、建 establish 任务；潜行、点侧面、点我们的方块 = 0.9.2 手动放（legacy）。拆下：带余额或显化的核心从 `breakBlock` 掉（`BuildCredit`/`Pending`/`Manifest`），不带的走普通 `getDrops`；active 园区的核心两种掉法都再带上园区 `Campus`，放回原处（同一维度同一方块，怎么放都行）园区接着用（campus.md §8）。
- 回响书库核心 `fluxecho:library`（TE `fluxecho:library`）：显化。手放在某座已加载中枢的模块位控制器格上时自动朝向中枢。拆下时书进队伍书库仓库（第 6 节）。
- 营造的方块——地板/墙板 `fluxecho:deck`、配件 `fluxecho:fitting`、补给口 `fluxecho:supply_port`——见 campus.md §3。

**显化配方**（`Manifests`，按注册顺序尝试，不受配置开关控制）：

| id | 研究 | 输入 | 产出 | tick | record |
|---|---|---|---|---|---|
| `echo_crystal` | `echo_crystal` | 通量结晶 ×1 | 回响晶（写入一条记录） | 200 | ✓ |
| `library_core` | `library` | 回响晶 ×4、回响图鉴、HV 电路 ×2、书 ×16 | 书库核心 | 1200 | |
| `library_card` | `library` | 回响晶 ×1、纸 ×8 | 空白索引卡 ×8 | 100 | |

## 3. 多方块框架

**`Blueprint`**：`String[layer 从顶往下][row 从前往后]`，空格 = 任意，`~` = 控制器（StructureLib `transpose` 格式）。`world(a,b,c,x,y,z,fx,fz)` 把格子换成世界坐标，`cell(...)` 是它的逆（世界方块 → 格子），`point(...)` 把世界里的点换成形状坐标；**图必须沿跨向镜像对称**（StructureLib 的跨轴怎么走都不影响，`symmetric()` 有测试）；`centreBack()=(depth-1)/2-ctrlC`；`cells()` 列出所有非空格子。

- `NexusShape.phase(p)`（p=1..5，`PHASE_1=phase(1)`）：11×11，高 `1+5p`。底座 y0 `|dx|,|dz|≤5 且 |dx|+|dz|≤7`（97 块，辐线 `dx==0||dz==0||\|dx\|==\|dz\|` 用 G 导能基座）；控制器 `(0,-5)` 的 y2（`CONTROLLER_UP=2`），脚下 y1 是 K 控制台座。往上每 5 层一节（`s=(y-1)%5`）：s0–1 支柱 `(±3,±3)`、s2–3 支柱 `(±2,±2)`（成型后是弧形肋架）；中心 s0–2 导能柱、s3 晶核座；s4 环段 `2.5≤d<3.5`（16 块），中心空着（晶核浮在那）。每相位 +16P +16R +3C +1S，低相位原样包含在高相位里，底座和控制器位置不变。`dissolves`：P/C/R/S（K 不溶解）。
- `LibraryShape.BLUEPRINT`（0.9.2 大厅，书库的 `hall` 形状）：13×13×9，x 跨向、z 从前往后、y 从地基往上。y0 全是 F；四角 `(1|11,1|11)` 每层 P；墙 y1–5 是 H（167 块），前墙 `x=3,4,8,9` 的 y1–3 是门（空格），控制器 `(6,2,1)`；大厅 x,z 2–10 空着，只有 `(6,1,6)` 是 K 阅读台；y6 天花板 B，`x,z 4–8` 是 `-`（必须是空气）；y7 阁楼：边 B、天井上方 `-`、其余任意；y8 屋顶：边 P、里面 B。`SHELVES` 按 y→z→x 排序，就是样本槽的顺序，每个带朝大厅的方向 `inX/inZ`。`cellOf(x,y,z)={x, 8-y, z}`。
- `ArchiveShape.BLUEPRINT`（书库的 `main` 形状，回响档案馆，27×35×16）：见 campus.md §12。字符在所有书库形状里唯一，共用的字符（`F`、`K`、`~`、`-`）意思相同。

**多形状**（0.10.0）：一个控制器可以有几种形状。`shapes()` 列出 `Shape{name, bp}`（第一个是 NEI 预览显示的；默认只有 `MAIN="main"` = `blueprint()`）；`tries(s)`（配置可以排除某个形状，书库的 `archiveEnabled`）；`legacyShape()`（存档里没有形状时当哪个，书库是 `hall`）；`currentShape()/shapeName()/current()` = 记住的那个；`shapeChanged(was)` 在换了形状、`formedChanged` 之前调。两个结构定义都按名字带上所有形状：`define(record, names[], shapes[], ch, block, meta, ...)`。凡是格子 ↔ 方块的换算（`cellPos`、`cellAt`、`bounds`、渲染）都跟着当前形状走。

**`TileMultiblock` 生命周期**：
- 抽象：`blueprint()`、`definition()`（检查用，元素包 `Recorded`）、`buildDefinition()`（投影仪和 NEI 用，**元素不包装**——blockrenderer6343 靠 `StructureHacks.getStacksForElement` 认元素，认不出 `Recorded`）、`coreBlock()`、`flags(char)`、`descriptionKey()`；钩子 `formedChanged`、`serverTick`、`clientTick`、`writeSync/readSync`；`consoleAt(x,y,z)`（默认 false）。
- **检查**：`definition().check(this, 形状名, world, ExtendedFacing.of(front), x,y,z, ctrlA,ctrlB,ctrlC, false)`；服务端先试记住的形状，再按顺序试其他（`tries` 允许的），匹配上就记住（换了形状 → `shapeChanged`）；检查前清空 `seen`，`Recorded` 每通过一格记一次 → 隐藏表和 StructureLib 实际检查的布局**完全一致，不用自己算旋转**。
- **服务端复查**：成型后每 100 tick、未成型每 40 tick；`place()`/`validate()`/`frameChanged()` 让下一 tick 立刻复查。**已成型的结构有区块没加载时不复查**（StructureLib 会把没加载的方块当成错的），20 tick 后再看。变化时设 `formed/formedTime`、`applyCells()` 或 `clearCells()`、sync、`formedChanged(ok)`。
- **`applyCells(level)`**：`seen` 里 `flags(ch)!=0` 且 `y≤level` 的写进 `Formed`，并算 `bounds`；**只有交给 `Formed` 的格子变了才在客户端重画区块**（书库没有溶解的格子，从不重画；中枢扫光时逐层重画）。
- **`bounds()`**：检查过就是找到的部件范围；之前是当前形状绕控制器的整个盒子（不会返回 null）。`covers()` 用最大形状的立方体（控制器周围 ±(最长边 + 1)：中枢 ±12、书库 ±36）；营造判断我们的方块是不是散落的也看它（已成型结构的 `covers()` 里的不算散落，campus.md §5）。
- **`Formed`**：坐标打包 long；**客户端是不可变 map 整体替换**（"its chunk builder may ask from another thread"）；服务端按维度、只在服务器线程。`BlockFrame` 在 PASS 时去掉碰撞，`FrameRender` 在 HIDE 时不画。
- **客户端扫光**：收到变为成型的描述包时 `formedAt = now - age*50ms`（"a structure seen for the first time long after it formed does not play the forming sweep again"）；客户端世界按服务端给的形状跑一次 `check()`；`sweepLevel()` 在 `SWEEP_MS=2000` 内**从下往上**，每过一层把 `y≤level` 的格子交给 `Formed` 并触发区块重画。成型状态下换了形状 → 清掉旧格子、重新扫光。
- **描述包**：`F` 朝向、`On`、`T` formedTime、`O` 主人名、`Sh` 形状名 + `writeSync`。
- **`realWorld()`**：服务端要 `WorldServer`，客户端要 `clientWorld.isInstance(worldObj)`（`ClientProxy` 设为 `WorldClient.class`）。NEI 预览/假世界里**不 tick、不进 `FrameEvents`/`ClientTiles`/`NexusRegistry`/`Formed`**。
- **NBT**：`Facing`、`OwnerMost/OwnerLeast`、`OwnerName`、`Formed`、`FormedTime`、`Shape`（没有就是 `legacyShape()`）。`team()` = `Owners.team(owner)`。`place(UUID, ownerName, front)` 让营造引擎不经玩家给模块定主人和朝向。
- **投影仪 / NEI**：`getStructureDefinition()` 返回 `buildDefinition()`；`construct` → 预览世界里先 `placeOwnBlock()` → `buildShape(trigger, hints)`（真实世界建当前形状，预览建第一个形状）；`survivalConstruct` 预览世界里整座 `construct` 返回 -1，真实世界已成型返回 -1，否则 `survivalBuild(当前形状)`；`InfoContainer` 临时改 `facing` 再还原。
- **NEI 预览（blockrenderer6343）怎么工作**：扫 `IMultiblockInfoContainer.MULTIBLOCK_MAP`，`Class.forName` 新建 TE 放进 `ObserverWorld`（继承 `World`，不是 `WorldClient`，所以 `realWorld()=false`）的 (0,64,0)，**不放方块**；用 stackSize 1..64 的触发物调 `construct` 估计等级数（结果不变就停），再从控制器位置的方块取多方块的物品——所以 `placeOwnBlock()` 必须先放下 `coreBlock()`（并把自己设回 TE），否则 NEI 里没有这个预览。界面里先放方块、再 `survivalConstruct`、再 `construct`；等级滑块就是触发物的 stackSize。通道只认带 `withChannel` 的分级元素，所以中枢把"选项"编进等级里。

**`TileModule`（停靠）**：`moduleKey()`（lang `fluxecho.module.<key>`）、`moduleColor()`、`centreCell()`（地基中心格；`blueprint()`/`centreCell()` 同包可见，中枢的预览要用）；`nexus()` 有效 = 中枢有效且成型且 `worldTime ≤ dockedUntil`；`dockStatus()` = `unformed/docked/no_nexus/拒绝原因`；同步 `Slot/Docked/Nexus`。

**`TileNexus.refreshDock`**（每 100 tick，成型且启用时，不要求有电）：按优先级找模块：（1）园区记录在场地上的控制器；（2）active 园区里站在模块位上的已成型模块（有 `ModuleSpec`、中心在 `CampusPlan.moduleCentre(site, spec.depth)` 两轴 ±3 内、离底座层上下 ≤1、正面朝中枢）→ 收编并 `campus.recordSite`；（3）0.9.2 内环：`RingSlots.slotAt(模块中心−中枢中心, innerRadius, front)`（水平每轴 ±3、竖直 ±6；槽 0 正前方，其余每 45° 顺时针）。场地上的模块用场地号当槽号（方向和同号槽一致）。不在 `teams()` 的拒绝 `other_team` → 按优先级、再按槽号 0→7 处理，同槽第二个 `slot_taken`，满了 `ring_closed`（open==0）/`ring_full` → 停靠或拒绝都给 220 tick 租约；结束时 `dockKnown=true`（`undockAll` 清掉；营造的自动任务要等它）。`openSlots()` = `has(INNER_RING) ? RingSlots.open(PHASE) : 0`（相位 1 = 2，2 = 4，3+ = 8），active 园区再封顶 `MAX_CAMPUS_MODULES`=7。

**加一个新模块（照书库）**：
1. `logic/XShape` + `XShapeTest`：对称、有 `~`、底层是 FOUNDATION；**控制器离地基两格、在前墙里朝外，留门，里面是能走进去干活的空间**（第 0 节）；字符和已有形状不冲突（共用的字符意思相同）；定义哪些字符 HIDE/PASS（能走进去的房子一般都不溶解）；测尺寸、控制器、数量、`symmetric()`、地基中心的 `world()`。要由中枢自建的模块：占地放得进模块位（`CampusPlanTest` 那样测不碰广场、环道、大门和彼此），只停内环槽的旧式模块：宽深 ≤21（内环槽间距）。
2. 需要新部件：构架的照 `BlockFrame`（常量/`NAMES`/`LIGHT`/`TYPES`、`box()`、`full()`、glow，贴图在 `tools/NexusTextures.java`）；营造的地板/配件照 campus.md §3（新部件码、`PartRecipes` 条目、`tools/CampusTextures.java`）；lang `tile.fluxecho.<block>.<n>.name`、`.tip`。
3. `TileX extends TileModule implements ITileWithModularUI`：`moduleKey/moduleColor/centreCell/blueprint/definition/buildDefinition/coreBlock/flags/descriptionKey`（需要的话 `consoleAt`、`shapes`）；结构定义里营造部件用 `PartBlocks.block/meta` 映射（照 `TileLibrary.parts()`）；里面的实体交互照 `Units`/`Shelves`：`BlockFrame.onUse(...)`/`BlockFitting.onUse(...)`，两边都用 `NexusRegistry.loaded(w)` 找结构、条件一致地返回 true（客户端返回 false 会去放手里的方块）；`serverTick()` **必须先 `super.serverTick()`**（租约过期靠它）；耗电照 `TileLibrary`（`GTWirelessBackend.INSTANCE.add(team,-eu)` + `ServerEvents.addTeamTick`）；每 100 tick `Directory.report`；sync/NBT 调 super；`createWindow`。
4. `BlockX extends BlockNexus`（`super(name, textureDir)`、`createNewTileEntity`、需要时 `dropsMore`）。
5. `XModule`：preInit `registerBlock(core, ItemBlockNexus.class, "<name>")` + `registerTileEntity(TileX.class, "fluxecho:<name>")`；init `IMultiblockInfoContainer.registerTileClass(TileX.class, NexusModule.infoContainer("<name>.structure"))` + `NexusModule.previewModule(core, TileX::new)`（中枢 NEI 预览 6–10 级里立在模块位上）+ 中枢自建的模块 `ModuleSpecs.register(...)`（campus.md §5）；postInit `Manifests.register(...)`（在 `NexusModule.postInit` 之后）；在 `FluxEcho.java` 三个阶段接上。
6. Waila：`NexusWaila` 的 `getNBTData`/`getWailaBody` 加 `instanceof` 分支。
7. GUI：`XGui`（ModularWindow + FakeSyncWidget）+ `XScreen`（`FluxDraw`）。
8. 渲染：`XRender.draw(tile,t,fade)`（`Shapes`/`Motes`、`bounds()`、`sweepLevel()`、`formedAt`、`client*` 字段），在 `ModuleRender.draw` 分派。
9. Lang 两份：`tile.fluxecho.<name>.name`、`fluxecho.<name>.tip`、`.structure`、`fluxecho.module.<key>`、GUI/Waila 键；自建模块的阶段名 `fluxecho.build.stage.m<n>`。
10. 研究节点（第 5 节规则）+ `Research` 常量 + lang `fluxecho.research.node.<id>` 和 `.desc`。
11. 任务：`build_quests.py` 的 `echo_nexus()`；README、CHANGELOG。

## 4. `TileNexus`

- 库存：6 入（0–5）+ 2 出（6–7），`ISidedInventory` 所有面开放；`PHASE=1`；活动位 `POWERED=1, RESEARCHING=2, MANIFESTING=4`。
- 结构：B→BASE、G→BASE_LIT、P→PILLAR、C→CONDUIT、R→RING、S→SEAT、K→CONSOLE；会溶解的字符 `HIDE|PASS`。`definition()` 只查 MAIN=相位 I；`buildDefinition()` 有 MAIN 和 `phase1`..`phase5`。中枢只有一种形状，从不存 `Shape`。`centre()` = `cellPos(ctrlA, height-1, ctrlC+centreBack)`：控制器后方 5 格、下方 2 格的底座中心（园区坐标的原点，campus.md §2）。`consoleAt` = 控制器正下方。
- **`buildShape` 的等级**：`tier=clamp(stackSize,1,10)`，`phase=(tier-1)%5+1`；真实世界里 `phase=min(phase,PHASE)`（投影仪只搭现在能用的）；预览世界里 tier>5 再 `previewModules`：在模块位 `CampusPlan.HALL_SITES`（4、2、6）上按顺序立 `min(RingSlots.open(phase), 3)` 个 `NexusModule.previews()` 里的模块（轮流），有 `ModuleSpec` 的放在 `spec.controller(site, …)`（书库 = `BuildPlan.archiveController`，朝向中枢），没有的退回同号内环槽（0.9.2 做法）；`setBlock(core)` + `setTileEntity` + 设 `facing` + `construct(stackSize=1)`。预览里不铺园区地面（太重）。超过 10 当 10，NEI 才找得到滑块的尽头。
- **每 tick**：**先跑园区**：`if (nexusEnabled && campusEnabled) campus.tick()`，成型与否都跑（establish 任务要围着核心把中枢立起来，campus.md §5）。然后原样：未成型/禁用 → 状态 `unformed/disabled`，不供电，每 100 tick `undockAll`。否则 `eu=upkeep()`，`powered = team!=null && (eu<=0 || 无线网扣 eu 成功)`；没电 `no_owner/no_power`；有电 `ready` → `runResearch()`、`runManifest()`。每 100 tick `refreshDock` + Directory（研究/显化 WORKING、ready IDLE、其余 PROBLEM；有营造任务时状态换成 `building`/`build_paused`，研究和显化优先）。
- **公式**：`computeRate = 成型且有电 ? nexusCompute × (compute_1 ? 1.5 : 1) : 0`（20 或 30 FC/s）；`upkeep = ceil(nexusUpkeep × (upkeep_1 ? 0.75 : 1)) + (显化中 ? manifestEut : 0)`（512/384，显化 +480）；研究进度每 tick `+computeRate/20`，总量 `ResearchTree.computeFor(n, researchScale)`。营造的 EU 另算（campus.md §6）。
- **开始研究** `startResearch(p,id)`（返回 `fluxecho.research.` 下的 lang 键）：成型 → `member(p)` → 一次只跑一项 → 节点存在 → `ResearchTree.check(n, unlockedNow(), PHASE, Records.count(玩家团队))` → `Costs.takeFrom(p, costs)`（从**玩家背包**扣，全有或全无，创造免费）。完成时 `Research.grant(发起者团队)`，其他绑定团队靠并集共享。**取消不退材料**。首次成型 `grant(ANCHOR)`。
- **显化** `startManifest`：按注册顺序，跳过研究不在并集里的；`Costs.plan(inputs, 6 个输入槽)`；record 配方要 `Records.next(主人团队, now, recordCooldown)`（挑休息最久的一条，没压过的最先），没有就 `records_resting` 看下一个；产物放不下 → `output_full` 并直接返回；确定后先 `ResearchData.pressed(team, "cat:key", now)`、扣输入、`pending=out`。交付放不下就等。**中枢核心被拆时 `pending` 和进度跟着核心物品走**（`Pending`/`Manifest`，再放下时恢复到没在显化的中枢，0.10.0 起）。
- **绑定**：`teams()` = 主人团队 ∪ 已绑定团队；`member(p)`；`unlocked()` = `Research.union(teams())` 缓存（按调用次数刷新，绑定/研究变化时强制刷新），`has(id)` 查它（营造按它决定提供哪些模块）。`setOpenBinding(false)` 清空所有别的团队（终端 NBT 仍在）。只有主人团队能切换开放绑定。
- **园区**：`campus()`（服务端 `Campus`）、`syncCampus()`（园区状态变了：存档 + 重发描述包）、客户端 `clientCampus`（`Campus.View`，从描述包的 `Cp` 读）。细节见 campus.md。
- **客户端同步** `writeSync`：`A` 活动位、`R` 研究 id、`RD` 进度、`M` 正在做的物品、`D` 停靠位掩码、`DC` 模块颜色[8]、`Op` 开放槽数、`H` 全息、`C` 算力×10、`U` 维持电、`Cp` 园区（`Campus.writeSync`，只在园区状态变化时变）。`send()` 只在 `A|mask<<3|open<<11` 变化时 sync，研究/显化中每 40 tick 强制重发；建造进度不走描述包（走 `BUILD_STATE`），免得中枢区块每秒重建网格。`readSync` 读 `Cp` 后调 `BuildClient.viewChanged`。
- **NBT**：`Inv`、`Bound`、`OpenBinding`、`Research`、`ResearchDone`、`ResearchTeam`、`Manifest`、`ManifestTicks`、`ManifestMax`、`Pending`、`Holo`（**全息默认关**）、`Campus`（园区，没有这个标签 = 0.9.2 的 legacy 中枢，campus.md §9）。
- **GUI** `NexusGui`：`W=H=248`、`INV_Y=166`；右上两个标签 [中枢] (164,4)、[建造] (204,4)，各 38×15，页只在客户端（换页时中枢的 8 个槽和投入槽 `setEnabled`，其余控件按页动态启用）；中枢未成型且园区有任务时开在「建造」页。**中枢页**：header (6,5) 156×14；输入 3×2 从 (10,36)，输出 (114,…) 竖排 2 个；making (68,34,42×40，NEI 跳转)、table (6,78,130×80)、state (140,22,102×98)、星图按钮 (140,124,58×18)、全息 (200,124,42×18)、开放绑定 (140,144,102×16)。`cooldownMinutes()`=max(1, recordCooldown/1200)。**建造页**见 campus.md §10。
- **Waila**：中枢 `feNexus/feFormed/feUpkeep/feCompute/feResearch/feDone/feDocked/feOpen/feOwner` + 营造 `feBuildK/feBuildPk/feBuildSt/feBuildPs/feBuildSg/feBuildPct/feBuildMi/feBuildMn`（`营造：<任务> <百分比>% · <阶段>`、`缺 <物品> ×n`）；书库 `feDock/fePowered/feSamples/feCap/feLends/feOwner`（`feCap` 跟形状：702 或 167）。

## 5. 研究

`ResearchTree`：星图 360×220，`PHASE_NOW=1`。分支：FRAME 构架 `0x4FE3FF`、ECHO 回响 `0x8A5CFF`、COMPUTE 算力 `0xFF8CE6`、GUARD 守护 `0x7DB8FF`、ENERGY 能量 `0xFFD27A`、DEPTHS 深层 `0x5FD3A8`。

| id（lang 名） | 分支 | 前置 | 算力 | 材料 | 记录 | 相位 | 效果 |
|---|---|---|---|---|---|---|---|
| `anchor` 锚定 | FRAME | — | 0 | — | 0 | 1 | 根；中枢首次成型自动授予 |
| `inner_ring` 内环 | FRAME | anchor | 3000 | 结晶 ×16、不锈钢板 ×8 | 0 | 1 | 开放 2 个内环槽（active 园区里就是 2 个模块位） |
| `phase_2` 相位 II | FRAME | inner_ring | 0 | — | 0 | 2 | 占位 |
| `echo_crystal` 回响结晶 | ECHO | anchor | 6000 | 结晶 ×8、末影珍珠 ×4 | **5** | 1 | 显化回响晶 |
| `library` 回响书库 | ECHO | echo_crystal、inner_ring | 12000 | 回响晶 ×4、书 ×16 | 0 | 1 | 显化书库核心和索引卡；active 园区在空模块位上投影出回响档案馆 |
| `library_reach` 跨维度借阅 | ECHO | library | 24000 | 回响晶 ×8、末影之眼 ×4 | 0 | 1 | 书库跨维度借出（看中枢并集） |
| `compute_1` 算力校准 | COMPUTE | anchor | 4000 | HV 电路 ×4、结晶 ×4 | 0 | 1 | 算力 ×1.5 |
| `upkeep_1` 节流 | ENERGY | anchor | 4000 | 结晶 ×4、不锈钢板 ×8 | 0 | 1 | 维持 ×0.75 |
| `tidal_well` 潮汐井 | ENERGY | upkeep_1 | 0 | — | 0 | 2 | 占位 |
| `grit_yield` 碎屑富集 | DEPTHS | anchor | 8000 | 碎屑 ×32、结晶 ×4 | 0 | 1 | 碎屑几率 ×1.5（看团队自己的研究） |
| `dome` 回响穹顶 | GUARD | anchor | 0 | — | 0 | 2 | 占位 |

- `Cost` 写法：`ore:<名>*n` 或 `item:<mod>:<name>[@meta]*n`；写错在类加载时抛异常（类初始化失败）。营造的 `PartRecipes` 用同样的 spec 写法（不带数量）。
- `check` 顺序：DONE → LATER_PHASE → REQUIRES → RECORDS → NONE。
- **加节点规则**（`ResearchTreeTest`）：坐标离边 ≥16、节点间距 ≥24；前置先列；唯一的根、无环；phase ≤ `PHASE_NOW` 的节点必须 compute>0 且有材料——**把 `PHASE_NOW` 改成 2 时，phase_2、tidal_well、dome 必须补上 compute 和材料**。
- `ResearchData`：主世界 mapStorage `fluxecho_research`（`data/fluxecho_research.dat`）：`Teams:[{Team, Done:[id], Pressed:{"cat:key": worldTime}}]`。主世界没加载时 `get()` 返回不缓存的临时对象。serverStopped `reset()`。
- 同步：`EchoNet.send` 把团队研究作为 `ClientLedger.RESEARCH="@research"` 塞进账本快照（登录时、变化后）；客户端 `ClientLedger.research()`（NEI 的锁按**玩家自己团队**的研究判断）；`records()` 排除这个键。
- `StarMapScreen`：每 20 次 `updateScreen` 发 `ASK_MAP`；只接受坐标一致的 MAP；`scale=min((w-48)/360,(h-70)/220)`；LATER_PHASE 只画一圈；取消要点两次；toast 3.5 s。push/pop `GL_ENABLE_BIT` 并关 CULL_FACE。

## 6. 回响书库：大厅和档案馆

书库有两种形状（第 3 节多形状）：**`main` = 回响档案馆**（0.10.0，营造在模块位上建的两层长厅，78 个书架 × 9 = 702 本，书架整组用）和 **`hall` = 0.9.2 大厅**（167 个墙上书格，一格一本）。新核心是 `main`；0.10.0 之前的存档（没有 `Shape`）读成 `hall`，照旧成型、照旧工作。**档案馆的一切（形状、书架单元、交互、GUI、书的同步、仓库、渲染）见 [campus.md](campus.md) §12**，这里只写两种形状共用的和大厅自己的。

- **共用**：`COLOR=0x8A5CFF`；`flags` 全 0（不溶解，是能走进去的房子）；`coreBlock()=LibraryModule.core`；`centreCell` 跟形状（大厅 `cellOf(6,0,6)={6,8,6}`）。样本 `ItemStackHandler` 702 格（`SLOTS`，按较大的形状开，换形状不丢书），每格 1，`capacity()` = 702 / 167（`HALL_BOOKS`），**槽 i 就是这个形状的第 i 个书位**；有效 = 槽 < 容量、不是写好的卡、别的格里没有相同的（item/damage/NBT，`holdsElsewhere`）、tile 没失效。任何物品都能放，回响机器样本槽用的东西才有意义。读 NBT 时先读进按存档大小的临时 handler 再逐格拷（`setSize` 会清空；0.9.0 的 54 格存档落在前 54 个书格，0.9.2 的 167 格落在前 167 槽）。NBT `Samples/Desk/Selected/Unit/Refilled`。
- **形状变化与队伍书库仓库**（`LibraryVault`，`fluxecho_vault`）：大厅成型时 ≥167 槽的书收进仓库；两种形状第一次成型时都从仓库取一次书（`refillOnce`，拆了核心再放回去的大厅也把书取回来）；拆核心时书进仓库（没有队伍才掉落）；两种窗口都有「取回」。细节 campus.md §12。
- 写卡台 2 格：槽 0 只收空白卡，槽 1 输出；每 10 tick `writeCard()`（有空白卡、输出空、有选中样本）。`select(±1)` 是 GUI 箭头，`selectSample(slot)` 直接指定（档案馆目录页用）。
- 供电：成型、`nexus()!=null`、`nexusEnabled`、有团队 → 每 tick 扣 `libraryUpkeep`（128）EU（停靠就扣，不看是否在借出）。两种形状一样。
- **`Lending.resolve(card, world, machineTeam)`**（服务端）：卡已写、卡的 Team 等于机器团队；样本从 `WeakHashMap` 缓存取；遍历 `NexusRegistry.allModules()` 找 `lib.lends(team, dim) && lib.holds(sample)` → `lent()` 并返回 `sample.copy()`。`lends` = 成型 + 有电 + 同队 + 停靠 + （同维度 或 中枢有 `library_reach`）。调用方 `MTEEchoMachine.sample()`；借不到返回 null，机器停下等。
- 每 100 tick 上报 Directory（`fluxecho.dock.<状态>`），统计过去 100 tick 借出次数；描述包只在开始/停止借出时重发（借出次数由 GUI 自己同步）。
- **书的同步**（两种形状共用）：描述包 `P/S/L/Dk/Rv/Ep` + 书（`BookPacket`：`B` 整堆带 `At`、`Bi` 不带 NBT 的 `槽, id<<16|meta`；按压缩后 28000 字节的预算决定谁带 NBT，取代了 0.9.2 按文本长度的 `BOOK_NBT`）；书变了不重发描述包，走 `NexusNet.LIB_DELTA`（第 7 节）。客户端 `clientBooks[]`（702）、`book(i)`。
- **大厅**（`hall`）：书格位置 `shelfPos(i)`、`shelfFace(i)`（朝大厅那面的 ForgeDirection）、`shelfAt(x,y,z)`（位置 → 槽，按坐标和朝向缓存），这三个按形状分支，档案馆也有对应的结果；`consoleAt` = 阅读台 `deskPos()`。
  - **`Shelves.use`**（`BlockFrame.onUse`，meta SHELF，只管成型的**大厅**）：找到拥有这块书格的大厅、点的是朝大厅那面 → 两边都返回 true；服务端 `mayUse` 后：空手取书（放到手上）、拿空白卡写卡（消耗一张，创造不耗）、拿写好的卡提示、格里有书提示、重复提示、否则放一本（消耗一个）。声音 `dig.cloth`。聊天提示键 `fluxecho.library.shelf.*`。
  - GUI `LibraryGui`（`window()` 按形状分派，档案馆走 `ArchiveGui`；两种窗口都有校验器，核心没了就关窗）：大厅窗口 `W=184`，**一页一层书架**（`PAGES=5`，每页 4×9，书格在页里按 `SHELVES` 顺序排），页码只在客户端，翻页时客户端对每个 `SlotWidget.setEnabled(...)`（它自己同步到服务端）；书格槽是 `BookSlot`（热键塞整堆只留一件）；页标 `LibraryScreen.page`；写卡台在 `DESK_Y`，旁边是「取回」（0.10.0，队伍仓库有书时可点）；状态栏右边显示仓库里的本数；总高 252。
- **档案馆**（`main`）：`Units.use` 整个书架交互、`ArchiveGui` 两页（书架/目录）、书架网格里画书（`LibraryBooks`）、`ArchiveRender` 室内特效——campus.md §12。

## 7. 网络

- **NexusNet**（`fluxecho_nx`，判别号 0 ToServer、1 ToClient，1 字节 kind + NBT，入队后在 tick START 主线程处理）：C→S `ASK_MAP=0`、`START=1`（`Id`）、`CANCEL=2`，都带 `Dim/X/Y/Z`；S→C `MAP=0`、`RESULT=1`（`Key`），以及 0.10.0 的 `BUILD_FX=2`、`BUILD_STATE=3`（营造，`CampusNet`，campus.md §10）、`LIB_DELTA=4`（书库书位的变化，`TileLibrary.sendBooks`/`receiveDelta`，campus.md §12）。后三种在客户端泵里直接交给 `CampusNet.receive`/`TileLibrary.receiveDelta`，不经星图的 sink；发送方都用 `Sight.near`（书库再加正在看控制器区块的人）。ASK_MAP 不检查成员和距离；START/CANCEL 后无论成败再回一次 MAP。MAP 字段：`Found/Records/Own` + 找到中枢时 `Member/Formed/Powered/Phase/Compute/Research/Done/Owner/Bound/Scale/Open/Cost{id→long}`。
- **终端**：`TerminalScreen` 在 machines/research/nexus 页每 10 tick 发 `TERM_REQUEST{hand:true, termPage}` → 服务端要 `carries()` → `TerminalView.build(player, page)` → `TERM_DATA`。
  - MACHINES：`m[{n,s,l,d,x,y,z,a}]`（最多 200 行，`a`=秒）、`total/working/problems`。数据来自 `core/Directory`（内存，1 小时没上报就忘）。
  - RESEARCH/NEXUS：`done`、`nodes`；绑定后 `bound[4]`、`loaded`；中枢已加载时 `member/owner/formed/powered/status/upkeep/compute/research/fraction/open/boundTeams/balance/unlocked/avail[{id}]/modules[{k,slot,c,dock}]/build{ac,k,pk,st,ps,sg,done,total}`（`build` 是 0.10.0 加的营造进度，没任务时只有 `ac`；`TerminalScreen` 的中枢页在模块列表上方画成一行：任务名、状态或暂停原因、`done / total`，campus.md §10）。
  - `NexusBinding.bound(p)` 先看手持终端，再看主背包第一个已绑定的。

## 8. 渲染

- **NexusRender**（`EVENT_BUS` + `FarDraw.add`；光门里每扇门的画面都会再调一次，所以任何逐帧状态（扫光等）都要按时间算，不能按调用次数）：`!nexusEffects` 或 shadow pass 跳过；**每个多方块单独 try/catch**：出错时关掉没结束的 motes 批次和 quad 批次、记一次日志、接着画下一个（0.10.0；异常逃出去会让光门里所有部件都不再画）。距离 > `range+48` 时只画冲天光柱（`farBeam`，有电、< `BEAM_RANGE=512`，最后 64 格淡出），否则 16 格淡出；每个 tile 的 finally 保证 `Shapes.end()`。`base` = 底座顶面。四条**弧形肋架**：二次曲线 `RIB_FOOT(4.5,0)→RIB_BEND(4.7,3.0)→RIB_HEAD(3.0,4.4)`（离中心距离, 高度），沿斜对角，7 节 `prism`（方截面、外亮内暗、往上变细），关节和内侧的青色光缝按 `sin(t*0.035)` 呼吸；环 base+4.5（半径 2.6–3.4，有电时转）；晶核在环心（显化时紫色），下面 base+3.1 一圈托光，base→+3.1 一道细光柱；有电时 `skyBeam`：晶核到 +64、再到 `max(320, yCoord+160)` 的光柱，外层 40 格光晕，5 个往上爬的光点；地面圆盘和环、反转虚线环、研究星座（base+8.5）、扫光盘；全息（默认关）在控制器上方 1.4，matrix 和 GL 状态在 finally 里还原。
  - **光桥**（`bridges`）：内环槽上的模块照旧画环桥（模块色，走光点）；active 园区场地上的模块画**场地光桥**：沿场地轴从半径 5.6 到模块朝中枢那一面（从客户端的 `bounds()` 算；模块没加载时到 23.5），门口一道横光（`siteDoorSafe` 出错记一次、本次运行退回环桥）。手持核心物品或终端时画空槽轮廓——**active 园区不画**（由营造的总图 `Masterplan` 代替，campus.md §11）。
  - 营造的投影、发射特效、总图是另外三个登记了 `FarDraw` 的处理器（`BuildRender`、`BuildFx`、`Masterplan`），见 campus.md §11。
- **LibraryRender**：按形状分派——档案馆（成型与否）交给 `ArchiveRender`（campus.md §12），大厅照 0.9.2：外面：屋顶上方 2.2 浮着翻页的图鉴（往上飘的光点）、地基边一圈停靠状态色的框、成型扫光。**相机在大厅里（距中心 <5.5、地板到天花板之间）才画里面**：天井往上一条书井（深 1.98 到屋顶下沿、6 段、半宽 2.5 收到 0.35，`pow(f,0.8)`，墙写深度，光线加法）；阅读台上 0.55 倍的图鉴（`codex()`，档案馆的阅读台也用）；每本书在书格朝大厅那面画一圈光（每 5 本一本金色）+ 深色封面 + 样本图标（先方块图集再物品图集，多 pass 带颜色，取不到图标的只留封面）；看着的那格（`objectMouseOver` + 面对）上方浮出书名或"空书格"。0.9.0 的"往方块里画书廊"去掉了（从角上能看进去）。没停靠/没电时亮度 0.35。`icons()`/`label()` 和各批次在 finally 里出栈、关批次（0.10.0）。
- **FrameRender**（ISBRH）：HIDE 返回 false；书格在书架单元里（`UnitLook.niche`）时交给 `ShelfUnits` 画成书架的一格（campus.md §12）；其余先画碰撞盒，再关 AO 用 `0xF000F0` 画发光叠层；物品栏里带法线画 3D。
- **`Shapes` API**（坐标相对相机）：`begin(additive)`、`additive(bool)`、`end()`（幂等）；`ring`、`band`、`disc`、`beam`（十字面）、`string`、`crystal`（八面体）、`strut`、`square`、`plane`、`quad`、`quad3`、`quadShade`、`mix`。**只用 `GL_QUADS`**（"triangles as quads with a doubled corner: Angelica's tessellator takes quads everywhere"），法线指向视线，不用纹理（`Motes` 除外）。`Motes.begin` 在打开批次前失败也会出栈，`Motes.end` 出错照样出栈（0.10.0）。
- 通用光影规则见 `CLAUDE.md` 和 [platform.md](platform.md)。

## 9. 配置 `nexus`

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 关掉：不注册构架、核心、地板、配件、补给口的配方，放下的停机，营造也不跑；通量物质不受影响 |
| `gritChance` / `crystalGrit` | 0.15 / 3 | |
| `upkeepEuPerTick` | 512 | |
| `computePerSecond` / `researchCostScale` | 20 / 1.0 | |
| `manifestEuPerTick` | 480 | |
| `recordCooldownTicks` | 24000 | 同一条记录再压的间隔（20 分钟） |
| `innerRingRadius` | 32 | 改了会挪动已建中枢的槽位（园区的模块位不受影响） |
| `libraryUpkeepEuPerTick` | 128 | `librarySamples` 0.9.2 删了（`Config.upgrade` 移除） |
| `effects` / `effectRange` | true / 128 | 客户端；`effectRange` 也决定营造包和书增量包的接收范围（+64） |
| `campusEnabled` / `archiveEnabled` | true / true | 营造开关、回响档案馆开关（0.10.0） |
| `build*`（16 个） | — | 营造的速度、EU、成本倍率、平整、间距、投影和特效：表在 campus.md §14 |

`VERSION="0.10.0"`；0.10.0 只加键，`upgrade()` 没有新步骤。

## 10. 已知问题、坑、测试

- 坑：NEI 预览要 `coreBlock()` 和不包装的 `buildDefinition()`，缺一个 NEI 里就没有或没用料；Blueprint 必须对称；客户端 `Formed` 只能整体替换；NEI 预览世界由 `realWorld()` 排除；迟到的客户端不重播扫光；新模块 `serverTick` 必须调 super；停靠租约 220 tick、复查 100 tick、优先级（记录场地 > 收编场地 > 内环槽）再低号槽优先；多形状的字符必须在所有形状里同义（`TileLibrary.parts()` 第一次建结构定义时检查）；`covers()` 用最大形状的立方体。
- 谁的研究算数：`grit_yield` 看团队自己的；`library_reach` 看中枢并集；营造提供模块看中枢并集（`has`）；`Records` 冷却看主人团队；NEI 显化页的锁看玩家自己团队。
- 已知问题：取消研究不退材料（设计如此）；ASK_MAP 不查成员（只读信息）；`nexus.scale`（BLUEPRINT 3.3）还没实现。0.9.x 的"中枢被破坏时 `pending` 丢失"在 0.10.0 修好（核心物品带走，campus.md §8）。营造和档案馆的已知问题见 campus.md §16。
- 测试：`NexusShapeTest`（尺寸、控制器在控制台座上、肋架往里收、核心柱、每相位加一节且包含下一相位、各字符数量、对称、溶解范围、world()）、`LibraryShapeTest`（13×13×9、控制器、数量、门、大厅空着只有阅读台、书格朝大厅且不重复、天井、地基中心的 world()）、`RingSlotsTest`（槽位、容差、间距、各相位开放数、模块朝向中枢）、`ResearchTreeTest`（根唯一、无环、坐标间距、当前相位可达且有花费、check、Cost 解析）、`CostPlanTest`（按槽序跨槽扣、不够返回 null、不重复计数）、`BlueprintTest`（`cell` 是 `world` 的逆、`point`）；营造和档案馆的测试列在 campus.md §15。

## 11. 下一步：0.11.0「守护」（BLUEPRINT 3.14）

原定 0.10.0 的内容顺延到这里：相位 II；**回响穹顶**（刷怪、爆炸、弹射物）；**潮汐井**和回响潮汐；相位合金、共鸣玻璃；覆层系统（晶簇、相位板）；相位核。要点：
- 相位 II：中枢再长一节（`NexusShape.phase(2)` 已经有了，NEI 里能看），内环开到 4 槽（active 园区：模块位最多 `min(4, 7)`，但大厅位只有 4/2/6 三个——第四个模块要先定对角位能放什么）。已预留 `TileNexus.PHASE`、`ResearchTree.PHASE_NOW`、`RingSlots.open(2)=4`、lang `nexus.phase.2`；做的时候 `definition()` 要按相位查（或查当前相位的 shape），`blueprint()`/`centre()` 不用动（底座不随相位移动），`NexusRender` 每节再画一组肋架和环。**相位 II 的那一节应该由营造来长**：给 `BuildPlan` 加一个"长一节"的计划（只放新增的 P/C/R/S，晶核座是 `partOnly`）；放核心时的 `HEADROOM`=30 已经给相位 V（高 26 层）留够了顶，底座上方的清场柱只清到 `buildClearHeight`，新的一节要自己清；改了已有计划就提 `BuildJob.V`。
- 回响穹顶（3.8）：天顶，半径按相位 48/72/96/128/160；功能逐项研究开启、逐项耗电（不刷怪、推出敌对生物、防爆、拦截弹射物、防雷、防火、挡雨雪）；护盾值和回充；六边形受击动效。只看所在维度。园区的平整圆盘是 `oct(64,90)`，相位 I 的穹顶在园区里面。
- 潮汐井（3.9 根位）：EV–LuV 最强，烧通量结晶，产量随潮汐 ±40%。每座发电只在两三个阶段里顶尖。要能走进去干活（第 0 节），由营造在园区里建（新的 `ModuleSpec`，场地和占地先在 `CampusPlan` 里定好）。
- 回响潮汐（3.11，数值由我定）：日潮（午夜满、正午枯）× 月相（满月 ±20%、新月 ±5%），一天平均正好 100%，影响速度不影响花费；研究「稳潮」；`tides.enabled`；终端加潮汐页（顺便把营造进度显示出来）。
- 材料（3.6）：相位合金（GT 电力高炉：通量结晶粉 + 钛 + 回响晶粉）、共鸣玻璃（显化：相位合金 + 硼硅玻璃）。
- 覆层和相位核（3.4）：锚点上装覆层方块（看得见地长在结构上），相位核 I–V 装进控制器。
- 0.9.2 的画面、NEI 预览和书库大厅用户试过，正常；0.10.0 的营造和档案馆还没进游戏试；数值等玩得久一些再调（BLUEPRINT 第七节）。
