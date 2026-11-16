package org.Azkerash.create_bioships.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.Azkerash.create_bioships.Create_bioships;

/** Shared recessed sucker cups for locomotion legs and mouth-bound graspers. */
final class TentacleSuckers {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath(
          Create_bioships.MOD_ID, "textures/block/tentacle_suckers.png");
  private static final ModelPart CUPS = bake();

  private static ModelPart bake() {
    var mesh = new MeshDefinition();
    var cubes =
        CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-1.6f, -10, -3.2f, 3.2f, .7f, 1.2f)
            .addBox(-1.6f, -7.4f, -3.2f, 3.2f, .7f, 1.2f)
            .addBox(-1.6f, -9.3f, -3.2f, .7f, 1.9f, 1.2f)
            .addBox(.9f, -9.3f, -3.2f, .7f, 1.9f, 1.2f)
            .texOffs(8, 8)
            .addBox(-.9f, -9.3f, -2.6f, 1.8f, 1.9f, .3f);
    mesh.getRoot().addOrReplaceChild("cups", cubes, PartPose.ZERO);
    return LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("cups");
  }

  static void renderSegment(
      ModelPart segment,
      PoseStack pose,
      MultiBufferSource buffers,
      ResourceLocation skin,
      int light,
      int overlay,
      int index) {
    org.Azkerash.create_bioships.MaterialRenderPass.draw(
        buffers::getBuffer,
        RenderType.entityCutoutNoCull(skin),
        RenderType.entityCutoutNoCull(TEXTURE),
        consumer -> segment.render(pose, consumer, light, overlay),
        consumer -> CUPS.render(pose, consumer, light, overlay),
        (index & 1) == 0);
  }
}
