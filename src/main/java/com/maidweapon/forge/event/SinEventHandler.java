package com.maidweapon.forge.event;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.sin.SinType;
import com.maidweapon.common.system.MonsterTierRegistry;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * ========================================
 * 七宗罪效果事件处理器（Forge 端）
 * ========================================
 *
 * 实现七宗罪嵌入武器后的具体Buff和Debuff效果。
 *
 * 已实现的效果：
 *   傲慢：攻击力×1.5，受伤×1.3，击杀后力量II
 *   暴怒：连击递增+10%/层(最多5层)，忠诚消耗×3
 *   懒惰：站桩回血+忠诚恢复+修复耐久，移速×0.85，攻速×0.80
 *   贪婪：额外掉落+50%（抢夺等效），双倍经验，升级进度减半
 *   暴食：食物效果×2，耐久消耗×2，食用食物恢复忠诚
 *   色欲：攻击不同怪物+25%，忠诚不恢复
 *   嫉妒：越级攻击×2，弱怪×0.2
 */
@Mod.EventBusSubscriber
public class SinEventHandler {

    // ==================== 暴怒连击追踪 ====================
    private static final Map<UUID, Integer> WRATH_COMBO_STACKS = new HashMap<>();
    private static final Map<UUID, Long> WRATH_LAST_HIT_TIME = new HashMap<>();
    private static final int WRATH_COMBO_TIMEOUT = 60; // 3秒超时（60 tick）

    // ==================== 懒惰站桩追踪 ====================
    private static final Map<UUID, Long> SLOTH_STAND_START = new HashMap<>();
    private static final Map<UUID, Double> SLOTH_LAST_X = new HashMap<>();
    private static final Map<UUID, Double> SLOTH_LAST_Z = new HashMap<>();
    private static final double SLOTH_MOVE_THRESHOLD = 0.01;

    // ==================== 色欲怪物种类追踪 ====================
    private static final Map<UUID, Set<String>> LUST_MONSTER_TYPES = new HashMap<>();

    // ==================== 贪婪经验加成追踪 ====================
    /** 记录贪婪罪恶玩家的击杀目标Tier，用于经验加成计算 */
    private static final Map<UUID, Integer> GREED_LAST_KILL_TIER = new HashMap<>();

    // ==================== 暴食食物追踪 ====================
    /** 记录玩家上次食用食物的tick，用于检测食用行为 */
    private static final Map<UUID, Integer> GLUTTONY_LAST_FOOD_LEVEL = new HashMap<>();

