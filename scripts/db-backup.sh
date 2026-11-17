#!/usr/bin/env bash
# Sauvegarde logique d'une base PostgreSQL (pg_dump, format custom) avec somme de contrôle SHA-256.
# Lecture seule sur la base source. Le mot de passe n'est jamais passé en argument : définir PGPASSWORD
# ou utiliser un fichier ~/.pgpass (droits 600). Le fichier produit n'est jamais écrasé.
#
# Usage : PGPASSWORD=... ./scripts/db-backup.sh -d elite_tunisia -u elite -o /var/backups/elite [-h 127.0.0.1] [-p 5432]
set -euo pipefail

usage() { echo "Usage: $0 -d <base> -u <utilisateur> -o <dossier> [-h <hôte>] [-p <port>]" >&2; exit 2; }

database=""; user=""; outdir=""; host="127.0.0.1"; port="5432"
while getopts "d:u:o:h:p:" opt; do
  case "$opt" in
    d) database="$OPTARG" ;;
    u) user="$OPTARG" ;;
    o) outdir="$OPTARG" ;;
    h) host="$OPTARG" ;;
    p) port="$OPTARG" ;;
    *) usage ;;
  esac
done
[[ -n "${database//[[:space:]]/}" && -n "${user//[[:space:]]/}" && -n "${outdir//[[:space:]]/}" ]] || usage
command -v pg_dump >/dev/null || { echo "pg_dump introuvable dans le PATH." >&2; exit 1; }
command -v pg_restore >/dev/null || { echo "pg_restore introuvable dans le PATH." >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum introuvable." >&2; exit 1; }

mkdir -p "$outdir"
file="$outdir/${database}_$(date +%Y%m%d_%H%M%S).dump"
if [[ -e "$file" ]]; then
  echo "Le fichier existe déjà : $file" >&2
  exit 1
fi

# Un échec partiel ne doit pas laisser une archive présentée comme sauvegarde.
trap 'rm -f "$file"' ERR

export PGCONNECT_TIMEOUT=10
pg_dump -w --format=custom --no-owner --no-privileges -h "$host" -p "$port" -U "$user" -d "$database" -f "$file"

# Une archive illisible ne doit pas être considérée comme une sauvegarde.
pg_restore --list "$file" >/dev/null

(cd "$outdir" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256")
trap - ERR

echo "Sauvegarde : $file"
echo "SHA-256    : $(cut -d' ' -f1 "$file.sha256")"
