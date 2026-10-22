@echo off
title Gui File Vao Bambuddy TVBox
color 0E
if "%~1"=="" (
    echo [HUONG DAN] Hay keo va tha file .3mf hoac .stl truc tiep vao bieu tuong file nay de day ngay len Bambuddy TVBox!
    echo.
    pause
    exit /b
)
:loop
if "%~1"=="" goto end
echo ------------------------------------------------------------
echo Dang day file: "%~nx1" len Bambuddy TVBox...
curl.exe -s -F "file=@%~1" http://tvbox.taileba178.ts.net:8000/api/v1/library/files
echo.
echo Da gui xong file: "%~nx1"
shift
goto loop
:end
echo ============================================================
echo Hoan tat tat ca file!
pause
