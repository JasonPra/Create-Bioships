package org.Azkerash.create_bioships.components.logic.legs;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.RotatedPillarBlock;

/** Passive armored bone; native assembly groups connected bone blocks between joints. */
public final class ModularSegmentBlock extends RotatedPillarBlock {
  public static final MapCodec<ModularSegmentBlock> CODEC = simpleCodec(ModularSegmentBlock::new);

  public ModularSegmentBlock(Properties p) {
    super(p);
  }

  @Override
  public MapCodec<? extends RotatedPillarBlock> codec() {
    return CODEC;
  }
}
