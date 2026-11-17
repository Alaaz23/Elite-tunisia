# Elite Tunisia — Plan directeur d'implémentation (marketplace de mobilité premium)

> Document de planification. Les statuts réels des tâches sont dans §11 bis (certaines tâches sont IMPLEMENTED ou IN PROGRESS, sur un arbre de travail non committé). Libellés : `[CONFIRMÉ]` vérifié dans le code ou un log ; `[CONSTAT]` observé sans test dédié ; `[HYPOTHÈSE]` ; `[PROPOSITION]` ; `[DÉCISION À VALIDER]` ; `[À VÉRIFIER]` nécessite un test ou une base.

## 1. Executive Summary

- Le socle actuel est un monolithe Angular / Spring Boot / PostgreSQL solide pour une agence unique : réservations avec contrainte d'exclusion (V4), devis, paiement simulé, affectation de chauffeurs, audit, CSRF, throttling de connexion. `[CONFIRMÉ]`
- Il n'est pas une marketplace : aucun concept de partenaire dans le code (table `partners` orpheline, V1), aucun rôle PARTNER / DRIVER, pas de commission, pas de grand livre, pas de paiement réel, pas de notifications, pas de portails partenaire et chauffeur. `[CONFIRMÉ]`
- Le flux client → facture n'est pas bouclé : `InvoiceService.createFromQuote` et `attachPayment` n'ont aucun appelant en production ; une location sans frais ne produit aucune facture. `[CONFIRMÉ]`
- Un défaut de modèle bloque la disponibilité par dates : `BookingService.rentable` exige `status == AVAILABLE`, donc un véhicule loué (RENTED) est refusé pour toute période future (NEW-01). `[CONFIRMÉ]`
- Stratégie : stabiliser (étape A), trancher les décisions métier bloquantes (B), construire partenaires, véhicules rattachés, disponibilité, réservation (C), puis finance, facturation, chauffeurs et portails (D), enfin notifications, reporting et préparation production (E). Monolithe modulaire, migrations additives V21+, sans microservice, Kafka ni Redis.
- Verdict : la fondation est exploitable pour des tests internes ; la marketplace nécessite un programme par étapes, dont le paiement réel dépend de décisions juridiques et fiscales non prises.

## 2. Vision produit et modèle économique

Décisions confirmées (non rediscutées) :
1. Marketplace de partenaires indépendants ; les véhicules leur appartiennent ou sont gérés par eux.
2. Deux modes : self-drive et avec chauffeur.
3. Toute réservation est validée par l'administration avant confirmation.
4. Encaissement centralisé par Elite Tunisia, commission prélevée par réservation.
5. Chauffeurs gérés centralement.
6. Couverture nationale, plusieurs partenaires, données strictement séparées.

Conséquences techniques : isolation par partenaire sur toutes les lectures et écritures ; flux d'argent à trois niveaux (client → Elite → partenaire) ; commission figée par réservation ; validation admin comme étape de la machine d'états, pas comme exception.

## 3. Sources analysées et méthodologie

Sources lues : `docs/technical-audit.md` (ISS-01 à ISS-14), `docs/architecture/marketplace-gap-analysis.md`, `docs/architecture/international-benchmark-and-target-architecture.md`. Chaque constat structurant a été revérifié dans le code (services, entités, `SecurityConfig`, migrations V1, V4, V6, V7, `pom.xml`, `app.routes.ts`). Aucun test ni service n'a été lancé pour ce document ; seuls des logs existants ont été lus.

Contradictions entre documents et résolution :

| # | Contradiction | Résolution |
|---|---|---|
| C1 | Audit technique : utilisateur désactivé bloqué seulement au refresh. | Faux : `JwtAuthenticationFilter` recharge l'utilisateur et filtre `isEnabled()`. `[CONFIRMÉ]` |
| C2 | Audit : frontend inexécutable sur Node 18. | Vrai pour Node 18.20.8. Un Node portable 26 (hors dépôt) a permis build et E2E. Node 24.15.0 est épinglé dans `.github/workflows/ci.yml` (job `frontend`) ; `frontend/package.json` `engines` accepte aussi `>=26` ; pas de `.nvmrc`. `[CONFIRMÉ]` |
| C3 | Audit : PostgreSQL non exécuté. | Des exécutions `-Ppostgres-test verify` existent (logs `verify6` à `verify13`). Dernier : `verify13` 164 + 25 tests, 0 échec. `verify12` échouait (409 / `users_email_key`), cause probable : données résiduelles de la base de test persistante. `[À VÉRIFIER]` |
| C4 | Audit : `recordReturn` contourne la machine d'états. | Partiellement inexact : `BookingEntity.transition` valide `canTransitionTo`. Ce qui est contourné : idempotence et événements de `BookingService.transition`. `[CONFIRMÉ]` |
| C5 | Benchmark : « 35 tables », migrations « V21 à V30 ». | Estimation et numérotation indicative. `[À VÉRIFIER]` sur une base réelle. |
| C6 | Audit technique ne mentionne pas le double paiement par devis. | Ajouté ici (ISS-03 étendu). `[CONFIRMÉ]` |

## 4. État des lieux consolidé

