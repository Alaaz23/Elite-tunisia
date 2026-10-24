# Elite Tunisia

Plateforme premium de location de véhicules et de services de mobilité avec chauffeur en Tunisie.

> Étape 3 : authentification, rôles/permissions, profil client de base et administration des utilisateurs sont implémentés et testés. Les réservations, devis, paiements, factures et catalogues métier restent à développer.

## Architecture

- `frontend/` : Angular standalone, TypeScript strict, routes lazy, layouts public/client/admin et design system SCSS.
- `backend/` : monolithe modulaire Spring Boot 4.1.1, Java 21, Spring Security, JPA, JWT et OpenAPI.
- `database/migrations/` : migration Flyway initiale et source SQL versionnée.
- PostgreSQL est le seul service Docker du MVP. Aucun microservice ni Redis n'est ajouté.

Les guards Angular améliorent la navigation, mais ne constituent pas une frontière de sécurité. Les routes API protégées doivent toujours vérifier l'identité et les permissions dans Spring Security.

## Prérequis

- Node.js 22.22.3 ou plus récent (ou 24.15.0+), npm 10+.
- Java 21 LTS et `JAVA_HOME` pointant vers ce JDK.
- Docker Desktop avec Docker Compose, ou une instance PostgreSQL 18.

Vérifier sous PowerShell :

```powershell
node --version
java -version
$env:JAVA_HOME
docker compose version
```

## Installation et démarrage

À la racine du dépôt, lancer PostgreSQL :

```powershell
docker compose up -d
```

Frontend :

```powershell
cd frontend
npm install
npm start
```

Le site est servi sur `http://localhost:4200`.

Backend, dans un autre terminal :

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Sous macOS/Linux, utiliser `./mvnw spring-boot:run`. Le profil par défaut est `dev`; l'URL PostgreSQL et les identifiants locaux correspondent aux valeurs Compose par défaut. `application-prod.yml` exige les variables d'environnement et les cookies sécurisés.

Contrôles rapides :

- API : `http://localhost:8080/api/health`
- Swagger UI : `http://localhost:8080/swagger-ui.html`
- OpenAPI : `http://localhost:8080/api-docs`
- Actuator : `http://localhost:8080/actuator/health`

## Tests

Frontend :

```powershell
cd frontend
npm test -- --watch=false
npm run build
```

Backend :

```powershell
cd backend
.\mvnw.cmd test
```

Le profil de test utilise H2 en mémoire. La migration et la connexion PostgreSQL doivent aussi être validées dans un environnement où PostgreSQL/Compose est accessible.

Tests E2E (profil isolé H2; démarrer le backend et Angular dans deux terminaux) :

```powershell
# Terminal 1: backend de test et super-administrateur fictif réservé au profil test
cd backend
& (Join-Path (Get-Location) 'mvnw.cmd') spring-boot:run '-Dspring-boot.run.profiles=test' '-Dspring-boot.run.useTestClasspath=true'

# Terminal 2: frontend
cd frontend

# Terminal 3, après le démarrage des serveurs:
$env:PLAYWRIGHT_EXECUTABLE_PATH = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
npm run e2e
```

Le serveur Angular proxy `/api` vers `localhost:8080`. Si un navigateur Playwright est déjà installé, `PLAYWRIGHT_EXECUTABLE_PATH` peut être omis. La fixture admin ne s'active que dans le profil `test`.

## API implémentée

- `GET /api/health`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/customer/profile`, `PUT /api/customer/profile`
- `GET /api/admin/users`, `GET /api/admin/users/{id}`, `POST /api/admin/users`, `PUT /api/admin/users/{id}`, `PATCH /api/admin/users/{id}/status`
- `GET /api/admin/roles`

L'inscription et la connexion produisent un access token JWT de 15 minutes. Le refresh token opaque est stocké sous forme de hash en base et remis via un cookie HttpOnly, SameSite Strict; `Secure` est activé en production. Les données user/roles/permissions passent par le backend. Aucun endpoint de réservation ou de catalogue n'est encore déclaré comme opérationnel.

## Configuration et données

Copier `.env.example` vers `.env` pour modifier les valeurs de Compose. Ce fichier ne contient que des exemples locaux; ne jamais committer `.env` ni utiliser les mots de passe locaux en production. Les variables d'environnement du backend doivent être exportées dans son processus ou configurées dans l'environnement d'exécution.

La migration initiale crée les tables socles et le référentiel des rôles. Les tables des domaines métier sont préparatoires : aucun CRUD correspondant n'est fourni à cette étape.

## État de préparation

Ce dépôt n'est pas prêt pour la production. Il reste notamment à livrer les modules métier, la limitation de débit, la validation de la politique CSRF selon le domaine de déploiement, la configuration d'un fournisseur de paiement, les contenus juridiques, les tests PostgreSQL/E2E et l'exploitation (stockage, sauvegardes, monitoring et déploiement).
