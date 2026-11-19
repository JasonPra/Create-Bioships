package org.Azkerash.create_bioships.compat.letitwalk;

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
import org.Azkerash.create_bioships.components.logic.flipper.FlipperBlockEntity;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.core.locomotion.SubLevelFacing;
import org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder;
import org.Azkerash.create_bioships.mixin.WalkerSwimInvoker;
import org.joml.Vector3d;

/**
 * Native rowing, constrained IK and mass-aware swimming suspension, adapted for submerged routes.
 */
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
  private final Map<BlockPos, LegRuntime> fins = new LinkedHashMap<>();
  private double holdY = Double.NaN;
  private boolean supporting;

  public FlipperController(ServerSubLevel body) {
    this.body = body;
    walker = new Context(body);
  }

  public void rebuild(Collection<BlockPos> positions) {
    fins.clear();
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
      var built =
          new BuiltLeg(
              joints, List.of(), pivots, axes, arr(points.get(3)), true, axis, new double[3]);
      var fin = new LegRuntime(built, 0, order++, outward.x + outward.z > 0 ? 1 : -1, false);
      fins.put(p.immutable(), fin);
    }
    walker.legs.clear();
    walker.legs.addAll(fins.values());
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
    for (var e : fins.entrySet()) if (wet(Vec3.atCenterOf(e.getKey()))) active.add(e.getValue());
    supporting = BrainMotorControls.active(body) && submerged && !active.isEmpty();
    if (!supporting) {
      holdY = Double.NaN;
      if (body.getLevel().getGameTime() % 4 == 0)
        for (var p : fins.keySet())
          if (body.getLevel().getBlockEntity(p) instanceof FlipperBlockEntity be)
            be.pose(FlipperBlockEntity.rest(be.getBlockState()));
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
    if (!Double.isFinite(holdY)) holdY = center.y;
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
    if (handle != null && handle.isValid())
      speed = handle.getLinearVelocity(new Vector3d()).length();
    // No bank target: flippers never reach for or anchor to land.
    WalkerSwimInvoker.bioships$row(walker, active, body.logicalPose(), speed, Double.NaN, null);
    if (body.getLevel().getGameTime() % 4 != 0) return;
    var core = Vec3.atLowerCornerOf(walker.getBlockPos());
    for (var e : fins.entrySet()) {
      if (!(body.getLevel().getBlockEntity(e.getKey()) instanceof FlipperBlockEntity be)) continue;
      var leg = e.getValue();
      if (!active.contains(leg) || leg.flightFoot == null) {
        be.pose(FlipperBlockEntity.rest(be.getBlockState()));
        continue;
      }
      var b = leg.built;
      b.chain.solveRobust(arr(leg.flightFoot.subtract(core)), b.angles, 10, .01);
      var result = new ArrayList<Vec3>();
      for (var point : b.chain.forward(b.angles, null))
        result.add(
            new Vec3(point[0], point[1], point[2])
                .add(core)
                .subtract(Vec3.atLowerCornerOf(e.getKey())));
      be.pose(result);
    }
  }

  public void physics(double dt) {
    if (!supporting || !BrainMotorControls.active(body)) return;
    var handle = RigidBodyHandle.of(body);
    if (handle != null && handle.isValid()) WalkerPhysics.substep(walker, body, handle, dt);
  }
}
