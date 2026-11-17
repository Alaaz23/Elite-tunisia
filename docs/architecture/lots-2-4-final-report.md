# Rapport final — LOTS 2 à 5 (réservations, devis/paiements, factures, frontend)

Date de la mission : 2026-11-03. Branche `main`, HEAD `78da39b`.

## 1. Fichiers analysés

- Réservations : `booking/BookingService.java`, `booking/BookingOperationService.java`, `booking/BookingEntity.java`, `booking/BookingStatus.java`, `booking/BookingRepository.java`, tests `booking/*Test*.java`.
- Paiements et devis : `payment/PaymentService.java`, `payment/PaymentRepository.java`, `payment/PaymentEntity.java`, `quote/QuoteRepository.java`, `quote/QuoteService.java` (lecture seule), tests `payment/*Test*.java`.
- Factures : `invoice/InvoiceService.java`, `invoice/CustomerInvoiceController.java`, `invoice/InvoiceRepository.java`.
- Frontend : `frontend/src/app/features/quotes/quote-detail.component.ts` (lecture seule).

## 2. Fichiers modifiés pendant cette mission

Aucun. Aucun défaut n'a été démontré dans les LOTS 2 à 4, donc aucune correction n'a été appliquée.

## 3. Défauts confirmés

Aucun défaut confirmé par reproduction. Les points examinés sont conformes :

- **Disponibilité** : `rentable` (statut `AVAILABLE`, marque et catégorie actives) et `overlaps` sur `CONFIRMED`/`IN_PROGRESS` sont contrôlés à la création, à la préview, à la confirmation et sous verrou. Un véhicule `RENTED` est refusé. Retirer ce contrôle serait une régression.
- **Verrouillage** : `BookingService.locked` verrouille le véhicule puis la réservation (`PESSIMISTIC_WRITE`), conformément à l'ordre Vehicle → Booking.
- **Pickup / retour** : idempotence, contrôle d'odomètre, transitions via `BookingEntity.transition`, audit obligatoire dans la transaction.
- **Paiements** : verrou pessimiste du devis conservé, invariant d'un seul `CAPTURED`, idempotence avec `IDEMPOTENCY_CONFLICT`, erreur fournisseur en `PROVIDER_ERROR` avec statut `PENDING`.
- **Factures** : `getForCustomer` vérifie la propriété et répond 404 dans les deux cas (inconnue ou autre client).

## 4. Tests exécutés

Commande exacte :

```
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
.\mvnw.cmd -B -ntp -DskipITs test-compile test
```

- Java : JDK 21.0.2 (Temurin).
- `$LASTEXITCODE` Maven : 0. Code de sortie PowerShell : 0.
- Résultat Surefire : **172 exécutés, 0 échec, 0 erreur, 10 ignorés**, `BUILD SUCCESS`.
- Les tests ignorés sont les `*PostgresIT` et `*PostgresIntegrationTests` (conditionnés par `ELITE_POSTGRES_TEST`). Ils ne sont pas validés.

## 5. Non validé

- **PostgreSQL** : aucun test PostgreSQL exécuté (verrous réels, contrainte `bookings_no_blocking_overlap`, idempotence concurrente). Interdit par la mission.
- **Angular (LOT 5)** : aucun build ni test exécuté. Node 18.20.8 est insuffisant. Revue de code uniquement ; aucun défaut démontré.
- **Concurrence réelle** : non validée sans PostgreSQL.

## 6. Décisions métier et autorisations bloquantes

- D1, D2, D3, D5, D6, D10, D11, D12, P-02/D-02 : ouverts.
- Retard de restitution et sémantique de `RESERVED` : ouverts. `VehicleStatus.AVAILABLE` et `BLOCKING` sont conservés.
- Expiration des devis : ouverte. `QuoteService.get` écrit pendant une lecture : **non modifié** (autorisation #22 manquante).
- B9 : pagination des factures non introduite.
- V21 et V22 : non appliquées.

## 7. État Git

- HEAD : `78da39b`.
- Modifications préexistantes (10 fichiers suivis) et fichiers non suivis : **inchangés**.
- Aucun commit, push, merge, reset, checkout de restauration, ni suppression.
- Aucun processus Java arrêté. Aucun accès à PostgreSQL 5432 ou 15432.
