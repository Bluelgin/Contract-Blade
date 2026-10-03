package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

/** Optional bridge enabled only when Iron's Spells, Goety and TLM Spell are all loaded. */
public final class TripleMagicCompat {
    public static final String PHANTOM_TAG = "MaidWeaponPhantomCopy";
    private static final String MAGIC_LOADOUT = "MaidWeaponMagicLoadout";
    private static final String MAID_SPELL = "touhou_little_maid_spell";
    private static final String IRONS = "irons_spellbooks";
    private static final String GOETY = "goety";
    private static Method tlmRestoreHandMethod;
    private static boolean searchedTlmRestoreHandMethod;
    private static Method ironIsSpellContainerMethod;
    private static boolean searchedIronSpellContainerMethod;
    private static String maidSpellRangedTaskId;
    private static boolean searchedMaidSpellRangedTaskId;

    public static boolean active() {
        ModList mods = ModList.get();
        return mods.isLoaded(IRONS) && mods.isLoaded(GOETY) && mods.isLoaded(MAID_SPELL);
    }

    public static boolean isMaidSpellLoaded() {
        return ModList.get().isLoaded(MAID_SPELL);
    }

    public static boolean hasMagicProvider() {
        ModList mods = ModList.get();
        return mods.isLoaded(IRONS) || mods.isLoaded(GOETY);
    }

    public static boolean isMagicCatalyst(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath();
        return switch (id.getNamespace()) {
            case IRONS -> ModList.get().isLoaded(IRONS)
                    && (path.contains("staff") || path.contains("wand")
                    || path.contains("spell_book") || path.contains("spellbook")
                    || isIronSpellContainer(stack));
            case GOETY -> ModList.get().isLoaded(GOETY)
                    && (path.contains("wand") || path.contains("staff")
                    || path.endsWith("_focus"));
            case MAID_SPELL -> isMaidSpellLoaded()
                    && (path.contains("book") || path.contains("crystal"));
            default -> false;
        };
    }

    /** True when Wan Fa Jie Tong can drive the real ranged spell-combat task. */
    public static boolean usesMaidSpellTask(ItemStack stack) {
        return isMaidSpellLoaded() && isMagicCatalyst(stack);
    }

