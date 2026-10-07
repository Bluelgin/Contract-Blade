package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.deployment.ContractCarrierLossService;
import com.maidweapon.forge.system.deployment.ContractCarrierLossJournal;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import java.util.Map;
import java.util.UUID;

/** Opt-in private-world regression: 32 losses, live A, present D, storage and server budgets. */
public final class ContractCarrierLossValidation {
    @SuppressWarnings("unchecked")
    public static void run(ServerLevel level) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("contractblade.home.nativeValidation")
                && !Boolean.getBoolean("contractblade.rebirth.validation"))
            throw new IllegalStateException("carrier-loss fixture disabled");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LossFixture"));
        var spawn = level.getSharedSpawnPos();
        player.setPos(spawn.getX() + .5, Math.max(80, spawn.getY()) + 3, spawn.getZ() + .5);
        player.getInventory().selected = 0;
        ItemStack[] carriers = new ItemStack[34];
        UUID[] maids = new UUID[34];
        var journal = ContractCarrierLossJournal.get(level.getServer());
        var pendingField = ContractCarrierLossJournal.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        var pending = (Map<UUID, Map<String, Object>>) pendingField.get(journal);
        try { validateStorage(); }
        catch (java.io.IOException failure) { throw new IllegalStateException("storage fixture failed", failure); }
        try {
            for (int i = 0; i < 34; i++) {
                Entity maid = (Entity) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
                maid.moveTo(player.getX(), player.getY(), player.getZ());
                TlmEntityAdapter.tame(maid, player);
                check(level.addFreshEntity(maid), "spawn fixture maid");
                maids[i] = maid.getUUID();
                carriers[i] = new ItemStack(Items.IRON_SWORD);
                player.getInventory().setItem(i, carriers[i]);
                check(ContractLifecycleService.capture(player, maid, carriers[i], false), "capture fixture maid");
            }
            check(InfusedMaidDeploymentSystem.manifestRequested(player, carriers[0]), "manifest A");
            check(ContractWeaponLocator.indexByBinding(player).size() == 34, "full inventory indexed without decoding payloads");
            var activeMaid = level.getEntity(maids[0]);
            var original = carriers[0].getTag().copy();
            carriers[0].getOrCreateTag().putByteArray("DormantArchive", new byte[
                    com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_STORED_BYTES.get() + 1]);
            var oversized = carriers[0].getTag().copy();
            check(!ContractLifecycleService.capture(player, activeMaid, carriers[0], false), "oversized capture rejected");
            check(!activeMaid.isRemoved() && oversized.equals(carriers[0].getTag()), "failed capture preserves live maid and weapon");
            carriers[0].setTag(original);
            var accept = com.maidweapon.forge.network.ContractCompanionNetwork.class.getDeclaredMethod(
                    "accept", net.minecraft.server.level.ServerPlayer.class);
            accept.setAccessible(true);
            check((boolean) accept.invoke(null, player) && !(boolean) accept.invoke(null, player), "duplicate call rate gate");
            var retained = carriers[33].copy();
            var duplicate = carriers[1].copy();
            for (int i = 1; i < 34; i++) {
                carriers[i].setDamageValue(carriers[i].getMaxDamage() - 1);
                carriers[i].hurtAndBreak(2, player, ignored -> {});
                check(carriers[i].isEmpty(), "actual durability consumption");
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
            // A retained/replaced carrier returns before delayed rescue and cancels it.
            carriers[33] = retained;
            player.getInventory().setItem(33, retained);
            net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, duplicate, null);
            check(pending.get(player.getUUID()).size() == 33, "duplicate events coalesced");
            check(journal.save(new net.minecraft.nbt.CompoundTag()).equals(
                    ContractCarrierLossJournal.load(journal.save(new net.minecraft.nbt.CompoundTag()))
                            .save(new net.minecraft.nbt.CompoundTag())), "pending snapshots survive save/reload");
            check(ContractWeaponLocator.indexByBinding(player).containsValue(carriers[33]), "short-lived index finds D");
            // Advance only candidate timestamps, not the shared world's clock.
            var candidates = pending.get(player.getUUID());
            for (var entry : candidates.entrySet()) {
                Object task = entry.getValue();
                var candidateField = task.getClass().getDeclaredField("candidate");
                candidateField.setAccessible(true);
                Object record = candidateField.get(task);
                var type = record.getClass();
                var ctor = type.getDeclaredConstructor(String.class, String.class, ItemStack.class, long.class);
                ctor.setAccessible(true);
                var maidId = type.getDeclaredMethod("maidId"); maidId.setAccessible(true);
                var snapshot = type.getDeclaredMethod("snapshot"); snapshot.setAccessible(true);
                candidateField.set(task, ctor.newInstance(maidId.invoke(record), entry.getKey(), snapshot.invoke(record),
                        level.getServer().overworld().getGameTime() - 45));
            }
            player.tickCount = 5;
            InfusedMaidDeploymentSystem.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            check(pending.get(player.getUUID()).size() == 31, "only two real restorations per player");
            int completed = 2;
            var budgetField = ContractCarrierLossService.class.getDeclaredField("BUDGETS");
            budgetField.setAccessible(true);
            var budgets = (Map<?, ?>) budgetField.get(null);
            while (pending.containsKey(player.getUUID())) {
                budgets.clear(); // Simulate the next budget tick without altering world time.
                InfusedMaidDeploymentSystem.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                completed += 2;
                check(completed <= 32, "batch recovery makes forward progress");
            }
            for (int i = 1; i < 33; i++) {
                Entity released = level.getEntity(maids[i]);
                check(released != null && !released.isRemoved(), "B/C released while A remains active");
                check(!released.getPersistentData().contains(ContractMaidKeys.ENTITY_BINDING_ID), "lost binding detached");
                check(released.getPersistentData().contains(ContractMaidKeys.EMERGENCY_FILM_PROGRESS), "growth preserved");
            }
            check(level.getEntity(maids[0]) != null, "A stays alive");
            check(MaidInfusion.containsMaid(carriers[33]) && level.getEntity(maids[33]) == null,
                    "present D is not falsely released");
            check(!pending.containsKey(player.getUUID()), "processed losses removed independently");
            check(InfusedMaidDeploymentSystem.recallRequested(player, carriers[0]), "A still recalls normally");
            validateServerBudget(level, pending);
            System.out.println("MULTI_CARRIER_LOSS_VALIDATION_PASSED");
        } finally {
            pending.remove(player.getUUID());
            journal.setDirty();
            InfusedMaidDeploymentSystem.onLogout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            for (UUID id : maids) {
                Entity maid = id == null ? null : level.getEntity(id);
                if (maid != null) maid.discard();
            }
            player.getInventory().clearContent();
        }
    }

    private static void validateStorage() throws java.io.IOException {
        var normal = new net.minecraft.nbt.CompoundTag();
        normal.putString("id", "touhou_little_maid:maid");
        var wide = new net.minecraft.nbt.CompoundTag();
        for (int i = 0; i < 5000; i++) wide.putInt("Key" + i, i);
        check(com.maidweapon.forge.system.ContractNbtGuard.depth(wide) == 2, "ordinary wide data is not mistaken for deep nesting");
        var stored = new net.minecraft.nbt.CompoundTag();
        com.maidweapon.forge.system.MaidEntityDataCodec.write(stored, normal);
        check(normal.equals(com.maidweapon.forge.system.MaidEntityDataCodec.read(stored)), "codec roundtrip");
        stored.putByteArray("DormantArchive", new byte[
                com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_STORED_BYTES.get() + 1]);
        var before = stored.copy();
        try {
            com.maidweapon.forge.system.MaidEntityDataCodec.write(stored, normal);
            throw new IllegalStateException("oversized complete contract accepted");
        } catch (java.io.IOException expected) {
            check(before.equals(stored), "rejected write is atomic including dormant archives");
        }
        var deep = new net.minecraft.nbt.CompoundTag();
        var cursor = deep;
        for (int i = 0; i < 140; i++) {
            var child = new net.minecraft.nbt.CompoundTag();
            cursor.put("Nested", child); cursor = child;
        }
        // Bypass our encoder to test hostile compressed input, not merely rejected writes.
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.writeCompressed(deep, bytes);
        var compressed = bytes.toByteArray();
        var crc = new java.util.zip.CRC32(); crc.update(compressed);
        var forged = new net.minecraft.nbt.CompoundTag();
        forged.putByteArray("MaidEntityDataCompressed", compressed);
        forged.putInt("MaidEntityDataFormat", 1);
        forged.putInt("MaidEntityDataUncompressedSize", (int) com.maidweapon.forge.system.ContractNbtGuard.serializedSize(deep));
        forged.putLong("MaidEntityDataChecksum", crc.getValue());
        try {
            com.maidweapon.forge.system.MaidEntityDataCodec.read(forged);
            throw new IllegalStateException("over-deep compressed data accepted");
        } catch (java.io.IOException expected) { }
        System.out.println("CONTRACT_STORAGE_LIMIT_VALIDATION_PASSED");
    }

    @SuppressWarnings("unchecked")
    private static void validateServerBudget(ServerLevel level, Map<UUID, Map<String, Object>> pending)
            throws ReflectiveOperationException {
        var budgetField = ContractCarrierLossService.class.getDeclaredField("BUDGETS");
        budgetField.setAccessible(true);
        var budgets = (Map<?, ?>) budgetField.get(null);
        budgets.clear();
        var players = new net.minecraft.server.level.ServerPlayer[5];
        try {
            for (int i = 0; i < players.length; i++) {
                var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "BudgetFixture"));
                players[i] = owner;
                for (int j = 0; j < 2; j++) {
                    String binding = UUID.randomUUID().toString();
                    ContractCarrierLossService.scheduleDestroyed(owner, UUID.randomUUID().toString(), binding, new ItemStack(Items.IRON_SWORD));
                    Object task = pending.get(owner.getUUID()).get(binding);
                    var field = task.getClass().getDeclaredField("candidate"); field.setAccessible(true);
                    var candidate = (ContractCarrierLossService.Candidate) field.get(task);
                    field.set(task, new ContractCarrierLossService.Candidate(candidate.maidId(), binding,
                            candidate.snapshot(), level.getGameTime() - 45));
                }
                ContractCarrierLossService.process(owner, Map.of(), (p, c) -> { throw new IllegalStateException("missing maid released"); });
            }
            int attempted = 0;
            for (var owner : players) {
                for (Object task : pending.get(owner.getUUID()).values()) {
                    var retry = task.getClass().getDeclaredField("nextAttempt"); retry.setAccessible(true);
                    if (retry.getLong(task) > level.getGameTime()) attempted++;
                }
                ContractCarrierLossService.process(owner, Map.of(), (p, c) -> { });
            }
            check(attempted == 8, "global restoration budget across five players");
            check(pending.get(players[4].getUUID()).size() == 2, "over-budget tasks retained");
            budgets.clear();
            for (var owner : players) ContractCarrierLossService.process(owner, Map.of(), (p, c) -> { });
            var used = budgets.get(level.getServer()).getClass().getDeclaredField("used");
            used.setAccessible(true);
            check(used.getInt(budgets.get(level.getServer())) == 2, "failed jobs do not retry before backoff");
            check(pending.get(players[0].getUUID()).size() == 2, "failed tasks retained with retry backoff");
            System.out.println("CONTRACT_SERVER_BUDGET_VALIDATION_PASSED");
        } finally {
            for (var owner : players) if (owner != null) pending.remove(owner.getUUID());
            budgets.clear();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private ContractCarrierLossValidation() {}
}
