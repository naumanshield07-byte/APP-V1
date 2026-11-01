package com.gtinwmsqr;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.WeakHashMap;

/** Presentation only: never replaces listeners, barcode values or business state. */
final class PremiumUi {
    private final MainActivity activity;
    private final WeakHashMap<View, Boolean> styled = new WeakHashMap<>();
    private final WeakHashMap<View, Boolean> visible = new WeakHashMap<>();
    private PremiumUi(MainActivity a) { activity = a; }
    static void install(MainActivity a) {
        PremiumUi ui = new PremiumUi(a);
        View root = a.findViewById(android.R.id.content);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View header = (View)a.flashButton.getParent();
            int headerVisibility = a.modeSelection.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE;
            if (header.getVisibility() != headerVisibility) header.setVisibility(headerVisibility);
            ui.visit(root);
            ui.enter(a.modeSelection);
            ui.enter(a.resultScroll);
            ui.enter(a.manualPanel);
            ui.enter(a.historyPanel);
            ui.enter(a.findViewById(R.id.cameraHint));
        });
        ui.visit(root);
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private boolean motion() {
        return Settings.Global.getFloat(activity.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }
    private void enter(View v) {
        if (v == null) return;
        boolean now = v.isShown();
        Boolean before = visible.put(v, now);
        if (now && !Boolean.TRUE.equals(before) && motion()) {
            v.animate().cancel();
            v.setAlpha(0f); v.setTranslationY(dp(12));
            v.animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator()).start();
        } else if (!now || !motion()) {
            v.animate().cancel(); v.setAlpha(1f); v.setTranslationY(0f);
        }
    }
    private void visit(View v) {
        if (!styled.containsKey(v)) {
            styled.put(v, true);
            if (v instanceof GridLayout) {
                android.content.res.Configuration c = activity.getResources().getConfiguration();
                if (c.screenWidthDp < 360 || c.fontScale > 1.3f)
                    ((GridLayout)v).setColumnCount(1);
            }
            if (v instanceof Button) {
                Button b = (Button)v;
                b.setBackgroundTintList(null);
                b.setTextColor(new ColorStateList(new int[][]{
                    new int[]{-android.R.attr.state_enabled}, new int[]{}},
                    new int[]{0xFF849396, 0xFFF1F6F5}));
                b.setAllCaps(false);
                b.setMinHeight(dp(52)); b.setMinimumHeight(dp(52));
                b.setPadding(dp(12), dp(12), dp(12), dp(12));
                ViewGroup.LayoutParams lp = b.getLayoutParams();
                if (lp != null && lp.height > 0) { lp.height = -2; b.setLayoutParams(lp); }
            }
            if (v.isClickable() && !(v instanceof EditText)) {
                // Elevation animation leaves click and touch handlers intact.
                StateListAnimator states = new StateListAnimator();
                states.addState(new int[]{android.R.attr.state_pressed},
                    ObjectAnimator.ofFloat(v, "translationZ", dp(3)).setDuration(100));
                states.addState(new int[]{},
                    ObjectAnimator.ofFloat(v, "translationZ", 0f).setDuration(140));
                v.setStateListAnimator(states);
            }
            if (v instanceof EditText) {
                ((EditText)v).setTextColor(0xFFF1F6F5);
                ((EditText)v).setHintTextColor(0xFFA7B8BC);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)v;
            for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i));
        }
    }
}
