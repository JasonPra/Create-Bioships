package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CapacityDamageTests {
  @GameTest(template = "test_empty", timeoutTicks = 140, batch = "capacity_damage")
  public static void wrench_and_replacement_preserve_injury_and_capacity_loss_can_kill(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(704, 80, 128);
    var flesh =
        BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("biomancy", "packed_flesh"));
    var cells = new ArrayList<BlockPos>();
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    cells.add(origin);
    for (int i = 1; i <= 5; i++) {
      level.setBlock(origin.east(i), flesh.defaultBlockState(), Block.UPDATE_ALL);
      cells.add(origin.east(i));
    }
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, cells, new BoundingBox3i(origin, origin.east(5)));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelHealth.damage(body, 1.5, "test");
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 58.5, "Initial fractional damage changed");
    var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "capacity-test");
    var player =
        new net.minecraft.server.level.ServerPlayer(
            level.getServer(),
            level,
            profile,
            net.minecraft.server.level.ClientInformation.createDefault());
    player.connection =
        new net.neoforged.neoforge.common.util.FakePlayer(level, profile).connection;
    org.Azkerash.create_bioships.core.access.SubLevelPermissions.assignIfUnowned(
        body, player.getUUID());
    player.setShiftKeyDown(true);
    player.setItemInHand(
        InteractionHand.MAIN_HAND, Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
    var part = core.east(5);
    for (int i = 0; i < 3; i++) {
      player.gameMode.useItemOn(
          player,
          level,
          player.getMainHandItem(),
          InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(part), Direction.NORTH, part, false));
      helper.assertTrue(
          SubLevelHealth.status(body).current() == 48.5
              && SubLevelHealth.status(body).maximum() == 50,
          "Removal erased fractional damage");
      level.setBlock(part, flesh.defaultBlockState(), Block.UPDATE_ALL);
      helper.assertTrue(
          SubLevelHealth.status(body).current() == 58.5
              && SubLevelHealth.status(body).maximum() == 60,
          "Replacing block healed old damage");
    }
    helper.assertTrue(
        body.getUserDataTag().getCompound(SubLevelHealth.DATA).getDouble("damage") == 1.5,
        "Damage balance was not persisted");
    SubLevelHealth.damage(body, 53.5, "test");
    SubLevelHealth.withoutRemovalDamage(
        body,
        () -> {
          level.removeBlock(part, false);
          level.setBlock(part, flesh.defaultBlockState(), Block.UPDATE_ALL);
          return true;
        });
    helper.assertTrue(
        !SubLevelHealth.status(body).dead() && SubLevelHealth.status(body).current() == 5,
        "Temporary removal killed a rolled-back operation");
    player.gameMode.useItemOn(
        player,
        level,
        player.getMainHandItem(),
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(part), Direction.NORTH, part, false));
    helper.assertTrue(
        SubLevelHealth.status(body).dead()
            && SubLevelHealth.status(body).current() == 0
            && SubLevelHealth.status(body).causeOfDeath().equals("component_loss"),
        "Insufficient capacity did not cause permanent death");
    level.setBlock(part, flesh.defaultBlockState(), Block.UPDATE_ALL);
    helper.assertTrue(
        SubLevelHealth.status(body).dead() && !SubLevelHealth.heal(body, 100),
        "Replacing/healing revived dead grid");
    helper.runAfterDelay(10, helper::succeed);
  }
}
