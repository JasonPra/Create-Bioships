package org.Azkerash.create_bioships.equipment;

import com.simibubi.create.content.equipment.wrench.WrenchItem;

/** Uses Create's wrench interactions; individual blocks own their restrictions. */
public final class BioshipWrenchItem extends WrenchItem {
  private static final com.simibubi.create.content.equipment.wrench.IWrenchable PICKUP =
      new com.simibubi.create.content.equipment.wrench.IWrenchable() {};

  @Override
  public net.minecraft.world.InteractionResult useOn(
      net.minecraft.world.item.context.UseOnContext context) {
    var level = context.getLevel();
    var player = context.getPlayer();
    var pos = context.getClickedPos();
    var state = level.getBlockState(pos);
    if (state.is(org.Azkerash.create_bioships.Create_bioships.CORE.get()))
      return net.minecraft.world.InteractionResult.FAIL;
    if (level instanceof net.minecraft.server.level.ServerLevel server
        && player != null
        && player.isShiftKeyDown()) {
      var body = org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(server, pos);
      if (body != null && org.Azkerash.create_bioships.core.CoreAssemblyRules.isCreature(body))
        return org.Azkerash.create_bioships.core.SubLevelHealth.withoutRemovalDamage(
            body,
            () ->
                state.getBlock() instanceof com.simibubi.create.content.equipment.wrench.IWrenchable
                    ? super.useOn(context)
                    : PICKUP.onSneakWrenched(state, context));
    }
    return super.useOn(context);
  }

  // Keep our own held model instead of Create's animated cogwheel renderer.
  @Override
  public void initializeClient(
      java.util.function.Consumer<
              net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>
          consumer) {}

  public BioshipWrenchItem(Properties properties) {
    super(properties);
  }
}
