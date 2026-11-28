package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.ContractChannels;
import com.maidweapon.port.storage.NativeChannelService;
import java.util.Objects;
import com.maidweapon.port.storage.ContractStackDataAccess;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeTransferJournal;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Native component + server-side snapshot transfer, disposable worlds ONLY. */
public final class PortContractLifecycleTrial {
    private static final Logger LOG = LogUtils.getLogger();

    public static boolean capture(ServerLevel level, EntityMaid live, UUID owner,
                                  String ownerName, ItemStack carrier) {
        if (level == null || live == null || owner == null || !level.getServer().isSameThread()) return false;
        ContractReference ref = null;
        NativeContractStore store = null;
        UUID op = UUID.randomUUID();
        ContractChannels priorChannels = carrier == null ? null : carrier.get(PortDataTypes.CONTRACT_CHANNELS.get());
        ContractChannels writtenChannels = null;
        try {
            if (live.level() != level || live.isRemoved() || !live.isAlive()
                    || level.getEntity(live.getUUID()) != live || !owner.equals(live.getOwnerUUID())
                    || !validCarrier(carrier) || carrier.has(PortDataTypes.CONTRACT_REFERENCE.get())
                    || ContractStackDataAccess.hasOwnedData(carrier)) return false;
            store = NativeContractStore.get(level.getServer());
            UUID maid = live.getUUID();
            var journal = NativeTransferJournal.get(level.getServer());
            if (!journal.permits(maid)) return false;
            var v = store.inspect(maid);
            if (priorChannels != null) {
                if (NativeChannelService.recall(level.getServer(),owner,carrier,maid)==null) return false;
            } else if (store.hasLiveLease(maid)||(v!=null&&!store.originMatches(maid,java.util.Optional.empty()))) return false;
            UUID binding;
            if (v == null) {
                if (live.hasData(PortDataTypes.MAID_BINDING)) return false;
                binding = UUID.randomUUID();
                store.registerLive(maid, owner, binding);
                live.setData(PortDataTypes.MAID_BINDING, binding);
            } else {
                if (v.phase() != NativeContractStore.Phase.LIVE || !owner.equals(v.owner())
                        || !live.hasData(PortDataTypes.MAID_BINDING)
                        || !v.binding().equals(live.getData(PortDataTypes.MAID_BINDING))) return false;
                binding = v.binding();
            }
            if(com.maidweapon.port.oath.NativeOathService.burning(level.getServer(),owner,maid,binding))return false;
            // Events receive a staged copy; foreign carrier modifications are not committed by this probe.
            CompoundTag snapshot = TlmNativeTransformBridge.captureSnapshot(live, carrier.copy());
            if (live.isRemoved() || level.getEntity(maid) != live || !owner.equals(live.getOwnerUUID())
                    || !validCarrier(carrier)
                    || carrier.has(PortDataTypes.CONTRACT_REFERENCE.get())
                    || ContractStackDataAccess.hasOwnedData(carrier)
                    || !Objects.equals(priorChannels,carrier.get(PortDataTypes.CONTRACT_CHANNELS.get()))
                    || (priorChannels!=null && NativeChannelService.recall(level.getServer(),owner,carrier,maid)==null)) return false;
            ref = store.beginCapture(maid, owner, binding, op, snapshot);
            journal.writeIntent(ref, op, NativeTransferJournal.Kind.CAPTURE, snapshot);
            if (priorChannels == null) carrier.set(PortDataTypes.CONTRACT_REFERENCE.get(), ref);
            else {
                writtenChannels=priorChannels.replaceSelected(ref);
                carrier.set(PortDataTypes.CONTRACT_CHANNELS.get(),writtenChannels);
            }
            live.discard();
            if (!live.isRemoved()) throw new IllegalStateException("Entity not removed");
            store.completeCapture(ref, op);
            return true;
        } catch (Exception failure) {
            LOG.error("Native capture failed; pending records require evidence", failure);
            if (ref != null && store != null && !live.isRemoved()
                    && level.getEntity(live.getUUID()) == live) {
                try {
                    store.abortCaptureWithLiveEvidence(ref, op);
                    if (ref.equals(carrier.get(PortDataTypes.CONTRACT_REFERENCE.get())))
                        carrier.remove(PortDataTypes.CONTRACT_REFERENCE.get());
                    if (writtenChannels!=null && writtenChannels.equals(carrier.get(PortDataTypes.CONTRACT_CHANNELS.get())))
                        carrier.set(PortDataTypes.CONTRACT_CHANNELS.get(),priorChannels);
                } catch (RuntimeException unresolved) {
                    LOG.error("Native capture rollback unresolved", unresolved);
                }
            }
            return false;
        }
    }

