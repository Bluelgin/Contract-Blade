package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.ability.NativeAbilityService;
import com.maidweapon.port.domain.ability.AbilityEngine;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.storage.ContractChannels;
import com.maidweapon.port.storage.ContractReference;
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
        event.getDispatcher().register(Commands.literal("contractportverifyrestart")
            .requires(source->source.hasPermission(4)).executes(ctx->verifyRestart(ctx.getSource())));
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
            verifyChannelLifecycle(context.getLevel());
            verifyUltimateBaubles(context.getLevel());
            verifyCarrierInteraction(context.getLevel());
            prepareRestartFixture(context.getLevel());
            require(com.maidweapon.port.recovery.NativeCheckpointService.checkpoint(context.getServer())>0,"No sealed checkpoints persisted");
            LOGGER.info("PORT_NATIVE_TLM_WORLD_TRANSFER_PASS: real spawn, capture, manifest, UUID, inventory and duplicate-carrier block");
            return 1;
        } catch (Throwable failure) {
            LOGGER.error("PORT_NATIVE_TLM_ROUNDTRIP_FAIL: {}", failure.toString(), failure);
            return 0;
        }
    }

    /** Exercise the actual item entry points, not just the lifecycle service. */
    private static void verifyCarrierInteraction(net.minecraft.server.level.ServerLevel level) throws Exception {
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CarrierFixture"));
        player.getInventory().clearContent();player.getInventory().selected=0;
        var stack=new ItemStack(ProbeItems.CONTRACT_CARRIER.get());
        player.getInventory().setItem(0,stack);
        var maid=new EntityMaid(level);maid.setOwnerUUID(player.getUUID());
        var base=level.getSharedSpawnPos().above(20);
        var id=maid.getUUID();maid.moveTo(base.getX()+.5,base.getY(),base.getZ()+.5,0,0);
        level.getChunkAt(maid.blockPosition());
        require(level.addFreshEntity(maid)&&level.getEntity(id)==maid,"Player fixture maid not published in loaded chunk");
        require(player.isAlive()&&!player.isSpectator(),"Player fixture must be alive and non-spectating");
        var item=ProbeItems.CONTRACT_CARRIER.get();
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        try {
            require(item.interactLivingEntity(stack,player,maid,hand).consumesAction(),"Item capture failed");
            require(NativeResonanceService.payStored(level.getServer(),player.getUUID(),stack,125,NativeResonanceService.newAttempt())==Payment.PAID,"Item fixture payment failed");
            var stale=stack.copy();
            var before=NativeChannelService.displayReference(stack);
            var pos=base.offset(1,0,1);
            level.getChunkAt(pos);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),
                net.minecraft.core.Direction.UP,pos.below(),false);
            require(item.useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit)).consumesAction(),"Item manifest failed");
            require(before.equals(NativeChannelService.displayReference(stack)),"Item manifest lost display identity");
            var live=NativeChannelService.selectedLive(level.getServer(),player.getUUID(),stack);
            require(live!=null&&live.getUUID().equals(id),"Item live lease unavailable to HUD/oath");
            require(NativeResonanceService.currentLive(level.getServer(),player.getUUID(),live)==875,"Live HUD balance reset or unavailable");
            require(item.use(level,player,hand).getResult().consumesAction(),"Air recall failed");
            require(level.getEntity(id)==null&&NativeChannelService.selected(level.getServer(),player.getUUID(),stack)!=null,"Air recall left entity or lost authority");
            require(NativeChannelService.selected(level.getServer(),player.getUUID(),stale)==null,"Pre-manifest carrier copy revived");
            require(NativeResonanceService.currentStored(level.getServer(),player.getUUID(),stack)==875,"Recall reset resonance");
            LOGGER.info("PORT_NATIVE_PLAYER_CARRIER_PASS: item capture, persistent live identity, HUD balance, air recall, stale copy rejected");
        } finally {
            player.getInventory().clearContent();
            var remaining=level.getEntity(id);if(remaining!=null)remaining.discard();
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
            var sealedView=mixedReloaded.copy();
            sealedView.remove(PortDataTypes.CONTRACT_REFERENCE.get());
            require(NativeChannelService.select(level.getServer(),OWNER,sealedView,java.util.Optional.empty()),
                "Sealed external selection rejected");
            require(NativeResonanceService.currentStored(level.getServer(),OWNER,sealedView)==1000,
                "Sealed channel did not resolve the shared resonance account");
            require(NativeResonanceService.currentStored(level.getServer(),UUID.randomUUID(),sealedView)==-1,
                "Foreign owner accessed sealed channel");
            // The sealed view now supports manifestation. Leave the original single-reference fixture intact.
            require(NativeAbilityService.executeProbe(level.getServer(),UUID.randomUUID(),carrier)==AbilityEngine.Result.UNAUTHORIZED,
                "Foreign owner executed probe skill");
            require(NativeAbilityService.executeProbe(level.getServer(),OWNER,carrier)==AbilityEngine.Result.SUCCESS,
                "Unified ability execution failed");
            require(NativeAbilityService.executeProbe(level.getServer(),OWNER,carrier)==AbilityEngine.Result.COOLDOWN,
                "Unified ability cooldown bypassed");
            require(NativeAbilityService.executeProbe(level.getServer(),OWNER,sealedView)==AbilityEngine.Result.COOLDOWN,
                "Channel view bypassed the shared ability cooldown");
            var attempt = NativeResonanceService.newAttempt();
            require(NativeResonanceService.payStored(level.getServer(), OWNER, carrier, 125, attempt) == Payment.PAID,
                "Native resonance payment rejected");
            require(NativeResonanceService.payStored(level.getServer(), OWNER, duplicatedItem, 125, attempt) == Payment.ALREADY_PAID,
                "Native resonance attempt charged twice");
            require(NativeResonanceService.currentStored(level.getServer(), UUID.randomUUID(), carrier) == -1,
                "Foreign owner obtained resonance");
            require(NativeResonanceService.currentStored(level.getServer(),OWNER,sealedView)==850,
                "Channel view diverged from the authoritative pool");
            restored = PortContractLifecycleTrial.manifest(level, pos.above(), OWNER, carrier);
            require(restored != null, "Native world manifestation rejected");
            require(NativeResonanceService.currentStored(level.getServer(),OWNER,sealedView)==-1,
                "Stale sealed channel retained authority");
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

    private static void verifyChannelLifecycle(net.minecraft.server.level.ServerLevel level) throws Exception {
        var server=level.getServer();
        var pos=level.getSharedSpawnPos().above(3);
        var a=new EntityMaid(level);EntityMaid b=null;
        a.setOwnerUUID(OWNER);
        a.getMaidInv().setStackInSlot(0,new ItemStack(Items.DIAMOND,7));
        a.moveTo(pos,0,0);
        UUID au=a.getUUID(),grant=UUID.randomUUID(),bu=NativeContractStore.intrinsicMaid(grant);
        var carrier=new ItemStack(ProbeItems.CONTRACT_CARRIER.get());
        var second=new ItemStack(ProbeItems.CONTRACT_CARRIER.get());
        var spiritId=NativeIntrinsicSpirits.PROBE_ID;
        var definition=new NativeIntrinsicSpirits.Definition(spiritId,"Fixture Spirit",(p,s)->false,m->m.getMaidInv().setStackInSlot(0,new ItemStack(Items.EMERALD,11)));
        try {
            require(level.addFreshEntity(a),"External fixture could not spawn");
            require(PortContractLifecycleTrial.capture(level,a,OWNER,"ProbeOwner",carrier),"External capture failed");
            require(NativeChannelService.convertExternal(server,OWNER,carrier),"External conversion failed");
            require(NativeIntrinsicSpirits.create(level,OWNER,carrier,definition,grant),"Native intrinsic creation failed");
            require(!NativeIntrinsicSpirits.create(level,OWNER,second,definition,grant),"Intrinsic grant replay succeeded");
            var old=carrier.get(PortDataTypes.CONTRACT_CHANNELS.get());
            var intrinsic=old.intrinsic().getFirst().reference();
            second.set(PortDataTypes.CONTRACT_REFERENCE.get(),intrinsic);
            require(NativeChannelService.selected(server,OWNER,second)==null,"Intrinsic accepted as external");
            var forged=carrier.copy();
            var fakeId=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("maid_weapon_port_probe","forged");
            forged.set(PortDataTypes.CONTRACT_CHANNELS.get(),new ContractChannels(old.external(),
                java.util.List.of(new ContractChannels.Intrinsic(fakeId,intrinsic)),java.util.Optional.of(fakeId)));
            require(NativeChannelService.selected(server,OWNER,forged)==null,"Forged origin accepted");
            var stale=carrier.copy();
            require(NativeResonanceService.payStored(server,OWNER,carrier,125,NativeResonanceService.newAttempt())==Payment.PAID,
                "External pool payment failed");
            a=PortContractLifecycleTrial.manifest(level,pos,OWNER,carrier);
            require(a!=null&&au.equals(a.getUUID()),"External channel manifestation failed");
            require(a.getMaidInv().getStackInSlot(0).getCount()==7,"External inventory lost");
            require(NativeChannelService.selectedLive(server,OWNER,carrier)==a,"Live selected channel not resolved");
            var entries=NativeChannelService.recoveryEntries(server,OWNER,carrier);
            require(entries.size()==2&&entries.stream().anyMatch(e->e.live()!=null)&&entries.stream().anyMatch(e->e.live()==null),"Live and dormant recovery entries not resolved independently");
            require(NativeResonanceService.recoverLive(server,OWNER,a,5)==880,"Live resonance recovery failed");
            require(!NativeChannelService.select(server,OWNER,carrier,java.util.Optional.of(spiritId)),"Switched while live");
            require(PortContractLifecycleTrial.manifest(level,pos,OWNER,stale)==null,"Duplicate channel spawned");
            require(!PortContractLifecycleTrial.capture(level,a,OWNER,"ProbeOwner",new ItemStack(ProbeItems.CONTRACT_CARRIER.get())),
                "Empty carrier stole live channel lease");
            require(PortContractLifecycleTrial.capture(level,a,OWNER,"ProbeOwner",carrier),"External recall failed");
            require(NativeResonanceService.currentStored(server,OWNER,carrier)==880,"External pool reset");
            require(NativeChannelService.selected(server,OWNER,stale)==null,"Old copied channel remained authorized");
            require(NativeChannelService.recoveryEntries(server,OWNER,stale).isEmpty(),"Stale channel recovered resources");
            require(NativeChannelService.selectedLive(server,OWNER,stale)==null,"Old copied channel retained live authority");
            require(NativeChannelService.cycle(server,OWNER,carrier),"Intrinsic channel selection failed");
            require(NativeResonanceService.currentStored(server,OWNER,carrier)==1000,"Intrinsic pool not independent");
            b=PortContractLifecycleTrial.manifest(level,pos.above(),OWNER,carrier);
            require(b!=null&&bu.equals(b.getUUID()),"Intrinsic fixture manifestation failed");
            require(b.getMaidInv().getStackInSlot(0).getCount()==11,"Intrinsic inventory lost");
            require(PortContractLifecycleTrial.capture(level,b,OWNER,"ProbeOwner",carrier),"Intrinsic recall failed");
            require(NativeChannelService.cycle(server,OWNER,carrier),"Return to external failed");
            require(NativeResonanceService.currentStored(server,OWNER,carrier)==880,"External balance lost after switching");
            require(NativeChannelService.selected(server,OWNER,second)==null,"Old intrinsic reference remained valid");
            LOGGER.info("PORT_NATIVE_CHANNEL_LIFECYCLE_PASS: independent pools, real two-channel spawn/recall, stale tickets, live switch denial");
        } finally {
            var x=level.getEntity(au);if(x!=null)x.discard();
            var y=level.getEntity(bu);if(y!=null)y.discard();
        }
    }

    private static void verifyUltimateBaubles(net.minecraft.server.level.ServerLevel level) throws Exception {
        var server=level.getServer();
        var pos=level.getSharedSpawnPos().above(4);
        var maid=new EntityMaid(level);maid.setOwnerUUID(OWNER);maid.moveTo(pos,0,0);
        UUID id=maid.getUUID();
        var carrier=new ItemStack(ProbeItems.CONTRACT_CARRIER.get());
        var oath=new ItemStack(ProbeItems.OATH_RING.get());
        var endless=new ItemStack(ProbeItems.ENDLESS_CONTRACT.get());
        require(maid.getMaidBauble().isItemValid(0,oath)&&maid.getMaidBauble().isItemValid(0,endless),
            "Native TLM ultimate baubles not registered");
        require(maid.getMaidBauble().getSlots()>=2,"Fixture requires two native bauble slots");
        maid.getMaidBauble().setStackInSlot(0,endless);
        maid.getMaidBauble().setStackInSlot(1,oath);
        try{
            require(level.addFreshEntity(maid),"Ultimate fixture spawn failed");
            require(PortContractLifecycleTrial.capture(level,maid,OWNER,"ProbeOwner",carrier),"Ultimate capture failed");
            var first=NativeResonanceService.newAttempt();
            require(NativeResonanceService.payStored(server,OWNER,carrier,100,first)==Payment.WAIVED,"Endless stored waiver missing");
            require(NativeResonanceService.payStored(server,OWNER,carrier,100,first)==Payment.ALREADY_PAID,"Waived attempt settled twice");
            require(NativeResonanceService.currentStored(server,OWNER,carrier)==1000,"Waiver drained resource");
            maid=PortContractLifecycleTrial.manifest(level,pos,OWNER,carrier);
            require(maid!=null,"Ultimate manifest failed");
            require(NativeResonanceService.payLive(server,OWNER,maid,100,NativeResonanceService.newAttempt())==Payment.WAIVED,"Endless live waiver missing");
            require(NativeResonanceService.burnLive(server,OWNER,maid,100,NativeResonanceService.newAttempt())==Payment.PAID,"Upkeep bypassed payment");
            maid.getMaidBauble().setStackInSlot(0,oath);maid.getMaidBauble().setStackInSlot(1,endless);
            require(PortContractLifecycleTrial.capture(level,maid,OWNER,"ProbeOwner",carrier),"Ultimate recapture failed");
            require(NativeResonanceService.payStored(server,OWNER,carrier,100,NativeResonanceService.newAttempt())==Payment.PAID,"Stale bauble cache or wrong slot order");
            require(NativeResonanceService.currentStored(server,OWNER,carrier)==800,"Ultimate account continuity lost");
            LOGGER.info("PORT_NATIVE_ULTIMATE_BAUBLES_PASS: native registration, first slot, stored/live waiver, burn bypass, cache rotation");
        }finally{var current=level.getEntity(id);if(current!=null)current.discard();}
    }
    private static java.nio.file.Path restartFixture(net.minecraft.server.MinecraftServer server){
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("contract-port-restart-fixture.nbt");
    }
    private static void prepareRestartFixture(net.minecraft.server.level.ServerLevel level) throws Exception{
        var server=level.getServer();var path=restartFixture(server);
        require(java.nio.file.Files.notExists(path),"Previous restart fixture exists; use fresh disposable world");
        var pos=level.getSharedSpawnPos().above(4);
        var maid=new EntityMaid(level);maid.setOwnerUUID(OWNER);maid.moveTo(pos,0,0);
        maid.getMaidInv().setStackInSlot(0,new ItemStack(Items.DIAMOND,29));
        maid.setExperience(53);
        var carrier=new ItemStack(ProbeItems.CONTRACT_CARRIER.get());
        try{
            require(level.addFreshEntity(maid),"Restart fixture spawn failed");
            require(PortContractLifecycleTrial.capture(level,maid,OWNER,"ProbeOwner",carrier),"Restart capture failed");
            require(NativeChannelService.convertExternal(server,OWNER,carrier),"Restart channel conversion failed");
            require(NativeResonanceService.payStored(server,OWNER,carrier,137,NativeResonanceService.newAttempt())==Payment.PAID,"Restart pool payment failed");
            var root=new CompoundTag();root.put("carrier",carrier.save(level.registryAccess()));root.putUUID("maid",maid.getUUID());
            net.minecraft.nbt.NbtIo.writeCompressed(root,path);
            LOGGER.info("PORT_NATIVE_RESTART_FIXTURE_PREPARED: saved carrier, 29 diamonds, 53 XP, 863 resonance");
        }finally{if(!maid.isRemoved())maid.discard();}
    }
    private static int verifyRestart(CommandSourceStack source){
        var level=source.getLevel();var server=level.getServer();EntityMaid live=null;
        try{
            var path=restartFixture(server);
            var tag=net.minecraft.nbt.NbtIo.readCompressed(path,net.minecraft.nbt.NbtAccounter.create(2L*1024*1024));
            var carrier=ItemStack.parse(level.registryAccess(),tag.getCompound("carrier")).orElseThrow();
            var stale=carrier.copy();var ref=NativeChannelService.selected(server,OWNER,carrier);
            require(ref!=null&&ref.maid().equals(tag.getUUID("maid")),"Saved checkpoint was not reauthorized");
            require(NativeResonanceService.currentStored(server,OWNER,carrier)==863,"Restart lost pool balance");
            require(NativeResonanceService.currentStored(server,UUID.randomUUID(),carrier)==-1,"Restart granted foreign authority");
            live=PortContractLifecycleTrial.manifest(level,level.getSharedSpawnPos().above(4),OWNER,carrier);
            require(live!=null&&ref.maid().equals(live.getUUID()),"Restart manifestation failed");
            require(live.getMaidInv().getStackInSlot(0).getCount()==29&&live.getExperience()==53,"Restart lost entity data");
            require(NativeResonanceService.currentLive(server,OWNER,live)==863,"Restart live balance wrong");
            require(PortContractLifecycleTrial.capture(level,live,OWNER,"ProbeOwner",carrier),"Restart recall failed");
            require(NativeChannelService.selected(server,OWNER,stale)==null,"Pre-restart ticket survived recall");
            require(NativeResonanceService.currentStored(server,OWNER,carrier)==863,"Restart recall reset pool");
            java.nio.file.Files.delete(path);
            LOGGER.info("PORT_NATIVE_RESTART_PASS: actual second boot, item and ledger reload, native inventory and pool, recall and stale ticket");
            return 1;
        }catch(Throwable failure){
            LOGGER.error("PORT_NATIVE_RESTART_FAIL",failure);return 0;
        }finally{if(live!=null&&!live.isRemoved())live.discard();}
    }
    private static void require(boolean accepted, String message) {
        if (!accepted) throw new IllegalStateException(message);
    }
    private NativeTlmRoundTripCommand() {}
}
