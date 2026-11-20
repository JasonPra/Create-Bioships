package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Static flooded catalogue, deliberately without START/RESET or per-sample ticking. */
public final class WaterloggingFixture {
  public static final int COLUMNS = 10, SPACING = 4, HALF_WIDTH = 22, HEIGHT = 8;
  private static final String MARK = "create_bioships:waterlogging_fixture";

  private WaterloggingFixture() {}

  public static BlockPos base(ServerLevel level) {
    return new BlockPos(1408, CoreTestState.get(level).layout().base().getY(), 256);
  }

  public static List<Block> catalog() {
    return BuiltInRegistries.BLOCK.stream()
        .filter(
            b -> {
              var id = BuiltInRegistries.BLOCK.getKey(b);
              return (id.getNamespace().equals("create_bioships")
                      || id.getNamespace().equals("biomancy"))
                  && b.asItem() != Items.AIR;
            })
        .sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()))
        .toList();
  }

  public static BlockPos sample(ServerLevel level, int index) {
    return base(level).offset(-18 + (index % COLUMNS) * SPACING, 1, (index / COLUMNS) * SPACING);
  }

  public static void install(ServerLevel level) {
    var entries = catalog();
    int back = ((entries.size() + COLUMNS - 1) / COLUMNS) * SPACING + 3;
    var base = base(level);
    for (int cx = (base.getX() - HALF_WIDTH) >> 4; cx <= (base.getX() + HALF_WIDTH) >> 4; cx++) {
      for (int cz = (base.getZ() - 4) >> 4; cz <= (base.getZ() + back) >> 4; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
      for (int z = -4; z <= back; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 0; y <= HEIGHT; y++) {
          level.setBlock(
              base.offset(x, y, z),
              (Math.abs(x) == HALF_WIDTH || z == -4 || z == back || y == HEIGHT
                      ? DevelopmentTestBlocks.GLASS.get()
                      : y <= 5 ? Blocks.WATER : Blocks.AIR)
                  .defaultBlockState(),
              2);
        }
      }
    }
    for (int x = 0; x <= 1; x++) {
      for (var half : DoubleBlockHalf.values()) {
        level.setBlock(
            base.offset(x, half == DoubleBlockHalf.LOWER ? 0 : 1, -4),
            DevelopmentTestBlocks.GLASS_DOOR
                .get()
                .defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, x == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                .setValue(DoorBlock.HALF, half),
            3);
      }
    }
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WaterloggingCatalog"));
    int unsupported = 0;
    for (int i = 0; i < entries.size(); i++) {
      var block = entries.get(i);
      var pos = sample(level, i);
      var flesh =
          com.github.elenterius.biomancy.init.ModBlocks.PACKED_FLESH.get().defaultBlockState();
      level.setBlock(pos.below(), flesh, 3);
      level.setBlock(pos.south(), flesh, 3);
      var context =
          new BlockPlaceContext(
              level,
              player,
              net.minecraft.world.InteractionHand.MAIN_HAND,
              new ItemStack(block),
              new BlockHitResult(
                  Vec3.atCenterOf(pos.south()), Direction.NORTH, pos.south(), false));
      BlockState state = null;
      try {
        state = block.getStateForPlacement(context);
      } catch (RuntimeException ignored) {
      }
      if (state == null) {
        state = block.defaultBlockState();
      }
      if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
        state = state.setValue(BlockStateProperties.WATERLOGGED, true);
      }
      for (var support :
          List.of(
              flesh,
              Blocks.DIRT.defaultBlockState(),
              Blocks.MYCELIUM.defaultBlockState(),
              Blocks.SOUL_SOIL.defaultBlockState())) {
        level.setBlock(pos.below(), support, 3);
        if (state.canSurvive(level, pos)) {
          break;
        }
      }
      boolean valid = state.canSurvive(level, pos);
      if (!valid) {
        unsupported++;
      }
      level.setBlock(pos, state, 3);
      if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
          && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
        level.setBlock(
            pos.above(),
            state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
            3);
      }
      label(
          level,
          Vec3.atCenterOf(pos).add(0, 1.3, 0),
          BuiltInRegistries.BLOCK.getKey(block).toString()
              + (valid ? "" : " [special support required]"),
          180);
    }
    label(
        level,
        Vec3.atLowerCornerOf(base).add(.5, 1.8, -6),
        "WATERLOGGING CATALOGUE\nStatic flooded exhibit · no START/RESET\n"
            + entries.size()
            + " Biomancy and Bioship block samples\n"
            + "Swim in and inspect waterlogging, attachment and wash-away behavior.\n"
            + "Every sample is labeled; empty spots reveal washed-away samples.\n"
            + "Some plant/multipart blocks require special placement support.",
        350);
    var saved = CoreTestState.get(level);
    saved.setWaterloggingBuilt(true);
    saved.setWaterloggingCatalogSize(entries.size());
    saved.setDirty();
  }

  private static void label(ServerLevel level, Vec3 at, String text, int width) {
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display == null) {
      return;
    }
    var tag = new CompoundTag();
    tag.putString(
        "text", Component.Serializer.toJson(Component.literal(text), level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putInt("line_width", width);
    tag.putInt("background", 0x66000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(at);
    display.addTag(MARK);
    level.addFreshEntity(display);
  }
}
