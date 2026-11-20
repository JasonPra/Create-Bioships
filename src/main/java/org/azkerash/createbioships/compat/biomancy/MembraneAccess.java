package org.azkerash.createbioships.compat.biomancy;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.access.SubLevelPermissions;

/** Native Biomancy collision hook; world membranes retain their original filters. */
public final class MembraneAccess {
  private MembraneAccess() {}

  public static Boolean permits(BlockGetter getter, BlockPos pos, Entity entity) {
    Level level = getter instanceof Level found ? found : entity.level();
    if (level instanceof net.minecraft.server.level.ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, pos);
      return body != null && SubLevelData.isLiving(body)
          ? (SubLevelPermissions.get(body).permits(entity.getUUID())
              || ConstructMembranePassage.passenger(body, entity))
          : null;
    }
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) {
      return null;
    }
    var plot = container.getPlot(new ChunkPos(pos));
    if (plot == null || plot.getSubLevel() == null) {
      return null;
    }
    return org.azkerash.createbioships.client.components.ClientMembraneAccess.permits(
        plot.getSubLevel().getUniqueId(), entity);
  }
}
