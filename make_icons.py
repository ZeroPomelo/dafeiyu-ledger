#!/usr/bin/env python3
"""把用户提供的 1254x1254 图标切成 Android 需要的各密度资源。"""
import os
from PIL import Image, ImageDraw

SRC = "/root/.dsh/attachments/v1/objects/a3/a3660ccccc1de83fa61d48c451f8c93456bae03d592b49bcbc7cd93e237f3d9b"
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "res")

# 原图不透明内容的外框（已实测）
BBOX = (42, 72, 1213, 1192)
# 让内容略微超出画布，保证任何 launcher 遮罩都吃不到透明边角
COVER_MARGIN = 1.02

DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}


def cover_scaled(src, canvas):
    """把 src 放大到不透明区域完整覆盖 canvas x canvas，再居中裁切。"""
    bw = BBOX[2] - BBOX[0]
    bh = BBOX[3] - BBOX[1]
    scale = max(canvas / bw, canvas / bh) * COVER_MARGIN
    new_w = max(canvas, int(round(src.width * scale)))
    new_h = max(canvas, int(round(src.height * scale)))
    resized = src.resize((new_w, new_h), Image.LANCZOS)
    # 原图内容中心
    cx = (BBOX[0] + BBOX[2]) / 2.0 * scale
    cy = (BBOX[1] + BBOX[3]) / 2.0 * scale
    left = int(round(cx - canvas / 2.0))
    top = int(round(cy - canvas / 2.0))
    left = max(0, min(left, new_w - canvas))
    top = max(0, min(top, new_h - canvas))
    return resized.crop((left, top, left + canvas, top + canvas))


def circular(img):
    size = img.size[0]
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = img.copy()
    out.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (size, size), 0), mask))
    return out


def main():
    src = Image.open(SRC).convert("RGBA")

    for name, factor in DENSITIES.items():
        d = os.path.join(RES, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)

        legacy_size = int(round(48 * factor))
        legacy = src.resize((legacy_size, legacy_size), Image.LANCZOS)
        legacy.save(os.path.join(d, "ic_launcher.png"), "PNG")

        round_src = cover_scaled(src, legacy_size)
        circular(round_src).save(os.path.join(d, "ic_launcher_round.png"), "PNG")

        fg_size = int(round(108 * factor))
        cover_scaled(src, fg_size).save(os.path.join(d, "ic_launcher_foreground.png"), "PNG")
        print("mipmap-%-8s legacy=%d fg=%d" % (name, legacy_size, fg_size))

    # 自适应图标（API 26+）
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/ic_launcher_background" />\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
           '</adaptive-icon>\n')
    for fname in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, fname), "w", encoding="utf-8") as f:
            f.write(xml)
    print("adaptive xml written")


if __name__ == "__main__":
    main()
