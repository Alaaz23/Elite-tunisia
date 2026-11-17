# Lot 7 : rapport de validation contre backend réel

Date : 2026-11-05
Verdict : **PARTIELLEMENT VALIDÉ**

## A. Résumé exécutif

La validation contre PostgreSQL réel et le backend démarré n'a pas pu être exécutée par l'agent. Les tests unitaires (109/109) et les scénarios Playwright simulés (22/22) restent les seules preuves disponibles, et ils ne valident pas l'intégration backend.

## B. Environnement

- Serveur PostgreSQL attendu : `127.0.0.1:15432` (PostgreSQL 18, cluster isolé).
- Base cible autorisée : `et_lot7_browser_20261105`.
- Utilisateur attendu : `et_lot7_tester`.
- Bases protégées, jamais touchées : `et_test_db`, `et_dev_db`, port 5432.
- Gestion des secrets : tentative DPAPI dans `%USERPROFILE%\.elitetunisia-secrets\lot7-db.json`. Le fichier est introuvable depuis l'agent (aucun dossier `.elitetunisia-secrets` dans les profils `C:\Users\*`). Aucun `.pgpass` présent.
- Version PostgreSQL, identité serveur, propriétaire de la base, utilisateur courant : **non vérifiés** (aucune connexion tentée).

## C. Audit technique

- Migrations Flyway V1 à V26 : **non exécutées** sur la base temporaire.
- Schéma, `ddl-auto: validate` : **non vérifié** contre PostgreSQL réel.
- Backend Spring Boot : **non démarré**.
- Frontend : Vitest et build validés (voir `frontend-lot-7-report.md`).

## D. Résultats des tests

| Test | Commande | Résultat |
|---|---|---|
| Backend compilation | `mvnw.cmd -B test-compile` (JDK 21) | réussie (exit 0) |
| Backend tests H2 | `mvnw.cmd -B test` (JDK 21) | 258 exécutés, 0 échec, 0 erreur, 9 ignorés (classes `*PostgresIntegrationTests` / `*PostgresTest` / `*PostgreSQL*`, à exécuter sur PostgreSQL réel) |
| Vitest unitaires | `npx ng test --watch=false` (Node 22.22.3 portable) | 109/109 sur 16 fichiers |
| Build production | `npx ng build` (Node 22.22.3 portable) | réussi (exit 0), 2 avertissements non bloquants : budget `home.component` (6,36 kB pour 4 kB) et module `jsbi` (CommonJS) |
| Playwright simulé | `playwright test -c playwright.lot7.config.ts` | 22/22 (API simulée, ne compte pas comme backend réel) |
| Playwright simulé | `playwright test -c playwright.lot7.config.ts` | 22/22 (API simulée, ne compte pas comme backend réel) |
| Flyway V1 à V26 | non exécuté | BLOQUÉ |
| Backend démarré et health | non exécuté | BLOQUÉ |
| Scénarios réels (accueil à PDF, session, accès partenaire) | non exécutés | BLOQUÉ |

## E. Modifications effectuées

Aucune modification du projet pendant cette passe. Aucun commit, push, reset ou clean.

## F. Anomalies restantes

- **Bloquant :** secret PostgreSQL inaccessible à l'agent (fichier DPAPI introuvable). Gravité haute pour la validation, faible pour le produit.
- `home.component` : 6,36 kB pour un budget de 4 kB (avertissement de build).
- Verrouillage du paiement après erreur réseau : vérification côté serveur à implémenter.
- Historique des paiements : aucun endpoint backend.
- Déclencheur de facturation (`InvoiceService.createFromQuote`) : sans appelant, décision métier en attente.

## G. Verdict final

**PARTIELLEMENT VALIDÉ — DÉVELOPPEMENT TERMINÉ, VALIDATION POSTGRESQL EN ATTENTE.** Compilation backend, tests backend H2 (258, 0 échec), tests Angular (109/109) et build production réussis. Aucun scénario n'est PASS contre le backend réel PostgreSQL. Statut : Flyway BLOQUÉ, backend démarré BLOQUÉ, scénarios réels BLOQUÉS.

## H. Procédure à exécuter par l'opérateur (sans passer le mot de passe dans le chat)

Dans un terminal PowerShell où `DATABASE_URL`, `DATABASE_USERNAME` et `DATABASE_PASSWORD` sont définies pour la session :

1. Contrôle d'identité (lecture seule) : `psql -h 127.0.0.1 -p 15432 -U et_lot7_tester -d et_lot7_browser_20261105 -c "SELECT inet_server_port(), current_database(), current_user;"` puis vérifier que la base est exactement `et_lot7_browser_20261105`.
2. Backend : `cd backend; .\mvnw.cmd spring-boot:run` avec un port libre (hors 8080, 5432, 15432), puis `curl http://localhost:<port>/actuator/health`.
3. Frontend : `ng serve --proxy-config <fichier-hors-depot>` pointant sur ce port.
4. Playwright : `LOT7_BASE_URL=http://127.0.0.1:4300 npx playwright test -c playwright.lot7.config.ts` sans mocks.

Transmettez les sorties, et je mettrai à jour ce rapport avec les résultats réels.
