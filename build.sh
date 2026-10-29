#!/usr/bin/env bash
# Run from Git Bash on Windows: bash build.sh
set -euo pipefail

trap 'printf "\nBuild failed. If SustSos is running from target/, close it and retry.\n" >&2' ERR

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$project_dir"

case "${OSTYPE:-}" in
    msys*|cygwin*) ;;
    *) printf 'This script requires Git Bash on Windows.\n' >&2; exit 1 ;;
esac

for tool in mvn powershell.exe; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Required tool not found on PATH: %s\n' "$tool" >&2
        exit 1
    fi
done

printf 'Building JARs and Windows application image (including tests)...\n'
mvn --batch-mode clean package

printf '\nCreating Windows ZIP package...\n'
powershell.exe -NoProfile -NonInteractive -Command - <<'POWERSHELL'
$ErrorActionPreference = 'Stop'
try {
$appDirectory = Join-Path (Get-Location) 'target\jpackage-out\SustSos'
$archivePath = Join-Path (Get-Location) 'target\SustSos-windows.zip'
if (-not (Test-Path -LiteralPath (Join-Path $appDirectory 'SustSos.exe') -PathType Leaf)) {
    throw 'Build did not produce SustSos.exe'
}
Compress-Archive -Path (Join-Path $appDirectory '*') -DestinationPath $archivePath -Force
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}

POWERSHELL

printf '\nBuild complete.\n'
printf 'Windows ZIP: %s/target/SustSos-windows.zip\n' "$project_dir"
printf 'Application: %s/target/jpackage-out/SustSos/SustSos.exe\n' "$project_dir"
printf 'JARs:        %s/target/\n' "$project_dir"
