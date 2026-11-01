"""
Builds in-game threat-scale images from large source art (e.g. AI-generated sheets).

What it does:
  * crops the bar frame, fill, 8 moon phases, and 4 skull stage icons;
  * removes white background where there is no real alpha;
  * downscales to in-game sizes with crisp edges (no semi-transparent fringe);
  * writes files to src/main/resources/assets/evolving-threats/textures/gui/sprites/threat/.

Run from project root:  python tools/prepare_threat_textures.py
Requires Python 3 and packages pillow, numpy, scipy.

Sources live in art/threat_hud/. Replace art there and run again to update in-game textures.
"""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

# ===================== SETTINGS =====================

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "art" / "threat_hud"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "evolving-threats" / "textures" / "gui" / "sprites" / "threat"

# Bar frame size in in-game pixels. Source aspect ratio is about 10.3 : 1.
FRAME_SIZE = (128, 12)

# Icon size (moon and skulls) in in-game pixels. 16 = same as inventory items.
ICON_SIZE = 16

# Extra margin when cropping an icon, as a fraction of its size. Larger = smaller icon in frame.
ICON_MARGIN = 0.06

# Alpha cutoff: pixels above become fully opaque, below become fully transparent.
ALPHA_CUT = 128

# Treat as white background on images without alpha (min of R, G, B channels above this value).
WHITE_LEVEL = 232

# Remove "zZZ" above the sleeping zombie (first skull). True = head only, centered in icon.
REMOVE_SLEEP_LETTERS = True

# From this height (fraction from top of icon) the face has no letters — used to measure head width.
SLEEP_FACE_ROWS_FROM = 0.65

# Letters end at this height (fraction from top). Above this line, right of center is mirrored from the left.
SLEEP_LETTERS_BOTTOM = 0.53

# =====================================================


def to_small(img: Image.Image, size) -> Image.Image:
    """Downscale RGBA with alpha-aware resize and hard edges."""
    premult = img.convert("RGBa").resize(size, Image.Resampling.BOX).convert("RGBA")
    arr = np.array(premult)
    arr[..., 3] = np.where(arr[..., 3] >= ALPHA_CUT, 255, 0)
    arr[arr[..., 3] == 0] = 0
    return Image.fromarray(arr, "RGBA")


def square_crop(img: Image.Image, box) -> Image.Image:
    """Square crop around box = (x0, y0, x1, y1) so aspect ratio is not stretched."""
    x0, y0, x1, y1 = box
    side = int(max(x1 - x0, y1 - y0) * (1 + ICON_MARGIN * 2))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    left, top = int(cx - side / 2), int(cy - side / 2)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, (-left, -top))
    return canvas


def remove_white_background(img: Image.Image) -> Image.Image:
    """White connected to the image edge becomes transparent. White inside outlines stays."""
    rgb = np.array(img.convert("RGB")).astype(int)
    white = rgb.min(axis=2) > WHITE_LEVEL
    labels, count = ndimage.label(white)
    sizes = ndimage.sum(white, labels, range(1, count + 1))
    background = labels == (int(np.argmax(sizes)) + 1)
    rgba = np.array(img.convert("RGBA"))
    rgba[background, 3] = 0
    return Image.fromarray(rgba, "RGBA")


