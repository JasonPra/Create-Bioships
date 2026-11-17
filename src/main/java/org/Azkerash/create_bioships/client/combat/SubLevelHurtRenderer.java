package org.Azkerash.create_bioships.client.combat;

import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.Azkerash.create_bioships.Create_bioships;

/** Brief shader overlay on the sublevel's actual block meshes, following its interpolated pose. */
@EventBusSubscriber(modid = Create_bioships.MODID, value = Dist.CLIENT)
public final class SubLevelHurtRenderer {
  private static final Map<UUID, Long> FLASHES = new HashMap<>();
  private static ShaderInstance shader;
  private static net.minecraft.client.multiplayer.ClientLevel world;

  private SubLevelHurtRenderer() {}

  public static void flash(UUID body) {
    var level = Minecraft.getInstance().level;
    if (level == null) return;
    if (world != level) {
      FLASHES.clear();
      world = level;
    }
    FLASHES.put(body, level.getGameTime() + 10);
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || shader == null)
      return;
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.level != world) {
      FLASHES.clear();
      world = mc.level;
      return;
    }
    FLASHES.entrySet().removeIf(e -> e.getValue() <= mc.level.getGameTime());
    if (FLASHES.isEmpty()) return;
    var container = SubLevelContainer.getContainer(mc.level);
    if (container == null) return;
    var buffers = mc.renderBuffers().bufferSource();
    var consumer = buffers.getBuffer(HurtType.TYPE);
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    for (var id : FLASHES.keySet()) {
      var body = container.getSubLevel(id);
      if (!(body instanceof ClientSubLevel client) || body.isRemoved()) continue;
      var transform = client.renderPose();
      var origin = body.getPlot().getCenterBlock();
      pose.pushPose();
      pose.translate(
          transform.position().x() - camera.x,
          transform.position().y() - camera.y,
          transform.position().z() - camera.z);
      pose.mulPose(new org.joml.Quaternionf(transform.orientation()));
      pose.scale(
          (float) transform.scale().x(),
          (float) transform.scale().y(),
          (float) transform.scale().z());
      pose.translate(
          origin.getX() - transform.rotationPoint().x(),
          origin.getY() - transform.rotationPoint().y(),
          origin.getZ() - transform.rotationPoint().z());
      for (var holder : body.getPlot().getLoadedChunks()) {
        var chunk = holder.getChunk();
        var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
          if (sections[i].hasOnlyAir()) continue;
          int floor = chunk.getSectionYFromSectionIndex(i) << 4;
          for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
              for (int z = 0; z < 16; z++) {
                var state = sections[i].getBlockState(x, y, z);
                if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL)
                  continue;
                var pos =
                    new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        floor + y,
                        chunk.getPos().getMinBlockZ() + z);
                pose.pushPose();
                pose.translate(
                    pos.getX() - origin.getX(),
                    pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ());
                mc.getBlockRenderer()
                    .getModelRenderer()
                    .tesselateBlock(
                        mc.level,
                        mc.getBlockRenderer().getBlockModel(state),
                        state,
                        pos,
                        pose,
                        consumer,
                        false,
                        RandomSource.create(),
                        state.getSeed(pos),
                        OverlayTexture.NO_OVERLAY);
                pose.popPose();
              }
        }
      }
      pose.popPose();
    }
    buffers.endBatch(HurtType.TYPE);
  }

  private static final class HurtType extends RenderType {
    private HurtType() {
      super(
          "unused",
          DefaultVertexFormat.BLOCK,
          VertexFormat.Mode.QUADS,
          256,
          false,
          false,
          () -> {},
          () -> {});
    }

    private static final RenderType TYPE =
        create(
            "sublevel_hurt",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            true,
            CompositeState.builder()
                .setShaderState(new ShaderStateShard(() -> shader))
                .setTextureState(BLOCK_SHEET_MIPPED)
                .setLightmapState(LIGHTMAP)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLayeringState(POLYGON_OFFSET_LAYERING)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
  }

  @EventBusSubscriber(
      modid = Create_bioships.MODID,
      value = Dist.CLIENT,
      bus = EventBusSubscriber.Bus.MOD)
  public static final class Registration {
    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws java.io.IOException {
      event.registerShader(
          new ShaderInstance(
              event.getResourceProvider(),
              ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "sublevel_hurt"),
              DefaultVertexFormat.BLOCK),
          s -> shader = s);
    }

    @SubscribeEvent
    public static void entity(EntityRenderersEvent.RegisterRenderers event) {
      registerInvisible(event, Create_bioships.CORE_HURTBOX.get());
      registerInvisible(event, Create_bioships.EYE_HURTBOX.get());
      event.registerEntityRenderer(
          Create_bioships.COMBAT_TARGET.get(),
          context ->
              new net.minecraft.client.renderer.entity.EntityRenderer<
                  org.Azkerash.create_bioships.combat.SubLevelCombatTarget>(context) {
                @Override
                public ResourceLocation getTextureLocation(
                    org.Azkerash.create_bioships.combat.SubLevelCombatTarget entity) {
                  return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;
                }
              });
    }

    private static <T extends net.minecraft.world.entity.Entity> void registerInvisible(
        EntityRenderersEvent.RegisterRenderers event,
        net.minecraft.world.entity.EntityType<T> type) {
      event.registerEntityRenderer(
          type,
          context ->
              new net.minecraft.client.renderer.entity.EntityRenderer<T>(context) {
                @Override
                public ResourceLocation getTextureLocation(T entity) {
                  return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;
                }
              });
    }
  }
}
