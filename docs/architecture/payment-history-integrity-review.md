# Intégrité de l'historique financier : `payments.quote_id ON DELETE CASCADE`

Statut : audit et proposition. Aucune migration appliquée, aucun code, schéma ou donnée modifié.
Périmètre : table `payments` active (V6), relations avec `quotes`, `bookings`, `invoices`, et chemins de suppression.

## 1. Synthèse

- `payments.quote_id` est en `ON DELETE CASCADE` (V6, `fk_payments_quote`). Supprimer un devis supprime silencieusement tous ses paiements, y compris `CAPTURED` et `REFUNDED`.
- Aucun chemin de suppression de devis n'existe aujourd'hui dans le code (aucun `quotes.delete`, aucun `DELETE FROM quotes` dans `backend/src`, ni dans les tests). Le risque est donc **latent** : il se déclenche par SQL manuel, par une future fonctionnalité, ou par un nettoyage de données.
- La cascade est le point le plus dangereux, mais elle n'est pas le seul : `payment_events` et `invoice_payments` suivent aussi en `CASCADE` sur `payments`, et `invoices` n'a aucune FK vers `quotes` ni `bookings`.
- Proposition : passer `fk_payments_quote` en `ON DELETE RESTRICT` (proposition V22, `database/proposed/V22__payments_quote_fk_restrict.sql`). C'est une correction minimale : aucune donnée n'est modifiée, aucun code n'est à changer pour le chemin actuel, et le blocage est explicite.
- Recommandation complémentaire, soumise à décision : interdire aussi la suppression directe de `payments` (ou de ses liens de facturation) par `RESTRICT` sur `payment_events` et `invoice_payments`.

## 2. Schéma réel (lecture des migrations, ordre V1 à V20)

| Table | Colonne | Référence | Règle | Migration |
|---|---|---|---|---|
| `payments` | `quote_id` NOT NULL | `quotes(id)` | **CASCADE** | V6 (`fk_payments_quote`) |
| `payments` | `booking_id` NULL | `bookings(id)` | SET NULL | V6 (`fk_payments_booking`) |
| `payments` | `customer_id` NOT NULL | **aucune FK** | — | V6 |
| `payment_events` | `payment_id` | `payments(id)` | CASCADE | V6 |
| `invoice_payments` | `payment_id` | `payments(id)` | CASCADE | V7 |
| `invoice_payments` | `invoice_id` | `invoices(id)` | CASCADE | V7 |
| `invoices` | `quote_id` NULL | **aucune FK** | — | V7 |
| `invoices` | `booking_id` NULL | **aucune FK** | — | V7 |
| `invoices` | `customer_id` NOT NULL | **aucune FK** | — | V7 |
| `quotes` | `booking_id` NOT NULL | `bookings(id)` | RESTRICT (défaut) | V5 |
| `quotes` | `customer_id` NOT NULL | `customers(id)` | RESTRICT | V1 |
| `quote_events` | `quote_id` | `quotes(id)` | RESTRICT (défaut) | V5 |
| `quote_items` | `quote_id` | `quotes(id)` | CASCADE | V1 (table legacy, usage à confirmer) |
| `bookings` | — | `drivers`/`vehicles` (V11, V16) | CASCADE ou SET NULL, sans lien direct avec les paiements | V11, V16 |

Remarques :

- La table `quote_lines` est utilisée par l'entité JPA `QuoteLineEntity`, mais je n'ai pas trouvé sa définition ni sa FK dans les migrations lues. **Point à vérifier** (voir section 8).
- `payments.customer_id` n'a pas de FK : un paiement peut référencer un client inexistant. Ce n'est pas la cause de la perte, mais c'est une faiblesse d'intégrité.
- `quote_events` est en RESTRICT : un devis ayant un historique d'événements ne peut déjà pas être supprimé. C'est une protection existante, à la fois incomplète (elle ne protège pas les paiements) et utile comme modèle.

## 3. Chemins de suppression possibles

