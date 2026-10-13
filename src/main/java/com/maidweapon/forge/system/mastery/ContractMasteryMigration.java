package com.maidweapon.forge.system.mastery;

import com.maidweapon.common.data.ContractMasteryData;
import com.maidweapon.forge.api.ContractAuthorization;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.fox.FoxSpiritLedger;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads metadata only. Never manifests, captures, decompresses, or changes a maid/channel. */
public final class ContractMasteryMigration {
    public static List<CompoundTag> channels(ItemStack carrier) {
        var result = new ArrayList<>(com.maidweapon.forge.system.contract.ContractChannelStorage.channels(carrier));
        if (!carrier.hasTag()) return result;
        var root = carrier.getTag();
        var sealed = root.getCompound(FoxSpiritState.SEAL);
        if (sealed.contains("Contract", Tag.TAG_COMPOUND)) result.add(sealed.getCompound("Contract"));
        return result;
    }
    public static String source(CompoundTag channel) {
        try {
            var maid = UUID.fromString(channel.getString("MaidUUID"));
            // Older valid contracts without binding IDs have a stable maid identity fallback.
            String binding = channel.getString("MaidBindingId");
            if (!binding.isEmpty()) UUID.fromString(binding);
            return maid.toString(); // Stable even if an old contract receives its first binding ID later.
        } catch (IllegalArgumentException malformed) { return ""; }
    }
    public static boolean validLegacy(CompoundTag channel) {
        var data = channel.getCompound("MaidData");
        if (data.getBoolean("ClientSuppliedGrowth")) return false;
        if (data.contains("SchemaVersion") && (!data.contains("SchemaVersion", Tag.TAG_INT)
                || data.getInt("SchemaVersion") < 0 || data.getInt("SchemaVersion") > ContractMasteryData.SCHEMA_VERSION)) return false;
        if (!channel.hasUUID("OwnerUUID") || channel.getBoolean("MaidContractSuperseded") || source(channel).isEmpty()
                || !data.contains("Level", Tag.TAG_INT) || data.getInt("Level") < 1 || data.getInt("Level") > 10) return false;
        for (String key : List.of("TotalKills", "EnderDragonKills", "WitherKills"))
            if (data.contains(key) && (!data.contains(key, Tag.TAG_INT) || data.getInt(key) < 0)) return false;
        if (data.contains("UnlockedTier") && (!data.contains("UnlockedTier", Tag.TAG_INT)
                || data.getInt("UnlockedTier") < 0 || data.getInt("UnlockedTier") > 5)) return false;
        if (data.contains("ConfiguredUpgradeProgress")) {
            if (!data.contains("ConfiguredUpgradeProgress", Tag.TAG_INT_ARRAY)) return false;
            var progress = data.getIntArray("ConfiguredUpgradeProgress");
            if (progress.length > 11) return false;
            for (int value : progress) if (value < 0 || value > 1000000) return false;
        }
        return true;
    }
    public static boolean observe(ServerPlayer owner, ItemStack carrier) {
        if (carrier == null || carrier.isEmpty() || !carrier.hasTag()
                || com.maidweapon.forge.system.deployment.ContractTransferSafetyService.isProjectionPhantom(carrier)) return false;
        boolean seal = FoxSpiritState.isSeal(carrier);
        if (!seal && !MaidInfusion.isWeapon(carrier)) return false;
        var identity = seal ? FoxSpiritState.sealed(carrier) : FoxSpiritState.resident(carrier);
        if (!identity.isEmpty() && (!FoxSpiritState.valid(identity) || !identity.getUUID("OwnerUUID").equals(owner.getUUID())
                || !FoxSpiritLedger.get(owner.getServer()).owns(identity, seal ? "SEAL" : "WEAPON"))) return false;
        var storage = ContractMasterySavedData.get(owner.getServer());
        boolean changed = false;
        for (var channel : channels(carrier)) {
            if (!validLegacy(channel) || !channel.getUUID("OwnerUUID").equals(owner.getUUID())) continue;
            // Validate additional authorization against exactly this channel, retaining carrier/fox identity.
            ItemStack view = carrier.copy();
            com.maidweapon.forge.system.contract.ContractChannelStorage.restoreContract(view, channel);
            if (!seal && !ContractAuthorization.allows(owner, view)) continue;
            String source = source(channel);
            if (carrier.getTag().getBoolean("MaidWeaponClientSuppliedContract")
                    && !storage.matchesSource(owner.getUUID(), source, ContractMasteryCodec.read(channel.getCompound("MaidData")))) continue;
            if (!storage.hasSource(source)) {
                boolean imported = storage.importSource(owner.getUUID(), source, ContractMasteryCodec.read(channel.getCompound("MaidData")));
                changed |= imported;
                if (imported) com.mojang.logging.LogUtils.getLogger().info("[ContractMastery] Imported legacy source {} for {}", source, owner.getUUID());
            }
        }
        return changed;
    }
    private ContractMasteryMigration() { }
}
