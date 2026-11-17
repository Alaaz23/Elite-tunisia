# Lot A — Validation finale

## Verdict

**Lot A NON VALIDÉ.**

Les corrections ISS-01, ISS-02 et ISS-03 sont implémentées et couvertes par des tests unitaires et H2 qui passent. Aucune validation PostgreSQL réelle n'a été exécutée : aucun identifiant de test n'est disponible dans l'environnement. Par règle, les tests non exécutés ne sont pas comptés comme réussis.

## 1. Catégories demandées

### Correctifs implémentés

| Ref | Correctif | Fichiers |
|---|---|---|
| ISS-03 | Idempotence scopée par `(client, devis, clé)` | `database/migrations/V27__payments_idempotency_scope.sql`, `payment/PaymentRepository.java` |
| ISS-03 | `PaymentService` en trois phases : réservation (transaction courte), appel fournisseur hors transaction, finalisation (transaction courte) | `payment/PaymentService.java` |
| ISS-03 | Violation de contrainte pendant la réservation → `IDEMPOTENCY_CONFLICT` | `payment/PaymentService.java` |
| ISS-03 | Erreur fournisseur → paiement reste `PENDING`, événement `PROVIDER_ERROR` | `payment/PaymentService.java` |
| ISS-02 | Passage à `COMPLETED` uniquement via `completeAfterReturn` (transition, opération `RETURN`, événement, audit) | `booking/BookingService.java`, `booking/BookingOperationService.java` |
| ISS-01 | Verrou `lockForOperation` (Vehicle → Booking) utilisé par pickup et return | `booking/BookingService.java`, `booking/BookingOperationService.java` |

### Correctifs testés (exécutés, verts)

| Lot de tests | Commande | Résultat |
|---|---|---|
| Compilation | `backend\mvnw.cmd -B -ntp test-compile` | BUILD SUCCESS (`backend/lotA-compile.log`) |
| Ciblés ISS-02/ISS-03 | `mvnw test -Dtest=BookingOperationServiceTest,PaymentServiceUnitTest` | 17 exécutés, 0 échec, 0 erreur (`backend/lotA-final-targeted.log`) |
| Suite H2 complète | `mvnw clean test` | **266 exécutés, 0 échec, 0 erreur, 9 ignorés**, BUILD SUCCESS (`backend/lotA-final-full.log`) |

Les 8 nouveaux tests : 3 pour le retour (ISS-02), 5 pour les paiements (ISS-03).

Détail des cas ISS-02 (`BookingOperationServiceTest`) :
- retour sans PICKUP refusé (`returnWithoutPickupFails`, existant) ;
- retour sur statut invalide refusé sans écriture (`returnRejectsBookingNotInProgressBeforeAnyWrite`) ;
- retour valide délègue à `completeAfterReturn(..., "BOOKING_RETURN")` (`validReturnDelegatesCompletionToBookingService`) ;
- erreur de finalisation/audit propagée (`completionFailureIsPropagatedNotSwallowed`).

Détail des cas ISS-03 (`PaymentServiceUnitTest`) :
- erreur fournisseur → `PENDING`, un seul appel fournisseur (`providerErrorLeavesPaymentPendingWithoutFailing`) ;
- même clé → paiement existant, zéro appel fournisseur (`sameIdempotencyKeyReturnsExistingPaymentWithoutProviderCall`) ;
- violation de contrainte → `IDEMPOTENCY_CONFLICT`, zéro appel fournisseur (`constraintViolationOnReservationBecomesIdempotencyConflictWithoutProviderCall`) ;
- paiement `PENDING` existant → `PAYMENT_IN_PROGRESS`, pas de double paiement (`pendingPaymentBlocksSecondPaymentForSameQuote`) ;
- erreur de finalisation propagée (`finalisationErrorIsPropagatedNotSwallowed`).

### Correctifs validés sur PostgreSQL

**Aucun.** Tous les tests PostgreSQL sont ignorés (9 ignorés dans la suite H2, dont les classes `*PostgresIT`, `*PostgresTest` et `PaymentPostgresIntegrationTests`). Ils ne comptent pas comme réussis.

### Tests non exécutés

