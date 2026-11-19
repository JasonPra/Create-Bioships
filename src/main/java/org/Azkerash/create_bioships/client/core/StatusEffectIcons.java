package org.Azkerash.create_bioships.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * Draw vanilla atlas icons in the text row's exact position, including Create's tooltip scaling.
 */
public final class StatusEffectIcons {
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
            var mc = Minecraft.getInstance();
            if (marker.startsWith("create_bioships:effect/")) {
              var id =
                  ResourceLocation.tryParse(marker.substring("create_bioships:effect/".length()));
              if (id != null)
                BuiltInRegistries.MOB_EFFECT
                    .getHolder(id)
                    .ifPresent(
                        effect ->
                            draw(
                                mc.getMobEffectTextures().get(effect),
                                x + offset[0],
                                y,
                                8,
                                matrix,
                                buffers));
            } else if (marker.startsWith("create_bioships:bar/")) {
              var values = marker.substring("create_bioships:bar/".length()).split("/");
              if (values.length == 3)
                CoreStatusBars.draw(
                    values[0],
                    Double.parseDouble(values[1]),
                    Double.parseDouble(values[2]),
                    x + offset[0],
                    y,
                    matrix,
                    buffers);
            } else if (marker.startsWith("create_bioships:oxygen/")) {
              var values = marker.substring("create_bioships:oxygen/".length()).split("/");
              double air = Double.parseDouble(values[0]), maximum = Double.parseDouble(values[1]);
              double bubbles = maximum > 0 ? Math.clamp(air / maximum, 0, 1) * 10 : 0;
              for (int i = 0; i < 10; i++) {
                if (bubbles <= i) continue;
                var sprite = bubbles >= i + 1 ? "air" : "air_bursting";
                draw(
                    mc.getGuiSprites()
                        .getSprite(ResourceLocation.withDefaultNamespace("hud/" + sprite)),
                    x + offset[0] + i * 8,
                    y,
                    9,
                    matrix,
                    buffers);
              }
            } else if (marker.startsWith("create_bioships:hunger/")) {
              var values = marker.substring("create_bioships:hunger/".length()).split("/");
              int hunger = Integer.parseInt(values[0]);
              int maximum = values.length > 1 ? Integer.parseInt(values[1]) : 5;
              for (int i = 0; i < maximum; i++) {
                var name = hunger > i ? "food_full" : "food_empty";
                draw(
                    mc.getGuiSprites()
                        .getSprite(ResourceLocation.withDefaultNamespace("hud/" + name)),
                    x + offset[0] + i * 8,
                    y,
                    9,
                    matrix,
                    buffers);
              }
            }
          }
          offset[0] +=
              font.width(
                  FormattedCharSequence.forward(new String(Character.toChars(codepoint)), style));
          return true;
        });
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
        .setLight(15728880);
    vertex
        .addVertex(matrix, x + size, y + size, 0)
        .setUv(sprite.getU1(), sprite.getV1())
        .setColor(-1)
        .setLight(15728880);
    vertex
        .addVertex(matrix, x + size, y, 0)
        .setUv(sprite.getU1(), sprite.getV0())
        .setColor(-1)
        .setLight(15728880);
    vertex
        .addVertex(matrix, x, y, 0)
        .setUv(sprite.getU0(), sprite.getV0())
        .setColor(-1)
        .setLight(15728880);
  }
}
