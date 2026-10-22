Set WshShell = CreateObject("WScript.Shell")
scriptPath = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\scripts\discord_torchlight_tracker.py"
WshShell.Run "pythonw """ & scriptPath & """", 0, False
MsgBox "Da khoi dong he thong theo doi Discord Torchlight: Infinite chay an trong nen Windows!" & vbCrLf & "- Du lieu se tu dong luu vao: torchlight_discord_chat.txt", 64, "Torchlight Discord Tracker"
