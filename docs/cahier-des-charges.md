# Cahier des charges — Elite Tunisia

**Version :** 1.4  
**Date de dernière validation :** 5 octobre 2026  
**Statut :** cahier des charges cible avec état d'implémentation vérifié

> Les sections 1 à 12 décrivent les exigences du produit cible. La section suivante précise ce qui est réellement livré dans le dépôt au 2 octobre 2026. Une exigence cible ne doit pas être considérée comme une fonction terminée sans preuve dans le code et les tests.

## État de réalisation au 5 octobre 2026

### Résumé

L'étape 3 livre auth/utilisateurs; l'étape 4 livre catalogue et flotte; l'étape 5 livre disponibilités/réservations. L'étape 6 ajoute les devis commerciaux versionnés, la tarification partagée/snapshots, les réponses client et la validation finale agence sous protection PostgreSQL.

Le produit **n'est pas prêt pour la production**. Les devis commerciaux versionnés sont livrés à l'étape 6. Paiements, factures légales, remboursements, livraison de notifications et chauffeurs opérationnels ne sont pas développés. Récupération de mot de passe, vérification d'e-mail et recette de la base permanente restent absentes.

### Parcours client réellement disponible

1. Depuis `/auth/register`, le client saisit prénom, nom, e-mail, téléphone, mot de passe et confirmation. Le formulaire vérifie les champs obligatoires, le format du téléphone, l'e-mail, la robustesse du mot de passe et la concordance de confirmation.
2. Le navigateur demande d'abord un jeton CSRF au serveur, puis envoie les données à `POST /api/auth/register`. Le backend normalise l'e-mail, vérifie son unicité, hash le mot de passe avec BCrypt, ignore tout rôle client fourni et attribue lui-même `CUSTOMER`.
3. Le backend crée dans une même opération l'utilisateur, son profil `customers`, le rôle et un refresh token opaque. Seul le hash SHA-256 du refresh token est enregistré en base. Le token brut est renvoyé dans un cookie HttpOnly; il n'est pas exposé au JavaScript.
4. L'API retourne un access token JWT de courte durée (15 minutes). Angular le garde uniquement en mémoire, appelle `GET /api/auth/me` pour hydrater le profil et ne le place pas dans `localStorage`.
5. Le client est redirigé vers `/customer/dashboard`. Cette page montre son nom et son statut et donne accès au profil. `/customer/profile` lit et met à jour les données du principal authentifié; l'identifiant du client n'est jamais choisi par le navigateur.
6. Au logout, Angular appelle `POST /api/auth/logout`; l'API révoque le refresh token, expire le cookie et Angular efface l'access token en mémoire.
7. Au renouvellement, `POST /api/auth/refresh` vérifie le hash, l'expiration, la révocation et l'état du compte, révoque l'ancien token et émet le suivant. Les tokens sont verrouillés pendant l'opération; la réutilisation d'un token ancien révoque les refresh tokens encore actifs de sa famille.

### Parcours super-administrateur réellement disponible

- Un super-administrateur authentifié ouvre `/auth/admin-login`, puis `/admin`.
- `/admin/users` charge les comptes via `GET /api/admin/users` et les rôles depuis `GET /api/admin/roles`.
- L'interface permet de créer un employé, de modifier son profil et son rôle, puis de désactiver ou réactiver son compte.
- L'API exige les permissions `users:read` pour lire et `users:manage` pour écrire. La création d'employés est refusée si le rôle demandé n'est pas un rôle interne autorisé.
- À la désactivation, les refresh tokens actifs sont révoqués. Le filtre JWT recharge les rôles et le statut en base pour chaque requête; un compte désactivé perd donc immédiatement l'accès API, même avec un ancien access token.
- Un client qui tente `/admin` est redirigé vers `/forbidden`; côté serveur, un client reçoit également `403` sur les opérations d'administration.

Le dashboard d'indicateurs `/admin` reste un placeholder. Les opérations de réservation de l'étape 5 sont maintenant reliées au backend; les autres domaines exclus restent à implémenter.

### Parcours catalogue réellement disponible (étape 4)

