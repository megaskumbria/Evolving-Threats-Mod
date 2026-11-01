# -*- coding: utf-8 -*-
"""One-off: translate Cyrillic text to English and rename Russian file names."""
from __future__ import annotations

import re
import sys
import time
from pathlib import Path

from deep_translator import MyMemoryTranslator

ROOT = Path(__file__).resolve().parents[1]
CYRILLIC = re.compile(r"[А-Яа-яЁё]")
SKIP_DIRS = {".git", "build", ".gradle", "run", "out", "classes", "node_modules", ".cursor"}
SKIP_NAMES = {"translate_log.txt", "translate_errors.txt", "translate_project_to_english.py"}
TEXT_SUFFIXES = {
    ".java",
    ".md",
    ".mdc",
    ".txt",
    ".gradle",
    ".py",
    ".json",
    ".properties",
}

# Old path fragment -> new (after renames)
PATH_SUBS = [
    ("идея.txt", "idea.txt"),
    ("mobs/common/шкала-угрозы.txt", "mobs/common/threat-scale.txt"),
    ("mobs/common/чары-на-оружии.txt", "mobs/common/enchantments-on-gear.txt"),
    ("mobs/common/опыт-за-убийство.txt", "mobs/common/kill-experience.txt"),
    ("mobs/common/дальность-взгляда.txt", "mobs/common/sight-range.txt"),
    ("mobs/common/топор-сбивает-щит.txt", "mobs/common/axe-disables-shield.txt"),
    ("mobs/common/осада.txt", "mobs/common/siege.txt"),
    ("mobs/common/бастион.txt", "mobs/common/bastion.txt"),
    ("mobs/common/зеркало.txt", "mobs/common/mirror.txt"),
    ("mobs/common/случайное-здоровье.txt", "mobs/common/random-health.txt"),
    ("mobs/common/рейдовый-якорь.txt", "mobs/common/raid-anchor.txt"),
    ("mobs/common/парящий-инструмент.txt", "mobs/common/floating-tool.txt"),
    ("mobs/common/полный-комплект-брони.txt", "mobs/common/full-armor-set.txt"),
    ("mobs/common/бафы-зелий.txt", "mobs/common/potion-buffs.txt"),
    ("mobs/common/два-оружия.txt", "mobs/common/dual-wield.txt"),
    ("mobs/common/крашеная-кожаная-броня.txt", "mobs/common/dyed-leather-armor.txt"),
    ("mobs/common/тотем-бессмертия.txt", "mobs/common/totem-of-undying.txt"),
    ("mobs/common/щиты.txt", "mobs/common/shields.txt"),
    ("шкала-угрозы.txt", "threat-scale.txt"),
    ("чары-на-оружии.txt", "enchantments-on-gear.txt"),
    ("опыт-за-убийство.txt", "kill-experience.txt"),
    ("дальность-взгляда.txt", "sight-range.txt"),
    ("топор-сбивает-щит.txt", "axe-disables-shield.txt"),
    ("осада.txt", "siege.txt"),
    ("зеркало.txt", "mirror.txt"),
    ("случайное-здоровье.txt", "random-health.txt"),
    ("рейдовый-якорь.txt", "raid-anchor.txt"),
    ("парящий-инструмент.txt", "floating-tool.txt"),
    ("полный-комплект-брони.txt", "full-armor-set.txt"),
    ("бафы-зелий.txt", "potion-buffs.txt"),
    ("replay-mod-клавиши.txt", "replay-mod-keys.txt"),
]

RENAME_MAP = {
    "идея.txt": "idea.txt",
    "replay-mod-клавиши.txt": "replay-mod-keys.txt",
    "mobs/common/шкала-угрозы.txt": "mobs/common/threat-scale.txt",
    "mobs/common/чары-на-оружии.txt": "mobs/common/enchantments-on-gear.txt",
    "mobs/common/опыт-за-убийство.txt": "mobs/common/kill-experience.txt",
    "mobs/common/дальность-взгляда.txt": "mobs/common/sight-range.txt",
    "mobs/common/топор-сбивает-щит.txt": "mobs/common/axe-disables-shield.txt",
    "mobs/common/осада.txt": "mobs/common/siege.txt",
    "mobs/common/бастион.txt": "mobs/common/bastion.txt",
    "mobs/common/зеркало.txt": "mobs/common/mirror.txt",
    "mobs/common/случайное-здоровье.txt": "mobs/common/random-health.txt",
    "mobs/common/рейдовый-якорь.txt": "mobs/common/raid-anchor.txt",
    "mobs/common/парящий-инструмент.txt": "mobs/common/floating-tool.txt",
    "mobs/common/полный-комплект-брони.txt": "mobs/common/full-armor-set.txt",
    "mobs/common/бафы-зелий.txt": "mobs/common/potion-buffs.txt",
    "mobs/common/два-оружия.txt": "mobs/common/dual-wield.txt",
    "mobs/common/крашеная-кожаная-броня.txt": "mobs/common/dyed-leather-armor.txt",
    "mobs/common/тотем-бессмертия.txt": "mobs/common/totem-of-undying.txt",
    "mobs/common/щиты.txt": "mobs/common/shields.txt",
}

