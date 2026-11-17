package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/** Read-only, paged access to the real member queues. No replacement server inventory. */
final class StomachInventoryView implements Container {
  static final int PAGE_SIZE = 27, FIELDS = 14;
  private final StomachBlockEntity anchor;
  private final List<StomachBlockEntity> members;
  private final boolean formed;
  private int page;
  private long sampled = Long.MIN_VALUE;
  private final int[] values = new int[FIELDS];

  StomachInventoryView(StomachBlockEntity anchor) {
    this.anchor = anchor;
    var core = BioshipStores.core(anchor);
    var cube = core == null ? null : StomachMultiblock.containing(core, anchor.getBlockPos());
    formed = cube != null;
    members =
        cube == null
            ? List.of(anchor)
            : cube.members().values().stream()
                .sorted(Comparator.comparingLong(s -> s.getBlockPos().asLong()))
                .toList();
  }

  int pages() {
    return Math.max(1, (members.size() * 9 + PAGE_SIZE - 1) / PAGE_SIZE);
  }

  boolean turn(int direction) {
    int next = Math.clamp(page + direction, 0, pages() - 1);
    if (next == page) return false;
    page = next;
    sampled = Long.MIN_VALUE;
    return true;
  }

  private boolean present(StomachBlockEntity organ) {
    var level = anchor.getLevel();
    return level != null
        && BioshipContainerState.entity(
                level,
                BioshipIntegration.containing(level, organ.getBlockPos()),
                organ.getBlockPos())
            == organ;
  }

  private void sample() {
    if (anchor.getLevel() == null) return;
    long now = anchor.getLevel().getGameTime();
    if (now == sampled) return;
    sampled = now;
    Arrays.fill(values, 0);
    for (var organ : members)
      if (present(organ)) {
        var data = organ.menuData();
        for (int i : new int[] {0, 1, 2, 6, 7, 8}) values[i] += data.get(i);
        values[9] += 9 * 64;
        values[10]++;
      }
    // Products are already shared across core/sac storage; do not count that ledger 27 times.
    for (int i = 3; i <= 5; i++) values[i] = anchor.menuData().get(i);
    values[11] = formed ? 1 : 0;
    values[12] = page;
    values[13] = pages();
  }

  ContainerData data() {
    return new ContainerData() {
      public int get(int index) {
        sample();
        int value = values[index / 2];
        return index % 2 == 0 ? value & 65535 : value >>> 16;
      }

      public void set(int index, int value) {}

      public int getCount() {
        return FIELDS * 2;
      }
    };
  }

  public int getContainerSize() {
    return PAGE_SIZE;
  }

  public boolean isEmpty() {
    for (int i = 0; i < PAGE_SIZE; i++) if (!getItem(i).isEmpty()) return false;
    return true;
  }

  public ItemStack getItem(int slot) {
    if (slot < 0 || slot >= PAGE_SIZE) return ItemStack.EMPTY;
    int index = page * PAGE_SIZE + slot;
    if (index >= members.size() * 9) return ItemStack.EMPTY;
    var organ = members.get(index / 9);
    return present(organ) ? organ.foodStack(index % 9) : ItemStack.EMPTY;
  }

  public ItemStack removeItem(int slot, int amount) {
    return ItemStack.EMPTY;
  }

  public ItemStack removeItemNoUpdate(int slot) {
    return ItemStack.EMPTY;
  }

  public void setItem(int slot, ItemStack stack) {}

  public void setChanged() {}

  public void clearContent() {}

  public boolean stillValid(Player player) {
    if (!present(anchor)) return false;
    if (!formed) return true;
    var core = BioshipStores.core(anchor);
    var cube = core == null ? null : StomachMultiblock.containing(core, anchor.getBlockPos());
    return cube != null && new HashSet<>(cube.members().values()).equals(new HashSet<>(members));
  }
}
