# Changelog

Notable changes to Physlaunch are recorded here.

## Unreleased

- Restored the Windows HTA app, setup, and Runner as the root project files.
- Added a single-file Windows Setup executable with the HTA app bundled inside.
- Changed the Runner to launch games directly through Steam's executable.
- Added a check that the `.phys` hidden attribute is set before reporting success.
- Moved shared app artwork into `assets/` and kept the Java port separate under `java/`.
- Added a tagged GitHub release workflow for the Windows installer and source archive.
