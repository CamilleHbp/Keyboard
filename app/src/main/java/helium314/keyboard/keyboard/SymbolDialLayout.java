// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard;

/** Positions the choices and uses the same rays to interpret a flick from the touch origin. */
final class SymbolDialLayout {
    final float[] x;
    final float[] y;
    final float radius;
    final float chipRadius;
    final int topSlot;
    private final double start;
    private final double step;
    private final boolean fullCircle;

    private SymbolDialLayout(int count, float cx, float cy, float radius, float chipRadius,
            double start, double step, boolean fullCircle, int topSlot) {
        x = new float[count];
        y = new float[count];
        this.radius = radius;
        this.chipRadius = chipRadius;
        this.start = start;
        this.topSlot = topSlot;
        this.step = step;
        this.fullCircle = fullCircle;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(start + i * step);
            x[i] = cx + radius * (float) Math.cos(angle);
            y[i] = cy + radius * (float) Math.sin(angle);
        }
    }

    static SymbolDialLayout fit(int count, float cx, float cy, int width, int height,
            float keySize, int pinnedGroupSize) {
        if (count <= 0 || keySize <= 0) return null;
        boolean pinned = pinnedGroupSize > 0;
        // All keys on this keyboard use the same reach, including keys with many accents.
        float radius = 1.5f * keySize;
        for (int percent = 54; percent >= 26; percent -= 4) {
            float chip = keySize * percent / 100;
            for (int limit : new int[]{90, 135, 180}) {
                SymbolDialLayout balanced = null;
                // Start balanced, then move more slots to one side for edge keys.
                for (int i = 0; i < count; i++) {
                    int middle = (count - 1) / 2;
                    int top = middle + (i % 2 == 0 ? -i / 2 : (i + 1) / 2);
                    int longerSide = Math.max(top, count - 1 - top);
                    double step = Math.min(60, limit / (double) Math.max(1, longerSide));
                    step = Math.min(step, 360.0 / count);
                    if (step < 15) continue;
                    if (count > 1 && !hasClearance(radius, chip, step)) continue;
                    boolean ring = Math.abs(step * count - 360) < 0.001;
                    SymbolDialLayout dial = new SymbolDialLayout(count, cx, cy, radius, chip,
                            -90 - top * step, step, ring, top);
                    if (!dial.fits(width, height)) continue;
                    if (balanced == null) balanced = dial;
                    // A mixed set reads as two blocks when the hinted group can touch an end.
                    if (pinnedGroupSize <= 1 || top < pinnedGroupSize || count - top <= pinnedGroupSize)
                        return dial;
                }
                if (balanced != null) return balanced;
            }
        }
        // A key without a hint can still open into the keyboard at a clipped upper edge.
        if (!pinned) {
            for (int sweep : new int[]{180, 150, 135, 120, 105, 90}) {
                double step = sweep / (double) Math.max(1, count - 1);
                float chip = (float) Math.min(keySize * 0.54,
                        radius * Math.sin(Math.toRadians(step / 2)) / 1.05);
                if (chip < keySize * 0.24) continue;
                for (int start = -90; start < 270; start += 15) {
                    SymbolDialLayout dial = new SymbolDialLayout(count, cx, cy, radius, chip,
                            start, step, false, 0);
                    if (dial.fits(width, height)) return dial;
                }
            }
        }
        return null;
    }

    private static boolean hasClearance(float radius, float chip, double step) {
        return radius * Math.sin(Math.toRadians(step / 2)) >= chip * 1.05;
    }

    /** Map the original choices to consecutive slots along the visible arc. */
    int[] groupedOrder(boolean[] letters, int anchor) {
        boolean anchorGroup = anchor < 0 || letters[anchor];
        int groupSize = 0;
        for (boolean letter : letters) if (letter == anchorGroup) groupSize++;
        // Keep the hint's group together and as close to one end of the arc as possible.
        int groupStart = 0;
        if (anchor >= 0) {
            int left = Math.max(0, topSlot - groupSize + 1);
            int right = Math.min(topSlot, letters.length - groupSize);
            groupStart = left <= letters.length - groupSize - right ? left : right;
        }
        int[] order = new int[letters.length];
        int inGroup = groupStart, outside = 0;
        for (int i = 0; i < letters.length; i++) {
            if (i == anchor) {
                order[topSlot] = i;
            } else if (letters[i] == anchorGroup) {
                if (anchor >= 0 && inGroup == topSlot) inGroup++;
                order[inGroup++] = i;
            } else {
                if (outside == groupStart) outside += groupSize;
                order[outside++] = i;
            }
        }
        return order;
    }

    private boolean fits(int width, int height) {
        for (int i = 0; i < x.length; i++) {
            if (x[i] < chipRadius || x[i] > width - chipRadius ||
                    y[i] < chipRadius || y[i] > height - chipRadius) return false;
        }
        return true;
    }

    int choice(float dx, float dy, float deadZone) {
        if (Math.hypot(dx, dy) <= deadZone) return -1;
        double angle = (Math.toDegrees(Math.atan2(dy, dx)) - start) * Math.signum(step);
        angle = (angle + 360) % 360;
        double spacing = Math.abs(step);
        if (fullCircle) return (int) Math.round(angle / spacing) % x.length;
        double end = spacing * (x.length - 1);
        if (angle <= end) return (int) Math.round(angle / spacing);
        double fromFirst = 360 - angle;
        double fromLast = angle - end;
        if (Math.min(fromFirst, fromLast) > Math.min(spacing / 2, 90)) return -1;
        return fromFirst < fromLast ? 0 : x.length - 1;
    }
}
