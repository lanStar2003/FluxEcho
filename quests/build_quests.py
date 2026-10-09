"""
Writes FluxEcho's quest lines in GTNH's BetterQuesting layout (config/betterquesting/DefaultQuests).
Run from the repository root: python quests/build_quests.py
Output, shipped inside the jar:
  src/main/resources/assets/fluxecho/quests/DefaultQuests/{QuestLines,Quests}/<line>/...
  src/main/resources/assets/fluxecho/quests/index.json
At startup FluxEcho's QuestInstaller copies each line whose mods are loaded into the instance's DefaultQuests and
adds the line to QuestLinesOrder.txt (/bq_admin default load only loads the lines listed there); when a server has
started, QuestInjector puts the same lines straight into that world's quest database, so nobody has to run the
default load. Other quest lines and the rest of the order file are never touched, so several mods can ship quest
lines side by side.

Quest ids are derived from fixed keys, so running it again keeps the same ids (and the players' progress). The lazy
AE and shard collector lines came from FluxDepths 0.1.x; their keys, folder names and file names are unchanged, so
the files players copied in by hand are overwritten in place.
"""
import base64
import json
import os
import shutil
import struct
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, os.pardir, "src", "main", "resources", "assets", "fluxecho", "quests")
OUT = os.path.join(ASSETS, "DefaultQuests")
INDEX = os.path.join(ASSETS, "index.json")
NS = uuid.UUID("6f1c2d6a-3a0b-4b51-9a57-7f1d2e9c4b10")
FLUXDEPTHS_FIRST_ID = 24520  # FluxDepths' shard_collectors.firstMachineId
FIRST_ID = 24530  # FluxEcho's general.firstMachineId


def ids(key):
    u = uuid.uuid5(NS, key)
    hi, lo = struct.unpack(">qq", u.bytes)
    return hi, lo, base64.urlsafe_b64encode(u.bytes).decode()


def item(i, dmg=0, count=1, ore=""):
    return {"Count:3": count, "Damage:2": dmg, "OreDict:8": ore, "id:8": i}


def numbered(entries):
    return {f"{n}:10": e for n, e in enumerate(entries)}


def stacks(i, dmg, count):
    out = []
    while count > 0:
        out.append(item(i, dmg, min(64, count)))
        count -= 64
    return out


AE = "appliedenergistics2:"
PART = AE + "item.ItemMultiPart"
MAT = AE + "item.ItemMultiMaterial"


def quest(key, name, desc, icon, tasks, rewards=(), pre=(), main=True):
    hi, lo, b64 = ids(key)
    t = []
    for n, task in enumerate(tasks):
        task = dict(task)
        task["index:3"] = n
        t.append(task)
    r = []
    for n, rew in enumerate(rewards):
        rew = dict(rew)
        rew["index:3"] = n
        r.append(rew)
    q = {}
    if pre:
        q["preRequisites:9"] = numbered([{"questIDHigh:4": ids(p)[0], "questIDLow:4": ids(p)[1]} for p in pre])
    q["properties:10"] = {"betterquesting:10": {
        "autoClaim:1": 0, "desc:8": desc, "globalShare:1": 1, "icon:10": icon, "isGlobal:1": 0,
        "isMain:1": 1 if main else 0, "isSilent:1": 0, "lockedProgress:1": 0, "name:8": name,
        "partySingleReward:1": 0, "questLogic:8": "AND", "repeatTime:3": -1, "repeat_relative:1": 1,
        "simultaneous:1": 0, "snd_complete:8": "random.levelup", "snd_update:8": "random.levelup",
        "taskLogic:8": "AND", "visibility:8": "NORMAL"}}
    q["questIDHigh:4"] = hi
    q["questIDLow:4"] = lo
    q["rewards:9"] = numbered(r)
    q["tasks:9"] = numbered(t)
    return key, q, b64


def checkbox():
    return {"taskID:8": "bq_standard:checkbox"}


def retrieval(*items, ignore_nbt=False):
    return {"autoConsume:1": 0, "consume:1": 0, "groupDetect:1": 0, "ignoreNBT:1": 1 if ignore_nbt else 0,
            "partialMatch:1": 1, "requiredItems:9": numbered(list(items)), "taskID:8": "bq_standard:retrieval"}


def give(*items):
    flat = []
    for i in items:
        flat.extend(i if isinstance(i, list) else [i])
    return {"ignoreDisabled:1": 0, "rewardID:8": "bq_standard:item", "rewards:9": numbered(flat)}


