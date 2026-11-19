package org.Azkerash.create_bioships.components.logic.stomach;

import com.github.elenterius.biomancy.api.serum.Serum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** Stomach-owned gene state and effects. New component types implement their own rules. */
public final class StomachGenes {
  public static final int LONGEVITY_CAPACITY = 5, MAX_LONGEVITY = 3, MAX_EFFICIENCY = 3;
  private static final int[] EXTRA_COMPONENT_CHANCE = {0, 20, 30, 35};
  private final StomachBlockEntity stomach;

  StomachGenes(StomachBlockEntity stomach) {
    this.stomach = stomach;
  }

  public boolean longevity() {
    return stomach.state().longevity > 0;
  }

  public int longevityTier() {
    return stomach.state().longevity;
  }

  public int efficiency() {
    return stomach.state().efficiency;
  }

  public int capacityBonus() {
    return stomach.state().longevity * LONGEVITY_CAPACITY;
  }

  public int pastePerMeal() {
    return 1 + stomach.state().efficiency;
  }

  public int extraComponentChance() {
    return EXTRA_COMPONENT_CHANCE[stomach.state().efficiency];
  }

  public boolean accepts(Serum serum) {
    return serum == ComponentRegistry.LONGEVITY_GENE.get()
            && stomach.state().longevity < MAX_LONGEVITY
        || serum == ComponentRegistry.EFFICIENCY_GENE.get()
            && stomach.state().efficiency < MAX_EFFICIENCY;
  }

  public ItemInteractionResult inject(Player player, ItemStack injector) {
    if (stomach.getLevel() instanceof ServerLevel level) {
      var body = CoreAssemblyRules.containing(level, stomach.getBlockPos());
      if (body == null || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead())
        return ItemInteractionResult.FAIL;
    }
    return org.Azkerash.create_bioships.compat.biomancy.BioInjectorIntegration.inject(
        stomach.getLevel(), player, injector, this::accepts, this::apply);
  }

  private void apply(Serum serum) {
    if (serum == ComponentRegistry.LONGEVITY_GENE.get()) stomach.state().longevity++;
    else stomach.state().efficiency++;
    stomach.setChanged();
    if (stomach.getLevel() instanceof ServerLevel level) {
      var body = CoreAssemblyRules.containing(level, stomach.getBlockPos());
      if (body != null) SubLevelHunger.refreshCapacity(body);
      level.sendBlockUpdated(
          stomach.getBlockPos(), stomach.getBlockState(), stomach.getBlockState(), 2);
    }
  }

  /**
   * Called once when the meal is rolled; pending outputs persist across blocked storage/reloads.
   */
  public void addExtraComponent(java.util.List<ItemStack> outputs, RandomSource random) {
    if (stomach.state().efficiency == 0 || random.nextInt(100) >= extraComponentChance()) return;
    var eligible =
        outputs.stream()
            .filter(stack -> !stack.isEmpty() && !stack.is(StomachBlockEntity.paste()))
            .toList();
    if (!eligible.isEmpty())
      outputs.add(eligible.get(random.nextInt(eligible.size())).copyWithCount(1));
  }

  public CompoundTag save() {
    var tag = new CompoundTag();
    tag.putInt("Longevity", stomach.state().longevity);
    tag.putInt("Efficiency", stomach.state().efficiency);
    return tag;
  }

  public void load(CompoundTag tag) {
    stomach.state().longevity = Math.clamp(tag.getInt("Longevity"), 0, MAX_LONGEVITY);
    stomach.state().efficiency = Math.clamp(tag.getInt("Efficiency"), 0, MAX_EFFICIENCY);
  }
}
