package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.storage.ContractReference;
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
        try {
            if (live.level() != level || live.isRemoved() || !live.isAlive()
                    || level.getEntity(live.getUUID()) != live || !owner.equals(live.getOwnerUUID())
                    || !validCarrier(carrier) || carrier.has(PortDataTypes.CONTRACT_REFERENCE.get())
                    || !ContractStackDataAccess.readContract(carrier).isEmpty()) return false;
            store = NativeContractStore.get(level.getServer());
            UUID maid = live.getUUID();
            var journal = NativeTransferJournal.get(level.getServer());
            if (!journal.permits(maid)) return false;
            var v = store.inspect(maid);
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
            // Events receive a staged copy; foreign carrier modifications are not committed by this probe.
            CompoundTag snapshot = TlmNativeTransformBridge.captureSnapshot(live, carrier.copy());
            if (live.isRemoved() || level.getEntity(maid) != live || !owner.equals(live.getOwnerUUID())
                    || !validCarrier(carrier)
                    || carrier.has(PortDataTypes.CONTRACT_REFERENCE.get())
                    || !ContractStackDataAccess.readContract(carrier).isEmpty()) return false;
            ref = store.beginCapture(maid, owner, binding, op, snapshot);
            journal.writeIntent(ref, op, NativeTransferJournal.Kind.CAPTURE, snapshot);
            carrier.set(PortDataTypes.CONTRACT_REFERENCE.get(), ref);
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
            ref = carrier.get(PortDataTypes.CONTRACT_REFERENCE.get());
            if (ref == null || !owner.equals(ref.owner())
                    || !ContractStackDataAccess.readContract(carrier).isEmpty()) return null;
            if (!level.getWorldBorder().isWithinBounds(destination) || !level.hasChunkAt(destination)
                    || !level.getBlockState(destination).isAir()
                    || !level.getBlockState(destination.above()).isAir()) return null;
            for (ServerLevel world : level.getServer().getAllLevels())
                if (world.getEntity(ref.maid()) != null) return null;
            store = NativeContractStore.get(level.getServer());
            var journal = NativeTransferJournal.get(level.getServer());
            if (!journal.permits(ref.maid())) return null;
            CompoundTag snapshot = store.readStored(ref);
            store.beginManifest(ref, op);
            began = true;
            journal.writeIntent(ref, op, NativeTransferJournal.Kind.MANIFEST, snapshot);
            candidate = TlmNativeTransformBridge.prepareManifestCandidate(level, carrier.copy(),
                snapshot, owner, ref.maid());
            if (!candidate.hasData(PortDataTypes.MAID_BINDING)
                    || !ref.binding().equals(candidate.getData(PortDataTypes.MAID_BINDING)))
                throw new IllegalStateException("Native snapshot binding attachment lost or changed");
            candidate.moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, 0, 0);
            if (!ref.equals(carrier.get(PortDataTypes.CONTRACT_REFERENCE.get())))
                throw new IllegalStateException("Carrier changed during transform");
            publicationAttempted = true;
            if (!level.addFreshEntity(candidate) || level.getEntity(ref.maid()) != candidate)
                throw new IllegalStateException("World publication rejected");
            store.completeManifest(ref, op);
            carrier.remove(PortDataTypes.CONTRACT_REFERENCE.get());
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
                && stack.is(ProbeItems.CONTRACT_CARRIER.get())
                && !stack.has(PortDataTypes.CONTRACT_CHANNELS.get());
    }
    private PortContractLifecycleTrial() {}
}
