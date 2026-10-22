# TỔNG HỢP TOÀN BỘ TÍNH NĂNG & HƯỚNG DẪN KIẾN TRÚC CODE
# DỰ ÁN APPSHAREPC (Android - Jetpack Compose - SMB - P2P)

> **Tài liệu lưu trữ chính thức** ghi lại toàn bộ yêu cầu tính năng từ người dùng, lịch sử phát triển qua các phiên bản, kiến trúc kỹ thuật và hướng dẫn lập trình chi tiết cho toàn bộ mã nguồn.

---

## 📑 MỤC LỤC
1. [Giới thiệu dự án](#1-giới-thiệu-dự-án)
2. [Toàn bộ tính năng đã yêu cầu (Matrix tính năng qua các phiên bản)](#2-toàn-bộ-tính-năng-đã-yêu-cầu)
   - [2.1. Quét tài liệu & Xuất tệp (v1.0.0 - v1.2.0)](#21-quét-tài-liệu--xuất-tệp)
   - [2.2. Kết nối đa mạng LAN / Tailscale & Đa hồ sơ PC (v1.3.0 - v1.6.0)](#22-kết-nối-đa-mạng-lan--tailscale--đa-hồ-sơ-pc)
   - [2.3. Truyền tệp 2 chiều & Đồng bộ In 3D (v1.7.0 - v1.9.0)](#23-truyền-tệp-2-chiều--đồng-bộ-in-3d)
   - [2.4. Sao lưu & Khôi phục cấu hình (v1.10.0)](#24-sao-lưu--khôi-phục-cấu-hình)
   - [2.5. Gửi liên kết (Link / URL) sang PC dạng .txt (v1.11.0)](#25-gửi-liên-kết-link--url-sang-pc-dạng-txt)
   - [2.6. Đẩy dữ liệu PC ➔ PC & Truyền trực tiếp ĐT ⇄ ĐT P2P (v1.12.0)](#26-đẩy-dữ-liệu-pc--pc--truyền-trực-tiếp-đt--đt-p2p)
   - [2.7. Nối giữa ĐT ⇄ ĐT (P2P Discovery, Dual-IP) & Tối ưu Máy tính bảng (v1.15.0)](#27-nối-giữa-đt--đt-p2p-discovery-dual-ip--tối-ưu-máy-tính-bảng-v1150)
   - [2.8. Bộ chọn thiết bị "+ Thêm mới" (PC vs ĐT) & Smart Detection (v1.16.0 MỚI)](#28-bộ-chọn-thiết-bị--thêm-mới-pc-vs-đt--smart-detection-v1160-mới)
3. [Kiến trúc kỹ thuật & Cấu trúc thư mục mã nguồn](#3-kiến-trúc-kỹ-thuật--cấu-trúc-thư-mục-mã-nguồn)
4. [Hướng dẫn chi tiết cách code từng module cốt lõi](#4-hướng-dẫn-chi-tiết-cách-code-từng-module-cốt-lõi)
   - [4.1. Module kết nối SMB và Chuyển dữ liệu PC ➔ PC (`SmbManager.kt`)](#41-module-kết-nối-smb-và-chuyển-dữ-liệu-pc--pc-smbmanagerkt)
   - [4.2. Module truyền dữ liệu ĐT ⇄ ĐT & Web Receiver (`P2PTransferManager.kt`)](#42-module-truyền-dữ-liệu-đt--đt--web-receiver-p2ptransfermanagerkt)
   - [4.3. Module lưu trữ MediaStore Scoped Storage (`FileUtils.kt`)](#43-module-lưu-trữ-mediastore-scoped-storage-fileutilskt)
   - [4.4. Module tạo file Word Docx và PDF sắc nét](#44-module-tạo-file-word-docx-và-pdf-sắc-nét)
   - [4.5. Module ViewModel điều phối nghiệp vụ (`MainViewModel.kt`)](#45-module-viewmodel-điều-phối-nghiệp-vụ-mainviewmodelkt)
   - [4.6. Giao diện Compose trực quan (UI Layer)](#46-giao-diện-compose-trực-quan-ui-layer)
5. [Quy trình đóng gói APK & Tự động đồng bộ hóa](#5-quy-trình-đóng-gói-apk--tự-động-đồng-bộ-hóa)

---

## 1. GIỚI THIỆU DỰ ÁN

* **Tên ứng dụng:** APPSharePC (Scan & Share to PC & P2P Transfer).
* **Nền tảng:** Android (Hỗ trợ từ Android 8.0 Oreo - API 26 đến Android 15 - API 35).
* **Ngôn ngữ:** Kotlin 2.0 (100% Kotlin Coroutines & Flow).
* **Giao diện (UI):** Jetpack Compose với chuẩn thiết kế Material 3.
* **Giao thức cốt lõi:**
  * **SMBv2 / SMBv3:** Kết nối Windows Network File Sharing qua thư viện mã nguồn mở `smbj`.
  * **Micro HTTP Server (Port 8999):** Máy chủ socket nhúng truyền nhận tệp P2P giữa các thiết bị di động và trình duyệt web.
  * **HTTP REST Daemon (Port 8888):** Kích hoạt tác vụ đồng bộ máy in 3D trên PC Windows.
  * **Tailscale WireGuard Mesh:** Kết nối an toàn xuyên mạng từ bất kỳ đâu.

---

## 2. TOÀN BỘ TÍNH NĂNG ĐÃ YÊU CẦU

### 2.1. Quét tài liệu & Xuất tệp
1. **Quét tài liệu liên tục nhiều trang (CameraX):**
   * Sử dụng thư viện `androidx.camera` chuẩn của Google.
   * Hỗ trợ bật/tắt đèn Flash, xem lại ảnh chụp trước (Preview), xóa từng ảnh hoặc xóa tất cả.
2. **Xuất ra 3 định dạng tài liệu chuyên nghiệp:**
   * **Word (.docx):** Gom nhiều ảnh chụp vào 1 tệp văn bản Microsoft Word chuẩn khổ A4, tự động căn chỉnh tỷ lệ trang.
   * **PDF (.pdf):** Ghép ảnh thành file tài liệu scan PDF độ phân giải cao, sắc nét.
   * **Ảnh đơn (.jpg):** Xuất trực tiếp file ảnh chất lượng gốc sang PC.
3. **Quy tắc đặt tên file tự động:**
   * Tự động sinh tên theo thời gian thực: `Scan_yyyyMMdd_HHmmss` hoặc cho phép người dùng nhập tên tùy thích.

### 2.2. Kết nối đa mạng LAN / Tailscale & Đa hồ sơ PC
1. **Cơ chế tự động dò tuyến (Dual-IP Auto Route Resolution):**
   * Người dùng nhập cả **IP LAN (WiFi)** (vd: `192.168.1.100`) và **IP Tailscale** (vd: `100.x.y.z`).
   * Ứng dụng tự động kiểm tra ping socket cổng 445: Nếu đang cùng mạng WiFi nội bộ thì truyền siêu tốc qua LAN; khi ra ngoài quán cafe/di động thì tự động chuyển sang đường truyền mã hóa Tailscale mà người dùng **không cần phải chỉnh lại cấu hình**.
2. **Quản lý đa hồ sơ máy tính (Profiles):**
   * Lưu trữ nhiều cấu hình PC khác nhau: `PC Cơ quan`, `PC Ở nhà`, `PC Xưởng in`...
   * Chuyển đổi qua lại giữa các máy tính chỉ với 1 chạm ngay trên thanh tiêu đề.
3. **Bảng kiểm tra sức khỏe kết nối đa điểm:**
   * Kiểm tra đồng thời: SMB Port 445 PC, Dịch vụ đồng bộ PC Port 8888, Máy in Snapmaker Moonraker và Bambu Lab (Bambuddy).

### 2.3. Truyền tệp 2 chiều & Đồng bộ In 3D
1. **Gửi tệp tin bất kỳ:**
   * Chọn tệp từ bộ nhớ máy (Word, Excel, Zip, Video, APK, CAD...) gửi thẳng sang thư mục chia sẻ PC.
2. **Gửi nguyên cây thư mục (Folder Upload):**
   * Tích hợp `ActivityResultContracts.OpenDocumentTree()` để chọn cả thư mục. Ứng dụng duyệt đệ quy và tải lên toàn bộ cây thư mục, giữ nguyên vị trí thư mục con trên PC.
3. **Duyệt tệp từ xa trên PC (Remote SMB Browser):**
   * Liệt kê danh sách file và thư mục con đang có trên máy tính Windows.
   * Nhấn vào thư mục con để xem bên trong, có nút quay lại thư mục cha.
4. **Tải file và thư mục từ PC về điện thoại:**
   * Tải tệp từ xa về lưu chuẩn vào thư mục `Bộ nhớ máy > Download > APPSharePC`.
   * Tương thích 100% cơ chế Scoped Storage của Android 10, 11, 12, 13, 14, 15 (MediaStore).
   * Tải nguyên thư mục: quét và kéo toàn bộ các file con về điện thoại.
5. **Kích hoạt đồng bộ in 3D:**
   * Nút bấm gọi HTTP GET tới `http://<PC_IP>:8888/sync` để máy tính tự động đồng bộ file gcode lên máy in 3D Snapmaker và Bambu.

### 2.4. Sao lưu & Khôi phục cấu hình
1. **Sao lưu toàn bộ cấu hình PC Profiles:**
   * Xuất danh sách máy tính sang chuỗi JSON có mã hóa kiểm tra cấu trúc.
2. **Nút [Lưu vào máy] trực tiếp (Fix triệt để lỗi file 0 B):**
   * Ghi file sao lưu thẳng vào `Download/APPSharePC/APPSharePC_Backup_yyyyMMdd_HHmmss.json` qua bộ điều phối MediaStore, đảm bảo có đầy đủ dữ liệu (không bao giờ bị rỗng 0 B như khi qua SAF trên một số dòng máy).
3. **Các tiện ích phụ trợ:**
   * Khôi phục nhanh bằng cách dán trực tiếp mã JSON từ bộ nhớ tạm.
   * Nút xem, copy và chia sẻ file cấu hình qua Zalo, Drive, Gmail...

### 2.5. Gửi liên kết (Link / URL) sang PC dạng .txt
1. **Tiếp nhận chia sẻ từ mọi ứng dụng (Android Share Sheet):**
   * Đăng ký `intent-filter` với `ACTION_SEND` (`text/plain`). Khi lướt web trên Chrome, xem YouTube, đọc tin Zalo... chỉ cần bấm **Chia sẻ ➔ APPSharePC**.
   * Ứng dụng tự động mở Tab "Gửi tệp & Link", điền sẵn link và sinh tên file: `Link_yyyyMMdd_HHmmss.txt`.
2. **Khung gửi link chuyên dụng:**
   * Ô nhập/dán URL, nút dán clipboard 1 chạm, nút làm mới tên file theo thời gian.
   * Nút **[GỬI LINK SANG PC (.TXT)]**: Stream trực tiếp nội dung văn bản sang thư mục chia sẻ PC thành file `.txt` (UTF-8), không tạo file rác trong điện thoại.

### 2.6. Đẩy dữ liệu PC ➔ PC & Truyền trực tiếp ĐT ⇄ ĐT P2P (v1.12.0 MỚI)
1. **Chuyển dữ liệu giữa 2 máy tính (PC Nguồn ➔ PC Đích):**
   * Người dùng chọn PC gửi (Source) và PC nhận (Target) từ danh sách hồ sơ PC.
   * Xem và chọn các tệp/thư mục trên PC nguồn.
   * Bấm **[CHUYỂN MỤC ĐÃ CHỌN SANG PC ĐÍCH]**: Điện thoại đóng vai trò cầu nối mạng (Network Bridge), đọc luồng từ PC1 và đẩy trực tiếp luồng ghi sang PC2 với bộ đệm Stream 256KB.
   * **Ưu điểm vượt trội:** Hoạt động hoàn toàn tự động giữa 2 máy tính qua mạng Tailscale/LAN mà **không tốn 1 byte dung lượng lưu trữ trên điện thoại**, và **không cần cài thêm bất kỳ phần mềm nào trên cả 2 máy tính**.
2. **Truyền trực tiếp giữa 2 điện thoại (ĐT ⇄ ĐT qua Tailscale / WiFi):**
   * **Chế độ Máy nhận (Receiver):**
     * Tích hợp máy chủ HTTP mini trên cổng `8999`.
     * Tự động nhận diện và hiển thị địa chỉ IP Tailscale (`http://100.x.y.z:8999`) và IP WiFi (`http://192.168.x.x:8999`).
     * Có giao diện Web HTML responsive tích hợp: Thiết bị khác (kể cả iPhone, iPad, laptop) chỉ cần mở trình duyệt gõ IP là có thể Kéo - Thả file hoặc gửi link sang điện thoại này.
     * Tự động lưu file vào `Download/APPSharePC/` và hiện lịch sử các file vừa nhận.
   * **Chế độ Máy gửi (Sender):**
     * Nhập IP của điện thoại nhận.
     * Chọn nhiều file hoặc dán link, bấm gửi trực tiếp qua HTTP POST Stream với thanh tiến trình thời gian thực.

### 2.7. Nối giữa ĐT ⇄ ĐT (P2P Discovery, Dual-IP) & Tối ưu Máy tính bảng (v1.15.0)
1. **Phần nối & Bảng điều khiển kết nối ĐT ⇄ ĐT:**
   * Bảng trạng thái kết nối chuyên dụng ngay đầu Tab P2P: Hiển thị ĐT đích, IP, huy hiệu tuyến mạng (WiFi LAN hoặc Tailscale) và đèn tín hiệu trạng thái (🟢 Đã nối / 🔴 Mất kết nối / 🟡 Đang kiểm tra).
   * **Nút [Thử kết nối ĐT]:** Ping kiểm tra cổng 8999 và gửi request `/ping`, đo độ trễ mạng thời gian thực (latency ms) và xác nhận thiết bị nhận đang mở APPSharePC.
   * **Nút [🔍 Quét tìm ĐT lân cận] (Auto-Discovery):** Tự động quét song song dải mạng WiFi cục bộ trên cổng 8999 để phát hiện các ĐT/Tablet khác đang mở APPSharePC. Hiện danh sách và nút **[NỐI NGAY]** chỉ với 1 chạm không cần nhập IP.
   * **Dual-IP cho ĐT (LAN IP & Tailscale IP):** Hỗ trợ lưu cả IP WiFi và IP Tailscale cho từng thiết bị di động, tự động chuyển tuyến tối ưu (ưu tiên WiFi tốc độ cao, ra ngoài chuyển Tailscale).
2. **Khắc phục triệt để lỗi mất nút Lưu trên Máy tính bảng (Tablet Responsive):**
   * **SmbSettingsDialog:** Bổ sung nút **[Lưu]** nổi bật ngay trên thanh **TopAppBar** bên phải. Người dùng máy tính bảng (dù xoay ngang, mở bàn phím ảo hay có taskbar hệ thống) luôn nhìn thấy và bấm được nút Lưu ở đầu màn hình.
   * Bổ sung `.imePadding().navigationBarsPadding()` và `decorFitsSystemWindows = false` giúp thanh điều khiển đáy tự động nổi lên trên bàn phím.
   * **PeerDeviceDialog:** Bổ sung `.verticalScroll(rememberScrollState())`, `.imePadding()` và nút Lưu trên header dialog, giải quyết dứt điểm lỗi tràn viền che mất nút Lưu trên màn hình ngang của tablet.

### 2.8. Bộ chọn thiết bị "+ Thêm mới" (PC vs ĐT) & Fix Hiển thị Dialog ĐT (v1.16.0 - v1.16.1 MỚI)
1. **Hộp thoại phân loại thiết bị `AddDeviceChoiceDialog`:**
   * Khi nhấn chip **[+ Thêm mới]** trên thanh thiết bị hoặc từ menu 3 chấm, ứng dụng hiển thị hộp thoại lựa chọn trực quan:
     * 🖥️ **Máy tính PC (Windows SMB):** Thư mục chia sẻ qua LAN hoặc Tailscale (Cổng 445). Yêu cầu chỉ định thư mục chia sẻ Windows (VD: `D:\ScanFiles`). Chọn mục này sẽ mở hộp thoại cài đặt PC (`SmbSettingsDialog`).
     * 📱 **Điện thoại / Máy tính bảng (P2P):** Kết nối trực tiếp giữa 2 thiết bị di động cài APPSharePC (Cổng 8999). **Không cần thư mục chia sẻ Windows!** Chọn mục này sẽ tự động chuyển sang tab "Chuyển P2P" và mở ngay hộp thoại thêm thiết bị ĐT (`PeerDeviceDialog`).
2. **Khắc phục triệt để lỗi "nhấp vào ĐT không hiện gì cả" (v1.16.1):**
   * **Nâng `PeerDeviceDialog` lên tầng gốc (`HomeScreen.kt`):** Trước đây dialog nằm cục bộ trong `P2PTransferTab`. Khi người dùng ở Tab khác bấm thêm ĐT, dialog không được kích hoạt ngay lập tức. Nay dialog được quản lý toàn cục tại `HomeScreen`, sẵn sàng xuất hiện từ bất kỳ màn hình nào.
   * **Sửa lỗi co kích thước về 0 (Zero Constraint Collapse) của Compose Dialog:** Khi dùng `DialogProperties(usePlatformDefaultWidth = false)`, nếu không có `Box(Modifier.fillMaxSize())` bao ngoài thì thẻ `Card(Modifier.fillMaxWidth())` bị tính toán thành chiều rộng 0 pixel khiến hộp thoại biến mất. Đã bọc `Box(Modifier.fillMaxSize().imePadding())` chuẩn xác, giúp dialog hiển thị sắc nét, căn giữa và tự động đẩy lên khi bàn phím ảo xuất hiện.
   * **Loại bỏ xung đột Touch Event trong `DeviceTypeOptionCard`:** Chuyển toàn bộ các thành phần nhãn con từ `Surface` sang `Box`, giúp toàn bộ bề mặt thẻ ăn cảm ứng 100% không bị nuốt click.
3. **Banner nhắc nhở phân loại trong từng màn hình cấu hình:**
   * Trong `SmbSettingsDialog`: Hiển thị banner thông báo màn hình này dành riêng cho Windows SMB cổng 445; nếu nối ĐT/Tablet thì dùng tính năng Chuyển P2P.
   * Trong `PeerDeviceDialog`: Hiển thị banner giải thích kết nối P2P cổng 8999 trực tiếp không cần chia sẻ thư mục Windows.
4. **Phát hiện thông minh (Smart Detection) chống cấu hình nhầm:**
   * Trong `SmbManager.kt`, nếu kiểm tra cổng 445 hoặc kết nối thư mục thất bại, ứng dụng tự động kiểm tra cổng 8999. Nếu phát hiện cổng 8999 đang mở (thiết bị là điện thoại chạy APPSharePC), ứng dụng thông báo rõ ràng cho người dùng chuyển sang tab "Chuyển P2P" thay vì báo lỗi mơ hồ.

---

## 3. KIẾN TRÚC KỸ THUẬT & CẤU TRÚC THƯ MỤC

Ứng dụng được tổ chức theo kiến trúc **MVVM (Model-View-ViewModel)** chuẩn của Google Android hiện đại:

```
app/src/main/java/com/appsharepc/scanner/
├── data/                               # Tầng Dữ liệu & Lưu trữ cấu hình
│   ├── SmbSettings.kt                  # Model cấu hình kết nối PC (IP, Share, User, Pass...)
│   ├── PcProfile.kt                    # Model quản lý nhiều máy tính (ID, Name, Settings)
│   └── SmbSettingsRepository.kt        # Repository đọc/ghi cấu hình SharedPreferences & JSON
│
├── service/                            # Tầng Xử lý dịch vụ & Mạng (Network & IO)
│   ├── SmbManager.kt                   # Bộ máy SMB Client (SMB2/3, Upload, Download, PC->PC)
│   ├── P2PTransferManager.kt           # Bộ máy P2P HTTP Server & HTTP Client (ĐT <-> ĐT)
│   ├── FileUtils.kt                    # Xử lý tệp, MediaStore Scoped Storage, SAF Folder
│   ├── ImageUtils.kt                   # Tối ưu hóa ảnh chụp CameraX (Rotate, Compress)
│   ├── DocxExporter.kt                 # Trình tạo file Microsoft Word (.docx) chuẩn A4
│   ├── PdfExporter.kt                  # Trình tạo file PDF sắc nét bằng Android PdfDocument
│   └── JpgExporter.kt                  # Trình xuất file ảnh đơn JPG chất lượng cao
│
├── viewmodel/                          # Tầng Điều phối nghiệp vụ (ViewModel Layer)
│   └── MainViewModel.kt                # Quản lý toàn bộ StateFlow của 4 Tab & logic chạy nền
│
├── ui/                                 # Tầng Giao diện Jetpack Compose (Presentation Layer)
│   ├── components/
│   │   ├── ConnectionStatusCard.kt     # Thẻ hiển thị trạng thái kết nối & Chọn nhanh Profile
│   │   ├── AddDeviceChoiceDialog.kt    # Hộp thoại chọn loại thiết bị mới: PC (SMB) vs ĐT (P2P)
│   │   ├── PeerDeviceDialog.kt         # Hộp thoại thêm/sửa thiết bị ĐT P2P (Dual-IP & Test kết nối)
│   │   ├── FileTransferTab.kt          # Tab 1: Gửi tệp bất kỳ, gửi Folder & Gửi Link sang PC
│   │   ├── RemotePcTab.kt              # Tab 2: Duyệt file PC từ xa & Tải file/folder về máy
│   │   ├── P2PTransferTab.kt           # Tab 3: Chuyển dữ liệu PC ➔ PC và ĐT ⇄ ĐT
│   │   ├── FullscreenImageViewer.kt    # Dialog xem ảnh scan toàn màn hình
│   │   └── ImageThumbnailRow.kt        # Hàng hiển thị danh sách ảnh vừa chụp
│   ├── screens/
│   │   ├── HomeScreen.kt               # Màn hình chính điều phối 4 Tab chính
│   │   ├── CameraCaptureScreen.kt      # Màn hình chụp ảnh tài liệu chuyên nghiệp
│   │   └── SmbSettingsDialog.kt        # Hộp thoại Cài đặt IP, Thư mục & Backup/Restore
│   └── theme/
│       ├── Color.kt, Theme.kt, Type.kt # Bảng màu Material 3 & Font chữ
│
└── MainActivity.kt                     # Entry point duy nhất, xử lý quyền & Intent Share Sheet
```

---

## 4. HƯỚNG DẪN CHI TIẾT CÁCH CODE TỪNG MODULE CỐT LÕI

### 4.1. Module kết nối SMB và Chuyển dữ liệu PC ➔ PC (`SmbManager.kt`)

#### A. Cách thức hoạt động của SMBJ:
Thư viện `com.hierynomus:smbj:0.13.0` hỗ trợ giao thức SMB 2.0, 2.1 và 3.0.
1. `SMBClient(smbConfig)` khởi tạo client.
2. `client.connect(targetIp, port)` tạo kết nối Socket TCP cấp giao thức.
3. `connection.authenticate(authContext)` xác thực tên người dùng và mật khẩu (hoặc Guest).
4. `session.connectShare(shareName)` gắn kết vào thư mục chia sẻ trên Windows (DiskShare).

#### B. Cách code chuyển dữ liệu trực tiếp PC ➔ PC (`transferBetweenPcs`):
```kotlin
suspend fun transferBetweenPcs(
    sourceSettings: SmbSettings,
    targetSettings: SmbSettings,
    itemsToTransfer: List<RemoteSmbItem>,
    targetSubFolder: String = "",
    sourceName: String = "PC Nguồn",
    targetName: String = "PC Đích",
    onProgress: ((currentFile: String, currentIndex: Int, totalFiles: Int, percent: Int) -> Unit)? = null
): SmbUploadResult = withContext(Dispatchers.IO) {
    // 1. Kết nối đồng thời tới PC nguồn và PC đích
    val resolvedSource = resolveEffectiveTarget(sourceSettings)
    val resolvedTarget = resolveEffectiveTarget(targetSettings)

    val clientSource = SMBClient(createSmbConfig())
    val clientTarget = SMBClient(createSmbConfig())

    val sourceConn = clientSource.connect(resolvedSource.settings.pcIp.trim(), resolvedSource.settings.port)
    val sourceSession = sourceConn.authenticate(createAuthContext(resolvedSource.settings))
    val sourceShare = sourceSession.connectShare(resolvedSource.settings.shareName) as DiskShare

    val targetConn = clientTarget.connect(resolvedTarget.settings.pcIp.trim(), resolvedTarget.settings.port)
    val targetSession = targetConn.authenticate(createAuthContext(resolvedTarget.settings))
    val targetShare = targetSession.connectShare(resolvedTarget.settings.shareName) as DiskShare

    // 2. Mở Stream đọc từ file nguồn và Stream ghi vào file đích
    val buffer = ByteArray(256 * 1024) // Đệm 256KB tối ưu mạng
    for ((index, item) in itemsToTransfer.withIndex()) {
        val sourceFile = sourceShare.openFile(
            item.relativePath,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        val targetFile = targetShare.openFile(
            item.relativePath,
            setOf(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )

        // 3. Pipe trực tiếp dữ liệu giữa 2 luồng: Source -> Phone RAM Buffer -> Target
        sourceFile.inputStream.use { input ->
            targetFile.outputStream.use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
        sourceFile.close()
        targetFile.close()
    }
}
```

---

### 4.2. Module truyền dữ liệu ĐT ⇄ ĐT & Web Receiver (`P2PTransferManager.kt`)

#### A. Kiến trúc máy chủ Socket HTTP Receiver:
Không cần cài đặt thư viện server cồng kềnh, sử dụng `java.net.ServerSocket` thuần:
* Chạy ngầm trong Coroutine `Dispatchers.IO` trên cổng `8999`.
* Phân tích các yêu cầu HTTP cơ bản:
  * `GET /`: Trả về mã nguồn HTML giao diện Web UI hỗ trợ Kéo - Thả file cho máy khác.
  * `POST /upload`: Nhận file stream, đọc header `X-File-Name` và ghi trực tiếp vào `Download/APPSharePC/` qua MediaStore.
  * `POST /link`: Nhận liên kết text và ghi thành file `.txt`.

#### B. Cách gửi tệp từ điện thoại máy gửi (`sendFilesToPeer`):
Sử dụng `HttpURLConnection` với chế độ `setFixedLengthStreamingMode`:
```kotlin
val url = URL("http://$targetIp:$port/upload")
val conn = (url.openConnection() as HttpURLConnection).apply {
    requestMethod = "POST"
    doOutput = true
    setRequestProperty("X-File-Name", URLEncoder.encode(item.name, "UTF-8"))
    setFixedLengthStreamingMode(item.size) // Tránh nạp toàn bộ file vào RAM
}
val buffer = ByteArray(64 * 1024)
conn.outputStream.use { out ->
    item.openInputStream().use { input ->
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
    }
}
```

---

### 4.3. Module lưu trữ MediaStore Scoped Storage (`FileUtils.kt`)

Trên Android 10+ (API 29 trở lên), ứng dụng không được phép ghi tự do vào đường dẫn `File("/sdcard/...")`. Cần sử dụng `MediaStore.Downloads`:
```kotlin
fun openDownloadOutputStream(context: Context, relativeFilePath: String): Pair<OutputStream, Uri?> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/APPSharePC")
            put(MediaStore.MediaColumns.IS_PENDING, 1) // Đánh dấu đang ghi
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        val stream = context.contentResolver.openOutputStream(uri!!)!!
        return Pair(stream, uri)
    } else {
        // Android 8 - 9 cũ: ghi File trực tiếp
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "APPSharePC/$fileName")
        target.parentFile?.mkdirs()
        return Pair(FileOutputStream(target), Uri.fromFile(target))
    }
}

// Khi ghi xong:
fun finishDownload(context: Context, uri: Uri?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0) // Mở khóa cho người dùng xem
        }
        context.contentResolver.update(uri, values, null, null)
    }
}
```

---

### 4.4. Module tạo file Word Docx và PDF sắc nét

1. **Word (.docx):**
   * Định dạng `.docx` thực chất là một tệp ZIP chứa cấu trúc thư mục XML chuẩn OpenXML (`[Content_Types].xml`, `word/document.xml`, `word/media/image1.jpeg`).
   * `DocxExporter.kt` tạo luồng `ZipOutputStream`, viết khung tài liệu XML định nghĩa khổ trang A4 (`11906 x 16838 dxa`), chèn từng ảnh vào thư mục `word/media/` và sinh các thẻ `<w:drawing>` tương ứng.
2. **PDF (.pdf):**
   * Sử dụng `android.graphics.pdf.PdfDocument`.
   * Khởi tạo trang với `PageInfo.Builder(595, 842, pageIndex)` (chuẩn A4 ở độ phân giải 72 DPI).
   * Lấy `Canvas` của trang, vẽ ảnh bitmap với thuật toán `matrix.setRectToRect(..., ScaleToFit.CENTER)` để ảnh luôn vừa khít trang và không bị méo tỷ lệ.

---

### 4.5. Module ViewModel điều phối nghiệp vụ (`MainViewModel.kt`)

* Sử dụng `MutableStateFlow` để duy trì trạng thái Reactive cho toàn bộ ứng dụng.
* Quản lý trạng thái 4 Tab:
  * Tab 0: Quét ảnh & xuất Docx/PDF/Jpg.
  * Tab 1: Gửi tệp tin bất kỳ, gửi Folder & gửi Link sang PC.
  * Tab 2: Quản lý & tải tệp từ xa trên PC.
  * Tab 3: Chuyển dữ liệu PC ➔ PC và P2P ĐT ⇄ ĐT.
* Đảm bảo mọi tác vụ mạng/I-O đều nằm trong `viewModelScope.launch(Dispatchers.IO)` để UI Compose luôn mượt mà 60/120 FPS không bao giờ bị giật lag.
* Giải phóng server Socket trong `onCleared()` để tránh rò rỉ tài nguyên mạng.

---

### 4.6. Giao diện Compose trực quan (UI Layer)

* **Navigation:** Sử dụng `TabRow` chuẩn Material 3 chuyển đổi linh hoạt.
* **Pull-to-refresh:** Tích hợp `PullToRefreshBox` cho phép người dùng vuốt xuống để quét lại kết nối hoặc làm mới danh sách tệp trên PC.
* **Phản hồi người dùng (Feedback):** Thanh tiến trình `LinearProgressIndicator` kết hợp văn bản trạng thái hiển thị rõ tên tệp đang chuyển, phần trăm và dung lượng đã gửi.

---

## 5. QUY TRÌNH ĐÓNG GÓI APK & TỰ ĐỘNG ĐỒNG BỘ HÓA

### ⚠️ Quy tắc an toàn bắt buộc:
* **Tuyệt đối không tự ý chạy lệnh build APK (`assembleDebug`)** khi chưa có sự xác nhận rõ ràng từ người dùng.
* Luôn kiểm tra tính toàn vẹn của mã nguồn bằng lệnh biên dịch thử nghiệm:
  ```bash
  gradlew.bat compileDebugKotlin
  ```

### Cơ chế tự động sao chép file APK sau khi đóng gói:
Trong file `app/build.gradle.kts`, hook Gradle `assembleProvider` đã được cấu hình tự động:
Khi lệnh `gradlew.bat assembleDebug` hoàn thành:
1. File APK được tạo tại: `app/build/outputs/apk/debug/APPSharePC-v<version>-debug.apk`.
2. Tự động sao chép ra thư mục gốc: `D:\APPSharePC\APPSharePC-v<version>-debug.apk`.
3. **Luôn tự động sao chép sang thư mục:** `D:\Apps\Filein\InPGH\PDFs\APPSharePC-v<version>-debug.apk`.
4. Sao chép bổ sung vào thư mục Dropbox (nếu có): `D:\Dropbox\Filein\InPGH\PDFs\APPSharePC-v<version>-debug.apk`.
5. Cập nhật file shortcut tiện ích `D:\APPSharePC\Mo_Thu_Muc_Chua_APK.bat` để người dùng chỉ cần nhấp đúp là Windows Explorer tự động mở và bôi đen sẵn file APK mới nhất.

---
*Tài liệu được cập nhật hoàn tất tại phiên bản v1.15.0.*
