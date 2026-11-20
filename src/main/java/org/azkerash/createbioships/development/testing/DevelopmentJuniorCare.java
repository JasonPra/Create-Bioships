package org.azkerash.createbioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.azkerash.createbioships.components.logic.mouth.MouthBlockEntity;
import org.azkerash.createbioships.components.logic.stomach.StomachBlockEntity;
import org.azkerash.createbioships.core.*;

/** Explicitly configured developer care, never a survival feeding mechanic or a healing cheat. */
@EventBusSubscriber(modid = "create_bioships")
public final class DevelopmentJuniorCare {
  public static final int INTERVAL = 20 * 60 * 5;

  private DevelopmentJuniorCare() {}

  public static int pasteInStomachs(ServerSubLevel body) {
    int result = 0;
    for (var part : SubLevelCreatureParts.members(body)) {
      for (var holder : part.getPlot().getLoadedChunks()) {
        for (var be : holder.getChunk().getBlockEntities().values()) {
          if (be instanceof StomachBlockEntity stomach) {
            for (var item : stomach.items()) {
              if (item.is(StomachBlockEntity.paste())) {
                result += item.getCount();
              }
            }
          }
        }
      }
    }
    return result;
  }

  public static int check(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)
        || !SubLevelData.isLiving(body)
        || SubLevelHealth.status(body).dead()) {
      return 0;
    }
    int enough =
        (int) Math.max(16, Math.ceil(SubLevelHunger.metabolism(body).multiplier() * 5 / 4) + 2);
    if (pasteInStomachs(body) >= enough) {
      return 0;
    }
    var mouths = new java.util.ArrayList<MouthBlockEntity>();
    int pending = 0;
    for (var part : SubLevelCreatureParts.members(body)) {
      for (var holder : part.getPlot().getLoadedChunks()) {
        for (var be : holder.getChunk().getBlockEntities().values()) {
          if (be instanceof MouthBlockEntity mouth) {
            mouths.add(mouth);
            for (var item : mouth.items()) {
              if (item.is(StomachBlockEntity.paste())) {
                pending += item.getCount();
              }
            }
          }
        }
      }
    }
    if (pending >= enough) {
      return 0;
    }
    var stack = new ItemStack(StomachBlockEntity.paste(), 64);
    int before = stack.getCount();
    for (var mouth : mouths) {
      stack = mouth.feed(stack, false);
      if (stack.isEmpty()) {
        break;
      }
    }
    return before - stack.getCount();
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 != 0) {
      return;
    }
    var level = event.getServer().overworld();
    if (!DevelopmentWorldSetup.isDevelopment(level)) {
      return;
    }
    var state = CoreTestState.get(level);
    if (state.getJuniorSubject() == null) {
      return;
    }
    long now = level.getGameTime();
    if (state.getJuniorFeedAt() == 0) {
      state.setJuniorFeedAt(now + INTERVAL);
      state.setDirty();
      return;
    }
    if (now < state.getJuniorFeedAt()) {
      return;
    }
    state.setJuniorFeedAt(now + INTERVAL);
    state.setDirty();
    var container = SubLevelContainer.getContainer(level);
    var body = container == null ? null : container.getSubLevel(state.getJuniorSubject());
    if (body instanceof ServerSubLevel server) {
      check(server);
    }
  }
}
