package org.azkerash.createbioships.core.legs;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "create_bioships")
public record LegPosePayload(UUID body, List<SubLevelLegs.Pose> poses)
    implements CustomPacketPayload {
  public static final Type<LegPosePayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:leg_pose"));
  public static final StreamCodec<RegistryFriendlyByteBuf, LegPosePayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.body);
            b.writeVarInt(p.poses.size());
            for (var pose : p.poses) {
              b.writeBlockPos(pose.cell());
              for (var v :
                  java.util.stream.Stream.concat(
                          pose.joints().stream(), pose.collisionEndpoints().stream())
                      .toList()) {
                b.writeDouble(v.x);
                b.writeDouble(v.y);
                b.writeDouble(v.z);
              }
            }
          },
          b -> {
            var id = b.readUUID();
            int count = b.readVarInt();
            if (count < 0 || count > 4096) {
              throw new IllegalArgumentException("Leg pose count");
            }
            var poses = new ArrayList<SubLevelLegs.Pose>();
            for (int i = 0; i < count; i++) {
              var cell = b.readBlockPos();
              var points = new ArrayList<Vec3>();
              for (int j = 0; j < 4; j++) {
                points.add(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
              }
              var endpoints = new ArrayList<Vec3>();
              for (int j = 0; j < 6; j++) {
                endpoints.add(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
              }
              poses.add(new SubLevelLegs.Pose(cell, List.copyOf(points), List.copyOf(endpoints)));
            }
            return new LegPosePayload(id, List.copyOf(poses));
          });

  @Override
  public Type<LegPosePayload> type() {
    return TYPE;
  }

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    event
        .registrar("2")
        .playToClient(
            TYPE,
            CODEC,
            (p, c) ->
                c.enqueueWork(
                    () -> org.azkerash.createbioships.client.legs.CrabLegRenderer.receive(p)));
  }
}
