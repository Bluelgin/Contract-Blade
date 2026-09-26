package com.maidweapon.forge.compat;

import net.minecraftforge.fml.ModList;

/**
 * Narrow Iron's Spells 'n Spellbooks compatibility boundary.
 *
 * <p>Actual contract combat projection is implemented by
 * {@link TripleMagicCompat}. These beta-era methods remain only as stable,
 * explicit compatibility shims instead of pretending to provide unfinished
 * spell mutation.</p>
 */
public final class IronsSpellsCompat {
    private static final String MOD_ID = "irons_spellbooks";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Dynamic tier estimation replaced the old per-mod monster table. */
    @Deprecated
    public static void registerMonsters() {
        // Intentionally empty.
    }

    /**
     * Core leaves Iron's spell power calculation to Iron's itself.
     */
    @Deprecated
    public static float getStaffSpellPower(Object staffStack) {
        return 1.0f;
    }

    /**
     * Directly mutating spell containers is unsupported; TripleMagicCompat
     * projects the owner's weapon/loadout onto the maid at runtime instead.
     */
    @Deprecated
    public static boolean addSpellToWeapon(Object weaponStack, String spellId) {
        return false;
    }

    private IronsSpellsCompat() {}
}
