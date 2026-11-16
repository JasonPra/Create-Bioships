package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
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
    boolean digestive, produced, reportedDead, missing, falseDeath;
    int layoutSignature = Integer.MIN_VALUE;
    net.minecraft.world.phys.Vec3 position = net.minecraft.world.phys.Vec3.ZERO;
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
    }
    if (tick % 20 != 0) return;
    for (var ship : SubLevelContainer.getContainer(level).getAllSubLevels()) {
      for (var entity :
          BioshipIntegration.blockEntities((dev.ryanhcode.sable.sublevel.ServerSubLevel) ship)) {
        if (!(entity instanceof LivingCoreBlockEntity core)) continue;
        var state = states.get(ship.getUniqueId());
        if (core.body().dead()) {
          if (state != null && !state.reportedDead) {
            state.reportedDead = true;
            System.out.println(
                "WILDPROBE DEATH tick="
                    + tick
                    + " ship="
                    + ship.getUniqueId()
                    + " data="
                    + BioshipDiagnostics.snapshot(core));
          }
          continue;
        }
        if (state == null) {
          state = new State();
          states.put(ship.getUniqueId(), state);
        }
        if (BioshipDecay.dead((dev.ryanhcode.sable.sublevel.ServerSubLevel) ship)
            && !state.falseDeath) {
          state.falseDeath = true;
          failures++;
          System.out.println(
              "WILDPROBE FAILURE alive_core_marked_dead tick="
                  + tick
                  + " ship="
                  + ship.getUniqueId());
        }
        state.missing = false;
        state.position = BioshipBlueprint.physical(core, BlockPos.ZERO);
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
        var bodyWork = core.adaptation().behavior().debug();
        var cells = BioshipStructure.cells(core.ship());
        int layoutSignature =
            Objects.hash(
                cells,
                BioshipDecay.dead(core.ship()),
                stomachs.stream().filter(gut -> gut.connected(core)).count());
        if (layoutSignature != state.layoutSignature) {
          state.layoutSignature = layoutSignature;
          System.out.println(
              "WILDPROBE LAYOUT tick="
                  + tick
                  + " ship="
                  + ship.getUniqueId()
                  + " data="
                  + BioshipDiagnostics.snapshot(core));
        }
        String signature =
            action
                + ":"
                + bodyWork.getString("Action")
                + ":"
                + cells.hashCode()
                + ":"
                + BlockPos.containing(BioshipBlueprint.physical(core, BlockPos.ZERO))
                + ":"
                + BioshipStorage.snapshot(core).hashCode();
        state.unchanged = signature.equals(state.signature) ? state.unchanged + 20 : 0;
        state.signature = signature;
        if (bodyWork.getString("Action").equals("CONFIGURE") && state.unchanged == 120) {
          failures++;
          System.out.println(
              "WILDPROBE FAILURE nonprogressing CONFIGURE " + bodyWork.getString("Reason"));
        }
        if (tick % 200 == 0 || core.aquatic().wet() && tick % 40 == 0)
          System.out.println(
              "WILDPROBE tick="
                  + tick
                  + " ship="
                  + ship.getUniqueId()
                  + " position="
                  + BioshipBlueprint.physical(core, BlockPos.ZERO)
                  + " health="
                  + core.body().health()
                  + " air="
                  + core.aquatic().air()
                  + " wet="
                  + core.aquatic().wet()
                  + " aquatic="
                  + core.aquatic().save()
                  + " organMove="
                  + core.organMove()
                  + " action="
                  + action
                  + " reason="
                  + core.brain().reason()
                  + " bodyWork="
                  + bodyWork
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
    for (var entry : states.entrySet()) {
      var state = entry.getValue();
      if (!state.missing && tick - state.lastSeen >= 40) {
        state.missing = true;
        System.out.println(
            "WILDPROBE MISSING tick="
                + tick
                + " ship="
                + entry.getKey()
                + " lastSeen="
                + state.lastSeen
                + " lastPosition="
                + state.position
                + " reportedDead="
                + state.reportedDead);
      }
    }
    if (tick >= Integer.getInteger("bioships.wildTicks", 3600)) {
      long fed = states.values().stream().filter(s -> s.feeds >= 2).count();
      long digestive = states.values().stream().filter(s -> s.digestive).count();
      long alive = states.values().stream().filter(s -> tick - s.lastSeen <= 40).count();
      if (fed < states.size() || digestive < states.size() || alive < states.size()) failures++;

      long produced = states.values().stream().filter(s -> s.produced).count();
      int expected = 3;
      if (states.size() != expected || produced < expected) failures++;
      System.out.println(
          "WILDPROBE COMPLETE ticks="
              + tick
              + " observed="
              + states.size()
              + " digestive="
              + digestive
              + " repeatedlyFed="
              + fed
              + " alive="
              + alive
              + " resourceProducing="
              + produced
              + " expected="
              + expected
              + " porkOffered="
              + offered
              + " failures="
              + failures);
      server.halt(false);
    }
  }

  @SubscribeEvent
  public static void threats(ServerTickEvent.Post event) {
    if (!Boolean.getBoolean("bioships.wildProbe")) return;
    var level = event.getServer().overworld();
    if (level.getGameTime() % 5 != 0) return;
    for (var core : BioshipDiagnostics.cores(level))
      if (!core.body().dead() && BioshipDefense.creeperThreat(core) != null)
        System.out.println(
            "WILDPROBE THREAT gameTime="
                + level.getGameTime()
                + " ship="
                + core.ship().getUniqueId()
                + " data="
                + BioshipDiagnostics.snapshot(core));
  }

  @SubscribeEvent
  public static void blast(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
    if (!Boolean.getBoolean("bioships.wildProbe")) return;
    var explosion = event.getExplosion();
    var nearby =
        states.entrySet().stream()
            .filter(e -> e.getValue().position.distanceToSqr(explosion.center()) < 256)
            .map(Map.Entry::getKey)
            .toList();
    if (nearby.isEmpty()) return;
    System.out.println(
        "WILDPROBE BLAST tick="
            + tick
            + " center="
            + explosion.center()
            + " radius="
            + explosion.radius()
            + " nearby="
            + nearby
            + " source="
            + (explosion.getDirectSourceEntity() == null
                ? "none"
                : explosion.getDirectSourceEntity().getType().getDescriptionId()));
  }
}
