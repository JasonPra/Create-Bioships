package org.Azkerash.create_bioships.amoeba;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.*;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** A real, temporary Sable body carries the original block and its complete block entity. */
public final class ComponentTransport {
  public record Move(
      UUID cargo,
      BlockPos cargoBlock,
      BlockPos source,
      BlockPos target,
      Vec3 start,
      long started,
      boolean external,
      boolean restoreFlesh,
      boolean mouth,
      boolean stomach,
      boolean manual,
      boolean ownerForced,
      List<BlockPos> tissuePath) {
    public Move {
      tissuePath = List.copyOf(tissuePath);
    }

    public boolean usesArm() {
      return external;
    }

    public CompoundTag save() {
      var tag = new CompoundTag();
      tag.putUUID("Cargo", cargo);
      tag.putLong("CargoBlock", cargoBlock.asLong());
      tag.putLong("Source", source.asLong());
      tag.putLong("Target", target.asLong());
      tag.putDouble("X", start.x);
      tag.putDouble("Y", start.y);
      tag.putDouble("Z", start.z);
      tag.putLong("Started", started);
      tag.putBoolean("External", external);
      tag.putBoolean("RestoreFlesh", restoreFlesh);
      tag.putBoolean("Mouth", mouth);
      tag.putBoolean("Stomach", stomach);
      tag.putBoolean("Manual", manual);
      tag.putBoolean("OwnerForced", ownerForced);
      tag.putLongArray("TissuePath", tissuePath.stream().mapToLong(BlockPos::asLong).toArray());
      return tag;
    }

    public static Move load(CompoundTag tag) {
      return tag.hasUUID("Cargo")
          ? new Move(
              tag.getUUID("Cargo"),
              BlockPos.of(tag.getLong("CargoBlock")),
              BlockPos.of(tag.getLong("Source")),
              BlockPos.of(tag.getLong("Target")),
              new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
              tag.getLong("Started"),
              tag.getBoolean("External"),
              tag.getBoolean("RestoreFlesh"),
              tag.getBoolean("Mouth"),
              tag.getBoolean("Stomach"),
              tag.getBoolean("Manual"),
              tag.getBoolean("OwnerForced"),
              java.util.Arrays.stream(tag.getLongArray("TissuePath"))
                  .limit(12000)
                  .mapToObj(BlockPos::of)
                  .toList())
          : null;
    }
  }

  public static boolean busy(AmoebaComponentHost core) {
    return core.movement() != null || core.absorptionActive();
  }

  public static boolean begin(
      AmoebaComponentHost core, BlockPos source, BlockPos target, boolean external) {
    return begin(core, source, target, external, false);
  }

  public static boolean begin(
      AmoebaComponentHost core,
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual) {
    return begin(core, source, target, external, manual, false);
  }

  public static boolean reabsorb(AmoebaComponentHost core, BlockPos source, BlockPos target) {
    return begin(core, source, target, false, false, true);
  }

  public static boolean reclaimToCore(AmoebaComponentHost core, BlockPos source) {
    return begin(core, source, core.controllerPosition(), false, false, true, true);
  }

  public static boolean storeComponent(AmoebaComponentHost core, BlockPos source) {
    return begin(core, source, core.controllerPosition(), false, false, false, true);
  }

  public static boolean swallow(AmoebaComponentHost core, BlockPos source) {
    return begin(core, source, core.controllerPosition(), true, false, false, true);
  }

  private static boolean begin(
      AmoebaComponentHost core,
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual,
      boolean consuming) {
    return begin(core, source, target, external, manual, consuming, false);
  }

  private static boolean begin(
      AmoebaComponentHost core,
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual,
      boolean consuming,
      boolean stash) {
    return begin(core, source, target, external, manual, consuming, stash, null);
  }

  public static boolean merge(
      AmoebaComponentHost core, BlockPos source, BlockPos target, String recipe) {
    return begin(core, source, target, false, false, false, false, recipe);
  }

