# 01 — Audit de l'existant : partenaires et disponibilités

Périmètre : analyse en lecture seule. Aucun fichier source, migration ou configuration n'a été modifié. Git : HEAD `78da39b`, modifications locales préexistantes conservées.

## 1. Documents et code inspectés

| Source | Constat |
|---|---|
| `docs/architecture/partner-vehicle-availability-analysis.md` | Analyse antérieure, cohérente avec le code relu ici. |
| `docs/architecture/vehicle-lifecycle-protection-report.md` | Protections du cycle de vie implémentées (voir §3). |
| `docs/architecture/lots-2-4-final-report.md` | Rapport précédent des lots 2 à 5. |
| `docs/architecture/booking-lock-order.md`, `docs/technical-audit.md`, etc. | Référencés par les missions précédentes ; non relus intégralement dans cette mission. |
| `backend/.../partner/` | Un seul fichier : `package-info.java`. **Aucune entité, service ni contrôleur.** |
| `backend/.../vehicle/VehicleEntity.java` | Aucune colonne ni relation vers un partenaire. |
| `backend/.../booking/BookingService.java` | `availability()` et `requireAvailable()` vérifient le statut du véhicule et les chevauchements de réservations bloquantes. |
| `backend/.../vehicle/VehicleSpecification.java` | `availableDuring()` exclut les véhicules ayant une réservation `CONFIRMED` ou `IN_PROGRESS` qui chevauche la période. |
| `backend/.../vehicle/PublicVehicleController.java` | Expose `/api/vehicles`, `/api/vehicles/{id}`, `/api/vehicles/available`. |
| `backend/.../common/config/SecurityConfig.java` | `GET /api/vehicles/**` est public ; `/api/admin/**` exige `admin:access`. |
| `database/migrations/V1__initial_schema.sql` | Table `partners` et colonne `documents.owner_user_id` présentes. |
| `database/migrations/V3`, `V4`, `V5`, `V6`, `V8` | Catalogue, moteur de réservation (EXCLUDE de chevauchement), devis, paiements, opérations. |
| `database/proposed/V21__…`, `V22__…` | Propositions **non appliquées** (dossier `proposed`, hors `migrations`). |
| `backend/src/main/resources/db/migration/` | Ne contient que `V11__create_booking_driver_assignments.sql` et des fichiers `_DISABLED_V4/V5/V6`. **Ce n'est pas une copie complète** de `database/migrations/` : une divergence de chemin existe et doit être documentée avant toute évolution Flyway. |
| `backend/.../payment/PaymentEntity.java` | `booking` nullable, `quote` obligatoire. Aucun champ de commission ni de snapshot financier. |
| `frontend/src/app/features/bookings/README.md` | Indique « NOT IMPLEMENTED », mais le code frontend contient un formulaire de réservation et un appel de disponibilité : **le README est obsolète**. |

## 2. Modèle partenaire : présent en schéma, absent du code

La table `partners` (V1) contient : `id`, `name`, `partner_type`, `email`, `phone`, `commission_rate` (NUMERIC 5,2, 0–100), `active`.

- Aucune entité JPA, aucun repository, aucun endpoint ne la référence (recherche `"partners"` sans résultat dans `backend/src/main/java`).
- `vehicles` (V1, V3) **n'a aucune clé vers `partners`** : impossible aujourd'hui de savoir quel partenaire possède un véhicule.
- `commission_rate` sur `partners` **contredit la décision D1** validée (taux par niveau commercial du véhicule, administré par Elite, snapshot par réservation). Ce champ ne doit pas être utilisé en l'état ; sa suppression ou son remplacement relève d'une migration future et d'une décision explicite.
- `documents.owner_user_id` relie des documents à un utilisateur, pas à un partenaire.

**Conclusion : le module partenaire n'existe qu'à l'état de schéma orphelin.**

## 3. Protections existantes (confirmées par le code)

- Verrou pessimiste véhicule puis réservation (`BookingService.locked`, `VehicleService.findLocked`).
- Contrainte PostgreSQL `bookings_no_blocking_overlap` (EXCLUDE gist, V4) : empêche deux réservations bloquantes qui se chevauchent sur un même véhicule.
- Refus de changement de statut vers `UNAVAILABLE` ou `MAINTENANCE` pendant une réservation bloquante, refus de suppression d'un véhicule ayant un historique (mission cycle de vie, tests H2 verts).
- `availability()` et la recherche `/available` n'affichent que les véhicules sans réservation bloquante.

Ces protections couvrent la **concurrence des réservations**. Elles ne couvrent **pas** la garantie commerciale d'Elite (voir §4).

## 4. Écarts entre l'existant et le besoin validé

| # | Besoin validé | Existant | Écart |
|---|---|---|---|
| E1 | Un véhicule n'est affiché que s'il est garanti par Elite pour toute la période | La recherche n'affiche que l'absence de réservation bloquante et le statut `AVAILABLE` | **Critique** : un véhicule non confirmé par son partenaire peut être proposé. |
| E2 | Chaque véhicule rattaché à son partenaire | Aucune relation | **Bloquant** pour l'isolation partenaire. |
| E3 | Disponibilité enregistrée par Elite (période, véhicule, partenaire, confirmation, admin, traçabilité) | Aucune table | **Bloquant**. |
| E4 | Recherche par période couvrant intégralement la garantie | Recherche par chevauchement avec réservations seulement | Même cause que E1. |
| E5 | Partenaire actif, véhicule publié et commercialement actif | `vehicles.status` seul ; `partners.active` non utilisé | Écart. |
| E6 | Isolation des données partenaires (API et base) | Aucun rôle, aucune permission, aucun filtre partenaire | **Bloquant**. |
| E7 | Commission par niveau commercial, snapshot par réservation (D1) | `commission_rate` sur `partners`, rien sur les réservations ni les paiements | Écart de modèle. |
| E8 | Pas de double réservation | EXCLUDE V4 + verrou véhicule | **Couvert**. |
| E9 | Réservation hors période garantie impossible | Non vérifié car la garantie n'existe pas | Bloquant. |

## 5. Problèmes et risques constatés

Problèmes confirmés :
- P1 : `GET /api/vehicles/available` et la réservation ne tiennent pas compte d'une garantie d'Elite (n'existe pas).
- P2 : Table `partners` orpheline avec un champ `commission_rate` contraire à D1.
- P3 : `backend/src/main/resources/db/migration/` n'est pas la source de vérité complète ; risque de confusion Flyway.
- P4 : README frontend des réservations obsolète.
- P5 : `PaymentEntity` sans snapshot financier (D1, D3).

Risques potentiels à vérifier (non testés) :
- R1 : comportement de la clé étrangère `bookings.vehicle_id` sans `ON DELETE` lors d'une suppression (mission précédente, non vérifié sur PostgreSQL).
- R2 : la réservation passe même si la disponibilité partenaire est retirée, car elle n'existe pas encore.

## 6. Limites de cet audit

- Tests PostgreSQL non exécutés (interdits pour cette mission).
- Build Angular non exécuté (Node 18.20.8 non conforme).
- Migrations V21/V22 non appliquées et non vérifiées sur une base réelle.
- Aucune copie représentative de données historiques n'est disponible.
