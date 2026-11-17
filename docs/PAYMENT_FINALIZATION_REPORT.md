# Elite Tunisia — Rapport de finalisation paiement

## Section migrations V21 à V33 (Phase 2)

### Périmètre et méthode

- Validation en lecture seule sur la base `elite_a4_test`. Aucune migration n'a été relancée, aucun DDL exécuté, aucune donnée modifiée.
- Aucune suppression, aucune recréation de base, aucun changement de privilège, aucun rôle créé.

### Source réelle des migrations

- Source : `database/migrations/` (V1 à V33).
- `backend/pom.xml` (lignes ~145–153) exclut `db/migration/**` des ressources et copie `database/migrations` vers `db/migration` au build.
- `backend/src/main/resources/db/migration/` ne contient que V11 et trois fichiers désactivés `_DISABLED_V4`, `_DISABLED_V5`, `_DISABLED_V6`.
- `backend/src/main/resources/application.yml` (ligne 14) : `flyway.locations: classpath:db/migration`.

### Environnement

- PostgreSQL 18.3 sur Windows, `127.0.0.1:15432`.
- Base : `elite_a4_test` (seule base utilisée).
- Rôle : `elite_test`, non superutilisateur, sans `createdb`. Les privilèges `CREATE` sur `public` et `CONNECT` sur `elite_a4_test` sont présents.
- Rôle `elite_a4_tester` : absent. Les quatre classes SQL brutes restent NON VALIDÉES tant qu'un administrateur ne le crée pas (script préparé : `docs/audits/grant-a4-tester-createdb.sql`, non exécuté).
- Outil : Python 3 + `psycopg2` 2.9.13 (psql non installé). Mot de passe lu depuis un fichier local, jamais affiché.

### Historique Flyway

- `flyway_schema_history` : 33 lignes, toutes `success = True`, version maximale 33.
- V21 à V33 étaient déjà appliquées (installées le 2026-11-13 à 17:19:14). Elles n'ont pas été exécutées par cette phase.
- Vérification des checksums : 33 sur 33 correspondent aux fichiers actuels de `database/migrations/`. Algorithme : CRC32 ligne par ligne (BOM retiré, terminateurs exclus), conforme à Flyway.

### Résultats par migration

| Migration | Contenu vérifié | Résultat |
|---|---|---|
| V21 | Index unique partiel `uq_payments_one_captured_per_quote` (`quote_id` WHERE `status='CAPTURED'`) | Présent. Doublons CAPTURED par devis : 0 |
| V22 | `fk_payments_quote` en `ON DELETE RESTRICT` | Règle `r` confirmée. Validée par V33 |
| V26 | Holds sur bookings, trigger `guard_bookings_availability` | Voir anomalie A1 |
| V27 | Index unique `uq_payments_customer_quote_idempotency` (customer, quote, key) ; ancien `payments_idempotency_idx` supprimé | Présent. Ancien index : absent. Doublons (customer, quote, key) : 0 |
| V28 | `uq_invoices_one_main_per_quote` (WHERE quote_id IS NOT NULL) | Présent. Doublons : 0 |
| V29 | `uq_invoices_one_main_per_booking` (WHERE quote_id IS NOT NULL) | Présent. Doublons : 0 |
| V30 | Tables `billing_requests` et `billing_request_history`, contraintes, index | Contraintes `ck_billing_requests_done`, `ck_billing_requests_pending_due`, `uq_billing_requests_booking_kind`, index `ix_billing_requests_due`, `ix_billing_request_history_request` présents |
| V31 | Colonnes heartbeat sur `billing_backfill_lock`, table `billing_backfill_runs`, permission `billing:backfill` | Colonnes présentes, table présente. `billing:backfill` accordé au seul SUPER_ADMIN |
| V32 | Suppression du blocage PENDING, expiration automatique, `ck_bookings_pending_hold`, index `idx_bookings_vehicle_blocking` et `bookings_no_blocking_overlap` (CONFIRMED/IN_PROGRESS) | Voir anomalie A1 |
| V33 | Validation de `fk_payments_quote` et `fk_bookings_guarantee` | Les deux validées. Orphelins : 0 |

### Contrôles SQL de données (lecture seule)

