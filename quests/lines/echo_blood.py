"""The Echo line for Blood Magic."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, echo_machine, give, item, quest, retrieval, write_line


BM = "AWWayofTime:"


def line():
    lore = quest(
        "echo_blood/lore", "§4§l拾遗 · 血",
        ECHO_LORE + "\n\n这些也是从别的岸漂来的——一个拿生命当货币的地方。我在祭坛里流过的每一滴血，海里都留着纹，"
        "宝珠就是我流过血的凭证。宝珠做出来以后，就不用再拿小刀割自己了。",
        item(BM + "weakBloodOrb"),
        [checkbox()])
    orb = quest(
        "echo_blood/first_orb", "§4第一颗血宝珠",
        "这一步只能亲手来：用献祭小刀割血，攒够 5000 LP，在祭坛里做出虚弱血宝珠，再拿着它右键绑定到自己身上。\n\n"
        "[note]这就是「手动做一次」。以后每一级宝珠也都要先在祭坛里做出来，鲜血回响器才会跟着提速。[/note]",
        item(BM + "weakBloodOrb"),
        [retrieval(item(BM + "weakBloodOrb"), ignore_nbt=True)],
        pre=["echo_blood/lore"])
    echo = quest(
        "echo_blood/blood_echo", "§4鲜血回响器",
        "一台 MV 机器，用血之祭坛和献祭小刀做成。\n\n"
        "• 特殊槽放一颗绑定过的血宝珠，一直留在槽里。宝珠越高级越快：虚弱 4 LP/t，学徒 8，魔导师 16，大师 32，"
        "大魔导师 48，超越 64；\n"
        "• 每 LP 耗 2 EU（最快 128 EU/t，正好是 MV），每 2000 LP 吃 1 块生肉或腐肉；\n"
        "• 默认把 LP 直接送进附近（水平 5 格、上下 10 格）血之祭坛的主罐，和苦难之井一样，不走那个每秒只进 20 mB 的输入缓冲。\n\n"
        "[note]祭坛满了它就停，不会浪费。祭坛上的自我献祭符文照样会放大送进去的 LP。[/note]",
        echo_machine(6),
        [retrieval(echo_machine(6))],
        [give(item("minecraft:rotten_flesh", 0, 32))],
        pre=["echo_blood/first_orb"])
    network = quest(
        "echo_blood/network", "§4灵魂网络模式：2 号电路",
        "编程电路选 2：LP 不进祭坛，直接进宝珠主人的灵魂网络，到宝珠的上限为止。\n\n"
        "印记、仪式、各种血魔法道具用的都是灵魂网络里的 LP，从此不用再拿宝珠右键扣血补网络了。",
        item("gregtech:gt.integrated_circuit", 2),
        [checkbox()],
        pre=["echo_blood/blood_echo"], main=False)
    placed = [(lore, (0, 24)), (orb, (48, 24)), (echo, (96, 24)), (network, (144, 24))]
    return write_line(
        "line/echo_blood", "FluxEchoBlood", "§4拾遗 · 血",
        "从别的岸漂来的血。第一颗宝珠亲手割出来，之后用电和一点肉产 LP：送进祭坛，或者直接补灵魂网络。",
        item(BM + "weakBloodOrb"), placed, ["AWWayofTime"])
