import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java"
RESOURCES = ROOT / "src/main/resources"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


dimension_path = RESOURCES / "data/maid_weapon/dimension/contract_interior.json"
dimension_type_path = RESOURCES / "data/maid_weapon/dimension_type/contract_interior.json"
if not dimension_path.exists() or not dimension_type_path.exists():
    raise SystemExit("contract interior dimension resources are missing")

dimension = json.loads(dimension_path.read_text(encoding="utf-8"))
dimension_type = json.loads(dimension_type_path.read_text(encoding="utf-8"))
if dimension.get("type") != "maid_weapon:contract_interior":
    raise SystemExit("contract interior does not use its dedicated dimension type")
if dimension.get("generator", {}).get("type") != "minecraft:flat":
    raise SystemExit("contract interior must remain a bounded void-style flat dimension")
if dimension_type.get("fixed_time") != 6000 or dimension_type.get("natural") is not False:
    raise SystemExit("contract interior lost its calm fixed-time environment")
if dimension_type.get("bed_works") is not False or dimension_type.get("has_raids") is not False:
    raise SystemExit("contract interior regained normal-world respawn/raid behavior")

profile = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorProfile.java")
saved = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorSavedData.java")
builder = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorBuilder.java")
service = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorService.java")
events = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorEvents.java")
key_item = read("src/main/java/com/maidweapon/forge/item/ContractInteriorKeyItem.java")
mod_items = read("src/main/java/com/maidweapon/forge/init/ModItems.java")
creative = read("src/main/java/com/maidweapon/forge/init/ModCreativeTab.java")
architecture = read("docs/ARCHITECTURE.md")
assets = read("docs/INTERIOR_ASSETS.md")

for needle in [
    "MAX_SPACE_STAGE = 5",
    "MAX_WARMTH_STAGE = 6",
    "data.getLevel()",
    "data.getFavorability()",
]:
    if needle not in profile:
        raise SystemExit(f"interior progression profile is incomplete: {needle}")

for needle in [
    'DATA_NAME = "maid_weapon_contract_interiors"',
    "CELL_SPACING = 1024",
    "getOrCreate(String bindingId)",
    "markBuilt(String bindingId, int spaceStage)",
]:
    if needle not in saved:
        raise SystemExit(f"interior plot persistence is incomplete: {needle}")

for needle in [
    "for (int stage = built + 1; stage <= profile.spaceStage(); stage++)",
    "applyWarmth(level, origin, profile.warmthStage())",
    "controlledDecoration",
]:
    if needle not in builder:
        raise SystemExit(f"interior builder lost staged/non-destructive behavior: {needle}")

for needle in [
    "MaidWeaponItem.ensureBindingId(contract)",
    "ContractMaidLifecycleService.manifest(player, contract, false)",
    "ContractMaidLifecycleService.capture(player, maid, contract, false)",
    "MaidWeaponItem.isOwner(contract, player)",
    "MaidWeaponItem.isContractSuperseded(contract)",
    "restoreReturn(player)",
]:
    if needle not in service:
        raise SystemExit(f"interior lifecycle invariant is missing: {needle}")

if "InfusedMaidDeploymentSystem" in service + events + builder + saved + profile:
    raise SystemExit("contract interior depends on the hotbar deployment state machine")

third_party_prefixes = (
    "import com.tacz.",
    "import mods.flammpfeil.",
    "import io.redspace.",
    "import com.Polarice3.",
    "import com.github.tartaricacid.",
)
for path in (JAVA / "com/maidweapon/forge/system/interior").rglob("*.java"):
    source = path.read_text(encoding="utf-8")
    for prefix in third_party_prefixes:
        if prefix in source:
            raise SystemExit(f"third-party API leaked into contract interior Core: {path}: {prefix}")

if '"contract_interior_key"' not in mod_items or "ContractInteriorKeyItem" not in mod_items:
    raise SystemExit("contract interior key is not registered")
if "CONTRACT_INTERIOR_KEY" not in creative:
    raise SystemExit("contract interior key is missing from the creative tab")
if "ContractInteriorService.enter" not in key_item or "ContractInteriorService.exit" not in key_item:
    raise SystemExit("contract interior key is not wired to the interior authority")

if "## Contract interiors" not in architecture:
    raise SystemExit("contract interior architecture is undocumented")
if "CC BY 4.0" not in assets or "Import policy" not in assets:
    raise SystemExit("external interior asset licensing/import policy is missing")

for language in ["en_us", "zh_cn"]:
    payload = json.loads(
        (RESOURCES / f"assets/maid_weapon/lang/{language}.json").read_text(encoding="utf-8")
    )
    for key in [
        "item.maid_weapon.contract_interior_key",
        "maid_weapon.tooltip.interior_key.use",
        "maid_weapon.message.interior.entered",
        "maid_weapon.message.interior.left",
    ]:
        if key not in payload:
            raise SystemExit(f"{language} is missing {key}")

print("Validated contract interior allocation, progression, lifecycle, resources and asset policy.")
