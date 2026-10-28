$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Split-Path -Parent $root
$build = Join-Path $root "build"
$stage = Join-Path $build "stage-win"
$dist = Join-Path $root "dist\windows-x64"

foreach ($path in @($stage, $dist)) {
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
}
$classes = Join-Path $build "classes"
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
$hubInput = Join-Path $stage "hub-input"
$setupInput = Join-Path $stage "setup-input"
New-Item -ItemType Directory -Force -Path $hubInput, $setupInput, $classes, $dist, (Join-Path $stage "hub"), (Join-Path $stage "setup") | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $root "src\main\java") -Filter "*.java" -Recurse | ForEach-Object FullName
& javac --release 17 -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

foreach ($name in @("hub-input", "setup-input")) {
    $target = if ($name -eq "hub-input") { $hubInput } else { $setupInput }
    New-Item -ItemType Directory -Force -Path (Join-Path $target "physlaunch") | Out-Null
    if ($name -eq "hub-input") {
        Copy-Item (Join-Path $classes "physlaunch\LauncherHub*.class") (Join-Path $target "physlaunch")
    } else {
        Copy-Item (Join-Path $classes "physlaunch\Setup*.class") (Join-Path $target "physlaunch")
    }
    Copy-Item (Join-Path $repo "assets\physlaunch.png") $target
}

& jar --create --file (Join-Path $hubInput "LauncherHub.jar") --main-class physlaunch.LauncherHub -C $hubInput .
if ($LASTEXITCODE -ne 0) { throw "Could not create LauncherHub.jar" }
& jar --create --file (Join-Path $setupInput "Setup.jar") --main-class physlaunch.Setup -C $setupInput .
if ($LASTEXITCODE -ne 0) { throw "Could not create Setup.jar" }
Remove-Item -LiteralPath (Join-Path $hubInput "physlaunch") -Recurse -Force
Remove-Item -LiteralPath (Join-Path $setupInput "physlaunch") -Recurse -Force

& jpackage --type app-image --name LauncherHub --app-version 1.0.0 --input $hubInput --main-jar LauncherHub.jar --main-class physlaunch.LauncherHub --icon (Join-Path $repo "assets\physlaunch.ico") --dest (Join-Path $stage "hub")
if ($LASTEXITCODE -ne 0) { throw "Could not package LauncherHub" }
& jpackage --type app-image --name Setup --app-version 1.0.0 --input $setupInput --main-jar Setup.jar --main-class physlaunch.Setup --icon (Join-Path $repo "assets\physlaunch.ico") --dest (Join-Path $stage "setup")
if ($LASTEXITCODE -ne 0) { throw "Could not package Setup" }

Copy-Item (Join-Path $stage "hub\LauncherHub") (Join-Path $dist "LauncherHub") -Recurse
Copy-Item (Join-Path $stage "setup\Setup") (Join-Path $dist "Setup") -Recurse
Copy-Item (Join-Path $repo "LICENSE") $dist
$setupExe = Join-Path $dist "Setup\Setup.exe"
& $setupExe --check-package
if ($LASTEXITCODE -ne 0) { throw "Setup could not locate the LauncherHub bundle" }
@"
Physlaunch release bundle

Run Setup\Setup.exe to install Physlaunch for your account. Keep the Setup and LauncherHub folders beside one another until setup has finished.
LauncherHub contains the game picker and the optional drive Runner.
"@ | Set-Content -LiteralPath (Join-Path $dist "README.txt") -Encoding UTF8

$archive = Join-Path $root "dist\Physlaunch-windows-x64.zip"
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
Compress-Archive -Path (Join-Path $dist "*") -DestinationPath $archive -CompressionLevel Optimal
Write-Host "Created $archive"
