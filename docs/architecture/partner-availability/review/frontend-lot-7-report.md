# Lot 7 — Frontend Angular : partenaires, garanties, espace partenaire

Statut : **LOT 7 PARTIELLEMENT VALIDÉ**.

- Validé par exécution réelle : tests unitaires Vitest (109/109, pool `threads`, Node 22.22.3 portable), build de production (exit 0), et **22 scénarios navigateur Playwright (22/22)** couvrant les 14 parcours demandés, avec des réponses API simulées (mocks).
- Non validé : **aucun backend réel n'a été démarré** et aucun test HTTP contre un backend réel n'a été exécuté. L'accès à PostgreSQL isolé (`127.0.0.1:15432`, rôle `et_test`) a été refusé par authentification (« password authentication failed »), donc aucune base temporaire n'a été créée et aucune migration Flyway n'a été jouée.
- Conséquence : les parcours sont vérifiés côté interface contre des réponses simulées conformes aux contrats du code backend. Les règles métier réelles (hold, concurrence, idempotence, PDF généré) ne sont pas prouvées de bout en bout dans cette passe.

Le lot 7 ne peut pas être déclaré VALIDÉ tant que la vérification contre un backend réellement démarré n'a pas été faite (voir section 8).

## 1. Environnement

- Node : **22.22.3 portable**, utilisé uniquement par préfixe de PATH de session (`C:\Users\aaziza\Downloads\node-v22.22.3-win-x64\node-v22.22.3-win-x64`). Aucune installation, PATH persistant et Node global non modifiés. Node global 18.20.8 incompatible avec Angular 22.
- Angular 22.2.x, Vitest 5.0.3 (`@angular/build:unit-test`), jsdom.
- Runner de tests : pool **`threads`** via `frontend/vitest.config.mjs` (référencé par `runnerConfig` dans `frontend/angular.json`). Le pool `forks` expire après 60 s sur cette machine (« Failed to start forks worker »). Choix confirmé par le lead technique ; stabilité vérifiée sur cinq runs (section 8).
- Backend : non modifié dans ce lot. Contrats relus dans `PartnerDtos.java`, `AdminPartnerController`, `AdminGuaranteeController`, `VehiclePartnerAssignmentController`, `PartnerPortalController`.
- Navigateur : Playwright 1.63.0 avec `frontend/playwright.lot7.config.ts`, baseURL `http://127.0.0.1:4300` (serveur `ng serve` lancé par cette passe, Node 22 de session). Le navigateur `chromium_headless_shell-1243` attendu par Playwright n'est pas présent ; la version 1248 déjà en cache est utilisée via `PLAYWRIGHT_EXECUTABLE_PATH`. Aucune installation.
- Backend réel : non démarré. Le port 8080 est occupé par un processus Java externe (PID 38128), non arrêté, non utilisé. Le port 4200 est occupé par un processus Node externe (PID 37336), non arrêté, non utilisé. Le port 5432 est interdit.

## 2. Fichiers

Relevé le 78da39b sur `main` (HEAD confirmé dans `.git/refs/heads/main` et par `git log`). `git status --short` : **168 entrées** après nettoyage final (dont 51 `M` et 117 `??`), contre 133 puis 166 dans les passes précédentes ; le nombre varie avec les logs `lot7-*` et les dossiers de sortie de test. `docs/architecture/` est entièrement non suivi (`??`), donc `review/` aussi : aucun fichier de ce dossier n'est suivi par Git.

Ajouts de la passe finale (non suivis) :
- `frontend/playwright.lot7.config.ts` et `frontend/tests/browser-lot7/lot7-journeys.e2e.ts` (22 scénarios navigateur) ;
- `frontend/test-results-lot7/` (sorties Playwright, dont captures des échecs de la première passe) ;
- `frontend/src/app/features/home/home.component.ts` (réécrit) et `home.component.spec.ts` (7 tests) ;
- `frontend/src/app/features/customer/customer-invoice-detail.component.ts` et `.spec.ts` ;
- `frontend/src/app/core/interceptors/error.interceptor.ts` (modifié) : session expirée, refresh unique, redirection avec `returnUrl`.

