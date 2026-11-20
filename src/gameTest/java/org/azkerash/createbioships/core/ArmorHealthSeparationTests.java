package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ArmorHealthSeparationTests {
  @GameTest(template = "test_empty", batch = "armor_health_separation")
  public static void plates_protect_without_adding_healing_or_removing_biological_hit_points(
      GameTestHelper h) {
    var level = h.getLevel();
    var at = h.absolutePos(new BlockPos(2, 3, 2));
    level.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        at.west(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        at.east(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, at, List.of(at.west(), at, at.east()), new BoundingBox3i(at.west(), at.east()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    h.assertTrue(
        SubLevelHealth.status(body).maximum() == 20, "Armor plate inflated the biological HP pool");
    h.assertTrue(
        SubLevelArmor.status(body).maximum() == 20
            && SubLevelHealth.blocks(body).contains(core.east()),
        "Zero-HP armor disappeared from protection/topology");
    h.assertTrue(
        BlockHealthValues.health(ComponentRegistry.ARMORED_MAW.get().defaultBlockState()) == 10,
        "Armored organ lost its biological HP");
    SubLevelHealth.damage(body, 5, "test_injury");
    level.setBlock(
        core.east(2), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        SubLevelArmor.status(body).maximum() == 40, "Added armor did not increase protection");
    h.assertTrue(
        SubLevelHealth.status(body).maximum() == 20 && SubLevelHealth.status(body).current() == 15,
        "Adding armor increased or healed HP");
    level.removeBlock(core.east(2), false);
    h.assertTrue(
        SubLevelHealth.status(body).current() == 15
            && !SubLevelHealth.blocks(body).contains(core.east(2)),
        "Removed armor damaged HP or left an occupancy ghost");
    SubLevelHealth.refreshCapacity(body);
    h.assertTrue(
        SubLevelHealth.status(body).current() == 15 && SubLevelHealth.status(body).maximum() == 20,
        "Capacity refresh restored armor HP");
    h.assertTrue(
        SubLevelArmor.status(body).maximum() == 20,
        "Armor capacity was lost during health refresh");
    h.succeed();
  }
}
