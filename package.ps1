$ErrorActionPreference = "Stop"

$name = Read-Host "请输入姓名"
$studentId = Read-Host "请输入学号"
$packageName = "$name$studentId"
$distDir = Join-Path $PSScriptRoot "dist"
$workDir = Join-Path $distDir $packageName

if (Test-Path $workDir) {
    Remove-Item -LiteralPath $workDir -Recurse -Force
}
New-Item -ItemType Directory -Path $workDir | Out-Null

.\gradlew.bat assembleDebug

Copy-Item -LiteralPath (Join-Path $PSScriptRoot "README.md") -Destination $workDir
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "docs") -Destination $workDir -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk") -Destination (Join-Path $workDir "DayMark-debug.apk")

$sourceDir = Join-Path $workDir "source"
New-Item -ItemType Directory -Path $sourceDir | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "app") -Destination $sourceDir -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "gradle") -Destination $sourceDir -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "build.gradle") -Destination $sourceDir
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "settings.gradle") -Destination $sourceDir
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "gradlew") -Destination $sourceDir
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "gradlew.bat") -Destination $sourceDir

Get-ChildItem -Path $sourceDir -Recurse -Directory -Filter build | Remove-Item -Recurse -Force
Get-ChildItem -Path $sourceDir -Recurse -Directory -Filter .gradle | Remove-Item -Recurse -Force

$zipPath = Join-Path $distDir "$packageName.zip"
if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -LiteralPath $workDir -DestinationPath $zipPath

$sizeMb = [Math]::Round((Get-Item $zipPath).Length / 1MB, 2)
Write-Host "已生成：$zipPath"
Write-Host "压缩包大小：$sizeMb MB"
