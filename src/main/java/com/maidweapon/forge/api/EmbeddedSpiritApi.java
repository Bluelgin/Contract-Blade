package com.maidweapon.forge.api;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Public addon API for weapons which awaken with a built-in contract spirit.
 * The spirit is stored through the same maid contract data used by injected
 * weapons, so deployment, recall, ownership and resonance remain centralized.
 */
public final class EmbeddedSpiritApi {
    public static final String TAG_SPIRIT_ID = "MaidWeaponEmbeddedSpirit";
    public static final String TAG_SPIRIT_NAME = "MaidWeaponEmbeddedSpiritName";
    public static final String TAG_DORMANT = "MaidWeaponEmbeddedSpiritDormant";

    public static boolean bindPresetSpirit(
            Player owner,
            ItemStack weapon,
            String spiritId,
            String displayName) {
        if (owner == null || weapon.isEmpty() || spiritId == null || spiritId.isBlank()
                || spiritId.length() > 64 || ResourceLocation.tryParse(spiritId) == null
                || displayName != null && displayName.length() > 128) {
            return false;
        }
        if (isSpirit(weapon, spiritId)) {
            setDormant(weapon, false);
            return true;
        }
        if (MaidWeaponItem.hasMaidData(weapon)) {
            return false;
        }
        String safeName = displayName == null || displayName.isBlank()
                ? spiritId : displayName;
        if (!TouhouLittleMaidHelper.createPresetSpiritContract(
                owner, weapon, spiritId, safeName)) {
            return false;
        }
        CompoundTag tag = weapon.getOrCreateTag();
        tag.putString(TAG_SPIRIT_ID, spiritId);
        tag.putString(TAG_SPIRIT_NAME, safeName);
        tag.putBoolean(TAG_DORMANT, false);
        return true;
    }

    public static boolean isSpirit(ItemStack weapon, String spiritId) {
        CompoundTag tag = weapon.getTag();
        return tag != null && spiritId != null
                && spiritId.equals(tag.getString(TAG_SPIRIT_ID));
    }

    public static boolean hasSpirit(ItemStack weapon) {
        CompoundTag tag = weapon.getTag();
        return tag != null && !tag.getString(TAG_SPIRIT_ID).isBlank();
    }

    public static void setDormant(ItemStack weapon, boolean dormant) {
        if (!hasSpirit(weapon)) return;
        weapon.getOrCreateTag().putBoolean(TAG_DORMANT, dormant);
    }

    public static boolean isDormant(ItemStack weapon) {
        CompoundTag tag = weapon.getTag();
        return tag != null && tag.getBoolean(TAG_DORMANT);
    }

    public static String getDisplayName(ItemStack weapon) {
        CompoundTag tag = weapon.getTag();
        return tag == null ? "" : tag.getString(TAG_SPIRIT_NAME);
    }

    @Nullable
    public static ResourceLocation getHudTexture(ItemStack weapon) {
        if (isSpirit(weapon, "blade_tetra:akatsuki")) {
            return ResourceLocation.fromNamespaceAndPath(
                    MaidWeaponConstants.MOD_ID, "textures/gui/spirit/akatsuki.png");
        }
        return null;
    }

    private EmbeddedSpiritApi() {
    }
}
