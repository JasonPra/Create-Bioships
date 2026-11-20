package org.azkerash.createbioships.compat.epicfight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.MultiBufferSource;

/** Optional native debug drawing, kept separate from server-side combat geometry. */
public final class EpicFightHitboxDebug {
  private static final Method DRAW;

  static {
    try {
      DRAW =
          Class.forName("yesman.epicfight.api.collider.OBBCollider")
              .getMethod("draw", PoseStack.class, MultiBufferSource.class, int.class);
    } catch (ReflectiveOperationException error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  private EpicFightHitboxDebug() {}

  public static void draw(Object geometry, PoseStack pose, MultiBufferSource buffers, int argb) {
    pose.pushPose();
    try {
      // Native colliders store world X/Z with inverted signs. Convert back only for drawing.
      pose.scale(-1, 1, -1);
      DRAW.invoke(geometry, pose, buffers, argb);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Unable to draw Epic Fight hitbox", error);
    } finally {
      pose.popPose();
    }
  }
}
