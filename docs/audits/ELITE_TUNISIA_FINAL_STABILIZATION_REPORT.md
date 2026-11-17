# Elite Tunisia — Rapport final de stabilisation

Verdict final : **NON PRÊT POUR LA PRODUCTION**. Statut : **NON FINALISÉ — BLOCAGE EXTERNE** (PostgreSQL 15432).

Aucun commit, aucun push, aucune suppression de données, de migrations ou de bases. Le port 5432 n'a pas été utilisé pour les tests.

## 1. État général

| Zone | Statut | Preuve |
|---|---|---|
| Backend Maven (JDK 21) | Verts hors tests PostgreSQL | BUILD SUCCESS, exit 0, 340 tests, 0 échec, 0 erreur, 16 ignorés |
| Frontend Angular (Node 22.22.3) | Vert | 17 fichiers, 107/107, exit 0 |
| PostgreSQL réel (15432) | BLOQUÉ | Port 15432 non joignable (`TcpTestSucceeded: False`), aucun listener |
| Concurrence, idempotence, transactions réelles | BLOQUÉ | Dépendent de PostgreSQL 15432 |
| Migrations sur base vierge | BLOQUÉ | Dépendent de PostgreSQL 15432 |
| Sauvegarde / restauration | BLOQUÉ | Aucun environnement de test dédié disponible |

## 2. Fonctionnalités

- **Terminé et validé (tests non-PostgreSQL verts)** : authentification et cookie refresh (Max-Age), pagination admin via `AdminPageRequest` (liste whitelistée, taille bornée), filtres de date des devis (binding serveur vérifié), correction ISS-02 du pickup, idempotence des paiements côté applicatif (tests H2 uniquement, non équivalents à PostgreSQL).
- **Implémenté mais à valider sur PostgreSQL** : verrous Véhicule → Réservation → Devis, idempotence des paiements par client et devis, contraintes V21 à V32.
- **Incomplet / à décider** : pénalités, remboursements, paiements CAPTURED après annulation, acomptes et soldes, unicité V21 (`payments_one_captured_per_quote`). Décisions métier en attente, non modifiées.

## 3. Corrections réalisées dans cette phase

1. **Frontend — filtre de date des devis** ([quotes-list.component.ts](/C:/Users/aaziza/Desktop/test/frontend/src/app/features/quotes/quotes-list.component.ts)) : si la politique métier (fuseau) n'est pas chargée, un filtre de date affiche « Fuseau horaire métier indisponible : filtre de date impossible » au lieu de « Dates de filtre invalides ». Aucune requête datée n'est envoyée.
2. **Tests** :
   - [quotes.spec.ts](/C:/Users/aaziza/Desktop/test/frontend/src/app/features/quotes/quotes.spec.ts) : 2 tests de non-régression (erreur affichée, filtre refusé explicitement). Spec devis : 17/17.
   - [QuoteDateFilterMockMvcTest.java](/C:/Users/aaziza/Desktop/test/backend/src/test/java/tn/elitetunisia/quote/QuoteDateFilterMockMvcTest.java) : 4 tests (date vide ignorée, Instant UTC accepté, bornes égales, date invalide → 400).

## 4. Tests exécutés

| Suite | Commande | Résultat exact |
|---|---|---|
| Backend complet | `.\mvnw.cmd -B test` (JDK 21.0.2, dans `backend`) | exit 0, Tests run: 340, Failures: 0, Errors: 0, Skipped: 16 |
| Frontend complet | `npx -y -p node@22.22.3 node node_modules/@angular/cli/bin/ng.js test --watch=false` (dans `frontend`) | exit 0, 17 fichiers, 107/107 |
| Spec devis ciblé | même commande avec `--include src/app/features/quotes/quotes.spec.ts` | exit 0, 17/17 |
| Validation technique (cette passe) Maven | `.\mvnw.cmd -B test` (JDK 21.0.2, `target\surefire-reports` purgé avant) | BUILD SUCCESS, exit 0, 340 run, 0 failure, 0 error, 16 skipped (PostgreSQL-gated, non validés) |
| Validation technique (cette passe) Angular tests | `ng test --watch=false` (Node 22.22.3) | exit 0, Test Files 17/17, Tests 107/107 |
| Validation technique (cette passe) Angular build | `ng build` (Node 22.22.3) | exit 0, sortie `frontend\dist\frontend`; avertissement non bloquant : module `jsbi` (dépendance de `@js-temporal/polyfill`) non ESM |

Aucune correction de code n'a été nécessaire dans cette passe : aucun défaut démontré. Les décisions D3, D5 et D11 n'ont pas été touchées.

Tests ignorés (non validés, BLOQUÉS) : BookingHoldPostgresTest (7), PaymentPostgresIntegrationTests (5), InvoicePostgresIntegrationTests (2), BookingEngineTests (1), QuoteEngineTests (1).

## 5. Migrations

- Chaîne packagée : `database/migrations` V1 à V32 (copie via `backend/pom.xml`).
- `backend/src/main/resources/db/migration` : V4/V5/V6 désactivées (`_DISABLED_*`, commit 45dec0c) et un doublon V11, exclus du packaging.
- V21 à V32 et `database/proposed/` : **non suivis par Git** (`??`). Aucun n'a été versionné, déplacé ni supprimé.
- Points d'attention : V6 contient un index d'idempotence global remplacé par V27 (`uq_payments_customer_quote_idempotency`) ; V21 (`payments_one_captured_per_quote`) relève d'une décision métier en attente.
- Limite : la relecture de V21 à V32 et de `database/proposed/` a été faite en lecture seule, mais aucune exécution Flyway n'a été possible. La validation réelle d'application sur base vierge est BLOQUÉE.

### 5.1 Analyse par migration (lecture seule, aucune modification)

| Migration | Contenu | Dépendances | Risque Flyway / données | Recommandation |
|---|---|---|---|---|
| V21 `payments_one_captured_per_quote` | Index unique partiel : un seul CAPTURED par devis | V6 (payments) | Échec si doublons CAPTURED existants. **Entre en conflit avec la décision D5** (acompte puis solde = deux CAPTURED) | **Bloquée** : ne pas appliquer avant décision D5 |
| V22 `payments_quote_restrict` | FK payments→quotes : CASCADE remplacé par RESTRICT (DROP + ADD NOT VALID, `lock_timeout` 5 s). Pas de `VALIDATE CONSTRAINT` | V6 | Deux fichiers différents : `migrations/V22__payments_quote_restrict.sql` et `proposed/V22__payments_quote_fk_restrict.sql`. Copier les deux = version Flyway dupliquée | Garder une seule version ; ajouter `VALIDATE` après contrôle des données |
| V23 partenaires | Colonnes `partners`, CHECK `partner_type`, `vehicles.partner_id`, table append-only `vehicle_partner_assignments` avec trigger, rôle PARTNER | V1–V3 (véhicules), rôles | Faible si base vierge ; trigger append-only | Valider sur PostgreSQL |
| V24 | Ajoute ARCHIVED à `chk_vehicles_status` | V3 | Faible (élargissement de CHECK) | Valider sur PostgreSQL |
| V25 garanties véhicule | `vehicle_guarantees`, maximum 90 jours, trigger d'insertion avec verrou véhicule et contrôle de chevauchement, transition ACTIVE→WITHDRAWN seulement | V23, V24 | Trigger de verrouillage à tester en concurrence réelle | Valider sur PostgreSQL (concurrence) |
| V26 PENDING avec expiration | `hold_expires_at`, `guarantee_id`, statut EXPIRED, trigger `guard_bookings_availability` | V4, V25 | Modifie le CHECK de statut | Valider sur PostgreSQL |
| V27 idempotence paiements | Supprime `payments_idempotency_idx` (V6, global), crée `uq_payments_customer_quote_idempotency` (customer, quote, key) | V6 | Pas de doublon possible au passage de global à scopé ; risque de rejeu si V6 seul | Cohérent avec PaymentService ; valider sur PostgreSQL |
| V28 / V29 factures | Une facture principale par devis et par réservation (index partiels sur `quote_id`/`booking_id` NOT NULL) | Facturation | Pré-contrôles documentés, **non exécutés** | Exécuter les pré-contrôles sur base de test avant application |
| V30 / V31 facturation | Outbox `billing_requests`, historique, verrou et exécutions de backfill, permission `billing:backfill` SUPER_ADMIN | V28, V29 | Nouvelles tables, faible risque sur base vierge | Valider sur PostgreSQL |
| V32 PENDING non bloquant | Supprime `ck_bookings_pending_hold` et l'index d'expiration PENDING, remplace le trigger ; seuls CONFIRMED et IN_PROGRESS bloquent | V26, V4 | EXCLUDE V4 reste le filet de sécurité | Cohérent avec V4 et V26 ; valider sur PostgreSQL |

Points transverses :
- Les fichiers `database/migrations/V21`–`V32` et `database/proposed/` sont **non suivis par Git** (`??`). Ils sont copiés dans `db/migration` au packaging (`backend/pom.xml`).
- Statut « appliquée » sur une base réelle **inconnu** : aucune base n'a été interrogée (`flyway_schema_history` non consulté, 5432 interdit, 15432 indisponible).
- Recommandation globale : ne versionner aucune migration tant que V21 (D5) et V22 (doublon) ne sont pas tranchés, puis valider V1→V32 sur une base vierge dédiée dès que 15432 est rétabli.

### 5.2 Résolution du doublon V22 (sans suppression)

- Version retenue : `database/migrations/V22__payments_quote_restrict.sql` (SHA256 `7EFF34CB5CB1AF40DFB379DD3A802542920E17E14109F7776F223ED991318613`). Seule version chargée par le packaging (`backend/pom.xml`, lignes 152-153).
- `database/proposed/V22__payments_quote_fk_restrict.sql` : même DDL (DROP puis ADD NOT VALID, RESTRICT), document de révision 2 explicitement « non appliquée ». Elle ne doit pas être copiée dans `database/migrations` : deux fichiers V22 provoqueraient une erreur Flyway de version dupliquée.
- Le fichier `proposed` n'a pas été modifié ni supprimé (non suivi par Git). Sa suppression reste une décision humaine.
- Point restant : `VALIDATE CONSTRAINT fk_payments_quote` n'est exécuté par aucune migration. La contrainte reste `NOT VALID` tant qu'une migration ultérieure ou un script d'exploitation ne la valide pas. Ce point doit être vérifié sur base de test (`convalidated`).
- Validation réelle (migration sur base vierge, `confdeltype = 'r'`) : BLOQUÉE par 15432.

## 6. PostgreSQL (diagnostic en lecture seule)

- Contrôle de cette passe (2026-11-11) : `Test-NetConnection 127.0.0.1 -Port 15432` → `TcpTestSucceeded: False`. Le seul service PostgreSQL présent est `postgresql-x64-18` (Running), qui correspond au cluster principal ; il n'a pas été touché.
- Contrôle Git de cette passe (2026-11-11, lecture seule, HEAD `78da39b`) :
  - `git status --porcelain` (mode par défaut, dossiers regroupés) : **175 entrées** = 90 `M` + 85 `??`.
  - `git status --porcelain -uall` (fichiers détaillés) : **257 entrées** = 90 `M` + **167 fichiers non suivis**. `git ls-files --others --exclude-standard` confirme 167.
  - Aucune entrée `D` (supprimé), `R` (renommé) ou `A` (ajouté en index). Aucun fichier stagé.
  - Le chiffre 169 du rapport précédent n'a pas été sauvegardé fichier par fichier : les checkpoints de la session montrent des compteurs qui varient selon le mode d'affichage et la mission (160 à 224). L'écart de 6 entrées **ne peut pas être attribué précisément** sans liste de référence. Cause probable : de nouveaux dossiers regroupés (par exemple `backend/src/main/resources/META-INF/`, `frontend/src/app/features/partner/`) apparus après le comptage de 169.
  - Fichiers sensibles : aucun secret trouvé par recherche de motifs (`.env`, `pgpass`, `*.pem`, `*.key`, `*.log`, `*.bak`). Le nom `TokenServiceSecretTest.java` est un test unitaire sans valeur réelle (chaînes de test comme `short-secret`). `META-INF/spring.factories` enregistre `ProductionConfigurationValidator` (fichier de configuration légitime à versionner).
  - Doublons : `technical-audit-final.md` (racine, 6680 octets) et `docs/technical-audit.md` ont des empreintes différentes ; ce ne sont pas des copies. `database/proposed/` duplique le SQL de V22 (voir 5.2).
  - Fichiers non suivis qui ne devraient probablement pas être versionnés (à décider, non appliqué) : `technical-audit-final.md` (racine, rapport temporaire), `database/proposed/` (propositions non appliquées), et les fichiers de migration V21 à V32 tant que D5 n'est pas tranché.
  - Recommandations de nettoyage (non appliquées) : décider du sort de `technical-audit-final.md` et de `database/proposed/` ; lister les 90 fichiers `M` par lot avant tout commit ; ajouter un `git status -uall` au contrôle de fin de mission pour éviter les écarts de comptage.
  - Avertissements Git : « LF will be replaced by CRLF » sur `backend/pom.xml`, `backend/.gitignore`, `BackendApplicationTests.java` (fins de ligne mixtes, à traiter avant tout commit).

