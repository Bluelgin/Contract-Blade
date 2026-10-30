"""Export original pixel SVG sources with Inkscape. Run from any directory."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[2]
for name, target in [('oath_ring', 'item'), ('oath_backlash', 'mob_effect')]:
    source = root / 'art/oath' / (name + '.svg')
    output = root / 'src/main/resources/assets/maid_weapon/textures' / target / (name + '.png')
    subprocess.run(['inkscape', str(source), '--export-type=png', '--export-filename=' + str(output)], check=True)
