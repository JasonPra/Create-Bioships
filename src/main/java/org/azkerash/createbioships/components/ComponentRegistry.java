package org.azkerash.createbioships.components;

import com.github.elenterius.biomancy.api.serum.Serum;
import com.github.elenterius.biomancy.init.ModItems;
import com.github.elenterius.biomancy.init.ModSerums;
import com.github.elenterius.biomancy.item.SerumItem;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.logic.armor.ArmorBlock;
import org.azkerash.createbioships.components.logic.brain.BehaviorCardItem;
import org.azkerash.createbioships.components.logic.brain.BrainBlock;
import org.azkerash.createbioships.components.logic.brain.BrainLimbDependencies;
import org.azkerash.createbioships.components.logic.brain.BrainMenu;
import org.azkerash.createbioships.components.logic.brain.CardWorkspaceMenu;
import org.azkerash.createbioships.components.logic.brain.FactoryBehaviorCardItem;
import org.azkerash.createbioships.components.logic.brain.HuntingBehaviorCardItem;
import org.azkerash.createbioships.components.logic.brain.HuntingCardMenu;
import org.azkerash.createbioships.components.logic.core.*;
import org.azkerash.createbioships.components.logic.eye.EyeBlock;
import org.azkerash.createbioships.components.logic.flipper.FlipperBlock;
import org.azkerash.createbioships.components.logic.flipper.FlipperBlockEntity;
import org.azkerash.createbioships.components.logic.intestine.IntestineBlock;
import org.azkerash.createbioships.components.logic.legs.ArachnidLegBlock;
import org.azkerash.createbioships.components.logic.legs.CompactLimbBlock;
import org.azkerash.createbioships.components.logic.legs.CrabLegBlock;
import org.azkerash.createbioships.components.logic.legs.ModularFootBlock;
import org.azkerash.createbioships.components.logic.legs.ModularJointBlock;
import org.azkerash.createbioships.components.logic.legs.ModularJointEntity;
import org.azkerash.createbioships.components.logic.legs.ModularSegmentBlock;
import org.azkerash.createbioships.components.logic.legs.SocketJointBlock;
import org.azkerash.createbioships.components.logic.mouth.MouthBlock;
import org.azkerash.createbioships.components.logic.mouth.MouthBlockEntity;
import org.azkerash.createbioships.components.logic.mouth.MouthMenu;
import org.azkerash.createbioships.components.logic.nametag.NameTagDisplayBlock;
import org.azkerash.createbioships.components.logic.nametag.NameTagDisplayEntity;
import org.azkerash.createbioships.components.logic.nametag.NameTagDisplayMenu;
import org.azkerash.createbioships.components.logic.nerve.NervePanelBlock;
import org.azkerash.createbioships.components.logic.readout.VitalReadoutBlock;
import org.azkerash.createbioships.components.logic.respiration.RespiratoryBlock;
import org.azkerash.createbioships.components.logic.serum.ComponentGeneSerum;
import org.azkerash.createbioships.components.logic.stomach.StomachBlock;
import org.azkerash.createbioships.components.logic.stomach.StomachBlockEntity;
import org.azkerash.createbioships.components.logic.stomach.StomachMenu;
import org.azkerash.createbioships.core.brain.behavior.WanderBehavior;
import org.azkerash.createbioships.furniture.ThroneBlock;
import org.azkerash.createbioships.registration.BlockRegistration;
import org.azkerash.createbioships.registration.ItemRegistration;

/**
 * Simple components share registration; constructors and optional setup refer to their logic
 * classes.
 */
