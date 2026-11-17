package org.Azkerash.create_bioships.creature.api;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Physical creature access; does not imply growth, movement or amoeba abilities. */
public interface CreatureBodyAccess {
  Level level();

  ServerSubLevel body();

  BlockPos controllerPosition();
}
