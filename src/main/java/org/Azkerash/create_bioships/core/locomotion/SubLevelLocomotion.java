package org.Azkerash.create_bioships.core.locomotion;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.core.*;

/** One appendage census on load; position deltas update the saved grid capabilities. */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelLocomotion {
  private static final TagKey<Block> LEGS = tag("land_legs"),
      FINS = tag("fins"),
      CLIMBERS = tag("wall_climbers");
  private static final Map<ServerSubLevel, SubLevelLocomotion> CENSUSES = new IdentityHashMap<>();
  private static final Map<
          ResourceLocation,
          java.util.function.Function<ServerSubLevel, net.minecraft.world.phys.AABB>>
      BOUNDS = new LinkedHashMap<>();

  private static final Map<ResourceLocation, java.util.function.Function<ServerSubLevel, Double>>
      STEP_HEIGHTS = new LinkedHashMap<>();

  public static long motionRevision(ServerSubLevel body) {
    var root = SubLevelCreatureParts.root(body).getUserDataTag();
    return root == null ? 0 : root.getLong("create_bioships:motion_revision");
  }

  public static void invalidateRoutes(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    var tag =
        body.getUserDataTag() == null
            ? new net.minecraft.nbt.CompoundTag()
            : body.getUserDataTag().copy();
    tag.putLong("create_bioships:motion_revision", motionRevision(body) + 1);
    body.setUserDataTag(tag);
  }

  public static void registerStepHeight(
      ResourceLocation type, java.util.function.Function<ServerSubLevel, Double> provider) {
    if (STEP_HEIGHTS.putIfAbsent(type, provider) != null)
      throw new IllegalArgumentException("Duplicate step-height provider: " + type);
  }

  public static double stepHeight(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    double height = 1;
    for (var provider : STEP_HEIGHTS.values()) {
      var limit = provider.apply(body);
      if (limit != null) height = Math.min(height, Math.max(0, limit));
    }
    return height;
  }

  public static void registerBounds(
      ResourceLocation type,
      java.util.function.Function<ServerSubLevel, net.minecraft.world.phys.AABB> provider) {
    if (BOUNDS.putIfAbsent(type, provider) != null)
      throw new IllegalArgumentException("Duplicate appendage bounds: " + type);
  }

  public static net.minecraft.world.phys.AABB bounds(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    var b = body.boundingBox();
    var result =
        new net.minecraft.world.phys.AABB(
            b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    for (var member : SubLevelCreatureParts.members(body)) {
      var box = member.boundingBox();
      result =
          result.minmax(
              new net.minecraft.world.phys.AABB(
                  box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
      for (var provider : BOUNDS.values()) {
        var extra = provider.apply(member);
        if (extra != null) result = result.minmax(extra);
      }
    }
    return result;
  }

  private final ServerSubLevel body;
  private final Map<BlockPos, Integer> appendages = new HashMap<>();

  private SubLevelLocomotion(ServerSubLevel body) {
    this.body = body;
    for (var p : SubLevelHealth.blocks(body)) {
      int types = classify(body.getLevel().getBlockState(p));
      if (types != 0) appendages.put(p.immutable(), types);
    }
    save();
  }

  private static TagKey<Block> tag(String name) {
    return TagKey.create(
        Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create_bioships", name));
  }

  private static int classify(BlockState state) {
    return (state.is(LEGS) ? 1 : 0) | (state.is(FINS) ? 2 : 0) | (state.is(CLIMBERS) ? 5 : 0);
  }

  public static SubLevelData.Locomotion get(ServerSubLevel body) {
    var owner = SubLevelCreatureParts.root(body);
    CENSUSES.computeIfAbsent(owner, SubLevelLocomotion::new);
    return SubLevelData.locomotion(owner);
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var census = CENSUSES.get(body);
    if (census == null) return;
    int type = classify(state);
    int previous = census.appendages.getOrDefault(pos, 0);
    if (type == previous) return;
    if (type == 0) census.appendages.remove(pos);
    else census.appendages.put(pos.immutable(), type);
    census.save();
  }

  public static void refresh(ServerSubLevel body) {
    var census = CENSUSES.get(body);
    if (census == null) {
      census = new SubLevelLocomotion(body);
      CENSUSES.put(body, census);
    } else census.save();
  }

  private void save() {
    int types = 0;
    for (int value : appendages.values()) types |= value;
    var owner = SubLevelCreatureParts.root(body);
    if (owner != body) {
      SubLevelCreatureParts.locomotionContribution(body, types);
      refresh(owner);
      return;
    }
    types |= SubLevelCreatureParts.extraLocomotion(owner);
    SubLevelData.locomotion(
        body, new SubLevelData.Locomotion((types & 1) != 0, (types & 2) != 0, (types & 4) != 0));
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    CENSUSES.keySet().removeIf(ServerSubLevel::isRemoved);
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    CENSUSES.clear();
  }

  @SubscribeEvent
  public static void tags(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
    if (!event.shouldUpdateStaticData()) return;
    CENSUSES.clear();
  }
}
