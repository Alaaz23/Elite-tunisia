# Lot A.2 — Rapport technique

Statut global : **Lot A NON VALIDÉ**. Aucune exécution PostgreSQL réelle n'a eu lieu (aucun identifiant de test fourni). Lot B non commencé.

## 1. Modifications de cette passe

| Fichier | Raison |
|---|---|
| `backend/src/main/java/tn/elitetunisia/invoice/InvoiceRepository.java` | `findFirstByQuoteId` remplacé par `findFirstByBookingIdAndQuoteIsNotNull` : l'idempotence de la facture principale se fait par réservation, pas par devis. |
| `backend/src/main/java/tn/elitetunisia/invoice/InvoiceService.java` | `createFromQuote` utilise la recherche par réservation. Ajout de `createMainInvoiceAfterReturn` (MANDATORY) : exige `COMPLETED`, prend le dernier devis `ACCEPTED`, sinon `DomainException.conflict("QUOTE_NOT_ACCEPTED")`. |
| `backend/src/main/java/tn/elitetunisia/booking/BookingOperationService.java` | `recordReturn` appelle `invoices.createMainInvoiceAfterReturn(booking, actorId)` juste après `bookingService.completeAfterReturn(...)`, dans la même transaction. |
| `backend/src/main/java/tn/elitetunisia/quote/QuoteRepository.java` | Ajout de `findFirstByBookingIdAndStatusOrderByVersionDesc`. |
| `database/migrations/V29__invoices_one_main_per_booking.sql` (nouveau) | Index unique partiel `uq_invoices_one_main_per_booking ON invoices (booking_id) WHERE quote_id IS NOT NULL`, avec requête de pré-contrôle en en-tête. |
| `backend/src/test/java/tn/elitetunisia/invoice/InvoiceServiceTest.java` | Stubs migrés ; 5 tests ajoutés (idempotence, verrou booking → quote, devis non accepté, booking non COMPLETED, pas de devis accepté). |
| `backend/src/test/java/tn/elitetunisia/booking/BookingOperationServiceTest.java` | 2 tests ajoutés : ordre `completeAfterReturn` puis facture ; erreur de facturation propagée. |
| `backend/src/test/java/tn/elitetunisia/quote/QuoteServiceLockTest.java` (nouveau) | Test unitaire direct de `QuoteService.lock` (ordre booking → quote, `refresh` PESSIMISTIC_WRITE, variante client). |

Non modifiés : V27, V28 (migrations existantes), `PaymentService`, `BookingService`, `docs/architecture/payment-reconciliation.md`.

## 2. Déclenchement de la facture principale

- La facture principale est créée **dans la transaction du retour**, après `completeAfterReturn`.
- Conséquences vérifiées par test : pas de facture sans retour ; si la facturation échoue, l'exception remonte et annule le retour (`mainInvoiceFailureRollsBackReturnByPropagation`) ; une nouvelle tentative de retour ne crée pas de doublon (idempotence par réservation).
- Les factures complémentaires (`quote_id` NULL, frais de retour) ne sont pas touchées par l'index V29.
- **Point métier à confirmer** : un échec de facturation bloque désormais le retour. C'est un choix de conception, pas une décision validée.
- **Risque résiduel** : les réservations déjà `COMPLETED` avant ce changement n'ont pas de facture principale. Aucun mécanisme de rattrapage n'a été implémenté. Décision métier requise.

## 3. Unicité par réservation

- V28 (`quote_id` unique partiel) **ne suffit pas** : `quotes_booking_version_unique` (V5) autorise plusieurs versions de devis par réservation, donc plusieurs devis distincts pourraient produire chacun une facture principale.
- V29 ajoute la contrainte sur `booking_id`, limitée aux factures liées à un devis. Pré-contrôle à exécuter avant migration : `SELECT booking_id, count(*) FROM invoices WHERE quote_id IS NOT NULL GROUP BY booking_id HAVING count(*) > 1;` (doit retourner 0 ligne). **Non exécuté** sur une base réelle.
- V27 et V28 n'ont pas été modifiées.

## 4. Tests exécutés

| Commande | Résultat |
|---|---|
| `mvnw.cmd -B -ntp -q test-compile` | Succès (code 0) |
| Ciblé : `InvoiceServiceTest,InvoiceAttachPaymentUnitTest,BookingOperationServiceTest,QuoteServiceLockTest` (log `backend/lotA2-targeted.log`) | 22 tests, 0 échec, 0 erreur, 0 ignoré — BUILD SUCCESS |
| Suite H2 complète `mvnw.cmd -B -ntp test` (log `backend/lotA2-full.log`) | 278 tests, 0 échec, 0 erreur, 9 ignorés — BUILD SUCCESS |

Remarque : la commande de la suite complète a rendu le code de sortie 1 au shell, alors que le log Maven se termine par `BUILD SUCCESS` et ne contient aucune ligne `[ERROR]`. Cause non identifiée ; à surveiller.

Erreurs corrigées pendant la passe (tests unitaires seulement) : `assertEquals` non importé ; un devis mocké sans `getId()` stubbé (QUOTE_NOT_FOUND).

