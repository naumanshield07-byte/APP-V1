package com.gtinwmsqr;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

/** Square QR tile constrained by available width, including small displays. */
public class ResponsiveQrView extends AppCompatImageView {
    public ResponsiveQrView(Context c) { super(c); }
    public ResponsiveQrView(Context c, AttributeSet a) { super(c, a); }
    public ResponsiveQrView(Context c, AttributeSet a, int d) { super(c, a, d); }
    @Override public void setImageBitmap(android.graphics.Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        if (bitmap != null && android.provider.Settings.Global.getFloat(
                getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f) {
            animate().cancel(); setAlpha(0f); animate().alpha(1f).setDuration(160).start();
        }
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int cap = Math.round(320 * getResources().getDisplayMetrics().density);
        int available = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
            ? cap : MeasureSpec.getSize(widthSpec);
        int side = Math.min(cap, available);
        setMeasuredDimension(side, side);
    }
}
