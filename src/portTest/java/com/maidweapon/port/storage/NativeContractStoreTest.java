package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class NativeContractStoreTest {
    private final UUID maid = UUID.randomUUID(), owner = UUID.randomUUID(), binding = UUID.randomUUID();
    private CompoundTag snapshot() {
        CompoundTag s = new CompoundTag();
        s.putUUID("UUID", maid);
        s.putUUID("Owner", owner);
        s.putString("foreign", "retained");
        return s;
    }
    @Test void referenceHasBoundedStreamAndPersistentRoundTrip() {
        var r = new ContractReference(maid, owner, binding, UUID.randomUUID());
        var tag = ContractReference.CODEC.encodeStart(NbtOps.INSTANCE, r).result().orElseThrow();
        assertEquals(r, ContractReference.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow());
        var buf = Unpooled.buffer();
        try {
            ContractReference.STREAM_CODEC.encode(buf, r);
            assertEquals(64, buf.readableBytes());
            assertEquals(r, ContractReference.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void snapshotAndPendingStateReloadTogetherAndStaleTicketFails() throws Exception {
        var store = new NativeContractStore();
        store.registerLive(maid, owner, binding);
        var op = UUID.randomUUID();
        var original = snapshot();
        var first = store.beginCapture(maid, owner, binding, op, original);
        original.putString("foreign", "modified");
        var reloaded = NativeContractStore.load(store.save(new CompoundTag(), null), null);
        assertEquals(NativeContractStore.Phase.CAPTURING, reloaded.inspect(maid).phase());
        assertThrows(IllegalStateException.class, () -> reloaded.readStored(first));
        assertThrows(IllegalStateException.class, () -> reloaded.completeCapture(first, UUID.randomUUID()));
        reloaded.completeCapture(first, op);
        var decoded = reloaded.readStored(first);
        assertEquals("retained", decoded.getString("foreign"));
        decoded.remove("foreign");
        assertEquals("retained", reloaded.readStored(first).getString("foreign"));
        UUID manifest = UUID.randomUUID();
        reloaded.beginManifest(first, manifest);
        reloaded.abortManifestWithStoredEvidence(first, manifest);
        assertEquals("retained", reloaded.readStored(first).getString("foreign"));
        reloaded.beginManifest(first, manifest);
        reloaded.completeManifest(first, manifest);
        assertThrows(IllegalStateException.class, () -> reloaded.readStored(first));
        UUID capture2 = UUID.randomUUID();
        var second = reloaded.beginCapture(maid, owner, binding, capture2, snapshot());
        reloaded.completeCapture(second, capture2);
        assertNotEquals(first.ticket(), second.ticket());
        assertThrows(IllegalStateException.class, () -> reloaded.readStored(first));
        assertThrows(IllegalStateException.class, () -> reloaded.readStored(new ContractReference(
            maid, UUID.randomUUID(), binding, second.ticket())));
        assertEquals(snapshot(), reloaded.readStored(second));
    }
    @Test void retainedLiveLeaseSurvivesAbortAndRotatesOnRecall() throws Exception {
        var store=new NativeContractStore();store.registerLive(maid,owner,binding);
        var op=UUID.randomUUID();var first=store.beginCapture(maid,owner,binding,op,snapshot());
        store.completeCapture(first,op);
        var manifest=UUID.randomUUID();store.beginManifest(first,manifest);store.completeManifest(first,manifest,true);
        assertTrue(store.matchesLiveLease(first));
        var retry=UUID.randomUUID();var next=store.beginCapture(maid,owner,binding,retry,snapshot());
        store.abortCaptureWithLiveEvidence(next,retry);
        assertTrue(store.matchesLiveLease(first));
        var again=UUID.randomUUID();var newest=store.beginCapture(maid,owner,binding,again,snapshot());
        store.completeCapture(newest,again);
        assertFalse(store.matchesLiveLease(first));
        assertThrows(IllegalStateException.class,()->store.readStored(first));
        assertEquals(snapshot(),store.readStored(newest));
    }
    @Test void unknownSchemaPreservedAndInvalidSnapshotDoesNotMutate() {
        var future = new CompoundTag();
        future.putInt("schema", 99);
        future.putString("unknown", "keep");
        var rejected = NativeContractStore.load(future, null);
        assertThrows(IllegalStateException.class, () -> rejected.registerLive(maid, owner, binding));
        assertEquals(future, rejected.save(new CompoundTag(), null));
        var store = new NativeContractStore();
        store.registerLive(maid, owner, binding);
        assertThrows(java.io.IOException.class, () -> store.beginCapture(maid, owner, binding,
            UUID.randomUUID(), new CompoundTag()));
        assertEquals(NativeContractStore.Phase.LIVE, store.inspect(maid).phase());
    }
}
