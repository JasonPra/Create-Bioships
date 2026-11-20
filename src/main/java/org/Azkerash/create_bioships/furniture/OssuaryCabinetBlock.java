package org.Azkerash.create_bioships.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Native 27-slot storage, including hopper support and inventory drops. */
public final class OssuaryCabinetBlock extends BarrelBlock {
  public static final MapCodec<BarrelBlock> CODEC = simpleCodec(OssuaryCabinetBlock::new);

  public OssuaryCabinetBlock(Properties p) {
    super(p);
  }

  @Override
  public MapCodec<BarrelBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new CabinetEntity(p, s);
  }

  public static final class CabinetEntity extends BarrelBlockEntity {
    public CabinetEntity(BlockPos p, BlockState s) {
      super(p, s);
    }

    @Override
    public BlockEntityType<?> getType() {
      return FurnitureRegistry.CABINET_ENTITY.get();
    }

    @Override
    protected Component getDefaultName() {
      return Component.translatable("block.create_bioships.ossuary_cabinet");
    }
  }
}
