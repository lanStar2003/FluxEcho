# 开发手册 · 通量网络（`com.fluxlite`）

> 给接手的会话看的：改通量连接器、监控台、终端、无线电网结算、蒸汽网络、充电、统计、告警之前读这一篇。玩家向的说明在 [docs/FluxLite.md](../FluxLite.md)。总览见仓库根目录 `CLAUDE.md`。

## 0. 定位

- jar 里的第二个 `@Mod`：`modid="fluxlite"`，`version=Tags.VERSION`（= `com.fluxecho.Tags.VERSION`），`dependencies="required-after:gregtech;after:Waila;after:IC2;after:CoFHCore"`（`FluxLite.java`）。
- 注册名 `fluxlite:*`、配置 `config/fluxlite.cfg`、命令 `/fluxlite`、网络频道 `fluxlite`、存档 `data/fluxlite_registry.dat` 和 `data/fluxlite_steam.dat`。**这些都不能改名**（0.8.0 合并时的约定，旧存档零迁移）。
- 思路：不自建网络、不设密码，**GT 无线电网就是网络**，按 GT 团队（队长 UUID）共享；蒸汽另走模组自己的团队蒸汽网络。
- `IsolationTest` 跳过 `com/fluxlite/`（"keep their own rules (IC2 is required by GT)"）。

FluxEcho 用到的 FluxLite API：
- 扣团队电：`GTWirelessBackend.INSTANCE.add(team, BigInteger.valueOf(-eu))`（余额不足返回 false 且不扣），扣成功后 `ServerEvents.addTeamTick(team, 0, eu, eu, true)` 计入团队输出统计。`TileNexus`、`TileLibrary` 都这么做。
- `ModBlocks.controlCenter`（中枢核心配方）、`ModItems.terminal`（中枢渲染判断手持）、`ItemFluxTerminal`（`NexusBinding`）。
- 终端的 FluxEcho 页：`net/Kinds.TERM_REQUEST/TERM_DATA` → `com.fluxecho.nexus.TerminalView.build`（见 [nexus.md](nexus.md)）。
- 监控台全息发包用 `com.fluxecho.gate.Sight.near(...)`，隔着光门也能看到（见 [gates.md](gates.md)）。

## 1. 文件

