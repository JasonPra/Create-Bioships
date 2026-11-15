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

  private static boolean pathIntact(
      LivingCoreBlockEntity core, BlockPos start, BlockPos end, List<BlockPos> path) {
    var previous = start;
    for (var point : path) {
      if (previous.distManhattan(point) != 1
          || !core.getLevel().getBlockState(point).is(Create_bioships.INTESTINE.get()))
        return false;
      previous = point;
    }
    return previous.distManhattan(end) == 1;
  }

  public static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    var mouth = core.mouthBlock();
    return mouth != null
        && mouth.equals(stomach.connectedMouth)
        && pathIntact(core, mouth, stomach.getBlockPos(), stomach.gutPath);
  }

  private static List<BlockPos> findPath(
      LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end) {
    var level = ship.getLevel();
    var origin = core.getBlockPos();
    Map<BlockPos, BlockPos> parent = new HashMap<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(start);
    parent.put(start, start);
    while (!queue.isEmpty() && parent.size() < 4096) {
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
            || Math.abs(offset.getX()) > 5
            || offset.getY() < 0
            || offset.getY() > 6
            || Math.abs(offset.getZ()) > 10) continue;
        if (!next.equals(end) && offset.getY() != 1) continue;
        if (!ship.getPlot().contains(new net.minecraft.world.level.ChunkPos(next))) continue;
        var state = level.getBlockState(next);
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

  public static void connect(LivingCoreBlockEntity core, ServerSubLevel ship) {
    var stomach = stomach(core);
    var mouth = core.mouthBlock();
    if (stomach == null || mouth == null) return;
    var main = findPath(core, ship, mouth, stomach.getBlockPos());
    if (main == null) return;
    Map<BlockPos, List<BlockPos>> branches = new LinkedHashMap<>();
    for (var entity : BioshipIntegration.blockEntities(ship))
      if (EngineRoom.storage(entity)) {
        var branch = findPath(core, ship, stomach.getBlockPos(), entity.getBlockPos());
        if (branch != null) branches.put(entity.getBlockPos(), branch);
      }
    Set<BlockPos> previous = new HashSet<>(stomach.gutPath);
    stomach.sacPaths.values().forEach(previous::addAll);
    Set<BlockPos> wanted = new HashSet<>(main);
    branches.values().forEach(wanted::addAll);
    var level = ship.getLevel();
    for (var old : previous)
      if (!wanted.contains(old) && level.getBlockState(old).is(Create_bioships.INTESTINE.get()))
        level.setBlock(
            old,
            level.getBlockState(old).getValue(IntestineBlock.ENCASED)
                ? Create_bioships.FLESH.get().defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
            3);
    for (var next : wanted) {
      var old = level.getBlockState(next);
      if (!old.is(Create_bioships.INTESTINE.get()))
        level.setBlock(
            next,
            Create_bioships.INTESTINE
                .get()
                .defaultBlockState()
                .setValue(IntestineBlock.ENCASED, old.is(Create_bioships.FLESH.get())),
            3);
    }
    stomach.gutPath.clear();
    stomach.gutPath.addAll(main);
    stomach.sacPaths.clear();
    stomach.sacPaths.putAll(branches);
    stomach.connectedMouth = mouth;
    stomach.setChanged();
  }

  public static boolean offerFood(LivingCoreBlockEntity core, ItemStack stack, boolean inherited) {
    var stomach = stomach(core);
    if (stomach == null || !stomach.connected(core)) return false;
    if (stomach.enqueue(stack, inherited)) return true;
    if (inherited) return false;
    for (var entry : stomach.sacPaths.entrySet())
      if (pathIntact(core, stomach.getBlockPos(), entry.getKey(), entry.getValue())) {
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
      if (pathIntact(core, stomach.getBlockPos(), entry.getKey(), entry.getValue())) {
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
