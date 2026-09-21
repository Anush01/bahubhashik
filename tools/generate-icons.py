from PIL import Image, ImageDraw, ImageFont
import os

ROOT = "/Users/anush/Downloads/BahuBhashik"
DEVANAGARI = "/System/Library/Fonts/Supplemental/Devanagari Sangam MN.ttc"
KANNADA = "/System/Library/Fonts/Supplemental/Kannada Sangam MN.ttc"

TERRACOTTA_DARK = (122, 62, 18)
TERRACOTTA = (176, 88, 36)
CREAM = (255, 244, 232)
TEAL = (10, 106, 112)

def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))

def bubble(draw, box, fill, radius):
    """Rounded speech bubble with a tail at the bottom-left."""
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, radius=radius, fill=fill)
    w = x1 - x0
    tail = w * 0.22
    tx = x0 + w * 0.22
    draw.polygon(
        [(tx, y1 - 2), (tx + tail, y1 - 2), (tx + tail * 0.15, y1 + tail * 0.85)],
        fill=fill,
    )

def fitted_font(path, glyph, target_px, index=0):
    """Binary-search a point size so the glyph's inked height matches target_px."""
    low, high = 10, 900
    best = ImageFont.truetype(path, 10, index=index)
    while low <= high:
        mid = (low + high) // 2
        font = ImageFont.truetype(path, mid, index=index)
        box = font.getbbox(glyph)
        height = box[3] - box[1]
        if height <= target_px:
            best, low = font, mid + 1
        else:
            high = mid - 1
    return best

def centered(draw, xy, glyph, font, fill):
    cx, cy = xy
    x0, y0, x1, y1 = draw.textbbox((0, 0), glyph, font=font)
    draw.text((cx - (x1 + x0) / 2, cy - (y1 + y0) / 2), glyph, font=font, fill=fill)

def render(size, with_background=True, content_scale=1.0):
    # Supersample for clean curves and glyph edges, then downsample.
    S = size * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    if with_background:
        for y in range(S):
            draw.line([(0, y), (S, y)], fill=lerp(TERRACOTTA_DARK, TERRACOTTA, y / S))
        # Round the corners only for the iOS/legacy square asset.
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S, S], radius=int(S * 0.22), fill=255)
        img.putalpha(mask)
        draw = ImageDraw.Draw(img)

    # Bubbles laid out in a unit square, then scaled into the canvas.
    c = S * content_scale
    off = (S - c) / 2
    def px(fx, fy):
        return (off + fx * c, off + fy * c)

    back = (*px(0.09, 0.10), *px(0.60, 0.52))
    front = (*px(0.40, 0.46), *px(0.88, 0.83))
    radius = int(c * 0.13)

    # A slim gap around the front bubble keeps the two readable when the icon
    # is only a few dozen pixels wide.
    gap = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gap)
    grow = c * 0.045
    bubble(gd, (front[0] - grow, front[1] - grow, front[2] + grow, front[3] + grow),
           (0, 0, 0, 255), int(radius * 1.2))

    bubble(draw, back, CREAM, radius)
    img = Image.alpha_composite(img, Image.new("RGBA", (S, S), (0, 0, 0, 0)))
    # Punch the gap out of the back bubble.
    base = img.copy()
    base.paste((0, 0, 0, 0), (0, 0), gap)
    img = base
    draw = ImageDraw.Draw(img)
    bubble(draw, front, TEAL, radius)

    # 0.42 of the bubble's height keeps the tall Devanagari stem inside it.
    dev = fitted_font(DEVANAGARI, "अ", (back[3] - back[1]) * 0.42)
    kan = fitted_font(KANNADA, "ಅ", (front[3] - front[1]) * 0.42)
    centered(draw, ((back[0] + back[2]) / 2, (back[1] + back[3]) / 2 - c * 0.03), "अ", dev, TERRACOTTA_DARK)
    centered(draw, ((front[0] + front[2]) / 2, (front[1] + front[3]) / 2 - c * 0.03), "ಅ", kan, CREAM)

    return img.resize((size, size), Image.LANCZOS)

def circular(img):
    size = img.size[0]
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size * 4, size * 4], fill=255)
    out = img.copy()
    out.putalpha(mask.resize((size, size), Image.LANCZOS))
    return out

def square_no_round(size):
    """Legacy launcher icon: full-bleed square, no corner rounding."""
    img = render(size)
    bg = Image.new("RGBA", (size, size), TERRACOTTA)
    S = size * 4
    grad = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(grad)
    for y in range(S):
        d.line([(0, y), (S, y)], fill=lerp(TERRACOTTA_DARK, TERRACOTTA, y / S))
    bg = grad.resize((size, size), Image.LANCZOS)
    fg = render(size, with_background=False, content_scale=1.0)
    bg.alpha_composite(fg)
    return bg

# ---- iOS: one 1024 master ----
ios = f"{ROOT}/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"
# iOS applies its own mask, so ship a full-bleed square.
square_no_round(1024).convert("RGB").save(ios)
print("ios:", ios)

# ---- Android ----
res = f"{ROOT}/androidApp/src/main/res"
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
# Adaptive foreground is 108dp with only the middle 72dp guaranteed visible.
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

for density, size in LEGACY.items():
    d = f"{res}/mipmap-{density}"
    os.makedirs(d, exist_ok=True)
    square_no_round(size).save(f"{d}/ic_launcher.png")
    circular(square_no_round(size)).save(f"{d}/ic_launcher_round.png")

for density, size in ADAPTIVE.items():
    d = f"{res}/mipmap-{density}"
    # 0.62 keeps the bubbles inside the safe zone whatever mask the launcher uses.
    render(size, with_background=False, content_scale=0.62).save(f"{d}/ic_launcher_foreground.png")

print("android: legacy + adaptive foregrounds written")
square_no_round(512).save("/private/tmp/claude-502/-Users-anush-Downloads-BahuBhashik/4c8dada8-276a-4166-8459-30f2e13b1d27/scratchpad/icon-preview.png")
