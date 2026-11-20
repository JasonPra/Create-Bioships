package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.respiration.RespiratoryBlock;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class OxygenTests {
  private static ServerSubLevel body(GameTestHelper h, BlockPos p, boolean organs) {
    var l = h.getLevel();
    l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var cells = new ArrayList<BlockPos>();
    cells.add(p);
    if (organs) {
      l.setBlock(p.east(), ComponentRegistry.LUNG.get().defaultBlockState(), Block.UPDATE_ALL);
      cells.add(p.east());
      l.setBlock(p.east(3), ComponentRegistry.GILLS.get().defaultBlockState(), Block.UPDATE_ALL);
      cells.add(p.east(3));
    }
    var b =
        SubLevelAssemblyHelper.assembleBlocks(
            l, p, cells, new BoundingBox3i(p, p.east(organs ? 3 : 0)));
    CoreAssemblyRules.mark(l, b.getPlot().getCenterBlock());
    SubLevelData.hungerPaused(b, true);
    return b;
  }

  @GameTest(template = "test_empty", batch = "oxygen_environment", timeoutTicks = 100)
  public static void lungs_and_gills_sample_opposite_environments_and_gills_require_clear_intake(
      GameTestHelper h) {
    var b = body(h, h.absolutePos(new BlockPos(1, 3, 1)), true);
    var l = h.getLevel();
    var core = b.getPlot().getCenterBlock();
    var lung = core.east();
    var gill = core.east(3);
    h.assertTrue(
        SubLevelOxygen.breathing(b, lung) && !SubLevelOxygen.breathing(b, gill),
        "Dry organ breathing incorrect");
    var world = BlockPos.containing(b.logicalPose().transformPosition(Vec3.atCenterOf(lung)));
    l.setBlock(world, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(!SubLevelOxygen.breathing(b, lung), "Lung breathed while physically submerged");
    l.setBlock(world, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(
        lung,
        l.getBlockState(lung).setValue(BlockStateProperties.WATERLOGGED, true),
        Block.UPDATE_ALL);
    h.assertTrue(!SubLevelOxygen.breathing(b, lung), "Waterlogged lung breathed");
    l.setBlock(
        lung,
        l.getBlockState(lung).setValue(BlockStateProperties.WATERLOGGED, false),
        Block.UPDATE_ALL);
    l.setBlock(
        gill,
        l.getBlockState(gill).setValue(BlockStateProperties.WATERLOGGED, true),
        Block.UPDATE_ALL);
    h.assertTrue(!SubLevelOxygen.breathing(b, gill), "Waterlogging bypassed dry gill intake");
    for (var face : Direction.values()) {
      l.setBlock(
          gill,
          l.getBlockState(gill).setValue(BlockStateProperties.FACING, face),
          Block.UPDATE_ALL);
      var neighbor = gill.relative(face);
      l.setBlock(neighbor, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
      h.assertTrue(SubLevelOxygen.breathing(b, gill), "Wet intake failed for " + face);
      l.setBlock(
          neighbor,
          Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
          Block.UPDATE_ALL);
      h.assertTrue(!SubLevelOxygen.breathing(b, gill), "Waterlogged solid failed to block intake");
      l.setBlock(neighbor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
    // Breathing uses the transformed world position, not only water in plot coordinates.
    var front =
        BlockPos.containing(
            b.logicalPose().transformPosition(Vec3.atCenterOf(gill).relative(Direction.EAST, .6)));
    l.setBlock(front, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        SubLevelOxygen.breathing(b, gill), "Gill missed water outside the physical intake");
    l.setBlock(front, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    for (var face : Direction.values()) {
      if (l.getBlockState(lung.relative(face)).isAir()) {
        l.setBlock(lung.relative(face), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    h.assertTrue(
        SubLevelOxygen.breathing(b, lung), "Enclosed lung incorrectly required an exposed intake");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "oxygen_lifecycle", timeoutTicks = 100)
  public static void reserve_drain_persistence_pause_refill_and_suffocation_are_creature_owned(
      GameTestHelper h) {
    var b = body(h, h.absolutePos(new BlockPos(1, 3, 1)), false);
    var p = b.getPlot().getCenterBlock();
    h.assertTrue(SubLevelData.oxygen(b).value() == 1200, "Legacy/new reserve not initialized full");
    SubLevelOxygen.advance(b, 1180);
    h.assertTrue(SubLevelData.oxygen(b).value() == 20, "Baseline reserve drain incorrect");
    SubLevelOxygen.advance(b, 20);
    h.assertTrue(!SubLevelHunger.canRegenerate(b), "Natural regeneration ignored empty oxygen");
    double hp = SubLevelHealth.status(b).current();
    SubLevelOxygen.advance(b, 19);
    var saved = b.getUserDataTag().copy();
    b.setUserDataTag(saved.copy());
    h.assertTrue(
        SubLevelData.oxygen(b).suffocationTicks() == 19 && SubLevelHealth.status(b).current() == hp,
        "Suffocation damaged early or reload lost timer");
    SubLevelOxygen.advance(b, 1);
    h.assertTrue(
        SubLevelHealth.status(b).current() == hp - 2,
        "Empty air did not deal player-style drowning damage after 20 ticks");
    SubLevelOxygen.advance(b, 40);
    h.assertTrue(
        SubLevelHealth.status(b).current() == hp - 6,
        "Repeated drowning pulses did not deal 2 damage per second");
    SubLevelData.oxygen(b, new SubLevelData.Oxygen(0, 0, true));
    SubLevelOxygen.advance(b, 10000);
    h.assertTrue(
        SubLevelData.oxygen(b).paused() && SubLevelHealth.status(b).current() == hp - 6,
        "Paused oxygen advanced");
    SubLevelData.oxygen(b, new SubLevelData.Oxygen(0, 0, false));
    h.getLevel()
        .setBlock(p.east(), ComponentRegistry.LUNG.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(SubLevelData.oxygen(b).value() == 0, "Placing lung refilled oxygen for free");
    SubLevelOxygen.advance(b, 20);
    h.assertTrue(SubLevelData.oxygen(b).value() == 20, "Lung failed to refill reserve");
    h.getLevel().removeBlock(p.east(), false);
    h.assertTrue(SubLevelOxygen.status(b).lungs() == 0, "Removed lung left breathing supply");
    SubLevelData.oxygen(b, new SubLevelData.Oxygen(0, 0, false));
    SubLevelOxygen.advance(b, 800);
    h.assertTrue(
        SubLevelHealth.status(b).dead()
            && SubLevelHealth.status(b).causeOfDeath().equals("suffocation"),
        "Oxygen depletion failed to kill permanently");
    var dead = SubLevelData.oxygen(b);
    SubLevelOxygen.advance(b, 10000);
    h.assertTrue(SubLevelData.oxygen(b).equals(dead), "Dead creature kept simulating oxygen");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "oxygen_family", timeoutTicks = 100)
  public static void linked_organs_share_supply_and_mass_increases_demand(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(1, 3, 1));
    var root = body(h, p, false);
    var l = h.getLevel();
    l.setBlock(p.east(6), ComponentRegistry.LUNG.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(p.east(7), ComponentRegistry.GILLS.get().defaultBlockState(), Block.UPDATE_ALL);
    var limb =
        SubLevelAssemblyHelper.assembleBlocks(
            l, p.east(6), List.of(p.east(6), p.east(7)), new BoundingBox3i(p.east(6), p.east(7)));
    SubLevelData.oxygen(root, new SubLevelData.Oxygen(100, 0, false));
    SubLevelCreatureParts.attach(root, limb);
    h.assertTrue(
        SubLevelOxygen.status(root).lungs() == 1
            && SubLevelOxygen.status(root).gills() == 1
            && SubLevelData.oxygen(limb).value() == 100,
        "Linked organs duplicated oxygen or lost supply");
    SubLevelOxygen.advance(limb, 20);
    h.assertTrue(SubLevelData.oxygen(root).value() == 100, "Linked body simulated reserve twice");
    double demand = SubLevelOxygen.status(root).demand();
    var c = root.getPlot().getCenterBlock();
    for (int x = 1; x <= 150; x++) {
      h.getLevel().setBlock(c.above(x), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }
    h.assertTrue(
        SubLevelOxygen.status(root).demand() > demand, "Mass did not increase oxygen demand");
    SubLevelCreatureParts.detach(limb);
    h.assertTrue(SubLevelOxygen.status(root).lungs() == 0, "Detached organs left breathing supply");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "gill_animation_sync", timeoutTicks = 100)
  public static void gills_share_phase_across_linked_bodies_and_stop_on_death(GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(1, 3, 1));
    var root = body(h, p, true);
    var c = root.getPlot().getCenterBlock();
    l.setBlock(
        c.above(), ComponentRegistry.ARMORED_GILLS.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(p.east(6), ComponentRegistry.GILLS.get().defaultBlockState(), Block.UPDATE_ALL);
    var part =
        SubLevelAssemblyHelper.assembleBlocks(
            l, p.east(6), List.of(p.east(6)), new BoundingBox3i(p.east(6), p.east(6)));
    SubLevelCreatureParts.attach(root, part);
    var at = List.of(c.east(3), c.above(), part.getPlot().getCenterBlock());
    for (var pos : at) {
      l.setBlock(pos.north(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    }
    var before = SubLevelData.oxygen(root);
    SubLevelOxygen.animate(root, 12);
    for (var pos : at) {
      var state = l.getBlockState(pos);
      h.assertTrue(
          state.getValue(RespiratoryBlock.BREATH) == 1 && !state.getValue(RespiratoryBlock.GASPING),
          "Submerged gills lost common breathing phase");
    }
    l.setBlock(at.getFirst().north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    SubLevelOxygen.animate(root, 12);
    h.assertTrue(
        l.getBlockState(at.getFirst()).getValue(RespiratoryBlock.BREATH) == 0
            && !l.getBlockState(at.getFirst()).getValue(RespiratoryBlock.GASPING),
        "Dry gills gasped despite a functioning lung");
    h.assertTrue(
        l.getBlockState(at.getLast()).getValue(RespiratoryBlock.BREATH) == 1,
        "Wet linked gills stopped breathing while lung supplied air");
    l.setBlock(
        c.east(),
        l.getBlockState(c.east()).setValue(BlockStateProperties.WATERLOGGED, true),
        Block.UPDATE_ALL);

    SubLevelOxygen.animate(root, 12);
    for (var pos : at) {
      var state = l.getBlockState(pos);
      h.assertTrue(
          state.getValue(RespiratoryBlock.BREATH) == 2
              && state.getValue(RespiratoryBlock.GASPING) == pos.equals(at.getFirst()),
          "Mixed wet/dry gills failed synchronized faster gasping");
    }
    for (var pos : at) {
      l.setBlock(pos.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
    SubLevelOxygen.animate(root, 12);
    for (var pos : at) {
      h.assertTrue(
          l.getBlockState(pos).getValue(RespiratoryBlock.GASPING),
          "All dry gills failed to gasp together without functional lungs");
    }
    l.setBlock(
        c.east(),
        l.getBlockState(c.east()).setValue(BlockStateProperties.WATERLOGGED, false),
        Block.UPDATE_ALL);
    SubLevelOxygen.animate(root, 15);
    for (var pos : at) {
      h.assertTrue(
          l.getBlockState(pos).getValue(RespiratoryBlock.BREATH) == 0
              && !l.getBlockState(pos).getValue(RespiratoryBlock.GASPING),
          "Dry gill plates failed to close when lung breathing resumed");
    }
    h.assertTrue(SubLevelData.oxygen(root).equals(before), "Cosmetic animation advanced oxygen");
    SubLevelHealth.damage(root, 10000, "test");
    SubLevelOxygen.animate(root, 15);
    for (var pos : at) {
      h.assertTrue(
          l.getBlockState(pos).getValue(RespiratoryBlock.BREATH) == 0, "Dead gills kept animating");
    }
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "gill_cosmetic_weakpoints", timeoutTicks = 100)
  public static void both_gill_variants_breathe_identically_and_never_use_armor(GameTestHelper h) {
    var b = body(h, h.absolutePos(new BlockPos(1, 3, 1)), true);
    var l = h.getLevel();
    var c = b.getPlot().getCenterBlock();
    var gill = c.east(3);
    l.setBlock(
        c.above(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(gill.north(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    var normal = SubLevelOxygen.status(b);
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GillWeakpoint"));
    var source = l.damageSources().playerAttack(player);
    var target = org.Azkerash.create_bioships.combat.CombatAdapter.target(b);
    for (var block :
        List.of(ComponentRegistry.GILLS.get(), ComponentRegistry.ARMORED_GILLS.get())) {
      l.setBlock(gill, block.defaultBlockState(), Block.UPDATE_ALL);
      var sample = SubLevelOxygen.status(b);
      h.assertTrue(
          sample.supply() == normal.supply()
              && sample.gills() == normal.gills()
              && SubLevelArmor.status(b).maximum() == 20,
          "Cosmetic gills changed oxygen or armor capacity");
      double hp = SubLevelHealth.status(b).current();
      target.invulnerableTime = 0;
      target.impact(gill, Direction.NORTH);
      target.hurt(source, 4);
      h.assertTrue(
          SubLevelHealth.status(b).current() == hp - 4 && SubLevelArmor.status(b).current() == 20,
          "Gill hit was protected by the creature armor pool");
    }
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "oxygen_recovery", timeoutTicks = 100)
  public static void either_valid_organ_refills_and_both_add_supply_without_free_air(
      GameTestHelper h) {
    var b = body(h, h.absolutePos(new BlockPos(1, 3, 1)), true);
    var l = h.getLevel();
    var core = b.getPlot().getCenterBlock();
    var lung = core.east();
    var gill = core.east(3);
    SubLevelData.oxygen(b, new SubLevelData.Oxygen(100, 19, false));
    h.assertTrue(
        SubLevelOxygen.status(b).breathingLungs() == 1
            && SubLevelOxygen.status(b).breathingGills() == 0,
        "Dry lung was not recognized as functioning");
    SubLevelOxygen.advance(b, 20);
    h.assertTrue(
        SubLevelData.oxygen(b).value() == 120 && SubLevelData.oxygen(b).suffocationTicks() == 0,
        "Working lung failed to refill and reset drowning timer");
    l.setBlock(
        lung,
        l.getBlockState(lung).setValue(BlockStateProperties.WATERLOGGED, true),
        Block.UPDATE_ALL);
    l.setBlock(gill.north(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        SubLevelOxygen.status(b).breathingLungs() == 0
            && SubLevelOxygen.status(b).breathingGills() == 1,
        "Underwater intake was not recognized as functioning");
    SubLevelOxygen.advance(b, 20);
    h.assertTrue(SubLevelData.oxygen(b).value() == 140, "Working gill alone failed to refill");
    l.setBlock(
        lung,
        l.getBlockState(lung).setValue(BlockStateProperties.WATERLOGGED, false),
        Block.UPDATE_ALL);
    SubLevelOxygen.advance(b, 20);
    h.assertTrue(
        SubLevelData.oxygen(b).value() == 200 && SubLevelOxygen.status(b).supply() == 80,
        "Working lung and gill failed to combine their supply");
    SubLevelData.oxygen(b, new SubLevelData.Oxygen(1190, 0, false));
    SubLevelOxygen.advance(b, 20);
    h.assertTrue(
        SubLevelData.oxygen(b).value() == SubLevelOxygen.MAXIMUM,
        "Recovery exceeded reserve capacity");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "junior_breathing_balance", timeoutTicks = 20)
  public static void junior_mass_needs_one_lung_while_large_bodies_still_scale(GameTestHelper h) {
    // Measured from the uploaded Junior save with its generated native compact limbs.
    double juniorDemand = SubLevelOxygen.demandForMass(88.55);
    h.assertTrue(
        juniorDemand == 20 && juniorDemand < SubLevelOxygen.SUPPLY_PER_ORGAN,
        "One working lung cannot sustain Junior and refill his reserve");
    h.assertTrue(
        SubLevelOxygen.demandForMass(300) == 60,
        "Large-creature demand stopped scaling independently of hunger");
    h.succeed();
  }
}
