package com.maidweapon.forge.item;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.data.MaidWeaponDataSerializer;
import com.maidweapon.common.sin.SinSlotManager;
import com.maidweapon.common.system.LoyaltySystem;
import com.maidweapon.common.legacy.LegacySinArchive;
import com.maidweapon.common.system.MonsterTierRegistry;
import com.maidweapon.forge.system.contract.ContractInteractionService;
import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * ========================================
 * 女仆武器物品类（Forge 端）
 * ========================================
 *
 * 核心物品，代表女仆变成的武器。
 * 成长系统：无死亡挑战制
 *   - 击杀更强的怪物（Tier更高）才能升级
 *   - 击杀过程中玩家不能死亡
 *
 * NBT 数据结构：
 *   MaidData/
 *     MaidName:           String  - 女仆名称
 *     Level:              int     - 等级 (1~10)
 *     Favorability:       int     - 好感度 (0~384)
 *     ContractResonance:  int     - 契约共鸣 (0~200)
 *     TotalKills:         int     - 总击杀数
 *     UnlockedTier:       int     - 已解锁最高Tier
 *     EnderDragonKills:   int     - 末影龙击杀数（无死亡）
 *     WitherKills:        int     - 凋灵击杀数（无死亡）
 */
public class MaidWeaponItem extends SwordItem {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NBT_MAID_DATA = "MaidData";
    private static final String NBT_MAID_ENTITY = "MaidEntityData";
    private static final String NBT_MAID_UUID = "MaidUUID";
    private static final String NBT_BINDING_ID = "MaidBindingId";
    private static final String NBT_SUPERSEDED = "MaidContractSuperseded";
    private static final String NBT_OWNER_UUID = "OwnerUUID";
    private static final String NBT_OWNER_NAME = "OwnerName";

    public MaidWeaponItem(Properties properties) {
        super(Tiers.IRON, 1, -2.4f, properties);
    }

    /** 子类构造器：自定义攻击/速度 */
    protected MaidWeaponItem(Tiers tier, int attackMod, float speedMod, Properties properties) {
        super(tier, attackMod, speedMod, properties);
    }

    // ==================== NBT 数据读写 ====================

