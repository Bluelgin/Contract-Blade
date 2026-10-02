package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.tlm.TlmResidenceAdapter;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Decides why a companion is present. Lifecycle/storage and equipment stay separate. */
public final class ContractCompanionService {
    public static final double RECALL_RANGE = 32;
    public static final int GUARD_TICKS = 400;
    private record Injury(String binding, float health) { }
    private static final Map<UUID, Injury> INJURIES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_REQUEST = new HashMap<>();

    public static void attacked(Player owner, Entity attacker, float damage) {
        if (damage <= 0 || !(attacker instanceof LivingEntity) || attacker == owner || atHome(owner)) return;
        ItemStack held = owner.getMainHandItem();
        if (InfusedMaidDeploymentSystem.isEligibleWeapon(held, owner)) {
            INJURIES.put(owner.getUUID(), new Injury(MaidWeaponItem.ensureBindingId(held), owner.getHealth()));
        }
        prolongGuard(owner);
    }

    public static void prolongGuard(Player owner) {
        for (ItemStack carrier : owner.getInventory().items) {
            if (!owned(owner, carrier) || MaidInfusion.containsMaid(carrier)
                    || ContractCompanionState.mode(carrier) != ContractCompanionState.Mode.GUARD) continue;
            Entity maid = loaded(owner, carrier);
            if (maid != null) ContractCompanionState.extend(carrier, maid, now(owner) + GUARD_TICKS);
        }
    }

