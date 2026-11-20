package org.azkerash.createbioships.development.testing;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.combat.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.eye.EyeBlink;
import org.azkerash.createbioships.core.*;

/** Three native dispensers test awake core/land/aquatic eyes, then repeat against the corpse. */
public final class CoreArrowFixture {
  // Persisted phase IDs retain their original values for existing development saves.
  private static final int OBSERVING = 0, LIVING_ROUND = 1, CORPSE_ROUND = 2, COMPLETE = 3;
  private static final int TICKS_PER_SECOND = 20;
  private static final int OBSERVATION_TICKS = 600;
  private static final int INITIAL_TIMEOUT_TICKS = 800;
  private static final int SHOT_INTERVAL_TICKS = 20;
  private static final int LIVING_TIMEOUT_TICKS = 160;
  private static final int CORPSE_PAUSE_TICKS = 100;
  private static final int CORPSE_TIMEOUT_TICKS = 300;

  private static final String MARK = "create_bioships.core_arrow_fixture";

  private CoreArrowFixture() {}

  public static BlockPos core(ServerLevel level) {
    return CoreTestState.get(level).getArrowOrigin().above(2);
  }

  public static BlockPos dispenser(ServerLevel level) {
    return core(level).north(4);
  }

  public static List<BlockPos> dispensers(ServerLevel level) {
    return List.of(dispenser(level), dispenser(level).west(3), dispenser(level).east(3));
  }

  public static List<BlockPos> targets(ServerSubLevel body) {
    var core = body.getPlot().getCenterBlock();
    return List.of(core, core.west(3).north(), core.east(3).north());
  }

  private static List<BlockPos> cells(BlockPos base) {
    var result = new ArrayList<BlockPos>();
    for (int x = -3; x <= 3; x++) {
      for (int z = -1; z <= 1; z++) {
        result.add(base.offset(x, 0, z));
      }
    }
    for (int x : new int[] {-3, 0, 3}) {
      result.add(base.offset(x, 1, 0));
      result.add(base.offset(x, 2, 0));
      if (x != 0) {
        result.add(base.offset(x, 2, -1));
      }
    }
    return result;
  }

