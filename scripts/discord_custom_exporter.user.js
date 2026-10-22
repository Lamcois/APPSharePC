// ==UserScript==
// @name         Discord Auto Message & Build Exporter (DiscordMate Mechanism)
// @namespace    https://antigravity.local/
// @version      4.0
// @description  Tu dong bat Token ngam, tai lich su chat tu 01/2026 va ghi nhan tin nhan moi
// @author       Antigravity
// @match        https://discord.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function() {
    'use strict';

    let capturedToken = localStorage.getItem('__TLI_DISCORD_TOKEN__') || '';
    let isExporting = false;
    let autoListenerInterval = null;

    // 1. HOOK VAO NETWORK (FETCH & XHR) DE TU DONG BAT TOKEN NGAM
    const origFetch = window.fetch;
    window.fetch = async function(...args) {
        try {
            const [url, config] = args;
            if (config && config.headers) {
                let token = null;
                if (config.headers instanceof Headers) {
                    token = config.headers.get('authorization') || config.headers.get('Authorization');
                } else if (typeof config.headers === 'object') {
                    token = config.headers['authorization'] || config.headers['Authorization'];
                }
                if (token && token.length > 20 && !token.startsWith('Bot ')) {
                    if (token !== capturedToken) {
                        capturedToken = token;
                        localStorage.setItem('__TLI_DISCORD_TOKEN__', token);
                        updateStatusUI();
                    }
                }
            }
        } catch (e) {}
        return origFetch.apply(this, args);
    };

    const origOpen = XMLHttpRequest.prototype.open;
    const origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
        if (header.toLowerCase() === 'authorization' && value && value.length > 20 && !value.startsWith('Bot ')) {
            if (value !== capturedToken) {
                capturedToken = value;
                localStorage.setItem('__TLI_DISCORD_TOKEN__', value);
                updateStatusUI();
            }
        }
        return origSetHeader.apply(this, arguments);
    };

    // 2. CAC HAM TIEN ICH
    function getChannelIdFromUrl() {
        const parts = window.location.pathname.split('/');
        // URL co dang /channels/{guild_id}/{channel_id}
        return parts[3] || null;
    }

    function formatTime(isoStr) {
        try {
            const d = new Date(isoStr);
            return d.toLocaleString('vi-VN');
        } catch (e) {
            return isoStr;
        }
    }

    // 3. TAI LICH SU CHAT TU 01/2026 (CO RATE LIMIT BACKOFF)
    async function fetchHistoricalMessages(channelId, fromDate, onProgress) {
        if (!capturedToken) {
            throw new Error('Chua bat duoc Token! Hay click vao 1 kenh chat bat ky tren Discord de he thong tu bat Token.');
        }

        const fromTimestamp = fromDate.getTime();
        let allMessages = [];
        let beforeId = null;
        let hasMore = true;
        let batchCount = 0;

        while (hasMore && isExporting) {
            batchCount++;
            let url = `https://discord.com/api/v9/channels/${channelId}/messages?limit=100`;
            if (beforeId) {
                url += `&before=${beforeId}`;
            }

            let res;
            try {
                res = await fetch(url, {
                    headers: {
                        'Authorization': capturedToken,
                        'Accept': '*/*'
                    }
                });
            } catch (err) {
                onProgress(`Loi ket noi: ${err.message}. Dang thu lai sau 3s...`, allMessages.length);
                await new Promise(r => setTimeout(r, 3000));
                continue;
            }

            if (res.status === 429) {
                const data = await res.json();
                const waitSec = (data.retry_after || 2) + 0.5;
                onProgress(`Bị giới hạn tốc độ (Rate Limit), tạm nghỉ ${waitSec.toFixed(1)}s...`, allMessages.length);
                await new Promise(r => setTimeout(r, waitSec * 1000));
                continue;
            }

            if (!res.ok) {
                throw new Error(`Loi HTTP ${res.status}: ${res.statusText}`);
            }

            const messages = await res.json();
            if (!messages || messages.length === 0) {
                break;
            }

            let reachedOldest = false;
            for (const msg of messages) {
                const msgTime = new Date(msg.timestamp).getTime();
                if (msgTime < fromTimestamp) {
                    reachedOldest = true;
                    break;
                }
                allMessages.push(msg);
            }

            beforeId = messages[messages.length - 1].id;
            onProgress(`Dang tai... Da thu thap ${allMessages.length} tin nhan (Dot ${batchCount})`, allMessages.length);

            if (reachedOldest || messages.length < 100) {
                hasMore = false;
                break;
            }

            // Nghi 800ms giua cac request de bao ve tai khoan an toan
            await new Promise(r => setTimeout(r, 800));
        }

        return allMessages;
    }

    // 4. TAO FILE TEXT VA DOWNLOAD
    function downloadTextFile(content, filename) {
        const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = filename;
        a.click();
    }

    function exportToText(messages, channelId) {
        // Dao nguoc de xep theo thu tu thoi gian cu -> moi
        messages.reverse();

        let text = `=================================================================\n`;
        text += `XUAT LICH SU CHAT DISCORD (THEO NGUYEN LY DISCORDMATE)\n`;
        text += `Kenh ID: ${channelId}\n`;
        text += `Tong so tin nhan: ${messages.length}\n`;
        text += `Thoi diem xuat: ${new Date().toLocaleString('vi-VN')}\n`;
        text += `=================================================================\n\n`;

        for (const m of messages) {
            const author = m.author ? (m.author.global_name || m.author.username) : 'An danh';
            const time = formatTime(m.timestamp);
            const content = m.content || '';
            const attachments = (m.attachments || []).map(a => a.url).join(' , ');

            text += `[${time}] [${author}]:\n`;
            if (content) text += `${content}\n`;
            if (attachments) text += `[Dinh kem]: ${attachments}\n`;
            text += `-----------------------------------------------------------------\n`;
        }

        return text;
    }

    // 5. GIAO DIEN DIEU KHIEN (CONTROL PANEL)
    function createUI() {
        if (document.getElementById('tli-discordmate-panel')) return;

        const panel = document.createElement('div');
        panel.id = 'tli-discordmate-panel';
        panel.style.position = 'fixed';
        panel.style.bottom = '20px';
        panel.style.right = '20px';
        panel.style.zIndex = '9999999';
        panel.style.backgroundColor = '#18191c';
        panel.style.color = '#dcddde';
        panel.style.border = '2px solid #5865f2';
        panel.style.borderRadius = '12px';
        panel.style.padding = '14px';
        panel.style.width = '320px';
        panel.style.boxShadow = '0 8px 24px rgba(0,0,0,0.7)';
        panel.style.fontFamily = 'sans-serif';
        panel.style.fontSize = '13px';

        panel.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
                <b style="color:#5865f2; font-size:14px;">🔥 Discord Chat Exporter</b>
                <span id="tli-token-status" style="font-size:11px; padding:2px 6px; border-radius:4px; background:#ed4245; color:#fff;">Chưa bắt được Token</span>
            </div>
            <div style="margin-bottom:8px; font-size:12px; color:#b9bbbe;">
                Mốc lấy tin: <b>01/01/2026</b> đến nay
            </div>
            <div id="tli-log-status" style="margin-bottom:10px; font-size:12px; color:#3ba55d; min-height:18px;">
                Sẵn sàng quét...
            </div>
            <div style="display:flex; gap:8px;">
                <button id="tli-start-btn" style="flex:1; padding:8px; background:#5865f2; color:#fff; border:none; border-radius:6px; font-weight:bold; cursor:pointer;">
                    📥 Bắt đầu tải lịch sử
                </button>
                <button id="tli-stop-btn" style="display:none; padding:8px 12px; background:#ed4245; color:#fff; border:none; border-radius:6px; font-weight:bold; cursor:pointer;">
                    Dừng
                </button>
            </div>
            <div style="margin-top:8px; font-size:11px; color:#72767d; line-height:1.3;">
                * Cơ chế tự động tóm Token ngầm an toàn, gọi thẳng API không phụ thuộc giao diện.
            </div>
        `;

        document.body.appendChild(panel);

        document.getElementById('tli-start-btn').onclick = startExport;
        document.getElementById('tli-stop-btn').onclick = () => {
            isExporting = false;
            document.getElementById('tli-stop-btn').style.display = 'none';
            document.getElementById('tli-start-btn').style.display = 'block';
            document.getElementById('tli-log-status').innerText = 'Đã dừng quét.';
        };

        updateStatusUI();
    }

    function updateStatusUI() {
        const badge = document.getElementById('tli-token-status');
        if (!badge) return;
        if (capturedToken) {
            badge.style.backgroundColor = '#3ba55d';
            badge.innerText = 'Token Đã Sẵn Sàng';
        } else {
            badge.style.backgroundColor = '#ed4245';
            badge.innerText = 'Chờ Click Chat';
        }
    }

    async function startExport() {
        const channelId = getChannelIdFromUrl();
        if (!channelId) {
            alert('Vui lòng mở đúng kênh chat bạn muốn quét trước!');
            return;
        }

        if (!capturedToken) {
            alert('Hệ thống chưa bắt được Token. Hãy thử click vào khung chat hoặc gõ 1 chữ rồi bấm lại nút nhé!');
            return;
        }

        isExporting = true;
        const startBtn = document.getElementById('tli-start-btn');
        const stopBtn = document.getElementById('tli-stop-btn');
        const statusEl = document.getElementById('tli-log-status');

        startBtn.style.display = 'none';
        stopBtn.style.display = 'block';

        const fromDate = new Date('2026-01-01T00:00:00Z');

        try {
            statusEl.innerText = 'Đang kết nối API Discord...';
            const messages = await fetchHistoricalMessages(channelId, fromDate, (text, count) => {
                statusEl.innerText = text;
            });

            if (messages.length === 0) {
                statusEl.innerText = 'Không có tin nhắn nào từ 01/2026 đến nay.';
            } else {
                statusEl.innerText = `Hoàn tất! Đã gom ${messages.length} tin. Đang lưu...`;
                const content = exportToText(messages, channelId);
                downloadTextFile(content, `discord_channel_${channelId}_tu_thang_1_2026.txt`);
                statusEl.innerText = `Đã tải về máy: ${messages.length} tin!`;
            }
        } catch (err) {
            statusEl.innerText = `Lỗi: ${err.message}`;
            alert(`Lỗi khi quét: ${err.message}`);
        } finally {
            isExporting = false;
            startBtn.style.display = 'block';
            stopBtn.style.display = 'none';
        }
    }

    // Khoi tao giao dien khi trang load xong
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        createUI();
    } else {
        window.addEventListener('DOMContentLoaded', createUI);
    }
    // Thu kiem tra lai sau 2 giay de phong Discord load cham
    setTimeout(createUI, 2000);
})();