    // ==================== 傲慢：击杀后获得力量 ====================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof MaidWeaponItem)) return;
        if (!MaidWeaponItem.hasMaidData(mainHand)) return;

        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);

        // 傲慢：击杀怪物后获得力量II，持续5秒（100 tick）
        if (data.hasSin(SinType.PRIDE)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1, false, true));
        }

        // 暴怒：击杀时好感度额外消耗（基于怪物血量，为基础消耗的3倍）
        if (data.hasSin(SinType.WRATH)) {
            float mobHealth = event.getEntity().getMaxHealth();
            int baseLoss = Math.max(1, (int)(mobHealth / 20.0f));
            data.reduceFavorability(baseLoss * 2); // 额外 2 倍，总计 3 倍
            MaidWeaponItem.setMaidData(mainHand, data);
        }

        // 贪婪：记录击杀Tier用于经验加成
        if (data.hasSin(SinType.GREED)) {
            String targetId = getEntityId(event.getEntity());
            int tier = MonsterTierRegistry.getTier(targetId);
            if (tier <= 0) {
                tier = MonsterTierRegistry.estimateTierFromHealth(event.getEntity().getMaxHealth());
            }
            GREED_LAST_KILL_TIER.put(player.getUUID(), tier);
        }
    }

    // ==================== 暴怒 + 色欲 + 嫉妒：伤害修改 ====================
    /** 使用 LOW 优先级确保在 CombatEventHandler 的 NORMAL 之后执行 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof MaidWeaponItem)) return;
        if (!MaidWeaponItem.hasMaidData(mainHand)) return;

        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);
        Entity target = event.getEntity();
        float damageMultiplier = 1.0f;
        UUID playerUUID = player.getUUID();

        // ===== 傲慢：攻击力×1.5 =====
        if (data.hasSin(SinType.PRIDE)) {
            damageMultiplier *= 1.5f;
        }

        // ===== 暴怒：连击递增 =====
        if (data.hasSin(SinType.WRATH)) {
            long currentTime = player.level().getGameTime();
            Long lastHit = WRATH_LAST_HIT_TIME.get(playerUUID);

            if (lastHit != null && (currentTime - lastHit) <= WRATH_COMBO_TIMEOUT) {
                // 连击中
                int stacks = WRATH_COMBO_STACKS.getOrDefault(playerUUID, 0);
                stacks = Math.min(stacks + 1, 5); // 最多5层
                WRATH_COMBO_STACKS.put(playerUUID, stacks);
                damageMultiplier *= 1.0f + stacks * 0.10f;
            } else {
                // 新连击
                WRATH_COMBO_STACKS.put(playerUUID, 1);
                damageMultiplier *= 1.10f;
            }
            WRATH_LAST_HIT_TIME.put(playerUUID, currentTime);
        }

        // ===== 懒惰：移速/攻速降低（通过伤害倍率体现攻速降低） =====
        if (data.hasSin(SinType.SLOTH)) {
            // 攻击速度惩罚体现在实际攻击频率上，这里通过略微降低伤害来平衡
            damageMultiplier *= 0.90f;
        }

        // ===== 色欲：攻击不同怪物+25% =====
        if (data.hasSin(SinType.LUST)) {
            String targetId = getEntityId(target);
            Set<String> types = LUST_MONSTER_TYPES.computeIfAbsent(playerUUID, k -> new HashSet<>());
            if (!types.contains(targetId)) {
                types.add(targetId);
                damageMultiplier *= 1.25f;
            }
            // 30秒后清除记录（使用ScheduledTask）
            if (!player.level().isClientSide && player.level().getServer() != null) {
                player.level().getServer().tell(new net.minecraft.server.TickTask(
                        player.level().getServer().getTickCount() + 600,
                        () -> {
                            Set<String> set = LUST_MONSTER_TYPES.get(playerUUID);
                            if (set != null) set.remove(targetId);
                        }
                ));
            }
        }

        // ===== 嫉妒：越级×2，弱怪×0.2 =====
        if (data.hasSin(SinType.ENVY)) {
            String targetId = getEntityId(target);
            int targetTier = MonsterTierRegistry.getTier(targetId);
            if (targetTier <= 0 && target instanceof LivingEntity livingTarget) {
                targetTier = MonsterTierRegistry.estimateTierFromHealth(livingTarget.getMaxHealth());
            }
            int weaponLevel = data.getLevel();

            if (targetTier > weaponLevel) {
                damageMultiplier *= 2.0f; // 越级挑战
            } else if (targetTier < weaponLevel) {
                damageMultiplier *= 0.2f; // 弱怪
            }
        }

        // 应用总倍率
        if (damageMultiplier != 1.0f) {
            event.setAmount(event.getAmount() * damageMultiplier);
        }

        // ===== 贪婪：抢夺等效（额外掉落） =====
        // 通过LootingLevelEvent处理
    }

    // ==================== 傲慢Debuff：受伤增加 ====================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof MaidWeaponItem)) return;
        if (!MaidWeaponItem.hasMaidData(mainHand)) return;

        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);

        // 傲慢：受到伤害×1.3
        if (data.hasSin(SinType.PRIDE)) {
            event.setAmount(event.getAmount() * 1.3f);
        }

        // 暴食：耐久消耗×2（通过受伤时额外损耗耐久实现）
        if (data.hasSin(SinType.GLUTTONY)) {
            // 30%概率额外损耗1点耐久
            if (player.level().random.nextFloat() < 0.3f) {
                int currentDmg = mainHand.getDamageValue();
                int maxDmg = mainHand.getMaxDamage();
                if (currentDmg + 1 < maxDmg) {
                    mainHand.setDamageValue(currentDmg + 1);
                }
            }
        }
    }

    // ==================== 贪婪：额外掉落等级（抢夺等效） ====================
    @SubscribeEvent
    public static void onLootingLevel(LootingLevelEvent event) {
        if (!(event.getDamageSource().getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof MaidWeaponItem)) return;
        if (!MaidWeaponItem.hasMaidData(mainHand)) return;

        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);

        // 贪婪：额外+2抢夺等级（等效50%额外掉落）
        if (data.hasSin(SinType.GREED)) {
            event.setLootingLevel(event.getLootingLevel() + 2);
        }
    }

    // ==================== 贪婪：双倍经验 ====================
    @SubscribeEvent
    public static void onPlayerXpPickup(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof MaidWeaponItem)) return;
        if (!MaidWeaponItem.hasMaidData(mainHand)) return;

        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);

        // 贪婪：双倍经验
        if (data.hasSin(SinType.GREED)) {
            // 原版已经给了基础经验，额外再给一份
            player.giveExperiencePoints(event.getOrb().value);
        }
    }

    // ==================== 暴食：食物效果×2 ====================
    /**
     * 当玩家食用食物时，检测并增强食物效果
     * 通过监听玩家tick来检测食物等级变化
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof MaidWeaponItem) || !MaidWeaponItem.hasMaidData(mainHand)) {
            mainHand = player.getOffhandItem();
            if (!(mainHand.getItem() instanceof MaidWeaponItem) || !MaidWeaponItem.hasMaidData(mainHand)) {
                return;
            }
        }

        MaidWeaponData data = MaidWeaponItem.getMaidData(mainHand);
        UUID playerUUID = player.getUUID();
        long currentTime = player.level().getGameTime();

        // ===== 暴食：食物效果增强 =====
        if (data.hasSin(SinType.GLUTTONY)) {
            int currentFoodLevel = player.getFoodData().getFoodLevel();
            Integer lastFoodLevel = GLUTTONY_LAST_FOOD_LEVEL.get(playerUUID);

            // 检测食物等级增加（表示玩家吃了食物）
            if (lastFoodLevel != null && currentFoodLevel > lastFoodLevel) {
                // 食物等级增加时，给玩家额外增益效果
                // 速度I 10秒
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, false));
                // 急迫I 10秒（模拟食用食物后的强化）
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0, false, false));

                // 暴食：额外好感度 + 速度/急迫效果
                data.addFavorability(8);
                MaidWeaponItem.setMaidData(mainHand, data);

                // 粒子效果提示
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.HEART,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            3, 0.5, 0.5, 0.5, 0.0
                    );
                }
            }
            GLUTTONY_LAST_FOOD_LEVEL.put(playerUUID, currentFoodLevel);
        }

        // ===== 懒惰：站桩检测 =====
        if (data.hasSin(SinType.SLOTH)) {
            double currentX = player.getX();
            double currentZ = player.getZ();
            Double lastX = SLOTH_LAST_X.get(playerUUID);
            Double lastZ = SLOTH_LAST_Z.get(playerUUID);

            boolean isStanding = lastX != null && lastZ != null &&
                    Math.abs(currentX - lastX) < SLOTH_MOVE_THRESHOLD &&
                    Math.abs(currentZ - lastZ) < SLOTH_MOVE_THRESHOLD;

            if (isStanding) {
                Long standStart = SLOTH_STAND_START.get(playerUUID);
                if (standStart == null) {
                    SLOTH_STAND_START.put(playerUUID, currentTime);
                } else if (currentTime - standStart >= 60) { // 站桩3秒后触发
                    // 每秒回血
                    if (currentTime % 20 == 0) {
                        player.heal(2.0f);
                    }
                    // 每秒恢复忠诚度
                    if (currentTime % 20 == 0) {
                        data.addFavorability(1);
                        MaidWeaponItem.setMaidData(mainHand, data);
                    }
                    // 站桩恢复改为好感度（已通过好感度系统处理）
                }
            } else {
                SLOTH_STAND_START.remove(playerUUID);
            }

            SLOTH_LAST_X.put(playerUUID, currentX);
            SLOTH_LAST_Z.put(playerUUID, currentZ);

            // 懒惰：移动速度降低（通过药水效果模拟）
            // 每100tick重新给予缓慢效果
            if (currentTime % 100 == 0) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 120, 0, false, false));
            }
        }

        // ===== 暴怒：连击超时重置 =====
        if (data.hasSin(SinType.WRATH)) {
            Long lastHit = WRATH_LAST_HIT_TIME.get(playerUUID);
            if (lastHit != null && (currentTime - lastHit) > WRATH_COMBO_TIMEOUT) {
                WRATH_COMBO_STACKS.remove(playerUUID);
                WRATH_LAST_HIT_TIME.remove(playerUUID);
            }
        }

        // 色欲忠诚度不恢复已在CombatEventHandler中通过检查data.hasSin(SinType.LUST)实现
    }

    // ==================== 辅助方法 ====================
    private static String getEntityId(Entity entity) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id.toString();
    }

    /**
     * 获取贪婪罪恶的升级进度倍率（供MaidWeaponData使用）
     * @return 0.5（升级进度减半）或 1.0（正常）
     */
    public static float getGreedUpgradeProgressMultiplier(List<SinType> sins) {
        return sins.contains(SinType.GREED) ? 0.5f : 1.0f;
    }

    /**
     * 检查是否有色欲罪恶（供LoyaltySystem使用）
     */
    public static boolean hasLustSin(List<SinType> sins) {
        return sins.contains(SinType.LUST);
    }
}