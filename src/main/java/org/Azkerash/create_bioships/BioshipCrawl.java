package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/** Small arms push against dry ground; this never supplies flight or consumes levitite. */
public final class BioshipCrawl {
  private Vec3 goal;
  private Vec3 lastStepPosition;
  private long gaitAt;
  private double gaitSpeed, gaitPhase;

  public boolean animating() {
    return walking && gaitSpeed > .08;
  }

  public double gaitSpeed() {
    return gaitSpeed;
  }

  public double gaitPhase() {
    return gaitPhase;
  }

  public long gaitAt() {
    return gaitAt;
  }

  private void measureGait(LivingCoreBlockEntity core) {
    var point =
        BioshipBlueprint.physical(core, core.structuralOrigin().subtract(core.getBlockPos()));
    long now = core.getLevel().getGameTime();
    if (lastStepPosition != null && now > gaitAt) {
      double distance = Math.sqrt(point.subtract(lastStepPosition).horizontalDistanceSqr());
      gaitSpeed = distance * 20 / (now - gaitAt);
      if (walking && distance < 1) gaitPhase += distance * (core.hatchling() ? 7 : 2);
    } else gaitSpeed = 0;
    lastStepPosition = point;
    gaitAt = now;
  }

  private boolean walking;
  private String mode = "idle";

  public String mode() {
    return mode;
  }

  public boolean running() {
    return mode.equals("flee") || mode.equals("defend") || mode.equals("forage") && goal != null;
  }

  private int legTier;
  private double measuredLoad;

  public double measuredLoad() {
    return measuredLoad;
  }

  public int legTier() {
    return legTier;
  }

  public int extraLift() {
    return 1 + legTier;
  }

  public int footingReach() {
    return 3 + legTier;
  }

  public double legThickness() {
    return 1 + legTier * .35;
  }

  public double loadCapacity() {
    return capacityFor(legTier);
  }

  static double capacityFor(int tier) {
    return 32 + 128d * tier * tier;
  }

