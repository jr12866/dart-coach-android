"""Generate the Play Store icon and feature graphic.

The artwork is the same dartboard used by the launcher icon, rendered at the
sizes Play requires.  Keeping it scripted means the store assets can be
regenerated when the app colours change, rather than being a set of opaque
binary files in the repository.

Run from the repository root:

    python tools/generate-store-assets.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "store"

BACKGROUND = "#0B0F14"
GREEN = "#4ADE80"
WHITE = "#E8EDF2"
AMBER = "#FFD166"
MUTED = "#7D8894"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """Use a Windows sans font when available, otherwise Pillow's default."""
    names = [
        "segoeuib.ttf" if bold else "segoeui.ttf",
        "arialbd.ttf" if bold else "arial.ttf",
    ]
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def dartboard(draw: ImageDraw.ImageDraw, centre: tuple[int, int], radius: int) -> None:
    """Draw the concentric dartboard used by the app and the launcher icon."""
    x, y = centre
    draw.ellipse(
        (x - radius, y - radius, x + radius, y + radius),
        fill=GREEN,
    )
    draw.ellipse(
        (x - int(radius * 0.81), y - int(radius * 0.81),
         x + int(radius * 0.81), y + int(radius * 0.81)),
        fill=BACKGROUND,
    )
    draw.ellipse(
        (x - int(radius * 0.46), y - int(radius * 0.46),
         x + int(radius * 0.46), y + int(radius * 0.46)),
        fill=WHITE,
    )
    draw.ellipse(
        (x - int(radius * 0.15), y - int(radius * 0.15),
         x + int(radius * 0.15), y + int(radius * 0.15)),
        fill=AMBER,
    )


def make_icon() -> None:
    size = 512
    image = Image.new("RGBA", (size, size), BACKGROUND)
    draw = ImageDraw.Draw(image)
    dartboard(draw, (size // 2, size // 2), int(size * 0.37))
    image.save(OUT / "icon-512.png", optimize=True)


def make_feature_graphic() -> None:
    width, height = 1024, 500
    image = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(image)

    # A restrained green glow behind the board. The feature graphic must have
    # no alpha channel, so it is flattened to RGB here.
    for i in range(8, 0, -1):
        r = 145 + i * 6
        draw.ellipse((690 - r, 250 - r, 690 + r, 250 + r), fill="#10251B")

    dartboard(draw, (690, 250), 135)

    title_font = font(58, bold=True)
    subtitle_font = font(27)
    draw.text((65, 165), "Darts Coach", font=title_font, fill=WHITE)
    draw.text((67, 245), "Say the score. Get the route.", font=subtitle_font, fill=GREEN)
    draw.text(
        (67, 305),
        "Offline speech and voice. Nothing leaves your phone.",
        font=font(19),
        fill=MUTED,
    )

    image.save(OUT / "feature-graphic.png", optimize=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    make_icon()
    make_feature_graphic()
    print(f"Wrote {OUT / 'icon-512.png'}")
    print(f"Wrote {OUT / 'feature-graphic.png'}")


if __name__ == "__main__":
    main()
