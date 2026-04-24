import os
from PIL import Image, ImageDraw, ImageOps

def generate_icons(source_path, res_path):
    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    if not os.path.exists(source_path):
        print(f"Source file {source_path} not found.")
        return

    source_img = Image.open(source_path).convert("RGBA")

    for folder, size in sizes.items():
        folder_path = os.path.join(res_path, folder)
        os.makedirs(folder_path, exist_ok=True)

        # Generate Square Icon
        square_icon = source_img.resize((size, size), Image.Resampling.LANCZOS)
        square_icon.save(os.path.join(folder_path, 'ic_launcher.png'))
        print(f"Generated {folder}/ic_launcher.png")

        # Generate Round Icon
        mask = Image.new('L', (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)

        round_icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        round_icon.paste(square_icon, (0, 0), mask=mask)
        round_icon.save(os.path.join(folder_path, 'ic_launcher_round.png'))
        print(f"Generated {folder}/ic_launcher_round.png")

import sys

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/generate_icons.py <source_image_path>")
        sys.exit(1)

    source = sys.argv[1]
    target_res = "app/src/main/res"
    generate_icons(source, target_res)
