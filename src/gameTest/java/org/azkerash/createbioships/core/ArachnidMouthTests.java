package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.mouth.*;
import org.azkerash.createbioships.core.brain.*;
import org.azkerash.createbioships.core.brain.behavior.*;
import org.azkerash.createbioships.core.brain.hunting.HuntingCardData;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ArachnidMouthTests {
  private record Rig(ServerSubLevel body, MouthBlockEntity mouth) {}

  private static int fixtureIndex;

  private static Rig rig(GameTestHelper h) {
    var level = h.getLevel();
    // Physical rigs need real loaded terrain, independent of the GameTest
    // structure/plot origin chosen for the rest of the suite.
    var p = new BlockPos(42_000 + fixtureIndex++ * 64, 84, 128);
    for (int x = (p.getX() - 16) >> 4; x <= (p.getX() + 16) >> 4; x++) {
      for (int z = (p.getZ() - 16) >> 4; z <= (p.getZ() + 16) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    for (int x = -3; x <= 10; x++) {
      for (int z = -3; z <= 3; z++) {
        level.setBlock(p.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    level.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(p.above(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        p.east(),
        ComponentRegistry.ARACHNID_MAW
            .get()
            .defaultBlockState()
            .setValue(MouthBlock.FACING, Direction.EAST),
        Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, p, List.of(p, p.above(), p.east()), new BoundingBox3i(p, p.east().above()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    var card = new ItemStack(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get());
    HuntingCardData.write(card, "minecraft:cow");
    SubLevelBrains.get(body).inventory().setItem(0, card);
    return new Rig(body, (MouthBlockEntity) level.getBlockEntity(core.east()));
  }

  private static LivingEntity prey(GameTestHelper h, Rig rig) {
    var cow = EntityType.COW.create(h.getLevel());
    cow.setNoAi(true);
    cow.setNoGravity(true);
    var center = rig.body.logicalPose().transformPosition(Vec3.atCenterOf(rig.mouth.getBlockPos()));
    var direction = rig.body.logicalPose().orientation().transform(new org.joml.Vector3d(1, 0, 0));
    cow.setPos(
        center
            .add(new Vec3(direction.x, direction.y, direction.z).scale(1.6))
            .add(0, -cow.getBbHeight() / 2, 0));
    h.getLevel().addFreshEntity(cow);
    return cow;
  }

  private static String diagnostics(Rig rig, LivingEntity prey) {
    var center = rig.body.logicalPose().transformPosition(Vec3.atCenterOf(rig.mouth.getBlockPos()));
    var facing = rig.body.logicalPose().orientation().transform(new org.joml.Vector3d(1, 0, 0));
    var origin = center.add(facing.x * .65, facing.y * .65, facing.z * .65);
    var hit =
        rig.body
            .getLevel()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    origin,
                    prey.getBoundingBox().getCenter(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    prey));
    return "active="
        + rig.mouth.active()
        + " brain="
        + BrainBehaviors.active(rig.body)
        + " pose="
        + rig.body.logicalPose().orientation()
        + " prey="
        + prey.position()
        + " mouth="
        + center
        + " hit="
        + hit.getType()
        + " at="
        + hit.getLocation();
  }

  @GameTest(template = "test_empty", batch = "arachnid_grab", timeoutTicks = 120)
  public static void articulated_fangs_wind_up_grip_bite_and_release_preys_that_escape(
      GameTestHelper h) {
    var rig = rig(h);
    h.runAfterDelay(
        20,
        () -> {
          var cow = prey(h, rig);
          SubLevelData.hunger(rig.body, SubLevelData.maximumHunger(rig.body) / 2);
          BrainBehaviors.advance(rig.body);
          h.assertTrue(rig.mouth.tryGrabPrey(cow), "Arachnid mouth did not start grabbing prey");
          h.assertTrue(
              !rig.mouth.getBlockState().getValue(MouthBlock.GRABBING)
                  && cow.getHealth() == cow.getMaxHealth(),
              "Grab skipped its opening telegraph");
          h.runAfterDelay(
              ArachnidMandibles.WINDUP_TICKS + 2,
              () -> {
                h.assertTrue(
                    rig.mouth.getBlockState().getValue(MouthBlock.GRABBING)
                        && cow.getUUID().equals(rig.mouth.getGrabbedPrey()),
                    "Fanged mandibles did not close on prey");
                h.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Closed fangs did not bite");
                h.assertTrue(
                    !cow.isPassenger(), "Grip incorrectly turned prey into a mounted passenger");
                var saved = rig.mouth.saveWithFullMetadata(h.getLevel().registryAccess());
                h.assertTrue(
                    !saved.hasUUID("GrabbedPrey"),
                    "A live prey binding was persisted into an item/world save");
                cow.setPos(cow.position().add(8, 0, 0));
                h.runAfterDelay(
                    2,
                    () -> {
                      h.assertTrue(
                          !rig.mouth.isHandlingPrey()
                              && !rig.mouth.getBlockState().getValue(MouthBlock.GRABBING),
                          "Escaped prey remained bound");
                      h.assertTrue(
                          !rig.mouth.tryGrabPrey(cow), "Grab cooldown or range was bypassed");
                      cow.discard();
                      dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel())
                          .removeSubLevel(
                              rig.body,
                              dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "test_empty", batch = "arachnid_grab_wall", timeoutTicks = 100)
  public static void obstacle_and_removed_mouth_release_the_grip(GameTestHelper h) {
    var rig = rig(h);
    h.runAfterDelay(
        20,
        () -> {
          var cow = prey(h, rig);
          SubLevelData.hunger(rig.body, SubLevelData.maximumHunger(rig.body) / 2);
          BrainBehaviors.advance(rig.body);
          h.assertTrue(rig.mouth.tryGrabPrey(cow), "Grip setup failed: " + diagnostics(rig, cow));
          h.runAfterDelay(
              ArachnidMandibles.WINDUP_TICKS + 2,
              () -> {
                h.assertTrue(
                    rig.mouth.getGrabbedPrey() != null, "Fixture never established its grip");
                var center =
                    rig.body
                        .logicalPose()
                        .transformPosition(Vec3.atCenterOf(rig.mouth.getBlockPos()));
                var wall = BlockPos.containing(center.add(1, 0, 0));
                h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                h.runAfterDelay(
                    2,
                    () -> {
                      h.assertTrue(
                          !rig.mouth.isHandlingPrey(),
                          "Grip pulled prey through an intervening wall");
                      h.getLevel().removeBlock(wall, false);
                      h.getLevel().removeBlock(rig.mouth.getBlockPos(), false);
                      h.assertTrue(
                          !rig.mouth.isHandlingPrey(), "Destroyed mouth retained its victim");
                      cow.discard();
                      dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel())
                          .removeSubLevel(
                              rig.body,
                              dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "test_empty", batch = "arachnid_grab_lifetime", timeoutTicks = 200)
  public static void grips_expire_and_destroying_an_active_mouth_releases_its_victim(
      GameTestHelper h) {
    var rig = rig(h);
    h.runAfterDelay(
        20,
        () -> {
          var cow = prey(h, rig);
          // A vanilla cow has only 10 HP; timer checks require a victim that survives several
          // bites.
          cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
              .setBaseValue(200);
          cow.setHealth(cow.getMaxHealth());
          SubLevelData.hunger(rig.body, SubLevelData.maximumHunger(rig.body) / 2);
          BrainBehaviors.advance(rig.body);
          h.assertTrue(
              rig.mouth.tryGrabPrey(cow), "Timed grip did not start: " + diagnostics(rig, cow));
          h.runAfterDelay(
              ArachnidMandibles.WINDUP_TICKS + ArachnidMandibles.HOLD_TICKS + 2,
              () -> {
                h.assertTrue(
                    !rig.mouth.isHandlingPrey(), "Prey was held beyond the bounded duration");
                // The hunter can still bite normally during grab recovery; keep this fixture
                // alive long enough to isolate the second-grab/removal assertion.
                cow.setHealth(cow.getMaxHealth());
                h.runAfterDelay(
                    ArachnidMandibles.COOLDOWN_TICKS + 2,
                    () -> {
                      var center =
                          rig.body
                              .logicalPose()
                              .transformPosition(Vec3.atCenterOf(rig.mouth.getBlockPos()));
                      cow.setPos(center.add(1.6, -cow.getBbHeight() / 2, 0));
                      h.assertTrue(
                          rig.mouth.tryGrabPrey(cow),
                          "Later grab failed: hp="
                              + cow.getHealth()
                              + " alive="
                              + cow.isAlive()
                              + " removed="
                              + cow.isRemoved()
                              + " ready="
                              + rig.mouth
                                  .saveWithFullMetadata(h.getLevel().registryAccess())
                                  .getLong("GrabReadyAt")
                              + " now="
                              + h.getLevel().getGameTime()
                              + " spitting="
                              + rig.mouth.getBlockState().getValue(MouthBlock.SPITTING)
                              + " up="
                              + rig.body.logicalPose().orientation());
                      h.runAfterDelay(
                          ArachnidMandibles.WINDUP_TICKS + 2,
                          () -> {
                            h.assertTrue(
                                rig.mouth.getGrabbedPrey() != null,
                                "Second grip was not active before removal");
                            h.getLevel().removeBlock(rig.mouth.getBlockPos(), false);
                            h.assertTrue(
                                !rig.mouth.isHandlingPrey(), "Removed mouth kept an active grab");
                            cow.discard();
                            dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(
                                    h.getLevel())
                                .removeSubLevel(
                                    rig.body,
                                    dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
                                        .REMOVED);
                            h.succeed();
                          });
                    });
              });
        });
  }

  @GameTest(template = "test_empty", batch = "arachnid_mandible_geometry")
  public static void jaws_and_distal_fangs_articulate_independently_and_retain_armor(
      GameTestHelper h) {
    h.assertTrue(
        MandibleGeometry.jawYaw(1, 1) == -MandibleGeometry.jawYaw(-1, 1),
        "Mandible hinges were not mirrored");
    h.assertTrue(
        MandibleGeometry.fangPitch(0) != MandibleGeometry.fangPitch(1),
        "Fangs did not have their own articulation");
    h.assertTrue(
        MandibleGeometry.opening(0, true, false, 0) < MandibleGeometry.opening(1, false, false, 0),
        "Grip pose did not close the jaws");
    var state = ComponentRegistry.ARACHNID_MAW.get().defaultBlockState();
    h.assertTrue(
        BlockHealthValues.health(state) == 10
            && org.azkerash.createbioships.components.logic.armor.ArmorValues.points(state) == 20,
        "Arachnid mouth changed biological HP or huntsman armor capacity");
    h.succeed();
  }
}
