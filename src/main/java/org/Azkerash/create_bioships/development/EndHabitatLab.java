package org.Azkerash.create_bioships.development;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Remote suspended End habitat. Construction is explicit through the developer navigator. */
public @EventBusSubscriber(modid = Create_bioships.MOD_ID) final class EndHabitatLab {
  public static final BlockPos CENTER = new BlockPos(8192, 112, 0);
  static final int RADIUS = 64;

  public static boolean inside(double x, double y, double z) {
    return Math.abs(x - CENTER.getX()) < RADIUS
        && Math.abs(z - CENTER.getZ()) < RADIUS
        && y > CENTER.getY() - 24
        && y < CENTER.getY() + 40;
  }

  public static BlockPos prepare(ServerLevel level) {
    // The marker is outside the habitat and cannot be consumed by a test creature.
    var marker = CENTER.offset(-RADIUS - 2, -24, 0);
    if (!level.getBlockState(marker).is(Blocks.BEDROCK)) {
      for (int x = -RADIUS; x <= RADIUS; x++)
        for (int z = -RADIUS; z <= RADIUS; z++) {
          boolean wall = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
          for (int y = -24; y <= 40; y++)
            if (wall || y == -24 || y == 40)
              level.setBlock(
                  CENTER.offset(x, y, z),
                  (y == 40 ? Blocks.TINTED_GLASS : Blocks.GLASS).defaultBlockState(),
                  2);
        }
      int[][] islands = {
        {0, 0, 0, 19}, {-35, 25, 10, 15}, {32, 26, -6, 18}, {28, -32, 15, 14}, {-30, -31, -4, 17}
      };
      for (var island : islands)
        for (int x = -island[3]; x <= island[3]; x++)
          for (int z = -island[3]; z <= island[3]; z++) {
            double distance = (x * x + z * z) / (double) (island[3] * island[3]);
            if (distance >= 1) continue;
            int top = island[2] + (int) Math.round(1.5 * Math.sin(x * .24) * Math.cos(z * .21));
            int depth = 2 + (int) ((1 - distance) * 10);
            for (int y = top - depth; y <= top; y++)
              level.setBlock(
                  CENTER.offset(island[0] + x, y, island[1] + z),
                  Blocks.END_STONE.defaultBlockState(),
                  2);
            if (Math.floorMod(x * 73 + z * 31, 67) == 0)
              level.setBlock(
                  CENTER.offset(island[0] + x, top + 1, island[1] + z),
                  Blocks.CHORUS_FLOWER.defaultBlockState(),
                  2);
          }
      // Fill-biome bounds stay below the command's default per-operation volume limit.
      var source =
          level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
      for (int x = -RADIUS; x < RADIUS; x += 16)
        for (int z = -RADIUS; z < RADIUS; z += 16)
          level
              .getServer()
              .getCommands()
              .performPrefixedCommand(
                  source,
                  "fillbiome "
                      + (CENTER.getX() + x)
                      + " "
                      + (CENTER.getY() - 24)
                      + " "
                      + z
                      + " "
                      + (CENTER.getX() + x + 15)
                      + " "
                      + (CENTER.getY() + 40)
                      + " "
                      + (z + 15)
                      + " minecraft:end_highlands");
      level.setBlock(marker, Blocks.BEDROCK.defaultBlockState(), 2);
    }
    return CENTER.above(6);
  }

  private static final java.util.Map<ServerLevel, Long> NEXT_REFILL = new java.util.WeakHashMap<>();

  @SubscribeEvent
  public static void population(ServerTickEvent.Post event) {
    var level = event.getServer().overworld();
    if (!NavigationLab.lab(level)
        || !level.isPositionEntityTicking(CENTER)
        || level.getGameTime() < NEXT_REFILL.getOrDefault(level, 0L)) return;
    NEXT_REFILL.put(level, level.getGameTime() + 6000);
    var bounds = new AABB(CENTER).inflate(RADIUS, 40, RADIUS);
    int count = level.getEntitiesOfClass(EnderMan.class, bounds).size();
    for (int attempt = 0; attempt < 40 && count < 12; attempt++) {
      int x = level.random.nextInt(27) - 13, z = level.random.nextInt(27) - 13;
      int top = (int) Math.round(1.5 * Math.sin(x * .24) * Math.cos(z * .21));
      var pos = CENTER.offset(x, top + 1, z);
      if (!level.getBlockState(pos.below()).is(Blocks.END_STONE)) continue;
      var mob = EntityType.ENDERMAN.create(level);
      if (mob == null) return;
      mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
      if (!level.noCollision(mob, mob.getBoundingBox())) continue;
      mob.setPersistenceRequired();
      mob.getPersistentData().putBoolean(LabPopulation.MARKER, true);
      if (level.addFreshEntity(mob)) count++;
    }
  }

  @SubscribeEvent
  public static void contain(EntityTeleportEvent.EnderEntity event) {
    if (!(event.getEntity().level() instanceof ServerLevel level) || !NavigationLab.lab(level))
      return;
    var entity = event.getEntity();
    if (inside(entity.getX(), entity.getY(), entity.getZ())
        && !inside(event.getTargetX(), event.getTargetY(), event.getTargetZ()))
      event.setCanceled(true);
  }
}
