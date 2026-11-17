package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;

/**
 * Detects broken gut graphs and proposes conserved obstruction moves to the existing goal queue.
 */
final class BioshipDigestiveRepair {
  record Move(BlockPos source, BlockPos target, int tubes) {}

  private record Audit(long at, int purchases, Move move) {}

  private static final Map<LivingCoreBlockEntity, Audit> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  static void invalidate(LivingCoreBlockEntity core) {
    CACHE.remove(core);
  }

  static int purchases(LivingCoreBlockEntity core) {
    return audit(core).purchases();
  }

  private static Audit audit(LivingCoreBlockEntity core) {
    long now = core.getLevel().getGameTime();
    var cached = CACHE.get(core);
    if (cached != null && now - cached.at() < 20) return cached;

    int needed = 0;
    for (var stomach : DigestiveSystem.stomachs(core))
      if (!stomach.connected(core)) {
        var path = DigestiveSystem.repairPath(core, stomach, null);
        if (path != null) needed += DigestiveSystem.repairPurchases(core, stomach, path);
      }
    var move = move(core);
    var value = new Audit(now, Math.max(needed, move == null ? 0 : move.tubes()), move);
    CACHE.put(core, value);
    return value;
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    var move = audit(core).move();
    if (move == null
        || !new MaterialCost(move.tubes(), move.tubes(), 0).funded(BioshipStorage.account(core)))
      return null;
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.REPAIR_DIGESTION,
        move.source(),
        core.getLevel().getBlockState(move.source()),
        0,
        0,
        0,
        1180,
        "Move an obstructing component to restore a disconnected stomach");
  }

  static boolean execute(LivingCoreBlockEntity core, BlockPos source) {
    var move = move(core);
    return move != null
        && move.source().equals(source)
        && ComponentTransport.begin(core.amoebaComponents(), source, move.target(), false);
  }

  private static Move move(LivingCoreBlockEntity core) {
    if (core.ship() == null || ComponentTransport.busy(core.amoebaComponents())) return null;
    var cells = BioshipStructure.cells(core.ship());
    for (var stomach : DigestiveSystem.stomachs(core)) {
      if (stomach.connected(core) || DigestiveSystem.repairPath(core, stomach, null) != null)
        continue;
      for (var source : stomach.gutPath) {
        var state = core.getLevel().getBlockState(source);
        if (state.isAir()
            || BioshipTissue.isTissue(state)
            || state.is(Create_bioships.INTESTINE.get())
            || state.is(Create_bioships.CORE.get())
            || state.is(Create_bioships.STOMACH.get())
            || BioshipIntegration.isMaw(state)
            || BioshipLegEvolution.leg(state)) continue;
        var route = DigestiveSystem.repairPath(core, stomach, source);
        if (route == null) continue;
        int tubes = DigestiveSystem.repairPurchases(core, stomach, route);
        var targets = new HashSet<BlockPos>();
        for (var cell : cells.keySet())
          for (var face : Direction.values()) targets.add(cell.relative(face));
        for (var target :
            targets.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(source))).toList()) {
          if (target.equals(source)
              || route.contains(target)
              || !core.getLevel().getBlockState(target).isAir()
              || !DigestiveSystem.permitsPlacement(core, target)) continue;
          if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(target)))
            continue;
          var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(target));
          if (!BioshipIntegration.growthSpace(core.getLevel(), BlockPos.containing(world)))
            continue;
          if (!BioshipSupport.safeMove(
              core,
              source,
              target,
              !BioshipTissue.host(core.getLevel().getBlockEntity(source)).isAir())) continue;
          if (!DigestiveSystem.reserveOrganRoute(core, source, target, false)) continue;
          return new Move(source, target, tubes);
        }
      }
    }
    return null;
  }
}
