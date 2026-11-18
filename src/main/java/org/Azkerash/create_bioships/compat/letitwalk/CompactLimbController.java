package org.Azkerash.create_bioships.compat.letitwalk;

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
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.legs.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.core.legs.SubLevelLegs;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** One native Let It Walk gait/suspension context; compact sockets generate real limb sublevels. */
public final class CompactLimbController {
  public static final String DATA = "create_bioships:compact_limbs",
      PART = "create_bioships:compact_limb";

  /** Generated collision tissue is edited through the one player-facing socket block. */
  public static net.minecraft.world.item.context.UseOnContext socketContext(
      net.minecraft.world.item.context.UseOnContext context) {
    if (!(context.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
        || context.getPlayer() == null) return context;
    var part = CoreAssemblyRules.containing(level, context.getClickedPos());
    if (part == null
        || part.getUserDataTag() == null
        || !part.getUserDataTag().contains(PART)
        || !SubLevelCreatureParts.isPart(part)) return context;
    var socket = BlockPos.of(part.getUserDataTag().getCompound(PART).getLong("Socket"));
    var owner = SubLevelCreatureParts.root(part);
    if (owner == part || !level.getBlockState(socket).is(ComponentRegistry.CRAB_LEG.get()))
      return context;
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

  private static final class Context extends WalkerCoreBlockEntity {
    Context(ServerSubLevel body) {
      super(
          body.getPlot().getCenterBlock(),
          ModContent.WALKER_CORE
              .get()
              .defaultBlockState()
              .setValue(WalkerCoreBlock.FACING, Direction.NORTH));
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
      return 32;
    }

    @Override
    public void setLoad(double v) {
      load = v;
    }
  }

  private static final class Limb {
    BlockPos socket;
    int height;
    double extension = 1;
    Direction face;
    LegRuntime runtime;
    final List<ServerSubLevel> bodies = new ArrayList<>();
    final List<GenericConstraintHandle> pins = new ArrayList<>();
    final List<Quaterniond> rest = new ArrayList<>();
    double[] lengths = new double[3];
    boolean contact;
  }

  private final ServerSubLevel body;
  private final Context walker;
  private final Map<BlockPos, Limb> limbs = new LinkedHashMap<>();
  private boolean dirty = true, hullContact;
  private int physicalContacts;

  public CompactLimbController(ServerSubLevel body) {
    this.body = body;
    walker = new Context(body);
    restore();
  }

  public void changed() {
    dirty = true;
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
    double scale = (height + .5) / 1.45;
    var side = Vec3.atLowerCornerOf(face.getNormal());
    var start = Vec3.atCenterOf(socket).subtract(side.scale(.25));
    return List.of(
        start,
        start.add(side.scale(.65 * scale)).add(0, .25 * scale, 0),
        start.add(side.scale(1.25 * scale)).add(0, -.45 * scale, 0),
        start.add(side.scale(1.45 * scale)).add(0, -1.45 * scale / .85, 0));
  }

  private void fill(ServerSubLevel part, int bone, int height, double length) {
    var base = part.getPlot().getCenterBlock();
    for (int y = 0; y < Math.ceil(length); y++)
      body.getLevel()
          .setBlock(
              base.above(y),
              ComponentRegistry.COMPACT_LIMB
                  .get()
                  .defaultBlockState()
                  .setValue(CompactLimbBlock.BONE, bone)
                  .setValue(CompactLimbBlock.HEIGHT, height)
                  .setValue(
                      CompactLimbBlock.FILL,
                      Math.clamp((int) Math.round(Math.min(1, length - y) * 64), 1, 64)),
              3);
  }

  private Limb create(BlockPos socket, int height, Direction face) {
    var limb = new Limb();
    limb.socket = socket.immutable();
    limb.height = height;
    limb.face = face;
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
      fill(part, i, height, limb.lengths[i]);
      placeSegment(part, world, new Quaterniond(body.logicalPose().orientation()).mul(q));
      limb.bodies.add(part);
    }
    configure(limb, points, null);
    for (var part : limb.bodies) SubLevelCreatureParts.attach(body, part);
    return limb;
  }

  /** Geometry edits preserve the planted distal end; routine gait never teleports a body. */
  private void placeSegment(ServerSubLevel part, Vec3 start, Quaterniond orientation) {
    var com = part.getMassTracker().getCenterOfMass();
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
    var oldRuntime = limb.runtime.save();
    var angles = limb.runtime.built.angles.clone();
    var oldLengths = limb.lengths.clone();
    var footPart = limb.bodies.getLast();
    var foot =
        footPart.logicalPose().transformPosition(anchor(footPart).add(0, limb.lengths[2], 0));
    releasePins(limb);
    for (var filter : limb.runtime.built.contactFilters) if (filter.isValid()) filter.remove();
    limb.runtime.built.contactFilters.clear();
    limb.extension = extension;
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
    var posed = limb.runtime.built.chain.forward(limb.runtime.built.angles, null);
    var turn = new Quaterniond();
    for (int i = 0; i < 3; i++) {
      var part = limb.bodies.get(i);
      var axis = limb.runtime.built.restAxes[i];
      turn.mul(
          new Quaterniond()
              .fromAxisAngleRad(axis[0], axis[1], axis[2], limb.runtime.built.angles[i]));
      var q = new Quaterniond(body.logicalPose().orientation()).mul(turn).mul(limb.rest.get(i));
      fill(part, i, limb.height, limb.lengths[i]);
      for (int y = (int) Math.ceil(limb.lengths[i]); y < (int) Math.ceil(oldLengths[i]); y++)
        body.getLevel().removeBlock(part.getPlot().getCenterBlock().above(y), false);
      placeSegment(part, body.logicalPose().transformPosition(origin.add(vec(posed[i]))), q);
    }
    oldRuntime.put("built", limb.runtime.built.save());
    var restored = LegRuntime.load(oldRuntime);
    if (restored != null) limb.runtime = restored;
  }

  private void configure(Limb limb, List<Vec3> points, BuiltLeg saved) {
    var origin = Vec3.atLowerCornerOf(walker.getBlockPos());
    var tangent = Vec3.atLowerCornerOf(limb.face.getNormal()).cross(new Vec3(0, 1, 0));
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
    for (int i = 0; i < 3; i++)
      built.segmentOffsets[i] = points.get(i).subtract(origin).subtract(anchor(limb.bodies.get(i)));
    double yaw = Math.atan2(-limb.face.getStepZ(), limb.face.getStepX());
    limb.runtime =
        new LegRuntime(
            built,
            yaw,
            limbs.size(),
            limb.face == Direction.WEST || limb.face == Direction.NORTH ? -1 : 1,
            false);
    ensurePins(limb);
  }

  /**
   * Keep saved identities while Sable streams their plots back in; never manufacture replacements.
   */
  private boolean loaded(Limb limb) {
    var container = SubLevelContainer.getContainer(body.getLevel());
    var found = new ArrayList<ServerSubLevel>();
    for (var id : limb.runtime.built.segments) {
      var entry = container.getSubLevel(id);
      if (!(entry instanceof ServerSubLevel part) || part.isRemoved()) return false;
      found.add(part);
    }
    if (found.size() != 3) return false;
    limb.bodies.clear();
    limb.bodies.addAll(found);
    return true;
  }

  private void ensurePins(Limb limb) {
    if (!loaded(limb)) {
      releasePins(limb);
      return;
    }
    if (limb.pins.size() == 3 && limb.pins.stream().allMatch(GenericConstraintHandle::isValid))
      return;
    releasePins(limb);
    var pipe = SubLevelContainer.getContainer(body.getLevel()).physicsSystem().getPipeline();
    for (int i = 0; i < 3; i++) {
      var joint = limb.runtime.built.joints.get(i);
      var parent = i == 0 ? body : limb.bodies.get(i - 1);
      var child = limb.bodies.get(i);
      // Express the native chain's hinge axis in each segment's own rest frame.
      var axisFrame =
          new Quaterniond().rotationTo(new Vector3d(1, 0, 0), v(joint.axis).normalize());
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
                      ConstraintJointAxis.ANGULAR_Y,
                      ConstraintJointAxis.ANGULAR_Z)));
      if (pin != null) {
        pin.setContactsEnabled(false);
        pin.setLimit(ConstraintJointAxis.ANGULAR_X, i == 0 ? -1.22 : -2.8, i == 0 ? 1.22 : 2.8);
        limb.pins.add(pin);
      }
    }
  }

  public void discardRemoved() {
    boolean removed = false;
    var it = limbs.values().iterator();
    while (it.hasNext()) {
      var l = it.next();
      if (!body.getLevel().getBlockState(l.socket).is(ComponentRegistry.CRAB_LEG.get())) {
        destroy(l);
        it.remove();
        removed = true;
      }
    }
    if (removed) {
      refreshLegs();
      save();
    }
  }

  private void rebuild() {
    if (!dirty) return;
    dirty = false;
    discardRemoved();
    for (var socket : SubLevelHealth.blocks(body)) {
      var state = body.getLevel().getBlockState(socket);
      if (!state.is(ComponentRegistry.CRAB_LEG.get())) continue;
      int height = state.getValue(CrabLegBlock.HEIGHT);
      var face =
          state.getValue(
              net.minecraft.world.level.block.state.properties.BlockStateProperties
                  .HORIZONTAL_FACING);
      var old = limbs.get(socket);
      if (old != null && (old.face != face)) {
        destroy(old);
        limbs.remove(socket);
        old = null;
      }
      if (old == null) limbs.put(socket.immutable(), create(socket, height, face));
      else old.height = height;
    }
    refreshLegs();
    save();
  }

  private void refreshLegs() {
    walker.legs.clear();
    for (var l : limbs.values()) walker.legs.add(l.runtime);
    LegRuntime.countLegs(walker.legs);
    walker.totalMass =
        body.getMassTracker().getMass()
            + limbs.values().stream()
                .flatMap(l -> l.bodies.stream())
                .filter(b -> !b.isRemoved())
                .mapToDouble(b -> b.getMassTracker().getMass())
                .sum();
  }

  private boolean commanded() {
    var r = BrainMotorControls.current(body);
    return !SubLevelHealth.status(body).dead()
        && r.expires() > body.getLevel().getGameTime()
        && r.mode() == org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND;
  }

  /** Feed the native pose controls the stance implied by unequal socket lengths. */
  private void stancePreference() {
    double sx = 0, sz = 0, sh = 0;
    int n = limbs.size();
    if (n < 3) return;
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

  public void update() {
    rebuild();
    if (limbs.isEmpty()) return;
    if (body.getLevel().getGameTime() % 4 == 0) {
      boolean resized = false;
      for (var limb : limbs.values())
        if (loaded(limb) && Math.abs(limb.extension - limb.height) > .001) {
          resize(limb, limb.extension + Math.clamp(limb.height - limb.extension, -.125, .125));
          resized = true;
        }
      if (resized) {
        refreshLegs();
        save();
      }
    }
    BuiltLegDriver.ensureContactFilters(walker, body.getLevel());
    BuiltLegDriver.updateActualFeet(walker, body.getLevel());
    var family = new HashSet<UUID>();
    for (var p : SubLevelCreatureParts.members(body)) family.add(p.getUniqueId());
    TerrainProbe.withIgnored(
        p -> family.contains(p.getUniqueId()),
        () -> {
          physicalContacts = 0;
          for (var l : limbs.values()) {
            l.contact = loaded(l) && CompactLegCollisions.grounded(body, l.bodies, l.lengths);
            if (l.contact) physicalContacts++;
          }
          hullContact = LimbGroundContacts.touching(body, body);
          if (commanded()) {
            var r = BrainMotorControls.current(body);
            var local = body.logicalPose().orientation().transformInverse(v(r.velocity()));
            int keys = 1 << WalkerBrain.GEAR_SHIFT | WalkerBrain.KEY_CRAB;
            if (local.z < -.05) keys |= WalkerBrain.KEY_FORWARD;
            if (local.z > .05) keys |= WalkerBrain.KEY_BACK;
            if (local.x < -.05) keys |= WalkerBrain.KEY_LEFT;
            if (local.x > .05) keys |= WalkerBrain.KEY_RIGHT;
            walker.input = keys;
            walker.lastInputTime = body.getLevel().getGameTime();
            walker.receiveTurn(r.yawRate() * .05);
            stancePreference();
            WalkerBrain.tick(walker, body);
            walker.targetVelocity.set(r.velocity().x, 0, r.velocity().z);
            BuiltLegDriver.solve(walker, body, body.getLevel().getGameTime());
          }
        });
    // Joint angles and physical poses are serialized by Let It Walk/Sable; no pose teleport driver.
    if (body.getLevel().getGameTime() % 20 == 0) save();
  }

  public void physics(double dt) {
    discardRemoved();
    boolean drive = commanded() && (physicalContacts > 0 || hullContact);
    for (var l : limbs.values()) {
      if (!loaded(l)) {
        releasePins(l);
        continue;
      }
      ensurePins(l);
      var tuning = LimbTuning.get(body.getLevel(), l.socket);
      for (int i = 0; i < l.pins.size(); i++)
        l.pins
            .get(i)
            .setMotor(
                ConstraintJointAxis.ANGULAR_X,
                l.runtime.built.angles[i] + (i == 0 ? Math.toRadians(tuning.restAngle()) : 0),
                drive ? 100 * tuning.stiffness() : 2,
                drive ? 25 * tuning.damping() : 4,
                true,
                drive ? Math.clamp(body.getMassTracker().getMass() * 2, 8, 80) : 1);
    }
    if (drive && physicalContacts > 0 && walker.stanceCount > 0) {
      var h = RigidBodyHandle.of(body);
      if (h != null && h.isValid()) {
        int planned = walker.stanceCount;
        walker.stanceCount = Math.min(planned, physicalContacts);
        WalkerPhysics.substep(walker, body, h, dt);
        walker.stanceCount = planned;
      }
    }
  }

  public List<SubLevelLegs.Pose> poses() {
    var result = new ArrayList<SubLevelLegs.Pose>();
    var origin = Vec3.atLowerCornerOf(walker.getBlockPos());
    for (var l : limbs.values()) {
      if (!loaded(l)) continue;
      var points =
          Arrays.stream(l.runtime.built.chain.forward(l.runtime.built.angles, null))
              .map(p -> origin.add(vec(p)))
              .toList();
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
    for (var pin : l.pins) if (pin.isValid()) pin.remove();
    l.pins.clear();
  }

  private void destroy(Limb l) {
    releasePins(l);
    for (var filter : l.runtime.built.contactFilters) if (filter.isValid()) filter.remove();
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
    for (var l : limbs.values()) releasePins(l);
    BuiltLegDriver.releaseAll(walker);
  }

  public void removed() {
    for (var l : limbs.values()) destroy(l);
    limbs.clear();
  }

  public void save() {
    if (body.isRemoved()) return;
    var data = new CompoundTag();
    var list = new ListTag();
    for (var l : limbs.values()) {
      var t = new CompoundTag();
      t.putLong("Socket", l.socket.asLong());
      t.putInt("Height", l.height);
      t.putDouble("Extension", l.extension);
      t.putString("Face", l.face.getName());
      t.put("Leg", l.runtime.built.save());
      t.put("Runtime", l.runtime.save());
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
      if (built == null || built.segments.size() != 3) continue;
      var l = new Limb();
      l.socket = BlockPos.of(t.getLong("Socket"));
      l.height = Math.clamp(t.getInt("Height"), 1, 3);
      l.extension = Math.clamp(t.getDouble("Extension"), 1, 3);
      l.face = Direction.byName(t.getString("Face"));
      if (l.face == null || !l.face.getAxis().isHorizontal()) continue;
      for (var id : built.segments) {
        var part = container.getSubLevel(id);
        if (part instanceof ServerSubLevel s && !s.isRemoved()) l.bodies.add(s);
      }
      var points = restPoints(l.socket, l.face, l.extension);
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
        var runtime = LegRuntime.load(t.getCompound("Runtime"));
        if (runtime != null) l.runtime = runtime;
      }
      if (loaded(l)) ensurePins(l);
      limbs.put(l.socket, l);
    }
    refreshLegs();
  }
}
