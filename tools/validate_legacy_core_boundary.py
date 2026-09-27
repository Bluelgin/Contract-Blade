from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


bond = read("src/main/java/com/maidweapon/forge/event/MaidBondCombatHandler.java")
combat = read("src/main/java/com/maidweapon/forge/event/ContractCombatEventHandler.java")
tooltip = read("src/main/java/com/maidweapon/forge/client/tooltip/ContractTooltipComposer.java")
serializer = read("src/main/java/com/maidweapon/common/data/MaidWeaponDataSerializer.java")
legacy = read("src/main/java/com/maidweapon/common/legacy/LegacySinArchive.java")
shim = read("src/main/java/com/maidweapon/forge/system/SinFragmentSystem.java")

# Old saves retain their serialized field.
if 'KEY_EMBEDDED_SINS = "EmbeddedSins"' not in serializer:
    raise SystemExit("legacy seven-sins NBT would be lost during rewrite")
if "LegacySinArchive.hasData(data)" not in tooltip:
    raise SystemExit("old Part data is no longer surfaced as archived metadata")

# Core gameplay must not execute the paused Part rules.
for name, source in {
    "MaidBondCombatHandler": bond,
    "ContractCombatEventHandler": combat,
}.items():
    if "SinType." in source or "SinFragmentSystem" in source or ".hasSin(" in source:
        raise SystemExit(f"{name} still executes legacy Part gameplay")

consume = shim.split("public static void consumeOnKill", 1)[1]
if "Intentionally no-op" not in consume:
    raise SystemExit("legacy sin runtime shim can mutate Core gameplay again")

if "must never affect Core combat" not in legacy:
    raise SystemExit("legacy data boundary is not explicitly documented")

print("Legacy Part data is preserved but inert in Contract Blade Core.")