| Domaine | État actuel | Maturité | Problèmes | Action |
|---|---|---|---|---|
| Authentification / sessions | JWT + refresh avec rotation et détection de rejeu ; CSRF cookie SameSite=Strict | Opérationnel | Throttle en mémoire (ISS-11) | Throttle partagé avant multi-instance |
| Rôles / RBAC | 6 rôles (V1) ; `/api/admin/**` = `admin:access` seule | Partiellement opérationnel | Aucun rôle partenaire ou chauffeur ; `admin:access` trop large | Nouveaux préfixes et rôles dédiés |
| Catalogue véhicules | CRUD admin, images ; pas de partenaire, plaque, caution, publication | Partiellement opérationnel | Pas de propriétaire | Rattachement partenaire |
| Disponibilité | EXCLUDE gist sur CONFIRMED/IN_PROGRESS ; mais statut véhicule bloque (NEW-01) | Partiellement opérationnel | Refus de toute période future si RENTED | Modèle de blocage par dates |
| Réservation | Machine d'états, PENDING ne bloque pas, pas d'expiration | Partiellement opérationnel | Pas de lien paiement ↔ statut | Étape de validation + expiration |
| Devis | Création, acceptation, expiration planifiée | Opérationnel | `get` écrit en transaction non readOnly (ISS-09) | Séparer lecture et expiration |
| Paiement | Fournisseur simulé, capture immédiate | Prototype | Pas d'unicité par devis, pas de remboursement, appel fournisseur sous verrou | Refonte avant tout paiement réel |
| Facturation | Facture de frais de retour seulement | Prototype | Chemin devis → facture sans appelant (ISS-04/05) | Brancher ou retirer, décision |
| Chauffeurs / transferts | Affectation sérialisée ; transferts avec `pickup_at` seul | Partiellement opérationnel | Pas d'exclusion en base, pas de durée | Ajouter fin/durée puis contrainte |
| Notifications | Interface seule, table orpheline | Absent | — | Outbox PostgreSQL |
| Partenaires / documents / reporting | Paquets vides, tables V1 orphelines | Absent | — | Construire |
| Portails partenaire et chauffeur | Absents ; routes placeholder (ISS-12) | Absent | — | Construire |
| Audit | `AuditService` MANDATORY ; erreurs avalées (ISS-02) | Partiellement opérationnel | Échec masqué | Ne plus masquer |
| Tests | 44 classes backend ; H2 sans Flyway ; suite PostgreSQL ; 21 E2E locaux hors CI | Partiellement opérationnel | E2E hors CI ; Node non épinglé | Étape A |
| Infrastructure prod | Pas de CSP, pas de `forward-headers-strategy`, pool Hikari par défaut | À vérifier | — | Étape E |

## 5. Risques techniques

### 5.1 Anomalies ISS-01 à ISS-14 réexaminées

| ID | Statut | Gravité réelle | Preuve | Action |
|---|---|---|---|---|
| ISS-01 | `[CONFIRMÉ]` | Haute en théorie, probabilité faible | `recordPickup/recordReturn` : réservation puis véhicule ; `BookingService.locked` : véhicule puis réservation | Ordre global véhicule → réservation + test de concurrence PostgreSQL |
| ISS-02 | `[CONFIRMÉ]`, nuancé | Moyenne | `try { audit.recordAudit } catch (Exception ignore)` ; propagation MANDATORY donc transaction possiblement rollback-only | Laisser l'échec remonter ; test |
| ISS-03 | `[CONFIRMÉ]` | Haute | Clé d'idempotence globale (index V6) ; `DataIntegrityViolation` attrapée dans la même transaction ; fournisseur appelé sous verrou ; pas d'unicité par devis | Unicité capture par réservation, idempotence par propriétaire, hors transaction |
| ISS-04 | `[CONFIRMÉ]` | Haute (fonctionnelle) | `createFromQuote`/`attachPayment` sans appelant | Décision de facturation puis câblage |
| ISS-05 | `[CONFIRMÉ]`, code dormant | Moyenne | Repli `currentTimeMillis() % 1000000` ; date en UTC vs Africa/Tunis ; pas d'unicité par devis ; `adminList` sans pagination | Supprimer le repli, fuseau, pagination, unicité |
| ISS-06 | `[CONSTAT]` | Moyenne | `booking_operations` sans CHECK ni unicité PICKUP/RETURN ; contrôles côté application | Contraintes additives ; `[À VÉRIFIER]` sur données réelles |
| ISS-07 | `[CONFIRMÉ]` | Moyenne | Migrations actives dans `database/migrations/` copiées par `pom.xml` ; 4 fichiers inertes dans le dossier backend dont un `V11` différent (hash) | Nettoyage sur décision explicite de l'utilisateur uniquement |
| ISS-08 | `[CONFIRMÉ]` | Moyenne | Verrou ligne chauffeur, pas d'EXCLUDE (V11) ; transferts sans fin | Durée de transfert puis contrainte |
| ISS-09 | `[CONFIRMÉ]` | Basse | `QuoteService.get` écrit ; worker d'expiration existant | Lecture seule + worker |
| ISS-10 | `[CONSTAT]` | Moyenne | E2E hors CI ; timeout 60 s nécessaire sur machine lente ; Node épinglé en CI (24.15.0) mais `engines` non resserré | `engines` resserré, `.nvmrc` optionnel, job E2E CI |
| ISS-11 | `[CONFIRMÉ]` | Moyenne (mono-instance : faible) | `LoginThrottle` en `ConcurrentHashMap` | Stockage PostgreSQL pour le throttle |
| ISS-12 | `[CONFIRMÉ]` | Fonctionnelle | Routes placeholder : chauffeur, airport-transfer, services, about, faq, contact, forgot-password, notifications, admin payments/partners/reports | Livrer ou masquer |
| ISS-13 | `[CONFIRMÉ]`, nuancé | Basse | Handler 500 neutre, `include-message: never` ; messages métier parfois en anglais | Uniformiser les messages |
| ISS-14 | `[CONFIRMÉ]` | Info | Voir C3 ; `verify13` vert | Fixer une base de test jetable par exécution |

