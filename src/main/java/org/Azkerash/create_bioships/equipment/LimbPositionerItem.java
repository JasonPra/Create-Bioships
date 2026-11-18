package org.Azkerash.create_bioships.equipment;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Our restricted variant of Let It Walk's Positioner, not a wrench or remote controller. */
public final class LimbPositionerItem extends Item {
  public LimbPositionerItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    return org.Azkerash.create_bioships.compat.letitwalk.LimbPositionerActions.use(
        org.Azkerash.create_bioships.compat.letitwalk.CompactLimbController.socketContext(context));
  }
}