  private static void sleeping(ServerLevel level) {
    var base = CoreTestState.get(level).getArrowOrigin();
    var flesh =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.parse("biomancy:packed_flesh"));
    for (var cell : cells(base)) {
      level.setBlock(cell, flesh.defaultBlockState(), Block.UPDATE_ALL);
    }
    level.setBlock(core(level), ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        core(level).west(3).north(),
        ComponentRegistry.LAND_EYE
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH),
        Block.UPDATE_ALL);
    level.setBlock(
        core(level).east(3).north(),
        ComponentRegistry.AQUATIC_EYE
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH),
        Block.UPDATE_ALL);
  }

  public static void reset(ServerLevel level) {
    var state = CoreTestState.get(level);
    var base = state.getArrowOrigin();
    for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++) {
      for (int z = (base.getZ() - 10) >> 4; z <= (base.getZ() + 8) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    level
        .getBlockTicks()
        .clearArea(
            net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
                base.offset(-7, 0, -10), base.offset(7, 8, 7)));
    var container = SubLevelContainer.getContainer(level);
    for (var entry : List.copyOf(container.getAllSubLevels())) {
      if (entry instanceof ServerSubLevel body
          && body.getUserDataTag() != null
          && body.getUserDataTag().getBoolean(MARK)) {
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
      }
    }
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
                    || e instanceof net.minecraft.world.entity.item.ItemEntity)) {
      entity.discard();
    }
    for (int x = -7; x <= 7; x++) {
      for (int z = -10; z <= 7; z++) {
        level.setBlock(
            base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (Math.abs(x) <= 6 && Math.abs(z) <= 6) {
          for (int y = 0; y <= 7; y++) {
            if (level.getBlockEntity(base.offset(x, y, z))
                instanceof net.minecraft.world.Container inventory) {
              inventory.clearContent();
            }
            level.setBlock(
                base.offset(x, y, z),
                (Math.abs(x) == 6 || Math.abs(z) == 6 || y == 7
                        ? DevelopmentTestBlocks.GLASS.get()
                        : Blocks.AIR)
                    .defaultBlockState(),
                Block.UPDATE_CLIENTS);
          }
        }
      }
    }
    sleeping(level);
    control(level, base.offset(-3, 0, -8), "arrow_test start");
    control(level, base.offset(3, 0, -8), "arrow_test reset");
    state.setArrowBuilt(true);
    state.setArrowRunning(false);
    state.setArrowBody(null);
    state.setArrowPhase(OBSERVING);
    state.setArrowResult("Ready");
    state.setDirty();
    explain(level);
  }

  private static void control(ServerLevel level, BlockPos pos, String command) {
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
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
        Block.UPDATE_ALL);
  }

  private static void explain(ServerLevel level) {
    var state = CoreTestState.get(level);
    var base = state.getArrowOrigin();
    for (var old :
        level.getEntitiesOfClass(
            Display.TextDisplay.class,
            new AABB(base).inflate(15),
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
                "EYE ARROW REGRESSION\n"
                    + "Left: START · Right: RESET\n"
                    + "Core · Land eye · Aquatic eye\n"
                    + "30s observation, then three front-facing dispensers.\n"
                    + "Then repeat on the corpse after 5 seconds.\n"
                    + "Expected: embedded arrows and fading wounds.\n"
                    + state.getArrowResult()),
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
    if (!state.isArrowBuilt() || state.getArrowBody() != null || state.isArrowRunning()) {
      return 0;
    }
    var base = state.getArrowOrigin();
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            core(level),
            cells(base),
            new BoundingBox3i(base.west(3).north(), core(level).east(3).south()));
    if (body == null) {
      return 0;
    }
    CoreAssemblyRules.changed(level, body.getPlot().getCenterBlock());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    DevelopmentLifeSupport.pause(body);
    DevelopmentTestOwnership.assign(body);
    state.setArrowBody(body.getUniqueId());
    state.setArrowRunning(true);
    state.setArrowPhase(OBSERVING);
    state.setArrowNextAction(level.getGameTime() + OBSERVATION_TICKS);
    state.setArrowDeadline(level.getGameTime() + INITIAL_TIMEOUT_TICKS);
    state.setArrowResult("Assembled: dispensers appear in 30 seconds");
    state.setDirty();
    explain(level);
    return 1;
  }

  private static void fire(ServerLevel level) {
    fire(level, dispensers(level));
  }

  private static void fire(ServerLevel level, List<BlockPos> positions) {
    for (var pos : positions) {
      if (!level.getBlockState(pos).is(Blocks.DISPENSER)) {
        level.setBlock(
            pos,
            Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH),
            Block.UPDATE_ALL);
        ((DispenserBlockEntity) level.getBlockEntity(pos))
            .setItem(0, new ItemStack(Items.ARROW, 16));
      }
      level.setBlock(pos.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      level.neighborChanged(pos, Blocks.AIR, pos.north());
      level.setBlock(pos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
      level.neighborChanged(pos, Blocks.REDSTONE_BLOCK, pos.north());
      level.scheduleTick(pos, Blocks.DISPENSER, 4);
    }
  }

  private static void clearShooters(ServerLevel level) {
    for (var pos : dispensers(level)) {
      if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container inventory) {
        inventory.clearContent();
      }
      level.removeBlock(pos, false);
      level.removeBlock(pos.north(), false);
    }
  }

  public static void tick(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.isArrowRunning() || state.getArrowPhase() == COMPLETE) {
      return;
    }
    var entry = SubLevelContainer.getContainer(level).getSubLevel(state.getArrowBody());
    if (!(entry instanceof ServerSubLevel body) || body.isRemoved()) {
      fail(level, "subject lost");
      return;
    }
    long now = level.getGameTime();
    switch (state.getArrowPhase()) {
      case OBSERVING -> {
        boolean awake =
            targets(body).stream()
                .allMatch(
                    pos -> {
                      var block = level.getBlockState(pos);
                      return block.hasProperty(EyeBlink.WAKE)
                          && block.getValue(EyeBlink.WAKE) == 0
                          && !block.getValue(EyeBlink.BLINK);
                    });
        if (now < state.getArrowNextAction() && now % TICKS_PER_SECOND == 0) {
          state.setArrowResult(
              "Dispensers appear in "
                  + ((state.getArrowNextAction() - now + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)
                  + "s");
          explain(level);
        }
        if (now >= state.getArrowNextAction()
            && awake
            && dispensers(level).stream().allMatch(level::isPositionEntityTicking)) {
          state.setArrowShotAt(now);
          fire(level);
          state.setArrowNextAction(now + SHOT_INTERVAL_TICKS);
          state.setArrowPhase(LIVING_ROUND);
          state.setArrowDeadline(now + LIVING_TIMEOUT_TICKS);
          state.setArrowResult("Firing at living eyes");
          state.setDirty();
          explain(level);
        }
      }
      case LIVING_ROUND -> {
        if (wounded(body, state.getArrowShotAt())) {
          clearShooters(level);
          state.setArrowPhase(CORPSE_ROUND);
          state.setArrowNextAction(now + CORPSE_PAUSE_TICKS);
          state.setArrowDeadline(now + CORPSE_TIMEOUT_TICKS);
          state.setArrowResult("Living eyes: PASS. Inspect before corpse round.");
          state.setDirty();
          explain(level);

        } else if (now >= state.getArrowNextAction()) {
          retryMissing(level, body, state.getArrowShotAt());
          state.setArrowNextAction(now + SHOT_INTERVAL_TICKS);
        }
      }
      case CORPSE_ROUND -> {
        if (now >= state.getArrowNextAction()) {
          if (!SubLevelHealth.status(body).dead()) {
            SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "arrow_test_corpse");
            state.setArrowShotAt(now);
            fire(level);
            state.setArrowNextAction(now + SHOT_INTERVAL_TICKS);
            state.setArrowResult("Firing at corpse eyes");
            state.setDirty();
            explain(level);
          } else if (wounded(body, state.getArrowShotAt())) {
            clearShooters(level);
            state.setArrowPhase(COMPLETE);
            state.setArrowResult("PASS: core, land and aquatic eyes · living + corpse");
            state.setDirty();
            explain(level);
            return;
          } else {
            retryMissing(level, body, state.getArrowShotAt());
            state.setArrowNextAction(now + SHOT_INTERVAL_TICKS);
          }
        }
      }
      default -> {
        // The deadline below still handles an invalid persisted phase.
      }
    }
    if (now >= state.getArrowDeadline()) {
      fail(level, "timeout in phase " + state.getArrowPhase());
    }
  }

  /** Native dispensers have random spread. Retry only missing lanes within the same deadline. */
  private static void retryMissing(ServerLevel level, ServerSubLevel body, long since) {
    var targets = targets(body);
    var wounds = SubLevelWounds.state(body).active(level.getGameTime());
    var missing = new ArrayList<BlockPos>();
    for (int i = 0; i < targets.size(); i++) {
      var cell = targets.get(i);
      if (wounds.stream()
          .noneMatch(
              w ->
                  w.surface().cell().equals(cell)
                      && w.surface().face() == Direction.NORTH
                      && w.at() >= since)) {
        missing.add(dispensers(level).get(i));
      }
    }
    // Remove misses embedded in supporting tissue; retained eye arrows still prove both rounds.
    for (var arrow :
        level.getEntitiesOfClass(
            net.minecraft.world.entity.projectile.Arrow.class,
            new AABB(CoreTestState.get(level).getArrowOrigin()).inflate(12),
            ProjectileAttachment::attached)) {
      var data = arrow.getPersistentData().getCompound("create_bioships:embedded_projectile");
      if (data.hasUUID("body")
          && data.getUUID("body").equals(body.getUniqueId())
          && !targets.contains(BlockPos.of(data.getLong("cell")))) {
        arrow.discard();
      }
    }
    fire(level, missing);
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
    state.setArrowRunning(false);
    state.setArrowResult("FAIL: " + reason);
    state.setDirty();
    explain(level);
  }

  public static String status(ServerLevel level) {
    return "Arrow test: " + CoreTestState.get(level).getArrowResult();
  }
}