Modifiés (statut `M`) :
- `frontend/angular.json` : `runnerConfig` pour le test.
- `frontend/src/app/app.routes.ts` : routes partenaires et garanties (`/admin/partners`, `/admin/guarantees`, roleGuard SUPER_ADMIN, MANAGER, AGENT) et route `/partner` (authGuard + roleGuard PARTNER, layout dédié). Les routes publiques, réservation, devis et espace client sont aussi déclarées dans ce fichier (voir section 4.1).
- `frontend/src/app/core/interceptors/error.interceptor.ts` : propagation du `code` backend (cette passe).
- `frontend/src/app/core/models/api-http-error.ts` : champ `code?` (cette passe).
- `frontend/src/app/core/services/booking-api.service.ts` : `EXPIRED`, `holdExpiresAt`, `holdExpired()` (cette passe).
- `frontend/src/app/core/services/catalogue-api.service.ts` : modifié dans l'arbre de travail ; l'origine exacte de cette modification n'a pas été isolée dans cette passe.
- `frontend/src/app/features/admin/admin-vehicles.component.ts` : statut `ARCHIVED`.
- `frontend/src/app/features/bookings/booking-detail.component.ts` et `booking.spec.ts` : affichage du hold (cette passe).
- `frontend/src/app/features/customer/README.md` : réécrit (cette passe).
- `frontend/src/app/features/quotes/quote-detail.component.ts` : paiement, idempotence, correction de `err.error.code` (cette passe).
- `frontend/src/app/features/quotes/quotes.spec.ts` : intercepteur enregistré et 5 tests de paiement (cette passe).
- `frontend/src/app/features/vehicles/vehicle-detail.component.ts` : libellé du statut (cette passe).
- `frontend/src/app/layout/admin-layout/admin-layout.component.ts` : liens « Partenaires » et « Garanties ».

Créés (non suivis, statut `??`) :
- Cette passe : `frontend/src/app/core/interceptors/error.interceptor.spec.ts` (3 tests), `frontend/src/app/features/vehicles/vehicle-detail.component.spec.ts` (2 tests).
- Lots précédents : `frontend/vitest.config.mjs`, `frontend/src/app/core/services/partner-api.service.ts` et `.spec.ts`, `frontend/src/app/features/partners/` (validation et écrans), `frontend/src/app/features/admin/admin-partners.component.ts`, `frontend/src/app/features/admin/admin-guarantees.component.ts`, `frontend/src/app/layout/partner-layout/`, `frontend/src/app/features/partner/`.

Le dossier `docs/architecture/partner-availability/review/` est entièrement non suivi (`??`) : ce rapport et `18-mvp-tracking.md` n'apparaissent dans aucun commit.

## 3. Migration

Aucune. Backend et migrations inchangés (V23 à V26 déjà présentes).

## 4. Fonctionnalités réellement implémentées

- Gestion admin des partenaires : liste paginée, création, modification, activation/désactivation, lien facultatif vers un compte PARTNER (UUID). Validation alignée sur les règles backend (matricule fiscal obligatoire pour COMPANY et interdit pour INDIVIDUAL, nom ≤ 160 caractères, e-mail).
- Affectation de véhicule : historique consultable par SUPER_ADMIN, MANAGER et AGENT (lecture seule, aligné sur `partners:read`) ; formulaire d'affectation et de transfert réservé à SUPER_ADMIN et MANAGER (`partners:manage`). Aucune affectation automatique.
- Gestion admin des garanties : liste paginée, filtre par véhicule, création (début futur, fin après début, ≤ 90 jours, motif obligatoire), retrait des garanties ACTIVE. Écriture réservée à SUPER_ADMIN et MANAGER.
- Espace partenaire `/partner` : profil, véhicules confiés, garanties. **Lecture seule** : aucun bouton, formulaire, champ ou select (vérifié par test du DOM).
- Rôle PARTNER : bloqué des routes admin et des routes non partenaires par `roleGuard` (vérifié par test). La sécurité réelle reste côté backend (`/api/partner/**` exige `ROLE_PARTNER`).