| 包 | 文件 → 作用 |
|---|---|
| 根 | `FluxLite`（`@Mod` 入口）、`Tags`、`CommonProxy`（preInit：`Config.load`、`ModBlocks/ModItems.register`、`Net.init`；init：注册 `ServerEvents`（FML+Forge 总线）、`WrenchActions`、`Charger`、区块加载回调、Waila IMC；postInit：`Recipes.register`；serverStarting 命令；serverStarted `ChunkLoadManager.onServerStarted`；serverStopping 对 Registry/SteamNetwork `markDirty`；serverStopped 重置单例）、`ClientProxy`（连接器 ISBRH、监控台 TESR `HologramRenderer`、`Highlighter`；`GuiHost(new XxxScreen)` 开三个界面）、`Config` |
| `adapter/` | `EnergyAdapter`（统一接口，`Kind{GT_MACHINE,GT_CABLE,GT_SINK,IC2,RF,STEAM}`）、`Adapters`（按固定顺序选适配器）、`GTMachineAdapter`、`GTCableAdapter`、`GTSinkAdapter`（AE2/Railcraft）、`ProbeSinkAdapter`（反射探测未知 GT EU 设备）、`IC2Adapter`、`IC2CableAdapter`、`EnderIOConduitAdapter`、`RFAdapter`、`RF`（换算）、`SteamAdapter`、`Packets`（分包 `most/whole/rest`） |
| `backend/` | `WirelessBackend`（接口）、`GTWirelessBackend`（GT `WirelessNetworkManager` 封装单例）、`SteamNetwork`（团队蒸汽，`WorldSavedData`） |
| `block/` | `BlockConnector`、`BlockControlCenter`、`ItemBlockFlux`（两方块共用物品，tooltip 显示保存的设置，Shift 展开 `.desc.1..11`）、`ModBlocks`（方块、TE、创造栏 `fluxlite`）、`Harvest`（扳手或镐都算正确工具，`WRENCH_SPEED=8`，掉落带设置）、`WrenchActions`（扳手右键；`settings()/restore()` 读写 NBT 根标签 `fluxlite`） |
| `charge/` | `ChargePlan`（纯函数 `share`、`capPerRound`）、`Charger`（无线充电） |
| `chunk/` | `ChunkLoadManager`（按区块引用计数、票据复用） |
| `client/` | `ConnectorRenderer`（ISBRH：核心、臂、法兰）、`HologramRenderer`（监控台全息 TESR）、`ShaderCompat`（反射 Iris：`packInUse`、`shadowPass`） |
| `command/` | `CommandFluxLite` |
| `compat/` | `Mods`、`AECompat`、`RailcraftCompat`、`EnderIOCompat`、`GTSinks`（任意电压都安全的汇）、`GTPower`（反射读 GT 私有字段：线缆电流、发电机输出；`BURN=40`）、`Wrenches`（识别各家扳手，扣耐久）、`WailaCompat` |
| `core/` | `Port`（一个通道的状态，`COUNT=12`）、`PortMode{AUTO,OFF}`、`PortRole{NONE,INPUT,OUTPUT,BOTH}`（以网络为视角）、`PortStatus{OK,DISABLED,NO_TARGET,UNKNOWN_SPEC,CONNECTOR_NEIGHBOUR,PENDING}`、`Settlement`、`FeedGovernor`、`CableScanner`、`MachineSample`、`ServerEvents`；`alert/{Alert,AlertManager}`；`registry/{Registry,ConnectorRecord,PortInfo,TeamData}`；`stats/{Series,Bucket}`；`view/{ConnectorView,ControlCenterView,DeviceRow,HoloView}` |
| `gui/` | `GuiHost`（`GuiScreen` 外壳，实现 `ui.Host`）、`ConnectorScreen`、`ControlCenterScreen`、`TerminalScreen`（0.9.0）、`Highlighter`（世界里闪烁的定位框）；`holo/{HoloPanel,HoloState}`；`ui/{Canvas,Host,McCanvas,Theme,UiScreen}` |
| `item/` | `ItemFluxTerminal`、`ModItems` |
| `net/` | `Kinds`、`Net`、`MsgToServer`、`MsgToClient`、`ServerPackets`、`ClientPackets` |
| 其他 | `recipe/Recipes`；`tile/TileConnector`（约 1000 行，核心）、`tile/TileControlCenter`；`util/Fmt`（`si/tier/duration/percent`，`LANG="@L:"` 前缀表示客户端翻译）、`util/Longs`、`util/Names`（`summarize`） |

资源 `assets/fluxlite/`：`lang/{en_US,zh_CN}.lang`（键集合一致）；`textures/blocks/connector_{arm,plate}_{in,out,both,idle,off,error}`、`connector_core(_active)`、`control_center_{front,front_alert,side,top}`；`textures/items/terminal.png`。

## 2. 方块和物品

创造栏 `itemGroup.fluxlite` = 简易通量。三个配方都在 `Recipes`（`GTModHandler.addCraftingRecipe`，`NOT_REMOVABLE`），受 `recipes.enableDefaultRecipes` 控制。

### 2.1 通量连接器 `fluxlite:connector`（TE `fluxlite.connector`）

