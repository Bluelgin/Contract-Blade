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
    var owner =
        FakePlayerFactory.get(
            level,
            new GameProfile(
                UUID.fromString("277e986c-c78b-42a1-8e4a-a6ac5d4308b1"), "DomainFixture"));
    if (Boolean.getBoolean("contractblade.akatsukiDomain.resume")) {
      check(
          AkatsukiRescueSavedData.get(server).readyAt(owner.getUUID()) > level.getGameTime(),
          "real stop/restart retains rescue deadline");
      LogUtils.getLogger().info("AKATSUKI_RESCUE_RESTART_PASS");
      return;
    }
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
    else provider(server, owner);
  }

  private static void provider(
      net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerPlayer owner)
      throws Exception {
    var level = server.overworld();
    var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
    var blade = prepareBlade(owner);
    var awakening = Class.forName("dev.bladetetra.easteregg.AkatsukiAwakening");
    for (int lv = 1; lv <= 10; lv++) {
      ContractMasteryService.setLevel(server.createCommandSourceStack(), owner, lv);
      var result = ContractAbilityProgressionService.query(owner, blade, AkatsukiDomainGrowth.ID);
      check(
          result.status() == ContractAbilityProgressionService.Status.READY
              && result.capabilities().features().size() == lv,
          "real authorized level " + lv);
    }
    String maid = ContractCarrierData.getBoundMaidUUID(blade);
    clock.setGameTime(
        level.getGameTime() + 11); // Respect the previous fixture's real 10-tick summon cooldown.
    ContractCompanionService.toggle(owner);
    check(level.getEntity(UUID.fromString(maid)) != null, "real provider manifestation succeeds");
    check(
        !AkatsukiDomainAuthorization.allows(owner, blade),
        "real provider manifestation suspends growth");
    check(InfusedMaidDeploymentSystem.forceRecall(owner, maid, 0), "real provider recall");
    check(AkatsukiDomainAuthorization.allows(owner, blade), "real provider recall restores growth");
    var target = EntityType.ZOMBIE.create(level);
    target.moveTo(2, 150, 2, 0, 0);
    target.getAttribute(Attributes.ARMOR).setBaseValue(0);
    check(level.addFreshEntity(target), "domain target actually loaded");
    AkatsukiDomainService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, owner));
    var field = AkatsukiDomainService.class.getDeclaredField("SESSIONS");
    field.setAccessible(true);
    var session = (AkatsukiDomainSession) ((java.util.Map<?, ?>) field.get(null)).get(owner);
    check(
        session != null && session.active(), "held authorized blade opens domain without any hit");
    target.invulnerableTime = 0;
    target.hurt(owner.damageSources().playerAttack(owner), 1);
    float hp = target.getHealth();
    AkatsukiDomainDamage.pulse(owner, target, session, true);
    check(
        target.getHealth() < hp && hp - target.getHealth() <= .501f,
        "actual isolated bounded domain damage through provider guard");
    float after = target.getHealth();
    AkatsukiDomainDamage.pulse(owner, target, session, true);
    check(target.getHealth() == after, "second same-tick candidate cannot claim extra budget");
    owner.setHealth(9);
    AkatsukiDomainService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, owner));
    check(session.lifecycle.rampage(), "below half automatically rampages");
    owner.setHealth(10.8f);
    AkatsukiDomainService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, owner));
    check(session.lifecycle.rampage(), "hysteresis retains rampage at 54 percent");
    owner.setHealth(11);
    AkatsukiDomainService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, owner));
    check(!session.lifecycle.rampage(), "55 percent exits rampage");
    owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    owner.getInventory().offhand.set(0, new ItemStack(Items.TOTEM_OF_UNDYING));
    owner.invulnerableTime = 0;
    owner.hurt(owner.damageSources().mobAttack(target), 30);
    check(
        owner.isAlive() && Math.abs(owner.getHealth() - 5) < .001,
        "real lethal hit restores 25 percent");
    check(!owner.getOffhandItem().isEmpty(), "rescue does not consume totem");
    check(!session.attacking(), "collapse pauses all domain damage");
    hp = target.getHealth();
    AkatsukiDomainDamage.pulse(owner, target, session, true);
    check(target.getHealth() == hp, "no collapse damage");
    var receipt = AkatsukiRescueSavedData.get(server);
    var restored = AkatsukiRescueSavedData.load(receipt.save(new net.minecraft.nbt.CompoundTag()));
    check(
        !restored.claim(owner.getUUID(), level.getGameTime(), 3600),
        "cooldown survives serialized reload");
    clock.setGameTime(level.getGameTime() + 16);
    AkatsukiDomainService.tick(
        new net.minecraftforge.event.TickEvent.PlayerTickEvent(
            net.minecraftforge.event.TickEvent.Phase.END, owner));
    check(session.attacking() && session.lifecycle.rampage(), "rescue reignites in rampage");
    var second =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            owner, owner.damageSources().mobAttack(target), 30);
    AkatsukiDomainService.rescueDamage(second);
    check(!second.isCanceled() && owner.getHealth() == 5, "cooldown prevents second fake success");
    var forced =
        new net.minecraftforge.event.entity.living.LivingDamageEvent(
            owner, owner.damageSources().genericKill(), 1000);
    AkatsukiDomainService.rescueDamage(forced);
    check(!forced.isCanceled(), "forced kill not rescued");
    clock.setGameTime(level.getGameTime() + 40);
    owner.invulnerableTime = 0;
    owner.hurt(owner.damageSources().mobAttack(target), 30);
    check(
        owner.isAlive() && owner.getOffhandItem().isEmpty(),
        "cooldown allows vanilla totem fallback exactly once");
    owner.setHealth(20);
    server.overworld().getDataStorage().save();
    awakening.getMethod("resetForDebug", ItemStack.class).invoke(null, blade);
    check(
        !AkatsukiDomainAuthorization.allows(owner, blade),
        "loss of actual awakening suspends domain");
    AkatsukiDomainService.logout(
        new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner));
    check(
        !((java.util.Map<?, ?>) field.get(null)).containsKey(owner),
        "logout clears live domain session");
    var proofs = AkatsukiDomainAuthorization.class.getDeclaredField("PROOFS");
    proofs.setAccessible(true);
    check(
        !((java.util.Map<?, ?>) proofs.get(null)).containsKey(blade),
        "logout clears held and dropped carrier proofs without retaining player objects");
    target.discard();
    LogUtils.getLogger().info("AKATSUKI_DOMAIN_PROVIDER_PASS");
  }

  static ItemStack prepareBlade(net.minecraft.server.level.ServerPlayer owner) throws Exception {
    var item =
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse("blade_tetra:modular_slashblade"));
    var blade = (ItemStack) item.getClass().getMethod("createDefaultStack").invoke(item);
    Class<?> module = Class.forName("dev.bladetetra.item.ModularSlashBladeItem");
    String slot = (String) module.getField("INSCRIPTION_SLOT").get(null);
    String inscription = (String) module.getField("AWAKENED_SOUL_INSCRIPTION_MODULE").get(null);
    blade.getOrCreateTag().putString(slot, inscription);
    blade
        .getOrCreateTag()
        .putString(inscription + "_material", "awakened_soul_inscription/blood_crystal");
    Class<?> skin = Class.forName("dev.bladetetra.visual.SayaPresetSkin");
    Object akatsuki =
        java.util.Arrays.stream(skin.getEnumConstants())
            .filter(v -> ((Enum<?>) v).name().equals("AKATSUKI"))
            .findFirst()
            .orElseThrow();
    skin.getMethod("apply", ItemStack.class, skin).invoke(null, blade, akatsuki);
    var awakening = Class.forName("dev.bladetetra.easteregg.AkatsukiAwakening");
    owner.getInventory().setItem(0, blade);
    check(!BladeTetraStoryCompat.isAkatsuki(blade), "unawakened real provider blade rejected");
    check(
        (Boolean) awakening.getMethod("unlockForDebug", ItemStack.class).invoke(null, blade),
        "real provider public debug awakening");
    Class.forName("dev.bladetetra.compat.ContractBladeCompat")
        .getMethod(
            "syncAkatsuki",
            net.minecraft.world.entity.player.Player.class,
            ItemStack.class,
            boolean.class)
        .invoke(null, owner, blade, true);
    check(BladeTetraStoryCompat.isAkatsuki(blade), "real awakened modular blade identity");
    check(
        AkatsukiDomainAuthorization.allows(owner, blade),
        "provider and full server-origin residency authorized");
    return blade;
  }

  private static void check(boolean passed, String message) {
    if (!passed) throw new IllegalStateException(message);
  }

  private AkatsukiDomainValidation() {}
}
