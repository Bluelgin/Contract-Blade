package com.maidweapon.forge.system.mastery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Never import legacy progression created by a creative inventory packet. Existing server originals remain valid. */
public final class ContractMasteryCreativeGuard {
    public static void sanitize(ServerPlayer player, ItemStack incoming) {
        if (incoming.isEmpty()) return;
        var known = ContractMasterySavedData.get(player.getServer());
        for (CompoundTag channel : ContractMasteryMigration.channels(incoming)) {
            if (!channel.getCompound("MaidData").contains("Level")) continue;
            boolean trusted = ContractMasteryMigration.validLegacy(channel)
                    && channel.getUUID("OwnerUUID").equals(player.getUUID())
                    && known.matchesSource(player.getUUID(), ContractMasteryMigration.source(channel),
                            ContractMasteryCodec.read(channel.getCompound("MaidData")));
            if (!trusted) channel.getCompound("MaidData").putBoolean("ClientSuppliedGrowth", true);
        }
        if (incoming.hasTag()) {
            var tag = incoming.getTag();
            tag.getCompound(com.maidweapon.forge.compat.tlm.ContractMaidKeys.FILM_PROGRESS).putBoolean("ClientSuppliedGrowth", true);
            var forge = tag.getCompound("MaidInfo").getCompound("ForgeData");
            var progress = forge.getCompound(com.maidweapon.forge.compat.tlm.ContractMaidKeys.EMERGENCY_FILM_PROGRESS);
            if (progress.contains("Level")) progress.putBoolean("ClientSuppliedGrowth", true);
        }

    }
    private ContractMasteryCreativeGuard() { }
}
