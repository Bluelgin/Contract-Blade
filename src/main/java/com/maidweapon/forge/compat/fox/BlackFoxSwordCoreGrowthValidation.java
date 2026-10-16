package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.*;
import com.maidweapon.forge.entity.*;
import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.system.mastery.*;
import static com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Called exclusively by the opt-in swordCore fixture; all attacks use the real native implementation. */
final class BlackFoxSwordCoreGrowthValidation {
    static void run(ServerPlayer player, ItemStack blade) throws Exception {
        var server = player.getServer(); var level = player.serverLevel(); var spawned = new ArrayList<Entity>();
        var field = server.getPlayerList().getClass().getSuperclass().getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var index = (Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
        var old = index.put(player.getUUID(),player);
        try {
            player.getInventory().clearContent(); player.setItemSlot(EquipmentSlot.MAINHAND,blade);
            var state = BlackFoxSwordCoreValidation.nativeState(blade);
            Set<net.minecraft.resources.ResourceLocation> previous = Set.of();
            for (int mastery=1;mastery<=10;mastery++) {
                ContractMasteryService.setLevel(server.createCommandSourceStack(),player,mastery);
                var result = ContractAbilityProgressionService.query(player,blade,ABILITY);
                check(result.status()==ContractAbilityProgressionService.Status.READY && result.masteryLevel()==mastery,"real player mastery resolution "+mastery);
                check(result.capabilities().features().containsAll(previous) && result.capabilities().features().size()>previous.size(),"new cumulative feature "+mastery);
                previous=result.capabilities().features();
            }
            var reward = blade.getTag().getCompound("MaidWeaponBlackFoxSwordArt").copy();
            blade.getTag().remove("MaidWeaponBlackFoxSwordArt");
            check(ContractAbilityProgressionService.query(player,blade,ABILITY).status()==ContractAbilityProgressionService.Status.DENIED,"Lv10 cannot invent reward");
            blade.getTag().put("MaidWeaponBlackFoxSwordArt",reward);
            var identity = com.maidweapon.forge.system.fox.FoxSpiritState.resident(blade);
            var originalToken=identity.getUUID("Token"); identity.putUUID("Token",UUID.randomUUID());
            check(ContractAbilityProgressionService.query(player,blade,ABILITY).status()==ContractAbilityProgressionService.Status.DENIED,"stale ledger token denies SA");
            identity.putUUID("Token",originalToken);
            ContractMasteryService.setLevel(server.createCommandSourceStack(),player,5);
            state.getClass().getMethod("setProudSoulCount",int.class).invoke(state,1000);
            BlackFoxSwordCoreValidation.release(player,blade,state);
            var core=BlackFoxSwordArtCompat.active(player);check(core!=null,"Lv5 actual release"); spawned.add(core);
            check(core.positionMode()==PositionMode.ANCHORED,"Lv5 still anchored");
            ContractMasteryService.setLevel(server.createCommandSourceStack(),player,6);
            BlackFoxSwordArtCompat.refreshGrowth(player);
            check(core.positionMode()==PositionMode.OWNER_FOLLOW && core.attackMode()==AttackMode.BARRAGE,"follow is independent of combo");
            player.setPos(player.position().add(12,3,0));
            for(int i=0;i<30;i++) tickCore(core);
            check(!core.isRemoved() && Math.abs(core.getY()-player.getY()-7)<.001 && core.centre().distanceToSqr(player.position())<.001,"smooth Lv6 core settles seven blocks above moved owner");
            var enemies=new ArrayList<LivingEntity>();
            for(int i=0;i<4;i++) {
                var enemy=EntityType.ZOMBIE.create(level);enemy.setNoAi(true);enemy.setNoGravity(true);
                enemy.setPos(player.position().add(1+i,0,1));enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);enemy.setHealth(10000);
                level.addFreshEntity(enemy); spawned.add(enemy);enemies.add(enemy);
            }
            var far=EntityType.ZOMBIE.create(level);far.setNoAi(true);far.setNoGravity(true);far.setPos(player.position().add(12,0,0));level.addFreshEntity(far);spawned.add(far);
            check(core.eligible(player,far,AttackMode.BARRAGE) && !core.eligible(player,far,AttackMode.COMBO_B),"12-block target barrage-only");
            ContractMasteryService.setLevel(server.createCommandSourceStack(),player,8);
            state.getClass().getMethod("setTargetEntityId",int.class).invoke(state,enemies.get(3).getId());
            BlackFoxSwordCoreValidation.release(player,blade,state);
            check(core.attackMode()==AttackMode.COMBO_B && core.targets(player,AttackMode.COMBO_B).get(0)==enemies.get(3),"Lv8 native lock leads combo allocation");
            tickCore(core);
            var actors=level.getEntitiesOfClass(BlackFoxSwordActorEntity.class,core.getBoundingBox().inflate(24),a->a.core()==core && a.victim()!=null);
            check(actors.size()==3,"Lv7+ bounded three hunters");
            check(actors.stream().noneMatch(a->a.permits(far)),"every hunter impact rejects distant target");
            BlackFoxSwordCoreValidation.release(player,blade,state);
            check(core.targets(player,AttackMode.BARRAGE).get(0)==enemies.get(3),"Lv8 barrage respects native lock");
            var emitter=BlackFoxBossCompat.spawnSwordActor(core,player,null);spawned.add(emitter);
            var marked=enemies.get(1);
            var shot=BlackFoxPhantomCompat.spawn(emitter,marked.getEyePosition(),new Vec3(0,0,1));spawned.add(shot);
            marked.invulnerableTime=0;BlackFoxPhantomCompat.impact(shot,marked);
            check(core.marked(marked),"actual native hit adds sword mark");shot.discard();
            state.getClass().getMethod("setTargetEntityId",int.class).invoke(state,-1);
            check(core.priority(player,marked)>core.priority(player,far),"marked/protect priorities bias target selection");
            var chainVictim=EntityType.ZOMBIE.create(level);chainVictim.setNoAi(true);chainVictim.setNoGravity(true);
            chainVictim.setPos(player.position().add(6,0,0));chainVictim.setHealth(1);level.addFreshEntity(chainVictim);spawned.add(chainVictim);
            var chainShot=BlackFoxPhantomCompat.spawn(emitter,chainVictim.getEyePosition(),new Vec3(0,0,1));spawned.add(chainShot);
            BlackFoxPhantomCompat.damage(chainShot,100);BlackFoxPhantomCompat.impact(chainShot,chainVictim);
            check(!chainVictim.isAlive() && core.pendingChains()==1,"real marked death queues bounded chain");
            var secondVictim=EntityType.ZOMBIE.create(level);secondVictim.setNoAi(true);secondVictim.setNoGravity(true);
            secondVictim.setPos(player.position().add(6,0,1));secondVictim.setHealth(1);level.addFreshEntity(secondVictim);spawned.add(secondVictim);
            var secondShot=BlackFoxPhantomCompat.spawn(emitter,secondVictim.getEyePosition(),new Vec3(0,0,1));spawned.add(secondShot);
            BlackFoxPhantomCompat.damage(secondShot,100);BlackFoxPhantomCompat.impact(secondShot,secondVictim);
            check(core.pendingChains()==1,"chain cooldown prevents recursive death bursts");
            tickCore(core);check(core.pendingChains()==0,"chain executes through native shots");
            chainShot.discard();secondShot.discard();
            ContractMasteryService.setLevel(server.createCommandSourceStack(),player,9);
            BlackFoxSwordCoreValidation.release(player,blade,state);
            // Post real Forge damage handling even though FakePlayer normally suppresses some incoming combat.
            var hurt=new net.minecraftforge.event.entity.living.LivingDamageEvent(player,level.damageSources().mobAttack((LivingEntity)enemies.get(2)),1);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(hurt);
            check(core.priority(player,enemies.get(2))==4,"Lv9 actual Forge owner-hurt hook prioritizes attacker");
            ContractMasteryService.setLevel(server.createCommandSourceStack(),player,10);
            BlackFoxSwordCoreValidation.release(player,blade,state);
            check(core.synchronizedDomain() && core.growth().has(SYNC),"Lv10 actual mode switch opens bounded domain sync");
            float domainHealth = enemies.get(0).getHealth()+enemies.get(1).getHealth()+enemies.get(2).getHealth()+enemies.get(3).getHealth();
            for(int i=0;i<25;i++) tickCore(core);
            check(enemies.stream().mapToDouble(LivingEntity::getHealth).sum()<domainHealth,"Lv10 native coordinated ticks actually damage enemies");
            check(!core.synchronizedDomain(),"collaboration ends without extending duration");
            // Native Drive, vertical Drive and WaveEdge: colour, real damage, ownership and shooter attribution.
            var target=enemies.get(0);
            for(int kind=1;kind<=3;kind++) {
                var before=new HashSet<>(level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,core.getBoundingBox().inflate(32)));
                BlackFoxCoreNativeBarrage.fire(emitter,target.position().add(0,2,0),target.getEyePosition(),kind,.6);
                var emitted=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,core.getBoundingBox().inflate(32),p->!before.contains(p)&&p.getOwner()==emitter);
                check(emitted.size()==(kind==3?2:1),"native emission count "+kind);
                for(var projectile:emitted) {
                    spawned.add(projectile);
                    check((((Number)projectile.getClass().getMethod("getColor").invoke(projectile)).intValue() & 0xFFFFFF)==0xB04CE5,"native purple palette kind="+kind+", colour="+projectile.getClass().getMethod("getColor").invoke(projectile)
                            +", actor="+BlackFoxSwordCoreValidation.nativeState(emitter.getMainHandItem()).getClass().getMethod("getColorCode").invoke(BlackFoxSwordCoreValidation.nativeState(emitter.getMainHandItem())));
                    check(emitter.masteryCredit()!=null,"original carrier survives switch and movement");
                    float health=target.getHealth();target.invulnerableTime=0;BlackFoxPhantomCompat.impact(projectile,target);
                    check(target.getHealth()<health,"actual native barrage damage "+kind);projectile.discard();
                }
            }
            validateMultipleCredits(player,blade,core,spawned);
            LogPass();
        } finally {
            BlackFoxSwordArtCompat.clear(player);spawned.forEach(Entity::discard);
            if(old==null)index.remove(player.getUUID());else index.put(player.getUUID(),old);
        }
    }
    private static void validateMultipleCredits(ServerPlayer player,ItemStack blade,BlackFoxSwordCoreEntity core,List<Entity> spawned) throws Exception {
        var level=player.serverLevel();
        var other=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SwordOtherOwner"));
        other.setPos(player.position().add(0,0,2));var otherBlade=BlackFoxSlashCompat.blade(level);
        BlackFoxSwordCoreValidation.prepareEarnedBlade(other,otherBlade);other.setItemSlot(EquipmentSlot.MAINHAND,otherBlade);
        ContractMasteryService.setLevel(player.getServer().createCommandSourceStack(),other,10);
        var otherCore=BlackFoxBossCompat.spawnSwordCore(other);spawned.add(otherCore);
        int before=ContractMasteryService.getProgress(player).getTotalKills(),beforeOther=ContractMasteryService.getProgress(other).getTotalKills();
        player.getInventory().setItem(1,blade);
        player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        for(int owner=0;owner<2;owner++) {
            var actor=BlackFoxBossCompat.spawnSwordActor(owner==0?core:otherCore,owner==0?player:other,null);spawned.add(actor);
            check(SlashBladeCompat.isNamedBlade(actor.getMainHandItem(),"item.slashblade.fox_black"),"new actor copies summon blade after hand switch");
            var victim=EntityType.ZOMBIE.create(level);victim.setNoAi(true);victim.setNoGravity(true);victim.setPos(player.position().add(6,0,3+owner));victim.setHealth(1);level.addFreshEntity(victim);spawned.add(victim);
            var shot=BlackFoxPhantomCompat.spawn(actor,victim.getEyePosition(),new Vec3(0,0,1));spawned.add(shot);
            BlackFoxPhantomCompat.damage(shot,100);BlackFoxPhantomCompat.impact(shot,victim);check(!victim.isAlive(),"actual attributed native kill "+owner);
        }
        check(ContractMasteryService.getProgress(player).getTotalKills()==before+1,"first owner's exact one kill after hand switch");
        check(ContractMasteryService.getProgress(other).getTotalKills()==beforeOther+1,"second owner's kill remains isolated");
        player.setItemSlot(EquipmentSlot.MAINHAND,blade);player.getInventory().setItem(1,ItemStack.EMPTY);
        com.mojang.logging.LogUtils.getLogger().info("SWORD_CORE_MULTI_CREDIT_PASS: two owners, summon-blade copy after hand switch, native delayed kills isolated");
    }
    private static void tickCore(BlackFoxSwordCoreEntity core) {
        core.tickCount++; core.tick();
        var level=core.level();
        for(var actor:level.getEntitiesOfClass(BlackFoxSwordActorEntity.class,core.getBoundingBox().inflate(32),a->a.core()==core && !a.isRemoved())) {
            actor.tickCount++; actor.tick();
        }
        for(var shot:level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,core.getBoundingBox().inflate(32),
                p->p.getOwner() instanceof BlackFoxSwordActorEntity actor && actor.core()==core && !p.isRemoved())) {
            shot.tickCount++; shot.tick();
        }
        var data=(net.minecraft.world.level.storage.ServerLevelData)core.level().getLevelData();
        data.setGameTime(core.level().getGameTime()+1);
    }
    private static void LogPass() { com.mojang.logging.LogUtils.getLogger().info("SWORD_CORE_GROWTH_PASS: Lv1-10 shared resolution, denied reward/token, native Lv6 follow, Lv8 lock, three short-range hunters, mark, retaliation, bounded Lv10 sync, native Drive/vertical/WaveEdge purple damage"); }
    private static void check(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
    private BlackFoxSwordCoreGrowthValidation() { }
}
