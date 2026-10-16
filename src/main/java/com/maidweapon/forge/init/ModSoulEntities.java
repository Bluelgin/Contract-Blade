package com.maidweapon.forge.init;

import com.maidweapon.forge.entity.SoulAnchorEntity;
import net.minecraft.world.entity.*;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid = "maid_weapon", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModSoulEntities {
  public static final DeferredRegister<EntityType<?>> TYPES =
      DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "maid_weapon");
  public static final RegistryObject<EntityType<SoulAnchorEntity>> ANCHOR =
      TYPES.register(
          "soul_anchor",
          () ->
              EntityType.Builder.<SoulAnchorEntity>of(SoulAnchorEntity::new, MobCategory.MISC)
                  .sized(1.3f, 1.8f)
                  .clientTrackingRange(4) // EntityType tracking units are chunks: 64 blocks.
                  .updateInterval(2)
                  .noSave()
                  .noSummon()
                  .build("maid_weapon:soul_anchor"));

  private ModSoulEntities() {}
}
