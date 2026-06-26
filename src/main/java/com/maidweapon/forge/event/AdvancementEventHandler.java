package com.maidweapon.forge.event;

import com.maidweapon.forge.init.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听成就解锁 → 触发对应剧情记忆。
 * 成就名 = maid_weapon:<id> → 映射到 maid_weapon.memory.<key>
 */
@Mod.EventBusSubscriber
public class AdvancementEventHandler {

    private static final Map<String, String> ADV_MEMORY_MAP = new HashMap<>();
    static {
        ADV_MEMORY_MAP.put("capture_maid",  "maid_weapon.memory.capture");
        ADV_MEMORY_MAP.put("level_5",       "maid_weapon.memory.level_5");
        ADV_MEMORY_MAP.put("level_10",      "maid_weapon.memory.level_10");
        ADV_MEMORY_MAP.put("embed_sin",     "maid_weapon.memory.sin_acquire");
        ADV_MEMORY_MAP.put("max_favorability", "maid_weapon.memory.max_favorability");
        ADV_MEMORY_MAP.put("broken_blade",  "maid_weapon.memory.broken_blade");
        ADV_MEMORY_MAP.put("sacred_fruit",  "maid_weapon.memory.sacred_tree");
        ADV_MEMORY_MAP.put("potion_vial",   "maid_weapon.memory.potion_room");
        ADV_MEMORY_MAP.put("yakkyoku",      "maid_weapon.memory.potion_room");
        ADV_MEMORY_MAP.put("kensei",        "maid_weapon.memory.broken_blade");
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Advancement adv = event.getAdvancement();
        ResourceLocation id = adv.getId();
        if (!"maid_weapon".equals(id.getNamespace())) return;

        String path = id.getPath();
        String memKey = ADV_MEMORY_MAP.get(path);
        if (memKey == null) return;

        Player player = event.getEntity();
        String flagKey = player.getScoreboardName() + "_adv_" + path;
        if (player.getPersistentData().getBoolean(flagKey)) return;
        player.getPersistentData().putBoolean(flagKey, true);

        player.displayClientMessage(
                Component.translatable(memKey), false
        );
    }
}
