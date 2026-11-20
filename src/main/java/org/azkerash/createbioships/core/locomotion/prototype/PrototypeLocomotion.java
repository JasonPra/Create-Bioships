package org.azkerash.createbioships.core.locomotion.prototype;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.azkerash.createbioships.core.brain.BrainMotorControls;

/** Per-creature switch; existing worlds retain the native controller until explicitly opted in. */
public final class PrototypeLocomotion {
  public enum Mode {
    NATIVE,
    NATIVE_FAST,
    PROTOTYPE
  }

  private static final String KEY = "create_bioships:locomotion_controller";
  public static final double MAX_SPEED_BLOCKS_PER_SECOND = 6;
  public static final int COMPARISON_GEAR = 3, COMPARISON_RPM = 256;

  private PrototypeLocomotion() {}

  public static Mode get(ServerSubLevel body) {
    var tag = SubLevelCreatureParts.root(body).getUserDataTag();
    if (tag == null) {
      return Mode.NATIVE;
    }
    return switch (tag.getString(KEY)) {
      case "prototype" -> Mode.PROTOTYPE;
      case "native_fast" -> Mode.NATIVE_FAST;
      default -> Mode.NATIVE;
    };
  }

  public static void set(ServerSubLevel body, Mode mode) {
    body = SubLevelCreatureParts.root(body);
    var existing = body.getUserDataTag();
    var tag = existing == null ? new CompoundTag() : existing.copy();
    if (mode != Mode.NATIVE) {
      tag.putString(KEY, mode.name().toLowerCase(java.util.Locale.ROOT));
    } else {
      tag.remove(KEY);
    }
    body.setUserDataTag(tag);
    BrainMotorControls.stop(body);
  }
}
