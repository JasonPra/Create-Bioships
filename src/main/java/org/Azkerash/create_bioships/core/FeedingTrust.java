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
  public static final double PER_ITEM = .1;
  private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> TRUST_FOOD =
      net.minecraft.tags.TagKey.create(
          net.minecraft.core.registries.Registries.ITEM,
          net.minecraft.resources.ResourceLocation.parse("create_bioships:trust_food"));

  public record Snapshot(ServerSubLevel body, List<ItemStack> items, boolean earnsTrust) {}

  private FeedingTrust() {}

  public static boolean meat(ItemStack stack) {
    return !stack.isEmpty() && stack.is(TRUST_FOOD);
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
    if (body != null && accepted > 0 && meat(stack))
      org.Azkerash.create_bioships.core.trust.SubLevelTrustGains.add(body, accepted * PER_ITEM);
  }

  public static Snapshot snapshot(AbstractContainerMenu menu) {
    if (menu instanceof MouthMenu mouth && mouth.intake() instanceof MouthBlockEntity be) {
      var body = recipient(be);
      return body == null
          ? null
          : new Snapshot(body, be.items().stream().map(ItemStack::copy).toList(), true);
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
    int accepted = 0;
    var seen = new ArrayList<ItemStack>();
    var all = new ArrayList<>(before.items);
    all.addAll(after.items);
    for (var stack : all) {
      if (!meat(stack) || seen.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, stack)))
        continue;
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
      accepted += now - old;
    }
    var body = after.body;
    if (body.isRemoved() || SubLevelHealth.status(body).dead()) return;
    if (accepted > 0 && after.earnsTrust)
      org.Azkerash.create_bioships.core.trust.SubLevelTrustGains.add(body, accepted * PER_ITEM);
    else if (accepted < 0) {
      SubLevelData.trust(body, SubLevelData.trust(body) + accepted * PER_ITEM);
      org.Azkerash.create_bioships.core.trust.SubLevelTrustGains.cancelParticles(body);
    }
  }
}
