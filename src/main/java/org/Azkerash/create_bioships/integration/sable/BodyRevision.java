package org.Azkerash.create_bioships.integration.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.BioshipIntegration;

/** Structural cache revision, including face/property changes, independent of any creature core. */
public final class BodyRevision {
  private static final Map<ServerSubLevel, Long> VERSIONS =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static long at(ServerSubLevel body) {
    return body == null ? 0 : VERSIONS.getOrDefault(body, 0L);
  }

  /**
   * Bulk Sable handoffs also invalidate topology, even when they bypass ordinary block callbacks.
   */
  public static void changed(ServerSubLevel body) {
    if (body != null) VERSIONS.merge(body, 1L, Long::sum);
  }

  public static void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
    if (before == null || before.equals(after)) return;
    var body = BioshipIntegration.containing(level, pos);
    changed(body);
  }

  private BodyRevision() {}
}
