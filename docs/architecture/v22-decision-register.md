# Registre de décision V22 — Elite Tunisia

**Date de référence :** 2026-11-02
**Migration concernée :** `database/proposed/V22__payments_quote_fk_restrict.sql` (non appliquée, non déplacée)
**Statut du document :** référentiel de préparation. Ce document n'est pas une autorisation d'exécution, ni une validation technique de V22, ni une décision métier adoptée.
**Nomenclature (Phase 0.1)** : les identifiants `D1` à `D6`, `D5b`, `D-01` de ce registre portent sur la migration V22. Ils ne correspondent pas aux `D1`–`D15` de la [roadmap §15](./implementation-roadmap.md), ni aux `D1`–`D14` du [benchmark §10.4](./international-benchmark-and-target-architecture.md). Les décisions métier globales sont dans [business-decisions-pending.md](./business-decisions-pending.md).
**Rapports historiques (non réécrits) :** [v22-validation-report.md](./v22-validation-report.md), [v22-final-review.md](./v22-final-review.md), [payment-history-integrity-review.md](./payment-history-integrity-review.md)

---

## 0. Verdict de cette phase

**NOT READY FOR PHASE 2.**

Les définitions de tests sont désormais unifiées dans ce registre, mais elles ne sont pas encore adoptées par l'utilisateur. Le brief original reste introuvable. Plusieurs décisions métier restent ouvertes. Les conditions de passage à READY sont listées en section 9.

---

## 1. Légende

| Étiquette | Signification |
|---|---|
| **[CONFIRMÉ]** | Constaté dans un fichier ou une sortie de commande du dépôt, avec référence |
| **[HYPOTHÈSE]** | Comportement attendu, non prouvé par exécution sur PostgreSQL ou Flyway dans cet environnement |
| **[PROPOSITION]** | Choix technique recommandé, non adopté |
| **[DÉCISION MÉTIER]** | Choix de politique, relevant de l'utilisateur |
| **[À VALIDER]** | Point qui doit être tranché par l'utilisateur avant la Phase 2 |

Aucune recommandation n'est une décision adoptée tant qu'elle n'est pas marquée comme telle par l'utilisateur.

---

## 2. Faits confirmés

| ID | Fait | Preuve |
|---|---|---|
| F-01 | `fk_payments_quote` : `payments.quote_id` → `quotes(id)` **ON DELETE CASCADE** | [V6__payments.sql](../../database/migrations/V6__payments.sql) ligne 21 |
| F-02 | `payments.booking_id` → `bookings(id)` ON DELETE SET NULL | V6 ligne 22 |
| F-03 | `payment_events.payment_id` → `payments(id)` ON DELETE CASCADE (nom `fk_payment_events_payment`) | V6 ligne 31 |
| F-04 | `invoice_payments.payment_id` → `payments(id)` ON DELETE CASCADE, **sans nom explicite** | [V7__invoices.sql](../../database/migrations/V7__invoices.sql) ligne 46 |
| F-05 | `invoice_payments.invoice_id` → `invoices(id)` ON DELETE CASCADE | V7 ligne 45 |
| F-06 | `quote_items.quote_id` → `quotes(id)` ON DELETE CASCADE | [V1__initial_schema.sql](../../database/migrations/V1__initial_schema.sql) ligne 146 |
| F-07 | `quote_events.quote_id` → `quotes(id)` **sans clause ON DELETE** (comportement NO ACTION) : supprimer un devis ayant des événements échoue, indépendamment de V22 | [V5__quotes.sql](../../database/migrations/V5__quotes.sql) ligne 44 |
| F-08 | `quotes.booking_id` → `bookings(id)` sans clause ON DELETE (NO ACTION) | V5 ligne 7 |
| F-09 | Aucun index sur `payments(quote_id)` dans V1 à V20 ni dans le code Java | Phase 1.1, annexe E du rapport |
| F-10 | La proposition V22 contient DROP puis ADD NOT VALID dans un seul fichier, `SET LOCAL lock_timeout = '5s'`, sans VALIDATE | [V22](../../database/proposed/V22__payments_quote_fk_restrict.sql) |
| F-11 | Le fichier V22 indique que VALIDATE doit être exécuté hors de la transaction du DROP | En-tête de V22 |
| F-12 | Aucune suppression de devis (`deleteById`, `deleteAll`, `DELETE FROM`, `quotes.delete`) n'a été trouvée dans `backend/src/main/java` ni dans `frontend/src/app`. Recherche par motif, non exhaustive | Grep de cette phase |
| F-13 | Aucune suppression de `payments` ni de `invoice_payments` n'a été trouvée dans le code Java (hors `delete` de `vehicles`, `categories`, `brands`, `driver assignments` sans rapport) | Grep de cette phase |
| F-14 | `InvoiceService` insère dans `invoice_payments` par SQL natif (`on conflict do nothing`), lignes 97 à 105 | [InvoiceService.java](../../backend/src/main/java/tn/elitetunisia/invoice/InvoiceService.java) |
| F-15 | Flyway 12.4.0 via Spring Boot 4.1.1 : `flyway-core` et `flyway-database-postgresql` résolus (`mvnw -o dependency:tree`, JDK 21, code de sortie 0, stderr vide) | Phase 1.1 |
| F-16 | `application.yml` : `flyway.enabled: true`, `locations: classpath:db/migration`. Aucune clé `executeInTransaction` ni `mixed` dans `application.yml`, `application-test.yml` ou `backend/pom.xml` | Phase 1.2, section 1.2-C |
| F-17 | Le profil test `application-test.yml` désactive Flyway (`flyway.enabled: false`) et utilise `ddl-auto: create-drop` : H2 et ce profil ne prouvent rien sur les FK PostgreSQL réelles | Revue finale, section 9 |
| F-18 | `scripts/test-backend-postgres.ps1` exécute `DROP DATABASE IF EXISTS ... WITH (FORCE)` (ligne 44) puis `CREATE DATABASE`. Il n'a jamais été exécuté | Phase 1.1, annexe G |
| F-19 | `MigrationWithExistingDataIT` contient un `DROP DATABASE ... WITH (FORCE)` (ligne 350). Cette classe ne doit pas être lancée dans les commandes de Phase 2 | Phase 1.1 |
| F-20 | Doublon V11 : copie active dans `database/migrations/`, copie inactive suivie dans `backend/src/main/resources/db/migration/`, exclue du packaging par `db/migration/**` dans `backend/pom.xml` | Phase 1.1, annexe H |
| F-21 | Le brief original de la mission V22 est introuvable dans le dépôt et dans l'historique Git | Phase 1.2, section 1.2-A |
| F-22 | Les définitions de T1, T2, T12, T13 et T14 divergent entre les rapports (voir section 6.2) | Phase 1.2, section 1.2-B |
| F-23 | Node 18.20.8 installé, insuffisant pour `frontend/package.json` (Node 22.22.3+ ou 24.15+). Le build Angular n'a pas été vérifié | Phase 1.1 |

