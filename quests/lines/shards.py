"""The FluxDepths line: the flux shard collectors, the drills and the fluid echo pumps."""
from questkit import FLUXDEPTHS_FIRST_ID, checkbox, give, item, quest, retrieval, write_line


def machine(tier):
    return item("gregtech:gt.blockmachines", FLUXDEPTHS_FIRST_ID + tier)


def line():
    lore = quest(
        "shards/depths", "§3§l通量深层",
        "FluxLite 的网络把电送进「通量层」——现实之下、贯穿所有世界的一层能量之海。\n\n"
        "通量层再往下，是「通量深层」。那里沉着你的船骸——大潮那一夜碎掉的中枢化成了灰，落在这里；也沉着世界诞生时留下的碎片：每个世界生成时，地下的每一条矿脉，都是深层里一块碎片的投影。"
        "这也是为什么 GT 的矿脉总是整整齐齐地每 3 个区块出现一次——那是碎片投进现实的网格。\n\n"
        "你挖空一条矿脉，碎片还在，只是不再投影。\n\n"
        "碎片采集器拿着一张「矿脉印记」对准深层里的那块碎片，让它再投影一次：不是投进地底，而是投进采集器的腔体里，一次凝结一块矿石。"
        "投影很微弱，所以很慢；核心电路越高级，共振越稳，速度越快。\n\n"
        "[note]为什么永远比不上原版的虚空采矿机？虚空采矿机是把整个世界的通量层撕开一道口子——所以出来的是全世界所有矿脉的混合物，"
        "要 LuV 的电，还要惰性气体撑住裂口。采集器只是透过针孔聆听一块碎片：最快的 LuV 核心也只有虚空采矿机 I 的 91%。[/note]",
        item("fluxdepths:imprint"),
        [checkbox()])
    imprinter = quest(
        "shards/imprinter", "§3印记拓印器",
        "一根带透镜的青铜杆，能听见脚下矿脉在深层里的回响。\n\n"
        "站在矿脉所在的区域里（以矿脉为中心的 3×3 区块）右键，就会拓下一张矿脉印记，每次消耗一张纸。\n\n"
        "[note]它只认世界生成时真实存在的矿脉——和 VisualProspecting 地图上记录的一样，所以得先找到矿脉。[/note]",
        item("fluxdepths:imprinter"),
        [retrieval(item("fluxdepths:imprinter"))],
        [give(item("minecraft:paper", 0, 16))],
        pre=["shards/depths"])
    first = quest(
        "shards/first", "§3第一张矿脉印记",
        "先找矿脉：\n"
        "• 用 GT 的探矿工具，或者打开地图（JourneyMap / Navigator）看 VisualProspecting 已经记下的矿脉；\n"
        "• 走到矿脉的范围里（水平位置在它的 3×3 区块之内，高度不限），拿着拓印器右键。\n\n"
        "印记上写着这条矿脉出哪些矿石、各占多少，以及它来自哪个世界。\n\n"
        "[warn]印记只在拓下它的那个世界里有效：主世界的印记在下界、月球都不会共振。外星的矿脉要先飞过去拓印，再在当地建采集器。[/warn]\n\n"
        "[note]NEI 里有单独的「通量碎片采集」页面，能看到每条矿脉的产出；查某种矿石的来源，也能看到它在哪些矿脉里。[/note]",
        item("fluxdepths:imprint"),
        [retrieval(item("fluxdepths:imprint"), ignore_nbt=True)],
        pre=["shards/imprinter"])
    steam = quest(
        "shards/steam", "§3通量碎片采集器",
        "只有一台采集器，从蒸汽时代用到 LuV：它的等级由核心槽里的电路板决定。\n\n"
        "• 核心槽空着时烧蒸汽：每 10 秒凝结 1 块矿石（每分钟 6 块），16 L/t 蒸汽；\n"
        "• 矿脉印记放进右边的印记格（卡片图标），用不到的格子会显示锁；\n"
        "• 钻头槽放一个 GT 钻头，它一直留在槽里，按概率磨损；\n"
        "• 用扳手设输出面，再在界面里打开自动输出，矿石就会自动推出去。\n\n"
        "出来的是普通矿石方块，后面照常打粉、洗矿。\n\n"
        "[note]0.3.0 以前的七台旧采集器：放下的会自动变成它（LV 到 IV 的还会自带一块同级电路），背包里的放进工作台就能换。[/note]",
        machine(0), [retrieval(machine(0))], pre=["shards/first"])
    core = quest(
        "shards/lv", "§b核心电路",
        "往核心槽放一块 GT 电路板，采集器就按那一级运行，随时可以换：\n\n"
        "• §7LV§r：32 V，24 EU/t，2.5 秒 1 块（每分钟 24），2 张印记；\n"
        "• §bMV§r：128 V，96 EU/t，1.65 秒 1 块（36），2 张印记；\n"
        "• §6HV§r：512 V，384 EU/t，1.25 秒 1 块（48），3 张印记；\n"
        "• §5EV§r：2048 V，1536 EU/t，0.9 秒 1 块（67），3 张印记；\n"
        "• §9IV§r：8192 V，6144 EU/t，0.65 秒 1 块（92），4 张印记；\n"
        "• §dLuV§r：32768 V，24576 EU/t，0.55 秒 1 块（109），4 张印记。\n\n"
        "多张印记轮流产出，是分享速度，不是叠加。两侧灯条会变成电路的颜色。\n\n"
        "[warn]和 GT 机器一样，电压高于核心等级会爆炸。换成低级电路之前先把高压线断开。[/warn]\n\n"
        "[note]可以用 FluxLite 连接器从无线电网给它供电。[/note]",
        machine(0), [checkbox()], pre=["shards/steam"])
    mv = quest(
        "shards/mv", "§6钻井液",
        "核心到 MV 起，针孔要用钻井液冷却：每块矿石消耗 20 L，加进界面左下的钻井液槽（或用管道从侧面灌）。\n\n"
        "钻井液在搅拌机里做，配方看 NEI。",
        machine(0), [checkbox()], pre=["shards/lv"])
    drill = quest(
        "shards/drill", "§e钻头",
        "钻头没有门槛：任意 GT 钻头都能用。它一直留在钻头槽里，每出一块矿石有 1/N 的概率磨损掉一个，平均能用 N 块：\n\n"
        "• 青铜 128、钢 256、铝 384、不锈钢 512、钛 768、钨钢 1024；\n"
        "• 其他材料按 GT 工具耐久的一半算。\n\n"
        "越好的钻头越耐用。可以用漏斗往钻头槽里补一整组。",
        machine(0), [checkbox()], pre=["shards/steam"], main=False)
    holo = quest(
        "shards/hologram", "§3全息投影",
        "界面右上角的按钮，或者拿螺丝刀右键采集器，能打开全息投影（默认关闭）。\n\n"
        "采集器上方会悬着一块面向你的投影屏：核心等级、状态、正在回响的矿脉、凝结进度和每分钟产量、电量和耗电、印记、钻头磨损、已凝结数量、钻井液。\n\n"
        "[note]16 格内可见，配置 shard_collectors.hologramRange 可以改距离，设 0 全部关闭。[/note]",
        machine(0), [checkbox()], pre=["shards/steam"], main=False)
    handover = quest(
        "shards/handover", "§3交接：虚空采矿机",
        "再往上，就该把通量层撕开了。\n\n"
        "原版的虚空采矿机（LuV 起）一次拿整个世界所有矿脉的混合，每秒 2 块起步，加上惰性气体能到每秒上百块。"
        "采集器最快的 LuV 核心也只有它的 91%，使命到此为止，剩下的交给它。\n\n"
        "[note]采集器不会因此作废：想专门刷某一条矿脉（只要铂、只要钍……），印记依然是最准的办法。[/note]",
        machine(0),
        [checkbox()], pre=["shards/lv"], main=False)
    fluid = quest(
        "shards/fluid_imprint", "§3流体印记",
        "深层里沉着的不只是矿脉。GT 的地下流体（石油、天然气、盐水……）也是碎片的投影，每个区块一种。\n\n"
        "拿着印记拓印器 [潜行右键]，就拓下脚下这个区块的地下流体，耗一张纸。印记记下的是这块碎片原本的储量，"
        "已经被抽走多少都不影响。\n\n"
        "[note]先用探矿工具或 VisualProspecting 地图找一块流体多的区块。[/note]",
        item("fluxdepths:imprint"),
        [checkbox()],
        pre=["shards/imprinter"], main=False)
    pump_lv = quest(
        "shards/pump_lv", "§7流体回响泵（LV）",
        "把流体印记放进印记槽，输入槽放一个钻头，它就在家里回响出那种流体，地下储量不会减少。\n\n"
        "• 每秒出「印记储量 × 0.1」升，24 EU/t；\n"
        "• 钻头：钢钻头或更好，放进输入槽，一直留在槽里按概率磨损：采集器里平均 N 块磨损一个的钻头，在泵里平均 N × 15 秒一个（钢钻头约 1 小时）；\n"
        "• 用扳手设输出面，流体自动推出去。\n\n"
        "[warn]和矿脉印记一样，只在拓下它的那个世界里有效。[/warn]",
        machine(7), [retrieval(machine(7))], pre=["shards/fluid_imprint"], main=False)
    pump_mv = quest(
        "shards/pump_mv", "§b流体回响泵（MV）",
        "每秒「印记储量 × 0.25」升，96 EU/t。钻头：铝钻头或更好。",
        machine(8), [retrieval(machine(8))], pre=["shards/pump_lv"], main=False)
    pump_hv = quest(
        "shards/pump_hv", "§6流体回响泵（HV）",
        "每秒「印记储量 × 0.5」升，384 EU/t。钻头：不锈钢钻头或更好。\n\n"
        "[note]再往上就交给 GT 的石油钻机：它们一次覆盖一大片区块。[/note]",
        machine(9), [retrieval(machine(9))], pre=["shards/pump_mv"], main=False)
    placed = [(lore, (0, 48)), (imprinter, (48, 24)), (first, (96, 24)), (steam, (144, 24)), (core, (192, 0)),
              (mv, (240, 0)), (drill, (192, 48)), (holo, (240, 48)), (handover, (288, 0)), (fluid, (96, 96)), (pump_lv, (144, 96)), (pump_mv, (192, 96)),
              (pump_hv, (240, 96))]
    return write_line(
        "line/shards", "FluxDepthsShards", "§3拾遗 · 船的灰",
        "深层沉着你的船骸。拓下一条矿脉的印记，在家里慢慢回响出它的矿石。从蒸汽时代一路到 IV，直到原版的虚空采矿机接手。",
        item("fluxdepths:imprint"), placed, ["fluxdepths"])
