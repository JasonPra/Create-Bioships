package org.Azkerash.create_bioships.components.logic.flipper;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Disposable render samples only. No inventory, movement requests or saved creature state. */
public final class FlipperBlockEntity extends BlockEntity {
  private List<Vec3> previous, points;
  private long received;

  public FlipperBlockEntity(BlockPos p, BlockState s) {
    super(ComponentRegistry.FLIPPER_ENTITY.get(), p, s);
  }

  public static List<Vec3> rest(BlockState s) {
    var outward =
        Vec3.atLowerCornerOf(s.getValue(BlockStateProperties.HORIZONTAL_FACING).getNormal());
    var base = new Vec3(.5, .5, .5).subtract(outward.scale(.5));
    return List.of(
        base.add(outward.scale(.08)),
        base.add(outward.scale(.36)),
        base.add(outward.scale(.64)),
        base.add(outward.scale(.92)));
  }

  public List<Vec3> points(float partial) {
    if (points == null || level == null || level.getGameTime() - received > 12) {
      return rest(getBlockState());
    }
    double mix = Math.clamp((level.getGameTime() - received + partial) / 4d, 0, 1);
    var result = new ArrayList<Vec3>();
    for (int i = 0; i < points.size(); i++) {
      result.add(previous.get(i).lerp(points.get(i), mix));
    }
    return result;
  }

  public void pose(List<Vec3> next) {
    if (next.size() != 4 || level == null) {
      return;
    }
    if (points != null && points.equals(next)) {
      received = level.getGameTime();
      return;
    }
    previous = points == null ? next : points;
    points = List.copyOf(next);
    received = level.getGameTime();
    if (!level.isClientSide) {
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
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
    if (list.size() != 4) {
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
