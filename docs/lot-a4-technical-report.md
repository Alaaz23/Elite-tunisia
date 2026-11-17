# Lot A.4 — Rapport technique

**Verdict Lot A : NON VALIDÉ.** La validation PostgreSQL réelle n'a pas été effectuée : aucun identifiant de test n'a été fourni. Le Lot B n'a pas été commencé.

## 1. Décisions appliquées

| Décision | Application | Statut |
|---|---|---|
| Outbox : 5 tentatives, backoff 0 / 1 min / 5 min / 15 min / 1 h, passage à DEAD, historique conservé, reprise manuelle auditée | `BillingRequestService`, `BillingRequestWorker`, `BillingRequestEntity`, `BillingRequestHistoryEntity`, `V30__billing_outbox.sql` | CORRIGÉ ET TESTÉ (H2 / mocks) — IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Permission `billing:backfill` | Créée par V30, **accordée à SUPER_ADMIN uniquement** par V31 | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Contrôle backend réel | `@PreAuthorize("hasAuthority('billing:backfill')")` sur `AdminBillingBackfillController` et `AdminBillingRequestController` | CORRIGÉ ET TESTÉ (mock MVC) |
| Backfill : dry-run, confirmation par empreinte, verrou, reprise | `BillingBackfillService` | CORRIGÉ ET TESTÉ (mocks) — NON VALIDÉ SUR POSTGRESQL |
| Pas d'exécution au démarrage | Aucun `ApplicationRunner` / `@Scheduled` lié au backfill | CORRIGÉ ET TESTÉ (revue de code) |
| Node frontend isolé | Node 24.22.2 portable, hors installation globale | CORRIGÉ ET TESTÉ (build et tests unitaires) |

Décision non appliquée : l'attribution de `billing:backfill` à d'autres administrateurs est une décision métier. Elle n'a pas été prise.

## 2. Fichiers modifiés (session A.4 et passe finale)

Créés :
- `database/migrations/V31__billing_backfill_lock_audit.sql` : accorde `billing:backfill` à SUPER_ADMIN, ajoute `heartbeat_at` à `billing_backfill_lock`, crée `billing_backfill_runs` (statuts RUNNING / COMPLETED / FAILED / ABORTED / TAKEN_OVER).
- `docs/lot-a4-technical-report.md` (ce fichier).

Modifiés :
- `backend/src/main/java/tn/elitetunisia/billing/BillingBackfillService.java` : `acquireRun` (reprise si heartbeat > 30 min, marquée TAKEN_OVER), `heartbeat` par réservation, abandon `BACKFILL_LOCK_LOST`, `finishRun` / `releaseRun` en `finally`.
- `backend/src/test/java/tn/elitetunisia/billing/BillingBackfillServiceTest.java` : 8 tests (ajout : verrou occupé, reprise avec TAKEN_OVER, perte de verrou avant enfilage).
- `backend/src/main/java/tn/elitetunisia/common/config/TestProfileAdminSeeder.java` : ajout de la permission `billing:backfill` au catalogue de test.
- `docs/architecture/payment-reconciliation.md` : section 8 « Écarts constatés entre le modèle et le code ».

Fichiers des passes précédentes encore non commités : `BillingRequest*`, `AdminBillingBackfillController`, `AdminBillingRequestController`, `V30__billing_outbox.sql`, `BillingRequestServiceTest`, etc.

## 3. Résultats backend

| Vérification | Commande | Résultat |
|---|---|---|
| Compilation et suite complète (JDK 21) | `mvn clean test` avec `JAVA_HOME=...jdk-21.0.2.13-hotspot` | **295 exécutés, 0 échec, 0 erreur, 9 ignorés, BUILD SUCCESS, exit 0** (`lot-a4-full-v31.log`, `lot-a4-full-v31.exit.txt`) |
| Ciblés billing | `BillingBackfillServiceTest` (8) + `BillingRequestServiceTest` (9) | 17 exécutés, 0 échec, 0 ignoré (`lot-a4-backfill-v31.log`) |
| Suite complète relancée après correction du seeder et des E2E | `.\mvnw.cmd -B -q test`, JDK 21 | exit 0 ; rapports Surefire : 295 tests, 0 échec, 0 erreur, 9 ignorés (`%TEMP%\backend-suite-run9.log`) |

Les 9 tests ignorés sont les tests PostgreSQL protégés par `ELITE_POSTGRES_TEST`. Ils ne sont **pas** comptés comme réussis. Les protections n'ont pas été modifiées.

Nombre de tests : la passe précédente en comptait 293 ; 295 aujourd'hui, à cause des 2 tests ajoutés au backfill.

