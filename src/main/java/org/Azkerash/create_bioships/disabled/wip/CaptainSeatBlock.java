package org.Azkerash.create_bioships.disabled.wip;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** A rib-backed captain's cradle retaining Create's seat entity and riding behavior. */
public class CaptainSeatBlock extends SeatBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 5, 16), Block.box(2, 5, 12, 14, 28, 16),
            Block.box(0, 5, 2, 3, 12, 14), Block.box(13, 5, 2, 16, 12, 14));
    public CaptainSeatBlock(Properties properties) { super(properties, DyeColor.PURPLE); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
}
