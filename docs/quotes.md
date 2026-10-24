# Devis et tarification

## Décisions validées

Le 5 octobre 2026, le commanditaire a confirmé :

- `priceWithDriver` est un prix journalier global avec chauffeur. La ligne DRIVER est la différence entre ce tarif et le tarif de location, multipliée par les tranches facturées. Un tarif global inférieur à la location est refusé comme incohérent.
- Chaque version possède sa propre référence DEV unique; les versions sont regroupées par réservation, avec unicité `(booking_id, version)`.
- La règle de l'étape 5 reste inchangée : chaque tranche de 24 heures commencée est facturée. PENDING ne bloque pas; seule la confirmation agence bloque via PostgreSQL.

## Modèle et migration

V5 enrichit la table `quotes` préparée en V1 et renomme `quote_items` en `quote_lines`. Un devis contient référence, booking/client/véhicule, version, statut, devise, sous-total/remise/taxe/total, validité explicite, notes et horodatages d'envoi/réponse. La période, le véhicule affiché et les lieux sont des snapshots.

Les lignes sont ordonnées et conservent type, description, quantité entière, prix unitaire, montant et metadata JSONB. Les types actifs sont VEHICLE_RENTAL, DRIVER, EXTRA_SERVICE, DISCOUNT et TAX. Aucun tarif de transfert/livraison/prise en charge spécifique n'est inventé : un service actif ne peut être choisi que s'il existe réellement dans le référentiel backend, avec sa devise et son prix.

V5 refuse explicitement une base contenant déjà des devis V1 sans lien Booking : cette reprise nécessite un mapping approuvé, pas une conversion silencieuse. Les migrations V1–V4 ne sont pas modifiées. `quote_reference_seq` produit DEV-année-numéro; trous de séquence autorisés, aucun lien avec une facture légale.

## Calcul et snapshots

Booking et Quote réutilisent `RentalPricingService`, sans seconde implémentation de la durée. 24h vaut 1 tranche; 25h et 48h valent 2; 49h vaut 3. Calculs en BigDecimal et NUMERIC(12,3), montants bornés à la capacité des colonnes.

Le sous-total additionne location, différence chauffeur et services. La remise est autorisée uniquement aux rôles `quotes:manage`, non négative et limitée au sous-total. Le total est `subtotal - discountAmount + taxAmount`. Les lignes DISCOUNT sont négatives; les autres sont positives ou nulles. Les quantités/prix/montants sont cohérents par contraintes PostgreSQL.

Le navigateur fournit des identifiants de service et quantités, jamais leurs prix ni un total officiel. La remise est une entrée agence permissionnée et revalidée; les réponses client accept/reject n'autorisent aucun champ financier. Le tarif véhicule doit avoir la devise du booking; les services d'une autre devise sont refusés, sans conversion inventée.

Le devis conserve ses tarifs dès la création, même lors de l'édition du brouillon. Une nouvelle version utilise les tarifs courants. Aucun recalcul n'est effectué sur un devis envoyé/accepté lorsqu'un prix catalogue change. La confirmation Booking reprend les montants du devis accepté, pas les tarifs catalogue devenus différents.

Les taxes sont à zéro par défaut. `QUOTE_DEFAULT_TAX_AMOUNT` prépare un montant fixe configuré, avec `QUOTE_TAX_CURRENCY` (TND par défaut), arrondi HALF_UP à trois décimales. Aucun taux fiscal n'est codé et aucun montant non nul ne doit être configuré sans validation agence/comptable. Une taxe non nulle d'une autre devise est refusée. Ce paramètre n'est pas un système fiscal ni une décision d'exonération.

## Statuts et versions

| Départ | Arrivées autorisées |
| --- | --- |
| DRAFT | SENT |
| SENT | ACCEPTED, REJECTED, EXPIRED, CANCELLED |
| ACCEPTED, REJECTED, EXPIRED, CANCELLED | Aucune |

Seul DRAFT est modifiable. Un brouillon doit être édité plutôt que dupliqué. Une nouvelle version après envoi/refus/expiration/acceptation conserve l'ancienne; une version SENT remplacée devient CANCELLED avec audit. Une version ACCEPTED reste ACCEPTED et inchangée, mais seule la version la plus récente peut soutenir la confirmation finale.

Création de version et actions utilisent le même verrou Booking que les réservations, dans l'ordre véhicule puis booking. La contrainte UNIQUE booking/version complète le verrou. Les requêtes concurrentes sur une version rechargent son statut sous verrou pour éviter deux réponses contradictoires.

## Validité

