# Mapping statique entités JPA → migrations

## Portée et niveau de preuve

Ce document compare le code Java et les fichiers SQL présents dans `database/migrations`, puis distingue trois niveaux de preuve :

- **Statique** : lecture du code et des migrations.
- **H2** : le profil de test désactive Flyway et crée un schéma temporaire à partir des entités ; il ne valide pas ce mapping SQL.
- **PostgreSQL 18.3 (exécuté le 2026-10-24)** : cluster jetable isolé sur `127.0.0.1:15432`, base `et_test_db` créée vide. Flyway a appliqué V1→V16 (16 migrations réussies, 0 erreur SQL), Hibernate `ddl-auto=validate` accepte le schéma et l'application redémarre sans réappliquer de migration (« Schema "public" is up to date »). Le mapping est donc confirmé **pour une base vide**. Il n'est **pas** confirmé pour une base contenant déjà des données : aucune base existante n'a été utilisée.

Un schéma attendu par les entités a aussi été généré par Hibernate dans une base jetable puis comparé colonne par colonne au schéma Flyway. Écarts trouvés : table `transfers` absente (corrigée par V16), `vehicle_images.url` limité à 500 caractères alors que l'entité en autorise 1000 (corrigé par V16), et trois colonnes déclarées `nullable = false` côté entité mais nullables en base (`invoice_lines.metadata`, `payments.refunded_amount`, `quotes.vehicle_id`) : écart non bloquant, laissé en l'état car imposer `NOT NULL` pourrait échouer sur des données existantes.

Le POM du backend copie `database/migrations` vers `db/migration` et exclut `backend/src/main/resources/db/migration/**`. La source versionnée est la séquence V1 à V16 de `database/migrations`; les fichiers locaux `_DISABLED_*` ne sont pas des migrations Flyway.

## Entités et objets de schéma

| Entité / objet | Migrations statiques | Éléments attendus et dépendances |
|---|---|---|
| `BookingEntity` → `bookings` | V1, V4, V8 | V4 crée `booking_reference_seq`, renseigne les références existantes et met en place les contraintes de période ; `BookingService` consomme la séquence. Les champs métier et relations client/véhicule sont issus du schéma initial et des migrations ultérieures. |
| `QuoteEntity` → `quotes` | V5 | V5 crée `quote_reference_seq`, consommée par `QuoteService`, ainsi que les devis et leurs lignes/historiques. |
| `VehicleEntity` → `vehicles` | V1, V3 | V3 ajoute `price_per_week NUMERIC(12,3) NOT NULL` et sa contrainte positive. C'est cohérent pour une base neuve ; une base existante contenant des lignes sans valeur n'a pas de backfill automatique garanti. |
| `DriverEntity` → `drivers` | V1, V12 | V1 définit `id`, noms, téléphone, `license_number` (unique et requis) et `status`. V12 ajoute conditionnellement `license_expiry`, `certified`, `notes`, `active` et `created_at`, puis donne à `status` la valeur par défaut `ACTIVE` pour les insertions JPA qui ne mappent pas cette ancienne colonne. Le numéro de licence vient de V1 et n'est pas synthétisé par la migration. |
| `BookingDriverAssignmentEntity` → `booking_driver_assignments` | V11 | Table liée à `bookings` et `drivers`, contrainte `starts_at < ends_at`, index des deux clés étrangères. V11 vérifie `pg_constraint` avant d'ajouter ses clés étrangères. |
| Paiements → `payments`, `payment_events` | V1, V6, V10 | V6 renomme la table `payments` en `legacy_payments`, puis crée le nouveau modèle, sa clé d'idempotence et ses relations ; V10 ajoute la permission nécessaire. |
| Factures → `invoices`, `invoice_lines`, `invoice_events`, `invoice_payments` | V1, V7, V9 | V7 renomme l'ancienne table, crée le nouveau modèle et `invoice_seq`; V9 ajuste le type devise. `InvoiceService` consomme la séquence. |
| Permissions factures client / chauffeurs | V13, V14 | V13 accorde `invoices:read` au rôle `CUSTOMER`. V14 crée `drivers:manage` et l'accorde à `SUPER_ADMIN`, `MANAGER` et `AGENT`, conformément aux contrôles d'accès des endpoints d'administration chauffeur. |
| Permission annuaire clients | V15 | Crée `customers:read` et la donne à `SUPER_ADMIN` et `MANAGER`, rôles autorisés à consulter les données personnelles via `/api/admin/customers`. |
| `TransferEntity` → `transfers`, longueur `vehicle_images.url` | V16 | Crée `transfers` (clés étrangères nullables vers `drivers` et `vehicles` avec `ON DELETE SET NULL`, trois index, `status` par défaut `PENDING`) : sans cette table, Hibernate `validate` empêchait l'application de démarrer sur PostgreSQL. Élargit aussi `vehicle_images.url` à `VARCHAR(1000)`. Aucune règle métier de transfert n'a été modifiée. |

