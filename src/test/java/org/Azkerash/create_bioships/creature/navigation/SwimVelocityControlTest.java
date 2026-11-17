package org.Azkerash.create_bioships.creature.navigation;

import static org.junit.jupiter.api.Assertions.*;

import org.Azkerash.create_bioships.creature.navigation.SwimVelocityControl.Vector;
import org.junit.jupiter.api.Test;

class SwimVelocityControlTest {
  @Test
  void holdsDepthAndCanDiveAgainstBuoyancy() {
    var control = new SwimVelocityControl();
    double vertical = 0;
    for (int tick = 0; tick < 400; tick++) {
      var force = control.acceleration(new Vector(0, 0, 0), new Vector(0, vertical, 0), .05);
      vertical += (force.y() + 9.8) * .05;
    }
    assertEquals(0, vertical, .01);
    for (int tick = 0; tick < 400; tick++) {
      var force = control.acceleration(new Vector(0, -1.5, 0), new Vector(0, vertical, 0), .05);
      vertical += (force.y() + 9.8) * .05;
    }
    assertEquals(-1.5, vertical, .01);
  }

  @Test
  void rejectsInvalidTimestepsAndBoundsAcceleration() {
    var control = new SwimVelocityControl();
    assertEquals(
        new Vector(0, 0, 0),
        control.acceleration(new Vector(1, 1, 1), new Vector(0, 0, 0), Double.NaN));
    var force = control.acceleration(new Vector(1000, -1000, 1000), new Vector(0, 0, 0), .05);
    assertTrue(Math.abs(force.x()) <= 36 && Math.abs(force.y()) <= 36 && Math.abs(force.z()) <= 36);
  }
}
