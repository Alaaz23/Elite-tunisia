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
Équivalent Linux (serveur de production) : `pg_dump -Fc --no-owner --no-privileges -h … -U … -d … -f fichier.dump`, `sha256sum`, puis `createdb` et `pg_restore --no-owner --exit-on-error -d <nouvelle base> fichier.dump`.

## Validation réalisée (2026-10-30)
Sur un cluster PostgreSQL 18 temporaire (127.0.0.1:15432, répertoire de données dans `%TEMP%`) : sauvegarde de `et_test_db`, somme de contrôle valide, restauration dans `et_restore_check` jusqu'à Flyway V20, comptage identique sur 39 tables, base supprimée ; refus vérifiés pour une base cible existante et pour un serveur distant. La base `et_test_db` ne contenait que les données laissées par les tests : **la restauration n'a pas été éprouvée sur un volume représentatif**, ni sur la base de préproduction.

## À faire avant la production
- Planifier les sauvegardes (fréquence et rétention à décider), les stocker hors du serveur de base de données et les chiffrer au repos.
- Définir les objectifs de perte de données et de temps de reprise (RPO/RTO), puis répéter un test de restauration complet régulièrement.
- Les fichiers uploadés (`STORAGE_LOCAL_PATH`) ne sont pas dans la base : les sauvegarder séparément.
