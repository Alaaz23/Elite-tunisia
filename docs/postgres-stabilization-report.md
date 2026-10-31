# Rapport de stabilisation PostgreSQL — étape 6A

Date des exécutions : 2026-10-28. Aucune base permanente n'a été contactée, lue ou modifiée. Ce rapport n'autorise aucun déploiement.

## 1. Périmètre audité

- Migrations Flyway V1 à V16 (existantes, commitées dans `45dec0c`) et V17, V18 (ajoutées par cette étape).
- Effets des migrations sur une base **contenant déjà des données**.
- Écarts de nullabilité entité JPA / migration (`invoice_lines.metadata`, `payments.refunded_amount`, `quotes.vehicle_id`).
- Permissions insérées par les migrations, comparées aux autorités exigées par le code.
- Parcours Playwright sur PostgreSQL réel.
- Tables sans entité ni API.

État du dépôt au départ : arbre propre, branche `main`, dernier commit `45dec0c`. V16 figurait déjà dans l'historique Git.

## 2. Environnement

| Élément | Valeur |
|---|---|
| Serveur | PostgreSQL 18.3 (x86_64-windows), binaires locaux, **sans Docker** |
| Cluster | répertoire de données dédié sous `%LOCALAPPDATA%\EliteTunisiaTools\pg-test-cluster`, écoute `127.0.0.1:15432` uniquement, SCRAM, mot de passe aléatoire hors dépôt |
| Port 5432 | service non identifié, **jamais contacté** |
| Bases créées/supprimées | uniquement dans ce cluster : `et_test_db`, `et_mig_*` (scénarios), `et_e2e_db` (E2E). Aucune base résiduelle à la fin (vérifié) |
| JDK / Node | Java 21.0.2 ; Node 26.12.1 portable |
| Garde-fous | `PostgresTestHelper`, `MigrationWithExistingDataIT` et `scripts/e2e-postgres.ps1` refusent toute cible autre que `127.0.0.1:15432` / `et_test` ; seules les bases préfixées `et_mig_` / `et_e2e_` / `et_test_db` peuvent être supprimées |

## 3. Audit des migrations avec données existantes

Colonne « Vérifié » : le comportement a été **exécuté** sur PostgreSQL 18.3 (section 4), pas seulement lu.

