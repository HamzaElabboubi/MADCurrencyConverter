"""Generates every launcher-icon asset of Currency Converter from the approved logo.

Source: design/launcher-icon/approved_logo.png (approved 3D artwork: a deep-green rounded-square
tile with a gold and a turquoise exchange arrow). The tile's rounded shape and rim cannot be kept
in an adaptive icon (each launcher applies its own mask), so the artwork is split into:
  - a background: the tile's deep-green interior, estimated from the plain background pixels;
  - a foreground: the arrows, their glow and their shadow, recovered against that background
    (each pixel's color and opacity are solved so that foreground over background gives back the
    original image), so the two layers recombine into the approved artwork.

Outputs (paths relative to the repository root):
  app/src/main/res/mipmap-<density>/ic_launcher_background.png   adaptive background (108dp)
  app/src/main/res/mipmap-<density>/ic_launcher_foreground.png   adaptive foreground (108dp)
  app/src/main/res/drawable/ic_launcher_monochrome.xml          themed icon silhouette (vector)
  app/src/main/res/mipmap-<density>/ic_launcher.webp             legacy icon (API 24-25): the tile
  app/src/main/res/mipmap-<density>/ic_launcher_round.webp       legacy round icon (API 24-25)
  design/play-store/currency_converter_play_icon_512.png        Google Play listing icon

Requires Python 3 with Pillow (WebP support) and numpy. Not part of the Gradle build:
  python design/launcher-icon/generate_launcher_icon.py [--previews DIR]
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "design" / "launcher-icon" / "approved_logo.png"
RES = ROOT / "app" / "src" / "main" / "res"
PLAY_ICON = ROOT / "design" / "play-store" / "currency_converter_play_icon_512.png"

# The tile in approved_logo.png (1254 x 1254 px), measured: outer edge of its light rim, and the
# rim width. The tile maps onto the 72dp a launcher shows of the 108dp adaptive layers.
TILE = (86, 77, 1167, 1141)  # left, top, right, bottom
TILE_CORNER = 232  # corner radius
RIM = 14  # rim + inner edge highlight, excluded from the layers
CANVAS_DP, VISIBLE_DP, SAFE_RADIUS_DP = 108.0, 72.0, 33.0

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_DP, LEGACY_SHAPE_DP = 48, 44  # legacy icons: the shape on a 44dp keyline, 2dp margin


def tile_center():
    return (TILE[0] + TILE[2]) / 2, (TILE[1] + TILE[3]) / 2


def px_per_dp():
    return ((TILE[2] - TILE[0]) + (TILE[3] - TILE[1])) / 2 / VISIBLE_DP


# --- Image helpers (numpy, float arrays) --------------------------------------------------------


def box(a, r):
    """Box blur of radius r over both axes (edges clamped), via cumulative sums."""
    for axis in (0, 1):
        pad = [(0, 0)] * a.ndim
        pad[axis] = (r + 1, r)
        p = np.pad(a, pad, mode="edge")
        c = np.cumsum(p, axis=axis)
        hi = np.take(c, range(2 * r + 1, p.shape[axis]), axis=axis)
        lo = np.take(c, range(0, p.shape[axis] - 2 * r - 1), axis=axis)
        a = (hi - lo) / (2 * r + 1)
    return a


def blur(a, sigma):
    """Approximately Gaussian (three box passes)."""
    r = max(1, int(sigma * 0.9))
    for _ in range(3):
        a = box(a, r)
    return a


def rounded_rect_mask(shape, rect, radius):
    m = Image.new("L", (shape[1], shape[0]), 0)
    ImageDraw.Draw(m).rounded_rectangle(rect, radius=radius, fill=255)
    return np.asarray(m) > 127


# --- Separation ---------------------------------------------------------------------------------


def separate(src):
    """Returns (background RGB, foreground RGB, foreground alpha, arrow-body mask), source px."""
    img = np.asarray(src.convert("RGB")).astype(np.float64)
    r, g, b = img[..., 0], img[..., 1], img[..., 2]
    l, t, rt, bt = TILE
    interior = rounded_rect_mask(img.shape, (l + RIM, t + RIM, rt - RIM, bt - RIM), TILE_CORNER - RIM)
    # Arrow bodies by color, well inside the tile (the rim is turquoise too).
    inner = rounded_rect_mask(img.shape, (l + 40, t + 40, rt - 40, bt - 40), TILE_CORNER - 40)
    gold = (r > 110) & (r > b + 40)
    turquoise = (g > 140) & (b > 90) & (g > r + 20)
    arrows = (gold | turquoise) & inner

    # Background: the plain tile pixels (away from the arrows' glow and shadow), smoothed and
    # spread under the arrows (normalized blur). The arrows' darker ribbons have the same colors
    # as the lit top of the tile, so they are kept out by the fitted arrow outline (MONO_ARROWS,
    # with a margin) rather than by color.
    outline = np.asarray(mono_raster_source(img.shape)) > 0.5
    near = (blur(arrows.astype(float), 25) > 0.01) | (blur(outline.astype(float), 30) > 0.01)
    samples = interior & ~near
    weight = blur(samples.astype(float), 60)
    bg = np.stack([blur(img[..., k] * samples, 60) for k in range(3)], -1) / np.maximum(weight, 1e-6)[..., None]

    # Foreground: the least opaque color that, over the background, gives the original pixel.
    up = np.where(img > bg, (img - bg) / np.maximum(255 - bg, 1e-6), 0)
    down = np.where(img < bg, (bg - img) / np.maximum(bg, 1e-6), 0)
    alpha = np.clip(np.max(np.maximum(up, down), -1), 0, 1)
    # Only the arrows and their glow/shadow, fading out with distance; nothing near the rim.
    # Full strength on the bright arrows and anywhere inside the fitted outline (dark ribbons).
    reach = np.clip(np.maximum(blur(arrows.astype(float), 30) * 6, blur(outline.astype(float), 15) * 3), 0, 1)
    alpha = alpha * reach * interior
    # Faint speckle is dropped only in the outer glow. Near the arrows every value is kept:
    # dropping it there would show the estimated background instead of the true pixel.
    alpha = np.where((alpha < 0.06) & (reach < 1), 0, alpha)
    fg = np.clip(bg + (img - bg) / np.maximum(alpha, 1e-6)[..., None], 0, 255)
    # Arrow material (bright bodies, and anything at least 35% opaque) is opaque with its own
    # original color: no noise amplified by the division above.
    solid = (blur(arrows.astype(float), 2) > 0.5) | (alpha >= 0.35)
    alpha = np.where(solid, 1.0, alpha)
    fg = np.where(solid[..., None], img, fg)
    fg = np.where(alpha[..., None] > 0, fg, 0)
    return bg, fg, alpha, arrows, interior


# Themed (monochrome) icon: the 3D arrows' layered ribbons do not reduce to a clean shape, so the
# silhouette is drawn as two flat arrows fitted to them (circle fits of each arrow, measured head
# corners). dp on the 108dp canvas, where the tile center is (54, 54); angles clockwise from east.
MONO_ARROWS = [
    # Gold, upper: tail on the left, over the top, head on the right pointing down.
    dict(cx=54.1, cy=52.7, r=21.6, w=8.4, start=198, base=317, inner=10.5, outer=28.6, tip=10, tip_r=27.5),
    # Turquoise, lower: tail on the right, under the bottom, head on the left pointing up.
    dict(cx=53.8, cy=56.0, r=19.1, w=8.6, start=8, base=145, inner=8.0, outer=26.0, tip=196, tip_r=23.0),
]


def mono_path(a):
    """Closed outline (absolute commands): outer arc, arrowhead, inner arc back."""

    def p(radius, degrees):
        t = math.radians(degrees)
        return f"{a['cx'] + radius * math.cos(t):.3f},{a['cy'] + radius * math.sin(t):.3f}"

    outer, inner = a["r"] + a["w"] / 2, a["r"] - a["w"] / 2
    large = 1 if a["base"] - a["start"] > 180 else 0
    return (
        f"M{p(outer, a['start'])} A{outer:.3f},{outer:.3f} 0 {large},1 {p(outer, a['base'])} "
        f"L{p(a['outer'], a['base'])} L{p(a['tip_r'], a['tip'])} L{p(a['inner'], a['base'])} "
        f"L{p(inner, a['base'])} A{inner:.3f},{inner:.3f} 0 {large},0 {p(inner, a['start'])} Z"
    )


MONO_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/launcher-icon/generate_launcher_icon.py: edit the script, not this file.
     Themed icon (Android 13+): one silhouette of the two arrows; the launcher applies its colors. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF000000"
        android:pathData="{path}" />
</vector>
"""


