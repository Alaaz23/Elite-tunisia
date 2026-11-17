# Elite Tunisia — Audit de complétion du projet (Phase 0)

> Audit en lecture seule. Aucun code, aucune migration, aucune base PostgreSQL, aucun commit, push ou déploiement n'a été touché. Aucun test n'a été exécuté dans cette phase : les résultats de tests cités proviennent de logs existants et sont marqués comme tels.

## 0. Légende

- Gravité : `CRITICAL` (bloque la mise en production ou expose des données / de l'argent), `HIGH`, `MEDIUM`, `LOW`, `INFORMATIONAL`.
- Libellés de preuve : `[CONFIRMÉ]` vérifié dans le code ou un fichier ; `[CONSTAT]` observé sans test dédié ; `[HYPOTHÈSE]` ; `[À VÉRIFIER]` nécessite un test, une base isolée ou un environnement de déploiement.
- Priorités : P0 bloquant ; P1 cœur marketplace ou pré-production ; P2 expérience et exploitation ; P3 évolutions ou nettoyage.

## 1. Périmètre, état Git et méthode

| Élément | Valeur |
|---|---|
| Branche | `main` |
| HEAD | `78da39b` (« Audit & sécurité ») |
| Modifications locales | 9 fichiers suivis modifiés (95 insertions, 66 suppressions) ; 4 fichiers de test non suivis ; dossiers `database/proposed/`, `docs/architecture/` et `docs/technical-audit.md`, `scripts/test-backend-postgres.ps1` non suivis |
| Traitement | Préservé. Aucun fichier n'a été restauré, supprimé ou déplacé. |

Fichiers lus ou recherchés : `.github/workflows/ci.yml`, `backend/pom.xml`, `backend/src/main/**`, `backend/src/main/resources/**`, `database/migrations/V1` à `V20` (V4, V6, V7, V19 lus ligne par ligne), `database/proposed/*`, `frontend/package.json`, `frontend/playwright.config.ts`, `frontend/src/app/app.routes.ts`, `frontend/e2e/**`, `frontend/tests/playwright/**`, `docs/technical-audit.md`, `docs/architecture/implementation-roadmap.md`, `docs/architecture/v22-*.md`, `scripts/test-backend-postgres.ps1` (lu, non exécuté).

Recherches effectuées (`grep`) : `partner_id`, `/api/partner`, `webhook|ledger|payout|commission`, `EXCLUDE USING`, `forward-headers`, `Content-Security`, `X-Frame-Options`, `readOnly`, `Pageable`, `adminList`, `notification|outbox`, `actuator|hikari`, `rentable`, `RENTED`.

## 2. Synthèse

| Gravité | Nombre | Identifiants |
|---|---|---|
| CRITICAL | 3 | AUD-01, AUD-02, AUD-03 |
| HIGH | 6 | AUD-04, AUD-05, AUD-06, AUD-07, AUD-08, AUD-09 |
| MEDIUM | 7 | AUD-10 à AUD-16 |
| LOW | 6 | AUD-17 à AUD-22 |
| INFORMATIONAL | 4 | AUD-23 à AUD-26 |

Lecture d'ensemble : le socle sécurité (authentification, rotation de jetons, CSRF, contrainte d'exclusion sur les réservations) est présent. Les écarts majeurs sont fonctionnels (marketplace, finance, paiement réel) et de reproductibilité (tests PostgreSQL, travail non commité). Aucun point n'est certifié ; cet audit ne garantit pas l'absence de bogues.

## 3. Points conformes vérifiés

- Contrainte `bookings_no_blocking_overlap` (EXCLUDE gist sur `CONFIRMED`/`IN_PROGRESS`) : [CONFIRMÉ] [database/migrations/V4__booking_engine.sql](C:/Users/aaziza/Desktop/test/database/migrations/V4__booking_engine.sql) ligne 25.
- Index d'idempotence unique sur `payments.idempotency_key` : [CONFIRMÉ] V6 ligne 20.
- Un utilisateur désactivé est refusé à chaque requête (et pas seulement au refresh) : [CONFIRMÉ] par la roadmap §3 (C1), non re-testé dans cette phase.
- Rotation et détection de rejeu des refresh tokens, CSRF en cookie `SameSite=Strict` : [CONFIRMÉ] par la roadmap §4, non re-testé dans cette phase.
- Pagination bornée sur la liste des devis (`size` plafonné à 100) : [CONFIRMÉ] `QuoteService.list`, ligne 121 et suivantes.

