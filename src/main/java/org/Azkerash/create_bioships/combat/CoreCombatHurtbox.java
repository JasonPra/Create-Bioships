package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Core-specific registration of the reusable sensory weak-point hurtbox. */
public final class CoreCombatHurtbox extends EyeCombatHurtbox {
  public CoreCombatHurtbox(EntityType<? extends CoreCombatHurtbox> type, Level level) {
    super(type, level);
  }

  public void bindCore(ServerSubLevel body, BlockPos core) {
    bindEye(body, core);
  }

  @Override
  protected boolean accepts(BlockState state) {
    return state.is(ComponentRegistry.CORE.get());
  }

  @Override
  protected Direction facing(ServerSubLevel body, BlockState state) {
    var center = body.logicalPose().transformPosition(Vec3.atCenterOf(this.getImpactCell()));
    var threat =
        level()
            .getEntitiesOfClass(
                Mob.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(48),
                HostileCombatIntegration::isHostile)
            .stream()
            .min(java.util.Comparator.comparingDouble(m -> m.position().distanceToSqr(center)))
            .orElse(null);
    if (threat == null) {
      return this.getImpactFace();
    }
    var local =
        body.logicalPose()
            .transformPositionInverse(threat.getEyePosition())
            .subtract(Vec3.atCenterOf(this.getImpactCell()));
    return Direction.getNearest(local.x, local.y, local.z);
  }
}
