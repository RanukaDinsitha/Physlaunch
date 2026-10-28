# Physlaunch Java Port

*This is expiremental.*

> **Experimental:** This is a separate Java implementation of Physlaunch. It is maintained apart from the Windows HTA app and is not the production release. It does not yet guarantee feature-for-feature parity with the HTA app.

It can write a small Steam game marker to a removable drive and run an optional background watcher. Pick an installed game and a drive, and the app writes that game's hidden .phys file at the drive root. The Runner reads the marker when the drive is connected and asks Steam to open the game.

The current app is written in Java and ships with its own runtime. It does not need a separate Java installation.

## Install

Release bundles are built for Windows x64, macOS, and Linux. Each bundle contains two self-contained application folders:

- **Setup** installs Physlaunch for the current user. It can create a desktop shortcut, start the Runner when you sign in, and register .phys files to open with Physlaunch.
- **LauncherHub** contains the game picker, marker writer, and optional Runner.

Keep both folders together in the extracted bundle while setup runs. Start Setup.exe from the Setup folder on Windows, Setup from Setup/bin on Linux, or Setup.app on macOS. Setup copies LauncherHub into your user application folder. No administrator access is needed.

## Use

1. Open Physlaunch and choose a game. Search narrows the list as you type.
2. Select a ready removable drive.
3. Select Write Game File. Physlaunch writes .phys, reads it back, and confirms the App ID.

If Steam is installed in a nonstandard location, use Add library... to select the Steam folder or a library folder that contains steamapps. Added library paths are remembered for your account.

## Marker and Runner

The marker is a hidden file named `.phys` in the root of the drive, for example `E:\.phys`. It contains the decimal Steam App ID. The Runner accepts only markers containing 1 to 10 decimal digits and asks the Steam executable to launch the game directly.

The Runner is part of LauncherHub. Setup can add it to the current user's sign-in startup. It watches removable and USB volumes and logs to the Physlaunch settings folder. Opening a .phys file through the registered file association also launches its game.

## Steam library scanning

Physlaunch checks common Steam install locations, reads steamapps/libraryfolders.vdf, and scans appmanifest_*.acf files for installed games. It skips Steamworks redistributables, Proton, and Steam Linux Runtime tools. Game names are sorted alphabetically.

Artwork is read from Steam's local library cache first. If no local header image is available, Physlaunch tries Steam's artwork CDN and falls back to a tile with the game's initial. Internet access is only needed for this artwork fallback.

## Supported systems

The build scripts target Windows x64, macOS, and Linux. USB and removable-volume discovery uses Windows disk information, Linux `lsblk`, and macOS `diskutil`. Build each bundle on its target operating system; native packages are not cross-compiled.

## Build from source

Install JDK 17 or newer with javac, jar, and jpackage. On macOS, the build also uses the system sips and iconutil tools.

Run build.ps1 on Windows or bash build.sh on macOS/Linux. The build compiles both apps and creates self-contained app images in dist/. The Windows build also creates dist/Physlaunch-windows-x64.zip.

The release workflow template is `release-workflow.yml`. GitHub only runs workflows stored under `.github/workflows/`; copy the template there if automated Java releases are wanted.

## Project layout

| Path | Purpose |
| --- | --- |
| src/main/java/physlaunch/LauncherHub.java | Game picker, Steam scanner, marker writer, and Runner |
| src/main/java/physlaunch/Setup.java | Per-user setup application |
| ../assets/physlaunch.png | Shared app logo image |
| ../assets/physlaunch.ico | Shared Windows icon and Java package icon |
| build.ps1, build.sh | Native app-image packaging |
| release-workflow.yml | Optional GitHub Actions release template |
| ../app/Physlaunch.hta, ../Setup.hta | Original Windows HTA app and setup |
| ../LICENSE | MIT License |

Physlaunch is not affiliated with or endorsed by Valve Corporation. Steam is a trademark of Valve Corporation.
