#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(dirname "$ROOT")"
BUILD="$ROOT/build"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
TARGET="$OS-$ARCH"
STAGE="$BUILD/stage-$TARGET"
DIST="$ROOT/dist/$TARGET"

rm -rf "$STAGE" "$DIST" "$BUILD/classes"
mkdir -p "$STAGE/hub" "$STAGE/setup" "$STAGE/hub-input/physlaunch" "$STAGE/setup-input/physlaunch" "$BUILD/classes" "$DIST"

find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -d "$BUILD/classes"
cp "$BUILD/classes/physlaunch/LauncherHub"*.class "$STAGE/hub-input/physlaunch/"
cp "$BUILD/classes/physlaunch/Setup"*.class "$STAGE/setup-input/physlaunch/"
cp "$REPO/assets/physlaunch.png" "$STAGE/hub-input/logo.png"
cp "$REPO/assets/physlaunch.png" "$STAGE/setup-input/logo.png"
jar --create --file "$STAGE/hub-input/LauncherHub.jar" --main-class physlaunch.LauncherHub -C "$STAGE/hub-input" .
jar --create --file "$STAGE/setup-input/Setup.jar" --main-class physlaunch.Setup -C "$STAGE/setup-input" .
rm -rf "$STAGE/hub-input/physlaunch" "$STAGE/setup-input/physlaunch"

if [[ "$OS" == *mingw* || "$OS" == *msys* || "$OS" == *cygwin* ]]; then
  ICON="$REPO/assets/physlaunch.ico"
elif [[ "$OS" == "darwin" ]]; then
  ICONSET="$STAGE/physlaunch.iconset"
  mkdir -p "$ICONSET"
  sips -z 16 16 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_16x16.png" >/dev/null
  sips -z 32 32 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_16x16@2x.png" >/dev/null
  sips -z 32 32 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_32x32.png" >/dev/null
  sips -z 64 64 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_32x32@2x.png" >/dev/null
  sips -z 128 128 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_128x128.png" >/dev/null
  sips -z 256 256 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_128x128@2x.png" >/dev/null
  sips -z 256 256 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_256x256.png" >/dev/null
  sips -z 512 512 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_256x256@2x.png" >/dev/null
  sips -z 512 512 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_512x512.png" >/dev/null
  sips -z 1024 1024 "$REPO/assets/physlaunch.png" --out "$ICONSET/icon_512x512@2x.png" >/dev/null
  iconutil -c icns "$ICONSET" -o "$STAGE/physlaunch.icns"
  ICON="$STAGE/physlaunch.icns"
else
  ICON="$REPO/assets/physlaunch.png"
fi

if [[ "$OS" == "darwin" ]]; then
  jpackage --type app-image --name LauncherHub --app-version 1.0.0 --input "$STAGE/hub-input" --main-jar LauncherHub.jar --main-class physlaunch.LauncherHub --icon "$ICON" --mac-package-identifier com.physlaunch.launcher --dest "$STAGE/hub"
  jpackage --type app-image --name Setup --app-version 1.0.0 --input "$STAGE/setup-input" --main-jar Setup.jar --main-class physlaunch.Setup --icon "$ICON" --dest "$STAGE/setup"
else
  jpackage --type app-image --name LauncherHub --app-version 1.0.0 --input "$STAGE/hub-input" --main-jar LauncherHub.jar --main-class physlaunch.LauncherHub --icon "$ICON" --dest "$STAGE/hub"
  jpackage --type app-image --name Setup --app-version 1.0.0 --input "$STAGE/setup-input" --main-jar Setup.jar --main-class physlaunch.Setup --icon "$ICON" --dest "$STAGE/setup"
fi
if [[ "$OS" == "darwin" ]]; then
  mkdir -p "$DIST/LauncherHub" "$DIST/Setup"
  cp -R "$STAGE/hub/LauncherHub.app" "$DIST/LauncherHub/"
  cp -R "$STAGE/setup/Setup.app" "$DIST/Setup/"
  "$DIST/Setup/Setup.app/Contents/MacOS/Setup" --check-package
else
  cp -R "$STAGE/hub/LauncherHub"* "$DIST/LauncherHub"
  cp -R "$STAGE/setup/Setup"* "$DIST/Setup"
  "$DIST/Setup/bin/Setup" --check-package
fi
cp "$REPO/LICENSE" "$DIST/LICENSE"
cat > "$DIST/README.txt" <<'EOF'
Physlaunch release bundle

Run the Setup application to install Physlaunch for your account. Keep the Setup and LauncherHub folders beside one another until setup has finished.
The LauncherHub folder contains the game picker and the optional drive Runner.
EOF
ZIP="$ROOT/dist/Physlaunch-$TARGET.zip"
(cd "$DIST" && zip -qr "$ZIP" .)
echo "Release bundle created at $DIST"
