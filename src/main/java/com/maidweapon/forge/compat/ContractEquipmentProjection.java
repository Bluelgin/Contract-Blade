package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.deployment.ContractProjectionMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Slot-scoped projection transaction. Originals are saved only for replaced slots. */
public final class ContractEquipmentProjection {
    private static final String GEAR = "MaidWeaponProjectionGear";
    private static final String LEGACY_GEAR = "MaidWeaponOriginalGear";
    private static final String LEGACY_ATTACK = "MaidWeaponOriginalAttack";
    private static final String LEGACY_ARMOR = "MaidWeaponOriginalArmor";

    public static void maintain(Player owner, LivingEntity maid, ItemStack source,
                                ContractProjectionMode mode) {
        migrateLegacy(maid, source);
        // TLM temporarily holds food while eating. Never replace or consume it here.
        if (maid.isUsingItem()) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            boolean enabled = slot.getType() == EquipmentSlot.Type.HAND ? mode.weapon() : mode.armor();
            if (!enabled) {
                restoreSlot(maid, source, slot);
                continue;
            }
            ItemStack original = slot == EquipmentSlot.MAINHAND ? source
                    : slot == EquipmentSlot.OFFHAND ? TripleMagicCompat.projectionAccessory(owner, source)
                    : owner.getItemBySlot(slot);
            CompoundTag gear = maid.getPersistentData().getCompound(GEAR);
            String key = slot.getName();
            ItemStack current = maid.getItemBySlot(slot);
            if (!gear.contains(key)) {
                gear.put(key, current.save(new CompoundTag()));
                maid.getPersistentData().put(GEAR, gear);
            } else if (!current.isEmpty() && !TripleMagicCompat.isPhantom(current)) {
                continue; // Respect equipment the player deliberately changed during projection.
            }
            if (slot == EquipmentSlot.MAINHAND && SlashBladeCompat.isMatchingPhantom(source, current)) {
                SlashBladeCompat.syncPhantomProgress(source, current);
                continue;
            }
            ItemStack replacement = original.isEmpty() ? ItemStack.EMPTY
                    : TripleMagicCompat.createProjection(original, owner);
            if (!ItemStack.isSameItemSameTags(current, replacement)) maid.setItemSlot(slot, replacement);
        }
    }

    public static void clear(LivingEntity maid, ItemStack source) {
        migrateLegacy(maid, source);
        for (EquipmentSlot slot : EquipmentSlot.values()) restoreSlot(maid, source, slot);
        maid.getPersistentData().remove(GEAR);
        EpicFightCompat.clearSelection(maid);
    }

    private static void restoreSlot(LivingEntity maid, ItemStack source, EquipmentSlot slot) {
        CompoundTag gear = maid.getPersistentData().getCompound(GEAR);
        String key = slot.getName();
        if (!gear.contains(key)) return;
        if (slot == EquipmentSlot.MAINHAND) {
            TripleMagicCompat.syncSlashBladeProgress(maid, source);
            EpicFightCompat.clearSelection(maid);
        }
        ItemStack current = maid.getItemBySlot(slot);
        ItemStack original = ItemStack.of(gear.getCompound(key));
        if (current.isEmpty() || TripleMagicCompat.isPhantom(current)) {
            maid.setItemSlot(slot, original);
        } else if (!ItemStack.isSameItemSameTags(current, original)) {
            com.maidweapon.forge.compat.tlm.TlmEquipmentReturns.returnOriginal(maid, original);
        }
        gear.remove(key);
        if (gear.isEmpty()) maid.getPersistentData().remove(GEAR);
        else maid.getPersistentData().put(GEAR, gear);
    }

    /** Upgrade old automatic projections without retaining their copied player base stats. */
    private static void migrateLegacy(LivingEntity maid, ItemStack source) {
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(LEGACY_GEAR) && !data.contains(LEGACY_ATTACK) && !data.contains(LEGACY_ARMOR)) return;
        TripleMagicCompat.syncSlashBladeProgress(maid, source);
        CompoundTag gear = data.getCompound(LEGACY_GEAR);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (TripleMagicCompat.isPhantom(maid.getItemBySlot(slot))) {
                maid.setItemSlot(slot, ItemStack.of(gear.getCompound(slot.getName())));
            }
        }
        if (data.contains(LEGACY_ATTACK) && maid.getAttribute(Attributes.ATTACK_DAMAGE) != null)
            maid.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(data.getDouble(LEGACY_ATTACK));
        if (data.contains(LEGACY_ARMOR) && maid.getAttribute(Attributes.ARMOR) != null)
            maid.getAttribute(Attributes.ARMOR).setBaseValue(data.getDouble(LEGACY_ARMOR));
        data.remove(LEGACY_GEAR);
        data.remove(LEGACY_ATTACK);
        data.remove(LEGACY_ARMOR);
    }

    private ContractEquipmentProjection() { }
}
