$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = Split-Path -Parent $projectRoot
$publishDirectory = Join-Path $repositoryRoot "dist\setup-publish"
$outputDirectory = Join-Path $repositoryRoot "dist"
$setupExe = Join-Path $outputDirectory "PhyslaunchSetup.exe"

New-Item -ItemType Directory -Force -Path $publishDirectory, $outputDirectory | Out-Null

& dotnet publish (Join-Path $projectRoot "Physlaunch.SetupBootstrap.csproj") `
    --configuration Release `
    --runtime win-x64 `
    --self-contained true `
    --output $publishDirectory `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true
if ($LASTEXITCODE -ne 0) {
    throw "Could not publish the standalone Setup executable."
}

$publishedExe = Join-Path $publishDirectory "PhyslaunchSetup.exe"
if (!(Test-Path -LiteralPath $publishedExe -PathType Leaf)) {
    throw "The standalone Setup executable was not created."
}

Copy-Item -LiteralPath $publishedExe -Destination $setupExe -Force
& $setupExe --check-package
if ($LASTEXITCODE -ne 0) {
    throw "The Setup executable is missing one or more embedded application files."
}

Write-Host "Created $setupExe"
