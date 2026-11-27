// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.Locale;

import helium314.keyboard.keyboard.internal.PopupKeySpec;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.settings.Settings;

final class SymbolDialView extends View implements PopupKeysPanel {
    private final MainKeyboardView keyboard;
    private final KeyboardActionListener listener;
    private final PopupKeySpec[] symbols;
    private final SymbolDialLayout layout;
    private final Paint paint;
    private final float chipRadius;
    private final float deadZone;
    private int originX, originY, pointerId;
    private int selection = -1;

    static SymbolDialView create(MainKeyboardView keyboard, Key key, ViewGroup overlay, Paint paint,
            KeyboardActionListener listener) {
        PopupKeySpec[] symbols = key.getPopupKeys();
        Keyboard board = keyboard.getKeyboard();
        float size = Math.min(board.mMostCommonKeyWidth, board.mMostCommonKeyHeight);
        float chipRadius = size * 0.54f;
        paint.setTextSize(chipRadius * 1.25f);
        for (PopupKeySpec symbol : symbols) {
            // Keep icon actions and long custom labels in the regular, full-size popup.
            if (symbol.mLabel == null || symbol.mIconName != null
                    || paint.measureText(symbol.mLabel) > 1.7f * chipRadius) return null;
        }
        String hint = Settings.getValues().mShowsHints ? key.getHintLabel() : null;
        PopupKeySpec anchor = null;
        for (PopupKeySpec symbol : symbols) {
            if (symbol.mLabel.equals(hint)) {
                anchor = symbol;
                break;
            }
            if (anchor == null && Character.isDigit(symbol.mLabel.codePointAt(0))) anchor = symbol;
        }
        // Hint sources and popup sources can be configured independently.
        if (hint != null && hint.codePointCount(0, hint.length()) == 1
                && (anchor == null || !hint.equals(anchor.mLabel))) {
            String literal = hint.replace("\\", "\\\\").replace("|", "\\|");
            anchor = new PopupKeySpec(literal, false, Locale.ROOT);
            symbols = Arrays.copyOf(symbols, symbols.length + 1);
            symbols[symbols.length - 1] = anchor;
        }
        boolean[] letters = new boolean[symbols.length];
        int anchorIndex = -1;
        for (int i = 0; i < symbols.length; i++) {
            letters[i] = Character.isLetter(symbols[i].mLabel.codePointAt(0));
            if (symbols[i] == anchor) anchorIndex = i;
        }
        int pinnedGroupSize = 0;
        if (anchorIndex >= 0) {
            for (boolean letter : letters) if (letter == letters[anchorIndex]) pinnedGroupSize++;
        }
        int[] origin = new int[2], overlayOrigin = new int[2];
        keyboard.getLocationInWindow(origin);
        overlay.getLocationInWindow(overlayOrigin);
        SymbolDialLayout layout = SymbolDialLayout.fit(symbols.length,
                origin[0] - overlayOrigin[0] + keyboard.getPaddingLeft() + key.getX() + key.getWidth() / 2f,
                origin[1] - overlayOrigin[1] + keyboard.getPaddingTop() + key.getY() + key.getHeight() / 2f,
                overlay.getWidth(), overlay.getHeight(), size, pinnedGroupSize);
        if (layout == null) return null;
        int[] order = layout.groupedOrder(letters, anchorIndex);
        PopupKeySpec[] ordered = new PopupKeySpec[symbols.length];
        for (int i = 0; i < order.length; i++) ordered[i] = symbols[order[i]];
        paint.setTextSize(layout.chipRadius * 1.25f);
        return new SymbolDialView(keyboard, ordered, layout, paint, layout.chipRadius, listener);
    }

    private SymbolDialView(MainKeyboardView keyboard, PopupKeySpec[] symbols, SymbolDialLayout layout,
            Paint paint, float chipRadius, KeyboardActionListener listener) {
        super(keyboard.getContext());
        this.keyboard = keyboard;
        this.symbols = symbols;
        this.layout = layout;
        this.listener = listener;
        this.paint = paint;
        this.chipRadius = chipRadius;
        deadZone = PointerTracker.symbolFlickThreshold();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override public View getContainerView() { return this; }

    @Override public void showInParent(ViewGroup parent) {
        parent.addView(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override protected void onDraw(Canvas canvas) {
        var colors = Settings.getValues().mColors;
        float textOffset = -(paint.ascent() + paint.descent()) / 2;
        for (int i = 0; i < symbols.length; i++) {
            boolean selected = i == selection;
            paint.setColor(colors.get(selected ? ColorType.ACTION_KEY_BACKGROUND : ColorType.POPUP_KEYS_BACKGROUND));
            canvas.drawCircle(layout.x[i], layout.y[i], chipRadius, paint);
            paint.setColor(colors.get(selected ? ColorType.ACTION_KEY_ICON : ColorType.POPUP_KEY_TEXT));
            canvas.drawText(symbols[i].mLabel, layout.x[i], layout.y[i] + textOffset, paint);
        }
    }

    // All coordinates are relative to the main keyboard view.
    @Override public int translateX(int x) { return x; }
    @Override public int translateY(int y) { return y; }

    @Override public void onDownEvent(int x, int y, int id, long time) {
        originX = x;
        originY = y;
        pointerId = id;
    }

    @Override public void onMoveEvent(int x, int y, int id, long time) {
        if (id != pointerId) return;
        int choice = layout.choice(x - originX, y - originY, deadZone);
        if (selection != choice) {
            selection = choice;
            invalidate();
        }
    }

    @Override public void onUpEvent(int x, int y, int id, long time) {
        if (id != pointerId) return;
        onMoveEvent(x, y, id, time);
        if (selection < 0) return;
        PopupKeySpec symbol = symbols[selection];
        if (symbol.mCode == KeyCode.MULTIPLE_CODE_POINTS) listener.onTextInput(symbol.mOutputText);
        else if (symbol.mCode != KeyCode.NOT_SPECIFIED)
            listener.onCodeInput(symbol.mCode, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false);
    }

    @Override public void dismissPopupKeysPanel() {
        if (isShowingInParent()) keyboard.onDismissPopupKeysPanel();
    }
}
