package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.api.*;
import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in disposable dedicated-server fixture; never infers full optional boss compatibility. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class AkatsukiDomainValidation {
  @SubscribeEvent
  public static void started(ServerStartedEvent e) {
    if (!Boolean.getBoolean("contractblade.akatsukiDomain.validation")) return;
    try {
      run(e.getServer());
      LogUtils.getLogger().info("AKATSUKI_DOMAIN_SERVER_PASS");
    } catch (Throwable error) {
      LogUtils.getLogger().error("AKATSUKI_DOMAIN_SERVER_FAIL", error);
    } finally {
      e.getServer().halt(false);
    }
  }

  private static void run(net.minecraft.server.MinecraftServer server) throws Exception {
    var level = server.overworld();
    var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "DomainFixture"));
    owner.getInventory().clearContent();
    owner.getInventory().selected = 0;
    owner.moveTo(.5, 150, .5, 0, 0);
    owner.setHealth(20);
    var weapon = new ItemStack(Items.IRON_SWORD);
    owner.getInventory().setItem(0, weapon);
    String spirit = AkatsukiDomainAuthorization.SPIRIT;
    check(
        IntrinsicSpiritApi.ensureIntrinsicSpirit(owner, weapon, spirit, "赤月"),
        "real native intrinsic creation");
    check(
        IntrinsicSpiritApi.setIntrinsicSpiritActive(owner, weapon, spirit, true),
        "native channel selection");
    check(AkatsukiDomainAuthorization.resident(owner, weapon), "complete native stored proof");
    check(
        !AkatsukiDomainAuthorization.allows(owner, weapon),
        "ordinary sword cannot impersonate provider blade");
    var original = weapon.getTag().copy();
    EmbeddedSpiritApi.setDormant(weapon, true);
    check(
        !AkatsukiDomainAuthorization.resident(owner, weapon), "dormant proof rejected immediately");
    weapon.setTag(original.copy());
    var snapshot = MaidEntityDataCodec.read(weapon.getTag());
    snapshot.putUUID("Owner", UUID.randomUUID());
    MaidEntityDataCodec.write(weapon.getTag(), snapshot);
    check(
        !AkatsukiDomainAuthorization.resident(owner, weapon),
        "tampered full owner rejected within proof cache interval");
    weapon.setTag(original.copy());
    weapon.getOrCreateTag().putUUID("OwnerUUID", UUID.randomUUID());
    check(!AkatsukiDomainAuthorization.resident(owner, weapon), "wrong carrier owner");
    weapon.setTag(original.copy());
    var fabricated = weapon.copy();
    var fabricatedData = MaidEntityDataCodec.read(fabricated.getTag());
    var inventedMaid = UUID.randomUUID();
    var inventedBinding = UUID.randomUUID().toString();
    fabricatedData.putUUID("UUID", inventedMaid);
    fabricatedData.getCompound("ForgeData").putString("MaidWeaponBindingId", inventedBinding);
    fabricated.getOrCreateTag().putString("MaidUUID", inventedMaid.toString());
    fabricated.getOrCreateTag().putString("MaidBindingId", inventedBinding);
    MaidEntityDataCodec.write(fabricated.getTag(), fabricatedData);
    owner.getInventory().setItem(0, fabricated);
    check(
        !AkatsukiDomainAuthorization.resident(owner, fabricated),
        "coherent fabricated full NBT has no server origin");
    owner.getInventory().setItem(0, weapon);
    var fake = new ItemStack(Items.PAPER);
    fake.getOrCreateTag().putString(EmbeddedSpiritApi.TAG_SPIRIT_ID, spirit);
    fake.getOrCreateTag().putBoolean("blade_tetra_akatsuki_unlocked", true);
    owner.getInventory().setItem(0, fake);
    check(
        !AkatsukiDomainAuthorization.allows(owner, fake),
        "forged name/unlock/spirit strings do not grant domain");
    owner.getInventory().setItem(0, weapon);
    String maid = ContractCarrierData.getBoundMaidUUID(weapon);
    ContractCompanionService.toggle(owner);
    check(level.getEntity(UUID.fromString(maid)) != null, "real manifestation");
    check(
        !AkatsukiDomainAuthorization.resident(owner, weapon),
        "manifested spirit cannot lend domain");
    check(InfusedMaidDeploymentSystem.forceRecall(owner, maid, 0), "real native recall");
    check(AkatsukiDomainAuthorization.resident(owner, weapon), "recall restores full residency");
    for (int n = 1; n <= 10; n++) {
      ContractMasteryService.setLevel(server.createCommandSourceStack(), owner, n);
      check(ContractMasteryService.getLevel(owner) == n, "shared player mastery " + n);
      check(
          AkatsukiDomainGrowth.fallback().resolve(n).features().size() == n,
          "distinct route capability " + n);
      check(
          ContractAbilityProgressionService.query(owner, weapon, AkatsukiDomainGrowth.ID).status()
              == ContractAbilityProgressionService.Status.DENIED,
          "high mastery cannot bypass real provider identity");
    }
    var zombie = EntityType.ZOMBIE.create(level);
    zombie.moveTo(2, 150, 2, 0, 0);
    zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);
    zombie.setHealth(20);
    var playerSource = owner.damageSources().playerAttack(owner);
    check(zombie.hurt(playerSource, 6), "baseline native hit");
    int timer = zombie.invulnerableTime;
    float health = zombie.getHealth();
    var key =
        ResourceKey.create(
            Registries.DAMAGE_TYPE, AkatsukiDomainGrowth.id("akatsuki_domain_rapid"));
    var rapid =
        new DamageSource(
            level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key),
            null,
            owner);
    try (var window = DomainHitWindow.capture(zombie)) {
      check(window != null, "production-name-safe hit bookkeeping access");
      check(zombie.hurt(rapid, .5f), "rapid damage registered with bypasses_cooldown");
    }
    check(Math.abs(health - zombie.getHealth() - .5f) < .001, "one domain delta, not all channels");
    check(zombie.invulnerableTime == timer, "domain does not lengthen original hurt window");
    health = zombie.getHealth();
    check(zombie.hurt(playerSource, 8), "next ordinary stronger hit");
    check(
        Math.abs(health - zombie.getHealth() - 2) < .001,
        "original lastHurt differential preserved");
    zombie.discard();
    if (!BladeTetraStoryCompat.isLoaded())
      LogUtils.getLogger().info("AKATSUKI_DOMAIN_NO_PROVIDER_PASS");
  }

  private static void check(boolean passed, String message) {
    if (!passed) throw new IllegalStateException(message);
  }

  private AkatsukiDomainValidation() {}
}
