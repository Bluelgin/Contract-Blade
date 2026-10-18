"""Lightweight integrity checks for ordinary maid danmaku; JUnit tests growth semantics."""
import json
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parents[1]
java = root / "src/main/java/com/maidweapon/forge"
def source(path):
    return (java / path).read_text(encoding="utf-8")

effects = source("compat/tlm/ContractBaubleEffects.java")
stored = source("compat/tlm/StoredMaidBaubleResolver.java")
policy = source("system/danmaku/DanmakuEligibility.java")
combat = source("system/danmaku/DanmakuCombatService.java")
network = source("network/DanmakuNetwork.java")
keys = source("client/DanmakuKeys.java")
wheel = source("client/DanmakuWheelScreen.java")
growth = source("system/danmaku/DanmakuGrowth.java")
projectile = source("compat/tlm/danmaku/ContractDanmakuProjectile.java")

assert "DANMAKU_ART" in effects
assert "StoredMaidBaubleResolver.has" in effects
assert "MaidBaubleInventory" in stored and "readNativeEffects" in stored
assert "ResonanceContract.resolve" in policy and "FoxSpiritState.hasResident" in policy
assert "Effect.DANMAKU_ART" in policy
assert "ContractMasteryService.getLevel" in combat
assert "getDirectEntity()" in combat and "LivingDamageEvent" in combat
assert "resonanceCost(DanmakuForm form" in growth and "return 0;" in growth
assert "ResonanceService.pay" not in combat and "ResonanceService.drain" not in combat
assert "safeTarget" in projectile and "super.onHitEntity" in projectile
assert "getSender()" in network and "enqueueWork" in network and "PLAY_TO_SERVER" in network
assert "KeyMapping" in keys and "GLFW_MOUSE_BUTTON_MIDDLE" in keys
assert "InputEvent.InteractionKeyMappingTriggered" in keys and "setCanceled(true)" in keys
assert "ticks >= 6" in keys and "DanmakuWheelScreen" in keys
assert "mouseReleased" in wheel and "keyReleased" in wheel and "commit" in wheel

model = json.loads((root / "src/main/resources/assets/maid_weapon/models/item/manka_talisman.json").read_text())
assert model["textures"]["layer0"] == "maid_weapon:item/manka_talisman"
recipe = json.loads((root / "src/main/resources/data/maid_weapon/recipes/altar/manka_talisman.json").read_text())
assert recipe["type"] == "touhou_little_maid:altar_crafting"
assert recipe["output"]["nbt"]["Item"]["id"] == "maid_weapon:manka_talisman"
assert len(recipe["ingredients"]) == 6
ElementTree.parse(root / "art/danmaku/manka_talisman.svg")
png = (root / "src/main/resources/assets/maid_weapon/textures/item/manka_talisman.png").read_bytes()
assert png[:8] == b"\x89PNG\r\n\x1a\n" and int.from_bytes(png[16:20], "big") == 32
assert int.from_bytes(png[20:24], "big") == 32
for locale in ("zh_cn", "en_us"):
    text = json.loads((root / f"src/main/resources/assets/maid_weapon/lang/{locale}.json").read_text())
    assert text["item.maid_weapon.manka_talisman"]
    for form in ("needle", "flower", "guard", "fantasy"):
        assert text[f"maid_weapon.danmaku.form.{form}"]
print("ORDINARY_DANMAKU_STATIC_PASS: independent tiers, no resonance cost, native baubles, inputs and assets")
