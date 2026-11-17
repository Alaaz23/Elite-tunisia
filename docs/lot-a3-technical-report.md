# Lot A.3 — Rapport technique

Statut global : **Lot A NON VALIDÉ**. Aucun test PostgreSQL réel n'a été exécuté. Lot B non commencé. Aucun commit.

## 1. Décisions appliquées

| Décision | Application | Statut |
|---|---|---|
| Un échec de facturation ne bloque pas le retour | Le retour enregistre une demande de facturation (outbox) dans la même transaction | CORRIGÉ ET TESTÉ (H2/mocks) |
| Outbox durable | Table `billing_requests`, worker planifié, reprise au redémarrage | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Rétrofacturation contrôlée | Endpoints admin dry-run et execute, permission `billing:backfill` | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Pré-contrôle V29 | Script en lecture seule `docs/architecture/v29-precheck.sql` | NON EXÉCUTÉ (aucune base autorisée) |
| Revue payment-reconciliation.md | Écarts documentés ci-dessous | EN ATTENTE D'UN FOURNISSEUR EXTERNE |
| Diagnostic code de sortie Maven | Voir section 6 | NON EXPLIQUÉ (A.2) |
| Frontend | Node incompatible | BLOQUÉ PAR L'ENVIRONNEMENT |

## 2. Architecture de facturation après retour (outbox)

- `BookingOperationService.recordReturn` passe la réservation à COMPLETED, enregistre le retour et son audit, puis appelle `BillingRequestService.enqueueMainInvoice(bookingId, actorId)`. Ces trois écritures sont dans la même transaction : si l'une échoue, le retour est annulé.
- La table `billing_requests` (migration `database/migrations/V30__billing_outbox.sql`) porte un `UNIQUE(booking_id, kind)`, un statut PENDING/DONE/DEAD, `attempts`, `last_attempt_at`, `last_error` (1000 caractères max), `invoice_id` et `completed_at`. Un CHECK impose qu'un statut DONE ait un `invoice_id` et un `completed_at`.
- `BillingRequestWorker` (planifié, `app.billing.outbox-scan-ms`, défaut 60 s, délai initial 30 s) reprend les demandes PENDING. Il n'est pas exécuté au démarrage de manière bloquante.
- `BillingRequestService.process` exécute la génération dans un `TransactionTemplate`. En cas d'erreur, l'échec est enregistré dans une transaction REQUIRES_NEW, pour que le compteur de tentatives survive au rollback. Après `app.billing.max-attempts` (défaut 5), la demande passe à DEAD et requiert une intervention manuelle. Il n'existe pas encore d'endpoint de remise à zéro.
- La génération appelle `InvoiceService.createMainInvoiceAfterReturn`, qui renvoie la facture existante si elle est présente. Cela garantit l'absence de doublon en cas de nouvelle tentative.
- Ordre de verrouillage : Vehicle → Booking → demande outbox → Quote. Conforme à `docs/architecture/booking-lock-order.md`, mais non vérifié sur PostgreSQL.

Risques transactionnels :
- Le test de rollback du retour est un test de propagation avec mock, pas un test d'atomicité réel.
- L'hypothèse que `refresh(PESSIMISTIC_WRITE)` conserve le verrou sous Hibernate 6 n'est pas vérifiée.
- La politique de reprise (5 tentatives, sans backoff exponentiel) est une proposition non validée par le métier.

## 3. Rétrofacturation (backfill)

- `BillingBackfillService` classe les réservations COMPLETED selon des motifs : ELIGIBLE, ALREADY_INVOICED, NOT_COMPLETED, DEAD_REQUEST, NO_RETURN, NO_ACCEPTED_QUOTE, MISSING_BILLING_INFO.
- `POST /api/admin/billing/dry-run` ne fait aucune écriture. `POST /api/admin/billing/execute` enqueue puis traite chaque réservation éligible dans sa propre transaction, journalise chaque facture créée et isole les erreurs par élément.
- Reprise possible et idempotence assurées par l'outbox. Aucun exécution au démarrage. Les factures existantes ne sont jamais modifiées.
- Point à confirmer : la définition des « informations de facturation suffisantes » est actuellement ACCEPTED quote avec `total_amount` et `currency` non nuls, plus `customer_id`. Le nom de permission `billing:backfill` est une proposition.
- La requête SQL de classification n'est testée qu'avec mocks. Elle n'a pas été exécutée sur PostgreSQL.

## 4. Pré-contrôle V29

