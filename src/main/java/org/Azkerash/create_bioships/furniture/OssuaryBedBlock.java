package org.Azkerash.create_bioships.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock;

/**
 * A native sleeping/respawn bed with static ossuary geometry and independent water in both halves.
 */
public final class OssuaryBedBlock extends BedBlock implements SimpleWaterloggedBlock {
  public static final MapCodec<BedBlock> CODEC = simpleCodec(OssuaryBedBlock::new);

  public OssuaryBedBlock(Properties p) {
    super(DyeColor.RED, p);
    registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
  }

  @Override
  public MapCodec<BedBlock> codec() {
    return CODEC;
  }

  @Override
  protected RenderShape getRenderShape(BlockState s) {
    return RenderShape.MODEL;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BlockStateProperties.WATERLOGGED);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return WaterloggedComponentBlock.placed(super.getStateForPlacement(c), c);
  }

  @Override
  protected FluidState getFluidState(BlockState s) {
    return WaterloggedComponentBlock.fluid(s);
  }

  @Override
  public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity placer, ItemStack stack) {
    var head = p.relative(s.getValue(FACING));
    boolean wet = l.getFluidState(head).is(net.minecraft.tags.FluidTags.WATER);
    super.setPlacedBy(l, p, s, placer, stack);
    if (!l.isClientSide && l.getBlockState(head).is(this)) {
      l.setBlock(
          head,
          l.getBlockState(head).setValue(BlockStateProperties.WATERLOGGED, wet),
          Block.UPDATE_ALL);
    }
  }

  @Override
  protected BlockState updateShape(
      BlockState s, Direction d, BlockState other, LevelAccessor l, BlockPos p, BlockPos q) {
    WaterloggedComponentBlock.schedule(s, l, p);
    var result = super.updateShape(s, d, other, l, p, q);
    return result.isAir() && s.getValue(BlockStateProperties.WATERLOGGED)
        ? Fluids.WATER.defaultFluidState().createLegacyBlock()
        : result;
  }

  @Override
  public BlockState playerWillDestroy(Level l, BlockPos p, BlockState s, Player player) {
    if (!l.isClientSide && player.isCreative() && s.getValue(PART) == BedPart.FOOT) {
      var head = p.relative(s.getValue(FACING));
      var other = l.getBlockState(head);
      if (other.is(this)) {
        l.setBlock(head, other.getFluidState().createLegacyBlock(), 35);
      }
    }
    return super.playerWillDestroy(l, p, s, player);
  }

  @Override
  protected net.minecraft.world.phys.shapes.VoxelShape getShape(
      BlockState s, BlockGetter l, BlockPos p, net.minecraft.world.phys.shapes.CollisionContext c) {
    var shape =
        net.minecraft.world.phys.shapes.Shapes.or(
            box(0, 3, 0, 16, 9, 16),
            s.getValue(PART) == BedPart.HEAD ? box(0, 5, 0, 16, 14, 2) : box(0, 5, 14, 16, 11, 16));
    int turns = (s.getValue(FACING).get2DDataValue() + 2) % 4;
    for (int i = 0; i < turns; i++) {
      var rotated = net.minecraft.world.phys.shapes.Shapes.empty();
      for (var a : shape.toAabbs()) {
        rotated =
            net.minecraft.world.phys.shapes.Shapes.or(
                rotated,
                net.minecraft.world.phys.shapes.Shapes.box(
                    1 - a.maxZ, a.minY, a.minX, 1 - a.minZ, a.maxY, a.maxX));
      }
      shape = rotated;
    }
    return shape;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new BedEntity(p, s);
  }

  public static final class BedEntity extends BedBlockEntity {
    public BedEntity(BlockPos p, BlockState s) {
      super(p, s, DyeColor.RED);
    }

    @Override
    public BlockEntityType<?> getType() {
      return FurnitureRegistry.BED_ENTITY.get();
    }
  }
}
