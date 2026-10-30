package com.gtinwmsqr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Direct barcode-to-QR mode. Never normalizes or looks up a scanned value. */
final class FeatureFour {
    static final int SAVE_REQUEST = 4004;
    private final MainActivity a;
    boolean active;
    private int generation;
    private LinearLayout panel;
    private final List<View> hidden = new ArrayList<>();
    private final List<Integer> visibility = new ArrayList<>();

    FeatureFour(MainActivity activity) { a = activity; }

    void enter() {
        active = true;
        a.textQrMode = false;
        a.slKlMode = false;
        a.openScannerMode();
    }

    void restore() {
        generation++;
        if (panel != null && panel.getParent() instanceof ViewGroup) {
            ((ViewGroup) panel.getParent()).removeView(panel);
        }
        panel = null;
        for (int i = 0; i < hidden.size(); i++) {
            hidden.get(i).setVisibility(visibility.get(i));
        }
        hidden.clear();
        visibility.clear();
    }

    void scannerLabels() {
        TextView hint = a.findViewById(R.id.cameraHint);
        hint.setText(active ? "SCAN ANY BARCODE" : "SCAN PRODUCT GTIN");
        if (active) a.manualButton.setVisibility(View.GONE);
    }

    private int dp(int n) {
        return Math.round(n * a.getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size, int color) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String text) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundResource(R.drawable.glow_button_red);
        b.setMinHeight(dp(52));
        return b;
    }

    void showValue(String value, boolean scanned) {
        if (!active || value == null || value.isEmpty()) return;
        restore();
        final int token = generation;
        a.stopCamera();
        a.cameraCard.setVisibility(View.GONE);
        a.manualPanel.setVisibility(View.GONE);
        a.resultScroll.setVisibility(View.VISIBLE);
        LinearLayout host = (LinearLayout) a.resultScroll.getChildAt(0);
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            hidden.add(child);
            visibility.add(child.getVisibility());
            child.setVisibility(View.GONE);
        }
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackgroundResource(R.drawable.glow_card);
        TextView title = label("GENERATING QR", 21, Color.parseColor("#A855F7"));
        panel.addView(title);
        panel.addView(label("SCANNED BARCODE", 13, Color.WHITE));
        TextView content = label(value, 17, Color.WHITE);
        content.setTextIsSelectable(true);
        panel.addView(content);

        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = button("EDIT"), copy = button("COPY");
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(0, dp(52), 1);
        actionSize.setMargins(dp(4), dp(8), dp(4), dp(12));
        actions.addView(edit, actionSize);
        actions.addView(copy, new LinearLayout.LayoutParams(actionSize));
        panel.addView(actions);

        ImageView image = new ImageView(a) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int side = Math.min(dp(320), View.MeasureSpec.getSize(widthSpec));
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY));
            }
        };
        image.setContentDescription("QR code for the scanned barcode");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams imageSize = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(320));
        imageSize.gravity = Gravity.CENTER_HORIZONTAL;
        imageSize.setMargins(0, dp(8), 0, dp(16));
        panel.addView(image, imageSize);
        Button save = button("SAVE QR"), again = button("SCAN AGAIN");
        save.setEnabled(false);
        panel.addView(save, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout.LayoutParams againSize = new LinearLayout.LayoutParams(-1, dp(56));
        againSize.topMargin = dp(12);
        panel.addView(again, againSize);
        host.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        a.resultScroll.scrollTo(0, 0);
        a.status.setText("Generating QR...");

        copy.setOnClickListener(v -> a.copyValue(value));
        again.setOnClickListener(v -> a.openScannerMode());
        edit.setOnClickListener(v -> {
            EditText input = new EditText(a);
            input.setText(value);
            input.setSelectAllOnFocus(true);
            input.setSingleLine(true);
            AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Edit scanned barcode").setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("UPDATE", null).create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(b -> {
                    String updated = input.getText().toString();
                    if (updated.isEmpty()) { input.setError("Enter a barcode value"); return; }
                    dialog.dismiss();
                    showValue(updated, false);
                }));
            dialog.show();
        });

        a.qrExecutor.submit(() -> {
            try {
                Bitmap bitmap = encode(value);
                a.runOnUiThread(() -> {
                    if (!current(token)) { bitmap.recycle(); return; }
                    image.setImageBitmap(bitmap);
                    title.setText("QR GENERATED");
                    save.setEnabled(true);
                    save.setOnClickListener(v -> chooseSave(bitmap));
                    if (scanned) a.saveHistoryEntry("BARCODE", value, "scan");
                    a.status.setText("QR ready - original barcode value preserved.");
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    if (!current(token)) return;
                    title.setText("QR NOT GENERATED");
                    image.setVisibility(View.GONE);
                    a.status.setText("QR generation failed. Edit the value or scan again.");
                    Toast.makeText(a, "QR error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean current(int token) {
        return active && generation == token && !a.isFinishing() && !a.isDestroyed();
    }

    static Bitmap encode(String value) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 4);
        BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 768, 768, hints);
        int width = matrix.getWidth(), height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private void chooseSave(Bitmap bitmap) {
        // The system file picker works on API 23+ without storage permissions.
        try {
            try (OutputStream out = new FileOutputStream(pendingFile())) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new java.io.IOException("PNG encoding failed");
            }
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_TITLE, "Barcode-QR-" + System.currentTimeMillis() + ".png");
            a.startActivityForResult(intent, SAVE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(a, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File pendingFile() { return new File(a.getCacheDir(), "feature4-pending.png"); }

    boolean handleSaveResult(int request, int result, Intent data) {
        if (request != SAVE_REQUEST) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        a.qrExecutor.submit(() -> {
            try (FileInputStream in = new FileInputStream(pendingFile());
                 OutputStream out = a.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Could not open destination");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                a.runOnUiThread(() -> Toast.makeText(a, "QR saved", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
        return true;
    }
}
