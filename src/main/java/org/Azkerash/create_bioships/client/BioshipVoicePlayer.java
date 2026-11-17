package org.Azkerash.create_bioships.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Short attack/release envelopes, mild pitch bends, and a moving mouth sound source. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class BioshipVoicePlayer {
  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;
    var inbox = mc.player.getPersistentData().getList("BioshipVoices", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(32, inbox.size()); i++) {
      var data = inbox.getCompound(i);
      if (ResourceLocation.tryParse(data.getString("Sample")) != null)
        mc.getSoundManager().play(new VocalSound(data));
    }
    mc.player.getPersistentData().remove("BioshipVoices");
  }

  private static final class VocalSound extends AbstractTickableSoundInstance {
    private final BlockPos mouth;
    private final int duration, attack;
    private final float basePitch, gain;
    private int elapsed;

    VocalSound(CompoundTag data) {
      super(
          SoundEvent.createVariableRangeEvent(ResourceLocation.parse(data.getString("Sample"))),
          SoundSource.NEUTRAL,
          SoundInstance.createUnseededRandom());
      mouth = BlockPos.of(data.getLong("Mouth"));
      duration = Math.clamp(data.getInt("Ticks"), 1, 40);
      attack = Math.clamp(data.getInt("Attack"), 1, duration);
      basePitch = Math.clamp(data.getFloat("Pitch"), .5f, 2f);
      gain = Math.clamp(data.getFloat("Volume"), 0f, 1f);
      pitch = basePitch;
      volume = gain / attack;
      position();
    }

    private void position() {
      var level = Minecraft.getInstance().level;
      if (level == null) {
        stop();
        return;
      }
      var container = SubLevelContainer.getContainer(level);
      var plot = container == null ? null : container.getPlot(new ChunkPos(mouth));
      var at =
          plot == null
              ? Vec3.atCenterOf(mouth)
              : plot.getSubLevel().logicalPose().transformPosition(Vec3.atCenterOf(mouth));
      x = at.x;
      y = at.y;
      z = at.z;
    }

    @Override
    public void tick() {
      if (++elapsed >= duration || Minecraft.getInstance().level == null) {
        stop();
        return;
      }
      position();
      volume =
          gain * Math.min(1f, (elapsed + 1f) / attack) * Math.min(1f, (duration - elapsed) / 3f);
      pitch = basePitch * (1f + .035f * (float) Math.sin(elapsed * .7));
    }
  }
}
