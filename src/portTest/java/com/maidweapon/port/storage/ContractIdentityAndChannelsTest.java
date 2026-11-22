package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ContractIdentityAndChannelsTest {
    private static final UUID OWNER = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID MAID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID BINDING = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Test void bindOncePreservesIdentityAcrossRepeatedUpdates() {
        CompoundTag original = new CompoundTag();
        original.putString("ForeignBladeData", "untouched");
        CompoundTag first = ContractIdentityFields.bindOnce(original, OWNER, "Owner", MAID, BINDING);
        CompoundTag second = ContractIdentityFields.bindOnce(first, OWNER, "RenamedPlayer", MAID, BINDING);
        assertTrue(ContractIdentityFields.isExplicitOwner(second, OWNER));
        assertTrue(ContractIdentityFields.matchesMaid(second, MAID));
        assertEquals(BINDING.toString(), second.getString("MaidBindingId"));
        assertEquals("Owner", second.getString("OwnerName"));
        assertEquals("untouched", second.getString("ForeignBladeData"));
    }

    @Test void rejectsRetargetingAnExistingContract() {
        CompoundTag first = ContractIdentityFields.bindOnce(new CompoundTag(), OWNER, "Owner", MAID, BINDING);
        assertThrows(IllegalStateException.class, () -> ContractIdentityFields.bindOnce(first,
            UUID.randomUUID(), "Other", MAID, BINDING));
        assertThrows(IllegalStateException.class, () -> ContractIdentityFields.bindOnce(first,
            OWNER, "Owner", UUID.randomUUID(), BINDING));
    }

    @Test void snapshotsAllDormantChannelsWithoutLeakingReferences() {
        CompoundTag root = new CompoundTag();
        root.putString("MaidUUID", MAID.toString());
        CompoundTag external = new CompoundTag();
        external.putString("MaidUUID", "other");
        root.put("MaidWeaponExternalContract", external);
        CompoundTag spirits = new CompoundTag();
        CompoundTag spiritA = new CompoundTag();
        spiritA.put("Contract", external.copy());
        spirits.put("spiritA", spiritA);
        root.put("MaidWeaponIntrinsicSpirits", spirits);
        var snapshots = ContractChannelView.snapshotChannels(root);
        assertEquals(3, snapshots.size());
        snapshots.get(1).putString("MaidUUID", "mutated");
        assertEquals("other", root.getCompound("MaidWeaponExternalContract").getString("MaidUUID"));
    }

    @Test void failsClosedOnUnboundedDormantChannels() {
        CompoundTag root = new CompoundTag();
        CompoundTag spirits = new CompoundTag();
        for (int i = 0; i < 65; i++) spirits.put("entry"+i, new CompoundTag());
        root.put("MaidWeaponIntrinsicSpirits", spirits);
        assertThrows(IllegalArgumentException.class, () -> ContractChannelView.snapshotChannels(root));
    }
}
