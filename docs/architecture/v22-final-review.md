# Revue finale de la proposition V22 — `payments.quote_id` ON DELETE RESTRICT

**Statut :** revue en lecture seule. Aucun fichier modifié hormis ce livrable, aucune migration créée, aucune requête SQL exécutée, aucune base touchée, aucun commit, push ni déploiement.

**Fichier revu :** [V22__payments_quote_fk_restrict.sql](C:/Users/aaziza/Desktop/test/database/proposed/V22__payments_quote_fk_restrict.sql) (non applicable, hors chemin Flyway, non déplacé).

**Contenu actuel de la proposition :**

```sql
ALTER TABLE payments DROP CONSTRAINT fk_payments_quote;
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_quote
    FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE RESTRICT NOT VALID;
ALTER TABLE payments VALIDATE CONSTRAINT fk_payments_quote;
```

---

## Verdict

# APPROUVÉE AVEC RÉSERVES

Le SQL cible la bonne contrainte, ne heurte aucune migration active connue et réduit le risque identifié (suppression d'un devis entraînant la suppression de ses paiements CAPTURED). Il ne doit toutefois pas être appliqué en l'état pour deux raisons :

1. **Verrou à mesurer et probablement à réduire** : Flyway exécute la migration dans une transaction unique. `DROP CONSTRAINT` prend un verrou `ACCESS EXCLUSIVE` sur `payments` qui n'est libéré qu'au `COMMIT`, donc il couvre aussi le `VALIDATE CONSTRAINT`, qui lit toute la table. Pendant ce scan, lectures et écritures sur `payments` sont bloquées. L'en-tête de V22 ne mentionne aucun verrou : ce point n'est donc pas documenté, alors qu'il conditionne la stratégie de production.
2. **Décisions métier D1 et D5 non tranchées** (voir plus bas), et aucun test PostgreSQL prouvant le comportement avant/après.

---

## 1. Ciblage de `fk_payments_quote`

**Confirmé.** La contrainte n'est définie que dans [V6__payments.sql](C:/Users/aaziza/Desktop/test/database/migrations/V6__payments.sql) ligne 21 :

```sql
ALTER TABLE payments ADD CONSTRAINT fk_payments_quote FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE CASCADE;
```

Recherche sur `database/migrations` et `backend/src` : aucune autre définition de `fk_payments`, aucune migration V7 à V20 ne modifie les FK de `payments` (V17 modifie seulement la nullabilité de `refunded_amount`). Le nom est donc stable. Le `DROP CONSTRAINT` ne peut échouer que si la base réelle a été modifiée à la main, ce qui est un point à vérifier sur une copie (voir §10).

## 2. Compatibilité V1 à V20 et V21

**Confirmé.** Les migrations actives sont V1 à V20 (plus V11 et les fichiers `_DISABLED_*` que Flyway ignore, car préfixés par `_`).

- V6 renomme l'ancienne table en `legacy_payments` et crée la nouvelle `payments` : la FK V22 cible la table active.
- V7 renomme `invoices` en `legacy_invoices` et crée `invoice_payments` : aucune interaction avec `fk_payments_quote`.
- **V21** (`CREATE UNIQUE INDEX ... ON payments (quote_id) WHERE status = 'CAPTURED'`) n'interagit pas avec la FK : un index unique partiel ne bloque pas la validation d'une clé étrangère. Les deux migrations peuvent être appliquées dans n'importe quel ordre. Par convention Flyway, V21 doit passer avant V22 pour que la numérotation reste monotone.

## 3. Dépendances pouvant empêcher l'application

**Aucun blocage identifié dans le code.** Points de vigilance :

- `VALIDATE CONSTRAINT` échouera si un paiement a un `quote_id` orphelin. Une FK existait déjà, donc un orphelin ne peut venir que d'une modification manuelle hors contraintes ou d'une désactivation de contrainte. À vérifier par requête en lecture seule sur une copie : `SELECT count(*) FROM payments p WHERE NOT EXISTS (SELECT 1 FROM quotes q WHERE q.id = p.quote_id);` (attendu : 0).
- Aucune vue, trigger ou fonction dépendant de la FK n'a été trouvé dans les migrations actives.
- Le nom par défaut de la FK `invoice_payments` n'est pas nécessaire à V22 (elle n'est pas modifiée), mais devra être vérifié avant toute future migration la touchant (voir §4).

