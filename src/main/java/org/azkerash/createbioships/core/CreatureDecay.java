package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.azkerash.createbioships.CreateBioships;

import javax.annotation.Nonnull;

/** Two-stage decay shared by every physical fragment of a dead creature. */
@EventBusSubscriber(modid = CreateBioships.MODID)
public final class CreatureDecay {
  private static final int TICKS_PER_GAME_DAY = 24_000;
  private static final int DECAY_GAME_DAYS = 2;
  private static final int DECAY_SCAN_INTERVAL_TICKS = 20;

  public static final String FAMILY = "create_bioships:remains_family";
  public static final int DURATION_TICKS = DECAY_GAME_DAYS * TICKS_PER_GAME_DAY;
  private static final Set<ResourceLocation> LAST_TO_ROT = new TreeSet<>();

  static {
    for (var name : List.of("living_core", "land_eye", "aquatic_eye")) {
      registerLastToRot(ResourceLocation.fromNamespaceAndPath(CreateBioships.MODID, name));
    }
  }

  private CreatureDecay() {}

  /** Extend this registry during common setup; existing saved summaries use these IDs too. */
  public static synchronized void registerLastToRot(ResourceLocation blockId) {
    LAST_TO_ROT.add(Objects.requireNonNull(blockId));
  }

  public static synchronized Set<ResourceLocation> lastToRotRegistry() {
    return Set.copyOf(LAST_TO_ROT);
  }

  public static UUID familyId(ServerSubLevel body) {
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    if (root.hasUUID(FAMILY)) {
      return root.getUUID(FAMILY);
    }
    root.putUUID(FAMILY, body.getUniqueId());
    body.setUserDataTag(root);
    return body.getUniqueId();
  }

  /** Associate living physical limbs without copying inventories or behavior state. */
  public static void link(ServerSubLevel parent, ServerSubLevel child) {
    var id = familyId(parent);
    var tag = child.getUserDataTag();
    if (tag == null){
      return;
    }
    tag.copy();
    tag.putUUID(FAMILY, id);
    child.setUserDataTag(tag);
    var world = WorldState.get(parent.getLevel());
    var family = world.families.computeIfAbsent(id, ignored -> new Family());
    family.members.putIfAbsent(parent.getUniqueId(), null);
    family.members.putIfAbsent(child.getUniqueId(), null);
    world.setDirty();
  }

  /** Native Sable split notification; physics separates, corpse identity and death do not. */
  public static void inherit(ServerSubLevel parent, ServerSubLevel child) {
    if (!CoreAssemblyRules.isCreature(parent) || !SubLevelHealth.status(parent).dead()) {
      return;
    }
    var id = familyId(parent);
    var data = parent.getUserDataTag();
    if (data == null) {
      return;
    }

    data.putUUID(FAMILY, id);
    data.putUUID(CoreAssemblyRules.ID, child.getUniqueId());
    child.setUserDataTag(data);
    var world = WorldState.get(parent.getLevel());
    world.cells.remove(child.getUniqueId());
    var family = world.families.computeIfAbsent(id, ignored -> new Family());
    if (!family.members.containsKey(parent.getUniqueId())) {
      family.members.put(parent.getUniqueId(), null);
    }
    family.members.put(child.getUniqueId(), null);
    world.setDirty();
  }

