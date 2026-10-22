import os
import sys
import datetime
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

def is_valid_file(file_name: str) -> bool:
    if file_name.startswith(".") or file_name.startswith("~"):
        return False
    lower = file_name.lower()
    if lower in ("desktop.ini", "thumbs.db"):
        return False
    if lower.endswith(".tmp") or lower.endswith(".crdownload") or lower.endswith(".part"):
        return False
    return True

def sync_snapmaker():
    print("\n------------------------------------------------------------")
    print(f"[*] DANG KIEM TRA SNAPMAKER U1 ({SNAPMAKER_URL})...")
    print(f"[*] Thu muc nguon: {SNAP_DIR}")

    if not os.path.exists(SNAP_DIR):
        print("[-] Thu muc Snap chua ton tai.")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}

    # 1. Check online
    try:
        r = requests.get(f"{SNAPMAKER_URL}/server/info", timeout=5)
        if r.status_code != 200:
            print("[-] May in Snapmaker U1 khong phan hoi (HTTP Khac 200).")
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}
    except Exception as e:
        print(f"[-] Khong the ket noi toi Snapmaker U1 qua Tailscale: {e}")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}

    # 2. Get remote files
    try:
        list_r = requests.get(f"{SNAPMAKER_URL}/server/files/list?root=gcodes", timeout=8)
        remote_data = list_r.json().get("result", [])
        existing_remote_files = {item.get("path") for item in remote_data if item.get("path")}
        print(f"[+] May in online! Da tim thay {len(existing_remote_files)} file gcode tren may in.")
    except Exception as e:
        print(f"[-] Loi khi lay danh sach file tu Snapmaker: {e}")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}

    local_files = [f for f in os.listdir(SNAP_DIR) if is_valid_file(f) and os.path.isfile(os.path.join(SNAP_DIR, f))]
    if not local_files:
        print("[i] Thu muc Snap tren PC khong co file nao de dong bo.")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": True}

    uploaded = 0
    skipped = 0
    failed = 0

    for file_name in local_files:
        if file_name in existing_remote_files:
            print(f"  [=] Bo qua (da co tren may in): {file_name}")
            skipped += 1
            continue

        file_path = os.path.join(SNAP_DIR, file_name)
        file_size_mb = os.path.getsize(file_path) / (1024 * 1024)
        print(f"  [>] Dang day file len Snapmaker: {file_name} ({file_size_mb:.2f} MB)...")

        try:
            with open(file_path, "rb") as f:
                files = {"file": (file_name, f, "application/octet-stream")}
                data = {"root": "gcodes", "print": "false"}
                up_r = requests.post(f"{SNAPMAKER_URL}/server/files/upload", files=files, data=data, timeout=180)

            if up_r.status_code in (200, 201):
                print(f"  [OK] Day thanh cong: {file_name}")
                existing_remote_files.add(file_name)
                uploaded += 1
            else:
                print(f"  [ERR] That bai: {file_name} (HTTP {up_r.status_code})")
                failed += 1
        except Exception as ex:
            print(f"  [ERR] Loi khi truyen file {file_name}: {ex}")
            failed += 1

    return {"uploaded": uploaded, "skipped": skipped, "failed": failed, "online": True}

def sync_bambuddy():
    print("\n------------------------------------------------------------")
    print(f"[*] DANG KIEM TRA BAMBUDDY TVBOX ({BAMBUDDY_URL})...")
    print(f"[*] Thu muc nguon: {BAMBU_DIR}")

    if not os.path.exists(BAMBU_DIR):
        print("[-] Thu muc Bambu chua ton tai.")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}

    # 1. Check online
    try:
        r = requests.get(f"{BAMBUDDY_URL}/docs", timeout=5)
        if r.status_code != 200:
            print("[-] May chu Bambuddy khong phan hoi.")
            return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}
    except Exception as e:
        print(f"[-] Khong the ket noi toi Bambuddy TVBox qua Tailscale: {e}")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}

    # 2. Get remote files
    try:
        list_r = requests.get(f"{BAMBUDDY_URL}/api/v1/library/files", timeout=10)
        remote_data = list_r.json()
        existing_remote_names = {item.get("filename") for item in remote_data if isinstance(item, dict) and item.get("filename")}
        print(f"[+] Bambuddy online! Da tim thay {len(existing_remote_names)} file trong thu vien.")
    except Exception as e:
        print(f"[-] Loi khi lay danh sach file tu Bambuddy: {e}")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": False}

    local_files = [f for f in os.listdir(BAMBU_DIR) if is_valid_file(f) and os.path.isfile(os.path.join(BAMBU_DIR, f))]
    if not local_files:
        print("[i] Thu muc Bambu tren PC khong co file nao de dong bo.")
        return {"uploaded": 0, "skipped": 0, "failed": 0, "online": True}

    uploaded = 0
    skipped = 0
    failed = 0

    for file_name in local_files:
        if file_name in existing_remote_names:
            print(f"  [=] Bo qua (da co trong Bambuddy): {file_name}")
            skipped += 1
            continue

        file_path = os.path.join(BAMBU_DIR, file_name)
        file_size_mb = os.path.getsize(file_path) / (1024 * 1024)
        print(f"  [>] Dang day file len Bambuddy: {file_name} ({file_size_mb:.2f} MB)...")

        try:
            with open(file_path, "rb") as f:
                files = {"file": (file_name, f, "application/octet-stream")}
                up_r = requests.post(f"{BAMBUDDY_URL}/api/v1/library/files", files=files, timeout=180)

            if up_r.status_code in (200, 201):
                print(f"  [OK] Day thanh cong: {file_name}")
                existing_remote_names.add(file_name)
                uploaded += 1
            else:
                print(f"  [ERR] That bai: {file_name} (HTTP {up_r.status_code})")
                failed += 1
        except Exception as ex:
            print(f"  [ERR] Loi khi truyen file {file_name}: {ex}")
            failed += 1

    return {"uploaded": uploaded, "skipped": skipped, "failed": failed, "online": True}

def main():
    print("=" * 60)
    print("       1-CLICK DONG BO FILE IN 3D SANG SNAPMAKER & BAMBU")
    print("=" * 60)

    snap_result = sync_snapmaker()
    bambu_result = sync_bambuddy()

    print("\n============================================================")
    print("                 TONG KET KET QUA DONG BO")
    print("============================================================")

    if snap_result["online"]:
        print(f"[Snapmaker U1]  : + Day moi: {snap_result['uploaded']} | = Da co (bo qua): {snap_result['skipped']} | ! Loi: {snap_result['failed']}")
    else:
        print("[Snapmaker U1]  : Ngoai tuyen (Khong ket noi duoc)")

    if bambu_result["online"]:
        print(f"[Bambuddy TVBox]: + Day moi: {bambu_result['uploaded']} | = Da co (bo qua): {bambu_result['skipped']} | ! Loi: {bambu_result['failed']}")
    else:
        print("[Bambuddy TVBox]: Ngoai tuyen (Khong ket noi duoc)")

    print("============================================================\n")

if __name__ == "__main__":
    main()
