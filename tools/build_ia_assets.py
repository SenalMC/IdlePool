from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
WORKING = ROOT / "itemsadder-pack" / "working"
CONTENTS = ROOT / "itemsadder-pack" / "contents" / "idlepool" / "textures"

def trim(image: Image.Image) -> Image.Image:
    alpha = image.getchannel("A")
    box = alpha.getbbox()
    if box is None:
        raise ValueError("Image has no visible pixels")
    return image.crop(box)


def fit_icon(source: Path, size: int, padding: int) -> Image.Image:
    image = trim(Image.open(source).convert("RGBA"))
    available = size - padding * 2
    scale = min(available / image.width, available / image.height)
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    image = image.resize((width, height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(image, ((size - width) // 2, (size - height) // 2))
    return canvas


def main() -> None:
    parser = argparse.ArgumentParser(description="Build production IdlePool ItemsAdder assets")
    parser.parse_args()

    item_dir = CONTENTS / "item"
    hud_dir = CONTENTS / "font" / "icons"
    item_dir.mkdir(parents=True, exist_ok=True)
    hud_dir.mkdir(parents=True, exist_ok=True)

    item_sources = {
        "start_button": "start_button.png",
        "info_button": "info_button.png",
        "rewards_button": "rewards_button.png",
        "afk_coin": "afk_coin.png",
    }
    for name, filename in item_sources.items():
        fit_icon(WORKING / filename, 32, 1).save(item_dir / f"{name}.png", optimize=True)

    hud_sources = {
        "clock": "clock.png",
        "gift": "gift.png",
        "coin": "afk_coin.png",
    }
    for name, filename in hud_sources.items():
        icon = fit_icon(WORKING / filename, 16, 1)
        icon.save(hud_dir / f"{name}.png", optimize=True)


if __name__ == "__main__":
    main()
