# 14 — Stratégie de transition et risques sur données historiques (Mission 6)

> **Limite majeure.** Aucune copie représentative de la base de production ou de données historiques n'est disponible. Tous les effets sur les lignes existantes sont **non vérifiés**. Aucune migration n'est créée ni exécutée. Aucune base réelle n'est touchée.

## 1. État des données historiques (constat code)

| Table | Constat | Source |
|---|---|---|
| vehicles | Aucun partner_id ; aucun état archivé | V1 |
| partners | Table présente, pas d'entité Java, commission_rate hérité | V1 |
| bookings | Pas de référence à une garantie | V4 |
| payments | quote_id CASCADE ; booking_id SET NULL | V6 |
| transfers | booking_id RESTRICT ; contraintes NOT VALID | V19 |
| migrations | V1 à V20 dans `database/migrations` ; V21 et V22 proposées, non appliquées | Vérifié (hash) |

## 2. Risques sur données historiques

| # | Risque | Probabilité | Impact | Mitigation proposée |
|---|---|---|---|---|
| H1 | Véhicules sans partenaire courant après ajout de `vehicles.partner_id` | Élevée | Bloquant pour transfert | Ajout NULL d'abord ; backfill par décision propriétaire ; NOT NULL seulement après backfill vérifié |
| H2 | Réservations existantes sans garantie | Certaine | Faible si booking_model=LEGACY | Référence NULL autorisée pour l'historique ; contrôle service pour les nouvelles uniquement |
| H3 | Contrainte CHECK forçant availability_id sur ancienne ligne | Moyenne | Migration en échec | CHECK ajouté NOT VALID, puis validé après contrôle |
| H4 | Chevauchements historiques de données importées | Inconnue | EXCLUDE en échec | Requête de détection avant création de contrainte (lecture seule) |
| H5 | CASCADE sur payments.quote_id | Certaine (schéma) | Perte financière lors suppression de devis | Appliquer V22 après revue ; interdire suppression de devis payé |
| H6 | Véhicules en MAINTENANCE avec réservations CONFIRMED futures | Inconnue | Clients sans véhicule | Rapport lecture seule avant activation D-PA-16 |
| H7 | Partenaires désactivés avec véhicules actifs | Inconnue | Engagements orphelins | Rapport lecture seule ; décision D-PA-17 |
| H8 | Données de test en base locale | Élevée | Faux positifs | Ne jamais utiliser la base locale comme référence |

## 3. Stratégie de transition (proposition, non validée)

Étapes, toutes non destructives :

1. **Audit lecture seule** sur une copie isolée : comptage des véhicules, réservations, paiements, partenaires désactivés, chevauchements. Aucun ordre d'écriture.
2. **Décision propriétaire** sur le backfill de `vehicles.partner_id` (quel partenaire pour chaque véhicule existant ; ou « aucun partenaire » temporairement).
3. **Migration additive** (future V23) : ajouter colonnes nullables, nouvelles tables, index. Pas de suppression.
4. **Backfill contrôlé** : script idempotent, journalisé, exécuté sur copie isolée d'abord.
5. **Contraintes** : CHECK NOT VALID, puis VALIDATE après contrôle. NOT NULL seulement si backfill complet.
6. **Activation applicative** : feature flag ou déploiement séparé ; le backend commence à exiger availability_id pour les nouvelles réservations.
7. **Rollback** : la migration additive est réversible par ajout de contraintes de désactivation ; ne pas supprimer de colonnes en V23.

## 4. Sauvegarde et restauration

- Sauvegarde logique `pg_dump` avant toute migration, sur environnement isolé. **Non exécutée.**
- Restauration testée sur une base vide à partir de la sauvegarde. **Non exécutée.**
- Procédure à valider par le propriétaire avant V23.

## 5. Ce qui est interdit

- Supprimer une garantie, un rattachement ou une réservation historique.
- Réécrire le partenaire porteur d'une garantie existante.
- Utiliser `partners.commission_rate`.
- Appliquer V21 ou V22 sur la base locale réelle sans revue.

## 6. Préconditions avant V23

| Précondition | Statut |
|---|---|
| Copie représentative des données | Non disponible |
| Décision backfill partner_id | Ouverte |
| Décision D-PA-14 (règle de chevauchement) | Ouverte (identifiant validé) |
| Procédure de sauvegarde et restauration validée | Non vérifiée |
| Tests PostgreSQL isolés (EXCLUDE, RESTRICT) | Non exécutés |
