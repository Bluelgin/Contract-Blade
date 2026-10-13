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
        WhiteFoxBlessingService.profile(player).number("attack_speed") == .06,
        "story unlock directly reads existing Lv8");
    for (int lv = 1; lv <= 10; lv++) {
      com.maidweapon.forge.system.mastery.ContractMasteryService.setLevel(
          server.createCommandSourceStack(), player, lv);
      WhiteFoxBlessingService.refresh(player);
      var p = WhiteFoxBlessingService.profile(player);
      check(
          p != null
              && Math.abs(
                      player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                          - .1 * (lv < 6 ? 1.03 : 1.05))
                  < .00001,
          "native movement attribute at Lv" + lv);
      check(
          Math.abs(
                  player.getAttributeValue(Attributes.ATTACK_SPEED)
                      - player.getAttribute(Attributes.ATTACK_SPEED).getBaseValue()
                          * (1 + p.number("attack_speed")))
              < .00001,
          "attack attribute at Lv" + lv);
      check(
          Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - (lv >= 7 ? .10 : 0))
              < .00001,
          "knockback attribute at Lv" + lv);
      var ordinary =
          new net.minecraftforge.event.entity.living.LivingDamageEvent(
              player, player.damageSources().generic(), 10);
      var fall =
          new net.minecraftforge.event.entity.living.LivingDamageEvent(
              player, player.damageSources().fall(), 10);
      WhiteFoxBlessingService.damage(ordinary);
      WhiteFoxBlessingService.damage(fall);
      double reduction = lv < 4 ? 1 : lv < 9 ? .97 : .94;
      check(
          Math.abs(ordinary.getAmount() - 10 * reduction) < .00001
              && Math.abs(fall.getAmount() - 10 * reduction * (lv < 2 ? 1 : .9)) < .00001,
          "native damage handlers at Lv" + lv);
    }
    check(
        original.equals(white.save(new CompoundTag())),
        "blessing and mastery never rewrite old white weapon data");
    player.getInventory().setItem(0, ItemStack.EMPTY);
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, white);
    WhiteFoxBlessingService.refresh(player);
    check(WhiteFoxBlessingService.profile(player) != null, "offhand alone works");
    var black = BlackFoxSlashCompat.blade(level);
    BlackFoxSwordCoreValidation.prepareEarnedBlade(player, black);
    player.getInventory().setItem(0, black);
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
    check(
        WhiteFoxBlessingService.profile(player) == null, "copied white cannot stack or authorize");
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
    var event =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            player, player.damageSources().generic(), 100);
    WhiteFoxBlessingService.damage(event);
    check(event.getAmount() == 19, "Lv10 oath caps lethal health damage once");
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
            && Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - .1) < .00001,
        "manifestation immediately removes blessing");
    var id = UUID.fromString(ContractCarrierData.getBoundMaidUUID(white));
    check(
        ContractLifecycleService.capture(player, level.getEntity(id), white, false),
        "real white recall");
    WhiteFoxBlessingService.refresh(player);
    check(WhiteFoxBlessingService.profile(player) != null, "recall restores blessing");
    player.setHealth(10);
    WhiteFoxBlessingService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, player));
    var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
    data.setGameTime(level.getGameTime() + 199);
    WhiteFoxBlessingService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, player));
    check(player.getHealth() == 10, "combat delay prevents healing");
    data.setGameTime(level.getGameTime() + 2);
    WhiteFoxBlessingService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, player));
    check(player.getHealth() == 11, "bounded out-of-combat heal");
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
    WhiteFoxBlessingService.refresh(player);
    check(
        Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - .1) < .00001,
        "unequip removes attributes");
    var checkpoint = new CompoundTag();
    checkpoint.putUUID("Owner", player.getUUID());
    level.getDataStorage().set("maid_weapon_blessing_fixture", new Checkpoint(checkpoint));
    level.getDataStorage().save();
    com.mojang.logging.LogUtils.getLogger().info("WHITE_BLESSING_PASS");
  }

  private static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
    com.mojang.logging.LogUtils.getLogger().info("White blessing fixture: {}", message);
  }
}
