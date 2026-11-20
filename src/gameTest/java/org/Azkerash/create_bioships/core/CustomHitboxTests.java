package org.Azkerash.create_bioships.core;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.CustomHitboxes;
import org.Azkerash.create_bioships.compat.epicfight.EpicFightIntegration;
import org.Azkerash.create_bioships.compat.epicfight.mixin.EpicFightColliderAccess;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CustomHitboxTests {
  @GameTest(template = "test_empty", batch = "custom_hitbox_geometry")
  public static void custom_geometry_selects_one_optional_backend(GameTestHelper helper) {
    var bounds = new AABB(10, 20, 30, 12, 24, 36);
    var hitbox = CustomHitboxes.create(bounds);
    boolean epic = EpicFightIntegration.isAvailable();
    helper.assertTrue(
        hitbox.getBackend()
            == (epic ? CustomHitboxes.Backend.EPIC_FIGHT : CustomHitboxes.Backend.VANILLA),
        "Wrong custom geometry backend");
    helper.assertTrue(hitbox.getBounds().equals(bounds), "Factory changed world bounds");
    helper.assertTrue(
        hitbox.intersects(bounds.deflate(.1)) && !hitbox.intersects(bounds.move(100, 0, 0)),
        "Volume queries changed");
    Object geometry = hitbox.getGeometry();
    helper.assertTrue(
        geometry == hitbox.getGeometry(), "Factory allocated a second native geometry");
    if (!epic) {
      helper.assertTrue(geometry == bounds, "Vanilla geometry should be its native AABB");
      helper.assertTrue(
          !CustomHitboxes.intersectsEpicFight(hitbox, new Object(), null),
          "Absent integration was invoked");
      helper.succeed();
      return;
    }
    helper.assertTrue(
        geometry.getClass().getName().equals("yesman.epicfight.api.collider.OBBCollider"),
        "Epic Fight backend did not create native collider geometry");
    var probe = EntityType.INTERACTION.create(helper.getLevel());
    helper.assertTrue(probe != null, "Missing fallback probe");
    var original = probe.getBoundingBox();
    try {
      var type = geometry.getClass();
      var near = type.getConstructor(AABB.class).newInstance(bounds.deflate(.1));
      var far = type.getConstructor(AABB.class).newInstance(bounds.move(100, 0, 0));
      helper.assertTrue(
          CustomHitboxes.intersectsEpicFight(hitbox, near, probe),
          "Native EF collider missed tissue");
      helper.assertTrue(
          !CustomHitboxes.intersectsEpicFight(hitbox, far, probe),
          "Native EF collider hit distant tissue");
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
    var fallback =
        new EpicFightColliderAccess() {
          public AABB core$bounds() {
            return bounds;
          }

          public boolean core$collides(Entity entity) {
            return entity.getBoundingBox().equals(bounds);
          }
        };
    helper.assertTrue(
        CustomHitboxes.intersectsEpicFight(hitbox, fallback, probe),
        "Fallback did not receive target bounds");
    helper.assertTrue(probe.getBoundingBox().equals(original), "Fallback leaked probe bounds");
    var broken =
        new EpicFightColliderAccess() {
          public AABB core$bounds() {
            return bounds;
          }

          public boolean core$collides(Entity entity) {
            throw new IllegalArgumentException("probe test");
          }
        };
    try {
      CustomHitboxes.intersectsEpicFight(hitbox, broken, probe);
      throw new AssertionError("Expected failed collider query");
    } catch (IllegalArgumentException expected) {
      helper.assertTrue(
          probe.getBoundingBox().equals(original), "Failed query leaked probe bounds");
    }
    helper.succeed();
  }
}
