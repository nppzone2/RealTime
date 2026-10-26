package vn.minh.giochuan;

import android.content.ContentResolver;
import android.provider.Settings;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

/**
 * Kiểm tra giờ máy:
 *  1) Ngày & giờ tự động + Múi giờ tự động phải bật
 *  2) Giờ máy không lệch quá ngưỡng so với giờ chuẩn internet (NTP, dự phòng HTTP)
 * Chạy trên background thread.
 */
public final class TimeChecker {

    public static final class Result {
        public boolean autoTime;
        public boolean autoZone;
        public boolean networkOk;
        /** Giờ chuẩn - giờ máy (ms). Dương = máy chạy chậm, âm = máy chạy nhanh. */
        public long offsetMs;
        public String source = "";

        public boolean autoOk() {
            return autoTime && autoZone;
        }

        /** Máy chạy chậm hơn giờ chuẩn (vượt sai số đo). */
        public boolean isBehind() {
            return offsetMs > BEHIND_GRACE_MS;
        }

        /**
         * Lệch một chiều: cho phép máy NHANH hơn giờ chuẩn tối đa maxAheadMs,
         * KHÔNG cho phép máy CHẬM hơn (chỉ chừa sai số đo vài giây).
         */
        public boolean driftOk(long maxAheadMs) {
            return networkOk && !isBehind() && -offsetMs <= maxAheadMs;
        }

        public boolean passed(long maxDriftMs) {
            return autoOk() && driftOk(maxDriftMs);
        }
    }

    /** Sai số đo qua mạng: máy chậm dưới mức này vẫn coi là đúng giờ. */
    public static final long BEHIND_GRACE_MS = 5_000L;

    private static final String[] NTP_HOSTS = {"time.google.com", "time.cloudflare.com", "pool.ntp.org"};
    private static final String[] HTTP_URLS = {"https://www.google.com/generate_204", "https://www.cloudflare.com/"};
    private static final long SEC_1900_TO_1970 = 2208988800L;

    private TimeChecker() {
    }

    public static Result check(ContentResolver cr) {
        Result r = new Result();
        r.autoTime = Settings.Global.getInt(cr, Settings.Global.AUTO_TIME, 0) == 1;
        r.autoZone = Settings.Global.getInt(cr, Settings.Global.AUTO_TIME_ZONE, 0) == 1;

        for (String host : NTP_HOSTS) {
            Long off = ntpOffset(host);
            if (off != null) {
                r.networkOk = true;
                r.offsetMs = off;
                r.source = host;
                return r;
            }
        }
        for (String url : HTTP_URLS) {
            Long off = httpOffset(url);
            if (off != null) {
                r.networkOk = true;
                r.offsetMs = off;
                r.source = url;
                return r;
            }
        }
        return r;
    }

    private static Long ntpOffset(String host) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(3000);
            InetAddress address = InetAddress.getByName(host);
            byte[] buf = new byte[48];
            buf[0] = 0x1B; // LI=0, VN=3, Mode=3 (client)
            long t0 = System.currentTimeMillis();
            writeTimestamp(buf, 40, t0);
            socket.send(new DatagramPacket(buf, buf.length, address, 123));
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
            long t3 = System.currentTimeMillis();

            int mode = buf[0] & 0x7;
            int stratum = buf[1] & 0xff;
            if (mode != 4 || stratum == 0 || stratum > 15) return null;

            long t1 = readTimestamp(buf, 32); // server nhận
            long t2 = readTimestamp(buf, 40); // server gửi
            if (t1 <= 0 || t2 <= 0) return null;
            return ((t1 - t0) + (t2 - t3)) / 2;
        } catch (Exception e) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    private static Long httpOffset(String url) {
        HttpURLConnection c = null;
        try {
            long t0 = System.currentTimeMillis();
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setUseCaches(false);
            c.setRequestMethod("HEAD");
            c.getResponseCode();
            long t3 = System.currentTimeMillis();
            long serverDate = c.getDate(); // header Date, độ chính xác 1 giây
            if (serverDate <= 0) return null;
            return serverDate + 500 + (t3 - t0) / 2 - t3;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static long read32(byte[] b, int o) {
        return ((long) (b[o] & 0xff) << 24) | ((long) (b[o + 1] & 0xff) << 16)
                | ((long) (b[o + 2] & 0xff) << 8) | (long) (b[o + 3] & 0xff);
    }

    private static long readTimestamp(byte[] b, int o) {
        long sec = read32(b, o);
        long frac = read32(b, o + 4);
        if (sec == 0 && frac == 0) return 0;
        return (sec - SEC_1900_TO_1970) * 1000L + (frac * 1000L) / 0x100000000L;
    }

    private static void writeTimestamp(byte[] b, int o, long ms) {
        long sec = ms / 1000L + SEC_1900_TO_1970;
        long frac = (ms % 1000L) * 0x100000000L / 1000L;
        b[o] = (byte) (sec >> 24);
        b[o + 1] = (byte) (sec >> 16);
        b[o + 2] = (byte) (sec >> 8);
        b[o + 3] = (byte) sec;
        b[o + 4] = (byte) (frac >> 24);
        b[o + 5] = (byte) (frac >> 16);
        b[o + 6] = (byte) (frac >> 8);
        b[o + 7] = (byte) frac;
    }
}