## 4. Relations `payments`, `quotes`, `payment_events`, `invoice_payments`

| Relation | Source | ON DELETE | Effet d'une suppression de quote |
|---|---|---|---|
| `payments.quote_id → quotes.id` (`fk_payments_quote`) | V6 l.21 | CASCADE → **RESTRICT en V22** | Bloqué si paiement existant |
| `payments.booking_id → bookings.id` (`fk_payments_booking`) | V6 l.22 | SET NULL | Hors chemin : un booking référencé par un quote ne peut pas être supprimé (`quotes.booking_id` RESTRICT, V5 l.7) |
| `payment_events.payment_id → payments.id` (`fk_payment_events_payment`) | V6 l.31 | CASCADE | Inchangé, mais inaccessible si V22 bloque la suppression du quote |
| `invoice_payments.payment_id → payments.id` (FK inline) | V7 l.46 | CASCADE | Inchangé : `DELETE FROM payments` supprime les liens de facture |
| `invoices` (`quote_id`, `booking_id`, `customer_id`) | V7 | **aucune FK** | Pas de cascade, pas de blocage |
| `quote_lines.quote_id → quotes.id` | V1 (table `quote_items`, renommée en `quote_lines` par V5) | CASCADE | Les lignes de devis sont supprimées, pas les paiements |

**Point à corriger dans la documentation :** V22 ne protège que le chemin `quotes → payments`. Il laisse deux chemins de destruction de l'historique :

- `DELETE FROM payments` direct (S5 de la revue précédente) : cascade vers `payment_events` et `invoice_payments`. Non bloqué par V22, car la FK n'est pas sur `payments` elle-même. Aucun chemin applicatif ne le fait (voir §6), mais une requête manuelle le permet.
- Les `payment_events` et `invoice_payments` ne sont jamais la cible d'une suppression applicative. Leur RESTRICT est le sujet de D5.

## 5. Conséquences d'une suppression directe d'un paiement

Non couvert par V22. Si un administrateur ou une requête manuelle exécute `DELETE FROM payments WHERE id = ...` :

- la ligne de paiement disparaît, ainsi que ses événements (`payment_events`) et ses liens de facture (`invoice_payments`) ;
- la facture perd la trace du paiement dans `invoice_payments`, ce qui peut rendre le total facturé incohérent avec les encaissements ;
- la contrainte V21 (unicité CAPTURED) n'empêche pas cette suppression.

Décision D3 (suppression administrative de paiement) : à trancher. Recommandation : aucune suppression physique de paiement en production ; si nécessaire, un statut `CANCELLED` ou un événement d'annulation, jamais un `DELETE`.

## 6. Usages réels dans les repositories et services

Recherche sur `backend/src` (insensible à la casse) :

- `delete`, `deleteAll`, `deleteById`, `DELETE FROM` sur `payments`, `quotes`, `payment_events`, `invoice_payments`, `quote_lines` : **aucune occurrence applicative**.
- `TRUNCATE` / `CASCADE DELETE` : aucune occurrence.
- Seule occurrence de `REFUNDED` hors enum : `backend/src/test/resources/migration-scenarios/v16-ambiguous-refund.sql` (fichier de scénario de test).
- [QuoteEntity.java](C:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/quote/QuoteEntity.java) possède une collection `@OneToMany` de lignes avec `CASCADE` et `orphanRemoval` (ligne 36), mais pas de collection de paiements : JPA ne cascade pas vers `payments`.
- [PaymentEntity.java](C:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/payment/PaymentEntity.java) : `@ManyToOne` vers `quote` et `booking`, sans collection inverse.
- [PaymentService.java](C:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/payment/PaymentService.java) : création de paiements et d'événements uniquement.

**Conséquence :** RESTRICT n'empêchera aucun flux applicatif actuel. Il ne bloque que des suppressions manuelles ou futures. C'est précisément le but.

**Réserve :** l'absence de suppression applicative a été vérifiée par recherche textuelle. Un `deleteAll` dans des scripts hors `backend/src` (ex. `scripts/`) n'a pas été exhaustivement revu ; à faire avant application.

## 7. Cohérence avec le cycle de vie métier

