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

- **preInit**：`Config.load` → `Mods.detect` → `QuestInstaller.preInit` → `CodexModule.preInit`（物品 codex、`EchoNet.init`）→ `MatterModule` → `FrameModule` → `NexusModule` → `LibraryModule` → `NexusNet.init` → `GateModule` → 按在场：Bee/TC/Mob/Crop/AE `preInit`。
- **init**：按在场调各模块 `.machines()`（GT ID 在这里 `claim`；"registered even when a module is off, so placed machines keep their blocks"）→ `GateModule.init` → `NexusModule.init` → `LibraryModule.init` → `proxy.init()`。
- **postInit**：Codex、Matter、Frame、Nexus、Library、Gate，再按在场 Bee/TC/Blood/Mob/Crop/Mana/AE（注册图鉴类别和配方）。
- **loadComplete**：Bee/Crop/TC/Mob 补 NEI 示例 → `RecipeCheck.run()`。
- **serverStarted**：`QuestInstaller.serverStarted()`（主模组没有服务端命令）。
- **serverStopped**：`EchoLedger.reset`、`ResearchData.reset`、`Formed.clearServer`、`FrameEvents.clear`、`NexusModule.serverStopped`、`GateModule.serverStopped`、（神秘在场）`TCModule.serverStopped`。`Directory.clear()` 存在但没人调用。
- **`ClientProxy.init()`**：`CodexTooltips`（Forge + FML 总线）→ `MachineFx` → `FrameClient` → `TileMultiblock.clientWorld = WorldClient.class` → `NexusRender` → `EchoCrystalRender` → `MachineHolo` → `FlowFx` → `GateClient` → （神秘）`TCClient.init`、（植物魔法）`ManaHolo` → NEI 在场时注册客户端命令 `NeiCheck`。

## 2. 配置约定（`Config.java`）

- 类别名是 `private static final String` 常量；字段 `public static` 带默认值；`load()` 里 `c.getInt/getBoolean(key, CAT, 当前值, min, max, "英文说明")`；double 用 `c.get(...).getDouble()`；long 用字符串 + `parseLong`（带回退）。
- 每个类别要 `setCategoryComment`；只在客户端生效的项说明以 "Client:" 开头。
- **删除某项或改默认值**：把 `VERSION` 提到新版本，在 `upgrade()` 里用 `older(was, "x.y.z")` 删掉旧 key（加载时按新默认值重建）。现有迁移：0.5.0 删 `enchanting` 类别和若干 thaum key；0.6.0 删 `manaPerTick`、`manaPerPetal`。`ConfigTest` 测 `older()`。
- README「配置」一节的中文表要同步。
- 类别：`general`（`firstMachineId` 24530、`enableDefaultRecipes`）、`quests`（`install`）、`bees`、`thaumcraft`、`bloodmagic`、`mobs`、`crops`、`botania`、`codex`（`tooltipHints`）、`nexus`、`effects`（`machineEffects`、`flowTrails`、`effectRange` 32、`hologramRange` 16）、`ae2`（`enabled`）、`gates`。各模块的键和默认值写在对应的手册里。

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
| `EchoRecipes` | 配方先走 `GTModHandler`（`NOT_REMOVABLE`），GT 拒收退成 Forge 矿辞配方；`forForge()`；全部登记到 `RecipeCheck.expect`；缺材料只写日志 |
| `RecipeCheck` | loadComplete 时补回丢失的配方，报告空矿辞名 |
| `EchoRecipeMaps` | 每台机器一个 GT `RecipeMap` `fluxecho.recipe.<key>`（`disableRegisterNEI`，只给 NEI 当示例）；`map/page/later/examples/get`；全部 `synchronized`（"NEI asks from its own threads"） |
| `EchoPattern` | 给 AE 的"现在能做什么" |
| `Directory` | 内存里的机器登记表：键 `dim:x:y:z`，`Entry(team, dim, xyz, name 键, status 键, level, seen)`；`of(team)` 丢掉 1 小时没上报的，按 PROBLEM(2) > IDLE(1) > WORKING(0)、再按名字排序。上报方：`MTEEchoMachine`、`TileNexus`、`TileLibrary`、FluxDepths 的采集器和泵（每 100 tick）。读取方：`nexus.TerminalView` |
| `Owners` | `team(uuid)` = GT `SpaceProjectManager.getLeader`（异常退回本人）；`online`、`loginName`（"Thaumcraft and Blood Magic key their player data by login name"） |
| `CoreCircuits` | 矿辞 `circuitLV..circuitLuV` → 等级 1–6，`example/label/color`（FluxDepths 有自己的副本） |