---

## 3. Hypothèses (non prouvées)

| ID | Hypothèse | Pourquoi elle n'est pas prouvée | Moyen de la confirmer |
|---|---|---|---|
| H-01 | V22 s'exécute dans une transaction Flyway (valeur par défaut `executeInTransaction=true`) | Source secondaire (résumé de recherche web). Source primaire Flyway non ouverte | Documentation Flyway 12.4.0 officielle + test P-10 |
| H-02 | Avec deux FK coexistantes (CASCADE et RESTRICT), l'ordre des triggers RI change le résultat d'un DELETE de devis | Raisonnement sur les triggers, non mesuré | Test T12-coexistence (section 6.5), hors périmètre retenu sauf reprise de la séquence temporaire |
| H-03 | Le nom réel de la FK de `invoice_payments` est `invoice_payments_payment_id_fkey` | Nom par défaut PostgreSQL, non lu sur la base | Lecture seule `pg_constraint` en Phase 2 |
| H-04 | Le serveur PostgreSQL est en version 18 | Repris de la revue finale, non vérifié | `SHOW server_version` en lecture seule |
| H-05 | `lock_timeout` produit SQLSTATE 55P03 après 5 s et annule la transaction | Comportement documenté, non mesuré dans ce projet | Test T13b |
| H-06 | Un échec de migration transactionnelle laisse une ligne `success=false` dans `flyway_schema_history`, qui bloque le `migrate` suivant jusqu'à `repair` | Source secondaire | Test T14a |
| H-07 | `ADD ... NOT VALID` prend `SHARE ROW EXCLUSIVE` sans scan, `VALIDATE` prend `SHARE UPDATE EXCLUSIVE` avec scan | Documentation PostgreSQL, non mesurée | Mesure des verrous (requêtes `pg_locks`, Phase 2) |
| H-08 | La FK RI empêche l'insertion d'un paiement sur un devis en cours de suppression, et l'un des deux ordres échoue sans créer d'orphelin | Comportement attendu en READ COMMITTED, non prouvé | Test T12 |
| H-09 | Le volume de `payments` en production et la durée de VALIDATE sont inconnus | Pas de copie représentative disponible | Copie anonymisée ou volumétrie fournie par l'utilisateur |
| H-10 | Il n'existe aucun chemin applicatif de suppression de devis ou de paiement | Recherche par motifs, non exhaustive (F-12, F-13) | Revue complète des repositories, requêtes natives et jobs planifiés |

---

## 4. Décisions techniques proposées (non adoptées)

Toutes les lignes ci-dessous sont **[PROPOSITION]**. Aucune n'est adoptée.

