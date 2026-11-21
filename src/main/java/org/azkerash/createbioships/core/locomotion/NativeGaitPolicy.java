package org.azkerash.createbioships.core.locomotion;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Requested speed selects a native gait; turning, strafing and weak support retain a walk. */
public final class NativeGaitPolicy {
  public record Gait(int gear, int rpm, boolean galloping, boolean strafing) {}

  public static final Gait WALK = new Gait(1, 32, false, false);
  private static final double RUN_SPEED_BLOCKS_PER_SECOND = 1.25;
  private static final double FAST_SPEED_BLOCKS_PER_SECOND = 2.5;
  private static final double MAX_RUN_YAW_RADIANS_PER_SECOND = .15;
  private static final double STRAFE_FRACTION = .15;
  private static final double MIN_RUN_SUPPORT_FRACTION = .5;

  private NativeGaitPolicy() {}

  /** Canonical gait coordinates use -Z forward, independent of the selected anatomical front. */
  public static Vec3 relative(Vec3 localVelocity, Direction front) {
    double forward = localVelocity.x * front.getStepX() + localVelocity.z * front.getStepZ();
    double right = -localVelocity.x * front.getStepZ() + localVelocity.z * front.getStepX();
    return new Vec3(right, localVelocity.y, -forward);
  }

  public static Gait select(Vec3 localVelocity, double yawRate, int contacts, int legs) {
    double speed = localVelocity.horizontalDistance();
    boolean strafe = Math.abs(localVelocity.x) > Math.max(.05, speed * STRAFE_FRACTION);
    boolean stable = legs >= 2 && contacts >= Math.ceil(legs * MIN_RUN_SUPPORT_FRACTION);
    boolean run =
        stable
            && !strafe
            && localVelocity.z < 0
            && Math.abs(yawRate) <= MAX_RUN_YAW_RADIANS_PER_SECOND
            && speed >= RUN_SPEED_BLOCKS_PER_SECOND;
    if (!run) {
      return new Gait(1, 32, false, strafe);
    }
    return speed >= FAST_SPEED_BLOCKS_PER_SECOND
        ? new Gait(3, 256, true, false)
        : new Gait(2, 128, true, false);
  }
}
