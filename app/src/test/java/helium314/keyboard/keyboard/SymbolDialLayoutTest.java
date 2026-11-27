// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class SymbolDialLayoutTest {
    @Test public void flicksMatchVisibleSymbolsAtTheCenterAndEdges() {
        for (int cx : new int[]{20, 200, 380}) {
            for (int cy : new int[]{25, 125, 225}) {
                for (int count = 1; count <= 6; count++) {
                    SymbolDialLayout dial = SymbolDialLayout.fit(count, cx, cy, 400, 250, 32, 0);
                    assertNotNull("count=" + count + " at " + cx + "," + cy, dial);
                    assertEquals(-1, dial.choice(4, -3, 10));
                    for (int i = 0; i < count; i++) {
                        assertTrue(dial.x[i] >= dial.chipRadius && dial.x[i] <= 400 - dial.chipRadius);
                        assertTrue(dial.y[i] >= dial.chipRadius && dial.y[i] <= 250 - dial.chipRadius);
                        // A short flick should work without traveling all the way to the label.
                        float scale = 15 / dial.radius;
                        assertEquals(i, dial.choice((dial.x[i] - cx) * scale, (dial.y[i] - cy) * scale, 10));
                        for (int j = 0; j < i; j++) {
                            assertTrue(Math.hypot(dial.x[i] - dial.x[j], dial.y[i] - dial.y[j]) >= 2 * dial.chipRadius - 0.01);
                        }
                    }
                }
            }
        }
    }

    @Test public void hintStaysNorthOnUpperArcsAndMirroredEdgeFans() {
        for (int cx : new int[]{20, 200, 380}) {
            SymbolDialLayout dial = SymbolDialLayout.fit(5, cx, 125, 400, 250, 32, 1);
            assertNotNull(dial);
            assertEquals(cx, dial.x[dial.topSlot], 0.01);
            assertEquals(dial.topSlot, dial.choice(0, -25, 10));
            if (cx == 200) assertTrue(dial.chipRadius > 16);
            if (cx == 200) for (float y : dial.y) assertTrue(y <= 125.01f);
        }
    }

    @Test public void groupsPunctuationBesideThePrintedSymbolAndPreservesAccentOrder() {
        SymbolDialLayout dial = SymbolDialLayout.fit(7, 200, 200, 400, 300, 32, 2);
        // é, |, è, 3, ê, ë, ē with | printed on the key.
        int[] order = dial.groupedOrder(new boolean[]{true, false, true, false, true, true, true}, 1);
        int[] accents = java.util.Arrays.stream(order).filter(i -> i != 3 && i != 1).toArray();
        assertArrayEquals(new int[]{0, 2, 4, 5, 6}, accents);
        assertTrue((dial.topSlot > 0 && order[dial.topSlot - 1] == 3)
                || (dial.topSlot + 1 < order.length && order[dial.topSlot + 1] == 3));
        assertEquals(1, order[dial.choice(0, -25, 10)]);
    }

    @Test public void sparseAndCrowdedKeysHaveTheSameReach() {
        for (int count : new int[]{1, 4, 7, 9}) {
            for (int cx : new int[]{40, 100, 200, 360}) {
                SymbolDialLayout dial = SymbolDialLayout.fit(count, cx, 200, 400, 320, 40, 1);
                assertNotNull(dial);
                for (int i = 0; i < count; i++) {
                    assertEquals(60, Math.hypot(dial.x[i] - cx, dial.y[i] - 200), 0.01);
                    assertEquals(i, dial.choice((dial.x[i] - cx) / 4, (dial.y[i] - 200) / 4, 10));
                }
            }
        }
    }

    @Test public void regularPopupCanHandleAnOvercrowdedDial() {
        assertNull(SymbolDialLayout.fit(40, 20, 25, 200, 100, 32, 0));
    }

    @Test public void aFlickAwayFromAnEdgeFanDoesNotSelectASymbol() {
        SymbolDialLayout dial = SymbolDialLayout.fit(3, 20, 25, 400, 250, 32, 0);
        assertNotNull(dial);
        assertEquals(-1, dial.choice(-25, -25, 10));
    }
}
