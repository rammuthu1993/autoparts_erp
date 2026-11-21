param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$OutputZip = (Join-Path (Split-Path -Parent (Get-Location)) 'autoparts-erp-source.zip')
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path $ProjectRoot).Path
$out = [System.IO.Path]::GetFullPath($OutputZip)
if ($out.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Output archive must be outside the project directory.'
}
if (Test-Path $out) { Remove-Item -LiteralPath $out -Force }

$exclude = @(
    '\\.git\\', '\\target\\', '\\build\\', '\\logs\\',
    '\\config\\application-secrets\.properties$', '\\.env($|\.)',
    '\\.log$', '\\.zip$', '\\.class$', '\\.jar$'
)
$files = Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object {
    $relative = $_.FullName.Substring($root.Length)
    -not ($exclude | Where-Object { $relative -match $_ })
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($out, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($root.Length).TrimStart('\\','/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, $file.FullName, $relative,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
finally { $archive.Dispose() }
Write-Host "Created source archive: $out"
Write-Host "Included $($files.Count) files; excluded .git, build output, logs, local secrets, env files, binaries, and nested archives."