def write_monochrome():
    path = " ".join(mono_path(a) for a in MONO_ARROWS)
    (RES / "drawable" / "ic_launcher_monochrome.xml").write_text(MONO_XML.format(path=path), encoding="utf-8")


def mono_raster_source(shape):
    """The fitted arrow outline in approved_logo.png pixels (bool-ish 0..1 array)."""
    m = Image.new("L", (shape[1], shape[0]), 0)
    d = ImageDraw.Draw(m)
    ppd = px_per_dp()
    cx0, cy0 = tile_center()
    for a in MONO_ARROWS:
        pts = [((x - CANVAS_DP / 2) * ppd + cx0, (y - CANVAS_DP / 2) * ppd + cy0) for x, y in mono_points(a)]
        d.polygon(pts, fill=255)
    return np.asarray(m) / 255.0


def mono_points(a, steps=4):
    """Outline points of one fitted arrow, in canvas dp."""

    def p(radius, degrees):
        t = math.radians(degrees)
        return a["cx"] + radius * math.cos(t), a["cy"] + radius * math.sin(t)

    n = int((a["base"] - a["start"]) * steps)
    pts = [p(a["r"] + a["w"] / 2, a["start"] + (a["base"] - a["start"]) * i / n) for i in range(n + 1)]
    pts += [p(a["outer"], a["base"]), p(a["tip_r"], a["tip"]), p(a["inner"], a["base"])]
    pts += [p(a["r"] - a["w"] / 2, a["base"] - (a["base"] - a["start"]) * i / n) for i in range(n + 1)]
    return pts


