package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.development.testing.network.DevelopmentToolPayload;

/** Development-only entry point; debug controls live in its client screen. */
@EventBusSubscriber(modid = "create_bioships")
public final class DevelopmentToolkitItem extends Item {
  public static final DeferredHolder<Item, DevelopmentToolkitItem> ITEM =
      DeferredHolder.create(
          Registries.ITEM, ResourceLocation.parse("create_bioships:development_toolkit"));

  public DevelopmentToolkitItem() {
    super(new Properties().stacksTo(1));
  }

  @SubscribeEvent
  public static void register(RegisterEvent e) {
    e.register(Registries.ITEM, h -> h.register(ITEM.getId(), new DevelopmentToolkitItem()));
  }

  public static boolean allowed(Player p) {
    return DevelopmentLoadout.active(p)
        && p.getInventory().contains(ITEM.get().getDefaultInstance());
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (!allowed(player)) return InteractionResultHolder.fail(player.getItemInHand(hand));
    if (player instanceof ServerPlayer server) {
      if (player.isShiftKeyDown())
        org.Azkerash.create_bioships.development.testing.network.FacingDebugSync.setFromLook(
            server);
      else PacketDistributor.sendToPlayer(server, new DevelopmentToolPayload(false));
    }
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }
}
