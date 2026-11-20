package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.mouth.*;
import org.azkerash.createbioships.components.logic.stomach.StomachBlockEntity;
import org.azkerash.createbioships.core.digestion.SubLevelDigestion;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ArmoredMawTests {
  @GameTest(template = "test_empty", batch = "armored_maw", timeoutTicks = 100)
  public static void feeding_and_directional_armor_share_existing_creature_systems(
      GameTestHelper h) {
    var level = h.getLevel();
    var p = h.absolutePos(new BlockPos(1, 3, 1));
    var blocks = List.of(p, p.east(), p.east(2), p.east(3));
    level.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(p.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        p.east(2), ComponentRegistry.INTESTINE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        p.east(3), ComponentRegistry.ARMORED_MAW.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(level, p, blocks, new BoundingBox3i(p, p.east(3)));
    var core = body.getPlot().getCenterBlock();
    var at = core.east(3);
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    var mouth = (MouthBlockEntity) level.getBlockEntity(at);
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "MawFeeder"));
    var food = new ItemStack(Items.BEEF, 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, food);
    var state = level.getBlockState(at);
    state.useItemOn(
        food,
        level,
        player,
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
    h.assertTrue(
        food.getCount() == 1
            && Math.abs(SubLevelData.trust(body) - .1) < 1e-6
            && mouth.chewRemaining() > 0,
        "Maw lost hand feeding, trust or chewing");
    h.assertTrue(
        SubLevelDigestion.transfer(mouth) == 1
            && ((StomachBlockEntity) level.getBlockEntity(core.east())).queued() == 1,
        "Maw not recognized by intestine transport");
    var source = level.damageSources().playerAttack(player);
    h.assertTrue(SubLevelArmor.status(body).maximum() == 20, "Maw did not add armor capacity");
    for (var facing : Direction.values()) {
      level.setBlock(
          at, level.getBlockState(at).setValue(MouthBlock.FACING, facing), Block.UPDATE_ALL);
      for (var face : Direction.values()) {
        if (face != facing) {
          h.assertTrue(
              SubLevelArmor.absorb(body, at, face, source, 2) == 2,
              "Maw armored an exposed face for orientation " + facing);
        }
      }
    }
    h.assertTrue(
        SubLevelArmor.status(body).current() == 20
            && SubLevelArmor.absorb(body, at, source, 2) == 2,
        "Exposed or unknown face spent armor");
    level.setBlock(
        at, level.getBlockState(at).setValue(MouthBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
    var target = org.azkerash.createbioships.combat.CombatAdapter.target(body);
    double health = SubLevelHealth.status(body).current();
    target.impact(at, Direction.NORTH);
    target.invulnerableTime = 0;
    target.hurt(source, 4);
    h.assertTrue(
        SubLevelHealth.status(body).current() == health
            && SubLevelArmor.status(body).current() == 16,
        "Native front hit failed armor routing");
    target.impact(at, Direction.SOUTH);
    target.invulnerableTime = 0;
    target.hurt(source, 4);
    h.assertTrue(
        SubLevelHealth.status(body).current() == health - 4
            && SubLevelArmor.status(body).current() == 16,
        "Native back hit incorrectly used armor");
    level.removeBlock(at, false);
    h.assertTrue(SubLevelArmor.status(body).maximum() == 0, "Removed maw left armor capacity");
    level.setBlock(at, ComponentRegistry.ARMORED_MAW.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(SubLevelArmor.status(body).current() == 16, "Replacement refilled spent armor");
    h.assertTrue(
        ((MouthBlockEntity) level.getBlockEntity(at)).salivaParticles(),
        "Living Eldritch Maw did not emit saliva");
    level.setBlock(at, ComponentRegistry.MOUTH.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        !((MouthBlockEntity) level.getBlockEntity(at)).salivaParticles(),
        "Ordinary Feeder Mouth emitted Eldritch saliva");
    level.setBlock(at, ComponentRegistry.ARMORED_MAW.get().defaultBlockState(), Block.UPDATE_ALL);
    SubLevelHealth.damage(body, 10000, "test");
    h.assertTrue(
        !((MouthBlockEntity) level.getBlockEntity(at)).salivaParticles(),
        "Dead Eldritch Maw kept salivating");
    h.succeed();
  }
}