Cohérent. Le cycle de vie documenté ([account-deletion-lifecycle-review.md](C:/Users/aaziza/Desktop/test/docs/architecture/account-deletion-lifecycle-review.md)) ne supprime pas de comptes ; il désactive (`users.enabled=false`). Un devis ayant donné lieu à un paiement doit rester comme trace historique : son statut évolue, il n'est pas supprimé. RESTRICT traduit ce principe au niveau base.

**Conséquence à assumer :** un devis avec un paiement FAILED ou CANCELLED ne pourra plus être supprimé non plus (RESTRICT vaut pour tous les statuts). C'est le choix de D2 (conservation des tentatives). Si D2 est tranché autrement, la proposition doit changer.

## 8. Implications des décisions D1 à D6

| Décision | Question | Impact sur V22 |
|---|---|---|
| **D1** | Un devis peut-il être supprimé s'il n'a aucun paiement ? | Sans objet pour V22 (RESTRICT n'agit que s'il existe un paiement). **À trancher pour la politique de suppression des devis** (ex. uniquement brouillons). |
| **D2** | Conserver les tentatives FAILED / CANCELLED ? | V22 les protège toutes. Si D2 = « suppression autorisée des tentatives échouées », V22 doit être remplacée par une règle plus fine (trigger ou contrainte partielle), non décrite ici. |
| **D3** | Suppression administrative de paiement ? | Non couverte par V22 (§5). Recommandation : interdire. |
| **D4** | Durées de conservation ? | Sans effet sur V22 ; une purge future devra contourner RESTRICT explicitement (migration dédiée, avec audit). |
| **D5** | RESTRICT sur `payment_events` et `invoice_payments` ? | Non inclus dans V22. Si oui, deux migrations supplémentaires, avec vérification préalable du nom réel de la FK `invoice_payments` (`invoice_payments_payment_id_fkey` attendu, non vérifié). **Recommandation : oui pour `invoice_payments`**, car la perte de ce lien fausse le total facturé. |
| **D6** | Modèle de remboursement et règle de re-paiement ? | Sans effet direct sur la FK. Mais V21 ne couvre pas `REFUNDED` : un devis remboursé pourrait être repayé. À trancher avant la fonctionnalité de remboursement. |

## 9. Compatibilité avec les tests PostgreSQL prévus

Les tests doivent être exécutés sur PostgreSQL réel (`et_test_db`, 127.0.0.1:15432). H2 ne suffit pas :

- le profil `test` utilise `ddl-auto: create-drop` et `flyway.enabled: false` ([application-test.yml](C:/Users/aaziza/Desktop/test/backend/src/main/resources/application-test.yml) lignes 13 et 17) : le schéma H2 n'est pas construit par les migrations, donc il ne prouve rien sur la FK réelle ;
- le profil `dev` / prod utilise `ddl-auto: validate` et Flyway ([application.yml](C:/Users/aaziza/Desktop/test/backend/src/main/resources/application.yml) lignes 13 et 17) ;
- `PaymentQuoteCapturePostgresIT` et `BookingLockOrderPostgresIT` ont déjà été exécutés sur PostgreSQL réel avec succès (T-A02, T-A03, T-A04) ; un nouveau test `PaymentHistoryIntegrityPostgresIT` est à créer.

**Tests à prouver (T1 à T11 de la revue précédente) :**