| Test | Raison |
|---|---|
| Concurrence PostgreSQL ISS-01 (`BookingLockOrderPostgresIT`, `BookingAuditAtomicityPostgresIT`) | Pas d'identifiants PostgreSQL. |
| Idempotence PostgreSQL (`PostgresIdempotencyIT`, `PaymentPostgresIntegrationTests.sameIdempotencyKeyIsScopedByCustomerAndQuote`) | Idem. |
| Application de V27 sur base vierge et vérification de l'index | Idem. |
| Rollback transactionnel réel (audit en échec → état inchangé) | Pas de test PostgreSQL écrit pour ce cas. |
| Réconciliation des paiements `PENDING` | Non implémentée (voir section 3). |

Aucune base n'a été créée sur `127.0.0.1:15432`. Le port répond, mais sans identifiants aucune connexion n'a été tentée. `pgpass.conf` n'a pas été lu (politique de contenu de l'organisation).

## 2. Vérification de V27

- **Syntaxe** : `DROP INDEX IF EXISTS payments_idempotency_idx;` puis `CREATE UNIQUE INDEX uq_payments_customer_quote_idempotency ON payments (customer_id, quote_id, idempotency_key);`. Syntaxe PostgreSQL valide. Non exécutée.
- **Portée** : l'unicité globale de la clé (V6) est remplacée par une unicité par `(client, devis, clé)`. C'est la décision métier validée.
- **Compatibilité PostgreSQL 18** : `DROP INDEX IF EXISTS` et `CREATE UNIQUE INDEX` sont standards. Non vérifiée sur une instance.
- **Compatibilité JPA** : `PaymentRepository.findByCustomerIdAndQuote_IdAndIdempotencyKey` s'appuie sur ces colonnes. Non vérifiée au démarrage Hibernate avec PostgreSQL.
- **Migration depuis le schéma précédent** : V27 ne modifie aucune donnée et ne supprime aucune migration. Elle échouerait si des données existantes contenaient déjà un doublon `(client, devis, clé)`, ce qui est impossible sous l'ancienne contrainte globale. Non vérifiée sur une base réelle.
- **Index** : l'index composite n'est couvert ni par un test ni par un plan d'exécution.

## 3. Politique de reprise des paiements PENDING

| Exigence | État |
|---|---|
| Même clé ne crée jamais un second paiement | Implémenté (`reserve`, branche `existing`). Testé unitairement. |
| PENDING → retour du paiement existant sans rappel fournisseur | Implémenté. Testé unitairement. |
| PENDING jamais transformé en FAILED sur erreur réseau | Implémenté : `PROVIDER_ERROR` laisse `PENDING`. Testé unitairement. |
| Nouvelle opération seulement si la précédente n'est pas encaissée | Implémenté : `QUOTE_ALREADY_PAID` et `PAYMENT_IN_PROGRESS`. Testé unitairement. |
| Réconciliation (consulter le statut réel auprès du fournisseur) | **Non implémentée.** Le fournisseur est simulé et aucune API de réconciliation externe n'existe. |

Interface à prévoir (documentation seulement, pas de fournisseur inventé) : une requête de statut indexée par l'identifiant du paiement local, retournant `CAPTURED`, `FAILED`, `PENDING` ou `UNKNOWN`. Un job de reprise ne doit pas réappeler le fournisseur pour un paiement `PENDING` sans cette réponse. Ce point nécessite une décision métier et un contrat fournisseur.

## 4. Revue des exceptions (ISS-02 / ISS-03)

| Fichier | Constat | Décision |
|---|---|---|
| `invoice/InvoiceService.java` (~ligne 40) | `catch (Exception)` autour de `nextval('invoice_seq')`, repli sur `System.currentTimeMillis() % 1000000`. Sur PostgreSQL, une erreur SQL abort la transaction : le repli échoue aussi et peut produire des références en double. | **Non corrigé.** Risque confirmé par lecture ; à corriger après validation PostgreSQL, car les tests H2 dépendent du repli. |
| `invoice/InvoiceService.java` (~ligne 101) | `catch (Exception)` autour de `on conflict do nothing`, puis select/insert de repli. Même problème d'abort de transaction sur PostgreSQL. | **Non corrigé.** Même décision. |
| `invoice/InvoiceDocumentServiceImpl.java` (lignes 41, 43) | Relance les `RuntimeException`, encapsule les autres. | Justifié. |
| `booking/BookingHoldExpirationWorker.java` | Supprimé : la règle métier retire toute expiration et tout hold des PENDING. | Justifié. |
| `quote/QuoteExpirationWorker.java` (ligne 14) | Isolation par élément avec log. | Justifié. |
| `payment/PaymentService.java` (lignes ~42, ~53) | Repli `PROVIDER_ERROR` (paiement reste PENDING) et `IDEMPOTENCY_CONFLICT`. | Justifié par la conception trois phases. |
| `booking/BookingOperationService.java` | Aucun bloc `catch`. | Rien à corriger. |

