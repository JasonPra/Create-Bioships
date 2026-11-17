package org.Azkerash.create_bioships.equipment;

import com.simibubi.create.content.equipment.wrench.WrenchItem;

/** Uses Create's wrench interactions; individual blocks own their restrictions. */
public final class BioshipWrenchItem extends WrenchItem {
  // Keep our own held model instead of Create's animated cogwheel renderer.
  @Override
  public void initializeClient(
      java.util.function.Consumer<
              net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>
          consumer) {}

  public BioshipWrenchItem(Properties properties) {
    super(properties);
  }
}
