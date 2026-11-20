package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.Azkerash.create_bioships.compat.epicfight.EpicFightHitboxes;
import org.Azkerash.create_bioships.compat.epicfight.EpicFightIntegration;

/**
 * Creates one combat representation for a custom tissue volume. Sable continues to own physical
 * block collision; these shapes bridge attacks into the creature's shared health system.
 */
public final class CustomHitboxes {
  public enum Backend {
    VANILLA,
    EPIC_FIGHT
  }

  public interface Hitbox {
    Backend getBackend();

    AABB getBounds();

    /** Broad-phase/world-volume queries use world-space bounds regardless of combat backend. */
    default boolean intersects(AABB volume) {
      return getBounds().intersects(volume);
    }

    /** The native shape is exposed for optional diagnostics; creating it never spawns an entity. */
    Object getGeometry();
  }

  private record VanillaHitbox(AABB bounds) implements Hitbox {
    public Backend getBackend() {
      return Backend.VANILLA;
    }

    public AABB getBounds() {
      return bounds;
    }

    public Object getGeometry() {
      return bounds;
    }
  }

  private CustomHitboxes() {}

  /** Select the installed integration once per volume, rather than registering both shapes. */
  public static Hitbox create(AABB worldBounds) {
    return EpicFightIntegration.isAvailable()
        ? EpicFightHitboxes.create(worldBounds)
        : new VanillaHitbox(worldBounds);
  }

  /**
   * Plot coordinates are transformed using the current logical pose, never a cached render pose.
   */
  public static Hitbox create(ServerSubLevel body, AABB plotBounds) {
    var bounds = new BoundingBox3d(plotBounds);
    return create(bounds.transform(body.logicalPose(), bounds).toMojang());
  }

  /**
   * Optional weapon queries share the same factory output and restore any temporary probe state.
   */
  public static boolean intersectsEpicFight(Hitbox target, Object weapon, Entity fallbackProbe) {
    if (!EpicFightIntegration.isAvailable() || target.getBackend() != Backend.EPIC_FIGHT) {
      return false;
    }
    return EpicFightHitboxes.intersects(target, weapon, fallbackProbe);
  }
}
