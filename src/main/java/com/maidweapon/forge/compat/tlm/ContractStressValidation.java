package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractCarrierLossService;
import com.maidweapon.forge.system.deployment.ContractCarrierLossJournal;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.ContractNbtGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Destructive-to-fixture-only heap stress. Never enabled in ordinary player worlds. */
public final class ContractStressValidation {
    @SuppressWarnings("unchecked")
    public static String run(ServerLevel level, int payloadKiB) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("contractblade.home.nativeValidation")
                || !Boolean.getBoolean("contractblade.stress.validation"))
            throw new IllegalStateException("native fixture only");
        var runtime = Runtime.getRuntime();
        if (runtime.maxMemory() > 1100L * 1024 * 1024)
            throw new IllegalStateException("stress requires an isolated heap capped at 1 GiB");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StressFixture"));
        var spawn = level.getSharedSpawnPos();
        player.setPos(spawn.getX() + .5, 85, spawn.getZ() + .5);
        var template = new ItemStack(Items.IRON_SWORD);
        var maid = (net.minecraft.world.entity.Entity) TlmEntityAdapter.maidClass()
                .getConstructor(net.minecraft.world.level.Level.class).newInstance(level);
        maid.moveTo(player.getX(), player.getY(), player.getZ());
        TlmEntityAdapter.tame(maid, player);
        level.addFreshEntity(maid);
        if (!ContractLifecycleService.capture(player, maid, template, false)) {
            maid.discard();
            throw new IllegalStateException("cannot build genuine captured-maid template");
        }
        // Independent byte arrays emulate large retained weapon/third-party archive data.
        if (payloadKiB > 0) template.getOrCreateTag().putByteArray("StressArchive", new byte[payloadKiB * 1024]);
        try { com.maidweapon.forge.system.MaidEntityDataCodec.validateContainer(template.getTag()); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        long itemBytes = ContractNbtGuard.serializedSize(template.save(new CompoundTag()));
        StringBuilder report = new StringBuilder("STRESS payloadKiB=" + payloadKiB + " itemBytes=" + itemBytes
                + " heapCapMiB=" + runtime.maxMemory() / 1048576);
        var journal = ContractCarrierLossJournal.get(level.getServer());
        var field = ContractCarrierLossJournal.class.getDeclaredField("pending"); field.setAccessible(true);
        var queues = (Map<UUID, ?>) field.get(journal);
        var originalMenu = player.containerMenu;
        var objects = new ArrayList<ItemStack>();
        int allocated = 0;
        boolean exhausted = false;
        long start = System.nanoTime();
        try {
            // First measure lookup with real captured contracts, without restoring entities.
            if (payloadKiB == 0) {
                for (int count : new int[]{36, 90, 256, 1024, 4096, 10000}) {
                    var menu = new StressMenu(count);
                    for (int i = 0; i < count; i++) {
                        var item = template.copy();
                        item.getOrCreateTag().putString("MaidBindingId", UUID.randomUUID().toString());
                        menu.slots.get(i).set(item);
                    }
                    player.containerMenu = menu;
                    for (int i = 0; i < 5; i++) ContractWeaponLocator.indexByBinding(player);
                    long[] samples = new long[30];
                    for (int i = 0; i < samples.length; i++) {
                        long before = System.nanoTime();
                        if (ContractWeaponLocator.indexByBinding(player).size() != count)
                            throw new IllegalStateException("index count mismatch");
                        samples[i] = System.nanoTime() - before;
                    }
                    Arrays.sort(samples);
                    report.append(String.format(java.util.Locale.ROOT,
                            "\nINDEX count=%d medianMs=%.3f p95Ms=%.3f", count,
                            samples[15] / 1e6, samples[28] / 1e6));
                }
                player.containerMenu = originalMenu;
            }
            System.gc();
            report.append("\nBASE heapUsedMiB=").append((runtime.totalMemory() - runtime.freeMemory()) / 1048576);
            // Deliberately drive only this capped JVM to allocation failure. Retain every
            // successful snapshot; no shared payload arrays or fake constant-memory counts.
            for (int target = 128; target <= 262144; target *= 2) {
                while (allocated < target) {
                    var weapon = template.copy();
                    String binding = UUID.randomUUID().toString();
                    weapon.getOrCreateTag().putString("MaidBindingId", binding);
                    objects.add(weapon);
                    ContractCarrierLossService.scheduleDestroyed(player, ContractCarrierData.getBoundMaidUUID(weapon), binding, weapon);
                    allocated++;
                }
                long before = System.nanoTime();
                // Clock has not advanced inside this command: tasks remain suspected,
                // so this probes bounded queue scanning, not an entity-spawn benchmark.
                ContractCarrierLossService.process(player, Map.of(), (p, c) -> { });
                report.append(String.format(java.util.Locale.ROOT,
                        "\nLOAD count=%d heapUsedMiB=%d queuePassMs=%.3f elapsedMs=%.0f", allocated,
                        (runtime.totalMemory() - runtime.freeMemory()) / 1048576,
                        (System.nanoTime() - before) / 1e6, (System.nanoTime() - start) / 1e6));
            }
        } catch (OutOfMemoryError expected) {
            exhausted = true;
        } finally {
            // Clear references before formatting the OOM result or the world is saved.
            player.containerMenu = originalMenu;
            objects.clear();
            queues.remove(player.getUUID());
            journal.setDirty();
            player.getInventory().clearContent();
            System.gc();
        }
        report.append("\nRESULT lastCompleted=").append(allocated).append(" allocationFailure=").append(exhausted)
                .append(" recoveredHeapMiB=").append((runtime.totalMemory() - runtime.freeMemory()) / 1048576);
        System.out.println(report);
        return report.toString();
    }

    private static final class StressMenu extends AbstractContainerMenu {
        StressMenu(int size) {
            super(null, 7);
            var container = new SimpleContainer(size);
            for (int i = 0; i < size; i++) addSlot(new Slot(container, i, 0, 0));
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    }
    private ContractStressValidation() {}
}