- 配方 ×2：`PRP/EHE/PwP`（青铜板、红石粉、末影珍珠、青铜外壳 `Hull_Bronze`、GT 扳手）。
- 放置：FakePlayer/非玩家放置会原地掉落（"automation cannot own a connector"）；团队达到 `maxConnectorsPerTeam` 提示 `fluxlite.msg.team_limit` 并掉落；否则记 owner 并从物品 NBT 恢复设置。
- 12 个通道：`ports[0..5]` 各面 EU，`ports[6..11]` 各面蒸汽（`index = side + (steam?6:0)`）。一个面的开关同时切 EU 和蒸汽。
- **方向判定** `TileConnector.resolve`：邻居区块未加载 → `NO_TARGET`；邻居是连接器 → `CONNECTOR_NEIGHBOUR`；`Adapters.create` 选适配器；面关着 → `DISABLED`；`refresh()` 后 `chooseRole`（玩家固定的方向优先，否则 `autoRole()`）；要供电却读不出规格 → BOTH 降为 INPUT，否则 `UNKNOWN_SPEC`（红色，**不供电**）；未知设备 → `PENDING`（黄色"待适配"，用 `pendingVoltage` 小包送）。
- 重判时机：`needsResolve`（邻居变化、`BlockEvent.Place/Break` 碰到扫描过的线缆/设备、RF 设备往非收集面推电），或每 20 tick（错峰 `floorMod(x*31+z*17+y,20)`）。
- 固定方向：`cycleDirection` 自动 → 固定输入 → 固定输出；只有设备能收能发时才能固定。
- 外观码 `VIS_NONE=0, IN=1, OUT=2, BOTH=3, IDLE=4, OFF=5, ERROR=6`；颜色：输入绿、输出橙、双向蓝、待机灰、无法识别红、关闭深灰；核心最近 1 秒有活动用 `core_active`。
- **电压/过载保护**：
  - 直连 GT 机器：只在 `0<getInputVoltage()<Integer.MAX_VALUE` 且安培>0 时供电（非电机器报 MAX_VALUE），注入电压 = 额定输入电压；每 tick 补到正好满（整安培包 + 一个小电压零头包，"GT accepts any voltage up to the rated one"）。收电：发电机自己推整包，另外每 tick 抽走高于 `getMinimumStoredEU()` 的部分，合计不超额定输出。
  - GT 线缆：电压 = `min(最弱线缆, 消费者最低输入)`；线上有说不出规格的设备（如 AE2 P2P）整面不供电；安培 = `min(最弱线缆, 消费者安培和)`；**烧线保护** 读最热 PowerNodePath，`HOT=20` 停供、`COOL=10` 以下恢复（GT 在 40 起火）。
  - 双向线缆：线上有机器且有发电机/电池 → BOTH；只收余电（`othersTake` 为真时拒收）、只补缺口（有发电机时只给缓存低于一半的机器算需求）、`FeedGovernor` 限份额；同一通道一次只走一个方向（相隔 2 tick）；**防绕圈**：注入期间 `Settlement.supplying=true`，所有连接器 `injectEnergyUnits` 返回 0；拓扑签名变了就重建 GT 线缆图（"GT only rebuilds its cable graph for its own machines"）。
  - IC2：任一面是 IC2 就注册进 IC2 能量网，收发签名变了先注销再注册（"IC2 caches the sides"）；`drawEnergy` 收到负数表示退电（`outDebt`）。IC2 和 EnderIO 的混合网（同时有源和汇）**只供电不收电**。
  - RF：只有 Kind=RF 的面响应 CoFH 方法；见 3.5。
  - 蒸汽 `IFluidHandler`：`fill` 只收蒸汽、只在蒸汽输入面，直接 `SteamNetwork.give`；`drain` 恒为 null；`getTankInfo` 给每个开着的面一个空罐让管道能连上。
- 交互：右键开 `ConnectorScreen`；扳手右键开关被点的面；潜行扳手右键拆下（同队或 OP，扣扳手耐久）。被破坏：`release()` 退还缓冲 → 释放区块 → `Registry.remove` → 刷新线缆图。
- 拆下保留设置（NBT `fluxlite`：`name`、`off` 位掩码、`dir` 每通道 2 位）；全默认时不写，物品仍可堆叠。
- 几何（1/16 格）：核心 5–11、臂 6–10、法兰 4–12 厚 1；碰撞箱跟随臂。

### 2.2 通量监控台 `fluxlite:control_center`（原"通量控制中心"，只改了显示名；TE `fluxlite.control_center`）

