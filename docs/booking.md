# Réservations et disponibilité

## Décisions validées

Le 5 octobre 2026, le commanditaire a choisi :

- `PENDING` ne bloque pas la flotte. Une demande reste en attente sans expiration automatique. Deux demandes concurrentes peuvent être enregistrées; une seule peut être confirmée sur une même période.
- Chaque tranche de 24 heures commencée est facturée au tarif journalier. Il n'y a pas de durée minimale commerciale, de réduction hebdomadaire automatique ni de supplément inventé.
- Le début doit être futur lors de la demande, du récapitulatif et de la confirmation. La fin doit suivre strictement le début.

Le critère initial « une création réussie, une refusée » est donc adapté au choix explicite `PENDING` non bloquant : **une confirmation réussie, une confirmation refusée**. Cette différence est volontaire, pas une garantie de blocage à la création.

## Modèle

`BookingEntity` référence un client et un véhicule; contient `reference`, les deux instants, les lieux, statut, nombre de jours, tarif journalier figé, prix de base/frais/remise/taxe/total/devise, notes client/interne, dates de création/modification/confirmation/annulation. Les montants sont `BigDecimal` et `NUMERIC(12,3)`.

Les références sont générées exclusivement côté serveur par `booking_reference_seq` et préfixées par l'année courante du fuseau métier : `ET-2026-000001`. L'unicité est imposée en base. La séquence globale n'est pas remise à zéro chaque année; des trous après rollback sont normaux et ce n'est pas une numérotation de facture.

`booking_events` expose un historique de transitions; `audit_logs` conserve acteur, action, entité réservation, horodatage, IP et résultat. Les mutations réussies et leur audit sont dans la même transaction. Aucun mot de passe, token ou contenu des notes n'est copié dans l'audit.

## Statuts

| Départ | Arrivées autorisées |
| --- | --- |
| PENDING | CONFIRMED, REJECTED, CANCELLED |
| CONFIRMED | IN_PROGRESS, CANCELLED |
| IN_PROGRESS | COMPLETED |
| REJECTED, CANCELLED, COMPLETED | Aucune |

Le client annule seulement ses réservations `PENDING` ou `CONFIRMED`. Aucun remboursement n'est effectué. L'agence démarre/termine les réservations via des actions explicites; il n'y a pas de changement automatique piloté par calendrier. Une seconde annulation renvoie `BOOKING_ALREADY_CANCELLED`.

## Temps et prix

Les API utilisent ISO-8601 avec offset (`Z` ou offset explicite), converti en `Instant`. PostgreSQL stocke des `TIMESTAMPTZ`; les comparaisons portent sur les instants, jamais sur des heures locales non qualifiées.
La précision API est limitée à la microseconde (six décimales de seconde), comme PostgreSQL. Une précision supérieure est refusée pour éviter de calculer un prix sur une fraction de temps perdue lors de la persistance.

Le fuseau métier vient de `BOOKING_TIME_ZONE` (défaut `Africa/Tunis`) et est fourni par `/api/bookings/policy` et les DTO. Angular utilise Temporal pour convertir `datetime-local` dans ce fuseau vers UTC, avec rejet des heures ambiguës/inexistantes lors d'un changement DST. L'affichage utilise `Intl.DateTimeFormat` avec le fuseau métier, indépendamment du fuseau du navigateur.

Le prix de base est `ceil(durée / 24 heures) × tarif journalier`. Exactement 24 heures vaut un jour; 24 heures et une seconde vaut deux jours. Les montants envoyés par le navigateur sont ignorés; le backend recalcule lors de la création et fige le tarif. Le récapitulatif est indicatif : une disponibilité peut changer avant l'envoi ou la confirmation.

Les frais supplémentaires, remise et taxe sont à zéro pour cette étape. Cela ne définit pas le régime fiscal de l'agence. Aucun catalogue d'options tarifées n'a été validé; `/api/bookings/policy` renvoie une liste d'options vide, sans faux supplément ni chauffeur opérationnel.

## Chevauchement et concurrence

Les intervalles sont demi-ouverts `[début, fin)`. Le chevauchement est `débutA < finB AND finA > débutB`; deux périodes adjacentes sont autorisées.

Seuls `CONFIRMED` et `IN_PROGRESS` bloquent. Le statut administratif du véhicule reste distinct : un véhicule non `AVAILABLE`, une marque inactive ou une catégorie inactive empêchent une demande/confirmation, même sans réservation temporelle.

Les opérations suivent un ordre de verrouillage constant : ligne véhicule (`SELECT ... FOR UPDATE`), puis ligne réservation pour une mutation existante. Le statut est rafraîchi après acquisition du verrou pour éviter une décision basée sur le premier chargement JPA. Création et confirmation revérifient les conflits dans leur transaction.

