package org.azkerash.createbioships.compat.letitwalk;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;
import org.azkerash.createbioships.components.logic.legs.ModularJointEntity;
import org.azkerash.createbioships.core.SubLevelCreatureParts;

/**
 * Grid-owned limits. Block entities retain only a stable component identity during native moves.
 */
public record SocketJointSettings(int swivel, int bend) {
  public static final String DATA = "create_bioships:socket_joints";
  public static final SocketJointSettings DEFAULT = new SocketJointSettings(360, 60);
  private static final ThreadLocal<Integer> MOVING = ThreadLocal.withInitial(() -> 0);

  public SocketJointSettings {
    swivel = Math.clamp(swivel, 0, 360);
    bend = Math.clamp(bend, 0, 150);
  }

  public static String compact(BlockPos socket, int index) {
    if (index < 0 || index > 2) {
      throw new IllegalArgumentException("compact joint index");
    }
    return "c:" + socket.asLong() + ":" + index;
  }

  public static String modular(ServerSubLevel physical, BlockPos cell) {
    return modular(SubLevelCreatureParts.root(physical), physical, cell);
  }

  public static String modular(ServerSubLevel owner, ServerSubLevel physical, BlockPos cell) {
    if (!(physical.getLevel().getBlockEntity(cell) instanceof ModularJointEntity joint)) {
      return null;
    }
    var root = SubLevelCreatureParts.root(owner);
    var tag = root.getUserDataTag() == null ? new CompoundTag() : root.getUserDataTag().copy();
    var data = tag.getCompound(DATA).copy();
    var index = data.getCompound("Index").copy();
    String key = "m:" + joint.componentIdentity();
    String address = physical.getUniqueId() + "@" + cell.asLong();
    if (!key.equals(index.getString(address))) {
      for (var old : java.util.Set.copyOf(index.getAllKeys())) {
        if (key.equals(index.getString(old))) {
          index.remove(old);
        }
      }
      index.putString(address, key);
      data.put("Index", index);
      tag.put(DATA, data);
      root.setUserDataTag(tag);
    }
    return key;
  }

  public static SocketJointSettings get(ServerSubLevel body, String key) {
    body = SubLevelCreatureParts.root(body);
    if (key == null || body.getUserDataTag() == null) {
      return DEFAULT;
    }
    var value = body.getUserDataTag().getCompound(DATA).getCompound("Values").getCompound(key);
    return value.isEmpty()
        ? DEFAULT
        : new SocketJointSettings(value.getInt("Swivel"), value.getInt("Bend"));
  }

  public void save(ServerSubLevel body, String key) {
    if (key == null) {
      throw new IllegalArgumentException("Missing socket identity");
    }
    body = SubLevelCreatureParts.root(body);
    boolean changed = !equals(get(body, key));
    var tag = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    var data = tag.getCompound(DATA).copy();
    var values = data.getCompound("Values").copy();
    if (equals(DEFAULT)) {
      values.remove(key);
    } else {
      var value = new CompoundTag();
      value.putInt("Swivel", swivel);
      value.putInt("Bend", bend);
      values.put(key, value);
    }
    data.put("Values", values);
    tag.put(DATA, data);
    body.setUserDataTag(tag);
    if (changed) {
      org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.invalidateRoutes(body);
    }
  }

  public static <T> T duringMove(Supplier<T> action) {
    MOVING.set(MOVING.get() + 1);
    try {
      return action.get();
    } finally {
      MOVING.set(MOVING.get() - 1);
    }
  }

  public static void changed(ServerSubLevel physical, BlockPos cell, BlockState state) {
    if (MOVING.get() != 0) {
      return;
    }
    var body = SubLevelCreatureParts.root(physical);
    if (body.getUserDataTag() == null || !body.getUserDataTag().contains(DATA)) {
      return;
    }
    var tag = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    var data = tag.getCompound(DATA).copy();
    var values = data.getCompound("Values").copy();
    if (!CompactLegSocketBlock.isSocket(state) && physical == body) {
      for (int i = 0; i < 3; i++) {
        values.remove(compact(cell, i));
      }
    }
    if (!state.is(ComponentRegistry.LIMB_SOCKET.get())) {
      var index = data.getCompound("Index").copy();
      String address = physical.getUniqueId() + "@" + cell.asLong();
      values.remove(index.getString(address));
      index.remove(address);
      data.put("Index", index);
    }
    data.put("Values", values);
    tag.put(DATA, data);
    body.setUserDataTag(tag);
  }
}
