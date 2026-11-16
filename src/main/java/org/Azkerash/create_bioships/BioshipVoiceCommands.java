package org.Azkerash.create_bioships;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator audition tool for the closest already upgraded mouth; never grants a free larynx. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipVoiceCommands {
  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("bioship_voice")
                .requires(s -> s.hasPermission(2))
                .then(
                    Commands.argument("sound", StringArgumentType.word())
                        .executes(
                            context -> {
                              var source = context.getSource();
                              String sound = StringArgumentType.getString(context, "sound");
                              if (!sound.equals("hurt") && !sound.equals("growl")) {
                                if (sound.length() != 1) {
                                  source.sendFailure(Component.literal("Use A-Z, hurt, or growl."));
                                  return 0;
                                }
                                try {
                                  BioshipVoicePalette.letter(sound.charAt(0));
                                } catch (IllegalArgumentException e) {
                                  source.sendFailure(Component.literal("Use A-Z, hurt, or growl."));
                                  return 0;
                                }
                              }
                              var container = SubLevelContainer.getContainer(source.getLevel());
                              FeedingMouthBlockEntity closest = null;
                              double distance = 64;
                              if (container != null)
                                for (var ship : container.getAllSubLevels())
                                  for (var actor : ship.getPlot().getBlockEntityActors())
                                    if (actor instanceof FeedingMouthBlockEntity mouth
                                        && mouth.voiced()) {
                                      double next =
                                          ship.logicalPose()
                                              .transformPosition(
                                                  Vec3.atCenterOf(mouth.getBlockPos()))
                                              .distanceToSqr(source.getPosition());
                                      if (next < distance) {
                                        closest = mouth;
                                        distance = next;
                                      }
                                    }
                              if (closest == null) {
                                source.sendFailure(
                                    Component.literal("No vocal mouth within eight blocks."));
                                return 0;
                              }
                              if (sound.length() == 1) closest.vocalize(sound.charAt(0));
                              else closest.vocalize(sound.equals("hurt"));
                              return 1;
                            })));
  }
}
