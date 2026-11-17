# Elite Tunisia — Plan maître de finalisation

Statut : proposition à valider. Aucun lot d'implémentation n'est lancé tant que ce plan n'est pas validé explicitement.

Documents liés :
- [project-completion-audit.md](./project-completion-audit.md) : audit de Phase 0, constats AUD-01 à AUD-26.
- [implementation-roadmap.md](./implementation-roadmap.md) : backlog §11, statuts réels en §11 bis.
- [v22-validation-report.md](./v22-validation-report.md) et [v22-decision-register.md](./v22-decision-register.md) : traitement de V22, hors périmètre de ce plan sauf mention.
- [business-decisions-pending.md](./business-decisions-pending.md) : registre centralisé des décisions métier en attente (Phase 0.1, complété en Phase 0.2).
- [business-decision-analysis.md](./business-decision-analysis.md) : analyse détaillée des décisions, options, conséquences et décisions liées (Phase 0.2).
- [technical-work-reconciliation.md](./technical-work-reconciliation.md) : réconciliation des 33 tâches et des résultats de tests historiques (Phase 0.2).
- [implementation-authorization-matrix.md](./implementation-authorization-matrix.md) : opérations soumises à autorisation explicite (Phase 0.1).

Légende de preuve : `[CONFIRMÉ]` observé dans le code ou les fichiers ; `[CONSTAT]` résultat d'une lecture ; `[HYPOTHÈSE]` non vérifié ; `[À VÉRIFIER]` vérification possible en Phase 2 ; `[DÉCISION À VALIDER]` choix métier.

---

## 1. Résumé exécutif

Elite Tunisia dispose d'une base technique réelle : Spring Boot 4.1.1, Java 21, Angular, PostgreSQL et Flyway 12.4.0, avec un moteur de réservation, des devis, une gestion des transferts, une authentification JWT avec refresh, et des tests unitaires et d'intégration PostgreSQL. `[CONSTAT]`

Le produit n'est pas prêt pour la préproduction. Trois constats bloquants ressortent de l'audit :

1. **Pas d'isolation entre partenaires** (AUD-01) : le modèle ne contient pas de `partner_id` opérationnel, et aucun endpoint `/api/partner/**` n'existe. `[CONFIRMÉ]`
2. **Pas de garantie en base d'un seul paiement CAPTURED par devis** (AUD-02) : la contrainte est prévue dans V21, non appliquée. `[CONFIRMÉ]`
3. **Pas de flux de remboursement ni de lien paiement–réservation** (AUD-03) : les colonnes `refunded_*` existent sans logique. `[CONFIRMÉ]`

Il existe aussi un défaut fonctionnel de disponibilité (NEW-01, AUD-05) : `BookingService.rentable` exige `VehicleStatus.AVAILABLE`, ce qui peut bloquer une réservation future pendant une location en cours. `[CONFIRMÉ]`

La reproductibilité des tests PostgreSQL n'est pas établie : le script `scripts/test-backend-postgres.ps1` est destructif, non suivi, et `verify12` a échoué sans cause confirmée. Les rapports de tests affichés dans la roadmap ne sont pas reproduits dans cette phase. `[CONFIRMÉ]` / `[À VÉRIFIER]`

Le travail local est important et non committé : 9 fichiers suivis modifiés, 4 fichiers de test non suivis, des propositions V21/V22 et des documents non suivis. Il doit être sécurisé avant toute autre opération. `[CONFIRMÉ]`

**Verdict initial : READY WITH BLOCKERS** (voir §11). Le plan peut démarrer par le lot 0 et les vérifications, mais le produit reste non prêt pour la préproduction.

---

## 2. État réel

### 2.1 Fonctionnalités observées

