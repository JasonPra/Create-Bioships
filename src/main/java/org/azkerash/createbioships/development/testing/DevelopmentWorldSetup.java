package org.azkerash.createbioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.azkerash.createbioships.CreateBioships;

/** Lifecycle setup for the data-driven bedrock-superflat development world preset. */
@EventBusSubscriber(modid = CreateBioships.MODID)
public final class DevelopmentWorldSetup {
  public static final int DEFAULT_TEST_Y = 64;
  private static final ResourceKey<net.minecraft.world.level.biome.Biome> BIOME =
      ResourceKey.create(
          Registries.BIOME,
          ResourceLocation.fromNamespaceAndPath(CreateBioships.MODID, "core_development"));

  private DevelopmentWorldSetup() {}

  public static boolean isDevelopment(ServerLevel level) {
    return level == level.getServer().overworld()
        && level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
            .anyMatch(b -> b.is(BIOME));
  }

  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    var level = event.getServer().overworld();
    if (!isDevelopment(level)) {
      return;
    }
    var state = CoreTestState.get(level);
    if (!state.isBuilt()) {
      var layout =
          new CoreSplitFixture.Layout(
              new BlockPos(0, level.getChunkSource().getGenerator().getSpawnHeight(level), 0));
      CoreTestCommands.reset(level, layout);
      level.setDefaultSpawnPos(layout.base().offset(0, 0, -15), 0);
    }
    if (state.isCombatBuilt()
        && (state.getCombatOrigin().getX() != 1024 || state.getCombatOrigin().getZ() != 1024)) {
      CombatFixture.relocate(level, new BlockPos(1024, state.layout().base().getY(), 1024));
    }
    if (!state.isPotionBuilt()) {
      state.setPotionOrigin(new BlockPos(256, state.layout().base().getY(), 0));
      PotionFixture.reset(level);
    }
    if (!state.isArrowBuilt()) {
      state.setArrowOrigin(new BlockPos(128, state.layout().base().getY(), 0));
      CoreArrowFixture.reset(level);
    }
    if (!state.isVisionBuilt()) {
      VisionFixture.reset(level);
    }
    if (!state.isMembraneBuilt()) {
      MembraneFixture.reset(level);
    }
    if (!state.isBrainBuilt()) {
      BrainFixture.reset(level);
    }
    if (!state.isLocomotionBuilt()) {
      LocomotionFixture.reset(level);
    }
    if (!state.isModularLimbsBuilt()) {
      ModularLimbFixture.reset(level);
    }
    if (state.getLegChamberVersion() < 1) {
      DevelopmentLegChambers.build(level, LocomotionFixture.base(level), false, 7);
      DevelopmentLegChambers.build(level, ModularLimbFixture.base(level), false, 11);
      state.setLegChamberVersion(1);
      state.setDirty();
    }
    LocomotionFixture.installDoors(level);
    ModularLimbFixture.installDoors(level);
    if (!state.isDigestionBuilt()) {
      state.setDigestionOrigin(new BlockPos(384, state.layout().base().getY(), 0));
      DigestionFixture.reset(level);
    }
    if (!state.isWaterloggingBuilt()
        || state.getWaterloggingCatalogSize() != WaterloggingFixture.catalog().size()) {
      WaterloggingFixture.install(level);
    }
    DevelopmentHabitatFixtures.installAquarium(level);
    if (!state.isCombatBuilt()) {
      state.setCombatOrigin(new BlockPos(1024, state.layout().base().getY(), 1024));
      CombatFixture.reset(level);
    }
    if (!state.isNecromancerDenBuilt()) {
      NecromancerDenFixture.build(level);
      DevelopmentJuniorFixture.install(level);
    }
    if (state.getDenAccessVersion() < 1) {
      NecromancerDenFixture.repairAccessAndLighting(level);
    }
    if (state.getDenAccessVersion() < 2) {
      NecromancerDenFixture.repairStairContinuity(level);
    }
    if (state.getDenFurnitureVersion() < 1) {
      EstateInteriors.library(level, NecromancerDenFixture.surface(level));
    }
    if (state.getDenFurnitureVersion() < 2) {
      EstateInteriors.rooms(level, NecromancerDenFixture.surface(level));
    }
    if (state.getDenLandscapeVersion() < 1) {
      EstateLandscape.terrain(level, NecromancerDenFixture.surface(level));
    }
    if (state.getDenLandscapeVersion() < 2) {
      EstateWaterworks.build(level, NecromancerDenFixture.surface(level));
    }
    if (state.getDenLandscapeVersion() < 3) {
      EstateRuins.build(level, NecromancerDenFixture.surface(level));
    }
    if (state.getDenLandscapeVersion() < 4) {
      EstateGardens.build(level, NecromancerDenFixture.surface(level));
    }
    if (state.getDenFurnitureVersion() < 4) {
      EstateInteriors.polish(level, NecromancerDenFixture.surface(level));
    }
  }
}
