package com.maidweapon.forge.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.maidweapon.forge.event.MaidBondCombatHandler;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractRecoveryService;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/** Optional TLM entry points: protect before custom death and restore only proven contract identities. */
public final class ContractMaidRebirthBridge {
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, MaidDeathEvent.class,
                ContractMaidRebirthBridge::death);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MaidAndItemTransformEvent.ToItem.class,
                event -> { if (event.getMaid().isAlive()) event.getMaid().getPersistentData().putBoolean("MaidWeaponNativeStorageTransfer", true); });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MaidAndItemTransformEvent.ToMaid.class,
                ContractMaidRebirthBridge::rebirth);
    }

    private static void death(MaidDeathEvent event) {
        var maid = event.getMaid();
        if (maid.level().isClientSide || maid.getServer() == null || maid.getOwnerUUID() == null) return;
        var owner = maid.getServer().getPlayerList().getPlayer(maid.getOwnerUUID());
        if (owner != null && MaidBondCombatHandler.protectContractDeath(owner, maid)
                != MaidBondCombatHandler.DeathProtection.NOT_CONTRACT) event.setCanceled(true);
    }

    private static void rebirth(MaidAndItemTransformEvent.ToMaid event) {
        var maid = event.getMaid();
        var data = event.getData();
        if (maid.level().isClientSide || maid.getServer() == null || !data.hasUUID("Owner")) return;
        if (event.getItem().hasTag() && event.getItem().getTag().getBoolean("MaidWeaponClientSuppliedContract")) {
            maid.getPersistentData().putBoolean("MaidWeaponRecoveryRejected", true); return;
        }
        Player owner = maid.getServer().getPlayerList().getPlayer(data.getUUID("Owner"));
        if (owner == null && (data.getCompound("ForgeData").contains("MaidWeaponEmbeddedSpirit")
                || event.getItem().hasTag() && event.getItem().getTag().contains(SoulTalismanService.EMERGENCY))) {
            maid.getPersistentData().putBoolean("MaidWeaponRecoveryRejected", true); return;
        }
        if (owner != null && event.getItem().hasTag()
                && event.getItem().getTag().contains(SoulTalismanService.EMERGENCY)) {
            if (!restoreEmergency(owner, maid, data, event.getItem().getTag().getCompound(SoulTalismanService.EMERGENCY)))
                maid.getPersistentData().putBoolean("MaidWeaponRecoveryRejected", true);
            return;
        }
        if (owner != null) {
            boolean restored = restoreIdentity(owner, maid, data);
            String binding = data.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
            var record = binding.isEmpty() ? new CompoundTag()
                    : com.maidweapon.forge.system.contract.ContractRecoveryJournal.get(owner.getServer()).incident(binding);
            if (!restored && (!record.isEmpty() && !record.getString("Status").equals("UNBOUND")
                    || data.getCompound("ForgeData").contains("MaidWeaponEmbeddedSpirit")))
                maid.getPersistentData().putBoolean("MaidWeaponRecoveryRejected", true);
        }
    }

    private static boolean restoreEmergency(Player owner, Entity maid, CompoundTag data, CompoundTag receipt) {
        if (!receipt.hasUUID("Owner") || !owner.getUUID().equals(receipt.getUUID("Owner"))
                || !receipt.hasUUID("Token") || !data.hasUUID("UUID") || !data.hasUUID("Owner")
                || !owner.getUUID().equals(data.getUUID("Owner")) || !receipt.getString("Maid").equals(data.getUUID("UUID").toString())) return false;
        var journal = com.maidweapon.forge.system.contract.ContractRecoveryJournal.get(owner.getServer());
        var authority = journal.transfer(receipt.getString("Maid"));
        if (!authority.getString("Kind").equals("EMERGENCY") || !authority.hasUUID("Token")
                || !authority.getUUID("Token").equals(receipt.getUUID("Token"))
                || !authority.getString("Binding").equals(receipt.getString("Binding"))) return false;
        try {
            if (!com.maidweapon.forge.system.MaidEntityDataCodec.read(authority).equals(data)) return false;
        } catch (java.io.IOException unsafe) { return false; }
        for (var level : owner.getServer().getAllLevels()) if (level.getEntity(data.getUUID("UUID")) != null) return false;
        TlmEntityAdapter.tame(maid, owner);
        if (!TlmEntityAdapter.restoreEmergencyExtras(maid, data)) return false;
        if (!ContractWeaponLocator.findBoundWeapon(owner, receipt.getString("Maid")).isEmpty()
                || ContractWeaponLocator.findManifestedMaid(owner, receipt.getString("Maid")) != null) return false;
        if (receipt.contains("Fox")) {
            var fox = receipt.getCompound("Fox");
            if (!com.maidweapon.forge.system.fox.FoxSpiritState.valid(fox)
                    || !com.maidweapon.forge.system.fox.FoxSpiritLedger.get(owner.getServer()).owns(fox, "EMERGENCY")) return false;
            maid.getPersistentData().put(com.maidweapon.forge.system.fox.FoxSpiritState.ROOT, fox.copy());
            com.maidweapon.forge.system.fox.FoxSpiritLedger.get(owner.getServer()).record(fox, "FREE_MAID");
        }
        authority.putString("Kind", "FREE_MAID"); journal.transfer(receipt.getString("Maid"), authority);
        owner.getServer().overworld().getDataStorage().save();
        maid.setUUID(data.getUUID("UUID")); maid.getPersistentData().remove(ContractMaidKeys.ENTITY_BINDING_ID);
        var progress = data.getCompound("ForgeData").getCompound(ContractMaidKeys.EMERGENCY_FILM_PROGRESS);
        if (!progress.isEmpty()) maid.getPersistentData().put(ContractMaidKeys.EMERGENCY_FILM_PROGRESS, progress.copy());
        if (receipt.contains("Fox")) maid.getPersistentData().putString("MaidWeaponEmbeddedSpirit",
                receipt.getCompound("Fox").getString("SpiritId"));
        return true;
    }

    /** TLM calls readAdditionalSaveData, not Entity.load: UUID and ForgeData otherwise disappear. */
    public static boolean restoreIdentity(Player owner, Entity maid, CompoundTag data) {
        if (!data.hasUUID("UUID") || !data.hasUUID("Owner")
                || !owner.getUUID().equals(data.getUUID("Owner"))) return false;
        String id = data.getUUID("UUID").toString();
        String binding = data.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
        var carrier = binding.isEmpty() ? ContractWeaponLocator.findBoundWeapon(owner, id)
                : ContractWeaponLocator.findBoundWeaponByBinding(owner, binding);
        if (carrier.isEmpty() || !MaidInfusion.isInfused(carrier)
                || !ContractCarrierData.isOwner(carrier, owner)
                || ContractCarrierData.isContractSuperseded(carrier)
                || MaidInfusion.containsMaid(carrier)
                || !id.equals(ContractCarrierData.getBoundMaidUUID(carrier))) return false;
        if (!com.maidweapon.forge.api.ContractAuthorization.allows(owner, carrier)) return false;
        var record = com.maidweapon.forge.system.contract.ContractAnomalyRecoveryService.incident(owner, carrier);
        if (record.getBoolean("BackupSpent")) return false;
        // Never replace an already-live maid with another copy from an old film.
        for (var level : owner.getServer().getAllLevels())
            if (level.getEntity(data.getUUID("UUID")) != null) return false;
        maid.setUUID(data.getUUID("UUID"));
        var forge = data.getCompound("ForgeData");
        if (forge.contains("MaidWeaponEmbeddedSpirit")) maid.getPersistentData().putString("MaidWeaponEmbeddedSpirit", forge.getString("MaidWeaponEmbeddedSpirit"));
        maid.getPersistentData().remove("MaidWeaponNativeStorageTransfer");
        maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID,
                ContractCarrierData.ensureBindingId(carrier));
        if (!record.isEmpty() && record.getBoolean("Confirmed")) {
            record.putBoolean("BackupSpent", true); record.putString("Status", "RESTORED_NATIVE");
            com.maidweapon.forge.system.contract.ContractRecoveryJournal.get(owner.getServer()).incident(
                    ContractCarrierData.getBindingId(carrier), record);
            owner.getServer().overworld().getDataStorage().save();
        }
        ContractRecoveryService.clearFailure(carrier);
        ContractRecoveryService.rememberLocation(carrier, maid);
        return true;
    }

    private ContractMaidRebirthBridge() { }
}
