package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.ContractNbtGuard;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Explicit, owner-only migration via the contract table's two output slots. */
public final class FoxSpiritTransferService {
    public record Result(ItemStack weapon, ItemStack seal) { }

    /** Claim the story identity only. Models and full maid contracts remain optional. */
    public static void claimOffering(ServerPlayer owner, ItemStack blade) {
        com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.ensureEffect(blade);
        if (!blade.hasTag() || !blade.getTag().getBoolean(FoxSpiritState.OFFERING)
                || blade.getTag().getBoolean(FoxSpiritState.VACANT)
                || blade.getTag().contains(FoxSpiritState.ROOT)
                || !SlashBladeCompat.isNamedBlade(blade, "item.slashblade.fox_white")) return;
        UUID origin = UUID.randomUUID();
        CompoundTag identity = new CompoundTag();
        identity.putInt("Version", 1);
        identity.putString("SpiritId", FoxSpiritState.WHITE);
        identity.putUUID("SpiritUUID", UUID.randomUUID());
        identity.putUUID("OriginUUID", origin);
        identity.putUUID("OwnerUUID", owner.getUUID());
        identity.putUUID("Token", UUID.randomUUID());
        blade.getOrCreateTag().putUUID(FoxSpiritState.ORIGIN, origin);
        blade.getOrCreateTag().put(FoxSpiritState.ROOT, identity);
        FoxSpiritLedger.get(owner.getServer()).record(identity, "WEAPON");
    }

    public static boolean handles(ItemStack weapon, ItemStack seal) {
        return FoxSpiritState.hasResident(weapon) || FoxSpiritState.isSeal(seal);
    }

