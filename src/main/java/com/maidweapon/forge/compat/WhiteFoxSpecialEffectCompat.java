package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/** Native, non-copyable SlashBlade SE; optional classes are resolved only when installed. */
public final class WhiteFoxSpecialEffectCompat {
    public static final ResourceLocation ID = ResourceLocation.parse("maid_weapon:purifying_edge");
    private static final ResourceKey<Registry<Object>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.parse("slashblade:special_effect"));

    public static void bootstrap(IEventBus bus) {
        bus.addListener(WhiteFoxSpecialEffectCompat::register);
    }

    private static void register(RegisterEvent event) {
        if (!event.getRegistryKey().equals(REGISTRY) || !SlashBladeCompat.isLoaded()) return;
        try {
            Object effect = Class.forName("mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect")
                    .getConstructor(int.class, boolean.class, boolean.class).newInstance(0, false, false);
            event.register(REGISTRY, helper -> helper.register(ID, effect));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot register White Fox's SlashBlade special effect", error);
        }
    }

    public static boolean isShrineBlade(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag()
                && stack.getTag().getBoolean(FoxSpiritState.OFFERING)
                && SlashBladeCompat.isNamedBlade(stack, "item.slashblade.fox_white");
    }

    /** Also upgrades existing shrine swords; the effect remains when the soul moves out. */
    public static void ensureEffect(ItemStack stack) {
        if (!isShrineBlade(stack)) return;
        Object state = SlashBladeCompat.resolveBladeState(stack);
        if (state == null) return;
        try {
            if (!(Boolean) state.getClass().getMethod("hasSpecialEffect", ResourceLocation.class).invoke(state, ID))
                state.getClass().getMethod("addSpecialEffect", ResourceLocation.class).invoke(state, ID);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot install White Fox's special effect", error);
        }
    }

    public static boolean hasEffect(ItemStack stack) {
        if (!isShrineBlade(stack)) return false;
        Object state = SlashBladeCompat.resolveBladeState(stack);
        try {
            return state != null && (Boolean) state.getClass()
                    .getMethod("hasSpecialEffect", ResourceLocation.class).invoke(state, ID);
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    /** Base plus diminishing-return refine bonus, excluding enchantments, rank, SA and other SEs. */
    public static double strength(ItemStack stack) {
        if (stack.isEmpty() || stack.hasTag() && (stack.getTag().contains("MaidWeaponPhantomBinding")
                || stack.getTag().contains("MaidWeaponPhantomCopy"))) return 0;
        Object state = SlashBladeCompat.resolveBladeState(stack);
        if (state == null) return 0;
        try {
            Class<?> type = state.getClass();
            if ((Boolean) type.getMethod("isBroken").invoke(state)
                    || (Boolean) type.getMethod("isSealed").invoke(state)) return 0;
            double base = ((Number) type.getMethod("getBaseAttackModifier").invoke(state)).doubleValue();
            int refine = Math.max(0, ((Number) type.getMethod("getRefine").invoke(state)).intValue());
            boolean fierce = ((Number) type.getMethod("getKillCount").invoke(state)).intValue() >= 1000;
            double strength = base * (2 - 1 / (1 + (fierce ? 0.1 : 0.05) * refine));
            return Double.isFinite(strength) ? Math.max(0, strength) : 0;
        } catch (ReflectiveOperationException error) {
            return 0;
        }
    }

    private WhiteFoxSpecialEffectCompat() { }
}
