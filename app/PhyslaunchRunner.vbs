Option Explicit

' Physlaunch Runner
' Sits in the background and starts the Steam game named in a hidden ".phys"
' file whenever a USB stick, memory card or disc containing one is connected.
'
'   app\PhyslaunchRunner.vbs              start watching
'   app\PhyslaunchRunner.vbs /install     start with Windows (and start now)
'   app\PhyslaunchRunner.vbs /uninstall   stop it and remove the autostart entry
'   app\PhyslaunchRunner.vbs /open <file> launch the game named in one .phys file

Const MARKER = ".phys"
Const POLL_MS = 5000
Const WBEM_TIMEOUT = -2147209215
Const MAX_LOG = 204800
Const PROMPT_KEY = "HKCU\Software\Physlaunch\RunnerPrompted"

Dim fso, sh, wmi, active, logPath, arg

Set fso = CreateObject("Scripting.FileSystemObject")
Set sh = CreateObject("WScript.Shell")
Set active = CreateObject("Scripting.Dictionary")
active.CompareMode = 1

On Error Resume Next
Set wmi = GetObject("winmgmts:\\.\root\cimv2")
On Error GoTo 0

logPath = sh.ExpandEnvironmentStrings("%LOCALAPPDATA%") & "\Physlaunch\runner.log"

arg = ""
If WScript.Arguments.Count > 0 Then arg = LCase(WScript.Arguments(0))

If arg = "/install" Then
    Install
    WScript.Quit
ElseIf arg = "/uninstall" Then
    Uninstall
    WScript.Quit
ElseIf arg = "/open" Then
    OpenMarker
    WScript.Quit
End If

If InstanceCount() > 1 Then WScript.Quit
OfferInstall
Watch


' ---------- main loop ----------

Sub Watch
    Dim events, ev

    Log "Runner started"
    Set events = NewEventQuery()

    Do
        Sweep

        If events Is Nothing Then
            WScript.Sleep POLL_MS
        Else
            On Error Resume Next
            Err.Clear
            Set ev = events.NextEvent(POLL_MS)
            If Err.Number = 0 Then
                ' a volume came or went, give Windows a moment to mount it
                WScript.Sleep 1500
            ElseIf Err.Number <> WBEM_TIMEOUT Then
                Err.Clear
                WScript.Sleep 2000
                Set events = NewEventQuery()
            End If
            Err.Clear
            On Error GoTo 0
        End If
    Loop
End Sub

Function NewEventQuery()
    Set NewEventQuery = Nothing
    On Error Resume Next
    Set NewEventQuery = wmi.ExecNotificationQuery("SELECT * FROM Win32_VolumeChangeEvent")
    If Err.Number <> 0 Then
        Log "Could not subscribe to drive events (" & Err.Description & "), polling instead"
        Set NewEventQuery = Nothing
        Err.Clear
    End If
End Function

