# 开发手册 · 光门与内室（`gate/`、`gate/client/`、`mixins/early/`）

> 改光门、内室、折叠区、mixin、AT 之前读这一篇；**加任何"画在世界里"的效果之前也要读第 5 节**（FarDraw / Sight 是全项目都要遵守的接口）。设计背景见 [BLUEPRINT 3.10](../BLUEPRINT.md)，总览见 `CLAUDE.md`。
> 路径相对 `src/main/java/com/fluxecho/`。

## 0. 一段话

每扇外门在**它自己所在维度**的折叠区（`x>=1048576 && z>=1048576`）里对应一间内室（plot），内室里有一扇不可破坏的内门，两扇门互为 partner，**永远同维度**。所以穿门只是 `setPlayerLocation` 同世界挪位置，不换维度（避免了 `Minecraft.loadWorld` 每次换维度都 `System.gc()` 的卡顿）。
- 服务端：`Pins` 让玩家额外 watch 门另一侧的区块（mixin 保证不被收回、不被挤出发送队列）；`Sight` 让实体追踪和全息发包按"隔着门的视点"算距离。
- 客户端：有 Angelica（Sodium）且没开光影包 → `Portal` 从远端摄像机把整个世界再 `renderWorld` 一遍画进 FBO（**真实视图**）；其他情况 → `GateClient`/`FarView` 用显示列表简化画。只要有 Angelica，`Portal` 都在眼睛穿过光膜时切换摄像机，并让 Angelica 提前"走"远端建好网格，所以**进出无缝（开光影包也一样）**。

用户硬性要求（历次反馈）：两边一直加载、进出无感、门里看得到机器和全息、开着光影包也要成立（用户玩 Complementary）；只适配 GTNH 2.8.4 的 Angelica 1.0.0-beta66b（可以深入适配）；内室是照着模块样子的**封闭房间**，壳拆不掉，不要空岛。

**状态**：用户在 0.9.2（带着 0.8.5 的修复）里试过，正常：进出无感，进门那一下不再重新加载（2026-10-09）。之后改光门别让这些退回去（见第 8 节）。

## 1. 文件

