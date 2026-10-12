package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Normal transfer uses soul slabs. Films, emergency payloads and fox seals never enter the ordinary path. */
public final class SoulTalismanService {
    public static final String RECEIPT = "MaidWeaponSoulTransfer";
    public static final String EMERGENCY = "MaidWeaponEmergencyFilm";
    private static String id(ItemStack item) { return BuiltInRegistries.ITEM.getKey(item.getItem()).toString(); }
    public static boolean empty(ItemStack item) {
        return !item.isEmpty() && !FoxSpiritState.isSeal(item) && id(item).equals("touhou_little_maid:smart_slab_empty")
                && (!item.hasTag() || !item.getTag().contains("MaidInfo") && !item.getTag().contains(EMERGENCY));
    }
    public static boolean filled(ItemStack item) {
        return !item.isEmpty() && !FoxSpiritState.isSeal(item) && id(item).equals("touhou_little_maid:smart_slab_has_maid")
                && item.hasTag() && item.getTag().contains("MaidInfo", 10) && !item.getTag().contains(EMERGENCY)
                && !item.getTag().getCompound("MaidInfo").getCompound("ForgeData").contains("MaidWeaponEmbeddedSpirit");
    }
    public static boolean importAllowed(Player owner, ItemStack item) {
        if (owner == null || owner.level().isClientSide || item.hasTag() && item.getTag().getBoolean("MaidWeaponClientSuppliedContract") || !filled(item) || item.getCount() != 1) return false;
        if (com.maidweapon.forge.system.ContractNbtGuard.serializedSize(item.getTag())
                    > com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get()
                || com.maidweapon.forge.system.ContractNbtGuard.depth(item.getTag())
                    > com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_DEPTH.get()) return false;
        var entity = item.getTag().getCompound("MaidInfo");
        if (!entity.hasUUID("Owner") || !entity.hasUUID("UUID") || !owner.getUUID().equals(entity.getUUID("Owner"))) return false;
        var maid = entity.getUUID("UUID").toString();
        if (ContractWeaponLocator.findManifestedMaid(owner, maid) != null) return false;
        var record = ContractRecoveryJournal.get(owner.getServer()).transfer(maid);
        if (record.isEmpty()) return entity.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID).isEmpty()
                && ContractWeaponLocator.findBoundWeapon(owner, maid).isEmpty(); // native legacy slab, not a lost weapon copy
        var receipt = item.getTag().getCompound(RECEIPT);
        return record.hasUUID("Owner") && record.getUUID("Owner").equals(owner.getUUID())
                && record.getString("Kind").equals("SEAL") && receipt.hasUUID("Token")
                && record.hasUUID("Token") && record.getUUID("Token").equals(receipt.getUUID("Token"))
                && record.getString("Binding").equals(receipt.getString("Binding"));
    }
    public static boolean authorizes(Player owner, ItemStack carrier) {
        String maid = ContractCarrierData.getBoundMaidUUID(carrier);
        if (carrier.hasTag() && carrier.getTag().getBoolean("MaidWeaponClientSuppliedContract")) return false;
        if (owner.getServer() == null) return true;
        var journal = ContractRecoveryJournal.get(owner.getServer());
        if (!journal.knownSchema()) return false;
        if (maid == null) return true;
        if (ContractWeaponLocator.hasConflictingCarrier(owner, carrier)) return false;
        var record = journal.transfer(maid);
        if (record.isEmpty()) return true;
        var receipt = carrier.hasTag() ? carrier.getTag().getCompound(RECEIPT) : new CompoundTag();
        return record.getString("Kind").equals("WEAPON") && record.hasUUID("Owner") && record.getUUID("Owner").equals(owner.getUUID())
                && record.getString("Binding").equals(ContractCarrierData.getBindingId(carrier))
                && record.hasUUID("Token") && receipt.hasUUID("Token") && record.getUUID("Token").equals(receipt.getUUID("Token"));
    }
    public static void imported(Player owner, ItemStack item, ItemStack carrier) {
        CompoundTag record = new CompoundTag(); record.putUUID("Owner", owner.getUUID());
        record.putString("Kind", "WEAPON"); record.putString("Binding", ContractCarrierData.getBindingId(carrier));
        record.putUUID("Token", UUID.randomUUID()); carrier.getOrCreateTag().put(RECEIPT, record.copy());
        ContractRecoveryJournal.get(owner.getServer()).transfer(ContractCarrierData.getBoundMaidUUID(carrier), record);
    }
    public static void captured(Player owner, ItemStack carrier) {
        var record = ContractRecoveryJournal.get(owner.getServer()).transfer(ContractCarrierData.getBoundMaidUUID(carrier));
        if (record.isEmpty() || record.getString("Kind").equals("WEAPON") && authorizes(owner, carrier)
                || record.getString("Kind").equals("FREE_MAID") && record.hasUUID("Owner") && record.getUUID("Owner").equals(owner.getUUID()))
            imported(owner, ItemStack.EMPTY, carrier);
    }
    public static void exported(Player owner, ItemStack carrier, ItemStack item) {
        var record = new CompoundTag(); record.putUUID("Owner", owner.getUUID()); record.putUUID("Token", UUID.randomUUID());
        record.putString("Kind", "SEAL"); record.putString("Binding", ContractCarrierData.getBindingId(carrier));
        item.getOrCreateTag().put(RECEIPT, record.copy());
        ContractRecoveryJournal.get(owner.getServer()).transfer(ContractCarrierData.getBoundMaidUUID(carrier), record);
    }
    /** Explicit operator migration. No input mutation until the player can receive the validated slab. */
    public static ItemStack migrateLegacy(Player owner, ItemStack film) {
        if (film.isEmpty() || film.getCount() != 1 || !id(film).equals("touhou_little_maid:film")
                || !film.hasTag() || film.getTag().contains(EMERGENCY) || FoxSpiritState.isSeal(film)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:smart_slab_has_maid")));
        if (result.isEmpty()) return result;
        result.setTag(film.getTag().copy());
        return importAllowed(owner, result) ? result : ItemStack.EMPTY;
    }
    private SoulTalismanService() { }
}
