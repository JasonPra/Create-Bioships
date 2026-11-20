package org.azkerash.createbioships.components.logic.mouth;

/**
 * Left/right jaw roots and distal fangs have separate hinges, in degrees about their local pivots.
 */
public final class MandibleGeometry {
  public static final double HINGE_Y = 6d / 16, HINGE_Z = .5 / 16;

  private MandibleGeometry() {}

  public static double opening(int chew, boolean grabbing, boolean spitting, double tick) {
    if (grabbing) {
      return .04;
    }
    if (spitting) {
      return 1;
    }
    return switch (chew) {
      case 1 -> .95;
      case 2 -> .45;
      case 3 -> .08;
      default -> .25 + Math.sin(tick * .12) * .04;
    };
  }

  public static double jawYaw(int side, double opening) {
    return side * (-18 + 50 * Math.clamp(opening, 0, 1));
  }

  public static double fangPitch(double opening) {
    return 18 + 32 * (1 - Math.clamp(opening, 0, 1));
  }
}