| # | Chemin | Déclencheur | État actuel | Conséquence sur les paiements |
|---|---|---|---|---|
| S1 | `DELETE FROM quotes` (SQL manuel, outil d'administration) | opérateur | Possible (si aucune FK RESTRICT ne bloque : `quote_events` bloque si des événements existent) | **Cascade : paiements effacés** |
| S2 | Future API ou service `QuoteService.delete` | développement futur | Absent aujourd'hui | Cascade : paiements effacés |
| S3 | `DELETE FROM bookings` | opérateur ou future fonctionnalité | Bloqué par `quotes.booking_id` RESTRICT tant qu'un devis existe | Aucun effet direct sur payments (bloqué avant) |
| S4 | `DELETE FROM customers` ou `users` (cascade depuis users) | opérateur | Bloqué par `quotes.customer_id` RESTRICT | Aucun effet direct |
| S5 | `DELETE FROM payments` (SQL manuel) | opérateur | Possible, aucune protection | `payment_events` et `invoice_payments` effacés en cascade |
| S6 | Suppression d'un `booking` lié via `payments.booking_id` | — | Non atteignable : un paiement exige un devis, donc un booking non supprimable | SET NULL non atteignable en pratique |
| S7 | Nettoyage de tests (`TRUNCATE`, `DELETE`) | tests | Aucun `DELETE` sur quotes/payments trouvé dans `backend/src` | Risque sur base de test cumulative (`et_test_db`) |

Le chemin S1 est le seul qui détruit réellement un paiement `CAPTURED` sans barrière, et S5 détruit en plus la trace de facturation.

## 4. Impacts des stratégies possibles

### Option A — Conserver `CASCADE` (statu quo)
- Aucun coût immédiat.
- Une suppression de devis efface l'historique financier. Non acceptable pour un système de paiement.
- **Rejetée.**

### Option B — `ON DELETE RESTRICT` sur `payments.quote_id` (recommandée)
- Une suppression de devis ayant au moins un paiement (quel que soit le statut) échoue avec SQLSTATE `23503`.
- Les paiements, leurs événements et leurs liens de facture sont conservés.
- Aucun code applicatif n'appelle de suppression de devis : pas de régression connue.
- Effet collatéral : un devis ayant seulement des tentatives `FAILED` ou `CANCELLED` ne peut plus être supprimé. C'est souhaité : l'historique des tentatives est une donnée de conformité. Le moyen de « retirer » un devis doit être un statut (`CANCELLED`/`EXPIRED`), pas une suppression.
- Compatible avec V21 : l'index unique partiel ne concerne que `CAPTURED`, sans lien avec la règle de suppression.

### Option C — `ON DELETE SET NULL` sur `quote_id`
- Impossible : `quote_id` est NOT NULL. Il faudrait rendre la colonne nullable, ce qui affaiblit le modèle (paiement orphelin sans devis) et casse la contrainte V21 de lecture par devis. **Rejetée.**

### Option D — Trigger `BEFORE DELETE` sur `payments` (protection par statut)
- Bloque la suppression d'un paiement `CAPTURED` ou `REFUNDED` seulement.
- Plus fin, mais plus complexe à maintenir et à tester, et ne protège pas les `payment_events` d'un paiement `FAILED`. **Non recommandée en première étape**, à garder comme défense en profondeur si une décision impose une suppression administrative de tentatives.

### Option E — Soft delete (statut `CANCELLED`/`ARCHIVED` sur `quotes`)
- Cohérent avec la recommandation de l'analyse de cycle de vie des comptes.
- Doit accompagner Option B : c'est le moyen légitime de retirer un devis.

**Choix proposé : Option B, complétée par E. Option D en réserve.**

## 5. Proposition de migration corrective (V22)

Fichier : `database/proposed/V22__payments_quote_fk_restrict.sql`. Non placé dans `database/migrations/` (le `pom.xml` copie ce dossier vers `db/migration`, donc un fichier y serait appliqué au prochain démarrage).

```sql
ALTER TABLE payments DROP CONSTRAINT fk_payments_quote;

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_quote
    FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE RESTRICT NOT VALID;

ALTER TABLE payments VALIDATE CONSTRAINT fk_payments_quote;
```

Propriétés :

- **Indépendante de V21** : elle ne touche que la FK, pas l'index. Les deux ordres d'application sont valides.
- **Sans perte de données** : la FK existait déjà, donc toutes les lignes respectent la nouvelle règle. `VALIDATE` ne fait que confirmer.
- **Transaction Flyway** : PostgreSQL exécute la migration dans une transaction, donc l'état sans FK n'est jamais visible d'une autre session.
- **Verrouillage** : `DROP CONSTRAINT` prend un verrou `ACCESS EXCLUSIVE` sur `payments` pendant une durée très courte ; `ADD ... NOT VALID` prend un verrou court ; `VALIDATE` ne bloque pas les écritures. À mesurer sur une copie avant toute application en production.
- **Retour arrière** : `DROP CONSTRAINT` puis `ADD ... ON DELETE CASCADE`. Ce retour est documenté, mais il ne doit jamais être automatisé.
- **Vérification** : `SELECT confdeltype FROM pg_constraint WHERE conname = 'fk_payments_quote';` doit renvoyer `r`.

## 6. Mesures complémentaires (décisions à valider, non proposées en SQL)

### 6.1 Protection des traces de paiement (V23 possible)

```sql
-- Brouillon, non applicable tel quel : a valider avant ecriture.
ALTER TABLE payment_events DROP CONSTRAINT fk_payment_events_payment;
ALTER TABLE payment_events ADD CONSTRAINT fk_payment_events_payment
    FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT;

ALTER TABLE invoice_payments DROP CONSTRAINT invoice_payments_payment_id_fkey;
ALTER TABLE invoice_payments ADD CONSTRAINT invoice_payments_payment_id_fkey
    FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT;
```

Le nom `invoice_payments_payment_id_fkey` est le nom par défaut PostgreSQL. **À vérifier** dans `pg_constraint` avant toute écriture.

### 6.2 Lien facture → devis et réservation

`invoices.quote_id`, `invoices.booking_id` et `invoices.customer_id` n'ont pas de FK. Avant d'ajouter des FK RESTRICT, vérifier les orphelins en lecture seule :

```sql
SELECT count(*) FROM invoices i
WHERE i.quote_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM quotes q WHERE q.id = i.quote_id);
```

(Équivalents pour `booking_id` et `customer_id`.) Ne pas appliquer sans ce contrôle sur une copie représentative.

### 6.3 Lien de paiement vers la réservation

`payments.booking_id` est en SET NULL. Non atteignable aujourd'hui, car la suppression d'un booking est bloquée par `quotes.booking_id`. Recommandation : passer en RESTRICT dans la même décision que 6.1, pour éviter une perte silencieuse de lien si la règle change.

### 6.4 FK sur `payments.customer_id`

Ajouter `REFERENCES customers(id) ON DELETE RESTRICT` après contrôle des orphelins. Cohérent avec R-02 de l'analyse de cycle de vie des comptes.

## 7. Compatibilité

### V21 (index unique partiel `CAPTURED`)

- V21 ne change pas la règle de suppression. Après V22, un devis avec un `CAPTURED` ne peut plus être supprimé, ce qui renforce l'invariant « un seul CAPTURED par devis ».
- Ordre d'application indifférent. Si V21 échoue (doublons `CAPTURED` existants), V22 reste valide.

### Remboursements futurs (`REFUNDED`)

- Un remboursement doit **mettre à jour** le statut ou créer une ligne de remboursement, jamais supprimer le paiement. RESTRICT protège ce modèle.
- Si un remboursement partiel est ajouté, il faudra vérifier que `refunded_amount <= amount` (contrainte CHECK à proposer avec cette future évolution).
- Un devis remboursé ne doit pas redevenir payable sans décision métier (point déjà noté pour V21).

### Facturation

- `invoice_payments` est en CASCADE sur `payments`. Avec Option B seule, une suppression directe d'un paiement (S5) efface encore le lien de facturation. C'est l'objet de 6.1.

### Frontend et API

- Aucun écran ni endpoint ne supprime un devis ou un paiement. Pas de changement de contrat attendu.

## 8. Risques et points à vérifier

| Réf. | Criticité | Constat | Preuve | Action |
|---|---|---|---|---|
| P-01 | P0 (latent) | Cascade sur paiements, y compris CAPTURED/REFUNDED | V6 l.21 | Corriger par V22 (proposition) |
| P-02 | P1 | DELETE direct de `payments` efface `invoice_payments` et `payment_events` | V6 l.31, V7 l.46 | Décision 6.1 |
| P-03 | P1 | `invoices` sans FK vers `quotes`, `bookings`, `customers` | V7 l.10 et suivantes | Contrôle d'orphelins (6.2) |
| P-04 | P2 | `payments.customer_id` sans FK | V6 | 6.4 |
| P-05 | P2 | `payments.booking_id` en SET NULL | V6 l.22 | 6.3 |
| P-06 | P2 | `quote_items` (V1) en CASCADE : table legacy à confirmer | V1 l.144 | Vérifier si la table existe encore et si elle est utilisée |
| P-07 | À vérifier | `quote_lines` (entité JPA) : FK non trouvée dans les migrations lues | `QuoteLineEntity` | Vérifier avec `\d quote_lines` en lecture seule sur base isolée |
| P-08 | Risque de test | Un test qui nettoie par `DELETE FROM quotes` échouerait après V22 | aucun trouvé | Vérifier avant application (les tests PostgreSQL utilisent `et_test_db` cumulative) |

Risques non vérifiables dans l'environnement :

- Données historiques réelles (aucune copie représentative disponible) : présence éventuelle de devis supprimés manuellement, de paiements sans devis, d'orphelins de facture.
- Procédures d'exploitation hors code (scripts de purge, SQL manuel de l'équipe).
- Durée réelle du verrou de V22 sur la volumétrie de production.

