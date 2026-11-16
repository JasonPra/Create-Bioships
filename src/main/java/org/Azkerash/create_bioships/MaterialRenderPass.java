package org.Azkerash.create_bioships;

import java.util.function.*;

/** Render adapters acquire a consumer at each material pass, never after a texture switch. */
public final class MaterialRenderPass {
  public static <Material, Buffer> void draw(
      Function<Material, Buffer> buffers,
      Material skin,
      Material detail,
      Consumer<Buffer> drawSkin,
      Consumer<Buffer> drawDetail,
      boolean includeDetail) {
    drawSkin.accept(buffers.apply(skin));
    if (includeDetail) drawDetail.accept(buffers.apply(detail));
  }
}
