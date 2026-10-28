# Contributing

Thanks for helping improve Physlaunch. Keep changes focused, describe the user-visible effect, and preserve the existing Windows HTA behavior unless a change intentionally updates it.

## Before opening a pull request

- Explain the issue and the change in plain language.
- For HTA or VBScript changes, check files under `app/` and `assets/` remain together in the release package. Manually verify the affected flow on Windows with VBScript enabled.
- For the standalone Setup bootstrap, use the .NET 10 SDK and verify the published `PhyslaunchSetup.exe --check-package` command.
- For Java changes, follow `java/README.md` and run the native build for the platform you changed.
- Do not include generated build output, local settings, or game-library data.

The root app depends on Windows MSHTA and VBScript. Java port changes belong under `java/`.
