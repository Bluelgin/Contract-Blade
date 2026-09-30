package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.event.MaidInteractionHandler;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.interior.ContractInteriorEvents;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Native regression fixture invoked only by the explicit headless home validator. */
public final class ContractCarrierValidation {
    public static void inventoryClicks(ServerPlayer player, ItemStack contract) {
        check(Boolean.getBoolean("contractblade.home.nativeValidation"), "fixture disabled");
        String binding = MaidWeaponItem.getBindingId(contract);
        CompoundTag original = contract.save(new CompoundTag());

        // Real server-side PICKUP moves the original inventory stack to the cursor.
        player.inventoryMenu.clicked(36, 0, ClickType.PICKUP, player);
        check(binding.equals(MaidWeaponItem.getBindingId(player.inventoryMenu.getCarried())),
                "mouse pickup keeps contract on cursor");
        player.tickCount = 1;
        ContractInteriorEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        check(ContractInteriorService.isInside(player), "cursor pickup does not exit home");
        check(original.equals(player.inventoryMenu.getCarried().save(new CompoundTag())),
                "open-menu safety does not consume or replace cursor contract");
        player.inventoryMenu.clicked(38, 0, ClickType.PICKUP, player);
        check(player.inventoryMenu.getCarried().isEmpty(), "mouse place clears cursor");
        check(original.equals(player.getInventory().getItem(2).save(new CompoundTag())),
                "mouse place preserves contract NBT");

        // Creative inventory cursors exist only on the client. Model the server's
        // temporary absence, then the later creative slot-placement packet.
        ItemStack clientCursor = player.getInventory().getItem(2);
        player.getInventory().setItem(2, ItemStack.EMPTY);
        ContractInteriorService.recoverFromVoid(player);
        check(ContractInteriorService.isInside(player), "creative pickup does not teleport");
        check(binding.equals(player.getPersistentData().getCompound("MaidWeaponInteriorReturn")
                .getString("Binding")), "creative pickup preserves return session");
        player.getInventory().setItem(0, clientCursor);
        check(binding.equals(ContractInteriorService.activeOwnedBinding(player)),
                "creative placement restores physical authority");

        player.inventoryMenu.clicked(36, 0, ClickType.PICKUP, player);
        check(ContractInteriorService.rescueActiveContractFromContainer(player, true),
                "closing menu rescues carried contract");
        check(player.inventoryMenu.getCarried().isEmpty(), "close clears rescued cursor");
        check(original.equals(player.getInventory().getItem(0).save(new CompoundTag())),
                "close retains one intact contract");
        System.out.println("CONTRACT_CARRIER_INVENTORY_VALIDATION_PASSED");
    }

    public static void dedicatedDeployment(ServerLevel interior) throws ReflectiveOperationException {
        check(Boolean.getBoolean("contractblade.home.nativeValidation"), "fixture disabled");
        ServerLevel level = interior.getServer().overworld();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(
                        java.util.UUID.fromString("32de43a6-4bde-4b7d-8259-e2e45d6f815"), "CarrierFixture"));
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        // Fake players do not create player chunk tickets. Use the already-loaded
        // spawn region, not (0, 0), which can be inactive on random CI world seeds.
        var spawn = level.getSharedSpawnPos();
        player.setPos(spawn.getX() + .5, Math.max(80, spawn.getY()) + 3, spawn.getZ() + .5);
        ItemStack weapon = new ItemStack(ModItems.MAID_SWORD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        Entity maid = (Entity) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        maid.moveTo(player.getX(), player.getY(), player.getZ());
        TlmEntityAdapter.tame(maid, player);
        check(level.addFreshEntity(maid), "fixture maid spawned");
        java.util.UUID maidId = maid.getUUID();
        try {
            var click = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, maid);
            MaidInteractionHandler.onEntityInteract(click);
            check(click.isCanceled() && click.getCancellationResult() == InteractionResult.SUCCESS,
                    "direct binding consumes TLM GUI click");
            check(MaidInfusion.containsMaid(weapon) && maid.isRemoved(),
                    "dedicated blade binds directly without Contract Table");
            check(MaidWeaponItem.isOwner(weapon, player)
                    && maidId.toString().equals(MaidWeaponItem.getBoundMaidUUID(weapon)),
                    "direct binding preserves owner and real maid identity");
            String binding = MaidWeaponItem.getBindingId(weapon);
            ticks(player, com.maidweapon.common.MaidWeaponConfig.MANIFEST_DEPLOY_DELAY.get() + 10);
            Entity live = level.getEntity(maidId);
            check(live != null && !live.isRemoved() && !MaidInfusion.containsMaid(weapon),
                    "main-hand dedicated blade automatically manifests real maid");
            check(binding.equals(live.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID)),
                    "automatic deployment keeps same binding");
            player.getInventory().selected = 1;
            ticks(player, com.maidweapon.common.MaidWeaponConfig.MANIFEST_RECALL_DELAY.get() + 10);
            live = level.getEntity(maidId);
            check(MaidInfusion.containsMaid(weapon) && (live == null || live.isRemoved()),
                    "switching away captures maid into the same dedicated blade");
            check(binding.equals(MaidWeaponItem.getBindingId(weapon)), "recall preserves binding");
            System.out.println("CONTRACT_CARRIER_DEPLOYMENT_VALIDATION_PASSED");
        } finally {
            InfusedMaidDeploymentSystem.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            Entity remaining = level.getEntity(maidId);
            if (remaining != null) remaining.discard();
            player.getInventory().clearContent();
        }
    }

    private static void ticks(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            player.tickCount++;
            InfusedMaidDeploymentSystem.onPlayerTick(
                    new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        }
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Contract carrier validation: " + reason);
    }

    private ContractCarrierValidation() {}
}
