package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.*;
import com.maidweapon.forge.entity.*;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in packaged-runtime fixture. Only launch against a disposable copied world. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class BlackFoxSwordCoreValidation {
    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.swordCore.validation")) return;
        var spawned = new ArrayList<Entity>();
        try {
            var level = event.getServer().overworld(); level.getChunk(0, 0);
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SwordCoreFixture"));
            player.setPos(.5, 150, .5);
            var blade = BlackFoxSlashCompat.blade(level);
            prepareEarnedBlade(player,blade);
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(event.getServer().createCommandSourceStack(),player,10);
            var core = BlackFoxBossCompat.spawnSwordCore(player); check(core != null, "Core registered"); spawned.add(core);
            var enemy = EntityType.ZOMBIE.create(level); check(enemy != null, "Enemy fixture");
            enemy.setNoAi(true); enemy.setNoGravity(true); enemy.setPos(1.5, 150, 1.5);
            enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000); enemy.setHealth(10000);
            level.addFreshEntity(enemy); spawned.add(enemy);
            var friend = EntityType.COW.create(level); friend.setNoAi(true); friend.setNoGravity(true); friend.setPos(1.5, 150, 1.5);
            level.addFreshEntity(friend); spawned.add(friend);
            var emitter = BlackFoxBossCompat.spawnSwordActor(core, player, null); spawned.add(emitter);
            for (boolean drive : new boolean[]{false, true}) {
                var shot = BlackFoxPhantomCompat.spawn(emitter, core.position(), new net.minecraft.world.phys.Vec3(0, -1, 0), drive, 1);
                spawned.add(shot); float friendlyHealth = friend.getHealth();
                BlackFoxPhantomCompat.impact(shot, friend); check(friend.getHealth() == friendlyHealth, "Native phantom excludes friend");
                float before = enemy.getHealth(); enemy.invulnerableTime = 0;
                BlackFoxPhantomCompat.impact(shot, enemy); check(enemy.getHealth() < before, "Native phantom damage executes");
                shot.discard();
            }
            var actor = BlackFoxBossCompat.spawnSwordActor(core, player, enemy); spawned.add(actor);
            var stages = new HashSet<String>(); float initial = enemy.getHealth(); float friendlyHealth = friend.getHealth();
            long initialTime = level.getGameTime();
            for (int age = 0; age < 180; age++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(initialTime + age); enemy.invulnerableTime = 0;
                actor.tick();
                if (actor.nativeStage() != null) stages.add(actor.nativeStage().toString());
                for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                        core.getBoundingBox().inflate(24))) if (entity.getOwner() == actor && !entity.isRemoved()) {
                    if (!spawned.contains(entity)) spawned.add(entity); entity.tick();
                }
            }
            for (int i = 1; i <= 7; i++) check(stages.contains("slashblade:combo_b" + i), "Native B stage " + i);
            check(enemy.getHealth() < initial, "Native B actually damages enemy");
            check(friend.getHealth() == friendlyHealth, "Native B excludes friend");
            core.tickCount = 12;
            core.follow(null); check(core.following(), "Switch to native B");
            core.follow(null); check(!core.following(), "Switch back to native barrage");
            core.follow(null); check(core.following(), "Switch again");
            spawned.forEach(Entity::discard); // The subsequent area-credit fixture must have exactly one living target.
            validateRelease(event.getServer(), player, blade);
            BlackFoxSwordCoreGrowthValidation.run(player,blade);
            LogUtils.getLogger().info("SWORD_CORE_NATIVE_PASS: B1-B7={}, enemy damage={}, bidirectional switches, friendly protection", stages, initial - enemy.getHealth());
        } catch (Throwable failure) { LogUtils.getLogger().error("SWORD_CORE_NATIVE_FAIL", failure); }
        finally { spawned.forEach(Entity::discard); event.getServer().halt(false); }
    }
    private static void validateRelease(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerPlayer player,
                                        net.minecraft.world.item.ItemStack blade) throws ReflectiveOperationException {
        // Use the real native releaseUsing path, including its Proud Soul charge and registered combo click action.
        BlackFoxSwordArtCompat.unlock(blade, player);
        blade.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1);
        var state = nativeState(blade);
        state.getClass().getMethod("setProudSoulCount", int.class).invoke(state, 150);
        com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(server.createCommandSourceStack(),player,1);
        check(com.maidweapon.forge.system.mastery.ContractMasteryService.getLevel(player) == 1, "SA fixture starts at player mastery 1");
        release(player, blade, state);
        var deployed = BlackFoxSwordArtCompat.active(player);
        check(deployed != null, "real native release summons basic core at Lv1");
        check(((Number)state.getClass().getMethod("getProudSoulCount").invoke(state)).intValue() == 130, "native deploy costs twenty souls");
        deployed.tickCount = 12;
        release(player,blade,state);
        check(!deployed.following() && ((Number)state.getClass().getMethod("getProudSoulCount").invoke(state)).intValue() == 130,
                "Lv1 cannot switch or spend souls on locked combo B");
        com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(server.createCommandSourceStack(),player,5);
        for (boolean following : new boolean[]{true, false, true}) {
            release(player, blade, state);
            check(deployed.following() == following, "native release repeatedly toggles modes");
        }
        check(((Number)state.getClass().getMethod("getProudSoulCount").invoke(state)).intValue() == 40, "native switches each cost thirty souls");
        var indexField = server.getPlayerList().getClass().getSuperclass().getDeclaredField("playersByUUID");
        indexField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var index = (Map<UUID, net.minecraft.server.level.ServerPlayer>) indexField.get(server.getPlayerList());
        var previous = index.put(player.getUUID(), player);
        try {
            deployed.tickCount = 399; deployed.tick();
            check(!deployed.isRemoved(), "core remains alive before four hundred ticks");
            deployed.tickCount = 400; deployed.tick();
            check(deployed.isRemoved(), "core expires at four hundred ticks, twenty seconds");
        } finally {
            BlackFoxSwordArtCompat.clear(player);
            if (previous == null) index.remove(player.getUUID()); else index.put(player.getUUID(), previous);
        }
        com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(server.createCommandSourceStack(),player,1);
        validateCredit(player, blade);
        LogUtils.getLogger().info("SWORD_CORE_RELEASE_PASS: actual native SA, Lv1 basic release / denied switch, Lv5 native B, 20/30 soul costs, repeated switches, 400-tick lifetime");
    }
    private static void validateCredit(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.ItemStack blade) throws ReflectiveOperationException {
        var relation = new com.maidweapon.common.data.MaidWeaponData("Native credit fixture");
        com.maidweapon.forge.system.contract.ContractCarrierData.setMaidData(blade, relation);
        com.maidweapon.forge.system.contract.ContractCarrierData.setOwner(blade, player);
        com.maidweapon.forge.system.contract.ContractCarrierData.setBoundMaidUUID(blade, UUID.randomUUID().toString());
        com.maidweapon.forge.system.contract.ContractCarrierData.ensureBindingId(blade);
        var level = player.serverLevel();
        var shot = BlackFoxPhantomCompat.spawn(player, player.position(), new net.minecraft.world.phys.Vec3(0, 0, 1));
        BlackFoxPhantomCompat.damage(shot, 100);
        player.getInventory().setItem(1, blade); player.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        var enemy = EntityType.ZOMBIE.create(level); enemy.setPos(player.position()); level.addFreshEntity(enemy);
        BlackFoxPhantomCompat.impact(shot, enemy);
        check(!enemy.isAlive() && com.maidweapon.forge.system.mastery.ContractMasteryService.getProgress(player).getTotalKills() == 1,
                "actual native projectile uses its launch contract after a hand switch");
        check(com.maidweapon.forge.system.mastery.ContractMasteryService.getLevel(player) == 2, "native phantom kill advances shared mastery");
        player.setItemSlot(EquipmentSlot.MAINHAND, blade); player.getInventory().setItem(1, net.minecraft.world.item.ItemStack.EMPTY);
        var rebound = (net.minecraft.world.entity.projectile.Projectile)BlackFoxPhantomCompat.spawn(player, player.position(), new net.minecraft.world.phys.Vec3(0,0,1));
        var other = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ReboundFixture"));
        rebound.setOwner(other);
        var target = EntityType.ZOMBIE.create(level);
        com.maidweapon.forge.system.mastery.ContractCombatCredits.record(target, level.damageSources().indirectMagic(rebound, other));
        check(com.maidweapon.forge.system.mastery.ContractCombatCredits.settle(target, level.damageSources().indirectMagic(rebound, other)) == null,
                "changed native shooter cannot credit the original player");
        var area = (net.minecraft.world.entity.projectile.Projectile)Class.forName("mods.flammpfeil.slashblade.util.AttackManager")
                .getMethod("doSlash", LivingEntity.class, float.class, boolean.class, boolean.class, double.class)
                .invoke(null, player, 0f, true, false, 100.0);
        player.getInventory().setItem(1, blade); player.setItemSlot(EquipmentSlot.MAINHAND, BlackFoxSlashCompat.blade(level));
        var areaEnemy = EntityType.ZOMBIE.create(level); areaEnemy.setPos(player.position().add(0, 0, 1));
        areaEnemy.setHealth(1); level.addFreshEntity(areaEnemy); area.tickCount = 2; area.tick();
        check(!areaEnemy.isAlive() && com.maidweapon.forge.system.mastery.ContractMasteryService.getProgress(player).getTotalKills() == 2,
                "native slash area tick retains launch credit when DamageSource becomes the player; health=" + areaEnemy.getHealth()
                        + ", kills=" + com.maidweapon.forge.system.mastery.ContractMasteryService.getProgress(player).getTotalKills());
        check(com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile() == null, "native scoped provenance is cleared after tick");
        area.discard(); areaEnemy.discard();
        shot.discard(); rebound.discard(); enemy.discard();
        LogUtils.getLogger().info("SWORD_CORE_MASTERY_CREDIT_PASS: native source, birth carrier, hand switch, changed-owner rejection");
    }
    /** Fixture server grants identity explicitly; actual earned reward remains covered by shrine-return validation. */
    static void prepareEarnedBlade(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.item.ItemStack blade) {
        var identity = new net.minecraft.nbt.CompoundTag(); var origin = UUID.randomUUID(); var maid = UUID.randomUUID();
        identity.putInt("Version",1); identity.putString("SpiritId",FoxSpiritState.BLACK);
        identity.putUUID("SpiritUUID",maid); identity.putUUID("OriginUUID",origin);
        identity.putUUID("OwnerUUID",player.getUUID()); identity.putUUID("Token",UUID.randomUUID());
        blade.getOrCreateTag().putUUID(FoxSpiritState.ORIGIN,origin); blade.getOrCreateTag().put(FoxSpiritState.ROOT,identity);
        com.maidweapon.forge.system.fox.FoxSpiritLedger.get(player.getServer()).record(identity,"WEAPON");
        com.maidweapon.forge.system.contract.ContractCarrierData.setMaidData(blade,new com.maidweapon.common.data.MaidWeaponData("Sword fixture"));
        com.maidweapon.forge.system.contract.ContractCarrierData.setOwner(blade,player);
        com.maidweapon.forge.system.contract.ContractCarrierData.setBoundMaidUUID(blade,maid.toString());
        com.maidweapon.forge.system.contract.ContractCarrierData.ensureBindingId(blade);
        blade.getOrCreateTag().putString(com.maidweapon.forge.api.EmbeddedSpiritApi.TAG_SPIRIT_ID,FoxSpiritState.BLACK);
        var snapshot=new net.minecraft.nbt.CompoundTag();snapshot.putUUID("Owner",player.getUUID());snapshot.putUUID("UUID",maid);snapshot.putFloat("Health",20);
        var forge=new net.minecraft.nbt.CompoundTag();forge.putString(com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID,
                com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(blade));snapshot.put("ForgeData",forge);
        try{com.maidweapon.forge.system.MaidEntityDataCodec.write(blade.getOrCreateTag(),snapshot);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        var persisted = player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        persisted.putBoolean("MaidWeaponBlackFoxDefeated",true); player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,persisted);
        BlackFoxSwordArtCompat.unlock(blade,player);
    }
    static Object nativeState(net.minecraft.world.item.ItemStack blade) throws ReflectiveOperationException {
        var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade")
                .getField("BLADESTATE").get(null);
        return blade.getCapability(cap).resolve().orElseThrow();
    }
    static void release(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.ItemStack blade, Object state)
            throws ReflectiveOperationException {
        state.getClass().getMethod("setComboSeq", ResourceLocation.class).invoke(state, ResourceLocation.parse("slashblade:none"));
        state.getClass().getMethod("setLastActionTime", long.class).invoke(state, player.level().getGameTime());
        blade.getItem().releaseUsing(blade, player.level(), player, blade.getUseDuration() - 100);
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
