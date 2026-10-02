from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


checks = {
    "combat owner gates": (
        "src/main/java/com/maidweapon/forge/event/ContractCombatEventHandler.java",
        "MaidWeaponItem.isOwner",
        5,
    ),
    "contract tooltip owner gate": (
        "src/main/java/com/maidweapon/forge/client/tooltip/ContractTooltipComposer.java",
        "MaidWeaponItem.isOwner",
        1,
    ),
    "contract interior owner gate": (
        "src/main/java/com/maidweapon/forge/system/interior/ContractInteriorService.java",
        "MaidWeaponItem.isOwner",
        1,
    ),
    "deployment owner gates": (
        "src/main/java/com/maidweapon/forge/system/InfusedMaidDeploymentSystem.java",
        "MaidWeaponItem.isOwner",
        3,
    ),
    "binding-table owner gate": (
        "src/main/java/com/maidweapon/forge/menu/MaidInjectorMenu.java",
        "MaidWeaponItem.isOwner",
        1,
    ),
}

for name, (path, needle, minimum) in checks.items():
    count = read(path).count(needle)
    if count < minimum:
        raise SystemExit(f"{name}: expected at least {minimum} occurrences of {needle}, found {count}")

deployment = read("src/main/java/com/maidweapon/forge/system/InfusedMaidDeploymentSystem.java")
if "hasDeployedMaid" not in deployment or "deployed_contract_transfer_blocked" not in deployment:
    raise SystemExit("Manifested-contract drop protection is missing")

weapon = read("src/main/java/com/maidweapon/forge/item/MaidWeaponItem.java")
interaction = read("src/main/java/com/maidweapon/forge/system/contract/ContractInteractionService.java")
lifecycle = read("src/main/java/com/maidweapon/forge/system/contract/ContractLifecycleService.java")
binding_table = read("src/main/java/com/maidweapon/forge/menu/MaidInjectorMenu.java")
maid_interaction = read("src/main/java/com/maidweapon/forge/event/MaidInteractionHandler.java")
commands = read("src/main/java/com/maidweapon/forge/event/MaidWeaponCommand.java")
gui_gate = "if (hasMaidData(stack)) return InteractionResult.PASS;"
if gui_gate not in weapon:
    raise SystemExit("Bound Contract Blade right-click must pass through to TLM/deployment")
if "ContractInteractionService.capture" not in weapon or "MaidWeaponItem.isOwner" not in interaction:
    raise SystemExit("Contract interaction authority is not centralized")

if "ContractInteractionService.toggleHeld" in weapon:
    raise SystemExit("Dedicated Contract Blade regained a second manual summon/recall gesture")
if "if (hasMaidData(stack)) return InteractionResult.PASS;" not in weapon:
    raise SystemExit("Bound Contract Blade right-click must belong to TLM/deployment, not capture")
if "MaidInfusion.isContractBlade(" in deployment:
    raise SystemExit("Dedicated Contract Blades are still excluded from Contract Table deployment behavior")
for needle in [
    "ContractLifecycleService.capture",
    "ContractLifecycleService.toggle",
]:
    if needle not in interaction:
        raise SystemExit(f"Contract interaction no longer delegates lifecycle authority: {needle}")
for forbidden in [
    "ContractWeaponLocator",
    "convertWeaponToMaid",
    "convertMaidToWeapon",
]:
    if forbidden in interaction:
        raise SystemExit(f"Contract interaction regained a second lifecycle implementation: {forbidden}")

for needle in [
    "MaidInfusion.containsMaid(contract)",
    "ContractWeaponLocator.findManifestedMaid",
    "TouhouLittleMaidHelper.convertWeaponToMaid",
    "TouhouLittleMaidHelper.convertMaidToWeapon",
    "TouhouLittleMaidHelper.infuseFromFilm",
    "TouhouLittleMaidHelper.extractMaidToFilm",
]:
    if needle not in lifecycle:
        raise SystemExit(f"Unified contract lifecycle is incomplete: {needle}")

for needle in [
    "ContractLifecycleService.extractToFilm",
    "ContractLifecycleService.infuseFromFilm",
]:
    if needle not in binding_table:
        raise SystemExit(f"Contract Table bypasses unified lifecycle: {needle}")

if "if (MaidInfusion.isInfused(mainHand)) {" not in maid_interaction:
    raise SystemExit("Bound weapon attacks can still bypass generic deployment recall")
if "ContractInteractionService.toggleHeld" in maid_interaction:
    raise SystemExit("Bound SlashBlade regained a second manual summon/recall path")
