# Validation finale du projet Elite Tunisia

## A. État général : PARTIELLEMENT VALIDÉ

Validé sur preuves réelles : backend complet (JDK 21, PostgreSQL réel `elite_a4_test`), tests Angular, build de production, E2E Playwright contre le vrai backend et PostgreSQL, migrations Flyway V1 à V32, règles de facturation et de réservation.

Restent non validés (détail en section D) :

- 4 classes de tests PostgreSQL héritées, non exécutées (cluster partagé 15432 avec `CREATE DATABASE` / `DROP DATABASE`).
- Règle de retard non décidée par le propriétaire (B2 / T-A05).
- Pénalités et remboursements hors MVP (D-PA-09).
- 2 avertissements de build non bloquants (budget `home.component.ts`, module `jsbi`).

## B. Résultats mesurés

Toutes les commandes ont été exécutées sur le code final de cette session. Codes de sortie réels.

| Validation | Commande | Résultat | Exit |
|---|---|---|---|
| Backend unitaires | `backend\mvnw.cmd -B -Ppostgres-test clean verify` (JDK 21.0.2) | Surefire : 302 exécutés, 0 échec, 0 erreur, 0 ignoré | 0 |
| Backend PostgreSQL réel (`*IT`) | même commande, phase failsafe | 17 exécutés, 0 échec, 0 erreur, 0 ignoré (`PostgresIdempotencyIT`, `PaymentQuoteCapturePostgresIT`, `LastSuperAdminConcurrencyIT`, etc.) | 0 |
| Angular | `$env:CI='1'; ng test --watch=false` (Node 24.22.2) | 17 fichiers, 106 tests réussis, 0 échec | 0 |
| Build production | `ng build` (Node 24.22.2) | Succès, bundle initial 704.93 kB, 2 avertissements (section D) | 0 |
| E2E Playwright | `node node_modules/@playwright/test/cli.js test --reporter=list` (backend et frontend démarrés, voir E) | 21 réussis, 0 échec, 0 ignoré | 0 |

Note de stabilité : une première passe E2E a donné 20 réussis et 1 échec sur `e2e/services.spec.ts` (test « agent peut consulter le catalogue »). La cause était une course dans `AdminServicesComponent` : une réponse de chargement initial, obsolète, pouvait écraser le résultat de la recherche. Le composant ignore désormais les réponses obsolètes (voir C). Après correction, la suite complète passe (21/21). Une relance dédiée du spec avait déjà réussi avant correction, ce qui confirme une course intermittente et non un échec fonctionnel.

Note sur les tests Angular : une passe a échoué avec `Timeout waiting for worker to respond` (worker Vitest), pendant que le serveur de dev et le backend tournaient. Relancée seule, la suite passe (106/106). C'est un problème d'environnement, non de code, mais il reste à surveiller.

## C. Corrections effectuées (cette session)

- `frontend/src/app/features/admin/admin-services.component.ts` : ignore les réponses de chargement obsolètes (`latestLoadId`), pour corriger la course de recherche observée en E2E.
- `.gitignore` (racine) : ignore les journaux et sorties de commandes locales (`/*.log`, `/*.exit.txt`, `/*-git-*.txt`, `/*-status*.txt`, `/*-check*.txt`). Ces fichiers ne sont pas supprimés. Ils sont cités par la documentation (`docs/lot-a4-technical-report.md`, `technical-audit-final.md`).
- `backend/pom.xml` (profil `postgres-test`) : exclusions documentées (section F).

Aucun fichier source, migration, document métier ou donnée n'a été supprimé.

Vérification métier, sans modification de code nécessaire :

- Calcul de facturation (`backend/src/main/java/tn/elitetunisia/common/pricing/RentalPricingService.java`) : `jours = ceil(durée / 24 h)`. Cas testés dans `RentalPricingBoundaryTest` : 1 h → 1 jour, 23 h → 1, 24 h → 1, 24 h + 1 min → 2, 25 h → 2, 47 h → 2, 48 h → 2, 48 h + 1 min → 3. Conforme à la règle « période commencée de 24 h ». La facturation intervient au retour (`BookingOperationService.recordReturn`, `billing.enqueueMainInvoice`).
- PENDING ne bloque pas la flotte (`BookingRepository.java`, requête de disponibilité).
- Chevauchement contrôlé à la confirmation (`BookingService.java`, ~152-157).
- Suppression physique interdite si historique de réservation ou de partenaire (`VehicleService.delete`).
- UNAVAILABLE / MAINTENANCE / ARCHIVED refusés pendant une location ou une réservation CONFIRMED future (`VehicleService.requireStatusChangeAllowed`). UNAVAILABLE reste distinct de l'archivage.
- Transitions de statut (`BookingStatus.java`) : pas de passage direct à COMPLETED.

