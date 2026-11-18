package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Persistent membership and cached capacity contributions for one creature's physical bodies. */
public final class SubLevelCreatureParts {
  private SubLevelCreatureParts() {}

  private static CompoundTag data(ServerSubLevel body) {
    return body.getUserDataTag() == null
        ? new CompoundTag()
        : body.getUserDataTag().getCompound(SubLevelData.PARTS);
  }

  public static boolean isPart(ServerSubLevel body) {
    return data(body).hasUUID("Root");
  }

  public static ServerSubLevel root(ServerSubLevel body) {
    if (!isPart(body)) return body;
    var original = body;
    var seen = new HashSet<UUID>();
    var container = SubLevelContainer.getContainer(body.getLevel());
    for (int i = 0; i < 32; i++) {
      var tag = data(body);
      if (!tag.hasUUID("Root")) return body;
      if (!seen.add(body.getUniqueId()) || container == null) return original;
      var found = container.getSubLevel(tag.getUUID("Root"));
      if (!(found instanceof ServerSubLevel parent) || parent.isRemoved()) return original;
      body = parent;
    }
    return original;
  }

  public static boolean available(ServerSubLevel body) {
    var owner = root(body);
    return !owner.isRemoved() && !isPart(owner);
  }

  public static List<ServerSubLevel> members(ServerSubLevel body) {
    var owner = root(body);
    var result = new ArrayList<ServerSubLevel>();
    result.add(owner);
    var container = SubLevelContainer.getContainer(owner.getLevel());
    if (container == null) return List.copyOf(result);
    for (String key : data(owner).getCompound("Members").getAllKeys())
      try {
        var entry = container.getSubLevel(UUID.fromString(key));
        if (entry instanceof ServerSubLevel part && !part.isRemoved() && data(part).hasUUID("Root") && data(part).getUUID("Root").equals(owner.getUniqueId()))
          result.add(part);
      } catch (IllegalArgumentException ignored) {
      }
    return List.copyOf(result);
  }

  public static double extraHealth(ServerSubLevel root) {
    return finite(data(root).getDouble("Health"));
  }

  public static double extraArmor(ServerSubLevel root) {
    return finite(data(root).getDouble("Armor"));
  }

  private static double finite(double value) {
    return Double.isFinite(value) ? Math.max(0, value) : 0;
  }

  public static void attach(ServerSubLevel owner, ServerSubLevel part) {
    owner = root(owner);
    if (part == owner || part.getLevel() != owner.getLevel() || !available(owner)) return;
    if (isPart(part) && root(part) != owner)
      throw new IllegalArgumentException("Limb belongs to another creature");
    if (SubLevelHealth.blocks(part).stream()
        .anyMatch(
            p ->
                part.getLevel()
                    .getBlockState(p)
                    .is(org.Azkerash.create_bioships.components.ComponentRegistry.CORE.get())))
      throw new IllegalArgumentException("A living core cannot become a limb");
    if (isPart(part) && root(part) == owner) return;
    if (!data(part).getCompound("Members").isEmpty())
      throw new IllegalArgumentException("Cannot attach a creature with its own limbs");
    org.Azkerash.create_bioships.core.brain.behavior.BrainBehaviors.discard(part);
    org.Azkerash.create_bioships.core.brain.BrainMotorControls.stop(part);
    SubLevelHealth.status(owner);
    SubLevelArmor.status(owner);
    double health = SubLevelHealth.localMaximum(part), armor = SubLevelArmor.localMaximum(part);
    var tag = part.getUserDataTag() == null ? new CompoundTag() : part.getUserDataTag().copy();
    var link = tag.getCompound(SubLevelData.PARTS).copy();
    link.putUUID("Root", owner.getUniqueId());
    tag.put(SubLevelData.PARTS, link);
    tag.putBoolean(SubLevelData.LIVING, true);
    tag.putString(CoreAssemblyRules.TYPE, owner.getUserDataTag().getString(CoreAssemblyRules.TYPE));
    tag.putUUID(CoreAssemblyRules.ID, owner.getUniqueId());
    tag.put(SubLevelData.HEALTH, owner.getUserDataTag().getCompound(SubLevelData.HEALTH).copy());
    tag.put(SubLevelData.STATE, owner.getUserDataTag().getCompound(SubLevelData.STATE).copy());
    part.setUserDataTag(tag);
    contribution(part, "Health", health);
    contribution(part, "Armor", armor);
    org.Azkerash.create_bioships.core.access.SubLevelPermissions.forget(part);
    CreatureDecay.link(owner, part);
    org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.refresh(part);
    SubLevelHealth.refreshCapacity(owner);
  }