| Domaine | État | Référence |
|---|---|---|
| Authentification JWT, refresh, CSRF cookie | Implémentée, testée en intégration | roadmap §3–§4 `[CONSTAT]` |
| Catalogue véhicules, statuts | Implémenté ; `rentable` restreint à `AVAILABLE` | `BookingService.java` 193–199 |
| Réservation avec EXCLUDE gist | Présent en base | `V4__booking_engine.sql` ligne 25 |
| Confirmation admin | Présente | `AdminBookingController.java` ligne 23 |
| Devis avec expiration | Présent ; `get` en écriture | `QuoteService.java` 114–120 |
| Paiements (V6) | Table et idempotence simple ; pas de lien réservation | `V6__payments.sql` lignes 20–21 |
| Factures (V7) | Table présente ; chemin d'appel non confirmé | `V7__invoices.sql` ligne 44 ; `InvoiceService.java` ligne 86 |
| Transferts | Chevauchement chauffeur contrôlé dans le code, pas en base | `TransferService.java` 116, 121–122 |
| Notifications | Interface seulement | `NotificationService.java` |
| Partenaires, flotte partenaire | Absents | AUD-01 |
| Commission, grand livre, reversements | Absents | AUD-04 |
| Routes `/chauffeur` et `/admin/partners` (et 10 autres routes `page`) | Placeholders | `app.routes.ts` lignes 42 à 47, 58, 74, 84, 96, 98, 100 |

### 2.2 Statut des 33 tâches

Le détail par tâche (dépendances, preuves) est en [roadmap §11 bis](./implementation-roadmap.md). Synthèse :

| Statut | Nombre | Tâches |
|---|---|---|
| NOT STARTED | 17 | T-A05, T-A08, T-C01–T-C04, T-D01–T-D03, T-E02, T-G02, T-G03, T-I02, T-J01, T-K01, T-L01, T-L04 |
| IN PROGRESS | 7 | T-A01, T-A04, T-A06, T-A07, T-E01, T-I01, T-L02 |
| BLOCKED | 6 | T-B01, T-E03, T-F01, T-F02, T-G01, T-H01 |
| IMPLEMENTED | 2 | T-A02, T-A03 |
| DEFERRED | 1 | T-L03 |
| TESTED | 0 | — |
| VALIDATED | 0 | — |

Aucune tâche ne passe en TESTED ou VALIDATED sans exécution réelle et conservée. Les statuts IMPLEMENTED reposent sur du code non committé.

---

## 3. Écarts fonctionnels et techniques

### 3.1 Écarts fonctionnels (par rapport au backlog §11)

- Partenaires : aucun modèle opérationnel, aucun espace partenaire, aucune isolation (T-C01 à T-C04).
- Disponibilité : pas de table de blocages (maintenance, indisponibilités) (T-D01).
- Tarification : pas de règles de prix (saison, durée, zone) (T-D02).
- Réservation : validation admin présente, mais motifs de refus et expiration des PENDING absents (T-E01, T-E02).
- Paiement : pas de lien réservation, pas de remboursement, pas de fournisseur réel (T-F01, T-F02).
- Finance : pas de commission, pas de grand livre, pas de reversements (T-G01 à T-G03).
- Facturation : code facture non branché (T-H01, AUD-07 à re-vérifier).
- Notifications : interface seulement (T-J01).
- Reporting : absent (T-K01).

### 3.2 Écarts techniques

- Stabilisation des tests PostgreSQL (T-A01) : script destructif, cause de `verify12` inconnue.
- Index sur `payments.quote_id` absent (hors V21, sujet de performance, voir [v22-decision-register.md](./v22-decision-register.md)).
- Contrainte V19 `NOT VALID` (AUD-16).
- Throttle de connexion en mémoire, non partagé entre instances (AUD-11) ; en-têtes proxy non configurés (AUD-10).
- Absence de CSP (AUD-12).
- Job E2E absent de la CI ; deux specs Playwright hors `testDir` (AUD-13).
- Node : `engines` accepte 22 et 24 ; CI épingle 24.15.0 ; environnement local en 18.20.8 (AUD-14).
- Pagination absente sur `adminList` des factures (AUD-15).

---

## 4. Risques critiques

| ID | Niveau | Risque concret | Référence |
|---|---|---|---|
| AUD-01 | CRITICAL | Un partenaire pourrait accéder aux données d'un autre partenaire dès que le modèle existera, faute d'isolation prévue. | T-C01, T-C02 |
| AUD-02 | CRITICAL | Deux paiements CAPTURED pour un même devis si l'application contourne son contrôle applicatif. | V21, T-A04 |
| AUD-03 | CRITICAL | Pas de remboursement : un litige financier ne peut pas être traité dans l'outil. | T-F01 |
| AUD-05 | HIGH | Réservation future refusée pendant une location en cours (NEW-01). | T-A05 |
| AUD-08 | HIGH | Résultats PostgreSQL non reproductibles ; risque de déclarer un succès non prouvé. | T-A01 |
| AUD-09 | HIGH | Travail non committé ; perte possible en cas de réinitialisation de l'environnement. | Git |

