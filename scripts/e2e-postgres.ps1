#requires -Version 5.1
<#
.SYNOPSIS
  Rejoue les scénarios Playwright sur PostgreSQL avec des migrations Flyway réelles, dans une base jetable.

.DESCRIPTION
  - N'accepte que le cluster de test isolé 127.0.0.1:15432 (utilisateur et_test). Le port 5432 n'est jamais utilisé.
  - Crée la base jetable et_e2e_db (supprimée à la fin), laisse Flyway appliquer V1..dernière (profil dev, Hibernate validate).
  - Aucun seeder : le compte super administrateur est créé explicitement en SQL, avec un mot de passe aléatoire
    gardé en mémoire (jamais écrit sur disque ni affiché).
  - Démarre le backend (port 8080, attendu par proxy.conf.json) et Angular (port 4200), puis lance `npm run e2e`.
  - Arrête les processus qui écoutent sur 8080 et 4200 (libres au départ, vérifié).

  Prérequis : le cluster est démarré (voir docs/start-local.md), les ports 8080 et 4200 sont libres.

.EXAMPLE
  .\scripts\e2e-postgres.ps1
  .\scripts\e2e-postgres.ps1 -Spec bookings.spec.ts
#>
param(
    [string]$Spec = '',
    [int]$Workers = 0,
    [string]$ClusterRoot = (Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\pg-test-cluster'),
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot',
    [string]$NodeHome = (Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\node-v26.12.1-win-x64'),
    [string]$Browser = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$dbName = 'et_e2e_db'
$logDir = Join-Path ([IO.Path]::GetTempPath()) ("elite-e2e-pg\{0:yyyyMMdd-HHmmss}" -f (Get-Date))
New-Item -ItemType Directory -Force $logDir | Out-Null

function New-RandomSecret([int]$bytes) {
    $b = [byte[]]::new($bytes); $r = [Security.Cryptography.RandomNumberGenerator]::Create(); $r.GetBytes($b); $r.Dispose()
    [Convert]::ToBase64String($b).Replace('+', 'A').Replace('/', 'B').Replace('=', 'C')
}
function Test-Port([int]$port) { [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) }
function Stop-Listener([int]$port) {
    $c = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) { Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue }
}
function Stop-Tree($proc) {
    if ($proc -and -not $proc.HasExited) { & taskkill.exe /PID $proc.Id /T /F *> $null }
}
function Invoke-Psql([string]$database, [string[]]$arguments) {
    # client_min_messages=warning : le NOTICE « base inexistante » de DROP IF EXISTS ne doit pas être traité comme une erreur par PowerShell.
    $env:PGPASSWORD = $script:pgPassword; $env:PGCONNECT_TIMEOUT = '10'; $env:PGOPTIONS = '-c client_min_messages=warning'
    try { & "$PgBin\psql.exe" -w -P pager=off -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 15432 -U et_test -d $database @arguments }
    finally { Remove-Item Env:\PGPASSWORD, Env:\PGOPTIONS -ErrorAction SilentlyContinue }
    if ($LASTEXITCODE -ne 0) { throw "psql a échoué ($database)." }
}

if (-not (Test-Path "$ClusterRoot\pgpass.local")) { throw "Cluster de test introuvable : $ClusterRoot (voir docs/start-local.md)." }
if (-not (Test-Port 15432)) { throw 'Le cluster PostgreSQL de test ne répond pas sur 127.0.0.1:15432.' }
foreach ($p in 8080, 4200) { if (Test-Port $p) { throw "Le port $p est déjà occupé ; l'identifier avant de continuer." } }
$script:pgPassword = [IO.File]::ReadAllText("$ClusterRoot\pgpass.local")

$identity = Invoke-Psql 'postgres' @('-At', '-c', "select inet_server_port() || '|' || current_user")
if ($identity -ne '15432|et_test') { throw "Identité inattendue du serveur : $identity" }

$adminEmail = 'e2e.admin@example.test'
$adminPassword = New-RandomSecret 18
$exit = 1
try {
    Invoke-Psql 'postgres' @('-c', "DROP DATABASE IF EXISTS $dbName WITH (FORCE)", '-c', "CREATE DATABASE $dbName")

    $env:JAVA_HOME = $Jdk; $env:Path = "$Jdk\bin;$NodeHome;$env:Path"
    $env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:15432/$dbName"
    $env:DATABASE_USERNAME = 'et_test'; $env:DATABASE_PASSWORD = $script:pgPassword
    $env:JWT_SECRET = New-RandomSecret 48
    $env:SERVER_PORT = '8080'
    $backend = Start-Process -FilePath "$repo\backend\mvnw.cmd" -ArgumentList '-DskipTests', 'spring-boot:run' `
        -WorkingDirectory "$repo\backend" -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "$logDir\backend.log" -RedirectStandardError "$logDir\backend.err.log"
    foreach ($name in 'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD', 'JWT_SECRET', 'SERVER_PORT') { Remove-Item "Env:\$name" -ErrorAction SilentlyContinue }

    $ready = $false
    for ($i = 0; $i -lt 90 -and -not $ready; $i++) {
        Start-Sleep -Seconds 3
        if ($backend.HasExited) { throw "Le backend s'est arrêté (code $($backend.ExitCode)) ; voir $logDir\backend.log" }
        try { $ready = (Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2).status -eq 'UP' } catch { }
    }
    if (-not $ready) { throw "Le backend n'est pas devenu UP ; voir $logDir\backend.log" }
    Write-Host 'Backend UP (Flyway + Hibernate validate sur PostgreSQL).'

    # Compte administrateur explicite : hash bcrypt calculé par PostgreSQL (pgcrypto) ; mot de passe jamais journalisé.
    $sql = @'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
WITH u AS (
  INSERT INTO users (id, email, password_hash, enabled)
  VALUES (gen_random_uuid(), :'admin_email', crypt(:'admin_pw', gen_salt('bf', 10)), true) RETURNING id
), r AS (
  INSERT INTO user_roles (user_id, role_key) SELECT id, 'SUPER_ADMIN' FROM u
)
INSERT INTO employees (id, employee_number, job_title, first_name, last_name, phone)
SELECT id, 'EMP-E2E-ADMIN', 'Super administrateur', 'E2E', 'Admin', '+216 20 000 000' FROM u;
'@
    $env:PGPASSWORD = $script:pgPassword
    $sql | & "$PgBin\psql.exe" -w -q -v ON_ERROR_STOP=1 -v "admin_email=$adminEmail" -v "admin_pw=$adminPassword" -h 127.0.0.1 -p 15432 -U et_test -d $dbName | Out-Null
    $psqlExit = $LASTEXITCODE
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    if ($psqlExit -ne 0) { throw 'Création du compte administrateur E2E impossible.' }

    $front = Start-Process -FilePath "$NodeHome\npm.cmd" -ArgumentList 'run', 'start', '--', '--host', '127.0.0.1' `
        -WorkingDirectory "$repo\frontend" -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "$logDir\frontend.log" -RedirectStandardError "$logDir\frontend.err.log"
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
        Start-Sleep -Seconds 3
        if ($front.HasExited) { throw "Angular s'est arrêté (code $($front.ExitCode)) ; voir $logDir\frontend.log" }
        try { $ready = (Invoke-WebRequest 'http://127.0.0.1:4200/api/health' -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch { }
    }
    if (-not $ready) { throw "Angular ou son proxy ne répond pas ; voir $logDir\frontend.log" }
    Write-Host 'Angular UP, proxy /api vers le backend PostgreSQL vérifié.'

    # Préchauffage : le premier chargement des pages déclenche l'optimisation des dépendances de Vite et le premier passage
    # de la JVM sur la connexion ; ce coût ne doit pas être imputé aux délais des tests (document servi en 7,9 s sous charge).
    foreach ($path in '/', '/auth/login', '/auth/admin-login', '/vehicles', '/api/vehicles') {
        try { Invoke-WebRequest "http://127.0.0.1:4200$path" -UseBasicParsing -TimeoutSec 60 | Out-Null } catch { Write-Warning "Préchauffage $path : $($_.Exception.Message)" }
    }

    $env:E2E_ADMIN_EMAIL = $adminEmail; $env:E2E_ADMIN_PASSWORD = $adminPassword
    if (Test-Path $Browser) { $env:PLAYWRIGHT_EXECUTABLE_PATH = $Browser }
    $env:PLAYWRIGHT_JSON_OUTPUT_NAME = "$logDir\playwright-report.json"
    Push-Location "$repo\frontend"
    try {
        $e2eArgs = @('run', 'e2e', '--', '--reporter=list,json')
        if ($Workers -gt 0) { $e2eArgs += "--workers=$Workers" }
if ($Spec) { $e2eArgs += "e2e/$Spec" }
        & "$NodeHome\npm.cmd" @e2eArgs
        $exit = $LASTEXITCODE
        # Traces et captures des échecs : conservées hors du dépôt (test-results est vidé à chaque exécution).
        if ($exit -ne 0 -and (Test-Path 'test-results')) { Copy-Item 'test-results' "$logDir\test-results" -Recurse -Force }
    } finally { Pop-Location }
} finally {
    foreach ($name in 'E2E_ADMIN_EMAIL', 'E2E_ADMIN_PASSWORD', 'PLAYWRIGHT_EXECUTABLE_PATH', 'PLAYWRIGHT_JSON_OUTPUT_NAME') { Remove-Item "Env:\$name" -ErrorAction SilentlyContinue }
    Stop-Tree $front; Stop-Tree $backend
    Stop-Listener 4200; Stop-Listener 8080
    Start-Sleep -Seconds 3
    try { Invoke-Psql 'postgres' @('-c', "DROP DATABASE IF EXISTS $dbName WITH (FORCE)") } catch { Write-Warning "Base $dbName non supprimée : $($_.Exception.Message)" }
    Write-Host "Journaux et rapports : $logDir"
}
exit $exit
