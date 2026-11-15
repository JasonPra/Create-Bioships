package org.Azkerash.create_bioships.mixin;

import com.github.elenterius.biomancy.block.membrane.Membrane;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sable normally queries a cached shape without the entity that Biomancy needs for filtering. */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public class BiometricSubLevelCollisionMixin {
  @Inject(method = "getSubLevelEntityCollisionShape", at = @At("HEAD"), cancellable = true)
  private static void membraneCollision(
      Entity entity,
      Vector3dc center,
      Pose3dc pose,
      BlockState state,
      LevelAccelerator level,
      BlockPos pos,
      LevelReusedVectors vectors,
      CallbackInfoReturnable<VoxelShape> callback) {
    if (state.getBlock() instanceof Membrane membrane
        && membrane.shouldIgnoreEntityCollisionAt(state, level, pos, entity))
      callback.setReturnValue(Shapes.empty());
  }
}
