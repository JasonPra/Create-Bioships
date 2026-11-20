package org.azkerash.createbioships.components.logic.flipper;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.flippers.FlipperGeometry;

/** Transient pose samples shared by rendering and submerged-paddle queries; no saved creature state. */
public final class FlipperBlockEntity extends BlockEntity {
  public static final int POSE_INTERVAL_TICKS = 4;
  private static final int STALE_POSE_TICKS = POSE_INTERVAL_TICKS * 3;
  private List<Vec3> previous, points;
  private long received;

  public FlipperBlockEntity(BlockPos p, BlockState s) {
    super(ComponentRegistry.FLIPPER_ENTITY.get(), p, s);
  }

  public static List<Vec3> rest(BlockState s) {
    return FlipperGeometry.rest(s);
  }

  public List<Vec3> points(float partial) {
    if (points == null || level == null || level.getGameTime() - received > STALE_POSE_TICKS) {
      return rest(getBlockState());
    }
    double mix = Math.clamp((level.getGameTime() - received + partial) / POSE_INTERVAL_TICKS, 0, 1);
    return FlipperGeometry.interpolate(previous, points, mix);
  }

  public void pose(List<Vec3> next) {
    if (next.size() != FlipperGeometry.SECTIONS + 1 || level == null) {
      return;
    }
    if (points != null && points.equals(next)) {
      previous = points;
      received = level.getGameTime();
      return;
    }
    previous = points == null ? next : points(0);
    points = List.copyOf(next);
    received = level.getGameTime();
    if (!level.isClientSide) {
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    var tag = super.getUpdateTag(registries);
    if (points != null) {
      var list = new ListTag();
      for (var p : points) {
        var t = new CompoundTag();
        t.putDouble("x", p.x);
        t.putDouble("y", p.y);
        t.putDouble("z", p.z);
        list.add(t);
      }
      tag.put("Pose", list);
    }
    return tag;
  }

  @Override
  public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
    super.handleUpdateTag(tag, registries);
    var list = tag.getList("Pose", 10);
    if (list.size() != FlipperGeometry.SECTIONS + 1) {
      return;
    }
    var next = new ArrayList<Vec3>();
    for (var raw : list) {
      var t = (CompoundTag) raw;
      next.add(new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")));
    }
    pose(next);
  }

  @Override
  public void onDataPacket(
      net.minecraft.network.Connection connection,
      ClientboundBlockEntityDataPacket packet,
      HolderLookup.Provider registries) {
    if (packet.getTag() != null) {
      handleUpdateTag(packet.getTag(), registries);
    }
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
