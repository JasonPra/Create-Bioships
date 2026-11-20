package org.Azkerash.create_bioships.mixin;

import com.github.elenterius.biomancy.block.membrane.BiometricMembraneBlock;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import org.spongepowered.asm.mixin.Mixin;

/** Native Sable voxel contact callback is selective for each biometric membrane cell. */
@Mixin(BiometricMembraneBlock.class)
public abstract class BiometricSubLevelCollisionMixin
    implements BlockWithSubLevelCollisionCallback {
  @Override
  public BlockSubLevelCollisionCallback sable$getCallback() {
    return org.Azkerash.create_bioships.compat.biomancy.ConstructMembranePassage.INSTANCE;
  }
}
