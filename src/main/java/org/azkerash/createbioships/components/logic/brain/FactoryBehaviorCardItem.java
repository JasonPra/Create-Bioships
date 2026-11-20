package org.azkerash.createbioships.components.logic.brain;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Fixed factory program identifier; execution and state belong to the creature's brain. */
public class FactoryBehaviorCardItem extends Item {
  private final ResourceLocation behavior;

  public FactoryBehaviorCardItem(ResourceLocation behavior) {
    super(new Properties().stacksTo(1));
    this.behavior = behavior;
  }

  public ResourceLocation behavior() {
    return behavior;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    lines.add(
        Component.translatable(getDescriptionId() + ".tooltip")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
  }
}
