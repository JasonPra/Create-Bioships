package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.ModContent;
import dev.letitwalk.content.*;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.*;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.core.locomotion.prototype.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Compact sockets generate real limb sublevels; native locomotion remains the default. */
public final class CompactLimbController {
  public static final String DATA = "create_bioships:compact_limbs",
      PART = "create_bioships:compact_limb";

  /** Generated collision tissue is edited through the one player-facing socket block. */
  public static net.minecraft.world.item.context.UseOnContext socketContext(
      net.minecraft.world.item.context.UseOnContext context) {
    if (!(context.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
        || context.getPlayer() == null) {
      return context;
    }
    var part = CoreAssemblyRules.containing(level, context.getClickedPos());
    if (part == null
        || part.getUserDataTag() == null
        || !part.getUserDataTag().contains(PART)
        || !SubLevelCreatureParts.isPart(part)) {
      return context;
    }
    var socket = BlockPos.of(part.getUserDataTag().getCompound(PART).getLong("Socket"));
    var owner = SubLevelCreatureParts.root(part);
    if (owner == part || !CompactLegSocketBlock.isSocket(level.getBlockState(socket))) {
      return context;
    }
    var face =
        level
            .getBlockState(socket)
            .getValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties
                    .HORIZONTAL_FACING);
    return new net.minecraft.world.item.context.UseOnContext(
        context.getPlayer(),
        context.getHand(),
        new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(socket), face, socket, false));
  }

  /** Reject old streamed-in segments after their socket was broken and replaced. */
  public static boolean ownsSavedSegment(ServerSubLevel owner, ServerSubLevel part) {
    if (owner.getUserDataTag() == null || part.getUserDataTag() == null) {
      return false;
    }
    long socket = part.getUserDataTag().getCompound(PART).getLong("Socket");
    for (var raw : owner.getUserDataTag().getCompound(DATA).getList("Legs", 10)) {
      var entry = (CompoundTag) raw;
      if (entry.getLong("Socket") != socket) {
        continue;
      }
      for (var segment : entry.getCompound("Leg").getList("segments", 10)) {
        var tag = (CompoundTag) segment;
        if (tag.hasUUID("id") && tag.getUUID("id").equals(part.getUniqueId())) {
          return true;
        }
      }
    }
    return false;
  }

  private final class Context extends WalkerCoreBlockEntity {
    private final ServerSubLevel owner;

    Context(ServerSubLevel body) {
      super(
          body.getPlot().getCenterBlock(),
          ModContent.WALKER_CORE
              .get()
              .defaultBlockState()
              .setValue(WalkerCoreBlock.FACING, Direction.NORTH));
      owner = body;
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

    @Override
    public float getTheoreticalSpeed() {
      return owner != null
              && org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion.get(
                      owner)
                  == org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion.Mode
                      .NATIVE_FAST
          ? org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion.COMPARISON_RPM
          : nativeGait.rpm();
    }

    @Override
    public void setLoad(double v) {
      load = v;
    }
  }

  private static final class Limb {
    BlockPos socket;
    int height, restAngle;
    double extension = 1;
    Direction face;
    LegRuntime runtime;
    final List<ServerSubLevel> bodies = new ArrayList<>();
    final List<GenericConstraintHandle> pins = new ArrayList<>();
    final List<Quaterniond> rest = new ArrayList<>();
    double[] lengths = new double[3];
    boolean contact;
    Vec3 contactPoint;
    boolean arachnid;
    int anatomyVersion, fanAngle;
    private long prototypeLimitRevision = Long.MIN_VALUE;
    private List<PrototypeJointPose.Limit> prototypeLimits = List.of();
    private double prototypeMobility;
  }

  private static final int ARACHNID_ANATOMY_VERSION = 3;
  private final ServerSubLevel body;
  private final Context walker;
  private final PrototypeCompactMotor prototype;
  private boolean prototypeActive;
  private final Map<BlockPos, Limb> limbs = new LinkedHashMap<>();
  private final Set<BlockPos> retiredSockets = new HashSet<>();
  private boolean dirty = true;
  private int physicalContacts;
  private org.azkerash.createbioships.core.locomotion.NativeGaitPolicy.Gait nativeGait =
      org.azkerash.createbioships.core.locomotion.NativeGaitPolicy.WALK;

  public CompactLimbController(ServerSubLevel body) {
    this.body = body;
    walker = new Context(body);
    prototype = new PrototypeCompactMotor(body);
    restore();
  }

  private boolean hasArachnidLegs() {
    return limbs.keySet().stream()
        .anyMatch(
            socket -> CompactLegSocketBlock.isArachnid(body.getLevel().getBlockState(socket)));
  }

  public void climbIntent(Vec3 movement) {
    if (prototypeActive && hasArachnidLegs()) {
      prototype.climbIntent(movement);
    }
  }

  public boolean wallAttached() {
    return prototypeActive && prototype.wallAttached();
  }

  public Vec3 surfaceMotion(Vec3 movement) {
    return prototypeActive
        ? prototype.surfaceMotion(movement)
        : new Vec3(movement.x, 0, movement.z);
  }

  public void changed() {
    dirty = true;
  }

  public void changed(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    dirty = true;
    if (limbs.containsKey(pos) && !CompactLegSocketBlock.isSocket(state)) {
      retiredSockets.add(pos.immutable());
    }
  }

  public boolean hasSocket(BlockPos pos) {
    return limbs.containsKey(pos);
  }

  private static Vector3d v(Vec3 p) {
    return new Vector3d(p.x, p.y, p.z);
  }

  private static Vec3 vec(double[] p) {
    return new Vec3(p[0], p[1], p[2]);
  }

  private static double[] arr(Vec3 p) {
    return new double[] {p.x, p.y, p.z};
  }

  private static Vec3 anchor(ServerSubLevel b) {
    return Vec3.atLowerCornerOf(b.getPlot().getCenterBlock()).add(.5, 0, .5);
  }

  private List<Vec3> restPoints(BlockPos socket, Direction face, double height) {
    return restPoints(socket, face, height, LimbTuning.get(body.getLevel(), socket).restAngle());
  }

  private List<Vec3> restPoints(BlockPos socket, Direction face, double height, int angle) {
    return restPoints(socket, face, height, angle, 0);
  }

  private List<Vec3> restPoints(
      BlockPos socket, Direction face, double height, int angle, int legacyFan) {
    // Old saves need their former geometry only while reconnecting the same physical segments.
    // The version migration below rebuilds them at the player's angle without the hidden fan.
    return CompactLegGeometry.restPoints(
        socket,
        face,
        height,
        angle + legacyFan,
        CompactLegSocketBlock.isArachnid(body.getLevel().getBlockState(socket)));
  }

  private void fill(ServerSubLevel part, int bone, int height, double length, boolean arachnid) {
    var base = part.getPlot().getCenterBlock();
    for (int y = 0; y < Math.ceil(length); y++) {
      body.getLevel()
          .setBlock(
              base.above(y),
              ComponentRegistry.COMPACT_LIMB
                  .get()
                  .defaultBlockState()
                  .setValue(CompactLimbBlock.ARACHNID, arachnid)
                  .setValue(CompactLimbBlock.BONE, bone)
                  .setValue(CompactLimbBlock.HEIGHT, height)
                  .setValue(
                      CompactLimbBlock.FILL,
                      Math.clamp((int) Math.round(Math.min(1, length - y) * 64), 1, 64)),
              Block.UPDATE_ALL);
    }
  }

  private Limb create(BlockPos socket, int height, Direction face) {
    var limb = new Limb();
    limb.socket = socket.immutable();
    limb.arachnid = CompactLegSocketBlock.isArachnid(body.getLevel().getBlockState(socket));
    limb.anatomyVersion = ARACHNID_ANATOMY_VERSION;
    limb.fanAngle = 0;
    limb.height = height;
    limb.face = face;
    limb.restAngle = LimbTuning.get(body.getLevel(), socket).restAngle();
    var points = restPoints(socket, face, limb.extension);
    var container = SubLevelContainer.getContainer(body.getLevel());
    for (int i = 0; i < 3; i++) {
      var delta = points.get(i + 1).subtract(points.get(i));
      limb.lengths[i] = delta.length();
      var q = new Quaterniond().rotationTo(new Vector3d(0, 1, 0), v(delta).normalize());
      limb.rest.add(q);
      var pose = new Pose3d();
      var world = body.logicalPose().transformPosition(points.get(i));
      pose.position().set(v(world));
      pose.orientation().set(body.logicalPose().orientation()).mul(q);
      var part = (ServerSubLevel) container.allocateNewSubLevel(pose);
      part.getPlot().newEmptyChunk(part.getPlot().getCenterChunk());
      part.logicalPose().rotationPoint().set(v(anchor(part)));
      part.logicalPose().position().set(v(world));
      var tag = new CompoundTag();
      tag.putUUID("Owner", body.getUniqueId());
      tag.putLong("Socket", socket.asLong());
      tag.putInt("Bone", i);
      var data = part.getUserDataTag() == null ? new CompoundTag() : part.getUserDataTag().copy();
      data.put(PART, tag);
      part.setUserDataTag(data);
      fill(
          part,
          i,
          height,
          limb.lengths[i],
          CompactLegSocketBlock.isArachnid(body.getLevel().getBlockState(socket)));
      placeSegment(part, world, new Quaterniond(body.logicalPose().orientation()).mul(q));
      limb.bodies.add(part);
    }
    configure(limb, points, null);
    for (var part : limb.bodies) {
      SubLevelCreatureParts.attach(body, part);
    }
    return limb;
  }

  /** Geometry edits preserve the planted distal end while rebuilding native chain rest geometry. */
  private void placeSegment(ServerSubLevel part, Vec3 start, Quaterniond orientation) {
    // During a synchronous restore, Sable can defer the merged mass update until
    // its next physics step. Flush native mass data before using its pivot.
    part.updateMergedMassData(1);
    var com = part.getMassTracker().getCenterOfMass();
    if (com == null) {
      throw new IllegalStateException(
          "Generated compact segment has no native mass: " + part.getUniqueId());
    }
    var offset = new Vector3d(com).sub(v(anchor(part)));
    var center = orientation.transform(offset).add(v(start));
    part.logicalPose().rotationPoint().set(com);
    part.logicalPose().position().set(center);
    part.logicalPose().orientation().set(orientation);
    SubLevelContainer.getContainer(body.getLevel())
        .physicsSystem()
        .getPipeline()
        .teleport(part, center, orientation);
    part.updateLastPose();
  }

  private void resize(Limb limb, double extension) {
    var oldRuntime = SocketJointRig.save(limb.runtime);
    var angles = limb.runtime.built.angles.clone();
    var oldLengths = limb.lengths.clone();
    for (int i = 0; i < limb.bodies.size(); i++) {
      var part = limb.bodies.get(i);
      oldLengths[i] =
          Math.max(
              oldLengths[i],
              part.getPlot().getBoundingBox().maxY() - part.getPlot().getCenterBlock().getY() + 1);
    }
    var footPart = limb.bodies.getLast();
    var foot =
        footPart.logicalPose().transformPosition(anchor(footPart).add(0, limb.lengths[2], 0));
    releasePins(limb);
    for (var filter : limb.runtime.built.contactFilters) {
      if (filter.isValid()) {
        filter.remove();
      }
    }
    limb.runtime.built.contactFilters.clear();
    limb.extension = extension;
    int angle = LimbTuning.get(body.getLevel(), limb.socket).restAngle();
    boolean changedAngle = limb.restAngle != angle || limb.fanAngle != 0;
    limb.fanAngle = 0;
    if (changedAngle) {
      prototype.resetRestPose(limb.socket);
    }
    limb.restAngle = angle;
    var points = restPoints(limb.socket, limb.face, extension);
    limb.rest.clear();
    for (int i = 0; i < 3; i++) {
      var d = points.get(i + 1).subtract(points.get(i));
      limb.lengths[i] = d.length();
      limb.rest.add(new Quaterniond().rotationTo(new Vector3d(0, 1, 0), v(d).normalize()));
    }
    configure(limb, points, null);
    System.arraycopy(angles, 0, limb.runtime.built.angles, 0, angles.length);
    var origin = Vec3.atLowerCornerOf(walker.getBlockPos());
    var target = body.logicalPose().transformPositionInverse(foot).subtract(origin);
    // Keep both the socket and the planted foot connected while changing bone lengths.
    // Translating a longer chain backward from its foot leaves a gap at the hip, which
    // the constraint solver can close by driving a light distal segment through terrain.
    limb.runtime.built.chain.solveRobust(arr(target), limb.runtime.built.angles, 16, .02);
    var posed = SocketJointRig.points(limb.runtime.built, origin);
    var turn = new Quaterniond();
    int axisCursor = 0;
    for (int i = 0; i < 3; i++) {
      var part = limb.bodies.get(i);
      int end = SocketJointRig.endAxis(limb.runtime.built, i);
      while (axisCursor <= end) {
        var axis = limb.runtime.built.restAxes[axisCursor];
        turn.mul(
            new Quaterniond()
                .fromAxisAngleRad(
                    axis[0], axis[1], axis[2], limb.runtime.built.angles[axisCursor]));
        axisCursor++;
      }
      var q = new Quaterniond(body.logicalPose().orientation()).mul(turn).mul(limb.rest.get(i));
      fill(
          part,
          i,
          limb.height,
          limb.lengths[i],
          CompactLegSocketBlock.isArachnid(body.getLevel().getBlockState(limb.socket)));
      for (int y = (int) Math.ceil(limb.lengths[i]); y < (int) Math.ceil(oldLengths[i]); y++) {
        body.getLevel().removeBlock(part.getPlot().getCenterBlock().above(y), false);
      }
      placeSegment(part, body.logicalPose().transformPosition(posed.get(i)), q);
    }
    oldRuntime = SocketJointRig.geometry(oldRuntime, limb.runtime);
    var restored = SocketJointRig.loadCompact(body, limb.socket, oldRuntime);
    if (restored != null && !changedAngle) {
      limb.runtime = restored;
    }
  }

  private void configure(Limb limb, List<Vec3> points, BuiltLeg saved) {
    var origin = Vec3.atLowerCornerOf(walker.getBlockPos());
    var coxa = points.get(1).subtract(points.get(0));
    var tangent = new Vec3(coxa.x, 0, coxa.z).normalize().cross(new Vec3(0, 1, 0));
    var joints = new ArrayList<BuiltLeg.Joint>();
    var ids = new ArrayList<UUID>();
    var pivots = new double[3][];
    var axes = new double[3][];
    for (int i = 0; i < 3; i++) {
      var parent = i == 0 ? body : limb.bodies.get(i - 1);
      var child = limb.bodies.get(i);
      var pa = i == 0 ? points.get(i) : anchor(parent).add(0, limb.lengths[i - 1], 0);
      var ca = anchor(child);
      var axis = i == 0 ? new Vec3(0, 1, 0) : tangent;
      joints.add(
          new BuiltLeg.Joint(parent.getUniqueId(), child.getUniqueId(), pa, ca, axis, i == 0));
      ids.add(child.getUniqueId());
      pivots[i] = arr(points.get(i).subtract(origin));
      axes[i] = arr(axis);
    }
    var built =
        saved != null
            ? saved
            : new BuiltLeg(
                joints,
                ids,
                pivots,
                axes,
                arr(points.getLast().subtract(origin)),
                true,
                tangent,
                new double[3]);
    built.footPlot = anchor(limb.bodies.getLast()).add(0, limb.lengths[2], 0);
    built.footKind = LegFootBlock.Kind.NORMAL;
    built.footKindKnown = true;
    built.segmentOffsets = new Vec3[3];
    for (int i = 0; i < 3; i++) {
      built.segmentOffsets[i] = points.get(i).subtract(origin).subtract(anchor(limb.bodies.get(i)));
    }
    double yaw =
        Math.atan2(-limb.face.getStepZ(), limb.face.getStepX()) + Math.toRadians(limb.restAngle);
    limb.runtime =
        new LegRuntime(
            built,
            yaw,
            limbs.size(),
            limb.face == Direction.WEST || limb.face == Direction.NORTH ? -1 : 1,
            false);
    limb.runtime = SocketJointRig.compact(body, limb.socket, limb.runtime);
    ensurePins(limb);
  }

  /**
   * Keep saved identities while Sable streams their plots back in; never manufacture replacements.
   */
  private boolean loaded(Limb limb) {
    var container = SubLevelContainer.getContainer(body.getLevel());
    var found = new ArrayList<ServerSubLevel>();
    for (var id : new LinkedHashSet<>(limb.runtime.built.segments)) {
      var entry = container.getSubLevel(id);
      if (!(entry instanceof ServerSubLevel part) || part.isRemoved()) {
        return false;
      }
      found.add(part);
    }
    if (found.size() != 3) {
      return false;
    }
    limb.bodies.clear();
    limb.bodies.addAll(found);
    return true;
  }

  private void ensurePins(Limb limb) {
    if (!loaded(limb)) {
      releasePins(limb);
      return;
    }
    if (limb.pins.size() == 3 && limb.pins.stream().allMatch(GenericConstraintHandle::isValid)) {
      return;
    }
    releasePins(limb);
    var pipe = SubLevelContainer.getContainer(body.getLevel()).physicsSystem().getPipeline();
    for (int i = 0; i < 3; i++) {
      var joint = SocketJointRig.base(limb.runtime.built).joints.get(i);
      var parent = i == 0 ? body : limb.bodies.get(i - 1);
      var child = limb.bodies.get(i);
      // Express the native chain's hinge axis in each segment's own rest frame.
      var socketFrame = SocketJointRig.frame(limb.runtime.built, i);
      var axisFrame =
          socketFrame == null
              ? new Quaterniond().rotationTo(new Vector3d(1, 0, 0), v(joint.axis).normalize())
              : socketFrame;
      var qa = i == 0 ? new Quaterniond() : new Quaterniond(limb.rest.get(i - 1));
      var qb = limb.rest.get(i);
      var pin =
          pipe.addConstraint(
              parent,
              child,
              new GenericConstraintConfiguration(
                  v(joint.parentPlot),
                  v(joint.childPlot),
                  qa.conjugate().mul(axisFrame),
                  new Quaterniond(qb).conjugate().mul(axisFrame),
                  EnumSet.of(
                      ConstraintJointAxis.LINEAR_X,
                      ConstraintJointAxis.LINEAR_Y,
                      ConstraintJointAxis.LINEAR_Z,
                      ConstraintJointAxis.ANGULAR_Z)));
      if (pin != null) {
        pin.setContactsEnabled(false);
        pin.setLimit(ConstraintJointAxis.ANGULAR_X, i == 0 ? -1.22 : -2.8, i == 0 ? 1.22 : 2.8);
        var limits = SocketJointRig.limits(limb.runtime.built, i);
        if (limits != null) {
          pin.setLimit(
              ConstraintJointAxis.ANGULAR_X,
              -Math.toRadians(limits.bend()),
              Math.toRadians(limits.bend()));
          pin.setLimit(
              ConstraintJointAxis.ANGULAR_Y,
              -Math.toRadians(limits.swivel() / 2d),
              Math.toRadians(limits.swivel() / 2d));
        }
        limb.pins.add(pin);
      }
    }
  }

  public void discardRemoved() {
    boolean removed = false;
    var it = limbs.values().iterator();
    while (it.hasNext()) {
      var l = it.next();
      if (retiredSockets.remove(l.socket)
          || !CompactLegSocketBlock.isSocket(body.getLevel().getBlockState(l.socket))) {
        destroy(l);
        it.remove();
        removed = true;
      }
    }
    retiredSockets.clear();
    if (removed) {
      refreshLegs();
      save();
    }
  }

  private void rebuild() {
    if (!dirty) {
      return;
    }
    dirty = false;
    discardRemoved();
    for (var socket : SubLevelHealth.blocks(body)) {
      var state = body.getLevel().getBlockState(socket);
      if (!CompactLegSocketBlock.isSocket(state)) {
        continue;
      }
      int height = state.getValue(CrabLegBlock.HEIGHT);
      var face =
          state.getValue(
              net.minecraft.world.level.block.state.properties.BlockStateProperties
                  .HORIZONTAL_FACING);
      var old = limbs.get(socket);
      if (old != null
          && (old.face != face || old.arachnid != CompactLegSocketBlock.isArachnid(state))) {
        destroy(old);
        limbs.remove(socket);
        old = null;
      }
      if (old == null) {
        limbs.put(socket.immutable(), create(socket, height, face));
      } else {
        old.height = height;
      }
    }
    refreshLegs();
    save();
  }

  private void refreshLegs() {
    walker.legs.clear();
    for (var l : limbs.values()) {
      walker.legs.add(l.runtime);
    }
    LegRuntime.countLegs(walker.legs);
    walker.totalMass =
        body.getMassTracker().getMass()
            + limbs.values().stream()
                .flatMap(l -> l.bodies.stream())
                .filter(b -> !b.isRemoved())
                .mapToDouble(b -> b.getMassTracker().getMass())
                .sum();
  }

  private boolean postureEnabled() {
    return BrainMotorControls.groundPostureEnabled(body);
  }

  /** Feed the native pose controls the stance implied by unequal socket lengths. */
  private void stancePreference() {
    double sx = 0, sz = 0, sh = 0;
    int n = limbs.size();
    walker.leanPitchTarget = walker.leanRollTarget = 0;
    if (n < 3) {
      return;
    }
    for (var l : limbs.values()) {
      sx += l.socket.getX();
      sz += l.socket.getZ();
      sh += l.runtime.gait().standHeight();
    }
    sx /= n;
    sz /= n;
    sh /= n;
    double xx = 0, zz = 0, xz = 0, xh = 0, zh = 0;
    for (var l : limbs.values()) {
      double x = l.socket.getX() - sx,
          z = l.socket.getZ() - sz,
          h = l.runtime.gait().standHeight() - sh;
      xx += x * x;
      zz += z * z;
      xz += x * z;
      xh += x * h;
      zh += z * h;
    }
    double d = xx * zz - xz * xz;
    double x = d > 1e-6 ? (xh * zz - zh * xz) / d : 0, z = d > 1e-6 ? (zh * xx - xh * xz) / d : 0;
    walker.leanPitchTarget = Math.clamp(-Math.atan(z), -.6, .6);
    walker.leanRollTarget = Math.clamp(-Math.atan(x), -.6, .6);
  }

  private List<PrototypeCompactMotor.Binding> prototypeBindings() {
    var result = new ArrayList<PrototypeCompactMotor.Binding>();
    var ranks = new EnumMap<Direction, Integer>(Direction.class);
    long limitRevision =
        org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.motionRevision(body);
    var upVector = body.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
    var rootUp = new Vec3(upVector.x, upVector.y, upVector.z);
    var ordered = new ArrayList<>(limbs.values());
    ordered.sort(
        Comparator.comparingInt((Limb l) -> l.face.ordinal())
            .thenComparingInt(
                l -> l.face.getAxis() == Direction.Axis.X ? l.socket.getZ() : l.socket.getX()));
    for (var limb : ordered) {
      if (!loaded(limb)) {
        continue;
      }
      int rank = ranks.merge(limb.face, 1, Integer::sum) - 1;
      int group =
          ((limb.face == Direction.NORTH || limb.face == Direction.WEST) ? 0 : 1) ^ (rank % 2);
      var restPoints = restPoints(limb.socket, limb.face, limb.extension, limb.restAngle);
      var hip = restPoints.getFirst();
      if (limb.prototypeLimitRevision != limitRevision) {
        var limits = new ArrayList<PrototypeJointPose.Limit>();
        double mobility = 0;
        for (int i = 0; i < 3; i++) {
          var setting = SocketJointSettings.get(body, SocketJointSettings.compact(limb.socket, i));
          limits.add(
              new PrototypeJointPose.Limit(
                  Math.toRadians(setting.swivel() / 2d), Math.toRadians(setting.bend())));
          mobility += Math.min(1, Math.max(setting.swivel() / 360d, setting.bend() / 60d));
        }
        limb.prototypeLimits = List.copyOf(limits);
        limb.prototypeMobility = mobility / 3;
        limb.prototypeLimitRevision = limitRevision;
      }
      var restDirections = new ArrayList<Vec3>();
      for (int i = 0; i < 3; i++) {
        var direction =
            body.logicalPose()
                .orientation()
                .transform(v(restPoints.get(i + 1).subtract(restPoints.get(i)).normalize()));
        restDirections.add(new Vec3(direction.x, direction.y, direction.z));
      }
      var firstDirection = restPoints.get(1).subtract(hip);
      var outward = new Vec3(firstDirection.x, 0, firstDirection.z).normalize();
      var worldOutward = body.logicalPose().orientation().transform(v(outward));
      var worldHip = body.logicalPose().transformPosition(hip);
      var leg =
          new PrototypeGait.Leg(
              limb.socket,
              worldHip,
              new Vec3(worldOutward.x, worldOutward.y, worldOutward.z),
              limb.extension + .5,
              limb.lengths[0],
              limb.lengths[1],
              limb.lengths[2],
              group,
              CompactLegSocketBlock.isArachnid(body.getLevel().getBlockState(limb.socket))
                  ? LegMotionProfile.ARACHNID
                  : LegMotionProfile.CRAB);
      // Locked/narrow joints can require a different neutral standing height, not stretched bones.
      var neutral =
          worldHip
              .add(
                  (PrototypeGait.anchored(leg)
                          ? ArachnidBodyMotor.tangent(
                              new Vec3(worldOutward.x, worldOutward.y, worldOutward.z), rootUp)
                          : new Vec3(worldOutward.x, 0, worldOutward.z))
                      .normalize()
                      .scale((limb.lengths[0] + limb.lengths[1] + limb.lengths[2]) * .5))
              .subtract(
                  (PrototypeGait.anchored(leg) ? rootUp : new Vec3(0, 1, 0)).scale(leg.height()));
      var bounded =
          PrototypeJointPose.solve(leg, neutral, restDirections, rootUp, limb.prototypeLimits);
      double standingHeight =
          Math.max(
              .1,
              PrototypeGait.anchored(leg)
                  ? worldHip.subtract(bounded.getLast()).dot(rootUp)
                  : worldHip.y - bounded.getLast().y);
      leg =
          new PrototypeGait.Leg(
              leg.socket(),
              leg.hip(),
              leg.outward(),
              standingHeight,
              leg.coxa(),
              leg.thigh(),
              leg.shin(),
              leg.group(),
              leg.profile());
      result.add(
          new PrototypeCompactMotor.Binding(
              leg,
              List.copyOf(limb.bodies),
              List.copyOf(restDirections),
              limb.prototypeLimits,
              rootUp,
              limb.prototypeMobility));
    }
    return result;
  }

  public void update() {
    rebuild();
    if (limbs.isEmpty()) {
      return;
    }
    if (body.getLevel().getGameTime() % 4 == 0) {
      boolean resized = false;
      for (var limb : limbs.values()) {
        if (loaded(limb)
            && (Math.abs(limb.extension - limb.height) > .001
                || limb.restAngle != LimbTuning.get(body.getLevel(), limb.socket).restAngle()
                || (limb.arachnid && limb.anatomyVersion < ARACHNID_ANATOMY_VERSION))) {
          resize(limb, limb.extension + Math.clamp(limb.height - limb.extension, -.125, .125));
          limb.anatomyVersion = ARACHNID_ANATOMY_VERSION;
          resized = true;
        }
      }
      if (resized) {
        refreshLegs();
        save();
      }
    }
    SocketJointRig.ensureContactFilters(walker, body.getLevel());
    for (var l : limbs.values()) {
      SocketJointRig.apply(l.runtime.built);
    }
    boolean usePrototype =
        (PrototypeLocomotion.get(body) == PrototypeLocomotion.Mode.PROTOTYPE || hasArachnidLegs())
            && postureEnabled();
    if (usePrototype != prototypeActive) {
      if (!usePrototype) {
        BuiltLegDriver.updateActualFeet(walker, body.getLevel());
        for (var limb : limbs.values()) {
          var leg = limb.runtime;
          leg.state = LegRuntime.State.STANCE;
          leg.anchor = leg.actualFoot;
          leg.swingFrom = leg.actualFoot;
          leg.swingTo = leg.actualFoot;
          leg.lastFoothold = leg.actualFoot;
          leg.settled = false;
          leg.built.prevDesired = null;
          leg.built.prevDesiredRot = null;
        }
      }
      prototype.clear();
      prototypeActive = usePrototype;
    }
    if (usePrototype) {
      prototype.update(prototypeBindings());
      physicalContacts = prototype.contacts();
      if (body.getLevel().getGameTime() % 20 == 0) {
        save();
      }
      return;
    }
    BuiltLegDriver.updateActualFeet(walker, body.getLevel());
    var family = new HashSet<UUID>();
    for (var p : SubLevelCreatureParts.members(body)) {
      family.add(p.getUniqueId());
    }
    TerrainProbe.withIgnored(
        p -> family.contains(p.getUniqueId()),
        () -> {
          physicalContacts = 0;
          for (var l : limbs.values()) {
            l.contactPoint =
                loaded(l) ? CompactLegCollisions.contact(body, l.bodies, l.lengths) : null;
            l.contact = l.contactPoint != null;
            if (l.contact) {
              physicalContacts++;
            }
          }
          if (postureEnabled()) {
            var r = BrainMotorControls.current(body);
            var local = body.logicalPose().orientation().transformInverse(v(r.velocity()));
            boolean fast =
                org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion.get(body)
                    == org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion
                        .Mode.NATIVE_FAST;
            nativeGait =
                org.azkerash.createbioships.core.locomotion.NativeGaitPolicy.select(
                    org.azkerash.createbioships.core.locomotion.NativeGaitPolicy.relative(
                        new Vec3(local.x, local.y, local.z),
                        org.azkerash.createbioships.core.locomotion.SubLevelFacing.local(body)),
                    r.yawRate(),
                    physicalContacts,
                    limbs.size());
            int keys =
                fast
                    ? org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion
                            .COMPARISON_GEAR
                        << WalkerBrain.GEAR_SHIFT
                    : nativeGait.gear() << WalkerBrain.GEAR_SHIFT;
            boolean strafe = Math.abs(local.x) > .05 || !fast && nativeGait.strafing();
            if (strafe) {
              keys |= WalkerBrain.KEY_CRAB;
            }
            if (fast && !strafe && r.velocity().horizontalDistance() > 1
                || !fast && nativeGait.galloping()) {
              keys |= WalkerBrain.KEY_GALLOP;
            }
            if (local.z < -.05) {
              keys |= WalkerBrain.KEY_FORWARD;
            }
            if (local.z > .05) {
              keys |= WalkerBrain.KEY_BACK;
            }
            if (local.x < -.05) {
              keys |= WalkerBrain.KEY_LEFT;
            }
            if (local.x > .05) {
              keys |= WalkerBrain.KEY_RIGHT;
            }
            walker.input = keys;
            walker.lastInputTime = body.getLevel().getGameTime();
            walker.receiveTurn(
                Math.clamp(
                        r.yawRate(),
                        -org.azkerash.createbioships.core.locomotion.LegTurnDrive.MAX_YAW_RATE,
                        org.azkerash.createbioships.core.locomotion.LegTurnDrive.MAX_YAW_RATE)
                    * .05);
            stancePreference();
            WalkerBrain.tick(walker, body);
            for (var leg : walker.legs) {
              SocketJointRig.prepareSwing(leg, body.getLevel().getGameTime());
            }
            double wanted = Math.hypot(r.velocity().x, r.velocity().z);
            double permitted = Math.hypot(walker.targetVelocity.x, walker.targetVelocity.z);
            double factor = permitted > 1e-8 ? Math.min(1, wanted / permitted) : 0;
            walker.targetVelocity.mul(factor);
            BuiltLegDriver.solve(walker, body, body.getLevel().getGameTime());
          }
        });
    // Let It Walk/Sable serialize joint angles and physical poses for reload recovery.
    if (body.getLevel().getGameTime() % 20 == 0) {
      save();
    }
  }

  public void physics(double dt) {
    discardRemoved();
    boolean powered = postureEnabled();
    boolean allLoaded = true;
    for (var l : limbs.values()) {
      if (!loaded(l)) {
        allLoaded = false;
        releasePins(l);
        continue;
      }
      if (powered) {
        releasePins(l);
      } else {
        ensurePins(l);
        var tuning = LimbTuning.get(body.getLevel(), l.socket);
        for (int i = 0; i < l.pins.size(); i++) {
          var pin = l.pins.get(i);
          var limits = SocketJointRig.limits(l.runtime.built, i);
          if (limits != null) {
            pin.setLimit(
                ConstraintJointAxis.ANGULAR_X,
                -Math.toRadians(limits.bend()),
                Math.toRadians(limits.bend()));
            pin.setLimit(
                ConstraintJointAxis.ANGULAR_Y,
                -Math.toRadians(limits.swivel() / 2d),
                Math.toRadians(limits.swivel() / 2d));
          }
          pin.setMotor(
              ConstraintJointAxis.ANGULAR_X,
              0,
              2 * tuning.stiffness(),
              4 * tuning.damping(),
              true,
              1);
        }
      }
    }
    if (!powered) {
      return;
    }
    var handle = RigidBodyHandle.of(body);
    if (handle == null || !handle.isValid()) {
      return;
    }
    if (prototypeActive) {
      if (allLoaded) {
        prototype.physics(prototypeBindings(), dt);
      }
      return;
    }
    // Match WalkerCoreBlockEntity's native physics ordering and support decisions.
    // Native WalkerBrain/WalkerPhysics determine loss of footing; no extra contact gate.
    var beforeTurn = handle.getAngularVelocity(new Vector3d());
    WalkerPhysics.substep(walker, body, handle, dt);
    var afterTurn = handle.getAngularVelocity(new Vector3d());
    // Keep native suspension, but replace the hull's yaw servo with contact forces from legs.
    handle.addLinearAndAngularVelocity(
        new Vector3d(), new Vector3d(0, beforeTurn.y - afterTurn.y, 0));
    var stance = new ArrayList<org.azkerash.createbioships.core.locomotion.LegTurnDrive.Foot>();
    for (var leg : limbs.values()) {
      if (leg.contactPoint != null && leg.runtime.state == LegRuntime.State.STANCE) {
        stance.add(
            new org.azkerash.createbioships.core.locomotion.LegTurnDrive.Foot(
                leg.contactPoint, null));
      }
    }
    LegTurnPhysics.apply(
        body, stance, new Vec3(0, 1, 0), BrainMotorControls.current(body).yawRate(), dt);
    for (var l : limbs.values()) {
      SocketJointRig.apply(l.runtime.built);
    }
    BuiltLegDriver.solve(walker, body, body.getLevel().getGameTime());
    // Preserve native partial-limb handling while Sable streams a saved rig back in.
    var posed =
        allLoaded
            ? NativeLegPoseBatch.calculate(walker, body, dt)
            : BuiltLegDriver.substep(walker, body, dt);
    for (var limb : limbs.values()) {
      if (!loaded(limb)) {
        continue;
      }
      for (int i = 0; i < limb.bodies.size(); i++) {
        var part = limb.bodies.get(i);
        var pose = posed.get(part.getUniqueId());
        if (pose == null) {
          continue;
        }
        // Native built limbs use a shared rest frame. Our narrow collision bones are
        // stored along local +Y, so adapt only that rest frame, not gait or suspension.
        var offset = new Vector3d(pose.rotationPoint()).sub(v(anchor(part)));
        var correction = limb.rest.get(i).transform(new Vector3d(offset)).sub(offset);
        pose.turn().transform(correction);
        var position = new Vector3d(pose.position()).add(correction);
        var turn = new Quaterniond(pose.turn()).mul(limb.rest.get(i));
        var velocity =
            new Vector3d(pose.velocity()).add(new Vector3d(pose.spin()).cross(correction));
        var spin = new Vector3d(pose.spin());
        var bone = RigidBodyHandle.of(part);
        if (bone == null || !bone.isValid()) {
          continue;
        }
        bone.teleport(position, turn);
        bone.addLinearAndAngularVelocity(
            velocity.sub(bone.getLinearVelocity(new Vector3d()), new Vector3d()),
            spin.sub(bone.getAngularVelocity(new Vector3d()), new Vector3d()));
        if (limb.runtime.grip == null && walker.upright()) {
          org.azkerash.createbioships.mixin.WalkerSnapInvoker.bioships$posed(
              part, position, turn, velocity, spin, dt);
        }
      }
    }
  }

  public List<SubLevelLegs.Pose> poses() {
    var result = new ArrayList<SubLevelLegs.Pose>();
    var origin = Vec3.atLowerCornerOf(walker.getBlockPos());
    for (var l : limbs.values()) {
      if (!loaded(l)) {
        continue;
      }
      var points = SocketJointRig.points(l.runtime.built, origin);
      var actual = new ArrayList<Vec3>();
      for (int i = 0; i < 3; i++) {
        var part = l.bodies.get(i);
        var a = anchor(part);
        actual.add(
            body.logicalPose().transformPositionInverse(part.logicalPose().transformPosition(a)));
        actual.add(
            body.logicalPose()
                .transformPositionInverse(
                    part.logicalPose().transformPosition(a.add(0, l.lengths[i], 0))));
      }
      if (prototypeActive) {
        // Debug joints must describe the active controller, not the dormant native plan.
        points = List.of(actual.get(0), actual.get(1), actual.get(3), actual.get(5));
      }
      result.add(new SubLevelLegs.Pose(l.socket, points, List.copyOf(actual)));
    }
    return List.copyOf(result);
  }

  public List<ServerSubLevel> segments() {
    return limbs.values().stream()
        .flatMap(l -> l.bodies.stream())
        .filter(b -> !b.isRemoved())
        .toList();
  }

  public String collisionDetails(net.minecraft.world.phys.AABB box) {
    var out = new StringBuilder();
    for (var pose : poses()) {
      if (CompactLegCollisions.overlaps(body, java.util.List.of(pose), box, .08)) {
        var leg = limbs.get(pose.cell()).runtime;
        out.append("cell=")
            .append(pose.cell())
            .append(" state=")
            .append(leg.state)
            .append(" wanted=")
            .append(leg.footWorld(body.getLevel().getGameTime()).add(0, .04, 0))
            .append(" actual=")
            .append(leg.actualFoot)
            .append(" from=")
            .append(leg.swingFrom)
            .append(" to=")
            .append(leg.swingTo)
            .append(" endpoints=")
            .append(
                pose.collisionEndpoints().stream()
                    .map(p -> body.logicalPose().transformPosition(p))
                    .toList());
      }
    }
    return out.toString();
  }

  public Double stepHeight() {
    return SocketJointRig.stepHeight(limbs.values().stream().map(l -> l.runtime).toList());
  }

  public int contacts() {
    return physicalContacts;
  }

  public net.minecraft.world.phys.AABB bounds() {
    return CompactLegCollisions.bounds(body, poses());
  }

  public boolean overlaps(net.minecraft.world.phys.AABB box, double tolerance) {
    return CompactLegCollisions.overlaps(body, poses(), box, tolerance);
  }

  private void releasePins(Limb l) {
    for (var pin : l.pins) {
      if (pin.isValid()) {
        pin.remove();
      }
    }
    l.pins.clear();
  }

  private void destroy(Limb l) {
    releasePins(l);
    for (var filter : l.runtime.built.contactFilters) {
      if (filter.isValid()) {
        filter.remove();
      }
    }
    l.runtime.built.contactFilters.clear();
    var container = SubLevelContainer.getContainer(body.getLevel());
    for (var id : l.runtime.built.segments) {
      var entry = container.getSubLevel(id);
      if (entry instanceof ServerSubLevel part && !part.isRemoved()) {
        SubLevelCreatureParts.detach(part);
        SubLevelContainer.getContainer(body.getLevel())
            .removeSubLevel(part, SubLevelRemovalReason.REMOVED);
      }
    }
  }

  public void close() {
    for (var l : limbs.values()) {
      releasePins(l);
    }
    BuiltLegDriver.releaseAll(walker);
  }

  public void removed() {
    for (var l : limbs.values()) {
      destroy(l);
    }
    limbs.clear();
  }

  public void save() {
    if (body.isRemoved()) {
      return;
    }
    if (!retiredSockets.isEmpty()) {
      discardRemoved();
    }
    var data = new CompoundTag();
    var list = new ListTag();
    for (var l : limbs.values()) {
      var t = new CompoundTag();
      t.putLong("Socket", l.socket.asLong());
      t.putInt("Height", l.height);
      t.putBoolean("Arachnid", l.arachnid);
      t.putInt("AnatomyVersion", l.anatomyVersion);
      t.putInt("FanAngle", l.fanAngle);
      t.putInt("RestAngle", l.restAngle);
      t.putDouble("Extension", l.extension);
      t.putString("Face", l.face.getName());
      t.put("Leg", l.runtime.built.save());
      t.put("Runtime", SocketJointRig.save(l.runtime));
      list.add(t);
    }
    data.put("Legs", list);
    var root = body.getUserDataTag().copy();
    root.put(DATA, data);
    body.setUserDataTag(root);
  }

  private void restore() {
    var data = body.getUserDataTag().getCompound(DATA);
    var container = SubLevelContainer.getContainer(body.getLevel());
    for (var raw : data.getList("Legs", 10)) {
      var t = (CompoundTag) raw;
      var built = BuiltLeg.load(t.getCompound("Leg"));
      if (built == null || new LinkedHashSet<>(built.segments).size() != 3) {
        continue;
      }
      var l = new Limb();
      l.socket = BlockPos.of(t.getLong("Socket"));
      l.arachnid = t.getBoolean("Arachnid");
      l.anatomyVersion = t.getInt("AnatomyVersion");
      l.height =
          Math.clamp(
              t.getInt("Height"), CompactLegGeometry.MIN_HEIGHT, CompactLegGeometry.MAX_HEIGHT);
      l.extension =
          Math.clamp(
              t.getDouble("Extension"),
              CompactLegGeometry.MIN_HEIGHT,
              CompactLegGeometry.MAX_HEIGHT);
      l.restAngle = t.getInt("RestAngle");
      l.face = Direction.byName(t.getString("Face"));
      if (l.face == null || !l.face.getAxis().isHorizontal()) {
        continue;
      }
      l.fanAngle = t.getInt("FanAngle");
      for (var id : new LinkedHashSet<>(built.segments)) {
        var part = container.getSubLevel(id);
        if (part instanceof ServerSubLevel s && !s.isRemoved()) {
          l.bodies.add(s);
        }
      }
      var points = restPoints(l.socket, l.face, l.extension, l.restAngle, l.fanAngle);
      for (int i = 0; i < 3; i++) {
        var d = points.get(i + 1).subtract(points.get(i));
        l.lengths[i] = d.length();
        l.rest.add(new Quaterniond().rotationTo(new Vector3d(0, 1, 0), v(d).normalize()));
      }
      l.runtime =
          new LegRuntime(
              built,
              Math.atan2(-l.face.getStepZ(), l.face.getStepX()),
              limbs.size(),
              l.face == Direction.WEST || l.face == Direction.NORTH ? -1 : 1,
              false);
      if (t.contains("Runtime")) {
        var runtime = SocketJointRig.loadCompact(body, l.socket, t.getCompound("Runtime"));
        if (runtime != null) {
          l.runtime = runtime;
        }
      }
      if (!t.contains("Runtime")) {
        l.runtime = SocketJointRig.compact(body, l.socket, l.runtime);
      }
      if (loaded(l)) {
        ensurePins(l);
      }
      limbs.put(l.socket, l);
    }
    refreshLegs();
  }
}
