package org.azkerash.createbioships.core.vision;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class OpticalTargetingTests {
  @GameTest(template = "test_empty", batch = "shared_optical_budget", timeoutTicks = 20)
  public static void hundred_eyes_share_two_rays_track_one_target_and_handoff(GameTestHelper h) {
    var tracker = new OpticalTargeting();
    UUID body = new UUID(0, 1), entity = new UUID(0, 2);
    var sensors = new ArrayList<OpticalTargeting.Sensor>();
    for (int i = 0; i < 100; i++) {
      sensors.add(
          new OpticalTargeting.Sensor(
              new OpticalTargeting.Key(
                  body, new SubLevelVision.Eye(new BlockPos(i, 0, 0), Direction.EAST)),
              new Vec3(i * .01, .5, 0),
              new Vec3(1, 0, 0),
              128,
              false));
    }
    var target =
        new OpticalTargeting.Candidate(
            entity, null, new AABB(20, 0, -.5, 21, 2, .5), new Vec3(20.5, 1.5, 0), Set.of());
    var count = new AtomicInteger();
    OpticalTargeting.RayQuery clear =
        (sensor, point) -> {
          count.incrementAndGet();
          return new VisionRaycaster.Ray(sensor.origin(), point, false, false);
        };
    var first = tracker.update(0, sensors, List.of(target), clear);
    h.assertTrue(
        count.get() == 2
            && first.size() == 2
            && first.stream()
                .allMatch(lock -> lock.visible() && lock.target().entity().equals(entity)),
        "100 eyes did not share two simultaneous rays to the same target");
    tracker.update(0, sensors, List.of(target), clear);
    h.assertTrue(count.get() == 2, "Repeated refresh overspent the shared budget");
    var selected = first.stream().map(lock -> lock.sensor().key()).toList();
    var turned =
        sensors.stream()
            .map(
                sensor ->
                    selected.contains(sensor.key())
                        ? new OpticalTargeting.Sensor(
                            sensor.key(), sensor.origin(), new Vec3(-1, 0, 0), 128, false)
                        : sensor)
            .toList();
    var handed = tracker.update(5, turned, List.of(target), clear);
    h.assertTrue(
        count.get() == 4
            && handed.size() == 2
            && handed.stream().noneMatch(lock -> selected.contains(lock.sensor().key())),
        "Turning eyes out of FOV did not transfer the target to other optics");
    var blocked = handed.getFirst().sensor().key();
    var mixed =
        tracker.update(
            10,
            turned,
            List.of(target),
            (sensor, point) -> {
              count.incrementAndGet();
              return new VisionRaycaster.Ray(
                  sensor.origin(),
                  sensor.key().equals(blocked) ? sensor.origin().add(.1, 0, 0) : point,
                  sensor.key().equals(blocked),
                  false);
            });
    h.assertTrue(
        count.get() == 6 && mixed.stream().anyMatch(OpticalTargeting.Lock::visible),
        "One blocked eye interrupted the other eye's sight");
    var alternate = tracker.update(15, turned, List.of(target), clear);
    h.assertTrue(
        count.get() == 8
            && alternate.stream().noneMatch(lock -> lock.sensor().key().equals(blocked)),
        "An occluded optic was not replaced by a better vantage");
    tracker.update(20, turned, List.of(), clear);
    h.assertTrue(count.get() == 8, "Empty scene cast rays without a target");
    h.succeed();
  }
}
