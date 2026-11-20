package org.Azkerash.create_bioships;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.equipment.*;

/** Mod registration and lifecycle wiring. */
@Mod(Create_bioships.MODID)
public final class Create_bioships {
  public static final String MODID = "create_bioships";
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
  public static final DeferredHolder<
          net.minecraft.world.entity.EntityType<?>,
          net.minecraft.world.entity.EntityType<
              org.Azkerash.create_bioships.combat.CoreCombatHurtbox>>
      CORE_HURTBOX =
          ENTITIES.register(
              "core_hurtbox",
              () ->
                  net.minecraft.world.entity.EntityType.Builder
                      .<org.Azkerash.create_bioships.combat.CoreCombatHurtbox>of(
                          org.Azkerash.create_bioships.combat.CoreCombatHurtbox::new,
                          net.minecraft.world.entity.MobCategory.MISC)
                      .sized(1, 1)
                      .clientTrackingRange(10)
                      .updateInterval(1)
                      .build("create_bioships:core_hurtbox"));
  public static final DeferredHolder<
          net.minecraft.world.entity.EntityType<?>,
          net.minecraft.world.entity.EntityType<
              org.Azkerash.create_bioships.combat.EyeCombatHurtbox>>
      EYE_HURTBOX =
          ENTITIES.register(
              "eye_hurtbox",
              () ->
                  net.minecraft.world.entity.EntityType.Builder
                      .<org.Azkerash.create_bioships.combat.EyeCombatHurtbox>of(
                          org.Azkerash.create_bioships.combat.EyeCombatHurtbox::new,
                          net.minecraft.world.entity.MobCategory.MISC)
                      .sized(.4f, .4f)
                      .clientTrackingRange(10)
                      .updateInterval(1)
                      .build("create_bioships:eye_hurtbox"));
  private static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

  public static final DeferredItem<BioshipGogglesItem> BIOSHIP_GOGGLES =
      ITEMS.register(
          "bioship_goggles", () -> new BioshipGogglesItem(new Item.Properties().stacksTo(1)));
  public static final DeferredItem<BioshipWrenchItem> BIOSHIP_WRENCH =
      ITEMS.register(
          "bioship_wrench", () -> new BioshipWrenchItem(new Item.Properties().stacksTo(1)));

  public static final DeferredItem<org.Azkerash.create_bioships.equipment.LimbPositionerItem>
      LIMB_POSITIONER =
          ITEMS.register(
              "limb_positioner",
              () ->
                  new org.Azkerash.create_bioships.equipment.LimbPositionerItem(
                      new Item.Properties().stacksTo(1)));

  public static final DeferredItem<org.Azkerash.create_bioships.equipment.SomaticResonatorItem>
      SOMATIC_RESONATOR =
          ITEMS.register(
              "somatic_resonator",
              org.Azkerash.create_bioships.equipment.SomaticResonatorItem::new);

  static {
    TABS.register(
        "core",
        () ->
            CreativeModeTab.builder()
                .title(Component.literal("Bioship Core"))
                .icon(() -> ComponentRegistry.CORE_ITEM.get().getDefaultInstance())
                .displayItems(
                    (parameters, output) -> {
                      ComponentRegistry.addToCreative(output);
                      org.Azkerash.create_bioships.furniture.FurnitureRegistry.addToCreative(
                          output);
                      output.accept(BIOSHIP_GOGGLES.get());
                      output.accept(BIOSHIP_WRENCH.get());
                      output.accept(LIMB_POSITIONER.get());
                      output.accept(SOMATIC_RESONATOR.get());
                    })
                .build());
  }

  public Create_bioships(IEventBus bus) {
    bus.addListener(
        (net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(
                () -> {
                  ComponentRegistry.initializeBehaviors();
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
    bus.addListener(
        (net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
            event.put(
                CORE_HURTBOX.get(),
                org.Azkerash.create_bioships.combat.SubLevelCombatTarget.attributes().build()));
    bus.addListener(
        (net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
            event.put(
                EYE_HURTBOX.get(),
                org.Azkerash.create_bioships.combat.SubLevelCombatTarget.attributes().build()));
    ENTITIES.register(bus);
    ComponentRegistry.register(bus);
    org.Azkerash.create_bioships.furniture.FurnitureRegistry.register(bus);
    ITEMS.register(bus);
    TABS.register(bus);
  }
}
