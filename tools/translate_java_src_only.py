# -*- coding: utf-8 -*-
"""Translate Cyrillic in src/**/*.java comments and string literals only."""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

from deep_translator import MyMemoryTranslator

TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))
from argos_translate import translate_ru_en

ROOT = Path(__file__).resolve().parents[1]
BACKEND = "argos"
SRC = ROOT / "src"
CACHE_PATH = ROOT / "translate_java_cache.json"
CYRILLIC = re.compile(r"[А-Яа-яЁё]")
SKIP_FILES = {"FeintCreeper.java"}
MAX_CHARS = 480
# MyMemory / Google free tier: stay under ~5 req/s
API_SLEEP_SEC = 0.45
RATE_LIMIT_SLEEP_SEC = 90

translator = MyMemoryTranslator(source="ru-RU", target="en-US")
segment_cache: dict[str, str] = {}

STATIC_REPLACEMENTS = [
    (
        "// ===================== SETTINGS (меняй эти числа) =====================",
        "// ===================== SETTINGS (change these numbers) =====================",
    ),
    ("меняй эти числа", "change these numbers"),
    ("ФИКС", "FIXED"),
    ("РАСТЁТ", "GROWING"),
    ("РАСТET", "GROWING"),
    ("НАСТРОЙКИ", "SETTINGS"),
    ("[ВЫКЛЮЧЕНА, ENABLED = false]", "[DISABLED, ENABLED = false]"),
]


def has_cyrillic(text: str) -> bool:
    return bool(CYRILLIC.search(text))


def apply_static(text: str) -> str:
    for old, new in STATIC_REPLACEMENTS:
        text = text.replace(old, new)
    return text


def extract_segments(content: str) -> list[str]:
    """Return comment/string segments that contain Cyrillic."""
    segments: list[str] = []
    i = 0
    n = len(content)
    state = "code"
    while i < n:
        if state == "code":
            if content.startswith("//", i):
                state = "line_comment"
                i += 2
                continue
            if content.startswith("/*", i):
                state = "block_comment"
                i += 2
                continue
            if content[i] == '"':
                state = "string"
                i += 1
                continue
            if content[i] == "'":
                state = "char"
                i += 1
                continue
            i += 1
            continue

        if state == "line_comment":
            start = i
            while i < n and content[i] != "\n":
                i += 1
            seg = content[start:i]
            if has_cyrillic(seg):
                segments.append(seg)
            if i < n:
                i += 1
            state = "code"
            continue

        if state == "block_comment":
            end = content.find("*/", i)
            if end == -1:
                seg = content[i:]
                if has_cyrillic(seg):
                    for line in seg.split("\n"):
                        if has_cyrillic(line):
                            segments.append(line)
                break
            seg = content[i:end]
            if has_cyrillic(seg):
                for line in seg.split("\n"):
                    if has_cyrillic(line):
                        segments.append(line)
            i = end + 2
            state = "code"
            continue

        if state == "string":
            start = i
            while i < n:
                if content[i] == "\\":
                    i += 2
                    continue
                if content[i] == '"':
                    break
                i += 1
            seg = content[start:i]
            if has_cyrillic(seg):
                segments.append(seg)
            if i < n:
                i += 1
            state = "code"
            continue

        if state == "char":
            start = i
            while i < n:
                if content[i] == "\\":
                    i += 2
                    continue
                if content[i] == "'":
                    break
                i += 1
            seg = content[start:i]
            if has_cyrillic(seg):
                segments.append(seg)
            if i < n:
                i += 1
            state = "code"
            continue

    return segments


