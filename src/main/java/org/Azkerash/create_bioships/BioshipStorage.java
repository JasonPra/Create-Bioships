package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.Azkerash.create_bioships.creature.ResourceAccount;

/** Shared material account: core first, connected sacs next, pending stomach output last. */
final class BioshipStorage {
  static final int CORE_CAPACITY = 256;

  static boolean fuel(String id) {
    return id.equals("biomancy:nutrient_paste");
  }

  static List<BlockEntity> sacks(LivingCoreBlockEntity core) {
    var result = new LinkedHashMap<net.minecraft.core.BlockPos, BlockEntity>();
    for (var stomach : DigestiveSystem.stomachs(core))
      for (var sac : BioshipStores.connected(stomach)) result.put(sac.getBlockPos(), sac);
    return List.copyOf(result.values());
  }

  static int deposit(LivingCoreBlockEntity core, String id, int count) {
    int room =
        Math.max(
            0,
            CORE_CAPACITY
                - core.adaptation().ledger().snapshot().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum());
    int reserved =
        EmergencyReserve.targets().entrySet().stream()
            .filter(e -> !e.getKey().equals(id))
            .mapToInt(e -> Math.max(0, e.getValue() - core.adaptation().material(e.getKey())))
            .sum();
    room = Math.max(0, room - reserved);
    int moved = Math.min(room, count);
    core.adaptation().ledger().credit(id, moved);
    count -= moved;
    for (var sac : sacks(core)) {
      moved = Math.min(count, Math.max(0, BioshipStores.SAC_CAPACITY - BioshipStores.total(sac)));
      if (moved > 0) {
        BioshipStores.change(sac, id, moved);
        count -= moved;
      }
      if (count == 0) break;
    }
    core.setChanged();
    return count;
  }

  static void flush(LivingCoreBlockEntity core) {
    refill(core);
    for (var stomach : DigestiveSystem.stomachs(core))
      for (var id : stomach.localMaterialIds())
        if (!fuel(id)) {
          int before = stomach.localMaterial(id);
          int left = deposit(core, id, before);
          if (before > left) stomach.spendLocal(id, before - left);
        }
  }

  static Map<String, Integer> snapshot(LivingCoreBlockEntity core) {
    var all = new HashMap<String, Integer>(core.adaptation().ledger().snapshot());
    for (var stomach : DigestiveSystem.stomachs(core))
      for (var id : stomach.localMaterialIds())
        all.merge(id, stomach.localMaterial(id), Integer::sum);
    for (var sac : sacks(core)) {
      var data = sac.getPersistentData().getCompound(BioshipStores.MATERIALS);
      for (var id : data.getAllKeys()) all.merge(id, data.getInt(id), Integer::sum);
      var inventory =
          core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, sac.getBlockPos(), null);
      if (inventory != null)
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
          var stack = inventory.extractItem(slot, Integer.MAX_VALUE, true);
          if (!stack.isEmpty()
              && !LivingCoreBlockEntity.isMeat(stack)
              && (stack.is(BioshipIntegration.constructionMaterial())
                  || BioshipNutrition.fuelValue(stack) > 0))
            all.merge(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                Integer::sum);
        }
    }
    return Map.copyOf(all);
  }

  static Map<String, Integer> metabolicStock(LivingCoreBlockEntity core) {
    var stock = new HashMap<>(snapshot(core));
    if (!core.hasMouth() || DigestiveSystem.stomachs(core).isEmpty()) {
      int mouths = core.hasMouth() ? 0 : 1,
          stomachs = DigestiveSystem.stomachs(core).isEmpty() ? 1 : 0;
      stock.computeIfPresent(
          MaterialCost.FLESH, (id, count) -> Math.max(0, count - 8 * mouths - 16 * stomachs));
      stock.computeIfPresent(
          MaterialCost.FIBER, (id, count) -> Math.max(0, count - 4 * mouths - 8 * stomachs));
      stock.computeIfPresent(
          MaterialCost.BONE, (id, count) -> Math.max(0, count - 2 * mouths - 4 * stomachs));
    }
    return stock;
  }

  static int amount(LivingCoreBlockEntity core, String id) {
    return snapshot(core).getOrDefault(id, 0);
  }

  static boolean spend(LivingCoreBlockEntity core, String id, int count) {
    if (count < 0 || amount(core, id) < count) return false;
    int left = withdrawExternal(core, id, count);
    if (left > 0 && !core.adaptation().ledger().spend(id, left)) {
      // A native handler may change between its simulated and actual extraction.
      // Restore any material already withdrawn before reporting a failed payment.
      account(core).credit(id, count - left);
      return false;
    }
    core.setChanged();
    return true;
  }

  private static int withdrawExternal(LivingCoreBlockEntity core, String id, int count) {
    int take;
    for (var sac : sacks(core)) {
      take = Math.min(count, BioshipStores.stored(sac, id));
      if (take > 0) {
        BioshipStores.change(sac, id, -take);
        count -= take;
      }
      var inventory =
          core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, sac.getBlockPos(), null);
      if (inventory != null)
        for (int slot = 0; slot < inventory.getSlots() && count > 0; slot++) {
          var stack = inventory.getStackInSlot(slot);
          if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id))
            count -= inventory.extractItem(slot, count, false).getCount();
        }
    }
    for (var stomach : DigestiveSystem.stomachs(core)) {
      take = Math.min(count, stomach.localMaterial(id));
      if (take > 0) {
        stomach.spendLocal(id, take);
        count -= take;
      }
    }
    return count;
  }

  private static void refill(LivingCoreBlockEntity core) {
    for (var entry : EmergencyReserve.targets().entrySet()) {
      int gap = entry.getValue() - core.adaptation().material(entry.getKey());
      int external = amount(core, entry.getKey()) - core.adaptation().material(entry.getKey());
      int room =
          CORE_CAPACITY
              - core.adaptation().ledger().snapshot().values().stream()
                  .mapToInt(Integer::intValue)
                  .sum();
      int take = Math.max(0, Math.min(gap, Math.min(external, room)));
      if (take > 0) {
        int left = withdrawExternal(core, entry.getKey(), take);
        core.adaptation().ledger().credit(entry.getKey(), take - left);
        core.setChanged();
      }
    }
  }

  static ResourceAccount account(LivingCoreBlockEntity core) {
    return new ResourceAccount() {
      public int material(String id) {
        return amount(core, id);
      }

      public boolean spend(String id, int count) {
        return BioshipStorage.spend(core, id, count);
      }

      public void credit(String id, int count) {
        int left = deposit(core, id, count);
        if (left > 0) {
          var stomach = DigestiveSystem.stomach(core);
          if (stomach != null) stomach.creditLocal(id, left);
          else core.adaptation().ledger().credit(id, left);
        }
      }
    };
  }

  static int capacity(LivingCoreBlockEntity core) {
    return CORE_CAPACITY
        + BioshipStores.STOMACH_CAPACITY * DigestiveSystem.stomachs(core).size()
        + BioshipStores.SAC_CAPACITY * sacks(core).size();
  }

  static int products(LivingCoreBlockEntity core) {
    return snapshot(core).entrySet().stream()
        .filter(e -> !fuel(e.getKey()))
        .mapToInt(Map.Entry::getValue)
        .sum();
  }
}
