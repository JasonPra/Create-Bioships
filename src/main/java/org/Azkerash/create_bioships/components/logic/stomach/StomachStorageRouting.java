package org.Azkerash.create_bioships.components.logic.stomach;

import com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.*;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity;

/** Stomach-owned storage policy. The sublevel supplies connected endpoints and schedules work. */
public final class StomachStorageRouting {
  private static final TagKey<Item> COMPONENTS =
      TagKey.create(
          net.minecraft.core.registries.Registries.ITEM,
          ResourceLocation.parse("create_bioships:stored_components"));

  private StomachStorageRouting() {}

  private static IItemHandler inventory(Level level, BlockPos pos) {
    if (!(level.getBlockEntity(pos) instanceof StorageSacBlockEntity sac)) return null;
    sac.unpackLootTable(null);
    return sac.getInventory();
  }

  private static int store(Level level, List<BlockPos> sacs, ItemStack stack) {
    var remaining = stack.copy();
    for (var pos : sacs) {
      var handler = inventory(level, pos);
      if (handler != null)
        remaining = ItemHandlerHelper.insertItemStacked(handler, remaining, false);
      if (remaining.isEmpty()) break;
    }
    return stack.getCount() - remaining.getCount();
  }

  public static int intake(MouthBlockEntity mouth, List<BlockPos> stomachs, List<BlockPos> sacs) {
    var level = mouth.getLevel();
    for (int slot = 0; slot < mouth.getContainerSize(); slot++) {
      var source = mouth.getItem(slot);
      if (source.isEmpty()) continue;
      var one = source.copyWithCount(1);
      for (var pos : stomachs)
        if (level.getBlockEntity(pos) instanceof StomachBlockEntity stomach
            && stomach.enqueue(one, true).isEmpty()
            && stomach.enqueue(one, false).isEmpty()) {
          mouth.removeItem(slot, 1);
          return 1;
        }
      if (store(level, sacs, one) == 1) {
        mouth.removeItem(slot, 1);
        return 1;
      }
    }
    return 0;
  }

  /** Move a bounded number of native stacks downward; no per-sac ticker or duplicate inventory. */
  public static int compactStacks(Level level, List<BlockPos> sacs, int cursor, int budget) {
    if (sacs.size() < 2 || budget <= 0) return 0;
    var known = new HashSet<>(sacs);
    int moved = 0;
    for (int i = 0; i < Math.min(sacs.size(), 8) && moved < budget; i++) {
      var upper = sacs.get(Math.floorMod(cursor + i, sacs.size()));
      if (!known.contains(upper.below())) continue;
      var source = inventory(level, upper);
      var below = inventory(level, upper.below());
      if (source == null || below == null) continue;
      for (int slot = 0; slot < source.getSlots(); slot++) {
        var preview = source.extractItem(slot, 64, true);
        if (preview.isEmpty()) continue;
        var rest = ItemHandlerHelper.insertItemStacked(below, preview, true);
        int accepted = preview.getCount() - rest.getCount();
        if (accepted <= 0) continue;
        var extracted = source.extractItem(slot, accepted, false);
        var left = ItemHandlerHelper.insertItemStacked(below, extracted, false);
        if (!left.isEmpty()) source.insertItem(slot, left, false);
        if (extracted.getCount() > left.getCount()) moved++;
        break;
      }
    }
    return moved;
  }

  public static int service(StomachBlockEntity stomach, List<BlockPos> sacs, int budget) {
    if (sacs.isEmpty() || budget <= 0) return 0;
    int moved = 0;
    boolean full = true;
    for (int slot = 9; slot < 27; slot++)
      if (stomach.getItem(slot).isEmpty()) {
        full = false;
        break;
      }
    if (full || stomach.progress() > 0 && stomach.progress() >= stomach.duration()) {
      int candidate = -1, pasteStacks = 0;
      for (int slot = 9; slot < 27; slot++) {
        var stack = stomach.getItem(slot);
        if (stack.isEmpty()) continue;
        if (stack.is(StomachBlockEntity.paste())) pasteStacks++;
        else if (candidate < 0) candidate = slot;
      }
      if (candidate < 0 && pasteStacks > 1)
        for (int slot = 9; slot < 27; slot++)
          if (stomach.getItem(slot).is(StomachBlockEntity.paste())) {
            candidate = slot;
            break;
          }
      if (candidate >= 0) {
        int count = store(stomach.getLevel(), sacs, stomach.getItem(candidate));
        if (count > 0) {
          stomach.removeItem(candidate, count);
          moved++;
        }
      }
    }
    if (moved < budget && pull(stomach, sacs, true)) moved++;
    if (moved < budget && pull(stomach, sacs, false)) moved++;
    return moved;
  }

  private static boolean pull(StomachBlockEntity stomach, List<BlockPos> sacs, boolean paste) {
    if (paste) {
      if (!(stomach.getLevel() instanceof net.minecraft.server.level.ServerLevel level))
        return false;
      var body =
          org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(
              level, stomach.getBlockPos());
      if (body == null
          || org.Azkerash.create_bioships.core.SubLevelData.hunger(body)
              >= org.Azkerash.create_bioships.core.SubLevelData.maximumHunger(body)) return false;
      for (int slot = 9; slot < 27; slot++)
        if (stomach.getItem(slot).is(StomachBlockEntity.paste())) return false;
    }
    for (var pos : sacs) {
      var handler = inventory(stomach.getLevel(), pos);
      if (handler == null) continue;
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        var current = handler.getStackInSlot(slot);
        if (current.isEmpty()) continue;
        if (paste
            ? !current.is(StomachBlockEntity.paste())
            : !current.has(DataComponents.FOOD)
                || current.is(StomachBlockEntity.paste())
                || current.is(COMPONENTS)) continue;
        var preview = handler.extractItem(slot, 1, true);
        if (preview.isEmpty()) continue;
        var remainder =
            paste ? stomach.insert(preview, 9, 27, true) : stomach.enqueue(preview, true);
        if (!remainder.isEmpty()) continue;
        var extracted = handler.extractItem(slot, 1, false);
        if (extracted.isEmpty()) continue;
        remainder =
            paste ? stomach.insert(extracted, 9, 27, false) : stomach.enqueue(extracted, false);
        if (!remainder.isEmpty()) handler.insertItem(slot, remainder, false);
        else return true;
      }
    }
    return false;
  }
}