Les écrans admin restent protégés côté UI seulement par `hasAnyRole`. Le backend fait foi.

### 4.1 Parcours ajoutés ou corrigés dans la passe de finalisation

Chaque point ci-dessous est couvert par des tests Angular exécutés (voir section 5.2). « Couvert par test unitaire » ne signifie pas « vérifié dans un navigateur ».

- **Catalogue public et filtres** : passage sur `GET /api/vehicles/available` quand les deux dates sont renseignées ; sans date, `GET /api/vehicles`. Tri (`newest`, `price_asc`, `price_desc`, `year_desc`, `name_asc`) et pagination alignés sur le backend. Spec existant `vehicle-catalogue.component.spec.ts`.
- **Fiche véhicule** (`vehicle-detail.component.ts`) : galerie, tarifs (jour, semaine, avec chauffeur), caractéristiques. Correction : le libellé `AVAILABLE` affichait « Disponible » sans vérification de date ; il affiche désormais « Ouvert à la réservation ». La disponibilité réelle ne vient que de `GET /api/vehicles/{id}/availability` ou de `/available`. Spec `vehicle-detail.component.spec.ts` (2 tests).
- **Disponibilité** (`booking-form.component.ts`) : « Disponible pour cette période » n'est affiché qu'après réponse `available: true` du backend.
- **Propagation du code d'erreur** : `ApiHttpError` porte `code` ; `error.interceptor.ts` le transmet depuis `error.error.code` quand c'est une chaîne. Le statut HTTP et les messages français sont inchangés. Spec `error.interceptor.spec.ts` (8 tests, dont 3 pour la propagation du code).
- **Hold de 30 minutes** (`booking-detail.component.ts`) : affichage de « Dates bloquées jusqu'au … » pour un hold PENDING actif, et « Cette demande a expiré… » quand `holdExpiresAt` est dépassé. Statut `EXPIRED` ajouté au modèle. 2 specs dans `booking.spec.ts`.
- **Paiement simulé** (`quote-detail.component.ts`, `pay()`) :
  - montant = total du devis, méthode choisie (CARD, BANK_TRANSFER, CASH, OTHER) ;
  - une clé d'idempotence par devis (`paymentKey`), réinitialisée après FAILED ou erreur non réessayable, conservée pour `PAYMENT_IN_PROGRESS` et statut 0 ;
  - message de succès « Paiement enregistré … en attente de validation par l'agence » : le paiement CAPTURED ne confirme pas la réservation, seule la validation admin le fait ;
  - correction d'un bogue : la branche `PAYMENT_IN_PROGRESS` lisait `err.error.code` alors que l'intercepteur remplace l'erreur par `ApiHttpError` ; elle ne s'exécutait donc jamais.
  - 5 specs dans `quotes.spec.ts`.
