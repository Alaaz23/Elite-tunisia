# Mission 8 — Audit base de données (lecture seule)

**Périmètre** : `database/migrations/` (V1 à V20, appliquées par Flyway), `database/proposed/` (V21, V22, non appliquées), `backend/pom.xml` et `application.yml`.
**Aucune base touchée, aucune migration exécutée, aucun test PostgreSQL lancé.** Le port 5432 n'a pas été utilisé.

## 1. Distinction des états

| Catégorie | Contenu | État |
|---|---|---|
| Migrations présentes et source de vérité | `database/migrations/V1`…`V20` | Présentes |
| Copie compilée | `backend/target/classes/db/migration` identique (hash vérifié en Mission 8 précédente) | Présente, dérivée au build |
| Copie dans `backend/src/main/resources/db/migration` | Seulement V11 et trois `_DISABLED_V4/V5/V6` | Trompeuse, exclue du build (`pom.xml`, lignes ~140–155) |
| Propositions | `database/proposed/V21__payments_one_captured_per_quote.sql`, `V22__payments_quote_fk_restrict.sql` | **Non appliquées** |
| Schéma réellement appliqué sur une base | Inconnu : aucune base de référence accessible en lecture dans cette mission | **Non vérifiable** |

Flyway lit `classpath:db/migration` (`application.yml`, ligne 16). Le build copie `database/migrations` ; le dossier `src` est donc non autoritatif.

**Risque** : deux répertoires de migrations au nom identique peuvent induire en erreur une future modification. À documenter, sans les supprimer (règle de préservation).

## 2. Chronologie des migrations (lue par grep ciblé)

| Version | Contenu principal | Points de contrôle |
|---|---|---|
| V1 | Schéma initial : users, roles, customers, vehicles, brands, categories, partners, quotes, invoices, refresh_tokens | `partners` (l.181) avec `partner_type VARCHAR(40) NOT NULL` et `commission_rate` |
| V2 | users/roles/permissions, audit | `chk_audit_logs_result`, index `idx_audit_logs_actor_time` |
| V3 | Catalogue véhicules | `chk_vehicles_status` (AVAILABLE, RESERVED, RENTED, MAINTENANCE, UNAVAILABLE), prix positifs, index d'image primaire unique |
| V4 | Moteur de réservation | `CREATE EXTENSION btree_gist` ; `bookings_reference_unique` ; `bookings_status_check` ; `bookings_no_blocking_overlap` EXCLUDE GIST |
| V5 | Devis | `quotes_booking_version_unique` ; `quote_lines_amount_check` |
| V6 | Paiements | `payments_idempotency_idx` UNIQUE ; `fk_payments_quote` **ON DELETE CASCADE** ; `fk_payments_booking` ON DELETE SET NULL |
| V7 | Factures | `invoices.reference` UNIQUE ; cascades sur lignes et paiements de facture |
| V11 | Affectations chauffeurs | `chk_bda_start_end` |
| V12–V15 | Champs chauffeur, etc. | Non détaillés (hors périmètre MVP) |
| V16 | Transferts, longueur image | Corrige un mapping Hibernate sans migration |
| V17 | Alignement de nullabilité | Ne comble aucune valeur métier inventée (commentaire de migration) |
| V18 | Permission facture SUPER_ADMIN | Corrige un accès admin aux factures |
| V19 | Transferts liés aux réservations | `transfers.booking_id` FK **ON DELETE RESTRICT** ; `transfers_status_check` NOT VALID |
| V20 | Permissions catalogue services | `ON CONFLICT DO NOTHING` |

## 3. Constats sur les contraintes et suppressions

### 3.1 Chevauchement des réservations
- `bookings_no_blocking_overlap` (V4) : EXCLUDE GIST, plage `tstzrange [)`, **restreinte aux statuts CONFIRMED et IN_PROGRESS**.
- Conséquence : deux réservations PENDING peuvent se chevaucher sur les mêmes dates. La protection PENDING n'existe pas. Confirmé (anomalie A2).
- L'extension `btree_gist` est bien créée (V4 l.1), donc l'EXCLUDE GIST mixant `vehicle_id` et plage est syntaxiquement possible. Son comportement réel sous PostgreSQL 18 n'a pas été testé (non vérifié).