| ID | Proposition | Justification | Alternatives écartées ou à conserver | Statut |
|---|---|---|---|---|
| DT-01 | Remplacement par DROP + ADD NOT VALID dans une seule transaction (V22 telle que rédigée) | Pas de scan, pas de coexistence visible de deux FK pour les autres sessions (sous réserve de H-01) | Séquence ADD temporaire / VALIDATE / DROP / RENAME : écartée tant que H-02 n'est pas tranchée. Elle ajoute des étapes et une coexistence potentielle | [PROPOSITION] |
| DT-02 | VALIDATE CONSTRAINT dans une migration séparée (numéro à décider), hors de la transaction du DROP | Le DROP garde ACCESS EXCLUSIVE jusqu'au COMMIT. Un VALIDATE dans la même transaction bloquerait toutes les lectures et écritures pendant le scan | Validation manuelle hors Flyway : possible mais non traçable | [PROPOSITION] |
| DT-03 | Conserver `SET LOCAL lock_timeout` dans V22 et tester son effet. Pour la migration VALIDATE, utiliser `SET` (portée session) si elle n'est pas transactionnelle, avec remise à zéro explicite | Voir section 7.2 : SET LOCAL n'a d'effet que dans un bloc de transaction | Supprimer le timeout : refusé, car un verrou en file bloquerait tout `payments` | [PROPOSITION] |
| DT-04 | Ne pas créer d'index sur `payments(quote_id)` avant mesure EXPLAIN. Si nécessaire, le créer avec CONCURRENTLY dans une migration non transactionnelle | Voir section 7.1 | Création immédiate dans V22 : écartée (verrou d'écriture sur `payments`) | [PROPOSITION] |
| DT-05 | Traiter le doublon V11 comme une tâche distincte | Hors périmètre V22, risque de confusion | Aucune action dans ce registre | [PROPOSITION] |
| DT-06 | Vérifier le nom réel de la FK `invoice_payments` par lecture seule en Phase 2 avant toute migration D5b | H-03 | Écrire le nom en dur dans le SQL sans vérification : refusé | [PROPOSITION] |
| DT-07 | Chaque test s'exécute dans un schéma dédié créé et supprimé par le test, sur `et_test_db` uniquement après autorisation. Aucun appel à `scripts/test-backend-postgres.ps1` | F-18, F-19 | Réinitialisation de la base : refusée | [PROPOSITION] |

---

## 5. Décisions métier

Ces décisions ne sont pas prises ici. Les recommandations techniques ne créent aucune politique financière.

### 5.1 D1 — Suppression d'un devis

- **Statut :** retenue par l'utilisateur pour préparation (message de préparation précédent) : un devis sans paiement peut être supprimé si aucune autre règle ou relation ne l'interdit ; un devis ayant un historique financier doit être conservé.
- **Question à formaliser :** qu'est-ce qu'un « devis sans historique financier » ? Un devis sans `payments`, mais avec `quote_events` (F-07) ou `quote_items` (F-06), est-il supprimable ?
- **Risque :** blocage injustifié (F-07) ou suppression d'une trace d'audit.
- **Options :** (a) suppression autorisée si aucun paiement ; (b) suppression interdite dès qu'un événement existe ; (c) suppression réservée aux brouillons.
- **Avantages / inconvénients :** (a) simple, mais en l'état elle n'est pas réalisable pour un devis ayant des événements, car `quote_events` bloque la suppression (F-07) ; une autorisation réelle exigerait de changer cette contrainte, ce qui est une autre décision ; les `quote_items` sont supprimés avec le devis (F-06). (b) protège l'audit, mais bloque la suppression de tout devis ayant été ouvert ; (c) ajoute un statut à gérer.
- **Recommandation technique :** distinguer explicitement la suppression logique (`soft delete`) de la suppression physique avant toute fonctionnalité de suppression. Sans fonctionnalité de suppression dans l'application (F-12), le risque reste un accès direct SQL.
- **Décision attendue :** [À VALIDER] quelle règle appliquer aux devis ayant des événements ou des items ?

### 5.2 D2 — Conservation des tentatives FAILED et CANCELLED

- **Question métier :** les paiements FAILED et CANCELLED doivent-ils empêcher la suppression de leur devis ?
- **Risque :** V22 protège tous les statuts. Un devis avec une tentative FAILED ne pourra donc plus être supprimé, même sans argent encaissé.
- **Options :**
  - (A) RESTRICT pour tous les statuts (V22 telle quelle) ;
  - (B) RESTRICT uniquement pour CAPTURED et REFUNDED. Une FK ne peut pas être partielle en PostgreSQL : il faudrait un trigger BEFORE DELETE sur `quotes`, plus complexe à tester ;
  - (C) CASCADE conservé pour FAILED et CANCELLED (supprime la trace de tentative).
- **Avantages / inconvénients :** (A) simple, conservatrice, sans trigger ; bloque des suppressions légitimes. (B) plus fine, mais demande un trigger à maintenir et tester. (C) permet le ménage, mais efface la trace d'une tentative de paiement.
- **Recommandation technique :** (A) pour la Phase 2, car c'est la seule option testable en SQL standard sans trigger. Ce choix ne vaut pas décision métier.
- **Décision attendue :** [À VALIDER] A, B ou C.

### 5.3 D3 — Suppression physique d'un paiement

- **Statut :** retenue par l'utilisateur pour préparation : la suppression physique d'un paiement CAPTURED ou REFUNDED est interdite par les opérations normales de l'application.
- **Point ouvert :** cette règle est applicative. V22 ne l'applique pas au niveau base (F-01 protège seulement la suppression du devis). Une suppression directe de `payments` cascade vers `payment_events` (F-03) et `invoice_payments` (F-04).
- **Risque :** perte de l'historique et du lien de facturation par une suppression administrative, un script ou une requête SQL manuelle.
- **Options :** (a) règle applicative seule ; (b) RESTRICT sur `payment_events` et `invoice_payments` (voir D5b) ; (c) trigger BEFORE DELETE interdisant CAPTURED et REFUNDED.
- **Recommandation technique :** (b) pour `invoice_payments` (voir 5.5). Pour `payment_events`, la décision dépend de la politique d'audit.
- **Décision attendue :** [À VALIDER] la protection doit-elle exister au niveau base pour les statuts CAPTURED et REFUNDED, ou la règle applicative suffit-elle ?

