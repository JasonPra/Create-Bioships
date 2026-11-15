package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** Beveled living shell; its collision shape follows the stepped organic profile. */
public class CarapaceBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CarapaceBlock> CODEC = simpleCodec(CarapaceBlock::new);
    private static final VoxelShape NORTH = Shapes.or(Block.box(0, 0, 6, 16, 5, 16), Block.box(0, 5, 3, 16, 10, 16), Block.box(0, 10, 0, 16, 16, 16));
    public CarapaceBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int turns = switch (state.getValue(FACING)) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        VoxelShape shape = NORTH;
        for (int n = 0; n < turns; n++) {
            VoxelShape rotated = Shapes.empty();
            for (var box : shape.toAabbs()) rotated = Shapes.or(rotated, Shapes.box(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX));
            shape = rotated;
        }
        return shape;
    }
}
