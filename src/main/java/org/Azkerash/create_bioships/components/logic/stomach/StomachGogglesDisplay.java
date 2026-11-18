package org.Azkerash.create_bioships.components.logic.stomach;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Reserved Create inspection hook. Intentionally no tooltip content or inventory. */
public final class StomachGogglesDisplay extends BlockEntity implements IHaveGoggleInformation {
  public StomachGogglesDisplay(BlockPos pos, BlockState state) {
    super(ComponentRegistry.STOMACH_DISPLAY.get(), pos, state);
  }

  @Override
  public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
    return false;
  }
}
