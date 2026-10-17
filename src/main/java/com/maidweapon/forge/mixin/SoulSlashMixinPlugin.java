package com.maidweapon.forge.mixin;

import java.util.*;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

/**
 * Version/shape gates for verified native SlashBlade bridges. Unrelated existing mixins retain
 * their policy; guard eligibility itself always belongs to the provider.
 */
public final class SoulSlashMixinPlugin implements IMixinConfigPlugin {
  public void onLoad(String pkg) {}

  public String getRefMapperConfig() {
    return null;
  }

  public boolean shouldApplyMixin(String target, String mixin) {
    if (mixin.endsWith("BlackFoxNativeGuardMixin")) {
      try {
        var mod = LoadingModList.get().getModFileById("slashblade");
        return mod != null && mod.getMods().stream().anyMatch(m -> m.getVersion().toString().equals("1.9.65"))
            && MixinService.getService().getBytecodeProvider().getClassNode(target).methods.stream()
                .filter(m -> m.name.equals("onLivingAttack") && m.desc.equals("(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V"))
                .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                .anyMatch(i -> i instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && call.owner.equals("mods/flammpfeil/slashblade/ability/Untouchable")
                    && call.name.equals("setUntouchable") && call.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;I)V"));
      } catch (Exception | LinkageError error) { return false; }
    }
    boolean attackHelper = mixin.endsWith("SoulAttackHelperMixin") || mixin.endsWith("BlackFoxNativeDamageMixin");
    if (!mixin.endsWith("SoulNativeMeleeMixin") && !attackHelper) return true;
    try {
      var mod = LoadingModList.get().getModFileById("slashblade");
      return mod != null
          && mod.getMods().stream().anyMatch(m -> m.getVersion().toString().equals("1.9.65"))
          && MixinService.getService().getBytecodeProvider().getClassNode(target).methods.stream()
              .anyMatch(
                  m ->
                      m.name.equals(attackHelper?"attack":"doMeleeAttack")
                          && m.desc.equals(
                              attackHelper?"(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;F)V":"(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V"));
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
