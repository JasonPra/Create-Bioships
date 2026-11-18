package org.Azkerash.create_bioships.core;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlock;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class ComponentRegistryTests {
  @GameTest(template = "test_empty", batch = "component_registration")
  public static void component_ids_and_stomach_shell_remain_wired(GameTestHelper helper) {
    for (var id : List.of("living_core", "land_eye", "aquatic_eye", "vital_readout", "stomach")) {
      var key = ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, id);
      var block = BuiltInRegistries.BLOCK.get(key);
      helper.assertTrue(
          !block.defaultBlockState().isAir() && BuiltInRegistries.ITEM.get(key) == block.asItem(),
          "Block/item registry mismatch: " + id);
      helper.assertTrue(
          BlockHealthValues.health(block.defaultBlockState()) == 10,
          "Block health registration lost: " + id);
    }
    helper.assertTrue(
        ComponentRegistry.CORE_DISPLAY
                .get()
                .isValid(ComponentRegistry.CORE.get().defaultBlockState())
            && ComponentRegistry.CORE_DISPLAY
                .get()
                .isValid(ComponentRegistry.VITAL_READOUT.get().defaultBlockState()),
        "Shared display block-entity wiring changed");
    var pos = helper.absolutePos(new BlockPos(1, 2, 1));
    for (var direction : net.minecraft.core.Direction.values()) {
      var state =
          ComponentRegistry.STOMACH
              .get()
              .defaultBlockState()
              .setValue(StomachBlock.FACING, direction);
      helper.getLevel().setBlock(pos, state, 3);
      helper.assertTrue(
          helper.getLevel().getBlockEntity(pos) == null && !state.hasBlockEntity(),
          "Shell-only stomach unexpectedly has a block entity/GUI backing");
      helper.assertTrue(
          state.isCollisionShapeFullBlock(helper.getLevel(), pos),
          "Stomach does not fit a full flesh block");
      helper.assertTrue(
          state.rotate(Rotation.CLOCKWISE_90).getValue(BlockStateProperties.FACING)
              == Rotation.CLOCKWISE_90.rotate(direction),
          "Stomach rotation lost its facing");
    }
    var drops =
        Block.getDrops(
            ComponentRegistry.STOMACH.get().defaultBlockState(), helper.getLevel(), pos, null);
    helper.assertTrue(
        drops.size() == 1 && drops.getFirst().is(ComponentRegistry.STOMACH_ITEM.get()),
        "Stomach mining does not return its block item");
    helper.succeed();
  }
}
