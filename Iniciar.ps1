param([switch]$NoBrowser,[int]$Port=8768,[string]$MotorPath='')
$ErrorActionPreference='Stop'
try {
 $appFolder=[IO.Path]::GetFullPath($PSScriptRoot)
 if (-not (Test-Path -LiteralPath (Join-Path $appFolder 'app/simulador.jar'))) { throw 'Falta app/simulador.jar. Extrae todo el ZIP antes de iniciar.' }
 $dataFolder=Join-Path $appFolder 'datos'
 [IO.Directory]::CreateDirectory($dataFolder) | Out-Null
 function Get-Ready([int]$candidatePort) {
  try { return (Invoke-RestMethod -Uri "http://127.0.0.1:$candidatePort/api/info" -TimeoutSec 2) } catch { return $null }
 }
 $portFile=Join-Path $dataFolder 'puerto.txt'
 if (Test-Path -LiteralPath $portFile) {
  $savedPort=0
  if ([int]::TryParse((Get-Content -LiteralPath $portFile -Raw).Trim(),[ref]$savedPort)) {
   $ready=Get-Ready $savedPort
   if ($ready -and $ready.service -eq 'LOGO_LAD_PORTABLE' -and $ready.folder -eq $appFolder) {
    $address="http://127.0.0.1:$savedPort/"
    Write-Host "Simulador disponible: $address"
    if (-not $NoBrowser) { Start-Process -FilePath $address }
    exit 0
   }
  }
 }
 $candidates=@($MotorPath,(Join-Path $appFolder 'MotorSiemens'),$env:LOGO_INSTALL,(Join-Path $env:ProgramFiles 'Siemens/LOGOComfort_V9.0'))
 $engine=$null
 foreach ($candidate in $candidates) {
  if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'jre/bin/java.exe')) -and (Test-Path -LiteralPath (Join-Path $candidate 'lib/classes.jar'))) { $engine=[IO.Path]::GetFullPath($candidate); break }
 }
 if (-not $engine) { throw 'No se encuentra el motor. Usa el ZIP con MotorSiemens incluido, o configura LOGO_INSTALL con la carpeta de tu LOGO! V9.' }
 $chosenPort=0
 for($candidatePort=$Port;$candidatePort -lt $Port+20;$candidatePort++) {
  $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$candidatePort)
  try { $listener.Start();$listener.Stop();$chosenPort=$candidatePort;break } catch { $listener.Stop() }
 }
 if ($chosenPort -eq 0) { throw 'No hay un puerto local disponible. Inicia con otro -Port.' }
 $classPath=@((Join-Path $appFolder 'app/simulador.jar'),$engine,(Join-Path $engine 'lib/*'),(Join-Path $engine 'lib/javafx/lib/*'),(Join-Path $engine 'lib/cloud/9.0.0.0.0/*')) -join ';'
 $profileFolder=Join-Path $dataFolder 'perfil'
 [IO.Directory]::CreateDirectory($profileFolder) | Out-Null
 $javaArgs=@('-Xss32m','-Xmx768m','--add-exports=java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED','--add-exports=java.base/sun.security.x509=ALL-UNNAMED','--add-exports=java.base/sun.security.util=ALL-UNNAMED','-Djava.awt.headless=true',('"-Duser.home='+$profileFolder+'"'),'-cp',('"'+$classPath+'"'),'ServidorPortable',('"'+$appFolder+'"'),[string]$chosenPort)
 $log=Join-Path $dataFolder 'motor.log';$errors=Join-Path $dataFolder 'motor_errores.log'
 $process=Start-Process -FilePath (Join-Path $engine 'jre/bin/java.exe') -ArgumentList $javaArgs -WorkingDirectory $engine -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $errors -PassThru
 [IO.File]::WriteAllText((Join-Path $dataFolder 'motor.pid'),[string]$process.Id)
 [IO.File]::WriteAllText($portFile,[string]$chosenPort)
 $address="http://127.0.0.1:$chosenPort/"
 $ready=$null
 for($attempt=0;$attempt -lt 80;$attempt++) {
  Start-Sleep -Milliseconds 250
  $ready=Get-Ready $chosenPort
  if ($ready -and $ready.service -eq 'LOGO_LAD_PORTABLE') { break }
  if ($process.HasExited) { throw "El motor no pudo iniciar. Consulta $errors" }
 }
 if (-not $ready -or $ready.service -ne 'LOGO_LAD_PORTABLE') { throw "No responde el motor. Consulta $errors" }
 Write-Host "Simulador disponible: $address"
 if (-not $NoBrowser) { Start-Process -FilePath $address }
 exit 0
} catch { Write-Host ('ERROR: '+$_.Exception.Message) -ForegroundColor Red; exit 1 }
