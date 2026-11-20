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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** Repeatable full-brain storage, wrench-port and mouth-chewing demonstration. */
public final class BrainFixture {
  private static final String MARK = "create_bioships:brain_fixture";

  private BrainFixture() {}

  public static BlockPos base(ServerLevel level) {
    return new BlockPos(768, CoreTestState.get(level).layout().base().getY(), 0);
  }

  public static BlockPos core(ServerLevel level) {
    return base(level).offset(-1, 1, 0);
  }

  private static List<BlockPos> cells(ServerLevel level) {
    var cells = new ArrayList<BlockPos>();
    var base = base(level);
    for (int x = 0; x < 3; x++) {
      for (int y = 1; y <= 3; y++) {
        for (int z = 0; z < 3; z++) {
          cells.add(base.offset(x, y, z));
        }
      }
    }
    cells.add(core(level));
    cells.add(core(level).north());
    return cells;
  }

  public static ServerSubLevel body(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    if (container != null) {
      for (var entry : container.getAllSubLevels()) {
        if (entry instanceof ServerSubLevel body
            && !body.isRemoved()
            && body.getUserDataTag() != null
            && body.getUserDataTag().getBoolean(MARK)) {
          return body;
        }
      }
    }
    return null;
  }

  public static void reset(ServerLevel level) {
    var base = base(level);
    for (int x = (base.getX() - 10) >> 4; x <= (base.getX() + 10) >> 4; x++) {
      for (int z = -1; z <= 0; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    var body = body(level);
    if (body != null) {
      SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
    for (var e :
        level.getEntitiesOfClass(
            Entity.class, new AABB(base).inflate(20), e -> e.getTags().contains(MARK))) {
      e.discard();
    }
    for (int x = -8; x <= 8; x++) {
      for (int z = -9; z <= 7; z++) {
        level.setBlock(
            base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (Math.abs(x) <= 6 && Math.abs(z) <= 6) {
          for (int y = 0; y <= 6; y++) {
            level.setBlock(
                base.offset(x, y, z),
                (Math.abs(x) == 6 || Math.abs(z) == 6 || y == 6
                        ? DevelopmentTestBlocks.GLASS.get()
                        : Blocks.AIR)
                    .defaultBlockState(),
                Block.UPDATE_CLIENTS);
          }
        }
      }
    }
    for (var cell : cells(level)) {
      level.setBlock(
          cell,
          org.Azkerash.create_bioships.components.ComponentRegistry.BRAIN.get().defaultBlockState(),
          Block.UPDATE_ALL);
    }
    level.setBlock(core(level), ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        core(level).north(), ComponentRegistry.MOUTH.get().defaultBlockState(), Block.UPDATE_ALL);
    for (int x = 0; x <= 1; x++) {
      for (int y = 0; y <= 1; y++) {
        level.setBlock(
            base.offset(x, y, -6),
            DevelopmentTestBlocks.GLASS_DOOR
                .get()
                .defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, y == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, x == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT),
            Block.UPDATE_ALL);
      }
    }
    control(level, base.offset(-3, 0, -8), "brain_test start");
    control(level, base.offset(3, 0, -8), "brain_test reset");
    CoreTestState.get(level).setBrainBuilt(true);
    CoreTestState.get(level).setDirty();
    control(level, base.offset(0, 0, -10), "brain_test feed");
    explain(level, "Ready: START assembles brain and mouth");
  }

  private static void control(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
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
        Block.UPDATE_ALL);
  }

  public static int start(ServerLevel level) {
    if (body(level) != null) {
      return 0;
    }
    if (!CoreTestState.get(level).isBrainBuilt()) {
      reset(level);
    }
    var origin = core(level);
    var base = base(level);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            cells(level),
            new BoundingBox3i(base.offset(-1, 1, -1), base.offset(2, 3, 2)));
    if (body == null) {
      return 0;
    }
    CoreAssemblyRules.changed(level, body.getPlot().getCenterBlock());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    DevelopmentTestOwnership.assign(body);
    DevelopmentLifeSupport.pause(body);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    var brain = org.Azkerash.create_bioships.core.brain.SubLevelBrains.get(body);
    brain.inventory().setItem(0, ComponentRegistry.BLANK_BEHAVIOR_CARD.get().getDefaultInstance());
    explain(level, "Running: 27 slots; one blank behavior card stored");
    return 1;
  }

  public static int feed(ServerLevel level) {
    var body = body(level);
    if (body == null) {
      return 0;
    }
    var p = body.getPlot().getCenterBlock().north();
    if (level.getBlockEntity(p)
        instanceof org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity mouth) {
      return mouth
              .feed(
                  new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PORKCHOP),
                  false)
              .isEmpty()
          ? 1
          : 0;
    }
    return 0;
  }

  public static String status(ServerLevel level) {
    var body = body(level);
    if (body == null) {
      return "Brain fixture: ready, unassembled";
    }
    var brain = org.Azkerash.create_bioships.core.brain.SubLevelBrains.get(body);
    return "Brain fixture: "
        + brain.capacity()
        + " slots; port "
        + brain.face().getName()
        + "; /brain_test feed animates mouth";
  }

  private static void explain(ServerLevel level, String result) {
    var base = base(level);
    for (var old :
        level.getEntitiesOfClass(
            Display.TextDisplay.class,
            new AABB(base).inflate(20),
            e -> e.getTags().contains(MARK))) {
      old.discard();
    }
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display == null) {
      return;
    }
    var tag = new CompoundTag();
    tag.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "BRAIN & CHEWING REGRESSION\n"
                    + "Left: START · Right: RESET · Front: FEED\n"
                    + "Brain has one shared 27-slot behavior-card memory.\n"
                    + "Use its central port; wrench another brain face to move it.\n"
                    + "No second brain or growth beyond 3 by 3 by 3.\n"
                    + "FEED (or /brain_test feed) demonstrates mouth chewing.\n"
                    + "Editor and behavior execution are reserved for later.\n"
                    + result),
            level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putInt("line_width", 340);
    tag.putInt("background", 0x70000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(Vec3.atLowerCornerOf(base).add(.5, 1.62, -7.5));
    display.addTag(MARK);
    level.addFreshEntity(display);
  }
}
