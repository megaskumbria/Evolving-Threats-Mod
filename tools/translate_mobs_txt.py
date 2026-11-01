# -*- coding: utf-8 -*-
"""Translate Cyrillic lines in mobs/**/*.txt using offline Argos."""
from __future__ import annotations

import re
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))
from argos_translate import translate_ru_en

ROOT = Path(__file__).resolve().parents[1]
MOBS = ROOT / "mobs"
CYRILLIC = re.compile(r"[А-Яа-яЁё]")


def translate_line(line: str) -> str:
    if not CYRILLIC.search(line):
        return line
    body = line.rstrip("\r\n")
    nl = "\n" if line.endswith("\n") else ""
    if not CYRILLIC.search(body):
        return line
    try:
        return translate_ru_en(body) + nl
    except Exception as exc:
        print(f"  skip line ({exc}): {body[:60]}...", file=sys.stderr)
        return line


def translate_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if not CYRILLIC.search(text):
        return False
    lines = text.splitlines(keepends=True)
    if not lines and text:
        lines = [text]
    new_lines = [translate_line(ln) for ln in lines]
    new_text = "".join(new_lines)
    if new_text != text:
        path.write_text(new_text, encoding="utf-8", newline="\n")
        return True
    return False


def main() -> None:
    files = sorted(MOBS.rglob("*.txt"))
    done = 0
    for path in files:
        if translate_file(path):
            done += 1
            print(path.relative_to(ROOT), flush=True)
    left = sum(
        1 for p in files if CYRILLIC.search(p.read_text(encoding="utf-8"))
    )
    print(f"updated {done} files. {left} files still contain Cyrillic.", flush=True)


if __name__ == "__main__":
    main()
