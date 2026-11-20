package org.Azkerash.create_bioships.compat.letitwalk;

import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.components.logic.legs.SocketJointBlock;
import org.Azkerash.create_bioships.mixin.JointChainLimitsAccess;

/**
 * Two native rotational axes at one socket, with one physical child segment. The native driver
 * visits both axes and its final pose includes both rotations; no extra rigid bodies are invented.
 * Collision filters and assembly operate on the distinct original physical segments.
 */
public final class SocketJointRig {
  private static final String BASE = "BioshipSocketBase";
  private static final Vec3[] CLEARANCE = {
    new Vec3(.25, 0, 0), new Vec3(-.25, 0, 0), new Vec3(0, 0, .25), new Vec3(0, 0, -.25)
  };
  private static final Map<BuiltLeg, SocketJointRig> RIGS = new WeakHashMap<>();
  private final BuiltLeg base;
  private final int[] source, twist, bend;
  private final String[] keys;
  private final ServerSubLevel owner;
  private long limitsVersion = Long.MIN_VALUE;
  private double rise = Double.NaN, toeMargin = Double.NaN;
  private long appliedRevision;
  private boolean initialized;
  private final SocketJointSettings[] configured;

  private SocketJointRig(
      ServerSubLevel owner, BuiltLeg base, int[] source, int[] twist, int[] bend, String[] keys) {
    this.owner = owner;
    this.base = base;
    this.source = source;
    this.twist = twist;
    this.bend = bend;
    this.keys = keys;
    this.configured = new SocketJointSettings[keys.length];
  }

  public static LegRuntime compact(ServerSubLevel owner, BlockPos socket, LegRuntime runtime) {
    return decorate(owner, runtime, i -> SocketJointSettings.compact(socket, i));
  }

  public static LegRuntime modular(ServerSubLevel owner, LegRuntime runtime) {
    var physicalDefinition = base(runtime.built);
    return decorate(
        owner,
        runtime,
        i -> {
          var joint = physicalDefinition.joints.get(i);
          var part =
              dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(owner.getLevel())
                  .getSubLevel(joint.parent);
          if (!(part instanceof ServerSubLevel physical)) {
            return null;
          }
          var cell = BlockPos.containing(joint.parentPlot);
          return owner.getLevel().getBlockState(cell).getBlock() instanceof SocketJointBlock
              ? SocketJointSettings.modular(owner, physical, cell)
              : null;
        });
  }

