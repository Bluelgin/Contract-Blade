package com.maidweapon.forge.compat.tacz.mixin;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class TaczMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String pkg) { }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        return LoadingModList.get().getModFileById("tacz") != null;
    }
    public void acceptTargets(Set<String> mine, Set<String> other) { }
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
}