- `127.0.0.1:15432` : non joignable, aucun listener TCP sur ce port.
- `127.0.0.1:5432` : joignable, mais **non utilisé** pour le projet (règle de la mission).
- Cause exacte non établie. Le blocage Windows (bind) précédemment constaté n'est pas résolu.
- Procédure admin requise : vérifier l'identité du cluster dédié 15432, son répertoire de données et ses logs, puis le démarrer avec une autorisation explicite. Aucune action effectuée sans autorisation.

## 7. Sécurité

- Cookie refresh : `Max-Age` aligné sur `app.security.jwt.refresh-token-days` (correction précédente, test dédié demandé au plan).
- Les réponses et logs n'ont pas été re-audités dans cette passe ; les audits de phase E restent la référence.

## 8. Fichiers modifiés (session)

- `frontend/src/app/features/quotes/quotes-list.component.ts`
- `frontend/src/app/features/quotes/quotes.spec.ts`
- `backend/src/test/java/tn/elitetunisia/quote/QuoteDateFilterMockMvcTest.java` (nouveau)
- `docs/audits/ELITE_TUNISIA_FINAL_STABILIZATION_REPORT.md` (nouveau)
- Logs temporaires supprimés.

## 9. Risques résiduels

1. Aucune validation PostgreSQL réelle : migrations, verrous, concurrence, idempotence et transactions restent non prouvés.
2. V21 à V32 non suivis par Git : risque de déploiement et de divergence de checksum.
3. Décisions métier en attente (pénalités, remboursements, CAPTURED après annulation, acomptes, soldes, V21).
4. Sauvegarde et restauration non vérifiées.

## 10. Actions externes nécessaires

- Autorisation et intervention administrateur pour rétablir le cluster PostgreSQL de test 15432.
- Décisions métier sur les points en attente.
- Validation de la stratégie de versionnement des migrations V21 à V32 et de `database/proposed/`.

## 11. Fonctionnalités financières (revue, aucune règle inventée)

| Sujet | Comportement implémenté | Attendu (cahier des charges / décisions) | Risque | Décision manquante |
|---|---|---|---|---|
| Pénalités d'annulation | Aucune grille de pénalités dans le code | Grille à définir (D3) | Annulation sans pénalité | D3 |
| Remboursements | Colonnes `refunded_*` présentes mais jamais écrites ; statut REFUNDED existe ; pas de fournisseur réel | Règle de remboursement et allocation | Aucun remboursement réel possible | D3, D11 (fournisseur) |
| CAPTURED après annulation | Aucune règle explicite ; le paiement CAPTURED reste en l'état | Non décidé | Incohérence de statut possible | D3 |
| Acomptes et soldes | Aucune distinction ; V21 interdit deux CAPTURED par devis | Acompte puis solde (D5) | V21 bloque le cas acompte+solde | D5 (avant V21) |
| Facture | `InvoiceService` refuse sans CAPTURED (`PAYMENT_NOT_CAPTURED`) ; V28/V29 limitent à une facture principale par devis et réservation | Dépend de D5 | Facture bloquée si acompte seul | D5 |
| Réservation | `BookingService` exige un paiement CAPTURED | Dépend de D5 | Même blocage | D5 |
| Commission sur pénalités, allocation de remboursement | Absentes | Non décidé | — | Décision métier |

Aucune modification financière n'a été faite. Les décisions D3, D5 et D11 restent en attente (voir `docs/architecture/business-decisions-pending.md`).

## 12. Procédure administrateur PostgreSQL 15432 (lecture seule)

Ne jamais utiliser 5432. Ne rien démarrer ni reconfigurer sans autorisation. L'administrateur doit fournir :
1. Confirmation que le cluster dédié aux tests existe, avec son répertoire de données et son numéro de port.
2. État du service Windows du cluster (nom, statut, compte de service) et son journal de démarrage (`log` du répertoire de données).
3. Résultat de `Get-NetTCPConnection -LocalPort 15432` et d'un test TCP vers 127.0.0.1:15432 après tentative de démarrage.
4. Droits : lecture du répertoire de données et des logs, droit de démarrer le service, rôle PostgreSQL de test autorisé à créer et supprimer uniquement sa base de test.
5. Une base de test vierge dédiée (ni développement, ni système), dont le nom sera fourni par l'administrateur.

Une fois 15432 rétabli, la séquence de validation est : migrations V1→V32 sur base vierge, tests PostgreSQL (`BookingHoldPostgresTest`, `PaymentPostgresIntegrationTests`, `InvoicePostgresIntegrationTests`, `BookingEngineTests`, `QuoteEngineTests`), puis tests de concurrence et d'idempotence, puis sauvegarde et restauration.

## 13. Interventions humaines indispensables

1. Décisions métier : D3 (annulation, pénalités, remboursement, CAPTURED après annulation), D5 (acompte et solde, à trancher avant V21), D11 (fournisseur de paiement).
2. Administrateur : rétablir le cluster PostgreSQL de test 15432 et fournir une base vierge dédiée.
3. Décision de versionnement : V21 (bloquée par D5), doublon V22, et autorisation d'ajouter V21–V32 à Git après validation.
4. Environnement de sauvegarde et restauration.

## 14. Préparation de la validation PostgreSQL finale (lecture seule, aucune exécution PostgreSQL)

### 14.1 Les 16 tests ignorés et leurs prérequis

| Classe | Fichier | Tests | Activation | Problème constaté |
|---|---|---|---|---|
| BookingHoldPostgresTest | `backend/src/test/java/tn/elitetunisia/booking/BookingHoldPostgresTest.java` | 7 | `@EnabledIfEnvironmentVariable ELITE_POSTGRES_TEST=true` (ligne 36) | Aucune URL PostgreSQL déclarée |
| PaymentPostgresIntegrationTests | `backend/src/test/java/tn/elitetunisia/PaymentPostgresIntegrationTests.java` | 5 | `ELITE_POSTGRES_TEST=true` (ligne 23) | Aucune URL PostgreSQL déclarée |
| InvoicePostgresIntegrationTests | `backend/src/test/java/tn/elitetunisia/InvoicePostgresIntegrationTests.java` | 2 | `ELITE_POSTGRES_TEST=true` (ligne 19) | Aucune URL PostgreSQL déclarée |
| BookingEngineTests | `backend/src/test/java/tn/elitetunisia/BookingEngineTests.java` | 1 (ligne 170) | `ELITE_POSTGRES_TEST=true` | Lignes 42-44 : force H2 via `System.setProperty` |
| QuoteEngineTests | `backend/src/test/java/tn/elitetunisia/QuoteEngineTests.java` | 1 (ligne 145) | `ELITE_POSTGRES_TEST=true` (+ `assumeTrue`) | Pas d'URL PostgreSQL déclarée |

Total : 16 tests, aucun n'a été exécuté contre PostgreSQL.

### 14.2 Défaut de harnais confirmé par lecture du code (bloquant pour la validation)

`backend/pom.xml` (plugin Surefire, lignes 198-217) force au niveau du plugin `spring.datasource.url=jdbc:h2:mem:elite_test…`, `spring.flyway.enabled=false` et vide `ELITE_POSTGRES_TEST`. Le profil `postgres-test` (lignes 240-260) ajoute `ELITE_POSTGRES_TEST=true` mais ne redéfinit **pas** l'URL. Conséquence : si le flag est activé, les 5 classes ci-dessus s'exécuteraient sur **H2** et paraîtraient « PostgreSQL validées ». C'est une fausse validation. **Ne pas lancer ces tests en l'état.**

Corrections nécessaires avant exécution (non appliquées, à faire avec validation du cluster disponible) :
1. Dans le profil `postgres-test`, redéfinir `spring.datasource.url`, `username`, `password` à partir de variables dédiées (ex. `ELITE_PG_TEST_URL`, `ELITE_PG_TEST_USER`, `ELITE_PG_TEST_PASSWORD`), et `spring.flyway.enabled=true`.
2. Remplacer le `create-drop` par `validate` pour ce profil, afin que le schéma soit celui produit par les migrations V1→V32 et non celui généré par Hibernate.
3. Ajouter un garde-fou qui refuse toute URL différente de `jdbc:postgresql://127.0.0.1:15432/<base de test>` ou `localhost:15432`, et toute base système ou de développement (même logique que `PaymentQuotePostgresA4Test`, lignes 78-83).
4. `BookingEngineTests` force H2 par `System.setProperty` (lignes 42-44) : son test PostgreSQL (ligne 170) ne peut pas être validé tel quel. Il faut retirer ce forçage pour le profil PostgreSQL ou le déplacer dans une classe dédiée.

### 14.3 Commande d'exécution (à utiliser seulement après les corrections 14.2 et le rétablissement de 15432)

Variables d'environnement (valeurs fournies par l'administrateur, jamais écrites dans le dépôt) : `ELITE_PG_TEST_URL=jdbc:postgresql://127.0.0.1:15432/<base_test_vierge>`, `ELITE_PG_TEST_USER`, `ELITE_PG_TEST_PASSWORD`.

```powershell
cd C:\Users\aaziza\Desktop\test\backend
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
.\mvnw.cmd -B -Ppostgres-test test -Dtest="BookingHoldPostgresTest,PaymentPostgresIntegrationTests,InvoicePostgresIntegrationTests,BookingEngineTests,QuoteEngineTests" -Dsurefire.failIfNoSpecifiedTests=true
```

Critère de réussite : 16 tests exécutés, 0 ignoré, 0 échec, 0 erreur, et rapports Surefire récents. Un test ignoré ne compte pas comme réussi. Les classes `@Tag("postgres")` `PaymentQuoteCapturePostgresIT`, `PostgresInvoiceConcurrencyIT` (Failsafe) ne font pas partie des 16 et doivent être exécutées séparément (`verify`).

### 14.4 Procédure de validation sur base vierge

1. L'administrateur crée une base vierge dédiée sur 127.0.0.1:15432 (procédure section 12). Aucune base existante n'est touchée.
2. Vérifier que `flyway_schema_history` est absent ou vide sur cette base.
3. Exécuter les migrations V1→V32 (via le démarrage Flyway du profil corrigé), puis contrôler `flyway_schema_history` : toutes les versions en `success = true`, aucun checksum mismatch.
4. Lancer la commande 14.3, puis les tests Failsafe PostgreSQL.
5. Consigner les résultats (commande, exit code, compteurs, rapports) dans ce rapport.

### 14.5 Nettoyage et isolation

- Chaque test PostgreSQL doit nettoyer ses données (ou utiliser des identifiants uniques) pour éviter les dépendances entre classes.
- Ne jamais réutiliser la base après exécution : recréer une base vierge pour chaque campagne de migration.
- Les classes legacy exclues (`PartnerMigrationPostgresTest`, `PaymentBookingPostgresTest`, `PaymentQuotePostgresA4Test`, `MigrationWithExistingDataIT`) font des `CREATE/DROP DATABASE` sur l'ancien cluster et restent hors campagne.

### 14.6 Git

`frontend/dist/` est ignoré par le `.gitignore` racine (ligne 8 : `frontend/dist/`). Le build produit donc un répertoire non suivi, ignoré : aucune action Git nécessaire.

### 14.7 Blocages restant à lever par l'administrateur

- Rétablir le cluster 15432 et fournir la base vierge dédiée et ses identifiants (section 12).
- Autoriser la correction du harnais 14.2 (modification de `backend/pom.xml` et des classes listées). Ces modifications touchent la configuration de test, pas les règles financières, donc aucune décision D3/D5/D11 n'est requise.

Statut : 16 tests PostgreSQL toujours NON VALIDÉS. Aucune exécution PostgreSQL n'a eu lieu.

### 14.1 Correction du harnais PostgreSQL (mission de hardening)

**Défaut initial :** Surefire forçait H2 au niveau du plugin et le profil `postgres-test` ne le surchargeait pas. Activer le drapeau pouvait donc exécuter les 16 tests sur H2 sans le signaler. Le helper créait aussi des bases temporaires et exécutait Flyway lui-même, avec des variables `ELITE_A4_PG_*` et un mode 5432 accepté.

