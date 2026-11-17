package org.Azkerash.create_bioships;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Reloadable tuning; future appendage adapters select a profile without replacing navigation. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class LocomotionProfiles extends SimpleJsonResourceReloadListener {
  private static volatile Map<ResourceLocation, LocomotionProfile> profiles = Map.of();
  private static final ResourceLocation DEFAULT =
      ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "tentacles");
  private static final Map<ResourceLocation, java.util.function.Predicate<LivingCoreBlockEntity>>
      adapters = new LinkedHashMap<>();

  private LocomotionProfiles() {
    super(new Gson(), "bioship_locomotion");
  }

  @SubscribeEvent
  public static void register(AddReloadListenerEvent event) {
    event.addListener(new LocomotionProfiles());
  }

  public static void registerAdapter(
      ResourceLocation profile, java.util.function.Predicate<LivingCoreBlockEntity> matches) {
    adapters.put(profile, matches);
  }

  static LocomotionProfile forCreature(LivingCoreBlockEntity core) {
    for (var entry : adapters.entrySet())
      if (entry.getValue().test(core))
        return profiles.getOrDefault(entry.getKey(), LocomotionProfile.TENTACLES);
    return profiles.getOrDefault(DEFAULT, LocomotionProfile.TENTACLES);
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
    var next = new HashMap<ResourceLocation, LocomotionProfile>();
    data.forEach(
        (id, value) -> {
          try {
            var v = value.getAsJsonObject();
            next.put(
                id,
                new LocomotionProfile(
                    v.get("walls").getAsBoolean(),
                    v.get("ceilings").getAsBoolean(),
                    v.get("grip_reach").getAsDouble(),
                    v.get("comfortable_clearance").getAsDouble(),
                    v.get("direct_distance").getAsInt(),
                    v.get("quick_nodes").getAsInt(),
                    v.get("search_nodes").getAsInt()));
          } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(LocomotionProfiles.class)
                .warn("Ignoring invalid locomotion profile {}: {}", id, error.getMessage());
          }
        });
    profiles = Map.copyOf(next);
  }
}
