package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/** Drives registered SlashBlade B states. Native callbacks own every slash, ratio and hit effect. */
public final class BlackFoxNativeCombo {
    private final BlackFoxCombatant actor;
    private final Set<Entity> projectiles = Collections.newSetFromMap(new IdentityHashMap<>());
    private ResourceLocation stage;
    private boolean executing;
    private long lastTick = Long.MIN_VALUE;
    private long stageStartedAt;
    public BlackFoxNativeCombo(BlackFoxCombatant actor) { this.actor = actor; }

    public void tick() {
        var boss = actor.body();
        long now = boss.level().getGameTime();
        if (lastTick == now) return;
        lastTick = now;
        projectiles.removeIf(Entity::isRemoved);
        if (stage == null) change(ResourceLocation.parse("slashblade:combo_b1"));
        if (stage.getPath().equals("none")) return;
        Object combo = Api.combo(stage);
        Object state = SlashBladeCompat.resolveBladeState(boss.getMainHandItem());
        long elapsed = (long) Api.call(Api.elapsed, state, boss);
        // Emulate consecutive attack inputs using the registered next-input gates, not our own B timeline.
        ResourceLocation next = (ResourceLocation) Api.call(Api.next, combo, boss);
        if (!actor.combat().fight().phaseTwo() && stage.getPath().equals("combo_b3")
                && next.getPath().equals("combo_b4")) {
            stop(); change(ResourceLocation.parse("slashblade:none")); lastTick = now;
            return;
        }
        if (stage.getPath().matches("combo_b[1-6]") && !next.equals(stage) && next.getPath().matches("combo_b[2-7]")) {
            change(next); combo = Api.combo(stage);
        } else if (elapsed * 50 > (int) Api.call(Api.timeout, combo)) {
            change((ResourceLocation) Api.call(Api.timeoutNext, combo, boss)); combo = Api.combo(stage);
        }
        if (stage.getPath().equals("none")) return;
        executing = true;
        try { Api.call(Api.tick, combo, boss); }
        finally { executing = false; }
    }

    private void change(ResourceLocation next) {
        stage = next;
        var boss = actor.body();
        stageStartedAt = boss.level().getGameTime();
        Api.call(Api.update, SlashBladeCompat.resolveBladeState(boss.getMainHandItem()), boss, next);
        boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, boss.getMainHandItem().copy());
    }
    public boolean pursuing() { return stage != null && stage.getPath().matches("combo_b[1-7]"); }
    /** Presentation only. The registered native state remains the authority for attack timing. */
    public int animationStage() { return pursuing() ? stage.getPath().charAt(7) - '0' : 0; }
    public long stageStartedAt() { return stageStartedAt; }
    public boolean finished() { return stage != null && stage.getPath().equals("none"); }
    public boolean owns(Entity projectile) { return projectiles.contains(projectile); }
    public static long elapsed(LivingEntity entity) {
        return (long) Api.call(Api.elapsed, SlashBladeCompat.resolveBladeState(entity.getMainHandItem()), entity);
    }
    public BlackFoxFight.Motion motion() {
        if (stage == null) return BlackFoxFight.Motion.GUARD;
        return switch (stage.getPath()) {
            case "combo_b1" -> BlackFoxFight.Motion.CROSS;
            case "combo_b2", "combo_b3", "combo_b4", "combo_b5", "combo_b6" -> BlackFoxFight.Motion.RUSH;
            case "combo_b7" -> BlackFoxFight.Motion.FINISH;
            default -> BlackFoxFight.Motion.READY;
        };
    }
    public void capture(Entity entity) {
        if (executing || actor.combat().fight().skill() == BlackFoxFight.Skill.RETURN_BLADE) {
            try { entity.getClass().getMethod("setColor", int.class).invoke(entity,
                    BlackFoxSlashColors.blade(actor.combat().fight().phaseTwo())); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Native B color", error); }
            projectiles.add(entity);
        }
    }
    public void stop() {
        projectiles.forEach(Entity::discard); projectiles.clear();
        if (stage != null) {
            BlackFoxSlashCompat.pose(actor.body(), "standby");
            actor.body().getPersistentData().remove("sb_yrot");
            actor.body().getPersistentData().remove("sb_yrot_prev");
        }
        stage = null; stageStartedAt = 0; lastTick = Long.MIN_VALUE;
    }

    /** Null means vanilla/native selection; a Boss query has exactly one authorized candidate. */
    public static List<Entity> targets(Entity source, AABB nativeBounds, double meleeReach) {
        Entity shooter = source instanceof Projectile projectile ? projectile.getOwner() : source;
        if (!(shooter instanceof BlackFoxCombatant boss)) return null;
        // Non-indirect native slashes query with the shooter, not the projectile itself.
        Entity active = BlackFoxAttackTrace.projectile();
        if (source == shooter && active instanceof Projectile projectile && projectile.getOwner() == shooter) source = active;
        var combo = boss.combat().nativeCombo();
        if (!BlackFoxEncounters.registered(boss) || boss.combat().fight().skill() != BlackFoxFight.Skill.RETURN_BLADE
                || source.isRemoved() || source instanceof Projectile && !combo.projectiles.contains(source)) return List.of();
        ServerPlayer player = shooter.getServer().getPlayerList().getPlayer(boss.challenger());
        if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative()
                || player.level() != shooter.level() || !nativeBounds.intersects(player.getBoundingBox())
                || meleeReach > 0 && shooter.distanceToSqr(player) > meleeReach * meleeReach) return List.of();
        // Exclude before native knockback/stun callbacks; vanilla i-frames are reset by native forceHit.
        if (boss.combat().protects(player)) return List.of();
        if (source.getPersistentData().getBoolean("MaidWeaponBlackFoxParried")) return List.of();
        if (boss.combat().parryNative(player)) {
            source.getPersistentData().putBoolean("MaidWeaponBlackFoxParried", true);
            return List.of();
        }
        return List.of(player);
    }

    /** Lazy linkage keeps SlashBlade completely optional; cache reflection outside the combat loop. */
    private static final class Api {
        static final net.minecraftforge.registries.IForgeRegistry<?> registry;
        static final Method update, elapsed, tick, next, timeoutNext, timeout;
        static {
            try {
                registry = (net.minecraftforge.registries.IForgeRegistry<?>) ((Supplier<?>) Class.forName(
                        "mods.flammpfeil.slashblade.registry.ComboStateRegistry").getField("REGISTRY").get(null)).get();
                Class<?> state = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
                Class<?> combo = Class.forName("mods.flammpfeil.slashblade.registry.combo.ComboState");
                update = state.getMethod("updateComboSeq", LivingEntity.class, ResourceLocation.class);
                elapsed = state.getMethod("getElapsedTime", LivingEntity.class);
                tick = combo.getMethod("tickAction", LivingEntity.class);
                next = combo.getMethod("getNext", LivingEntity.class);
                timeoutNext = combo.getMethod("getNextOfTimeout", LivingEntity.class);
                timeout = combo.getMethod("getTimeoutMS");
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Black Fox native Combo B API", error); }
        }
        static Object combo(ResourceLocation id) {
            return Objects.requireNonNull(registry.getValue(id), "Missing native combo " + id);
        }
        static Object call(Method method, Object receiver, Object... args) {
            try { return method.invoke(receiver, args); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Black Fox native combo " + method.getName(), error); }
        }
    }
}
