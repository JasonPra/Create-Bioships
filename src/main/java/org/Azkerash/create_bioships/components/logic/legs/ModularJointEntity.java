package org.Azkerash.create_bioships.components.logic.legs;

import dev.letitwalk.content.LegJointBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Native joint configuration; no per-block gait or movement ticker. */
public final class ModularJointEntity extends LegJointBlockEntity {
  public ModularJointEntity(BlockPos pos, BlockState state) {
    super(pos, state);
  }

  @Override
  public BlockEntityType<?> getType() {
    return ComponentRegistry.MODULAR_JOINT_ENTITY.get();
  }
}
