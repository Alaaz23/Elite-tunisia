# Rapport de validation architecturale et technique de V22 (Phase 1)

Statut : **Phase 1 — audit en lecture seule**. Aucun SQL exécuté, aucune base touchée, aucune migration déplacée ou appliquée, aucun fichier de code modifié.

Fichier évalué : `database/proposed/V22__payments_quote_fk_restrict.sql` (révision 2).

**Phase 1.1 (revue finale)** : mise à jour après relecture du brief dans le journal de session. Le brief n'est que partiellement récupérable (voir annexe A). Les libellés des critères 1 à 12 et le nombre de sections du brief ne sont donc **pas confirmés**. Les T12 à T14 de ce rapport et ceux du brief de la Phase 1.1 ne sont pas identiques : voir annexe F.

Règle appliquée : un critère n'est déclaré `PASS` que sur preuve d'une exécution réelle. Aucun test PostgreSQL n'ayant été exécuté dans cette phase, aucun `PASS` n'est attribué.

---

## 1. Objectif de la validation

Vérifier si la proposition V22 (`payments.quote_id` passe de `ON DELETE CASCADE` à `ON DELETE RESTRICT` sur la FK `fk_payments_quote`) peut être appliquée sans risque pour l'intégrité des paiements, la disponibilité de la base et la reprise après incident.

## 2. Périmètre et méthode

- Lecture seule : fichiers SQL, configuration Maven, code Java, état Git.
- Résolution de dépendances Maven hors ligne avec JDK 21 (`dependency:list`), sans compilation ni exécution de test.
- Aucune requête SQL, aucune connexion à PostgreSQL (ni 5432, ni 15432).
- Pas de copie de données historiques disponible : les risques liés aux données de production restent non mesurés.

## 3. État Git (constaté, rien committé)

Fichiers modifiés suivis (9) :
- `backend/src/main/java/tn/elitetunisia/booking/BookingOperationService.java`
- `backend/src/main/java/tn/elitetunisia/booking/BookingService.java`
- `backend/src/main/java/tn/elitetunisia/payment/PaymentRepository.java`
- `backend/src/main/java/tn/elitetunisia/payment/PaymentService.java`
- `backend/src/test/java/tn/elitetunisia/BackendApplicationTests.java`
- `backend/src/test/java/tn/elitetunisia/booking/BookingOperationServiceTest.java`
- `backend/src/test/java/tn/elitetunisia/payment/PaymentServiceIdempotenceCompatibleTest.java`
- `docs/start-local.md`
- `frontend/src/app/features/quotes/quote-detail.component.ts`

Non suivis (à préserver) : `database/proposed/`, `docs/architecture/`, `docs/technical-audit.md`, `scripts/test-backend-postgres.ps1`, et les tests `BookingAuditAtomicityPostgresIT`, `BookingLockOrderPostgresIT`, `PaymentQuoteCapturePostgresIT`, `PaymentQuoteInvariantUnitTest`.

## 4. Analyse du SQL V22

Contenu réel du fichier :

```sql
SET LOCAL lock_timeout = '5s';
ALTER TABLE payments DROP CONSTRAINT fk_payments_quote;
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_quote
    FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE RESTRICT NOT VALID;
```

Constats :
- Le nom ciblé `fk_payments_quote` correspond à la définition de `database/migrations/V6__payments.sql` (ligne 21 : `REFERENCES quotes(id) ON DELETE CASCADE`). C'est la seule définition de ce nom dans V1 à V20 (vérification statique).
- Le fichier **ne suit pas la séquence demandée** (ADD NOT VALID, VALIDATE, DROP, RENAME). Il fait DROP puis ADD dans une seule transaction, sans RENAME. Raison documentée dans la révision 2 : pendant la coexistence de deux FK CASCADE et RESTRICT sur la même colonne, le résultat d'une suppression de devis dépend de l'ordre des triggers RI. Cette hypothèse n'est **pas encore prouvée** (test T12 non exécuté). Cet écart doit être validé explicitement par l'auteur.
- `VALIDATE CONSTRAINT` est volontairement exclu du fichier. Il doit être lancé hors transaction Flyway.
- Le fichier ne crée aucun index. Voir section 7.

## 5. Relations entre payments, quotes, payment_events, invoice_payments

| Relation | Définition constatée | Source |
|---|---|---|
| `payments.quote_id` → `quotes.id` | `ON DELETE CASCADE` (nom `fk_payments_quote`) | V6 ligne 21 |
| `payments.booking_id` → bookings | `ON DELETE SET NULL` (`fk_payments_booking`) | V6 ligne 22 |
| `payment_events.payment_id` → `payments.id` | `ON DELETE CASCADE` (`fk_payment_events_payment`) | V6 ligne 31 |
| `invoice_payments.payment_id` → `payments.id` | `ON DELETE CASCADE`, nom par défaut attendu `invoice_payments_payment_id_fkey` (non vérifié en base) | V7 ligne 46 |
| `invoice_payments.invoice_id` → `invoices.id` | `ON DELETE CASCADE` | V7 ligne 45 |
| `invoices.quote_id` | **Aucune FK** | V7 ligne 10 |
| `quote_events.quote_id` → `quotes.id` | Pas d'action explicite (NO ACTION) | V5 ligne 44 |
| `quote_lines.quote_id` → `quotes.id` | `ON DELETE CASCADE` (ex-`quote_items`, renommée en V5) | V1 ligne 146 |

Conséquence : une suppression de devis avec paiements déclenche aujourd'hui une cascade vers `payments`, puis vers `payment_events` et `invoice_payments`. La V22 ne bloque que la première étape. La suppression directe d'un paiement (hors devis) reste en cascade vers `payment_events` et `invoice_payments`. Ce point relève de D3/D5, pas de V22 seule.

## 6. Usages réels dans le code

- Recherche sur `backend/src/main/java` : aucune méthode de suppression pour `quotes`, `payments`, `invoices` ou `invoice_payments`. Les seules suppressions concernent les catégories, marques, affectations chauffeurs et véhicules.
- `InvoiceService` (environ lignes 97 à 105) insère dans `invoice_payments` via `JdbcTemplate` avec `ON CONFLICT DO NOTHING`. Aucune suppression.
- `PaymentService` et `PaymentRepository` : modifications T-A04 (idempotence). Aucun chemin de suppression.