## 9. Décisions métier à prendre

1. **D1 — Suppression d'un devis** : faut-il autoriser la suppression, même sans paiement ? Recommandation : non, utiliser un statut `CANCELLED` ou `EXPIRED`.
2. **D2 — Tentatives échouées** : doivent-elles être conservées indéfiniment ? Recommandation : oui, elles font partie de la trace de conformité.
3. **D3 — Suppression administrative d'un paiement** : interdite, ou réservée à un cas précis (erreur de saisie avant capture) avec journal ? Recommandation : interdite après `CAPTURED`.
4. **D4 — Durée de conservation** des paiements, événements et factures (à valider juridiquement).
5. **D5 — Option 6.1** (RESTRICT sur `payment_events` et `invoice_payments`) : acceptée ou non.
6. **D6 — Remboursement** : modèle (mise à jour du statut ou ligne dédiée) et règle de re-paiement après remboursement.

## 10. Tests PostgreSQL nécessaires

À placer dans un test d'intégration dédié (ex. `PaymentHistoryIntegrityPostgresIT`), sur PostgreSQL réel, avec une base isolée autorisée. Chaque test prépare ses données via SQL explicite et ne suppose pas une base vide.

| Test | Scénario | Résultat attendu |
|---|---|---|
| T1 | Devis avec un paiement `CAPTURED`, `DELETE FROM quotes` | Échec SQLSTATE `23503` ; paiement toujours présent, statut, montant et `captured_at` inchangés |
| T2 | Pour chaque statut `PENDING`, `AUTHORIZED`, `FAILED`, `CAPTURED`, `REFUNDED`, `CANCELLED` | Suppression du devis refusée dans chaque cas |
| T3 | Devis sans paiement ni événement | Suppression autorisée (non-régression de la règle, ne bloque pas à tort) |
| T4 | `SELECT confdeltype FROM pg_constraint WHERE conname='fk_payments_quote'` | `r` après V22 ; `c` avant |
| T5 | Race : transaction A insère un paiement `CAPTURED` pour le devis Q (non commitée), transaction B supprime Q | B attend le commit de A puis échoue en `23503` ; ou B passe avant et A échoue en `23503` ; jamais de paiement orphelin |
| T6 | Paiement `CAPTURED` présent, V21 appliquée : tentative d'un second `CAPTURED` sur le même devis | Refusé par l'index unique (SQLSTATE `23505`) ; compatibilité V21/V22 |
| T7 | Suppression d'un booking lié à un devis | Échec (`quotes.booking_id` RESTRICT), aucun paiement touché |
| T8 | Option 6.1 si acceptée : `DELETE FROM payments` avec `payment_events` et `invoice_payments` | Échec `23503` ; aucune ligne supprimée |
| T9 | Migration sur base vide, V1 à V22 | Application complète sans erreur |
| T10 | Migration sur copie représentative des données historiques | Application sans erreur, comptage des paiements inchangé |
| T11 | Rollback documenté (retour à CASCADE) sur base de test | Comportement conforme au retour arrière documenté |

