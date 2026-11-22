package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.storage.LegacyMaidSnapshotCodec;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Official TLM transform-event integration. Prepares and validates full
 * snapshots and candidate entities but does NOT publish/remove anything.
 *
 * This does not implement capture or manifest transactions. A world-wide
 * authority ledger and single-owner transaction are prerequisites.
 */
public final class TlmNativeTransformBridge {
    /**
     * Native TLM's film strips the inventory: DO NOT call ItemFilm.maidToFilm
     * or use its filtered data for a full Contract Blade snapshot.
     */
    public static CompoundTag captureSnapshot(EntityMaid maid, ItemStack carrier) throws IOException {
        Objects.requireNonNull(carrier, "carrier");
        CompoundTag full = VerifiedTlmSnapshot.captureCandidate(maid);
        UUID owner = maid.getOwnerUUID();
        UUID uuid = maid.getUUID();
        NeoForge.EVENT_BUS.post(new MaidAndItemTransformEvent.ToItem(maid, carrier, full));
        VerifiedTlmSnapshot.requireIdentity(full, owner, uuid);
        LegacyMaidSnapshotCodec.validateEntity(full);
        requireInventory(full);
        return full.copy();
    }

    /**
     * Loads the full snapshot into a NON-PUBLISHED candidate maid.
     * Client fields are never granted authority. Identity is verified before
     * and after events and native TLM serialization.
     */
    public static EntityMaid prepareManifestCandidate(ServerLevel level, ItemStack carrier,
            CompoundTag snapshot, UUID owner, UUID maidUuid) throws IOException {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(carrier, "carrier");
        VerifiedTlmSnapshot.requireIdentity(snapshot, owner, maidUuid);
        LegacyMaidSnapshotCodec.validateEntity(snapshot);
        requireInventory(snapshot);
        CompoundTag candidateData = snapshot.copy();
        EntityMaid candidate = new EntityMaid(level);
        try {
            NeoForge.EVENT_BUS.post(new MaidAndItemTransformEvent.ToMaid(candidate, carrier, candidateData));
            VerifiedTlmSnapshot.requireIdentity(candidateData, owner, maidUuid);
            LegacyMaidSnapshotCodec.validateEntity(candidateData);
            requireInventory(candidateData);
            candidate.load(candidateData.copy());
            if (!maidUuid.equals(candidate.getUUID())
                    || !owner.equals(candidate.getOwnerUUID())) {
                throw new IOException("TLM native restore changed the contract identity");
            }
            return candidate;
        } catch (IOException error) {
            throw error;
        } catch (RuntimeException | LinkageError failure) {
            throw new IOException("Native TLM restore event or snapshot load failed", failure);
        }
    }

    private static void requireInventory(CompoundTag snapshot) throws IOException {
        if (!snapshot.contains(EntityMaid.MAID_INVENTORY_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)
                || !snapshot.contains(EntityMaid.MAID_BAUBLE_INVENTORY_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            throw new IOException("Full native TLM snapshot must retain both inventory channels");
        }
    }
    private TlmNativeTransformBridge() {}
}