| Test | Vérification | Attendu avant V22 | Attendu après V22 |
|---|---|---|---|
| T1 | `DELETE` d'un quote avec un paiement CAPTURED | Réussit (paiement supprimé) : **échec de la protection** | Échoue, erreur de FK (SQLSTATE 23503) |
| T2 | `DELETE` d'un quote avec un paiement FAILED | Réussit | Échoue |
| T3 | `DELETE` d'un quote sans paiement | Réussit | Réussit (non-régression) |
| T4 | Mise à jour du statut d'un paiement (CAPTURED → REFUNDED) | Réussit | Réussit (non-régression) |
| T5 | Vérification de la définition : `SELECT confdeltype FROM pg_constraint WHERE conname = 'fk_payments_quote'` | `c` | `r` |
| T6 | `VALIDATE` sur données existantes (copie) | Sans objet | Réussit, nombre d'orphelins = 0 |
| T7 | Écriture concurrente pendant `VALIDATE` (session parallèle) | Sans objet | Mesurer le blocage (voir §10) |
| T8 | `DELETE FROM payments` direct : `payment_events` et `invoice_payments` supprimés | Cascade | Cascade (documente le risque S5 tant que D3/D5 non tranchées) |
| T9 | Migration V1 à V22 sur base vide, puis `ddl-auto: validate` | Passe | Passe |
| T10 | Rollback : si `ADD` échoue, l'ancienne FK reste et la migration est annulée | Sans objet | Vérifier l'absence d'état partiel |
| T11 | Non-régression : `PaymentQuoteCapturePostgresIT`, `BookingLockOrderPostgresIT`, `BookingAuditAtomicityPostgresIT` | Verts | Verts |
| T12 | Coexistence : deux FK sur `payments.quote_id` (CASCADE et RESTRICT), puis `DELETE` d'un quote avec un paiement CAPTURED | Non testé ici, à prouver : résultat dépend de l'ordre des triggers | Sans objet : la séquence retenue n'a jamais deux FK visibles. Test servant à prouver le risque de la séquence rejetée |
| T13 | Mesure du verrou : transaction ouverte sur `payments` (`BEGIN; SELECT ... FOR UPDATE`) pendant le `DROP`, avec `lock_timeout` | Sans objet | Le `DROP` échoue après 5 s et l'ancienne FK reste en place (aucun état partiel) |
| T14 | `pg_constraint` sur `invoice_payments` : nom réel de la FK `payment_id` | Lecture seule | Nom confirmé avant toute migration D5 |

**Pré-requis :** T1 et T2 doivent échouer avant V22 (preuve que le risque existe) puis réussir après (preuve de la correction). Une seule exécution verte après ne suffit pas.

## 10. Risque de verrouillage et stratégie de production

**Mécanisme réel (PostgreSQL) :**

- `DROP CONSTRAINT` : verrou `ACCESS EXCLUSIVE` sur `payments` (bloque lectures et écritures).
- `ADD CONSTRAINT ... NOT VALID` : verrou `SHARE ROW EXCLUSIVE` sur `payments` et `quotes`, sans scan (bloque les écritures, pas les lectures).
- `VALIDATE CONSTRAINT` : verrou `SHARE UPDATE EXCLUSIVE` (ne bloque pas les écritures), mais scan complet de `payments`.

**Problème dans la proposition :** Flyway enveloppe la migration dans une transaction. Le verrou `ACCESS EXCLUSIVE` pris par `DROP` est conservé jusqu'au `COMMIT`, donc pendant le `VALIDATE`. Pour une table de paiements volumineuse, cela bloque toute l'application pendant la durée du scan. Le commentaire d'en-tête doit être corrigé.

**Séquence demandée (temporaire, renommage) : rejetée.** Ma version précédente affirmait qu'aucune fenêtre sans protection n'existe pendant la coexistence des deux FK. C'est faux. Lors d'une suppression de devis, PostgreSQL exécute les triggers RI dans l'ordre de leur nom, donc selon leur OID. Si la cascade (ancienne FK) s'exécute avant le contrôle RESTRICT (nouvelle FK), elle supprime les paiements, le contrôle ne trouve plus rien et la suppression réussit. Le résultat dépend donc de l'ordre des triggers, ce qui n'est pas une garantie. Ce comportement doit être prouvé par le test T12 (section Plan de tests) avant de pouvoir être écarté.

**Séquence retenue (dans le fichier V22) :**

```sql
-- Transaction unique (Flyway) : aucune coexistence visible, pas de scan.
SET LOCAL lock_timeout = '5s';
ALTER TABLE payments DROP CONSTRAINT fk_payments_quote;
ALTER TABLE payments ADD CONSTRAINT fk_payments_quote
    FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE RESTRICT NOT VALID;
```

Le nom définitif est donné directement : le renommage devient inutile, ce qui supprime une étape. Le `VALIDATE` est séparé, pour les raisons suivantes.

**Étape 2 (séparée) : validation.**

```sql
SET lock_timeout = '5s';
ALTER TABLE payments VALIDATE CONSTRAINT fk_payments_quote;
```

**Analyse des verrous, étape par étape**

