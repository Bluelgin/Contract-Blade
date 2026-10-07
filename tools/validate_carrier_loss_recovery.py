from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/maidweapon/forge"
deployment = (JAVA / "system/InfusedMaidDeploymentSystem.java").read_text(encoding="utf-8")
loss = (JAVA / "system/deployment/ContractCarrierLossService.java").read_text(encoding="utf-8")
locator = (JAVA / "system/deployment/ContractWeaponLocator.java").read_text(encoding="utf-8")
companion = (JAVA / "system/deployment/ContractCompanionState.java").read_text(encoding="utf-8")
film_service = (JAVA / "compat/tlm/TlmFilmService.java").read_text(encoding="utf-8")

for token in (
    "ContractCarrierLossService.process(player, carriers",
    "indexByBinding",
):
    assert token in deployment, f"missing carrier-loss safeguard: {token}"

bridge = (JAVA / "system/deployment/ContractDurabilityBridge.java").read_text(encoding="utf-8")
assert "ContractCarrierLossService.scheduleDestroyed" in bridge
assert "stack.getCount() <= count" in bridge and "!stack.isEmpty()) return" in bridge
assert bridge.index("snapshot = stack.copy()") < bridge.index("stack.shrink(count)")
tick_handler = deployment.split("public static void onPlayerTick", 1)[1].split("public static void onLogout", 1)[0]
assert "scheduleDestroyed" not in tick_handler
assert "ACTIVE_CARRIERS" not in deployment
assert "PlayerDestroyItemEvent" not in deployment
assert not (JAVA / "system/deployment/ContractCarrierTransferGuard.java").exists()
for path, method in [("mixin/ContractDurabilityMixin.java", "hurtAndBreak"),
                     ("compat/fox/mixin/SlashBladeDurabilityMixin.java", "damageItem")]:
    mixin = (JAVA / path).read_text(encoding="utf-8")
    assert method in mixin and "ItemStack;shrink(I)V" in mixin
    assert "ItemStack;m_41774_(I)V" in mixin and "ContractDurabilityBridge.consume" in mixin
interior = (JAVA / "system/interior/ContractInteriorEvents.java").read_text(encoding="utf-8")
assert "PlayerDestroyItemEvent" not in interior
assert "ContractInteriorService.recoverDestroyedActiveContract" in bridge
assert "maid.discard()" not in bridge
assert "createEmergencyResurrectionFilm" not in bridge

resolver = loss[loss.index("private static boolean resolve("):]
assert "ContractLifecycleService.manifest(player, transientCarrier, false)" in resolver
assert "ContractMaidKeys.EMERGENCY_FILM_PROGRESS" in resolver
assert 'snapshot.getTag().getCompound("MaidData").copy()' in resolver
assert "ContractMaidRuntimeService.cleanupBeforeRecall(player, snapshot, maid)" in resolver
assert "remove(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID)" in resolver
assert "ContractCompanionState.clear(maid)" in resolver
assert "maid.discard()" not in resolver
assert "createEmergencyResurrectionFilm" in resolver, (
    "recovery film must remain a last-resort fallback when stored manifestation fails"
)

assert "public static ItemStack findBoundWeaponByBinding" in locator
assert "if (stack.isEmpty()) return;" in locator
for carrier_surface in (
    "player.getInventory().getContainerSize()",
    "player.getOffhandItem()",
    "player.containerMenu.getCarried()",
    "player.containerMenu.slots",
):
    assert carrier_surface in locator, f"binding lookup misses carrier surface: {carrier_surface}"

assert "public static void clear(Entity maid)" in companion
assert "ContractCarrierLossJournal.get" in loss
assert 'if (!task.durabilityConfirmed) continue;' in loss
journal = (JAVA / "system/deployment/ContractCarrierLossJournal.java").read_text(encoding="utf-8")
assert 'entry.getBoolean("DurabilityConfirmed")' in journal
assert 'entry.putBoolean("DurabilityConfirmed", task.durabilityConfirmed)' in journal
assert 'pending.get(bindingId)' in loss and 'pending.put(bindingId' in loss
cleanup = deployment.split('private static void clearCarrierLossRuntime', 1)[1].split(
    'public static boolean manifestRequested', 1)[0]
assert 'active.bindingId().equals(candidate.bindingId())' in cleanup
assert 'ContractRecoveryService.cancel(player, candidate.maidId())' in cleanup
assert 'ContractActiveDeployments.forgetMaid(player.getUUID(), candidate.maidId())' in cleanup
assert "maid.getPersistentData().remove(KEY)" in companion

film_method = film_service[film_service.index("public static ItemStack createEmergencyResurrectionFilm") :]
for inventory_key in ("MaidInventory", "MaidBaubleInventory", "MaidExperience", "ArmorItems", "HandItems"):
    assert f'remove("{inventory_key}")' not in film_method, (
        f"fallback recovery film must retain {inventory_key}"
    )

assert "maid.discard();" not in deployment, (
    "carrier loss must never delete a live maid; losing a weapon is not maid death"
)

print("Durability-only destruction, no transfer adapters, legacy quarantine and bounded rescue validated.")


for token in ("CARRIER_LOSS_CONFIRM_TICKS = 40", "CHECKS_PER_PASS = 32", "RESTORES_PER_PLAYER = 2",
              "RESTORES_PER_SERVER_TICK = 8", "task.nextAttempt = now + RETRY_TICKS", "budget.players",
              "pending.put(binding, task)", "pending.remove(binding)"):
    assert token in loss, token
assert "findBoundWeaponByBinding" not in loss
assert "MaidEntityDataCodec" not in locator
assert loss.count("if (changed) journal.setDirty();") == 2, (
    "unchanged duplicate events and queue rotations must not dirty saved data"
)
