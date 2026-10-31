#requires -Version 5.1
<#
.SYNOPSIS
  Sauvegarde logique d'une base PostgreSQL (pg_dump, format custom) avec somme de contrôle SHA-256.

.DESCRIPTION
  Lecture seule sur la base source. Le mot de passe n'est jamais passé en argument : définir PGPASSWORD
  ou utiliser un fichier pgpass. Le fichier produit n'est jamais écrasé.

.EXAMPLE
  $env:PGPASSWORD = '...'; .\scripts\db-backup.ps1 -Database elite_tunisia -User elite -OutDir D:\sauvegardes
#>
param(
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$User,
    [Parameter(Mandatory)][string]$OutDir,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin'
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $OutDir | Out-Null
$file = Join-Path $OutDir ("{0}_{1:yyyyMMdd_HHmmss}.dump" -f $Database, (Get-Date))
if (Test-Path $file) { throw "Le fichier existe déjà : $file" }

$env:PGCONNECT_TIMEOUT = '10'
& "$PgBin\pg_dump.exe" -w --format=custom --no-owner --no-privileges -h $HostName -p $Port -U $User -d $Database -f $file
if ($LASTEXITCODE -ne 0) { Remove-Item $file -ErrorAction SilentlyContinue; throw "pg_dump a échoué (code $LASTEXITCODE)." }

# Une archive illisible ne doit pas être considérée comme une sauvegarde.
& "$PgBin\pg_restore.exe" --list $file | Out-Null
if ($LASTEXITCODE -ne 0) { throw "L'archive produite est illisible : $file" }

$hash = (Get-FileHash $file -Algorithm SHA256).Hash
Set-Content -Path "$file.sha256" -Value "$hash  $(Split-Path $file -Leaf)"
Write-Host "Sauvegarde : $file"
Write-Host "SHA-256    : $hash"
