package org.Azkerash.create_bioships;

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
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** A real, temporary Sable body carries the original block and its complete block entity. */
public final class OrganMotion {
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
      return external || tissuePath.isEmpty();
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

  public static boolean busy(LivingCoreBlockEntity core) {
    return core.organMove() != null || core.assimilation().active();
  }

  public static boolean begin(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean external) {
    return begin(core, source, target, external, false);
  }

  public static boolean begin(
      LivingCoreBlockEntity core,
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual) {
    return begin(core, source, target, external, manual, false);
  }

  static boolean reabsorb(LivingCoreBlockEntity core, BlockPos source, BlockPos target) {
    return begin(core, source, target, false, false, true);
  }

  static boolean reclaimToCore(LivingCoreBlockEntity core, BlockPos source) {
    return begin(core, source, core.getBlockPos(), false, false, true, true);
  }

  static boolean storeComponent(LivingCoreBlockEntity core, BlockPos source) {
    return begin(core, source, core.getBlockPos(), false, false, false, true);
  }

  static boolean swallow(LivingCoreBlockEntity core, BlockPos source) {
    return begin(core, source, core.getBlockPos(), true, false, false, true);
  }

  private static boolean begin(
      LivingCoreBlockEntity core,
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual,
      boolean consuming) {
    return begin(core, source, target, external, manual, consuming, false);
  }

