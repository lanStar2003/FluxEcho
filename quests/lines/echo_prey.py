"""The Echo line for prey (MobsInfo)."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, echo_machine, give, item, quest, retrieval, write_line


def line():
    lore = quest(
        "echo_prey/lore", "§c§l通量回响 · 猎物",
        ECHO_LORE + "\n\n猎物这一边：你亲手杀过的每一种怪，都在通量层里留着回响。"
        "末影珍珠、烈焰棒、线、火药……杀一只拓下印记，以后用电刷它的掉落，不用再建刷怪塔。",
        item("fluxecho:mob_imprint"),
        [checkbox()])
    blank = quest(
        "echo_prey/imprint", "§c猎物印记",
        "4 张纸加 1 块腐肉，合成 4 张空白猎物印记。写上一种怪有两种办法，都要你「亲手杀过」：\n\n"
        "• 背包里带着空白印记，亲手杀一只怪（机器、假玩家杀的不算），一张空白印记就写上那种怪；\n"
        "• 拿着空白印记右键一只活着的怪：原版统计里你杀过这种怪，就直接写上。\n\n"
        "[note]只有 MobsInfo 有掉落表的怪能写。背包里已经有那种怪的印记时，杀它不会再写一张。[/note]",
        item("fluxecho:mob_imprint"),
        [retrieval(item("fluxecho:mob_imprint"), ignore_nbt=True)],
        [give(item("minecraft:paper", 0, 16), item("minecraft:rotten_flesh", 0, 4))],
        pre=["echo_prey/lore"])
    echo = quest(
        "echo_prey/mob_echo", "§c猎物回响器",
        "一台 LV 机器。\n\n"
        "• 特殊槽放猎物印记，一直留在槽里；\n"
        "• 每次按怪的最大生命收费：每点生命 128 EU，最少 10 秒（末影人 40 点生命，10 秒一只）；\n"
        "• 掉落照 MobsInfo 的表随机，包括「玩家击杀才掉」的东西，和极限实体粉碎机一样；\n"
        "• 输入槽放一把武器：按它的抢夺附魔算掉落，每次扣 1 点耐久。\n\n"
        "[note]腐肉正好喂给鲜血回响器。[/note]",
        echo_machine(7),
        [retrieval(echo_machine(7))],
        pre=["echo_prey/imprint"])
    bosses = quest(
        "echo_prey/bosses", "§c首领",
        "凋灵、暮色森林的首领……亲手打过一次，也能拓成印记。\n\n"
        "首领要 50 倍的电和时间，凋灵一只大约 50 分钟。配置里可以改倍数，也可以整个关掉。\n\n"
        "[warn]凋灵骷髅头默认在掉落黑名单里（dropBlacklist），想刷就自己从配置里删掉。[/warn]",
        item("minecraft:nether_star"),
        [checkbox()],
        pre=["echo_prey/mob_echo"], main=False)
    placed = [(lore, (0, 24)), (blank, (48, 24)), (echo, (96, 24)), (bosses, (144, 24))]
    return write_line(
        "line/echo_prey", "FluxEchoPrey", "§c通量回响 · 猎物",
        "亲手杀一只怪拓下印记，以后用电刷它的掉落。末影珍珠、烈焰棒不用再建刷怪塔。",
        item("fluxecho:mob_imprint"), placed, ["mobsinfo"])