Les autres constats (HIGH, MEDIUM, LOW) sont dans l'audit, §4.

---

## 5. Dépendances

Chemin critique (identique à roadmap §12) :

T-B01 → T-C01 → T-C03 → T-D01 → T-E01 → T-F01 → T-G01 → T-G03

Lectures :
- Le lot de stabilisation (A) est indépendant des décisions, sauf T-A05 (référence D-02 à clarifier, AUD-22).
- T-A04 dépend de la validation de V21 par une autorisation distincte.
- T-F02 dépend d'un prestataire validé, pas seulement de code.
- T-L01 et T-L02 sont indépendants et peuvent progresser en parallèle.
- T-J01 dépend de T-E01.
- T-L03 (V11) reste hors chemin critique et exige une autorisation distincte.

---

## 6. Priorités

| Niveau | Signification | Tâches |
|---|---|---|
| P0 | Bloque la préproduction ou la sûreté des données | T-A01, T-A02, T-A03, T-A04, T-A05, T-B01, T-C01, T-C02, T-C03, T-D01, T-E01 |
| P1 | Cœur marketplace et exploitation | T-A06, T-C04, T-D02, T-D03, T-E02, T-E03, T-F01, T-F02, T-G01, T-G02, T-G03, T-H01, T-I01, T-J01, T-L01, T-L02 |
| P2 | Expérience et exploitation | T-A07, T-A08, T-I02, T-K01, T-L04 |
| P3 | Évolutions et nettoyage | T-L03 |

Les priorités suivent le risque et les dépendances, pas la facilité.

---

## 7. Lots d'exécution

Chaque lot ne démarre qu'après validation de ce plan et, pour les lots touchant la base ou Git, d'une autorisation explicite.

| Lot | Contenu | Pré-requis | Sortie |
|---|---|---|---|
| 0 — Sécurisation | Inventaire et mise en sécurité du travail local (sans commit) ; tâches sans décision : T-A06, T-A07, T-A08, T-L01, T-L04 | Validation du plan | Liste des fichiers protégés ; corrections du lot 0 faites sans commit |
| 1 — Stabilisation PostgreSQL | T-A01 (base jetable par exécution, diagnostic `verify12`), vérification de T-A02 à T-A04 | Autorisation Phase 2 isolée | 3 exécutions `verify` consécutives sans échec, rapports conservés |
| 2 — Décisions | T-B01 : trancher D1, D3, D5, D6, D10, D11, D12 (bloquantes) ; D2 avant l'étape D (T-G03) ; D7, D8 avant production | Décisions utilisateur | Registre des décisions validé |
| 3 — Partenaires et flotte | T-C01 à T-C04, T-D01 à T-D03 ; T-A05 après clarification de D-02 | Lot 2 partiel (D1, D10) | Matrice d'isolation A→B à 100 % refusée |
| 4 — Réservation et paiement | T-E01, T-E02, T-E03, T-F01, T-F02 | Lot 3, V21 appliquée (autorisation distincte) | Remboursements partiel et total testés |
| 5 — Finance et facturation | T-G01 à T-G03, T-H01, T-I01 | Lot 4 ; D1, D6, D12 | Grand livre ajout seul ; une facture par réservation |
| 6 — Production | T-I02, T-J01, T-K01, T-L02 (sauvegarde/restauration), T-L03 sur autorisation | Lots précédents | Restauration réussie sur base isolée ; rapport conservé |

### 7.1 Analyse des lots 0 et 1 (Phase 0.1, documentaire)

Typologie : **Doc** = documentaire ; **App** = code applicatif ou configuration ; **BD** = base de données ou PostgreSQL.

**Constat transverse** : la CI (`.github/workflows/ci.yml`) n'exécute que ce qui est commité et poussé. Les fichiers non suivis (`BookingLockOrderPostgresIT.java`, `BookingAuditAtomicityPostgresIT.java`, `PaymentQuoteCapturePostgresIT.java`, `PaymentQuoteInvariantUnitTest.java`) et les modifications locales (`BookingService`, `BookingOperationService`, `PaymentService`, etc.) ne sont couverts par aucun job tant qu'ils ne sont pas committés. Le commit et le push sont donc des autorisations distinctes, à demander avant toute validation CI des lots 0 et 1.

