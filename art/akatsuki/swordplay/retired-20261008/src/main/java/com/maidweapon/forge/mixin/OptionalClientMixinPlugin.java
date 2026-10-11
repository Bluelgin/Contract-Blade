package com.maidweapon.forge.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.*;

/** Skip the new Gecko 1.5.3 hooks on legacy maid renderers or installations without TLM. */
public final class OptionalClientMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName) {
        if(!mixinClassName.endsWith("AkatsukiGeckoPoseMixin") && !mixinClassName.endsWith("AkatsukiGeckoHeldMixin"))return true;
        var loading=FMLLoader.getLoadingModList();
        if(loading==null)return false;
        return loading.getMods().stream().filter(mod->mod.getModId().equals("touhou_little_maid"))
                .anyMatch(mod->mod.getVersion().compareTo(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.5.3"))>=0);
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets,Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo mixinInfo) { }
}
