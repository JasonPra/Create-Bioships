package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.core.locomotion.SubLevelFacing;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;
import org.Azkerash.create_bioships.development.testing.network.FacingDebugSync;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class FacingTests {
  @GameTest(template = "test_empty", batch = "creature_facing", timeoutTicks = 100)
  public static void front_is_saved_local_shared_and_rotates_with_the_creature(GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(1, 3, 1));
    l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var b = SubLevelAssemblyHelper.assembleBlocks(l, p, List.of(p), new BoundingBox3i(p, p));
    CoreAssemblyRules.mark(l, b.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(b);
    h.assertTrue(
        SubLevelFacing.local(b) == Direction.SOUTH
            && b.getUserDataTag()
                .getCompound(SubLevelData.STATE)
                .getString("front")
                .equals("south"),
        "Legacy +Z front was not preserved and saved");
    h.assertTrue(
        SubLevelFacing.set(b, Direction.NORTH) && !SubLevelFacing.set(b, Direction.UP),
        "Invalid front accepted");
    var saved = b.getUserDataTag().copy();
    b.setUserDataTag(saved.copy());
    b.logicalPose().orientation().rotationY(Math.PI / 2);
    h.assertTrue(
        SubLevelFacing.local(b) == Direction.NORTH
            && SubLevelFacing.world(b).dot(new Vec3(-1, 0, 0)) > .99999,
        "Front was world-fixed or lost on reload");
    var q = p.east(5);
    l.setBlock(q, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    var part = SubLevelAssemblyHelper.assembleBlocks(l, q, List.of(q), new BoundingBox3i(q, q));
    SubLevelCreatureParts.attach(b, part);
    h.assertTrue(SubLevelFacing.local(part) == Direction.NORTH, "Limb duplicated the front");
    h.assertTrue(
        SubLevelFacing.set(part, Direction.EAST) && SubLevelFacing.local(b) == Direction.EAST,
        "Changing through a member missed the root");
    var owner = UUID.randomUUID();
    SubLevelPermissions.assignIfUnowned(b, owner);
    h.assertTrue(
        FacingDebugSync.canSet(part, owner) && !FacingDebugSync.canSet(b, UUID.randomUUID()),
        "Facing edit ignored ownership");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "facing_steering", timeoutTicks = 30)
  public static void steering_turns_before_driving_and_uses_the_saved_nose(GameTestHelper h) {
    var south = new Vec3(0, 0, 1);
    var north = new Vec3(0, 0, -1);
    var reverse = SubLevelFacing.towards(south, north, .65, false);
    h.assertTrue(
        reverse.velocity().lengthSqr() == 0 && reverse.yawRate() != 0,
        "Waypoint behind caused backward travel without turning");
    var aligned = SubLevelFacing.towards(north, north, .65, false);
    h.assertTrue(
        aligned.velocity().z < -.64 && Math.abs(aligned.yawRate()) < 1e-8,
        "North-facing creature failed aligned travel");
    var east = SubLevelFacing.towards(south, new Vec3(1, 0, 0), .65, false);
    h.assertTrue(
        east.yawRate() > 0 && east.velocity().lengthSqr() == 0,
        "Right turn sign or movement gating incorrect");
    var swim = SubLevelFacing.towards(north, new Vec3(0, 1, -1), .65, true);
    h.assertTrue(swim.velocity().y > 0, "Facing control lost vertical swimming motion");
    h.succeed();
  }
}
