package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.compat.TripleMagicCompat;
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
        MaidCareTaskSystem.rememberOriginalTask(weapon, maid);
        if (player.tickCount % 20 == 0) {
            TouhouLittleMaidHelper.syncFavorabilityFromMaid(maid, weapon);
        }
        TouhouLittleMaidHelper.setAllDaySchedule(maid);
        if (!(maid instanceof LivingEntity living)) return;

        TripleMagicCompat.equipPhantoms(player, living, weapon);
        TripleMagicCompat.syncMaidSpellLoadout(player, living, weapon);
        if (TaczCompat.isGun(weapon)) {
            TaczCompat.maintain(player, living, weapon);
        }

        boolean safeTask = MaidCareTaskSystem.applySafeTask(player, weapon, maid);
        if (!safeTask) {
            ContractCombatTaskRouter.configure(player, weapon, maid);
            TripleMagicCompat.castFallbackSpell(player, living, weapon);
        }
    }

    public static void selectCombatTask(Player player, ItemStack weapon, Entity maid) {
        MaidCareTaskSystem.rememberOriginalTask(weapon, maid);
        if (!(maid instanceof LivingEntity)) return;
        if (!MaidCareTaskSystem.applySafeTask(player, weapon, maid)) {
            ContractCombatTaskRouter.configure(player, weapon, maid);
        }
    }

    public static void cleanupBeforeRecall(Player player, ItemStack weapon, Entity maid) {
        MaidCareTaskSystem.restoreOriginalTask(weapon, maid);
        if (maid instanceof LivingEntity living) {
            TaczCompat.clear(player, living, weapon);
            TripleMagicCompat.clearPhantoms(living, weapon);
        }
    }

    private ContractMaidRuntimeService() {}
}
