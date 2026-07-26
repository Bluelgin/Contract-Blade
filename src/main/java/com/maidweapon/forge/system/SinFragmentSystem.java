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
        if (!MaidInfusion.isInfused(weapon)) return;
        MaidWeaponData data = MaidInfusion.data(weapon);
        ArrayList<SinType> remaining = new ArrayList<>(data.getEmbeddedSins());
        boolean changed = false;
        for (SinType sin : data.getEmbeddedSins()) {
            int next = getPower(weapon, sin) - 1;
            weapon.getOrCreateTagElement(TAG).putInt(sin.getId(), Math.max(0, next));
            if (next <= 0) {
                remaining.remove(sin);
                Item residue = BuiltInRegistries.ITEM.get(
                        new ResourceLocation("maid_weapon", "sin_residue"));
                if (residue != Items.AIR) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(residue));
                }
                player.displayClientMessage(Component.translatable(
                        "maid_weapon.message.sin_exhausted", sin.getChineseName()), true);
                changed = true;
            }
        }
        if (changed) {
            data.setEmbeddedSins(remaining);
            MaidWeaponItem.setMaidData(weapon, data);
        }
    }

    private SinFragmentSystem() {}
}
