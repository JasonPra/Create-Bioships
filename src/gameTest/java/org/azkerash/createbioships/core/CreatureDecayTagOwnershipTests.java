package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class CreatureDecayTagOwnershipTests {
  @GameTest(template = "test_empty", batch = "decay_tag_ownership", timeoutTicks = 60)
  public static void linking_and_inheritance_copy_tags_without_changing_parent_identity(
      GameTestHelper h) {
    var level = h.getLevel();
    var origin = new BlockPos(22512, 80, 136);
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var parent =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, List.of(origin), new BoundingBox3i(origin, origin));
    var detached = origin.east(4);
    level.setBlock(detached, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    var child =
        SubLevelAssemblyHelper.assembleBlocks(
            level, detached, List.of(detached), new BoundingBox3i(detached, detached));
    CoreAssemblyRules.mark(level, parent.getPlot().getCenterBlock());
    var original = new CompoundTag();
    var nested = new CompoundTag();
    nested.putInt("value", 7);
    original.put("payload", nested);
    child.setUserDataTag(original);
    CreatureDecay.link(parent, child);
    h.assertTrue(
        !original.hasUUID(CreatureDecay.FAMILY), "Link mutated its input tag instead of its copy");
    nested.putInt("value", 9);
    h.assertTrue(
        child.getUserDataTag().getCompound("payload").getInt("value") == 7,
        "Link shared nested mutable data");
    child.setUserDataTag(null);
    CreatureDecay.link(parent, child);
    h.assertTrue(
        child.getUserDataTag() != null
            && CreatureDecay.familyId(parent).equals(CreatureDecay.familyId(child)),
        "Fresh child with no user data was silently omitted from the corpse family");
    SubLevelHealth.damage(parent, SubLevelHealth.status(parent).current(), "test");
    var parentTag = parent.getUserDataTag();
    var payload = new CompoundTag();
    payload.putInt("value", 11);
    parentTag.put("payload", payload);
    CreatureDecay.inherit(parent, child);
    h.assertTrue(
        parentTag.getUUID(CoreAssemblyRules.ID).equals(parent.getUniqueId())
            && child.getUserDataTag().getUUID(CoreAssemblyRules.ID).equals(child.getUniqueId())
            && parent.getUserDataTag() != child.getUserDataTag(),
        "Inheritance overwrote the parent's identity or shared its mutable tag");
    child.getUserDataTag().getCompound("payload").putInt("value", 13);
    h.assertTrue(
        parent.getUserDataTag().getCompound("payload").getInt("value") == 11,
        "Fragment edits changed the parent's nested data");
    h.succeed();
  }
}
