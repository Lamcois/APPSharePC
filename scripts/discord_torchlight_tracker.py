import os
import sys
import json
import time
import datetime
import warnings

# Đảm bảo console Windows không bị lỗi Unicode
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

warnings.filterwarnings("ignore")
import requests

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "discord_config.json")
STATE_PATH = os.path.join(BASE_DIR, "discord_state.json")

def load_config():
    if not os.path.exists(CONFIG_PATH):
        default_cfg = {
            "token": "DIEN_TOKEN_VAO_DAY",
            "check_interval_seconds": 30,
            "output_file": "torchlight_discord_chat.txt",
            "channels": {
                "1357539594681323612": "#🔥┃gemma",
                "1357532825561202748": "#💬┃general"
            }
        }
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(default_cfg, f, indent=2, ensure_ascii=False)
        return default_cfg

    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

def save_config(cfg):
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)

def load_state():
    if os.path.exists(STATE_PATH):
        try:
            with open(STATE_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"last_message_ids": {}}

def save_state(state):
    with open(STATE_PATH, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2, ensure_ascii=False)

def format_timestamp(iso_str):
    try:
        dt = datetime.datetime.fromisoformat(iso_str.replace("Z", "+00:00"))
        # Chuyển về giờ địa phương
        dt_local = dt.astimezone()
        return dt_local.strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        return iso_str

def append_to_log(output_path, message_entries):
    if not message_entries:
        return
    with open(output_path, "a", encoding="utf-8") as f:
        for entry in message_entries:
            f.write(entry + "\n")
        f.flush()

def main():
    print("=" * 60)
    print("[*] HE THONG THEO DOI CHAT DISCORD - TORCHLIGHT: INFINITE")
    print("=" * 60)

    cfg = load_config()
    token = cfg.get("token", "").strip()

    if not token or token == "DIEN_TOKEN_VAO_DAY":
        print("\n[CHU Y] Chua co Token Discord trong file config.")
        if sys.stdin.isatty():
            try:
                user_token = input(">> Dan Token Discord cua ban vao day va bam Enter: ").strip()
                if user_token:
                    cfg["token"] = user_token
                    save_config(cfg)
                    token = user_token
                    print(">> Da luu Token vao scripts/discord_config.json!\n")
                else:
                    print(">> Khong co Token, tien trinh ket thuc.")
                    return
            except (KeyboardInterrupt, EOFError):
                return
        else:
            print("Vui long mo file 'scripts/discord_config.json' va dien Token vao truong 'token'.")
            return

    # Xác định đường dẫn file log kết quả
    out_rel = cfg.get("output_file", "torchlight_discord_chat.txt")
    if os.path.isabs(out_rel):
        output_path = out_rel
    else:
        # Lưu ra thư mục gốc d:\APPSharePC
        output_path = os.path.abspath(os.path.join(BASE_DIR, "..", out_rel))

    interval = max(10, int(cfg.get("check_interval_seconds", 30)))
    channels = cfg.get("channels", {})
    state = load_state()
    last_ids = state.get("last_message_ids", {})

    headers = {
        "Authorization": token,
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9"
    }

    print(f"[*] Dang theo doi {len(channels)} kenh:")
    for ch_id, ch_name in channels.items():
        print(f"    - {ch_name} (ID: {ch_id})")
    print(f"[*] Chu ky kiem tra: {interval} giay/lan")
    print(f"[*] File ghi log: {output_path}")
    print("[*] Nhan Ctrl+C de dung.")
    print("-" * 60)

    session = requests.Session()
    session.headers.update(headers)

    while True:
        for ch_id, ch_name in channels.items():
            last_id = last_ids.get(ch_id)
            url = f"https://discord.com/api/v9/channels/{ch_id}/messages"

            params = {}
            is_first_run = (last_id is None)

            if is_first_run:
                params["limit"] = 25  # Lấy 25 tin gần nhất khi mới khởi động
            else:
                params["after"] = last_id
                params["limit"] = 50

            try:
                res = session.get(url, params=params, timeout=15)

                if res.status_code == 200:
                    messages = res.json()
                    if messages:
                        # API trả về từ mới nhất -> cũ nhất, đảo ngược để ghi theo thứ tự thời gian
                        messages.reverse()
                        entries = []
                        new_last_id = last_id

                        for msg in messages:
                            mid = msg.get("id")
                            new_last_id = mid
                            author_info = msg.get("author", {})
                            author_name = author_info.get("global_name") or author_info.get("username") or "Vo danh"
                            content = msg.get("content", "").strip()
                            timestamp = format_timestamp(msg.get("timestamp", ""))

                            # File đính kèm (ảnh / clip)
                            attachments = [att.get("url") for att in msg.get("attachments", []) if att.get("url")]
                            att_str = ""
                            if attachments:
                                att_str = "\n[Dinh kem]: " + " , ".join(attachments)

                            entry_text = f"[{timestamp}] [{ch_name}] [{author_name}]:\n{content}{att_str}\n" + ("-" * 50)
                            entries.append(entry_text)

                        append_to_log(output_path, entries)
                        last_ids[ch_id] = new_last_id
                        state["last_message_ids"] = last_ids
                        save_state(state)

                        log_tag = "Lich su" if is_first_run else "Moi"
                        print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] [{ch_name}] Da ghi nhan {len(entries)} tin ({log_tag})")

                elif res.status_code == 429:
                    retry_after = res.json().get("retry_after", 5)
                    print(f"[!] Bị giới hạn tốc độ (Rate limit). Tam dung {retry_after} giay...")
                    time.sleep(retry_after)
                elif res.status_code == 401:
                    print("\n[ERROR] Token Discord khong hop le hoac da het han!")
                    print("Vui long cap nhat lai Token trong file 'scripts/discord_config.json'.")
                    time.sleep(60)
                elif res.status_code == 403:
                    print(f"[!] Khong co quyen truy cap kenh {ch_name} (ID: {ch_id}).")
                else:
                    print(f"[!] Loi HTTP {res.status_code} khi doc kenh {ch_name}")

            except requests.exceptions.RequestException as e:
                print(f"[!] Loi ket noi mang: {e}")

            time.sleep(2)  # Nghỉ nhẹ giữa các kênh

        time.sleep(interval)

if __name__ == "__main__":
    main()
