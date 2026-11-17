# Phase C – Rapport d'exécution (Elite Tunisia)

Verdict global : **NON PRÊT POUR LA PRODUCTION**. Les tests PostgreSQL réels sont BLOQUÉS (cluster 15432 injoignable) et ne sont pas substitués par H2.

## C1/C2 – Migrations et provenance

- Chaîne packagée : `database/migrations` V1 à V32, copiée dans `db/migration` au build par `backend/pom.xml` (~lignes 140–155).
- `backend/src/main/resources/db/migration` contient V4, V5, V6 renommées `_DISABLED_*` (commit 45dec0c), et un doublon V11 ; ce dossier est exclu du packaging.
- V21 à V32 et `database/proposed/` (V21, V22) sont **non suivies par Git** (`??`). Elles n'ont pas été modifiées, déplacées, supprimées ni ajoutées à Git.
- Responsabilités notables :
  - V6 : table `payments` avec un index d'idempotence global.
  - V27 : supprime cet index global et crée `uq_payments_customer_quote_idempotency (customer_id, quote_id, idempotency_key)`.
  - V21 : `payments_one_captured_per_quote` (unicité d'un paiement CAPTURED par devis). **Décision métier en attente** ; ne pas modifier.
  - V22 : `payments_quote_restrict` (brouillon dans `database/proposed/`), statut non confirmé.
- Risques de déploiement : V21/V22 hors Git et non validées ; l'état réel d'application de V27 sur la base cible n'est pas vérifié ; V4/V5/V6 désactivées dans le dossier source.
- Contraintes d'intégrité attendues : unicité par (client, devis, clé d'idempotence) ; un seul paiement CAPTURED par devis (si V21 validée) ; FK sur les devis et réservations.
- Stratégie recommandée : valider métier V21/V22, vérifier V27 sur une base PostgreSQL de test vierge (bloqué actuellement), puis versionner les migrations dans Git.

## C3 – Idempotence du pickup (ISS-02)

- Anomalie confirmée : un second `recordPickup` sur une réservation déjà `IN_PROGRESS` levait `INVALID_OPERATION` (contrôle CONFIRMED) avant la recherche d'opération PICKUP existante.
- Correction : `backend/src/main/java/tn/elitetunisia/booking/BookingOperationService.java`, recherche de l'opération PICKUP existante déplacée avant le contrôle de statut et la validation odomètre/carburant.
- Test de non-régression : `secondPickupOnInProgressBookingReturnsExistingOperationWithoutDuplicate` dans `backend/src/test/java/tn/elitetunisia/booking/BookingOperationServiceTest.java`. Échec avant correction, succès après.
- Ordre des verrous (véhicule puis réservation) et `completeAfterReturn` non modifiés.

## DataIntegrityViolationException – mapping

- `PaymentService.createForQuote` : le catch ne renvoie plus IDEMPOTENCY_CONFLICT inconditionnellement. S'il n'existe pas de gagnant pour la même clé, l'exception d'origine est relancée et classée par `ApiExceptionHandler.handleConflict` (~188–210).
- Sources possibles : clé d'idempotence (même client/devis/clé), autre UNIQUE (V21), FK (V22 si appliquée), NOT NULL, autres.
- Tests mis à jour : `PaymentServiceUnitTest`, `PaymentServiceIdempotenceCompatibleTest`.
- Non confirmé : la distinction exacte par SQLSTATE 23505 / 23503 / 23502 sur PostgreSQL réel (non testable actuellement).

## C4 – Tris, filtres, pagination

- Devis (`QuoteService` ~118–135) : correct (page bornée, size 1..100, tri fixe).
- Recherche véhicules : tri whitelisté.
- Anomalie confirmée et corrigée : endpoints partenaires (`AdminPartnerController`) et garanties (`AdminGuaranteeController`) acceptaient un `Pageable` brut. Propriété de tri inconnue = erreur serveur ; taille non bornée.
  - Correction : helper partagé `backend/src/main/java/tn/elitetunisia/common/web/AdminPageRequest.java` (taille 1..100, défaut 20 = défaut Spring, tri whitelisté, tie-breaker id ASC, propriété inconnue → 400). Déplacé depuis `partner/`.
  - Test : `backend/src/test/java/tn/elitetunisia/common/web/AdminPageRequestTest.java` (3/3).
