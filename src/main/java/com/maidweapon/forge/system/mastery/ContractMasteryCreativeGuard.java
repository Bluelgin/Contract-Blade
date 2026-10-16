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
    }
    public static final String UNTRUSTED_CONTRACT = "MaidWeaponClientSuppliedContract";
    public static void protectCopies(ServerPlayer player, net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket packet) {
        ItemStack incoming = packet.getItem();
        if (!incoming.hasTag()) return;
        var tag = incoming.getTag();
        if (!tag.contains("MaidData") && !tag.contains("MaidInfo") && !tag.contains("MaidWeaponFoxSpirit")
                && !tag.contains("MaidWeaponFoxSoulSeal") && !tag.contains("MaidWeaponEmergencyFilm")
                && !tag.contains("MaidWeaponShrineWhiteFox") && !tag.contains("MaidWeaponFoxOrigin")
                && !tag.contains("MaidWeaponBlackFoxSwordArt")) return;
        int slot = packet.getSlotNum();
        ItemStack existing = slot >= 0 && slot < player.inventoryMenu.slots.size()
                ? player.inventoryMenu.getSlot(slot).getItem() : ItemStack.EMPTY;
        if (existing.isEmpty() || incoming.getCount() != existing.getCount()
                || !incoming.save(new CompoundTag()).equals(existing.save(new CompoundTag())))
            incoming.getOrCreateTag().putBoolean(UNTRUSTED_CONTRACT, true);
    }
    private ContractMasteryCreativeGuard() { }
}