- V29 crée `uq_invoices_one_main_per_booking` (unique sur `invoices(booking_id)` WHERE `quote_id IS NOT NULL`). V28 seule ne couvre que `quote_id`.
- Script : `docs/architecture/v29-precheck.sql`. Quatre requêtes en lecture seule : doublons de factures principales par réservation (bloquant), factures principales sans réservation, factures dont le devis ne correspond pas à la réservation, et répartition principales/complémentaires (informatif).
- Statut : **NON EXÉCUTÉ**. Aucune base PostgreSQL autorisée n'est disponible, et aucune connexion n'a été tentée. Si la première requête renvoie des lignes, V29 doit être arrêtée et arbitrée métier. Aucune fusion ni suppression automatique.

## 5. Revue de `docs/architecture/payment-reconciliation.md`

Constats vérifiés dans le code :
- `PaymentStatus` contient PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED. **UNKNOWN n'existe pas dans l'enum et n'est jamais persisté.** Le document le présente comme état logique, ce qui est correct, mais il ne doit pas être présenté comme implémenté.
- `PaymentService` (vers les lignes 55 et 102) : une erreur fournisseur produit un événement `PROVIDER_ERROR` ("Statut fournisseur inconnu, rapprochement manuel requis") et laisse le paiement en PENDING. Ce mécanisme est le plus proche d'un état UNKNOWN, sans état dédié.
- `PaymentStatusProvider` est documentée mais n'est implémentée nulle part. `SimulatedPaymentProvider` n'a pas de consultation de statut.
- Écarts documentés :
  - Le document mentionne un « journal d'événements de paiement à créer » ; il n'existe pas encore comme mécanisme de réconciliation.
  - Il prévoit un `SELECT FOR UPDATE` sur le paiement pour la transition ; il n'est pas implémenté.
  - Aucune transition de réconciliation n'est tracée comme événement dédié.
- Aucun second débit n'est déclenché automatiquement. Aucune API fournisseur n'est inventée.
- Statut : **EN ATTENTE D'UN FOURNISSEUR EXTERNE** pour la réconciliation réelle. **NON CORRIGÉ** pour les écarts de documentation et d'implémentation listés ci-dessus.

## 6. Diagnostic du code de sortie Maven

- Run A.3 synchrone : `MVN_EXIT=0`, BUILD SUCCESS, 286 exécutés, 0 échec, 0 erreur, 9 ignorés. Les totaux XML Surefire correspondent au log (`backend/lotA3-full.log`).
- Le code 1 signalé en A.2 **n'a pas été reproduit** sur ce run unique. Sa cause reste **non expliquée**. Ne pas la considérer comme résolue.
- Les 9 ignorés sont les tests PostgreSQL protégés par `ELITE_POSTGRES_TEST` (PaymentPostgresIntegrationTests 5, InvoicePostgresIntegrationTests 2, BookingEngineTests 1, QuoteEngineTests 1). Ils ne comptent pas comme réussis.

## 7. Rapprochement Git

Snapshots : `backend/lotA1-git-before.txt` (181), `lotA1-git-after.txt` (195), `lotA2-git-after.txt` (200), `lotA3-git-before.txt` (202), `lotA3-git-after.txt` (208).

- **181 → 195 (+14, passe A.1)** : modifiés `InvoiceRepository.java`, `InvoiceService.java`, `InvoicePostgresIntegrationTests.java`, `InvoiceAttachPaymentUnitTest.java`, `InvoiceServiceTest.java`, `PostgresInvoiceConcurrencyIT.java` ; nouveaux `V28__invoices_one_main_per_quote.sql`, `docs/lot-a1-technical-report.md`, logs `lotA1-*.log`, `lotA1-git-after.txt`.
- **195 → 200 (+5, passe A.2)** : `V29__invoices_one_main_per_booking.sql`, `QuoteServiceLockTest.java`, logs `lotA2-*`, `lotA2-git-after.txt`.
- **200 → 202 (+2)** : `docs/lot-a2-technical-report.md`, `lotA3-git-before.txt`.
- **202 → 208 (+6, passe A.3)** : `database/migrations/V30__billing_outbox.sql`, dossiers `backend/src/main/java/tn/elitetunisia/billing/` et `backend/src/test/java/tn/elitetunisia/billing/`, logs `lotA3-*`, `lotA3-git-after.txt`.
- Aucune entrée supprimée. Les entrées modifiées de la passe précédente (fichiers Invoice, booking, payment, etc.) restent dans le dépôt. Les modifications antérieures au snapshot A.1 (181 entrées) ne sont pas attribuées au Lot A ici.
- Limite : ce rapprochement ne compare que les noms de fichiers. Il ne compare pas le contenu avec les états antérieurs.