| 文件 | 作用 |
|---|---|
| `gate/GateModule` | preInit 方块/TE/`GateNet`；init 注册 0.8.1 旧维度（失败只 warn）、`ForgeChunkManager` 回调 `Gates.Tickets`、`GateServer`（FML 总线）、`Zone`（Forge 总线）；postInit 配方；serverStopped `GateServer.stopped()` |
| `gate/Gates` | 放门、拆门、掉落物、`farBox`/`roomBox`、`sync`（registry→TE）、`makeRoom`、强加载 ticket、`moveOldPlots`（0.8.1 迁移） |
| `gate/GateRegistry` | **唯一权威数据**：`WorldSavedData "fluxecho_gates"`（主世界 mapStorage，所有维度共用）。`Entry{id,dim,x,y,z,facing,plot,inside,partner,room}`、`nextId`、`nextPlot` |
| `gate/GateServer` | 每 tick 每玩家：穿门、摔出内室救援、旧维度清人、`keep()`（Pins+Sight）、`Pins.tick` |
| `gate/Pins` | 玩家额外 watch 的区块（PlayerManager 层），mixin 回调 |
| `gate/Sight` | 每玩家的隔门视线 `GateSight.Line`。公开 API：`near`、`distanceSq`、`trackX/trackZ` |
| `gate/Zone` | `emptyChunk()`、`biome()`、取消折叠区刷怪 |
| `gate/Rooms` | 把 `RoomPlan` 直接写进 `ExtendedBlockStorage`，统一打光，玻璃顶补天光 |
| `gate/TileLightGate` | TE：`facing`、`inside`、`linked`、`partnerX/Y/Z/Facing`、`far`（Box）；客户端弱引用集合 `CLIENT`；不 tick |
| `gate/BlockLightGate`、`ItemBlockLightGate` | 门方块（门槛板，高 0.125）；门物品（NBT `plot`/`room`，潜行右键空气切换形状，禁止放进折叠区） |
| `gate/BlockInteriorShell`、`BlockInteriorGlass` | 内室壳体（注册名 `interior_floor`，meta 0–6）；玻璃顶 `interior_glass` |
| `gate/GateNet` | 频道 `fluxecho_gate`，一条 S→C `Transit`（id 0），handler 只入队 |
| `gate/GateTeleporter`、`InteriorProvider` | 只为 0.8.1 旧维度（7270）兼容保留 |
| `gate/client/GateClient` | 客户端主类：FarView 管理、`RenderWorldLastEvent` 里画门框和光膜、回退模式离屏图、涟漪过渡 |
| `gate/client/Portal` | 真实视图、无缝穿门、所有客户端 mixin 的回调 |
| `gate/client/Angelica` | 对 Angelica 1.0.0-beta66b 的反射桥 |
| `gate/client/GateCamera` | 不进世界的 `EntityLivingBase` 摄像机（`passCam`/`transitCam`），"never in the world, never ticks and is never sent anywhere" |
| `gate/client/FarView` | 回退画法：远端按 16³ section 编显示列表，收集 TESR |
| `gate/client/GateDraw` | 门框（两根塔柱 + 9 段浮弧）、没画面时的光膜、全屏涟漪 |
| `client/FarDraw`、`client/ShaderCompat` | 见第 5 节；Iris API 反射（`packInUse()`、`shadowPass()`） |
| `logic/GateGeometry` | 朝向、`entered`、`carry`/`exit`/`exitYaw`、`Box`、`view`、`project` |
| `logic/PortalMath` | 列主序矩阵、`invert`、`carryPlane`、`oblique`（Lengyel 斜近平面）、`perspective`、`corners`、`pyramid`、`membranePlane`、`screenRect` |
| `logic/GateSight` | `Line`、`viewpoints`、`nearestFlat`（Chebyshev）、`distanceSq` |
| `logic/GatePins` | 区块 key（同 `chunkXZ2Int`）、`outsideView`、`wanted()` |
| `logic/FoldedZone`、`logic/RoomPlan` | 折叠区布局；10 个内室模板 |
| `mixins/early/*`、`asm/FluxEchoCore` | 9 个 mixin；coremod + `IEarlyMixinLoader`，按端返回 mixin 列表 |
| 资源 | `mixins.fluxecho.early.json`（`required:true`，列表由 `getMixins` 提供）、`mixins.fluxecho.json`（空）、`META-INF/fluxecho_at.cfg`；`gradle.properties`：`coreModClass=asm.FluxEchoCore`、`usesMixins=true`、`accessTransformersFile=fluxecho_at.cfg` |
| `tools/GateTextures.java` | `blocks/gate/*` 10 张贴图 |

## 2. 玩家可见

- `fluxecho:light_gate`：硬度 3、抗爆 2000、亮度 0.6、碰撞高 0.125。内门挖不动（硬度 -1）；所有门 `canEntityDestroy=false`；外门拆下掉落带 `plot`/`room` 的物品（创造不掉）。
- 配方 `GEG/EOE/GEG`（玻璃、末影珍珠、黑曜石），要 `general.enableDefaultRecipes` 和 `gates.enableRecipe`。
- **放门**（`Gates.placed`，服务端）：门正面对着放置者（朝向 0=南 1=西 2=北 3=东）；物品带 `plot` 且那间内室的内门存在、没有 partner、同维度 → 复用；否则开新内室；形状取物品 NBT（默认 `hall`），群系取外门所在群系；聊天 `fluxecho.gate.opened`。不能放进折叠区或旧维度（`fluxecho.gate.not_inside`）。
- **拆门**：删 registry 条目、partner 变未连接，**内室保留**。站在没有 partner 的内门处会被送回出生点（"nobody stays shut in"）。
- **10 个模板**（`RoomPlan.TEMPLATES`，顺序 = 物品循环顺序，未知 id 回退 `hall`）：

