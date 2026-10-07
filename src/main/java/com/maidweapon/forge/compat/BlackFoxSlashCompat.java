package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import java.lang.reflect.Method;

/** Optional native blade bridge. B damage is owned by the registered combo driver, other skills use cosmetic slashes. */
public final class BlackFoxSlashCompat {
    private static Method doSlash, setDamage, setColor;
    public static ItemStack blade(net.minecraft.world.level.Level level) {
        if (!SlashBladeCompat.isLoaded()) return ItemStack.EMPTY;
        try {
            var key = net.minecraft.resources.ResourceKey.<net.minecraft.core.Registry<Object>>createRegistryKey(
                    ResourceLocation.parse("slashblade:named_blades"));
            Object definition = level.registryAccess().registryOrThrow(key)
                    .get(ResourceLocation.parse("slashblade:fox_black"));
            var blade = ((ItemStack) definition.getClass().getMethod("getBlade").invoke(definition)).copy();
            // Only this encounter's equipment is unbreakable; player-crafted Black Fox blades are unchanged.
            blade.getOrCreateTag().putBoolean("Unbreakable", true);
            Object state = SlashBladeCompat.resolveBladeState(blade);
            state.getClass().getMethod("setBroken", boolean.class).invoke(state, false);
            state.getClass().getMethod("setDamage", int.class).invoke(state, 0);
            return blade;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot load Black Fox's native blade", error);
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void bootstrap() {
        if (!SlashBladeCompat.isLoaded()) return;
        try {
            Class<Event> event = (Class) Class.forName("mods.flammpfeil.slashblade.event.BladeMotionEvent");
            Method entity = event.getMethod("getEntity"), combo = event.getMethod("getCombo");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, event, action -> {
                try {
                    Object attacking = entity.invoke(action);
                    if (attacking instanceof ServerPlayer player) {
                        ResourceLocation id = (ResourceLocation) combo.invoke(action);
                        if (meleeCombo(id.getPath())) BlackFoxEncounters.nativeSwing(player, player.getMainHandItem(), id);
                    } else if (attacking instanceof LivingEntity living) {
                        if (meleeCombo(((ResourceLocation) combo.invoke(action)).getPath()))
                            com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.melee(living);
                    }
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            });
            Class<?> manager = Class.forName("mods.flammpfeil.slashblade.util.AttackManager");
            doSlash = manager.getMethod("doSlash", LivingEntity.class, float.class, boolean.class, boolean.class, double.class);
            Class<?> slash = Class.forName("mods.flammpfeil.slashblade.entity.EntitySlashEffect");
            setDamage = slash.getMethod("setDamage", double.class);
            setColor = slash.getMethod("setColor", int.class);
            Class<Event> update = (Class) Class.forName("mods.flammpfeil.slashblade.event.SlashBladeEvent$UpdateEvent");
            Method actor = update.getMethod("getEntity");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, update, action -> {
                try {
                    if (actor.invoke(action) instanceof com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant)
                        action.setCanceled(true);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            });
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Black Fox native motion bridge", error); }
    }
    public static boolean meleeCombo(String id) {
        return (id.startsWith("combo_a") || id.startsWith("combo_b")) && !id.contains("end")
                || id.startsWith("aerial_rave") || id.equals("upper_slash")
                || id.equals("upperslash") || id.equals("rapid_slash") || id.equals("circle_slash");
    }
    /** Native stun also changes falling physics outside its Goal. A zero limit disables both paths. */
    public static void disableStun(LivingEntity boss) {
        if (!SlashBladeCompat.isLoaded()) return;
        try {
            var capability = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName(
                    "mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect").getField("MOB_EFFECT").get(null);
            Object state = boss.getCapability(capability).resolve().orElse(null);
            if (state != null) {
                Class<?> api = Class.forName("mods.flammpfeil.slashblade.capability.mobeffect.IMobEffectState");
                api.getMethod("setStunLimit", int.class).invoke(state, 0);
                api.getMethod("clearStunTimeOut").invoke(state);
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Black Fox stun policy", error); }
    }
    /** Visual state for non-B skills only. The B driver uses updateComboSeq and native tickAction. */
    public static void pose(LivingEntity boss, String combo) {
        Object state = SlashBladeCompat.resolveBladeState(boss.getMainHandItem());
        if (state == null) return;
        try {
            state.getClass().getMethod("setComboSeq", ResourceLocation.class)
                    .invoke(state, ResourceLocation.parse("slashblade:" + combo));
            state.getClass().getMethod("setLastActionTime", long.class).invoke(state, boss.level().getGameTime());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static void slash(LivingEntity boss, float roll) {
        if (doSlash == null) return;
        try {
            Entity visual = (Entity) doSlash.invoke(null, boss, roll, true, false, 0.0);
            if (visual == null) return;
            setDamage.invoke(visual, 0.0);
            setColor.invoke(visual, 0x9B46D1);
            // Native slash effects also execute hit effects. No shooter means visual only,
            // avoiding off-screen stun, i-frames and damage outside the encounter controller.
            if (visual instanceof Projectile projectile) projectile.setOwner(null);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static boolean nativeSlash(Entity entity) {
        return entity != null && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("slashblade:slash_effect");
    }
    private BlackFoxSlashCompat() { }
}
