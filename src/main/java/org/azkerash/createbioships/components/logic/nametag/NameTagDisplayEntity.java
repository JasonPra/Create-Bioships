package org.azkerash.createbioships.components.logic.nametag;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.inventory.OrganInventory;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.component.ComponentState;
import org.azkerash.createbioships.core.naming.SubLevelNames;

/**
 * Slot and offsets bind to the sublevel component store; a detached panel keeps its portable data.
 */
public final class NameTagDisplayEntity extends OrganInventory {
  /** Offsets are serialized in sixteenths of a block, relative to the panel's centre. */
  public static final int OFFSET_UNITS_PER_BLOCK = 16;

  public static final int MAX_OFFSET_BLOCKS = 16;
  public static final int DEFAULT_Y_UNITS = 24;

  public static final int MIN = -MAX_OFFSET_BLOCKS * OFFSET_UNITS_PER_BLOCK,
      MAX = MAX_OFFSET_BLOCKS * OFFSET_UNITS_PER_BLOCK;

  public static final class Offset implements ComponentState {
    private int x, y = DEFAULT_Y_UNITS, z;

    public CompoundTag save(HolderLookup.Provider r) {
      var t = new CompoundTag();
      t.putInt("LabelX", x);
      t.putInt("LabelY", y);
      t.putInt("LabelZ", z);
      return t;
    }

    public void load(CompoundTag t, HolderLookup.Provider r) {
      x = Math.clamp(t.getInt("LabelX"), MIN, MAX);
      y = t.contains("LabelY") ? Math.clamp(t.getInt("LabelY"), MIN, MAX) : DEFAULT_Y_UNITS;
      z = Math.clamp(t.getInt("LabelZ"), MIN, MAX);
    }

    public int getX() {
      return this.x;
    }

    public int getY() {
      return this.y;
    }

    public int getZ() {
      return this.z;
    }
  }

  private final Offset detached = new Offset();

  public NameTagDisplayEntity(BlockPos p, BlockState s) {
    super(ComponentRegistry.NAME_TAG_DISPLAY_ENTITY.get(), p, s, 1);
  }

  @Override
  protected ComponentState localState() {
    return detached;
  }

  public Offset offset() {
    return (Offset) ownedState();
  }

  public static boolean accepts(ItemStack stack) {
    return stack.is(Items.NAME_TAG)
        && stack.has(DataComponents.CUSTOM_NAME)
        && !stack.get(DataComponents.CUSTOM_NAME).getString().isBlank();
  }

  public Component label() {
    var item = getItem(0);
    return accepts(item) ? item.get(DataComponents.CUSTOM_NAME) : Component.empty();
  }

  @Override
  public boolean canPlaceItem(int slot, ItemStack stack) {
    return slot == 0 && accepts(stack);
  }

  @Override
  public int getMaxStackSize() {
    return 1;
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    if (stack.isEmpty() || canPlaceItem(slot, stack)) {
      super.setItem(slot, stack);
    }
  }

  public boolean editable(Player player) {
    if (!(level instanceof ServerLevel server)) {
      return true;
    }
    var body = CoreAssemblyRules.containing(server, worldPosition);
    return body == null
        || !SubLevelData.isLiving(body)
        || org.azkerash.createbioships.core.access.SubLevelInteractionInterceptor.isOwner(
            player.getUUID(), body);
  }

  @Override
  public boolean stillValid(Player player) {
    return editable(player) && super.stillValid(player);
  }

  public void setOffset(int x, int y, int z) {
    var o = offset();
    o.x = Math.clamp(x, MIN, MAX);
    o.y = Math.clamp(y, MIN, MAX);
    o.z = Math.clamp(z, MIN, MAX);
    setChanged();
  }

  @Override
  public void setChanged() {
    super.setChanged();
    if (level instanceof ServerLevel server) {
      var b = CoreAssemblyRules.containing(server, worldPosition);
      if (b != null) {
        SubLevelNames.request(b);
      }
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level instanceof ServerLevel server) {
      var b = CoreAssemblyRules.containing(server, worldPosition);
      if (b != null) {
        SubLevelNames.request(b);
      }
    }
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.create_bioships.name_tag_displayer");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return editable(player) ? new NameTagDisplayMenu(id, inv, this) : null;
  }

  @Override
  protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.saveAdditional(t, r);
    t.merge(offset().save(r));
  }

  @Override
  protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.loadAdditional(t, r);
    offset().load(t, r);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider r) {
    return saveWithoutMetadata(r);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public void onDataPacket(
      net.minecraft.network.Connection c,
      ClientboundBlockEntityDataPacket p,
      HolderLookup.Provider r) {
    if (p.getTag() != null) {
      loadWithComponents(p.getTag(), r);
    }
  }
}
