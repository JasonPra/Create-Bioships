package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.Azkerash.create_bioships.Create_bioships;

/** Lifecycle setup for the data-driven bedrock-superflat development world preset. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class DevelopmentWorldSetup {
  public static final int DEFAULT_TEST_Y = 64;
  private static final ResourceKey<net.minecraft.world.level.biome.Biome> BIOME =
      ResourceKey.create(
          Registries.BIOME,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "core_development"));

  private DevelopmentWorldSetup() {}

  public static boolean isDevelopment(ServerLevel level) {
    return level == level.getServer().overworld()
        && level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
            .anyMatch(b -> b.is(BIOME));
  }

  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    var level = event.getServer().overworld();
    if (!isDevelopment(level)) return;
    var state = CoreTestState.get(level);
    if (!state.built) {
      var layout =
          new CoreSplitFixture.Layout(
              new BlockPos(0, level.getChunkSource().getGenerator().getSpawnHeight(level), 0));
      CoreTestCommands.reset(level, layout);
      level.setDefaultSpawnPos(layout.base().offset(0, 0, -15), 0);
    }
    if (state.combatBuilt
        && (state.combatOrigin.getX() != 1024 || state.combatOrigin.getZ() != 1024))
      CombatFixture.relocate(level, new BlockPos(1024, state.layout().base().getY(), 1024));
    if (!state.potionBuilt) {
      state.potionOrigin = new BlockPos(256, state.layout().base().getY(), 0);
      PotionFixture.reset(level);
    }
    if (!state.arrowBuilt) {
      state.arrowOrigin = new BlockPos(128, state.layout().base().getY(), 0);
      CoreArrowFixture.reset(level);
    }
    if (!state.visionBuilt) VisionFixture.reset(level);
    if (!state.membraneBuilt) MembraneFixture.reset(level);
    if (!state.brainBuilt) BrainFixture.reset(level);
    if (!state.locomotionBuilt) LocomotionFixture.reset(level);
    if (!state.modularLimbsBuilt) ModularLimbFixture.reset(level);
    if (state.legChamberVersion < 1) {
      DevelopmentLegChambers.build(level, LocomotionFixture.base(level), false, 7);
      DevelopmentLegChambers.build(level, ModularLimbFixture.base(level), false, 11);
      state.legChamberVersion = 1;
      state.setDirty();
    }
    LocomotionFixture.installDoors(level);
    ModularLimbFixture.installDoors(level);
    if (!state.digestionBuilt) {
      state.digestionOrigin = new BlockPos(384, state.layout().base().getY(), 0);
      DigestionFixture.reset(level);
    }
    if (!state.waterloggingBuilt
        || state.waterloggingCatalogSize != WaterloggingFixture.catalog().size())
      WaterloggingFixture.install(level);
    DevelopmentHabitatFixtures.installAquarium(level);
    if (!state.combatBuilt) {
      state.combatOrigin = new BlockPos(1024, state.layout().base().getY(), 1024);
      CombatFixture.reset(level);
    }
    if (!state.necromancerDenBuilt) {
      NecromancerDenFixture.build(level);
      DevelopmentJuniorFixture.install(level);
    }
    if (state.denAccessVersion < 1) NecromancerDenFixture.repairAccessAndLighting(level);
    if (state.denFurnitureVersion < 1)
      EstateInteriors.library(level, NecromancerDenFixture.surface(level));
    if (state.denFurnitureVersion < 2)
      EstateInteriors.rooms(level, NecromancerDenFixture.surface(level));
    if (state.denLandscapeVersion < 1)
      EstateLandscape.terrain(level, NecromancerDenFixture.surface(level));
  }
}