def mono_raster(size):
    """The monochrome silhouette rendered at size px (previews only)."""
    big = size * 8
    m = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(m)
    s = big / CANVAS_DP
    for a in MONO_ARROWS:
        d.polygon([(x * s, y * s) for x, y in mono_points(a)], fill=255)
    return np.asarray(m.resize((size, size), Image.LANCZOS)) / 255.0


# --- Mapping onto the 108dp canvas --------------------------------------------------------------


def canvas_box():
    """The 108dp canvas, in source px, centered on the tile."""
    cx, cy = tile_center()
    half = CANVAS_DP / 2 * px_per_dp()
    return cx - half, cy - half, cx + half, cy + half


def to_canvas(rgba, size, extend_edges=False):
    """Crops the 108dp canvas out of a source-size RGBA float array and scales it to size px.
    Outside the image the canvas is transparent, or (extend_edges) the nearest interior color."""
    x0, y0, x1, y1 = canvas_box()
    n = int(round(x1 - x0))
    ys = np.clip(np.arange(n) + int(round(y0)), 0, rgba.shape[0] - 1)
    xs = np.clip(np.arange(n) + int(round(x0)), 0, rgba.shape[1] - 1)
    if extend_edges:
        l, t, rt, bt = TILE
        ys = np.clip(np.arange(n) + int(round(y0)), t + RIM, bt - RIM - 1)
        xs = np.clip(np.arange(n) + int(round(x0)), l + RIM, rt - RIM - 1)
    out = rgba[ys][:, xs].copy()
    if not extend_edges:
        inside_y = (np.arange(n) + round(y0) >= 0) & (np.arange(n) + round(y0) < rgba.shape[0])
        inside_x = (np.arange(n) + round(x0) >= 0) & (np.arange(n) + round(x0) < rgba.shape[1])
        out[~inside_y, :, 3] = 0
        out[:, ~inside_x, 3] = 0
    return resize_rgba(out, size)


def resize_rgba(rgba, size):
    """High-quality downscale with premultiplied alpha (no dark fringes)."""
    a = rgba[..., 3:4] / 255.0
    pre = np.dstack([rgba[..., :3] * a, rgba[..., 3:4]])
    chans = [Image.fromarray(np.clip(pre[..., k], 0, 255).astype(np.float32), "F").resize((size, size), Image.LANCZOS) for k in range(4)]
    out = np.stack([np.asarray(c) for c in chans], -1)
    alpha = np.clip(out[..., 3], 0, 255)
    rgb = np.where(alpha[..., None] > 0, out[..., :3] / np.maximum(alpha[..., None] / 255.0, 1e-6), 0)
    return np.clip(np.dstack([rgb, alpha]), 0, 255).astype(np.uint8)


