package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bounded normal-terrain test ecosystem; leaves interior terrain and ordinary world rules intact.
 */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class EcosystemLab {
  private static final ResourceKey<net.minecraft.world.level.biome.Biome> BIOME =
      ResourceKey.create(
          Registries.BIOME,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "ecosystem_lab"));
  static final int RADIUS = 64, PREY_CAP = 12, HOSTILE_CAP = 4;

  static final class State extends SavedData {
    boolean built;
    int version;
    long nextRefill;
    final List<BlockPos> starters = new ArrayList<>();
    BlockPos center = BlockPos.ZERO;
    final List<BlockPos> caches = new ArrayList<>();

    static State get(ServerLevel level) {
      return level
          .getDataStorage()
          .computeIfAbsent(
              new Factory<>(
                  State::new,
                  (tag, lookup) -> {
                    var s = new State();
                    s.built = tag.getBoolean("Built");
                    s.version = tag.getInt("Version");
                    s.nextRefill = tag.getLong("NextRefill");
                    for (long p : tag.getLongArray("Starters")) s.starters.add(BlockPos.of(p));
                    s.center = BlockPos.of(tag.getLong("Center"));
                    for (long p : tag.getLongArray("Caches")) s.caches.add(BlockPos.of(p));
                    return s;
                  }),
              "bioship_ecosystem_lab");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      tag.putBoolean("Built", built);
      tag.putInt("Version", version);
      tag.putLong("NextRefill", nextRefill);
      tag.putLongArray("Starters", starters.stream().mapToLong(BlockPos::asLong).toArray());
      tag.putLong("Center", center.asLong());
      tag.putLongArray("Caches", caches.stream().mapToLong(BlockPos::asLong).toArray());
      return tag;
    }
  }

  static boolean lab(ServerLevel level) {
    return level == level.getServer().overworld()
        && (NavigationLab.lab(level)
            || level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .anyMatch(b -> b.is(BIOME)));
  }

  @SubscribeEvent
  public static void start(ServerStartedEvent event) {
    var level = event.getServer().overworld();
    if (lab(level))
      prepare(
          level, NavigationLab.lab(level) ? new BlockPos(0, 80, 320) : level.getSharedSpawnPos());
  }

  static void prepare(ServerLevel level, BlockPos center) {
    var state = State.get(level);
    if (state.built) {
      if (state.version < 2) installFixtures(level, state);
      if (state.version < 3) {
        LabPopulation.install(level, state.center);
        state.version = 3;
        state.setDirty();
      }
      AquaticLab.build(level, state.center);
      return;
    }
    state.center = center.immutable();
    if (NavigationLab.lab(level)) LabHabitatTerrain.land(level, center);
    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ()) + 40;
    for (int x = -RADIUS; x <= RADIUS; x++)
      for (int z = -RADIUS; z <= RADIUS; z++) {
        if (Math.abs(x) != RADIUS && Math.abs(z) != RADIUS) continue;
        int bottom = NavigationLab.lab(level) ? center.getY() - 12 : level.getMinBuildHeight();
        for (int y = bottom; y < top; y++)
          level.setBlock(
              new BlockPos(center.getX() + x, y, center.getZ() + z),
              (NavigationLab.lab(level) ? Blocks.GLASS : Blocks.BARRIER).defaultBlockState(),
              2);
      }
    for (int i = 0; i < 12; i++) {
      int x = center.getX() - 44 + (i % 4) * 28, z = center.getZ() - 42 + (i / 4) * 40;
      var surface =
          level.getHeightmapPos(
              i % 3 == 0
                  ? Heightmap.Types.MOTION_BLOCKING
                  : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
              new BlockPos(x, 0, z));
      var pos = habitat(level, surface, i % 3);
      if (pos == null) continue;
      if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos).canBeReplaced())
        continue;
      level.setBlock(
          pos,
          BuiltInRegistries.BLOCK
              .get(ResourceLocation.parse("biomancy:storage_sac"))
              .defaultBlockState(),
          3);
      var inventory =
          level.getCapability(
              net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
      if (inventory != null) {
        net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
            inventory, new ItemStack(Items.PORKCHOP, 64), false);
        for (var id : List.of("flesh_bits", "elastic_fibers", "bone_fragments"))
          net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
              inventory,
              new ItemStack(
                  BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:" + id)), 32),
              false);
      }
      state.caches.add(pos.immutable());
      System.out.println(
          "ECOSYSTEM cache="
              + pos
              + " sky="
              + level.canSeeSky(pos)
              + " light="
              + level.getMaxLocalRawBrightness(pos));
    }
    AquaticLab.build(level, center);
    state.built = true;
    installFixtures(level, state);
    state.setDirty();
  }

  private static void installFixtures(ServerLevel level, State state) {
    LabPopulation.install(level, state.center);
    LabStarters.seed(level, state);
    LabPopulation.prime(level, state.center);
    state.version = 3;
    state.setDirty();
  }

  static void reset(ServerLevel level) {
    var state = State.get(level);
    LabStarters.clear(level, state);
    for (var mob :
        level.getEntitiesOfClass(Mob.class, LabPopulation.landBounds(level, state.center)))
      if (mob.getPersistentData().getBoolean(LabPopulation.MARKER)) mob.discard();
    for (var pos : state.caches) level.removeBlock(pos, false);
    state.caches.clear();
    AquaticLab.reset(level, state.center);
    var center = state.center;
    state.built = false;
    state.nextRefill = 0;
    prepare(level, center);
  }

  static BlockPos habitat(ServerLevel level, BlockPos base, int kind) {
    // Use real habitat features. Absence is reported by a skipped cache, never fabricated terrain.
    for (int radius = 0; radius <= 12; radius += 2)
      for (int dx = -radius; dx <= radius; dx += 2)
        for (int dz = -radius; dz <= radius; dz += 2) {
          int x = base.getX() + dx, z = base.getZ() + dz;
          var top =
              level.getHeightmapPos(
                  kind == 0
                      ? Heightmap.Types.MOTION_BLOCKING
                      : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                  new BlockPos(x, 0, z));
          if (kind == 0
              && level.getBlockState(top.below()).is(net.minecraft.tags.BlockTags.LEAVES)
              && level.getBlockState(top).canBeReplaced()) return top;
          if (kind == 1
              && (level.getBlockState(top).is(Blocks.SHORT_GRASS)
                  || level.getBlockState(top).is(Blocks.TALL_GRASS))) return top;
          if (kind == 2)
            for (int y = top.getY() - 1; y >= top.getY() - 24; y--) {
              var cave = new BlockPos(x, y, z);
              if (level.getBlockState(cave).isAir()
                  && level.getBlockState(cave.above()).isAir()
                  && level
                      .getBlockState(cave.below())
                      .isFaceSturdy(level, cave.below(), Direction.UP)
                  && !level.canSeeSky(cave)) return cave;
            }
        }
    // Still provide ordinary ground caches if this patch has no matching feature.
    return level.getBlockState(base).canBeReplaced() ? base : null;
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    var level = event.getServer().overworld();
    if (!lab(level) && !Boolean.getBoolean("bioships.wildProbe")) return;
    var state = State.get(level);
    if (!state.built) return;
    // Dedicated servers commonly disable command blocks. Keep the same capped controller
    // usable there; the per-tick gate prevents duplicate execution if both paths run.
    if (!event.getServer().isCommandBlockEnabled()) LabPopulation.pulse(level, state);
    if (LabPopulation.refillDue(state, level.getGameTime())) LabPopulation.replenish(level, state);
  }

  static void sunProtection(Mob mob) {
    if (mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
      mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
      mob.setDropChance(EquipmentSlot.HEAD, 0);
    }
  }

  @SubscribeEvent
  public static void join(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
        || !lab(player.serverLevel())) return;
    if (player.getPersistentData().getBoolean("BioshipEcosystemKit")) return;
    player.getPersistentData().putBoolean("BioshipEcosystemKit", true);
    player.getInventory().add(new ItemStack(Create_bioships.CORE_ITEM.get()));
    for (var id : List.of("bio_forge", "decomposer", "digester"))
      player
          .getInventory()
          .add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:" + id))));
    for (int i = 0; i < 18; i++) player.getInventory().add(new ItemStack(Items.PORKCHOP, 64));
    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Create_bioships.BIOSHIP_GOGGLES.get()));
    player.getInventory().add(new ItemStack(Create_bioships.BIOSHIP_WRENCH.get()));
    player.sendSystemMessage(
        net.minecraft.network.chat.Component.literal(
            "Ecosystem Lab: quarantined natural terrain, hidden caches, sparse hostiles and"
                + " replenishing prey. Use /bioship_ecosystem status."));
  }

  @SubscribeEvent
  public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            net.minecraft.commands.Commands.literal("bioship_ecosystem")
                .requires(s -> s.hasPermission(2))
                .then(
                    net.minecraft.commands.Commands.literal("reset")
                        .executes(
                            ctx -> {
                              var level = ctx.getSource().getLevel();
                              if (!lab(level)) return 0;
                              reset(level);
                              ctx.getSource()
                                  .sendSuccess(
                                      () ->
                                          net.minecraft.network.chat.Component.literal(
                                              "Reset ecosystem fixtures, populations, water"
                                                  + " enclosure and three starter cores. Natural"
                                                  + " terrain is retained."),
                                      false);
                              return 1;
                            }))
                .then(
                    net.minecraft.commands.Commands.literal("replenish")
                        .executes(
                            ctx -> {
                              var level = ctx.getSource().getLevel();
                              if (!lab(level)) return 0;
                              LabPopulation.replenish(level, State.get(level));
                              return 1;
                            }))
                .then(
                    net.minecraft.commands.Commands.literal("pulse")
                        .executes(
                            ctx -> {
                              var level = ctx.getSource().getLevel();
                              if (!lab(level)) return 0;
                              LabPopulation.pulse(level, State.get(level));
                              return 1;
                            }))
                .then(
                    net.minecraft.commands.Commands.literal("water")
                        .executes(
                            ctx -> {
                              var level = ctx.getSource().getLevel();
                              if (!lab(level)) return 0;
                              AquaticLab.build(level, State.get(level).center);
                              var pos = AquaticLab.viewpoint(level);
                              if (ctx.getSource().getEntity()
                                  instanceof net.minecraft.server.level.ServerPlayer player)
                                player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                              return 1;
                            }))
                .then(
                    net.minecraft.commands.Commands.literal("status")
                        .executes(
                            ctx -> {
                              var level = ctx.getSource().getLevel();
                              if (!lab(level)) return 0;
                              var state = State.get(level);
                              var counts = LabPopulation.counts(level, state.center);
                              ctx.getSource()
                                  .sendSuccess(
                                      () ->
                                          net.minecraft.network.chat.Component.literal(
                                              "Ecosystem Lab center "
                                                  + state.center
                                                  + ", radius 64, caches "
                                                  + state.caches.size()
                                                  + ", living starters "
                                                  + counts.cores()
                                                  + ", fish "
                                                  + counts.fish()
                                                  + "/16"
                                                  + ", prey "
                                                  + counts.prey()
                                                  + "/12"
                                                  + ", hostiles "
                                                  + counts.hostiles()
                                                  + "/4, prey cap 12, hostile cap 4, fish cap 16."
                                                  + " Controller "
                                                  + LabPopulation.controller(state.center)
                                                  + ". Capped command spawning; natural spawning"
                                                  + " remains enabled. Full refill every five"
                                                  + " in-game minutes; next in "
                                                  + Math.max(
                                                          0, state.nextRefill - level.getGameTime())
                                                      / 20
                                                  + " seconds."),
                                      false);
                              return 1;
                            })));
  }
}
