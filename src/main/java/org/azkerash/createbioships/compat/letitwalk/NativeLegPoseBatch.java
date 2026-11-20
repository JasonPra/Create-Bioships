package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.BuiltLegDriver;
import dev.letitwalk.walker.WalkerCargo;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Map;
import java.util.UUID;

/** Computes native targets before the compact adapter applies each physical bone's rest frame. */
public final class NativeLegPoseBatch {
  private static final ThreadLocal<Boolean> DEFER_BODY_WRITES = new ThreadLocal<>();

  private NativeLegPoseBatch() {}

  public static boolean defersBodyWrites() {
    return Boolean.TRUE.equals(DEFER_BODY_WRITES.get());
  }

  public static Map<UUID, WalkerCargo.Pose> calculate(
      WalkerCoreBlockEntity walker, ServerSubLevel body, double dt) {
    var previous = DEFER_BODY_WRITES.get();
    DEFER_BODY_WRITES.set(true);
    try {
      // Native virtual joint axes can share a physical body. Keep their target/history
      // calculations, but let the compact adapter perform the final corrected write once.
      return BuiltLegDriver.substep(walker, body, dt);
    } finally {
      if (previous == null) {
        DEFER_BODY_WRITES.remove();
      } else {
        DEFER_BODY_WRITES.set(previous);
      }
    }
  }
}
