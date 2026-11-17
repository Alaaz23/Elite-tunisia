# Rapport — Protection du cycle de vie des véhicules

**Date :** session courante · **Branche :** `main` · **HEAD :** `78da39b` (aucun commit effectué)

## 1. Problèmes identifiés (avant modification)

| # | Constat | Preuve dans le code |
|---|---------|---------------------|
| P1 | Le passage d'un véhicule à `UNAVAILABLE` ou `MAINTENANCE` ne vérifiait aucune réservation, même `CONFIRMED` ou `IN_PROGRESS`. | `VehicleService.updateStatus` : `find(id)` puis `setStatus`, sans contrôle |
| P2 | Le même contournement existait via `PUT /api/admin/vehicles/{id}` (champ `status` optionnel). | `VehicleService.update` : `if (request.status() != null) vehicle.setStatus(...)` |
| P3 | La suppression d'un véhicule ne vérifiait pas les réservations. Elle reposait uniquement sur la contrainte de clé étrangère en base, sans message métier. | `VehicleService.delete` : `vehicles.delete(vehicle)` ; V1 déclare `bookings.vehicle_id REFERENCES vehicles(id)` sans `ON DELETE` |
| P4 | Les opérations de statut ne prenaient aucun verrou véhicule, contrairement à `BookingService` (ordre Vehicle → Booking). | `vehicles.findLocked` n'était utilisé que dans `BookingService` et `BookingOperationService` |

## 2. Protections déjà existantes (conservées)

- Création de réservation : `BookingService` verrouille le véhicule (`findLocked`), puis revérifie `requireAvailable` (statut `AVAILABLE`, marque et catégorie actives, pas de chevauchement `CONFIRMED`/`IN_PROGRESS`).
- Contrainte d'exclusion PostgreSQL `bookings_no_blocking_overlap` (V4) : protection finale contre les chevauchements, même en concurrence.
- Transitions de réservation : `BookingStatus.canTransitionTo` et contrôles de `BookingService.transition`.
- Contrôles d'accès : `AdminVehicleController` exige `vehicles:manage` pour `PATCH /{id}/status`, `PUT /{id}` et `DELETE /{id}`.
- Vérification de l'ordre de verrouillage : `BookingService.locked` prend d'abord le véhicule, puis la réservation.

## 3. Modifications réalisées

### Backend

- [`VehicleService.java`](../../backend/src/main/java/tn/elitetunisia/vehicle/VehicleService.java)
  - Injection de `BookingRepository` (constructeur).
  - `update`, `updateStatus` et `delete` prennent désormais le verrou véhicule (`findLocked`) avant toute vérification.
  - Nouvelle garde `requireStatusChangeAllowed` :
    - passage à `UNAVAILABLE` refusé s'il existe une réservation `IN_PROGRESS`, ou une réservation `CONFIRMED` dont la fin est postérieure à maintenant ;
    - passage à `MAINTENANCE` refusé seulement s'il existe une réservation `IN_PROGRESS` ;
    - les autres transitions (dont retour à `AVAILABLE`, `RENTED`, `RESERVED`) et le maintien du statut courant ne sont pas bloqués.
  - `delete` refusé (`VEHICLE_HAS_BOOKINGS`) dès qu'il existe une réservation de n'importe quel statut, afin de préserver l'historique.
- [`BookingRepository.java`](../../backend/src/main/java/tn/elitetunisia/booking/BookingRepository.java) : deux requêtes de comptage, `countByVehicle` et `countByVehicleEndingAfter`. Lecture seule, aucune modification de schéma.
- [`ApiExceptionHandler.java`](../../backend/src/main/java/tn/elitetunisia/common/exception/ApiExceptionHandler.java) : messages métier pour `VEHICLE_HAS_ACTIVE_BOOKING` et `VEHICLE_HAS_BOOKINGS`. Les réponses sont en HTTP 409 avec un champ `code`, via `DomainException.conflict`.

### Tests

- Nouveau fichier [`VehicleLifecycleProtectionTest.java`](../../backend/src/test/java/tn/elitetunisia/vehicle/VehicleLifecycleProtectionTest.java) : 10 tests unitaires Mockito.

| Cas de la mission | Test | Résultat |
|---|---|---|
| 1. Retrait sans réservation | `removalWithoutAnyBookingIsAllowed` | OK |
| 2. Retrait avec CONFIRMED à venir | `removalWithConfirmedBookingAheadIsRefused` | OK |
| 3. Retrait avec IN_PROGRESS | `removalWithInProgressBookingIsRefused` | OK |
| 4. Modification de statut autorisée | `statusChangeToAvailableIsAllowedEvenWithActiveBooking`, `maintenanceWithConfirmedBookingAheadIsStillAllowed` | OK |
| 5. Modification de statut interdite | `maintenanceWithInProgressBookingIsRefused` | OK |
| 6. Véhicule d'un autre partenaire | Non applicable (voir §6) | — |
| 7. Historique préservé | `deleteWithAnyBookingIsRefusedToPreserveHistory`, `deleteWithoutBookingIsAllowed` | OK |
| Complément | `unchangedStatusIsNotBlocked`, `unknownVehicleIsReportedAsNotFound` | OK |