| Instruction | Verrou sur `payments` | Verrou sur `quotes` | Scan | Effet sur les lectures / écritures |
|---|---|---|---|---|
| `DROP CONSTRAINT` | ACCESS EXCLUSIVE | SHARE ROW EXCLUSIVE (retrait des triggers, **à confirmer par test**) | Non | Bloque tout accès à `payments`, lectures comprises, jusqu'au COMMIT |
| `ADD ... NOT VALID` | SHARE ROW EXCLUSIVE | SHARE ROW EXCLUSIVE | Non | Bloque les écritures sur `payments` et `quotes`, pas les lectures |
| `COMMIT` | libération | libération | Non | Durée : quelques millisecondes si le verrou ACCESS EXCLUSIVE a été obtenu |
| `VALIDATE CONSTRAINT` | SHARE UPDATE EXCLUSIVE | ROW SHARE | **Oui** (jointure sur la PK de `quotes`) | Ne bloque pas INSERT / UPDATE / DELETE. Bloque seulement les DDL et VACUUM FULL concurrents |

Points importants :

- **`NOT VALID` ne supprime pas tous les risques de verrouillage.** Il évite le scan à l'ajout, mais le DROP prend ACCESS EXCLUSIVE, et ce verrou est seulement mis en file d'attente : si une transaction longue détient déjà un verrou sur `payments`, le DROP attend, et toutes les requêtes suivantes sur `payments` attendent derrière lui. C'est pourquoi `lock_timeout` est indispensable : sans lui, une attente peut bloquer l'application.
- **`VALIDATE` dans la même transaction que le DROP serait dangereux** : le verrou ACCESS EXCLUSIVE serait conservé pendant tout le scan, donc lectures et écritures bloquées pendant la durée complète du scan. Séparer les deux transactions évite cela.
- Le DROP et l'ADD prennent SHARE ROW EXCLUSIVE sur `quotes` : pendant quelques millisecondes, la création ou la modification de devis est retardée (pas de scan).

**Effets en cas d'échec**

| Interruption | État résultant | Protection contre la suppression d'un devis ayant des paiements | Action |
|---|---|---|---|
| Échec ou `lock_timeout` pendant la transaction DROP + ADD | Rollback complet : l'ancienne FK (CASCADE) est intacte | Ancienne règle (CASCADE, donc **non protégé**) | Rien à corriger. Relancer dans une fenêtre calme |
| Crash ou kill après COMMIT, avant VALIDATE | FK RESTRICT en `NOT VALID` | **Protégé** : la contrainte s'applique aux suppressions de devis et aux nouvelles insertions / mises à jour | Exécuter VALIDATE, après vérification des orphelins |
| VALIDATE échoue (orphelins, `lock_timeout`, annulation) | Identique au cas précédent : **protégé** | Protégé | Corriger la cause puis relancer VALIDATE (opération idempotente) |
| Interruption pendant VALIDATE | Aucun changement partiel : la contrainte reste en `NOT VALID` | Protégé | Relancer VALIDATE |

Dans tous les cas, aucun état ne retire la protection d'un devis sans la remplacer. Le seul état dégradé est l'ancienne règle, et il n'existe que si la transaction échoue, auquel cas PostgreSQL annule tout.

**Compatibilité Flyway et PostgreSQL**

- Par défaut, Flyway exécute un fichier de migration dans une transaction. Le fichier V22 tient donc dans une seule transaction, ce qui garantit l'atomicité du DROP + ADD. `SET LOCAL` est valide dans cette transaction.
- `VALIDATE` doit rester hors de cette transaction. La solution la plus sûre est une migration distincte (par exemple V23, à créer seulement après autorisation), qui s'exécute dans sa propre transaction. Elle ne prend que SHARE UPDATE EXCLUSIVE, donc compatible avec la transaction Flyway par défaut.
- Alternative à une migration distincte : la directive `-- flyway:executeInTransaction=false`. **Non retenue** : son support dépend de la version de Flyway, et la version utilisée par le projet n'est pas vérifiable ici (absente du dépôt Maven local, gérée par le parent Spring Boot).
- PostgreSQL 18 (version du serveur de test) : `ADD ... NOT VALID` et `VALIDATE CONSTRAINT` sont des syntaxes standard des FK. Le comportement décrit ici est à confirmer par le test T12 sur le serveur réel, non par la documentation seule.

**Reprise sûre**

