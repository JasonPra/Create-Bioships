package org.Azkerash.create_bioships.mixin;

import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.core.SubLevelHealth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sable exposes this native notification but no public block-change event. */
@Mixin(ServerLevelPlot.class)
public abstract class SubLevelHealthMixin {
  @Inject(method = "onBlockChange", at = @At("TAIL"), remap = false)
  private void core$blockChanged(BlockPos pos, BlockState state, CallbackInfo callback) {
    var body = ((ServerLevelPlot) (Object) this).getSubLevel();
    SubLevelHealth.blockChanged(body, pos, state);
    org.Azkerash.create_bioships.core.legs.SubLevelLegs.changed(body, pos, state);
    org.Azkerash.create_bioships.core.SubLevelArmor.changed(body, pos, state);
    org.Azkerash.create_bioships.core.brain.SubLevelBrains.changed(body, pos, state);
    org.Azkerash.create_bioships.core.vision.SubLevelVision.changed(body, pos, state);
    org.Azkerash.create_bioships.core.digestion.SubLevelDigestion.changed(body, pos, state);
    org.Azkerash.create_bioships.core.component.SubLevelComponentStore.changed(body, pos, state);
    org.Azkerash.create_bioships.components.logic.eye.EyeBlock.supportChanged(body, pos);
    org.Azkerash.create_bioships.core.CreatureDecay.changed(body, pos, state);
  }
}
