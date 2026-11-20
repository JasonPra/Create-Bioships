package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.combat.CombatAdapter;
import org.azkerash.createbioships.components.ComponentRegistry;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ArmorRegenerationTests {
  @GameTest(template = "test_empty", batch = "armor_regeneration", timeoutTicks = 100)
  public static void health_repairs_first_then_armor_and_zero_air_blocks_every_healing_path(
      GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 3, 2));
    l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(
        p.east(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(p.west(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l, p, List.of(p, p.east(), p.west()), new BoundingBox3i(p.west(), p.east()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(l, core);
    SubLevelHealth.status(body); // Initialize the stomach-derived capacity before filling hunger.
    SubLevelData.oxygen(body, new SubLevelData.Oxygen(100, 0, true));
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    SubLevelData.hungerPaused(body, false);
    h.assertTrue(
        SubLevelArmor.absorb(
                    body, core.east(), l.damageSources().mobAttack(CombatAdapter.target(body)), 7)
                == 0
            && SubLevelArmor.status(body).current() == 13,
        "Test strike bypassed armor instead of spending it");
    SubLevelHealth.damage(body, 2, "test_setup");
    SubLevelHunger.advance(body, 160);
    h.assertTrue(
        SubLevelHealth.status(body).current() == SubLevelHealth.status(body).maximum()
            && SubLevelArmor.status(body).current() == 13,
        "Armor repaired before health reached full");
    SubLevelHunger.advance(body, 79);
    h.assertTrue(
        SubLevelArmor.status(body).current() == 13,
        "Armor regenerated before its interval elapsed");
    var saved = body.getUserDataTag().copy();
    body.setUserDataTag(saved.copy());
    SubLevelHunger.advance(body, 1);
    h.assertTrue(
        SubLevelArmor.status(body).current() == 14,
        "Full-health armor failed to regenerate or lost saved timer");
    SubLevelData.oxygen(body, new SubLevelData.Oxygen(0, 0, true));
    SubLevelHunger.advance(body, 160);
    h.assertTrue(
        SubLevelArmor.status(body).current() == 14 && !SubLevelArmor.repair(body, 10),
        "Armor regenerated without oxygen");
    SubLevelHealth.damage(body, 3, "test_setup");
    var hp = SubLevelHealth.status(body).current();
    h.assertTrue(!SubLevelHealth.heal(body, 2), "Direct healing bypassed empty oxygen");
    var target = CombatAdapter.target(body);
    target.heal(5);
    MobEffects.REGENERATION.value().applyEffectTick(target, 0);
    MobEffects.HEAL.value().applyInstantenousEffect(null, null, target, 0, 1);
    h.assertTrue(
        SubLevelHealth.status(body).current() == hp, "Potion/entity healing bypassed empty oxygen");
    SubLevelData.oxygen(body, new SubLevelData.Oxygen(100, 0, true));
    h.assertTrue(SubLevelHealth.heal(body, 3), "Health healing stayed blocked after air returned");
    SubLevelData.hunger(body, 0);
    h.assertTrue(!SubLevelArmor.repair(body, 5), "Starving creature repaired armor");
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    SubLevelHunger.advance(body, 80);
    h.assertTrue(
        SubLevelArmor.status(body).current() == 15,
        "Armor repair failed to resume after life support recovered");
    SubLevelHealth.damage(body, 10000, "test_setup");
    h.assertTrue(
        !SubLevelArmor.repair(body, 20) && !SubLevelHealth.heal(body, 20),
        "Dead creature repaired or revived");
    h.succeed();
  }
}
