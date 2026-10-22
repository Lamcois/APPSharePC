@echo off
title Gui File Vao Snapmaker U1
color 0A
if "%~1"=="" (
    echo [HUONG DAN] Hay keo va tha file .gcode hoac .3mf truc tiep vao bieu tuong file nay de day ngay len Snapmaker U1!
    echo.
    pause
    exit /b
)
:loop
if "%~1"=="" goto end
echo ------------------------------------------------------------
echo Dang day file: "%~nx1" len Snapmaker U1...
curl.exe -s -F "file=@%~1" -F "root=gcodes" -F "print=false" http://snapmaker.taileba178.ts.net/server/files/upload
echo.
echo Da gui xong file: "%~nx1"
shift
goto loop
:end
echo ============================================================
echo Hoan tat tat ca file!
pause
