package org.azkerash.createbioships.client.legs;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/** Tiles the core's shell pixels at block texture density, including on extended limbs. */
final class CrabLegMesh {
  private static final ResourceLocation SKIN =
      ResourceLocation.parse("create_bioships:block/living_core");

  private static final ResourceLocation ARACHNID_SKIN =
      ResourceLocation.parse("create_bioships:block/arachnid_chitin");
  private static final int MAX_BARBS_PER_SEGMENT = 3;

  private CrabLegMesh() {}

  static void render(
      PoseStack stack, Minecraft mc, double width, double length, int light, RenderType hurt) {
    render(stack, mc, width, length, light, hurt, false, false);
  }

  static void render(
      PoseStack stack,
      Minecraft mc,
      double width,
      double length,
      int light,
      RenderType hurt,
      boolean arachnid,
      boolean barbs) {
    var sprite =
        mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(arachnid ? ARACHNID_SKIN : SKIN);
    var buffers = mc.renderBuffers().bufferSource();
    var base = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    draw(stack, buffers.getBuffer(base), sprite, width, length, light);
    if (barbs) {
      barbs(stack, buffers.getBuffer(base), sprite, width, length, light);
    }
    if (hurt != null) {
      buffers.endBatch(base);
      draw(stack, buffers.getBuffer(hurt), sprite, width, length, light);
      if (barbs) {
        barbs(stack, buffers.getBuffer(hurt), sprite, width, length, light);
      }
      buffers.endBatch(hurt);
    }
  }

  /** Pointed spines are mesh detail, not extra Sable bodies or per-frame physics queries. */
  private static void barbs(
      PoseStack stack,
      VertexConsumer out,
      TextureAtlasSprite sprite,
      double width,
      double length,
      int light) {
    int count = Math.min(MAX_BARBS_PER_SEGMENT, Math.max(1, (int) Math.ceil(length)));
    for (int i = 0; i < count; i++) {
      stack.pushPose();
      double side = i % 2 == 0 ? 1 : -1;
      stack.translate(side * width * .45, length * (i + 1) / (count + 1), 0);
      stack.mulPose(new org.joml.Quaternionf().rotationZ((float) (-side * Math.PI / 3)));
      double half = width * .12, reach = width * .8;
      var corners =
          java.util.List.of(
              new Vec3(-half, 0, -half),
              new Vec3(half, 0, -half),
              new Vec3(half, 0, half),
              new Vec3(-half, 0, half));
      var tip = new Vec3(0, reach, 0);
      for (int face = 0; face < 4; face++) {
        var a = corners.get(face);
        var b = corners.get((face + 1) % 4);
        var normal = b.subtract(a).cross(tip.subtract(a)).normalize();
        vertex(stack, out, sprite, a, normal, 0, 0, light);
        vertex(stack, out, sprite, b, normal, 1, 0, light);
        vertex(stack, out, sprite, tip, normal, .5f, 1, light);
        vertex(stack, out, sprite, tip, normal, .5f, 1, light);
      }
      stack.popPose();
    }
  }

  private static void draw(
      PoseStack stack,
      VertexConsumer out,
      TextureAtlasSprite sprite,
      double w,
      double length,
      int light) {
    double h = w / 2;
    face(
        stack,
        out,
        sprite,
        new Vec3(-h, 0, -h),
        new Vec3(1, 0, 0),
        new Vec3(0, 1, 0),
        new Vec3(0, 0, -1),
        w,
        length,
        light);
    face(
        stack,
        out,
        sprite,
        new Vec3(-h, 0, h),
        new Vec3(1, 0, 0),
        new Vec3(0, 1, 0),
        new Vec3(0, 0, 1),
        w,
        length,
        light);
    face(
        stack,
        out,
        sprite,
        new Vec3(-h, 0, -h),
        new Vec3(0, 0, 1),
        new Vec3(0, 1, 0),
        new Vec3(-1, 0, 0),
        w,
        length,
        light);
    face(
        stack,
        out,
        sprite,
        new Vec3(h, 0, -h),
        new Vec3(0, 0, 1),
        new Vec3(0, 1, 0),
        new Vec3(1, 0, 0),
        w,
        length,
        light);
    face(
        stack,
        out,
        sprite,
        new Vec3(-h, 0, -h),
        new Vec3(1, 0, 0),
        new Vec3(0, 0, 1),
        new Vec3(0, -1, 0),
        w,
        w,
        light);
    face(
        stack,
        out,
        sprite,
        new Vec3(-h, length, -h),
        new Vec3(1, 0, 0),
        new Vec3(0, 0, 1),
        new Vec3(0, 1, 0),
        w,
        w,
        light);
  }

  private static void face(
      PoseStack stack,
      VertexConsumer out,
      TextureAtlasSprite sprite,
      Vec3 origin,
      Vec3 uAxis,
      Vec3 vAxis,
      Vec3 normal,
      double width,
      double height,
      int light) {
    boolean forward = uAxis.cross(vAxis).dot(normal) > 0;
    int row = 0;
    for (double v = 0; v < height - .000001; v += .25, row++) {
      double dh = Math.min(.25, height - v);
      for (double u = 0; u < width - .000001; u += .25) {
        double dw = Math.min(.25, width - u);
        var a = origin.add(uAxis.scale(u)).add(vAxis.scale(v));
        var b = a.add(uAxis.scale(dw));
        var c = b.add(vAxis.scale(dh));
        var d = a.add(vAxis.scale(dh));
        float u0 = (float) (u % 1), u1 = u0 + (float) dw;
        float v0 = row % 2 == 0 ? .75f : 1f;
        float v1 = v0 + (float) dh * (row % 2 == 0 ? 1 : -1);
        vertex(stack, out, sprite, a, normal, u0, v0, light);
        if (forward) {
          vertex(stack, out, sprite, b, normal, u1, v0, light);
          vertex(stack, out, sprite, c, normal, u1, v1, light);
          vertex(stack, out, sprite, d, normal, u0, v1, light);
        } else {
          vertex(stack, out, sprite, d, normal, u0, v1, light);
          vertex(stack, out, sprite, c, normal, u1, v1, light);
          vertex(stack, out, sprite, b, normal, u1, v0, light);
        }
      }
    }
  }

  private static void vertex(
      PoseStack stack,
      VertexConsumer out,
      TextureAtlasSprite sprite,
      Vec3 p,
      Vec3 normal,
      float u,
      float v,
      int light) {
    out.addVertex(stack.last().pose(), (float) p.x, (float) p.y, (float) p.z)
        .setColor(1f, 1f, 1f, 1f)
        .setUv(sprite.getU(u), sprite.getV(v))
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(light)
        .setNormal(stack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
  }
}
