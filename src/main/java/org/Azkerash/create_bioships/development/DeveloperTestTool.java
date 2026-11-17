package org.Azkerash.create_bioships.development;

import org.Azkerash.create_bioships.registry.Create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-authoritative test destination picker using the vanilla inventory screen. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class DeveloperTestTool extends Item {
  public DeveloperTestTool(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (player instanceof ServerPlayer server && server.hasPermissions(2)) open(server, 0);
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }

  private static ItemStack icon(Item item, String name) {
    var stack = new ItemStack(item);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    return stack;
  }

  private static void open(ServerPlayer player, int section) {
    var contents = new SimpleContainer(27);
    contents.setItem(0, icon(Items.COMPASS, "General regression tests"));
    contents.setItem(1, icon(Items.TROPICAL_FISH_BUCKET, "Ecosystem tests"));
    if (section == 0) {
      contents.setItem(10, icon(Items.GLASS, "Flat glass navigation maze"));
      contents.setItem(12, icon(Items.GRASS_BLOCK, "Elevation and tall-grass maze"));
    } else {
      contents.setItem(10, icon(Items.OAK_SAPLING, "Land ecosystem habitat"));
      contents.setItem(12, icon(Items.KELP, "Ocean ecosystem aquarium"));
      contents.setItem(14, icon(Items.END_STONE, "Remote End floating-island habitat"));
    }
    player.openMenu(
        new SimpleMenuProvider(
            (id, inventory, ignored) ->
                new ChestMenu(MenuType.GENERIC_9x3, id, inventory, contents, 3) {
                  @Override
                  public void clicked(int slot, int button, ClickType type, Player actor) {
                    if (!player.hasPermissions(2)) return;
                    if (slot == 0 || slot == 1) {
                      open(player, slot);
                      return;
                    }
                    if (slot != 10 && slot != 12 && !(section == 1 && slot == 14)) return;
                    var level = player.serverLevel();
                    if (!NavigationLab.lab(level)) {
                      player.displayClientMessage(
                          Component.literal("Open the developer lab world to visit these tests."),
                          false);
                      return;
                    }
                    BlockPos target;
                    if (section == 0)
                      target = slot == 10 ? new BlockPos(12, 82, 0) : new BlockPos(12, 82, 128);
                    else if (slot == 14) target = EndHabitatLab.prepare(level);
                    else {
                      EcosystemLab.prepare(level, new BlockPos(0, 80, 320));
                      target =
                          slot == 10
                              ? EcosystemLab.State.get(level).center.above(4)
                              : AquaticLab.viewpoint(level);
                    }
                    player.closeContainer();
                    player.teleportTo(
                        level,
                        target.getX() + .5,
                        target.getY(),
                        target.getZ() + .5,
                        java.util.Set.of(),
                        0,
                        15);
                  }

                  @Override
                  public ItemStack quickMoveStack(Player actor, int slot) {
                    return ItemStack.EMPTY;
                  }
                },
            Component.literal(
                section == 0 ? "Developer tests · Regression" : "Developer tests · Ecosystem")));
  }

  @SubscribeEvent
  public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && NavigationLab.lab(player.serverLevel())
        && !player
            .getInventory()
            .contains(new ItemStack(Create_bioships.DEVELOPER_TEST_TOOL.get()))) {
      var previous = player.getInventory().getItem(8);
      player.getInventory().setItem(8, ItemStack.EMPTY);
      if (!previous.isEmpty()) {
        int empty = -1;
        for (int slot = 9; slot < 36; slot++)
          if (player.getInventory().getItem(slot).isEmpty()) {
            empty = slot;
            break;
          }
        if (empty >= 0) player.getInventory().setItem(empty, previous);
        else player.drop(previous, false);
      }
      player.getInventory().setItem(8, new ItemStack(Create_bioships.DEVELOPER_TEST_TOOL.get()));
    }
  }
}
