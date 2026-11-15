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
    return state.is(Create_bioships.FLESH.get())
        || state.is(Create_bioships.INTESTINE.get())
        || state.is(Create_bioships.CARAPACE.get());
  }

  private boolean clearWorld(LivingCoreBlockEntity core, BlockPos pos) {
    var level = (ServerLevel) core.getLevel();
    var point = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
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
                point.z + .45))
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

  private BlockPos choose(LivingCoreBlockEntity core, boolean returning) {
    var level = core.getLevel();
    var source = core.getBlockPos();
    Map<BlockPos, BlockPos> previous = new LinkedHashMap<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    previous.put(source, source);
    queue.add(source);
    BlockPos best = source;
    int score = coverage(core, source);
    while (!queue.isEmpty() && previous.size() < 160) {
      var pos = queue.removeFirst();
      for (var dir : Direction.values()) {
        var next = pos.relative(dir);
        if (previous.containsKey(next)
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
          int value = coverage(core, next);
          if (value > score
              || value == score && value > coverage(core, source) && next.getY() > best.getY()) {
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

  /** Returning true retires this block tick, since a commit replaces its block entity. */
  boolean tick(LivingCoreBlockEntity core) {
    if (!core.hatchling()
        || core.body().dead()
        || core.blueprint().busy()
        || OrganMotion.busy(core)) {
      if (active()) cancel(core);
      return false;
    }
    var level = (ServerLevel) core.getLevel();
    if (active()) {
      if (!tissue(level.getBlockState(target)) || !clearWorld(core, target)) {
        cancel(core);
        return false;
      }
      if (level.getGameTime() - started >= 12) commit(core);
      return true;
    }
    if (nextScan == 0) {
      nextScan = level.getGameTime() + 200;
      return false;
    }
    boolean returning = !core.getBlockPos().equals(home(core)) && core.growthSiteReady();
    if (!returning && core.growthStockReady()) return false;
    if (!returning && level.getGameTime() < nextScan) return false;
    nextScan = level.getGameTime() + 40;
    if (!returning
        && (BioshipDefense.target(core) != null
            || coverage(core, core.getBlockPos()) == FULL_COVERAGE)) return false;
    var objective = BioshipPriorities.bestWorld(core);
    if (!returning
        && objective != null
        && objective.rank() == 0
        && Vec3.atCenterOf(objective.pos())
                .distanceToSqr(BioshipBlueprint.physical(core, BlockPos.ZERO))
            < 25) return false;
    var next = choose(core, returning);
    if (next == null) return false;
    if (!DigestiveSystem.reserveCoreRoute(core, next, true)) return false;
    if (home == null) home = core.getBlockPos();
    target = next;
    started = level.getGameTime();
    level.setBlock(
        core.getBlockPos(), core.getBlockState().setValue(LivingCoreBlock.RELOCATING, true), 2);
    core.setChanged();
    level.sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
    return true;
  }

  String scoutingHint(LivingCoreBlockEntity core) {
    if (active()) return "moving through connected tissue";
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
    return above ? "checking connected higher tissue" : "no connected higher tissue to move into";
  }

  void scoutNow(LivingCoreBlockEntity core) {
    nextScan = core.getLevel().getGameTime();
    tick(core);
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
    tag.putLong("Started", started);
    tag.putLong("NextScan", nextScan);
    return tag;
  }

  void load(CompoundTag tag) {
    home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
    target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
    started = tag.getLong("Started");
    nextScan = tag.getLong("NextScan");
  }
}
