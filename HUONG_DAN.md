# RealTime – Hướng dẫn build & cài đặt

App Android làm cổng vào trước app **glassRUN Delivery Management** (com.disrptiv.glassrun.deliverymanagement): giờ máy phải đúng mới mở được glassRUN.

## App kiểm tra gì
1. **Ngày & giờ tự động** và **Múi giờ tự động** phải đang bật.
2. **Lệch một chiều** so với giờ chuẩn internet (máy chủ NTP của Google/Cloudflare):
   - Máy chạy **nhanh** hơn giờ chuẩn: cho phép tối đa 2 phút (chỉnh được).
   - Máy chạy **chậm** hơn giờ chuẩn: **không cho phép** (chỉ chừa 5 giây sai số đo qua mạng).
3. Không có mạng → chưa đối chiếu được → vẫn khoá, yêu cầu bật 4G/Wi-Fi.

App kiểm tra lại khi mở app, khi quay lại từ phần Cài đặt, khi giờ máy vừa bị đổi, cứ 60 giây một lần, và ngay trước khi mở app bán hàng.

## Bước 1 – Build file APK trên GitHub (miễn phí, không cần cài gì)
1. Tạo tài khoản tại github.com (nếu chưa có) → **New repository** → đặt tên `RealTime` → **Create**.
2. Giải nén file zip. Trong repo vừa tạo, chọn **uploading an existing file**, kéo thả **toàn bộ nội dung bên trong** thư mục `RealTime` (gồm `app`, `build.gradle`, `settings.gradle`, `gradle.properties`, ...) → **Commit changes**.
3. Tạo file workflow (GitHub thường bỏ qua thư mục ẩn `.github` khi kéo thả):
   **Add file → Create new file** → gõ tên `.github/workflows/build-apk.yml` → dán nội dung file `build-apk.yml` (có trong zip, thư mục `.github/workflows`) → **Commit changes**.
4. Mở tab **Actions**, chờ khoảng 3–5 phút đến khi có dấu ✓ xanh.
5. Mở mục **Releases** (cột phải trang repo) → tải `RealTime.apk`.
   Repo để **Public** thì link Releases gửi thẳng cho DCR tải được; để **Private** thì bạn tải về rồi gửi qua Zalo.

Mỗi lần sửa code và commit, GitHub tự build bản mới. Các bản đều ký cùng một khoá (`app/giochuan.jks`) nên cài đè lên bản cũ được, không mất cài đặt.

## Bước 2 – Cài lên máy DCR
1. Mở file `RealTime.apk` → cho phép **Cài ứng dụng không rõ nguồn gốc** nếu máy hỏi → **Cài đặt**.
2. Nếu Play Protect cảnh báo: chọn **Vẫn cài đặt** (app nội bộ, không phát hành trên CH Play).

## Bước 3 – Cài đặt quản trị (làm 1 lần mỗi máy)
1. Mở app → **nhấn giữ vùng màu xanh phía trên** (hoặc nút *Chọn app bán hàng*).
2. Nhập PIN mặc định **2026**.
3. App đã chọn sẵn **glassRUN**. Chỉ cần chỉnh mức cho phép chạy nhanh nếu muốn, **đổi PIN mới** → **Lưu cài đặt**. Máy chưa cài glassRUN thì app tự mở CH Play để cài.
4. Kéo icon **RealTime** ra màn hình chính, thay vị trí icon glassRUN.

## Lưu ý
- Android không cho một app khoá hẳn app khác. "RealTime" là cổng vào: DCR vẫn có thể mở glassRUN trực tiếp nếu cố tình. Để chặn triệt để, nên kết hợp: ẩn icon glassRUN khỏi màn hình chính, quản lý kiểm tra định kỳ, hoặc dùng phần mềm quản lý thiết bị (MDM) của công ty.
- Muốn đổi PIN, mức nhanh tối đa, app đích mặc định: sửa `Prefs.java` (DEFAULT_PIN, DEFAULT_MAX_DRIFT_MIN, DEFAULT_TARGET_PKG). Sai số cho phép chậm: `BEHIND_GRACE_MS` trong `TimeChecker.java`.

Developed by Ta Thi Thu Minh
