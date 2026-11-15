package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The same needs ordering drives approaching a source and actually grabbing it. */
final class BioshipPriorities {
  record Objective(BlockPos pos, int rank) {}

  static int rank(LivingCoreBlockEntity core, BlockState state, BlockPos pos) {
    if (state.isAir()) return Integer.MAX_VALUE;
    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    var stomach = DigestiveSystem.stomach(core);
    boolean mouth = core.hasMouth();
    if (!mouth && (BioshipIntegration.isMaw(state) || id.equals("biomancy:bio_forge"))) return 0;
    if (stomach == null) {
      if (state.is(Create_bioships.STOMACH.get())) return 0;
      if ((id.equals("biomancy:decomposer") || id.equals("biomancy:digester"))
          && BioshipIntegration.blockEntities(core.ship()).stream()
              .noneMatch(e -> e.getBlockState().is(state.getBlock()))) return 0;
    }
    var entity = core.getLevel().getBlockEntity(pos);
    boolean container = id.equals("biomancy:storage_sac") || entity instanceof Container;
    var inventory =
        core.getLevel()
            .getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
    container |= inventory != null;
    if (container) {
      if (!mouth || stomach == null) return 4;
      boolean full = entity instanceof Container c && !c.isEmpty();
      if (inventory != null)
        for (int slot = 0; slot < inventory.getSlots(); slot++)
          full |= !inventory.getStackInSlot(slot).isEmpty();
      return full ? 1 : 2;
    }
    if (state.is(Create_bioships.FLESH.get()) || id.contains("flesh"))
      return core.missingBiomass() > 0 ? 3 : 5;
    return 5;
  }

  static Objective bestWorld(LivingCoreBlockEntity core) {
    if (core.ship() == null) return null;
    var level = (ServerLevel) core.getLevel();
    Vec3 here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    BlockPos origin = BlockPos.containing(here), best = null, previous = null;
    int bestRank = Integer.MAX_VALUE, previousRank = Integer.MAX_VALUE;
    double distance = 16 * 16;
    for (var pos : BlockPos.betweenClosed(origin.offset(-16, -3, -16), origin.offset(16, 3, 16))) {
      if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
          || BioshipIntegration.containing(level, pos) != null) continue;
      var state = level.getBlockState(pos);
      if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("biomancy")
          || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
      double next = Vec3.atCenterOf(pos).distanceToSqr(here);
      if (next > 16 * 16 || !core.vision().exposed(core, pos)) continue;
      int rank = rank(core, state, pos);
      if (pos.equals(core.supplyTarget)) {
        previous = pos.immutable();
        previousRank = rank;
      }
      if (rank < bestRank || rank == bestRank && next < distance) {
        best = pos.immutable();
        bestRank = rank;
        distance = next;
      }
    }
    if (previous != null && previousRank == bestRank) best = previous;
    core.supplyTarget = best;
    return best == null ? null : new Objective(best, bestRank);
  }
}