| Migration | Condition d'échec ou de risque | Données concernées | Stratégie | Vérifié |
|---|---|---|---|---|
| V2 | `employees.first_name/last_name/phone` ajoutés `NOT NULL` sans valeur par défaut (SQLSTATE 23502) | tout employé existant | Aucune valeur inventée : renseigner les trois champs avant migration. Détecté par le pré-contrôle (`V2_EMPLOYEES_NOT_NULL`) | oui |
| V2 | `customers.first_name/last_name/phone` passés `NOT NULL` (23502) | clients avec champ NULL | Corriger les clients avant migration (`V2_CUSTOMER_NULLS`) | oui |
| V2 | `users.role_key` supprimé après copie vers `user_roles` | tous les utilisateurs | Migration sans perte (copie préalable) ; non bloquant | oui (base vide + V3 valide) |
| V3 | `vehicles.price_per_week` et `currency` `NOT NULL` sans défaut (23502) | tout véhicule existant | Backfill manuel validé par le métier avant migration (`V3_VEHICLES_NOT_NULL`) | oui |
| V3 | CHECK : prix > 0, année 1886–2100, listes de statut / transmission / carburant | véhicules aux valeurs libres | Détectés par le pré-contrôle (`V3_PRICE_NOT_POSITIVE`, `V3_YEAR_OUT_OF_RANGE`, `V3_ENUM_VALUES`) ; l'échec effectif survient d'abord sur le `NOT NULL` | oui |
| V4 | Exclusion `bookings_no_blocking_overlap` : deux réservations `CONFIRMED`/`IN_PROGRESS` du même véhicule qui se chevauchent (23P01) | réservations bloquantes | Résoudre les conflits avant migration. Les `PENDING` qui se chevauchent et les `CONFIRMED` consécutives (bornes `[)`) passent | oui |
| V4 | `bookings_status_check` : statut hors `PENDING/CONFIRMED/REJECTED/CANCELLED/IN_PROGRESS/COMPLETED` (23514) | réservations à statut libre | Normaliser les statuts avant migration (`V4_STATUS_INVALID`) | oui |
| V4 | `btree_gist` doit pouvoir être créée | serveur | Le pré-contrôle vérifie sa disponibilité (`V4_BTREE_GIST_UNAVAILABLE`) ; non simulé car l'extension est présente | non (disponibilité seulement lue) |
| V5 | Exception volontaire si `quotes` n'est pas vide (P0001) | devis au format V1 | Mapping vers réservations à définir avec le métier ; aucune reprise automatique inventée (`V5_QUOTES_PRESENT`) | oui |
| V6 / V7 | `payments` et `invoices` renommées en `legacy_payments` / `legacy_invoices`, nouveau modèle **vide** | tous les paiements et factures historiques | La migration réussit, **mais les données deviennent invisibles de l'application** (conservées dans `legacy_*`). Aucune reprise écrite : le mapping fiscal/comptable relève du métier (`V6_PAYMENTS_STRANDED`, `V7_INVOICES_STRANDED`) | oui |
| V3, V4, V5, V10, V13, V14, V15 (et V2) | `INSERT` de permissions sans `ON CONFLICT` : clé déjà présente (23505) | `permissions` | Ne pas modifier les migrations commitées ; le pré-contrôle liste chaque clé en conflit (`PERMISSION_ALREADY_EXISTS`) | oui (V2 et V14) |
| V16 | `CREATE TABLE transfers` non idempotent : table déjà créée par Hibernate (`ddl-auto=update`) → 42P07 | table `transfers` éventuelle | Sauvegarder, renommer la table, migrer, recopier. Détecté par le pré-contrôle (`V16_TRANSFERS_PRE_EXISTING`, `V16_TRANSFER_ORPHAN_REFERENCES`) | oui |

**V16 n'a pas été modifiée.** Je l'avais d'abord rendue idempotente puis restaurée à son contenu commité : réécrire une migration déjà commitée casserait le checksum Flyway de toute base qui l'a appliquée.

## 4. Scénarios de migration avec données (exécutés)

Test : `backend/src/test/java/tn/elitetunisia/migration/MigrationWithExistingDataIT.java`, jeux de données dans `backend/src/test/resources/migration-scenarios/`. Chaque scénario clone un modèle migré jusqu'à une version, insère des données, lance le pré-contrôle puis Flyway jusqu'à V18 et vérifie l'état final. Résultat : **18 tests, 0 échec** (17 scénarios + audit des permissions).

| Scénario | Départ | Constats du pré-contrôle | Flyway | Dernière migration | Vérifications |
|---|---|---|---|---|---|
| base vide | V0 | aucun | OK | V18 | 3, 0 écart |
| employés existants | V1 | BLOCKER V2_EMPLOYEES_NOT_NULL | échec V2 (23502) | V1 | 2, 0 écart |
| client sans nom | V1 | BLOCKER V2_CUSTOMER_NULLS | échec V2 (23502) | V1 | 2, 0 écart |
| permission déjà présente (V2) | V1 | BLOCKER PERMISSION_ALREADY_EXISTS | échec V2 (23505) | V1 | 2, 0 écart |
| véhicules existants | V1 | BLOCKER V3_VEHICLES_NOT_NULL | échec V3 (23502) | V2 | 2, 0 écart |
| véhicules à valeurs invalides | V1 | 4 BLOCKER (NOT NULL, prix, année, énumérations) | échec V3 (23502) | V2 | 1, 0 écart |
| réservations CONFIRMED consécutives | V3 | aucun | OK | V18 | 6, 0 écart (références uniques et formatées) |
| réservations PENDING qui se chevauchent | V3 | aucun | OK | V18 | 2, 0 écart |
| CONFIRMED + IN_PROGRESS qui se chevauchent | V3 | BLOCKER V4_OVERLAPPING_BLOCKING_BOOKINGS | échec V4 (23P01) | V3 | 2, 0 écart |
| statut de réservation invalide | V3 | BLOCKER V4_STATUS_INVALID | échec V4 (23514) | V3 | 1, 0 écart |
| devis historique | V4 | BLOCKER V5_QUOTES_PRESENT | échec V5 (P0001) | V4 | 2, 0 écart |
| paiements et factures V1 | V5 | DATA_RISK V6 et V7 | OK | V18 | 4, 0 écart (1 ligne dans chaque `legacy_*`, 0 dans les nouvelles tables) |
| permission déjà présente (V14) | V13 | BLOCKER PERMISSION_ALREADY_EXISTS | échec V14 (23505) | V13 | 1, 0 écart |
| `transfers` créée par Hibernate | V15 | BLOCKER V16_TRANSFERS_PRE_EXISTING | échec V16 (42P07) | V15 | 2, 0 écart |
| `transfers` avec référence orpheline | V15 | 2 BLOCKER | échec V16 (42P07) | V15 | 1, 0 écart |
| colonnes NULL alignées par V17 | V16 | aucun | OK | V18 | 4, 0 écart |
| remboursement sans montant | V16 | BLOCKER V17_REFUND_AMOUNT_AMBIGUOUS | échec V17 (P0001) | V16 | 1, 0 écart |

