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
import net.minecraft.world.phys.AABB;
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
  private static final String MARKER = "BioshipEcosystemMob";
  static final int RADIUS = 64, PREY_CAP = 12, HOSTILE_CAP = 4;

  static final class State extends SavedData {
    boolean built;
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
                    s.center = BlockPos.of(tag.getLong("Center"));
                    for (long p : tag.getLongArray("Caches")) s.caches.add(BlockPos.of(p));
                    return s;
                  }),
              "bioship_ecosystem_lab");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      tag.putBoolean("Built", built);
      tag.putLong("Center", center.asLong());
      tag.putLongArray("Caches", caches.stream().mapToLong(BlockPos::asLong).toArray());
      return tag;
    }
  }

  static boolean lab(ServerLevel level) {
    return level == level.getServer().overworld()
        && level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
            .anyMatch(b -> b.is(BIOME));
  }

  @SubscribeEvent
  public static void start(ServerStartedEvent event) {
    var level = event.getServer().overworld();
    if (lab(level) && !State.get(level).built) prepare(level, level.getSharedSpawnPos());
  }

  static void prepare(ServerLevel level, BlockPos center) {
    var state = State.get(level);
    if (state.built) return;
    state.center = center.immutable();
    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ()) + 40;
    for (int x = -RADIUS; x <= RADIUS; x++)
      for (int z = -RADIUS; z <= RADIUS; z++) {
        if (Math.abs(x) != RADIUS && Math.abs(z) != RADIUS) continue;
        int bottom = level.getMinBuildHeight();
        for (int y = bottom; y < top; y++)
          level.setBlock(
              new BlockPos(center.getX() + x, y, center.getZ() + z),
              Blocks.BARRIER.defaultBlockState(),
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
    state.setDirty();
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
    AquaticLab.tick(level);
    long now = level.getGameTime();
    if (now % 600 == 0) replenish(level, state, false);
    if (now % 1200 == 0) replenish(level, state, true);
  }

  static void sunProtection(Mob mob) {
    if (mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
      mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
      mob.setDropChance(EquipmentSlot.HEAD, 0);
    }
  }

  private static void replenish(ServerLevel level, State state, boolean hostile) {
    if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING))
      return;
    var bounds = new AABB(state.center).inflate(RADIUS, 40, RADIUS);
    long count =
        level
            .getEntitiesOfClass(
                Mob.class,
                bounds,
                m ->
                    hostile
                        ? m instanceof net.minecraft.world.entity.monster.Monster
                        : m instanceof net.minecraft.world.entity.animal.Animal)
            .size();
    if (count >= (hostile ? HOSTILE_CAP : PREY_CAP)) return;
    List<EntityType<? extends Mob>> choices =
        hostile
            ? List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER)
            : List.of(EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN);
    var type = choices.get(level.random.nextInt(choices.size()));
    for (int i = 0; i < 16; i++) {
      int x = state.center.getX() + level.random.nextInt(100) - 50,
          z = state.center.getZ() + level.random.nextInt(100) - 50;
      if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
      var pos =
          level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
      if (hostile && level.canSeeSky(pos)) {
        for (int y = pos.getY() - 1; y > pos.getY() - 40; y--) {
          var cave = new BlockPos(x, y, z);
          if (level.getBlockState(cave).isAir()
              && level.getBlockState(cave.above()).isAir()
              && level.getBlockState(cave.below()).isFaceSturdy(level, cave.below(), Direction.UP)
              && !level.canSeeSky(cave)) {
            pos = cave;
            break;
          }
        }
      }
      if (level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 24, false) != null) continue;
      if (!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, level.random))
        continue;
      var mob = type.create(level);
      if (mob == null) return;
      mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, level.random.nextFloat() * 360, 0);
      if (!level.noCollision(mob, mob.getBoundingBox())) continue;
      net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(
          mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
      sunProtection(mob);
      mob.getPersistentData().putBoolean(MARKER, true);
      level.addFreshEntity(mob);
      return;
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
                    net.minecraft.commands.Commands.literal("water")
                        .executes(
                            ctx -> {
                              var level = ctx.getSource().getLevel();
                              if (!lab(level)) return 0;
                              AquaticLab.build(level, State.get(level).center);
                              var pos = AquaticLab.center(level).offset(-17, 2, 0);
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
                              ctx.getSource()
                                  .sendSuccess(
                                      () ->
                                          net.minecraft.network.chat.Component.literal(
                                              "Ecosystem Lab center "
                                                  + state.center
                                                  + ", radius 64, caches "
                                                  + state.caches.size()
                                                  + ", prey cap 12, hostile cap 4. Natural spawning"
                                                  + " checks apply."),
                                      false);
                              return 1;
                            })));
  }
}
