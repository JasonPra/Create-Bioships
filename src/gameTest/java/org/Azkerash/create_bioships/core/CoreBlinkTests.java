package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CoreBlinkTests {
  @GameTest(template = "test_empty", timeoutTicks = 220)
  public static void core_blinks_when_assembled_and_prevents_disassembly(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = new BlockPos(32, 80, 32);
    var idle = pos.east(8);
    level.getChunk(2, 2);
    for (int x = -3; x <= 3; x++)
      for (int z = -3; z <= 3; z++)
        level.setBlock(pos.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(idle, Create_bioships.CORE.get().defaultBlockState(), 3);
    level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    helper.assertTrue(
        level.getBlockState(pos).getValue(CoreBlock.BLINK), "Unassembled core is not closed-eyed");
    helper.assertTrue(level.getBlockEntity(pos) == null, "Core acquired an unwanted block entity");
    var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    for (var wrench :
        java.util.List.of(
            Create_bioships.BIOSHIP_WRENCH.get(), com.simibubi.create.AllItems.WRENCH.get())) {
      player.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND, wrench.getDefaultInstance());
      for (boolean sneaking : new boolean[] {false, true}) {
        player.setShiftKeyDown(sneaking);
        var before = level.getBlockState(pos);
        var context =
            new net.minecraft.world.item.context.UseOnContext(
                level,
                player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                player.getMainHandItem(),
                new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos),
                    net.minecraft.core.Direction.UP,
                    pos,
                    false));
        helper.assertTrue(
            wrench.useOn(context) == net.minecraft.world.InteractionResult.FAIL,
            "Wrench did not reject the core");
        helper.assertTrue(
            level.getBlockState(pos).equals(before), "Wrench changed or collected core");
      }
    }
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, java.util.List.of(pos), new BoundingBox3i(pos, pos));
    helper.assertTrue(body != null, "Sable could not assemble the core");
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    var logical = body.getPlot().getCenterBlock();
    sample(helper, body, logical, idle, new int[2], 0);
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void core_has_native_recipe_no_drops_and_cannot_be_picked(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = new BlockPos(64, 80, 32);
    level.getChunk(4, 2);
    var state = Create_bioships.CORE.get().defaultBlockState();
    level.setBlock(pos, state, 3);
    helper.assertTrue(
        Create_bioships.CORE.get().getCloneItemStack(level, pos, state).isEmpty(),
        "Pick block yielded a core");
    for (var tool :
        java.util.List.of(
            net.minecraft.world.item.ItemStack.EMPTY,
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)))
      helper.assertTrue(
          net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, tool)
              .isEmpty(),
          "Breaking the core yielded drops");
    var recipe =
        level
            .getRecipeManager()
            .byKey(
                net.minecraft.resources.ResourceLocation.parse(
                    "create_bioships:bio_forging/living_core"))
            .orElseThrow();
    helper.assertTrue(
        recipe.value().getResultItem(level.registryAccess()).is(Create_bioships.CORE_ITEM.get()),
        "Bio-Forge recipe output is not the core");
    level.destroyBlock(pos, true);
    helper.assertTrue(
        level
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1))
            .isEmpty(),
        "Destroyed core spawned an item");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void ordinary_grids_allow_assemblers_and_native_disassembly(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(96, 80, 32);
    level.getChunk(6, 2);
    level.setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, java.util.List.of(origin), new BoundingBox3i(origin, origin));
    var logical = body.getPlot().getCenterBlock();
    var site = logical.east();
    var snapshot =
        net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, site);
    var assembler =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.parse("simulated:physics_assembler"));
    level.setBlock(site, assembler.defaultBlockState(), 3);
    var placement =
        new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(
            snapshot,
            Blocks.STONE.defaultBlockState(),
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(placement);
    helper.assertTrue(!placement.isCanceled(), "Ordinary grid rejected an assembler");
    snapshot.restore();
    var target = origin.east(4);
    dev.simulated_team.simulated.util.SimAssemblyHelper.disassembleSubLevel(
        level, body, logical, target, net.minecraft.world.level.block.Rotation.NONE, false);
    helper.assertTrue(
        level.getBlockState(target).is(Blocks.STONE), "Ordinary grid could not disassemble");
    level.removeBlock(target, false);
    helper.succeed();
  }

  private static void sample(
      GameTestHelper helper,
      ServerSubLevel body,
      BlockPos pos,
      BlockPos idle,
      int[] counts,
      int tick) {
    helper.runAfterDelay(
        1,
        () -> {
          var level = helper.getLevel();
          var state = level.getBlockState(pos);
          helper.assertTrue(
              level.getBlockState(idle).getValue(CoreBlock.BLINK),
              "Unassembled core blinked or opened");
          if (tick >= 26 && tick <= 100)
            helper.assertTrue(
                !state.getValue(CoreBlock.BLINK), "Assembled core did not default open-eyed");
          if (tick >= 26)
            helper.assertTrue(state.getValue(CoreBlock.WAKE) == 0, "Wake animation replayed");
          counts[state.getValue(CoreBlock.BLINK) ? 1 : 0]++;
          if (tick < 170) {
            sample(helper, body, pos, idle, counts, tick + 1);
            return;
          }
          helper.assertTrue(
              counts[0] > 100 && counts[1] >= 7, "The original double-blink cycle did not run");
          helper.assertTrue(
              level.getBlockEntity(pos) == null, "Assembly added an unwanted block entity");
          helper.assertTrue(
              CoreAssemblyRules.isCreature(body)
                  && body.getUserDataTag().hasUUID(CoreAssemblyRules.ID),
              "Core grid lacks persistent creature identity");
          var assembler =
              net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                  net.minecraft.resources.ResourceLocation.parse("simulated:physics_assembler"));
          helper.assertTrue(
              assembler
                  instanceof
                  dev.simulated_team.simulated.content.blocks.physics_assembler
                      .PhysicsAssemblerBlock,
              "Native assembler fixture missing");
          var site = pos.east();
          var snapshot =
              net.neoforged.neoforge.common.util.BlockSnapshot.create(
                  level.dimension(), level, site);
          level.setBlock(site, assembler.defaultBlockState(), 3);
          var placement =
              new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(
                  snapshot,
                  state,
                  helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
          net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(placement);
          helper.assertTrue(
              placement.isCanceled(), "Player could place assembler on creature grid");
          snapshot.restore();
          var target = idle.east(2);
          dev.simulated_team.simulated.util.SimAssemblyHelper.disassembleSubLevel(
              level, body, pos, target, net.minecraft.world.level.block.Rotation.NONE, false);
          helper.assertTrue(
              level.getBlockState(target).isAir()
                  && !body.isRemoved()
                  && level.getBlockState(pos).is(Create_bioships.CORE.get()),
              "Native disassembly removed a grid with a core");
          if (!body.isRemoved())
            SubLevelContainer.getContainer(level)
                .removeSubLevel(body, SubLevelRemovalReason.REMOVED);
          level.removeBlock(target, false);
          level.removeBlock(idle, false);
          helper.succeed();
        });
  }
}
