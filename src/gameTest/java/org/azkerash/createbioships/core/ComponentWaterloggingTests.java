package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ComponentWaterloggingTests {
  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "waterlogged_component_catalog")
  public static void all_nonfull_component_types_place_in_water_and_keep_source_fluid(
      GameTestHelper h) {
    var l = h.getLevel();
    var base = new BlockPos(5632, 80, 128);
    var types =
        List.of(
            ComponentRegistry.BRAIN.get(),
            ComponentRegistry.LUNG.get(),
            ComponentRegistry.GILLS.get(),
            ComponentRegistry.ARMORED_GILLS.get(),
            ComponentRegistry.INTESTINE.get(),
            ComponentRegistry.NERVE_PANEL.get(),
            ComponentRegistry.VITAL_READOUT.get(),
            ComponentRegistry.CRAB_LEG.get(),
            ComponentRegistry.LIMB_SOCKET.get(),
            ComponentRegistry.LIMB_JOINT.get(),
            ComponentRegistry.LIMB_SWIVEL.get(),
            ComponentRegistry.LIMB_SEGMENT.get(),
            ComponentRegistry.LIMB_FOOT.get(),
            ComponentRegistry.LAND_EYE.get(),
            ComponentRegistry.AQUATIC_EYE.get());
    for (int cx = (base.getX() - 4) >> 4; cx <= (base.getX() + 40) >> 4; cx++) {
      for (int cz = 7; cz <= 9; cz++) {
        l.setChunkForced(cx, cz, true);
      }
    }
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "WetComponents"));
    h.runAfterDelay(
        20,
        () -> {
          for (int i = 0; i < types.size(); i++) {
            var block = types.get(i);
            h.assertTrue(
                !block.defaultBlockState().getValue(BlockStateProperties.WATERLOGGED),
                "Dry component defaults unexpectedly contain water: " + block);
            var p = base.east(i * 3);
            l.setBlock(
                p.below(),
                com.github.elenterius.biomancy.init.ModBlocks.PACKED_FLESH
                    .get()
                    .defaultBlockState(),
                Block.UPDATE_ALL);
            l.setBlock(
                p.south(),
                com.github.elenterius.biomancy.init.ModBlocks.PACKED_FLESH
                    .get()
                    .defaultBlockState(),
                Block.UPDATE_ALL);
            l.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            var c =
                new BlockPlaceContext(
                    l,
                    player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(block),
                    new BlockHitResult(
                        Vec3.atCenterOf(p.south()), Direction.NORTH, p.south(), false));
            var state = block.getStateForPlacement(c);
            h.assertTrue(
                state != null
                    && state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED),
                "Component did not waterlog on placement: " + block);
            l.setBlock(p, state, Block.UPDATE_ALL);
          }
          h.runAfterDelay(
              80,
              () -> {
                for (int i = 0; i < types.size(); i++) {
                  var s = l.getBlockState(base.east(i * 3));
                  h.assertTrue(
                      s.is(types.get(i))
                          && s.getValue(BlockStateProperties.WATERLOGGED)
                          && s.getFluidState().is(net.minecraft.tags.FluidTags.WATER),
                      "Submerged component lost itself or its water: " + types.get(i));
                }
                h.succeed();
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 240, batch = "waterlogged_eye_rebuild")
  public static void both_eyes_survive_submerged_assembly_and_native_block_move_back_to_world(
      GameTestHelper h) {
    var l = h.getLevel();
    var base = new BlockPos(5760, 80, 128);
    for (int cx = 359; cx <= 361; cx++) {
      for (int cz = 7; cz <= 9; cz++) {
        l.setChunkForced(cx, cz, true);
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var cells = new ArrayList<BlockPos>();
          for (int x = 0; x <= 3; x++) {
            var p = base.east(x);
            l.setBlock(
                p,
                (x == 0
                        ? ComponentRegistry.CORE.get()
                        : com.github.elenterius.biomancy.init.ModBlocks.PACKED_FLESH.get())
                    .defaultBlockState(),
                Block.UPDATE_ALL);
            cells.add(p);
          }
          for (var p : List.of(base.east().north(), base.east(3).north())) {
            l.setBlock(
                p,
                (p.equals(base.east().north())
                        ? ComponentRegistry.LAND_EYE
                        : ComponentRegistry.AQUATIC_EYE)
                    .get()
                    .defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.WATERLOGGED, true),
                Block.UPDATE_ALL);
            cells.add(p);
          }
          var body =
              dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                  l,
                  base,
                  cells,
                  new dev.ryanhcode.sable.companion.math.BoundingBox3i(base.north(), base.east(3)));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(l, core);
          DevelopmentLifeSupport.pause(body);
          var from = cells.stream().map(p -> p.subtract(base).offset(core)).toList();
          h.runAfterDelay(
              40,
              () -> {
                for (var p : List.of(core.east().north(), core.east(3).north())) {
                  h.assertTrue(
                      l.getBlockState(p).getBlock()
                              instanceof org.azkerash.createbioships.components.logic.eye.EyeBlock
                          && l.getBlockState(p).getValue(BlockStateProperties.WATERLOGGED),
                      "Assembly washed away an eye");
                }
                var destination = base.south(8);
                for (int x = -1; x <= 4; x++) {
                  for (int z = -2; z <= 1; z++) {
                    for (int y = 0; y <= 2; y++) {
                      l.setBlock(
                          destination.offset(x, y, z),
                          Blocks.WATER.defaultBlockState(),
                          Block.UPDATE_ALL);
                    }
                  }
                }
                dev.ryanhcode.sable.api.SubLevelAssemblyHelper.moveBlocks(
                    l,
                    new dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform(
                        core, destination, 0, Rotation.NONE, l),
                    from);
                h.runAfterDelay(
                    40,
                    () -> {
                      for (var p :
                          List.of(destination.east().north(), destination.east(3).north())) {
                        h.assertTrue(
                            l.getBlockState(p).getBlock()
                                    instanceof
                                    org.azkerash.createbioships.components.logic.eye.EyeBlock
                                && l.getBlockState(p).getValue(BlockStateProperties.WATERLOGGED),
                            "Rebuilding into world water lost an eye");
                      }
                      h.succeed();
                    });
              });
        });
  }
}
