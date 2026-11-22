package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class LegacyMaidSnapshotCodecTest {
    static CompoundTag nativeMaid() {
        CompoundTag maid = new CompoundTag();
        maid.putUUID("UUID", UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
        maid.putUUID("Owner", UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        maid.putString("Name", "White Fox");
        CompoundTag inventory = new CompoundTag();
        inventory.putString("TestStack", "no-items-dropped");
        maid.put("FakeInventory", inventory);
        return maid;
    }

    @Test
    void v1EncodeDecodeRoundTripRetainsIdentityAndEquipment() throws Exception {
        CompoundTag original = nativeMaid();
        CompoundTag carrier = new CompoundTag();
        carrier.putString("ThirdPartyState", "preserve");
        LegacyMaidSnapshotCodec.write(carrier, original);
        CompoundTag decoded = LegacyMaidSnapshotCodec.read(carrier);
        assertEquals(original.getUUID("UUID"), decoded.getUUID("UUID"));
        assertEquals(original.getUUID("Owner"), decoded.getUUID("Owner"));
        assertEquals("no-items-dropped", decoded.getCompound("FakeInventory").getString("TestStack"));
        assertEquals("preserve", carrier.getString("ThirdPartyState"));
    }

    @Test
    void readsUncompressedLegacyEntityWithoutMutation() throws Exception {
        CompoundTag carrier = new CompoundTag();
        carrier.put("MaidEntityData", nativeMaid());
        CompoundTag copy = LegacyMaidSnapshotCodec.read(carrier);
        copy.putString("Name", "Changed");
        assertEquals("White Fox", carrier.getCompound("MaidEntityData").getString("Name"));
    }

    @Test
    void corruptInputIsReadOnlyAndDoesNotClobberPreviousSnapshot() throws Exception {
        CompoundTag carrier = new CompoundTag();
        LegacyMaidSnapshotCodec.write(carrier, nativeMaid());
        long oldCrc = carrier.getLong("MaidEntityDataChecksum");
        carrier.putLong("MaidEntityDataChecksum", 0);
        assertThrows(IOException.class, () -> LegacyMaidSnapshotCodec.read(carrier));
        assertTrue(carrier.contains("MaidEntityDataCompressed"));
        assertNotEquals(oldCrc, carrier.getLong("MaidEntityDataChecksum"));
    }

    @Test
    void preservesRootOnFailedWrite() {
        CompoundTag carrier = new CompoundTag();
        carrier.putString("ThirdPartyState", "original");
        CompoundTag huge = new CompoundTag();
        huge.putByteArray("oversized", new byte[LegacyMaidSnapshotCodec.MAX_NBT_SIZE + 1]);
        assertThrows(IOException.class, () -> LegacyMaidSnapshotCodec.write(carrier, huge));
        assertEquals("original", carrier.getString("ThirdPartyState"));
        assertFalse(carrier.contains("MaidEntityDataCompressed"));
    }
}