**Lot 0 — Sécurisation**

| Tâche | Typologie | Fichiers | Dépendances | Critères d'entrée | Critères de sortie | Tests | Risques | Autorisation |
|---|---|---|---|---|---|---|---|---|
| T-A06 | App + CI | `.github/workflows/ci.yml`, `frontend/package.json` (`engines`), `frontend/playwright.config.ts`, `frontend/e2e/` | Aucune | Plan validé | Job E2E en CI avec rapport conservé ; `engines` resserré (Node 24.15.0 épinglé) | Exécution CI réelle (après push) | `engines` resserré peut casser un poste de développement ; les deux specs hors `e2e/` (AUD-13) restent hors périmètre tant qu'elles ne sont pas déplacées | Commit et push pour validation CI |
| T-A07 | App | `QuoteService.java` (`get` en écriture) | Aucune | Plan validé | `get` en `readOnly` ; expiration déplacée vers un worker sans régression | Tests unitaires et MockMvc ; test que l'expiration reste effective | Changement de comportement sur l'expiration | Modification de code (phase d'implémentation) |
| T-A08 | App | `InvoiceService.java` (`adminList`), contrôleur admin, écran admin factures | Aucune | Plan validé | Réponse paginée, taille maximale bornée | MockMvc (page, taille max) ; test frontend | Changement de contrat d'API consommé par le frontend | Modification de code |
| T-L01 | App + config | `SecurityConfig.java`, configuration `server.forward-headers-strategy` (absente : seul un commentaire de `LoginThrottle.java` la mentionne), `LoginThrottle.java` | Aucune pour les en-têtes ; **throttle partagé : infrastructure à justifier** | Plan validé | En-têtes et CSP testés ; throttle partagé validé sur deux instances | Tests d'en-têtes ; test de throttle sur deux instances (non réalisable en l'état) | Mélange de deux sujets : en-têtes (faisable) et throttle partagé (nouvelle infrastructure) | Modification de code ; tout service externe (Redis, table partagée) exige une justification |
| T-L04 | App (frontend) | `frontend/src/app/app.routes.ts` (routes rendues par `PagePlaceholderComponent`) | Aucune technique | Plan validé | Aucune route publique sans contenu réel | Tests de routage | **Décision produit** : livrer ou masquer ; l'option « masquer » est un choix produit, non technique | Décision produit avant implémentation |

Points à traiter avant d'entrer dans le lot 0 :
- Scinder T-L01 en deux sous-tâches : **T-L01a** (en-têtes, CSP, forward headers) dans le lot 0, et **T-L01b** (throttle partagé) reportée jusqu'à la décision d'infrastructure. Proposition, non appliquée.
- T-L04 n'est pas « sans décision » : voir [business-decisions-pending.md](./business-decisions-pending.md) (point P-01).

**Lot 1 — Stabilisation PostgreSQL**

| Tâche | Typologie | Fichiers | Dépendances | Critères d'entrée | Critères de sortie | Tests | Risques | Autorisation |
|---|---|---|---|---|---|---|---|---|
| T-A01 | BD + App (test) | `scripts/test-backend-postgres.ps1` (non suivi, destructif : `DROP DATABASE ... WITH (FORCE)`), profil `postgres-test` de `backend/pom.xml`, job `backend-postgres` | Autorisation Phase 2 sur instance isolée 127.0.0.1:15432 ; décision de remplacer le script (§10.2) | Autorisation explicite | 3 exécutions `verify` consécutives sans échec, base jetable, rapports conservés ; cause de `verify12` identifiée | `mvnw -Ppostgres-test verify` ×3 | Script destructif ; cause de `verify12` non prouvée ; **le script ne doit pas être lancé en l'état** | Autorisation Phase 2 + modification ou remplacement du script |
| T-A02 | BD + App | `BookingService.java`, `BookingOperationService.java` (modifiés, non commités), `BookingLockOrderPostgresIT.java` (non suivi) | T-A01 | Autorisation Phase 2 ; fichiers préservés | 0 deadlock sur 100 itérations (concurrence retrait ∥ transition) | Test PostgreSQL concurrent | Résultat `verify13` non reproduit | Autorisation Phase 2 |
| T-A03 | BD + App | `BookingOperationService.java` (modifié), `BookingAuditAtomicityPostgresIT.java` (non suivi) | T-A01 | Idem | Échec d'audit → opération annulée et erreur explicite | Test PostgreSQL d'atomicité | Dépend de la gestion transactionnelle réelle | Autorisation Phase 2 |
| T-A04 | BD + App | `PaymentService.java` (modifié), `PaymentRepository.java`, `PaymentQuoteCapturePostgresIT.java` (non suivi), `PaymentQuoteInvariantUnitTest.java` (unit, non suivi), `database/proposed/V21__payments_one_captured_per_quote.sql` (non appliquée) | T-A01 ; **V21 : application séparée** | Idem ; précontrôle des doublons documenté | Deux clés distinctes sur un devis → un seul `CAPTURED` ; test de concurrence vert | Test unitaire (invariant) + test PostgreSQL de concurrence | Données historiques en doublon ; échec de migration si doublons présents | Autorisation Phase 2 pour les tests ; **autorisation distincte pour appliquer V21** |

