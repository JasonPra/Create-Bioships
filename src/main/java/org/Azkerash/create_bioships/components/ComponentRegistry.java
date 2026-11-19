package org.Azkerash.create_bioships.components;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.logic.core.*;
import org.Azkerash.create_bioships.components.logic.eye.EyeBlock;
import org.Azkerash.create_bioships.components.logic.readout.VitalReadoutBlock;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlock;

/**
 * Simple components share registration; constructors and optional setup refer to their logic
 * classes.
 */
public final class ComponentRegistry {
  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks(Create_bioships.MODID);
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems(Create_bioships.MODID);
  private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Create_bioships.MODID);
  private static final DeferredRegister<com.github.elenterius.biomancy.api.serum.Serum>
      GENE_SERUMS =
          DeferredRegister.create(
              com.github.elenterius.biomancy.init.ModSerums.REGISTRY, Create_bioships.MODID);
  public static final DeferredHolder<
          com.github.elenterius.biomancy.api.serum.Serum,
          org.Azkerash.create_bioships.components.logic.serum.ComponentGeneSerum>
      LONGEVITY_GENE =
          GENE_SERUMS.register(
              "longevity_gene",
              () ->
                  new org.Azkerash.create_bioships.components.logic.serum.ComponentGeneSerum(
                      0xffba84cf));
  public static final DeferredHolder<
          com.github.elenterius.biomancy.api.serum.Serum,
          org.Azkerash.create_bioships.components.logic.serum.ComponentGeneSerum>
      EFFICIENCY_GENE =
          GENE_SERUMS.register(
              "efficiency_gene",
              () ->
                  new org.Azkerash.create_bioships.components.logic.serum.ComponentGeneSerum(
                      0xffb4ca76));
  public static final DeferredItem<com.github.elenterius.biomancy.item.SerumItem> LONGEVITY_SERUM =
      ITEMS.register(
          "longevity_gene_serum",
          () ->
              new com.github.elenterius.biomancy.item.SerumItem(
                  new Item.Properties()
                      .craftRemainder(com.github.elenterius.biomancy.init.ModItems.VIAL.get()),
                  LONGEVITY_GENE));
  public static final DeferredItem<com.github.elenterius.biomancy.item.SerumItem> EFFICIENCY_SERUM =
      ITEMS.register(
          "efficiency_gene_serum",
          () ->
              new com.github.elenterius.biomancy.item.SerumItem(
                  new Item.Properties()
                      .craftRemainder(com.github.elenterius.biomancy.init.ModItems.VIAL.get()),
                  EFFICIENCY_GENE));
  private static final Map<String, DeferredItem<BlockItem>> BLOCK_ITEMS = new LinkedHashMap<>();
  private static final List<Runnable> SETUP = new ArrayList<>();
  private static boolean initialized;

  public static final DeferredBlock<org.Azkerash.create_bioships.components.logic.armor.ArmorBlock>
      CHITIN_ARMOR =
          component(
              new ComponentDefinition<>(
                  "chitin_armor",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.armor.ArmorBlock(
                          BlockBehaviour.Properties.of()
                              .strength(3, 6)
                              .sound(SoundType.BONE_BLOCK))));

  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.legs.CompactLimbBlock>
      COMPACT_LIMB =
          BLOCKS.register(
              "compact_limb",
              () ->
                  new org.Azkerash.create_bioships.components.logic.legs.CompactLimbBlock(
                      BlockBehaviour.Properties.of()
                          .strength(-1, 3600000)
                          .noLootTable()
                          .noOcclusion()));

  public static final DeferredBlock<org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock>
      CRAB_LEG =
          component(
              new ComponentDefinition<>(
                      "crab_leg",
                      () ->
                          new org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock(
                              BlockBehaviour.Properties.of()
                                  .strength(2, 5)
                                  .noOcclusion()
                                  .sound(SoundType.BONE_BLOCK)))
                  .withBehavior(
                      org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock::initialize));

  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.legs.ModularJointBlock>
      LIMB_JOINT =
          component(
              new ComponentDefinition<>(
                  "limb_joint",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.legs.ModularJointBlock(
                          false,
                          BlockBehaviour.Properties.of()
                              .strength(3, 6)
                              .noOcclusion()
                              .sound(SoundType.BONE_BLOCK))));
  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.legs.SocketJointBlock>
      LIMB_SOCKET =
          component(
              new ComponentDefinition<>(
                      "limb_socket",
                      () ->
                          new org.Azkerash.create_bioships.components.logic.legs.SocketJointBlock(
                              BlockBehaviour.Properties.of()
                                  .strength(3, 6)
                                  .noOcclusion()
                                  .sound(SoundType.BONE_BLOCK)))
                  .withBehavior(
                      org.Azkerash.create_bioships.components.logic.legs.SocketJointBlock
                          ::initialize));

  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.legs.ModularJointBlock>
      LIMB_SWIVEL =
          component(
              new ComponentDefinition<>(
                  "limb_swivel",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.legs.ModularJointBlock(
                          true,
                          BlockBehaviour.Properties.of()
                              .strength(3, 6)
                              .noOcclusion()
                              .sound(SoundType.BONE_BLOCK))));
  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.legs.ModularSegmentBlock>
      LIMB_SEGMENT =
          component(
              new ComponentDefinition<>(
                  "limb_segment",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.legs.ModularSegmentBlock(
                          BlockBehaviour.Properties.of()
                              .strength(3, 6)
                              .noOcclusion()
                              .sound(SoundType.BONE_BLOCK))));
  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.legs.ModularFootBlock>
      LIMB_FOOT =
          component(
              new ComponentDefinition<>(
                  "limb_foot",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.legs.ModularFootBlock(
                          BlockBehaviour.Properties.of()
                              .strength(3, 6)
                              .noOcclusion()
                              .sound(SoundType.BONE_BLOCK))));
  public static final DeferredHolder<
          BlockEntityType<?>,
          BlockEntityType<org.Azkerash.create_bioships.components.logic.legs.ModularJointEntity>>
      MODULAR_JOINT_ENTITY =
          BLOCK_ENTITIES.register(
              "limb_joint",
              () ->
                  BlockEntityType.Builder.of(
                          org.Azkerash.create_bioships.components.logic.legs.ModularJointEntity
                              ::new,
                          LIMB_JOINT.get(),
                          LIMB_SWIVEL.get(),
                          LIMB_SOCKET.get())
                      .build(null));
  public static final DeferredHolder<
          BlockEntityType<?>,
          BlockEntityType<
              org.Azkerash.create_bioships.components.logic.legs.ModularFootBlock.FootEntity>>
      MODULAR_FOOT_ENTITY =
          BLOCK_ENTITIES.register(
              "limb_foot",
              () ->
                  BlockEntityType.Builder.of(
                          org.Azkerash.create_bioships
                                  .components
                                  .logic
                                  .legs
                                  .ModularFootBlock
                                  .FootEntity
                              ::new,
                          LIMB_FOOT.get())
                      .build(null));

  public static final DeferredBlock<CoreBlock> CORE =
      component(
          new ComponentDefinition<>(
              "living_core",
              () -> new CoreBlock(BlockBehaviour.Properties.of().strength(2).noLootTable())));
  public static final DeferredBlock<VitalReadoutBlock> VITAL_READOUT =
      component(
          new ComponentDefinition<>(
              "vital_readout",
              () ->
                  new VitalReadoutBlock(BlockBehaviour.Properties.of().strength(1).noOcclusion())));
  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.nerve.NervePanelBlock>
      NERVE_PANEL =
          component(
              new ComponentDefinition<>(
                  "nerve_panel",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.nerve.NervePanelBlock(
                          BlockBehaviour.Properties.of()
                              .strength(1)
                              .noOcclusion()
                              .sound(SoundType.SLIME_BLOCK)
                              .lightLevel(state -> 3))));
  public static final DeferredBlock<EyeBlock> LAND_EYE =
      component(
          new ComponentDefinition<>(
              "land_eye",
              () -> new EyeBlock(BlockBehaviour.Properties.of().strength(1).noOcclusion())));
  public static final DeferredBlock<EyeBlock> AQUATIC_EYE =
      component(
          new ComponentDefinition<>(
              "aquatic_eye",
              () -> new EyeBlock(BlockBehaviour.Properties.of().strength(1).noOcclusion())));
  public static final DeferredBlock<org.Azkerash.create_bioships.components.logic.brain.BrainBlock>
      BRAIN =
          component(
              new ComponentDefinition<>(
                  "brain",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.brain.BrainBlock(
                          BlockBehaviour.Properties.of()
                              .strength(1)
                              .noOcclusion()
                              .sound(SoundType.SLIME_BLOCK))));
  public static final DeferredItem<
          org.Azkerash.create_bioships.components.logic.brain.FactoryBehaviorCardItem>
      WANDER_BEHAVIOR_CARD =
          ITEMS.register(
              "wander_behavior_card",
              () ->
                  new org.Azkerash.create_bioships.components.logic.brain.FactoryBehaviorCardItem(
                      org.Azkerash.create_bioships.core.brain.behavior.WanderBehavior.ID));
  public static final DeferredItem<
          org.Azkerash.create_bioships.components.logic.brain.BehaviorCardItem>
      BLANK_BEHAVIOR_CARD =
          ITEMS.register(
              "blank_behavior_card",
              org.Azkerash.create_bioships.components.logic.brain.BehaviorCardItem::new);
  public static final DeferredBlock<StomachBlock> STOMACH =
      component(
          new ComponentDefinition<>(
              "stomach",
              () ->
                  new StomachBlock(
                      BlockBehaviour.Properties.of().strength(1).sound(SoundType.SLIME_BLOCK))));

  public static final DeferredBlock<org.Azkerash.create_bioships.components.logic.mouth.MouthBlock>
      MOUTH =
          component(
              new ComponentDefinition<>(
                  "feeding_mouth",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.mouth.MouthBlock(
                          BlockBehaviour.Properties.of()
                              .strength(1)
                              .sound(SoundType.SLIME_BLOCK))));
  public static final DeferredBlock<org.Azkerash.create_bioships.components.logic.mouth.MouthBlock>
      ARMORED_MAW =
          component(
              new ComponentDefinition<>(
                  "armored_maw",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.mouth.MouthBlock(
                          BlockBehaviour.Properties.of()
                              .strength(2)
                              .sound(SoundType.SLIME_BLOCK))));
  public static final DeferredBlock<
          org.Azkerash.create_bioships.components.logic.intestine.IntestineBlock>
      INTESTINE =
          component(
              new ComponentDefinition<>(
                  "fleshy_intestine",
                  () ->
                      new org.Azkerash.create_bioships.components.logic.intestine.IntestineBlock(
                          BlockBehaviour.Properties.of()
                              .strength(1)
                              .sound(SoundType.SLIME_BLOCK)
                              .noOcclusion())));
  public static final DeferredHolder<
          BlockEntityType<?>,
          BlockEntityType<org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity>>
      MOUTH_ENTITY =
          BLOCK_ENTITIES.register(
              "feeding_mouth",
              () ->
                  BlockEntityType.Builder.of(
                          org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity::new,
                          MOUTH.get(),
                          ARMORED_MAW.get())
                      .build(null));

  public static final DeferredItem<BlockItem> CORE_ITEM = item(CORE),
      VITAL_READOUT_ITEM = item(VITAL_READOUT),
      LAND_EYE_ITEM = item(LAND_EYE),
      AQUATIC_EYE_ITEM = item(AQUATIC_EYE),
      STOMACH_ITEM = item(STOMACH);
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoreGogglesDisplay>>
      CORE_DISPLAY =
          BLOCK_ENTITIES.register(
              "core_display",
              () ->
                  BlockEntityType.Builder.of(
                          CoreGogglesDisplay::new, CORE.get(), VITAL_READOUT.get())
                      .build(null));

  public static final DeferredHolder<
          BlockEntityType<?>,
          BlockEntityType<org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity>>
      STOMACH_DISPLAY =
          BLOCK_ENTITIES.register(
              "stomach_display",
              () ->
                  BlockEntityType.Builder.of(
                          org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity
                              ::new,
                          STOMACH.get())
                      .build(null));

  private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, Create_bioships.MODID);
  public static final DeferredHolder<
          net.minecraft.world.inventory.MenuType<?>,
          net.minecraft.world.inventory.MenuType<
              org.Azkerash.create_bioships.components.logic.stomach.StomachMenu>>
      STOMACH_MENU =
          MENUS.register(
              "stomach",
              () ->
                  net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                      org.Azkerash.create_bioships.components.logic.stomach.StomachMenu
                          ::fromNetwork));

  public static final DeferredHolder<
          net.minecraft.world.inventory.MenuType<?>,
          net.minecraft.world.inventory.MenuType<
              org.Azkerash.create_bioships.components.logic.mouth.MouthMenu>>
      MOUTH_MENU =
          MENUS.register(
              "feeding_mouth",
              () ->
                  net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                      org.Azkerash.create_bioships.components.logic.mouth.MouthMenu::fromNetwork));

  public static final DeferredHolder<
          net.minecraft.world.inventory.MenuType<?>,
          net.minecraft.world.inventory.MenuType<
              org.Azkerash.create_bioships.components.logic.brain.BrainMenu>>
      BRAIN_MENU =
          MENUS.register(
              "brain",
              () ->
                  net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                      org.Azkerash.create_bioships.components.logic.brain.BrainMenu::fromNetwork));

  public static final DeferredHolder<
          BlockEntityType<?>,
          BlockEntityType<
              org.Azkerash.create_bioships.components.logic.brain.BrainLimbDependencies>>
      BRAIN_LIMB_ENTITY =
          BLOCK_ENTITIES.register(
              "brain_limb_dependencies",
              () ->
                  BlockEntityType.Builder.of(
                          org.Azkerash.create_bioships.components.logic.brain.BrainLimbDependencies
                              ::new,
                          BRAIN.get())
                      .build(null));

  private ComponentRegistry() {}

  private static <B extends Block> DeferredBlock<B> component(ComponentDefinition<B> definition) {
    var block = BLOCKS.register(definition.id(), definition.constructor());
    BLOCK_ITEMS.put(definition.id(), ITEMS.registerSimpleBlockItem(block));
    SETUP.add(() -> definition.initialize(block.get()));
    return block;
  }

  private static DeferredItem<BlockItem> item(DeferredBlock<?> block) {
    return BLOCK_ITEMS.get(block.getId().getPath());
  }

  public static void addToCreative(CreativeModeTab.Output output) {
    BLOCK_ITEMS.values().forEach(item -> output.accept(item.get()));
    output.accept(BLANK_BEHAVIOR_CARD.get());
    output.accept(WANDER_BEHAVIOR_CARD.get());
    output.accept(LONGEVITY_SERUM.get());
    output.accept(EFFICIENCY_SERUM.get());
  }

  public static void initializeBehaviors() {
    if (initialized) return;
    SETUP.forEach(Runnable::run);
    initialized = true;
  }

  public static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    GENE_SERUMS.register(bus);
    BLOCK_ENTITIES.register(bus);
    MENUS.register(bus);
  }
}