def dump(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def write_line(key, slug, name, desc, icon, placed, requires):
    """Writes one quest line; returns its index entry. requires: mod ids that must be loaded to install it."""
    hi, lo, b64 = ids(key)
    line_dir = f"{slug}-{b64}"
    qdir = os.path.join(OUT, "Quests", line_dir)
    ldir = os.path.join(OUT, "QuestLines", line_dir)
    os.makedirs(qdir, exist_ok=True)
    os.makedirs(ldir, exist_ok=True)
    files = [f"QuestLines/{line_dir}/QuestLine.json"]
    dump(os.path.join(ldir, "QuestLine.json"), {
        "properties:10": {"betterquesting:10": {"bg_image:8": "", "bg_size:3": 256, "desc:8": desc,
                                                "icon:10": icon, "name:8": name, "visibility:8": "NORMAL"}},
        "questLineIDHigh:4": hi, "questLineIDLow:4": lo})
    for (qkey, q, qb64), (x, y) in placed:
        stem = "".join(c for c in qkey.split("/")[-1].title() if c.isalnum())[:16]
        fname = f"{stem}-{qb64}.json"
        dump(os.path.join(qdir, fname), q)
        dump(os.path.join(ldir, fname), {"sizeX:3": 24, "sizeY:3": 24, "x:3": x, "y:3": y,
                                          "questIDHigh:4": q["questIDHigh:4"], "questIDLow:4": q["questIDLow:4"]})
        files += [f"Quests/{line_dir}/{fname}", f"QuestLines/{line_dir}/{fname}"]
    return {"order": f"{b64}: {name}", "requires": list(requires),
            "dirs": [f"QuestLines/{line_dir}", f"Quests/{line_dir}"], "files": sorted(files)}


# ---------------------------------------------------------------- lazy AE

def lazy_ae():
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


# ---------------------------------------------------------------- shard collectors

def machine(tier):
    return item("gregtech:gt.blockmachines", FLUXDEPTHS_FIRST_ID + tier)


def shards():
    lore = quest(
        "shards/depths", "§3§l通量深层",
        "FluxLite 的网络把电送进「通量层」——现实之下、贯穿所有世界的一层能量之海。\n\n"
        "通量层再往下，是「通量深层」。那里沉着世界诞生时留下的碎片：每个世界生成时，地下的每一条矿脉，都是深层里一块碎片的投影。"
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
        "line/shards", "FluxDepthsShards", "§3通量深层 · 碎片采集器",
        "拓下一条矿脉的印记，在家里慢慢回响出它的矿石。从蒸汽时代一路到 IV，直到原版的虚空采矿机接手。",
        item("fluxdepths:imprint"), placed, ["fluxdepths"])


# ---------------------------------------------------------------- FluxEcho

def echo_machine(offset):
    return item("gregtech:gt.blockmachines", FIRST_ID + offset)


ECHO_LORE = (
    "FluxLite 的网络把电送进「通量层」，FluxDepths 从它下面的深层里捞出矿脉的碎片。"
    "可通量层不只是一片能量之海：流过它的一切，它都记得。\n\n"
    "你亲手做过一次的事，都在通量层里留下了回响。回响机器（每一台都带一颗末影珍珠，那是通往通量层的那一环）"
    "把这些回响重新放大：第一次必须亲手做，之后交给机器。\n\n"
    "[note]规则只有一条：机器只认真东西。蜂要从真蜂身上拓，要素要从真的要素里学，血要从你亲手做出的宝珠里来。[/note]")


def echo_bees():
    lore = quest(
        "echo_bees/lore", "§3§l通量回响 · 养蜂",
        ECHO_LORE + "\n\n养蜂这一边：你配出过的每一种蜂都在通量层里留着回响。"
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
        "line/echo_bees", "FluxEchoBees", "§3通量回响 · 养蜂",
        "拓下一只真蜂的全部基因，以后要多少原始公主就培育多少；拓下单个性状的基因样本，想要哪些性状就组合哪些。",
        item("fluxecho:bee_imprint"), placed, ["Forestry"])


TC = "Thaumcraft:"


def echo_thaum():
    lore = quest(
        "echo_thaum/lore", "§5§l通量回响 · 神秘",
        ECHO_LORE + "\n\n神秘这一边：你亲手炼出过的每一种要素、捡到过的每一种魔力碎片，都在通量层里留着回响。"
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
        "line/echo_thaum", "FluxEchoThaum", "§5通量回响 · 神秘",
        "每种要素亲手炼一次，以后用电现合成；初始要素认识一次，以后用电充法杖。注魔不再炼要素，法杖不再找节点。",
        item(TC + "ItemEssence", 1), placed, ["Thaumcraft"])


BM = "AWWayofTime:"


def echo_blood():
    lore = quest(
        "echo_blood/lore", "§4§l通量回响 · 血魔法",
        ECHO_LORE + "\n\n血魔法这一边：你在祭坛里流过的每一滴血都在通量层里留着回响，"
        "宝珠就是你流过血的凭证。宝珠做出来以后，就不用再拿小刀割自己了。",
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
        "line/echo_blood", "FluxEchoBlood", "§4通量回响 · 血魔法",
        "第一颗宝珠亲手割出来，之后用电和一点肉产 LP：送进祭坛，或者直接补灵魂网络。",
        item(BM + "weakBloodOrb"), placed, ["AWWayofTime"])


def echo_prey():
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


def echo_crops():
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


def echo_mana():
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


def echo_general():
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


if __name__ == "__main__":
    # GTNH names a quest line after its id as url-safe base64 of the two longs; check against one of its own
    probe = struct.pack(">qq", 3630150074513574271, -7072631871726141045)
    assert base64.urlsafe_b64encode(probe).decode() == "MmDiQmi9SX-d2PbI-qhBiw==", "id format changed"
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    lines = [lazy_ae(), shards(), echo_bees(), echo_thaum(), echo_blood(), echo_prey(), echo_crops(), echo_mana(),
             echo_general()]
    dump(INDEX, {"lines": lines})
    print("\n".join(entry["order"] for entry in lines))
