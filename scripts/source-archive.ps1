$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$package = Get-Content -LiteralPath (Join-Path $repoRoot 'package.json') -Raw | ConvertFrom-Json
$releaseRoot = Join-Path $repoRoot 'release'
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
$zipPath = Join-Path $releaseRoot "pagenote-$($package.version)-source.zip"
Add-Type -AssemblyName System.IO.Compression
$stream = [System.IO.File]::Open($zipPath, [System.IO.FileMode]::Create)
$archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = [System.Collections.Generic.List[string]]::new()
    foreach ($folder in @('licenses','src','desktop','mcp','skills','scripts','docs','locales','build','.github','examples','tests/fixtures')) {
        $path = Join-Path $repoRoot $folder
        if (Test-Path -LiteralPath $path) {
            Get-ChildItem -LiteralPath $path -Recurse -File | ForEach-Object { $files.Add($_.FullName) }
        }
    }
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'tests') -Filter '*.mjs' -File | ForEach-Object { $files.Add($_.FullName) }
    foreach ($name in @('AGENTS.md','.npmrc','.gitignore','.gitattributes','.prettierignore','.prettierrc.json','package.json','package-lock.json','index.html','server.mjs','projects.mjs','external-documents.mjs','convert-office.ps1','launch.mjs','start.cmd','README.md','README.en.md','CHANGELOG.md','KNOWN_ISSUES.md','LICENSE','LICENSING.md','SECURITY.md','THIRD-PARTY-NOTICES.txt')) {
        $files.Add((Join-Path $repoRoot $name))
    }
    Get-ChildItem -LiteralPath $repoRoot -Filter 'MCP*.md' -File | ForEach-Object { $files.Add($_.FullName) }
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'tests') -Filter 'default-word-keyboard.ps1' -File | ForEach-Object { $files.Add($_.FullName) }
    foreach ($file in $files) {
        $relative = $file.Substring($repoRoot.Length + 1).Replace('\','/')
        $entry = $archive.CreateEntry("pagenote/$relative", [System.IO.Compression.CompressionLevel]::Optimal)
        $inputStream = [System.IO.File]::OpenRead($file)
        $outputStream = $entry.Open()
        try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
    }
    Write-Output "Source archive: $zipPath ($($files.Count) files; user data, dependencies and release artifacts excluded)"
} finally { $archive.Dispose(); $stream.Dispose() }