    public static EntityMaid manifest(ServerLevel level, BlockPos destination, UUID owner, ItemStack carrier) {
        if (level == null || destination == null || owner == null || !level.getServer().isSameThread()
                || !validCarrier(carrier)) return null;
        ContractReference ref = null;
        NativeContractStore store = null;
        EntityMaid candidate = null;
        UUID op = UUID.randomUUID();
        boolean began = false;
        boolean publicationAttempted = false;
        try {
            ContractChannels channels = carrier.get(PortDataTypes.CONTRACT_CHANNELS.get());
            ref = NativeChannelService.selected(level.getServer(),owner,carrier);
            if (ref == null || !owner.equals(ref.owner())
                    || ContractStackDataAccess.hasOwnedData(carrier)) return null;
            if (!level.getWorldBorder().isWithinBounds(destination) || !level.hasChunkAt(destination)
                    || !level.getBlockState(destination).isAir()
                    || !level.getBlockState(destination.above()).isAir()) return null;
            for (ServerLevel world : level.getServer().getAllLevels())
                if (world.getEntity(ref.maid()) != null) return null;
            store = NativeContractStore.get(level.getServer());
            var journal = NativeTransferJournal.get(level.getServer());
            if (!journal.permits(ref.maid())) return null;
            CompoundTag snapshot = store.readStored(ref);
            candidate = TlmNativeTransformBridge.prepareManifestCandidate(level, carrier.copy(),
                snapshot, owner, ref.maid());
            if (!candidate.hasData(PortDataTypes.MAID_BINDING)
                    || !ref.binding().equals(candidate.getData(PortDataTypes.MAID_BINDING)))
                throw new IllegalStateException("Native snapshot binding attachment lost or changed");
            candidate.moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, 0, 0);
            if (!validCarrier(carrier) || ContractStackDataAccess.hasOwnedData(carrier)
                || !Objects.equals(channels,carrier.get(PortDataTypes.CONTRACT_CHANNELS.get()))
                || (channels==null && !ref.equals(carrier.get(PortDataTypes.CONTRACT_REFERENCE.get())))
                || (channels!=null && carrier.has(PortDataTypes.CONTRACT_REFERENCE.get())))
                throw new IllegalStateException("Carrier changed during transform");
            if (!level.noCollision(candidate)) throw new IllegalStateException("Manifestation collision");
            // Ordinary collision/transform validation must not replace a good CAPTURE
            // checkpoint with an unpublishable MANIFEST intent.
            store.beginManifest(ref, op);
            began = true;
            journal.writeIntent(ref, op, NativeTransferJournal.Kind.MANIFEST, snapshot);
            publicationAttempted = true;
            if (!level.addFreshEntity(candidate) || level.getEntity(ref.maid()) != candidate)
                throw new IllegalStateException("World publication rejected");
            store.completeManifest(ref, op, channels!=null);
            if (channels==null) carrier.remove(PortDataTypes.CONTRACT_REFERENCE.get());
            return candidate;
        } catch (Exception failure) {
            LOG.warn("Native manifest rejected; snapshot retained unless publication committed", failure);
            // Once publication has been attempted, an exception may hide a partial spawn.
            // Preserve the pending snapshot rather than trusting absence in loaded chunks.
            if (began && !publicationAttempted && ref != null && store != null) {
                boolean absent = true;
                for (ServerLevel world : level.getServer().getAllLevels())
                    if (world.getEntity(ref.maid()) != null) absent = false;
                if (absent) {
                    try { store.abortManifestWithStoredEvidence(ref, op); }
                    catch (RuntimeException unresolved) { LOG.error("Native manifest rollback unresolved", unresolved); }
                }
            }
            return null;
        }
    }

    private static boolean validCarrier(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
                && com.maidweapon.port.item.NativeCarriers.accepts(stack)
                && !(stack.has(PortDataTypes.CONTRACT_CHANNELS.get()) && stack.has(PortDataTypes.CONTRACT_REFERENCE.get()));
    }
    private PortContractLifecycleTrial() {}
}