  int hullTier(LivingCoreBlockEntity core) {
    double unit =
        dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
            core.getLevel(), core.getBlockPos(), Create_bioships.FLESH.get().defaultBlockState());
    double mass = 0;
    for (var part : core.plannedBody())
      mass +=
          dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
              core.getLevel(), core.structuralOrigin().offset(part.offset()), part.state());
    double load = Math.max(carriedLoad(core), mass / Math.max(.001, unit));
    for (int tier = 0; tier <= 3; tier++) if (load <= capacityFor(tier)) return tier;
    return 4;
  }

  public double carriedLoad(LivingCoreBlockEntity core) {
    double tissue =
        dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
            core.getLevel(), core.getBlockPos(), Create_bioships.FLESH.get().defaultBlockState());
    return core.ship() == null
        ? 0
        : core.ship().getMassTracker().getMass() / Math.max(.001, tissue);
  }

  private boolean canEnhance(LivingCoreBlockEntity core) {
    var stomach = DigestiveSystem.stomach(core);
    return legTier < 3
        && stomach != null
        && stomach.connected(core)
        && stomach.material("biomancy:flesh_bits") >= 8
        && stomach.material("biomancy:elastic_fibers") >= 4
        && stomach.material("biomancy:bone_fragments") >= 2;
  }

  private void payEnhancement(LivingCoreBlockEntity core) {
    var stomach = DigestiveSystem.stomach(core);
    stomach.spend("biomancy:flesh_bits", 8);
    stomach.spend("biomancy:elastic_fibers", 4);
    stomach.spend("biomancy:bone_fragments", 2);
    core.setChanged();
    core.getLevel()
        .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
  }

  boolean enhance(LivingCoreBlockEntity core) {
    if (!canEnhance(core)) return false;
    legTier++;
    payEnhancement(core);
    return true;
  }

  private boolean supportLoad(LivingCoreBlockEntity core) {
    double load = carriedLoad(core);
    measuredLoad = load;
    while (load > loadCapacity() && enhance(core)) {}
    return load <= loadCapacity();
  }

  private int cursor;
  private final GroundRoute route = new GroundRoute();
  private final Map<BlockPos, Long> unreachable = new HashMap<>();

  net.minecraft.nbt.CompoundTag navigationDebug(long now) {
    var tag = route.debug(now);
    tag.putString("Mode", mode);
    tag.putBoolean("Walking", walking);
    tag.putDouble("Speed", gaitSpeed);
    if (goal != null) tag.put("Goal", VisionDebug.vector(goal));
    return tag;
  }

  public java.util.List<Vec3> path() {
    return route.remaining();
  }

  boolean reachableGoal(BlockPos pos) {
    return unreachable.getOrDefault(new BlockPos(pos.getX(), 0, pos.getZ()), 0L) <= currentTime;
  }

  private long currentTime;

  void lowerHeight(double height) {
    if (Double.isFinite(supportHeight)) supportHeight = Math.min(supportHeight, height);
  }

  void stepHeight(double height) {
    supportHeight = Double.isFinite(supportHeight) ? Math.max(supportHeight, height) : height;
  }

  private boolean gathering;
  private Direction wallFacing;
  private double wallDistance;

  public Direction wallFacing() {
    return wallFacing;
  }

  public double wallDistance() {
    return wallDistance;
  }

  void clingHeight(double height) {
    supportHeight = height;
  }

  private Vec3 reach;

  public Vec3 reach() {
    return reach;
  }

  void reach(Vec3 point) {
    reach = point;
  }

  private double supportHeight = Double.NaN;
  private Vec3 stanceUp = new Vec3(0, 1, 0);

  public boolean bracing() {
    return Double.isFinite(supportHeight);
  }

  public double supportHeight() {
    return supportHeight;
  }

  public Vec3 stanceUp() {
    return stanceUp;
  }

  public boolean walking() {
    return walking;
  }

  public boolean gathering() {
    return gathering;
  }

  void stop() {
    goal = null;
    walking = false;
    gathering = false;
  }

  Vec3 steeringGoal() {
    var points = route.remaining();
    return walking && !points.isEmpty() ? points.getFirst() : goal;
  }

  public Vec3 goal() {
    return goal;
  }

  void tick(LivingCoreBlockEntity core, boolean blocked) {
    currentTime = core.getLevel().getGameTime();
    measureGait(core);
    unreachable.entrySet().removeIf(e -> e.getValue() <= currentTime);
    boolean supported = core.canFly() || supportLoad(core);
    brace(core);
    if (core.isMature() && core.canFly()
        || !supported
        || core.body().health() <= 0
        || core.blueprint().busy()) {
      goal = null;
      walking = false;
      return;
    }
    if (core.getLevel().getGameTime() % 10 != 0) return;
    Vec3 here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    core.brain().growthBlocked(blocked);
    var decision = core.brain().choose(core);
    if (decision.action() != BioshipBrain.Action.GROW_SITE) {
      goal = core.gatheringGoal();
      mode =
          switch (decision.action()) {
            case FLEE -> "flee";
            case DEFEND -> "defend";
            case WANDER -> "wander";
            case ASSIMILATE, SCAVENGE, COLLECT_ITEM, HUNT -> "forage";
            default -> "idle";
          };
      gathering =
          switch (decision.action()) {
            case ASSIMILATE, SCAVENGE, COLLECT_ITEM, HUNT, DEFEND -> true;
            default -> false;
          };
      if (goal != null && !mode.equals("wander")) core.autonomy().activity(core);
      walking =
          goal != null
              && (goal.distanceToSqr(here) > (gathering ? 2.25 : .25)
                  || gathering && !BioshipReach.clear(core, goal, true));
      core.setChanged();
      core.getLevel()
          .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
      return;
    }
    mode = "growth";
    gathering = false;
    if (goal != null && (!clear(core, goal) || goal.distanceToSqr(here) < .2)) goal = null;
    if (goal == null)
      for (int attempt = 0; attempt < 4; attempt++) {
        int n = cursor++ % 48;
        double angle = (n % 8) * Math.PI / 4;
        double radius = 6 + (n / 8) * 3;
        Vec3 p =
            new Vec3(
                Math.floor(here.x + Math.cos(angle) * radius) + .5,
                here.y,
                Math.floor(here.z + Math.sin(angle) * radius) + .5);
        if (clear(core, p)) {
          goal = p;
          break;
        }
      }
    walking = goal != null;
    core.setChanged();
    core.getLevel()
        .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
  }

  boolean clear(LivingCoreBlockEntity core, Vec3 candidate) {
    if (!reachableGoal(BlockPos.containing(candidate))) return false;
    var level = (ServerLevel) core.getLevel();
    for (var part : core.plannedBody()) {
      Vec3 d =
          core.ship()
              .logicalPose()
              .transformNormal(
                  Vec3.atLowerCornerOf(
                      part.offset().offset(core.structuralOrigin().subtract(core.getBlockPos()))));
      var p = BlockPos.containing(candidate.add(d));
      if (!level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)
          || !level.getWorldBorder().isWithinBounds(p)
          || !BioshipIntegration.growthSpace(level, p)
          || !level.getFluidState(p).isEmpty()) return false;
    }
    // Future floor must stay accessible and supported. Avoid cliffs, water and buried foundations.

    for (int z = core.layoutVersion() >= 6 ? -11 : -9; z <= 9; z++)
      for (int x = -DinghyGrowth.radius(z, core.layoutVersion());
          x <= DinghyGrowth.radius(z, core.layoutVersion());
          x++) {
        Vec3 d =
            core.ship()
                .logicalPose()
                .transformNormal(
                    new Vec3(x, 0, z)
                        .add(
                            Vec3.atLowerCornerOf(
                                core.structuralOrigin().subtract(core.getBlockPos()))));
        var p = ground(level, BlockPos.containing(candidate.add(d.x, 0, d.z)));
        if (p == null) return false;
        if (!level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)
            || !level.getFluidState(p).isEmpty()) return false;
      }
    return true;
  }

  Vec3 velocity(LivingCoreBlockEntity core) {
    if (!walking || goal == null) return Vec3.ZERO;
    var level = (ServerLevel) core.getLevel();
    Vec3 here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var waypoint = route.next(core, goal);
    if (waypoint == null) {
      if (route.failed()) {
        if (canEnhance(core)) {
          legTier++;
          if (route.plan(core, goal) && route.complete()) {
            payEnhancement(core);
            return Vec3.ZERO;
          }
          legTier--;
          route.plan(core, goal);
        }
        unreachable.put(
            new BlockPos((int) Math.floor(goal.x), 0, (int) Math.floor(goal.z)),
            core.getLevel().getGameTime() + 400);
        goal = null;
        walking = false;
      }
      return Vec3.ZERO;
    }
    Vec3 delta = waypoint.subtract(here).multiply(1, 0, 1);
    if (delta.lengthSqr() < .01) return Vec3.ZERO;
    Vec3 direction = delta.normalize();
    if (!core.hatchling()) return direction.scale(running() ? 1.4 : .7);
    double phase = core.getLevel().getGameTime() * 1.05;
    double speed =
        (2.4 + .6 * Math.max(0, Math.sin(phase)))
            * (running() ? 1.65 : mode.equals("wander") ? .65 : 1);
    return direction
        .scale(speed)
        .add(new Vec3(-direction.z, 0, direction.x).scale(Math.sin(phase * .5) * .08));
  }

  private BlockPos ground(ServerLevel level, BlockPos point) {
    for (int i = 1; i <= footingReach(); i++) {
      var p = point.below(i);
      if (level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)
          && level.getFluidState(p).isEmpty()
          && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return p;
    }
    return null;
  }

  private void brace(LivingCoreBlockEntity core) {
    supportHeight = Double.NaN;
    stanceUp = new Vec3(0, 1, 0);
    if (core.isMature() && core.canFly()
        || carriedLoad(core) > loadCapacity()
        || core.body().health() <= 0
        || core.blueprint().busy()
        || core.hatchling() && !walking && wallFacing == null && !core.growthStockReady()) return;
    var level = (ServerLevel) core.getLevel();
    Vec3 here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var grip = walking || wallFacing != null ? GroundRoute.grip(core, here) : null;
    wallFacing = null;
    if (grip != null) {
      var root =
          core.ship()
              .logicalPose()
              .transformPositionInverse(
                  here.add(Vec3.atLowerCornerOf(grip.direction().getNormal())))
              .subtract(core.ship().logicalPose().transformPositionInverse(here));
      wallFacing = Direction.getNearest(root.x, root.y, root.z);
      wallDistance = grip.distance();
      supportHeight = here.y;
      return;
    }
    double[] heights = new double[4];
    int count = 0;
    double highest = -Double.MAX_VALUE;
    for (int i = 0; i < 4; i++) {
      Vec3 point =
          here.add(
              (i % 2 == 0 ? -1 : 1) * (core.isMature() ? 3.8 : 1.4),
              -core.coreHeightOffset(),
              (i < 2 ? -1 : 1) * (core.isMature() ? 6 : 1.4));
      var floor = ground(level, BlockPos.containing(point));
      if (floor == null) continue;
      heights[i] = floor.getY() + 1;
      highest = Math.max(highest, heights[i]);
      count++;
    }
    if (count < 3) return;
    double wanted = highest + (core.hatchling() ? .85 : 1.25) + core.coreHeightOffset();
    if (wanted - here.y > 2 + legTier
        || here.y - core.coreHeightOffset() - highest > 2.75 + legTier) return;
    supportHeight = wanted;
    if (count == 4)
      stanceUp =
          new Vec3(
                  Math.clamp((heights[0] + heights[2] - heights[1] - heights[3]) * .025, -.08, .08),
                  1,
                  Math.clamp((heights[0] + heights[1] - heights[2] - heights[3]) * .025, -.08, .08))
              .normalize();
  }

  CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("Wall", wallFacing == null ? -1 : wallFacing.ordinal());
    t.putDouble("WallDistance", wallDistance);
    t.putString("Mode", mode);
    t.putBoolean("Walking", walking);
    t.putBoolean("Gathering", gathering);
    if (reach != null) {
      t.putDouble("ReachX", reach.x);
      t.putDouble("ReachY", reach.y);
      t.putDouble("ReachZ", reach.z);
    }
    t.putDouble("SupportHeight", supportHeight);
    t.putDouble("UpX", stanceUp.x);
    t.putDouble("UpZ", stanceUp.z);
    t.putInt("Cursor", cursor);
    t.putDouble("GaitPhase", gaitPhase);
    t.putDouble("GaitSpeed", gaitSpeed);
    t.putLong("GaitAt", gaitAt);
    t.putInt("LegTier", legTier);
    t.putDouble("LegLoad", measuredLoad);
    if (goal != null) {
      t.putDouble("X", goal.x);
      t.putDouble("Y", goal.y);
      t.putDouble("Z", goal.z);
      t.putBoolean("Goal", true);
    }
    return t;
  }

  void load(CompoundTag t) {
    wallFacing =
        t.contains("Wall") && t.getInt("Wall") >= 0
            ? Direction.values()[Math.min(5, t.getInt("Wall"))]
            : null;
    wallDistance = t.getDouble("WallDistance");
    mode = t.contains("Mode") ? t.getString("Mode") : "idle";
    walking = t.getBoolean("Walking");
    gathering = t.getBoolean("Gathering");
    reach =
        t.contains("ReachX")
            ? new Vec3(t.getDouble("ReachX"), t.getDouble("ReachY"), t.getDouble("ReachZ"))
            : null;
    supportHeight = t.contains("SupportHeight") ? t.getDouble("SupportHeight") : Double.NaN;
    stanceUp = new Vec3(t.getDouble("UpX"), 1, t.getDouble("UpZ")).normalize();
    cursor = t.getInt("Cursor");
    gaitPhase = t.getDouble("GaitPhase");
    gaitSpeed = Math.max(0, t.getDouble("GaitSpeed"));
    gaitAt = t.getLong("GaitAt");
    lastStepPosition = null;
    legTier = Math.clamp(t.getInt("LegTier"), 0, 3);
    measuredLoad = Math.max(0, t.getDouble("LegLoad"));
    goal =
        t.getBoolean("Goal")
            ? new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"))
            : null;
  }
}