Dans chaque scénario réussi, un second `migrate()` n'applique rien. Dans chaque échec, la dernière migration reste celle d'avant l'échec (transaction par migration) et les données d'origine sont intactes (vérifié).

Portée : ces scénarios couvrent les risques identifiés ci-dessus avec des **données synthétiques minimales**, pas une copie de données réelles.

Pré-contrôle réutilisable (lecture seule, transaction annulée) : `database/verification/preflight-before-flyway.sql`. Il peut être lancé avec `psql` sur toute base avant de lancer Flyway.

## 5. Écarts de nullabilité

| Colonne | Entité JPA | Migration d'origine | Usage dans le code | Constat | Correction |
|---|---|---|---|---|---|
| `invoice_lines.metadata` | `@Column(nullable = false)`, jamais null (copie via `Map.copyOf`) | V7 : `jsonb DEFAULT '{}'`, nullable | écrite par `InvoiceLineEntity`, lue par les contrôleurs de facture | **accidentel** : le défaut `'{}'` existait déjà ; `quote_lines.metadata` est `NOT NULL` | V17 : `NULL` → `'{}'` puis `NOT NULL` |
| `payments.refunded_amount` | `nullable = false`, initialisée à 0 | V6 : `numeric(12,3) DEFAULT 0`, nullable | getter/setter uniquement ; aucun remboursement n'est implémenté | **accidentel** (défaut 0 déjà déclaré) | V17 : `NULL` → 0 puis `NOT NULL`. Un paiement avec `refunded_at` sans montant est **ambigu** : la migration échoue volontairement au lieu d'inventer un montant |
| `quotes.vehicle_id` | `nullable = false`, dérivée de la réservation liée | V1 : nullable ; V5 n'y touche pas | `QuoteEntity` la renseigne depuis `booking.getVehicle()` ; sert à `matches()` | **accidentel** et dérivable | V17 : renseignée depuis `bookings.vehicle_id` via `booking_id`, puis `NOT NULL` |

