"""Volume II, Echoes (line/echo_general): the Echo Codex and the first records (narrative 7.2)."""
from lines.echo_lore import ECHO_LORE
from questkit import checkbox, echo_machine, give, item, quest, retrieval, write_line

# The single-block echo machines (docs/dev/echo-machines.md; offsets 3 and 12 are retired).
ECHO_MACHINES = (0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 13)


def scoreboard(name, shown, target):
    """BetterQuesting's scoreboard task: done once the player's score in the objective is at least the target. The
    objectives are FluxEcho's own (ScoreBridge, narrative 7.4); the bridge writes them under each player's name."""
    return {"operation:8": "MORE_OR_EQUAL", "scoreDisp:8": shown, "scoreName:8": name, "target:3": target,
            "taskID:8": "bq_standard:scoreboard", "type:8": "dummy", "unitConversion:5": 1.0, "unitSuffix:8": ""}


def any_of(q):
    """Turns a quest's tasks into alternatives: any one of them completes it."""
    q[1]["properties:10"]["betterquesting:10"]["taskLogic:8"] = "OR"
    return q


def line():
    codex = quest(
        "echo_general/codex", "§3§l航海日志",
        ECHO_LORE + "\n\n回响图鉴是我的航海日志：这个世界替我记下的每一件事都在里面。拓过的蜂、树、蝴蝶，学会的要素，"
        "杀过的怪，拓过的作物，回响过的注魔成品，用过的血宝珠。\n\n"
        "• 右键打开：「回响录」页左边分类、右边图标，鼠标停上去看名字；「卷」页是这一路的故事；"
        "「舱志」页记着城里造起来的每一舱；\n"
        "• 物品提示（NEI 里也一样）会标出「通量回响：团队已经亲手做过」，还没做过的会告诉你下一步怎么做；\n"
        "• Waila 看着一只怪，也能看到它有没有猎物印记。\n\n"
        "[note]合成：一本书、一颗末影珍珠、一块玻璃板。这里先送一本。[/note]",
        item("fluxecho:codex"),
        [checkbox()],
        [give(item("fluxecho:codex"))])
    drift = quest(
        "echo_general/drift", "§3它们也是漂来的",
        "我开始明白这片滩为什么这么奇怪。蜂、魔法、血、会自己翻页的书、天上那些别的星球……"
        "它们都不是这里自己长出来的。\n\n"
        "它们和我一样，是从别的岸漂来的。\n\n"
        "[note]回响图鉴的「卷」页：图鉴里有了第一条记录，卷二就翻开了。读完它，把这一项勾上。[/note]",
        item("fluxecho:codex"),
        [checkbox()],
        pre=["echo_general/codex"])
    tools = any_of(quest(
        "echo_general/tools", "§3拾来的小工具",
        "滩上拾来的小工具：每一台都嵌着一颗末影珍珠。把一道纹交给它，它就替我一遍遍地做下去，"
        "我只管给它电。\n\n"
        "[note]做出任意一台单方块回响机器：蜂种拓印机、要素回响仪、鲜血回响器、猎物回响器、种子拓印机、"
        "魔力回响泉……做法在各条「拾遗」线里。[/note]",
        echo_machine(0),
        [retrieval(echo_machine(m)) for m in ECHO_MACHINES],
        pre=["echo_general/drift"]))
    ten = quest(
        "echo_general/ten", "§3十道纹",
        "海里有了十道纹。每一道都是一件我亲手做过的事，它记得，我也记得。\n\n"
        "[note]团队的回响图鉴里有 10 条记录（记分项 feRec，通量回响每几秒同步一次）。[/note]",
        item("fluxecho:codex"),
        [scoreboard("feRec", "回响记录", 10)],
        pre=["echo_general/tools"])
    kinds = quest(
        "echo_general/kinds", "§3不止一种记法",
        "蜂留下的纹和血留下的纹不一样，要素的纹又是另一种写法。这片滩用好几种字母记事，"
        "我得一种一种地学。\n\n"
        "[note]团队的回响图鉴里有 4 个类别各有记录（记分项 feCat）：蜂种、树种、蝶种、要素、猎物、作物、"
        "注魔与奥术、血宝珠，哪几类都行。[/note]",
        item("fluxecho:codex"),
        [scoreboard("feCat", "有记录的类别", 4)],
        pre=["echo_general/ten"])
    forty = quest(
        "echo_general/forty", "§3四十道纹",
        "四十道纹。晶核亮得比从前久了，像是在用这些纹一点一点想起什么。\n\n"
        "[note]团队的回响图鉴里有 40 条记录（记分项 feRec）。记得越多，中枢压回响晶就越快。[/note]",
        item("fluxecho:codex"),
        [scoreboard("feRec", "回响记录", 40)],
        pre=["echo_general/kinds"])
    return write_line(
        "line/echo_general", "FluxEchoGeneral", "§3卷二 · 回响",
        "这个世界会记事：亲手做过一次的事，都在海里留下一道纹。回响图鉴把它们记下来。",
        item("fluxecho:codex"),
        [(codex, (0, 24)), (drift, (48, 24)), (tools, (96, 24)), (ten, (144, 24)), (kinds, (192, 24)),
         (forty, (240, 24))],
        [])
