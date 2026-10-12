param([string]$MotorPath=$env:LOGO_INSTALL)
$ErrorActionPreference='Stop'
if (-not $MotorPath) { $MotorPath=Join-Path $env:ProgramFiles 'Siemens/LOGOComfort_V9.0' }
$sdk=[IO.Path]::GetFullPath($MotorPath)
$javac=Join-Path $sdk 'jre/bin/javac.exe'
$jar=Join-Path $sdk 'jre/bin/jar.exe'
if (-not (Test-Path -LiteralPath $javac)) { throw 'Se necesita LOGO! Soft Comfort V9 con javac incluido; indica -MotorPath.' }
$build=Join-Path $PSScriptRoot 'build/classes'
[IO.Directory]::CreateDirectory($build) | Out-Null
$sources=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'Fuentes') -Filter '*.java' | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -cp (Join-Path $sdk 'lib/*') -d $build $sources
if ($LASTEXITCODE -ne 0) { throw 'No se ha podido compilar el simulador.' }
& $jar --create --file (Join-Path $PSScriptRoot 'app/simulador.jar') -C $build .
if ($LASTEXITCODE -ne 0) { throw 'No se ha podido generar el JAR; cierra el motor antes de compilar.' }
Write-Host 'Simulador compilado.'
