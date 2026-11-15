package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Food only crosses a continuous physical intestine network; outputs remain in the stomach. */
public final class DigestiveSystem {
  public static StomachBlockEntity stomach(LivingCoreBlockEntity core) {
    var ship = core.ship();
    if (ship == null) return null;
    for (var entity : BioshipIntegration.blockEntities(ship))
      if (entity instanceof StomachBlockEntity stomach) return stomach;
    return null;
  }

  private static boolean linked(LivingCoreBlockEntity core, BlockPos a, BlockPos b) {
    for (var direction : Direction.values())
      if (a.relative(direction).equals(b)) {
        var first = core.getLevel().getBlockState(a);
        var second = core.getLevel().getBlockState(b);
        return IntestineBlock.compatible(first)
            && IntestineBlock.compatible(second)
            && (!first.is(Create_bioships.INTESTINE.get())
                || first.getValue(IntestineBlock.CONNECTIONS.get(direction)))
            && (!second.is(Create_bioships.INTESTINE.get())
                || second.getValue(IntestineBlock.CONNECTIONS.get(direction.getOpposite())));
      }
    return false;
  }

  private static boolean pathIntact(
      LivingCoreBlockEntity core, BlockPos start, BlockPos end, List<BlockPos> path) {
    var previous = start;
    for (var point : path) {
      if (!linked(core, previous, point)
          || !core.getLevel().getBlockState(point).is(Create_bioships.INTESTINE.get()))
        return false;
      previous = point;
    }
    return linked(core, previous, end);
  }

