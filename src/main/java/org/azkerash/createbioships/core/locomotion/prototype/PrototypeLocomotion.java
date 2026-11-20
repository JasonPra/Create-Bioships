package org.azkerash.createbioships.core.locomotion.prototype;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.azkerash.createbioships.core.brain.BrainMotorControls;

/** Per-creature switch; existing worlds retain the native controller until explicitly opted in. */
public final class PrototypeLocomotion {
  public enum Mode {
    NATIVE,
    PROTOTYPE
  }

  private static final String KEY = "create_bioships:locomotion_controller";
  public static final double MAX_SPEED_BLOCKS_PER_SECOND = 6;

  private PrototypeLocomotion() {}

  public static Mode get(ServerSubLevel body) {
    var tag = SubLevelCreatureParts.root(body).getUserDataTag();
    return tag != null && "prototype".equals(tag.getString(KEY)) ? Mode.PROTOTYPE : Mode.NATIVE;
  }

  public static void set(ServerSubLevel body, Mode mode) {
    body = SubLevelCreatureParts.root(body);
    var existing = body.getUserDataTag();
    var tag = existing == null ? new CompoundTag() : existing.copy();
    if (mode == Mode.PROTOTYPE) {
      tag.putString(KEY, "prototype");
    } else {
      tag.remove(KEY);
    }
    body.setUserDataTag(tag);
    BrainMotorControls.stop(body);
  }
}
