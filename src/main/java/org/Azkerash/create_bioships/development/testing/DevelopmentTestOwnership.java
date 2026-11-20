package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;

/** The first real player joining a development save owns its otherwise unowned test subjects. */
@EventBusSubscriber(modid = "create_bioships")
public final class DevelopmentTestOwnership {
  private DevelopmentTestOwnership() {}

  public static void assign(ServerSubLevel body) {
    var level = body.getLevel();
    if (!DevelopmentWorldSetup.isDevelopment(level)) {
      return;
    }
    var owner = CoreTestState.get(level).getDevelopmentOwner();
    if (owner != null) {
      SubLevelPermissions.assignIfUnowned(body, owner);
    }
  }

  @SubscribeEvent
  public static void joined(
      net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) {
      return;
    }
    var level = player.getServer().overworld();
    if (!DevelopmentWorldSetup.isDevelopment(level)) {
      return;
    }
    var state = CoreTestState.get(level);
    if (state.getDevelopmentOwner() == null) {
      state.setDevelopmentOwner(player.getUUID());
      state.setDirty();
    }
    var container = SubLevelContainer.getContainer(level);
    if (container != null) {
      for (var entry : container.getAllSubLevels()) {
        if (entry instanceof ServerSubLevel body
            && body.getUserDataTag() != null
            && (state.getOwned().contains(body.getUniqueId())
                || body.getUserDataTag().getAllKeys().stream()
                    .anyMatch(k -> k.startsWith("create_bioships") && k.contains("fixture")))) {
          assign(body);
        }
      }
    }
  }
}