- 配方 `GGG/RHR/PdP`（玻璃板、红石粉、青铜外壳、青铜板、螺丝刀）。
- 朝向 meta 2..5，放置时 `{2,5,3,4}[玩家朝向]`；扳手旋转。
- 告警：每 20 tick 看团队告警是否非空 → 正面换 `front_alert`、可选弱红石输出 15。
- 全息：每 10 tick 向 `Sight.near(world, x+.5, y+1.5, z+.5, holoRange+4)` 的玩家推 `HOLO_DATA`；4 档尺寸（范围 ×{1,1.5,2,2.5}、`HOLO_UNIT`、`HOLO_LIFT`）；只在半透明 pass 画。
- 拆下保留 `rs/noHolo/holoSize/holoOpaque`。

### 2.3 通量终端 `fluxlite:terminal`（堆叠 1）

- 配方 `PGP/RER/PdP`。
- 右键开 `TerminalScreen`；潜行右键空气切换充电（NBT `fluxliteNoCharge`）；`onItemUseFirst` → `NexusBinding.use`（潜行右键中枢核心绑定，NBT `feNexus{Dim,X,Y,Z}`，同一座再来一次解绑；别的团队的中枢要它开着「开放绑定」）。tooltip 显示绑定的中枢（`fluxlite.terminal.bound`）和充电状态。
- `carries()`：手上或主栏里有终端；所有 `hand:true` 请求都要求它。看网络**不需要绑定**，任何距离、维度都行。
- `TerminalScreen`（0.9.0）：继承 `ControlCenterScreen`，左栏 `RAIL=76`，5 页 `{"network","machines","codex","research","nexus"}` 对应 `TerminalView.NETWORK=0…NEXUS=4`：
  - network：`page()` 返回 false，画父类仪表盘；
  - codex：纯客户端，`Categories.all()` + `ClientLedger.keys(cat)`，按钮调 `FluxEcho.proxy.openCodex`；
  - machines/research/nexus：每 10 tick 发 `TERM_REQUEST{hand:true, termPage}`，只接受 `termPage` 等于当前页的 `TERM_DATA`。字段见 [nexus.md](nexus.md) 的 TerminalView。machines 页 `a>15` 秒显示为未加载，可筛选"只看问题"，点条目在世界里高亮。research 页按钮打开 `StarMapScreen`。

## 3. 能量模型

### 3.1 GT 无线电网 `GTWirelessBackend`
- 团队 = `SpaceProjectManager.getLeader(uuid)`（null 用本人）；所有团队数据以队长 UUID 为键。
- `getBalance` = `WirelessNetworkManager.getUserEU`；`add(uuid, BigInteger)` 结果为负时返回 false 且不改动；`ensureUser` = `strongCheckOrAddUser`。
- 任何 Throwable → `available=false`（只记一次 error），60 000 ms 后自动重试。不可用时结算暂停、充电跳过、告警 `BACKEND_DOWN`。

### 3.2 端口缓冲 `core/Port`
- `supply`（从网络取来待送设备）、`collected`（从设备收来待交网络），单位 EU。periods = `settlementPeriod × bufferPeriods`（默认 2）。`supplyCap = max(V×A×periods, V)`，`collectCap = max(V×A×periods, 2048)`，被动推送超预期会扩容。蒸汽通道无缓冲。

### 3.3 结算 `core/Settlement`（每 `settlementPeriod` tick，tick END）
- 按团队批处理：`collected` 全收；正在供电的面请求 `supplyCap − supply`；不再供电的面把剩余 `supply` 退回。
- `offered = returned + applyLoss(collected)`；`available = balance + offered`；够就全满足，不够按比例 `amount × available / wanted`；`delta = offered − granted` 调 `backend.add`，成功后才给端口加 `supply`；失败且 `offered>0` 只把 offered 加回（"never lose what generators handed in"）。
- 损耗 `wirelessLossPercent` **只作用于发电输入**。`refundBuffers()` 在 `release()` 时把缓冲退回网络。