## 4. Diagnostic du code de sortie Maven (A.2)

- Avec JDK 21 : `mvnw compile` retourne 0.
- Avec le `JAVA_HOME` par défaut (JDK 8) : retour 1, dû à la règle `enforce-java-21` de `maven-enforcer-plugin` (« Java 21 minimum requis »).
- Le cas « code 1 avec BUILD SUCCESS dans le log » **n'a pas été reproduit**. Hypothèse la plus probable : code de sortie perdu dans un pipeline ou un wrapper. **Statut : NON EXPLIQUÉ DÉFINITIVEMENT.**

## 5. Frontend

- Node requis par `frontend/package.json` : `^22.22.3 || ^24.15.0 || >=26`.
- Node 24.22.2 portable utilisé, installé dans `%LOCALAPPDATA%\elite-tools\`. Node global 18.20.8 non modifié.
- Installation faite dans une copie temporaire (`%TEMP%\elite-fe-ci`) : le `npm ci` dans `frontend/` échoue (EBUSY) car le `ng serve` de l'utilisateur (port 4200) verrouille `node_modules`.
- `npm ci` (lockfile) : OK. Build production : OK. Tests unitaires : 110 OK.
- E2E Playwright (backend réel isolé sur 8080 en profil test, Angular isolé sur 4300, `ng serve` de l'utilisateur sur 4200 non touché) : **21/21 réussis, exit 0** (`%TEMP%\e2e-isolated-run8.log`, après les runs 1 à 7 qui ont permis d'identifier les causes).
- Causes corrigées, toutes côté test ou seeder, sans changer de règle métier :
  - 403 sur les écrans et API admin : CORS (origine `127.0.0.1:4300` absente, contournée par `CORS_ALLOWED_ORIGINS`), et permissions `partners:*` et `billing:backfill` absentes du catalogue du seeder de test (`TestProfileAdminSeeder.java`).
  - « Indisponible » : absence de garantie partenaire ACTIVE couvrant la période (fixture `fleet-setup.ts`).
  - `PAYMENT_REQUIRED` à la confirmation : règle métier conservée (quote ACCEPTED + paiement CAPTURED), fixture `quote-setup.ts`.
  - `QUOTE_VALIDITY_INVALID` : `validUntil` avec nanosecondes, rejeté par `QuoteService.java:54`. Corrigé dans le fixture (précision à la microseconde).
  - Scénario « véhicule pris après acceptation » : la règle de chevauchement bloque le concurrent dès la création (`VEHICLE_NOT_AVAILABLE`). L'état décrit par le scénario ne peut pas être atteint. Le scénario a été réécrit pour vérifier ce refus et la confirmation de la proposition acceptée. **Décision à valider** : la règle backend n'a pas été modifiée.
- Frontend : code applicatif non modifié. Build et 110 tests unitaires : résultats de la passe précédente, non relancés après cette passe (aucun changement de `src/`).

## 6. Tests PostgreSQL

**BLOQUÉ PAR L'ENVIRONNEMENT.** Aucun identifiant de test n'est disponible. Aucune connexion n'a été tentée, `pgpass.conf` n'a pas été lu, aucune base existante n'a été touchée.

Non exécutés (aucun n'est compté comme réussi) :
- Exécution V1 à V31 sur une base temporaire vierge sur `127.0.0.1:15432`.
- Concurrence des workers Outbox ; atomicité Outbox ; backfill sur PostgreSQL ; contraintes d'unicité ; reprise et statut DEAD.

Pour lancer la validation une fois un compte temporaire non superutilisateur fourni, il faut : créer la base dédiée, exécuter Flyway, puis lancer les tests avec `ELITE_POSTGRES_TEST` activé. Les secrets ne doivent pas être stockés dans le projet.

## 7. Vérification Outbox

- Couverte par `BillingRequestServiceTest` (9 tests, mocks) : backoff, passage à DEAD après 5 échecs, historique, reprise manuelle.
- **Non validée sur PostgreSQL** : la concurrence entre workers et l'atomicité de l'écriture (retour + demande) ne sont pas prouvées par un test réel.

## 8. Vérification backfill

- `BillingBackfillServiceTest` (8 tests, mocks) : dry-run, verrou occupé, reprise avec TAKEN_OVER, perte de verrou, classification des motifs d'ignorance.
- **Non validée sur PostgreSQL.** Les tables `billing_backfill_lock` et `billing_backfill_runs` sont en SQL brut et n'existent pas en H2 (Flyway désactivé) : la logique de verrou n'est testée qu'avec des mocks.
- Motifs d'éligibilité : conforme à la liste de A.4 (COMPLETED, retour, client, facturation, véhicule, devis accepté, période, montant, acomptes, frais). Une réservation incomplète est ignorée avec son motif.

## 9. Migrations

- V30 : non modifiée (déjà appliquée ou potentiellement appliquée). Contient des caractères mal encodés dans des commentaires (« rÃ©trofacturation »). Risque cosmétique, à corriger dans une migration ultérieure si nécessaire.
- V31 : créée, non appliquée. Doit être validée sur une base temporaire avant tout usage.
- Aucune migration ancienne n'a été supprimée ou modifiée.

## 10. Revue de payment-reconciliation.md

Écarts entre le modèle et le code, documentés dans la section 8 du document :
- `PaymentStatus` contient `AUTHORIZED`, `REFUNDED`, `CANCELLED`, non décrits dans la table des transitions.
- `UNKNOWN` n'existe ni dans l'enum ni en base. Il reste un état logique non persisté.
- `PaymentStatusProvider` n'est pas implémenté. **EN ATTENTE D'UN FOURNISSEUR EXTERNE.**
- Réconciliation : **NON CORRIGÉE / NON IMPLÉMENTÉE.** Aucun remboursement n'est simulé.

## 11. Mentions fiscales et légales Tunisie (à confirmer avant production)

Séparation des éléments :
- **Obligations à vérifier par un expert** : mentions obligatoires de la facture, numérotation, conservation, et traitement de la TVA. Aucun taux n'a été inventé ni codé.
- **Choix techniques** : numérotation unique par facture principale, distinction facture principale / complémentaire.
- **Hypothèses métier non confirmées** : statut fiscal du loueur, régime de TVA applicable aux locations, mentions à faire figurer sur les factures complémentaires.

Statut : **EN ATTENTE DE CONFIRMATION MÉTIER / FISCALE.**

## 12. État Git

- `git status --porcelain` mesuré à cette passe : **224 entrées** (62 modifiées `M`, 162 non suivies `??`). Les valeurs 210 (baseline) et 214 (annoncée) ne correspondent pas à cet état. Le rapprochement fichier par fichier reste **NON TERMINÉ**.
- Rapprochement détaillé des entrées : **NON TERMINÉ** dans cette passe. Aucune entrée n'a été attribuée au Lot A sans preuve.
- Les journaux `lot-a4-*.log` et `lot-a4-full-v31.exit.txt` sont des artefacts non commités à classer.
- Aucun commit, aucune commande destructive, aucune suppression.

## 13. Statuts par sujet

| Sujet | Statut |
|---|---|
| Outbox (5 tentatives, backoff, DEAD) | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Permission `billing:backfill` (V31, SUPER_ADMIN) | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Backfill (dry-run, verrou, reprise) | IMPLÉMENTÉ MAIS NON VALIDÉ SUR POSTGRESQL |
| Suite backend H2 (295 tests, 0 échec) | CORRIGÉ ET TESTÉ |
| Tests PostgreSQL réels (9 ignorés) | BLOQUÉ PAR L'ENVIRONNEMENT |
| Frontend build et tests unitaires | CORRIGÉ ET TESTÉ (résultats de la passe précédente) |
| Frontend E2E (21/21 réussis, isolé) | CORRIGÉ ET TESTÉ |
| Maven code de sortie 1 (A.2) | Partiellement expliqué (JDK 8 : enforcer). Cas « exit 1 avec BUILD SUCCESS » non reproduit |
| Réconciliation des paiements | NON CORRIGÉE |
| PaymentStatusProvider (fournisseur) | EN ATTENTE D'UN FOURNISSEUR EXTERNE |
| Mentions fiscales Tunisie | EN ATTENTE DE CONFIRMATION MÉTIER / FISCALE |

## 14. Risques résiduels

1. Aucune preuve PostgreSQL : la concurrence du verrou, l'Outbox et les contraintes ne sont validées qu'en mocks.
2. V31 et V30 peuvent être appliquées sur un environnement partagé sans que cela soit connu.
3. E2E frontend en échec : impossible de garantir le parcours admin.
4. `billing:backfill` limité à SUPER_ADMIN : décision métier à confirmer.
5. Mentions fiscales Tunisie non validées.
6. Le `ng serve` de l'utilisateur sur 4200 peut bloquer les installations dans `frontend/`.

## 15. Points à décider

- Fournir un compte PostgreSQL temporaire non superutilisateur sur `127.0.0.1:15432`, pour une base dédiée.
- Confirmer que SUPER_ADMIN est le seul rôle autorisé à `billing:backfill`.
- Autoriser l'arrêt du `ng serve` (4200) pour le diagnostic E2E.
- Fournir les informations fiscales Tunisie validées.

Le Lot B n'a pas été commencé.
