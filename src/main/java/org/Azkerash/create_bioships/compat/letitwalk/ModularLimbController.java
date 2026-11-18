package org.Azkerash.create_bioships.compat.letitwalk;

import dev.letitwalk.ModContent;
import dev.letitwalk.content.*;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * One native assembly/gait context per creature. Limb bodies remain collision-driven rigid bodies.
 */
@EventBusSubscriber(modid = "create_bioships")
public final class ModularLimbController {
  public static final String DATA = "create_bioships:modular_limbs";
  private static final Map<ServerSubLevel, ModularLimbController> CONTROLLERS =
      new IdentityHashMap<>();

  /** API context only: no hidden walker block, kinetic network, inventory, or independent owner. */
  private static final class Context extends WalkerCoreBlockEntity {
    Context(ServerSubLevel body, BlockPos anchor) {
      super(
          anchor,
          ModContent.WALKER_CORE
              .get()
              .defaultBlockState()
              .setValue(WalkerCoreBlock.FACING, Direction.NORTH));
      setLevel(body.getLevel());
    }

    @Override
    public void syncToClients() {}

    @Override
    public void setChanged() {}

    @Override
    public boolean isPowered() {
      return true;
    }

    @Override
    public float getTheoreticalSpeed() {
      return 32;
    }

    @Override
    public void setLoad(double value) {
      load = value;
    }
  }

  private record Pin(GenericConstraintHandle handle, BuiltLeg leg, int index) {}

  private final ServerSubLevel body;
  private final Context nativeWalker;
  private final List<Pin> pins = new ArrayList<>();
  private final Map<UUID, Map<Long, net.minecraft.world.level.block.Block>> blockTypes =
      new HashMap<>();
  private boolean dirty;
  private long edit = -1;

  private ModularLimbController(ServerSubLevel body, BlockPos anchor) {
    this.body = body;
    nativeWalker = new Context(body, anchor);
    var data = body.getUserDataTag().getCompound(DATA);
    nativeWalker.loadLegs(data.getList("Legs", 10));
    dirty = nativeWalker.legs.isEmpty();
    rememberTopology();
  }

  public static void changed(
      ServerSubLevel physical,
      BlockPos pos,
      net.minecraft.world.level.block.state.BlockState state) {
    var controller = CONTROLLERS.get(SubLevelCreatureParts.root(physical));
    if (controller == null) return;
    var cells =
        controller.blockTypes.computeIfAbsent(physical.getUniqueId(), ignored -> new HashMap<>());
    var previous = cells.get(pos.asLong());
    var next = state.isAir() ? null : state.getBlock();
    if (previous == next) return;
    if (next == null) cells.remove(pos.asLong());
    else cells.put(pos.asLong(), next);
    controller.dirty = true;
  }

  private void rememberTopology() {
    blockTypes.clear();
    for (var member : SubLevelCreatureParts.members(body)) {
      var cells = new HashMap<Long, net.minecraft.world.level.block.Block>();
      for (var pos : SubLevelHealth.blocks(member))
        cells.put(pos.asLong(), member.getLevel().getBlockState(pos).getBlock());
      blockTypes.put(member.getUniqueId(), cells);
    }
  }

