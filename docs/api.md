# API

Base locale : `http://localhost:8080`. OpenAPI est disponible sur `/api-docs` et Swagger UI sur `/swagger-ui.html`.

| Méthode | Chemin | Autorisation | Contrat |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | `{ "status": "UP", "timestamp": "..." }` |
| GET | `/api/auth/csrf` | Public | Initialise le cookie/header CSRF pour les mutations navigateur. |
| POST | `/api/auth/register` | Public + CSRF | Prénom, nom, e-mail, téléphone, mot de passe robuste et confirmation; attribue `CUSTOMER`, crée le profil, pose un refresh HttpOnly. |
| POST | `/api/auth/login` | Public + CSRF | E-mail/mot de passe; renvoie un JWT court et un refresh opaque rotatif. |
| POST | `/api/auth/refresh` | Cookie refresh + CSRF | Vérifie expiration/révocation, fait tourner le token et révoque la famille en cas de rejeu. |
| POST | `/api/auth/logout` | Cookie refresh + CSRF | Révoque le refresh token et expire le cookie. |
| GET | `/api/auth/me` | Authentifié | Profil courant : id, nom, e-mail, téléphone, rôles, statut. Aucun secret. |
| GET | `/api/customer/profile` | `CUSTOMER` | Profil du principal authentifié; aucun identifiant fourni par le client. |
| PUT | `/api/customer/profile` | `CUSTOMER` + CSRF | Modifie uniquement le profil du principal authentifié. |
| GET | `/api/admin/users` | Permission `users:read` | Liste paginée des comptes utilisateurs. |
| GET | `/api/admin/users/{id}` | Permission `users:read` | Détail sans hash ni jeton. |
| POST | `/api/admin/users` | Permission `users:manage` + CSRF | Crée un employé avec un rôle interne. |
| PUT | `/api/admin/users/{id}` | Permission `users:manage` + CSRF | Modifie le profil d'employé et son rôle. |
| PATCH | `/api/admin/users/{id}/status` | Permission `users:manage` + CSRF | Active/désactive le compte; la désactivation révoque les refresh tokens. |
| GET | `/api/admin/roles` | Permission `users:manage` | Catalogue des rôles et permissions chargés de la base. |
| GET | `/api/vehicles` | Public | Catalogue paginé; filtres `search`, `brand`, `category`, `minPrice`, `maxPrice`, `passengers`, `transmission`, `withDriver`; tri `newest`, `price_asc`, `price_desc`, `year_desc`, `name_asc`; pagination `page`, `size`. Uniquement véhicules `AVAILABLE` liés à une marque/catégorie active. |
| GET | `/api/vehicles/{id}` | Public | Fiche d'un véhicule publié et disponible, avec tarifs, images et équipements. |
| GET | `/api/brands`, `/api/brands/{id}` | Public | Marques actives; liste paginée. |
| GET | `/api/categories`, `/api/categories/{id}` | Public | Catégories actives; liste paginée. |
| GET | `/api/admin/vehicles` | Permission `vehicles:read` | Flotte paginée avec filtres/tri, y compris les statuts non publics. |
| POST / PUT / DELETE | `/api/admin/vehicles[/{id}]` | Permission `vehicles:manage` + CSRF | CRUD véhicule, marque/catégorie liées, tarifs, équipements et métadonnées d'images; DELETE renvoie 204. |
| PATCH | `/api/admin/vehicles/{id}/status` | Permission `vehicles:manage` + CSRF | Met à jour le statut administratif de flotte. |
| GET | `/api/admin/brands`, `/api/admin/categories` | Permission `brands:read` / `categories:read` | Listes paginées incluant les entrées non publiées. |
| POST / PUT / DELETE | `/api/admin/brands[/{id}]` | Permission `brands:manage` + CSRF | Gestion des marques, activation et logo URL. Suppression refusée si des véhicules y sont liés. DELETE renvoie 204. |
| POST / PUT / DELETE | `/api/admin/categories[/{id}]` | Permission `categories:manage` + CSRF | Gestion des catégories et publication. Suppression refusée si des véhicules y sont liés. DELETE renvoie 204. |
| POST / DELETE | `/api/admin/uploads/images[/{key}]` | Permission `vehicles:manage` ou `brands:manage` + CSRF | Upload JPEG/PNG/WebP (10 MiB max), renvoie une URL `/api/files/{uuid.ext}`; DELETE supprime un upload non associé. |
| GET | `/api/files/{key}` | Public | Sert uniquement un fichier local correspondant à un identifiant généré. |

## Réservations (étape 5)