    public static boolean canTransfer(Player owner, ItemStack weapon, ItemStack seal) {
        if (weapon.getCount() != 1 || seal.getCount() != 1
                || owner.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)) return false;
        if (FoxSpiritState.isSeal(seal)) {
            var identity = FoxSpiritState.sealed(seal);
            if (!FoxSpiritState.valid(identity) || !identity.getUUID("OwnerUUID").equals(owner.getUUID())
                    || !isSoulItem(seal) || !MaidInfusion.isWeapon(weapon)
                    || occupied(weapon)) return false;
            return !(owner instanceof ServerPlayer serverPlayer)
                    || FoxSpiritLedger.get(serverPlayer.getServer()).owns(identity, "SEAL");
        }
        CompoundTag identity = FoxSpiritState.resident(weapon);
        return FoxSpiritState.valid(identity) && identity.getUUID("OwnerUUID").equals(owner.getUUID())
                && TouhouLittleMaidHelper.isEmptyMaidFilm(seal)
                && (!(owner instanceof ServerPlayer serverPlayer)
                || FoxSpiritLedger.get(serverPlayer.getServer()).owns(identity, "WEAPON"));
    }

    public static boolean isSoulItem(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("touhou_little_maid:film") || id.equals("touhou_little_maid:smart_slab_empty")
                || id.equals("touhou_little_maid:smart_slab_has_maid");
    }

    private static boolean occupied(ItemStack weapon) {
        return FoxSpiritState.isProtected(weapon) || ContractCarrierData.hasMaidData(weapon)
                || ContractCarrierData.hasMaidEntityData(weapon) || weapon.hasTag()
                && (weapon.getTag().contains("MaidWeaponIntrinsicSpirits")
                || weapon.getTag().contains("MaidWeaponExternalContract")
                || weapon.getTag().contains("MaidWeaponEmbeddedSpirit")
                || weapon.getTag().contains("MaidUUID")
                || weapon.getTag().contains("MaidBindingId"));
    }

    /** A result is prepared in copies; the menu commits both outputs in one server action. */
    public static Result transfer(ServerPlayer owner, ItemStack weapon, ItemStack seal) {
        if (!canTransfer(owner, weapon, seal)) return null;
        return FoxSpiritState.isSeal(seal) ? infuse(owner, weapon, seal) : extract(owner, weapon, seal);
    }

    private static Result extract(ServerPlayer owner, ItemStack source, ItemStack emptySeal) {
        // Never recall across dimensions or beyond the player-facing recall range.
        String spirit = FoxSpiritState.resident(source).getString("SpiritId");
        if (!IntrinsicSpiritApi.prepareSpiritTransfer(owner, source, spirit)) return null;
        ItemStack clean = source.copy();
        CompoundTag identity = FoxSpiritState.resident(source).copy();
        CompoundTag contract = IntrinsicSpiritApi.detachStoredSpirit(clean, spirit);
        if (contract == null) return null;
        if (!contract.isEmpty()) identity.put("Contract", contract);
        identity.putUUID("Token", UUID.randomUUID());
        String emptyId = BuiltInRegistries.ITEM.getKey(emptySeal.getItem()).toString();
        ItemStack filled = emptyId.equals("touhou_little_maid:smart_slab_empty")
                ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:smart_slab_has_maid")))
                : emptySeal.copy();
        if (filled.isEmpty()) return null;
        filled.setCount(1);
        filled.setTag(null);
        filled.getOrCreateTag().put(FoxSpiritState.SEAL, identity);
        if (!safe(filled.getTag())) return null;
        clean.getOrCreateTag().remove(FoxSpiritState.ROOT);
        clean.getOrCreateTag().putBoolean(FoxSpiritState.VACANT, true);
        FoxSpiritLedger.get(owner.getServer()).record(identity, "SEAL");
        return new Result(clean, filled);
    }

    private static Result infuse(ServerPlayer owner, ItemStack target, ItemStack filledSeal) {
        CompoundTag identity = FoxSpiritState.sealed(filledSeal).copy();
        String spirit = identity.getString("SpiritId");
        if (!safe(identity)) return null;
        ItemStack result = target.copy();
        if (identity.contains("Contract")) {
            CompoundTag contract = identity.getCompound("Contract");
            if (!contract.hasUUID("OwnerUUID") || !contract.getUUID("OwnerUUID").equals(owner.getUUID())
                    || !spirit.equals(contract.getString(
                    com.maidweapon.forge.api.EmbeddedSpiritApi.TAG_SPIRIT_ID))) return null;
            if (!IntrinsicSpiritApi.attachStoredSpirit(result, spirit,
                    contract)) return null;
            identity.remove("Contract");
        }
        identity.putUUID("Token", UUID.randomUUID());
        result.getOrCreateTag().put(FoxSpiritState.ROOT, identity);
        result.getOrCreateTag().remove(FoxSpiritState.VACANT);
        IntrinsicSpiritApi.allowTransferredSpiritInitialization(result, spirit);
        if (!safe(result.getTag())) return null;
        ItemStack empty = TouhouLittleMaidHelper.createEmptyMaidStoreItem(filledSeal);
        if (empty.isEmpty()) return null;
        FoxSpiritLedger.get(owner.getServer()).record(identity, "WEAPON");
        return new Result(result, empty);
    }

    public static boolean storyEligible(ServerPlayer owner, ItemStack weapon) {
        return FoxSpiritState.isOriginalHome(weapon)
                && FoxSpiritState.WHITE.equals(FoxSpiritState.resident(weapon).getString("SpiritId"))
                && SlashBladeCompat.isNamedBlade(weapon, "item.slashblade.fox_white")
                && FoxSpiritState.resident(weapon).getUUID("OwnerUUID").equals(owner.getUUID())
                && FoxSpiritLedger.get(owner.getServer()).owns(FoxSpiritState.resident(weapon), "WEAPON");
    }

    /** Stale copied contracts cannot manifest the spirit after her authority moved. */
    public static boolean authorizesContract(Player owner, ItemStack weapon) {
        if (!com.maidweapon.forge.api.EmbeddedSpiritApi.isSpirit(weapon, FoxSpiritState.WHITE)
                && !com.maidweapon.forge.api.EmbeddedSpiritApi.isSpirit(weapon, FoxSpiritState.BLACK)) return true;
        return owner instanceof ServerPlayer serverPlayer && FoxSpiritState.hasResident(weapon)
                && com.maidweapon.forge.api.EmbeddedSpiritApi.isSpirit(weapon,
                FoxSpiritState.resident(weapon).getString("SpiritId"))
                && FoxSpiritState.resident(weapon).getUUID("OwnerUUID").equals(owner.getUUID())
                && FoxSpiritLedger.get(serverPlayer.getServer()).owns(FoxSpiritState.resident(weapon), "WEAPON");
    }

    private static boolean safe(CompoundTag payload) {
        return payload != null && ContractNbtGuard.serializedSize(payload)
                <= com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get()
                && ContractNbtGuard.depth(payload)
                <= com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_DEPTH.get();
    }

    private FoxSpiritTransferService() { }
}
