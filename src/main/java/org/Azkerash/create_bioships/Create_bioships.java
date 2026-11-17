package org.Azkerash.create_bioships;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.equipment.*;

/** Mod registration and lifecycle wiring. */
@Mod(Create_bioships.MODID)
public final class Create_bioships {
  public static final String MODID = "create_bioships";
  private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
  private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
  public static final DeferredHolder<
          net.minecraft.world.entity.EntityType<?>,
          net.minecraft.world.entity.EntityType<
              org.Azkerash.create_bioships.combat.SubLevelCombatTarget>>
      COMBAT_TARGET =
          ENTITIES.register(
              "sublevel_combat_target",
              () ->
                  net.minecraft.world.entity.EntityType.Builder
                      .<org.Azkerash.create_bioships.combat.SubLevelCombatTarget>of(
                          org.Azkerash.create_bioships.combat.SubLevelCombatTarget::new,
                          net.minecraft.world.entity.MobCategory.MISC)
                      .sized(.5f, .5f)
                      .clientTrackingRange(10)
                      .updateInterval(1)
                      .build("create_bioships:sublevel_combat_target"));
  private static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
  private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>>
      BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
  public static final DeferredBlock<CoreBlock> CORE =
      BLOCKS.register(
          "living_core",
          () -> new CoreBlock(BlockBehaviour.Properties.of().strength(2).noLootTable()));
  public static final DeferredHolder<
          net.minecraft.world.level.block.entity.BlockEntityType<?>,
          net.minecraft.world.level.block.entity.BlockEntityType<CoreGogglesDisplay>>
      CORE_DISPLAY =
          BLOCK_ENTITIES.register(
              "core_display",
              () ->
                  net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                          CoreGogglesDisplay::new, CORE.get())
                      .build(null));
  public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem(CORE);

  public static final DeferredItem<BioshipGogglesItem> BIOSHIP_GOGGLES =
      ITEMS.register(
          "bioship_goggles", () -> new BioshipGogglesItem(new Item.Properties().stacksTo(1)));
  public static final DeferredItem<BioshipWrenchItem> BIOSHIP_WRENCH =
      ITEMS.register(
          "bioship_wrench", () -> new BioshipWrenchItem(new Item.Properties().stacksTo(1)));

  static {
    TABS.register(
        "core",
        () ->
            CreativeModeTab.builder()
                .title(Component.literal("Bioship Core"))
                .icon(() -> CORE_ITEM.get().getDefaultInstance())
                .displayItems(
                    (parameters, output) -> {
                      output.accept(CORE_ITEM.get());
                      output.accept(BIOSHIP_GOGGLES.get());
                      output.accept(BIOSHIP_WRENCH.get());
                    })
                .build());
  }

  public Create_bioships(IEventBus bus) {
    bus.addListener(
        (net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(
                () -> {
                  BlockPlacementRules.refresh();
                  BlockHealthValues.registerBlocks();
                  com.simibubi.create.content.equipment.goggles.GogglesItem.addIsWearingPredicate(
                      player ->
                          player
                              .getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                              .is(BIOSHIP_GOGGLES.get()));
                }));
    bus.addListener(
        (net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
            event.put(
                COMBAT_TARGET.get(),
                org.Azkerash.create_bioships.combat.SubLevelCombatTarget.attributes().build()));
    ENTITIES.register(bus);
    BLOCKS.register(bus);
    BLOCK_ENTITIES.register(bus);
    ITEMS.register(bus);
    TABS.register(bus);
  }
}
