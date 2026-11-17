# 18 — Suivi global du MVP Elite Tunisia

> Mis à jour le 2026-11-03. Ce document distingue strictement : **décidé**, **implémenté**, **testé**, **non vérifié**. Une ligne n'est marquée « testé » que si une commande a été exécutée avec son résultat.

## 1. Missions

| Mission | Intitulé | Statut | Preuve |
|---|---|---|---|
| 1–6 | Audits, LOT 1, D1, documentation partner-availability, validation D-PA | Terminées (antérieures) | review/01 à 16 |
| 7 | Arbitrage métier et gel du périmètre | **Terminée (documentaire)** | review/17 |
| 8 | Audit technique et préparation | **Livrables produits (lecture seule), tests non exécutés** | review/19 à 24 |
| 9 | Fondations partenaires | Non commencée | — |
| 10 | Garanties de disponibilité | Non commencée | — |
| 11 | Réservations | Non commencée | — |
| 12 | Espace partenaire | Non commencée | — |
| 13 | Administration Elite | Non commencée | — |
| 14 | Site client | Non commencée | — |
| 15 | Paiements et intégrations | Non commencée | — |
| 16 | Tests et démonstration finale | Non commencée | — |

## 2. Décisions

| Sujet | État | Référence |
|---|---|---|
| D-PA-05, D-PA-12 (principe), D-PA-14 (identifiant), historique, référence booking→garantie | Validées propriétaire (antérieures) | review/08 |
| D-PA-01, 04, 07, 10/D5, 12 §3.5, 14 (règle), 15 à 18 | **Décisions MVP prises** (hypothèses documentées), non validées propriétaire | review/17 |
| Valeurs commerciales (durée réelle, acompte, pénalités) | Hors MVP / en attente | review/17 §2 |

## 3. Fonctionnalités

| Fonctionnalité | Implémentée | Testée |
|---|---|---|
| Gestion des partenaires | Non | Non |
| Rattachement véhicule/partenaire | Non | Non |
| Garanties de disponibilité | Non | Non |
| Catalogue public filtré par garanties | Non | Non |
| Recherche par dates | Partielle (existante, non garantie) | Non vérifiée |
| Réservation avec référence garantie | Non | Non |
| Conflits empêchés (CONFIRMED/IN_PROGRESS) | Existant (EXCLUDE V4) | Non vérifié sur PostgreSQL |
| Hold PENDING | Non | Non |
| Espace partenaire lecture seule | Non | Non |
| Administration du processus | Partielle | Non vérifiée |
| Paiement simulé | Non vérifié | Non |

## 4. Tests

- Mission 7 : aucun test exécuté (documentaire).
- Mission 8 : **aucun test exécuté**. Seule commande réelle : `java -version` → OpenJDK 21.0.2 (Temurin), confirmé comme JDK actif.
- Tests présents mais non exécutés : `BookingLockOrderPostgresIT`, `BookingAuditAtomicityPostgresIT`, `PaymentQuoteCapturePostgresIT`, `VehicleLifecycleProtectionTest`, `PaymentQuoteInvariantUnitTest`, `SecurityHeadersTest`.
- Build et tests frontend : non exécutés.

### Résultats réels — Étape A et B (A3, A4)

