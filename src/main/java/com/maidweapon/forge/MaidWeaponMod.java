package com.maidweapon.forge;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.compat.TlmReflection;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.init.ModCreativeTab;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.init.ModBlocks;
import com.maidweapon.forge.init.ModMenus;
import com.maidweapon.forge.init.ModRecipeSerializers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * ========================================
 * MaidWeapon Mod 主类（Forge 端）
 * ========================================
 *
 * v3.0 变更：通过动态订阅 TLM 的 InteractMaidEvent 实现女仆捕获。
 * InteractMaidEvent 是 TLM 为附属 mod 预留的官方扩展点：
 *   当玩家右键自己驯服的女仆时，TLM 先投递此事件，再决定是否打开 GUI。
 *   我们在此事件中捕获女仆并取消事件 → TLM 不会打开 GUI。
 *
 * 这样做的好处：
 *   1. 使用 TLM 官方 API 接口，不依赖私有方法反射
 *   2. 不与其他 mod 的事件优先级冲突
 *   3. 生产环境不受混淆影响（事件系统是 Forge 基础设施）
 */
@Mod(MaidWeaponConstants.MOD_ID)
public class MaidWeaponMod {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * TLM InteractMaidEvent 的完整类名（反射用）
     */
    private static final String INTERACT_MAID_EVENT_CLASS =
            "com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent";

    public MaidWeaponMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置文件
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, MaidWeaponConfig.SPEC);
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        TaczCompat.bootstrap(modBus);

        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("[{}] Mod 加载中... 版本: {}", MaidWeaponConstants.MOD_ID, MaidWeaponConstants.MOD_VERSION);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[MaidWeapon] TLM compatibility: {}", TlmReflection.diagnostics());
            LOGGER.info("[MaidWeapon] SlashBlade compatibility: {}", SlashBladeCompat.diagnostics());
            LOGGER.info("[MaidWeapon] TACZ compatibility: {}",
                    TaczCompat.isLoaded() ? "enabled" : "not installed");
            // ============================================================
            // 动态订阅 TLM 的 InteractMaidEvent
            // ============================================================
            // InteractMaidEvent 是 TLM 在 mobInteract() 中投递的 Forge 事件。
            // 当玩家右键自己驯服的女仆时触发，Cancelable。
            // 如果此事件被取消，TLM 不会打开女仆 GUI。
            //
            // 我们通过反射动态订阅它，避免编译时依赖 TLM。
            // ============================================================
            try {
                Class<?> eventClass = Class.forName(INTERACT_MAID_EVENT_CLASS);
                Method getPlayer = eventClass.getMethod("getPlayer");
                Method getMaid = eventClass.getMethod("getMaid");
                Method getStack = eventClass.getMethod("getStack");

                // 获取 MinecraftForge.EVENT_BUS 的 addListener(EventPriority, boolean, Class, Consumer)
                Method addListenerMethod = null;
                for (Method m : MinecraftForge.EVENT_BUS.getClass().getMethods()) {
                    if (m.getName().equals("addListener") && m.getParameterCount() == 4
                            && m.getParameterTypes()[0] == EventPriority.class
                            && m.getParameterTypes()[1] == boolean.class
                            && m.getParameterTypes()[2] == Class.class) {
                        addListenerMethod = m;
                        break;
                    }
                }

                if (addListenerMethod == null) {
                    LOGGER.warn("[MaidWeapon] Could not find addListener method on EVENT_BUS");
                    return;
                }

                Consumer onInteract = evt -> {
                    try {
                        Player player = (Player) getPlayer.invoke(evt);
                        Entity maid = (Entity) getMaid.invoke(evt);
                        ItemStack stack = (ItemStack) getStack.invoke(evt);

                        if (!(stack.getItem() instanceof MaidWeaponItem)) return;
                        ItemStack targetStack = stack;

                        // Preserve TLM's normal maid GUI after manifestation.
                        // Sneak-right-click explicitly requests contract recall.
                        if (MaidWeaponItem.hasMaidData(targetStack) && !player.isShiftKeyDown()) return;

                        // 检查武器主人权限（已绑定的武器只有主人才能操作）
                        if (MaidWeaponItem.hasMaidData(targetStack)
                                && !MaidWeaponItem.isOwner(targetStack, player)) {
                            player.displayClientMessage(
                                    net.minecraft.network.chat.Component
                                            .translatable("maid_weapon.message.not_owner"), true);
                            ((Event) evt).setCanceled(true);
                            return;
                        }

                        // 捕获女仆
                        boolean success = TouhouLittleMaidHelper.convertMaidToWeapon(player, maid, targetStack);
                        if (success) {
                            // 取消事件 → TLM 不打开 GUI
                            ((Event) evt).setCanceled(true);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[MaidWeapon] Error handling InteractMaidEvent", e);
                    }
                };

                addListenerMethod.invoke(MinecraftForge.EVENT_BUS,
                        EventPriority.HIGHEST, true, eventClass, onInteract);

                LOGGER.info("[MaidWeapon] 已订阅 TLM InteractMaidEvent，准备就绪");

            } catch (ClassNotFoundException e) {
                LOGGER.info("[MaidWeapon] TLM 未安装，跳过 InteractMaidEvent 订阅");
            } catch (Exception e) {
                LOGGER.warn("[MaidWeapon] 无法订阅 InteractMaidEvent: {}", e.getMessage());
            }
        });
    }
}
