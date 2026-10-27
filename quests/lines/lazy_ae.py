"""The lazy AE line: a whole AE2 network at the start, from FluxDepths 0.1.x."""
from questkit import checkbox, give, item, quest, retrieval, stacks, write_line


AE = "appliedenergistics2:"
PART = AE + "item.ItemMultiPart"
MAT = AE + "item.ItemMultiMaterial"


def line():
    core = quest(
        "lazyae/core", "§b§l懒人AE · 核心与存储",
        "一套能一直用到 EV 的 AE 网络核心。\n\n"
        "[note]ME 自供能控制器自带无限能源，一个就够整个网络用：不用能源接收器，也不用谐振仓。"
        "另外 7 个普通 ME 控制器和它贴在一起，算同一个控制器，多出来的面都能接线。[/note]\n\n"
        "怎么搭：\n"
        "1. 8 个控制器拼成 2×2×2 的一整块，自供能的放在哪个位置都行。从控制器的面上接致密线缆当主干，一面最多 32 个频道。\n"
        "2. 两台 ME 驱动器插上存储元件：物品用 64k / 16k 元件；流体用多重流体存储元件，一个能存 5 种流体，10 个一共 50 种。\n"
        "3. 合成终端看物品、直接合成；流体终端看流体；流体样板终端写样板，「合成」「处理」两种模式都能写；"
        "接口终端统一管理所有接口里的样板。\n\n"
        "[warn]领取前先清空背包，放不下的会掉在地上。[/warn]",
        item(AE + "tile.BlockCreativeEnergyController"),
        [checkbox()],
        [give(item(AE + "tile.BlockCreativeEnergyController"), item(AE + "tile.BlockController", 0, 7),
              item(AE + "tile.BlockDrive", 0, 2),
              item(AE + "item.ItemBasicStorageCell.64k", 0, 6), item(AE + "item.ItemBasicStorageCell.16k", 0, 4),
              item("ae2fc:multi_fluid_storage64", 0, 6), item("ae2fc:multi_fluid_storage16", 0, 4),
              item(PART, 360), item("ae2fc:part_fluid_terminal"), item("ae2fc:part_fluid_pattern_terminal"),
              item(PART, 480), item(AE + "item.ToolNetworkTool"), item(AE + "item.ToolMemoryCard", 0, 2))])
    crafting = quest(
        "lazyae/crafting", "§b§l懒人AE · 自动合成",
        "让 AE 替你干活。\n\n"
        "• 工作台配方：在流体样板终端里用「合成」模式写样板，放进 ME 接口，接口贴着分子装配室。要 GT 工具的配方也行，工具磨损后会还回网络。\n"
        "• 机器配方（蒸汽打粉机、锻造锤、合金炉……）：用「处理」模式写样板，放进 ME 二合一接口，二合一接口贴着机器并打开「阻挡模式」；"
        "机器的另一面贴一个 ME 输入总线，把产物抽回网络。\n"
        "• 要流体的配方也用「处理」模式写：流体可以直接放进样板格子（从 NEI 拖进去，或拿装着流体的容器点一下），二合一接口会把流体一起送进机器。\n"
        "• 合成 CPU：拼成 2×2×2，每组放 1 个 64k 和 1 个 16k 合成存储器、4 个并行处理单元、1 个合成单元、1 个合成监控器（能看进度）。这里给了两组的料。\n\n"
        "[note]加速卡插进输入 / 输出总线会快很多；样板扩容卡让一个接口放更多样板；合成卡让输出总线缺货时自动下单。[/note]",
        item(AE + "tile.BlockMolecularAssembler"),
        [checkbox()],
        [give(item(AE + "tile.BlockMolecularAssembler", 0, 12), item("ae2fc:fluid_interface", 0, 12),
              item(AE + "tile.BlockInterface", 0, 4), item(AE + "tile.BlockCraftingStorage", 3, 2),
              item(AE + "tile.BlockCraftingStorage", 2, 2), item(AE + "tile.BlockCraftingUnit", 1, 8),
              item(AE + "tile.BlockCraftingUnit", 0, 2), item(AE + "tile.BlockCraftingMonitor", 0, 2),
              stacks(MAT, 52, 192), item(MAT, 30, 24), item(MAT, 53, 8), item(MAT, 27, 8), item(MAT, 54, 8),
              item(MAT, 29, 4), item(MAT, 26, 4))])
    cables = quest(
        "lazyae/cables", "§b§l懒人AE · 线缆与输入输出",
        "把网络接到每一台机器上。\n\n"
        "• 玻璃 / 包层线缆走 8 个频道，智能线缆能看到频道占用，致密线缆走 32 个。控制器每一面出 32 个频道，用致密线缆做主干，再分出普通线缆。\n"
        "• 存储总线贴在箱子、抽屉、储罐上，网络就能直接读写它们。\n"
        "• 输入总线把机器的产物抽回网络，输出总线把物品推出去；流体用对应的流体总线。\n"
        "• ME 标准发信器：库存低于设定值时输出红石，可以用来开关锅炉、机器。\n"
        "• P2P 通道能把一整束频道送到远处。\n\n"
        "[note]GTNH 里 AE 要到 EV 才能自己做，这套的量就是按撑到那时准备的。[/note]",
        item(PART, 56),
        [checkbox()],
        [give(stacks(PART, 16, 128), item(PART, 36, 64), item(PART, 56, 64), item(PART, 76, 16), item(PART, 140, 16),
              item(PART, 240, 8), item(PART, 260, 8), item(PART, 220, 8), item(PART, 280, 4),
              item("ae2fc:part_fluid_import", 0, 4), item("ae2fc:part_fluid_export", 0, 4),
              item("ae2fc:part_fluid_storage_bus", 0, 4), item(PART, 460, 4))])
    provider = quest(
        "lazyae/echo_provider", "§b回响 ME 供应器",
        "FluxEcho 的回响机器也能交给 AE 自动合成，不用给每台机器写样板、配接口。\n\n"
        "• 把供应器贴在回响机器旁边（六个面都行），再接上 AE 网络，占一个频道；\n"
        "• 旁边的机器现在能做什么，它就自动把对应的「处理」配方挂进 AE：幼虫培育箱出公主 / 树苗 / 蝴蝶，种子回响箱出种子，"
        "炼金回响釜出坩埚产物，注魔回响台出注魔成品，附魔回响器出附魔书；\n"
        "• AE 下单时它把原料塞进机器，机器输出槽里的东西每秒收回网络。\n\n"
        "[note]换了样本或电路，配方会自动跟着变。猎物回响器的掉落是随机的，没法做成配方，但它的产物也会被收回网络。[/note]",
        item("fluxecho:echo_provider"),
        [retrieval(item("fluxecho:echo_provider"))],
        pre=["lazyae/crafting"], main=False)
    return write_line(
        "line/lazyae", "LazyAE", "§b私货 · 懒人AE",
        "开局直接领一整套 AE2，蒸汽时代就能全自动。\n\n"
        "三个任务都是勾选即领，没有前置。GTNH 正常要到 EV 才能自己做 AE，这套的量够你撑到那时。",
        item(AE + "tile.BlockCreativeEnergyController"),
        [(core, (0, 0)), (crafting, (48, 0)), (cables, (96, 0)), (provider, (48, 48))],
        ["appliedenergistics2", "ae2fc"])