TERM_SUBS = [
    ("ФИКС", "FIXED"),
    ("РАСТЁТ", "GROWING"),
    ("РАСТET", "GROWING"),
    ("НАСТРОЙКИ", "SETTINGS"),
]

MAX_CHARS = 480  # MyMemory limit is 500
translator = MyMemoryTranslator(source="ru-RU", target="en-US")


def has_cyrillic(text: str) -> bool:
    return bool(CYRILLIC.search(text))


def _translate_chunk(chunk: str) -> str:
    chunk = chunk.strip()
    if not chunk or not has_cyrillic(chunk):
        return chunk
    if len(chunk) > MAX_CHARS:
        mid = chunk.rfind(" ", 0, MAX_CHARS)
        if mid < MAX_CHARS // 2:
            mid = MAX_CHARS
        return _translate_chunk(chunk[:mid]) + chunk[mid:]
    for attempt in range(6):
        try:
            time.sleep(0.35)
            out = translator.translate(chunk)
            if out:
                return out
        except Exception as exc:
            print(f"  retry {attempt + 1} ({len(chunk)} chars): {exc}", file=sys.stderr)
            time.sleep(1.2 * (attempt + 1))
    return chunk


def translate_once(text: str) -> str:
    if not text or not has_cyrillic(text):
        return text
    lines = text.split("\n")
    return "\n".join(_translate_chunk(line) if has_cyrillic(line) else line for line in lines)


def translate_line(line: str) -> str:
    if not has_cyrillic(line):
        return line
    # Preserve leading/trailing whitespace on the line
    lead = len(line) - len(line.lstrip(" \t"))
    trail = len(line) - len(line.rstrip(" \r\n"))
    core = line.strip()
    if "//" in core and not core.strip().startswith("//"):
        idx = core.index("//")
        before = core[:idx]
        comment = core[idx + 2 :]
        if has_cyrillic(comment):
            comment = translate_once(comment)
        return line[:lead] + before + "//" + comment
    return line[:lead] + translate_once(core) + line[len(core) + lead :]


def apply_subs(text: str) -> str:
    for old, new in PATH_SUBS:
        text = text.replace(old, new)
    for old, new in TERM_SUBS:
        text = text.replace(old, new)
    return text


def translate_prose_chunks(text: str) -> str:
    """Translate large .txt/.md files line-by-line (safe for API limits)."""
    if not has_cyrillic(text):
        return text
    return "\n".join(
        translate_line(line) if has_cyrillic(line) else line for line in text.splitlines()
    )


def process_file(path: Path) -> bool:
    try:
        raw = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return False
    if not has_cyrillic(raw):
        return False
    if path.suffix.lower() in {".txt", ".md"} and len(raw) > 1500:
        text = apply_subs(translate_prose_chunks(raw))
    else:
        lines = raw.splitlines(keepends=True)
        out_lines = []
        for line in lines:
            newline = ""
            if line.endswith("\r\n"):
                newline = "\r\n"
                body = line[:-2]
            elif line.endswith("\n"):
                newline = "\n"
                body = line[:-1]
            else:
                body = line
            new_body = translate_line(body) if has_cyrillic(body) else body
            out_lines.append(new_body + newline)
        text = apply_subs("".join(out_lines))
    if text != raw:
        path.write_text(text, encoding="utf-8", newline="\n")
        return True
    return False


def iter_files() -> list[Path]:
    files: list[Path] = []
    for path in ROOT.rglob("*"):
        if path.is_dir():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        if path.name in SKIP_NAMES or path.name == Path(__file__).name:
            continue
        files.append(path)
    return sorted(files)


def rename_files() -> None:
    for old_rel, new_rel in sorted(RENAME_MAP.items(), key=lambda x: -len(x[0])):
        old = ROOT / old_rel.replace("/", "\\") if "\\" in str(ROOT) else ROOT / Path(old_rel)
        new = ROOT / new_rel.replace("/", "\\") if "\\" in str(ROOT) else ROOT / Path(new_rel)
        if old.exists() and not new.exists():
            new.parent.mkdir(parents=True, exist_ok=True)
            old.rename(new)
            print(f"rename: {old_rel} -> {new_rel}")


def main() -> None:
    ru_lang = ROOT / "src/main/resources/assets/evolving-threats/lang/ru_ru.json"
    if ru_lang.exists():
        ru_lang.unlink()
        print("removed ru_ru.json")

    # Path subs in all text files (including those without cyrillic)
    for path in iter_files():
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        subbed = apply_subs(text)
        if subbed != text:
            path.write_text(subbed, encoding="utf-8", newline="\n")

    files = []
    for p in iter_files():
        try:
            text = p.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if has_cyrillic(text):
            files.append(p)
    print(f"translating {len(files)} files...")
    done = 0
    for i, path in enumerate(files, 1):
        rel = path.relative_to(ROOT)
        if process_file(path):
            done += 1
            print(f"[{i}/{len(files)}] {rel}")
    print(f"finished. updated {done} files.")


if __name__ == "__main__":
    main()
