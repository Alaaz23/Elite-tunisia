# Ordre de verrouillage des réservations (T-A02)

## Ordre global retenu

```
Vehicle  →  Booking  →  (Quote, Driver, Transfer)
```

Toute transaction qui verrouille plusieurs ressources doit acquérir les verrous dans cet ordre.
Le devis (Quote) est verrouillé explicitement après la réservation lorsqu'un chemin le modifie ou le lit pour une décision (`QuoteService.lock`, `PaymentService.reserve`, `InvoiceService.createFromQuote`).
Les tables `BookingOperation`, `Payment` et `Invoice` ne reçoivent que des insertions ; elles ne portent pas de verrou pessimiste dédié.

## Point d'acquisition unique

`BookingService.locked(id)` est l'unique méthode qui acquiert le verrou Booking pour les opérations de réservation :

1. lecture simple de la réservation pour connaître le véhicule ;
2. `VehicleRepository.findLocked` (`PESSIMISTIC_WRITE`) sur le véhicule ;
3. `BookingRepository.findLocked` (`PESSIMISTIC_WRITE`) sur la réservation ;
4. `EntityManager.refresh` pour éviter une entité obsolète.

Les points d'entrée publics qui l'utilisent :

| Méthode | Ressource verrouillée | Appelant |
|---|---|---|
| `transition`, `update`, `customerCancel` | Vehicle → Booking | `BookingService` |
| `lockForCommercialOperation` (`MANDATORY`) | Vehicle → Booking | `QuoteService.lock` |
| `lockForOperation` (`MANDATORY`) | Vehicle → Booking | `BookingOperationService.recordPickup`, `recordReturn` |

## Chemins recensés

| Chemin | Acquisitions | Conforme |
|---|---|---|
| `BookingService.transition` / `update` / `customerCancel` | Vehicle → Booking | oui |
| `BookingService.create` | Vehicle (sans Booking) puis contrôle de chevauchement | oui |
| `BookingOperationService.recordPickup` | Vehicle → Booking (via `lockForOperation`) | oui, corrigé en T-A02 |
| `BookingOperationService.recordReturn` | Vehicle → Booking (via `lockForOperation`) | oui, corrigé en T-A02 |
| `QuoteService.lock` | Vehicle → Booking (via `lockForCommercialOperation`), puis Quote (`QuoteRepository.findLockedById`, `PESSIMISTIC_WRITE`, puis `refresh`) | oui, verrou devis ajouté en Lot A.1 |
| `InvoiceService.createFromQuote` | Vehicle → Booking (via `lockForOperation`), puis Quote (`findLockedById`) | oui, ajouté en Lot A.1 (idempotence de facture principale) |
| `DriverAssignmentService` | Booking → Driver (sans verrou véhicule) | oui, sans cycle possible |
| `TransferService` | Booking → Transfer → Driver (sans verrou véhicule) | oui, sans cycle possible |
| `PaymentService.createForQuote` | Vehicle → Booking → Quote | oui, corrigé en stabilisation (ancien ordre : Quote → Booking) |

## Limite connue de l'ordre global

Les chemins Driver et Transfer acquièrent Booking sans jamais acquérir Vehicle. L'ordre global reste respecté
(aucun chemin n'acquiert Booking *avant* Vehicle), mais il n'est pas uniforme : un chemin Booking-only ne prend pas le verrou véhicule.
Aucun cycle n'a été démontré par analyse statique. Aucun test PostgreSQL concurrent mélangeant ces chemins n'existe encore.
Décision : documenter, ne pas modifier l'ordre sans cycle prouvé.

## Problème corrigé

Avant T-A02, `recordPickup` et `recordReturn` acquéraient `bookings.findLocked` puis `vehicles.findLocked`.
Une transaction `transition` ou `update` concurrente acquiert Vehicle puis Booking.
Deux transactions de ce type peuvent former un cycle d'attente : PostgreSQL l'interrompt alors par une erreur `40P01` (deadlock).

Le test déterministe `BookingLockOrderPostgresIT.pickupWaitsOnVehicleBeforeAcquiringBooking` démontre l'inversion :
avec l'ancien ordre, la sonde `SELECT ... FOR UPDATE NOWAIT` sur `bookings` échoue en `55P03` pendant qu'un pickup attend le véhicule.

## Règles pour les futures modifications

- Ne jamais acquérir `Booking` avant `Vehicle` dans une transaction qui touche aussi le véhicule.
- Passer par `BookingService.lockForOperation` ou `lockForCommercialOperation` plutôt que par `BookingRepository.findLocked` directement.
- Toute nouvelle ressource verrouillée dans une réservation (Quote, Driver, Transfer) doit être acquise après Booking.
- Ne pas ajouter de retry automatique pour masquer un `40P01` : corriger l'ordre.

## Écritures d'audit dans les opérations de réservation (T-A03)

- `AuditService.recordAudit` participe à la transaction de l'appelant (`MANDATORY`). Toute erreur d'audit annule l'opération métier : les `catch` silencieux de `recordPickup` et `recordReturn` ont été supprimés.
- Tous les audits existants sont classés **obligatoires**. Aucun n'est best-effort. Les échecs de connexion et de rafraîchissement sont les seules exceptions volontaires, via `noRollbackFor` dans `AuthService`.
- `recordReturn` vérifie `IN_PROGRESS` avant toute écriture, avec le code `INVALID_STATUS_TRANSITION`. L'idempotence (retour déjà présent) reste contrôlée avant cette vérification.

## Limites

- Un test de concurrence réduit la probabilité d'un deadlock sans l'exclure. La preuve la plus forte reste le test déterministe ci-dessus.
- La contrainte `VEHICLE_NOT_AVAILABLE` dépend de l'état du véhicule au moment du verrou ; elle n'est pas une contrainte d'exclusion PostgreSQL.
- Le message d'erreur de `locked` est désormais en français (`BOOKING_NOT_FOUND`) pour pickup et retour, alors que le message précédent était en anglais.