  public static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    var mouth = core.mouthBlock();
    return mouth != null
        && mouth.equals(stomach.connectedMouth)
        && pathIntact(core, mouth, stomach.getBlockPos(), stomach.gutPath);
  }

  private static List<BlockPos> findPath(
      LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end) {
    return findPath(core, ship, start, end, null, null);
  }

  private static List<BlockPos> findPath(
      LivingCoreBlockEntity core,
      ServerSubLevel ship,
      BlockPos start,
      BlockPos end,
      BlockPos blocked,
      BlockPos freed) {
    var level = ship.getLevel();
    var origin = core.structuralOrigin();
    Map<BlockPos, BlockPos> parent = new HashMap<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(start);
    parent.put(start, start);
    while (!queue.isEmpty() && parent.size() < 12000) {
      var point = queue.removeFirst();
      if (point.equals(end)) {
        List<BlockPos> path = new ArrayList<>();
        var previous = parent.get(end);
        while (!previous.equals(start)) {
          path.add(previous);
          previous = parent.get(previous);
        }
        Collections.reverse(path);
        return path;
      }
      for (var direction : Direction.values()) {
        var next = point.relative(direction);
        var offset = next.subtract(origin);
        if (parent.containsKey(next)
            || Math.abs(offset.getX()) > 24
            || offset.getY() < 0
            || offset.getY() > 6
            || Math.abs(offset.getZ()) > 24) continue;
        if (!next.equals(end) && offset.getY() != 1) continue;
        if (!ship.getPlot().contains(new net.minecraft.world.level.ChunkPos(next))) continue;
        var state =
            next.equals(blocked)
                ? Create_bioships.CORE.get().defaultBlockState()
                : next.equals(freed)
                    ? Create_bioships.INTESTINE.get().defaultBlockState()
                    : level.getBlockState(next);
        if (!next.equals(end)
            && !state.isAir()
            && !state.is(Create_bioships.FLESH.get())
            && !state.is(Create_bioships.INTESTINE.get())) continue;
        if (!next.equals(end)) {
          Vec3 physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(next));
          if (!level.getBlockState(BlockPos.containing(physical)).isAir()) continue;
        }
        parent.put(next, point);
        queue.addLast(next);
      }
    }
    return null;
  }

  private record Plan(Set<BlockPos> wanted, List<BlockPos> retired, int purchases) {}

  private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> tubes(
      LivingCoreBlockEntity core) {
    Map<BlockPos, net.minecraft.world.level.block.state.BlockState> result = new LinkedHashMap<>();
    for (var p : BioshipDecay.blocks(core.ship())) {
      var state = core.getLevel().getBlockState(p);
      if (state.is(Create_bioships.INTESTINE.get())) result.put(p, state);
    }
    return result;
  }

  private static boolean removable(BlockPos point, Set<BlockPos> cells) {
    var neighbors = new ArrayList<BlockPos>();
    for (var d : Direction.values())
      if (cells.contains(point.relative(d))) neighbors.add(point.relative(d));
    if (neighbors.size() < 2) return true;
    Set<BlockPos> seen = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(neighbors.getFirst());
    seen.add(neighbors.getFirst());
    while (!queue.isEmpty() && seen.size() < 20000) {
      var p = queue.removeFirst();
      for (var d : Direction.values()) {
        var n = p.relative(d);
        if (!n.equals(point) && cells.contains(n) && seen.add(n)) queue.add(n);
      }
    }
    return seen.containsAll(neighbors);
  }

  private static boolean coreLink(
      LivingCoreBlockEntity core, BlockPos removed, Set<BlockPos> cells, Set<BlockPos> wanted) {
    if (core.getBlockPos().equals(core.structuralOrigin())) return true;
    Set<BlockPos> seen = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(core.getBlockPos());
    seen.add(core.getBlockPos());
    while (!queue.isEmpty() && seen.size() < 1000) {
      var point = queue.removeFirst();
      if (point.equals(core.structuralOrigin())) return true;
      for (var d : Direction.values()) {
        var next = point.relative(d);
        if (!next.equals(removed)
            && cells.contains(next)
            && (wanted.contains(next) || CoreMotion.tissue(core.getLevel().getBlockState(next)))
            && seen.add(next)) queue.add(next);
      }
    }
    return false;
  }

  static boolean reserveOrganRoute(LivingCoreBlockEntity core, BlockPos source, BlockPos target) {
    var stomach = stomach(core);
    var mouth = core.mouthBlock();
    if (stomach == null || mouth == null || !stomach.tubeBootstrapped) return true;
    boolean movesMouth = source.equals(mouth), movesStomach = source.equals(stomach.getBlockPos());
    boolean movesStore = stomach.sacPaths.containsKey(source);
    if (!movesMouth && !movesStomach && !movesStore) return true;
    var end = movesStomach ? target : stomach.getBlockPos();
    var start = movesMouth ? target : mouth;
    var main = findPath(core, core.ship(), start, end);
    if (main == null) return false;
    var wanted = new LinkedHashSet<>(main);
    for (var sac : stomach.sacPaths.keySet()) {
      var path = findPath(core, core.ship(), end, movesStore && sac.equals(source) ? target : sac);
      if (path == null) return false;
      wanted.addAll(path);
    }
    var result = plan(core, stomach, wanted, Set.of(), tubes(core));
    if (!affordable(stomach, result)) return false;
    if (result.purchases > 0) {
      stomach.spend("biomancy:flesh_bits", result.purchases);
      stomach.spend("biomancy:elastic_fibers", result.purchases);
      stomach.paidTubeStock += result.purchases;
      stomach.setChanged();
    }
    return true;
  }

  private static Plan plan(
      LivingCoreBlockEntity core,
      StomachBlockEntity stomach,
      Set<BlockPos> wanted,
      Set<BlockPos> starter,
      Map<BlockPos, net.minecraft.world.level.block.state.BlockState> existing) {
    Set<BlockPos> cells = new HashSet<>(BioshipDecay.blocks(core.ship()));
    cells.addAll(wanted);
    var retired = new ArrayList<BlockPos>();
    int reuse = stomach.freeTubeStock + stomach.paidTubeStock;
    for (var entry : existing.entrySet())
      if (!entry.getKey().equals(core.structuralOrigin())
          && !wanted.contains(entry.getKey())
          && !entry.getValue().getValue(IntestineBlock.MANUAL)) {
        if (entry.getValue().getValue(IntestineBlock.ENCASED)
            || removable(entry.getKey(), cells) && coreLink(core, entry.getKey(), cells, wanted)) {
          retired.add(entry.getKey());
          reuse++;
          if (!entry.getValue().getValue(IntestineBlock.ENCASED)) cells.remove(entry.getKey());
        }
      }
    int missing = 0, free = 0;
    for (var p : wanted)
      if (!existing.containsKey(p)) {
        missing++;
        if (starter.contains(p)) free++;
      }
    return new Plan(new LinkedHashSet<>(wanted), retired, Math.max(0, missing - free - reuse));
  }

  private static boolean affordable(StomachBlockEntity stomach, Plan plan) {
    return stomach.material("biomancy:flesh_bits") >= plan.purchases
        && stomach.material("biomancy:elastic_fibers") >= plan.purchases;
  }

  static boolean reserveCoreRoute(LivingCoreBlockEntity core, BlockPos target, boolean reserve) {
    var stomach = stomach(core);
    if (stomach == null) return true;
    boolean crosses =
        stomach.gutPath.contains(target)
            || stomach.sacPaths.values().stream().anyMatch(p -> p.contains(target));
    if (!crosses) return true;
    var main =
        findPath(
            core,
            core.ship(),
            core.mouthBlock(),
            stomach.getBlockPos(),
            target,
            core.getBlockPos());
    if (main == null) return false;
    var wanted = new LinkedHashSet<>(main);
    for (var sac : stomach.sacPaths.keySet()) {
      var path =
          findPath(core, core.ship(), stomach.getBlockPos(), sac, target, core.getBlockPos());
      if (path == null) return false;
      wanted.addAll(path);
    }
    var existing = tubes(core);
    var skin = existing.remove(target);
    if (skin != null) existing.put(core.getBlockPos(), skin);
    var plan = plan(core, stomach, wanted, Set.of(), existing);
    if (!affordable(stomach, plan)) return false;
    if (reserve && plan.purchases > 0) {
      stomach.spend("biomancy:flesh_bits", plan.purchases);
      stomach.spend("biomancy:elastic_fibers", plan.purchases);
      stomach.paidTubeStock += plan.purchases;
      stomach.setChanged();
    }
    return true;
  }

  public static void connect(LivingCoreBlockEntity core, ServerSubLevel ship) {
    var stomach = stomach(core);
    var mouth = core.mouthBlock();
    if (stomach == null || mouth == null) return;
    var main = findPath(core, ship, mouth, stomach.getBlockPos());
    if (main == null) return;
    var existing = tubes(core);
    Set<BlockPos> starter = stomach.tubeBootstrapped ? Set.of() : new HashSet<>(main);
    var wanted = new LinkedHashSet<>(main);
    var selected = plan(core, stomach, wanted, starter, existing);
    if (!affordable(stomach, selected)) return;
    Map<BlockPos, List<BlockPos>> branches = new LinkedHashMap<>();
    var stores =
        BioshipIntegration.blockEntities(ship).stream()
            .filter(EngineRoom::storage)
            .sorted(
                java.util.Comparator.comparing(e -> !stomach.sacPaths.containsKey(e.getBlockPos())))
            .toList();
    for (var store : stores) {
      var path = findPath(core, ship, stomach.getBlockPos(), store.getBlockPos());
      if (path == null) continue;
      var trial = new LinkedHashSet<>(wanted);
      trial.addAll(path);
      var candidate = plan(core, stomach, trial, starter, existing);
      if (affordable(stomach, candidate)) {
        wanted = trial;
        selected = candidate;
        branches.put(store.getBlockPos(), path);
      }
    }
    var level = ship.getLevel();
    for (var old : selected.retired) {
      var state = existing.get(old);
      if (state.getValue(IntestineBlock.PAID)) stomach.paidTubeStock++;
      else stomach.freeTubeStock++;
      level.setBlock(
          old,
          state.getValue(IntestineBlock.ENCASED)
              ? Create_bioships.FLESH.get().defaultBlockState()
              : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
          3);
    }
    for (var next : wanted) {
      var old = level.getBlockState(next);
      if (old.is(Create_bioships.INTESTINE.get())) continue;
      boolean paid;
      if (starter.contains(next)) paid = false;
      else if (stomach.freeTubeStock > 0) {
        stomach.freeTubeStock--;
        paid = false;
      } else if (stomach.paidTubeStock > 0) {
        stomach.paidTubeStock--;
        paid = true;
      } else {
        stomach.spend("biomancy:flesh_bits", 1);
        stomach.spend("biomancy:elastic_fibers", 1);
        paid = true;
      }
      level.setBlock(
          next,
          Create_bioships.INTESTINE
              .get()
              .defaultBlockState()
              .setValue(IntestineBlock.ENCASED, old.is(Create_bioships.FLESH.get()))
              .setValue(IntestineBlock.PAID, paid),
          3);
    }
    for (var next : wanted) {
      var state = level.getBlockState(next);
      var connected = Create_bioships.INTESTINE.get().connections(state, level, next);
      if (!state.equals(connected)) level.setBlock(next, connected, 3);
    }
    stomach.gutPath.clear();
    stomach.gutPath.addAll(main);
    stomach.sacPaths.clear();
    stomach.sacPaths.putAll(branches);
    stomach.connectedMouth = mouth;
    stomach.tubeBootstrapped = true;
    stomach.setChanged();
  }

  private static boolean connectedStore(
      LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos pos, List<BlockPos> path) {
    var entity = core.getLevel().getBlockEntity(pos);
    return BioshipIntegration.containing(core.getLevel(), pos) == core.ship()
        && entity != null
        && EngineRoom.storage(entity)
        && pathIntact(core, stomach.getBlockPos(), pos, path);
  }

  public static boolean offerFood(LivingCoreBlockEntity core, ItemStack stack, boolean inherited) {
    if (stack.isEmpty()) return false;
    var stomach = stomach(core);
    if (stomach == null || !stomach.connected(core)) return false;
    if (stomach.enqueue(stack, inherited)) return true;
    if (inherited) return false;
    for (var entry : stomach.sacPaths.entrySet())
      if (connectedStore(core, stomach, entry.getKey(), entry.getValue())) {
        var inventory =
            core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, entry.getKey(), null);
        if (inventory == null) continue;
        var one = stack.copyWithCount(1);
        if (ItemHandlerHelper.insertItemStacked(inventory, one, true).isEmpty())
          return ItemHandlerHelper.insertItemStacked(inventory, one, false).isEmpty();
      }
    return false;
  }

  public static void pullStoredFood(LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    if (!stomach.connected(core) || !core.wantsStoredFood()) return;
    for (var entry : stomach.sacPaths.entrySet())
      if (connectedStore(core, stomach, entry.getKey(), entry.getValue())) {
        var inventory =
            core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, entry.getKey(), null);
        if (inventory == null) continue;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
          var one = inventory.extractItem(slot, 1, true);
          if (one.isEmpty()) continue;
          boolean material =
              one.is(BioshipIntegration.constructionMaterial())
                  && !LivingCoreBlockEntity.isMeat(one);
          if (material) {
            var taken = inventory.extractItem(slot, 1, false);
            if (!taken.isEmpty()) stomach.retain(taken);
            return;
          }
          if (stomach.canEnqueue(one, false)) {
            var taken = inventory.extractItem(slot, 1, false);
            if (!taken.isEmpty()) stomach.enqueue(taken, false);
            return;
          }
        }
      }
  }
}
