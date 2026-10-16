package com.maidweapon.forge.api.ability;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractAbilityReloadListener extends SimpleJsonResourceReloadListener {
    public ContractAbilityReloadListener() { super(new Gson(), "contract_ability_progression"); }
    @SubscribeEvent public static void register(AddReloadListenerEvent event) { event.addListener(new ContractAbilityReloadListener()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ContractAbilityRegistry.replaceDatapackDefinitions(Map.of()); }
    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var accepted = new HashMap<ResourceLocation, ContractAbilityDefinition>();
        files.forEach((file, json) -> {
            try {
                var definition = ContractAbilityJson.parse(json);
                if (!file.equals(definition.ability())) throw new IllegalArgumentException("File ID must equal ability ID");
                ContractAbilityRegistry.validate(file, ContractAbilityRegistry.registration(file), definition);
                accepted.put(file, definition);
            } catch (RuntimeException invalid) {
                com.mojang.logging.LogUtils.getLogger().warn("[ContractAbility] Rejected {}: {}; using registered Java fallback", file, invalid.toString());
            }
        });
        ContractAbilityRegistry.replaceDatapackDefinitions(accepted);
    }
}
