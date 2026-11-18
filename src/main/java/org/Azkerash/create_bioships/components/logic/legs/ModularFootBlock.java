package org.Azkerash.create_bioships.components.logic.legs;

import dev.letitwalk.content.LegFootBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Normal collision/friction foot, never a climbing or world-anchoring foot. */
public final class ModularFootBlock extends LegFootBlock {
  public ModularFootBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return net.minecraft.world.level.block.Block.box(1, 0, 1, 15, 6, 15);
  }

  @Override
  protected VoxelShape getCollisionShape(
      BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return getShape(s, l, p, c);
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new FootEntity(p, s);
  }

  public static final class FootEntity extends LegFootBlockEntity {
    public FootEntity(BlockPos p, BlockState s) {
      super(p, s);
    }

    @Override
    public BlockEntityType<?> getType() {
      return ComponentRegistry.MODULAR_FOOT_ENTITY.get();
    }
  }
}
