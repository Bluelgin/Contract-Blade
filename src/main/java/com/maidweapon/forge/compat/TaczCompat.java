package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Classloading-safe entry point for the optional TACZ integration.
 *
 * <p>This class contains no TACZ types. The implementation class is resolved
 * only after Forge confirms that mod id {@code tacz} is loaded.</p>
 */
@Mod.EventBusSubscriber
public final class TaczCompat {
    public static final String GUN_TASK = "touhou_little_maid:gun_attack";
    public static final String ENTITY_OWNER_TAG = "MaidWeaponTaczAmmoOwner";
    public static final String ENTITY_BINDING_TAG = "MaidWeaponTaczAmmoBinding";
    public static final String PROJECTION_TAG = "MaidWeaponTaczProjection";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String IMPLEMENTATION =
            "com.maidweapon.forge.compat.tacz.TaczLoadedCompat";
    private static Class<?> implementation;
    private static boolean lookupAttempted;

    public static void bootstrap(IEventBus modBus) {
        if (!isLoaded()) return;
        invoke("register", new Class<?>[]{IEventBus.class}, modBus);
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("tacz");
    }

    public static boolean isGun(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) return false;
        Object result = invoke("isGun", new Class<?>[]{ItemStack.class}, stack);
        return result instanceof Boolean value && value;
    }

    /**
     * Equips and maintains the non-droppable TACZ combat projection and its
     * owner-backed ammunition link.
     */
    public static boolean maintain(Player owner, LivingEntity maid, ItemStack source) {
        if (!isGun(source)) return false;
        Object result = invoke("maintain",
                new Class<?>[]{Player.class, LivingEntity.class, ItemStack.class},
                owner, maid, source);
        return result instanceof Boolean value && value;
    }

    /** Returns projected ammunition before removing all transient TACZ state. */
    public static void clear(Player owner, LivingEntity maid, ItemStack source) {
        if (!isLoaded()) return;
        invoke("clear", new Class<?>[]{Player.class, LivingEntity.class, ItemStack.class},
                owner, maid, source);
    }

    public static void purgeLeakedLinks(Player player) {
        if (!isLoaded()) return;
        invoke("purgeLeakedLinks", new Class<?>[]{Player.class}, player);
    }

    public static boolean isProjection(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null
                && stack.getTag().getBoolean(PROJECTION_TAG);
    }

    @SubscribeEvent
    public static void onLinkedMaidDeath(LivingDeathEvent event) {
        LivingEntity maid = event.getEntity();
        if (!isLoaded() || !maid.getPersistentData().hasUUID(ENTITY_OWNER_TAG)
                || maid.getServer() == null) return;

        UUID ownerId = maid.getPersistentData().getUUID(ENTITY_OWNER_TAG);
        ServerPlayer owner = maid.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return;
        ItemStack weapon = InfusedMaidDeploymentSystem.findBoundWeapon(
                owner, maid.getStringUUID());
        clear(owner, maid, weapon);
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) {
        Class<?> type = implementation();
        if (type == null) return null;
        try {
            Method method = type.getMethod(name, parameterTypes);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("[MaidWeapon] TACZ compatibility call {} failed: {}",
                    name, e.getMessage());
            return null;
        }
    }

    private static Class<?> implementation() {
        if (!isLoaded()) return null;
        if (implementation != null) return implementation;
        if (lookupAttempted) return null;
        lookupAttempted = true;
        try {
            implementation = Class.forName(IMPLEMENTATION);
        } catch (ClassNotFoundException | LinkageError e) {
            LOGGER.warn("[MaidWeapon] TACZ is loaded but its compatibility API is unavailable: {}",
                    e.getMessage());
        }
        return implementation;
    }

    private TaczCompat() {
    }
}
