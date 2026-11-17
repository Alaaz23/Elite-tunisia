# Mission 8 — Correspondance règles métier ↔ réalisation technique

Référence métier : [17-mission7-arbitrage-scope-freeze.md](./17-mission7-arbitrage-scope-freeze.md). Les règles sont **des hypothèses MVP**, non des décisions validées par le propriétaire. Aucune règle n'est modifiée ici.

Légende : **Réutilisable** = existe et peut être étendu ; **À construire** = absent ; **Corriger** = existe mais contredit la règle.

## 1. Règles de la Mission 7

| Règle | Exigence | Réutilisable | À construire / corriger | Contraintes BDD | Risque concurrence | Tests requis |
|---|---|---|---|---|---|---|
| D-PA-01 | Garantie bornée, expiration auto, max 90 j | `VehicleService`, `RentalPricingService` (calcul de durée) | Entité `guaranteed_availabilities`, job d'expiration (ou expiration calculée à la lecture) | CHECK `ends_at > starts_at` et `ends_at - starts_at <= 90 jours` ; index `(vehicle_id, starts_at, ends_at)` | Deux créations de garanties simultanées | Unitaire sur la borne ; intégration sur expiration |
| D-PA-14 | Refus de chevauchement de garanties ACTIVE | Modèle EXCLUDE GIST de V4 (`bookings_no_blocking_overlap`) | Contrainte d'exclusion sur `guaranteed_availabilities` restreinte à `status = 'ACTIVE'` | EXCLUDE GIST `(vehicle_id WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (status='ACTIVE')` | Forte : sans verrou, deux garanties peuvent passer | Intégration PostgreSQL : deux insertions concurrentes |
| D-PA-04 | Hold PENDING 30 min avec expiration | `BookingEntity`, `BookingStatus.PENDING`, `booking_events` | `hold_expires_at` ; prise en compte des PENDING valides dans `availability` ; libération à expiration | Index `(status, hold_expires_at)` ; CHECK cohérence PENDING ↔ hold | Hold concurrent sur le même créneau ; expiration pendant confirmation | Unitaire sur expiration ; intégration sur deux holds concurrents |
| D-PA-07 | Retrait interdit si CONFIRMED ou IN_PROGRESS référence la garantie | `VehicleService.requireStatusChangeAllowed` (modèle de refus) | Vérification sur la garantie : retrait refusé si réservation liée | FK `bookings.availability_id` RESTRICT | Retrait pendant création de réservation : verrou garantie puis véhicule | Intégration : retrait refusé pendant réservation |
| D-PA-12 | Transfert partenaire interdit si engagements bloquants | `vehicle_partner_assignments` (à créer, cf. doc 12) | Table d'historique + contrôle (garantie active, CONFIRMED futur, IN_PROGRESS, hold actif) | Index unique partiel « un rattachement actif par véhicule » | Transfert concurrent avec une réservation : verrou véhicule | Intégration : transfert refusé avec chaque engagement |
| D-PA-10 / D5 | Confirmation après paiement simulé intégral | `SimulatedPaymentProvider`, `PaymentService`, `QuoteEntity` | Lien paiement → réservation en PENDING → CONFIRMED ; idempotence déjà présente (`payments_idempotency_idx`) | Corriger `fk_payments_quote` (V22 proposée) | Double paiement : V21 (CAPTURED unique par devis) | Intégration : paiement simulé, rejeu idempotent |
| D-PA-15 | Partenaire INDIVIDUAL ou COMPANY | `partners.partner_type` existe (V1) | Entité `Partner` et validation de l'enum | CHECK sur `partner_type` (à vérifier V1) | Faible | Unitaire enum |
| D-PA-16 | Maintenance interdite si CONFIRMED futur | `VehicleService.requireStatusChangeAllowed` | **Corriger** : `MAINTENANCE` doit refuser aussi `confirmedAhead` | Aucune | Maintenance concurrente avec création : verrou véhicule déjà présent | Intégration : maintenance refusée avec CONFIRMED futur |
| D-PA-17 | Aucune nouvelle garantie pour partenaire désactivé | `partners.active` (V1) | Contrôle dans le service de garantie | Aucune | Désactivation concurrente : verrou partenaire | Unitaire + intégration |
| D-PA-18 | Rôle PARTNER lecture seule, données propres | Mécanisme `hasAllAuthorities` de `SecurityConfig`, `AuditService` | Rôle `PARTNER`, routes `/api/partner/**`, filtrage par `partner_id` du JWT | Index `vehicle_partner_assignments(partner_id)` | Faible | Intégration sécurité : accès croisé refusé |
| Archivage véhicule | Pas de suppression physique | `VehicleStatus.UNAVAILABLE`, `VehicleService.updateStatus` | Colonne `archived_at` ou statut dédié | Interdire DELETE physique (pas de route) | Faible | Intégration : archive sans suppression |

## 2. Anomalies bloquantes

| Réf. | Règle impactée | Fichier / classe | Correction recommandée | Dépendances |
|---|---|---|---|---|
| A1 | Partner absent, `vehicles.partner_id` absent | V1, V3 ; package `partner` vide | Migration additive V23 : table `vehicle_partner_assignments` + `partner_id` dérivé de l'historique (non deviné) | Aucune |
| A2 | PENDING ne bloque pas | `BookingService.BLOCKING`, EXCLUDE V4 | Ajouter les PENDING non expirés à la disponibilité ; EXCLUDE sur PENDING valide | A4 pour l'expiration |
| A3 | Maintenance avec CONFIRMED futur | `VehicleService.requireStatusChangeAllowed` | Ajouter `confirmedAhead` au cas `MAINTENANCE` | Aucune |
| A4 | Cascade paiement → devis | V6 l.21 ; proposition V22 | Appliquer V22 après copie de test | Vérification de données (D6) |

## 3. Fonctionnalités réutilisables

- Moteur de réservation avec verrou pessimiste et EXCLUDE GIST (V4, `BookingService`).
- Devis versionnés (V5, `QuoteEntity`).
- Paiement simulé avec idempotence (V6, `SimulatedPaymentProvider`).
- Factures (V7).
- Audit (`AuditService`, V2).
- Sécurité par autorités (`SecurityConfig`).
- Catalogue public filtré (`VehicleService.isPublic`).

## 4. Fonctionnalités à construire

- Partenaires (entité, service, API admin, rôle PARTNER, espace lecture seule).
- Rattachements véhicule-partenaire et historique.
- Garanties de disponibilité (cycle ACTIVE / expirée / retirée).
- Holds PENDING avec expiration et libération.
- Recherche client par dates sur garanties confirmées uniquement.
- Liaison `bookings.availability_id` (référence obligatoire, cf. doc 12).
- Pages admin partenaires et garanties ; espace partenaire.
- Tests PostgreSQL d'intégration et E2E.
