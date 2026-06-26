package com.maidweapon.forge.init;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * ========================================
 * 创造模式物品栏注册（Forge 端）
 * ========================================
 *
 * 在创造模式中添加一个新的物品栏标签页，方便玩家获取本 Mod 的物品。
 */
public final class ModCreativeTab {

    /**
     * 创造模式标签页延迟注册器
     */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaidWeaponConstants.MOD_ID);

    /**
     * 主标签页 - 包含所有女仆武器物品
     */
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MaidWeaponConstants.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.MAID_SWORD.get()))
                    .displayItems((params, output) -> {
                        // 主武器
                        output.accept(ModItems.MAID_SWORD.get());
                        // 七宗罪
                        output.accept(ModItems.SIN_PRIDE.get());
                        output.accept(ModItems.SIN_WRATH.get());
                        output.accept(ModItems.SIN_SLOTH.get());
                        output.accept(ModItems.SIN_GREED.get());
                        output.accept(ModItems.SIN_GLUTTONY.get());
                        output.accept(ModItems.SIN_LUST.get());
                        output.accept(ModItems.SIN_ENVY.get());
                        // 忏悔之泪
                        output.accept(ModItems.TEAR_OF_REPENTANCE.get());
                        output.accept(ModItems.TEAR_OF_XIAO.get());
                        // 剧情道具
                        output.accept(ModItems.BROKEN_BLADE.get());
                        output.accept(ModItems.SPIRIT_CRYSTAL.get());
                        output.accept(ModItems.HAIR_RIBBON.get());
                        output.accept(ModItems.ISSHIKI_NECKLACE.get());
                        output.accept(ModItems.SACRED_FRUIT.get());
                        output.accept(ModItems.POTION_VIAL.get());
                        // 女仆之刃变种
                        output.accept(ModItems.MAID_HEAVY.get());
                        output.accept(ModItems.MAID_FAST.get());
                        // 拔刀剑测试物品
                        output.accept(ModItems.TEST_SLASHBLADE.get());
                    })
                    .build()
    );

    private ModCreativeTab() {}
}