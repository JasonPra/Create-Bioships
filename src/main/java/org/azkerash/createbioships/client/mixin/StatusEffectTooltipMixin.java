package org.azkerash.createbioships.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.azkerash.createbioships.client.core.StatusEffectIcons;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replays insertion-marked icons in the tooltip text transform, preserving Create tooltip scaling.
 */
@Mixin(ClientTextTooltip.class)
public abstract class StatusEffectTooltipMixin {
  @Shadow @Final private FormattedCharSequence text;

  @Inject(method = "renderText", at = @At("RETURN"))
  private void core$readoutIcons(
      Font font,
      int x,
      int y,
      Matrix4f matrix,
      MultiBufferSource.BufferSource buffers,
      CallbackInfo callback) {
    StatusEffectIcons.render(text, font, x, y, matrix, buffers);
  }
}
