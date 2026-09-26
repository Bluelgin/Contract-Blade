from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


data = read("src/main/java/com/maidweapon/common/data/MaidWeaponData.java")
serializer = read("src/main/java/com/maidweapon/common/data/MaidWeaponDataSerializer.java")
weapon = read("src/main/java/com/maidweapon/forge/item/MaidWeaponItem.java")
config = read("src/main/java/com/maidweapon/common/MaidWeaponConfig.java")
bond = read("src/main/java/com/maidweapon/forge/event/MaidBondCombatHandler.java")
combat = read("src/main/java/com/maidweapon/forge/event/ContractCombatEventHandler.java")
magic = read("src/main/java/com/maidweapon/forge/compat/TripleMagicCompat.java")
helper = read("src/main/java/com/maidweapon/forge/compat/TouhouLittleMaidHelper.java")
overlay = read("src/main/java/com/maidweapon/forge/client/MaidFavorabilityOverlay.java")

if "MAX_RESONANCE = 200" not in data or 'KEY_RESONANCE = "ContractResonance"' not in serializer:
    raise SystemExit("Contract resonance storage is missing or has the wrong range")
if "maidTag.contains(MaidWeaponDataSerializer.KEY_RESONANCE)" not in weapon:
    raise SystemExit("Legacy contracts do not migrate to full resonance")
if ": MaidWeaponData.MAX_RESONANCE" not in weapon:
    raise SystemExit("Legacy resonance migration must default to 200")

for path, source in {
    "MaidBondCombatHandler": bond,
    "ContractCombatEventHandler": combat,
    "TripleMagicCompat": magic,
}.items():
    if "addFavorability(" in source or "reduceFavorability(" in source:
        raise SystemExit(f"{path} still mutates precious TLM favorability")

if 'defineInRange("favorabilityBaseMultiplierV2", 1.0' not in config:
    raise SystemExit("Zero favorability still weakens contract damage")
if 'defineInRange("favorabilityMaxBonusV2", 0.25' not in config:
    raise SystemExit("Full-favorability reward is not the agreed +25%")
if "getResonance()" not in overlay or "SHOW_RESONANCE_HUD" not in overlay:
    raise SystemExit("HUD still displays favorability instead of contract resonance")
if "syncFavorabilityFromMaid" not in helper:
    raise SystemExit("Manifested TLM favorability is not mirrored back to the weapon")

recall = helper.split(
    "public static boolean convertMaidToWeapon(Player player, Entity entity, ItemStack weaponStack,",
    1,
)[1].split("public static boolean convertWeaponToMaid", 1)[0]
if "setMaidFavorability(entity" in recall:
    raise SystemExit("Weapon data still overwrites TLM favorability before recall")

print("Validated resonance migration, recovery/combat rules, favorability isolation, and HUD")
