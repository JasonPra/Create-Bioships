package org.Azkerash.create_bioships.core.component;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.logic.inventory.OrganInventory;

/**
 * One authoritative component store per loaded grid. Block entities are views with portable
 * snapshots.
 */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class SubLevelComponentStore {
  public static final String KEY = "create_bioships:components";
  private static final Map<ServerSubLevel, SubLevelComponentStore> STORES = new IdentityHashMap<>();

  /**
   * Stable component identity; block entities borrow this state rather than owning duplicate
   * inventories.
   */
  public static final class Entry {
    private final UUID id;
    private BlockPos pos;
    private final String type;
    private final NonNullList<ItemStack> items;
    private ComponentState state;
    private CompoundTag storedState;

    Entry(
        UUID id,
        BlockPos pos,
        String type,
        NonNullList<ItemStack> items,
        ComponentState state,
        CompoundTag storedState) {
      this.id = id;
      this.pos = pos;
      this.type = type;
      this.items = items;
      this.state = state;
      this.storedState = storedState;
    }

    public UUID getId() {
      return this.id;
    }

    public BlockPos getPos() {
      return this.pos;
    }

    public String getType() {
      return this.type;
    }

    /**
     * Live fixed-size inventory backing. Views must notify their container's dirty hook after
     * edits.
     */
    public NonNullList<ItemStack> getItems() {
      return this.items;
    }

    /**
     * Live component-specific state, shared by replacement views of the same physical component.
     */
    public ComponentState getState() {
      return this.state;
    }
  }

  private final ServerSubLevel body;
  private final Map<UUID, Entry> entries = new LinkedHashMap<>();
  private final Map<BlockPos, UUID> positions = new HashMap<>();
  private boolean dirty;

  private SubLevelComponentStore(ServerSubLevel body) {
    this.body = body;
    var root = body.getUserDataTag();
    if (root == null) {
      return;
    }
    var saved = root.getCompound(KEY);
    for (var key : saved.getAllKeys()) {
      UUID id;
      try {
        id = UUID.fromString(key);
      } catch (IllegalArgumentException ignored) {
        continue;
      }
      var tag = saved.getCompound(key);
      var items = NonNullList.withSize(Math.clamp(tag.getInt("Size"), 0, 256), ItemStack.EMPTY);
      ContainerHelper.loadAllItems(tag, items, body.getLevel().registryAccess());
      var entry =
          new Entry(
              id,
              BlockPos.of(tag.getLong("Pos")),
              tag.getString("Type"),
              items,
              null,
              tag.getCompound("State"));
      entries.put(id, entry);
      positions.put(entry.pos, id);
    }
  }

  public static SubLevelComponentStore get(ServerSubLevel body) {
    return STORES.computeIfAbsent(body, SubLevelComponentStore::new);
  }

  public Entry bind(
      UUID requested,
      BlockPos pos,
      String type,
      NonNullList<ItemStack> local,
      ComponentState localState) {
    var entry = entries.get(requested);
    if (entry != null
        && (!entry.type.equals(type)
            || !entry.pos.equals(pos)
                && body.getLevel().getBlockEntity(entry.pos) instanceof OrganInventory other
                && !other.isRemoved()
                && other.componentId().equals(requested))) {
      requested = UUID.randomUUID();
      entry = null;
    }
    var previous = positions.get(pos);
    if (previous != null && !previous.equals(requested)) {
      entries.remove(previous);
    }
    if (entry == null) {
      entry = new Entry(requested, pos.immutable(), type, local, localState, new CompoundTag());
      entries.put(requested, entry);
      dirty = true;
    } else {
      positions.remove(entry.pos);
      if (!entry.pos.equals(pos)) {
        dirty = true;
      }
      entry.pos = pos.immutable();
      if (entry.state == null && localState != null) {
        entry.state = localState;
        localState.load(entry.storedState, body.getLevel().registryAccess());
      }
    }
    positions.put(entry.pos, entry.id);
    return entry;
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var store = STORES.get(body);
    if (store == null) {
      return;
    }
    var id = store.positions.get(pos);
    if (id == null) {
      return;
    }
    var entry = store.entries.get(id);
    if (entry != null
        && !entry.type.equals(
            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .toString())) {
      store.positions.remove(pos);
      store.entries.remove(id);
      store.dirty = true;
    }
  }

  public static void release(ServerSubLevel body, UUID id, BlockPos pos) {
    var store = STORES.get(body);
    if (store == null) {
      return;
    }
    var entry = store.entries.get(id);
    if (entry != null && entry.pos.equals(pos)) {
      store.entries.remove(id);
      store.positions.remove(pos);
      store.dirty = true;
    }
  }

  public void dirty() {
    dirty = true;
  }

  public boolean contains(UUID id) {
    return entries.containsKey(id);
  }

  public static void flush(ServerSubLevel body) {
    var store = STORES.get(body);
    if (store == null || !store.dirty) {
      return;
    }
    var saved = new CompoundTag();
    for (var entry : store.entries.values()) {
      var tag = new CompoundTag();
      tag.putLong("Pos", entry.pos.asLong());
      tag.putString("Type", entry.type);
      tag.putInt("Size", entry.items.size());
      ContainerHelper.saveAllItems(tag, entry.items, body.getLevel().registryAccess());
      tag.put(
          "State",
          entry.state == null
              ? entry.storedState
              : entry.state.save(body.getLevel().registryAccess()));
      saved.put(entry.id.toString(), tag);
    }
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    root.put(KEY, saved);
    body.setUserDataTag(root);
    store.dirty = false;
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 8 != 0) {
      return;
    }
    for (var body : new ArrayList<>(STORES.keySet())) {
      if (body.isRemoved()) {
        STORES.remove(body);
      } else {
        flush(body);
      }
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    STORES.clear();
  }
}
