package com.maidweapon.forge.item.variant;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;

/**
 * 女仆之刃变种 — 不同攻击/速度风格，共享 MaidWeaponData。
 *
 * 命名对照：
 *   鉄刀（Tetsuto）= 初始均衡型
 *   剛刀（Goto）= 高伤慢速
 *   迅刀（Jinto）= 快速连击
 *   堅刀（Kento）= 防御型
 */
public class MaidWeaponVariantItem extends MaidWeaponItem {

    public enum Variant {
        HEAVY(Tiers.DIAMOND, 3, -3.2f, "§e剛刀"),
        FAST(Tiers.GOLD, 0, -1.8f, "§b迅刀");
        public final Tiers tier;
        public final int damageMod;
        public final float speedMod;
        public final String prefix;

        Variant(Tiers tier, int damageMod, float speedMod, String prefix) {
            this.tier = tier; this.damageMod = damageMod;
            this.speedMod = speedMod; this.prefix = prefix;
        }
    }

    private final Variant variant;

    public MaidWeaponVariantItem(Variant variant) {
        super(variant.tier, variant.damageMod, variant.speedMod,
                new Properties().stacksTo(1).durability(MaidWeaponData.MAX_FAVORABILITY + 1));
        this.variant = variant;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (hasMaidData(stack)) {
            MaidWeaponData data = getMaidData(stack);
            return Component.literal("§d" + data.getMaidName() + " §f· " + variant.prefix + " Lv." + data.getLevel());
        }
        return Component.translatable("item.maid_weapon." + variant.name().toLowerCase());
    }
}
