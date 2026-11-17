#requires -Version 5.1
<#
.SYNOPSIS
  Exécute `mvnw -Ppostgres-test verify` sur une base et_test_db recréée vide à chaque passage.

.DESCRIPTION
  - N'accepte que le cluster de test isolé 127.0.0.1:15432 (utilisateur et_test). Le port 5432 n'est jamais utilisé.
  - Supprime puis recrée uniquement la base et_test_db de ce cluster : aucune donnée laissée par un passage
    précédent (verify12 : doublons users_email_key sur une base déjà utilisée) ne peut influencer le suivant.
  - Flyway applique V1..dernière sur la base vide ; Surefire puis Failsafe partagent cette base, comme en CI.
  - Le mot de passe est lu dans pgpass.local, transmis par variable d'environnement et jamais affiché.

  Prérequis : le cluster est démarré (voir docs/start-local.md).

.EXAMPLE
  .\scripts\test-backend-postgres.ps1
#>
param(
    [string]$ClusterRoot = (Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\pg-test-cluster'),
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$dbName = 'et_test_db'
$logFile = Join-Path ([IO.Path]::GetTempPath()) ("elite-backend-pg-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))

function Invoke-Psql([string]$database, [string[]]$arguments) {
    $env:PGPASSWORD = $script:pgPassword; $env:PGCONNECT_TIMEOUT = '10'; $env:PGOPTIONS = '-c client_min_messages=warning'
    try { & "$PgBin\psql.exe" -w -P pager=off -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 15432 -U et_test -d $database @arguments }
    finally { Remove-Item Env:\PGPASSWORD, Env:\PGOPTIONS -ErrorAction SilentlyContinue }
    if ($LASTEXITCODE -ne 0) { throw "psql a échoué ($database)." }
}

if (-not (Test-Path "$ClusterRoot\pgpass.local")) { throw "Cluster de test introuvable : $ClusterRoot (voir docs/start-local.md)." }
if (-not (Get-NetTCPConnection -State Listen -LocalPort 15432 -ErrorAction SilentlyContinue)) {
    throw 'Le cluster PostgreSQL de test ne répond pas sur 127.0.0.1:15432.'
}
$script:pgPassword = [IO.File]::ReadAllText("$ClusterRoot\pgpass.local")

$identity = Invoke-Psql 'postgres' @('-At', '-c', "select inet_server_port() || '|' || current_user")
if ($identity -ne '15432|et_test') { throw "Identité inattendue du serveur : $identity" }

Invoke-Psql 'postgres' @('-c', "DROP DATABASE IF EXISTS $dbName WITH (FORCE)", '-c', "CREATE DATABASE $dbName")
$tables = (Invoke-Psql $dbName @('-At', '-c', "select count(*) from information_schema.tables where table_schema = 'public'") | Out-String).Trim()
if ($tables -ne '0') { throw "$dbName n'est pas vide après recréation ($tables tables)." }
Write-Host "$dbName recréée vide ; journal : $logFile"

$env:JAVA_HOME = $Jdk
$env:ELITE_POSTGRES_TEST = 'true'
$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:15432/$dbName"
$env:DATABASE_USERNAME = 'et_test'
$env:DATABASE_PASSWORD = $script:pgPassword
try {
    Push-Location "$repo\backend"
    # Les avertissements Maven/JVM (ex. Mockito sur stderr) ne doivent pas interrompre le script : le code de sortie fait foi.
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & "$repo\backend\mvnw.cmd" -B -ntp -Ppostgres-test verify 2>&1 | Tee-Object -FilePath $logFile
    }
    finally { $ErrorActionPreference = $previousErrorAction }
    $exit = $LASTEXITCODE
}
finally {
    Pop-Location
    foreach ($name in 'ELITE_POSTGRES_TEST', 'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD') { Remove-Item "Env:\$name" -ErrorAction SilentlyContinue }
}
exit $exit
