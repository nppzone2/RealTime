package vn.minh.giochuan;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Thiết lập bắt buộc sau khi cài: phải cấp đủ quyền mới vào được màn hình chính.
 * Bấm "Cấp quyền lần lượt" → app mở từng trang cài đặt, cấp xong quay lại sẽ tự chuyển sang quyền kế tiếp.
 */
public class SetupActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int STEPS = 5;

    private static final String[] TITLES = {
            "Thông báo",
            "Thông báo toàn màn hình",
            "Hiển thị trên ứng dụng khác",
            "Truy cập dữ liệu sử dụng",
            "Pin không hạn chế"
    };
    private static final String[] HINTS = {
            "Để hiện cảnh báo \"Giờ máy chưa đúng\"",
            "Để bật màn hình đỏ khi máy khoá",
            "Để phủ màn hình đỏ lên glassRUN",
            "Để nhận biết khi đang mở glassRUN",
            "Để RealTime không bị tắt khi chạy nền"
    };

    private Prefs prefs;
    private final TextView[] values = new TextView[STEPS];
    private final GradientDrawable[] dots = new GradientDrawable[STEPS];
    private TextView progress, startBtn;
    private boolean chain;
    private int lastOpened = -1;

    // ------------------------------------------------------------ Kiểm tra quyền

    static boolean granted(Context c, int step) {
        switch (step) {
            case 0:
                if (Build.VERSION.SDK_INT >= 33) {
                    return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                            == PackageManager.PERMISSION_GRANTED;
                }
                return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled();
            case 1:
                return Build.VERSION.SDK_INT < 34
                        || ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).canUseFullScreenIntent();
            case 2:
                return Settings.canDrawOverlays(c);
            case 3:
                return TimeGuardService.hasUsageAccess(c);
            case 4:
                return ((PowerManager) c.getSystemService(Context.POWER_SERVICE))
                        .isIgnoringBatteryOptimizations(c.getPackageName());
            default:
                return true;
        }
    }

    static boolean allGranted(Context c) {
        for (int i = 0; i < STEPS; i++) {
            if (!granted(c, i)) return false;
        }
        return true;
    }

    private int nextMissing() {
        for (int i = 0; i < STEPS; i++) {
            if (!granted(this, i)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------ Giao diện

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        getWindow().setStatusBarColor(Ui.DARK);
        getWindow().setNavigationBarColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.vbox(this);
        scroll.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));

        LinearLayout header = Ui.vbox(this);
        header.setPadding(dp(24), dp(24), dp(24), dp(28));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.DARK, Ui.GREEN}));
        TextView brand = Ui.text(this, "★ REALTIME", 13, 0xE6FFFFFF, true);
        brand.setLetterSpacing(0.18f);
        header.addView(brand);
        header.addView(Ui.text(this, "Thiết lập lần đầu", 26, Ui.WHITE, true), Ui.lp(this, WRAP, WRAP, 0, 14, 0, 0));
        header.addView(Ui.text(this, "Cấp đủ 5 quyền để RealTime cảnh báo và chặn khi giờ máy sai.", 14, 0xCCFFFFFF, false),
                Ui.lp(this, MATCH, WRAP, 0, 6, 0, 0));
        progress = Ui.text(this, "", 14, Ui.WHITE, true);
        header.addView(progress, Ui.lp(this, WRAP, WRAP, 0, 14, 0, 0));
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout card = Ui.card(this);
        for (int i = 0; i < STEPS; i++) {
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(Ui.LINE);
                card.addView(line, Ui.lp(this, MATCH, 1, 0, 0, 0, 0));
            }
            card.addView(buildRow(i), new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        root.addView(card, Ui.lp(this, MATCH, WRAP, 16, 16, 16, 0));

        // Hướng dẫn khi máy báo "Cài đặt bị hạn chế" (Pixel, Android 13+)
        LinearLayout tip = Ui.vbox(this);
        tip.setBackground(Ui.rounded(this, 0xFFFFF4D6, 16));
        tip.setPadding(dp(16), dp(14), dp(16), dp(14));
        tip.addView(Ui.text(this, "Máy không cho bật quyền?", 15, Ui.INK, true));
        tip.addView(Ui.text(this,
                "Với Pixel và Android 13 trở lên: mở Thông tin ứng dụng → bấm ⋮ góc trên phải → "
                        + "\"Cho phép cài đặt bị hạn chế\" → xác nhận vân tay hoặc mã PIN, rồi quay lại bật quyền.",
                13, Ui.MUTED, false), Ui.lp(this, MATCH, WRAP, 0, 4, 0, 0));
        TextView info = Ui.text(this, "Mở Thông tin ứng dụng", 14, Ui.GREEN, true);
        info.setPadding(0, dp(10), 0, 0);
        info.setOnClickListener(v -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        tip.addView(info);
        root.addView(tip, Ui.lp(this, MATCH, WRAP, 16, 14, 16, 0));

        startBtn = Ui.button(this, "", Ui.GREEN, Ui.WHITE);
        startBtn.setOnClickListener(v -> startChain());
        root.addView(startBtn, Ui.lp(this, MATCH, WRAP, 16, 20, 16, 0));

        TextView skip = Ui.text(this, "Quản lý: bỏ qua bằng mã PIN", 13, Ui.MUTED, false);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, dp(14), 0, dp(14));
        skip.setOnClickListener(v -> askPinSkip());
        root.addView(skip, Ui.lp(this, MATCH, WRAP, 16, 4, 16, 0));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        TextView footer = Ui.text(this, "Developed by Ta Thi Thu Minh", 12, Ui.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, Ui.lp(this, MATCH, WRAP, 16, 16, 16, 20));

        setContentView(scroll);
    }

    private View buildRow(final int i) {
        LinearLayout row = Ui.hbox(this);
        row.setPadding(0, dp(14), 0, dp(14));
        row.setOnClickListener(v -> openStep(i));

        View dot = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Ui.DISABLED);
        dot.setBackground(bg);
        dots[i] = bg;
        row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        LinearLayout texts = Ui.vbox(this);
        texts.addView(Ui.text(this, (i + 1) + ". " + TITLES[i], 15, Ui.INK, true));
        texts.addView(Ui.text(this, HINTS[i], 12, Ui.MUTED, false));
        LinearLayout.LayoutParams tp = Ui.lp(this, 0, WRAP, 12, 0, 8, 0);
        tp.weight = 1f;
        row.addView(texts, tp);

        values[i] = Ui.text(this, "", 14, Ui.MUTED, true);
        row.addView(values[i]);
        return row;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allGranted(this)) {
            startService(new Intent(this, TimeGuardService.class));
            Toast.makeText(this, "Đã cấp đủ quyền", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        render();
        if (chain) {
            int next = nextMissing();
            // Quyền vừa mở vẫn chưa được bật → dừng tự chuyển, tránh mở lặp lại
            if (next == lastOpened) {
                chain = false;
                Toast.makeText(this, "\"" + TITLES[next] + "\" chưa bật. Chạm vào dòng đó để thử lại.", Toast.LENGTH_LONG).show();
                render();
            } else {
                openStep(next);
            }
        }
    }

    private void render() {
        int done = 0;
        for (int i = 0; i < STEPS; i++) {
            boolean ok = granted(this, i);
            if (ok) done++;
            values[i].setText(ok ? "Đã bật" : "Chưa bật");
            values[i].setTextColor(ok ? Ui.GREEN : Ui.RED);
            dots[i].setColor(ok ? Ui.GREEN : Ui.RED);
        }
        progress.setText("Đã bật " + done + "/" + STEPS + " quyền");
        startBtn.setText(done == 0 ? "Cấp quyền lần lượt" : "Tiếp tục cấp quyền còn thiếu");
    }

    private void startChain() {
        int next = nextMissing();
        if (next < 0) return;
        chain = true;
        openStep(next);
    }

    // ------------------------------------------------------------ Mở trang cấp quyền

    private void openStep(int i) {
        if (granted(this, i)) return;
        lastOpened = i;
        String pkg = "package:" + getPackageName();
        switch (i) {
            case 0:
                if (Build.VERSION.SDK_INT >= 33 && !prefs.asked("notif_dialog")) {
                    prefs.markAsked("notif_dialog");
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                } else {
                    open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
                break;
            case 1:
                open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse(pkg)));
                break;
            case 2:
                open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg)));
                break;
            case 3:
                open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse(pkg)));
                break;
            case 4:
                open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse(pkg)));
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        onResume();
    }

    private void open(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void askPinSkip() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Nhập mã PIN quản lý");
        LinearLayout box = Ui.vbox(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input, new LinearLayout.LayoutParams(MATCH, WRAP));
        new AlertDialog.Builder(this)
                .setTitle("Bỏ qua thiết lập")
                .setView(box)
                .setPositiveButton("Bỏ qua", (d, w) -> {
                    if (prefs.pin().equals(input.getText().toString().trim())) {
                        prefs.markAsked("setup_skipped");
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Sai mã PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        // Chưa cấp đủ quyền thì không thoát ra màn hình chính bằng nút Back
        moveTaskToBack(true);
    }
}
