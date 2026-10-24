# Rapport étape 5 : Disponibilité et réservations

**Validation :** 5 octobre 2026. **Production : NOT READY.**

## Décisions confirmées par le commanditaire

PENDING ne bloque pas et n'expire pas automatiquement. Chaque tranche de 24 heures commencée est facturée. La garantie de concurrence porte donc sur la **confirmation**, pas sur la création de deux demandes PENDING : une confirmation réussie, une refusée pour la même période. Deux demandes PENDING peuvent légitimement coexister. Aucun délai arbitraire d'expiration n'a été inventé.

## Résultats fonctionnels

| Critère | Résultat | Preuve |
| --- | --- | --- |
| Booking model | PASS | Entité JPA, V4, référence unique générée par séquence, tarifs figés, lieux/notes/horodatages et historique |
| Availability | PASS | Endpoint public sans donnée client, statut administratif distinct et catalogue avec période/filtres combinés |
| Overlap detection | PASS | Comparaisons strictes et intervalles demi-ouverts; périodes adjacentes autorisées |
| Concurrency protection | PASS | Verrou véhicule puis réservation, rafraîchissement JPA et exclusion GiST CONFIRMED/IN_PROGRESS; deux tests PostgreSQL concurrentiels |
| Price calculation | PASS | BigDecimal/NUMERIC(12,3), tranches de 24h commencées, calcul serveur au preview et à la création, limite de précision microseconde |
| Customer booking | PASS | Formulaire réel depuis la fiche, availability/preview/create, référence et statut PENDING, liste/détail |
| Customer isolation | PASS | Recherche propriétaire + réservation, 404 inter-client, notes internes absentes; tests HTTP et navigateur |
| Booking cancellation | PASS | Client propriétaire PENDING/CONFIRMED, audit et cancelledAt, période libérée; aucun remboursement |
| Admin booking management | PASS | Liste paginée, filtres référence/client/véhicule/statut/période, détail, historique, confirmation/refus/annulation/démarrage/fin, édition limitée lieux/note interne |
| RBAC | PASS | bookings:read/create/manage/cancel, droits de domaine côté serveur; écriture agence limitée à SUPER_ADMIN/MANAGER/AGENT |
| Audit | PASS | BOOKING_CREATED/CONFIRMED/REJECTED/CANCELLED/UPDATED dans la transaction; acteur, entité, timestamp, résultat |

## Vérifications

| Contrôle | Résultat |
| --- | --- |
| Backend tests | **23/23** sur PostgreSQL : 12 auth/catalogue, 3 stockage, 8 réservation |
| Frontend tests | **19/19** Vitest, dont 9 tests du parcours réservation et filtre temporel combiné |
| E2E tests | **9/9** contre l'API PostgreSQL; 4 parcours réservation sans interception, 5 auth/catalogue antérieurs |
| Concurrency tests | **2/2**, inclus dans les 23 tests : service et SQL direct, deux transactions/barrière, une confirmation réussie/une refusée |
| PostgreSQL | PASS, 18.3 UTF-8 sur bases vierges jetables; aucune validation de base permanente |
| Flyway | PASS, V1–V4 exécutées par Flyway sur schéma vierge |
| Angular build | PASS, templates/types stricts et routes lazy compilés |
| Backend build | PASS, Maven verify et JAR Spring Boot reconditionné |
| Responsive | Formulaire public et détail agence capturés à 1366 et 375 px; aucun débordement horizontal |

Les deux scénarios publics catalogue « filtres/détail » et « vide/erreur » utilisent des réponses contrôlées. **Aucun endpoint de réservation n'est intercepté dans ses E2E.** Le parcours client se connecte, choisit véhicule/dates/lieux, vérifie la disponibilité, voit le prix serveur, crée la demande puis la retrouve. Un AGENT réel confirme depuis l'agence; une seconde demande chevauchante est refusée. Un autre client reçoit 404 sur lecture/annulation et ne voit pas le détail. L'annulation propriétaire libère la période.

Les tests H2 rapides désactivent explicitement les deux tests de concurrence PostgreSQL. Ils ne sont pas utilisés comme preuve de l'exclusion GiST.

## Données et concurrence

Les références utilisent une séquence globale : trous de numérotation autorisés, aucun numéro financier/facture. Les montants annexes sont à zéro et aucun tarif d'option n'est inventé. Les dates passent par Instant/TIMESTAMPTZ; BOOKING_TIME_ZONE configurable, Africa/Tunis par défaut. Temporal convertit les heures locales avec rejet des ambiguïtés DST. Les changements de formulaire invalident un récapitulatif périmé.

La contrainte d'exclusion reste la garantie de dernier recours, même si une écriture contourne JPA. L'extension btree_gist doit être disponible via le DBA/rôle de migration. Les plans EXPLAIN observés utilisent GiST pour les conflits et l'index client V1 pour les listes; il ne s'agit pas d'un benchmark de production.

## Limites et environnement

- Aucun paiement, facture, remboursement, devis complet, SMS, notification ou chauffeur opérationnel ajouté.
- Aucune option tarifée disponible tant qu'elle n'est pas validée; la policy renvoie une liste vide. La taxe à zéro n'est pas une décision fiscale.
- PENDING peut rester indéfiniment en attente; l'agence doit arbitrer. Il n'y a pas d'expiration ni de notification automatique.
- Les dates/vehicule/propriétaire/prix d'une demande existante ne sont pas éditables dans cette étape; seuls lieux et note interne sont autorisés à l'agence.
- L'application locale est servie sur 4200, API 8080 reliée au PostgreSQL de recette temporaire 55442. Le profil test fournit des comptes de test; ne jamais le déployer en production.
- Les clusters de vérification ont été arrêtés/supprimés. Le cluster de recette reste actif uniquement pour l'essai local. Base permanente et service PostgreSQL Windows de l'utilisateur non modifiés.
- Deux avertissements non bloquants au build frontend : budget CSS préexistant de l'accueil et dépendance CommonJS jsbi du polyfill Temporal. Aucun budget de build n'a été augmenté pour les masquer.
- Secrets/déploiement, extension en production, règles fiscales/commerciales, sauvegardes, supervision et recette sur données permanentes restent à traiter.

Règles détaillées : [booking.md](booking.md). API : [api.md](api.md). Schéma : [database.md](database.md). Sécurité : [security.md](security.md). Reproduction : [testing.md](testing.md).