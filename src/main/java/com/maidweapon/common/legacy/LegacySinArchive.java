package com.maidweapon.common.legacy;

import com.maidweapon.common.data.MaidWeaponData;

/**
 * Compatibility view of the paused Part campaign's seven-sins payload.
 *
 * <p>Core preserves these values when old weapons are read and written, but
 * they are archival metadata only and must never affect Core combat,
 * resonance, deployment or progression.</p>
 */
public final class LegacySinArchive {
    public static boolean hasData(MaidWeaponData data) {
        return data != null && !data.getEmbeddedSins().isEmpty();
    }

    public static int entryCount(MaidWeaponData data) {
        return data == null ? 0 : data.getEmbeddedSins().size();
    }

    private LegacySinArchive() {}
}
