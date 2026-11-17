package org.Azkerash.create_bioships;

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

/** Persistent grid identity and player assembler restrictions; no creature behavior. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CoreAssemblyRules {
  public static final String TYPE = "create_bioships:creature_type",
      ID = "create_bioships:creature_id";
  private static final String PRIMARY = "create_bioships:primary_core",
      CHECK = "create_bioships:check_cores";

  public static ServerSubLevel containing(ServerLevel level, BlockPos pos) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) return null;
    var plot = container.getPlot(new ChunkPos(pos));
    return plot != null && plot.getSubLevel() instanceof ServerSubLevel body && !body.isRemoved()
        ? body
        : null;
  }

  public static void mark(ServerLevel level, BlockPos core) {
    var body = containing(level, core);
    if (body == null) return;
    var old = body.getUserDataTag();
    if (old != null
        && "amoeba".equals(old.getString(TYPE))
        && old.hasUUID(ID)
        && old.getUUID(ID).equals(body.getUniqueId())) return;
    var tag = old == null ? new CompoundTag() : old.copy();
    tag.putString(TYPE, "amoeba");
    tag.putUUID(ID, body.getUniqueId());
    body.setUserDataTag(tag);
  }

  /** Movement callbacks request work; splitting happens after native assembly has finished. */
  public static void changed(ServerLevel level, BlockPos core) {
    mark(level, core);
    var body = containing(level, core);
    if (body == null) return;
    var tag = body.getUserDataTag().copy();
    if (!tag.contains(PRIMARY)) tag.putLong(PRIMARY, core.asLong());
    tag.putBoolean(CHECK, true);
    body.setUserDataTag(tag);
  }

  static List<BlockPos> cores(ServerSubLevel body) {
    var result = new ArrayList<BlockPos>();
    for (var holder : body.getPlot().getLoadedChunks()) {
      var chunk = holder.getChunk();
      var sections = chunk.getSections();
      for (int index = 0; index < sections.length; index++) {
        var section = sections[index];
        if (!section.maybeHas(state -> state.is(Create_bioships.CORE.get()))) continue;
        int floor = chunk.getSectionYFromSectionIndex(index) << 4;
        for (int x = 0; x < 16; x++)
          for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
              if (section.getBlockState(x, y, z).is(Create_bioships.CORE.get()))
                result.add(
                    new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        floor + y,
                        chunk.getPos().getMinBlockZ() + z));
      }
    }
    result.sort(Comparator.naturalOrder());
    return List.copyOf(result);
  }

  public static void reconcile(ServerLevel level, BlockPos core) {
    var body = containing(level, core);
    if (body == null) return;
    var tag = body.getUserDataTag();
    if (tag == null) {
      mark(level, core);
      tag = body.getUserDataTag();
    }
    // The usual single-core blink does not scan the grid. Extra loaded cores still self-detect.
    if (tag.contains(PRIMARY) && !tag.getBoolean(CHECK) && tag.getLong(PRIMARY) == core.asLong())
      return;
    var positions = cores(body);
    if (positions.isEmpty()) return;
    var keeper = tag.contains(PRIMARY) ? BlockPos.of(tag.getLong(PRIMARY)) : positions.getFirst();
    if (!positions.contains(keeper)) keeper = positions.getFirst();
    tag = tag.copy();
    tag.putLong(PRIMARY, keeper.asLong());
    tag.putBoolean(CHECK, false);
    body.setUserDataTag(tag);
    for (var extra : positions) {
      if (extra.equals(keeper)) continue;
      var child =
          SubLevelAssemblyHelper.assembleBlocks(
              level, extra, List.of(extra), new BoundingBox3i(extra, extra));
      if (child == null) {
        var retry = body.getUserDataTag().copy();
        retry.putBoolean(CHECK, true);
        body.setUserDataTag(retry);
        level.scheduleTick(keeper, Create_bioships.CORE.get(), 20);
        return;
      }
      // Sable preserves the source pose and velocity. The new grid gets an independent identity.
      changed(level, child.getPlot().getCenterBlock());
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

  @SubscribeEvent
  public static void assemblePlayerPlacements(
      net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      var positions = PLAYER_PLACEMENTS.remove(level);
      if (positions == null) continue;
      for (var pos : positions) {
        if (!level.getBlockState(pos).is(Create_bioships.CORE.get())) continue;
        if (containing(level, pos) != null) changed(level, pos);
        else
          SubLevelAssemblyHelper.assembleBlocks(
              level, pos, List.of(pos), new BoundingBox3i(pos, pos));
      }
    }
  }

  private static boolean creatureSurface(net.minecraft.world.level.Level level, BlockPos pos) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null || !container.inBounds(pos)) return false;
    var plot = container.getPlot(new ChunkPos(pos));
    if (plot == null) return false;
    var body = plot.getSubLevel();
    if (body instanceof ServerSubLevel) return isCreature(body);
    // The client can reject the click before item prediction using its loaded core geometry.
    for (var holder : plot.getLoadedChunks())
      for (var section : holder.getChunk().getSections())
        if (section.maybeHas(state -> state.is(Create_bioships.CORE.get()))) return true;
    return false;
  }

  @SubscribeEvent
  public static void clicked(
      net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem item)
        || BlockPlacementRules.allows(item.getBlock().defaultBlockState())) return;
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
        && isCreature(containing(level, event.getPos()))) event.setCanceled(true);
  }
}
