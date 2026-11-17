# Mission 8 — Audit frontend (lecture seule)

**Périmètre** : `frontend/src/app/` (routage, gardes, services, layouts). Aucun développement effectué.
**Référence** : arbre de travail courant. Une modification locale existe : `features/quotes/quote-detail.component.ts` (préexistante, non auditée en détail).

## 1. Architecture

- Angular standalone, routes chargées en paresse (`loadComponent`) dans `frontend/src/app/app.routes.ts`.
- Trois layouts : `PublicLayoutComponent`, `CustomerLayoutComponent`, `AdminLayoutComponent`.
- Services HTTP dans `features/*` et `core/` (auth, booking, catalogue, driver, health, invoice, quote, service catalogue, customer admin).
- Intercepteurs : `core/interceptors/auth.interceptor.ts`, `error.interceptor.ts`.

## 2. Routage et gardes (lecture de `app.routes.ts`)

| Zone | Garde | Rôles déclarés côté front |
|---|---|---|
| `''` (public) | aucune | — |
| `auth/*` | aucune | — |
| `customer/*` | `authGuard`, `roleGuard` | `CUSTOMER` |
| `admin/*` (parent) | `authGuard`, `roleGuard` | `SUPER_ADMIN`, `MANAGER`, `AGENT`, `CONCIERGE`, `ACCOUNTANT` |
| `admin/customers` | `roleGuard` | `SUPER_ADMIN`, `MANAGER` |
| `admin/drivers` | `roleGuard` | `SUPER_ADMIN`, `MANAGER`, `AGENT` |
| `admin/quotes/new` | `roleGuard` | `SUPER_ADMIN`, `MANAGER`, `AGENT` |
| `admin/users` | `roleGuard` | `SUPER_ADMIN` |

Constats :

- **F1 — Garde = UX uniquement.** Le contrôle réel est côté serveur (`SecurityConfig`, cf. backend §5). Un utilisateur peut contourner la garde front ; le backend reste la seule frontière de sécurité. Ce n'est pas une faille en soi, mais toute nouvelle règle doit être testée côté API.
- **F2 — Dérive de rôles.** Les rôles de `app.routes.ts` (`CONCIERGE`, `ACCOUNTANT`, etc.) ne sont pas vérifiés à partir d'une source commune avec le backend. Risque de divergence (non confirmé sans comparaison aux autorités réelles).
- **F3 — `admin/bookings` et `admin/quotes` n'ont pas de restriction de rôle propre** au-delà du parent. Cohérence avec `SecurityConfig` à vérifier.
- **F4 — Placeholders** : `/admin/partners`, `/admin/payments`, `/admin/reports`, `/services`, `/about`, `/faq`, `/contact`, `/chauffeur`, `/airport-transfer`, `/auth/forgot-password`, `/customer/notifications`. Ces routes affichent un texte d'attente via `PagePlaceholderComponent`.
- **F5 — Route `**` redirige vers `''`**, pas de page 404 dédiée.

## 3. Authentification

- `core/auth/auth.service.ts` : `session()`, `restoreSession()`, `hasAnyRole()`.
- Cookies et CSRF : le backend pose le cookie CSRF avec `SameSite=Strict`, `Path=/api`, non HttpOnly (`SecurityConfig` lignes 64–66). Le front doit renvoyer la valeur en en-tête : à vérifier dans `auth.interceptor.ts` (non lu en détail).
- Le front ne stocke pas de token dans `localStorage` d'après la structure ; à confirmer (non vérifié).

## 4. Espaces

| Espace | État | Intégration |
|---|---|---|
| Site client (public) | Partiel | Catalogue, détail véhicule, marques et catégories existent. Pas de page de recherche par dates (le catalogue public n'a pas de filtre de disponibilité). |
| Espace client | Partiel | Dashboard, réservations, devis, factures, profil. Pas de parcours « réserver avec hold PENDING ». |
| Administration Elite | Partiel | Véhicules, marques, catégories, réservations, devis, factures, clients, chauffeurs, services, utilisateurs. Pas de gestion des partenaires, pas de paiement admin. |
| Espace partenaire (lecture seule) | **Absent** | Aucune route, aucun service, aucun modèle TypeScript. |

## 5. Services HTTP et modèles

- Services présents (liste de fichiers) : `booking`, `catalogue`, `driver`, `health`, `invoice`, `quote`, `service catalogue`, `customer admin`.
- **Aucun service `partner`.** Aucun modèle TypeScript pour `Partner`, `Guarantee`, `Availability`, `PartnerAssignment`.
- Les modèles de réservation ne contiennent pas de statut hold ni de date d'expiration de hold.

## 6. Formulaires, erreurs, états

- Formulaires : `features/quotes/quote-editor.component.ts`, `features/auth/login.component.ts`, `register.component.ts`, `customer-profile.component.ts`.
- `error.interceptor.ts` : gère les erreurs HTTP ; le détail des codes métier (`VEHICLE_HAS_ACTIVE_BOOKING`, `BOOKING_ACCESS_DENIED`) n'est pas vérifié comme affiché à l'utilisateur.
- États vides et chargement : présents dans certaines listes ; non vérifiés composant par composant.

## 7. Tests et build

- Présence de fichiers de test Angular : non recensés dans cette mission. **Non vérifié.**
- Build et tests front : **non exécutés**. Node détecté : 18.20.8, incompatible avec le build Angular 22 du projet (constat de la mission précédente, à confirmer avant toute exécution). Aucune installation effectuée, conformément à la mission.

## 8. Ce qu'il faut construire côté front (MVP)

1. Module `partner` (lecture seule) : routes `/partner/*`, garde `PARTNER`, services et modèles.
2. Admin partenaires et garanties : remplacer le placeholder `/admin/partners`.
3. Recherche client par dates et affichage des seuls véhicules garantis.
4. Parcours de réservation avec état PENDING, délai de 30 minutes et confirmation après paiement simulé.
5. Tests : unitaires (services, garde), E2E Playwright sur parcours critiques (non présents en l'état pour ces parcours).

## 9. Synthèse des constats

| Réf. | Constat | Statut |
|---|---|---|
| F1 | Garde front non sécurisante, backend authoritative | Confirmé (conception) |
| F2 | Liste de rôles dupliquée entre front et back | Risque, non vérifié |
| F4 | Placeholders nombreux, dont partenaires et paiements | Confirmé |
| F-P | Espace partenaire absent | Confirmé |
| F-B | Build et tests front non exécutés | Non vérifié |
