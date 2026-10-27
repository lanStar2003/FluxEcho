"""
The helpers every FluxEcho quest line is written with (quests/lines/*.py), and where the lines go. Quest and line ids
are uuid5 of fixed keys (ids), so a key never changes once shipped: the players' progress hangs on it.
"""
import base64
import json
import os
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


def echo_machine(offset):
    return item("gregtech:gt.blockmachines", FIRST_ID + offset)
