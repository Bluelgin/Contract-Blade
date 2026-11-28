package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PortMasterySavedDataTest {
    private static final UUID OWNER = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Test void oldWorldMasteryKeepsPlayerAndSourceRecords() {
        CompoundTag old = new CompoundTag();
        old.putInt("SchemaVersion", 1);
        CompoundTag players = new CompoundTag();
        CompoundTag player = new CompoundTag();
        player.putInt("Level", 8);
        players.put(OWNER.toString(), player);
        old.put("Players", players);
        CompoundTag receipts = new CompoundTag();
        receipts.putString("source-A", "already-imported");
        old.put("LegacySources", receipts);
        PortMasterySavedData loaded = PortMasterySavedData.load(old, null);
        assertTrue(loaded.hasSource("source-A"));
        assertEquals(8, loaded.readDetached(OWNER).getInt("Level"));
        loaded.readDetached(OWNER).putInt("Level", 999);
        assertEquals(8, loaded.readDetached(OWNER).getInt("Level"));
        assertEquals("already-imported", loaded.save(new CompoundTag(), null)
            .getCompound("LegacySources").getString("source-A"));
    }

    @Test void unknownNewerSchemaRemainsReadOnlyAndPreserved() {
        CompoundTag newer = new CompoundTag();
        newer.putInt("SchemaVersion", 23);
        newer.putString("FutureMigration", "do-not-delete");
        PortMasterySavedData loaded = PortMasterySavedData.load(newer, null);
        assertFalse(loaded.writable(OWNER));
        assertThrows(IllegalStateException.class, () -> loaded.readDetached(OWNER));
        assertEquals(newer, loaded.save(new CompoundTag(), null));
    }

    @Test void cannotOverwriteKnownRecordWithFutureSchema() {
        PortMasterySavedData empty = new PortMasterySavedData();
        CompoundTag unsupported = new CompoundTag();
        unsupported.putInt("SchemaVersion", 99);
        assertThrows(IllegalStateException.class,
            () -> empty.replaceOwned(OWNER, unsupported));
    }
}
