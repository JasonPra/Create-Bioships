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
  public static void native_sacs_join_only_top_and_bottom_pipe_faces(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(2, 3, 2));
    var sac = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:storage_sac"));
    level.setBlock(pos, ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    for (var face : Direction.values()) {
      var adjacent = pos.relative(face);
      level.setBlock(adjacent, sac.defaultBlockState(), 3);
      helper.assertTrue(
          level.getBlockState(pos).getValue(IntestineBlock.CONNECTIONS.get(face))
              == (face.getAxis() == Direction.Axis.Y),
          "Wrong permitted sac port on " + face);
      level.removeBlock(adjacent, false);
      helper.assertTrue(
          !level.getBlockState(pos).getValue(IntestineBlock.CONNECTIONS.get(face)),
          "Removed sac left a stale connection on "
              + face
              + " adjacent="
              + level.getBlockState(adjacent)
              + " pipe="
              + level.getBlockState(pos));
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
    level.setBlock(origin.east(2).above(), sacBlock.defaultBlockState(), 3);
    var cells =
        List.of(origin, origin.east(), origin.east(2), origin.east(3), origin.east(2).above());
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, cells, new BoundingBox3i(origin, origin.east(3).above()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    var mouth = (MouthBlockEntity) level.getBlockEntity(core.east());
    var stomach = (StomachBlockEntity) level.getBlockEntity(core.east(3));
    var sac =
        (com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity)
            level.getBlockEntity(core.east(2).above());
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

  @GameTest(template = "test_empty", batch = "vertical_sac_stack", timeoutTicks = 100)
  public static void stacked_sacs_pull_bottom_first_compact_downward_and_exclude_side_ports(
      GameTestHelper h) {
    var l = h.getLevel();
    var o = h.absolutePos(new BlockPos(1, 3, 1));
    var sacBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:storage_sac"));
    l.setBlock(o, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    l.setBlock(o.east(), ComponentRegistry.MOUTH.get().defaultBlockState(), 3);
    l.setBlock(o.east(2), ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    l.setBlock(o.east(3), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    for (var pos : List.of(o.east(2).above(), o.east(2).above(2), o.east(2).south()))
      l.setBlock(pos, sacBlock.defaultBlockState(), 3);
    var cells =
        List.of(
            o,
            o.east(),
            o.east(2),
            o.east(3),
            o.east(2).above(),
            o.east(2).above(2),
            o.east(2).south());
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l, o, cells, new BoundingBox3i(o, o.east(3).above(2).south()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(l, core);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    var stomach = (StomachBlockEntity) l.getBlockEntity(core.east(3));
    var lower =
        (com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity)
            l.getBlockEntity(core.east(2).above());
    var upper =
        (com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity)
            l.getBlockEntity(core.east(2).above(2));
    var group =
        SubLevelDigestion.groups(body).stream()
            .filter(g -> g.stomachs().contains(stomach.getBlockPos()))
            .findFirst()
            .orElseThrow();
    h.assertTrue(
        group.sacs().equals(List.of(lower.getBlockPos(), upper.getBlockPos())),
        "Sac order or side-port exclusion incorrect: " + group.sacs());
    lower.getInventory().insertItem(0, new ItemStack(Items.BEEF), false);
    upper.getInventory().insertItem(0, new ItemStack(Items.PORKCHOP, 2), false);
    StomachStorageRouting.service(stomach, group.sacs(), 1);
    h.assertTrue(
        stomach.getItem(0).is(Items.BEEF) && count(upper.getInventory(), Items.PORKCHOP) == 2,
        "Pull did not prefer bottom sac");
    h.assertTrue(
        StomachStorageRouting.compactStacks(l, group.sacs(), 0, 1) == 1,
        "Upper sac did not compact into newly free lower sac");
    h.assertTrue(
        count(lower.getInventory(), Items.PORKCHOP) == 2
            && count(upper.getInventory(), Items.PORKCHOP) == 0,
        "Stack compaction lost or duplicated food");
    StomachStorageRouting.service(stomach, group.sacs(), 1);
    h.assertTrue(
        count(lower.getInventory(), Items.PORKCHOP) == 1,
        "Could not retrieve upper food after compaction");
    var node =
        org.Azkerash.create_bioships.development.testing.network.DigestiveDebugSync.inventoryNode(
            body, lower.getBlockPos(), 4);
    h.assertTrue(
        node.slots() > 0 && node.used() == 1 && node.items() == 1,
        "Inventory debug snapshot missing recognized slots/counts");
    var b =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), l.registryAccess());
    var packet =
        new org.Azkerash.create_bioships.development.testing.network.DigestiveDebugPayload(
            body.getUniqueId(), List.of(node), false);
    org.Azkerash.create_bioships.development.testing.network.DigestiveDebugPayload.CODEC.encode(
        b, packet);
    h.assertTrue(
        org.Azkerash.create_bioships
            .development
            .testing
            .network
            .DigestiveDebugPayload
            .CODEC
            .decode(b)
            .equals(packet),
        "Inventory snapshot wire data lost");
    b.release();
    h.succeed();
  }

  private static int count(net.neoforged.neoforge.items.IItemHandler inventory, Item item) {
    int count = 0;
    for (int slot = 0; slot < inventory.getSlots(); slot++)
      if (inventory.getStackInSlot(slot).is(item))
        count += inventory.getStackInSlot(slot).getCount();
    return count;
  }
}
