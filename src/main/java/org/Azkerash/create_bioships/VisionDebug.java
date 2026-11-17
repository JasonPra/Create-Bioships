package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** On-demand server observations, shared by the overlay and in-engine regression tests. */
public final class VisionDebug {
  public static final String DATA = "BioshipSightDebug";

  public static CompoundTag inspect(LivingCoreBlockEntity core, Vec3 target) {
    var result = new CompoundTag();
    result.put("Target", vector(target));
    var route = new ListTag();
    route.add(vector(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO)));
    for (var point : core.crawl().path()) route.add(vector(point));
    result.put("Route", route);
    result.putBoolean("Walking", core.crawl().walking());
    result.putString("Behavior", core.brain().action().name());
    result.putString("BehaviorReason", core.brain().reason());
    result.putInt("SearchRange", core.vision().maximumDistance(core));
    result.putDouble("Distance", BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO).distanceTo(target));
    result.putString("Core", core.getBlockPos().toShortString());
    result.putBoolean("Scouting", core.coreMotion().active());
    result.putString("ScoutStatus", core.coreMotion().scoutingHint(core));
    var views = new ListTag();
    for (var view : core.vision().views(core)) {
      var entry = new CompoundTag();
      entry.put("Origin", vector(view.origin()));
      entry.put("Forward", vector(view.forward()));
      entry.putDouble("Range", view.range());
      String reason = reason(core, view, target);
      entry.putString("Reason", reason);
      views.add(entry);
    }
    result.put("Views", views);
    return result;
  }

  static String reason(LivingCoreBlockEntity core, BioshipVision.View view, Vec3 target) {
    if (view.origin().distanceToSqr(target) > view.range() * view.range()) return "range";
    if (!view.contains(target)) return "cone";
    var pos = BlockPos.containing(target);
    var level = (net.minecraft.server.level.ServerLevel) core.getLevel();
    if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return "unloaded";
    if (!core.vision().inView(core, view, target)) return "dark";
    if (!BioshipVision.ray(core, view.origin(), target)) return "blocked";
    if (!BioshipVision.openToNature(level, pos)) return "roof";
    return "visible";
  }

  public static CompoundTag vector(Vec3 point) {
    var tag = new CompoundTag();
    tag.putDouble("X", point.x);
    tag.putDouble("Y", point.y);
    tag.putDouble("Z", point.z);
    return tag;
  }

  public static Vec3 vector(CompoundTag tag) {
    return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
  }

  static CompoundTag request(ServerPlayer player) {
    var data = player.getPersistentData();
    long now = player.serverLevel().getGameTime();
    if (data.contains("SightRequest") && now - data.getLong("SightRequest") < 10) return null;
    data.putLong("SightRequest", now);
    if (!player.getItemBySlot(EquipmentSlot.HEAD).is(Create_bioships.BIOSHIP_GOGGLES.get()))
      return new CompoundTag();
    var container = SubLevelContainer.getContainer(player.level());
    LivingCoreBlockEntity nearest = null;
    double best = 64 * 64;
    if (container != null)
      for (var body : container.getAllSubLevels())
        if (body instanceof ServerSubLevel ship && !ship.isRemoved())
          for (var entity : BioshipIntegration.blockEntities(ship))
            if (entity instanceof LivingCoreBlockEntity core && !core.body().dead()) {
              double distance =
                  BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO).distanceToSqr(player.position());
              if (distance < best) {
                nearest = core;
                best = distance;
              }
            }
    if (nearest == null) return new CompoundTag();
    var hit = player.pick(64, 0, false);
    Vec3 target =
        hit instanceof BlockHitResult block
                && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
            ? Vec3.atCenterOf(block.getBlockPos())
            : hit.getLocation();
    // Sable picks may return logical coordinates. The sight tests always use world coordinates.
    if (container.inBounds(BlockPos.containing(target))) {
      var body = BioshipIntegration.containing(player.level(), BlockPos.containing(target));
      if (body != null) target = body.logicalPose().transformPosition(target);
    }
    return inspect(nearest, target);
  }
}