La migration V4 ajoute `bookings_no_blocking_overlap`, une exclusion GiST sur `vehicle_id` et `tstzrange(starts_at, ends_at, '[)')`, partielle sur les deux statuts bloquants. Elle garantit l'intégrité même pour des écritures SQL contournant les services. SQLSTATE `23P01` est transformé en `409 VEHICLE_NOT_AVAILABLE`; aucune stack trace n'est retournée.

`btree_gist` doit être installable par le rôle de migration ou préparée par le DBA. Les tests ont utilisé PostgreSQL 18.3 UTF-8 jetable, jamais la base permanente. Deux tests synchronisent réellement deux transactions avec une barrière : confirmations par service et écritures SQL directes. Dans chaque cas une transaction réussit et l'autre est refusée. H2 n'est pas une preuve pour ces contraintes.

## Sécurité

- `POST /api/bookings` prend le client du principal authentifié, pas du JSON. Référence, statut et prix sont imposés par le serveur.
- Les routes client exigent `CUSTOMER` et la permission correspondante. Lecture/annulation recherchent directement par `(bookingId, customerId)`; un autre client reçoit 404 pour limiter l'énumération.
- Les notes internes ne sont jamais incluses dans les DTO client. Les mutations agence autorisées se limitent aux lieux et à la note interne; propriétaire, période et montants ne sont pas éditables dans cette étape.
- Les routes admin exigent `admin:access` puis `bookings:read/manage/cancel`. Seuls super-admin, manager et agent ont l'écriture; concierge/comptable lisent seulement.
- Les mutations utilisent la protection CSRF existante. Availability et policy sont publiques sans données d'autres clients. Le récapitulatif public exige aussi CSRF.

## Erreurs

| Code | HTTP | Comportement |
| --- | --- | --- |
| INVALID_DATE_RANGE | 400 | Dates absentes, passées ou fin non postérieure |
| VEHICLE_NOT_FOUND | 404 | Véhicule inconnu |
| VEHICLE_NOT_AVAILABLE | 409 | Statut administratif ou conflit temporel |
| BOOKING_NOT_FOUND | 404 | Réservation absente ou n'appartenant pas au client |
| BOOKING_ACCESS_DENIED | 403 | Absence de profil client |
| INVALID_STATUS_TRANSITION | 409 | Action impossible dans l'état courant |
| BOOKING_ALREADY_CANCELLED | 409 | Annulation répétée |

Les erreurs d'authentification/permission restent 401/403; les dates malformées sont des erreurs de format 400. Une réponse de disponibilité négative renvoie `available:false` et une raison (`VEHICLE_ALREADY_BOOKED` ou `VEHICLE_NOT_AVAILABLE`), sans identifiant ni information d'une réservation concurrente.

## Recherches et index

`GET /api/vehicles/available` conserve les filtres/tri/pagination du catalogue et ajoute une sous-requête `NOT EXISTS` sur les réservations bloquantes. Le backend ne récupère pas toutes les réservations pour filtrer en Java.

Les listes sont paginées et stables. Les filtres agence acceptent référence (sous-chaîne), identifiants client/véhicule, statut et bornes du **début** de réservation (`startDate` inclusif, `endDate` exclusif), exprimées en instants UTC.

V1 fournit déjà `(vehicle_id, starts_at, ends_at)` et `(customer_id, created_at DESC)`. L'unicité de référence et l'exclusion créent leurs propres index. V4 ajoute seulement `(status, starts_at)` pour les recherches agence et `(booking_id, created_at)` pour l'historique; aucun doublon d'index client n'est ajouté.

`EXPLAIN (ANALYZE, BUFFERS)` sur la base de recette a utilisé `bookings_no_blocking_overlap` pour le conflit (environ 1,3 ms) et `idx_bookings_customer_created` pour la liste client (environ 0,045 ms). Ces mesures sur peu de données ne constituent pas un benchmark de production. La recherche de référence avec wildcard initial peut nécessiter un scan; aucun index trigram non justifié n'est créé.

## Hors périmètre

L'étape 6 ajoute les devis commerciaux décrits dans [quotes.md](quotes.md). Lorsqu'un devis existe, la dernière version doit être acceptée et cohérente avant confirmation agence; ses montants snapshot sont repris après recontrôle de disponibilité. Sans devis, le parcours de l'étape 5 conserve son prix de base.

Pas de paiement, facture légale, remboursement, SMS, notifications livrées ou chauffeur opérationnel. L'extension et la disponibilité sont validées sur PostgreSQL jetable; la base permanente, la reprise des données historiques, les règles commerciales/fiscales et le déploiement restent à recetter.