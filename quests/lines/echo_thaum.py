"""The Echo line for Thaumcraft."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, echo_machine, give, item, quest, retrieval, write_line


TC = "Thaumcraft:"


def line():
    lore = quest(
        "echo_thaum/lore", "§5§l拾遗 · 要素",
        ECHO_LORE + "\n\n这些也是从别的岸漂来的——另一个时空写物质的字母。我亲手炼出过的每一种要素、捡到过的每一种魔力碎片，海里都留着它的纹。"
        "学会一次，以后用电现合成：注魔不用再炼要素，法杖不用再找节点。\n\n"
        "[note]研究：本实例的 config/Thaumcraft.cfg 里 research_difficulty 是 -1，研究直接用研究点购买，没有解谜小游戏。"
        "整合包更新可能把它改回去，记得再改一次。[/note]\n\n"
        "[note]找要素、攒研究点：Salis Arcana 让神秘透镜能在背包界面里扫物品（鼠标停在物品上），"
        "研究了「箱子扫描」（CHESTSCAN）以后能一次扫一整个箱子。[/note]",
        item(TC + "ItemEssence", 1),
        [checkbox()])
    memory = quest(
        "echo_thaum/memory", "§5要素记忆",
        "FluxEcho 的神秘机器按团队记住你们拿到过的要素，一台机器学会，团队所有机器都会。\n\n"
        "怎么学：\n"
        "• 装着要素的瓶子、罐子、要素水晶：学会里面的要素；\n"
        "• 魔力碎片：学会它的初始要素（风、火、水、地、秩序、混沌）；\n"
        "• 放进机器的输入槽，或者拿在手上右键机器都行。瓶罐学完会退到输出槽，碎片留在输入槽。\n\n"
        "[note]所以第一次还是要亲手炼：炼金炉加蒸馏塔，每种要素炼出一瓶就够了。[/note]\n\n"
        "碎片也是这些机器的耗材：输入槽里的魔力碎片（平衡碎片也算）按需折成碎片额度。",
        item(TC + "ItemShard", 6),
        [checkbox()],
        pre=["echo_thaum/lore"])
    echo = quest(
        "echo_thaum/essentia_echo", "§5要素回响仪",
        "一台 MV 机器，用炼金炉和源质罐做成：你亲手炼过要素，它才学得会。\n\n"
        "它按需合成团队学会过的要素：每 1 点要素的代价是它拆到初始要素后的单位数（初始要素 1，光 2，交换 3……），"
        "每单位 128 EU，外加碎片额度（1 块魔力碎片 64 单位）。\n\n"
        "[note]GT 的基础机器只存 64 个电包，回响仪改成能存 2048 个、最多 4 A 输入，一次大注魔也付得起。[/note]",
        echo_machine(2),
        [retrieval(echo_machine(2))],
        [give(item(TC + "ItemShard", 6, 16))],
        pre=["echo_thaum/memory"])
    outlet = quest(
        "echo_thaum/outlet", "§5要素回响口",
        "回响仪本身不能直接接神秘的东西，要在它旁边贴一个要素回响口。\n\n"
        "• 注魔：回响口在注魔矩阵 12 格内，注魔要什么要素就直接从回响口抽，回响仪当场合成。不用罐子，不用管道。\n"
        "• 管道：要素管道接在回响口上，下游的罐子贴了标签要哪种就送哪种；没有标签时，送回响口选定的要素"
        "（拿着装要素的瓶子右键回响口来选）。\n"
        "• 空手右键回响口：看团队学会了几种要素、选定的要素现在能合成多少。\n\n"
        "[note]回响口不存要素：每一点都是离开的那一刻才合成、才付钱。新放的回响口要等几秒才会被注魔矩阵发现。[/note]",
        item("fluxecho:essentia_outlet"),
        [retrieval(item("fluxecho:essentia_outlet"))],
        pre=["echo_thaum/essentia_echo"])
    charger = quest(
        "echo_thaum/vis_charger", "§5通量灵气汲取台",
        "通量层深处沉着大量的要素。汲取台用电把六种初始要素实时抽上来，灌进悬浮在它上方的法杖：不用节点，不用等。\n\n"
        "• 拿着法杖、权杖、长杖或灵气护符右键放上去，它会浮起来转着充；空手右键取回；\n"
        "• 每种要素每秒 5 点灵气，每点灵气 1000 EU（六种一起充约 1500 EU/t，装汲取模块更快也更费电）；\n"
        "• 电从 GT 线缆来，除了顶面哪一面都行，什么电压都收；内置 400 万 EU 电池；\n"
        "• 台子上方的全息投影一直显示电量、输入、速度、模块和法杖的灵气；Waila 看着它也能看到。\n\n"
        "[note]用法杖充能台做成：你亲手给法杖充过灵气。[/note]",
        item("fluxecho:vis_pedestal"),
        [retrieval(item("fluxecho:vis_pedestal"))],
        pre=["echo_thaum/memory"])
    modules = quest(
        "echo_thaum/vis_modules", "§5汲取台模块",
        "汲取台最多装 4 个模块，拿着模块右键装上，空手潜行右键取下最后装的那个。\n\n"
        "• §d汲取模块§r：每种要素每秒多 10 点灵气，可以叠加，装满 4 个是每秒 45 点；\n"
        "• §b无线充能模块§r：32 格内团队成员背包里的法杖、权杖、长杖、灵气护符一起充，人在旁边干活也不断灵气；\n"
        "• §e通量链接模块§r：接入团队的 GT 无线电网（就是 FluxLite 的通量网络），电量低于一半时自动取电，不用拉线。\n\n"
        "[note]无线充能模块和通量链接模块各只装 1 个。[/note]",
        item("fluxecho:vis_module", 0),
        [checkbox()],
        [give(item("fluxecho:vis_module", 0, 1))],
        pre=["echo_thaum/vis_charger"], main=False)
    insight = quest(
        "echo_thaum/insight_echo", "§5灵感回响仪",
        "研究点不够买研究？一台 LV 机器，用桌子、书写工具和神秘透镜做成（研究台本身没有物品形态）。\n\n"
        "• 特殊槽放一件你用神秘透镜扫描过的东西，一直留在槽里；\n"
        "• 每次耗 1 张纸，10 秒，把这件东西的要素点再给你一次，就像重新扫描一遍；\n"
        "• 你要在线：神秘只在玩家在线时才有研究数据。\n\n"
        "[note]挑要素多、又正好是你缺的那种东西放进去。神秘自己的研究点上限照常生效，点数越多涨得越慢。[/note]",
        echo_machine(4),
        [retrieval(echo_machine(4))],
        [give(item("minecraft:paper", 0, 64))],
        pre=["echo_thaum/lore"])
    crucible = quest(
        "echo_thaum/crucible_echo", "§5炼金回响釜",
        "一台 MV 机器，用坩埚做成。坩埚配方不用再往锅里扔东西攒要素了。\n\n"
        "• 输入槽放催化剂，配方要的要素当场合成（和要素回响仪一样：每单位 128 EU，碎片额度另算），直接出产物；\n"
        "• 只做你研究过、团队学会了全部要素的配方；\n"
        "• 一个催化剂对应好几个配方时，编程电路选第几个（顺序固定，NEI 里神秘自己的页面能看到有哪些）。",
        echo_machine(5),
        [retrieval(echo_machine(5))],
        pre=["echo_thaum/memory"])
    infusion = quest(
        "echo_thaum/infusion_echo", "§5注魔回响台",
        "一台 MV 机器，用符文矩阵和奥术基座做成。神秘里最磨人的注魔，亲手做一次就够了。\n\n"
        "• 特殊槽放一件你亲手注魔出来的成品（奥术工作台做的也行），一直留在槽里；\n"
        "• 九个输入槽放这件东西要的全部材料：注魔是中心物品加全部组件，奥术是全部原料（不看摆法）；\n"
        "• 注魔的要素当场合成（和要素回响仪一样：每单位 128 EU，碎片额度另算），奥术的灵气每点按 1 单位初始要素算；\n"
        "• 没有基座、没有不稳定度、不会炸，也不加扭曲。\n\n"
        "[note]只做你研究过、团队学会了全部要素的配方。碎片既是材料又是额度时，先算材料。[/note]",
        echo_machine(8),
        [retrieval(echo_machine(8))],
        pre=["echo_thaum/crucible_echo"])
    placed = [(lore, (0, 24)), (memory, (48, 24)), (echo, (96, 0)), (outlet, (144, 0)), (charger, (96, 48)),
              (crucible, (144, 48)), (insight, (48, 72)), (infusion, (192, 48)), (modules, (96, 96))]
    return write_line(
        "line/echo_thaum", "FluxEchoThaum", "§5拾遗 · 要素",
        "从别的岸漂来的字母。每种要素亲手炼一次，以后用电现合成；初始要素认识一次，以后用电充法杖。注魔不再炼要素，法杖不再找节点。",
        item(TC + "ItemEssence", 1), placed, ["Thaumcraft"])
