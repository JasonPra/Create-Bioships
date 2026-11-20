package org.azkerash.createbioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.azkerash.createbioships.CreateBioships;

/** Paints only the generated floor of one chunk; fixtures and existing chunks are untouched. */
@EventBusSubscriber(modid = CreateBioships.MODID)
public final class DevelopmentFloorFeature extends Feature<NoneFeatureConfiguration> {
  public DevelopmentFloorFeature() {
    super(NoneFeatureConfiguration.CODEC);
  }

  @SubscribeEvent
  public static void register(RegisterEvent event) {
    event.register(
        Registries.FEATURE,
        helper ->
            helper.register(
                ResourceLocation.fromNamespaceAndPath(CreateBioships.MODID, "development_floor"),
                new DevelopmentFloorFeature()));
  }

  @Override
  public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
    var level = context.level();
    int y = context.chunkGenerator().getSpawnHeight(level) - 1;
    int startX = context.origin().getX() & ~15;
    int startZ = context.origin().getZ() & ~15;
    var palette =
        DevelopmentTestBlocks.getFloorBlocks().values().stream().map(h -> h.get()).toList();
    var surface = DevelopmentTestBlocks.getFloorBlocks().get("stone_bricks").get();
    for (int x = startX; x < startX + 16; x++) {
      for (int z = startZ; z < startZ + 16; z++) {
        var pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).is(surface)
            && level.getBlockState(pos.below()).is(Blocks.BEDROCK)) {
          level.setBlock(
              pos,
              palette.get(context.random().nextInt(palette.size())).defaultBlockState(),
              Block.UPDATE_CLIENTS);
        }
      }
    }
    return true;
  }
}
