package org.Azkerash.create_bioships.mixin;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sable normally requests a context-free shape; biometric membranes need the moving entity. */
@Mixin(SubLevelEntityCollision.class)
public abstract class SableMembraneCollisionMixin {
  @Inject(
      method = "getSubLevelEntityCollisionShape",
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private static void creature$biometricShape(
      Entity entity,
      org.joml.Vector3dc center,
      Pose3dc pose,
      BlockState state,
      LevelAccelerator level,
      BlockPos pos,
      LevelReusedVectors vectors,
      CallbackInfoReturnable<VoxelShape> callback) {
    if (state.getBlock()
        instanceof com.github.elenterius.biomancy.block.membrane.BiometricMembraneBlock) {
      callback.setReturnValue(state.getCollisionShape(level, pos, CollisionContext.of(entity)));
    }
  }
}