| Méthode | Chemin | Autorisation | Contrat |
| --- | --- | --- | --- |
| GET | `/api/bookings/policy` | Public | Fuseau métier, règle `STARTED_24_HOURS`, `pendingBlocks:false`, options disponibles (actuellement vide). |
| GET | `/api/vehicles/{vehicleId}/availability` | Public | `startDateTime`, `endDateTime` ISO-8601 avec offset; `available` et raison éventuelle, aucune donnée client. |
| GET | `/api/vehicles/available` | Public | Les mêmes dates, plus les filtres/tri/pagination du catalogue V3. |
| POST | `/api/bookings/quote-preview` | Public + CSRF | Même entrée que la demande; retourne véhicule, période, jours, prix de base/frais/remise/taxe/total/devise/fuseau calculés côté serveur. |
| POST | `/api/bookings` | CUSTOMER + `bookings:create` + CSRF | Véhicule, dates, lieux, note client. Propriétaire, référence, prix et statut PENDING imposés par le backend; 201. |
| GET | `/api/bookings` | CUSTOMER + `bookings:read` | Liste paginée limitée au principal; filtre référence/statut. |
| GET | `/api/bookings/{id}` | CUSTOMER + `bookings:read` | Détail/historique propre au client; pas de note interne. Un autre propriétaire reçoit 404. |
| POST | `/api/bookings/{id}/cancel` | CUSTOMER + `bookings:cancel` + CSRF | Annule seulement sa demande PENDING/CONFIRMED, sans remboursement. |
| GET | `/api/admin/bookings`, `/api/admin/bookings/{id}` | `admin:access` + `bookings:read` | Liste/détail/historique agence; filtres référence, customer, vehicle, status, startDate, endDate, page, size. Les bornes portent sur le début du trajet, avec fin exclusive. |
| PUT | `/api/admin/bookings/{id}` | `bookings:manage` + CSRF | Modifie uniquement lieux et note interne, en PENDING/CONFIRMED. |
| PATCH | `/api/admin/bookings/{id}/confirm`, `/reject`, `/start`, `/complete` | `bookings:manage` + CSRF | Transitions contrôlées; confirmation revérifie la disponibilité dans la transaction. |
| PATCH | `/api/admin/bookings/{id}/cancel` | `bookings:cancel` + CSRF | Annulation contrôlée, sans paiement/remboursement. |

`BookingRequest` : vehicleId, startDateTime, endDateTime, pickupLocation, dropoffLocation, customerNote facultatif. Aucun customerId, reference, status ou montant envoyé par le navigateur n'est une source de vérité.

PENDING ne bloque pas selon le choix du commanditaire : la garantie anti-chevauchement porte sur CONFIRMED/IN_PROGRESS, pas sur deux demandes en attente. Les règles précises, erreurs et limites sont dans [booking.md](booking.md).

## Devis (étape 6)

| Méthode | Chemin | Autorisation | Contrat |
| --- | --- | --- | --- |
| GET | `/api/admin/quotes`, `/api/admin/quotes/{id}` | admin:access + quotes:read | Liste paginée sans lignes/historique; détail avec snapshots et historique. |
| GET | `/api/admin/quotes/options/{bookingId}` | quotes:read | Chauffeur disponible et services réellement configurés pour la devise de la demande. |
| POST | `/api/admin/quotes/booking/{bookingId}` | quotes:create + quotes:manage + CSRF | Crée DRAFT, calcule/sauvegarde lignes et total, génère référence/version serveur. |
| PUT | `/api/admin/quotes/{id}` | quotes:manage + CSRF | Modifie uniquement DRAFT, conserve les tarifs snapshot existants. |
| POST | `/api/admin/quotes/{id}/versions` | quotes:create + quotes:manage + CSRF | Nouvelle référence et version pour le même booking, tarifs courants, ancien snapshot conservé. |
| POST | `/api/admin/quotes/{id}/send` | quotes:send + CSRF | DRAFT vers SENT, publication dans l'espace client, pas d'e-mail/SMS. |
| POST | `/api/admin/quotes/{id}/cancel` | quotes:manage + CSRF | SENT vers CANCELLED, sans suppression du devis. |
| GET | `/api/customer/quotes`, `/api/customer/quotes/{id}` | CUSTOMER + quotes:read | Propositions appartenant au principal, brouillons/notes internes non exposés. |
| POST | `/api/customer/quotes/{id}/accept` | CUSTOMER + quotes:accept + CSRF | Vérifie SENT/validité/cohérence/disponibilité/version; ACCEPTED sans confirmation Booking automatique. |
| POST | `/api/customer/quotes/{id}/reject` | CUSTOMER + quotes:reject + CSRF | SENT vers REJECTED, message facultatif. |
| POST | `/api/customer/quotes/{id}/request-change` | CUSTOMER + quotes:read + CSRF | Message facultatif tracé, sans modifier le devis ou son total. |

Entrée agence : validUntil, withDriver, discountAmount autorisé, customerNote, internalNote, services [{serviceId, quantity}]. Les prix unitaires, taxe, total, référence et version sont imposés/recalculés par le backend. Entrée client : uniquement message facultatif, jamais une donnée financière.

Filtres : reference, customer, booking, vehicle, status, date (création à partir de), validUntil (échéance au plus tard), page, size. Dates avec offset. Les listes client imposent toujours leur propriétaire.

Lorsqu'un devis existe, sa version courante doit être ACCEPTED pour confirmer le booking. La confirmation agence reprend son snapshot et refait le contrôle transactionnel de disponibilité. Règles/snapshots/erreurs : [quotes.md](quotes.md).

Paiements, factures légales, remboursements, chauffeurs opérationnels et livraison de notifications restent hors périmètre.

Les erreurs suivent le format Problem Details et les messages ne divulguent pas les exceptions internes. Les tri possibles sont whitelistés, les pages sont bornées et les montants utilisent des nombres décimaux (`BigDecimal` côté Java).