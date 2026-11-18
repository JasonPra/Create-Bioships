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
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.eye.EyeBlink;
import org.Azkerash.create_bioships.core.*;

/** Three native dispensers test awake core/land/aquatic eyes, then repeat against the corpse. */
public final class CoreArrowFixture {
  private static final String MARK = "create_bioships.core_arrow_fixture";

  private CoreArrowFixture() {}

  public static BlockPos core(ServerLevel level) {
    return CoreTestState.get(level).arrowOrigin.above(2);
  }

  public static BlockPos dispenser(ServerLevel level) {
    return core(level).north(2);
  }

  public static List<BlockPos> dispensers(ServerLevel level) {
    return List.of(dispenser(level), core(level).west(3).north(), core(level).east(3).north());
  }

  public static List<BlockPos> targets(ServerSubLevel body) {
    var core = body.getPlot().getCenterBlock();
    return List.of(core, core.west(3), core.east(3));
  }

  private static List<BlockPos> cells(BlockPos base) {
    var result = new ArrayList<BlockPos>();
    for (int x = -3; x <= 3; x++) result.add(base.offset(x, 0, 0));
    for (int x : new int[] {-3, 0, 3}) {
      result.add(base.offset(x, 1, 0));
      result.add(base.offset(x, 2, 0));
      result.add(base.offset(x, 2, 1));
    }
    return result;
  }