**Incohérence relevée pour le lot 1** : T-A05 (P0, épic A) n'apparaît dans aucun lot 0 ou 1. Le plan le place dans le lot 3, après clarification de « D-02 ». La référence D-02 est orpheline (voir [business-decisions-pending.md](./business-decisions-pending.md), point P-02). NEW-01 est un défaut P0 confirmé (`BookingService` exige `VehicleStatus.AVAILABLE`). Décision à prendre : le lot de T-A05 (lot 1 ou lot 3). Non tranchée ici.

**Critères d'entrée et de sortie des lots**
- Lot 0 entrée : plan validé ; liste des fichiers protégés (ce document, §1 de la Phase 0.1). Sortie : en-têtes et routes traités ; CI E2E réellement exécutée (après autorisation de push) ; aucun commit non autorisé.
- Lot 1 entrée : autorisation Phase 2 écrite ; base isolée prouvée ; script destructif non lancé. Sortie : trois `verify` consécutifs verts, rapports conservés, cause de `verify12` documentée.

---

## 8. Critères de validation

Une tâche n'est déclarée TESTED que si la commande a été exécutée et que son rapport est conservé. Une tâche n'est VALIDATED que si le critère d'acceptation de la roadmap est démontré.

| Domaine | Preuve exigée |
|---|---|
| Backend unitaire | `./mvnw -DskipITs test` (JDK 21) : code de sortie 0, rapport Surefire conservé |
| PostgreSQL | `./mvnw -Ppostgres-test verify` sur 127.0.0.1:15432 uniquement, 3 exécutions consécutives sans échec |
| Frontend | `npm test`, `npm run build`, `npm audit` : code de sortie et rapports |
| E2E | Playwright sur `./e2e` (configuration actuelle) avec rapport conservé |
| Isolation partenaire | Matrice A→B : 100 % des endpoints refusés, témoin positif accepté |
| Sauvegarde | Restauration sur base isolée, comparaison de comptages et de contraintes |

---

## 9. Complexité relative

Estimation relative, sans calendrier : S (quelques heures), M (une à deux journées), L (plusieurs journées), XL (à découper).

| Lot | Complexité | Justification |
|---|---|---|
| 0 | S | Corrections localisées, pas de décision |
| 1 | M | Diagnostic de `verify12` et isolation de la base de test |
| 2 | M | Dépend des réponses métier, non de l'effort technique |
| 3 | XL | Nouveau modèle partenaire, isolation transverse, migrations |
| 4 | L | Flux de paiement et remboursement, intégration fournisseur |
| 5 | XL | Grand livre, commission, facturation légale |
| 6 | L | Portail chauffeur, notifications, reporting |

---

## 10. Décisions et autorisations nécessaires

### 10.1 Décisions métier (roadmap §15)

