package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxPhantomCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** One bounded, session-owned native sword formation; launched swords leave this controller. */
public final class BlackFoxSwordFormation {
    public static final int FIRST_HOLD = 14, SECOND_HOLD = 10;
    public static final int LOCK_LEAD = 4;
    private record Sword(Entity entity, int slot) { }
    private final BlackFoxCombatant actor;
    private final List<Sword> held = new ArrayList<>();
    private long born;
    private boolean second, locked, arc, wheel;
    public static final int WHEEL_FLAG = 1 << 12, WHEEL_STOP = 32, WHEEL_FIRST = 40, WHEEL_SECOND = 56;
    private float yaw;
    private Vec3 target = Vec3.ZERO;
    BlackFoxSwordFormation(BlackFoxCombatant actor) { this.actor = actor; }
    public long born() { return born; }
    public int mask() {
        int mask = 0;
        for (var sword : held) mask |= 1 << sword.slot();
        return mask == 0 ? 0 : mask | (wheel ? WHEEL_FLAG : arc ? 32 : 0);
    }
    public float yaw() { return yaw; }
    public static Vec3 wheelOffset(int slot, float yaw, double age) {
        double angle = Math.PI * 2 * slot / 12 + Math.min(WHEEL_STOP, Math.max(0, age)) * .09;
        return new Vec3(Math.sin(angle) * 2.8, 1.8 + Math.cos(angle) * 2.3, -.65)
                .yRot((float) Math.toRadians(-yaw));
    }
    public static int wheelFireAge(int slot) { return slot < 6 ? WHEEL_FIRST : WHEEL_SECOND; }
    public boolean holds(Entity entity) { return held.stream().anyMatch(sword -> sword.entity() == entity); }
    public static Vec3 offset(int slot, float yaw) {
        double side = (slot & 1) == 0 ? -1 : 1;
        return new Vec3(side * (1.15 + slot / 2 * .35), 1.25 + slot / 2 * .45, -.25)
                .yRot((float) Math.toRadians(-yaw));
    }
    public static Vec3 arcOffset(int slot, float yaw) {
        double angle = Math.toRadians(-60 + slot * 30);
        return new Vec3(Math.sin(angle) * 2, 1.6 + Math.cos(angle) * .6, -Math.cos(angle) * .7)
                .yRot((float) Math.toRadians(-yaw));
    }
    public static int fireAge(int slot, boolean second) {
        return (second ? SECOND_HOLD : FIRST_HOLD) + slot * (second ? 2 : 3);
    }
    void begin(ServerPlayer player, int capacity, Consumer<Entity> track) {
        begin(player, capacity, track, false);
    }
    void begin(ServerPlayer player, int capacity, Consumer<Entity> track, boolean arc) {
        if (!held.isEmpty()) return;
        this.arc = arc; wheel = false;
        second = actor.combat().fight().phaseTwo();
        born = actor.body().level().getGameTime(); locked = false; yaw = actor.body().getYRot();
        int count = Math.min(second || arc ? 5 : 3, capacity);
        for (int slot = 0; slot < count; slot++) {
            Vec3 at = actor.body().position().add(arc ? arcOffset(slot, yaw) : offset(slot, yaw));
            var entity = BlackFoxPhantomCompat.spawn(actor.body(), at, player.getEyePosition().subtract(at), false, 0);
            entity.setNoGravity(true);
            held.add(new Sword(entity, slot)); track.accept(entity);
        }
        if (!held.isEmpty()) BlackFoxEffects.tell(actor.body(), false);
    }
    void beginWheel(ServerPlayer player, int capacity, Consumer<Entity> track) {
        if (!held.isEmpty()) return;
        wheel = true; arc = false; second = true; locked = false;
        born = actor.body().level().getGameTime(); yaw = actor.body().getYRot();
        for (int slot = 0; slot < Math.min(12, capacity); slot++) {
            Vec3 at = actor.body().position().add(wheelOffset(slot, yaw, 0));
            var entity = BlackFoxPhantomCompat.spawn(actor.body(), at, player.getEyePosition().subtract(at), false, 0);
            held.add(new Sword(entity, slot)); track.accept(entity);
        }
        BlackFoxEffects.tell(actor.body(), false);
    }
    void tick(ServerPlayer player) {
        if (held.isEmpty()) return;
        long age = actor.body().level().getGameTime() - born;
        if (!locked) {
            yaw = actor.body().getYRot();
            if (age >= (wheel ? WHEEL_STOP : (second ? SECOND_HOLD : FIRST_HOLD) - LOCK_LEAD)) {
                locked = true; target = player.getEyePosition();
                BlackFoxEffects.tell(actor.body(), true);
            }
        }
        var iterator = held.iterator();
        while (iterator.hasNext()) {
            var sword = iterator.next();
            if (sword.entity().isRemoved()) { iterator.remove(); continue; }
            Vec3 at = actor.body().position().add(wheel ? wheelOffset(sword.slot(), yaw, age)
                    : arc ? arcOffset(sword.slot(), yaw) : offset(sword.slot(), yaw));
            sword.entity().teleportTo(at.x, at.y, at.z);
            Vec3 aim = (locked ? target : player.getEyePosition()).subtract(at);
            if (arc) aim = aim.yRot((float) Math.toRadians((sword.slot() - 2) * 6));
            if (aim.lengthSqr() < .0001) aim = new Vec3(0,0,1);
            boolean firing = age >= (wheel ? wheelFireAge(sword.slot()) : fireAge(sword.slot(), second));
            BlackFoxPhantomCompat.fire(sword.entity(), aim, firing ? (second ? 1.5f : 1.2f) : 0);
            if (firing) {
                sword.entity().playSound(net.minecraft.sounds.SoundEvents.TRIDENT_THROW, .45f, 1.5f);
                iterator.remove();
            }
        }
    }
    void clear() { held.forEach(sword -> sword.entity().discard()); held.clear(); }
}
