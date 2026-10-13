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
        assertEquals(raw, journal.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> journal.incident("binding", new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> journal.transfer("maid", new CompoundTag()));
        assertEquals(raw, journal.save(new CompoundTag()));
    }
    @Test void legacyReviewArchiveCleanupIsIdempotent() {
        var journal = ContractRecoveryJournal.load(new CompoundTag());
        for (String state : new String[]{"REVIEW_REQUIRED", "COMMITTING", "PENDING"}) {
            var incident = new CompoundTag(); incident.putString("Status", state); incident.putBoolean("Confirmed", true);
            journal.incident("binding", incident);
            journal.clearIncident("binding"); journal.clearIncident("binding");
            assertTrue(journal.incident("binding").isEmpty());
        }
    }
    @Test void unloadedPresenceSurvivesRestartUntilActualCaptureOrRemoval() {
        var journal = ContractRecoveryJournal.load(new CompoundTag());
        journal.worldPresent("maid", true);
        var restarted = ContractRecoveryJournal.load(journal.save(new CompoundTag()));
        assertTrue(restarted.transfer("maid").getBoolean("WorldPresent"));
        restarted.worldPresent("maid", false);
        assertFalse(ContractRecoveryJournal.load(restarted.save(new CompoundTag())).transfer("maid").getBoolean("WorldPresent"));
        assertTrue(restarted.transfer("other").isEmpty());
    }
    @Test void unknownPresenceIsNotInventedByDetachAndSurvivesRestart() {
        var journal = ContractRecoveryJournal.load(new CompoundTag());
        assertEquals(ContractRecoveryJournal.Presence.UNKNOWN, journal.presence("legacy"));
        journal.destination("legacy", UUID.randomUUID(), "binding", "FREE_MAID",
                journal.presence("legacy"), null);
        var restarted = ContractRecoveryJournal.load(journal.save(new CompoundTag()));
        assertEquals(ContractRecoveryJournal.Presence.UNKNOWN, restarted.presence("legacy"));
        assertFalse(restarted.transfer("legacy").contains("WorldPresent"));
        assertEquals("FREE_MAID", restarted.transfer("legacy").getString("Kind"));
    }
    @Test void namedPresenceOperationsPreserveDestinationAndCannotAffectOtherMaids() {
        var journal = ContractRecoveryJournal.load(new CompoundTag());
        UUID owner = UUID.randomUUID();
        journal.destination("maid", owner, "binding", "WEAPON", ContractRecoveryJournal.Presence.ABSENT, null);
        journal.markMaidPresent("maid");
        assertTrue(journal.isMaidPresent("maid"));
        assertEquals(owner, journal.transfer("maid").getUUID("Owner"));
        assertEquals("binding", journal.transfer("maid").getString("Binding"));
        journal.markMaidAbsent("maid");
        assertEquals(ContractRecoveryJournal.Presence.ABSENT, journal.presence("maid"));
        assertEquals(ContractRecoveryJournal.Presence.UNKNOWN, journal.presence("other"));
    }
    @Test void semanticWritesRetainFutureSchemaWithoutInventingAuthority() {
        var raw = new CompoundTag(); raw.putInt("Schema", 42); raw.putString("Future", "preserve");
        var journal = ContractRecoveryJournal.load(raw);
        journal.markMaidPresent("maid");
        journal.destination("maid", UUID.randomUUID(), "binding", "WEAPON", ContractRecoveryJournal.Presence.PRESENT, null);
        assertEquals(raw, journal.save(new CompoundTag()));
        assertEquals(ContractRecoveryJournal.Presence.UNKNOWN, journal.presence("maid"));
    }
    @Test void foxLedgerRejectsTokenOwnerOriginAndSpiritConflicts() {
        var identity = new CompoundTag(); identity.putInt("Version", 1); identity.putString("SpiritId", "maid_weapon:fox_white");
        for (String key : new String[]{"OwnerUUID", "SpiritUUID", "OriginUUID", "Token"}) identity.putUUID(key, UUID.randomUUID());
        var ledger = FoxSpiritLedger.load(new CompoundTag()); ledger.record(identity, "WEAPON");
        assertTrue(ledger.owns(identity, "WEAPON")); assertFalse(ledger.owns(identity, "SEAL"));
        String maid = UUID.randomUUID().toString(); ledger.rememberMaid(identity, maid);
        ledger.rememberMaid(identity, UUID.randomUUID().toString()); assertEquals(maid, ledger.maid(identity));
        for (String key : new String[]{"OwnerUUID", "SpiritUUID", "OriginUUID", "Token"}) {
            var wrong = identity.copy(); wrong.putUUID(key, UUID.randomUUID()); assertFalse(ledger.owns(wrong, "WEAPON"));
        }
        var wrong = identity.copy(); wrong.putString("SpiritId", "maid_weapon:fox_black"); assertFalse(ledger.owns(wrong, "WEAPON"));
        var seal = identity.copy(); seal.putUUID("Token", UUID.randomUUID()); ledger.record(seal, "SEAL");
        assertFalse(ledger.owns(identity, "WEAPON")); assertTrue(FoxSpiritLedger.load(ledger.save(new CompoundTag())).owns(seal, "SEAL"));
        assertEquals(maid, FoxSpiritLedger.load(ledger.save(new CompoundTag())).maid(seal));
    }
}