  private static boolean begin(
      AmoebaComponentHost core,
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual,
      boolean consuming,
      boolean stash,
      String mergeRecipe) {
    if ((stash ? core.movement() != null : busy(core))
        || source.equals(target)
        || core.body() == null) return false;
    var request =
        new AmoebaComponentHost.Request(
            source, target, external, manual, consuming, stash, mergeRecipe);
    var prepared = core.prepare(request);
    if (prepared == null) return false;
    var ship = core.body();
    var level = ship.getLevel();
    var sourceHost = prepared.sourceHost();
    boolean restore = !sourceHost.isAir();
    var cargo =
        SubLevelAssemblyHelper.assembleBlocks(
            level, source, List.of(source), new BoundingBox3i(source, source));
    if (cargo == null) return false;
    core.restore(cargo, cargo.getPlot().getCenterBlock(), prepared.capsule());
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(cargo, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    cargo.setName(external ? "Bioship assimilation" : "Bioship moving organ");
    cargo.setUserDataTag(prepared.cargoTags());
    if (restore && !external) level.setBlock(source, sourceHost, 3);
    var move =
        new Move(
            cargo.getUniqueId(),
            cargo.getPlot().getCenterBlock(),
            source,
            target,
            prepared.sourceWorld(),
            level.getGameTime(),
            external,
            restore,
            prepared.mouth(),
            prepared.stomach(),
            manual,
            SubLevelContainer.getContainer(level)
                .addForceLoadTicket(ship, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE),
            prepared.tissuePath());
    core.departed(request, prepared);
    core.movement(move);
    if (external && prepared.sourceBody() == null) core.forgetSource(source);
    return true;
  }

  public static List<BlockPos> findTissuePath(
      AmoebaComponentHost core, BlockPos source, BlockPos target) {
    var level = core.level();
    Map<BlockPos, BlockPos> parents = new HashMap<>();
    var queue = new ArrayDeque<BlockPos>();
    queue.add(source);
    parents.put(source, source);
    while (!queue.isEmpty() && parents.size() < 12000) {
      var current = queue.remove();
      if (current.equals(target)) {
        var path = new ArrayList<BlockPos>();
        var p = current;
        while (!p.equals(source)) {
          path.add(p);
          p = parents.get(p);
        }
        path.add(source);
        Collections.reverse(path);
        // Adjacent free-standing organs have no flesh to sink through.
        return path.stream().anyMatch(cell -> core.travelTissue(level.getBlockState(cell)))
            ? List.copyOf(path)
            : List.of();
      }
      for (var d : Direction.values()) {
        var next = current.relative(d);
        if (parents.containsKey(next) || !core.owns(next)) continue;
        if (!next.equals(target) && !core.travelTissue(level.getBlockState(next))) continue;
        parents.put(next, current);
        queue.add(next);
      }
    }
    return List.of();
  }

  private static Vec3 alongTissue(List<BlockPos> path, double progress) {
    double index = progress * (path.size() - 1);
    int start = Math.min(path.size() - 2, (int) index);
    return Vec3.atCenterOf(path.get(start))
        .lerp(Vec3.atCenterOf(path.get(start + 1)), index - start);
  }

  public static void tick(AmoebaComponentHost core) {
    var move = core.movement();
    var owner = core.body();
    if (move == null || owner == null) return;
    var level = owner.getLevel();
    var container = SubLevelContainer.getContainer(level);
    if (!(container.getSubLevel(move.cargo()) instanceof ServerSubLevel cargo)
        || cargo.isRemoved()) {
      core.movement(null);
      core.changed();
      return;
    }
    long age = level.getGameTime() - move.started();
    double t = Math.clamp((age - 6) / 30.0, 0, 1);
    t = t * t * (3 - 2 * t);
    Vec3 destination = owner.logicalPose().transformPosition(Vec3.atCenterOf(move.target()));
    Vec3 location;
    if (move.external()) location = move.start().lerp(destination, t);
    else {
      Vec3 local;
      if (move.tissuePath().isEmpty()) {
        local =
            Vec3.atCenterOf(move.source())
                .lerp(Vec3.atCenterOf(move.target()), t)
                .add(0, Math.sin(Math.PI * t) * .35, 0);
      } else local = alongTissue(move.tissuePath(), t);
      location = owner.logicalPose().transformPosition(local);
    }
    var orientation = new Quaterniond(owner.logicalPose().orientation());
    var pipeline = SubLevelPhysicsSystem.get(level).getPipeline();
    Vec3 center = cargo.logicalPose().transformPosition(Vec3.atCenterOf(move.cargoBlock()));
    var offset = new Vector3d(cargo.logicalPose().position()).sub(center.x, center.y, center.z);
    pipeline.teleport(
        cargo, new Vector3d(location.x, location.y, location.z).add(offset), orientation);
    pipeline.resetVelocity(cargo);
    if (age < ComponentMerging.DURATION_TICKS) return;
    if (ComponentMerging.finish(core, cargo, move)) return;
    if (cargo.getUserDataTag() != null && cargo.getUserDataTag().getBoolean("BioshipStash")) {
      core.receive(
          cargo, move.cargoBlock(), cargo.getUserDataTag().getBoolean("BioshipCoreReclamation"));
      finishCargo(core, cargo);
      core.invalidateVision();
      return;
    }
    var host = level.getBlockState(move.target());
    if (!host.isAir() && !BioshipTissue.isTissue(host)) {
      release(core, cargo);
      return;
    }
    var original = level.getBlockState(move.cargoBlock());
    if (!core.validAttachment(original, move.target())) {
      release(core, cargo);
      return;
    }
    if (!host.isAir()) level.removeBlock(move.target(), false);
    // Snapshot the current cargo, never an old pre-digestion/pre-growth copy.
    var capsule = core.capture(core.component(cargo, move.cargoBlock()));
    SubLevelAssemblyHelper.moveBlocks(
        level,
        new SubLevelAssemblyHelper.AssemblyTransform(
            move.cargoBlock(), move.target(), 0, Rotation.NONE, level),
        List.of(move.cargoBlock()));
    ComponentAttachment.restore(core, move, host, capsule);
    boolean scavenged =
        cargo.getUserDataTag() != null && cargo.getUserDataTag().getBoolean("BioshipScavenged");
    boolean recycle =
        cargo.getUserDataTag() != null
            && cargo.getUserDataTag().getBoolean("BioshipEmergencyRecycle");
    boolean hostFlesh =
        cargo.getUserDataTag() != null && cargo.getUserDataTag().getBoolean("ScavengeHostFlesh");
    finishCargo(core, cargo);
    core.invalidateVision();
    core.placed(move, scavenged, recycle, hostFlesh);
  }

  static void finishCargo(AmoebaComponentHost core, ServerSubLevel cargo) {
    var move = core.movement();
    var container = SubLevelContainer.getContainer(cargo.getLevel());
    container.removeForceLoadTicket(cargo, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    container.removeSubLevel(cargo, SubLevelRemovalReason.REMOVED);
    if (move != null && move.ownerForced() && core.body() != null)
      container.removeForceLoadTicket(
          core.body(), SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    core.movement(null);
    core.changed();
  }

  static void release(AmoebaComponentHost core, ServerSubLevel cargo) {
    var move = core.movement();
    if (move != null && move.ownerForced() && core.body() != null)
      SubLevelContainer.getContainer(cargo.getLevel())
          .removeForceLoadTicket(
              core.body(), SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    SubLevelContainer.getContainer(cargo.getLevel())
        .removeForceLoadTicket(cargo, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    cargo.setName("Detached bioship component");
    core.movement(null);
    core.changed();
  }

  public static void abandon(AmoebaComponentHost core) {
    var move = core.movement();
    if (move == null || !(core.level() instanceof net.minecraft.server.level.ServerLevel level))
      return;
    var cargo = SubLevelContainer.getContainer(level).getSubLevel(move.cargo());
    if (cargo instanceof ServerSubLevel server) release(core, server);
  }
}
