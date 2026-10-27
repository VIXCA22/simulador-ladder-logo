$ErrorActionPreference='Stop'
try {
 $dataFolder=Join-Path $PSScriptRoot 'datos'
 $portFile=Join-Path $dataFolder 'puerto.txt'
 if (-not (Test-Path -LiteralPath $portFile)) { Write-Host 'El simulador no está iniciado desde esta carpeta.';exit 0 }
 $port=[int](Get-Content -LiteralPath $portFile -Raw).Trim()
 try { $state=Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/info" -TimeoutSec 2 } catch { Write-Host 'El motor ya está cerrado.';exit 0 }
 if ($state.service -ne 'LOGO_LAD_PORTABLE' -or $state.folder -ne [IO.Path]::GetFullPath($PSScriptRoot)) { throw 'El puerto corresponde a otra aplicación o carpeta. No se ha cerrado.' }
 Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/shutdown" -Method Post -TimeoutSec 3 | Out-Null
 Write-Host 'Motor del simulador cerrado.'
} catch { Write-Host $_.Exception.Message -ForegroundColor Red;exit 1 }