Tests (scénario `v16-null-columns` et `v16-ambiguous-refund`) : valeurs `{}` / `0.000` / identifiant du véhicule restaurées, colonnes devenues `NOT NULL`, échec contrôlé sur le cas ambigu, aucune valeur métier inventée. Après V17, la comparaison colonne par colonne avec le schéma généré par Hibernate (relevée à l'étape précédente) ne laisse plus que ces écarts corrigés.

## 6. Audit des permissions

Insertions de rôles : V1 (6 rôles, sans conflit possible sur base vide). Insertions de permissions : V2, V3, V4, V5, V10, V13, V14, V15 ; `role_permissions` : mêmes migrations. Les clés primaires (`permission_key`, `(role_key, permission_key)`) interdisent les doublons ; elles font **échouer** la migration si la clé existe déjà (voir section 3).

Comparaison entre autorités exigées par le code (`@PreAuthorize`, `SecurityConfig`) et base migrée **sans seeder** (test `permissionsFromMigrationsMatchWhatTheCodeRequires`, exécuté) :

- Les 23 autorités exigées existent en base ; aucune ligne de `role_permissions` ne référence une permission absente.
- `SUPER_ADMIN` détient toutes les autorités d'administration. Il ne détient pas `payments:create`, `quotes:accept`, `quotes:reject` : ce sont des actions client, leurs contrôleurs exigent aussi `hasRole('CUSTOMER')`.
- `CUSTOMER` détient exactement `bookings:read/create/cancel`, `quotes:read/accept/reject`, `payments:create`, `invoices:read`, `profile:read:self`, `profile:update:self`, et aucun droit `admin:access` ni `*:manage`.
- `AGENT` détient `drivers:manage` et `bookings:manage`, pas `customers:read` (annuaire de données personnelles réservé à `SUPER_ADMIN` et `MANAGER`).

Écarts trouvés et traitement :

1. **`invoices:read` n'était accordé qu'à `CUSTOMER` (V13)**, alors que `AdminInvoiceController` l'exige et que `CUSTOMER` n'a pas `admin:access`. Sur une base migrée par Flyway, **personne ne pouvait consulter les factures côté administration** ; le seeder H2 (qui donne tout à `SUPER_ADMIN`) le masquait. Correction : migration V18 accorde `invoices:read` à `SUPER_ADMIN` seulement (`ON CONFLICT DO NOTHING`). Vérifié par le test de matrice ; **non rejoué en E2E** (aucun scénario Playwright ne visite la page admin des factures).
2. **`AdminTransferController` n'avait aucun `@PreAuthorize`** : tout compte avec `admin:access` (dont `CONCIERGE` et `ACCOUNTANT`) pouvait créer et lister des transferts. Correction : `hasAuthority('bookings:manage')`, par cohérence avec les contrôleurs de réservation et d'affectation. **Cela réduit les droits effectifs** de `CONCIERGE` et `ACCOUNTANT` sur cette route (elle n'a pas d'interface frontend). Cinq tests de sécurité ajoutés (`AdminTransferControllerSecurityTest`), réussis.

Décisions laissées au métier (non modifiées) : accès des rôles `ACCOUNTANT` et `MANAGER` aux factures ; accès aux paiements côté administration.

## 7. E2E Playwright sur PostgreSQL

Les specs dépendaient du compte du seeder H2 codé en dur. Elles lisent désormais `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD` (`frontend/e2e/admin-credentials.ts`), avec les anciennes valeurs par défaut pour le profil H2.

`scripts/e2e-postgres.ps1` : vérifie le cluster et l'identité du serveur, crée `et_e2e_db`, démarre le backend (profil `dev`, Flyway réel, Hibernate `validate`, secret JWT aléatoire), crée explicitement le compte super administrateur en SQL (hash bcrypt calculé par PostgreSQL, mot de passe aléatoire en mémoire, jamais écrit), démarre Angular, exécute `npm run e2e`, arrête les services et supprime la base.

Résultat réel : **18 scénarios réussis, 0 échec, 0 ignoré** (57 s), couvrant : inscription et connexion, rôles et refus d'accès admin, création/blocage d'un agent par le super admin, catalogue public et éditeur de flotte, création d'une réservation, disponibilité, isolation entre deux clients (lecture et annulation refusées), confirmation par l'agence et refus du chevauchement, annulation et libération de la période, prise en charge/restitution avec facture de frais, affectation d'un chauffeur certifié, cycle complet des devis (demande, brouillon, envoi, acceptation, refus, nouvelle version, devis d'un autre client refusé, conflit après prise du véhicule), paiement simulé unique, annuaire clients.

Non couvert par E2E : page admin des factures, redémarrage de l'application pendant une session, notifications, partenaires, services (aucune interface).

## 8. Tables sans entité ni API

Les 39 tables de la base migrée : 27 ont une entité JPA. Tables restantes ou particulières :

