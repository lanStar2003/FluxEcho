"""The Echo line for Botania: the Mana Echo Spring."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, echo_machine, give, item, quest, retrieval, write_line


def line():
    lore = quest(
        "echo_mana/lore", "§b§l通量回响 · 植物魔法",
        ECHO_LORE + "\n\n植物魔法这一边：花儿产出的魔力流经魔力池，也流过通量层，在那里留下了回响。"
        "亲手做出魔力池和魔力发射器，就能用电把这份回响引回地面，不用再伺候一排产魔花。",
        item("Botania:pool"),
        [checkbox()])
    echo = quest(
        "echo_mana/mana_echo", "§b魔力回响泉",
        "一台用魔力池和魔力发射器做成的机器，LV 就能做。\n\n"
        "• 核心槽放一块电路板（LV–LuV），放哪一阶就是哪一阶的电压和产量；没有电路板不工作；\n"
        "• 用 EU 产魔力：每点魔力 2 EU，LV 每 tick 16 魔力（32 EU/t），每升一阶 ×4；\n"
        "• 神秘花瓣放在花瓣槽里：每片的价值也随阶 ×4，所以各阶都是大约每分钟 15 片；\n"
        "• 魔力直接涌进范围内的魔力池（LV 水平 4 格、上下 2 格，每升一阶再远一些）："
        "从最近的开始，满了就换下一个，全满才停；\n"
        "• 充能槽放魔力平板或魔力戒指，会先充满它。\n\n"
        "[note]符文祭坛、魔力池浸染、魔力发射器都从魔力池里取，这一台就把它们都喂上了。[/note]",
        echo_machine(11),
        [retrieval(echo_machine(11))],
        [give(item("Botania:petal", 0, 16))],
        pre=["echo_mana/lore"])
    tiers = quest(
        "echo_mana/tiers", "§b升级核心",
        "回响泉只有一台，靠核心里的电路板升级：换一块更高阶的电路板，电压、产量、花瓣价值和范围一起变。\n\n"
        "• NEI 里查回响泉（或神秘花瓣的用途）能看到每一阶的数值；界面里点中间的泉池也能打开；\n"
        "• 拿螺丝刀右键可以打开全息投影，在机器上方显示状态、魔力池和电量（默认关闭）。\n\n"
        "[warn]换入更高阶的电路板会提高输入电压，先确认线缆和电源跟得上。[/warn]",
        echo_machine(11),
        [checkbox()],
        pre=["echo_mana/mana_echo"], main=False)
    return write_line(
        "line/echo_mana", "FluxEchoMana", "§b通量回响 · 植物魔法",
        "用电把魔力的回响引回地面，直接涌进魔力池。不用再伺候产魔花。",
        item("Botania:pool"), [(lore, (0, 24)), (echo, (48, 24)), (tiers, (96, 24))], ["Botania"])