    /**
     * Reads the task UID from Wan Fa Jie Tong itself. This keeps task selection
     * working if an older or newer compatible release changes its namespace.
     */
    public static String getMaidSpellRangedTaskId() {
        if (!searchedMaidSpellRangedTaskId) {
            searchedMaidSpellRangedTaskId = true;
            try {
                Class<?> task = Class.forName(
                        "com.github.yimeng261.maidspell.task.SpellCombatFarTask");
                Object uid = task.getField("UID").get(null);
                if (uid instanceof ResourceLocation id) {
                    maidSpellRangedTaskId = id.toString();
                }
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        return maidSpellRangedTaskId == null
                ? "maidspell:spell_combat_far" : maidSpellRangedTaskId;
    }

    public static void equipPhantoms(Player owner, LivingEntity maid, ItemStack source) {
        ContractEquipmentProjection.maintain(owner, maid, source,
                com.maidweapon.forge.compat.tlm.TlmProjectionBaubles.mode(maid));
    }

    public static void clearPhantoms(LivingEntity maid) {
        clearPhantoms(maid, ItemStack.EMPTY);
    }

    public static void clearPhantoms(LivingEntity maid, ItemStack source) {
        restoreTlmEatingHand(maid);
        ContractEquipmentProjection.clear(maid, source);
        maid.getPersistentData().remove(MAGIC_LOADOUT);
    }

    /** Applies all permanent SlashBlade deltas without touching transient combos or targets. */
    public static boolean syncSlashBladeProgress(LivingEntity maid, ItemStack source) {
        if (!SlashBladeCompat.isSlashBlade(source)) return false;
        ItemStack phantom = maid.getMainHandItem();
        return isPhantom(phantom)
                && SlashBladeCompat.syncPhantomProgress(source, phantom);
    }

    /** Cancels a TLM meal without losing the temporary hand stack before the maid is stored. */
    private static void restoreTlmEatingHand(LivingEntity maid) {
        if (!maid.isUsingItem() || !maid.getUseItem().isEdible()
                || !maid.getClass().getName().equals(
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid")) return;
        try {
            maid.stopUsingItem();
            if (!searchedTlmRestoreHandMethod) {
                searchedTlmRestoreHandMethod = true;
                tlmRestoreHandMethod = maid.getClass().getDeclaredMethod("backCurrentHandItemStack");
                tlmRestoreHandMethod.setAccessible(true);
            }
            if (tlmRestoreHandMethod != null) tlmRestoreHandMethod.invoke(maid);
        } catch (ReflectiveOperationException ignored) {
            // Keeping the food in the serialized hand is safer than deleting it if TLM changes internals.
        }
    }

    public static void purgeLeakedCopies(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isPhantom(player.getInventory().getItem(i))) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (isPhantom(player.getOffhandItem())) player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    /**
     * Refreshes Wan Fa Jie Tong's spell-book manager after the phantom staff and
     * focus bag have been equipped. The manager scans both maid hands, so an
     * Iron's staff contributes its spell container and a Goety staff uses the
     * owner's copied focus bag.
     */
    public static boolean syncMaidSpellLoadout(Player owner, LivingEntity maid, ItemStack weapon) {
        if (!usesMaidSpellTask(weapon)) return false;
        ResourceLocation weaponId = ForgeRegistries.ITEMS.getKey(weapon.getItem());
        ItemStack accessory = equippedMagicAccessory(owner);
        ResourceLocation accessoryId = accessory.isEmpty() ? null
                : ForgeRegistries.ITEMS.getKey(accessory.getItem());
        String loadout = String.valueOf(weaponId) + "|" + String.valueOf(accessoryId);
        if (loadout.equals(maid.getPersistentData().getString(MAGIC_LOADOUT))) return true;
        try {
            Class<?> maidType = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            if (!maidType.isInstance(maid)) return false;
            Class<?> managerType = Class.forName(
                    "com.github.yimeng261.maidspell.spell.manager.SpellBookManager");
            Object manager = managerType.getMethod("getOrCreateManager", maidType)
                    .invoke(null, maid);
            managerType.getMethod("setMaid", maidType).invoke(manager, maid);
            managerType.getMethod("initSpellBooks").invoke(manager);
            maid.getPersistentData().putString(MAGIC_LOADOUT, loadout);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            maid.getPersistentData().remove(MAGIC_LOADOUT);
            return false;
        }
    }

    /**
     * Lightweight ranged fallback when a supported magic mod is present without
     * Wan Fa Jie Tong. With Wan Fa Jie Tong installed its real providers cast,
     * so this method intentionally does nothing to avoid duplicate damage.
     */
    public static boolean castFallbackSpell(Player owner, LivingEntity maid, ItemStack weapon) {
        if (isMaidSpellLoaded() || !isMagicCatalyst(weapon) || !(maid instanceof Mob mob)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || maid.tickCount % 50 != 0) return false;
        int cost = 4;
        if (!pay(owner, weapon, cost)) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(weapon.getItem());
        boolean ice = id != null && id.getNamespace().equals(IRONS);
        target.hurt(maid.level().damageSources().indirectMagic(maid, owner), ice ? 7 : 8);
        if (ice) {
            target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 80, target.getTicksFrozen() + 100));
            target.setDeltaMovement(target.getDeltaMovement().scale(.55));
        } else {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DARKNESS, 60, 0));
        }
        if (maid.level() instanceof ServerLevel level) level.sendParticles(
                ice ? ParticleTypes.SNOWFLAKE : ParticleTypes.SCULK_SOUL,
                target.getX(), target.getY() + 1, target.getZ(), 24, .5, .7, .5, .05);
        return true;
    }