  /** Update summaries only when block types change; blinking does not trigger a rescan. */
  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var world = WorldState.get(body.getLevel());
    var cells = world.cells.get(body.getUniqueId());
    if (cells == null) {
      return;
    }
    // Sable replays plot block notifications before restoring user data on load. A persisted row
    // already belongs to its saved family; bootstrap notifications must not assign a new one.
    var data = body.getUserDataTag();
    if (data == null || !data.hasUUID(FAMILY)) {
      return;
    }
    var next = state.isAir() ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock());
    var previous = cells.get(pos.asLong());
    if (Objects.equals(previous, next)) {
      return;
    }
    if (next == null) {
      cells.remove(pos.asLong());
    } else {
      cells.put(pos.asLong(), next);
    }
    summarize(body, cells);
  }

  /** Permanent deletion differs from chunk unloading, which must keep its blocking summary. */
  public static void removed(ServerSubLevel body) {
    var world = WorldState.get(body.getLevel());
    if (world.cells.remove(body.getUniqueId()) != null) {
      world.setDirty();
    }
    var data = body.getUserDataTag();
    if (data == null || !data.hasUUID(FAMILY)) {
      return;
    }
    var id = data.getUUID(FAMILY);
    var family = world.families.get(id);
    if (family == null) {
      return;
    }
    family.members.remove(body.getUniqueId());
    if (family.members.isEmpty()) {
      world.families.remove(id);
    }
    world.setDirty();
  }

  private static void observe(ServerSubLevel body) {
    var world = WorldState.get(body.getLevel());
    if (world.cells.containsKey(body.getUniqueId())) {
      return;
    }
    var cells = new HashMap<Long, ResourceLocation>();
    for (var pos : SubLevelHealth.blocks(body)) {
      var state = body.getLevel().getBlockState(pos);
      if (!state.isAir()) {
        cells.put(pos.asLong(), BuiltInRegistries.BLOCK.getKey(state.getBlock()));
      }
    }
    world.cells.put(body.getUniqueId(), cells);
    summarize(body, cells);
  }

  private static void summarize(ServerSubLevel body, Map<Long, ResourceLocation> cells) {
    var counts = new HashMap<ResourceLocation, Integer>();
    for (var id : cells.values()) {
      counts.merge(id, 1, Integer::sum);
    }
    var world = WorldState.get(body.getLevel());
    var family = world.families.computeIfAbsent(familyId(body), ignored -> new Family());
    family.members.put(body.getUniqueId(), counts);
    world.setDirty();
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      if (level.getGameTime() % DECAY_SCAN_INTERVAL_TICKS == 0) {
        advance(level, level.getGameTime());
      }
    }
  }

  /** Explicit simulation time also lets development tests advance decay without waiting an hour. */
  public static void advance(ServerLevel level, long now) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null) {
      return;
    }
    var loaded = new HashMap<UUID, List<ServerSubLevel>>();
    for (var body : List.copyOf(container.getAllSubLevels())) {
      if (body.isRemoved()
          || !CoreAssemblyRules.isCreature(body)
          || !SubLevelHealth.status(body).dead()) {
        continue;
      }
      observe(body);
      loaded.computeIfAbsent(familyId(body), ignored -> new ArrayList<>()).add(body);
    }
    var world = WorldState.get(level);
    var last = lastToRotRegistry();
    for (var entry : loaded.entrySet()) {
      var family = world.families.get(entry.getKey());
      if (family == null) {
        continue;
      }
      if (family.interval == 0) {
        int size =
            family.members.values().stream()
                .filter(Objects::nonNull)
                .flatMap(counts -> counts.values().stream())
                .mapToInt(Integer::intValue)
                .sum();
        family.interval =
            Math.max(
                DECAY_SCAN_INTERVAL_TICKS,
                (DURATION_TICKS + Math.max(1, size) - 1) / Math.max(1, size));
        family.next = now + family.interval;
        world.setDirty();
        continue;
      }
      if (now < family.next) {
        continue;
      }
      boolean ordinary = family.hasOrdinary(last);
      var exposed = new ArrayList<Piece>();
      var interior = new ArrayList<Piece>();
      for (var body : entry.getValue()) {
        var summary = family.members.get(body.getUniqueId());
        if (summary != null
            && summary.keySet().stream().noneMatch(id -> last.contains(id) != ordinary)) {
          continue;
        }
        for (var cell : world.cells.get(body.getUniqueId()).entrySet()) {
          if (last.contains(cell.getValue()) == ordinary) {
            continue;
          }
          var pos = BlockPos.of(cell.getKey());
          var piece = new Piece(body, pos);
          boolean outside = false;
          for (var face : Direction.values()) {
            if (level.getBlockState(pos.relative(face)).isAir()) {
              outside = true;
              break;
            }
          }
          (outside ? exposed : interior).add(piece);
        }
      }
      var eligible = exposed.isEmpty() ? interior : exposed;
      if (eligible.isEmpty()) {
        // An unloaded ordinary fragment still blocks the final group.
        continue;
      }
      var selected = eligible.get(level.random.nextInt(eligible.size()));
      if (world.cells.get(selected.body.getUniqueId()).size() == 1) {
        // Delete the grid through Sable before creating an empty physics body. Never drop loot.
        container.removeSubLevel(selected.body, SubLevelRemovalReason.REMOVED);
      } else {
        level.removeBlockEntity(
            selected.pos); // Discard stored contents before onRemove can spill them.
        level.removeBlock(selected.pos, false); // No block loot or player-break interaction.
      }
      family.next = now + family.interval;
      world.setDirty();
    }
  }

  private record Piece(ServerSubLevel body, BlockPos pos) {}

  public static final class Family {
    private final Map<UUID, Map<ResourceLocation, Integer>> members = new HashMap<>();
    private long next;
    private int interval;

    public boolean hasOrdinary(Set<ResourceLocation> last) {
      for (var counts : members.values()) {
        if (counts == null) {
          return true;
        }
        for (var entry : counts.entrySet()) {
          if (entry.getValue() > 0 && !last.contains(entry.getKey())) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * Snapshot preserves null summaries: an unloaded unknown member must still block the final
     * stage.
     */
    public Map<UUID, Map<ResourceLocation, Integer>> getMembers() {
      var snapshot = new HashMap<UUID, Map<ResourceLocation, Integer>>();
      members.forEach((id, counts) -> snapshot.put(id, counts == null ? null : Map.copyOf(counts)));
      return Collections.unmodifiableMap(snapshot);
    }

    void rememberMember(UUID id, Map<ResourceLocation, Integer> counts) {
      members.put(id, counts == null ? null : new HashMap<>(counts));
    }

    void forgetMember(UUID id) {
      members.remove(id);
    }

    public long getNext() {
      return this.next;
    }

    public int getInterval() {
      return this.interval;
    }
  }

  /** UUID-owned layouts, summaries and deadlines survive both unloading and server restarts. */
  public static final class WorldState extends SavedData {
    private static final Factory<WorldState> FACTORY =
        new Factory<>(WorldState::new, (tag, registries) -> load(tag));
    private final Map<UUID, Family> families = new HashMap<>();
    // Sable persists plot allocation coordinates. These packed positions belong to that plot,
    // never the creature's moving world-space pose or an ephemeral ServerSubLevel reference.
    private final Map<UUID, Map<Long, ResourceLocation>> cells = new HashMap<>();

    public static WorldState get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(FACTORY, "create_bioships_decay");
    }

    public static WorldState load(CompoundTag tag) {
      var result = new WorldState();
      for (var value : tag.getList("families", 10)) {
        var saved = (CompoundTag) value;
        var family = new Family();
        family.next = saved.getLong("next");
        family.interval = saved.getInt("interval");
        for (var memberValue : saved.getList("members", 10)) {
          var member = (CompoundTag) memberValue;
          Map<ResourceLocation, Integer> counts = null;
          if (!member.getBoolean("unknown")) {
            counts = new HashMap<>();
            var data = member.getCompound("counts");
            for (var key : data.getAllKeys()) {
              counts.put(ResourceLocation.parse(key), data.getInt(key));
            }
          }
          family.members.put(member.getUUID("id"), counts);
        }
        result.families.put(saved.getUUID("id"), family);
      }
      // Older saves contain only family summaries. Missing layouts stay unobserved so the first
      // loaded instance rebuilds them without resetting the family's existing decay deadline.
      for (var value : tag.getList("cells", Tag.TAG_COMPOUND)) {
        var saved = (CompoundTag) value;
        if (!saved.hasUUID("id")) {
          continue;
        }
        var layout = readCells(saved);
        if (layout != null) {
          result.cells.put(saved.getUUID("id"), layout);
        }
      }
      return result;
    }

    private static Map<Long, ResourceLocation> readCells(CompoundTag saved) {
      if (!saved.contains("positions", Tag.TAG_LONG_ARRAY)
          || !(saved.get("blocks") instanceof ListTag blocks)) {
        return null;
      }
      var positions = saved.getLongArray("positions");
      if ((!blocks.isEmpty() && blocks.getElementType() != Tag.TAG_STRING)
          || positions.length != blocks.size()) {
        return null;
      }
      var layout = new HashMap<Long, ResourceLocation>();
      for (int i = 0; i < positions.length; i++) {
        var id = ResourceLocation.tryParse(blocks.getString(i));
        if (id == null || layout.put(positions[i], id) != null) {
          // Never interpret an incomplete row as an empty/finished corpse. A loaded body can
          // rebuild it, while the persisted family summary keeps unloaded fragments blocking.
          return null;
        }
      }
      return layout;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
      var familiesTag = new ListTag();
      for (var entry : families.entrySet()) {
        var saved = new CompoundTag();
        var family = entry.getValue();
        saved.putUUID("id", entry.getKey());
        saved.putLong("next", family.next);
        saved.putInt("interval", family.interval);
        var membersTag = new ListTag();
        for (var member : family.members.entrySet()) {
          var item = new CompoundTag();
          item.putUUID("id", member.getKey());
          if (member.getValue() == null) {
            item.putBoolean("unknown", true);
          } else {
            var counts = new CompoundTag();
            member.getValue().forEach((id, count) -> counts.putInt(id.toString(), count));
            item.put("counts", counts);
          }
          membersTag.add(item);
        }
        saved.put("members", membersTag);
        familiesTag.add(saved);
      }
      tag.put("families", familiesTag);
      var layoutsTag = new ListTag();
      for (var entry : cells.entrySet()) {
        var saved = new CompoundTag();
        saved.putUUID("id", entry.getKey());
        // Sort once so the parallel arrays stay aligned and produce stable cell ordering in NBT.
        long[] positions =
            entry.getValue().keySet().stream().mapToLong(Long::longValue).sorted().toArray();
        var blocks = new ListTag();
        for (long pos : positions) {
          blocks.add(StringTag.valueOf(entry.getValue().get(pos).toString()));
        }
        saved.putLongArray("positions", positions);
        saved.put("blocks", blocks);
        layoutsTag.add(saved);
      }
      tag.put("cells", layoutsTag);
      return tag;
    }

    /** Read-only layout snapshot for one persistent sublevel identity. */
    public Map<Long, ResourceLocation> getCells(UUID subLevelId) {
      var layout = cells.get(subLevelId);
      return layout == null ? Map.of() : Map.copyOf(layout);
    }

    public Map<UUID, Family> getFamilies() {
      return Collections.unmodifiableMap(this.families);
    }
  }
}
