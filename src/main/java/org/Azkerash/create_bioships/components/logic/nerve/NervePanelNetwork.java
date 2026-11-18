package org.Azkerash.create_bioships.components.logic.nerve;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "create_bioships")
public final class NervePanelNetwork {
  private NervePanelNetwork() {}

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    var r = event.registrar("1");
    r.playToServer(
        NervePanelAction.TYPE,
        NervePanelAction.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof net.minecraft.server.level.ServerPlayer player)
                    NervePanelControl.handle(player, p);
                }));
    r.playToClient(
        NervePanelPayload.TYPE,
        NervePanelPayload.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> org.Azkerash.create_bioships.client.components.NervePanelScreen.receive(p)));
  }
}
