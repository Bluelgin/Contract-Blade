from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


# Keep the data/domain layer free from implementation-layer dependencies.
for folder in [
    JAVA / "com/maidweapon/common/data",
    JAVA / "com/maidweapon/common/sin",
    JAVA / "com/maidweapon/common/system",
    JAVA / "com/maidweapon/common/legacy",
]:
    for path in folder.rglob("*.java"):
        source = read(path)
        if "import com.maidweapon.forge." in source:
            raise SystemExit(f"common layer depends on forge implementation: {path}")

# Optional mod APIs belong in forge/compat only.
third_party_prefixes = (
    "import com.tacz.",
    "import mods.flammpfeil.",
    "import io.redspace.",
    "import com.Polarice3.",
    "import com.github.tartaricacid.",
)
for path in JAVA.rglob("*.java"):
    rel = path.relative_to(JAVA).as_posix()
    if "/forge/compat/" in f"/{rel}":
        continue
    source = read(path)
    for prefix in third_party_prefixes:
        if prefix in source:
            raise SystemExit(f"third-party API leaked outside compat layer: {rel}: {prefix}")

# Contract tooltip key state and presentation are client-only.
item_source = read(JAVA / "com/maidweapon/forge/item/MaidWeaponItem.java")
if "Screen.hasShiftDown()" in item_source or "flag.isAdvanced()" in item_source:
    raise SystemExit("MaidWeaponItem regained client key-state or F3+H tooltip logic")

legacy_tooltip_handler = JAVA / "com/maidweapon/forge/event/MaidInfusionEventHandler.java"
if legacy_tooltip_handler.exists():
    raise SystemExit("legacy generic contract tooltip handler was restored")

tooltip_handler = read(
    JAVA / "com/maidweapon/forge/client/tooltip/ContractTooltipHandler.java"
)
tooltip_composer = read(
    JAVA / "com/maidweapon/forge/client/tooltip/ContractTooltipComposer.java"
)
if "value = Dist.CLIENT" not in tooltip_handler:
    raise SystemExit("contract tooltip handler is no longer client-only")
if "Screen.hasShiftDown()" not in tooltip_composer:
    raise SystemExit("contract tooltip composer lost real Shift expansion")

# The bootstrap must not own gameplay interaction logic.
bootstrap = read(JAVA / "com/maidweapon/forge/MaidWeaponMod.java")
if "InteractMaidEvent" in bootstrap or "addListenerMethod" in bootstrap:
    raise SystemExit("Forge bootstrap regained reflected maid interaction authority")

# These are intentionally kept below 'god class' size. Raise the threshold only
# after extracting another responsibility, never to silence the guard.
helper = read(JAVA / "com/maidweapon/forge/compat/TouhouLittleMaidHelper.java")
deployment = read(JAVA / "com/maidweapon/forge/system/InfusedMaidDeploymentSystem.java")
if len(helper.splitlines()) > 220:
    raise SystemExit("TouhouLittleMaidHelper stopped being a thin facade")
if len(deployment.splitlines()) > 650:
    raise SystemExit("InfusedMaidDeploymentSystem is growing back into a god class")
for optional_facade in ("TaczCompat", "TripleMagicCompat", "ContractCombatTaskRouter"):
    if optional_facade in deployment:
        raise SystemExit(f"deployment state machine regained optional-mod knowledge: {optional_facade}")

# Development-only registry aliases may remain for old worlds, but not in the UI.
creative = read(JAVA / "com/maidweapon/forge/init/ModCreativeTab.java")
if "TEST_SLASHBLADE" in creative:
    raise SystemExit("legacy development blade is visible in the creative tab")

# Compat classes must not advertise unfinished TODO implementations.
for path in (JAVA / "com/maidweapon/forge/compat").rglob("*.java"):
    if "TODO:" in read(path):
        raise SystemExit(f"unfinished compatibility stub remains: {path}")

print("Architecture boundaries validated.")
