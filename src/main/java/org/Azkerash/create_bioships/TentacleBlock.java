package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

public class TentacleBlock extends BaseEntityBlock {
    public static final MapCodec<TentacleBlock> CODEC = simpleCodec(TentacleBlock::new);
    public TentacleBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(5, 10, 5, 11, 16, 11); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TentacleBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Create_bioships.TENTACLE_ENTITY.get(), (world, pos, block, tentacle) -> tentacle.serverTick());
    }
}
