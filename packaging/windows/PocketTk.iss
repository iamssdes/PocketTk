; Inno Setup script. build_app.ps1 passes /DAppVersion=... /DSourceDir=... /DOutDir=... /DArch=x64|arm64
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
[Setup]
AppId={{6F0B6E0B-5B1B-4C55-9D7C-50C4E7E7A7D1}
AppName=PocketTk
AppVersion={#AppVersion}
AppPublisher=SSDES
DefaultDirName={localappdata}\Programs\PocketTk
DefaultGroupName=PocketTk
PrivilegesRequired=lowest
OutputDir={#OutDir}
OutputBaseFilename=PocketTk-{#AppVersion}-windows-{#Arch}-setup
SetupIconFile={#SourceDir}\PocketTk.ico
UninstallDisplayIcon={app}\PocketTk.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=yes
LicenseFile={#SourceDir}\app\LICENSE
#if Arch == "arm64"
ArchitecturesAllowed=arm64
ArchitecturesInstallIn64BitMode=arm64
#else
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
#endif

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\PocketTk"; Filename: "{app}\python\pythonw.exe"; Parameters: """{app}\app\pockettk_gui.py"""; WorkingDir: "{userdocs}"; IconFilename: "{app}\PocketTk.ico"
Name: "{autodesktop}\PocketTk"; Filename: "{app}\python\pythonw.exe"; Parameters: """{app}\app\pockettk_gui.py"""; WorkingDir: "{userdocs}"; IconFilename: "{app}\PocketTk.ico"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; Flags: unchecked

[Run]
Filename: "{app}\python\pythonw.exe"; Parameters: """{app}\app\pockettk_gui.py"""; Description: "Start PocketTk"; Flags: nowait postinstall skipifsilent
