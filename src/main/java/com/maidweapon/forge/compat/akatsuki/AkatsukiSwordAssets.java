package com.maidweapon.forge.compat.akatsuki;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small immutable curves and mesh, parsed once per resource reload, not per frame. */
final class AkatsukiSwordAssets {
    static final ResourceLocation MATERIAL = ResourceLocation.parse("maid_weapon:akatsuki/white.png");
    record Key(float time, float x, float y, float z) { }
    record Box(float x0,float y0,float z0,float x1,float y1,float z1,int rgb,int faceMask) { }
    record Clip(float length, Map<String, Key[]> bones) { }
    private static Map<String, Clip> clips = Map.of();
    private static Map<String, List<Box>> parts = Map.of();
    private static boolean attempted;
    static void invalidate() { attempted = false; clips = Map.of(); parts = Map.of(); }
    static boolean ensure() {
        if (attempted) return !clips.isEmpty() && !parts.isEmpty();
        attempted = true;
        try {
            var loadedClips = new HashMap<String, Clip>();
            for (var entry : read("swordplay.json").getAsJsonObject("animations").entrySet()) {
                var json = entry.getValue().getAsJsonObject();
                var bones = new LinkedHashMap<String, Key[]>();
                for (var b : json.getAsJsonObject("bones").entrySet()) {
                    var keys = new ArrayList<Key>();
                    for (var key : b.getValue().getAsJsonObject().getAsJsonObject("rotation").entrySet()) {
                        var v = key.getValue().getAsJsonArray();
                        keys.add(new Key(Float.parseFloat(key.getKey()), v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()));
                    }
                    keys.sort(Comparator.comparing(Key::time));
                    bones.put(b.getKey(), keys.toArray(Key[]::new));
                }
                loadedClips.put(entry.getKey(), new Clip(json.get("animation_length").getAsFloat(), Map.copyOf(bones)));
            }
            var loadedParts = new HashMap<String, List<Box>>();
            for (var entry : read("katana.json").getAsJsonObject("parts").entrySet()) {
                var boxes = new ArrayList<Box>();
                for (var cube : entry.getValue().getAsJsonArray()) {
                    var c = cube.getAsJsonObject(); var a = c.getAsJsonArray("from"); var b = c.getAsJsonArray("to");
                    boxes.add(new Box(a.get(0).getAsFloat()/16,a.get(1).getAsFloat()/16,a.get(2).getAsFloat()/16,
                            b.get(0).getAsFloat()/16,b.get(1).getAsFloat()/16,b.get(2).getAsFloat()/16,
                            Integer.parseInt(c.get("color").getAsString(),16),c.has("faceMask")?c.get("faceMask").getAsInt():63));
                }
                loadedParts.put(entry.getKey(), List.copyOf(boxes));
            }
            for (String name : List.of("draw","ready","cut_1","cut_2","cut_3","sheathe"))
                if (!loadedClips.containsKey(name)) throw new IOException("Missing Akatsuki clip: " + name);
            if (!loadedParts.keySet().containsAll(List.of("blade","sheath"))) throw new IOException("Incomplete katana mesh");
            clips = Map.copyOf(loadedClips); parts = Map.copyOf(loadedParts);
            return true;
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().warn("[MaidWeapon] Akatsuki cosmetic assets unavailable; native weapon rendering retained", failure);
            return false;
        }
    }
    private static JsonObject read(String file) throws IOException {
        try (var in = Minecraft.getInstance().getResourceManager().open(ResourceLocation.parse("maid_weapon:akatsuki/" + file));
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    static Clip clip(String name) { return clips.get(name); }
    static List<Box> part(String name) { return parts.getOrDefault(name, List.of()); }
    static Key sample(Key[] keys, float time) {
        if (time <= keys[0].time()) return keys[0];
        for (int i=1;i<keys.length;i++) if (time <= keys[i].time()) {
            var a=keys[i-1]; var b=keys[i]; float f=(time-a.time())/Math.max(.00001f,b.time()-a.time());
            return new Key(time,a.x()+(b.x()-a.x())*f,a.y()+(b.y()-a.y())*f,a.z()+(b.z()-a.z())*f);
        }
        return keys[keys.length-1];
    }
    private AkatsukiSwordAssets() { }
}
