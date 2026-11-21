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
import org.azkerash.createbioships.core.flippers.FlipperThrust;
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
  // Forces use Sable mass-units * blocks/s²; response and fluid-drag gains use inverse seconds.
  private static final double FORCE_PER_FLIPPER = 24;
  private static final double RECOVERY_THRUST_FRACTION = .2;
  private static final double TURN_REQUEST_THRESHOLD = .02;
  private static final double MIN_THRUST = 1e-6;
  private static final double LINEAR_RESPONSE = 2, YAW_RESPONSE = 4;
  private static final double LINEAR_WATER_DRAG = .8, YAW_WATER_DRAG = .9;
  private static final double MIN_INERTIA = 1e-6;

  private static final class Stroke {
    private double phase, drive;
    private double direction = 1;
  }

  private final Map<BlockPos, Stroke> strokes = new LinkedHashMap<>();
  private final Map<BlockPos, Double> immersion = new LinkedHashMap<>();
  private final Map<BlockPos, Vec3> pressureCenters = new LinkedHashMap<>();
  private final Map<BlockPos, Double> efforts = new LinkedHashMap<>();
  private Vec3 requestedVelocity = Vec3.ZERO;
  private double requestedYaw;
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
    strokes.keySet().retainAll(flippers.keySet());
    efforts.keySet().retainAll(flippers.keySet());
    flippers.keySet().forEach(socket -> strokes.computeIfAbsent(socket, ignored -> new Stroke()));
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

  public Map<BlockPos, Double> getStrokeEfforts() {
    return Map.copyOf(efforts);
  }

  public boolean supporting() {
    return supporting;
  }

  public void update() {
    var active = new ArrayList<LegRuntime>();
    boolean submerged = wet(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    immersion.clear();
    pressureCenters.clear();
    for (var e : flippers.entrySet()) {
      var local =
          body.getLevel().getBlockEntity(e.getKey()) instanceof FlipperBlockEntity blockEntity
              ? blockEntity.points(0)
              : FlipperBlockEntity.rest(body.getLevel().getBlockState(e.getKey()));
      int wetSamples = 0;
      var centerOfPressure = Vec3.ZERO;
      for (int i = 1; i < local.size(); i++) {
        var sample = local.get(i - 1).lerp(local.get(i), .5).add(Vec3.atLowerCornerOf(e.getKey()));
        if (wet(sample)) {
          wetSamples++;
          centerOfPressure = centerOfPressure.add(sample);
        }
      }
      if (wetSamples > 0) {
        immersion.put(e.getKey(), wetSamples / (double) FlipperGeometry.SECTIONS);
        pressureCenters.put(e.getKey(), centerOfPressure.scale(1d / wetSamples));
        active.add(e.getValue());
      }
    }
    supporting = BrainMotorControls.active(body) && submerged && !active.isEmpty();
    if (!supporting) {
      holdY = Double.NaN;
      requestedVelocity = Vec3.ZERO;
      requestedYaw = 0;
      efforts.clear();
      animate(BrainMotorControls.active(body), 0);
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
    requestedVelocity = velocity;
    requestedYaw = request.mode() == SubLevelPathfinder.Mode.WATER ? request.yawRate() : 0;
    walker.targetVelocity.zero();
    walker.swimming = true;
    walker.stanceCount = active.size();
    walker.supportScale = 1;
    walker.comSupported = true;
    walker.supportPoint = null;
    walker.terrainNormal.set(0, 1, 0);
    walker.yawRate = 0;
    var forward = body.logicalPose().orientation().transform(new Vector3d(0, 0, 1));
    walker.desiredYaw = Math.atan2(forward.x, forward.z);
    walker.throttle = Math.clamp(velocity.length() / WalkerSwim.SWIM_SPEED, 0, 1);
    animate(true, Math.clamp(Math.abs(velocity.y) / WalkerSwim.SWIM_SPEED, 0, 1));
  }

  private void animate(boolean alive, double verticalEffort) {
    for (var entry : strokes.entrySet()) {
      var stroke = entry.getValue();
      double effort = supporting ? efforts.getOrDefault(entry.getKey(), 0d) : 0;
      double target = alive ? Math.max(Math.abs(effort), verticalEffort) : 0;
      stroke.drive += (target - stroke.drive) * DRIVE_RESPONSE;
      if (Math.abs(effort) > .01) {
        stroke.direction = Math.signum(effort);
      }
      if (alive) {
        stroke.phase =
            (stroke.phase + IDLE_PHASE_STEP + DRIVEN_PHASE_STEP * stroke.drive) % (Math.PI * 2);
      } else {
        stroke.drive = 0;
      }
      if (body.getLevel().getGameTime() % FlipperBlockEntity.POSE_INTERVAL_TICKS == 0
          && body.getLevel().getBlockEntity(entry.getKey())
              instanceof FlipperBlockEntity blockEntity) {
        blockEntity.pose(
            alive
                ? FlipperGeometry.stroke(
                    blockEntity.getBlockState(),
                    stroke.phase * stroke.direction,
                    stroke.drive,
                    SubLevelFacing.local(body))
                : FlipperBlockEntity.rest(blockEntity.getBlockState()));
      }
    }
  }

  public void physics(double dt) {
    if (!supporting || !BrainMotorControls.active(body)) {
      return;
    }
    var handle = RigidBodyHandle.of(body);
    if (handle == null || !handle.isValid()) {
      return;
    }
    var mass = body.getMassTracker();
    if (mass == null || mass.isInvalid()) {
      return;
    }
    var velocity = handle.getLinearVelocity(new Vector3d());
    var spin = handle.getAngularVelocity(new Vector3d());
    WalkerPhysics.substep(walker, body, handle, dt);
    var afterVelocity = handle.getLinearVelocity(new Vector3d());
    var afterSpin = handle.getAngularVelocity(new Vector3d());
    // Keep native depth/upright suspension. Horizontal propulsion and yaw come only from paddles.
    handle.addLinearAndAngularVelocity(
        new Vector3d(velocity.x - afterVelocity.x, 0, velocity.z - afterVelocity.z),
        new Vector3d(0, spin.y - afterSpin.y, 0));
    var nose = SubLevelFacing.world(body);
    var forward = new Vec3(nose.x, 0, nose.z).normalize();
    var localUp = body.logicalPose().orientation().transformInverse(new Vector3d(0, 1, 0));
    double inertia =
        Math.max(
            MIN_INERTIA, localUp.dot(mass.getInertiaTensor().transform(localUp, new Vector3d())));
    double current = velocity.x * forward.x + velocity.z * forward.z;
    double wanted = requestedVelocity.dot(forward);
    double force =
        mass.getMass() * (LINEAR_RESPONSE * (wanted - current) + LINEAR_WATER_DRAG * current);
    double torque = inertia * (YAW_RESPONSE * (requestedYaw - spin.y) + YAW_WATER_DRAG * spin.y);
    var center = body.logicalPose().position();
    var fins = new ArrayList<FlipperThrust.Fin>();
    for (var entry : immersion.entrySet()) {
      var socket = entry.getKey();
      // The submerged paddle area determines leverage, not an assumed symmetric socket layout.
      var point = body.logicalPose().transformPosition(pressureCenters.get(socket));
      var offset = point.subtract(new Vec3(center.x, center.y, center.z));
      var stroke = strokes.get(socket);
      // Recovery strokes have less authority; the allocator compensates using available effort.
      double power =
          RECOVERY_THRUST_FRACTION
              + (1 - RECOVERY_THRUST_FRACTION) * Math.max(0, Math.cos(stroke.phase));
      fins.add(new FlipperThrust.Fin(socket, offset, FORCE_PER_FLIPPER * entry.getValue() * power));
    }
    double turn = Math.abs(requestedYaw) > TURN_REQUEST_THRESHOLD ? requestedYaw : -spin.y;
    var forces = FlipperThrust.allocate(fins, forward, force, torque, turn);
    efforts.clear();
    for (var fin : fins) {
      double thrust = forces.getOrDefault(fin.socket(), 0d);
      efforts.put(
          fin.socket(),
          Math.clamp(thrust / (FORCE_PER_FLIPPER * immersion.get(fin.socket())), -1, 1));
      if (Math.abs(thrust) < MIN_THRUST) {
        continue;
      }
      var impulse = new Vector3d(forward.x, 0, forward.z).mul(thrust * dt);
      var lever = new Vector3d(fin.offset().x, fin.offset().y, fin.offset().z);
      applyWorldImpulse(handle, impulse, lever.cross(impulse, new Vector3d()));
    }
    // Fluid drag dissipates momentum gradually; it never assigns a fixed turning speed.
    applyWorldImpulse(
        handle,
        new Vector3d(velocity.x, 0, velocity.z).mul(-mass.getMass() * LINEAR_WATER_DRAG * dt),
        new Vector3d(0, -inertia * YAW_WATER_DRAG * spin.y * dt, 0));
  }

  private void applyWorldImpulse(RigidBodyHandle handle, Vector3d linear, Vector3d angular) {
    org.azkerash.createbioships.core.physics.SubLevelImpulse.applyWorld(
        body, handle, linear, angular);
  }
}
