# Architecture

## Découpage

Le MVP est un monorepo à deux applications et une base PostgreSQL. Le backend est un monolithe modulaire : chaque package métier est une frontière interne, pas un service déployé séparément.

- `frontend/src/app/core/` : état de session, guards, interceptors, services HTTP et modèles partagés.
- `frontend/src/app/shared/` : composants et conventions UI transverses.
- `frontend/src/app/features/` : domaines UI chargés à la demande.
- `frontend/src/app/layout/` : layouts public, client et administration.
- `backend/src/main/java/tn/elitetunisia/auth/` : endpoints de session, DTO, persistance des comptes et tokens.
- `backend/src/main/java/tn/elitetunisia/common/` : sécurité HTTP, OpenAPI, santé et erreurs communes.
- Les packages `vehicle`, `booking`, `quote`, etc. ne contiennent que leur espace de nommage initial.

## Flux actuellement présent

Le client Angular appelle l'API REST Spring. Spring Security valide les JWT pour les chemins protégés. JPA persiste les comptes et les hashes de refresh token dans PostgreSQL. Flyway applique le SQL versionné depuis `database/migrations/` au packaging Maven.

Les routes UI métier sont des états de préparation, pas des écrans CRUD ni des données de démonstration. Aucun microservice, cache Redis, stockage d'image ou fournisseur de paiement n'est actif.

## Évolution

Les fonctionnalités métier doivent suivre Controller → Service → Repository, utiliser des DTO validés et exposer des erreurs Problem Details. Les contrôleurs ne doivent pas contenir de règles métier ni accéder directement aux repositories.