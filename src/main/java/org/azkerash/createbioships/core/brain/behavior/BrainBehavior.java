package org.azkerash.createbioships.core.brain.behavior;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/** One runtime instance per creature. Cards identify programs; components never tick them. */
public interface BrainBehavior {
  void tick(ServerSubLevel body);

  default void stop(ServerSubLevel body) {
    org.azkerash.createbioships.core.brain.BrainMotorControls.stop(body);
    org.azkerash.createbioships.core.pathfinding.SubLevelPaths.clear(body);
  }
}
