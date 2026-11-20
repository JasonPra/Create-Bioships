package org.Azkerash.create_bioships.components.logic.brain;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.core.brain.BrainInventory;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;

/**
 * An editing lease points to one physical card, never to an arbitrary client-supplied inventory.
 */
public sealed interface CardWorkspaceTarget
    permits CardWorkspaceTarget.Held, CardWorkspaceTarget.Brain {
  ItemStack card();

  boolean valid(Player player);

  void changed();

  void replace(ItemStack card);

  default dev.ryanhcode.sable.sublevel.ServerSubLevel body() {
    return null;
  }

  record Held(Player owner, int slot, UUID identity) implements CardWorkspaceTarget {
    public ItemStack card() {
      return owner.getInventory().getItem(slot);
    }

    public boolean valid(Player player) {
      return player == owner
          && slot >= 0
          && slot < owner.getInventory().getContainerSize()
          && ProgramCardData.editable(card())
          && identity.equals(ProgramCardData.identity(card()));
    }

    public void replace(ItemStack card) {
      owner.getInventory().setItem(slot, card);
    }

    public void changed() {
      owner.getInventory().setChanged();
    }
  }

  record Brain(BrainInventory memory, BlockPos port, int slot, UUID identity)
      implements CardWorkspaceTarget {
    public ItemStack card() {
      return memory.getItem(slot);
    }

    public boolean valid(Player player) {
      return slot >= 0
          && slot < memory.capacity()
          && memory.validAt(player, port)
          && ProgramCardData.editable(card())
          && identity.equals(ProgramCardData.identity(card()));
    }

    public void replace(ItemStack card) {
      memory.setItem(slot, card);
    }

    public void changed() {
      memory.setChanged();
    }

    public dev.ryanhcode.sable.sublevel.ServerSubLevel body() {
      return memory.body();
    }
  }
}