    public static void toggle(Player owner) {
        if (!owner.isAlive() || owner.isSpectator() || owner.containerMenu != owner.inventoryMenu) return;
        long now = now(owner);
        if (now < NEXT_REQUEST.getOrDefault(owner.getUUID(), 0L)) return;
        NEXT_REQUEST.put(owner.getUUID(), now + 10);
        if (atHome(owner)) { message(owner, "interior"); return; }
        ItemStack carrier = owner.getMainHandItem();
        if (!owned(owner, carrier)) { message(owner, "hold_contract"); return; }
        if (MaidWeaponItem.isContractSuperseded(carrier)) { message(owner, "unavailable"); return; }
        Entity maid = loaded(owner, carrier);
        if (maid != null || !MaidInfusion.containsMaid(carrier)) {
            // A missing loaded entity is not permission to instantiate a replacement.
            if (maid == null) { message(owner, otherDimension(owner, carrier) ? "other_world" : "not_loaded"); return; }
            if (maid.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)) {
                message(owner, "interior_resident"); return;
            }
            if (maid.level() != owner.level()) { message(owner, "other_world"); return; }
            if (maid.distanceToSqr(owner) > RECALL_RANGE * RECALL_RANGE) { message(owner, "too_far"); return; }
            if (!InfusedMaidDeploymentSystem.recallRequested(owner, carrier)) message(owner, "failed");
            else message(owner, "recalled");
            return;
        }
        if (!InfusedMaidDeploymentSystem.isEligibleWeapon(carrier, owner)) { message(owner, "unavailable"); return; }
        if (hasFollowingCompanion(owner)) { message(owner, "already_accompanied"); return; }
        summon(owner, carrier, ContractCompanionState.Mode.MANUAL);
    }

    public static void tick(Player owner) {
        Injury injury = INJURIES.remove(owner.getUUID());
        ItemStack held = owner.getMainHandItem();
        if (injury != null && owner.isAlive() && owner.getHealth() < injury.health()
                && injury.binding().equals(MaidWeaponItem.getBindingId(held))
                && InfusedMaidDeploymentSystem.isEligibleWeapon(held, owner)
                && MaidInfusion.containsMaid(held) && loaded(owner, held) == null && !hasFollowingCompanion(owner)) {
            summon(owner, held, ContractCompanionState.Mode.GUARD);
        }
        if (owner.tickCount % 5 != 0) return;
        var seen = new HashSet<String>();
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack carrier = owner.getInventory().getItem(slot);
            if (!owned(owner, carrier) || MaidWeaponItem.isContractSuperseded(carrier) || MaidInfusion.containsMaid(carrier)) continue;
            String binding = MaidWeaponItem.getBindingId(carrier);
            if (binding == null || !seen.add(binding)) continue;
            Entity maid = loaded(owner, carrier);
            if (maid == null || maid.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)) continue;
            boolean resident = TlmResidenceAdapter.isResident(maid);
            var mode = ContractCompanionState.mode(carrier);
            if (resident) mode = ContractCompanionState.Mode.RESIDENT;
            else if (mode == ContractCompanionState.Mode.RESIDENT) mode = ContractCompanionState.Mode.MANUAL;
            ContractCompanionState.mark(carrier, maid, mode, ContractCompanionState.until(carrier));
            InfusedMaidDeploymentSystem.maintainCompanion(owner, carrier, maid, resident);
            if (resident) continue;
            if (MaidInfusion.data(carrier).getResonance() <= 0) {
                InfusedMaidDeploymentSystem.forceRecall(owner, maid.getStringUUID(), 300);
                continue;
            }
            if (mode != ContractCompanionState.Mode.GUARD) continue;
            if (inCombat(owner, maid)) ContractCompanionState.extend(carrier, maid, now(owner) + 200);
            if (now(owner) >= ContractCompanionState.until(carrier)
                    && maid.level() == owner.level() && maid.distanceToSqr(owner) <= RECALL_RANGE * RECALL_RANGE) {
                ContractCompanionDialogue.say(owner, maid, owner.getHealth() < owner.getMaxHealth() ? "hurt" : "safe");
                InfusedMaidDeploymentSystem.recallRequested(owner, carrier);
            }
        }
    }

    public static boolean isResident(Player owner, ItemStack carrier) {
        Entity maid = loaded(owner, carrier);
        return maid != null ? TlmResidenceAdapter.isResident(maid)
                : ContractCompanionState.mode(carrier) == ContractCompanionState.Mode.RESIDENT;
    }

    public static void cancelRequests(Player owner) { INJURIES.remove(owner.getUUID()); }
    public static void disconnect(Player owner) {
        cancelRequests(owner);
        NEXT_REQUEST.remove(owner.getUUID());
    }
    public static void clearSession() { INJURIES.clear(); NEXT_REQUEST.clear(); }

    private static void summon(Player owner, ItemStack carrier, ContractCompanionState.Mode mode) {
        if (!InfusedMaidDeploymentSystem.manifestRequested(owner, carrier, mode)) { message(owner, "failed"); return; }
        Entity maid = loaded(owner, carrier);
        if (maid == null) return;
        ContractCompanionState.mark(carrier, maid, mode, mode == ContractCompanionState.Mode.GUARD ? now(owner) + GUARD_TICKS : 0);
        ContractCompanionDialogue.say(owner, maid, mode == ContractCompanionState.Mode.GUARD ? "protect" : "greeting");
    }

    private static boolean hasFollowingCompanion(Player owner) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack carrier = owner.getInventory().getItem(slot);
            if (owned(owner, carrier) && !MaidWeaponItem.isContractSuperseded(carrier)
                    && !MaidInfusion.containsMaid(carrier) && !isResident(owner, carrier)) return true;
        }
        return false;
    }

    private static boolean inCombat(Player owner, Entity maid) {
        if (maid instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) return true;
        return !owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12),
                mob -> mob.isAlive() && (mob.getTarget() == owner || mob.getTarget() == maid)).isEmpty();
    }

    private static boolean otherDimension(Player owner, ItemStack carrier) {
        var tag = carrier.getTag();
        return tag != null && tag.contains("MaidDeploymentLocation") && !tag.getCompound("MaidDeploymentLocation")
                .getString("Dimension").equals(owner.level().dimension().location().toString());
    }
    private static Entity loaded(Player owner, ItemStack carrier) {
        String id = MaidWeaponItem.getBoundMaidUUID(carrier);
        Entity maid = id == null ? null : ContractWeaponLocator.findManifestedMaid(owner, id);
        return maid != null && MaidWeaponItem.ensureBindingId(carrier).equals(maid.getPersistentData()
                .getString(com.maidweapon.forge.compat.TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID)) ? maid : null;
    }
    private static boolean owned(Player owner, ItemStack carrier) {
        return !ContractTransferSafetyService.isProjectionPhantom(carrier)
                && MaidInfusion.isInfused(carrier) && MaidWeaponItem.isOwner(carrier, owner);
    }
    private static boolean atHome(Player owner) { return owner.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL); }
    private static long now(Player owner) { return owner.getServer().overworld().getGameTime(); }
    private static void message(Player owner, String key) {
        owner.displayClientMessage(Component.translatable("maid_weapon.companion." + key), true);
    }
    private ContractCompanionService() { }
}