### 3.4 团队蒸汽网络 `SteamNetwork`
- `Map<队长 UUID, BigInteger 升>`，无上限，`data/fluxlite_steam.dat`（键 `b[{m,l,v}]`，0 不写）。`give`/`take`。
- `SteamAdapter.classify`（每个邻居只分类一次，"so a filling tank can't flip it"）：GT 锅炉/输出仓 → PRODUCER；`isSteampowered()` 的 GT 机器 → CONSUMER；其他靠模拟填充判断；什么都收的储罐排除。GT 锅炉主流体是水，所以先试不带类型的 drain。

### 3.5 RF/EU `adapter/RF`
- 供电 `outPer100 = max(1, GregTechAPI.mEUtoRF)`（GTNH：100 EU → 360 RF）；收电 `inPer100 = max(outPer100, ceil(10000/mRFtoEU))`（GTNH 下也是 360），**绕一圈不增值**。`Carry` 累积零头。RF 面每 tick 上限 `rfNominalVoltage`（8192）EU。
- `RFAdapter.decide` 决策表：最近推过电或不是 receiver → INPUT；不是 provider 或当前能收 → OUTPUT；能被抽且从没收过 → INPUT；否则保持上次。`PUSH_MEMORY=200`。

### 3.6 `FeedGovernor`（双向线缆的安培份额）
- `HOLD=5, SETTLE=100, STEP=5`，初始 share=1。发电机连续 2 tick held（有整包没送出）→ 暂停 HOLD；若自己最近供过电就缩到 `fed−1` 并标记 learned；否则视为网络满只暂停。不 held 且到期：learned 后每 STEP tick +1，没学习过就翻倍。单个 tick 的 held 不算（可能刚补燃料）。

### 3.7 无线充电 `Charger` + `ChargePlan`
- 每 `charging.intervalTicks`（20）一轮；对象：非 FakePlayer、带着终端且没关充电的玩家。顺序：盔甲在前，然后主栏按槽位；只处理 `stackSize==1`。
- GT/IC2 电动物品 `GTModHandler.chargeElectricItem(…, Integer.MAX_VALUE 等级, ignoreTransferLimit=true)`；RF 物品 `IEnergyContainerItem`。
- 先 `add(team, −reserved)` 预扣（失败整轮跳过），实际充多少把差额退回；`ServerEvents.addTeamTick(team,0,spent,spent,true)` 计入团队输出。

### 3.8 统计 `stats/Series`、`Bucket`
- 环：`SEC=60`、`MIN=60`、`HOUR=72`；累计 `total/today/session`（dayKey=yyyyMMdd 服务器本地时间）。
- "当前"读数（`SMOOTH=20`，`WINDOW=64`）：最近若干包，回看至少 20 tick，去掉最旧一包的能量 ÷ 首尾间隔；最新一包超过 `3×平均间隔+2` 归零。逐 tick 曲线 `TICKS=200` 只在被查看后 60 秒内记录。
- 存盘只存分/时环和累计；秒环和当前窗口不存。

### 3.9 告警 `core/alert`
- 每秒评估，只处理 `TeamData.alertsOn` 的团队（默认关）。类型：`BACKEND_DOWN`、`LOW_BALANCE`、`ETA_SHORT`、`CHUNK_FAIL`、`UNDER_SUPPLY`、`OVERLOAD`、`IDLE`（条件见 `AlertManager.evaluate`）。聊天提示前缀 "[FluxLite] "，同一 key 在 `chatCooldownSeconds` 内只发一次。

## 4. 网络

频道 `fluxlite`：`MsgToServer` 判别号 0、`MsgToClient` 1，格式 1 字节 kind + NBT；消息入队在主线程 tick START 处理（服务端每 tick 最多 256 条）。

