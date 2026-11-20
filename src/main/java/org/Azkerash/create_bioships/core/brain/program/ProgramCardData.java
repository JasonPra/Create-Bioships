package org.Azkerash.create_bioships.core.brain.program;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Versioned source stored on a physical card. Drafts never execute. */
public final class ProgramCardData {
  public static final int MAX_SOURCE = ProgramLimits.SOURCE;
  public static final String KEY = "CerebralProgram";
  public static final String TEMPLATE =
      "using System;\n"
          + "class Program {\n"
          + "    static void Main() {\n"
          + "        for (int pulse = 0; pulse < 3; pulse++) {\n"
          + "            Console.WriteLine(\"Pulse \" + pulse);\n"
          + "            Brain.Wait(20);\n"
          + "        }\n"
          + "    }\n"
          + "}\n";

  private static final net.neoforged.neoforge.registries.DeferredRegister<
          net.minecraft.core.component.DataComponentType<?>>
      COMPONENTS =
          net.neoforged.neoforge.registries.DeferredRegister.create(
              net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, "create_bioships");
  public static final net.neoforged.neoforge.registries.DeferredHolder<
          net.minecraft.core.component.DataComponentType<?>,
          net.minecraft.core.component.DataComponentType<CustomData>>
      PRIVATE_PROGRAM =
          COMPONENTS.register(
              "cerebral_program",
              () ->
                  net.minecraft.core.component.DataComponentType.<CustomData>builder()
                      .persistent(CustomData.CODEC)
                      .networkSynchronized(publicMarkerCodec())
                      .build());

  private static net.minecraft.network.codec.StreamCodec<
          net.minecraft.network.RegistryFriendlyByteBuf, CustomData>
      publicMarkerCodec() {
    return net.minecraft.network.codec.StreamCodec.of(
        (buffer, value) -> {
          var tag = value.copyTag();
          boolean known = tag.hasUUID("Identity") && tag.hasUUID("Revision");
          buffer.writeBoolean(known);
          if (known) {
            buffer.writeUUID(tag.getUUID("Identity"));
            buffer.writeUUID(tag.getUUID("Revision"));
          }
        },
        buffer -> {
          var marker = new CompoundTag();
          if (buffer.readBoolean()) {
            marker.putUUID("Identity", buffer.readUUID());
            marker.putUUID("Revision", buffer.readUUID());
          }
          return CustomData.of(marker);
        });
  }

  public static void register(net.neoforged.bus.api.IEventBus bus) {
    COMPONENTS.register(bus);
  }

  private ProgramCardData() {}

  public static boolean editable(ItemStack stack) {
    return (stack.is(ComponentRegistry.BLANK_BEHAVIOR_CARD.get())
            || stack.is(ComponentRegistry.PROGRAM_BEHAVIOR_CARD.get()))
        && stack.getCount() == 1;
  }

  /**
   * Stack packets contain only opaque identity/revision markers, never source or executable state.
   */
  public static CompoundTag document(ItemStack stack) {
    var value = stack.get(PRIVATE_PROGRAM.get());
    return value != null
        ? value.copyTag()
        : stack
            .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getCompound(KEY);
  }

  public static String source(ItemStack stack) {
    var d = document(stack);
    String source = d.contains("Source") ? d.getString("Source") : TEMPLATE;
    return source.length() <= MAX_SOURCE ? source : source.substring(0, MAX_SOURCE);
  }

  public static UUID identity(ItemStack stack) {
    var d = document(stack);
    return d.hasUUID("Identity") ? d.getUUID("Identity") : null;
  }

  public static UUID identify(ItemStack stack) {
    var id = identity(stack);
    if (id != null) {
      if (stack.get(PRIVATE_PROGRAM.get()) == null) {
        write(stack, document(stack));
      }
      return id;
    }
    id = UUID.randomUUID();
    var d = document(stack);
    d.putUUID("Identity", id);
    write(stack, d);
    return id;
  }

  public static boolean saveDraft(ItemStack stack, String source) {
    if (!editable(stack) || source == null || source.length() > MAX_SOURCE) {
      return false;
    }
    var d = document(stack);
    d.putInt("Version", 1);
    d.putString("Language", "csharp");
    if (!source.equals(d.getString("Source"))) {
      d.putBoolean("Compiled", false);
    }
    d.putString("Source", source);
    write(stack, d);
    return true;
  }

  /** The server compiler must accept the exact source before this is called. */
  public static ItemStack compiledCopy(ItemStack original, String source) {
    if (!editable(original) || !ProgramCompiler.compile(source).success()) {
      return ItemStack.EMPTY;
    }
    var result = original.transmuteCopy(ComponentRegistry.PROGRAM_BEHAVIOR_CARD.get(), 1);
    identify(result);
    saveDraft(result, source);
    var d = document(result);
    d.putBoolean("Compiled", true);
    d.putString("CompiledHash", fingerprint(source));
    write(result, d);
    return result;
  }

  public static boolean executable(ItemStack stack) {
    if (!stack.is(ComponentRegistry.PROGRAM_BEHAVIOR_CARD.get()) || stack.getCount() != 1) {
      return false;
    }
    var d = document(stack);
    String source = d.getString("Source");
    return d.getBoolean("Compiled")
        && d.getInt("Version") == 1
        && d.getString("Language").equals("csharp")
        && d.contains("Source")
        && source.length() <= MAX_SOURCE
        && d.getString("CompiledHash").equals(fingerprint(source));
  }

  public static String fingerprint(String source) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static void write(ItemStack stack, CompoundTag document) {
    var previous = stack.get(PRIVATE_PROGRAM.get());
    if (previous == null || !previous.copyTag().equals(document) || !document.hasUUID("Revision")) {
      document.putUUID("Revision", UUID.randomUUID());
    }
    stack.set(PRIVATE_PROGRAM.get(), CustomData.of(document));
    var legacy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    if (legacy.contains(KEY)) {
      legacy.remove(KEY);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
    }
  }
}
