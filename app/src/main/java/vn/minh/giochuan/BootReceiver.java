package vn.minh.giochuan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bật lại dịch vụ canh giờ sau khi máy khởi động. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.startForegroundService(new Intent(context, TimeGuardService.class));
        }
    }
}
