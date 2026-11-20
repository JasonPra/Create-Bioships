package org.Azkerash.create_bioships.core.brain.program;

import java.util.Comparator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Restore a creative client-side copy without disclosing source in public item synchronization. */
@EventBusSubscriber(modid = "create_bioships")
public final class PrivateCardBackups {
  private static final String KEY = "create_bioships:private_card_backups";
  private static final int MAX_BACKUPS = 64;

  private PrivateCardBackups() {}

  private static CompoundTag cache(ServerPlayer player) {
    return player.getPersistentData().getCompound(KEY);
  }

  private static String key(CompoundTag d) {
    return d.hasUUID("Identity") && d.hasUUID("Revision")
        ? d.getUUID("Identity") + "." + d.getUUID("Revision")
        : null;
  }

  public static void remember(Player player, ItemStack card) {
    if (!(player instanceof ServerPlayer server) || !ProgramCardData.editable(card)) {
      return;
    }
    var document = ProgramCardData.document(card);
    var key = key(document);
    if (key == null || !document.contains("Source")) {
      return;
    }
    var cache = cache(server);
    var record = new CompoundTag();
    record.put("Document", document);
    record.putLong("Seen", server.level().getGameTime());
    cache.put(key, record);
    while (cache.size() > MAX_BACKUPS) {
      cache.remove(
          cache.getAllKeys().stream()
              .min(Comparator.comparingLong(k -> cache.getCompound(k).getLong("Seen")))
              .orElseThrow());
    }
    server.getPersistentData().put(KEY, cache);
  }

  public static boolean restore(Player player, ItemStack card) {
    if (!(player instanceof ServerPlayer server) || !ProgramCardData.editable(card)) {
      return false;
    }
    var current = ProgramCardData.document(card);
    if (current.contains("Source")) {
      remember(server, card);
      return true;
    }
    var key = key(current);
    if (key == null) {
      return false;
    }
    var saved = cache(server).getCompound(key).getCompound("Document");
    if (!saved.contains("Source")) {
      return false;
    }
    card.set(
        ProgramCardData.PRIVATE_PROGRAM.get(),
        net.minecraft.world.item.component.CustomData.of(saved));
    remember(server, card);
    return true;
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
    if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0) {
      for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
        restore(p, p.getInventory().getItem(i));
      }
    }
  }

  @SubscribeEvent
  public static void clone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone e) {
    var cache = e.getOriginal().getPersistentData().getCompound(KEY);
    if (!cache.isEmpty()) {
      e.getEntity().getPersistentData().put(KEY, cache.copy());
    }
  }
}
