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

  private CrabLegMesh() {}

  static void render(
      PoseStack stack, Minecraft mc, double width, double length, int light, RenderType hurt) {
    var sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SKIN);
    var buffers = mc.renderBuffers().bufferSource();
    var base = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    draw(stack, buffers.getBuffer(base), sprite, width, length, light);
    if (hurt != null) {
      buffers.endBatch(base);
      draw(stack, buffers.getBuffer(hurt), sprite, width, length, light);
      buffers.endBatch(hurt);
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
