# Lot 8 — Rapport de validation : intégration globale

## Statut final

**PARTIELLEMENT VALIDÉ — validation PostgreSQL réelle et E2E sur backend réel en attente.**

Le Lot 7 conserve son statut `PARTIELLEMENT VALIDÉ — DÉVELOPPEMENT TERMINÉ, VALIDATION POSTGRESQL EN ATTENTE`. Le Lot 9 n'a pas été commencé.

## 1. Périmètre officiel du Lot 8

Source : `18-mvp-tracking.md`, section 6bis. Lot 8 = administration, portail partenaire en lecture seule, espace client, frontend, E2E.

## 2. État initial

- Lot 7 : développement terminé, PostgreSQL réel non validé (identifiants `et_lot7_tester` indisponibles).
- Aucune connexion PostgreSQL n'a été tentée pendant ce lot. Aucune base protégée n'a été touchée.

## 3. Analyse de l'architecture

- Backend : Spring Boot 4 / Java 21, monolithe modulaire, Flyway, JWT, `@PreAuthorize` pour le RBAC.
- Frontend : Angular, Vitest, services `*-api.service.ts`, intercepteur d'erreurs (refresh 401, exclusion des URLs `/api/auth/`).
- Séparation des API : `/api/admin/...`, `/api/customer/...`, `/api/bookings/...` (public), `/api/auth/...`.

## 4. Fonctionnalités vérifiées

Vérification statique (correspondance entre appels Angular et mappings Spring) :

| Appel frontend | Contrôleur backend | Résultat |
|---|---|---|
| `/api/bookings/policy`, `/api/bookings/quote-preview` | `BookingPublicController` | Présent |
| `/api/admin/bookings/{id}/operations`, `/return` | `AdminBookingOperationsController` | Présent |
| PATCH booking `confirm/reject/cancel/start/complete` | `AdminBookingController` | Présent |
| Quote `send`, `cancel`, `versions`, `options`, `booking` | `AdminQuoteController` | Présent |
| Quote client `reject` | `CustomerQuoteController` | Présent |
| Service `activate/deactivate`, PUT | `AdminServiceCatalogController` | Présent |
| Véhicule PUT/DELETE, marque et catégorie PUT/DELETE, image DELETE | `AdminVehicleController`, `ImageUploadController` | Présent |
| `/api/auth/refresh`, `/me`, `/csrf` | `AuthController` | Présent |
| `/available` | `PublicVehicleController` | Présent |
| Paiement `POST .../payments` (`amount`, `method`, `idempotencyKey`) | `CustomerPaymentController` | Présent, contrat cohérent |

Contrat de paiement : `PaymentMethod` = `CARD, BANK_TRANSFER, CASH, OTHER`. Le frontend envoie toujours `CARD` et une clé d'idempotence.

## 5. Anomalies détectées

| # | Anomalie | Sévérité | Statut |
|---|---|---|---|
| A1 | Le type TypeScript `idempotencyKey` est optionnel dans `quote-api.service.ts`, alors que le backend l'exige (`@NotBlank`). Pas de bug à l'exécution, car le seul appelant envoie toujours la clé. | P2 | Ouvert (type seulement) |
| A2 | Aucun test ne couvre le verrouillage du paiement après une erreur réseau (status 0). | P1 (test) | **Corrigé** (test ajouté) |
| A3 | `ApiExceptionHandler` mappe tout SQLSTATE 23P01 vers `VEHICLE_NOT_AVAILABLE`, y compris les chevauchements de garantie ou de hold. Libellé potentiellement trompeur. | P2 | Ouvert |
| A4 | Le statut `RESERVED` n'est jamais écrit. | P2 | Ouvert |
| A5 | `BookingHoldExpirationWorker` (`@Scheduled`) n'a pas de test de déclenchement. | P2 | Ouvert |
| A6 | Le budget Angular de `home.component` est dépassé (6.36 kB pour 4 kB). | P2 | Ouvert (avertissement) |
| A7 | Avertissement de build : module `jsbi` en CommonJS. | P2 | Ouvert (avertissement) |
| A8 | `InvoiceService.createFromQuote` n'a aucun appelant en production. | Décision propriétaire | Ouvert |

