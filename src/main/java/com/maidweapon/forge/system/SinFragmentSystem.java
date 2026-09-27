package com.maidweapon.forge.system;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.sin.SinType;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;

/**
 * @deprecated Legacy Part compatibility shim. Core preserves old power tags but
 * does not consume or apply them.
 */
@Deprecated
public final class SinFragmentSystem {
    private static final String TAG = "SinFragmentPower";
    public static final int MAX_POWER = 64;

    public static void initialize(ItemStack weapon, SinType sin) {
        weapon.getOrCreateTag().getCompound(TAG);
        CompoundTag power = weapon.getOrCreateTagElement(TAG);
        power.putInt(sin.getId(), MAX_POWER);
    }

    public static int getPower(ItemStack weapon, SinType sin) {
        CompoundTag power = weapon.getTagElement(TAG);
        if (power == null) return MAX_POWER;
        return power.contains(sin.getId()) ? power.getInt(sin.getId()) : MAX_POWER;
    }

    public static void consumeOnKill(Player player, ItemStack weapon) {
        // Intentionally no-op: the Part campaign is archived and Core must not
        // mutate legacy seven-sins progression.
    }

    private SinFragmentSystem() {}
}
