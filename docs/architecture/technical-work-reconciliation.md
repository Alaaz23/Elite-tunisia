# Réconciliation des travaux techniques — Phase 0.2

Date de la mission : Phase 0.2 (documentaire uniquement).
Périmètre : les 33 tâches de [implementation-roadmap.md](./implementation-roadmap.md) (§11 et §11 bis), les résultats de tests historiques et l'état du dépôt de travail.

## 0. Règles appliquées

- Aucun code modifié, aucune migration, aucune connexion ni SQL PostgreSQL, aucun test exécuté, aucun commit, aucune suppression.
- Le script [scripts/test-backend-postgres.ps1](../../scripts/test-backend-postgres.ps1) n'a pas été lancé (il est non suivi et exécute `DROP DATABASE ... WITH (FORCE)`).
- Les modifications locales (9 fichiers suivis modifiés et fichiers non suivis) n'ont pas été touchées.
- Aucune tâche n'est déclarée réalisée, non réalisée ou validée sur la seule base d'une lecture documentaire. Chaque statut est rattaché à une preuve observée, ou marqué « non prouvé ».

## 1. Définitions utilisées pour les résultats de tests

| Classement | Signification dans ce document |
|---|---|
| PREVIOUSLY EXECUTED | Exécution consignée dans un document rattaché à une étape identifiée. Non rejouée ici. |
| REPRODUCED | Exécution refaite pendant cette mission avec le même résultat. **Aucun test n'ayant été exécuté pendant cette mission, aucun résultat n'est dans cette catégorie.** |
| NOT REPRODUCED | Exécution refaite pendant cette mission avec un résultat différent. **Aucune.** |
| UNKNOWN | Chiffre ou exécution sans log joint, sans étape identifiable, ou contradictoire avec un autre chiffre. |
| BLOCKED | Reproduction impossible dans le cadre de cette mission (interdiction de connexion PostgreSQL, de tests, ou environnement non disponible). |

## 2. Tableau des 33 tâches

Légende des statuts : ceux de la roadmap §11 bis, sans modification. Les colonnes « Constat » reprennent ce qui est observable dans le dépôt de travail.

