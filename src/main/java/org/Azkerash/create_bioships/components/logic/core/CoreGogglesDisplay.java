package org.Azkerash.create_bioships.components.logic.core;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** A synchronized inspection snapshot; all authoritative health belongs to the sublevel. */
public final class CoreGogglesDisplay extends BlockEntity implements IHaveGoggleInformation {
  private CompoundTag inspection = new CompoundTag(), coreSnapshot = new CompoundTag();
  private boolean recoveredDead;

  public void restoreRecoveredCore() {
    if (!getBlockState().is(ComponentRegistry.CORE.get())
        || !(level instanceof ServerLevel server)) {
      return;
    }
    var body = CoreAssemblyRules.containing(server, worldPosition);
    if (body != null
        && SubLevelHealth.blocks(body).stream()
                .filter(pos -> server.getBlockState(pos).is(ComponentRegistry.CORE.get()))
                .count()
            == 1
        && (recoveredDead || coreSnapshot.getCompound(SubLevelData.HEALTH).getBoolean("dead"))) {
      SubLevelData.restoreDeadCore(body, coreSnapshot);
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    restoreRecoveredCore();
  }

  @Override
  public void saveToItem(
      net.minecraft.world.item.ItemStack stack, HolderLookup.Provider registries) {
    super.saveToItem(stack, registries);
    if (!getBlockState().is(ComponentRegistry.CORE.get())) {
      return;
    }
    var body =
        level instanceof ServerLevel server
            ? CoreAssemblyRules.containing(server, worldPosition)
            : null;
    if (recoveredDead || body != null && SubLevelData.isLiving(body)) {
      stack.update(
          net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
          net.minecraft.world.item.component.CustomData.EMPTY,
          data -> {
            var tag = data.copyTag();
            tag.putBoolean("RecoveredDeadCore", true);
            return net.minecraft.world.item.component.CustomData.of(tag);
          });
    }
  }

  public CoreGogglesDisplay(BlockPos pos, BlockState state) {
    super(ComponentRegistry.CORE_DISPLAY.get(), pos, state);
  }

  public static <T extends BlockEntity>
      net.minecraft.world.level.block.entity.BlockEntityTicker<T> ticker(
          net.minecraft.world.level.Level level,
          net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
    return level.isClientSide || type != ComponentRegistry.CORE_DISPLAY.get()
        ? null
        : (world, pos, state, entity) -> {
          if (world.getGameTime() % 10 == 0) {
            ((CoreGogglesDisplay) entity).update();
          }
        };
  }

  public boolean dead() {
    return inspection.getBoolean("dead");
  }

  public void update() {
    if (!(level instanceof ServerLevel server)) {
      return;
    }
    var body = CoreAssemblyRules.containing(server, worldPosition);
    var next = new CompoundTag();
    if (body != null) {
      var status = SubLevelHealth.status(body);
      next.putDouble("current", status.current());
      next.putDouble("maximum", status.maximum());
      var armor = SubLevelArmor.status(body);
      next.putDouble("armor", armor.current());
      next.putDouble("maximumArmor", armor.maximum());
      next.putBoolean("dead", status.dead());
      next.putString("cause", status.causeOfDeath());
      next.putInt("hunger", SubLevelData.hunger(body));
      next.putInt("maximumHunger", SubLevelData.maximumHunger(body));
      next.putBoolean("hungerPaused", SubLevelData.hungerState(body).paused());
      next.putDouble("trust", SubLevelData.trust(body));
      var respiration = SubLevelOxygen.status(body);
      next.putDouble("oxygen", respiration.current());
      next.putDouble("airSupply", respiration.supply());
      next.putDouble("airUse", respiration.demand());
      next.putInt("breathingLungs", respiration.breathingLungs());
      next.putInt("breathingGills", respiration.breathingGills());
      next.putInt("lungs", respiration.lungs());
      next.putInt("gills", respiration.gills());
      next.putBoolean("oxygenPaused", SubLevelData.oxygen(body).paused());
      next.putInt(
          "sicknessTicks",
          org.Azkerash.create_bioships.core.digestion.SubLevelSickness.remaining(body));
      var effects = new net.minecraft.nbt.ListTag();
      var owner =
          CoreAssemblyRules.isCreature(body)
              ? org.Azkerash.create_bioships.combat.CombatAdapter.target(body)
              : null;
      if (owner != null) {
        for (var effect : owner.getActiveEffects()) {
          effects.add(effect.save());
        }
      }
      next.put("effects", effects);
    }
    if (inspection.equals(next)) {
      return;
    }
    inspection = next;
    setChanged();
    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
  }

  @Override
  public net.minecraft.world.item.ItemStack getIcon(boolean sneaking) {
    return Create_bioships.BIOSHIP_GOGGLES.get().getDefaultInstance();
  }

  @Override
  public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
    tooltip.add(Component.empty());
    tooltip.add(Component.empty());
    tooltip.add(Component.literal("Amoeba · Sublevel"));
    if (!inspection.contains("maximum")) {
      tooltip.add(Component.literal("Unassembled"));
    } else {
      tooltip.add(
          meter(
              "Health",
              "health",
              inspection.getDouble("current"),
              inspection.getDouble("maximum")));
      tooltip.add(
          meter(
              "Armor",
              "armor",
              inspection.getDouble("armor"),
              inspection.getDouble("maximumArmor")));
      tooltip.add(
          Component.literal("Hunger: ")
              .append(
                  Component.literal(" ".repeat(inspection.getInt("maximumHunger") * 2 + 2))
                      .withStyle(
                          style ->
                              style.withInsertion(
                                  "create_bioships:hunger/"
                                      + inspection.getInt("hunger")
                                      + "/"
                                      + inspection.getInt("maximumHunger"))))
              .append(
                  Component.literal(
                      " "
                          + inspection.getInt("hunger")
                          + "/"
                          + inspection.getInt("maximumHunger")
                          + (inspection.getBoolean("hungerPaused") ? " (paused)" : ""))));
      tooltip.add(
          Component.literal("Air:    ")
              .append(
                  Component.literal(" ".repeat(28))
                      .withStyle(
                          style ->
                              style.withInsertion(
                                  "create_bioships:oxygen/"
                                      + inspection.getDouble("oxygen")
                                      + "/"
                                      + SubLevelOxygen.MAXIMUM)))
              .append(
                  Component.literal(
                      " "
                          + Math.round(
                              100 * inspection.getDouble("oxygen") / SubLevelOxygen.MAXIMUM)
                          + "%"
                          + (inspection.getBoolean("oxygenPaused") ? " (paused)" : ""))));
      tooltip.add(
          Component.literal(
              String.format(
                  java.util.Locale.ROOT,
                  "  Air: %.0f/s supply · %.0f/s use",
                  inspection.getDouble("airSupply"),
                  inspection.getDouble("airUse"))));
      tooltip.add(
          Component.literal(
              "  Breathing: "
                  + inspection.getInt("breathingLungs")
                  + "/"
                  + inspection.getInt("lungs")
                  + " lungs · "
                  + inspection.getInt("breathingGills")
                  + "/"
                  + inspection.getInt("gills")
                  + " gills"));
      tooltip.add(meter("Trust", "trust", inspection.getDouble("trust"), 100));
      if (inspection.getInt("sicknessTicks") > 0) {
        tooltip.add(
            Component.literal(
                "Digestive sickness: " + (inspection.getInt("sicknessTicks") + 19) / 20 + "s"));
      }
      for (var entry : inspection.getList("effects", 10)) {
        var effect = net.minecraft.world.effect.MobEffectInstance.load((CompoundTag) entry);
        if (effect == null) {
          continue;
        }
        var id =
            net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(
                effect.getEffect().value());
        int seconds = Math.max(0, effect.getDuration() / 20);
        var label = Component.translatable(effect.getDescriptionId());
        if (effect.getAmplifier() > 0) {
          label.append(" " + (effect.getAmplifier() + 1));
        }
        label.append(
            effect.isInfiniteDuration()
                ? " ∞"
                : String.format(java.util.Locale.ROOT, " %d:%02d", seconds / 60, seconds % 60));
        tooltip.add(
            Component.literal("   ")
                .withStyle(style -> style.withInsertion("create_bioships:effect/" + id))
                .append(label));
      }
      if (inspection.getBoolean("dead")) {
        tooltip.add(Component.literal("Dead · " + inspection.getString("cause")));
      }
    }
    return true;
  }

  private static Component meter(String label, String kind, double value, double maximum) {
    return Component.literal(label + ": " + (label.length() < 6 ? " " : ""))
        .append(
            Component.literal(" ".repeat(28))
                .withStyle(
                    style ->
                        style.withInsertion(
                            "create_bioships:bar/" + kind + "/" + value + "/" + maximum)))
        .append(
            Component.literal(
                String.format(java.util.Locale.ROOT, " %.1f / %.1f", value, maximum)));
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("inspection", inspection);
    if (getBlockState().is(ComponentRegistry.CORE.get()) && level instanceof ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, worldPosition);
      if (body != null) {
        coreSnapshot = SubLevelData.coreSnapshot(body);
      }
    }
    tag.put("CoreSnapshot", coreSnapshot);
    tag.putBoolean("RecoveredDeadCore", recoveredDead);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    inspection = tag.getCompound("inspection");
    coreSnapshot = tag.getCompound("CoreSnapshot");
    recoveredDead = tag.getBoolean("RecoveredDeadCore");
    restoreRecoveredCore();
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    var tag = new CompoundTag();
    tag.put("inspection", inspection.copy());
    return tag;
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