- Le public consulte `/vehicles`, `/vehicles/{id}`, `/brands` et `/categories`; le contenu est chargé depuis PostgreSQL via l'API, avec états chargement, liste vide et erreur.
- Le catalogue combine recherche texte, marque, catégorie, fourchette de prix/jour, capacité, transmission et disponibilité d'un chauffeur. Le tri serveur est limité aux options documentées et la pagination est bornée; les filtres partagés sont portés par l'URL.
- Une fiche véhicule montre la marque, catégorie, caractéristiques, équipements, images et tarifs jour/semaine/chauffeur. Elle ne promet pas de disponibilité à une date.
- Les utilisateurs internes ayant les permissions correspondantes gèrent marques/catégories (création, édition, activation/publication, suppression contrôlée) et la flotte (CRUD, statut, tarifs, équipements et images).
- Les images sont téléversées comme fichiers JPEG/PNG/WebP vers le stockage local configuré, taille/signature contrôlées, noms UUID et URL servie par `/api/files/{key}`. Les binaires ne sont pas mis en base; les fichiers remplacés sont nettoyés après commit.
- L'API applique `vehicles:read/manage`, `brands:read/manage`, `categories:read/manage`; les rôles `AGENT`, `CONCIERGE` et `ACCOUNTANT` ont une lecture catalogue, tandis que `MANAGER` et `SUPER_ADMIN` peuvent gérer ces données.
- Les états véhicule publics se limitent à `AVAILABLE` et aux marques/catégories actives. `RESERVED`, `RENTED`, `MAINTENANCE` et `UNAVAILABLE` sont des statuts de flotte, sans moteur de réservation ou calendrier.

### Étape 5 : Disponibilité et réservations

- Formulaire depuis la fiche véhicule : dates/heures, lieux, vérification réelle et récapitulatif calculé par le serveur; envoi authentifié avec référence générée et statut PENDING.
- Catalogue filtrable sur une période, en conservant les filtres de l'étape 4.
- Espaces client liste/détail/annulation, limités au propriétaire et sans notes internes.
- Back-office liste/recherche/filtres/détail/historique, confirmation/refus/annulation/démarrage/fin et modification limitée des lieux/notes internes.
- Décisions explicitement validées le 5 octobre : PENDING ne bloque pas et n'expire pas; chaque tranche de 24 heures commencée est facturée. Deux demandes peuvent coexister, mais une seule peut être confirmée sur une période chevauchante.
- PostgreSQL impose l'exclusion des intervalles bloquants CONFIRMED/IN_PROGRESS; les verrous applicatifs et deux tests réels de concurrence complètent cette protection.
- UTC/Instant/TIMESTAMPTZ, avec affichage dans BOOKING_TIME_ZONE (Africa/Tunis par défaut). Pas de politique de remboursement, taxes commerciales, tarifs d'options ou chauffeur inventés.

Contrats et preuves : [booking.md](booking.md), [step-5-report.md](step-5-report.md). Les anciens comptes de tests ci-dessous décrivent la recette étape 4, pas le décompte final étape 5.

### Étape 6 : État explicite

| Fonction | État |
| --- | --- |
| Devis/lignes/versioning/historique et références serveur | IMPLEMENTED |
| Tarification partagée Booking/Quote et snapshots | IMPLEMENTED |
| Validité explicite, expiration auditée | IMPLEMENTED |
| Réponses client acceptation/refus/demande de modification | IMPLEMENTED |
| Gestion agence brouillon/envoi/annulation/nouvelle version | IMPLEMENTED |
| Confirmation agence après devis accepté, recontrôle PostgreSQL | IMPLEMENTED |
| RBAC/isolation/audit et interfaces reliées aux APIs | IMPLEMENTED |
| Calcul fiscal légal, taux de taxes validés | NOT IMPLEMENTED (montant configuré zéro par défaut) |
| Tarifs inventés de livraison/transfert/options | NOT IMPLEMENTED (services uniquement configurés en backend) |
| PDF commercial, email/SMS/push | NOT IMPLEMENTED (contrats futurs seulement) |
| Paiement, facture légale, remboursement, banque/comptabilité, chauffeur opérationnel | NOT IMPLEMENTED |

