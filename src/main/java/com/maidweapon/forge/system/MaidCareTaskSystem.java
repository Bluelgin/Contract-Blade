package com.maidweapon.forge.system;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
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
    public static final String ATTACK_TASK = "touhou_little_maid:attack";
    public static final String FEED_TASK = "touhou_little_maid:feed";

    private static final Map<UUID, Long> LAST_DANGER = new HashMap<>();

    /**
     * Dedicated Contract Blades are manifested manually, so they are not part
     * of the generic weapon deployment state machine. Maintain their temporary
     * care/combat task here while the blade remains in the owner's main hand.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || event.player.tickCount % 10 != 0
                || !MaidWeaponConfig.ENABLE_SAFE_FEEDING.get()) return;

        Player player = event.player;
        if (player.level().dimension().equals(
                com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)) return;
        ItemStack weapon = player.getMainHandItem();
        if (!MaidInfusion.isContractBlade(weapon)
                || !MaidInfusion.isInfused(weapon)
                || !MaidWeaponItem.isOwner(weapon, player)
                || MaidWeaponItem.isContractSuperseded(weapon)) return;

        String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
        if (maidId == null || maidId.isEmpty()) return;
        Entity maid = InfusedMaidDeploymentSystem.findManifestedMaid(player, maidId);
        if (maid == null) return;

        // 与自动显形路径保持一致的周期性维护：作息、装备幻影、法术与 TACZ。
        TouhouLittleMaidHelper.prepareManifestedMaid(player, weapon, maid);
        if (!applySafeTask(player, weapon, maid)) {
            switchIfNeeded(maid, ATTACK_TASK);
        }
    }

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
    }

    /**
     * @return true only while feeding is actively selected. Merely being safe
     * must not suppress a weapon-specific combat task such as Native POWER.
     */
    public static boolean applySafeTask(Player owner, ItemStack weapon, Entity maid) {
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
    }

    public static void clearOriginalTask(ItemStack weapon) {
        if (!weapon.isEmpty() && weapon.getTag() != null) {
            weapon.getTag().remove(ORIGINAL_TASK_TAG);
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