Preuves exigées pour déclarer chaque étape réussie : sortie Maven `BUILD SUCCESS` avec compteurs `Tests run / Failures / Errors / Skipped: 0`, requête `pg_constraint` avant/après, et comptage des lignes avant/après sur la base isolée. Les tests T5 et T6 doivent utiliser de vraies connexions PostgreSQL indépendantes, pas H2 ni Mockito.

## 11. Plan de travail proposé (après autorisation)

1. Décisions D1 à D3 (bloquant pour V22 et les tests de suppression).
2. Écriture du test `PaymentHistoryIntegrityPostgresIT` (T1 à T5) : il doit **échouer** avant V22 et **réussir** après, pour prouver la correction.
3. Autorisation explicite pour appliquer V22 sur une base isolée (jamais `et_test_db` sans accord, car elle est cumulative).
4. Application sur base isolée, vérification `pg_constraint`, exécution des tests T1 à T7.
5. Contrôle des orphelins (6.2) sur copie représentative, puis décision sur V23 (6.1 à 6.4).
6. Décision sur le déplacement de V22 vers `database/migrations/` (à faire seulement après validation des tests).

## 12. Garanties

- Aucun fichier existant modifié. Seuls deux fichiers nouveaux ont été créés : ce document et la proposition dans `database/proposed/`.
- Aucune migration historique touchée. V22 n'est pas dans le chemin Flyway.
- Aucune base modifiée, aucune requête d'écriture, aucun test exécuté pour cette analyse.
- Aucun commit, push ni déploiement.
- Les constats proviennent de la lecture des migrations V1, V5, V6, V7 et des sources Java ; les points de la section 8 restent à vérifier.
