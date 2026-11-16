param([switch]$RenderAtlas,[switch]$IncludeBlocks)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot = Join-Path $PSScriptRoot '../src/main/resources/assets/spawn_9b5fbb9'
$auditIssues = [System.Collections.Generic.List[string]]::new()
$auditItems = [System.Collections.Generic.List[object]]::new()
foreach ($definition in Get-ChildItem -LiteralPath (Join-Path $assetRoot 'items') -Filter *.json | Sort-Object Name) {
    $itemName = $definition.BaseName
    $itemDefinition = Get-Content -LiteralPath $definition.FullName -Raw | ConvertFrom-Json
    $modelName = $itemDefinition.model.model
    if ($modelName -ne "spawn_9b5fbb9:item/$itemName") { $auditIssues.Add("$itemName has an unexpected item definition"); continue }
    $modelFile = Join-Path $assetRoot "models/item/$itemName.json"
    if (-not (Test-Path -LiteralPath $modelFile)) { $auditIssues.Add("$itemName lacks a model"); continue }
    $model = Get-Content -LiteralPath $modelFile -Raw | ConvertFrom-Json
    if ($model.textures.layer0 -ne "spawn_9b5fbb9:item/$itemName") { $auditIssues.Add("$itemName still shares a placeholder or block-model icon"); continue }
    $textureFile = Join-Path $assetRoot "textures/item/$itemName.png"
    if (-not (Test-Path -LiteralPath $textureFile)) { $auditIssues.Add("$itemName lacks its texture"); continue }
    $bitmap = [System.Drawing.Bitmap]::new($textureFile)
    try {
        if ($bitmap.Width -ne 16 -or $bitmap.Height -ne 16) { $auditIssues.Add("$itemName is not 16 by 16") }
        $opaque = 0; $transparent = 0
        for ($x = 0; $x -lt 16; $x++) { for ($y = 0; $y -lt 16; $y++) { if ($bitmap.GetPixel($x,$y).A -eq 0) { $transparent++ } else { $opaque++ } } }
        if ($opaque -lt 6 -or $transparent -lt 16) { $auditIssues.Add("$itemName lacks a usable transparent icon silhouette") }
    } finally { $bitmap.Dispose() }
    $auditItems.Add([pscustomobject]@{ Name=$itemName; Path=$textureFile })
}
$language = Get-Content -LiteralPath (Join-Path $assetRoot 'lang/en_us.json') -Raw | ConvertFrom-Json
foreach ($item in $auditItems) { if ($null -eq $language.PSObject.Properties["item.spawn_9b5fbb9.$($item.Name)"] -and $null -eq $language.PSObject.Properties["block.spawn_9b5fbb9.$($item.Name)"]) { $auditIssues.Add("$($item.Name) has no localized name") } }
$completeItemCount=$auditItems.Count
foreach ($cutoutFile in Get-ChildItem -LiteralPath (Join-Path $assetRoot 'textures/block') -Filter *.png | Where-Object { $_.BaseName.StartsWith('wild_') -or $_.BaseName.EndsWith('_crop') }) {
    $cutoutBitmap=[System.Drawing.Bitmap]::new($cutoutFile.FullName)
    try { if ($cutoutBitmap.GetPixel(0,0).A -ne 0) { $auditIssues.Add("$($cutoutFile.BaseName) plant texture has an opaque background") } } finally { $cutoutBitmap.Dispose() }
}
if ($IncludeBlocks) { foreach ($blockTexture in Get-ChildItem -LiteralPath (Join-Path $assetRoot 'textures/block') -Filter *.png | Sort-Object Name) { $auditItems.Add([pscustomobject]@{ Name="block / $($blockTexture.BaseName)"; Path=$blockTexture.FullName }) } }
if ($RenderAtlas -and $auditItems.Count -gt 0) {
    $atlasDirectory = Join-Path $PSScriptRoot '../run/qube/item-atlas'
    New-Item -ItemType Directory -Force -Path $atlasDirectory | Out-Null
    $atlasFont = [System.Drawing.Font]::new('Consolas',9)
    $atlasBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(231,230,211))
    try {
        for ($page = 0; $page -lt [math]::Ceiling($auditItems.Count / 36.0); $page++) {
            $atlasBitmap = [System.Drawing.Bitmap]::new(1032,792)
            $atlasGraphics = [System.Drawing.Graphics]::FromImage($atlasBitmap)
            try {
                $atlasGraphics.Clear([System.Drawing.Color]::FromArgb(25,34,37))
                $atlasGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $atlasGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                for ($index = 0; $index -lt 36 -and $page*36+$index -lt $auditItems.Count; $index++) {
                    $item = $auditItems[$page*36+$index]; $x = ($index % 6)*172; $y = [math]::Floor($index/6)*132
                    $icon = [System.Drawing.Bitmap]::new($item.Path)
                    try { $atlasGraphics.DrawImage($icon,[System.Drawing.Rectangle]::new($x+38,$y+5,96,96),0,0,16,16,[System.Drawing.GraphicsUnit]::Pixel) } finally { $icon.Dispose() }
                    $label = $item.Name.Replace('_',' ')
                    if ($label.Length -gt 24) { $breakAt=$label.LastIndexOf(' ',23);if ($breakAt -lt 1) {$breakAt=24};$label = $label.Substring(0,$breakAt) + "`n" + $label.Substring($breakAt).Trim() }
                    $atlasGraphics.DrawString($label,$atlasFont,$atlasBrush,$x+4,$y+103)
                }
                $atlasFile = Join-Path $atlasDirectory "items-$page.png"
                $atlasBitmap.Save($atlasFile,[System.Drawing.Imaging.ImageFormat]::Png)
                "Atlas: $atlasFile"
            } finally { $atlasGraphics.Dispose(); $atlasBitmap.Dispose() }
        }
    } finally { $atlasFont.Dispose(); $atlasBrush.Dispose() }
}
if ($auditIssues.Count -gt 0) { $auditIssues | Select-Object -First 16; throw "$($auditIssues.Count) item asset issue(s); $completeItemCount complete item icons" }
"PASS: $completeItemCount localized item definitions, distinct bindings, 16 by 16 PNGs, and transparent silhouettes."
