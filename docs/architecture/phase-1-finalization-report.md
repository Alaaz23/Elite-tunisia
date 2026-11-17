# Phase 1 — Finalisation du Lot 1A (rapport)

Statut global : **NOT COMPLETED** pour le Lot 1A. Un incrément autorisé et non ambigu est terminé et validé (en-têtes de sécurité de l'API, partie de T-L01a). Les cinq autres tâches sont bloquées par des décisions métier, des autorisations ou un environnement indisponible. Aucun commit, push, déploiement, migration, connexion PostgreSQL ni modification de CI ou de configuration n'a été effectué.

## 1. État initial du dépôt

- `git status` : 9 fichiers suivis modifiés avant la phase (`BookingOperationService.java`, `BookingService.java`, `PaymentRepository.java`, `PaymentService.java`, `BackendApplicationTests.java`, `BookingOperationServiceTest.java`, `PaymentServiceIdempotenceCompatibleTest.java`, `docs/start-local.md`, `quote-detail.component.ts`).
- Fichier modifié par la phase : `SecurityConfig.java` (ajout Phase 1 uniquement).
- Fichiers non suivis préservés : `*PostgresIT.java`, `PaymentQuoteInvariantUnitTest.java`, `database/proposed/`, `docs/architecture/`, `docs/technical-audit.md`, `scripts/test-backend-postgres.ps1`, `SecurityHeadersTest.java` (créé en Phase 1).
- Aucun travail existant n'a été supprimé, réinitialisé ou écrasé.

## 2. Statut des tâches du Lot 1A

| Tâche | Statut | Justification |
|---|---|---|
| T-L01a (API) | PARTIALLY VALIDATED | En-têtes implémentés et validés par tests H2. Restent : CSP du SPA, `forward-headers-strategy`, validation navigateur. |
| T-A05 | BLOCKED | Dépend de P-02 (référence « D-02 » non résolue), de la règle de retard d'un `IN_PROGRESS` dépassé et du sens de `RESERVED`. Aucun code ajouté. |
| T-A06 | BLOCKED | Job E2E en CI : modification de `.github/workflows/ci.yml` non autorisée (matrice #16). Node local 18.20.8 incompatible avec `engines` (`^22.22.3 \|\| ^24.15.0 \|\| >=26`). Proposition documentée, non appliquée. |
| T-A07 | BLOCKED | `QuoteService.get` écrit l'expiration via `expireIfDue`. Passer en lecture seule changerait le comportement (matrice #22, non autorisé). Décision requise sur le statut affiché d'un devis expiré. |
| T-A08 | BLOCKED | Pagination de `InvoiceService.adminList` : changement de contrat API (le frontend attend un tableau). Taille maximale et tri stable à décider ; frontend non validable localement. |
| T-L02 | BLOCKED | Hikari, health et actuator : modification de configuration non autorisée (matrice #17). Restauration : PostgreSQL isolé et autorisation distincte requis. |

## 3. Travaux réalisés

- `SecurityConfig.java` : bloc `.headers(...)` ajouté après `.cors(cors -> { })` :
  - `Content-Security-Policy-Report-Only: default-src 'none'; frame-ancestors 'none'; base-uri 'none'` (non bloquant) ;
  - `Referrer-Policy: no-referrer` ;
  - `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()`.
  - Diff vérifié : 6 ajouts, 0 suppression (`git --numstat`). CORS, CSRF, autorisations et politique de session sont inchangés.
- `SecurityHeadersTest.java` (nouveau) : 2 tests MockMvc (`GET /api/health` → 200 avec en-têtes ; `GET /api/admin/security-headers-probe` → 401 avec en-têtes).
- Vérification d'absence de doublon : aucun `addHeader` d'en-tête de sécurité dans `backend/src/main` (seul `AuthController` ajoute `Set-Cookie`).

## 4. Tests exécutés et résultats

Environnement : JDK 21 (`jdk-21.0.2.13-hotspot`) passé via `JAVA_HOME` dans la commande, profil H2 par défaut, `-DskipITs`. Aucune connexion PostgreSQL.

| Test | Commande | Résultat |
|---|---|---|
| SecurityHeadersTest (état final) | `.\mvnw.cmd -B -ntp -DskipITs -Dtest=SecurityHeadersTest test` | 2 OK, 0 échec |
| Suite H2 complète (état final) | `.\mvnw.cmd -B -ntp -DskipITs test` (log : `%TEMP%\phase1-final-h2.log`) | 172 exécutés, 0 échec, 0 erreur, 10 ignorés, BUILD SUCCESS |
| Sensibilité 1 : `Referrer-Policy` `NO_REFERRER` → `SAME_ORIGIN` | idem, sur une copie mutée | 2 échecs (`expected:<no-referrer> but was:<same-origin>`) : détecté |
| Sensibilité 2 : suppression de `.reportOnly()` | idem | 2 échecs (`Content-Security-Policy-Report-Only` attendu, valeur `null`) : détecté |

Les 10 tests ignorés sont les tests d'intégration PostgreSQL (non exécutés dans ce mode).

Gestion du fichier pendant les mutations : la sauvegarde `SecurityConfig.java` a été vérifiée par SHA256 avant mutation (`E992492DD8B3ED81EA5305B01A358485005C1185B0A15349701BA4FA4D75DDA3`). Après la mutation 2, une erreur d'édition a été corrigée et le fichier a été contrôlé : même empreinte que la sauvegarde. La sauvegarde temporaire a ensuite été supprimée.

## 5. Tests non exécutés et raisons

- Tests unitaires et build Angular (`frontend`) : non exécutés. Node 18.20.8 est seul disponible ; le projet exige Node 22.22.3+ ou 24.15.0+. Aucune installation sans autorisation.
- E2E Playwright : non exécutés (même raison ; de plus `playwright.config.ts` n'a pas de `webServer`).
- Tests PostgreSQL (`*PostgresIT`, `PaymentPostgresIntegrationTests`, `scripts/test-backend-postgres.ps1`) : non exécutés, interdits pour cette phase.
- Validation CSP dans un navigateur : non effectuée.
- Résultats historiques `verify6` à `verify13` : non reproduits et non cités comme preuves.

## 6. Décisions métier et points ouverts

- P-02 / D-02 : options a/b/c à trancher par le propriétaire du produit (T-A05).
- Règle de retard d'une réservation `IN_PROGRESS` dépassée, et sens de `RESERVED` (T-A05).
- Statut affiché d'un devis expiré entre deux passages du worker (60 s) (T-A07).
- Taille maximale et tri stable de la liste admin des factures (T-A08).
- Topologie du proxy en production, pour `forward-headers-strategy` (T-L01a).
- Mode de service du SPA et domaines externes (Google Fonts) à autoriser dans la CSP (T-L01a).

## 7. Autorisations restantes

- Matrice #16 : modification de `.github/workflows/ci.yml` (T-A06).
- Matrice #17 : modification de `application*.yml` (forward-headers, Hikari, actuator) (T-L02, T-L01a).
- Matrice #22 : changement de comportement de l'expiration des devis (T-A07).
- Restauration de base : PostgreSQL isolé et autorisation distincte (T-L02).
- Installation d'un Node 22 ou 24 pour le frontend et les E2E.
- Passage de la CSP en mode bloquant : étape séparée, après validation navigateur et mise en place d'un point de rapport.

## 8. Risques

- Confirmé : la CSP est en `Report-Only` sans `report-uri`. Elle ne bloque rien et ne produit aucun signal observable.
- Confirmé : les en-têtes sont posés par la chaîne Spring Security. Ils ne couvrent pas les pages HTML servies hors de l'API.
- Risque : le CSP de l'application frontend n'est pas validé ; Google Fonts (`index.html`) doit être autorisé avant tout passage en mode bloquant.
- Risque : `forward-headers-strategy` non configuré ; la confiance accordée aux en-têtes proxy dépend de la topologie de production (inconnue).
- Limite : la suite H2 ne valide pas le comportement PostgreSQL (10 tests ignorés).
- Limite : la suite H2 affiche un avertissement Mockito (self-attach) ; il n'affecte pas les résultats.

## 9. Tâches reportées

- Passage de la CSP en mode bloquant.
- Validation CSP du SPA dans un navigateur.
- Configuration de `forward-headers-strategy`.
- T-A05, T-A06, T-A07, T-A08, T-L02 (voir section 2).
- Mise à jour de `first-implementation-batch.md` et `implementation-roadmap.md` pour refléter le statut PARTIALLY VALIDATED de T-L01a (non modifié dans cette phase).

## 10. Commandes de validation

```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
.\mvnw.cmd -B -ntp -DskipITs -Dtest=SecurityHeadersTest test
.\mvnw.cmd -B -ntp -DskipITs test
```

Les commandes frontend (`npm ci`, `npm test`, `npm run build`, `npx playwright test`) nécessitent Node 22 ou 24 et ne sont pas exécutées.

## 11. Prochaine étape recommandée

1. Le propriétaire du produit tranche P-02 / D-02, la règle de retard et le statut d'un devis expiré. Cela débloque T-A05 et T-A07.
2. Autoriser explicitement la modification de CI (#16) et de configuration (#17) si T-A06 et la configuration Hikari/health doivent être traitées.
3. Installer un Node compatible (22 ou 24) pour valider Angular et E2E, après autorisation.
4. Valider la CSP dans un navigateur, puis décider de son passage en mode bloquant.
