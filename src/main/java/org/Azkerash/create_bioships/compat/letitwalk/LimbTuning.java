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

  private static double bounded(double n) {
    return Double.isFinite(n) ? Math.clamp(n, .25, 2) : 1;
  }

  public void save(ServerLevel level, BlockPos pos) {
    var body = CoreAssemblyRules.containing(level, pos);
    if (body == null) return;
    var root = body.getUserDataTag().copy();
    var values = root.getCompound(DATA).copy();
    var value = new CompoundTag();
    value.putInt("RestAngle", Math.clamp(restAngle, -45, 45));
    value.putDouble("Stiffness", bounded(stiffness));
    value.putDouble("Damping", bounded(damping));
    values.put(Long.toString(pos.asLong()), value);
    root.put(DATA, values);
    body.setUserDataTag(root);
  }
}
