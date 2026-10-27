"""
Writes FluxEcho's quest lines in GTNH's BetterQuesting layout (config/betterquesting/DefaultQuests).
Run from the repository root: python quests/build_quests.py
Each line is a module of quests/lines (its line() returns the line's index entry); the helpers they share are in
quests/questkit.py. This file only puts the lines together, in the order of LINES.
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
import os
import shutil
import struct

from lines import (echo_blood, echo_bees, echo_crops, echo_general, echo_mana, echo_nexus, echo_prey, echo_thaum,
                   lazy_ae, shards)
from questkit import INDEX, OUT, dump

# The lines in the order of index.json, which is the order QuestInstaller adds them to QuestLinesOrder.txt. A new line
# is a new module in quests/lines and one more entry here (at the end, so the lines before it keep their place).
LINES = [lazy_ae, shards, echo_bees, echo_thaum, echo_blood, echo_prey, echo_crops, echo_mana, echo_general, echo_nexus]


if __name__ == "__main__":
    # GTNH names a quest line after its id as url-safe base64 of the two longs; check against one of its own
    probe = struct.pack(">qq", 3630150074513574271, -7072631871726141045)
    assert base64.urlsafe_b64encode(probe).decode() == "MmDiQmi9SX-d2PbI-qhBiw==", "id format changed"
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    lines = [m.line() for m in LINES]
    dump(INDEX, {"lines": lines})
    print("\n".join(entry["order"] for entry in lines))
