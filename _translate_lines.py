import re
import time
from pathlib import Path
from deep_translator import GoogleTranslator

path = Path(__file__).parent / "mobs" / "skeletons" / "skeleton.txt"
tr = GoogleTranslator(source="ru", target="en")
text = path.read_text(encoding="utf-8")
lines = text.splitlines(keepends=True)
out = []
batch = []
batch_idx = []

def flush_batch():
    global batch, batch_idx, out
    if not batch:
        return
    joined = "\n".join(batch)
    for attempt in range(6):
        try:
            translated = tr.translate(joined).split("\n")
            if len(translated) != len(batch):
                # fallback line by line
                for i, ln in zip(batch_idx, batch):
                    out[i] = tr.translate(ln) + ("\n" if lines[i].endswith("\n") else "")
            else:
                for i, tln in zip(batch_idx, translated):
                    nl = "\n" if lines[i].endswith("\n") else ""
                    out[i] = tln + nl
            break
        except Exception:
            time.sleep(2 + attempt)
    else:
        for i in batch_idx:
            out[i] = lines[i]
    batch = []
    batch_idx = []
    time.sleep(0.2)

for i, line in enumerate(lines):
    out.append(line)
    if re.search(r"[\u0400-\u04FF]", line):
        batch.append(line.rstrip("\r\n"))
        batch_idx.append(i)
        if len(batch) >= 15:
            flush_batch()
flush_batch()

path.write_text("".join(out), encoding="utf-8", newline="\n")
remaining = sum(1 for ln in out if re.search(r"[\u0400-\u04FF]", ln))
print("remaining_cyrillic_lines", remaining)
