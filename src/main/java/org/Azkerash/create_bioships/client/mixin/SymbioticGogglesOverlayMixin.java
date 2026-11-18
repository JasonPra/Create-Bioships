package org.Azkerash.create_bioships.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.Azkerash.create_bioships.equipment.SymbioticGogglesAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public abstract class SymbioticGogglesOverlayMixin {
  @WrapOperation(
      method = "renderOverlay",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
  private static BlockEntity core$restrictInspection(
      ClientLevel level, BlockPos pos, Operation<BlockEntity> original) {
    var display = original.call(level, pos);
    return SymbioticGogglesAccess.canInspect(Minecraft.getInstance().player, display)
        ? display
        : null;
  }
}