| id | 平面 | 尺寸（含壳） | 玻璃顶 |
|---|---|---|---|
| hall | 八角 | 51×51 高 28 | |
| garden | 圆 | 59×59 高 36 | ✓ |
| gallery | 矩形 | 19×99 高 22 | |
| sanctum | 八角 | 35×35 高 34 | |
| altar | 圆 | 39×39 高 21 | |
| arena | 矩形 | 59×59 高 22 | |
| spring | 圆 | 75×75 高 16 | ✓ |
| datavoid | 矩形 | 67×67 高 26 | |
| canvas | 矩形 | 27×75 高 34 | |
| observatory | 圆 | 31×31 高 24 | ✓ |

  `Cell` 类型 `OUT, AIR, FLOOR, RIM, WALL, CEILING, PILLAR, LIGHT, TRIM, GLASS`（壳体 meta FLOOR 0…TRIM 6）。内门在 `(cx, 65, cz + halfZ-2)`，朝北对着房间。
- **壳**：`setBlockUnbreakable`、抗爆 6 000 000、`canEntityDestroy=false`、活塞推不动、没有创造物品栏条目。
- **折叠区** `FoldedZone`：`START=1<<20`，`SPACING=1024`，`COLUMNS=64`，`FLOOR_Y=64`；`centerX(plot)=START+floorMod(plot,64)*1024+512`。空区块由 `MixinChunkProviderServer` 在 `originalLoadChunk` HEAD 生成（HEAD 是因为 Angelica 和 ArchaicFix 都改了调用生成器的那一行）。折叠区不刷怪。
- **建房** `Rooms.build`：原本是空气的格子直接写 storage，非空气走 `setBlock`——**重建只补壳，里面的东西不动**。
- **强加载**：`ForgeChunkManager` NORMAL ticket，modData 存 `room`=plot 和 `chunks`；重进世界只重新 force 带 `room` 的 ticket。
- **0.8.1 旧档**：仍注册旧维度 7270；第一个 tick `moveOldPlots` 把旧内门迁到外门自己维度的新 `hall`；还在旧维度的玩家送回主世界出生点。

## 3. 服务端

**每 tick**（`GateServer.onServerTick` END，整体 try/catch）对每个玩家：旧维度清人 → 折叠区里 y<0 救援（传到内门前）→ 穿门检测（上一 tick 脚部位置 `LAST`、`COOLDOWN` 已过、不骑乘；遍历本维度 8 格内的已连接门，`entered(last, now)`）→ 每 5 tick（`REPIN`）`keep()`，每 40 tick 对 64 格内的门 `Gates.sync` → `Pins.tick(p)`。

**穿门 `through()`**：出口 `GateGeometry.exit(a,b,…,0.35)`，yaw 用 `exitYaw`；**先**发 `GateNet.Transit` **再** `setPlayerLocation`（S08）；`fallDistance=0`；**立刻**按落点重新 `keep()`（"before the server notices they moved, so nothing they had is taken away"）；`COOLDOWN=10` tick。门光膜宽 3 高 3，只能从正面进入。`carry` 旋转 `turns(from,to)=floorMod(to.facing-(from.facing+2),4)` 个四分之一圈。

**区块钉住** `keep()` → `GatePins.wanted` → `Pins.set`：
- `keepRange = max(16, viewRange)+16`（默认 48）；
- 门 3D 距离 ≤ range 时加入它的 far box **外扩 1 圈区块**（"the renderer only builds a chunk's mesh once all its neighbours are there"，0.8.5 的修复，`GatePinsTest` 守着）；
- 人在内室时加入 room box + outsideView(外门)；`holdBase` 时再加外门周围 `viewRadius+1` 的方块；
- 三个 mixin 守护：`PlayerInstance.removePlayer` HEAD 检查 `Pins.holds` 并 cancel；`PlayerManager.removePlayer` HEAD 先 `Pins.leave`（**必须先从 HELD 移除**，否则会把放手也 cancel 掉）；`filterChunkLoadQueue` HEAD/RETURN 记住再放回钉块；
- `Pins.tick`：队列里没被 tick 过的钉块手动 tick（服务器只发 tick 过的区块）。

