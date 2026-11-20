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
  private final CardWorkspaceTarget target;
  private final UUID identity;
  private final String initialSource;
  private final boolean initiallyCompiled;
  private long nextCompile, nextPoll;

  public boolean allowPoll(long tick) {
    if (tick < nextPoll) {
      return false;
    }
    nextPoll = tick + 10;
    return true;
  }

  public boolean allowCompile(long tick) {
    if (tick < nextCompile) {
      return false;
    }
    nextCompile = tick + 10;
    return true;
  }

  public CardWorkspaceMenu(int id, CardWorkspaceTarget target) {
    this(
        id,
        target,
        ProgramCardData.identity(target.card()),
        ProgramCardData.source(target.card()),
        ProgramCardData.executable(target.card()));
  }

  private CardWorkspaceMenu(
      int id, CardWorkspaceTarget target, UUID identity, String source, boolean compiled) {
    super(ComponentRegistry.CARD_WORKSPACE_MENU.get(), id);
    this.target = target;
    this.identity = identity;
    initialSource = source;
    initiallyCompiled = compiled;
  }

  public static CardWorkspaceMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new CardWorkspaceMenu(
        id,
        null,
        buffer.readUUID(),
        buffer.readUtf(ProgramCardData.MAX_SOURCE),
        buffer.readBoolean());
  }

  @Override
  public boolean stillValid(Player player) {
    return target == null || target.valid(player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }

  public CardWorkspaceTarget getTarget() {
    return this.target;
  }

  public UUID getIdentity() {
    return this.identity;
  }

  public String getInitialSource() {
    return this.initialSource;
  }

  public boolean isInitiallyCompiled() {
    return this.initiallyCompiled;
  }
}
