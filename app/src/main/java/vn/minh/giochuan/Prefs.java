package vn.minh.giochuan;

import android.content.Context;
import android.content.SharedPreferences;

/** Cấu hình lưu trên máy: app bán hàng cần mở, ngưỡng lệch giờ, mã PIN quản trị. */
public final class Prefs {

    /** App bán hàng mặc định: glassRUN Delivery Management. */
    public static final String DEFAULT_TARGET_PKG = "com.disrptiv.glassrun.deliverymanagement";
    public static final String DEFAULT_TARGET_LABEL = "glassRUN";
    /** Mức tối đa cho phép giờ máy chạy NHANH hơn giờ chuẩn (phút). Máy chạy CHẬM thì không cho phép. */
    public static final int DEFAULT_MAX_DRIFT_MIN = 2;
    /** Mã PIN quản trị mặc định — nên đổi ngay sau khi cài. */
    public static final String DEFAULT_PIN = "2026";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("gio_chuan_cfg", Context.MODE_PRIVATE);
    }

    public String targetPackage() {
        return sp.getString("target_pkg", DEFAULT_TARGET_PKG);
    }

    public String targetLabel() {
        return sp.getString("target_label", DEFAULT_TARGET_LABEL);
    }

    public void setTarget(String pkg, String label) {
        sp.edit().putString("target_pkg", pkg).putString("target_label", label).apply();
    }

    public int maxDriftMinutes() {
        return sp.getInt("max_drift_min", DEFAULT_MAX_DRIFT_MIN);
    }

    public void setMaxDriftMinutes(int m) {
        sp.edit().putInt("max_drift_min", m).apply();
    }

    public long maxDriftMs() {
        return maxDriftMinutes() * 60_000L;
    }

    public String pin() {
        return sp.getString("admin_pin", DEFAULT_PIN);
    }

    public void setPin(String pin) {
        sp.edit().putString("admin_pin", pin).apply();
    }

    public boolean asked(String key) {
        return sp.getBoolean("asked_" + key, false);
    }

    public void markAsked(String key) {
        sp.edit().putBoolean("asked_" + key, true).apply();
    }
}
