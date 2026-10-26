package vn.minh.giochuan;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Màn hình đỏ toàn màn hình khi giờ máy sai. Không thoát bằng nút Back. Tự đóng khi giờ đúng. */
public class AlertActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView title, sub;

    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            if (TimeGuardService.lastOk || TimeGuardService.inGrace()) {
                finish();
                return;
            }
            title.setText(TimeGuardService.lastTitle);
            sub.setText(TimeGuardService.lastSub);
            ui.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().setStatusBarColor(Ui.RED);

        LinearLayout box = Ui.vbox(this);
        box.setBackgroundColor(Ui.RED);
        box.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 28);
        box.setPadding(p, p, p, p);

        TextView brand = Ui.text(this, "★ REALTIME", 13, 0xCCFFFFFF, true);
        brand.setGravity(Gravity.CENTER);
        box.addView(brand);

        title = Ui.text(this, TimeGuardService.lastTitle, 28, Ui.WHITE, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 0));

        sub = Ui.text(this, TimeGuardService.lastSub, 16, Ui.WHITE, false);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));

        TextView note = Ui.text(this, "Màn hình này sẽ tự đóng khi giờ máy đúng.", 13, 0xCCFFFFFF, false);
        note.setGravity(Gravity.CENTER);
        box.addView(note, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 0));

        TextView btn = Ui.button(this, "Chỉnh giờ ngay", Ui.WHITE, Ui.RED);
        btn.setOnClickListener(v -> fixTime());
        box.addView(btn, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 32, 0, 0));

        setContentView(box);
        ui.post(watch);
    }

    private void fixTime() {
        TimeGuardService.graceUntil = System.currentTimeMillis() + TimeGuardService.GRACE_MS;
        Toast.makeText(this, "Bật \"Ngày & giờ tự động\" rồi quay lại RealTime", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    public void onBackPressed() {
        // Không cho thoát bằng nút Back khi giờ còn sai
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
