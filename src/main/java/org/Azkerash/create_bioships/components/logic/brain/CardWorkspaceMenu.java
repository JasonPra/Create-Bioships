package org.Azkerash.create_bioships.components.logic.brain;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;

/** The server keeps the editing lease; clients receive only this card's source and identity. */
public final class CardWorkspaceMenu extends AbstractContainerMenu {
  public final CardWorkspaceTarget target;
  public final UUID identity;
  public final String initialSource;
  private long nextCompile;

  public boolean allowCompile(long tick) {
    if (tick < nextCompile) return false;
    nextCompile = tick + 10;
    return true;
  }

  public CardWorkspaceMenu(int id, CardWorkspaceTarget target) {
    this(
        id, target, ProgramCardData.identity(target.card()), ProgramCardData.source(target.card()));
  }

  private CardWorkspaceMenu(int id, CardWorkspaceTarget target, UUID identity, String source) {
    super(ComponentRegistry.CARD_WORKSPACE_MENU.get(), id);
    this.target = target;
    this.identity = identity;
    initialSource = source;
  }

  public static CardWorkspaceMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new CardWorkspaceMenu(
        id, null, buffer.readUUID(), buffer.readUtf(ProgramCardData.MAX_SOURCE));
  }

  @Override
  public boolean stillValid(Player player) {
    return target == null || target.valid(player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }
}
