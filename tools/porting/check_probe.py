#!/usr/bin/env python3
"""No dependency required: reject unsafe or mislabeled port artifacts."""
from pathlib import Path
from collections import Counter
root = Path(__file__).resolve().parents[2]
build = (root / "build.gradle").read_text(encoding="utf-8")
meta = (root / "src/port/resources/META-INF/neoforge.mods.toml").read_text(encoding="utf-8")
assert "src/port/java" in build and "src/port/resources" in build, "legacy Java/resources not isolated"
assert 'modId = "maid_weapon"' in meta, "formal native content must use stable mod ID"
assert "DEV-INCOMPLETE" in build and "NOT FOR PLAY" in meta
assert (root / "src/main/java/com/maidweapon/forge/MaidWeaponMod.java").is_file()
assert (root / "src/main/resources/META-INF/mods.toml").is_file()
assert (root / "src/port/java/com/maidweapon/port/storage/ContractStackDataAccess.java").is_file()
sources = list((root / "src/main/java").rglob("*.java"))
print(f"PORT_PROBE_BOUNDARY_PASS: preserved {len(sources)} old Java sources")
