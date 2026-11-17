package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.network.VisionDebug;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;
import org.Azkerash.create_bioships.disabled.wip.DinghyGrowth;

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
        BodyCoordinates.physical(core.amoebaComponents(), core.structuralOrigin().subtract(core.getBlockPos()));
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

  private final BioshipLegAnchors legAnchors = new BioshipLegAnchors();

  public BioshipLegAnchors legAnchors() {
    return legAnchors;
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

  public static double capacityFor(int tier) {
    return 32 + 128d * tier * tier;
  }

  int hullTier(LivingCoreBlockEntity core) {
    double unit =
        dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
            core.getLevel(), core.getBlockPos(), BioshipTissue.body().defaultBlockState());
    double mass = 0;
    for (var part : core.plannedBody())
      mass +=
          dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
              core.getLevel(), core.structuralOrigin().offset(part.offset()), part.state());
    double load = Math.max(carriedLoad(core), mass / Math.max(.001, unit));
    for (int tier = 0; tier <= 3; tier++) if (load <= capacityFor(tier)) return tier;
    return 4;
  }

  public double blockLoad(
      LivingCoreBlockEntity core,
      BlockPos pos,
      net.minecraft.world.level.block.state.BlockState state) {
    double unit =
        dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
            core.getLevel(), core.getBlockPos(), BioshipTissue.body().defaultBlockState());
    return dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
            core.getLevel(), pos, state)
        / Math.max(.001, unit);
  }

  public double carriedLoad(LivingCoreBlockEntity core) {
    double tissue =
        dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(
            core.getLevel(), core.getBlockPos(), BioshipTissue.body().defaultBlockState());
    return core.ship() == null
        ? 0
        : core.ship().getMassTracker().getMass() / Math.max(.001, tissue)
            + core.assimilation().load(core);
  }

  boolean canEnhance(LivingCoreBlockEntity core) {
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
  private String selection = "";

  private void retireChangedSelection(LivingCoreBlockEntity core) {
    var decision = core.brain().lastDecision();
    if (!selection.isEmpty() && decision != null && !selection.equals(decision.offer().key()))
      stop();
  }

  private final Map<BlockPos, Long> unreachable = new HashMap<>();

  public net.minecraft.nbt.CompoundTag navigationDebug(long now) {
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
    route.clear();
    selection = "";
    reach = null;
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
    retireChangedSelection(core);
    unreachable.entrySet().removeIf(e -> e.getValue() <= currentTime);
    boolean supported = core.canFly() || supportLoad(core);
    brace(core);
    legAnchors.tick(core);
    if (core.isMature() && core.canFly()
        || !supported
        || core.body().health() <= 0
        || core.blueprint().busy()) {
      stop();
      return;
    }
    if (core.getLevel().getGameTime() % 10 != 0) return;
    Vec3 here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    core.brain().growthBlocked(blocked);
    var decision = core.brain().choose(core);
    if (decision.action() != BioshipBrain.Action.GROW_SITE) {
      var nextGoal = core.gatheringGoal();
      var chosen = core.brain().lastDecision();
      var nextSelection = chosen == null ? "" : chosen.offer().key();
      if (!selection.equals(nextSelection) || goal == null && nextGoal != null || nextGoal == null)
        route.clear();
      selection = nextSelection;
      goal = nextGoal;
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
    retireChangedSelection(core);
    if (!walking || goal == null) return Vec3.ZERO;
    var level = (ServerLevel) core.getLevel();
    Vec3 here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
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
    return direction.scale(speed);
  }

  private BlockPos ground(ServerLevel level, BlockPos point) {
    return ground(level, point, point.getY());
  }

  private BlockPos ground(ServerLevel level, BlockPos point, double highestTop) {
    for (int i = -extraLift(); i <= footingReach(); i++) {
      var p = point.below(i);
      if (p.getY() + 1 <= highestTop
          && level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)
          && level.getFluidState(p).isEmpty()
          && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return p;
    }
    return null;
  }

  private void braceGrip(LivingCoreBlockEntity core, Vec3 here, GroundRoute.Grip grip) {
    if (grip != null) {
      var root =
          core.ship()
              .logicalPose()
              .transformPositionInverse(
                  here.add(Vec3.atLowerCornerOf(grip.direction().getNormal())))
              .subtract(core.ship().logicalPose().transformPositionInverse(here));
      wallFacing = Direction.getNearest(root.x, root.y, root.z);
      wallDistance = grip.distance();
      stanceUp = Vec3.atLowerCornerOf(grip.direction().getOpposite().getNormal());
      supportHeight = here.y;
      return;
    }
  }

  private void brace(LivingCoreBlockEntity core) {
    supportHeight = Double.NaN;
    stanceUp = new Vec3(0, 1, 0);
    if (core.isMature() && core.canFly()
        || carriedLoad(core) > loadCapacity()
        || core.body().health() <= 0
        || core.blueprint().busy()) return;
    var level = (ServerLevel) core.getLevel();
    Vec3 here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    boolean clinging = wallFacing != null;
    // A planted tentacle remains attached while resting or changing tasks. Only losing the
    // actual support surface should release an existing wall/ceiling stance.
    var grip = walking || clinging || reach != null ? GroundRoute.grip(core, here) : null;
    wallFacing = null;
    if (clinging && grip != null) {
      braceGrip(core, here, grip);
      return;
    }
    var anchor =
        core.ship().logicalPose().transformPosition(Vec3.atCenterOf(core.structuralOrigin()));
    var righting = BioshipRighting.support(core);
    if (righting != null) {
      supportHeight = righting.height();
      stanceUp = new Vec3(0, 1, 0);
      return;
    }
    var layout = BioshipSupport.layout(core);
    if (!layout.stable()) return;
    Vec3[] contacts = new Vec3[layout.legs().size()];
    int count = 0;
    double highest = -Double.MAX_VALUE;
    double sum = 0;
    double legHeight = core.hatchling() ? .85 : 1.25;
    for (int i = 0; i < layout.legs().size(); i++) {
      var foot = layout.legs().get(i).foot();
      var local =
          Vec3.atLowerCornerOf(core.structuralOrigin()).add(foot.x, foot.y + legHeight, foot.z);
      var world = core.ship().logicalPose().transformPosition(local);
      Vec3 point = world;
      var floor = ground(level, BlockPos.containing(point), point.y - legHeight + extraLift() + .5);
      if (floor == null) continue;
      double top = floor.getY() + 1;
      var planted = legAnchors.supportContact(level, i, level.getGameTime());
      contacts[i] =
          planted != null && planted.distanceToSqr(world) < Math.pow(footingReach(), 2)
              ? planted
              : new Vec3(point.x, top, point.z);
      top = contacts[i].y;
      highest = Math.max(highest, top);
      sum += top;
      count++;
    }
    if (count < 3) {
      braceGrip(core, here, grip);
      return;
    }
    double lowest =
        Arrays.stream(contacts)
            .filter(Objects::nonNull)
            .mapToDouble(p -> p.y)
            .min()
            .orElse(highest);
    if (grip != null && highest - lowest < .1 && goal != null && goal.y > here.y + .5) {
      braceGrip(core, here, grip);
      return;
    }
    var massCenter =
        core.ship()
            .logicalPose()
            .transformPosition(Vec3.atLowerCornerOf(core.structuralOrigin()).add(layout.center()));
    if (!BioshipSupport.containsProjection(massCenter, contacts)) return;
    double center = sum / count;
    double underside = layout.legs().getFirst().foot().y + (core.hatchling() ? .35 : .75);
    double wanted = center + legHeight - underside + here.y - anchor.y;
    if (highest - (anchor.y + underside) > extraLift() + .25
        || anchor.y + underside - highest > 2.75 + legTier) return;
    supportHeight = wanted;
    if (count == 4 && contacts.length == 4) {
      var right = contacts[1].add(contacts[3]).subtract(contacts[0]).subtract(contacts[2]);
      var forward = contacts[0].add(contacts[1]).subtract(contacts[2]).subtract(contacts[3]);
      var normal = right.cross(forward).normalize();
      if (normal.y < 0) normal = normal.scale(-1);
      if (normal.y > .65) {
        stanceUp = normal;
        double cx = Arrays.stream(contacts).mapToDouble(p -> p.x).average().orElseThrow();
        double cz = Arrays.stream(contacts).mapToDouble(p -> p.z).average().orElseThrow();
        double plane =
            center - (normal.x * (anchor.x - cx) + normal.z * (anchor.z - cz)) / normal.y;
        supportHeight = plane + legHeight - underside + here.y - anchor.y;
      }
    }
  }

  CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("Wall", wallFacing == null ? -1 : wallFacing.ordinal());
    t.putDouble("WallDistance", wallDistance);
    t.put("LegAnchors", legAnchors.save());
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
    t.putDouble("UpY", stanceUp.y);
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
    legAnchors.load(t.getCompound("LegAnchors"));
    // Routes and selection identity are transient; a restored goal must be replanned together.
    selection = "";
    route.clear();
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
    stanceUp =
        new Vec3(t.getDouble("UpX"), t.contains("UpY") ? t.getDouble("UpY") : 1, t.getDouble("UpZ"))
            .normalize();
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