| ID | Statut (roadmap §11 bis) | Implémentation observée | Preuve historique | Reproduit dans cette mission | Validation restante |
|---|---|---|---|---|---|
| T-A01 | IN PROGRESS | Script `scripts/test-backend-postgres.ps1` non suivi, destructif. Cause de `verify12` non identifiée. | Aucune trace de `verify12` sur disque. | Non (BLOCKED) | Base jetable par exécution ; diagnostic de `verify12` ; 3 exécutions `verify` consécutives sans échec. |
| T-A02 | IMPLEMENTED | `BookingLockOrderPostgresIT` non suivi (`ROUNDS = 20`, ligne 38), `BookingService` et `BookingOperationService` modifiés localement. | `verify13` cité par la roadmap, log absent. | Non (BLOCKED) | **Écart de critère** : la roadmap exige 100 itérations, le test en fait 20. Décider : porter à 100, ou corriger le critère. Puis exécuter. |
| T-A03 | IMPLEMENTED | `BookingAuditAtomicityPostgresIT` non suivi (4 tests) ; `BookingOperationService` modifié localement. | Cité par `verify13`, log absent. | Non (BLOCKED) | Exécution PostgreSQL isolée ; vérifier que l'échec d'audit annule l'opération. |
| T-A04 | IN PROGRESS | `PaymentQuoteCapturePostgresIT` non suivi (6 threads, ligne 51) ; `PaymentQuoteInvariantUnitTest` non suivi ; `PaymentService` modifié ; V21 non appliquée (`database/proposed/`). | Non documentée. | Non (BLOCKED) | Voir §4.4 : conflit avec D5 (acompte + solde). |
| T-A05 | NOT STARTED | Aucun code. `BookingService.rentable` lignes 193–199 exige `VehicleStatus.AVAILABLE` (NEW-01). | — | — | Voir §4.5. |
| T-A06 | IN PROGRESS | Node 24.15.0 épinglé dans `ci.yml` (job frontend). Aucun job E2E dans `ci.yml` (4 jobs : `backend-h2`, `backend-postgres`, `frontend`, `dependency-review`). | La roadmap annonce un job E2E : non présent dans `ci.yml`. | Non | Job E2E avec conservation des rapports. |
| T-A07 | IN PROGRESS | `QuoteService.list` en `readOnly`. `QuoteService.get` reste en transaction d'écriture. | — | Non | `get` en lecture seule ; expiration confiée à un worker. |
| T-A08 | NOT STARTED | `InvoiceService.adminList` ligne 86 : `invoices.findAll()`, sans pagination. | — | — | Pagination et taille maximale bornée. |
| T-B01 | BLOCKED | Aucune décision tranchée dans les documents. | — | — | Décisions propriétaire (voir [business-decision-analysis.md](./business-decision-analysis.md) §4). |
| T-C01 | NOT STARTED | Aucune entité partenaire opérationnelle. `partners.commission_rate` existe en V1 seulement. | — | — | Entité `Partner`, partenaire interne, rôles `PARTNER_*`. |
| T-C02 | NOT STARTED | Aucun préfixe `/api/partner/**`. | — | — | Matrice d'isolation : 100 % des endpoints A→B refusés. |
| T-C03 | NOT STARTED | Aucun rattachement véhicule–partenaire. | — | — | `partner_id`, plaque, caution, publication. |
| T-C04 | NOT STARTED | Aucun document partenaire ou véhicule. | — | — | Types et tailles contrôlés ; accès par propriétaire. |
| T-D01 | NOT STARTED | Aucune table de blocages. | — | — | Table de blocages ; `EXCLUDE` cohérent. |
| T-D02 | NOT STARTED | Pas de règles de prix structurées (un montant fixe `QUOTE_DEFAULT_TAX_AMOUNT`, défaut 0, `application.yml` ligne 35). | — | — | Règles de prix testées sur table de cas. |
| T-D03 | NOT STARTED | Pas de zones structurées. | — | — | Réservation référence une zone valide. |
| T-E01 | IN PROGRESS | Confirmation admin présente : `AdminBookingController` lignes 22–23 (`PATCH /{id}/confirm`). Motifs de refus absents. | — | Non | Motif obligatoire au refus (D10). |
| T-E02 | NOT STARTED | Pas d'expiration des `PENDING`. | — | — | Passage à `EXPIRED` après délai décidé. |
| T-E03 | BLOCKED | Pas de règle d'annulation chiffrée (`customerCancel` sans frais). | — | — | Décision D3. |
| T-F01 | BLOCKED | Colonnes `refunded_amount` et `refunded_at` (V6, V17) sans flux. Pas de lien paiement–réservation. | — | — | Décisions D3 et D5. |
| T-F02 | BLOCKED | Pas de prestataire de paiement réel. Le fournisseur simulé ne produit que CAPTURED ou FAILED. | — | — | Décision D11 et choix du prestataire. |
| T-G01 | BLOCKED | Pas de commission ni de snapshot. | — | — | Décision D1. |
| T-G02 | NOT STARTED | Pas de grand livre. | — | — | Dépend de T-G01. |
| T-G03 | NOT STARTED | Pas de reversements. | — | — | Dépend de T-G02 et de D2. |
| T-H01 | BLOCKED | `InvoiceService.createFromQuote` ligne 30 sans appelant dans `backend/src/main/java`. `createReturnChargeInvoice` ligne 64 actif. | AUD-07 à re-vérifier. | Non | Décisions D6 et D12. |
| T-I01 | IN PROGRESS | Chevauchement chauffeur contrôlé au niveau applicatif (`TransferService` lignes 116, 121–122 ; `TransferRepository` lignes 23–26). Pas de contrainte en base. | — | Non | Décision D13 à confirmer avant de poursuivre. |
| T-I02 | NOT STARTED | Pas de portail chauffeur. | — | — | Dépend de T-I01 et T-C02. |
| T-J01 | NOT STARTED | Selon la roadmap, `NotificationService` est une interface seulement (non revérifié dans cette mission). | — | — | Outbox et canal e-mail. |
| T-K01 | NOT STARTED | Pas de tableau de bord ; `/admin` est un placeholder. | — | — | Dépend de T-G02. |
| T-L01 | NOT STARTED | Pas de CSP ni de `forward-headers-strategy`. Throttle en mémoire (`LoginThrottle.java` ligne 22). | — | — | Voir §5. |
| T-L02 | IN PROGRESS | Actuator exposé (`pom.xml` ligne 41 ; `SecurityConfig` ligne 80). Hikari et sauvegarde/restauration non documentés. | — | Non | Restauration réussie sur base isolée, rapport conservé. |
| T-L03 | DEFERRED | Doublon V11 non traité. | — | — | Uniquement après autorisation explicite. |
| T-L04 | NOT STARTED | Douze routes placeholder dans `app.routes.ts` (voir §6). | — | — | Décision P-01 puis retrait ou masquage. |