- **Espace client** : `frontend/src/app/features/customer/README.md` réécrit avec l'état réel de chaque route, composant et endpoint (l'ancien README indiquait « NON IMPLÉMENTÉ », devenu faux).
- **Détail de facture et PDF** (`customer-invoice-detail.component.ts`, route `/customer/invoices/:id`) : détail via `GET /api/invoices/{id}`, téléchargement via `GET /api/invoices/{id}/pdf` (blob, nom de fichier issu de `Content-Disposition`). Messages dédiés pour 404 (facture introuvable, sans détail), 403 (accès refusé), 401 et erreur serveur. Liste : lien vers le détail et bouton de téléchargement. 7 specs unitaires, plus 3 scénarios navigateur.
- **Hold expiré sur le devis** (`quote-detail.component.ts`) : sur `BOOKING_HOLD_EXPIRED`, message explicite, aucun succès affiché, bouton « Vérifier la disponibilité » puis « Reprendre la réservation » si le véhicule est encore disponible. Le DTO de devis n'est pas modifié : le backend reste l'autorité sur l'expiration.
- **Session expirée** (`error.interceptor.ts`, `auth.service.ts`) : sur 401 avec un jeton, un seul renouvellement partagé entre les requêtes concurrentes (`refreshSession` en vol), reprise de la requête une fois ; si le refresh échoue, session nettoyée et redirection vers `/auth/login` avec `returnUrl` (destination demandée). Un 401 sur la requête rejouée nettoie aussi la session. Specs : 4 dans `auth.service.spec.ts`, 8 dans `error.interceptor.spec.ts`.
- **Page d'accueil** (`home.component.ts`) : présentation de l'agence, recherche (redirige vers `/vehicles` avec les paramètres), catégories chargées depuis l'API avec états chargement, erreur et vide, services (chauffeur et transfert présentés comme services à confirmer, sans statistique ni témoignage inventés), footer et navigation. 7 specs unitaires.
- **Parcours navigateur** : 22 scénarios Playwright (voir section 5.3), API simulée par interception des appels `/api`.

### 4.2 Endpoints réellement utilisés par le frontend (contrats relus dans le code backend)

| Parcours | Méthode et URL | Contrat vérifié |
|---|---|---|
| Catalogue | `GET /api/vehicles` | `PublicVehicleController` |
| Catalogue à dates | `GET /api/vehicles/available` | dates obligatoires, futures, fin après début, rejet `INVALID_DATE_RANGE` (400) |
| Fiche véhicule | `GET /api/vehicles/{id}` | `VehicleSummary` (champs utilisés tous présents) |
| Disponibilité | `GET /api/vehicles/{id}/availability` | `{available, reason}` |
| Aperçu tarifaire | `POST /api/bookings/quote-preview` | `BookingPublicController` |
| Politique | `GET /api/bookings/policy` | `BookingPublicController` |
| Réservation client | `POST /api/bookings`, `GET /api/bookings`, `GET /api/bookings/{id}`, `POST /api/bookings/{id}/cancel` | `CustomerBookingController` (annulation : PENDING et CONFIRMED) |
| Devis client | `GET /api/customer/quotes` (liste paginée), `GET /api/customer/quotes/{id}`, `POST …/{id}/accept`, `POST …/{id}/reject`, `POST …/{id}/request-change` | `CustomerQuoteController` (rôle CUSTOMER et `quotes:read`/`quotes:accept`/`quotes:reject`) |
| Paiement simulé | `POST /api/customer/quotes/{quoteId}/payments` | `CustomerPaymentController` ; corps `{amount, method, idempotencyKey}` ; réponse `{id, status, transactionRef, amount}` ; codes `PAYMENT_AMOUNT_MISMATCH`, `IDEMPOTENCY_CONFLICT`, `BOOKING_HOLD_EXPIRED`, `QUOTE_ALREADY_PAID`, `PAYMENT_IN_PROGRESS` |
| Profil client | `GET` et `PUT /api/customer/profile` | `CustomerProfileController` |
| Factures | `GET /api/invoices` (liste, UI client) ; `GET /api/invoices/{id}` et `GET /api/invoices/{id}/pdf` (détail et PDF, UI client de cette passe, via `InvoiceApiService`) | `CustomerInvoiceController` |

Non exposé par le backend, donc non implémenté côté frontend : liste des paiements d'un client (l'historique des paiements reste visible par devis dans le détail de devis). Hors périmètre MVP, rien n'a été inventé : remboursements, pénalités et acomptes.

## 5. Résultats réels

### 5.1 Historique du lot (passes précédentes)

