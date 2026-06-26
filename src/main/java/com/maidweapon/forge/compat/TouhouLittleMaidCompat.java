package com.maidweapon.forge.compat;

import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 车万女仆兼容层 — 调用前检查 TLM 是否已加载，避免 ClassNotFound。
 * 所有实现委托给 {@link TouhouLittleMaidHelper}。
 */
public final class TouhouLittleMaidCompat {

    private static final boolean TLM_LOADED;

    static {
        boolean loaded = false;
        try {
            Class.forName("com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid");
            loaded = true;
            System.out.println("[MaidWeapon] Touhou Little Maid detected! TLM integration enabled.");
        } catch (ClassNotFoundException e) {
            System.out.println("[MaidWeapon] Touhou Little Maid not found. Running standalone mode.");
        }
        TLM_LOADED = loaded;
    }

    /** TLM 是否已安装 */
    public static boolean isTouhouLittleMaidLoaded() {
        return TLM_LOADED;
    }

    public static boolean isMaidEntity(Entity entity) {
        if (!TLM_LOADED) return false;
        return TouhouLittleMaidHelper.isMaidEntity(entity);
    }

    public static boolean convertMaidToWeapon(Player player, Entity entity, ItemStack weaponStack) {
        if (!TLM_LOADED) return false;
        return TouhouLittleMaidHelper.convertMaidToWeapon(player, entity, weaponStack);
    }

    public static boolean convertWeaponToMaid(Player player, ItemStack weaponStack) {
        if (!TLM_LOADED) return false;
        return TouhouLittleMaidHelper.convertWeaponToMaid(player, weaponStack);
    }

    public static InteractionResult onPlayerInteractWithMaid(Player player, Entity entity, InteractionHand hand) {
        if (!TLM_LOADED) return InteractionResult.PASS;
        return TouhouLittleMaidHelper.onPlayerInteractWithMaid(player, entity, hand);
    }

    public static InteractionResult onPlayerShiftRightClick(Player player, InteractionHand hand) {
        if (!TLM_LOADED) return InteractionResult.PASS;
        return TouhouLittleMaidHelper.onPlayerShiftRightClick(player, hand);
    }

    private TouhouLittleMaidCompat() {}
}
