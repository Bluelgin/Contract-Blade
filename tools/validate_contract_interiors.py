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
terrain_builder = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorTerrainBuilder.java")
terrain_theme = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorTerrainTheme.java")
terrain_selection = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorSelectionService.java")
terrain_preview = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorTerrainPreview.java")
player_commands = read("src/main/java/com/maidweapon/forge/event/ContractInteriorCommand.java")
service = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorService.java")
events = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorEvents.java")
gallery = read("src/main/java/com/maidweapon/forge/system/interior/ContractInteriorGallery.java")
commands = read("src/main/java/com/maidweapon/forge/event/MaidWeaponCommand.java")
deployment = read("src/main/java/com/maidweapon/forge/system/InfusedMaidDeploymentSystem.java")
key_item = read("src/main/java/com/maidweapon/forge/item/ContractInteriorKeyItem.java")
mod_items = read("src/main/java/com/maidweapon/forge/init/ModItems.java")
creative = read("src/main/java/com/maidweapon/forge/init/ModCreativeTab.java")
architecture = read("docs/ARCHITECTURE.md")
assets = read("docs/INTERIOR_ASSETS.md")
renderer = read("tools/render_contract_interior_world.py")
mods_toml = read("src/main/resources/META-INF/mods.toml")
build_gradle = read("build.gradle")
preview_workflow = read(".github/workflows/interior-preview.yml")
rcon = read("tools/rcon_command.py")

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
    "TerrainTheme",
    "TerrainSeed",
    "GeneratedStage",
    "chooseTerrain(String bindingId, String themeId)",
    "markGenerated(String bindingId, int spaceStage)",
]:
    if needle not in saved:
        raise SystemExit(f"interior plot persistence is incomplete: {needle}")

for needle in [
    "for (int stage = built + 1; stage <= profile.spaceStage(); stage++)",
    "applyWarmth(level, origin, profile.warmthStage())",
    "controlledDecoration",
    "buildGround(level, o, 12, 20)",
    "previousRadius",
    "buildBoundary",
    "buildGabledRoof",
    "Blocks.DEEPSLATE_TILE_STAIRS",
    "Blocks.BAMBOO_MOSAIC",
    "fillGableEndsZ",
    "fillGableEndsX",
    "for (int y = -4; y <= 20; y++)",
    "placeNaturalPathBlock",
    "clearGeneratedWallBlock",
    "landscapeHash",
    "paintOuterRingPatch",
    "scatterOuterRingGroundCover",
    "buildBambooGrove",
    "buildOpenPavilion",
    "buildPond",
]:
    if needle not in builder:
        raise SystemExit(f"interior builder lost staged/non-destructive behavior: {needle}")
if "DEPSLATE_" in builder:
    raise SystemExit("interior builder contains misspelled deepslate block constants")


for needle in [
    "case 1 -> 16",
    "case 2 -> 24",
    "case 3 -> 32",
    "case 4 -> 40",
    "default -> 52",
    "for (int stage = generated + 1; stage <= profile.spaceStage(); stage++)",
    "saved.markGenerated(bindingId, stage)",
    "previousRadius",
    "Preserve anything a player somehow built beyond the old barrier",
    "if (stage == 1)",
    "Blocks.BARRIER",
]:
    if needle not in terrain_builder:
        raise SystemExit(f"real contract terrain progression is incomplete: {needle}")

for theme in [
    "PLAINS_GARDEN",
    "SAKURA_GARDEN",
    "BAMBOO_GROVE",
    "LAKE_ISLET",
    "HILL_GARDEN",
]:
    if theme not in terrain_theme:
        raise SystemExit(f"built-in contract terrain theme is missing: {theme}")

for needle in [
    "/contractinterior choose ",
    "ClickEvent.Action.RUN_COMMAND",
    "saved.chooseTerrain(bindingId, theme.id())",
]:
    if needle not in terrain_selection:
        raise SystemExit(f"first-entry terrain selection is incomplete: {needle}")

for theme_id in [
    "plains_garden",
    "sakura_garden",
    "bamboo_grove",
    "lake_islet",
    "hill_garden",
]:
    if theme_id not in player_commands:
        raise SystemExit(f"player terrain selection command is missing: {theme_id}")

if "ContractInteriorBuilder.buildSnapshot" not in gallery:
    raise SystemExit("example-home gallery no longer owns the old house snapshots")

for needle in [
    "MaidWeaponItem.ensureBindingId(contract)",
    "ContractLifecycleService.manifest(player, contract, false)",
    "ContractLifecycleService.capture(player, maid, contract, false)",
    "MaidWeaponItem.isOwner(contract, player)",
    "MaidWeaponItem.isContractSuperseded(contract)",
    "restoreReturn(player)",
    "resumeInteriorMaid",
    "existingMaid.level().dimension().equals(INTERIOR_LEVEL)",
    "ContractInteriorSelectionService.prompt(player, contract)",
    "ContractInteriorTerrainBuilder.ensureGenerated",
]:
    if needle not in service:
        raise SystemExit(f"interior lifecycle invariant is missing: {needle}")