**Fichiers modifiés :**
- `backend/pom.xml` : profils `postgres-test` Surefire et Failsafe. Ils surchargent `spring.datasource.*` depuis `ELITE_POSTGRES_URL|USERNAME|PASSWORD`, activent Flyway, et vident `DATABASE_*`. Les variables `ELITE_A4_PG_*` sont retirées.
- `backend/src/test/java/tn/elitetunisia/PostgresTestGuard.java` (nouveau) : validation pure, sans contact base. Exige `jdbc:postgresql://(127.0.0.1|localhost):15432/<base>_test`, refuse les placeholders, le rôle `postgres`, et les valeurs vides.
- `backend/src/test/java/tn/elitetunisia/PostgresTestGuardTest.java` (nouveau) : 9 cas.
- `backend/src/test/java/tn/elitetunisia/PostgresTestHelper.java` (réécrit) : ne crée et ne supprime plus de base, ne lance plus Flyway lui-même. Avec `ELITE_POSTGRES_TEST=true`, il valide via le garde-fou et positionne Flyway activé, `ddl-auto=validate`, dialecte PostgreSQL, `spring.sql.init.mode=never`.
- `BookingEngineTests.java` : aucune modification. Son forçage H2 est déjà conditionné au drapeau (branche `else`). Il ne s'applique donc pas en mode PostgreSQL.

**Commandes et résultats (PostgreSQL non démarré) :**
- `.\mvnw.cmd -B -ntp "-Dtest=PostgresTestGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` (JDK 21.0.2) : 9 tests, 0 échec, 0 erreur, BUILD SUCCESS.
- `.\mvnw.cmd -B -ntp -Ppostgres-test test-compile` : BUILD SUCCESS.
- `target/classes/db/migration` contient V1 à V32, avec un seul V22 (`V22__payments_quote_restrict.sql`).
- Preuve 1 (URL sur 5432, `-Ppostgres-test -Dtest=BookingHoldPostgresTest`) : 7 erreurs, toutes causées par `IllegalStateException` du garde-fou, avant tout contact base. Le message nomme la valeur reçue et la valeur attendue.
- Preuve 2 (URL valide sur 127.0.0.1:15432, cluster fermé) : échec sur `Connection to 127.0.0.1:15432 refused` dans Flyway. Le contexte utilise PostgreSQL et n'a pas basculé sur H2.
- Variables d'environnement `ELITE_POSTGRES_*` retirées après les preuves. Aucune base créée ni supprimée.

**Prérequis avant exécution réelle des 16 tests :** cluster 127.0.0.1:15432 disponible ; base dédiée `<nom>_test` créée vierge par l'administrateur, avec un nom finissant par `_test` ; rôle non superutilisateur ; les trois variables `ELITE_POSTGRES_TEST=true`, `ELITE_POSTGRES_URL`, `ELITE_POSTGRES_USERNAME`, `ELITE_POSTGRES_PASSWORD` renseignées.

**Statut :** harnais prêt, preuves de non-repli sur H2 obtenues. Les 16 tests PostgreSQL restent **NON VALIDÉS** (aucune exécution réussie). Les classes legacy `PartnerMigrationPostgresTest`, `PaymentBookingPostgresTest`, `PaymentQuotePostgresA4Test` et `MigrationWithExistingDataIT` restent exclues du profil et ne sont pas comptées comme réussies.

### 14.2 Validation PostgreSQL réelle : état à la date de la mission (2026-11-12, 08:44 +01:00)

- **Contrôle TCP en lecture seule sur 127.0.0.1:15432 : FERMÉ** (connexion refusée explicitement par la cible). Port 5432 non sondé.
- Variables `ELITE_POSTGRES_URL|USERNAME|PASSWORD` non définies dans la session : aucune exécution des 16 tests n'a été tentée.
- Base `elite_a4_test`, rôle dédié et état vierge : **non vérifiables** tant que le cluster ne répond pas.
- Résultat : **0 des 16 tests PostgreSQL exécutés**. Statut : NON VALIDÉ (blocage externe).
- Aucune base créée, supprimée ou recréée. Aucun secret inscrit dans ce rapport.

**Décision métier D5 : VALIDÉE — paiement intégral à la réservation.**
**Décision D3 : EN ATTENTE.**
**Décision D11 : EN ATTENTE.**

Procédure de reprise dès que le cluster répond : définir `ELITE_POSTGRES_TEST=true`, `ELITE_POSTGRES_URL=jdbc:postgresql://127.0.0.1:15432/elite_a4_test`, `ELITE_POSTGRES_USERNAME`, `ELITE_POSTGRES_PASSWORD` (saisis hors conversation), puis `.\mvnw.cmd -B -ntp -Ppostgres-test "-Dtest=BookingHoldPostgresTest,PaymentPostgresIntegrationTests,InvoicePostgresIntegrationTests,BookingEngineTests,QuoteEngineTests" test`. Vérifier : 16 exécutés, 0 ignoré, 0 échec, et contrôle `current_user` non superutilisateur.

### 14.3 Diagnostic du cluster 15432 (lecture seule, 2026-11-12 09:10 +01:00)

- **Cause :** aucun cluster PostgreSQL configuré sur 15432 n'existe sur cette machine. Le seul cluster local est `postgresql-x64-18` (service Automatic, Running, `C:\Program Files\PostgreSQL\18\data`), configuré avec `port = 5432` et `listen_addresses = '*'`. Il ne répond pas sur 15432 et ne doit pas être utilisé.
- Aucune écoute TCP sur 15432 (`Get-NetTCPConnection`). Recherche de `postgresql.conf` dans les répertoires d'installation connus : une seule occurrence, port 5432.
- Journaux : `postgresql-2026-11-08_000000.log` (14 Ko) ; les journaux du 07 et du 09 sont vides. Aucune erreur de démarrage liée à 15432.
- `elite_a4_test` et le rôle de test : **non vérifiables**. Une connexion exigerait le cluster 15432, absent, ou le 5432, interdit.
- Aucune action effectuée : pas de démarrage, de reconfiguration, de création, de suppression ni de sondage du port 5432.
- Blocage résiduel : le cluster dédié 15432 doit être créé ou fourni par l'administrateur (autre machine, conteneur ou instance à configurer), avec `elite_a4_test` et un rôle non superutilisateur.

**Statut :** 0 des 16 tests PostgreSQL exécutés. NON VALIDÉ. Verdict inchangé : NON PRÊT POUR LA PRODUCTION.

### 14.4 Second cluster PostgreSQL 18 dédié aux tests (2026-11-12, 09:15–09:35 +01:00)

**Prérequis :** shell non administrateur ; C: 50 Go libres ; `initdb.exe`/`pg_ctl.exe` présents dans `C:\Program Files\PostgreSQL\18\bin`.

