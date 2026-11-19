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
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity;
import org.Azkerash.create_bioships.core.*;

/** Repeatable native digestion demonstration; no substitute digestion implementation. */
public final class DigestionFixture {
  private static final String MARK = "create_bioships:digestion_fixture";

  private DigestionFixture() {}

  public static BlockPos mouth(ServerLevel level) {
    return CoreTestState.get(level).digestionOrigin.offset(-3, 1, 0);
  }

  public static BlockPos isolated(ServerLevel level) {
    return CoreTestState.get(level).digestionOrigin.offset(3, 1, 0);
  }

  private static List<BlockPos> cells(ServerLevel level) {
    var m = mouth(level);
    return List.of(
        m,
        m.east(),
        m.east(2),
        m.east(3),
        m.east(3).south(),
        m.east().south(),
        m.east().south().above(),
        m.east(2).north());
  }

  public static void reset(ServerLevel level) {
    var state = CoreTestState.get(level);
    var base = state.digestionOrigin;
    for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++)
      for (int z = (base.getZ() - 9) >> 4; z <= (base.getZ() + 8) >> 4; z++)
        level.setChunkForced(x, z, true);
    var container = SubLevelContainer.getContainer(level);
    for (var entry : List.copyOf(container.getAllSubLevels()))
      if (entry instanceof ServerSubLevel body
          && body.getUserDataTag() != null
          && body.getUserDataTag().getBoolean(MARK))
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    for (var entity :
        level.getEntitiesOfClass(
            Entity.class,
            new AABB(base).inflate(15),
            e ->
                e.getTags().contains(MARK)
                    || e instanceof net.minecraft.world.entity.item.ItemEntity)) entity.discard();
    for (int x = -6; x <= 6; x++)
      for (int z = -8; z <= 6; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        if (Math.abs(x) <= 5 && Math.abs(z) <= 5)
          for (int y = 0; y <= 5; y++) {
            var pos = base.offset(x, y, z);
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container inventory)
              inventory.clearContent();
            level.setBlock(
                pos,
                (Math.abs(x) == 5 || Math.abs(z) == 5 || y == 5
                        ? DevelopmentTestBlocks.GLASS.get()
                        : Blocks.AIR)
                    .defaultBlockState(),
                2);
          }
      }
    for (int x = 0; x <= 1; x++)
      for (int y = 0; y <= 1; y++)
        level.setBlock(
            base.offset(x, y, -5),
            DevelopmentTestBlocks.GLASS_DOOR
                .get()
                .defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, y == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, x == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT),
            3);
    var m = mouth(level);
    level.setBlock(m, ComponentRegistry.MOUTH.get().defaultBlockState(), 3);
    level.setBlock(m.east(), ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(m.east(2), ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(m.east(3), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    level.setBlock(m.east(3).south(), ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(m.east().south(), ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(m.east(2).north(), ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(
        m.east().south().above(),
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(net.minecraft.resources.ResourceLocation.parse("biomancy:storage_sac"))
            .defaultBlockState(),
        3);
    level.setBlock(isolated(level), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    control(level, base.offset(-3, 0, -7), "digestion_test start");
    control(level, base.offset(3, 0, -7), "digestion_test reset");
    state.digestionBuilt = true;
    state.digestionRunning = false;
    state.digestionBody = null;
    state.digestionControl = null;
    state.digestionResult = "Ready";
    state.setDirty();
    explain(level);
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
    var state = CoreTestState.get(level);
    if (!state.digestionBuilt || state.digestionRunning || state.digestionBody != null) return 0;
    var m = mouth(level);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            m.east(3).south(),
            cells(level),
            new BoundingBox3i(m.north(), m.east(3).south(2).above()));
    if (body == null) return 0;
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.changed(level, core);
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    DevelopmentLifeSupport.pause(body);
    DevelopmentTestOwnership.assign(body);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body) - 1);
    ((MouthBlockEntity) level.getBlockEntity(core.north().west(3)))
        .feed(new ItemStack(Items.PORKCHOP, 4), false);
    var controlPos = isolated(level);
    level.setBlock(controlPos.south(), ComponentRegistry.CORE.get().defaultBlockState(), 3);
    var controlBody =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            controlPos.south(),
            List.of(controlPos, controlPos.south()),
            new BoundingBox3i(controlPos, controlPos.south()));
    if (controlBody == null) {
      reset(level);
      return 0;
    }
    var controlCore = controlBody.getPlot().getCenterBlock();
    CoreAssemblyRules.changed(level, controlCore);
    var controlTag = controlBody.getUserDataTag().copy();
    controlTag.putBoolean(MARK, true);
    controlBody.setUserDataTag(controlTag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(controlBody, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    DevelopmentLifeSupport.pause(controlBody);
    DevelopmentTestOwnership.assign(controlBody);
    SubLevelData.hunger(controlBody, SubLevelData.maximumHunger(controlBody));
    ((StomachBlockEntity) level.getBlockEntity(controlCore.north()))
        .enqueue(new ItemStack(Items.PORKCHOP), false);
    state.digestionControl = controlBody.getUniqueId();
    state.digestionBody = body.getUniqueId();
    state.digestionRunning = true;
    state.digestionDeadline = level.getGameTime() + 2400;
    state.digestionResult = "Running: four meals through mouth; one seeded isolated control";
    state.setDirty();
    explain(level);
    return 1;
  }

  public static void tick(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.digestionRunning) return;
    var entry = SubLevelContainer.getContainer(level).getSubLevel(state.digestionBody);
    if (!(entry instanceof ServerSubLevel body) || body.isRemoved()) {
      finish(level, "FAIL: subject lost");
      return;
    }
    var core = body.getPlot().getCenterBlock();
    var controlEntry =
        state.digestionControl == null
            ? null
            : SubLevelContainer.getContainer(level).getSubLevel(state.digestionControl);
    if (!(controlEntry instanceof ServerSubLevel controlBody) || controlBody.isRemoved()) {
      finish(level, "FAIL: reset required for living control");
      return;
    }
    if (level.getBlockEntity(core.north().west(3)) instanceof MouthBlockEntity mouth
        && level.getBlockEntity(core.north()) instanceof StomachBlockEntity stomach
        && level.getBlockEntity(controlBody.getPlot().getCenterBlock().north())
            instanceof StomachBlockEntity control) {
      boolean isolatedPassed =
          control.queued() == 0 && components(control) > 0 && paste(control) > 0;
      if (mouth.isEmpty()
          && stomach.queued() == 0
          && components(stomach) > 0
          && SubLevelData.hunger(body) == SubLevelData.maximumHunger(body)
          && isolatedPassed) {
        finish(level, "PASS: transport, components, paste, hunger, isolated digestion");
        return;
      }
    }
    if (level.getGameTime() >= state.digestionDeadline) finish(level, "FAIL: digestion timed out");
  }

  private static int components(StomachBlockEntity stomach) {
    int count = 0;
    for (int i = 9; i < 27; i++)
      if (!stomach.getItem(i).is(StomachBlockEntity.paste()))
        count += stomach.getItem(i).getCount();
    return count;
  }

  private static int paste(StomachBlockEntity stomach) {
    int count = 0;
    for (int i = 9; i < 27; i++)
      if (stomach.getItem(i).is(StomachBlockEntity.paste())) count += stomach.getItem(i).getCount();
    return count;
  }

  private static void finish(ServerLevel level, String result) {
    var state = CoreTestState.get(level);
    state.digestionRunning = false;
    state.digestionResult = result;
    state.setDirty();
    explain(level);
  }

  public static String status(ServerLevel level) {
    return CoreTestState.get(level).digestionResult;
  }

  private static void explain(ServerLevel level) {
    var state = CoreTestState.get(level);
    var base = state.digestionOrigin;
    for (var old :
        level.getEntitiesOfClass(
            Display.TextDisplay.class, new AABB(base).inflate(15), e -> e.getTags().contains(MARK)))
      old.discard();
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display == null) return;
    var tag = new CompoundTag();
    tag.putString(
        "text",
        Component.Serializer.toJson(
            Component.literal(
                "DIGESTION REGRESSION\n"
                    + "Left: START · Right: RESET\n"
                    + "Mouth → intestines → stomach; four pork chops.\n"
                    + "One paste restores four hunger; Efficiency improves nutrition.\n"
                    + "Seeded living control has no mouth or intestines.\n"
                    + "Inspect acid, digestion, components and hunger.\n"
                    + "Toolkit → Intestine connections: sac branches and open ends.\n"
                    + state.digestionResult),
            level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putInt("line_width", 300);
    tag.putInt("background", 0x70000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(Vec3.atLowerCornerOf(base).add(.5, 1.62, -6.5));
    display.addTag(MARK);
    level.addFreshEntity(display);
  }
}
