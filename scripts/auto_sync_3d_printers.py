import os
import sys
import time
import json
import datetime
import threading
import urllib.parse
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
import warnings
warnings.filterwarnings("ignore")

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

import requests

SNAP_DIR = r"D:\Dropbox\Filein\InPGH\PDFs\Snap"
BAMBU_DIR = r"D:\Dropbox\Filein\InPGH\PDFs\Bambu"

SNAPMAKER_URL = "http://snapmaker.taileba178.ts.net"
BAMBUDDY_URL = "http://tvbox.taileba178.ts.net:8000"

HTTP_PORT = 8888
CHECK_INTERVAL_SECONDS = 5

sync_lock = threading.Lock()

def log(tag: str, message: str):
    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{now_str}] [{tag}] {message}", flush=True)

def is_valid_file(file_name: str) -> bool:
    if file_name.startswith(".") or file_name.startswith("~"):
        return False
    lower = file_name.lower()
    if lower in ("desktop.ini", "thumbs.db"):
        return False
    if lower.endswith(".tmp") or lower.endswith(".crdownload") or lower.endswith(".part"):
        return False
    return True

def sync_snapmaker(target_file_name: str = None) -> dict:
    if not os.path.exists(SNAP_DIR):
        try:
            os.makedirs(SNAP_DIR, exist_ok=True)
        except Exception:
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": []}

    try:
        check_resp = requests.get(f"{SNAPMAKER_URL}/server/info", timeout=4)
        if check_resp.status_code != 200:
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": ["Máy in Snapmaker không phản hồi."]}
    except Exception as e:
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": [f"Lỗi kết nối Snapmaker: {e}"]}

    try:
        list_resp = requests.get(f"{SNAPMAKER_URL}/server/files/list?root=gcodes", timeout=8)
        if list_resp.status_code != 200:
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": ["Lỗi đọc danh sách gcode."]}
        remote_data = list_resp.json().get("result", [])
        existing_remote_files = {item.get("path") for item in remote_data if item.get("path")}
    except Exception as e:
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": [f"Lỗi Moonraker: {e}"]}

    try:
        all_files = os.listdir(SNAP_DIR)
    except Exception:
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": True, "details": []}

    if target_file_name:
        files_to_check = [target_file_name] if target_file_name in all_files else []
    else:
        files_to_check = [f for f in all_files if is_valid_file(f) and os.path.isfile(os.path.join(SNAP_DIR, f))]

    uploaded = 0
    skipped = 0
    failed = 0
    details = []

    for file_name in files_to_check:
        if file_name in existing_remote_files:
            skipped += 1
            details.append(f"Đã có trên máy in: {file_name}")
            continue

        file_path = os.path.join(SNAP_DIR, file_name)
        if not os.path.isfile(file_path):
            continue

        file_size_mb = os.path.getsize(file_path) / (1024 * 1024)
        log("Snapmaker", f"Đang đồng bộ file '{file_name}' ({file_size_mb:.2f} MB) lên máy in Snapmaker...")

        try:
            with open(file_path, "rb") as f:
                files = {"file": (file_name, f, "application/octet-stream")}
                data = {"root": "gcodes", "print": "false"}
                upload_resp = requests.post(f"{SNAPMAKER_URL}/server/files/upload", files=files, data=data, timeout=180)

            if upload_resp.status_code in (200, 201):
                log("Snapmaker", f"Đã đẩy thành công '{file_name}' lên máy in!")
                existing_remote_files.add(file_name)
                uploaded += 1
                details.append(f"Thành công: {file_name}")
            else:
                log("Snapmaker", f"Thất bại khi đẩy '{file_name}': HTTP {upload_resp.status_code}")
                failed += 1
                details.append(f"Lỗi: {file_name} (HTTP {upload_resp.status_code})")
        except Exception as ex:
            log("Snapmaker", f"Lỗi ngoại lệ file '{file_name}': {ex}")
            failed += 1
            details.append(f"Lỗi: {file_name} ({ex})")

    return {"uploaded": uploaded, "skipped": skipped, "failed": failed, "online": True, "details": details}