| Contrôle | Commande | Résultat | Preuve |
|---|---|---|---|
| Tests unitaires Angular (partenaires) | `npx ng test --watch=false` (Node 22) | 12 fichiers, 74 tests passés, exit 0 | `lot7-ng-test-lot7c.log` |
| Tests de base (avant lot) | idem | 9 fichiers, 38 passés | `lot7-ng-test-full.log` |
| Audit prod / complet | `npm audit --omit=dev` / `npm audit` | 0 vulnérabilité (exit 0) | `lot7-npm-audit-prod.log`, `lot7-npm-audit-full.log` |

Échecs intermédiaires corrigés pendant la passe précédente : build de référence cassé par `ARCHIVED` ; spec de composant qui ne compilait pas (fixture `PartnerSummary` sans `deactivatedAt`).

### 5.2 Passe de finalisation (parcours public, réservation, paiement, espace client)

| Contrôle | Commande (Node 22 par préfixe de session) | Résultat réel | Preuve |
|---|---|---|---|
| Tests Angular, paiement et hold | `npx ng test --watch=false` | 84/84 passés, exit 0 | `lot7-ng-test-payment-hold2.log` |
| Stabilité `threads` (run 1) | idem | 84/84, 43,76 s, exit 0 | `lot7-ng-test-stability-run1.log` |
| Stabilité `threads` (run 2) | idem | 84/84, 5,87 s, exit 0 | `lot7-ng-test-stability-run2.log` |
| Stabilité `threads` (run 3) | idem | 84/84, 6,48 s, exit 0 | `lot7-ng-test-stability-run3.log` |
| Suite complète après spec détail véhicule | idem | **86/86 passés, 14 fichiers, exit 0**, 7,10 s | `lot7-ng-test-vehicle-detail3.log` |
| Build production | `npx ng build` (configuration production) | **exit 0**, « Application bundle generation complete », 310,29 kB initial (85,41 kB transfert) | `lot7-build-final.log` |

