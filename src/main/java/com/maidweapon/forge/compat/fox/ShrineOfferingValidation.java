package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.system.fox.ShrineOfferingService;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Real native stand interactions and capability counts, in the isolated opt-in fixture only. */
public final class ShrineOfferingValidation {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void run(ItemFrame stand, ServerPlayer player) throws Exception {
        player.getInventory().clearContent();
        player.setPos(stand.getX(), stand.getY(), stand.getZ() + 1);
        var offered = stand.getItem().save(new CompoundTag());
        var cake = new ItemStack(Items.CAKE, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, cake);
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(cake.getCount() == 2 && !stand.getPersistentData().hasUUID(ShrineOfferingService.ACCEPTED_BY),
                "insufficient kills preserve cake");
        var notice = player.getPersistentData().copy();
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(notice.equals(player.getPersistentData()), "repeated clicks do not repeat dialogue receipt");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(stand.getItem().save(new CompoundTag()).equals(offered), "empty hand cannot take locked blade");
        var itemClass = Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade");
        Capability capability = (Capability) itemClass.getField("BLADESTATE").get(null);
        ItemStack first = stand.getItem().copy();
        ItemStack second = stand.getItem().copy();
        Object state = first.getCapability(capability).resolve().orElseThrow();
        state.getClass().getMethod("setKillCount", int.class).invoke(state, 300);
        Object secondState = second.getCapability(capability).resolve().orElseThrow();
        secondState.getClass().getMethod("setKillCount", int.class).invoke(secondState, 219);
        player.getInventory().setItem(1, first);
        player.setItemInHand(InteractionHand.OFF_HAND, second);
        check(ShrineOfferingService.totalKills(player) == 519, "inventory and offhand counts sum without double counting");
        player.setItemInHand(InteractionHand.MAIN_HAND, cake);
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(cake.getCount() == 2 && !stand.getPersistentData().hasUUID(ShrineOfferingService.ACCEPTED_BY),
                "519 rejects held cake offering");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        secondState.getClass().getMethod("setKillCount", int.class).invoke(secondState, 220);
        check(ShrineOfferingService.totalKills(player) == 520, "520 reaches threshold across two blades");
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(ShrineOfferingService.isProtected(stand), "kills alone do not unlock blade");
        player.setItemInHand(InteractionHand.MAIN_HAND, first.copy());
        var held = player.getMainHandItem().save(new CompoundTag());
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(player.getMainHandItem().save(new CompoundTag()).equals(held)
                && stand.getItem().save(new CompoundTag()).equals(offered), "native sword swap blocked without changing either blade");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(!stand.hurt(stand.damageSources().playerAttack(player), 1)
                        && stand.getItem().save(new CompoundTag()).equals(offered),
                "native stand attack cannot drop offering");
        check(!stand.hurt(stand.damageSources().explosion(null, null), 10)
                        && stand.getItem().save(new CompoundTag()).equals(offered),
                "explosion cannot destroy or drop offering");
        player.setItemInHand(InteractionHand.MAIN_HAND, cake);
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(stand.getPersistentData().getUUID(ShrineOfferingService.ACCEPTED_BY).equals(player.getUUID())
                        && cake.getCount() == 1 && stand.getItem().save(new CompoundTag()).equals(offered),
                "eligible offering consumes exactly one cake");
        stand.interact(player, InteractionHand.MAIN_HAND);
        check(cake.getCount() == 1 && stand.getItem().save(new CompoundTag()).equals(offered),
                "repeated cake clicks do not consume more cake or rotate the offering");
        ItemFrame restored = (ItemFrame) stand.getType().create(player.serverLevel());
        restored.load(stand.saveWithoutId(new CompoundTag()));
        check(restored.getPersistentData().getUUID(ShrineOfferingService.ACCEPTED_BY).equals(player.getUUID()),
                "offering reservation survives entity save and reload");
        var stranger = FakePlayerFactory.get(player.serverLevel(), new GameProfile(
                UUID.fromString("7e6043d2-d2d0-4372-bf0c-fb207813840b"), "OfferingStranger"));
        stranger.getInventory().clearContent();
        stranger.getInventory().setItem(1, first.copy());
        stranger.setItemInHand(InteractionHand.OFF_HAND, second.copy());
        restored.interact(stranger, InteractionHand.MAIN_HAND);
        check(ShrineOfferingService.isProtected(restored) && stranger.getMainHandItem().isEmpty(),
                "another eligible player cannot steal reserved blade");
        player.setItemInHand(InteractionHand.MAIN_HAND, first.copy());
        restored.interact(player, InteractionHand.MAIN_HAND);
        check(ShrineOfferingService.isProtected(restored), "swap remains blocked even after offering");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        restored.interact(player, InteractionHand.MAIN_HAND);
        check(restored.getItem().isEmpty() && player.getMainHandItem().save(new CompoundTag()).equals(offered)
                        && !ShrineOfferingService.isProtected(restored)
                        && restored.getPersistentData().getBoolean(ShrineOfferingService.TAKEN),
                "native empty-hand pickup preserves the complete blade and releases stand");
        restored.setItem(ItemStack.of(offered));
        restored.removeTag(ShrineOfferingService.STAND_TAG);
        player.setItemInHand(InteractionHand.MAIN_HAND, first.copy());
        var replacement = player.getMainHandItem().save(new CompoundTag());
        restored.interact(player, InteractionHand.MAIN_HAND);
        check(restored.getItem().save(new CompoundTag()).equals(replacement)
                        && player.getMainHandItem().save(new CompoundTag()).equals(offered),
                "ordinary stand retains native blade exchange");
        player.getInventory().clearContent();
        stranger.getInventory().clearContent();
        restored.discard();
        LogUtils.getLogger().info("SHRINE_OFFERING_NATIVE_PASS");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new IllegalStateException(message);
        LogUtils.getLogger().info("Offering fixture: {}", message);
    }

    private ShrineOfferingValidation() { }
}