def sync_bambuddy(target_file_name: str = None) -> dict:
    if not os.path.exists(BAMBU_DIR):
        try:
            os.makedirs(BAMBU_DIR, exist_ok=True)
        except Exception:
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": []}

    try:
        check_resp = requests.get(f"{BAMBUDDY_URL}/docs", timeout=4)
        if check_resp.status_code != 200:
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": ["Bambuddy TVBox không phản hồi."]}
    except Exception as e:
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": [f"Lỗi kết nối Bambuddy: {e}"]}

    try:
        list_resp = requests.get(f"{BAMBUDDY_URL}/api/v1/library/files", timeout=10)
        if list_resp.status_code != 200:
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": ["Lỗi lấy thư viện Bambuddy."]}
        remote_data = list_resp.json()
        existing_remote_names = {item.get("filename") for item in remote_data if isinstance(item, dict) and item.get("filename")}
    except Exception as e:
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False, "details": [f"Lỗi thư viện Bambuddy: {e}"]}

    try:
        all_files = os.listdir(BAMBU_DIR)
    except Exception:
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": True, "details": []}

    if target_file_name:
        files_to_check = [target_file_name] if target_file_name in all_files else []
    else:
        files_to_check = [f for f in all_files if is_valid_file(f) and os.path.isfile(os.path.join(BAMBU_DIR, f))]

    uploaded = 0
    skipped = 0
    failed = 0
    details = []

    for file_name in files_to_check:
        if file_name in existing_remote_names:
            skipped += 1
            details.append(f"Đã có trong Bambuddy: {file_name}")
            continue

        file_path = os.path.join(BAMBU_DIR, file_name)
        if not os.path.isfile(file_path):
            continue

        file_size_mb = os.path.getsize(file_path) / (1024 * 1024)
        log("Bambuddy", f"Đang đồng bộ file '{file_name}' ({file_size_mb:.2f} MB) lên Bambuddy TVBox...")

        try:
            with open(file_path, "rb") as f:
                files = {"file": (file_name, f, "application/octet-stream")}
                upload_resp = requests.post(f"{BAMBUDDY_URL}/api/v1/library/files", files=files, timeout=180)

            if upload_resp.status_code in (200, 201):
                log("Bambuddy", f"Đã đẩy thành công '{file_name}' lên thư viện Bambuddy!")
                existing_remote_names.add(file_name)
                uploaded += 1
                details.append(f"Thành công: {file_name}")
            else:
                log("Bambuddy", f"Thất bại khi đẩy '{file_name}': HTTP {upload_resp.status_code}")
                failed += 1
                details.append(f"Lỗi: {file_name} (HTTP {upload_resp.status_code})")
        except Exception as ex:
            log("Bambuddy", f"Lỗi ngoại lệ file '{file_name}': {ex}")
            failed += 1
            details.append(f"Lỗi: {file_name} ({ex})")

    return {"uploaded": uploaded, "skipped": skipped, "failed": failed, "online": True, "details": details}

