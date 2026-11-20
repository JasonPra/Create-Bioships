package org.azkerash.createbioships.components.logic.diagnostics;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded, read-only diagnostic rows; no executable commands or private card source cross here. */
public record DebugPanelPayload(
    UUID grid, BlockPos panel, long tick, boolean opening, List<Row> rows)
    implements CustomPacketPayload {
  public static final int MAX_ROWS = 96, MAX_TEXT = 384;

  public record Row(String section, String title, String detail, String suggestion, int severity) {}

  public DebugPanelPayload {
    rows = List.copyOf(rows);
    if (rows.size() > MAX_ROWS) {
      throw new IllegalArgumentException("Too many diagnostic rows");
    }
  }

  public static final Type<DebugPanelPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:debug_panel"));

  private static String bounded(String value) {
    return value.substring(0, Math.min(MAX_TEXT, value.length()));
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, DebugPanelPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.grid);
            b.writeBlockPos(p.panel);
            b.writeLong(p.tick);
            b.writeBoolean(p.opening);
            b.writeVarInt(p.rows.size());
            for (var row : p.rows) {
              b.writeUtf(bounded(row.section), MAX_TEXT);
              b.writeUtf(bounded(row.title), MAX_TEXT);
              b.writeUtf(bounded(row.detail), MAX_TEXT);
              b.writeUtf(bounded(row.suggestion), MAX_TEXT);
              b.writeVarInt(Math.clamp(row.severity, 0, 2));
            }
          },
          b -> {
            var grid = b.readUUID();
            var panel = b.readBlockPos();
            long tick = b.readLong();
            boolean opening = b.readBoolean();
            int count = b.readVarInt();
            if (count < 0 || count > MAX_ROWS) {
              throw new IllegalArgumentException("Invalid diagnostic row count");
            }
            var rows = new ArrayList<Row>();
            for (int i = 0; i < count; i++) {
              rows.add(
                  new Row(
                      b.readUtf(MAX_TEXT),
                      b.readUtf(MAX_TEXT),
                      b.readUtf(MAX_TEXT),
                      b.readUtf(MAX_TEXT),
                      Math.clamp(b.readVarInt(), 0, 2)));
            }
            return new DebugPanelPayload(grid, panel, tick, opening, rows);
          });

  public Type<DebugPanelPayload> type() {
    return TYPE;
  }

  public record Request(UUID grid, BlockPos panel) implements CustomPacketPayload {
    public static final Type<Request> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:debug_panel_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeUUID(p.grid);
              b.writeBlockPos(p.panel);
            },
            b -> new Request(b.readUUID(), b.readBlockPos()));

    public Type<Request> type() {
      return TYPE;
    }
  }
}
