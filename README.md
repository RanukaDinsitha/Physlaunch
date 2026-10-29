<div align="center">

# Physlaunch

### Turn any drive into a game launcher

Choose a Steam game and a removable drive. Physlaunch writes a hidden game marker
that its optional Runner can use to launch the game through Steam.

<br />

![Platform](https://img.shields.io/badge/platform-Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white)
![App type](https://img.shields.io/badge/app%20type-HTA-66c0f4?style=for-the-badge&labelColor=1b2838)
![Installer](https://img.shields.io/badge/installer-standalone-4c9f70?style=for-the-badge)
![Steam](https://img.shields.io/badge/Steam-library%20scanner-171a21?style=for-the-badge&logo=steam&logoColor=white)

![VBScript](https://img.shields.io/badge/requires-Windows%20VBScript-5ba32b?style=flat-square&labelColor=1b2838)
![Java](https://img.shields.io/badge/Java%20port-experimental-orange?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

</div>

---

## Table of contents

- [Install](#install)
- [Use](#use)
- [Requirements and platform support](#requirements-and-platform-support)
- [Build and release](#build-and-release)
- [Project layout](#project-layout)
- [License](#license)

---

## Install

> **Note:** The first GitHub release hasn't been published yet. Until it is, use the setup file in `dist` folder or `app` folder instead.

Once released, download and run **`PhyslaunchSetup.exe`** from the latest GitHub release. It is a single-file installer with the app files and bootstrap runtime bundled inside. Setup extracts its payload to a temporary folder, runs the installer, then installs Physlaunch for the current user. A separate Java or .NET installation is not required.

**In the meantime:** run the setup from `dist/setup`, or use the individual app files in `app/files`.

Setup uses Windows MSHTA and VBScript for its installer interface. If it reports that VBScript did not start, enable **VBScript** in **Settings > System > Optional features**, close Setup, and run the installer again.

Once released, the release will also include `Physlaunch-Windows-HTA.zip` for people who want the individual app files. Keep its `app/` and `assets/` folders beside `Setup.hta` when extracting it.

## Use

1. Choose a game from an installed Steam library. Use **Add library...** if a library is not found automatically.
2. Choose a ready removable drive and select **Write Game File**.
3. Physlaunch writes the game's decimal Steam App ID to a hidden `.phys` file at the drive root and verifies both its contents and hidden attribute.

The optional Runner watches for `.phys` markers and starts games through `Steam.exe -applaunch <AppID>`. This calls Steam directly instead of sending a `steam://` link to the default browser. Steam must be installed on the computer that reads the drive.

## Requirements and platform support

- The production HTA app is for Windows and requires MSHTA and the Windows VBScript feature.
- Steam is required to scan installed games and launch them.
- Artwork may use an internet connection; artwork failure does not prevent writing or launching a marker.
- The separate [Java port](java/README.md) has Windows, macOS, and Linux build instructions. It is experimental and does not guarantee feature-for-feature parity with the HTA app.

## Build and release

The HTA app has no compile step. Build the standalone Windows Setup executable with the .NET 10 SDK:

```powershell
./setup-bootstrap/build.ps1
```

The script creates `dist/PhyslaunchSetup.exe` and checks that the app payload is embedded. The GitHub Actions workflow builds the standalone installer and source archive for `v*` tags, then attaches SHA-256 checksums. Review the workflow run and release assets before announcing a version.

## Project layout

| Path | Purpose |
| --- | --- |
| `Setup.hta` | Installer UI and install/uninstall steps |
| `app/Physlaunch.hta` | Game picker and marker writer |
| `app/PhyslaunchRunner.vbs` | Optional USB watcher and Steam launcher |
| `assets/physlaunch.ico` | Windows app and `.phys` file icon |
| `assets/physlaunch.png` | Matching logo shown inside the HTA apps and Java port |
| `setup-bootstrap/` | Source for the single-file Setup executable |
| `java/` | Experimental Java port, build scripts, and documentation |
| `.github/workflows/release.yml` | Windows release packaging |
| `LICENSE` | MIT License |

## License

Released under the [MIT License](LICENSE).

Physlaunch is not affiliated with or endorsed by Valve Corporation. Steam is a trademark of Valve Corporation.

<div align="center">

<sub>Built with HTA and VBScript for Windows. Java port is experimental.</sub>

</div>