Décisions validées : tarif chauffeur global, ligne différence avec location; référence DEV unique par version. L'acceptation ne confirme ni ne paie automatiquement. Règles et recette : [quotes.md](quotes.md), [step-6-report.md](step-6-report.md).

### API et contrats livrés

| Méthode et chemin | Fonction réalisée |
| --- | --- |
| `GET /api/auth/csrf` | Initialise le cookie et retourne le jeton CSRF des mutations navigateur. |
| `POST /api/auth/register` | Inscription client validée, rôle `CUSTOMER` attribué par le backend, profil client créé. |
| `POST /api/auth/login` | Vérifie identifiants et état actif; renvoie JWT court et pose le cookie refresh. |
| `POST /api/auth/refresh` | Valide et fait tourner le refresh token; rejette expiré, révoqué, inconnu ou rejoué. |
| `POST /api/auth/logout` | Révoque le refresh token et efface le cookie. |
| `GET /api/auth/me` | Renvoie uniquement id, nom, e-mail, téléphone, rôles et statut; jamais mot de passe ou token. |
| `GET /api/customer/profile` | Lit le profil client du principal authentifié. |
| `PUT /api/customer/profile` | Met à jour uniquement le profil client du principal authentifié. |
| `GET /api/admin/users` et `GET /api/admin/users/{id}` | Liste paginée et détail utilisateur, réservés à `users:read`. |
| `POST /api/admin/users` | Crée un employé avec un mot de passe BCrypt et un rôle interne, réservé à `users:manage`. |
| `PUT /api/admin/users/{id}` | Met à jour profil employé et rôle, réservé à `users:manage`. |
| `PATCH /api/admin/users/{id}/status` | Désactive/réactive un compte et révoque ses refresh tokens lors de la désactivation. |
| `GET /api/admin/roles` | Fournit les rôles et permissions depuis la base pour le formulaire admin. |
| `GET /api/vehicles`, `GET /api/vehicles/{id}` | Catalogue public avec filtres whitelistés, tri et pagination; uniquement les véhicules publiés au statut `AVAILABLE`. |
| `GET /api/brands`, `GET /api/categories` | Listes publiques des marques/catégories actives. |
| `GET /api/admin/brands`, `GET /api/admin/categories`; POST/PUT/DELETE sur les collections/items | Gestion permissionnée des référentiels, slugs uniques et suppression interdite si liés à des véhicules. |
| `GET/POST/PUT/DELETE /api/admin/vehicles`, `PATCH /api/admin/vehicles/{id}/status` | Recherche flotte admin et CRUD véhicule, caractéristiques, tarifs, images et statuts. |
| `POST/DELETE /api/admin/uploads/images`, `GET /api/files/{key}` | Téléversement protégé, suppression d'upload non associé et lecture publique des fichiers image locaux. |

Les réponses sont des DTO et n'incluent aucun hash, refresh token ou mot de passe. Les erreurs de validation, authentification, accès interdit, doublon et compte désactivé ont des statuts/messages non techniques.

### Données et rôles implémentés

La migration Flyway V2 complète V1 avec `user_roles`, les profils client/employé, les permissions, les liens rôle-permissions, le regroupement des refresh tokens par famille et les informations de résultat/IP de l'audit. Les rôles persistés sont : `SUPER_ADMIN`, `MANAGER`, `AGENT`, `CONCIERGE`, `ACCOUNTANT` et `CUSTOMER`.

Permissions actuellement utilisées par les fonctions livrées : `admin:access`, `users:read/manage`, `vehicles:read/manage`, `brands:read/manage`, `categories:read/manage`, `profile:read:self` et `profile:update:self`. Le super-administrateur reçoit toutes les permissions du catalogue; les droits d'écriture catalogue sont accordés à `MANAGER` et `SUPER_ADMIN`. Les rôles et droits sont contrôlés côté backend, pas uniquement dans Angular.

### Sécurité réalisée

