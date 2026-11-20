package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CrabLegBlock;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

/** Opt-in dedicated-server experiment. Generate a normal overworld; never alter its terrain. */
@EventBusSubscriber(modid = "create_bioships")
public final class PrototypeTerrainQa {
  private static final int SETTLE_TICKS = 120, TRAVEL_TICKS = 240;
  private static final int ROUTE_BLOCKS = 16;
  private static final int LEG_HEIGHT = Integer.getInteger("core.terrainLegHeight", 1);
  private static final boolean RUN_ONLY = Boolean.getBoolean("core.terrainRunOnly");

  private static int trialsPerSite() {
    return RUN_ONLY ? 2 : 4;
  }

  private static double speed() {
    return RUN_ONLY ? 4 : trial % 4 < 2 ? 2 : 4;
  }

  private static final Path OUTPUT =
      Path.of(System.getProperty("core.terrainOutput", "build/prototype-terrain"));

  private record Site(String kind, BlockPos start, int elevationRange, int trunks) {}

  private static final List<Site> sites = new ArrayList<>();
  private static ServerSubLevel subject;
  private static int age, trial, waypoint, blocked;
  private static Vec3 start, previous, goal;
  private static double distance, minUp, minY, maxY;
  private static List<Vec3> route;
  private static org.azkerash.createbioships.core.pathfinding.WaypointNavigator navigator;
  private static int detourUpdates;
  private static boolean started, done, routeFound;
  private static int arrived;

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (!Boolean.getBoolean("core.terrainQa") || done) {
      return;
    }
    var server = event.getServer();
    var level = server.overworld();
    try {
      if (!started) {
        started = true;
        if (level.getChunkSource().getGenerator()
            instanceof net.minecraft.world.level.levelgen.FlatLevelSource) {
          throw new IllegalStateException("Terrain comparison requires a normally generated world");
        }
        Files.createDirectories(OUTPUT);
        Files.writeString(
            OUTPUT.resolve("results.csv"),
            "terrain,mode,leg_height,requested_speed,seed,x,y,z,elevation_range,trunks,route_found,arrival_ticks,distance,net_distance,min_up,vertical_range,blocked_commands,detour_updates\n");
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        selectSites(level);
        if (sites.isEmpty()) {
          throw new IllegalStateException("No dry, clear natural spawn locations");
        }
        spawn(level);
        return;
      }
      if (subject.isRemoved()) {
        throw new IllegalStateException("Subject unloaded before trial: " + trial);
      }
      var container = SubLevelContainer.getContainer(level);
      for (var part : SubLevelCreatureParts.members(subject)) {
        container.addForceLoadTicket(
            part,
            dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
            net.minecraft.util.Unit.INSTANCE);
      }
      age++;
      if (age < SETTLE_TICKS) {
        return;
      }
      var position = SubLevelPathfinder.position(subject);
      if (age == SETTLE_TICKS) {
        if (!BrainMotorControls.active(subject) || SubLevelLegs.segments(subject).size() != 12) {
          throw new IllegalStateException(
              "Inactive terrain subject: living="
                  + SubLevelData.isLiving(subject)
                  + " health="
                  + SubLevelHealth.status(subject)
                  + " parts="
                  + SubLevelLegs.segments(subject).size());
        }
        start = previous = position;
        minY = maxY = position.y;
        minUp = 1;
        distance = 0;
        arrived = blocked = waypoint = detourUpdates = 0;
        navigator = new org.azkerash.createbioships.core.pathfinding.WaypointNavigator();
        var site = sites.get(trial / trialsPerSite());
        var end = ground(level, site.start.getX(), site.start.getZ() + ROUTE_BLOCKS);
        if (end == null) {
          end = site.start.south(ROUTE_BLOCKS);
        }
        double height = position.y - site.start.getY();
        goal = new Vec3(end.getX() + .5, end.getY() + height, end.getZ() + .5);
        var planned = SubLevelPathfinder.find(subject, goal, SubLevelPathfinder.Mode.GROUND);
        routeFound = planned.found();
        route = routeFound ? planned.points() : List.of(goal);
      }
      minUp =
          Math.min(
              minUp,
              subject.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0)).y);
      minY = Math.min(minY, position.y);
      maxY = Math.max(maxY, position.y);
      distance += position.distanceTo(previous);
      previous = position;
      if (arrived == 0) {
        var progress =
            navigator.follow(subject, route, waypoint, speed(), 3, SubLevelPathfinder.Mode.GROUND);
        waypoint = progress.next();
        if (progress.detouring()) {
          detourUpdates++;
        }
        if (!progress.accepted()) {
          blocked++;
        }
        if (progress.arrived() || horizontal(position, goal) < 1) {
          arrived = age - SETTLE_TICKS;
          BrainMotorControls.stop(subject);
        }
      }
      if (age % 80 == 0) {
        System.out.println(
            "TERRAIN_PROGRESS trial="
                + trial
                + " tick="
                + age
                + " travelled="
                + distance
                + " up="
                + minUp);
      }
      if (age >= SETTLE_TICKS + TRAVEL_TICKS || arrived > 0) {
        finish(level, position);
        trial++;
        if (trial >= sites.size() * trialsPerSite()) {
          done = true;
          Files.writeString(
              OUTPUT.resolve("complete.txt"), "Completed " + trial + " natural-terrain trials\n");
          server.halt(false);
        } else {
          spawn(level);
        }
      }
    } catch (Exception error) {
      done = true;
      error.printStackTrace();
      try {
        Files.writeString(OUTPUT.resolve("failure.txt"), error.toString());
      } catch (Exception ignored) {
        // Original failure remains in the server log even if artifact writing fails.
      }
      server.halt(false);
    }
  }

  private static double horizontal(Vec3 a, Vec3 b) {
    return Math.hypot(a.x - b.x, a.z - b.z);
  }

  private static BlockPos ground(ServerLevel level, int x, int z) {
    level.getChunk(x >> 4, z >> 4);
    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - 64); y--) {
      var pos = new BlockPos(x, y, z);
      var state = level.getBlockState(pos);
      if (!state.getFluidState().isEmpty()) {
        return null;
      }
      if (!state.is(BlockTags.LEAVES)
          && !state.is(BlockTags.LOGS)
          && !state.getCollisionShape(level, pos).isEmpty()) {
        return pos;
      }
    }
    return null;
  }

  private static void selectSites(ServerLevel level) throws Exception {
    Site gentle = null, rough = null, wooded = null;
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
        System.out.println(
            "TERRAIN_REGION biome=" + biome.location() + " center=" + found.getFirst());
      }
    }
    for (var origin : origins) {
      for (int x = origin.getX() - 48; x <= origin.getX() + 48; x += 16) {
        for (int z = origin.getZ() - 48; z <= origin.getZ() + 48; z += 16) {
          var base = ground(level, x, z);
          if (base == null) {
            continue;
          }
          boolean clear = true;
          for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
              for (int dy = 1; dy <= 5; dy++) {
                if (!level
                    .getBlockState(base.offset(dx, dy, dz))
                    .getCollisionShape(level, base.offset(dx, dy, dz))
                    .isEmpty()) {
                  clear = false;
                }
              }
            }
          }
          if (!clear) {
            continue;
          }
          int low = base.getY(), high = low, trunks = 0;
          boolean dry = true;
          for (int dz = 0; dz <= ROUTE_BLOCKS; dz += 2) {
            for (int dx : new int[] {-2, 0, 2}) {
              var floor = ground(level, x + dx, z + dz);
              if (floor == null) {
                dry = false;
                continue;
              }
              low = Math.min(low, floor.getY());
              high = Math.max(high, floor.getY());
              if (level.getBlockState(floor.above()).is(BlockTags.LOGS)) {
                trunks++;
              }
            }
          }
          if (!dry) {
            continue;
          }
          int range = high - low;
          if (range >= 1 && range <= 3 && trunks == 0 && gentle == null) {
            gentle = new Site("gentle", base, range, trunks);
          }
          if (range >= 4
              && range <= 10
              && trunks == 0
              && (rough == null || range > rough.elevationRange)) {
            rough = new Site("rough", base, range, trunks);
          }
          if (trunks > 0 && (wooded == null || trunks > wooded.trunks)) {
            wooded = new Site("wooded", base, range, trunks);
          }
        }
      }
    }
    for (var site : new Site[] {gentle, rough, wooded}) {
      if (site != null) {
        sites.add(site);
        System.out.println("TERRAIN_SITE " + site);
      }
    }
    Files.writeString(
        OUTPUT.resolve("sites.txt"),
        "Normal overworld seed="
            + level.getSeed()
            + "\n"
            + sites
            + "\n"
            + "Terrain is unmodified. Ground lookup skips foliage/logs to identify soil below"
            + " trees.\n");
  }

  private static void spawn(ServerLevel level) {
    var site = sites.get(trial / trialsPerSite());
    for (int x = (site.start.getX() - 32) >> 4; x <= (site.start.getX() + 32) >> 4; x++) {
      for (int z = (site.start.getZ() - 32) >> 4; z <= (site.start.getZ() + 64) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    var base = site.start.above(4);
    var core = base.above();
    var cells = new LinkedHashMap<BlockPos, BlockState>();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        cells.put(base.offset(x, 0, z), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState());
      }
    }
    cells.put(core, ComponentRegistry.CORE.get().defaultBlockState());
    cells.put(core.west(), ComponentRegistry.BRAIN.get().defaultBlockState());
    for (int x : new int[] {-1, 1}) {
      for (int z : new int[] {-1, 1}) {
        cells.put(
            base.offset(x, 0, z),
            ComponentRegistry.CRAB_LEG
                .get()
                .defaultBlockState()
                .setValue(CrabLegBlock.HEIGHT, LEG_HEIGHT)
                .setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    z < 0 ? Direction.NORTH : Direction.SOUTH));
      }
    }
    // Spawn cells are above the original terrain and are removed into the new sublevel immediately.
    cells.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
    subject =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            core,
            List.copyOf(cells.keySet()),
            new BoundingBox3i(base.offset(-1, 0, -1), base.offset(1, 1, 1)));
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(
            subject,
            dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
            net.minecraft.util.Unit.INSTANCE);
    CoreAssemblyRules.mark(level, subject.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(subject);
    SubLevelLegs.request(subject);
    PrototypeLocomotion.set(
        subject,
        trial % 2 == 0 ? PrototypeLocomotion.Mode.NATIVE_FAST : PrototypeLocomotion.Mode.PROTOTYPE);
    age = 0;
    System.out.println(
        "TERRAIN_BEGIN trial="
            + trial
            + " site="
            + site
            + " mode="
            + PrototypeLocomotion.get(subject));
  }

  private static void finish(ServerLevel level, Vec3 position) throws Exception {
    BrainMotorControls.stop(subject);
    var site = sites.get(trial / trialsPerSite());
    String row =
        String.format(
            Locale.ROOT,
            "%s,%s,%d,%.1f,%d,%d,%d,%d,%d,%d,%s,%d,%.3f,%.3f,%.6f,%.3f,%d,%d%n",
            site.kind,
            PrototypeLocomotion.get(subject),
            LEG_HEIGHT,
            speed(),
            level.getSeed(),
            site.start.getX(),
            site.start.getY(),
            site.start.getZ(),
            site.elevationRange,
            site.trunks,
            routeFound,
            arrived,
            distance,
            horizontal(position, start),
            minUp,
            maxY - minY,
            blocked,
            detourUpdates);
    Files.writeString(OUTPUT.resolve("results.csv"), row, StandardOpenOption.APPEND);
    System.out.println("TERRAIN_RESULT " + row.strip());
    var container = SubLevelContainer.getContainer(level);
    for (var part : List.copyOf(SubLevelCreatureParts.members(subject))) {
      if (!part.isRemoved()) {
        container.removeSubLevel(part, SubLevelRemovalReason.REMOVED);
      }
    }
    subject = null;
  }
}
