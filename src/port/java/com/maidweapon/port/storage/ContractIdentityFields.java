package com.maidweapon.port.storage;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Stable old-format identity transition. Does not consult a world or decide
 * whether a live entity exists; that remains the server lifecycle's job.
 */
public final class ContractIdentityFields {
    public static CompoundTag bindOnce(CompoundTag original, UUID owner, String ownerName,
                                        UUID maid, UUID binding) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(maid, "maid");
        Objects.requireNonNull(binding, "binding");
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("ownerName");
        CompoundTag next = original.copy();
        if (next.contains("OwnerUUID") && (!next.hasUUID("OwnerUUID")
                || !owner.equals(next.getUUID("OwnerUUID"))))
            throw new IllegalStateException("Owner identity mismatch");
        if (next.contains("MaidUUID") && !maid.toString().equals(next.getString("MaidUUID")))
            throw new IllegalStateException("Maid identity mismatch");
        if (next.contains("MaidBindingId") && !binding.toString().equals(next.getString("MaidBindingId")))
            throw new IllegalStateException("Binding identity mismatch");
        if (!next.hasUUID("OwnerUUID")) next.putUUID("OwnerUUID", owner);
        if (!next.contains("OwnerName")) next.putString("OwnerName", ownerName);
        if (!next.contains("MaidUUID")) next.putString("MaidUUID", maid.toString());
        if (!next.contains("MaidBindingId")) next.putString("MaidBindingId", binding.toString());
        return next;
    }

    public static boolean isExplicitOwner(CompoundTag contract, UUID claimant) {
        return contract != null && claimant != null
            && contract.hasUUID("OwnerUUID") && claimant.equals(contract.getUUID("OwnerUUID"));
    }

    public static boolean matchesMaid(CompoundTag contract, UUID maid) {
        return contract != null && maid != null
            && maid.toString().equals(contract.getString("MaidUUID"));
    }

    public static boolean isSuperseded(CompoundTag contract) {
        return contract != null && contract.getBoolean("MaidContractSuperseded");
    }

    private ContractIdentityFields() {}
}
