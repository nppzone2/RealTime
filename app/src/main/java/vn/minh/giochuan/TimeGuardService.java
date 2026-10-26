package vn.minh.giochuan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Dịch vụ canh giờ chạy nền:
 *  - Đối chiếu giờ 10 phút một lần, ngay khi giờ máy hoặc "Ngày & giờ tự động" bị đổi.
 *  - Giờ sai: bật thông báo toàn màn hình (không vuốt bỏ được), kêu và rung mỗi 30 giây;
 *    nếu được cấp quyền thì phủ thêm màn hình đỏ lên mọi app.
 *  - Khi đi chỉnh giờ trong Cài đặt, tạm ngừng cảnh báo 3 phút.
 */
public class TimeGuardService extends Service {

    static final long PERIOD_MS = 10 * 60_000L;
    static final long ALERT_MS = 30_000L;
    static final long GRACE_MS = 3 * 60_000L;
    static final long FAST_MS = 3_000L;

    /** Trạng thái dùng chung với AlertActivity. */
    static volatile boolean lastOk = false;
    static volatile String lastTitle = "";
    static volatile String lastSub = "";
    static volatile long graceUntil;

    static boolean inGrace() {
        return System.currentTimeMillis() < graceUntil;
    }

    private static final String CH_KEEP = "guard_keep";
    private static final String CH_ALERT = "guard_alert";
    private static final int NOTI_KEEP = 7;
    private static final int NOTI_ALERT = 8;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private NotificationManager nm;
    private LinearLayout overlay;
    private TextView overlayTitle, overlaySub;
    private boolean checking;

    private final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            check();
            ui.postDelayed(this, PERIOD_MS);
        }
    };

    private final Runnable alertLoop = new Runnable() {
        @Override
        public void run() {
            check();
            ui.removeCallbacks(this);
            if (!lastOk) ui.postDelayed(this, ALERT_MS);
        }
    };

    private final Runnable fast = new Runnable() {
        @Override
        public void run() {
            check();
            if (System.currentTimeMillis() < graceUntil + FAST_MS) ui.postDelayed(this, FAST_MS);
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            check();
        }
    };

    private final ContentObserver autoObserver = new ContentObserver(ui) {
        @Override
        public void onChange(boolean selfChange) {
            check();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels();
        startForeground(NOTI_KEEP, keepNotification("Đang canh giờ máy"));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME), false, autoObserver);
        ui.post(periodic);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(timeReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        getContentResolver().unregisterContentObserver(autoObserver);
        hideOverlay();
        super.onDestroy();
    }

    // ------------------------------------------------------------ Kiểm tra

    void check() {
        if (checking) return;
        checking = true;
        final ContentResolver cr = getContentResolver();
        new Thread(() -> {
            final TimeChecker.Result r = TimeChecker.check(cr);
            ui.post(() -> {
                checking = false;
                apply(r);
            });
        }, "guard-check").start();
    }

    private void apply(TimeChecker.Result r) {
        Prefs prefs = new Prefs(this);
        if (r.passed(prefs.maxDriftMs())) {
            lastOk = true;
            ui.removeCallbacks(alertLoop);
            hideOverlay();
            nm.cancel(NOTI_ALERT);
            nm.notify(NOTI_KEEP, keepNotification("Giờ máy đã chuẩn"));
            return;
        }

        String title = "Giờ máy chưa đúng";
        String sub;
        if (!r.autoOk()) {
            sub = "Bật \"Ngày & giờ tự động\" và \"Múi giờ tự động\" để tiếp tục làm việc.";
        } else if (!r.networkOk) {
            title = "Chưa xác minh được giờ";
            sub = "Bật 4G hoặc Wi-Fi để RealTime đối chiếu giờ chuẩn.";
        } else if (r.isBehind()) {
            sub = "Máy đang chậm " + MainActivity.formatDuration(r.offsetMs)
                    + " so với giờ chuẩn. Chỉnh lại để tiếp tục.";
        } else {
            sub = "Máy đang nhanh " + MainActivity.formatDuration(-r.offsetMs)
                    + ", vượt mức cho phép " + prefs.maxDriftMinutes() + " phút. Chỉnh lại để tiếp tục.";
        }

        lastOk = false;
        lastTitle = title;
        lastSub = sub;
        nm.notify(NOTI_KEEP, keepNotification(title));

        ui.removeCallbacks(alertLoop);
        ui.postDelayed(alertLoop, ALERT_MS);

        if (inGrace()) {
            hideOverlay();
            nm.cancel(NOTI_ALERT);
        } else {
            nm.notify(NOTI_ALERT, alertNotification(title, sub));
            showOverlay(title, sub);
        }
    }

    // ------------------------------------------------------------ Màn hình đỏ phủ (khi có quyền)

    private void showOverlay(String title, String sub) {
        if (!Settings.canDrawOverlays(this)) return;
        if (overlay == null) {
            LinearLayout box = Ui.vbox(this);
            box.setBackgroundColor(Ui.RED);
            box.setGravity(Gravity.CENTER);
            int p = Ui.dp(this, 28);
            box.setPadding(p, p, p, p);
            box.setClickable(true);

            overlayTitle = Ui.text(this, "", 26, Ui.WHITE, true);
            overlayTitle.setGravity(Gravity.CENTER);
            box.addView(overlayTitle);

            overlaySub = Ui.text(this, "", 16, Ui.WHITE, false);
            overlaySub.setGravity(Gravity.CENTER);
            box.addView(overlaySub, Ui.lp(this, MATCH, WRAP, 0, 12, 0, 0));

            TextView btn = Ui.button(this, "Chỉnh giờ ngay", Ui.WHITE, Ui.RED);
            btn.setOnClickListener(v -> openDateSettings());
            box.addView(btn, Ui.lp(this, MATCH, WRAP, 0, 32, 0, 0));

            try {
                wm.addView(box, new WindowManager.LayoutParams(MATCH, MATCH,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.OPAQUE));
                overlay = box;
            } catch (RuntimeException e) {
                return;
            }
        }
        overlayTitle.setText(title);
        overlaySub.setText(sub);
    }

    private void hideOverlay() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (RuntimeException ignored) {
            }
            overlay = null;
        }
    }

    private void openDateSettings() {
        hideOverlay();
        graceUntil = System.currentTimeMillis() + GRACE_MS;
        ui.removeCallbacks(fast);
        ui.post(fast);
        Toast.makeText(this, "Chỉnh giờ rồi quay lại RealTime", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // ------------------------------------------------------------ Thông báo

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        nm.createNotificationChannel(new NotificationChannel(CH_KEEP, "Canh giờ",
                NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alert = new NotificationChannel(CH_ALERT, "Giờ máy chưa đúng",
                NotificationManager.IMPORTANCE_HIGH);
        alert.enableVibration(true);
        nm.createNotificationChannel(alert);
    }

    private Notification.Builder builder(String channel) {
        return Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channel)
                : new Notification.Builder(this);
    }

    private PendingIntent mainPendingIntent() {
        return PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification keepNotification(String text) {
        return builder(CH_KEEP)
                .setContentTitle("RealTime")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(mainPendingIntent())
                .setOngoing(true)
                .build();
    }

    private Notification alertNotification(String title, String sub) {
        PendingIntent full = PendingIntent.getActivity(this, 1,
                new Intent(this, AlertActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return builder(CH_ALERT)
                .setContentTitle(title)
                .setContentText(sub)
                .setStyle(new Notification.BigTextStyle().bigText(sub))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }
}
