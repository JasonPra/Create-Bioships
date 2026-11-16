package org.Azkerash.create_bioships;

import net.minecraft.world.item.*;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Shared nutritional value and task purpose; no navigation or world mutations. */
final class BioshipNutrition {
  static final int PRESSING_FOOD = 50;

  static boolean critical(LivingCoreBlockEntity core) {
    return core.foodLevel() < PRESSING_FOOD;
  }

  static int fuelValue(ItemStack stack) {
    if (stack.isEmpty()) return 0;
    if (LivingCoreBlockEntity.isMeat(stack)) return stack.is(Items.ROTTEN_FLESH) ? 0 : 70;
    String id =
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    return switch (id) {
      case "biomancy:nutrients", "biomancy:nutrient_paste", "biomancy:nutrient_bar" -> 20;
      case "biomancy:flesh_bits" -> 10;
      case "biomancy:elastic_fibers" -> 8;
      case "biomancy:bone_fragments" -> 2;
      default -> stack.is(BioshipIntegration.constructionMaterial()) ? 1 : 0;
    };
  }

  static boolean foodRelated(LivingCoreBlockEntity core, BioshipBrain.Decision decision) {
    if (decision == null) return false;
    switch (decision.action()) {
      case HUNT, SCAVENGE, REGROW_MOUTH, REGROW_STOMACH -> {
        return true;
      }
      case COLLECT_ITEM -> {
        return decision.item() != null
            && (fuelValue(decision.item().getItem()) > 0
                || BioshipScavenging.droppedBlock(decision.item().getItem()));
      }
      case CONFIGURE -> {
        var stomach = DigestiveSystem.stomach(core);
        return !core.hasMouth() || stomach == null || !stomach.connected(core);
      }
      case RECLAIM_COMPONENT -> {
        return critical(core);
      }
      case ASSIMILATE -> {
        if (decision.recycle()) return true;
        if (decision.block() == null) return false;
        var state = core.getLevel().getBlockState(decision.block());
        if (BioshipPriorities.rank(core, state, decision.block()) == 0) return true;
        if (!BioshipScavenging.component(state))
          return fuelValue(new ItemStack(state.getBlock().asItem())) > 0;
        return inventoryFuel(core, decision.block());
      }
      default -> {
        return false;
      }
    }
  }

  static boolean inventoryFuel(LivingCoreBlockEntity core, net.minecraft.core.BlockPos pos) {
    var entity = core.getLevel().getBlockEntity(pos);
    if (entity != null && BioshipStores.total(entity) > 0) return true;
    var inv = core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    if (inv != null)
      for (int slot = 0; slot < inv.getSlots(); slot++)
        if (fuelValue(inv.getStackInSlot(slot)) > 0) return true;
    return false;
  }

  static boolean movingFood(LivingCoreBlockEntity core) {
    if (core.assimilation().active() && core.assimilation().feeding()) return true;
    if (core.coreMotion().active() && core.coreMotion().feeding()) return true;
    var move = core.organMove();
    if (move == null) return false;
    if (move.mouth() || move.stomach()) return true;
    var body =
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(core.getLevel())
            .getSubLevel(move.cargo());
    if (body instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel cargo) {
      var tag = cargo.getUserDataTag();
      if (tag != null
          && (tag.getBoolean("BioshipEmergencyRecycle") || tag.getBoolean("BioshipScavenged")))
        return true;
      if (inventoryFuel(core, move.cargoBlock())) return true;
    }
    return false;
  }
}
