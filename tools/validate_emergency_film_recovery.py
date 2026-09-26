from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
deployment = (ROOT / "src/main/java/com/maidweapon/forge/system/InfusedMaidDeploymentSystem.java").read_text(
    encoding="utf-8"
)
helper = (ROOT / "src/main/java/com/maidweapon/forge/compat/TouhouLittleMaidHelper.java").read_text(
    encoding="utf-8"
)


required_deployment = (
    "PlayerDestroyItemEvent",
    "ACTIVE_CARRIERS",
    "createEmergencyResurrectionFilm",
    "deliverEmergencyFilm(player, film)",
    "maid.discard();",
    "carrier_destroyed_film_created",
)
for token in required_deployment:
    assert token in deployment, f"missing emergency recovery token: {token}"

recovery = deployment[deployment.index("private static boolean emergencyFilmRecovery") :]
assert recovery.index("deliverEmergencyFilm(player, film)") < recovery.index("maid.discard();"), (
    "the recovery film must be delivered before the live maid is discarded"
)
assert "if (weapon.isEmpty())" in deployment and "ACTIVE_CARRIERS.getOrDefault" in deployment, (
    "missing fallback for third-party carriers that vanish without a destroy event"
)

required_helper = (
    '"touhou_little_maid", "film"',
    'filmTag.put("MaidInfo", maidData)',
    'filmTag.put(TAG_FILM_PROGRESS, progress)',
    'forgeData.remove(TAG_ENTITY_BINDING_ID)',
    "TAG_EMERGENCY_FILM_PROGRESS",
    "ContractNbtGuard.inspect(filmTag)",
)
for token in required_helper:
    assert token in helper, f"missing TLM film compatibility token: {token}"

film_method = helper[helper.index("public static ItemStack createEmergencyResurrectionFilm") :]
for inventory_key in ("MaidInventory", "MaidBaubleInventory", "MaidExperience", "ArmorItems", "HandItems"):
    assert f'remove("{inventory_key}")' not in film_method, (
        f"emergency film must retain {inventory_key} because no tombstone is created"
    )

print("Emergency contract film recovery validation passed.")