状态颜色（`EchoText.statusColor`）：`working` 0x6CFF8A；`idle/ready` 0x86A6B8；`no_power/disabled` 0xFF6050；其余 0xFFB040。机器"房屋风格"（用户认可）：一台机器靠核心电路分阶、自己的贴图/GUI/NEI 页、全息默认关可开、通量世界观。其他回响机器保持原玩法（不加核心分阶，要加先问）。

## 4. 客户端视觉框架与光影安全

| 文件 | 作用 |
|---|---|
| `client/FluxDraw` | 通量风格平面 GL 绘制（不用纹理）：`pane/bar/column/scan/frame/corners/gradient/text/small...`、调色板（DEEP 0x0A1622、SEAM 0x1F3C4E、CYAN 0x4FE3FF、VIOLET 0x8A5CFF…）、`worldBegin/worldEnd` |
| `client/ShaderCompat` | 反射 Iris API：`packInUse()`、`shadowPass()` |
| `client/FarDraw` | "画完世界后"部件的登记表，光门从另一侧再画一遍（见 [gates.md](gates.md) §5） |
| `client/Motes` | 朝向相机的发光点（加法混合，`textures/effects/mote.png`） |
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
7. 登记 `FarDraw.add`，相对 `RenderManager.renderPos*` 画；服务端发包用 `gate.Sight.near`。
8. 世界里的东西相对相机画、每处理器 try/finally 恢复状态；全息默认关、可切换。

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
| `fluxecho_nx` | `nexus/NexusNet` | 0 ToServer（ASK_MAP/START/CANCEL）、1 ToClient（MAP/RESULT） |
| `fluxecho_gate` | `gate/GateNet` | 0 Transit（S→C） |
| `fluxlite` | FluxLite | 见 fluxlite.md §4（终端的 TERM_REQUEST/TERM_DATA 也在这里） |
| `fluxdepths` | FluxDepths `holo/HoloNet` | 0 全息（S→C） |

新增消息用新的 discriminator；handler 只入队，在主线程 tick 里处理。

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
| `NexusTextures.java` | 构架、中枢、书库、通量物质、索引卡 |
| `DepthsTextures.java` / `DepthsGuiTextures.java` | FluxDepths 的方块、物品、GUI |

- **资源**：`mcmod.info`（三个条目，后两个 `parent` 指向 fluxecho）；`assets/fluxecho/lang/{en_US,zh_CN}.lang`（行一一对应，按版本 `#` 分节）；`textures/{blocks,items,gui,effects}`；`quests/{index.json, DefaultQuests/...}`。
- **lang 键约定**：机器 `gt.blockmachines.fluxecho.<key>.name`；方块 `tile.fluxecho.<name>.name`；物品 `item.fluxecho.<name>.name`；创造栏 `itemGroup.fluxecho`；其余一律 `fluxecho.<area>.<…>`（`status.*`、`recipe.<key>`、`<key>.tip/.type/.gui_sample`、`nei.*`、`gui.*`、`holo.*`、`codex.*`、`waila.*`、`research.*`、`nexus.*`、`spring.*`、`gate.*`…）；文本里 `\n` 写字面量。

## 11. 测试（`src/test/java/com/fluxecho/`，JUnit 5，不需要 MC 运行时）

| 测试 | 守住什么 |
|---|---|
| `LangTest` | en/zh 键集合一致、无重复、占位符一致；代码里**字面写出**的键都存在（`EchoText.t/lines("x")` → `fluxecho.x`，`ChatComponentTranslation`/`translateToLocal("fluxecho.…")`）。**运行时拼出来的键查不出**，要自己核对 |
| `IsolationTest` | 读 `build/classes/java/main` 常量池：`forestry/`→`bees`、`thaumcraft/`→`thaumcraft`、`WayofTime/`→`blood`、`betterquesting/`→`quest/bq`、`com/kuba6000/mobsinfo/`→`mobs`、`ic2/`→`crops`、`vazkii/botania/`→`mana`、`appeng/`→`ae`。只看 `com/fluxecho/`（"The dev classpath always has Forestry (GT pulls it in), so a leak would never show in a dev run"） |
| `ConfigTest` | `older()` 版本比较 |
| `core/MachineIdTest` | offset 唯一且 < 40；不碰 FluxDepths |
| `core/EchoRecipesTest` | `forForge` |
| `nei/CatalystLookupTest`、`HandlerCopyTest`、`PageLayoutTest` | transfer rect、`newInstance()`、输出不压 NEI 按钮 |
| `quest/QuestPackTest` | 每条线文件齐全、一个 QuestLine.json、任务和摆放一致 |
| `logic/QuestOrderTest`、`QuestPlanTest` | 顺序文件合并、stale 文件 |
| `logic/*Test` | 各纯逻辑类（见各子系统手册） |

FluxLite、FluxDepths 另有 20 个测试文件；共约 55 个测试类、234 个测试（0.9.0）。