  public static LegRuntime decorate(
      ServerSubLevel owner, LegRuntime runtime, IntFunction<String> settings) {
    var previous = RIGS.get(runtime.built);
    var original = base(runtime.built);
    int n = original.joints.size();
    var joints = new ArrayList<BuiltLeg.Joint>();
    var ids = new ArrayList<UUID>();
    var points = new ArrayList<double[]>();
    var axes = new ArrayList<double[]>();
    var offsets = new ArrayList<Vec3>();
    var restOffsets = new ArrayList<Double>();
    var sources = new ArrayList<Integer>();
    int[] twist = new int[n], bend = new int[n];
    Arrays.fill(twist, -1);
    Arrays.fill(bend, -1);
    String[] keys = new String[n];
    boolean added = false;
    for (int i = 0; i < n; i++) {
      var joint = original.joints.get(i);
      keys[i] = settings.apply(i);
      if (keys[i] != null) {
        added = true;
        Vec3 along = joint.swivel ? joint.axis : Vec3.ZERO;
        if (!joint.swivel && i > 0) {
          along = vector(original.restPivots[i]).subtract(vector(original.restPivots[i - 1]));
        }
        if (along.lengthSqr() < 1e-8) {
          along = new Vec3(0, 1, 0);
        }
        along = along.normalize();
        Vec3 hinge = joint.swivel ? original.planeNormal : joint.axis;
        if (hinge.lengthSqr() < 1e-8 || Math.abs(hinge.normalize().dot(along)) > .99) {
          hinge = along.cross(new Vec3(0, 0, 1));
          if (hinge.lengthSqr() < 1e-8) {
            hinge = along.cross(new Vec3(1, 0, 0));
          }
        }
        hinge = hinge.subtract(along.scale(hinge.dot(along))).normalize();
        twist[i] = joints.size();
        append(original, i, along, true, joints, ids, points, axes, offsets, restOffsets, sources);
        bend[i] = joints.size();
        append(
            original,
            i,
            hinge.normalize(),
            false,
            joints,
            ids,
            points,
            axes,
            offsets,
            restOffsets,
            sources);
      } else {
        bend[i] = joints.size();
        append(
            original,
            i,
            joint.axis,
            joint.swivel,
            joints,
            ids,
            points,
            axes,
            offsets,
            restOffsets,
            sources);
      }
    }
    if (!added) {
      return runtime;
    }
    var expanded =
        new BuiltLeg(
            joints,
            ids,
            points.toArray(double[][]::new),
            axes.toArray(double[][]::new),
            original.restFoot.clone(),
            true,
            original.planeNormal,
            restOffsets.stream().mapToDouble(Double::doubleValue).toArray());
    expanded.footPlot = original.footPlot;
    expanded.footKind = original.footKind;
    expanded.footKindKnown = original.footKindKnown;
    expanded.segmentOffsets = offsets.toArray(Vec3[]::new);
    var rig =
        new SocketJointRig(
            owner,
            original,
            sources.stream().mapToInt(Integer::intValue).toArray(),
            twist,
            bend,
            keys);
    RIGS.put(expanded, rig);
    var tag = runtime.save();
    tag.put("built", expanded.save());
    var result = LegRuntime.load(tag);
    if (result == null) {
      throw new IllegalStateException("Native socket runtime could not load");
    }
    RIGS.remove(expanded);
    RIGS.put(result.built, rig);
    rig.preserveNativePosture(result.built);
    if (previous == null) {
      Arrays.fill(result.built.angles, 0);
      for (int i = 0; i < rig.keys.length; i++) {
        result.built.angles[rig.primary(i)] = runtime.built.angles[i];
      }
    }
    if (previous != null
        && result.built.angles.length == runtime.built.angles.length
        && Arrays.equals(previous.keys, rig.keys)
        && Arrays.equals(previous.source, rig.source)) {
      System.arraycopy(
          runtime.built.angles, 0, result.built.angles, 0, runtime.built.angles.length);
    }
    apply(result.built);
    return result;
  }

  private static void append(
      BuiltLeg original,
      int i,
      Vec3 axis,
      boolean swivel,
      List<BuiltLeg.Joint> joints,
      List<UUID> ids,
      List<double[]> points,
      List<double[]> axes,
      List<Vec3> offsets,
      List<Double> restOffsets,
      List<Integer> sources) {
    var j = original.joints.get(i);
    joints.add(new BuiltLeg.Joint(j.parent, j.child, j.parentPlot, j.childPlot, axis, swivel));
    ids.add(original.segments.get(i));
    points.add(original.restPivots[i].clone());
    axes.add(new double[] {axis.x, axis.y, axis.z});
    offsets.add(original.segmentOffsets[i]);
    restOffsets.add(swivel == j.swivel ? original.restOffsets[i] : 0d);
    sources.add(i);
  }

  private static Vec3 vector(double[] a) {
    return new Vec3(a[0], a[1], a[2]);
  }

  private int primary(int joint) {
    return base.joints.get(joint).swivel && twist[joint] >= 0 ? twist[joint] : bend[joint];
  }

  private void preserveNativePosture(BuiltLeg built) {
    var from = (JointChainLimitsAccess) (Object) base.chain;
    var to = (JointChainLimitsAccess) (Object) built.chain;
    Arrays.fill(to.bioships$preferred(), 0);
    for (var seed : to.bioships$seeds()) {
      Arrays.fill(seed, 0);
    }
    for (int i = 0; i < keys.length; i++) {
      int axis = primary(i);
      to.bioships$preferred()[axis] = from.bioships$preferred()[i];
      for (int seed = 0;
          seed < Math.min(from.bioships$seeds().length, to.bioships$seeds().length);
          seed++) {
        to.bioships$seeds()[seed][axis] = from.bioships$seeds()[seed][i];
      }
    }
  }