### 5.4 D4 — Durées de conservation

- **Question métier :** combien de temps les paiements, événements de paiement, factures et devis doivent-ils être conservés, et sous quelle forme (archive, purge) ?
- **Risque :** une purge future sans règle claire supprimerait des données comptables. Une conservation indéfinie a un coût de stockage et de données personnelles.
- **Options :** (a) conservation indéfinie ; (b) durée fixée par l'utilisateur sur avis de son expert-comptable ou juriste ; (c) archivage puis purge.
- **Constat :** aucune durée de conservation n'a été trouvée dans les documents consultés (recherche non exhaustive). Aucune durée n'est proposée ici.
- **Recommandation technique :** aucune purge physique avant décision. Toute purge future passera par une migration dédiée, auditée, qui contourne explicitement RESTRICT.
- **Décision attendue :** [À VALIDER] durées et mode de conservation. Ce point n'est pas bloquant pour la validation technique de V22.

### 5.5 D5b — Protection de `invoice_payments` (suite de D5)

- **Statut :** retenue par l'utilisateur pour préparation : analyser et préparer une protection RESTRICT pour empêcher la suppression d'un lien de facturation associé à un paiement historique. Pas d'intégration dans V22.
- **Fait :** `invoice_payments.payment_id` est en CASCADE sans nom explicite (F-04). Le nom réel n'est pas vérifié (H-03).
- **Usage applicatif :** l'application insère dans `invoice_payments` (F-14) et n'en supprime aucune ligne (F-13).
- **Point ouvert :** RESTRICT sur `payment_id` bloque la suppression d'un paiement lié, mais ne bloque pas la suppression d'une facture, qui cascade vers `invoice_payments` par `invoice_id` (F-05). La protection D5b est donc partielle et doit être décrite comme telle.
- **Options :** (a) CASCADE conservé ; (b) RESTRICT sur `payment_id` (proposition séparée, nom vérifié en Phase 2) ; (c) RESTRICT sur `invoice_id` aussi, à analyser avec les règles de facture.
- **Recommandation technique :** (b) comme migration séparée, après lecture du nom réel de la contrainte. Pas dans V22.
- **Décision attendue :** [À VALIDER] confirmation de (b), et règle de suppression des factures (c).

### 5.6 D6 — Remboursement et re-paiement

- **Question métier :** après un remboursement (REFUNDED), un devis peut-il recevoir un nouveau paiement CAPTURED ?
- **Constat :** V21 (index unique partiel sur CAPTURED) n'empêche qu'un seul CAPTURED par devis. Un devis REFUNDED ne bloque donc pas un nouveau CAPTURED. Ce point est un fait de conception de V21, non un test exécuté.
- **Risque :** double encaissement après remboursement, ou impossibilité de re-facturer un devis légitime.
- **Options :** (a) un devis remboursé est clos et ne peut plus être payé ; (b) un nouveau paiement est autorisé, avec une règle de montant ; (c) remboursement partiel autorisé, avec un modèle à définir.
- **Recommandation technique :** trancher avant la fonctionnalité de remboursement. V22 n'a pas d'effet direct sur ce point.
- **Décision attendue :** [À VALIDER] modèle de remboursement et règle de re-paiement.

---

## 6. Définitions unifiées des tests

### 6.1 Principe

Les identifiants canoniques sont **T1, T2, T12, T13, T14**, avec des sous-cas. Ces définitions remplacent celles des rapports pour les travaux futurs. Les rapports historiques ne sont pas réécrits (section 10).

**Aucun test n'a été exécuté.** Tous ont le statut **NOT TESTED**. Une simulation Java ou H2 n'est jamais une preuve. Chaque test s'exécute sur PostgreSQL réel isolé.

### 6.2 Correspondance avec les anciens identifiants

| Source | Identifiant ancien | Sens ancien | Équivalent canonique |
|---|---|---|---|
| Revue finale | T1 | DELETE quote avec paiement CAPTURED | T1 (sous-cas CAPTURED) |
| Revue finale | T2 | DELETE d'un paiement FAILED | Hors canon : suppression directe de paiement, voir D-01 |
| Phase 1.1 | T1 | Suppression d'un devis avec paiement | T1 |
| Phase 1.1 | T2 | Suppression d'un devis sans paiement | T2 |
| Phase 1.1 | T12 | Concurrence devis / paiement | T12 |
| Phase 1.1 | T13 | Rollback du remplacement de FK | T13 |
| Phase 1.1 | T14 | Interruption et reprise | T14 |
| Revue finale | T3 à T9, T11 | Non-régression et vérifications | Voir section 6.7 |
| Revue finale | T10 | Rollback si ADD échoue | T13a |
| Revue finale | T12 | Coexistence des deux FK | Hors canon : H-02 |
| Revue finale | T13 | Verrou pendant le DROP | T13b |
| Revue finale | T14 | Nom réel de la FK invoice_payments | DT-06 (lecture seule) |
| Phase 1.2 | P-01 à P-14 | Spécification documentaire | Voir colonne « Équivalent » en 6.8 |

