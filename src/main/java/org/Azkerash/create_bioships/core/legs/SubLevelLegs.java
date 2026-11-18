package org.Azkerash.create_bioships.core.legs;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.compat.letitwalk.CrabLegKinematics;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;

/** One topology cache, gait and contact-force controller per creature, never per-block tickers. */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelLegs {
  public record Pose(BlockPos cell, List<Vec3> joints) {}

  private static final Map<ServerSubLevel, SubLevelLegs> CONTROLLERS = new IdentityHashMap<>();

  private static final class Leg {
    final BlockPos cell;
    final CrabLegKinematics solver;
    Vec3 planted, from, to, contact;
    boolean swing, grounded;
    long started;
    List<Vec3> points = List.of();

    Leg(BlockPos cell, int height) {
      this.solver = new CrabLegKinematics(height);
      this.cell = cell.immutable();
    }
  }

  private final ServerSubLevel body;
  private final Map<BlockPos, Leg> legs = new LinkedHashMap<>();
  private boolean dirty = true;
  private double phase;
  private long lastSent = Long.MIN_VALUE;

  private SubLevelLegs(ServerSubLevel body) {
    this.body = body;
  }

  public static void request(ServerSubLevel body) {
    if (SubLevelData.isLiving(body))
      CONTROLLERS.computeIfAbsent(body, SubLevelLegs::new).dirty = true;
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var controller = CONTROLLERS.get(body);
    if (state.is(ComponentRegistry.CRAB_LEG.get())) request(body);
    else if (controller != null && controller.legs.containsKey(pos)) controller.dirty = true;
  }

  private void rebuild() {
    if (!dirty) return;
    dirty = false;
    var present = new LinkedHashSet<BlockPos>();
    for (var pos : SubLevelHealth.blocks(body)) {
      var state = body.getLevel().getBlockState(pos);
      if (state.is(ComponentRegistry.CRAB_LEG.get())) present.add(pos);
    }
    legs.keySet().retainAll(present);
    for (var pos : present) {
      int height =
          body.getLevel()
              .getBlockState(pos)
              .getValue(org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock.HEIGHT);
      var old = legs.get(pos);
      if (old == null || old.solver.height != height) legs.put(pos, new Leg(pos, height));
    }
  }

  public static List<Pose> poses(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    if (c == null) return List.of();
    return c.legs.values().stream()
        .filter(l -> l.points.size() == 4)
        .map(l -> new Pose(l.cell, l.points))
        .toList();
  }

  public static int contacts(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    return c == null ? 0 : (int) c.legs.values().stream().filter(l -> l.grounded).count();
  }

  private void update() {
    rebuild();
    if (legs.isEmpty()) return;
    boolean alive = !SubLevelHealth.status(body).dead();
    var command = BrainMotorControls.current(body);
    boolean moving =
        alive && (command.velocity().lengthSqr() > .001 || Math.abs(command.yawRate()) > .01);
    if (moving) phase += .12;
    long now = body.getLevel().getGameTime();
    int index = 0;
    for (var leg : legs.values()) {
      var state = body.getLevel().getBlockState(leg.cell);
      var face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
      var side = Vec3.atLowerCornerOf(face.getNormal());
      // Socket root is flush to its supporting face; feet project outward and down.
      var root = Vec3.atCenterOf(leg.cell).subtract(side.scale(.25));
      var desiredLocal =
          root.add(side.scale(1.45 * leg.solver.scale)).add(0, -leg.solver.standingHeight(), 0);
      var desired =
          body.logicalPose().transformPosition(desiredLocal).add(command.velocity().scale(.25));
      var ground = alive ? leg.solver.ground(body, desired) : null;
      boolean stance = !moving || ((phase + (index++ % 2) * .5) % 1) < .65;
      if (ground == null) {
        leg.grounded = false;
        leg.planted = null;
        leg.swing = false;
        leg.points = leg.solver.solve(root, face, desiredLocal);
        continue;
      }
      var target = ground.point().add(0, .03, 0);
      if (leg.planted == null) leg.planted = target;
      if (!stance && moving && !leg.swing) {
        leg.swing = true;
        leg.started = now;
        leg.from = leg.planted;
        leg.to = target;
      }
      if (leg.swing) {
        double t = Math.clamp((now - leg.started) / 6d, 0, 1);
        double smooth = t * t * (3 - 2 * t);
        leg.planted = leg.from.lerp(leg.to, smooth).add(0, Math.sin(t * Math.PI) * .35, 0);
        if (t >= 1) {
          leg.swing = false;
          leg.planted = target;
        }
      } else if (!moving && leg.planted.distanceToSqr(target) > .64) leg.planted = target;
      leg.grounded =
          !leg.swing
              && root.distanceTo(body.logicalPose().transformPositionInverse(leg.planted))
                  < 2.4 * leg.solver.scale;
      leg.contact = target;
      leg.points =
          leg.solver.solve(root, face, body.logicalPose().transformPositionInverse(leg.planted));
    }
    if (lastSent == Long.MIN_VALUE || now - lastSent >= 4) {
      lastSent = now;
      for (var player : body.getLevel().players())
        if (player
                .position()
                .distanceToSqr(
                    body.logicalPose()
                        .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock())))
            < 192 * 192)
          PacketDistributor.sendToPlayer(
              player, new LegPosePayload(body.getUniqueId(), poses(body)));
    }
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    // Discover pre-existing saved creatures as well as newly assembled sockets.
    if (event.getServer().getTickCount() % 20 == 0)
      for (var level : event.getServer().getAllLevels()) {
        var container = SubLevelContainer.getContainer(level);
        if (container != null)
          for (var entry : container.getAllSubLevels())
            if (entry instanceof ServerSubLevel body
                && SubLevelData.isLiving(body)
                && !CONTROLLERS.containsKey(body)) request(body);
      }
    CONTROLLERS.keySet().removeIf(ServerSubLevel::isRemoved);
    if (event.getServer().getTickCount() % 2 == 0)
      for (var controller : CONTROLLERS.values()) controller.update();
  }

  @SubscribeEvent
  public static void physics(
      dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent event) {
    var level = event.getPhysicsSystem().getLevel();
    double dt = event.getTimeStep();
    if (!(dt > 0 && dt <= .1)) return;
    for (var c : CONTROLLERS.values()) {
      var body = c.body;
      if (body.getLevel() != level || body.isRemoved()) continue;
      var handle = RigidBodyHandle.of(body);
      if (handle == null || !handle.isValid()) continue;
      double mass = body.getMassTracker().getMass();
      if (!Double.isFinite(mass) || mass <= 0) continue;
      if (SubLevelHealth.status(body).dead()) continue;
      int contacts = contacts(body);
      if (contacts == 0) continue;
      var velocity = handle.getLinearVelocity();
      var gravity =
          dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(
              level);
      var command = BrainMotorControls.current(body);
      // Fixed force per appendage: extra shell mass has a real locomotion cost.
      double load = Math.min(1, c.legs.size() * 3d / mass);
      var desired = command.velocity().scale(load);
      for (var leg : c.legs.values()) {
        if (!leg.grounded || leg.contact == null) continue;
        var root = body.logicalPose().transformPosition(leg.points.getFirst());
        double heightError = leg.solver.standingHeight() - (root.y - leg.contact.y);
        double lift =
            Math.clamp(
                -gravity.y + 20 * heightError - 7 * velocity.y(), 0, Math.abs(gravity.y) * 2.5 + 4);
        var impulse =
            new Vec3(
                    Math.clamp((desired.x - velocity.x()) * 4, -8 * load, 8 * load),
                    lift,
                    Math.clamp((desired.z - velocity.z()) * 4, -8 * load, 8 * load))
                .scale(mass * dt / contacts);
        // Support acts on the body COM; gait contacts must not inject a
        // plot-space lever arm or tip the body when one stance foot lifts.
        handle.applyLinearImpulse(new org.joml.Vector3d(impulse.x, impulse.y, impulse.z));
      }
      var angular = handle.getAngularVelocity();
      // Bounded upright suspension torque, with brain-controlled yaw only.
      var up = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0));
      double pitch = Math.clamp(-up.z * 8 - angular.x() * 3, -8, 8) * mass * dt;
      double roll = Math.clamp(up.x * 8 - angular.z() * 3, -8, 8) * mass * dt;
      double torque = (command.yawRate() * load - angular.y()) * mass * dt * .6;
      handle.applyAngularImpulse(
          new org.joml.Vector3d(pitch, Math.clamp(torque, -mass * dt, mass * dt), roll));
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CONTROLLERS.clear();
  }
}
