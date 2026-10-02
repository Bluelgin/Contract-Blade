"""Guard the contract table's block-item display inheritance."""
import json
from pathlib import Path


def main():
    assets = Path(__file__).resolve().parents[1] / "src/main/resources/assets/maid_weapon"
    item = json.loads((assets / "models/item/maid_injector.json").read_text(encoding="utf-8"))
    block = json.loads((assets / "models/block/maid_injector.json").read_text(encoding="utf-8"))
    assert item["parent"] == "maid_weapon:block/maid_injector", "Item must use the actual table geometry"
    assert block["parent"] == "minecraft:block/block", "Table must inherit vanilla GUI/hand/ground transforms"
    assert "display" not in item and "display" not in block, "Do not override inherited display transforms"
    assert len(block["elements"]) == 3, "Keep base, pedestal and tabletop geometry"
    for texture in block["textures"].values():
        namespace, path = texture.split(":", 1)
        assert namespace == "maid_weapon"
        assert (assets / "textures" / (path + ".png")).is_file(), f"Missing texture: {texture}"
    print("Contract table item model validation passed")


if __name__ == "__main__":
    main()
