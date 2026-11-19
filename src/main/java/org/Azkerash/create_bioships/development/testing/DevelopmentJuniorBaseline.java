package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import org.Azkerash.create_bioships.compat.letitwalk.CompactLimbController;
import org.Azkerash.create_bioships.core.*;

/** Developer-only baseline for Junior's main body and procedurally generated compact limbs. */
public final class DevelopmentJuniorBaseline {
  private DevelopmentJuniorBaseline() {}

  public static void capture(ServerSubLevel body) {
    if (SubLevelHealth.status(body).dead())
      throw new IllegalArgumentException("Cannot capture a dead subject");
    for (var part : SubLevelCreatureParts.members(body))
      if (part != body && !part.getUserDataTag().contains(CompactLimbController.PART))
        throw new IllegalArgumentException(
            "This baseline supports generated compact limbs; modular assemblies require a future"
                + " multipart schematic");
    var level = body.getLevel();
    var center = body.getPlot().getCenterBlock();
    var result = new CompoundTag();
    var blocks = new ListTag();
    org.Azkerash.create_bioships.core.component.SubLevelComponentStore.flush(body);
    for (var p : SubLevelHealth.blocks(body)) {
      var entry = new CompoundTag();
      entry.putLong("offset", p.subtract(center).asLong());
      entry.put("state", NbtUtils.writeBlockState(level.getBlockState(p)));
      var be = level.getBlockEntity(p);
      if (be != null) entry.put("entity", be.saveWithFullMetadata(level.registryAccess()));
      blocks.add(entry);
    }
    result.put("blocks", blocks);
    result.putLong("origin", center.asLong());
    result.put("user", body.getUserDataTag().copy());
    var pose = body.logicalPose();
    var pos = pose.position();
    var rot = pose.orientation();
    var pivot = pose.rotationPoint();
    result.putDouble("x", pos.x());
    result.putDouble("y", pos.y());
    result.putDouble("z", pos.z());
    result.putDouble("qx", rot.x());
    result.putDouble("qy", rot.y());
    result.putDouble("qz", rot.z());
    result.putDouble("qw", rot.w());
    result.putDouble("px", pivot.x());
    result.putDouble("py", pivot.y());
    result.putDouble("pz", pivot.z());
    if (body.getName() != null) result.putString("name", body.getName());
    var state = CoreTestState.get(level);
    state.juniorBaseline = result;
    state.juniorSubject = body.getUniqueId();
    state.setDirty();
  }