### 5.2 Constats additionnels

- **NEW-01** `[CONFIRMÉ]` : disponibilité par statut de véhicule, incompatible avec les dates futures (voir §4).
- **NEW-02** `[CONFIRMÉ]` : aucune contrainte d'unicité de paiement par devis ni par réservation ; deux clés différentes donnent deux paiements CAPTURED. Table `payments` en `ON DELETE CASCADE` depuis `quotes` (V6), inadapté au financier.
- **NEW-03** `[CONFIRMÉ]` : `SecurityConfig` donne `admin:access` seule sur `/api/admin/**` ; un futur rôle partenaire ne doit jamais l'obtenir.
- **NEW-04** `[CONFIRMÉ]` : lieux de prise et retour en texte libre `varchar(300)` (V4) : pas de zone géographique ni de tarification par zone.
- **NEW-05** `[CONFIRMÉ]` : pas d'expiration des réservations PENDING.
- **NEW-06** `[CONFIRMÉ]` : `verify12` en échec (6 échecs + 1 erreur, doublons d'e-mail) ; `verify13` vert. Cause exacte non prouvée `[À VÉRIFIER]`.

### 5.3 Risques marketplace

| Risque | Gravité | Atténuation |
|---|---|---|
| Fuite de données entre partenaires (IDOR) | Critique | Identifiant partenaire pris du jeton, repositories filtrés, tests d'isolation systématiques |
| Erreur de calcul de commission ou de reversement | Critique | Snapshot par réservation, grand livre en ajout seul, tests de rapprochement |
| Prestataire de paiement incompatible marketplace | Critique | Validation auprès du prestataire et d'un professionnel tunisien ; aucun prestataire présenté comme compatible sans preuve |
| Double réservation ou double paiement | Haute | Contraintes en base + tests de concurrence PostgreSQL |
| Responsabilité (assurance, sinistres, statut chauffeur) | Haute | Décisions juridiques avant production |
| Dette de migration (schéma réel non reproduit sur données historiques) | Moyenne | Copie de données représentative `[À VÉRIFIER]` |

## 6. Architecture cible

Monolithe modulaire, un déploiement, un schéma PostgreSQL. Modules : chaque domaine expose un service et ses repositories ; pas d'accès direct aux repositories d'un autre module.

| Domaine | Responsabilité | Existant | Dépendances | Données | Rôles | Risque | Priorité |
|---|---|---|---|---|---|---|---|
| A Identité et accès | Auth, rôles, permissions, throttle | Oui | — | users, roles, refresh tokens | tous | Moyen | P0 |
| B Partenaires | Onboarding, statut, contrat, documents | Table seule | A | partners, partner_users, documents | admin, partenaire | Élevé | P0 |
| C Flotte | Véhicules rattachés, publication, documents | Partiel | B | vehicles | admin, partenaire | Moyen | P0 |
| D Disponibilité et tarification | Blocages par dates, règles de prix | Partiel | C | availability, pricing_rules | admin, partenaire | Élevé | P0 |
| E Réservation | Cycle de vie, validation admin, expiration | Partiel | C, D | bookings, booking_events | client, admin | Élevé | P0 |
| F Paiement | Abstraction fournisseur, webhooks, remboursements | Prototype | E | payments, payment_events, refunds | client, admin | Critique | P1 |
| G Finance | Commission, grand livre, reversements, relevés | Absent | F | ledger_entries, commission_snapshots, payouts | admin, partenaire | Critique | P1 |
| H Facturation | Factures, avoirs, numérotation | Prototype | F, G | invoices | client, admin | Élevé | P1 |
| I Chauffeurs et transferts | Affectation, planning, durée | Partiel | E | drivers, assignments, transfers | admin, chauffeur | Moyen | P1 |
| J Notifications | Outbox, canaux, modèles | Absent | E | notifications, outbox | tous | Moyen | P2 |
| K Reporting | Tableaux de bord, exports | Absent | F, G | vues | admin, partenaire | Faible | P2 |
| L Plateforme | Observabilité, CI, sauvegardes, stockage | Partiel | tous | — | exploitation | Moyen | P0 / P2 |

Choix `[PROPOSITION]` : outbox PostgreSQL avec `FOR UPDATE SKIP LOCKED` pour les notifications ; migrations additives V21+ (expand/contract) dans `database/migrations/` ; feature flag pour le paiement réel ; partenaire interne « Elite Tunisia » pour les véhicules existants.

## 7. Modèle de données conceptuel

| Entité | Existe | Utilisée | Action | Relation clé |
|---|---|---|---|---|
| Partner | Table V1 | Non | Créer entité et adapter | 1-N vehicles, partner_users |
| PartnerUser | Non | — | Créer | N-1 Partner, N-1 User |
| PartnerDocument | `documents` V1 | Non | Adapter | N-1 Partner |
| PartnerContract | Non | — | Créer `[DÉCISION À VALIDER]` | N-1 Partner |
| Vehicle | Oui | Oui | Ajouter partner_id, plaque, publication, caution | N-1 Partner |
| VehicleDocument | Non | — | Créer | N-1 Vehicle |
| VehicleAvailability | Non | — | Créer (blocages par dates) | N-1 Vehicle |
| PricingRule | Non | — | Créer | N-1 Vehicle ou catégorie |
| Booking | Oui | Oui | Ajouter partner_id dénormalisé, statut de validation | N-1 Vehicle |
| BookingEvent | Oui | Oui | Conserver | N-1 Booking |
| Payment | Oui | Oui | Lier à la réservation, unicité capture | N-1 Booking |
| PaymentEvent | Oui | Oui | Conserver (webhooks dédupliqués) | N-1 Payment |
| Refund | Non | — | Créer | N-1 Payment |
| CommissionRule | Non | — | Créer | N-1 Partner |
| CommissionSnapshot | Non | — | Créer, immuable | 1-1 Booking |
| LedgerEntry | Non | — | Créer, ajout seul | N-1 Booking |
| PartnerPayout | Non | — | Créer | N-1 Partner |
| PartnerStatement | Non | — | Créer | N-1 Partner |
| Driver | Oui | Oui | Conserver | 1-N assignments |
| DriverAssignment | Oui | Oui | Ajouter contrainte d'exclusion | N-1 Booking |
| Transfer | Oui | Oui | Ajouter durée / fin | N-1 Booking |
| Invoice | Oui | Partiel | Lier à réservation, unicité | N-1 Booking |
| Notification | Table V1 | Non | Créer entité + outbox | N-1 User |
| AuditLog | Oui | Oui | Conserver | — |

Principes : (1) migrations additives uniquement ; (2) `ON DELETE RESTRICT` pour le financier ; (3) montants en `numeric` avec devise explicite ; (4) commission figée par réservation ; (5) grand livre en ajout seul, jamais de mise à jour ; (6) cautions hors chiffre d'affaires ; (7) unicité d'un paiement capturé par réservation (index unique partiel) ; (8) contraintes CHECK sur montants et statuts ; (9) horodatages en UTC, affichage Africa/Tunis ; (10) toute table partenaire porte `partner_id` indexé.

## 8. Sécurité et isolation multi-partenaires

Rôles cibles : SUPER_ADMIN, MANAGER, AGENT, ACCOUNTANT, CONCIERGE (existants), CUSTOMER (existant), PARTNER_ADMIN, PARTNER_USER, DRIVER (nouveaux).

| Domaine | Admin | Comptable | Partner admin | Partner user | Driver | Customer |
|---|---|---|---|---|---|---|
| Partenaires | CRUD | Lecture | Son profil | Lecture | — | — |
| Véhicules | CRUD | — | Les siens | Les siens (lecture) | — | Publiés |
| Disponibilité | CRUD | — | Les siens | Les siens | — | Lecture |
| Réservations | Toutes | Lecture | Les siennes | Les siennes (lecture) | Affectées | Les siennes |
| Paiements | Lecture | Lecture | — | — | — | Les siens |
| Finance partenaire | Tout | Tout | Ses relevés | — | — | — |
| Factures | Toutes | Toutes | Ses commissions | — | — | Les siennes |
| Chauffeurs | CRUD | — | — | — | Son profil | — |
| Transferts | Tous | — | — | — | Affectés | Les siens |
| Documents | Tous | — | Les siens | — | Les siens | Les siens |

Règles `[PROPOSITION]` : préfixes `/api/partner/**` et `/api/driver/**` ; `partner_id` et `driver_id` toujours extraits du jeton, jamais d'un paramètre ; aucun rôle partenaire avec `admin:access` ; repositories avec méthodes filtrées par propriétaire (pas de `findById` nu) ; tests d'isolation par endpoint (partenaire A tentant d'accéder à une ressource de B → 404) ; téléversements validés (type, taille, stockage hors racine web) ; CSP et en-têtes de sécurité, `forward-headers-strategy` explicite derrière proxy ; throttle partagé.

## 9. Paiements et finance

Parcours cible : réservation PENDING → validation admin → demande de paiement (acompte ou total `[DÉCISION À VALIDER]`) → capture → confirmation → exécution → retour → clôture financière → reversement partenaire.

Distinctions obligatoires : montant brut client ; commission Elite ; net partenaire ; frais du prestataire ; taxes ; caution (bloquée, hors CA) ; remboursements ; pénalités. Chaque mouvement = ligne du grand livre en ajout seul, avec référence réservation et paiement ; les corrections se font par écriture inverse.

Exigences techniques : appel au prestataire hors transaction et hors verrou ; idempotence par propriétaire et par opération ; webhooks signés et dédupliqués ; réconciliation périodique ; feature flag, le simulateur reste par défaut hors production.

Points juridiques à faire valider (aucun prestataire n'est présenté comme compatible marketplace sans preuve) : statut d'encaissement pour compte de tiers, TVA sur la commission et sur la location, facture au nom de qui, contrats partenaires, assurance et sinistres, statut du chauffeur. `[DÉCISION À VALIDER]`, par un professionnel tunisien.

## 10. Parcours utilisateurs

| Espace | Existant | Manquant | API à créer |
|---|---|---|---|
| Client | Catalogue, réservation, devis, paiement simulé, factures de frais | Recherche par dates et zone, modification/annulation chiffrée, notifications, documents, mot de passe oublié | disponibilité par dates, annulation, documents |
| Partenaire | Rien | Inscription/onboarding, flotte, calendrier, réservations, relevés | `/api/partner/**` |
| Admin | Véhicules, clients, chauffeurs, devis, réservations, utilisateurs | Partenaires, validation de réservation dédiée, paiements, reporting, finance | `/api/admin/partners`, `/payments`, `/payouts`, `/reports` |
| Chauffeur | Affectation côté admin | Portail, planning, statut de trajet, incidents | `/api/driver/**` |

## 11. Backlog priorisé

Priorités : P0 bloquant ; P1 cœur marketplace ; P2 expérience et exploitation ; P3 évolutions. Les critères sont mesurables.

| ID | EPIC | Tâche | Prio | Dépend. | Backend | Frontend | BD | Critère d'acceptation |
|---|---|---|---|---|---|---|---|---|
| T-A01 | Stabilisation | Base de test jetable par exécution + diagnostic `verify12` | P0 | — | scripts | — | — | 3 exécutions `verify` consécutives, 0 échec |
| T-A02 | Stabilisation | Ordre de verrouillage global véhicule → réservation (ISS-01) | P0 | — | BookingService / OperationService | — | — | Test PostgreSQL concurrent retrait ∥ transition : 0 deadlock sur 100 itérations (**constat : `ROUNDS = 20` dans `BookingLockOrderPostgresIT.java`, ligne 38, soit 20 itérations au lieu de 100**) |
| T-A03 | Stabilisation | Ne plus masquer les échecs d'audit (ISS-02) | P0 | — | OperationService | — | — | Test : échec d'audit → opération annulée et erreur explicite |
| T-A04 | Stabilisation | Idempotence et unicité du paiement par devis/réservation (ISS-03, NEW-02) | P0 | — | PaymentService | — | V21 index unique partiel | 2 clés distinctes sur un devis → 1 seul CAPTURED ; test de concurrence |
| T-A05 | Stabilisation | Disponibilité par dates, sans dépendre du statut RENTED (NEW-01) | P0 | « D-02 » (référence orpheline, voir §15 note) | BookingService | — | — | Réservation future acceptée pendant une location en cours ; chevauchement refusé |
| T-A06 | Stabilisation | Épingler Node, job E2E en CI (ISS-10) | P1 | — | — | `engines` | — | CI exécute les E2E et conserve les rapports |
| T-A07 | Stabilisation | Lecture de devis sans écriture (ISS-09) | P2 | — | QuoteService | — | — | `get` en readOnly ; expiration par worker |
| T-A08 | Stabilisation | Pagination `adminList` factures (ISS-05) | P2 | — | InvoiceService | — | — | Réponse paginée, taille max bornée |
| T-B01 | Décisions | Registre des décisions §15 tranché | P0 | — | — | — | — | Décisions bloquantes marquées validées |
| T-C01 | Partenaires | Entité Partner, partenaire interne, rôles PARTNER_* | P0 | T-B01 | partner | — | V22 | Véhicules existants rattachés au partenaire interne ; migration testée sur copie |
| T-C02 | Partenaires | Préfixe `/api/partner/**` + isolation | P0 | T-C01 | SecurityConfig | — | — | Matrice d'isolation : 100 % des endpoints testés A→B refusés |
| T-C03 | Flotte | Rattacher véhicules (partner_id, plaque, caution, publication) | P0 | T-C01 | vehicle | forms | V23 (réalisée : `partner_id`, historique des affectations) | Un partenaire ne voit que ses véhicules |
| T-C04 | Flotte | Documents partenaire et véhicule | P1 | T-C01 | document, storage | upload | Non planifiée ; V24 réalisée = statut ARCHIVED | Type et taille contrôlés ; accès par propriétaire |
| T-D01 | Disponibilité | Table de blocages (maintenance, indisponibilités) | P0 | T-C03 | availability | calendrier | Garanties : V25 (réalisée). Holds : V26 (réalisée). Blocages de maintenance : non implémentés | Blocage empêche réservation ; EXCLUDE cohérent |
| T-D02 | Tarification | Règles de prix (saison, durée, zone) | P1 | T-C03 | pricing | admin | Non planifiée (V26 réalisée = holds) | Devis recalculé identique à la règle, test de table |
| T-D03 | Lieux | Zones géographiques structurées (NEW-04) | P1 | T-B01 | location | sélecteur | V27 | Réservation référence une zone valide |
| T-E01 | Réservation | Étape de validation admin explicite et motifs de refus | P0 | T-C03 | booking | admin | — | PENDING → CONFIRMED uniquement via admin ; motif obligatoire au refus |
| T-E02 | Réservation | Expiration des PENDING (NEW-05) : **abandonnée**, décision métier : aucune expiration automatique des PENDING | — | — | — | — | — | Sans objet ; ne pas implémenter |
| T-E03 | Réservation | Annulation chiffrée | P1 | T-B01 | booking | client | — | Frais calculés selon la règle validée |
| T-F01 | Paiement | Paiement lié à la réservation, remboursements | P1 | T-A04, T-B01 | payment | client | V28 | Remboursement partiel et total testés |
| T-F02 | Paiement | Fournisseur réel derrière feature flag, webhooks signés | P1 | T-F01, prestataire validé | payment | — | V29 | Webhook rejoué n'a aucun effet ; signature invalide rejetée |
| T-G01 | Finance | Commission : règle, snapshot par réservation | P1 | T-B01, T-E01 | finance | admin | V30 | Snapshot immuable ; test de calcul sur table de cas |
| T-G02 | Finance | Grand livre ajout seul | P1 | T-G01 | finance | — | V31 | UPDATE/DELETE refusés (trigger ou droits) ; somme = 0 par réservation |
| T-G03 | Finance | Reversements et relevés partenaire | P1 | T-G02 | finance | partenaire | V32 | Relevé = somme du grand livre ; export vérifié |
| T-H01 | Facturation | Brancher facture depuis réservation payée (ISS-04) | P1 | T-F01, T-B01 | invoice | client | V33 unicité | Une facture par réservation ; numérotation sans repli, fuseau Tunis |
| T-I01 | Transferts | Durée/fin des transferts + exclusion chauffeur (ISS-08) | P1 | T-B01 | transfer | admin | V34 | Chevauchement refusé par la base |
| T-I02 | Chauffeurs | Portail chauffeur | P2 | T-I01, T-C02 | driver | portail | — | Un chauffeur ne voit que ses courses |
| T-J01 | Notifications | Outbox et canal e-mail | P2 | T-E01 | notification | — | V35 | Envoi au moins une fois, dédupliqué |
| T-K01 | Reporting | Tableaux de bord admin et partenaire | P2 | T-G02 | report | dashboards | vues | Chiffres rapprochés du grand livre |
| T-L01 | Plateforme | CSP, en-têtes, `forward-headers-strategy`, throttle partagé (ISS-11) | P1 | — | config | — | — | Tests d'en-têtes ; throttle effectif sur 2 instances |
| T-L02 | Plateforme | Pool Hikari, health checks, sauvegarde/restauration testée | P1 | — | config | — | — | Restauration réussie sur base isolée, rapport conservé |
| T-L03 | Plateforme | Nettoyage migrations inertes (ISS-07) | P3 | décision utilisateur | — | — | — | Uniquement après autorisation explicite |
| T-L04 | Frontend | Routes placeholder : livrer ou masquer (ISS-12) | P2 | — | — | routes | — | Aucune route publique sans contenu réel |

Numéros V21+ indicatifs `[HYPOTHÈSE]` : à recalculer au moment de l'écriture.

**Collision de numéro (Phase 0.3)** : T-C01 indique V22, mais `database/proposed/V22__payments_quote_fk_restrict.sql` existe déjà et réserve ce numéro pour la clé étrangère des paiements (CASCADE vers RESTRICT). T-C01 et les tâches suivantes ne doivent pas utiliser V22 ; leur numéro est à recalculer après V21 et V22. Voir [first-implementation-batch.md](./first-implementation-batch.md) §4.

## 11 bis. Statut réel des tâches

Ajouté lors de l'audit de complétion (voir [project-completion-audit.md](./project-completion-audit.md)). Le tableau §11 ci-dessus reste inchangé : il décrit le backlog cible, pas l'état réel.

Légende : NOT STARTED, IN PROGRESS, BLOCKED, IMPLEMENTED, TESTED, VALIDATED, DEFERRED. Aucune tâche n'atteint TESTED ni VALIDATED : aucune exécution PostgreSQL n'a été reproduite dans cette phase. Les statuts IMPLEMENTED reposent sur du code présent dans le dépôt de travail, non committé.

| ID | Statut | Bloquée par | Preuve observée |
|---|---|---|---|
| T-A01 | IN PROGRESS | — | `scripts/test-backend-postgres.ps1` non suivi, destructif (`DROP DATABASE ... WITH (FORCE)`) ; cause de `verify12` non confirmée |
| T-A02 | IMPLEMENTED | — | `BookingLockOrderPostgresIT.java` non suivi ; `BookingService` / `BookingOperationService` modifiés localement ; résultat `verify13` non reproduit |
| T-A03 | IMPLEMENTED | — | `BookingAuditAtomicityPostgresIT.java` non suivi ; `BookingOperationService` modifié localement |
| T-A04 | IN PROGRESS | — | `PaymentQuoteCapturePostgresIT.java`, `PaymentQuoteInvariantUnitTest.java` non suivis ; `PaymentService` modifié ; V21 non appliquée |
| T-A05 | NOT STARTED | D-02 (référence incohérente, AUD-22) | `BookingService.java` lignes 193–199 : `rentable` exige `VehicleStatus.AVAILABLE` (NEW-01 ouvert) |
| T-A06 | IN PROGRESS | — | Node 24.15.0 épinglé dans `ci.yml` ; aucun job E2E ; `playwright.config.ts` `testDir: './e2e'` |
| T-A07 | IN PROGRESS | — | `QuoteService.list` en `readOnly` ; `QuoteService.get` en transaction d'écriture |
| T-A08 | NOT STARTED | — | `InvoiceService.adminList` ligne 86 : `invoices.findAll()` |
| T-B01 | BLOCKED | Décisions utilisateur D1–D15 | Aucune décision tranchée dans les documents disponibles |
| T-C01 | NOT STARTED | T-B01 | Aucun `partner_id` ni entité partenaire opérationnelle |
| T-C02 | NOT STARTED | T-C01 | Aucun préfixe `/api/partner/**` |
| T-C03 | NOT STARTED | T-C01 | Aucun rattachement véhicule–partenaire |
| T-C04 | NOT STARTED | T-C01 | Pas de documents partenaire |
| T-D01 | NOT STARTED | T-C03 | Pas de table de blocages |
| T-D02 | NOT STARTED | T-C03 | Pas de règles de prix structurées |
| T-D03 | NOT STARTED | T-B01 | Pas de zones structurées |
| T-E01 | IN PROGRESS | T-C03, D10 | Confirmation admin présente (`AdminBookingController` ligne 23) ; motifs de refus absents |
| T-E02 | ABANDONNÉE | — | Décision métier : aucune expiration automatique des PENDING |
| T-E03 | BLOCKED | D3 | Pas de règle d'annulation chiffrée |
| T-F01 | BLOCKED | D3, D5 | Colonnes `refunded_*` présentes sans flux ; pas de lien paiement–réservation |
| T-F02 | BLOCKED | D11, choix du prestataire | `NotificationService` et paiement réel absents |
| T-G01 | BLOCKED | D1 | Pas de commission ni de snapshot (`partners.commission_rate` existe en V1 seulement) |
| T-G02 | NOT STARTED | T-G01 | Pas de grand livre |
| T-G03 | NOT STARTED | T-G02 | Pas de reversements |
| T-H01 | BLOCKED | D6, D12 | Code facture présent mais sans appelant (AUD-07, à re-vérifier) |
| T-I01 | IN PROGRESS | — | Chevauchement chauffeur contrôlé au niveau applicatif (`TransferService` lignes 116, 121–122 ; `TransferRepository` lignes 23–26) ; pas de contrainte en base |
| T-I02 | NOT STARTED | T-I01, T-C02 | Pas de portail chauffeur |
| T-J01 | NOT STARTED | T-E01 | `NotificationService.java` = interface seulement |
| T-K01 | NOT STARTED | T-G02 | Pas de tableaux de bord |
| T-L01 | PARTIALLY IMPLEMENTED (API seule, tests H2 OK) | Phase 1 | En-têtes API et CSP `Report-Only` ajoutés dans `SecurityConfig` ; `SecurityHeadersTest` OK. Pas de `forward-headers-strategy` (`LoginThrottle.java` ligne 22) ; CSP frontend non faite ; throttle en mémoire |
| T-L02 | IN PROGRESS | — | Actuator exposé (`pom.xml` ligne 41, `SecurityConfig` ligne 80) ; Hikari et sauvegarde non documentés |
| T-L03 | DEFERRED | Autorisation explicite | Doublon V11 non traité |
| T-L04 | NOT STARTED | — | Routes placeholder : 12 routes utilisant `page` dans `app.routes.ts` (lignes 42 à 47, 58, 74, 84, 96, 98, 100) |

Compte : NOT STARTED 17 · IN PROGRESS 7 · BLOCKED 6 · IMPLEMENTED 2 · DEFERRED 1 · TESTED 0 · VALIDATED 0.

## 12. Dépendances et ordre d'exécution

Séquentiel : A (T-A01 → T-A05) → B (décisions) → C (partenaires → flotte) → D (disponibilité, prix) → E (réservation) → F (paiement) → G (finance) → H (facturation). Chemin critique (tel qu'écrit dans cette version) : T-B01 → T-C01 → T-C03 → T-D01 → T-E01 → T-F01 → T-G01 → T-G03. **Corrections constatées** : T-D01 n'est pas une dépendance formelle de T-E01 (§11) ; T-G02 est omise alors que T-G03 en dépend (§11). Chemin corrigé proposé : T-B01 → T-C01 → T-C03 → T-E01 → T-F01 → T-G01 → T-G02 → T-G03. Analyse détaillée : [technical-work-reconciliation.md](./technical-work-reconciliation.md).

Parallèle possible : T-A06 à T-A08, T-L01, T-L02 (indépendants) ; T-I01 avec C/D ; T-J01 après T-E01 ; T-L04 à tout moment.

Réorganisation par rapport à l'ordre initial : T-A05 (NEW-01) dépend de T-D01 pour son modèle de blocage final ; une correction minimale intermédiaire est possible `[PROPOSITION]`. Le paiement réel (T-F02) dépend d'un prestataire validé, pas seulement du code.

## 13. Stratégie de tests

| Catégorie | Contenu | Statut |
|---|---|---|
| Rapporté historiquement (logs absents du dépôt) | `verify13` : 164 unitaires/MockMvc + 25 d'intégration PostgreSQL, 0 échec ; E2E Playwright 21 réussis (3 exécutions à 60 s) | Logs `verify13` non retrouvés sur disque : non vérifiable ici, non relancé. Statut `UNKNOWN` dans [technical-work-reconciliation.md](./technical-work-reconciliation.md) |
| Existants non exécutés ici | Toute la suite, CI GitHub réelle | `[À VÉRIFIER]` |
| À créer | Concurrence PostgreSQL (ISS-01, ISS-03, double réservation), isolation partenaires par endpoint, calcul commission (table de cas), grand livre (invariants), webhooks, migrations sur copie de données, E2E parcours partenaire / admin / chauffeur, tests d'en-têtes de sécurité | Backlog |

Règle : tout correctif ISS porte un test de régression ; les tests H2 ne prouvent ni les contraintes ni les verrous, toute règle de base se teste sur PostgreSQL isolé.

## 14. Roadmap (sans calendrier)

| Étape | Objectif | Tâches | Risques | Critères de sortie |
|---|---|---|---|---|
| A Stabilisation | Fiabiliser l'existant | T-A01 à T-A08, T-L01, T-L02 | Régressions de verrouillage | `verify` vert 3 fois, tests de concurrence verts, E2E en CI |
| B Décisions | Lever les blocages métier | T-B01 | Décisions tardives | Décisions bloquantes validées par écrit |
| C Marketplace socle | Partenaires, flotte, disponibilité, réservation | T-C01 à T-C04, T-D01 à T-D03, T-E01 à T-E03 | IDOR, migration des données | Matrice d'isolation 100 %, migration testée sur copie |
| D Argent | Paiement, finance, facturation | T-F01, T-F02, T-G01 à T-G03, T-H01 | Erreurs financières, conformité | Grand livre équilibré, réconciliation, validation juridique |
| E Opérations | Chauffeurs, notifications, reporting, production | T-I01, T-I02, T-J01, T-K01, T-L03, T-L04 | Complexité opérationnelle | Restauration testée, health checks, portail chauffeur isolé |

## 15. Décisions à valider

| ID | Question | Recommandation | Si reportée | Modules | Validateur | Nature |
|---|---|---|---|---|---|---|
| D1 | Taux de commission, global ou par partenaire/catégorie | Taux par partenaire avec défaut global | Pas de snapshot possible | G | Direction | Bloquante (étape D) |
| D2 | Fréquence et méthode de reversement | Mensuel, virement | Pas de payouts | G | Direction, comptable | Bloquante (étape D) |
| D3 | Annulation, remboursement, no-show | Grille par délai | Pas d'annulation chiffrée | E, F | Direction, juridique | Bloquante (T-E03, T-F01) |
| D4 | Cautions | Empreinte carte ou dépôt, hors CA | Pas de caution | C, F | Direction, juridique | Reportable |
| D5 | Acompte, total ou différé | Acompte à la validation | Parcours de paiement indéfini | F | Direction | Bloquante (T-F01) |
| D6 | TVA, factures, fiscalité | Avis d'un expert-comptable tunisien | Facturation non branchée | H | Expert-comptable | Bloquante (T-H01) |
| D7 | Contrats et responsabilités | Contrat-cadre partenaire | Onboarding sans contrat | B | Juridique | Bloquante (production) |
| D8 | Assurances et sinistres | Politique écrite | Risque de litige | C, E | Juridique, assureur | Bloquante (production) |
| D9 | Statut des chauffeurs | Avis juridique/social | Portail chauffeur différé | I | Juridique | Reportable |
| D10 | Conditions de validation / refus d'une réservation | Liste de motifs | Étape admin sans règles | E | Direction | Bloquante (T-E01) |
| D11 | Prestataire de paiement | Compatibilité marketplace à prouver | Paiement réel impossible | F | Direction, prestataire, juridique | Bloquante (T-F02) |
| D12 | Règle de facturation depuis un devis (antérieure) | À trancher avec D6 | ISS-04 non traitée | H | Direction | Bloquante (T-H01) |
| D13 | Exclusion des chauffeurs sur transferts (antérieure) | Durée par défaut puis EXCLUDE | Risque de conflits | I | Direction | Reportable |
| D14 | Notifications : canaux et contenus (antérieure) | E-mail d'abord | Pas de notifications | J | Direction | Reportable |
| D15 | Nettoyage des migrations inertes (ISS-07) | Après sauvegarde, sur autorisation | Confusion persistante | L | Propriétaire du dépôt | Reportable |

**Note de nomenclature (Phase 0.1)** : les identifiants D1 à D15 de ce tableau sont la référence canonique des décisions métier (les tâches T-x y renvoient). Ils ne doivent pas être confondus avec :
- les identifiants `D1`–`D6`, `D5b`, `D-01` du registre V22 ([v22-decision-register.md](./v22-decision-register.md)), qui portent sur la migration V22 ;
- les identifiants `D1`–`D14` de la section 10.4 du [benchmark](./international-benchmark-and-target-architecture.md), qui ne correspondent pas à ce tableau (par exemple benchmark D9 = barème d'annulation, roadmap D9 = statut des chauffeurs).

« D-02 », cité par T-A05, ne correspond à aucune ligne de ce tableau. Voir [business-decisions-pending.md](./business-decisions-pending.md).

## 16. Recommandations finales

1. Ne pas ouvrir la marketplace avant l'étape A et les décisions D1, D3, D5, D6, D10, D11.
2. Garder le monolithe modulaire ; ajouter Redis ou un broker uniquement sur mesure démontrée.
3. Traiter l'isolation partenaire comme une exigence de conception testée endpoint par endpoint, pas comme une revue finale.
4. Séparer strictement le simulateur de paiement des environnements de production.
5. Obtenir une copie de données représentative avant toute migration structurante `[À VÉRIFIER]`.
6. Aucune garantie d'absence de bug n'est donnée ; ce plan réduit des risques identifiés.

## 17. Critères de sortie par grande étape

- **A** : trois `mvnw -Ppostgres-test verify` consécutifs sans échec ; tests de concurrence ISS-01/ISS-03 verts ; E2E exécutés en CI avec rapports conservés ; NEW-01 couvert par test.
- **B** : registre §15 : toutes les décisions « bloquantes » de l'étape suivante validées par écrit.
- **C** : tous les endpoints partenaire testés en accès croisé (refus) ; véhicules existants rattachés au partenaire interne ; migrations V21+ appliquées sur base jetable puis sur copie représentative ; réservation par dates sans régression.
- **D** : paiement idempotent et sans doublon ; grand livre équilibré par réservation ; relevé partenaire = grand livre ; webhooks rejoués sans effet ; validation fiscale et juridique obtenue.
- **E** : planning chauffeur sans chevauchement garanti par la base ; sauvegarde/restauration testée ; health checks, CSP et throttle partagé vérifiés ; aucune route publique placeholder.

Limites : aucun test, build ou serveur n'a été lancé pour rédiger ce document ; les résultats cités viennent de rapports historiques (`verify12`, `verify13`) dont les logs ne sont pas présents sur disque, et des checkpoints de session. Voir [technical-work-reconciliation.md](./technical-work-reconciliation.md) (réconciliation des tests) et [business-decision-analysis.md](./business-decision-analysis.md) (analyse des décisions).
