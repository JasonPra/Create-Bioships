package org.Azkerash.create_bioships.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.*;

/** Client-only cargo deformation; logical poses, collisions and inventories stay authoritative. */
public final class ComponentMergeRendering {
  private record Active(OrganMotion.Move move, boolean swallowed) {}

  private static final Map<UUID, Active> ACTIVE = new HashMap<>();
  private static WeakReference<ClientLevel> level = new WeakReference<>(null);
  private static long tick = Long.MIN_VALUE;

  private ComponentMergeRendering() {}

  public static Pose3dc pose(ClientSubLevel cargo, Pose3dc original, float partial) {
    var current = cargo.getLevel();
    if (current != level.get() || tick != current.getGameTime()) {
      level = new WeakReference<>(current);
      tick = current.getGameTime();
      ACTIVE.clear();
      var container = SubLevelContainer.getContainer(current);
      if (container != null)
        for (var ship : container.getAllSubLevels())
          for (var actor : ship.getPlot().getBlockEntityActors())
            if (actor instanceof LivingCoreBlockEntity core && !core.body().dead()) {
              var moving = core.organMove();
              if (moving != null) ACTIVE.put(moving.cargo(), new Active(moving, false));
              for (var move : core.assimilation().arms())
                ACTIVE.put(move.cargo(), new Active(move, true));
            }
    }
    var active = ACTIVE.get(cargo.getUniqueId());
    if (active == null) return original;
    double progress = (current.getGameTime() + partial - active.move().started()) / 36.0;
    double scale = ComponentMergeAnimation.scale(progress, active.swallowed());
    if (scale >= 1) return original;
    var pivot = Vec3.atCenterOf(active.move().cargoBlock());
    var before = original.transformPosition(pivot);
    var result = new Pose3d(original);
    result.scale().mul(scale);
    var after = result.transformPosition(pivot);
    result.position().add(before.x - after.x, before.y - after.y, before.z - after.z);
    return result;
  }
}
