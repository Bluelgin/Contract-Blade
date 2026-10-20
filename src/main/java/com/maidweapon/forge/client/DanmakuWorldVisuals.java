package com.maidweapon.forge.client;

import com.maidweapon.forge.api.danmaku.DanmakuVisualEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import java.util.*;

/** Small bounded timelines, independent of real projectile simulation and collision. */
@Mod.EventBusSubscriber(modid="maid_weapon", value=Dist.CLIENT)
public final class DanmakuWorldVisuals {
    public record Frame(DanmakuVisualEvent event, long received, int life) { }
    static final List<Frame> FRAMES = new ArrayList<>();
    static final Map<UUID, Frame> GUARDS = new HashMap<>();
    private static final Map<Integer, Frame> PROJECTILES = new HashMap<>();
    private static Object world;
    private static void session() {
        var mc = Minecraft.getInstance();
        if (world != mc.level || mc.player == null || !mc.player.isAlive()) {
            world = mc.level; FRAMES.clear(); GUARDS.clear(); PROJECTILES.clear();
        }
    }
    public static void accept(DanmakuVisualEvent event) {
        session(); var level = Minecraft.getInstance().level;
        if (level == null || !event.valid(level.dimension().location(), level.getGameTime())) return;
        long now = level.getGameTime();
        switch (event.kind()) {
            case CANCEL -> {
                FRAMES.removeIf(f -> f.event.owner().equals(event.owner()));
                GUARDS.remove(event.owner());
                PROJECTILES.values().removeIf(f -> f.event.owner().equals(event.owner()));
            }
            case GUARD_STOP -> {
                var f = GUARDS.remove(event.owner());
                if (f != null && FRAMES.size() < 128) FRAMES.add(new Frame(event, now, 8));
            }
            case GUARD_START -> { if (GUARDS.size() < 64) GUARDS.put(event.owner(), new Frame(event, now, 60)); }
            case PROJECTILE -> { if (PROJECTILES.size() < 256) PROJECTILES.put(event.entityId(), new Frame(event, now, 100)); }
            default -> { if (FRAMES.size() < 128) FRAMES.add(new Frame(event, now,
                    event.kind() == DanmakuVisualEvent.Kind.WAVE ? 30 : 9)); }
        }
    }
    public static Frame projectileFrame(Entity entity) {
        var f = PROJECTILES.get(entity.getId());
        return f != null && f.event.subject().equals(entity.getUUID()) ? f : null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        session(); var level = Minecraft.getInstance().level; if (level == null) return;
        long now = level.getGameTime();
        java.util.function.Predicate<Frame> expired = f -> now - f.received > f.life
                || level.getPlayerByUUID(f.event.owner()) == null
                || !level.getPlayerByUUID(f.event.owner()).isAlive();
        FRAMES.removeIf(expired); GUARDS.values().removeIf(expired);
        PROJECTILES.values().removeIf(f -> expired.test(f) ||
                (now - f.received > 5 && (level.getEntity(f.event.entityId()) == null ||
                 !f.event.subject().equals(level.getEntity(f.event.entityId()).getUUID()))));
    }
    public static int size() { return FRAMES.size() + GUARDS.size() + PROJECTILES.size(); }
    @Mod.EventBusSubscriber(modid="maid_weapon", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    manager -> DanmakuTextures.clear());
        }
    }
    private DanmakuWorldVisuals() { }
}
