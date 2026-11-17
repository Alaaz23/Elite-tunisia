# 05 — Feuille de route d'implémentation finale

Statut : plan documentaire. **Aucun lot n'est autorisé à démarrer** sans validation explicite du propriétaire.

## 1. Lots

| Lot | Contenu | Fichiers potentiellement concernés | Dépendances | Prérequis métier | Risques | Tests | Critères d'acceptation |
|---|---|---|---|---|---|---|---|
| L0 | Corrections documentaires (doc 01, C2, C4, C5, C12) | `docs/architecture/partner-availability/*` | Aucune | Aucun | Faible | Aucun (documentaire) | Documents cohérents sur D5, D-PA-08, cycle de vie |
| L1 | Fondations partenaires : activer le modèle `partners` (sans commission) | `database/proposed/` (nouvelle V), entité `PartnerEntity`, service admin | L0, D-PA-02, D-PA-13 | Statut partenaire | Données historiques | H2 CRUD, isolation | Partenaire créé, désactivé, aucun impact sur réservations existantes |
| L2 | Association véhicule/partenaire en trois temps | Migration (nullable puis NOT NULL), `VehicleEntity`, `VehicleService` | L1, rattachement de transition validé | Partenaire de transition pour véhicules existants | **P0** : véhicules historiques sans partenaire | H2 + revue migration (PostgreSQL non exécuté) | Aucun véhicule sans partenaire après migration ; création exige un partenaire |
| L3 | Disponibilités garanties : table, service, EXCLUDE | Migration, `guaranteed_availabilities`, service dédié (module `availability`) | L2, D-PA-01, D-PA-14 (chevauchement), D-PA-09 ; D-PA-05 validé | Durée, période, expiration | Dépendance circulaire (C2) | H2 : chevauchement, retrait, expiration, changement de partenaire du véhicule. Test PostgreSQL à faire ailleurs | Pas de garanties chevauchantes ; historique conservé ; partenaire porteur figé |
| L4 | Recherche publique par dates | `VehicleSpecification.availableDuring`, contrôleur public, Angular catalogue | L3, D-PA-08 | Visibilité | Performance de recherche | Tests d'API, H2 | Seuls les véhicules garantis et libres apparaissent |
| L5 | Intégration réservations : contrôle de couverture, PENDING, R-3 à R-5 | `BookingService`, `VehicleService` | L3, D-PA-04, D-PA-07 | PENDING, retrait | Ordre de verrouillage | H2 : scénarios R-1 à R-8 | Confirmation refusée hors garantie ; retrait refusé si CONFIRMED |
| L6 | Sécurité et isolation partenaire (`/api/partner/**`, contrôles backend) | Contrôleurs partenaire, `SecurityConfig` | L1, L2 | D-PA-06 | Fuite de données | Tests d'autorisation par route | Partenaire A ne voit ni ne modifie B |
| L7 | Interfaces Angular : admin garanties, partenaire, client dates | `frontend/src/app/features/*` | L3, L4, L6 | Parcours validés | Régression UI | Tests unitaires et e2e (à exécuter) | Parcours complets |
| L8 | Tests et validation | Tests H2, Playwright, PostgreSQL isolé | Tous | Environnement PostgreSQL isolé | Environnement non disponible ici | Suite complète | Preuves exécutées, pas déclarées |
| L9 | Correction MAINTENANCE (écart C1) | `VehicleService.requireStatusChangeAllowed` | Accord explicite | Confirmation de la règle | Changement de comportement | Tests unitaires et H2 | MAINTENANCE refusée si CONFIRMED futur |
| L10 | Paiement et acompte (D5, V21/V22) | `PaymentService`, migrations V21, V22 | D5, D3 | D5 tranchée | Double comptabilisation | Tests paiement isolés | Invariant révisé et testé |

## 2. Ordre recommandé

1. L0 (documentation), sans attendre de décision.
2. L9 (MAINTENANCE), si le propriétaire confirme la règle. C'est un correctif de sécurité métier, indépendant du module garanties.
3. Décisions D-PA-01, D-PA-14 (chevauchement, à renuméroter), D-PA-07, D-PA-04, D5. (D-PA-05 validé.)
4. L1, puis L2 après validation du rattachement de transition.
5. L3, puis L4 et L5 en parallèle.
6. L6, puis L7.
7. L8 à chaque lot, puis sur l'ensemble.
8. L10 seulement après D5.

## 3. Ce qui est bloqué

- L2 : rattachement des véhicules existants (décision).
- L3 : D-PA-01, D-PA-14.
- L5 : D-PA-04, D-PA-07.
- L10 : D5, D3.
- Toute migration : autorisation explicite.

## 4. Vérifications exigées pour déclarer un lot réussi

- Compilation Maven (JDK 21) : preuve par sortie.
- Tests H2 ciblés : preuve par sortie (nombre de tests, échecs).
- Tests PostgreSQL : sur base isolée uniquement, après autorisation. Non exécutés dans ce cycle.
- Revue de migration : preuve par script et sortie de dry-run sur base isolée.

Aucun de ces points n'est déclaré réussi dans cette mission.
