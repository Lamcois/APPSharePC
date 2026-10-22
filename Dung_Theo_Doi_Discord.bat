@echo off
echo Dang dung tien trinh theo doi Discord Torchlight...
powershell -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*discord_torchlight_tracker.py*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
echo Da tat he thong theo doi Discord thanh cong!
pause
