package vn.minh.giochuan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final long RECHECK_INTERVAL_MS = 60_000L;

    private enum State { CHECKING, NO_NETWORK, WRONG, OK }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat hms = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE, dd/MM/yyyy", new Locale("vi", "VN"));

    private Prefs prefs;
    private TimeChecker.Result last;
    private long lastCheckElapsed;
    private boolean checking;
    private boolean launchAfterCheck;
    private State state = State.CHECKING;

    private TextView clockDevice, dateDevice, clockNet, netLabel;
    private TextView statusIcon, statusTitle, statusSub;
    private GradientDrawable statusIconBg;
    private Row autoRow, driftRow;
    private TextView primaryBtn, recheckBtn;

    private static final class Row {
        GradientDrawable dot;
        TextView value;
        TextView sub;
    }

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runCheck(false);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClocks();
            if (!checking && SystemClock.elapsedRealtime() - lastCheckElapsed > RECHECK_INTERVAL_MS) {
                runCheck(false);
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        getWindow().setStatusBarColor(Ui.DARK);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildLayout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        ui.post(tick);
        runCheck(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        try {
            unregisterReceiver(timeReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // ------------------------------------------------------------------ Layout

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private View buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = Ui.vbox(this);
        scroll.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));

        root.addView(buildHeader(), new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(buildStatusCard(), Ui.lp(this, MATCH, WRAP, 16, -28, 16, 0));
        root.addView(buildChecklistCard(), Ui.lp(this, MATCH, WRAP, 16, 14, 16, 0));

        primaryBtn = Ui.button(this, "Đang kiểm tra…", Ui.DISABLED, Ui.WHITE);
        primaryBtn.setOnClickListener(v -> onPrimary());
        root.addView(primaryBtn, Ui.lp(this, MATCH, WRAP, 16, 20, 16, 0));

        recheckBtn = Ui.text(this, "Kiểm tra lại", 15, Ui.GREEN, true);
        recheckBtn.setGravity(Gravity.CENTER);
        recheckBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        recheckBtn.setBackground(Ui.ripple(Ui.rounded(this, 0x00000000, 16), 0x2200843D));
        recheckBtn.setOnClickListener(v -> runCheck(false));
        root.addView(recheckBtn, Ui.lp(this, MATCH, WRAP, 16, 6, 16, 0));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        TextView footer = Ui.text(this, "Developed by Ta Thi Thu Minh", 12, Ui.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, Ui.lp(this, MATCH, WRAP, 16, 24, 16, 20));
        return scroll;
    }

    private View buildHeader() {
        LinearLayout header = Ui.vbox(this);
        header.setPadding(dp(24), dp(24), dp(24), dp(52));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.DARK, Ui.GREEN});
        float r = dp(28);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        header.setBackground(bg);
        header.setOnLongClickListener(v -> {
            askPinThenOpenAdmin();
            return true;
        });

        LinearLayout brand = Ui.hbox(this);
        TextView star = Ui.text(this, "★", 14, Ui.RED, true);
        TextView name = Ui.text(this, "REALTIME", 13, 0xE6FFFFFF, true);
        name.setLetterSpacing(0.18f);
        brand.addView(star);
        brand.addView(name, Ui.lp(this, WRAP, WRAP, 8, 0, 0, 0));
        header.addView(brand);

        TextView label = Ui.text(this, "Giờ trên máy", 13, 0xB3FFFFFF, false);
        header.addView(label, Ui.lp(this, WRAP, WRAP, 0, 22, 0, 0));

        clockDevice = Ui.text(this, "--:--:--", 54, Ui.WHITE, false);
        clockDevice.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockDevice.setFontFeatureSettings("tnum");
        clockDevice.setIncludeFontPadding(false);
        header.addView(clockDevice, Ui.lp(this, WRAP, WRAP, 0, 4, 0, 0));

        dateDevice = Ui.text(this, "", 14, 0xCCFFFFFF, false);
        header.addView(dateDevice, Ui.lp(this, WRAP, WRAP, 0, 6, 0, 0));

        LinearLayout pill = Ui.hbox(this);
        pill.setBackground(Ui.rounded(this, 0x26FFFFFF, 14));
        pill.setPadding(dp(14), dp(10), dp(14), dp(10));
        netLabel = Ui.text(this, "Giờ chuẩn internet", 13, 0xCCFFFFFF, false);
        clockNet = Ui.text(this, "--:--:--", 15, Ui.WHITE, true);
        clockNet.setFontFeatureSettings("tnum");
        pill.addView(netLabel, new LinearLayout.LayoutParams(0, WRAP, 1f));
        pill.addView(clockNet);
        header.addView(pill, Ui.lp(this, MATCH, WRAP, 0, 18, 0, 0));
        return header;
    }

    private View buildStatusCard() {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        statusIcon = Ui.text(this, "…", 24, Ui.WHITE, true);
        statusIcon.setGravity(Gravity.CENTER);
        statusIconBg = new GradientDrawable();
        statusIconBg.setShape(GradientDrawable.OVAL);
        statusIconBg.setColor(Ui.AMBER);
        statusIcon.setBackground(statusIconBg);
        card.addView(statusIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout texts = Ui.vbox(this);
        statusTitle = Ui.text(this, "Đang kiểm tra giờ…", 18, Ui.INK, true);
        statusSub = Ui.text(this, "Vui lòng chờ trong giây lát.", 14, Ui.MUTED, false);
        statusSub.setLineSpacing(0, 1.15f);
        texts.addView(statusTitle);
        texts.addView(statusSub, Ui.lp(this, MATCH, WRAP, 0, 4, 0, 0));
        card.addView(texts, Ui.lp(this, 0, WRAP, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) texts.getLayoutParams()).weight = 1f;
        return card;
    }

    private View buildChecklistCard() {
        LinearLayout card = Ui.card(this);
        TextView title = Ui.text(this, "ĐIỀU KIỆN BẮT BUỘC", 12, Ui.MUTED, true);
        title.setLetterSpacing(0.12f);
        card.addView(title);

        autoRow = addRow(card, "Ngày & giờ tự động", "Cài đặt › Ngày & giờ", true);
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        card.addView(line, Ui.lp(this, MATCH, 1, 0, 0, 0, 0));
        driftRow = addRow(card, "Độ lệch so với giờ chuẩn", driftRule(), false);
        return card;
    }

    private Row addRow(LinearLayout parent, String title, String sub, boolean first) {
        LinearLayout row = Ui.hbox(this);
        row.setPadding(0, dp(first ? 12 : 14), 0, dp(first ? 14 : 2));

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Ui.DISABLED);
        dot.setBackground(dotBg);
        row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        LinearLayout texts = Ui.vbox(this);
        texts.addView(Ui.text(this, title, 15, Ui.INK, true));
        TextView subView = Ui.text(this, sub, 12, Ui.MUTED, false);
        texts.addView(subView);
        LinearLayout.LayoutParams tp = Ui.lp(this, 0, WRAP, 12, 0, 8, 0);
        tp.weight = 1f;
        row.addView(texts, tp);

        TextView value = Ui.text(this, "—", 15, Ui.MUTED, true);
        row.addView(value);
        parent.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));

        Row r = new Row();
        r.dot = dotBg;
        r.value = value;
        r.sub = subView;
        return r;
    }

    // ------------------------------------------------------------------ Logic

    private void runCheck(boolean thenLaunch) {
        if (thenLaunch) launchAfterCheck = true;
        if (checking) return;
        checking = true;
        if (last == null) render();
        recheckBtn.setText("Đang kiểm tra…");
        final android.content.ContentResolver cr = getContentResolver();
        new Thread(() -> {
            final TimeChecker.Result r = TimeChecker.check(cr);
            ui.post(() -> {
                checking = false;
                last = r;
                lastCheckElapsed = SystemClock.elapsedRealtime();
                recheckBtn.setText("Kiểm tra lại");
                render();
                if (launchAfterCheck) {
                    launchAfterCheck = false;
                    if (state == State.OK) launchTarget();
                }
            });
        }, "time-check").start();
    }

    private void render() {
        long maxDrift = prefs.maxDriftMs();
        if (last == null) {
            state = State.CHECKING;
        } else if (!last.autoOk()) {
            state = State.WRONG;
        } else if (!last.networkOk) {
            state = State.NO_NETWORK;
        } else if (!last.driftOk(maxDrift)) {
            state = State.WRONG;
        } else {
            state = State.OK;
        }

        // Dòng 1: giờ tự động
        if (last == null) {
            setRow(autoRow, "—", Ui.DISABLED, Ui.MUTED);
        } else if (last.autoOk()) {
            setRow(autoRow, "Đã bật", Ui.GREEN, Ui.GREEN);
        } else {
            setRow(autoRow, "Đang tắt", Ui.RED, Ui.RED);
        }

        // Dòng 2: độ lệch
        driftRow.sub.setText(driftRule());
        if (last == null) {
            setRow(driftRow, "—", Ui.DISABLED, Ui.MUTED);
        } else if (!last.networkOk) {
            setRow(driftRow, "Chưa có mạng", Ui.AMBER, Ui.AMBER);
        } else if (last.driftOk(maxDrift)) {
            setRow(driftRow, driftLabel(last.offsetMs), Ui.GREEN, Ui.GREEN);
        } else {
            setRow(driftRow, driftLabel(last.offsetMs), Ui.RED, Ui.RED);
        }

        switch (state) {
            case CHECKING:
                setStatus("…", Ui.AMBER, "Đang kiểm tra giờ…", "Vui lòng chờ trong giây lát.");
                setPrimary("Đang kiểm tra…", Ui.DISABLED, false);
                break;
            case NO_NETWORK:
                setStatus("!", Ui.AMBER, "Chưa đối chiếu được giờ chuẩn",
                        "Bật 4G hoặc Wi-Fi rồi nhấn Kiểm tra lại.");
                setPrimary("Mở cài đặt mạng", Ui.AMBER, true);
                break;
            case WRONG:
                String sub;
                if (!last.autoOk()) {
                    sub = "Bật Ngày & giờ tự động (và Múi giờ tự động) để tiếp tục làm việc.";
                } else if (last.isBehind()) {
                    sub = "Máy đang chậm " + formatDuration(last.offsetMs)
                            + " so với giờ chuẩn. Không cho phép giờ máy chậm hơn giờ thực.";
                } else {
                    sub = "Máy đang nhanh " + formatDuration(-last.offsetMs)
                            + ", vượt mức cho phép " + prefs.maxDriftMinutes() + " phút. Chỉnh lại để tiếp tục.";
                }
                setStatus("✕", Ui.RED, "Giờ máy chưa đúng", sub);
                setPrimary("Chỉnh giờ ngay", Ui.RED, true);
                break;
            case OK:
                setStatus("✓", Ui.GREEN, "Giờ máy đã chuẩn", "Sẵn sàng làm việc. Chúc bạn một ngày bán hàng hiệu quả!");
                String label = prefs.targetLabel();
                if (prefs.targetPackage().isEmpty()) {
                    setPrimary("Chọn app bán hàng", Ui.GREEN, true);
                } else {
                    setPrimary("Mở " + (label.isEmpty() ? "app bán hàng" : label) + "  →", Ui.GREEN, true);
                }
                break;
        }
        updateClocks();
    }

    private void setRow(Row row, String value, int dotColor, int textColor) {
        row.value.setText(value);
        row.value.setTextColor(textColor);
        row.dot.setColor(dotColor);
    }

    private void setStatus(String icon, int color, String title, String sub) {
        statusIcon.setText(icon);
        statusIconBg.setColor(color);
        statusTitle.setText(title);
        statusSub.setText(sub);
    }

    private void setPrimary(String label, int color, boolean enabled) {
        primaryBtn.setText(label);
        primaryBtn.setBackground(Ui.ripple(Ui.rounded(this, color, 16), 0x33FFFFFF));
        primaryBtn.setEnabled(enabled);
    }

    private void onPrimary() {
        switch (state) {
            case NO_NETWORK:
                openSettings(Settings.ACTION_WIRELESS_SETTINGS);
                break;
            case WRONG:
                openSettings(Settings.ACTION_DATE_SETTINGS);
                Toast.makeText(this, "Bật \"Ngày & giờ tự động\" rồi quay lại app", Toast.LENGTH_LONG).show();
                break;
            case OK:
                if (prefs.targetPackage().isEmpty()) {
                    askPinThenOpenAdmin();
                } else {
                    // Kiểm tra lại ngay trước khi mở để chặn trường hợp vừa chỉnh sai giờ
                    runCheck(true);
                }
                break;
            default:
                break;
        }
    }

    private void launchTarget() {
        Intent i = getPackageManager().getLaunchIntentForPackage(prefs.targetPackage());
        if (i == null) {
            Toast.makeText(this, "Máy chưa cài " + prefs.targetLabel() + ". Đang mở CH Play để cài.", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + prefs.targetPackage()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + prefs.targetPackage()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void askPinThenOpenAdmin() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Nhập mã PIN");
        LinearLayout box = Ui.vbox(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input, new LinearLayout.LayoutParams(MATCH, WRAP));

        new AlertDialog.Builder(this)
                .setTitle("Cài đặt quản trị")
                .setView(box)
                .setPositiveButton("Vào", (d, w) -> {
                    if (prefs.pin().equals(input.getText().toString().trim())) {
                        startActivity(new Intent(this, AdminActivity.class));
                    } else {
                        Toast.makeText(this, "Sai mã PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    // ------------------------------------------------------------------ Clock

    private void updateClocks() {
        long now = System.currentTimeMillis();
        clockDevice.setText(hms.format(new Date(now)));
        String day = dayFmt.format(new Date(now));
        if (!day.isEmpty()) day = Character.toUpperCase(day.charAt(0)) + day.substring(1);
        dateDevice.setText(day);
        if (last != null && last.networkOk) {
            clockNet.setText(hms.format(new Date(now + last.offsetMs)));
        } else {
            clockNet.setText("--:--:--");
        }
    }

    private String driftRule() {
        int m = prefs.maxDriftMinutes();
        return m > 0 ? "Được nhanh tối đa " + m + " phút, không được chậm" : "Không được nhanh hay chậm";
    }

    /** Nhãn độ lệch có chiều: offset = giờ chuẩn - giờ máy. */
    static String driftLabel(long offsetMs) {
        if (Math.abs(offsetMs) < 1000) return "Khớp";
        return (offsetMs < 0 ? "Nhanh " : "Chậm ") + formatDuration(Math.abs(offsetMs));
    }

    static String formatDuration(long ms) {
        long s = ms / 1000;
        if (s < 1) return "< 1 giây";
        if (s < 60) return s + " giây";
        long m = s / 60;
        if (m < 60) return m + " phút" + (s % 60 > 0 ? " " + (s % 60) + " giây" : "");
        long h = m / 60;
        if (h < 24) return h + " giờ" + (m % 60 > 0 ? " " + (m % 60) + " phút" : "");
        long d = h / 24;
        return d + " ngày" + (h % 24 > 0 ? " " + (h % 24) + " giờ" : "");
    }
}
