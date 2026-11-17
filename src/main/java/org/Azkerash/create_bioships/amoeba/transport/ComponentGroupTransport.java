package org.Azkerash.create_bioships.amoeba.transport;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.BioshipTissue;
import org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost;

/** One real cargo body and one commit for an indivisible organ. Reuses the shared sinking pose. */
final class ComponentGroupTransport {
  private static final String MEMBERS = "BioshipMovingGroup";

  static boolean begin(AmoebaComponentHost core, BlockPos source, BlockPos target, boolean manual) {
    var sources = core.movementGroup(source);
    if (sources.size() < 2
        || sources.size() > 27
        || new HashSet<>(sources).size() != sources.size()) return false;
    var delta = target.subtract(source);
    var sourceSet = new HashSet<>(sources);
    var owner = core.body();
    var level = owner.getLevel();
    for (var p : sources) {
      var destination = p.offset(delta);
      if (!owner.getPlot().contains(new net.minecraft.world.level.ChunkPos(destination)))
        return false;
      var state = level.getBlockState(destination);
      if (!sourceSet.contains(destination)
          && (!state.isAir() && !BioshipTissue.isTissue(state)
              || !core.permitsGroupTarget(destination))) return false;
      if (core.component(owner, p) == null) return false;
    }
    if (!core.prepareGroup(sources, delta)) return false;
    var capsules = new HashMap<BlockPos, CompoundTag>();
    var hosts = new HashMap<BlockPos, BlockState>();
    for (var p : sources) {
      var entity = core.component(owner, p);
      capsules.put(p, core.capture(entity));
      hosts.put(p, core.hostTissue(entity));
    }
    var low =
        new BlockPos(
            sources.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
            sources.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
            sources.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
    var high = low.offset(2, 2, 2);
    var cargo =
        SubLevelAssemblyHelper.assembleBlocks(level, source, sources, new BoundingBox3i(low, high));
    if (cargo == null) return false;
    var cargoOrigin = cargo.getPlot().getCenterBlock();
    var members = new ListTag();
    for (var p : sources) {
      var offset = p.subtract(source);
      var carried = cargoOrigin.offset(offset);
      core.restore(cargo, carried, capsules.get(p));
      var entry = new CompoundTag();
      entry.putLong("Offset", offset.asLong());
      members.add(entry);
      if (!hosts.get(p).isAir()) level.setBlock(p, hosts.get(p), 3);
    }
    var data = new CompoundTag();
    data.put(MEMBERS, members);
    data.putUUID("BioshipCargoOwner", owner.getUniqueId());
    data.putBoolean("BioshipBound", true);
    cargo.setUserDataTag(data);
    cargo.setName("Bioship moving organ structure");
    var container = SubLevelContainer.getContainer(level);
    container.addForceLoadTicket(cargo, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    boolean forced =
        container.addForceLoadTicket(
            owner, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    core.groupDeparted(sources);
    core.movement(
        new ComponentTransport.Move(
            cargo.getUniqueId(),
            cargoOrigin,
            source,
            target,
            owner.logicalPose().transformPosition(Vec3.atCenterOf(source)),
            level.getGameTime(),
            false,
            false,
            false,
            true,
            manual,
            forced,
            ComponentTransport.findTissuePath(core, source, target)));
    core.changed();
    return true;
  }

  /** A blocked destination rolls the entire structure back; no member is committed individually. */
  static boolean finish(
      AmoebaComponentHost core, ServerSubLevel cargo, ComponentTransport.Move move) {
    var data = cargo.getUserDataTag();
    if (data == null || !data.contains(MEMBERS)) return false;
    var offsets = new ArrayList<BlockPos>();
    for (var entry : data.getList(MEMBERS, 10))
      offsets.add(BlockPos.of(((CompoundTag) entry).getLong("Offset")));
    if (offsets.size() != 27 || new HashSet<>(offsets).size() != 27) return true;
    var destination = data.getBoolean("BioshipGroupRollback") ? move.source() : move.target();
    if (!available(core, offsets, destination)) {
      data.putBoolean("BioshipGroupRollback", true);
      cargo.setUserDataTag(data);
      destination = move.source();
      if (!available(core, offsets, destination))
        return true; // Retain real cargo until safe to restore.
    }
    var level = core.body().getLevel();
    var capsules = new HashMap<BlockPos, CompoundTag>();
    var hosts = new HashMap<BlockPos, BlockState>();
    var carried = new ArrayList<BlockPos>();
    for (var offset : offsets) {
      var p = move.cargoBlock().offset(offset);
      carried.add(p);
      capsules.put(offset, core.capture(core.component(cargo, p)));
      var target = destination.offset(offset);
      hosts.put(offset, level.getBlockState(target));
    }
    for (var offset : offsets) {
      var target = destination.offset(offset);
      if (!level.getBlockState(target).isAir()) level.removeBlock(target, false);
    }
    SubLevelAssemblyHelper.moveBlocks(
        level,
        new SubLevelAssemblyHelper.AssemblyTransform(
            move.cargoBlock(), destination, 0, Rotation.NONE, level),
        carried);
    for (var offset : offsets) {
      var target = destination.offset(offset);
      core.restore(core.body(), target, capsules.get(offset));
      var entity = core.component(core.body(), target);
      if (entity != null) {
        core.rememberHost(entity, hosts.get(offset));
        entity.setChanged();
      }
    }
    ComponentTransport.finishCargo(core, cargo);
    core.invalidateVision();
    core.merged();
    return true;
  }

  private static boolean available(
      AmoebaComponentHost core, List<BlockPos> offsets, BlockPos origin) {
    for (var offset : offsets) {
      var target = origin.offset(offset);
      var state = core.level().getBlockState(target);
      if (!core.body().getPlot().contains(new net.minecraft.world.level.ChunkPos(target))
          || !state.isAir() && !BioshipTissue.isTissue(state)) return false;
    }
    return true;
  }

  private ComponentGroupTransport() {}
}
