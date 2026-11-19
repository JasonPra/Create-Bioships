package org.Azkerash.create_bioships.components;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.logic.mouth.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class MouthChewingTests {
  @GameTest(template = "test_empty", batch = "mouth_chewing", timeoutTicks = 150)
  public static void accepted_food_chews_then_closes_and_gui_input_restarts(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), ComponentRegistry.MOUTH.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east()),
            new BoundingBox3i(origin, origin.east()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    var pos = core.east();
    var mouth = (MouthBlockEntity) level.getBlockEntity(pos);
    mouth.feed(new ItemStack(Items.PORKCHOP), true);
    helper.assertTrue(
        mouth.chewRemaining() == 0 && level.getBlockState(pos).getValue(MouthBlock.CHEW) == 0,
        "Simulated feeding animated mouth");
    helper.assertTrue(
        mouth.feed(new ItemStack(Items.PORKCHOP), false).isEmpty(), "Real feeding failed");
    helper.assertTrue(
        mouth.chewRemaining() == 60 && level.getBlockState(pos).getValue(MouthBlock.CHEW) > 0,
        "Accepted food failed to start chewing");
    helper.runAfterDelay(
        12,
        () -> {
          int remaining = mouth.chewRemaining();
          helper.assertTrue(remaining > 0 && remaining < 60, "Chewing clock failed to advance");
          var stack = mouth.removeItemNoUpdate(0);
          mouth.setItem(8, stack);
          helper.assertTrue(
              mouth.chewRemaining() == remaining, "Internal slot rearrangement restarted chewing");
        });
    helper.runAfterDelay(
        65,
        () -> {
          helper.assertTrue(
              mouth.chewRemaining() == 0 && level.getBlockState(pos).getValue(MouthBlock.CHEW) == 0,
              "Mouth did not return to closed idle");
          var player =
              new net.neoforged.neoforge.common.util.FakePlayer(
                  level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Feeder"));
          player.getInventory().setItem(9, new ItemStack(Items.PORKCHOP, 2));
          var menu = new MouthMenu(1, player.getInventory(), mouth);
          menu.quickMoveStack(player, 9);
          helper.assertTrue(mouth.chewRemaining() == 60, "GUI shift-feeding did not start chewing");
          for (int slot = 0; slot < 9; slot++)
            mouth.setItem(slot, new ItemStack(Items.PORKCHOP, 64));
          helper.assertTrue(
              !mouth.feed(new ItemStack(Items.PORKCHOP), false).isEmpty(),
              "Full mouth accepted more food");
          SubLevelHealth.damage(body, 10000, "test");
          helper.runAfterDelay(
              4,
              () -> {
                helper.assertTrue(
                    level.getBlockState(pos).getValue(MouthBlock.CHEW) == 0,
                    "Dead mouth kept chewing");
                helper.succeed();
              });
        });
  }
}