def composite(bg_rgba, fg_rgba):
    a = fg_rgba[..., 3:4] / 255.0
    rgb = fg_rgba[..., :3] * a + bg_rgba[..., :3] * (1 - a)
    return np.dstack([rgb, np.full(rgb.shape[:2] + (1,), 255)]).astype(np.uint8)


def visible(full):
    """The central 72dp of a 108dp canvas array."""
    n = full.shape[0]
    m = int(round(n * (CANVAS_DP - VISIBLE_DP) / 2 / CANVAS_DP))
    return full[m:n - m, m:n - m]


def masked(rgba, shape):
    size = rgba.shape[0]
    big = size * 8
    m = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(m)
    if shape == "circle":
        d.ellipse([0, 0, big - 1, big - 1], fill=255)
    elif shape == "rounded":
        d.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.22), fill=255)
    elif shape == "squircle":
        d.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.32), fill=255)
    elif shape == "teardrop":
        d.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.5), fill=255)
        d.rectangle([big // 2, big // 2, big, big], fill=255)
    m = np.asarray(m.resize((size, size), Image.LANCZOS)) / 255.0
    out = rgba.copy()
    out[..., 3] = (out[..., 3] * m).astype(np.uint8)
    return out


# --- Outputs ------------------------------------------------------------------------------------


def build(src):
    bg, fg, alpha, _, _ = separate(src)
    opaque = np.full(alpha.shape, 255.0)
    bg_rgba = np.dstack([bg, opaque])
    fg_rgba = np.dstack([fg, alpha * 255])
    return bg_rgba, fg_rgba


def write_layers(bg_rgba, fg_rgba):
    for density, f in DENSITIES.items():
        size = round(CANVAS_DP * f)
        out = RES / f"mipmap-{density}"
        Image.fromarray(to_canvas(bg_rgba, size, extend_edges=True)[..., :3], "RGB").save(out / "ic_launcher_background.png", optimize=True)
        Image.fromarray(to_canvas(fg_rgba, size), "RGBA").save(out / "ic_launcher_foreground.png", optimize=True)


def legacy_tile(src, size):
    """The approved tile itself (rounded shape and rim), on the legacy 44dp keyline."""
    img = np.asarray(src.convert("RGB")).astype(np.float64)
    l, t, rt, bt = TILE
    shape = rounded_rect_mask(img.shape, (l + 2, t + 2, rt - 2, bt - 2), TILE_CORNER - 2)
    rgba = np.dstack([img, shape * 255.0])[t:bt, l:rt]
    side = max(rgba.shape[:2])  # square it up (the tile is 1081 x 1064)
    sq = np.zeros((side, side, 4))
    oy, ox = (side - rgba.shape[0]) // 2, (side - rgba.shape[1]) // 2
    sq[oy:oy + rgba.shape[0], ox:ox + rgba.shape[1]] = rgba
    inner = round(size * LEGACY_SHAPE_DP / LEGACY_DP)
    icon = resize_rgba(sq, inner)
    canvas = np.zeros((size, size, 4), np.uint8)
    o = (size - inner) // 2
    canvas[o:o + inner, o:o + inner] = icon
    return canvas


def legacy_round(bg_rgba, fg_rgba, size):
    inner = round(size * LEGACY_SHAPE_DP / LEGACY_DP)
    n = round(inner * CANVAS_DP / VISIBLE_DP)
    full = composite(to_canvas(bg_rgba, n, extend_edges=True), to_canvas(fg_rgba, n))
    icon = masked(resize_rgba(visible(full).astype(np.float64), inner), "circle")
    canvas = np.zeros((size, size, 4), np.uint8)
    o = (size - inner) // 2
    canvas[o:o + inner, o:o + inner] = icon
    return canvas


def write_legacy(src, bg_rgba, fg_rgba):
    for density, f in DENSITIES.items():
        size = round(LEGACY_DP * f)
        out = RES / f"mipmap-{density}"
        Image.fromarray(legacy_tile(src, size), "RGBA").save(out / "ic_launcher.webp", "WEBP", lossless=True, quality=100, method=6)
        Image.fromarray(legacy_round(bg_rgba, fg_rgba, size), "RGBA").save(out / "ic_launcher_round.webp", "WEBP", lossless=True, quality=100, method=6)


def play_icon(bg_rgba, fg_rgba):
    """512 x 512 full square, opaque 32-bit PNG; Google Play applies its own corner mask."""
    n = round(512 * CANVAS_DP / VISIBLE_DP)
    full = composite(to_canvas(bg_rgba, n, extend_edges=True), to_canvas(fg_rgba, n))
    icon = visible(full)
    return resize_rgba(icon.astype(np.float64), 512)


def write_previews(directory, bg_rgba, fg_rgba):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    n = 432
    full = composite(to_canvas(bg_rgba, n, extend_edges=True), to_canvas(fg_rgba, n))
    fg = to_canvas(fg_rgba, n)
    sheet = Image.new("RGB", (1300, 1080), (255, 255, 255))
    # Layers: background, foreground on a checkerboard, monochrome, with keylines.
    yy, xx = np.mgrid[0:n, 0:n]
    checker = (((xx // 18 + yy // 18) % 2) * 70 + 120)[..., None].repeat(3, -1)
    on_checker = (fg[..., :3] * (fg[..., 3:4] / 255) + checker * (1 - fg[..., 3:4] / 255)).astype(np.uint8)
    mono = mono_raster(n)[..., None]
    mono_view = (np.full((n, n, 3), (0x1E, 0x2B, 0x29)) * (1 - mono) + np.array((0xB7, 0xDE, 0xD6)) * mono).astype(np.uint8)
    for i, arr in enumerate((full[..., :3], on_checker, mono_view)):
        im = Image.fromarray(arr.astype(np.uint8), "RGB")
        d = ImageDraw.Draw(im)
        c, s = n / 2, n / CANVAS_DP
        d.ellipse([c - SAFE_RADIUS_DP * s] * 2 + [c + SAFE_RADIUS_DP * s] * 2, outline=(255, 70, 70), width=2)
        v = (CANVAS_DP - VISIBLE_DP) / 2 * s
        d.rectangle([v, v, n - v, n - v], outline=(255, 255, 255), width=1)
        sheet.paste(im.resize((400, 400), Image.LANCZOS), (20 + i * 420, 10))
    # Masks on light and dark wallpaper, and small sizes.
    for row, wall in enumerate(((0xF5, 0xF7, 0xF4), (0x12, 0x14, 0x14))):
        panel = Image.new("RGB", (1260, 190), wall)
        x = 15
        for shape in ("circle", "squircle", "rounded", "teardrop"):
            ic = Image.fromarray(masked(resize_rgba(visible(full).astype(np.float64), 150), shape), "RGBA")
            panel.paste(ic, (x, 20), ic)
            x += 170
        for size in (96, 72, 48, 36, 24):
            ic = Image.fromarray(masked(resize_rgba(visible(full).astype(np.float64), size), "circle"), "RGBA")
            panel.paste(ic, (x, 20 + (150 - size) // 2), ic)
            x += size + 20
        sheet.paste(panel, (20, 430 + row * 200))
    # Legacy icons as packaged.
    panel = Image.new("RGB", (1260, 240), (0xF5, 0xF7, 0xF4))
    x = 15
    for density in DENSITIES:
        for name in ("ic_launcher.webp", "ic_launcher_round.webp"):
            ic = Image.open(RES / f"mipmap-{density}" / name).convert("RGBA")
            panel.paste(ic, (x, 220 - ic.size[1]), ic)
            x += ic.size[0] + 10
    sheet.paste(panel, (20, 830))
    sheet.save(directory / "launcher_icon_previews.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--previews", help="also write review previews to this directory")
    args = parser.parse_args()
    src = Image.open(SOURCE)
    bg_rgba, fg_rgba = build(src)
    write_layers(bg_rgba, fg_rgba)
    write_monochrome()
    write_legacy(src, bg_rgba, fg_rgba)
    PLAY_ICON.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(play_icon(bg_rgba, fg_rgba)[..., :3], "RGB").convert("RGBA").save(PLAY_ICON, "PNG")
    if args.previews:
        write_previews(args.previews, bg_rgba, fg_rgba)
    # Safe zone: nothing of the foreground beyond 33dp from the center.
    fg = to_canvas(fg_rgba, 432)
    yy, xx = np.mgrid[0:432, 0:432]
    radius_dp = np.hypot(xx - 216 + 0.5, yy - 216 + 0.5) / 4
    reach = radius_dp[fg[..., 3] > 8].max()
    print(f"foreground reaches {reach:.1f}dp from the center (safe zone {SAFE_RADIUS_DP}dp)")
    assert reach <= SAFE_RADIUS_DP, "foreground leaves the safe zone"


if __name__ == "__main__":
    main()