def load_cache() -> None:
    global segment_cache
    if not CACHE_PATH.exists():
        segment_cache = {}
        return
    try:
        segment_cache = json.loads(CACHE_PATH.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        segment_cache = {}


def save_cache() -> None:
    CACHE_PATH.write_text(
        json.dumps(segment_cache, ensure_ascii=False, indent=0) + "\n",
        encoding="utf-8",
    )


def translate_chunk(chunk: str, *, api: bool = True) -> str:
    if chunk in segment_cache:
        return segment_cache[chunk]
    if not api:
        return chunk
    chunk = apply_static(chunk.strip())
    if not chunk or not has_cyrillic(chunk):
        segment_cache[chunk] = chunk
        return chunk
    if len(chunk) > MAX_CHARS:
        parts: list[str] = []
        rest = chunk
        while rest:
            if len(rest) <= MAX_CHARS:
                parts.append(_api_translate(rest))
                break
            cut = rest.rfind(" ", 0, MAX_CHARS)
            if cut < MAX_CHARS // 2:
                cut = MAX_CHARS
            parts.append(_api_translate(rest[:cut]))
            rest = rest[cut:]
        out = apply_static("".join(parts))
        segment_cache[chunk] = out
        return out
    out = apply_static(_api_translate(chunk))
    segment_cache[chunk] = out
    return out


def _api_translate(text: str) -> str:
    text = text.strip()
    if not text or not has_cyrillic(text):
        return text
    if BACKEND == "argos":
        try:
            out = translate_ru_en(text)
            if out:
                return out
        except Exception as exc:
            print(f"  argos failed: {exc}", file=sys.stderr, flush=True)
        return text
    for attempt in range(12):
        try:
            time.sleep(API_SLEEP_SEC)
            out = translator.translate(text)
            if out:
                return out
        except Exception as exc:
            msg = str(exc).lower()
            print(f"  retry {attempt + 1}: {exc}", file=sys.stderr, flush=True)
            if "too many requests" in msg or "429" in msg:
                time.sleep(RATE_LIMIT_SLEEP_SEC)
            else:
                time.sleep(1.2 * (attempt + 1))
    return text


def mapping_for_segments(
    unique: set[str], api_budget: list[int | None], *, api: bool = True
) -> dict[str, str]:
    """Build replace map; api_budget[0] is remaining API calls (None = unlimited)."""
    mapping: dict[str, str] = {}
    items = sorted(unique, key=len)
    for original in items:
        if api_budget[0] is not None and api_budget[0] <= 0:
            print("  API budget exhausted; re-run later to continue.", flush=True)
            break
        static_only = apply_static(original)
        if not has_cyrillic(static_only):
            mapping[original] = static_only
            continue
        before = len(segment_cache)
        lead = original[: len(original) - len(original.lstrip())]
        trail = original[len(original.rstrip()) :]
        core = original.strip()
        translated = translate_chunk(core, api=api)
        if api_budget[0] is not None and len(segment_cache) > before:
            api_budget[0] -= 1
            save_cache()
        elif len(segment_cache) > before:
            save_cache()
        mapping[original] = lead + translated + trail
    return mapping


def apply_map(content: str, mapping: dict[str, str]) -> str:
    if not mapping:
        return content
    # longest keys first to avoid partial overlaps
    for original in sorted(mapping.keys(), key=len, reverse=True):
        if original in content:
            content = content.replace(original, mapping[original])
    return apply_static(content)


def process_java(content: str, mapping: dict[str, str]) -> str:
    return apply_map(content, mapping)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--max-api-calls",
        type=int,
        default=0,
        help="Stop after this many new API translations (0 = no limit).",
    )
    parser.add_argument(
        "--cache-only",
        action="store_true",
        help="Apply translate_java_cache.json only; no API calls.",
    )
    parser.add_argument(
        "--backend",
        choices=("argos", "mymemory"),
        default="argos",
        help="Translation engine (default: offline argos).",
    )
    args = parser.parse_args()
    global BACKEND
    BACKEND = args.backend
    api_budget: list[int | None] = [
        args.max_api_calls if args.max_api_calls > 0 else None
    ]

    load_cache()
    print(f"cache entries: {len(segment_cache)}", flush=True)
    use_api = not args.cache_only

    if use_api:
        ru_lang = ROOT / "src/main/resources/assets/minecraft/lang/ru_ru.json"
        if ru_lang.exists():
            ru_lang.write_text("{}\n", encoding="utf-8")
            print("emptied minecraft/lang/ru_ru.json", flush=True)

    files = sorted(SRC.rglob("*.java"))
    todo = []
    for path in files:
        if path.name in SKIP_FILES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            print(f"skip (encoding): {path}", file=sys.stderr)
            continue
        if has_cyrillic(text):
            todo.append(path)

    print(f"files with Cyrillic: {len(todo)}", flush=True)
    updated = 0
    for idx, path in enumerate(todo, 1):
        if api_budget[0] is not None and api_budget[0] <= 0:
            break
        raw = path.read_text(encoding="utf-8")
        segs = set(extract_segments(raw))
        file_map = mapping_for_segments(segs, api_budget, api=use_api)
        new_text = process_java(raw, file_map)
        if new_text != raw:
            path.write_text(new_text, encoding="utf-8", newline="\n")
            updated += 1
            print(f"[{idx}/{len(todo)}] {path.relative_to(ROOT)}", flush=True)

    save_cache()

    remaining = 0
    for p in SRC.rglob("*.java"):
        if p.name in SKIP_FILES:
            continue
        try:
            if has_cyrillic(p.read_text(encoding="utf-8")):
                remaining += 1
        except UnicodeDecodeError:
            pass

    print(
        f"done. updated {updated} files this run. "
        f"{remaining} files still contain Cyrillic. cache: {len(segment_cache)} entries.",
        flush=True,
    )


if __name__ == "__main__":
    main()
