# Runbook de déploiement — Elite Tunisia

Ce document décrit la procédure **générique** de déploiement. Aucune valeur de production n'est définie ici : les éléments entre `<...>` relèvent d'une décision d'infrastructure non encore prise. Ne jamais committer de secret.

## 1. Prérequis serveur
- Java 21 (le POM refuse une version inférieure via `maven-enforcer-plugin`).
- PostgreSQL (version validée en test : 18). Un rôle applicatif dédié, sans droits superutilisateur, propriétaire du schéma `public` de la base cible.
- Un serveur web / reverse proxy pour servir le frontend et relayer `/api` : `<à choisir>`.
- Node.js `^22.22.3 || ^24.15.0 || >=26` uniquement pour **construire** le frontend (pas nécessaire à l'exécution).
- Un volume persistant pour les uploads (voir §6).

## 2. Variables d'environnement du backend
| Variable | Obligatoire en prod | Rôle |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | oui | Active `application-prod.yml` (Swagger/OpenAPI désactivés, cookies `Secure`). Sans cela, le profil par défaut est `dev`. |
| `DATABASE_URL` | oui | URL JDBC PostgreSQL. |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | oui | Identifiants du rôle applicatif. |
| `JWT_SECRET` | oui | Au moins 32 octets, généré aléatoirement (ex. `openssl rand -base64 48`). Le démarrage échoue sinon. |
| `CORS_ALLOWED_ORIGINS` | oui | Origine(s) exacte(s) du frontend, séparées par des virgules. |
| `STORAGE_LOCAL_PATH` | recommandé | Dossier des uploads (défaut : `~/.elite-tunisia/uploads`). |
| `MAX_IMAGE_SIZE`, `MAX_REQUEST_SIZE`, `MAX_IMAGE_SIZE_BYTES` | non | Limites d'upload (défauts : 10MB / 12MB / 10485760). |
| `SERVER_PORT` | non | Défaut 8080. |
| `QUOTE_DEFAULT_TAX_AMOUNT`, `QUOTE_TAX_CURRENCY`, `BOOKING_TIME_ZONE` | non | Paramètres métier (défauts : 0, TND, Africa/Tunis). Le taux de taxe est une décision métier. |

Fournir les secrets via le gestionnaire de secrets de la plateforme choisie, pas via un fichier versionné.

## 3. Base de données et migrations
1. Sauvegarder la base avant toute migration (§5).
2. Flyway s'exécute au démarrage du backend (`spring.flyway.enabled=true`, `ddl-auto=validate`). Les migrations V1 à V20 ont été validées sur un PostgreSQL isolé ; aucune donnée historique représentative n'a été testée.
3. Avant la première migration sur une base **non vide**, exécuter le contrôle en lecture seule `database/verification/preflight-before-flyway.sql`.
4. Les fichiers `_DISABLED_*` ne sont pas exécutés ; ne pas les renommer sans décision (voir `docs/security-review-images-storage-migrations.md`).
5. Retour arrière : Flyway Community n'a pas de migration inverse. Le retour arrière = restauration de la sauvegarde pré-migration (sur une base vide, jamais par-dessus la base en service) + redéploiement de la version précédente.

## 4. Premier administrateur
Voir `docs/first-admin.md` (script `database/admin/create-first-admin.sql`, exécuté manuellement). Il n'existe aucun endpoint public pour devenir administrateur.

## 5. Sauvegarde et restauration
Scripts : `scripts/db-backup.ps1` et `scripts/db-restore.ps1` ; procédure détaillée dans `docs/backup-restore.md`.
- Planifier la sauvegarde (fréquence et rétention : `<à définir>`), la stocker hors du serveur, tester périodiquement une restauration sur une **base temporaire**.
- Sauvegarder aussi le dossier des uploads : il est hors base.

## 6. Fichiers uploadés
Stockage local via `FileStorageService` (une seule implémentation locale). Sur un seul serveur : volume persistant + sauvegarde. Plusieurs instances : un disque local par instance ne convient pas ; il faut un volume partagé ou une implémentation externe (décision requise).

## 7. Construction et démarrage
Backend :
```
cd backend
./mvnw -B -ntp -DskipTests package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```
Frontend :
```
cd frontend
npm ci
npm run build   # sortie : frontend/dist/...
```
Servir `dist` en statique, avec repli sur `index.html` pour les routes Angular, et relayer `/api` vers le backend (même origine recommandée : le cookie de rafraîchissement est `SameSite=Strict` et limité à l'API).

## 8. Proxy, HTTPS et en-têtes
À décider selon l'infrastructure :
- Terminaison TLS et redirection HTTP→HTTPS ; HSTS (Spring ne l'émet que sur requêtes HTTPS vues par l'application).
- `server.forward-headers-strategy` (non configuré). Tant qu'il ne l'est pas, derrière un proxy `getRemoteAddr()` renvoie l'IP du proxy : le limiteur de connexion et l'audit voient une seule IP. Ne l'activer que si le proxy écrase les en-têtes `X-Forwarded-*` reçus.
- CSP : aucune n'est définie. Le frontend charge des polices depuis `fonts.googleapis.com` et `fonts.gstatic.com` (`index.html`) ; Angular peut injecter du CSS inline. Toute CSP doit être testée avec le build réel (en mode `Report-Only` d'abord).

## 9. Logs et supervision
- Les logs partent sur la sortie standard ; la collecte et la rotation dépendent de la plateforme `<à choisir>`.
- Health checks : `/actuator/health` (probes `liveness`/`readiness` activées) accessible sans authentification ; `/actuator/info` exige une authentification. Aucun autre endpoint Actuator n'est exposé.
- Ne pas journaliser de secrets ; vérifier les logs d'un démarrage réel.

## 10. Redémarrage
1. Arrêt propre du processus (SIGTERM).
2. Vérifier PostgreSQL et le volume d'uploads.
3. Démarrer avec le même profil et les mêmes variables.
4. Contrôles post-démarrage (§11).

## 11. Contrôles après déploiement
- `GET /actuator/health` → `UP`.
- Logs : `Successfully applied/validated ... migrations`, pas d'erreur de démarrage.
- `GET /swagger-ui.html` et `/api-docs` ne répondent pas avec du contenu (profil `prod`).
- Connexion d'un compte test, rafraîchissement, déconnexion ; vérifier les cookies (`HttpOnly`, `Secure`, `SameSite=Strict` pour le refresh).
- Une requête d'écriture sans en-tête CSRF est refusée (403).
- Un client ne voit pas les ressources d'un autre client.
- Catalogue public lisible, image uploadée par un administrateur accessible via `/api/files/...`.
- Sauvegarde fraîche réalisée et restaurable.