- La migration V22 ne fait rien en partie : si elle échoue, Flyway marque la migration en échec. Avant de la relancer, il faut vérifier l'état réel (`pg_constraint`) et ne pas supposer l'ancien ou le nouvel état.
- Si VALIDATE doit être relancé : aucun effet de bord, l'opération est idempotente.
- Rollback (retour à CASCADE), à proposer seulement en cas de besoin, selon le même schéma : `DROP` puis `ADD ... ON DELETE CASCADE NOT VALID` dans une transaction, puis `VALIDATE` séparé. Cela rétablit la règle dangereuse et doit être décidé explicitement.

**Données historiques orphelines**

- Avec l'ancienne FK en place, un paiement sans devis ne peut pas exister, sauf si la FK a été désactivée à la main ou par un import (`session_replication_role = replica`). Vérification en lecture seule, à exécuter seulement après autorisation :

```sql
SELECT count(*) FROM payments p
WHERE NOT EXISTS (SELECT 1 FROM quotes q WHERE q.id = p.quote_id);
```

  Résultat attendu : `0`. Si le résultat est supérieur à 0, VALIDATE échoue. La décision de correction (rattacher, marquer, ou archiver) relève d'une décision métier distincte. Ne pas supprimer les paiements.
- `legacy_payments` (V6) n'a aucune FK vers `quotes` (V1 ligne 152 : seulement `booking_id`). Il n'existe donc pas de cascade cachée sur cette table.
- Le test existant `MigrationWithExistingDataIT` (dans `backend/src/test/java/tn/elitetunisia/migration/`) contient des scénarios de migration sur données existantes : à réutiliser pour le test de V22 sur une base contenant des données.

**Stratégie de production à documenter :**

1. Sauvegarde logique (`pg_dump`) avant application, restaurée sur une copie pour vérifier la restauration.
2. Copie de la base de production (non disponible ici) : mesurer la durée du VALIDATE, le nombre de lignes `payments`, la présence d'orphelins.
3. Fenêtre à faible trafic, `lock_timeout` respecté, et arrêt de tout travail long pendant la transaction DROP + ADD.
4. Vérification post-migration : `pg_constraint` (`confdeltype = 'r'`, `convalidated = true`), puis tests PostgreSQL.

Points non vérifiables ici : volume réel de `payments`, durée réelle du scan, présence d'orphelins en production, existence d'une copie représentative, version exacte de Flyway.

---

## Proposition séparée D5 : `invoice_payments` (non intégrée à V22)

**Nom de la FK (à vérifier, non confirmé sans base) :** `invoice_payments` est créée dans [V7__invoices.sql](C:/Users/aaziza/Desktop/test/database/migrations/V7__invoices.sql) (lignes 44 à 46). La FK `payment_id` est déclarée inline, sans nom, donc PostgreSQL lui attribue le nom par défaut `invoice_payments_payment_id_fkey`. Vérification à faire avant toute migration :

```sql
SELECT conname, confdeltype FROM pg_constraint
WHERE conrelid = 'invoice_payments'::regclass AND contype = 'f';
```

**Justification :** un paiement CAPTURED ou REFUNDED lié à une facture est la preuve du montant encaissé. Un `DELETE` sur `payments` supprime aujourd'hui la ligne `invoice_payments` par cascade, ce qui fausse le total facturé sans trace. Le RESTRICT est donc justifié, cohérent avec D3.

**Usages applicatifs :** aucun `delete` sur `payments` ni sur `invoice_payments` dans `backend/src/main/java` (recherche effectuée). [InvoiceService.java](C:/Users/aaziza/Desktop/test/backend/src/main/java/tn/elitetunisia/invoice/InvoiceService.java) (lignes 97 à 105) insère via `JdbcTemplate` avec `on conflict do nothing`, sans supprimer. Le RESTRICT n'a donc pas d'effet sur le code actuel.

**SQL proposé (séparé, en deux étapes, même schéma que V22) :**

```sql
-- Étape 1 : transaction unique
SET LOCAL lock_timeout = '5s';
ALTER TABLE invoice_payments DROP CONSTRAINT invoice_payments_payment_id_fkey;
ALTER TABLE invoice_payments ADD CONSTRAINT fk_invoice_payments_payment
    FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT NOT VALID;

-- Étape 2 : séparée
SET lock_timeout = '5s';
ALTER TABLE invoice_payments VALIDATE CONSTRAINT fk_invoice_payments_payment;
```

Le verrou du `DROP` est ACCESS EXCLUSIVE sur `invoice_payments` (petite table, durée courte) : impact limité, mais il suit la même règle de `lock_timeout`.

