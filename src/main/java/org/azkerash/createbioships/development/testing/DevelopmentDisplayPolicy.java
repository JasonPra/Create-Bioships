package org.azkerash.createbioships.development.testing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Remove retired decorative captions even when their saved entity chunks load after startup. */
@EventBusSubscriber(modid = "create_bioships")
public final class DevelopmentDisplayPolicy {
  private DevelopmentDisplayPolicy() {}

  public static boolean obsolete(Entity e) {
    if (!(e instanceof Display.TextDisplay)) {
      return false;
    }
    if (e.getTags().contains("bioships.junior_habitat")) {
      return true;
    }
    if (!e.getTags().contains(NecromancerDenFixture.MARK)) {
      return false;
    }
    var text = e.saveWithoutId(new CompoundTag()).getString("text");
    return !text.contains("THE DEN OF AZ'KERASH") && !text.contains("RESTORE JUNIOR");
  }

  @SubscribeEvent
  public static void joined(EntityJoinLevelEvent e) {
    if (e.getLevel() instanceof ServerLevel level
        && DevelopmentWorldSetup.isDevelopment(level)
        && obsolete(e.getEntity())) {
      e.getEntity().discard();
      e.setCanceled(true);
    }
  }
}