**隔门实体追踪**：`keep()` 为 keepRange 内每扇门加 `Line(gate, partner)` 到 `Sight.LINES`；`MixinEntityTrackerEntry` 改写 `tryStartWachingThis` 读的玩家 posX/posZ 为离实体最近的视点。

**同步与存档**：`GateRegistry` 权威；TE 是给客户端的副本（NBT `facing/inside/linked/partner[4]/far[6]`，S35 描述包）；`Gates.sync` 有变化才 `markBlockForUpdate`；`GateServer.stopped()` 清空静态表。

## 4. 客户端

**模式判定**（`Portal`）：
- `usable = !broken && gates.realView && Angelica.ready()`（`enableSodium` 且能拿到 `RenderingState.setProjectionMatrix`）
- `drawing = gates.liveView && viewRange>0 && FBO 可用 && !ShaderCompat.packInUse()`
- `hooked` = 500 ms 内 `beforeWorld` 被调过（自检 `require=0` 的 `MixinEntityRenderer` 是否生效）
- `real() = hooked && usable && drawing`；`seamless() = hooked && usable && (drawing || Angelica.canWalk())`

| 情况 | 门里 | 穿门 |
|---|---|---|
| A：Angelica、无光影 | 真实视图（第二次 renderWorld） | 无缝 |
| B：Angelica、开光影 | FarView 简化画 + 远端 walk 预建网格 | 无缝 |
| C：无 Angelica / realView=false / Portal 坏了 | FarView | 涟漪过渡（0.8.2 做法） |
| D：liveView=false / viewRange=0 / 无 FBO / GateClient 坏了 | 光膜只发光 | 有 Angelica 时仍可能无缝 |

**真实视图每帧**：`updateCameraAndRender` 调 `renderWorld` 前 → `Portal.beforeWorld`（换世界时新建摄像机、`Angelica.forget()`；`transit()`；`passes()`）。`passes()` 按距离排序候选门（viewer 在门正面、≤ viewRange+4），最多 `PASSES=2` 扇真画：`passCam` 放到 carry 后的位置，绑 FBO、scissor 到光膜屏幕矩形、`renderViewEntity=passCam`、`Angelica.enter(key)`、调 `mc.entityRenderer.renderWorld(pt, 0L)`，finally 全部恢复。其余门按帧间隔 `walk`（只建网格不画），最近的门 ≤16 格时定期 all-walk（无视锥，从四面八方建）。
- 嵌套 renderWorld 里：`bob` 补视角晃动；`oblique()` 在 `ClippingHelperImpl.getInstance` 前把投影的近平面斜放到另一边光膜上（同时 `Angelica.projection(...)` 让 Sodium 的区块 shader 用同一矩阵）；`planes()` 把视锥换成光膜金字塔；`MixinChunkGraphCuller` 关遮挡剔除；粒子朝向 GateCamera；`RenderHandEvent` 取消。
- **嵌套 renderWorld 照常触发 `RenderWorldLastEvent`**：全息等部件自然画进门里。`GateClient.onRenderLast` 在 `inPass` 时跳过被看穿的那扇门，其他门只发光（门里的门不递归）。
- 主 renderWorld 里光膜用 12×12 网格按屏幕 UV 贴 FBO；摄像机贴着光膜时全屏贴。
- `Angelica.enter/leave` 为每扇门单独保存 `lastCameraTranslucentX/Y/Z`，避免透明方块每帧重排。

**光影包路径**（`Angelica.walk`）：`Camera.INSTANCE.update(passCam)` → `new Frustrum()`（mixin 套用门视锥）→ `RenderDevice.enterManagedCode()` → `SodiumWorldRenderer.updateChunks(camera, frustum, false, walks--, false)` → 退出、恢复 camera。只建网格不画；光膜仍由 FarView 画。"With a shader pack the world cannot be drawn twice in a frame (the pack's passes and its memory of the last frame would mix the two cameras)"。

