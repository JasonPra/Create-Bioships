package org.Azkerash.create_bioships.client.core;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;

/** Compact meters aligned with the existing goggles tooltip's scaled text rows. */
final class CoreStatusBars {
  private CoreStatusBars() {}

  static void draw(
      String kind,
      double value,
      double maximum,
      float x,
      float y,
      Matrix4f matrix,
      MultiBufferSource buffers) {
    int color =
        switch (kind) {
          case "health" -> 0xffc95e72;
          case "armor" -> 0xffd7c29c;
          default -> 0xffb28cdb;
        };
    double fraction =
        maximum > 0 && Double.isFinite(value) && Double.isFinite(maximum)
            ? Math.clamp(value / maximum, 0, 1)
            : 0;
    var vertices = buffers.getBuffer(RenderType.gui());
    rect(vertices, matrix, x, y + 1, x + 80, y + 8, 0xff744655);
    rect(vertices, matrix, x + 1, y + 2, x + 79, y + 7, 0xff20131f);
    float end = x + 1 + (float) (78 * fraction);
    if (fraction > 0) {
      rect(vertices, matrix, x + 1, y + 2, end, y + 7, color);
      rect(vertices, matrix, x + 1, y + 2, end, y + 3, 0xffead5cf);
    }
    for (int i = 1; i < 4; i++) {
      rect(vertices, matrix, x + i * 20, y + 6, x + i * 20 + 1, y + 8, 0xff422431);
    }
  }

  private static void rect(
      VertexConsumer v, Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
    v.addVertex(matrix, x0, y0, 0).setColor(color);
    v.addVertex(matrix, x0, y1, 0).setColor(color);
    v.addVertex(matrix, x1, y1, 0).setColor(color);
    v.addVertex(matrix, x1, y0, 0).setColor(color);
  }
}
