# Matrice d'autorisations d'implémentation — Elite Tunisia

Statut : **Phase 0.1, documentaire**. Ce document liste les opérations qui exigent une **autorisation explicite** de l'utilisateur. Il n'autorise rien par lui-même. Une autorisation s'applique à une opération précise, pour un périmètre précis, et ne vaut pas pour les opérations voisines.

Voir aussi : [master-project-completion-plan.md](./master-project-completion-plan.md), [implementation-roadmap.md](./implementation-roadmap.md), [business-decisions-pending.md](./business-decisions-pending.md).

## 1. Règles générales

1. Une autorisation est **explicite, datée et limitée** : elle indique l'opération, le périmètre et la base ou l'environnement concerné.
2. Une autorisation de lecture (Git, fichiers, `EXPLAIN` en Phase 2 si autorisé) n'autorise pas l'écriture.
3. Aucune opération destructive n'est autorisée sur une base réelle ou non isolée.
4. Les fichiers non suivis et les modifications locales sont **préservés** : aucune suppression, réinitialisation, écrasement ou sauvegarde qui écrase une sauvegarde existante.
5. Toute opération non listée ici est soumise à autorisation.

## 2. Matrice

Légende des statuts : **Interdit** (aucune exécution autorisée en l'état) ; **Autorisation requise** (peut être demandée) ; **Lecture seule** (autorisée sans écriture).

| # | Opération | Périmètre | Autorisation requise | Qui autorise | Statut actuel | Phase / tâche |
|---|---|---|---|---|---|---|
| 1 | Lecture de l'état Git (`git status`, `git diff`, `git --no-pager log`) | Dépôt local | Lecture seule | Déjà couvert | Autorisé | Toutes |
| 2 | Connexion PostgreSQL | Instance isolée 127.0.0.1:15432 uniquement | Autorisation Phase 2 écrite | Utilisateur | **Interdit** | Phase 2, T-A01 |
| 3 | Connexion PostgreSQL sur le port 5432 | Instance locale par défaut | Non demandée | — | **Interdit** (exigence explicite) | — |
| 4 | Connexion à la base réelle ou à une base non isolée | Toute base contenant des données existantes | Non demandée | — | **Interdit** | — |
| 5 | Exécution de tests PostgreSQL (`mvnw -Ppostgres-test verify`) | Base isolée, jetable, 127.0.0.1:15432 | Autorisation Phase 2 écrite, avec la base cible nommée | Utilisateur | **Interdit** | T-A01 à T-A04 |
| 6 | Lancement de `scripts/test-backend-postgres.ps1` | Script non suivi, contient `DROP DATABASE ... WITH (FORCE)` (ligne 44) | Autorisation distincte **après** remplacement ou validation du script | Utilisateur, après revue | **Interdit** en l'état | T-A01 |
| 7 | Modification ou remplacement de `scripts/test-backend-postgres.ps1` | Fichier non suivi | Autorisation distincte | Utilisateur | Non autorisé | T-A01 |
| 8 | Application de V21 (`database/proposed/V21__payments_one_captured_per_quote.sql`) | Base isolée | Autorisation distincte, après précontrôle des doublons | Utilisateur | **Interdit** | T-A04 |
| 9 | Application de V22 (`database/proposed/V22__payments_quote_fk_restrict.sql`) | Base isolée | Autorisation distincte, après décision D1/D3/D5 et validation V22 | Utilisateur | **Interdit** | Registre V22 |
| 10 | Modification de V21 ou V22 | Fichiers `database/proposed/` | Autorisation distincte | Utilisateur | Non autorisé | Registre V22 |
| 11 | Migration sur copie représentative de données | Copie isolée | Autorisation distincte, et copie fournie | Utilisateur | **Interdit** (aucune copie disponible) | Lot 1 |
| 12 | Déplacement, renommage ou suppression du doublon V11 | `database/` (copie inactive) | Autorisation distincte (T-L03) | Propriétaire du dépôt | **Interdit** | T-L03, D15 |
| 13 | Commit des modifications locales (9 fichiers suivis modifiés) | Dépôt local | Autorisation explicite | Utilisateur | **Interdit** | Avant lot 0 |
| 14 | Commit des fichiers non suivis (tests PostgreSQL, `docs/`, `database/proposed/`, script) | Dépôt local | Autorisation explicite, fichier par fichier si nécessaire | Utilisateur | **Interdit** | Avant lot 1 |
| 15 | Push vers le dépôt distant | Remote GitHub | Autorisation explicite | Utilisateur | **Interdit** | Avant T-A06 |
| 16 | Modification de `.github/workflows/ci.yml` | CI | Autorisation explicite | Utilisateur | Non autorisé | T-A06, T-A01 |
| 17 | Modification de la configuration applicative (`application*.yml`, `server.forward-headers-strategy`) | Configuration backend | Autorisation explicite | Utilisateur | Non autorisé | T-L01a |
| 18 | Modification de `SecurityConfig.java` (en-têtes, CSP, actuator) | Code de sécurité | Autorisation de la phase d'implémentation | Utilisateur | Non autorisé | T-L01a |
| 19 | Ajout d'une dépendance Maven ou npm | `pom.xml`, `package.json` | Justification écrite et autorisation | Utilisateur | Non autorisé | Toutes |
| 20 | Ajout d'un service externe (Redis, base partagée, prestataire) | Infrastructure | Justification écrite et autorisation | Utilisateur | **Interdit** | T-L01b, D11 |
| 21 | Modification du code applicatif (service, contrôleur, écran) | `backend/src`, `frontend/src` | Autorisation de la phase d'implémentation | Utilisateur | **Interdit** (Phase 0.1) | Lots 0 et 1 |
| 22 | Changement de comportement de l'expiration des devis | `QuoteService.java` | Autorisation de la phase d'implémentation, avec tests | Utilisateur | Non autorisé | T-A07 |
| 23 | Ajout d'une migration Flyway de production | `database/migrations/` | Autorisation distincte, après décisions bloquantes | Utilisateur | **Interdit** | Lots 1 et 3 |
| 24 | Suppression ou réinitialisation de données | Toute base | Non demandée | — | **Interdit** | — |
| 25 | Déploiement (préproduction ou production) | Tout environnement | Autorisation explicite, distincte pour chaque environnement | Utilisateur | **Interdit** | Lot 6 |
| 26 | Création ou modification de sauvegarde | Dossier de sauvegarde | Autorisation explicite ; ne jamais écraser une sauvegarde existante | Utilisateur | Non autorisé | Lot 6, T-L02 |
| 27 | Lancement de `npm install`, `npm ci` ou `mvnw` | Dépendances locales | Autorisé seulement si un manifeste a changé ou si une commande échoue faute de dépendance | Harnais | Conditionnel | Lots 0 et 1 |
| 28 | Modification des documents `docs/architecture/` | Documentation | Autorisée dans la phase documentaire en cours, par fichier listé | Utilisateur | Autorisé pour les fichiers listés en Phase 0.1 | Phase 0.1 |
| 29 | Décision métier (D1 à D15, P-01, P-02) | Décisions du propriétaire | Décision du propriétaire, consignée | Propriétaire du produit | **Non prise** | Voir [business-decisions-pending.md](./business-decisions-pending.md) |

## 3. Opérations interdites en permanence (hors autorisation expresse)

- Connexion au port 5432 et à toute base réelle ou non isolée.
- Exécution du script destructif sans remplacement validé.
- Suppression, réinitialisation ou écrasement de fichiers, de modifications locales ou de sauvegardes.
- Push, déploiement ou publication.
- Toute opération qui modifie des données existantes.

## 4. Autorisations à demander, par lot

| Lot | Autorisations à demander avant d'entrer dans le lot |
|---|---|
| Lot 0 — Sécurisation | Commit ou push pour valider la CI (#13, #15) ; modification de `ci.yml` (#16) ; configuration des en-têtes (#17, #18) ; décision P-01 pour T-L04 |
| Lot 1 — Stabilisation PostgreSQL | Connexion à 127.0.0.1:15432 (#2) ; tests PostgreSQL (#5) ; remplacement ou validation du script (#6, #7) ; commit des fichiers de test (#14) ; V21 (#8) ; précontrôle des doublons (lecture, à autoriser séparément) |
| Lot 2 — Décisions | Aucune autorisation technique ; décisions du propriétaire (#29) |
| Lot 3 — Métier | Décisions D2, D7, D8 ; V22 (#9, #10) ; application du correctif T-A05 après affectation de P-02 |
| Lot 6 — Production | Déploiement (#25) ; sauvegarde (#26) ; restauration sur base isolée (#11) |

## 5. Procédure de demande

Pour chaque autorisation, l'utilisateur indique :
1. L'opération (numéro de la matrice).
2. Le périmètre (base, fichiers, environnement).
3. La date ou la session concernée.
4. Les opérations voisines explicitement exclues.

Sans réponse explicite, la valeur par défaut est **Interdit**.
