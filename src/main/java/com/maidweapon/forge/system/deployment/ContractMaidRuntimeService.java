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
        // Projection maintenance must not take ownership of work or schedules.
        if (player.tickCount % 20 == 0) {
            TouhouLittleMaidHelper.syncFavorabilityFromMaid(maid, weapon);
        }
        if (!(maid instanceof LivingEntity living)) return;
        ContractProjectionMode mode = prepare(player, weapon, living);
        // Preserve the optional no-provider spell fallback for a manually chosen
        // attack task, without making idle/feeding maids cast or selecting a task.
        if (mode.weapon() && MaidCareTaskSystem.ATTACK_TASK.equals(TouhouLittleMaidHelper.getMaidTaskId(maid))) {
            TripleMagicCompat.castFallbackSpell(player, living, weapon);
        }
        if (!ContractWorkPolicy.automatic(mode)) return;

        boolean safeTask = ContractWorkPolicy.feeding(mode) && MaidCareTaskSystem.applySafeTask(player, weapon, maid);
        if (!safeTask) {
            if (ContractWorkPolicy.combat(mode)) {
                ContractCombatTaskRouter.configure(player, weapon, maid);
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
            com.maidweapon.forge.compat.tlm.TlmMealLifecycle.prepareForStorage(living);
            com.maidweapon.forge.compat.ContractEquipmentCleanup.clear(player, living, weapon);
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
        boolean gunProjection = mode.weapon() && TaczCompat.isGun(weapon);
        if (mode == ContractProjectionMode.NONE) {
            com.maidweapon.forge.compat.ContractEquipmentCleanup.clear(player, maid, weapon);
        } else {
            if (!gunProjection) TaczCompat.clear(player, maid, weapon);
            TripleMagicCompat.equipPhantoms(player, maid, weapon);
        }
        if (mode.weapon()) {
            TripleMagicCompat.syncMaidSpellLoadout(player, maid, weapon);
            if (gunProjection) TaczCompat.maintain(player, maid, weapon);
        }
        return mode;
    }

    private ContractMaidRuntimeService() {}
}
