# Lot A.1 — Rapport technique

**Statut global : Lot A NON VALIDÉ.** Les corrections locales sont implémentées et la suite H2 passe (271 exécutés, 0 échec, 0 erreur, 9 ignorés PostgreSQL). Aucune validation PostgreSQL réelle n'a été exécutée : aucun identifiant n'a été fourni. Le Lot B reste bloqué.

## 0. Décisions métier appliquées (passe « Décisions métier »)

- **Décision 1 — Facturation idempotente** : `InvoiceService.createFromQuote` verrouille la réservation puis le devis, renvoie la facture principale existante si elle existe (`InvoiceRepository.findFirstByQuoteId`), et ne crée une facture principale qu'une seule fois par devis. La migration V28 ajoute une garde en base (index unique partiel). Les factures complémentaires (`createReturnChargeInvoice`, `quote_id` NULL) ne sont pas bloquées. L'acceptation d'un devis ne crée toujours pas de facture.
- **Décision 2 — Verrou du devis** : `QuoteService.lock` verrouille la réservation (Véhicule → Réservation), puis la ligne du devis (`QuoteRepository.findLockedById`, `PESSIMISTIC_WRITE`) avec `refresh`. Analyse des chemins concurrents : `PaymentService.reserve` suit le même ordre (Véhicule → Réservation → Devis). `BookingService`, `BookingOperationService` et les services Driver/Transfer ne touchent pas au devis. Aucune inversion d'ordre détectée.
- **Décision 3 — Réconciliation** : architecture et transitions documentées dans [payment-reconciliation.md](/C:/Users/aaziza/Desktop/test/docs/architecture/payment-reconciliation.md). **Non implémentée**, aucun fournisseur ni API inventé.

Limites : le verrou du devis et l'idempotence de la V28 ne sont **pas** validés sur PostgreSQL. La création de la facture principale après retour n'a **pas** de déclencheur en production (voir section 12).

## 1. Règles de sécurité appliquées

- Aucun `git reset`, `git checkout`, `git clean` ni commit.
- `pgpass.conf` non lu, aucun mot de passe recherché, aucune connexion PostgreSQL tentée.
- Aucune base créée, aucune base existante modifiée.
- Aucun fournisseur de paiement inventé, aucune réconciliation simulée.

## 2. État Git avant / après

| Mesure | Avant (baseline `backend/lotA1-git-before.txt`) | Après |
|---|---|---|
| Entrées `git status --porcelain` | 181 | 195 (sortie complète : `backend/lotA1-git-after.txt`) |

Comparaison ligne à ligne (`Compare-Object`) : **aucune entrée supprimée ni réinitialisée**. Les 14 nouvelles entrées sont :

- Fichiers déjà suivis devenus modifiés dans ce passage : `InvoiceRepository.java`, `InvoiceService.java`, `InvoiceAttachPaymentUnitTest.java`, `InvoiceServiceTest.java`, `PostgresInvoiceConcurrencyIT.java`, `InvoicePostgresIntegrationTests.java`.
- Nouveau fichier de migration : `database/migrations/V28__invoices_one_main_per_quote.sql`.
- Journaux de preuve : `backend/lotA1-decisions-targeted.log`, `backend/lotA1-decisions-full.log`, `backend/lotA1-full.log`, `backend/lotA1-invoice-service.log`, `backend/lotA1-invoice-targeted.log`, `backend/lotA1-git-after.txt`.

`QuoteService.java` figurait déjà comme modifié dans la baseline (travaux des lots précédents). Ses modifications de ce passage (verrou du devis) sont **mêlées** à ce travail préexistant et n'ont pas été séparées. Le dossier `docs/architecture/` était déjà non suivi ; le fichier `payment-reconciliation.md` y a été ajouté. `docs/start-local.md` était déjà modifié et n'a pas été touché.

Aucune modification préexistante n'a été supprimée. Les avertissements CRLF de Git sont bénins.

Les avertissements CRLF de Git sont bénins.

## 3. Migrations

