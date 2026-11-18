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
public final class ModularLimbFixture {
  private static final String MARK = "create_bioships:modular_limb_fixture";
  private static final String WANDER_VERSION = "create_bioships:modular_limb_wander";

  private ModularLimbFixture() {}

  public static BlockPos base(ServerLevel level) {
    return new BlockPos(1024, CoreTestState.get(level).layout().base().getY(), 0);
  }

  public static ServerSubLevel body(ServerLevel level) {
    var container = SubLevelContainer.getContainer(level);
    if (container != null)
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel b
            && !b.isRemoved()
            && b.getUserDataTag() != null
            && b.getUserDataTag().getBoolean(MARK)) return b;
    return null;
  }

  public static List<BlockPos> place(ServerLevel level, BlockPos base) {
    var cells = new ArrayList<BlockPos>();
    for (int x = -1; x <= 1; x++)
      for (int z = -2; z <= 2; z++) {
        var pos = base.offset(x, 4, z);
        level.setBlock(
            pos,
            (x == 0 && z == 0 ? ComponentRegistry.CORE.get() : ComponentRegistry.CHITIN_ARMOR.get())
                .defaultBlockState(),
            3);
        cells.add(pos);
      }
    var brain = base.offset(0, 5, 0);
    level.setBlock(brain, ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
    cells.add(brain);
    for (int side : new int[] {-1, 1})
      for (int z : new int[] {-2, 2}) {
        var hip = base.offset(side * 2, 4, z);
        var knee = base.offset(side * 4, 4, z);
        var facing = side < 0 ? Direction.WEST : Direction.EAST;
        level.setBlock(
            hip,
            ComponentRegistry.LIMB_JOINT
                .get()
                .defaultBlockState()
                .setValue(dev.letitwalk.content.LegJointBlock.FACING, facing),
            3);
        level.setBlock(
            hip.relative(facing),
            ComponentRegistry.LIMB_SEGMENT
                .get()
                .defaultBlockState()
                .setValue(
                    net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X),
            3);
        level.setBlock(
            knee,
            ComponentRegistry.LIMB_JOINT
                .get()
                .defaultBlockState()
                .setValue(dev.letitwalk.content.LegJointBlock.FACING, Direction.DOWN),
            3);
        level.setBlock(knee.below(), ComponentRegistry.LIMB_SEGMENT.get().defaultBlockState(), 3);
        level.setBlock(knee.below(2), ComponentRegistry.LIMB_SEGMENT.get().defaultBlockState(), 3);
        level.setBlock(knee.below(3), ComponentRegistry.LIMB_FOOT.get().defaultBlockState(), 3);
        cells.addAll(
            List.of(hip, hip.relative(facing), knee, knee.below(), knee.below(2), knee.below(3)));
      }
    return cells;
  }

  public static void reset(ServerLevel level) {
    CoreTestState.get(level).modularLimbsBuilt = true;
    CoreTestState.get(level).setDirty();
    var base = base(level);
    for (int x = (base.getX() - 12) >> 4; x <= (base.getX() + 12) >> 4; x++)
      for (int z = -1; z <= 2; z++) level.setChunkForced(x, z, true);
    var old = body(level);
    if (old != null) {
      var members = SubLevelCreatureParts.members(old);
      for (var member : members)
        if (member != old)
          SubLevelContainer.getContainer(level)
              .removeSubLevel(member, SubLevelRemovalReason.REMOVED);
      SubLevelContainer.getContainer(level).removeSubLevel(old, SubLevelRemovalReason.REMOVED);
    }
    for (var e :
        level.getEntitiesOfClass(
            Display.TextDisplay.class, new AABB(base).inflate(40), e -> e.getTags().contains(MARK)))
      e.discard();
    for (int x = -10; x <= 10; x++)
      for (int z = -8; z <= 24; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 0; y <= 11; y++)
          level.setBlock(
              base.offset(x, y, z),
              (Math.abs(x) == 10 || z == -8 || z == 24 || y == 11
                      ? DevelopmentTestBlocks.GLASS.get()
                      : Blocks.AIR)
                  .defaultBlockState(),
              2);
      }
    place(level, base);
    button(level, base.offset(-3, 0, -10), "modular_limb_test start");
    button(level, base.offset(3, 0, -10), "modular_limb_test reset");
    var text = new Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, level);
    text.setPos(base.getX() + .5, base.getY() + 2, base.getZ() - 10);
    var nbt = new CompoundTag();
    text.saveWithoutId(nbt);
    nbt.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "MODULAR LIMB LOCOMOTION\n"
                    + "Left: START · Right: RESET\n"
                    + "Four modular limbs · native assembly, IK and suspension\n"
                    + "Wander card in brain · local ground routes.\n"
                    + "Armor absorbs only hits on armored parts."),
            level.registryAccess()));
    nbt.putString("billboard", "center");
    nbt.putInt("line_width", 300);
    text.load(nbt);
    text.addTag(MARK);
    level.addFreshEntity(text);
  }

  private static void button(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    if (level.getBlockEntity(pos) instanceof CommandBlockEntity be)
      be.getCommandBlock().setCommand(command);
    level.setBlock(
        pos.north(),
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
        3);
  }

  public static int start(ServerLevel level) {
    if (body(level) != null) return 0;
    var base = base(level);
    var cells = place(level, base);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            base.offset(0, 4, 0),
            cells,
            new BoundingBox3i(base.offset(-4, 1, -2), base.offset(4, 5, 2)));
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    SubLevelData.hungerPaused(body, true);
    DevelopmentTestOwnership.assign(body);
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(
            body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
    org.Azkerash.create_bioships.compat.letitwalk.ModularLimbController.assemble(body);
    installWander(body);
    return 1;
  }

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("modular_limb_test")
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
                              if (!DevelopmentWorldSetup.isDevelopment(c.getSource().getLevel()))
                                return 0;
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
                                                  : "Modular limbs: "
                                                      + org.Azkerash.create_bioships.compat
                                                          .letitwalk.ModularLimbController
                                                          .limbCount(body)
                                                      + " · Armor: "
                                                      + SubLevelArmor.status(body)),
                                      false);
                              return 1;
                            })));
  }

  private static void installWander(ServerSubLevel body) {
    var brain = SubLevelBrains.get(body);
    if (brain.capacity() == 0) return;
    brain.inventory().setItem(0, ComponentRegistry.WANDER_BEHAVIOR_CARD.get().getDefaultInstance());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(WANDER_VERSION, true);
    body.setUserDataTag(tag);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 != 0) return;
    for (var level : event.getServer().getAllLevels()) {
      if (!DevelopmentWorldSetup.isDevelopment(level)) continue;
      var subject = body(level);
      if (subject != null && !subject.getUserDataTag().getBoolean(WANDER_VERSION))
        installWander(subject);
    }
  }
}
