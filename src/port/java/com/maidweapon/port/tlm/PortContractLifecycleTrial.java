package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.storage.ContractIdentityFields;
import com.maidweapon.port.storage.ContractStackDataAccess;
import com.maidweapon.port.storage.LegacyMaidSnapshotCodec;
import com.maidweapon.port.storage.PortContractAuthoritySavedData;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

/**
 * Developer-only capture/manifest transaction prototype, on the isolated
 * maid_weapon_port_probe carrier. NEVER migrate a player world with it.
 *
 * All authority mutations happen on a server thread; incomplete transactions
 * stay blocked after restart. They are NOT auto-rolled back without evidence.
 */
public final class PortContractLifecycleTrial {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean capture(ServerLevel level, EntityMaid live, UUID owner,
                                  String ownerName, ItemStack carrier) {
        if (level == null || live == null || owner == null || ownerName == null
                || !level.getServer().isSameThread() || live.level() != level
                || live.isRemoved() || !live.isAlive()
                || !owner.equals(live.getOwnerUUID()) || !validCarrier(carrier)
                || !ContractStackDataAccess.readContract(carrier).isEmpty()) return false;

        var ledger = PortContractAuthoritySavedData.get(level.getServer());
        UUID maid = live.getUUID();
        var existing = ledger.inspect(maid);
        UUID binding;
        if (existing == null) {
            binding = UUID.randomUUID();
            ledger.registerVerifiedLive(maid, owner, binding);
        } else {
            if (!existing.owner().equals(owner)
                    || existing.phase() != PortContractAuthoritySavedData.Phase.LIVE)
                return false;
            binding = existing.binding();
        }

        CustomData previousComponent = carrier.get(DataComponents.CUSTOM_DATA);
        UUID operation = UUID.randomUUID();
        boolean began = false;
        try {
            CompoundTag full = TlmNativeTransformBridge.captureSnapshot(live, carrier);
            CompoundTag contract = ContractIdentityFields.bindOnce(
                new CompoundTag(), owner, ownerName, maid, binding);
            contract.put("MaidData", new CompoundTag());
            contract.putInt("ContractCarrierStorageVersion", 1);
            LegacyMaidSnapshotCodec.write(contract, full);
            ledger.beginCapture(maid, owner, binding, operation);
            began = true;
            ContractStackDataAccess.replaceContract(carrier, contract);

            live.discard();
            if (!live.isRemoved()) throw new IllegalStateException("Entity discard did not remove live maid");
            ledger.completeCapture(maid, owner, binding, operation);
            return true;
        } catch (Exception failure) {
            LOGGER.error("Probe capture failed for maid {}: {}", maid, failure.toString(), failure);
            if (!live.isRemoved()) {
                if (previousComponent == null) carrier.remove(DataComponents.CUSTOM_DATA);
                else carrier.set(DataComponents.CUSTOM_DATA, previousComponent);
                if (began) {
                    try { ledger.abortCaptureWithVerifiedLiveEntity(maid, owner, binding, operation); }
                    catch (RuntimeException unresolved) {
                        LOGGER.error("Probe capture rollback left unresolved ledger state", unresolved);
                    }
                }
            } else {
                // Never pretend the live entity exists after a failed post-removal callback.
                // Item snapshot and transaction marker remain for manual reconciliation.
                LOGGER.error("Entity was removed; retaining stored snapshot and pending ledger for manual recovery");
            }
            return false;
        }
    }

    public static EntityMaid manifest(ServerLevel level, BlockPos destination, UUID owner,
                                      ItemStack carrier) {
        if (level == null || destination == null || owner == null
                || !level.getServer().isSameThread() || !validCarrier(carrier)) return null;
        CompoundTag contract = ContractStackDataAccess.readContract(carrier);
        if (!ContractIdentityFields.isExplicitOwner(contract, owner)
                || ContractIdentityFields.isSuperseded(contract)
                || !LegacyMaidSnapshotCodec.contains(contract)) return null;
        UUID maid;
        UUID binding;
        try {
            maid = UUID.fromString(contract.getString("MaidUUID"));
            binding = UUID.fromString(contract.getString("MaidBindingId"));
        } catch (IllegalArgumentException invalid) {
            return null;
        }

        var ledger = PortContractAuthoritySavedData.get(level.getServer());
        var authority = ledger.inspect(maid);
        if (authority == null || authority.phase() != PortContractAuthoritySavedData.Phase.STORED
                || !owner.equals(authority.owner()) || !binding.equals(authority.binding()))
            return null;

        if (!level.getWorldBorder().isWithinBounds(destination)
                || !level.getBlockState(destination).isAir()
                || !level.getBlockState(destination.above()).isAir())
            return null;

        for (ServerLevel loadedWorld : level.getServer().getAllLevels())
            if (loadedWorld.getEntity(maid) != null) return null;

        EntityMaid candidate;
        try {
            CompoundTag stored = LegacyMaidSnapshotCodec.read(contract);
            candidate = TlmNativeTransformBridge.prepareManifestCandidate(level, carrier, stored, owner, maid);
            candidate.moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, 0, 0);
        } catch (Exception invalid) {
            LOGGER.warn("Probe manifest rejected invalid stored maid {}", maid, invalid);
            return null;
        }

        UUID operation = UUID.randomUUID();
        ledger.beginManifest(maid, owner, binding, operation);
        boolean published = false;
        try {
            boolean accepted = level.addFreshEntity(candidate);
            published = level.getEntity(maid) == candidate;
            if (!accepted || !published) {
                if (published) throw new IllegalStateException("Spawn result inconsistent");
                ledger.abortManifestWithVerifiedStoredSnapshot(maid, owner, binding, operation);
                return null;
            }

            ledger.completeManifest(maid, owner, binding, operation);
            ContractStackDataAccess.replaceContract(carrier, new CompoundTag());
            return candidate;
        } catch (Exception failure) {
            LOGGER.error("Probe manifest failed for maid {}: {}", maid, failure.toString(), failure);
            if (!published && level.getEntity(maid) != candidate) {
                try { ledger.abortManifestWithVerifiedStoredSnapshot(maid, owner, binding, operation); }
                catch (RuntimeException unresolved) {
                    LOGGER.error("Probe manifest rollback left unresolved ledger state", unresolved);
                }
            }
            // If published, preserve LIVE or MANIFESTING state; never try spawn again.
            return null;
        }
    }

    private static boolean validCarrier(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
                && stack.is(ProbeItems.CONTRACT_CARRIER.get());
    }
    private PortContractLifecycleTrial() {}
}
