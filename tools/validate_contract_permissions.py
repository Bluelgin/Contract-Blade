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
    "inventory owner gates": (
        "src/main/java/com/maidweapon/forge/event/MaidInfusionEventHandler.java",
        "MaidWeaponItem.isOwner",
        2,
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
gui_gate = "hasMaidData(stack) && !player.isShiftKeyDown()"
if gui_gate not in weapon:
    raise SystemExit("Normal right-click must pass through to TLM's manifested-maid GUI")
if "ContractInteractionService.capture" not in weapon or "MaidWeaponItem.isOwner" not in interaction:
    raise SystemExit("Contract interaction authority is not centralized")

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
if "if (slot.container == player.getInventory()) continue;" not in deployment:
    raise SystemExit("Container-close recall must ignore the player's own inventory slots")
if "if (TripleMagicCompat.isPhantom(stack)) continue;" not in deployment:
    raise SystemExit("TLM accessory page changes can still recall the phantom contract weapon")
if deployment.count("if (TripleMagicCompat.isPhantom(stack)) continue;") < 2:
    raise SystemExit("Phantom contracts must also be ignored by self-storage rescue")
if "rescueSelfStoredContract(player)" not in deployment:
    raise SystemExit("A manifested maid can still consume a contract stored in her own inventory")
if 'getMethod("getMaid")' not in deployment or "returnContractToPlayer" not in deployment:
    raise SystemExit("Self-contract inventory rescue is not tied to the opened TLM maid")
recall_body = deployment.split("private static boolean recall(Player player, String maidId)", 1)[1]
if "rescueSelfStoredContract(player);" not in recall_body.split("private static", 1)[0]:
    raise SystemExit("Recall can still discard a maid before rescuing her own contract stack")
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
if "TripleMagicCompat.clearPhantoms(living, weapon)" not in deployment:
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
if "SlashBladeCompat.isMatchingPhantom(source, current)" not in magic:
    raise SystemExit("SlashBlade phantoms are still replaced instead of preserving combat progress")

print("Validated contract ownership, TLM GUI pass-through, magic/SlashBlade tasks, progress sync, and optional bridges")
