package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

/** Read-only digestive recovery options and safe sacrificial material selection. */
final class BioshipRecovery {
  record Options(boolean mouth, boolean stomach) {}

  static Options options(LivingCoreBlockEntity core) {
    if (core.hasMouth() && DigestiveSystem.stomach(core) != null) return new Options(false, false);
    return available(core);
  }

  static Options available(LivingCoreBlockEntity core) {
    var ids = new HashSet<String>();
    for (var entity : BioshipIntegration.blockEntities(core.ship()))
      ids.add(BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString());
    for (var source : BioshipPriorities.worldObjectives(core)) {
      var state = core.vision().sourceState(core, source.pos());
      if (state != null) ids.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }
    return new Options(
        ids.contains("biomancy:bio_forge") || ids.contains("biomancy:maw_hopper"),
        ids.contains("biomancy:decomposer") && ids.contains("biomancy:digester"));
  }

  static BlockPos sacrifice(
      LivingCoreBlockEntity core, Map<String, Integer> reserve, Options options) {
    boolean mouth = !core.hasMouth() && !options.mouth(),
        stomach = DigestiveSystem.stomach(core) == null && !options.stomach();
    if (!mouth && !stomach) return null;
    int flesh = (mouth ? 8 : 0) + (stomach ? 16 : 0),
        fiber = (mouth ? 4 : 0) + (stomach ? 8 : 0),
        bone = (mouth ? 2 : 0) + (stomach ? 4 : 0);
    var cells = new HashSet<>(BioshipDecay.blocks(core.ship()));
    if (!core.hasMouth() && options.mouth()
        || DigestiveSystem.stomach(core) == null && options.stomach()) return null;
    var safe =
        cells.stream()
            .filter(p -> eligible(core, p) && connectedWithout(cells, p, core.getBlockPos()))
            .toList();
    long fleshParts = safe.size();
    long bones =
        safe.stream()
            .filter(p -> BioshipScavenging.frameMaterial(core.getLevel().getBlockState(p)))
            .count();
    long fibers =
        safe.stream()
            .filter(p -> BioshipScavenging.fiberMaterial(core.getLevel().getBlockState(p)))
            .count();
    var fractions = core.scavenging().save();
    if (reserve.getOrDefault(MaterialCost.FLESH, 0)
                + (fleshParts + fractions.getInt("FleshRemainder")) / 2
            < flesh
        || reserve.getOrDefault(MaterialCost.BONE, 0)
                + (bones + fractions.getInt("BoneRemainder")) / 4
            < bone
        || reserve.getOrDefault(MaterialCost.FIBER, 0)
                + (fibers + fractions.getInt("FiberRemainder")) / 4
            < fiber) return null;

    for (var pos :
        cells.stream()
            .sorted(Comparator.comparingDouble(p -> p.distSqr(core.getBlockPos())))
            .toList()) {
      var state = core.getLevel().getBlockState(pos);
      if (!eligible(core, pos)) continue;
      boolean useful =
          reserve.getOrDefault(MaterialCost.FLESH, 0) < flesh
              || reserve.getOrDefault(MaterialCost.BONE, 0) < bone
                  && BioshipScavenging.frameMaterial(state)
              || reserve.getOrDefault(MaterialCost.FIBER, 0) < fiber
                  && BioshipScavenging.fiberMaterial(state);
      if (!useful || !connectedWithout(cells, pos, core.getBlockPos())) continue;
      var existing = DigestiveSystem.stomach(core);
      if (existing != null && existing.gutPath.contains(pos)) continue;
      return pos;
    }
    return null;
  }

  private static boolean eligible(LivingCoreBlockEntity core, BlockPos pos) {
    var state = core.getLevel().getBlockState(pos);
    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    if (pos.equals(core.getBlockPos())
        || BioshipIntegration.isMaw(state)
        || state.is(Create_bioships.STOMACH.get())
        || state.is(Create_bioships.INTESTINE.get())) return false;
    if (id.equals("biomancy:bio_forge")
        || id.equals("biomancy:decomposer")
        || id.equals("biomancy:digester")) return false;
    if (core.canFly() && state.is(Create_bioships.TENTACLE.get())) return false;
    boolean disposable =
        BioshipTissue.isTissue(state)
            || state.is(Create_bioships.CARAPACE.get())
            || state.is(Create_bioships.TENTACLE.get())
            || state.is(Create_bioships.ONE_WAY_WINDOW.get())
            || state.hasBlockEntity() && id.startsWith("biomancy:");
    if (!disposable || !BioshipScavenging.emptyComponent(core, pos)) return false;
    var entity = core.getLevel().getBlockEntity(pos);
    if (entity != null && entity.getPersistentData().getBoolean("BioshipManualPlacement"))
      return false;
    var stomach = DigestiveSystem.stomach(core);
    return stomach == null || !stomach.gutPath.contains(pos);
  }

  private static boolean connectedWithout(Set<BlockPos> cells, BlockPos removed, BlockPos root) {
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    seen.add(root);
    queue.add(root);
    while (!queue.isEmpty()) {
      var point = queue.removeFirst();
      for (var face : net.minecraft.core.Direction.values()) {
        var next = point.relative(face);
        if (!next.equals(removed) && cells.contains(next) && seen.add(next)) queue.addLast(next);
      }
    }
    return seen.size() == cells.size() - 1;
  }
}
