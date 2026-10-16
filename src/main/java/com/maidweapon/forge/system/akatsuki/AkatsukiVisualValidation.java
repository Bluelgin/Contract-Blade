package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real gameplay/render fixture. Refuses every world except its named disposable save. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class AkatsukiVisualValidation {
  private record Scene(long started, Zombie target) {}

  private static final Map<ServerPlayer, Scene> SCENES = new WeakHashMap<>();
  private static final Map<UUID, Long> STARTS = new java.util.concurrent.ConcurrentHashMap<>();

  public static long started(UUID owner) {
    return STARTS.getOrDefault(owner, -1L);
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
    SCENES.clear();
    STARTS.clear();
  }

  @SubscribeEvent
  public static void tick(TickEvent.PlayerTickEvent e) {
    if (!Boolean.getBoolean("contractblade.akatsukiVisual.validation")
        || e.phase != TickEvent.Phase.START
        || !(e.player instanceof ServerPlayer p)
        || !p.getServer().getWorldData().getLevelName().equals("Akatsuki Visual Validation"))
      return;
    try {
      var scene = SCENES.get(p);
      if (scene == null) {
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(6); // No natural regeneration during threshold screenshots.
        p.teleportTo(p.serverLevel(), .5, 81, .5, 0, 0);
        for (int x = -10; x <= 10; x++)
          for (int z = -10; z <= 12; z++)
            p.serverLevel()
                .setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState());
        p.serverLevel().setDayTime(1000);
        var receipt = AkatsukiRescueSavedData.get(p.getServer());
        // Reusing this disposable save must wait out its real saved cooldown, never erase it.
        if (receipt.readyAt(p.getUUID()) > p.level().getGameTime())
          ((net.minecraft.world.level.storage.ServerLevelData) p.level().getLevelData())
              .setGameTime(receipt.readyAt(p.getUUID()));
        AkatsukiDomainValidation.prepareBlade(p);
        ContractMasteryService.setLevel(p.getServer().createCommandSourceStack(), p, 10);
        var target =
            Boolean.getBoolean("contractblade.soulVisual.validation")
                ? (Zombie) SoulProvenanceValidation.visualTarget(p.serverLevel())
                : EntityType.HUSK.create(p.serverLevel());
        target.moveTo(.5, 81, 4.5, 180, 0);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        target.setHealth(1024);
        if (!Boolean.getBoolean("contractblade.soulVisual.validation"))
          p.serverLevel().addFreshEntity(target);
        scene = new Scene(p.level().getGameTime(), target);
        SCENES.put(p, scene);
        STARTS.put(p.getUUID(), scene.started());
        LogUtils.getLogger().info("AKATSUKI_VISUAL_SCENE_READY");
      }
      long age = p.level().getGameTime() - scene.started();
      var target = scene.target();
      if (Boolean.getBoolean("contractblade.soulVisual.validation")) {
        if (age == 160) p.setHealth(p.getMaxHealth() * .4f);
        target.moveTo(.5, 81, 3.4, 180, 0);
        long local = age % 80;
        if (age > 40 && age < 550 && local == 48) p.teleportTo(p.serverLevel(), .5, 81, 1.9, 0, 0);
        if (age > 40 && age < 550 && local == 50) {
          target.setHealth(1024);
          target.invulnerableTime = 0;
          SoulProvenanceValidation.nativeHit(target, p, 100);
        }
        if (age > 40 && age < 550 && local == 79) {
          var a =
              p.getServer()
                  .getAdvancements()
                  .getAdvancement(
                      net.minecraft.resources.ResourceLocation.parse(
                          "slashblade:abilities/enemy_step"));
          if (a != null && p.getAdvancements().getOrStartProgress(a).isDone())
            LogUtils.getLogger().info("SOUL_NATIVE_ENEMY_STEP_OBSERVED age={}", age);
        }
        return;
      }
      if (Boolean.getBoolean("contractblade.akatsukiRain.validation")) {
        // Same real authorization, contact and rain cue path, with isolated presentation scenarios.
        if (age == 340) p.setHealth(p.getMaxHealth() * .4f);
        boolean moving = age >= 180 && age < 330 || age >= 490 && age < 640;
        double x = moving ? .5 + Math.sin((age - (age < 340 ? 180 : 490)) * .06) * 2.2 : .5;
        target.moveTo(x, 81, 5.5, 180, 0);
        if (age > 40 && age < 810 && age % 8 == 0) {
          target.setHealth(1024);
          target.invulnerableTime = 0;
          target.hurt(p.damageSources().playerAttack(p), .1f);
        }
        if (age == 60 || age == 180 || age == 340 || age == 490 || age == 650 || age == 730)
          LogUtils.getLogger()
              .info("AKATSUKI_RAIN_SCENARIO age={} moving={} hp={}", age, moving, p.getHealth());
        return;
      }
      if (age == 160) p.setHealth(p.getMaxHealth() * .4f);
      if (age == 300) {
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().mobAttack(target), 100);
      }
      if (age == 520) {
        // High native contribution and enough remaining HP for one stronger domain cut before
        // the provider's first real execution pulse. No provider state or threshold is changed.
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        target.setHealth(1024);
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().playerAttack(p), 640);
      }
      if (age == 528) {
        target.setHealth(290);
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().playerAttack(p), 250);
      }
      if (age > 40 && age < 500 && age % 8 == 0 && target.isAlive()) {
        target.setHealth(1024);
        target.invulnerableTime = 0;
        // Real player-attributed contact; the server domain and damage guard run normally.
        target.hurt(p.damageSources().playerAttack(p), .1f);
      }
    } catch (Exception | LinkageError failure) {
      LogUtils.getLogger().error("AKATSUKI_VISUAL_SCENE_FAIL", failure);
      SCENES.put(p, new Scene(Long.MAX_VALUE, null));
    }
  }

  private AkatsukiVisualValidation() {}
}
