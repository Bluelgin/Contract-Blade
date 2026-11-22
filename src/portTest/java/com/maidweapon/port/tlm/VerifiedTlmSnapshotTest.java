package com.maidweapon.port.tlm;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class VerifiedTlmSnapshotTest {
    private static final UUID OWNER = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID MAID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Test void acceptsMatchingServerIdentity() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", OWNER);
        tag.putUUID("UUID", MAID);
        assertDoesNotThrow(() -> VerifiedTlmSnapshot.requireIdentity(tag, OWNER, MAID));
    }

    @Test void refusesForeignOwnerOrReusedUuid() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", OWNER);
        tag.putUUID("UUID", MAID);
        assertThrows(IOException.class,
            () -> VerifiedTlmSnapshot.requireIdentity(tag, UUID.randomUUID(), MAID));
        assertThrows(IOException.class,
            () -> VerifiedTlmSnapshot.requireIdentity(tag, OWNER, UUID.randomUUID()));
    }

    @Test void refusesAbsentOwnerInsteadOfInventingOne() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("UUID", MAID);
        assertThrows(IOException.class,
            () -> VerifiedTlmSnapshot.requireIdentity(tag, OWNER, MAID));
    }
}
