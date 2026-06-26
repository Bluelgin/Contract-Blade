package com.maidweapon.forge.recipe;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.sin.SinSlotManager;
import com.maidweapon.common.sin.SinType;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.item.sin.SinBaseItem;
import com.maidweapon.forge.item.sin.TearOfRepentanceItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 铁砧处理器（简化版）：仅处理罪恶嵌入/取出/充能。
 *
 * v2.0 变更：移除了女仆绑定/解绑功能（改用直接右键捕获/释放）。
 * 保留以下功能：
 *   - 嵌入罪恶：左槽武器 + 右槽罪恶物品
 *   - 取出罪恶：左槽武器 + 右槽忏悔之泪
 *   - 充能：   左槽忏悔之泪 + 右槽下界之星
 */
@Mod.EventBusSubscriber
public class SinAnvilHandler {

    private static final int EMBED_COST = 3;
    private static final int REMOVE_COST = 3;
    private static final int RECHARGE_COST = 1;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;

        // 场景1：嵌入罪恶
        if (left.getItem() instanceof MaidWeaponItem && right.getItem() instanceof SinBaseItem) {
            handleEmbedSin(event, left, right);
            return;
        }

        // 场景2：取出罪恶
        if (left.getItem() instanceof MaidWeaponItem && right.getItem() instanceof TearOfRepentanceItem) {
            handleRemoveSin(event, left, right);
            return;
        }

        // 场景3：忏悔之泪充能
        if (left.getItem() instanceof TearOfRepentanceItem && right.is(net.minecraft.world.item.Items.NETHER_STAR)) {
            handleRechargeTear(event, left);
            return;
        }
    }

    private static void handleEmbedSin(AnvilUpdateEvent event, ItemStack weaponStack, ItemStack sinStack) {
        MaidWeaponData data;
        if (!MaidWeaponItem.hasMaidData(weaponStack)) {
            data = new MaidWeaponData("未绑定女仆");
            MaidWeaponItem.setMaidData(weaponStack, data);
        } else {
            data = MaidWeaponItem.getMaidData(weaponStack);
        }
        SinType sinType = ((SinBaseItem) sinStack.getItem()).getSinType();
        if (!SinSlotManager.canEmbed(data.getEmbeddedSins(), sinType)) return;

        ItemStack result = weaponStack.copy();
        MaidWeaponData resultData = MaidWeaponItem.getMaidData(result);
        resultData.embedSin(sinType);
        MaidWeaponItem.setMaidData(result, resultData);
        event.setOutput(result);
        event.setCost(EMBED_COST);
        event.setMaterialCost(1);
    }

    private static void handleRemoveSin(AnvilUpdateEvent event, ItemStack weaponStack, ItemStack tearStack) {
        if (!MaidWeaponItem.hasMaidData(weaponStack)) return;
        if (!TearOfRepentanceItem.hasCharges(tearStack)) return;
        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        if (!data.hasAnySin()) return;

        ItemStack resultWeapon = weaponStack.copy();
        MaidWeaponData resultData = MaidWeaponItem.getMaidData(resultWeapon);
        resultData.setEmbeddedSins(new java.util.ArrayList<>());
        MaidWeaponItem.setMaidData(resultWeapon, resultData);
        event.setOutput(resultWeapon);
        event.setCost(REMOVE_COST);
        event.setMaterialCost(1);
    }

    private static void handleRechargeTear(AnvilUpdateEvent event, ItemStack tearStack) {
        ItemStack result = tearStack.copy();
        TearOfRepentanceItem.recharge(result, 1);
        event.setOutput(result);
        event.setCost(RECHARGE_COST);
        event.setMaterialCost(1);
    }
}
