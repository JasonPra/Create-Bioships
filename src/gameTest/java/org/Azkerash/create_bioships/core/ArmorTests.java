package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class ArmorTests {
  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "armor")
  public static void armored_hits_capacity_replacement_and_persistence(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 3, 1));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), 3);
    level.setBlock(origin.east(2), Blocks.STONE.defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east(), origin.east(2)),
            new BoundingBox3i(origin, origin.east(2)));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelData.hungerPaused(body, true);
    var attacker =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArmorTest"));
    var source = level.damageSources().playerAttack(attacker);
    helper.assertTrue(SubLevelArmor.status(body).current() == 20, "Armor capacity incorrect");
    helper.assertTrue(
        SubLevelArmor.absorb(body, core.east(2), source, 6) == 6
            && SubLevelArmor.status(body).current() == 20,
        "Unarmored hit consumed armor");
    helper.assertTrue(
        SubLevelArmor.absorb(body, core.east(), source, 7) == 0
            && SubLevelArmor.status(body).current() == 13,
        "Armor did not absorb hit");
    level.removeBlock(core.east(), false);
    helper.assertTrue(SubLevelArmor.status(body).maximum() == 0, "Removed armor still counted");
    level.setBlock(core.east(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), 3);
    helper.assertTrue(SubLevelArmor.status(body).current() == 13, "Replacement repaired armor");
    helper.assertTrue(
        body.getUserDataTag().getCompound(SubLevelData.ARMOR).getDouble("spent") == 7,
        "Armor ledger not saved");
    helper.assertTrue(
        SubLevelArmor.absorb(body, core.east(), source, 18) == 5
            && SubLevelArmor.status(body).current() == 0,
        "Excess damage was not carried through");
    helper.assertTrue(
        SubLevelArmor.absorb(body, core.east(), source, 3) == 3, "Empty armor absorbed damage");
    helper.assertTrue(
        SubLevelArmor.absorb(body, core.east(), level.damageSources().starve(), 1) == 1,
        "Starvation was blocked by armor");
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 30, "Armor bookkeeping damaged health");
    helper.succeed();
  }
}
