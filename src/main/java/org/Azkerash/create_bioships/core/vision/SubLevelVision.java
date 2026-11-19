package org.Azkerash.create_bioships.core.vision;

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
import org.Azkerash.create_bioships.combat.SubLevelWounds;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** One transient, bounded sensory cache per simulated living grid. No world-block tickers. */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelVision {
  public static final int INTERVAL = 5, EYES_PER_GRID = 4, EYES_PER_TICK = 80;

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
    final Map<Eye, Integer> targets = new HashMap<>();
    List<Eye> eyes = List.of();
    boolean dirty = true;
    int cursor;

    Controller(ServerSubLevel body) {
      for (var pos : SubLevelHealth.blocks(body)) {
        var state = body.getLevel().getBlockState(pos);
        if (EyeVisionProfile.of(state) != null) organs.put(pos.immutable(), state);
      }
    }

    void rebuild() {
      if (!dirty) return;
      var result = new ArrayList<Eye>();
      organs.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              e -> {
                if (e.getValue().is(ComponentRegistry.CORE.get()))
                  for (var face : Direction.values()) result.add(new Eye(e.getKey(), face));
                else
                  result.add(
                      new Eye(e.getKey(), e.getValue().getValue(BlockStateProperties.FACING)));
              });
      eyes = List.copyOf(result);
      cache.keySet().retainAll(eyes);
      targets.keySet().retainAll(eyes);
      dirty = false;
    }
  }

  private SubLevelVision() {}

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var controller = CONTROLLERS.get(body);
    if (controller == null) return;
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
    if (!active(body)) return List.of();
    var controller = CONTROLLERS.get(body);
    if (controller == null) return List.of();
    controller.rebuild();
    long now = body.getLevel().getGameTime();
    return controller.cache.values().stream()
        .filter(s -> now - s.tick <= 40 && !injured(body, s.eye, now))
        .toList();
  }

  public static List<VisionDetection.Observation> detected(ServerSubLevel body) {
    var seen = new LinkedHashMap<UUID, VisionDetection.Observation>();
    long now = body.getLevel().getGameTime();
    for (var member : SubLevelCreatureParts.members(body))
      for (var sight : sights(member))
        for (var target : sight.detected())
          if (now - target.tick() <= 40)
            seen.merge(target.key(), target, (a, b) -> a.tick() > b.tick() ? a : b);
    return List.copyOf(seen.values());
  }

  private static boolean injured(ServerSubLevel body, Eye eye, long now) {
    var wounds = SubLevelWounds.state(body);
    if (body.getLevel().getBlockState(eye.cell).is(ComponentRegistry.CORE.get()))
      return wounds.active(eye.cell, eye.face, now);
    return wounds.active(now).stream().anyMatch(w -> w.surface().cell().equals(eye.cell));
  }

  /**
   * Refresh at most the requested number of eye cones; exposed for deterministic regression tests.
   */
  public static int refresh(ServerSubLevel body, int budget) {
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
      Vec3 up =
          pose.transformPosition(
                  logical.add(
                      eye.face.getAxis() == Direction.Axis.Y
                          ? new Vec3(0, 0, 1)
                          : new Vec3(0, 1, 0)))
              .subtract(origin)
              .normalize();
      boolean water = level.getFluidState(physical).is(FluidTags.WATER);
      int light =
          Math.max(
              level.getBrightness(LightLayer.BLOCK, physical),
              Math.max(0, level.getBrightness(LightLayer.SKY, physical) - level.getSkyDarken()));
      double range =
          EyeVisionProfile.of(state)
              .range(water, light, level.getServer().getPlayerList().getViewDistance() * 16d);
      var rays =
          VisionRaycaster.directions(forward, up).stream()
              .map(direction -> VisionRaycaster.cast(body, origin, direction, range))
              .toList();
      var previous = controller.cache.get(eye);
      var scan =
          VisionDetection.scan(
              body,
              origin,
              forward,
              range,
              controller.targets.getOrDefault(eye, 0),
              previous == null ? List.of() : previous.detected());
      controller.targets.put(eye, scan.next());
      controller.cache.put(
          eye, new Sight(eye, now, range, light, water, rays, scan.observations()));
    }
    return count;
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    var bodies = new ArrayList<ServerSubLevel>();
    for (var level : event.getServer().getAllLevels()) {
      var container = SubLevelContainer.getContainer(level);
      if (container != null)
        for (var entry : container.getAllSubLevels())
          if (entry instanceof ServerSubLevel body && active(body)) bodies.add(body);
    }
    CONTROLLERS.keySet().retainAll(bodies);
    if (event.getServer().getTickCount() % INTERVAL != 0 || bodies.isEmpty()) return;
    int remaining = EYES_PER_TICK;
    for (int i = 0; i < bodies.size() && remaining > 0; i++) {
      var body = bodies.get(Math.floorMod(gridCursor++, bodies.size()));
      remaining -= refresh(body, Math.min(EYES_PER_GRID, remaining));
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CONTROLLERS.clear();
    gridCursor = 0;
  }
}
