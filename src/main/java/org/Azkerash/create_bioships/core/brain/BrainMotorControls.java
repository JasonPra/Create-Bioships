package org.Azkerash.create_bioships.core.brain;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.*;

/** Brain control surface. Behavior cards can request motion without knowing joints or gait. */
public final class BrainMotorControls {
  public record Request(Vec3 velocity, double yawRate, long expires) {}

  private static final Map<ServerSubLevel, Request> REQUESTS = new WeakHashMap<>();

  private BrainMotorControls() {}

  public static boolean move(ServerSubLevel body, Vec3 velocity, double yawRate, int leaseTicks) {
    if (!active(body)
        || !Double.isFinite(velocity.x)
        || !Double.isFinite(velocity.y)
        || !Double.isFinite(velocity.z)
        || !Double.isFinite(yawRate)) return false;
    var horizontal = new Vec3(velocity.x, 0, velocity.z);
    if (horizontal.lengthSqr() > 4) horizontal = horizontal.normalize().scale(2);
    REQUESTS.put(
        body,
        new Request(
            horizontal,
            Math.clamp(yawRate, -1, 1),
            body.getLevel().getGameTime() + Math.clamp(leaseTicks, 1, 200)));
    return true;
  }

  public static boolean active(ServerSubLevel body) {
    return !body.isRemoved()
        && SubLevelData.isLiving(body)
        && !SubLevelHealth.status(body).dead()
        && SubLevelBrains.get(body).capacity() > 0;
  }

  public static Request current(ServerSubLevel body) {
    var request = REQUESTS.get(body);
    if (!active(body) || request == null || body.getLevel().getGameTime() >= request.expires) {
      REQUESTS.remove(body);
      return new Request(Vec3.ZERO, 0, 0);
    }
    return request;
  }

  public static void stop(ServerSubLevel body) {
    REQUESTS.remove(body);
  }
}
