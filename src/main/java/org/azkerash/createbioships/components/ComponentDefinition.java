package org.azkerash.createbioships.components;

import java.util.*;
import java.util.function.*;
import net.minecraft.world.level.block.Block;

/** Immutable construction recipe. Registry handles and block behavior remain separate concerns. */
public record ComponentDefinition<B extends Block>(
    String id, Supplier<B> constructor, List<Consumer<B>> setup) {
  public ComponentDefinition(String id, Supplier<B> constructor) {
    this(id, constructor, List.of());
  }

  public ComponentDefinition {
    Objects.requireNonNull(id);
    Objects.requireNonNull(constructor);
    setup = List.copyOf(setup);
  }

  public ComponentDefinition<B> withBehavior(Consumer<B> behavior) {
    var steps = new ArrayList<>(setup);
    steps.add(Objects.requireNonNull(behavior));
    return new ComponentDefinition<>(id, constructor, steps);
  }

  public void initialize(B block) {
    setup.forEach(step -> step.accept(block));
  }
}
