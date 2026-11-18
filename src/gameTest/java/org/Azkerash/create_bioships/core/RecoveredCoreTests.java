package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.core.CoreGogglesDisplay;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class RecoveredCoreTests {
  @GameTest(template = "test_empty", batch = "recovered_dead_core", timeoutTicks = 100)
  public static void an_edge_case_recovered_core_item_cannot_revive_the_creature(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, java.util.List.of(origin), new BoundingBox3i(origin, origin));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelData.trust(body, 37);
    SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "test_death");
    var item = ComponentRegistry.CORE_ITEM.get().getDefaultInstance();
    ((CoreGogglesDisplay) level.getBlockEntity(core)).saveToItem(item, level.registryAccess());
    var tag = item.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag();
    helper.assertTrue(
        tag.getBoolean("RecoveredDeadCore"), "Recovered core item forgot terminal death");
    var placed = origin.east(4);
    level.setBlock(placed, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    var be =
        BlockEntity.loadStatic(placed, level.getBlockState(placed), tag, level.registryAccess());
    level.setBlockEntity(be);
    var recovered =
        SubLevelAssemblyHelper.assembleBlocks(
            level, placed, java.util.List.of(placed), new BoundingBox3i(placed, placed));
    var restored = recovered.getPlot().getCenterBlock();
    CoreAssemblyRules.changed(level, restored);
    ((CoreGogglesDisplay) level.getBlockEntity(restored)).restoreRecoveredCore();
    helper.assertTrue(
        SubLevelHealth.status(recovered).dead() && SubLevelHealth.status(recovered).current() == 0,
        "Recovered dead core revived on a new grid");
    helper.assertTrue(SubLevelData.trust(recovered) == 37, "Core snapshot lost saved data");
    helper.assertTrue(
        net.minecraft.world.level.block.Block.getDrops(
                level.getBlockState(restored), level, restored, level.getBlockEntity(restored))
            .isEmpty(),
        "Core gained ordinary drops");
    helper.runAfterDelay(10, helper::succeed);
  }
}
