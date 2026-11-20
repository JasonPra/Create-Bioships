package org.azkerash.createbioships.mixin;

import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.azkerash.createbioships.core.SubLevelHealth;
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
    org.azkerash.createbioships.core.naming.SubLevelNames.changed(body, pos, state);
    org.azkerash.createbioships.core.flippers.SubLevelFlippers.changed(body, pos, state);
    org.azkerash.createbioships.compat.letitwalk.SocketJointSettings.changed(body, pos, state);
    org.azkerash.createbioships.compat.letitwalk.LimbTuning.changed(body, pos, state);
    org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.changed(body, pos, state);
    org.azkerash.createbioships.compat.letitwalk.LimbGroundContacts.invalidate(body);
    org.azkerash.createbioships.core.legs.SubLevelLegs.changed(body, pos, state);
    org.azkerash.createbioships.compat.letitwalk.ModularLimbController.changed(body, pos, state);
    org.azkerash.createbioships.core.SubLevelArmor.changed(body, pos, state);
    org.azkerash.createbioships.core.brain.SubLevelBrains.changed(body, pos, state);
    org.azkerash.createbioships.core.vision.SubLevelVision.changed(body, pos, state);
    org.azkerash.createbioships.core.digestion.SubLevelDigestion.changed(body, pos, state);
    org.azkerash.createbioships.core.component.SubLevelComponentStore.changed(body, pos, state);
    org.azkerash.createbioships.components.logic.eye.EyeBlock.supportChanged(body, pos);
    org.azkerash.createbioships.core.CreatureDecay.changed(body, pos, state);
  }
}
