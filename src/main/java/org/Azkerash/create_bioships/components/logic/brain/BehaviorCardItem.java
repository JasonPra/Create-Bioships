package org.Azkerash.create_bioships.components.logic.brain;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;

/** Portable graph document, reserved for the later behavior editor. Nothing executes here. */
public final class BehaviorCardItem extends Item {
  private static CustomData blank() {
    var tag = new CompoundTag();
    var graph = new CompoundTag();
    graph.putInt("Version", 1);
    graph.put("Nodes", new ListTag());
    graph.put("Connections", new ListTag());
    graph.put("Variables", new ListTag());
    tag.put("BehaviorGraph", graph);
    return CustomData.of(tag);
  }

  public BehaviorCardItem() {
    super(new Properties().stacksTo(1).component(DataComponents.CUSTOM_DATA, blank()));
  }
}
