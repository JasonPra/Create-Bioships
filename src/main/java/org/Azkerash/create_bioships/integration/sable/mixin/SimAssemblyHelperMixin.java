package org.Azkerash.create_bioships.integration.sable.mixin;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.Azkerash.create_bioships.BioshipIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimAssemblyHelper.class)
public class SimAssemblyHelperMixin {
    @Inject(method = "disassembleSubLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void protect(Level level, SubLevel ship, BlockPos anchor, BlockPos goal, Rotation rotation, boolean sound, CallbackInfo callback) {
        if (BioshipIntegration.bound(ship)) callback.cancel();
    }
}
