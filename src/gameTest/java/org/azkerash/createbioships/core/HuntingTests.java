package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.brain.*;
import org.azkerash.createbioships.core.brain.behavior.*;
import org.azkerash.createbioships.core.brain.hunting.*;
import org.azkerash.createbioships.core.vision.SubLevelVision;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class HuntingTests {
  @GameTest(template = "test_empty", batch = "hunt_card_data")
  public static void whitelist_is_card_owned_and_survives_nbt_round_trip(GameTestHelper h) {
    var card = new ItemStack(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get());
    HuntingCardData.write(card, "minecraft:cow, pig minecraft:cow");
    h.assertTrue(HuntingCardData.read(card).size() == 2, "Whitelist failed to deduplicate");
    var loaded =
        ItemStack.parseOptional(
            h.getLevel().registryAccess(),
            (net.minecraft.nbt.CompoundTag) card.save(h.getLevel().registryAccess()));
    h.assertTrue(
        HuntingCardData.read(loaded).equals(HuntingCardData.read(card)),
        "Whitelist was not stored on the card");
    h.assertTrue(
        HuntingCardData.read(new ItemStack(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get()))
            .isEmpty(),
        "Fresh card inherited another card's prey");
    try {
      HuntingCardData.write(card, "not_a_real_entity");
      h.fail("Invalid entity identifier accepted");
    } catch (IllegalArgumentException expected) {
      h.assertTrue(HuntingCardData.read(card).size() == 2, "Rejected save erased whitelist");
    }
    h.assertTrue(
        !org.azkerash.createbioships.core.brain.program.ProgramCardData.editable(card),
        "Fixed hunt card allowed source editing");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "hunt_runtime", timeoutTicks = 100)
  public static void hunting_needs_a_weapon_and_bites_only_whitelisted_visible_prey(
      GameTestHelper h) {
    var level = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 4, 2));
    for (int x = -3; x <= 14; x++) {
      for (int z = -3; z <= 3; z++) {
        level.setBlock(p.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    var cells = List.of(p, p.above(), p.east(), p.east().above());
    level.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(p.above(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(p.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        p.east().above(),
        ComponentRegistry.LAND_EYE
            .get()
            .defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                Direction.EAST),
        Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, p, cells, new BoundingBox3i(p, p.east().above()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    var card = new ItemStack(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get());
    HuntingCardData.write(card, "minecraft:cow");
    SubLevelBrains.get(body).inventory().setItem(0, card);
    var cow = EntityType.COW.create(level);
    cow.setNoAi(true);
    cow.setNoGravity(true);
    h.runAfterDelay(
        20,
        () -> {
          var center = body.logicalPose().transformPosition(Vec3.atCenterOf(core.east()));
          cow.moveTo(center.x + 1.6, center.y - .5, center.z, 0, 0);
          level.addFreshEntity(cow);
          h.assertTrue(
              HuntingWeapons.mounts(body).isEmpty(), "Nonweapon fulfilled attack prerequisite");
          BrainBehaviors.advance(body);
          h.assertTrue(
              BrainMotorControls.current(body).velocity().equals(Vec3.ZERO),
              "Unarmed hunter moved");
          level.setBlock(
              core.east(),
              ComponentRegistry.HUNTSMAN_MAW
                  .get()
                  .defaultBlockState()
                  .setValue(
                      org.azkerash.createbioships.components.logic.mouth.MouthBlock.FACING,
                      Direction.EAST),
              Block.UPDATE_ALL);
          h.assertTrue(
              HuntingWeapons.mounts(body).size() == 1, "Matching maw variation was not a weapon");
          SubLevelData.locomotion(body, new SubLevelData.Locomotion(true, false, false));
          SubLevelVision.refresh(body, 8);
          h.assertTrue(
              SubLevelVision.detected(body).stream()
                  .anyMatch(s -> s.entity().equals(cow.getUUID())),
              "Fixture cow not visible");
          float health = cow.getHealth();
          BrainBehaviors.advance(body);
          h.assertTrue(cow.getHealth() < health, "Hunter did not bite visible whitelisted cow");
          float bitten = cow.getHealth();
          cow.invulnerableTime = 0;
          BrainBehaviors.advance(body);
          h.assertTrue(cow.getHealth() == bitten, "Bite cooldown was bypassed");
          HuntingCardData.write(card, "minecraft:pig");
          h.assertTrue(
              !HuntingBehavior.isPrey(body, cow, HuntingBehavior.preyTypes(body)),
              "Whitelist changes were ignored");
          BrainBehaviors.advance(body);
          h.assertTrue(cow.getHealth() == bitten, "Hunter attacked nonwhitelisted cow");
          HuntingCardData.write(card, "minecraft:cow");
          cow.setPos(cow.position().add(6, 0, 0));
          h.runAfterDelay(
              10,
              () -> {
                SubLevelVision.refresh(body, 8);
                BrainBehaviors.advance(body);
                var path = org.azkerash.createbioships.core.pathfinding.SubLevelPaths.get(body);
                h.assertTrue(
                    path != null && !path.points().isEmpty(),
                    "Visible distant prey did not receive a chase route");
                SubLevelBrains.get(body).inventory().setItem(0, ItemStack.EMPTY);
                BrainBehaviors.advance(body);
                h.assertTrue(
                    org.azkerash.createbioships.core.pathfinding.SubLevelPaths.get(body) == null
                        && BrainMotorControls.current(body).velocity().equals(Vec3.ZERO),
                    "Removing hunting card left movement active");
                h.succeed();
              });
        });
  }
}
