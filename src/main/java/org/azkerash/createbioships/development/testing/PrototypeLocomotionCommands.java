package org.azkerash.createbioships.development.testing;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.locomotion.SubLevelFacing;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion;

/** Admin-only A/B controls; UUID targeting avoids ambiguous hits on articulated limbs. */
@EventBusSubscriber(modid = "create_bioships")
public final class PrototypeLocomotionCommands {
  private PrototypeLocomotionCommands() {}

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
    var root = Commands.literal("bioship_proto").requires(s -> s.hasPermission(2));
    var controller = Commands.literal("controller");
    for (var mode : PrototypeLocomotion.Mode.values()) {
      controller.then(
          Commands.literal(mode.name().toLowerCase(Locale.ROOT).replace('_', '-'))
              .then(
                  Commands.argument("body", UuidArgument.uuid())
                      .executes(
                          c -> {
                            var body = body(c.getSource(), UuidArgument.getUuid(c, "body"));
                            if (body == null) {
                              return 0;
                            }
                            PrototypeLocomotion.set(body, mode);
                            c.getSource()
                                .sendSuccess(
                                    () ->
                                        Component.literal(
                                            "Locomotion: " + mode + " for " + body.getUniqueId()),
                                    false);
                            return 1;
                          })));
    }
    root.then(controller);
    root.then(
        Commands.literal("move")
            .then(
                Commands.argument("body", UuidArgument.uuid())
                    .then(
                        Commands.argument(
                                "speed",
                                DoubleArgumentType.doubleArg(
                                    0, PrototypeLocomotion.MAX_SPEED_BLOCKS_PER_SECOND))
                            .executes(
                                c -> {
                                  var body = body(c.getSource(), UuidArgument.getUuid(c, "body"));
                                  if (body == null) {
                                    return 0;
                                  }
                                  var velocity =
                                      SubLevelFacing.world(body)
                                          .scale(DoubleArgumentType.getDouble(c, "speed"));
                                  boolean accepted =
                                      BrainMotorControls.move(body, velocity, 0, 200);
                                  c.getSource()
                                      .sendSuccess(
                                          () ->
                                              Component.literal(
                                                  accepted
                                                      ? "Forward movement requested for 10 seconds."
                                                          + " Empty the brain's behavior slot for"
                                                          + " a controlled comparison."
                                                      : "Movement blocked or creature cannot"
                                                          + " walk."),
                                          false);
                                  return accepted ? 1 : 0;
                                }))));
    event.getDispatcher().register(root);
  }

  private static ServerSubLevel body(CommandSourceStack source, UUID id) {
    var container = SubLevelContainer.getContainer(source.getLevel());
    var found = container == null ? null : container.getSubLevel(id);
    if (found instanceof ServerSubLevel body && SubLevelData.isLiving(body)) {
      return SubLevelCreatureParts.root(body);
    }
    source.sendFailure(Component.literal("Living creature is not loaded in this dimension."));
    return null;
  }
}
