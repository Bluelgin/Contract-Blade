package com.maidweapon.forge.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.maidweapon.forge.event.MaidBondCombatHandler;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.*;
import com.maidweapon.forge.system.fox.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.*;

/** Thin native photo/film adapter. No backup directory reads, consumption ledger or replay transactions. */
public final class ContractMaidRebirthBridge {
    private static final Set<Entity> REJECTED = Collections.newSetFromMap(new IdentityHashMap<>());
    public static boolean rejected(Entity entity) { return REJECTED.remove(entity); }
    public static void clearSession() { REJECTED.clear(); }
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, MaidDeathEvent.class, ContractMaidRebirthBridge::death);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MaidAndItemTransformEvent.ToItem.class, event -> {
            if (!event.getMaid().level().isClientSide && event.getMaid().getServer() != null
                    && event.getMaid().level() instanceof net.minecraft.server.level.ServerLevel level
                    && level.getEntity(event.getMaid().getUUID()) == event.getMaid()) {
                event.getMaid().getPersistentData().putBoolean(ContractMaidKeys.NATIVE_STORAGE_TRANSFER, true);
                ContractRecoveryJournal.get(event.getMaid().getServer()).markMaidAbsent(event.getMaid().getStringUUID());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MaidAndItemTransformEvent.ToMaid.class, ContractMaidRebirthBridge::rebirth);
    }
    private static void death(MaidDeathEvent event) {
        var maid = event.getMaid();
        if (maid.level().isClientSide || maid.getServer() == null || maid.getOwnerUUID() == null) return;
        var owner = maid.getServer().getPlayerList().getPlayer(maid.getOwnerUUID());
        if (owner != null && MaidBondCombatHandler.protectContractDeath(owner, maid)
                != MaidBondCombatHandler.DeathProtection.NOT_CONTRACT) event.setCanceled(true);
    }
    private static void rebirth(MaidAndItemTransformEvent.ToMaid event) {
        var maid = event.getMaid(); var data = event.getData();
        if (maid.level().isClientSide || maid.getServer() == null || !data.hasUUID("UUID")) return;
        String id = data.getUUID("UUID").toString();
        var journal = ContractRecoveryJournal.get(maid.getServer());
        boolean collision = journal.isMaidPresent(id);
        for (var level : maid.getServer().getAllLevels()) if (level.getEntity(data.getUUID("UUID")) != null) collision = true;
        if (collision) { REJECTED.add(maid); return; }
        Player owner = data.hasUUID("Owner") ? maid.getServer().getPlayerList().getPlayer(data.getUUID("Owner")) : null;
        boolean emergency = event.getItem().hasTag() && event.getItem().getTag().contains(SoulTalismanService.EMERGENCY);
        if (emergency) {
            var metadata = event.getItem().getTag().getCompound(SoulTalismanService.EMERGENCY);
            if (!data.hasUUID("Owner") || !metadata.hasUUID("Owner")
                    || !data.getUUID("Owner").equals(metadata.getUUID("Owner"))
                    || !id.equals(metadata.getString("Maid"))) { REJECTED.add(maid); return; }
            // Old emergency films carry the entire snapshot; allow copies but never a second real UUID.
            if (!TlmEntityAdapter.restoreEmergencyExtras(maid, data)) { REJECTED.add(maid); return; }
            if (metadata.contains("Fox")) {
                var fox = metadata.getCompound("Fox");
                var ledger = FoxSpiritLedger.get(maid.getServer());
                if (!FoxSpiritState.valid(fox) || !data.getUUID("Owner").equals(fox.getUUID("OwnerUUID"))
                        || !ledger.owns(fox, "EMERGENCY") && !ledger.owns(fox, "FREE_MAID")
                        || !ledger.maid(fox).isEmpty() && !id.equals(ledger.maid(fox))) { REJECTED.add(maid); return; }
                data.getCompound("ForgeData").put(FoxSpiritState.ROOT, fox.copy());
                maid.getPersistentData().put(FoxSpiritState.ROOT, fox.copy()); ledger.record(fox, "FREE_MAID");
            }
        }
        maid.setUUID(data.getUUID("UUID")); // ItemFilm does not call Entity.load; ItemPhoto subsequently does.
        data.getCompound("ForgeData").remove("MaidWeaponRecoveryRejected");
        data.getCompound("ForgeData").remove(ContractMaidKeys.CORE_STORAGE_TRANSFER);
        data.getCompound("ForgeData").remove(ContractMaidKeys.NATIVE_STORAGE_TRANSFER);
        if (owner != null) restoreIdentity(owner, maid, data);
        // A native photo is a historical snapshot. The player explicitly requested its restoration.
        if (owner != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItem().getItem()).getPath().equals("photo"))
            owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.recovery.historical_snapshot"), false);
    }
    /** Reconnect the original identity when a valid empty carrier is available; never overwrite current storage. */
    public static boolean restoreIdentity(Player owner, Entity maid, CompoundTag data) {
        if (!data.hasUUID("UUID") || !data.hasUUID("Owner") || !owner.getUUID().equals(data.getUUID("Owner"))) return false;
        String id = data.getUUID("UUID").toString();
        if (!ContractWeaponLocator.mayManifest(owner, id)) return false;
        String binding = data.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
        var carrier = binding.isEmpty() ? ContractWeaponLocator.findBoundWeapon(owner, id)
                : ContractWeaponLocator.findBoundWeaponByBinding(owner, binding);
        if (carrier.isEmpty() || !MaidInfusion.isInfused(carrier) || !ContractCarrierData.isOwner(carrier, owner)
                || ContractCarrierData.isContractSuperseded(carrier) || MaidInfusion.containsMaid(carrier)
                || !id.equals(ContractCarrierData.getBoundMaidUUID(carrier))
                || !com.maidweapon.forge.api.ContractAuthorization.allows(owner, carrier)) return false;
        maid.setUUID(data.getUUID("UUID"));
        var forge = data.getCompound("ForgeData");
        // Native photos load after the event, so update both the load payload and the film entity.
        forge.putString(ContractMaidKeys.ENTITY_BINDING_ID, ContractCarrierData.ensureBindingId(carrier));
        maid.getPersistentData().merge(forge.copy());
        ContractRecoveryJournal.get(owner.getServer()).clearIncident(ContractCarrierData.getBindingId(carrier));
        ContractRecoveryService.clearFailure(carrier); ContractRecoveryService.rememberLocation(carrier, maid);
        return true;
    }
    private ContractMaidRebirthBridge() { }
}
