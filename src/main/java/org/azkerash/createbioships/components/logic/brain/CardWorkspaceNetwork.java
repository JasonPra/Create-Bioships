package org.azkerash.createbioships.components.logic.brain;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.core.brain.program.ProgramCardData;
import org.azkerash.createbioships.core.brain.program.ProgramLimits;

/** Private editor packets; handlers validate the active menu and card identity before mutation. */
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

  public record Write(int menu, UUID identity, String source) implements CustomPacketPayload {
    public static final Type<Write> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:card_workspace_write"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Write> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeUUID(p.identity);
              b.writeUtf(p.source, ProgramCardData.MAX_SOURCE);
            },
            b -> new Write(b.readVarInt(), b.readUUID(), b.readUtf(ProgramCardData.MAX_SOURCE)));

    public Type<Write> type() {
      return TYPE;
    }
  }

  public record Poll(int menu, UUID identity) implements CustomPacketPayload {
    public static final Type<Poll> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:card_workspace_poll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Poll> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeUUID(p.identity);
            },
            b -> new Poll(b.readVarInt(), b.readUUID()));

    public Type<Poll> type() {
      return TYPE;
    }
  }

  public record Output(int menu, UUID identity, String state, java.util.List<String> lines)
      implements CustomPacketPayload {
    public Output {
      lines = java.util.List.copyOf(lines);
    }

    public static final Type<Output> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:card_workspace_output"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Output> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeVarInt(p.menu);
              b.writeUUID(p.identity);
              b.writeUtf(p.state, ProgramLimits.DIAGNOSTIC_CHARS);
              if (p.lines.size() > ProgramLimits.OUTPUT_LINES) {
                throw new IllegalArgumentException("Console history exceeds limit");
              }
              b.writeVarInt(p.lines.size());
              for (var line : p.lines) {
                b.writeUtf(line, ProgramLimits.OUTPUT_LINE);
              }
            },
            b -> {
              int menu = b.readVarInt();
              UUID id = b.readUUID();
              String state = b.readUtf(ProgramLimits.DIAGNOSTIC_CHARS);
              int count = b.readVarInt();
              if (count < 0 || count > ProgramLimits.OUTPUT_LINES) {
                throw new IllegalArgumentException("Invalid console history");
              }
              var lines = new java.util.ArrayList<String>();
              for (int i = 0; i < count; i++) {
                lines.add(b.readUtf(ProgramLimits.OUTPUT_LINE));
              }
              return new Output(menu, id, state, lines);
            });

    public Type<Output> type() {
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
              b.writeUtf(p.message, ProgramLimits.DIAGNOSTIC_CHARS);
              b.writeUtf(p.fingerprint, ProgramLimits.FINGERPRINT_HEX_CHARS);
            },
            b ->
                new Result(
                    b.readVarInt(),
                    b.readUUID(),
                    b.readBoolean(),
                    b.readUtf(ProgramLimits.DIAGNOSTIC_CHARS),
                    b.readUtf(ProgramLimits.FINGERPRINT_HEX_CHARS)));

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
                  if (c.player() instanceof ServerPlayer player) {
                    CardWorkspaces.openBrain(player, p.menu, p.slot);
                  }
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
                            ProgramCardData.fingerprint(p.source)));
                  }
                }));
    registrar.playToServer(
        Write.TYPE,
        Write.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    var result = CardWorkspaces.compile(player, p.menu, p.identity, p.source);
                    PacketDistributor.sendToPlayer(
                        player,
                        new Result(
                            p.menu,
                            p.identity,
                            result.success(),
                            result.message(),
                            ProgramCardData.fingerprint(p.source)));
                  }
                }));
    registrar.playToServer(
        Poll.TYPE,
        Poll.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    var view = CardWorkspaces.output(player, p.menu, p.identity);
                    if (view != null) {
                      PacketDistributor.sendToPlayer(
                          player, new Output(p.menu, p.identity, view.state(), view.output()));
                    }
                  }
                }));
    registrar.playToClient(
        Output.TYPE,
        Output.CODEC,
        (p, c) ->
            c.enqueueWork(
                () ->
                    org.azkerash.createbioships.client.components.CerebralIdeScreen.receiveOutput(
                        p)));
    registrar.playToClient(
        Result.TYPE,
        Result.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> org.azkerash.createbioships.client.components.CerebralIdeScreen.receive(p)));
  }
}
