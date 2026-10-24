# Elite Tunisia — bilan de validation

## Résumé exécutif

Le projet peut être lancé localement en mode recette avec Spring Boot sur H2 en mémoire et Angular. **Une validation PostgreSQL/Flyway réelle a été exécutée le 2026-10-24** sur un cluster PostgreSQL 18.3 jetable et isolé (`127.0.0.1:15432`, base vide `et_test_db`, créé avec les binaires locaux, sans Docker) : migrations V1→V16 appliquées sans erreur SQL, Hibernate `validate` satisfait, suite de tests PostgreSQL complète réussie. Le service PostgreSQL du port 5432 n'a jamais été contacté ; aucune base existante n'a été utilisée ni modifiée. La validité des migrations sur une base **contenant des données** reste non démontrée.

## Vérifications réellement effectuées

### Backend

- JDK 21.0.2 / Maven Wrapper 3.9.16.
- La première tentative Maven utilisait le `JAVA_HOME` utilisateur qui pointait vers JDK 8 et a échoué à compiler. En définissant `JAVA_HOME` vers JDK 21 dans le terminal de la commande, le test a réussi.
- `.\mvnw.cmd -DskipITs test` (après les modifications backend de cette phase) : **88 tests, 0 échec, 0 erreur, 10 ignorés** (78 exécutés sans échec). Les ignorés incluent les tests conditionnés à PostgreSQL, exécutés séparément (voir « PostgreSQL et migrations »).
- `.\mvnw.cmd -Ppostgres-test validate` : BUILD SUCCESS ; cela valide uniquement le modèle Maven/profil, pas une connexion ou les tests PostgreSQL.
- Le scope H2 est `test`. `.\mvnw.cmd -DskipTests package` : BUILD SUCCESS ; le JAR contenait 12 migrations Flyway uniques lors de cette vérification (antérieure à V13–V16 ; à refaire avant déploiement), aucun JAR H2 et le driver PostgreSQL. `schema-test.sql` est une ressource inerte du JAR, référencée uniquement par le profil `test`.
- Le profil `test` utilise H2 en mémoire, `ddl-auto=create-drop` et Flyway désactivé. Le démarrage local révélait une séquence de réservation absente : `schema-test.sql` initialise les séquences H2 de réservation et de devis. Le seeder de test accorde aussi `payments:create` au rôle `CUSTOMER`, conformément à V10. Ce profil n'est pas une preuve du schéma PostgreSQL.
- Le handler d'erreurs API journalise désormais le chemin et le type d'exception inattendue sans inclure le corps de requête; les détails de pile ne sont disponibles qu'au niveau DEBUG.

### Frontend

- Runtime portable vérifié : Node 26.12.1 ; npm 11.20.0. Le champ `packageManager` déclare npm 10.9.4, donc la version npm n'est pas strictement identique à la déclaration du dépôt.
- `npm ci` : réussite, 271 paquets ajoutés, zéro vulnérabilité rapportée. npm a signalé quatre scripts d'installation de dépendances non approuvés ; aucun script n'a été approuvé automatiquement.
- `npm test -- --watch=false` : **33 tests réussis dans 8 fichiers**.
- `npm run build` : réussite. Avertissements non bloquants : styles SCSS de `home.component.ts` au-dessus du seuil de recommandation de 4 kB et dépendance CommonJS `jsbi`.
- `npm audit --audit-level=low` : zéro vulnérabilité.
- `npm run e2e -- --reporter=line` avec Edge local : **18 scénarios réussis, 0 échec**, après redémarrage du backend H2 pour charger les permissions et endpoints mis à jour. Le scénario annuaire client et les 5 tests de `bookings.spec.ts` ont aussi réussi séparément, ces derniers couvrant la création et l'affectation d'un chauffeur certifié, la prise en charge, la restitution et la facture complémentaire de frais.
- Aucun script `lint` n'est défini dans `frontend/package.json`.

### Services et intégration locale

- Backend `http://127.0.0.1:8080` et Angular `http://127.0.0.1:4200` sont démarrés dans cet environnement.
- Contrôles HTTP 200 : backend `/actuator/health`, `/api/health`, `/api/auth/csrf`; frontend `/`, `/vehicles`, `/api/health` et `/api/bookings/policy`. Le proxy Angular vers le backend a donc été exercé.
- Le front utilise les routes API relatives et `proxy.conf.json` vers le backend local. L'authentification Angular garde l'access token en mémoire, emploie les cookies de refresh avec `withCredentials`, CSRF et les guards de rôle. Les vérifications de tests couvrent le service des factures client/admin et leurs routes PDF.
- Les endpoints client et admin des factures ont été alignés avec leurs contrôleurs et DTOs. Les tests Angular de contrat couvrent ces routes.

