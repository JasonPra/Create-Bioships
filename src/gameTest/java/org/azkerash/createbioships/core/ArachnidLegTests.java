package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.*;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.legs.*;
import org.azkerash.createbioships.core.locomotion.prototype.*;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;
import org.joml.Vector3d;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ArachnidLegTests {
  @GameTest(template = "test_empty")
  public static void adhesive_motor_requires_three_real_toes_and_supports_wall_and_ceiling(
      GameTestHelper h) {
    for (var normal : List.of(new Vec3(0, 1, 0), new Vec3(-1, 0, 0), new Vec3(0, -1, 0))) {
      var held =
          ArachnidBodyMotor.calculate(
              Vec3.ZERO, Vec3.ZERO, normal, normal, Vec3.ZERO, Vec3.ZERO, 0, 0, 3, .05);
      h.assertTrue(
          held.velocity().distanceTo(new Vec3(0, 9.81 * .05, 0)) < 1e-7,
          "Adhesion failed to counter gravity in the contact frame");
      var falling =
          ArachnidBodyMotor.calculate(
              Vec3.ZERO, Vec3.ZERO, normal, normal, Vec3.ZERO, Vec3.ZERO, 0, 0, 2, .05);
      h.assertTrue(falling.velocity().equals(Vec3.ZERO), "Insufficient toes still levitate");
    }
    h.assertTrue(
        ArachnidBodyMotor.surfaceMotion(new Vec3(1, 0, 0), new Vec3(-1, 0, 0))
                .distanceTo(new Vec3(0, 1, 0))
            < 1e-7,
        "Horizontal heading did not become upward wall motion");
    h.assertTrue(
        ArachnidBodyMotor.surfaceMotion(new Vec3(0, 1, 0), new Vec3(-1, 0, 0))
            .equals(new Vec3(0, 1, 0)),
        "Explicit tangent command was rotated twice");
    h.assertTrue(
        ArachnidBodyMotor.surfaceMotion(new Vec3(0, 1, 0), new Vec3(0, 1, 0)).equals(Vec3.ZERO),
        "Ground adhesion allowed free vertical flight");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void spider_surface_frame_retains_three_anchors_and_releases_removed_faces(
      GameTestHelper h) {
    var normal = new Vec3(-1, 0, 0);
    var gait = new PrototypeGait();
    var present = new boolean[] {true};
    var terrain =
        new PrototypeGait.Terrain() {
          public PrototypeGait.Contact probe(Vec3 desired, double height) {
            return present[0]
                ? new PrototypeGait.Contact(
                    new Vec3(0, desired.y, desired.z), null, Vec3.ZERO, normal, normal)
                : null;
          }

          public Vec3 position(PrototypeGait.Contact contact) {
            return contact.point();
          }

          public Vec3 up(PrototypeGait.Leg leg) {
            return normal;
          }

          public List<Vec3> solve(PrototypeGait.Leg leg, Vec3 target) {
            return PrototypeLegKinematics.solve(
                leg.hip(), leg.outward(), target, leg.coxa(), leg.thigh(), leg.shin(), normal);
          }
        };
    var legs = new ArrayList<PrototypeGait.Leg>();
    for (int tick = 0; tick < 100; tick++) {
      legs.clear();
      for (int y : new int[] {-1, 1}) {
        for (int z : new int[] {-1, 1}) {
          legs.add(
              new PrototypeGait.Leg(
                  new BlockPos(0, y, z),
                  new Vec3(-1.5, y + tick * .04, z),
                  new Vec3(0, 0, z),
                  1.5,
                  .7,
                  1,
                  1.4,
                  y == z ? 0 : 1,
                  LegMotionProfile.ARACHNID));
        }
      }
      gait.update(legs, new Vec3(0, .8, 0), tick, terrain, new Vec3(0, 1, 0));
      h.assertTrue(gait.anchoredContacts(legs) >= 3, "Spider lifted too many toes on a wall");
      long casts = gait.getProbeCount();
      for (var leg : legs) {
        gait.pose(leg, tick + .5, terrain);
      }
      h.assertTrue(gait.getProbeCount() == casts, "Physics pose generated extra casts");
    }
    h.assertTrue(gait.getStepCount() > 0, "Surface movement never stepped");
    h.assertTrue(
        gait.groundContacts(legs, terrain, .7) == 0, "Wall anchors counted as floor support");
    h.assertTrue(
        Double.isNaN(gait.groundSupportHeight(legs, 0, terrain, .7)),
        "Wall feet supplied a fake ground height");
    present[0] = false;
    gait.update(legs, Vec3.ZERO, 120, terrain, new Vec3(0, 1, 0));
    h.assertTrue(gait.anchoredContacts(legs) == 0, "Removed wall retained adhesion");
    h.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "arachnid_surface_probe")
  public static void surface_probe_uses_collision_shapes_ignores_water_and_excludes_own_body(
      GameTestHelper h) {
    var level = h.getLevel();
    var at = h.absolutePos(new BlockPos(1, 4, 1));
    level.setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    var owner =
        SubLevelAssemblyHelper.assembleBlocks(level, at, List.of(at), new BoundingBox3i(at, at));
    var glass = at.east(5);
    level.setBlock(at.east(4), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(glass, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
    var from = Vec3.atCenterOf(at.east(2));
    var to = Vec3.atCenterOf(at.east(9));
    var hit = SurfaceContactProbe.cast(owner, from, to);
    h.assertTrue(
        hit != null
            && hit.support() == null
            && hit.normal().equals(new Vec3(-1, 0, 0))
            && Math.abs(hit.point().x - glass.getX()) < 1e-7,
        "Glass collision face not acquired through water");
    level.setBlock(glass, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        SurfaceContactProbe.cast(owner, from, to) == null, "Water supplied a phantom anchor");
    var other = at.east(7);
    level.setBlock(other, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    var platform =
        SubLevelAssemblyHelper.assembleBlocks(
            level, other, List.of(other), new BoundingBox3i(other, other));
    h.runAfterDelay(
        2,
        () -> {
          var target =
              platform
                  .logicalPose()
                  .transformPosition(Vec3.atCenterOf(platform.getPlot().getCenterBlock()));
          var foreignHit =
              SurfaceContactProbe.cast(owner, target.add(-2, 0, 0), target.add(2, 0, 0));
          h.assertTrue(
              foreignHit != null && platform.getUniqueId().equals(foreignHit.support()),
              "Foreign Sable surface was not tracked in its local frame: " + foreignHit);
          h.assertTrue(
              SurfaceContactProbe.cast(
                      owner, Vec3.atCenterOf(at.west(2)), Vec3.atCenterOf(at.east(2)))
                  == null,
              "Own hull anchored a toe");
          for (var body : List.of(owner, platform)) {
            SubLevelContainer.getContainer(level)
                .removeSubLevel(
                    body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
          }
          h.succeed();
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 800, batch = "arachnid_physical")
  public static void spider_socket_builds_walks_reloads_and_cleans_up(GameTestHelper h) {
    physical(h, false);
  }

  @GameTestGenerator
  public static List<TestFunction> transitions() {
    return List.of(
        new TestFunction(
            "arachnid_transition",
            "arachnid_floor_to_wall",
            "create_bioships:test_empty",
            1200,
            0,
            true,
            h -> physical(h, true)));
  }

  private static void physical(GameTestHelper h, boolean transition) {
    var level = h.getLevel();
    var base = new BlockPos(transition ? 27128 : 27000, 80, 128);
    for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++) {
      for (int z = (base.getZ() - 8) >> 4; z <= (base.getZ() + 28) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    for (int x = -7; x <= 7; x++) {
      for (int z = -7; z <= 28; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var core = base.above(2);
          var cells = new ArrayList<BlockPos>();
          for (int x = -1; x <= 1; x++) {
            for (int z = -(transition ? 2 : 1); z <= (transition ? 2 : 1); z++) {
              var cell = core.offset(x, -1, z);
              level.setBlock(
                  cell, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
              cells.add(cell);
            }
          }
          level.setBlock(core, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          cells.add(core);
          level.setBlock(
              core.west(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
          cells.add(core.west());
          for (int x : new int[] {-1, 1}) {
            for (int z : (transition ? new int[] {-2, -1, 1, 2} : new int[] {-1, 1})) {
              var cell = base.offset(x, 1, z);
              level.setBlock(
                  cell,
                  ComponentRegistry.ARACHNID_LEG
                      .get()
                      .defaultBlockState()
                      .setValue(
                          BlockStateProperties.HORIZONTAL_FACING,
                          transition
                              ? (x < 0 ? Direction.WEST : Direction.EAST)
                              : (z < 0 ? Direction.NORTH : Direction.SOUTH)),
                  Block.UPDATE_ALL);
            }
          }
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  core,
                  cells,
                  new BoundingBox3i(
                      base.offset(-1, 1, transition ? -2 : -1),
                      base.offset(1, 2, transition ? 2 : 1)));
          CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
          DevelopmentLifeSupport.pause(body);
          SubLevelLegs.request(body);
          h.runAfterDelay(
              120,
              () -> {
                h.assertTrue(
                    SubLevelLegs.segments(body).size() == (transition ? 24 : 12),
                    "Missing generated spider segments");
                h.assertTrue(
                    SubLevelLegs.contacts(body) >= 3, "Spider cannot establish ground anchors");
                var part = SubLevelLegs.segments(body).getFirst();
                h.assertTrue(
                    level
                        .getBlockState(part.getPlot().getCenterBlock())
                        .getValue(CompactLimbBlock.ARACHNID),
                    "Physical segment lost spider anatomy");
                var before =
                    SubLevelLegs.segments(body).stream()
                        .map(ServerSubLevel::getUniqueId)
                        .collect(java.util.stream.Collectors.toSet());
                reload(body);
                h.assertTrue(
                    before.equals(
                        SubLevelLegs.segments(body).stream()
                            .map(ServerSubLevel::getUniqueId)
                            .collect(java.util.stream.Collectors.toSet())),
                    "Reload replaced spider segments");
                var start =
                    body.logicalPose()
                        .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
                class Travel implements Runnable {
                  int ticks;

                  public void run() {
                    BrainMotorControls.move(body, new Vec3(0, 0, 1), 0, 10);
                    ticks += 5;
                    if (ticks < 100) {
                      h.runAfterDelay(5, () -> run());
                      return;
                    }
                    var end =
                        body.logicalPose()
                            .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
                    System.out.println(
                        "ARACHNID_TRAVEL distance="
                            + (end.z - start.z)
                            + " contacts="
                            + SubLevelLegs.contacts(body));
                    h.assertTrue(
                        end.z - start.z > 2.5, "Spider did not walk with default controller mode");
                    h.assertTrue(
                        body.logicalPose().orientation().transform(new Vector3d(0, 1, 0)).y > .9,
                        "Spider lost floor posture");
                    BrainMotorControls.stop(body);
                    if (transition) {
                      approachWall(h, body, base);
                    } else {
                      wallAndRelease(h, body, base);
                    }
                  }
                }
                new Travel().run();
              });
        });
  }

  private static void approachWall(GameTestHelper h, ServerSubLevel body, BlockPos base) {
    var level = body.getLevel();
    int wallZ = base.getZ() + 11;
    for (int x = base.getX() - 7; x <= base.getX() + 7; x++) {
      for (int y = base.getY(); y <= base.getY() + 24; y++) {
        level.setBlock(
            new BlockPos(x, y, wallZ), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    double startY = body.logicalPose().position().y;
    class Approach implements Runnable {
      int tick, accepted;

      public void run() {
        if (BrainMotorControls.move(body, new Vec3(0, 0, 1), 0, 10)) {
          accepted++;
        }
        tick += 5;
        if (tick % 40 == 0) {
          var up = body.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
          System.out.println(
              "ARACHNID_APPROACH tick="
                  + tick
                  + " gain="
                  + (body.logicalPose().position().y - startY)
                  + " up="
                  + up
                  + " contacts="
                  + SubLevelLegs.contacts(body)
                  + " accepted="
                  + accepted);
        }
        if (tick < 360) {
          h.runAfterDelay(5, () -> run());
          return;
        }
        BrainMotorControls.stop(body);
        h.assertTrue(
            body.logicalPose().position().y - startY > 3
                && body.logicalPose().orientation().transform(new Vector3d(0, 1, 0)).z < -.85
                && SubLevelLegs.contacts(body) >= 3,
            "Spider did not transfer from floor onto wall");
        for (int x = base.getX() - 7; x <= base.getX() + 7; x++) {
          for (int y = base.getY(); y <= base.getY() + 24; y++) {
            level.setBlock(
                new BlockPos(x, y, wallZ), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          }
        }
        ceilingAndRelease(h, body, base);
      }
    }
    new Approach().run();
  }

  private static void wallAndRelease(GameTestHelper h, ServerSubLevel body, BlockPos base) {
    var level = body.getLevel();
    var centre =
        body.logicalPose().transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    double floorDistance = centre.y - base.getY();
    int wallX = (int) Math.ceil(centre.x + floorDistance);
    var rotation = new org.joml.Quaterniond().rotationZ(Math.PI / 2);
    var shift = new Vector3d(wallX - centre.x - floorDistance, 12, 0);
    // Rotate the complete rig rigidly onto a vertical fixture; this isolates adhesion from routing.
    for (var member : SubLevelCreatureParts.members(body)) {
      var pose = member.logicalPose();
      var position =
          rotation
              .transform(
                  new Vector3d(pose.position()).sub(new Vector3d(centre.x, centre.y, centre.z)))
              .add(centre.x, centre.y, centre.z)
              .add(shift);
      var orientation = new org.joml.Quaterniond(rotation).mul(pose.orientation());
      var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(member);
      handle.teleport(position, orientation);
      handle.addLinearAndAngularVelocity(
          handle.getLinearVelocity(new Vector3d()).negate(),
          handle.getAngularVelocity(new Vector3d()).negate());
      dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level).updatePose(member);
    }
    var wallCells = new ArrayList<BlockPos>();
    for (int y = base.getY() + 5; y <= base.getY() + 24; y++) {
      for (int z = (int) centre.z - 7; z <= (int) centre.z + 7; z++) {
        var cell = new BlockPos(wallX, y, z);
        wallCells.add(cell);
        level.setBlock(cell, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    reload(body);
    double startingY = body.logicalPose().position().y;
    h.runAfterDelay(
        80,
        () -> {
          double heldY = body.logicalPose().position().y;
          int contacts = SubLevelLegs.contacts(body);
          double up = body.logicalPose().orientation().transform(new Vector3d(0, 1, 0)).x;
          System.out.println(
              "ARACHNID_WALL contacts=" + contacts + " upX=" + up + " fall=" + (startingY - heldY));
          h.assertTrue(
              contacts >= 3 && up < -.85 && Math.abs(startingY - heldY) < .8,
              "Spider failed to hold a vertical wall");
          class Climb implements Runnable {
            int ticks, accepted;

            public void run() {
              if (BrainMotorControls.move(body, new Vec3(1, 0, 0), 0, 10)) {
                accepted++;
              }
              ticks += 5;
              if (ticks < 100) {
                h.runAfterDelay(5, () -> run());
                return;
              }
              BrainMotorControls.stop(body);
              double climbedY = body.logicalPose().position().y;
              System.out.println(
                  "ARACHNID_CLIMB gain=" + (climbedY - heldY) + " accepted=" + accepted);
              h.assertTrue(climbedY - heldY > 2, "Spider could grip but could not climb the wall");
              wallCells.forEach(
                  cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
              h.runAfterDelay(
                  40,
                  () -> {
                    h.assertTrue(
                        climbedY - body.logicalPose().position().y > 2,
                        "Removing wall did not release adhesion");
                    ceilingAndRelease(h, body, base);
                  });
            }
          }
          new Climb().run();
        });
  }

  private static void ceilingAndRelease(GameTestHelper h, ServerSubLevel body, BlockPos base) {
    var level = body.getLevel();
    var centre =
        body.logicalPose().transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
    var up = body.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
    var rotation = new org.joml.Quaterniond().rotationTo(up, new Vector3d(0, -1, 0));
    int ceilingY = base.getY() + 24;
    var shift = new Vector3d(0, ceilingY - 2.7 - centre.y, 0);
    for (var member : SubLevelCreatureParts.members(body)) {
      var pose = member.logicalPose();
      var position =
          rotation
              .transform(
                  new Vector3d(pose.position()).sub(new Vector3d(centre.x, centre.y, centre.z)))
              .add(centre.x, centre.y, centre.z)
              .add(shift);
      var orientation = new org.joml.Quaterniond(rotation).mul(pose.orientation());
      var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(member);
      handle.teleport(position, orientation);
      handle.addLinearAndAngularVelocity(
          handle.getLinearVelocity(new Vector3d()).negate(),
          handle.getAngularVelocity(new Vector3d()).negate());
      dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level).updatePose(member);
    }
    var cells = new ArrayList<BlockPos>();
    for (int x = (int) centre.x - 7; x <= (int) centre.x + 7; x++) {
      for (int z = (int) centre.z - 7; z <= (int) centre.z + 7; z++) {
        var cell = new BlockPos(x, ceilingY, z);
        cells.add(cell);
        level.setBlock(cell, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    reload(body);
    double start = body.logicalPose().position().y;
    h.runAfterDelay(
        80,
        () -> {
          double y = body.logicalPose().position().y;
          double upY = body.logicalPose().orientation().transform(new Vector3d(0, 1, 0)).y;
          System.out.println(
              "ARACHNID_CEILING contacts="
                  + SubLevelLegs.contacts(body)
                  + " upY="
                  + upY
                  + " fall="
                  + (start - y));
          h.assertTrue(
              SubLevelLegs.contacts(body) >= 3 && upY < -.85 && Math.abs(y - start) < .8,
              "Spider could not hang from ceiling");
          cells.forEach(
              cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
          h.runAfterDelay(
              40,
              () -> {
                h.assertTrue(
                    y - body.logicalPose().position().y > 2,
                    "Removing ceiling did not release adhesion");
                for (var member : List.copyOf(SubLevelCreatureParts.members(body))) {
                  SubLevelContainer.getContainer(level)
                      .removeSubLevel(
                          member,
                          dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                }
                h.succeed();
              });
        });
  }

  private static void reload(ServerSubLevel body) {
    try {
      var field = SubLevelLegs.class.getDeclaredField("CONTROLLERS");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      var controllers =
          (Map<ServerSubLevel, org.azkerash.createbioships.compat.letitwalk.CompactLimbController>)
              field.get(null);
      var old = controllers.remove(body);
      old.save();
      // Simulate an existing pre-huntsman rig. Updating its anatomy must preserve saved parts.
      var data = body.getUserDataTag().copy();
      for (var raw :
          data.getCompound(org.azkerash.createbioships.compat.letitwalk.CompactLimbController.DATA)
              .getList("Legs", 10)) {
        var entry = (net.minecraft.nbt.CompoundTag) raw;
        entry.remove("AnatomyVersion");
        entry.remove("FanAngle");
      }
      body.setUserDataTag(data);
      old.close();
      var restored = new org.azkerash.createbioships.compat.letitwalk.CompactLimbController(body);
      controllers.put(body, restored);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }
}
