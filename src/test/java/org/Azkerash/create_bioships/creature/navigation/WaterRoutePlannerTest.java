package org.Azkerash.create_bioships.creature.navigation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Predicate;
import org.Azkerash.create_bioships.creature.navigation.WaterRoutePlanner.*;
import org.junit.jupiter.api.Test;

class WaterRoutePlannerTest {
  private static Point p(double x, double y, double z) {
    return new Point(x + .5, y + .5, z + .5);
  }

  private static Space space(Predicate<Point> fits) {
    return new Space() {
      public boolean fits(Point p) {
        return fits.test(p);
      }

      public boolean clear(Point a, Point b) {
        int samples = Math.max(1, (int) Math.ceil(a.distance(b) / .1));
        for (int i = 1; i <= samples; i++) if (!fits(a.lerp(b, i / (double) samples))) return false;
        return true;
      }
    };
  }

  @Test
  void openWaterUsesDirectPathWithoutSearchingAndKeepsDiagonals() {
    var route = WaterRoutePlanner.toTarget(p(0, 0, 0), p(8, 3, 8), .1, space(q -> true), 4096, 32);
    assertEquals(Status.COMPLETE, route.status());
    assertEquals(0, route.expanded());
    assertEquals(2, route.points().size());
  }

  @Test
  void routesAroundAThreeDimensionalBarrier() {
    var water =
        space(
            q ->
                q.y() > 0
                    && q.y() < 5
                    && Math.abs(q.z()) < 5
                    && q.x() > 0
                    && q.x() < 10
                    && !(q.x() > 3 && q.x() < 5 && q.z() < 2));
    var route = WaterRoutePlanner.toTarget(p(1, 1, 0), p(8, 1, 0), .7, water, 4096, 20);
    assertEquals(Status.COMPLETE, route.status());
    assertTrue(route.points().stream().anyMatch(q -> q.z() >= 2));
    for (int i = 1; i < route.points().size(); i++)
      assertTrue(water.clear(route.points().get(i - 1), route.points().get(i)));
  }

  @Test
  void airSearchCanTravelSidewaysBeforeAscendingThroughARoofOpening() {
    var water =
        space(
            q ->
                q.x() >= 0
                    && q.x() < 7
                    && q.z() >= 0
                    && q.z() < 3
                    && q.y() >= 0
                    && q.y() < 7
                    && !(q.y() >= 3 && q.y() < 4 && q.x() < 5));
    var route =
        WaterRoutePlanner.find(
            p(1, 1, 1), q -> q.y() >= 5, q -> Math.max(0, 5 - q.y()), water, 4096, 16);
    assertEquals(Status.COMPLETE, route.status());
    assertTrue(route.points().stream().anyMatch(q -> q.x() >= 5));
    assertTrue(route.points().getLast().y() >= 5);
  }

  @Test
  void narrowGapRejectsAWideBodyAndSealedSearchIsBounded() {
    var sealed =
        space(q -> q.x() > 0 && q.x() < 3 && q.y() > 0 && q.y() < 3 && q.z() > 0 && q.z() < 3);
    var route = WaterRoutePlanner.toTarget(p(1, 1, 1), p(9, 1, 1), .5, sealed, 32, 16);
    assertNotEquals(Status.COMPLETE, route.status());
    assertTrue(route.expanded() <= 32);
    assertTrue(route.points().isEmpty());
  }

  @Test
  void searchBudgetDoesNotReturnAPartialRouteAsSuccess() {
    var route = WaterRoutePlanner.find(p(0, 0, 0), q -> false, q -> 0, space(q -> true), 20, 32);
    assertEquals(Status.BUDGET_EXHAUSTED, route.status());
    assertEquals(20, route.expanded());
    assertTrue(route.points().isEmpty());
  }
}
