package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;
import org.Azkerash.create_bioships.registry.Create_bioships;

/**
 * Read-only twenty-tick anatomy audit; all rearrangement is executed through conserved transport.
 */
final class BioshipAnatomy {
  record Move(BlockPos source, BlockPos target, String reason, int urgency) {}

  private record Audit(long at, Move move) {}

  private static final Map<LivingCoreBlockEntity, Audit> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());
  static final MaterialCost ARMOR_COST = new MaterialCost(8, 4, 2);

  static void observe(LivingCoreBlockEntity core) {
    audit(core);
  }

  static void invalidate(LivingCoreBlockEntity core) {
    CACHE.remove(core);
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    var move = audit(core).move();
    if (move == null) return null;
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.REPOSITION_ANATOMY,
        move.source(),
        core.getLevel().getBlockState(move.source()),
        0,
        0,
        0,
        move.urgency(),
        move.reason());
  }

  static boolean execute(LivingCoreBlockEntity core, BlockPos source) {
    var move = audit(core).move();
    if (move == null
        || !source.equals(move.source())
        || !canMove(core, move.source(), move.target())) {
      CACHE.remove(core);
      return false;
    }
    boolean moved = ComponentTransport.begin(core.amoebaComponents(), source, move.target(), false);
    if (moved) CACHE.remove(core);
    return moved;
  }

  private static Audit audit(LivingCoreBlockEntity core) {
    long now = core.getLevel().getGameTime();
    var cached = CACHE.get(core);
    if (cached != null && now - cached.at() < 20) return cached;
    var cells = BioshipStructure.cells(core.ship());
    Move move = opening(core, cells);
    // Invalid appendages are repaired by the coordinated batch path.
    if (move == null
        && !needsBatchRepair(core)
        && DigestiveSystem.functional(core)
        && BioshipLegGrowth.missing(core) == 0) move = compact(core, cells);
    var value = new Audit(now, move);
    CACHE.put(core, value);
    return value;
  }

  record RepairPart(BlockPos source, BlockPos target, BlockState state) {}

  static boolean blockedMouths(LivingCoreBlockEntity core) {
    var cells = BioshipStructure.cells(core.ship());
    return DigestiveSystem.mouths(core).stream()
        .anyMatch(
            p -> {
              var state = cells.get(p);
              var front = p.relative(state.getValue(FeedingMouthBlock.FACING));
              return cells.containsKey(front)
                  && !cells.get(front).getCollisionShape(core.getLevel(), front).isEmpty();
            });
  }

  static boolean invalidAttachment(
      LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells, BlockPos pos, BlockState state) {
    if (BioshipLegEvolution.leg(state))
      return !AmoebaLegBlock.mounted(cells, pos, state)
          || cells.containsKey(pos.relative(state.getValue(AmoebaLegBlock.FACING)));
    if (state.is(Create_bioships.SURFACE_EYE.get())) {
      var face = state.getValue(SurfaceEyeBlock.FACING);
      var host = pos.relative(face.getOpposite());
      return !SurfaceEyeBlock.canHostEye(cells.get(host), host, face)
          || cells.containsKey(pos.relative(face));
    }
    var entity = core.getLevel().getBlockEntity(pos);
    if (entity != null
        && org.Azkerash.create_bioships.integration.biomancy.NativeStorage.isStorage(entity)) {
      var face =
          state.getValue(
              net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
      boolean pipe = false, aligned = false;
      for (var direction : Direction.values()) {
        var neighbor = cells.get(pos.relative(direction));
        if (neighbor != null && neighbor.is(Create_bioships.INTESTINE.get())) {
          pipe = true;
          if (face == direction.getOpposite()) aligned = true;
        }
      }
      return pipe && !aligned || DigestiveSystem.needsStorageRepair(core, pos);
    }
    return false;
  }

  static boolean blockedGill(Map<BlockPos, BlockState> cells, BlockPos pos, BlockState state) {
    return state.is(Create_bioships.GILLS.get())
        && cells.containsKey(pos.relative(state.getValue(RespiratoryOrganBlock.FACING)));
  }

  private static boolean primaryNeedsRepair(
      LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    var primary = core.mouthBlock();
    var state = primary == null ? null : cells.get(primary);
    if (!core.hatchling() || state == null || !state.is(Create_bioships.FEEDING_MOUTH.get()))
      return false;
    var entity = core.getLevel().getBlockEntity(primary);
    return (entity == null || !entity.getPersistentData().getBoolean("BioshipManualPlacement"))
        && (state.getValue(FeedingMouthBlock.FACING) != Direction.NORTH
            || primary.getZ() >= core.structuralOrigin().getZ());
  }

  static boolean needsBatchRepair(LivingCoreBlockEntity core) {
    if (blockedMouths(core)) return true;
    var cells = BioshipStructure.cells(core.ship());
    if (primaryNeedsRepair(core, cells)) return true;
    return cells.entrySet().stream()
        .anyMatch(
            e ->
                blockedGill(cells, e.getKey(), e.getValue())
                    || invalidAttachment(core, cells, e.getKey(), e.getValue()));
  }

  /** One immutable arrangement is selected before any inventory leaves its owner. */
  static List<RepairPart> repairPlan(LivingCoreBlockEntity core) {
    if (!needsBatchRepair(core)) return List.of();
    var original = BioshipStructure.cells(core.ship());
    boolean mouthRepair = blockedMouths(core);
    var primary = core.mouthBlock();
    mouthRepair |= primaryNeedsRepair(core, original);
    var adjusted = new LinkedHashMap<BlockPos, BlockState>();
    for (var e : original.entrySet())
      if (blockedGill(original, e.getKey(), e.getValue())) {
        for (var face : Direction.values()) {
          if (original.containsKey(e.getKey().relative(face))) continue;
          var candidate = e.getValue().setValue(RespiratoryOrganBlock.FACING, face);
          if (core.aquatic().wet() && !BioshipRespiration.exposedGill(core, e.getKey(), candidate))
            continue;
          adjusted.put(e.getKey(), candidate);
          break;
        }
      }
    var selected = new ArrayList<BlockPos>();
    for (var e : original.entrySet()) {
      var state = e.getValue();
      boolean organ =
          BioshipTissue.isTissue(state)
                  && DigestiveSystem.mouths(core).stream()
                      .anyMatch(p -> p.distManhattan(e.getKey()) == 1)
              || (mouthOpening(core, e.getKey())
                  && !state.is(Create_bioships.CORE.get())
                  && !state.is(Create_bioships.GILLS.get())
                  && !state.is(Create_bioships.LUNGS.get())
                  && !(state.is(Create_bioships.STOMACH.get())
                      && state.getValue(StomachBlock.ROLE) != StomachBlock.Role.NORMAL))
              || state.is(Create_bioships.FEEDING_MOUTH.get())
              || BioshipLegEvolution.leg(state)
              || state.is(Create_bioships.SURFACE_EYE.get())
              || state.is(Create_bioships.STOMACH.get())
                  && state.getValue(StomachBlock.ROLE) == StomachBlock.Role.NORMAL
              || core.getLevel().getBlockEntity(e.getKey()) != null
                  && org.Azkerash.create_bioships.integration.biomancy.NativeStorage.isStorage(
                      core.getLevel().getBlockEntity(e.getKey()));
      boolean grouped =
          state.is(Create_bioships.STOMACH.get())
              && StomachMultiblock.containing(core, e.getKey()) != null;
      boolean gillBlocker =
          original.entrySet().stream()
              .anyMatch(
                  g ->
                      blockedGill(original, g.getKey(), g.getValue())
                          && !adjusted.containsKey(g.getKey())
                          && g.getKey()
                              .relative(g.getValue().getValue(RespiratoryOrganBlock.FACING))
                              .equals(e.getKey()));
      boolean unmovable =
          state.is(Create_bioships.CORE.get())
              || state.is(Create_bioships.GILLS.get())
              || state.is(Create_bioships.LUNGS.get());
      if (!grouped
          && !unmovable
          && (mouthRepair && organ
              || invalidAttachment(core, original, e.getKey(), state)
              || gillBlocker)) selected.add(e.getKey());
    }
    if (selected.isEmpty() && adjusted.isEmpty() || selected.size() + adjusted.size() > 64)
      return List.of();
    // Respiration organs remain deployed. A breath-limited submerged body cannot begin a long
    // recall.
    if (core.aquatic().wet()
        && !BioshipRespiration.activeGills(core)
        && selected.size()
                * org.Azkerash.create_bioships.amoeba.merging.ComponentMerging.DURATION_TICKS
                * 2
            > core.aquatic().air() - 60
        && adjusted.isEmpty()
        && original.values().stream().noneMatch(st -> st.is(Create_bioships.GILLS.get())))
      return List.of();
    selected.sort(
        Comparator.<BlockPos>comparingInt(
                p -> {
                  var state = original.get(p);
                  return state.is(Create_bioships.FEEDING_MOUTH.get())
                      ? 0
                      : BioshipLegEvolution.leg(state)
                              || state.is(Create_bioships.SURFACE_EYE.get())
                          ? 2
                          : 1;
                })
            .thenComparing(Comparator.naturalOrder()));
    var planned = new LinkedHashMap<>(original);
    planned.putAll(adjusted);
    selected.forEach(planned::remove);
    for (var p : selected) {
      var host = BioshipTissue.host(core.getLevel().getBlockEntity(p));
      if (!host.isAir()) planned.put(p, host);
    }
    var parts = new ArrayList<RepairPart>();
    var openings = new HashSet<BlockPos>();
    for (var e : planned.entrySet())
      if (e.getValue().is(Create_bioships.GILLS.get()))
        openings.add(e.getKey().relative(e.getValue().getValue(RespiratoryOrganBlock.FACING)));
    int attempts = 0, quotes = 0;
    for (var source : selected) {
      var state = original.get(source);
      boolean mouth = state.is(Create_bioships.FEEDING_MOUTH.get());
      var candidates = new LinkedHashSet<BlockPos>();
      boolean storageRepair = DigestiveSystem.needsStorageRepair(core, source);
      var storageTarget = storageRepair ? DigestiveSystem.storagePort(core, source) : null;
      if (storageTarget != null) candidates.add(storageTarget);
      candidates.add(source);
      for (var p : original.keySet())
        for (var face : Direction.values()) candidates.add(p.relative(face));
      var ordered =
          candidates.stream()
              .sorted(
                  Comparator.<BlockPos>comparingDouble(
                          p ->
                              mouth
                                      && source.equals(primary)
                                      && p.equals(core.structuralOrigin().north())
                                  ? -3
                                  : p.equals(storageTarget)
                                      ? -2
                                      : p.equals(source) ? -1 : p.distSqr(source))
                      .thenComparing(Comparator.naturalOrder()))
              .limit(256)
              .toList();
      RepairPart chosen = null;
      for (var target : ordered) {
        if (++attempts > 2048) return List.of();
        if (openings.contains(target)
            || planned.containsKey(target)
                && !(target.equals(source) && BioshipTissue.isTissue(planned.get(target)))
            || !core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(target)))
          continue;
        if (target.distManhattan(core.structuralOrigin()) > 24) continue;
        if (java.util.Arrays.stream(Direction.values())
            .noneMatch(d -> planned.containsKey(target.relative(d)))) continue;
        var occupied = core.getLevel().getBlockState(target);
        if (!selected.contains(target) && !occupied.isAir()) continue;
        var world =
            BlockPos.containing(
                core.ship().logicalPose().transformPosition(Vec3.atCenterOf(target)));
        if (!BioshipIntegration.aquaticGrowthSpace(core.getLevel(), world)) continue;
        for (var face : Direction.values()) {
          var oriented = state;
          if (mouth) {
            // Keep a front mouth on hatchlings; additional mouths may cover other directions.
            if (source.equals(core.mouthBlock()) && core.hatchling() && face != Direction.NORTH)
              continue;
            var front = target.relative(face);
            if (planned.containsKey(front)
                || original.containsKey(front) && !selected.contains(front)
                || !core.getLevel()
                    .getBlockState(
                        BlockPos.containing(
                            core.ship().logicalPose().transformPosition(Vec3.atCenterOf(front))))
                    .getCollisionShape(core.getLevel(), world)
                    .isEmpty()) continue;
            oriented = state.setValue(FeedingMouthBlock.FACING, face);
          } else if (BioshipLegEvolution.leg(state)
              || state.is(Create_bioships.SURFACE_EYE.get())) {
            if (planned.containsKey(target.relative(face))) continue;
            var support = target.relative(face.getOpposite());
            if (BioshipLegEvolution.leg(state)
                ? !AmoebaLegBlock.canHostLeg(planned.get(support), support, face)
                : !SurfaceEyeBlock.canHostEye(planned.get(support), support, face)) continue;
            oriented =
                state.setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                    face);
          }
          var entity = core.getLevel().getBlockEntity(source);
          if (entity != null
              && org.Azkerash.create_bioships.integration.biomancy.NativeStorage.isStorage(
                  entity)) {
            var facing =
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
            for (var d : Direction.values())
              if (planned.get(target.relative(d)) != null
                  && planned.get(target.relative(d)).is(Create_bioships.INTESTINE.get())) {
                oriented = state.setValue(facing, d.getOpposite());
                break;
              }
          }
          if (storageRepair
              && (target.equals(source)
                  || !DigestiveSystem.reserveOrganRoute(core, source, target, false))) continue;
          if (mouth && ++quotes > 64) return List.of();
          if (mouth && !DigestiveSystem.reserveOrganRoute(core, source, target, false)) continue;
          chosen = new RepairPart(source, target, oriented);
          break;
        }
        if (chosen != null) break;
      }
      if (chosen == null) {
        return List.of();
      }
      parts.add(chosen);
      planned.put(chosen.target(), chosen.state());
      if (mouth)
        openings.add(chosen.target().relative(chosen.state().getValue(FeedingMouthBlock.FACING)));
    }
    if (!core.aquatic().swimming() && !layoutStable(core, planned)) {
      return List.of();
    }
    // All selected organs must remain connected to the residual body after deployment.
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    queue.add(core.getBlockPos());
    seen.add(core.getBlockPos());
    while (!queue.isEmpty()) {
      var n = queue.removeFirst();
      for (var f : Direction.values()) {
        var next = n.relative(f);
        if (planned.containsKey(next) && seen.add(next)) queue.add(next);
      }
    }
    if (seen.size() != planned.size()) {
      return List.of();
    }
    // Recall only a sequence that leaves the physical body connected after every departure.
    var recall = new ArrayList<RepairPart>();
    var pending = new ArrayList<>(parts);
    var residual = new HashSet<>(original.keySet());
    while (!pending.isEmpty()) {
      RepairPart leaf = null;
      for (var part : pending) {
        var remaining = new HashSet<>(residual);
        remaining.remove(part.source());
        if (!BioshipTissue.host(core.getLevel().getBlockEntity(part.source())).isAir())
          remaining.add(part.source());
        if (connectedCells(core.getBlockPos(), remaining)) {
          leaf = part;
          break;
        }
      }
      if (leaf == null) return List.of();
      recall.add(leaf);
      pending.remove(leaf);
      residual.remove(leaf.source());
      if (!BioshipTissue.host(core.getLevel().getBlockEntity(leaf.source())).isAir())
        residual.add(leaf.source());
    }
    if (core.aquatic().wet() && !BioshipRespiration.activeGills(core) && adjusted.isEmpty()) {
      int breathAfter =
          selected.size()
                  * org.Azkerash.create_bioships.amoeba.merging.ComponentMerging.DURATION_TICKS
                  * 2
              + 60;
      for (int i = 0; i < recall.size(); i++) {
        var part = recall.get(i);
        if (original.entrySet().stream()
            .anyMatch(
                e ->
                    blockedGill(original, e.getKey(), e.getValue())
                        && e.getKey()
                            .relative(e.getValue().getValue(RespiratoryOrganBlock.FACING))
                            .equals(part.source())))
          breathAfter =
              Math.min(
                  breathAfter,
                  (i + 1)
                          * (org.Azkerash.create_bioships
                                  .amoeba
                                  .merging
                                  .ComponentMerging
                                  .DURATION_TICKS
                              + 2)
                      + 20);
      }
      if (breathAfter >= core.aquatic().air()) return List.of();
    }
    for (var e : adjusted.entrySet())
      recall.add(new RepairPart(e.getKey(), e.getKey(), e.getValue()));
    return deploymentOrder(core, recall).length == recall.size() ? List.copyOf(recall) : List.of();
  }

  static int[] deploymentOrder(LivingCoreBlockEntity core, List<RepairPart> plan) {
    var present = new LinkedHashMap<>(BioshipStructure.cells(core.ship()));
    for (var p : plan) {
      if (p.state().is(Create_bioships.GILLS.get())) continue;
      present.remove(p.source());
      var host = BioshipTissue.host(core.getLevel().getBlockEntity(p.source()));
      if (!host.isAir()) present.put(p.source(), host);
    }
    var pending = new ArrayList<Integer>();
    for (int i = 0; i < plan.size(); i++) pending.add(i);
    var order = new ArrayList<Integer>();
    while (!pending.isEmpty()) {
      Integer next = null;
      for (var slot : pending) {
        var p = plan.get(slot);
        if (java.util.Arrays.stream(Direction.values())
            .noneMatch(d -> present.containsKey(p.target().relative(d)))) continue;
        if (BioshipLegEvolution.leg(p.state()) || p.state().is(Create_bioships.SURFACE_EYE.get())) {
          var face =
              p.state()
                  .getValue(
                      net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
          var host = p.target().relative(face.getOpposite());
          if (BioshipLegEvolution.leg(p.state())
              ? !AmoebaLegBlock.canHostLeg(present.get(host), host, face)
              : !SurfaceEyeBlock.canHostEye(present.get(host), host, face)) continue;
        }
        next = slot;
        break;
      }
      if (next == null) return new int[0];
      order.add(next);
      pending.remove(next);
      var p = plan.get(next);
      present.put(p.target(), p.state());
    }
    return order.stream().mapToInt(Integer::intValue).toArray();
  }

  private static boolean connectedCells(BlockPos root, Set<BlockPos> cells) {
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    seen.add(root);
    queue.add(root);
    while (!queue.isEmpty()) {
      var p = queue.removeFirst();
      for (var face : Direction.values()) {
        var n = p.relative(face);
        if (cells.contains(n) && seen.add(n)) queue.add(n);
      }
    }
    return seen.size() == cells.size();
  }

  private static boolean layoutStable(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    return BioshipSupport.layout(core, cells).stable();
  }

  static boolean mouthOpening(LivingCoreBlockEntity core, BlockPos pos) {
    return DigestiveSystem.mouths(core).stream()
        .anyMatch(
            mouth -> {
              var state = core.getLevel().getBlockState(mouth);
              return state.hasProperty(FeedingMouthBlock.FACING)
                  && mouth.relative(state.getValue(FeedingMouthBlock.FACING)).equals(pos);
            });
  }

  private static boolean free(LivingCoreBlockEntity core, BlockPos target) {
    if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(target))
        || !core.getLevel().getBlockState(target).isAir()
        || !DigestiveSystem.permitsPlacement(core, target)
        || mouthOpening(core, target)) return false;
    var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(target));
    return BioshipIntegration.growthSpace(core.getLevel(), BlockPos.containing(world));
  }

  private static boolean canMove(LivingCoreBlockEntity core, BlockPos source, BlockPos target) {
    return free(core, target)
        && BioshipSupport.safeMove(
            core,
            source,
            target,
            !BioshipTissue.host(core.getLevel().getBlockEntity(source)).isAir())
        && DigestiveSystem.reserveOrganRoute(core, source, target, false);
  }

  private static List<BlockPos> ports(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    var result = new HashSet<BlockPos>();
    for (var cell : cells.keySet())
      for (var face : Direction.values()) result.add(cell.relative(face));
    var center =
        Vec3.atLowerCornerOf(core.structuralOrigin()).add(BioshipSupport.layout(core).center());
    return result.stream()
        .filter(p -> !cells.containsKey(p) && free(core, p))
        .sorted(
            Comparator.<BlockPos>comparingDouble(p -> p.distToCenterSqr(center))
                .thenComparing(Comparator.naturalOrder()))
        .toList();
  }

  private static Move opening(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    if (core.hasMouth()
        && !DigestiveSystem.stomachs(core).isEmpty()
        && !BioshipRespiration.needsLungs(core)) return null;
    for (var face : Direction.values())
      if (free(core, core.getBlockPos().relative(face))) return null;
    for (var face : Direction.values()) {
      var source = core.getBlockPos().relative(face);
      if (!BioshipTissue.isTissue(core.getLevel().getBlockState(source))
          || !DigestiveSystem.permitsPlacement(core, source)) continue;
      for (var target : ports(core, cells))
        if (canMove(core, source, target))
          return new Move(
              source,
              target,
              "Move covering tissue or tier-one armor to regrow vital organs",
              1140);
    }
    return null;
  }

  static int anchorIndex(
      BlockPos origin, BlockPos socket, Direction face, BioshipSupport.Layout layout) {
    var root =
        Vec3.atCenterOf(socket)
            .subtract(Vec3.atLowerCornerOf(origin))
            .subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(.48));
    for (int index = 0; index < layout.legs().size(); index++) {
      var leg = layout.legs().get(index);
      if (!leg.bootstrap() && leg.face() == face && leg.root().distanceToSqr(root) < 1.0e-8)
        return index;
    }
    return -1;
  }

  static Move compact(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    var targets = ports(core, cells);
    for (var entry : cells.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
      var state = entry.getValue();
      if (!state.is(Create_bioships.STOMACH.get())
          && !state.is(Create_bioships.FEEDING_MOUTH.get())
          && !state.is(Create_bioships.LUNGS.get())) continue;
      int distance = entry.getKey().distManhattan(core.structuralOrigin());
      if (distance <= 2) continue;
      for (var target : targets) {
        if (target.distManhattan(core.structuralOrigin()) > distance - 2
            || !canMove(core, entry.getKey(), target)) continue;
        return new Move(
            entry.getKey(), target, "Compact vital organs before adding protective flesh", 920);
      }
      // A removable covering block must not permanently prevent internal expansion.
      for (var cover : cells.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        if (!BioshipTissue.isTissue(cover.getValue())
            || !DigestiveSystem.permitsPlacement(core, cover.getKey())
            || cover.getKey().distManhattan(core.structuralOrigin()) > distance - 2
            || mouthOpening(core, cover.getKey())
            // Clearing a chamber is useful only if the waiting organ can actually occupy it.
            || !BioshipSupport.safeMove(
                core,
                entry.getKey(),
                cover.getKey(),
                !BioshipTissue.host(core.getLevel().getBlockEntity(entry.getKey())).isAir())
            || !DigestiveSystem.reserveOrganRoute(core, entry.getKey(), cover.getKey(), false))
          continue;
        for (var target : targets)
          if (target.distManhattan(core.structuralOrigin()) > distance - 2
              && canMove(core, cover.getKey(), target))
            return new Move(
                cover.getKey(),
                target,
                "Shift protective flesh or armor to make room for compact internals",
                920);
      }
    }
    return null;
  }

  static BioshipAdaptation.Proposal protection(LivingCoreBlockEntity core) {
    if (!DigestiveSystem.functional(core)
        || BioshipLegGrowth.missing(core) > 0
        || audit(core).move() != null
        || needsBatchRepair(core)) return null;
    var cells = BioshipStructure.cells(core.ship());
    var cover = new LinkedHashSet<BlockPos>();
    for (var entry : cells.entrySet()) {
      var state = entry.getValue();
      boolean mouth = state.is(Create_bioships.FEEDING_MOUTH.get());
      boolean gut = state.is(Create_bioships.INTESTINE.get());
      if (!entry.getKey().equals(core.getBlockPos())
          && !mouth
          && !state.is(Create_bioships.STOMACH.get())
          && !state.is(Create_bioships.LUNGS.get())
          && !gut) continue;
      if (gut && state.getValue(IntestineBlock.ENCASED)) continue;
      for (var face : Direction.values()) {
        if (mouth && face == state.getValue(FeedingMouthBlock.FACING)) continue;
        var pos = entry.getKey().relative(face);
        if (!cells.containsKey(pos) && !mouthOpening(core, pos)) {
          var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
          if (BioshipIntegration.growthSpace(core.getLevel(), BlockPos.containing(world)))
            cover.add(pos);
        }
      }
    }
    boolean covered = cover.isEmpty();
    var positions =
        covered
            ? cells.entrySet().stream()
                .filter(e -> e.getValue().is(BioshipTissue.body()))
                .map(Map.Entry::getKey)
                .toList()
            : List.copyOf(cover);
    var state = (covered ? Create_bioships.FLESH.get() : BioshipTissue.body()).defaultBlockState();
    var cost = covered ? ARMOR_COST : BioshipTissue.GROWTH_COST;
    for (var pos : positions) {
      if (!DigestiveSystem.permitsPlacement(core, pos) || !covered && !free(core, pos)) continue;
      if (!BioshipSupport.safeAddition(core, pos, state)) continue;
      double old =
          core.getLevel().getBlockState(pos).isAir()
              ? 0
              : core.crawl().blockLoad(core, pos, core.getLevel().getBlockState(pos));
      double load = core.crawl().carriedLoad(core) + core.crawl().blockLoad(core, pos, state) - old;
      int tiers = 0;
      while (core.crawl().legTier() + tiers < 3
          && load > BioshipCrawl.capacityFor(core.crawl().legTier() + tiers)) tiers++;
      if (load > BioshipCrawl.capacityFor(core.crawl().legTier() + tiers)) continue;
      var complete =
          new MaterialCost(
              cost.flesh() + 8 * tiers, cost.fiber() + 4 * tiers, cost.bone() + 2 * tiers);
      if (!EmergencyReserve.after(core, complete)) continue;
      return new BioshipAdaptation.Proposal(
          tiers > 0 ? BioshipBrain.Action.STRENGTHEN_LEGS : BioshipBrain.Action.FORTIFY,
          pos,
          state,
          cost.flesh(),
          cost.fiber(),
          cost.bone(),
          covered ? 280 : 350,
          tiers > 0
              ? "Strengthen legs before adding protective mass"
              : covered
                  ? "Armor protected internals with tier-one living hull"
                  : "Cover compact vital organs with paid packed flesh");
    }
    if (covered
        && positions.isEmpty()
        && core.crawl().legTier() < 1
        && EmergencyReserve.after(core, new MaterialCost(8, 4, 2)))
      return new BioshipAdaptation.Proposal(
          BioshipBrain.Action.STRENGTHEN_LEGS,
          core.getBlockPos(),
          core.getBlockState(),
          8,
          4,
          2,
          270,
          "Improve locomotion strength after protecting and armoring the internals");
    return null;
  }
}