L'agence fournit `validUntil` explicitement, sans durée commerciale par défaut. ISO-8601 avec offset, Instant/TIMESTAMPTZ et précision maximale microseconde; affichage dans BOOKING_TIME_ZONE comme les réservations.

Un SENT arrivé à échéance devient EXPIRED, avec historique et audit système. Vérification à l'accès et à la réponse client, plus worker borné à 100 devis par passage (`QUOTE_EXPIRATION_SCAN_MS`, défaut 60000 ms, fréquence technique et non validité commerciale). Le refus d'acceptation expirée ne rollback pas l'événement d'expiration. Les listes peuvent conserver SENT jusqu'au passage du worker; le détail et l'acceptation vérifient immédiatement l'échéance.

Un devis accepté avant échéance reste accepté; sa validité n'est pas une expiration de réservation. La confirmation finale exige toujours des dates futures et la cohérence de la demande.

## Actions client et agence

Le client lit seulement ses propositions publiées, accepte/refuse SENT, ou enregistre une demande de modification avec message facultatif. Cette dernière crée un événement QUOTE_CHANGE_REQUESTED et ne modifie pas le devis ni le montant; l'agence prépare ensuite une nouvelle version.

L'acceptation vérifie propriétaire, statut, échéance, version courante, Booking PENDING, période/lieux/devise cohérents et disponibilité. Elle ne confirme rien et ne déclenche ni paiement ni facture. L'agence confirme ensuite via le moteur Booking existant, qui revérifie la disponibilité dans sa transaction et conserve l'exclusion PostgreSQL des intervalles bloquants. Deux devis acceptés peuvent coexister sur un véhicule; une seule confirmation chevauchante peut réussir.

Une modification des lieux du booking après envoi nécessite une nouvelle version cohérente avant acceptation/confirmation. En l'absence de devis, les confirmations de l'étape 5 restent disponibles. Dès qu'un devis existe, sa dernière version doit être acceptée avant confirmation.

## Sécurité et audit

Permissions : quotes:read/create/manage/send/accept/reject. SUPER_ADMIN, MANAGER et AGENT créent/gèrent/envoient; CONCIERGE/ACCOUNTANT lisent seulement. CUSTOMER lit/accepte/refuse ses seuls devis et demande une modification. Le client ne peut pas créer un devis, imposer propriétaire/référence/version/taxe/remise/prix unitaire/total. Les notes internes sont absentes des DTO client.

Les mutations utilisent JWT/principal/RBAC et CSRF existants. Un autre propriétaire reçoit 404. Les événements et l'audit des actions QUOTE_CREATED/UPDATED/SENT/ACCEPTED/REJECTED/EXPIRED/CANCELLED/CHANGE_REQUESTED sont dans la transaction; l'audit ne copie pas les notes ni les messages du client. Ces messages restent seulement dans l'historique autorisé de son devis.

Erreurs métier au format ProblemDetail avec code : QUOTE_NOT_FOUND (404), QUOTE_EXPIRED, QUOTE_IMMUTABLE, INVALID_QUOTE_TRANSITION, QUOTE_SUPERSEDED, QUOTE_DRAFT_EXISTS, QUOTE_BOOKING_CHANGED, QUOTE_BOOKING_INVALID, QUOTE_PRICE_INVALID, QUOTE_CURRENCY_MISMATCH, QUOTE_DRIVER_UNAVAILABLE et QUOTE_SERVICE_INVALID (409). Validation de format/champs : 400; permissions : 401/403. Aucune stack trace n'est affichée.

## Performance et architecture future

Les listes sont paginées et ne chargent pas toutes les lignes/historiques. Unicité référence et booking/version fournissent leurs index; V5 ajoute client/date, statut/validité, date de création et historique devis/date. L'unicité quote/position indexe les lignes ordonnées.

EXPLAIN ANALYZE sur la recette PostgreSQL utilise quotes_customer_created_idx pour la liste client (~0,119 ms), quotes_status_valid_until_idx pour l'expiration (~0,027 ms) et quote_lines_position_unique pour les lignes (~0,064 ms). Ce sont des mesures de petite base de test, pas un benchmark de production. La recherche de référence par sous-chaîne n'est pas couverte par un index trigram non justifié.

NotificationService est seulement un contrat futur. « Envoyer » publie la proposition dans l'espace client : aucun e-mail/SMS/push n'est délivré. QuoteDocumentService prépare un rendu futur depuis le snapshot persisté; aucun export PDF ni facture légale n'est implémenté et aucun faux bouton de téléchargement n'est affiché.