- A3 : `VehicleLifecycleProtectionTest` exécuté sous JDK 21 : 10 tests, 0 échec, BUILD SUCCESS.
- Sauvegarde `et_test_db` (`pg_dump -Fc`, hors dépôt) et restauration dans `et_restore_check` : exit 0. Données identiques ; schéma : différences de rendu CHECK uniquement (sémantiquement équivalentes).
- V21 (`uq_payments_one_captured_per_quote`) et V22 (`fk_payments_quote` en RESTRICT, NOT VALID) appliquées sur `et_restore_check` : OK.
- Comportement vérifié sur `et_restore_check` (transaction annulée) : T1 suppression d'un devis avec paiements refusée (`foreign_key_violation`) ; T2 second paiement CAPTURED refusé (`unique_violation`) ; T3 paiement non-CAPTURED accepté.
- Flyway V1 à V22 depuis zéro sur la base vide `et_a4_flyway_v1_22` (cluster 15432, PostgreSQL 18.3, créée pour cette validation) : 22 migrations `success=true`, dans l'ordre 1 à 22, depuis `database/migrations` (répertoire actif copié par `backend/pom.xml`).
- `VALIDATE CONSTRAINT fk_payments_quote` exécuté sur `et_restore_check` et `et_a4_flyway_v1_22` après contrôle : 0 paiement orphelin, 0 doublon CAPTURED ; `convalidated = t` sur les deux.
- Démarrage applicatif en mode web normal sur `et_a4_flyway_v1_22` (port 18080, variables de processus uniquement) : `Started BackendApplication`, `/actuator/health` HTTP 200, `"status":"UP"`. Processus arrêté ensuite. Le mode `web-application-type=none` échouait sur `AuthenticationConfiguration` : cause du harnais, pas du code métier.
- Suite backend par défaut (H2 en mémoire, JDK 21, `mvnw -B test`) : 182 tests exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS. Code de sortie Maven réel relu via `cmd /c` : 0. Le code 1 précédent venait du pipeline PowerShell (l'avertissement Mockito sur stderr est converti en erreur par PowerShell), pas de Maven. Les 10 ignorés sont les classes `*PostgresIntegrationTests` (profil PostgreSQL non activé). Les `*PostgresIT` relèvent de failsafe/profil `postgres-test` et n'ont pas été lancés (ils ciblent `et_test_db`).
- Limite : la base `et_a4_flyway_v1_22` est vide, donc les contrôles de données ont été faits sur la copie `et_restore_check` uniquement.
- Tests Maven/PostgreSQL A4 automatisés : **non écrits**.

**Statut Étape B (A4)** : PARTIELLEMENT TERMINÉE. Migrations, RESTRICT, NOT VALID puis VALID et comportements de suppression et d'unicité vérifiés manuellement. Reste : test d'intégration automatisé et démarrage web.

## 5. Anomalies ouvertes (numérotation Mission 8, confirmée par audit)

| # | Anomalie | Gravité | Preuve |
|---|---|---|---|
| A1 | Pas de `vehicles.partner_id`, pas d'entité Partner (package `partner` vide) | Bloquante MVP | V1, V3 ; `backend/src/main/java/tn/elitetunisia/partner/` |
| A2 | PENDING ne bloque pas les dates | Haute | `BookingService.BLOCKING` (l.19) ; EXCLUDE V4 |
| A3 | Maintenance autorisée avec CONFIRMED futur (contredit D-PA-16) | Haute | `VehicleService.requireStatusChangeAllowed` |
| A4 | `payments.quote_id` en ON DELETE CASCADE | Haute | V6 l.21 ; correction V22 non appliquée |
| A5 | Copie trompeuse de migrations dans `backend/src/main/resources/db/migration` | Moyenne | Vérifié (exclue du build par `pom.xml`) |
| A6 | Identifiants D-PA obsolètes (D-PA-08, 09, 13) dans review/ | Moyenne | review/09 |
| A7 | Build Angular et tests front non vérifiés (Node 18.20.8 signalé) | Moyenne | Environnement |
| A8 | Pas de rôle PARTNER dans `SecurityConfig` | Bloquante MVP | `SecurityConfig` l.85–95 |

Note : la numérotation Mission 7 précédente (A1 PENDING, A2 maintenance, A3 cascade, A4 partner_id) est remplacée par celle ci-dessus, qui suit l'ordre du brief Mission 8.

## 6. Décisions en attente de validation propriétaire (non bloquantes)

Les décisions MVP du document 17 sont appliquées sans attendre. Le propriétaire peut les revoir ; les valeurs commerciales réelles restent à fixer avant toute exploitation.

## 6bis. Inventaire et découpage du master prompt (état constaté le 2026-11-04)

Git : branche `main`, HEAD `78da39b`, 25 entrées de `git status` préexistantes (modifications de `booking/`, `payment/`, `SecurityConfig`, `ApiExceptionHandler`, `VehicleService`, tests, `docs/start-local.md`, `quote-detail.component.ts`, et fichiers non suivis). Aucune n'a été modifiée ni supprimée pendant ce tour.

Constat code :
- Backend : package `partner` vide (1 fichier, sans entité). Pas de garantie, pas de hold, pas de paiement simulé, pas de rôle PARTNER.
- Migrations actives : V1 à V22 (`database/migrations`). Pas de V23.
- Frontend : Angular 22, `engines.node` `^22.22.3 || ^24.15.0 || >=26`. Node installé 18.20.8 : incompatible, non modifié.
- Tests A4 automatisés : non écrits (fixture à construire à partir du schéma réel).

Découpage par lots (un lot = code + migration + tests + suivi) :
1. A4 automatisé (PostgreSQL, base temporaire nommée, identité vérifiée).
2. Partenaires : entité, INDIVIDUAL/COMPANY, statuts, `vehicles.partner_id`, historique, rôle PARTNER, V23.
3. Catalogue : statuts AVAILABLE/UNAVAILABLE/MAINTENANCE/ARCHIVED, archivage sans suppression.
4. Garanties : 90 jours max, EXCLUDE sur ACTIVE, identité partenaire immuable.
5. Disponibilité : recherche réelle, holds PENDING 30 min, expiration idempotente.
6. Réservations : confirmation revérifiée sous verrou.
7. Paiement simulé : idempotence, historique, confirmation après succès.
8. Administration, portail partenaire lecture seule, espace client, frontend, E2E.

### Lot 1 — A4 PostgreSQL automatisé : TERMINÉ (preuves réelles)

- Test : [PaymentQuotePostgresA4Test.java](../../../../../backend/src/test/java/tn/elitetunisia/payment/PaymentQuotePostgresA4Test.java).
- Cible : cluster 127.0.0.1:15432 uniquement, base temporaire `et_a4_it_<uuid>` créée puis supprimée par le test. Garde-fou refusant tout autre hôte/port, `et_test_db` et `et_dev_db`. Aucun accès au port 5432.
- Migrations : V1 à V22 appliquées par Flyway depuis `database/migrations` (répertoire actif) ; 22 succès vérifiés dans `flyway_schema_history`.
- Commande : `ELITE_A4_PG_TEST=true ELITE_A4_PG_URL=jdbc:postgresql://127.0.0.1:15432/postgres ELITE_A4_PG_USER=et_test ELITE_A4_PG_PASSWORD=<lu depuis pgpass.local> backend\mvnw.cmd -B -Dtest=PaymentQuotePostgresA4Test -Dsurefire.failIfNoSpecifiedTests=false test` (JDK 21).
- Résultat : **6 exécutés, 0 échec, 0 erreur, 0 ignoré, exit 0**.
  - `fk_payments_quote` = `ON DELETE RESTRICT` (`confdeltype='r'`).
  - Validation de `fk_payments_quote` : `NOT VALID` après V22 (attendu) ; `VALIDATE CONSTRAINT` exécuté après contrôle d'orphelins, dans une transaction annulée.
  - Suppression d'un devis ayant des paiements refusée : SQLSTATE `23001` (restrict_violation, attendu pour RESTRICT ; `23503` est réservé à NO ACTION), paiements conservés.
  - Second paiement CAPTURED pour un même devis refusé : SQLSTATE `23505`.
  - Statuts PENDING, FAILED acceptés en plus de CAPTURED.
  - Devis sans paiement supprimable.
  - Chaque scénario est annulé par rollback.
- Erreurs corrigées pendant la mise au point (fixture seulement, aucun changement de schéma) : `id` UUID sans défaut (généré par Hibernate), `customers.id` référence `users.id`, valeurs CHECK `PETROL`/`AUTOMATIC`, colonnes `discount_amount`/`tax_amount`.
- Limite : la contrainte de l'historique réel (copie de données de production) n'est pas disponible ; les tests portent sur un schéma vide.
- Base `et_a4_it_20261104` (créée au cours du lot) : conservée, non supprimée.

Statut lots : Lot 1 TERMINÉ. Lot 2 (Partner) TERMINÉ côté backend (voir section 6ter). Lots 3 à 8 NON COMMENCÉS. Frontend Angular du lot 2 BLOQUÉ (Node 18.20.8 < Node 22.22.3 requis) : non implémenté.

## 6ter. Lot 2 — Partner (backend)

- Migration : `database/migrations/V23__partner_foundations.sql` (après relecture de V1 à V22). Ajoute à `partners` : address, city, tax_identifier, user_id (UNIQUE partiel, FK RESTRICT), created_at, updated_at, deactivated_at, CHECKs type/nom/cohérence active-désactivé. Ajoute `vehicles.partner_id` (FK RESTRICT). Crée `vehicle_partner_assignments` (append-only par trigger : DELETE interdit, UPDATE limité à la fermeture de `ended_at`, unicité d'un rattachement courant par véhicule). Crée le rôle `PARTNER` et les permissions `partners:read`, `partners:manage`, `partner-portal:read`.
- Écart documentaire : la doc 04 prévoit V23 (partners + vehicles), V24 (garanties), V25 (permissions). Les permissions et le rôle PARTNER ont été regroupés dans V23. À arbitrer.
- Code : `partner/` (PartnerType, PartnerEntity, VehiclePartnerAssignmentEntity, repositories, PartnerService, PartnerDtos, AdminPartnerController, VehiclePartnerAssignmentController, PartnerPortalController). `VehicleEntity.partner`, `VehicleService.delete` refuse la suppression si historique (409 `VEHICLE_HAS_PARTNER_HISTORY`). `SecurityConfig` : `/api/partner/**` exige `ROLE_PARTNER` et `partner-portal:read`.
- Décisions : `commission_rate` conservée dans `partners` (suppression relevant de D1) ; un partenaire ne se supprime pas (FK RESTRICT) ; le transfert est refusé si réservation CONFIRMED ou IN_PROGRESS (le filtre de garantie active `ends_at > now` sera ajouté au lot 4) ; l'affectation n'est jamais automatique.
- Tests exécutés (JDK 21, exit 0) :
  - `PartnerServiceTest` : **13 exécutés, 0 échec**. Corrections pendant la mise au point : import `DomainException` (`common.exception`) et `UserRepository` (`auth.repository`), vérification d'audit invalide, stub `save` manquant.
  - `PartnerMigrationPostgresTest` (V1→V23 sur base isolée `et_a4_it_*`) : **7 exécutés, 0 échec**.
  - `PaymentQuotePostgresA4Test` : **6 exécutés, 0 échec** après fixation de la cible Flyway à V22 (le test appliquait désormais V23, ce qui fausse le comptage « 22 succès » ; V23 est couverte par `PartnerMigrationPostgresTest`).
  - Suite backend complète `mvnw.cmd -B clean test` (H2, profil par défaut) : **195 exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS, exit 0**.
- Régressions corrigées pendant le lot : accolade manquante dans `VehicleService.delete` (masquée par des classes obsolètes dans `target/`, d'où le `clean`), `PagedResponse` mal construit dans `AdminPartnerController`.
- Commande PostgreSQL (variables d'environnement, mot de passe lu depuis `pgpass.local` sans affichage) : `mvnw.cmd -B -Dtest=PartnerMigrationPostgresTest,PaymentQuotePostgresA4Test test`. Sans les variables, ces tests sont **ignorés silencieusement** (surveiller `Tests run: 0`).

- Validation complémentaire (session suivante, JDK 21, exit réels) :
  - `PartnerSecurityMockMvcTest` (MockMvc réel, Spring Security) : **13 exécutés, 0 échec**. Couvre 401 anonymes, 403 PARTNER sur routes admin et écritures, 405 sur écriture du portail (lecture seule), isolation (véhicule d'un autre partenaire = 404 `VEHICLE_NOT_FOUND`), liste filtrée, SUPER_ADMIN refusé sur `/api/partner/me`.
  - `PartnerAssignmentHistoryTest` : **3 exécutés, 0 échec** (première affectation, transfert avec conservation des anciennes lignes et un seul rattachement actif, rejet vers partenaire inactif sans modification de l'historique).
  - Correction globale : `ApiExceptionHandler` renvoie désormais 405 (et non 500) pour une méthode HTTP non supportée. Changement de comportement à signaler.
  - PostgreSQL isolé (variables `ELITE_A4_PG_*`) : `PartnerMigrationPostgresTest` 7/7 et `PaymentQuotePostgresA4Test` 6/6, exit 0.
  - `PartnerServiceTest` : 13/13.
  - Suite complète `mvnw.cmd -B clean test` (H2) : **211 exécutés, 0 échec, 0 erreur, 10 ignorés, exit 0**.

Non fait dans le lot 2 (limites explicites) :
- Harmonisation documentaire V23/V24/V25 (doc 04) : **à faire**.
- Concurrence sur les affectations : verrou pessimiste (`findLocked`) présent, mais pas de test de charge concurrent.
- Frontend Angular : non implémenté (Node 18 bloque le build).
- Le transfert n'inspecte pas encore les garanties actives (lot 3/4).


## 6quater. Lot 3 — Catalogue véhicules (backend)

- Constat d'audit : marques, modèles (véhicules), catégories, caractéristiques, images, tarifs, filtres, tri, pagination, catalogue public et routes admin existaient déjà et étaient protégés (`@PreAuthorize` `vehicles:read` / `vehicles:manage`). Le statut `ARCHIVED` était absent (Java et SQL). `RESERVED` n'est jamais écrit par le code (à déprécier, non tranché). `RENTED` est écrit au pickup.
- Migration : `database/migrations/V24__vehicle_archived_status.sql` (DROP/ADD de `chk_vehicles_status` avec `ARCHIVED`). Aucune donnée modifiée, V1 à V23 non touchées.
- Code : `VehicleStatus.ARCHIVED` ; `VehicleService.requireStatusChangeAllowed` refuse UNAVAILABLE, MAINTENANCE et ARCHIVED si une réservation IN_PROGRESS ou CONFIRMED à venir existe (code `VEHICLE_HAS_ACTIVE_BOOKING`). Le retour vers AVAILABLE reste toujours autorisé, donc l'archivage est réversible.
- Suppression physique : toujours refusée si historique de réservations ou d'affectations partenaire (409). L'archivage est la voie de retrait.
- Tests ajoutés : `VehicleLifecycleProtectionTest` (+4 : archivage refusé avec IN_PROGRESS et CONFIRMED à venir, archivage autorisé sans réservation sans suppression, restauration vers UNAVAILABLE) ; `CataloguePublicMockMvcTest` (7 : visibilité AVAILABLE seulement, détail 200/404, marque inactive masquée, filtres/tri/pagination, tri invalide, prix invalides, archivage admin 401 puis 200 et disparition du public) ; `PartnerMigrationPostgresTest` (+1 : `vehicleStatusAcceptsArchivedAndRejectsUnknownValues`, chaîne attendue V1 à V24).
- Frontend : `catalogue-api.service.ts` étendu à `'ARCHIVED'` (non vérifié, Node 18.20.8 bloque le build Angular 22).

Preuves réelles (JDK 21, variables `ELITE_A4_PG_*` sur le cluster 15432 uniquement) :
  - Ciblés : `PartnerMigrationPostgresTest` **8/8**, `CataloguePublicMockMvcTest` **7/7**, `VehicleLifecycleProtectionTest` **14/14**, exit 0.
  - Suite complète `mvnw.cmd -B clean test` : **236 exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS, exit 0**. Les 10 ignorés sont préexistants (`BookingEngineTests` 2, `InvoicePostgresIntegrationTests` 2, `QuoteEngineTests` 1, `PaymentPostgresIntegrationTests` 5).
  - Écart à noter : le lot 2 annonçait 211 tests, la suite actuelle en compte 236. La différence de 25 n'est pas entièrement expliquée par les 12 tests ajoutés au lot 3 (non réconciliée).
- Limites : pas de test de montée de version ARCHIVED sur données historiques réelles (copie non disponible) ; la visibilité publique ignore les holds (anomalie A2, lot 5).

## 6quinquies. Lot 4 — Garanties et transferts (backend)

- Migration : `database/migrations/V25__vehicle_guarantees.sql`. Le numéro V24 est déjà pris par ARCHIVED (lot 3) ; la doc 04 prévoyait « V24 garanties » : écart à harmoniser au lot 8. V1 à V24 non modifiées.
- Modèle : table `vehicle_guarantees` (FK RESTRICT vers `vehicles`, `partners`, `users`), statuts ACTIVE et WITHDRAWN, durée maximale de 90 jours (CHECK), motif non vide, cohérence du retrait.
- Chevauchement : protégé par trigger PL/pgSQL (`SELECT ... FROM vehicles FOR UPDATE` puis contrôle, erreur 23P01). Choix d'un trigger plutôt qu'une contrainte `EXCLUDE` pour éviter l'extension `btree_gist` (privilèges et disponibilité non vérifiés). Périodes semi-ouvertes : une garantie qui se termine à T n'empêche pas une garantie qui commence à T.
- Immuabilité : DELETE refusé (23001) ; UPDATE accepté uniquement pour ACTIVE vers WITHDRAWN, les colonnes d'engagement restant figées. Le partenaire engagé est dérivé du véhicule au moment de la création et ne change jamais, même si `vehicles.partner_id` change ensuite.
- Retrait : refusé si une réservation CONFIRMED ou IN_PROGRESS du même véhicule chevauche la garantie (pas de FK entre `bookings` et garanties) ; autorisé seulement si ACTIVE et `ends_at > now`. Interprétation à valider.
- Transfert : `PartnerService.assign` refuse `VEHICLE_TRANSFER_BLOCKED_BY_GUARANTEE` si une garantie ACTIVE couvre l'instant présent ou futur. Le refus des holds PENDING est reporté au lot 5.
- Partenaire désactivé : interdit de créer une nouvelle garantie ; ses garanties existantes restent consultables.
- Concurrence : création, transfert et réservation verrouillent la ligne `vehicles` (FOR UPDATE / PESSIMISTIC_WRITE).
- API : `AdminGuaranteeController` (`/api/admin/guarantees`, lecture `partners:read`, écriture `partners:manage`) ; `GET /api/partner/guarantees` (portail PARTNER, limité à son partenaire, lecture seule).
- Code : `GuaranteeStatus`, `GuaranteeEntity`, `GuaranteeRepository`, `GuaranteeService`, `AdminGuaranteeController`, DTO `GuaranteeRequest` / `GuaranteeSummary` dans `PartnerDtos`.
- Point à noter : `ApiExceptionHandler` mappe toujours tout 23P01 vers `VEHICLE_NOT_AVAILABLE`. Le service garanties intercepte avant, mais ce mapping global reste un risque.

Preuves réelles (JDK 21, PostgreSQL isolé sur 15432 uniquement, base temporaire `et_a4_it_*`) :
  - Ciblés (`GuaranteeServiceTest`, `PartnerServiceTest`, `PartnerSecurityMockMvcTest`, `PartnerAssignmentHistoryTest`, `PartnerMigrationPostgresTest`) : **17 + 13 + 18 + 3 + 17 = 68 tests, 0 échec, 0 erreur, 0 ignoré, exit 0** (lu dans les rapports Surefire).
  - `PartnerMigrationPostgresTest` 17/17 : chaîne V1 à V25 complète (25 succès), chevauchement 23P01, adjacence acceptée, durée 90 j acceptée et 92 j refusée, DELETE et réécriture refusés, partenaire engagé conservé après changement, retrait unique, concurrence réelle avec verrou observé dans `pg_stat_activity`.
  - `PartnerSecurityMockMvcTest` : 18 tests (et non 20 comme estimé auparavant ; le fichier contient 18 `@Test`). Couvre anonyme refusé, PARTNER 403 sur admin, lecture partenaire limitée, POST portail 405, lecture admin des garanties.
  - Suite complète `mvnw.cmd -B clean test` : **267 exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS, exit 0**. Par rapport au lot 3 (236), la hausse de 31 vient notamment de `GuaranteeServiceTest` (+17) et de MockMvc (+5). La réconciliation exacte par classe reste partielle.
- Non vérifié : comportement des `createdAt`/`updatedAt` lors des UPDATE Hibernate ; effet du CHECK `ck_partners_deactivated_consistent` sur données historiques réelles (copie non disponible).
- Logs de run à la racine du dépôt (non suivis, à supprimer sur accord) : `full-lot2.log`, `history-partner.log`, `mockmvc-partner.log`, `pg-lot2.log`, `lot3-targeted.log`, `lot3-full.log`, `lot4-compile.log`, `lot4-pg.log`, `lot4-targeted.log`, `lot4-full.log`.

## 6sexies. Lot 5 — Holds et disponibilité (backend)

- Migration : `database/migrations/V26__booking_holds.sql` (non modifiée depuis sa création, V1 à V25 intactes). Ajoute `bookings.hold_expires_at`, `bookings.guarantee_id` (FK vers `vehicle_guarantees`), statut EXPIRED, contrainte `ck_bookings_pending_hold`, index, backfill de l'expiration des PENDING existants (created_at + 30 min).
- Garde de disponibilité : trigger `guard_bookings_availability` (BEFORE INSERT OR UPDATE) avec `clock_timestamp()`, verrou `vehicles FOR UPDATE`, contrôle de chevauchement CONFIRMED/IN_PROGRESS/hold actif, EXPIRED terminal, hold échu non confirmable, CONFIRMED exige une garantie ACTIVE couvrant toute la période.
- Pourquoi un trigger et non une contrainte `EXCLUDE` seule : une contrainte statique bloquerait les dates à vie après expiration d'un hold. La contrainte `bookings_no_blocking_overlap` (V4) reste pour CONFIRMED et IN_PROGRESS.
- Expiration : `BookingService.expireHold` (idempotent, refuse un hold actif et ne touche jamais une réservation confirmée) ; `BookingHoldExpirationWorker` (`@Scheduled`, `app.booking.hold-expiration-scan-ms`, lot de 100, erreurs journalisées par identifiant).
- Confirmation : `BookingService.transition(CONFIRMED)` re-vérifie la disponibilité hors soi-même, refuse un hold échu (`BOOKING_HOLD_EXPIRED`), vérifie que la garantie couvrante est la garantie d'origine.
- Anomalie A2 (`VehicleSpecification.availableDuring` ignorait les holds PENDING actifs) : corrigée, couverte par des tests (`BookingEngineTests`, 3 nouveaux tests de disponibilité).
- Transfert : `PartnerService.assign` refuse `VEHICLE_TRANSFER_BLOCKED_BY_BOOKING` si un hold PENDING actif existe (point reporté par le lot 4, désormais couvert par `PartnerServiceTest#transferIsRefusedWhenActiveHoldExists`).
- Documentation d'ordre de verrouillage : `docs/architecture/booking-lock-order.md`.

Preuves réelles (JDK 21, PostgreSQL isolé `127.0.0.1:15432`, base temporaire `et_a4_it_*` supprimée après test) :
  - `BookingHoldPostgresTest` : **15 exécutés, 0 échec, 0 erreur, 0 ignoré, exit 0** (log `lot5-holdpg.log`). Couvre : hold de 30 min par défaut, hold actif bloque, hold expiré ne bloque plus, réservation confirmée bloque un hold, expiration une seule fois (second appel sans effet), hold actif non expirable, réservation confirmée non expirée, EXPIRED terminal, hold échu non confirmable, garantie exigée, concurrence réelle sur deux connexions pour chevauchement (un seul gagnant, SQLSTATE 23P01), concurrence réelle sur confirmation d'un même hold (un seul gagnant), expiration qui survit à un redémarrage sans répétition.
  - `BookingEngineTests` : 11 exécutés, 0 échec, 2 ignorés (les 2 tests PG sont conditionnés à `ELITE_POSTGRES_TEST`, donc non exécutés, voir limites).
  - `PartnerServiceTest` : **14 exécutés, 0 échec, 0 erreur, 0 ignoré** après ajout de `transferIsRefusedWhenActiveHoldExists` (`lot5-partnersvc.log`, exit 0, `clean test`).
  - Suite complète `mvnw.cmd -B clean test` (JDK 21, variables `ELITE_A4_PG_*` vers 15432, `ELITE_POSTGRES_TEST` retiré) : **285 exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS, exit 0** (`lot5-fullsuite.log`, lu dans les 50 rapports Surefire). Les 10 ignorés sont tous conditionnés à `ELITE_POSTGRES_TEST` (BookingEngineTests 2, InvoicePostgresIntegrationTests 2, PaymentPostgresIntegrationTests 5, QuoteEngineTests 1). Cette suite a été lancée avant l'ajout du test de transfert ; ce test a été vérifié par la passe ciblée (14/14). Une nouvelle suite complète sera relancée en fin de lot 6.

Limites du lot 5 :
- Les tests `ELITE_POSTGRES_TEST` ne sont pas exécutés : ils ne peuvent viser que `et_test_db`, interdite. Ils ne constituent donc pas une preuve.
- Le déclenchement réel de `@Scheduled` n'est pas testé. La logique d'expiration (idempotence, reprise) l'est, mais pas le planificateur lui-même.
- `ApiExceptionHandler` mappe tout 23P01 vers `VEHICLE_NOT_AVAILABLE`. Un chevauchement de garantie ou de hold peut donc afficher un message « véhicule déjà réservé » trompeur. Risque de libellé, non de cohérence des données ; non corrigé dans ce lot.

## 6septies. Lot 6 — Paiement simulé et confirmation (backend)

- Règle gelée appliquée : D-PA-10 (`review/17-mission7-arbitrage-scope-freeze.md`, ligne 17) : confirmation après paiement simulé intégral, aucun pourcentage d'acompte en MVP. D-PA-09 (ligne 30) : pénalités et remboursements réels exclus. Les documents d'analyse plus anciens (`business-decision-analysis.md` D10, `partner-vehicle-availability-analysis.md` D5) restent en « ouvert » et sont obsolètes sur ce point.
- Paiement : `PaymentService.createForQuote` verrouille la réservation (`BookingRepository.findLocked`), puis refuse `BOOKING_NOT_PAYABLE` (réservation absente ou non PENDING) et `BOOKING_HOLD_EXPIRED` (hold échu), puis `QUOTE_ALREADY_PAID` et `PAYMENT_IN_PROGRESS`. Sur CAPTURED : `capturedAt` renseigné et hold étendu jusqu'au début de la réservation (`BookingEntity.extendHoldUntil`, extension uniquement).
- Confirmation : `BookingService.transition(CONFIRMED)` exige le devis le plus récent, ACCEPTED et cohérent (`QUOTE_NOT_ACCEPTED`), puis un paiement CAPTURED pour ce devis (`PAYMENT_REQUIRED`). `expireHold` ne touche jamais une réservation ayant un paiement CAPTURED (`PaymentRepository.existsByBooking_IdAndStatusIn`) ; `BookingRepository.findDueHoldIds` les exclut.
- Invariants base : V21 (un seul CAPTURED par devis, SQLSTATE 23505), V22 (`fk_payments_quote` ON DELETE RESTRICT, SQLSTATE 23001). Aucun trigger « paiement avant confirmation » : volontaire, il casserait les fixtures historiques. La règle est appliquée par le service.
- Changement de comportement à signaler : une confirmation sans devis, ou sans paiement CAPTURED pour le dernier devis, est désormais refusée (`PAYMENT_REQUIRED`). Auparavant elle réussissait.
- Codes d'erreur : `PAYMENT_REQUIRED`, `BOOKING_NOT_PAYABLE`, `BOOKING_HOLD_EXPIRED` tombent dans le mapping par défaut (409) de `ApiExceptionHandler`.

Preuves réelles (JDK 21, PostgreSQL isolé `127.0.0.1:15432`, base temporaire `et_a4_it_*` supprimée après test, utilisateur `et_test`) :
  - `PaymentBookingPostgresTest` : **4 exécutés, 0 échec, 0 erreur, 0 ignoré, exit 0** (`lot6-pg-payment.log`). Tests : `capturedPaymentIsUniquePerQuoteWhileOtherStatusesAreAccepted`, `concurrentCapturedPaymentsHaveSingleWinner` (deux connexions, un seul gagnant), `capturedPaymentIsPreservedWhenQuoteDeletionIsAttempted` (suppression refusée, SQLSTATE 23001), `pendingHoldCanBeExtendedWithoutOverlapConflict`.
  - `CustomerPaymentControllerMockMvcTest` : **5 exécutés, 0 échec, 0 erreur, 0 ignoré, exit 0** (`lot6-mockmvc-payment.log`). Ajouts : `quoteOwnedByAnotherCustomerIsNotFound` (404, `QUOTE_NOT_FOUND`, l'identifiant client du principal est transmis au service) et `paymentOnBookingWithExpiredHoldIsRefused` (409, `BOOKING_HOLD_EXPIRED`).
  - `PaymentServiceUnitTest` : 5 tests verts, dont `rejectsPaymentWhenBookingIsNotPending` et `rejectsPaymentWhenHoldIsDue`.
  - `BookingEngineTests` : `confirmationRequiresFullPaymentOfAcceptedQuote` ajouté, réservations de test payées avant confirmation (helper `payInFull`). `QuoteEngineTests` : confirmation agence testée en `PAYMENT_REQUIRED`, puis paiement, puis confirmation.
  - Suite complète `mvnw.cmd -B clean test` (JDK 21, variables `ELITE_A4_PG_*` vers 15432, `ELITE_POSTGRES_TEST` absent) : **295 exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS, exit 0** (`lot6-full-suite2.log`). Les rapports Surefire XML concordent avec le résumé Maven. Les 10 ignorés sont les mêmes que lot 5, tous conditionnés à `ELITE_POSTGRES_TEST`.

Limites du lot 6 :
- Pas de remboursement ni de pénalité (D-PA-09). Une annulation ou un refus d'une réservation payée laisse le paiement CAPTURED pour réconciliation manuelle.
- Une réservation PENDING payée dont le début est passé n'est pas expirée automatiquement. Son hold est étendu jusqu'au début, puis `holdDue` refuse la confirmation : traitement manuel requis.
- Un paiement sur un devis qui n'est pas le plus récent n'est pas refusé au moment du paiement. La confirmation échoue alors proprement.
- Inversion de l'ordre de verrouillage : `PaymentService` verrouille le devis puis la réservation, alors que `docs/architecture/booking-lock-order.md` prévoit Véhicule, Réservation, Devis. Aucun cycle identifié (confirmation et expiration ne prennent que le verrou réservation), mais la doc et le code doivent être réconciliés.
- `BookingLockOrderPostgresIT` et `BookingAuditAtomicityPostgresIT` ne sont pas exécutés par défaut (gardés par `ELITE_POSTGRES_TEST`) : le comportement du lot 6 y reste non vérifié (ils compilent).
- Deux tests PG gardés sont probablement obsolètes : `BookingEngineTests#concurrentConfirmationsAllowOnlyOneBlockingBookingOnPostgres` et `QuoteEngineTests#acceptedQuotesStillAllowOnlyOneConcurrentConfirmationOnPostgres`. Une seconde création sur les mêmes dates entre en conflit avec le hold actif. Non exécutés, non corrigés.
- Facturation non déclenchée : `InvoiceService.createFromQuote` n'a aucun appelant de production. Ni le paiement ni la confirmation ne produisent de facture. Décision propriétaire requise (ajout de comportement métier).
- Aucune intégration bancaire réelle : `SimulatedPaymentProvider` (CAPTURED si montant > 0, sinon FAILED). Un PROVIDER_ERROR laisse le paiement PENDING sans relance automatique.

## 6octies. Stabilisation backend avant frontend

Rapport détaillé : [backend-stabilization-before-frontend.md](./backend-stabilization-before-frontend.md).

- Tests PostgreSQL : les tests gardés par `ELITE_POSTGRES_TEST` étaient silencieusement ignorés (le profil Surefire effaçait la variable). Le profil `postgres-test` la transmet désormais à Surefire et Failsafe. Les tests PG utilisent une base temporaire `et_a4_it_<12 hex>` sur `127.0.0.1:15432`, supprimée à la fin de la JVM.
- Ordre de verrouillage : `PaymentService.createForQuote` passe à Véhicule → Réservation → Devis (contre Devis → Réservation avant). `docs/architecture/booking-lock-order.md` corrigé. Limite documentée : les chemins Transfer et Driver prennent Réservation puis Chauffeur, sans verrou véhicule. Aucun cycle démontré.
- Conflits SQLSTATE 23P01 : `ApiExceptionHandler` distingue la garantie chevauchante (`GUARANTEE_OVERLAP`), la période bloquée (`VEHICLE_NOT_AVAILABLE`), et le 23514 « aucune garantie active » (`VEHICLE_NOT_AVAILABLE`). Les autres cas restent un 409 générique sans détail SQL. Test : `SqlConflictMappingMockMvcTest` (6/0/0/0).
- Planificateur des holds : `BookingHoldSchedulerPostgresIT` (5/0/0/0 sur PG) déclenche le vrai `@Scheduled`, vérifie l'expiration, la conservation des réservations payées et confirmées, l'idempotence et la concurrence (au plus un gagnant). Redémarrage réel de la JVM non simulé.
- Tests obsolètes `BookingEngineTests#concurrentConfirmationsAllowOnlyOneBlockingBookingOnPostgres` et `QuoteEngineTests#acceptedQuotesStillAllowOnlyOneConcurrentConfirmationOnPostgres` : réécrits selon les règles actuelles (hold actif, paiement intégral avant confirmation). Ils ne sont plus ignorés.
- Harmonisation documentaire : roadmap (`implementation-roadmap.md` T-C03, T-C04, T-D01, T-D02), étude benchmark (§8.2, note de correspondance), `partner-availability/11-implementation-roadmap.md` (lignes 20–21). Doc 04 était déjà à jour (V23 à V26). Les migrations appliquées n'ont pas été modifiées.
- Facturation : `InvoiceService.createFromQuote` reste sans appelant. Décision propriétaire requise (déclencheur : paiement ou confirmation). Non câblé.

Preuves (JDK 21, PostgreSQL `127.0.0.1:15432`, base temporaire supprimée par les tests) :
  - `mvnw.cmd -B clean verify -Ppostgres-test` (exit 0, `stab-full-verify.log`) : Surefire **300 exécutés, 0 échec, 0 erreur, 0 ignoré** ; Failsafe **41 exécutés, 0 échec, 0 erreur, 0 ignoré** ; total **341, 0/0/0, BUILD SUCCESS**.
  - Note : `mvn clean test` seul n'exécute pas les `*IT` (Failsafe). Le total complet passe par `verify`.
  - Les 12 lignes ERROR du log viennent des scénarios de migration volontairement en échec de `MigrationWithExistingDataIT` (rollback Flyway attendu).

Limites : pas de test de cycle de vie de paiement sur PG (analyse statique seulement) ; pas de test de verrouillage mixte dynamique ; pas de sauvegarde/restauration exercée ; CI non exécutable localement ; frontend non buildable (Node 18.20.8).

## 8. Point de reprise (fin de passage)

- Stabilisation backend avant frontend (section 6octies) : TERMINÉE pour le périmètre backend. Suite complète `verify` : 341 exécutés, 0 échec, 0 erreur, 0 ignoré (exit 0).
- Lots 1, 2, 3, 4 et 5 : backend TERMINÉS, validés par tests réels (voir sections 6ter, 6quater, 6quinquies, 6sexies).
- Lot 6 (paiement simulé intégral, confirmation après paiement) : backend TERMINÉ et validé (section 6septies). Chiffres de fin de lot 6 conservés pour l'historique (295/0/0/10 ignorés, sans PG) ; le total actuel est 341/0/0/0 avec PG.
- Lot 7 (frontend) : PARTIELLEMENT VALIDÉ, voir [frontend-lot-7-report.md](./frontend-lot-7-report.md). Livré et vérifié : partenaires et garanties admin, espace partenaire, parcours public (accueil, catalogue, filtres, détail, galerie, disponibilité), réservation et devis, hold de 30 minutes et expiration, paiement simulé, espace client, détail et PDF de facture, expiration de session avec refresh unique et redirection. Preuves : Vitest `threads` 109/109 ; build de production exit 0 (2 avertissements non bloquants) ; 22 scénarios navigateur Playwright réussis contre une API simulée. Non validé : tout test contre un backend réellement démarré (rôle `et_test` refusé sur `127.0.0.1:15432`, aucune base temporaire créée). Reste : décision sur le déclencheur de `InvoiceService.createFromQuote`, historique des paiements sans endpoint.
- Lot 8 : rapport final produit ([final-mvp-validation.md](./final-mvp-validation.md)). Doc 04 harmonisée avec V23 à V26. Reste à arbitrer : facturation déclenchée ou non par paiement/confirmation ; remboursement et pénalités (hors MVP) ; réservation payée dont le début est passé.
- Nettoyage à valider : base `et_a4_it_20261104` présente sur 15432 (origine inconnue, non supprimée) ; logs racine `lot*.log` (non suivis, conservés).
- Blocages connus : frontend non buildable avec Node 18.20.8 global (Angular 22) ; build et tests OK avec Node 22.22.3 portable de session ; logs de run à la racine du dépôt à supprimer sur accord ; écart de comptage lot 2 (211) vs suite actuelle partiellement réconcilié ; `ApiExceptionHandler` mappe tout 23P01 vers `VEHICLE_NOT_AVAILABLE` ; `scripts/test-backend-postgres.ps1` ne doit jamais être exécuté (recrée `et_test_db`).


## 6nonies. Lot 7 — Frontend (PARTIELLEMENT VALIDÉ)

Rapport détaillé : [frontend-lot-7-report.md](./frontend-lot-7-report.md).

- Implémenté (code présent, tests unitaires Vitest) : `/admin/partners` (liste, création, modification, activation/désactivation, affectation et historique), `/admin/guarantees` (liste, filtre véhicule, création, retrait), `/partner` (profil, véhicules, garanties, lecture seule), catalogue public et détail véhicule (statut affiché « Ouvert à la réservation » ; disponibilité réelle seulement via `/availability` ou `/available`), réservation et devis, paiement simulé (idempotence, codes métier), affichage du hold de 30 minutes, espace client (profil, réservations, devis, paiements, liste et détail des factures avec PDF), accueil public refondu en français.
- Preuves (Node 22.22.3 portable, session PATH uniquement) :
  - `npx ng test --watch=false` (Vitest, pool `threads`, Node 22.22.3 portable) : 16 fichiers, **109 passés, 0 échec, exit 0** (`%TEMP%\lot7e-test2.log`, passe finale).
  - Stabilité du pool `threads` : runs antérieurs verts (84/84 ×3, 86/86) ; aucun échec intermittent observé.
  - `npx ng build --configuration production` : **exit 0** (`%TEMP%\lot7e-build.log`), avec 2 avertissements non bloquants : budget `home.component` (6,36 kB / 4 kB, introduit par la réécriture de l'accueil) et `jsbi` CommonJS.
  - Navigateur (Playwright 1.63, Chromium headless shell, API simulée sur `127.0.0.1:4300`) : `frontend/tests/browser-lot7/lot7-journeys.e2e.ts`, **22 passés, 0 échec, exit 0** (`%TEMP%\lot7-pw-run3.log`). Ces scénarios ne prouvent pas l'intégration backend.
  - Backend réel / HTTP : **non exécuté** (section 5.4 du rapport).
  - `npm audit --omit=dev` et `npm audit` : 0 vulnérabilité (`lot7-npm-audit-prod.log`, `lot7-npm-audit-full.log`). Ces audits datent d'une étape antérieure ; aucune dépendance n'a été modifiée dans cette passe.
- Corrections : `ARCHIVED` ajouté au statut véhicule admin ; runner Vitest en pool `threads` (`forks` expire localement) ; historique d'affectation visible en lecture pour AGENT (`partners:read`), formulaire d'affectation réservé à SUPER_ADMIN et MANAGER ; code d'erreur backend propagé par l'intercepteur (`ApiHttpError.code`) ; `err.error.code` corrigé dans le paiement (branche `PAYMENT_IN_PROGRESS` qui ne s'exécutait jamais) ; libellé « Disponible » remplacé sur le détail véhicule.
- Non couvert : tests HTTP contre un backend démarré (bloqués, pas de mot de passe valide pour le rôle de test) ; rendu navigateur sur backend réel ; historique des paiements (pas d'endpoint). Corrigé dans cette passe : détail et PDF de facture, expiration de session, accueil. Brief Lot 7 sections 15-16 introuvable.
- Aucun commit, push ni déploiement. Migrations inchangées. État Git : HEAD `78da39b` sur `main`, 168 entrées (51 `M`, 117 `??`), `docs/architecture/` non suivi et conservé.

## 7. Progression réelle du MVP

- Documentation de conception : avancée.
- Code métier partenaires : lot 2 backend TERMINÉ et testé (V23, API, service, sécurité MockMvc, historique). Lot 3 catalogue backend TERMINÉ (V24 ARCHIVED). Lot 4 garanties et transferts backend TERMINÉ (V25, trigger de chevauchement, API admin et portail, refus de transfert). Lot 5 holds et disponibilité backend TERMINÉ (V26, garde de disponibilité, expiration, concurrence PG). Lot 6 paiement simulé et confirmation backend TERMINÉ (paiement intégral, confirmation gardée, pas de double CAPTURED). Lot 7 frontend : PARTIELLEMENT VALIDÉ (section 6nonies) : code livré, tests unitaires, navigateur sur API simulée ; HTTP contre backend réel non exécuté.
- Frontend Angular : build de production exit 0 (2 avertissements non bloquants) ; 109 tests Vitest passés avec Node 22.22.3 portable (session PATH uniquement, sans installation). Global Node 18.20.8 inchangé et incompatible. Détail dans la section 6nonies.
- A3 : TERMINÉE (test 10/10). A4 : TERMINÉE (lot 1, 6/6 tests PostgreSQL).
- Tests backend : suite complète `mvnw.cmd -B clean test` = **295 exécutés, 0 échec, 0 erreur, 10 ignorés (exit 0)** à la fin du lot 6 (285 à la fin du lot 5). `BookingHoldPostgresTest` 15/15 et `PaymentBookingPostgresTest` 4/4 sur PostgreSQL isolé `127.0.0.1:15432`.

Mission 8 : audit documentaire réalisé ; code métier partenaires toujours non implémenté. Prochaine étape : Mission 9 (fondations partenaires), après l'étape 0 et l'étape 1 de [23-mission8-implementation-roadmap.md](./23-mission8-implementation-roadmap.md).
