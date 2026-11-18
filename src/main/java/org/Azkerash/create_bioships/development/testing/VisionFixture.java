package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.vision.SubLevelVision;

/** Fixed subjects in air/water lanes, an occluding wall, and an unassembled control. */
public final class VisionFixture {
  private static final String MARK = "create_bioships:vision_fixture";

  private VisionFixture() {}

  public static BlockPos base(ServerLevel level) {
    return new BlockPos(512, CoreTestState.get(level).layout().base().getY(), 0);
  }

  private static BlockPos subject(ServerLevel level, int lane) {
    return base(level).offset(lane == 0 ? -5 : 5, 2, 0);
  }

  private static List<BlockPos> cells(BlockPos core) {
    return List.of(core, core.west(), core.east(), core.west().north(), core.east().north());
  }

  private static void subjectBlocks(ServerLevel level, BlockPos core) {
    level.setBlock(core, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    for (var pos : List.of(core.west(), core.east())) {
      level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
      level.setBlock(
          pos.north(),
          (pos.getX() < core.getX() ? ComponentRegistry.LAND_EYE : ComponentRegistry.AQUATIC_EYE)
              .get()
              .defaultBlockState()
              .setValue(BlockStateProperties.FACING, Direction.NORTH),
          3);
    }
  }

  public static void reset(ServerLevel level) {
    var base = base(level);
    for (int x = (base.getX() - 16) >> 4; x <= (base.getX() + 16) >> 4; x++)
      for (int z = -2; z <= 1; z++) level.setChunkForced(x, z, true);
    var container = SubLevelContainer.getContainer(level);
    for (var entry : List.copyOf(container.getAllSubLevels()))
      if (entry instanceof ServerSubLevel body
          && body.getUserDataTag() != null
          && body.getUserDataTag().getBoolean(MARK))
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    for (var entity :
        level.getEntitiesOfClass(
            Entity.class, new AABB(base).inflate(25), e -> e.getTags().contains(MARK)))
      entity.discard();
    for (int x = -12; x <= 12; x++)
      for (int z = -12; z <= 9; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        if (Math.abs(x) <= 10 && Math.abs(z) <= 8)
          for (int y = 0; y <= 7; y++) {
            boolean wall = Math.abs(x) == 10 || Math.abs(z) == 8 || y == 7 || x == 0;
            level.setBlock(
                base.offset(x, y, z),
                (wall
                        ? DevelopmentTestBlocks.GLASS.get()
                        : x > 0 && y < 6 ? Blocks.WATER : Blocks.AIR)
                    .defaultBlockState(),
                2);
          }
      }
    // Front-facing land and aquatic eyes in each environment; wall only blocks the land-eye lane.
    for (int lane = 0; lane < 2; lane++) {
      var core = subject(level, lane);
      subjectBlocks(level, core);
      for (int y = 1; y <= 3; y++)
        level.setBlock(core.offset(-1, y - 2, -4), Blocks.BEDROCK.defaultBlockState(), 2);
    }
    level.setBlock(
        base.offset(-8, 0, 5),
        ComponentRegistry.LAND_EYE
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.UP),
        3);
    level.setBlock(base.offset(-8, -1, 5), Blocks.BEDROCK.defaultBlockState(), 3);
    for (int lane = 0; lane < 2; lane++)
      for (int x = 0; x <= 1; x++)
        for (int y = 0; y <= 1; y++)
          level.setBlock(
              base.offset((lane == 0 ? -6 : 4) + x, y, -8),
              DevelopmentTestBlocks.GLASS_DOOR
                  .get()
                  .defaultBlockState()
                  .setValue(DoorBlock.FACING, Direction.NORTH)
                  .setValue(DoorBlock.HALF, y == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                  .setValue(DoorBlock.HINGE, x == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT),
              3);
    control(level, base.offset(-3, 0, -10), "vision_test start");
    control(level, base.offset(3, 0, -10), "vision_test reset");
    CoreTestState.get(level).visionBuilt = true;
    CoreTestState.get(level).setDirty();
    explain(level, "Ready: unassembled eyes cast no rays");
  }

  private static void control(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    var be = (CommandBlockEntity) level.getBlockEntity(pos);
    be.getCommandBlock().setCommand(command);
    be.getCommandBlock().setTrackOutput(false);
    be.setChanged();
    level.setBlock(
        pos.above(),
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
        3);
  }

  public static int start(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    for (var entry : container.getAllSubLevels())
      if (entry instanceof ServerSubLevel body
          && body.getUserDataTag() != null
          && body.getUserDataTag().getBoolean(MARK)) return 0;
    if (!CoreTestState.get(level).visionBuilt) reset(level);
    for (int lane = 0; lane < 2; lane++) {
      var origin = subject(level, lane);
      var body =
          SubLevelAssemblyHelper.assembleBlocks(
              level,
              origin,
              cells(origin),
              new BoundingBox3i(origin.west().north(), origin.east()));
      if (body == null) {
        reset(level);
        return 0;
      }
      CoreAssemblyRules.changed(level, body.getPlot().getCenterBlock());
      var tag = body.getUserDataTag().copy();
      tag.putBoolean(MARK, true);
      body.setUserDataTag(tag);
      SubLevelData.hungerPaused(body, true);
      DevelopmentTestOwnership.assign(body);
      container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    }
    explain(level, "Running: F3+V shows actual cached collision rays");
    return 1;
  }

  public static String status(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    int grids = 0, eyes = 0;
    for (var entry : container.getAllSubLevels())
      if (entry instanceof ServerSubLevel body
          && body.getUserDataTag() != null
          && body.getUserDataTag().getBoolean(MARK)) {
        grids++;
        eyes += SubLevelVision.sights(body).size();
      }
    return "Vision fixture: "
        + grids
        + " assembled subjects, "
        + eyes
        + " cached eye cones. F3+V toggles rays.";
  }

  private static void explain(ServerLevel level, String result) {
    var base = base(level);
    for (var old :
        level.getEntitiesOfClass(
            Display.TextDisplay.class, new AABB(base).inflate(25), e -> e.getTags().contains(MARK)))
      old.discard();
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display == null) return;
    var tag = new CompoundTag();
    tag.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "VISION REGRESSION\n"
                    + "Left: START · Right: RESET\n"
                    + "Left chamber: air · Right: water\n"
                    + "Each: core + land eye (left) + aquatic eye (right)\n"
                    + "Wall blocks left eye; floor eye remains unassembled.\n"
                    + "F3+V: cyan clear · red blocked · yellow unloaded\n"
                    + "Use /time set day or night to compare range.\n"
                    + result),
            level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putInt("line_width", 360);
    tag.putInt("background", 0x70000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(Vec3.atLowerCornerOf(base).add(.5, 1.62, -9.5));
    display.addTag(MARK);
    level.addFreshEntity(display);
  }
}
