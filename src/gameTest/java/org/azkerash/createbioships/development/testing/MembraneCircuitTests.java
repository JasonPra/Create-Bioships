package org.azkerash.createbioships.development.testing;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.access.SubLevelPermissions;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = "create_bioships")
public final class MembraneCircuitTests {
  @net.neoforged.bus.api.SubscribeEvent
  public static void left(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) {
    if (Boolean.getBoolean("core.membraneTrace")
        && e.getEntity().getTags().contains("create_bioships:membrane_fixture")) {
      System.out.println(
          "MEMBRANE_GUEST_LEFT "
              + e.getEntity().getName().getString()
              + " reason="
              + e.getEntity().getRemovalReason()
              + " pos="
              + e.getEntity().position());
    }
  }

  @GameTest(template = "test_empty", timeoutTicks = 1600, batch = "membrane_guest_circuit")
  public static void
      both_named_guests_cross_when_allowed_return_around_and_are_blocked_after_revocation(
          GameTestHelper h) {
    var level = h.getLevel();
    MembraneFixture.reset(level);
    awaitStart(h);
  }

  private static void awaitStart(GameTestHelper h) {
    var level = h.getLevel();
    var base = MembraneFixture.base(level);
    if (!level.isPositionEntityTicking(base.offset(3, 0, -3))
        || !level.isPositionEntityTicking(base.offset(-3, 0, -3))) {
      h.runAfterDelay(5, () -> awaitStart(h));
      return;
    }
    MembraneFixture.start(level);
    var body = MembraneFixture.body(level);
    var owner = java.util.UUID.randomUUID();
    SubLevelPermissions.assignIfUnowned(body, owner);
    var guests = new java.util.ArrayList<net.minecraft.world.entity.Mob>();
    h.startSequence()
        .thenExecuteAfter(
            10,
            () -> {
              guests.addAll(MembraneFixture.guests(level));
              h.assertTrue(
                  guests.size() == 2,
                  "Missing circuit guests after entity index: "
                      + guests.stream()
                          .map(m -> m.getName().getString() + " " + m.position())
                          .toList());
            })
        .thenExecuteAfter(
            160,
            () -> {
              for (var mob : guests) {
                h.assertTrue(
                    mob.getZ() < MembraneFixture.base(level).getZ() + .4,
                    "Unassigned guest bypassed membrane");
                h.assertTrue(
                    SubLevelPermissions.add(body, owner, mob), "Could not assign circuit guest");
              }
            })
        .thenWaitUntil(
            () -> {
              for (var mob : guests) {
                h.assertTrue(
                    mob.getPersistentData().getInt("create_bioships:membrane_phase") == 2
                        && mob.getZ() > MembraneFixture.base(level).getZ() + 2,
                    "Waiting for allowed guest crossing: "
                        + mob.getName().getString()
                        + " "
                        + mob.position());
              }
            })
        .thenExecute(
            () -> {
              for (var mob : guests) {
                h.assertTrue(
                    SubLevelPermissions.remove(body, owner, mob.getUUID()),
                    "Could not revoke guest");
              }
            })
        .thenWaitUntil(
            () -> {
              for (var mob : guests) {
                h.assertTrue(
                    mob.getPersistentData().getInt("create_bioships:membrane_phase") == 13
                        && mob.getZ() < MembraneFixture.base(level).getZ(),
                    "Waiting for return corridor: "
                        + mob.getName().getString()
                        + " phase="
                        + mob.getPersistentData().getInt("create_bioships:membrane_phase")
                        + " pos="
                        + mob.position());
              }
            })
        .thenExecuteAfter(
            100,
            () -> {
              for (var mob : guests) {
                h.assertTrue(
                    mob.getZ() < MembraneFixture.base(level).getZ() + .4,
                    "Revoked guest passed membrane again");
              }
              MembraneFixture.reset(level);
            })
        .thenSucceed();
  }
}
