package org.azkerash.createbioships.components.logic.brain;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.core.brain.hunting.HuntingCardData;

@EventBusSubscriber(modid = "create_bioships")
public final class HuntingCardNetwork {
  public record Save(int menu, String whitelist) implements CustomPacketPayload {
    public static final Type<Save> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:hunting_card_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeUtf(p.whitelist, HuntingCardData.MAX_TEXT);
            },
            b -> new Save(b.readVarInt(), b.readUtf(HuntingCardData.MAX_TEXT)));

    public Type<Save> type() {
      return TYPE;
    }
  }

  public record Result(int menu, String error) implements CustomPacketPayload {
    private static final int MAX_ERROR = 256;
    public static final Type<Result> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:hunting_card_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeUtf(p.error, MAX_ERROR);
            },
            b -> new Result(b.readVarInt(), b.readUtf(MAX_ERROR)));

    public Type<Result> type() {
      return TYPE;
    }
  }

  private HuntingCardNetwork() {}

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar.playToServer(
        Save.TYPE,
        Save.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player
                      && player.containerMenu instanceof HuntingCardMenu menu
                      && menu.containerId == p.menu) {
                    PacketDistributor.sendToPlayer(
                        player, new Result(p.menu, menu.save(player, p.whitelist)));
                  }
                }));
    registrar.playToClient(
        Result.TYPE,
        Result.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> org.azkerash.createbioships.client.components.HuntingCardScreen.receive(p)));
  }
}
