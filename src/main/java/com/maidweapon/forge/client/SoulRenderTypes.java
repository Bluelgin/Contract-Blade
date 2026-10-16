package com.maidweapon.forge.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;

/** Alpha-blended dark glass; additive lightning would make the black shell disappear. */
final class SoulRenderTypes extends RenderType {
  static final RenderType SHARDS =
      create(
          "maid_weapon_soul_shards",
          DefaultVertexFormat.POSITION_COLOR,
          VertexFormat.Mode.QUADS,
          512,
          false,
          true,
          CompositeState.builder()
              .setShaderState(POSITION_COLOR_SHADER)
              .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
              .setCullState(NO_CULL)
              .setWriteMaskState(COLOR_WRITE)
              .createCompositeState(false));

  /**
   * High-contrast owner-only locator. The shell keeps ordinary occlusion; only the
   * small aiming reticle and target tether ignore depth so tall bosses cannot hide them.
   * COLOR_WRITE never modifies the depth buffer for the other entities.
   */
  static final RenderType OWNER_MARKER =
      create(
          "maid_weapon_soul_owner_marker",
          DefaultVertexFormat.POSITION_COLOR,
          VertexFormat.Mode.QUADS,
          256,
          false,
          true,
          CompositeState.builder()
              .setShaderState(POSITION_COLOR_SHADER)
              .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
              .setDepthTestState(NO_DEPTH_TEST)
              .setCullState(NO_CULL)
              .setWriteMaskState(COLOR_WRITE)
              .createCompositeState(false));

  private SoulRenderTypes(
      String n,
      VertexFormat f,
      VertexFormat.Mode m,
      int size,
      boolean a,
      boolean b,
      Runnable setup,
      Runnable clear) {
    super(n, f, m, size, a, b, setup, clear);
  }
}