**穿门过渡**：
- 无缝（`Portal.transit()`）：检测到眼睛穿过光膜 → 每帧把 `transitCam` 放到 carry 后的位置当 renderViewEntity；1500 ms 超时或回到近侧结束。服务器 S08 到达时 `MixinNetHandlerPlayClient`（`beforeJump/afterJump`）：位移 ≥16 格时把位置、prevPos、lastTickPos、yaw 系列、速度一起 carry，`calm=600 ms`。
- 回退（`GateClient`）：涟漪 overlay（alpha 1 → 被挪走 16 格以上且过了 250 ms 或 1500 ms 后 300 ms 淡出）。

**FarView**（回退画法）：每 10 tick 为 viewRange+8 内的已连接门建 view；16³ section 两个显示列表（pass 0/1），`ChunkCache`+`RenderBlocks` 编译；每 view 最多 256 个 TESR；每帧总预算 3 个 section。`picture()` 画进 FBO，`GL_CLIP_PLANE0` 只留门后，pass0 → TESR → pass1 → 远端实体 + `FarDraw.draw(...)`。

**降级都是一次性的**（本次运行内不恢复）：`Portal.broken`、`GateClient.broken`、`FarDraw.failed`、`Angelica.sortBroken/walkBroken`。

## 5. 其他子系统必须遵守的接口：FarDraw 与 Sight

**新增任何"画完世界后"的效果（全息、动效、轨迹、中枢渲染……），四件事都要做：**
1. 在 `RenderWorldLastEvent` 里画，并登记：
   ```java
   MinecraftForge.EVENT_BUS.register(h); FarDraw.add(h::onRenderLast);
   ```
   现有登记者：`MachineHolo`、`MachineFx`、`FlowFx`、`NexusRender`、`ManaHolo`、`PedestalHolo`、`fluxdepths/client/HoloClient`。
2. **相对 `RenderManager.renderPosX/Y/Z` 画**，距离和淡出也按它算，**不要用 `mc.thePlayer` 的位置**；朝向摄像机的公告板用 `RenderManager.instance.playerViewY/X`，不要用玩家的 `rotationYaw`。回退路径里 `FarDraw.draw` 被调用时（`GateClient.far`）：`renderPos*`、`RenderManager.viewerPos*`、`TileEntityRendererDispatcher.staticPlayer*` 都换成了门另一侧的摄像机，`playerViewY` 换成 carry 后的朝向；modelview 已经平移并旋转好，按 renderPos 相对坐标画的东西直接落在本门离屏图里的正确位置；绑着离屏 FBO、开着 CLIP_PLANE0、着色器程序为 0、`GL_TEXTURE_2D` 开着、光照和 lightmap 关着。
3. `ShaderCompat.shadowPass()` 时直接返回。
4. **容忍每帧被调用多次、而且不是站在玩家位置**：
   - 真实视图（无光影）：嵌套 renderWorld 让 `RenderWorldLastEvent` 每帧最多多来 `PASSES=2` 次；这时 `mc.renderViewEntity` 是 `GateCamera`，`Portal.inPass()` 为真，`Portal.passGate()` 是摄像机正看出去的那扇（另一侧的）门。
   - 回退画法（开光影包，也就是用户实际在用的情况）：`FarDraw.draw` 每帧对**每扇画了离屏图的门**各调一次（96 格内、在视锥里、已连接、人在门正面——没有固定上限）；这时 `renderViewEntity` 仍是玩家、`Portal.inPass()` 为 false，变的只有第 2 条列出的摄像机字段。
   - 所以：动画按 `getTotalWorldTime() + partialTicks` 或系统时间算，不按调用次数推进；不要在 `onRenderLast` 里生成粒子、发包或改世界/全局状态（现有部件都是幂等的：清理过期条目可以，计数器自增不行）。
