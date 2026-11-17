# 05 — Contrats API proposés

Tous les endpoints sont proposés, aucun n'est implémenté par cette mission. Les permissions listées sont à créer.

## 1. Existant à faire évoluer

| Méthode | Chemin | Accès actuel | Évolution |
|---|---|---|---|
| GET | `/api/vehicles` | Public | Ne doit plus exposer les véhicules sans partenaire rattaché ni sans garantie. À décider (voir D-PA-08). |
| GET | `/api/vehicles/available?startDateTime&endDateTime` | Public | Doit exiger la **couverture** par garantie, pas seulement l'absence de conflit. |
| GET | `/api/vehicles/{vehicleId}/availability` | Public (`BookingPublicController`) | Idem : retourner `reason = VEHICLE_NOT_GUARANTEED` si aucune garantie couvre la période. |
| POST | `/api/bookings/quote-preview` | Public | Refuser si non couvert. |

Les codes `reason` existants (`VEHICLE_NOT_AVAILABLE`, `VEHICLE_ALREADY_BOOKED`) restent inchangés. Un nouveau code est ajouté, sans renommer les anciens.

## 2. Administration des partenaires

| Méthode | Chemin | Permission | Corps / réponse | Codes |
|---|---|---|---|---|
| GET | `/api/admin/partners?page&size&active` | `partners:read` | Liste paginée `PartnerSummary` | 200, 403 |
| POST | `/api/admin/partners` | `partners:manage` | `PartnerUpsertRequest` → `PartnerSummary` | 201, 400, 403, 409 |
| GET | `/api/admin/partners/{id}` | `partners:read` | `PartnerSummary` | 200, 404 |
| PUT | `/api/admin/partners/{id}` | `partners:manage` | `PartnerUpsertRequest` | 200, 400, 404, 409 |
| PATCH | `/api/admin/partners/{id}/status` | `partners:manage` | `{ "active": boolean }` | 200, 404, 409 |

`PartnerUpsertRequest` : `name` (obligatoire), `partnerType`, `email`, `phone`, `legalName`, `taxId`, `address`. Pas de champ de commission (voir D1).

## 3. Administration des véhicules et rattachement

| Méthode | Chemin | Permission | Remarque |
|---|---|---|---|
| POST | `/api/admin/vehicles` | `vehicles:manage` | Doit recevoir `partnerId` obligatoire. |
| PUT | `/api/admin/vehicles/{id}` | `vehicles:manage` | Changement de `partnerId` : interdit directement ; passer par le transfert contrôlé (D-PA-12 validé). Ne modifie pas le partenaire porteur des garanties existantes (D-PA-05). |
| POST | `/api/admin/vehicles/{id}/partner-transfers` | `vehicles:manage` (admin Elite) | Transfert contrôlé : body `{ newPartnerId, effectiveAt, reason, operationRef? }`. 409 avec motif métier si engagement actif (D-PA-12). Voir review/08 §3.6 |

## 4. Disponibilités garanties

| Méthode | Chemin | Permission | Corps / réponse | Codes |
|---|---|---|---|---|
| GET | `/api/admin/availabilities?vehicleId&partnerId&from&to&status` | `availability:read` | Liste paginée | 200 |
| POST | `/api/admin/vehicles/{vehicleId}/availabilities` | `availability:manage` | `GuaranteedAvailabilityCreateRequest` (`startDateTime`, `endDateTime`, `confirmedAt`, `note`) → 201 | 400, 404, 409 (`AVAILABILITY_OVERLAP`), 422 (`PARTNER_INACTIVE`, `VEHICLE_NOT_PUBLISHABLE`) |
| PUT | `/api/admin/availabilities/{id}` | `availability:manage` | Modification de période ; refusée si réservation bloquante | 200, 404, 409 (`AVAILABILITY_HAS_BOOKING`) |
| POST | `/api/admin/availabilities/{id}/withdraw` | `availability:manage` | `{ "reason": string }` ; refusée si engagée | 200, 404, 409 (`AVAILABILITY_HAS_BOOKING`) |
| GET | `/api/admin/availabilities/{id}/events` | `availability:read` | Historique | 200, 404 |

Règles :
- `partnerId` n'est pas accepté en entrée : il est déduit du partenaire **actuellement** rattaché au véhicule, vérifié (actif), puis figé comme partenaire porteur.
- La réponse expose `carryingPartnerId` en lecture seule. Ce champ n'est jamais modifiable par `PUT` ni par `withdraw`.
- Un changement de `partnerId` sur le véhicule n'altère aucune garantie existante (D-PA-05).
- Un transfert est refusé (409) si une garantie active, une réservation CONFIRMED future ou IN_PROGRESS, ou un autre engagement actif existe (D-PA-12).
- Réservation : pour une nouvelle réservation garantie, `availabilityId` est obligatoire ; le backend le vérifie et ne le déduit jamais du client sans contrôle de couverture (review/08 §5).
- `confirmedAt` est obligatoire et ne peut pas être dans le futur.
- La mise à jour utilise `If-Match` ou un champ `version` (verrou optimiste).

## 5. Partenaire (phase ultérieure, non planifiée)

Préfixe `/api/partner/**`, rôle `PARTNER`. Lecture seule de ses véhicules, de ses disponibilités et des réservations liées. Chaque requête filtre sur l'identifiant partenaire **déduit de l'utilisateur authentifié**, jamais sur un paramètre client. Aucune écriture sur les commissions ni sur les garanties.

## 6. Format d'erreur

Erreurs métier via `ProblemDetail` (RFC 9457) avec `code` (identifiant stable) et `message` en français, déjà utilisé par `ApiExceptionHandler`. Pas de stack trace ni de SQL dans la réponse.

## 7. Compatibilité

- Les réponses publiques existantes gardent leurs champs.
- Ajouts uniquement, aucun renommage.
