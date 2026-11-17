package org.Azkerash.create_bioships.disabled.wip;

import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.BioshipAccess;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;

public class AccessMenu extends AbstractContainerMenu {
  public record Choice(UUID id, String label, boolean animal) {}

  private final LivingCoreBlockEntity core;
  private final BlockPos pos;
  public final List<Choice> choices;
  private final ContainerData allowed;

  public AccessMenu(
      int id, Inventory inventory, LivingCoreBlockEntity core, BlockPos pos, List<Choice> choices) {
    super(Create_bioships.ACCESS_MENU.get(), id);
    this.core = core;
    this.pos = pos;
    this.choices = List.copyOf(choices);
    allowed =
        core == null
            ? new SimpleContainerData(choices.size())
            : new ContainerData() {
              public int getCount() {
                return choices.size();
              }

              public int get(int i) {
                var entry = choices.get(i);
                return (entry.animal() ? core.access().animals : core.access().players)
                        .containsKey(entry.id())
                    ? 1
                    : 0;
              }

              public void set(int i, int value) {}
            };
    addDataSlots(allowed);
  }

  public static AccessMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    var pos = buffer.readBlockPos();
    int count = buffer.readVarInt();
    if (count < 0 || count > 128) throw new IllegalArgumentException("Invalid access list size");
    List<Choice> entries = new ArrayList<>();
    for (int i = 0; i < count; i++)
      entries.add(new Choice(buffer.readUUID(), buffer.readUtf(128), buffer.readBoolean()));
    return new AccessMenu(id, inventory, null, pos, entries);
  }

  public boolean allowed(int index) {
    return allowed.get(index) != 0;
  }

  public static List<Choice> choices(LivingCoreBlockEntity core) {
    Map<UUID, Choice> entries = new LinkedHashMap<>();
    core.access().players.forEach((id, name) -> entries.put(id, new Choice(id, name, false)));
    core.access().animals.forEach((id, name) -> entries.put(id, new Choice(id, name, true)));
    for (var player : core.getLevel().getServer().getPlayerList().getPlayers())
      if (!player.getUUID().equals(core.creator()))
        entries.put(
            player.getUUID(),
            new Choice(player.getUUID(), player.getGameProfile().getName(), false));
    Vec3 center = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(core.getBlockPos()));
    for (var animal :
        core.getLevel()
            .getEntitiesOfClass(
                Animal.class,
                new AABB(center, center).inflate(32),
                entity -> entity.hasCustomName()))
      entries.put(
          animal.getUUID(), new Choice(animal.getUUID(), animal.getName().getString(), true));
    return entries.values().stream().limit(128).toList();
  }

  @Override
  public boolean clickMenuButton(Player player, int index) {
    if (core == null || index < 0 || index >= choices.size() || !stillValid(player)) return false;
    var entry = choices.get(index);
    var list = entry.animal() ? core.access().animals : core.access().players;
    if (list.containsKey(entry.id())) list.remove(entry.id());
    else {
      if (list.size() >= 64) return false;
      if (entry.animal()
          && (!(core.getLevel() instanceof net.minecraft.server.level.ServerLevel server)
              || !(server.getEntity(entry.id()) instanceof Animal animal)
              || !animal.hasCustomName())) return false;
      if (entry.id().equals(core.creator())) return false;
      list.put(entry.id(), entry.label());
    }
    core.setChanged();
    BioshipAccess.tick(core);
    broadcastChanges();
    return true;
  }

  @Override
  public boolean stillValid(Player player) {
    if (player.level().isClientSide) return true;
    if (core == null
        || core.isRemoved()
        || core.ship() == null
        || !player.getUUID().equals(core.creator())
        || !player.level().getBlockState(pos).is(Create_bioships.ACCESS_PANEL.get())) return false;
    Vec3 target = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
    return player.getEyePosition().distanceToSqr(target) <= 64;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }
}
