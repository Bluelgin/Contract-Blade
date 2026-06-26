package com.maidweapon.forge.init;

import com.maidweapon.common.MaidWeaponConstants;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.item.sin.*;
import com.maidweapon.forge.item.variant.MaidWeaponVariantItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * ========================================
 * 物品注册表（Forge 端）
 * ========================================
 *
 * 使用 Forge 的 DeferredRegister（延迟注册）系统来注册物品。
 * DeferredRegister 的好处：
 *   1. 线程安全
 *   2. 自动处理注册顺序
 *   3. 提供 RegistryObject 用于延迟获取注册后的物品实例
 *
 * 所有物品都在这里集中管理，方便查找和维护。
 */
public final class ModItems {

    /**
     * 物品延迟注册器
     * 第一个参数是注册类型（物品），第二个参数是 modId
     * 会自动注册到 minecraft:item 注册表中
     */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidWeaponConstants.MOD_ID);

    /**
     * 女仆武器 - 主手剑
     *
     * RegistryObject 是一个延迟引用，游戏完全加载后才能获取到真实的 Item 实例。
     * 这样做可以避免循环依赖和注册顺序问题。
     */
    public static final RegistryObject<Item> MAID_SWORD = ITEMS.register(
            "maid_sword",
            () -> new MaidWeaponItem(
                    new Item.Properties()
                            .stacksTo(1)
                            .durability(MaidWeaponData.MAX_FAVORABILITY + 1)
            )
    );

    // ==================== 女仆之刃变种 ====================

    public static final RegistryObject<Item> MAID_HEAVY = ITEMS.register(
            "maid_heavy", () -> new MaidWeaponVariantItem(MaidWeaponVariantItem.Variant.HEAVY));
    public static final RegistryObject<Item> MAID_FAST = ITEMS.register(
            "maid_fast", () -> new MaidWeaponVariantItem(MaidWeaponVariantItem.Variant.FAST));

    // ==================== 剧情道具 ====================

    public static final RegistryObject<Item> BROKEN_BLADE = ITEMS.register(
            "broken_blade",
            () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.literal("§7插在石台上的断剑。刃上刻着"));
                    tooltip.add(Component.literal("§7一行模糊的小字——"));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§8「一式，卒于北境。终年未等到主人。」"));
                }
            }
    );

    /** 神树之实 — 神树掉落，拾取时触发记忆 + 好感度 */
    public static final RegistryObject<Item> SACRED_FRUIT = ITEMS.register(
            "sacred_fruit",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.literal("§7从雪山神树上摘下的果实。"));
                    tooltip.add(Component.literal("§7握在掌心的时候，能感到细微的温热。"));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§8「她种这棵树的时候，手被冻土磨破了。"));
                    tooltip.add(Component.literal("§8 但她没有停下来。」"));
                }
            }
    );

    /** 药房试剂 — 药房拾取，拾取时触发记忆 + 回血 */
    public static final RegistryObject<Item> POTION_VIAL = ITEMS.register(
            "potion_vial",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.literal("§7标签上写着「一日三次」。"));
                    tooltip.add(Component.literal("§7笔迹很认真，但药的份量像是"));
                    tooltip.add(Component.literal("§7为某个特定的人准备的。"));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§8「她已经很久没有见过那个人受伤了。」"));
                }
            }
    );

    public static final RegistryObject<Item> HAIR_RIBBON = ITEMS.register(
            "hair_ribbon",
            () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.literal("§7褪色的蓝白绳结。边缘已经起毛了。"));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§8「她说这是主人送她的。她戴了一辈子。」"));
                }
            }
    );

    public static final RegistryObject<Item> ISSHIKI_NECKLACE = ITEMS.register(
            "isshiki_necklace",
            () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.literal("§7刻着一式名字的旧吊坠。边缘磨损得厉害。"));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§8「她把储物箱里唯一一件没交给主人的东西留给了自己。」"));
                }
            }
    );

    // ==================== 消耗品 ====================

    public static final RegistryObject<Item> SPIRIT_CRYSTAL = ITEMS.register(
            "spirit_crystal",
            () -> new Item(new Item.Properties().stacksTo(16)) {
                @Override
                public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                    ItemStack crystal = player.getItemInHand(hand);
                    // 查找主手或副手的女仆之刃
                    ItemStack weapon = player.getMainHandItem();
                    if (!(weapon.getItem() instanceof MaidWeaponItem) || !MaidWeaponItem.hasMaidData(weapon)) {
                        weapon = player.getOffhandItem();
                        if (!(weapon.getItem() instanceof MaidWeaponItem) || !MaidWeaponItem.hasMaidData(weapon)) {
                            return InteractionResultHolder.pass(crystal);
                        }
                    }
                    if (level.isClientSide) return InteractionResultHolder.success(crystal);

                    MaidWeaponData data = MaidWeaponItem.getMaidData(weapon);
                    int current = data.getFavorability();
                    if (current >= MaidWeaponData.MAX_FAVORABILITY) {
                        player.displayClientMessage(
                                Component.literal("§e好感度已满，无法继续提升"), true);
                        return InteractionResultHolder.fail(crystal);
                    }
                    data.addFavorability(50);
                    MaidWeaponItem.setMaidData(weapon, data);
                    crystal.shrink(1);
                    player.displayClientMessage(
                            Component.literal("§a好感度 +50！"), true);
                    return InteractionResultHolder.success(crystal);
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.literal("§7右键女仆之刃使用"));
                    tooltip.add(Component.literal("§a好感度 +50"));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§8「封存着一缕温暖的灵力。」"));
                }
            }
    );

    /** 试斩 — 拔刀剑测试物品（桩代码，未集成时用普通剑代） */
    public static final RegistryObject<Item> TEST_SLASHBLADE = ITEMS.register(
            "test_slashblade",
            () -> new MaidWeaponItem(
                    new Item.Properties()
                            .stacksTo(1)
                            .durability(MaidWeaponData.MAX_FAVORABILITY + 1)
            ) {
                @Override
                public Component getName(ItemStack stack) {
                    if (MaidWeaponItem.hasMaidData(stack)) {
                        MaidWeaponData data = MaidWeaponItem.getMaidData(stack);
                        return Component.literal("§d" + data.getMaidName() + " §f· 试斩 Lv." + data.getLevel());
                    }
                    return Component.translatable("item.maid_weapon.test_slashblade");
                }
                @Override
                public void onCraftedBy(ItemStack stack, Level level, Player player) {
                    stack.getOrCreateTag().putBoolean(MaidWeaponConstants.TAG_SLASHBLADE_MODE, true);
                }
            }
    );

    // ==================== 七宗罪物品 ====================

    /** 傲慢之冠 */
    public static final RegistryObject<Item> SIN_PRIDE = ITEMS.register(
            "sin_pride", PrideCrownItem::new);

    /** 暴怒之核 */
    public static final RegistryObject<Item> SIN_WRATH = ITEMS.register(
            "sin_wrath", WrathCoreItem::new);

    /** 懒惰之息 */
    public static final RegistryObject<Item> SIN_SLOTH = ITEMS.register(
            "sin_sloth", SlothBreathItem::new);

    /** 贪婪之眼 */
    public static final RegistryObject<Item> SIN_GREED = ITEMS.register(
            "sin_greed", GreedEyeItem::new);

    /** 暴食之胃 */
    public static final RegistryObject<Item> SIN_GLUTTONY = ITEMS.register(
            "sin_gluttony", GluttonyStomachItem::new);

    /** 色欲之链 */
    public static final RegistryObject<Item> SIN_LUST = ITEMS.register(
            "sin_lust", LustChainItem::new);

    /** 嫉妒之瞳 */
    public static final RegistryObject<Item> SIN_ENVY = ITEMS.register(
            "sin_envy", EnvyPupilItem::new);

    // ==================== 忏悔之泪 ====================

    /** 忏悔之泪 */
    public static final RegistryObject<Item> TEAR_OF_REPENTANCE = ITEMS.register(
            "tear_of_repentance", TearOfRepentanceItem::new);

    /** 契约之泪·宵——结局物品 */
    public static final RegistryObject<Item> TEAR_OF_XIAO = ITEMS.register(
            "tear_of_xiao",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                                            List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§d§l✦ 契约之泪·宵 ✦"));
                    tooltip.add(Component.literal("§7凝视它的时候，你听到了一个声音——"));
                    tooltip.add(Component.literal("§7\n§7「……对不起。」"));
                    tooltip.add(Component.literal("§8只有一句。但你等了这句话很久。"));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§8握在手中有一丝温热"));
                }
            }
    );

    // 私有构造器
    private ModItems() {}
}
