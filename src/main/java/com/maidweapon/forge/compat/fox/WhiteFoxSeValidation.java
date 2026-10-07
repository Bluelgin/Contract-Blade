package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.api.BlackFoxEncounterApi;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.FoxSpiritTransferService;
import com.maidweapon.forge.system.fox.WhiteFoxPurifyingEdge;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Explicit opt-in fixture; uses real SlashBlade capabilities and Forge's damage event bus. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class WhiteFoxSeValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.whiteFoxSe.validation")) return;
        try {
            run(event.getServer());
            LogUtils.getLogger().info("WHITE_FOX_SE_PASS");
        } catch (Throwable error) {
            LogUtils.getLogger().error("WHITE_FOX_SE_FAIL", error);
        } finally {
            BlackFoxEncounterApi.setResolver((source, target) -> null);
            event.getServer().halt(false);
        }
    }

    private static void run(net.minecraft.server.MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("568b0661-5843-4421-9fd2-29b9a1c89594"), "WhiteFoxSeFixture"));
        player.getInventory().clearContent();
        var boss = EntityType.ZOMBIE.create(level);
        var other = EntityType.ZOMBIE.create(level);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        boss.setHealth(1000);
        var source = level.damageSources().playerAttack(player);
        ItemStack ordinary = new ItemStack(Items.IRON_SWORD);
        WhiteFoxSpecialEffectCompat.ensureEffect(ordinary);
        check(!WhiteFoxSpecialEffectCompat.hasEffect(ordinary), "ordinary items are inert");
        if (!SlashBladeCompat.isLoaded()) {
            var hurt = new LivingHurtEvent(boss, source, 8);
            MinecraftForge.EVENT_BUS.post(hurt);
            check(hurt.getAmount() == 8, "absent SlashBlade remains inert");
            return;
        }
        var template = level.getStructureManager().get(ResourceLocation.parse("maid_weapon:shinkitsu_shrine"))
                .orElseThrow().save(new CompoundTag());
        var itemTag = template.getList("entities", 10).getCompound(0).getCompound("nbt").getCompound("Item");
        ItemStack white = ItemStack.of(itemTag);
        FoxSpiritTransferService.claimOffering(player, white);
        check(WhiteFoxSpecialEffectCompat.hasEffect(white), "native registry accepts SE on existing offering");
        Object effectRegistry = ((java.util.function.Supplier<?>) Class.forName(
                "mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry").getField("REGISTRY").get(null)).get();
        Object effect = effectRegistry.getClass().getMethod("getValue", ResourceLocation.class)
                .invoke(effectRegistry, WhiteFoxSpecialEffectCompat.ID);
        check(effect != null && !(Boolean) effect.getClass().getMethod("isCopiable").invoke(effect)
                && !(Boolean) effect.getClass().getMethod("isRemovable").invoke(effect), "SE is non-copyable and non-removable");
        check(WhiteFoxSpecialEffectCompat.hasEffect(ItemStack.of(white.save(new CompoundTag()))),
                "SE survives serialization");
        var donor = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("slashblade:slashblade")));
        Object state = state(donor);
        state.getClass().getMethod("setBaseAttackModifier", float.class).invoke(state, 20f);
        state.getClass().getMethod("setRefine", int.class).invoke(state, 10);
        check(close(WhiteFoxSpecialEffectCompat.strength(donor), 20 * (2 - 1 / 1.5)), "native refine formula");
        player.getInventory().setItem(1, donor);
        var donorBefore = donor.save(new CompoundTag()).copy();
        var whiteBefore = white.save(new CompoundTag()).copy();
        var inactive = new LivingHurtEvent(boss, source, 8);
        MinecraftForge.EVENT_BUS.post(inactive);
        check(inactive.getAmount() == 8, "default encounter hook disabled");
        BlackFoxEncounterApi.setResolver((hitSource, target) -> target == boss
                ? new BlackFoxEncounterApi.HitContext(player, white) : null);
        var hurt = new LivingHurtEvent(boss, source, 8);
        MinecraftForge.EVENT_BUS.post(hurt);
        check(close(hurt.getAmount(), 8 + Math.max(0, WhiteFoxSpecialEffectCompat.strength(donor)
                - WhiteFoxSpecialEffectCompat.strength(white))), "temporary strength supplement");
        var unrelated = new LivingHurtEvent(other, source, 8);
        MinecraftForge.EVENT_BUS.post(unrelated);
        check(unrelated.getAmount() == 8, "unrelated target unchanged");
        var zero = new LivingDamageEvent(boss, source, 0);
        MinecraftForge.EVENT_BUS.post(zero);
        check(zero.getAmount() == 0, "blocked/zero hit cannot trigger purification");
        var canceled = new LivingDamageEvent(boss, source, 8);
        canceled.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(canceled);
        check(canceled.getAmount() == 8, "canceled hit cannot trigger purification");
        var first = new LivingDamageEvent(boss, source, 8);
        MinecraftForge.EVENT_BUS.post(first);
        check(first.getAmount() == 18, "one percent of target maximum health");
        var repeat = new LivingDamageEvent(boss, level.damageSources().mobAttack(other), 8);
        MinecraftForge.EVENT_BUS.post(repeat);
        check(repeat.getAmount() == 8, "different attacker shares target cooldown");
        server.getWorldData().overworldData().setGameTime(level.getGameTime() + 20);
        var next = new LivingDamageEvent(boss, source, 8);
        MinecraftForge.EVENT_BUS.post(next);
        check(next.getAmount() == 18, "purification refreshes after twenty ticks");
        check(donorBefore.equals(donor.save(new CompoundTag())) && whiteBefore.equals(white.save(new CompoundTag())),
                "combat never rewrites donor or White Fox NBT/capabilities");
        white.getOrCreateTag().remove(FoxSpiritState.ROOT);
        white.getOrCreateTag().putBoolean(FoxSpiritState.VACANT, true);
        check(WhiteFoxSpecialEffectCompat.hasEffect(white), "SE stays on sword after soul moves out");
        donor.getOrCreateTag().putBoolean("MaidWeaponPhantomCopy", true);
        check(WhiteFoxSpecialEffectCompat.strength(donor) == 0, "projection cannot donate");
        donor.getTag().remove("MaidWeaponPhantomCopy");
        state.getClass().getMethod("setBroken", boolean.class).invoke(state, true);
        check(WhiteFoxSpecialEffectCompat.strength(donor) == 0, "broken blade cannot donate");
        state.getClass().getMethod("setBroken", boolean.class).invoke(state, false);
        state.getClass().getMethod("setSealed", boolean.class).invoke(state, true);
        check(WhiteFoxSpecialEffectCompat.strength(donor) == 0, "sealed blade cannot donate");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, white);
        server.getWorldData().overworldData().setGameTime(level.getGameTime() + 20);
        check(WhiteFoxPurifyingEdge.strongest(player) == 0, "inventory cache refreshes and shrine sword cannot donate to itself");
        var weaker = new LivingHurtEvent(boss, source, 8);
        MinecraftForge.EVENT_BUS.post(weaker);
        check(weaker.getAmount() == 8, "no stronger donor never lowers White Fox damage");
        player.getInventory().clearContent();
    }

    private static Object state(ItemStack stack) throws Exception {
        var capability = (Capability<?>) Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade")
                .getField("BLADESTATE").get(null);
        return stack.getCapability(capability).resolve().orElseThrow();
    }

    private static boolean close(double first, double second) { return Math.abs(first - second) < 0.0001; }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        LogUtils.getLogger().info("WHITE_FOX_SE_CHECK {}", message);
    }

    private WhiteFoxSeValidation() { }
}
