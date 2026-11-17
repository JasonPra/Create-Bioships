package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.Create_bioships;

/** Read-only vanilla inventory menu for visiting development fixtures. */
@EventBusSubscriber(modid = Create_bioships.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DevelopmentNavigatorItem extends Item {
  public static final DeferredHolder<Item, DevelopmentNavigatorItem> ITEM =
      DeferredHolder.create(
          Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_navigator"));

  public DevelopmentNavigatorItem() {
    super(new Properties().stacksTo(1));
  }

  @SubscribeEvent
  public static void register(RegisterEvent event) {
    event.register(Registries.ITEM, h -> h.register(ITEM.getId(), new DevelopmentNavigatorItem()));
  }

  @SubscribeEvent
  public static void tab(BuildCreativeModeTabContentsEvent event) {
    if (event
        .getTabKey()
        .location()
        .equals(ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "core")))
      event.accept(ITEM.get());
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (player instanceof ServerPlayer server) open(server);
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }

  private static ItemStack icon(Item item, String name) {
    var stack = new ItemStack(item);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    return stack;
  }

  public static void open(ServerPlayer player) {
    var level = player.serverLevel();
    if (!DevelopmentWorldSetup.isDevelopment(level)) {
      player.displayClientMessage(
          Component.literal("This navigator is for the development world."), true);
      return;
    }
    var state = CoreTestState.get(level);
    var entries = new SimpleContainer(9);
    entries.setItem(0, icon(Items.GLASS, "Regression: Core separation"));
    entries.setItem(1, icon(Items.DIAMOND_SWORD, "Regression: Hostile combat arena"));
    entries.setItem(2, icon(Items.DISPENSER, "Regression: Core arrow wound"));
    player.openMenu(
        new SimpleMenuProvider(
            (id, inventory, viewer) ->
                new ChestMenu(MenuType.GENERIC_9x1, id, inventory, entries, 1) {
                  @Override
                  public void clicked(int slot, int button, ClickType type, Player clicking) {
                    if (slot < 0 || slot > 2) return;
                    var base =
                        slot == 0
                            ? state.layout().base()
                            : slot == 1 ? state.combatOrigin : state.arrowOrigin;
                    clicking.closeContainer();
                    if (clicking instanceof ServerPlayer server) {
                      server.teleportTo(
                          level,
                          base.getX() + .5,
                          base.getY() + 1,
                          base.getZ() + (slot == 0 ? -15 : slot == 1 ? -18 : -10),
                          0,
                          0);
                      server.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                      server.fallDistance = 0;
                    }
                  }

                  @Override
                  public ItemStack quickMoveStack(Player player, int slot) {
                    return ItemStack.EMPTY;
                  }
                },
            Component.literal("Development Tests · Regression")));
  }

  @EventBusSubscriber(modid = Create_bioships.MODID)
  public static final class Provision {
    @SubscribeEvent
    public static void login(
        net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player
          && DevelopmentWorldSetup.isDevelopment(player.serverLevel())
          && !player.getInventory().contains(new ItemStack(ITEM.get())))
        player.addItem(new ItemStack(ITEM.get()));
    }
  }
}
