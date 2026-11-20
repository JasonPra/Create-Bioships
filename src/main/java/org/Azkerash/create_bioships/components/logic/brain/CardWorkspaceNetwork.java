package org.Azkerash.create_bioships.components.logic.brain;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;

@EventBusSubscriber(modid = "create_bioships")
public final class CardWorkspaceNetwork {
  private CardWorkspaceNetwork() {}

  public record Open(int menu, int slot) implements CustomPacketPayload {
    public static final Type<Open> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:card_workspace_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeVarInt(p.slot);
            },
            b -> new Open(b.readVarInt(), b.readVarInt()));

    public Type<Open> type() {
      return TYPE;
    }
  }

  public record Save(int menu, UUID identity, String source) implements CustomPacketPayload {
    public static final Type<Save> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:card_workspace_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeUUID(p.identity);
              b.writeUtf(p.source, ProgramCardData.MAX_SOURCE);
            },
            b -> new Save(b.readVarInt(), b.readUUID(), b.readUtf(ProgramCardData.MAX_SOURCE)));

    public Type<Save> type() {
      return TYPE;
    }
  }

  public record Result(int menu, UUID identity, boolean saved, String message, String fingerprint)
      implements CustomPacketPayload {
    public static final Type<Result> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:card_workspace_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeUUID(p.identity);
              b.writeBoolean(p.saved);
              b.writeUtf(p.message, 512);
              b.writeUtf(p.fingerprint, 64);
            },
            b ->
                new Result(
                    b.readVarInt(), b.readUUID(), b.readBoolean(), b.readUtf(512), b.readUtf(64)));

    public Type<Result> type() {
      return TYPE;
    }
  }

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) {
    var registrar = e.registrar("1");
    registrar.playToServer(
        Open.TYPE,
        Open.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player)
                    CardWorkspaces.openBrain(player, p.menu, p.slot);
                }));
    registrar.playToServer(
        Save.TYPE,
        Save.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    boolean saved = CardWorkspaces.save(player, p.menu, p.identity, p.source);
                    PacketDistributor.sendToPlayer(
                        player,
                        new Result(
                            p.menu,
                            p.identity,
                            saved,
                            saved
                                ? "Draft saved. It is not executable until compiled."
                                : "Card changed or access expired. Reopen the editor.",
                            saved ? ProgramCardData.fingerprint(p.source) : ""));
                  }
                }));
    registrar.playToClient(
        Result.TYPE,
        Result.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> org.Azkerash.create_bioships.client.components.CerebralIdeScreen.receive(p)));
  }
}
