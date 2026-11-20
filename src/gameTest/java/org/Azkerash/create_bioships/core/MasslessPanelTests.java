package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class MasslessPanelTests {
  @GameTest(template = "test_empty", batch = "massless_panels", timeoutTicks = 100)
  public static void panels_add_no_native_mass_buoyancy_hunger_or_oxygen_demand(GameTestHelper h) {
    var l = h.getLevel();
    var at = new BlockPos(8700, 100, 128);
    for (int x = -2; x <= 12; x++) {
      for (int z = -2; z <= 10; z++) {
        l.setChunkForced((at.getX() + x) >> 4, (at.getZ() + z) >> 4, true);
        l.setBlock(at.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
    }
    var cells = new ArrayList<BlockPos>();
    for (int x = 0; x < 10; x++) {
      for (int z = 0; z < 8; z++) {
        var p = at.offset(x, 0, z);
        cells.add(p);
        l.setBlock(p, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    l.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var b =
        SubLevelAssemblyHelper.assembleBlocks(
            l, at, cells, new BoundingBox3i(at, at.offset(9, 0, 7)));
    var c = b.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(l, c);
    DevelopmentLifeSupport.pause(b);
    var before = SubLevelHunger.metabolism(b);
    double oxygen = SubLevelOxygen.status(b).demand();
    h.assertTrue(oxygen > 20, "Fixture does not exercise mass-scaled air demand");
    var panels =
        Map.of(
            c.above(),
            ComponentRegistry.NAME_TAG_DISPLAYER
                .get()
                .defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP),
            c.north(),
            ComponentRegistry.NERVE_PANEL
                .get()
                .defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH),
            c.west(),
            ComponentRegistry.VITAL_READOUT
                .get()
                .defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
    for (var e : panels.entrySet()) {
      var state = e.getValue().setValue(BlockStateProperties.WATERLOGGED, true);
      l.setBlock(e.getKey(), state, Block.UPDATE_ALL);
      h.assertTrue(
          PhysicsBlockPropertyHelper.getMass(l, e.getKey(), state) == 0
              && PhysicsBlockPropertyHelper.getVolume(state) == 0,
          "Panel has native weight or water displacement");
    }
    // Reloading a body rebuilds its mass from block data; cover that path too.
    b.buildMassTracker();
    b.updateMergedMassData(1);
    var after = SubLevelHunger.metabolism(b);
    h.assertTrue(
        Math.abs(after.mass() - before.mass()) < .000001
            && Math.abs(after.multiplier() - before.multiplier()) < .000001
            && Math.abs(SubLevelOxygen.status(b).demand() - oxygen) < .000001,
        "Display panels increased physics mass, hunger drain or oxygen demand");
    h.succeed();
  }
}
