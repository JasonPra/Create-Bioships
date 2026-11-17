package org.Azkerash.create_bioships.core;

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

/** A synchronized inspection snapshot; all authoritative health belongs to the sublevel. */
public final class CoreGogglesDisplay extends BlockEntity implements IHaveGoggleInformation {
  private CompoundTag inspection = new CompoundTag();

  public CoreGogglesDisplay(BlockPos pos, BlockState state) {
    super(Create_bioships.CORE_DISPLAY.get(), pos, state);
  }

  public void update() {
    if (!(level instanceof ServerLevel server)) return;
    var body = CoreAssemblyRules.containing(server, worldPosition);
    var next = new CompoundTag();
    if (body != null) {
      var status = SubLevelHealth.status(body);
      next.putDouble("current", status.current());
      next.putDouble("maximum", status.maximum());
      next.putBoolean("dead", status.dead());
      next.putString("cause", status.causeOfDeath());
    }
    if (inspection.equals(next)) return;
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
    if (!inspection.contains("maximum")) tooltip.add(Component.literal("Unassembled"));
    else {
      tooltip.add(
          Component.literal(
              String.format(
                  java.util.Locale.ROOT,
                  "Health: %.1f / %.1f",
                  inspection.getDouble("current"),
                  inspection.getDouble("maximum"))));
      if (inspection.getBoolean("dead"))
        tooltip.add(Component.literal("Dead · " + inspection.getString("cause")));
    }
    return true;
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("inspection", inspection);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    inspection = tag.getCompound("inspection");
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return saveWithoutMetadata(registries);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
