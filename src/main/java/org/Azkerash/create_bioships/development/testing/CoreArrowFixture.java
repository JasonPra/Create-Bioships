package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.Create_bioships;

/** Native dispenser/arrow regression, available visibly with the other development fixtures. */
public final class CoreArrowFixture {
  private static final String MARK = "create_bioships.core_arrow_fixture";

  private CoreArrowFixture() {}

  public static BlockPos core(ServerLevel level) {
    return CoreTestState.get(level).arrowOrigin.above(2);
  }

  public static BlockPos dispenser(ServerLevel level) {
    return core(level).north(4);
  }

  public static void reset(ServerLevel level) {
    var state = CoreTestState.get(level);
    var base = state.arrowOrigin;
    var container = SubLevelContainer.getContainer(level);
    for (var body : List.copyOf(container.getAllSubLevels()))
      if (body instanceof ServerSubLevel server
          && server.getUserDataTag() != null
          && server.getUserDataTag().getBoolean(MARK))
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    var box =
        new AABB(
            Vec3.atLowerCornerOf(base.offset(-7, 0, -10)),
            Vec3.atLowerCornerOf(base.offset(8, 9, 8)));
    for (var entity :
        level.getEntitiesOfClass(
            net.minecraft.world.entity.Entity.class,
            box,
            e ->
                e.getTags().contains(MARK)
                    || e instanceof net.minecraft.world.entity.projectile.Projectile))
      entity.discard();
    for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++)
      for (int z = (base.getZ() - 10) >> 4; z <= (base.getZ() + 8) >> 4; z++)
        level.setChunkForced(x, z, true);
    for (int x = -7; x <= 7; x++)
      for (int z = -10; z <= 7; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        if (Math.abs(x) <= 6 && Math.abs(z) <= 6)
          for (int y = 0; y <= 7; y++)
            level.setBlock(
                base.offset(x, y, z),
                (Math.abs(x) == 6 || Math.abs(z) == 6 || y == 7
                        ? DevelopmentTestBlocks.GLASS.get()
                        : Blocks.AIR)
                    .defaultBlockState(),
                2);
      }
    var flesh =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "biomancy", "packed_flesh"));
    level.setBlock(base, flesh.defaultBlockState(), 3);
    level.setBlock(base.above(), flesh.defaultBlockState(), 3);
    level.setBlock(core(level), Create_bioships.CORE.get().defaultBlockState(), 3);
    level.setBlock(
        dispenser(level),
        Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH),
        3);
    var dispenser = (DispenserBlockEntity) level.getBlockEntity(dispenser(level));
    dispenser.clearContent();
    dispenser.setItem(0, new ItemStack(Items.ARROW, 16));
    dispenser.setChanged();
    control(level, base.offset(-3, 0, -8), "arrow_test start");
    control(level, base.offset(3, 0, -8), "arrow_test reset");
    var display = EntityType.TEXT_DISPLAY.create(level);
    if (display != null) {
      var tag = new net.minecraft.nbt.CompoundTag();
      tag.putString(
          "text",
          Component.Serializer.toJson(
              Component.literal(
                  "CORE ARROW REGRESSION\n"
                      + "Left: START · Right: RESET\n"
                      + "Assemble, then fire one dispenser arrow.\n"
                      + "Expected: critical damage, embedded arrow,\n"
                      + "only the struck eye shut, fading wound.\n"
                      + "Arrow/wound lifetime: one minute."),
              level.registryAccess()));
      tag.putString("billboard", "center");
      tag.putString("alignment", "center");
      tag.putInt("line_width", 230);
      tag.putInt("background", 0x70000000);
      tag.putBoolean("shadow", true);
      display.load(tag);
      display.setPos(Vec3.atLowerCornerOf(base).add(.5, 1.62, -7.5));
      display.addTag(MARK);
      level.addFreshEntity(display);
    }
    state.arrowBuilt = true;
    state.arrowRunning = false;
    state.arrowBody = null;
    state.setDirty();
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

  public static int start(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.arrowBuilt || state.arrowRunning) return 0;
    var base = state.arrowOrigin;
    var cells = List.of(base, base.above(), core(level));
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, core(level), cells, new BoundingBox3i(base, core(level)));
    if (body == null) return 0;
    org.Azkerash.create_bioships.core.CoreAssemblyRules.changed(
        level, body.getPlot().getCenterBlock());
    var tag = body.getUserDataTag().copy();
    tag.putBoolean(MARK, true);
    body.setUserDataTag(tag);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    org.Azkerash.create_bioships.core.SubLevelData.hungerPaused(body, true);
    state.arrowBody = body.getUniqueId();
    state.arrowRunning = true;
    state.setDirty();
    level.setBlock(dispenser(level).north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
    level.neighborChanged(dispenser(level), Blocks.REDSTONE_BLOCK, dispenser(level).north());
    return 1;
  }
}
