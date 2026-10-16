package com.maidweapon.forge.mixin;

import java.nio.file.Files;
import java.util.*;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

/**
 * Version AND bytecode gate: an absent or changed optional boss never becomes a launch dependency.
 */
public final class SoulBossMixinPlugin implements IMixinConfigPlugin {
  public void onLoad(String pkg) {}

  public String getRefMapperConfig() {
    return null;
  }

  public boolean shouldApplyMixin(String target, String mixin) {
    try {
      boolean goety = mixin.endsWith("SoulApostleTimerMixin");
      var mod = LoadingModList.get().getModFileById(goety ? "goety" : "legendary_monsters");
      if (mod == null) return false;
      try (var in = Files.newInputStream(mod.getFile().findResource("META-INF", "MANIFEST.MF"))) {
        String release =
            new java.util.jar.Manifest(in).getMainAttributes().getValue("Implementation-Version");
        if (goety) {
          if (mod.getMods().stream().noneMatch(m -> m.getVersion().toString().equals("2.5.58.4")))
            return false;
        } else if (!Set.of("2.1.15", "2.1.15 MC 1.20.1").contains(release)) return false;
      }
      var node = MixinService.getService().getBytecodeProvider().getClassNode(target);
      return node.fields.stream()
              .anyMatch(
                  f ->
                      f.name.equals(goety ? "moddedInvul" : "GolemInvulnerabilityTime")
                          && f.desc.equals("I"))
          && node.methods.stream()
              .anyMatch(
                  m ->
                      (m.name.equals("hurt") || m.name.equals("m_6469_"))
                          && m.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
                          && readsTimer(m, goety ? "moddedInvul" : "GolemInvulnerabilityTime")
                          && (!goety || readsTimer(m, "obsidianInvul")));
    } catch (Exception | LinkageError unavailable) {
      com.mojang.logging.LogUtils.getLogger()
          .warn("Soul timer adapter unavailable: {}", unavailable.toString());
      return false;
    }
  }

  private static boolean readsTimer(org.objectweb.asm.tree.MethodNode method, String name) {
    for (var instruction : method.instructions)
      if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
          && field.getOpcode() == 180
          && field.name.equals(name)
          && field.desc.equals("I")) return true;
    return false;
  }

  public void acceptTargets(Set<String> mine, Set<String> other) {}

  public List<String> getMixins() {
    return null;
  }

  public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}

  public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {
    com.mojang.logging.LogUtils.getLogger().info("SOUL_TIMER_ADAPTER_APPLIED {}", name);
  }
}
