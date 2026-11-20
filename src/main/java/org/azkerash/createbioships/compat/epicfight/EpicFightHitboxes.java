package org.azkerash.createbioships.compat.epicfight;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.azkerash.createbioships.combat.CustomHitboxes;
import org.azkerash.createbioships.compat.epicfight.mixin.EpicFightColliderAccess;

/** Native Epic Fight geometry, reached only after checking EpicFightIntegration.isAvailable(). */
public final class EpicFightHitboxes {
  // Resolve exact signatures once. Class.getMethod enumerates EF's client-only draw methods and
  // loads PoseStack on dedicated servers, even when looking up an unrelated combat method.
  private static final Class<?> OBB_TYPE;
  private static final MethodHandle BOX, INTERSECTS;

  static {
    try {
      OBB_TYPE = Class.forName("yesman.epicfight.api.collider.OBBCollider");
      var lookup = MethodHandles.publicLookup();
      BOX = lookup.findConstructor(OBB_TYPE, MethodType.methodType(void.class, AABB.class));
      INTERSECTS =
          lookup.findVirtual(OBB_TYPE, "isCollide", MethodType.methodType(boolean.class, OBB_TYPE));
    } catch (ReflectiveOperationException error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  private static final class Hitbox implements CustomHitboxes.Hitbox {
    private final AABB bounds;
    private Object geometry;

    private Hitbox(AABB bounds) {
      this.bounds = bounds;
    }

    public CustomHitboxes.Backend getBackend() {
      return CustomHitboxes.Backend.EPIC_FIGHT;
    }

    public AABB getBounds() {
      return bounds;
    }

    public Object getGeometry() {
      // Most tissue volumes fail broad phase. Allocate the native OBB only if it is inspected or
      // reaches narrow phase, then reuse that same shape for the lifetime of this snapshot.
      if (geometry == null) {
        try {
          geometry = BOX.invoke(bounds);
        } catch (RuntimeException | Error error) {
          throw error;
        } catch (Throwable error) {
          throw new IllegalStateException("Unable to create Epic Fight hitbox", error);
        }
      }
      return geometry;
    }
  }

  private EpicFightHitboxes() {}

  public static CustomHitboxes.Hitbox create(AABB worldBounds) {
    // EF's constructor performs its X/Z conversion, matching isCollide(Entity).
    return new Hitbox(worldBounds);
  }

  public static boolean intersects(CustomHitboxes.Hitbox target, Object weapon, Entity probe) {
    if (OBB_TYPE.isInstance(weapon)) {
      try {
        return (boolean) INTERSECTS.invoke(weapon, target.getGeometry());
      } catch (RuntimeException | Error error) {
        throw error;
      } catch (Throwable error) {
        throw new IllegalStateException("Unable to test Epic Fight hitbox", error);
      }
    }
    // Line/plane colliders expose only the entity overload. Reuse the unspawned probe rather than
    // registering a second hurtbox or leaving its bounds changed for another combat query.
    var previous = probe.getBoundingBox();
    try {
      probe.setBoundingBox(target.getBounds());
      return ((EpicFightColliderAccess) weapon).core$collides(probe);
    } finally {
      probe.setBoundingBox(previous);
    }
  }
}