| Contrôle | Résultat |
|---|---|
| Paiements CAPTURED en double par devis (V21) | 0 |
| Paiements orphelins sans devis (V22) | 0 |
| Doublons (customer, quote, idempotency_key) (V27) | 0 |
| Doublons de clé globale (V27) | 8 cas (`same-key-*`, `scope-*`). Autorisés : V27 remplace l'index global |
| Factures principales en double par devis (V28) | 0 |
| Factures principales en double par réservation (V29) | 0 |
| billing_requests DONE sans facture ou sans `completed_at` (V30) | 0 |
| billing_requests PENDING sans `next_attempt_at` (V30) | 0 |
| Réservations COMPLETED sans demande de facturation (V30) | 0 |
| Demandes sur réservations non COMPLETED (V30) | 0 |
| MANUAL_RETRY sans acteur ou sans raison (V30) | 0 |
| Permission `billing:backfill` | SUPER_ADMIN uniquement |
| `billing_backfill_lock` | 1 ligne, `run_id` NULL (libre) |
| `billing_backfill_runs` | vide |

Volumes observés : 2082 réservations, 1824 paiements (CAPTURED 1737, UNKNOWN 72, MANUAL_REVIEW 7, FAILED 7, PENDING 1), 60 factures (56 principales, 4 complémentaires), 12 billing_requests (toutes DONE).

### Catalogue de contraintes et d'index (vérifié le même jour)

- Index V21, V27, V28, V29 : définitions conformes aux fichiers SQL.
- Index V30 : présents comme décrit.
- Colonnes V31 `billing_backfill_lock` : `id`, `run_id`, `actor_id`, `started_at`, `heartbeat_at`.
- Privilèges du rôle `elite_test` sur `billing_requests`, `invoices`, `billing_backfill_lock` : SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER.

### Anomalies détectées

**A1 — Dérive V26 → V32 sur les holds PENDING (non corrigée, décision requise).**
- 840 réservations PENDING ont `hold_expires_at` NULL (créées 2026-08 : 4 ; 2026-11-13 : 177 ; 2026-11-16 : 423 ; 2026-11-17 : 236).
- Cause : V32 utilise `CREATE OR REPLACE FUNCTION guard_bookings_availability`, ce qui supprime la valeur par défaut de 30 minutes à l'INSERT introduite par V26. L'en-tête de V32 ne mentionne pas cette suppression. La fonction en base ne contient pas la logique de 30 minutes (vérifié via `pg_get_functiondef`).
- Côté Java, `BookingEntity.java` (ligne 38, getter ligne 89) mappe la colonne sans jamais la renseigner.
- Effet métier actuel : une réservation PENDING ne bloque pas la disponibilité et n'expire pas. C'est cohérent avec la décision V32, mais la suppression de la valeur par défaut est un effet de bord non documenté.
- Action : décision métier requise. Si la valeur par défaut doit être restaurée, elle doit passer par une nouvelle migration V34 après approbation. Rien n'a été modifié.

### Corrections réalisées

- Aucune. Aucune migration n'a été modifiée, relancée ou marquée artificiellement.

### Permissions manquantes

- Rôle `elite_a4_tester` absent. Requis pour les tests SQL bruts des quatre classes concernées. Intervention administrateur nécessaire. Aucun contournement par superutilisateur n'a été utilisé.

### Tests non exécutés et raisons

- Validation sur base PostgreSQL vierge : non exécutée. Créer une base de comparaison n'est pas autorisé par les garde-fous (« aucune base autre que `elite_a4_test` »).
- Rejeu complet des migrations : non exécutable sur `elite_a4_test`, car déjà appliquées.
- Redémarrage applicatif sur base peuplée : non exécuté dans cette phase.
- Tests de violation de contrainte en transaction annulée : non exécutés dans cette phase.

### Limites restantes

- Pas de validation sur base vierge ni de rejeu : preuve d'application existante seulement.
- Dérive A1 non tranchée.
- Rôle `elite_a4_tester` absent.
- Les quatre classes SQL brutes restent NON VALIDÉES.

### Verdict Phase 2 (migrations)

**PARTIELLEMENT VALIDÉE.**
- Validé : application de V21 à V33 (Flyway, 33/33), checksums, contraintes et index présents, FK validées, données cohérentes sur les contrôles listés, permissions du rôle `elite_test`.
- Non validé : cohérence V26/V32 sur les holds (A1), rejeu et base vierge, quatre classes SQL brutes.
