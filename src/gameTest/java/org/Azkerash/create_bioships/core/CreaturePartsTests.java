package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class CreaturePartsTests {
  private static ServerSubLevel assemble(GameTestHelper h, BlockPos p, boolean core) {
    var level = h.getLevel();
    level.setBlock(
        p,
        (core ? ComponentRegistry.CORE.get() : ComponentRegistry.CHITIN_ARMOR.get())
            .defaultBlockState(),
        3);
    level.setBlock(
        p.above(),
        (core ? ComponentRegistry.BRAIN.get() : ComponentRegistry.CRAB_LEG.get())
            .defaultBlockState(),
        3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, p, List.of(p, p.above()), new BoundingBox3i(p, p.above()));
    if (core) {
      CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
      DevelopmentLifeSupport.pause(body);
    }
    return body;
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "creature_parts")
  public static void linked_bodies_share_damage_armor_identity_and_capabilities(GameTestHelper h) {
    var level = h.getLevel();
    var p = new BlockPos(3328, 90, 128);
    level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
    h.runAfterDelay(
        10,
        () -> {
          var root = assemble(h, p, true);
          var limb = assemble(h, p.offset(5, 0, 0), false);
          double base = SubLevelHealth.status(root).maximum();
          var owner = UUID.randomUUID();
          SubLevelPermissions.assignIfUnowned(root, owner);
          SubLevelHealth.damage(root, 3, "before_link");
          SubLevelCreatureParts.attach(root, limb);
          h.assertTrue(SubLevelCreatureParts.root(limb) == root, "Limb did not resolve its owner");
          h.assertTrue(
              SubLevelHealth.status(limb).maximum() == base + 20, "Limb capacity was not combined");
          h.assertTrue(SubLevelHealth.status(limb).damage() == 3, "Linking healed existing damage");
          SubLevelHealth.damage(limb, 2, "limb_hit");
          h.assertTrue(SubLevelHealth.status(root).damage() == 5, "Limb damage did not reach root");
          SubLevelData.trust(limb, 12.5);
          h.assertTrue(SubLevelData.trust(root) == 12.5, "Trust was duplicated per limb");
          h.assertTrue(
              owner.equals(SubLevelPermissions.get(limb).owner()),
              "Limb permissions did not use root");
          h.assertTrue(SubLevelLocomotion.get(root).hasLegs(), "Linked appendage was omitted");
          h.assertTrue(SubLevelArmor.status(limb).maximum() == 30, "Linked armor omitted");
          var cell = limb.getPlot().getCenterBlock();
          SubLevelArmor.absorb(
              limb,
              cell,
              level
                  .damageSources()
                  .mobAttack(net.minecraft.world.entity.EntityType.ZOMBIE.create(level)),
              7);
          h.assertTrue(
              SubLevelArmor.status(root).current() == 23, "Limb armor did not spend shared pool");
          h.assertTrue(
              CreatureDecay.familyId(root).equals(CreatureDecay.familyId(limb)),
              "Corpse identity split");
          SubLevelCreatureParts.attach(root, limb);
          h.assertTrue(
              SubLevelHealth.status(root).maximum() == base + 20,
              "Repeated link double counted health");
          var stomach = limb.getPlot().getCenterBlock().above(2);
          level.setBlock(stomach, ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
          h.assertTrue(
              SubLevelHunger.hasStomach(root)
                  && SubLevelHunger.hasStomach(limb)
                  && SubLevelData.maximumHunger(root) == 10,
              "Limb stomach did not share creature capacity/restriction");
          var rootStomach = root.getPlot().getCenterBlock().east();
          level.setBlock(rootStomach, ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
          var otherLimb = assemble(h, p.offset(10, 0, 0), false);
          SubLevelCreatureParts.attach(root, otherLimb);
          var otherStomach = otherLimb.getPlot().getCenterBlock().above(2);
          level.setBlock(otherStomach, ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
          h.assertTrue(
              SubLevelData.maximumHunger(root) == 13,
              "Linked stomachs did not apply one creature-wide diminishing-return curve");
          SubLevelCreatureParts.attach(root, otherLimb);
          h.assertTrue(
              SubLevelData.maximumHunger(root) == 13,
              "Repeated link counted stomach capacity twice");
          level.removeBlock(otherStomach, false);
          SubLevelCreatureParts.detach(otherLimb);
          level.removeBlock(rootStomach, false);
          h.assertTrue(
              SubLevelData.maximumHunger(root) == 10,
              "Removing extra root/linked stomachs left stale capacity");
          level.removeBlock(stomach, false);
          h.assertTrue(
              !SubLevelHunger.hasStomach(root) && SubLevelData.maximumHunger(root) == 5,
              "Removed limb stomach left capacity behind");
          SubLevelCreatureParts.detach(limb);
          h.assertTrue(
              SubLevelHealth.status(root).maximum() == base
                  && SubLevelHealth.status(root).damage() == 5,
              "Severance repaired root");
          h.assertTrue(SubLevelHealth.status(limb).dead(), "Severed limb remained alive");
          h.assertTrue(
              !SubLevelLocomotion.get(root).hasLegs(), "Severed limb left locomotion flag");
          h.succeed();
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "creature_parts_death")
  public static void losing_limb_capacity_can_kill_whole_creature(GameTestHelper h) {
    var p = new BlockPos(3360, 90, 128);
    h.getLevel().setChunkForced(p.getX() >> 4, p.getZ() >> 4, true);
    h.runAfterDelay(
        10,
        () -> {
          var root = assemble(h, p, true);
          var limb = assemble(h, p.offset(5, 0, 0), false);
          SubLevelCreatureParts.attach(root, limb);
          SubLevelHealth.damage(limb, 25, "attack");
          h.assertTrue(!SubLevelHealth.status(root).dead(), "Fixture died too early");
          SubLevelCreatureParts.detach(limb);
          h.assertTrue(SubLevelHealth.status(root).dead(), "Lost capacity failed to kill root");
          h.assertTrue(!SubLevelHealth.heal(root, 100), "Dead root revived");
          h.succeed();
        });
  }
}
