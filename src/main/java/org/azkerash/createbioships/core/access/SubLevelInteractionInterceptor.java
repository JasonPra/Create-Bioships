package org.azkerash.createbioships.core.access;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.core.CoreAssemblyRules;

/** Checks player actions before native placement/destruction; no per-sublevel polling. */
@EventBusSubscriber(modid = CreateBioships.MODID)
public final class SubLevelInteractionInterceptor {
  private SubLevelInteractionInterceptor() {}

  /** Shared authorization for future tools: owner or an explicitly invited player, fail closed. */
  public static boolean canModify(UUID player, ServerSubLevel body) {
    return player != null && body != null && SubLevelPermissions.canInspect(body, player);
  }

  public static boolean isOwner(UUID player, ServerSubLevel body) {
    return player != null && body != null && player.equals(SubLevelPermissions.get(body).owner());
  }

  /** Safe component extraction remains owner-only, independently of invited building access. */
  public static boolean canRemoveComponent(UUID player, ServerSubLevel body) {
    return isOwner(player, body);
  }

  /** Living tissue receives attacks instead of vanilla mining, even for its owner. */
  public static boolean routesMiningToCombat(ServerSubLevel body) {
    return body != null
        && org.azkerash.createbioships.core.SubLevelData.isLiving(body)
        && !org.azkerash.createbioships.core.SubLevelHealth.status(body).dead();
  }

  public static boolean canBreak(UUID player, ServerSubLevel body) {
    return canModify(player, body);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void breaking(BlockEvent.BreakEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }
    var body = CoreAssemblyRules.containing(level, event.getPos());
    if (body != null && !canBreak(event.getPlayer().getUUID(), body)) {
      // Do not cancel LeftClickBlock/AttackEntity: combat must still process punches and weapons.
      event.setCanceled(true);
    }
  }

  private static boolean canPlaceAt(Player player, ServerLevel level, BlockPos pos) {
    var body = CoreAssemblyRules.containing(level, pos);
    return body == null || canModify(player.getUUID(), body);
  }

  /** Check both the clicked support and destination, including clicks from outside a plot. */
  public static boolean canPlace(
      Player player, ServerLevel level, BlockPos clicked, Direction face) {
    return canPlaceAt(player, level, clicked)
        && (face == null || canPlaceAt(player, level, clicked.relative(face)));
  }

  private static boolean deniedAction(
      Player player,
      ServerLevel level,
      net.minecraft.world.item.ItemStack stack,
      BlockPos pos,
      Direction face) {
    if (stack.getItem() instanceof BlockItem && !canPlace(player, level, pos, face)) {
      return true;
    }
    if (player.isShiftKeyDown()
        && (stack.getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem
            || stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH))) {
      var body = CoreAssemblyRules.containing(level, pos);
      return body != null && !canRemoveComponent(player.getUUID(), body);
    }
    return false;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
    if (event.getLevel() instanceof ServerLevel level
        && deniedAction(
            event.getEntity(), level, event.getItemStack(), event.getPos(), event.getFace())) {
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.FAIL);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void useItem(net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent event) {
    var player = event.getPlayer();
    if (player != null
        && event.getLevel() instanceof ServerLevel level
        && deniedAction(player, level, event.getItemStack(), event.getPos(), event.getFace())) {
      // Also catches direct ItemStack use before placement. Broad post-placement snapshots can
      // contain incidental chunk-load/animation changes belonging to unrelated Sable bodies.
      event.cancelWithResult(net.minecraft.world.ItemInteractionResult.FAIL);
    }
  }
}
