package org.azkerash.createbioships.core.vision;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.azkerash.createbioships.combat.SubLevelWounds;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.*;

/** One transient, bounded sensory cache per simulated living grid. No world-block tickers. */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelVision {
  public static final int INTERVAL = 5, EYES_PER_GRID = 256, EYES_PER_TICK = 4096;
  private static final int SIGHT_EXPIRY_TICKS = 40;

  public record Eye(BlockPos cell, Direction face) {}

  public record Sight(
      Eye eye,
      long tick,
      double range,
      int light,
      boolean underwater,
      List<VisionRaycaster.Ray> rays,
      List<VisionDetection.Observation> detected) {}

  private static final Map<ServerSubLevel, Controller> CONTROLLERS = new IdentityHashMap<>();
  private static int gridCursor;

  private static final class Controller {
    final Map<BlockPos, BlockState> organs = new HashMap<>();
    final Map<Eye, Sight> cache = new LinkedHashMap<>();
    final Map<Eye, OpticalTargeting.Sensor> sensors = new LinkedHashMap<>();
    final OpticalTargeting targeting = new OpticalTargeting();
    List<Eye> eyes = List.of();
    boolean dirty = true;
    int cursor;

    Controller(ServerSubLevel body) {
      for (var pos : SubLevelHealth.blocks(body)) {
        var state = body.getLevel().getBlockState(pos);
        if (EyeVisionProfile.of(state) != null) {
          organs.put(pos.immutable(), state);
        }
      }
    }

    void rebuild() {
      if (!dirty) {
        return;
      }
      var result = new ArrayList<Eye>();
      organs.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              e -> {
                if (e.getValue().is(ComponentRegistry.CORE.get())) {
                  for (var face : Direction.values()) {
                    result.add(new Eye(e.getKey(), face));
                  }
                } else {
                  result.add(
                      new Eye(e.getKey(), e.getValue().getValue(BlockStateProperties.FACING)));
                }
              });
      eyes = List.copyOf(result);
      cache.keySet().retainAll(eyes);
      sensors.keySet().retainAll(eyes);
      dirty = false;
    }
  }

  private SubLevelVision() {}

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var controller = CONTROLLERS.get(body);
    if (controller == null) {
      return;
    }
    var old = controller.organs.get(pos);
    if (EyeVisionProfile.of(state) == null) {
      if (old != null) {
        controller.organs.remove(pos);
        controller.dirty = true;
      }
    } else {
      controller.organs.put(pos.immutable(), state);
      if (old == null
          || old.getBlock() != state.getBlock()
          || old.hasProperty(BlockStateProperties.FACING)
              && old.getValue(BlockStateProperties.FACING)
                  != state.getValue(BlockStateProperties.FACING)) {
        controller.dirty = true;
        controller.cache.keySet().removeIf(e -> e.cell.equals(pos));
      }
    }
  }

  public static boolean active(ServerSubLevel body) {
    return !body.isRemoved() && SubLevelData.isLiving(body) && !SubLevelHealth.status(body).dead();
  }

  public static List<Sight> sights(ServerSubLevel body) {
    if (!active(body)) {
      return List.of();
    }
    var controller = CONTROLLERS.get(body);
    if (controller == null) {
      return List.of();
    }
    controller.rebuild();
    long now = body.getLevel().getGameTime();
    return controller.cache.values().stream()
        .filter(s -> now - s.tick <= SIGHT_EXPIRY_TICKS && !injured(body, s.eye, now))
        .toList();
  }

  public static List<VisionDetection.Observation> detected(ServerSubLevel body) {
    var seen = new LinkedHashMap<UUID, VisionDetection.Observation>();
    long now = body.getLevel().getGameTime();
    for (var member : SubLevelCreatureParts.members(body)) {
      for (var sight : sights(member)) {
        for (var target : sight.detected()) {
          if (now - target.tick() <= SIGHT_EXPIRY_TICKS) {
            seen.merge(target.key(), target, (a, b) -> a.tick() > b.tick() ? a : b);
          }
        }
      }
    }
    return List.copyOf(seen.values());
  }

  private static boolean injured(ServerSubLevel body, Eye eye, long now) {
    var wounds = SubLevelWounds.state(body);
    if (body.getLevel().getBlockState(eye.cell).is(ComponentRegistry.CORE.get())) {
      return wounds.active(eye.cell, eye.face, now);
    }
    return wounds.active(now).stream().anyMatch(w -> w.surface().cell().equals(eye.cell));
  }

  /**
   * Refresh at most the requested number of eye cones; exposed for deterministic regression tests.
   */
  public static int refresh(ServerSubLevel body, int budget) {
    int count = refreshSensors(body, budget);
    if (active(body)) {
      track(SubLevelCreatureParts.root(body));
    }
    return count;
  }

  public static List<OpticalTargeting.Sensor> sensors(ServerSubLevel body) {
    var controller = CONTROLLERS.get(body);
    return controller == null
        ? List.of()
        : controller.sensors.values().stream()
            .filter(sensor -> controller.cache.containsKey(sensor.key().eye()))
            .filter(
                sensor ->
                    body.getLevel().getGameTime() - controller.cache.get(sensor.key().eye()).tick()
                        <= SIGHT_EXPIRY_TICKS)
            .filter(sensor -> !injured(body, sensor.key().eye(), body.getLevel().getGameTime()))
            .toList();
  }

  private static int refreshSensors(ServerSubLevel body, int budget) {
    if (!active(body)) {
      CONTROLLERS.remove(body);
      return 0;
    }
    var controller = CONTROLLERS.computeIfAbsent(body, Controller::new);
    controller.rebuild();
    int count = Math.min(Math.max(0, budget), controller.eyes.size());
    var level = body.getLevel();
    long now = level.getGameTime();
    for (int i = 0; i < count; i++) {
      var eye = controller.eyes.get(Math.floorMod(controller.cursor++, controller.eyes.size()));
      if (injured(body, eye, now)) {
        controller.cache.remove(eye);
        continue;
      }
      var state = level.getBlockState(eye.cell);
      var bounds = state.getShape(level, eye.cell).bounds();
      Vec3 normal = Vec3.atLowerCornerOf(eye.face.getNormal());
      double extent =
          eye.face.getAxis().choose(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
      Vec3 logical =
          bounds
              .getCenter()
              .add(Vec3.atLowerCornerOf(eye.cell))
              .add(normal.scale(extent / 2 + .015));
      var pose = body.logicalPose();
      Vec3 origin = pose.transformPosition(logical);
      var physical = BlockPos.containing(origin);
      if (!level.hasChunk(physical.getX() >> 4, physical.getZ() >> 4)) {
        controller.cache.remove(eye);
        continue;
      }
      Vec3 forward = pose.transformPosition(logical.add(normal)).subtract(origin).normalize();
      boolean water = level.getFluidState(physical).is(FluidTags.WATER);
      int light =
          Math.max(
              level.getBrightness(LightLayer.BLOCK, physical),
              Math.max(0, level.getBrightness(LightLayer.SKY, physical) - level.getSkyDarken()));
      double range =
          EyeVisionProfile.of(state)
              .budget(water, light, level.getServer().getPlayerList().getViewDistance() * 16d);
      var key = new OpticalTargeting.Key(body.getUniqueId(), eye);
      controller.sensors.put(
          eye,
          new OpticalTargeting.Sensor(
              key, origin, forward, range, EyeVisionProfile.of(state).aquatic()));
      controller.cache.put(eye, new Sight(eye, now, range, light, water, List.of(), List.of()));
    }
    return count;
  }

  private static void track(ServerSubLevel root) {
    var all = new ArrayList<OpticalTargeting.Sensor>();
    var members = SubLevelCreatureParts.members(root);
    for (var member : members) {
      all.addAll(sensors(member));
    }
    var controller = CONTROLLERS.computeIfAbsent(root, Controller::new);
    var candidates = VisionDetection.candidates(root, all);
    if (org.azkerash.createbioships.core.brain.behavior.HuntingBehavior.ID.equals(
        org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.active(root))) {
      var preyTypes =
          org.azkerash.createbioships.core.brain.behavior.HuntingBehavior.preyTypes(root);
      candidates =
          candidates.stream()
              .filter(
                  candidate -> {
                    var entity = root.getLevel().getEntity(candidate.entity());
                    return entity instanceof net.minecraft.world.entity.LivingEntity living
                        && org.azkerash.createbioships.core.brain.behavior.HuntingBehavior.isPrey(
                            root, living, preyTypes);
                  })
              .toList();
    }
    var byId = new HashMap<UUID, ServerSubLevel>();
    for (var member : members) {
      byId.put(member.getUniqueId(), member);
    }
    long now = root.getLevel().getGameTime();
    var locks =
        controller.targeting.update(
            now,
            all,
            candidates,
            (sensor, point) -> {
              var delta = point.subtract(sensor.origin());
              return VisionRaycaster.cast(
                  byId.get(sensor.key().body()),
                  sensor.origin(),
                  delta.normalize(),
                  delta.length(),
                  sensor.range(),
                  sensor.aquatic());
            });
    for (var member : members) {
      var local = CONTROLLERS.get(member);
      if (local == null) {
        continue;
      }
      local.cache.replaceAll(
          (eye, sight) -> {
            var lock =
                locks.stream()
                    .filter(
                        value ->
                            value.sensor().key().body().equals(member.getUniqueId())
                                && value.sensor().key().eye().equals(eye))
                    .findFirst()
                    .orElse(null);
            var rays = lock == null ? List.<VisionRaycaster.Ray>of() : List.of(lock.ray());
            var detected =
                lock == null || !lock.visible()
                    ? List.<VisionDetection.Observation>of()
                    : List.of(
                        new VisionDetection.Observation(
                            lock.target().entity(),
                            lock.target().creature(),
                            lock.ray().to(),
                            now));
            return new Sight(
                eye,
                sight.tick(),
                sight.range(),
                sight.light(),
                sight.underwater(),
                rays,
                detected);
          });
    }
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    var bodies = new ArrayList<ServerSubLevel>();
    for (var level : event.getServer().getAllLevels()) {
      var container = SubLevelContainer.getContainer(level);
      if (container != null) {
        for (var entry : container.getAllSubLevels()) {
          if (entry instanceof ServerSubLevel body
              && active(body)
              && !SubLevelCreatureParts.isPart(body)) {
            bodies.add(body);
          }
        }
      }
    }
    CONTROLLERS.keySet().removeIf(body -> !active(body));
    if (event.getServer().getTickCount() % INTERVAL != 0 || bodies.isEmpty()) {
      return;
    }
    int remaining = EYES_PER_TICK;
    for (int i = 0; i < bodies.size() && remaining > 0; i++) {
      var body = bodies.get(Math.floorMod(gridCursor++, bodies.size()));
      int budget = Math.min(EYES_PER_GRID, remaining);
      for (var member : SubLevelCreatureParts.members(body)) {
        int used = refreshSensors(member, budget);
        budget -= used;
        remaining -= used;
        if (budget <= 0) {
          break;
        }
      }
      track(body);
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CONTROLLERS.clear();
    gridCursor = 0;
  }
}
