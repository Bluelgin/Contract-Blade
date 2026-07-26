package com.maidweapon.forge.item;

import com.maidweapon.common.data.MaidWeaponData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/** Shared access point for maid data stored on any weapon ItemStack. */
public final class MaidInfusion {
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (com.maidweapon.forge.compat.SlashBladeCompat.isSlashBlade(stack)) return true;
        if (com.maidweapon.forge.compat.TripleMagicCompat.isMagicCatalyst(stack)) return true;
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() == com.maidweapon.forge.init.ModBlocks.MAID_INJECTOR.get()) return false;
        if (stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem) return true;
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .containsKey(Attributes.ATTACK_DAMAGE);
    }

    public static boolean isInfused(ItemStack stack) {
        return isWeapon(stack) && MaidWeaponItem.hasMaidData(stack);
    }

    public static boolean containsMaid(ItemStack stack) {
        return isInfused(stack) && MaidWeaponItem.hasMaidEntityData(stack);
    }

    public static boolean isContractBlade(ItemStack stack) {
        return stack.getItem() instanceof MaidWeaponItem;
    }

    public static MaidWeaponData data(ItemStack stack) {
        return MaidWeaponItem.getMaidData(stack);
    }

    /** Generic weapons only gain damage; low favorability can never reduce base damage. */
    public static float getGenericDamageBonus(MaidWeaponData data) {
        return Math.max(0.0f,
                data.getAttackDamageBonus() * data.getFavorabilityDamageMultiplier());
    }

    private MaidInfusion() {}
}
