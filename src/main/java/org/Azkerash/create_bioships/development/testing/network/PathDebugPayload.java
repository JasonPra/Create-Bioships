package org.Azkerash.create_bioships.development.testing.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.pathfinding.*;

/** Bounded world-space snapshot of the actual active route and current next waypoint. */
public record PathDebugPayload(
    UUID body, SubLevelPathfinder.Mode mode, Vec3 position, List<Vec3> points, int next)
    implements CustomPacketPayload {
  public static final Type<PathDebugPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:path_debug"));

  public PathDebugPayload {
    points = List.copyOf(points);
    if (points.size() > SubLevelPaths.MAX_POINTS
        || next < 0
        || (!points.isEmpty() && next >= points.size())
        || points.isEmpty() && next != 0) {
      throw new IllegalArgumentException("Invalid path snapshot");
    }
    if (!finite(position) || points.stream().anyMatch(p -> !finite(p))) {
      throw new IllegalArgumentException("Invalid path position");
    }
  }

  private static boolean finite(Vec3 v) {
    return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
  }

  private static void vector(RegistryFriendlyByteBuf b, Vec3 p) {
    b.writeDouble(p.x);
    b.writeDouble(p.y);
    b.writeDouble(p.z);
  }

  private static Vec3 vector(RegistryFriendlyByteBuf b) {
    return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, PathDebugPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.body);
            b.writeEnum(p.mode);
            vector(b, p.position);
            b.writeVarInt(p.points.size());
            for (var v : p.points) {
              vector(b, v);
            }
            b.writeVarInt(p.next);
          },
          b -> {
            var id = b.readUUID();
            var mode = b.readEnum(SubLevelPathfinder.Mode.class);
            var pos = vector(b);
            int n = b.readVarInt();
            if (n < 0 || n > SubLevelPaths.MAX_POINTS) {
              throw new IllegalArgumentException("Invalid path count");
            }
            var points = new ArrayList<Vec3>();
            for (int i = 0; i < n; i++) {
              points.add(vector(b));
            }
            return new PathDebugPayload(id, mode, pos, points, b.readVarInt());
          });

  public Type<PathDebugPayload> type() {
    return TYPE;
  }
}