**Décision D5b, non traitée ici :** la FK `invoice_id` (V7 ligne 45) est en CASCADE : supprimer une facture supprime ses liens de paiement. Aucun code ne supprime de facture. Il s'agit d'une décision métier distincte, à trancher avant d'ajouter un RESTRICT sur `invoice_id`.

**Test associé :** T14 (vérification du nom), puis un test PostgreSQL : `DELETE FROM payments` d'un paiement lié à une facture doit échouer après la correction.

## Corrections nécessaires avant application

1. Compléter l'en-tête de V22 : indiquer l'indépendance vis-à-vis de V21 et documenter le verrou `ACCESS EXCLUSIVE` qui persiste jusqu'au commit (donc pendant `VALIDATE` si les deux étaient dans la même transaction).
2. Appliquer la séquence retenue (DROP + ADD NOT VALID dans une transaction, VALIDATE séparé, sans renommage). **La séquence ADD / VALIDATE / DROP / RENAME initialement demandée est rejetée** pour le risque de coexistence des deux FK (test T12).
3. Ajouter une requête d'orphelins avant application et son résultat attendu (0).
4. Vérifier le nom de la FK `invoice_payments` par `pg_constraint` avant toute migration future la touchant (ne pas le supposer).
5. Corriger le nom obsolète `payments_one_captured_per_quote_idx` dans l'en-tête de V21 (tâche séparée, non bloquante).

## Décisions métier

- **D1 (retenue par l'utilisateur)** : un devis sans paiement peut être supprimé si aucune autre règle ne l'interdit ; un devis ayant un historique financier doit être conservé. Non encore implémenté : V22 ne couvre que la protection des paiements.
- **D2 (ouverte)** : conservation des tentatives FAILED / CANCELLED. V22 les protège toutes.
- **D3 (retenue par l'utilisateur)** : suppression physique d'un paiement CAPTURED ou REFUNDED interdite par l'application. Non couvert par V22 : aucune contrainte base ne l'empêche. Option ouverte : trigger `BEFORE DELETE` sur `payments` pour ces statuts.
- **D4 (ouverte)** : durées de conservation des paiements et devis.
- **D5 (retenue par l'utilisateur)** : RESTRICT sur `invoice_payments` ; proposition séparée ci-dessus.
- **D5b (ouverte)** : RESTRICT ou CASCADE sur `invoice_id`.
- **D6 (ouverte)** : modèle de remboursement et règle de re-paiement d'un devis remboursé (V21 ne couvre pas `REFUNDED`).

## Plan de tests

1. Créer `PaymentHistoryIntegrityPostgresIT` sur `et_test_db` (127.0.0.1:15432, utilisateur `et_test`), couvrant T1 à T14.
2. Avant toute correction : exécuter T1, T2 et T12 pour prouver le risque (T12 prouve si la coexistence est réellement dangereuse).
3. Après application de V22 sur `et_test_db` **uniquement après autorisation explicite** : T1, T2, T5 doivent passer ; T3, T4, T11 doivent rester verts.
4. Mesurer la durée de `VALIDATE` et le blocage concurrent (T7, T13) sur une copie de données représentatives. Non disponible ici : limite documentée.
5. Exécuter la suite PostgreSQL existante (`scripts/test-backend-postgres.ps1`) **uniquement après autorisation**, car le script recrée la base.

## Recommandation pour la prochaine étape

1. Confirmer l'autorisation de modifier `database/proposed/V22__payments_quote_fk_restrict.sql` et ce document : déjà fait dans cette revue, à valider.
2. Autoriser la création de `PaymentHistoryIntegrityPostgresIT` et l'exécution de T1, T2, T12 (avant correction) sur `et_test_db`.
3. Trancher D2, D4, D5b et D6, et décider si D3 nécessite un trigger en base.
4. Demander une autorisation explicite pour toute application de V22 ou V23 (VALIDATE) sur `et_test_db`.

**Fichiers modifiés dans cette étape : `database/proposed/V22__payments_quote_fk_restrict.sql` et `docs/architecture/v22-final-review.md` uniquement.** Aucune migration n'a été déplacée ni appliquée, aucun SQL n'a été exécuté, aucun commit, push ou déploiement n'a été effectué, et T-A05 n'est pas commencée. **En attente de votre autorisation explicite avant toute validation sur PostgreSQL.**

