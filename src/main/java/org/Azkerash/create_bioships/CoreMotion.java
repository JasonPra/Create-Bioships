package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The eye trades places with existing tissue. No organs, grid, or extra biomass are moved. */
public final class CoreMotion {
  private static final int FULL_COVERAGE = 48;
  private BlockPos home, target;
  private long started, nextScan;
  private boolean evasive;
  private boolean layoutDirty;

  void layoutChanged(LivingCoreBlockEntity core) {
    layoutDirty = true;
    nextScan = Math.max(1, core.getLevel().getGameTime());
    core.vision().invalidate();
  }

  static boolean protectedInside(LivingCoreBlockEntity core, BlockPos pos) {
    return protectedInside(core, pos, new HashSet<>(BioshipDecay.blocks(core.ship())));
  }

  private static boolean protectedInside(
      LivingCoreBlockEntity core, BlockPos pos, Set<BlockPos> cells) {
    for (var direction : Direction.values()) {
      boolean shield = false;
      for (int distance = 1; distance <= 24; distance++) {
        var sample = pos.relative(direction, distance);
        if (cells.contains(sample)) {
          var state = core.getLevel().getBlockState(sample);
          if (state.isFaceSturdy(core.getLevel(), sample, direction.getOpposite())) {
            shield = true;
            break;
          }
        }
      }
      if (!shield) return false;
    }
    return true;
  }

  private BlockPos shelteredStep(
      LivingCoreBlockEntity core, net.minecraft.world.entity.LivingEntity threat) {
    var source = core.getBlockPos();
    var cells = new HashSet<>(BioshipDecay.blocks(core.ship()));
    var queue = new ArrayDeque<BlockPos>();
    var previous = new LinkedHashMap<BlockPos, BlockPos>();
    queue.add(source);
    previous.put(source, source);
    BlockPos best = source;
    double bestScore =
        protectedInside(core, source, cells)
            ? shelterScore(core, source, threat)
            : -Double.MAX_VALUE;
    while (!queue.isEmpty() && previous.size() < 160) {
      var point = queue.removeFirst();
      for (var direction : Direction.values()) {
        var next = point.relative(direction);
        if (previous.containsKey(next)
            || next.distManhattan(source) > 6
            || !cells.contains(next)
            || !tissue(core.getLevel().getBlockState(next))
            || !protectedInside(core, next, cells)
            || !clearWorld(core, next)
            || !DigestiveSystem.reserveCoreRoute(core, next, false)) continue;
        previous.put(next, point);
        queue.add(next);
        double score = shelterScore(core, next, threat);
        if (score > bestScore + .001) {
          bestScore = score;
          best = next;
        }
      }
    }
    if (best.equals(source)) return null;
    while (!previous.get(best).equals(source)) best = previous.get(best);
    return best;
  }

  private double shelterScore(
      LivingCoreBlockEntity core, BlockPos pos, net.minecraft.world.entity.LivingEntity threat) {
    if (threat == null) return -pos.distSqr(core.getBlockPos());
    var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
    return Math.min(16, world.distanceTo(threat.position()))
        + (BioshipVision.rayAtCore(core, threat.getEyePosition(), world, pos) ? 0 : 50);
  }

  public int duration() {
    return evasive ? 4 : 12;
  }

  public BlockPos home(LivingCoreBlockEntity core) {
    return home == null ? core.getBlockPos() : home;
  }

  public BlockPos target() {
    return target;
  }

  public long started() {
    return started;
  }

  public boolean active() {
    return target != null;
  }

  static boolean tissue(BlockState state) {
    return BioshipTissue.isTissue(state)
        || state.is(Create_bioships.INTESTINE.get())
        || state.is(Create_bioships.CARAPACE.get());
  }

  private boolean clearWorld(LivingCoreBlockEntity core, BlockPos pos) {
    return clearWorld(core, Vec3.atCenterOf(pos));
  }

