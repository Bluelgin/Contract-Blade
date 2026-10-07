package com.maidweapon.forge.compat.fox;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonAnimationUtils;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Uses only the bundled generic geometry/animation parser, not the maid pack/model manager. */
public final class BlackFoxBossResources {
    public static final ResourceLocation MODEL = ResourceLocation.parse("maid_weapon:geo/black_fox_boss.json");
    public static final ResourceLocation TEXTURE = ResourceLocation.parse("maid_weapon:textures/entity/black_fox_boss.png");
    public static final ResourceLocation ANIMATION = ResourceLocation.parse("maid_weapon:animations/black_fox_boss.animation.json");
    public static final ResourceLocation COMBO_ANIMATION = ResourceLocation.parse("maid_weapon:animations/black_fox_combo_b.animation.json");
    public static final ResourceLocation IAIDO_ANIMATION = ResourceLocation.parse("maid_weapon:animations/black_fox_iaido.animation.json");
    private static volatile boolean invalid = true;
    private static int generation;

    static void invalidate() { invalid = true; }
    static int generation() { return generation; }
    public static void ensureLoaded() {
        var cache = GeckoLibCache.getInstance();
        if (!invalid && cache.getGeoModels().containsKey(MODEL) && cache.getAnimations().containsKey(ANIMATION)) return;
        var resources = Minecraft.getInstance().getResourceManager();
        try (var geo = resources.open(MODEL); var base = resources.open(ANIMATION);
             var combo = resources.open(COMBO_ANIMATION); var iaido = resources.open(IAIDO_ANIMATION)) {
            var geometry = GeoBuilder.getGeoBuilder().constructGeoModel(RawGeometryTree.parseHierarchy(Converter.fromInputStream(geo)));
            var file = new AnimationFile();
            merge(base, file);
            merge(combo, file);
            merge(iaido, file);
            cache.getGeoModels().put(MODEL, geometry);
            cache.getAnimations().put(ANIMATION, file);
            generation++;
            invalid = false;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load Black Fox battle assets", failure);
        }
    }
    private static void merge(InputStream input, AnimationFile file) throws IOException {
        JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var entry : JsonAnimationUtils.getAnimations(json))
            file.putAnimation(entry.getKey(), JsonAnimationUtils.deserializeJsonToAnimation(
                    entry, GeckoLibCache.getInstance().parser));
    }
    private BlackFoxBossResources() { }
}
