package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.ability.NativeAbilityService;
import com.maidweapon.port.domain.ability.AbilityEngine;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeTransferJournal;
import com.maidweapon.port.resonance.NativeResonanceService;
import com.maidweapon.port.domain.resonance.ResonanceRules.Payment;
import com.maidweapon.port.storage.ContractIdentityFields;
import com.maidweapon.port.storage.ContractStackDataAccess;
import com.maidweapon.port.storage.LegacyMaidSnapshotCodec;
import com.mojang.brigadier.CommandDispatcher;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * In-memory native TLM round trip. Never publishes or removes a world entity.
 * Invoked by the dedicated-server console test, not a normal gameplay command.
 */
public final class NativeTlmRoundTripCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MAID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID BINDING = UUID.fromString("33333333-3333-4333-8333-333333333333");

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("contractportverify")
            .requires(source -> source.hasPermission(4))
            .executes(ctx -> run(ctx.getSource())));
    }

    private static int run(CommandSourceStack context) {
        try {
            EntityMaid original = new EntityMaid(context.getLevel());
            original.setOwnerUUID(OWNER);
            original.setUUID(MAID);
            original.setFavorability(20);
            original.setExperience(73);
            original.getMaidInv().setStackInSlot(0, new ItemStack(Items.DIAMOND, 23));
            original.getMaidBauble().setStackInSlot(0, new ItemStack(ProbeItems.CONTRACT_BAUBLE.get()));

            CompoundTag full = TlmNativeTransformBridge.captureSnapshot(original, new ItemStack(ProbeItems.CONTRACT_BAUBLE.get()));
            require(full.contains(EntityMaid.MAID_INVENTORY_TAG), "Missing native inventory");
            require(full.contains(EntityMaid.MAID_BAUBLE_INVENTORY_TAG), "Missing native bauble inventory");

            CompoundTag contract = ContractIdentityFields.bindOnce(
                new CompoundTag(), OWNER, "ProbeOwner", MAID, BINDING);
            LegacyMaidSnapshotCodec.write(contract, full);
            ItemStack carrier = new ItemStack(Items.STICK);
            CompoundTag foreign = new CompoundTag();
            foreign.putString("ExternalTestKey", "keep");
            carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(foreign));
            ContractStackDataAccess.replaceContract(carrier, contract);

            require("keep".equals(ContractStackDataAccess.readRoot(carrier)
                .getString("ExternalTestKey")), "Foreign carrier custom NBT was destroyed");
            CompoundTag decoded = LegacyMaidSnapshotCodec.read(ContractStackDataAccess.readContract(carrier));
            VerifiedTlmSnapshot.requireIdentity(decoded, OWNER, MAID);
            EntityMaid restored = TlmNativeTransformBridge.prepareManifestCandidate(context.getLevel(),
                carrier, decoded, OWNER, MAID);

            require(restored.getUUID().equals(MAID), "Restored UUID changed");
            require(OWNER.equals(restored.getOwnerUUID()), "Restored owner changed");
            require(restored.getMaidInv().getStackInSlot(0).is(Items.DIAMOND), "Inventory item vanished");
            require(restored.getMaidInv().getStackInSlot(0).getCount() == 23, "Inventory count changed");
            require(restored.getMaidBauble().getStackInSlot(0).is(ProbeItems.CONTRACT_BAUBLE.get()),
                "Native bauble disappeared");
            require(restored.getFavorability() == 20, "Favorability was lost");
            require(restored.getExperience() == 73, "Experience was lost");
            require(restored.getModelId().equals(original.getModelId()), "Model ID changed");
            CompoundTag again = VerifiedTlmSnapshot.captureCandidate(restored);
            require(again.contains(EntityMaid.MAID_INVENTORY_TAG), "Second snapshot lacks inventory");
            require(again.contains(EntityMaid.MAID_BAUBLE_INVENTORY_TAG), "Second snapshot lacks baubles");
            LOGGER.info("PORT_NATIVE_TLM_ROUNDTRIP_PASS: UUID, owner, 23 diamonds, bauble, favorability, XP, model, foreign custom data");
            verifyWorldTransfer(context.getLevel());
            LOGGER.info("PORT_NATIVE_TLM_WORLD_TRANSFER_PASS: real spawn, capture, manifest, UUID, inventory and duplicate-carrier block");
            return 1;
        } catch (Throwable failure) {
            LOGGER.error("PORT_NATIVE_TLM_ROUNDTRIP_FAIL: {}", failure.toString(), failure);
            return 0;
        }
    }

    /**
     * Disposable server-world integration fixture: this is a genuine TLM
     * spawn/discard/respawn, but NEVER a production player's maid.
     */
    private static void verifyWorldTransfer(net.minecraft.server.level.ServerLevel level) throws Exception {
        java.util.UUID unique = java.util.UUID.randomUUID();
        EntityMaid published = new EntityMaid(level);
        published.setUUID(unique);
        published.setOwnerUUID(OWNER);
        published.setFavorability(13);
        published.setExperience(37);
        published.getMaidInv().setStackInSlot(0, new ItemStack(Items.DIAMOND, 17));
        net.minecraft.core.BlockPos pos = level.getSharedSpawnPos().above(3);
        published.moveTo(pos, 0, 0);
        ItemStack carrier = new ItemStack(ProbeItems.CONTRACT_CARRIER.get());
        CompoundTag foreign = new CompoundTag();
        foreign.putString("third_party", "preserve");
        carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(foreign));
        carrier.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Native probe"));
        EntityMaid restored = null;
        if (!level.addFreshEntity(published) || level.getEntity(unique) != published)
            throw new IllegalStateException("Test TLM entity could not enter the server world");
        try {
            require(PortContractLifecycleTrial.capture(level, published, OWNER, "ProbeOwner", carrier),
                "Native world capture refused");
            require(published.isRemoved(), "Capture left the live entity published");
            var first = carrier.get(PortDataTypes.CONTRACT_REFERENCE.get());
            require(first != null, "Native reference absent");
            var journal = NativeTransferJournal.get(level.getServer());
            require(journal.read(unique).getUUID("ticket").equals(first.ticket()), "Durable capture intent missing");
            require(journal.read(unique).getString("kind").equals("CAPTURE"), "Wrong capture intent");
            require(ContractStackDataAccess.readContract(carrier).isEmpty(), "Snapshot leaked into legacy item NBT");
            require(NativeContractStore.get(level.getServer()).readStored(first).hasUUID("UUID"),
                "World snapshot missing");
            ItemStack persisted = ItemStack.parse(level.registryAccess(), carrier.save(level.registryAccess())).orElseThrow();
            require(first.equals(persisted.get(PortDataTypes.CONTRACT_REFERENCE.get())),
                "Native item persistent codec lost reference");
            require(foreign.equals(ContractStackDataAccess.readRoot(persisted)),
                "Persistent item codec changed foreign data");
            require(persisted.has(DataComponents.CUSTOM_NAME), "Persistent item codec lost custom name");
            ItemStack duplicatedItem = persisted.copy();
            // A channel component can round-trip, but must not implicitly authorize
            // either single-channel lifecycle or resonance until live recall is implemented.
            var mixed = persisted.copy();
            mixed.set(PortDataTypes.CONTRACT_CHANNELS.get(), new com.maidweapon.port.storage.ContractChannels(
                java.util.Optional.of(first), java.util.List.of(), java.util.Optional.empty()));
            var mixedReloaded = ItemStack.parse(level.registryAccess(), mixed.save(level.registryAccess())).orElseThrow();
            require(mixedReloaded.has(PortDataTypes.CONTRACT_CHANNELS.get()), "Channel component persistence failed");
            require(PortContractLifecycleTrial.manifest(level,pos.above(),OWNER,mixedReloaded)==null,
                "Mixed carrier unexpectedly granted summon authority");
            require(NativeResonanceService.currentStored(level.getServer(),OWNER,mixedReloaded)==-1,
                "Mixed carrier unexpectedly granted resonance authority");
            require(NativeAbilityService.executeProbe(level.getServer(),UUID.randomUUID(),carrier)==AbilityEngine.Result.UNAUTHORIZED,
                "Foreign owner executed probe skill");
            require(NativeAbilityService.executeProbe(level.getServer(),OWNER,carrier)==AbilityEngine.Result.SUCCESS,
                "Unified ability execution failed");
            require(NativeAbilityService.executeProbe(level.getServer(),OWNER,carrier)==AbilityEngine.Result.COOLDOWN,
                "Unified ability cooldown bypassed");
            var attempt = NativeResonanceService.newAttempt();
            require(NativeResonanceService.payStored(level.getServer(), OWNER, carrier, 125, attempt) == Payment.PAID,
                "Native resonance payment rejected");
            require(NativeResonanceService.payStored(level.getServer(), OWNER, duplicatedItem, 125, attempt) == Payment.ALREADY_PAID,
                "Native resonance attempt charged twice");
            require(NativeResonanceService.currentStored(level.getServer(), UUID.randomUUID(), carrier) == -1,
                "Foreign owner obtained resonance");
            restored = PortContractLifecycleTrial.manifest(level, pos.above(), OWNER, carrier);
            require(restored != null, "Native world manifestation rejected");
            require(journal.read(unique).getString("kind").equals("MANIFEST"), "Durable manifest intent missing");
            require(NativeResonanceService.currentLive(level.getServer(), OWNER, restored) == 850,
                "Resonance balance lost between stored and live forms");
            require(NativeResonanceService.currentStored(level.getServer(), OWNER, duplicatedItem) == -1,
                "Stale carrier retained resonance authority");
            require(level.getEntity(unique) == restored, "Restored world entity identity lost");
            require(restored.getMaidInv().getStackInSlot(0).getCount() == 17,
                "World transfer lost diamond count");
            require(restored.getExperience() == 37, "World transfer lost experience");
            require(PortContractLifecycleTrial.manifest(level, pos.above(2), OWNER, duplicatedItem) == null,
                "Duplicated item created a second maid");
            require(!carrier.has(PortDataTypes.CONTRACT_REFERENCE.get()),
                "Successfully manifested carrier still contains a reference");
            require(PortContractLifecycleTrial.capture(level, restored, OWNER, "ProbeOwner", carrier),
                "Second capture refused");
            require(PortContractLifecycleTrial.manifest(level, pos.above(2), OWNER, duplicatedItem) == null,
                "Old ticket became valid again after recapture");
            restored = PortContractLifecycleTrial.manifest(level, pos.above(), OWNER, carrier);
            require(restored != null, "Second manifest refused");
            require(NativeResonanceService.currentLive(level.getServer(), OWNER, restored) == 850,
                "Recapture reset the resonance account");
            require(foreign.equals(ContractStackDataAccess.readRoot(carrier)), "Foreign data changed in lifecycle");
            require(carrier.has(DataComponents.CUSTOM_NAME), "Foreign vanilla component removed");
            require(restored.hasData(PortDataTypes.MAID_BINDING), "Binding attachment lost");
        } finally {
            if (!published.isRemoved()) published.discard();
            if (restored != null && !restored.isRemoved()) restored.discard();
        }
    }

    private static void require(boolean accepted, String message) {
        if (!accepted) throw new IllegalStateException(message);
    }
    private NativeTlmRoundTripCommand() {}
}