  private boolean clearWorld(LivingCoreBlockEntity core, Vec3 local) {
    var level = (ServerLevel) core.getLevel();
    var point = core.ship().logicalPose().transformPosition(local);
    for (var cell :
        BlockPos.betweenClosed(
            BlockPos.containing(point.add(-.45, -.45, -.45)),
            BlockPos.containing(point.add(.45, .45, .45))))
      if (!BioshipIntegration.growthSpace(level, cell) || !level.getFluidState(cell).isEmpty())
        return false;
    return level
        .getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class,
            new net.minecraft.world.phys.AABB(
                point.x - .45,
                point.y - .45,
                point.z - .45,
                point.x + .45,
                point.y + .45,
                point.z + .45),
            e -> !(e instanceof BioshipTargetEntity))
        .isEmpty();
  }

  int coverage(LivingCoreBlockEntity core, BlockPos vantage) {
    Vec3 origin = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(vantage));
    int count = 0;
    for (double rise : new double[] {0, 4, 8})
      for (int i = 0; i < 16; i++) {
        double angle = i * Math.PI / 8;
        if (BioshipVision.rayAtCore(
            core, origin, origin.add(Math.cos(angle) * 16, rise, Math.sin(angle) * 16), vantage))
          count++;
      }
    return count;
  }

  private Vec3 blockedSupply(LivingCoreBlockEntity core) {
    var focus = core.vision().focus(core);
    if (focus == null && core.supplyTarget != null) focus = Vec3.atCenterOf(core.supplyTarget);
    return focus != null && !core.vision().visible(core, focus) ? focus : null;
  }

  private int score(LivingCoreBlockEntity core, BlockPos vantage) {
    var focus = blockedSupply(core);
    return coverage(core, vantage)
        + (focus != null
                && BioshipVision.rayAtCore(
                    core,
                    core.ship().logicalPose().transformPosition(Vec3.atCenterOf(vantage)),
                    focus,
                    vantage)
            ? FULL_COVERAGE
            : 0);
  }

  boolean canAvoidBlast(
      LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper) {
    var queue = new ArrayDeque<BlockPos>();
    var seen = new HashSet<BlockPos>();
    queue.add(core.getBlockPos());
    seen.add(core.getBlockPos());
    double safe = (creeper.isPowered() ? 6 : 3) + 1;
    while (!queue.isEmpty() && seen.size() < 160) {
      var p = queue.removeFirst();
      if (!p.equals(core.getBlockPos())
          && core.ship()
                  .logicalPose()
                  .transformPosition(Vec3.atCenterOf(p))
                  .distanceToSqr(creeper.position())
              > safe * safe) return true;
      for (var d : Direction.values()) {
        var next = p.relative(d);
        if (next.distManhattan(core.getBlockPos()) <= 6
            && !seen.contains(next)
            && tissue(core.getLevel().getBlockState(next))
            && (core.hatchling() || protectedInside(core, next))
            && clearWorld(core, next)
            && DigestiveSystem.reserveCoreRoute(core, next, false)) {
          seen.add(next);
          queue.add(next);
        }
      }
    }
    return false;
  }

  private BlockPos choose(LivingCoreBlockEntity core, boolean returning) {
    var level = core.getLevel();
    var source = core.getBlockPos();
    Map<BlockPos, BlockPos> previous = new LinkedHashMap<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    previous.put(source, source);
    queue.add(source);
    BlockPos best = source;
    int score = score(core, source);
    while (!queue.isEmpty() && previous.size() < 160) {
      var pos = queue.removeFirst();
      for (var dir : Direction.values()) {
        var next = pos.relative(dir);
        if (previous.containsKey(next)
            || core.hasMouth() && next.equals(home(core).north())
            || next.distManhattan(home(core)) > 6
            || !tissue(level.getBlockState(next))) continue;
        if (!clearWorld(core, next) || !DigestiveSystem.reserveCoreRoute(core, next, false))
          continue;
        previous.put(next, pos);
        queue.add(next);
        if (returning) {
          if (next.equals(home(core))) {
            best = next;
            queue.clear();
            break;
          }
        } else {
          int value = score(core, next);
          if (value > score) {
            best = next;
            score = value;
          }
        }
      }
      if (returning && best.equals(home(core))) break;
    }
    if (best.equals(source) || returning && !best.equals(home(core))) return null;
    while (!previous.get(best).equals(source)) best = previous.get(best);
    return best;
  }

  private static boolean connectedAfter(Set<BlockPos> body, BlockPos from, BlockPos to) {
    var cells = new HashSet<>(body);
    cells.remove(from);
    cells.add(to);
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    queue.add(to);
    seen.add(to);
    while (!queue.isEmpty()) {
      var cell = queue.removeFirst();
      for (var face : Direction.values()) {
        var next = cell.relative(face);
        if (cells.contains(next) && seen.add(next)) queue.add(next);
      }
    }
    return seen.size() == cells.size();
  }

  private boolean clearPerchTravel(LivingCoreBlockEntity core, BlockPos pos) {
    var from = Vec3.atCenterOf(core.getBlockPos());
    var to = Vec3.atCenterOf(pos);
    int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 4));
    for (int step = 0; step <= steps; step++)
      if (!clearWorld(core, from.lerp(to, step / (double) steps))) return false;
    return true;
  }

  private boolean supportedPerch(LivingCoreBlockEntity core, BlockPos pos) {
    if (!core.hatchling()
        || !core.getLevel().getBlockState(pos).isAir()
        || !core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(pos)))
      return false;
    var body = BodyTopology.structuralCells(BioshipStructure.cells(core.ship()));
    boolean supported = pos.equals(home(core));
    for (var face : Direction.values())
      supported |=
          body.contains(pos.relative(face)) && !pos.relative(face).equals(core.getBlockPos());
    return supported
        && !BioshipAnatomy.mouthOpening(core, pos)
        && !BioshipRespiration.blocksOpening(core, pos)
        && BodyTopology.canCommit(body, core.getBlockPos(), pos, false)
        && clearPerchTravel(core, pos)
        && DigestiveSystem.reserveCoreRoute(core, pos, false);
  }

  private boolean stableSupport(LivingCoreBlockEntity core, BlockPos pos) {
    if (core.getLevel().getBlockEntity(pos) == null) return false;
    var id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
            core.getLevel().getBlockState(pos).getBlock());
    // The first digester disappears during stomach fusion; it cannot support the core.
    return !id.toString().equals("biomancy:digester") || DigestiveSystem.stomach(core) != null;
  }

  private BlockPos perch(LivingCoreBlockEntity core, boolean returning) {
    if (returning) return supportedPerch(core, home(core)) ? home(core) : null;
    BlockPos best = null;
    int current = score(core, core.getBlockPos());
    for (var host : BioshipDecay.blocks(core.ship())) {
      if (!stableSupport(core, host)) continue;
      var pos = host.above();
      if (host.equals(core.getBlockPos())
          || pos.getY() <= core.getBlockPos().getY()
          || pos.distManhattan(home(core)) > 6
          || !supportedPerch(core, pos)) continue;
      int value = score(core, pos);
      if (value <= current) continue;
      if (best == null
          || value > score(core, best)
          || value == score(core, best)
              && pos.distSqr(core.structuralOrigin()) < best.distSqr(core.structuralOrigin()))
        best = pos;
    }
    return best;
  }

  private BlockPos reconnect(LivingCoreBlockEntity core) {
    var cells = BioshipStructure.cells(core.ship());
    var structural = BodyTopology.structuralCells(cells);
    int before = BodyTopology.components(structural);
    if (before <= 1) return null;
    BlockPos best = null;
    int bestSections = before;
    for (var host : structural) {
      if (host.equals(core.getBlockPos())) continue;
      for (var face : Direction.values()) {
        var pos = host.relative(face);
        if (cells.containsKey(pos)
            || !core.hatchling() && !protectedInside(core, pos)
            || !DigestiveSystem.permitsPlacement(core, pos)
            || BioshipAnatomy.mouthOpening(core, pos)
            || !clearWorld(core, pos)
            || !clearPerchTravel(core, pos)
            || !DigestiveSystem.reserveCoreRoute(core, pos, false)
            || !BioshipSupport.safeMove(core, core.getBlockPos(), pos, false)) continue;
        var after = new HashSet<>(structural);
        after.remove(core.getBlockPos());
        after.add(pos);
        int sections = BodyTopology.components(after);
        if (sections < bestSections
            || sections == bestSections
                && best != null
                && pos.distSqr(core.structuralOrigin()) < best.distSqr(core.structuralOrigin())) {
          best = pos;
          bestSections = sections;
        }
      }
    }
    return best;
  }

  private boolean preparePerch(LivingCoreBlockEntity core) {
    // Reuse a machine as the bridge beside the mouth. No stalk or filler block is created.
    var level = core.getLevel();
    var support = home(core).above();
    var mouth = core.mouthBlock();
    if (mouth == null
        || !level.getBlockState(support).isAir()
        || !core.getBlockPos().equals(home(core))) return false;
    var body = new HashSet<>(BioshipDecay.blocks(core.ship()));
    var destination = mouth.above();
    if (score(core, destination) <= score(core, core.getBlockPos())) return false;
    if (!level.getBlockState(destination).isAir() || destination.equals(support)) return false;
    for (var source : body) {
      var entity = level.getBlockEntity(source);
      if (entity == null
          || !stableSupport(core, source)
          || source.equals(mouth)
          || source.equals(core.getBlockPos())
          || source.getY() > home(core).getY()
          || entity.getPersistentData().getBoolean("BioshipManualPlacement")
          || !BioshipIntegration.isIntegratedMachine(level, source)) continue;
      var rearranged = new HashSet<>(body);
      rearranged.remove(source);
      rearranged.add(support);
      if (!connectedAfter(body, source, support)
          || !connectedAfter(rearranged, core.getBlockPos(), destination)
          || !clearWorld(core, destination)) continue;
      if (OrganMotion.begin(core, source, support, false)) return true;
    }
    return false;
  }

  /** Returning true retires this block tick, since a commit replaces its block entity. */
  boolean tick(LivingCoreBlockEntity core) {
    var creeper = BioshipDefense.creeperThreat(core);
    if (core.body().dead()
        || !core.hatchling() && !core.isMature()
        || core.blueprint().busy()
        || OrganMotion.busy(core)) {
      if (active()) cancel(core);
      return false;
    }
    var level = (ServerLevel) core.getLevel();
    if (active()) {
      if (!tissue(level.getBlockState(target)) && !supportedPerch(core, target)
          || !clearWorld(core, target)
          || !core.hatchling() && !protectedInside(core, target)) {
        cancel(core);
        return false;
      }
      if (level.getGameTime() - started >= duration()) commit(core);
      return true;
    }
    if (core.autonomy().creeperPanic()) return false;
    var rejoin = creeper == null ? reconnect(core) : null;
    if (rejoin != null) return begin(core, rejoin, false);
    if (!core.hatchling()) {
      if (creeper != null) return evade(core, creeper);
      var threat = BioshipDefense.target(core);
      boolean exposed =
          threat != null
              && BioshipVision.rayAtCore(
                  core,
                  threat.getEyePosition(),
                  core.ship().logicalPose().transformPosition(Vec3.atCenterOf(core.getBlockPos())),
                  core.getBlockPos());
      if (!protectedInside(core, core.getBlockPos()) || exposed) {
        if (level.getGameTime() < nextScan) return false;
        nextScan = level.getGameTime() + 40;
        var shelter = shelteredStep(core, threat);
        if (shelter != null) return begin(core, shelter, false);
      }
      return false;
    }
    if (creeper != null) return evade(core, creeper);
    if (nextScan == 0) {
      nextScan = level.getGameTime() + 200;
      return false;
    }
    boolean returning = !core.getBlockPos().equals(home(core)) && core.growthSiteReady();
    if (!returning && core.growthStockReady()) return false;
    if (!returning && level.getGameTime() < nextScan) return false;
    nextScan = level.getGameTime() + 40;
    boolean changed = layoutDirty;
    layoutDirty = false;
    if (!returning
        && coverage(core, core.getBlockPos()) == FULL_COVERAGE
        && blockedSupply(core) == null) return false;
    var elevated = perch(core, returning);
    if (elevated != null) return begin(core, elevated, false);
    if (!returning
        && !changed
        && (BioshipDefense.target(core) != null
            || coverage(core, core.getBlockPos()) == FULL_COVERAGE && blockedSupply(core) == null))
      return false;
    var objective = BioshipPriorities.bestWorld(core);
    if (!returning
        && !changed
        && objective != null
        && objective.rank() == 0
        && Vec3.atCenterOf(objective.pos())
                .distanceToSqr(BioshipBlueprint.physical(core, BlockPos.ZERO))
            < 25
        && blockedSupply(core) == null) return false;
    var next = choose(core, returning);
    if (next == null) {
      if (!returning) preparePerch(core);
      return false;
    }
    return begin(core, next, false);
  }

  private boolean begin(LivingCoreBlockEntity core, BlockPos next, boolean emergency) {
    if (!BioshipSupport.safeMove(core, core.getBlockPos(), next, false)
        || !DigestiveSystem.reserveCoreRoute(core, next, true)) return false;
    if (home == null) home = core.getBlockPos();
    target = next;
    evasive = emergency;
    started = core.getLevel().getGameTime();
    feeding = BioshipNutrition.foodRelated(core, core.brain().lastDecision());
    core.getLevel()
        .setBlock(
            core.getBlockPos(), core.getBlockState().setValue(LivingCoreBlock.RELOCATING, true), 2);
    core.setChanged();
    core.getLevel()
        .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
    return true;
  }

  boolean evade(LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper) {
    if (active() || core.body().dead() || core.blueprint().busy()) return false;
    var safe = safestStep(core, creeper);
    if (safe == null || !DigestiveSystem.reserveCoreRoute(core, safe, true)) return false;
    if (home == null) home = core.getBlockPos();
    target = safe;
    evasive = true;
    started = core.getLevel().getGameTime();
    feeding = BioshipNutrition.foodRelated(core, core.brain().lastDecision());
    core.getLevel()
        .setBlock(
            core.getBlockPos(), core.getBlockState().setValue(LivingCoreBlock.RELOCATING, true), 2);
    core.setChanged();
    return true;
  }

  static double blastSafety(
      LivingCoreBlockEntity core,
      BlockPos point,
      net.minecraft.world.entity.monster.Creeper creeper) {
    var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(point));
    double distance = world.distanceTo(creeper.position());
    double radius = creeper.isPowered() ? 6 : 3;
    boolean shielded =
        !BioshipVision.rayAtCore(core, creeper.getBoundingBox().getCenter(), world, point);
    return Math.min(distance, radius + 3) * 10 + (shielded ? 25 : 0);
  }

  private BlockPos safestStep(
      LivingCoreBlockEntity core, net.minecraft.world.entity.monster.Creeper creeper) {
    var source = core.getBlockPos();
    var previous = new LinkedHashMap<BlockPos, BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    previous.put(source, source);
    queue.add(source);
    BlockPos best = source;
    double score = blastSafety(core, source, creeper);
    while (!queue.isEmpty() && previous.size() < 160) {
      var point = queue.removeFirst();
      double current = blastSafety(core, point, creeper);
      for (var direction : Direction.values()) {
        var next = point.relative(direction);
        if (previous.containsKey(next)
            || next.distManhattan(source) > 6
            || !tissue(core.getLevel().getBlockState(next))
            || !core.hatchling() && !protectedInside(core, next)
            || !clearWorld(core, next)
            || !DigestiveSystem.reserveCoreRoute(core, next, false)) continue;
        double value = blastSafety(core, next, creeper);
        if (value + .001 < current) continue;
        previous.put(next, point);
        queue.add(next);
        if (value > score + .001) {
          score = value;
          best = next;
        }
      }
    }
    if (best.equals(source)) return null;
    while (!previous.get(best).equals(source)) best = previous.get(best);
    return best;
  }

  String scoutingHint(LivingCoreBlockEntity core) {
    if (active()) return "moving to a supported vantage";
    if (!core.hatchling()) return "core concealed inside grown body";
    if (coverage(core, core.getBlockPos()) == FULL_COVERAGE) return "nearby survey is clear";
    boolean above = false;
    var queue = new ArrayDeque<BlockPos>();
    var seen = new HashSet<BlockPos>();
    queue.add(core.getBlockPos());
    seen.add(core.getBlockPos());
    while (!queue.isEmpty() && seen.size() < 160) {
      var point = queue.removeFirst();
      for (var direction : Direction.values()) {
        var next = point.relative(direction);
        if (next.distManhattan(home(core)) <= 6
            && tissue(core.getLevel().getBlockState(next))
            && seen.add(next)) {
          above |= next.getY() > core.getBlockPos().getY();
          queue.add(next);
        }
      }
    }
    return above
        ? "checking connected higher tissue"
        : "no higher connected tissue; checking supported component perches";
  }

  void scoutNow(LivingCoreBlockEntity core) {
    nextScan = core.getLevel().getGameTime();
    tick(core);
  }

  private boolean feeding;

  boolean feeding() {
    return feeding;
  }

  void cancel(LivingCoreBlockEntity core) {
    target = null;
    core.getLevel()
        .setBlock(
            core.getBlockPos(),
            core.getBlockState().setValue(LivingCoreBlock.RELOCATING, false),
            2);
    core.setChanged();
  }

  private void commit(LivingCoreBlockEntity core) {
    var level = (ServerLevel) core.getLevel();
    var ship = core.ship();
    var from = core.getBlockPos();
    var to = target;
    var skin = level.getBlockState(to);
    var before = BioshipBody.actual(core);
    target = null;
    nextScan = level.getGameTime() + 40;
    level.setBlock(from, core.getBlockState().setValue(LivingCoreBlock.RELOCATING, false), 2);
    core.setChanged();
    level.removeBlock(to, false);
    SubLevelAssemblyHelper.moveBlocks(
        level,
        new SubLevelAssemblyHelper.AssemblyTransform(from, to, 0, Rotation.NONE, level),
        List.of(from));
    level.setBlock(from, skin, 3);
    if (!(level.getBlockEntity(to) instanceof LivingCoreBlockEntity moved))
      throw new IllegalStateException("Lost relocating bioship core");
    var delta = from.subtract(to);
    moved.body().rebase(delta);
    Map<BlockPos, BlockState> shifted = new LinkedHashMap<>();
    before.forEach((p, s) -> shifted.put(p.offset(delta), s));
    moved.inheritFlight(core);
    moved.crawl().stop();
    DigestiveSystem.connect(moved, ship);
    moved.body().reconcile(moved, shifted);
    moved.setChanged();
    level.sendBlockUpdated(to, moved.getBlockState(), moved.getBlockState(), 2);
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    if (home != null) tag.putLong("Home", home.asLong());
    if (target != null) tag.putLong("Target", target.asLong());
    tag.putBoolean("Evasive", evasive);
    tag.putLong("Started", started);
    tag.putBoolean("Feeding", feeding);
    tag.putLong("NextScan", nextScan);
    tag.putBoolean("LayoutDirty", layoutDirty);
    return tag;
  }

  void load(CompoundTag tag) {
    home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
    target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
    evasive = tag.getBoolean("Evasive");
    started = tag.getLong("Started");
    feeding = tag.getBoolean("Feeding");
    nextScan = tag.getLong("NextScan");
    layoutDirty = tag.getBoolean("LayoutDirty");
  }
}
