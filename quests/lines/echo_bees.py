"""The Echo line for bees (Forestry)."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, echo_machine, give, item, quest, retrieval, write_line


def line():
    lore = quest(
        "echo_bees/lore", "§3§l拾遗 · 蜂",
        ECHO_LORE + "\n\n这些也是从别的岸漂来的——一种会酿蜜的小东西，它们原来的世界大概全是花。我配出过的每一种蜂，海里都留着它的纹。"
        "拓下它的基因，以后要多少公主就培育多少，不用再找环境、不用再碰运气突变。\n\n"
        "还能像基因工业那样拆开来用：从不同的蜂身上拓下单个性状的基因样本，想要哪些性状就组合哪些，"
        "拼出你要的那只蜂。",
        item("fluxecho:bee_imprint"),
        [checkbox()])
    imprinter = quest(
        "echo_bees/imprinter", "§3蜂种拓印机",
        "一台 LV 机器，用一台便携蜂类分析仪做成：你亲手分析过蜂，它才学得会。\n\n"
        "• 特殊槽放一只蜂（雄蜂、公主、蜂后、幼虫都行），它会一直留在槽里，毫发无伤；\n"
        "• 输入槽放一张纸，约 5 秒拓下一张蜂种印记，记下这只蜂的全部基因；\n"
        "• 纸再配一个编程电路，就只拓电路对应的那个性状，得到一个基因样本（见后面的任务）。\n\n"
        "[note]和基因工业的采样器、模板是一个思路，但不用液态 DNA、不会失败，LV 就能用。[/note]",
        echo_machine(0),
        [retrieval(echo_machine(0))],
        [give(item("minecraft:paper", 0, 16))],
        pre=["echo_bees/lore"])
    first = quest(
        "echo_bees/first_imprint", "§3第一张蜂种印记",
        "印记上记着品种和 12 个性状，按住 Shift 能看到每一个。\n\n"
        "印记可以复制：特殊槽放一张印记、输入槽放纸，就再拓一张。",
        item("fluxecho:bee_imprint"),
        [retrieval(item("fluxecho:bee_imprint"), ignore_nbt=True)],
        pre=["echo_bees/imprinter"])
    edit = quest(
        "echo_bees/edit", "§3基因样本：想要哪个性状就拓哪个",
        "蜂种拓印机的特殊槽放一只性状好的蜂，输入纸，再选一个编程电路，就只拓下这一个性状，得到一个基因样本：\n\n"
        "1 速度、2 寿命、3 繁殖、4 温度耐受、5 夜行、6 湿度耐受、7 耐雨、8 穴居、9 花、10 授粉、11 领地、12 效果；\n"
        "13 一次拓下全部环境基因（4–8），14 拓下全部性状（不含品种）。\n\n"
        "从速度最快的蜂身上拓速度，从繁殖最多的蜂身上拓繁殖……攒一套你想要的性状。"
        "蜂一直留在槽里，样本用完了随时再拓，一张纸一个。\n\n"
        "[note]品种不在样本里：新品种还是得在蜂箱里亲手突变一次，拓成印记以后就不用再配了。[/note]",
        item("fluxecho:gene_sample"),
        [checkbox()],
        pre=["echo_bees/first_imprint"], main=False)
    splice = quest(
        "echo_bees/splice", "§3组合基因：工作台",
        "在工作台里把一张蜂种印记和几个基因样本放在一起，合成出来的印记品种不变，"
        "样本里的性状全部写进去：想要哪些性状，就放哪些样本。\n\n"
        "• 只放样本（两个以上）：合成一个包含全部基因的大样本，可以一次套到很多品种上；\n"
        "• 两个样本对同一个性状给出不同的值时合不出来；\n"
        "• 蜂、树、蝴蝶的印记和样本不能混。\n\n"
        "做好的印记放进幼虫培育箱，出来的就是你拼出来的那只蜂。",
        item("minecraft:crafting_table"),
        [checkbox()],
        pre=["echo_bees/edit"], main=False)
    assembler = quest(
        "echo_bees/assembler", "§3基因组合机",
        "工作台里的组合，换成一台 LV 机器来做，可以接自动化。\n\n"
        "• 九个输入槽里的东西算一次组合：一张印记加样本，或者只有样本；\n"
        "• 每样消耗一个，约 5 秒一次；\n"
        "• 接 AE 用处理样板时，打开接口的阻挡模式，免得两次组合的东西混在一起。",
        echo_machine(13),
        [retrieval(echo_machine(13))],
        pre=["echo_bees/splice"], main=False)
    incubator = quest(
        "echo_bees/incubator", "§3幼虫培育箱",
        "一台 LV 机器，用一台蜂箱做成。\n\n"
        "• 特殊槽放蜂种印记（也可以直接放一只蜂），一直留在槽里；\n"
        "• 输入蜂蜜滴：默认 12 滴出 1 只公主加 2 只雄蜂；选 2 号电路，16 滴出 16 只雄蜂；\n"
        "• 出来的蜂都是原始的（不会退化）、已分析、纯合子，培育时不看气候。\n\n"
        "[note]输出槽里的公主没取走，下一批就不会开始，不会浪费。[/note]",
        echo_machine(1),
        [retrieval(echo_machine(1))],
        [give(item("Forestry:honeyDrop", 0, 32))],
        pre=["echo_bees/first_imprint"])
    others = quest(
        "echo_bees/trees", "§3树和蝴蝶",
        "蜂种拓印机和幼虫培育箱也认林业的树和蝴蝶。\n\n"
        "• 拓印：特殊槽放一棵树苗（或花粉）、一只蝴蝶，输入纸，拓出树种印记、蝶种印记；\n"
        "• 基因样本：电路 1–12（蝴蝶 1–13）拓单个性状，14 拓全部性状，在工作台或基因组合机里组合，只能在同一种之间用；\n"
        "• 培育：树种印记吃林业肥料，4 个出 4 棵已分析的树苗；蝶种印记吃蜂蜜滴，8 滴出 1 只蝴蝶。",
        item("Forestry:sapling"),
        [checkbox()],
        pre=["echo_bees/incubator"], main=False)
    placed = [(lore, (0, 24)), (imprinter, (48, 24)), (first, (96, 24)), (edit, (144, 0)), (splice, (192, 0)),
              (assembler, (240, 0)), (incubator, (144, 48)), (others, (192, 48))]
    return write_line(
        "line/echo_bees", "FluxEchoBees", "§3拾遗 · 蜂",
        "从别的岸漂来的蜂。拓下一只真蜂的全部基因，以后要多少原始公主就培育多少；拓下单个性状的基因样本，想要哪些性状就组合哪些。",
        item("fluxecho:bee_imprint"), placed, ["Forestry"])
