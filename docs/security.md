# Sécurité

## Socle livré

- Mots de passe hashés avec BCrypt.
- Access token HMAC/JWT limité à 15 minutes.
- Refresh token opaque aléatoire, hashé en SHA-256 en base, révocable et rotatif.
- Cookie de refresh HttpOnly, SameSite Strict, limité à `/api/auth`; l'environnement `prod` impose `Secure`.
- Validation Bean Validation, messages d'erreur génériques et CORS configurable.
- Les chemins `/api/admin/**` exigent `admin:access` côté backend; les guards Angular ne sont qu'une aide UX.
- Les permissions fines `users:read/manage`, `vehicles:read/manage`, `brands:read/manage`, `categories:read/manage` et permissions profil sont liées en base; chaque contrôleur admin applique aussi sa permission de domaine.
- L'API recharge l'utilisateur actif et ses autorités depuis PostgreSQL/Hibernate à chaque requête bearer; un rôle modifié ou un compte désactivé invalide immédiatement l'accès API, sans attendre l'expiration JWT.
- Les mutations navigateur utilisent le cookie/header CSRF (`/api/auth/csrf`); le refresh cookie reste HttpOnly, distinct du cookie CSRF.
- Le stockage image accepte JPEG/PNG/WebP, contrôle la taille configurée (10 MiB par défaut) et la signature binaire, génère des noms UUID et confine les chemins à la racine locale. Les mutations d'upload sont authentifiées et soumises au CSRF; les URLs de fichiers sont en lecture publique.

## Réservations

Les routes client exigent le rôle CUSTOMER et `bookings:read/create/cancel`. Le propriétaire vient du principal, et les lectures/annulations recherchent `(id, customer_id)`. Un autre client reçoit 404. Montant, statut et référence ne sont jamais acceptés comme source de vérité dans le JSON; les notes internes ne sont pas exposées aux clients.

L'agence exige `admin:access` et `bookings:read/manage/cancel`; super-admin, manager et agent peuvent écrire, concierge/comptable lisent seulement. Les mutations sont protégées par CSRF. Les transitions et les montants sont contrôlés côté serveur; l'exclusion PostgreSQL protège les périodes CONFIRMED/IN_PROGRESS même hors application. Availability et policy publiques ne renvoient aucune réservation ni donnée personnelle.

Les guards Angular attendent la restauration mutualisée de session avant le contrôle de rôle lors d'un chargement direct. Cela reste une protection UX : les contrôles d'autorisation de l'API sont indépendants.

## Devis

Quotes read/create/manage/send/accept/reject sont contrôlés côté serveur. Écriture agence : SUPER_ADMIN/MANAGER/AGENT; lecture seule : CONCIERGE/ACCOUNTANT. Les routes client exigent CUSTOMER et recherchent leur propriétaire, avec 404 inter-client. Les brouillons et notes internes ne sont pas exposés.

Référence, version, prix unitaire, taxe et total ne sont pas repris du JSON client. Seule la remise agence permissionnée est revalidée et bornée au sous-total. Les services sont résolus par ID actif/devise; aucun montant utilisateur ne devient un prix de service officiel. Les snapshots envoyés/acceptés sont immuables, et une nouvelle version est distincte.

Les réponses et versions sont sérialisées sous verrou Booking. L'acceptation ne bloque pas le véhicule, ne paie rien et ne confirme rien. La confirmation finale réutilise les verrous et l'exclusion PostgreSQL V4; elle exige la dernière version acceptée et cohérente. Expiration et audit système sont persistés même lorsqu'une acceptation dépassée est refusée.

## Limites avant production

La clé locale de développement n'est pas une clé de production. `JWT_SECRET`, les credentials DB et les origines CORS doivent être injectés par le gestionnaire de secrets/déploiement. La rotation/révocation du JWT d'accès n'est pas fournie; un token déjà émis reste valide jusqu'à son expiration. Aucun rate limiting ni flux de vérification d'adresse e-mail/récupération de mot de passe n'est présent.

Le CORS de développement accepte les deux origines loopback Angular. En production, `CORS_ALLOWED_ORIGINS` doit être limitée aux domaines déployés. Valider l'interaction du cookie SameSite et du jeton CSRF avec les domaines réels avant mise en production.