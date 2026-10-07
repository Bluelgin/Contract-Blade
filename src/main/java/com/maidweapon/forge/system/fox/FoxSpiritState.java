package com.maidweapon.forge.system.fox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Item-local identity only; it never creates a maid or decides server authority. */
public final class FoxSpiritState {
    public static final String WHITE = "maid_weapon:fox_white";
    public static final String BLACK = "maid_weapon:fox_black";
    public static final String OFFERING = "MaidWeaponShrineWhiteFox";
    public static final String ROOT = "MaidWeaponFoxSpirit";
    public static final String SEAL = "MaidWeaponFoxSoulSeal";
    public static final String ORIGIN = "MaidWeaponFoxOrigin";
    public static final String VACANT = "MaidWeaponFoxVacant";

    public static CompoundTag resident(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getCompound(ROOT) : new CompoundTag();
    }

    public static boolean valid(CompoundTag identity) {
        return identity.getInt("Version") == 1 && (WHITE.equals(identity.getString("SpiritId"))
                || BLACK.equals(identity.getString("SpiritId")))
                && identity.hasUUID("SpiritUUID") && identity.hasUUID("OriginUUID")
                && identity.hasUUID("OwnerUUID") && identity.hasUUID("Token");
    }

    public static boolean hasResident(ItemStack stack) {
        return valid(resident(stack));
    }

    public static boolean isSeal(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(SEAL);
    }

    public static CompoundTag sealed(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getCompound(SEAL) : new CompoundTag();
    }

    /** Includes unclaimed offerings: they must be protected before the first pickup tick. */
    public static boolean isProtected(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (hasResident(stack) || isSeal(stack)
                || stack.hasTag() && stack.getTag().getBoolean(OFFERING)
                && !stack.getTag().getBoolean(VACANT));
    }

    /** A look-alike named blade or a second shrine's blade is not her original home. */
    public static boolean isOriginalHome(ItemStack stack) {
        CompoundTag identity = resident(stack);
        return valid(identity) && stack.hasTag() && stack.getTag().hasUUID(ORIGIN)
                && identity.getUUID("OriginUUID").equals(stack.getTag().getUUID(ORIGIN));
    }

    private FoxSpiritState() { }
}