' looks at every drive, launches anything new and forgets drives that were removed
Sub Sweep
    Dim d, letter, here, serial, needsLaunch, k

    here = ";"
    On Error Resume Next

    For Each d In fso.Drives
        letter = UCase(d.DriveLetter)
        If letter <> "" And d.IsReady Then
            ' removable (1), fixed (2) and optical (4); fixed drives only count if they are on USB
            If d.DriveType = 1 Or d.DriveType = 2 Or d.DriveType = 4 Then
                If fso.FileExists(letter & ":\" & MARKER) Then
                    If d.DriveType <> 2 Or IsUsbLetter(letter) Then
                        here = here & letter & ";"
                        serial = CStr(d.SerialNumber)

                        If Not active.Exists(letter) Then
                            needsLaunch = True
                        ElseIf active(letter) <> serial Then
                            needsLaunch = True     ' different media in the same drive letter
                        Else
                            needsLaunch = False
                        End If

                        If needsLaunch Then Launch letter, serial
                    End If
                End If
            End If
        End If
        Err.Clear
    Next

    For Each k In active.Keys
        If InStr(here, ";" & k & ";") = 0 Then active.Remove k
    Next
    Err.Clear
End Sub

Sub Launch(letter, serial)
    Dim appId

    ' remember the drive even if the file is bad so we don't retry every few seconds
    active(letter) = serial

    appId = ReadAppId(letter & ":\" & MARKER)
    If appId = "" Then
        Log letter & ": " & MARKER & " found but it doesn't contain a valid App ID"
        Exit Sub
    End If

    If StartSteamApp(appId) Then
        Log letter & ": started Steam App ID " & appId
    Else
        Log letter & ": could not start App ID " & appId & ". Steam.exe was not found or could not be started."
    End If
End Sub

' used by the .phys file association: double-click a marker file to launch its game
Sub OpenMarker
    Dim path, appId

    If WScript.Arguments.Count < 2 Then Exit Sub
    path = WScript.Arguments(1)

    appId = ReadAppId(path)
    If appId = "" Then
        MsgBox "That .phys file doesn't contain a valid Steam App ID.", vbExclamation, "Physlaunch"
        Exit Sub
    End If

    If Not StartSteamApp(appId) Then
        MsgBox "Steam could not be started. Check that Steam is installed and try again.", vbExclamation, "Physlaunch"
        Exit Sub
    End If
    Log "Opened " & path & ": started Steam App ID " & appId
End Sub

' Start Steam directly so Windows never hands the steam:// link to a browser.
Function StartSteamApp(appId)
    Dim exe, command

    StartSteamApp = False
    exe = FindSteamExe()
    If exe = "" Then Exit Function

    command = """" & exe & """ -applaunch " & appId
    On Error Resume Next
    Err.Clear
    sh.Run command, 1, False
    If Err.Number = 0 Then StartSteamApp = True
    On Error GoTo 0
End Function

Function FindSteamExe()
    Dim candidates, candidate, path

    FindSteamExe = ""
    candidates = Array( _
        ReadRegistry("HKCU\Software\Valve\Steam\SteamPath"), _
        ReadRegistry("HKCU\Software\Valve\Steam\InstallPath"), _
        ReadRegistry("HKLM\SOFTWARE\WOW6432Node\Valve\Steam\InstallPath"), _
        ReadRegistry("HKLM\SOFTWARE\Valve\Steam\InstallPath"), _
        sh.ExpandEnvironmentStrings("%ProgramFiles(x86)%") & "\Steam", _
        sh.ExpandEnvironmentStrings("%ProgramFiles%") & "\Steam", _
        sh.ExpandEnvironmentStrings("%LOCALAPPDATA%") & "\Programs\Steam", _
        "C:\Program Files (x86)\Steam", _
        "C:\Program Files\Steam")

    For Each candidate In candidates
        path = Trim(Replace(CStr(candidate), "/", "\"))
        If path <> "" Then
            If LCase(Right(path, 9)) = "steam.exe" Then
                If fso.FileExists(path) Then
                    FindSteamExe = path
                    Exit Function
                End If
            ElseIf fso.FileExists(path & "\steam.exe") Then
                FindSteamExe = path & "\steam.exe"
                Exit Function
            End If
        End If
    Next
End Function

Function ReadRegistry(key)
    ReadRegistry = ""
    On Error Resume Next
    ReadRegistry = CStr(sh.RegRead(key))
    Err.Clear
    On Error GoTo 0
End Function

Function ReadAppId(path)
    Dim ts, text, re

    ReadAppId = ""
    On Error Resume Next

    Set ts = fso.OpenTextFile(path, 1)
    If Err.Number <> 0 Then
        Err.Clear
        Exit Function
    End If
    text = ts.ReadAll
    ts.Close

    text = Trim(Replace(Replace(text, vbCr, ""), vbLf, ""))
    Set re = New RegExp
    re.Pattern = "^\d{1,10}$"
    If re.Test(text) Then ReadAppId = text
    Err.Clear
End Function

' external USB disks often report themselves as "fixed", so ask WMI about those
Function IsUsbLetter(letter)
    Dim disk, part, logical

    IsUsbLetter = False
    On Error Resume Next

    For Each disk In wmi.ExecQuery("SELECT * FROM Win32_DiskDrive WHERE InterfaceType='USB'")
        For Each part In wmi.ExecQuery("ASSOCIATORS OF {Win32_DiskDrive.DeviceID='" & Replace(disk.DeviceID, "\", "\\") & "'} WHERE AssocClass=Win32_DiskDriveToDiskPartition")
            For Each logical In wmi.ExecQuery("ASSOCIATORS OF {Win32_DiskPartition.DeviceID='" & part.DeviceID & "'} WHERE AssocClass=Win32_LogicalDiskToPartition")
                If UCase(logical.DeviceID) = letter & ":" Then IsUsbLetter = True
            Next
        Next
    Next
    Err.Clear
End Function


' ---------- running instances ----------

' true for a normal runner process, false for the short-lived /install, /uninstall and /open ones
Function IsRunner(commandLine)
    Dim cl
    cl = LCase(commandLine & "")
    IsRunner = (InStr(cl, LCase(WScript.ScriptName)) > 0 And InStr(cl, "/install") = 0 And InStr(cl, "/uninstall") = 0 And InStr(cl, "/open") = 0)
End Function

Function InstanceCount()
    Dim p, n
    n = 0
    On Error Resume Next
    For Each p In wmi.ExecQuery("SELECT CommandLine FROM Win32_Process WHERE Name = 'wscript.exe' OR Name = 'cscript.exe'")
        If IsRunner(p.CommandLine) Then n = n + 1
    Next
    InstanceCount = n
End Function

Sub StopOthers
    Dim p
    On Error Resume Next
    For Each p In wmi.ExecQuery("SELECT * FROM Win32_Process WHERE Name = 'wscript.exe' OR Name = 'cscript.exe'")
        If IsRunner(p.CommandLine) Then p.Terminate
    Next
End Sub


' ---------- autostart ----------

Function ShortcutPath()
    ShortcutPath = sh.SpecialFolders("Startup") & "\Physlaunch Runner.lnk"
End Function

Sub MakeShortcut
    Dim lnk
    Set lnk = sh.CreateShortcut(ShortcutPath())
    lnk.TargetPath = sh.ExpandEnvironmentStrings("%SystemRoot%") & "\System32\wscript.exe"
    lnk.Arguments = """" & WScript.ScriptFullName & """"
    lnk.WorkingDirectory = fso.GetParentFolderName(WScript.ScriptFullName)
    lnk.Description = "Physlaunch Runner"
    lnk.WindowStyle = 7
    lnk.Save
End Sub

Sub Install
    StopOthers
    WScript.Sleep 1000
    MakeShortcut
    sh.Run """" & sh.ExpandEnvironmentStrings("%SystemRoot%") & "\System32\wscript.exe"" """ & WScript.ScriptFullName & """", 0, False
    MsgBox "Physlaunch Runner is running in the background and will start with Windows.", vbInformation, "Physlaunch Runner"
End Sub

Sub Uninstall
    StopOthers
    On Error Resume Next
    If fso.FileExists(ShortcutPath()) Then fso.DeleteFile ShortcutPath()
    On Error GoTo 0
    MsgBox "Physlaunch Runner has been stopped and removed from startup.", vbInformation, "Physlaunch Runner"
End Sub

' first manual launch: offer to set up autostart, but only ask once
Sub OfferInstall
    Dim seen, answer

    If fso.FileExists(ShortcutPath()) Then Exit Sub

    On Error Resume Next
    seen = ""
    seen = sh.RegRead(PROMPT_KEY)
    Err.Clear
    If seen <> "" Then Exit Sub

    sh.RegWrite PROMPT_KEY, "1", "REG_SZ"
    answer = MsgBox("Start Physlaunch Runner automatically when you sign in to Windows?", vbYesNo + vbQuestion, "Physlaunch Runner")
    If answer = vbYes Then MakeShortcut
    Err.Clear
End Sub


' ---------- logging ----------

Sub Log(message)
    Dim folder, f
    On Error Resume Next

    folder = fso.GetParentFolderName(logPath)
    If Not fso.FolderExists(folder) Then fso.CreateFolder folder
    If fso.FileExists(logPath) Then
        If fso.GetFile(logPath).Size > MAX_LOG Then fso.DeleteFile logPath
    End If

    Set f = fso.OpenTextFile(logPath, 8, True)
    f.WriteLine Now & "  " & message
    f.Close
    Err.Clear
End Sub
