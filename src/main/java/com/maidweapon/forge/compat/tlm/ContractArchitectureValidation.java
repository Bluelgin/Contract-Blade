package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.api.event.ContractMaidCapturedEvent;
import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.system.fox.FoxSpiritAbilityAuthorization;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.FoxSpiritLedger;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import net.minecraft.server.level.ServerPlayer;
import com.maidweapon.forge.api.event.ContractMaidManifestedEvent;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.event.ContractRecoveryCommand;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.contract.ContractRecoveryJournal;
import com.maidweapon.forge.system.deployment.ContractCarrierLossService;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in actual Forge/TLM failure fixture. Never represents a human client acceptance test. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractArchitectureValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.architecture.validation")) return;
        try {
            run(event.getServer());
            LogUtils.getLogger().info("CONTRACT_ARCHITECTURE_PASS");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("CONTRACT_ARCHITECTURE_FAIL", failure);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArchitectureFixture"));
        player.setPos(.5, 150, .5);
        player.setHealth(20);
        Entity maid = (Entity) TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, player);
        maid.setPos(player.position());
        check(level.addFreshEntity(maid), "real maid publication");
        UUID id = maid.getUUID();
        var carrier = new ItemStack(Items.IRON_SWORD);
        player.getInventory().setItem(0, carrier);

        String task = TlmEntityAdapter.taskId(maid);
        String schedule = TlmEntityAdapter.scheduleName(maid);
        carrier.getOrCreateTag().putString(MaidCareTaskSystem.ORIGINAL_TASK_TAG, MaidCareTaskSystem.FEED_TASK);
        carrier.getOrCreateTag().putByteArray("InjectedOversize",
                new byte[com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_STORED_BYTES.get() + 1]);
        var before = carrier.getTag().copy();
        check(!ContractLifecycleService.capture(player, maid, carrier, false), "encoding rejection");
        check(maid.isAlive() && !maid.isRemoved() && !MaidInfusion.containsMaid(carrier), "only live entity retained");
        check(before.equals(carrier.getTag()), "weapon preparation rolled back");
        check(maid.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID).isEmpty(), "new binding rolled back");
        check(task.equals(TlmEntityAdapter.taskId(maid)) && schedule.equals(TlmEntityAdapter.scheduleName(maid)), "task and schedule rolled back");
        carrier.getTag().remove("InjectedOversize");
        carrier.getTag().remove(MaidCareTaskSystem.ORIGINAL_TASK_TAG);

        var throwingCapture = new ThrowingCapture(carrier);
        MinecraftForge.EVENT_BUS.register(throwingCapture);
        try {
            check(ContractLifecycleService.capture(player, maid, carrier, false), "observer exception cannot reject committed capture");
        } finally { MinecraftForge.EVENT_BUS.unregister(throwingCapture); }
        check(maid.isRemoved() && MaidInfusion.containsMaid(carrier), "capture authority moved to storage");
        var stored = carrier.getTag().copy();
        check(!ContractLifecycleService.manifest(player, carrier, false, candidate -> {
            throw new IllegalStateException("injected before spawn");
        }), "pre-publication exception rejected");
        check(stored.equals(carrier.getTag()) && level.getEntity(id) == null, "failed candidate cannot consume snapshot");
        check(!ContractLifecycleService.manifest(player, carrier, false, candidate -> candidate.setUUID(UUID.randomUUID())),
                "callback identity mutation rejected at commit boundary");
        check(stored.equals(carrier.getTag()), "mutated candidate retains exact snapshot");

        var cancel = new CancelSpawn(id);
        MinecraftForge.EVENT_BUS.register(cancel);
        try {
            check(!ContractLifecycleService.manifest(player, carrier, false), "Forge canceled spawn rejected");
        } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        check(stored.equals(carrier.getTag()) && !ContractRecoveryJournal.get(server).isMaidPresent(id.toString()),
                "canceled spawn cannot publish authority or consume storage");

        var throwingManifest = new ThrowingManifest(carrier);
        MinecraftForge.EVENT_BUS.register(throwingManifest);
        try {
            check(ContractLifecycleService.manifest(player, carrier, false), "post-publication observer exception remains success");
        } finally { MinecraftForge.EVENT_BUS.unregister(throwingManifest); }
        maid = level.getEntity(id);
        check(maid != null && maid.isAlive() && !MaidInfusion.containsMaid(carrier), "single live authority after observer failure");
        check(ContractRecoveryJournal.get(server).isMaidPresent(id.toString()), "world fact retained after notification failure");
        check(!ContractWeaponLocator.mayManifest(player, id.toString()), "duplicate UUID stays denied");
        check(ContractLifecycleService.capture(player, maid, carrier, false), "recall after observer failure");

        verifyAuthorizationCache(player);

        // Confirmed destruction with a throwing completion observer cannot replay the result.
        String binding = ContractCarrierData.getBindingId(carrier);
        ContractCarrierLossService.scheduleDestroyed(player, id.toString(), binding, carrier.copy());
        player.getInventory().clearContent();
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 41);
        ContractCarrierLossService.process(player, Map.of(), (owner, result) -> {
            throw new IllegalStateException("injected recovery completion observer");
        });
        check(!ContractCarrierLossService.hasPending(player), "completed recovery removed before callback");
        check(level.getEntity(id) != null, "recovery retains original real entity");
        ContractCarrierLossService.process(player, Map.of(), (owner, result) -> {
            throw new AssertionError("completed rescue must not replay");
        });
        player.getInventory().setItem(0, carrier);
        var correction = ContractRecoveryCommand.class.getDeclaredMethod("confirmAbsent",
                net.minecraft.commands.CommandSourceStack.class, ServerPlayer.class);
        correction.setAccessible(true);
        check((int) correction.invoke(null, server.createCommandSourceStack(), player) == 0,
                "operator correction cannot override a loaded real UUID");
        level.getEntity(id).discard();
        ContractRecoveryJournal.get(server).markMaidPresent(id.toString()); // simulate external stale observation
        check((int) correction.invoke(null, server.createCommandSourceStack(), player) == 1,
                "explicit audited correction has a real repair path");
        check(!ContractRecoveryJournal.get(server).isMaidPresent(id.toString()), "audited absence fact repaired");
    }

    private static void verifyAuthorizationCache(ServerPlayer owner) throws Exception {
        var carrier = new ItemStack(Items.DIAMOND_SWORD);
        var fox = new CompoundTag();
        fox.putInt("Version", 1);
        fox.putString("SpiritId", FoxSpiritState.WHITE);
        fox.putUUID("OwnerUUID", owner.getUUID());
        for (String field : new String[]{"SpiritUUID", "OriginUUID", "Token"}) fox.putUUID(field, UUID.randomUUID());
        carrier.getOrCreateTag().put(FoxSpiritState.ROOT, fox);
        carrier.getTag().putUUID(FoxSpiritState.ORIGIN, fox.getUUID("OriginUUID"));
        FoxSpiritLedger.get(owner.getServer()).record(fox, "WEAPON");
        check(EmbeddedSpiritApi.bindPresetSpirit(owner, carrier, FoxSpiritState.WHITE, "CacheFixture"),
                "real fox full snapshot created");
        String maid = ContractCarrierData.getBoundMaidUUID(carrier);
        FoxSpiritLedger.get(owner.getServer()).rememberMaid(fox, maid);
        check(FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE), "warm valid compressed proof");
        byte[] compressed = carrier.getTag().getByteArray(MaidEntityDataCodec.COMPRESSED_DATA);
        compressed[compressed.length - 1] ^= 1;
        check(!FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE),
                "same root/checksum/length cannot reuse mutated compressed proof");
        compressed[compressed.length - 1] ^= 1;
        check(FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE), "restored snapshot revalidates");
        var journal = ContractRecoveryJournal.get(owner.getServer());
        journal.markMaidPresent(maid);
        check(!FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE), "world presence invalidates proof immediately");
        journal.markMaidAbsent(maid);
        var data = MaidEntityDataCodec.read(carrier.getTag());
        MaidEntityDataCodec.remove(carrier.getTag());
        carrier.getTag().put(MaidEntityDataCodec.LEGACY_DATA, data);
        check(FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE), "warm legacy proof");
        carrier.getTag().getCompound(MaidEntityDataCodec.LEGACY_DATA).putUUID("Owner", UUID.randomUUID());
        check(!FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE), "legacy in-place owner edit invalidates proof");
        carrier.getTag().getCompound(MaidEntityDataCodec.LEGACY_DATA).putUUID("Owner", owner.getUUID());
        check(FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE), "legacy owner restored");
        journal.markMaidPresent(maid);
        check(!FoxSpiritAbilityAuthorization.allows(owner, carrier, FoxSpiritState.WHITE), "legacy proof never caches world permission");
        journal.markMaidAbsent(maid);
        // Legacy destruction has no existence observation. It still relinquishes the
        // destroyed narrative carrier so a future native-restored fox can be recaptured.
        String binding = ContractCarrierData.getBindingId(carrier);
        ContractMaidStorage.remove(carrier);
        var unknown = new CompoundTag();
        journal.transfer(maid, unknown);
        ContractCarrierLossService.scheduleDestroyed(owner, maid, binding, carrier);
        ((net.minecraft.world.level.storage.ServerLevelData) owner.level().getLevelData()).setGameTime(owner.level().getGameTime() + 41);
        ContractCarrierLossService.process(owner, Map.of(), (player, result) -> {});
        check(journal.presence(maid) == ContractRecoveryJournal.Presence.UNKNOWN,
                "unknown legacy loss cannot invent world existence");
        check(FoxSpiritLedger.get(owner.getServer()).owns(fox, "FREE_MAID"),
                "unknown loss detaches narrative ownership independently of existence");
    }

    public static final class ThrowingCapture {
        private final ItemStack carrier;
        ThrowingCapture(ItemStack carrier) { this.carrier = carrier; }
        @SubscribeEvent public void captured(ContractMaidCapturedEvent event) {
            if (event.getWeapon() == carrier) throw new IllegalStateException("injected capture observer");
        }
    }
    public static final class ThrowingManifest {
        private final ItemStack carrier;
        ThrowingManifest(ItemStack carrier) { this.carrier = carrier; }
        @SubscribeEvent public void manifested(ContractMaidManifestedEvent event) {
            if (event.carrier == carrier) {
                check(!MaidInfusion.containsMaid(carrier), "event follows authority transfer");
                throw new IllegalStateException("injected manifest observer");
            }
        }
    }
    public static final class CancelSpawn {
        private final UUID maid;
        CancelSpawn(UUID maid) { this.maid = maid; }
        @SubscribeEvent public void joined(EntityJoinLevelEvent event) {
            if (maid.equals(event.getEntity().getUUID())) event.setCanceled(true);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Architecture fixture: " + message);
    }
    private ContractArchitectureValidation() {}
}