Conclusion : aucun usage applicatif actuel ne supprime un devis ayant des paiements. Le risque est donc **potentiel** (futur endpoint, suppression SQL manuelle, script d'administration), pas confirmé en production.

## 7. Verrous PostgreSQL et risques de blocage

| Instruction | Verrou attendu | Statut |
|---|---|---|
| `SET LOCAL lock_timeout` | Aucun | Constaté dans le fichier |
| `ALTER TABLE payments DROP CONSTRAINT` | `ACCESS EXCLUSIVE` sur `payments` jusqu'au COMMIT | Attendu selon la doc PostgreSQL, **non mesuré** |
| `ADD CONSTRAINT ... NOT VALID` | `SHARE ROW EXCLUSIVE` sur `payments` et `quotes`, pas de scan | Attendu, **non mesuré** (à confirmer via `pg_locks`) |
| `VALIDATE CONSTRAINT` (hors fichier) | `SHARE UPDATE EXCLUSIVE`, scan de `payments` | Attendu, **non mesuré** |

Risques :
- Le DROP bloque lectures et écritures sur `payments` pendant toute la transaction. Si une transaction longue détient un verrou sur `payments`, le DROP attend en file et bloque à son tour toutes les requêtes suivantes. `lock_timeout = 5s` limite ce risque : la migration échoue au lieu d'attendre indéfiniment.
- Le scan de `VALIDATE` ne bloque pas les DML mais consomme I/O et temps sur `payments`. Sa durée dépend du volume, inconnu ici.
- Absence d'index sur `payments(quote_id)` : à chaque suppression ou vérification sur `quotes`, PostgreSQL doit scanner `payments`. Le plan réel n'a **pas été mesuré**. Ce point ne doit pas être présenté comme confirmé.

## 8. Effets des décisions D1 à D6

| Décision | Implication sur V22 | État |
|---|---|---|
| D1 — devis sans paiement supprimable, devis historisé conservé | RESTRICT est cohérent : seul le devis avec paiement devient non supprimable | Retenue |
| D3 — paiement CAPTURED/REFUNDED non supprimable physiquement | V22 ne couvre que la suppression de devis. Suppression directe de paiement non couverte : `payment_events` et `invoice_payments` restent en CASCADE | Retenue, couverture partielle |
| D5 — RESTRICT sur `invoice_payments` | Proposition séparée, nom présumé `invoice_payments_payment_id_fkey` | Non intégrée à V22, nom non vérifié |
| D2, D4, D5b (`invoice_id` CASCADE), D6 (remboursement et nouveau paiement) | Non traitées | **Ouvertes** |
| D3 au niveau base (trigger) | Non décidé | **Ouvert** |

## 9. Plan de tests PostgreSQL (non exécutés)

Tests prévus dans `docs/architecture/v22-final-review.md` :
- T1, T2 : comportement de la FK avant et après la migration.
- T12 : prouver ou infirmer l'ordre des triggers RI pendant la coexistence CASCADE/RESTRICT (hypothèse centrale).
- T13, T14 : suppression de devis avec et sans paiement, paiements historiques.

Ces tests doivent tourner **uniquement** sur `et_test_db` (127.0.0.1:15432, utilisateur `et_test`), sous autorisation distincte.

## 10. Stratégie d'application en production

- Appliquer le fichier dans Flyway : une transaction par fichier (comportement PostgreSQL par défaut). Le DROP+ADD est atomique.
- `VALIDATE CONSTRAINT` doit être exécuté séparément, hors Flyway, après vérification des orphelins, dans une fenêtre acceptée.
- Vérification post-migration : `SELECT confdeltype, convalidated FROM pg_constraint WHERE conname = 'fk_payments_quote';` attendu : `r` et `false` (puis `true` après VALIDATE).
- Mesure de volume, nombre d'orphelins et durée du VALIDATE : **impossibles** sans copie représentative de production.

## 11. Flyway, versions et configuration

- Version Flyway résolue : **`flyway-core` 12.4.0** et **`flyway-database-postgresql` 12.4.0**, via `spring-boot-starter-flyway` 4.1.1 (Spring Boot 4.1.1 selon `backend/pom.xml`). **Confirmé** par `mvnw -o dependency:tree` avec JDK 21 (Phase 1.1) : code de sortie 0, stderr vide (0 ligne), `BUILD SUCCESS`. Le code -1 de la passe précédente n'est pas reproduit.
- La directive `executeInTransaction` est présente dans le jar 12.4.0, mais **non utilisée** par V22. Son comportement n'est pas prouvé à l'exécution.
- `backend/pom.xml` exclut `db/migration/**` des ressources et copie `database/migrations` vers `db/migration`. V21 et V22 ne sont donc **pas** dans le classpath Flyway. Correct tant que ce n'est pas autorisé.
- Doublon V11 (détail en annexe H) : `backend/src/main/resources/db/migration/V11__...` est suivi par Git, mais exclu du packaging. La copie active est `database/migrations/V11__...`, copiée dans `db/migration` par Maven.
- Trois fichiers `_DISABLED_V4`, `_DISABLED_V5`, `_DISABLED_V6` existent dans le même dossier. Ils sont aussi exclus du packaging.
- Profil de test `application-test.yml` : H2 avec `create-drop` et Flyway désactivé. **Ne prouve rien** sur les contraintes PostgreSQL réelles.

## 12. Points bloquants et limites

Bloquants confirmés pour une validation complète :
- Aucun test PostgreSQL de V22 n'a été exécuté (T1, T2, T12 à T14).
- Hypothèse RI (ordre des triggers) non prouvée.
- Node 18.20.8 installé, alors que `frontend/package.json` exige `^22.22.3 || ^24.15.0 || >=26` : `ng build` impossible dans cet environnement. Hors périmètre V22 mais à lever avant pré-production.
- JDK par défaut = JDK 8 : toute validation backend doit forcer JDK 21.

Limites non levables dans cet environnement :
- Volume réel de `payments`, nombre d'orphelins, durée de VALIDATE.
- Absence de copie représentative de données historiques.
- Plan d'exécution réel (absence d'index `payments(quote_id)` à mesurer).
- Configuration des verrous en production (transactions longues en cours).

## 13. Critères d'acceptation

Légende : PASS (preuve d'exécution réelle), FAIL (preuve d'un défaut), NOT TESTED (non exécuté), BLOCKED (impossible à exécuter ici).

| # | Critère (libellé reconstruit) | Statut | Preuve / motif |
|---|---|---|---|
| 1 | Le SQL cible exactement `fk_payments_quote` | NOT TESTED | Nom cohérent avec V6 ligne 21 (statique). Non vérifié dans `pg_constraint`. |
| 2 | Compatibilité V1 à V20 et V21 | NOT TESTED | Aucune migration exécutée. Pas de conflit de nom constaté (statique). |
| 3 | Aucune dépendance bloquante (vues, triggers, contraintes) | NOT TESTED | Non interrogé en base. |
| 4 | Relations payments / quotes / payment_events / invoice_payments | NOT TESTED | Relations lues dans V5, V6, V7 (statique). Pas de vérification `pg_constraint`. |
| 5 | Effet d'une suppression directe de paiement | NOT TESTED | Non exécuté. Cascade vers `payment_events` et `invoice_payments` attendue. |
| 6 | Usages réels repositories et services | NOT TESTED | Recherche statique seulement (grep sur `backend/src/main/java` : aucune suppression de quote/payment/invoice). Aucun test exécuté, donc pas de PASS. |
| 7 | Cohérence RESTRICT avec le cycle de vie (D1, D3) | NOT TESTED | Cohérence de principe, non prouvée par test. D3 partiel. |
| 8 | Verrous PostgreSQL mesurés | NOT TESTED | Types de verrous attendus, non mesurés via `pg_locks`. |
| 9 | Reprise après interruption ou échec | NOT TESTED | Transaction unique, annulation attendue. Non testé. |
| 10 | Compatibilité avec les tests PostgreSQL prévus (T1 à T14) | BLOCKED | Tests non écrits ni exécutés. Exécution soumise à autorisation distincte. |
| 11 | Orphelins historiques | BLOCKED | Pas de copie représentative. Requête prévue, non exécutée. |
| 12 | Stratégie d'application en production | NOT TESTED | Ordre DROP+ADD, VALIDATE séparé et `lock_timeout` documentés. Durée et blocage non mesurés. |

## 14. Verdict

**NON VALIDÉE.** Recommandation Phase 1.1 : **NOT READY FOR PHASE 2** (motifs ci-dessous).

