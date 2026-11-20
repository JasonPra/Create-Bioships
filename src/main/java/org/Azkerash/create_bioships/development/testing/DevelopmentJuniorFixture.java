package org.Azkerash.create_bioships.development.testing;

import java.io.IOException;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Authored reference creature for the development preset, isolated from survival generation. */
public final class DevelopmentJuniorFixture {
  private static final ResourceLocation TEMPLATE =
      ResourceLocation.fromNamespaceAndPath("create_bioships", "development/junior.nbt");

  private DevelopmentJuniorFixture() {}

  public static CompoundTag template(ServerLevel level) {
    try (var input = level.getServer().getResourceManager().getResourceOrThrow(TEMPLATE).open()) {
      var resource = NbtIo.readCompressed(input, NbtAccounter.create(8 * 1024 * 1024));
      var baseline = resource.getCompound("baseline").copy();
      if (baseline.getList("blocks", Tag.TAG_COMPOUND).isEmpty())
        throw new IllegalStateException("Junior's developer template has no blocks");
      baseline.putDouble(
          "y",
          baseline.getDouble("y")
              + NecromancerDenFixture.base(level).getY()
              - resource.getInt("floor_y"));
      return baseline;
    } catch (IOException error) {
      throw new IllegalStateException("Cannot load Junior's developer template", error);
    }
  }

  public static void install(ServerLevel level) {
    var state = CoreTestState.get(level);
    if (state.juniorBaseline.isEmpty()) {
      state.juniorBaseline = template(level);
      state.setDirty();
    }
    // Only the exhibit UUID identifies this reference creature; names elsewhere do not.
    if (state.juniorSubject == null) DevelopmentJuniorBaseline.reset(level);
    JuniorAdminCommands.installControl(level);
  }
}
