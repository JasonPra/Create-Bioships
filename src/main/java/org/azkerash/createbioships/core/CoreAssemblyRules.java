package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.ComponentRegistry;

/** Persistent grid identity and player assembler restrictions; no creature behavior. */
@EventBusSubscriber(modid = CreateBioships.MODID)
public final class CoreAssemblyRules {
  public static final String TYPE = "create_bioships:creature_type",
      ID = "create_bioships:creature_id";
  private static final String PRIMARY = "create_bioships:primary_core";
  private static final int SEPARATION_RETRY_TICKS = 20;
  // Disposable placement work only. Core identity and the primary position remain in Sable NBT.
  private static final Map<ServerLevel, Map<BlockPos, Long>> PENDING = new IdentityHashMap<>();

  public static ServerSubLevel containing(ServerLevel level, BlockPos pos) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) {
      return null;
    }
    var plot = container.getPlot(new ChunkPos(pos));
    return plot != null && plot.getSubLevel() instanceof ServerSubLevel body && !body.isRemoved()
        ? body
        : null;
  }

  public static void mark(ServerLevel level, BlockPos core) {
    var body = containing(level, core);
    if (body == null) {
      return;
    }
    if (body.getUserDataTag() == null || !body.getUserDataTag().contains(SubLevelData.LIVING)) {
      SubLevelData.living(body, true);
    }
    var old = body.getUserDataTag();
    if (old != null
        && "amoeba".equals(old.getString(TYPE))
        && old.hasUUID(ID)
        && old.getUUID(ID).equals(body.getUniqueId())) {
      return;
    }
    var tag = old == null ? new CompoundTag() : old.copy();
    tag.putString(TYPE, "amoeba");
    tag.putUUID(ID, body.getUniqueId());
    body.setUserDataTag(tag);
  }

  /** Placement/move/load callbacks provide the exact cell; never rescan a healthy grid. */
  public static void changed(ServerLevel level, BlockPos core) {
    mark(level, core);
    var body = containing(level, core);
    if (body == null) {
      return;
    }
    primary(body, core);
    queue(level, core, level.getGameTime());
  }

  private static void queue(ServerLevel level, BlockPos core, long due) {
    PENDING
        .computeIfAbsent(level, ignored -> new LinkedHashMap<>())
        .merge(core.immutable(), due, Math::min);
  }

  /**
   * A valid primary is an O(1) identity check. An unloaded primary keeps its claim until loaded;
   * looking up this metadata must not request plot chunks or elect a duplicate as its replacement.
   */
  private static BlockPos primary(ServerSubLevel body, BlockPos candidate) {
    var tag = body.getUserDataTag();
    if (tag != null && tag.contains(PRIMARY)) {
      var existing = BlockPos.of(tag.getLong(PRIMARY));
      var level = body.getLevel();
      if (containing(level, existing) == body
          && (!level.hasChunk(existing.getX() >> 4, existing.getZ() >> 4)
              || level.getBlockState(existing).is(ComponentRegistry.CORE.get()))) {
        return existing;
      }
    }
    tag = tag == null ? new CompoundTag() : tag.copy();
    tag.putLong(PRIMARY, candidate.asLong());
    body.setUserDataTag(tag);
    return candidate;
  }

  /** Diagnostic enumeration only; placement and animation never call this scan. */
  static List<BlockPos> cores(ServerSubLevel body) {
    var result = new ArrayList<BlockPos>();
    for (var holder : body.getPlot().getLoadedChunks()) {
      var chunk = holder.getChunk();
      var sections = chunk.getSections();
      for (int index = 0; index < sections.length; index++) {
        var section = sections[index];
        if (!section.maybeHas(state -> state.is(ComponentRegistry.CORE.get()))) {
          continue;
        }
        int floor = chunk.getSectionYFromSectionIndex(index) << 4;
        for (int x = 0; x < 16; x++) {
          for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
              if (section.getBlockState(x, y, z).is(ComponentRegistry.CORE.get())) {
                result.add(
                    new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        floor + y,
                        chunk.getPos().getMinBlockZ() + z));
              }
            }
          }
        }
      }
    }
    result.sort(Comparator.naturalOrder());
    return List.copyOf(result);
  }

  /** Compatibility entry point; validation uses the supplied core cell, never a grid search. */
  public static void reconcile(ServerLevel level, BlockPos core) {
    separate(level, core);
  }

  private static ServerSubLevel separate(ServerLevel level, BlockPos core) {
    var body = containing(level, core);
    if (body == null || !level.getBlockState(core).is(ComponentRegistry.CORE.get())) {
      return null;
    }
    mark(level, core);
    if (primary(body, core).equals(core)) {
      return body;
    }
    // Defer this until native placement/assembly has finished. Sable carries the pose/velocity.
    var child =
        SubLevelAssemblyHelper.assembleBlocks(
            level, core, List.of(core), new BoundingBox3i(core, core));
    if (child == null) {
      queue(level, core, level.getGameTime() + SEPARATION_RETRY_TICKS);
      return null;
    }
    changed(level, child.getPlot().getCenterBlock());
    org.azkerash.createbioships.core.access.SubLevelPermissions.inherit(body, child);
    return child;
  }

  private static void validatePending(ServerLevel level) {
    // Assembly callbacks may enqueue new child cores while this batch is being processed.
    var pending = PENDING.remove(level);
    if (pending == null) {
      return;
    }
    long now = level.getGameTime();
    for (var entry : pending.entrySet()) {
      if (entry.getValue() > now) {
        queue(level, entry.getKey(), entry.getValue());
      } else {
        reconcile(level, entry.getKey());
      }
    }
  }

  public static boolean isCreature(SubLevel body) {
    return body instanceof ServerSubLevel server
        && server.getUserDataTag() != null
        && "amoeba".equals(server.getUserDataTag().getString(TYPE));
  }

  private static final Map<ServerLevel, Set<BlockPos>> PLAYER_PLACEMENTS = new WeakHashMap<>();

  public static void playerPlaced(ServerLevel level, BlockPos pos) {
    PLAYER_PLACEMENTS.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
  }

  private static final Map<ServerLevel, Map<BlockPos, UUID>> PLACERS = new WeakHashMap<>();

  public static void playerPlaced(ServerLevel level, BlockPos pos, UUID player) {
    playerPlaced(level, pos);
    PLACERS.computeIfAbsent(level, l -> new HashMap<>()).put(pos.immutable(), player);
  }

  @SubscribeEvent
  public static void assemblePlayerPlacements(
      net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      var positions = PLAYER_PLACEMENTS.remove(level);
      var owners = PLACERS.remove(level);
      if (positions != null) {
        for (var pos : positions) {
          if (!level.getBlockState(pos).is(ComponentRegistry.CORE.get())) {
            continue;
          }
          var body = containing(level, pos);
          if (body != null) {
            // Intercept only the newly placed core; never enumerate or move the host's blocks.
            body = separate(level, pos);
          } else {
            body =
                SubLevelAssemblyHelper.assembleBlocks(
                    level, pos, List.of(pos), new BoundingBox3i(pos, pos));
          }
          if (body != null && owners != null) {
            org.azkerash.createbioships.core.access.SubLevelPermissions.assignIfUnowned(
                body, owners.get(pos));
          }
        }
      }
      validatePending(level);
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    PENDING.clear();
    PLAYER_PLACEMENTS.clear();
    PLACERS.clear();
  }

  private static boolean creatureSurface(net.minecraft.world.level.Level level, BlockPos pos) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) {
      return false;
    }
    var plot = container.getPlot(new ChunkPos(pos));
    if (plot == null) {
      return false;
    }
    var body = plot.getSubLevel();
    if (body instanceof ServerSubLevel) {
      return isCreature(body);
    }
    // The client can reject the click before item prediction using its loaded core geometry.
    for (var holder : plot.getLoadedChunks()) {
      for (var section : holder.getChunk().getSections()) {
        if (section.maybeHas(state -> state.is(ComponentRegistry.CORE.get()))) {
          return true;
        }
      }
    }
    return false;
  }

  @SubscribeEvent
  public static void clicked(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem item)
        || BlockPlacementRules.allows(item.getBlock().defaultBlockState())) {
      return;
    }
    var pos = event.getPos();
    var face = event.getFace();
    if (creatureSurface(event.getLevel(), pos)
        || face != null && creatureSurface(event.getLevel(), pos.relative(face))) {
      event.setCanceled(true);
      event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
    }
  }

  @SubscribeEvent
  public static void placed(BlockEvent.EntityPlaceEvent event) {
    if (event.getEntity() instanceof Player
        && event.getLevel() instanceof ServerLevel level
        && !BlockPlacementRules.allows(event.getPlacedBlock())
        && isCreature(containing(level, event.getPos()))) {
      event.setCanceled(true);
    }
  }
}
