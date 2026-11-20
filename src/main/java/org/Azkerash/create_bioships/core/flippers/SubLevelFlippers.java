package org.Azkerash.create_bioships.core.flippers;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.compat.letitwalk.FlipperController;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion;

/** One appendage cache and native adapter per creature. No per-block simulation ticker. */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelFlippers {
  private static final class Fins {
    final Set<BlockPos> positions = new LinkedHashSet<>();
    FlipperController controller;
    boolean dirty = true;
  }

  private static final Map<ServerSubLevel, Fins> CREATURES = new IdentityHashMap<>();

  private SubLevelFlippers() {}

  public static void request(ServerSubLevel body) {
    if (body.isRemoved() || !SubLevelData.isLiving(body) || SubLevelCreatureParts.isPart(body)) {
      return;
    }
    CREATURES.computeIfAbsent(
        body,
        b -> {
          var fins = new Fins();
          for (var p : SubLevelHealth.blocks(b)) {
            if (b.getLevel().getBlockState(p).is(ComponentRegistry.FLIPPER.get())) {
              fins.positions.add(p.immutable());
            }
          }
          SubLevelLocomotion.get(b);
          return fins;
        });
  }

  public static void changed(ServerSubLevel body, BlockPos p, BlockState state) {
    var fins = CREATURES.get(body);
    if (fins == null) {
      if (state.is(ComponentRegistry.FLIPPER.get())) {
        request(body);
      }
      return;
    }
    boolean changed =
        state.is(ComponentRegistry.FLIPPER.get())
            ? fins.positions.add(p.immutable())
            : fins.positions.remove(p);
    if (changed || state.is(ComponentRegistry.FLIPPER.get())) {
      fins.dirty = true;
      SubLevelLocomotion.invalidateRoutes(body);
    }
  }

  public static boolean supporting(ServerSubLevel body) {
    var fins = CREATURES.get(SubLevelCreatureParts.root(body));
    return fins != null && fins.controller != null && fins.controller.supporting();
  }

  public static int count(ServerSubLevel body) {
    var fins = CREATURES.get(body);
    return fins == null ? 0 : fins.positions.size();
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 == 0) {
      for (var level : event.getServer().getAllLevels()) {
        var container = SubLevelContainer.getContainer(level);
        if (container != null) {
          for (var body : List.copyOf(container.getAllSubLevels())) {
            request(body);
          }
        }
      }
    }
    CREATURES.keySet().removeIf(ServerSubLevel::isRemoved);
    for (var e : List.copyOf(CREATURES.entrySet())) {
      var fins = e.getValue();
      if (fins.dirty) {
        if (!fins.positions.isEmpty() && fins.controller == null) {
          fins.controller = new FlipperController(e.getKey());
        }
        if (fins.controller != null) {
          fins.controller.rebuild(fins.positions);
        }
        fins.dirty = false;
      }
      if (fins.controller != null) {
        fins.controller.update();
      }
    }
  }

  @SubscribeEvent
  public static void physics(
      dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent event) {
    double dt = event.getTimeStep();
    if (!(dt > 0 && dt <= .1)) {
      return;
    }
    for (var e : List.copyOf(CREATURES.entrySet())) {
      if (!e.getKey().isRemoved()
          && e.getKey().getLevel() == event.getPhysicsSystem().getLevel()
          && e.getValue().controller != null) {
        e.getValue().controller.physics(dt);
      }
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CREATURES.clear();
  }
}
