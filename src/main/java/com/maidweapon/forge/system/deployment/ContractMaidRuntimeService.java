package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.compat.tlm.TlmProjectionBaubles;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Runtime maintenance for a manifested contract maid.
 *
 * <p>Deployment orchestration owns <em>when</em> a maid is active. This service
 * owns the compatibility projections and temporary task state required while
 * she is active.</p>
 */
public final class ContractMaidRuntimeService {
    public static void purgeLeakedProjections(Player player) {
        TripleMagicCompat.purgeLeakedCopies(player);
        TaczCompat.purgeLeakedLinks(player);
    }

    public static void maintain(Player player, ItemStack weapon, Entity maid) {
        if (maid.level().dimension().equals(
                com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)) return;
        // Adoption of an already-live maid must snapshot runtime settings before
        // ALL schedule/combat policy can overwrite the player's original choices.
        if (player.tickCount % 20 == 0) {
            TouhouLittleMaidHelper.syncFavorabilityFromMaid(maid, weapon);
        }
        if (!(maid instanceof LivingEntity living)) return;
        ContractProjectionMode mode = prepare(player, weapon, living);
        if (!ContractWorkPolicy.automatic(mode)) return;

        boolean safeTask = ContractWorkPolicy.feeding(mode) && MaidCareTaskSystem.applySafeTask(player, weapon, maid);
        if (!safeTask) {
            if (ContractWorkPolicy.combat(mode)) {
                ContractCombatTaskRouter.configure(player, weapon, maid);
                TripleMagicCompat.castFallbackSpell(player, living, weapon);
            } else ContractWorkPolicy.release(weapon, maid);
        }
    }

    public static void selectCombatTask(Player player, ItemStack weapon, Entity maid) {
        if (maid.level().dimension().equals(
                com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)) return;
        if (!(maid instanceof LivingEntity living)) return;
        ContractProjectionMode mode = prepare(player, weapon, living);
        if (!ContractWorkPolicy.automatic(mode)) return;
        if (!(ContractWorkPolicy.feeding(mode) && MaidCareTaskSystem.applySafeTask(player, weapon, maid))) {
            if (!ContractWorkPolicy.combat(mode)) { ContractWorkPolicy.release(weapon, maid); return; }
            ContractCombatTaskRouter.configure(player, weapon, maid);
        }
    }

    public static void cleanupBeforeRecall(Player player, ItemStack weapon, Entity maid) {
        ContractWorkPolicy.release(weapon, maid);
        if (maid instanceof LivingEntity living) {
            TaczCompat.clear(player, living, weapon);
            TripleMagicCompat.clearPhantoms(living, weapon);
        }
    }

    /** Shared pre-spawn and live preparation; no independent task-selection path. */
    public static ContractProjectionMode prepare(Player player, ItemStack weapon, LivingEntity maid) {
        if (maid.level().dimension().equals(
                com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)) return ContractProjectionMode.NONE;
        ContractProjectionMode mode = com.maidweapon.forge.compat.tlm.TlmResidenceAdapter.isResident(maid)
                ? ContractProjectionMode.NONE : TlmProjectionBaubles.mode(maid);
        ContractWorkPolicy.prepare(weapon, maid, mode);
        if (ContractWorkPolicy.automatic(mode)) TouhouLittleMaidHelper.setAllDaySchedule(maid);
        if (mode == ContractProjectionMode.NONE) TripleMagicCompat.clearPhantoms(maid, weapon);
        else TripleMagicCompat.equipPhantoms(player, maid, weapon);
        if (mode.weapon()) {
            TripleMagicCompat.syncMaidSpellLoadout(player, maid, weapon);
            if (TaczCompat.isGun(weapon)) TaczCompat.maintain(player, maid, weapon);
        } else {
            TaczCompat.clear(player, maid, weapon);
        }
        return mode;
    }

    private ContractMaidRuntimeService() {}
}
