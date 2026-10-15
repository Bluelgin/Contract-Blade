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
        AkatsukiDomainValidation.prepareBlade(p);
        ContractMasteryService.setLevel(p.getServer().createCommandSourceStack(), p, 10);
        var target = EntityType.HUSK.create(p.serverLevel());
        target.moveTo(.5, 81, 4.5, 180, 0);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        target.setHealth(1024);
        p.serverLevel().addFreshEntity(target);
        scene = new Scene(p.level().getGameTime(), target);
        SCENES.put(p, scene);
        LogUtils.getLogger().info("AKATSUKI_VISUAL_SCENE_READY");
      }
      long age = p.level().getGameTime() - scene.started();
      var target = scene.target();
      if (age == 160) p.setHealth(p.getMaxHealth() * .4f);
      if (age == 300) {
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().mobAttack(target), 100);
      }
      if (age == 520) {
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().playerAttack(p), 125);
      }
      if (age == 528) {
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().playerAttack(p), 55);
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
