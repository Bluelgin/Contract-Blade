package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PortContractAuthoritySavedDataTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MAID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID BINDING = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OP = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test void fullStateMachineSurvivesSerialization() {
        var ledger = new PortContractAuthoritySavedData();
        ledger.registerVerifiedLive(MAID, OWNER, BINDING);
        ledger.beginCapture(MAID, OWNER, BINDING, OP);
        var pending = PortContractAuthoritySavedData.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(PortContractAuthoritySavedData.Phase.CAPTURING, pending.inspect(MAID).phase());
        assertEquals(OP, pending.inspect(MAID).transaction());
        pending.completeCapture(MAID, OWNER, BINDING, OP);
        assertEquals(PortContractAuthoritySavedData.Phase.STORED, pending.inspect(MAID).phase());
        UUID second = UUID.fromString("55555555-5555-4555-8555-555555555555");
        pending.beginManifest(MAID, OWNER, BINDING, second);
        pending.completeManifest(MAID, OWNER, BINDING, second);
        assertEquals(PortContractAuthoritySavedData.Phase.LIVE, pending.inspect(MAID).phase());
        assertFalse(pending.inspect(MAID).unsettled());
    }

    @Test void cannotDuplicateAnExistingMaidOrManifestTwice() {
        var ledger = new PortContractAuthoritySavedData();
        ledger.registerVerifiedLive(MAID, OWNER, BINDING);
        assertThrows(IllegalStateException.class,
            () -> ledger.registerVerifiedLive(MAID, OWNER, BINDING));
        assertThrows(IllegalStateException.class,
            () -> ledger.beginManifest(MAID, OWNER, BINDING, OP));
        ledger.beginCapture(MAID, OWNER, BINDING, OP);
        assertThrows(IllegalStateException.class,
            () -> ledger.beginCapture(MAID, OWNER, BINDING, OP));
        assertThrows(IllegalStateException.class,
            () -> ledger.beginManifest(MAID, OWNER, BINDING, OP));
    }

    @Test void operationAndOwnershipCannotBeForged() {
        var ledger = new PortContractAuthoritySavedData();
        ledger.registerVerifiedLive(MAID, OWNER, BINDING);
        assertThrows(IllegalStateException.class,
            () -> ledger.beginCapture(MAID, UUID.randomUUID(), BINDING, OP));
        assertThrows(IllegalStateException.class,
            () -> ledger.beginCapture(MAID, OWNER, UUID.randomUUID(), OP));
        ledger.beginCapture(MAID, OWNER, BINDING, OP);
        assertThrows(IllegalStateException.class,
            () -> ledger.completeCapture(MAID, OWNER, BINDING, UUID.randomUUID()));
        assertTrue(ledger.inspect(MAID).unsettled());
        ledger.abortCaptureWithVerifiedLiveEntity(MAID, OWNER, BINDING, OP);
        assertEquals(PortContractAuthoritySavedData.Phase.LIVE, ledger.inspect(MAID).phase());
    }

    @Test void ambiguousRestartRequiresEvidenceNotAutomaticReset() {
        var ledger = new PortContractAuthoritySavedData();
        ledger.registerVerifiedLive(MAID, OWNER, BINDING);
        ledger.beginCapture(MAID, OWNER, BINDING, OP);
        var reloaded = PortContractAuthoritySavedData.load(ledger.save(new CompoundTag(), null), null);
        assertThrows(IllegalStateException.class,
            () -> reloaded.beginCapture(MAID, OWNER, BINDING, UUID.randomUUID()));
        assertThrows(IllegalStateException.class,
            () -> reloaded.beginManifest(MAID, OWNER, BINDING, UUID.randomUUID()));
    }

    @Test void unversionedPopulatedWorldDataIsPreservedWithoutMutation() {
        CompoundTag mystery = new CompoundTag();
        mystery.putString("UnrecognizedRecord", "must-survive");
        var ledger = PortContractAuthoritySavedData.load(mystery, null);
        assertFalse(ledger.writable());
        assertEquals(mystery, ledger.save(new CompoundTag(), null));
    }

    @Test void futureSchemaIsStrictlyReadOnlyAndPreserved() {
        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", 100);
        future.putString("FutureData", "no-loss");
        var ledger = PortContractAuthoritySavedData.load(future, null);
        assertFalse(ledger.writable());
        assertThrows(IllegalStateException.class,
            () -> ledger.registerVerifiedLive(MAID, OWNER, BINDING));
        assertEquals(future, ledger.save(new CompoundTag(), null));
    }
}
