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
 * One native assembly/gait context per creature. Powered poses use the native BuiltLegDriver;
 * passive bodies retain physical hinges for ragdoll collision.
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
  private final Map<UUID, Map<Long, net.minecraft.world.level.block.state.BlockState>> blockTypes =
      new HashMap<>();
  private int physicalContacts;
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
    var next = structural(state);
    if (previous == next) return;
    if (next == null) cells.remove(pos.asLong());
    else cells.put(pos.asLong(), next);
    controller.dirty = true;
  }

  private static net.minecraft.world.level.block.state.BlockState structural(
      net.minecraft.world.level.block.state.BlockState state) {
    if (state.isAir()) return null;
    return state.getBlock() instanceof LegJointBlock ? state : state.getBlock().defaultBlockState();
  }

  private void rememberTopology() {
    blockTypes.clear();
    for (var member : SubLevelCreatureParts.members(body)) {
      var cells = new HashMap<Long, net.minecraft.world.level.block.state.BlockState>();
      for (var pos : SubLevelHealth.blocks(member))
        cells.put(pos.asLong(), structural(member.getLevel().getBlockState(pos)));
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

  /** Measured world-space pivot separation, for development diagnostics of real native joints. */
  public static double maximumJointSeparation(ServerSubLevel body) {
    var controller = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    if (controller == null) return 0;
    var container = SubLevelContainer.getContainer(body.getLevel());
    double gap = 0;
    for (var runtime : controller.nativeWalker.legs)
      for (var joint : runtime.built.joints) {
        var parent = container.getSubLevel(joint.parent);
        var child = container.getSubLevel(joint.child);
        if (parent == null || child == null || parent.isRemoved() || child.isRemoved())
          return Double.POSITIVE_INFINITY;
        gap =
            Math.max(
                gap,
                parent
                    .logicalPose()
                    .transformPosition(joint.parentPlot)
                    .distanceTo(child.logicalPose().transformPosition(joint.childPlot)));
      }
    return gap;
  }

  /**
   * Native chain joints, independent of whether powered pose driving or passive hinges are active.
   */
  public static int jointCount(ServerSubLevel body) {
    var c = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    return c == null ? 0 : c.nativeWalker.legs.stream().mapToInt(l -> l.built.joints.size()).sum();
  }

  public static int passiveJointCount(ServerSubLevel body) {
    var c = CONTROLLERS.get(SubLevelCreatureParts.root(body));
    return c == null ? 0 : (int) c.pins.stream().filter(p -> p.handle.isValid()).count();
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
          var members = new ArrayList<>(segments(owner));
          for (var member : SubLevelCreatureParts.members(owner))
            if (member != owner && member.getUserDataTag().contains(CompactLimbController.PART))
              members.add(member);
          SubLevelCreatureParts.sync(owner, members);
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

  private boolean postureEnabled() {
    return BrainMotorControls.groundPostureEnabled(body);
  }

  private void update() {
    BuiltLegDriver.updateActualFeet(nativeWalker, body.getLevel());
    physicalContacts = 0;
    var ignoredBodies = new HashSet<UUID>();
    for (var member : SubLevelCreatureParts.members(body)) ignoredBodies.add(member.getUniqueId());
    TerrainProbe.withIgnored(
        s -> ignoredBodies.contains(s.getUniqueId()),
        () -> {
          var container = SubLevelContainer.getContainer(body.getLevel());
          for (var leg : nativeWalker.legs) {
            if (leg.arm) continue;
            for (var id : leg.built.segments) {
              var entry = container.getSubLevel(id);
              if (entry instanceof ServerSubLevel part && LimbGroundContacts.touching(part, body)) {
                physicalContacts++;
                break;
              }
            }
          }
        });
    if (!postureEnabled()) return;
    var request = BrainMotorControls.current(body);
    var local = body.logicalPose().orientation().transformInverse(v(request.velocity()));
    int keys = 1 << WalkerBrain.GEAR_SHIFT | WalkerBrain.KEY_CRAB;
    if (local.z < -.05) keys |= WalkerBrain.KEY_FORWARD;
    if (local.z > .05) keys |= WalkerBrain.KEY_BACK;
    if (local.x < -.05) keys |= WalkerBrain.KEY_LEFT;
    if (local.x > .05) keys |= WalkerBrain.KEY_RIGHT;
    nativeWalker.input = keys;
    nativeWalker.lastInputTime = body.getLevel().getGameTime();
    nativeWalker.receiveTurn(request.yawRate() * .05);
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
    if (!(event.getTimeStep() > 0 && event.getTimeStep() <= .1)) return;
    for (var c : CONTROLLERS.values()) {
      if (c.body.isRemoved() || c.body.getLevel() != event.getPhysicsSystem().getLevel()) continue;
      if (!c.postureEnabled()) {
        c.ensurePins();
        for (var pin : c.pins) {
          var tuning =
              LimbTuning.get(
                  c.body.getLevel(), BlockPos.containing(pin.leg.joints.get(pin.index).parentPlot));
          pin.handle.setMotor(
              ConstraintJointAxis.ANGULAR_X,
              0,
              2 * tuning.stiffness(),
              4 * tuning.damping(),
              true,
              1);
        }
        continue;
      }
      c.releasePins();
      var handle = RigidBodyHandle.of(c.body);
      if (handle != null && handle.isValid()) {
        WalkerPhysics.substep(c.nativeWalker, c.body, handle, event.getTimeStep());
        BuiltLegDriver.solve(c.nativeWalker, c.body, c.body.getLevel().getGameTime());
        WalkerCargo.substep(
            c.nativeWalker,
            c.body,
            BuiltLegDriver.substep(c.nativeWalker, c.body, event.getTimeStep()),
            event.getTimeStep());
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
