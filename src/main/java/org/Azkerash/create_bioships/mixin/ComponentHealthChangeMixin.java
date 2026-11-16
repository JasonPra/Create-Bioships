package org.Azkerash.create_bioships.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.Azkerash.create_bioships.BioshipHealthChanges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class ComponentHealthChangeMixin {
  @Inject(method = "setBlockState", at = @At("RETURN"))
  private void bioships$structuralChange(
      BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> callback) {
    var chunk = (LevelChunk) (Object) this;
    if (chunk.getLevel() instanceof ServerLevel level)
      BioshipHealthChanges.changed(level, pos, callback.getReturnValue(), state);
  }
}