  public static boolean decorated(BuiltLeg built) {
    return RIGS.containsKey(built);
  }

  /**
   * Upstream omits root/toe edges from its collision samples. Our compact sections need all edges.
   */
  public static final class CollisionPoints extends ArrayList<Vec3> {
    final double toeMargin;

    CollisionPoints(java.util.List<Vec3> points, double toeMargin) {
      super(points);
      this.toeMargin = toeMargin;
    }
  }

  private double toeMargin() {
    if (!Double.isNaN(toeMargin)) {
      return toeMargin;
    }
    boolean compact = Arrays.stream(keys).anyMatch(k -> k != null && k.startsWith("c:"));
    if (!compact) {
      return toeMargin = .7;
    }
    int height = 1;
    var container =
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(owner.getLevel());
    var part = container == null ? null : container.getSubLevel(base.segments.getLast());
    if (part != null) {
      var state = owner.getLevel().getBlockState(part.getPlot().getCenterBlock());
      if (state.hasProperty(
          org.Azkerash.create_bioships.components.logic.legs.CompactLimbBlock.HEIGHT)) {
        height =
            state.getValue(
                org.Azkerash.create_bioships.components.logic.legs.CompactLimbBlock.HEIGHT);
      }
    }
    return toeMargin = CompactLegCollisions.width(2, height) / 2 + .08;
  }

  public static java.util.List<Vec3> collisionSamples(BuiltLeg built, java.util.List<Vec3> points) {
    var rig = RIGS.get(built);
    return rig == null ? points : new CollisionPoints(points, rig.toeMargin());
  }

