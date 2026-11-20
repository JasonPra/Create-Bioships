package org.azkerash.createbioships.components.logic.brain;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import org.azkerash.createbioships.core.brain.program.ProgramBehavior;
import org.azkerash.createbioships.core.brain.program.ProgramCardData;
import org.azkerash.createbioships.core.brain.program.ProgramCompiler;

/** Authoritative entry points shared by held cards and permission-checked brain memory. */
public final class CardWorkspaces {
  private CardWorkspaces() {}

  public static boolean openHeld(ServerPlayer player, InteractionHand hand) {
    int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
    var stack = player.getInventory().getItem(slot);
    org.azkerash.createbioships.core.brain.program.PrivateCardBackups.restore(player, stack);
    if (!ProgramCardData.editable(stack)) {
      return false;
    }
    var id = ProgramCardData.identify(stack);
    player.getInventory().setChanged();
    return open(player, new CardWorkspaceTarget.Held(player, slot, id));
  }

  public static boolean openBrain(ServerPlayer player, int menuId, int slot) {
    if (!(player.containerMenu instanceof BrainMenu menu)
        || menu.containerId != menuId
        || !menu.stillValid(player)
        || slot < 0
        || slot >= menu.capacity()) {
      return false;
    }
    var memory = menu.serverMemory();
    if (memory == null) {
      return false;
    }
    var stack = memory.getItem(slot);
    org.azkerash.createbioships.core.brain.program.PrivateCardBackups.restore(player, stack);
    if (!ProgramCardData.editable(stack)) {
      return false;
    }
    var id = ProgramCardData.identify(stack);
    memory.setChanged();
    return open(player, new CardWorkspaceTarget.Brain(memory, memory.port(), slot, id));
  }

  private static boolean open(ServerPlayer player, CardWorkspaceTarget target) {
    if (!target.valid(player)) {
      return false;
    }
    var identity = ProgramCardData.identity(target.card());
    var source = ProgramCardData.source(target.card());
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> new CardWorkspaceMenu(id, target),
            Component.literal("Cerebral IDE · C#")),
        b -> {
          b.writeUUID(identity);
          b.writeUtf(source, ProgramCardData.MAX_SOURCE);
          b.writeBoolean(ProgramCardData.executable(target.card()));
        });
    return true;
  }

  public static boolean save(
      ServerPlayer player, int menuId, java.util.UUID identity, String source) {
    if (!(player.containerMenu instanceof CardWorkspaceMenu menu)
        || menu.containerId != menuId
        || !menu.getIdentity().equals(identity)
        || menu.getTarget() == null
        || !menu.stillValid(player)) {
      return false;
    }
    if (!ProgramCardData.saveDraft(menu.getTarget().card(), source)) {
      return false;
    }
    menu.getTarget().changed();
    org.azkerash.createbioships.core.brain.program.PrivateCardBackups.remember(
        player, menu.getTarget().card());
    return true;
  }

  public record Written(boolean success, String message) {}

  public static Written compile(
      ServerPlayer player, int menuId, java.util.UUID identity, String source) {
    if (!(player.containerMenu instanceof CardWorkspaceMenu menu)
        || menu.containerId != menuId
        || !menu.getIdentity().equals(identity)
        || menu.getTarget() == null
        || !menu.stillValid(player)) {
      return new Written(false, "Card changed or access expired. Reopen the editor.");
    }
    if (!menu.allowCompile(player.level().getGameTime())) {
      return new Written(false, "Please wait a moment before compiling again.");
    }
    var result = ProgramCompiler.compile(source);
    if (!result.success()) {
      return new Written(false, result.diagnostic().toString());
    }
    var card = ProgramCardData.compiledCopy(menu.getTarget().card(), source);
    if (card.isEmpty()) {
      return new Written(false, "Unable to write this card.");
    }
    menu.getTarget().replace(card);
    menu.getTarget().changed();
    org.azkerash.createbioships.core.brain.program.PrivateCardBackups.remember(player, card);
    return new Written(
        true, "Compiled and written. Place this card in active brain memory to run it.");
  }

  /** An active server lease is the only route to another creature's runtime output. */
  public static ProgramBehavior.View output(
      ServerPlayer player, int menuId, java.util.UUID identity) {
    if (!(player.containerMenu instanceof CardWorkspaceMenu menu)
        || menu.containerId != menuId
        || !menu.getIdentity().equals(identity)
        || menu.getTarget() == null
        || !menu.stillValid(player)) {
      return new ProgramBehavior.View("Access expired.", java.util.List.of());
    }
    if (!menu.allowPoll(player.level().getGameTime())) {
      return null;
    }
    var body = menu.getTarget().body();
    if (body == null) {
      return new ProgramBehavior.View("Held card: install in a brain to run.", java.util.List.of());
    }
    return org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.programView(
        body, menu.getTarget().card());
  }
}
