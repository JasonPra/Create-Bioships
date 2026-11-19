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
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.Create_bioships;

/** Read-only vanilla inventory menu for visiting development fixtures. */
@EventBusSubscriber(modid = Create_bioships.MODID)
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
    var entries = new SimpleContainer(18);
    entries.setItem(0, icon(Items.GLASS, "Regression: Core separation"));
    entries.setItem(1, icon(Items.DIAMOND_SWORD, "Regression: Hostile combat arena"));
    entries.setItem(2, icon(Items.DISPENSER, "Regression: Core arrow wound"));
    entries.setItem(3, icon(Items.SPLASH_POTION, "Regression: Shared potion effects"));
    entries.setItem(
        4,
        icon(
            org.Azkerash.create_bioships.components.ComponentRegistry.STOMACH_ITEM.get(),
            "Regression: Digestion and food intake"));
    entries.setItem(5, icon(Items.ENDER_EYE, "Regression: Vision in air and water"));
    entries.setItem(
        6,
        icon(
            org.Azkerash.create_bioships.components.ComponentRegistry.NERVE_PANEL.get().asItem(),
            "Regression: Biometric membrane access"));
    entries.setItem(
        7,
        icon(
            org.Azkerash.create_bioships.components.ComponentRegistry.BRAIN.get().asItem(),
            "Regression: Brain memory and chewing"));
    entries.setItem(
        8,
        icon(
            org.Azkerash.create_bioships.components.ComponentRegistry.CRAB_LEG.get().asItem(),
            "Regression: Armored locomotion"));
    entries.setItem(
        9,
        icon(
            org.Azkerash.create_bioships.components.ComponentRegistry.LIMB_JOINT.get().asItem(),
            "Regression: Modular limbs and Synaptic Calibrator"));
    entries.setItem(10, icon(Items.WATER_BUCKET, "Regression: Waterlogging catalogue"));
    entries.setItem(11, icon(Items.KELP, "Habitat: Aquatic homunculus aquarium"));
    if (state.juniorHabitatBuilt)
      entries.setItem(12, icon(Items.POPPY, "Habitat: Junior’s garden"));
    if (state.necromancerDenBuilt)
      entries.setItem(13, icon(Items.BOOKSHELF, "Landmark: The Den of Az’kerash"));
    player.openMenu(
        new SimpleMenuProvider(
            (id, inventory, viewer) ->
                new ChestMenu(MenuType.GENERIC_9x2, id, inventory, entries, 2) {
                  @Override
                  public void clicked(int slot, int button, ClickType type, Player clicking) {
                    if (slot < 0
                        || slot > 13
                        || slot == 12 && !state.juniorHabitatBuilt
                        || slot == 13 && !state.necromancerDenBuilt) return;
                    var base =
                        switch (slot) {
                          case 0 -> state.layout().base();
                          case 1 -> state.combatOrigin;
                          case 2 -> state.arrowOrigin;
                          case 3 -> state.potionOrigin;
                          case 4 -> state.digestionOrigin;
                          case 5 -> VisionFixture.base(level);
                          case 6 -> MembraneFixture.base(level);
                          case 7 -> BrainFixture.base(level);
                          case 8 -> LocomotionFixture.base(level);
                          case 9 -> ModularLimbFixture.base(level);
                          case 10 -> WaterloggingFixture.base(level);
                          case 11 -> DevelopmentHabitatFixtures.aquariumBase(level);
                          case 12 -> state.juniorHabitatOrigin;
                          default -> NecromancerDenFixture.surface(level);
                        };
                    int approach =
                        switch (slot) {
                          case 0 -> -15;
                          case 1 -> -18;
                          case 5, 7 -> -12;
                          case 11 -> -24;
                          case 12 -> -21;
                          case 13 -> -20;
                          default -> -10;
                        };
                    clicking.closeContainer();
                    if (clicking instanceof ServerPlayer server) {
                      server.teleportTo(
                          level, base.getX() + .5, base.getY() + 1, base.getZ() + approach, 0, 0);
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
}
