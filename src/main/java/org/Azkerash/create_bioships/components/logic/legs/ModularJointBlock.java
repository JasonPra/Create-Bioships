package org.Azkerash.create_bioships.components.logic.legs;

import dev.letitwalk.content.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Native Let It Walk hinge/swivel semantics with our armored shell and entity registration. */
public class ModularJointBlock extends LegJointBlock {
  public ModularJointBlock(boolean swivel, Properties properties) {
    super(swivel, properties);
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.ENTITYBLOCK_ANIMATED;
  }

  @Override
  public BlockEntityType<? extends LegJointBlockEntity> getBlockEntityType() {
    return ComponentRegistry.MODULAR_JOINT_ENTITY.get();
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new ModularJointEntity(pos, state);
  }
}
