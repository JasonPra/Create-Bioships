package org.Azkerash.create_bioships;

import com.mojang.datafixers.DSL;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;

@Mod(Create_bioships.MOD_ID)
public class Create_bioships {
  public static final String MOD_ID = "create_bioships";
  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
  public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
  public static final net.neoforged.neoforge.registries.DeferredHolder<
          net.minecraft.world.entity.EntityType<?>,
          net.minecraft.world.entity.EntityType<BioshipTargetEntity>>
      BIOSHIP_TARGET =
          ENTITIES.register(
              "bioship_attack_surface",
              () ->
                  net.minecraft.world.entity.EntityType.Builder.of(
                          BioshipTargetEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                      .sized(.9f, 1f)
                      .clientTrackingRange(8)
                      .updateInterval(3)
                      .noSave()
                      .build("bioship_attack_surface"));
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
  public static final DeferredBlock<LivingCoreBlock> CORE =
      BLOCKS.register(
          "living_core",
          () ->
              new LivingCoreBlock(
                  BlockBehaviour.Properties.of().strength(3).sound(SoundType.SLIME_BLOCK)));

  // Legacy prototype IDs remain registered; the new body uses Simulated's native helm.
  static {
    BLOCKS.register(
        "living_helm",
        () ->
            new LivingHelmBlock(
                BlockBehaviour.Properties.of()
                    .strength(2)
                    .sound(SoundType.SLIME_BLOCK)
                    .noOcclusion()));
  }

  public static final DeferredBlock<LivingThrottleBlock> THROTTLE =
      BLOCKS.register(
          "living_throttle",
          () ->
              new LivingThrottleBlock(
                  BlockBehaviour.Properties.of()
                      .strength(2)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<LivingThrottleBlock> VERTICAL_TRIM =
      BLOCKS.register(
          "vertical_trim",
          () ->
              new LivingThrottleBlock(
                  BlockBehaviour.Properties.of()
                      .strength(2)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion(),
                  LivingThrottleBlock.Kind.VERTICAL));
  public static final DeferredBlock<PulseLightBlock> PULSE_LIGHT =
      BLOCKS.register(
          "pulse_light",
          () ->
              new PulseLightBlock(
                  BlockBehaviour.Properties.of()
                      .strength(1)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()
                      .lightLevel(state -> 13)));
  public static final DeferredBlock<SurfaceEyeBlock> SURFACE_EYE =
      BLOCKS.register(
          "surface_eye",
          () ->
              new SurfaceEyeBlock(
                  BlockBehaviour.Properties.of()
                      .strength(1)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<OneWayWindowBlock> ONE_WAY_WINDOW =
      BLOCKS.register(
          "one_way_window",
          () ->
              new OneWayWindowBlock(
                  BlockBehaviour.Properties.of()
                      .strength(2)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<AccessPanelBlock> ACCESS_PANEL =
      BLOCKS.register(
          "access_panel",
          () ->
              new AccessPanelBlock(
                  BlockBehaviour.Properties.of()
                      .strength(2)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<BlueprintPanelBlock> BLUEPRINT_PANEL =
      BLOCKS.register(
          "blueprint_panel",
          () ->
              new BlueprintPanelBlock(
                  BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK)));
  public static final DeferredBlock<StomachBlock> STOMACH =
      BLOCKS.register(
          "stomach",
          () ->
              new StomachBlock(
                  BlockBehaviour.Properties.of().strength(3).sound(SoundType.SLIME_BLOCK)));
  public static final DeferredBlock<IntestineBlock> INTESTINE =
      BLOCKS.register(
          "fleshy_intestine",
          () ->
              new IntestineBlock(
                  BlockBehaviour.Properties.of()
                      .strength(1.5f)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
      DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, MOD_ID);
  public static final DeferredHolder<
          net.minecraft.world.inventory.MenuType<?>,
          net.minecraft.world.inventory.MenuType<StomachMenu>>
      STOMACH_MENU =
          MENUS.register(
              "stomach",
              () ->
                  net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                      StomachMenu::fromNetwork));
  public static final DeferredHolder<
          net.minecraft.world.inventory.MenuType<?>,
          net.minecraft.world.inventory.MenuType<AccessMenu>>
      ACCESS_MENU =
          MENUS.register(
              "access_panel",
              () ->
                  net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                      AccessMenu::fromNetwork));
  public static final DeferredHolder<
          net.minecraft.world.inventory.MenuType<?>,
          net.minecraft.world.inventory.MenuType<BlueprintMenu>>
      BLUEPRINT_MENU =
          MENUS.register(
              "blueprint_panel",
              () ->
                  net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                      BlueprintMenu::fromNetwork));
  public static final DeferredBlock<FeedingMouthBlock> FEEDING_MOUTH =
      BLOCKS.register(
          "feeding_mouth",
          () ->
              new FeedingMouthBlock(
                  BlockBehaviour.Properties.of()
                      .strength(3)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<Block> MOUTH =
      BLOCKS.registerSimpleBlock(
          "living_mouth",
          BlockBehaviour.Properties.of().strength(3).sound(SoundType.SLIME_BLOCK).noOcclusion());
  public static final DeferredBlock<BuoyancyOrganBlock> BUOYANCY_ORGAN =
      BLOCKS.register(
          "buoyancy_organ",
          () ->
              new BuoyancyOrganBlock(
                  BlockBehaviour.Properties.of()
                      .strength(2)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()
                      .lightLevel(state -> 9)));
  public static final DeferredBlock<Block> FLESH =
      BLOCKS.registerSimpleBlock(
          "living_hull", BlockBehaviour.Properties.of().strength(2).sound(SoundType.SLIME_BLOCK));
  public static final DeferredBlock<CaptainSeatBlock> PERCH =
      BLOCKS.register(
          "living_perch",
          () ->
              new CaptainSeatBlock(
                  BlockBehaviour.Properties.of()
                      .strength(2)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<CarapaceBlock> CARAPACE =
      BLOCKS.register(
          "living_carapace",
          () ->
              new CarapaceBlock(
                  BlockBehaviour.Properties.of()
                      .strength(3)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<TransparentBlock> MEMBRANE =
      BLOCKS.register(
          "living_membrane",
          () ->
              new TransparentBlock(
                  BlockBehaviour.Properties.of()
                      .strength(1.2f)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<LivingDoorBlock> DOOR =
      BLOCKS.register(
          "living_door",
          () ->
              new LivingDoorBlock(
                  BlockBehaviour.Properties.of()
                      .strength(2)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final DeferredBlock<TentacleBlock> TENTACLE =
      BLOCKS.register(
          "tentacle",
          () ->
              new TentacleBlock(
                  BlockBehaviour.Properties.of()
                      .strength(1)
                      .sound(SoundType.SLIME_BLOCK)
                      .noOcclusion()));
  public static final java.util.List<DeferredBlock<NerveControlBlock>> CONTROLS =
      java.util.Arrays.stream(NerveControlBlock.Command.values())
          .map(
              command ->
                  BLOCKS.register(
                      "nerve_" + command.id(),
                      () ->
                          new NerveControlBlock(
                              command,
                              BlockBehaviour.Properties.of()
                                  .strength(1.5f)
                                  .sound(SoundType.SLIME_BLOCK)
                                  .noOcclusion()
                                  .lightLevel(
                                      state -> state.getValue(NerveControlBlock.ACTIVE) ? 6 : 0))))
          .toList();
  public static final DeferredItem<BioshipWrenchItem> BIOSHIP_WRENCH =
      ITEMS.register(
          "bioship_wrench", () -> new BioshipWrenchItem(new Item.Properties().stacksTo(1)));
  public static final DeferredItem<BioshipGogglesItem> BIOSHIP_GOGGLES =
      ITEMS.register(
          "bioship_goggles", () -> new BioshipGogglesItem(new Item.Properties().stacksTo(1)));
  public static final DeferredItem<BlockItem> CORE_ITEM =
      ITEMS.registerSimpleBlockItem("living_core", CORE);
  public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> DOOR_ITEM =
      ITEMS.register(
          "living_door",
          () ->
              new net.minecraft.world.item.DoubleHighBlockItem(DOOR.get(), new Item.Properties()));

  static {
    // Recoverable body parts need item forms so mining a wreck can leave useful supplies.
    for (var block : BLOCKS.getEntries()) {
      String name = block.getId().getPath();
      if (!name.equals("living_core") && !name.equals("living_door"))
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
  }

  // These custom NBT layouts have no vanilla DFU schema; preserve them as remainder data.
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LivingCoreBlockEntity>>
      CORE_ENTITY =
          BLOCK_ENTITIES.register(
              "living_core",
              () ->
                  BlockEntityType.Builder.of(LivingCoreBlockEntity::new, CORE.get())
                      .build(DSL.remainderType()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StomachBlockEntity>>
      STOMACH_ENTITY =
          BLOCK_ENTITIES.register(
              "stomach",
              () ->
                  BlockEntityType.Builder.of(StomachBlockEntity::new, STOMACH.get())
                      .build(DSL.remainderType()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeedingMouthBlockEntity>>
      FEEDING_MOUTH_ENTITY =
          BLOCK_ENTITIES.register(
              "feeding_mouth",
              () ->
                  BlockEntityType.Builder.of(FeedingMouthBlockEntity::new, FEEDING_MOUTH.get())
                      .build(DSL.remainderType()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TentacleBlockEntity>>
      TENTACLE_ENTITY =
          BLOCK_ENTITIES.register(
              "tentacle",
              () ->
                  BlockEntityType.Builder.of(TentacleBlockEntity::new, TENTACLE.get())
                      .build(DSL.remainderType()));

  static {
    TABS.register(
        "bioships",
        () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.create_bioships"))
                .icon(() -> CORE_ITEM.get().getDefaultInstance())
                .displayItems(
                    (parameters, output) -> {
                      output.accept(CORE_ITEM.get());
                      output.accept(DOOR_ITEM.get());
                      output.accept(BIOSHIP_WRENCH.get());
                      output.accept(BIOSHIP_GOGGLES.get());
                    })
                .build());
  }

  public Create_bioships(IEventBus bus) {
    com.simibubi.create.content.equipment.goggles.GogglesItem.addIsWearingPredicate(
        player ->
            player
                .getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(BIOSHIP_GOGGLES.get()));
    ENTITIES.register(bus);
    bus.addListener(
        (net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
            event.put(BIOSHIP_TARGET.get(), BioshipTargetEntity.attributes().build()));
    MENUS.register(bus);
    BLOCKS.register(bus);
    ITEMS.register(bus);
    BLOCK_ENTITIES.register(bus);
    TABS.register(bus);
    bus.addListener(BioshipNetwork::register);
    bus.addListener(
        (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
            event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                FEEDING_MOUTH_ENTITY.get(),
                (mouth, side) ->
                    side == null || side == mouth.getBlockState().getValue(FeedingMouthBlock.FACING)
                        ? new net.neoforged.neoforge.items.wrapper.InvWrapper(mouth)
                        : null));
    com.simibubi.create.api.contraption.BlockMovementChecks.registerMovementAllowedCheck(
        (state, level, pos) ->
            BioshipIntegration.bound(BioshipIntegration.containing(level, pos))
                ? com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.FAIL
                : com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.PASS);
  }
}