## 5. Tests non exécutés (ignorés, non comptés comme réussis)

- 9 tests ignorés au total (log `backend/lotA2-full.log`, lignes 255, 518, 594, 654) :
  - `BookingEngineTests` : 1 (test PostgreSQL, `@EnabledIfEnvironmentVariable ELITE_POSTGRES_TEST`, ligne 171 de `BookingEngineTests.java`) ;
  - `InvoicePostgresIntegrationTests` : 2 (dont `invoiceCreationFromSameQuoteIsIdempotent`) ;
  - `PaymentPostgresIntegrationTests` : 5 ;
  - `QuoteEngineTests` : 1 (test PostgreSQL, ligne 145).
  Ils sont protégés par `ELITE_POSTGRES_TEST`. Aucun n'est compté comme réussi.
- Migrations V27, V28, V29 : **non exécutées** sur PostgreSQL. H2 ne les applique pas (`spring.flyway.enabled=false`).
- Test PostgreSQL de concurrence sur un même devis entre deux transactions : **non ajouté dans cette passe**. Les tests existants (`PostgresInvoiceConcurrencyIT`) doivent être étendus puis exécutés.
- Revue de `docs/architecture/payment-reconciliation.md` : non relue dans cette passe (rédigée en A.1).
- Tests frontend : non lancés dans cette passe.

## 6. ISS-01 / verrous

- `QuoteService.lock` : Vehicle (via `lockForCommercialOperation`) → Booking → Quote (`findLockedById` + `refresh` PESSIMISTIC_WRITE). Couvert par `QuoteServiceLockTest` (unitaire). **Non validé sur PostgreSQL.**
- `PaymentService` n'appelle pas `QuoteService.lock` ; l'interaction a été vérifiée par revue de code, pas par test.
- Le double verrouillage du véhicule dans `recordReturn` est redondant mais inoffensif ; nettoyage non fait (analyse à conclure).

## 7. Statuts

| Élément | Statut |
|---|---|
| Déclenchement facture principale après retour (code + tests unitaires) | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Idempotence par réservation (tests unitaires H2) | CORRIGÉ ET TESTÉ (H2 uniquement) |
| Unicité par réservation V29 | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Verrou devis `QuoteService.lock` (unitaire) | CORRIGÉ ET TESTÉ (unitaire) ; concurrence PG NON VALIDÉE |
| Concurrence PostgreSQL facturation / devis | NON CORRIGÉ (test PG non étendu, non exécuté) |
| Réconciliation paiements (documentation) | Documentée, non implémentée — EN ATTENTE D'UNE DÉCISION MÉTIER pour le fournisseur |
| Retour sans PICKUP / état invalide / atomicité audit | CORRIGÉ ET TESTÉ (H2) ; atomicité PG NON VALIDÉE |
| Factures des réservations COMPLETED antérieures | EN ATTENTE D'UNE DÉCISION MÉTIER |
| Échec de facturation bloquant le retour | EN ATTENTE D'UNE DÉCISION MÉTIER |

## 8. Git

- Baseline A.1 : `backend/lotA1-git-after.txt` (195 entrées).
- État après cette passe : `backend/lotA2-git-after.txt` (200 entrées).
- Entrées nouvelles par rapport à A.1 : `backend/lotA2-full.log`, `backend/lotA2-git-after.txt`, `backend/lotA2-targeted.log`, `backend/src/test/java/tn/elitetunisia/quote/QuoteServiceLockTest.java`, `database/migrations/V29__invoices_one_main_per_booking.sql`.
- Aucune entrée retirée. Aucune commande destructive, aucun commit, aucun fichier supprimé.

## 9. Points bloqués par PostgreSQL

- Exécution de V27, V28, V29 sur une base vierge `127.0.0.1:15432` (base temporaire dédiée, identifiants de test explicites requis).
- Pré-contrôle de doublons avant V28/V29 sur les bases existantes (lecture seule, à arbitrer si doublons).
- Tests `PostgresInvoiceConcurrencyIT`, `InvoicePostgresIntegrationTests`, `BookingAuditAtomicityPostgresIT`, `BookingLockOrderPostgresIT`, `PaymentPostgresIntegrationTests`.
- Variables attendues : `ELITE_POSTGRES_TEST=true` et les paramètres de connexion de test fournis explicitement. Aucun pgpass lu, aucune connexion tentée.

## 10. Décisions métier nécessaires

1. Confirmer qu'un échec de facturation doit bloquer le retour (choix actuel) ou être traité autrement.
2. Définir le traitement des réservations COMPLETED sans facture principale (rattrapage ou non).
3. Fournisseur de paiement et contrat de consultation de statut (réconciliation toujours non implémentée).

## 11. Risques restants

- Aucune preuve PostgreSQL : contraintes, verrous, concurrence et migrations non démontrés.
- Verrou devis : comportement de `refresh(PESSIMISTIC_WRITE)` sous Hibernate 6 non vérifié en réel.
- Blocage du retour en cas d'erreur de facturation (impact opérationnel).
- Code de sortie 1 inexpliqué sur la suite complète malgré `BUILD SUCCESS`.