## 4. Constats

| ID | Gravité | Constat | Type de preuve | Preuve (fichier, ligne) | Priorité | Tâche(s) |
|---|---|---|---|---|---|---|
| AUD-01 | CRITICAL | Aucun modèle partenaire ni isolation par partenaire. | [CONFIRMÉ] | `partner/package-info.java` seul ; aucune occurrence de `partner_id` dans `database/migrations` ni `backend/src/main` ; table `partners` V1 ligne 181 jamais utilisée ; aucun préfixe `/api/partner` dans `SecurityConfig.java` | P0 | T-C01, T-C02, T-C03 |
| AUD-02 | CRITICAL | Pas de garantie en base d'un seul paiement `CAPTURED` par devis. | [CONFIRMÉ] en base ; [CONSTAT] garde applicative | V21 seulement dans `database/proposed/` (non appliquée) ; `payments_idempotency_idx` ne couvre que la clé d'idempotence (V6 ligne 20) ; garde applicative modifiée localement dans `PaymentService.java` (non commitée) | P0 | T-A04 |
| AUD-03 | CRITICAL | Aucun flux de remboursement, aucun lien paiement ↔ réservation, fournisseur simulé. Les colonnes `refunded_at` / `refunded_amount` existent sans flux. | [CONFIRMÉ] | `PaymentEntity.java` lignes 27–28 ; absence de code de remboursement dans `backend/src/main` | P1 (bloquant paiement réel) | T-F01, T-F02 |
| AUD-04 | HIGH | Pas de commission, grand livre ni reversement partenaire. | [CONFIRMÉ] | Aucune occurrence de `webhook`, `ledger`, `payout`, `commission` dans `backend/src/main` (seule la colonne V1 `commission_rate`) | P1 | T-G01, T-G02, T-G03 |
| AUD-05 | HIGH | La disponibilité exige `VehicleStatus.AVAILABLE` : un véhicule `RENTED` est refusé pour toute période, y compris future. | [CONFIRMÉ] | `BookingService.java` lignes 193–199 (`rentable` puis `requireAvailable`) ; `BookingOperationService.java` ligne 48 passe le véhicule à `RENTED` ; défaut présent dans l'arbre de travail (le diff local ne touche pas ces lignes) | P0 | T-A05 |
| AUD-06 | HIGH | Non-chevauchement des transferts par chauffeur uniquement au niveau applicatif. Pas de contrainte d'exclusion en base. | [CONFIRMÉ] absence de contrainte ; [HYPOTHÈSE] course concurrente | `TransferService.java` lignes 116, 121–122 ; `TransferRepository.java` lignes 23–26 ; aucune occurrence de `EXCLUDE` hors V4 | P1 | T-I01 |
| AUD-07 | HIGH | Chemin devis → facture et `attachPayment` sans appelant en production. | [CONFIRMÉ] par la roadmap §4 (ISS-04) ; [À VÉRIFIER] par recherche des appelants | `InvoiceService.createFromQuote` / `attachPayment` ; roadmap §1 | P1 | T-H01 |
| AUD-08 | HIGH | Reproductibilité des tests PostgreSQL non établie. `verify12` a échoué (409, `users_email_key`), cause non confirmée. Le script de test est destructif et non suivi par Git. | [CONFIRMÉ] script destructif ; [CONSTAT] échec ; cause [HYPOTHÈSE] | `scripts/test-backend-postgres.ps1` ligne 44 (`DROP DATABASE ... WITH (FORCE)`) ; logs `verify12` cités par la roadmap §13 | P0 | T-A01 |
| AUD-09 | HIGH | Travail non commité : tests PostgreSQL, migrations proposées, documents et script de test ne sont pas dans l'historique. Risque de perte et de divergence avec la CI. | [CONFIRMÉ] | Sortie `git status` : 9 fichiers modifiés, 4 tests non suivis, `database/proposed/`, `docs/architecture/`, `scripts/` non suivis | P0 (processus) | Décision utilisateur |
| AUD-10 | MEDIUM | En-têtes proxy non configurés. `server.forward-headers-strategy` absent des ressources. Le throttle de connexion se base sur l'IP. Derrière un reverse proxy, toutes les connexions partagent une IP ; si les en-têtes sont activés sans proxy de confiance, le throttle devient contournable. | [CONFIRMÉ] absence ; [HYPOTHÈSE] impact selon déploiement | `LoginThrottle.java` ligne 22 (« à définir au déploiement ») ; aucune occurrence dans `backend/src/main/resources` | P1 | T-L01 |
| AUD-11 | MEDIUM | Throttle de connexion en mémoire, non partagé entre instances. | [CONFIRMÉ] | `auth/service/LoginThrottle.java` (mémoire) ; roadmap ISS-11 | P1 | T-L01 |
| AUD-12 | MEDIUM | Pas de Content-Security-Policy applicative. Les en-têtes par défaut de Spring Security ne sont pas vérifiés. | [CONFIRMÉ] absence de CSP dans le code ; [À VÉRIFIER] en-têtes réellement renvoyés | Aucune occurrence de `Content-Security` ni `X-Frame-Options` dans `backend/src/main` ni `frontend/src` | P1 | T-L01 |
| AUD-13 | MEDIUM | Pas de job E2E en CI. Les 2 specs de `frontend/tests/playwright/` ne sont pas dans le `testDir` de la configuration (`./e2e`) et ne sont jamais lancées. | [CONFIRMÉ] | `.github/workflows/ci.yml` : 4 jobs, aucun Playwright ; `frontend/playwright.config.ts` ligne 4 ; `frontend/tests/playwright/*.spec.ts` | P1 | T-A06 |
| AUD-14 | MEDIUM | Version Node non homogène. Node 18.20.8 installé localement, incompatible avec `engines`. La CI épingle 24.15.0, mais `engines` accepte aussi `>=26`. | [CONFIRMÉ] | `frontend/package.json` (`engines`) ; sortie d'environnement ; roadmap §3 (C2) | P1 | T-A06 |
| AUD-15 | MEDIUM | Liste admin des factures non paginée, chargée entièrement en mémoire. | [CONFIRMÉ] | `InvoiceService.java` ligne 86 (`invoices.findAll()`) ; `AdminInvoiceController.java` ligne 13 | P2 | T-A08 |
| AUD-16 | MEDIUM | Contrainte de statut transfert ajoutée `NOT VALID` : les lignes historiques ne sont pas validées. | [CONFIRMÉ] DDL ; [À VÉRIFIER] données réelles | `database/migrations/V19__transfer_booking_link_and_lifecycle.sql` ligne 16 | P2 | T-I01 |
| AUD-17 | LOW | `QuoteService.get` ouvre une transaction écrivante (verrou pessimiste, `expireIfDue`). Le critère « `get` en readOnly » n'est pas atteint. Le choix se justifie par l'expiration, mais doit être séparé. | [CONFIRMÉ] | `QuoteService.java` lignes 114–118 ; `list` readOnly ligne 120 | P2 | T-A07 |
| AUD-18 | LOW | Routes placeholder encore servies : `/chauffeur` et `/admin/partners` (12 routes au total utilisent `page` ; voir [business-decisions-pending.md](./business-decisions-pending.md) P-01). | [CONFIRMÉ] | `frontend/src/app/app.routes.ts` lignes 42 à 47, 58, 74, 84, 96, 98, 100 | P2 | T-L04 |
| AUD-19 | LOW | Migrations inertes : V11 en double dans `backend/src/main/resources/db/migration/` (exclue du packaging par `pom.xml`), copies `_DISABLED_V5` / `_DISABLED_V6`. Risque de confusion. | [CONFIRMÉ] | Répertoire `backend/src/main/resources/db/migration/` ; exclusion dans `backend/pom.xml` | P3 | T-L03 (DEFERRED) |
| AUD-20 | LOW | Swagger et `api-docs` accessibles sans authentification. Doit être désactivé en profil production. | [CONFIRMÉ] permitAll ; [À VÉRIFIER] profil prod | `SecurityConfig.java` ligne 80 | P2 | T-L01 |
| AUD-21 | LOW | Actuator : seul `/actuator/health/**` est en `permitAll`. L'exposition des autres endpoints, le pool Hikari et la procédure de sauvegarde / restauration ne sont pas documentés. | [CONFIRMÉ] `starter-actuator` (`backend/pom.xml` ligne 41) ; [À VÉRIFIER] le reste | `SecurityConfig.java` ligne 80 | P1 | T-L02 |
| AUD-22 | LOW | Incohérence de référence : le backlog dépend de « D-02 » pour T-A05, alors que la §15 de la roadmap numérote D1–D15 (D2 = fréquence des reversements, sans rapport avec la disponibilité). | [CONFIRMÉ] | `implementation-roadmap.md` §11 et §15 | P2 | Correction documentaire |
| AUD-23 | INFORMATIONAL | Brief original de la mission V22 introuvable. Les 12 critères et les 13 sections ne sont pas confirmables. | [CONFIRMÉ] (déjà constaté en phase 1.2) | `docs/architecture/v22-validation-report.md` | — | — |
| AUD-24 | INFORMATIONAL | La roadmap affirme « Aucune tâche n'est implémentée », ce qui n'est plus exact : A02, A03 et A04 existent dans l'arbre de travail. | [CONFIRMÉ] | `implementation-roadmap.md` ligne 3 ; fichiers listés en §1 | — | Mise à jour documentaire |
| AUD-25 | INFORMATIONAL | Documentation `features/vehicles/README.md` obsolète : le catalogue est implémenté. | [CONFIRMÉ] | `frontend/src/app/features/vehicles/README.md` | — | Mise à jour documentaire |
| AUD-26 | INFORMATIONAL | Environnement local : JDK par défaut 8, JDK 21 à imposer explicitement ; Node 18 incompatible. | [CONFIRMÉ] | Sortie d'environnement | — | Documentation de prérequis |

