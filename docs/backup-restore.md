# Sauvegarde et restauration PostgreSQL

Scripts : [`scripts/db-backup.ps1`](../scripts/db-backup.ps1) et [`scripts/db-restore.ps1`](../scripts/db-restore.ps1) (PowerShell, outils clients PostgreSQL 18).

## Principes de sécurité
- Le mot de passe n'est jamais passé en argument : `PGPASSWORD` ou fichier `pgpass`.
- La sauvegarde est en lecture seule sur la source, au format custom (`pg_dump -Fc`), accompagnée d'un fichier `.sha256`. Elle n'écrase jamais un fichier existant et est relue par `pg_restore --list`.
- La restauration **crée toujours une nouvelle base** et refuse une base existante. Elle refuse aussi un serveur non local sans `-AllowRemote`, et une somme de contrôle invalide.
- Une restauration de reprise sur incident se fait en restaurant dans une nouvelle base, en la contrôlant, puis en basculant `DATABASE_URL` après arrêt de l'application. Aucun script ne remplace une base en place.

## Utilisation
```powershell
$env:PGPASSWORD = '<mot de passe>'
.\scripts\db-backup.ps1 -Database elite_tunisia -User elite -OutDir D:\sauvegardes
# Test de restauration : base jetable, comparaison du nombre de lignes par table, puis suppression
.\scripts\db-restore.ps1 -BackupFile D:\sauvegardes\elite_tunisia_<horodatage>.dump -TargetDatabase elite_restore_check -User elite -CompareWith elite_tunisia -DropAfter
```
Équivalent Linux : [`scripts/db-backup.sh`](../scripts/db-backup.sh) (bash, mêmes garde-fous : refus d'écraser un fichier, `pg_restore --list`, somme `sha256sum`) :
```bash
PGPASSWORD='<mot de passe>' ./scripts/db-backup.sh -d elite_tunisia -u elite -o /var/backups/elite -h 127.0.0.1 -p 5432
sha256sum -c /var/backups/elite/elite_tunisia_<horodatage>.dump.sha256
```
La restauration Linux n'est pas scriptée : `createdb <nouvelle base>` puis `pg_restore --no-owner --exit-on-error -d <nouvelle base> fichier.dump`.

## Validation réalisée (2026-11-08)
Cluster PostgreSQL 18.3 isolé (127.0.0.1:15432, données dans `%LOCALAPPDATA%\EliteTunisiaTools\pg-test-cluster`). Base source dédiée `et_release_check` : schéma Flyway V1 à V32 appliqué au démarrage du backend en profil `prod`, 45 tables, 164 lignes, 1 compte de test.

- Sauvegarde Linux `scripts/db-backup.sh -d et_release_check` : code retour 0, fichier de 128 728 octets, SHA-256 recalculé indépendamment identique à celui du `.sha256`, `pg_restore --list` code 0 (45 définitions de tables).
- Restauration `scripts/db-restore.ps1` dans la base neuve `et_restore_validation` (inexistante avant), code 0, SHA-256 vérifié avant restauration.
- Comparaison source / restaurée : 45/45 tables, colonnes identiques (empreinte MD5), 164 lignes identiques, empreinte de contenu MD5 identique table par table, historique Flyway identique (32 succès, max V32), compte de test présent des deux côtés.
- Écart cosmétique : les CHECK et les prédicats d'index partiels sont reconstruits avec une écriture différente (`ARRAY[...]::text[]` contre `ARRAY[('X'::character varying)::text, ...]`). Les listes de valeurs sont identiques. Non vérifié par un test de comportement.

Scénarios d'échec : voir [`FINAL-PROJECT-VALIDATION.md`](./FINAL-PROJECT-VALIDATION.md), section J.

Corrections issues de ces tests : `db-backup.sh` et `db-backup.ps1` rejettent désormais les valeurs composées uniquement d'espaces ; `db-backup.ps1` écrit le `.sha256` en UTF-8 sans BOM avec fin de ligne LF (une fin CRLF faisait échouer `sha256sum -c` sous Linux).

Limites : base source de test très petite (pas de volume représentatif) ; statistiques `pg_stat` non fiables (`n_live_tup` à 0 sur la source) ; restauration validée sur PostgreSQL 18 uniquement.

## À faire avant la production
- Planifier les sauvegardes (fréquence et rétention à décider), les stocker hors du serveur de base de données et les chiffrer au repos.
- Définir les objectifs de perte de données et de temps de reprise (RPO/RTO), puis répéter un test de restauration complet régulièrement.
- Les fichiers uploadés (`STORAGE_LOCAL_PATH`) ne sont pas dans la base : les sauvegarder séparément.
