package org.Azkerash.create_bioships.equipment;

import com.simibubi.create.content.equipment.wrench.WrenchItem;

/** Uses Create's wrench interactions; individual blocks own their restrictions. */
@net.neoforged.fml.common.EventBusSubscriber(
    modid = org.Azkerash.create_bioships.Create_bioships.MODID)
public final class BioshipWrenchItem extends WrenchItem {
  /** Handle supported grid wrench actions before normal block-use handlers consume the click. */
  @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
  public static void pickupGridBlock(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
        || !(event.getItemStack().getItem() instanceof BioshipWrenchItem wrench)
        || !event.getEntity().mayBuild()) return;
    var body =
        org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(level, event.getPos());
    if (!supportsGridBlock(body, level.getBlockState(event.getPos()))) return;
    var context =
        new net.minecraft.world.item.context.UseOnContext(
            event.getEntity(), event.getHand(), event.getHitVec());
    event.setCancellationResult(wrench.useOn(context));
    event.setCanceled(true);
  }

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
    if (level instanceof net.minecraft.server.level.ServerLevel server && player != null) {
      var body = org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(server, pos);
      if (supportsGridBlock(body, state)) {
        java.util.function.Supplier<net.minecraft.world.InteractionResult> action =
            () ->
                !player.isShiftKeyDown()
                        || state.getBlock()
                            instanceof com.simibubi.create.content.equipment.wrench.IWrenchable
                    ? super.useOn(context)
                    : PICKUP.onSneakWrenched(state, context);
        return org.Azkerash.create_bioships.core.CoreAssemblyRules.isCreature(body)
            ? org.Azkerash.create_bioships.core.SubLevelHealth.withoutRemovalDamage(body, action)
            : action.get();
      }
    }
    return super.useOn(context);
  }

  private static boolean supportsGridBlock(
      dev.ryanhcode.sable.sublevel.ServerSubLevel body,
      net.minecraft.world.level.block.state.BlockState state) {
    if (body == null) return false;
    var namespace =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(state.getBlock())
            .getNamespace();
    return org.Azkerash.create_bioships.core.CoreAssemblyRules.isCreature(body)
        || namespace.equals("biomancy")
        || namespace.equals(org.Azkerash.create_bioships.Create_bioships.MODID);
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