| Décision | Bloque | Priorité |
|---|---|---|
| D1 — Commission | T-G01, T-B01 | Bloquante |
| D2 — Fréquence des reversements | T-G03 | Bloquante (étape D, avant T-G03) |
| D3 — Annulation, remboursement, no-show | T-E03, T-F01 | Bloquante |
| D4 — Cautions (roadmap §15) | Aucun ID de tâche (modules C, F) | Reportable |
| D5 — Moment de l'acompte | T-F01 | Bloquante |
| D6 — TVA et facturation | T-H01 | Bloquante |
| D7 — Contrats partenaires | Production | Avant production |
| D8 — Assurance | Production | Avant production |
| D9 — Statut chauffeur | T-I02 (T-I01 si la règle de chevauchement dépend du statut) | Reportable |
| D10 — Motifs de refus | T-E01 | Bloquante |
| D11 — Fournisseur de paiement | T-F02 | Bloquante |
| D12 — Facturation depuis le devis | T-H01 | Bloquante |
| D13 — Exclusion chauffeur sur transferts | T-I01 | Reportable (à confirmer : voir business-decisions-pending) |
| D14 — Canaux de notification | T-J01 | Reportable |
| D15 — Migrations inertes | T-L03 | Reportable |
| « D-02 » (cité par T-A05) | T-A05 | Non classée : référence orpheline, à clarifier |

Classifications alignées sur la roadmap §15, qui fait référence. Ce tableau ne choisit aucune option. Les options et questions ouvertes sont dans [business-decisions-pending.md](./business-decisions-pending.md).

Référence D-02 (T-A05) : la roadmap cite « D-02 » sans correspondance exacte avec le registre §15 (AUD-22). Clarification demandée.

### 10.2 Autorisations techniques distinctes

- Exécuter des tests PostgreSQL sur l'instance isolée 127.0.0.1:15432 (Phase 2).
- Modifier `scripts/test-backend-postgres.ps1` ou le remplacer par une base jetable par exécution (le script actuel ne doit pas être lancé).
- Committer les tests, propositions et documents non suivis.
- Appliquer V21 ou V22 (autorisations séparées).
- Supprimer ou déplacer le doublon V11 (T-L03).

---

## 11. Verdict initial

**READY WITH BLOCKERS.**

Justification :
- Le plan est complet : les 33 tâches ont un statut réel, les risques sont classés et les dépendances sont explicites.
- Les blocages sont réels et identifiés : décisions métier, travail non committé, reproductibilité des tests PostgreSQL, absence d'isolation partenaire.
- Le produit n'est pas prêt pour la préproduction : ce verdict ne vaut que pour l'exécution du plan.

Conditions pour passer à READY TO EXECUTE :
1. Validation de ce plan.
2. Sécurisation du travail local (lot 0), sans perte ni commit non autorisé.
3. Réponse aux décisions bloquantes du lot 2 au moment où elles sont requises.

---

## 12. Commandes de validation futures

Aucune de ces commandes n'a été exécutée dans cette phase. Elles seront lancées uniquement dans les lots autorisés.

```powershell
# Backend unitaire (JDK 21 requis, le JDK par défaut est 8)
cd backend
./mvnw -DskipITs test

# Backend PostgreSQL, sur instance isolée uniquement (127.0.0.1:15432)
./mvnw -Ppostgres-test verify

# Frontend
cd ..\frontend
npm test
npm run build
npm audit

# E2E (configuration actuelle : testDir ./e2e)
npx playwright test
```

Interdit : `scripts/test-backend-postgres.ps1` (suppression de base via `DROP DATABASE ... WITH (FORCE)`).

---

## 13. Limites de la phase

- Le brief original de la mission V22 n'a pas été retrouvé ; certaines formulations ne peuvent pas être confirmées.
- Le comportement réel de PostgreSQL, les durées de verrous, la version exacte du serveur et la configuration Flyway par défaut restent `[À VÉRIFIER]`.
- Les résultats `verify6` à `verify13` cités dans la roadmap sont des traces de journaux, non reproduites ici.
- Le nombre de jobs CI est de quatre (`backend-h2`, `backend-postgres`, `frontend`, `dependency-review`) ; la mission en mentionnait trois.
- Aucune analyse ASVS approfondie n'a été faite pour V5, V8 et V9.
- Le flux d'upload de documents et la sauvegarde réelle ne sont pas vérifiés.
- L'audit n'est pas une certification.

---

## 14. Prochaine étape

Valider ou modifier :
1. Les priorités et lots de la section 7.
2. Les décisions bloquantes à obtenir (section 10.1).
3. L'autorisation de Phase 2 PostgreSQL isolée (aucune connexion avant accord explicite).

Aucun lot d'implémentation, aucune migration, aucun commit et aucune connexion PostgreSQL ne sont engagés avant cette validation.
