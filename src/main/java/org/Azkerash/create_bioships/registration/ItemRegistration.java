package org.Azkerash.create_bioships.registration;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.*;

/** Shared item construction and registration, with no eager resolution of registry handles. */
public final class ItemRegistration {
  private ItemRegistration() {}

  public static <I extends Item> DeferredItem<I> register(
      DeferredRegister.Items items, String id, Supplier<I> factory) {
    return items.register(id, factory);
  }

  /**
   * Each invocation receives its own mutable properties, inside the deferred registration phase.
   */
  public static <I extends Item> DeferredItem<I> register(
      DeferredRegister.Items items,
      String id,
      Function<Item.Properties, I> constructor,
      Supplier<Item.Properties> properties) {
    Objects.requireNonNull(constructor);
    Objects.requireNonNull(properties);
    return register(items, id, () -> constructor.apply(properties.get()));
  }

  public static <I extends Item> DeferredItem<I> registerNonStackable(
      DeferredRegister.Items items, String id, Function<Item.Properties, I> constructor) {
    return register(items, id, constructor, () -> new Item.Properties().stacksTo(1));
  }

  /** Block items retain the block's registry path and NeoForge's default block-item properties. */
  public static DeferredItem<BlockItem> registerBlockItem(
      DeferredRegister.Items items, DeferredBlock<? extends Block> block) {
    return items.registerSimpleBlockItem(block);
  }

  /** Development items register within their existing event, without joining a creative tab. */
  public static <I extends Item> void register(
      RegisterEvent.RegisterHelper<Item> registry,
      DeferredHolder<Item, I> handle,
      Supplier<I> factory) {
    registry.register(handle.getId(), factory.get());
  }
}
