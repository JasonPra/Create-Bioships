package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Fill missing meat drops, preserving all ordinary loot and normal item physics. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipMeatDrops {
  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
  public static void drops(LivingDropsEvent event) {
    var entity = event.getEntity();
    if (!BioshipPrey.eligible(entity)) return;
    if (event.getDrops().stream().noneMatch(d -> LivingCoreBlockEntity.isMeat(d.getItem()))) {
      String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
      String meat =
          switch (type) {
            case "wandering_trader" -> "villager";
            case "cave_spider" -> "spider";
            case "pillager", "vindicator", "evoker", "ravager" -> "illager";
            default ->
                entity instanceof net.minecraft.world.entity.animal.WaterAnimal ? "aquatic" : type;
          };
      var item =
          Create_bioships.MOB_MEATS.getOrDefault(meat, Create_bioships.MOB_MEATS.get("creature"));
      event
          .getDrops()
          .add(
              new ItemEntity(
                  entity.level(),
                  entity.getX(),
                  entity.getY(),
                  entity.getZ(),
                  new ItemStack(item.get())));
    }
    if (event.getSource().getEntity() instanceof BioshipTargetEntity hunter) {
      var core = hunter.core();
      if (core != null)
        for (var drop : event.getDrops())
          if (LivingCoreBlockEntity.droppedSupply(drop.getItem())) {
            if (LivingCoreBlockEntity.isMeat(drop.getItem())) {
              var stack = drop.getItem();
              var flags =
                  stack
                      .getOrDefault(
                          net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                          net.minecraft.world.item.component.CustomData.EMPTY)
                      .copyTag();
              flags.putBoolean("BioshipHunted", true);
              stack.set(
                  net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                  net.minecraft.world.item.component.CustomData.of(flags));
            }
            core.loot().remember(drop, entity.level().getGameTime());
          }
    }
  }
}
