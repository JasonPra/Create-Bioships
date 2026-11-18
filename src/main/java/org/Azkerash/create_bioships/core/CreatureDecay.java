package org.Azkerash.create_bioships.core;

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
import org.Azkerash.create_bioships.Create_bioships;

/** Two-stage decay shared by every physical fragment of a dead creature. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CreatureDecay {
  public static final String FAMILY = "create_bioships:remains_family";
  public static final int DURATION_TICKS = 3 * 24000;
  private static final Set<ResourceLocation> LAST_TO_ROT = new TreeSet<>();
  private static final Map<ServerSubLevel, Map<Long, ResourceLocation>> CELLS = new WeakHashMap<>();

  static {
    for (var name : List.of("living_core", "land_eye", "aquatic_eye"))
      registerLastToRot(ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, name));
  }

  private CreatureDecay() {}

  /** Extend this registry during common setup; existing saved summaries use these IDs too. */
  public static synchronized void registerLastToRot(ResourceLocation blockId) {
    LAST_TO_ROT.add(Objects.requireNonNull(blockId));
  }

  public static synchronized Set<ResourceLocation> lastToRotRegistry() {
    return Set.copyOf(LAST_TO_ROT);
  }

  public static boolean rotsLast(BlockState state) {
    return lastToRotRegistry().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
  }

  public static UUID familyId(ServerSubLevel body) {
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    if (root.hasUUID(FAMILY)) return root.getUUID(FAMILY);
    root.putUUID(FAMILY, body.getUniqueId());
    body.setUserDataTag(root);
    return body.getUniqueId();
  }

  /** Associate living physical limbs without copying inventories or behavior state. */
  public static void link(ServerSubLevel parent, ServerSubLevel child) {
    var id = familyId(parent);
    var tag = child.getUserDataTag().copy();
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
    if (!CoreAssemblyRules.isCreature(parent) || !SubLevelHealth.status(parent).dead()) return;
    var id = familyId(parent);
    var data = parent.getUserDataTag().copy();
    data.putUUID(FAMILY, id);
    data.putUUID(CoreAssemblyRules.ID, child.getUniqueId());
    child.setUserDataTag(data);
    CELLS.remove(child);
    var world = WorldState.get(parent.getLevel());
    var family = world.families.computeIfAbsent(id, ignored -> new Family());
    if (!family.members.containsKey(parent.getUniqueId()))
      family.members.put(parent.getUniqueId(), null);
    family.members.put(child.getUniqueId(), null);
    world.setDirty();
  }

  /** Update summaries only when block types change; blinking does not trigger a rescan. */
  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var cells = CELLS.get(body);
    if (cells == null) return;
    var next = state.isAir() ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock());
    var previous = cells.get(pos.asLong());
    if (Objects.equals(previous, next)) return;
    if (next == null) cells.remove(pos.asLong());
    else cells.put(pos.asLong(), next);
    summarize(body, cells);
  }

  /** Permanent deletion differs from chunk unloading, which must keep its blocking summary. */
  public static void removed(ServerSubLevel body) {
    CELLS.remove(body);
    var data = body.getUserDataTag();
    if (data == null || !data.hasUUID(FAMILY)) return;
    var world = WorldState.get(body.getLevel());
    var id = data.getUUID(FAMILY);
    var family = world.families.get(id);
    if (family == null) return;
    family.members.remove(body.getUniqueId());
    if (family.members.isEmpty()) world.families.remove(id);
    world.setDirty();
  }

  private static void observe(ServerSubLevel body) {
    if (CELLS.containsKey(body)) return;
    var cells = new HashMap<Long, ResourceLocation>();
    for (var pos : SubLevelHealth.blocks(body)) {
      var state = body.getLevel().getBlockState(pos);
      if (!state.isAir()) cells.put(pos.asLong(), BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }
    CELLS.put(body, cells);
    summarize(body, cells);
  }

  private static void summarize(ServerSubLevel body, Map<Long, ResourceLocation> cells) {
    var counts = new HashMap<ResourceLocation, Integer>();
    for (var id : cells.values()) counts.merge(id, 1, Integer::sum);
    var world = WorldState.get(body.getLevel());
    var family = world.families.computeIfAbsent(familyId(body), ignored -> new Family());
    family.members.put(body.getUniqueId(), counts);
    world.setDirty();
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels())
      if (level.getGameTime() % 20 == 0) advance(level, level.getGameTime());
  }

  /** Explicit simulation time also lets development tests advance decay without waiting an hour. */
  public static void advance(ServerLevel level, long now) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null) return;
    var loaded = new HashMap<UUID, List<ServerSubLevel>>();
    for (var entry : List.copyOf(container.getAllSubLevels())) {
      if (!(entry instanceof ServerSubLevel body)
          || body.isRemoved()
          || !CoreAssemblyRules.isCreature(body)
          || !SubLevelHealth.status(body).dead()) continue;
      observe(body);
      loaded.computeIfAbsent(familyId(body), ignored -> new ArrayList<>()).add(body);
    }
    var world = WorldState.get(level);
    var last = lastToRotRegistry();
    for (var entry : loaded.entrySet()) {
      var family = world.families.get(entry.getKey());
      if (family == null) continue;
      if (family.interval == 0) {
        int size =
            family.members.values().stream()
                .filter(Objects::nonNull)
                .flatMap(counts -> counts.values().stream())
                .mapToInt(Integer::intValue)
                .sum();
        family.interval =
            Math.max(20, (DURATION_TICKS + Math.max(1, size) - 1) / Math.max(1, size));
        family.next = now + family.interval;
        world.setDirty();
        continue;
      }
      if (now < family.next) continue;
      boolean ordinary = family.hasOrdinary(last);
      var exposed = new ArrayList<Piece>();
      var interior = new ArrayList<Piece>();
      for (var body : entry.getValue()) {
        var summary = family.members.get(body.getUniqueId());
        if (summary != null
            && summary.keySet().stream().noneMatch(id -> last.contains(id) != ordinary)) continue;
        for (var cell : CELLS.get(body).entrySet()) {
          if (last.contains(cell.getValue()) == ordinary) continue;
          var pos = BlockPos.of(cell.getKey());
          var piece = new Piece(body, pos);
          boolean outside = false;
          for (var face : Direction.values())
            if (level.getBlockState(pos.relative(face)).isAir()) {
              outside = true;
              break;
            }
          (outside ? exposed : interior).add(piece);
        }
      }
      var eligible = exposed.isEmpty() ? interior : exposed;
      if (eligible.isEmpty())
        continue; // An unloaded ordinary fragment still blocks the final group.
      var selected = eligible.get(level.random.nextInt(eligible.size()));
      if (CELLS.get(selected.body).size() == 1) {
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
    final Map<UUID, Map<ResourceLocation, Integer>> members = new HashMap<>();
    long next;
    int interval;

    public boolean hasOrdinary(Set<ResourceLocation> last) {
      for (var counts : members.values()) {
        if (counts == null) return true;
        for (var entry : counts.entrySet())
          if (entry.getValue() > 0 && !last.contains(entry.getKey())) return true;
      }
      return false;
    }
  }

  /** Counts persist for unloaded fragments, rather than treating them as already rotted. */
  public static final class WorldState extends SavedData {
    final Map<UUID, Family> families = new HashMap<>();

    public static WorldState get(ServerLevel level) {
      return level
          .getDataStorage()
          .computeIfAbsent(
              new Factory<>(WorldState::new, (tag, registries) -> load(tag)),
              "create_bioships_decay");
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
            for (var key : data.getAllKeys())
              counts.put(ResourceLocation.parse(key), data.getInt(key));
          }
          family.members.put(member.getUUID("id"), counts);
        }
        result.families.put(saved.getUUID("id"), family);
      }
      return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
          if (member.getValue() == null) item.putBoolean("unknown", true);
          else {
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
      return tag;
    }
  }
}
