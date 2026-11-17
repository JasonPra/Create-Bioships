package org.Azkerash.create_bioships.amoeba.components;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.*;
import org.Azkerash.create_bioships.FeedingMouthBlockEntity;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.creature.api.CreatureBodyAccess;
import org.Azkerash.create_bioships.creature.resources.ResourceAccount;

/** Amoeba-only component lifecycle; other creature kinds need not implement this capability. */
public interface AmoebaComponentHost extends CreatureBodyAccess {
  record Request(
      BlockPos source,
      BlockPos target,
      boolean external,
      boolean manual,
      boolean consuming,
      boolean stash,
      String mergeRecipe) {}

  record Prepared(
      ServerSubLevel sourceBody,
      Vec3 sourceWorld,
      BlockState sourceHost,
      CompoundTag capsule,
      boolean mouth,
      boolean stomach,
      List<BlockPos> tissuePath,
      CompoundTag cargoTags) {
    public Prepared {
      tissuePath = List.copyOf(tissuePath);
    }
  }

  /** An indivisible formed organ moves together; single components use a one-cell group. */
  default List<BlockPos> movementGroup(BlockPos source) {
    return List.of(source);
  }

  default boolean prepareGroup(List<BlockPos> sources, BlockPos delta) {
    return false;
  }

  default boolean permitsGroupTarget(BlockPos target) {
    return false;
  }

  default void groupDeparted(List<BlockPos> sources) {}

  ResourceAccount resources();

  ComponentTransport.Move movement();

  void movement(ComponentTransport.Move move);

  boolean absorptionActive();

  boolean alive();

  List<ComponentTransport.Move> absorptions();

  /** Validates existing anatomy rules and reserves their costs; null means rejected. */
  Prepared prepare(Request request);

  void departed(Request request, Prepared prepared);

  void forgetSource(BlockPos source);

  boolean owns(BlockPos pos);

  boolean travelTissue(BlockState state);

  boolean validAttachment(BlockState state, BlockPos target);

  boolean leg(BlockState state);

  Direction legFacing(BlockPos target);

  Direction mouthFacing(BlockPos target);

  BlockEntity component(ServerSubLevel owner, BlockPos pos);

  List<BlockEntity> components();

  CompoundTag capture(BlockEntity entity);

  void restore(ServerSubLevel owner, BlockPos pos, CompoundTag capsule);

  void rememberHost(BlockEntity entity, BlockState host);

  BlockState hostTissue(BlockEntity entity);

  void changed();

  void invalidateVision();

  void receive(ServerSubLevel cargo, BlockPos pos, boolean reclaim);

  void placed(ComponentTransport.Move move, boolean scavenged, boolean recycle, boolean hostFlesh);

  void merged();

  boolean needsStomach();

  boolean digestionReady();

  List<FeedingMouthBlockEntity> mouths();

  void stomachFormed();
}
