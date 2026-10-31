#requires -Version 5.1
<#
.SYNOPSIS
  Restaure une sauvegarde pg_dump dans une NOUVELLE base (jamais dans une base existante).

.DESCRIPTION
  - Refuse si la base cible existe déjà : elle n'est jamais écrasée.
  - Refuse un serveur non local sans -AllowRemote.
  - Vérifie la somme de contrôle SHA-256 si le fichier .sha256 est présent.
  - Avec -CompareWith <base source>, compare le nombre de lignes de chaque table (même serveur) puis
    supprime la base restaurée si -DropAfter est précisé.
  - Le mot de passe se fournit par PGPASSWORD ou pgpass, jamais en argument.

.EXAMPLE
  .\scripts\db-restore.ps1 -BackupFile D:\sauvegardes\elite.dump -TargetDatabase elite_restore_check -User et_test -Port 15432 -CompareWith et_test_db -DropAfter
#>
param(
    [Parameter(Mandatory)][string]$BackupFile,
    [Parameter(Mandatory)][string]$TargetDatabase,
    [Parameter(Mandatory)][string]$User,
    [string]$CompareWith = '',
    [switch]$DropAfter,
    [switch]$AllowRemote,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin'
)
$ErrorActionPreference = 'Stop'
if ($TargetDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'Nom de base cible invalide.' }
if (-not $AllowRemote -and $HostName -notin '127.0.0.1', 'localhost', '::1') { throw 'Serveur non local : ajouter -AllowRemote après vérification.' }
if (-not (Test-Path $BackupFile)) { throw "Sauvegarde introuvable : $BackupFile" }

$sumFile = "$BackupFile.sha256"
if (Test-Path $sumFile) {
    $expected = ((Get-Content $sumFile -Raw).Trim() -split '\s+')[0]
    if ((Get-FileHash $BackupFile -Algorithm SHA256).Hash -ne $expected) { throw 'Somme de contrôle SHA-256 invalide : sauvegarde altérée.' }
    Write-Host 'Somme de contrôle SHA-256 valide.'
} else { Write-Warning 'Aucun fichier .sha256 : intégrité non vérifiée.' }

$env:PGCONNECT_TIMEOUT = '10'
function Invoke-Psql([string]$db, [string]$sql) {
    $out = & "$PgBin\psql.exe" -w -At -P pager=off -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $User -d $db -c $sql
    if ($LASTEXITCODE -ne 0) { throw "psql a échoué ($db)." }
    $out
}

if (Invoke-Psql 'postgres' "select 1 from pg_database where datname = '$TargetDatabase'") {
    throw "La base '$TargetDatabase' existe déjà : elle ne sera pas écrasée."
}
Invoke-Psql 'postgres' "CREATE DATABASE $TargetDatabase" | Out-Null
try {
    & "$PgBin\pg_restore.exe" -w --no-owner --no-privileges --exit-on-error -h $HostName -p $Port -U $User -d $TargetDatabase $BackupFile
    if ($LASTEXITCODE -ne 0) { throw "pg_restore a échoué (code $LASTEXITCODE)." }
    $version = Invoke-Psql $TargetDatabase 'select max(version::int) from flyway_schema_history where success'
    Write-Host "Restauration terminée. Dernière migration Flyway restaurée : V$version"

    if ($CompareWith) {
        $tables = Invoke-Psql $CompareWith "select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by 1"
        $diff = @()
        foreach ($t in $tables) {
            $a = Invoke-Psql $CompareWith "select count(*) from public.""$t"""
            $b = Invoke-Psql $TargetDatabase "select count(*) from public.""$t"""
            if ($a -ne $b) { $diff += "$t : source=$a restauré=$b" }
        }
        if ($diff) { throw ("Écarts de comptage :`n" + ($diff -join "`n")) }
        Write-Host "Comptage identique sur $(@($tables).Count) tables."
    }
} finally {
    if ($DropAfter) { Invoke-Psql 'postgres' "DROP DATABASE IF EXISTS $TargetDatabase WITH (FORCE)" | Out-Null; Write-Host "Base $TargetDatabase supprimée." }
}
