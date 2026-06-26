package com.maidweapon.forge.event;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.data.MaidWeaponDataSerializer;
import com.maidweapon.common.sin.SinType;
import com.maidweapon.common.system.LoyaltySystem;
import com.maidweapon.common.system.MonsterTierRegistry;
import com.maidweapon.common.compat.ModCompatManager;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.ChallengeTracker;
import com.maidweapon.forge.system.SacredTreeSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;


/**
 * ========================================
 * 战斗事件处理器（Forge 端）
 * ========================================
 *
 * 核心机制：无死亡挑战系统
 *
 * 流程：
 *   1. 玩家使用女仆武器攻击怪物 → 进入"挑战状态"
 *   2. 在该怪物死亡前，玩家不能死亡
 *   3. 怪物死亡 → 挑战成功 → 尝试升级武器
 *   4. 玩家死亡 → 挑战失败 → 忠诚度大幅下降
 *
 * 同时追踪多个被攻击的怪物，任何一个被击杀且玩家未死亡即视为挑战成功。
 */
@Mod.EventBusSubscriber
public class CombatEventHandler {

/**
     * 当玩家使用女仆武器攻击怪物时，开始挑战
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        // 检查攻击者是否为玩家
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        // 获取玩家主手物品
        ItemStack mainHand = player.getMainHandItem();

        // 检查是否是女仆武器
        if (!(mainHand.getItem() instanceof MaidWeaponItem)) return;
        if (!MaidWeaponItem.hasMaidData(mainHand)) return;

        // 每次命中立即减少好感度（不依赖挑战系统）
        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);

        // 未捕获女仆时伤害为 1
        if (!MaidWeaponItem.hasMaidEntityData(mainHand)) {
            event.setAmount(1.0f);
            return;
        }

        // 已捕获女仆：应用等级加成 + 好感度倍率
        float levelBonus = data.getAttackDamageBonus();
        float multiplier = data.getFavorabilityDamageMultiplier();
        event.setAmount((3.0f + levelBonus) * multiplier);

        String targetId = getEntityId(event.getEntity());
        int tier = MonsterTierRegistry.getTier(targetId);
        if (tier == MaidWeaponData.TIER_NONE) {
            tier = ModCompatManager.getExternalTier(targetId);
        }
        if (tier == -1 || tier == MaidWeaponData.TIER_NONE) {
            tier = MonsterTierRegistry.estimateTierFromHealth(event.getEntity().getMaxHealth());
        }
        boolean isBoss = MonsterTierRegistry.isBoss(targetId)
                || ModCompatManager.getExternalBosses().getOrDefault(targetId, false)
                || MonsterTierRegistry.isBossFromHealth(event.getEntity().getMaxHealth());

        // 获取被攻击的实体
        LivingEntity target = event.getEntity();
        UUID playerUUID = player.getUUID();

        // 获取怪物Tier（已有）
        if (tier <= 0) {
            tier = MonsterTierRegistry.estimateTierFromHealth(target.getMaxHealth());
        }

        // 判断是否为末影龙/凋灵
        boolean isDragon = targetId.equals("minecraft:ender_dragon");
        boolean isWither = targetId.equals("minecraft:wither");

        // 更新挑战状态（记录最高Tier）
        Integer currentTier = ChallengeTracker.getTier(playerUUID);
        if (currentTier == null || tier > currentTier) {
            ChallengeTracker.setTier(playerUUID, tier);
        }

        if (isBoss) {
            ChallengeTracker.setHasBoss(playerUUID, true);
        }
        if (isDragon) {
            ChallengeTracker.setHasDragon(playerUUID, true);
        }
        if (isWither) {
            ChallengeTracker.setHasWither(playerUUID, true);
        }
    }

    /**
     * 怪物死亡事件：检查挑战是否成功
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 检查击杀者是否为玩家
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        // 获取玩家主手物品
        ItemStack mainHand = player.getMainHandItem();

        // 检查是否是女仆武器
        if (!(mainHand.getItem() instanceof MaidWeaponItem)) return;
        if (!MaidWeaponItem.hasMaidData(mainHand)) return;

        UUID playerUUID = player.getUUID();

        // 检查是否有活跃的挑战
        Integer challengeTier = ChallengeTracker.getTier(playerUUID);
        if (challengeTier == null) return;

        // 检查是否在本次挑战中失败过（死亡过）
        Boolean failed = ChallengeTracker.isFailed(playerUUID);
        if (failed != null && failed) {
            // 挑战失败过，清除状态但不升级
            ChallengeTracker.clear(playerUUID);
            return;
        }

        // 挑战成功！尝试升级
        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);
        String targetId = getEntityId(event.getEntity());
        boolean isDragon = ChallengeTracker.hasDragon(playerUUID);
        boolean isWither = ChallengeTracker.hasWither(playerUUID);

        // 记录击杀
        data.addKill();

        // 好感度减少（按怪物血量计算）
        float monsterHealth = event.getEntity().getMaxHealth();
        LoyaltySystem.onKill(data, monsterHealth);

        // 尝试升级
        int oldLevel = data.getLevel();
        boolean upgraded = data.tryUpgrade(challengeTier, isDragon, isWither);

        if (upgraded) {
            int newLevel = data.getLevel();

            // 升级特效：声音 + 粒子
            player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            if (!player.level().isClientSide && player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        50, 1.5, 1.0, 1.5, 0.5
                );
            }

            player.displayClientMessage(
                    Component.literal("§e✦ 女仆武器突破！现在是 Lv." + newLevel + " ✦"),
                    true
            );
            // 剧情记忆碎片
            String memoryKey = null;
            switch (newLevel) {
                case 3 -> memoryKey = "maid_weapon.memory.level_3";
                case 7 -> memoryKey = "maid_weapon.memory.level_7";
            }
            if (memoryKey != null) {
                player.displayClientMessage(
                        Component.translatable(memoryKey), false
                );
            }
            // 通知兼容层
            ModCompatManager.notifyUpgrade("maid_sword", oldLevel, newLevel);
        }

        // 通知兼容层：怪物被击杀
        boolean isBoss = ChallengeTracker.hasBoss(playerUUID);
        ModCompatManager.notifyMonsterKilled("maid_sword", targetId, challengeTier, isBoss);

        // 保存数据
        MaidWeaponItem.setMaidData(mainHand, data);

        // 清除挑战状态
        ChallengeTracker.clear(playerUUID);
    }

    /**
     * 玩家死亡事件：标记挑战失败
     */
    @SubscribeEvent
    public static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID playerUUID = player.getUUID();

