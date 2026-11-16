package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Coalesces structural edits at the server tick boundary, after physical handoffs finish. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipHealthChanges {
  private static final Set<ServerSubLevel> DIRTY =
      Collections.newSetFromMap(new IdentityHashMap<>());

  public static void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
    if (before == null
        || before.getBlock() == after.getBlock()
            && (!before.is(Create_bioships.INTESTINE.get())
                || before.getValue(IntestineBlock.ENCASED)
                    == after.getValue(IntestineBlock.ENCASED))) return;
    var ship = BioshipIntegration.containing(level, pos);
    if (ship == null || ship.isRemoved()) return;
    var tag = ship.getUserDataTag();
    if (tag != null && tag.hasUUID("BioshipCargoOwner")) {
      var owner =
          dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
              .getSubLevel(tag.getUUID("BioshipCargoOwner"));
      if (owner instanceof ServerSubLevel original) DIRTY.add(original);
    } else DIRTY.add(ship);
  }

  static void changed(LivingCoreBlockEntity core) {
    if (core.ship() != null) DIRTY.add(core.ship());
  }

  @SubscribeEvent
  public static void flush(ServerTickEvent.Post event) {
    var pending = List.copyOf(DIRTY);
    DIRTY.clear();
    for (var ship : pending) {
      if (ship.isRemoved() || ship.getLevel().getServer() != event.getServer()) continue;
      for (var actor : BioshipIntegration.blockEntities(ship))
        if (actor instanceof LivingCoreBlockEntity core && !core.body().dead())
          core.body().observe(core);
    }
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent event) {
    DIRTY.clear();
  }

  private BioshipHealthChanges() {}
}