  public static ServerSubLevel reset(ServerLevel level) {
    var state = CoreTestState.get(level);
    var snapshot = state.juniorBaseline;
    if (snapshot.isEmpty()) throw new IllegalStateException("Capture Junior's baseline first");
    var container = SubLevelContainer.getContainer(level);
    var old = state.juniorSubject == null ? null : container.getSubLevel(state.juniorSubject);
    var former = old instanceof ServerSubLevel b ? b : null;
    var access =
        former != null && former.getUserDataTag() != null
            ? former.getUserDataTag().getCompound(SubLevelData.ACCESS).copy()
            : snapshot.getCompound("user").getCompound(SubLevelData.ACCESS).copy();
    var origin = BlockPos.of(snapshot.getLong("origin"));
    var staging = new BlockPos(18000, 100, 18000);
    level.setChunkForced(staging.getX() >> 4, staging.getZ() >> 4, true);
    var cells = new ArrayList<BlockPos>();
    BlockPos low = staging, high = staging;
    var lookup =
        level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
    for (var raw : snapshot.getList("blocks", 10)) {
      var entry = (CompoundTag) raw;
      var p = staging.offset(BlockPos.of(entry.getLong("offset")));
      var block = NbtUtils.readBlockState(lookup, entry.getCompound("state"));
      level.setBlock(p, block, 2);
      if (entry.contains("entity") && level.getBlockEntity(p) != null) {
        var data = entry.getCompound("entity").copy();
        data.putInt("x", p.getX());
        data.putInt("y", p.getY());
        data.putInt("z", p.getZ());
        level.getBlockEntity(p).loadWithComponents(data, level.registryAccess());
      }
      cells.add(p);
      low =
          new BlockPos(
              Math.min(low.getX(), p.getX()),
              Math.min(low.getY(), p.getY()),
              Math.min(low.getZ(), p.getZ()));
      high =
          new BlockPos(
              Math.max(high.getX(), p.getX()),
              Math.max(high.getY(), p.getY()),
              Math.max(high.getZ(), p.getZ()));
    }
    var replacement =
        SubLevelAssemblyHelper.assembleBlocks(level, staging, cells, new BoundingBox3i(low, high));
    if (replacement == null)
      throw new IllegalStateException("Baseline assembly failed; current Junior was retained");
    var anchor = replacement.getPlot().getCenterBlock();
    var delta = anchor.subtract(origin);
    var user = snapshot.getCompound("user").copy();
    user.remove(SubLevelData.PARTS);
    user.remove(CompactLimbController.DATA);
    remap(user, origin, delta);
    user.put(SubLevelData.ACCESS, access);
    replacement.setUserDataTag(user);
    org.Azkerash.create_bioships.core.access.SubLevelPermissions.forget(replacement);
    CoreAssemblyRules.mark(level, anchor);
    var pose = replacement.logicalPose();
    pose.position().set(snapshot.getDouble("x"), snapshot.getDouble("y"), snapshot.getDouble("z"));
    pose.rotationPoint()
        .set(
            snapshot.getDouble("px") + delta.getX(),
            snapshot.getDouble("py") + delta.getY(),
            snapshot.getDouble("pz") + delta.getZ());
    pose.orientation()
        .set(
            snapshot.getDouble("qx"),
            snapshot.getDouble("qy"),
            snapshot.getDouble("qz"),
            snapshot.getDouble("qw"));
    replacement.updateLastPose();
    replacement.updateBoundingBox();
    var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(replacement);
    if (handle != null) handle.teleport(pose.position(), pose.orientation());
    if (snapshot.contains("name")) replacement.setName(snapshot.getString("name"));
    if (former != null) {
      for (var part : SubLevelCreatureParts.members(former))
        if (part != former && !part.isRemoved())
          container.removeSubLevel(part, SubLevelRemovalReason.REMOVED);
      if (!former.isRemoved()) container.removeSubLevel(former, SubLevelRemovalReason.REMOVED);
    }
    state.juniorSubject = replacement.getUniqueId();
    state.juniorFeedAt = level.getGameTime() + DevelopmentJuniorCare.INTERVAL;
    state.setDirty();
    org.Azkerash.create_bioships.core.legs.SubLevelLegs.request(replacement);
    return replacement;
  }

  /** Plot coordinates change on reassembly; names, item UUIDs and player UUIDs do not. */
  private static void remap(CompoundTag tag, BlockPos origin, BlockPos delta) {
    for (var key : List.copyOf(tag.getAllKeys())) {
      var value = tag.get(key);
      String mappedKey = remapText(key, origin, delta);
      if (value instanceof CompoundTag child) remap(child, origin, delta);
      else if (value instanceof ListTag list) {
        for (var element : list)
          if (element instanceof CompoundTag child) remap(child, origin, delta);
      } else if (value instanceof LongTag number)
        tag.putLong(key, remapPosition(number.getAsLong(), origin, delta));
      if (!mappedKey.equals(key)) {
        tag.put(mappedKey, tag.get(key));
        tag.remove(key);
      }
    }
  }

  private static long remapPosition(long value, BlockPos origin, BlockPos delta) {
    var p = BlockPos.of(value);
    return Math.abs(p.getX() - origin.getX()) < 64
            && Math.abs(p.getY() - origin.getY()) < 64
            && Math.abs(p.getZ() - origin.getZ()) < 64
        ? p.offset(delta).asLong()
        : value;
  }

  private static String remapText(String text, BlockPos origin, BlockPos delta) {
    var matcher = java.util.regex.Pattern.compile("-?\\d{10,20}").matcher(text);
    var result = new StringBuffer();
    while (matcher.find())
      try {
        matcher.appendReplacement(
            result, Long.toString(remapPosition(Long.parseLong(matcher.group()), origin, delta)));
      } catch (NumberFormatException e) {
        matcher.appendReplacement(result, matcher.group());
      }
    matcher.appendTail(result);
    return result.toString();
  }
}
