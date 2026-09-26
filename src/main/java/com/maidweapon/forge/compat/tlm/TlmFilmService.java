package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Transfer boundary between TLM film/slab storage and Contract Blade storage. */
public final class TlmFilmService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isMaidFilm(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.equals("touhou_little_maid:smart_slab_has_maid")
                || itemId.equals("touhou_little_maid:film");
    }

    public static boolean isFilledMaidFilm(ItemStack stack) {
        return isMaidFilm(stack) && stack.getTag() != null
                && stack.getTag().contains("MaidInfo");
    }

    public static boolean isEmptyMaidFilm(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.equals("touhou_little_maid:smart_slab_empty")
                || (itemId.equals("touhou_little_maid:film")
                && (stack.getTag() == null || !stack.getTag().contains("MaidInfo")));
    }

    public static ItemStack createEmptyMaidStoreItem(ItemStack filled) {
        String itemId = BuiltInRegistries.ITEM.getKey(filled.getItem()).toString();
        if (itemId.equals("touhou_little_maid:smart_slab_has_maid")) {
            return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "smart_slab_empty")));
        }
        ItemStack empty = filled.copy();
        empty.setCount(1);
        empty.setTag(null);
        return empty;
    }

    public static boolean infuseFromFilm(Player player, ItemStack film, ItemStack weaponStack) {
        if (!isFilledMaidFilm(film) || !MaidInfusion.isWeapon(weaponStack)
                || MaidInfusion.isInfused(weaponStack)) return false;
        CompoundTag originalWeaponTag = weaponStack.getTag() == null
                ? null : weaponStack.getTag().copy();
        try {
            Class<?> maidClass = TlmEntityAdapter.maidClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            TlmEntityAdapter.load(maid, film.getTag().getCompound("MaidInfo").copy());
            if (!TlmEntityAdapter.isOwnedByPlayer(maid, player)) {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.film_not_owner"), true);
                return false;
            }

            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            MaidWeaponItem.setContractSuperseded(weaponStack, false);
            maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, bindingId);
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, maid.getStringUUID());
            if (film.getTag().contains(ContractMaidKeys.FILM_PROGRESS)) {
                weaponStack.getOrCreateTag().put("MaidData",
                        film.getTag().getCompound(ContractMaidKeys.FILM_PROGRESS).copy());
            } else {
                MaidWeaponData data = new MaidWeaponData(maid.getName().getString());
                data.setFavorability(Math.min(MaidWeaponData.MAX_FAVORABILITY,
                        Math.max(0, TlmEntityAdapter.favorability(maid))));
                MaidWeaponItem.setMaidData(weaponStack, data);
            }
            if (!ContractMaidStorage.commit(player, weaponStack, TlmEntityAdapter.save(maid))) {
                weaponStack.setTag(originalWeaponTag);
                return false;
            }
            ContractMaidStorage.retirePreviousContracts(
                    player, maid.getStringUUID(), weaponStack);
            return true;
        } catch (Exception exception) {
            weaponStack.setTag(originalWeaponTag);
            LOGGER.error("[MaidWeapon] Failed to infuse maid film", exception);
            return false;
        }
    }

    public static ItemStack extractMaidToFilm(Player player, ItemStack weapon, ItemStack emptyFilm) {
        if (!MaidInfusion.containsMaid(weapon) || !isEmptyMaidFilm(emptyFilm)
                || !MaidWeaponItem.isOwner(weapon, player)) return ItemStack.EMPTY;
        try {
            CompoundTag weaponTag = weapon.getTag();
            if (weaponTag == null) return ItemStack.EMPTY;
            CompoundTag maidTag = ContractMaidStorage.read(player, weapon);
            if (maidTag == null) return ItemStack.EMPTY;
            Class<?> maidClass = TlmEntityAdapter.maidClass();
            if (maidClass == null) return ItemStack.EMPTY;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            TlmEntityAdapter.load(maid, maidTag);
            if (!TlmEntityAdapter.isOwnedByPlayer(maid, player)) return ItemStack.EMPTY;
            maid.getPersistentData().remove(ContractMaidKeys.ENTITY_BINDING_ID);

            String emptyId = BuiltInRegistries.ITEM.getKey(emptyFilm.getItem()).toString();
            ItemStack filled = emptyId.equals("touhou_little_maid:smart_slab_empty")
                    ? new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "smart_slab_has_maid")))
                    : emptyFilm.copy();
            filled.setCount(1);
            CompoundTag filledTag = filled.getOrCreateTag();
            filledTag.put("MaidInfo", TlmEntityAdapter.save(maid));
            filledTag.put(ContractMaidKeys.FILM_PROGRESS,
                    weaponTag.getCompound("MaidData").copy());
            ContractMaidStorage.inspectExternalPayload(player, filledTag);
            ContractMaidStorage.remove(weapon);
            return filled;
        } catch (Exception exception) {
            LOGGER.error("[MaidWeapon] Failed to extract maid into film", exception);
            return ItemStack.EMPTY;
        }
    }

    /**
     * Builds a TLM-compatible recovery film without discarding the live maid.
     */
    public static ItemStack createEmergencyResurrectionFilm(
            Player player, ItemStack contract, Entity liveMaid) {
        if (contract.isEmpty() || !MaidInfusion.isInfused(contract)
                || !MaidWeaponItem.isOwner(contract, player)) return ItemStack.EMPTY;
        try {
            CompoundTag maidData;
            if (liveMaid != null) {
                if (!TlmEntityAdapter.isOwnedMaid(liveMaid, player)
                        || !MaidWeaponItem.isBoundMaid(contract, liveMaid)) {
                    return ItemStack.EMPTY;
                }
                MaidCareTaskSystem.restoreOriginalTask(contract, liveMaid);
                if (liveMaid instanceof LivingEntity living) {
                    TripleMagicCompat.clearPhantoms(living, contract);
                    TaczCompat.clear(player, living, contract);
                }
                maidData = TlmEntityAdapter.save(liveMaid);
            } else {
                maidData = ContractMaidStorage.read(player, contract);
                if (maidData == null) return ItemStack.EMPTY;
            }

            maidData.putString("id", "touhou_little_maid:maid");
            CompoundTag contractTag = contract.getTag();
            CompoundTag progress = contractTag != null
                    ? contractTag.getCompound("MaidData").copy() : new CompoundTag();
            CompoundTag forgeData = maidData.getCompound("ForgeData");
            forgeData.remove(ContractMaidKeys.ENTITY_BINDING_ID);
            if (!progress.isEmpty()) {
                forgeData.put(ContractMaidKeys.EMERGENCY_FILM_PROGRESS, progress.copy());
            }
            maidData.put("ForgeData", forgeData);

            ItemStack film = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "film")));
            if (film.isEmpty()) return ItemStack.EMPTY;
            CompoundTag filmTag = film.getOrCreateTag();
            filmTag.put("MaidInfo", maidData);
            if (!progress.isEmpty()) {
                filmTag.put(ContractMaidKeys.FILM_PROGRESS, progress);
            }

            Class<?> maidClass = TlmEntityAdapter.maidClass();
            if (maidClass == null) return ItemStack.EMPTY;
            Entity verifier = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            TlmEntityAdapter.load(verifier, filmTag.getCompound("MaidInfo").copy());
            if (!TlmEntityAdapter.isOwnedByPlayer(verifier, player)) return ItemStack.EMPTY;

            ContractMaidStorage.inspectExternalPayload(player, filmTag);
            return film;
        } catch (Exception exception) {
            LOGGER.error("[MaidWeapon] Failed to create emergency resurrection film", exception);
            return ItemStack.EMPTY;
        }
    }

    private TlmFilmService() {}
}
