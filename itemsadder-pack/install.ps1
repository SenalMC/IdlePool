param(
    [Parameter(Mandatory = $true)]
    [string]$ServerDirectory
)

$source = Join-Path $PSScriptRoot 'contents\idlepool'
$server = [System.IO.Path]::GetFullPath($ServerDirectory)
$target = Join-Path $server 'plugins\ItemsAdder\contents\idlepool'

if (-not (Test-Path -LiteralPath $source)) {
    throw "IdlePool contents directory not found: $source"
}

New-Item -ItemType Directory -Force -Path $target | Out-Null
Get-ChildItem -LiteralPath $source -Force | Copy-Item -Destination $target -Recurse -Force

# rc.2 retired the font-image container backgrounds. Remove only those known legacy files
# so an update cannot leave them in the generated client resource pack.
$legacyGui = Join-Path $target 'textures\font\gui'
@('pool_menu.png', 'admin_menu.png', 'inbox_menu.png') | ForEach-Object {
    $legacyFile = Join-Path $legacyGui $_
    if (Test-Path -LiteralPath $legacyFile) {
        Remove-Item -LiteralPath $legacyFile -Force
    }
}
if ((Test-Path -LiteralPath $legacyGui) -and -not (Get-ChildItem -LiteralPath $legacyGui -Force)) {
    Remove-Item -LiteralPath $legacyGui
}

Write-Host "IdlePool ItemsAdder content installed to: $target"
Write-Host 'Run /iazip in the server console to rebuild the resource pack.'
