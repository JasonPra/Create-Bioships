package org.Azkerash.create_bioships.development.testing.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record CreatureStatsPayload(UUID body, Vec3 at, List<Metric> rows)
    implements CustomPacketPayload {
  private static final int MAX_ROWS = 14;
  private static final int MAX_ROW_LABEL_CHARS = 120;

  public record Metric(String text, double fraction, int color) {
    public Metric {
      if (text.length() > 120 || !Double.isFinite(fraction) || fraction < -1 || fraction > 1) {
        throw new IllegalArgumentException("Invalid statistic");
      }
    }
  }

  public CreatureStatsPayload {
    rows = List.copyOf(rows);
    if (rows.size() > MAX_ROWS
        || !Double.isFinite(at.x)
        || !Double.isFinite(at.y)
        || !Double.isFinite(at.z)) {
      throw new IllegalArgumentException("Invalid statistics snapshot");
    }
  }

  public static final Type<CreatureStatsPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:creature_stats"));

  public Type<CreatureStatsPayload> type() {
    return TYPE;
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, CreatureStatsPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.body);
            b.writeDouble(p.at.x);
            b.writeDouble(p.at.y);
            b.writeDouble(p.at.z);
            b.writeVarInt(p.rows.size());
            for (var row : p.rows) {
              b.writeUtf(row.text, MAX_ROW_LABEL_CHARS);
              b.writeDouble(row.fraction);
              b.writeInt(row.color);
            }
          },
          b -> {
            var id = b.readUUID();
            var pos = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
            int count = b.readVarInt();
            if (count < 0 || count > MAX_ROWS) {
              throw new IllegalArgumentException("Invalid statistics count");
            }
            var rows = new ArrayList<Metric>();
            for (int i = 0; i < count; i++) {
              rows.add(new Metric(b.readUtf(MAX_ROW_LABEL_CHARS), b.readDouble(), b.readInt()));
            }
            return new CreatureStatsPayload(id, pos, rows);
          });
}
