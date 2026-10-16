package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.compat.*;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.fox.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class WhiteFoxBlessingValidation {
  @SubscribeEvent
  public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
    if (!Boolean.getBoolean("contractblade.whiteBlessing.validation")) return;
    try {
      if (Boolean.getBoolean("contractblade.whiteBlessing.resume")) resume(event.getServer());
      else run(event.getServer());
    } catch (Throwable failure) {
      com.mojang.logging.LogUtils.getLogger().error("WHITE_BLESSING_FAIL", failure);
    } finally {
      event.getServer().halt(false);
    }
  }

  private static final class Checkpoint extends net.minecraft.world.level.saveddata.SavedData {
    final CompoundTag data;

    Checkpoint(CompoundTag data) {
      this.data = data;
      setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag root) {
      return data.copy();
    }
  }

  private static void resume(net.minecraft.server.MinecraftServer server) {
    var data =
        server
            .overworld()
            .getDataStorage()
            .computeIfAbsent(
                Checkpoint::new,
                () -> new Checkpoint(new CompoundTag()),
                "maid_weapon_blessing_fixture")
            .data;
    check(
        data.hasUUID("Owner")
            && !WhiteFoxBlessingCooldownData.get(server)
                .ready(data.getUUID("Owner"), server.overworld().getGameTime()),
        "oath cooldown survives actual server restart");
    check(
        !WhiteFoxBlessingCooldownData.get(server)
            .consumeCrisis(data.getUUID("Owner"), server.overworld().getGameTime(), 1200),
        "crisis cooldown survives actual restart");
    com.mojang.logging.LogUtils.getLogger().info("WHITE_BLESSING_RESTART_PASS");
  }

  private static void run(net.minecraft.server.MinecraftServer server) throws Exception {
    var level = server.overworld();
    var player =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "BlessingFixture"));
    player.getInventory().clearContent();
    player.getInventory().selected = 0;
    player.setPos(.5, 150, .5);
    player.setHealth(20);
    var template =
        level
            .getStructureManager()
            .get(ResourceLocation.parse("maid_weapon:shinkitsu_shrine"))
            .orElseThrow()
            .save(new CompoundTag());
    var white =
        ItemStack.of(
            template.getList("entities", 10).getCompound(0).getCompound("nbt").getCompound("Item"));
    FoxSpiritTransferService.claimOffering(player, white);
    player.getInventory().setItem(0, white);
    check(
        FoxSpiritCompanionService.initialize(player, white),
        "old original initializes without resetting entity or native blade data");
    var original = white.save(new CompoundTag());
    check(WhiteFoxBlessingService.profile(player) == null, "unearned story cannot unlock blessing");
    com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(
        server.createCommandSourceStack(), player, 8);
    com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters.recordWin(player);
    check(
        WhiteFoxBlessingService.profile(player).has("blessing_domain"),
        "story unlock directly reads existing Lv8");
    var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
    double[] expectedGuardDamage = {9.4, 9.3, 9.2, 9.1, 9.0, 8.8, 8.6, 8.4, 8.2, 8.0};
    for (int lv = 1; lv <= 10; lv++) {
      com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(
          server.createCommandSourceStack(), player, lv);
      WhiteFoxBlessingService.refresh(player);
      var p = WhiteFoxBlessingService.profile(player);
      check(
          p != null && p.features().equals(WhiteFoxBlessingGrowth.fallback().resolve(lv)),
          "route at Lv" + lv);
      check(
          Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - (lv >= 3 ? .12 : .1)) < .00001,
          "passive speed before guard at Lv" + lv);
      check(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED) == (lv >= 3),
          "passive Speed I unlock at Lv" + lv);
      check(player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION) == (lv >= 5),
          "passive Regeneration I unlock at Lv" + lv);
      check(player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE) == (lv >= 9),
          "passive Resistance I unlock at Lv" + lv);
      var ordinary =
          new net.minecraftforge.event.entity.living.LivingDamageEvent(
              player, player.damageSources().generic(), 10);
      WhiteFoxBlessingService.damage(ordinary);
      check(Math.abs(ordinary.getAmount() - expectedGuardDamage[lv - 1]) < .00001,
          "level-scaled percentage guard at Lv" + lv);
      check(Math.abs(p.guardedDamage(1000) - expectedGuardDamage[lv - 1] * 100) < .0001,
          "percentage guard has no fixed HP cap at Lv" + lv);
      var repeat =
          new net.minecraftforge.event.entity.living.LivingDamageEvent(
              player, player.damageSources().generic(), 10);
      WhiteFoxBlessingService.damage(repeat);
      check(repeat.getAmount() == 10, "guard cooldown at Lv" + lv);
      check(
          Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED)
              - (lv >= 4 ? .132 : lv >= 3 ? .12 : .1))
              < .00001,
          "temporary guard movement at Lv" + lv);
      check(
          player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION) == (lv >= 5),
          "lantern stage at Lv" + lv);
      player.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
      clock.setGameTime(level.getGameTime() + 61);
      WhiteFoxBlessingService.refresh(player);
    }
    var effects = net.minecraft.world.effect.MobEffects.POISON;
    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effects, 1000));
    player.addEffect(
        new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.BAD_OMEN, 1000));
    tick(player);
    check(
        !player.hasEffect(effects)
            && player.hasEffect(net.minecraft.world.effect.MobEffects.BAD_OMEN),
        "cleanse respects vanilla whitelist");
    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effects, 1000));
    tick(player);
    check(player.hasEffect(effects), "cleanse cannot repeat immediately");
    clock.setGameTime(level.getGameTime() + 201);
    tick(player);
    check(!player.hasEffect(effects), "cleanse recharges");
    // Explicitly exercise the Lv6 threshold before the later domain/heal fixture.
    com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(
        server.createCommandSourceStack(), player, 6);
    var enemy = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
    enemy.setPos(1, 150, 1);
    level.addFreshEntity(enemy);
    var edgeAtSix =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            enemy, player.damageSources().playerAttack(player), 2);
    WhiteFoxBlessingService.damage(edgeAtSix);
    check(edgeAtSix.getAmount() == 3.5f, "Lv6 assist unlock");
    clock.setGameTime(level.getGameTime() + 101);
    com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(
        server.createCommandSourceStack(), player, 10);
    var maidType =
        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
            ResourceLocation.parse("touhou_little_maid:maid"));
    var ally = (net.minecraft.world.entity.TamableAnimal) maidType.create(level);
    ally.setOwnerUUID(player.getUUID());
    ally.setPos(2, 150, 2);
    ally.setHealth(5);
    level.addFreshEntity(ally);
    var stranger = (net.minecraft.world.entity.TamableAnimal) maidType.create(level);
    stranger.setOwnerUUID(UUID.randomUUID());
    stranger.setPos(.5, 150, .7);
    stranger.setHealth(5);
    level.addFreshEntity(stranger);
    player.setHealth(10);
    for (int i = 0; i < 6; i++) {
      var hit =
          new net.minecraftforge.event.entity.living.LivingDamageEvent(
              enemy, player.damageSources().playerAttack(player), 2);
      WhiteFoxBlessingService.damage(hit);
      check(
          Math.abs(hit.getAmount() - (i % 4 == 0 ? 3.5 : 2)) < .00001,
          "edge bounded inside successful hit");
      clock.setGameTime(level.getGameTime() + 10);
    }
    check(player.getHealth() == 11, "six spaced real-carrier hits heal one HP");
    check(
        ally.getHealth() == 5.5f && stranger.getHealth() == 5,
        "domain heals exactly one owned maid");
    var friendly =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            ally, player.damageSources().playerAttack(player), 2);
    WhiteFoxBlessingService.damage(friendly);
    check(friendly.getAmount() == 2, "owned maid does not trigger assist");
    // Launch-time proof remains mandatory for delayed native damage.
    var shot = new net.minecraft.world.entity.projectile.Snowball(level, player);
    var credit = new com.maidweapon.forge.api.ContractCombatCreditResolver.Credit(player, white);
    var nativeSource = player.damageSources().indirectMagic(shot, player);
    check(
        com.maidweapon.forge.system.mastery.ContractCombatCredits.resolveHit(nativeSource) == null,
        "unstamped projectile rejected");
    com.maidweapon.forge.system.mastery.ContractCombatCredits.stampLaunch(shot, credit);
    check(
        com.maidweapon.forge.system.mastery.ContractCombatCredits.resolveHit(nativeSource) != null,
        "stamped carrier accepted");
    shot.setOwner(enemy);
    check(
        com.maidweapon.forge.system.mastery.ContractCombatCredits.resolveHit(nativeSource) == null,
        "changed shooter rejected");
    ally.discard();
    stranger.discard();
    var unrelated =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            enemy, player.damageSources().generic(), 2);
    WhiteFoxBlessingService.damage(unrelated);
    check(unrelated.getAmount() == 2, "environment hit cannot borrow carrier");
    player.setHealth(20);
    var emptyResident = white.copy();
    emptyResident.getOrCreateTag().remove(FoxSpiritState.ROOT);
    player.getInventory().setItem(0, emptyResident);
    check(WhiteFoxBlessingService.profile(player) == null, "empty resident cannot gain blessing");
    player.getInventory().setItem(0, white);
    var wrongOwner = white.copy();
    FoxSpiritState.resident(wrongOwner).putUUID("OwnerUUID", UUID.randomUUID());
    player.getInventory().setItem(0, wrongOwner);
    check(WhiteFoxBlessingService.profile(player) == null, "foreign fox owner rejected");
    var wrongOrigin = white.copy();
    wrongOrigin.getOrCreateTag().putUUID(FoxSpiritState.ORIGIN, UUID.randomUUID());
    player.getInventory().setItem(0, wrongOrigin);
    check(WhiteFoxBlessingService.profile(player) == null, "nonoriginal blade rejected");
    var unknownSpirit = white.copy();
    FoxSpiritState.resident(unknownSpirit).putUUID("SpiritUUID", UUID.randomUUID());
    player.getInventory().setItem(0, unknownSpirit);
    check(WhiteFoxBlessingService.profile(player) == null, "unledgered fox rejected");
    var ordinaryWhite = white.copy();
    ordinaryWhite.getOrCreateTag().remove(FoxSpiritState.ROOT);
    ordinaryWhite.getOrCreateTag().remove(FoxSpiritState.ORIGIN);
    player.getInventory().setItem(0, ordinaryWhite);
    check(WhiteFoxBlessingService.profile(player) == null, "ordinary same-name blade rejected");
    player.getInventory().setItem(0, white);
    // Native visuals must not acquire a shooter or alter surrounding health/i-frames/motion.
    float healthBefore = enemy.getHealth();
    int framesBefore = enemy.invulnerableTime;
    var motionBefore = enemy.getDeltaMovement();
    var visuals = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
    for (var entity : level.getAllEntities())
      if (BlackFoxSlashCompat.nativeSlash(entity)) visuals.add(entity);
    check(!visuals.isEmpty(), "native visual entity actually spawned");
    for (var entity : visuals)
      if (BlackFoxSlashCompat.nativeSlash(entity)) {
        check(
            entity instanceof net.minecraft.world.entity.projectile.Projectile visualShot
                && visualShot.getOwner() == null,
            "native white visual has no shooter");
        for (int i = 0; i < 8; i++) level.tickNonPassenger(entity);
        check(entity.isRemoved(), "native white visual has short lifetime");
      }
    check(
        enemy.getHealth() == healthBefore
            && enemy.invulnerableTime == framesBefore
            && enemy.getDeltaMovement().equals(motionBefore),
        "visual ticks cannot damage, stun or knock back");
    check(
        original.equals(white.save(new CompoundTag())),
        "blessing and mastery never rewrite old white weapon data");
    player.getInventory().setItem(0, ItemStack.EMPTY);
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, white);
    WhiteFoxBlessingService.refresh(player);
    check(WhiteFoxBlessingService.profile(player) != null, "offhand alone works");
    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
    var foreignAttack =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            enemy, player.damageSources().playerAttack(player), 2);
    WhiteFoxBlessingService.damage(foreignAttack);
    check(foreignAttack.getAmount() == 2, "ordinary main hand cannot activate offhand assist");
    player.getInventory().setItem(0, ItemStack.EMPTY);
    var black = BlackFoxSlashCompat.blade(level);
    BlackFoxSwordCoreValidation.prepareEarnedBlade(player, black);
    player.getInventory().setItem(0, black);
    for (int lv : new int[] {6, 7}) {
      com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(
          server.createCommandSourceStack(), player, lv);
      clock.setGameTime(level.getGameTime() + 101);
      var hit =
          new net.minecraftforge.event.entity.living.LivingDamageEvent(
              enemy, player.damageSources().playerAttack(player), 2);
      WhiteFoxBlessingService.damage(hit);
      check(
          hit.getAmount() == (lv == 7 ? 2.75f : 2), "offhand black resonance boundary at Lv" + lv);
    }
    com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(
        server.createCommandSourceStack(), player, 10);
    var state = BlackFoxSwordCoreValidation.nativeState(black);
    state.getClass().getMethod("setProudSoulCount", int.class).invoke(state, 1000);
    BlackFoxSwordCoreValidation.release(player, black, state);
    var core = BlackFoxSwordArtCompat.active(player);
    check(core != null, "black mainhand core coexists with white offhand");
    check(WhiteFoxBlessingService.profile(player) != null, "black core does not hijack blessing");
    check(
        ContractLifecycleService.manifest(player, black, false),
        "black spirit manifests through native lifecycle");
    check(
        core.isRemoved() && BlackFoxSwordArtCompat.active(player) == null,
        "black manifestation closes the active sword core immediately");
    var blackId = UUID.fromString(ContractCarrierData.getBoundMaidUUID(black));
    check(
        ContractLifecycleService.capture(player, level.getEntity(blackId), black, false),
        "black recall restores qualification");
    BlackFoxSwordCoreValidation.release(
        player, black, BlackFoxSwordCoreValidation.nativeState(black));
    check(BlackFoxSwordArtCompat.active(player) != null, "black can summon again after recall");
    BlackFoxSwordArtCompat.clear(player);
    var clone = white.copy();
    player.getInventory().setItem(1, clone);
    player.getInventory().setItem(0, clone.copy());
    check(
        WhiteFoxBlessingService.profile(player) != null,
        "unchanged authorized snapshot retains baseline copy semantics");
    WhiteFoxBlessingService.refresh(player);
    check(
        Math.abs(
                player.getAttributeValue(Attributes.ATTACK_SPEED)
                    - player.getAttribute(Attributes.ATTACK_SPEED).getBaseValue())
            < .00001,
        "multiple hands do not restore old attack attribute");
    player.getInventory().setItem(1, ItemStack.EMPTY);
    player.getInventory().setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
    var totem =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            player, player.damageSources().generic(), 100);
    WhiteFoxBlessingService.damage(totem);
    check(totem.getAmount() > 20, "native held totem has priority");
    check(
        WhiteFoxBlessingCooldownData.get(server).ready(player.getUUID(), level.getGameTime()),
        "totem does not consume fresh oath cooldown");
    player.getInventory().setItem(0, black);
    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effects, 1000));
    var event =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            player, player.damageSources().generic(), 100);
    WhiteFoxBlessingService.damage(event);
    check(event.getAmount() == 19, "Lv10 oath caps lethal health damage once");
    check(
        !player.hasEffect(effects)
            && player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION),
        "oath cleanses and shields");
    var again =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            player, player.damageSources().generic(), 100);
    WhiteFoxBlessingService.damage(again);
    check(again.getAmount() > 20, "same player cannot repeat oath");
    player.getInventory().setItem(0, ItemStack.EMPTY);
    check(ContractLifecycleService.manifest(player, white, false), "real white manifestation");
    WhiteFoxBlessingService.refresh(player);
    check(
        WhiteFoxBlessingService.profile(player) == null
            && !player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)
            && !player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)
            && !player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)
            && !player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE)
            && Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - .1) < .00001,
        "manifestation immediately removes blessing");
    var id = UUID.fromString(ContractCarrierData.getBoundMaidUUID(white));
    check(
        ContractLifecycleService.capture(player, level.getEntity(id), white, false),
        "real white recall");
    WhiteFoxBlessingService.refresh(player);
    check(WhiteFoxBlessingService.profile(player) != null, "recall restores blessing");
    player.setHealth(4);
    tick(player);
    check(
        player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION),
        "crisis protects low health");
    player.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
    tick(player);
    check(
        !player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION),
        "crisis long cooldown cannot repeat");
    WhiteFoxBlessingService.dimension(
        new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
            player,
            net.minecraft.world.level.Level.OVERWORLD,
            net.minecraft.world.level.Level.NETHER));
    tick(player);
    check(
        !player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION),
        "dimension cleanup keeps persistent crisis receipt");
    WhiteFoxBlessingService.logout(
        new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
    tick(player);
    check(
        !player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION),
        "logout cleanup keeps persistent crisis receipt");
    WhiteFoxBlessingService.death(
        new net.minecraftforge.event.entity.living.LivingDeathEvent(
            player, player.damageSources().generic()));
    tick(player);
    check(
        !player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION),
        "death cleanup keeps persistent crisis receipt");
    player.setHealth(20);
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
    WhiteFoxBlessingService.refresh(player);
    check(
        Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - .1) < .00001,
        "unequip removes attributes");
    // The same ledger-owned spirit survives real carrier consumption and can be rehoused normally.
    var emptySoul =
        new ItemStack(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("touhou_little_maid:smart_slab_empty")));
    var extracted = FoxSpiritTransferService.transfer(player, white, emptySoul);
    check(extracted != null, "white soul extracts before durability-rescue regression");
    var housed =
        FoxSpiritTransferService.transfer(
            player, new ItemStack(Items.IRON_SWORD), extracted.seal());
    check(housed != null, "white soul moves into an ordinary destructible carrier");
    var doomed = housed.weapon();
    String maidId = ContractCarrierData.getBoundMaidUUID(doomed);
    player.getInventory().clearContent();
    player.getInventory().setItem(0, doomed);
    doomed.setDamageValue(doomed.getMaxDamage() - 1);
    doomed.hurtAndBreak(2, player, ignored -> {});
    check(doomed.isEmpty(), "fox carrier was consumed by real native durability");
    clock.setGameTime(level.getGameTime() + 45);
    com.maidweapon.forge.system.deployment.ContractCarrierLossService.process(
        player,
        com.maidweapon.forge.system.deployment.ContractWeaponLocator.indexByBinding(player),
        (p, c) -> {});
    var free = level.getEntity(UUID.fromString(maidId));
    check(
        free != null
            && free.getPersistentData()
                .getString(com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID)
                .isEmpty(),
        "same original white is released as a free maid");
    var originalIdentity = free.getPersistentData().getCompound(FoxSpiritState.ROOT).copy();
    check(
        FoxSpiritLedger.get(server).owns(originalIdentity, "FREE_MAID"),
        "released white retains story identity");
    var newHome = new ItemStack(Items.DIAMOND_SWORD);
    player.getInventory().setItem(0, newHome);
    check(
        ContractLifecycleService.capture(player, free, newHome, false),
        "ordinary right-click capture rehouses the original free fox");
    check(
        FoxSpiritLedger.get(server).owns(FoxSpiritState.resident(newHome), "WEAPON")
            && FoxSpiritState.sameIdentity(originalIdentity, FoxSpiritState.resident(newHome))
            && maidId.equals(ContractCarrierData.getBoundMaidUUID(newHome)),
        "rehousing preserves UUID, origin, owner and fox identity");
    check(
        WhiteFoxBlessingService.profile(player) == null,
        "an ordinary new home does not masquerade as the original reward blade");
    var checkpoint = new CompoundTag();
    checkpoint.putUUID("Owner", player.getUUID());
    level.getDataStorage().set("maid_weapon_blessing_fixture", new Checkpoint(checkpoint));
    level.getDataStorage().save();
    com.mojang.logging.LogUtils.getLogger().info("WHITE_BLESSING_PASS");
  }

  private static void tick(net.minecraft.server.level.ServerPlayer player) {
    WhiteFoxBlessingService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, player));
  }

  private static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
    com.mojang.logging.LogUtils.getLogger().info("White blessing fixture: {}", message);
  }
}
