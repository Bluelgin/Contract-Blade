"""Static safeguards supplement the opt-in real-provider server fixture."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / "src/main/java/com/maidweapon/forge"
bridge = (java / "compat/EpicFightCompat.java").read_text(encoding="utf-8")
router = (java / "system/deployment/ContractCombatTaskRouter.java").read_text(encoding="utf-8")
runtime = (java / "system/deployment/ContractMaidRuntimeService.java").read_text(encoding="utf-8")
fixture = (java / "compat/tlm/ContractEpicFightValidation.java").read_text(encoding="utf-8")
assert 'isLoaded("epicfight")' in bridge and 'isLoaded("ef_tlm")' in bridge
assert 'getField("CAPABILITY_ITEM")' in bridge and 'getField("CAPABILITY_ENTITY")' in bridge
assert 'capabilities.getMethod(' not in bridge, "Client-only signatures cannot be reflected on servers"
assert "getHoldingItemWeaponMotionBuilder" in bridge
assert "motions != api.fallbackTools" in bridge and "motions != api.fallbackFist" in bridge
for category in ('"BOW"', '"CROSSBOW"', '"SHIELD"', '"PICKAXE"', '"SHOVEL"', '"HOE"'):
    assert category in bridge
assert "!slashBladeMode && !magicMode && !taczMode" in router
assert "!SlashBladeCompat.isSlashBlade(weapon)" in router
assert "!TripleMagicCompat.isMagicCatalyst(weapon)" in router
assert runtime.index("INTERIOR_LEVEL)) return;") < runtime.index("equipPhantoms(")
assert "ContractWorkPolicy.release(weapon, maid)" in runtime
assert "if (!ContractWorkPolicy.combat(com.maidweapon.forge.compat.tlm.TlmProjectionBaubles.mode(maid))) return;" in router
work = (java / "system/deployment/ContractWorkPolicy.java").read_text(encoding="utf-8")
assert "mode.weapon() && com.maidweapon.common.ContractRulesConfig.AUTO_COMBAT.get()" in work
care = (java / "system/MaidCareTaskSystem.java").read_text(encoding="utf-8")
assert "onPlayerTick" not in care, "Care must not compete with deployment's combat task routing"
assert "switchIfNeeded(maid, ATTACK_TASK)" not in care
assert "selection.equals(SELECTIONS.get(maid))" in bridge
assert bridge.index("selection.equals(SELECTIONS.get(maid))") < bridge.index("api.motionBuilder.invoke(patch)")
assert "EpicFightCompat.clearSelection(maid)" in (java / "compat/ContractEquipmentProjection.java").read_text(encoding="utf-8")
assert 'if (!Boolean.getBoolean("contractblade.epicfight.validation")) return;' in fixture
for check in ("original task restored", "original equipment restored", "source data preserved",
              "ordinary fallback", "stable repeated routing", "200 maintenance ticks remain in Epic Fight",
              "temporary food cannot reset combat mode", "temporary empty hand cannot reset combat mode",
              "recall clears the session selection"):
    assert check in fixture
for locale in ("zh_cn", "en_us"):
    translations = json.loads((root / f"src/main/resources/assets/maid_weapon/lang/{locale}.json")
                              .read_text(encoding="utf-8"))
    assert "maid_weapon.message.epicfight_task_unavailable" in translations
print("Epic Fight optional bridge, support checks, priority and restoration safeguards validated")