  /** Full native solid sampling, including finite toe width beside a ledge. */
  public static java.util.List<Vec3> completeSamples(CollisionPoints points) {
    if (points.isEmpty()) {
      return java.util.List.of();
    }
    var complete = new ArrayList<Vec3>();
    complete.add(points.getFirst());
    complete.add(points.getFirst());
    var foot = points.getLast().add(0, .06, 0);
    for (int i = 1; i < points.size(); i++) {
      var a = points.get(i - 1);
      var b = i == points.size() - 1 ? foot : points.get(i);
      double length = a.distanceTo(b);
      if (length < 1e-6) {
        continue;
      }
      int count = Math.max(1, (int) Math.ceil(length / .125));
      for (int step = 1; step <= count; step++) {
        var point = a.lerp(b, step / (double) count);
        complete.add(point);
        // Check a small clearance envelope before a planted section reaches a wall.
        // Native gait checks are staggered across ticks, so a zero-width centreline is too late.
        for (var offset : CLEARANCE) {
          complete.add(point.add(offset));
          complete.add(point);
        }
      }
    }
    double r = points.toeMargin / .8;
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        if (x != 0 || z != 0) {
          complete.add(foot.add(x * r, 0, z * r));
          complete.add(foot);
        }
      }
    }
    var tail = foot.add(0, 1, 0);
    complete.add(tail);
    complete.add(tail);
    complete.add(tail);
    return complete;
  }

  /** Let the native swing interpolation clear a change in floor height at finite foot width. */
  public static void prepareSwing(LegRuntime leg, long time) {
    if (!decorated(leg.built)
        || leg.state != LegRuntime.State.SWING
        || leg.swingFrom == null
        || leg.swingTo == null) {
      return;
    }
    double rise = Math.abs(leg.swingTo.y - leg.swingFrom.y);
    if (rise < .2) {
      return;
    }
    double height = Math.max(leg.swingHeight, rise + .4);
    double horizontal =
        Math.hypot(leg.swingTo.x - leg.swingFrom.x, leg.swingTo.z - leg.swingFrom.z);
    int ticks =
        Math.max(
            leg.swingTicks,
            (int)
                Math.ceil((height * 2 + horizontal) / Math.max(3, leg.gait().speed() * 2.5) * 20));
    if (height > leg.swingHeight + 1e-6 || ticks > leg.swingTicks) {
      double progress =
          Math.clamp((time - leg.swingStart) / (double) Math.max(1, leg.swingTicks), 0, 1);
      leg.swingHeight = height;
      leg.swingTicks = ticks;
      leg.swingStart = time - Math.round(progress * ticks);
    }
  }

  /** Raise the native point-foot target by the rotated physical sole's lowest corner. */
  public static double soleClearance(BuiltLeg built) {
    var rig = RIGS.get(built);
    if (rig == null || Arrays.stream(rig.keys).anyMatch(k -> k != null && k.startsWith("c:"))) {
      return 0;
    }
    var container =
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(rig.owner.getLevel());
    if (container == null || built.footPlot == null) {
      return 0;
    }
    var entry = container.getSubLevel(rig.base.segments.getLast());
    if (!(entry instanceof ServerSubLevel part)) {
      return 0;
    }
    var rotation = new org.joml.Quaterniond(rig.owner.logicalPose().orientation());
    for (int i = 0; i < built.angles.length; i++) {
      var axis = built.restAxes[i];
      rotation.mul(
          new org.joml.Quaterniond().fromAxisAngleRad(axis[0], axis[1], axis[2], built.angles[i]));
    }
    var down = rotation.transformInverse(new org.joml.Vector3d(0, 1, 0));
    double lowest = 0;
    for (var box : LimbGroundContacts.shapes(part)) {
      double x = (down.x < 0 ? box.maxX : box.minX) - built.footPlot.x;
      double y = (down.y < 0 ? box.maxY : box.minY) - built.footPlot.y;
      double z = (down.z < 0 ? box.maxZ : box.minZ) - built.footPlot.z;
      lowest = Math.min(lowest, x * down.x + y * down.y + z * down.z);
    }
    return -lowest;
  }

  public static SocketJointSettings limits(BuiltLeg built, int joint) {
    var rig = RIGS.get(built);
    if (rig == null || rig.keys[joint] == null) {
      return null;
    }
    apply(built);
    return rig.configured[joint];
  }

  public static org.joml.Quaterniond frame(BuiltLeg built, int joint) {
    var rig = RIGS.get(built);
    if (rig == null || rig.keys[joint] == null) {
      return null;
    }
    var x = vector(built.restAxes[rig.bend[joint]]);
    var y = vector(built.restAxes[rig.twist[joint]]);
    var z = x.cross(y).normalize();
    var matrix =
        new org.joml.Matrix3d()
            .setColumn(0, new org.joml.Vector3d(x.x, x.y, x.z))
            .setColumn(1, new org.joml.Vector3d(y.x, y.y, y.z))
            .setColumn(2, new org.joml.Vector3d(z.x, z.y, z.z));
    return new org.joml.Quaterniond().setFromNormalized(matrix);
  }

  public static int endAxis(BuiltLeg built, int physicalJoint) {
    var rig = RIGS.get(built);
    return rig == null ? physicalJoint : rig.bend[physicalJoint];
  }

  public static BuiltLeg base(BuiltLeg built) {
    var rig = RIGS.get(built);
    return rig == null ? built : rig.base;
  }

  public static void apply(BuiltLeg built) {
    var rig = RIGS.get(built);
    if (rig == null) {
      return;
    }
    long revision =
        org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.motionRevision(rig.owner);
    if (rig.initialized && rig.appliedRevision == revision) {
      normalizeCyclic(built, rig);
      return;
    }
    var limits = (JointChainLimitsAccess) (Object) built.chain;
    long version = 1;
    for (int i = 0; i < rig.keys.length; i++) {
      if (rig.keys[i] == null) {
        continue;
      }
      var settings = SocketJointSettings.get(rig.owner, rig.keys[i]);
      rig.configured[i] = settings;
      version = version * 31 + settings.hashCode();
      set(
          limits,
          built,
          rig.twist[i],
          Math.toRadians(settings.swivel() == 360 ? 360 : settings.swivel() / 2d));
      set(limits, built, rig.bend[i], Math.toRadians(settings.bend()));
    }
    rig.appliedRevision = revision;
    rig.initialized = true;
    normalizeCyclic(built, rig);
    if (rig.limitsVersion != version) {
      rig.limitsVersion = version;
      rig.rise = Double.NaN;
    }
  }

  private static void normalizeCyclic(BuiltLeg built, SocketJointRig rig) {
    for (int i = 0; i < rig.configured.length; i++) {
      if (rig.configured[i] != null && rig.configured[i].swivel() == 360) {
        int axis = rig.twist[i];
        built.angles[axis] =
            built.restOffsets[axis]
                + Math.IEEEremainder(built.angles[axis] - built.restOffsets[axis], Math.PI * 2);
      }
    }
  }

  private static void set(JointChainLimitsAccess access, BuiltLeg built, int axis, double limit) {
    double centre = built.restOffsets[axis];
    access.bioships$minimum()[axis] = centre - limit;
    access.bioships$maximum()[axis] = centre + limit;
    built.angles[axis] = Math.clamp(built.angles[axis], centre - limit, centre + limit);
  }

  /** Cached, bounded native-IK samples; route search never solves IK per visited node. */
  public static Double stepHeight(java.util.Collection<LegRuntime> legs) {
    if (legs.isEmpty()) {
      return null;
    }
    var heights = new ArrayList<Double>();
    for (var leg : legs) {
      if (leg.arm) {
        continue;
      }
      var rig = RIGS.get(leg.built);
      if (rig == null) {
        heights.add(1d);
        continue;
      }
      apply(leg.built);
      if (Double.isNaN(rig.rise)) {
        rig.rise = 0;
        var foot = leg.built.standFoot;
        var hip = leg.built.restPivots[0];
        double dx = foot[0] - hip[0], dz = foot[2] - hip[2];
        double length = Math.hypot(dx, dz);
        if (length < 1e-5) {
          dx = 1;
          dz = 0;
          length = 1;
        }
        for (double height : new double[] {.25, .5, .75, 1}) {
          var target =
              new double[] {
                foot[0] + .25 * dx / length, foot[1] + height, foot[2] + .25 * dz / length
              };
          if (leg.built.chain.reachable(target, leg.built.chain.preferred(), .08, new int[] {1})) {
            rig.rise = height;
          }
        }
      }
      heights.add(rig.rise);
    }
    if (heights.isEmpty()) {
      return null;
    }
    heights.sort(java.util.Comparator.reverseOrder());
    return heights.get(Math.max(0, (int) Math.ceil(heights.size() / 2d) - 1));
  }

  public static java.util.List<net.minecraft.world.phys.Vec3> points(BuiltLeg built, Vec3 origin) {
    var all = built.chain.forward(built.angles, null);
    var rig = RIGS.get(built);
    if (rig == null) {
      return Arrays.stream(all).map(p -> origin.add(vector(p))).toList();
    }
    var points = new ArrayList<Vec3>();
    int last = -1;
    for (int i = 0; i < rig.source.length; i++) {
      if (rig.source[i] != last) {
        points.add(origin.add(vector(all[i])));
        last = rig.source[i];
      }
    }
    points.add(origin.add(vector(all[all.length - 1])));
    return List.copyOf(points);
  }

  public static CompoundTag geometry(CompoundTag state, LegRuntime runtime) {
    var tag = state.copy();
    tag.put("built", runtime.built.save());
    var rig = RIGS.get(runtime.built);
    if (rig != null) {
      tag.put(BASE, rig.base.save());
      writeKeys(tag, rig);
    } else {
      tag.remove(BASE);
      tag.remove("BioshipSocketKeys");
    }
    return tag;
  }

  public static CompoundTag save(LegRuntime runtime) {
    var tag = runtime.save();
    var rig = RIGS.get(runtime.built);
    if (rig != null) {
      tag.put(BASE, rig.base.save());
      writeKeys(tag, rig);
    }
    return tag;
  }

  public static LegRuntime loadCompact(ServerSubLevel owner, BlockPos socket, CompoundTag tag) {
    return load(owner, tag, r -> compact(owner, socket, r));
  }

  public static LegRuntime loadModular(ServerSubLevel owner, CompoundTag tag) {
    return load(owner, tag, r -> modular(owner, r));
  }

  private static LegRuntime load(
      ServerSubLevel owner, CompoundTag tag, Function<LegRuntime, LegRuntime> decorate) {
    var saved = LegRuntime.load(tag);
    if (saved == null) {
      return null;
    }
    if (!tag.contains(BASE)) {
      return decorate.apply(saved);
    }
    var clean = tag.copy();
    clean.put("built", tag.getCompound(BASE));
    var original = LegRuntime.load(clean);
    if (original == null) {
      return null;
    }
    var keys = tag.getList("BioshipSocketKeys", 8);
    var loaded =
        keys.size() == original.built.joints.size()
            ? decorate(owner, original, i -> keys.getString(i).isEmpty() ? null : keys.getString(i))
            : decorate.apply(original);
    if (loaded.built.angles.length == saved.built.angles.length) {
      System.arraycopy(saved.built.angles, 0, loaded.built.angles, 0, saved.built.angles.length);
    }
    var rig = RIGS.get(loaded.built);
    if (rig != null) {
      rig.initialized = false;
    }
    apply(loaded.built);
    return loaded;
  }

  private static void writeKeys(CompoundTag tag, SocketJointRig rig) {
    var keys = new ListTag();
    for (var key : rig.keys) {
      keys.add(StringTag.valueOf(key == null ? "" : key));
    }
    tag.put("BioshipSocketKeys", keys);
  }

  public static boolean ensureContactFilters(
      WalkerCoreBlockEntity walker, net.minecraft.server.level.ServerLevel level) {
    boolean ready = true;
    for (var leg : walker.legs) {
      if (leg.built.contactFilters.isEmpty()
          || leg.built.contactFilters.stream().anyMatch(h -> !h.isValid())) {
        ready = false;
        break;
      }
    }
    if (ready
        && (walker.legs.size() < 2
            || (!walker.crossFilters.isEmpty()
                && walker.crossFilters.stream().allMatch(h -> h.isValid())))) {
      return true;
    }
    return physical(walker, () -> BuiltLegDriver.ensureContactFilters(walker, level));
  }

  /** Scope native physical bookkeeping to one entry per actual segment, not one per DOF. */
  public static <T> T physical(WalkerCoreBlockEntity walker, Supplier<T> operation) {
    var current = new ArrayList<>(walker.legs);
    var originals = new ArrayList<LegRuntime>();
    for (var runtime : current) {
      var rig = RIGS.get(runtime.built);
      if (rig == null) {
        originals.add(runtime);
        continue;
      }
      var tag = runtime.save();
      tag.put("built", rig.base.save());
      var original = LegRuntime.load(tag);
      if (original == null) {
        throw new IllegalStateException("Missing native socket base");
      }
      rig.base.contactFilters.clear();
      rig.base.contactFilters.addAll(runtime.built.contactFilters);
      original.built.contactFilters.addAll(runtime.built.contactFilters);
      originals.add(original);
    }
    walker.legs.clear();
    walker.legs.addAll(originals);
    try {
      return operation.get();
    } finally {
      // A successful rescan replaces the original runtimes: retain those for the caller to
      // decorate.
      boolean replaced = !walker.legs.equals(originals);
      if (!replaced) {
        for (int i = 0; i < current.size(); i++) {
          current.get(i).built.contactFilters.clear();
          current.get(i).built.contactFilters.addAll(originals.get(i).built.contactFilters);
        }
        walker.legs.clear();
        walker.legs.addAll(current);
      }
    }
  }
}