## Corrections et contrôles de sécurité

- H2 reste uniquement une dépendance de test.
- Les valeurs de connexion PostgreSQL et `JWT_SECRET` sont obligatoires dans la configuration Spring Boot standard, au lieu de secrets et d'une URL de base locaux par défaut. Le profil `test` fournit sa configuration H2 dédiée.
- Les fichiers Compose exigent maintenant une variable `POSTGRES_PASSWORD`; ils ne fournissent plus de mot de passe faible par défaut. Le binding local est sur loopback, mais les volumes Compose restent persistants.
- Le profil Maven `postgres-test` ne s'active qu'à la demande et transmet les variables d'environnement explicitement. Le helper refuse toute cible autre que `127.0.0.1:15432/et_test_db` avec l'utilisateur `et_test`, puis vérifie l'identité de la base via JDBC.
- Les migrations V11/V12 ont été corrigées statiquement, puis exécutées avec succès sur PostgreSQL 18.3 (base vide). V11 évite la syntaxe PostgreSQL invalide `ADD CONSTRAINT IF NOT EXISTS`. V12 ajoute les champs opérationnels manquants de `drivers` et fournit une valeur par défaut à l'ancienne colonne `status`.
- Le test PostgreSQL d'idempotence ne dépend plus de UUID inexistants et ne masque plus toutes ses erreurs ; il est conditionné au profil PostgreSQL et utilise des fixtures valides.
- Le formulaire API admin de création de chauffeur demande maintenant un numéro de licence, requis par le schéma V1, et valide les champs avant sauvegarde.

## État fonctionnel et limites

- Parcours E2E validés sur H2 : catalogue/détails, inscription/connexion, rôles et accès admin, réservation/disponibilité, devis (création, acceptation, refus, modification), paiement simulé et protection contre l'accès à la réservation d'un autre client.
- Les factures, profils et formulaires de gestion sont couverts par des tests unitaires/contrat ou backend, mais ne font pas tous l'objet d'un parcours E2E complet.
- Parcours présents et partiellement couverts : factures, gestion admin des véhicules/taxonomies/utilisateurs et routes API de gestion chauffeurs/affectations.
- La page admin des chauffeurs permet la création et la désactivation via l'API ; le détail de réservation permet l'affectation/désaffectation. Le parcours E2E de création, affectation et vérification de certification est passé.
- L'annuaire admin des clients est connecté à `/api/admin/customers`, paginé, filtrable par nom/e-mail/téléphone et en lecture seule. L'accès est réservé au SUPER_ADMIN et MANAGER côté route et backend. Les tests API et le scénario E2E de recherche sont passés ; la gestion/modification des comptes client n'est pas implémentée.
- Les routes publiques chauffeur/transfert, contact, FAQ et récupération de mot de passe sont des placeholders. Les notifications, services, partenaires et rapports restent à développer ; il n'existe pas encore de modèle/API métier pour ces domaines. Les paiements admin ne sont pas un fournisseur de paiement et restent volontairement désactivés.
- Aucun fournisseur de paiement réel n'est configuré ; les paiements du backend utilisent un traitement simulé. Aucun paiement réel n'a été exécuté.
- Le parcours E2E couvre la prise en charge, la restitution (kilométrage, carburant, dommages et frais) et la consultation par le client de la facture complémentaire créée pour les frais positifs. Il ne valide pas de paiement réel.

## PostgreSQL et migrations

### Environnement isolé (exécuté)

- Docker n'est pas disponible. PostgreSQL 18 est installé localement ; ses binaires (`initdb`, `postgres`, `psql`) ont servi, en lecture seule, à créer un cluster **distinct** : répertoire de données dédié sous `%LOCALAPPDATA%\EliteTunisiaTools\pg-test-cluster\data`, écoute `127.0.0.1:15432` uniquement, authentification SCRAM, mot de passe aléatoire hors dépôt. Version confirmée par le serveur : **PostgreSQL 18.3**, répertoire de données et base `et_test_db` vérifiés avant toute migration (0 table).
- Le port 5432 est écouté (toutes interfaces) par un processus `postgres` non identifié : il n'a pas été contacté. Aucune base existante n'a été lue, migrée ou modifiée. Seules les bases jetables `et_test_db` et `et_diff_db` (comparaison de schéma, supprimée) ont été créées/supprimées, dans le cluster isolé.

