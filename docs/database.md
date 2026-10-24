# Base de données

PostgreSQL 18 est la cible. Flyway V1 (schéma), V2 (utilisateurs), V3 (catalogue), V4 (booking_engine) et V5 (quotes) sont copiées au classpath par Maven. Hibernate utilise ddl-auto:validate et ne crée pas le schéma en production.

## Relations principales

- `users` référence un rôle; `customers` et `employees` sont des profils rattachés à un utilisateur.
- `vehicles` référence une marque et une catégorie; images et caractéristiques dépendent du véhicule.
- V3 ajoute descriptions, slugs, statuts actifs et horodatages aux marques/catégories; enrichit les véhicules avec année, capacité, transmission, carburant, statuts et tarifs journaliers/hebdomadaires/chauffeur en `NUMERIC(12,3)`.
- V3 normalise les métadonnées d'images (`url`, `alt_text`, `position`, `is_primary`) et les caractéristiques en lignes ordonnées, avec index d'accès et unicité d'image principale par véhicule. Les binaires image sont dans le stockage local configuré par `STORAGE_LOCAL_PATH`, pas dans PostgreSQL.
- V3 ajoute les permissions `vehicles:read/manage`, `brands:read/manage` et `categories:read/manage`; les rôles internes les reçoivent selon leur périmètre.
- `bookings` référence un client, un véhicule et éventuellement un chauffeur; `booking_services` associe les services au prix enregistré lors de la réservation.
- `quotes` appartient à un client et comporte des lignes `quote_items`.
- `payments` et `invoices` référencent une réservation.
- `notifications`, `documents` et `audit_logs` conservent leurs liens vers l'utilisateur ou le domaine concerné.
- `refresh_tokens` ne stocke que le hash SHA-256 du secret opaque; révocation et expiration sont persistées.

Des index initiaux couvrent la recherche des réservations par véhicule/période, les listes client et les notifications. Ils ne constituent pas encore une règle transactionnelle anti-chevauchement : le service de réservation et ses contraintes seront ajoutés avec ce domaine.

Les tests d'intégration rapides utilisent H2. En complément, Flyway V1–V3 et Hibernate `ddl-auto:validate` ont été exécutés sur PostgreSQL 18 temporaire vierge; cette validation ne correspond pas à une installation ou une recette sur la base permanente de l'utilisateur.

V3 ne portait pas les règles de disponibilité. V4 enrichit réellement `bookings`, ajoute les références serveur uniques via séquence, les tarifs figés, lieux/notes/horodatages, le journal `booking_events` et les permissions réservation. Le statut véhicule AVAILABLE reste un état administratif, distinct de sa disponibilité temporelle.

V4 installe `btree_gist` et impose une exclusion GiST des intervalles `[starts_at, ends_at)` pour les statuts CONFIRMED/IN_PROGRESS. Un rôle de migration autorisé à installer l'extension ou une préparation DBA est nécessaire. PENDING ne bloque pas et n'expire pas automatiquement, conformément au choix validé.

Les index véhicule/période et client/date de V1 sont réutilisés; V4 ajoute statut/début et réservation/date d'événement. Référence UNIQUE et exclusion créent leurs propres index. Aucun index client dupliqué n'est ajouté. Stratégie transactionnelle et analyse EXPLAIN : [booking.md](booking.md).

V5 enrichit quotes et renomme quote_items en quote_lines : booking, version, référence unique, devise, période/lieux/véhicule snapshot, validité TIMESTAMPTZ, montants NUMERIC(12,3) et metadata JSONB des lignes. Unicité booking/version et quote/position; contraintes sur états et cohérence des montants. Quote events trace le cycle. Le référentiel services reçoit une devise explicite (TND par défaut pour les anciennes lignes, à valider avant usage commercial).

V5 refuse une table quotes préexistante non vide sans mapping Booking approuvé : aucune reprise arbitraire des devis historiques. Référence UNIQUE et booking/version fournissent leurs index; ajouts client/date, statut/validité, création et historique. Les listes ne chargent pas toutes les lignes. Plans et règles : [quotes.md](quotes.md).