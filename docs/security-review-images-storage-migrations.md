# Revue complémentaire : images, stockage, migrations désactivées

Constats établis sur le code réel. Ce document ne constitue pas une certification.

## 1. URLs d'images (véhicules, marques)

| Question | Constat | Preuve |
|---|---|---|
| Stockées seulement comme chaînes ? | Oui (`varchar(1000)`). | `VehicleImageEntity.url`, `BrandEntity.logoUrl` |
| Utilisées côté serveur pour récupérer une ressource ? | Non : aucun client HTTP (`RestClient`, `RestTemplate`, `WebClient`, `HttpClient`, `new URL`) dans le backend. Pas de risque SSRF via ces champs. | recherche dans `backend/src/main/java` |
| Affichées dans Angular ? | Oui, uniquement via `<img [src]>` (catalogue, détail, administration). Aucun `innerHTML` ni `bypassSecurityTrust*` ne les utilise. | `vehicle-*.component.ts`, `admin-vehicles.component.ts` |
| Validation actuelle | `^(https?://[^\s]+|/api/files/[A-Za-z0-9._-]+)$`, 1000 caractères max (`VehicleImageRequest`, `BrandUpsertRequest`). Les schémas `javascript:`, `data:`, `file:`, `ftp:` et les URLs relatives au protocole (`//hote`) sont refusés. | `VehicleImageUrlValidationTest` (12 cas) |

Risques résiduels, sans restriction de domaine imposée :

- Une URL externe permet à son hébergeur de voir l'adresse IP et le navigateur des visiteurs (suivi). Faible.
- Aucune politique CSP n'est définie côté backend. Si le frontend est servi par un autre hôte, une CSP `img-src` est à décider avec l'infrastructure ; elle doit alors autoriser les hôtes d'images réellement utilisés.
- Une URL externe peut devenir indisponible : le frontend n'a pas de repli vérifié.

Aucune modification de règle métier n'est nécessaire. Un test de non-régression de la validation a été ajouté.

## 2. Stockage des fichiers

- Abstraction : l'interface `FileStorageService` (`storeImage`, `load`, `deleteUrl`) est la seule dépendance de `ImageUploadController`, `PublicFileController` et `VehicleService`. `LocalFileStorageService` est l'unique implémentation (disque, `app.storage.local-path`, défaut `~/.elite-tunisia/uploads`).
- Remplacement futur : possible en fournissant une autre implémentation de l'interface. Les URLs stockées en base ont la forme `/api/files/<uuid>.<ext>` ; un stockage externe devra soit servir cette route (proxy), soit migrer les URLs. Aucune migration vers un service externe n'a été faite (décision explicite requise).
- Limites en multi-instance :
  - Chaque instance écrit sur son propre disque : un fichier téléversé sur l'instance A est introuvable (404) sur l'instance B sauf volume partagé.
  - Sans volume persistant, les fichiers disparaissent à chaque redéploiement de conteneur.
  - La sauvegarde de la base (`docs/backup-restore.md`) ne contient pas les fichiers : le répertoire de stockage doit être sauvegardé séparément.
  - La suppression d'une image (`deleteUrl`) n'agit que sur l'instance qui reçoit la requête.
- Prérequis pour un déploiement multi-instance : volume partagé (accès concurrent à valider) ou implémentation de stockage objet.

## 3. Migrations `_DISABLED_*`

Fichiers : `backend/src/main/resources/db/migration/_DISABLED_V4__add_driver_fields.sql`, `_DISABLED_V5__payments_idempotency_unique.sql`, `_DISABLED_V6__invoices_sequence_and_constraints.sql` (suivis par Git).

Constats :

- Ils ne sont jamais exécutés : `backend/pom.xml` exclut `db/migration/**` de `src/main/resources` et copie `database/migrations` vers `db/migration`. Le préfixe `_DISABLED_` ne correspond pas au préfixe `V` attendu par Flyway, qui ne les considère donc pas comme des migrations. Seule référence textuelle : `docs/audit/entity-migration-mapping.md`.
- Leurs en-têtes les déclarent redondants avec la séquence centrale. Comparaison avec `database/migrations` :

| Fichier | Contenu | Équivalent dans l'historique réel | Risque en cas de réactivation |
|---|---|---|---|
| `_DISABLED_V4` | ajoute `license_number`, `license_expiry`, `certified`, `notes` à `drivers` | `license_number` existe dès V1 (`varchar(80) NOT NULL UNIQUE`) ; `license_expiry`, `certified`, `notes` sont ajoutés par V12 de façon idempotente | échec immédiat (colonne déjà existante), et conflit de numéro de version avec V4 |
| `_DISABLED_V5` | contrainte unique sur `payments.idempotency_key` | V6 crée `payments_idempotency_idx` (index unique) | doublon de contrainte, version V5 déjà utilisée |
| `_DISABLED_V6` | séquence `invoice_seq`, unicité de `invoices.reference`, table `invoice_payments` | V7 : `invoice_seq`, `reference ... UNIQUE`, `invoice_payments` | version V6 déjà utilisée ; `invoice_payments` sans clés étrangères (V7 en définit) |

- Risques de réactivation : numéros de version déjà occupés (V4 à V6 existent dans `database/migrations`), donc échec de validation Flyway sur toute base existante.
- Risques de suppression : aucun effet sur le comportement ni sur `flyway_schema_history`. Perte de la trace d'audit uniquement (le contenu reste dans l'historique Git).

Recommandation (aucune action automatique effectuée) : ne pas réactiver ; après validation explicite, soit les supprimer (l'historique Git conserve le contenu), soit les déplacer hors de `backend/src/main/resources` vers un dossier d'archive (par exemple `docs/audit/`). La comparaison ci-dessus reste à vérifier sur un schéma de production réel si celui-ci diffère des migrations.
