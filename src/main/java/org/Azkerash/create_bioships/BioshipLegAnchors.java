package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.network.VisionDebug;

/** World-space planted tips and collision-driven steps, independent of the feeding graspers. */
public final class BioshipLegAnchors {
  public static final double TIP_RADIUS = .12;
  private static final int STEP_TICKS = 5;
  private final Vec3[] from = new Vec3[32], to = new Vec3[32], up = new Vec3[32];
  private final long[] started = new long[32];

  public Vec3 normal(int leg) {
    return up[leg] == null ? new Vec3(0, 1, 0) : up[leg];
  }

  public AABB contactBox(int leg) {
    return to[leg] == null ? null : new AABB(to[leg], to[leg]).inflate(TIP_RADIUS);
  }

  public Vec3 foot(int leg, double time) {
    if (to[leg] == null) return null;
    double t = Math.clamp((time - started[leg]) / STEP_TICKS, 0, 1);
    return from[leg].lerp(to[leg], t).add(up[leg].scale(Math.sin(Math.PI * t) * .3));
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.aquatic().swimming() || core.body().health() <= 0) {
      java.util.Arrays.fill(to, null);
      return;
    }
    var layout = BioshipSupport.layout(core);
    if (layout.legs().isEmpty()) return;
    var level = (ServerLevel) core.getLevel();
    var transform = core.ship().logicalPose();
    var origin = Vec3.atLowerCornerOf(core.structuralOrigin());
    var face = core.crawl().wallFacing();
    var direction =
        face == null
            ? new Vec3(0, -1, 0)
            : transform.transformNormal(Vec3.atLowerCornerOf(face.getNormal())).normalize();
    long now = level.getGameTime();
    boolean changed = false;
    for (int leg = 0; leg < layout.legs().size(); leg++) {
      var attachment = layout.legs().get(leg);
      var ideal = attachment.foot();
      Vec3 probe;
      if (face == null) {
        probe =
            transform.transformPosition(origin.add(ideal).add(0, core.hatchling() ? .85 : 1.25, 0));
      } else {
        var normal = Vec3.atLowerCornerOf(face.getNormal());
        var tangent =
            Math.abs(normal.y) > .5 ? new Vec3(1, 0, 0) : new Vec3(-normal.z, 0, normal.x);
        var local =
            new Vec3(.5, .4, .5)
                .add(tangent.scale((leg % 2 == 0 ? -1 : 1) * .7))
                .add(normal.cross(tangent).scale((leg < 2 ? -1 : 1) * .65));
        if (!attachment.bootstrap()) {
          var offset = attachment.root().subtract(BioshipSupport.layout(core).center());
          local =
              BioshipSupport.layout(core)
                  .center()
                  .add(offset.subtract(normal.scale(offset.dot(normal))));
        }
        probe = transform.transformPosition(origin.add(local));
      }
      var hit =
          BioshipSurfaceContacts.terrainRay(
              level, probe, probe.add(direction.scale(core.crawl().footingReach())));
      if (hit == null || !level.getFluidState(hit.getBlockPos()).isEmpty()) {
        var root = transform.transformPosition(origin.add(layout.legs().get(leg).root()));
        if (to[leg] == null || to[leg].distanceToSqr(root) > .001) {
          from[leg] = to[leg] == null ? root : foot(leg, now);
          to[leg] = root;
          up[leg] = Vec3.ZERO;
          started[leg] = now;
          changed = true;
        }
        continue;
      }
      var target = hit.getLocation();
      boolean reset = to[leg] == null || to[leg].distanceToSqr(target) > 16;
      if (!reset && now - started[leg] < STEP_TICKS) continue;
      boolean planted = !reset && touching(level, contactBox(leg));
      if (!reset && planted && to[leg].distanceToSqr(target) < .55 * .55) continue;
      // Keep the opposing pair planted while this pair transfers its weight.
      boolean neighborMoving = false;
      for (int other = 0; other < layout.legs().size(); other++)
        if (other != leg
            && ((other / 2 + other % 2) % 2 != (leg / 2 + leg % 2) % 2)
            && to[other] != null
            && now - started[other] < STEP_TICKS) neighborMoving = true;
      if (!reset && neighborMoving) continue;
      from[leg] = reset ? target : foot(leg, now);
      to[leg] = target;
      up[leg] = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
      started[leg] = reset ? now - STEP_TICKS : now;
      changed = true;
    }
    if (changed) {
      core.setChanged();
      level.sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
    }
  }

  Vec3 supportContact(ServerLevel level, int leg, long now) {
    return to[leg] != null
            && now - started[leg] >= STEP_TICKS
            && up[leg].lengthSqr() > .5
            && touching(level, contactBox(leg))
        ? to[leg]
        : null;
  }

  private static boolean touching(ServerLevel level, AABB box) {
    for (var cell :
        BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
      if (!level.getChunkSource().hasChunk(cell.getX() >> 4, cell.getZ() >> 4)) return false;
      for (var shape : level.getBlockState(cell).getCollisionShape(level, cell).toAabbs())
        if (shape.move(cell).intersects(box)) return true;
    }
    return false;
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    for (int i = 0; i < 32; i++)
      if (to[i] != null) {
        var foot = new CompoundTag();
        foot.put("From", VisionDebug.vector(from[i]));
        foot.put("To", VisionDebug.vector(to[i]));
        foot.put("Up", VisionDebug.vector(up[i]));
        foot.putLong("Started", started[i]);
        tag.put("Leg" + i, foot);
      }
    return tag;
  }

  void load(CompoundTag tag) {
    for (int i = 0; i < 32; i++) {
      to[i] = null;
      if (!tag.contains("Leg" + i)) continue;
      var foot = tag.getCompound("Leg" + i);
      from[i] = VisionDebug.vector(foot.getCompound("From"));
      to[i] = VisionDebug.vector(foot.getCompound("To"));
      up[i] = VisionDebug.vector(foot.getCompound("Up"));
      started[i] = foot.getLong("Started");
    }
  }
}
