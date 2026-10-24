# Lancement local

## Prérequis vérifiés

- JDK 21 (le `JAVA_HOME` utilisateur peut pointer vers Java 8 ; définir le JDK 21 dans le terminal comme ci-dessous).
- Runtime Node portable disponible sous `%LOCALAPPDATA%\EliteTunisiaTools\node-v26.12.1-win-x64` ; il satisfait `engines.node`. Il contient npm 11.20.0 alors que `package.json` déclare npm 10.9.4.
- Ports 8080 et 4200 libres avant démarrage.

## Backend sans base persistante

Le profil `test` utilise H2 en mémoire, Flyway désactivé et `create-drop`. Il est destiné à une recette locale, pas à valider PostgreSQL ni à conserver des données.
Le profil crée les séquences H2 de réservation et de devis via `schema-test.sql`; le seeder du profil fournit les rôles, permissions et le compte administrateur de test. Ces éléments ne remplacent pas les migrations Flyway.

Dans un premier terminal PowerShell :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\aaziza\Desktop\test\backend'
.\mvnw.cmd -DskipTests "-Dspring-boot.run.profiles=test" "-Dspring-boot.run.useTestClasspath=true" spring-boot:run
```

Contrôler les réponses depuis un autre terminal :

```powershell
(Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health').status
Invoke-WebRequest 'http://127.0.0.1:8080/api/health' -UseBasicParsing
```

Le démarrage standard (profil `dev`) exige explicitement `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` et `JWT_SECRET`. Ne pas choisir une instance PostgreSQL existante sans en confirmer l'identité et l'isolation. Le port 5432 de cette machine appartient à un processus PostgreSQL dont la base et le répertoire de données n'ont pas été identifiés ; il n'a pas été utilisé.

## Frontend

Dans un deuxième terminal PowerShell :

```powershell
$nodeHome = Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\node-v26.12.1-win-x64'
$env:Path = "$nodeHome;$env:Path"
Set-Location 'C:\Users\aaziza\Desktop\test\frontend'
npm ci
npm run start -- --host 127.0.0.1
```

Angular écoute sur `http://127.0.0.1:4200`; `proxy.conf.json` transmet `/api` au backend `http://localhost:8080`. L'API utilise des cookies `HttpOnly` pour le refresh et un access token en mémoire, avec CSRF sur les mutations ; elle ne persiste pas le token d'accès dans `localStorage`.

Contrôler l'accueil, le catalogue et le proxy :

```powershell
Invoke-WebRequest 'http://127.0.0.1:4200/' -UseBasicParsing
Invoke-WebRequest 'http://127.0.0.1:4200/vehicles' -UseBasicParsing
Invoke-WebRequest 'http://127.0.0.1:4200/api/health' -UseBasicParsing
```

Vérifications frontend exécutées avec le Node portable ci-dessus :

```powershell
npm test -- --watch=false
npm run build
$env:PLAYWRIGHT_EXECUTABLE_PATH = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
npm run e2e
```

Résultats de la dernière exécution : **33 tests Angular réussis**, build réussi et **18 scénarios Playwright réussis** (dont l’annuaire clients, les parcours chauffeur, affectation, prise en charge, restitution et facture des frais). Le build affiche deux avertissements non bloquants : budget SCSS de `home.component.ts` supérieur de 1,03 kB et dépendance CommonJS `jsbi`. Aucun script `lint` n'est défini.

Les vérifications E2E exigent que les deux services soient démarrés et que le backend utilise le profil `test`. Elles écrivent uniquement dans la base H2 en mémoire du processus Spring Boot ; redémarrer ce processus repart d'un état de test vierge. Si Edge n'est pas installé, retirer `PLAYWRIGHT_EXECUTABLE_PATH` et utiliser le navigateur Playwright installé localement.

## PostgreSQL isolé sans Docker (validé le 2026-10-24)

Docker est absent, mais PostgreSQL 18 est installé dans `C:\Program Files\PostgreSQL\18`. Ses binaires (`initdb`, `postgres`, `psql`) servent, en lecture seule, à créer un **cluster jetable distinct** : répertoire de données propre sous `%LOCALAPPDATA%\EliteTunisiaTools\pg-test-cluster\data`, écoute sur `127.0.0.1:15432` uniquement, authentification SCRAM avec mot de passe aléatoire conservé dans `pgpass.local` (hors dépôt, lecture limitée à l'utilisateur). Le service existant sur le port 5432 (processus non identifié, écoute sur toutes les interfaces) n'a jamais été contacté, ni modifié.

Création (une seule fois ; refuse d'écraser un répertoire existant) :

```powershell
$ErrorActionPreference = 'Stop'
$bin  = 'C:\Program Files\PostgreSQL\18\bin'
$root = Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\pg-test-cluster'
if (Test-Path $root) { throw "$root existe déjà ; l'identifier avant de continuer." }
if (Get-NetTCPConnection -State Listen -LocalPort 15432 -ErrorAction SilentlyContinue) { throw 'Le port 15432 est occupé.' }
New-Item -ItemType Directory $root | Out-Null
$bytes = [byte[]]::new(24); $rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose()
$pw = [Convert]::ToBase64String($bytes).Replace('+','A').Replace('/','B').Replace('=','C')
[IO.File]::WriteAllText("$root\pw.tmp", $pw)
& "$bin\initdb.exe" -D "$root\data" -U et_test -A scram-sha-256 --pwfile="$root\pw.tmp" -E UTF8 --no-locale
Remove-Item "$root\pw.tmp" -Force
[IO.File]::WriteAllText("$root\pgpass.local", $pw)
icacls "$root\pgpass.local" /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null
```

Démarrage : `postgres.exe` doit tourner dans une session qui survit à la commande (avec `pg_ctl start` et un pipe, ou si le terminal parent est arrêté, le serveur s'arrête aussi). Dans un terminal dédié, laissé ouvert :

```powershell
$root = Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\pg-test-cluster'
& 'C:\Program Files\PostgreSQL\18\bin\postgres.exe' -D "$root\data" -p 15432 -c listen_addresses=127.0.0.1
```

Création de la base vide (utiliser `-P pager=off`, sinon `psql` attend un pager interactif) :

```powershell
$bin  = 'C:\Program Files\PostgreSQL\18\bin'
$root = Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\pg-test-cluster'
$env:PGPASSWORD = [IO.File]::ReadAllText("$root\pgpass.local")
& "$bin\psql.exe" -P pager=off -h 127.0.0.1 -p 15432 -U et_test -d postgres -Atc "select current_setting('data_directory')"
& "$bin\psql.exe" -P pager=off -h 127.0.0.1 -p 15432 -U et_test -d postgres -c "CREATE DATABASE et_test_db"
Remove-Item Env:\PGPASSWORD
```

Validation complète (migrations V1→V16 sur base vide + tests d'intégration). Pour une exécution déterministe, recréer `et_test_db` avant chaque passage (`DROP DATABASE IF EXISTS et_test_db` puis `CREATE DATABASE et_test_db`, uniquement sur ce cluster) :

```powershell
$root = Join-Path $env:LOCALAPPDATA 'EliteTunisiaTools\pg-test-cluster'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ELITE_POSTGRES_TEST = 'true'
$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/et_test_db'
$env:DATABASE_USERNAME = 'et_test'
$env:DATABASE_PASSWORD = [IO.File]::ReadAllText("$root\pgpass.local")
Set-Location 'C:\Users\aaziza\Desktop\test\backend'
.\mvnw.cmd -Ppostgres-test verify
'ELITE_POSTGRES_TEST','DATABASE_URL','DATABASE_USERNAME','DATABASE_PASSWORD' | ForEach-Object { Remove-Item "Env:\$_" -ErrorAction SilentlyContinue }
```

Démarrage de l'application sur cette base (profil `dev` par défaut : Flyway activé, Hibernate `validate`). Le secret JWT est généré aléatoirement pour la session, sans être écrit :

```powershell
$bytes = [byte[]]::new(48); $rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/et_test_db'; $env:DATABASE_USERNAME = 'et_test'
$env:DATABASE_PASSWORD = [IO.File]::ReadAllText("$root\pgpass.local"); $env:SERVER_PORT = '18080'
.\mvnw.cmd -DskipTests spring-boot:run
```

Sur cette base, aucun compte administrateur n'existe : le seeder `superadmin@example.test` n'est actif qu'avec le profil `test`/H2. Un premier compte staff doit être créé explicitement pour une recette manuelle.

Arrêt : `Ctrl+C` dans le terminal de `postgres.exe` (ou `pg_ctl -D "$root\data" stop`). Suppression : retirer le répertoire `pg-test-cluster` quand il n'est plus utile.

Résultats obtenus avec cette procédure : voir [elite-tunisia-audit-final.md](audit/elite-tunisia-audit-final.md).

## PostgreSQL isolé via Docker (non exécuté dans cet environnement)

Docker n'est pas installé ici. `docker-compose.dev.yml` expose le port 5433 sur loopback, mais utilise un volume nommé persistant : ce fichier n'est pas la base éphémère des tests. `docker-compose.yml` utilise également un volume persistant sur 5432. Ni l'un ni l'autre ne doit être démarré si le service ou les données sont inconnus. Ne jamais utiliser `down -v` comme nettoyage.

Lorsque Docker sera disponible, lancer uniquement un conteneur temporaire sans volume, avec un nom et un port libres. Cette procédure utilise une valeur aléatoire gardée dans l'environnement du terminal et limite les tests à `127.0.0.1:15432/et_test_db` :

```powershell
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker est requis.' }
if (Get-NetTCPConnection -State Listen -LocalPort 15432 -ErrorAction SilentlyContinue) {
    throw 'Le port 15432 est déjà occupé ; identifier le service avant de continuer.'
}

$containerName = "elite-tunisia-test-$([guid]::NewGuid().ToString('N'))"
$randomBytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($randomBytes)
    $env:POSTGRES_PASSWORD = [Convert]::ToBase64String($randomBytes)
} finally {
    $rng.Dispose()
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}

docker run -d --rm --name $containerName `
    -e POSTGRES_DB=et_test_db -e POSTGRES_USER=et_test -e POSTGRES_PASSWORD `
    -p 127.0.0.1:15432:5432 postgres:15
if ($LASTEXITCODE -ne 0) { throw 'Le conteneur PostgreSQL de test n’a pas démarré.' }

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $containerName pg_isready -U et_test -d et_test_db *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'PostgreSQL de test n’est pas prêt.' }
    docker inspect --format '{{.State.Running}} {{json .Mounts}}' $containerName

    $env:ELITE_POSTGRES_TEST = 'true'
    $env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:15432/et_test_db'
    $env:DATABASE_USERNAME = 'et_test'
    $env:DATABASE_PASSWORD = $env:POSTGRES_PASSWORD
    Set-Location 'C:\Users\aaziza\Desktop\test\backend'
    .\mvnw.cmd -Ppostgres-test verify
    if ($LASTEXITCODE -ne 0) { throw 'Les tests/migrations PostgreSQL ont échoué.' }
} finally {
    'ELITE_POSTGRES_TEST','DATABASE_URL','DATABASE_USERNAME','DATABASE_PASSWORD','POSTGRES_PASSWORD' |
        ForEach-Object { Remove-Item "Env:\$_" -ErrorAction SilentlyContinue }
    docker stop $containerName *> $null
}
```

`-Ppostgres-test verify` active explicitement les tests PostgreSQL et Flyway ; le helper refuse toute URL sauf le port loopback 15432 avec la base et le compte attendus. N'exécuter cette procédure qu'après avoir confirmé que le listener est le conteneur temporaire nouvellement créé. Elle n'a pas été exécutée dans cet environnement. La commande `docker-compose.dev.yml` ne remplace pas cette procédure et son volume ne doit pas être supprimé.
