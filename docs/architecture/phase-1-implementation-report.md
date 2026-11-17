# Elite Tunisia — Rapport de la Phase 1 (implémentation contrôlée)

## 1. État initial du dépôt

- `git status` avant intervention : 9 fichiers suivis modifiés (`BookingOperationService.java`, `BookingService.java`, `PaymentRepository.java`, `PaymentService.java`, `BackendApplicationTests.java`, `BookingOperationServiceTest.java`, `PaymentServiceIdempotenceCompatibleTest.java`, `docs/start-local.md`, `quote-detail.component.ts`) et de nombreux fichiers non suivis (tests PostgreSQL `*PostgresIT.java`, `PaymentQuoteInvariantUnitTest.java`, `database/proposed/`, `docs/architecture/`, `docs/technical-audit.md`, `scripts/test-backend-postgres.ps1`).
- Aucun de ces travaux n'a été modifié, supprimé, réinitialisé ou commité pendant cette phase.

## 2. Analyse des six tâches du Lot 1A

| Tâche | Classe | Décision |
|---|---|---|
| T-A05 (réservation NEW-01 / retard) | C | Non implémentée : la règle de retard n'est pas validée et la référence « D-02 » (P-02) est absente du registre. |
| T-L01a (en-têtes, CSP, forward-headers) | A pour la partie API | **Retenue** : partie API uniquement. Forward-headers (D) et CSP frontend (B/D) exclus. |
| T-A06 (job E2E en CI) | D | Non implémentée : modifier la CI exige une autorisation ; l'environnement Node 18 ne respecte pas `engines`. |
| T-A07 (lecture de devis) | B | Non implémentée : passer `QuoteService.get` en `readOnly` ferait perdre l'écriture d'expiration ; le statut affiché est une décision à valider. |
| T-A08 (factures admin) | B/D | Non implémentée : la pagination change le contrat API (liste brute consommée par le frontend). |
| T-L02 (Hikari, health, restauration) | D | Non implémentée : exige une base PostgreSQL isolée et une autorisation séparée. |

## 3. Tâche retenue et justification

**T-L01a, partie API uniquement.** Elle est classée A : aucune décision métier, aucune dépendance, aucune base de données, changement local à `SecurityConfig`, testable en H2.

## 4. Modifications réelles

- `backend/src/main/java/tn/elitetunisia/common/config/SecurityConfig.java` :
  - ajout d'un bloc `.headers(...)` : CSP en `Report-Only` (`default-src 'none'; frame-ancestors 'none'; base-uri 'none'`), `Referrer-Policy: no-referrer`, `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()` ;
  - import `ReferrerPolicyHeaderWriter` ;
  - CORS, CSRF, authentification, autorisations et stratégie stateless inchangés.

## 5. Fichiers modifiés ou créés

- Modifié : `backend/src/main/java/tn/elitetunisia/common/config/SecurityConfig.java`
- Créé : `backend/src/test/java/tn/elitetunisia/common/config/SecurityHeadersTest.java`
- Mis à jour (statut) : `docs/architecture/first-implementation-batch.md` (§3.2), `docs/architecture/implementation-roadmap.md` (ligne T-L01 du §11 bis)
- Créé : ce rapport.

## 6. Tests réellement exécutés

Environnement : JDK 21.0.2 (Temurin) via `JAVA_HOME` positionné dans la commande, profil H2 par défaut, `-DskipITs`.

| Commande | Résultat |
|---|---|
| `backend\mvnw.cmd -B -ntp -DskipITs -Dtest=SecurityHeadersTest test` | 2 tests, 0 échec, BUILD SUCCESS |
| `backend\mvnw.cmd -B -ntp -DskipITs test` | 172 tests, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS |

Précisions :
- Les 10 tests ignorés sont les tests `*PostgresIntegrationTests` et `PaymentPostgresIntegrationTests`, qui exigent PostgreSQL. **Ils n'ont pas été exécutés.**
- `ProductionProfileHardeningTest` et `EndpointAuthorizationSweepTest` (sécurité existante) passent avec la modification.
- Angular (`ng test`, `ng build`) et Playwright (E2E) **non exécutés** : Node v18.20.8 ne respecte pas `engines` (`^22.22.3 || ^24.15.0 || >=26`). Ces validations restent à faire.

## 7. Preuves de sensibilité non obtenues

- Le test `SecurityHeadersTest` n'a pas été vérifié en échec sans la modification de `SecurityConfig`. Le test est donc présent mais sa capacité à détecter une régression n'est pas démontrée.
- Aucune vérification par un agent navigateur ou un rapport de violation CSP n'a été faite.

## 8. Problèmes rencontrés

- `JAVA_HOME` global pointe vers JDK 8 ; Maven doit être lancé avec `JAVA_HOME` JDK 21 dans le même appel PowerShell.
- Un appel `git diff --stat` a bloqué le shell PowerShell (interrompu proprement, aucune modification).
- Warning Mockito (self-attach) dans les logs, sans effet sur les résultats.

## 9. Risques restants

- **CSP** : `Report-Only` sans `report-uri` ne produit aucun signal observable ; son bénéfice réel est faible tant qu'un point de collecte n'existe pas. La CSP réelle du SPA doit être posée là où le frontend est servi, hors dépôt et non identifié.
- **Google Fonts** (`frontend/src/index.html`) : à autoriser si la CSP passe en bloquant.
- **Forward headers** : sans `server.forward-headers-strategy`, Spring ignore les `X-Forwarded-*`. Un réglage erroné en production fausserait l'IP du throttle (`LoginThrottle.java`).
- **Throttle en mémoire** : non partagé entre instances.
- **Tests PostgreSQL** : non exécutés ; la compatibilité réelle avec PostgreSQL reste non prouvée par cette phase.

## 10. Décisions encore nécessaires

- Topologie de proxy de production (pour `forward-headers-strategy`).
- Service du SPA en production (pour la CSP frontend).
- Passage de la CSP en mode bloquant et point de collecte des rapports.
- Règle de retard (T-A05) et référence « D-02 ».
- Statut affiché d'un devis expiré entre deux exécutions du worker (T-A07).
- Pagination ou non de la liste admin des factures (T-A08).

## 11. Prochaine tâche recommandée

1. Vérifier la sensibilité de `SecurityHeadersTest` (échec attendu sans la modification), en travaillant sur une copie ou après autorisation explicite.
2. Mettre en place l'environnement Node 22/24 conforme pour valider Angular et E2E (prérequis de T-A06).
3. Décider de la topologie de proxy pour finaliser `forward-headers-strategy`.

## 12. Autorisations nécessaires pour la suite

- Modifier la CI (T-A06) : autorisation explicite.
- Modifier la configuration (forward-headers, Hikari, health) : autorisation explicite.
- Tests PostgreSQL et restauration (T-L02) : autorisation distincte, sur base isolée uniquement.
- Modifier le contrat API des factures (T-A08) : décision et coordination frontend.
- Aucune migration, aucune connexion au port 5432, aucun commit, aucun push, aucun déploiement sans autorisation explicite.

## Verdict de la Phase 1

- Incrément livré : partie API de T-L01a, implémentée et testée sur H2 (2 tests ciblés, suite H2 complète 172 OK).
- Non démontré : sensibilité du test, validation Angular, validation E2E, validation PostgreSQL.
- T-L01 reste **partiellement terminée**.
