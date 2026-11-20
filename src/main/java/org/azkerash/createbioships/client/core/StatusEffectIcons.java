package org.azkerash.createbioships.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * Draw vanilla atlas icons in the text row's exact position, including Create's tooltip scaling.
 */
public final class StatusEffectIcons {
  private static final int OXYGEN_BUBBLES = 10;
  private static final int ICON_STRIDE_PIXELS = 8;
  private static final int HUD_ICON_SIZE_PIXELS = 9;
  private static final int EFFECT_ICON_SIZE_PIXELS = 8;

  private StatusEffectIcons() {}

  public static void render(
      FormattedCharSequence text,
      Font font,
      int x,
      int y,
      Matrix4f matrix,
      MultiBufferSource buffers) {
    float[] offset = {0};
    boolean[] rendered = {false};
    text.accept(
        (index, style, codepoint) -> {
          var marker = style.getInsertion();
          if (!rendered[0] && marker != null && marker.startsWith("create_bioships:")) {
            rendered[0] = true;
            drawMarker(marker, x + offset[0], y, matrix, buffers);
          }
          offset[0] +=
              font.width(
                  FormattedCharSequence.forward(new String(Character.toChars(codepoint)), style));
          return true;
        });
  }

  /** Insertion metadata anchors icons without adding visible placeholder glyphs to the tooltip. */
  private static void drawMarker(
      String marker, float x, int y, Matrix4f matrix, MultiBufferSource buffers) {
    var mc = Minecraft.getInstance();
    int separator = marker.indexOf('/');
    if (separator < 0) {
      return;
    }
    String payload = marker.substring(separator + 1);
    switch (marker.substring(0, separator)) {
      case "create_bioships:effect" -> {
        var id = ResourceLocation.tryParse(payload);
        if (id != null) {
          BuiltInRegistries.MOB_EFFECT
              .getHolder(id)
              .ifPresent(
                  effect ->
                      draw(
                          mc.getMobEffectTextures().get(effect),
                          x,
                          y,
                          EFFECT_ICON_SIZE_PIXELS,
                          matrix,
                          buffers));
        }
      }
      case "create_bioships:bar" -> {
        var values = payload.split("/");
        if (values.length == 3) {
          CoreStatusBars.draw(
              values[0],
              Double.parseDouble(values[1]),
              Double.parseDouble(values[2]),
              x,
              y,
              matrix,
              buffers);
        }
      }
      case "create_bioships:oxygen" -> {
        var values = payload.split("/");
        double air = Double.parseDouble(values[0]), maximum = Double.parseDouble(values[1]);
        double bubbles = maximum > 0 ? Math.clamp(air / maximum, 0, 1) * OXYGEN_BUBBLES : 0;
        for (int i = 0; i < OXYGEN_BUBBLES; i++) {
          if (bubbles <= i) {
            continue;
          }
          var sprite = bubbles >= i + 1 ? "air" : "air_bursting";
          draw(
              mc.getGuiSprites().getSprite(ResourceLocation.withDefaultNamespace("hud/" + sprite)),
              x + i * ICON_STRIDE_PIXELS,
              y,
              HUD_ICON_SIZE_PIXELS,
              matrix,
              buffers);
        }
      }
      case "create_bioships:hunger" -> {
        var values = payload.split("/");
        int hunger = Integer.parseInt(values[0]);
        int maximum =
            values.length > 1
                ? Integer.parseInt(values[1])
                : org.azkerash.createbioships.core.SubLevelData.BASE_HUNGER;
        for (int i = 0; i < maximum; i++) {
          var name = hunger > i ? "food_full" : "food_empty";
          draw(
              mc.getGuiSprites().getSprite(ResourceLocation.withDefaultNamespace("hud/" + name)),
              x + i * ICON_STRIDE_PIXELS,
              y,
              HUD_ICON_SIZE_PIXELS,
              matrix,
              buffers);
        }
      }
      default -> {
        // Unrecognized insertion markers leave the text layout intact.
      }
    }
  }

  private static void draw(
      TextureAtlasSprite sprite,
      float x,
      float y,
      int size,
      Matrix4f matrix,
      MultiBufferSource buffers) {
    var vertex = buffers.getBuffer(RenderType.text(sprite.atlasLocation()));
    vertex
        .addVertex(matrix, x, y + size, 0)
        .setUv(sprite.getU0(), sprite.getV1())
        .setColor(-1)
        .setLight(LightTexture.FULL_BRIGHT);
    vertex
        .addVertex(matrix, x + size, y + size, 0)
        .setUv(sprite.getU1(), sprite.getV1())
        .setColor(-1)
        .setLight(LightTexture.FULL_BRIGHT);
    vertex
        .addVertex(matrix, x + size, y, 0)
        .setUv(sprite.getU1(), sprite.getV0())
        .setColor(-1)
        .setLight(LightTexture.FULL_BRIGHT);
    vertex
        .addVertex(matrix, x, y, 0)
        .setUv(sprite.getU0(), sprite.getV0())
        .setColor(-1)
        .setLight(LightTexture.FULL_BRIGHT);
  }
}
