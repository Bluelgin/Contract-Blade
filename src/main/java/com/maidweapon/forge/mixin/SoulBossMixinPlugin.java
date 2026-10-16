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
      if (mixin.endsWith("SoulWroughtnautMixin")) {
        var file = LoadingModList.get().getModFileById("mowziesmobs");
        if (file == null || file.getMods().stream().noneMatch(m ->
            m.getModId().equals("mowziesmobs") && m.getVersion().toString().equals("1.8.2")))
          return false;
        var node = MixinService.getService().getBytecodeProvider().getClassNode(target);
        for (var method : node.methods) {
          if (!(method.name.equals("hurt") || method.name.equals("m_6469_"))
              || !method.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")) continue;
          int attackerReads = 0, parentCalls = 0;
          boolean weakness = false, bypassTag = false;
          for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                && field.getOpcode() == 180 && field.name.equals("vulnerable") && field.desc.equals("Z"))
              weakness = true;
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
              if (call.owner.equals("net/minecraft/world/damagesource/DamageSource")) {
                if ((call.name.equals("getEntity") || call.name.equals("m_7639_"))
                    && call.desc.equals("()Lnet/minecraft/world/entity/Entity;")) attackerReads++;
                if ((call.name.equals("is") || call.name.equals("m_269533_"))
                    && call.desc.equals("(Lnet/minecraft/tags/TagKey;)Z")) bypassTag = true;
              }
              if (call.getOpcode() == 183
                  && call.owner.equals("com/bobmowzie/mowziesmobs/server/entity/MowzieLLibraryEntity")
                  && (call.name.equals("hurt") || call.name.equals("m_6469_"))
                  && call.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")) parentCalls++;
            }
          }
          return attackerReads == 1 && parentCalls == 2 && weakness && bypassTag;
        }
        return false;
      }
      if (mixin.substring(mixin.lastIndexOf('.') + 1).startsWith("Observation")) {
        String modId =
            target.startsWith("com.Polarice3")
                ? "goety"
                : target.startsWith("com.github.L_Ender") ? "cataclysm" : "legendary_monsters";
        var file = LoadingModList.get().getModFileById(modId);
        if (file == null) return false;
        String version =
            file.getMods().stream()
                .filter(m -> m.getModId().equals(modId))
                .findFirst()
                .orElseThrow()
                .getVersion()
                .toString();
        if (modId.equals("legendary_monsters"))
          try (var in =
              Files.newInputStream(file.getFile().findResource("META-INF", "MANIFEST.MF"))) {
            version =
                new java.util.jar.Manifest(in)
                    .getMainAttributes()
                    .getValue("Implementation-Version");
          }
        if (!java.util.Objects.equals(
                version,
                modId.equals("goety") ? "2.5.58.4" : modId.equals("cataclysm") ? "3.31" : "2.1.15")
            && !java.util.Objects.equals(version, "2.1.15 MC 1.20.1")) return false;
        var node = MixinService.getService().getBytecodeProvider().getClassNode(target);
        boolean hurt =
            node.methods.stream()
                .anyMatch(
                    m ->
                        (m.name.equals("hurt") || m.name.equals("m_6469_"))
                            && m.desc.equals(
                                "(Lnet/minecraft/world/damagesource/DamageSource;F)Z"));
        if (!hurt) return false;
        if (mixin.endsWith("BaseMixin") || mixin.endsWith("ObservationApostleMixin"))
          return node.methods.stream()
              .anyMatch(
                  m -> {
                    if (!(m.name.equals(modId.equals("goety") ? "actuallyHurt" : "hurt")
                        || m.name.equals(modId.equals("goety") ? "m_6475_" : "m_6469_")))
                      return false;
                    for (var i : m.instructions)
                      if (i instanceof org.objectweb.asm.tree.MethodInsnNode call
                          && call.owner.equals("java/lang/Math")
                          && call.name.equals("min")
                          && call.desc.equals(
                              modId.equals("legendary_monsters") ? "(DD)D" : "(FF)F")) return true;
                    return false;
                  });
        return true;
      }
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
