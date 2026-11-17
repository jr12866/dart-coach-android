"""
Generates the public privacy policy page from the markdown source.

There are several copies of the policy and they all have to agree, so they are
built from one source rather than edited one by one:

`docs/PRIVACY-POLICY.md` is the source. `docs/store/privacy-policy.html` is the
page that gets uploaded to the web and linked from the Play listing.
`app/src/main/res/raw/privacy_policy.html` is the copy bundled in the app, so
the policy can be read offline on a fresh install, before the Play listing's
URL has been filled in. `site/index.html` is the copy GitHub Pages serves at
`https://jr12866.github.io/dart-coach-android/`, which is the URL that goes
into the Play Console and into the app.

Run it:

    python tools/generate-privacy-policy.py
"""

import html
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs" / "PRIVACY-POLICY.md"
TARGET = ROOT / "docs" / "store" / "privacy-policy.html"
APP_COPY = (
    ROOT / "app" / "src" / "main" / "res" / "raw" / "privacy_policy.html"
)
# The GitHub Pages copy. `site` is the Pages source directory, and
# `index.html` is the page the URL serves, so the policy is what a visitor
# lands on rather than a link to it.
PAGES_DIR = ROOT / "site"
PAGES_COPY = PAGES_DIR / "index.html"
# GitHub Pages runs Jekyll over the source by default, which rewrites files
# it thinks are templates. The policy is a static page and must be copied
# exactly, so the build is turned off.
PAGES_NOJEKYLL = PAGES_DIR / ".nojekyll"

STYLE = """    body { font-family: system-ui, sans-serif; line-height: 1.55; margin: 0 auto; max-width: 760px; padding: 2rem 1.25rem; color: #20262d; }
    h1 { line-height: 1.2; }
    h2 { margin-top: 2rem; }
    code { background: #f2f4f6; padding: .1rem .25rem; }"""


def inline(text: str) -> str:
    """The inline markup: bold, code and links."""
    text = html.escape(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"`(.+?)`", r"<code>\1</code>", text)
    text = re.sub(r"\[(.+?)\]\((.+?)\)", r'<a href="\2">\1</a>', text)
    return text


def render(markdown: str) -> str:
    """Turns the markdown into the body of the page."""
    out = []
    lines = markdown.splitlines()
    index = 0

    def collect_paragraph(start: int) -> tuple[str, int]:
        """Gathers a paragraph: every line until a blank one."""
        parts = []
        look = start
        while look < len(lines) and lines[look].strip():
            parts.append(lines[look].strip())
            look += 1
        return " ".join(parts), look

    while index < len(lines):
        line = lines[index].rstrip()

        if not line.strip():
            index += 1
            continue

        if line.startswith("# "):
            out.append(f"  <h1>{inline(line[2:])}</h1>")
            index += 1
            continue

        if line.startswith("## "):
            out.append(f"  <h2>{inline(line[3:])}</h2>")
            index += 1
            continue

        if line.startswith("- "):
            items = []
            while index < len(lines):
                candidate = lines[index].rstrip()
                if not candidate.strip():
                    # A blank line may end the list, or may just be a
                    # paragraph break inside it. The next non-blank line
                    # decides.
                    look = index
                    while look < len(lines) and not lines[look].strip():
                        look += 1
                    if look < len(lines) and lines[look].startswith("- "):
                        index = look
                        continue
                    index += 1
                    break
                if not candidate.startswith("- "):
                    break

                # An item may wrap onto the following lines. They are the
                # lines that are indented but do not start a new item.
                text = candidate[2:].strip()
                index += 1
                while index < len(lines):
                    following = lines[index].rstrip()
                    if not following.strip():
                        break
                    if following.startswith("- ") or following.startswith("#"):
                        break
                    if not following.startswith(" "):
                        break
                    text += " " + following.strip()
                    index += 1

                items.append(f"    <li>{inline(text)}</li>")

            out.append("  <ul>")
            out.extend(items)
            out.append("  </ul>")
            continue

        # A paragraph: every line until a blank one.
        text, index = collect_paragraph(index)
        out.append(f"  <p>{inline(text)}</p>")

    return "\n".join(out)


def page(body: str) -> str:
    """The whole page."""
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Darts Coach Privacy Policy</title>
  <style>
{STYLE}
  </style>
</head>
<body>
{body}
</body>
</html>
"""


def main() -> int:
    if not SOURCE.exists():
        print(f"Not found: {SOURCE}", file=sys.stderr)
        return 1

    markdown = SOURCE.read_text(encoding="utf-8")
    # The title is the first heading, which the page has of its own.
    markdown = re.sub(r"^#\s+.*\n", "", markdown, count=1)

    result = page(render(markdown))

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(result, encoding="utf-8")

    APP_COPY.parent.mkdir(parents=True, exist_ok=True)
    APP_COPY.write_text(result, encoding="utf-8")

    PAGES_DIR.mkdir(parents=True, exist_ok=True)
    PAGES_COPY.write_text(result, encoding="utf-8")
    PAGES_NOJEKYLL.write_text("", encoding="utf-8")

    print(f"Wrote {TARGET.relative_to(ROOT)}")
    print(f"Wrote {APP_COPY.relative_to(ROOT)}")
    print(f"Wrote {PAGES_COPY.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
