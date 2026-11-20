package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;
import org.azkerash.createbioships.development.testing.network.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class MassHungerTests {
  @GameTest(template = "test_empty", timeoutTicks = 120, batch = "mass_scaled_hunger")
  public static void linked_mass_scales_drain_preserves_fractions_and_pause(GameTestHelper h) {
    var level = h.getLevel();
    var at = new BlockPos(8192, 90, 128);
    for (int cx = 511; cx <= 515; cx++) {
      for (int cz = 7; cz <= 9; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    ServerSubLevel[] bodies = new ServerSubLevel[2];
    h.startSequence()
        .thenExecuteAfter(
            10,
            () -> {
              level.setBlock(
                  at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
              bodies[0] =
                  SubLevelAssemblyHelper.assembleBlocks(
                      level, at, List.of(at), new BoundingBox3i(at, at));
              CoreAssemblyRules.mark(level, bodies[0].getPlot().getCenterBlock());
              DevelopmentLifeSupport.pause(bodies[0]);
              var cells = new ArrayList<BlockPos>();
              for (int x = 0; x < 20; x++) {
                var p = at.offset(x + 32, 0, 0);
                level.setBlock(
                    p, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
                cells.add(p);
              }
              bodies[1] =
                  SubLevelAssemblyHelper.assembleBlocks(
                      level,
                      cells.getFirst(),
                      cells,
                      new BoundingBox3i(cells.getFirst(), cells.getLast()));
            })
        .thenExecuteAfter(
            20,
            () -> {
              var body = bodies[0];
              var part = bodies[1];
              double own = body.getMassTracker().getMass();
              h.assertTrue(own > 0, "Sable root mass was unavailable");
              h.assertTrue(
                  SubLevelHunger.metabolism(body).multiplier() == 1,
                  "Small creature lost the baseline drain rate");
              SubLevelCreatureParts.attach(body, part);
              var metabolism = SubLevelHunger.metabolism(body);
              h.assertTrue(
                  Math.abs(metabolism.mass() - own - part.getMassTracker().getMass()) < 1e-5,
                  "Linked mass omitted a body or counted it twice");
              h.assertTrue(
                  metabolism.multiplier() > 2,
                  "Heavy linked material did not increase hunger drain");
              SubLevelData.hungerState(body, new SubLevelData.Hunger(5, false, 0, 0));
              SubLevelHunger.advance(body, 7);
              double fraction = 7 * metabolism.multiplier();
              h.assertTrue(
                  Math.abs(SubLevelData.hungerState(body).drainTicks() - fraction) < 1e-6,
                  "Mass drain rounded away fractional work");
              var saved = body.getUserDataTag().copy();
              body.setUserDataTag(saved.copy());
              h.assertTrue(
                  Math.abs(SubLevelData.hungerState(body).drainTicks() - fraction) < 1e-6,
                  "Reload lost fractional metabolic progress");
              DevelopmentLifeSupport.pause(body);
              var paused = SubLevelData.hungerState(body);
              SubLevelHunger.advance(body, 10000);
              SubLevelHunger.advance(part, 10000);
              h.assertTrue(
                  paused.equals(SubLevelData.hungerState(body)),
                  "Pause or linked-part advance added hunger debt");
              SubLevelData.hungerState(body, new SubLevelData.Hunger(5, false, 0, 0));
              SubLevelHunger.advance(body, SubLevelHunger.DRAIN_INTERVAL / 2);
              h.assertTrue(
                  SubLevelData.hunger(body) < 5,
                  "Heavy creature did not drain sooner than the baseline minute");
              var snapshot = CreatureStatsSync.snapshot(body);
              var buffer =
                  new net.minecraft.network.RegistryFriendlyByteBuf(
                      io.netty.buffer.Unpooled.buffer(), level.registryAccess());
              try {
                CreatureStatsPayload.CODEC.encode(buffer, snapshot);
                h.assertTrue(
                    snapshot.equals(CreatureStatsPayload.CODEC.decode(buffer)),
                    "Developer statistics codec lost live values");
              } finally {
                buffer.release();
              }
              h.assertTrue(
                  snapshot.rows().stream().anyMatch(r -> r.text().contains("mass /")),
                  "Stats omitted the mass-to-hunger formula");
              SubLevelCreatureParts.detach(part);
              h.assertTrue(
                  SubLevelHunger.metabolism(body).multiplier() == 1,
                  "Removing linked mass did not reduce the drain rate");
              System.out.println(
                  "MASS_HUNGER_PASS mass="
                      + metabolism.mass()
                      + " rate="
                      + metabolism.multiplier()
                      + " fractions=true paused=true linked=true");
            })
        .thenSucceed();
  }
}
