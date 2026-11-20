package org.azkerash.createbioships.registration;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.*;

/** Shared registration plumbing; block behavior and per-block properties stay with their owners. */
public final class BlockRegistration {
  public record RegisteredBlock<B extends Block>(
      DeferredBlock<B> block, DeferredItem<BlockItem> item) {}

  private BlockRegistration() {}

  /** A fresh mutable properties object is obtained only when NeoForge constructs the block. */
  public static <B extends Block> Supplier<B> createFactory(
      Function<BlockBehaviour.Properties, B> constructor,
      Supplier<BlockBehaviour.Properties> properties) {
    Objects.requireNonNull(constructor);
    Objects.requireNonNull(properties);
    return () -> constructor.apply(properties.get());
  }

  /** Register both handles with the same path without resolving either during class loading. */
  public static <B extends Block> RegisteredBlock<B> register(
      DeferredRegister.Blocks blocks,
      DeferredRegister.Items items,
      String id,
      Supplier<B> factory) {
    var block = register(blocks, id, factory);
    return new RegisteredBlock<>(block, ItemRegistration.registerBlockItem(items, block));
  }

  /** Internal blocks can deliberately omit an item and creative-tab entry. */
  public static <B extends Block> DeferredBlock<B> register(
      DeferredRegister.Blocks blocks, String id, Supplier<B> factory) {
    return blocks.register(id, factory);
  }

  /** Fixture blocks use an existing registration event and likewise receive no implicit item. */
  public static <B extends Block> void register(
      RegisterEvent.RegisterHelper<Block> registry,
      DeferredHolder<Block, B> handle,
      Supplier<B> factory) {
    registry.register(handle.getId(), factory.get());
  }
}