  public static int extraLocomotion(ServerSubLevel owner) {
    int mask = 0;
    var entries = data(owner).getCompound("Members");
    for (var key : entries.getAllKeys()) mask |= (int) entries.getCompound(key).getDouble("Locomotion");
    return mask;
  }

  public static void locomotionContribution(ServerSubLevel part, int mask) {
    contribution(part, "Locomotion", mask);
  }

  private static void contribution(ServerSubLevel part, String key, double value) {
    if (!isPart(part) || !available(part)) return;
    var owner = root(part);
    var rootTag = owner.getUserDataTag().copy();
    var group = data(owner).copy();
    var members = group.getCompound("Members").copy();
    String id = part.getUniqueId().toString();
    var record = members.getCompound(id).copy();
    value = finite(value);
    if (record.contains(key) && record.getDouble(key) == value) return;
    record.putDouble(key, value);
    members.put(id, record);
    group.put("Members", members);
    double total = 0;
    for (String member : members.getAllKeys())
      total += finite(members.getCompound(member).getDouble(key));
    group.putDouble(key, total);
    group.putInt("Version", 1);
    rootTag.put(SubLevelData.PARTS, group);
    owner.setUserDataTag(rootTag);
  }

  public static void healthContribution(ServerSubLevel part, double value) {
    contribution(part, "Health", value);
    if (isPart(part) && available(part)) SubLevelHealth.refreshCapacity(root(part));
  }

  public static void armorContribution(ServerSubLevel part, double value) {
    contribution(part, "Armor", value);
  }

  /** Permanent severance removes capacity; the separated material remains a dead woundable body. */
  public static void detach(ServerSubLevel part) {
    if (!isPart(part)) return;
    var owner = root(part);
    var tag = part.getUserDataTag().copy();
    tag.remove(SubLevelData.PARTS);
    part.setUserDataTag(tag);
    if (owner != part) {
      var rootTag = owner.getUserDataTag().copy();
      var group = data(owner).copy();
      var members = group.getCompound("Members").copy();
      members.remove(part.getUniqueId().toString());
      group.put("Members", members);
      for (String key : List.of("Health", "Armor", "Locomotion")) {
        double total = 0;
        for (String id : members.getAllKeys())
          total += finite(members.getCompound(id).getDouble(key));
        group.putDouble(key, total);
      }
      rootTag.put(SubLevelData.PARTS, group);
      owner.setUserDataTag(rootTag);
      SubLevelHealth.refreshCapacity(owner);
    }
    org.Azkerash.create_bioships.core.access.SubLevelPermissions.forget(part);
    org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.refresh(owner);
    double maximum = SubLevelHealth.localMaximum(part);
    SubLevelData.health(part, new SubLevelData.Health(0, maximum, maximum, true, "detached_limb"));
  }

  public static void sync(ServerSubLevel owner, Collection<ServerSubLevel> parts) {
    var desired = Collections.newSetFromMap(new IdentityHashMap<ServerSubLevel, Boolean>());
    desired.addAll(parts);
    for (var part : parts) attach(owner, part);
    for (var old : members(owner)) if (old != owner && !desired.contains(old)) detach(old);
  }

  public static void removed(ServerSubLevel body) {
    if (isPart(body)) detach(body);
    else for (var part : members(body)) if (part != body) detach(part);
  }

  public static void died(ServerSubLevel owner) {
    var source = owner.getUserDataTag();
    for (var part : members(owner))
      if (part != owner) {
        var tag = part.getUserDataTag().copy();
        tag.put(SubLevelData.HEALTH, source.getCompound(SubLevelData.HEALTH).copy());
        tag.put(SubLevelData.STATE, source.getCompound(SubLevelData.STATE).copy());
        part.setUserDataTag(tag);
      }
  }
}