  private static BlockPos origin(ServerSubLevel body, SubLevelBrains brain) {
    var cores =
        net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.ResourceLocation.parse("create_bioships:creature_cores"));
    return SubLevelHealth.blocks(body).stream()
        .filter(p -> body.getLevel().getBlockState(p).is(cores))
        .findFirst()
        .orElseGet(
            () ->
                brain.activeCells().stream()
                    .min(Comparator.comparingLong(BlockPos::asLong))
                    .orElseThrow());
  }

  public static int limbCount(ServerSubLevel body) {
    var c = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    return c == null ? 0 : c.nativeWalker.legs.size();
  }

  public static int jointCount(ServerSubLevel body) {
    var c = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    return c == null ? 0 : c.pins.size();
  }

  public static List<ServerSubLevel> segments(ServerSubLevel body) {
    var c = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    return c == null
        ? List.of()
        : BuiltLegDriver.segments(c.nativeWalker).stream()
            .filter(ServerSubLevel.class::isInstance)
            .map(ServerSubLevel.class::cast)
            .toList();
  }

  /** Called outside physics iteration; the native assembler moves blocks between physical plots. */
  public static boolean assemble(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    if (!SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) return false;
    var brain = SubLevelBrains.get(body);
    if (brain.activeCells().isEmpty()) return false;
    var owner = body;
    var c = CONTROLLERS.computeIfAbsent(body, b -> new ModularLimbController(b, origin(b, brain)));
    return SubLevelHealth.withoutRemovalDamage(
        owner,
        () -> {
          c.releasePins();
          boolean success = BuiltLegAssembler.rescan(c.nativeWalker, owner.getLevel(), owner);
          if (!success) {
            c.dirty = false;
            c.edit = BuiltLegAssembler.edits();
            return false;
          }
          SubLevelCreatureParts.sync(owner, segments(owner));
          c.rememberTopology();
          c.dirty = false;
          c.edit = BuiltLegAssembler.edits();
          c.nativeWalker.totalMass =
              BuiltLegDriver.totalMass(c.nativeWalker, owner.getLevel(), owner);
          c.save();
          return !c.nativeWalker.legs.isEmpty();
        });
  }

  private void save() {
    var root = body.getUserDataTag().copy();
    var data = new CompoundTag();
    data.putLong("Anchor", nativeWalker.getBlockPos().asLong());
    data.put("Legs", nativeWalker.saveLegs());
    root.put(DATA, data);
    body.setUserDataTag(root);
  }

  private void ensurePins() {
    if (pins.size() == nativeWalker.legs.stream().mapToInt(l -> l.built.joints.size()).sum()
        && !pins.isEmpty()
        && pins.stream().allMatch(p -> p.handle.isValid())) return;
    releasePins();
    var container = SubLevelContainer.getContainer(body.getLevel());
    if (container == null) return;
    var pipeline = container.physicsSystem().getPipeline();
    for (var runtime : nativeWalker.legs) {
      var leg = runtime.built;
      for (int i = 0; i < leg.joints.size(); i++) {
        var joint = leg.joints.get(i);
        var a = container.getSubLevel(joint.parent);
        var b = container.getSubLevel(joint.child);
        if (!(a instanceof ServerSubLevel parent)
            || !(b instanceof ServerSubLevel child)
            || parent.isRemoved()
            || child.isRemoved()) continue;
        var axis = new Vector3d(joint.axis.x, joint.axis.y, joint.axis.z);
        if (axis.lengthSquared() < .001) continue;
        var frame = new Quaterniond().rotationTo(new Vector3d(1, 0, 0), axis.normalize());
        var pin =
            pipeline.addConstraint(
                parent,
                child,
                new GenericConstraintConfiguration(
                    v(joint.parentPlot),
                    v(joint.childPlot),
                    frame,
                    frame,
                    EnumSet.of(
                        ConstraintJointAxis.LINEAR_X,
                        ConstraintJointAxis.LINEAR_Y,
                        ConstraintJointAxis.LINEAR_Z,
                        ConstraintJointAxis.ANGULAR_Y,
                        ConstraintJointAxis.ANGULAR_Z)));
        if (pin == null) continue;
        pin.setContactsEnabled(false);
        pin.setLimit(ConstraintJointAxis.ANGULAR_X, -Math.PI * .85, Math.PI * .85);
        pins.add(new Pin(pin, leg, i));
      }
    }
  }

  private static Vector3d v(Vec3 p) {
    return new Vector3d(p.x, p.y, p.z);
  }

  private void releasePins() {
    for (var pin : pins) if (pin.handle.isValid()) pin.handle.remove();
    pins.clear();
  }

  private boolean commanded() {
    var request = BrainMotorControls.current(body);
    return request.expires() > body.getLevel().getGameTime()
        && request.mode()
            == org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND;
  }

  private void update() {
    BuiltLegDriver.updateActualFeet(nativeWalker, body.getLevel());
    if (!commanded()) return;
    var request = BrainMotorControls.current(body);
    var local = body.logicalPose().orientation().transformInverse(v(request.velocity()));
    int keys = 1 << WalkerBrain.GEAR_SHIFT | WalkerBrain.KEY_CRAB;
    if (local.z < -.05) keys |= WalkerBrain.KEY_FORWARD;
    if (local.z > .05) keys |= WalkerBrain.KEY_BACK;
    if (local.x < -.05) keys |= WalkerBrain.KEY_LEFT;
    if (local.x > .05) keys |= WalkerBrain.KEY_RIGHT;
    nativeWalker.input = keys;
    nativeWalker.lastInputTime = body.getLevel().getGameTime();
    nativeWalker.mouseTurnRate = request.yawRate();
    var ignored = new HashSet<UUID>();
    for (var member : SubLevelCreatureParts.members(body)) ignored.add(member.getUniqueId());
    TerrainProbe.withIgnored(
        s -> ignored.contains(s.getUniqueId()), () -> WalkerBrain.tick(nativeWalker, body));
    nativeWalker.targetVelocity.set(request.velocity().x, 0, request.velocity().z);
    BuiltLegDriver.solve(nativeWalker, body, body.getLevel().getGameTime());
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      var container = SubLevelContainer.getContainer(level);
      if (container == null) continue;
      for (var entry : List.copyOf(container.getAllSubLevels())) {
        if (!(entry instanceof ServerSubLevel body)
            || body.isRemoved()
            || !SubLevelData.isLiving(body)
            || SubLevelCreatureParts.isPart(body)) continue;
        var c = CONTROLLERS.get(body);
        if (c == null) {
          boolean saved = body.getUserDataTag().contains(DATA);
          if (!saved
              && (event.getServer().getTickCount() % 10 != 0
                  || SubLevelHealth.blocks(body).stream()
                      .noneMatch(
                          p ->
                              level.getBlockState(p).getBlock()
                                  instanceof
                                  org.Azkerash.create_bioships
                                      .components
                                      .logic
                                      .legs
                                      .ModularJointBlock))) continue;
          var brain = SubLevelBrains.get(body);
          if (brain.activeCells().isEmpty()) continue;
          var data = body.getUserDataTag().getCompound(DATA);
          var anchor =
              data.contains("Anchor") ? BlockPos.of(data.getLong("Anchor")) : origin(body, brain);
          c = new ModularLimbController(body, anchor);
          CONTROLLERS.put(body, c);
        }
        if (SubLevelHealth.status(body).dead()) continue;
        if (c.dirty || c.edit != BuiltLegAssembler.edits()) assemble(body);
        c.update();
      }
    }
    var iterator = CONTROLLERS.entrySet().iterator();
    while (iterator.hasNext()) {
      var e = iterator.next();
      if (e.getKey().isRemoved()) {
        e.getValue().releasePins();
        BuiltLegDriver.releaseAll(e.getValue().nativeWalker);
        iterator.remove();
      }
    }
  }

  @SubscribeEvent
  public static void physics(
      dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent event) {
    for (var c : CONTROLLERS.values()) {
      if (c.body.isRemoved() || c.body.getLevel() != event.getPhysicsSystem().getLevel()) continue;
      c.ensurePins();
      boolean drive = c.commanded() && !SubLevelHealth.status(c.body).dead();
      // Physical joint motors have finite torque and yield to real terrain collision.
      double torque = Math.clamp(c.body.getMassTracker().getMass() * 8, 20, 1000);
      for (var pin : c.pins) {
        var tuning =
            LimbTuning.get(
                c.body.getLevel(), BlockPos.containing(pin.leg.joints.get(pin.index).parentPlot));
        pin.handle.setMotor(
            ConstraintJointAxis.ANGULAR_X,
            pin.leg.angles[pin.index],
            drive ? 300 * tuning.stiffness() : 2,
            drive ? 30 * tuning.damping() : 4,
            true,
            drive ? torque : 1);
      }
      if (drive && c.nativeWalker.stanceCount > 0) {
        var handle = RigidBodyHandle.of(c.body);
        if (handle != null && handle.isValid())
          WalkerPhysics.substep(c.nativeWalker, c.body, handle, event.getTimeStep());
      }
    }
  }

  @SubscribeEvent
  public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
    for (var c : CONTROLLERS.values()) {
      c.save();
      c.releasePins();
      BuiltLegDriver.releaseAll(c.nativeWalker);
    }
    CONTROLLERS.clear();
  }
}
