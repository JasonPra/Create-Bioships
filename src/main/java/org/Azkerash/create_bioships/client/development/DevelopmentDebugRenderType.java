package org.Azkerash.create_bioships.client.development;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;

/** Debug lines remain visible inside opaque tissue without writing into the world's depth. */
final class DevelopmentDebugRenderType extends RenderType {
  private DevelopmentDebugRenderType() {
    super(
        "unused",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        256,
        false,
        false,
        () -> {},
        () -> {});
  }

  private static final DepthTestStateShard SEE_THROUGH =
      new DepthTestStateShard("development_see_through", 519) {
        @Override
        public void setupRenderState() {
          com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        }

        @Override
        public void clearRenderState() {
          com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        }
      };
  static final RenderType LINES =
      create(
          "development_debug",
          DefaultVertexFormat.POSITION_COLOR_NORMAL,
          VertexFormat.Mode.LINES,
          1536,
          CompositeState.builder()
              .setShaderState(RENDERTYPE_LINES_SHADER)
              .setLineState(new LineStateShard(java.util.OptionalDouble.of(2)))
              .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
              .setDepthTestState(SEE_THROUGH)
              .setWriteMaskState(COLOR_WRITE)
              .setCullState(NO_CULL)
              .createCompositeState(false));
}
