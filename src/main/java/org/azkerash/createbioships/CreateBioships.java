package org.azkerash.createbioships;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.equipment.*;
import org.azkerash.createbioships.registration.ItemRegistration;

/** Mod registration and lifecycle wiring. */
@Mod(CreateBioships.MODID)
public final class CreateBioships {
  public static final String MODID = "create_bioships";
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
  private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
  public static final DeferredHolder<
          net.minecraft.world.entity.EntityType<?>,
          net.minecraft.world.entity.EntityType<
              org.azkerash.createbioships.combat.SubLevelCombatTarget>>
      COMBAT_TARGET =
          ENTITIES.register(
              "sublevel_combat_target",
              () ->
                  net.minecraft.world.entity.EntityType.Builder
                      .<org.azkerash.createbioships.combat.SubLevelCombatTarget>of(
                          org.azkerash.createbioships.combat.SubLevelCombatTarget::new,
                          net.minecraft.world.entity.MobCategory.MISC)
                      .sized(.5f, .5f)
                      .clientTrackingRange(10)
                      .updateInterval(1)
                      .build("create_bioships:sublevel_combat_target"));
  public static final DeferredHolder<
          net.minecraft.world.entity.EntityType<?>,
          net.minecraft.world.entity.EntityType<
              org.azkerash.createbioships.combat.CoreCombatHurtbox>>
      CORE_HURTBOX =
          ENTITIES.register(
              "core_hurtbox",
              () ->
                  net.minecraft.world.entity.EntityType.Builder
                      .<org.azkerash.createbioships.combat.CoreCombatHurtbox>of(
                          org.azkerash.createbioships.combat.CoreCombatHurtbox::new,
                          net.minecraft.world.entity.MobCategory.MISC)
                      .sized(1, 1)
                      .clientTrackingRange(10)
                      .updateInterval(1)
                      .build("create_bioships:core_hurtbox"));
  public static final DeferredHolder<
          net.minecraft.world.entity.EntityType<?>,
          net.minecraft.world.entity.EntityType<
              org.azkerash.createbioships.combat.EyeCombatHurtbox>>
      EYE_HURTBOX =
          ENTITIES.register(
              "eye_hurtbox",
              () ->
                  net.minecraft.world.entity.EntityType.Builder
                      .<org.azkerash.createbioships.combat.EyeCombatHurtbox>of(
                          org.azkerash.createbioships.combat.EyeCombatHurtbox::new,
                          net.minecraft.world.entity.MobCategory.MISC)
                      .sized(.4f, .4f)
                      .clientTrackingRange(10)
                      .updateInterval(1)
                      .build("create_bioships:eye_hurtbox"));
  private static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

  public static final DeferredItem<BioshipGogglesItem> BIOSHIP_GOGGLES =
      ItemRegistration.registerNonStackable(ITEMS, "bioship_goggles", BioshipGogglesItem::new);
  public static final DeferredItem<BioshipWrenchItem> BIOSHIP_WRENCH =
      ItemRegistration.registerNonStackable(ITEMS, "bioship_wrench", BioshipWrenchItem::new);

  public static final DeferredItem<LimbPositionerItem> LIMB_POSITIONER =
      ItemRegistration.registerNonStackable(ITEMS, "limb_positioner", LimbPositionerItem::new);

  public static final DeferredItem<SomaticResonatorItem> SOMATIC_RESONATOR =
      ItemRegistration.register(ITEMS, "somatic_resonator", SomaticResonatorItem::new);

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
                      org.azkerash.createbioships.furniture.FurnitureRegistry.addToCreative(output);
                      output.accept(BIOSHIP_GOGGLES.get());
                      output.accept(BIOSHIP_WRENCH.get());
                      output.accept(LIMB_POSITIONER.get());
                      output.accept(SOMATIC_RESONATOR.get());
                    })
                .build());
  }

  public CreateBioships(IEventBus bus) {
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
                org.azkerash.createbioships.combat.SubLevelCombatTarget.attributes().build()));
    bus.addListener(
        (net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
            event.put(
                CORE_HURTBOX.get(),
                org.azkerash.createbioships.combat.SubLevelCombatTarget.attributes().build()));
    bus.addListener(
        (net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
            event.put(
                EYE_HURTBOX.get(),
                org.azkerash.createbioships.combat.SubLevelCombatTarget.attributes().build()));
    ENTITIES.register(bus);
    org.azkerash.createbioships.core.brain.program.ProgramCardData.register(bus);
    ComponentRegistry.register(bus);
    org.azkerash.createbioships.furniture.FurnitureRegistry.register(bus);
    ITEMS.register(bus);
    TABS.register(bus);
  }
}
