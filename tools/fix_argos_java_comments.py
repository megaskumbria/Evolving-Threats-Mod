# -*- coding: utf-8 -*-
"""Fix common Argos mistranslations in src/**/*.java comments."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"

# Longest phrases first (applied in order)
REPLACEMENTS = [
    ("DANGEROUS START RATIO", "DANGEROUS_START_RATIO"),
    ("ThreatTable.EXCALIBUR DAY", "ThreatTable.EXCALIBUR_DAY"),
    ("Turn on the mechanics. False - such zombies do not appear, the team does not save.",
     "Enable this mechanic. false — no such zombies spawn; commands cannot spawn them either."),
    ("Turn on the mechanics. false — no such zombies spawn, commands cannot spawn them either.",
     "Enable this mechanic. false — no such zombies spawn; commands cannot spawn them either."),
    ("The team doesn't take chances.", "Commands ignore spawn chance."),
    ("The team does not check the day and the chance.", "Commands ignore day and spawn chance."),
    ("The team does not take chances.", "Commands ignore spawn chance."),
    ("0 = team only.", "0 = commands only."),
    ("= team only.", "= commands only."),
    ("for murder", "for killing"),
    ("Experience for murder", "Experience reward"),
    ("with prey", "with looting"),
    ("with prey.", "with looting."),
    ("even with prey", "even with looting"),
    ("fashion potions", "mod potion buffs"),
    ("in this fashion", "in this mod"),
    ("zombie resident", "zombie villager"),
    ("does not give up this chance", "skip this roll"),
    ("do not give up this chance", "skip this roll"),
    ("this chance does not give up", "skip this roll"),
    ("Drowning, cadaver,", "Drowned, husk,"),
    ("cadaver", "husk"),
    ("Name overhead and text on the tag.", "Display name and text on the name tag."),
    ("How many tags always fall.", "How many name tags always drop."),
    ("don't drop the tag", "do not drop the name tag"),
    ("End of zombies.", "Called at end of zombie spawn."),
    ("End of the appearance of the zombie.", "Called at end of zombie spawn."),
    ("End of the appearance of", "Called at end of spawn for"),
    ("End of tick of the target.", "End-of-tick for the target."),
    ("End of tick target.", "End-of-tick for the target."),
    ("End of tick", "End-of-tick"),
    (", tics.", ", ticks."),
    (" tics.", " ticks."),
    ("Fuse, tics", "Fuse, ticks"),
    # Do not replace "recoil" globally — it corrupts Entity.discard().
    ("discard", "knockback"),
    ("ordinary Return", "vanilla Knockback"),
    ("Return II", "Knockback II"),
    ("neserite", "netherite"),
    ("Tight 20", "Sharpness 20"),
    ("Charm damage", "Enchantment damage"),
    ("Acute I", "Sharpness I"),
    ("overseer", "overseer"),  # keep
    ("does not save", "does not spawn"),
    ("Blocks don't break", "Does not break blocks"),
    ("Drops ordinary", "drops lit"),
    ("Armour", "Armor"),
    ("armour", "armor"),
    ("* ** ", "/** "),
]

CYRILLIC = re.compile(r"[\u0400-\u04FF]")


def fix_discard_corruption(content: str) -> str:
    """Argos/recoil fixes sometimes turn Entity.discard() into knockback()."""
    return (
        content.replace("hook.knockback()", "hook.discard()")
        .replace("horse.knockback()", "horse.discard()")
        .replace("passenger.knockback()", "passenger.discard()")
    )


def fix_broken_javadoc(content: str) -> str:
    """Turn malformed `* ** text */` lines into proper one-line javadocs."""
    lines = content.split("\n")
    out: list[str] = []
    for line in lines:
        m = re.match(r"^(\t)\* \*\* (.+) \*/$", line)
        if m:
            out.append(f"{m.group(1)}/** {m.group(2)} */")
            continue
        out.append(line)
    return "\n".join(out)


def main() -> None:
    updated = 0
    for path in sorted(SRC.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        new = fix_broken_javadoc(text)
        for old, repl in REPLACEMENTS:
            new = new.replace(old, repl)
        new = fix_discard_corruption(new)
        if new != text:
            path.write_text(new, encoding="utf-8", newline="\n")
            updated += 1
            print(path.relative_to(ROOT))
    remaining = sum(
        1
        for p in SRC.rglob("*.java")
        if CYRILLIC.search(p.read_text(encoding="utf-8"))
    )
    print(f"updated {updated} files; {remaining} still contain Cyrillic")


if __name__ == "__main__":
    main()