## 8. Tests

| Commande | Résultat |
|---|---|
| `.\mvnw.cmd -B -ntp "-Dtest=..." "-Dsurefire.failIfNoSpecifiedTests=false" test` (ciblé, depuis `backend`, liste de classes dans le log) | 25 tests, 0 échec, BUILD SUCCESS (`backend/lotA3-targeted.log`) |
| `.\mvnw.cmd -B -ntp test` (suite complète, JDK 21) | `MVN_EXIT=0`, 286 exécutés, 0 échec, 0 erreur, 9 ignorés (`backend/lotA3-full.log`) |
| Frontend : Node v18.20.8 / npm 10.9.4 | **BLOQUÉ PAR L'ENVIRONNEMENT.** `frontend/package.json` exige Node `^22.22.3 \|\| ^24.15.0 \|\| >=26`. Aucun install, build, test unitaire ou E2E exécuté. Node global non modifié. |

Tests PostgreSQL : **NON EXÉCUTÉS**. Aucun identifiant fourni. Ce qui reste à exécuter sur une base temporaire autorisée, avec `ELITE_POSTGRES_TEST=true` :
- `PaymentPostgresIntegrationTests` (5), `InvoicePostgresIntegrationTests` (2), `BookingEngineTests` (1), `QuoteEngineTests` (1) ;
- tests de concurrence des factures, du verrou devis et de la demande outbox ;
- contrôle V29 avec `v29-precheck.sql`.

Les tests ajoutés pour l'outbox (`BillingRequestServiceTest`, 5 tests) et le backfill (`BillingBackfillServiceTest`, 3 tests) utilisent des mocks. Ils ne valident ni le verrouillage réel ni le SQL de classification.

## 9. Migrations

- V27, V28, V29 : appliquées sur H2 uniquement, **non validées sur PostgreSQL**.
- V30 (`billing_outbox`) : créée, **non exécutée**, ni sur H2 ni sur PostgreSQL en tant que migration Flyway réelle. Aucune migration existante modifiée.
- Pré-contrôle V29 : **NON EXÉCUTÉ** (voir section 4).

## 10. Risques résiduels

1. Aucune validation PostgreSQL réelle : concurrence, index partiels, contraintes CHECK, verrous.
2. Une demande outbox en DEAD n'a pas de mécanisme de remise à zéro.
3. Politique de reprise à valider par le métier.
4. Définition des « informations de facturation suffisantes » à confirmer.
5. Code de sortie Maven 1 de A.2 non expliqué.
6. Réconciliation des paiements non implémentée : pas d'état UNKNOWN persisté, pas de journal dédié, pas de `SELECT FOR UPDATE`.
7. Frontend non validé.

## 11. Statuts

| Sujet | Statut |
|---|---|
| Outbox de facturation après retour | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Rétrofacturation contrôlée | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Pré-contrôle V29 | BLOQUÉ PAR L'ENVIRONNEMENT |
| Migrations V27 à V30 sur PostgreSQL | BLOQUÉ PAR L'ENVIRONNEMENT |
| Réconciliation des paiements | EN ATTENTE D'UN FOURNISSEUR EXTERNE |
| Écarts payment-reconciliation.md | NON CORRIGÉ |
| Code de sortie Maven 1 (A.2) | NON EXPLIQUÉ (non reproduit) |
| Backend H2 (286 tests) | CORRIGÉ ET TESTÉ (sur H2 uniquement) |
| Frontend | BLOQUÉ PAR L'ENVIRONNEMENT |
| Git | Rapprochement fait, aucun commit |

## 12. Points à décider

- Fournir une base PostgreSQL temporaire et ses identifiants pour exécuter les tests réels et V1 à V30.
- Confirmer la politique de reprise de l'outbox (5 tentatives, sans backoff, DEAD manuel).
- Confirmer le nom `billing:backfill` et la définition des informations de facturation suffisantes.
- Autoriser l'installation d'un Node compatible (22.22.3+, 24.15+ ou 26+) pour le frontend.

**Verdict : Lot A NON VALIDÉ.** Les corrections locales sont terminées sur H2. Aucune validation PostgreSQL n'a été effectuée.