- Mots de passe BCrypt; stratégie de mot de passe d'inscription : au moins 12 caractères, majuscule, minuscule, chiffre et symbole.
- JWT signé HMAC, durée de vie de 15 minutes. Il identifie le sujet; les rôles/droits ne reposent pas sur un claim stale et sont rechargés depuis la base.
- Refresh token aléatoire, hashé SHA-256 en base, rotatif, révocable, expirant après la durée configurée; cookie `HttpOnly`, `SameSite=Strict`, path limité à `/api/auth`, `Secure` imposé dans le profil production.
- Protection CSRF cookie + header pour les mutations navigateur. Le frontend renouvelle le jeton CSRF avant les mutations; les API protégées rejouent une fois après un 401 en tentant d'abord le refresh.
- Angular conserve l'access token en mémoire seulement. Le guard et le `RoleGuard` améliorent la navigation; Spring Security vérifie aussi identité, rôles et permissions côté serveur.
- Les échecs de connexion, inscriptions, connexions, déconnexions, créations/modifications de comptes, changements de rôle et désactivations sont inscrits dans `audit_logs` sans enregistrer mot de passe ou token.
- Le CORS par défaut est limité à `localhost:4200` et `127.0.0.1:4200`; le profil déployé doit fournir ses origines exactes.
- Les uploads vérifient le format déclaré et la signature de fichier, plafonnent la taille et n'acceptent pas de chemin fourni par l'utilisateur; les extensions SVG et autres formats ne sont pas admis.

### Interfaces et tests réalisés

- Formulaires responsive de connexion client/agence et d'inscription; erreurs de validation, mauvais identifiants, doublon et compte désactivé affichés avec des textes compréhensibles.
- Dashboard client et profil client alimentés par le serveur; formulaire du profil éditable et lié à l'utilisateur courant.
- Interface admin des utilisateurs avec liste paginée, formulaire création/édition, rôles tirés de l'API, statuts et actions d'activation.
- Tests backend : 15/15 passés sur PostgreSQL 18 temporaire, couvrant auth/RBAC, CRUD catalogue, filtres/tri/pagination, statuts, validations, suppressions liées, upload et mises à jour d'images.
- Tests frontend : 10/10 Vitest passés, dont filtres, pagination et états vide/erreur.
- Playwright : 5/5 passés; catalogue/détail et états vide/erreur (réponses contrôlées), création réelle marque/catégorie/véhicule puis fiche publique, inscription/profil et opérations utilisateurs. Le refus HTTP 403 client est vérifié aussi contre le serveur réel H2.
- PostgreSQL 18 temporaire vierge : Flyway valide/applique V1–V3, Hibernate `ddl-auto=validate` démarre et les routes health/catalogue répondent. Cette validation ne concerne pas la base permanente.

### Ce qui n'est pas réalisé

- Récupération/réinitialisation de mot de passe, validation d'e-mail et verrouillage après tentatives répétées.
- Paiements, factures légales, remboursements, comptabilité complète, notifications et gestion opérationnelle des chauffeurs.
- Transferts opérationnels, CRUD complet des services, partenaires et PDF commercial.
- Le dashboard admin métier, les pages client réservations/devis/factures/notifications sont encore des placeholders.
- Limitation de débit, monitoring/alertes, sauvegardes, déploiement, HTTPS et procédure de reprise.
- Conditions légales, règles de remboursement, taxes et politiques commerciales à valider avec l'agence.

### Blocage d'exploitation et préparation production

Flyway et Hibernate ont été réellement validés contre un cluster PostgreSQL temporaire local, mais aucune connexion à la base PostgreSQL permanente n'a été configurée : ses credentials ne sont pas fournis et Docker n'est pas installé. Ne pas interpréter la validation temporaire comme un déploiement ou une recette sur les données réelles. Le projet est **NOT READY FOR PRODUCTION**.

Pour les bilans, consulter [docs/step-3-report.md](step-3-report.md) et [docs/step-4-report.md](step-4-report.md). Les endpoints livrés sont détaillés dans [docs/api.md](api.md), et les choix/risques de sécurité dans [docs/security.md](security.md).

---

Les chapitres suivants restent la spécification fonctionnelle et technique cible du MVP.

## 1. Présentation

