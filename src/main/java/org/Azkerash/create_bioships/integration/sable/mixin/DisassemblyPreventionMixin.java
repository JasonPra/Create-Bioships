package org.Azkerash.create_bioships.integration.sable.mixin;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.simulated_team.simulated.content.blocks.physics_assembler.assembly_preventer.DisassemblyPrevention;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.Azkerash.create_bioships.BioshipIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DisassemblyPrevention.class)
public class DisassemblyPreventionMixin {
  @Inject(method = "checkSubLevelForPrimary", at = @At("HEAD"), remap = false)
  private static void protect(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> callback)
      throws AssemblyException {
    if (level != null
        && pos != null
        && BioshipIntegration.bound(BioshipIntegration.containing(level, pos)))
      throw new AssemblyException(Component.translatable("message.create_bioships.no_disassembly"));
  }
}
