package com.maidweapon.forge.mixin;

import java.util.*;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

/**
 * Gate only the new verified native melee invocation bridge; existing SlashBlade mixins retain
 * their policy.
 */
public final class SoulSlashMixinPlugin implements IMixinConfigPlugin {
  public void onLoad(String pkg) {}

  public String getRefMapperConfig() {
    return null;
  }

  public boolean shouldApplyMixin(String target, String mixin) {
    if (!mixin.endsWith("SoulNativeMeleeMixin")&&!mixin.endsWith("SoulAttackHelperMixin")) return true;
    try {
      var mod = LoadingModList.get().getModFileById("slashblade");
      return mod != null
          && mod.getMods().stream().anyMatch(m -> m.getVersion().toString().equals("1.9.65"))
          && MixinService.getService().getBytecodeProvider().getClassNode(target).methods.stream()
              .anyMatch(
                  m ->
                      m.name.equals(mixin.endsWith("SoulAttackHelperMixin")?"attack":"doMeleeAttack")
                          && m.desc.equals(
                              mixin.endsWith("SoulAttackHelperMixin")?"(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;F)V":"(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V"));
    } catch (Exception | LinkageError e) {
      return false;
    }
  }

  public void acceptTargets(Set<String> mine, Set<String> other) {}

  public List<String> getMixins() {
    return null;
  }

  public void preApply(String n, ClassNode c, String m, IMixinInfo i) {}

  public void postApply(String n, ClassNode c, String m, IMixinInfo i) {}
}