**Elite Tunisia** est une plateforme de location de voitures premium et de services de mobilité haut de gamme en Tunisie. Elle met en relation les clients et une agence qui gère les véhicules, demandes, réservations, chauffeurs, devis et documents.

La plateforme comporte trois espaces :

- **Site public :** présenter l'offre, rechercher les véhicules et envoyer des demandes.
- **Espace client :** suivre son profil, ses demandes, ses réservations, devis, factures et notifications.
- **Back-office agence :** traiter les opérations, gérer les ressources et consulter les indicateurs.

Le produit sera livré par étapes. Le premier MVP ne doit pas prétendre gérer un paiement en ligne tant qu'un prestataire compatible avec les besoins de l'agence n'a pas été sélectionné.

## 2. Objectifs

- Présenter une offre premium cohérente sur ordinateur, tablette et téléphone.
- Permettre à un client de trouver un véhicule et de soumettre une demande traçable.
- Donner à l'agence un outil unique de traitement des demandes et de suivi client.
- Empêcher les conflits de disponibilité et calculer les prix côté serveur.
- Construire une base maintenable, sécurisée et extensible sans microservices pour le MVP.

Les indicateurs métier (taux de conversion, délai de réponse, occupation de flotte, revenus) seront définis avec l'agence avant la mise en production.

## 3. Utilisateurs et habilitations

| Profil | Besoin principal | Accès cible |
| --- | --- | --- |
| Visiteur | Parcourir l'offre et contacter l'agence | Pages publiques et demandes publiques limitées |
| Client | Gérer ses demandes et consulter ses documents | Uniquement ses propres données |
| Agent | Traiter les demandes et échanger avec les clients | Clients et réservations autorisés |
| Concierge | Organiser les services et demandes VIP | Demandes de services autorisées |
| Comptable | Suivre factures et paiements enregistrés | Données financières autorisées |
| Manager | Piloter l'activité | Flotte, clients et réservations selon matrice d'habilitation |
| Super-administrateur | Administrer la plateforme | Administration complète et gestion des habilitations |

Rôles techniques prévus : `SUPER_ADMIN`, `MANAGER`, `AGENT`, `CONCIERGE`, `ACCOUNTANT`, `CUSTOMER`. La matrice fine des permissions doit être validée par l'agence. Les contrôles Angular ne sont jamais une protection suffisante : toute autorisation est également vérifiée par l'API.

## 4. Périmètre fonctionnel cible

### 4.1 Site public

- Accueil de marque avec présentation de la flotte, des services, du chauffeur et des transferts.
- Catalogue de véhicules alimenté par l'API : recherche, filtres, tri, pagination et disponibilité.
- Fiche véhicule avec caractéristiques, images, équipements, conditions, prix transmis par l'API et appels à l'action.
- Pages marques, catégories, services, à propos, FAQ et contact.
- Formulaires de demande de location, de devis, de chauffeur et de transfert aéroport.

### 4.2 Réservation et disponibilité

Le parcours cible est : véhicule → service → dates → lieux → options → récapitulatif serveur → coordonnées client → demande/confirmation.

Le système doit valider les dates, les champs obligatoires et la disponibilité au moment de l'enregistrement. Il doit refuser les chevauchements selon les statuts qui bloquent la flotte. Le choix des statuts bloquants, l'expiration d'une éventuelle pré-réservation et la confirmation automatique ou manuelle sont à décider avec l'agence.

### 4.3 Devis

- Un client soumet un besoin de véhicule, chauffeur, période, transfert ou options, avec commentaire facultatif.
- L'agence prépare des lignes, remises, taxes si applicables, total et date de validité.
- Le client consulte, accepte, refuse ou demande une modification.
- Toute acceptation doit déclencher une transition métier traçable; la conversion automatique en réservation est à confirmer.

### 4.4 Espace client

- Tableau de bord avec demandes en cours et prochaines échéances.
- Historique des réservations et statuts : `PENDING`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `REJECTED`.
- Consultation des devis, factures, profil et notifications.
- Isolation stricte des données : un client ne consulte ni ne modifie les ressources d'un autre client.

### 4.5 Back-office

