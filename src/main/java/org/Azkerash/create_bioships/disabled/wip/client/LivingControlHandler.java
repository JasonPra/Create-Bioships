package org.Azkerash.create_bioships.disabled.wip.client;

import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.BioshipNetwork;
import org.Azkerash.create_bioships.disabled.wip.LivingThrottleBlock;

import dev.simulated_team.simulated.util.hold_interaction.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.*;

/** Captures vertical mouse movement through the same manager as Aeronautics' helm. */
public final class LivingControlHandler extends BlockHoldInteraction {
  private double value = 0.5;
  private int sent = 10;
  private LivingThrottleBlock.Kind kind;

  @Override
  public void startHold(Level level, Player player, BlockPos pos) {
    var state = level.getBlockState(pos);
    if (!(state.getBlock() instanceof LivingThrottleBlock control)) return;
    kind = control.kind();
    sent = state.getValue(LivingThrottleBlock.THROTTLE);
    value = sent / 20.0;
    super.startHold(level, player, pos);
  }

  @Override
  public boolean activeOnMouseMove(double yaw, double pitch) {
    if (HoldInteractionManager.unblockedShift()) value = 0.5;
    else value = Math.clamp(value - pitch / 180.0, 0, 1);
    send();
    return true;
  }

  private void send() {
    int setting = (int) Math.round(value * 20);
    if (setting != sent && getInteractionPos() != null) {
      sent = setting;
      PacketDistributor.sendToServer(new BioshipNetwork.Throttle(getInteractionPos(), setting));
    }
  }

  @Override
  public boolean activeTick(Level level, LocalPlayer player) {
    var state = level.getBlockState(getInteractionPos());
    if (!(state.getBlock() instanceof LivingThrottleBlock control)
        || control.kind() != kind
        || !BlockHoldInteraction.inInteractionRange(player, getInteractionPos().getCenter()))
      return true;
    if (HoldInteractionManager.unblockedShift()) {
      value = 0.5;
      send();
    }
    return false;
  }

  @Override
  public int getCrouchBlockingTicks() {
    return 6;
  }

  @Override
  public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui) {
    var mc = Minecraft.getInstance();
    if (hideGui
        || mc.player == null
        || !mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Create_bioships.BIOSHIP_GOGGLES.get()))
      return;
    int x = Math.max(8, Math.min(width / 2 + 24, width - 165)),
        y = Math.max(8, Math.min(height / 2 - 34, height - 76));
    graphics.fill(x, y, x + 8, y + 68, 0xbd241c20);
    graphics.fill(x + 1, y + 33, x + 7, y + 35, 0xffe8d7a2);
    int marker = y + 65 - (int) Math.round(value * 62);
    graphics.fill(x - 2, marker, x + 10, marker + 3, 0xffb5dfaf);
    Component label =
        Component.translatable(
            kind == LivingThrottleBlock.Kind.THRUST
                ? "hud.create_bioships.thrust"
                : "hud.create_bioships.vertical",
            (sent - 10) * 10);
    graphics.drawString(mc.font, label, x + 15, y + 26, 0xffe6e0ca, true);
    graphics.drawString(
        mc.font,
        Component.translatable("hud.create_bioships.center"),
        x + 15,
        y + 39,
        0xffaaa28a,
        true);
  }
}
