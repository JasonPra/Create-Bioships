package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Water propulsion is separate from flight; breathing remains a finite survival resource. */
public final class BioshipAquatic {
  private int air = 300;
  private long lastTick = Long.MIN_VALUE;
  private volatile boolean wet, swimming;
  private boolean breathing = true;
  private volatile Vec3 velocity = Vec3.ZERO;

  public boolean swimming() {
    return swimming;
  }

  public int air() {
    return air;
  }

  boolean needsAir() {
    return !breathing && air < 200;
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() == lastTick) return;
    lastTick = core.getLevel().getGameTime();
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var level = core.getLevel();
    int immersed = 0, total = 0;
    for (var cell : BioshipDecay.blocks(core.ship())) {
      total++;
      var point = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(cell));
      if (level.getFluidState(BlockPos.containing(point)).is(FluidTags.WATER)
          && !aboveFluid(core, point)) immersed++;
    }
    wet = immersed > 0 && immersed * 4 >= total;
    breathing = canBreathe(core);
    if (!breathing) {
      air = Math.max(0, air - 1);
      if (air == 0 && level.getGameTime() % 20 == 0) core.body().drown(core);
    } else air = Math.min(300, air + 4);
    swimming =
        wet
            && !core.body().dead()
            && (core.canFly() || core.crawl().carriedLoad(core) <= core.crawl().loadCapacity());
    velocity = Vec3.ZERO;
    if (swimming) {
      var selected = core.brain().lastDecision();
      Vec3 direction =
          selected != null && selected.point() != null && !needsAir()
              ? selected.point().subtract(here).normalize()
              : new Vec3(0, 1, 0);
      var options =
          List.of(
              direction,
              new Vec3(0, 1, 0),
              new Vec3(1, .5, 0).normalize(),
              new Vec3(-1, .5, 0).normalize(),
              new Vec3(0, .5, 1).normalize(),
              new Vec3(0, .5, -1).normalize());
      for (var candidate : options)
        if (clear(core, candidate.scale(.65))) {
          velocity = candidate.scale(1.5);
          break;
        }
    }
    if (level.getGameTime() % 20 == 0) {
      core.setChanged();
      level.sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
    }
  }

  static boolean canBreathe(LivingCoreBlockEntity core) {
    boolean mouth = false;
    for (var entry : BioshipStructure.cells(core.ship()).entrySet())
      if (BioshipIntegration.isMaw(entry.getValue())) {
        mouth = true;
        var point = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(entry.getKey()));
        if (aboveFluid(core, point)) return true;
      }
    return !mouth && aboveFluid(core, BioshipBlueprint.physical(core, BlockPos.ZERO));
  }

  private static boolean aboveFluid(LivingCoreBlockEntity core, Vec3 point) {
    var pos = BlockPos.containing(point);
    var fluid = core.getLevel().getFluidState(pos);
    return fluid.isEmpty() || point.y >= pos.getY() + fluid.getHeight(core.getLevel(), pos);
  }

  private boolean clear(LivingCoreBlockEntity core, Vec3 offset) {
    var ship = core.ship();
    for (var cell : BioshipDecay.blocks(ship)) {
      var point =
          BlockPos.containing(
              ship.logicalPose().transformPosition(Vec3.atCenterOf(cell)).add(offset));
      if (!core.getLevel().getBlockState(point).getCollisionShape(core.getLevel(), point).isEmpty())
        return false;
    }
    return true;
  }

  void propel(ServerSubLevel ship, RigidBodyHandle body, double dt) {
    var current = body.getLinearVelocity(new Vector3d());
    var goal = velocity;
    var acceleration =
        new Vector3d(goal.x - current.x, goal.y - current.y, goal.z - current.z).mul(4);
    acceleration.y -=
        dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(
                ship.getLevel())
            .y;
    body.addLinearAndAngularVelocity(acceleration.mul(dt), new Vector3d());
  }

  CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("Air", air);
    t.putBoolean("Wet", wet);
    t.putBoolean("Breathing", breathing);
    t.putBoolean("Swimming", swimming);
    return t;
  }

  void load(CompoundTag t) {
    air = t.contains("Air") ? Math.clamp(t.getInt("Air"), 0, 300) : 300;
    wet = t.getBoolean("Wet");
    breathing = !t.contains("Breathing") || t.getBoolean("Breathing");
    swimming = t.getBoolean("Swimming");
  }
}