- Migrations existantes : `database/migrations` V1 à V27, copiées vers `db/migration` par le `pom.xml`.
- Aucune migration déjà appliquée n'a été modifiée ni supprimée.
- **Nouvelle migration V28** (`database/migrations/V28__invoices_one_main_per_quote.sql`) : index unique partiel `uq_invoices_one_main_per_quote` sur `invoices(quote_id) WHERE quote_id IS NOT NULL`. Elle n'interdit pas les factures complémentaires (`quote_id` NULL). Elle ne supprime aucune donnée. Pré-contrôle documenté en commentaire : si des doublons de `quote_id` existent déjà, la migration échoue et doit être traitée manuellement. **Non exécutée sur PostgreSQL.** H2 n'applique pas Flyway (`spring.flyway.enabled=false`), donc cet index n'est pas exercé par la suite H2.
- Version la plus élevée de l'historique : V28. V7 a renommé l'ancienne table `invoices` en `legacy_invoices` ; la table `invoices` actuelle porte `reference` UNIQUE (V7), `quote_id` et `booking_id` nullables.
- V27 (`payments_idempotency_scope.sql`) remplace l'index global `payments_idempotency_idx` (V6) par l'unicité `(customer_id, quote_id, idempotency_key)`. Relecture faite : pas de suppression de données, pas de conflit détecté avec V21 (`uq_payments_one_captured_per_quote`). **Non exécutée sur PostgreSQL.**

## 4. Fichiers examinés

- `BookingService`, `BookingOperationService`, `PaymentService`, `QuoteService` (méthode `lock`)
- `InvoiceService`, `InvoiceRepository`, `InvoiceDocumentServiceImpl`
- `DriverAssignmentService`, `TransferService`, `GuaranteeService`, `PartnerService` (accès `PESSIMISTIC_WRITE` / `FOR UPDATE`)
- `database/migrations/V6`, `V7`, `V21`, `V27`
- Tests : `InvoiceServiceTest`, `InvoiceAttachPaymentUnitTest`, `BookingOperationServiceTest`, `PaymentServiceUnitTest`, `PostgresInvoiceConcurrencyIT`, `BookingLockOrderPostgresIT`, `BookingAuditAtomicityPostgresIT`, `PostgresIdempotencyIT`

## 5. Fichiers modifiés dans ce passage et raisons

- [InvoiceService.java](/C:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/invoice/InvoiceService.java)
  - `createFromQuote` : suppression du fallback `System.currentTimeMillis() % 1000000`. Le numéro vient de `nextval('invoice_seq')` et les erreurs remontent.
  - `createFromQuote` (Décision 1) : verrouille Véhicule → Réservation (`bookingService.lockForOperation`), puis le devis (`findLockedById` + `refresh`), renvoie la facture principale existante si elle existe.
  - `attachPayment` : le `ON CONFLICT DO NOTHING` capturé et avalé est remplacé par une vérification d'existence, puis un insert.
  - Constructeur : ajout de `BookingService` et `EntityManager`.
- [InvoiceRepository.java](/C:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/invoice/InvoiceRepository.java) : ajout de `findFirstByQuoteId`. Raison : idempotence de la facture principale.
- [QuoteService.java](/C:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/quote/QuoteService.java) (`lock`) : verrou explicite `PESSIMISTIC_WRITE` sur la ligne du devis après le verrou réservation (Décision 2). Fichier déjà modifié avant ce passage (voir section 2).
- [V28__invoices_one_main_per_quote.sql](/C:/Users/aaziza/Desktop/test/database/migrations/V28__invoices_one_main_per_quote.sql) (nouveau) : garde d'unicité de la facture principale par devis.
- [docs/architecture/payment-reconciliation.md](/C:/Users/aaziza/Desktop/test/docs/architecture/payment-reconciliation.md) (nouveau) : architecture de réconciliation, non implémentée (Décision 3).
- [docs/architecture/booking-lock-order.md](/C:/Users/aaziza/Desktop/test/docs/architecture/booking-lock-order.md) : ajout du verrou devis et du chemin `InvoiceService.createFromQuote`.
- Tests modifiés :
  - [InvoiceServiceTest.java](/C:/Users/aaziza/Desktop/test/backend/src/test/java/tn/elitetunisia/invoice/InvoiceServiceTest.java) : constructeur à 8 arguments ; test de lien existant (idempotence) ; test d'ordre de verrouillage Réservation puis Devis avec refus d'un devis non accepté.
  - [InvoiceAttachPaymentUnitTest.java](/C:/Users/aaziza/Desktop/test/backend/src/test/java/tn/elitetunisia/invoice/InvoiceAttachPaymentUnitTest.java) : constructeur mis à jour (ce fichier ne compilait plus avec le nouveau constructeur ; corrigé et relancé).
  - [PostgresInvoiceConcurrencyIT.java](/C:/Users/aaziza/Desktop/test/backend/src/test/java/tn/elitetunisia/invoice/PostgresInvoiceConcurrencyIT.java) : attend désormais une seule facture principale par devis (test renommé `concurrentInvoiceCreationProducesSingleMainInvoice`). Les exceptions des futures ne sont plus avalées. **Skipped sans PostgreSQL.**
  - [InvoicePostgresIntegrationTests.java](/C:/Users/aaziza/Desktop/test/backend/src/test/java/tn/elitetunisia/InvoicePostgresIntegrationTests.java) : `invoiceReferenceUnique` remplacé par `invoiceCreationFromSameQuoteIsIdempotent`. **Skipped sans PostgreSQL.**


