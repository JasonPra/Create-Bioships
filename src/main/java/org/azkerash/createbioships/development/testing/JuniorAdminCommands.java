package org.azkerash.createbioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "create_bioships")
public final class JuniorAdminCommands {
  private static final String CONTROL_LABEL = "bioships.junior_reset_control";

  private JuniorAdminCommands() {}

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
    e.getDispatcher()
        .register(
            Commands.literal("bioship_dev")
                .requires(
                    s -> s.hasPermission(2) && DevelopmentWorldSetup.isDevelopment(s.getLevel()))
                .then(
                    Commands.literal("junior")
                        .then(
                            Commands.literal("reset")
                                .executes(
                                    c -> {
                                      try {
                                        var b =
                                            DevelopmentJuniorBaseline.reset(
                                                c.getSource().getLevel());
                                        c.getSource()
                                            .sendSuccess(
                                                () ->
                                                    Component.literal(
                                                        "Junior restored from the saved baseline."
                                                            + " Owner retained. New body: "
                                                            + b.getUniqueId()),
                                                true);
                                        return 1;
                                      } catch (RuntimeException error) {
                                        c.getSource()
                                            .sendFailure(Component.literal(error.getMessage()));
                                        return 0;
                                      }
                                    }))
                        .then(
                            Commands.literal("capture")
                                .executes(
                                    c -> {
                                      var l = c.getSource().getLevel();
                                      var state = CoreTestState.get(l);
                                      var container = SubLevelContainer.getContainer(l);
                                      var body =
                                          state.getJuniorSubject() == null
                                              ? null
                                              : container.getSubLevel(state.getJuniorSubject());
                                      if (!(body instanceof ServerSubLevel b)) {
                                        c.getSource()
                                            .sendFailure(
                                                Component.literal("Junior is not loaded."));
                                        return 0;
                                      }
                                      try {
                                        DevelopmentJuniorBaseline.capture(b);
                                        c.getSource()
                                            .sendSuccess(
                                                () ->
                                                    Component.literal(
                                                        "Updated Junior's development baseline."),
                                                true);
                                        return 1;
                                      } catch (RuntimeException error) {
                                        c.getSource()
                                            .sendFailure(Component.literal(error.getMessage()));
                                        return 0;
                                      }
                                    }))));
  }

  public static void installControl(ServerLevel l) {
    var p = NecromancerDenFixture.base(l).offset(5, 0, -23);
    for (var previous :
        l.getEntitiesOfClass(
            net.minecraft.world.entity.Display.TextDisplay.class,
            new net.minecraft.world.phys.AABB(p).inflate(8),
            entity -> entity.getTags().contains(CONTROL_LABEL))) {
      previous.discard();
    }
    l.setBlock(
        p.below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
    l.setBlock(
        p,
        Blocks.COMMAND_BLOCK.defaultBlockState().setValue(CommandBlock.FACING, Direction.UP),
        Block.UPDATE_CLIENTS);
    if (l.getBlockEntity(p) instanceof CommandBlockEntity command) {
      command.getCommandBlock().setCommand("bioship_dev junior reset");
      command.getCommandBlock().setTrackOutput(false);
      command.setChanged();
    }
    l.setBlock(
        p.north(),
        DevelopmentTestBlocks.BUTTON
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL),
        Block.UPDATE_CLIENTS);
    var sign = net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(l);
    if (sign != null) {
      var tag = new net.minecraft.nbt.CompoundTag();
      tag.putString(
          "text",
          Component.Serializer.toJson(
              Component.literal(
                  "RESTORE JUNIOR\n"
                      + "Rebuild saved baseline · ownership retained\n"
                      + "Update baseline: /bioship_dev junior capture"),
              l.registryAccess()));
      tag.putString("billboard", "center");
      tag.putString("alignment", "center");
      tag.putInt("line_width", 300);
      tag.putInt("background", 0x66000000);
      sign.load(tag);
      sign.setPos(p.getX() + .5, p.getY() + 2.5, p.getZ() - .5);
      sign.addTag(NecromancerDenFixture.MARK);
      sign.addTag(CONTROL_LABEL);
      l.addFreshEntity(sign);
    }
  }
}