- 任何部件抛异常 → `FarDraw.failed=true`，**所有**部件在本次运行内都不再画进门里。

**服务端**：
- **按距离挑接收者的包**（全息、轨迹；自己遍历 `playerEntities` 算距离的、`sendToAllAround` 的）必须改用 `Sight.near(world, x, y, z, range)`（直接距离或隔门距离在 range 内）。现有调用方：`fluxdepths/holo/HoloNet.send`、`fluxlite/tile/TileControlCenter.pushHologram`、`codex/EchoNet.flow/holo`。
- **走区块观察者的数据不用管**：TE 描述包（`markBlockForUpdate` → `getDescriptionPacket`）、方块更新都发给 watch 那个区块的人，而 `Pins` 让 keepRange 内隔门看的人本来就 watch 门另一侧的区块；实体由 `MixinEntityTrackerEntry` 管。中枢（`nexus/TileMultiblock`）就是走描述包，所以没有 `Sight` 调用。
- 两者的范围一致：`Pins` 和 `Sight` 都只算 keepRange（默认 48）内的门。

## 6. Mixin 与 AT

| Mixin（端） | 目标 | 作用 | require |
|---|---|---|---|
| `MixinChunkProviderServer`（两端） | `originalLoadChunk` HEAD，cancel，`remap=false` | 折叠区空区块 | 默认 |
| `MixinPlayerManager`（两端） | `removePlayer` HEAD → `Pins.leave`；`filterChunkLoadQueue` HEAD/RETURN | 钉块不丢 | 默认 |
| `MixinPlayerInstance`（两端） | `PlayerInstance.removePlayer` HEAD，cancel | `Pins.holds` 时不收回 | 默认 |
| `MixinEntityTrackerEntry`（两端） | `tryStartWachingThis` 里玩家 posX/posZ 的 GETFIELD（MixinExtras `@ModifyExpressionValue`） | 隔门实体追踪 | 0 |
| `MixinEntityRenderer`（客户端） | `renderWorld` 调用前后、`setupViewBobbing` 之后、`ClippingHelperImpl.getInstance` 之前 | 真实视图主钩子 | 全部 0（`hooked()` 自检） |
| `MixinClippingHelperImpl`（客户端） | `getInstance` RETURN | 门视锥 | 默认 |
| `MixinActiveRenderInfo`（客户端） | `updateRenderInfo` TAIL；`getBlockAtEntityViewpoint` HEAD | 粒子朝向、介质 | 默认 |
| `MixinNetHandlerPlayClient`（客户端） | `handlePlayerPosLook` HEAD/RETURN | 无缝落点 | 默认 |
| `MixinChunkGraphCuller`（客户端，`@Pseudo`） | Sodium `ChunkGraphCuller.initSearch` RETURN | pass 期间关遮挡剔除 | 0 |

AT（`META-INF/fluxecho_at.cfg`）开放：`PlayerManager$PlayerInstance`、`PlayerManager.func_72690_a`（getOrCreateChunkWatcher）、`field_72698_e`（playerViewRadius）、`PlayerInstance.field_73263_b`、`field_73264_c`、`EntityRenderer.func_78481_a`（getFOVModifier）。

**reobf 的坑**：0.8.2 开 mixin+AT 后，一次本地构建在陈旧的 RFG 状态下把继承来的 MC 成员 reobf 错了（`setBlockName` 留着 MCP 名），游戏卡在 33%。所以**测试用 jar 只用 CI 发布的那个**，并 javap 检查 Block 子类构造器调用的是 `func_*` 名。在这个整合包里（RFB + Java 25），模组初始化异常会被 log4j 的 `NoClassDefFoundError: net/minecraft/block/BlockContainer` 盖住——看到它先怀疑 reobf 或别的 init 异常。

## 7. 配置 `gates`