### 6.3 T1 — Suppression d'un devis ayant des paiements

**Objectif :** prouver que V22 empêche la suppression d'un devis tant qu'un paiement le référence, quel que soit son statut, et que le paiement reste intact.

**Risque couvert :** perte de l'historique financier par CASCADE (F-01).

**Préconditions :**
- Schéma isolé construit par V1 à V20 puis V22 (ou V1 à V19 pour l'état avant).
- Un client, un véhicule, une réservation, un devis (le devis exige `booking_id`, F-08).
- Aucun `quote_events` ni `quote_items` pour isoler le test de F-06 et F-07.

**Données dédiées :** un devis par statut de paiement, 6 devis au total, portant un identifiant préfixé `t1-` pour le nettoyage.

**Connexions :** 1.

**Séquence :** pour chaque statut S de {PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED} : `BEGIN` ; `DELETE FROM quotes WHERE id = devis_S` ; observer ; `ROLLBACK`.

**Résultat attendu sous V22 :**

| Statut | Résultat attendu | Statut de la décision |
|---|---|---|
| CAPTURED | Refus, SQLSTATE 23503 sur `fk_payments_quote` | Adopté techniquement : objectif principal de V22 |
| REFUNDED | Refus, SQLSTATE 23503 | Adopté techniquement, lié à D6 pour le re-paiement |
| FAILED | Refus, SQLSTATE 23503 | Dépend de D2 (option A) |
| CANCELLED | Refus, SQLSTATE 23503 | Dépend de D2 (option A) |
| PENDING | Refus, SQLSTATE 23503 | À valider : paiement non finalisé |
| AUTHORIZED | Refus, SQLSTATE 23503 | À valider : paiement autorisé non capturé |

**Résultat d'échec attendu avant V22 :** suppression réussie pour les six statuts, avec suppression de `payments` (CASCADE). C'est la preuve du défaut.

**Vérification d'intégrité :** après chaque DELETE, le nombre de lignes de `payments` pour le devis reste égal à 1, et son `status` n'a pas changé.

**Nettoyage :** `ROLLBACK` ; pas de données persistantes. Suppression du schéma de test en fin de run.

**Preuves :** SQLSTATE et message exact, `pg_constraint` (`confdeltype = 'r'`), compte de paiements avant et après.

**Vérification de l'objectif de V22 :** V22 protège les paiements contre la suppression de leur devis. Elle ne protège pas les paiements contre une suppression directe de `payments` (voir T8 / D-01).

### 6.4 T2 — Suppression d'un devis sans paiement

**Objectif :** distinguer le comportement attendu de V22 (aucun effet sans paiement) du comportement des autres contraintes.

**Sous-cas :**

| Sous-cas | Données | Attendu | Responsable du résultat |
|---|---|---|---|
| T2a | Devis sans paiement, sans items, sans événement | Suppression réussie | V22 ne bloque pas (non-régression) |
| T2b | Devis sans paiement, avec `quote_items` | Suppression réussie, items supprimés | CASCADE de V1 (F-06), non modifié par V22 |
| T2c | Devis sans paiement, avec `quote_events` | Refus, SQLSTATE 23503 sur `quote_events` | NO ACTION de V5 (F-07), non modifié par V22 |

**Point de décision :** T2c est un blocage hors V22. Il est peut-être souhaitable (protection de l'audit), mais il doit être décidé avant toute fonctionnalité de suppression (D1). Le test doit vérifier le nom de la contrainte qui échoue, pas seulement l'échec.

**Connexions :** 1. **Nettoyage :** `ROLLBACK`.

**Preuves :** SQLSTATE, nom de la contrainte dans le message, compte des lignes enfants avant et après.

### 6.5 T12 — Concurrence entre suppression du devis et insertion d'un paiement

**Objectif :** prouver qu'aucune configuration concurrente ne produit un paiement orphelin ni un devis supprimé avec paiement.

**Risque couvert :** H-08. Une insertion concurrente pendant la suppression crée un paiement sans devis, ou un devis supprimé avec un paiement.

**Préconditions :** schéma isolé avec V22 appliquée ; un devis `t12-quote` existant ; `lock_timeout = '5s'` dans les deux sessions.

**Connexions :** 2 indépendantes (A et B), plus 1 observateur pour `pg_locks` et `pg_blocking_pids`. Total : 3.

**Séquence T12a (suppression d'abord) :**
1. A : `BEGIN` ; `DELETE FROM quotes WHERE id = t12-quote` (sans COMMIT).
2. B : `BEGIN` ; `INSERT INTO payments (...) VALUES (... quote_id = t12-quote ...)`. Attendu : B attend.
3. Observateur : confirmer que B est bloquée par A (`pg_blocking_pids`).
4. A : `COMMIT`. Attendu : A réussit si aucun paiement n'existe au moment de son contrôle RI, et B échoue ensuite en 23503.
5. B : `ROLLBACK` si nécessaire.

**Séquence T12b (insertion d'abord) :**
1. B : `BEGIN` ; `INSERT` du paiement (sans COMMIT).
2. A : `BEGIN` ; `DELETE FROM quotes WHERE id = t12-quote`. Attendu : A attend B.
3. B : `COMMIT`.
4. A : reçoit 23503 (paiement visible au contrôle RESTRICT), ou réussit selon l'ordre réel. Dans les deux cas, l'invariant doit tenir.

**Résultats attendus (invariants, pas un ordre supposé) :**
- Jamais de paiement dont le devis n'existe pas.
- Jamais de devis supprimé avec un paiement committé.
- Un seul des deux états est possible : (devis présent et paiement présent) ou (devis absent et aucun paiement). Le test échoue si un autre état apparaît.

**Résultat d'échec attendu :** ni l'un ni l'autre n'est une preuve d'échec. Un échec serait un état orphelin ou un DELETE réussi avec un paiement committé.

**Vérification d'intégrité :** requête d'orphelins après chaque séquence : `SELECT count(*) FROM payments p WHERE NOT EXISTS (SELECT 1 FROM quotes q WHERE q.id = p.quote_id)` (doit être 0).

**Nettoyage :** `ROLLBACK` dans les deux sessions ; `DROP` du schéma de test.

**Preuves :** trace de `pg_blocking_pids`, SQLSTATE final de chaque session, requête d'orphelins, état final des lignes.

**Hors canon :** le cas « coexistence de deux FK » (ancienne revue T12) ne fait pas partie de T12. Il ne sera exécuté que si la séquence temporaire est reconsidérée (H-02, DT-01).

### 6.6 T13 — Rollback du remplacement de FK

**Objectif :** démontrer qu'une migration interrompue avant COMMIT ne laisse aucun état partiel.

**Sous-cas :**

| Sous-cas | Mécanisme | Attendu |
|---|---|---|
| T13a | Dans une transaction : exécuter DROP et ADD de V22, puis une instruction forcée en erreur (ex. `SELECT 1/0`), puis `ROLLBACK` | `fk_payments_quote` existe une seule fois, `confdeltype = 'c'`, `convalidated = true` |
| T13b | Session B : `BEGIN` ; `SELECT 1 FROM payments LIMIT 1` (ACCESS SHARE, sans COMMIT). Session A : exécuter V22 avec `lock_timeout = '5s'` | Session A échoue en 55P03 après environ 5 s ; annulation automatique ; `confdeltype = 'c'` ; B fait ensuite `ROLLBACK` |

**Note :** la revue finale utilisait `SELECT ... FOR UPDATE`. Les deux verrous entrent en conflit avec ACCESS EXCLUSIVE. Le sous-cas canonique est `SELECT 1 FROM payments LIMIT 1` pour éviter de confondre verrou de ligne et verrou de table. [HYPOTHÈSE H-05 et H-07 pour la durée et le verrou exact.]

**Précondition :** schéma isolé construit par V1 à V20 ; la FK existe en CASCADE.

**Connexions :** T13a : 1. T13b : 2.

**Preuves :** sortie SQL avec SQLSTATE, durée mesurée, `pg_constraint` avant et après, `pg_locks` pendant l'attente.

**Nettoyage :** `ROLLBACK`, puis `DROP` du schéma de test. L'instruction forcée de T13a est un artefact de test et ne doit jamais figurer dans une migration.

### 6.7 T14 — Interruption et reprise

Les quatre situations ne sont pas équivalentes. Chacune a une preuve distincte.

| Sous-cas | Situation | Comportement attendu | Statut |
|---|---|---|---|
| T14a | Échec SQL dans la transaction (ex. T13b) capturé par Flyway | Rollback PostgreSQL ; ligne `success=false` dans `flyway_schema_history` (H-06) ; FK inchangée ; `migrate` suivant bloqué | [HYPOTHÈSE] |
| T14b | Arrêt du processus Flyway/JVM pendant la transaction (kill) | Connexion fermée ; rollback par PostgreSQL ; aucune ligne V22 ; FK inchangée ; relance réussie | [HYPOTHÈSE] |
| T14c | `pg_terminate_backend` sur la seule session de migration créée par le test | Même résultat que T14b | [HYPOTHÈSE] |
| T14d | Contrôle Flyway avant exécution (checksum modifié sur une copie isolée) | Refus de `validate`, aucune instruction SQL exécutée | [HYPOTHÈSE] |
| T14e | Reprise après T14a | Nécessite `flyway repair`, qui n'est pas autorisé. Résultat : BLOCKED tant que non autorisé | BLOCKED |

**Hors canon :** un échec de migration hors transaction n'est pas applicable à la V22 actuelle (F-10, H-01). Il ne sera testé que si V22 devient non transactionnelle, ce qui n'est pas proposé.

**Précondition pour T14b et T14c :** le test identifie le PID de sa propre session de migration. Il ne termine jamais une session qu'il n'a pas créée.

**Résultat attendu de reprise (T14b, T14c) :** `flyway migrate` applique V22 une seule fois, `pg_constraint` indique `confdeltype = 'r'`, `convalidated = false` jusqu'à VALIDATE.

**Preuves :** `flyway_schema_history` avant et après, `pg_constraint` avant et après, sortie Flyway, code de sortie.

### 6.8 Tests complémentaires (non canoniques, rattachés)

| ID | Objectif | Rattachement |
|---|---|---|
| C-01 | Mise à jour du statut CAPTURED vers REFUNDED reste permise | Non-régression T4 (revue finale) |
| C-02 | `confdeltype` et `convalidated` après V22 | Vérification de T1 et T13 (ancien T5) |
| C-03 | VALIDATE sur copie : orphelins = 0 | DT-02, ancien T6 |
| C-04 | Écriture concurrente pendant VALIDATE | DT-02, ancien T7, équivalent P-14 |
| D-01 | Suppression directe d'un paiement cascade vers `payment_events` et `invoice_payments` | Risque D3/D5b, ancien T8, équivalent P-04. Test de constat, pas de correction |
| F-01 | V1 à V22 sur base vide, puis `ddl-auto: validate` | Ancien T9, équivalent P-12 |
| R-01 | Non-régression : `PaymentQuoteCapturePostgresIT`, `BookingLockOrderPostgresIT`, `BookingAuditAtomicityPostgresIT` | Ancien T11. Ne jamais lancer `MigrationWithExistingDataIT` (F-19) |

### 6.9 Correspondance des identifiants P-xx

| P-xx | Équivalent canonique |
|---|---|
| P-01 | T1 (CAPTURED) |
| P-02 | T1 (autres statuts) |
| P-03 | T2a |
| P-04 | D-01 |
| P-05 | DT-06, lecture seule |
| P-06 | T12a / T12b |
| P-07 | Hors V22 : rappel de V21, à traiter dans le référentiel V21 |
| P-08 | T13b |
| P-09 | T13a |
| P-10 | T14b / T14c |
| P-11 | T14a |
| P-12 | F-01 |
| P-13 | Contrôle de packaging, à rattacher à la Phase 2 |
| P-14 | C-04 |

---

## 7. Analyses techniques

### 7.1 Index sur `payments.quote_id`

- **Constat :** aucun index (F-09). Une suppression de devis déclenche une vérification RI sur `payments`. Sans index, cette vérification peut parcourir la table entière.
- **Nature du sujet :** performance et verrouillage, pas intégrité. L'intégrité est assurée par la FK, indépendamment de l'index.
- **Rôle de V21 :** l'index partiel `WHERE status = 'CAPTURED'` ne sert pas à la vérification RI, car la requête RI ne filtre pas sur `status`.
- **Coût :** un index supplémentaire ralentit les écritures sur `payments` et occupe de l'espace.
- **Méthode de mesure (Phase 2, sans création) :**
  1. `EXPLAIN (COSTS, BUFFERS)` d'une requête équivalente à la vérification RI (`SELECT 1 FROM payments WHERE quote_id = $1 FOR KEY SHARE`) sur copie représentative, avant tout index.
  2. Même `EXPLAIN` dans une transaction annulée, avec un index de test créé dans le schéma de test uniquement, puis `ROLLBACK`.
  3. Comparer `Seq Scan` et `Index Scan`, le nombre de buffers, et la durée sur volume réaliste.
  4. `EXPLAIN ANALYZE` seulement sur un schéma de test, jamais sur une requête destructive.
- **Décision :** [À VALIDER] si la mesure justifie un index. Recommandation : mesurer d'abord (DT-04). Aucun index n'est créé dans ce registre.

### 7.2 `lock_timeout` : SET LOCAL et SET SESSION

| Forme | Portée | Effet dans V22 |
|---|---|---|
| `SET LOCAL lock_timeout = '5s'` | Jusqu'à la fin de la transaction courante | Effet seulement si une transaction est ouverte. Flyway ouvre une transaction par défaut (H-01) |
| `SET lock_timeout = '5s'` | Jusqu'à la fin de la session ou un RESET | Effet dans toutes les conditions, mais doit être remis à zéro explicitement |
| `SET LOCAL` hors transaction | En autocommit, la valeur s'annule à la fin de l'instruction | Sans effet sur les instructions suivantes. PostgreSQL émet un avertissement (documentation PostgreSQL) |

**Vérification de la configuration :** aucune clé de configuration Flyway ne désactive la transaction (F-16). Le comportement transactionnel est donc supposé (H-01), non prouvé.

**Conclusion :** l'efficacité du timeout dans V22 n'est pas prouvée. Elle doit être testée par T13b, qui observe SQLSTATE 55P03 et la durée. **Ne pas affirmer que le timeout fonctionne sans cette preuve.**

**Recommandation :** conserver le timeout (DT-03). Ne pas le supprimer.

### 7.3 Doublon V11

Tâche distincte (DT-05). Aucune suppression, aucun déplacement, aucune modification dans ce registre. La copie inactive dans `backend/src/main/resources/db/migration/` est exclue du packaging (F-20). Une suppression nécessite une autorisation distincte.

### 7.4 FK `invoice_payments`

- Nom non vérifié (F-04, H-03). Ne pas l'écrire en dur dans une migration.
- Vérification prévue en Phase 2, par lecture seule de `pg_constraint` sur `invoice_payments` avec `conrelid` et `confrelid`.
- Le résultat déterminera le nom utilisé dans la proposition D5b séparée.

---

## 8. Commandes préparées (NON EXÉCUTÉES)

Aucune de ces commandes n'a été exécutée. Elles requièrent une autorisation explicite de Phase 2, avec cible, périmètre et limites.

```text
# Lecture seule (Phase 2 uniquement, sur et_test_db, sans afficher le mot de passe)
SHOW server_version;
SELECT conname, confdeltype, convalidated FROM pg_constraint WHERE conrelid = 'payments'::regclass;
SELECT conname, confdeltype FROM pg_constraint WHERE conrelid = 'invoice_payments'::regclass AND contype = 'f';
SELECT indexname FROM pg_indexes WHERE tablename = 'payments';

# Flyway (vérification de version, lecture)
backend\mvnw.cmd -o dependency:tree -Dincludes=org.flywaydb   (JDK 21 requis)

# Tests ciblés (à confirmer : profil et URL pointant sur un schéma de test isolé)
backend\mvnw.cmd -o "-Dtest=PaymentQuoteCapturePostgresIT,BookingLockOrderPostgresIT,BookingAuditAtomicityPostgresIT" test
```

**Commandes interdites :**
- `scripts/test-backend-postgres.ps1` (F-18) ;
- `MigrationWithExistingDataIT` (F-19) ;
- `flyway repair`, `flyway clean`, `flyway migrate` hors schéma de test ;
- tout `DROP DATABASE`, `DROP SCHEMA public`, `TRUNCATE`, `DELETE` sans clause isolée.

---

## 9. Critères de passage à la Phase 2

| # | Critère | État | Justification |
|---|---|---|---|
| 1 | Référentiel de tests unique | **PASS documentaire** | Ce registre (section 6). Adoption par l'utilisateur : PENDING DECISION |
| 2 | Définitions T1, T2, T12, T13, T14 stabilisées | **PENDING DECISION** | Les définitions sont proposées, pas adoptées. Choix requis sur T2c, D2, et la coexistence (H-02) |
| 3 | Commandes de test préparées | **PASS** (section 8) | Non exécutées. Profil et URL à confirmer |
| 4 | Données de test isolables | **NOT TESTED** | Création d'un schéma dédié non vérifiée sur `et_test_db`. Aucun accès effectué |
| 5 | Nettoyage sans impact sur les données existantes | **PASS documentaire / NOT TESTED** | Règle DT-07 : suppression du seul schéma créé par le test. À prouver |
| 6 | Périmètre d'autorisation explicite | **PENDING DECISION** | Rédaction par l'utilisateur : cible, tests, limites, interdits |
| 7 | Absence de dépendance au script destructif | **PASS** | Aucune commande de section 8 n'utilise le script. Exclusion de `MigrationWithExistingDataIT` documentée |

### 9.1 Décisions bloquantes pour la Phase 2

1. Adopter les définitions de la section 6, ou les modifier.
2. Décider T2c : `quote_events` bloque-t-il la suppression d'un devis ?
3. Choisir l'option D2 (A, B ou C) pour les statuts FAILED et CANCELLED, car elle conditionne le résultat attendu de T1.
4. Fournir le périmètre d'autorisation de la Phase 2.

### 9.2 Décisions non bloquantes pour la validation technique

- D4 : durées de conservation (la validation technique de RESTRICT n'en dépend pas).
- D5b : confirmation de la protection `invoice_payments` (migration séparée, après la Phase 2).
- D6 : modèle de remboursement (la fonctionnalité n'existe pas encore).
- Index sur `payments.quote_id` : dépend de la mesure.
- Doublon V11 : tâche distincte.

Ces décisions doivent rester ouvertes et identifiées. Elles ne doivent pas être présentées comme adoptées.

---

## 10. Statut des conclusions historiques

Les rapports précédents ne sont pas réécrits. Leurs conclusions restent des constats datés :

| Document | Conclusion | Statut après Phase 1.3 |
|---|---|---|
| `v22-validation-report.md` Phase 1 | NON VALIDÉE | Constat historique, maintenu |
| `v22-validation-report.md` Phase 1.1 | NOT READY FOR PHASE 2 | Constat historique, maintenu |
| `v22-validation-report.md` Phase 1.2 | NOT READY FOR PHASE 2 | Constat historique, maintenu |
| `v22-final-review.md` | APPROUVÉE AVEC RÉSERVES | Maintenu. Les définitions de tests de ce document sont remplacées par la section 6 de ce registre pour les travaux futurs |
| Définitions T1 à T14 des rapports | Contradictoires | Remplacées par la section 6, après validation de l'utilisateur |

---

## 11. Fichiers concernés par cette phase

- Créé : `docs/architecture/v22-decision-register.md` (ce document).
- Mis à jour : `docs/architecture/v22-validation-report.md` (section Phase 1.3 ajoutée en fin de fichier, sans réécrire les sections précédentes).
- Non modifiés : V21, V22, toutes les migrations V1 à V20, V11 (les deux copies), le code applicatif, les scripts, la base de données.

---

## 12. Verdict

**NOT READY FOR PHASE 2.**

Passage à **READY FOR PHASE 2** possible si, et seulement si :
1. l'utilisateur adopte les définitions de la section 6 (ou les corrige) ;
2. l'utilisateur tranche T2c et D2 ;
3. l'utilisateur fournit un périmètre d'autorisation explicite (cible, schéma isolé, tests, interdits).

Ce verdict ne signifie pas que V22 est validée ni prête pour la production. Il signifie seulement que la préparation documentaire est suffisante pour demander une autorisation de test.

**Aucune connexion PostgreSQL, aucun SQL, aucun test, aucune commande Flyway n'a été exécuté.**
