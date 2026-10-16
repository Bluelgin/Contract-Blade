package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.system.fox.FoxSpiritLedger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ContractRecoveryJournalTest {
    @Test void incidentReceiptsSurviveRestartAndCannotBeMutatedThroughReadCopies() {
        var journal = ContractRecoveryJournal.load(new CompoundTag());
        var record = new CompoundTag(); record.putString("Status", "COMMITTING"); record.putBoolean("BackupSpent", true);
        record.putUUID("Owner", UUID.randomUUID()); record.putString("Maid", UUID.randomUUID().toString());
        journal.incident("binding", record); record.putBoolean("BackupSpent", false);
        var loaded = ContractRecoveryJournal.load(journal.save(new CompoundTag()));
        assertTrue(loaded.incident("binding").getBoolean("BackupSpent"));
        loaded.incident("binding").putString("Status", "PENDING");
        assertEquals("COMMITTING", loaded.incident("binding").getString("Status"));
    }
    @Test void transferReplayAuthorityMovesBetweenSealAndWeaponWithIndependentOwner() {
        var journal = ContractRecoveryJournal.load(new CompoundTag());
        var record = new CompoundTag(); record.putString("Kind", "SEAL"); record.putUUID("Token", UUID.randomUUID());
        journal.transfer("maid-a", record); record.putString("Kind", "WEAPON");
        assertEquals("SEAL", journal.transfer("maid-a").getString("Kind"));
        journal.transfer("maid-a", record); assertEquals("WEAPON", journal.transfer("maid-a").getString("Kind"));
        assertTrue(journal.transfer("maid-b").isEmpty());
    }
    @Test void unknownFutureSchemaIsKeptAndWritesAreRejected() {
        var raw = new CompoundTag(); raw.putInt("Schema", 42); raw.putString("Future", "last trusted data");
        var journal = ContractRecoveryJournal.load(raw);
        assertTrue(journal.blocksAbility("binding"));
        assertEquals(raw, journal.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> journal.incident("binding", new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> journal.transfer("maid", new CompoundTag()));
        assertEquals(raw, journal.save(new CompoundTag()));
    }
    @Test void foxLedgerRejectsTokenOwnerOriginAndSpiritConflicts() {
        var identity = new CompoundTag(); identity.putInt("Version", 1); identity.putString("SpiritId", "maid_weapon:fox_white");
        for (String key : new String[]{"OwnerUUID", "SpiritUUID", "OriginUUID", "Token"}) identity.putUUID(key, UUID.randomUUID());
        var ledger = FoxSpiritLedger.load(new CompoundTag()); ledger.record(identity, "WEAPON");
        assertTrue(ledger.owns(identity, "WEAPON")); assertFalse(ledger.owns(identity, "SEAL"));
        for (String key : new String[]{"OwnerUUID", "SpiritUUID", "OriginUUID", "Token"}) {
            var wrong = identity.copy(); wrong.putUUID(key, UUID.randomUUID()); assertFalse(ledger.owns(wrong, "WEAPON"));
        }
        var wrong = identity.copy(); wrong.putString("SpiritId", "maid_weapon:fox_black"); assertFalse(ledger.owns(wrong, "WEAPON"));
        var seal = identity.copy(); seal.putUUID("Token", UUID.randomUUID()); ledger.record(seal, "SEAL");
        assertFalse(ledger.owns(identity, "WEAPON")); assertTrue(FoxSpiritLedger.load(ledger.save(new CompoundTag())).owns(seal, "SEAL"));
    }
}
