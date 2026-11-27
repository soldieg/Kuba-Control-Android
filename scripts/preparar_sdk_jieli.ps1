# Download only the two required binary libraries from the official JieLi repository.
# Uses published Git blob SHA-1 to detect HTML, Git LFS pointers, or replacement files.
param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$dest = Join-Path $ProjectRoot 'app\libs'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
function Git-BlobSha1([byte[]]$data) {
    $prefix = [Text.Encoding]::ASCII.GetBytes(('blob {0}' -f $data.Length) + [char]0)
    $comb = New-Object byte[] ($prefix.Length + $data.Length)
    [Array]::Copy($prefix,0,$comb,0,$prefix.Length)
    [Array]::Copy($data,0,$comb,$prefix.Length,$data.Length)
    $sha = [Security.Cryptography.SHA1]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($comb))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
$files = @(
    @{Name='jl_bluetooth_rcsp_V4.2.0_40250-release.aar'; Blob='96be927894a69eb4af1fcc7dc1191a337360f86e'},
    @{Name='jldecryption_v0.4-release.aar'; Blob='fd933770e05afe815240922641a76fe62be7778b'}
)
foreach ($lib in $files) {
    $f=Join-Path $dest $lib.Name
    $needsDownload = $true
    if (Test-Path -LiteralPath $f) {
        $raw=[IO.File]::ReadAllBytes($f)
        if ((Git-BlobSha1 $raw) -eq $lib.Blob) { $needsDownload=$false }
    }
    if ($needsDownload) {
        $url='https://raw.githubusercontent.com/Jieli-Tech/Android-JL_Bluetooth/main/libs/' + $lib.Name
        $tmp = $f + '.partial'
        Write-Host "[JIELI] Baixando $($lib.Name) da organizacao oficial..."
        Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing -TimeoutSec 180
        $raw=[IO.File]::ReadAllBytes($tmp)
        $h = Git-BlobSha1 $raw
        if ($h -ne $lib.Blob) {
            Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
            throw "Integridade SHA-1 Git inesperada para $($lib.Name): $h. AAR nao foi instalado."
        }
        if ($raw.Length -lt 4 -or $raw[0] -ne 80 -or $raw[1] -ne 75) { throw 'AAR nao e um ZIP valido' }
        Move-Item -LiteralPath $tmp -Destination $f -Force
    }
    Write-Host "[OK] $($lib.Name) - SHA1 Git verificado" -ForegroundColor Green
}
Write-Host '[JIELI] Somente bibliotecas de controle e autenticacao RCSP; sem OTA.'