if "ContractInteriorBuilder.ensureBuilt" in service:
    raise SystemExit("real player interiors regressed to the generated example-home builder")

if "InfusedMaidDeploymentSystem" in service + events + builder + terrain_builder + saved + profile:
    raise SystemExit("contract interior depends on the hotbar deployment state machine")

if "ContractInteriorService.INTERIOR_LEVEL" not in deployment:
    raise SystemExit("hotbar deployment can adopt/recall the maid while inside a contract interior")


for needle in [
    "BASE_X = -8192",
    "BASE_Z = -4096",
    "STAGE_SPACING = 144",
    "ContractInteriorBuilder.buildSnapshot",
    "if (isStageBuilt(level, origin)) continue;",
    "public static boolean rebuild",
]:
    if needle not in gallery:
        raise SystemExit(f"contract interior gallery invariant is missing: {needle}")

if (
    "clearFirst) {" not in builder
    or "clearSnapshotArea(level, origin, safeStage)" not in builder
    or "radiusForStage(stage) + 6" not in builder
):
    raise SystemExit("explicit gallery rebuild cannot reset a stage snapshot efficiently")
if "ContractInteriorGallery.open" not in commands:
    raise SystemExit("contract interior gallery command is missing")
if "ContractInteriorGallery.visitStage" not in commands:
    raise SystemExit("contract interior stage jump command is missing")
if "ContractInteriorGallery.rebuild" not in commands:
    raise SystemExit("contract interior rebuild command is missing")
if "ContractInteriorService.isGallerySession" not in commands:
    raise SystemExit("contract interior gallery leave command is missing")
if 'TAG_GALLERY = "Gallery"' not in service:
    raise SystemExit("gallery sessions are not isolated from real contract sessions")
if "ContractInteriorGallery.overviewSpawn()" not in service:
    raise SystemExit("gallery void recovery does not return to the gallery overview")


if "placeItemBackInInventory(protectedStack)" not in events:
    raise SystemExit("cancelled interior contract toss can delete the contract stack")
if "placeItemBackInInventory(protectedStack)" not in deployment:
    raise SystemExit("cancelled deployed-contract toss can delete the contract stack")

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


compile(renderer, "tools/render_contract_interior_world.py", "exec")
for needle in [
    'dimensions" / "maid_weapon" / "contract_interior"',
    'f"r.{region_x}.{region_z}.mca"',
    '"block_states"',
    "values_per_long = 64 // bits",
    "def render_iso",
    "def render_top",
    "def render_iso_png",
    "def render_top_png",
    "class Raster",
    "GALLERY_STAGE_RADII",
    "default is stage radius + 2 blocks",
    "--center-x",
    "--center-z",
    "def centered_bounds",
    "name == \"minecraft:bamboo\"",
    "choices=(0, 90, 180, 270)",
    "def rotate_blocks",
    "name.endswith(\"_slab\")",
    "def block_visual_shape",
]:
    if needle not in renderer:
        raise SystemExit(f"offline contract interior renderer is incomplete: {needle}")


compile(rcon, "tools/rcon_command.py", "exec")
for needle in [
    "encode_packet",
    "receive_packet",
    "RCON authentication failed",
]:
    if needle not in rcon:
        raise SystemExit(f"preview RCON helper is incomplete: {needle}")

for needle in [
    "Contract Interior Preview",
    "-PgalleryPreview=true",
    "maidweapon interior gallery generate 4",
    "maidweapon interior terrain-preview generate",
    "terrain_sakura_stage_5_iso.png",
    "--center-x",
    "save-all flush",
    "render_contract_interior_world.py",
    "--format png",
    "actions/upload-artifact@v4",
    "stage_5_iso_r${rotation}.png",
    "--rotation \"$rotation\"",
]:
    if needle not in preview_workflow:
        raise SystemExit(f"real-save preview workflow is incomplete: {needle}")

if "galleryPreviewMode" not in build_gradle or "tlm_mandatory" not in build_gradle:
    raise SystemExit("gallery preview build mode is not wired through Gradle resources")
if 'mandatory = ${tlm_mandatory}' not in mods_toml:
    raise SystemExit("TLM preview-only mandatory override is missing")
if (
    "private static int generateInteriorGallery" not in commands
    or "ContractInteriorGallery.rebuild(" not in commands
    or "source.getServer()" not in commands
):
    raise SystemExit("server-console gallery generation command is missing")
if "public static int rebuild(MinecraftServer server" not in gallery:
    raise SystemExit("gallery cannot be generated headlessly by preview CI")

for language in ["en_us", "zh_cn"]:
    payload = json.loads(
        (RESOURCES / f"assets/maid_weapon/lang/{language}.json").read_text(encoding="utf-8")
    )
    for key in [
        "item.maid_weapon.contract_interior_key",
        "maid_weapon.tooltip.interior_key.use",
        "maid_weapon.message.interior.entered",
        "maid_weapon.message.interior.left",
        "maid_weapon.message.interior.choose_theme_title",
        "maid_weapon.interior.theme.sakura_garden",
    ]:
        if key not in payload:
            raise SystemExit(f"{language} is missing {key}")

print("Validated contract interior allocation, progression, lifecycle, resources and asset policy.")