Tentatives échouées conservées comme preuves, non supprimées : `lot7-ng-test-payment-hold.log` (2 échecs, spec sans intercepteur d'erreur, corrigé), `lot7-ng-test-vehicle-detail.log` et `lot7-ng-test-vehicle-detail2.log` (échecs du spec détail véhicule, corrigés en retirant les `verify()` et les flush des requêtes annexes du formulaire de réservation embarqué).

Le build de production affiche **deux avertissements**, présents avant cette passe et non traités (le résumé précédent disait à tort « sans avertissement ») :
- budget de style dépassé pour `home.component` (5,03 kB pour 4 kB à la passe précédente ; **6,36 kB pour 4 kB** après la réécriture de la page d'accueil, voir 5.3) ;
- module `jsbi` (dépendance de `@js-temporal/polyfill`) en CommonJS.

### 5.3 Passe finale (parcours navigateur, factures, hold, session, accueil)

| Contrôle | Commande (Node 22 de session) | Résultat réel | Preuve |
|---|---|---|---|
| Vitest, pool `threads` | `npx ng test --watch=false` | **16 fichiers, 109/109 tests passés, exit 0**, 27,58 s | `%TEMP%\lot7e-test2.log` |
| Build production | `npx ng build --configuration production` | **exit 0**, « Application bundle generation complete » (35 s), 2 avertissements (section 6) | `%TEMP%\lot7e-build.log` |
| Navigateur (22 scénarios) | `npx playwright test -c playwright.lot7.config.ts` depuis `frontend/`, base `127.0.0.1:4300`, API simulée | **22 passés, 0 échec, exit 0**, 26,0 s | `%TEMP%\lot7-pw-run3.log` |
| Backend réel / HTTP | non exécuté | bloqué (voir 5.4) | — |

Note : une première exécution Vitest (`%TEMP%\lot7e-test1.log`) a échoué à la compilation sur deux erreurs TS2345 (`unknown` passé à un paramètre typé) ; elles ont été corrigées avant la passe finale, dont le résultat est celui du tableau ci-dessus. Le log est conservé.

Les 22 scénarios navigateur couvrent : accueil et navigation ; catalogue, filtre et pagination ; détail véhicule ; disponibilité (visiteur disponible et indisponible) ; création de demande par un client ; acceptation d'un devis et paiement (CAPTURED, FAILED, PENDING, hold expiré avec et sans véhicule disponible) ; tableau de bord, devis, réservations et profil ; liste, détail et PDF de facture ; facture inexistante et facture d'un autre utilisateur ; access token expiré avec renouvellement puis paiement réussi ; refresh token invalide avec nettoyage de session et retour après connexion ; visiteur sur route protégée ; client sur route d'administration.

Corrections des scénarios pendant la passe (défauts de test, pas de défauts applicatifs) : le formulaire de disponibilité n'avait pas les deux lieux ; le lien de la barre latérale client s'appelle « Réservations » (et non « Mes réservations »). Les deux ont été corrigés dans le test, pas dans l'application.

Limites des scénarios navigateur : API simulée, donc pas de preuve que le backend renvoie ces réponses ; pas de test de déconnexion ; pas de contrôle du nombre exact de renouvellements au-delà du test unitaire.

### 5.4 Backend réel et PostgreSQL isolé : bloqué

- Tentatives : rôle `et_test` sur `127.0.0.1:15432`, avec le mot de passe fourni par le lead technique (deux fois) : « password authentication failed for user "et_test" ». Aucun autre mot de passe n'a été essayé, `pgpass.conf` n'a pas été lu.
- Conséquences : aucune base temporaire `et_lot7_browser_*` créée, aucune migration V1 à V26 jouée, aucun backend démarré, aucun appel HTTP réel, aucune vérification des règles de hold, de concurrence ou de génération de PDF sur un backend réel.
- `et_test_db`, `et_dev_db` et le port 5432 n'ont pas été touchés. `scripts/test-backend-postgres.ps1` n'a pas été exécuté.

Déblocage nécessaire : un mot de passe valide pour `et_test` (ou un rôle dédié de test), puis la procédure de la section 8, point 1.

## 6. Avertissements de build (antérieurs, non traités)

- Budget de style dépassé sur `home.component` : **6,36 kB** pour un budget de **4 kB** (dépassement de 2,36 kB, relevé dans `%TEMP%\lot7e-build.log`). Ce composant a été réécrit par la passe finale : ce dépassement vient de cette réécriture, pas d'une dette antérieure. Le budget n'a pas été relevé dans `angular.json`. À réduire en déplaçant une partie des styles vers des classes partagées ou en réduisant la feuille de style de la page.
- Module `jsbi` (dépendance de `@js-temporal/polyfill`) en CommonJS. Non bloquant, déjà présent avant le lot 7 ; à traiter par la mise à jour ou le remplacement de cette dépendance.

## 7. Non exécuté / non couvert

- Aucun backend réel démarré, donc aucun test HTTP d'intégration (section 5.4). Les contrats sont vérifiés par lecture du code et par `HttpTestingController` ; les scénarios navigateur utilisent des réponses simulées.
- Règles métier réelles non prouvées de bout en bout dans cette passe : expiration réelle du hold, concurrence de réservation, idempotence du paiement en base, génération réelle du PDF de facture.
- Déconnexion non couverte par les scénarios navigateur (un test unitaire existe dans `auth.service.spec.ts`).
- Pas de test de flakiness du pool `threads` au-delà des runs de la passe précédente (section 8).
- Écrans site public, catalogue, réservation, devis, paiement et espace client : couverts par tests unitaires Angular uniquement (voir section 4.1). Aucun rendu réel dans un navigateur.
- Page d'accueil et navigation publique : couvertes par 7 tests unitaires (`home.component.spec.ts`) et par le scénario navigateur « accueil et navigation ». Le dépassement de budget de style reste signalé (section 6).
- Pas de test HTTP contre un backend démarré. Aucun backend n'a été lancé pendant cette passe, donc aucune preuve d'intégration réelle n'est déclarée.
- Expiration de session en cours de navigation : couverte par les tests unitaires de l'intercepteur et par les scénarios navigateur « access token expiré » et « refresh token invalide » (réponses simulées). Pas de preuve sur un backend réel de la révocation côté serveur.
- Sections 15 et 16 du brief Lot 7 (critères d'acceptation et format du rapport) : le fichier `Pasted text #1.txt` n'a pas été retrouvé dans la session ni dans le dépôt. Ces critères n'ont donc pas pu être relus.

## 8. Points ouverts et décisions

1. **Code d'erreur backend : résolu.** `error.interceptor.ts` transmet désormais le champ `code` dans `ApiHttpError`, sans perdre le statut HTTP ni les messages français (spec `error.interceptor.spec.ts`, 8 tests). Le composant de paiement exploite `PAYMENT_IN_PROGRESS`, `BOOKING_HOLD_EXPIRED` et les autres codes via `code`.
2. **Historique d'affectation visible par AGENT : corrigé** (voir passe précédente, test `partner-screens.spec.ts`).
3. **Runner Vitest `threads` : décision confirmée et stable.** Run final vert : 109/109 sur 16 fichiers, code de sortie 0 (`%TEMP%\lot7e-test2.log`), après une tentative précédente en échec de compilation (deux erreurs TS2345, corrigées). Runs antérieurs verts : 84/84 et 86/86 (`payment-hold2`, `vehicle-detail3`). Durées : 5,4 à 7,1 s, sauf un run à 43,8 s (`lot7-ng-test-stability-run1.log`). Aucun échec intermittent observé. Le pool `forks` expire toujours sur cette machine (voir section 1) : le choix de `threads` reste motivé, mais ces runs ne prouvent pas l'absence de flakiness à long terme.
4. **Node 22 portable : non installé proprement.** Le binaire est dans `Downloads` et n'est utilisé que par préfixe de PATH de session. Pour la CI, un Node 22 ou 24 compatible reste à installer par l'équipe ; rien n'a été installé ici, Node 18.20.8 global est inchangé.
5. **Avertissements de build** : budget de style de `home.component` et module `jsbi` (section 6). À traiter avant la finalisation de la page d'accueil.
6. **Facturation : détail et PDF côté client implémentés** (`/api/invoices/{id}` et `/pdf`, voir 4.1). Décision métier encore ouverte côté backend : `InvoiceService.createFromQuote` n'a pas d'appelant, donc aucune facture n'est générée automatiquement. Le déclencheur (paiement CAPTURED ou confirmation) reste à trancher ; ce n'est pas une décision frontend.
7. **Pay button et expiration du hold.** Le DTO de devis ne porte pas `holdExpiresAt`. Le bouton de paiement ne peut donc pas vérifier l'expiration côté client ; le backend refuse avec `BOOKING_HOLD_EXPIRED` et l'UI l'affiche.
8. **Erreur de statut 0 pendant un paiement** : le bouton reste verrouillé jusqu'au rechargement de la page, pour éviter un double paiement ; après rechargement une nouvelle clé est générée et la règle serveur « un seul paiement CAPTURED par devis » protège contre le doublon. Limite à documenter.
9. **Pas de liste de paiements côté backend** : l'historique des paiements client n'est pas affichable tel quel.
10. **Sections 15 et 16 du brief Lot 7** : introuvables dans la session et le dépôt. Si le brief les contient, les critères d'acceptation doivent être relus avant clôture.


## 9. État Git

- Branche `main`, HEAD `78da39b` (confirmé par `lot7-git-branch-check.txt`), **aucun commit, push ni déploiement**.
- Aucun fichier supprimé. Aucun `git reset`, `git clean` ni checkout destructif. Aucun log supprimé. `database/proposed/` non touché.
- Nombre d'entrées `git status --short` relevé après nettoyage final : **168** (51 modifiées `M`, 117 non suivies `??`), contre 133 au début de la mission précédente et 166 en cours de passe. L'écart avec 169 relevé plus tôt tient au dossier `frontend/test-results-lot7/`, supprimé en fin de passe. Le dossier `docs/architecture/` est entièrement non suivi (`?? docs/architecture/`), donc `review/` aussi : ces fichiers sont conservés, non supprimés.
- Fichiers ajoutés en passe finale : `frontend/playwright.lot7.config.ts`, `frontend/tests/browser-lot7/lot7-journeys.e2e.ts` (22 scénarios). Le dossier de sortie Playwright `frontend/test-results-lot7/` a été supprimé après les runs (sortie générée, non versionnée).
- Fichiers modifiés cette passe (frontend) : `core/models/api-http-error.ts`, `core/interceptors/error.interceptor.ts`, `core/services/booking-api.service.ts`, `features/bookings/booking-detail.component.ts`, `features/bookings/booking.spec.ts`, `features/quotes/quote-detail.component.ts`, `features/quotes/quotes.spec.ts`, `features/vehicles/vehicle-detail.component.ts`, `features/customer/README.md`, et ce rapport.
- Fichiers créés cette passe : `core/interceptors/error.interceptor.spec.ts`, `features/vehicles/vehicle-detail.component.spec.ts`, et les logs racine `lot7-ng-test-*.log`, `lot7-build-final.log`, `lot7-build-payment-hold.log`, `lot7-git-status-check.txt`, `lot7-git-branch-check.txt`.
- Logs conservés, y compris les échecs : `lot7-ng-test-payment-hold.log`, `lot7-ng-test-vehicle-detail.log`, `lot7-ng-test-vehicle-detail2.log`.
- Fichiers de la passe précédente (toujours présents) : `frontend/vitest.config.mjs`, `frontend/src/app/core/services/partner-api.service*.ts`, `frontend/src/app/features/admin/admin-guarantees.component.ts`, `frontend/src/app/features/admin/admin-partners.component.ts`, `frontend/src/app/features/partner/`, `frontend/src/app/features/partners/`, `frontend/src/app/layout/partner-layout/`.
- Logs racine de la passe précédente conservés : `lot7-baseline-build.log`, `lot7-build-after-archived.log`, `lot7-baseline-tests.log`, `lot7-ng-test-full.log`, `lot7-ng-test-lot7c.log`, `lot7-build-lot7c.log`, `lot7-npm-audit-prod.log`, `lot7-npm-audit-full.log`.

## 10. Avancement du MVP

- Backend lots 1 à 6 : terminés et validés (voir `18-mvp-tracking.md`). Aucun backend modifié dans cette passe.
- Frontend, lot 7 :
  - **Réalisé et couvert par tests unitaires** : administration des partenaires et garanties ; espace partenaire en lecture seule ; catalogue public avec filtres et pagination ; fiche véhicule ; contrôle de disponibilité ; réservation et hold de 30 minutes (affichage et expiration) ; devis (liste, détail, accepter, refuser, demande de modification) ; paiement simulé avec idempotence et gestion des erreurs métier ; profil client ; liste des réservations et des factures.
  - **Non vérifié ou non réalisé** : tests HTTP contre un backend démarré et parcours sur backend réel (bloqués, section 5.4) ; expiration réelle du hold côté serveur ; historique des paiements (pas d'endpoint). Les parcours navigateur ont été exécutés avec API simulée (22/22), pas contre le backend.
- **Verdict : LOT 7 PARTIELLEMENT VALIDÉ.** Validés : build de production (code 0, 2 avertissements non bloquants), Vitest `threads` 109/109, parcours navigateur 22/22 sur API simulée. Non validé : tout test contre un backend réellement démarré, faute de mot de passe valide pour le rôle de test sur `127.0.0.1:15432`. Le critère « parcours réellement fonctionnels contre le backend » n'est donc pas rempli.
- Ce verdict n'est pas VALIDÉ : les scénarios navigateur reposent sur des réponses simulées et ne prouvent pas l'intégration avec les règles métier réelles (hold, paiement, facture).
- Lot 8 : non commencé, conformément à la consigne.

Ce rapport ne garantit pas l'absence de bugs.

