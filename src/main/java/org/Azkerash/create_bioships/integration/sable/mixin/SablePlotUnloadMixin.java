package org.Azkerash.create_bioships.integration.sable.mixin;

import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sable serializes plot storage itself; its holders deliberately never become vanilla-save-ready.
 */
@Mixin(ChunkMap.class)
abstract class SablePlotUnloadMixin {
  @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

  @Inject(method = "scheduleUnload", at = @At("HEAD"), cancellable = true)
  private void bioships$keepPlotsWithSable(long pos, ChunkHolder holder, CallbackInfo callback) {
    // Vanilla has already removed this holder from its updating map. Sable owns plot serialization;
    // do not put its deliberately unready holder into an infinitely retrying vanilla save callback.
    if (holder instanceof PlotChunkHolder) {
      pendingUnloads.remove(pos, holder);
      callback.cancel();
    }
  }
}