Compte vérifié : NOT STARTED 17 · IN PROGRESS 7 · BLOCKED 6 · IMPLEMENTED 2 · DEFERRED 1 · TESTED 0 · VALIDATED 0 (total 33). Le compte de la roadmap est donc cohérent avec son tableau.

## 3. Tâches T-A01 à T-A04 : déjà traitées, non réimplémentées

Ces tâches ont été traitées lors de missions antérieures. Aucune réimplémentation n'est proposée. Leurs preuves historiques ne sont pas effacées. Elles ne sont pas non plus déclarées non réalisées parce qu'elles n'ont pas été retestées pendant cette mission.

| ID | Implémentation réalisée | Tests exécutés historiquement | Tests reproduits dans cette mission | Validations restantes |
|---|---|---|---|---|
| T-A01 | Script de base jetable : non suivi, non lancé. Diagnostic de `verify12` non fait. | Non documenté avec une trace. | Aucun (interdit). | Diagnostic, puis 3 exécutions `verify` consécutives. |
| T-A02 | Test de verrouillage présent (20 itérations). Code modifié localement. | `verify13` cité, log absent : UNKNOWN. | Aucun (interdit). | Décider 20 ou 100 itérations ; exécuter. |
| T-A03 | Test d'atomicité présent (4 tests). Code modifié localement. | `verify13` cité, log absent : UNKNOWN. | Aucun (interdit). | Exécuter ; vérifier l'annulation en cas d'échec d'audit. |
| T-A04 | Tests de concurrence et unitaire présents, non suivis. `PaymentService` modifié. V21 non appliquée. | Non documenté. | Aucun (interdit). | Décision D5 avant V21 ; puis exécution. |

Résultats historiques : aucune trace de `verify6` à `verify13` n'est présente sur disque. Ces exécutions ne sont donc pas rattachables à une preuve vérifiable dans ce dépôt de travail.

## 4. Points d'attention

### 4.1 T-A02 : écart de critère

`BookingLockOrderPostgresIT` lance `ROUNDS = 20` (ligne 38). Le critère de la roadmap est « 0 deadlock sur 100 itérations ». Le statut IMPLEMENTED ne doit pas être présenté comme atteignant le critère. Il faut soit augmenter `ROUNDS` à 100, soit corriger le critère par décision, soit documenter la limite.

### 4.2 Nomenclature des migrations

La roadmap affecte V21 à V35 à des tâches. Le dépôt contient déjà `database/proposed/V21__payments_one_captured_per_quote.sql` et `database/proposed/V22__payments_quote_fk_restrict.sql`, hors `database/migrations/`. Le « V22 » de la roadmap (T-C01) et le « V22 » du registre V22 sont deux objets différents. Les numéros sont indicatifs (`[HYPOTHÈSE]`, roadmap §11) et doivent être recalculés avant toute écriture.

### 4.3 Chemin critique et dépendances

La roadmap §12 indique le chemin critique « T-B01 → T-C01 → T-C03 → T-D01 → T-E01 → T-F01 → T-G01 → T-G03 ». Deux écarts avec le tableau §11 :

- T-E01 dépend de T-C03 seulement (§11), pas de T-D01. L'intercalation de T-D01 est un choix d'ordre, pas une dépendance formelle.
- La chaîne saute T-G02 (grand livre) entre T-G01 et T-G03. Or T-G03 dépend de T-G02 (§11).

Chemin corrigé proposé : T-B01 → T-C01 → T-C03 → T-E01 → T-F01 → T-G01 → T-G02 → T-G03, avec T-D01 comme prérequis de disponibilité (cohérence) à confirmer.

### 4.4 T-A04 et décision D5

Le test « 2 clés distinctes sur un devis → 1 seul CAPTURED » suppose un seul paiement encaissé par devis. Le modèle acompte + solde (D5) produit deux `CAPTURED` pour un même devis. Le contrôle actuel `PaymentService.createForQuote` (ligne 27 ; contrôles lignes 48 et 51) et l'index unique partiel de V21 imposent cette contrainte.

Conséquence : T-A04 ne peut pas être finalisée, ni V21 appliquée, tant que D5 n'est pas tranchée. Le fichier `quote-detail.component.ts` (`paymentLocked`, ligne 41) bloque aussi le paiement pour les statuts `processing`, `pending` et `success`.

### 4.5 T-A05 : position recommandée

Constats :

