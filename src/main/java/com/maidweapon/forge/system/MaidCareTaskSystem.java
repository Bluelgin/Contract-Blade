package com.maidweapon.forge.system;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Temporarily selects TLM's feeding task while a manifested contract maid is
 * near a hungry owner in a safe area. The maid's real pre-contract task is
 * retained on the weapon and restored when the contract manifestation ends.
 */
@Mod.EventBusSubscriber
public final class MaidCareTaskSystem {
    public static final String ORIGINAL_TASK_TAG = "MaidInfusionOriginalTask";
    public static final String ORIGINAL_SCHEDULE_TAG = "MaidInfusionOriginalSchedule";
    public static final String ATTACK_TASK = "touhou_little_maid:attack";
    public static final String FEED_TASK = "touhou_little_maid:feed";

    private static final Map<UUID, Long> LAST_DANGER = new HashMap<>();

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_DANGER.remove(event.getEntity().getUUID());
    }

    public static void markDanger(Player player) {
        if (player != null && !player.level().isClientSide) {
            LAST_DANGER.put(player.getUUID(), player.level().getGameTime());
        }
    }

    public static void rememberOriginalTask(ItemStack weapon, Entity maid) {
        if (weapon.isEmpty() || maid == null) return;
        if (!weapon.getOrCreateTag().contains(ORIGINAL_TASK_TAG)) {
            weapon.getOrCreateTag().putString(ORIGINAL_TASK_TAG,
                    TouhouLittleMaidHelper.getMaidTaskId(maid));
        }
        if (!weapon.getOrCreateTag().contains(ORIGINAL_SCHEDULE_TAG)) {
            String schedule = TlmEntityAdapter.scheduleName(maid);
            if (!schedule.isEmpty()) {
                weapon.getOrCreateTag().putString(ORIGINAL_SCHEDULE_TAG, schedule);
            }
        }
    }

    /**
     * @return true only while feeding is actively selected. Merely being safe
     * must not suppress a weapon-specific combat task such as Native POWER.
     */
    public static boolean applySafeTask(Player owner, ItemStack weapon, Entity maid) {
        if (maid.level().dimension().equals(
                com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)) return false;
        if (!MaidWeaponConfig.ENABLE_SAFE_FEEDING.get() || !isSafe(owner, maid)) {
            return false;
        }

        rememberOriginalTask(weapon, maid);
        boolean hungry = owner.getFoodData().needsFood()
                && owner.getFoodData().getFoodLevel()
                <= MaidWeaponConfig.SAFE_FEEDING_HUNGER_THRESHOLD.get();
        if (!hungry) return false;
        return switchIfNeeded(maid, FEED_TASK);
    }

    public static void restoreOriginalTask(ItemStack weapon, Entity maid) {
        if (weapon.isEmpty() || maid == null || weapon.getTag() == null) return;
        String original = weapon.getTag().getString(ORIGINAL_TASK_TAG);
        if (!original.isEmpty()) switchIfNeeded(maid, original);
        String schedule = weapon.getTag().getString(ORIGINAL_SCHEDULE_TAG);
        if (!schedule.isEmpty()) TlmEntityAdapter.setSchedule(maid, schedule);
    }

    public static void clearOriginalTask(ItemStack weapon) {
        if (!weapon.isEmpty() && weapon.getTag() != null) {
            weapon.getTag().remove(ORIGINAL_TASK_TAG);
            weapon.getTag().remove(ORIGINAL_SCHEDULE_TAG);
        }
    }

    private static boolean isSafe(Player owner, Entity maid) {
        if (!owner.isAlive() || !maid.isAlive() || owner.isOnFire() || maid.isOnFire()) {
            markDanger(owner);
            return false;
        }
        if (owner.getActiveEffects().stream()
                .anyMatch(effect -> effect.getEffect().getCategory()
                        == MobEffectCategory.HARMFUL)) {
            markDanger(owner);
            return false;
        }
        if (maid instanceof Mob maidMob && maidMob.getTarget() != null) {
            markDanger(owner);
            return false;
        }

        double radius = MaidWeaponConfig.SAFE_FEEDING_DANGER_RADIUS.get();
        boolean nearbyThreat = owner.level().getEntitiesOfClass(
                Mob.class, owner.getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && mob != maid
                        && (mob instanceof Enemy
                        || mob.getTarget() == owner
                        || mob.getTarget() == maid))
                .stream().findAny().isPresent();
        if (nearbyThreat) {
            markDanger(owner);
            return false;
        }

        long lastDanger = LAST_DANGER.getOrDefault(owner.getUUID(), Long.MIN_VALUE / 2);
        return owner.level().getGameTime() - lastDanger
                >= MaidWeaponConfig.SAFE_FEEDING_DELAY.get();
    }

    private static boolean switchIfNeeded(Entity maid, String task) {
        if (task.equals(TouhouLittleMaidHelper.getMaidTaskId(maid))) return true;
        return TouhouLittleMaidHelper.switchMaidTask(maid, task)
                && task.equals(TouhouLittleMaidHelper.getMaidTaskId(maid));
    }

    private MaidCareTaskSystem() {
    }
}
