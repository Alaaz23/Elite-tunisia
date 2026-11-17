# Mission 8 — Audit backend (lecture seule)

**Périmètre** : `backend/src/main/java/tn/elitetunisia/` et `backend/src/test/java/`.
**Référence de l'état** : arbre de travail courant. Ce dépôt contient des modifications locales non commitées (voir §0) ; les constats portent sur ce qui est présent sur disque, pas sur `HEAD`.
**Mode** : lecture seule. Aucun fichier source modifié, aucun test PostgreSQL exécuté.

## 0. Modifications locales préexistantes (à préserver)

`git status --short` retourne 23 entrées avant et après cette mission. Parmi elles, des fichiers backend déjà modifiés hors de cette mission :

- Modifiés : `booking/BookingOperationService.java`, `booking/BookingRepository.java`, `booking/BookingService.java`, `common/config/SecurityConfig.java`, `common/exception/ApiExceptionHandler.java`, `payment/PaymentRepository.java`, `payment/PaymentService.java`, `vehicle/VehicleService.java`, ainsi que trois tests existants.
- Non suivis : `BookingAuditAtomicityPostgresIT.java`, `BookingLockOrderPostgresIT.java`, `SecurityHeadersTest.java`, `PaymentQuoteCapturePostgresIT.java`, `PaymentQuoteInvariantUnitTest.java`, `VehicleLifecycleProtectionTest.java`, `scripts/test-backend-postgres.ps1`, `database/proposed/`, `docs/technical-audit.md`.

Ces fichiers n'ont été ni lus en détail par cette mission ni exécutés. Ils ne sont pas attribués à cette mission.

## 1. Architecture et packages

| Package | État | Constat |
|---|---|---|
| `booking` | Implémenté | Service, entité, statuts, verrou pessimiste (`BookingRepository.findLocked`, `@Lock(PESSIMISTIC_WRITE)`). |
| `quote`, `payment`, `invoice` | Implémentés | Devis, paiement simulé (`payment/provider/SimulatedPaymentProvider`), factures. |
| `vehicle`, `catalogue`, `brand`, `category` | Implémentés | Catalogue public filtré par `VehicleService.isPublic` (statut AVAILABLE + marque et catégorie actives). |
| `auth` | Implémenté | JWT, refresh tokens, `LoginThrottle`. |
| `common` | Implémenté | audit, config, exception, pricing, storage, validation. |
| `customer`, `driver`, `transfer`, `employee`, `notification` | Implémentés | Hors périmètre MVP. |
| `partner` | **Vide** | Seul `package-info.java` présent. Aucune entité, repository, service, contrôleur. |
| `report`, `service`, `document` | Vides | Placeholders. |

**Conclusion** : l'architecture modulaire permet d'ajouter un module `partner` et une évolution de `booking` et `vehicle` sans réécriture.

## 2. Entités et relations

- `VehicleEntity` : aucun champ `partner_id`, aucun état archivé. Statuts `VehicleStatus` = AVAILABLE, RESERVED, RENTED, MAINTENANCE, UNAVAILABLE (contrainte `chk_vehicles_status` dans `database/migrations/V3__vehicle_catalogue.sql`).
- `BookingEntity` : porte `status` (`BookingStatus` = PENDING, CONFIRMED, REJECTED, CANCELLED, IN_PROGRESS, COMPLETED), `vehicle`, `customer`, dates, montants. Aucune référence à une garantie (`availability_id`) : à créer.
- `PaymentEntity` (lecture seule, aucun changement) : `quote` LAZY non nullable, `booking` nullable, `amount` NUMERIC(12,3), `refundedAmount` par défaut à zéro, statut par défaut PENDING, pas de setter pour `quote`.
- Aucune entité `Partner` (table `partners` présente en V1 avec `commission_rate` qui ne doit pas être utilisé, cf. D1).

## 3. Services métier : réservation

### 3.1 Disponibilité
- `BookingService.BLOCKING` (ligne 19) = `CONFIRMED`, `IN_PROGRESS`. **PENDING ne bloque pas les dates.** Confirmé (anomalie A2).
- `BookingService.availability` (lignes 49–55) : refuse si `rentable(vehicle)` est faux, sinon compte les chevauchements sur `BLOCKING`.
- `BookingService.rentable` (lignes 193–197) : exige statut AVAILABLE et marque/catégorie actives.
- `requireAvailable` (ligne 197) : vérifie `rentable` puis `bookings.overlaps(..., BLOCKING, excluded)`.

### 3.2 Création
- `BookingService.create` (annotée `@Transactional`, ligne 73) : valide les dates, charge le client, puis `vehicle(request.vehicleId(), true)` **avec verrou pessimiste** (`vehicles.findLocked`), puis `requireAvailable`, puis `nextval('booking_reference_seq')`. La transaction démarre bien avant la vérification : le verrou véhicule sérialise les créations concurrentes sur le même véhicule.
- Le verrou est pris sur le véhicule ; la contrainte d'exclusion PostgreSQL `bookings_no_blocking_overlap` (V4) est la défense finale, mais elle ne couvre que CONFIRMED et IN_PROGRESS.

### 3.3 Transitions et verrous
- `BookingService` lignes 111–121 : `locked(id)` avec `findLocked` puis `entityManager.refresh`.
- Lignes 139–143 : passage à un état de devis exige un `QuoteStatus.ACCEPTED` cohérent avec la réservation.
- Lignes 149–152 : annulation admise uniquement depuis PENDING ou CONFIRMED.
- Lignes 181–185 : `lockForCommercialOperation` et `lockForOperation` (`Propagation.MANDATORY`). Un commentaire référence l'ordre de verrouillage `Vehicle → Booking → (Quote, Driver, Transfer)`, documenté dans `docs/architecture/booking-lock-order.md`. **Ce fichier n'a pas été vérifié** : existence à confirmer.

