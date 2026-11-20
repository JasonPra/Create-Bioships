package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Block;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.eye.EyeWoundBehavior;
import org.Azkerash.create_bioships.core.SurfaceWoundState;

/** Wound state belongs to the grid and is replayed to new observers without resetting its age. */
public final class SubLevelWounds {
  private static final String DATA = "create_bioships:wounds";
  private static final Map<ServerSubLevel, SurfaceWoundState> STATES = new WeakHashMap<>();

  private SubLevelWounds() {}

  public static SurfaceWoundState state(ServerSubLevel body) {
    return STATES.computeIfAbsent(
        body,
        b ->
            SurfaceWoundState.load(
                b.getUserDataTag() == null
                    ? new net.minecraft.nbt.ListTag()
                    : b.getUserDataTag().getList(DATA, 10)));
  }

  public static void healEyes(ServerSubLevel body) {
    var wounds = state(body);
    var cleared =
        wounds.removeIf(
            surface -> {
              var block = body.getLevel().getBlockState(surface.cell());
              return block.is(ComponentRegistry.CORE.get()) || EyeWoundBehavior.isEye(block);
            });
    if (cleared.isEmpty()) {
      return;
    }
    var now = body.getLevel().getGameTime();
    var root = body.getUserDataTag().copy();
    root.put(DATA, wounds.save(now));
    body.setUserDataTag(root);
    for (var wound : cleared) {
      net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(
          body.getLevel(),
          new WoundPayload(
              body.getUniqueId(), wound.surface().cell(), wound.surface().face(), now, 0));
    }
  }

  public static void hit(ServerSubLevel body, BlockPos cell, Direction face) {
    var impact = SubLevelImpactAdapter.get(body);
    if (impact != null) {
      impact.wound(cell, face);
    }
  }

  static void record(ServerSubLevel body, BlockPos cell, Direction face) {
    var block = body.getLevel().getBlockState(cell);
    if (block.is(ComponentRegistry.CORE.get()) || EyeWoundBehavior.isEye(block)) {
      var health = org.Azkerash.create_bioships.core.SubLevelHealth.status(body);
      if (!health.dead() && health.maximum() > 0 && health.current() == health.maximum()) {
        return;
      }
    }
    var now = body.getLevel().getGameTime();
    var wounds = state(body);
    wounds.hit(cell, face, now, EyeWoundBehavior.WOUND_TICKS);
    if (EyeWoundBehavior.isEye(block)) {
      body.getLevel()
          .setBlock(
              cell,
              org.Azkerash.create_bioships.components.logic.eye.EyeBlink.sleeping(block),
              Block.UPDATE_CLIENTS);
      body.getLevel().scheduleTick(cell, block.getBlock(), 1);
    }
    var root =
        body.getUserDataTag() == null
            ? new net.minecraft.nbt.CompoundTag()
            : body.getUserDataTag().copy();
    root.put(DATA, wounds.save(now));
    body.setUserDataTag(root);
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(
        body.getLevel(),
        new WoundPayload(body.getUniqueId(), cell, face, now, EyeWoundBehavior.WOUND_TICKS));
  }
}