Motifs :
1. Aucun test PostgreSQL de V22 n'a été exécuté : aucun PASS sur preuve d'exécution.
2. L'hypothèse centrale (ordre des triggers RI pendant la coexistence CASCADE/RESTRICT) n'est pas prouvée.
3. Le fichier ne suit pas la séquence ADD/VALIDATE/DROP/RENAME du brief initial. L'écart est justifié, mais doit être validé.
4. Les verrous de chaque instruction ne sont pas mesurés.
5. Absence d'index sur `payments(quote_id)` : impact non mesuré.
6. Brief initial non intégralement récupérable, et numérotation des T12 à T14 en conflit (annexe A et F).
7. Volume de production et orphelins inconnus.

Ce verdict ne signifie pas que V22 est incorrecte. Il signifie qu'elle n'est pas démontrée.

## 15. Prochaine étape (attente d'autorisation)

Phase 2 à autoriser séparément, avec au minimum :
- Base : `et_test_db`, hôte `127.0.0.1`, port `15432`, utilisateur `et_test`. Port 5432 interdit.
- Opérations permises : `pg_constraint`, `pg_locks` (lecture seule), tests T1, T2, T12 à T14 sur un schéma isolé.
- Interdit sans autorisation : `scripts/test-backend-postgres.ps1` (qui recrée la base), toute migration Flyway, tout déplacement de V22, tout VALIDATE sur base réelle.

Décisions métier à obtenir avant implémentation : D2, D4, D5b, D6, D3 au niveau base (trigger ou non), et besoin d'un index sur `payments(quote_id)`.

Fichiers modifiés par cette phase : **un seul**, `docs/architecture/v22-validation-report.md` (créé en Phase 1, complété en Phase 1.1). Aucun autre fichier touché. Aucun commit, push ou déploiement.

---

## Annexes Phase 1.1

### A. Statut du brief initial

- **Récupérable partiellement** : le journal de session ne contient que les 253 premières lignes (environ 8 350 caractères) d'un brief de 510 lignes. Le texte est tronqué dans T2, et il est en mojibake (UTF-8 lu en cp1252).
- **Non récupérable** : les 12 critères d'acceptation exacts, le nombre exact de sections attendues (13 ou autre), les définitions complètes de T12 à T14.
- Les libellés de la section 13 sont donc **reconstruits**. Ils ne doivent pas être présentés comme ceux du brief.
- Éléments confirmés par le fragment : sections 1 à 6, étapes A1 à A4 (analyse), B1 à B4 (stratégie V22 : DROP, ADD NOT VALID, VALIDATE séparé, RENAME ; verrous ; transactions ; Flyway), T1 avec les statuts PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED, et T2 (suppression d'un devis sans paiement).

### B. Flyway (preuve)

- Commande : `backend\mvnw.cmd -o dependency:tree` depuis `backend\`, avec `JAVA_HOME` = JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot`).
- Code de sortie : 0. Stderr : vide. Résultat : `BUILD SUCCESS`.
- Dépendances effectives : `spring-boot-starter-flyway:4.1.1` → `spring-boot-flyway:4.1.1` ; `flyway-database-postgresql:12.4.0` → `flyway-core:12.4.0`.
- Statut : **PASS** pour la version résolue. Ce n'est pas une preuve d'exécution de migration.

### C. Revue ligne par ligne de V22

Fichier : `database/proposed/V22__payments_quote_fk_restrict.sql`.

| Élément | Constat | Statut |
|---|---|---|
| `SET LOCAL lock_timeout = '5s'` | Effectif seulement dans une transaction. Hors transaction Flyway (autocommit), sans effet. | Risque, à tester |
| `ALTER TABLE payments DROP CONSTRAINT fk_payments_quote` | Nom cohérent avec V6 ligne 21. Verrou ACCESS EXCLUSIVE sur `payments` jusqu'au COMMIT. | Statique |
| `ADD CONSTRAINT ... ON DELETE RESTRICT NOT VALID` | Pas de scan des lignes existantes. Verrous SHARE ROW EXCLUSIVE sur `payments` et `quotes`. | Statique, non mesuré |
| `VALIDATE CONSTRAINT` | Absent de V22 : séparé volontairement. Un VALIDATE dans la même transaction garderait ACCESS EXCLUSIVE pendant le scan. | Justifié |
| Rollback | Une erreur sur DROP ou ADD annule toute la transaction : l'ancienne FK CASCADE reste en place. | Attendu, non prouvé |
| Flyway | Transaction par migration par défaut sur PostgreSQL. Une interruption brutale ne laisse aucune ligne d'historique. Une erreur capturée écrit une ligne en échec, qui bloque le `migrate` suivant jusqu'à `flyway repair` (non autorisé). | Attendu, non prouvé |

### D. Verrous et sessions : requêtes préparées (NON EXÉCUTÉES)

