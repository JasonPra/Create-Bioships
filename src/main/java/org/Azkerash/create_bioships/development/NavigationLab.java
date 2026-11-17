package org.Azkerash.create_bioships.development;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.Azkerash.create_bioships.DigestiveSystem;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Shared deterministic course geometry for the shipped developer world and real-physics tests. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class NavigationLab {
  private static final ResourceKey<Biome> BIOME =
      ResourceKey.create(
          Registries.BIOME,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "navigation_lab"));
  private static final BlockPos FLAT = new BlockPos(16, 80, 0),
      ELEVATED = new BlockPos(16, 80, 128);

  public record Course(
      Map<BlockPos, BlockState> blocks,
      Set<BlockPos> glass,
      Set<BlockPos> grass,
      BlockPos prize,
      int[] gates) {}

  private NavigationLab() {}

  public static Course course(BlockPos origin, boolean elevated) {
    var blocks = new LinkedHashMap<BlockPos, BlockState>();
    var glass = new LinkedHashSet<BlockPos>();
    var grass = new LinkedHashSet<BlockPos>();
    int end = elevated ? 23 : 20, width = elevated ? 10 : 8;
    int[] gates = elevated ? new int[] {5, 11, 17} : new int[] {4, 9, 14};
    for (int x = -2; x <= end; x++)
      for (int z = -width; z <= width; z++) {
        int floor = elevated ? (x >= 17 ? 3 : x >= 11 ? 2 : x >= 3 ? 1 : 0) : 0;
        for (int y = -1; y < floor; y++)
          blocks.put(
              origin.offset(x, y, z),
              (y == floor - 1 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState());
        int gap = elevated ? 5 : 4;
        boolean baffle = (x == gates[0] || x == gates[2]) && z <= gap || x == gates[1] && z >= -gap;
        for (int y = floor; y <= floor + 4; y++)
          if (x == -2 || x == end || z == -width || z == width || baffle || y == floor + 4) {
            var p = origin.offset(x, y, z);
            glass.add(p);
            blocks.put(p, Blocks.GLASS.defaultBlockState());
          }
        if (elevated
            && x > 1
            && x < end - 1
            && Math.abs(z) < width
            && Math.floorMod(x * 7 + z, 4) == 0) {
          var p = origin.offset(x, floor, z);
          if (!blocks.containsKey(p) && !blocks.containsKey(p.above())) {
            blocks.put(p, Blocks.TALL_GRASS.defaultBlockState());
            blocks.put(
                p.above(),
                Blocks.TALL_GRASS
                    .defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            grass.add(p);
          }
        }
      }
    var prize = origin.offset(elevated ? 21 : 18, elevated ? 5 : 0, 0);
    if (elevated)
      for (int y = 3; y < 5; y++) {
        var p = origin.offset(21, y, 0);
        blocks.put(p, (y == 4 ? Blocks.GLOWSTONE : Blocks.POLISHED_ANDESITE).defaultBlockState());
        grass.remove(p);
        grass.remove(p.below());
      }
    blocks.remove(prize);
    blocks.remove(prize.above());
    grass.remove(prize);
    grass.remove(prize.below());
    return new Course(
        Collections.unmodifiableMap(blocks), Set.copyOf(glass), Set.copyOf(grass), prize, gates);
  }

  public static void place(ServerLevel level, Course course) {
    for (var entry : course.blocks().entrySet()) {
      var p = entry.getKey();
      level.getChunk(p.getX() >> 4, p.getZ() >> 4);
      level.setBlock(p, entry.getValue(), 3);
    }
  }

  static boolean lab(ServerLevel level) {
    return level == level.getServer().overworld()
        && level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
            .anyMatch(holder -> holder.is(BIOME));
  }

  private static final class State extends SavedData {
    boolean built, seeded;

    public static State get(ServerLevel level) {
      return level
          .getDataStorage()
          .computeIfAbsent(
              new Factory<>(
                  State::new,
                  (tag, registries) -> {
                    var value = new State();
                    value.built = tag.getBoolean("Built");
                    value.seeded = tag.getBoolean("Seeded");
                    return value;
                  }),
              "create_bioships_navigation_lab");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putBoolean("Built", built);
      tag.putBoolean("Seeded", seeded);
      return tag;
    }
  }

  private static void build(ServerLevel level) {
    for (int x = -16; x <= 64; x++)
      for (int z = -32; z <= 160; z++)
        level.setBlock(new BlockPos(x, 79, z), Blocks.BEDROCK.defaultBlockState(), 2);
    for (var origin : List.of(FLAT, ELEVATED)) {
      for (var p : BlockPos.betweenClosed(origin.offset(-3, 0, -11), origin.offset(24, 11, 11)))
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
      var course = course(origin, origin.equals(ELEVATED));
      place(level, course);
      level.setBlock(
          course.prize(),
          net.minecraft.core.registries.BuiltInRegistries.BLOCK
              .get(ResourceLocation.parse("biomancy:bio_forge"))
              .defaultBlockState(),
          3);
    }
    level.setDefaultSpawnPos(new BlockPos(8, 80, -12), 0);
    level.setDayTime(1000);
    var state = State.get(level);
    state.built = true;
    state.seeded = false;
    state.setDirty();
    com.mojang.logging.LogUtils.getLogger()
        .info(
            "Bioship Navigation Lab built: flat course at {}, elevation course at {}",
            FLAT,
            ELEVATED);
  }

  private static void seed(ServerLevel level, UUID owner) {
    for (var origin : List.of(FLAT, ELEVATED)) {
      level.setBlock(origin, Create_bioships.CORE.get().defaultBlockState(), 3);
      var ship =
          SubLevelAssemblyHelper.assembleBlocks(
              level, origin, List.of(origin), new BoundingBox3i(origin, origin));
      if (ship == null) throw new IllegalStateException("Cannot create navigation lab hatchling");
      var data = new CompoundTag();
      data.putBoolean("BioshipNavigationLab", true);
      ship.setUserDataTag(data);
      for (var actor : ship.getPlot().getBlockEntityActors())
        if (actor instanceof LivingCoreBlockEntity core) {
          core.claimCreator(owner);
          core.chunkLoading().update(core);
          if (origin.equals(ELEVATED)) {
            level.setBlock(
                core.getBlockPos().north(),
                Create_bioships.FEEDING_MOUTH.get().defaultBlockState(),
                3);
            level.setBlock(
                core.getBlockPos().west(), Create_bioships.STOMACH.get().defaultBlockState(), 3);
            level.setBlock(
                core.getBlockPos().south(),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(ResourceLocation.parse("biomancy:bio_lab"))
                    .defaultBlockState(),
                3);
            DigestiveSystem.connect(core, ship);
          }
          break;
        }
    }
    var state = State.get(level);
    state.seeded = true;
    state.setDirty();
  }

  @SubscribeEvent
  public static void started(ServerStartedEvent event) {
    var level = event.getServer().overworld();
    if (lab(level) && !State.get(level).built) build(level);
  }

  @SubscribeEvent
  public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
        && lab(player.serverLevel())
        && !State.get(player.serverLevel()).seeded) {
      player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      player.setItemSlot(
          net.minecraft.world.entity.EquipmentSlot.HEAD,
          new net.minecraft.world.item.ItemStack(Create_bioships.BIOSHIP_GOGGLES.get()));
      player
          .getInventory()
          .add(new net.minecraft.world.item.ItemStack(Create_bioships.BIOSHIP_WRENCH.get()));
      seed(player.serverLevel(), player.getUUID());
    }
  }

  @SubscribeEvent
  public static void commands(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("bioship_lab")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("reset")
                        .executes(
                            context -> {
                              var source = context.getSource();
                              var level = source.getLevel();
                              if (!lab(level)) {
                                source.sendFailure(
                                    Component.literal(
                                        "This command only resets the Bioship Navigation Lab world"
                                            + " type."));
                                return 0;
                              }
                              var container = SubLevelContainer.getContainer(level);
                              var ids = new HashSet<UUID>();
                              for (var entry : List.copyOf(container.getAllSubLevels()))
                                if (entry instanceof ServerSubLevel ship
                                    && ship.getUserDataTag() != null
                                    && ship.getUserDataTag().getBoolean("BioshipNavigationLab"))
                                  ids.add(ship.getUniqueId());
                              for (var entry : List.copyOf(container.getAllSubLevels()))
                                if (entry instanceof ServerSubLevel ship
                                    && (ids.contains(ship.getUniqueId())
                                        || ship.getUserDataTag() != null
                                            && ship.getUserDataTag().hasUUID("BioshipCargoOwner")
                                            && ids.contains(
                                                ship.getUserDataTag()
                                                    .getUUID("BioshipCargoOwner"))))
                                  container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                              build(level);
                              if (source.getEntity()
                                  instanceof net.minecraft.server.level.ServerPlayer player)
                                seed(level, player.getUUID());
                              source.sendSuccess(
                                  () ->
                                      Component.literal(
                                          "Reset both courses. Flat maze: 16 80 0; elevation maze:"
                                              + " 16 80 128."),
                                  false);
                              return 1;
                            })));
  }
}
