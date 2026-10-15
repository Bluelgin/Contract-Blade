package com.maidweapon.forge.client;

import com.maidweapon.forge.compat.CompatDiagnostics;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Client-only optional APIs. No copied models, post chains, provider fields or damage entities. */
public final class AkatsukiNativeVisualBridge {
  private static boolean tintChecked, swordChecked;
  private static Method begin, manager, model, luminous, color, resetColor;
  private static final ResourceLocation SWORD =
      ResourceLocation.parse("slashblade:model/util/ss.obj");
  private static final ResourceLocation TEXTURE =
      ResourceLocation.parse("slashblade:model/util/ss.png");

  /** Maximum eight ticks of lease; never call endAkatsuki on the shared native execution timer. */
  public static void renewTint() {
    if (!tintChecked) {
      tintChecked = true;
      if (ModList.get().isLoaded("blade_tetra"))
        try {
          begin =
              Class.forName("dev.bladetetra.client.MikageBloodMoonDomainClient")
                  .getMethod("beginAkatsuki", int.class);
        } catch (ReflectiveOperationException | LinkageError failure) {
          CompatDiagnostics.warnOnce("akatsuki-native-tint", failure);
        }
    }
    if (begin != null)
      try {
        begin.invoke(null, 8);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
        begin = null;
        CompatDiagnostics.warnOnce("akatsuki-native-tint-call", failure);
      }
  }

  /** Same ss mesh, texture, scale and luminous pass as SummonedSwordRenderer in 1.9.65. */
  public static void sword(PoseStack pose, MultiBufferSource buffers, float alpha) {
    if (!swordChecked) {
      swordChecked = true;
      if (ModList.get().isLoaded("slashblade"))
        try {
          var managerType =
              Class.forName("mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager");
          var meshType =
              Class.forName("mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject");
          var state =
              Class.forName("mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState");
          manager = managerType.getMethod("getInstance");
          model = managerType.getMethod("getModel", ResourceLocation.class);
          color = state.getMethod("setCol", int.class, boolean.class);
          resetColor = state.getMethod("resetCol");
          luminous =
              state.getMethod(
                  "renderOverridedLuminous",
                  ItemStack.class,
                  meshType,
                  String.class,
                  ResourceLocation.class,
                  PoseStack.class,
                  MultiBufferSource.class,
                  int.class);
        } catch (ReflectiveOperationException | LinkageError failure) {
          luminous = null;
          CompatDiagnostics.warnOnce("akatsuki-native-sword", failure);
        }
    }
    if (luminous == null) return; // No geometric fake-sword fallback.
    try {
      Object mesh = model.invoke(manager.invoke(null), SWORD);
      color.invoke(null, (Math.max(0, Math.min(255, (int) (alpha * 190))) << 24) | 0xC91129, true);
      luminous.invoke(null, ItemStack.EMPTY, mesh, "ss", TEXTURE, pose, buffers, 15728864);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      luminous = null;
      CompatDiagnostics.warnOnce("akatsuki-native-sword-render", failure);
    } finally {
      if (resetColor != null)
        try {
          resetColor.invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
  }

  public static void reload() {
    tintChecked = swordChecked = false;
    begin = luminous = null;
  }

  private AkatsukiNativeVisualBridge() {}
}
