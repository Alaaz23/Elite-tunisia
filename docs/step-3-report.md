# Step 3 — Authentication and Users Report

| Fonctionnalité | Statut | Preuve |
| --- | --- | --- |
| Registration | PASS | Inscription avec validation des champs, téléphone, mot de passe fort/confirmé, e-mail unique, BCrypt, rôle `CUSTOMER` imposé côté serveur, profil et refresh token créés. |
| Login | PASS | E-mail/mot de passe vérifiés; comptes désactivés refusés; JWT court et cookie refresh HttpOnly émis. |
| Logout | PASS | Cookie refresh révoqué; une tentative de refresh ultérieure est refusée. |
| Refresh token | PASS | Rotation et verrouillage transactionnel; expiration, révocation et rejeu testés; un rejeu révoque la famille. Hash SHA-256 uniquement en base. |
| Roles | PASS | Six rôles stockés en base via `user_roles`; rôles client et internes vérifiés. |
| Permissions | PASS | Permissions relationnelles en base; `admin:access`, `users:read/manage` vérifiés côté backend. |
| Customer profile | PASS | GET/PUT liés à l'utilisateur authentifié; aucun ID client dans l'URL; tentative d'accès au profil d'un autre client retourne 404. |
| Admin users | PASS | Liste paginée, détail, création employé, modification de rôle/profil et statut; désactivation révoque les refresh tokens. Réservé à `SUPER_ADMIN` par permission `users:manage`. |
| Database migrations | PASS | Flyway a appliqué V1/V2 sur PostgreSQL 18 vierge; Hibernate `ddl-auto=validate` a démarré sans divergence; `/api/health` et Actuator ont répondu 200. Test sur cluster temporaire jetable. |
| Security tests | PASS | 10 tests backend : validation, mauvais mot de passe, duplicata, `/me` sans secrets, client→admin, non authentifié→client, isolation de profil, rotation/rejeu/logout/expiration, compte désactivé, audit et gestion super-admin. |
| Frontend tests | PASS | 7 tests unitaires frontend, dont HttpClient auth/CSRF/session et validation des formulaires. |
| E2E tests | PASS | 2 parcours Playwright/Edge : client register/profile/refus admin/logout; super-admin crée un agent, change son rôle, le désactive puis le login est refusé. |

## Base de données et migrations

Les migrations `V1` et `V2` ont été appliquées par Flyway sur un cluster PostgreSQL 18 temporaire vierge; Hibernate `validate` a ensuite accepté toutes les entités. L'API a démarré et les endpoints health ont répondu 200. Les tables, relations, index et catalogues RBAC ont été vérifiés sur cette instance jetable. Le code empaquette les migrations dans `db/migration`.

**Limite :** l'application n'a pas démarré contre la base PostgreSQL permanente de la machine, car ses identifiants locaux ne sont pas disponibles; Docker n'est pas installé. Le profil E2E utilise H2. Le démarrage de production Flyway contre la base PostgreSQL provisionnée pour le projet reste à vérifier avant déploiement.

Les scripts E2E nécessitent les serveurs déjà démarrés avec le profil `test` et utilisent Edge local via `PLAYWRIGHT_EXECUTABLE_PATH`, car le téléchargement Playwright Chromium est bloqué par le certificat réseau de cette machine.

## Limites restantes

- `Forgot password` est encore un placeholder; aucun endpoint de récupération ou de réinitialisation n'a été demandé/ajouté à l'API de cette étape.
- Vérification d'e-mail, limitation de débit, règles de verrouillage et matrice fine de toutes les opérations métier restent à livrer.
- Le compte E2E `superadmin@example.test` / `Test-Admin-123!` n'est créé que si le profil `test` et `app.test-seed.admin=true` sont actifs. Ne pas utiliser ces identifiants hors test.
- Tests Playwright exécutés avec Microsoft Edge déjà installé, car le téléchargement Chromium Playwright échoue sur le certificat réseau local.
- L'instance PostgreSQL de développement doit être créée/configurée puis démarrée avec les identifiants du `.env` local; aucune valeur de la base machine n'a été lue ou modifiée.

## Décision de mise en production

**NOT READY FOR PRODUCTION.** Les parcours d'authentification et de gestion de comptes testés fonctionnent sur H2 et en E2E, mais la configuration opérationnelle PostgreSQL, la récupération de compte, le rate limiting, les contenus légaux, la gestion des secrets et la revue CSRF/CORS des domaines de production restent nécessaires.