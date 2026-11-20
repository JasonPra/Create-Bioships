package org.azkerash.createbioships.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import org.azkerash.createbioships.combat.CustomHitboxes;
import org.azkerash.createbioships.compat.epicfight.EpicFightIntegration;
import org.azkerash.createbioships.compat.epicfight.client.EpicFightHitboxDebug;

/** Draws supplied active geometry without deciding which hitboxes combat should create. */
public final class HitboxDebugRenderer {
  private HitboxDebugRenderer() {}

  /** Draw every supplied shape, including mixed backends; no global backend hides another shape. */
  public static void drawAll(
      Iterable<? extends CustomHitboxes.Hitbox> hitboxes,
      PoseStack pose,
      MultiBufferSource buffers,
      int argb) {
    for (var hitbox : hitboxes) {
      draw(hitbox, pose, buffers, argb);
    }
  }

  public static void draw(
      CustomHitboxes.Hitbox hitbox, PoseStack pose, MultiBufferSource buffers, int argb) {
    switch (hitbox.getBackend()) {
      case VANILLA -> draw(hitbox.getBounds(), pose, buffers, argb);
      case EPIC_FIGHT -> {
        // The flag is safe without EF installed; the optional drawing class is loaded only here.
        if (EpicFightIntegration.isAvailable()) {
          EpicFightHitboxDebug.draw(hitbox.getGeometry(), pose, buffers, argb);
        }
      }
    }
  }

  /** Vanilla and Sable AABB diagnostics remain available even when Epic Fight is installed. */
  public static void draw(AABB bounds, PoseStack pose, MultiBufferSource buffers, int argb) {
    LevelRenderer.renderLineBox(
        pose,
        buffers.getBuffer(RenderType.lines()),
        bounds,
        ((argb >>> 16) & 255) / 255f,
        ((argb >>> 8) & 255) / 255f,
        (argb & 255) / 255f,
        ((argb >>> 24) & 255) / 255f);
  }
}