if "PlayerInteractEvent.LeftClickEmpty" in maid_interaction:
    raise SystemExit("Bound SlashBlade still owns a manual empty-space recall gesture")
if "ContractLifecycleService.manifest(player, weapon, false," not in deployment:
    raise SystemExit("Generic deployment bypasses unified contract manifest lifecycle")
if "ContractLifecycleService.capture(player, maid, weapon, false)" not in deployment:
    raise SystemExit("Generic deployment bypasses unified contract recall lifecycle")

if "instanceof MaidWeaponItem" in commands:
    raise SystemExit("Debug contract commands regressed to dedicated MaidWeaponItem-only handling")
if commands.count("editableContract(source)") < 3:
    raise SystemExit("Level/favorability/resonance commands do not share the generic contract gate")
if "MaidInfusion.isInfused(stack)" not in commands or "MaidWeaponItem.isOwner(stack, player)" not in commands:
    raise SystemExit("Debug contract command gate is missing generic contract/owner validation")

if "favorability > 0" in deployment or "getFavorability() <= 0" in deployment:
    raise SystemExit("Zero favorability must not block or recall contract manifestation")

infusion = read("src/main/java/com/maidweapon/forge/item/MaidInfusion.java")
slashblade = read("src/main/java/com/maidweapon/forge/compat/SlashBladeCompat.java")
mods_toml = read("src/main/resources/META-INF/mods.toml")
build_gradle = read("build.gradle")
if "SlashBladeCompat.isSlashBlade(stack)" not in infusion:
    raise SystemExit("Contract Binding Table does not recognize SlashBlade items")
if "mods.flammpfeil.slashblade.item.ItemSlashBlade" not in slashblade:
    raise SystemExit("SlashBlade ItemSlashBlade compatibility bridge is missing")
if 'modId = "slashblade"' not in mods_toml or "mandatory = false" not in mods_toml:
    raise SystemExit("SlashBlade must remain an optional mod dependency")
if 'modId = "true_power_of_maid"' not in mods_toml:
    raise SystemExit("TLM: True POWER must be declared as an optional dependency")
if 'modId = "native_power_of_maid"' not in mods_toml:
    raise SystemExit("TLM: Native POWER must be declared as an optional dependency")
if "compatTestMode == 'native-power'" not in build_gradle or "tlm-native-power-1404371:7384912" not in build_gradle:
    raise SystemExit("TLM: Native POWER development runtime profile is missing")

deployment = read("src/main/java/com/maidweapon/forge/system/InfusedMaidDeploymentSystem.java")
transfer = read("src/main/java/com/maidweapon/forge/system/deployment/ContractTransferSafetyService.java")
if "if (slot.container == player.getInventory()) continue;" not in deployment:
    raise SystemExit("Container-close recall must ignore the player's own inventory slots")
if "ContractTransferSafetyService.isProjectionPhantom(stack)" not in deployment:
    raise SystemExit("TLM accessory page changes can still recall the phantom contract weapon")
if "if (TripleMagicCompat.isPhantom(stack)) continue;" not in transfer:
    raise SystemExit("Phantom contracts must also be ignored by self-storage rescue")
if "ContractTransferSafetyService.rescueSelfStoredContract(player)" not in deployment:
    raise SystemExit("A manifested maid can still consume a contract stored in her own inventory")
if 'getMethod("getMaid")' not in transfer or "returnToPlayer" not in transfer:
    raise SystemExit("Self-contract inventory rescue is not tied to the opened TLM maid")
recall_body = deployment.split("private static boolean recall(Player player, String maidId)", 1)[1]
if "ContractTransferSafetyService.rescueSelfStoredContract(player);" not in recall_body.split("private static", 1)[0]:
    raise SystemExit("Recall can still discard a maid before rescuing her own contract stack")

recovery_body = deployment.split("private static boolean processRecovery(Player player)", 1)[1].split(
    "private static", 1
)[0]
if recovery_body.index("recall(player, maidId)") > recovery_body.index("ContractRecoveryService.tick(player)"):
    raise SystemExit("Recovery must attempt recall before advancing timeout state")
recovery = read("src/main/java/com/maidweapon/forge/system/deployment/ContractRecoveryService.java")
if "MAID_AVAILABLE" in recovery:
    raise SystemExit("A loaded-but-unrecallable maid can bypass recovery timeout")
task_router = read("src/main/java/com/maidweapon/forge/system/deployment/ContractCombatTaskRouter.java")
if "TripleMagicCompat.usesMaidSpellTask(weapon)" not in task_router:
    raise SystemExit("Magic contract weapons must select Wan Fa Jie Tong's ranged task")
