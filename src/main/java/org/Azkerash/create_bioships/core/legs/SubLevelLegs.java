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
  public record Pose(BlockPos cell, List<Vec3> joints, List<Vec3> collisionEndpoints) {}

  private static final Map<ServerSubLevel, SubLevelLegs> CONTROLLERS = new IdentityHashMap<>();

  private static final class Leg {
    final BlockPos cell;
    CrabLegKinematics solver;
    int desiredHeight;
    Vec3 planted, from, to, contact;
    boolean swing, grounded;
    long started;
    List<Vec3> points = List.of();

    Leg(BlockPos cell, int height) {
      this.solver = new CrabLegKinematics(1);
      this.desiredHeight = height;
      this.cell = cell.immutable();
    }
  }

  private final ServerSubLevel body;
  private final org.Azkerash.create_bioships.compat.letitwalk.CompactLegCollisions collisions;
  private final Map<BlockPos, Leg> legs = new LinkedHashMap<>();
  private boolean dirty = true;
  private double phase;
  private boolean hullContact;
  private long lastSent = Long.MIN_VALUE, lastContactUpdate = Long.MIN_VALUE;

  private SubLevelLegs(ServerSubLevel body) {
    this.body = body;
    this.collisions = new org.Azkerash.create_bioships.compat.letitwalk.CompactLegCollisions(body);
  }

  public static void request(ServerSubLevel body) {
    if (SubLevelData.isLiving(body)) {
      org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.get(body);
      CONTROLLERS.computeIfAbsent(body, SubLevelLegs::new).dirty = true;
    }
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
    collisions.retain(present);
    for (var pos : present) {
      int height =
          body.getLevel()
              .getBlockState(pos)
              .getValue(org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock.HEIGHT);
      var old = legs.get(pos);
      if (old == null) legs.put(pos, new Leg(pos, height));
      else old.desiredHeight = height;
    }
  }

  public static List<Pose> poses(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    if (c == null) return List.of();
    return c.legs.values().stream()
        .filter(l -> l.points.size() == 4)
        .map(l -> new Pose(l.cell, l.points, c.collisions.endpoints(l.cell, l.points)))
        .toList();
  }

  public static int collisionShapes(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    return c == null ? 0 : c.collisions.count();
  }

  public static boolean collisionOverlaps(
      ServerSubLevel body, net.minecraft.world.phys.AABB box, double tolerance) {
    var c = CONTROLLERS.get(body);
    return c != null && c.collisions.overlaps(box, tolerance);
  }

  public static net.minecraft.world.phys.AABB collisionBounds(ServerSubLevel body) {
    var c = CONTROLLERS.get(body);
    return c == null ? null : c.collisions.bounds();
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
      if (Math.abs(leg.solver.extension - leg.desiredHeight) > .001)
        leg.solver =
            new CrabLegKinematics(
                leg.solver.extension
                    + Math.clamp(leg.desiredHeight - leg.solver.extension, -.05, .05));
      var state = body.getLevel().getBlockState(leg.cell);
      var face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
      var side = Vec3.atLowerCornerOf(face.getNormal());
      // Socket root is flush to its supporting face; feet project outward and down.
      var root = Vec3.atCenterOf(leg.cell).subtract(side.scale(.25));
      var tuning =
          org.Azkerash.create_bioships.compat.letitwalk.LimbTuning.get(body.getLevel(), leg.cell);
      var adjustedSide =
          new org.joml.Quaterniond()
              .rotationY(Math.toRadians(tuning.restAngle()))
              .transform(new org.joml.Vector3d(side.x, side.y, side.z));
      side = new Vec3(adjustedSide.x, adjustedSide.y, adjustedSide.z);
      var desiredLocal =
          root.add(side.scale(1.45 * leg.solver.scale)).add(0, -leg.solver.standingHeight(), 0);
      var desired =
          body.logicalPose().transformPosition(desiredLocal).add(command.velocity().scale(.25));
      var ground = alive ? leg.solver.ground(body, desired) : null;
      int ordinal = index++;
      boolean stance =
          !moving
              || (legs.size() < 6
                  ? ordinal != (int) Math.floor(phase) % legs.size() || phase % 1 > .4
                  : ((phase + (ordinal % 2) * .5) % 1) < .65);
      if (ground == null) {
        leg.contact = null;
        leg.planted = null;
        leg.swing = false;
        leg.points = leg.solver.solve(root, face, desiredLocal);
        continue;
      }
      var target =
          ground
              .point()
              .add(
                  0,
                  org.Azkerash.create_bioships.compat.letitwalk.CompactLegCollisions.width(
                              2, leg.desiredHeight)
                          * .75
                      + .02,
                  0);
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
        double clearance = .35 + Math.max(0, leg.to.y - leg.from.y);
        leg.planted = leg.from.lerp(leg.to, smooth).add(0, Math.sin(t * Math.PI) * clearance, 0);
        if (t >= 1) {
          leg.swing = false;
          leg.planted = target;
        }
      } else if (!moving && leg.planted.distanceToSqr(target) > .64) leg.planted = target;
      leg.contact = target;
      // Contact is a surface target, never a point below the terrain. Suspension
      // supplies body lift while finite motors articulate the physical bones.
      var foot = leg.planted;
      leg.points = leg.solver.solve(root, face, body.logicalPose().transformPositionInverse(foot));
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

  private org.joml.Vector3d supportNormal() {
    if (legs.isEmpty()) return new org.joml.Vector3d(0, 1, 0);
    var center = Vec3.atCenterOf(body.getPlot().getCenterBlock());
    double sx = 0, sz = 0, sh = 0;
    int n = 0;
    for (var leg : legs.values())
      if (leg.contact != null) {
        sx += leg.cell.getX() - center.x;
        sz += leg.cell.getZ() - center.z;
        sh += leg.contact.y + leg.solver.standingHeight() - (leg.cell.getY() - center.y);
        n++;
      }
    if (n < 2) return new org.joml.Vector3d(0, 1, 0);
    sx /= n;
    sz /= n;
    sh /= n;
    double xx = 0, zz = 0, xz = 0, xh = 0, zh = 0;
    for (var leg : legs.values())
      if (leg.contact != null) {
        double x = leg.cell.getX() - center.x - sx, z = leg.cell.getZ() - center.z - sz;
        double h = leg.contact.y + leg.solver.standingHeight() - (leg.cell.getY() - center.y) - sh;
        xx += x * x;
        zz += z * z;
        xz += x * z;
        xh += x * h;
        zh += z * h;
      }
    double det = xx * zz - xz * xz;
    double slopeX = det > 1e-6 ? (xh * zz - zh * xz) / det : (xx > 1e-6 ? xh / xx : 0);
    double slopeZ = det > 1e-6 ? (zh * xx - xh * xz) / det : (zz > 1e-6 ? zh / zz : 0);
    var normal = new org.joml.Vector3d(-slopeX, 1, -slopeZ).normalize();
    double yaw = body.logicalPose().orientation().getEulerAnglesYXZ(new org.joml.Vector3d()).y;
    return new org.joml.Quaterniond().rotationY(yaw).transform(normal);
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
    CONTROLLERS
        .entrySet()
        .removeIf(
            e -> {
              if (!e.getKey().isRemoved()) return false;
              e.getValue().collisions.close();
              return true;
            });
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
      if (body.getLevel() != level || body.isRemoved() || c.legs.isEmpty()) continue;
      var handle = RigidBodyHandle.of(body);
      if (handle == null || !handle.isValid()) continue;
      var command = BrainMotorControls.current(body);
      if (c.lastContactUpdate != body.getLevel().getGameTime()) {
        var family = new HashSet<UUID>();
        for (var member : SubLevelCreatureParts.members(body)) family.add(member.getUniqueId());
        dev.letitwalk.walker.TerrainProbe.withIgnored(
            part -> family.contains(part.getUniqueId()),
            () -> {
              for (var leg : c.legs.values()) leg.grounded = c.collisions.grounded(leg.cell);
              c.hullContact =
                  org.Azkerash.create_bioships.compat.letitwalk.LimbGroundContacts.touching(
                      body, body);
            });
        c.lastContactUpdate = body.getLevel().getGameTime();
      }
      int planted = contacts(body);
      boolean controlled =
          command.expires() > body.getLevel().getGameTime()
              && command.mode()
                  == org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND
              && !SubLevelHealth.status(body).dead();
      for (var leg : c.legs.values())
        c.collisions.update(
            leg.cell, leg.desiredHeight, leg.points, controlled && (planted > 0 || c.hullContact));
      if (!controlled || planted < 3) continue;
      double mass = body.getMassTracker().getMass();
      if (!Double.isFinite(mass) || mass <= 0) continue;
      if (SubLevelHealth.status(body).dead()) continue;
      int contacts = contacts(body);
      if (contacts == 0) continue;
      var velocity = handle.getLinearVelocity();
      // Fixed force per appendage: extra shell mass has a real locomotion cost.
      double load = Math.min(1, c.legs.size() * 3d / mass);
      var desired = command.velocity().scale(load);
      for (var leg : c.legs.values()) {
        if (!leg.grounded || leg.contact == null) continue;
        var root = body.logicalPose().transformPosition(leg.points.getFirst());
        var face =
            body.getLevel()
                .getBlockState(leg.cell)
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
        var restFoot =
            leg.points
                .getFirst()
                .add(Vec3.atLowerCornerOf(face.getNormal()).scale(1.45 * leg.solver.scale))
                .add(0, -leg.solver.standingHeight(), 0);
        double error = leg.contact.y - body.logicalPose().transformPosition(restFoot).y;
        double gravity =
            -dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(
                        level)
                    .y
                * (1 + c.collisions.mass() / mass);
        var tuning = org.Azkerash.create_bioships.compat.letitwalk.LimbTuning.get(level, leg.cell);
        double lift =
            Math.clamp(
                gravity + 20 * tuning.stiffness() * error - 7 * tuning.damping() * velocity.y(),
                0,
                gravity * 2.5 + 4);
        var impulse =
            new Vec3(
                    Math.clamp((desired.x - velocity.x()) * 4, -8 * load, 8 * load),
                    lift,
                    Math.clamp((desired.z - velocity.z()) * 4, -8 * load, 8 * load))
                .scale(mass * dt / contacts);
        var com = body.getMassTracker().getCenterOfMass();
        var worldCom = body.logicalPose().transformPosition(new Vec3(com.x(), com.y(), com.z()));
        var moment = root.subtract(worldCom).cross(impulse);
        double limit = mass * dt * 10 / contacts;
        handle.applyLinearAndAngularImpulse(
            new org.joml.Vector3d(impulse.x, impulse.y, impulse.z),
            new org.joml.Vector3d(
                Math.clamp(moment.x, -limit, limit),
                Math.clamp(moment.y, -limit, limit),
                Math.clamp(moment.z, -limit, limit)));
      }
      var angular = handle.getAngularVelocity();
      // Stabilize toward the support plane implied by the leg lengths, rather
      // than toward global level. Unequal legs therefore retain their tilt.
      var desiredUp = c.supportNormal();
      var up = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0));
      var axisAngle =
          new org.joml.Quaterniond().rotationTo(up, desiredUp).get(new org.joml.AxisAngle4d());
      var tilt = new org.joml.Vector3d(axisAngle.x, axisAngle.y, axisAngle.z).mul(axisAngle.angle);
      var localCom = body.getMassTracker().getCenterOfMass();
      var worldCom =
          body.logicalPose().transformPosition(new Vec3(localCom.x(), localCom.y(), localCom.z()));
      var inertia = c.collisions.inertia(worldCom);
      var hull = body.getMassTracker().getInertiaTensor();
      inertia.add(Math.max(1, hull.m00()), Math.max(1, hull.m11()), Math.max(1, hull.m22()));
      double pitch = Math.clamp(tilt.x * 10 - angular.x() * 4, -20, 20) * inertia.x * dt;
      double roll = Math.clamp(tilt.z * 10 - angular.z() * 4, -20, 20) * inertia.z * dt;
      double torque =
          Math.clamp((command.yawRate() * load - angular.y()) * 2, -2, 2) * inertia.y * dt;
      handle.applyAngularImpulse(new org.joml.Vector3d(pitch, torque, roll));
    }
  }

  @SubscribeEvent
  public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
    CONTROLLERS.values().forEach(c -> c.collisions.close());
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CONTROLLERS.clear();
  }
}
