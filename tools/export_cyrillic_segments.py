# -*- coding: utf-8 -*-
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
CY = re.compile(r"[\u0400-\u04FF]")
SKIP = {"FeintCreeper.java"}


def extract(content: str) -> list[str]:
    segments: list[str] = []
    i = 0
    n = len(content)
    state = "code"
    while i < n:
        if state == "code":
            if content.startswith("//", i):
                state = "lc"
                i += 2
                continue
            if content.startswith("/*", i):
                state = "bc"
                i += 2
                continue
            if content[i] == '"':
                state = "str"
                i += 1
                continue
            if content[i] == "'":
                state = "ch"
                i += 1
                continue
            i += 1
            continue
        if state == "lc":
            s = i
            while i < n and content[i] != "\n":
                i += 1
            seg = content[s:i]
            if CY.search(seg):
                segments.append(seg)
            if i < n:
                i += 1
            state = "code"
            continue
        if state == "bc":
            e = content.find("*/", i)
            if e == -1:
                seg = content[i:]
                i = n
            else:
                seg = content[i:e]
                i = e + 2
            if CY.search(seg):
                for line in seg.split("\n"):
                    if CY.search(line):
                        segments.append(line)
            if e == -1:
                break
            state = "code"
            continue
        if state in ("str", "ch"):
            q = '"' if state == "str" else "'"
            s = i
            while i < n:
                if content[i] == "\\":
                    i += 2
                    continue
                if content[i] == q:
                    break
                i += 1
            seg = content[s:i]
            if CY.search(seg):
                segments.append(seg)
            if i < n:
                i += 1
            state = "code"
            continue
    return segments


def main() -> None:
    unique: set[str] = set()
    for p in SRC.rglob("*.java"):
        if p.name in SKIP:
            continue
        text = p.read_text(encoding="utf-8")
        if CY.search(text):
            unique.update(extract(text))
    out = ROOT / "tools" / "cyrillic_segments.json"
    out.write_text(json.dumps(sorted(unique, key=len), ensure_ascii=False, indent=0), encoding="utf-8")
    print(len(unique), "segments ->", out)


if __name__ == "__main__":
    main()
