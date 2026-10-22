@echo off
echo Dang dung tien trinh dong bo in 3D...
powershell -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*auto_sync_3d_printers.py*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
echo Da tat he thong dong bo in 3D thanh cong!
pause
