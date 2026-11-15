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
    return rank(core, state, pos, true);
  }

  private static int rank(
      LivingCoreBlockEntity core, BlockState state, BlockPos pos, boolean observed) {
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
    var entity = observed ? core.getLevel().getBlockEntity(pos) : null;
    boolean container =
        id.equals("biomancy:storage_sac")
            || entity instanceof Container
            || !observed && state.hasBlockEntity();
    var inventory =
        observed
            ? core.getLevel()
                .getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null)
            : null;
    container |= inventory != null;
    if (container) {
      if (!mouth || stomach == null) return 4;
      boolean full =
          entity instanceof Container c && !c.isEmpty()
              || !observed && core.vision().rememberedFull(pos);
      if (inventory != null)
        for (int slot = 0; slot < inventory.getSlots(); slot++)
          full |= !inventory.getStackInSlot(slot).isEmpty();
      return full ? 1 : 2;
    }
    if (state.is(Create_bioships.FLESH.get()) || id.contains("flesh"))
      return core.missingBiomass() > 0 ? 3 : 5;
    return 5;
  }

  private record Sources(long at, BlockPos center, int range, java.util.List<BlockPos> positions) {}

  private static final java.util.Map<LivingCoreBlockEntity, Sources> SOURCE_CACHE =
      new java.util.WeakHashMap<>();

  static boolean supply(BlockState state) {
    return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("biomancy")
        && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock);
  }

  private static java.util.List<BlockPos> sources(
      LivingCoreBlockEntity core, Vec3 here, int range) {
    var level = (ServerLevel) core.getLevel();
    var center = BlockPos.containing(here);
    var saved = SOURCE_CACHE.get(core);
    boolean reuse =
        saved != null
            && saved.range == range
            && saved.center.distSqr(center) < 16
            && level.getGameTime() - saved.at < 20;
    var positions = new java.util.LinkedHashSet<BlockPos>();
    if (reuse) positions.addAll(saved.positions);
    int radius = (range + 15) / 16;
    for (int cx = (center.getX() >> 4) - radius; cx <= (center.getX() >> 4) + radius; cx++)
      for (int cz = (center.getZ() >> 4) - radius; cz <= (center.getZ() >> 4) + radius; cz++) {
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) continue;
        for (var entry : chunk.getBlockEntities().entrySet())
          if (supply(entry.getValue().getBlockState())) positions.add(entry.getKey());
        if (reuse) continue;
        var sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
          var section = sections[index];
          int bottom = chunk.getSectionYFromSectionIndex(index) * 16;
          if (bottom > center.getY() + range
              || bottom + 15 < center.getY() - range
              || section.hasOnlyAir()
              || !section.maybeHas(BioshipPriorities::supply)) continue;
          for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
              for (int x = 0; x < 16; x++)
                if (supply(section.getBlockState(x, y, z)))
                  positions.add(new BlockPos(cx * 16 + x, bottom + y, cz * 16 + z));
        }
      }
    var result = java.util.List.copyOf(positions);
    if (!reuse) SOURCE_CACHE.put(core, new Sources(level.getGameTime(), center, range, result));
    return result;
  }

  static java.util.List<Objective> worldObjectives(LivingCoreBlockEntity core) {
    if (core.ship() == null) return java.util.List.of();
    var level = (ServerLevel) core.getLevel();
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    int range = core.vision().maximumDistance(core);
    var candidates = new java.util.LinkedHashSet<>(sources(core, here, range));
    candidates.addAll(core.vision().rememberedSources());
    var result = new java.util.ArrayList<Objective>();
    for (var pos : candidates) {
      if (!core.crawl().reachableGoal(pos)
          || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
          || BioshipIntegration.containing(level, pos) != null) continue;
      var state = core.vision().sourceState(core, pos);
      if (state == null || !supply(state)) continue;
      if (Vec3.atCenterOf(pos).distanceToSqr(here) > (double) range * range
          && !core.vision().rememberedSources().contains(pos)) continue;
      int rank = rank(core, state, pos, core.vision().exposed(core, pos));
      if (rank > 0 && !core.vision().surveyed(core)) continue;
      result.add(new Objective(pos.immutable(), rank));
    }
    return result;
  }

  static Objective bestWorld(LivingCoreBlockEntity core) {
    return core.brain().bestWorld(core);
  }
}
