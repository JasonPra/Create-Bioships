package org.Azkerash.create_bioships.core.access;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import org.Azkerash.create_bioships.core.SubLevelData;

/** Grid-owned identity permissions. Names are labels; identity is always a UUID. */
public final class SubLevelPermissions {
  public static final int MAX_ENTRIES = 128;

  public record Entry(UUID id, String name, boolean player) {}

  public record Access(UUID owner, List<Entry> allowed) {
    public boolean permits(UUID id) {
      return id.equals(owner) || allowed.stream().anyMatch(e -> e.id.equals(id));
    }
  }

  private static final Map<ServerSubLevel, Access> CACHE = new WeakHashMap<>();

  private SubLevelPermissions() {}

  public static Access get(ServerSubLevel body) {
    return CACHE.computeIfAbsent(
        body,
        b -> {
          var root = b.getUserDataTag();
          var tag = root == null ? new CompoundTag() : root.getCompound(SubLevelData.ACCESS);
          var entries = new LinkedHashMap<UUID, Entry>();
          for (var element : tag.getList("allowed", 10)) {
            var value = (CompoundTag) element;
            if (value.hasUUID("id") && entries.size() < MAX_ENTRIES) {
              var id = value.getUUID("id");
              entries.put(id, new Entry(id, value.getString("name"), value.getBoolean("player")));
            }
          }
          return new Access(
              tag.hasUUID("owner") ? tag.getUUID("owner") : null, List.copyOf(entries.values()));
        });
  }

  private static void save(ServerSubLevel body, Access access) {
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    var tag = new CompoundTag();
    if (access.owner != null) tag.putUUID("owner", access.owner);
    var list = new ListTag();
    for (var entry : access.allowed) {
      var item = new CompoundTag();
      item.putUUID("id", entry.id);
      item.putString("name", entry.name);
      item.putBoolean("player", entry.player);
      list.add(item);
    }
    tag.put("allowed", list);
    root.put(SubLevelData.ACCESS, tag);
    body.setUserDataTag(root);
    CACHE.put(body, access);
  }

  public static boolean assignIfUnowned(ServerSubLevel body, UUID owner) {
    var current = get(body);
    if (owner == null || current.owner != null) return false;
    save(body, new Access(owner, current.allowed));
    return true;
  }

  public static boolean add(ServerSubLevel body, UUID actor, Entity entity) {
    var current = get(body);
    boolean player = entity instanceof net.minecraft.world.entity.player.Player;
    if (!actor.equals(current.owner)
        || (!player
            && (!(entity instanceof net.minecraft.world.entity.Mob) || !entity.hasCustomName()))
        || current.permits(entity.getUUID())
        || current.allowed.size() >= MAX_ENTRIES) return false;
    var entries = new ArrayList<>(current.allowed);
    entries.add(new Entry(entity.getUUID(), entity.getName().getString(), player));
    save(body, new Access(current.owner, List.copyOf(entries)));
    return true;
  }

  public static boolean remove(ServerSubLevel body, UUID actor, UUID target) {
    var current = get(body);
    if (!actor.equals(current.owner)) return false;
    var entries = current.allowed.stream().filter(e -> !e.id.equals(target)).toList();
    if (entries.size() == current.allowed.size()) return false;
    save(body, new Access(current.owner, entries));
    return true;
  }

  public static boolean transfer(ServerSubLevel body, UUID actor, UUID target) {
    var current = get(body);
    if (target == null || !actor.equals(current.owner) || actor.equals(target)) return false;
    save(
        body,
        new Access(target, current.allowed.stream().filter(e -> !e.id.equals(target)).toList()));
    return true;
  }

  public static void inherit(ServerSubLevel parent, ServerSubLevel child) {
    if (get(child).owner == null && get(parent).owner != null) save(child, get(parent));
  }

  /** Save/reload testing and shutdown do not retain a grid's transient parsed view. */
  public static void forget(ServerSubLevel body) {
    CACHE.remove(body);
  }
}
