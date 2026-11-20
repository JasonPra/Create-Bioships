package org.Azkerash.create_bioships.client.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared Biomancy-palette framing. Screens retain their own layout and interaction logic. */
final class OrganScreenStyle {
  static final int TEXT = 0xffead8c9, MUTED = 0xffb78b9d;
  private static final ResourceLocation FLESH =
      ResourceLocation.parse("biomancy:textures/block/packed_flesh.png");
  private static final ResourceLocation FRAME =
      ResourceLocation.parse("biomancy:textures/gui/menu_digester.png");

  private OrganScreenStyle() {}

  static void panel(GuiGraphics g, int x, int y, int width, int height) {
    g.fill(x + 2, y + 3, x + width + 3, y + height + 3, 0x90000000);
    g.fillGradient(x, y, x + width, y + height, 0xff6c2848, 0xff301226);
    g.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xff21121e);
    g.setColor(.33f, .17f, .27f, 1f);
    for (int dx = 4; dx < width - 4; dx += 16) {
      for (int dy = 4; dy < height - 4; dy += 16) {
        g.blit(
            FLESH,
            x + dx,
            y + dy,
            0,
            0,
            Math.min(16, width - 4 - dx),
            Math.min(16, height - 4 - dy),
            16,
            16);
      }
    }
    g.setColor(1, 1, 1, 1);
    g.fill(x + 3, y + 3, x + width - 3, y + 4, 0xffb15b76);
    g.fill(x + 3, y + 4, x + 4, y + height - 4, 0xff84344f);
    g.fill(x + width - 4, y + 4, x + width - 3, y + height - 4, 0xff561c3d);
    g.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, 0xff84344f);
    g.blit(FRAME, x, y, 0, 0, 8, 8, 256, 256);
    g.blit(FRAME, x + width - 8, y, 168, 0, 8, 8, 256, 256);
    g.blit(FRAME, x + width / 2 - 40, y, 48, 0, 80, 8, 256, 256);
    divider(g, x + 12, y + 22, width - 24);
  }

  static void divider(GuiGraphics g, int x, int y, int width) {
    g.fill(x, y, x + width, y + 1, 0xff62253f);
    g.fill(x + width / 2 - 4, y - 1, x + width / 2 + 4, y + 2, 0xff9c4b64);
    g.fill(x + width / 2 - 1, y - 2, x + width / 2 + 1, y + 3, 0xffd7a18b);
  }

  static void slot(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff8e445f);
    g.fill(x, y, x + 16, y + 16, 0xff170d17);
    g.fill(x + 1, y + 1, x + 16, y + 16, 0xff3e1a31);
    g.fill(x + 1, y + 1, x + 15, y + 2, 0xff210e20);
    g.fill(x + 1, y + 15, x + 16, y + 16, 0xff643044);
  }

  static void gauge(GuiGraphics g, int x, int y, int width, int value, int maximum, int color) {
    g.fill(x - 1, y - 1, x + width + 1, y + 11, 0xff936044);
    g.fill(x, y, x + width, y + 10, 0xff160d15);
    int fill = (width - 2) * Math.clamp(value, 0, Math.max(1, maximum)) / Math.max(1, maximum);
    if (fill > 0) {
      g.fillGradient(x + 1, y + 1, x + 1 + fill, y + 9, color, 0xff342538);
      g.fill(x + 1, y + 1, x + 1 + fill, y + 2, color);
    }
    for (int i = 1; i < 4; i++) {
      g.fill(x + width * i / 4, y + 8, x + width * i / 4 + 1, y + 10, 0xffab7b56);
    }
  }
}
