package org.azkerash.createbioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.core.locomotion.SubLevelLocomotion;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion;
import org.azkerash.createbioships.core.pathfinding.*;

/** Opt-in normal-overworld experiment, excluded from shipping sources and standard tests. */
@EventBusSubscriber(modid = "create_bioships")
public final class HuntsmanTerrainProbe {
  private static final int SETTLE_TICKS = 160, TRAVEL_TICKS = 1200, SAMPLE_TICKS = 20;
  private static final int DESTINATION_DISTANCE_BLOCKS = 16, SPAWN_LIFT_BLOCKS = 9;
  private static final double WALK_SPEED = 1.5, RUN_SPEED = 3;

  private record Site(String kind, BlockPos ground) {}

  private static final List<Site> SITES = new ArrayList<>();
  private static final WaypointNavigator NAVIGATOR = new WaypointNavigator();
  private static ServerSubLevel subject;
  private static int age, trial, next, rejected, arrived;
  private static Vec3 start, previous, goal;
  private static List<Vec3> route;
  private static double travelled, minUp;
  private static long planningNanos;
  private static boolean initialized, done, routeFound;
  private static Path output;

  private HuntsmanTerrainProbe() {}

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (!Boolean.getBoolean("bioships.huntsmanTerrain") || done) {
      return;
    }
    var server = event.getServer();
    var level = server.overworld();
    try {
      if (!initialized) {
        initialized = true;
        output = Path.of(System.getProperty("bioships.huntsmanOutput", "huntsman-results"));
        Files.createDirectories(output);
        Files.writeString(
            output.resolve("results.csv"),
            "site,appendage,controller,speed,seed,x,y,z,route_found,planning_ms,travelled,net_distance,rejected,arrived_tick,min_up,recovery_attempts,recovery_steps\n");
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        selectSites(level);
        spawn(level);
        return;
      }
      age++;
      if (subject.isRemoved()) {
        throw new IllegalStateException("Huntsman subject unloaded during trial");
      }
      for (var part : SubLevelCreatureParts.members(subject)) {
        SubLevelContainer.getContainer(level)
            .addForceLoadTicket(
                part, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
      }
      if (age < SETTLE_TICKS) {
        return;
      }
      var position = SubLevelPathfinder.position(subject);
      if (age == SETTLE_TICKS) {
        start = previous = position;
        travelled = 0;
        minUp = 1;
        rejected = arrived = next = 0;
        var target =
            ground(
                level,
                SITES.get((trial / 2) % SITES.size()).ground.north(DESTINATION_DISTANCE_BLOCKS));
        if (target == null) {
          throw new IllegalStateException("Selected destination became unavailable");
        }
        goal =
            new Vec3(
                target.getX() + .5,
                target.getY() + position.y - SITES.get((trial / 2) % SITES.size()).ground.getY(),
                target.getZ() + .5);
        long before = System.nanoTime();
        var planned = SubLevelPathfinder.find(subject, goal, SubLevelPathfinder.Mode.GROUND);
        planningNanos = System.nanoTime() - before;
        routeFound = planned.found();
        // Keep the real requested goal visible even when initial planning fails;
        // the normal navigator can then attempt its bounded live recovery.
        route = routeFound ? planned.points() : List.of(goal);
        NAVIGATOR.reset();
        System.out.println(
            "HUNTSMAN_START site="
                + SITES.get((trial / 2) % SITES.size())
                + " mode="
                + PrototypeLocomotion.get(subject)
                + " bounds="
                + SubLevelLocomotion.bounds(subject)
                + " contacts="
                + SubLevelLegs.contacts(subject)
                + " segments="
                + SubLevelLegs.segments(subject).size()
                + " route="
                + route
                + " planning_ms="
                + planningNanos / 1e6);
      }
      travelled += position.distanceTo(previous);
      previous = position;
      minUp =
          Math.min(
              minUp,
              subject.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0)).y);
      if (arrived == 0) {
        var progress =
            NAVIGATOR.follow(
                subject,
                route,
                next,
                trial % 2 == 0 ? WALK_SPEED : RUN_SPEED,
                SAMPLE_TICKS + 1,
                SubLevelPathfinder.Mode.GROUND);
        next = progress.next();
        if (!progress.accepted()) {
          rejected++;
        }
        if (progress.arrived()) {
          arrived = age - SETTLE_TICKS + 1;
        }
      }
      if (age % SAMPLE_TICKS == 0) {
        System.out.println(
            "HUNTSMAN_SAMPLE trial="
                + trial
                + " age="
                + age
                + " position="
                + position
                + " contacts="
                + SubLevelLegs.contacts(subject)
                + " rejected="
                + rejected
                + " remaining="
                + position.distanceTo(goal)
                + " request="
                + BrainMotorControls.current(subject));
      }
      if (age < SETTLE_TICKS + TRAVEL_TICKS && arrived == 0) {
        return;
      }
      var site = SITES.get((trial / 2) % SITES.size());
      String row =
          String.format(
              Locale.ROOT,
              "%s,%s,%s,%.1f,%d,%d,%d,%d,%s,%.3f,%.3f,%.3f,%d,%d,%.5f,%d,%d%n",
              site.kind,
              trial < SITES.size() * 2 ? "arachnid" : "crab",
              trial < SITES.size() * 2 ? "procedural" : "native",
              trial % 2 == 0 ? WALK_SPEED : RUN_SPEED,
              level.getSeed(),
              site.ground.getX(),
              site.ground.getY(),
              site.ground.getZ(),
              routeFound,
              planningNanos / 1e6,
              travelled,
              Math.hypot(position.x - start.x, position.z - start.z),
              rejected,
              arrived,
              minUp,
              SubLevelLegs.recoveryStatus(subject).attempts(),
              SubLevelLegs.recoveryStatus(subject).steps());
      Files.writeString(output.resolve("results.csv"), row, StandardOpenOption.APPEND);
      System.out.println("HUNTSMAN_RESULT " + row.strip());
      BrainMotorControls.stop(subject);
      for (var part : List.copyOf(SubLevelCreatureParts.members(subject))) {
        if (!part.isRemoved()) {
          SubLevelContainer.getContainer(level).removeSubLevel(part, SubLevelRemovalReason.REMOVED);
        }
      }
      trial++;
      if (trial >= SITES.size() * 4) {
        done = true;
        server.halt(false);
      } else {
        spawn(level);
      }
    } catch (Exception exception) {
      done = true;
      exception.printStackTrace();
      server.halt(false);
      throw new IllegalStateException("Normal-terrain huntsman probe failed", exception);
    }
  }

  private static BlockPos ground(ServerLevel level, BlockPos column) {
    level.getChunk(column.getX() >> 4, column.getZ() >> 4);
    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
    for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - 64); y--) {
      var cell = new BlockPos(column.getX(), y, column.getZ());
      var state = level.getBlockState(cell);
      if (!state.getFluidState().isEmpty()) {
        return null;
      }
      if (!state.is(BlockTags.LEAVES)
          && !state.is(BlockTags.LOGS)
          && !state.getCollisionShape(level, cell).isEmpty()) {
        return cell;
      }
    }
    return null;
  }

  private static void selectSites(ServerLevel level) {
    var origins = new ArrayList<BlockPos>();
    for (var biome :
        List.of(
            net.minecraft.world.level.biome.Biomes.PLAINS,
            net.minecraft.world.level.biome.Biomes.FOREST,
            net.minecraft.world.level.biome.Biomes.WINDSWEPT_HILLS)) {
      var found =
          level.findClosestBiome3d(
              holder -> holder.is(biome), level.getSharedSpawnPos(), 4096, 64, 64);
      if (found != null) {
        origins.add(found.getFirst());
      }
    }
    for (int region = 0; region < origins.size(); region++) {
      var origin = origins.get(region);
      boolean selected = false;
      for (int x = -48; x <= 48 && !selected; x += 16) {
        for (int z = -48; z <= 48; z += 16) {
          var floor = ground(level, origin.offset(x, 0, z));
          if (floor == null || ground(level, floor.north(DESTINATION_DISTANCE_BLOCKS)) == null) {
            continue;
          }
          var cells = HuntsmanFixture.anatomy(floor.above(SPAWN_LIFT_BLOCKS));
          if (cells.keySet().stream().allMatch(cell -> level.getBlockState(cell).isAir())) {
            SITES.add(new Site("region_" + region, floor));
            selected = true;
            break;
          }
        }
      }
    }
    if (SITES.isEmpty()) {
      throw new IllegalStateException("No dry natural clearing for the giant huntsman");
    }
  }

  private static void spawn(ServerLevel level) {
    var site = SITES.get((trial / 2) % SITES.size());
    for (int x = (site.ground.getX() - 32) >> 4; x <= (site.ground.getX() + 32) >> 4; x++) {
      for (int z = (site.ground.getZ() - 48) >> 4; z <= (site.ground.getZ() + 32) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    subject =
        HuntsmanFixture.spawn(
            level, site.ground.above(SPAWN_LIFT_BLOCKS), trial < SITES.size() * 2);
    PrototypeLocomotion.set(subject, PrototypeLocomotion.Mode.NATIVE);
    age = 0;
  }
}
