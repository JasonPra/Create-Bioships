package org.Azkerash.create_bioships.development.testing;

import dev.letitwalk.ModContent;
import dev.letitwalk.content.*;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Working upstream reference: original core, joints, girders, feet and a real Create motor. */
public final class NativeLegReference {
  public record Reference(ServerSubLevel body, BlockPos core, WalkerCoreBlockEntity walker) {}

  public static Reference create(ServerLevel level, BlockPos base) {
    for (int cx = (base.getX() - 16) >> 4; cx <= (base.getX() + 16) >> 4; cx++) {
      for (int cz = (base.getZ() - 16) >> 4; cz <= (base.getZ() + 16) >> 4; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    for (int x = -14; x <= 14; x++) {
      for (int z = -14; z <= 14; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
      }
    }
    for (int x = -1; x <= 1; x++) {
      for (int z = -2; z <= 2; z++) {
        level.setBlock(
            base.offset(x, 4, z), ModContent.KLAXIUM_CASING.get().defaultBlockState(), 3);
      }
    }
    var core = base.above(4);
    level.setBlock(
        core,
        ModContent.WALKER_CORE
            .get()
            .defaultBlockState()
            .setValue(WalkerCoreBlock.FACING, Direction.NORTH),
        3);
    var motor = core.north();
    level.setBlock(
        motor,
        com.simibubi.create.AllBlocks.CREATIVE_MOTOR
            .getDefaultState()
            .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.SOUTH),
        3);
    var motorBe =
        (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
            level.getBlockEntity(motor);
    motorBe.generatedSpeed.setValue(32);
    motorBe.updateGeneratedRotation();
    var beam = com.simibubi.create.AllBlocks.METAL_GIRDER.getDefaultState();
    for (int side : new int[] {-1, 1}) {
      for (int z : new int[] {-2, 2}) {
        var hip = base.offset(side * 2, 4, z);
        var knee = base.offset(side * 4, 4, z);
        var facing = side < 0 ? Direction.WEST : Direction.EAST;
        level.setBlock(
            hip,
            ModContent.LEG_HINGE.get().defaultBlockState().setValue(LegJointBlock.FACING, facing),
            3);
        level.setBlock(hip.relative(facing), beam, 3);
        level.setBlock(
            knee,
            ModContent.LEG_HINGE
                .get()
                .defaultBlockState()
                .setValue(LegJointBlock.FACING, Direction.DOWN),
            3);
        level.setBlock(knee.below(), beam, 3);
        level.setBlock(knee.below(2), beam, 3);
        level.setBlock(knee.below(3), ModContent.LEG_FOOT.get().defaultBlockState(), 3);
      }
    }
    var result = BuiltLegAssembler.assemble(level, core);
    for (var entry : SubLevelContainer.getContainer(level).getAllSubLevels()) {
      if (entry instanceof ServerSubLevel body) {
        var c = body.getPlot().getCenterBlock();
        if (level.getBlockEntity(c) instanceof WalkerCoreBlockEntity walker
            && walker.legs.size() == 4
            && body.logicalPose()
                    .transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(c))
                    .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(core))
                < 4) {
          return new Reference(body, c, walker);
        }
      }
    }
    throw new IllegalStateException("Native reference failed: " + result.getString());
  }

  public static double gap(Reference ref) {
    var container = SubLevelContainer.getContainer(ref.body.getLevel());
    double gap = 0;
    for (var leg : ref.walker.legs) {
      for (var j : leg.built.joints) {
        var a = container.getSubLevel(j.parent);
        var b = container.getSubLevel(j.child);
        if (a == null || b == null) {
          return Double.POSITIVE_INFINITY;
        }
        gap =
            Math.max(
                gap,
                a.logicalPose()
                    .transformPosition(j.parentPlot)
                    .distanceTo(b.logicalPose().transformPosition(j.childPlot)));
      }
    }
    return gap;
  }
}
