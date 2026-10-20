package com.maidweapon.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Resource existence is cached by ResourceManager identity, including resource-pack reloads. */
public final class DanmakuTextures {
    private static Object manager;
    private static final Map<ResourceLocation, Boolean> PRESENT = new java.util.concurrent.ConcurrentHashMap<>();
    public static ResourceLocation path(String name) {
        return new ResourceLocation("maid_weapon", "textures/gui/danmaku/" + name + ".png");
    }
    public static boolean present(ResourceLocation texture) {
        var current = Minecraft.getInstance().getResourceManager();
        if (manager != current) { manager = current; PRESENT.clear(); }
        return PRESENT.computeIfAbsent(texture, t -> current.getResource(t).isPresent());
    }
    public static void clear() { PRESENT.clear(); }
    private DanmakuTextures() { }
}