Résidu ISS-03 : si la finalisation échoue après un `CAPTURED` fournisseur, la transaction de finalisation est annulée et le paiement reste `PENDING` alors que le fournisseur a encaissé. C'est exactement le cas que la réconciliation (section 3) doit couvrir. Non résolu sans réconciliation.

## 5. Vérification des verrous (ISS-01)

Ordre observé dans le code (lecture seule) :

| Chemin | Ordre | Statut |
|---|---|---|
| `BookingService.locked()` | Vehicle → Booking | Conforme |
| `BookingService.lockForOperation` (pickup/return) | Vehicle → Booking, puis `vehicles.findLocked` à nouveau dans `BookingOperationService` | Conforme. Le second verrou est redondant (même ligne, pas d'interblocage) ; à nettoyer plus tard. |
| `PaymentService.reserve` | Vehicle → Booking → Quote | Conforme |
| `PaymentService.finish` | Booking | Conforme |
| `QuoteService.lock` | Vehicle → Booking. Le devis n'est lu qu'en lecture simple, sans `findLockedById`. | Conforme à l'ordre ; le verrou devis n'est pas posé. À vérifier sous PostgreSQL. |
| `DriverAssignmentService`, `TransferService` | Booking verrouillé en premier, puis driver/transfer. Pas de verrou véhicule. | Cohérent avec Booking avant ressources liées, à prouver sous PostgreSQL. |
| `GuaranteeService`, `PartnerService` | Véhicule seul | Pas de conflit identifié. |

**ISS-01 n'est pas considéré corrigé** : aucune exécution PostgreSQL réelle des chemins de concurrence n'a été faite. Les tests `BookingLockOrderPostgresIT` et `BookingAuditAtomicityPostgresIT` existent mais sont ignorés.

## 6. Git et préservation du travail

- `git status --porcelain` : 177 entrées au moment du contrôle (contre 169 au départ). La liste des 169 n'avait pas été sauvegardée : l'écart ne peut pas être attribué fichier par fichier avec certitude.
- Les entrées Lot A nouvelles sont : `database/migrations/V27__payments_idempotency_scope.sql`, `docs/lot-a-technical-report.md`, `docs/lot-a-final-validation.md`, `technical-audit-final.md`, et les journaux `backend/lot8-*.log`, `backend/lotA-*.log`.
- Aucun commit, aucune réinitialisation, aucune suppression de fichier existant.
- Les fichiers préexistants non commités (dont `BookingHoldExpirationWorker.java`, `docs/architecture/`, `docs/technical-audit.md`) n'ont pas été modifiés.

## 7. Risques restants

1. **Aucune validation PostgreSQL** : concurrence ISS-01, V27 et idempotence scopée ne sont pas prouvées sur une base réelle. Risque principal.
2. **Paiement capturé par le fournisseur mais resté PENDING** après une erreur de finalisation. Nécessite la réconciliation (non implémentée).
3. **Blocs `catch` d'`InvoiceService`** : repli inopérant sur PostgreSQL, risque de références de facture en double. Non corrigé.
4. **Relâchement de l'unicité** (V27) : décision métier validée, mais à confirmer sur une base de test.
5. **Verrou devis non posé dans `QuoteService.lock`** : ordre respecté, mais l'absence de verrou sur le devis peut autoriser une course. À prouver.
6. **Verrou véhicule redondant** dans `BookingOperationService` (pas de risque d'interblocage, coût inutile).
7. **Périmètre git non entièrement attribué** (169 → 177).

## 8. Actions pour lever le blocage

Pour valider le Lot A, il faut fournir un identifiant PostgreSQL pour une base de test temporaire sur `127.0.0.1:15432`, puis :
1. créer une base vierge dédiée (sans toucher aux bases existantes) ;
2. exécuter V1 à V27 ;
3. lancer `BookingLockOrderPostgresIT`, `BookingAuditAtomicityPostgresIT`, `PostgresIdempotencyIT`, `PaymentPostgresIntegrationTests` ;
4. corriger les anomalies remontées et relancer la suite H2.

Lot B non commencé.
