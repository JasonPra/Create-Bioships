package org.Azkerash.create_bioships.network;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.BioshipBrain;
import org.Azkerash.create_bioships.BioshipIntegration;
import org.Azkerash.create_bioships.DigestiveSystem;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.TargetMemory;
import org.Azkerash.create_bioships.disabled.wip.AirshipPrototype;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

/**
 * Read-only observations of the selected behavior and actual route; never asks the brain to replan.
 */
public final class NavigationDebug {
  public static final String DATA = "BioshipPathDebug";

  public static CompoundTag inspect(LivingCoreBlockEntity core) {
    var level = core.getLevel();
    var ship = core.ship();
    if (level == null || ship == null || core.body().dead()) return new CompoundTag();
    var data =
        core.aquatic().swimming()
            ? core.aquatic().navigationDebug(level.getGameTime())
            : core.crawl().navigationDebug(level.getGameTime());
    data.putUUID("Ship", ship.getUniqueId());
    data.putString("Action", core.brain().action().name());
    data.putString("Reason", core.brain().reason());
    data.putBoolean("Flying", core.canFly());
    data.put("Priorities", core.brain().priorities());
    data.put("BodyWork", core.adaptation().behavior().debug());
    data.putInt("OfferCount", core.brain().offerCount());
    data.putInt("QueuedGoals", core.brain().queuedGoals().size());
    data.putInt("StoredComponents", core.assimilation().stored());
    data.putInt("CollectingArms", core.assimilation().arms().size());
    var missing = core.brain().materialNeeds(core);
    data.putInt("NeedFlesh", missing.flesh());
    data.putInt("NeedFiber", missing.fiber());
    data.putInt("NeedBone", missing.bone());
    data.putInt("DigestiveDemand", DigestiveSystem.requiredPairs(core));
    if (AirshipPrototype.available()) data.putInt("EngineCapacity", core.engineRoom().capacity());
    var origin = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    data.put(
        "Hologram",
        VisionDebug.vector(new Vec3(origin.x, ship.boundingBox().maxY() + .8, origin.z)));
    data.put(
        "Position",
        VisionDebug.vector(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)));
    var decision = core.brain().lastDecision();
    if (decision != null) {
      Vec3 target =
          decision.point() != null
              ? decision.point()
              : decision.item() != null ? decision.item().position() : null;
      if (target != null) data.put("Target", VisionDebug.vector(target));
      String label =
          decision.item() != null
              ? decision.item().getItem().getHoverName().getString()
              : decision.entity() != null ? decision.entity().getName().getString() : "location";
      if (decision.block() != null) {
        var remembered = core.vision().rememberedState(decision.block());
        if (decision.action() == BioshipBrain.Action.ASSIMILATE && remembered != null) {
          // Report the observation the behavior is pursuing, not unseen world changes.
          label = remembered.getBlock().getName().getString();
          data.putLong(
              "TargetMemoryMillis",
              Math.max(
                  0,
                  (TargetMemory.TIMEOUT - core.vision().unseenNanos(decision.block()))
                      / 1_000_000L));
          data.putLong("TargetSeenAge", core.vision().unseenNanos(decision.block()) / 50_000_000L);
        } else if (level
            .getChunkSource()
            .hasChunk(decision.block().getX() >> 4, decision.block().getZ() >> 4))
          label = level.getBlockState(decision.block()).getBlock().getName().getString();
      }
      data.putString("TargetLabel", label);
    }
    var route = new ListTag();
    var points = core.aquatic().swimming() ? core.aquatic().path() : core.crawl().path();
    for (var point : points.stream().limit(256).toList()) {
      var entry = new CompoundTag();
      entry.put("Point", VisionDebug.vector(point));
      route.add(entry);
    }
    data.put("Route", route);
    data.putInt("Waypoints", points.size());
    return data;
  }

  public static CompoundTag request(ServerPlayer player) {
    var saved = player.getPersistentData();
    long now = player.serverLevel().getGameTime();
    if (saved.contains("PathRequested") && now - saved.getLong("PathRequested") < 5) return null;
    saved.putLong("PathRequested", now);
    var container = SubLevelContainer.getContainer(player.serverLevel());
    LivingCoreBlockEntity selected = null;
    double best = 64 * 64;
    if (container != null)
      for (var body : container.getAllSubLevels())
        if (body instanceof ServerSubLevel ship && !ship.isRemoved())
          for (var entity : BioshipIntegration.blockEntities(ship))
            if (entity instanceof LivingCoreBlockEntity core && !core.body().dead()) {
              double distance =
                  BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)
                      .distanceToSqr(player.position());
              if (distance < best) {
                selected = core;
                best = distance;
              }
            }
    return selected == null ? new CompoundTag() : inspect(selected);
  }

  private NavigationDebug() {}
}
