package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.SubLevelBrains;

/** Repeatable native physics demonstration: six armored legs under brain motor control. */
@EventBusSubscriber(modid = "create_bioships")
public final class LocomotionFixture {
  private static final String MARK = "create_bioships:locomotion_fixture";
  private static final String WANDER_VERSION = "create_bioships:locomotion_wander";

  private LocomotionFixture() {}

  public static BlockPos base(ServerLevel level) {
    return new BlockPos(896, CoreTestState.get(level).layout().base().getY(), 0);
  }

  public static ServerSubLevel body(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    if (container != null) {
      for (var entry : container.getAllSubLevels()) {
        if (entry instanceof ServerSubLevel b
            && !b.isRemoved()
            && b.getUserDataTag() != null
            && b.getUserDataTag().getBoolean(MARK)) {
          return b;
        }
      }
    }
    return null;
  }

  public static List<BlockPos> place(ServerLevel level, BlockPos base) {
    var cells = new ArrayList<BlockPos>();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        var pos = base.offset(x, 2, z);
        level.setBlock(
            pos,
            (x == 0 && z == 0 ? ComponentRegistry.CORE.get() : ComponentRegistry.CHITIN_ARMOR.get())
                .defaultBlockState(),
            3);
        cells.add(pos);
      }
    }
    var brain = base.offset(0, 3, 0);
    level.setBlock(brain, ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
    cells.add(brain);
    for (int side : new int[] {-1, 1}) {
      for (int z = -1; z <= 1; z++) {
        var pos = base.offset(side * 2, 2, z);
        level.setBlock(
            pos,
            ComponentRegistry.CRAB_LEG
                .get()
                .defaultBlockState()
                .setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    side < 0 ? Direction.WEST : Direction.EAST),
            3);
        cells.add(pos);
      }
    }
    return cells;
  }

  public static void reset(ServerLevel level) {
    CoreTestState.get(level).locomotionBuilt = true;
    CoreTestState.get(level).setDirty();
    var base = base(level);
    for (int x = (base.getX() - 12) >> 4; x <= (base.getX() + 12) >> 4; x++) {
      for (int z = -1; z <= 2; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    var old = body(level);
    if (old != null) {
      SubLevelContainer.getContainer(level).removeSubLevel(old, SubLevelRemovalReason.REMOVED);
    }
    for (var e :
        level.getEntitiesOfClass(
            Display.TextDisplay.class,
            new AABB(base).inflate(40),
            e -> e.getTags().contains(MARK))) {
      e.discard();
    }
    DevelopmentLegChambers.build(level, base, true, 7);
    installDoors(level);
    place(level, base);
    button(level, base.offset(-3, 0, -10), "leg_test start");
    button(level, base.offset(3, 0, -10), "leg_test reset");
    var text = new Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, level);
    text.setPos(base.getX() + .5, base.getY() + 2, base.getZ() - 10);
    var nbt = new CompoundTag();
    text.saveWithoutId(nbt);
    nbt.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "ARMORED LOCOMOTION\n"
                    + "Left: START · Right: RESET\n"
                    + "Six attachable legs · native gait / IK / suspension\n"
                    + "Wander card in brain · local ground routes.\n"
                    + "Armor absorbs only hits on armored parts."),
            level.registryAccess()));
    nbt.putString("billboard", "center");
    nbt.putInt("line_width", 300);
    text.load(nbt);
    text.addTag(MARK);
    level.addFreshEntity(text);
  }

  /** Add access without resetting an existing subject or its recorded settings. */
  public static void installDoors(ServerLevel level) {
    var base = base(level);
    for (int x = 0; x <= 1; x++) {
      var door =
          DevelopmentTestBlocks.GLASS_DOOR
              .get()
              .defaultBlockState()
              .setValue(DoorBlock.FACING, Direction.NORTH)
              .setValue(
                  DoorBlock.HINGE,
                  x == 0
                      ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                      : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
      for (var half : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.values()) {
        var pos =
            base.offset(
                x,
                half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    ? 0
                    : 1,
                -8);
        if (!level.getBlockState(pos).is(DevelopmentTestBlocks.GLASS_DOOR.get())) {
          level.setBlock(pos, door.setValue(DoorBlock.HALF, half), 3);
        }
      }
    }
  }

  private static void button(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    if (level.getBlockEntity(pos) instanceof CommandBlockEntity be) {
      be.getCommandBlock().setCommand(command);
    }
    level.setBlock(
        pos.north(),
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
        3);
  }

  public static int start(ServerLevel level) {
    if (body(level) != null) {
      return 0;
    }
    var base = base(level);
    var cells = place(level, base);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            base.offset(0, 2, 0),
            cells,
            new BoundingBox3i(base.offset(-2, 2, -1), base.offset(2, 3, 1)));
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(body);
    DevelopmentTestOwnership.assign(body);
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(
            body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    installWander(body);
    return 1;
  }

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("leg_test")
                .requires(s -> s.hasPermission(2))
                .then(
                    Commands.literal("start")
                        .executes(
                            c ->
                                DevelopmentWorldSetup.isDevelopment(c.getSource().getLevel())
                                    ? start(c.getSource().getLevel())
                                    : 0))
                .then(
                    Commands.literal("reset")
                        .executes(
                            c -> {
                              if (!DevelopmentWorldSetup.isDevelopment(c.getSource().getLevel())) {
                                return 0;
                              }
                              reset(c.getSource().getLevel());
                              return 1;
                            }))
                .then(
                    Commands.literal("status")
                        .executes(
                            c -> {
                              var body = body(c.getSource().getLevel());
                              c.getSource()
                                  .sendSuccess(
                                      () ->
                                          Component.literal(
                                              body == null
                                                  ? "Leg test: ready"
                                                  : "Leg contacts: "
                                                      + org.Azkerash.create_bioships.core.legs
                                                          .SubLevelLegs.contacts(body)
                                                      + " · Armor: "
                                                      + SubLevelArmor.status(body)),
                                      false);
                              return 1;
                            })));
  }

  private static void installWander(ServerSubLevel body) {
    var brain = SubLevelBrains.get(body);
    if (brain.capacity() == 0) {
      return;
    }
    brain.inventory().setItem(0, ComponentRegistry.WANDER_BEHAVIOR_CARD.get().getDefaultInstance());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(WANDER_VERSION, true);
    body.setUserDataTag(tag);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 != 0) {
      return;
    }
    for (var level : event.getServer().getAllLevels()) {
      if (!DevelopmentWorldSetup.isDevelopment(level)) {
        continue;
      }
      var subject = body(level);
      if (subject != null && !subject.getUserDataTag().getBoolean(WANDER_VERSION)) {
        installWander(subject);
      }
    }
  }
}
