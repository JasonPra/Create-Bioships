package org.Azkerash.create_bioships.compat.biomancy;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.joml.Vector3d;

/** Per-membrane contact filtering; authorization never disables a hull's solid frame collisions. */
public final class ConstructMembranePassage implements BlockSubLevelCollisionCallback {
  public static final ConstructMembranePassage INSTANCE = new ConstructMembranePassage();

  private ConstructMembranePassage() {}

  public static boolean registered(ServerSubLevel host, ServerSubLevel craft) {
    host = SubLevelCreatureParts.root(host);
    craft = SubLevelCreatureParts.root(craft);
    if (host == craft) return false;
    var id = craft.getUniqueId();
    return SubLevelPermissions.get(host).allowed().stream()
        .anyMatch(entry -> entry.sublevel() && entry.id().equals(id));
  }

  public static boolean permitsCollision(ServerLevel l, BlockPos membrane, BlockPos other) {
    if (other == null) return false;
    var host = CoreAssemblyRules.containing(l, membrane);
    var craft = CoreAssemblyRules.containing(l, other);
    return host != null && craft != null && SubLevelData.isLiving(host) && registered(host, craft);
  }

  @Override
  public CollisionResult sable$onCollision(
      BlockPos pos, BlockPos other, Vector3d at, double speed) {
    var system =
        dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.getCurrentlySteppingSystem();
    return system != null && permitsCollision(system.getLevel(), pos, other)
        ? new CollisionResult(new Vector3d(), true)
        : CollisionResult.NONE;
  }

  /**
   * Feet/seat position must be inside the oriented plot bounds; a nearby rotated AABB is
   * insufficient.
   */
  public static boolean within(SubLevel craft, Entity entity, Pose3dc pose) {
    if (!(entity instanceof LivingEntity)
        || craft.isRemoved()
        || craft.getLevel() != entity.level()) return false;
    var local =
        pose.transformPositionInverse(
            new Vec3(entity.getX(), entity.getBoundingBox().minY + .05, entity.getZ()));
    var bounds = craft.getPlot().getBoundingBox();
    if (bounds == null) return false;
    double e = .125;
    return local.x >= bounds.minX() - e
        && local.x <= bounds.maxX() + 1 + e
        && local.y >= bounds.minY() - e
        && local.y <= bounds.maxY() + 1 + e
        && local.z >= bounds.minZ() - e
        && local.z <= bounds.maxZ() + 1 + e;
  }

  public static boolean passenger(ServerSubLevel host, Entity entity) {
    if (!(entity instanceof LivingEntity)) return false;
    var container = SubLevelContainer.getContainer(host.getLevel());
    if (container == null) return false;
    for (var entry : SubLevelPermissions.get(host).allowed())
      if (entry.sublevel()) {
        var found = container.getSubLevel(entry.id());
        if (!(found instanceof ServerSubLevel craft) || craft.isRemoved()) continue;
        for (var member : SubLevelCreatureParts.members(craft))
          if (within(member, entity, member.logicalPose())) return true;
      }
    return false;
  }
}