- Objectif : une réservation future doit être acceptée pendant une location en cours. Un chevauchement de dates doit être refusé.
- Le critère d'acceptation (roadmap §11) ne dépend d'aucune décision métier documentée. Le texte « D-02 » est une référence orpheline : aucune entrée D-02 dans la roadmap §15 (voir [business-decision-analysis.md](./business-decision-analysis.md) §3.10, P-02).
- Pourquoi P0 : `rentable` (lignes 193–199 de `BookingService`) exige `vehicle.getStatus() == VehicleStatus.AVAILABLE`. Un véhicule au statut `RENTED` est donc refusé pour une réservation future, même sans chevauchement de dates. C'est le comportement que T-A05 doit corriger.
- Fichiers : `backend/src/main/java/tn/elitetunisia/booking/BookingService.java`. Tests : à créer (PostgreSQL isolé, chevauchement et non-chevauchement).
- Risques : changer la règle de disponibilité modifie le comportement de réservation. Une règle de « marge de préparation » entre deux locations serait une règle métier non documentée.
- Critères d'acceptation : voir roadmap §11 (réservation future acceptée pendant une location en cours ; chevauchement refusé).

Position recommandée (sans modifier le statut, qui reste NOT STARTED) :

- Placer T-A05 dans le lot de stabilisation (lot A), **en correction minimale** : contrôle par chevauchement de dates, sans marge de préparation.
- La version complète (blocage par table, contrainte `EXCLUDE`) dépend de T-D01 et reste au lot D.
- Confirmation nécessaire du propriétaire sur P-02 avant de retirer la dépendance « D-02 ».

### 4.6 Tests historiques contradictoires

Voir §7.

## 5. T-L01 : proposition définitive

Évaluation :

- **T-L01a** (en-têtes de sécurité, CSP, `forward-headers-strategy`) : techniquement pertinent. Faisable sans infrastructure nouvelle. Pas de dépendance métier. À placer dans le lot de stabilisation (lot A).
- **T-L01b** (throttle partagé) : la séparation est justifiée, mais le besoin n'est pas démontré. `LoginThrottle` est en mémoire ; un throttle partagé n'a de sens que si l'application tourne sur plusieurs instances. Aucun déploiement multi-instance n'est documenté. **Proposition : reporter T-L01b** tant que la topologie de déploiement n'est pas décidée. Pas de Redis ni d'autre infrastructure sans besoin démontré.
- **Lien avec `forward-headers-strategy`** : la confiance accordée à `X-Forwarded-For` détermine l'adresse IP utilisée par le throttle. Sa configuration relève donc de T-L01a, pas de T-L01b.

Risques CSP pour Angular et intégrations externes :

- `frontend/src/index.html` (lignes 10 à 12) charge les polices depuis `fonts.googleapis.com` et `fonts.gstatic.com`. La CSP doit les autoriser (`style-src`, `font-src`), ou les polices doivent être servies localement.
- `frontend/src/app/features/home/home.component.ts` (ligne 9) charge une image depuis `images.unsplash.com`. Autoriser dans `img-src`, ou héberger l'image.
- Angular peut injecter des balises `<style>` à l'exécution. Selon la configuration, cela peut imposer `style-src 'unsafe-inline'` ou un nonce (`ngCspNonce`). **À vérifier par test de build et de navigateur**, non vérifiable ici.
- `frontend/src/app/app.html` est un fichier mort (`app.ts` utilise un template inline). Ne pas le prendre comme référence.
- `connect-src` doit autoriser l'origine de l'API en production.

Aucun `bypassSecurityTrust` ni `ngCspNonce` n'a été trouvé lors des lectures (à reconfirmer par recherche exhaustive avant implémentation).

## 6. T-L04 : routes placeholder

Douze routes utilisent le composant `page` (`frontend/src/app/app.routes.ts`, `const page` ligne 8) :