### 3.2 Paiements et devis
- `payments.quote_id` : **ON DELETE CASCADE** (V6, l.21). Supprimer un devis supprime ses paiements, y compris un paiement capturé. Confirmé (anomalie A4). Risque métier : perte de la trace d'un encaissement.
- `database/proposed/V22` remplace par RESTRICT en DROP + ADD dans une même transaction, avec `NOT VALID`, puis `VALIDATE` hors transaction. Proposition cohérente ; **non appliquée**, donc non effective.
- `database/proposed/V21` : index unique partiel `WHERE status = 'CAPTURED'`. Le commentaire mentionne une vérification en lecture seule datée (`quotes_with_multiple_captured = 0`). **Non vérifiable dans cette mission** : aucune base à laquelle accéder. À reproduire sur copie avant application.

### 3.3 Transferts
- `transfers.booking_id` : FK ON DELETE RESTRICT (V19). Protège l'historique.
- `transfers.driver_id` et `vehicle_id` : ON DELETE SET NULL (V16). Une suppression de véhicule peut effacer le lien historique du transfert. Risque à noter pour l'historique.

### 3.4 Factures
- Cascades `invoice_lines`, `invoice_payments` sur `invoices` (V7) : cohérentes entre elles. Une facture supprimée supprime ses lignes ; à valider face à la règle de conservation des factures (non vérifiée).

### 3.5 Véhicules et archivage
- `vehicles` n'a pas de colonne `archived_at` ni `partner_id` (V1, V3). Confirmé.
- Aucune migration n'ajoute encore `partners.vehicle_partner_assignments`, `guaranteed_availabilities`, `booking.availability_id`, ni les colonnes de hold.

## 4. Index et performance

- Index sur `bookings (status, starts_at)` (V4) ; index EXCLUDE GIST sur chevauchement.
- Index sur `quotes (customer_id, created_at DESC)`, `quotes (status, valid_until)` (V5).
- Index catalogue `vehicles (status, created_at DESC)` et `(brand_id, category_id)` (V3).
- Manque probable pour MVP : index sur les lignes de disponibilité garantie `(vehicle_id, starts_at, ends_at)` et sur les holds PENDING `(status, hold_expires_at)`. À créer avec le moteur de disponibilité.

## 5. Verrouillage et transactions (côté code)

- `BookingRepository.findLocked` et `VehicleRepository.findLocked` utilisent `PESSIMISTIC_WRITE` (cf. backend audit §3.5).
- L'ordre de verrouillage documenté : Vehicle → Booking → (Quote, Driver, Transfer). Le document de référence `docs/architecture/booking-lock-order.md` est cité dans le code ; son existence n'a pas été vérifiée dans cette mission.

## 6. Sauvegarde et restauration

- Aucun script de sauvegarde ni de restauration n'a été trouvé dans les dossiers lus (non exhaustif). **Non vérifié.**
- Aucune procédure documentée de restauration de test : à créer (voir plan, étape 0).
- Aucune copie représentative de données historiques n'est disponible : toute migration sur données réelles reste bloquée.

## 7. Tests PostgreSQL

- Tests présents mais **non exécutés** : `BookingLockOrderPostgresIT`, `BookingAuditAtomicityPostgresIT`, `PaymentQuoteCapturePostgresIT` (non suivis).
- Script `scripts/test-backend-postgres.ps1` (non suivi) : non lancé.
- Règle respectée : pas de test sur 5432 ; un test sur 15432 n'a pas été lancé car il n'était pas explicitement sûr pour cette mission.

## 8. Synthèse des constats

| Réf. | Constat | Fichier | Statut |
|---|---|---|---|
| D1 | Pas de `vehicles.partner_id` ni d'archivage | V1, V3 | Confirmé |
| D2 | EXCLUDE ne couvre pas PENDING | V4 | Confirmé |
| D3 | Cascade paiement → devis | V6 l.21 | Confirmé |
| D4 | Proposition V21 / V22 non appliquée | `database/proposed/` | Confirmé |
| D5 | Dossier `src/.../db/migration` trompeur | `backend/src/main/resources` | Confirmé |
| D6 | Affirmation de vérification read-only dans V21 | V21 l.4 | Non vérifiable |
| D7 | Schéma réel d'une base quelconque | — | Non vérifiable |
| D8 | Sauvegarde/restauration | — | Non vérifiable |
| D9 | Suppression véhicule : FK SET NULL sur transferts | V16 | Confirmé, à décider |
