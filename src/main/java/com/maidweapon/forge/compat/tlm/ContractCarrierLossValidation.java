package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import java.util.Map;
import java.util.UUID;

/** Opt-in private-world regression: live A, stored B/C lost together, broken-but-present D. */
public final class ContractCarrierLossValidation {
    @SuppressWarnings("unchecked")
    public static void run(ServerLevel level) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("contractblade.home.nativeValidation"))
            throw new IllegalStateException("carrier-loss fixture disabled");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LossFixture"));
        var spawn = level.getSharedSpawnPos();
        player.setPos(spawn.getX() + .5, Math.max(80, spawn.getY()) + 3, spawn.getZ() + .5);
        player.getInventory().selected = 0;
        ItemStack[] carriers = new ItemStack[4];
        UUID[] maids = new UUID[4];
        var pendingField = InfusedMaidDeploymentSystem.class.getDeclaredField("PENDING_CARRIER_LOSSES");
        pendingField.setAccessible(true);
        var pending = (Map<UUID, Map<String, Object>>) pendingField.get(null);
        try {
            for (int i = 0; i < 4; i++) {
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
            for (int i = 1; i < 4; i++) {
                InfusedMaidDeploymentSystem.onContractCarrierDestroyed(
                        new PlayerDestroyItemEvent(player, carriers[i].copy(), null));
                if (i < 3) player.getInventory().setItem(i, ItemStack.EMPTY);
            }
            check(pending.get(player.getUUID()).size() == 3, "losses queued independently");
            // Advance only candidate timestamps, not the shared world's clock.
            var candidates = pending.get(player.getUUID());
            for (var entry : candidates.entrySet()) {
                Object record = entry.getValue();
                var type = record.getClass();
                var ctor = type.getDeclaredConstructor(String.class, String.class, ItemStack.class, long.class);
                ctor.setAccessible(true);
                var maidId = type.getDeclaredMethod("maidId"); maidId.setAccessible(true);
                var snapshot = type.getDeclaredMethod("snapshot"); snapshot.setAccessible(true);
                entry.setValue(ctor.newInstance(maidId.invoke(record), entry.getKey(), snapshot.invoke(record),
                        level.getServer().overworld().getGameTime() - 45));
            }
            player.tickCount = 5;
            InfusedMaidDeploymentSystem.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            for (int i = 1; i < 3; i++) {
                Entity released = level.getEntity(maids[i]);
                check(released != null && !released.isRemoved(), "B/C released while A remains active");
                check(!released.getPersistentData().contains(ContractMaidKeys.ENTITY_BINDING_ID), "lost binding detached");
                check(released.getPersistentData().contains(ContractMaidKeys.EMERGENCY_FILM_PROGRESS), "growth preserved");
            }
            check(level.getEntity(maids[0]) != null, "A stays alive");
            check(MaidInfusion.containsMaid(carriers[3]) && level.getEntity(maids[3]) == null,
                    "present D is not falsely released");
            check(!pending.containsKey(player.getUUID()), "processed losses removed independently");
            check(InfusedMaidDeploymentSystem.recallRequested(player, carriers[0]), "A still recalls normally");
            System.out.println("MULTI_CARRIER_LOSS_VALIDATION_PASSED");
        } finally {
            pending.remove(player.getUUID());
            InfusedMaidDeploymentSystem.onLogout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            for (UUID id : maids) {
                Entity maid = id == null ? null : level.getEntity(id);
                if (maid != null) maid.discard();
            }
            player.getInventory().clearContent();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private ContractCarrierLossValidation() {}
}
