package vn.minh.giochuan;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bộ màu tone Heineken và các hàm dựng giao diện dùng chung. */
final class Ui {
    static final int GREEN = 0xFF00843D;
    static final int GREEN_LIGHT = 0xFFE3F2E9;
    static final int DARK = 0xFF0B3D1E;
    static final int RED = 0xFFE2001A;
    static final int RED_LIGHT = 0xFFFDE6E8;
    static final int AMBER = 0xFFF2A900;
    static final int BG = 0xFFF3F6F4;
    static final int CARD = 0xFFFFFFFF;
    static final int INK = 0xFF14261B;
    static final int MUTED = 0xFF6B7A70;
    static final int LINE = 0xFFE4EAE6;
    static final int DISABLED = 0xFFB9C4BD;
    static final int WHITE = 0xFFFFFFFF;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Drawable ripple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(rounded(c, CARD, 20));
        l.setElevation(dp(c, 2));
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout.LayoutParams lp(Context c, int width, int height, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static TextView button(Context c, String label, int bg, int fg) {
        TextView b = text(c, label, 16, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        b.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        b.setBackground(ripple(rounded(c, bg, 16), 0x33FFFFFF));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }
}
