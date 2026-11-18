package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** Native test execution and repeatable cleanup; no alternate creature behavior. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CoreTestCommands {
  private static final String FIXTURE = "create_bioships:regression_fixture";

  private CoreTestCommands() {}

  public static boolean development(ServerLevel level) {
    return DevelopmentWorldSetup.isDevelopment(level);
  }

  public static int start(ServerLevel level)
      throws com.simibubi.create.content.contraptions.AssemblyException {
    var state = CoreTestState.get(level);
    if (!state.built || state.running) return 0;
    var layout = state.layout();
    if (layout.cores().stream()
        .anyMatch(p -> !level.getBlockState(p).is(ComponentRegistry.CORE.get()))) return 0;
    var result =
        SimAssemblyHelper.assembleFromSingleBlock(
            level, layout.tip().below(), layout.tip(), false, true);
    if (result == null || !(result.subLevel() instanceof ServerSubLevel body)) return 0;
    state.running = true;
    state.owned.add(body.getUniqueId());
    state.setDirty();
    own(level, state, body);
    return 1;
  }

  private static void own(ServerLevel level, CoreTestState state, ServerSubLevel body) {
    org.Azkerash.create_bioships.core.SubLevelData.hungerPaused(body, true);
    if (state.owned.add(body.getUniqueId())) state.setDirty();
    var data = body.getUserDataTag();
    if (data == null || !"core_split".equals(data.getString(FIXTURE))) {
      data = data == null ? new net.minecraft.nbt.CompoundTag() : data.copy();
      data.putString(FIXTURE, "core_split");
      body.setUserDataTag(data);
    }
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
  }

  public static void observe(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (!state.running) return;
    var bounds = state.layout().arena().inflate(1);
    for (var body : SubLevelContainer.getContainer(level).getAllSubLevels())
      if (!body.isRemoved()
          && (state.owned.contains(body.getUniqueId())
              || CoreAssemblyRules.isCreature(body)
                  && bounds.contains(
                      body.logicalPose()
                          .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock())))))
        own(level, state, body);
  }

  private static Component copyableCombatResults(ServerLevel level) {
    var results = CombatFixture.results(level);
    var failures = results.stream().filter(result -> result.contains(": FAIL")).toList();
    var status = CombatFixture.status(level);
    return Component.literal(status + " · " + failures.size() + " failures")
        .append(copyLink(" [Copy failures]", status + "\n" + String.join("\n", failures)))
        .append(copyLink(" [Copy all results]", status + "\n" + String.join("\n", results)));
  }

  private static Component copyLink(String label, String report) {
    return Component.literal(label)
        .withStyle(
            style ->
                style
                    .withColor(net.minecraft.ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, report))
                    .withHoverEvent(
                        new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Copy the saved report to your clipboard"))));
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    observe(event.getServer().overworld());
    CombatFixture.tick(event.getServer().overworld());
    PotionFixture.tick(event.getServer().overworld());
    CoreArrowFixture.tick(event.getServer().overworld());
    DigestionFixture.tick(event.getServer().overworld());
  }

  public static void reset(ServerLevel level, CoreSplitFixture.Layout layout) {
    var state = CoreTestState.get(level);
    observe(level);
    state.running = false;
    var container = SubLevelContainer.getContainer(level);
    for (var body : List.copyOf(container.getAllSubLevels())) {
      var data = body.getUserDataTag();
      if (state.owned.contains(body.getUniqueId())
          || data != null && "core_split".equals(data.getString(FIXTURE))) {
        var logical = new AABB(body.getPlot().getCenterBlock()).inflate(10);
        for (var glue : level.getEntitiesOfClass(HoneyGlueEntity.class, logical)) glue.discard();
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
      }
    }
    for (var glue : level.getEntitiesOfClass(HoneyGlueEntity.class, layout.arena())) glue.discard();
    for (var item :
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, layout.arena()))
      item.discard();
    state.owned.clear();
    state.origin = layout.base();
    state.built = true;
    state.setDirty();
    CoreSplitFixture.build(level, layout);
  }

  @SubscribeEvent
  public static void commands(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("dev_tests")
                .requires(source -> source.hasPermission(2))
                .executes(
                    ctx -> {
                      DevelopmentNavigatorItem.open(ctx.getSource().getPlayerOrException());
                      return 1;
                    })
                .then(
                    Commands.literal("give")
                        .executes(
                            ctx -> {
                              ctx.getSource()
                                  .getPlayerOrException()
                                  .addItem(
                                      new net.minecraft.world.item.ItemStack(
                                          DevelopmentNavigatorItem.ITEM.get()));
                              return 1;
                            })));
    var root = Commands.literal("core_test").requires(source -> source.hasPermission(2));
    for (var action : List.of("start", "reset", "status"))
      root.then(
          Commands.literal(action)
              .executes(
                  ctx -> {
                    var source = ctx.getSource();
                    var level = source.getLevel();
                    var state = CoreTestState.get(level);
                    if (!state.built) {
                      source.sendFailure(
                          Component.literal("No core regression fixture in this world."));
                      return 0;
                    }
                    if (action.equals("reset")) {
                      reset(level, state.layout());
                      source.sendSuccess(
                          () ->
                              Component.literal(
                                  "Core test reset: closed unassembled pyramid restored."),
                          false);
                      return 1;
                    }
                    if (action.equals("start")) {
                      try {
                        int result = start(level);
                        if (result == 0)
                          source.sendFailure(
                              Component.literal(
                                  "Test already running or changed. Reset it first."));
                        return result;
                      } catch (com.simibubi.create.content.contraptions.AssemblyException failure) {
                        source.sendFailure(
                            Component.literal("Cannot assemble test: " + failure.getMessage()));
                        return 0;
                      }
                    }
                    source.sendSuccess(
                        () ->
                            Component.literal(
                                "Core test: "
                                    + (state.running ? "running" : "ready")
                                    + ", tracked grids: "
                                    + state.owned.size()),
                        false);
                    return 1;
                  }));
    event.getDispatcher().register(root);
    var combat = Commands.literal("combat_test").requires(source -> source.hasPermission(2));
    for (var action : List.of("start", "reset", "status", "results"))
      combat.then(
          Commands.literal(action)
              .executes(
                  ctx -> {
                    var source = ctx.getSource();
                    var level = source.getLevel();
                    if (!CoreTestState.get(level).combatBuilt) {
                      source.sendFailure(Component.literal("No combat fixture in this world."));
                      return 0;
                    }
                    if (action.equals("start")) return CombatFixture.start(level);
                    if (action.equals("reset")) CombatFixture.reset(level);
                    if (action.equals("results")) {
                      if (source.getPlayer() == null) {
                        for (var result : CombatFixture.results(level))
                          source.sendSuccess(() -> Component.literal(result), false);
                      } else {
                        source.sendSuccess(() -> copyableCombatResults(level), false);
                        return 1;
                      }
                    }
                    source.sendSuccess(() -> Component.literal(CombatFixture.status(level)), false);
                    return 1;
                  }));
    event.getDispatcher().register(combat);
    var potions = Commands.literal("potion_test").requires(source -> source.hasPermission(2));
    for (var action : List.of("start", "reset", "status", "results"))
      potions.then(
          Commands.literal(action)
              .executes(
                  ctx -> {
                    var level = ctx.getSource().getLevel();
                    if (action.equals("reset")) PotionFixture.reset(level);
                    if (action.equals("start")) return PotionFixture.start(level);
                    if (action.equals("results"))
                      ctx.getSource()
                          .sendSuccess(
                              () ->
                                  Component.literal(PotionFixture.status(level))
                                      .append(
                                          copyLink(
                                              " [Copy results]",
                                              String.join("\n", PotionFixture.results(level)))),
                              false);
                    else
                      ctx.getSource()
                          .sendSuccess(() -> Component.literal(PotionFixture.status(level)), false);
                    return 1;
                  }));
    event.getDispatcher().register(potions);
    var digestion = Commands.literal("digestion_test").requires(source -> source.hasPermission(2));
    for (var action : List.of("start", "reset", "status"))
      digestion.then(
          Commands.literal(action)
              .executes(
                  ctx -> {
                    var level = ctx.getSource().getLevel();
                    if (action.equals("reset")) {
                      DigestionFixture.reset(level);
                      return 1;
                    }
                    if (action.equals("start")) return DigestionFixture.start(level);
                    ctx.getSource()
                        .sendSuccess(
                            () -> Component.literal(DigestionFixture.status(level)), false);
                    return 1;
                  }));
    event.getDispatcher().register(digestion);
    var arrow = Commands.literal("arrow_test").requires(source -> source.hasPermission(2));
    for (var action : List.of("start", "reset", "status"))
      arrow.then(
          Commands.literal(action)
              .executes(
                  ctx -> {
                    var level = ctx.getSource().getLevel();
                    var state = CoreTestState.get(level);
                    if (!state.arrowBuilt) return 0;
                    if (action.equals("start")) return CoreArrowFixture.start(level);
                    if (action.equals("reset")) CoreArrowFixture.reset(level);
                    ctx.getSource()
                        .sendSuccess(
                            () -> Component.literal(CoreArrowFixture.status(level)), false);
                    return 1;
                  }));
    event.getDispatcher().register(arrow);
  }
}
