package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Opt-in normal-world soak observer. Never selects goals or changes interior terrain, weather or
 * game rules.
 */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipWildProbe {
  private static int tick, failures, offered;
  private static final Map<UUID, State> states = new HashMap<>();

  private static final class State {
    int unchanged, feeds, lastSeen;
    String signature = "";
    boolean digestive, produced;
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (!Boolean.getBoolean("bioships.wildProbe")) return;
    var server = event.getServer();
    var level = server.overworld();
    tick++;
    if (tick == 1) {
      var spawn = level.getSharedSpawnPos();
      var center =
          EcosystemLab.lab(level) ? EcosystemLab.State.get(level).center : spawn.offset(80, 0, 40);
      EcosystemLab.prepare(level, center);
      for (int n = 0; n < 2; n++) {
        BlockPos pos = null;
        for (int d = 0; d < 64; d++) {
          int x = center.getX() - 20 + n * 40 + d % 8 * 3, z = center.getZ() - 16 + d / 8 * 3;
          var top =
              level.getHeightmapPos(
                  Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
          if (level.getFluidState(top.below()).isEmpty()
              && level.getBlockState(top.below()).isFaceSturdy(level, top.below(), Direction.UP)
              && level.getBlockState(top).canBeReplaced()) {
            pos = top;
            break;
          }
        }
        if (pos == null) {
          failures++;
          continue;
        }
        level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
        BioshipIntegration.awaken(level, pos);
        String[] parts = {"bio_forge", "decomposer", "digester"};
        for (int i = 0; i < parts.length; i++) {
          var p = pos.offset(i * 2 - 2, 0, -3);
          var ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
          if (level.getBlockState(ground).canBeReplaced())
            level.setBlock(
                ground,
                BuiltInRegistries.BLOCK
                    .get(ResourceLocation.parse("biomancy:" + parts[i]))
                    .defaultBlockState(),
                3);
        }
        System.out.println("WILDPROBE seeded normal terrain " + pos);
      }
    }
    if (tick == 1 && Boolean.getBoolean("bioships.wildSwim")) {
      var water = AquaticLab.center(level);
      var start = water.offset(-8, -2, 0);
      level.setBlock(start, Create_bioships.CORE.get().defaultBlockState(), 3);
      BioshipIntegration.awaken(level, start);
      int n = 0;
      for (var id : new String[] {"bio_forge", "decomposer", "digester"}) {
        var pos = start.offset(n++ * 2 - 2, 0, -2);
        level.setBlock(
            pos,
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("biomancy:" + id))
                .defaultBlockState(),
            3);
      }
      System.out.println("WILDPROBE aquatic starter=" + start);
    }
    if (tick % 20 != 0) return;
    for (var ship : SubLevelContainer.getContainer(level).getAllSubLevels()) {
      for (var entity :
          BioshipIntegration.blockEntities((dev.ryanhcode.sable.sublevel.ServerSubLevel) ship)) {
        if (!(entity instanceof LivingCoreBlockEntity core) || core.body().dead()) continue;
        var state = states.computeIfAbsent(ship.getUniqueId(), id -> new State());
        state.lastSeen = tick;
        var stomachs = DigestiveSystem.stomachs(core);
        state.digestive |= !stomachs.isEmpty() && core.hasMouth();
        if ((state.feeds == 0 || tick % 600 == 0) && state.feeds < 4) {
          boolean fed = false;
          for (var mouth : BioshipLarynx.mouths(core))
            for (int slot = 0; slot < mouth.getContainerSize(); slot++) {
              var old = mouth.getItem(slot);
              if (old.isEmpty() || old.is(Items.PORKCHOP)) {
                int add = 64 - old.getCount();
                if (add > 0) {
                  mouth.setItem(slot, new ItemStack(Items.PORKCHOP, 64));
                  offered += add;
                  fed = true;
                }
              }
            }
          if (fed) state.feeds++;
        }
        state.produced |=
            BioshipLegGrowth.missing(core) == 0
                && BioshipStorage.amount(core, MaterialCost.FLESH) > 0;
        var action = core.brain().action();
        var cells = BioshipStructure.cells(core.ship());
        String signature =
            action
                + ":"
                + cells.hashCode()
                + ":"
                + BlockPos.containing(BioshipBlueprint.physical(core, BlockPos.ZERO))
                + ":"
                + BioshipStorage.snapshot(core).hashCode();
        state.unchanged = signature.equals(state.signature) ? state.unchanged + 20 : 0;
        state.signature = signature;
        if (action == BioshipBrain.Action.CONFIGURE && state.unchanged == 120) {
          failures++;
          System.out.println("WILDPROBE FAILURE nonprogressing CONFIGURE " + core.brain().reason());
        }
        if (tick % 200 == 0)
          System.out.println(
              "WILDPROBE tick="
                  + tick
                  + " action="
                  + action
                  + " reason="
                  + core.brain().reason()
                  + " cells="
                  + cells.size()
                  + " stomachs="
                  + stomachs.size()
                  + " connected="
                  + stomachs.stream().filter(s -> s.connected(core)).count()
                  + " coreMotion="
                  + core.coreMotion().active()
                  + " coreRemoved="
                  + core.isRemoved()
                  + " primary="
                  + core.primary(core.ship())
                  + " digestion="
                  + stomachs.stream()
                      .map(
                          gut -> {
                            var data = gut.saveWithFullMetadata(level.registryAccess());
                            return "acid="
                                + gut.acid()
                                + ", queued="
                                + gut.queuedFood()
                                + ", progress="
                                + data.getInt("Progress")
                                + "/"
                                + data.getInt("Duration");
                          })
                      .toList()
                  + " mouthFood="
                  + BioshipLarynx.mouths(core).stream().map(m -> m.getItem(0).getCount()).toList()
                  + " waterUse="
                  + core.aquatic().legUsage().water()
                  + " swimmingLegs="
                  + BioshipLegEvolution.swimmingFraction(core)
                  + " legsMissing="
                  + BioshipLegGrowth.missing(core)
                  + " materials="
                  + BioshipStorage.snapshot(core)
                  + " feeds="
                  + state.feeds);
      }
    }
    if (tick >= Integer.getInteger("bioships.wildTicks", 3600)) {
      long fed = states.values().stream().filter(s -> s.feeds >= 2).count();
      long digestive = states.values().stream().filter(s -> s.digestive).count();
      long alive = states.values().stream().filter(s -> tick - s.lastSeen <= 40).count();
      if (fed < states.size() || digestive < states.size() || alive < states.size()) failures++;

      boolean produced = states.values().stream().anyMatch(s -> s.produced);
      if (!produced) failures++;
      System.out.println(
          "WILDPROBE COMPLETE ticks="
              + tick
              + " observed="
              + states.size()
              + " digestive="
              + digestive
              + " repeatedlyFed="
              + fed
              + " porkOffered="
              + offered
              + " failures="
              + failures);
      server.halt(false);
    }
  }
}