Les corrections ISS-01, ISS-02 et ISS-03 de la passe précédente (`BookingOperationService`, `PaymentService`, `BookingService`, V27, tests associés) restent telles quelles dans l'arbre de travail.

## 6. Analyse et corrections par problème

### ISS-01 — Verrous
- Ordre global Véhicule → Réservation → Devis. `QuoteService.lock` prend le verrou de réservation via `bookingService.lockForCommercialOperation`, puis rafraîchit le devis. Toutes les mutations de devis (update, send, customerAction, cancel, expire) passent par ce chemin. `PaymentService.reserve` prend le même verrou en premier.
- **Conclusion par lecture de code :** une modification concurrente du devis pendant une réservation ou un paiement est bloquée par le verrou de réservation. Ce n'est pas démontré par un test PostgreSQL.
- Double verrouillage du véhicule dans `BookingOperationService` : même ligne, pas de risque de deadlock. Le nettoyage est sans risque mais non fait, pour ne pas toucher au code sans nécessité.
- **Verrou ligne sur le devis (Décision 2) : ajouté** dans `QuoteService.lock` (`findLockedById` puis `refresh`), après le verrou réservation. Ordre : Véhicule → Réservation → Devis. Pas d'inversion trouvée dans `BookingService`, `PaymentService`, `QuoteService`, `InvoiceService`.
- Test unitaire du verrou devis dans `QuoteService.lock` : **non ajouté**. Seul l'ordre du chemin `InvoiceService.createFromQuote` est testé en unitaire (InOrder). Le verrou devis de `QuoteService` n'est couvert que par le futur test PostgreSQL de concurrence.
- Statut : **IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL.**

### ISS-02 — Retour et audit
- `completeAfterReturn` est la transition officielle (propagation MANDATORY). Le retour sans PICKUP et sur état invalide est refusé par les tests existants.
- Atomicité : un échec d'audit annule la finalisation grâce à la transaction. Les unit tests utilisent un `PlatformTransactionManager` mocké, donc le rollback réel n'est prouvé que par les tests PostgreSQL d'atomicité (`BookingAuditAtomicityPostgresIT`).
- Statut : **IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL.**

### ISS-03 — Paiements PENDING
- Idempotence par `(customer_id, quote_id, idempotency_key)` (V27).
- Flux en trois phases : réservation en transaction, appel fournisseur hors verrou, finalisation en transaction. Une erreur fournisseur laisse le paiement en `PENDING`. Une erreur réseau ne le transforme jamais en `FAILED`.
- Une nouvelle opération est refusée tant que le paiement précédent est `PENDING`.
- **Cas non résolu :** si la finalisation échoue après une capture fournisseur, la transaction est annulée et le paiement reste `PENDING` alors que le fournisseur a encaissé. Ce cas est identifiable mais seule une réconciliation peut le résoudre. Elle n'est pas implémentée.

**États et transitions :** détaillés dans [payment-reconciliation.md](/C:/Users/aaziza/Desktop/test/docs/architecture/payment-reconciliation.md). Résumé : `PENDING`, `AUTHORIZED`, `CAPTURED`, `FAILED` sont dans l'enum `PaymentStatus`. `UNKNOWN` est un état logique non présent dans l'enum (ajout à décider avec la migration future).

**Interface de réconciliation :** spécifiée seulement (`PaymentStatusProvider`), non implémentée. Aucun fournisseur ni API n'a été inventé.