## Points de vigilance avant toute base préexistante

Ces points viennent de la lecture des migrations ; seul le cas « base vide » a été exécuté. Aucune de ces migrations n'a été testée sur une base contenant des données.

- V2 ajoute `employees.first_name`, `last_name` et `phone` en `NOT NULL` sans valeur par défaut, et supprime `users.role_key` : échec si `employees` contient des lignes.
- V6 et V7 renomment `payments` et `invoices` en `legacy_payments` / `legacy_invoices` puis créent un nouveau modèle **sans copier les données** : les paiements et factures antérieurs resteraient dans les tables `legacy_*`, invisibles pour l'application. Inspecter d'abord l'historique Flyway et prévoir une reprise explicite.
- V3 impose `vehicles.price_per_week`, `currency` en `NOT NULL` sans valeur par défaut : échec si `vehicles` contient des lignes.
- V4 ajoute une contrainte d'exclusion anti-chevauchement sur `bookings` : échec si des réservations bloquantes se chevauchent déjà ; elle exige l'extension `btree_gist` (le rôle doit avoir le droit de la créer).
- V10, V13, V14, V15 et V3/V4 insèrent des permissions sans `ON CONFLICT` : échec si ces clés existent déjà (par exemple seedées manuellement).
- V12 suppose le modèle canonique `drivers` de V1. Si une base non canonique manque `license_number`, ne pas inventer de valeur ; corriger explicitement ses données et contraintes avant validation.
- Écarts de nullabilité entité/base restants : `invoice_lines.metadata`, `payments.refunded_amount`, `quotes.vehicle_id`.
- Les tables `partners`, `notifications`, `documents`, `driver_documents`, `service_requests`, `booking_services` existent dans les migrations mais n'ont pas d'entité ni d'API : aucune fonctionnalité n'est confirmée pour elles.

## Vérifications séparées

- **Code/migrations :** inspection statique ; relation ci-dessus.
- **H2 :** `.\mvnw.cmd -DskipITs test` : 88 tests, 0 échec, 0 erreur, 10 ignorés, avec `ddl-auto=create-drop` et Flyway désactivé. Cela prouve le comportement sur H2, pas le schéma PostgreSQL.
- **PostgreSQL 18.3 isolé (exécuté) :** `.\mvnw.cmd -Ppostgres-test verify` sur une base `et_test_db` vide : Flyway V1→V16 réussies (16/16, `success`), puis surefire **88 tests, 0 échec, 0 erreur, 0 ignoré** et failsafe **3 ITs, 0 échec** (`DriverAssignmentPostgresIT`, `PostgresInvoiceConcurrencyIT`, `PostgresIdempotencyIT`) : BUILD SUCCESS. Schéma résultant vérifié : 16 entrées d'historique, séquences `booking_reference_seq`, `invoice_seq`, `quote_reference_seq`, contrainte d'exclusion `bookings_no_blocking_overlap`, extension `btree_gist`, 88 index, tables `legacy_invoices` / `legacy_payments`, permissions `customers:read`, `drivers:manage`, `invoices:read`, `payments:create` attribuées aux rôles attendus. Démarrage de l'application (profil `dev`, `validate`) : UP, puis redémarrage sans migration réappliquée.
- **Non vérifié :** migration d'une base contenant des données (aucune base existante n'a été utilisée) ; PostgreSQL d'une autre version majeure que 18.