### 3.4 Maintenance et retrait de véhicule
- `VehicleService.requireStatusChangeAllowed` (lignes 155–167) :
  - `UNAVAILABLE` refusé si une réservation IN_PROGRESS **ou** CONFIRMED future existe.
  - `MAINTENANCE` refusé **uniquement** si IN_PROGRESS existe.
  - Confirmé (anomalie A3) : une maintenance peut être posée alors qu'une réservation CONFIRMED future existe. Le commentaire de code l'assume explicitement (« reste possible avant une réservation future »), ce qui contredit la règle D-PA-16 gelée en Mission 7.
- `VehicleService` lignes 102–139 : mise à jour, changement de statut, verrouillage via `findLocked`.

### 3.5 Concurrence
- `VehicleService.findLocked` et `BookingRepository.findLocked` utilisent `PESSIMISTIC_WRITE`. Bon point.
- Points à vérifier par test : interblocage entre création de réservation (véhicule puis réservation) et changement de statut (véhicule puis réservations). L'ordre semble cohérent, mais le test `BookingLockOrderPostgresIT.java` (non suivi) n'a pas été exécuté.

## 4. Paiements et devis

- `PaymentService` et `PaymentRepository` sont modifiés localement (cf. §0). Le contenu n'est pas audité en détail ici.
- `payments.quote_id` : `ON DELETE CASCADE` (V6). Un devis supprimé supprime ses paiements. Confirmé (anomalie A4). Le correctif proposé `database/proposed/V22` n'est pas appliqué.
- `payments_one_captured_per_quote` (V21 proposée, non appliquée) : pas de garantie en base qu'un seul paiement capturé existe par devis.
- `SimulatedPaymentProvider` est le seul fournisseur. Conforme à la décision MVP « paiement simulé uniquement ».

## 5. Sécurité et exceptions

- `SecurityConfig` (lignes 64–95) : CSRF par cookie `CookieCsrfTokenRepository.withHttpOnlyFalse()`, chemin `/api`, `Secure` si configuré, `SameSite=Strict`. CORS activé via `corsConfigurationSource` (ligne 104), origines lues depuis `app.cors.allowed-origins`.
- Routes publiques : `/api/bookings/policy`, `/api/bookings/quote-preview` (POST), catalogue GET, `/api/health`, auth.
- Routes admin : `/api/admin/**` exige `admin:access` ; transferts et services exigent des autorités supplémentaires.
- Routes client : `/api/customer/**` exige `hasRole("CUSTOMER")`.
- **Pas de rôle PARTNER** : `hasRole`/authorities ne contiennent que CUSTOMER et admin. D-PA-18 n'est pas implémentable sans ajout.
- `ApiExceptionHandler` est modifié localement ; comportement de fuite d'information non vérifié en détail.
- `common/exception` : codes métier `BookingException("QUOTE_NOT_ACCEPTED", ...)`, `DomainException.conflict("VEHICLE_HAS_ACTIVE_BOOKING")`, réutilisables pour les nouveaux refus (garantie, hold, transfert).

## 6. Audit (existant)

- `common/audit/AuditService` existe et est utilisé (table `audit_logs`, V2). Réutilisable pour les actions partenaires et administratives.
- `booking_events` (V4) et `quote_events` (V5) : historique métier existant.

## 7. Tests backend

| Fichier | Type | Exécuté dans cette mission |
|---|---|---|
| `BackendApplicationTests.java` (modifié) | Contexte Spring | Non |
| `booking/BookingOperationServiceTest.java` (modifié) | Unitaire | Non |
| `payment/PaymentServiceIdempotenceCompatibleTest.java` (modifié) | Unitaire | Non |
| `common/config/SecurityHeadersTest.java` (non suivi) | Unitaire/MVC | Non |
| `payment/PaymentQuoteInvariantUnitTest.java` (non suivi) | Unitaire | Non |
| `vehicle/VehicleLifecycleProtectionTest.java` (non suivi) | Unitaire | Non |
| `booking/BookingAuditAtomicityPostgresIT.java` (non suivi) | Intégration PostgreSQL | Non |
| `booking/BookingLockOrderPostgresIT.java` (non suivi) | Intégration PostgreSQL | Non |
| `payment/PaymentQuoteCapturePostgresIT.java` (non suivi) | Intégration PostgreSQL | Non |

Aucun test n'a été exécuté dans cette mission. Le script `scripts/test-backend-postgres.ps1` (non suivi) n'a pas été lancé, pour respecter la règle « aucun test PostgreSQL » de la mission et éviter tout contact avec le port 5432.

Aucun test de charge ni de concurrence réel n'existe.

## 8. Résumé des constats backend

| Réf. | Constat | Statut |
|---|---|---|
| B1 | Package `partner` vide, pas d'entité `Partner` | Confirmé |
| B2 | Pas de `vehicles.partner_id`, pas d'archivage véhicule | Confirmé |
| B3 | PENDING ne bloque pas les dates (`BookingService.BLOCKING`) | Confirmé (A2) |
| B4 | Maintenance autorisée avec CONFIRMED futur (`VehicleService.requireStatusChangeAllowed`) | Confirmé (A3) |
| B5 | `payments.quote_id` cascade (V6) | Confirmé (A4) |
| B6 | Pas de rôle PARTNER dans `SecurityConfig` | Confirmé |
| B7 | Pas de garantie, hold ni `availability_id` | Confirmé |
| B8 | Verrouillage pessimiste véhicule et réservation en place | Confirmé dans le code ; comportement sous concurrence non testé |
| B9 | Tests PostgreSQL existants non exécutés | Non vérifié |
