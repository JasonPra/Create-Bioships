package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CoreFacePlacementTests {
  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "core_face_placement")
  public static void every_face_accepts_blocks_despite_combat_hurtboxes(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(608, 80, 128);
    for (int x = 37; x <= 39; x++) for (int z = 7; z <= 9; z++) level.setChunkForced(x, z, true);
    for (int x = -5; x <= 5; x++)
      for (int z = -5; z <= 5; z++)
        level.setBlock(origin.offset(x, -3, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    level.isPositionEntityTicking(origin), "Waiting for placement chunks"))
        .thenExecute(
            () -> {
              var flesh =
                  BuiltInRegistries.BLOCK.get(
                      ResourceLocation.fromNamespaceAndPath("biomancy", "packed_flesh"));
              var profile =
                  new com.mojang.authlib.GameProfile(UUID.randomUUID(), "face-placement-test");
              var player =
                  new net.minecraft.server.level.ServerPlayer(
                      level.getServer(),
                      level,
                      profile,
                      net.minecraft.server.level.ClientInformation.createDefault());
              player.connection =
                  new net.neoforged.neoforge.common.util.FakePlayer(level, profile).connection;
              player.setPos(Vec3.atCenterOf(origin).add(0, 0, -4));
              player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(flesh.asItem(), 64));
              level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
              for (var face : Direction.values()) {
                player.gameMode.useItemOn(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        Vec3.atCenterOf(origin).relative(face, .5), face, origin, false));
                helper.assertTrue(
                    level.getBlockState(origin.relative(face)).is(flesh),
                    "Unassembled core rejected face " + face);
                level.removeBlock(origin.relative(face), false);
              }
              var body =
                  SubLevelAssemblyHelper.assembleBlocks(
                      level, origin, List.of(origin), new BoundingBox3i(origin, origin));
              var core = body.getPlot().getCenterBlock();
              CoreAssemblyRules.mark(level, core);
              helper.runAfterDelay(
                  5,
                  () -> {
                    var normal = CombatAdapter.target(body);
                    var weak = CombatAdapter.coreTarget(body);
                    helper.assertTrue(
                        !normal.blocksBuilding && !weak.blocksBuilding,
                        "Invisible adapters still obstruct building");
                    for (var face : Direction.values()) {
                      var cell = core.relative(face);
                      var world = body.logicalPose().transformPosition(Vec3.atCenterOf(cell));
                      var box = new AABB(world.add(-.45, -.45, -.45), world.add(.45, .45, .45));
                      normal.setPos(world);
                      normal.setBoundingBox(box);
                      weak.setPos(world);
                      weak.setBoundingBox(box);
                      helper.assertTrue(
                          level.isUnobstructed(player, Shapes.create(box)),
                          "Hurtbox blocked vanilla placement on " + face);
                      player.gameMode.useItemOn(
                          player,
                          level,
                          player.getMainHandItem(),
                          InteractionHand.MAIN_HAND,
                          new BlockHitResult(
                              Vec3.atCenterOf(core).relative(face, .5), face, core, false));
                      helper.assertTrue(
                          level.getBlockState(cell).is(flesh),
                          "Assembled core rejected face " + face);
                      level.removeBlock(cell, false);
                    }
                    helper.succeed();
                  });
            });
  }
}