if "TripleMagicCompat.getMaidSpellRangedTaskId()" not in task_router:
    raise SystemExit("Wan Fa Jie Tong ranged task selection is still hard-coded")
if "MAGIC_TASK_FAILURE" not in task_router:
    raise SystemExit("Magic task failures are silent and cannot be diagnosed in a modpack")
if "SlashBladeCompat.usesMaidSlashBladeTask(weapon)" not in task_router:
    raise SystemExit("Supported SlashBlade maid addons do not select their dedicated task")
if "SlashBladeCompat.getMaidSlashBladeTaskId()" not in task_router:
    raise SystemExit("SlashBlade maid task UID selection is missing or hard-coded in deployment")
if "SLASHBLADE_TASK_FAILURE" not in task_router:
    raise SystemExit("True POWER task failures are silent and cannot safely fall back")
care = read("src/main/java/com/maidweapon/forge/system/MaidCareTaskSystem.java")
if "if (!hungry) return false;" not in care:
    raise SystemExit("Safe-care mode still overrides Native POWER while the owner is not hungry")
if "return switchIfNeeded(maid, FEED_TASK);" not in care:
    raise SystemExit("A failed feeding-task switch can still suppress weapon-specific combat")
runtime = read("src/main/java/com/maidweapon/forge/system/deployment/ContractMaidRuntimeService.java")
if "TripleMagicCompat.clearPhantoms(living, weapon)" not in runtime:
    raise SystemExit("Recall can discard final-tick SlashBlade progress")

magic = read("src/main/java/com/maidweapon/forge/compat/TripleMagicCompat.java")
if 'private static final String MAID_SPELL = "touhou_little_maid_spell"' not in magic:
    raise SystemExit("Wan Fa Jie Tong must use its real mod id")
if 'path.contains("wand") || path.contains("staff")' not in magic:
    raise SystemExit("Goety wand/staff recognition is incomplete")
if 'path.contains("staff") || path.contains("wand")' not in magic:
    raise SystemExit("Iron's Spells wand/staff recognition is incomplete")
if "isIronSpellContainer(stack)" not in magic:
    raise SystemExit("Iron's Spells spell-container API fallback is missing")
if "syncMaidSpellLoadout" not in magic or 'getMethod("initSpellBooks")' not in magic:
    raise SystemExit("Wan Fa Jie Tong spell-book refresh bridge is missing")
if "SpellCombatFarTask" not in magic or 'getField("UID")' not in magic:
    raise SystemExit("Wan Fa Jie Tong ranged task UID bridge is missing")
if 'modId = "touhou_little_maid_spell"' not in mods_toml:
    raise SystemExit("Wan Fa Jie Tong must be declared as an optional dependency")

for needle in [
    '"true_power_of_maid"',
    '"native_power_of_maid"',
    '"net.mrqx.slashblade.maidpower.task.TaskSlashBlade"',
    '"net.jfrx.slashblade.maidnativepower.task.TaskSlashBlade"',
    '"true_power_of_maid:slashblade_attack"',
    '"native_power_of_maid:slashblade_attack"',
    'getField("UID")',
    'getMethod("findTask", ResourceLocation.class)',
]:
    if needle not in slashblade:
        raise SystemExit(f"Dual SlashBlade maid-task bridge is missing: {needle}")
if "selectedTaskProvider" not in slashblade:
    raise SystemExit("Registered maid task provider is not cached after discovery")
if "syncPhantomProgress" not in slashblade or "(long) real + phantom - baseline" not in slashblade:
    raise SystemExit("SlashBlade phantom progress is not merged by delta")
if "getProudSoulCount" not in slashblade or "getKillCount" not in slashblade:
    raise SystemExit("SlashBlade ProudSoul and kill progress are not synchronized")
if "getRefine" not in slashblade or "getDamage" not in slashblade or "isBroken" not in slashblade:
    raise SystemExit("SlashBlade refine, durability, or broken state synchronization is missing")
if "MaidWeaponItem.clearMaidContract(copy)" not in magic:
    raise SystemExit("Combat phantoms still carry a second serialized maid contract")
projection = read("src/main/java/com/maidweapon/forge/compat/ContractEquipmentProjection.java")
if "SlashBladeCompat.isMatchingPhantom(source, current)" not in projection:
    raise SystemExit("SlashBlade phantoms are still replaced instead of preserving combat progress")

print("Validated contract ownership, TLM GUI pass-through, magic/SlashBlade tasks, progress sync, and optional bridges")