class SyncRequestHandler(BaseHTTPRequestHandler):
    def _send_json(self, status_code: int, data: dict):
        response_bytes = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()
        self.wfile.write(response_bytes)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_GET(self):
        self._handle_request()

    def do_POST(self):
        self._handle_request()

    def _handle_request(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/")
        query = urllib.parse.parse_qs(parsed.query)

        if path in ("", "/status", "/status/all", "/check"):
            snap_online = False
            bambu_online = False
            try:
                r1 = requests.get(f"{SNAPMAKER_URL}/server/info", timeout=2)
                snap_online = (r1.status_code == 200)
            except Exception:
                try:
                    r1 = requests.get(SNAPMAKER_URL, timeout=2)
                    snap_online = (r1.status_code == 200)
                except Exception:
                    pass

            try:
                r2 = requests.get(f"{BAMBUDDY_URL}/docs", timeout=2)
                bambu_online = (r2.status_code == 200)
            except Exception:
                pass

            self._send_json(200, {
                "status": "online",
                "service": "APPSharePC 3D Sync Server",
                "pc_sync_online": True,
                "snapmaker_online": snap_online,
                "bambuddy_online": bambu_online,
                "snapmaker_url": SNAPMAKER_URL,
                "bambuddy_url": BAMBUDDY_URL,
                "snap_dir": SNAP_DIR,
                "bambu_dir": BAMBU_DIR
            })
            return

        with sync_lock:
            if path in ("/sync", "/sync/all"):
                log("API", "Nhận lệnh đồng bộ toàn bộ từ App điện thoại...")
                snap_res = sync_snapmaker()
                bambu_res = sync_bambuddy()
                msg = (f"Snapmaker: {snap_res['uploaded']} mới, {snap_res['skipped']} đã có | "
                       f"Bambuddy: {bambu_res['uploaded']} mới, {bambu_res['skipped']} đã có")
                self._send_json(200, {
                    "success": True,
                    "message": msg,
                    "snapmaker": snap_res,
                    "bambuddy": bambu_res
                })
                return

            elif path == "/sync/snap":
                log("API", "Nhận lệnh đồng bộ thư mục Snap lên Snapmaker U1...")
                snap_res = sync_snapmaker()
                msg = f"Snapmaker U1: Đã đẩy {snap_res['uploaded']} file mới, bỏ qua {snap_res['skipped']} file đã có."
                self._send_json(200, {
                    "success": True,
                    "message": msg,
                    "result": snap_res
                })
                return

            elif path == "/sync/bambu":
                log("API", "Nhận lệnh đồng bộ thư mục Bambu lên Bambuddy TVBox...")
                bambu_res = sync_bambuddy()
                msg = f"Bambuddy TVBox: Đã đẩy {bambu_res['uploaded']} file mới, bỏ qua {bambu_res['skipped']} file đã có."
                self._send_json(200, {
                    "success": True,
                    "message": msg,
                    "result": bambu_res
                })
                return

            elif path == "/sync/file":
                target = query.get("target", [""])[0].lower()
                name = query.get("name", [""])[0]
                if not name:
                    self._send_json(400, {"success": False, "message": "Thiếu tham số tên file 'name'"})
                    return

                if target in ("snap", "snapmaker"):
                    log("API", f"Nhận lệnh đẩy file '{name}' lên Snapmaker U1...")
                    res = sync_snapmaker(target_file_name=name)
                    success = res["uploaded"] > 0 or res["skipped"] > 0
                    msg = f"Snapmaker U1: {'Đã đẩy thành công' if res['uploaded'] > 0 else 'File đã có sẵn trên máy in'} ({name})"
                    self._send_json(200, {"success": success, "message": msg, "result": res})
                    return
                elif target in ("bambu", "bambuddy"):
                    log("API", f"Nhận lệnh đẩy file '{name}' lên Bambuddy TVBox...")
                    res = sync_bambuddy(target_file_name=name)
                    success = res["uploaded"] > 0 or res["skipped"] > 0
                    msg = f"Bambuddy TVBox: {'Đã đẩy thành công' if res['uploaded'] > 0 else 'File đã có sẵn trong thư viện'} ({name})"
                    self._send_json(200, {"success": success, "message": msg, "result": res})
                    return
                else:
                    self._send_json(400, {"success": False, "message": "Target không hợp lệ (hỗ trợ 'snap' hoặc 'bambu')"})
                    return

            else:
                self._send_json(404, {"success": False, "message": f"Endpoint không tồn tại: {path}"})

    def log_message(self, format, *args):
        pass  # Tắt log mặc định của HTTP server để console gọn gàng

def run_http_server():
    server_address = ("0.0.0.0", HTTP_PORT)
    try:
        # Đa luồng: lệnh /sync chạy lâu không làm treo /status (App sẽ báo nhầm "dịch vụ không chạy")
        httpd = ThreadingHTTPServer(server_address, SyncRequestHandler)
        log("Server", f"API Listener đang lắng nghe tại cổng {HTTP_PORT} (hỗ trợ lệnh từ App điện thoại)...")
        httpd.serve_forever()
    except Exception as e:
        log("Server", f"Không thể khởi động HTTP Server trên cổng {HTTP_PORT}: {e}")

def run_background_watcher():
    while True:
        with sync_lock:
            try:
                sync_snapmaker()
            except Exception as e:
                log("Watcher", f"Lỗi vòng lặp Snapmaker: {e}")

            try:
                sync_bambuddy()
            except Exception as e:
                log("Watcher", f"Lỗi vòng lặp Bambuddy: {e}")

        time.sleep(CHECK_INTERVAL_SECONDS)

def main():
    print("=" * 60)
    print("[HE THONG TU DONG DONG BO FILE IN 3D: PC -> SNAPMAKER & BAMBUDDY]")
    print(f"- Thu muc Snapmaker: {SNAP_DIR}")
    print(f"- Thu muc Bambuddy:  {BAMBU_DIR}")
    print(f"- May in Snapmaker:  {SNAPMAKER_URL}")
    print(f"- May chu Bambuddy:  {BAMBUDDY_URL}")
    print(f"- Cong nhan lenh:    http://0.0.0.0:{HTTP_PORT}")
    print(f"- Chu ky kiem tra:   {CHECK_INTERVAL_SECONDS} giay / lan")
    print("=" * 60, flush=True)

    # 1. Khởi chạy HTTP Server nhận lệnh từ điện thoại trong luồng phụ
    t = threading.Thread(target=run_http_server, daemon=True)
    t.start()

    # 2. Khởi chạy vòng lặp theo dõi thư mục tự động
    run_background_watcher()

if __name__ == "__main__":
    main()
