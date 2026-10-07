package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Opt-in real native entity/damage fixture; not enabled in normal games. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class BlackFoxBossValidation {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.blackFox.validation")) return;
        try { run(event.getServer()); LogUtils.getLogger().info("BLACK_FOX_BOSS_PASS"); }
        catch (Throwable failure) { LogUtils.getLogger().error("BLACK_FOX_BOSS_FAIL", failure); }
        finally { event.getServer().halt(false); }
    }
    private static void run(MinecraftServer server) throws Exception {
        // Forge fake players normally reject every damage source. This fixture must exercise real hurt events.
        var player = new net.minecraftforge.common.util.FakePlayer(server.overworld(), new GameProfile(
                UUID.fromString("5bfe8eaf-a00f-4e1d-9b7e-23c08e19f23e"), "BlackFoxFixture")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.CLIENTBOUND), player);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var spawnProtection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        spawnProtection.setAccessible(true); spawnProtection.setInt(player, 0);
        player.getInventory().clearContent(); player.setHealth(player.getMaxHealth());
        var playerIndex = server.getPlayerList().getClass().getSuperclass().getDeclaredField("playersByUUID");
        playerIndex.setAccessible(true);
        @SuppressWarnings("unchecked")
        var index = (java.util.Map<UUID, net.minecraft.server.level.ServerPlayer>) playerIndex.get(server.getPlayerList());
        index.put(player.getUUID(), player);
        try {
            check(FoxChallengeService.enter(player, ItemStack.EMPTY, FoxChallengeService.position(player)), "preview arena enters");
            check(BlackFoxEncounters.get(player) == null, "preview does not silently start combat");
            if (!BlackFoxBossCompat.available()) {
                check(!BlackFoxEncounters.start(player), "missing optional providers safely decline combat");
                check(FoxChallengeService.leave(player), "optional-free return"); return;
            }
            check(BlackFoxEncounters.start(player), "actual Boss registers and spawns");
            var actor = BlackFoxEncounters.get(player); var boss = actor.body();
            // Fake players supply no ordinary player chunk tickets; expose the whole private arena
            // entity index independently of which direction the new skills happen to move.
            var entityManager = net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");
            entityManager.setAccessible(true);
            var sections = (net.minecraft.world.level.entity.PersistentEntitySectionManager<?>) entityManager.get(boss.level());
            var cellChunk = new net.minecraft.world.level.ChunkPos(actor.arenaOrigin());
            for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++)
                sections.updateChunkStatus(new net.minecraft.world.level.ChunkPos(cellChunk.x + x, cellChunk.z + z),
                        net.minecraft.server.level.FullChunkStatus.FULL);
            check(boss.getType() == BlackFoxBossCompat.type() && boss.getMaxHealth() == 400, "real Boss type and attributes");
            check(boss.getClass().getSuperclass() == net.minecraft.world.entity.monster.Monster.class,
                    "independent Monster body, not a maid or tameable companion");
            check(!com.maidweapon.forge.compat.TouhouLittleMaidHelper.isMaidEntity(boss),
                    "companion/contract APIs do not recognize the encounter Boss as a maid");
            check(!boss.isNoAi() && !boss.canPickUpLoot() && !boss.canBeLeashed(player),
                    "normal physics without companion equipment pickup or leashing");
            check(!boss.removeWhenFarAway(1000000), "session, not natural monster distance, controls Boss lifetime");
            check(com.maidweapon.forge.compat.SlashBladeCompat.isNamedBlade(boss.getMainHandItem(), "item.slashblade.fox_black"), "native Black Fox equipped");
            var bossBlade = boss.getMainHandItem();
            check(bossBlade.getOrCreateTag().getBoolean("Unbreakable"), "Boss-only blade is unbreakable");
            bossBlade.hurtAndBreak(10000, boss, ignored -> { throw new IllegalStateException("Boss blade broke"); });
            check(bossBlade.getCount() == 1 && bossBlade.getDamageValue() == 0, "native attack durability cannot consume or wear Boss blade");
            var stunCap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName(
                    "mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect").getField("MOB_EFFECT").get(null);
            Object stunState = boss.getCapability(stunCap).resolve().orElseThrow();
            var stunApi = Class.forName("mods.flammpfeil.slashblade.capability.mobeffect.IMobEffectState");
            var stunManager = Class.forName("mods.flammpfeil.slashblade.ability.StunManager");
            for (long duration : new long[]{10, 40, 1000}) {
                stunManager.getMethod("setStun", net.minecraft.world.entity.LivingEntity.class, long.class).invoke(null, boss, duration);
                check(!(boolean) stunApi.getMethod("isStun", long.class).invoke(stunState, boss.level().getGameTime()),
                        "native stun cannot affect Boss: " + duration);
            }
            boss.setDeltaMovement(0, -.5, 0); boss.fallDistance = 6;
            stunManager.getMethod("onEntityLivingUpdate", net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent.class)
                    .invoke(stunManager.getConstructor().newInstance(), new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(boss));
            check(boss.getDeltaMovement().y == -.5, "native stun does not alter falling physics");
            boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); boss.fallDistance = 0;
            player.setItemSlot(EquipmentSlot.MAINHAND, BlackFoxSlashCompat.blade(player.level()));
            player.setPos(boss.getX(), boss.getY(), boss.getZ() - 3);
            player.setYRot(0); boss.setYRot(180);
            actor.combat().tick();
            var fight = actor.combat().fight();
            fight.start(BlackFoxFight.Skill.RETURN_BLADE); for (int i = 0; i < 16; i++) fight.advance();
            float before = boss.getHealth();
            check(!boss.hurt(boss.damageSources().playerAttack(player), 20) && boss.getHealth() == before, "Boss front guard cancels damage");
            player.setPos(boss.getX(), boss.getY(), boss.getZ() + 3);
            check(boss.hurt(boss.damageSources().playerAttack(player), 20) && boss.getHealth() < before, "rear hits bypass front guard");
            boss.invulnerableTime = 0; player.setPos(boss.getX(), boss.getY(), boss.getZ() - 3); player.setYRot(0); boss.setYRot(180);
            validateNativeB(actor, player);
            fight.start(BlackFoxFight.Skill.RETURN_BLADE); for (int i = 0; i < 26; i++) fight.advance();
            BlackFoxEncounters.swing(player, player.getMainHandItem(), true);
            check(fight.skill() == BlackFoxFight.Skill.RETURN_BLADE, "ordinary offensive swing cannot cancel Combo B");
            for (int i = 0; i < 100; i++) actor.combat().landedHit(100);
            check(fight.skill() == BlackFoxFight.Skill.RETURN_BLADE, "ordinary posture damage cannot cancel pursuit");
            nativeMotion(player, "slashblade:combo_a1");
            nativeMotion(player, "slashblade:combo_b1_end");
            nativeMotion(player, "fake:combo_b1");
            check(fight.skill() == BlackFoxFight.Skill.RETURN_BLADE, "A, end states and foreign identifiers cannot counter");
            nativeMotion(player, "slashblade:combo_b1");
            check(fight.skill() == BlackFoxFight.Skill.STAGGER, "real native Combo B motion counters without defense");
            float playerHealth = player.getHealth();
            for (int i = 0; i < BlackFoxController.PARRY_PROTECTION_TICKS + 1; i++) combatTick(actor);
            check(player.getHealth() == playerHealth, "canceled finisher cannot land later");
            boss.invulnerableTime = 0; fight.start(BlackFoxFight.Skill.STAGGER);
            before = boss.getHealth();
            check(boss.hurt(boss.damageSources().playerAttack(player), 20) && boss.getHealth() < before, "recovery takes ordinary damage");
            // White Fox SE on this actual Boss, not a model-name stand-in.
            var white = BlackFoxSlashCompat.blade(player.level());
            var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade").getField("BLADESTATE").get(null);
            Object state = white.getCapability(cap).resolve().orElseThrow();
            state.getClass().getMethod("setTranslationKey", String.class).invoke(state, "item.slashblade.fox_white");
            white.getOrCreateTag().putBoolean(FoxSpiritState.OFFERING, true); WhiteFoxSpecialEffectCompat.ensureEffect(white);
            player.setItemSlot(EquipmentSlot.MAINHAND, white);
            for (int i = 0; i < 16; i++) advanceClock((net.minecraft.server.level.ServerLevel) boss.level());
            BlackFoxEncounters.swing(player, white, true); boss.invulnerableTime = 0; before = boss.getHealth();
            boss.hurt(boss.damageSources().playerAttack(player), 1);
            check(before - boss.getHealth() > 3.9f, "native White Fox SE purifies actual encounter target");
            var doSlash = Class.forName("mods.flammpfeil.slashblade.util.AttackManager").getMethod("doSlash",
                    net.minecraft.world.entity.LivingEntity.class, float.class, boolean.class, boolean.class, double.class);
            var pending = (net.minecraft.world.entity.Entity) doSlash.invoke(null, player, 0f, true, false, 0d);
            player.getInventory().setItem(1, white);
            player.setItemSlot(EquipmentSlot.MAINHAND, BlackFoxSlashCompat.blade(player.level()));
            BlackFoxAttackTrace.enter(pending);
            try {
                var hit = com.maidweapon.forge.api.BlackFoxEncounterApi.resolve(boss.damageSources().playerAttack(player), boss);
                check(hit != null && hit.originalBlade() == white, "lingering native slash retains its birth-time carrier after switching blades");
            } finally { BlackFoxAttackTrace.exit(); pending.discard(); }
            check(BlackFoxAttackTrace.current() == null, "native hit trace cannot leak into another damage event");
            fight.start(BlackFoxFight.Skill.RETURN_BLADE); for (int i = 0; i < 26; i++) fight.advance();
            player.setYRot(180);
            nativeMotion(player, "slashblade:combo_b1");
            check(fight.skill() == BlackFoxFight.Skill.RETURN_BLADE, "attacking away from the blade cannot clash");
            player.setYRot(0);
            player.setPos(boss.getX(), boss.getY(), boss.getZ() - 10);
            nativeMotion(player, "slashblade:combo_b1");
            check(fight.skill() == BlackFoxFight.Skill.RETURN_BLADE, "distant swings cannot clash");
            BlackFoxEncounters.consumeComboB(player);
            fight.start(BlackFoxFight.Skill.APPROACH);
            var other = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.randomUUID(), "Outsider"));
            check(!boss.hurt(boss.damageSources().playerAttack(other), 100), "outsider cannot damage private encounter");
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            player.setHealth(1000);
            player.setPos(boss.getX(), boss.getY(), boss.getZ() - 10);
            for (int attempt = 1; attempt <= BlackFoxFight.PRESSURE_COST; attempt++) {
                actor.combat().nativeCombo().stop();
                fight.start(BlackFoxFight.Skill.RETURN_BLADE);
                int deadline = fight.duration();
                for (int i = 0; i < deadline && fight.skill() == BlackFoxFight.Skill.RETURN_BLADE; i++) combatTick(actor);
                check(fight.skill() == BlackFoxFight.Skill.APPROACH && fight.pressure() == attempt,
                        "native timeout recovers and adds one pressure: " + attempt);
            }
            player.setPos(boss.position().add(0, 0, -4));
            for (int i = 0; i < 8; i++) combatTick(actor);
            check(fight.skill() == BlackFoxFight.Skill.UNSEAL && fight.pressure() == 0,
                    "three failed counters actually schedule and spend a special attack");
            for (int i = 0; i < 72; i++) actor.combat().tick();
            check(fight.skill() == BlackFoxFight.Skill.APPROACH && fight.pressure() == 0,
                    "special attack completes without earning another failure");
            float combatHealth = boss.getHealth();
            for (var skill : new BlackFoxFight.Skill[]{BlackFoxFight.Skill.SIDESTEP_CUT, BlackFoxFight.Skill.CROSS_CUT, BlackFoxFight.Skill.PHANTOM_FEINT,
                    BlackFoxFight.Skill.RETURN_BLADE, BlackFoxFight.Skill.SKY, BlackFoxFight.Skill.SEAL, BlackFoxFight.Skill.UNSEAL}) {
                actor.combat().nativeCombo().stop(); fight.start(skill);
                player.setPos(boss.getX(), boss.getY(), boss.getZ() - 3);
                var context = BlackFoxController.class.getDeclaredField("actions"); context.setAccessible(true);
                Object actions = context.get(actor.combat());
                var direction = actions.getClass().getDeclaredField("committed"); direction.setAccessible(true);
                direction.set(actions, new net.minecraft.world.phys.Vec3(0, 0, -1));
                var start = boss.position();
                double displacement = 0, height = 0;
                int duration = fight.duration();
                for (int tick = 0; tick < duration; tick++) {
                    if (skill == BlackFoxFight.Skill.RETURN_BLADE && fight.skill() != skill) break;
                    if (skill == BlackFoxFight.Skill.RETURN_BLADE)
                        player.setPos(start.x + (tick < 25 ? 8 : -8), start.y, start.z);
                    advanceClock((net.minecraft.server.level.ServerLevel) boss.level());
                    boss.tick();
                    displacement = Math.max(displacement, boss.position().subtract(start).horizontalDistance());
                    height = Math.max(height, boss.getY() - start.y);
                }
                check(!boss.isRemoved() && FoxChallengeArena.contains(actor.arenaOrigin(), boss.getX(), boss.getY(), boss.getZ()),
                        "native physics/tick completes bounded skill " + skill);
                if (skill == BlackFoxFight.Skill.SIDESTEP_CUT)
                    check(displacement > .5, "actual native lateral/dash movement " + skill);
                if (skill == BlackFoxFight.Skill.SKY) check(height < .2, "anti-air attack does not launch the boss body");
                if (skill == BlackFoxFight.Skill.RETURN_BLADE) {
                    check(displacement < .2, "Combo B stays stationary when a player changes sides");
                }
            }
            check(boss.getHealth() <= combatHealth, "combat ticks have no companion regeneration");
            validateBalance(actor, player);
            resetBalance(actor);
            BlackFoxPhaseOneValidation.run(actor, player);
            resetBalance(actor);
            BlackFoxSwordFormationValidation.run(actor, player);
            resetBalance(actor);
            BlackFoxNewSkillsValidation.run(actor, player);
            resetBalance(actor);
            BlackFoxStandingIaidoValidation.run(actor,player);
            resetBalance(actor);
            BlackFoxDefenseValidation.run(actor, player);
            resetBalance(actor);
            BlackFoxSwordplayValidation.run(actor, player);
            resetBalance(actor);
            BlackFoxDimensionValidation.run(actor, player);
            resetBalance(actor);
            validateCorruption(actor, player);
            player.setPos(boss.position().add(0, 0, -3));
            check(fight.skill() == BlackFoxFight.Skill.DEFEATED && boss.isAlive(), "defeat without death loot or companion lifecycle");
            check(FoxChallengeService.leave(player) && boss.isRemoved() && BlackFoxEncounters.get(player) == null, "return clears session and entity");
            check(FoxChallengeService.enter(player, ItemStack.EMPTY, FoxChallengeService.position(player)), "re-entry allowed");
            check(BlackFoxEncounters.start(player), "rechallenge owns exactly one session");
            var previous = BlackFoxEncounters.get(player).body();
            check(BlackFoxEncounters.start(player) && previous.isRemoved(), "restarting cannot duplicate Bosses");
            var lethalActor=BlackFoxEncounters.get(player);
            var lethalBoss=lethalActor.body();
            lethalBoss.setHealth(1); lethalActor.combat().fight().start(BlackFoxFight.Skill.STAGGER);
            player.setPos(lethalBoss.position().add(0,0,-3)); lethalBoss.invulnerableTime=0;
            check(lethalBoss.hurt(lethalBoss.damageSources().playerAttack(player),10000)
                    && lethalBoss.isAlive() && lethalActor.combat().fight().skill()==BlackFoxFight.Skill.DOOM,
                    "lethal damage cannot skip final core or cause vanilla death");
            FoxChallengeService.leave(player);
        } finally {
            BlackFoxEncounters.stop(player); index.remove(player.getUUID());
        }
    }
    private static void advanceClock(net.minecraft.server.level.ServerLevel level) {
        ((net.minecraft.world.level.storage.ServerLevelData) level.getServer().overworld().getLevelData())
                .setGameTime(level.getGameTime() + 1);
    }
    private static void resetBalance(BlackFoxCombatant actor) throws Exception {
        var field=BlackFoxController.class.getDeclaredField("balance"); field.setAccessible(true);
        ((com.maidweapon.forge.system.fox.challenge.BlackFoxBalance)field.get(actor.combat())).clear();
    }
    private static void validateBalance(BlackFoxCombatant actor, net.minecraft.server.level.ServerPlayer player) throws Exception {
        resetBalance(actor);
        var boss=actor.body(); var fight=actor.combat().fight();
        for(int i=0;i<5;i++) {
            for(int tick=0;tick<30;tick++) advanceClock(player.serverLevel());
            boss.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0,1,0));
            player.setPos(boss.position().add(0,0,-4)); player.setYRot(0); boss.setYRot(180);
            fight.start(BlackFoxFight.Skill.PROBE_CUT);
            BlackFoxEncounters.swing(player,player.getMainHandItem(),true);
            check(actor.combat().parryNative(player), "balance counts actual fresh parries");
            check(!actor.combat().parryNative(player), "one swing cannot count twice");
            combatTick(actor);
            check(fight.offBalance()==(i==4), "only fifth successful parry loses balance");
        }
        check(fight.duration()==100, "balance loss lasts five seconds");
        for(int tick=0;tick<25;tick++) combatTick(actor);
        boss.invulnerableTime=0; int age=fight.age();
        check(boss.hurt(boss.damageSources().playerAttack(player),1) && fight.age()==age+10,
                "attacking during balance loss lands and shortens recovery by half a second");
        for(int tick=fight.age();tick<99;tick++) combatTick(actor);
        check(fight.offBalance(), "balance loss does not recover prematurely");
        combatTick(actor); check(!fight.offBalance(), "balance recovery completes after shortened duration");
        LogUtils.getLogger().info("BLACK_FOX_BALANCE_PASS: five fresh parries, duplicate rejection, 100-tick recovery and hit acceleration");
    }
    private static void validateCorruption(BlackFoxCombatant actor, net.minecraft.server.level.ServerPlayer player) throws Exception {
        var boss = actor.body(); var fight = actor.combat().fight();
        boss.setHealth(200); actor.combat().nativeCombo().stop(); fight.start(BlackFoxFight.Skill.DIMENSION_STRIKE);
        player.setPos(actor.arenaOrigin().getX() + 10, actor.arenaOrigin().getY() + 1, actor.arenaOrigin().getZ());
        combatTick(actor);
        check(fight.skill() == BlackFoxFight.Skill.TRANSITION && boss.isCurrentlyGlowing(), "half health starts corruption takeover");
        check(!boss.hurt(boss.damageSources().playerAttack(player), 1), "transition rejects damage");
        for (int i = 0; i < 60; i++) combatTick(actor);
        check(fight.skill() == BlackFoxFight.Skill.DOMAIN && boss.isNoGravity(), "transition ends in hovering domain");
        BlackFoxSwordFormationValidation.secondPhase(actor, player);
        check(actor.combat().damageTaken(10) == 7, "domain has thirty percent reduction");
        var held = player.getMainHandItem();
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
        check(actor.combat().intercept(boss.damageSources().playerAttack(player)), "phase two rejects non-SlashBlade melee");
        player.setItemSlot(EquipmentSlot.MAINHAND, held);
        player.setPos(boss.position().add(0, 0, -3));
        check(!actor.combat().intercept(boss.damageSources().playerAttack(player)), "phase two accepts close SlashBlade melee");
        check(actor.combat().intercept(boss.damageSources().indirectMagic(player, player)), "holding a blade cannot disguise magic as melee");
        var incoming = (net.minecraft.world.entity.projectile.Projectile) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .get(net.minecraft.resources.ResourceLocation.parse("slashblade:abstract_summoned_sword")).create(boss.level());
        incoming.setOwner(player);
        check(actor.combat().intercept(boss.damageSources().indirectMagic(incoming, player)), "phase two body rejects phantom swords");
        incoming.discard();
        var level = (net.minecraft.server.level.ServerLevel) boss.level();
        var volleys = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                new net.minecraft.world.phys.AABB(actor.arenaOrigin()).inflate(40)).stream()
                .filter(shot -> actor.combat().ranged().owns(shot) && !actor.combat().ranged().held(shot)).toList();
        check(!volleys.isEmpty(), "central domain emits real native phantom swords");
        player.invulnerableTime = 0; float shotHealth = player.getHealth();
        var impact = new net.minecraftforge.event.entity.ProjectileImpactEvent(volleys.get(0), new net.minecraft.world.phys.EntityHitResult(player));
        com.maidweapon.forge.event.BlackFoxCombatEvents.phantomImpact(impact);
        check(player.getHealth() < shotHealth, "private native phantom impact works with PvP disabled");
        player.setPos(boss.position().add(0, 0, -3));
        actor.combat().intercept(boss.damageSources().playerAttack(player));
        // Guard cooldown expires shortly after takeover, and only an incoming hit schedules retaliation.
        for (int i = 0; i < 45; i++) combatTick(actor);
        fight.start(BlackFoxFight.Skill.DOMAIN);
        for (int i = 0; i < 24; i++) combatTick(actor);
        actor.combat().intercept(boss.damageSources().playerAttack(player));
        check(fight.skill() == BlackFoxFight.Skill.RETURN_BLADE, "incoming attack schedules native stationary B");
        var position = boss.position();
        for (int i = 0; i < 30; i++) combatTick(actor);
        check(boss.position().distanceToSqr(position) < .001, "phase two B does not chase");
        var nativeCuts = slashes(actor).stream().filter(actor.combat().nativeCombo()::owns).toList();
        check(!nativeCuts.isEmpty() && nativeCuts.stream().allMatch(shot -> {
            try { return (int) shot.getClass().getMethod("getColor").invoke(shot) == 0xB04CE5; }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }), "phase two native B slashes are purple");
        actor.combat().nativeCombo().stop(); fight.start(BlackFoxFight.Skill.RETURN_BLADE);
        var fullStages = new java.util.LinkedHashSet<String>();
        var fullCuts = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.world.entity.Entity, Boolean>());
        for (int i = 0; i < 90; i++) {
            advanceClock(level); actor.combat().nativeCombo().tick();
            validateComboSynchronization(actor);
            fullStages.add(stage(actor)); fullCuts.addAll(slashes(actor).stream().filter(actor.combat().nativeCombo()::owns).toList());
        }
        for (int i = 1; i <= 7; i++) check(fullStages.contains("slashblade:combo_b" + i), "phase two retains complete native B stage " + i);
        check(fullStages.contains("slashblade:combo_b7_end") && fullStages.contains("slashblade:none") && fullCuts.size() > 25,
                "phase two keeps native finisher and dense full sequence");
        for (var shot : fullCuts) check((int) shot.getClass().getMethod("getColor").invoke(shot)
                == com.maidweapon.forge.compat.BlackFoxSlashColors.blade(true), "phase-two native B carries vivid purple color");
        actor.combat().nativeCombo().stop(); fight.start(BlackFoxFight.Skill.DOMAIN);
        check(fullCuts.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved), "full phase-two sequence still clears every owned slash");
        var ranged = actor.combat().ranged();
        ranged.clear();
        player.setPos(actor.arenaOrigin().getX() + 8.5, actor.arenaOrigin().getY() + 1.1, actor.arenaOrigin().getZ() + .5);
        // Expire all casting cooldowns, including the 180-tick rain, regardless of phase-one duration.
        for (int i = 0; i < 200; i++) advanceClock(level);
        player.invulnerableTime = 0; float beamHealth = player.getHealth();
        ranged.tick(player, true);
        for (int i = 0; i < 23; i++) { advanceClock(level); ranged.tick(player, false); }
        check(player.getHealth() == beamHealth, "seal lanes do not hurt during telegraph");
        for (int i = 0; i < 8; i++) { advanceClock(level); ranged.tick(player, false); }
        check(player.getHealth() < beamHealth, "committed laser seal damages a player standing on its lane");
        var rain = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                new net.minecraft.world.phys.AABB(actor.arenaOrigin()).inflate(40)).stream()
                .filter(ranged::owns).filter(shot -> shot.getDeltaMovement().y < -.8).toList();
        check(rain.size() == 9, "telegraphed phantom sword rain emits nine downward native swords: " + rain.size());
        BlackFoxDomainValidation.run(actor, player);
        for (int i = 0; i < 280; i++) combatTick(actor);
        check(fight.skill() != BlackFoxFight.Skill.DOOM, "healthy phase two never schedules the final stand");
        boss.setHealth(1); actor.combat().damageAccepted();
        check(fight.skill() == BlackFoxFight.Skill.DOOM, "near death starts the final stand");
        float finalHealth = boss.getHealth(); boss.invulnerableTime = 0;
        check(!boss.hurt(boss.damageSources().playerAttack(player), 10000) && boss.getHealth() == finalHealth,
                "boss is invulnerable until the final core breaks");
        var cores = level.getEntitiesOfClass(com.maidweapon.forge.entity.BlackFoxCoreEntity.class,
                new net.minecraft.world.phys.AABB(actor.arenaOrigin()).inflate(30));
        check(cores.size() == 1, "ultimate owns exactly one targetable core");
        var core = cores.get(0); double height = core.getY();
        var lock = (net.minecraft.world.entity.ai.targeting.TargetingConditions) Class.forName(
                "mods.flammpfeil.slashblade.util.TargetSelector").getField("lockon").get(null);
        check(lock.test(player, core), "native SlashBlade lock-on accepts the core");
        for (int i = 0; i < 20; i++) combatTick(actor);
        check(core.getY() < height && core.isPickable(), "core descends and is pickable");
        var scattered = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                new net.minecraft.world.phys.AABB(actor.arenaOrigin()).inflate(40)).stream().filter(ranged::owns).toList();
        check(scattered.size() >= 8 && scattered.stream().anyMatch(shot -> shot.getType() ==
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("slashblade:drive"))),
                "descending ultimate scatters native phantom swords and drives");
        var drive = scattered.stream().filter(shot -> shot.getType() == net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .get(net.minecraft.resources.ResourceLocation.parse("slashblade:drive"))).findFirst().orElseThrow();
        player.invulnerableTime = 0; float driveHealth = player.getHealth();
        com.maidweapon.forge.event.BlackFoxCombatEvents.phantomImpact(new net.minecraftforge.event.entity.ProjectileImpactEvent(
                drive, new net.minecraft.world.phys.EntityHitResult(player)));
        check(player.getHealth() < driveHealth, "native phantom blade really hits the private challenger");
        boolean bounded = true;
        for (int i = 0; i < 1200; i++) {
            advanceClock(level); ranged.scatter(core.position(), player);
            int count = ranged.activeShots(); ranged.scatter(core.position(), player);
            bounded &= count <= BlackFoxDomainAttacks.MAX_SHOTS && ranged.activeShots() == count;
        }
        check(bounded, "1200 ticks of scatter remain bounded and idempotent per tick");
        int aimedCount = 0, scatteredCount = 0;
        for(var shot:level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                new net.minecraft.world.phys.AABB(actor.arenaOrigin()).inflate(40)).stream().filter(ranged::owns).toList()) {
            var direction=shot.getDeltaMovement().normalize();
            var target=player.getEyePosition().subtract(shot.position()).normalize();
            if (direction.dot(target) > .98) aimedCount++; else scatteredCount++;
        }
        check(aimedCount > 0 && scatteredCount > 0, "final stand mixes aimed volleys with broad random scatter");
        var outsider = FakePlayerFactory.get(player.serverLevel(), new GameProfile(UUID.randomUUID(), "CoreOutsider"));
        check(!core.hurt(boss.damageSources().playerAttack(outsider), 1000), "outsider cannot break private core");
        player.setHealth(1000); player.invulnerableTime = 0;
        for (int i = 0; i < BlackFoxFight.CORE_FALL_TICKS + 40; i++) combatTick(actor);
        check(!core.isRemoved() && fight.skill() == BlackFoxFight.Skill.DOOM, "landing never bypasses the core-break requirement");
        core.discard();
        for (int i = 0; i < 20; i++) combatTick(actor);
        core = level.getEntitiesOfClass(com.maidweapon.forge.entity.BlackFoxCoreEntity.class,
                new net.minecraft.world.phys.AABB(actor.arenaOrigin()).inflate(30)).stream()
                .filter(e -> !e.isRemoved()).findFirst().orElseThrow();
        check(fight.skill() == BlackFoxFight.Skill.DOOM, "unexpected core removal retries rather than awarding victory");
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("slashblade:abstract_summoned_sword"));
        var sword = (net.minecraft.world.entity.projectile.Projectile) type.create(level);
        sword.setOwner(player);
        sword.getClass().getMethod("setDamage", double.class).invoke(sword, 1000.0);
        sword.getClass().getMethod("doForceHitEntity", net.minecraft.world.entity.Entity.class).invoke(sword, core);
        check(core.isRemoved() && fight.skill() == BlackFoxFight.Skill.DEFEATED, "real phantom sword breaks final core and awards victory");
        check(ranged.activeShots() == 0 && scattered.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),
                "breaking core cancels all already-emitted ultimate projectiles");
        actor.combat().corruption().close();
        check(core.isRemoved() && !boss.isNoGravity() && !boss.isCurrentlyGlowing(), "session cleanup removes active core and flight state");
        LogUtils.getLogger().info("BLACK_FOX_CORRUPTION_PASS");
    }
    private static void combatTick(BlackFoxCombatant actor) {
        advanceClock((net.minecraft.server.level.ServerLevel) actor.body().level());
        actor.combat().tick();
    }
    private static java.util.List<net.minecraft.world.entity.Entity> slashes(BlackFoxCombatant actor) {
        var result = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        for (var entity : ((net.minecraft.server.level.ServerLevel) actor.body().level()).getAllEntities())
            if (!entity.isRemoved() && BlackFoxSlashCompat.nativeSlash(entity)
                    && entity instanceof net.minecraft.world.entity.projectile.Projectile shot && shot.getOwner() == actor.body()) result.add(entity);
        return result;
    }
    private static String stage(BlackFoxCombatant actor) throws Exception {
        var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade").getField("BLADESTATE").get(null);
        var state = actor.body().getMainHandItem().getCapability(cap).resolve().orElseThrow();
        return state.getClass().getMethod("getComboSeq").invoke(state).toString();
    }
    private static void validateNativeB(BlackFoxCombatant actor, net.minecraft.server.level.ServerPlayer player) throws Exception {
        var boss = actor.body(); var fight = actor.combat().fight();
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000); player.invulnerableTime = 0;
        actor.combat().nativeCombo().stop(); fight.start(BlackFoxFight.Skill.RETURN_BLADE);
        var stages = new java.util.LinkedHashSet<String>();
        var born = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.world.entity.Entity, Boolean>());
        for (int i = 0; i < 35; i++) {
            combatTick(actor); stages.add(stage(actor)); born.addAll(slashes(actor));
            validateComboSynchronization(actor);
            if (actor.combat().nativeCombo().finished()) break;
        }
        for (int i = 1; i <= 3; i++) check(stages.contains("slashblade:combo_b" + i), "registered first-half B stage executes: " + i);
        check(!stages.contains("slashblade:combo_b4") && !stages.contains("slashblade:combo_b7"), "phase one never enters second-half B or finisher");
        check(stages.contains("slashblade:none") && born.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),
                "B3 boundary returns to idle and clears all live native cuts");
        check(born.size() >= 10, "first-half timelines still generate dense real native attacks: " + born.size());
        int once = slashes(actor).size();
        actor.combat().nativeCombo().tick();
        check(slashes(actor).size() == once, "same world tick cannot execute native callbacks twice");
        for (var shot : born) {
            check((int) shot.getClass().getMethod("getColor").invoke(shot)
                    == com.maidweapon.forge.compat.BlackFoxSlashColors.blade(false), "phase-one native B carries pale purple color");
            double ratio = (double) shot.getClass().getMethod("getDamage").invoke(shot);
            check(ratio > .2 && ratio < .3, "native B damage ratio retained");
        }
        actor.combat().nativeCombo().stop();
        validateComboSynchronization(actor);
        check(born.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved), "native session cancels every owned slash");
        fight.start(BlackFoxFight.Skill.RETURN_BLADE);
        for (int i = 0; i < 8; i++) combatTick(actor);
        var shots = slashes(actor);
        check(shots.size() >= 2, "native B opening creates its actual paired slashes");
        player.setPos(boss.getX(), boss.getY(), boss.getZ() - 3.6); player.setYRot(0); boss.setYRot(180);
        var selected = com.maidweapon.forge.compat.BlackFoxNativeCombo.targets(shots.get(0), boss.getBoundingBox().inflate(10), 0);
        check(selected.size() == 1 && selected.get(0) == player, "native Boss attacks select only their own challenger");
        // A true native slash tick goes through target selection, hit effects and native hurt calculations.
        float before = player.getHealth();
        var rankCap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName(
                "mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank").getField("RANK_POINT").get(null);
        Object rating = player.getCapability(rankCap).resolve().orElseThrow();
        var rankApi = Class.forName("mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank");
        rankApi.getMethod("setRawRankPoint", long.class).invoke(rating, 0L);
        rankApi.getMethod("setLastUpdte", long.class).invoke(rating, boss.level().getGameTime());
        long unit = (long) rankApi.getMethod("getUnitCapacity").invoke(rating);
        nativeMotion(player, "slashblade:combo_a1");
        var blocked = shots.get(0); blocked.tick(); blocked.tick();
        check(player.getHealth() == before && fight.skill() == BlackFoxFight.Skill.RETURN_BLADE,
                "ordinary native swing parries one real native cut without canceling B");
        check((long) rankApi.getMethod("getRawRankPoint").invoke(rating) == (long) (unit * .1), "real parry rewards native rating using the native capacity");
        check(!BlackFoxEncounters.consumeParry(player), "native parry consumes one swing");
        check(blocked.getPersistentData().getBoolean("MaidWeaponBlackFoxParried"), "blocked native slash cannot hit on a later tick");
        blocked.tick(); blocked.tick(); check(player.getHealth() == before, "parried native slash stays harmless");
        var unblocked = shots.get(1); unblocked.tick(); unblocked.tick();
        check((long) rankApi.getMethod("getRawRankPoint").invoke(rating) == (long) (unit * .1), "protected and previously parried cuts cannot duplicate rank rewards");
        check(player.getHealth() == before && actor.combat().protects(player), "successful parry grants protection against dense native forceHit slashes");
        player.invulnerableTime = 0;
        check(!player.hurt(boss.damageSources().mobAttack(boss), 10) && player.getHealth() == before,
                "parry protection also intercepts Black Fox's non-native damage");
        check(!player.isInvulnerable(), "parry never enables global player invulnerability");
        float bossHealth = boss.getHealth(); boss.invulnerableTime = 0;
        check(!boss.hurt(boss.damageSources().playerAttack(player), 30) && boss.getHealth() == bossHealth,
                "player cannot damage Black Fox during the mutual clash window");
        for (int i = 0; i < BlackFoxController.PARRY_PROTECTION_TICKS; i++) advanceClock((net.minecraft.server.level.ServerLevel) boss.level());
        check(!actor.combat().protects(player), "short parry protection expires on world time, not native i-frames");
        unblocked.tick(); unblocked.tick();
        check(player.getHealth() < before, "unblocked native B slash really damages challenger even with PvP disabled");
        var far = com.maidweapon.forge.compat.BlackFoxNativeCombo.targets(unblocked, new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), 0);
        check(far.isEmpty(), "native B still respects its native attack bounds");
        float after = player.getHealth();
        long priorRank = (long) rankApi.getMethod("getRankPoint", long.class).invoke(rating, boss.level().getGameTime());
        nativeMotion(player, "slashblade:combo_b1");
        check((long) rankApi.getMethod("getRawRankPoint").invoke(rating) == Math.max(0, priorRank) + (long) (unit * .2),
                "native B clash awards the larger rank bonus once");
        check(fight.skill() == BlackFoxFight.Skill.STAGGER && shots.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),
                "player's native B counter clears already-emitted native attacks");
        check(actor.combat().protects(player), "native B counter also grants short player protection");
        check(player.getHealth() == after, "native B cancellation has no manual duplicate hit");
        for (int i = 0; i < BlackFoxController.PARRY_PROTECTION_TICKS; i++) advanceClock((net.minecraft.server.level.ServerLevel) boss.level());
        boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 1), "Black Fox becomes vulnerable during remaining stagger after clash protection expires");
        fight.start(BlackFoxFight.Skill.RETURN_BLADE); for (int i = 0; i < 7; i++) fight.advance();
        boss.invulnerableTime = 0; float contactHealth = boss.getHealth();
        nativeMotion(player, "slashblade:combo_a1");
        check(!boss.hurt(boss.damageSources().playerAttack(player), 20) && boss.getHealth() == contactHealth
                        && actor.combat().protects(player) && fight.skill() == BlackFoxFight.Skill.RETURN_BLADE,
                "offensive parry blocks player damage even when the player's hit arrives before the Boss slash");
        for (int i = 0; i < BlackFoxController.PARRY_PROTECTION_TICKS; i++) advanceClock((net.minecraft.server.level.ServerLevel) boss.level());
        player.setHealth(1000); player.invulnerableTime = 0;
        LogUtils.getLogger().info("BLACK_FOX_NATIVE_B_PASS: stages {}, {} real native slashes, native damage, parry and cancellation", stages, born.size());
    }
    private static void nativeMotion(net.minecraft.server.level.ServerPlayer player, String combo) throws Exception {
        var event = (net.minecraftforge.eventbus.api.Event) Class.forName("mods.flammpfeil.slashblade.event.BladeMotionEvent")
                .getConstructor(net.minecraft.world.entity.LivingEntity.class, net.minecraft.resources.ResourceLocation.class)
                .newInstance(player, net.minecraft.resources.ResourceLocation.parse(combo));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    }
    private static void validateComboSynchronization(BlackFoxCombatant actor) {
        var entity = (com.maidweapon.forge.entity.BlackFoxBossEntity) actor.body();
        var combo = actor.combat().nativeCombo();
        entity.sync(combo.motion(), actor.combat().fight().phaseTwo());
        check(entity.comboStage() == combo.animationStage(), "native B stage reaches synchronized entity data");
        check(entity.comboStartedAt() == combo.stageStartedAt(), "native B animation clock is the native state's start time");
        if (combo.pursuing()) {
            check(entity.comboStartedAt() <= entity.level().getGameTime(), "stage clock is not in the future");
            check(entity.level().getGameTime() - entity.comboStartedAt()
                    == com.maidweapon.forge.compat.BlackFoxNativeCombo.elapsed(entity),
                    "animation clock matches native blade elapsed time");
        }
        else check(entity.comboStage() == 0, "cancel/end clears active B animation");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
        LogUtils.getLogger().info("BLACK_FOX_CHECK {}", message);
    }
    private BlackFoxBossValidation() { }
}