    private static boolean pay(Player owner, ItemStack weapon, int resonanceCost) {
        if (MaidInfusion.isInfused(weapon) && ContractCarrierData.isOwner(weapon, owner)) {
            var data = MaidInfusion.data(weapon);
            if (data.getResonance() >= resonanceCost) {
                data.reduceResonance(resonanceCost);
                ContractCarrierData.setMaidData(weapon, data);
                return true;
            }
        }
        return false;
    }

    public static ItemStack createProjection(ItemStack original, Player owner) {
        String binding = ContractCarrierData.getBindingId(original);
        ItemStack copy = original.copy();
        copy.setCount(1);
        // A combat projection must never carry a second serialized maid or a
        // second usable contract. Weapon-native NBT/capabilities remain intact.
        ContractCarrierData.clearMaidContract(copy);
        com.maidweapon.forge.system.deployment.ContractCompanionState.clear(copy);
        copy.getOrCreateTag().remove("MaidInfusionMagicTaskFailure");
        copy.getOrCreateTag().remove("MaidInfusionSlashBladeTaskFailure");
        copy.getOrCreateTag().putBoolean(PHANTOM_TAG, true);
        copy.getOrCreateTag().putUUID("MaidWeaponPhantomOwner", owner.getUUID());
        if (binding != null && !binding.isEmpty()) {
            copy.getOrCreateTag().putString("MaidWeaponPhantomBinding", binding);
        }
        SlashBladeCompat.initializePhantom(original, copy);
        return copy;
    }
    public static boolean isPhantom(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(PHANTOM_TAG);
    }
    public static ItemStack projectionAccessory(Player owner, ItemStack source) {
        return usesMaidSpellTask(source) ? equippedMagicAccessory(owner) : ItemStack.EMPTY;
    }

    private static ItemStack equippedMagicAccessory(Player player) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object lazy = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, player);
            Object optional = lazy.getClass().getMethod("resolve").invoke(lazy);
            if (!(optional instanceof java.util.Optional<?> value) || value.isEmpty()) return ItemStack.EMPTY;
            Class<?> handlerType = Class.forName(
                    "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Object equipped = handlerType.getMethod("getEquippedCurios").invoke(value.get());
            Class<?> itemHandler = Class.forName("net.minecraftforge.items.IItemHandler");
            int slots = ((Number) itemHandler.getMethod("getSlots").invoke(equipped)).intValue();
            Method getStack = itemHandler.getMethod("getStackInSlot", int.class);
            for (int i = 0; i < slots; i++) {
                ItemStack stack = (ItemStack) getStack.invoke(equipped, i);
                if (isAllowedAccessory(stack)) return stack;
            }
        } catch (ReflectiveOperationException ignored) {}
        return ItemStack.EMPTY;
    }

    private static boolean isAllowedAccessory(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath();
        return (id.getNamespace().equals("irons_spellbooks")
                && (path.contains("spell_book") || path.contains("spellbook")))
                || (id.getNamespace().equals("goety")
                && (path.equals("focus_bag") || path.equals("focus_pack")))
                || (id.getNamespace().equals("touhou_little_maid_spell") && path.contains("book"));
    }

    /**
     * Iron's Spells also has preset and add-on casting items whose registry paths
     * do not contain "staff". Prefer its public spell-container API when present,
     * while retaining path matching for empty catalyst staffs.
     */
    private static boolean isIronSpellContainer(ItemStack stack) {
        if (!ModList.get().isLoaded(IRONS) || stack.isEmpty()) return false;
        try {
            if (!searchedIronSpellContainerMethod) {
                searchedIronSpellContainerMethod = true;
                Class<?> container = Class.forName(
                        "io.redspace.ironsspellbooks.api.spells.ISpellContainer");
                ironIsSpellContainerMethod =
                        container.getMethod("isSpellContainer", ItemStack.class);
            }
            return ironIsSpellContainerMethod != null
                    && Boolean.TRUE.equals(ironIsSpellContainerMethod.invoke(null, stack));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            ironIsSpellContainerMethod = null;
            return false;
        }
    }
    private TripleMagicCompat() {}
}