Aucune migration, aucun changement de schéma, aucune modification de CI/CD, de paiement, de workflow de réservation ni de commission.

## 4. Tests exécutés et résultats exacts

Environnement : JDK 21 (`jdk-21.0.2.13-hotspot`) passé explicitement, Maven Wrapper du dépôt. Le code de sortie est lu immédiatement après chaque commande.

1. Ciblé : `-Dtest=VehicleLifecycleProtectionTest,BookingServiceUnitLogicTest -DskipITs test`
   - Une première tentative a échoué à la compilation (appel `delete` ambigu dans mon test). Corrigé.
   - Une deuxième tentative a montré 4 erreurs `NullPointerException` dues au harnais de test (mocks de marque et catégorie absents, nécessaires au résumé renvoyé). Corrigé dans le test, sans toucher au code de production.
   - Résultat final : `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, code de sortie `0`.
2. Suite complète H2 : `test-compile test -DskipITs`
   - Résultat : `Tests run: 182, Failures: 0, Errors: 0, Skipped: 10`, `BUILD SUCCESS`, code de sortie `0`.
   - Les 10 tests ignorés sont les `*PostgresIT`, désactivés sans `ELITE_POSTGRES_TEST=true`. Ils n'ont **pas** été exécutés.
   - Les 182 tests comprennent les 172 de la mission précédente et les 10 nouveaux.

**Non exécuté :** tests PostgreSQL (hors périmètre), tests frontend Angular (Node 18.20.8 insuffisant, non installé).

## 5. Risques restants

- **Preuve PostgreSQL non réalisée.** Le comportement exact de la clé étrangère `bookings.vehicle_id` sur un `DELETE` n'est pas testé sur PostgreSQL. La garde applicative ajoute désormais une réponse métier avant ce cas.
- **Concurrence non testée.** La protection repose sur le verrou pessimiste `findLocked` pris avant la vérification. Ce point est démontré par conception et par lecture du code, pas par un test concurrent.
- **Test d'intégration HTTP absent.** Les contrôles sont testés en unitaire. Le code HTTP 409 et le champ `code` ne sont pas vérifiés par MockMvc. Ajouter un test MockMvc est la prochaine étape si besoin.
- **Statut `RENTED` et `RESERVED`.** Non gardés. `RESERVED` n'est jamais posé par le backend (sémantique gelée par le propriétaire).
- **`UNAVAILABLE` au sens métier.** Sa sémantique exacte (retrait temporaire ou définitif) n'est pas documentée dans le code.
- **Transition réservation PENDING → CONFIRMED après passage en `UNAVAILABLE`.** `BookingService` revérifie `requireAvailable`, donc la confirmation échoue. Ce comportement est déduit du code, non testé.

## 6. Isolation partenaire : limitation documentée

Le modèle partenaire n'existe pas. `VehicleEntity` ne contient aucune référence à un propriétaire, le package `partner` est vide, et les endpoints véhicules sont réservés aux administrateurs ayant `vehicles:manage`. Il n'est donc pas possible aujourd'hui de vérifier qu'un partenaire ne modifie que ses propres véhicules. Aucune architecture n'a été inventée. Voir [partner-vehicle-availability-analysis.md](./partner-vehicle-availability-analysis.md).

## 7. Décisions métier nécessaires

1. **Maintenance avec réservation confirmée à venir.** Actuellement autorisée. Faut-il la bloquer, ou la laisser et gérer le conflit manuellement ?
2. **Suppression d'un véhicule ayant un historique.** Actuellement refusée dès qu'une réservation existe, quel que soit son statut. Confirmer, ou n'autoriser que les véhicules sans réservation passée ?
3. **Sémantique de `UNAVAILABLE`.** Retrait temporaire ou définitif ? Influence la règle de réactivation.
4. **Engagement de disponibilité garantie et conséquences financières.** Toujours ouverts (D3, D7).

## 8. Prochaine étape recommandée

Lancer le lot de **disponibilités garanties** (modèle partenaire, disponibilité par véhicule et par période, validation Elite), après validation explicite des décisions du §7. Ce lot exigera une migration, donc une autorisation écrite préalable.

Validation préalable recommandée avant tout déploiement : exécuter les tests PostgreSQL sur une base isolée pour confirmer le comportement des clés étrangères lors d'une suppression.