| Table | Utilité supposée (schéma / docs) | Entité | Service | API | Statut |
|---|---|---|---|---|---|
| `partners` | partenaires commerciaux (type, e-mail, taux de commission) | non | non | non | non implémenté |
| `notifications` | notifications par utilisateur et canal, lue/non lue | non | non | non | non implémenté (les pages client « notifications » sont des placeholders) |
| `documents` | stockage de pièces (clé, type MIME) | non | non | non (le stockage d'images véhicule passe par un autre mécanisme) | non implémenté |
| `driver_documents` | pièces d'un chauffeur avec expiration | non | non | non | non implémenté |
| `service_requests` | demandes client de services | non | non | non | non implémenté |
| `booking_services` | services rattachés à une réservation | non | non | non | non implémenté ; les services passent aujourd'hui par les lignes de devis |
| `services` | catalogue de services additionnels | oui (`ConfiguredServiceEntity`) | lecture seule par `QuoteService` | **aucune API ni CRUD** (état 6A) → **API d'administration livrée en 6B** (V20, page `/admin/services`) | partiel en 6A ; implémenté pour l'administration en 6B (page publique `/services` toujours placeholder) |
| `invoice_payments` | lien facture/paiement | non (SQL natif dans `InvoiceService`) | oui | via les factures | implémenté sans entité |
| `legacy_payments`, `legacy_invoices` | anciennes données renommées par V6/V7 | non | non | non | volontairement orphelines (voir section 3) |
| `transfers` | transferts avec chauffeur | oui | oui (`createForBooking` n'est appelé par aucun contrôleur) | `/api/admin/transfers` (création, liste, détail) | **partiel** ; état 6A : le contrôleur liait directement l'entité JPA (statut, chauffeur, véhicule modifiables par le client de l'API), sans interface. **Corrigé en 6B** (DTO, règles, V19) ; toujours sans interface Angular, ni E2E, ni tarification |
| `role_permissions`, `user_roles` | tables de jointure | via `@ManyToMany` | — | via administration des utilisateurs | implémenté |

Aucune API n'a été créée : ces domaines relèvent d'étapes ultérieures.

## 9. Résultats des tests et builds

| Vérification | Résultat réel |
|---|---|
| Flyway sur base vide | V1→V18, 18 migrations réussies, 0 erreur SQL |
| Migration depuis une version antérieure | 17 scénarios (section 4), 0 écart |
| Démarrage avec Hibernate `validate` | réussi sur PostgreSQL (backend UP pendant l'E2E) |
| `.\mvnw.cmd -Ppostgres-test verify` | surefire **93 tests, 0 échec, 0 erreur, 0 ignoré** ; failsafe **21 tests, 0 échec** ; BUILD SUCCESS |
| `.\mvnw.cmd -DskipITs test` (H2) | **93 tests, 0 échec, 0 erreur, 10 ignorés** ; BUILD SUCCESS |
| `npm test -- --watch=false` | 33 tests, 8 fichiers, tous réussis |
| `npm run build` | réussi (avertissements non bloquants connus : budget SCSS de la page d'accueil, dépendance CommonJS `jsbi`) |
| `tsc --noEmit` (application) | réussi |
| Playwright sur PostgreSQL | 18/18 |
| Playwright sur H2 | **non rejoué** après la modification des specs (seule l'origine des identifiants admin a changé ; valeurs par défaut identiques) |
| Contraintes, index, séquences (étape précédente) | 3 séquences, contrainte d'exclusion, extension `btree_gist`, `legacy_*` confirmés ; non revérifiés individuellement après V17/V18 hormis le test de nullabilité et de permissions |

Régressions : aucune détectée. Erreurs préexistantes révélées puis corrigées : voir sections 5 et 6. Tests non exécutés : E2E H2 (ci-dessus), migration de données réelles.

## 10. Problèmes trouvés et corrections

1. Facturation inaccessible côté administration sur base migrée (V18).
2. Contrôleur de transferts sans contrôle d'autorité (`@PreAuthorize`, 5 tests).
3. Trois colonnes nullables contre l'entité (V17, 2 scénarios).
4. Migrations non protégées contre des données existantes : pré-contrôle en lecture seule + 17 scénarios reproductibles.
5. E2E dépendant du seeder H2 : identifiants configurables et orchestration PostgreSQL isolée.
6. V16 modifiée puis restaurée : la règle « ne pas réécrire une migration commitée » est respectée.

## 11. Risques restants

- **Aucune base contenant des données réelles n'a été migrée.** Les scénarios sont synthétiques ; une sauvegarde et le pré-contrôle sont indispensables avant toute base existante.
- V6/V7 laissent l'historique des paiements et factures dans `legacy_*` sans reprise : décision comptable requise.
- V2, V3, V5 exigent un backfill ou un mapping métier non écrit.
- V18 et la restriction des transferts modifient des droits effectifs ; à valider par le métier.
- `AdminTransferController` expose l'entité JPA directement ; à remplacer par un DTO validé avant tout usage réel.
- PostgreSQL 18.3 seulement ; autres versions non testées.
- Les E2E ne couvrent pas la page admin des factures ni les domaines non implémentés.
- Les ports 8080 et 4200 doivent être libres pour `scripts/e2e-postgres.ps1`.
- Rien ici ne valide la sécurité de production (secrets, TLS, sauvegardes, supervision).

## 12. Commandes de reproduction

Prérequis : cluster créé et démarré selon `docs/start-local.md` (section « PostgreSQL isolé sans Docker »).

```powershell
# Backend PostgreSQL : migrations V1→V18, scénarios avec données, permissions, tests d'intégration
$root = Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\pg-test-cluster'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ELITE_POSTGRES_TEST = 'true'
$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/et_test_db'; $env:DATABASE_USERNAME = 'et_test'
$env:DATABASE_PASSWORD = [IO.File]::ReadAllText("$root\pgpass.local")
Set-Location .\backend
.\mvnw.cmd -Ppostgres-test verify        # rapport des scénarios : backend\target\migration-scenarios-report.md
'ELITE_POSTGRES_TEST','DATABASE_URL','DATABASE_USERNAME','DATABASE_PASSWORD' | ForEach-Object { Remove-Item "Env:\$_" -ErrorAction SilentlyContinue }

# Régression H2
.\mvnw.cmd -DskipITs test

# E2E sur PostgreSQL isolé (ports 8080 et 4200 libres)
Set-Location ..
.\scripts\e2e-postgres.ps1                          # toute la suite
.\scripts\e2e-postgres.ps1 -Spec bookings.spec.ts   # un seul fichier

# Pré-contrôle en lecture seule sur une base à migrer (psql, hors dépôt pour le mot de passe)
psql -h <hote> -p <port> -U <utilisateur> -d <base> -f .\database\verification\preflight-before-flyway.sql
```

Lecture du pré-contrôle : `NOTICE: PREFLIGHT|<GRAVITE>|<CODE>|<détail>`. Un constat `BLOCKER` signale un échec certain d'une migration en attente ; `DATA_RISK` signale des données qui deviendront invisibles. L'absence de constat ne prouve pas que les migrations soient compatibles : seuls les risques listés en section 3 sont contrôlés.

## 13. Mise à jour étape 6B (2026-10-28)

Ce rapport décrit l'étape 6A ; ses sections restent exactes pour V1→V18. L'étape 6B ajoute :

- **V19** (rattachement des transferts à une réservation, contraintes `NOT VALID`) et **V20** (permissions `services:read` / `services:manage`, `ON CONFLICT DO NOTHING`) ; le pré-contrôle signale `V19_TRANSFER_STATUS_UNKNOWN` ; `MigrationWithExistingDataIT` compte désormais 20 tests (2 scénarios de plus : V18 → V20 avec transfert existant, V19 → V20 avec permission déjà présente).
- Résultats exécutés sur PostgreSQL 18.3 isolé (V1→V20) : surefire **118 tests, 0 échec, 0 ignoré** ; failsafe **23 tests, 0 échec** ; E2E `.\scripts\e2e-postgres.ps1` **20/20**. H2 : 118 tests, 0 échec, 10 ignorés. Angular : 38 tests, build réussi.
- Audit de sécurité ciblé, sécurisation des transferts, cartographie des droits de facturation, inventaire des modules et décisions métier en attente : [step-6b-security-and-modules.md](step-6b-security-and-modules.md).
