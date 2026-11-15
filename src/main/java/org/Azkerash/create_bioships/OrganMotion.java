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
      boolean ownerForced) {
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
              tag.getBoolean("OwnerForced"))
          : null;
    }
  }

  public static boolean busy(LivingCoreBlockEntity core) {
    return core.organMove() != null;
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
    if (busy(core) || source.equals(target) || core.ship() == null) return false;
    var ship = core.ship();
    var level = ship.getLevel();
    var host = level.getBlockState(target);
    if (!host.isAir() && !host.is(Create_bioships.FLESH.get())) return false;
    var state = level.getBlockState(source);
    if (state.isAir() || state.is(Create_bioships.CORE.get())) return false;
    Vec3 sourceWorld =
        external
            ? Vec3.atCenterOf(source)
            : ship.logicalPose().transformPosition(Vec3.atCenterOf(source));
    Vec3 destination = ship.logicalPose().transformPosition(Vec3.atCenterOf(target));
    if (!level.getBlockState(BlockPos.containing(destination)).isAir()) return false;
    String id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    boolean mouth =
        state.is(Create_bioships.FEEDING_MOUTH.get())
            || id.equals("biomancy:bio_forge") && !core.hasMouth();
    boolean stomach =
        state.is(Create_bioships.STOMACH.get())
            || id.equals("biomancy:decomposer") && DigestiveSystem.stomach(core) == null;
    boolean restore = false;
    var entity = level.getBlockEntity(source);
    if (entity != null) restore = entity.getPersistentData().getBoolean("BioshipHostFlesh");
    if (!external)
      for (var part : DinghyGrowth.body())
        if (part.offset().equals(source.subtract(core.getBlockPos()))
            && part.state().is(Create_bioships.FLESH.get())) restore = true;
    if (!external)
      for (var player : level.players())
        if (player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu
            && menu.getContainer() == entity) player.closeContainer();
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
    cargo.setUserDataTag(data);
    if (restore && !external)
      level.setBlock(source, Create_bioships.FLESH.get().defaultBlockState(), 3);
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
                .addForceLoadTicket(ship, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE));
    core.organMove(move);
    return true;
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
      Vec3 from = Vec3.atCenterOf(move.source()), to = Vec3.atCenterOf(move.target());
      double floor = core.getBlockPos().getY() + 1.5;
      Vec3 local;
      if (t < .25) local = from.lerp(new Vec3(from.x, floor, from.z), t * 4);
      else if (t > .75) local = new Vec3(to.x, floor, to.z).lerp(to, (t - .75) * 4);
      else local = new Vec3(from.x, floor, from.z).lerp(new Vec3(to.x, floor, to.z), (t - .25) * 2);
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
    var host = level.getBlockState(move.target());
    if (!host.isAir() && !host.is(Create_bioships.FLESH.get())) {
      release(core, cargo);
      return;
    }
    var original = level.getBlockState(move.cargoBlock());
    if (!host.isAir()) level.removeBlock(move.target(), false);
    SubLevelAssemblyHelper.moveBlocks(
        level,
        new SubLevelAssemblyHelper.AssemblyTransform(
            move.cargoBlock(), move.target(), 0, Rotation.NONE, level),
        List.of(move.cargoBlock()));
    var moved = level.getBlockEntity(move.target());
    if (moved != null) {
      moved
          .getPersistentData()
          .putBoolean("BioshipHostFlesh", host.is(Create_bioships.FLESH.get()));
      if (move.manual()) moved.getPersistentData().putBoolean("BioshipManualPlacement", true);
      moved.setChanged();
    }
    if (moved instanceof FeedingMouthBlockEntity mouth) {
      mouth.embeddedInHull(host.is(Create_bioships.FLESH.get()));
      var d = move.target().subtract(core.getBlockPos());
      Direction facing =
          EngineRoom.inside(core, move.target())
              ? (d.getX() > 0 ? Direction.WEST : Direction.EAST)
              : Direction.DOWN;
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
    container.removeSubLevel(cargo, SubLevelRemovalReason.REMOVED);
    if (move.ownerForced())
      container.removeForceLoadTicket(
          owner, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    core.organMove(null);
    if (move.external()) BioshipIntegration.finishAssimilation(core, move.target());
    DigestiveSystem.connect(core, owner);
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
