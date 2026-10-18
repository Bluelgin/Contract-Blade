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
assert "resonanceCost(DanmakuForm form" in growth and "DanmakuResonanceCostPolicy.cost(form)" in growth
assert "DanmakuResonanceCostPolicy.charge(" in combat
assert "ResonanceService.drain(" not in combat
assert "ResonanceService.pay(" in source("system/danmaku/DanmakuResonanceCostPolicy.java")
assert "MAX_ACTIVE_PER_OWNER" in source("system/danmaku/DanmakuPerformanceBudget.java")
assert "MIN_TICKS = 2" in source("system/danmaku/DanmakuHitPolicy.java")
assert "PlayerInteractEvent.LeftClickEmpty" in keys
assert "AttackEntityEvent" in combat
assert "DanmakuShotQueue" in source("compat/tlm/danmaku/DanmakuProjectilePatterns.java")
assert "DanmakuHitPolicy.mayHit" in projectile
assert "DanmakuLaunchGeometry.launch" in source("compat/tlm/danmaku/DanmakuProjectilePatterns.java")
assert "lastFormCast" in combat and "attackReady" in combat
assert "SpiritAbilityResonanceCosts.pay" in source("compat/BlackFoxSwordArtCompat.java")
assert "SpiritAbilityResonanceCosts.pay" in source("entity/BlackFoxSwordCoreEntity.java")
assert "Action.WHITE_OATH" in source("system/fox/WhiteFoxBlessingService.java")
assert "SpiritAbilityResonanceCosts.pay" in source("system/akatsuki/AkatsukiDomainService.java")
assert "SpiritAbilityResonanceCosts.payPoints" in source("system/akatsuki/soul/SoulBurstService.java")
assert "ResonanceService.maximum()" in source("init/ModItems.java")
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
# Animated Manka Talisman: 40 hand-authored SVG animation samples at 32x32.
svg = ElementTree.parse(root / "art/danmaku/manka_talisman.svg").getroot()
svg_ns = {"svg": "http://www.w3.org/2000/svg"}
animations = svg.findall(".//svg:animate", svg_ns) + svg.findall(".//svg:animateTransform", svg_ns)
assert animations and all(len(anim.attrib["values"].split(";")) == 40 for anim in animations)
texture = root / "src/main/resources/assets/maid_weapon/textures/item/manka_talisman.png"
png = texture.read_bytes()
assert png.startswith(b"\x89PNG\r\n\x1a\n")
assert int.from_bytes(png[16:20], "big") == 32
assert int.from_bytes(png[20:24], "big") == 32 * 40
# Check every PNG chunk and the compressed pixels, not just the 24-byte header.
# This catches a truncated IDAT that otherwise appears to be a valid 32x1280 sprite.
import zlib
position = 8
image_data = bytearray()
saw_end = False
while position < len(png):
    assert position + 12 <= len(png), "truncated PNG chunk header"
    length = int.from_bytes(png[position:position + 4], "big")
    assert position + 12 + length <= len(png), "truncated PNG chunk body"
    tag = png[position + 4:position + 8]
    payload = png[position + 8:position + 8 + length]
    checksum = int.from_bytes(png[position + 8 + length:position + 12 + length], "big")
    assert zlib.crc32(tag + payload) == checksum, f"PNG CRC mismatch in {tag!r}"
    if tag == b"IDAT":
        image_data.extend(payload)
    position += length + 12
    if tag == b"IEND":
        saw_end = True
        break
assert saw_end and position == len(png), "PNG must end with a complete IEND chunk"
assert image_data, "PNG has no image data"
assert png[28] == 0, "interlaced atlas texture not supported"
channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[png[25]]
bytes_per_row = (32 * channels * png[24] + 7) // 8
pixels = zlib.decompress(image_data)
assert len(pixels) == (bytes_per_row + 1) * 1280, "decoded animation data length mismatch"
animation_meta = json.loads((root / (str(texture.relative_to(root)) + ".mcmeta")).read_text())
assert animation_meta["animation"]["frametime"] == 2
assert animation_meta["animation"]["interpolate"] is False
assert "frames" not in animation_meta["animation"]  # all 40 frames in order
for locale in ("zh_cn", "en_us"):
    text = json.loads((root / f"src/main/resources/assets/maid_weapon/lang/{locale}.json").read_text())
    assert text["item.maid_weapon.manka_talisman"]
    for form in ("needle", "flower", "guard", "fantasy"):
        assert text[f"maid_weapon.danmaku.form.{form}"]
print("ORDINARY_DANMAKU_STATIC_PASS: native baubles, per-shot damage, 2-tick spacing, resonance payment, inputs and assets")