## D. Problèmes restants (réels, non résolus)

1. Quatre classes PostgreSQL non exécutées : `PartnerMigrationPostgresTest`, `PaymentBookingPostgresTest`, `PaymentQuotePostgresA4Test` (surefire) et `MigrationWithExistingDataIT` (failsafe). Elles sont liées au cluster abandonné 15432 et utilisent `CREATE DATABASE` / `DROP DATABASE`. Elles sont exclues et non comptées comme réussies.
2. Règle de retard non décidée : aucune pénalité de retard n'est calculée. Décision propriétaire ouverte (B2 / T-A05, `docs/architecture/...`). Un booking `IN_PROGRESS` passé `ends_at` reste bloquant pour le véhicule (choix de sécurité par défaut).
3. Pénalités et remboursements hors MVP (D-PA-09). Une annulation d'une réservation payée laisse le paiement en CAPTURED, à réconcilier manuellement.
4. Avertissement de budget : `home.component.ts` (styles inline) dépasse 4 kB (environ 6.36 kB). Le réduire touche au design. Non corrigé pour éviter une régression visuelle.
5. Avertissement `jsbi` non ESM : `jsbi@4.3.2` vient de `@js-temporal/polyfill@0.5.1` (`frontend/package.json`), utilisé par `booking-api.service.ts` et `quote-editor.component.ts`. Le remplacer est un risque fonctionnel. Non modifié.
6. Deux brouillons `database/proposed/V21__...` et `V22__...` diffèrent des migrations appliquées `database/migrations/V21`/`V22`. Ils sont conservés, à trancher.
7. Git : `core.autocrlf=true`, avertissements CRLF attendus.

## E. Reproduction

