package vn.minh.giochuan;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Màn hình quản trị: chọn app bán hàng, ngưỡng lệch giờ, đổi mã PIN. */
public class AdminActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Prefs prefs;
    private String selectedPkg;
    private String selectedLabel;
    private LinearLayout appList;
    private EditText driftInput, pinInput;

    private static final class AppItem {
        String pkg;
        String label;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        selectedPkg = prefs.targetPackage();
        selectedLabel = prefs.targetLabel();
        getWindow().setStatusBarColor(Ui.DARK);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildLayout());
        renderApps();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private View buildLayout() {
        LinearLayout outer = Ui.vbox(this);
        outer.setBackgroundColor(Ui.BG);

        LinearLayout header = Ui.vbox(this);
        header.setBackgroundColor(Ui.DARK);
        header.setPadding(dp(24), dp(20), dp(24), dp(20));
        header.addView(Ui.text(this, "Cài đặt quản trị", 22, Ui.WHITE, true));
        header.addView(Ui.text(this, "Chỉ dành cho quản lý. DCR không cần thao tác màn hình này.", 13, 0xCCFFFFFF, false),
                Ui.lp(this, MATCH, WRAP, 0, 4, 0, 0));
        outer.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = Ui.vbox(this);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(body, new ViewGroup.LayoutParams(MATCH, WRAP));
        outer.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // Ngưỡng & PIN
        LinearLayout cfg = Ui.card(this);
        cfg.addView(sectionTitle("MÁY ĐƯỢC CHẠY NHANH TỐI ĐA (PHÚT) · KHÔNG CHO CHẬM"));
        driftInput = new EditText(this);
        driftInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        driftInput.setText(String.valueOf(prefs.maxDriftMinutes()));
        cfg.addView(driftInput, new LinearLayout.LayoutParams(MATCH, WRAP));
        cfg.addView(sectionTitle("ĐỔI MÃ PIN (để trống nếu giữ nguyên)"), Ui.lp(this, MATCH, WRAP, 0, 12, 0, 0));
        pinInput = new EditText(this);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("PIN mới, 4–8 số");
        cfg.addView(pinInput, new LinearLayout.LayoutParams(MATCH, WRAP));
        body.addView(cfg, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Danh sách app
        LinearLayout appsCard = Ui.card(this);
        appsCard.addView(sectionTitle("APP BÁN HÀNG SẼ MỞ SAU KHI GIỜ ĐÚNG"));
        appList = Ui.vbox(this);
        appsCard.addView(appList, Ui.lp(this, MATCH, WRAP, 0, 8, 0, 0));
        body.addView(appsCard, Ui.lp(this, MATCH, WRAP, 0, 14, 0, 0));

        // Nút lưu
        TextView save = Ui.button(this, "Lưu cài đặt", Ui.GREEN, Ui.WHITE);
        save.setOnClickListener(v -> save());
        LinearLayout bottom = Ui.vbox(this);
        bottom.setPadding(dp(16), dp(10), dp(16), dp(16));
        bottom.addView(save, new LinearLayout.LayoutParams(MATCH, WRAP));
        outer.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        return outer;
    }

    private TextView sectionTitle(String s) {
        TextView t = Ui.text(this, s, 12, Ui.MUTED, true);
        t.setLetterSpacing(0.1f);
        return t;
    }

    private List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        List<AppItem> items = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            AppItem it = new AppItem();
            it.pkg = pkg;
            it.label = String.valueOf(ri.loadLabel(pm));
            it.icon = ri.loadIcon(pm);
            items.add(it);
        }
        final Collator col = Collator.getInstance(new Locale("vi", "VN"));
        Collections.sort(items, (a, b) -> col.compare(a.label, b.label));
        return items;
    }

    private void renderApps() {
        appList.removeAllViews();
        for (final AppItem it : loadApps()) {
            boolean sel = it.pkg.equals(selectedPkg);
            LinearLayout row = Ui.hbox(this);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setBackground(Ui.ripple(Ui.rounded(this, sel ? Ui.GREEN_LIGHT : 0x00FFFFFF, 12), 0x2200843D));

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(it.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

            LinearLayout texts = Ui.vbox(this);
            texts.addView(Ui.text(this, it.label, 15, Ui.INK, sel));
            texts.addView(Ui.text(this, it.pkg, 11, Ui.MUTED, false));
            LinearLayout.LayoutParams tp = Ui.lp(this, 0, WRAP, 12, 0, 8, 0);
            tp.weight = 1f;
            row.addView(texts, tp);

            TextView check = Ui.text(this, sel ? "✓" : "", 18, Ui.GREEN, true);
            check.setGravity(Gravity.CENTER);
            row.addView(check, new LinearLayout.LayoutParams(dp(24), WRAP));

            row.setOnClickListener(v -> {
                selectedPkg = it.pkg;
                selectedLabel = it.label;
                renderApps();
            });
            appList.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
    }

    private void save() {
        int drift;
        try {
            drift = Integer.parseInt(driftInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            drift = -1;
        }
        if (drift < 0 || drift > 60) {
            Toast.makeText(this, "Ngưỡng phải từ 0 đến 60 phút", Toast.LENGTH_SHORT).show();
            return;
        }
        String newPin = pinInput.getText().toString().trim();
        if (!newPin.isEmpty() && (newPin.length() < 4 || newPin.length() > 8)) {
            Toast.makeText(this, "Mã PIN gồm 4–8 chữ số", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedPkg == null || selectedPkg.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn app bán hàng", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setMaxDriftMinutes(drift);
        prefs.setTarget(selectedPkg, selectedLabel);
        if (!newPin.isEmpty()) prefs.setPin(newPin);
        Toast.makeText(this, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show();
        finish();
    }
}
