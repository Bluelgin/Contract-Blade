package com.maidweapon.port.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.nbt.CompoundTag;
import static org.junit.jupiter.api.Assertions.*;

class NativeTransferJournalTest {
    @TempDir Path temp;
    @Test void durableBackupAndRestartQuarantine() throws Exception {
        var maid=UUID.randomUUID(); var owner=UUID.randomUUID();
        var ref=new ContractReference(maid,owner,UUID.randomUUID(),UUID.randomUUID());
        var snapshot=new CompoundTag(); snapshot.putUUID("UUID",maid); snapshot.putUUID("Owner",owner);
        snapshot.putString("foreign","keep");
        var dir=temp.resolve("journal"); var first=new NativeTransferJournal(dir,UUID.randomUUID());
        assertTrue(first.permits(maid));
        var op=UUID.randomUUID();
        first.writeIntent(ref,op,NativeTransferJournal.Kind.CAPTURE,snapshot);
        snapshot.remove("foreign");
        assertTrue(first.permits(maid));
        assertEquals("keep",first.read(maid).getCompound("snapshot").getString("foreign"));
        assertEquals(op,first.read(maid).getUUID("operation"));
        var restarted=new NativeTransferJournal(dir,UUID.randomUUID());
        assertFalse(restarted.permits(maid));
        assertThrows(java.io.IOException.class,()->restarted.writeIntent(ref,UUID.randomUUID(),NativeTransferJournal.Kind.MANIFEST,snapshot));
        assertEquals(op,restarted.read(maid).getUUID("operation"));
    }
    @Test void previousManifestCanBeAcknowledgedOnlyAsMatchingLive() throws Exception {
        var maid=UUID.randomUUID();var owner=UUID.randomUUID();var binding=UUID.randomUUID();
        var ref=new ContractReference(maid,owner,binding,UUID.randomUUID());
        var s=new CompoundTag();s.putUUID("UUID",maid);s.putUUID("Owner",owner);
        var dir=temp.resolve("journal");var a=new NativeTransferJournal(dir,UUID.randomUUID());
        a.writeIntent(ref,UUID.randomUUID(),NativeTransferJournal.Kind.MANIFEST,s);
        var b=new NativeTransferJournal(dir,UUID.randomUUID());
        assertFalse(b.permits(maid));
        assertFalse(b.acknowledgeObservedLive(new NativeContractStore.View(maid,owner,binding,NativeContractStore.Phase.STORED,ref.ticket(),null),ref));
        assertFalse(b.acknowledgeObservedLive(new NativeContractStore.View(maid,owner,UUID.randomUUID(),NativeContractStore.Phase.LIVE,null,null),ref));
        assertFalse(b.acknowledgeObservedLive(new NativeContractStore.View(maid,owner,binding,NativeContractStore.Phase.LIVE,null,null),
            new ContractReference(maid,owner,binding,UUID.randomUUID())));
        assertTrue(b.acknowledgeObservedLive(new NativeContractStore.View(maid,owner,binding,NativeContractStore.Phase.LIVE,null,null),ref));
        assertTrue(b.permits(maid));
        assertFalse(new NativeTransferJournal(dir,UUID.randomUUID()).permits(maid));
    }
    @Test void flushedCaptureRequiresExactLedgerAndIsInvalidatedByNextIntent() throws Exception{
        var maid=UUID.randomUUID();var owner=UUID.randomUUID();var binding=UUID.randomUUID();
        var s=new CompoundTag();s.putUUID("UUID",maid);s.putUUID("Owner",owner);
        var store=new NativeContractStore();store.registerLive(maid,owner,binding);
        var op=UUID.randomUUID();var ref=store.beginCapture(maid,owner,binding,op,s);
        var dir=temp.resolve("checkpoint");var a=new NativeTransferJournal(dir,UUID.randomUUID());
        a.writeIntent(ref,op,NativeTransferJournal.Kind.CAPTURE,s);
        assertThrows(IllegalStateException.class,()->a.markStoredAfterFlush(store,ref));
        store.completeCapture(ref,op);
        var before=new NativeTransferJournal(dir,UUID.randomUUID());
        assertFalse(before.permitsStored(store,ref));
        assertTrue(a.markStoredAfterFlush(store,ref));
        var reloaded=NativeContractStore.load(store.save(new CompoundTag(),null),null);
        var b=new NativeTransferJournal(dir,UUID.randomUUID());
        assertTrue(b.permitsStored(reloaded,ref));
        assertFalse(b.permitsStored(reloaded,new ContractReference(maid,owner,binding,UUID.randomUUID())));
        var manifest=UUID.randomUUID();
        b.writeIntent(ref,manifest,NativeTransferJournal.Kind.MANIFEST,s);
        assertFalse(new NativeTransferJournal(dir,UUID.randomUUID()).permitsStored(reloaded,ref));
    }
    @Test void malformedJournalDoesNotAuthorize() throws Exception {
        var maid=UUID.randomUUID(); var dir=temp.resolve("journal"); Files.createDirectories(dir);
        Files.writeString(dir.resolve(maid+".nbt"),"corrupt");
        var journal=new NativeTransferJournal(dir,UUID.randomUUID());
        assertThrows(java.io.IOException.class,()->journal.permits(maid));
    }
}
