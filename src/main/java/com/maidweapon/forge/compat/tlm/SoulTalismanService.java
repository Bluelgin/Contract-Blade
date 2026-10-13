package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Normal transfer uses soul slabs. Only the current destination is recorded; no rotating receipts. */
public final class SoulTalismanService {
    public static final String RECEIPT = "MaidWeaponSoulTransfer"; // legacy binding/interior compatibility
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
        if (owner == null || owner.level().isClientSide || !filled(item) || item.getCount() != 1) return false;
        if (com.maidweapon.forge.system.ContractNbtGuard.serializedSize(item.getTag())
                    > com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get()
                || com.maidweapon.forge.system.ContractNbtGuard.depth(item.getTag())
                    > com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_DEPTH.get()) return false;
        var entity = item.getTag().getCompound("MaidInfo");
        return entity.hasUUID("Owner") && entity.hasUUID("UUID") && owner.getUUID().equals(entity.getUUID("Owner"))
                && ContractWeaponLocator.mayManifest(owner, entity.getUUID("UUID").toString());
    }
    public static boolean authorizes(Player owner, ItemStack carrier) {
        if (owner == null || !ContractCarrierData.isOwner(carrier, owner)
                || ContractCarrierData.isContractSuperseded(carrier)) return false;
        if (owner.getServer() == null) return true;
        String maid = ContractCarrierData.getBoundMaidUUID(carrier);
        if (maid == null) return true;
        var record = ContractRecoveryJournal.get(owner.getServer()).transfer(maid);
        // Current storage is revalidated by the lifecycle (full owner/UUID/load checks), not a historical receipt.
        if (ContractCarrierData.hasMaidEntityData(carrier)) return true;
        var live = ContractWeaponLocator.findManifestedMaid(owner, maid);
        if (live != null && java.util.Objects.equals(ContractCarrierData.getBindingId(carrier),
                live.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID))) return true;
        // No receipt or unknown legacy metadata is not proof of an invalid contract.
        if (!record.contains("Kind")) return true;
        return (!record.hasUUID("Owner") || record.getUUID("Owner").equals(owner.getUUID()))
                && record.getString("Kind").equals("WEAPON")
                && record.getString("Binding").equals(ContractCarrierData.getBindingId(carrier));
    }
    private static void destination(Player owner, ItemStack carrier, String kind, boolean present) {
        if (owner.getServer() == null) return;
        var journal = ContractRecoveryJournal.get(owner.getServer());
        if (!journal.knownSchema()) return; // preserve future files; never make normal storage throw after spawn
        journal.destination(ContractCarrierData.getBoundMaidUUID(carrier), owner.getUUID(),
                ContractCarrierData.getBindingId(carrier), kind,
                present ? ContractRecoveryJournal.Presence.PRESENT : ContractRecoveryJournal.Presence.ABSENT, null);
    }
    public static void imported(Player owner, ItemStack item, ItemStack carrier) {
        destination(owner, carrier, "WEAPON", false);
        carrier.getOrCreateTag().remove(RECEIPT);
    }
    public static void captured(Player owner, ItemStack carrier) {
        imported(owner, ItemStack.EMPTY, carrier);
    }
    public static void manifested(Player owner, ItemStack carrier) {
        destination(owner, carrier, "WEAPON", true);
    }
    public static void exported(Player owner, ItemStack carrier, ItemStack item) {
        var metadata = new CompoundTag(); metadata.putString("Binding", ContractCarrierData.getBindingId(carrier));
        item.getOrCreateTag().put(RECEIPT, metadata); // keep the interior's stable binding
        destination(owner, carrier, "SEAL", false);
    }
    /** Explicit migration of an old ordinary film; emergency films never become transfer slabs. */
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