public final class ComponentRegistry {
  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks(CreateBioships.MODID);
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems(CreateBioships.MODID);
  private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateBioships.MODID);
  private static final DeferredRegister<Serum> GENE_SERUMS =
      DeferredRegister.create(ModSerums.REGISTRY, CreateBioships.MODID);
  public static final DeferredHolder<Serum, ComponentGeneSerum> LONGEVITY_GENE =
      GENE_SERUMS.register("longevity_gene", () -> new ComponentGeneSerum(0xffba84cf));
  public static final DeferredHolder<Serum, ComponentGeneSerum> EFFICIENCY_GENE =
      GENE_SERUMS.register("efficiency_gene", () -> new ComponentGeneSerum(0xffb4ca76));
  public static final DeferredItem<SerumItem> LONGEVITY_SERUM =
      ItemRegistration.register(
          ITEMS,
          "longevity_gene_serum",
          () ->
              new SerumItem(
                  new Item.Properties().craftRemainder(ModItems.VIAL.get()), LONGEVITY_GENE));
  public static final DeferredItem<SerumItem> EFFICIENCY_SERUM =
      ItemRegistration.register(
          ITEMS,
          "efficiency_gene_serum",
          () ->
              new SerumItem(
                  new Item.Properties().craftRemainder(ModItems.VIAL.get()), EFFICIENCY_GENE));
  private static final Map<String, DeferredItem<BlockItem>> BLOCK_ITEMS = new LinkedHashMap<>();
  private static final List<Runnable> SETUP = new ArrayList<>();
  private static boolean initialized;

  public static final DeferredBlock<NameTagDisplayBlock> NAME_TAG_DISPLAYER =
      component(
          "name_tag_displayer", NameTagDisplayBlock::new, () -> boneProperties(1.5f).noOcclusion());
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NameTagDisplayEntity>>
      NAME_TAG_DISPLAY_ENTITY =
          BLOCK_ENTITIES.register(
              "name_tag_displayer",
              () ->
                  BlockEntityType.Builder.of(NameTagDisplayEntity::new, NAME_TAG_DISPLAYER.get())
                      .build(null));

  public static final DeferredBlock<ThroneBlock> THRONE_OF_AZKERASH =
      component("throne_of_azkerash", ThroneBlock::new, () -> boneProperties(2.5f).noOcclusion());

  public static final DeferredBlock<ArmorBlock> CHITIN_ARMOR =
      component("chitin_armor", ArmorBlock::new, () -> boneProperties(3, 6));

  public static final DeferredBlock<FlipperBlock> FLIPPER =
      component("flipper", FlipperBlock::new, () -> fleshProperties(1.5f).noOcclusion());
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlipperBlockEntity>>
      FLIPPER_ENTITY =
          BLOCK_ENTITIES.register(
              "flipper",
              () -> BlockEntityType.Builder.of(FlipperBlockEntity::new, FLIPPER.get()).build(null));

  public static final DeferredBlock<CompactLimbBlock> COMPACT_LIMB =
      BlockRegistration.register(
          BLOCKS,
          "compact_limb",
          () ->
              new CompactLimbBlock(
                  BlockBehaviour.Properties.of()
                      .strength(-1, 3600000)
                      .noLootTable()
                      .noOcclusion()));

  public static final DeferredBlock<CrabLegBlock> CRAB_LEG =
      component(
          "crab_leg",
          CrabLegBlock::new,
          () -> boneProperties(2, 5).noOcclusion(),
          CrabLegBlock::initialize);

  // The shared compact-rig bounds and step-height providers above cover both socket types.
  public static final DeferredBlock<ArachnidLegBlock> ARACHNID_LEG =
      component("arachnid_leg", ArachnidLegBlock::new, () -> boneProperties(2, 5).noOcclusion());

  public static final DeferredBlock<ModularJointBlock> LIMB_JOINT =
      component(
          "limb_joint", () -> new ModularJointBlock(false, boneProperties(3, 6).noOcclusion()));
  public static final DeferredBlock<SocketJointBlock> LIMB_SOCKET =
      component(
          "limb_socket",
          SocketJointBlock::new,
          () -> boneProperties(3, 6).noOcclusion(),
          SocketJointBlock::initialize);

  public static final DeferredBlock<ModularJointBlock> LIMB_SWIVEL =
      component(
          "limb_swivel", () -> new ModularJointBlock(true, boneProperties(3, 6).noOcclusion()));
  public static final DeferredBlock<ModularSegmentBlock> LIMB_SEGMENT =
      component("limb_segment", ModularSegmentBlock::new, () -> boneProperties(3, 6).noOcclusion());
  public static final DeferredBlock<ModularFootBlock> LIMB_FOOT =
      component("limb_foot", ModularFootBlock::new, () -> boneProperties(3, 6).noOcclusion());
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ModularJointEntity>>
      MODULAR_JOINT_ENTITY =
          BLOCK_ENTITIES.register(
              "limb_joint",
              () ->
                  BlockEntityType.Builder.of(
                          ModularJointEntity::new,
                          LIMB_JOINT.get(),
                          LIMB_SWIVEL.get(),
                          LIMB_SOCKET.get())
                      .build(null));
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<ModularFootBlock.FootEntity>>
      MODULAR_FOOT_ENTITY =
          BLOCK_ENTITIES.register(
              "limb_foot",
              () ->
                  BlockEntityType.Builder.of(ModularFootBlock.FootEntity::new, LIMB_FOOT.get())
                      .build(null));

  public static final DeferredBlock<CoreBlock> CORE =
      component(
          "living_core",
          CoreBlock::new,
          () -> BlockBehaviour.Properties.of().strength(2).noLootTable());
  public static final DeferredBlock<VitalReadoutBlock> VITAL_READOUT =
      component(
          "vital_readout",
          VitalReadoutBlock::new,
          () -> BlockBehaviour.Properties.of().strength(1).noOcclusion());
  public static final DeferredBlock<NervePanelBlock> NERVE_PANEL =
      component(
          "nerve_panel",
          NervePanelBlock::new,
          () -> fleshProperties(1).noOcclusion().lightLevel(state -> 3));
  public static final DeferredBlock<EyeBlock> LAND_EYE =
      component(
          "land_eye",
          EyeBlock::new,
          () -> BlockBehaviour.Properties.of().strength(1).noOcclusion());
  public static final DeferredBlock<EyeBlock> AQUATIC_EYE =
      component(
          "aquatic_eye",
          EyeBlock::new,
          () -> BlockBehaviour.Properties.of().strength(1).noOcclusion());
  public static final DeferredBlock<BrainBlock> BRAIN =
      component("brain", BrainBlock::new, () -> fleshProperties(1).noOcclusion());
  public static final DeferredItem<FactoryBehaviorCardItem> WANDER_BEHAVIOR_CARD =
      ItemRegistration.register(
          ITEMS, "wander_behavior_card", () -> new FactoryBehaviorCardItem(WanderBehavior.ID));
  public static final DeferredItem<HuntingBehaviorCardItem> HUNTING_BEHAVIOR_CARD =
      ItemRegistration.register(ITEMS, "hunting_behavior_card", HuntingBehaviorCardItem::new);
  public static final DeferredItem<BehaviorCardItem> BLANK_BEHAVIOR_CARD =
      ItemRegistration.register(ITEMS, "blank_behavior_card", BehaviorCardItem::new);
  public static final DeferredItem<BehaviorCardItem> PROGRAM_BEHAVIOR_CARD =
      ItemRegistration.register(ITEMS, "program_behavior_card", BehaviorCardItem::new);
  public static final DeferredBlock<RespiratoryBlock>
      LUNG = component("lung", () -> new RespiratoryBlock(fleshProperties(1).noOcclusion(), false)),
      GILLS =
          component("gills", () -> new RespiratoryBlock(fleshProperties(1).noOcclusion(), true));
  public static final DeferredBlock<RespiratoryBlock> ARMORED_GILLS =
      component(
          "armored_gills", () -> new RespiratoryBlock(fleshProperties(1).noOcclusion(), true));
  public static final DeferredBlock<StomachBlock> STOMACH =
      component("stomach", StomachBlock::new, () -> fleshProperties(1));

  public static final DeferredBlock<MouthBlock> MOUTH =
      component("feeding_mouth", MouthBlock::new, () -> fleshProperties(1));
  public static final DeferredBlock<MouthBlock> ARMORED_MAW =
      component("armored_maw", MouthBlock::new, () -> fleshProperties(2));
  public static final DeferredBlock<ArmorBlock> HUNTSMAN_ARMOR =
      component("huntsman_armor", ArmorBlock::new, () -> boneProperties(3, 6));
  public static final DeferredBlock<MouthBlock> HUNTSMAN_MAW =
      component("huntsman_maw", MouthBlock::new, () -> fleshProperties(2));
  public static final DeferredBlock<IntestineBlock> INTESTINE =
      component("fleshy_intestine", IntestineBlock::new, () -> fleshProperties(1).noOcclusion());
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MouthBlockEntity>>
      MOUTH_ENTITY =
          BLOCK_ENTITIES.register(
              "feeding_mouth",
              () ->
                  BlockEntityType.Builder.of(
                          MouthBlockEntity::new, MOUTH.get(), ARMORED_MAW.get(), HUNTSMAN_MAW.get())
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

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StomachBlockEntity>>
      STOMACH_DISPLAY =
          BLOCK_ENTITIES.register(
              "stomach_display",
              () -> BlockEntityType.Builder.of(StomachBlockEntity::new, STOMACH.get()).build(null));

  private static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, CreateBioships.MODID);
  public static final DeferredHolder<MenuType<?>, MenuType<StomachMenu>> STOMACH_MENU =
      MENUS.register("stomach", () -> IMenuTypeExtension.create(StomachMenu::fromNetwork));

  public static final DeferredHolder<MenuType<?>, MenuType<NameTagDisplayMenu>>
      NAME_TAG_DISPLAY_MENU =
          MENUS.register(
              "name_tag_displayer",
              () -> IMenuTypeExtension.create(NameTagDisplayMenu::fromNetwork));

  public static final DeferredHolder<MenuType<?>, MenuType<MouthMenu>> MOUTH_MENU =
      MENUS.register("feeding_mouth", () -> IMenuTypeExtension.create(MouthMenu::fromNetwork));

  public static final DeferredHolder<MenuType<?>, MenuType<BrainMenu>> BRAIN_MENU =
      MENUS.register("brain", () -> IMenuTypeExtension.create(BrainMenu::fromNetwork));

  public static final DeferredHolder<MenuType<?>, MenuType<HuntingCardMenu>> HUNTING_CARD_MENU =
      MENUS.register("hunting_card", () -> IMenuTypeExtension.create(HuntingCardMenu::fromNetwork));
  public static final DeferredHolder<MenuType<?>, MenuType<CardWorkspaceMenu>> CARD_WORKSPACE_MENU =
      MENUS.register(
          "card_workspace", () -> IMenuTypeExtension.create(CardWorkspaceMenu::fromNetwork));

  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrainLimbDependencies>>
      BRAIN_LIMB_ENTITY =
          BLOCK_ENTITIES.register(
              "brain_limb_dependencies",
              () ->
                  BlockEntityType.Builder.of(BrainLimbDependencies::new, BRAIN.get()).build(null));

  private ComponentRegistry() {}

  private static BlockBehaviour.Properties boneProperties(float hardness) {
    return BlockBehaviour.Properties.of().strength(hardness).sound(SoundType.BONE_BLOCK);
  }

  private static BlockBehaviour.Properties boneProperties(float hardness, float blastResistance) {
    return BlockBehaviour.Properties.of()
        .strength(hardness, blastResistance)
        .sound(SoundType.BONE_BLOCK);
  }

  private static BlockBehaviour.Properties fleshProperties(float hardness) {
    return BlockBehaviour.Properties.of().strength(hardness).sound(SoundType.SLIME_BLOCK);
  }

  @SafeVarargs
  private static <B extends Block> DeferredBlock<B> component(
      String id, Supplier<B> factory, Consumer<B>... setup) {
    return component(new ComponentDefinition<>(id, factory, List.of(setup)));
  }

  @SafeVarargs
  private static <B extends Block> DeferredBlock<B> component(
      String id,
      Function<BlockBehaviour.Properties, B> constructor,
      Supplier<BlockBehaviour.Properties> properties,
      Consumer<B>... setup) {
    return component(id, BlockRegistration.createFactory(constructor, properties), setup);
  }

  private static <B extends Block> DeferredBlock<B> component(ComponentDefinition<B> definition) {
    var registered =
        BlockRegistration.register(BLOCKS, ITEMS, definition.id(), definition.constructor());
    BLOCK_ITEMS.put(definition.id(), registered.item());
    SETUP.add(() -> definition.initialize(registered.block().get()));
    return registered.block();
  }

  private static DeferredItem<BlockItem> item(DeferredBlock<?> block) {
    return BLOCK_ITEMS.get(block.getId().getPath());
  }

  public static void addToCreative(CreativeModeTab.Output output) {
    BLOCK_ITEMS.values().forEach(item -> output.accept(item.get()));
    output.accept(BLANK_BEHAVIOR_CARD.get());
    output.accept(PROGRAM_BEHAVIOR_CARD.get());
    output.accept(WANDER_BEHAVIOR_CARD.get());
    output.accept(HUNTING_BEHAVIOR_CARD.get());
    output.accept(LONGEVITY_SERUM.get());
    output.accept(EFFICIENCY_SERUM.get());
  }

  public static void initializeBehaviors() {
    if (initialized) {
      return;
    }
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
