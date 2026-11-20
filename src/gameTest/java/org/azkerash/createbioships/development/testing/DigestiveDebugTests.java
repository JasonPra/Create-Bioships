package org.azkerash.createbioships.development.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.digestion.SubLevelDigestion;
import org.azkerash.createbioships.development.testing.network.*;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class DigestiveDebugTests {
  @GameTest(template = "test_empty", batch = "digestive_debug_protocol", timeoutTicks = 100)
  public static void graph_snapshot_matches_topology_and_wire_codec(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(origin, ComponentRegistry.MOUTH.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        origin.east(), ComponentRegistry.INTESTINE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        origin.east(2), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        origin.east().south(),
        ComponentRegistry.INTESTINE.get().defaultBlockState(),
        Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east(), origin.east(2), origin.east().south()),
            new BoundingBox3i(origin, origin.east(2).south()));
    SubLevelData.living(body, true);
    var center = body.getPlot().getCenterBlock();
    var nodes = SubLevelDigestion.debugNodes(body);
    int version = SubLevelDigestion.topologyVersion(body);
    helper.assertTrue(
        nodes.size() == 4
            && nodes.get(center) == 2
            && nodes.get(center.east()) == 1
            && nodes.get(center.east(2)) == 3,
        "Debug nodes disagree with actual graph");
    helper.assertTrue(
        SubLevelDigestion.topologyVersion(body) == version,
        "Unchanged graph rebuilt unnecessarily");
    var payload =
        new DigestiveDebugPayload(
            body.getUniqueId(),
            nodes.entrySet().stream()
                .map(n -> new DigestiveDebugPayload.Node(n.getKey(), n.getValue()))
                .toList(),
            false);
    var buffer =
        new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
    DigestiveDebugPayload.CODEC.encode(buffer, payload);
    helper.assertTrue(
        DigestiveDebugPayload.CODEC.decode(buffer).equals(payload),
        "Graph wire snapshot lost node IDs/types");
    buffer.release();
    level.removeBlock(center.east().south(), false);
    helper.assertTrue(
        SubLevelDigestion.topologyVersion(body) != version
            && SubLevelDigestion.debugNodes(body).size() == 3,
        "Removed branch not reflected in debug graph");
    SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "test");
    helper.assertTrue(
        SubLevelDigestion.debugNodes(body).size() == 3, "Dead grid lost inspection topology");
    helper.runAfterDelay(10, helper::succeed);
  }
}
