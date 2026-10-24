# Tests

## Backend

`cd backend; .\mvnw.cmd test` utilise le profil test H2. La suite comprend les tests auth/catalogue/stockage ainsi que `BookingEngineTests` (dates, prix, disponibilité, isolation, transitions, audit et concurrence). Les deux tests de concurrence sont désactivés sur H2 : ils ne constituent pas une preuve tant qu'ils ne sont pas exécutés sur PostgreSQL.

Pour exécuter la même suite sur une base PostgreSQL de test vierge dédiée, fournir ces paramètres Maven (ne jamais utiliser une base permanente contenant des données) :

```powershell
.\mvnw.cmd test '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55442/elite_final_test' '-Dspring.datasource.username=elite_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver' '-Dspring.flyway.enabled=true' '-Dspring.jpa.hibernate.ddl-auto=validate' '-Dspring.jpa.defer-datasource-initialization=false' '-Dspring.sql.init.mode=never'
```

Pour activer les deux tests PostgreSQL de concurrence, définir `$env:ELITE_POSTGRES_TEST = 'true'` avant la commande ci-dessus puis supprimer cette variable après. `BookingEngineTests` synchronise deux transactions indépendantes et vérifie une confirmation réussie/une refusée, conformément au choix PENDING non bloquant. Un second test contourne le service par deux écritures SQL pour prouver l'exclusion en base.

La suite de l'étape 5 a été exécutée sur PostgreSQL 18.3 temporaire UTF-8, avec V1–V4 et Hibernate validate. Les décomptes finaux figurent dans [step-5-report.md](step-5-report.md). Docker/Testcontainers n'est pas configuré; la base permanente n'est pas testée.

## Frontend

`cd frontend; npm test -- --watch=false` utilise Angular/Vitest : auth, routes, catalogue/période et réservation (validation, disponibilité, récapitulatif serveur, création, erreur, liste, détail, annulation et accès refusé). `npm run build` vérifie templates et types stricts. Node 22.22.3+ ou 24.15+ est requis; le Node 18 global de cette machine n'est pas compatible.

`npm run e2e` inclut les quatre parcours réservation PostgreSQL : client, confirmation agent/conflit/catalogue filtré, isolation et annulation libérant la période. Aucun endpoint réservation n'est intercepté. Les cinq scénarios auth/catalogue antérieurs sont également conservés; deux seulement contrôlent des réponses publiques d'état.

Démarrer Angular sur 4200 (`ng serve --host 127.0.0.1 --prebundle=false` évite les rechargements de découverte de dépendances pendant une recette) et le backend sur 8080. Pour une recette PostgreSQL, utiliser le profil test pour les seuls comptes de test, puis passer les propriétés datasource/flyway/ddl-auto ci-dessus via `-Dspring-boot.run.arguments=...` et `-Dspring-boot.run.useTestClasspath=true`. Ne jamais activer le seeder de test sur une base permanente. Sur cette machine, `PLAYWRIGHT_EXECUTABLE_PATH` pointe vers Microsoft Edge, le téléchargement Chromium étant bloqué par les certificats locaux.

Des captures ont vérifié le catalogue à 1366 et 375 pixels sans débordement horizontal. Une recette accessibilité complète et une recette sur base permanente restent à réaliser.

Pour l'étape 5, le formulaire de réservation et le détail agence ont également été capturés aux deux formats. La recette finale : 23/23 tests backend sur PostgreSQL (concurrence 2/2 incluse), 19/19 Vitest et 9/9 E2E. Le JAR est produit par Maven verify; le build Angular reste strict.

## Étape 6

QuoteEngineTests ajoute tarification 24/25/48/49 heures, chauffeur global/différence, service configuré, remise, taxe configurable/arrondi, snapshots brouillon/envoi/nouvelle version, validité, messages de modification, immutabilité, isolation et confirmation finale. Un test PostgreSQL supplémentaire synchronise deux confirmations de bookings munis de devis acceptés. ELITE_POSTGRES_TEST=true active les trois tests de concurrence (deux de l'étape 5 et un devis); H2 seul n'est pas une preuve.

Les tests frontend devis couvrent chargement/vide/erreur, listes paginées, détail/montant serveur, création/édition/envoi, acceptation/refus/modification et expiration. Les six nouveaux E2E n'interceptent aucune API devis : création client puis agence, acceptation/confirmation, refus, versioning, isolation et disponibilité perdue. Décomptes et preuves finales : [step-6-report.md](step-6-report.md).