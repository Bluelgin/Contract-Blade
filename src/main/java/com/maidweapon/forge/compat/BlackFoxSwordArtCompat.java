package com.maidweapon.forge.compat;

import com.maidweapon.forge.entity.BlackFoxSwordCoreEntity;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.BlackFoxSwordCoreTimeline;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.WeakHashMap;

/** Optional native SA/combos. Native charge acceptance and Proud Soul payment stay authoritative. */
public final class BlackFoxSwordArtCompat {
    public static final ResourceLocation ID = ResourceLocation.parse("maid_weapon:sealed_sword_core");
    private static final ResourceLocation DEPLOY = ResourceLocation.parse("maid_weapon:sword_core_deploy"), FOLLOW = ResourceLocation.parse("maid_weapon:sword_core_follow");
    private static final ResourceLocation NONE = ResourceLocation.parse("slashblade:none");
    private static final String UNLOCK = "MaidWeaponBlackFoxSwordArt";
    private static final WeakHashMap<ServerPlayer, BlackFoxSwordCoreEntity> CORES = new WeakHashMap<>();
    public static void bootstrap(IEventBus bus) { bus.addListener(BlackFoxSwordArtCompat::register); }
    private static void register(RegisterEvent event) {
        if (!SlashBladeCompat.isLoaded()) return;
        String registry = event.getRegistryKey().location().toString();
        try {
            if (registry.equals("slashblade:slash_arts")) {
                var type = Class.forName("mods.flammpfeil.slashblade.slasharts.SlashArts");
                Function<LivingEntity, ResourceLocation> choose = user -> user instanceof ServerPlayer player && ready(player)
                        ? active(player) == null ? DEPLOY : FOLLOW : NONE;
                Object art = type.getConstructor(Function.class).newInstance(choose);
                type.getMethod("setComboStateSuper", Function.class).invoke(art, choose);
                type.getMethod("setProudSoulCost", int.class).invoke(art, 20);
                event.register(ResourceKey.<Object>createRegistryKey(event.getRegistryKey().location()), helper -> helper.register(ID, art));
            } else if (registry.equals("slashblade:combo_state")) {
                var builderType = Class.forName("mods.flammpfeil.slashblade.registry.combo.ComboState$Builder");
                for (var key : new ResourceLocation[]{DEPLOY, FOLLOW}) {
                    var builder = builderType.getMethod("newInstance").invoke(null);
                    builderType.getMethod("startAndEnd", int.class, int.class).invoke(builder, 0, 1);
                    builderType.getMethod("priority", int.class).invoke(builder, -100);
                    // SlashBlade's Builder leaves nextOfTimeout null unless explicitly configured.
                    // Both server inventory ticking and client held-blade rendering dereference it.
                    Function<LivingEntity, ResourceLocation> idle = user -> NONE;
                    builderType.getMethod("next", Function.class).invoke(builder, idle);
                    builderType.getMethod("nextOfTimeout", Function.class).invoke(builder, idle);
                    Consumer<LivingEntity> action = user -> { if (user instanceof ServerPlayer player) execute(player, key.equals(FOLLOW)); };
                    builderType.getMethod("clickAction", Consumer.class).invoke(builder, action);
                    Object combo = builderType.getMethod("build").invoke(builder);
                    validateIdleExit(combo);
                    event.register(ResourceKey.<Object>createRegistryKey(event.getRegistryKey().location()), helper -> helper.register(key, combo));
                }
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Register Black Fox sword art", error); }
    }
    private static void validateIdleExit(Object combo) throws ReflectiveOperationException {
        var type = combo.getClass();
        if (!NONE.equals(type.getMethod("getNext", LivingEntity.class).invoke(combo, new Object[]{null}))
                || !NONE.equals(type.getMethod("getNextOfTimeout", LivingEntity.class).invoke(combo, new Object[]{null})))
            throw new IllegalStateException("Sword art combo must have a safe idle exit");
    }
    public static void unlock(ItemStack blade, ServerPlayer player) {
        if (!SlashBladeCompat.isNamedBlade(blade, "item.slashblade.fox_black") || !blade.hasTag() || !blade.getTag().hasUUID(FoxSpiritState.ORIGIN)) return;
        var tag = blade.getOrCreateTag();
        if (!tag.contains(UNLOCK)) {
            var reward = new net.minecraft.nbt.CompoundTag(); reward.putUUID("Owner", player.getUUID());
            reward.putUUID("Origin", tag.getUUID(FoxSpiritState.ORIGIN)); tag.put(UNLOCK, reward);
            var state = SlashBladeCompat.resolveBladeState(blade);
            try { if (state != null) state.getClass().getMethod("setSlashArtsKey", ResourceLocation.class).invoke(state, ID); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Unlock Black Fox sword art", error); }
        }
    }
    public static boolean entitled(ItemStack blade, ServerPlayer player) {
        if (!SlashBladeCompat.isNamedBlade(blade, "item.slashblade.fox_black") || !blade.hasTag()) return false;
        var reward = blade.getTag().getCompound(UNLOCK);
        return reward.hasUUID("Owner") && reward.getUUID("Owner").equals(player.getUUID()) && reward.hasUUID("Origin")
                && blade.getTag().hasUUID(FoxSpiritState.ORIGIN) && reward.getUUID("Origin").equals(blade.getTag().getUUID(FoxSpiritState.ORIGIN));
    }
    public static BlackFoxSwordCoreEntity active(ServerPlayer player) {
        var core = CORES.get(player);
        if (core != null && (core.isRemoved() || core.level() != player.level())) { core.discard(); CORES.remove(player); return null; }
        return core;
    }
    private static int souls(ItemStack blade) {
        Object state = SlashBladeCompat.resolveBladeState(blade);
        try { return state == null ? 0 : ((Number)state.getClass().getMethod("getProudSoulCount").invoke(state)).intValue(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Sword art Proud Soul", error); }
    }
    private static boolean ready(ServerPlayer player) {
        if (!com.maidweapon.forge.compat.fox.BlackFoxBossCompat.available() || !player.isAlive() || player.isSpectator() || !entitled(player.getMainHandItem(), player)) return false;
        var core = active(player);
        String denied = null;
        if (core != null && !BlackFoxSwordCoreTimeline.canFollow(core.tickCount, core.following())) denied = "follow_unavailable";
        else if (!player.getAbilities().instabuild && souls(player.getMainHandItem()) < (core == null ? 20 : 30)) denied = "souls";
        if (denied != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.fox.sa." + denied), true);
        return denied == null;
    }
    private static void execute(ServerPlayer player, boolean follow) {
        if (!ready(player)) return;
        var core = active(player);
        if (follow) {
            if (core == null) return;
            var target = core.followTarget(player);
            if (!player.getAbilities().instabuild) {
                var state = SlashBladeCompat.resolveBladeState(player.getMainHandItem());
                try { state.getClass().getMethod("setProudSoulCount", int.class).invoke(state, souls(player.getMainHandItem()) - 10); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException("Sword art follow payment", error); }
            }
            core.follow(target); // The native release charges the remaining twenty souls once.
        } else {
            if (core != null) return;
            core = com.maidweapon.forge.compat.fox.BlackFoxBossCompat.spawnSwordCore(player);
            if (core != null) CORES.put(player, core);
        }
    }
    public static net.minecraft.world.entity.Entity lock(ServerPlayer player) {
        var state = SlashBladeCompat.resolveBladeState(player.getMainHandItem());
        try { return state == null ? null : player.level().getEntity(((Number)state.getClass().getMethod("getTargetEntityId").invoke(state)).intValue()); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Sword art target", error); }
    }
    public static void clear(ServerPlayer player) { var core = CORES.remove(player); if (core != null) core.discard(); }
    public static boolean select(ServerPlayer player) {
        if (!entitled(player.getMainHandItem(), player)) return false;
        Object state = SlashBladeCompat.resolveBladeState(player.getMainHandItem());
        try { state.getClass().getMethod("setSlashArtsKey", ResourceLocation.class).invoke(state, ID); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Select Black Fox sword art", error); }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.fox.sa.selected")); return true;
    }
    public static void clearAll() { CORES.values().forEach(net.minecraft.world.entity.Entity::discard); CORES.clear(); }
    private BlackFoxSwordArtCompat() { }
}
