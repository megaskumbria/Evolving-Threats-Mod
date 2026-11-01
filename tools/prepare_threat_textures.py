"""
Готовит картинки шкалы угрозы для игры из больших исходников (то, что нарисовала нейросеть).

Что делает:
  * вырезает рамку, заполнение, 8 фаз луны и 4 иконки-черепа;
  * убирает белый фон (там, где нет настоящей прозрачности);
  * уменьшает до игровых размеров и делает края чёткими (без полупрозрачной каймы);
  * кладёт файлы в src/main/resources/assets/evolving-threats/textures/gui/sprites/threat/.

Запуск (из корня проекта):  python tools/prepare_threat_textures.py
Нужны Python 3 и пакеты pillow, numpy, scipy.

Исходники лежат в art/threat_hud/. Заменишь картинку там и запустишь скрипт снова — в игре обновится.
"""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

# ===================== SETTINGS =====================

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "art" / "threat_hud"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "evolving-threats" / "textures" / "gui" / "sprites" / "threat"

# Размер рамки шкалы в пикселях игры. Соотношение сторон исходника около 10.3 : 1.
FRAME_SIZE = (128, 12)

# Размер иконок (луна и черепа) в пикселях игры. 16 = как предметы в инвентаре.
ICON_SIZE = 16

# Запас вокруг иконки при вырезании, доля от её размера. Больше = иконка в кадре меньше.
ICON_MARGIN = 0.06

# Порог прозрачности: пиксели с прозрачностью выше становятся полностью видимыми, ниже — пропадают.
ALPHA_CUT = 128

# Что считать белым фоном у картинок без прозрачности (минимальный из каналов R, G, B больше этого числа).
WHITE_LEVEL = 232

# Убирать ли «zZZ» над спящим зомби (первый череп). True = только голова, она стоит ровно по центру.
REMOVE_SLEEP_LETTERS = True

# С какой высоты (доля высоты иконки сверху) начинается «чистое» лицо, где букв нет. Нужно, чтобы измерить ширину головы.
SLEEP_FACE_ROWS_FROM = 0.65

# До какой высоты (доля высоты иконки сверху) нарисованы буквы. Всё выше этой линии справа от центра заменяется зеркалом левой стороны.
SLEEP_LETTERS_BOTTOM = 0.53

# =====================================================


def to_small(img: Image.Image, size) -> Image.Image:
    """Уменьшает RGBA с учётом прозрачности и делает края чёткими."""
    premult = img.convert("RGBa").resize(size, Image.Resampling.BOX).convert("RGBA")
    arr = np.array(premult)
    arr[..., 3] = np.where(arr[..., 3] >= ALPHA_CUT, 255, 0)
    arr[arr[..., 3] == 0] = 0
    return Image.fromarray(arr, "RGBA")


def square_crop(img: Image.Image, box) -> Image.Image:
    """Вырезает квадрат вокруг прямоугольника box = (x0, y0, x1, y1), чтобы не искажать пропорции."""
    x0, y0, x1, y1 = box
    side = int(max(x1 - x0, y1 - y0) * (1 + ICON_MARGIN * 2))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    left, top = int(cx - side / 2), int(cy - side / 2)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, (-left, -top))
    return canvas


def remove_white_background(img: Image.Image) -> Image.Image:
    """Белый фон, который соединён с краем картинки, делает прозрачным. Белое внутри обводки остаётся."""
    rgb = np.array(img.convert("RGB")).astype(int)
    white = rgb.min(axis=2) > WHITE_LEVEL
    labels, count = ndimage.label(white)
    sizes = ndimage.sum(white, labels, range(1, count + 1))
    background = labels == (int(np.argmax(sizes)) + 1)
    rgba = np.array(img.convert("RGBA"))
    rgba[background, 3] = 0
    return Image.fromarray(rgba, "RGBA")


def opaque_boxes(img: Image.Image, min_pixels: int, grow: int = 0):
    """Находит отдельные картинки на листе. Возвращает рамки (x0, y0, x1, y1), отсортированные слева направо, сверху вниз."""
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

    # Окно внутри рамки: прозрачная область, которая не касается края.
    sub = ~np.array(frame_crop)[..., 3].astype(bool) | (np.array(frame_crop)[..., 3] < 16)
    labels, count = ndimage.label(sub)
    hole = None
    for i in range(1, count + 1):
        hy, hx = np.where(labels == i)
        touches = hy.min() == 0 or hx.min() == 0 or hy.max() == sub.shape[0] - 1 or hx.max() == sub.shape[1] - 1
        if not touches and (hole is None or len(hy) > hole[0]):
            hole = (len(hy), hx.min(), hy.min(), hx.max() + 1, hy.max() + 1)
    assert hole, "не нашёл окно внутри рамки"

    fw, fh = FRAME_SIZE
    sx = fw / frame_crop.width
    sy = fh / frame_crop.height
    frame = to_small(frame_crop, FRAME_SIZE)
    hx0, hy0 = round(hole[1] * sx), round(hole[2] * sy)
    hx1, hy1 = round(hole[3] * sx), round(hole[4] * sy)
    hole_w, hole_h = hx1 - hx0, hy1 - hy0
    # В рамке окно должно быть прозрачным совсем.
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
    print(f"рамка {fw}x{fh}, окно x={hx0} y={hy0} {hole_w}x{hole_h} (эти числа нужны в HUD)")


def make_moons():
    src = Image.open(SOURCE / "moon_phases.png").convert("RGBA")
    boxes = sorted(opaque_boxes(src, min_pixels=2000), key=lambda b: b[1])  # сверху вниз
    assert len(boxes) >= 8, f"нашёл {len(boxes)} лун, нужно хотя бы 8"
    # Девятая луна на листе повторяет первую (круг замкнут). Берём только 8.
    for index, box in enumerate(boxes[:8]):
        to_small(square_crop(src, box), (ICON_SIZE, ICON_SIZE)).save(OUT / f"moon_{index}.png")
    print("луны: 8 файлов moon_0..moon_7 (0 = полнолуние, 4 = новолуние)")


def remove_sleep_letters(img: Image.Image, box) -> Image.Image:
    """
    Убирает «zZZ» над головой спящего зомби, чтобы голова стояла в центре иконки.
    Буквы частично заходят на голову, поэтому всю правую верхнюю часть (над глазами) заменяем
    зеркальной копией левой: голова симметрична. Глаза и рот внизу не трогаем.
    """
    x0, y0, x1, y1 = box
    rgba = np.array(img.convert("RGBA"))
    alpha = rgba[..., 3] > ALPHA_CUT

    # Ширина головы: смотрим на нижнюю часть иконки, где букв точно нет.
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
    assert len(boxes) == 4, f"нашёл {len(boxes)} иконок, нужно 4"
    if REMOVE_SLEEP_LETTERS:
        src = remove_sleep_letters(src, boxes[0])
        # Рамка первой иконки теперь только по голове, поэтому иконка встанет по центру.
        boxes[0] = sorted(opaque_boxes(src, min_pixels=5000, grow=12), key=lambda b: b[0])[0]
    for index, box in enumerate(boxes):
        to_small(square_crop(src, box), (ICON_SIZE, ICON_SIZE)).save(OUT / f"stage_{index}.png")
    print("иконки ступеней: stage_0..stage_3")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    make_frame_and_fill()
    make_moons()
    make_stages()
    print("готово:", OUT)