- Gestion de la flotte, marques, catégories, images et statuts des véhicules.
- Gestion des clients et accès à leur historique.
- Traitement des demandes, réservations, devis, affectations de véhicules et chauffeurs.
- Gestion des chauffeurs et documents, services, partenaires, factures et paiements enregistrés.
- Tableau de bord, recherche, filtres, pagination et vues adaptées aux opérations quotidiennes.
- Journalisation des actions sensibles et restriction des fonctions par rôle.

Le périmètre MVP peut être livré progressivement; l'administration complète n'est pas un prérequis pour la première mise en ligne publique si elle empêche la livraison des parcours prioritaires.

## 5. Règles métier

1. Les véhicules, catégories, marques, services et prix affichés proviennent du backend; aucune donnée statique ne doit simuler une disponibilité réelle.
2. Le backend recalcule chaque devis et réservation à partir des tarifs et options enregistrés. Un montant envoyé par le navigateur n'est jamais une source de vérité.
3. Les montants sont stockés en `NUMERIC`, avec devise explicite. La devise d'exploitation et les règles d'arrondi/taxation doivent être confirmées par l'agence et son comptable.
4. Les bornes de location sont cohérentes (`fin > début`) et leur fuseau de saisie/affichage doit être précisé. Stocker les instants en UTC et définir le fuseau métier avant production.
5. La prévention des doubles réservations doit fonctionner sous concurrence, pas uniquement au moyen d'une vérification préalable côté interface.
6. Les annulations, remboursements, dépôts de garantie, frais de livraison, réductions, taxes, conditions d'âge/permis et durée minimale sont des paramètres métier à valider; ils ne sont pas inventés dans cette version.
7. Une facture ne peut être émise qu'à la suite d'une règle comptable validée. Le PDF et la numérotation légale sont à définir avec l'agence.
8. Un paiement en ligne reste désactivé jusqu'au choix d'un fournisseur. Aucune donnée de carte bancaire n'est enregistrée par l'application.

## 6. Exigences d'interface

- Identité premium sobre, typographie lisible, images pertinentes et espaces de respiration.
- Navigation clavier, états de focus visibles, libellés associés aux champs, erreurs compréhensibles et textes alternatifs.
- Formulaires réactifs avec validations côté navigateur et revalidation serveur.
- États de chargement, succès, liste vide et erreur prévus sur chaque vue dynamique.
- Tableaux utilisables sur petits écrans par défilement contrôlé ou présentation adaptée.
- Vérification aux formats de référence : 1920×1080, 1366×768, 768 px et 375 px.
- Contenu public indexable : titres, descriptions, Open Graph, sitemap, robots, URLs propres et images avec texte alternatif. Le SEO ne doit pas entraîner de contenu inventé ou de répétition artificielle de mots-clés.

## 7. Exigences techniques

### Frontend

- Angular standalone récent, TypeScript strict, Angular Router, Reactive Forms et HttpClient.
- Lazy loading, services séparés, guards, interceptors et gestion uniforme des erreurs.
- Les modules métier sont isolés sous `frontend/src/app/features/`; les placeholders actuels ne sont pas des fonctionnalités.

### Backend

- Java 21 LTS et Spring Boot 4.1.1 dans un monolithe modulaire.
- Spring Web, Security, Data JPA, Validation, PostgreSQL, Flyway, Actuator et OpenAPI.
- Séparation Controller → Service → Repository, DTO validés et erreurs API au format Problem Details.
- `ddl-auto` ne crée pas le schéma en production; les changements de base passent par des migrations versionnées.

### Données principales

Le schéma prépare les identités, rôles et permissions, profils client/employé, marques, catégories, véhicules, chauffeurs, réservations, devis, services, paiements, factures, partenaires, notifications, documents et journaux d'audit. Les relations et les index sont décrits dans [la documentation de base de données](database.md). Le schéma initial n'implémente pas encore les règles métier de ces domaines.

## 8. Sécurité et données personnelles