- Statut : **IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL.** La réconciliation est **NON CORRIGÉE**.

### ISS-04 / ISS-05 — Numérotation des factures
- Exception 1 : fallback après échec de `nextval('invoice_seq')`. Sur PostgreSQL, l'erreur rend la transaction inutilisable et le fallback aurait échoué aussi. Le fallback temporel est supprimé.
- Exception 2 : fallback après échec de `ON CONFLICT DO NOTHING` dans `attachPayment`. Remplacé par une vérification d'existence et un insert, pour que les erreurs réelles ne soient plus masquées.
- L'unicité de `reference` est garantie par la contrainte `UNIQUE` de V7.
- **Idempotence (Décision 1) :** `createFromQuote` renvoie la facture principale existante pour un devis donné ; la création concurrente est sérialisée par le verrou devis ; la V28 garantit l'unicité en base. La facture principale de location **n'est pas déclenchée en production** : `createFromQuote` n'a aucun appelant de production (seulement des tests). Le seul chemin de production est `createReturnChargeInvoice` (facture complémentaire de retour), qui ne crée pas la facture principale. Ce point est signalé en section 12 et n'a pas été implémenté sans décision.
- **Décision métier en attente :** qui déclenche la facture principale après retour, et faut-il un discriminant de type de facture ?
- Statut : **CORRIGÉ ET TESTÉ en H2 (idempotence unitaire). Non validé sur PostgreSQL** (concurrence et V28).

## 7. Tests exécutés

| Commande | Résultat | Log |
|---|---|---|
| `mvnw.cmd -B -ntp test -Dtest=InvoiceServiceTest,InvoiceServiceUnitTest,InvoiceAttachPaymentUnitTest,BookingOperationServiceTest,PaymentServiceUnitTest` (première exécution) | ERREUR de compilation de `InvoiceAttachPaymentUnitTest` (ancien constructeur) : 2 erreurs, BUILD FAILURE | `backend/lotA1-decisions-targeted.log` (écrasé par la suite) |
| Même commande après correction du constructeur | 27 run, 0 failure, 0 error, 0 skipped, BUILD SUCCESS | `backend/lotA1-decisions-targeted.log` |
| `mvnw.cmd -B -ntp test` (suite H2 complète) | **271 run, 0 failure, 0 error, 9 skipped (PostgreSQL), BUILD SUCCESS** | `backend/lotA1-decisions-full.log` |

Détail des classes ciblées : `BookingOperationServiceTest` 7/7, `InvoiceAttachPaymentUnitTest` 2/2, `InvoiceServiceTest` 6/6, `InvoiceServiceUnitTest` 2/2, `PaymentServiceUnitTest` 10/10.

Les 9 tests ignorés sont des tests PostgreSQL protégés par `ELITE_POSTGRES_TEST=true`. Ils ne sont **pas** comptés comme réussis.

Au cours de ce passage, un test a échoué : `attachPayment_propagatesInsertErrorInsteadOfSwallowingIt` (matcher `any()` en varargs de `jdbc.update`, corrigé avec des arguments exacts). Lors de la passe « Décisions métier », la compilation de `InvoiceAttachPaymentUnitTest` a échoué après le changement de constructeur ; corrigé.

Non exécuté dans ce passage : tests frontend (Node non vérifié pour ce lot).

Les tests ci-dessus n'ont pas vérifié le verrou devis de `QuoteService.lock` ni la garde V28 : ce sont des comportements PostgreSQL.

## 8. Tests non exécutés et raisons

- **Tous les tests PostgreSQL** (9 classes ignorées) : aucun identifiant fourni, aucune connexion tentée. Ils restent ignorés et ne sont **pas** comptés comme réussis.
- Tests de concurrence PostgreSQL (verrous Véhicule → Réservation → Devis, création concurrente de factures) : non exécutés.
- Test d'atomicité d'audit sur PostgreSQL : non exécuté.
- Vérification de V27 et V28 sur base vierge : non exécutée.
- Test unitaire direct du verrou devis dans `QuoteService.lock` : non ajouté dans ce passage.
- Tests frontend : non exécutés.


## 9. Tests PostgreSQL à exécuter plus tard

