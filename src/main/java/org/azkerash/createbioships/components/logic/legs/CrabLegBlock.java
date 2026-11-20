package org.azkerash.createbioships.components.logic.legs;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Friction-contact compact leg; crab stance does not supply adhesive anchoring. */
public final class CrabLegBlock extends CompactLegSocketBlock {
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty HEIGHT =
      CompactLegSocketBlock.HEIGHT;
  public static final MapCodec<CrabLegBlock> CODEC = simpleCodec(CrabLegBlock::new);

  public CrabLegBlock(Properties properties) {
    super(properties);
  }

  public static void initialize(CrabLegBlock block) {
    CompactLegSocketBlock.initialize(block);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }
}
