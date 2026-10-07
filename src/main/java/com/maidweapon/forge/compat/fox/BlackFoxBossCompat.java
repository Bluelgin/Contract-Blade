package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Optional combat registration; the entity itself has no third-party superclass or interface. */
public final class BlackFoxBossCompat {
    private static RegistryObject<EntityType<BlackFoxBossEntity>> type;
    private static RegistryObject<EntityType<com.maidweapon.forge.entity.BlackFoxCoreEntity>> core;
    private static RegistryObject<EntityType<com.maidweapon.forge.entity.BlackFoxMinorCutEntity>> minorCut;
    private static RegistryObject<net.minecraft.core.particles.SimpleParticleType> parry;
    public static void bootstrap(IEventBus bus) {
        if (!available()) return;
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.register();
        var sounds = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "maid_weapon");
        sounds.register("black_fox_music", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.parse("maid_weapon:black_fox_music")));
        sounds.register(bus);
        var entities = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "maid_weapon");
        type = entities.register("black_fox_boss", () -> EntityType.Builder
                .of(BlackFoxBossEntity::new, MobCategory.MONSTER).sized(.65f, 1.8f)
                .clientTrackingRange(12).updateInterval(1).noSave().fireImmune().build("maid_weapon:black_fox_boss"));
        core = entities.register("black_fox_core", () -> EntityType.Builder
                .of(com.maidweapon.forge.entity.BlackFoxCoreEntity::new, MobCategory.MONSTER).sized(1.5f, 1.5f)
                .clientTrackingRange(12).updateInterval(1).noSave().fireImmune().build("maid_weapon:black_fox_core"));
        minorCut = entities.register("black_fox_minor_cut", () -> EntityType.Builder
                .of(com.maidweapon.forge.entity.BlackFoxMinorCutEntity::new, MobCategory.MISC).sized(.6f, .6f)
                .clientTrackingRange(12).updateInterval(1).noSave().fireImmune().build("maid_weapon:black_fox_minor_cut"));
        entities.register(bus);
        var particles = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "maid_weapon");
        parry = particles.register("black_fox_parry", () -> new net.minecraft.core.particles.SimpleParticleType(false));
        particles.register(bus);
        bus.addListener(BlackFoxBossCompat::attributes);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> BlackFoxBossClient.bootstrap(bus));
    }
    public static boolean available() {
        return SlashBladeCompat.isLoaded() && net.minecraftforge.fml.ModList.get()
                .getModContainerById("touhou_little_maid").map(mod -> mod.getModInfo().getVersion()
                        .compareTo(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.5.3")) >= 0).orElse(false);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(type.get(), BlackFoxBossEntity.bossAttributes().build());
        event.put(core.get(), com.maidweapon.forge.entity.BlackFoxCoreEntity.attributes().build());
    }
    static EntityType<BlackFoxBossEntity> type() { return type.get(); }
    public static net.minecraft.core.particles.SimpleParticleType parryParticle() { return parry.get(); }
    static EntityType<com.maidweapon.forge.entity.BlackFoxCoreEntity> coreType() { return core.get(); }
    static EntityType<com.maidweapon.forge.entity.BlackFoxMinorCutEntity> minorCutType() { return minorCut.get(); }
    public static com.maidweapon.forge.entity.BlackFoxMinorCutEntity spawnMinorCut(BlackFoxCombatant owner,
            net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 velocity) {
        var entity = minorCut.get().create(owner.body().level());
        if (entity == null) return null;
        entity.setOwner(owner.body()); entity.setPos(at); entity.setDeltaMovement(velocity);
        return owner.body().level().addFreshEntity(entity) ? entity : null;
    }
    public static com.maidweapon.forge.entity.BlackFoxCoreEntity spawnCore(BlackFoxCombatant owner, net.minecraft.world.phys.Vec3 at) {
        var entity = core.get().create(owner.body().level());
        if (entity == null) return null;
        entity.initialize(owner); entity.setPos(at);
        return owner.body().level().addFreshEntity(entity) ? entity : null;
    }
    public static BlackFoxCombatant spawn(ServerPlayer player, BlockPos origin) {
        if (type == null) return null;
        var boss = type.get().create(player.serverLevel());
        if (boss == null) return null;
        boss.initialize(player, origin);
        return player.serverLevel().addFreshEntity(boss) ? boss : null;
    }
    private BlackFoxBossCompat() { }
}