- Variables d'environnement attendues : `ELITE_POSTGRES_TEST=true` et les paramètres de connexion lus par `PostgresTestHelper`. Aucun mot de passe ne doit être écrit dans le dépôt.
- Base temporaire vierge, dédiée, sur `127.0.0.1:15432`, créée uniquement avec un compte de test autorisé. Aucune base existante ne doit être touchée.
- Ordre d'exécution PostgreSQL recommandé : appliquer V1 à V28 sur la base vierge (Flyway) ; contrôler V28 (pré-contrôle de doublons `quote_id` déjà documenté dans la migration) ; puis lancer les classes ci-dessous avec `ELITE_POSTGRES_TEST=true`.
- Classes à exécuter (ajouts de ce passage en gras) :
  - `BookingLockOrderPostgresIT`
  - `BookingAuditAtomicityPostgresIT`
  - `payment/PostgresIdempotencyIT`
  - **`PostgresInvoiceConcurrencyIT`** (`concurrentInvoiceCreationProducesSingleMainInvoice`)
  - `PaymentPostgresIntegrationTests`
  - `BookingEngineTests`
  - `QuoteEngineTests`
  - **`InvoicePostgresIntegrationTests`** (`invoiceCreationFromSameQuoteIsIdempotent`)
  - et les autres classes portant `ELITE_POSTGRES_TEST`
- **À ajouter avant validation :** un test PostgreSQL de concurrence Devis (verrou `QuoteService.lock` contre `PaymentService.reserve`), non encore écrit.

## 10. Risques résiduels

- Aucune preuve PostgreSQL des verrous (Véhicule → Réservation → Devis), de l'atomicité, de V27, de V28 ni de l'idempotence. C'est le risque principal.
- Pas de déclencheur de production pour la facture principale après retour : le métier est validé, mais le chemin n'est pas câblé. Risque de facturation principale absente.
- V28 échouera si des doublons de `quote_id` existent déjà en production (pré-contrôle à exécuter avant migration).
- Paiement capturé par le fournisseur mais finalisation échouée : reste `PENDING` sans réconciliation (architecture documentée, non implémentée).
- Verrou devis : ajouté sans test unitaire direct ; comportement sous charge non démontré.
- Frontend non vérifié dans ce passage.

## 11. Statuts par problème

| Problème | Statut |
|---|---|
| ISS-01 ordre des verrous (Véhicule → Réservation) | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| ISS-01 verrou ligne sur le devis (Décision 2) | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| ISS-02 retour, audit, transitions | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| ISS-03 idempotence, PENDING, erreurs fournisseur | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| ISS-03 réconciliation fournisseur (Décision 3) | NON CORRIGÉ (architecture documentée seulement) |
| ISS-04 fallback séquence facture | CORRIGÉ ET TESTÉ (H2) |
| ISS-05 fallback ON CONFLICT facture | CORRIGÉ ET TESTÉ (H2) |
| Idempotence de facture principale (Décision 1) | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL (idempotence unitaire H2 OK) |
| Garde d'unicité V28 | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| V27 | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Déclencheur de la facture principale après retour | EN ATTENTE D'UNE DÉCISION MÉTIER |

## 12. Décisions métier

Décisions appliquées (validées par l'utilisateur) :
1. Facturation idempotente : une seule facture principale par devis, facture complémentaire autorisée (appliqué).
2. Verrou explicite du devis dans `QuoteService.lock`, ordre Véhicule → Réservation → Devis (appliqué).
3. Réconciliation : architecture seule, pas de fournisseur fictif (appliqué à la documentation).

Décisions encore nécessaires :
1. Quel composant déclenche la facture principale après retour du véhicule ? Aucun chemin de production ne la crée aujourd'hui.
2. Faut-il un discriminant de type de facture (principale ou complémentaire) ? Aujourd'hui, la distinction repose sur `quote_id` NULL.
3. Contrat et API du fournisseur pour la consultation de statut (réconciliation).
4. `UNKNOWN` : nouvelle valeur d'enum et migration, ou statut dérivé ?

## 13. Conclusion

Corrections locales implémentées et suite H2 verte : 271 exécutés, 0 échec, 0 erreur, 9 ignorés (PostgreSQL). Ces 9 tests ne sont pas réussis, ils ne sont pas exécutés.

**Verdict Lot A : NON VALIDÉ.** La validation PostgreSQL réelle (concurrence, V27, V28, atomicité) n'a pas été effectuée, faute d'identifiants. Le Lot B reste bloqué.