**Réalisé (sans droits administrateur) :**
- Répertoire dédié `D:\PostgreSQL\elite-test-15432\data`, créé vide. Aucun répertoire existant touché.
- `initdb` PostgreSQL 18.3, encodage UTF8, locale C, auth `scram-sha-256`, superutilisateur bootstrap `etadmin`.
- Écoute `127.0.0.1:15432` via options de démarrage (`-p 15432 -c listen_addresses=127.0.0.1`). Le `postgresql.conf` de ce cluster est exclu par la politique de contenu : il n'a pas été lu ni modifié.
- Serveur démarré en processus détaché : `LISTEN 127.0.0.1:15432` vérifié. Journal : « database system is ready to accept connections ».
- Rôle `elite_test` : `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS`. Mot de passe généré aléatoirement, stocké dans `%USERPROFILE%\.elite-test-pg\elite_test.pw` (ACL réservée à l'utilisateur), jamais affiché ni consigné.
- Base `elite_a4_test`, propriétaire `elite_test`, encodage UTF8, template0.

**Vérifications SQL (connexion TCP 127.0.0.1:15432, rôle `elite_test`) :**
- `current_user=elite_test`, `current_database=elite_a4_test`, `rolsuper=f`, `rolcreatedb=f`, PostgreSQL 18.3.
- `pg_database` : `elite_a4_test|elite_test`, `postgres|etadmin`.
- Schéma `public` : 0 table (base vierge).
- Requête de privilèges de base/schéma : non obtenue (la commande ne répond pas dans cette session). À confirmer.

**Service Windows : NON INSTALLÉ.** La création d'un service exige des droits administrateur. Le processus actuel (PID 18400) est lancé manuellement et s'arrêtera à la fin de la session.

**Procédure administrateur (PowerShell en administrateur) :**
1. Arrêter l'instance manuelle : `& 'C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe' -D 'D:\PostgreSQL\elite-test-15432\data' -m fast stop`
2. Donner accès au compte service : `icacls 'D:\PostgreSQL\elite-test-15432' /grant 'NT AUTHORITY\NetworkService:(OI)(CI)F' /T`
3. Enregistrer le service : `sc.exe create postgresql-elite-test-15432 start= auto obj= 'NT AUTHORITY\NetworkService' binPath= '"C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe" runservice -N "postgresql-elite-test-15432" -D "D:\PostgreSQL\elite-test-15432\data" -o "-p 15432 -c listen_addresses=127.0.0.1" -w'`
4. Démarrer : `Start-Service postgresql-elite-test-15432`, puis vérifier `Get-NetTCPConnection -LocalPort 15432 -State Listen`.

**Service 5432 :** `postgresql-x64-18` toujours `Running`, non modifié. Port 5432 non utilisé.

**Non réalisé :** lancement des tests applicatifs (interdit dans cette étape). Les 16 tests PostgreSQL restent NON VALIDÉS.

**Statut :** cluster dédié opérationnel en mode manuel ; installation en service Windows en attente de l'administrateur. Verdict inchangé : NON PRÊT POUR LA PRODUCTION.

### 14.5 Exécution réelle des 16 tests PostgreSQL sur 127.0.0.1:15432 (2026-11-12, ~10:00–10:01 +01:00)

**Contrôles préalables :**
- Shell non administrateur. Service `postgresql-elite-test-15432` : **absent** (installation admin toujours requise).
- Listener 127.0.0.1:15432 actif (PID 18400, mode manuel). Service `postgresql-x64-18` (5432) : `Running`, non modifié, non sollicité.
- Une requête `psql` de contrôle des privilèges est restée bloquée dans le shell. Elle a été interrompue. Les privilèges de `elite_test` ne sont donc pas vérifiés par requête directe ; la validation repose sur l'exécution Maven ci-dessous.

**Commande exécutée** (depuis `backend\`, JDK 21.0.2) :
```
.\mvnw.cmd -B -ntp -Ppostgres-test "-Dtest=BookingHoldPostgresTest,PaymentPostgresIntegrationTests,InvoicePostgresIntegrationTests,BookingEngineTests,QuoteEngineTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
Variables injectées dans le processus uniquement : `ELITE_POSTGRES_TEST=true`, `ELITE_POSTGRES_URL=jdbc:postgresql://127.0.0.1:15432/elite_a4_test`, `ELITE_POSTGRES_USERNAME=elite_test`, `ELITE_POSTGRES_PASSWORD` (lu depuis le fichier utilisateur, non affiché, non écrit).

**Preuves Flyway (log) :** `Database: jdbc:postgresql://127.0.0.1:15432/elite_a4_test (PostgreSQL 18.3)`, `Successfully applied 32 migrations to schema "public", now at version v32`. Aucun repli H2.

**Résultats (Surefire) :**

| Classe | Exécutés | Échecs | Erreurs | Ignorés |
|---|---|---|---|---|
| BookingHoldPostgresTest | 7 | 0 | 0 | 0 |
| InvoicePostgresIntegrationTests | 2 | 0 | 0 | 0 |
| PaymentPostgresIntegrationTests | 5 | 0 | 0 | 0 |
| BookingEngineTests (inclut 1 test gated) | 11 | 0 | 0 | 0 |
| QuoteEngineTests (inclut 1 test gated) | 10 | 0 | 0 | 0 |
| **Total** | **35** | **0** | **0** | **0** |

Les 16 tests conditionnés par `ELITE_POSTGRES_TEST` sont couverts (7 + 2 + 5 + 1 + 1). Les 19 autres tests de ces deux classes s'exécutent avec le même profil. Le build est `BUILD SUCCESS`. Log : `%TEMP%\pg16-run.log`.

**Non réalisé :** vérification directe de `ddl-auto=validate` (aucune erreur de validation Hibernate dans le log, mais pas de contrôle dédié), tests de concurrence, idempotence, rollback et sauvegarde/restauration sur PostgreSQL (phase suivante), service Windows.

**Statut :** 16/16 tests PostgreSQL exécutés, 0 ignoré, 0 échec sur PostgreSQL 18.3 réel. La validation reste partielle tant que le service Windows n'est pas installé et que les contrôles complémentaires ne sont pas faits.

### 14.6 Correction du test PICKUP et relance des 17 ITs PostgreSQL (2026-11-12)

- Défaut : `BookingLockOrderPostgresIT.concurrentPickupsCreateExactlyOneOperation` attendait un échec `INVALID_OPERATION` pour le second pickup. Le verrou sérialise bien les deux transactions, et le second appel renvoie le PICKUP existant (idempotence validée, checkpoints 063/064). Correction du test uniquement : les deux appels renvoient la même opération, un seul PICKUP en base. Aucun changement de `BookingOperationService`, aucune migration.
- Relance : `.\mvnw.cmd -B -ntp -Ppostgres-test "-Dtest=NoSuchUnitTestSkip" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=BookingPendingConcurrencyPostgresIT,BookingLockOrderPostgresIT,BookingAuditAtomicityPostgresIT,PostgresIdempotencyIT,PaymentQuoteCapturePostgresIT,PostgresInvoiceConcurrencyIT,DriverAssignmentPostgresIT,LastSuperAdminConcurrencyIT verify`, PostgreSQL 18.3, `127.0.0.1:15432`, base `elite_a4_test`, rôle `elite_test`.
- Résultat vérifié dans `target/failsafe-reports` : 17 exécutés, 0 échec, 0 erreur, 0 ignoré (BookingAuditAtomicityPostgresIT 4, BookingLockOrderPostgresIT 6, BookingPendingConcurrencyPostgresIT 1, DriverAssignmentPostgresIT 1, PostgresInvoiceConcurrencyIT 1, PaymentQuoteCapturePostgresIT 1, PostgresIdempotencyIT 1, LastSuperAdminConcurrencyIT 2). BUILD SUCCESS.
- Restent non traités dans cette session : tests métier complémentaires (webhooks répétés, rollback multi-entités, arrondis de facturation), audit détaillé V21–V32, classes legacy, service Windows (admin requis), E2E réels, audit sécurité et cohérence frontend/backend. Ces volets ne sont pas validés.

### 14.7 Audit migrations V21–V32, correction V33 et tests d'invariants (2026-11-12)

**Règle métier VALIDÉE (décision du propriétaire) :** une réservation PENDING ne bloque jamais la disponibilité d'un véhicule ; V32 supprime les holds bloquants introduits par V26. Le PENDING n'expire pas automatiquement (aucune règle d'expiration inventée). La confirmation respecte toujours la disponibilité et la concurrence des réservations confirmées.

**Test ajouté :** `backend/src/test/java/tn/elitetunisia/MigrationInvariantsPostgresIT.java` (6 tests, PostgreSQL 18.3, `127.0.0.1:15432`, base `elite_a4_test`, rôle `elite_test`).

Premier passage : 6 exécutés, 2 échecs, 1 erreur. Causes :
- `stalePendingDoesNotBlock...` : défaut du test. Le devis du PENDING était créé après la confirmation concurrente, donc refusé par le moteur de devis (`QuoteException`) avant la règle de réservation. Le devis est désormais créé avant.
- `paymentForeignKey...` : **anomalie réelle**. `fk_payments_quote` (V22, `NOT VALID`) n'était jamais validée. Corrigée par la migration V33 (voir ci-dessous).
- `mainInvoiceIsRefused...` : défaut du test. L'exception levée par le service marque la transaction partagée en rollback-only ; le test doit annuler explicitement (`setRollbackOnly`). Le comportement de production est correct.

**Correction :** `database/migrations/V33__validate_payments_quote_fk.sql` : `ALTER TABLE payments VALIDATE CONSTRAINT fk_payments_quote;`. Aucune donnée modifiée ; la validation échoue proprement en cas de ligne orpheline. Flyway a appliqué V33 (« Successfully applied 1 migration… now at version v33 »).

Second passage : **6 exécutés, 0 échec, 0 erreur, 0 ignoré, BUILD SUCCESS.** Le test FK vérifie `convalidated = true`, `confdeltype = 'r'` (ON DELETE RESTRICT) et le refus de suppression d'un devis payé. La suppression est refusée par `quote_events_quote_id_fkey` (historique) et non par `fk_payments_quote` ; le devis reste protégé, l'assertion a été ajustée à cet invariant.

**Verdicts par migration (honnêtes) :**

| Migration | Verdict | Justification |
|---|---|---|
| V4 (EXCLUDE CONFIRMED/IN_PROGRESS) | VALIDÉE | Compatible avec la règle PENDING non bloquant ; testé indirectement (14.5, 14.6). |
| V5, V1 (legacy) | NON VALIDÉE en détail | Lecture seule ; `invoices` V1 renommée `legacy_invoices` en V7, donc son `UNIQUE(booking_id)` ne s'applique pas à la table active. |
| V6 / V27 (idempotence paiement) | VALIDÉE | `customer_id NOT NULL` vérifié ; unicité scopée client+devis testée. |
| V21 (un paiement CAPTURED par devis) | NON VALIDÉE | Index partiel non testé directement. |
| V22 (FK payments → quotes) | **ANOMALIE, corrigée par V33** | `NOT VALID` jamais validée ; V33 valide la contrainte. |
| V26 (holds bloquants) | ANOMALIE, supersédée par V32 | Holds bloquant les PENDING, contraire à la règle validée ; V32 les retire. |
| V28 / V29 (factures principales uniques) | VALIDÉE | Unicité par devis et par réservation ; création répétée retourne la même facture, montant = total du devis (test 14.7). |
| V30 / V31 (billing outbox, backfill) | NON VALIDÉE | Unicité et comportement du backfill non testés. |
| V32 (PENDING non bloquant) | VALIDÉE | Règle métier confirmée ; confirmation d'un PENDING ancien testée. |

**Suite complète PostgreSQL :** `.\mvnw.cmd -B -ntp -Ppostgres-test verify` avec variables `ELITE_POSTGRES_*` : Flyway valide 33 migrations sur `elite_a4_test`. Surefire : 349 exécutés, **6 échecs** (`BackendApplicationTests` 2 : seed super-admin ; `PartnerSecurityMockMvcTest` 3 : 401 au lieu de 200 ; `CataloguePublicMockMvcTest` 1). Ces tests supposent un profil H2 et ne sont pas encore diagnostiqués : **NON VALIDÉ, point ouvert**.

**Référence sans variables PostgreSQL :** `.\mvnw.cmd -B -ntp test` : 349 exécutés, 0 échec, 0 erreur, **16 ignorés** (tests PostgreSQL protégés, non exécutés dans ce mode).

**Fichiers modifiés :** `backend/src/test/java/tn/elitetunisia/MigrationInvariantsPostgresIT.java` (créé), `database/migrations/V33__validate_payments_quote_fk.sql` (créé), `docs/audits/ELITE_TUNISIA_FINAL_STABILIZATION_REPORT.md`. Aucune migration existante modifiée, aucun fichier supprimé, aucun git add/commit/push.

**Restent non traités :** tests webhooks répétés, rollback multi-entités, arrondis, V21/V30/V31 en détail, classes legacy, service Windows (admin), E2E réels, audit sécurité, cohérence frontend.

### 14.8 Diagnostic et correction des 6 échecs du profil PostgreSQL (2026-11-12)

**Échecs observés (run 1, `backend/target/full-verify.log`, profil `postgres-test`) :** `BackendApplicationTests` 2 (`testProfileSeedsAnInternalSuperAdmin` : employé absent ; `seededSuperAdminCanAuthenticate` : login 401), `PartnerSecurityMockMvcTest` 3 et `CataloguePublicMockMvcTest` 1 (login 401 au lieu de 200 avec le compte seed).

**Cause exacte (prouvée) :** `BookingEngineTests.admin()` (non `@Transactional`, sans profil) créait et committait un utilisateur `superadmin@example.test` avec un hash de mot de passe brut `not-a-login-password`, rôle SUPER_ADMIN, sans ligne employé. `TestProfileAdminSeeder.run` s'arrête tôt si cet e-mail existe (`existsByEmailIgnoreCase`), donc le vrai compte seed n'était jamais créé. Sur H2 (base neuve par JVM, seeder exécuté en premier) le défaut est masqué ; sur la base PostgreSQL persistante il devient une contamination dépendante de l'ordre d'exécution. Lecture seule avant correction : hash préfixé `not-`, activé, rôle SUPER_ADMIN, 0 ligne employé.

**Correction appliquée (test uniquement, aucun code de production modifié) :**
- `backend/src/test/java/tn/elitetunisia/BookingEngineTests.java`, méthode `admin()` : crée désormais un acteur unique `booking-actor-<uuid>@example.test` au lieu de réutiliser l'e-mail seed.
- Réparation de données sur la base de test dédiée `elite_a4_test` (aucune suppression) : l'e-mail de la seule ligne contaminée (e-mail seed, hash `not-`, sans employé) a été renommé en `contaminated-<id>@example.test`. La ligne, ses références et ses FK sont conservés. Le seeder peut à nouveau créer le vrai compte. Ce renommage est une modification de données de test, à signaler explicitement.
- Le seeder, les attentes des tests de sécurité et le profil n'ont pas été modifiés. Aucun test supprimé, aucun test ignoré, aucune désactivation de CSRF/JWT/contrôles d'accès.

**Non-régression :** le test `BookingEngineTests` ne réutilise plus l'e-mail seed ; la suite PostgreSQL vérifie l'authentification du seed (`BackendApplicationTests`, `PartnerSecurityMockMvcTest`, `CataloguePublicMockMvcTest`).

**Résultats après correction :**
- `.\mvnw.cmd -B -ntp -Ppostgres-test verify` (log `backend/target/full-verify2.log`, exit 0) : **BUILD SUCCESS**.
- Surefire : **349 exécutés, 0 échec, 0 erreur, 0 ignoré** (`BackendApplicationTests` 12/0, `PartnerSecurityMockMvcTest` 18/0, `CataloguePublicMockMvcTest` 7/0, `BookingEngineTests` 11/0, `LastSuperAdminProtectionTest` 7/0).
- Failsafe : **23 exécutés, 0 échec, 0 erreur, 0 ignoré** (dont `BookingLockOrderPostgresIT` 6, `MigrationInvariantsPostgresIT` 6, `LastSuperAdminConcurrencyIT` 2, `BookingAuditAtomicityPostgresIT` 4, `PostgresIdempotencyIT` 1, `PaymentQuoteCapturePostgresIT` 1, `PostgresInvoiceConcurrencyIT` 1, `DriverAssignmentPostgresIT` 1, `BookingPendingConcurrencyPostgresIT` 1).
- Flyway sur `elite_a4_test` (PostgreSQL 18.3) : migrations V1 à V33 présentes.
- Les preuves précédentes (17 ITs, 6 tests d'invariants, V33, 35 tests précédents) sont conservées.

**Risques ouverts :**
- Pas encore de garde automatique contre la réutilisation de l'e-mail seed dans d'autres tests.
- Le renommage de la ligne contaminée est une réparation manuelle de la base de test, non rejouable par Flyway.
- Contamination globale de la base de test : voir 14.9.

### 14.9 Suite H2, LastSuperAdminConcurrencyIT et contrôle de résidus (2026-11-12)

**Suite H2 (sans variables PostgreSQL)** : `.\mvnw.cmd -B -ntp verify`, log `backend/target/h2-verify3.log`, BUILD SUCCESS. Surefire : 349 exécutés, 0 échec, 0 erreur, **16 ignorés** (tests PostgreSQL protégés par `ELITE_POSTGRES_TEST`, non exécutés dans ce mode).

**LastSuperAdminConcurrencyIT (fichier modifié : `backend/src/test/java/tn/elitetunisia/user/LastSuperAdminConcurrencyIT.java`) :** dans le `finally`, les comptes créés par le test sont désactivés, et les comptes préexistants désactivés pendant le test sont réactivés. Avant cette correction, les comptes créés restaient actifs en base persistante.

**Validation PostgreSQL (`.\mvnw.cmd -B -ntp -Ppostgres-test verify`, deux exécutions successives, logs `backend/target/pg-verify-run1.log` et `pg-verify-run2.log`) :** les deux runs sont BUILD SUCCESS. Surefire : 349 exécutés, 0 échec, 0 erreur, 0 ignoré. Failsafe : 23 exécutés, 0 échec, 0 erreur, 0 ignoré (dont `LastSuperAdminConcurrencyIT` 2/0/0/0 à chaque run).

**Contrôle de résidus (lecture seule, `elite_a4_test`) :** 842 comptes SUPER_ADMIN actifs sur 851 ; 9 comptes `concurrent-*` restent actifs. Ces résidus viennent d'exécutions antérieures au correctif et d'autres tests qui commitent des SUPER_ADMIN. Le correctif ne supprime pas les résidus existants et n'a pas été mesuré avant le premier run de cette passe. Nettoyage non effectué : décision à confirmer.

### 14.10 Point B : isolation des identités de test (2026-11-12)

**Verdict Point B : PARTIELLEMENT VALIDÉ.** Les suites H2 et PostgreSQL passent deux fois, mais le contrôle de résidus montre des comptes `@example.test` actifs hérités d'exécutions antérieures. Ces résidus ne sont pas nettoyés (voir ci-dessous).

**Mécanisme :** `backend/src/test/java/tn/elitetunisia/TestIdentities.java` génère `it-<scénario>-<RUN_ID>-<uuid8>@example.test` (`email`) ou une adresse stable pour la JVM (`runEmail`), et enregistre chaque adresse dans un registre exact. `disableCreated` ne désactive que ces adresses enregistrées, avec `enabled = true`. Aucun `LIKE` n'est utilisé. `backend/src/test/java/tn/elitetunisia/TestAccountCleanupListener.java` appelle `disableCreated` dans `afterTestClass`, même en cas d'échec. Il est déclaré dans `backend/src/test/resources/META-INF/spring.factories`.

**Classes corrigées (emails fixes ou UUID remplacés par le helper) :** `BackendApplicationTests`, `BookingEngineTests` (`admin()` ne dépend plus de `superadmin@example.test`), `QuoteEngineTests` (idem), `TestFixtures`, `RefreshCookieAttributesTest`, `LoginThrottleHttpTest`, `ProductionProfileHardeningTest`, `LastSuperAdminConcurrencyIT`, `ServiceCatalogWorkflowTest`, `TransferWorkflowTest`. Les `@AfterAll` et `@TestInstance(PER_CLASS)` ajoutés précédemment ont été retirés au profit du listener.

**Classes laissées volontairement inchangées :** `AdminUserPaginationTest`, `LastSuperAdminProtectionTest`, `PartnerSecurityMockMvcTest`, `PartnerAssignmentHistoryTest`, `CataloguePublicMockMvcTest` (écritures transactionnelles rollbackées, non vérifiées ligne à ligne). `PaymentBookingPostgresTest`, `PartnerMigrationPostgresTest`, `PaymentQuotePostgresA4Test` et `MigrationWithExistingDataIT` insèrent encore des utilisateurs en SQL brut : **non audités, à traiter**.

**Corrections de compilation pendant cette passe :** accolade et déclaration `var brand` manquantes dans `BookingEngineTests.fixture()` (édition accidentelle corrigée), imports `TestIdentities` manquants dans 7 fichiers de sous-packages.

**Résultats (logs dans `backend/target/`) :**
- `test-compile` : BUILD SUCCESS (`tc-lot1.log`).
- H2 `h2-verify5.log` : Surefire 349 exécutés, 0 échec, 0 erreur, 16 ignorés (tests PostgreSQL protégés).
- PostgreSQL run 1 `pg-verify-run1.log` : Surefire 349 / 0 / 0 / 0 ; Failsafe 23 / 0 / 0 / 0.
- PostgreSQL run 2 `pg-verify-run2.log` : Surefire 349 / 0 / 0 / 0 ; Failsafe 23 / 0 / 0 / 0. Résultats identiques.

**Résidus (lecture seule, `elite_a4_test`, après les deux runs) :**
- Comptes `it-*@example.test` actifs : **0**. Le nettoyage des comptes créés par cette passe fonctionne.
- Comptes `concurrent-*` actifs : **0**.
- Comptes `@example.test` actifs : **1337**, dont préfixes `svc-` (80), `booking-` (78), `staff-` (52), `http-` (48), `throttle-` (16), et **115** adresses au format UUID. Ces comptes viennent d'exécutions antérieures à `TestIdentities` : **non supprimés ni désactivés** (aucune autorisation pour un nettoyage large, et pas de liste d'IDs validée). Il faut un audit puis une autorisation avant tout nettoyage.
- SUPER_ADMIN actifs : **836** (inchangé par rapport à l'audit précédent). Désactivés : 393.

**Points restants :** audit des 4 classes en SQL brut ci-dessus ; audit du flux de 1337 résidus (origine et autorisation) ; vérification des classes transactionnelles listées.

### 14.11 Point B : exécution des 4 classes SQL, audit transactionnel, audit des 1 337 comptes (2026-11-12)

**Verdict Point B : PARTIELLEMENT VALIDÉ.** Les suites H2 et PostgreSQL passent, mais les 4 classes SQL brutes n'ont pas pu être exécutées (blocage de droits) et les comptes historiques ne sont ni classés comme métier ni nettoyés.

**Quatre classes SQL autonomes (non exécutées avec succès) :**
- Commande utilisée : `mvn test -Dtest=...` avec `ELITE_A4_PG_TEST=true`, `ELITE_A4_PG_URL=jdbc:postgresql://127.0.0.1:15432/postgres` (URL d'administration exigée par le garde), `ELITE_A4_PG_USER` / `ELITE_A4_PG_PASSWORD` (`elite_test`, secret non affiché).
- Tentative 1 avec `.../elite_a4_test` : refusée par le garde (« Base refusée : elite_a4_test »). Erreur de configuration de ma part, corrigée à la tentative 2.
- `PaymentBookingPostgresTest`, `PaymentQuotePostgresA4Test`, `PartnerMigrationPostgresTest` : **erreur dans `setUp`**, `PSQL ERROR: permission denied to create database`. Cause : le rôle `elite_test` n'a ni `CREATEDB` ni `SUPERUSER`. Aucune base `et_a4_it_*` n'a été créée. Logs : `backend/target/sql4-run.log`, `sql3-run.log`.
- `MigrationWithExistingDataIT` : **ignorée** (2 tests skipped) par son garde : `elite_test` n'est pas dans l'allowlist `{et_test, elite_a4_tester}` (`MigrationWithExistingDataIT.java`, lignes 47–49). Les rôles `et_test` et `elite_a4_tester` n'existent pas sur le cluster. Je n'ai pas élargi l'allowlist ni tenté d'élever les privilèges : le test resterait bloqué par le même défaut `CREATEDB`.
- Conséquence : ces 4 classes **ne sont pas validées**. Elles ne doivent pas être comptées dans les 349 / 23 tests.

**Audit transactionnel (lecture du code) :**
- `LastSuperAdminProtectionTest`, `AdminUserPaginationTest`, `PartnerSecurityMockMvcTest`, `PartnerAssignmentHistoryTest`, `CataloguePublicMockMvcTest` : `@Transactional` au niveau classe, écritures rollbackées. Le `UPDATE ... WHERE enabled = true` du listener ne touche aucune ligne. Aucun nettoyage ajouté.
- `BookingAuditAtomicityPostgresIT` : `TransactionTemplate` utilisé uniquement pour installer un stub Mockito, sans écriture persistante. Aucun changement.
- `MigrationInvariantsPostgresIT` : `TransactionTemplate` avec `setRollbackOnly()`. Aucun changement.
- Seul chemin de commit réel côté production identifié : `BillingRequestService` (`REQUIRES_NEW`, ligne 54), pour l'enregistrement des échecs. Aucun test transactionnel ne semble l'atteindre ; la preuve empirique est le contrôle de résidus ci-dessous.

**Audit des 1 337 comptes `@example.test` actifs (lecture seule, `elite_a4_test`) :**
- `svc-` 80, `booking-` 78, `staff-` 52, `http-` 48, `throttle-` 16 : créés entre 12:08 et 14:09. Préfixes présents dans les classes de test du dépôt. **Origine probable** (anciennes exécutions antérieures à `TestIdentities`), non démontrée par un chemin de code.
- 115 adresses au format UUID, créées entre 10:01 et 14:09. **Origine probable**.
- 948 adresses à 8 caractères hexadécimaux, créées entre 10:01 et 14:09. Aucun code du dépôt ne produit ce format. **Origine inconnue.**
- Classification globale : ces comptes ont des références métier (1 058 réservations, 908 devis, 67 factures via réservations, 817 paiements via réservations, 1 065 lignes `customers`). Ils sont donc à traiter comme **potentiellement métier**. **Aucun nettoyage, désactivation ni suppression**, faute d'autorisation explicite.
- 92 SUPER_ADMIN actifs portent un `@example.test` ; 836 SUPER_ADMIN actifs au total : **non touchés**.

**Résultats H2 (`backend/target/h2-verify6.log`) :** `test-compile` EXIT=0 ; suite complète : 349 exécutés, 0 échec, 0 erreur, **16 ignorés**. Les 16 ignorés sont des tests gardés par `ELITE_POSTGRES_TEST` / `assumeTrue` (ex. `BookingEngineTests`, `InvoicePostgresIntegrationTests`, `MigrationInvariantsPostgresIT`) : ils ne tournent que sur PostgreSQL et ne sont pas comptés comme réussis en H2.

**Résultats PostgreSQL `postgres-test verify` :**
- Run 3 (`backend/target/pg-verify-run3.log`) : BUILD SUCCESS ; Surefire 349 / 0 / 0 / 0 ; Failsafe 23 / 0 / 0 / 0.
- Run 4 (`backend/target/pg-verify-run4.log`) : BUILD SUCCESS ; Surefire 349 / 0 / 0 / 0 ; Failsafe 23 / 0 / 0 / 0. Aucun test ignoré. Résultats identiques à run 3.

**Résidus avant/après (lecture seule, `elite_a4_test`) :**

| Indicateur | Après run 3 | Après run 4 |
|---|---|---|
| `it-*@example.test` actifs | 0 | 0 |
| `it-*@example.test` désactivés | 1 302 | 1 736 (+434) |
| `concurrent-*` actifs | 0 | 0 |
| `@example.test` actifs | 1 337 | 1 337 (inchangé) |
| SUPER_ADMIN actifs | 836 | 836 (inchangé) |
| Bases `et_*` présentes | aucune | aucune |

Les 434 comptes ajoutés entre les deux relevés ont été désactivés par le listener, pas supprimés. Ce n'est pas une fuite de comptes actifs, mais l'accumulation de comptes de test désactivés (+434 par exécution complète) est un résidu à traiter. Ce chiffre suppose que run 4 est la seule exécution entre les deux relevés, ce qui est vraisemblable mais non prouvé par un journal horodaté par exécution.

**Blocages restants :**
1. Le rôle `elite_test` n'a pas `CREATEDB`. Options : accorder `CREATEDB` à `elite_test` sur ce cluster de test (action administrateur) ou fournir un rôle dédié. Tant que ce n'est pas fait, les 3 classes Payment/Partner ne tournent pas.
2. L'allowlist de `MigrationWithExistingDataIT` n'accepte pas `elite_test`. Une décision est nécessaire : l'ajouter après vérification que `elite_test` est exclusivement dédié aux tests, ou créer un rôle `et_test` / `elite_a4_tester`.
3. Autorisation nécessaire avant tout nettoyage des 1 337 comptes historiques (dont 1 058 réservations liées) et avant toute modification des 836 SUPER_ADMIN.
4. Décision à prendre sur les 1 736 comptes `it-*` désactivés : purge ou conservation.

### 14.12 Audit du flux de paiement (PaymentEntity, idempotence, transactions, fournisseur)

STATUT : PARTIELLEMENT VALIDÉ (audit réalisé, aucune modification de code ; anomalies ouvertes documentées ci-dessous).

Fichiers audités : `PaymentService`, `PaymentEntity`, `PaymentRepository`, `PaymentStatus`, `CustomerPaymentController`, `SimulatedPaymentProvider`, migrations `database/migrations/V6__payments.sql`, `V21__payments_one_captured_per_quote.sql`, `V22__payments_quote_restrict.sql`, `V27__payments_idempotency_scope.sql`, `V33__validate_payments_quote_fk.sql`.

Chaîne de migrations : le `pom.xml` copie `database/migrations` dans `db/migration` au build (`backend/pom.xml:152-153`), donc les migrations de paiement sont bien effectives.

Constats (schéma) :
- Idempotence : l'unicité globale de `idempotency_key` (V6) est remplacée par `(customer_id, quote_id, idempotency_key)` (V27). Conforme à la décision « idempotence délimitée par client et devis ».
- Un seul paiement `CAPTURED` par devis (index unique partiel, V21).
- Clés FK `quote` (RESTRICT, V22, validée en V33) et `booking` (SET NULL).
- Constat : `idempotency_key` est nullable en base et en JPA ; le service rejette les clés nulles ou vides (`PaymentService.reserve`), et le DTO impose `@NotBlank`. Pas d'anomalie exploitable constatée, mais la contrainte n'est pas `NOT NULL` en base.
- Constat : aucune contrainte `CHECK` sur `status` ni `method` (varchar). La cohérence repose sur l'enum Java.

Constats (flux) :
- Trois transactions courtes (réservation, appel fournisseur hors transaction, finalisation). Aucune transaction n'est laissée en rollback-only par le code : le `DataIntegrityViolationException` est rattrapé à l'extérieur de la transaction échouée, puis le gagnant est relu.
- Rejeu identique : renvoie le paiement existant sans second appel fournisseur. Clé réutilisée avec autre méthode ou montant : `IDEMPOTENCY_CONFLICT`.
- Réponse fournisseur inconnue ou exception : le paiement reste `PENDING` (événement `PROVIDER_ERROR`), jamais considéré réussi ni échoué automatiquement.

Anomalies ouvertes (non corrigées, car elles nécessitent une décision métier ou une infrastructure absente) :
1. Aucun webhook fournisseur : pas de vérification de signature, pas de protection contre le rejeu, pas de traitement des notifications tardives ou multiples. Non applicable tant qu'aucun webhook n'existe ; à traiter avant tout fournisseur réel.
2. Aucun job de réconciliation : un paiement `PENDING` (cas `PROVIDER_ERROR`) bloque toute nouvelle tentative sur le devis (`PAYMENT_IN_PROGRESS`) sans chemin de sortie automatique. Un mécanisme de résolution doit être défini (qui résout, avec quelle preuve fournisseur). Décision métier requise.
3. ~~`PaymentRepository.findByIdempotencyKey` (global)~~ : CORRIGÉ (voir « Correction appliquée » ci-dessous).
4. `finish` sur `CAPTURED` verrouille la réservation sans changer son statut : la règle de confirmation de réservation après paiement n'est pas définie dans le dépôt. Non inventée.
5. Remboursement et annulation : non définis par les règles métier validées ; aucune logique n'est ajoutée.

Commandes exécutées :
- `.\mvnw.cmd -B -ntp test "-Dtest=Payment*,CustomerPayment*,PostgresIdempotencyIT" "-Dsurefire.failIfNoSpecifiedTests=false"` (dans `backend\`, JDK 21 Adoptium).

Résultats réels (H2, profil par défaut) : 30 tests exécutés, 0 échec, 0 erreur, 7 ignorés, BUILD SUCCESS.
- Tests PostgreSQL ignorés, donc NON validés : `PaymentPostgresIntegrationTests` (5), `PostgresIdempotencyIT` (1), `PaymentQuoteCapturePostgresIT` (1).
- `PaymentBookingPostgresTest` : 0 test exécuté ; `PaymentQuotePostgresA4Test` : 0 test exécuté. Bloqués par le rôle `elite_a4_tester` (non créé, voir section 14.11).

Preuve PostgreSQL : aucune pour le flux de paiement dans cette passe. Les validations PostgreSQL restent BLOQUÉES jusqu'à l'exécution de `docs/audits/grant-a4-tester-createdb.sql` par un administrateur.

Risques restants : webhook absent, réconciliation absente, verrou `PENDING` sans sortie automatique, règles de confirmation et de remboursement indéfinies.

#### Correction appliquée (lot « corrections techniques PAYMENT »)

STATUT : VALIDÉ pour la correction de portée ; PostgreSQL validé sur les tests ciblés ; webhook, réconciliation et intégration fournisseur NON VALIDÉS (décisions requises).

Cause : `PaymentRepository` exposait `findByIdempotencyKey(String)` et `countByIdempotencyKey(String)` sans périmètre client ni devis, alors que l'idempotence est définie par `(customer_id, quote_id, idempotency_key)` (V27).

Correction :
- `backend/src/main/java/tn/elitetunisia/payment/PaymentRepository.java` : méthodes globales supprimées ; ajout de `countByCustomerIdAndQuote_IdAndIdempotencyKey(UUID, UUID, String)`.
- Aucun appelant de production n'utilisait les méthodes globales (vérifié) ; `PaymentService` utilise déjà la recherche scopée.
- `backend/src/test/java/tn/elitetunisia/PaymentPostgresIntegrationTests.java` : 5 usages migrés ; le test `sameIdempotencyKeyIsScopedByCustomerAndQuote` vérifie 1 par (client, devis, clé) et 0 en inter-périmètre.
- `backend/src/test/java/tn/elitetunisia/payment/PostgresIdempotencyIT.java` : test de concurrence migré vers le comptage scopé.

Anomalie d'outillage constatée : un premier run échouait avec `ClassNotFoundException: TestFixtures.Fixture` (build incrémental obsolète, non lié au code). Corrigé par `clean test-compile`.

Commandes exécutées :
- `.\mvnw.cmd -B -ntp clean test-compile` (dans `backend\`) : BUILD SUCCESS.
- `.\mvnw.cmd -B -ntp test "-Dtest=Payment*,CustomerPayment*,PostgresIdempotencyIT" "-Dsurefire.failIfNoSpecifiedTests=false"` : 30 tests, 0 échec, 0 erreur, 7 ignorés (tous PostgreSQL-conditionnés), BUILD SUCCESS.
- `.\mvnw.cmd -B -ntp -Ppostgres-test verify "-Dit.test=PaymentQuoteCapturePostgresIT,PostgresIdempotencyIT,PaymentPostgresIntegrationTests" "-Dfailsafe.failIfNoSpecifiedTests=false"` avec `ELITE_POSTGRES_TEST=true`, cluster `127.0.0.1:15432`, base `elite_a4_test`, utilisateur `elite_test` (mot de passe lu depuis un fichier, non affiché) : Failsafe 7 tests (1 + 1 + 5), 0 échec, 0 erreur, 0 ignoré ; `PaymentServiceUnitTest` 10/0/0 ; BUILD SUCCESS.

Résidus après exécution (lecture seule) : comptes `it-*` enabled 0 ; `concurrent-*` enabled 0 ; `@example.test` enabled 1 337 (inchangé) ; SUPER_ADMIN enabled 836 (inchangé). Requête SUPER_ADMIN sur `user_roles(user_id, role_key, assigned_at)`.

Blocages restants (humains) :
- Rôle `elite_a4_tester` : script `docs/audits/grant-a4-tester-createdb.sql` à exécuter par un administrateur ; sans lui, `PaymentBookingPostgresTest` et `PaymentQuotePostgresA4Test` restent BLOQUÉS (0 test exécuté).
- Décisions métier requises (non implémentées, volontairement) :
  1. Règle de confirmation de réservation après `CAPTURED`.
  2. Qui débloque un paiement `PENDING` et sur quelle preuve fournisseur (`PAYMENT_IN_PROGRESS` bloque le devis sans sortie automatique).
  3. Politique d'annulation et de remboursement (aucun remboursement simulé).
  4. Choix du fournisseur de paiement (aucun fournisseur inventé).

Architecture préparatoire recommandée (non implémentée, sans simulation de fournisseur) :
- Webhook : vérification de signature avant tout traitement ; table de journalisation des identifiants d'événements reçus (unicité pour bloquer les rejeux) ; traitement idempotent par `transaction_ref`.
- Réconciliation : job qui liste les `PENDING` plus anciens qu'un seuil, interroge le fournisseur par `transaction_ref` ou `idempotency_key`, et n'applique une transition qu'après décision métier.
- Double capture : le partial unique index V21 (un `CAPTURED` par devis) reste la garde en base ; toute reprise doit repasser par ce contrôle.
- Traçabilité : `payment_events` (existant ou à créer par nouvelle migration Flyway versionnée) pour chaque transition et chaque réponse fournisseur.

## 14.13 Authentification, JWT et sécurité (lot sécurité)

STATUT : PARTIELLEMENT VALIDÉ (JWT, refresh, logout et accès croisés validés en H2 HTTP et sur PostgreSQL 18.3 `elite_a4_test` pour les tests HTTP ; décisions de session et modèle de propriété des documents encore ouverts).

Fichiers analysés : `AuthController`, `AuthService`, `TokenService`, `JwtAuthenticationFilter`, `AuthAuthorities`, `LoginThrottle`, `SecurityConfig`, contrôleurs client (booking, quote, invoice, payment), `PartnerPortalController`, `InvoiceService`, `PaymentService`.

Anomalie confirmée et corrigée :
- Cause : `TokenService.parse` vérifiait la signature mais n'exigeait pas la présence de `exp`. Un JWT signé valide sans expiration était donc accepté indéfiniment.
- Correction : `parse` rejette désormais tout token sans `exp` (`JwtException`). Les tokens émis par `createAccessToken` portent toujours `exp`, le comportement légitime est inchangé.
- Fichier modifié : `backend/src/main/java/tn/elitetunisia/auth/security/TokenService.java`.

Nouveaux tests : `backend/src/test/java/tn/elitetunisia/auth/security/TokenServiceValidationTest.java` (7 tests : token émis accepté, signature d'une autre clé rejetée, payload altéré rejeté, token expiré rejeté, malformés rejetés, algorithme `none` rejeté, token sans `exp` rejeté).

Commandes et résultats (H2 / unitaire) :
- `.\mvnw.cmd -B -ntp test "-Dtest=TokenServiceValidationTest,TokenServiceSecretTest"` : 11 run, 0 échec, BUILD SUCCESS (après correction ; avant correction, 1 échec attendu sur le token sans `exp`).
- `.\mvnw.cmd -B -ntp test "-Dtest=tn.elitetunisia.auth.**.*Test,EndpointAuthorizationSweepTest"` : 27 run, 0 échec, 0 erreur, BUILD SUCCESS. Le code de sortie du shell était 1 malgré BUILD SUCCESS ; cause non identifiée, à vérifier avant de conclure.

Contrôles vérifiés par lecture de code (non rejoués par test dédié) :
- Ownership : réservations (`BookingService.own`, `findByIdAndCustomerId`), factures (`InvoiceService.getForCustomer`, contrôle `customerId`), devis (`quotes.findBookingIdForCustomer`), paiements (`PaymentService.createForQuote` avec `actorCustomerId`). Aucun IDOR confirmé.
- Partenaire : `PartnerPortalController` filtre par `accounts.current(auth.getName()).id()`.
- Balayage anonyme et CUSTOMER sur `/api/admin/**` : couvert par `EndpointAuthorizationSweepTest` (vert).
- Cookie refresh (Max-Age, HttpOnly, SameSite=Strict, Path=/api/auth) : couvert par `RefreshCookieAttributesTest` (vert).
- Throttling login : couvert par `LoginThrottleTest` et `LoginThrottleHttpTest` (vert).

Risques résiduels et décisions métier à prendre (non implémentées) :
1. Un access token reste valide jusqu'à 15 minutes après logout (pas de liste de révocation des access tokens).
2. Deux refresh simultanés avec le même cookie (multi-onglets) : le second revoque toute la famille et déconnecte l'utilisateur. Politique de session à décider.
3. Le logout révoque uniquement le refresh token présenté, pas toute la famille. Décision à prendre.
4. Documents : `/api/files/**` (`PublicFileController`, permitAll) ne sert que des images stockées par `LocalFileStorageService.storeImage` : clés `UUID.randomUUID()` + extension `.jpg|.png|.webp`, validation de signature et `safePath` par regex. Aucun document personnel ne transite par ce stockage. Les PDF de facture passent par la route authentifiée `/api/invoices/{id}/pdf` avec contrôle `customerId`. Risques : URL publique sans expiration ni révocation (la clé UUID n'est pas devinable mais se transmet), aucun journal d'accès, pas de propriétaire sur l'image. Décision requise : modèle de propriété et de visibilité des images (publique ou signée).
5. Paiement IDOR : couvert par `CrossCustomerAccessHttpTest` (POST `/api/customer/quotes/{id}/payments` par un autre client, renvoie 404 `QUOTE_NOT_FOUND` via `quotes.findBookingIdForCustomer`). Il n'existe ni route GET ni route de modification de paiement par identifiant.

Tests HTTP ajoutés :
- `backend/src/test/java/tn/elitetunisia/auth/service/AuthRefreshLogoutHttpTest.java` : 14 tests (rotation, expiré, révoqué, rejeu, sans cookie, cookie malformé, après logout, deux refresh simultanés (au plus un 200), logout cookie supprimé, logout sans cookie/expiré/malformé/double).
- `backend/src/test/java/tn/elitetunisia/auth/service/CrossCustomerAccessHttpTest.java` : 5 tests (réservation lecture/annulation, devis lecture/accept/reject/request-change, paiement POST par un client étranger, facture et PDF, listes filtrées). Le paiement vérifie le montant égal à `quotes.total_amount` (sinon 409 `PAYMENT_AMOUNT_MISMATCH`, `PaymentService` ligne 76). Résultat H2 : 5 run, 0 échec, EXIT=0.
- Relecture PostgreSQL : `.\mvnw.cmd -B -ntp -Ppostgres-test clean test "-Dtest=CrossCustomerAccessHttpTest,AuthRefreshLogoutHttpTest"` avec `ELITE_POSTGRES_URL=jdbc:postgresql://127.0.0.1:15432/elite_a4_test`, `ELITE_POSTGRES_USERNAME=elite_test`. Log : Flyway `Database: jdbc:postgresql://127.0.0.1:15432/elite_a4_test (PostgreSQL 18.3)`. Résultats : `AuthRefreshLogoutHttpTest` 14/0/0, `CrossCustomerAccessHttpTest` 5/0/0, total 19/0/0, EXIT=0. Ce résultat est la seule preuve PostgreSQL de ce lot ; le mot de passe a été lu depuis un fichier local et n'a pas été journalisé.
- Correction associée : `AuthController` lit le cookie refresh brut (`readRefreshCookie`) au lieu de `@CookieValue`, car un cookie `%%%` provoquait un HTTP 500 (décodage URI dans `@CookieValue`). Couvert par régression.

Diagnostic du code retour 1 avec BUILD SUCCESS : non reproduit. Maven sans pipe retourne 0. Cause la plus probable : le pipeline PowerShell (`Select-String`/`Select-Object`) ; non confirmé.

Décisions non implémentées (à trancher) : durée de validité d'un access token après logout ; révocation d'une session ou de toute une famille au logout ; comportement de deux refresh simultanés (multi-onglets) ; politique de sessions multi-onglets.

## 14.14 Réservations et concurrence (lot prioritaire)

**Statut : VALIDÉ EN CONCURRENCE sous PostgreSQL 18.3 (`elite_a4_test`) pour les 10 scénarios demandés.** Aucune anomalie de production confirmée ; les défauts découverts sont tous des défauts de test (noms de colonnes, attente d'historique, exception métier mal classée), corrigés dans les tests. Verdict global inchangé : NOT READY FOR PRODUCTION.

Architecture examinée : `BookingService` (`create`, `transition`, `locked`, `requireAvailable`), `BookingOperationService` (pickup/retour), `VehicleService` (`update`, `updateStatus`, `delete`, `requireStatusChangeAllowed`), `GuaranteeService`, `BookingRepository` (`findLocked`, `countBlocking` limité à CONFIRMED/IN_PROGRESS), contrainte EXCLUDE `bookings_no_blocking_overlap` (V4). Ordre de verrouillage : véhicule puis réservation (`docs/architecture/booking-lock-order.md`).

Constats :
- `transition(CONFIRMED)` prend le verrou véhicule (`locked`), exige `rentable` (statut AVAILABLE), revérifie le chevauchement, la garantie, le devis accepté et le paiement CAPTURED.
- `requireStatusChangeAllowed` (MAINTENANCE/UNAVAILABLE/ARCHIVED) refuse si une réservation IN_PROGRESS existe ou une CONFIRMED se termine après maintenant. Il s'exécute après `findLocked` du véhicule, donc sérialisé avec la confirmation.
- Les réservations PENDING ne bloquent pas la disponibilité, conformément à la décision métier.

Tests de concurrence réels (`backend/src/test/java/tn/elitetunisia/booking/`), tous sur PostgreSQL 18.3, base `elite_a4_test`, rôle `elite_test`, vrais threads et transactions, départ synchronisé par `CountDownLatch` (aucun délai arbitraire comme mécanisme de réussite) :

| # | Scénario | Test | Répétitions | Invariants vérifiés | Statut |
|---|---|---|---|---|---|
| 1 | Deux clients confirment la même réservation/période | `BookingPendingConcurrencyPostgresIT` (existant) | 1 | Une seule confirmation, l'autre `VEHICLE_NOT_AVAILABLE` | VALIDÉ EN CONCURRENCE |
| 2 | Périodes disjointes sur un véhicule | `BookingConcurrencyScenariosPostgresIT.disjointPeriodsBothConfirm` | 20 | Les deux CONFIRMED, aucun chevauchement bloquant | VALIDÉ EN CONCURRENCE |
| 3 | Chevauchement partiel | `partialOverlapOnlyOneConfirms` | 20 | Une seule CONFIRMED, perdant PENDING sans événement | VALIDÉ EN CONCURRENCE |
| 4 | Chevauchement total | `totalOverlapOnlyOneConfirms` | 20 | Une seule CONFIRMED, aucun chevauchement bloquant | VALIDÉ EN CONCURRENCE |
| 5 | Garantie retirée pendant une confirmation | `guaranteeWithdrawalNeverLeavesConfirmedBookingUncovered` | 20 | Jamais CONFIRMED + garantie WITHDRAWN ; refus métier `GUARANTEE_HAS_BOOKINGS` accepté | VALIDÉ EN CONCURRENCE |
| 6 | Échec/perdant en cours de transaction | `losingConfirmationLeavesNoPartialWrite` | 20 | Perdant sans événement `BOOKING_CONFIRMED` fantôme | VALIDÉ EN CONCURRENCE |
| 7 | Requêtes simultanées confirmation + annulation | `concurrentConfirmAndCancelReachOneConsistentState` | 20 | État final CONFIRMED ou CANCELLED ; au plus un événement de chaque type ; `BOOKING_CANCELLED` présent si CANCELLED | VALIDÉ EN CONCURRENCE |
| 8 | Confirmations répétées simultanées (3 threads) | `repeatedConcurrentConfirmationIsIdempotent` | 20 | CONFIRMED, exactement un `BOOKING_CONFIRMED` | VALIDÉ EN CONCURRENCE |
| 9 | Intégrité après concurrence | `integrityHoldsAfterConcurrentRounds` | 1 contrôle global | Aucun événement orphelin ; aucun chevauchement CONFIRMED/IN_PROGRESS persisté | VALIDÉ SOUS POSTGRESQL |
| 10 | Confirmation vs passage en maintenance | `BookingMaintenanceConcurrencyPostgresIT` (existant, conservé) | 20 | Une seule opération réussit ; jamais CONFIRMED sur véhicule MAINTENANCE | VALIDÉ EN CONCURRENCE |

Résultats PostgreSQL réels :
- `BookingConcurrencyScenariosPostgresIT` : 8 tests, 0 échec, 0 erreur, 0 ignoré (BUILD SUCCESS, log : `jdbc:postgresql://127.0.0.1:15432/elite_a4_test`).
- `BookingMaintenanceConcurrencyPostgresIT` : 1 test, 0 échec, 0 erreur, 0 ignoré.
- `BookingPendingConcurrencyPostgresIT` : 1 test, 0 échec, 0 erreur, 0 ignoré.
- `mvnw.cmd -B -ntp -Ppostgres-test clean verify` : 375 tests H2/unitaires (surefire) et 32 tests PostgreSQL d'intégration (failsafe), 0 échec, 0 erreur, 0 ignoré, BUILD SUCCESS. Les classes `elite_a4_tester` restent exclues par le profil et ne sont pas comptées comme validées.

Anomalies rencontrées pendant ce passage (toutes côté test, aucun changement de règle métier ni de code de production) :
- Noms de colonnes faux dans les assertions SQL (`start_date_time`/`end_date_time`) : corrigés en `starts_at`/`ends_at` (V1/V4).
- Scénario 7 : l'historique conserve un `BOOKING_CONFIRMED` antérieur à l'annulation ; l'assertion supposait à tort une absence. Corrigée pour vérifier la cohérence de la séquence.
- Helper de course qui traitait toute `RuntimeException` comme un échec métier, pouvant masquer une vraie erreur : restreint à `BookingException` et `DomainException` (refus métier explicites).

Limites restantes : Driver et Transfer verrouillent la réservation sans véhicule (limitation documentée dans `docs/architecture/booking-lock-order.md`, non prouvée comme défaut). Le scénario 5 valide l'invariant final, pas toutes les issues possibles de la course. Blocage administrateur inchangé : `elite_a4_tester` (`PaymentBookingPostgresTest`, `PaymentQuotePostgresA4Test`), à débloquer par `docs/audits/grant-a4-tester-createdb.sql` côté administrateur, sans contournement.

Dépôt : 189 entrées au début du lot, 191 à la fin (fichiers de lots antérieurs et nouveaux tests du lot ; aucun commit, aucun `git add`, aucune modification existante annulée).

Prochain lot recommandé : disponibilité véhicule et partenaires (section 15), hors périmètre de ce lot.

## 14.15 Disponibilité véhicules et partenaires (lot en cours)

Statut : **VALIDÉ SOUS POSTGRESQL** pour les scénarios garanties, retraits, transferts et intégrité ; **VALIDÉ EN CONCURRENCE** pour les courses listées ci-dessous (5 à 10 tours chacune, non répétées massivement). Le lot n'est pas clos pour PRODUCTION READY (voir limites).

Base : PostgreSQL 18.3, `jdbc:postgresql://127.0.0.1:15432/elite_a4_test`, rôle `elite_test` (confirmé dans les logs Flyway).

Test ajouté : `backend/src/test/java/tn/elitetunisia/partner/PartnerAvailabilityPostgresIT.java`, 13 tests, 0 échec, 0 erreur, 0 ignoré (lancé deux fois).

| Scénario | Test | Résultat |
|---|---|---|
| Garantie puis confirmation dans la période | `guaranteeThenBookingInsidePeriodConfirms` | OK (5 tours) |
| Garanties disjointes simultanées | `disjointGuaranteesBothCreated` | OK (10 tours) |
| Chevauchement partiel simultané | `partialOverlappingGuaranteesOnlyOneCreated` | OK (10 tours) |
| Chevauchement total simultané | `totalOverlappingGuaranteesOnlyOneCreated` | OK (10 tours) |
| Chevauchement séquentiel refusé sans effet | `sequentialOverlapIsRefusedWithoutSideEffect` | OK |
| Retrait en course avec confirmation | `withdrawalRacingConfirmationNeverLeavesUncoveredBooking` | OK (10 tours) |
| Retrait refusé si CONFIRMED couverte ; PENDING seul et période future autorisés | `withdrawalRefusedWhileConfirmedBookingIsCovered`, `withdrawalAllowedWithPendingOnlyAndFuturePeriod` | OK |
| Deux retraits simultanés | `concurrentWithdrawalsOfSameGuaranteeSucceedOnce` | OK (10 tours) |
| Maintenance et retrait en course avec CONFIRMED | `maintenanceAndWithdrawalRacingWithConfirmedBookingBothRefused` | OK (10 tours) |
| Accès inter-partenaires | `partnerCannotAccessAnotherPartnersVehicleOrGuarantees` | OK |
| Échec de création sans persistance | `failedCreationLeavesNoGuarantee` | OK (limite : échec avant insertion, pas de rollback après insertion) |
| Triggers d'intégrité (chevauchement, DELETE, UPDATE interdit) | `databaseRejectsOverlapDeletionAndIllegalUpdates` | OK |

Non-régression existante (PostgreSQL) : `GuaranteeServiceTest` 17/17, `PartnerServiceTest` 13/13, `PartnerAssignmentHistoryTest` 3/3, `PartnerSecurityMockMvcTest` 18/18, `BookingHoldPostgresTest` 7/7 ; total 58 tests, 0 échec, 0 ignoré, BUILD SUCCESS.

Build complet : `.\mvnw.cmd -B -ntp -Ppostgres-test clean verify` : BUILD SUCCESS. Surefire (H2 et unitaires) 375 tests, 0 échec, 0 erreur, 0 ignoré. Failsafe (PostgreSQL et HTTP) 45 tests, 0 échec, 0 erreur, 0 ignoré. Logs : `jdbc:postgresql://127.0.0.1:15432/elite_a4_test (PostgreSQL 18.3)`.

Non exécuté (à ne pas compter comme validé) :
- `PartnerMigrationPostgresTest` (18 tests) : désactivé sans `ELITE_A4_PG_TEST=true`. Il crée une base temporaire `et_a4_it_*` via `postgres`, donc il exige un droit CREATEDB. Non lancé, car je ne modifie pas les privilèges. Décision : à traiter avec l'administrateur ou à réécrire sans création de base.

Limites restantes :
- Répétitions : 5 à 10 tours par course, pas de campagne de répétition longue.
- Rollback après insertion non simulé.
- Chevauchement de garanties : la règle « un retrait refusé dès qu'une réservation CONFIRMED/IN_PROGRESS chevauche la période » est appliquée, plus stricte que « couverte ». À confirmer avec le métier.
- Blocages inchangés : `elite_a4_tester` absent (4 classes), décisions D3, D11 et politique de session ouvertes.

## 14.16 Scénarios de paiement PostgreSQL (fournisseur de test contrôlé)

Base : `elite_a4_test` (port 15432, rôle `elite_test`). Le test vérifie `current_database()` avant chaque scénario. Fournisseur : `ControlledPaymentProvider` (test uniquement, non bean, aucun appel réseau). Aucune modification du workflow financier.

Classe : `backend/src/test/java/tn/elitetunisia/payment/PaymentScenariosPostgresIT.java`, 10 tests (9 scénarios, le 6 existe en deux variantes).

| # | Test | Objectif | Résultat |
|---|---|---|---|
| 1 | `s1_providerError_leavesPendingWithoutCaptureAndBlocksRetry` | Erreur fournisseur : PENDING, aucune capture, nouvelle clé bloquée | Réussi |
| 2 | `s2_providerTimeout_leavesPendingAndNeverCountsCapture` | Timeout : traité comme résultat inconnu, PENDING | Réussi |
| 3 | `s3_lostResponseAfterCapture_replayDoesNotCallProviderAgain` | Réponse perdue après capture : rejeu sans second appel | Réussi (écart : capture non enregistrée localement) |
| 4 | `s4_captureThenFinishFailure_rollsBackAndDoesNotAllowSecondCapture` | Échec de finish après capture : rollback, pas de second débit | Réussi (écart : référence fournisseur perdue) |
| 5 | `s5_replaySameKeyAfterCapture_returnsSamePaymentWithoutNewCapture` | Rejeu après capture : même paiement | Réussi |
| 6 | `s6_concurrentSameKey_singleRowSingleProviderCall` | 6 requêtes simultanées, même clé | Réussi |
| 6b | `s6b_concurrentDifferentKeys_onlyOneProviderCallAndOneCapture` | 4 requêtes simultanées, clés différentes | Réussi |
| 7 | `s7_replayWithDifferentAmount_isIdempotencyConflictWithoutProviderCall` | Rejeu, montant différent : IDEMPOTENCY_CONFLICT | Réussi |
| 8 | `s8_restartDuringAttempt_recoversPendingWithoutSecondCapture` | Arrêt pendant l'appel puis redémarrage | Réussi (PENDING conservé, aucun appel) |
| 9 | `s9_uncertainAttempt_providerKnowsCaptureButLocalStatePendingBlocksQuote` | Réconciliation | **Mesure de l'écart, NON VALIDÉ** : aucun mécanisme de réconciliation n'existe (décision en attente) |

Exécution : `mvn clean verify -Ppostgres-test` (PowerShell, `$LASTEXITCODE` = 0). Logs : `files\payment-scenarios-run1.log` (Failsafe ciblé, 10/10) et `files\clean-verify-pg-run2.log` (complet). Surefire : 379 tests, 0 échec, 0 erreur, 0 ignoré. Failsafe : 59 tests, 0 échec, 0 erreur, 0 ignoré (49 précédents + 10).

Anomalies techniques identifiées (non corrigées, à traiter après décision métier) :
- Capture réussie puis échec de `finish` : le paiement reste PENDING sans référence fournisseur (scénario 4). Risque de perte d'information financière.
- Réponse perdue après capture (scénario 3) : même effet.
- PENDING après PROVIDER_ERROR ou timeout bloque le devis (`PAYMENT_IN_PROGRESS`) sans reprise automatique (scénarios 1, 2, 8, 9).

Git : 196 entrées (194 + `ControlledPaymentProvider.java` + `PaymentScenariosPostgresIT.java`). Aucun commit, aucune suppression.

## 14.17 Workflow UNKNOWN et réconciliation des paiements (2026-11-16)

**Statut : TECHNIQUEMENT IMPLÉMENTÉ ET VALIDÉ SUR POSTGRESQL RÉEL (`elite_a4_test`, port 15432, rôle `elite_test`).** Les règles de transition restent une proposition technique : la validation métier de `UNKNOWN` et de `PROVIDER_ERROR` reste requise (voir `ELITE_TUNISIA_PAYMENT_DECISIONS.md`).

Changements :
- `PaymentStatus` : ajout de `UNKNOWN` et `MANUAL_REVIEW`, table `canTransitionTo` (PENDING→CAPTURED/FAILED/UNKNOWN ; UNKNOWN→CAPTURED/FAILED/MANUAL_REVIEW ; le reste interdit). `PaymentEntity.transitionTo` lève une exception sur transition illégale.
- `PaymentService` : timeout, erreur ou réponse perdue du fournisseur → `UNKNOWN` (événement `PROVIDER_UNKNOWN`), jamais `FAILED`. Échec de `finish` après capture → transaction séparée qui persiste la référence fournisseur et passe en `UNKNOWN` (événement `FINISH_FAILED`). Le statut est relu en base avant cette écriture : l'état en mémoire est annulé par le rollback.
- `PaymentReconciliationService` (nouveau) : `reconcile(id)` consulte le fournisseur par clé d'idempotence **hors transaction**, puis applique le résultat sous verrou (réservation d'abord, paiement en `SKIP LOCKED`, contrôle de statut). Preuve de capture → `CAPTURED` ; preuve de refus → `FAILED` ; sans preuve → reste `UNKNOWN` (`RECONCILE_INCONCLUSIVE`). `requireManualReview` : `UNKNOWN`→`MANUAL_REVIEW` tracé (`MANUAL_REVIEW`). `reconcileAll` existe mais n'est pas planifié.
- `PaymentProvider.lookup` : méthode par défaut retournant vide (aucun fournisseur réel). Sans capacité de lecture, la réconciliation reste INCONCLUSIVE, ce qui est le comportement sûr.
- Aucune migration : `payments.status` est un `varchar(30)` sans CHECK (V6), donc `UNKNOWN` et `MANUAL_REVIEW` ne nécessitent aucune modification de schéma.

Tests PostgreSQL (`PaymentScenariosPostgresIT`, 16 méthodes, `current_database()` = `elite_a4_test` vérifié) :

| Test | Résultat |
|---|---|
| `s1_providerError_leavesUnknownWithoutCaptureAndBlocksRetry` | Réussi |
| `s2_providerTimeout_leavesUnknownAndNeverCountsCapture` | Réussi |
| `s3_lostResponseAfterCapture_replayDoesNotCallProviderAgain` | Réussi (référence null, rejeu sans appel) |
| `s4_captureThenFinishFailure_persistsReferenceAsUnknownWithoutSecondCapture` | Réussi (référence persistée, `FINISH_FAILED`) |
| `s5_replaySameKeyAfterCapture_returnsSamePaymentWithoutNewCapture` | Réussi |
| `s6_concurrentSameKey_singleRowSingleProviderCall` | Réussi |
| `s6b_concurrentDifferentKeys_onlyOneProviderCallAndOneCapture` | Réussi |
| `s7_replayWithDifferentAmount_isIdempotencyConflictWithoutProviderCall` | Réussi |
| `s8_restartDuringAttempt_recoversPendingWithoutSecondCapture` | Réussi |
| `s9a_restartAfterCapture_reconciliationRecoversCaptureWithoutSecondCapture` | Réussi |
| `s9b_pendingWithoutProof_reconciliationMovesToUnknown` | Réussi |
| `s9c_unknownWithCaptureProof_reconciliationMovesToCaptured` | Réussi |
| `s9d_unknownWithFailureProof_reconciliationMovesToFailed` | Réussi |
| `s9e_stillUncertain_staysUnknownAndBlocksNewAttempt` | Réussi |
| `s9f_concurrentReconciliation_singleConfirmation` | Réussi |
| `s9g_adminManualReview_isTracedAndBlocksQuote` | Réussi |

Résultats : PaymentScenariosPostgresIT 16 exécutés, 0 échec, 0 erreur, 0 ignoré (`files\it-scenarios-unknown-run2.log`, rapport `backend\target\failsafe-reports\tn.elitetunisia.payment.PaymentScenariosPostgresIT.txt`). Un premier passage a révélé une régression de ma part (relecture en mémoire après rollback, corrigée) : s4 échouait, le statut restait CAPTURED.

Tests unitaires ciblés (`PaymentServiceUnitTest`, `PaymentQuoteInvariantUnitTest`) : 17 exécutés, 0 échec (`files\unit-payment-unknown.log`). Deux tests avaient échoué à cause d'une relecture `findById` non mockée ; corrigé en passant par la relecture réelle.

Suite complète `mvn clean verify -Ppostgres-test` : exit code Maven 0, BUILD SUCCESS, log `files\clean-verify-unknown.log`. Surefire : 379 exécutés, 0 échec, 0 ignoré. Failsafe : 65 exécutés, 0 échec, 0 ignoré (59 précédents + 6 nouveaux cas de réconciliation et de variantes ; le compte exact est dans le rapport `backend\target\failsafe-reports`).

Points restant ouverts (non implémentés volontairement) :
- Une réponse fournisseur synchrone `CAPTURED` est encore acceptée directement dans le chemin normal, sans lecture de confirmation. La règle « une réponse HTTP n'est pas une preuve » suggère de l'imposer : décision à valider.
- Le planificateur de réconciliation n'est pas branché.
- `MANUAL_REVIEW` n'a aucune transition sortante : la résolution administrative est à définir.
- Aucun fournisseur réel : la lecture par clé d'idempotence n'est testée que sur le fournisseur de test.
- Ordre de verrouillage : la réconciliation prend la réservation puis le paiement, alors que `finish` prend le paiement puis la réservation. Risque d'inversion à auditer avant mise en production.
- Git : 200 entrées (196 + `PaymentReconciliationService.java`). Les trois autres entrées n'ont pas été comparées à la liste précédente ; à réconcilier. Aucun commit, aucun reset, aucun clean.

## 14.18 Sécurisation des transitions, planificateur de réconciliation (2026-11-16)

Sécurisation : `PaymentEntity.setStatus` supprimé. Aucun code de production ne l'appelait ; seul `CustomerPaymentControllerMockMvcTest` l'utilisait, remplacé par `transitionTo(CAPTURED)`. Mapping JPA sur champs (`@Id` sur champ), donc aucun setter n'est requis. Test ajouté : `PaymentEntityTransitionUnitTest` (transitions légales et refus des transitions illégales).

Planificateur : `PaymentReconciliationWorker` (désactivé par défaut, `app.payment.reconciliation.enabled=true`). Lots de taille `batch-size` (défaut 50), tentatives PENDING plus anciennes que `pending-older-than` (défaut PT15M) et UNKNOWN. Verrou advisory de session `7412031001` sur connexion dédiée (protège aussi entre instances). Erreurs isolées par élément (`ERROR:` dans le détail, journalisées). Aucun appel de capture.

Tests PostgreSQL ajoutés dans `PaymentScenariosPostgresIT` : s13 (deux réconciliations simultanées, une seule confirmation), s14 (cycle bloqué par un verrou détenu ailleurs, aucune modification, reprise après libération), s15 (capture prouvée récupérée, incertain conservé en UNKNOWN, second cycle sans effet).

Résultats (base `elite_a4_test`, cluster `127.0.0.1:15432`, rôle `elite_test`) :
- `PaymentScenariosPostgresIT` : 22 exécutés, 0 échec, 0 erreur, 0 ignoré, code de sortie 0. Log : `files\it-worker.log`. Rapport : `backend\target\failsafe-reports`.
- `mvn clean verify -Ppostgres-test` : code de sortie 0, BUILD SUCCESS. Surefire 381 exécutés, 0 échec, 0 erreur, 0 ignoré. Failsafe 71 exécutés, 0 échec, 0 erreur, 0 ignoré. Log : `files\verify-full-worker.log`.

Non réalisé dans cette étape : test unitaire de la valeur par défaut désactivée (couverte par la condition `@ConditionalOnProperty`, non vérifiée par test), audit des trois entrées Git inexpliquées, migrations V21–V33, E2E, Angular, sécurité, performance, sauvegarde et déploiement. Verdict inchangé : `NOT READY FOR PRODUCTION`.

## 15. Verdict

**NON PRÊT POUR LA PRODUCTION.** Les 9 scénarios de paiement (16 méthodes de test) sont validés sur PostgreSQL réel, y compris la réconciliation (section 14.17). Le build complet `clean verify -Ppostgres-test` est BUILD SUCCESS (exit 0) avec 379 Surefire et 65 Failsafe, 0 ignoré. Les règles `UNKNOWN` et `PROVIDER_ERROR` restent à valider par le métier. Restent bloquants : service Windows non installé (admin), 4 classes SQL brutes NON VALIDÉES (`elite_a4_tester` absent), décisions D3, D11 et paiement `PENDING`/`UNKNOWN` en attente, intégration prestataire de paiement absente, lots restants (disponibilité publique, V21–V33 sur bases vierge et peuplée, E2E, Angular, sécurité, performance, sauvegarde/restauration, déploiement). Statut : PARTIELLEMENT VALIDÉ.