## 6. Corrections effectuées

- Ajout dans `frontend/src/app/features/quotes/quotes.spec.ts` d'un test : après une erreur réseau sur le paiement, le bouton reste désactivé et un second clic n'envoie aucune requête. Ce test passe, mais je ne l'ai pas vérifié contre une version défectueuse du code.

## 7. Fichiers modifiés

- `frontend/src/app/features/quotes/quotes.spec.ts` (test ajouté)
- `docs/architecture/partner-availability/review/lot8-validation-report.md` (créé)

Fichiers modifiés lors des lots précédents, conservés : `backend/.../InvoiceDocumentService.java`, `lot7-validation-report.md`. Aucun commit.

## 8. Résultats détaillés des tests

| Suite | Commande | Résultat | Nature |
|---|---|---|---|
| Backend compilation et tests | `mvnw.cmd -B -q test` (JDK 21) | 258 tests, 0 échec, 0 erreur, 9 ignorés | **H2 uniquement** |
| Angular tests | `ng test --watch=false` (Node 22.22.3) | 110/110 sur 16 fichiers | Unitaires |
| Angular build | `ng build` | Réussi, 2 avertissements (A6, A7) | Build de production |
| Quotes (ciblé) | `ng test --include quotes` | 20/20 | Unitaires |

Les 9 tests ignorés sont les classes `*PostgresIntegrationTests` et `*PostgresTest`, gardées par `ELITE_POSTGRES_TEST`. Ils n'ont **pas** été exécutés.

Playwright : 22 scénarios du Lot 7 sur API simulée. Non rejoués dans ce lot. Résultat simulé, pas un E2E réel.

## 9. Problèmes restant ouverts

- A1, A3, A4, A5, A6, A7, A8 (voir section 5).
- Validation PostgreSQL réelle (`et_lot7_tester` indisponible).
- E2E Playwright sur backend réel.
- Décisions propriétaire : déclenchement de la facturation, remboursements et pénalités (hors MVP), réservations payées déjà démarrées, valeurs métier.
- Base `et_a4_it_20261104` sur 127.0.0.1:15432 d'origine inconnue : à ne pas supprimer.

## 10. Dépendances avec les lots précédents

- Lot 7 : statut inchangé. Les tests PostgreSQL du Lot 7 restent en attente.
- Les correspondances de la section 4 reposent sur les contrôleurs des lots 1 à 7.

## 11. Actions nécessaires pour la validation PostgreSQL finale

1. Réinitialiser le mot de passe de `et_lot7_tester` par un administrateur, de façon interactive et sans l'afficher.
2. Vérifier l'identité : `127.0.0.1:15432`, base `et_lot7_browser_20261105`, utilisateur `et_lot7_tester`, propriétaire attendu identique.
3. Lancer les tests PostgreSQL :
   `$env:ELITE_POSTGRES_TEST="true"; .\mvnw.cmd -B test` (variables de connexion fournies par l'environnement, jamais en clair dans le dépôt).
4. Lancer Playwright sur backend réel.

## Tableau de contrôle

| Contrôle | Résultat | Preuve |
|---|---|---|
| Backend | Compilation et tests H2 OK | 258 tests, 0 échec (Surefire) |
| Frontend | Build OK, tests OK | 110/110, build exit 0 |
| API Integration | Vérifiée statiquement | Tableau section 4 |
| Authentification | Vérifiée statiquement (`/refresh`, `/me`, `/csrf`) | `AuthController` |
| Autorisations | Vérifiées statiquement (`@PreAuthorize`) | Contrôleurs admin et client |
| Modules métier | Partiel | Anomalies A3 à A5, A8 |
| Tests unitaires | OK | Backend H2 et Angular |
| Tests E2E | Simulés uniquement | 22 scénarios Lot 7 non rejoués |
| PostgreSQL réel | **En attente** | Identifiants indisponibles |
| Régressions | Aucune détectée par les suites exécutées | Résultats ci-dessus |