- Corrigé : listes admin factures (`AdminInvoiceController`, `InvoiceService.adminList(Pageable)`) et demandes de facturation (`AdminBillingRequestController`, `BillingRequestService.listByStatus(status, pageable)`) renvoient désormais `PagedResponse` (content/page/size/totalElements/totalPages/sort). Tri par défaut factures `issuedAt` DESC ; demandes `createdAt` ASC (conserve l'ordre historique le plus ancien d'abord).
  - Frontend adapté : `invoice-api.service.ts` (`listForAdmin(page,size)`), `admin-invoices.component.ts` (boutons Précédent/Suivant).
  - Test : `InvoiceAccessControlTest.adminInvoiceListIsPagedWithBoundedSizeAndWhitelistedSort` (taille 100000 bornée à 100, `sort=passwordHash` → 400). Pas de test dédié à la liste des demandes de facturation (couverture indirecte uniquement).
- Non audité en profondeur : dates et fuseaux horaires des filtres de devis.

## C5 – PostgreSQL réel

- Statut : **BLOQUÉ**. Le cluster de test 15432 reste inaccessible (bind Windows, non résolu, nécessite des droits administrateur). Le port 5432 n'a pas été utilisé.
- Tests PostgreSQL ignorés (16, gated par `ELITE_POSTGRES_TEST=true`) :
  - `BookingHoldPostgresTest` : 7
  - `PaymentPostgresIntegrationTests` : 5
  - `InvoicePostgresIntegrationTests` : 2
  - `BookingEngineTests` : 1
  - `QuoteEngineTests` : 1
- Restant en attente, non validés : migrations V1–V32 sur base vierge ; concurrence réelle (verrous véhicule puis réservation) ; idempotence des paiements sous concurrence ; transactions et rollback réels ; sauvegarde et restauration.
- H2 n'a servi à aucune validation PostgreSQL.

## C6 – Non-régression

- Backend (JDK 21, `.\mvnw.cmd -B test` depuis `backend`, rapports Surefire nettoyés avant) : `BUILD SUCCESS`, exit 0, **Tests run: 336, Failures: 0, Errors: 0, Skipped: 16**. Les 16 ignorés sont les tests PostgreSQL bloqués ci-dessus.
- Ciblés avant la dernière suite : BookingOperationServiceTest 10/10, CustomerPaymentControllerMockMvcTest 4/4, PaymentQuoteInvariantUnitTest 6/6, PaymentServiceIdempotenceCompatibleTest 2/2, PaymentServiceUnitTest 10/10, AdminPageRequestTest 3/3, PartnerSecurityMockMvcTest 18/18, GuaranteeServiceTest 17/17, PartnerServiceTest 13/13.
- Frontend (Angular, Node 22.22.3 isolé via `npx -y -p node@22.22.3`, `ng test --watch=false`, relancé après pagination admin) : 17 fichiers, **106 tests passés, 0 échec, 0 ignoré**, exit 0.
- PostgreSQL 15432 (C5, revérifié) : `Test-NetConnection 127.0.0.1 -Port 15432` = échec de connexion. Service `postgresql-x64-18` = Running (le cluster 15432 n'est pas ce service). Aucune action destructive ; 5432 non utilisé. Tests PostgreSQL toujours BLOQUÉS. Le système Node 18 est refusé par Angular CLI ; rien n'a été installé globalement. Un premier essai a échoué sur un timeout de worker Vitest transitoire ; le second a réussi.

## Fichiers modifiés (cette phase)

- `backend/src/main/java/tn/elitetunisia/booking/BookingOperationService.java`
- `backend/src/test/java/tn/elitetunisia/booking/BookingOperationServiceTest.java`
- `backend/src/main/java/tn/elitetunisia/payment/PaymentService.java`
- `backend/src/test/java/tn/elitetunisia/payment/PaymentServiceUnitTest.java`
- `backend/src/test/java/tn/elitetunisia/payment/PaymentServiceIdempotenceCompatibleTest.java`
- `backend/src/main/java/tn/elitetunisia/partner/AdminPageRequest.java` (nouveau)
- `backend/src/test/java/tn/elitetunisia/partner/AdminPageRequestTest.java` (nouveau)
- `backend/src/main/java/tn/elitetunisia/partner/AdminPartnerController.java`
- `backend/src/main/java/tn/elitetunisia/partner/AdminGuaranteeController.java`
- Antérieurs : `backend/src/main/java/tn/elitetunisia/auth/controller/AuthController.java` (cookie refresh Max-Age) et son test.

Les autres fichiers modifiés dans `git status` sont des travaux préexistants non commités, préservés tels quels.

## Risques restants

1. Tests PostgreSQL non exécutés (bloqués) : migrations, concurrence, idempotence, transactions, sauvegarde/restauration.
2. V21/V22 hors Git, décision métier en attente.
3. Listes admin factures et demandes de facturation non paginées (contrat API à décider).
4. Fuseaux horaires des filtres de devis non audités en profondeur.
5. Application réelle de V27 sur la base cible non vérifiée.

## Décisions non prises (hors périmètre)

Pénalités de retard, remboursements, paiements CAPTURED après annulation, acomptes et soldes : non modifiés.
