package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.Azkerash.create_bioships.Create_bioships;

/** Development starter equipment and protected development tools; no menu or test behavior. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class DevelopmentLoadout {
  public static final int NAVIGATOR_SLOT = 8, NAVIGATOR_MENU_SLOT = 44;
  public static final int TOOLKIT_SLOT = 7, TOOLKIT_MENU_SLOT = 43;
  private static final String GIVEN = "create_bioships:development_kit";

  private DevelopmentLoadout() {}

  public static boolean active(Player player) {
    if (player instanceof ServerPlayer server)
      return DevelopmentWorldSetup.isDevelopment(server.getServer().overworld());
    return player != null
        && player.level().dimension() == Level.OVERWORLD
        && player
            .level()
            .getBiome(player.blockPosition())
            .is(
                ResourceKey.create(
                    Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath(
                        Create_bioships.MODID, "core_development")));
  }

  public static boolean navigator(ItemStack stack) {
    return stack.is(DevelopmentNavigatorItem.ITEM.get());
  }

  public static boolean managed(ItemStack stack) {
    return navigator(stack) || stack.is(DevelopmentToolkitItem.ITEM.get());
  }

  public static boolean reservedSlot(int slot) {
    return slot == NAVIGATOR_SLOT || slot == TOOLKIT_SLOT;
  }

  public static boolean reservedMenuSlot(int slot) {
    return slot == NAVIGATOR_MENU_SLOT || slot == TOOLKIT_MENU_SLOT;
  }

  public static boolean protectedItem(Player player, ItemStack stack) {
    return active(player) && managed(stack);
  }

  public static void prepare(ServerPlayer player) {
    if (!active(player)) return;
    var persistent = player.getPersistentData();
    var data = persistent.getCompound(Player.PERSISTED_NBT_TAG);
    if (!data.getBoolean(GIVEN)) {
      var goggles = Create_bioships.BIOSHIP_GOGGLES.get();
      if (!player.getItemBySlot(EquipmentSlot.HEAD).is(goggles)) {
        var previous = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player
            .getInventory()
            .clearOrCountMatchingItems(
                stack -> stack.is(goggles), 1, player.inventoryMenu.getCraftSlots());
        player.setItemSlot(EquipmentSlot.HEAD, goggles.getDefaultInstance());
        if (!previous.isEmpty() && !player.addItem(previous)) player.drop(previous, false);
      }
      if (!player
          .getInventory()
          .contains(Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance()))
        player.addItem(Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
      data.putBoolean(GIVEN, true);
      persistent.put(Player.PERSISTED_NBT_TAG, data);
    }
    ensureNavigator(player);
  }

  public static void ensureNavigator(ServerPlayer player) {
    var inventory = player.getInventory();
    if (!active(player)) {
      for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        if (inventory.getItem(slot).is(DevelopmentToolkitItem.ITEM.get()))
          inventory.setItem(slot, ItemStack.EMPTY);
      if (player.containerMenu.getCarried().is(DevelopmentToolkitItem.ITEM.get()))
        player.containerMenu.setCarried(ItemStack.EMPTY);
      return;
    }
    boolean changed = false;
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      var item = inventory.getItem(slot);
      if (managed(item)
          && !(slot == NAVIGATOR_SLOT && navigator(item))
          && !(slot == TOOLKIT_SLOT && item.is(DevelopmentToolkitItem.ITEM.get()))) {
        inventory.setItem(slot, ItemStack.EMPTY);
        changed = true;
      }
    }
    if (managed(player.containerMenu.getCarried())) {
      player.containerMenu.setCarried(ItemStack.EMPTY);
      changed = true;
    }
    for (int slot : new int[] {TOOLKIT_SLOT, NAVIGATOR_SLOT}) {
      var expected =
          slot == TOOLKIT_SLOT
              ? DevelopmentToolkitItem.ITEM.get()
              : DevelopmentNavigatorItem.ITEM.get();
      if (inventory.getItem(slot).is(expected)) continue;
      var displaced = inventory.getItem(slot).copy();
      inventory.setItem(slot, new ItemStack(expected));
      changed = true;
      if (!displaced.isEmpty() && !inventory.add(displaced)) player.drop(displaced, false);
    }
    if (changed) {
      inventory.setChanged();
      player.inventoryMenu.broadcastChanges();
    }
  }

  public static boolean rejectsClick(
      AbstractContainerMenu menu, int slotId, int button, ClickType type, Player player) {
    if (!active(player)) return false;
    if (type == ClickType.SWAP && reservedSlot(button) || managed(menu.getCarried())) return true;
    if (slotId < 0 || slotId >= menu.slots.size()) return false;
    var slot = menu.getSlot(slotId);
    return slot.container == player.getInventory() && reservedSlot(slot.getContainerSlot());
  }

  public static void synchronize(ServerPlayer player) {
    player.inventoryMenu.broadcastFullState();
    player.containerMenu.broadcastFullState();
  }

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) prepare(player);
  }

  @SubscribeEvent
  public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) ensureNavigator(player);
  }

  @SubscribeEvent
  public static void clone(PlayerEvent.Clone event) {
    if (!active(event.getOriginal())) return;
    var original = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    var data = event.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    data.putBoolean(GIVEN, original.getBoolean(GIVEN));
    event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, data);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) ensureNavigator(player);
  }

  @SubscribeEvent
  public static void drops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
    if (event.getEntity() instanceof Player player && active(player))
      event.getDrops().removeIf(drop -> managed(drop.getItem()));
  }
}
