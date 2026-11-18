package org.Azkerash.create_bioships.components;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.logic.brain.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class BrainFoundationTests {
  @GameTest(template = "test_empty", batch = "brain_foundation", timeoutTicks = 120)
  public static void one_grid_inventory_centered_port_exact_wrench_move_and_capacity(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    var cells = new ArrayList<BlockPos>();
    cells.add(origin);
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    for (int x = 1; x <= 3; x++)
      for (int y = 0; y < 3; y++)
        for (int z = 0; z < 3; z++) {
          var p = origin.offset(x, y, z);
          cells.add(p);
          level.setBlock(p, ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
        }
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, cells, new BoundingBox3i(origin, origin.offset(3, 2, 2)));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelData.hungerPaused(body, true);
    var brain = SubLevelBrains.get(body);
    helper.assertTrue(
        brain.capacity() == 27
            && brain.port().equals(core.offset(2, 1, 0))
            && brain.face() == Direction.NORTH,
        "Full brain failed to center its single access port");
    helper.assertTrue(
        brain.cells().stream()
                .filter(
                    p -> level.getBlockState(p).getValue(BrainBlock.PORT) != BrainBlock.Port.NONE)
                .count()
            == 1,
        "Multiple brain access ports appeared");
    helper.assertTrue(
        !brain.canAdd(core.offset(4, 1, 1)) && !brain.canAdd(core.offset(8, 0, 0)),
        "Full brain allowed expansion or a second brain");
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "BrainWrench"));
    player.setItemInHand(
        InteractionHand.MAIN_HAND, Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
    var placement =
        new net.minecraft.world.item.context.BlockPlaceContext(
            player,
            InteractionHand.MAIN_HAND,
            ComponentRegistry.BRAIN.get().asItem().getDefaultInstance(),
            new BlockHitResult(
                Vec3.atCenterOf(core.offset(3, 1, 1)),
                Direction.EAST,
                core.offset(3, 1, 1),
                false));
    helper.assertTrue(
        ComponentRegistry.BRAIN.get().getStateForPlacement(placement) == null,
        "Native placement allowed a 28th brain block");
    helper.assertTrue(
        level
                .getBlockState(brain.port())
                .useItemOn(
                    player.getMainHandItem(),
                    level,
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(brain.port()), brain.face(), brain.port(), false))
            == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
        "Brain GUI consumed the wrench interaction");
    var chosen = core.offset(1, 2, 0);
    var result =
        Create_bioships.BIOSHIP_WRENCH
            .get()
            .useOn(
                new UseOnContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(chosen), Direction.UP, chosen, false)));
    helper.assertTrue(
        result.consumesAction() && brain.port().equals(chosen) && brain.face() == Direction.UP,
        "Wrench did not move port to the exact clicked brain face");
    helper.assertTrue(!brain.movePort(core, Direction.UP), "Port moved to a non-brain block");
    var memory = brain.inventory();
    var card = ComponentRegistry.BLANK_BEHAVIOR_CARD.get().getDefaultInstance();
    helper.assertTrue(
        memory.canPlaceItem(0, card) && !memory.canPlaceItem(0, new ItemStack(Items.STONE)),
        "Brain accepted ordinary storage items");
    var doc = card.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
    doc.getCompound("BehaviorGraph").putString("TestGraph", "remember this");
    card.set(
        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
        net.minecraft.world.item.component.CustomData.of(doc));
    memory.setItem(0, card);
    memory.setItem(26, card.copy());
    level.removeBlock(core.offset(3, 2, 2), false);
    helper.assertTrue(
        brain.capacity() == 26 && !memory.canPlaceItem(26, card) && !memory.getItem(26).isEmpty(),
        "Shrinking deleted or accepted new overflow cards");
    helper.assertTrue(
        !brain.canAdd(core.offset(4, 1, 1)) && brain.canAdd(core.offset(3, 2, 2)),
        "3 by 3 bounds were not enforced below maximum count");
    SubLevelBrains.forget(body);
    var restored = SubLevelBrains.get(body);
    helper.assertTrue(
        restored.port().equals(chosen)
            && restored.face() == Direction.UP
            && restored
                .inventory()
                .getItem(0)
                .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                .copyTag()
                .getCompound("BehaviorGraph")
                .getString("TestGraph")
                .equals("remember this"),
        "Grid persistence lost port or graph document");
    helper.assertTrue(
        !restored.inventory().removeItem(26, 1).isEmpty(),
        "Stored overflow could not be retrieved");
    for (var cell : new ArrayList<>(restored.cells())) level.removeBlock(cell, false);
    helper.assertTrue(
        restored.capacity() == 0 && !restored.inventory().getItem(0).isEmpty(),
        "Removing all physical brain blocks deleted grid data");
    level.setBlock(core.east(), ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
    helper.assertTrue(
        restored.capacity() == 1 && !restored.inventory().getItem(0).isEmpty(),
        "Rebuilding the brain failed to recover grid-owned cards");
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "brain_imported_topology", timeoutTicks = 100)
  public static void imported_disconnected_brains_have_only_one_active_group(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
    level.setBlock(origin.south(), ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east(), origin.south()),
            new BoundingBox3i(origin, origin.east().south()));
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    SubLevelData.hungerPaused(body, true);
    var brain = SubLevelBrains.get(body);
    helper.assertTrue(
        brain.capacity() == 1 && brain.cells().size() == 2,
        "Disconnected brains shared an active inventory capacity");
    helper.assertTrue(
        brain.cells().stream()
                .filter(
                    p -> level.getBlockState(p).getValue(BrainBlock.PORT) != BrainBlock.Port.NONE)
                .count()
            == 1,
        "Imported disconnected groups acquired multiple ports");
    var only = Set.of(BlockPos.ZERO);
    helper.assertTrue(
        BrainShape.canAdd(only, BlockPos.ZERO.east())
            && !BrainShape.canAdd(only, BlockPos.ZERO.east().south())
            && !BrainShape.canAdd(only, BlockPos.ZERO.east(5)),
        "Face adjacency policy failed");
    helper.succeed();
  }
}
