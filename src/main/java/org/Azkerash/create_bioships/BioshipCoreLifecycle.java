package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Validate source-core removal only after synchronous assembly and its chunk loads complete. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
final class BioshipCoreLifecycle {
  private record Removal(
      LivingCoreBlockEntity source, ServerSubLevel owner, ServerLevel level, BlockPos pos) {}

  // Server-thread work only. A server task can be polled inside a blocking chunk load; it is
  // therefore not a transaction boundary. ServerTickEvent.Post is the completion boundary.
  private static final Map<LivingCoreBlockEntity, Removal> pending = new IdentityHashMap<>();

  static void removed(LivingCoreBlockEntity core) {
    if (!(core.getLevel() instanceof ServerLevel level)) return;
    var owner = core.ship();
    if (owner != null)
      pending.putIfAbsent(core, new Removal(core, owner, level, core.getBlockPos().immutable()));
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    var batch =
        pending.values().stream().filter(r -> r.level().getServer() == event.getServer()).toList();
    batch.forEach(r -> pending.remove(r.source()));
    for (var r : batch) {
      if (r.source().transferred()) continue;
      boolean loaded =
          r.owner()
                  .getPlot()
                  .getChunkHolder(
                      r.owner().getPlot().toLocal(new net.minecraft.world.level.ChunkPos(r.pos())))
              != null;
      if (r.source().hasTransferReceipt()
          || loaded && !r.level().getBlockState(r.pos()).is(Create_bioships.CORE.get())) {
        r.source().body().die(r.source(), BioshipDeathCause.CORE_REMOVED);
        BioshipDecay.mark(r.owner(), BioshipDeathCause.CORE_REMOVED);
      }
    }
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent event) {
    pending.values().removeIf(r -> r.level().getServer() == event.getServer());
  }

  private BioshipCoreLifecycle() {}
}