| Lignes | Routes |
|---|---|
| 42 à 47 | `/chauffeur`, `/airport-transfer`, `/services`, `/about`, `/faq`, `/contact` |
| 58 | `/auth/forgot-password` |
| 74 | `/customer/notifications` |
| 84 | `/admin` (vue d'ensemble) |
| 96 | `/admin/payments` |
| 98 | `/admin/partners` |
| 100 | `/admin/reports` |

Corrections documentaires à faire :

- La roadmap (T-L04, ligne 274) cite « lignes 42 et 98 » : incomplet. Les lignes 42 et 98 existent (`/chauffeur` et `/admin/partners`), mais la liste doit couvrir les 12 routes.
- AUD-18 de [project-completion-audit.md](./project-completion-audit.md) (ligne 65) cite `/partners` : la route réelle est `/admin/partners`.
- La route publique `/services` affiche « configurables depuis le back-office » alors que `/admin/services` (ligne 97) est livré.

## 7. Réconciliation des tests

Aucun test n'a été exécuté pendant cette mission. Le tableau ci-dessous identifie la source de chaque chiffre.

| Chiffres | Source | Étape ou périmètre | Classement |
|---|---|---|---|
| Surefire 88 ; Failsafe 3 ; Angular 33 ; E2E 18 | [audit/elite-tunisia-audit-final.md](../audit/elite-tunisia-audit-final.md) lignes 70–71, 93 | Étape 6A, Flyway V1 à V16 | PREVIOUSLY EXECUTED (déclaré, sans log joint) |
| PG Surefire 93 ; Failsafe 21 ; H2 93 (10 ignorés) ; Angular 33 ; E2E 18/18 | [postgres-stabilization-report.md](../postgres-stabilization-report.md) §9 | Flyway V1 à V18 | PREVIOUSLY EXECUTED (déclaré, sans log joint) |
| PG Surefire 118 ; Failsafe 23 ; H2 118 (10 ignorés) ; Angular 38 ; E2E 20/20 | [postgres-stabilization-report.md](../postgres-stabilization-report.md) §13 | Étape 6B, Flyway V1 à V20 | PREVIOUSLY EXECUTED (déclaré, sans log joint) |
| PG Surefire 118 ; Failsafe 23 (dont 20 `MigrationWithExistingDataIT`) ; H2 118 ; Angular 38 | [step-6b-security-and-modules.md](../step-6b-security-and-modules.md) lignes 117–119 | Étape 6B (même périmètre que §13) | PREVIOUSLY EXECUTED (cohérent avec §13) |
| PG Surefire 138 ; Failsafe 23 ; H2 138 ; Angular 38 ; E2E 21 (`e2e-postgres.ps1`) | [product-readiness-matrix.md](../product-readiness-matrix.md) lignes 81–83 | Date et étape absentes | UNKNOWN : incompatible avec 118 pour le même périmètre de l'étape 6B |
| `verify13` : Surefire 164 ; Failsafe 25 ; E2E 21 | [implementation-roadmap.md](./implementation-roadmap.md) §13 ; [project-completion-audit.md](./project-completion-audit.md) ligne 80 ; [international-benchmark-and-target-architecture.md](./international-benchmark-and-target-architecture.md) ligne 259 | Logs cités | UNKNOWN : logs absents du dépôt |
| `verify6` à `verify13` | Références dans plusieurs documents | Aucun log présent sur disque | UNKNOWN |
| 33 tests Angular | [start-local.md](../start-local.md) ligne 69 | Étape non précisée | UNKNOWN |
| 176 tests (1 ignoré, 0 échec) ; Failsafe 35 (0 échec) | `backend/target/surefire-reports` (44 fichiers XML) ; `backend/target/failsafe-reports` (7 fichiers XML) | Dernière écriture le 11/02/2026 ; périmètre et exécution inconnus | UNKNOWN : ne correspondent à aucun chiffre documenté ; possiblement cumulés sur plusieurs exécutions |
| Artefacts E2E | `frontend/test-results` | Présent, contenu non examiné | UNKNOWN |
| Étape 5 : 23/23 backend PostgreSQL (concurrence 2/2 incluse), 19/19 Vitest, 9/9 E2E | [testing.md](../testing.md) ligne 27 ; [step-5-report.md](../step-5-report.md) | Étape 5, PostgreSQL 18.3 temporaire, Flyway V1 à V4 (ligne 15) | PREVIOUSLY EXECUTED (déclaré, rapport cité non examiné ici) |
| Backend : 81 tests, 0 échec, 10 ignorés | [TEST_RUN_RESULT_BACKEND.md](../TEST_RUN_RESULT_BACKEND.md) ligne 4 | Exécution antérieure, date et périmètre non précisés ; même document signale des erreurs de compilation pré-existantes | UNKNOWN |
| Frontend unitaires et E2E « bloqués » | [SKIPPED_TESTS_REPORT.md](../SKIPPED_TESTS_REPORT.md) ; [DEVELOPMENT_STATUS.md](../DEVELOPMENT_STATUS.md) lignes 4–5 | Node 18.20.8 incompatible avec `engines` de `frontend/package.json` | BLOCKED (sur cette machine). Ces documents contredisent le fait que « Angular build succeeded earlier » sans précision |
| Tests PostgreSQL « skipped » sans base de test | [SKIPPED_TESTS_REPORT.md](../SKIPPED_TESTS_REPORT.md) ligne 8 | Absence de base PostgreSQL de test | BLOCKED (cause historique, pas un résultat) |
| E2E Playwright, PostgreSQL réel, toute la suite | — | Exécution interdite pendant cette mission | BLOCKED |

Conséquences :

- Aucun chiffre n'est retenu comme preuve de l'état actuel du code.
- Les chiffres 88, 93, 118 et 138 ne sont pas comparables entre eux sans connaître leur étape et leur périmètre.
- Les 176 tests présents dans `backend/target` ne doivent pas être cités comme résultat de la version actuelle, tant qu'une exécution datée ne les rattache pas au code de travail.

## 8. Preuves nécessaires avant de déclarer une tâche TESTED ou VALIDATED

- TESTED : un test exécuté sur le code actuel, avec son commande, son horodatage, son résultat et son log conservé.
- VALIDATED : TESTED, plus la vérification du critère d'acceptation de la roadmap §11, plus l'approbation du propriétaire quand la tâche dépend d'une décision métier.

Aucune tâche ne remplit ces conditions à ce jour.

## 9. Chemin critique vers une version techniquement exploitable

Ordre proposé, sous réserve des décisions du propriétaire :

1. **Lot 0, documentaire** : réconciliation des références (T-A05, D-02, routes T-L04, `verify`).
2. **Lot A, stabilisation** : T-A01 (base jetable, diagnostic), T-A02, T-A03 (exécution et décision sur le nombre d'itérations), T-A06, T-A07, T-A08, T-L01a. T-A04 reste bloquée par D5. T-A05 en correction minimale après confirmation P-02.
3. **Lot B, décisions** : T-B01 (D1, D2, D3, D5, D6, D10, D11, D12, D13).
4. **Lot C, partenaires et flotte** : T-C01 → T-C03, puis T-C02 et T-C04.
5. **Lot D, disponibilité** : T-D01 (et T-D02, T-D03 en parallèle).
6. **Lot E, réservation** : T-E01 → T-E02, T-E03.
7. **Lot F, paiement** : T-F01 (après T-A04 et D3/D5), puis T-F02 (après D11 et choix du prestataire).
8. **Lot G, finance** : T-G01 → T-G02 → T-G03 (après D1 et D2).
9. **Lot H, facturation** : T-H01 (après D6 et D12).

Chemin critique : T-B01 → T-C01 → T-C03 → T-E01 → T-F01 → T-G01 → T-G02 → T-G03. T-D01 s'insère avant T-E01 si la disponibilité par blocage est requise en pré-production.

Parallèle possible dès maintenant, sans décision métier : T-A01, T-A02, T-A03, T-A06, T-A07, T-A08, T-L01a, T-L02, T-L04 (après P-01).

## 10. Corrections documentaires recommandées (non appliquées ici)

| Document | Correction |
|---|---|
| implementation-roadmap.md, T-L04 (ligne 274) | Remplacer « lignes 42 et 98 » par la liste des 12 routes (§6). |
| implementation-roadmap.md, §12 | Corriger le chemin critique (§4.3) : ajouter T-G02, préciser T-D01. |
| implementation-roadmap.md, T-A02 | Ajouter la note « 20 itérations au lieu de 100 ». |
| implementation-roadmap.md, §13 et §11 bis | Remplacer « résultats lus dans des logs » par « logs absents du dépôt » tant qu'ils ne sont pas retrouvés. |
| project-completion-audit.md, AUD-18 (ligne 65) | Corriger `/partners` en `/admin/partners` et la liste des lignes. |
| business-decisions-pending.md | Voir [business-decision-analysis.md](./business-decision-analysis.md) §4 et les mises à jour P-01, P-02, D5, D10, D13. |

Ces corrections ne modifient aucun statut et aucune décision métier.

## 11. Limites

- Aucun test ni commande PostgreSQL n'a été lancé : les statuts « IMPLEMENTED » sont des constats de code, non des validations.
- Les modifications non committées peuvent évoluer : les constats portent sur l'état du dépôt à la date de la mission.
- Les logs `verify6` à `verify13` ne sont pas présents sur disque. Leurs résultats ne peuvent pas être confirmés.
- La version de PostgreSQL de production n'est pas documentée.
