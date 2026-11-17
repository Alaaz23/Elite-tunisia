# 11 — Matrice de scénarios métier (Mission 6)

> **Statut : scénarios de conception.** Les règles citées comme « validées » proviennent de `review/08-owner-decisions-validation-record.md`. Toute autre règle est une **recommandation non validée**, à approuver par le propriétaire (voir `review/10-mission6-business-decisions-proposal.md`).

Légende des règles : **V** = validée propriétaire ; **R** = recommandation non validée ; **O** = ouvert, comportement à décider (le scénario ne doit pas être implémenté tel quel).

| # | Scénario | État initial | Action | Vérifications | Résultat attendu | Règle | Risque si mal géré |
|---|---|---|---|---|---|---|---|
| S01 | Création d'une garantie | Véhicule V sans garantie sur la période ; partenaire A actif | Admin enregistre garantie A, 10–20 juin | Partenaire A autorisé à proposer V ; dates valides ; pas de chevauchement | Garantie ACTIVE, partenaire porteur = A, admin et date de confirmation enregistrés | V (D-PA-05), R (D-PA-14 option A) | Garantie sans partenaire porteur |
| S02 | Chevauchement de garanties | Garantie A ACTIVE 10–20 juin | Admin crée garantie B 15–25 juin | Chevauchement sur le même véhicule | Refus avec code explicite | R (D-PA-14, O sur la règle finale) | Double engagement |
| S03 | Garantie non autorisée | Partenaire B n'est pas le partenaire courant de V | B tente une garantie sur V | Autorisation partenaire/véhicule | Refus | V (D-PA-05 : vérifier l'autorisation) | Garantie d'un véhicule non possédé |
| S04 | Changement de partenaire après garantie | Garantie A ACTIVE ; V chez A | Transfert V vers B | Garantie active couvrant ou suivant la date | Transfert refusé | V (D-PA-12) | Garantie reportée sur B |
| S05 | Transfert sans engagement | V chez A ; aucune garantie ni réservation future | Transfert vers B, admin, motif, date | Engagements vides ; partenaire courant = A | Ligne de rattachement fermée pour A, nouvelle ouverte pour B | V (D-PA-12, historique) | Perte de traçabilité |
| S06 | Historique après transfert | S05 effectué | Consulter une ancienne garantie | Partenaire porteur | Toujours A | V (D-PA-05) | Réécriture rétroactive |
| S07 | Transfert et garantie historique | Garantie A WITHDRAWN ou EXPIRED passée | Transfert vers B | Garanties historiques exclues du contrôle | Transfert autorisé, historique intact | R (D-PA-12 : historique non bloquant) | Blocage injustifié |
| S08 | Réservation PENDING sans garantie | V disponible, pas de garantie | Client demande V | Garantie couvrant la période | Refus de création (règle garantie obligatoire) | V (référence obligatoire) | Réservation non garantie |
| S09 | Réservation CONFIRMED avec garantie | Garantie A ACTIVE 10–20 juin | Client réserve 12–15 juin | Couverture totale ; pas de conflit | Réservation créée, référence garantie renseignée | V (référence obligatoire) | Litige sans preuve |
| S10 | Réservation hors couverture | Garantie 10–20 juin | Client réserve 18–25 juin | Couverture totale | Refus | R (D-PA-01 couverture totale) | Engagement non couvert |
| S11 | Double demande PENDING | V libre 5–7 août | Clients X et Y demandent les mêmes dates | Hold selon D-PA-04 | Selon décision O ; actuel : les deux PENDING coexistent | O (D-PA-04) | Échec tardif à la confirmation |
| S12 | Confirmation concurrente | X et Y en PENDING | Deux confirmations simultanées | EXCLUDE V4 sur CONFIRMED/IN_PROGRESS | Une seule réussit ; l'autre reçoit une erreur | Code actuel (vérifié) ; test PostgreSQL requis | Double réservation |
| S13 | Annulation d'une réservation CONFIRMED | Réservation liée à garantie A | Client annule | Politique d'annulation | Statut CANCELLED ; référence garantie conservée | R (D-PA-09 pour les frais, O) | Perte de la référence |
| S14 | Annulation d'une réservation PENDING | PENDING sur garantie A | Client annule | Transition PENDING→CANCELLED autorisée par le code | CANCELLED ; garantie inchangée | Code actuel (BookingStatus) | Aucun si garantie intacte |
| S15 | Retrait d'une garantie avec CONFIRMED | Garantie A ACTIVE, réservation CONFIRMED future | Admin retire | Matrice D-PA-07 | Refus par défaut | O (D-PA-07), R (refus par défaut) | Client sans véhicule |
| S16 | Retrait d'une garantie sans réservation | Garantie A ACTIVE, aucune réservation | Admin retire, motif | Pas de réservation référencée | WITHDRAWN, audit écrit | R (D-PA-07) | Retrait non tracé |
| S17 | Début de location | Réservation CONFIRMED | Prise en charge | Transition CONFIRMED→IN_PROGRESS | IN_PROGRESS ; véhicule RENTED ; transfert et retrait bloqués | Code actuel ; V (D-PA-12 : IN_PROGRESS bloquant) | Véhicule retiré en location |
| S18 | Fin de location | IN_PROGRESS | Restitution | Transition IN_PROGRESS→COMPLETED | COMPLETED ; garantie et référence conservées | V (historique) | Données historiques supprimées |
| S19 | Véhicule en maintenance avec réservation future | V CONFIRMED future | Passage en MAINTENANCE | Aucune garde actuelle | Refus ou action imposée | R (D-PA-16) ; **écart connu** du code | Client sans véhicule |
| S20 | Partenaire désactivé avec garanties futures | A désactivé ; garantie future | Désactivation | Règle non décidée | Garantie non prolongée ; pas de retrait automatique | R (D-PA-17) ; O | Engagement d'un partenaire inactif |
| S21 | Véhicule archivé | V a réservations et garanties historiques | Demande de suppression | Dépendances | Archivage, jamais suppression physique | R (§11 proposition, non validée) | Perte historique |
| S22 | Suppression impossible | Réservation référence V | DELETE véhicule | FK | Refus par contrainte | Code actuel : FK booking→vehicle (à vérifier) | Incohérence |
| S23 | Réservation PENDING pendant un transfert | PENDING sur A ; transfert demandé | Transfert | Règle §3.5 | Selon décision O (recommandation : refus pendant le hold) | O (D-PA-12 §3.5, D-PA-04) | Client sur mauvais partenaire |
| S24 | Acompte versé, réservation annulée | Paiement capturé, CONFIRMED | Annulation | Remboursement | Selon politique O | O (D5 / D-PA-09) | Litige financier |
| S25 | Paiement sur devis supprimé | Payment lié au devis | Suppression du devis | FK quote ON DELETE CASCADE (V6) | Risque : paiement effacé | Constat (V6) ; V22 proposée | Perte financière |
| S26 | Donnée historique sans garantie | Réservation créée avant le nouveau système | Consultation, annulation | Référence NULL autorisée | Accès OK ; aucune nouvelle réservation sans garantie | V (référence NULL historique) | Rejet de données légitimes |
| S27 | Nouvelle réservation sans garantie via API | Client appelle l'API directement | POST sans garantie | Validation backend obligatoire | Refus 400/422 | V (contrôle BookingService) | Contournement de l'interface |
| S28 | Accès à la réservation d'un autre client | Client X connecté | GET réservation de Y | Propriété de la ressource | Refus 403 ou 404 | Exigence sécurité (ASVS) | Fuite de données |
| S29 | Recherche publique pendant maintenance | V en MAINTENANCE | Recherche | `rentable` | V absent des résultats | Code actuel (BookingService) | Aucun |
| S30 | Partenaire et commission | Partenaire A avec commission_rate en V1 | Calcul de commission | Champ non utilisé | Commission D1 indépendante du partenaire | V (D1 ; commission_rate interdit) | Commission erronée |

## Synthèse

- **Scénarios couverts par une règle validée** : S01, S03, S04, S05, S06, S08, S09, S17, S18, S26, S27, S30.
- **Scénarios bloqués par une décision ouverte** : S11, S15, S20, S23, S24. Ne pas implémenter sans décision.
- **Écarts connus du code actuel** : S11 (PENDING ne bloque pas), S19 (maintenance sans garde), S25 (cascade sur paiement). Ces écarts doivent être référencés dans `review/12` et `review/14`.
- **Scénarios à vérifier sur PostgreSQL isolé** : S02, S12, S22, S25. Aucun n'a été exécuté dans cette mission.