| 键 | 默认 | 说明 |
|---|---|---|
| `enableRecipe` | true | 还要 `general.enableDefaultRecipes` |
| `viewRange` | 32（0–64） | 服务端 keepRange=max(16,v)+16；客户端 FarView v+8、Portal 候选 v+4；0 关闭视图 |
| `liveView` | true | 关掉光膜只发光 |
| `realView` | true | 关掉回到 0.8.2（客户端） |
| `holdBase` | true | 在内室里时外门周围一直保留 |
| `dimensionId` / `providerId` | 7270 / 7270 | 0.8.1 旧维度，只为旧档保留 |

## 8. 安全修改须知

- **`FoldedZone` 常量不能动**：存档只存 plot 号，坐标靠公式重算；保持 START 区块对齐、`reach + 15*16 < SPACING/2`、坐标 < 30M。
- **`RoomPlan` 模板**：id 存在 registry 和物品 NBT 里；改形状会让已有内室的 `roomBox` 与实际方块不一致；增删会改循环顺序；改完跑 `RoomPlanTest`（封闭、可达、光照 ≥8、门周围空间）。
- **`GatePins` 的 border=1** 去掉会让 0.8.5 修好的进门加载问题复发。
- **传送**只能 `setPlayerLocation`（同世界）；时间阈值是一起调的：客户端 `calm` 600 ms ↔ 服务端 `COOLDOWN` 10 tick、`Transit` 1500 ms、`carried` 3 格容差、出口 margin 0.35。
- **渲染器相关 mixin 保持 `require=0`**；改 AT 时同步检查 `Pins`/`Portal`。
- **Angelica 升级**：反射名单在 `Angelica.init()` 和 `Portal.noOcclusion`（`AngelicaConfig.enableSodium`、`RenderingState.INSTANCE.setProjectionMatrix`、`SodiumWorldRenderer.getInstance/chunkRenderManager/updateChunks`、`ChunkRenderManager.lastCameraTranslucentX/Y/Z`、`Camera.INSTANCE`、`RenderDevice.enter/exitManagedCode`、`ChunkGraphCuller.useOcclusionCulling`）；失败降级不崩。
- **性能旋钮**：`PASSES=2`；walk 频率 `frame%3/%2`，all-walk `%4/%8`；FarView 每帧 3 个 section、`MAX_TILES=256`；`REPIN=5`；sync 每 40 tick。
- 用户反馈过的问题都要先在**光影包开着**的前提下想清楚（见 `CLAUDE.md`）。用户报画面问题时先看 `logs/latest.log` 里有没有 "Using shaderpack"。

## 9. 限制与文档出入

- 光影包下门里用简单画法：不经过光影、看不到别的模组在世界上画的东西和粒子（进出照样无缝）。门那边的声音听不到。门里的门不递归。
- `BlockLightGate` 的 Javadoc 还用 0.8.1 的说法。
- `Sight.LINES` 以 `EntityPlayerMP` 为 key，但重生**不会**留下旧条目：1.7.10 的 `Entity.equals/hashCode` 按 `entityId`，`respawnPlayer` 把旧 id 设给新实例，所以新旧实例是同一个 key（已用 javap 核对），登出时 `Sight.forget` 一并清掉。

## 10. 测试（`src/test/java/com/fluxecho/logic/`）

- `GateGeometryTest`：转向符合 MC yaw；只能从正面进入；exit 落点和 yaw；carry 互逆；`view` 盒子；`project` 与 OpenGL 一致。
- `PortalMathTest`：逆矩阵；斜近平面在平面处切割且屏幕位置不变；金字塔包含光膜能看到的；`screenRect` 各种情况。
- `GateSightTest`：视点 = carry；`nearestFlat`/`distanceSq`。
- `FoldedZoneTest`：plot ↔ 坐标；相邻间距 1024；中心在区块角上。
- `RoomPlanTest`：封闭、可达、门周围空间、光照 ≥8、玻璃房、模板循环。
- `GatePinsTest`：进出都不掉区块；**门显示的每个区块 8 个邻居都已加载**（0.8.5）；远离门什么都不留。
- 渲染部分没有单元测试，只能用户进游戏看。
