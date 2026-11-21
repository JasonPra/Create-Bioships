package org.azkerash.createbioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.azkerash.createbioships.compat.letitwalk.LimbTuning;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.access.SubLevelPermissions;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.core.locomotion.SubLevelFacing;

/** Repeatable eight-legged, laterigrade huntsman subject; never builds or clears terrain. */
@EventBusSubscriber(modid = "create_bioships")
public final class HuntsmanFixture {
  public static final int LEG_HEIGHT = 4;
  private static final int SPAWN_LIFT_BLOCKS = 10, SPAWN_DISTANCE_BLOCKS = 24;
  private static final int[] LEG_ROWS = {-2, -1, 0, 1};
  private static final int[] REST_ANGLES = {-45, -15, 15, 45};

  private HuntsmanFixture() {}

  /** Anatomical offsets in blocks from the core; north is the spider's front. */
  public static Map<BlockPos, BlockState> anatomy(BlockPos core) {
    return anatomy(core, true);
  }

  /** Matching body/load permits a controlled comparison with friction-based crab legs. */
  public static Map<BlockPos, BlockState> anatomy(BlockPos core, boolean arachnid) {
    var cells = new LinkedHashMap<BlockPos, BlockState>();
    var armor = ComponentRegistry.HUNTSMAN_ARMOR.get().defaultBlockState();
    // Low cephalothorax and a rounded, hollow abdomen, joined by a narrow waist.
    for (int x = -2; x <= 2; x++) {
      for (int y = 0; y <= 1; y++) {
        for (int z = -2; z <= 1; z++) {
          if (Math.abs(x) == 2 || y == 0 || y == 1 || z == -2 || z == 1) {
            cells.put(core.offset(x, y, z), armor);
          }
        }
      }
    }
    cells.put(core.south(2), armor);
    for (int x = -2; x <= 2; x++) {
      for (int y = 0; y <= 2; y++) {
        for (int z = 3; z <= 5; z++) {
          if ((Math.abs(x) == 2 && (z == 3 || z == 5)) || (y == 2 && Math.abs(x) == 2)) {
            continue;
          }
          if (Math.abs(x) >= 2 || y == 0 || y == 2 || z == 3 || z == 5) {
            cells.put(core.offset(x, y, z), armor);
          }
        }
      }
    }
    cells.put(core, ComponentRegistry.CORE.get().defaultBlockState());
    cells.put(core.above(), ComponentRegistry.BRAIN.get().defaultBlockState());
    cells.put(core.south(), ComponentRegistry.LUNG.get().defaultBlockState());
    cells.put(core.south(4).above(), ComponentRegistry.STOMACH.get().defaultBlockState());
    cells.put(
        core.north(3),
        ComponentRegistry.ARACHNID_MAW
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH));
    for (int row : new int[] {0, 1}) {
      for (int x : new int[] {-2, -1, 1, 2}) {
        cells.put(
            core.offset(x, row, -3),
            ComponentRegistry.LAND_EYE
                .get()
                .defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
      }
    }
    for (int side : new int[] {-1, 1}) {
      for (int row : LEG_ROWS) {
        cells.put(
            core.offset(side * 3, 0, row),
            (arachnid ? ComponentRegistry.ARACHNID_LEG : ComponentRegistry.CRAB_LEG)
                .get()
                .defaultBlockState()
                .setValue(CompactLegSocketBlock.HEIGHT, LEG_HEIGHT)
                .setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    side < 0 ? Direction.WEST : Direction.EAST));
      }
    }
    return Collections.unmodifiableMap(cells);
  }

  public static ServerSubLevel spawn(ServerLevel level, BlockPos core) {
    return spawn(level, core, true);
  }

  public static ServerSubLevel spawn(ServerLevel level, BlockPos core, boolean arachnid) {
    var cells = anatomy(core, arachnid);
    // Preflight the complete subject before writing anything. A failed spawn
    // cannot erase trees, terrain, another creature or a player's construction.
    for (var cell : cells.keySet()) {
      if (!level.getBlockState(cell).isAir()) {
        throw new IllegalArgumentException(
            "Huntsman spawn needs empty air at " + cell.toShortString());
      }
    }
    cells.forEach((cell, state) -> level.setBlock(cell, state, Block.UPDATE_ALL));
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            core,
            List.copyOf(cells.keySet()),
            new BoundingBox3i(core.offset(-3, 0, -3), core.offset(3, 2, 5)));
    var center = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, center);
    SubLevelFacing.set(body, Direction.NORTH);
    DevelopmentLifeSupport.pause(body);
    for (int side : new int[] {-1, 1}) {
      for (int index = 0; index < LEG_ROWS.length; index++) {
        new LimbTuning(side < 0 ? REST_ANGLES[index] : -REST_ANGLES[index], 1, 1)
            .save(level, center.offset(side * 3, 0, LEG_ROWS[index]));
      }
    }
    SubLevelLegs.request(body);
    return body;
  }

  @SubscribeEvent
  public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("bioship_huntsman")
                .requires(source -> source.hasPermission(2))
                .executes(
                    context -> {
                      var source = context.getSource();
                      var base =
                          BlockPos.containing(source.getPosition())
                              .offset(0, SPAWN_LIFT_BLOCKS, -SPAWN_DISTANCE_BLOCKS);
                      try {
                        var body = spawn(source.getLevel(), base);
                        if (source.getEntity()
                            instanceof net.minecraft.server.level.ServerPlayer player) {
                          SubLevelPermissions.assignIfUnowned(body, player.getUUID());
                        }
                        source.sendSuccess(
                            () ->
                                Component.literal(
                                    "Huntsman test subject: "
                                        + body.getUniqueId()
                                        + ". Life support paused; terrain preserved."),
                            false);
                        return 1;
                      } catch (IllegalArgumentException exception) {
                        source.sendFailure(Component.literal(exception.getMessage()));
                        return 0;
                      }
                    }));
  }
}
