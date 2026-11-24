package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.storage.ContractNbtBoundsGuard;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Read-only native TLM capture PREVIEW for the 1.21.1 port. It never removes,
 * publishes, summons or modifies an entity or a contract weapon.
 */
public final class VerifiedTlmSnapshot {
    public static CompoundTag captureCandidate(EntityMaid maid) throws IOException {
        if (maid == null || maid.level().isClientSide)
            throw new IOException("Only live server-side TLM maids may be serialized");
        UUID owner = maid.getOwnerUUID();
        if (owner == null) throw new IOException("Unowned maid has no contract authority");
        UUID uuid = maid.getUUID();
        CompoundTag snapshot = maid.saveWithoutId(new CompoundTag());
        requireIdentity(snapshot, owner, uuid);
        sanitizeMotion(snapshot);
        ContractNbtBoundsGuard.validate(snapshot);
        return snapshot;
    }

    public static void requireIdentity(CompoundTag snapshot, UUID owner, UUID uuid) throws IOException {
        if (snapshot == null || owner == null || uuid == null
            || !snapshot.hasUUID("UUID") || !snapshot.getUUID("UUID").equals(uuid)
            || !snapshot.hasUUID("Owner") || !snapshot.getUUID("Owner").equals(owner))
            throw new IOException("Native TLM identity or owner validation failed");
    }

    private static void sanitizeMotion(CompoundTag tag) {
        for (String key : new String[] {
            "Pos", "Motion", "Rotation", "FallDistance", "Fire",
            "Air", "OnGround", "PortalCooldown"
        }) tag.remove(key);
    }

    private VerifiedTlmSnapshot() {}
}
