package org.Azkerash.create_bioships.client.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.Azkerash.create_bioships.Create_bioships;

/** Inventory sprites and fitted equipment meshes are intentionally independent. */
@EventBusSubscriber(modid = Create_bioships.MODID, value = Dist.CLIENT)
public final class EquipmentModels {
  private static ModelResourceLocation extra(String name) {
    return ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "item/" + name));
  }

  @SubscribeEvent
  public static void additional(ModelEvent.RegisterAdditional event) {
    event.register(extra("bioship_goggles_worn"));
    event.register(extra("bioship_wrench_held"));
    event.register(extra("synaptic_calibrator_held"));
  }

  @SubscribeEvent
  public static void bake(ModelEvent.ModifyBakingResult event) {
    bind(event, "bioship_goggles", "bioship_goggles_worn", true);
    bind(event, "bioship_wrench", "bioship_wrench_held", false);
    bind(event, "limb_positioner", "synaptic_calibrator_held", false);
  }

  private static void bind(
      ModelEvent.ModifyBakingResult event, String item, String mesh, boolean headOnly) {
    var id =
        ModelResourceLocation.inventory(
            ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, item));
    var icon = event.getModels().get(id);
    var model = event.getModels().get(extra(mesh));
    if (icon == null || model == null) return;
    event
        .getModels()
        .put(
            id,
            new BakedModelWrapper<BakedModel>(icon) {
              @Override
              public BakedModel applyTransform(
                  ItemDisplayContext context, PoseStack pose, boolean leftHand) {
                boolean threeD =
                    headOnly
                        ? context == ItemDisplayContext.HEAD
                        : context != ItemDisplayContext.GUI;
                return (threeD ? model : originalModel).applyTransform(context, pose, leftHand);
              }
            });
  }
}
