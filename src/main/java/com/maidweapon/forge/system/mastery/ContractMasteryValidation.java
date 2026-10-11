package com.maidweapon.forge.system.mastery;

import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.contract.ContractChannelStorage;
import com.maidweapon.common.data.MaidWeaponData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in actual server fixture; use only an isolated disposable world, never a player's save. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractMasteryValidation {
    public static void registerAbilities() {
        if (!Boolean.getBoolean("contractblade.mastery.validation")) return;
        var ability = new ResourceLocation("mastery_fixture:example_ability");
        var basic = new ResourceLocation("mastery_fixture:basic"); var ultimate = new ResourceLocation("mastery_fixture:ultimate");
        ContractAbilityRegistry.register(ability, (player, item) -> item.hasTag() && item.getTag().getBoolean("FixtureAbilityEarned"),
                Set.of(basic, ultimate), Map.of(), new ContractAbilityDefinition(ability, List.of(
                        new ContractAbilityMilestone(1, Set.of(basic), Map.of()), new ContractAbilityMilestone(10, Set.of(ultimate), Map.of()))));
        var passive = new ResourceLocation("mastery_fixture:passive");
        ContractAbilityRegistry.register(passive, (player, item) -> true, Set.of(basic), Map.of(),
                new ContractAbilityDefinition(passive, List.of(new ContractAbilityMilestone(1, Set.of(basic), Map.of()))));
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.mastery.validation")) return;
        try {
            var level = event.getServer().overworld();
            var a = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.fromString("55a76f4d-dc80-4a92-b021-1cac05950001"), "MasteryA"));
            var b = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.fromString("55a76f4d-dc80-4a92-b021-1cac05950002"), "MasteryB"));
            if (Boolean.getBoolean("contractblade.mastery.resume")) {
                check(ContractMasteryService.getLevel(a) == 2 && ContractMasteryService.getProgress(a).getTotalKills() == 1,
                        "actual server restart restores UUID mastery");
                check(ContractMasteryService.getLevel(b) == 1, "restart retains player separation");
                com.mojang.logging.LogUtils.getLogger().info("CONTRACT_MASTERY_RESTART_PASS");
                return;
            }
            a.getInventory().clearContent(); b.getInventory().clearContent(); a.setHealth(20); b.setHealth(20);
            var admin = event.getServer().createCommandSourceStack();
            ContractMasteryService.setLevel(admin, a, 1); ContractMasteryService.setLevel(admin, b, 1);
            var sword = carrier(a, 278, 141); a.getInventory().setItem(0, sword); a.getInventory().selected = 0;
            sword.getTag().getCompound("MaidData").putInt("Level", 5);
            ContractMasteryService.observe(a, sword); check(ContractMasteryService.getLevel(a) == 5, "single old sword imported");
            var high = carrier(a, 120, 100); high.getTag().getCompound("MaidData").putInt("Level", 10);
            var relation = high.getTag().copy();
            ContractMasteryService.observe(b, high); check(ContractMasteryService.getLevel(b) == 1, "foreign owner cannot import");
            ContractMasteryService.observe(a, high); ContractMasteryService.observe(a, high);
            check(ContractMasteryService.getLevel(a) == 10 && high.getTag().equals(relation), "late higher sword and repeated migration leave maid state intact");
            var fresh = carrier(a, 300, 80);
            check(ContractMasteryService.getLevel(a, fresh) == 10 && !fresh.getTag().getCompound("MaidData").contains("Level"), "new carrier shares mastery without item level");
            var archived = ContractChannelStorage.captureContract(high); ContractChannelStorage.restoreContract(fresh, archived);
            check(ContractCarrierData.getMaidData(fresh).getFavorability() == 120 && ContractCarrierData.getMaidData(sword).getFavorability() == 278,
                    "channel transfer preserves distinct affection");
            var serialized = ContractMasterySavedData.get(event.getServer()).save(new CompoundTag());
            var restored = ContractMasterySavedData.load(serialized);
            check(restored.read(a.getUUID()).getLevel() == 10 && restored.read(b.getUUID()).getLevel() == 1, "world record roundtrip and player separation");
            var forged = carrier(a, 0, 40); forged.getTag().getCompound("MaidData").putInt("Level", 10);
            a.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var packet = new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, forged);
            packet.handle(a.connection);
            check(!ContractMasteryMigration.validLegacy(packet.getItem().getTag()), "actual creative packet handler cannot invent a legacy source");
            a.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            a.getInventory().setItem(0, sword);
            var ability = new ResourceLocation("mastery_fixture:example_ability"); var basic = new ResourceLocation("mastery_fixture:basic"); var ultimate = new ResourceLocation("mastery_fixture:ultimate");
            check(ContractAbilityProgressionService.query(a, sword, ability).status() == ContractAbilityProgressionService.Status.DENIED, "mastery does not grant ability entitlement");
            sword.getOrCreateTag().putBoolean("FixtureAbilityEarned", true);
            check(ContractAbilityProgressionService.query(a, sword, ability).capabilities().has(ultimate), "earned test ability resolves global lv10");
            var passive = new ResourceLocation("mastery_fixture:passive");
            check(ContractAbilityProgressionService.query(a, sword, passive).capabilities().has(basic)
                    && ContractAbilityProgressionService.query(a, sword, ability).capabilities().has(ultimate),
                    "one carrier exposes two independent abilities");
            sword.getOrCreateTag().putBoolean("FixtureAbilityEarned", false);
            check(ContractAbilityProgressionService.query(a, sword, ability).status() == ContractAbilityProgressionService.Status.DENIED
                    && ContractAbilityProgressionService.query(a, sword, passive).capabilities().has(basic),
                    "ability entitlements are independent on the same carrier");
            sword.getOrCreateTag().putBoolean("FixtureAbilityEarned", true);
            ContractMasteryService.setLevel(admin, a, 1);
            check(!ContractAbilityProgressionService.query(a, sword, ability).capabilities().has(ultimate), "admin level update immediately changes feature query");
            var zombie = new net.minecraft.world.entity.monster.Zombie(level); zombie.setPos(a.position()); level.addFreshEntity(zombie);
            zombie.hurt(level.damageSources().playerAttack(a), 100);
            check(ContractMasteryService.getProgress(a).getTotalKills() == 1 && ContractMasteryService.getLevel(a) == 2, "actual Forge lethal attack settles once");
            com.maidweapon.forge.event.ContractCombatEventHandler.onLivingDeath(new net.minecraftforge.event.entity.living.LivingDeathEvent(zombie, level.damageSources().playerAttack(a)));
            check(ContractMasteryService.getProgress(a).getTotalKills() == 1, "duplicate death event rejected");
            var pending = new net.minecraft.world.entity.monster.Zombie(level);
            ContractCombatCredits.record(pending, level.damageSources().playerAttack(a));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(a, level.damageSources().generic()));
            check(ContractCombatCredits.settle(pending, level.damageSources().playerAttack(a)) == null && ContractMasteryService.getLevel(a) == 2,
                    "death invalidates pending challenge without losing earned mastery");
            var another = carrier(a, 300, 80); a.getInventory().setItem(1, another);
            ContractCombatCredits.record(pending, level.damageSources().playerAttack(a)); a.getInventory().selected = 1;
            check(ContractCombatCredits.settle(pending, level.damageSources().playerAttack(a)) == null, "hand switch cannot borrow old hit credit");
            var profile = new com.mojang.authlib.GameProfile(a.getUUID(), "MasteryRecreated");
            var recreated = new net.minecraftforge.common.util.FakePlayer(level, profile);
            check(ContractMasteryService.getLevel(recreated) == 2, "recreated player entity retains UUID mastery");
            var otherDimension = new net.minecraftforge.common.util.FakePlayer(event.getServer().getLevel(net.minecraft.world.level.Level.NETHER), profile);
            check(ContractMasteryService.getLevel(otherDimension) == 2, "dimension context reads same overworld authority");
            check(ContractCarrierData.getMaidData(sword).getFavorability() == 278, "growth never changes affection");
            com.mojang.logging.LogUtils.getLogger().info("CONTRACT_MASTERY_VALIDATION_PASS");
        } catch (Throwable failure) {
            com.mojang.logging.LogUtils.getLogger().error("CONTRACT_MASTERY_VALIDATION_FAIL", failure);
        } finally { event.getServer().halt(false); }
    }
    private static ItemStack carrier(net.minecraft.server.level.ServerPlayer owner, int affection, int resonance) {
        var item = new ItemStack(Items.IRON_SWORD); var data = new MaidWeaponData("Fixture");
        data.setFavorability(affection); data.setResonance(resonance); ContractCarrierData.setMaidData(item, data);
        ContractCarrierData.setOwner(item, owner); ContractCarrierData.setBoundMaidUUID(item, UUID.randomUUID().toString()); ContractCarrierData.ensureBindingId(item);
        return item;
    }
    private static void check(boolean pass, String label) {
        if (!pass) throw new IllegalStateException(label); com.mojang.logging.LogUtils.getLogger().info("[Mastery fixture] {}", label);
    }
    private ContractMasteryValidation() { }
}
