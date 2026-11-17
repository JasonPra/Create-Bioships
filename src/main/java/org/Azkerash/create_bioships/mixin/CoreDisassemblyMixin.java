package org.Azkerash.create_bioships.mixin;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.Azkerash.create_bioships.core.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No cancellable disassembly event is exposed by the pinned APIs. Guard their shared entry. */
@Mixin(SimAssemblyHelper.class)
public abstract class CoreDisassemblyMixin {
  @Inject(method = "disassembleSubLevel", at = @At("HEAD"), cancellable = true, remap = false)
  private static void core$preventDisassembly(
      Level level,
      SubLevel body,
      BlockPos anchor,
      BlockPos target,
      Rotation rotation,
      boolean sound,
      CallbackInfo callback) {
    if (org.Azkerash.create_bioships.core.CoreAssemblyRules.isCreature(body)) callback.cancel();
  }
}
