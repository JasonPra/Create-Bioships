package org.Azkerash.create_bioships.compat.letitwalk;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.Azkerash.create_bioships.core.CoreAssemblyRules;

/** Saved on the physical sublevel, shared by the Positioner and both limb controllers. */
public record LimbTuning(int restAngle, double stiffness, double damping) {
  public static final String DATA = "create_bioships:limb_tuning";
  public static final LimbTuning DEFAULT = new LimbTuning(0, 1, 1);

  public static LimbTuning get(ServerLevel level, BlockPos pos) {
    var body = CoreAssemblyRules.containing(level, pos);
    if (body == null || body.getUserDataTag() == null) return DEFAULT;
    var value = body.getUserDataTag().getCompound(DATA).getCompound(Long.toString(pos.asLong()));
    return new LimbTuning(
        Math.clamp(value.getInt("RestAngle"), -45, 45),
        value.contains("Stiffness") ? bounded(value.getDouble("Stiffness")) : 1,
        value.contains("Damping") ? bounded(value.getDouble("Damping")) : 1);
  }

  /** Delete the component entry, rather than saving defaults at a now-empty position. */
  public static void clear(ServerLevel level, BlockPos pos) {
    var body = CoreAssemblyRules.containing(level, pos);
    if (body != null) clear(body, pos);
  }

  private static void clear(dev.ryanhcode.sable.sublevel.ServerSubLevel body, BlockPos pos) {
    if (body.getUserDataTag() == null) return;
    var root = body.getUserDataTag().copy();
    var values = root.getCompound(DATA).copy();
    if (!values.contains(Long.toString(pos.asLong()))) return;
    values.remove(Long.toString(pos.asLong()));
    if (values.isEmpty()) root.remove(DATA);
    else root.put(DATA, values);
    body.setUserDataTag(root);
  }

  /** Height/rest-pose changes retain tuning; removal or replacement retires the old component. */
  public static void changed(
      dev.ryanhcode.sable.sublevel.ServerSubLevel body,
      BlockPos pos,
      net.minecraft.world.level.block.state.BlockState state) {
    if (body.getUserDataTag() == null) return;
    var value = body.getUserDataTag().getCompound(DATA).getCompound(Long.toString(pos.asLong()));
    if (value.isEmpty()) return;
    boolean limb =
        state.getBlock() instanceof org.Azkerash.create_bioships.components.logic.legs.CrabLegBlock
            || state.getBlock()
                instanceof org.Azkerash.create_bioships.components.logic.legs.ModularJointBlock;
    var id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    if (!limb || (value.contains("Block") && !value.getString("Block").equals(id)))
      clear(body, pos);
  }

  private static double bounded(double n) {
    return Double.isFinite(n) ? Math.clamp(n, .25, 2) : 1;
  }

  public void save(ServerLevel level, BlockPos pos) {
    var body = CoreAssemblyRules.containing(level, pos);
    if (body == null) return;
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    var values = root.getCompound(DATA).copy();
    var value = new CompoundTag();
    value.putString(
        "Block",
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(pos).getBlock())
            .toString());
    value.putInt("RestAngle", Math.clamp(restAngle, -45, 45));
    value.putDouble("Stiffness", bounded(stiffness));
    value.putDouble("Damping", bounded(damping));
    values.put(Long.toString(pos.asLong()), value);
    root.put(DATA, values);
    body.setUserDataTag(root);
  }
}