## 5. Hypothèses et points non vérifiés

- `[HYPOTHÈSE]` Le comportement transactionnel de Flyway (migrations dans une transaction par défaut) n'est pas prouvé. Il conditionne la validité de `SET LOCAL lock_timeout` dans V22.
- `[HYPOTHÈSE]` La version du serveur PostgreSQL utilisée en CI et en test est supposée 18 (job `backend-postgres`), sans preuve d'exécution dans cette phase.
- `[À VÉRIFIER]` Les durées réelles de verrouillage et l'échec éventuel de `verify12` ne peuvent être confirmés qu'en Phase 2, sur base isolée et après autorisation.
- `[À VÉRIFIER]` Les logs `verify6` à `verify13` (164 tests unitaires, 25 tests d'intégration PostgreSQL, 21 E2E) ne sont pas reproduits ici.
- `[À VÉRIFIER]` Présence de sauvegarde et de restauration documentées et testées : aucune procédure trouvée dans cette phase.
- `[À VÉRIFIER]` Exposition réelle des endpoints actuator et Swagger selon le profil de production.
- `[À VÉRIFIER]` Présence d'un flux d'upload de documents (table `documents` V1 ligne 201 existante ; aucun flux identifié).
- Non vérifiable dans cet environnement : protection réelle contre la force brute sous charge, comportement derrière un reverse proxy de production, performance des requêtes sous volume.

## 6. Grille de contrôle (OWASP ASVS, indicative)

Cette grille sert de référence de revue. Elle ne constitue pas une certification.

| Domaine ASVS | Constat principal | Statut |
|---|---|---|
| V2 / V3 Authentification et sessions | Rotation des refresh tokens confirmée ; throttle en mémoire (AUD-11) | Partiel |
| V4 Contrôle d'accès | Isolation par partenaire absente (AUD-01) ; `admin:access` trop large (roadmap §4) | Non conforme au modèle cible |
| V5 Validation des entrées | Non audité en détail dans cette phase | Non vérifié |
| V7 Journalisation et erreurs | Échec d'audit avalé (ISS-02, T-A03) | Partiel, corrigé localement, non prouvé |
| V13 API | Swagger accessible (AUD-20) | À vérifier |
| V14 Configuration | Pas de CSP (AUD-12) ; proxy non configuré (AUD-10) | Partiel |
| V8 / V9 Données et communications | Non audité en détail (HTTPS terminé au niveau du proxy, à vérifier) | Non vérifié |

## 7. Conclusion de l'audit

Le projet est exploitable pour des tests internes d'une agence unique. Il n'est pas prêt pour une marketplace multi-partenaires ni pour un paiement réel. Les trois constats CRITICAL doivent être traités avant toute ouverture publique. Les tâches détaillées, leurs dépendances et les décisions à prendre sont dans [master-project-completion-plan.md](C:/Users/aaziza/Desktop/test/docs/architecture/master-project-completion-plan.md) et dans [implementation-roadmap.md](C:/Users/aaziza/Desktop/test/docs/architecture/implementation-roadmap.md) (§11 bis).
