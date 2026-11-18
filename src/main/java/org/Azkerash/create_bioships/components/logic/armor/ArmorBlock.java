package org.Azkerash.create_bioships.components.logic.armor;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Passive shell material. Armor capacity and damage are owned by the grid. */
public final class ArmorBlock extends Block {
  public static final MapCodec<ArmorBlock> CODEC = simpleCodec(ArmorBlock::new);

  public ArmorBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }
}
