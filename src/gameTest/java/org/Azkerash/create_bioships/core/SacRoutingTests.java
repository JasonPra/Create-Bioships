package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.intestine.IntestineBlock;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity;
import org.Azkerash.create_bioships.components.logic.stomach.*;
import org.Azkerash.create_bioships.core.digestion.SubLevelDigestion;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class SacRoutingTests {
  @GameTest(template = "test_empty", batch = "native_sac_connections")
  public static void native_sacs_join_all_six_pipe_faces(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(2, 3, 2));
    var sac = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:storage_sac"));
    level.setBlock(pos, ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    for (var face : Direction.values()) {
      var adjacent = pos.relative(face);
      level.setBlock(adjacent, sac.defaultBlockState(), 3);
      helper.assertTrue(
          level.getBlockState(pos).getValue(IntestineBlock.CONNECTIONS.get(face)),
          "Sac did not create pipe branch on " + face);
      level.removeBlock(adjacent, false);
      helper.assertTrue(
          !level.getBlockState(pos).getValue(IntestineBlock.CONNECTIONS.get(face)),
          "Removed sac left a stale connection");
    }
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "sublevel_sac_routing", timeoutTicks = 160)
  public static void overflow_food_and_components_use_native_sac_without_loss(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 3, 1));
    var sacBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:storage_sac"));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), ComponentRegistry.MOUTH.get().defaultBlockState(), 3);
    level.setBlock(origin.east(2), ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(3), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    level.setBlock(origin.east(2).south(), sacBlock.defaultBlockState(), 3);
    var cells =
        List.of(origin, origin.east(), origin.east(2), origin.east(3), origin.east(2).south());
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, cells, new BoundingBox3i(origin, origin.east(3).south()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    var mouth = (MouthBlockEntity) level.getBlockEntity(core.east());
    var stomach = (StomachBlockEntity) level.getBlockEntity(core.east(3));
    var sac =
        (com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity)
            level.getBlockEntity(core.east(2).south());
    var inventory = sac.getInventory();
    helper.assertTrue(
        SubLevelDigestion.groups(body).stream()
            .anyMatch(
                g ->
                    g.sacs().contains(sac.getBlockPos())
                        && g.stomachs().contains(stomach.getBlockPos())),
        "Native sac missing from cached network");
    for (int slot = 0; slot < 9; slot++) stomach.setItem(slot, new ItemStack(Items.PORKCHOP, 64));
    mouth.feed(new ItemStack(Items.PORKCHOP, 3), false);
    helper.assertTrue(
        SubLevelDigestion.transfer(mouth) == 1
            && mouth.getItem(0).getCount() == 2
            && count(inventory, Items.PORKCHOP) == 1,
        "Full stomach lost food instead of overflowing it");
    var flesh = BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:flesh_bits"));
    for (int slot = 9; slot < 27; slot++) stomach.setItem(slot, new ItemStack(flesh, 64));
    StomachStorageRouting.service(stomach, List.of(sac.getBlockPos()), 3);
    helper.assertTrue(
        count(inventory, flesh) == 64 && stomach.getItem(9).isEmpty(),
        "Component overflow did not free stomach space");
    stomach.setItem(0, ItemStack.EMPTY);
    StomachStorageRouting.service(stomach, List.of(sac.getBlockPos()), 3);
    helper.assertTrue(
        stomach.getItem(0).is(Items.PORKCHOP)
            && count(inventory, Items.PORKCHOP) == 0
            && count(inventory, flesh) == 64,
        "Stored food retrieval consumed components or lost food");
    var saved = sac.saveWithFullMetadata(level.registryAccess());
    helper.assertTrue(!saved.isEmpty(), "Native sac persistence unavailable");
    level.removeBlock(core.east(2), false);
    helper.assertTrue(
        SubLevelDigestion.transfer(mouth) == 0 && mouth.getItem(0).getCount() == 2,
        "Transfer bypassed broken intestine");
    helper.runAfterDelay(10, helper::succeed);
  }

  private static int count(net.neoforged.neoforge.items.IItemHandler inventory, Item item) {
    int count = 0;
    for (int slot = 0; slot < inventory.getSlots(); slot++)
      if (inventory.getStackInSlot(slot).is(item))
        count += inventory.getStackInSlot(slot).getCount();
    return count;
  }
}