| 方向 | Kind | 含义 |
|---|---|---|
| C→S | `CONNECTOR_REQUEST=0` | 连接器卡片数据 |
| C→S | `CONNECTOR_EDIT=1` | `op`：`OP_NAME=0`（≤32 字，去控制字符和 §）、`OP_TOGGLE=1`、`OP_DIRECTION=2`（side 0–11） |
| C→S | `CC_REQUEST=2` | 监控台/终端页面数据（`page` 0 总览/1 设备/2 告警/3 设置，`range`、`filter`、`sort`、`offset`、`rows`、`detail` 等） |
| C→S | `CC_ACTION=3` | `ACT_CHAT=0, ACT_REDSTONE=1, ACT_HOLOGRAM=2, ACT_HOLO_SIZE=3, ACT_HOLO_OPAQUE=4, ACT_ALERTS=5` |
| C→S | `TERM_REQUEST=4` | 终端 FluxEcho 页（`termPage`） |
| S→C | `CONNECTOR_DATA=0` | `ConnectorView.build` 或 `denied` |
| S→C | `CC_DATA=1` | `ControlCenterView.build` |
| S→C | `HOLO_DATA=2` | `HoloView.build` + `x,y,z,r` |
| S→C | `TERM_DATA=3` | `com.fluxecho.nexus.TerminalView.build(player, termPage)` |

权限：`near()` 要求同维度、距离 ≤ 64、方块已加载；`hand:true` 跳过距离但要 `carries()`；连接器数据和编辑要同队或 OP。详情 key：`"t"` 团队 EU、`"ts"` 团队蒸汽、`"p:<recordId>:<通道>"`、`"m:<recordId>:<posKey>"`。

**加新 Kind**：在 `Kinds` 加常量（C→S 和 S→C 编号各自独立），`ServerPackets`/`ClientPackets` 加分支，`PacketsTest` 之外没有对编号的测试，注意别和已有编号撞。

## 5. GUI 框架

- `ui/UiScreen`：即时模式 UI，每帧 `render(Canvas)` 重画并注册点击区，**不引用 MC 客户端类**（测试用 Java2D 画同一套界面，输出到 `build/previews`）。控件：`TextInput`、`segmented`、`chip`+`menus()`、`toggle`、`pill`、`button`、`chart`、`bars`、`window/card/closeButton/scrim`。主题 `ui/Theme`（Apple 暗色；输入绿、输出橙、双向青、蒸汽 `0xFFA8C7DA`）。
- `GuiHost`：MC 外壳，ESC/背包键关闭（输入框聚焦时除外），不暂停游戏；`tr()` 处理 `@L:` 前缀。
- `McCanvas.world(font)` 世界内绘制：法线朝上；`surface(true)` 写深度；**结束必须 `restoreTexture()`**（"Angelica only tells the shader pipeline when texturing is switched through glEnable / glDisable"，不然箱子等会变白）。
- `ControlCenterScreen` 的子类钩子：`protected float rail()`（左栏宽，默认 0）、`protected void rail(Canvas,x,y,h)`、`protected boolean page(Canvas,x,y,w,h)`（返回 true 表示子类接管内容区）、`tick()`、`scroll()`；受保护构造器带 `hand` 参数。
- 全息 `HoloPanel`（`W=168,H=122`，三段展开动画）、`HoloState`（`STALE_MS=2500` 无数据就收起）、`HologramRenderer`（跳过光影阴影 pass；方块光 240，有光影包时天空光 0；背面只画玻璃）。
- `Highlighter`：`RenderWorldLastEvent` 里画黄色脉动线框，关深度测试，仅同维度。

## 6. 配置 `config/fluxlite.cfg`（preInit 读一次）