Prérequis : JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot`), Node 24 (`%LOCALAPPDATA%\elite-tools\node-v24.22.2-win-x64\node.exe`). Variables d'environnement : `ELITE_A4_PG_URL`, `ELITE_A4_PG_USER`, `ELITE_DB_PASSWORD`. Le mot de passe n'est jamais écrit dans les fichiers ni dans les logs.

Backend (PostgreSQL réel) :

```
cd backend
mvnw.cmd -B -Ppostgres-test clean verify
```

Backend lancé sur `elite_a4_test` pour les E2E :

```
cd backend
mvnw.cmd -B spring-boot:run -Dspring-boot.run.profiles=postgres-test   (DATABASE_URL/USERNAME/PASSWORD depuis les variables ci-dessus, JWT_SECRET aléatoire, SERVER_PORT=8080)
```

Frontend :

```
cd frontend
node node_modules/@angular/cli/bin/ng.js test --watch=false        (avec CI=1)
node node_modules/@angular/cli/bin/ng.js build
node node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1 --port 4200 --proxy-config proxy.conf.json
node node_modules/@playwright/test/cli.js test --reporter=list     (serveur 4200 et backend 8080 requis)
```

Les serveurs doivent être arrêtés par PID après usage.

## F. Tests PostgreSQL hérités : exclusion et procédure isolée

Exclusions (`backend/pom.xml`, profil `postgres-test`) :

- Surefire : `PartnerMigrationPostgresTest`, `PaymentBookingPostgresTest`, `PaymentQuotePostgresA4Test`.
- Failsafe : `MigrationWithExistingDataIT`.

Justification : ces classes ciblent le port 15432 (cluster abandonné) et exécutent `CREATE DATABASE` / `DROP DATABASE`. Elles ne doivent pas tourner sur le cluster partagé. Les garde-fous de `PostgresTestHelper` n'ont pas été affaiblis, et la logique métier de ces tests n'a pas été modifiée. Ces 4 classes ne sont pas comptées comme réussies.

Procédure isolée (non exécutée) : provisionner un PostgreSQL de test dédié et jetable, distinct du cluster partagé, avec un utilisateur de test dédié, puis lancer `mvnw.cmd -B -Ppostgres-test verify` avec les exclusions retirées et une autorisation explicite du propriétaire pour les opérations de base de données.

## G. Migrations et sécurité

- Flyway : V1 à V32 appliquées sur `elite_a4_test` (32 `SUCCESS`, 0 échec), vérifiées au Lot A4.
- Secrets : aucun identifiant dans le code versionné. Le mot de passe de base vient de `ELITE_DB_PASSWORD`.
- Les garde-fous PostgreSQL restent actifs (URL, port 5432, base et utilisateur autorisés).
- Authentification : les routes API protégées répondent 401 sans jeton (vérifié par la réponse de `/api/actuator/health`).

## H. Git

- HEAD : `78da39b`. Aucun commit ni push effectué.
- `git status` : 145 entrées après ajout des règles `.gitignore` (214 avant). 71 fichiers suivis modifiés (`git diff --stat` : 1690 insertions, 605 suppressions).
- Aucun fichier supprimé dans cette session.

## I. Préparation à la livraison

Le projet est fonctionnellement validé sur les parcours testés, avec backend réel et PostgreSQL réel. Verdict détaillé à la section K.

## J. Sauvegarde, restauration et variables de production (2026-11-08)

Commandes et résultats détaillés dans `docs/backup-restore.md` (sauvegarde et restauration réelles sur `et_release_check` vers `et_restore_validation`, comparaison de 45 tables et 164 lignes, identique).

Scénarios d'échec exécutés sur le cluster isolé :

| Scénario | Résultat | Exit |
|---|---|---|
| `db-backup.sh` : port 15499 fermé | `Connection refused`, aucune archive | 1 |
| `db-backup.sh` : `-u` absent | usage | 2 |
| `db-backup.sh` : `-d` vide | usage | 2 |
| `db-backup.sh` : `-d` = espaces | **anomalie** avant correction : `pg_dump` tenté sur la base `"  "` ; **corrigé** : usage | 2 |
| `db-backup.sh` : `-o` pointe sur un fichier | `mkdir` échoue (erreur brute, pas de message dédié) | 1 |
| `db-backup.ps1` : port 15499 fermé | `pg_dump a échoué`, fichier partiel supprimé | 1 |
| `db-backup.ps1` : `-User ''` | rejeté (MissingArgument) | 1 |
| `db-backup.ps1` : `-Database ' '` | **anomalie** avant correction (pg_dump tenté) ; **corrigé** : message dédié | 1 |
| `db-restore.ps1` : SHA-256 incorrect | `Somme de contrôle SHA-256 invalide` | 1 |
| `db-restore.ps1` : dump absent | refus | 1 |
| `db-restore.ps1` : base cible existante | refus avant création | 1 |
| `db-restore.ps1` : `-TargetDatabase` = espaces | refus (nom invalide) | 1 |
| `db-restore.ps1` : port 15499 fermé | `psql a échoué` | 1 |
| `db-restore.ps1` : serveur non local | refus sans `-AllowRemote` | 1 |

Vérification du `.sha256` PowerShell : **anomalie** avant correction : fin de ligne CRLF, que `sha256sum -c` lit comme partie du nom de fichier (échec « could not be read »). Pas de BOM. **Corrigé** : écriture UTF-8 sans BOM, LF. Après correction : `sha256sum -c` → `OK`.

Variables de production : `TokenService` acceptait un `JWT_SECRET` composé uniquement d'espaces de plus de 32 octets. **Corrigé** (`isBlank()`), couvert par `TokenServiceSecretTest` (3/3 réussis). `ProductionProfileHardeningTest` 7/7 réussis après correction.

**Non vérifié** : le démarrage du backend avec `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `CORS_ALLOWED_ORIGINS` et `STORAGE_LOCAL_PATH` absents, vides ou composés d'espaces n'a pas été exécuté. Pour ces variables, aucune validation applicative explicite n'a été ajoutée : une valeur blanche est passée telle quelle à Spring. Le risque est une erreur de connexion ou de configuration au démarrage, pas une acceptation silencieuse, mais ce point reste à tester.

## K. Verdict de production (2026-11-08)

**NON PRÊT POUR LA PRODUCTION.** Raisons, selon les preuves :

1. Décisions métier ouvertes (non tranchées par l'agent) : pénalités de retard, traitement des paiements CAPTURED après annulation, politique de remboursement, compatibilité acompte + solde avec l'index V21, brouillons `database/proposed/V21` et `V22`.
2. Quatre classes PostgreSQL héritées toujours exclues (opérations destructives sur un cluster partagé).
3. Sauvegarde validée sur une base de test très petite, sans planification, sans stockage externe ni chiffrement.
4. Démarrage avec variables absentes ou vides non testé (voir J).
5. Uploads locaux : persistance, sauvegarde et plusieurs instances non résolues (voir `deployment-runbook.md`).

