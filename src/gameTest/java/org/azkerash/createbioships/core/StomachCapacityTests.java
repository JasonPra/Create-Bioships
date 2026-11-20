package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;
import org.azkerash.createbioships.equipment.SymbioticGogglesAccess;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class StomachCapacityTests {
  @GameTest(template = "test_empty", timeoutTicks = 150, batch = "stomach_capacity")
  public static void stomach_capacity_threshold_and_inspection_whitelist(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(1040, 80, 128);
    for (int x = 64; x <= 65; x++) {
      for (int z = 7; z <= 8; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    for (int x = -4; x <= 4; x++) {
      for (int z = -4; z <= 4; z++) {
        level.setBlock(
            origin.offset(x, -2, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(
              origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(
              origin.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(origin.east(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  origin,
                  List.of(origin, origin.east(), origin.east(2)),
                  new BoundingBox3i(origin, origin.east(2)));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          DevelopmentLifeSupport.pause(body);
          helper.assertTrue(
              SubLevelData.maximumHunger(body) == 10 && SubLevelData.hunger(body) == 5,
              "Stomach failed to add capacity without filling hunger");
          helper.assertTrue(
              SubLevelHunger.minimumHungerToHeal(body) == 9, "90% threshold is not 9/10");
          var player =
              new net.neoforged.neoforge.common.util.FakePlayer(
                  level,
                  new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "StomachTest"));
          var stack = ComponentRegistry.STOMACH_ITEM.get().getDefaultInstance();
          var hit =
              new BlockHitResult(
                  Vec3.atCenterOf(core.east(2)), Direction.EAST, core.east(2), false);
          var placement =
              new BlockPlaceContext(
                  player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
          helper.assertTrue(
              ComponentRegistry.STOMACH.get().getStateForPlacement(placement) != null,
              "Existing stomach prevented another stomach placement");
          player.setPos(core.getX() + .5, core.getY() + 3, core.getZ() + .5);
          helper.assertTrue(
              ((BlockItem) stack.getItem()).place(placement).consumesAction()
                  && level.getBlockState(core.east(3)).is(ComponentRegistry.STOMACH.get()),
              "Player BlockItem placement rejected a second stomach");
          var third =
              new BlockPlaceContext(
                  player,
                  net.minecraft.world.InteractionHand.MAIN_HAND,
                  ComponentRegistry.STOMACH_ITEM.get().getDefaultInstance(),
                  new BlockHitResult(
                      Vec3.atCenterOf(core.east(2)), Direction.NORTH, core.east(2), false));
          helper.assertTrue(
              ((BlockItem) third.getItemInHand().getItem()).place(third).consumesAction()
                  && level.getBlockState(core.east(2).north()).is(ComponentRegistry.STOMACH.get()),
              "Player BlockItem placement rejected a third stomach");
          level.removeBlock(core.east(3), false);
          level.removeBlock(core.east(2).north(), false);
          for (var pos : List.of(core, core.east())) {
            var display = level.getBlockEntity(pos);
            helper.assertTrue(display != null, "Inspection block entity lost during assembly");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            helper.assertTrue(
                !SymbioticGogglesAccess.canInspect(player, display), "No goggles revealed display");
            for (String id : List.of("create:goggles", "aeronautics:aviators_goggles")) {
              var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
              helper.assertTrue(item != Items.AIR, "Expected dependency goggles missing: " + id);
              player.setItemSlot(EquipmentSlot.HEAD, item.getDefaultInstance());
              helper.assertTrue(
                  !SymbioticGogglesAccess.canInspect(player, display),
                  "Foreign goggles revealed display: " + id);
            }
            player.setItemSlot(
                EquipmentSlot.HEAD, CreateBioships.BIOSHIP_GOGGLES.get().getDefaultInstance());
            helper.assertTrue(
                SymbioticGogglesAccess.canInspect(player, display)
                    && com.simibubi.create.content.equipment.goggles.GogglesItem.isWearingGoggles(
                        player),
                "Symbiotic goggles lost native Create support");
          }
          player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
          var chest =
              new net.minecraft.world.level.block.entity.ChestBlockEntity(
                  BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
          helper.assertTrue(
              SymbioticGogglesAccess.canInspect(player, chest),
              "Whitelist changed foreign display rules");
          SubLevelHealth.damage(body, 10, "test_setup");
          double before = SubLevelHealth.status(body).current();
          SubLevelData.hunger(body, 8);
          SubLevelData.hungerPaused(body, false);
          SubLevelHunger.advance(body, 80);
          helper.assertTrue(
              SubLevelHealth.status(body).current() == before, "Regenerated below 90% hunger");
          var owner = org.azkerash.createbioships.combat.CombatAdapter.target(body);
          owner.addEffect(
              new net.minecraft.world.effect.MobEffectInstance(
                  net.minecraft.world.effect.MobEffects.REGENERATION, 60, 0));
          for (int tick = 0; tick < 20; tick++) {
            owner.tick();
          }
          helper.assertTrue(
              SubLevelHealth.status(body).current() == before + 1,
              "Regeneration potion was incorrectly gated by hunger");
          owner.removeAllEffects();
          before++;
          SubLevelData.hunger(body, 9);
          SubLevelHunger.advance(body, 79);
          helper.assertTrue(
              SubLevelHealth.status(body).current() == before, "Regenerated before four seconds");
          SubLevelHunger.advance(body, 1);
          helper.assertTrue(
              SubLevelHealth.status(body).current() == before + 1,
              "Failed to regenerate at 90% hunger");
          DevelopmentLifeSupport.pause(body);
          SubLevelHunger.advance(body, 10000);
          helper.assertTrue(
              SubLevelHealth.status(body).current() == before + 1,
              "Paused metabolism regenerated health");
          level.setBlock(core.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
          helper.assertTrue(
              SubLevelData.maximumHunger(body) == 5
                  && SubLevelData.hunger(body) == 5
                  && SubLevelHunger.minimumHungerToHeal(body) == 5,
              "Removal did not shrink capacity and clamp hunger");
          level.setBlock(
              core.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(
              core.east(2), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
          helper.assertTrue(
              SubLevelData.maximumHunger(body) == 12 && SubLevelData.hunger(body) == 5,
              "Additional stomach failed to stack capacity without filling hunger");
          level.setBlock(core.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
          helper.assertTrue(
              SubLevelData.maximumHunger(body) == 10, "Remaining stomach lost capacity");
          level.setBlock(core.east(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
          helper.assertTrue(
              SubLevelData.maximumHunger(body) == 5, "Last stomach removal kept bonus capacity");
          // Build the same group in reverse order; capacity depends on the total, not placement
          // order.
          level.setBlock(
              core.east(2), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(
              core.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
          helper.assertTrue(
              SubLevelData.maximumHunger(body) == 12 && SubLevelData.hunger(body) == 5,
              "Reverse placement changed diminishing returns or filled hunger");
          long[] raw = {0, 5, 10, 20, 45, 80};
          int[] expected = {5, 10, 12, 15, 20, 25};
          for (int i = 0; i < raw.length; i++) {
            helper.assertTrue(
                SubLevelHunger.balancedCapacity(raw[i]) == expected[i],
                "Unexpected diminishing-return capacity for raw total " + raw[i]);
          }
          helper.succeed();
        });
  }
}
