Set WshShell = CreateObject("WScript.Shell")
scriptPath = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\scripts\auto_sync_3d_printers.py"
WshShell.Run "pythonw """ & scriptPath & """", 0, False
MsgBox "Da khoi dong he thong dong bo in 3D chay an trong nen Windows!" & vbCrLf & "- Thu muc Snap -> Day vao Snapmaker U1" & vbCrLf & "- Thu muc Bambu -> Day vao Bambuddy TVBox", 64, "APPSharePC Auto Sync"
