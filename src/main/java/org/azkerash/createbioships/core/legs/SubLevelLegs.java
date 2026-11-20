package org.azkerash.createbioships.core.legs;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.compat.letitwalk.CompactLimbController;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;
import org.azkerash.createbioships.core.*;

/** Grid-owned lifecycle and pose delivery for native crab and adhesive arachnid controllers. */
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
    if (!OBSERVED.add(body.getLevel())) {
      return;
    }
    SubLevelContainer.getContainer(body.getLevel())
        .addObserver(
            new dev.ryanhcode.sable.api.sublevel.SubLevelObserver() {
              @Override
              public void onSubLevelRemoved(
                  dev.ryanhcode.sable.sublevel.SubLevel removed,
                  dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason reason) {
                if (!(removed instanceof ServerSubLevel server)) {
                  return;
                }
                var controller = CONTROLLERS.get(server);
                if (controller == null) {
                  return;
                }
                controller.close();
                if (reason == dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED) {
                  DELETED.add(server);
                }
              }
            });
  }

  public static void request(ServerSubLevel body) {
    if (!body.isRemoved() && SubLevelData.isLiving(body) && !SubLevelCreatureParts.isPart(body)) {
      observe(body);
      org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.get(body);
      CONTROLLERS.computeIfAbsent(body, CompactLimbController::new).changed();
    }
  }

  /** Finish a replacement's native limbs while the former body's plots remain occupied. */
  public static void initialize(ServerSubLevel body) {
    request(body);
    var controller = CONTROLLERS.get(body);
    if (controller != null) {
      controller.update();
    }
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var c = CONTROLLERS.get(body);
    if (c != null && (CompactLegSocketBlock.isSocket(state) || c.hasSocket(pos))) {
      org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.invalidateRoutes(body);
      c.changed(pos, state);
    } else if (CompactLegSocketBlock.isSocket(state)) {
      request(body);
    }
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

  public static String collisionDetails(
      ServerSubLevel body, net.minecraft.world.phys.AABB obstacle) {
    var c = CONTROLLERS.get(body);
    return c == null ? "no controller" : c.collisionDetails(obstacle);
  }

  public static Double stepHeight(ServerSubLevel body) {
    var c = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    return c == null ? null : c.stepHeight();
  }

  /** A blocked movement can request a foothold search, but never grants an unsafe motor lease. */
  public static void climbIntent(ServerSubLevel body, Vec3 movement) {
    var controller = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    if (controller != null) {
      controller.climbIntent(movement);
    }
  }

  /**
   * Adhesive toes deliberately touch a surface. Use the hull and non-leg parts for the command
   * sweep there; each toe has its own reachable collision-face test and Sable collision body.
   */
  public static net.minecraft.world.phys.AABB wallMotionBounds(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    var controller = CONTROLLERS.get(body);
    if (controller == null || !controller.wallAttached()) {
      return null;
    }
    net.minecraft.world.phys.AABB result = null;
    for (var member : SubLevelCreatureParts.members(body)) {
      if (member != body
          && member.getUserDataTag() != null
          && member.getUserDataTag().contains(CompactLimbController.PART)) {
        continue;
      }
      var box = member.boundingBox();
      var world =
          new net.minecraft.world.phys.AABB(
              box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
      result = result == null ? world : result.minmax(world);
    }
    return result;
  }

  public static Vec3 surfaceMotion(ServerSubLevel body, Vec3 movement) {
    var controller = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    return controller == null
        ? new Vec3(movement.x, 0, movement.z)
        : controller.surfaceMotion(movement);
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
    if (event.getServer().getTickCount() % 20 == 0) {
      for (var level : event.getServer().getAllLevels()) {
        var container = SubLevelContainer.getContainer(level);
        if (container != null) {
          for (var body : List.copyOf(container.getAllSubLevels())) {
            if (body.getUserDataTag() != null
                && body.getUserDataTag().contains(CompactLimbController.PART)) {
              var owner = SubLevelCreatureParts.root(body);
              var socket =
                  BlockPos.of(
                      body.getUserDataTag()
                          .getCompound(CompactLimbController.PART)
                          .getLong("Socket"));
              if ((owner != body
                      && (!CompactLegSocketBlock.isSocket(level.getBlockState(socket))
                          || !CompactLimbController.ownsSavedSegment(owner, body)))
                  || (!SubLevelCreatureParts.isPart(body) && SubLevelHealth.status(body).dead())) {
                container.removeSubLevel(
                    body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
              }
            } else if (SubLevelData.isLiving(body)
                && !SubLevelCreatureParts.isPart(body)
                && !CONTROLLERS.containsKey(body)) {
              request(body);
            }
          }
        }
      }
    }
    var it = CONTROLLERS.entrySet().iterator();
    while (it.hasNext()) {
      var e = it.next();
      if (e.getKey().isRemoved()) {
        if (DELETED.remove(e.getKey())) {
          e.getValue().removed();
        } else {
          e.getValue().close();
        }
        it.remove();
      }
    }
    for (var e : List.copyOf(CONTROLLERS.entrySet())) {
      e.getValue().update();
      var body = e.getKey();
      if (body.getLevel().getGameTime() % 4 == 0) {
        var payload = new LegPosePayload(body.getUniqueId(), e.getValue().poses());
        for (var player : body.getLevel().players()) {
          if (player
                  .position()
                  .distanceToSqr(
                      body.logicalPose()
                          .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock())))
              < 192 * 192) {
            PacketDistributor.sendToPlayer(player, payload);
          }
        }
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
    for (var e : List.copyOf(CONTROLLERS.entrySet())) {
      if (!e.getKey().isRemoved() && e.getKey().getLevel() == event.getPhysicsSystem().getLevel()) {
        e.getValue().physics(dt);
      }
    }
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
