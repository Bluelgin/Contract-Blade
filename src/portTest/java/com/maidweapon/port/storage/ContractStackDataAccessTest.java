package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ContractStackDataAccessTest {
    @Test
    void preservesForeignFieldsWhileReplacingContract() {
        CompoundTag original = new CompoundTag();
        original.putString("SlashBladeState", "critical");
        original.putString("MaidUUID", "old");
        original.putInt("OtherModData", 42);
        CompoundTag replacement = new CompoundTag();
        replacement.putString("MaidUUID", "new");
        CompoundTag result = ContractStackDataAccess.mergeOwned(original, replacement);
        assertEquals("new", result.getString("MaidUUID"));
        assertEquals("critical", result.getString("SlashBladeState"));
        assertEquals(42, result.getInt("OtherModData"));
        assertEquals("old", original.getString("MaidUUID"));
    }

    @Test
    void refusesForeignWriteAndLeavesSourceUntouched() {
        CompoundTag original = new CompoundTag();
        original.putInt("OtherModData", 12);
        CompoundTag attempted = new CompoundTag();
        attempted.putInt("OtherModData", 0);
        assertThrows(IllegalArgumentException.class,
            () -> ContractStackDataAccess.mergeOwned(original, attempted));
        assertEquals(12, original.getInt("OtherModData"));
    }

    @Test
    void copiesNestedTagAndDoesNotLeakReference() {
        CompoundTag root = new CompoundTag();
        CompoundTag maid = new CompoundTag();
        maid.putString("Name", "Sayo");
        root.put("MaidData", maid);
        CompoundTag extracted = ContractStackDataAccess.extractOwned(root);
        extracted.getCompound("MaidData").putString("Name", "changed");
        assertEquals("Sayo", root.getCompound("MaidData").getString("Name"));
    }

    @Test
    void refusesDestructiveWritesForUnknownFutureSchema() {
        CompoundTag previous = new CompoundTag();
        previous.putInt("ContractCarrierStorageVersion", 3);
        previous.putString("MaidUUID", "preserve-me");
        CompoundTag next = new CompoundTag();
        next.putInt("ContractCarrierStorageVersion", 1);
        assertThrows(IllegalStateException.class,
            () -> ContractStackDataAccess.mergeOwned(previous, next));
        assertEquals("preserve-me", previous.getString("MaidUUID"));
    }

    @Test
    void knownFieldsCanBeClearedWithoutDeletingUnknownOnes() {
        CompoundTag root = new CompoundTag();
        root.putString("MaidUUID", "original");
        root.putString("ExternalCustomData", "keep-me");
        CompoundTag merged = ContractStackDataAccess.mergeOwned(root, new CompoundTag());
        assertFalse(merged.contains("MaidUUID"));
        assertEquals("keep-me", merged.getString("ExternalCustomData"));
    }
}
