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
      decorateNewWorld(level);
      var layout = new CoreSplitFixture.Layout(new BlockPos(0, level.getMinBuildHeight() + 2, 0));
      CoreTestCommands.reset(level, layout);
      level.setDefaultSpawnPos(layout.base().offset(0, 0, -15), 0);
    }
    if (state.combatBuilt
        && (state.combatOrigin.getX() != 1024 || state.combatOrigin.getZ() != 1024))
      CombatFixture.relocate(level, new BlockPos(1024, state.layout().base().getY(), 1024));
    if (!state.combatBuilt) {
      state.combatOrigin = new BlockPos(1024, state.layout().base().getY(), 1024);
      CombatFixture.reset(level);
    }
  }

  private static void decorateNewWorld(ServerLevel level) {
    var palette =
        DevelopmentTestBlocks.FLOOR.values().stream()
            .map(h -> h.get())
            .toArray(net.minecraft.world.level.block.Block[]::new);
    var random = net.minecraft.util.RandomSource.create(level.getSeed());
    for (int center : new int[] {0, 1024})
      for (int x = center - 16; x <= center + 16; x++)
        for (int z = center - 21; z <= center + 17; z++) {
          var bottom = new BlockPos(x, level.getMinBuildHeight(), z);
          level.setBlock(
              bottom, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 2);
          level.setBlock(
              bottom.above(), palette[random.nextInt(palette.length)].defaultBlockState(), 2);
        }
  }
}
