package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.ModContent;
import dev.letitwalk.content.*;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.components.logic.flipper.FlipperBlockEntity;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.flippers.FlipperGeometry;
import org.azkerash.createbioships.core.locomotion.SubLevelFacing;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;
import org.joml.Vector3d;

/** Dedicated flipper strokes paired with native mass-aware swimming suspension. */
public final class FlipperController {
  private static final class Context extends WalkerCoreBlockEntity {
    Context(ServerSubLevel body) {
      super(body.getPlot().getCenterBlock(), ModContent.WALKER_CORE.get().defaultBlockState());
      setLevel(body.getLevel());
    }

    @Override
    public void syncToClients() {}

    @Override
    public void setChanged() {}

    @Override
    public boolean isPowered() {
      return true;
    }
  }

  private final ServerSubLevel body;
  private final Context walker;
  private final Map<BlockPos, LegRuntime> flippers = new LinkedHashMap<>();
  private static final double IDLE_PHASE_STEP = .08;
  private static final double DRIVEN_PHASE_STEP = .12;
  private static final double DRIVE_RESPONSE = .15;
  private static final double TURN_FULL_STROKE_RATE = .8;
  private double phase, strokeDrive;
  private double holdY = Double.NaN;
  private boolean supporting;

  public FlipperController(ServerSubLevel body) {
    this.body = body;
    walker = new Context(body);
  }

  public void rebuild(Collection<BlockPos> positions) {
    flippers.clear();
    var core = Vec3.atLowerCornerOf(walker.getBlockPos());
    int order = 0;
    for (var p : positions) {
      var state = body.getLevel().getBlockState(p);
      var points =
          FlipperBlockEntity.rest(state).stream()
              .map(v -> v.add(Vec3.atLowerCornerOf(p)).subtract(core))
              .toList();
      var outward =
          Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getNormal());
      var axis = outward.cross(new Vec3(0, 1, 0));
      var ids = List.of(body.getUniqueId(), body.getUniqueId(), body.getUniqueId());
      var joints = new ArrayList<BuiltLeg.Joint>();
      var pivots = new double[3][3];
      var axes = new double[3][3];
      for (int i = 0; i < 3; i++) {
        var a = i == 0 ? new Vec3(0, 1, 0) : axis;
        joints.add(new BuiltLeg.Joint(ids.get(i), ids.get(i), Vec3.ZERO, Vec3.ZERO, a, i == 0));
        pivots[i] = arr(points.get(i));
        axes[i] = arr(a);
      }
      // Native swimming physics needs support descriptors; these chains never drive animation.
      var built =
          new BuiltLeg(
              joints, List.of(), pivots, axes, arr(points.get(3)), true, axis, new double[3]);
      var fin = new LegRuntime(built, 0, order++, outward.x + outward.z > 0 ? 1 : -1, false);
      flippers.put(p.immutable(), fin);
    }
    walker.legs.clear();
    walker.legs.addAll(flippers.values());
    LegRuntime.countLegs(walker.legs);
  }

  private static double[] arr(Vec3 v) {
    return new double[] {v.x, v.y, v.z};
  }

  private boolean wet(Vec3 local) {
    var at = BlockPos.containing(body.logicalPose().transformPosition(local));
    return body.getLevel().hasChunkAt(at) && body.getLevel().getFluidState(at).is(FluidTags.WATER);
  }

  public boolean supporting() {
    return supporting;
  }

  public void update() {
    var active = new ArrayList<LegRuntime>();
    boolean submerged = wet(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    for (var e : flippers.entrySet()) {
      if (wet(Vec3.atCenterOf(e.getKey()))) {
        active.add(e.getValue());
      }
    }
    supporting = BrainMotorControls.active(body) && submerged && !active.isEmpty();
    if (!supporting) {
      holdY = Double.NaN;
      strokeDrive = 0;
      if (body.getLevel().getGameTime() % FlipperBlockEntity.POSE_INTERVAL_TICKS == 0) {
        for (var p : flippers.keySet()) {
          if (body.getLevel().getBlockEntity(p) instanceof FlipperBlockEntity be) {
            be.pose(FlipperBlockEntity.rest(be.getBlockState()));
          }
        }
      }
      return;
    }
    walker.setBlockState(
        walker.getBlockState().setValue(WalkerCoreBlock.FACING, SubLevelFacing.local(body)));
    walker.legs.clear();
    walker.legs.addAll(active);
    var request = BrainMotorControls.current(body);
    var velocity = request.mode() == SubLevelPathfinder.Mode.WATER ? request.velocity() : Vec3.ZERO;
    var center =
        body.logicalPose().transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    if (!Double.isFinite(holdY)) {
      holdY = center.y;
    }
    // Native surface swimming targets the waterline. Our water paths instead hold the requested
    // depth.
    holdY = Math.clamp(holdY + velocity.y * .05, center.y - .5, center.y + .5);
    walker.heightError = holdY - center.y;
    walker.targetVelocity.set(velocity.x, 0, velocity.z);
    walker.swimming = true;
    walker.stanceCount = active.size();
    walker.supportScale = 1;
    walker.comSupported = true;
    walker.supportPoint = null;
    walker.terrainNormal.set(0, 1, 0);
    walker.yawRate = request.mode() == SubLevelPathfinder.Mode.WATER ? request.yawRate() : 0;
    var forward = body.logicalPose().orientation().transform(new Vector3d(0, 0, 1));
    walker.desiredYaw = Math.atan2(forward.x, forward.z) + walker.yawRate * .05;
    walker.throttle = Math.clamp(velocity.length() / WalkerSwim.SWIM_SPEED, 0, 1);
    double speed = 0;
    var handle = RigidBodyHandle.of(body);
    if (handle != null && handle.isValid()) {
      speed = handle.getLinearVelocity(new Vector3d()).length();
    }
    double effort = Math.max(walker.throttle, Math.abs(walker.yawRate) / TURN_FULL_STROKE_RATE);
    effort = Math.clamp(Math.max(effort, speed / WalkerSwim.SWIM_SPEED), 0, 1);
    strokeDrive += (effort - strokeDrive) * DRIVE_RESPONSE;
    phase = (phase + IDLE_PHASE_STEP + DRIVEN_PHASE_STEP * strokeDrive) % (Math.PI * 2);
    if (body.getLevel().getGameTime() % FlipperBlockEntity.POSE_INTERVAL_TICKS != 0) {
      return;
    }
    for (var entry : flippers.entrySet()) {
      if (body.getLevel().getBlockEntity(entry.getKey())
          instanceof FlipperBlockEntity blockEntity) {
        var samples =
            active.contains(entry.getValue())
                ? FlipperGeometry.stroke(
                    blockEntity.getBlockState(), phase, strokeDrive, SubLevelFacing.local(body))
                : FlipperBlockEntity.rest(blockEntity.getBlockState());
        blockEntity.pose(samples);
      }
    }
  }

  public void physics(double dt) {
    if (!supporting || !BrainMotorControls.active(body)) {
      return;
    }
    var handle = RigidBodyHandle.of(body);
    if (handle != null && handle.isValid()) {
      WalkerPhysics.substep(walker, body, handle, dt);
    }
  }
}
