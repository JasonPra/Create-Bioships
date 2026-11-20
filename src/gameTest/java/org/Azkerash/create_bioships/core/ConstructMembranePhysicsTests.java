package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;
import org.joml.Vector3d;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ConstructMembranePhysicsTests {
  @GameTest(template = "test_empty", batch = "construct_membrane_physics", timeoutTicks = 200)
  public static void native_voxel_contacts_allow_only_registered_hulls(GameTestHelper h) {
    var l = h.getLevel();
    var crafts = new ArrayList<ServerSubLevel>();
    var walls = new ArrayList<ServerSubLevel>();
    for (int lane = 0; lane < 2; lane++) {
      var p = new BlockPos(8250 + lane * 24, 90, 128);
      for (int x = -7; x <= 7; x++)
        for (int z = -7; z <= 32; z++) {
          l.setChunkForced((p.getX() + x) >> 4, (p.getZ() + z) >> 4, true);
          l.setBlock(p.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        }
      var cells = new ArrayList<BlockPos>();
      for (int x = -5; x <= 5; x++)
        for (int y = 0; y < 4; y++) {
          var at = p.offset(x, y, 0);
          cells.add(at);
          l.setBlock(
              at,
              (Math.abs(x) <= 1 && y < 3
                      ? com.github.elenterius.biomancy.init.ModBlocks.BIOMETRIC_MEMBRANE.get()
                      : Blocks.OBSIDIAN)
                  .defaultBlockState(),
              3);
        }
      l.setBlock(p.offset(-4, 0, 0), ComponentRegistry.CORE.get().defaultBlockState(), 3);
      var wall =
          SubLevelAssemblyHelper.assembleBlocks(
              l, p, cells, new BoundingBox3i(p.offset(-5, 0, 0), p.offset(5, 3, 0)));
      CoreAssemblyRules.mark(l, wall.getPlot().getCenterBlock().offset(-4, 0, 0));
      DevelopmentLifeSupport.pause(wall);
      var start = p.north(4);
      l.setBlock(start, ComponentRegistry.CORE.get().defaultBlockState(), 3);
      var craft =
          SubLevelAssemblyHelper.assembleBlocks(
              l, start, List.of(start), new BoundingBox3i(start, start));
      CoreAssemblyRules.mark(l, craft.getPlot().getCenterBlock());
      DevelopmentLifeSupport.pause(craft);
      craft.setName("Contact test craft");
      var owner = UUID.randomUUID();
      SubLevelPermissions.assignIfUnowned(wall, owner);
      if (lane == 0) SubLevelPermissions.addConstruct(wall, owner, craft);
      var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(l);
      container.addForceLoadTicket(
          wall,
          dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
          net.minecraft.util.Unit.INSTANCE);
      container.addForceLoadTicket(
          craft,
          dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
          net.minecraft.util.Unit.INSTANCE);
      walls.add(wall);
      crafts.add(craft);
    }
    final int[] ticks = {0};
    h.onEachTick(
        () -> {
          ticks[0]++;
          for (var craft : crafts) {
            if (craft.isRemoved())
              throw new IllegalStateException(
                  "Contact craft removed tick="
                      + ticks[0]
                      + " health="
                      + SubLevelHealth.status(craft));
            if (ticks[0] % 25 == 0)
              System.out.println(
                  "CONTACT_TICK t="
                      + ticks[0]
                      + " at="
                      + craft
                          .logicalPose()
                          .transformPosition(Vec3.atCenterOf(craft.getPlot().getCenterBlock())));
            var handle = RigidBodyHandle.of(craft);
            if (handle == null) continue;
            double v = handle.getLinearVelocity(new Vector3d()).z;
            handle.applyLinearImpulse(
                new Vector3d(0, 0, craft.getMassTracker().getMass() * Math.clamp(3 - v, -2, 2)));
          }
        });
    h.runAtTickTime(
        160,
        () -> {
          double[] z = new double[2];
          for (int i = 0; i < 2; i++) {
            var craft = crafts.get(i);
            var wall = walls.get(i);
            var at =
                craft
                    .logicalPose()
                    .transformPosition(Vec3.atCenterOf(craft.getPlot().getCenterBlock()));
            var local = wall.logicalPose().transformPositionInverse(at);
            z[i] = local.z - wall.getPlot().getCenterBlock().getZ();
            System.out.println(
                "MEMBRANE_PHYSICS lane="
                    + i
                    + " at="
                    + at
                    + " velocity="
                    + RigidBodyHandle.of(craft).getLinearVelocity(new Vector3d())
                    + " wall="
                    + wall.logicalPose());
          }
          h.assertTrue(
              z[0] > 1 && z[1] < 0,
              "Native membrane contact filtering failed: allowed=" + z[0] + " blocked=" + z[1]);
          h.succeed();
        });
  }
}
