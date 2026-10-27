"""The general Echo line: the Echo Codex."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, give, item, quest, write_line


def line():
    codex = quest(
        "echo_general/codex", "§3§l回响图鉴",
        ECHO_LORE + "\n\n回响图鉴记着团队亲手做过一次、通量层记住了的东西：拓过的蜂、树、蝴蝶，学会的要素，"
        "杀过的怪，拓过的作物，回响过的注魔成品，用过的血宝珠。\n\n"
        "• 右键打开，左边分类，右边是图标，鼠标停上去看名字；\n"
        "• 物品提示（NEI 里也一样）会标出「通量回响：团队已经亲手做过」，还没做过的会告诉你下一步怎么做；\n"
        "• Waila 看着一只怪，也能看到它有没有猎物印记。\n\n"
        "[note]合成：一本书、一颗末影珍珠、一块玻璃板。这里先送一本。[/note]",
        item("fluxecho:codex"),
        [checkbox()],
        [give(item("fluxecho:codex"))])
    return write_line(
        "line/echo_general", "FluxEchoGeneral", "§3通量回响 · 通用",
        "回响图鉴：团队亲手做过、通量层记住了的东西。",
        item("fluxecho:codex"), [(codex, (0, 24))], [])
