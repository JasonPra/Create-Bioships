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

/** Only explicit player deposits earn trust. Network transfers never enter this hook. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "create_bioships")
public final class FeedingTrust {
  public static final double PER_ITEM = .1;
  private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> TRUST_FOOD =
      net.minecraft.tags.TagKey.create(
          net.minecraft.core.registries.Registries.ITEM,
          net.minecraft.resources.ResourceLocation.parse("create_bioships:trust_food"));

  public record Snapshot(ServerSubLevel body, List<ItemStack> items) {}

  private static final Map<ServerSubLevel, Long> PARTICLES = new WeakHashMap<>();

  private FeedingTrust() {}

  public static boolean meat(ItemStack stack) {
    return !stack.isEmpty() && stack.is(TRUST_FOOD);
  }

  private static ServerSubLevel recipient(BlockEntity entity, boolean mouth) {
    if (entity == null
        || !(entity.getLevel() instanceof net.minecraft.server.level.ServerLevel level))
      return null;
    var body = CoreAssemblyRules.containing(level, entity.getBlockPos());
    if (body == null || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead())
      return null;
    if (mouth) return body;
    return SubLevelDigestion.groups(body).stream()
            .anyMatch(g -> g.sacs().contains(entity.getBlockPos()) && !g.stomachs().isEmpty())
        ? body
        : null;
  }

  public static void hand(ServerPlayer player, BlockPos pos, ItemStack stack, int accepted) {
    var body = recipient(player.serverLevel().getBlockEntity(pos), true);
    if (body != null && accepted > 0 && meat(stack)) gain(body, accepted);
  }

  public static Snapshot snapshot(AbstractContainerMenu menu) {
    if (menu instanceof MouthMenu mouth && mouth.intake() instanceof MouthBlockEntity be) {
      var body = recipient(be, true);
      return body == null
          ? null
          : new Snapshot(body, be.items().stream().map(ItemStack::copy).toList());
    }
    if (menu instanceof com.github.elenterius.biomancy.menu.StorageSacMenu) {
      var sac = ((org.Azkerash.create_bioships.mixin.StorageSacMenuAccess) menu).bioships$sac();
      var body = recipient(sac, false);
      if (body == null) return null;
      var handler = sac.getInventory();
      var items = new ArrayList<ItemStack>();
      for (int i = 0; i < handler.getSlots(); i++) items.add(handler.getStackInSlot(i).copy());
      return new Snapshot(body, List.copyOf(items));
    }
    return null;
  }

  public static void deposited(Snapshot before, Snapshot after) {
    if (before == null || after == null || before.body != after.body) return;
    int accepted = 0;
    var seen = new ArrayList<ItemStack>();
    for (var stack : after.items) {
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
      accepted += Math.max(0, now - old);
    }
    if (accepted > 0) gain(after.body, accepted);
  }

  public static void cancelParticles(ServerSubLevel body) {
    PARTICLES.remove(SubLevelCreatureParts.root(body));
  }

  private static void gain(ServerSubLevel body, int count) {
    if (body.isRemoved() || SubLevelHealth.status(body).dead()) return;
    double before = SubLevelData.trust(body);
    SubLevelData.trust(body, before + count * PER_ITEM);
    if (SubLevelData.trust(body) > before)
      PARTICLES.putIfAbsent(body, body.getLevel().getGameTime() + 5);
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void particles(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    PARTICLES
        .entrySet()
        .removeIf(
            entry -> {
              var body = entry.getKey();
              if (body.isRemoved() || SubLevelHealth.status(body).dead()) return true;
              if (body.getLevel().getGameTime() < entry.getValue()) return false;
              var cells = SubLevelHealth.blocks(body);
              int count = Math.min(6, cells.size());
              for (int i = 0; i < count; i++) {
                var cell =
                    cells.get(
                        (i * cells.size() / count + body.getLevel().random.nextInt(cells.size()))
                            % cells.size());
                var at =
                    body.logicalPose()
                        .transformPosition(
                            net.minecraft.world.phys.Vec3.atCenterOf(cell).add(0, .65, 0));
                body.getLevel()
                    .sendParticles(
                        net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        at.x,
                        at.y,
                        at.z,
                        1,
                        .12,
                        .12,
                        .12,
                        .01);
              }
              return true;
            });
  }
}
