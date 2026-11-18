package org.Azkerash.create_bioships.core.brain;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.brain.BrainBlock;
import org.Azkerash.create_bioships.core.*;

/** One brain inventory and one active physical group per grid, rebuilt only on topology edits. */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelBrains {
  private static final Map<ServerSubLevel, SubLevelBrains> BRAINS = new IdentityHashMap<>();
  private static final Set<ServerSubLevel> PENDING =
      Collections.newSetFromMap(new IdentityHashMap<>());
  public final ServerSubLevel body;
  final NonNullList<ItemStack> cards = NonNullList.withSize(27, ItemStack.EMPTY);
  private final Set<BlockPos> cells = new HashSet<>();
  private Set<BlockPos> active = Set.of();
  private BlockPos port, anchor;
  private Direction face = Direction.NORTH;
  private boolean pinned, dirty = true, rebuilding;
  private final BrainInventory inventory;

  private SubLevelBrains(ServerSubLevel body) {
    this.body = body;
    inventory = new BrainInventory(this);
    var tag =
        body.getUserDataTag() == null
            ? new CompoundTag()
            : body.getUserDataTag().getCompound(SubLevelData.BRAIN);
    ContainerHelper.loadAllItems(tag, cards, body.getLevel().registryAccess());
    if (tag.contains("Port")) port = BlockPos.of(tag.getLong("Port"));
    if (tag.contains("Anchor")) anchor = BlockPos.of(tag.getLong("Anchor"));
    face = Direction.from3DDataValue(tag.getByte("Face"));
    if (!tag.contains("Face")) face = Direction.NORTH;
    pinned = tag.getBoolean("Pinned");
    for (var pos : SubLevelHealth.blocks(body))
      if (body.getLevel().getBlockState(pos).is(ComponentRegistry.BRAIN.get()))
        cells.add(pos.immutable());
  }

  public static SubLevelBrains get(ServerSubLevel body) {
    var result = BRAINS.computeIfAbsent(body, SubLevelBrains::new);
    result.rebuild();
    return result;
  }

  public static void request(ServerSubLevel body) {
    if (body != null && !body.isRemoved()) PENDING.add(body);
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var brain = BRAINS.get(body);
    if (brain == null) {
      if (state.is(ComponentRegistry.BRAIN.get())) request(body);
      return;
    }
    boolean added =
        state.is(ComponentRegistry.BRAIN.get())
            ? brain.cells.add(pos.immutable())
            : brain.cells.remove(pos);
    if (added) {
      brain.dirty = true;
      request(body);
    }
  }

  public Set<BlockPos> cells() {
    return Set.copyOf(cells);
  }

  public Set<BlockPos> activeCells() {
    rebuild();
    return active;
  }

  public int capacity() {
    rebuild();
    return active.size();
  }

  public BlockPos port() {
    rebuild();
    return port;
  }

  public Direction face() {
    return face;
  }

  public BrainInventory inventory() {
    return inventory;
  }

  public boolean canAdd(BlockPos pos) {
    return BrainShape.canAdd(cells, pos);
  }

  public boolean movePort(BlockPos pos, Direction direction) {
    rebuild();
    if (!active.contains(pos) || cells.contains(pos.relative(direction))) return false;
    port = pos.immutable();
    face = direction;
    pinned = true;
    updateVisuals();
    save();
    return true;
  }

  private void rebuild() {
    if (!dirty || rebuilding) return;
    rebuilding = true;
    try {
      var valid = BrainShape.groups(cells).stream().filter(BrainShape::bounded).toList();
      active =
          valid.stream()
              .filter(g -> anchor != null && g.contains(anchor))
              .findFirst()
              .orElse(valid.isEmpty() ? Set.of() : valid.getFirst());
      if (active.isEmpty()) {
        port = null;
        anchor = null;
        pinned = false;
      } else {
        anchor = active.stream().min(BlockPos::compareTo).orElseThrow();
        if (!pinned
            || port == null
            || !active.contains(port)
            || cells.contains(port.relative(face))) {
          pinned = false;
          port = centerPort(active, face);
        }
      }
      dirty = false;
      updateVisuals();
      save();
    } finally {
      rebuilding = false;
    }
  }

  private static BlockPos centerPort(Set<BlockPos> cells, Direction face) {
    double x =
        cells.stream().mapToInt(BlockPos::getX).min().orElseThrow()
            + cells.stream().mapToInt(BlockPos::getX).max().orElseThrow();
    double y =
        cells.stream().mapToInt(BlockPos::getY).min().orElseThrow()
            + cells.stream().mapToInt(BlockPos::getY).max().orElseThrow();
    double z =
        cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow()
            + cells.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
    return cells.stream()
        .filter(p -> !cells.contains(p.relative(face)))
        .min(
            Comparator.<BlockPos>comparingDouble(
                    p ->
                        Math.pow(p.getX() - x / 2, 2)
                            + Math.pow(p.getY() - y / 2, 2)
                            + Math.pow(p.getZ() - z / 2, 2))
                .thenComparing(BlockPos::compareTo))
        .orElseThrow();
  }

  private void updateVisuals() {
    for (var p : cells) {
      var state = body.getLevel().getBlockState(p);
      if (!state.is(ComponentRegistry.BRAIN.get())) continue;
      var desired = p.equals(port) ? BrainBlock.Port.of(face) : BrainBlock.Port.NONE;
      if (state.getValue(BrainBlock.PORT) != desired)
        body.getLevel().setBlock(p, state.setValue(BrainBlock.PORT, desired), 2);
    }
  }

  public void save() {
    var tag = new CompoundTag();
    ContainerHelper.saveAllItems(tag, cards, body.getLevel().registryAccess());
    if (port != null) tag.putLong("Port", port.asLong());
    if (anchor != null) tag.putLong("Anchor", anchor.asLong());
    tag.putByte("Face", (byte) face.ordinal());
    tag.putBoolean("Pinned", pinned);
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    root.put(SubLevelData.BRAIN, tag);
    body.setUserDataTag(root);
  }

  public static void forget(ServerSubLevel body) {
    BRAINS.remove(body);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    for (var body : new ArrayList<>(PENDING))
      if (!body.isRemoved() && SubLevelData.isLiving(body)) get(body);
    PENDING.clear();
    if (event.getServer().getTickCount() % 20 == 0)
      BRAINS.keySet().removeIf(ServerSubLevel::isRemoved);
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    BRAINS.clear();
    PENDING.clear();
  }
}
