package org.Azkerash.create_bioships.development;

import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.Create_bioships;

/** Visible fixture geometry only. Construction never uses player placement or assembles a core. */
public final class CoreSplitFixture {
  public static final int RADIUS = 9, HEIGHT = 13, PYRAMID_RADIUS = 3;

  public record Layout(BlockPos base) {
    public BlockPos tip() {
      return base.above(4);
    }

    public AABB arena() {
      return new AABB(
          Vec3.atLowerCornerOf(base.offset(-RADIUS, 0, -RADIUS)),
          Vec3.atLowerCornerOf(base.offset(RADIUS + 1, HEIGHT + 1, RADIUS + 1)));
    }

    public AABB glue() {
      return new AABB(
          Vec3.atLowerCornerOf(tip().offset(-PYRAMID_RADIUS, 0, -PYRAMID_RADIUS)),
          Vec3.atLowerCornerOf(
              tip().offset(PYRAMID_RADIUS + 1, PYRAMID_RADIUS + 1, PYRAMID_RADIUS + 1)));
    }

    public List<BlockPos> cores() {
      var result = new ArrayList<BlockPos>();
      for (int r = 0; r <= PYRAMID_RADIUS; r++)
        for (int x = -r; x <= r; x++)
          for (int z = -r; z <= r; z++)
            if (r == 0 || Math.abs(x) == r || Math.abs(z) == r) result.add(tip().offset(x, r, z));
      return List.copyOf(result);
    }

    public BlockPos startButton() {
      return base.offset(-3, 1, -12);
    }

    public BlockPos resetButton() {
      return base.offset(3, 1, -12);
    }
  }

  private CoreSplitFixture() {}

  public static void build(ServerLevel level, Layout layout) {
    var base = layout.base();
    // A regression fixture must keep running when the observer walks away.
    for (int x = (base.getX() - 10) >> 4; x <= (base.getX() + 10) >> 4; x++)
      for (int z = (base.getZ() - 15) >> 4; z <= (base.getZ() + 10) >> 4; z++)
        level.setChunkForced(x, z, true);
    for (int x = -10; x <= 10; x++)
      for (int z = -15; z <= 10; z++)
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
    for (int x = -RADIUS; x <= RADIUS; x++)
      for (int z = -RADIUS; z <= RADIUS; z++)
        for (int y = 0; y <= HEIGHT; y++) {
          boolean shell = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS || y == HEIGHT;
          level.setBlock(
              base.offset(x, y, z), (shell ? Blocks.GLASS : Blocks.AIR).defaultBlockState(), 2);
        }
    for (int y = 0; y < 4; y++)
      level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
    for (var pos : layout.cores())
      level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    var glue = new HoneyGlueEntity(level, layout.glue());
    level.addFreshEntity(glue);
    glue.setBoundsAndSync(layout.glue());
    control(level, layout.startButton(), "core_test start", "START", "Assemble pyramid");
    control(level, layout.resetButton(), "core_test reset", "RESET", "Restore fixture");
  }

  private static void control(
      ServerLevel level, BlockPos button, String command, String title, String description) {
    var pos = button.below();
    level.setBlock(pos, Blocks.COMMAND_BLOCK.defaultBlockState(), 3);
    var entity = (CommandBlockEntity) level.getBlockEntity(pos);
    entity.getCommandBlock().setCommand(command);
    entity.getCommandBlock().setTrackOutput(false);
    entity.setAutomatic(false);
    entity.setChanged();
    level.setBlock(
        button,
        Blocks.STONE_BUTTON
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
        3);
    var signPos = pos.north();
    level.setBlock(
        signPos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), 3);
    var sign = (SignBlockEntity) level.getBlockEntity(signPos);
    var text =
        sign.getFrontText()
            .setMessage(0, Component.literal("CORE SPLIT TEST"))
            .setMessage(1, Component.literal(title))
            .setMessage(2, Component.literal(description))
            .setMessage(3, Component.literal("/" + command));
    sign.setText(text, true);
    sign.setText(text, false);
    sign.setWaxed(true);
    sign.setChanged();
  }
}
