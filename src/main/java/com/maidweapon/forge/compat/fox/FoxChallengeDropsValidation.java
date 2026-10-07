package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.event.FoxChallengeEvents;
import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import java.util.ArrayList;
import java.util.List;

/** Opt-in actual native dimension transfer of captured and already-spawned items. No inventory snapshots. */
final class FoxChallengeDropsValidation {
    static void run(ServerPlayer player) {
        var from = player.serverLevel();
        var point = FoxChallengeService.position(player);
        check(FoxChallengeService.enter(player, ItemStack.EMPTY, point), "drop fixture enters");
        var arena = player.serverLevel();
        var origin = FoxChallengeService.origin(player);
        ItemStack blade = com.maidweapon.forge.compat.BlackFoxSlashCompat.blade(arena);
        blade.getOrCreateTag().putString("DropFixture", "native-capability-preserved");
        var bladeBefore = blade.save(new CompoundTag());
        var drop = new ItemEntity(arena, player.getX(), player.getY(), player.getZ(), blade);
        drop.setThrower(player.getUUID()); drop.setTarget(player.getUUID());
        var saved = drop.saveWithoutId(new CompoundTag());
        saved.putShort("Age", (short) 5990); saved.putShort("PickupDelay", (short) 60);
        drop.load(saved);
        drop.lifespan = 12000;
        var id = drop.getUUID();
        var drops = new ArrayList<>(List.of(drop));
        // Captured death drops have not yet been added to the origin level.
        net.minecraftforge.common.ForgeHooks.onLivingDrops(player, player.damageSources().generic(), drops, 0, false);
        var returned = from.getEntity(id);
        check(drops.isEmpty() && drop.isRemoved() && returned instanceof ItemEntity, "native death event moves once before vanilla spawn");
        var item = (ItemEntity) returned;
        check(bladeBefore.equals(item.getItem().save(new CompoundTag())), "exact item NBT and SlashBlade capabilities retained");
        var returnedState = item.saveWithoutId(new CompoundTag());
        check(returnedState.getShort("Age") == 0 && returnedState.getShort("PickupDelay") == 10
                && item.lifespan == 12000 && returnedState.getUUID("Thrower").equals(player.getUUID())
                && returnedState.getUUID("Owner").equals(player.getUUID()), "timer restarts without losing ownership or mod lifespan");
        check(Math.abs(item.getX() - point.getDouble("X")) < .01
                && Math.abs(item.getZ() - point.getDouble("Z")) < .01, "drop arrives at the real entrance position");
        item.discard();
        var rules = arena.getGameRules();
        boolean keep = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        rules.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(true, player.getServer());
        var kept = new ItemStack(Items.NETHERITE_INGOT, 7);
        player.getInventory().setItem(0, kept);
        var noDrops = new ArrayList<ItemEntity>();
        FoxChallengeEvents.drops(new LivingDropsEvent(player, player.damageSources().generic(), noDrops, 0, false));
        check(noDrops.isEmpty() && player.getInventory().getItem(0) == kept && kept.getCount() == 7,
                "keepInventory deaths do not generate replacement drops or change the retained inventory");
        player.getInventory().clearContent();
        rules.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(keep, player.getServer());
        var captured = new ItemEntity(arena, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        var canceledDrops = new ArrayList<>(List.of(captured));
        var canceled = new LivingDropsEvent(player, player.damageSources().generic(), canceledDrops, 0, false);
        canceled.setCanceled(true); FoxChallengeEvents.drops(canceled);
        check(canceledDrops.size() == 1 && !captured.isRemoved(), "grave-mod canceled drops are not recreated");
        captured.discard();
        var old = new ItemEntity(arena, origin.getX() + .5, origin.getY() + 1, origin.getZ() + .5, new ItemStack(Items.EMERALD, 3));
        arena.addFreshEntity(old);
        var outside = new ItemEntity(arena, origin.getX() + FoxChallengeArena.SPACING, origin.getY() + 1,
                origin.getZ(), new ItemStack(Items.GOLD_INGOT));
        arena.addFreshEntity(outside);
        var oldId = old.getUUID();
        check(FoxChallengeService.leave(player) && from.getEntity(oldId) instanceof ItemEntity && old.isRemoved(),
                "return recovers pre-fix loose items from the private cell");
        check(!outside.isRemoved() && outside.level() == arena, "another cell is untouched");
        from.getEntity(oldId).discard(); outside.discard();
        var normal = new ItemEntity(from, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.IRON_INGOT));
        var normalDrops = new ArrayList<>(List.of(normal));
        FoxChallengeEvents.drops(new LivingDropsEvent(player, player.damageSources().generic(), normalDrops, 0, false));
        check(normalDrops.size() == 1 && !normal.isRemoved(), "ordinary deaths keep their normal drops");
        normal.discard();
        check(!from.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY), "no global keepInventory mutation");
        check(FoxChallengeDrops.startRecovery(player), "legacy recovery enters without White Fox and without Boss");
        check(BlackFoxEncounters.get(player) == null, "legacy recovery never starts a new fight");
        var leftover = new ItemEntity(player.serverLevel(), origin.getX() + .5, origin.getY() + 1,
                origin.getZ() + .5, new ItemStack(Items.DIAMOND, 2));
        player.serverLevel().addFreshEntity(leftover);
        var leftoverId = leftover.getUUID();
        ((net.minecraft.world.level.storage.ServerLevelData) player.getServer().overworld().getLevelData())
                .setGameTime(player.level().getGameTime() + 40);
        FoxChallengeDrops.tickRecovery(player);
        check(player.serverLevel() == from && from.getEntity(leftoverId) instanceof ItemEntity,
                "timed recovery brings existing drops back without copying inventory");
        from.getEntity(leftoverId).discard();
        LogUtils.getLogger().info("FOX_CHALLENGE_DROPS_PASS: real captured drops, native capabilities, age/ownership, canceled events, normal deaths and private-cell legacy recovery");
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
