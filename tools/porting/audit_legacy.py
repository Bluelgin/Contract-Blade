#!/usr/bin/env python3
"""Inventory port blockers deterministically. Reports, never rewrites."""
from pathlib import Path
from collections import Counter, defaultdict
root = Path(__file__).resolve().parents[2]
needle = {
    "forge_packages": "net.minecraftforge.",
    "old_item_tag": ".getOrCreateTag(",
    "old_item_settag": ".setTag(",
    "old_item_gettag": ".getTag(",
    "forge_capabilities": "ForgeCaps",
    "network_simplechannel": "SimpleChannel",
    "registryobject": "RegistryObject<",
    "saveddata": "extends SavedData",
    "mixin": "@Mixin(",
    "tlm_native_api": "com.github.tartaricacid.touhoulittlemaid.",
}
files = sorted((root / "src/main/java").rglob("*.java"))
groups = defaultdict(list)
for file in files:
    text = file.read_text(encoding="utf-8")
    relative = str(file.relative_to(root))
    for name, term in needle.items():
        if term in text:
            groups[name].append(relative)
print(f"Legacy source count: {len(files)}")
for name in needle:
    print(f"{name}: {len(groups[name])} files")
print("Audit is informational. A source occurrence is NOT automatically a required code rewrite.")