```sql
-- NON EXÉCUTÉ : verrous sur payments et quotes
SELECT l.locktype, l.mode, l.granted, c.relname, l.pid
FROM pg_locks l
LEFT JOIN pg_class c ON c.oid = l.relation
WHERE c.relname IN ('payments', 'quotes');

-- NON EXÉCUTÉ : sessions bloquées et bloquantes
SELECT pid, pg_blocking_pids(pid) AS blocked_by, state, wait_event_type, left(query, 120)
FROM pg_stat_activity
WHERE cardinality(pg_blocking_pids(pid)) > 0;

-- NON EXÉCUTÉ : transactions longues
SELECT pid, now() - xact_start AS xact_age, state, left(query, 120)
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
ORDER BY xact_age DESC;

-- NON EXÉCUTÉ : deadlocks cumulés
SELECT datname, deadlocks FROM pg_stat_database WHERE datname = current_database();

-- NON EXÉCUTÉ : état de la FK et de la FK invoice_payments
SELECT conname, confdeltype, convalidated
FROM pg_constraint
WHERE conname IN ('fk_payments_quote', 'invoice_payments_payment_id_fkey');

-- NON EXÉCUTÉ : historique Flyway (aucune ligne en échec attendue)
SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

Mesures à consigner pendant la Phase 2 : `pg_locks` avant, pendant et après chaque instruction, avec le `pid` de la session de migration.

### E. Index sur `payments(quote_id)`

- **Aucun index** sur `payments(quote_id)` dans V1 à V20 ni dans le code Java. Seuls existent `idx_invoices_quote` (V7 ligne 51) et `quote_events_quote_time_idx` (V5 ligne 53).
- `PaymentService` lignes 48 et 51 appelle `existsByQuote_IdAndStatusIn`, qui filtre sur `quote_id` sans index.
- Plan de comparaison (Phase 2 uniquement) : `EXPLAIN` sans `ANALYZE` avant et après un index candidat, dans une transaction annulée par `ROLLBACK`. `EXPLAIN ANALYZE DELETE` exécute réellement la suppression : interdit.
- Aucun index créé. Si un index est retenu en production, il doit être créé avec `CREATE INDEX CONCURRENTLY`, hors transaction.

### F. Tests PostgreSQL : conflit de numérotation

Le brief de la Phase 1.1 et `docs/architecture/v22-final-review.md` ne définissent pas les mêmes T12 à T14 :

| Test | `v22-final-review.md` | Brief Phase 1.1 |
|---|---|---|
| T12 | Coexistence CASCADE et RESTRICT | Concurrence suppression devis / insertion paiement |
| T13 | Mesure des verrous avec `lock_timeout` | Rollback du remplacement de FK |
| T14 | Nom de la FK de `invoice_payments` | Interruption et reprise de la migration |

**Décision requise** : confirmer la définition retenue avant Phase 2. Ce rapport ne fusionne pas les deux listes. Les tests ci-dessous suivent le brief de la Phase 1.1. La coexistence CASCADE/RESTRICT (définition de `v22-final-review.md`) n'a pas de fiche dédiée : elle n'est pas couverte tant que la décision n'est pas prise.

Fiche de test (8 champs chacune) :

**T1 — suppression d'un devis avec paiement**
1. Objectif : vérifier qu'un devis ayant un paiement ne peut pas être supprimé après V22.
2. Préconditions : `et_test_db` isolé, V22 appliquée dans un schéma de test, aucun autre client.
3. Connexions : 1.
4. Données : un devis, un paiement par statut (PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED).
5. SQL : `DELETE FROM quotes WHERE id = :id` dans une transaction annulée. Vérifier SQLSTATE `23503`.
6. Attendu : `23503` pour tous les statuts après V22. Avant V22, la suppression réussit et supprime les paiements (preuve du défaut).
7. Nettoyage : `ROLLBACK`, puis suppression du schéma de test.
8. Preuve : sortie console avec SQLSTATE, un résultat par statut.

**T2 — suppression d'un devis sans paiement**
1. Objectif : vérifier qu'un devis sans paiement reste supprimable (décision D1).
2. Préconditions : identiques à T1.
3. Connexions : 1.
4. Données : un devis sans paiement **et sans `quote_events`**. `quote_events.quote_id` (V5 ligne 44) n'a pas de clause ON DELETE : la suppression échouerait pour une raison sans rapport. Ce cas doit être explicite.
5. SQL : `DELETE FROM quotes WHERE id = :id`.
6. Attendu : succès (1 ligne supprimée).
7. Nettoyage : `ROLLBACK`.
8. Preuve : nombre de lignes supprimées.

**T12 — concurrence entre suppression du devis et insertion du paiement**
1. Objectif : vérifier qu'il n'existe pas de paiement orphelin ni de paiement créé sur un devis supprimé.
2. Préconditions : devis existant, `lock_timeout` défini sur les deux sessions.
3. Connexions : 2 (session A : `DELETE` du devis ; session B : `INSERT` du paiement).
4. Données : un devis, une clé d'idempotence distincte pour B.
5. SQL : A ouvre une transaction, `DELETE FROM quotes`, sans COMMIT. B exécute `INSERT INTO payments (quote_id, ...)`. Puis A fait COMMIT ou ROLLBACK.
6. Attendu : B est bloquée ou échoue avec une erreur de FK (`23503`). Aucun paiement sans devis en fin de test.
7. Nettoyage : `ROLLBACK` des deux sessions.
8. Preuve : `pg_blocking_pids` pendant l'attente, SQLSTATE final, requête `payments` sans orphelin.

**T13 — rollback du remplacement de FK**
1. Objectif : prouver qu'un échec pendant V22 laisse l'ancienne FK CASCADE en place.
2. Préconditions : session B détient un verrou sur `payments` (par exemple `LOCK TABLE payments IN ACCESS SHARE MODE` dans une transaction ouverte).
3. Connexions : 2.
4. Données : aucune donnée spécifique.
5. SQL : exécuter V22 dans la session A. Le DROP attend le verrou et dépasse `lock_timeout` de 5 s. Vérifier `pg_constraint` après l'échec.
6. Attendu : erreur de timeout, `fk_payments_quote` toujours `confdeltype = 'c'` (CASCADE).
7. Nettoyage : `ROLLBACK` de B, suppression du schéma de test.
8. Preuve : message d'erreur, requête `pg_constraint`, historique Flyway.

**T14 — interruption et reprise de la migration**
1. Objectif : prouver qu'une migration interrompue peut être relancée sans état corrompu.
2. Préconditions : schéma de test avec V1 à V20 appliquées.
3. Connexions : 1, plus un moyen d'interrompre (annulation de la session via `pg_terminate_backend` sur le `pid` de la migration).
4. Données : aucune.
5. SQL : lancer V22, puis terminer le backend pendant la transaction. Vérifier l'état, puis relancer.
6. Attendu : aucune ligne d'historique pour V22 après interruption, FK inchangée, relance réussie.
7. Nettoyage : suppression du schéma de test.
8. Preuve : `flyway_schema_history` avant et après, `pg_constraint` avant et après.

Note : `pg_terminate_backend` est une action destructive pour la session ciblée. Elle n'est permise que sur un `pid` du schéma de test, avec autorisation explicite.

### G. Script `scripts/test-backend-postgres.ps1` (lu, non exécuté)

- Ligne 36 : vérifie que le port 15432 écoute.
- Ligne 44 : `DROP DATABASE IF EXISTS $dbName WITH (FORCE)` puis `CREATE DATABASE $dbName` : **destructif**, interdit.
- Ligne 60 : `mvnw -B -ntp -Ppostgres-test verify`.
- Ligne 67 : nettoyage des variables d'environnement.

Méthode ciblée proposée (Phase 2, sur autorisation) : une commande Maven `-Dtest=PaymentQuoteCapturePostgresIT` avec `ELITE_POSTGRES_TEST=true` et `DATABASE_URL` pointant vers un schéma dédié, sans DROP DATABASE. Préalable : vérifier l'état Flyway de `et_test_db` en lecture seule, car Flyway migre au démarrage de Spring.

Attention : `MigrationWithExistingDataIT` ligne 350 exécute aussi un `DROP DATABASE ... WITH (FORCE)`. Il ne doit pas être lancé sur `et_test_db`.

### H. Doublon V11

| Élément | Copie active | Copie inactive |
|---|---|---|
| Chemin | `database/migrations/V11__create_booking_driver_assignments.sql` | `backend/src/main/resources/db/migration/V11__create_booking_driver_assignments.sql` |
| Suivi Git | Oui | Oui (commit `2080a58`) |
| Packagée | Oui (copiée dans `db/migration` par `backend/pom.xml`) | Non (exclue par `db/migration/**`) |
| Ajouts de contraintes | Bloc `DO $$` avec vérification `pg_constraint` | `ALTER TABLE ... ADD CONSTRAINT` nus |
| En-tête | Variante actuelle | Variante plus ancienne, « non-destructive » |

Risque : si l'exclusion Maven est retirée, Flyway verra deux versions V11 et échouera. Risque de confusion future : un lecteur peut modifier la copie inactive. Aucun fichier supprimé ou déplacé. Suppression du doublon : sur autorisation seulement.

### Commandes Phase 2 (à exécuter seulement après autorisation explicite)

Dans l'ordre :
1. `git status --short` : vérifier que les fichiers modifiés et non suivis sont inchangés.
2. Lecture seule sur `et_test_db` (127.0.0.1:15432, utilisateur `et_test`) : état Flyway et `pg_constraint`, sans afficher le mot de passe.
3. Création d'un schéma de test isolé, dédié à la Phase 2 (autorisation requise).
4. Tests T1, T2 sur ce schéma, puis T12, T13, T14.
5. `backend\mvnw.cmd -B -ntp -o -Dtest=PaymentQuoteCapturePostgresIT test` avec JDK 21 et les variables PostgreSQL de test, uniquement si la cible est confirmée.
6. Nettoyage du schéma de test, puis `git status --short` pour prouver l'absence de modification du code.

### Recommandation

**NOT READY FOR PHASE 2.**

Conditions de passage :
1. Confirmer la définition de T12 à T14 (annexe F).
2. Fournir le brief complet, ou confirmer que la version reconstruite suffit.
3. Autoriser explicitement la Phase 2 avec la cible, les tests et les limites.
4. Décider du traitement du doublon V11 (annexe H), séparément.

### Conclusion Phase 1.1

- **Fichiers consultés** : `database/proposed/V22__payments_quote_fk_restrict.sql`, `database/proposed/V21__payments_one_captured_per_quote.sql`, `database/migrations/V5`, `V6`, `V7`, `V11`, `backend/src/main/resources/db/migration/V11` et `_DISABLED_*`, `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`, `scripts/test-backend-postgres.ps1`, `backend/.../PaymentService.java`, `InvoiceService.java`, `PaymentQuoteCapturePostgresIT.java`, `MigrationWithExistingDataIT.java`, `docs/architecture/v22-final-review.md`, `docs/architecture/payment-history-integrity-review.md`, journal de session (brief tronqué).
- **Fichiers modifiés** : `docs/architecture/v22-validation-report.md` uniquement.
- **Vérifications réalisées** : version Flyway avec JDK 21 (PASS), revue statique de V22, recherche d'index, lecture du script, comparaison V11. Aucune requête SQL, aucune connexion PostgreSQL, aucun Flyway, aucune migration déplacée.
- **Non vérifiable** : brief complet, comportement réel des verrous, ordre des triggers RI, plan d'exécution, volume de production, orphelins historiques.
- **Risques bloquants** : tests PostgreSQL non exécutés, numérotation T12 à T14 en conflit, script destructif, `SET LOCAL` inefficace hors transaction.
- **Recommandation** : **NOT READY FOR PHASE 2**.

Attente de votre autorisation explicite avant tout test PostgreSQL.

---

## Phase 1.2 — Clarification finale avant validation PostgreSQL de V22

Mission documentaire uniquement : aucune connexion PostgreSQL, aucun SQL, aucun test, aucun Flyway, aucune modification de code ou de schéma. Seul ce rapport est mis à jour.

### 1.2-A. Brief initial : traçabilité

Recherche effectuée : dépôt (`docs/`, `database/`), historique Git (`git log --all` sur `docs/architecture` et `database/proposed`, et recherche des messages « V22 » ou « brief »).

Résultat : **aucun document contenant le brief original n'existe dans le dépôt ni dans l'historique Git**. Les seuls commits trouvés sont des commits automatiques « Agent host session … », qui ne contiennent pas le brief. Le journal de session ne contient qu'un extrait tronqué.

| Élément | Brief original | Phase 1.1 | Écart | Statut |
|---|---|---|---|---|
| Texte complet de la mission | Introuvable | Fragment de 253 lignes sur 510 | Inconnu | BLOCKED |
| 12 critères d'acceptation (libellés exacts) | Introuvable | Libellés reconstruits (section 13) | Non vérifiable | PENDING DECISION |
| 13 sections attendues du rapport | Introuvable | Rapport en 15 sections | Non vérifiable : la structure réelle du rapport ne peut pas être comparée au brief | PENDING DECISION |
| T1 | Fragment : suppression d'un devis avec paiement, statuts PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED | Même intention (section F) | Définition de `v22-final-review.md` différente (T1 = suppression d'un paiement CAPTURED) | PENDING DECISION |
| T2 | Fragment : suppression d'un devis sans paiement | Même intention (section F) | `v22-final-review.md` : T2 = suppression d'un paiement FAILED | PENDING DECISION |
| T12 à T14 | Introuvables dans le fragment | Section F | Voir 1.2-B | PENDING DECISION |

Conclusion : le brief ne peut pas être confirmé. Les libellés des critères de la section 13 ne doivent pas être présentés comme ceux du brief. Aucune version supposée du brief n'est reconstruite ici.

### 1.2-B. T12, T13 et T14 : comparaison des définitions

Quatre sources donnent des définitions différentes :

- **S1 — Brief initial** : introuvable pour T12 à T14.
- **S2 — `v22-final-review.md`** (table des tests, section 9).
- **S3 — `v22-validation-report.md` section 9 (Phase 1)**.
- **S4 — Phase 1.1, section F du présent rapport**.

| Test | S2 | S3 | S4 |
|---|---|---|---|
| T12 | Coexistence des deux FK (CASCADE et RESTRICT), puis `DELETE` d'un devis avec paiement CAPTURED. Prouve le risque de la séquence rejetée. | Prouver ou infirmer l'ordre des triggers RI pendant la coexistence. | Concurrence entre suppression du devis et insertion du paiement (2 sessions). |
| T13 | Mesure du verrou : `BEGIN; SELECT … FOR UPDATE` sur `payments` pendant le `DROP` avec `lock_timeout` ; le `DROP` échoue après 5 s, l'ancienne FK reste. | Suppression de devis avec et sans paiement (historique). | Rollback du remplacement de FK. |
| T14 | `pg_constraint` : nom réel de la FK `payment_id` de `invoice_payments`. Lecture seule. | Suppression de devis avec et sans paiement (historique). | Interruption et reprise de la migration. |

Éléments communs : aucune des sources n'est vérifiée par exécution. S3 reprend la même intention que S2 pour T13 et T14 sans en donner la même définition. Les numéros de test sont donc **incompatibles** entre les documents.

Détail par scénario (1 à 8 demandés) :

**T12**

| Champ | S2 | S3 | S4 |
|---|---|---|---|
| Objectif | Prouver que la coexistence CASCADE/RESTRICT est dangereuse | Idem | Prouver l'absence de paiement orphelin ou créé sur un devis supprimé |
| Opérations | Deux FK sur `payments.quote_id`, DELETE quote | Idem | DELETE quote (session A), INSERT payment (session B) |
| Connexions | Au moins 1 | Au moins 1 | 2 |
| Préconditions | État intermédiaire avec deux FK (ne se produit pas avec la séquence retenue) | Idem | Devis existant, `lock_timeout` défini |
| Résultat attendu | Dépend de l'ordre des triggers (non prouvé) | Non précisé | Une session échoue ou attend, aucun orphelin |
| Preuves | Résultat du DELETE | Non précisé | `pg_blocking_pids`, SQLSTATE, requête d'orphelins |
| Risques | Faux négatif si l'état intermédiaire n'est jamais atteint | Idem | Non couvert par V22 seule : la concurrence dépend du verrou sur `payments` |

**T13**

| Champ | S2 | S3 | S4 |
|---|---|---|---|
| Objectif | Mesurer le verrou et prouver l'absence d'état partiel après timeout | Historique de suppression | Prouver que l'échec de la migration laisse l'ancienne FK |
| Opérations | Transaction B tient `payments`, migration A fait DROP | Suppression de devis | Migration qui échoue à la suite d'une erreur |
| Connexions | 2 | 1 | 2 |
| Préconditions | `lock_timeout` 5 s effectif | Données de test | Verrou tenu par B |
| Résultat attendu | Timeout après 5 s, FK CASCADE présente | Non précisé | Rollback, FK CASCADE présente |
| Preuves | Durée, message d'erreur, `pg_constraint` | Non précisé | `pg_constraint` et historique Flyway |
| Risques | Le verrou du test doit être réel (`FOR UPDATE`) et non simulé | Idem | Une erreur de timeout peut ne pas être une erreur de contrainte |

**T14**

| Champ | S2 | S3 | S4 |
|---|---|---|---|
| Objectif | Confirmer le nom réel de la FK de `invoice_payments` | Historique de suppression | Vérifier la reprise après interruption |
| Opérations | Lecture de `pg_constraint` | Suppression de devis | Terminer le backend de migration puis relancer |
| Connexions | 1 | 1 | 1 + moyen d'interruption |
| Préconditions | Base avec V1 à V20 | Données | Migration en cours |
| Résultat attendu | Nom `invoice_payments_payment_id_fkey` ou autre | Non précisé | Pas de ligne d'historique V22, relance réussie |
| Preuves | Résultat de la requête | Non précisé | `flyway_schema_history` et `pg_constraint` avant et après |
| Risques | Le nom par défaut n'est qu'une hypothèse (V7 ligne 46 : nom non fixé) | Idem | Interruption brutale ou échec capturé par Flyway, deux cas différents |

Les tests T1 et T2 divergent aussi : S2 = paiement CAPTURED et paiement FAILED ; S4 = devis avec paiement (tous statuts) et devis sans paiement. Il faut arbitrer aussi ces deux numéros.

**Proposition de classification (à titre de comparaison, non adoptée)**

| Risque | Couvert par S2 | Couvert par S4 | Commentaire |
|---|---|---|---|
| Concurrence suppression / insertion | Non | Oui (T12) | Seul S4 le couvre. S2 ne couvre que la coexistence. |
| Rollback du remplacement de FK | Oui (T10 : rollback si ADD échoue ; T13 : verrou) | Oui (T13) | Deux approches différentes : erreur forcée ou timeout de verrou. |
| Interruption et reprise | Non | Oui (T14) | Seul S4 le couvre. |

Décision : **PENDING DECISION**. Aucune définition n'est retenue arbitrairement. L'auteur doit trancher entre S2 et S4, ou fournir le brief original.

### 1.2-C. V22 et `lock_timeout`

SQL lu ligne par ligne (`database/proposed/V22__payments_quote_fk_restrict.sql`) : `SET LOCAL lock_timeout = '5s'` ; `ALTER TABLE payments DROP CONSTRAINT fk_payments_quote` ; `ALTER TABLE payments ADD CONSTRAINT fk_payments_quote FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE RESTRICT NOT VALID`. Aucun `VALIDATE`, aucun `CONCURRENTLY`, aucun `COMMIT` explicite.

**Transaction Flyway (preuve documentaire, non prouvée à l'exécution)**
- `backend/src/main/resources/application.yml` (lignes 14-16) : `flyway.enabled: true`, `locations: classpath:db/migration`. Aucune clé `executeInTransaction` ni `mixed` dans `application.yml`, `application-test.yml` ou `backend/pom.xml`. La valeur par défaut s'applique.
- Documentation Flyway (source secondaire, résumé d'une recherche web, source primaire non ouverte) : `executeInTransaction` vaut `true` par défaut. Flyway passe en non transactionnel seulement si une instruction l'exige (par exemple `CREATE INDEX CONCURRENTLY`). V22 ne contient aucune de ces instructions : elle reste transactionnelle.
- Conclusion : V22 s'exécute dans une transaction Flyway. C'est une **hypothèse documentaire**, à confirmer par le test T13 ou par un journal de migration.

**Effet de `SET LOCAL`**
- Dans une transaction : effectif jusqu'à la fin de la transaction, donc pour les deux `ALTER TABLE`. Documentation PostgreSQL : `SET LOCAL` ne vaut que pour la transaction courante.
- Hors transaction (autocommit) : `SET LOCAL` ne produit aucun effet et PostgreSQL émet un avertissement. Le `lock_timeout` ne s'appliquerait alors à aucune instruction de V22.
- Cas à risque : si Flyway passait en mode non transactionnel (par exemple via une future directive), `lock_timeout` serait sans effet et `DROP` attendrait indéfiniment. Ce cas est absent aujourd'hui, mais il n'est pas protégé par un test.

**Couverture du timeout**
- `lock_timeout` s'applique à toute attente de verrou de la session, donc aux verrous pris par `DROP` (sur `payments`, et une prise sur `quotes` à confirmer) et par `ADD` (`SHARE ROW EXCLUSIVE` sur `payments` et `quotes`).
- Il ne s'applique pas à l'attente de verrous de ligne, qui n'intervient pas ici.
- En cas de dépassement : erreur `55P03` (lock not available), rollback de toute la transaction, l'ancienne FK reste. Flyway enregistre alors un échec dans `flyway_schema_history` et bloque le `migrate` suivant tant qu'il n'y a pas de `flyway repair`, non autorisé.

**Fenêtre sans protection référentielle**
- Les autres transactions voient l'état committé (ancienne FK CASCADE) jusqu'au COMMIT. L'état intermédiaire (aucune FK) n'est pas visible.
- Une écriture concurrente sur `payments` attend le verrou `ACCESS EXCLUSIVE` posé par `DROP`. Une écriture déjà en cours avant le `DROP` bloque le `DROP` jusqu'à son COMMIT.
- Donc aucune fenêtre visible n'existe pour `payments`. Cette conclusion repose sur les règles de verrouillage de PostgreSQL, pas sur un test : **hypothèse à prouver par T13 (S4)**.

**Compatibilité**
- `NOT VALID` existe depuis PostgreSQL 9.4. PostgreSQL 18 est indiqué comme version du serveur de test dans `v22-final-review.md`, mais la version réelle du serveur n'a pas été interrogée (aucune connexion autorisée) : **NOT TESTED**.
- Flyway 12.4.0 (vérifié en Phase 1.1 par `dependency:tree`) : compatible avec ce SQL, aucune directive particulière.

**Décisions sur le timeout**
- Ne pas supprimer ni modifier le `lock_timeout` sans analyse : il évite une attente en file sur `payments`, qui bloquerait toutes les écritures.
- Mais le timeout n'est efficace que dans la transaction Flyway. La condition doit donc être prouvée (T13) avant de s'appuyer dessus en production.

Verdict sur cette partie : **NOT TESTED** pour l'efficacité réelle de `lock_timeout` ; **PASS** pour la lecture du SQL et de la configuration.

### 1.2-D. Spécification des tests PostgreSQL (non exécutés)

Identifiants propres à cette spécification (**P-xx**), pour éviter de reprendre les numéros en conflit. Chaque test est à aligner sur une des définitions, après décision. Aucune simulation Java ou H2 n'est une preuve : tous les tests s'exécutent sur PostgreSQL réel isolé.

**Comportement des contraintes**

| ID | Objectif | Risque | Préconditions | Données dédiées | Connexions | Séquence | Attendu | Échec attendu avant V22 | Intégrité | Nettoyage |
|---|---|---|---|---|---|---|---|---|---|---|
| P-01 | Protection d'un devis avec paiement CAPTURED | Perte d'historique financier | V1-V20 + V22 sur schéma de test | 1 devis, 1 paiement CAPTURED | 1 | DELETE quote dans transaction, puis ROLLBACK | SQLSTATE 23503 | Suppression réussie (preuve du défaut) | Paiement toujours présent | ROLLBACK, puis DROP du schéma de test |
| P-02 | Idem pour chaque statut | Idem | Idem | 1 devis par statut (PENDING, AUTHORIZED, FAILED, REFUNDED, CANCELLED) | 1 | DELETE par devis | 23503 pour les statuts historiques | Succès | Paiement toujours présent | Idem |
| P-03 | Devis sans paiement supprimable (D1) | Blocage injustifié | Idem | 1 devis sans paiement et sans `quote_events` | 1 | DELETE | 1 ligne supprimée | Succès | Aucun orphelin | Idem |
| P-04 | Suppression directe de paiement (couverture D3) | Suppression d'un paiement CAPTURED | Idem | 1 paiement CAPTURED avec `payment_events` | 1 | DELETE payments | Résultat dépend de D3 : cascade vers `payment_events` et `invoice_payments` (non couvert par V22) | Idem | Compte de `payment_events` | Idem |
| P-05 | Nom réel des FK | Migration ciblant un nom inexistant | Base V1-V20 | Aucune | 1 | Lecture `pg_constraint` (`conname`, `confdeltype`, `convalidated`) | `fk_payments_quote` existe ; nom réel de la FK `invoice_payments` relevé | Non applicable | Lecture seule | Aucun |

**Concurrence**

| ID | Objectif | Risque | Préconditions | Données | Connexions | Séquence | Attendu | Échec attendu | Intégrité | Nettoyage |
|---|---|---|---|---|---|---|---|---|---|---|
| P-06 | Suppression du devis pendant l'insertion d'un paiement | Paiement orphelin ou créé sur devis supprimé | V22 appliquée | 1 devis | 2 | A : DELETE quote (sans COMMIT). B : INSERT payment. A : COMMIT. | B bloquée puis erreur 23503, ou B committée avant A et A échoue en 23503 | Deux paiements, ou orphelin | Aucun paiement sans devis | ROLLBACK des deux sessions |
| P-07 | Deux paiements CAPTURED en concurrence (rappel de V21) | Double capture | V21 appliquée | 1 devis, 2 clés | 2 | Deux INSERT CAPTURED simultanés | Une seule réussite (contrainte unique) | Deux CAPTURED | Un seul CAPTURED | ROLLBACK |

**Rollback**

| ID | Objectif | Risque | Préconditions | Données | Connexions | Séquence | Attendu | Échec attendu | Intégrité | Nettoyage |
|---|---|---|---|---|---|---|---|---|---|---|
| P-08 | Échec de DROP (timeout) : rollback | État partiel | V1-V20, verrou tenu | Aucune | 2 | B : `LOCK TABLE payments IN ACCESS SHARE MODE`. A : V22. | Timeout après 5 s, FK CASCADE présente | FK absente ou RESTRICT | `pg_constraint` : `confdeltype = 'c'` | ROLLBACK de B, DROP du schéma |
| P-09 | Échec de ADD après DROP (erreur forcée dans la même transaction) | État partiel | Idem | Idem | 1 | Migration avec instruction ajoutée en test pour échouer après le DROP | Rollback complet, FK CASCADE présente | Absence de FK | Idem | Idem |

**Interruption et reprise**

| ID | Objectif | Risque | Préconditions | Données | Connexions | Séquence | Attendu | Échec attendu | Intégrité | Nettoyage |
|---|---|---|---|---|---|---|---|---|---|---|
| P-10 | Arrêt brutal pendant la transaction | Migration incomplète sans trace | V1-V20 | Aucune | 2 (migration + contrôle) | Lancer V22, `pg_terminate_backend` du pid de migration, vérifier | Aucune ligne V22 dans `flyway_schema_history`, FK CASCADE, relance réussie | Ligne en échec ou FK RESTRICT partielle | `flyway_schema_history` et `pg_constraint` avant et après | DROP du schéma de test |
| P-11 | Échec capturé par Flyway | Ligne en échec bloquant | Idem | Idem | 1 | Provoquer l'échec de P-08 | Ligne en échec écrite, `migrate` bloqué jusqu'à `repair` | Migration réussie à tort | `flyway_schema_history.success` | Suppression du schéma de test |

**Flyway**

| ID | Objectif | Risque | Préconditions | Données | Connexions | Séquence | Attendu | Échec attendu | Intégrité | Nettoyage |
|---|---|---|---|---|---|---|---|---|---|---|
| P-12 | V1 à V22 sur base vide | Migration non rejouable | Base vide de test | Aucune | 1 | `flyway migrate` | Succès, historique complet | Erreur de version ou de script | `ddl-auto: validate` passe | DROP du schéma de test |
| P-13 | Pas de V21 ni V22 dans le classpath | Application accidentelle | Packaging | Aucune | 0 | Inspection du JAR | Absentes de `db/migration` | Présentes | Lecture seule | Aucun |

**Performance (non prouvées ici)**

| ID | Objectif | Risque | Préconditions | Données | Connexions | Séquence | Attendu | Échec | Intégrité | Nettoyage |
|---|---|---|---|---|---|---|---|---|---|---|
| P-14 | Durée du VALIDATE et blocage des écritures | Blocage en production | Copie représentative | Volume réel (non disponible) | 2 | VALIDATE + écriture concurrente | Durée mesurée, écriture bloquée au plus X s | Blocage long | Compte d'orphelins | Aucun |

Les tests P-12 à P-14 et la vérification `ddl-auto: validate` ne peuvent être exécutés que sur base isolée, sous autorisation distincte. Les tests Java `application-test.yml` (H2, Flyway désactivé) ne prouvent rien sur les contraintes PostgreSQL.

### 1.2-E. Index sur `payments.quote_id`

- **Rôle dans les vérifications de référence** : à chaque suppression de devis, PostgreSQL exécute une requête du type `SELECT 1 FROM payments WHERE quote_id = $1 FOR KEY SHARE`, pour chaque devis supprimé. Sans index, cette requête lit toute la table `payments`.
- **Index V21 non utilisable pour ce contrôle** : `uq_payments_one_captured_per_quote` est partiel (`WHERE status = 'CAPTURED'`). PostgreSQL ne peut l'utiliser que si le prédicat de la requête implique `status = 'CAPTURED'`, ce qui n'est pas le cas du contrôle référentiel.
- **Intégrité et performance** : l'intégrité ne dépend pas de l'index (les contraintes restent correctes sans lui). La performance en dépend : suppression de nombreux devis, ou `existsByQuote_IdAndStatusIn` de `PaymentService` (lignes 48 et 51), exécutés sans index.
- **Coût** : un index supplémentaire ralentit les écritures sur `payments` et consomme de l'espace. En production, il doit être créé avec `CREATE INDEX CONCURRENTLY`, hors transaction, ce qui exclut Flyway transactionnel par défaut (à traiter comme migration non transactionnelle).
- **Mesure en Phase 2 (sans créer d'index)** : `EXPLAIN` (sans `ANALYZE` sur un `DELETE`) sur la requête de contrôle, avant et après un index candidat, dans une transaction annulée. Mesures complémentaires : `pg_stat_user_tables` (`seq_scan`, `seq_tup_read`) sur `payments`, et durée d'une suppression de devis de test.

**Recommandation distincte (non une décision d'implémentation)** : étudier un index non partiel sur `payments(quote_id)` après mesure. Il ne remplace pas V21 et n'est pas nécessaire à la correction de V22.

### 1.2-F. V11 (tâche indépendante)

Documenté en annexe H. Chemins : active `database/migrations/V11__create_booking_driver_assignments.sql`, inactive `backend/src/main/resources/db/migration/V11__create_booking_driver_assignments.sql`. Exclusion packaging : `backend/pom.xml` (`db/migration/**`). Aucun fichier modifié, supprimé ou déplacé. Son traitement est une tâche distincte qui nécessite une autorisation propre.

### 1.2-G. Faits, analyses, hypothèses et décisions

| Catégorie | Éléments |
|---|---|
| Faits prouvés par les fichiers | SQL V22 (contenu exact) ; `fk_payments_quote` cité en V6 ligne 21 ; configuration Flyway (`application.yml` lignes 14-16, aucune directive transactionnelle) ; absence d'index sur `quote_id` (recherche dans les migrations et le code) ; Flyway 12.4.0 (Phase 1.1) ; V21 partiel sur CAPTURED ; quatre numérotations de T12 à T14 |
| Analyses techniques | Comportement de `SET LOCAL` ; couverture des verrous par `lock_timeout` ; visibilité des états intermédiaires ; rôle de l'index pour le contrôle référentiel |
| Hypothèses | Transaction Flyway effective pour V22 ; ordre des triggers RI ; absence de fenêtre visible ; nom par défaut de la FK de `invoice_payments` ; version PostgreSQL 18 du serveur |
| Décisions humaines nécessaires | Définition retenue de T1, T2, T12, T13, T14 ; décision sur la séquence rejetée ; D2, D3 (niveau base), D4, D5b, D6 ; traitement du doublon V11 ; besoin d'un index sur `quote_id` |
| Non vérifiable ici | Brief original ; version du serveur ; comportement réel des verrous ; durée de VALIDATE ; volume de production ; orphelins historiques ; efficacité réelle du timeout |

### 1.2-H. Décision finale

| Condition | Résultat | Justification |
|---|---|---|
| Brief initial retrouvé | **BLOCKED** | Absent du dépôt, de l'historique Git et du journal complet |
| Critères d'acceptation confirmés | **PENDING DECISION** | Libellés reconstruits, non confirmés |
| T12 défini sans ambiguïté | **PENDING DECISION** | Quatre définitions (S2, S3, S4 ; S1 introuvable) |
| T13 défini sans ambiguïté | **PENDING DECISION** | Trois définitions différentes |
| T14 défini sans ambiguïté | **PENDING DECISION** | Trois définitions différentes |
| Stratégie V22 documentée | **PASS** (lecture de fichier uniquement) | SQL et séquence décrits dans le rapport ; non exécutés |
| `lock_timeout` vérifié | **NOT TESTED** | Analyse documentaire ; efficacité réelle non prouvée |
| Tests PostgreSQL spécifiés | **PASS** (spécification écrite) | Spécification P-01 à P-14 ; aucun test exécuté |
| Risques bloquants identifiés | **PASS** | Voir section 12 et 1.2-G |

**Verdict : NOT READY FOR PHASE 2.**

Ce verdict signifie que la préparation documentaire n'est pas suffisante pour envisager une autorisation de tests. Il ne signifie pas que V22 est validée ou prête pour la production.

Pour passer à READY FOR PHASE 2 : (1) trancher les définitions de T1, T2 et T12 à T14, ou fournir le brief original ; (2) confirmer la séquence de tests retenue (P-xx ou définitions de l'auteur). Une autorisation de test reste nécessaire ensuite.

### 1.2-I. Compte rendu

1. **Documents consultés** : `database/proposed/V22__payments_quote_fk_restrict.sql` ; `database/proposed/V21__payments_one_captured_per_quote.sql` ; `docs/architecture/v22-final-review.md` ; `docs/architecture/v22-validation-report.md` ; `backend/src/main/resources/application.yml` ; `backend/pom.xml` (recherche Flyway) ; historique Git en lecture seule ; une recherche web sur `executeInTransaction` (source secondaire).
2. **Fichiers modifiés** : `docs/architecture/v22-validation-report.md` uniquement.
3. **Ambiguïtés résolues** : aucune définition de T12 à T14 n'a été choisie arbitrairement. Le comportement de `SET LOCAL` hors transaction est établi (documentation PostgreSQL). La configuration Flyway ne contient aucune directive transactionnelle.
4. **Ambiguïtés non résolues** : brief original ; définitions de T1, T2, T12, T13, T14 ; version exacte du serveur ; transaction Flyway réelle de V22 ; ordre des triggers RI.
5. **Décisions humaines nécessaires** : arbitrage des définitions de tests ; choix de la séquence de tests (P-xx ou définitions de l'auteur) ; décisions D2, D3, D4, D5b, D6 ; doublon V11 ; besoin d'un index sur `payments.quote_id`.
6. **Scénarios de tests proposés** : P-01 à P-14 (section 1.2-D). Les scénarios concurrence, rollback, interruption et reprise sont couverts par P-06 à P-11.
7. **Risques résiduels** : fenêtre de verrouillage non mesurée ; `lock_timeout` sans effet si la migration n'est pas transactionnelle ; RI sans index sur `quote_id` ; échec Flyway bloquant ; script destructif à ne pas lancer ; `VALIDATE` non mesuré.
8. **Verdict final : NOT READY FOR PHASE 2.**

Attente d'une autorisation distincte avant toute connexion ou opération PostgreSQL.

---

## Phase 1.3 — Référentiel de décision V22

**Référence :** [v22-decision-register.md](./v22-decision-register.md) est désormais la référence unique de préparation de V22 pour les tests et décisions à venir.

**Périmètre :** documentaire uniquement. Aucune connexion PostgreSQL, aucun SQL, aucun test, aucune commande Flyway, aucune modification de migration, de code ou de fichier existant.

**Effet sur les conclusions historiques :** les conclusions des Phases 1, 1.1 et 1.2 ne sont pas réécrites. Elles restent des constats datés. Le registre les reprend dans sa section 10 (statut de chaque conclusion historique). Les définitions T1, T2, T12, T13 et T14 du registre ne remplacent les définitions antérieures qu'après adoption explicite par l'utilisateur.

**Statut des points clés :**

| Point | Statut dans le registre |
|---|---|
| Faits confirmés | Section 2 (F-01 à F-23), avec références fichier/ligne |
| Hypothèses | Section 3 (H-01 à H-10), non prouvées |
| Décisions techniques proposées | Section 4 (DT-01 à DT-07), aucune adoptée |
| Décisions métier | Section 5 : D1 et D3 conservées pour préparation ; D2, D4, D5b, D6 ouvertes |
| Tests unifiés | Section 6 : T1, T2, T12, T13, T14 et tests complémentaires, non exécutés |
| Index sur `payments.quote_id` | Section 7 : sujet de performance, méthode EXPLAIN, aucun index créé |
| `lock_timeout` | Section 7 : SET LOCAL vs SET SESSION, effet non prouvé |
| Doublon V11 | Tâche distincte, aucune modification |
| FK `invoice_payments` | Nom non supposé, vérification prévue en Phase 2 |

**Verdict Phase 1.3 : NOT READY FOR PHASE 2.** Ce verdict signifie que le référentiel n'est pas encore suffisant pour autoriser une exécution sûre. Il ne signifie pas que V22 est validée ou prête pour la production.

**Conditions de passage à READY FOR PHASE 2 :**
1. L'utilisateur adopte ou corrige les définitions T1, T2, T12, T13 et T14 du registre.
2. Le cas T2c (événements de devis) et la décision D2 sont tranchés.
3. Un périmètre explicite de Phase 2 est donné : base cible isolée, schéma dédié, tests autorisés, interdictions.

**Rappel :** aucune connexion PostgreSQL ni aucune opération sur la base n'est autorisée avant une autorisation distincte.


