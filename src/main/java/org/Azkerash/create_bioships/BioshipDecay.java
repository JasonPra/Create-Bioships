package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Decay belongs to the physics body so fragments continue to rot without a core. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipDecay {
  static final int GRACE_TICKS = 1200;
  static final int DECAY_INTERVAL = 200;

  static int priority(ServerLevel level, BlockPos pos) {
    var state = level.getBlockState(pos);
    if (state.is(Create_bioships.CORE.get())) return 2;
    if (state.is(Create_bioships.FLESH.get()) || state.is(Create_bioships.CARAPACE.get())) return 0;
    var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
    if (state.hasBlockEntity() || id.getNamespace().equals(Create_bioships.MOD_ID)) return 1;
    if (id.getNamespace().equals("biomancy")
        && !state.is(
            net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.ResourceLocation.parse(
                    "create_bioships:construction_flesh")))) return 1;
    return 0;
  }

  /** Tombstones survive unloading and the disappearance of the last loaded wreck block. */
  static final class Deaths extends net.minecraft.world.level.saveddata.SavedData {
    private final Map<UUID, Long> deadlines = new HashMap<>();

    static Deaths get(ServerLevel level) {
      return level
          .getDataStorage()
          .computeIfAbsent(new Factory<>(Deaths::new, Deaths::load), "create_bioships_deaths");
    }

    static Deaths load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
      var data = new Deaths();
      for (String key : tag.getAllKeys()) {
        try {
          data.deadlines.put(UUID.fromString(key), tag.getLong(key));
        } catch (IllegalArgumentException ignored) {
          /* Ignore an unrecognized saved key. */
        }
      }
      return data;
    }

    void remember(UUID lineage, long after) {
      if (!deadlines.containsKey(lineage) || after < deadlines.get(lineage)) {
        deadlines.put(lineage, after);
        setDirty();
      }
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookup) {
      deadlines.forEach((id, after) -> tag.putLong(id.toString(), after));
      return tag;
    }
  }

  public static boolean dead(ServerSubLevel ship) {
    return ship.getUserDataTag() != null && ship.getUserDataTag().getBoolean("BioshipDead");
  }

  public static UUID lineage(ServerSubLevel ship) {
    var tag = ship.getUserDataTag();
    return tag != null && tag.hasUUID("BioshipLineage")
        ? tag.getUUID("BioshipLineage")
        : ship.getUniqueId();
  }

  public static void link(ServerSubLevel child, ServerSubLevel original) {
    var tag = child.getUserDataTag() == null ? new CompoundTag() : child.getUserDataTag().copy();
    tag.putBoolean("BioshipBound", true);
    tag.putUUID("BioshipLineage", lineage(original));
    child.setUserDataTag(tag);
    inherit(child, original);
  }

  public static void mark(ServerSubLevel ship) {
    if (dead(ship)) return;
    var tag = ship.getUserDataTag() == null ? new CompoundTag() : ship.getUserDataTag().copy();
    tag.putBoolean("BioshipDead", true);
    tag.putUUID("BioshipCorpseRoot", ship.getUniqueId());
    tag.putLong("BioshipDecayAfter", ship.getLevel().getGameTime() + GRACE_TICKS);
    ship.setUserDataTag(tag);
    Deaths.get(ship.getLevel()).remember(lineage(ship), tag.getLong("BioshipDecayAfter"));
    Deaths.get(ship.getLevel()).remember(ship.getUniqueId(), tag.getLong("BioshipDecayAfter"));
    var container = SubLevelContainer.getContainer(ship.getLevel());
    if (container != null)
      for (var body : List.copyOf(container.getAllSubLevels()))
        if (body instanceof ServerSubLevel fragment
            && fragment != ship
            && !dead(fragment)
            && lineage(fragment).equals(lineage(ship))) inherit(fragment, ship);
  }

  public static void inherit(ServerSubLevel child, ServerSubLevel original) {
    if (!dead(original)) return;
    mark(child);
    var tag = child.getUserDataTag().copy();
    var old = original.getUserDataTag();
    if (old.hasUUID("BioshipCorpseRoot"))
      tag.putUUID("BioshipCorpseRoot", old.getUUID("BioshipCorpseRoot"));
    tag.putLong("BioshipDecayAfter", old.getLong("BioshipDecayAfter"));
    child.setUserDataTag(tag);
  }

  static List<BlockPos> blocks(ServerSubLevel ship) {
    return BioshipStructure.positions(ship);
  }

  static boolean recoverLegacyHull(ServerSubLevel part) {
    if (BioshipIntegration.bound(part)
        || part.isRemoved()
        || part.getSplitFromSubLevel() != null
        || part.getUserDataTag() != null && part.getUserDataTag().hasUUID("BioshipCargoOwner"))
      return false;
    int hull = 0;
    boolean machinery = false;
    Set<net.minecraft.world.level.block.Block> tissues = new HashSet<>();
    for (var p : blocks(part)) {
      var state = part.getLevel().getBlockState(p);
      if (state.is(Create_bioships.CORE.get())) return false;
      if (state.is(Create_bioships.FLESH.get())
          || state.is(Create_bioships.CARAPACE.get())
          || state.is(Create_bioships.ONE_WAY_WINDOW.get())) {
        hull++;
        tissues.add(state.getBlock());
      }
      machinery |=
          state.is(Create_bioships.TENTACLE.get())
              || state.is(Create_bioships.BUOYANCY_ORGAN.get());
    }
    if (hull < 8 || tissues.size() < 2 || !machinery) return false;
    BioshipIntegration.bind(part);
    mark(part);
    var data = part.getUserDataTag().copy();
    data.putBoolean("BioshipRecoveredLegacyHull", true);
    part.setUserDataTag(data);
    return true;
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 0) return;
    var container = SubLevelContainer.getContainer(level);
    if (container == null) return;
    var deaths = Deaths.get(level);
    for (var body : List.copyOf(container.getAllSubLevels()))
      if (body instanceof ServerSubLevel part) recoverLegacyHull(part);
    for (var body : List.copyOf(container.getAllSubLevels()))
      if (body instanceof ServerSubLevel corpse && dead(corpse))
        deaths.remember(lineage(corpse), corpse.getUserDataTag().getLong("BioshipDecayAfter"));
    // Upgrade older saved fragments and movement cargo before applying the shared decay schedule.
    for (var body : List.copyOf(container.getAllSubLevels())) {
      if (!(body instanceof ServerSubLevel part) || part.isRemoved()) continue;
      var data = part.getUserDataTag();
      if (data != null && data.hasUUID("BioshipLineage")) continue;
      UUID parentId =
          data != null && data.hasUUID("BioshipCargoOwner")
              ? data.getUUID("BioshipCargoOwner")
              : part.getSplitFromSubLevel();
      var parent = parentId == null ? null : container.getSubLevel(parentId);
      if (parent instanceof ServerSubLevel original && BioshipIntegration.bound(original))
        link(part, original);
      else if (parentId != null && deaths.deadlines.containsKey(parentId)) {
        var inherited = data == null ? new CompoundTag() : data.copy();
        inherited.putBoolean("BioshipBound", true);
        inherited.putBoolean("BioshipDead", true);
        inherited.putUUID("BioshipLineage", parentId);
        inherited.putUUID("BioshipCorpseRoot", parentId);
        inherited.putLong("BioshipDecayAfter", deaths.deadlines.get(parentId));
        part.setUserDataTag(inherited);
      } else if (BioshipIntegration.blockEntities(part).stream()
          .anyMatch(e -> e instanceof LivingCoreBlockEntity)) BioshipIntegration.bind(part);
      else if (parentId == null && BioshipIntegration.bound(part)) mark(part);
    }
    for (var body : List.copyOf(container.getAllSubLevels())) {
      if (!(body instanceof ServerSubLevel part)
          || dead(part)
          || !deaths.deadlines.containsKey(lineage(part))) continue;
      var data = part.getUserDataTag() == null ? new CompoundTag() : part.getUserDataTag().copy();
      data.putBoolean("BioshipDead", true);
      data.putUUID("BioshipCorpseRoot", lineage(part));
      data.putLong("BioshipDecayAfter", deaths.deadlines.get(lineage(part)));
      part.setUserDataTag(data);
    }
    if (level.getGameTime() % DECAY_INTERVAL == 0) decay(level);
  }

  /** One slow pulse; shared lineage keeps organs safe while any loaded hull remains. */
  static void decay(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    if (container == null) return;
    Map<UUID, List<BlockPos>> wrecks = new HashMap<>();
    for (var body : List.copyOf(container.getAllSubLevels()))
      if (body instanceof ServerSubLevel ship && !ship.isRemoved() && dead(ship))
        wrecks.computeIfAbsent(lineage(ship), ignored -> new ArrayList<>()).addAll(blocks(ship));
    for (var entry : wrecks.entrySet()) {
      var cells = entry.getValue();
      int phase = cells.stream().mapToInt(p -> priority(level, p)).min().orElse(2);
      cells.removeIf(
          p ->
              priority(level, p) != phase
                  || level.getGameTime()
                      < BioshipIntegration.containing(level, p)
                          .getUserDataTag()
                          .getLong("BioshipDecayAfter"));
      Collections.shuffle(cells, new Random(level.getGameTime() ^ entry.getKey().hashCode()));
      int amount = Math.min(cells.size(), 1 + entry.getValue().size() / 256);
      for (int i = 0; i < amount; i++) level.destroyBlock(cells.get(i), false);
    }
  }
}