### Commandes et résultats

- `.\mvnw.cmd -Ppostgres-test verify` (JDK 21, variables `ELITE_POSTGRES_TEST`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` définies pour la session uniquement, base recréée vide avant chaque exécution) :
  - Flyway : **16 migrations appliquées, 0 erreur SQL**, version finale v16.
  - Surefire : **88 tests, 0 échec, 0 erreur, 0 ignoré** (les 10 tests ignorés en H2 s'exécutent ici).
  - Failsafe : **3 ITs, 0 échec** (`DriverAssignmentPostgresIT`, `PostgresInvoiceConcurrencyIT`, `PostgresIdempotencyIT`). BUILD SUCCESS.
- Démarrage de l'application (profil `dev`, Flyway actif, `ddl-auto=validate`, secret JWT aléatoire de session) : `/actuator/health` UP. Montée de version V15→V16 sur une base déjà en V15 : réussie. Redémarrage sur la même base : « Schema "public" is up to date. No migration necessary. » puis UP.
- Schéma résultant : 16 entrées `flyway_schema_history` toutes en succès ; séquences `booking_reference_seq`, `invoice_seq`, `quote_reference_seq` ; contrainte d'exclusion `bookings_no_blocking_overlap` (extension `btree_gist`) ; 88 index ; `legacy_invoices` et `legacy_payments` ; permissions `customers:read`, `drivers:manage`, `invoices:read`, `payments:create` attribuées aux rôles attendus.

### Défauts révélés par PostgreSQL et corrigés

- **Application non démarrable sur PostgreSQL** : l'entité `TransferEntity` n'avait aucune migration (`missing table [transfers]` avec `validate`). Corrigé par la migration `V16__transfers_and_image_url_length.sql`, qui élargit aussi `vehicle_images.url` à 1000 caractères comme l'entité. Le comparatif complet entités/schéma Flyway ne montre plus d'autre table ou colonne manquante ; trois écarts de nullabilité subsistent (`invoice_lines.metadata`, `payments.refunded_amount`, `quotes.vehicle_id`).
- **Harnais de test `postgres-test`** : les propriétés système PostgreSQL fuyaient entre classes de test (40 erreurs de chargement de contexte). `PostgresTestHelper` fixe désormais le dialecte PostgreSQL (le profil `test` impose `H2Dialect`) et un secret JWT aléatoire par exécution.
- `BookingEngineTests` supposait un compte `superadmin@example.test` créé seulement par le seeder H2 ; la fixture le crée désormais si absent.
- `PostgresIdempotencyIT` utilisait `Instant.now()` (précision 100 ns sous Windows) alors que la règle métier de devis refuse plus de six décimales ; l'instant est tronqué à la microseconde dans le test. La règle métier n'a pas été modifiée.
- Régression H2 après ces changements : `.\mvnw.cmd -DskipITs test` = 88 tests, 0 échec, 0 erreur, 10 ignorés.

### Risques restants

- **Base contenant des données : non validée.** V2 (colonnes `NOT NULL` sans défaut sur `employees`), V3 (`price_per_week`, `currency`), V4 (exclusion de chevauchement), V6/V7 (renommage vers `legacy_*` sans copie de données), permissions insérées sans `ON CONFLICT` : voir `entity-migration-mapping.md`. Ne pas appliquer ces migrations à une base existante sans sauvegarde, inspection de `flyway_schema_history` et plan de reprise des données.
- Aucune version de PostgreSQL autre que 18.3 n'a été testée.
- Le seeder administrateur n'existe qu'avec le profil `test` : une recette manuelle sur PostgreSQL exige de créer un premier compte staff.
- Les tests E2E Playwright n'ont pas été rejoués sur PostgreSQL (ils utilisent le seeder H2) ; seuls les tests backend d'intégration ont été validés sur PostgreSQL.
- `docs/start-local.md` contient la procédure reproductible sans Docker (exécutée) et la procédure Docker (non exécutée ici).

## Conclusion

Les tests Angular (33), le build Angular, les 18 scénarios E2E et les tests backend H2 (88 tests, 10 ignorés) passent selon les comptes ci-dessus. Sur PostgreSQL 18.3 isolé : migrations V1→V16 sur base vide, démarrage avec `validate`, redémarrage idempotent, 88 tests surefire et 3 ITs failsafe sont réussis. Restent non démontrés : la migration d'une base préexistante avec données, les autres versions de PostgreSQL et les E2E sur PostgreSQL.
