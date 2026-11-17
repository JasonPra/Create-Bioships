package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Audits physical parts plus owned parts temporarily carried, retracted or stashed. */
final class BioshipHealthPool {
  static int part(BlockState state, CompoundTag data, boolean host) {
    int points =
        BlockHealthTable.value(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    if (state.is(Create_bioships.INTESTINE.get()) && state.getValue(IntestineBlock.ENCASED))
      points += BlockHealthTable.COMPONENT;
    if (data != null) {
      if (host && data.getCompound("NeoForgeData").getBoolean("BioshipHostFlesh")) {
        var id = data.getCompound("NeoForgeData").getCompound("BioshipHostState").getString("Name");
        points += BlockHealthTable.value(id.isEmpty() ? "create_bioships:living_hull" : id);
      }
      if (state.is(Create_bioships.FEEDING_MOUTH.get()) && !data.getCompound("Larynx").isEmpty())
        points += BlockHealthTable.COMPONENT;
    }
    return points;
  }

  private static CompoundTag anatomy(net.minecraft.world.level.block.entity.BlockEntity actor) {
    if (actor == null || actor instanceof LivingCoreBlockEntity) return null;
    var tag = new CompoundTag();
    tag.put("NeoForgeData", actor.getPersistentData());
    if (actor instanceof FeedingMouthBlockEntity mouth) tag.put("Larynx", mouth.larynxCapsule());
    return tag;
  }

  static int total(
      LivingCoreBlockEntity core, Map<net.minecraft.core.BlockPos, BlockState> current) {
    long total = 0;
    var actors =
        new HashMap<
            net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity>();
    for (var actor : BioshipIntegration.blockEntities(core.ship()))
      actors.put(actor.getBlockPos(), actor);
    for (var entry : current.entrySet()) {
      var actor = actors.get(core.getBlockPos().offset(entry.getKey()));
      var tag = anatomy(actor);
      total += part(entry.getValue(), tag, true);
    }
    total += core.assimilation().health(core);
    for (var state : core.rescue().tissue().values()) total += part(state, null, true);
    var moves = new ArrayList<>(core.assimilation().arms());
    if (core.organMove() != null) moves.add(core.organMove());
    var seen = new HashSet<java.util.UUID>();
    for (var move : moves) {
      if (move.external() || !seen.add(move.cargo())) continue;
      var cargo =
          dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(core.getLevel())
              .getSubLevel(move.cargo());
      if (!(cargo instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ship) || ship.isRemoved())
        continue;
      for (var entry : BioshipStructure.cells(ship).entrySet()) {
        var actor = BioshipContainerState.entity(core.getLevel(), ship, entry.getKey());
        total += part(entry.getValue(), anatomy(actor), !move.restoreFlesh());
      }
    }
    return (int) Math.clamp(total, BlockHealthTable.CORE, Integer.MAX_VALUE);
  }

  private BioshipHealthPool() {}
}
