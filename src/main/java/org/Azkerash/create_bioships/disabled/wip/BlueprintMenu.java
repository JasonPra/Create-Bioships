package org.Azkerash.create_bioships.disabled.wip;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.registry.Create_bioships;

public class BlueprintMenu extends AbstractContainerMenu {
  public final LivingCoreBlockEntity model;
  public final BioshipBlueprint.Draft draft;
  private final LivingCoreBlockEntity core;
  private final BlockPos panel;
  private final int revision;
  private final ContainerData data;

  public BlueprintMenu(int id, Inventory inventory, LivingCoreBlockEntity core, BlockPos panel) {
    super(Create_bioships.BLUEPRINT_MENU.get(), id);
    this.core = core;
    this.model = core;
    this.panel = panel;
    revision = core.blueprint().revision();
    draft = new BioshipBlueprint.Draft(core.blueprint().rooms());
    data =
        core.getLevel().isClientSide
            ? new SimpleContainerData(3)
            : new ContainerData() {
              public int getCount() {
                return 3;
              }

              public int get(int i) {
                return i == 0
                    ? core.blueprint().available(core) & 65535
                    : i == 1
                        ? core.blueprint().available(core) >>> 16
                        : core.blueprint().cost(core, draft);
              }

              public void set(int i, int v) {}
            };
    addDataSlots(data);
  }

  public static BlueprintMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf b) {
    var pos = b.readBlockPos();
    var corePos = b.readBlockPos();
    var memory = b.readNbt();
    var blueprint = b.readNbt();
    var preview =
        new LivingCoreBlockEntity(corePos, Create_bioships.CORE.get().defaultBlockState());
    preview.setLevel(inventory.player.level());
    preview.body().load(memory, inventory.player.registryAccess());
    preview.blueprint().load(blueprint, inventory.player.registryAccess());
    return new BlueprintMenu(id, inventory, preview, pos);
  }

  public int available() {
    return (data.get(0) & 65535) | ((data.get(1) & 65535) << 16);
  }

  public int cost() {
    return data.get(2);
  }

  public void edit(int action) {
    draft.edit(action);
  }

  @Override
  public boolean clickMenuButton(Player player, int action) {
    if (!stillValid(player)) return false;
    if (action == 2) {
      player.closeContainer();
      return true;
    }
    if (action == 1) {
      boolean accepted = core.blueprint().confirm(core, draft, revision);
      player.displayClientMessage(Component.literal(core.blueprint().status()), false);
      if (accepted) player.closeContainer();
      return accepted;
    }
    if (action == 3) {
      draft.edit(action);
      broadcastChanges();
      return true;
    }
    if (action < 100 || action >= 17500) return false;
    draft.edit(action);
    broadcastChanges();
    return true;
  }

  @Override
  public boolean stillValid(Player p) {
    if (p.level().isClientSide) return true;
    return !core.isRemoved()
        && core.ship() != null
        && !core.body().dead()
        && !core.blueprint().busy()
        && p.getUUID().equals(core.creator())
        && p.level().getBlockState(panel).is(Create_bioships.BLUEPRINT_PANEL.get())
        && p.getEyePosition()
                .distanceToSqr(core.ship().logicalPose().transformPosition(Vec3.atCenterOf(panel)))
            <= 64;
  }

  @Override
  public ItemStack quickMoveStack(Player p, int slot) {
    return ItemStack.EMPTY;
  }
}
