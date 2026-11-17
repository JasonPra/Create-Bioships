package org.Azkerash.create_bioships.integration.sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.api.CreatureBodyAccess;
/** Coordinate conversion used by active creatures, independent of reconstruction prototypes. */
public final class BodyCoordinates {
  private BodyCoordinates() {}
  public static Vec3 physical(CreatureBodyAccess body, BlockPos relative) {
    return body.body().logicalPose().transformPosition(Vec3.atCenterOf(body.controllerPosition().offset(relative)));
  }
}
