package org.Azkerash.create_bioships.components.logic.brain;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** A passive Sable loading dependency view; all program and motor data remain grid-owned. */
public final class BrainLimbDependencies extends BlockEntity implements BlockEntitySubLevelActor {
  public BrainLimbDependencies(BlockPos pos, BlockState state) {
    super(ComponentRegistry.BRAIN_LIMB_ENTITY.get(), pos, state);
  }

  @Override
  public Iterable<SubLevel> sable$getLoadingDependencies() {
    if (!(level instanceof ServerLevel server)) return List.of();
    var body = CoreAssemblyRules.containing(server, worldPosition);
    if (body == null || body.getUserDataTag() == null) return List.of();
    var data = body.getUserDataTag().getCompound("create_bioships:modular_limbs");
    if (!data.contains("Anchor") || data.getLong("Anchor") != worldPosition.asLong())
      return List.of();
    var container = SubLevelContainer.getContainer(server);
    if (container == null) return List.of();
    var result = new ArrayList<SubLevel>();
    for (var id :
        body.getUserDataTag().getCompound(SubLevelData.PARTS).getCompound("Members").getAllKeys())
      try {
        var part = container.getSubLevel(UUID.fromString(id));
        if (part != null && !part.isRemoved()) result.add(part);
      } catch (IllegalArgumentException ignored) {
      }
    return result;
  }

  @Override
  public Iterable<SubLevel> sable$getConnectionDependencies() {
    return sable$getLoadingDependencies();
  }
}