def opaque_boxes(img: Image.Image, min_pixels: int, grow: int = 0):
    """Find separate sprites on a sheet. Returns boxes (x0, y0, x1, y1), sorted left-to-right, top-to-bottom."""
    alpha = np.array(img.convert("RGBA"))[..., 3] > ALPHA_CUT
    grown = ndimage.binary_dilation(alpha, iterations=grow) if grow else alpha
    labels, count = ndimage.label(grown)
    boxes = []
    for i in range(1, count + 1):
        ys, xs = np.where((labels == i) & alpha)
        if len(ys) >= min_pixels:
            boxes.append((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))
    return sorted(boxes, key=lambda b: (b[1] // 200, b[0]))


def make_frame_and_fill():
    frame_src = Image.open(SOURCE / "bar_frame.png").convert("RGBA")
    alpha = np.array(frame_src)[..., 3] > ALPHA_CUT
    ys, xs = np.where(alpha)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    frame_crop = frame_src.crop(box)

    # Inner window: transparent region that does not touch the frame edge.
    sub = ~np.array(frame_crop)[..., 3].astype(bool) | (np.array(frame_crop)[..., 3] < 16)
    labels, count = ndimage.label(sub)
    hole = None
    for i in range(1, count + 1):
        hy, hx = np.where(labels == i)
        touches = hy.min() == 0 or hx.min() == 0 or hy.max() == sub.shape[0] - 1 or hx.max() == sub.shape[1] - 1
        if not touches and (hole is None or len(hy) > hole[0]):
            hole = (len(hy), hx.min(), hy.min(), hx.max() + 1, hy.max() + 1)
    assert hole, "could not find inner window in bar frame"

    fw, fh = FRAME_SIZE
    sx = fw / frame_crop.width
    sy = fh / frame_crop.height
    frame = to_small(frame_crop, FRAME_SIZE)
    hx0, hy0 = round(hole[1] * sx), round(hole[2] * sy)
    hx1, hy1 = round(hole[3] * sx), round(hole[4] * sy)
    hole_w, hole_h = hx1 - hx0, hy1 - hy0
    # Hole in the frame must be fully transparent.
    arr = np.array(frame)
    arr[hy0:hy1, hx0:hx1] = 0
    Image.fromarray(arr, "RGBA").save(OUT / "bar_frame.png")

    fill_src = Image.open(SOURCE / "bar_fill.png").convert("RGBA")
    a = np.array(fill_src)[..., 3] > ALPHA_CUT
    fy, fx = np.where(a)
    fill_crop = fill_src.crop((int(fx.min()), int(fy.min()), int(fx.max()) + 1, int(fy.max()) + 1))
    fill = fill_crop.convert("RGBa").resize((hole_w, hole_h), Image.Resampling.BOX).convert("RGBA")
    arr = np.array(fill)
    arr[..., 3] = 255
    Image.fromarray(arr, "RGBA").save(OUT / "bar_fill.png")
    print(f"frame {fw}x{fh}, hole x={hx0} y={hy0} {hole_w}x{hole_h} (use these numbers in HUD code)")


def make_moons():
    src = Image.open(SOURCE / "moon_phases.png").convert("RGBA")
    boxes = sorted(opaque_boxes(src, min_pixels=2000), key=lambda b: b[1])  # top to bottom
    assert len(boxes) >= 8, f"found {len(boxes)} moons, need at least 8"
    # Ninth moon on the sheet duplicates the first (closed cycle). Keep 8 only.
    for index, box in enumerate(boxes[:8]):
        to_small(square_crop(src, box), (ICON_SIZE, ICON_SIZE)).save(OUT / f"moon_{index}.png")
    print("moons: 8 files moon_0..moon_7 (0 = full moon, 4 = new moon)")


def remove_sleep_letters(img: Image.Image, box) -> Image.Image:
    """
    Remove "zZZ" above the sleeping zombie head so the head sits centered in the icon.
    Letters overlap the head, so the upper-right (above eyes) is replaced with a mirror
    of the left side: the head is symmetric. Eyes and mouth at the bottom are untouched.
    """
    x0, y0, x1, y1 = box
    rgba = np.array(img.convert("RGBA"))
    alpha = rgba[..., 3] > ALPHA_CUT

    # Head width: use the lower part of the icon where there are no letters.
    face_rows = alpha[y0 + int((y1 - y0) * SLEEP_FACE_ROWS_FROM): y1]
    columns = np.where(face_rows.any(axis=0))[0]
    columns = columns[(columns >= x0) & (columns <= x1)]
    head_left, head_right = int(columns.min()), int(columns.max())
    center = (head_left + head_right) / 2

    bottom = y0 + int((y1 - y0) * SLEEP_LETTERS_BOTTOM)
    out = rgba.copy()
    for y in range(y0, bottom):
        for x in range(int(center) + 1, x1 + 1):
            mirror = int(round(2 * center - x))
            if head_left <= mirror <= head_right:
                out[y, x] = rgba[y, mirror]
            else:
                out[y, x] = 0
    return Image.fromarray(out, "RGBA")


def make_stages():
    src = remove_white_background(Image.open(SOURCE / "stages.png"))
    boxes = opaque_boxes(src, min_pixels=5000, grow=12)
    boxes = sorted(boxes, key=lambda b: b[0])
    assert len(boxes) == 4, f"found {len(boxes)} icons, need 4"
    if REMOVE_SLEEP_LETTERS:
        src = remove_sleep_letters(src, boxes[0])
        # First icon bounds are head-only so it centers in the square crop.
        boxes[0] = sorted(opaque_boxes(src, min_pixels=5000, grow=12), key=lambda b: b[0])[0]
    for index, box in enumerate(boxes):
        to_small(square_crop(src, box), (ICON_SIZE, ICON_SIZE)).save(OUT / f"stage_{index}.png")
    print("stage icons: stage_0..stage_3")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    make_frame_and_fill()
    make_moons()
    make_stages()
    print("done:", OUT)
