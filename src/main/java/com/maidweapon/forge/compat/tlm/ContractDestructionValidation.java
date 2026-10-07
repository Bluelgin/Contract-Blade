package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.deployment.ContractCarrierLossService;
import com.maidweapon.forge.system.deployment.ContractCarrierLossJournal;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;

/** Isolated, opt-in destruction policy regression; never scans or edits a player's save. */
final class ContractDestructionValidation {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void run(MinecraftServer server) throws ReflectiveOperationException {
        var level = server.overworld();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "DestructionFixture"));
        owner.getInventory().clearContent(); owner.getInventory().selected = 0;
        owner.containerMenu = owner.inventoryMenu; owner.setHealth(20);
        var spawn = level.getSharedSpawnPos(); owner.setPos(spawn.getX()+.5,100,spawn.getZ()+.5);
        Entity maid = (Entity) TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, owner); maid.setPos(owner.position()); level.addFreshEntity(maid);
        var identity = maid.getUUID();
        var blade = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse("slashblade:slashblade")));
        owner.getInventory().setItem(0, blade);
        check(ContractLifecycleService.capture(owner, maid, blade, false), "capture blade spirit");
        var capability = (net.minecraftforge.common.capabilities.Capability) Class.forName(
                "mods.flammpfeil.slashblade.item.ItemSlashBlade").getField("BLADESTATE").get(null);
        Object state = blade.getCapability(capability).resolve().orElseThrow();
        state.getClass().getMethod("setBroken", boolean.class).invoke(state, true);
        check((boolean) state.getClass().getMethod("isBroken").invoke(state), "native broken flag is set");
        check(InfusedMaidDeploymentSystem.manifestRequested(owner, blade), "broken blade manifests normally");
        net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(owner, blade.copy(), InteractionHand.MAIN_HAND);
        check(!ContractCarrierLossService.hasPending(owner), "retained broken blade event never schedules rescue");
        owner.getInventory().setItem(0, ItemStack.EMPTY);
        var closedStorage = new net.minecraft.world.SimpleContainer(1); closedStorage.setItem(0, blade);
        advance(owner, 100);
        check(level.getEntity(identity) != null && !ContractCarrierLossService.hasPending(owner), "closed storage absence never triggers rescue");
        String binding = ContractCarrierData.getBindingId(blade);
        check(binding.equals(level.getEntity(identity).getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID)), "absence does not detach contract");
        var stand = new ItemFrame(level, owner.blockPosition().offset(2,0,0), net.minecraft.core.Direction.NORTH);
        stand.setItem(closedStorage.removeItemNoUpdate(0)); level.addFreshEntity(stand);
        advance(owner, 100);
        check(!ContractCarrierLossService.hasPending(owner) && level.getEntity(identity) != null, "blade stand absence never triggers rescue");
        blade = stand.getItem().copy(); stand.discard(); owner.getInventory().setItem(0, blade);
        check(SlashBladeCompat.isRetainedContractBreak(blade.copy(), blade), "copy/refinement retains identity");
        check(InfusedMaidDeploymentSystem.recallRequested(owner, blade) && MaidInfusion.containsMaid(blade), "broken blade recalls normally");
        blade = validateNativeStandTransfer(owner, blade);
        validateTetraContainer(owner);
        blade.enchant(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1);
        blade.hurtAndBreak(1, owner, ignored -> {});
        check(!blade.isEmpty() && !ContractCarrierLossService.hasPending(owner), "enchanted broken SlashBlade remains usable without rescue");
        var wooden = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse("slashblade:slashblade_wood")));
        wooden.setTag(blade.getTag().copy());
        wooden.getOrCreateTag().remove("Enchantments");
        Object woodenState = wooden.getCapability(capability).resolve().orElseThrow();
        woodenState.getClass().getMethod("setBroken", boolean.class).invoke(woodenState, true);
        blade = wooden;
        owner.setItemInHand(InteractionHand.MAIN_HAND, blade);
        var snapshot = blade.copy();
        blade.hurtAndBreak(1, owner, ignored -> {});
        check(blade.isEmpty() && ContractCarrierLossService.hasPending(owner), "native SlashBlade durability consumption schedules rescue directly");
        net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(owner, snapshot, InteractionHand.MAIN_HAND);
        advance(owner, 50);
        maid = level.getEntity(identity);
        check(maid != null && !maid.isRemoved() && !maid.getPersistentData().contains(ContractMaidKeys.ENTITY_BINDING_ID), "explicit destruction releases living spirit");
        check(!ContractCarrierLossService.hasPending(owner), "destroyed event completed once");
        // No Forge destruction notification is needed to prove vanilla durability consumption.
        var ordinary = new ItemStack(Items.IRON_SWORD); owner.getInventory().setItem(0, ordinary);
        check(ContractLifecycleService.capture(owner, maid, ordinary, false), "capture into ordinary carrier");
        ordinary.setDamageValue(ordinary.getMaxDamage()-1);
        ordinary.getOrCreateTag().putBoolean("Unbreakable", true);
        ordinary.hurtAndBreak(100, owner, ignored -> {});
        check(!ordinary.isEmpty() && !ContractCarrierLossService.hasPending(owner), "unbreakable carrier never triggers rescue");
        ordinary.getOrCreateTag().remove("Unbreakable");
        owner.getAbilities().instabuild = true;
        ordinary.hurtAndBreak(100, owner, ignored -> {});
        check(!ordinary.isEmpty() && !ContractCarrierLossService.hasPending(owner), "creative durability immunity never triggers rescue");
        owner.getAbilities().instabuild = false;
        var original = ordinary.copy();
        ordinary.hurtAndBreak(2, owner, ignored -> {});
        check(ordinary.isEmpty(), "ordinary carrier actually consumed by vanilla durability");
        check(ContractCarrierLossService.hasPending(owner), "durability hook schedules rescue before any generic event");
        net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(owner, original, InteractionHand.MAIN_HAND);
        check(ContractCarrierLossService.hasPending(owner), "Forge destruction hook schedules rescue");
        advance(owner, 50);
        maid = level.getEntity(identity);
        check(maid != null && !maid.isRemoved(), "vanilla destruction never kills maid");
        maid.discard();
        validateLegacy(owner);
        InfusedMaidDeploymentSystem.onLogout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner));
        owner.getInventory().clearContent();
        LogUtils.getLogger().info("CONTRACT_DESTRUCTION_PASS: native stand insertion/swap/removal, stored/live maid, retained menu carrier, broken SlashBlade, actual destruction and legacy quarantine");
    }

    private static ItemStack validateNativeStandTransfer(net.minecraft.server.level.ServerPlayer owner, ItemStack blade) {
        var level = owner.serverLevel();
        String binding = ContractCarrierData.getBindingId(blade);
        String maidId = ContractCarrierData.getBoundMaidUUID(blade);
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                net.minecraft.resources.ResourceLocation.parse("slashblade:blade_stand_entity"));
        var events = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<PlayerDestroyItemEvent> observer = event -> {
            if (event.getEntity() == owner && binding.equals(ContractCarrierData.getBindingId(event.getOriginal())))
                events.incrementAndGet();
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(observer);
        try {
            owner.getAbilities().instabuild = false;
            for (boolean live : new boolean[] {false, true}) {
                owner.setItemInHand(InteractionHand.MAIN_HAND, blade);
                if (live) check(InfusedMaidDeploymentSystem.manifestRequested(owner, blade), "manifest before native stand transfer");
                var frame = (ItemFrame) type.create(level);
                check(frame != null, "native BladeStand entity available");
                frame.setPos(owner.getX()+2, owner.getY(), owner.getZ());
                level.addFreshEntity(frame);
                try {
                    int before = events.get();
                    check(owner.interactOn(frame, InteractionHand.MAIN_HAND).consumesAction(), "native survival right-click inserts blade");
                    check(owner.getMainHandItem().isEmpty() && binding.equals(ContractCarrierData.getBindingId(frame.getItem())), "carrier actually moved into native stand");
                    check(events.get() > before, "real Player.interactOn emitted misleading Forge destruction event");
                    check(!ContractCarrierLossService.hasPending(owner), "successful stand transfer never schedules rescue");
                    advance(owner, 50);
                    if (live) check(binding.equals(level.getEntity(java.util.UUID.fromString(maidId)).getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID)), "live maid retains binding on stand");
                    else check(MaidInfusion.containsMaid(frame.getItem()), "stored maid remains in stand carrier");
                    // Swap with an ordinary blade through the native interaction, not setItem().
                    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(frame.getItem().getItem()));
                    owner.interactOn(frame, InteractionHand.MAIN_HAND);
                    blade = owner.getMainHandItem();
                    check(binding.equals(ContractCarrierData.getBindingId(blade)), "native swap retrieves original binding");
                    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    owner.interactOn(frame, InteractionHand.MAIN_HAND);
                    check(frame.getItem().isEmpty(), "native empty-hand removal works");
                    owner.setItemInHand(InteractionHand.MAIN_HAND, blade);
                    if (live) check(InfusedMaidDeploymentSystem.recallRequested(owner, blade), "recall after live native transfer");
                    check(MaidInfusion.containsMaid(blade) && !ContractCarrierLossService.hasPending(owner), "stand transfer ends without duplicate spirit");
                } finally { frame.discard(); }
            }
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observer); }
        LogUtils.getLogger().info("NATIVE_STAND_TRANSFER_PASS: real survival Forge events, stored/live maid, swap and removal");
        return blade;
    }

    private static void validateTetraContainer(net.minecraft.server.level.ServerPlayer owner) throws ReflectiveOperationException {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("tetra")) return;
        var level = owner.serverLevel();
        var pos = owner.blockPosition().offset(4, 0, 0);
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("tetra:basic_workbench"));
        var old = level.getBlockState(pos);
        ItemStack held = owner.getMainHandItem();
        var oldMenu = owner.containerMenu;
        try {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            var tile = level.getBlockEntity(pos);
            check(tile != null, "real Tetra workbench tile available");
            var handler = tile.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            var item = new ItemStack(Items.IRON_SWORD);
            var maid = (Entity) TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class).newInstance(level);
            TlmEntityAdapter.tame(maid, owner); level.addFreshEntity(maid);
            check(ContractLifecycleService.capture(owner, maid, item, false), "capture into Tetra carrier");
            owner.setItemInHand(InteractionHand.MAIN_HAND, item);
            var menu = (net.minecraft.world.inventory.AbstractContainerMenu) Class.forName("se.mickelus.tetra.blocks.workbench.WorkbenchContainer")
                    .getConstructor(int.class, tile.getClass(), net.minecraft.world.Container.class, net.minecraft.world.entity.player.Player.class)
                    .newInstance(79, tile, owner.getInventory(), owner);
            owner.containerMenu = menu;
            var snapshot = item.copy();
            int slot = -1;
            for (int i=0;i<menu.slots.size();i++) if (menu.getSlot(i).getItem() == item) { slot=i; break; }
            check(slot >= 0, "Tetra menu exposes carrier inventory slot");
            menu.quickMoveStack(owner, slot);
            check(owner.getMainHandItem().isEmpty() && ContractCarrierData.getBindingId(snapshot).equals(ContractCarrierData.getBindingId(handler.getStackInSlot(0))), "native Tetra shift-click places carrier in target slot");
            check(!ContractCarrierLossService.hasPending(owner), "Tetra insertion does not publish destructive rescue");
            // Even a provider's extra destruction notification must not override a retained menu carrier.
            net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(owner, snapshot, InteractionHand.MAIN_HAND);
            check(!ContractCarrierLossService.hasPending(owner), "retained Tetra slot cancels extra consumption notification");
            owner.containerMenu = owner.inventoryMenu;
            advance(owner, 50);
            check(!ContractCarrierLossService.hasPending(owner) && MaidInfusion.containsMaid(handler.getStackInSlot(0)), "closed Tetra workbench preserves stored maid");
            handler.extractItem(0, 1, false);
            LogUtils.getLogger().info("TETRA_CONTAINER_TRANSFER_PASS: real workbench shift-click, open/closed storage and retained-slot notification");
        } finally {
            owner.containerMenu = oldMenu;
            owner.setItemInHand(InteractionHand.MAIN_HAND, held);
            level.setBlockAndUpdate(pos, old);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateLegacy(net.minecraft.server.level.ServerPlayer owner) throws ReflectiveOperationException {
        var entry = new net.minecraft.nbt.CompoundTag();
        entry.putUUID("Player", owner.getUUID()); entry.putString("Maid", java.util.UUID.randomUUID().toString());
        String binding = java.util.UUID.randomUUID().toString(); entry.putString("Binding", binding);
        entry.put("Carrier", new ItemStack(Items.IRON_SWORD).save(new net.minecraft.nbt.CompoundTag()));
        entry.putBoolean("DestroyedEvent", true);
        var tasks = new net.minecraft.nbt.ListTag(); tasks.add(entry);
        var root = new net.minecraft.nbt.CompoundTag(); root.put("Tasks", tasks);
        var loaded = ContractCarrierLossJournal.load(root);
        var field = ContractCarrierLossJournal.class.getDeclaredField("pending"); field.setAccessible(true);
        var actual = (java.util.Map) field.get(ContractCarrierLossJournal.get(owner.getServer()));
        actual.putAll((java.util.Map)field.get(loaded));
        check(!loaded.save(new net.minecraft.nbt.CompoundTag()).getList("Tasks",10).getCompound(0).getBoolean("DurabilityConfirmed"),
                "old generic destruction tasks are not confirmed durability");
        ContractCarrierLossService.process(owner, java.util.Map.of(), (p,c) -> { throw new IllegalStateException("legacy rescue executed"); });
        check(actual.containsKey(owner.getUUID()) && loaded.save(new net.minecraft.nbt.CompoundTag()).getList("Tasks",10).size()==1,
                "unproven legacy snapshot is preserved without rescue");
        actual.remove(owner.getUUID());
        ContractCarrierLossJournal.get(owner.getServer()).setDirty();
    }

    private static void advance(net.minecraft.server.level.ServerPlayer owner, int ticks) {
        for (int i=0;i<ticks;i+=5) {
            var data = (net.minecraft.world.level.storage.ServerLevelData) owner.getServer().overworld().getLevelData();
            data.setGameTime(owner.getServer().overworld().getGameTime()+5);
            owner.tickCount += 5;
            InfusedMaidDeploymentSystem.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
        }
    }
    private static void check(boolean passed, String label) {
        if (!passed) throw new IllegalStateException(label);
    }
    private ContractDestructionValidation() { }
}
