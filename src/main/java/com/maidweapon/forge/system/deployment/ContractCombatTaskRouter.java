package com.maidweapon.forge.system.deployment;

import com.mojang.logging.LogUtils;
import com.maidweapon.forge.compat.EpicFightCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Chooses the combat task for a manifested contract maid.
 *
 * <p>The deployment state machine should not know which optional mod provides a
 * task. This router owns that decision and all fallback/error bookkeeping.</p>
 */
public final class ContractCombatTaskRouter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MAGIC_TASK_FAILURE = "MaidInfusionMagicTaskFailure";
    private static final String SLASHBLADE_TASK_FAILURE = "MaidInfusionSlashBladeTaskFailure";
    private static final String TACZ_TASK_FAILURE = "MaidInfusionTaczTaskFailure";
    private static final String EPIC_FIGHT_TASK_FAILURE = "MaidInfusionEpicFightTaskFailure";

    public static void configure(Player owner, ItemStack weapon, Entity maid) {
        if (!com.maidweapon.forge.compat.tlm.TlmProjectionBaubles.mode(maid).weapon()) return;
        boolean slashBladeMode = SlashBladeCompat.usesMaidSlashBladeTask(weapon);
        boolean magicMode = !slashBladeMode && TripleMagicCompat.usesMaidSpellTask(weapon);
        boolean taczMode = !slashBladeMode && !magicMode && TaczCompat.isGun(weapon);
        boolean epicFightMode = !slashBladeMode && !magicMode && !taczMode
                && !SlashBladeCompat.isSlashBlade(weapon)
                && !TripleMagicCompat.isMagicCatalyst(weapon)
                && EpicFightCompat.usesMaidFightTask(weapon, maid);
        String desired = slashBladeMode
                ? SlashBladeCompat.getMaidSlashBladeTaskId()
                : magicMode ? TripleMagicCompat.getMaidSpellRangedTaskId()
                : taczMode ? TaczCompat.GUN_TASK
                : epicFightMode ? EpicFightCompat.FIGHT_TASK : MaidCareTaskSystem.ATTACK_TASK;
        String current = TouhouLittleMaidHelper.getMaidTaskId(maid);
        if (!desired.equals(current)) {
            TouhouLittleMaidHelper.switchMaidTask(maid, desired);
            current = TouhouLittleMaidHelper.getMaidTaskId(maid);
        }

        if (slashBladeMode && !desired.equals(current)) {
            reportFailure(owner, weapon, SLASHBLADE_TASK_FAILURE,
                    SlashBladeCompat.getMaidSlashBladeProviderName(), desired, current,
                    "maid_weapon.message.slashblade_task_unavailable",
                    SlashBladeCompat.getMaidSlashBladeProviderName());
            fallback(maid, current);
        } else if (magicMode && !desired.equals(current)) {
            reportFailure(owner, weapon, MAGIC_TASK_FAILURE,
                    "magic", desired, current,
                    "maid_weapon.message.magic_task_unavailable");
            fallback(maid, current);
        } else if (taczMode && !desired.equals(current)) {
            reportFailure(owner, weapon, TACZ_TASK_FAILURE,
                    "TACZ", desired, current,
                    "maid_weapon.message.tacz_task_unavailable");
            fallback(maid, current);
        } else if (epicFightMode && !desired.equals(current)) {
            reportFailure(owner, weapon, EPIC_FIGHT_TASK_FAILURE,
                    "Epic Fight", desired, current,
                    "maid_weapon.message.epicfight_task_unavailable");
            fallback(maid, current);
        } else {
            clearFailures(weapon);
        }
    }

    private static void fallback(Entity maid, String current) {
        if (!MaidCareTaskSystem.ATTACK_TASK.equals(current)) {
            TouhouLittleMaidHelper.switchMaidTask(maid, MaidCareTaskSystem.ATTACK_TASK);
        }
    }

    private static void clearFailures(ItemStack weapon) {
        weapon.getOrCreateTag().remove(MAGIC_TASK_FAILURE);
        weapon.getOrCreateTag().remove(SLASHBLADE_TASK_FAILURE);
        weapon.getOrCreateTag().remove(TACZ_TASK_FAILURE);
        weapon.getOrCreateTag().remove(EPIC_FIGHT_TASK_FAILURE);
    }

    private static void reportFailure(Player owner, ItemStack weapon, String failureTag,
                                      String mode, String desired, String current,
                                      String messageKey, Object... messageArguments) {
        if (weapon.getOrCreateTag().getBoolean(failureTag)) return;
        weapon.getOrCreateTag().putBoolean(failureTag, true);
        LOGGER.warn("[MaidWeapon] Failed to select {} task for {}: item={}, desired={}, actual={}",
                mode, owner.getScoreboardName(), weapon.getItem(), desired, current);
        owner.displayClientMessage(Component.translatable(messageKey, messageArguments), true);
    }

    private ContractCombatTaskRouter() {}
}