  private static boolean begin(
      LivingCoreBlockEntity core,
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual,
      boolean consuming,
      boolean stash) {
    if ((stash ? core.organMove() != null : busy(core))
        || source.equals(target)
        || core.ship() == null) return false;
    var ship = core.ship();
    var level = ship.getLevel();
    if (!stash && !consuming && !DigestiveSystem.permitsPlacement(core, target)) return false;
    var host = level.getBlockState(target);
    if (!stash && !host.isAir() && !BioshipTissue.isTissue(host)) return false;
    var state = level.getBlockState(source);
    if (state.isAir() || state.is(Create_bioships.CORE.get())) return false;
    if (!stash && !consuming && BioshipLegEvolution.leg(state)) {
      var mounts = new java.util.LinkedHashMap<>(BioshipStructure.cells(ship));
      if (!external) mounts.remove(source);
      var oriented = state.setValue(AmoebaLegBlock.FACING, AmoebaLegBlock.outward(mounts, target));
      if (!AmoebaLegBlock.mounted(mounts, target, oriented)) return false;
    }
    var sourceShip = BioshipIntegration.containing(level, source);
    boolean scavenged =
        external && sourceShip != null && sourceShip != ship && BioshipDecay.dead(sourceShip);
    if (external && sourceShip != null && sourceShip != ship && !scavenged) return false;
    Vec3 sourceWorld =
        sourceShip != null
            ? sourceShip.logicalPose().transformPosition(Vec3.atCenterOf(source))
            : Vec3.atCenterOf(source);
    Vec3 destination = ship.logicalPose().transformPosition(Vec3.atCenterOf(target));
    var destinationCell = BlockPos.containing(destination);
    boolean vacatedSource = external && sourceShip == null && destinationCell.equals(source);
    if (!stash && !vacatedSource && !BioshipIntegration.growthSpace(level, destinationCell))
      return false;
    String id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    boolean mouth =
        state.is(Create_bioships.FEEDING_MOUTH.get())
            || id.equals("biomancy:bio_forge") && !core.hasMouth();
    boolean stomach =
        state.is(Create_bioships.STOMACH.get())
            || id.equals("biomancy:decomposer") && DigestiveSystem.stomach(core) == null;
    var entity = level.getBlockEntity(source);
    var sourceHost = BioshipTissue.host(entity);
    boolean restore = !sourceHost.isAir();
    if (!stash
        && !consuming
        && !external
        && !manual
        && !BioshipSupport.safeMove(core, source, target, restore)) return false;
    List<BlockPos> tissuePath = external ? List.of() : findTissuePath(core, source, target);
    if (!external)
      for (var player : level.players())
        if (player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu
            && menu.getContainer() == entity) return false;
    if (!stash
        && !consuming
        && (!external || entity != null && EngineRoom.storage(entity))
        && !DigestiveSystem.reserveOrganRoute(core, source, target)) return false;
    BioshipIntegration.clearGrowthPlant(level, BlockPos.containing(destination));
    var cargo =
        SubLevelAssemblyHelper.assembleBlocks(
            level, source, List.of(source), new BoundingBox3i(source, source));
    if (cargo == null) return false;
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(cargo, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    cargo.setName(external ? "Bioship assimilation" : "Bioship moving organ");
    var data = new CompoundTag();
    data.putUUID("BioshipCargoOwner", ship.getUniqueId());
    data.putBoolean("BioshipBound", true);
    data.putBoolean("BioshipStash", stash);
    data.putBoolean("BioshipCoreReclamation", stash && consuming);
    data.putBoolean("BioshipScavenged", scavenged);
    data.putBoolean("ScavengeHostFlesh", BioshipTissue.isTissue(host));
    var selected = core.brain().lastDecision();
    data.putBoolean(
        "BioshipEmergencyRecycle",
        consuming
            || external
                && selected != null
                && selected.recycle()
                && source.equals(selected.block()));
    cargo.setUserDataTag(data);
    if (restore && !external) level.setBlock(source, sourceHost, 3);
    var move =
        new Move(
            cargo.getUniqueId(),
            cargo.getPlot().getCenterBlock(),
            source,
            target,
            sourceWorld,
            level.getGameTime(),
            external,
            restore,
            mouth,
            stomach,
            manual,
            SubLevelContainer.getContainer(level)
                .addForceLoadTicket(ship, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE),
            tissuePath);
    if (!external) {
      var local = source.subtract(core.getBlockPos());
      core.body().memory.remove(local);
      if (restore) core.body().memory.put(local, level.getBlockState(source));
    }
    core.organMove(move);
    if (external && sourceShip == null) core.vision().forget(core, source);
    return true;
  }

  private static boolean tissue(net.minecraft.world.level.block.state.BlockState state) {
    return BioshipTissue.isTissue(state)
        || state.is(Create_bioships.CARAPACE.get())
        || state.is(Create_bioships.INTESTINE.get())
        || state.is(Create_bioships.ONE_WAY_WINDOW.get());
  }

  static List<BlockPos> findTissuePath(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target) {
    var level = core.getLevel();
    var ship = core.ship();
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
        return path.stream().anyMatch(cell -> tissue(level.getBlockState(cell)))
            ? List.copyOf(path)
            : List.of();
      }
      for (var d : Direction.values()) {
        var next = current.relative(d);
        if (parents.containsKey(next) || BioshipIntegration.containing(level, next) != ship)
          continue;
        if (!next.equals(target) && !tissue(level.getBlockState(next))) continue;
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

  public static void tick(LivingCoreBlockEntity core) {
    var move = core.organMove();
    var owner = core.ship();
    if (move == null || owner == null) return;
    var level = owner.getLevel();
    var container = SubLevelContainer.getContainer(level);
    if (!(container.getSubLevel(move.cargo()) instanceof ServerSubLevel cargo)
        || cargo.isRemoved()) {
      core.organMove(null);
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
    if (age < 36) return;
    if (cargo.getUserDataTag() != null && cargo.getUserDataTag().getBoolean("BioshipStash")) {
      if (cargo.getUserDataTag().getBoolean("BioshipCoreReclamation"))
        core.scavenging().recycleToCore(core, level.getBlockState(move.cargoBlock()));
      else core.assimilation().receive(core, cargo, move.cargoBlock());
      container.removeForceLoadTicket(
          cargo, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
      container.removeSubLevel(cargo, SubLevelRemovalReason.REMOVED);
      if (move.ownerForced())
        container.removeForceLoadTicket(
            owner, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
      core.organMove(null);
      core.vision().invalidate();
      return;
    }
    var host = level.getBlockState(move.target());
    if (!host.isAir() && !BioshipTissue.isTissue(host)) {
      release(core, cargo);
      return;
    }
    var original = level.getBlockState(move.cargoBlock());
    if (BioshipLegEvolution.leg(original)) {
      var mounts = BioshipStructure.cells(owner);
      var oriented =
          original.setValue(AmoebaLegBlock.FACING, AmoebaLegBlock.outward(mounts, move.target()));
      if (!AmoebaLegBlock.mounted(mounts, move.target(), oriented)) {
        // The host may have been mined or moved during transport. Keep the actual cargo
        // instead of attaching the socket to empty space or inventing replacement material.
        release(core, cargo);
        return;
      }
    }
    if (!host.isAir()) level.removeBlock(move.target(), false);
    SubLevelAssemblyHelper.moveBlocks(
        level,
        new SubLevelAssemblyHelper.AssemblyTransform(
            move.cargoBlock(), move.target(), 0, Rotation.NONE, level),
        List.of(move.cargoBlock()));
    var moved = level.getBlockEntity(move.target());
    if (moved != null) {
      BioshipTissue.remember(moved, host);
      if (move.manual()) moved.getPersistentData().putBoolean("BioshipManualPlacement", true);
      moved.setChanged();
    }
    if (move.manual() && level.getBlockState(move.target()).is(Create_bioships.SURFACE_EYE.get()))
      level.setBlock(
          move.target(),
          level.getBlockState(move.target()).setValue(SurfaceEyeBlock.AUTOMATIC, false),
          3);
    if (BioshipLegEvolution.leg(level.getBlockState(move.target())))
      level.setBlock(
          move.target(),
          level
              .getBlockState(move.target())
              .setValue(AmoebaLegBlock.FACING, AmoebaLegBlock.outward(core, move.target())),
          3);
    if (moved instanceof FeedingMouthBlockEntity mouth) {
      mouth.embeddedInHull(BioshipTissue.isTissue(host));
      Direction facing = FeedingMouthBlock.outward(core, move.target());
      level.setBlock(
          move.target(),
          level.getBlockState(move.target()).setValue(FeedingMouthBlock.FACING, facing),
          3);
    }
    if (moved instanceof StomachBlockEntity) {
      var d = move.target().subtract(core.getBlockPos());
      level.setBlock(
          move.target(),
          level
              .getBlockState(move.target())
              .setValue(StomachBlock.FACING, d.getX() < 0 ? Direction.EAST : Direction.WEST),
          3);
    }
    boolean scavenged =
        cargo.getUserDataTag() != null && cargo.getUserDataTag().getBoolean("BioshipScavenged");
    boolean recycle =
        cargo.getUserDataTag() != null
            && cargo.getUserDataTag().getBoolean("BioshipEmergencyRecycle");
    boolean hostFlesh =
        cargo.getUserDataTag() != null && cargo.getUserDataTag().getBoolean("ScavengeHostFlesh");
    container.removeSubLevel(cargo, SubLevelRemovalReason.REMOVED);
    if (move.ownerForced())
      container.removeForceLoadTicket(
          owner, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    core.organMove(null);
    core.vision().invalidate();
    if (scavenged || recycle) core.scavenging().finish(core, move.target(), hostFlesh, recycle);
    else if (move.external()) BioshipIntegration.finishAssimilation(core, move.target());
    DigestiveSystem.connect(core, owner);
    core.coreMotion().layoutChanged(core);
    BioshipAccess.tick(core);
  }

  private static void release(LivingCoreBlockEntity core, ServerSubLevel cargo) {
    var move = core.organMove();
    if (move != null && move.ownerForced() && core.ship() != null)
      SubLevelContainer.getContainer(cargo.getLevel())
          .removeForceLoadTicket(
              core.ship(), SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    SubLevelContainer.getContainer(cargo.getLevel())
        .removeForceLoadTicket(cargo, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    cargo.setName("Detached bioship component");
    core.organMove(null);
  }

  public static void abandon(LivingCoreBlockEntity core) {
    var move = core.organMove();
    if (move == null || !(core.getLevel() instanceof net.minecraft.server.level.ServerLevel level))
      return;
    var cargo = SubLevelContainer.getContainer(level).getSubLevel(move.cargo());
    if (cargo instanceof ServerSubLevel server) release(core, server);
  }
}
