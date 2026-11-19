package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.Azkerash.create_bioships.components.logic.mouth.*;
import org.Azkerash.create_bioships.core.digestion.SubLevelDigestion;

/** Player meat deposits and withdrawals change trust; autonomous transfers never enter here. */
public final class FeedingTrust {
  public static final double PER_ITEM = .1, PER_PASTE = .05, ROTTEN_PENALTY = 2;
  private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> TRUST_FOOD =
      net.minecraft.tags.TagKey.create(
          net.minecraft.core.registries.Registries.ITEM,
          net.minecraft.resources.ResourceLocation.parse("create_bioships:trust_food"));

  public record Snapshot(ServerSubLevel body, List<ItemStack> items, boolean earnsTrust) {}

  private FeedingTrust() {}

  public static boolean meat(ItemStack stack) {
    return !stack.isEmpty()
        && !stack.is(net.minecraft.world.item.Items.ROTTEN_FLESH)
        && stack.is(TRUST_FOOD);
  }

  private static double unit(ItemStack stack) {
    return stack.is(
            org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity.paste())
        ? PER_PASTE
        : meat(stack) ? PER_ITEM : 0;
  }

  private static void change(ServerSubLevel body, double amount) {
    if (body.isRemoved() || SubLevelHealth.status(body).dead()) return;
    if (amount > 0) org.Azkerash.create_bioships.core.trust.SubLevelTrustGains.add(body, amount);
    else if (amount < 0) {
      SubLevelData.trust(body, SubLevelData.trust(body) + amount);
      org.Azkerash.create_bioships.core.trust.SubLevelTrustGains.cancelParticles(body);
    }
  }

  private static ServerSubLevel recipient(BlockEntity entity) {
    if (entity == null
        || !(entity.getLevel() instanceof net.minecraft.server.level.ServerLevel level))
      return null;
    var body = CoreAssemblyRules.containing(level, entity.getBlockPos());
    if (body == null || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead())
      return null;
    return body;
  }

  public static void hand(ServerPlayer player, BlockPos pos, ItemStack stack, int accepted) {
    var body = recipient(player.serverLevel().getBlockEntity(pos));
    if (body != null && accepted > 0)
      change(
          body,
          accepted
              * (stack.is(net.minecraft.world.item.Items.ROTTEN_FLESH)
                  ? -ROTTEN_PENALTY
                  : unit(stack)));
  }

  public static Snapshot snapshot(AbstractContainerMenu menu) {
    if (menu instanceof MouthMenu mouth && mouth.intake() instanceof MouthBlockEntity be) {
      var body = recipient(be);
      return body == null
          ? null
          : new Snapshot(body, be.items().stream().map(ItemStack::copy).toList(), true);
    }
    if (menu instanceof org.Azkerash.create_bioships.components.logic.stomach.StomachMenu stomach
        && stomach.pasteIntake()
            instanceof
            org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity be) {
      var body = recipient(be);
      return body == null
          ? null
          : new Snapshot(
              body,
              List.of(
                  be.getItem(
                          org.Azkerash.create_bioships
                              .components
                              .logic
                              .stomach
                              .StomachBlockEntity
                              .PASTE_SLOT)
                      .copy()),
              true);
    }
    if (menu instanceof com.github.elenterius.biomancy.menu.StorageSacMenu) {
      var sac = ((org.Azkerash.create_bioships.mixin.StorageSacMenuAccess) menu).bioships$sac();
      var body = recipient(sac);
      if (body == null) return null;
      var handler = sac.getInventory();
      var items = new ArrayList<ItemStack>();
      for (int i = 0; i < handler.getSlots(); i++) items.add(handler.getStackInSlot(i).copy());
      boolean connected =
          SubLevelDigestion.groups(body).stream()
              .anyMatch(g -> g.sacs().contains(sac.getBlockPos()) && !g.stomachs().isEmpty());
      return new Snapshot(body, List.copyOf(items), connected);
    }
    return null;
  }

  public static void deposited(Snapshot before, Snapshot after) {
    if (before == null || after == null || before.body != after.body) return;
    double change = 0;
    var seen = new ArrayList<ItemStack>();
    var all = new ArrayList<>(before.items);
    all.addAll(after.items);
    for (var stack : all) {
      if (stack.isEmpty()
          || seen.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) continue;
      seen.add(stack);
      int old =
          before.items.stream()
              .filter(s -> ItemStack.isSameItemSameComponents(s, stack))
              .mapToInt(ItemStack::getCount)
              .sum();
      int now =
          after.items.stream()
              .filter(s -> ItemStack.isSameItemSameComponents(s, stack))
              .mapToInt(ItemStack::getCount)
              .sum();
      int delta = now - old;
      if (stack.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) {
        if (delta > 0 && after.earnsTrust) change -= delta * ROTTEN_PENALTY;
      } else if (delta < 0 || after.earnsTrust) change += delta * unit(stack);
    }
    change(after.body, change);
  }
}