        // 检查是否有活跃的挑战
        Integer challengeTier = ChallengeTracker.getTier(playerUUID);
        if (challengeTier == null) return;

        // 标记挑战失败
        ChallengeTracker.setFailed(playerUUID, true);

        // 通知玩家挑战失败
        player.displayClientMessage(
                Component.literal("§c✗ 挑战失败！女仆武器未能升级..."),
                true
        );

        // 降低忠诚度作为惩罚
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof MaidWeaponItem && MaidWeaponItem.hasMaidData(mainHand)) {
            MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);
            data.reduceFavorability(MaidWeaponConfig.DEATH_PENALTY.get()); // 失败惩罚
            MaidWeaponItem.setMaidData(mainHand, data);
        }

        // 通知兼容层
        ModCompatManager.notifyChallengeFailed("maid_sword", "unknown");

        // 清除挑战状态
        ChallengeTracker.clear(playerUUID);
    }

    /**
     * 玩家重生时清除所有状态
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ChallengeTracker.clear(event.getEntity().getUUID());
    }

    /** 拾取剧情物品时触发记忆 + 效果 */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        // ponytail: 记忆触发已移至 AdvancementEventHandler
    }

    /** 追踪玩家持有神树之实的 tick 累加器 */
    private static final java.util.Map<java.util.UUID, Float> SACRED_FRUIT_ACCUM = new java.util.HashMap<>();

    /** 药房试剂最近一次触发效果的 gameTime */
    private static final java.util.Map<java.util.UUID, Long> POTION_VIAL_LAST_TICK = new java.util.HashMap<>();

    /**
     * 玩家 tick 事件：处理忠诚度自然恢复
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;

        // 首次靠近神树触发回忆（独立于武器检测）
        SacredTreeSystem.tryTriggerMemory(player);

        // 首次靠近药房触发回忆
        SacredTreeSystem.tryTriggerYakkyokuMemory(player);

        // 药房附近：生命恢复 II
        if (SacredTreeSystem.isNearYakkyoku(player)) {
            if (player.tickCount % 40 == 0) { // 每 2 秒一次
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, false));
            }
        }

        // 在主手或副手中找到女仆之刃
        ItemStack weaponStack = player.getMainHandItem();
        if (!(weaponStack.getItem() instanceof MaidWeaponItem) || !MaidWeaponItem.hasMaidData(weaponStack)) {
            weaponStack = player.getOffhandItem();
            if (!(weaponStack.getItem() instanceof MaidWeaponItem) || !MaidWeaponItem.hasMaidData(weaponStack)) {
                return;
            }
        }

        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        UUID playerUUID = player.getUUID();

        // 神树附近：每 tick 恢复好感度
        int treeRecovery = SacredTreeSystem.tickRecovery(player);
        if (treeRecovery > 0) {
            data.addFavorability(treeRecovery);
            MaidWeaponItem.setMaidData(weaponStack, data);
        }

        // 持有神树之实时：每秒 +0.05 好感度（每 20 秒 +1）
        boolean hasFruit = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.SACRED_FRUIT.get())) {
                hasFruit = true;
                break;
            }
        }
        if (hasFruit) {
            float acc = SACRED_FRUIT_ACCUM.getOrDefault(playerUUID, 0f);
            acc += 0.0025f; // 每 tick 0.0025 = 每秒 0.05
            if (acc >= 1.0f) {
                acc -= 1.0f;
                data.addFavorability(1);
                MaidWeaponItem.setMaidData(weaponStack, data);
            }
            SACRED_FRUIT_ACCUM.put(playerUUID, acc);
        } else {
            SACRED_FRUIT_ACCUM.remove(playerUUID);
        }

        // 每个游戏日（24000 tick）恢复好感度
        long gameTime = player.level().getGameTime();
        if (gameTime % 24000 == 0) {
            if (!data.hasSin(SinType.LUST)) {
                LoyaltySystem.naturalRecovery(data);
                MaidWeaponItem.setMaidData(weaponStack, data);
            }
        }

        // 检测进食：饱食度增加 → 好感度恢复
        int currentFood = player.getFoodData().getFoodLevel();
        Integer lastFood = ChallengeTracker.getLastFoodLevel(playerUUID);
        if (lastFood != null && currentFood > lastFood) {
            int diff = currentFood - lastFood;
            LoyaltySystem.onEat(data, diff);
            MaidWeaponItem.setMaidData(weaponStack, data);
        }
        ChallengeTracker.setFoodLevel(playerUUID, currentFood);

        // 检测回血：血量增加 → 好感度恢复
        float currentHealth = player.getHealth();
        Float lastHealth = ChallengeTracker.getLastHealth(playerUUID);
        if (lastHealth != null && currentHealth > lastHealth) {
            float diff = currentHealth - lastHealth;
            LoyaltySystem.onHeal(data, diff);
            MaidWeaponItem.setMaidData(weaponStack, data);
        }
        // 检测受伤：血量减少 → 女仆心疼主人，好感度增加
        if (lastHealth != null && currentHealth < lastHealth) {
            float lost = lastHealth - currentHealth;
            data.addFavorability(Math.max(1, (int)(lost * MaidWeaponConfig.HURT_RECOVERY_MULTIPLIER.get())));
            MaidWeaponItem.setMaidData(weaponStack, data);
        }
        ChallengeTracker.setHealth(playerUUID, currentHealth);

        // 药房试剂 + 断剑 → 每60秒获得10秒生命恢复 I
        Long lastTick = POTION_VIAL_LAST_TICK.get(playerUUID);
        if (lastTick == null || gameTime - lastTick >= 1200) {
            boolean hasVial = false;
            boolean hasBlade = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack inv = player.getInventory().getItem(i);
                if (inv.is(ModItems.POTION_VIAL.get())) hasVial = true;
                if (inv.is(ModItems.BROKEN_BLADE.get())) hasBlade = true;
            }
            if (hasVial && hasBlade) {
                POTION_VIAL_LAST_TICK.put(playerUUID, gameTime);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            }
        }

        // 好感度 → 耐久度同步（耐久条显示好感度）
        int maxDmg = weaponStack.getMaxDamage();
        int targetDmg = Math.max(1, Math.min(maxDmg - 1, maxDmg - data.getFavorability()));
        if (weaponStack.getDamageValue() != targetDmg) {
            weaponStack.setDamageValue(targetDmg);
        }
    }


    // ==================== 工具方法 ====================

    /**
     * 获取实体的注册名
     */
    private static String getEntityId(Entity entity) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id.toString();
    }


}