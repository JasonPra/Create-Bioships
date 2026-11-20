package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.core.CoreGogglesDisplay;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class SubLevelHealthTests {
  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void pooled_health_tracks_grid_and_persists_death(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(2, 3, 2));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            java.util.List.of(origin, origin.east()),
            new BoundingBox3i(origin, origin.east()));
    helper.assertTrue(body != null, "Assembly failed");
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    var health = SubLevelHealth.status(body);
    helper.assertTrue(
        health.maximum() == 20 && health.current() == 20,
        "Grid blocks did not each contribute 10 HP: " + health);
    helper.assertTrue(
        BlockHealthValues.entries().size()
            == net.minecraft.core.registries.BuiltInRegistries.BLOCK.size(),
        "Missing registered block IDs");
    var core = body.getPlot().getCenterBlock();
    level.setBlock(core.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    helper.assertTrue(SubLevelHealth.status(body).maximum() == 30, "Added grid block not counted");
    SubLevelHealth.damage(body, 7, "arrow");
    level.removeBlock(core.above(), false);
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 13 && SubLevelHealth.status(body).maximum() == 20,
        "Removal healed damage or lost pooled health");
    var display = (CoreGogglesDisplay) level.getBlockEntity(core);
    display.update();
    var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
    display.addToGoggleTooltip(tooltip, false);
    helper.assertTrue(
        tooltip.stream().anyMatch(t -> t.getString().contains("13.0 / 20.0")),
        "Goggles do not show pooled health");
    SubLevelHealth.damage(body, 100, "arrow");
    health = SubLevelHealth.status(body);
    helper.assertTrue(
        health.current() == 0 && health.dead() && health.causeOfDeath().equals("arrow"),
        "Death not clamped and attributed: " + health);
    level.setBlock(core.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 0 && SubLevelHealth.status(body).dead(),
        "Growth revived dead grid");
    helper.assertTrue(
        body.getUserDataTag().getCompound(SubLevelHealth.DATA).getBoolean("dead"),
        "Death not persisted on sublevel");
    helper.succeed();
  }
}