  private static void sleeping(ServerLevel level) {
    var base = CoreTestState.get(level).arrowOrigin;
    var flesh =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.parse("biomancy:packed_flesh"));
    for (var cell : cells(base)) level.setBlock(cell, flesh.defaultBlockState(), 3);
    level.setBlock(core(level), ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(
        core(level).west(3),
        ComponentRegistry.LAND_EYE
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH),
        3);
    level.setBlock(
        core(level).east(3),
        ComponentRegistry.AQUATIC_EYE
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH),
        3);
  }

  public static void reset(ServerLevel level) {
    var state = CoreTestState.get(level);
    var base = state.arrowOrigin;
    for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++)
      for (int z = (base.getZ() - 10) >> 4; z <= (base.getZ() + 8) >> 4; z++)
        level.setChunkForced(x, z, true);
    level
        .getBlockTicks()
        .clearArea(
            net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
                base.offset(-7, 0, -10), base.offset(7, 8, 7)));
    var container = SubLevelContainer.getContainer(level);
    for (var entry : List.copyOf(container.getAllSubLevels()))
      if (entry instanceof ServerSubLevel body
          && body.getUserDataTag() != null
          && body.getUserDataTag().getBoolean(MARK))
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    var box =
        new AABB(
            Vec3.atLowerCornerOf(base.offset(-7, 0, -10)),
            Vec3.atLowerCornerOf(base.offset(8, 9, 8)));
    for (var entity :
        level.getEntitiesOfClass(
            Entity.class,
            box,
            e ->
                e.getTags().contains(MARK)
                    || e instanceof net.minecraft.world.entity.projectile.Projectile
                    || e instanceof net.minecraft.world.entity.item.ItemEntity)) entity.discard();
    for (int x = -7; x <= 7; x++)
      for (int z = -10; z <= 7; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        if (Math.abs(x) <= 6 && Math.abs(z) <= 6)
          for (int y = 0; y <= 7; y++) {
            if (level.getBlockEntity(base.offset(x, y, z))
                instanceof net.minecraft.world.Container inventory) inventory.clearContent();
            level.setBlock(
                base.offset(x, y, z),
                (Math.abs(x) == 6 || Math.abs(z) == 6 || y == 7
                        ? DevelopmentTestBlocks.GLASS.get()
                        : Blocks.AIR)
                    .defaultBlockState(),
                2);
          }
      }
    sleeping(level);
    for (var pos : dispensers(level)) {
      level.setBlock(
          pos,
          Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH),
          3);
      var inventory = (DispenserBlockEntity) level.getBlockEntity(pos);
      inventory.setItem(0, new ItemStack(Items.ARROW, 16));
      inventory.setChanged();
    }
    control(level, base.offset(-3, 0, -8), "arrow_test start");
    control(level, base.offset(3, 0, -8), "arrow_test reset");
    state.arrowBuilt = true;
    state.arrowRunning = false;
    state.arrowBody = null;
    state.arrowPhase = 0;
    state.arrowResult = "Ready";
    state.setDirty();
    explain(level);
  }

  private static void control(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    var block = (CommandBlockEntity) level.getBlockEntity(pos);
    block.getCommandBlock().setCommand(command);
    block.getCommandBlock().setTrackOutput(false);
    block.setChanged();
    level.setBlock(
        pos.above(),
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
        3);
  }

  private static void explain(ServerLevel level) {
    var state = CoreTestState.get(level);
    var base = state.arrowOrigin;
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
                "EYE ARROW REGRESSION\n"
                    + "Left: START · Right: RESET\n"
                    + "Core · Land eye · Aquatic eye\n"
                    + "Wait for wake-up; three front-facing arrows.\n"
                    + "Then repeat on the corpse after 5 seconds.\n"
                    + "Expected: embedded arrows and fading wounds.\n"
                    + state.arrowResult),
            level.registryAccess()));
    tag.putString("billboard", "center");
    tag.putInt("line_width", 270);
    tag.putInt("background", 0x70000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(Vec3.atLowerCornerOf(base).add(.5, 1.62, -7.5));
    display.addTag(MARK);
    level.addFreshEntity(display);
  }

  public static int start(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.arrowBuilt || state.arrowBody != null || state.arrowRunning) return 0;
    var base = state.arrowOrigin;
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            core(level),
            cells(base),
            new BoundingBox3i(base.west(3), core(level).east(3).south()));
    if (body == null) return 0;
    CoreAssemblyRules.changed(level, body.getPlot().getCenterBlock());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    SubLevelData.hungerPaused(body, true);
    state.arrowBody = body.getUniqueId();
    state.arrowRunning = true;
    state.arrowPhase = 0;
    state.arrowDeadline = level.getGameTime() + 200;
    state.arrowResult = "Waiting for all three eyes to wake";
    state.setDirty();
    explain(level);
    return 1;
  }

  private static void fire(ServerLevel level) {
    for (var pos : dispensers(level)) {
      if (!level.getBlockState(pos).is(Blocks.DISPENSER)) {
        level.setBlock(
            pos,
            Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH),
            3);
        ((DispenserBlockEntity) level.getBlockEntity(pos))
            .setItem(0, new ItemStack(Items.ARROW, 16));
      }
      level.setBlock(pos.north(), Blocks.AIR.defaultBlockState(), 3);
      level.neighborChanged(pos, Blocks.AIR, pos.north());
      level.setBlock(pos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
      level.neighborChanged(pos, Blocks.REDSTONE_BLOCK, pos.north());
      level.scheduleTick(pos, Blocks.DISPENSER, 4);
    }
  }

  private static void clearShooters(ServerLevel level) {
    for (var pos : dispensers(level)) {
      if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container inventory)
        inventory.clearContent();
      level.removeBlock(pos, false);
      level.removeBlock(pos.north(), false);
    }
  }

  public static void tick(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.arrowRunning || state.arrowPhase == 3) return;
    var entry = SubLevelContainer.getContainer(level).getSubLevel(state.arrowBody);
    if (!(entry instanceof ServerSubLevel body) || body.isRemoved()) {
      fail(level, "subject lost");
      return;
    }
    long now = level.getGameTime();
    if (state.arrowPhase == 0) {
      boolean awake =
          targets(body).stream()
              .allMatch(
                  pos -> {
                    var block = level.getBlockState(pos);
                    return block.hasProperty(EyeBlink.WAKE)
                        && block.getValue(EyeBlink.WAKE) == 0
                        && !block.getValue(EyeBlink.BLINK);
                  });
      if (awake && dispensers(level).stream().allMatch(level::isPositionEntityTicking)) {
        state.arrowShotAt = now;
        fire(level);
        state.arrowPhase = 1;
        state.arrowDeadline = now + 160;
        state.arrowResult = "Firing at living eyes";
        state.setDirty();
        explain(level);
      }
    } else if (state.arrowPhase == 1 && wounded(body, state.arrowShotAt)) {
      clearShooters(level);
      state.arrowPhase = 2;
      state.arrowNextAction = now + 100;
      state.arrowDeadline = now + 300;
      state.arrowResult = "Living eyes: PASS. Inspect before corpse round.";
      state.setDirty();
      explain(level);
    } else if (state.arrowPhase == 2 && now >= state.arrowNextAction) {
      if (!SubLevelHealth.status(body).dead()) {
        SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "arrow_test_corpse");
        state.arrowShotAt = now;
        fire(level);
        state.arrowResult = "Firing at corpse eyes";
        state.setDirty();
        explain(level);
      } else if (wounded(body, state.arrowShotAt)) {
        clearShooters(level);
        state.arrowPhase = 3;
        state.arrowResult = "PASS: core, land and aquatic eyes · living + corpse";
        state.setDirty();
        explain(level);
        return;
      }
    }
    if (now >= state.arrowDeadline) fail(level, "timeout in phase " + state.arrowPhase);
  }

  private static boolean wounded(ServerSubLevel body, long since) {
    var wounds = SubLevelWounds.state(body).active(body.getLevel().getGameTime());
    return targets(body).stream()
        .allMatch(
            pos ->
                wounds.stream()
                    .anyMatch(
                        w ->
                            w.surface().cell().equals(pos)
                                && w.surface().face() == Direction.NORTH
                                && w.at() >= since));
  }

  private static void fail(ServerLevel level, String reason) {
    var state = CoreTestState.get(level);
    state.arrowRunning = false;
    state.arrowResult = "FAIL: " + reason;
    state.setDirty();
    explain(level);
  }

  public static String status(ServerLevel level) {
    return "Arrow test: " + CoreTestState.get(level).arrowResult;
  }
}
