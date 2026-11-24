#!/usr/bin/env python3
"""Check shared gameplay can be compiled for BOTH Java 17 and 21, without MC."""
from pathlib import Path
import subprocess
import tempfile
import re

root=Path(__file__).resolve().parents[2]
sources=sorted((root/"src/shared/java").rglob("*.java"))
assert sources, "Missing shared Java source"
for path in sources:
    text=path.read_text(encoding="utf-8")
    assert not re.search(r"(?m)^\s*import\s+(?:static\s+)?(?:net\.minecraft|net\.minecraftforge|net\.neoforged|com\.github\.tartaricacid|com\.mojang)\.", text), path
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac","--release","17","-d",tmp,*map(str,sources)],check=True)
print(f"SHARED_JAVA17_PASS: {len(sources)} pure domain sources")
