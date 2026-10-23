package com.maidweapon.forge.entity;

import com.maidweapon.forge.compat.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Invisible, temporary native SlashBlade user. Never changes the real player's combo. */
public final class BlackFoxSwordActorEntity extends PathfinderMob {
    private BlackFoxSwordCoreEntity core;
    private ServerPlayer owner;
    private LivingEntity victim;
    private com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth.AttackMode emitterMode = com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth.AttackMode.BARRAGE;
    private ResourceLocation stage;
    private int rest;
    public BlackFoxSwordActorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level); setNoAi(true); setInvisible(true); setInvulnerable(true); setNoGravity(true); noPhysics = true;
    }
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 1)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 8);
    }
    public void initialize(BlackFoxSwordCoreEntity formation, ServerPlayer player, LivingEntity target) {
        core = formation; owner = player; victim = target;
        setItemSlot(EquipmentSlot.MAINHAND, core.sourceBlade().copy());
        getMainHandItem().getOrCreateTag().putBoolean("Unbreakable", true);
        BlackFoxJudgementCompat.purpleBlade(this);
        BlackFoxNativeCombo.proxyTarget(this, target);
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(
                player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        setPos(core.position());
    }
    public com.maidweapon.forge.api.ContractCombatCreditResolver.Credit masteryCredit() {
        if (core == null || core.isRemoved() || owner == null || !owner.isAlive() || owner.level() != level()) return null;
        return core.credit(owner);
    }
    public BlackFoxSwordCoreEntity core() { return core; }
    public ServerPlayer contractOwner() { return owner; }

    public void setEmitterMode(com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth.AttackMode mode) { emitterMode = mode; }
    public LivingEntity victim() { return victim; }
    public boolean validVictim() { return victim != null && permits(victim); }
    public boolean permits(Entity entity) {
        return core != null && !core.isRemoved() && owner != null && entity instanceof LivingEntity living
                && (victim == null || living == victim) && core.eligible(owner, living, victim == null
                ? emitterMode
                : com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth.AttackMode.COMBO_B);
    }
    public void capture(Entity shot) {
        if (core != null) {
            try { shot.getClass().getMethod("setColor",int.class).invoke(shot,0xB04CE5); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Sword core native projectile palette",error); }
            core.capture(shot);
            com.maidweapon.forge.system.mastery.ContractCombatCredits.stampLaunch(shot,masteryCredit());
        }
    }
    public ResourceLocation nativeStage() { return stage; }
    @Override public void tick() {
        super.tick(); if (level().isClientSide) return;
        if (core == null || core.isRemoved() || owner == null || !owner.isAlive()
                || victim != null && !permits(victim)) { discard(); return; }
        if (victim == null) { setPos(core.position()); return; }
        if (masteryCredit() == null || !core.fundAttack(owner)) { discard(); return; }
        Vec3 offset = new Vec3(Math.cos(getId() * 1.7), 0, Math.sin(getId() * 1.7)).scale(2);
        setPos(victim.position().add(offset)); setDeltaMovement(Vec3.ZERO);
        Vec3 aim = victim.getEyePosition().subtract(getEyePosition());
        setYRot((float)(Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90));
        setXRot((float)-Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        yBodyRot = getYRot(); yHeadRot = getYRot();
        if (rest > 0) { rest--; return; }
        if (stage == null) change(ResourceLocation.parse("slashblade:combo_b1"));
        Object combo = NativeApi.combo(stage);
        ResourceLocation next = (ResourceLocation)NativeApi.call("getNext", combo, this);
        if (stage.getPath().matches("combo_b[1-6]") && !stage.equals(next) && next.getPath().matches("combo_b[2-7]")) {
            change(next); combo = NativeApi.combo(stage);
        } else if (BlackFoxNativeCombo.elapsed(this) * 50 > ((Number)NativeApi.call("getTimeoutMS", combo)).intValue()) {
            change((ResourceLocation)NativeApi.call("getNextOfTimeout", combo, this)); combo = NativeApi.combo(stage);
        }
        if (stage.getPath().equals("none")) { stage = null; rest = 4; return; }
        NativeApi.call("tickAction", combo, this);
    }
    private void change(ResourceLocation next) {
        stage = next;
        BlackFoxNativeCombo.update(this, next);
    }
    private static final class NativeApi {
        private static final net.minecraftforge.registries.IForgeRegistry<?> REGISTRY;
        private static final java.util.Map<String, java.lang.reflect.Method> METHODS;
        static {
            try {
                REGISTRY = (net.minecraftforge.registries.IForgeRegistry<?>)((java.util.function.Supplier<?>)Class.forName(
                        "mods.flammpfeil.slashblade.registry.ComboStateRegistry").getField("REGISTRY").get(null)).get();
                var type = Class.forName("mods.flammpfeil.slashblade.registry.combo.ComboState");
                METHODS = java.util.Map.of("getNext", type.getMethod("getNext", LivingEntity.class),
                        "getNextOfTimeout", type.getMethod("getNextOfTimeout", LivingEntity.class),
                        "getTimeoutMS", type.getMethod("getTimeoutMS"), "tickAction", type.getMethod("tickAction", LivingEntity.class));
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native B API", error); }
        }
        static Object combo(ResourceLocation id) {
            return java.util.Objects.requireNonNull(REGISTRY.getValue(id), "Missing native B state " + id);
        }
        static Object call(String name, Object receiver, LivingEntity... actor) {
            try { return actor.length == 0 ? METHODS.get(name).invoke(receiver)
                    : METHODS.get(name).invoke(receiver, actor[0]); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Sword core native B " + name, error); }
        }
    }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
}