| 分类 | 键 | 默认 | 说明 |
|---|---|---|---|
| general | `settlementPeriod` | 1 | 1–1200 tick |
| general | `wirelessLossPercent` | 0.0 | 只作用于发电输入 |
| general | `maxConnectorsPerTeam` | 64 | 0 = 不限 |
| general | `bufferPeriods` | 2 | 1–20 |
| chunkloading | `enabled` / `radius` / `keepLoadedWhenOwnerOffline` | true / 1 / true | radius 1 = 3×3 |
| cable_scan | `maxNodes` / `rescanInterval` / `maxSampledMachines` | 2048 / 100 / 64 | |
| statistics | `guiSyncInterval` | 10 | **读了但没用** |
| statistics | `saveIntervalMinutes` | 5 | |
| alert_defaults | `lowBalance` | "0" | 字符串，`Long.parseLong` 无 try，填错会抛异常 |
| alert_defaults | `etaMinutes` / `underSupplyPercent` / `idleMinutes` / `loadPercent` / `chatCooldownSeconds` | 10 / 90 / 0 / 90 / 300 | |
| recipes | `enableDefaultRecipes` | true | 同时删掉 0.4 的旧键 |
| steam | `enabled` / `maxLitresPerTick` | true / 1 000 000 | |
| compat | `rfNominalVoltage` / `pendingVoltage` / `pendingAmperage` | 8192 / 32 / 64 | |
| compat | `ic2MaxPacketsPerTick` | 16 | **读了但没用** |
| charging | `enabled` / `intervalTicks` / `maxEuPerSecond` / `batteries` / `armor` / `rf` | true / 20 / "0" / true / true / true | maxEuPerSecond 0 = 不限 |
| display | `hologramRange` | 12 | 0 = 关闭全息 |

## 7. 存档、区块加载、命令

- `Registry`（`data/fluxlite_registry.dat`）：连接器记录 `ConnectorRecord`（`id, om/ol, on, d,x,y,z, n, g(未使用), ls, p[12]{PortInfo + st Series}, tot, stot`）和 `TeamData`（`lm/ll, s, ss, al`=alertsOn, `ch`=chat；两者默认关）。线缆后的采样机器不存盘。
- `TileConnector` NBT：`ownerM/ownerL/ownerName/record`、`ports[12]{m,r,s,f?,sb,cb}`。
- 区块加载 `ChunkLoadManager`：票据类型 NORMAL，多个连接器复用一张票；拿不到票只警告一次（"raise maximumTicketCount for 'fluxlite' in config/forgeChunkLoading.cfg"）并置 `chunkLoadFailed`；条件 `enabled && hasActivePort() && (keepLoadedWhenOffline || 有队员在线)`；`tickSecond` 以注册表为准重建。
- `/fluxlite`：`balance [玩家]`、`add <EU> [玩家]`（OP）、`steam`、`addsteam`（OP）、`team`、`status`、`settle`（OP）。

## 8. 不变量和坑

1. **区块卸载时退款**：在 `ChunkEvent.Unload`（存盘之前）调 `release()`，"Refunding here keeps the saved NBT buffer at zero, so the energy can't be counted twice"。`TileEntity.onChunkUnload` 在存盘之后，太晚。`release()` 可重复调用。
2. 复制的结构（同一记录 id 出现在别的坐标）会新建记录；记录丢失按原 id 重建。
3. **安全优先**：读不出规格就不供电；未知设备只送小包。
4. `Adapters` 顺序有意义：优先能准确说出需求、无需换算的 API（AE2 走 GT EU 比 RF 少损失约一成）。
5. 扫描不加载区块（越界记 `truncated`）。
6. `GTPower` 靠反射读 GT 私有字段，GT 改名时 `GTPowerTest.theFieldsGtKeepsPrivateAreThere` 最先报警。
7. 统计方向以网络为视角：线缆后的采样机器 avgOut 记"输入"。
8. 服务器停止前强制 `markDirty`（统计每 tick 都变，平时只定期标脏）。

## 9. 测试

`src/test/java/com/fluxlite/`：
- `adapter/PacketsTest`（分包边界）、`RFAdapterTest`（方向决策表）、`RFTest`（汇率、往返不增值）；
- `backend/SteamNetworkTest`；`charge/ChargePlanTest`；`compat/GTPowerTest`；
- `core/FeedGovernorTest`（4000 tick 仿真）、`PortTest`、`SettlementTest`；`core/stats/SeriesTest`；
- `util/FmtTest`、`NamesTest`；
- `gui/preview/{PreviewTest,HoloPreviewTest,ModelPreviewTest}`：用 Java2D 把界面渲染成 PNG 到 `build/previews`，**不做断言**，缺 MC client.jar 时跳过；预览里没有 TerminalScreen 的 FluxEcho 页。
