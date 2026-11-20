package org.Azkerash.create_bioships.components.logic.brain;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;

/** Authoritative entry points shared by held cards and permission-checked brain memory. */
public final class CardWorkspaces {
  private CardWorkspaces() {}

  public static boolean openHeld(ServerPlayer player, InteractionHand hand) {
    int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
    var stack = player.getInventory().getItem(slot);
    org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.restore(player, stack);
    if (!ProgramCardData.editable(stack)) return false;
    var id = ProgramCardData.identify(stack);
    player.getInventory().setChanged();
    return open(player, new CardWorkspaceTarget.Held(player, slot, id));
  }

  public static boolean openBrain(ServerPlayer player, int menuId, int slot) {
    if (!(player.containerMenu instanceof BrainMenu menu)
        || menu.containerId != menuId
        || !menu.stillValid(player)
        || slot < 0
        || slot >= menu.capacity()) return false;
    var memory = menu.serverMemory();
    if (memory == null) return false;
    var stack = memory.getItem(slot);
    org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.restore(player, stack);
    if (!ProgramCardData.editable(stack)) return false;
    var id = ProgramCardData.identify(stack);
    memory.setChanged();
    return open(player, new CardWorkspaceTarget.Brain(memory, memory.port(), slot, id));
  }

  private static boolean open(ServerPlayer player, CardWorkspaceTarget target) {
    if (!target.valid(player)) return false;
    var identity = ProgramCardData.identity(target.card());
    var source = ProgramCardData.source(target.card());
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> new CardWorkspaceMenu(id, target),
            Component.literal("Cerebral IDE · C#")),
        b -> {
          b.writeUUID(identity);
          b.writeUtf(source, ProgramCardData.MAX_SOURCE);
        });
    return true;
  }

  public static boolean save(
      ServerPlayer player, int menuId, java.util.UUID identity, String source) {
    if (!(player.containerMenu instanceof CardWorkspaceMenu menu)
        || menu.containerId != menuId
        || !menu.identity.equals(identity)
        || menu.target == null
        || !menu.stillValid(player)) return false;
    if (!ProgramCardData.saveDraft(menu.target.card(), source)) return false;
    menu.target.changed();
    org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.remember(
        player, menu.target.card());
    return true;
  }

  public record Written(boolean success, String message) {}

  public static Written compile(
      ServerPlayer player, int menuId, java.util.UUID identity, String source) {
    if (!(player.containerMenu instanceof CardWorkspaceMenu menu)
        || menu.containerId != menuId
        || !menu.identity.equals(identity)
        || menu.target == null
        || !menu.stillValid(player))
      return new Written(false, "Card changed or access expired. Reopen the editor.");
    if (!menu.allowCompile(player.level().getGameTime()))
      return new Written(false, "Please wait a moment before compiling again.");
    var result = org.Azkerash.create_bioships.core.brain.program.ProgramCompiler.compile(source);
    if (!result.success()) return new Written(false, result.diagnostic().toString());
    var card = ProgramCardData.compiledCopy(menu.target.card(), source);
    if (card.isEmpty()) return new Written(false, "Unable to write this card.");
    menu.target.replace(card);
    menu.target.changed();
    org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.remember(player, card);
    return new Written(
        true, "Compiled and written. Place this card in active brain memory to run it.");
  }
}
