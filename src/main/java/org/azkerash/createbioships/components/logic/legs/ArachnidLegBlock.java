package org.azkerash.createbioships.components.logic.legs;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Adhesive spider socket. Rig settings and placement rules are shared with compact sockets. */
public final class ArachnidLegBlock extends CompactLegSocketBlock {
  public static final MapCodec<ArachnidLegBlock> CODEC = simpleCodec(ArachnidLegBlock::new);

  public ArachnidLegBlock(Properties properties) {
    super(properties);
  }

  @Override
  public void appendHoverText(
      net.minecraft.world.item.ItemStack stack,
      net.minecraft.world.item.Item.TooltipContext context,
      java.util.List<net.minecraft.network.chat.Component> lines,
      net.minecraft.world.item.TooltipFlag flag) {
    lines.add(
        net.minecraft.network.chat.Component.translatable(
                "block.create_bioships.arachnid_leg.tooltip")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }
}
