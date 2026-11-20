package org.Azkerash.create_bioships.components.logic.nerve;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;

public record NervePanelPayload(
    UUID grid,
    BlockPos panel,
    String owner,
    boolean editable,
    List<SubLevelPermissions.Entry> allowed,
    List<SubLevelPermissions.Entry> candidates)
    implements CustomPacketPayload {
  public static final Type<NervePanelPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:nerve_panel"));

  private static void entries(RegistryFriendlyByteBuf b, List<SubLevelPermissions.Entry> entries) {
    b.writeVarInt(entries.size());
    for (var e : entries) {
      b.writeUUID(e.id());
      b.writeUtf(e.name().substring(0, Math.min(80, e.name().length())), 80);
      b.writeBoolean(e.player());
      b.writeBoolean(e.sublevel());
    }
  }

  private static List<SubLevelPermissions.Entry> entries(RegistryFriendlyByteBuf b) {
    int count = b.readVarInt();
    if (count < 0 || count > 128) {
      throw new IllegalArgumentException("Invalid nerve list");
    }
    var entries = new ArrayList<SubLevelPermissions.Entry>();
    for (int i = 0; i < count; i++) {
      entries.add(
          new SubLevelPermissions.Entry(
              b.readUUID(), b.readUtf(80), b.readBoolean(), b.readBoolean()));
    }
    return List.copyOf(entries);
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, NervePanelPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.grid);
            b.writeBlockPos(p.panel);
            b.writeUtf(p.owner, 80);
            b.writeBoolean(p.editable);
            entries(b, p.allowed);
            entries(b, p.candidates);
          },
          b ->
              new NervePanelPayload(
                  b.readUUID(),
                  b.readBlockPos(),
                  b.readUtf(80),
                  b.readBoolean(),
                  entries(b),
                  entries(b)));

  public Type<NervePanelPayload> type() {
    return TYPE;
  }
}
