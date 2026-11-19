package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.compat.letitwalk.CompactLimbController;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.core.legs.SubLevelLegs;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CompactNativeTests {
  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "compact_native_sustained")
  public static void empty_brain_stands_without_requests_and_after_movement_stops(
      GameTestHelper h) {
    runSmall(h, 4096, 20, 3, false, true);
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "compact_native_sustained")
  public static void small_creature_survives_second_physics_spawn_phase(GameTestHelper h) {
    runSmall(h, 4160, 21, 3);
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "compact_native_sustained")
  public static void small_creature_survives_third_physics_spawn_phase(GameTestHelper h) {
    runSmall(h, 4224, 22, 3);
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "compact_native_sustained")
  public static void minimum_height_default_tuning_supports_small_creature(GameTestHelper h) {
    runSmall(h, 4288, 20, 1);
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "compact_native_sustained")
  public static void medium_height_default_tuning_supports_small_creature(GameTestHelper h) {
    runSmall(h, 4352, 21, 2);
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "compact_native_sustained")
  public static void minimum_height_native_suspension_handles_armored_load(GameTestHelper h) {
    runSmall(h, 4416, 20, 1, true);
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "compact_native_sustained")
  public static void maximum_height_native_suspension_handles_armored_load(GameTestHelper h) {
    runSmall(h, 4480, 21, 3, true);
  }

  private static void runSmall(GameTestHelper h, int baseX, int delay, int height) {
    runSmall(h, baseX, delay, height, false);
  }

  private static void runSmall(
      GameTestHelper h, int baseX, int delay, int height, boolean armored) {
    runSmall(h, baseX, delay, height, armored, false);
  }

  private static void runSmall(
      GameTestHelper h, int baseX, int delay, int height, boolean armored, boolean editRestPose) {
    var level = h.getLevel();
    var base = new BlockPos(baseX, 80, 128);
    for (int x = (baseX >> 4) - 2; x <= (baseX >> 4) + 2; x++)
      for (int z = 6; z <= 10; z++) level.setChunkForced(x, z, true);
    for (int x = -18; x <= 18; x++)
      for (int z = -18; z <= 25; z++)
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
    h.runAfterDelay(
        delay,
        () -> {
          var cells = new ArrayList<BlockPos>();
          var core = base.above(2);
          var flesh =
              net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                  net.minecraft.resources.ResourceLocation.parse("biomancy:packed_flesh"));
          var entries =
              Map.of(
                  core,
                  ComponentRegistry.CORE.get().defaultBlockState(),
                  core.west(),
                  ComponentRegistry.BRAIN.get().defaultBlockState(),
                  core.west().below(),
                  flesh.defaultBlockState(),
                  core.below(),
                  ComponentRegistry.STOMACH.get().defaultBlockState(),
                  core.east().below(),
                  ComponentRegistry.MOUTH.get().defaultBlockState());
          entries.forEach(
              (p, s) -> {
                level.setBlock(p, s, 3);
                cells.add(p);
              });
          for (int x : new int[] {-1, 1})
            for (int z : new int[] {-1, 1}) {
              var p = base.offset(x, 1, z);
              level.setBlock(
                  p,
                  ComponentRegistry.CRAB_LEG
                      .get()
                      .defaultBlockState()
                      .setValue(CrabLegBlock.HEIGHT, height)
                      .setValue(
                          BlockStateProperties.HORIZONTAL_FACING,
                          z < 0 ? Direction.NORTH : Direction.SOUTH),
                  3);
              cells.add(p);
            }
          if (armored) {
            for (int x = -1; x <= 1; x++)
              for (int z = -1; z <= 1; z++)
                for (int y = 3; y <= 4; y++) {
                  var cell = base.offset(x, y, z);
                  level.setBlock(cell, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), 3);
                  cells.add(cell);
                }
          }
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  core,
                  cells,
                  new BoundingBox3i(base.offset(-1, 1, -1), base.offset(1, armored ? 4 : 2, 1)));
          var logical = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, logical);
          DevelopmentLifeSupport.pause(body);
          SubLevelLegs.request(body);
          double capacity = SubLevelHealth.status(body).maximum();
          class Sample implements Runnable {
            int tick;
            BlockPos tuned;
            Set<UUID> originalSegments;

            public void run() {
              tick += 5;
              if ((tick >= 360 && tick < 440) || tick == 520)
                BrainMotorControls.move(body, new Vec3(.4, 0, 0), 0, 20);
              if (tick == 440) BrainMotorControls.stop(body);
              if (tick < 360 || (tick >= 440 && tick < 520) || tick >= 540)
                h.assertTrue(
                    BrainMotorControls.current(body).expires() == 0
                        && BrainMotorControls.current(body).velocity().equals(Vec3.ZERO),
                    "Standing invented or retained a movement request");
              h.assertTrue(
                  org.Azkerash.create_bioships
                      .core
                      .brain
                      .SubLevelBrains
                      .get(body)
                      .inventory()
                      .isEmpty(),
                  "Standing regression unexpectedly contains a behavior card");
              if (tick == 40) {
                var struckPart = SubLevelLegs.segments(body).getFirst();
                double armor = SubLevelArmor.status(body).current();
                var attacker =
                    new net.neoforged.neoforge.common.util.FakePlayer(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CompactArmor"));
                h.assertTrue(
                    SubLevelArmor.absorb(
                                struckPart,
                                struckPart.getPlot().getCenterBlock(),
                                level.damageSources().playerAttack(attacker),
                                2)
                            == 0
                        && SubLevelArmor.status(body).current() == armor - 2,
                    "Generated limb hits bypassed its socket armor");

                h.assertTrue(
                    SubLevelLegs.segments(body).size() == 12,
                    "Compact bones are not real sublevels");
                h.assertTrue(
                    SubLevelCreatureParts.members(body).size() == 13,
                    "Generated limbs lack shared creature membership");
                h.assertTrue(
                    SubLevelHealth.status(body).maximum() == capacity,
                    "Hidden limb tissue duplicated creature health");
                for (var part : SubLevelLegs.segments(body))
                  h.assertTrue(
                      SubLevelCreatureParts.root(part) == body, "Limb lost its brain/health owner");
              }
              if (tick >= 140) {
                var up = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0));
                var center = body.logicalPose().transformPosition(Vec3.atCenterOf(logical.below()));
                double clearance = center.y - .5 - base.getY();
                if (tick % 40 == 0)
                  System.out.println(
                      "COMPACT_NATIVE_SOAK height="
                          + height
                          + " armored="
                          + armored
                          + " tick="
                          + tick
                          + " up="
                          + up.y
                          + " clearance="
                          + clearance
                          + " contacts="
                          + SubLevelLegs.contacts(body));
                h.assertTrue(
                    up.y > .85, "Small creature lost upright stance at " + tick + ": " + up.y);
                h.assertTrue(
                    clearance > height - .8 && clearance < height + 1.2,
                    "Small creature collapsed/launched at " + tick + ": " + clearance);
                h.assertTrue(
                    !SubLevelLegs.collisionOverlaps(
                        body,
                        new AABB(
                            base.getX() - 18,
                            base.getY() - 1,
                            base.getZ() - 18,
                            base.getX() + 19,
                            base.getY(),
                            base.getZ() + 26),
                        .08),
                    "Native limb penetrated terrain at "
                        + tick
                        + ": "
                        + SubLevelLegs.collisionBounds(body));
              }
              if (editRestPose && tick == 180) {
                for (int x = 7; x <= 9; x++)
                  for (int z = -1; z <= 1; z++)
                    level.setBlock(base.offset(x, 0, z), Blocks.BEDROCK.defaultBlockState(), 3);
                for (var pose : SubLevelLegs.poses(body))
                  for (int i = 0; i < 3; i++)
                    new org.Azkerash.create_bioships.compat.letitwalk.SocketJointSettings(0, 0)
                        .save(
                            body,
                            org.Azkerash.create_bioships.compat.letitwalk.SocketJointSettings
                                .compact(pose.cell(), i));
                var goal =
                    org.Azkerash.create_bioships
                        .core
                        .pathfinding
                        .SubLevelPathfinder
                        .position(body)
                        .add(8, 1, 0);
                h.assertTrue(
                    org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.stepHeight(body)
                        == 0,
                    "Locked socket limits did not reach the grid's motion profile");
                h.assertTrue(
                    !org.Azkerash.create_bioships
                        .core
                        .pathfinding
                        .SubLevelPathfinder
                        .find(
                            body,
                            goal,
                            org.Azkerash.create_bioships
                                .core
                                .pathfinding
                                .SubLevelPathfinder
                                .Mode
                                .GROUND)
                        .found(),
                    "Pathfinder routed locked joints up a full block");
                for (var pose : SubLevelLegs.poses(body))
                  for (int i = 0; i < 3; i++)
                    org.Azkerash.create_bioships.compat.letitwalk.SocketJointSettings.DEFAULT.save(
                        body,
                        org.Azkerash.create_bioships.compat.letitwalk.SocketJointSettings.compact(
                            pose.cell(), i));
                h.assertTrue(
                    org.Azkerash.create_bioships
                        .core
                        .pathfinding
                        .SubLevelPathfinder
                        .find(
                            body,
                            goal,
                            org.Azkerash.create_bioships
                                .core
                                .pathfinding
                                .SubLevelPathfinder
                                .Mode
                                .GROUND)
                        .found(),
                    "Default socket limits prevented an ordinary stair route");
              }
              if (editRestPose && tick == 240) {
                tuned = SubLevelLegs.poses(body).getFirst().cell();
                originalSegments =
                    SubLevelLegs.segments(body).stream()
                        .map(p -> p.getUniqueId())
                        .collect(java.util.stream.Collectors.toSet());
                new org.Azkerash.create_bioships.compat.letitwalk.LimbTuning(20, 1, 1)
                    .save(level, tuned);
              }
              if (editRestPose && (tick == 280 || tick == 320)) {
                var saved =
                    body
                        .getUserDataTag()
                        .getCompound(CompactLimbController.DATA)
                        .getList("Legs", 10)
                        .stream()
                        .map(t -> (net.minecraft.nbt.CompoundTag) t)
                        .filter(t -> t.getLong("Socket") == tuned.asLong())
                        .findFirst()
                        .orElseThrow();
                h.assertTrue(
                    saved.getInt("RestAngle") == (tick == 280 ? 20 : 0),
                    "Native chain ignored compact rest-pose editing/reset");
                h.assertTrue(
                    originalSegments.equals(
                        SubLevelLegs.segments(body).stream()
                            .map(p -> p.getUniqueId())
                            .collect(java.util.stream.Collectors.toSet())),
                    "Native rest-pose change replaced physical segments");
                if (tick == 280)
                  org.Azkerash.create_bioships.compat.letitwalk.LimbTuning.DEFAULT.save(
                      level, tuned);
              }
              if (tick == 350) {
                var ids =
                    SubLevelLegs.segments(body).stream()
                        .map(dev.ryanhcode.sable.sublevel.ServerSubLevel::getUniqueId)
                        .collect(java.util.stream.Collectors.toSet());
                try {
                  var field = SubLevelLegs.class.getDeclaredField("CONTROLLERS");
                  field.setAccessible(true);
                  @SuppressWarnings("unchecked")
                  var controllers =
                      (Map<dev.ryanhcode.sable.sublevel.ServerSubLevel, CompactLimbController>)
                          field.get(null);
                  var old = controllers.remove(body);
                  old.save();
                  old.close();
                  var saved = body.getUserDataTag().getCompound(CompactLimbController.DATA).copy();
                  var delayed = saved.copy();
                  var pending = delayed.getList("Legs", 10).getCompound(0);
                  var missing = UUID.randomUUID();
                  var oldId =
                      pending
                          .getCompound("Leg")
                          .getList("segments", 10)
                          .getCompound(0)
                          .getUUID("id");
                  for (var definition :
                      List.of(
                          pending.getCompound("Leg"),
                          pending.getCompound("Runtime").getCompound("built"),
                          pending.getCompound("Runtime").getCompound("BioshipSocketBase")))
                    for (var rawSegment : definition.getList("segments", 10)) {
                      var segment = (net.minecraft.nbt.CompoundTag) rawSegment;
                      if (segment.hasUUID("id") && segment.getUUID("id").equals(oldId))
                        segment.putUUID("id", missing);
                    }
                  var rootData = body.getUserDataTag().copy();
                  rootData.put(CompactLimbController.DATA, delayed);
                  body.setUserDataTag(rootData);
                  int count =
                      dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                          .getLoadedCount();
                  var waiting = new CompactLimbController(body);
                  waiting.update();
                  h.assertTrue(
                      dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                              .getLoadedCount()
                          == count,
                      "A temporarily missing segment caused duplicate limb allocation");
                  waiting.close();
                  rootData = body.getUserDataTag().copy();
                  rootData.put(CompactLimbController.DATA, saved);
                  body.setUserDataTag(rootData);
                  controllers.put(body, new CompactLimbController(body));
                } catch (ReflectiveOperationException e) {
                  throw new IllegalStateException(e);
                }
                h.assertTrue(
                    SubLevelLegs.segments(body).stream()
                        .map(dev.ryanhcode.sable.sublevel.ServerSubLevel::getUniqueId)
                        .collect(java.util.stream.Collectors.toSet())
                        .equals(ids),
                    "Reload duplicated or replaced saved limb bodies");
              }
              if (tick == 650) {
                var part = SubLevelLegs.segments(body).getFirst();
                var player =
                    new net.neoforged.neoforge.common.util.FakePlayer(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CompactPickup"));
                var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                player.setItemInHand(
                    hand,
                    new net.minecraft.world.item.ItemStack(Create_bioships.BIOSHIP_WRENCH.get()));
                player.setShiftKeyDown(true);
                var cell = part.getPlot().getCenterBlock();
                var context =
                    new net.minecraft.world.item.context.UseOnContext(
                        player,
                        hand,
                        new BlockHitResult(Vec3.atCenterOf(cell), Direction.UP, cell, false));
                var socket = CompactLimbController.socketContext(context).getClickedPos();
                var oldState = level.getBlockState(socket);
                var oldIds =
                    SubLevelLegs.segments(body).stream()
                        .map(dev.ryanhcode.sable.sublevel.ServerSubLevel::getUniqueId)
                        .collect(java.util.stream.Collectors.toSet());
                new org.Azkerash.create_bioships.compat.letitwalk.LimbTuning(15, 1.75, 1.5)
                    .save(level, socket);
                h.assertTrue(
                    Create_bioships.BIOSHIP_WRENCH.get().useOn(context).consumesAction()
                        && level.getBlockState(socket).isAir(),
                    "Wrench hit did not remove the owning socket");
                h.assertTrue(
                    org.Azkerash.create_bioships
                        .compat
                        .letitwalk
                        .LimbTuning
                        .get(level, socket)
                        .equals(org.Azkerash.create_bioships.compat.letitwalk.LimbTuning.DEFAULT),
                    "Wrench removal retained limb tuning");
                // Replace immediately: even before the next physics step this must be a new
                // component.
                level.setBlock(
                    socket,
                    ComponentRegistry.CRAB_LEG
                        .get()
                        .defaultBlockState()
                        .setValue(
                            BlockStateProperties.HORIZONTAL_FACING,
                            oldState.getValue(BlockStateProperties.HORIZONTAL_FACING)),
                    3);
                h.runAfterDelay(
                    10,
                    () -> {
                      h.assertTrue(
                          SubLevelLegs.segments(body).size() == 12
                              && SubLevelCreatureParts.members(body).size() == 13,
                          "Replacement left stale or duplicate limb sublevels");
                      long retained =
                          SubLevelLegs.segments(body).stream()
                              .filter(p -> oldIds.contains(p.getUniqueId()))
                              .count();
                      h.assertTrue(
                          retained == 9,
                          "Same-tick replacement reused the removed limb's native identities");
                      h.assertTrue(
                          level.getBlockState(socket).getValue(CrabLegBlock.HEIGHT) == 1
                              && org.Azkerash.create_bioships
                                  .compat
                                  .letitwalk
                                  .LimbTuning
                                  .get(level, socket)
                                  .equals(
                                      org.Azkerash.create_bioships
                                          .compat
                                          .letitwalk
                                          .LimbTuning
                                          .DEFAULT),
                          "Replacement inherited removed limb settings");
                      h.assertTrue(
                          SubLevelHealth.status(body).damage() == 0,
                          "Wrench removal injured the creature");
                      h.succeed();
                    });
              } else h.runAfterDelay(5, () -> this.run());
            }
          }
          h.runAfterDelay(5, new Sample());
        });
  }
}
