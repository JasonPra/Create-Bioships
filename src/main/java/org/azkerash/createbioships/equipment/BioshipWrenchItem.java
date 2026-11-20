package org.azkerash.createbioships.equipment;

import com.simibubi.create.content.equipment.wrench.WrenchItem;

/** Uses Create's wrench interactions; individual blocks own their restrictions. */
@net.neoforged.fml.common.EventBusSubscriber(
    modid = org.azkerash.createbioships.CreateBioships.MODID)
public final class BioshipWrenchItem extends WrenchItem {
  private record DropContext(
      net.minecraft.server.level.ServerLevel level,
      java.util.UUID player,
      net.minecraft.world.phys.Vec3 position) {}

  private static final ThreadLocal<DropContext> GROUND_DROPS = new ThreadLocal<>();

  /**
   * Called at Create's inventory delivery point; native removal, loot/NBT and protection stay
   * intact.
   */
  public static boolean dropRemovalStack(
      net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack) {
    var context = GROUND_DROPS.get();
    if (context == null || stack.isEmpty() || !context.player().equals(player.getUUID())) {
      return false;
    }
    var point = context.position();
    var drop =
        new net.minecraft.world.entity.item.ItemEntity(
            context.level(), point.x, point.y, point.z, stack.copy());
    drop.setDefaultPickUpDelay();
    if (!context.level().addFreshEntity(drop)) {
      return false;
    }
    stack.setCount(0);
    return true;
  }

  /** Handle supported grid wrench actions before normal block-use handlers consume the click. */
  @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
  public static void pickupGridBlock(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
        || !(event.getItemStack().getItem() instanceof BioshipWrenchItem wrench)
        || !event.getEntity().mayBuild()) {
      return;
    }
    var body = org.azkerash.createbioships.core.CoreAssemblyRules.containing(level, event.getPos());
    if (!supportsGridBlock(body, level.getBlockState(event.getPos()))) {
      return;
    }
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
    var socketContext =
        org.azkerash.createbioships.compat.letitwalk.CompactLimbController.socketContext(context);
    if (socketContext != context) {
      return useOn(socketContext);
    }
    var level = context.getLevel();
    var player = context.getPlayer();
    var pos = context.getClickedPos();
    var state = level.getBlockState(pos);
    if (state.is(org.azkerash.createbioships.components.ComponentRegistry.CORE.get())) {
      return net.minecraft.world.InteractionResult.FAIL;
    }
    if (level instanceof net.minecraft.server.level.ServerLevel server && player != null) {
      var body = org.azkerash.createbioships.core.CoreAssemblyRules.containing(server, pos);
      if (supportsGridBlock(body, state)) {
        if (player.isShiftKeyDown()
            && org.azkerash.createbioships.core.SubLevelData.isLiving(body)
            && !player
                .getUUID()
                .equals(
                    org.azkerash.createbioships.core.access.SubLevelPermissions.get(body)
                        .owner())) {
          player.displayClientMessage(
              net.minecraft.network.chat.Component.literal(
                  "Only the creature's owner can remove its components."),
              true);
          return net.minecraft.world.InteractionResult.FAIL;
        }
        java.util.function.Supplier<net.minecraft.world.InteractionResult> action =
            () ->
                !player.isShiftKeyDown()
                        || state.getBlock()
                            instanceof com.simibubi.create.content.equipment.wrench.IWrenchable
                    ? super.useOn(context)
                    : PICKUP.onSneakWrenched(state, context);
        var previous = GROUND_DROPS.get();
        if (player.isShiftKeyDown()) {
          GROUND_DROPS.set(
              new DropContext(
                  server,
                  player.getUUID(),
                  body.logicalPose()
                      .transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(pos))));
        }
        try {
          return org.azkerash.createbioships.core.CoreAssemblyRules.isCreature(body)
              ? org.azkerash.createbioships.core.SubLevelHealth.withoutRemovalDamage(body, action)
              : action.get();
        } finally {
          if (previous == null) {
            GROUND_DROPS.remove();
          } else {
            GROUND_DROPS.set(previous);
          }
        }
      }
    }
    return super.useOn(context);
  }

  private static boolean supportsGridBlock(
      dev.ryanhcode.sable.sublevel.ServerSubLevel body,
      net.minecraft.world.level.block.state.BlockState state) {
    if (body == null) {
      return false;
    }
    var namespace =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(state.getBlock())
            .getNamespace();
    return org.azkerash.createbioships.core.CoreAssemblyRules.isCreature(body)
        || namespace.equals("biomancy")
        || namespace.equals(org.azkerash.createbioships.CreateBioships.MODID);
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
