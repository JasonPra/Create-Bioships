package org.Azkerash.create_bioships.core;

import com.github.elenterius.biomancy.init.ModItems;
import com.github.elenterius.biomancy.item.injector.InjectorItem;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.stomach.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class StomachGeneTests {
  private static ItemStack injector(Item serum, int count) {
    var stack = ModItems.INJECTOR.get().getDefaultInstance();
    var handler = InjectorItem.getItemHandler(stack).orElseThrow();
    handler.insertItem(0, new ItemStack(serum, count), false);
    return stack;
  }

  private static ItemInteractionResult inject(
      StomachBlockEntity stomach, Player player, ItemStack tool) {
    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
    return stomach
        .getBlockState()
        .useItemOn(
            tool,
            stomach.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(
                Vec3.atCenterOf(stomach.getBlockPos()),
                Direction.EAST,
                stomach.getBlockPos(),
                false));
  }

  @GameTest(template = "test_empty", batch = "stomach_gene_injection", timeoutTicks = 150)
  public static void native_injector_consumes_only_compatible_genes_and_preserves_upgraded_capacity(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(origin, ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    var stomach = (StomachBlockEntity) level.getBlockEntity(origin);
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GeneInjection"));
    player.getAbilities().instabuild = false;
    var tool = injector(ComponentRegistry.LONGEVITY_SERUM.get(), 3);
    var item = (InjectorItem) tool.getItem();
    helper.assertTrue(
        inject(stomach, player, tool) == ItemInteractionResult.FAIL
            && item.getStoredItemStack(tool).getCount() == 3,
        "World stomach consumed serum");
    level.setBlock(origin.west(), ComponentRegistry.CORE.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin.west(),
            List.of(origin.west(), origin),
            new BoundingBox3i(origin.west(), origin));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, 7);
    stomach = (StomachBlockEntity) level.getBlockEntity(core.east());
    helper.assertTrue(
        inject(stomach, player, tool).consumesAction() && stomach.genes().longevity(),
        "Native injector failed to install longevity from side");
    helper.assertTrue(
        SubLevelData.maximumHunger(body) == 15 && SubLevelData.hunger(body) == 7,
        "Longevity refilled food or failed to add capacity");
    helper.assertTrue(
        item.getStoredItemStack(tool).getCount() == 2
            && player.getInventory().countItem(ModItems.VIAL.get()) == 1,
        "Native serum consumption/vial return failed");
    for (int tier = 2; tier <= 3; tier++) {
      player.getCooldowns().removeCooldown(item);
      helper.assertTrue(
          inject(stomach, player, tool).consumesAction() && stomach.genes().longevityTier() == tier,
          "Longevity tier failed: " + tier);
      helper.assertTrue(
          SubLevelData.maximumHunger(body) == 10 + tier * 5 && SubLevelData.hunger(body) == 7,
          "Capacity tier refilled food or incorrect maximum");
    }
    var extra = injector(ComponentRegistry.LONGEVITY_SERUM.get(), 1);
    player.getCooldowns().removeCooldown(item);
    helper.assertTrue(
        inject(stomach, player, extra) == ItemInteractionResult.FAIL
            && item.getStoredItemStack(extra).getCount() == 1,
        "Fourth longevity dose was accepted or consumed");
    var saved = stomach.saveWithFullMetadata(level.registryAccess());
    var restored =
        (StomachBlockEntity)
            BlockEntity.loadStatic(
                core.east(), stomach.getBlockState(), saved, level.registryAccess());
    restored.setLevel(level);
    helper.assertTrue(restored.genes().longevityTier() == 3, "Reload lost longevity tier");
    var drops =
        net.minecraft.world.level.block.Block.getDrops(
            stomach.getBlockState(), level, stomach.getBlockPos(), stomach);
    var genes =
        drops
            .getFirst()
            .get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
            .copyTag()
            .getCompound("Genes");
    helper.assertTrue(genes.getInt("Longevity") == 3, "Block item lost upgraded gene tier");
    SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "test");
    var efficiency = injector(ComponentRegistry.EFFICIENCY_SERUM.get(), 2);
    player.getCooldowns().removeCooldown(item);
    helper.assertTrue(
        inject(stomach, player, efficiency) == ItemInteractionResult.FAIL
            && item.getStoredItemStack(efficiency).getCount() == 2,
        "Dead stomach consumed upgrade serum");
    helper.runAfterDelay(10, helper::succeed);
  }

  @GameTest(template = "test_empty", batch = "stomach_efficiency_genes", timeoutTicks = 150)
  public static void efficiency_has_guaranteed_paste_and_one_probabilistic_component_bonus(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(origin, ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, List.of(origin), new BoundingBox3i(origin, origin));
    SubLevelData.living(body, true);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    var stomach = (StomachBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "EfficiencyGenes"));
    player.getAbilities().instabuild = false;
    var tool = injector(ComponentRegistry.EFFICIENCY_SERUM.get(), 5);
    var injector = (InjectorItem) tool.getItem();
    for (int tier = 1; tier <= 3; tier++) {
      player.getCooldowns().removeCooldown(injector);
      helper.assertTrue(
          inject(stomach, player, tool).consumesAction(), "Efficiency injection failed");
      helper.assertTrue(
          stomach.genes().efficiency() == tier && stomach.genes().pastePerMeal() == tier + 1,
          "Wrong efficiency tier/paste yield");
      int successes = 0;
      var random = net.minecraft.util.RandomSource.create(100 + tier);
      for (int trial = 0; trial < 10000; trial++) {
        var outputs =
            new ArrayList<ItemStack>(
                List.of(new ItemStack(Items.BONE, 3), new ItemStack(Items.STRING, 2)));
        stomach.genes().addExtraComponent(outputs, random);
        helper.assertTrue(
            outputs.get(0).getCount() == 3 && outputs.get(1).getCount() == 2 && outputs.size() <= 3,
            "Efficiency multiplied every component");
        if (outputs.size() == 3) {
          helper.assertTrue(
              outputs.get(2).getCount() == 1, "Extra component bonus was not one item");
          successes++;
        }
      }
      helper.assertTrue(
          Math.abs(successes - stomach.genes().extraComponentChance() * 100) < 250,
          "Unexpected component bonus frequency");
      for (int slot = 0; slot < 27; slot++) stomach.setItem(slot, ItemStack.EMPTY);
      stomach.enqueue(new ItemStack(Items.PORKCHOP), false);
      for (int tick = 0; tick < 2000 && stomach.queued() > 0; tick++) stomach.advance();
      int paste = 0;
      for (int slot = 9; slot < 27; slot++)
        if (stomach.getItem(slot).is(StomachBlockEntity.paste()))
          paste += stomach.getItem(slot).getCount();
      helper.assertTrue(paste == tier + 1, "Meal did not guarantee upgraded paste yield");
    }
    player.getCooldowns().removeCooldown(injector);
    int remaining = injector.getStoredItemStack(tool).getCount();
    helper.assertTrue(
        inject(stomach, player, tool) == ItemInteractionResult.FAIL
            && injector.getStoredItemStack(tool).getCount() == remaining,
        "Maxed efficiency consumed serum");
    helper.runAfterDelay(10, helper::succeed);
  }
}
