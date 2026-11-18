"""
Crops the phone screenshots down to the size Play asks for.

A Pixel 7 captures 1080 x 2400, which is a 9:19.5 screen. Play wants 1080 x
1920, which is 9:16, and it does not pad the image for you - the extra strip
has to come off somewhere.

It comes off the bottom. The top of a capture holds the status bar and the
score, which is the part the listing is about, and the bottom holds the
buttons and the navigation bar, which the listing does not need.

The files are also flattened onto an opaque background. Play accepts a PNG
with an alpha channel, but there is nothing transparent in a screenshot and
an opaque file is one less thing to go wrong in the console.

Run it:

    python tools/crop-screenshots.py
"""

import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCREENSHOTS = ROOT / "docs" / "store" / "screenshots"

TARGET_WIDTH = 1080
TARGET_HEIGHT = 1920


def crop(path: pathlib.Path) -> bool:
    """Crops one file in place. Returns True if it changed."""
    with Image.open(path) as image:
        width, height = image.size
        if width != TARGET_WIDTH or height <= TARGET_HEIGHT:
            print(f"Skipped {path.name}: {width} x {height}")
            return False
        cropped = image.crop((0, 0, TARGET_WIDTH, TARGET_HEIGHT))
        if cropped.mode != "RGB":
            # Flatten it: a screenshot has no transparency, and an opaque
            # file is what the Play console expects.
            flattened = Image.new("RGB", cropped.size, (0, 0, 0))
            flattened.paste(cropped, mask=cropped.split()[3])
            cropped = flattened
        cropped.save(path, "PNG", optimize=True)
    print(f"Cropped {path.name} to {TARGET_WIDTH} x {TARGET_HEIGHT}")
    return True


def main() -> int:
    if not SCREENSHOTS.exists():
        print(f"Not found: {SCREENSHOTS}", file=sys.stderr)
        return 1
    files = sorted(SCREENSHOTS.glob("*.png"))
    if not files:
        print("No screenshots to crop.")
        return 0
    for path in files:
        crop(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