    public static MaidWeaponData getMaidData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_MAID_DATA)) {
            return new MaidWeaponData();
        }

        CompoundTag maidTag = tag.getCompound(NBT_MAID_DATA);
        return MaidWeaponDataSerializer.fromValues(
                maidTag.getString(MaidWeaponDataSerializer.KEY_MAID_NAME),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_LEVEL),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_FAVORABILITY),
                maidTag.contains(MaidWeaponDataSerializer.KEY_RESONANCE)
                        ? maidTag.getInt(MaidWeaponDataSerializer.KEY_RESONANCE)
                        : MaidWeaponData.MAX_RESONANCE,
                maidTag.getInt(MaidWeaponDataSerializer.KEY_TOTAL_KILLS),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_UNLOCKED_TIER),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_ENDER_DRAGON_KILLS),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_WITHER_KILLS),
                maidTag.getString(MaidWeaponDataSerializer.KEY_EMBEDDED_SINS)
        );
    }

    public static void setMaidData(ItemStack stack, MaidWeaponData data) {
        CompoundTag maidTag = new CompoundTag();
        maidTag.putString(MaidWeaponDataSerializer.KEY_MAID_NAME, data.getMaidName());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_LEVEL, data.getLevel());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_FAVORABILITY, data.getFavorability());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_RESONANCE, data.getResonance());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_TOTAL_KILLS, data.getTotalKills());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_UNLOCKED_TIER, data.getUnlockedTier());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_ENDER_DRAGON_KILLS, data.getEnderDragonKills());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_WITHER_KILLS, data.getWitherKills());

        maidTag.putString(MaidWeaponDataSerializer.KEY_EMBEDDED_SINS,
                SinSlotManager.sinsToString(data.getEmbeddedSins()));

        CompoundTag rootTag = stack.getOrCreateTag();
        rootTag.put(NBT_MAID_DATA, maidTag);
    }

    public static boolean hasMaidData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_MAID_DATA);
    }

    /** 检查武器是否当前捕获了女仆实体（有 MaidEntityData 标签） */
    public static boolean hasMaidEntityData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return com.maidweapon.forge.system.MaidEntityDataCodec.hasData(tag);
    }

    /** Removes all maid-contract data while preserving the original weapon and its own NBT. */
    public static void clearMaidContract(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(NBT_MAID_DATA);
        com.maidweapon.forge.system.MaidEntityDataCodec.remove(tag);
        tag.remove(NBT_MAID_UUID);
        tag.remove(NBT_BINDING_ID);
        tag.remove(NBT_SUPERSEDED);
        tag.remove(NBT_OWNER_UUID);
        tag.remove(NBT_OWNER_NAME);
        tag.remove("MaidInfusionOriginalTask");
        tag.remove("MaidDeploymentLocation");
        tag.remove("MaidDeploymentRecoveryFailed");
        tag.remove("MaidInfusionTaczTaskFailure");
        tag.remove("MaidInfusionTaczAmmoLinkFailure");
        if (tag.isEmpty()) stack.setTag(null);
    }

    // ==================== 女仆绑定（一武器一女仆） ====================

    /**
     * 设置武器绑定的女仆 UUID（首次捕获时写入，永不删除）
     */
    public static void setBoundMaidUUID(ItemStack stack, String maidUUID) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(NBT_MAID_UUID)) {
            tag.putString(NBT_MAID_UUID, maidUUID);
        }
    }

    /**
     * 获取武器绑定的女仆 UUID
     */
    @Nullable
    public static String getBoundMaidUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_MAID_UUID)) return null;
        return tag.getString(NBT_MAID_UUID);
    }

    /** Unique identity for this concrete weapon/maid contract, separate from the maid UUID. */
    public static String ensureBindingId(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(NBT_BINDING_ID)) {
            tag.putString(NBT_BINDING_ID, java.util.UUID.randomUUID().toString());
        }
        return tag.getString(NBT_BINDING_ID);
    }

    @Nullable
    public static String getBindingId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_BINDING_ID)) return null;
        return tag.getString(NBT_BINDING_ID);
    }

    public static void setContractSuperseded(ItemStack stack, boolean superseded) {
        stack.getOrCreateTag().putBoolean(NBT_SUPERSEDED, superseded);
    }

    public static boolean isContractSuperseded(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(NBT_SUPERSEDED);
    }

    /** 检查实体 UUID 是否匹配武器绑定的女仆 */
    public static boolean isBoundMaid(ItemStack stack, Entity entity) {
        String boundUUID = getBoundMaidUUID(stack);
        if (boundUUID == null) return true; // 还没绑定，任何人都行
        return boundUUID.equals(entity.getStringUUID());
    }

    // ==================== 武器主人系统 ====================

    /**
     * 设置武器主人（首次捕获女仆时写入）
     */
    public static void setOwner(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        // 只写入一次，已有主人时不再覆盖
        if (!tag.contains(NBT_OWNER_UUID)) {
            tag.putUUID(NBT_OWNER_UUID, player.getUUID());
            tag.putString(NBT_OWNER_NAME, player.getScoreboardName());
        }
    }

    /**
     * 获取武器主人的 UUID
     */
    @Nullable
    public static String getOwnerUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_OWNER_UUID)) return null;
        return tag.getUUID(NBT_OWNER_UUID).toString();
    }

    /**
     * 获取武器主人的名字
     */
    @Nullable
    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_OWNER_NAME)) return null;
        return tag.getString(NBT_OWNER_NAME);
    }

    /**
     * 检查玩家是否为武器主人（或武器无主）
     */
    public static boolean isOwner(ItemStack stack, Player player) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_OWNER_UUID)) return true; // 无主武器，谁都能用
        return tag.getUUID(NBT_OWNER_UUID).equals(player.getUUID());
    }

    // ==================== Tooltip 显示 ====================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.title"));

        if (hasMaidData(stack)) {
            MaidWeaponData data = getMaidData(stack);
            boolean hasEntity = hasMaidEntityData(stack);
            if (isContractSuperseded(stack)) {
                tooltip.add(Component.translatable("maid_weapon.tooltip.superseded_contract"));
            }

            // 女仆名称 + 状态标记
            String statusColor = hasEntity ? "§a" : "§7";
            String statusSuffix = hasEntity ? "" : " §7(已释放)";
            tooltip.add(Component.translatable("maid_weapon.tooltip.maid_name",
                    statusColor + data.getMaidName() + statusSuffix));

            // 等级
            tooltip.add(Component.translatable("maid_weapon.tooltip.level", data.getLevel()));

            // 下一级升级条件（已释放时隐藏升级提示）
            if (hasEntity) {
                tooltip.add(Component.translatable("maid_weapon.tooltip.next_upgrade", data.getNextUpgradeHint()));
            }

            // 好感度
            tooltip.add(Component.translatable("maid_weapon.tooltip.favorability",
                    data.getFavorability(), LoyaltySystem.getFavorabilityTitle(data.getFavorability())));
            tooltip.add(Component.translatable("maid_weapon.tooltip.favorability_bonus",
                    String.format("%.1f", (data.getFavorabilityDamageMultiplier() - 1.0f) * 100.0f)));
            tooltip.add(Component.translatable("maid_weapon.tooltip.resonance",
                    data.getResonance(), MaidWeaponData.MAX_RESONANCE));

            // 战斗统计
            tooltip.add(Component.translatable("maid_weapon.tooltip.kills", data.getTotalKills()));

            // 已解锁最高Tier
            tooltip.add(Component.translatable("maid_weapon.tooltip.unlocked_tier",
                    MonsterTierRegistry.getTierName(data.getUnlockedTier())));

            // Boss击杀统计（如果有）
            if (data.getEnderDragonKills() > 0 || data.getWitherKills() > 0) {
                tooltip.add(Component.translatable("maid_weapon.tooltip.boss_kills",
                        data.getEnderDragonKills(), data.getWitherKills()));
            }

            // Legacy Part data is preserved for old worlds but is no longer active Core gameplay.
            if (LegacySinArchive.hasData(data)) {
                tooltip.add(Component.translatable(
                        "maid_weapon.tooltip.legacy_sin_data",
                        LegacySinArchive.entryCount(data)));
            }

            // 主人信息
            String ownerName = getOwnerName(stack);
            if (ownerName != null) {
                tooltip.add(Component.translatable("maid_weapon.tooltip.owner", ownerName));
            }

            // 释放/捕获提示
            boolean slashBlade = stack.getTag() != null && stack.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE);
            if (hasEntity) {
                tooltip.add(Component.translatable(slashBlade
                        ? "maid_weapon.tooltip.release_hint_sb"
                        : "maid_weapon.tooltip.release_hint"));
            } else {
                tooltip.add(Component.translatable("maid_weapon.tooltip.recapture_hint"));
            }

            // 按住 Shift 显示详细信息
            if (flag.isAdvanced()) {
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("§7─── 战斗加成 ───"));
                tooltip.add(Component.literal(
                        String.format("§7等级加成: §a+%.1f 攻击力", data.getAttackDamageBonus())
                ));
                tooltip.add(Component.literal(
                        String.format("§7好感伤害奖励: §a+%.1f%%",
                                (data.getFavorabilityDamageMultiplier() - 1.0f) * 100.0f)
                ));
                float totalDmg = (3.0f + data.getAttackDamageBonus()) * data.getFavorabilityDamageMultiplier();
                tooltip.add(Component.literal(
                        String.format("§7实际伤害: §f%.1f", totalDmg)
                ));
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("§7─── 成长机制 ───"));
                tooltip.add(Component.literal("§7使用此武器攻击怪物，"));
                tooltip.add(Component.literal("§7若在怪物死亡前未死亡，"));
                tooltip.add(Component.literal("§7即可根据怪物Tier提升等级。"));
            }

        } else {
            tooltip.add(Component.translatable("maid_weapon.tooltip.soul_slab_hint"));
        }
    }

    // ==================== 实体交互（右键女仆） ====================

    /**
     * 当玩家手持此物品右键一个实体时调用（在 TLM 的 mobInteract 链中触发）。
     *
     * TLM 的 EntityMaid.mobInteract() 执行链：
     *   InteractMaidEvent 投递 → stack.interactLivingEntity() ← 我们在这里拦截
     *   → 如果返回 SUCCESS（consumesAction=true），则不打开 TLM 女仆 GUI。
     *
     * 所以通过重写此方法，我们优雅地避开 TLM 的 GUI 打开逻辑，
     * 无需 EventPriority 冲突、无需反射订阅 TLM 内部事件。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        // A normal right-click on an already manifested contract belongs to TLM's GUI.
        // Sneaking explicitly hands the gesture to the contract interaction authority.
        if (hasMaidData(stack) && !player.isShiftKeyDown()) return InteractionResult.PASS;
        return ContractInteractionService.capture(player, target, stack);
    }

    // ==================== 右键使用（空气/方块） ====================

    /**
     * 右键使用（空气/方块）：潜行时释放已捕获的女仆。
     *
     * 玩家潜行 + 右键空气 → 从武器中释放女仆（保留武器等级/七罪/击杀数）
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            boolean sneak = player.isShiftKeyDown();
            boolean hasData = hasMaidData(stack);
            if (sneak && hasData) {
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        }

        LOGGER.info("[MaidWeapon] SERVER use() player={} hand={} shift={} hasData={}",
                player.getScoreboardName(), hand, player.isShiftKeyDown(), hasMaidData(stack));

        if (hasMaidData(stack) && !isOwner(stack, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.not_owner"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 潜行 + 右键空气：释放女仆（普通剑模式）
        // 拔刀剑模式使用潜行+Q（见 MaidWeaponDropHandler）
        if (player.isShiftKeyDown() && hasMaidData(stack)) {
            // 拔刀剑模式使用潜行+Q释放（见 MaidWeaponDropHandler），这里跳过
            boolean isSlashBladeMode = stack.getTag() != null && stack.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE);
            if (!isSlashBladeMode) {
                InteractionResult result = ContractInteractionService.recallHeld(player, hand);
                if (result.consumesAction()) {
                    return InteractionResultHolder.success(stack);
                }
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    // ==================== 工具属性 ====================

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairStack) {
        return repairStack.is(Items.GOLD_INGOT);
    }

    @Override
    public float getDamage() {
        return 3.0f;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasMaidEntityData(stack);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        if (hasMaidData(stack)) {
            int level = getMaidData(stack).getLevel();
            if (level >= 9) return Rarity.EPIC;
            if (level >= 7) return Rarity.RARE;
            if (level >= 4) return Rarity.UNCOMMON;
        }
        return Rarity.COMMON;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (hasMaidData(stack)) {
            MaidWeaponData data = getMaidData(stack);
            return Component.literal("§d" + data.getMaidName() + " §f· 女仆之刃 Lv." + data.getLevel());
        }
        return Component.translatable("item.maid_weapon.maid_sword_empty");
    }
}