- BCrypt pour les mots de passe; aucune donnée d'authentification en clair.
- Access token court; refresh token opaque, rotatif, révocable et stocké sous forme de hash.
- Cookie refresh HttpOnly, Secure en production, avec stratégie SameSite adaptée aux domaines réels.
- TLS/HTTPS en production, CORS limité aux origines autorisées, secrets injectés par environnement ou gestionnaire de secrets.
- Limitation de débit sur les opérations sensibles, protection CSRF évaluée pour le déploiement réel et permissions vérifiées côté serveur.
- Collecter uniquement les données nécessaires; définir durée de conservation, droit d'accès/suppression, journalisation et procédure d'incident avec le responsable légal.
- Les documents d'identité, permis et coordonnées nécessitent des règles spécifiques de stockage, accès, chiffrement et rétention avant leur prise en charge.

L'authentification et les permissions catalogue sont implémentées, mais ne constituent pas une conformité de production : récupération de compte, validation d'adresse, rate limiting et validation de la politique CSRF/déploiement restent à réaliser.

## 9. API cible

L'API est REST, documentée par OpenAPI et versionnée si une rupture de contrat l'exige. Catalogue, disponibilité, réservations et devis commerciaux sont livrés; paiement et facture légale restent futurs. Les endpoints réalisés sont répertoriés dans [docs/api.md](api.md).

## 10. Tests et critères d'acceptation

Une fonctionnalité est acceptée lorsqu'elle est reliée à l'API et aux données réelles, valide ses entrées, contrôle l'accès, gère les erreurs, dispose de tests et a été vérifiée sur les tailles d'écran visées.

### Tests obligatoires à mesure de la livraison

- **Backend :** tests unitaires et d'intégration pour droits, validation, persistance PostgreSQL, calcul de prix et conflits de disponibilité.
- **Frontend :** services, formulaires, navigation, filtres, états d'erreur et restrictions d'accès.
- **E2E :** inscription → recherche → demande; traitement agence; refus d'un chevauchement; acceptation d'un devis; refus d'accès client à l'administration.
- **Sécurité :** JWT invalide/expiré, absence d'identité, accès inter-client, entrées malformées et limitation de débit.
- **Base :** exécution des migrations sur PostgreSQL réel ou un environnement de test équivalent; H2 seul ne valide pas la compatibilité PostgreSQL.

### Conditions de sortie du MVP

- Déploiement de test reproductible depuis une base vide par migrations.
- Authentification et autorisation testées de bout en bout.
- Catalogue et prix fournis par le serveur.
- Demande de réservation protégée contre dates invalides et conflits concurrents.
- Parcours client et opérations agence prioritaires testés.
- Tests et sauvegardes vérifiés, erreurs surveillées, configuration de production documentée.
- Conditions de location, politique d'annulation/remboursement, documents juridiques et responsabilités de l'entreprise validés avant ouverture au public.

## 11. Environnements et exploitation

- **Development :** PostgreSQL local, données de développement non sensibles et intégrations externes désactivées par défaut.
- **Test :** tests rapides plus validation d'intégration PostgreSQL automatisée.
- **Production :** secrets dédiés, HTTPS, base sauvegardée, monitoring, journalisation, procédure de déploiement et de restauration.

Les sauvegardes, objectifs de disponibilité, hébergement, domaine, support, responsabilités opérationnelles et procédure de reprise doivent être fixés avec l'agence avant le lancement.

## 12. Décisions à confirmer avec le commanditaire

- Entité juridique, coordonnées officielles et langues du site (français, anglais, arabe).
- Devise, régime fiscal, règles comptables et format légal des factures.
- Tarifs, durée minimale, caution, frais, options, remises et politique d'annulation.
- Statuts qui bloquent la disponibilité, expiration des demandes en attente et politique de confirmation.
- Processus de validation des permis et pièces d'identité; conservation des documents.
- Matrice finale rôles/permissions et éventuelle séparation des responsabilités.
- Prestataires email/SMS, stockage de fichiers et paiement compatibles avec l'activité tunisienne.
- Politique de confidentialité, conditions générales, cookies et durée de conservation.
- Hébergement, localisation des données, budget, sauvegardes, disponibilité et procédure de support.

Aucune de ces décisions ne doit être présentée comme acquise sans validation explicite.
