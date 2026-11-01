# One-off: translate remaining Cyrillic in mobs/**/*.txt
import re
import time
from pathlib import Path

from deep_translator import GoogleTranslator

ROOT = Path(__file__).parent / "mobs"
tr = GoogleTranslator(source="ru", target="en")

HEADER_MAP = {
    "СУТЬ": "SUMMARY",
    "КАК РАБОТАЕТ": "HOW IT WORKS",
    "КАК ДЕЙСТВУЕТ": "HOW IT ACTS",
    "ЧТО ПРОИСХОДИТ": "WHAT HAPPENS",
    "КОМАНДА": "COMMAND",
    "КОМАНДЫ": "COMMANDS",
    "КОГО НЕТ": "WHO IS EXCLUDED",
    "КТО": "WHO",
    "ТЕСТ": "TEST",
    "ТЕСТ В ИГРЕ": "IN-GAME TEST",
    "НАСТРОЙКИ": "SETTINGS",
    "ЧТО МЕНЯТЬ": "WHAT TO TUNE",
    "ПРОВЕРКА В ИГРЕ": "IN-GAME TEST",
    "КАК ВЫГЛЯДИТ": "HOW IT LOOKS",
    "КОГДА РУБИТ": "WHEN IT CHOPS",
    "ЧТО ЛОМАЕТ": "WHAT IT BREAKS",
    "СКОРОСТЬ": "SPEED",
    "ЭТАПЫ": "PHASES",
    "ЗАПРЕТЫ": "RESTRICTIONS",
    "ВОЛНЫ": "WAVES",
    "ЛУТ": "LOOT",
    "УДАР": "HIT",
    "ГДЕ ПОЯВЛЯЕТСЯ": "WHERE IT SPAWNS",
    "ЛОШАДЬ": "HORSE",
    "СПОСОБНОСТИ": "ABILITIES",
    "ЕСЛИ ВЫШЛО «ГРЯЗНО»": 'IF COLORS LOOK "MUDDY"',
    "QUIZ": "TEST",
}


def fix_headers(line: str) -> str:
    s = line.strip()
    for ru, en in HEADER_MAP.items():
        if s == ru or s.startswith(ru + " "):
            return line.replace(ru, en, 1)
    return line


def translate_text(text: str) -> str:
    if not re.search(r"[\u0400-\u04FF]", text):
        return text
    # Split into chunks under ~4500 chars on paragraph boundaries
    parts = []
    buf = []
    size = 0
    for para in text.split("\n\n"):
        if size + len(para) > 4000 and buf:
            parts.append("\n\n".join(buf))
            buf = [para]
            size = len(para)
        else:
            buf.append(para)
            size += len(para) + 2
    if buf:
        parts.append("\n\n".join(buf))

    out = []
    for i, chunk in enumerate(parts):
        if re.search(r"[\u0400-\u04FF]", chunk):
            for attempt in range(5):
                try:
                    out.append(tr.translate(chunk))
                    break
                except Exception:
                    time.sleep(2 + attempt)
            else:
                out.append(chunk)
            time.sleep(0.3)
        else:
            out.append(chunk)
    return "\n\n".join(out)


def process_file(path: Path) -> bool:
    raw = path.read_text(encoding="utf-8")
    if not re.search(r"[\u0400-\u04FF]", raw):
        return False
    text = translate_text(raw)
    lines = [fix_headers(ln) for ln in text.splitlines()]
    text = "\n".join(lines) + ("\n" if raw.endswith("\n") else "")
    path.write_text(text, encoding="utf-8", newline="\n")
    return True


def main():
    files = sorted(ROOT.rglob("*.txt"))
    n = 0
    for f in files:
        if process_file(f):
            print("OK", f.relative_to(ROOT.parent))
            n += 1
    print("done", n)


if __name__ == "__main__":
    main()
