package com.maidweapon.forge.item;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.system.contract.ContractInteractionService;
import com.maidweapon.forge.system.contract.ContractCarrierData;
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
    public MaidWeaponItem(Properties properties) {
        super(Tiers.IRON, 1, -2.4f, properties);
    }

    /** 子类构造器：自定义攻击/速度 */
    protected MaidWeaponItem(Tiers tier, int attackMod, float speedMod, Properties properties) {
        super(tier, attackMod, speedMod, properties);
    }

    // Compatibility forwarding methods: external addons may keep using this item API.

    public static MaidWeaponData getMaidData(ItemStack stack) {
        return ContractCarrierData.getMaidData(stack);
    }

    public static void setMaidData(ItemStack stack, MaidWeaponData data) {
        ContractCarrierData.setMaidData(stack, data);
    }

    public static boolean hasMaidData(ItemStack stack) {
        return ContractCarrierData.hasMaidData(stack);
    }

    public static boolean hasMaidEntityData(ItemStack stack) {
        return ContractCarrierData.hasMaidEntityData(stack);
    }

    public static void clearMaidContract(ItemStack stack) {
        ContractCarrierData.clearMaidContract(stack);
    }

    public static void setBoundMaidUUID(ItemStack stack, String maidUUID) {
        ContractCarrierData.setBoundMaidUUID(stack, maidUUID);
    }

    public static String getBoundMaidUUID(ItemStack stack) {
        return ContractCarrierData.getBoundMaidUUID(stack);
    }

    public static String ensureBindingId(ItemStack stack) {
        return ContractCarrierData.ensureBindingId(stack);
    }

    public static String getBindingId(ItemStack stack) {
        return ContractCarrierData.getBindingId(stack);
    }

    public static void setContractSuperseded(ItemStack stack, boolean superseded) {
        ContractCarrierData.setContractSuperseded(stack, superseded);
    }

    public static boolean isContractSuperseded(ItemStack stack) {
        return ContractCarrierData.isContractSuperseded(stack);
    }

    public static boolean isBoundMaid(ItemStack stack, Entity entity) {
        return ContractCarrierData.isBoundMaid(stack, entity);
    }

    public static void setOwner(ItemStack stack, Player player) {
        ContractCarrierData.setOwner(stack, player);
    }

    public static String getOwnerUUID(ItemStack stack) {
        return ContractCarrierData.getOwnerUUID(stack);
    }

    public static String getOwnerName(ItemStack stack) {
        return ContractCarrierData.getOwnerName(stack);
    }

    public static boolean isOwner(ItemStack stack, Player player) {
        return ContractCarrierData.isOwner(stack, player);
    }

    // ==================== Tooltip 显示 ====================

    /**
     * Bound contract presentation is composed by the client-only contract tooltip layer.
     * The item itself only owns the unbound hint so common item code never depends on
     * client key-state classes.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (hasMaidData(stack)) return;

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.title"));
        tooltip.add(Component.translatable("maid_weapon.tooltip.direct_bind_hint"));
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
        // Once bound, this item behaves like every Contract Table carrier:
        // deployment owns manifestation/recall and right-click belongs to TLM.
        if (hasMaidData(stack)) return InteractionResult.PASS;
        return ContractInteractionService.capture(player, target, stack);
    }

    // ==================== 右键使用（空气/方块） ====================

    /**
     * Bound Contract Blades deliberately do not own a manual summon/recall
     * gesture. They participate in the same main-hand deployment state machine
     * as weapons created through the Contract Table.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
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
