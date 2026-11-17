# Mission 8 — Feuille de route d'implémentation

Ordre fondé sur les dépendances réelles du code et de la base. Chaque étape est un lot livrable et testable. Aucune étape n'est réalisée par la présente mission.

## Étape 0 — Préparation sûre (prérequis)
- **Prérequis** : copie de test isolée de la base (sauvegarde + restauration vérifiées) ; environnement PostgreSQL de test sur 15432 explicitement autorisé ; JDK 21 confirmé (`java -version` = 21.0.2, constaté).
- **Livrables** : procédure de sauvegarde/restauration documentée ; décision sur le dossier `src/.../db/migration` trompeur (documenter, ne pas supprimer).
- **Risque** : ne pas tester sur 5432.
- **Critère** : restauration d'une copie réussie et journalisée.

## Étape 1 — Correctifs bloquants de cohérence (A3, A4)
- **A3** : `VehicleService.requireStatusChangeAllowed` refuse MAINTENANCE si CONFIRMED futur (D-PA-16).
- **A4** : valider V22 (RESTRICT) sur copie ; vérifier V21 sur copie avant application.
- **Prérequis** : Étape 0.
- **Tests** : intégration PostgreSQL (maintenance refusée ; suppression de devis avec paiement refusée).
- **Critère** : tests verts sur 15432 ; migrations non appliquées sur base réelle.

## Étape 2 — Fondations partenaires (A1, B1, D-PA-15, D-PA-17)
- Migration additive V23 : table `partners` existante étendue si nécessaire, `vehicle_partner_assignments` (historique, un rattachement actif par véhicule).
- Entité `Partner` et service dans le package `partner`.
- API admin : création, désactivation, rattachement. Pas de partenaire attribué automatiquement aux véhicules historiques (règle de la mission).
- **Prérequis** : Étapes 0 et 1.
- **Risque** : véhicules historiques sans partenaire ; traitement manuel, documenté.
- **Tests** : unitaires (enum, désactivation) ; intégration (un seul rattachement actif).
- **Critère** : un véhicule rattaché apparaît dans l'historique ; un partenaire désactivé ne peut plus recevoir de garantie.

## Étape 3 — Garanties de disponibilité (D-PA-01, D-PA-14, D-PA-07)
- Table `guaranteed_availabilities`, CHECK 90 jours, EXCLUDE GIST sur ACTIVE, index.
- API admin : création, retrait (refusé si réservation CONFIRMED ou IN_PROGRESS).
- Catalogue public : ne montre que les véhicules avec garantie active.
- **Prérequis** : Étape 2.
- **Risque** : concurrence sur la création ; verrou sur le véhicule puis contrainte EXCLUDE.
- **Tests** : intégration des chevauchements concurrents ; retrait refusé ; expiration à 90 jours.
- **Critère** : seul un véhicule garanti est visible ; aucun chevauchement accepté.

## Étape 4 — Moteur de disponibilité et holds (A2, D-PA-04)
- Colonne `hold_expires_at`, prise en compte des PENDING valides, libération à expiration.
- Liaison `bookings.availability_id` (obligatoire).
- **Prérequis** : Étape 3.
- **Risque** : expiration pendant confirmation ; verrou sur le véhicule.
- **Tests** : deux holds concurrents ; expiration à 30 minutes ; confirmation après expiration refusée.
- **Critère** : deux clients ne peuvent pas tenir le même créneau.

## Étape 5 — Réservation et paiement simulé
- Parcours : recherche par dates → hold PENDING → paiement simulé intégral → CONFIRMED.
- Refus de transfert partenaire si engagements (D-PA-12) : nécessite l'historique de l'Étape 2.
- **Prérequis** : Étapes 2 à 4.
- **Tests** : intégration du parcours complet ; idempotence ; paiement capturé unique par devis (V21).
- **Critère** : paiement rejoué sans double capture.

## Étape 6 — Administration Elite
- Remplacer les placeholders `/admin/partners`, `/admin/payments` par les écrans réels.
- Suivi des garanties, réservations et holds.
- **Prérequis** : Étapes 2 à 5.
- **Tests** : unitaires de composants ; E2E des parcours administrateur.

## Étape 7 — Espace partenaire en lecture seule (D-PA-18)
- Rôle `PARTNER`, routes `/api/partner/**`, filtrage par `partner_id` issu du contexte d'authentification.
- Front : module `partner` en lecture seule.
- **Prérequis** : Étapes 2 et 3.
- **Risque** : accès croisé ; à couvrir par tests de sécurité.
- **Tests** : intégration (accès à un autre partenaire refusé) ; E2E lecture.

## Étape 8 — Parcours client complet et tests de bout en bout
- Site client : recherche, réservation, paiement simulé, espace client.
- Tests : PostgreSQL d'intégration (15432) ; E2E Playwright sur le parcours complet.
- **Prérequis** : Étapes 1 à 7.
- **Critère** : parcours démontrable sans intervention technique.

## Ordre et justification

Les étapes 1 (corrections) et 2 (partenaires) sont prioritaires car elles touchent l'intégrité des données ; elles dépendent seulement de l'étape 0. La disponibilité (3, 4) ne peut être écrite avant les partenaires et les garanties. Le paiement (5) dépend des holds. L'administration et l'espace partenaire (6, 7) consomment ces données. Les tests E2E (8) closent le lot.

## Points de vigilance transverses
- Ne jamais attribuer automatiquement un partenaire à un véhicule historique.
- Ne jamais déclarer une étape terminée sans test exécuté et journalisé.
- Toute migration : additive, Flyway, testée sur copie.
