"""The Echo line for crops (IC2 and Crops++)."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, echo_machine, give, item, quest, retrieval, write_line


def line():
    lore = quest(
        "echo_crops/lore", "§a§l通量回响 · 作物",
        ECHO_LORE + "\n\n作物这一边：你杂交出的每一种作物、刷出的每一组属性，都在通量层里留着回响。"
        "拓下一袋好种子，以后要多少袋就出多少袋，不用再守着作物架等杂交。",
        item("fluxecho:crop_imprint"),
        [checkbox()])
    imprinter = quest(
        "echo_crops/imprinter", "§a种子拓印机",
        "一台 LV 机器，用作物分析仪做成。\n\n"
        "• 特殊槽放一袋扫描到满级的种子（作物分析仪扫 4 次），种子留在槽里，不消耗；\n"
        "• 输入纸，拓下一张作物印记：作物和它的生长、增产、抗性。",
        echo_machine(9),
        [retrieval(echo_machine(9))],
        [give(item("minecraft:paper", 0, 16))],
        pre=["echo_crops/lore"])
    first = quest(
        "echo_crops/first_imprint", "§a第一张作物印记",
        "印记的提示里写着三项属性。\n\n"
        "改属性：特殊槽放属性好的种子（或印记），输入槽放要改的印记，编程电路选 1 生长、2 增产、3 抗性、4 全部。"
        "作物不变，只换属性。\n\n"
        "[note]新作物还是得在作物架上亲手杂交出来一次；拓下来以后就不用再杂交了。[/note]",
        item("fluxecho:crop_imprint"),
        [retrieval(item("fluxecho:crop_imprint"), ignore_nbt=True)],
        pre=["echo_crops/imprinter"])
    echo = quest(
        "echo_crops/seed_echo", "§a种子回响箱",
        "一台 LV 机器，用作物架做成。\n\n"
        "• 特殊槽放作物印记，一直留在槽里；\n"
        "• 输入 1 根作物架，出 1 袋扫描满级、属性和印记一样的种子。\n\n"
        "[note]收获交给 GT 的工业温室；这里只负责把种子变出来。[/note]",
        echo_machine(10),
        [retrieval(echo_machine(10))],
        pre=["echo_crops/first_imprint"])
    placed = [(lore, (0, 24)), (imprinter, (48, 24)), (first, (96, 24)), (echo, (144, 24))]
    return write_line(
        "line/echo_crops", "FluxEchoCrops", "§a通量回响 · 作物",
        "一袋扫满的种子拓成印记，以后直接出同属性的种子。杂交一次就够了。",
        item("fluxecho:crop_imprint"), placed, ["IC2"])
