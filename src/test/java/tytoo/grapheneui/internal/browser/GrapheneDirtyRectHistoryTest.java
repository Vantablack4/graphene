package tytoo.grapheneui.internal.browser;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneDirtyRectHistoryTest {
    @Test
    void damageIsClampedAndOverlapsAreMergedBeforeAreaAccounting() {
        GrapheneDirtyRegion damage = GrapheneDirtyRegion.from(
                new Rectangle[]{
                        new Rectangle(-5, -5, 10, 10),
                        new Rectangle(1, 1, 3, 3),
                        new Rectangle(80, 80, 30, 30)
                },
                100,
                100
        );

        assertFalse(damage.isFull());
        assertArrayEquals(
                new Rectangle[]{new Rectangle(0, 0, 5, 5), new Rectangle(80, 80, 20, 20)},
                damage.copyRectangles()
        );
        assertEquals(425L, damage.pixelCount(100, 100));
    }

    @Test
    void normalizedDamagePreservesCoverageWithoutOverlappingUploads() {
        int width = 20;
        int height = 20;
        Rectangle[] input = {
                new Rectangle(-3, 2, 10, 8),
                new Rectangle(4, 5, 9, 9),
                new Rectangle(8, 0, 7, 20)
        };

        GrapheneDirtyRegion damage = GrapheneDirtyRegion.from(input, width, height);
        boolean[][] expected = coverage(input, width, height, false);
        boolean[][] actual = coverage(damage.copyRectangles(), width, height, true);

        assertFalse(damage.isFull());
        for (int y = 0; y < height; y++) {
            assertArrayEquals(expected[y], actual[y]);
        }
    }

    @Test
    void skippedVersionsAccumulateAllIntermediateDamage() {
        GrapheneDirtyRectHistory history = new GrapheneDirtyRectHistory(8);
        history.add(1L, 100, 100, region(0, 0, 10, 10));
        history.add(2L, 100, 100, region(20, 20, 10, 10));
        history.add(3L, 100, 100, region(25, 25, 10, 10));

        GrapheneDirtyRegion damage = history.damageSince(1L, 3L, 100, 100);

        assertFalse(damage.isFull());
        assertEquals(175L, damage.pixelCount(100, 100));
    }

    @Test
    void anExpiredHistoryWindowFallsBackToAFullFrame() {
        GrapheneDirtyRectHistory history = new GrapheneDirtyRectHistory(2);
        history.add(1L, 100, 100, region(0, 0, 5, 5));
        history.add(2L, 100, 100, region(10, 10, 5, 5));
        history.add(3L, 100, 100, region(20, 20, 5, 5));

        GrapheneDirtyRegion damage = history.damageSince(0L, 3L, 100, 100);

        assertTrue(damage.isFull());
        assertTrue(damage.isHistoryFallback());
    }

    @Test
    void ordinaryFullInvalidationIsNotReportedAsAHistoryFallback() {
        GrapheneDirtyRegion damage = GrapheneDirtyRegion.from(
                new Rectangle[]{new Rectangle(0, 0, 100, 100)},
                100,
                100
        );

        assertTrue(damage.isFull());
        assertFalse(damage.isHistoryFallback());
    }

    private static GrapheneDirtyRegion region(int x, int y, int width, int height) {
        return GrapheneDirtyRegion.from(new Rectangle[]{new Rectangle(x, y, width, height)}, 100, 100);
    }

    private static boolean[][] coverage(Rectangle[] rectangles, int width, int height, boolean requireDisjoint) {
        boolean[][] coverage = new boolean[height][width];
        for (Rectangle rectangle : rectangles) {
            Rectangle clamped = rectangle.intersection(new Rectangle(0, 0, width, height));
            for (int y = clamped.y; y < clamped.y + clamped.height; y++) {
                for (int x = clamped.x; x < clamped.x + clamped.width; x++) {
                    if (requireDisjoint) {
                        assertFalse(coverage[y][x], "Normalized damage rectangles overlap at " + x + "," + y);
                    }
                    coverage[y][x] = true;
                }
            }
        }
        return coverage;
    }
}
