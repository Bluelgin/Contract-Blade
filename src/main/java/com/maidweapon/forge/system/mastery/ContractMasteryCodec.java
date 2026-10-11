package com.maidweapon.forge.system.mastery;

import com.maidweapon.common.data.ContractMasteryData;
import net.minecraft.nbt.CompoundTag;

/** Versioned player records; no entity or carrier payloads. */
public final class ContractMasteryCodec {
    public static ContractMasteryData read(CompoundTag tag) {
        int version = tag.getInt("SchemaVersion");
        if (version > ContractMasteryData.SCHEMA_VERSION) throw new IllegalStateException("Unsupported mastery schema " + version);
        var data = new ContractMasteryData();
        data.setLevel(tag.getInt("Level")); data.setTotalKills(tag.getInt("TotalKills"));
        data.setUnlockedTier(tag.getInt("UnlockedTier")); data.setEnderDragonKills(tag.getInt("EnderDragonKills"));
        data.setWitherKills(tag.getInt("WitherKills"));
        data.setUpgradeProgress(tag.getIntArray("ConfiguredUpgradeProgress"));
        return data;
    }
    public static CompoundTag write(ContractMasteryData data) {
        var tag = new CompoundTag(); tag.putInt("SchemaVersion", ContractMasteryData.SCHEMA_VERSION);
        tag.putInt("Level", data.getLevel()); tag.putInt("TotalKills", data.getTotalKills());
        tag.putInt("UnlockedTier", data.getUnlockedTier()); tag.putInt("EnderDragonKills", data.getEnderDragonKills());
        tag.putInt("WitherKills", data.getWitherKills()); tag.putIntArray("ConfiguredUpgradeProgress", data.getUpgradeProgress());
        return tag;
    }
    private ContractMasteryCodec() { }
}
