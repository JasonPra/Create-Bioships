package org.Azkerash.create_bioships.core.legs;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.compat.letitwalk.CompactLimbController;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/**
 * Grid-owned lifecycle and pose delivery; native Let It Walk owns compact limb gait and support.
 */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelLegs {
  public record Pose(BlockPos cell, List<Vec3> joints, List<Vec3> collisionEndpoints) {}

  private static final Map<ServerSubLevel, CompactLimbController> CONTROLLERS =
      new IdentityHashMap<>();

  private static final Set<net.minecraft.server.level.ServerLevel> OBSERVED =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static final Set<ServerSubLevel> DELETED =
      Collections.newSetFromMap(new IdentityHashMap<>());

  private static void observe(ServerSubLevel body) {
    if (!OBSERVED.add(body.getLevel())) return;
    SubLevelContainer.getContainer(body.getLevel())
        .addObserver(
            new dev.ryanhcode.sable.api.sublevel.SubLevelObserver() {
              @Override
              public void onSubLevelRemoved(
                  dev.ryanhcode.sable.sublevel.SubLevel removed,
                  dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason reason) {
                if (!(removed instanceof ServerSubLevel server)) return;
                var controller = CONTROLLERS.get(server);
                if (controller == null) return;
                controller.close();
                if (reason == dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
                  DELETED.add(server);
              }
            });
  }

  public static void request(ServerSubLevel body) {
    if (!body.isRemoved() && SubLevelData.isLiving(body) && !SubLevelCreatureParts.isPart(body)) {
      observe(body);
      org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.get(body);
      CONTROLLERS.computeIfAbsent(body, CompactLimbController::new).changed();
    }
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var c = CONTROLLERS.get(body);
    if (c != null && (state.is(ComponentRegistry.CRAB_LEG.get()) || c.hasSocket(pos)))
      c.changed(pos, state);
    else if (state.is(ComponentRegistry.CRAB_LEG.get())) request(body);
  }

  public static List<Pose> poses(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    return c == null ? List.of() : c.poses();
  }

  public static List<ServerSubLevel> segments(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    return c == null ? List.of() : c.segments();
  }

  public static int collisionShapes(ServerSubLevel body) {
    return segments(body).size();
  }

  public static int contacts(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    return c == null ? 0 : c.contacts();
  }

  public static net.minecraft.world.phys.AABB collisionBounds(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    return c == null ? null : c.bounds();
  }

  public static boolean collisionOverlaps(
      ServerSubLevel body, net.minecraft.world.phys.AABB box, double tolerance) {
    var c = CONTROLLERS.get(body);
    return c != null && c.overlaps(box, tolerance);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 == 0)
      for (var level : event.getServer().getAllLevels()) {
        var container = SubLevelContainer.getContainer(level);
        if (container != null)
          for (var body : List.copyOf(container.getAllSubLevels()))
            if (body.getUserDataTag() != null
                && body.getUserDataTag().contains(CompactLimbController.PART)) {
              var owner = SubLevelCreatureParts.root(body);
              var socket =
                  BlockPos.of(
                      body.getUserDataTag()
                          .getCompound(CompactLimbController.PART)
                          .getLong("Socket"));
              if ((owner != body
                      && (!level.getBlockState(socket).is(ComponentRegistry.CRAB_LEG.get())
                          || !CompactLimbController.ownsSavedSegment(owner, body)))
                  || (!SubLevelCreatureParts.isPart(body) && SubLevelHealth.status(body).dead()))
                container.removeSubLevel(
                    body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            } else if (SubLevelData.isLiving(body)
                && !SubLevelCreatureParts.isPart(body)
                && !CONTROLLERS.containsKey(body)) request(body);
      }
    var it = CONTROLLERS.entrySet().iterator();
    while (it.hasNext()) {
      var e = it.next();
      if (e.getKey().isRemoved()) {
        if (DELETED.remove(e.getKey())) e.getValue().removed();
        else e.getValue().close();
        it.remove();
      }
    }
    for (var e : List.copyOf(CONTROLLERS.entrySet())) {
      e.getValue().update();
      var body = e.getKey();
      if (body.getLevel().getGameTime() % 4 == 0) {
        var payload = new LegPosePayload(body.getUniqueId(), e.getValue().poses());
        for (var player : body.getLevel().players())
          if (player
                  .position()
                  .distanceToSqr(
                      body.logicalPose()
                          .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock())))
              < 192 * 192) PacketDistributor.sendToPlayer(player, payload);
      }
    }
  }

  @SubscribeEvent
  public static void physics(
      dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent event) {
    double dt = event.getTimeStep();
    if (!(dt > 0 && dt <= .1)) return;
    for (var e : List.copyOf(CONTROLLERS.entrySet()))
      if (!e.getKey().isRemoved() && e.getKey().getLevel() == event.getPhysicsSystem().getLevel())
        e.getValue().physics(dt);
  }

  @SubscribeEvent
  public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
    for (var c : CONTROLLERS.values()) {
      c.save();
      c.close();
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CONTROLLERS.clear();
    OBSERVED.clear();
    DELETED.clear();
  }
}
